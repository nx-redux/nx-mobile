/*
 *     Copyright (C) 2020  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#define MODULE_NAME_CORE "Libretro Core"

#include <utility>
#include <vector>
#include <string>
#include <cstring>
#include <cmath>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <unordered_map>
#include <algorithm>

#include "../../libretro-common/include/libretro.h"
#include "log.h"
#include "environment.h"
#include "hwcontext.h"
#include "sharedcontexts.h"
#include "vfs/vfs.h"
#include "microphone/microphoneinterface.h"
#include "languagemap.h"
#include "achievements/achievements.h"
#include "corelog.h"
#include "netplay/linkhooks.h"
#include "sensorstate.h"
#include "vulkanbridge.h"

void Environment::initialize(
    const std::string &requiredSystemDirectory,
    const std::string &requiredSavesDirectory,
    retro_hw_get_current_framebuffer_t required_callback_get_current_framebuffer
) {
    // A failed load never reaches destroy() (the view's lifecycle observer is only registered after a successful load),
    // so the previous core's callbacks and options would otherwise survive into the next create() after its .so is dlclosed.
    deinitialize();
    callback_get_current_framebuffer = required_callback_get_current_framebuffer;
    systemDirectory = requiredSystemDirectory;
    savesDirectory = requiredSavesDirectory;
}

void Environment::deinitialize() {
    callback_get_current_framebuffer = nullptr;
    hw_context_reset = nullptr;
    hw_context_destroy = nullptr;

    hasDiskControl = false;
    diskControl = {};
    core_options_update_display_callback = nullptr;

    variables.clear();
    dirtyVariables = false;
    nextVariableOrder = 0;

    savesDirectory = std::string();
    systemDirectory = std::string();
    language = RETRO_LANGUAGE_ENGLISH;

    pixelFormat = RETRO_PIXEL_FORMAT_RGB565;
    useHWAcceleration = false;
    useDepth = false;
    useStencil = false;
    bottomLeftOrigin = false;
    screenRotation = 0;
    screenRotationUpdated = false;

    gameGeometryUpdated = false;
    gameGeometryWidth = 0;
    gameGeometryHeight = 0;
    gameGeometryAspectRatio = -1.0f;
    timingUpdated.store(false);
    timingFps.store(0.0);

    rumbleStates.fill(libretrodroid::RumbleState {});

    controllers.clear();
    useVirtualFileSystem = false;
    enableMicrophone = false;
    shutdownRequest.reset();
    preferVulkan = false;
    useVulkan = false;
    loggedVulkanInterface = false;
}

void Environment::updateVariable(const std::string& key, const std::string& value) {
    auto current = variables[key];
    current.key = key;

    if (value != current.value) {
        current.value = value;
        variables[key] = current;
        dirtyVariables = true;
    }
}

namespace {

std::string safeString(const char* value) {
    return value != nullptr ? std::string(value) : std::string();
}

// Rebuilds the legacy "Label; a|b|c" description so SET_VARIABLES consumers keep working.
std::string legacyDescription(const struct Variable& option) {
    std::string result = option.label + "; ";
    for (size_t i = 0; i < option.values.size(); i++) {
        if (i > 0) result += "|";
        result += option.values[i];
    }
    return result;
}

void fillValues(struct Variable& option, const struct retro_core_option_value* values) {
    for (int i = 0; i < RETRO_NUM_CORE_OPTION_VALUES_MAX && values[i].value != nullptr; i++) {
        option.values.emplace_back(values[i].value);
        option.valueLabels.emplace_back(
            values[i].label != nullptr ? values[i].label : values[i].value
        );
    }
}

// Overrides the labels of values the localized definition also declares.
void localizeValues(struct Variable& option, const struct retro_core_option_value* localValues) {
    for (int i = 0; i < RETRO_NUM_CORE_OPTION_VALUES_MAX && localValues[i].value != nullptr; i++) {
        if (localValues[i].label == nullptr) continue;
        for (size_t j = 0; j < option.values.size(); j++) {
            if (option.values[j] == localValues[i].value) {
                option.valueLabels[j] = localValues[i].label;
            }
        }
    }
}

template <typename Definition>
const Definition* findDefinition(const Definition* definitions, const std::string& key) {
    if (definitions == nullptr) return nullptr;
    for (int i = 0; definitions[i].key != nullptr; i++) {
        if (key == definitions[i].key) return &definitions[i];
    }
    return nullptr;
}

std::string categoryLabel(const struct retro_core_option_v2_category* categories, const std::string& key) {
    if (categories == nullptr || key.empty()) return std::string();
    for (int i = 0; categories[i].key != nullptr; i++) {
        if (key == categories[i].key) return safeString(categories[i].desc);
    }
    return std::string();
}

std::string categoryInfo(const struct retro_core_option_v2_category* categories, const std::string& key) {
    if (categories == nullptr || key.empty()) return std::string();
    for (int i = 0; categories[i].key != nullptr; i++) {
        if (key == categories[i].key) return safeString(categories[i].info);
    }
    return std::string();
}

struct Variable optionFromV1(
    const struct retro_core_option_definition& definition,
    const struct retro_core_option_definition* local
) {
    struct Variable option;
    option.key = definition.key;
    option.label = safeString(local != nullptr && local->desc != nullptr ? local->desc : definition.desc);
    option.info = safeString(local != nullptr && local->info != nullptr ? local->info : definition.info);
    fillValues(option, definition.values);
    if (local != nullptr) localizeValues(option, local->values);
    option.defaultValue = safeString(definition.default_value);
    return option;
}

struct Variable optionFromV2(
    const struct retro_core_options_v2& options,
    const struct retro_core_option_v2_definition& definition,
    const struct retro_core_options_v2* localOptions
) {
    auto local = localOptions != nullptr
        ? findDefinition(localOptions->definitions, definition.key)
        : nullptr;

    struct Variable option;
    option.key = definition.key;
    option.label = safeString(local != nullptr && local->desc != nullptr ? local->desc : definition.desc);
    option.labelCategorized = safeString(
        local != nullptr && local->desc_categorized != nullptr
            ? local->desc_categorized
            : definition.desc_categorized
    );
    option.info = safeString(local != nullptr && local->info != nullptr ? local->info : definition.info);
    option.category = safeString(definition.category_key);
    if (localOptions != nullptr) {
        option.categoryLabel = categoryLabel(localOptions->categories, option.category);
    }
    if (option.categoryLabel.empty()) {
        option.categoryLabel = categoryLabel(options.categories, option.category);
    }
    if (localOptions != nullptr) {
        option.categoryInfo = categoryInfo(localOptions->categories, option.category);
    }
    if (option.categoryInfo.empty()) {
        option.categoryInfo = categoryInfo(options.categories, option.category);
    }
    fillValues(option, definition.values);
    if (local != nullptr) localizeValues(option, local->values);
    option.defaultValue = safeString(definition.default_value);
    return option;
}

}

void Environment::registerOption(struct Variable option) {
    if (option.defaultValue.empty() && !option.values.empty()) {
        option.defaultValue = option.values.front();
    }
    option.description = legacyDescription(option);

    auto existing = variables.find(option.key);
    if (existing != variables.end()) {
        // Values set by the frontend before the core declared its options win over the default.
        option.value = existing->second.value;
        option.visible = existing->second.visible;
        option.order = existing->second.order;
    }
    if (option.value.empty()) {
        option.value = option.defaultValue;
    }
    if (option.order < 0) {
        option.order = nextVariableOrder++;
    }

    LOGD("Registering option %s: %s", option.key.c_str(), option.value.c_str());
    variables[option.key] = option;
}

bool Environment::environment_handle_set_variables(const struct retro_variable* received) {
    for (unsigned i = 0; received[i].key != nullptr; i++) {
        LOGD("Received variable %s: %s", received[i].key, received[i].value);

        struct Variable option;
        option.key = received[i].key;

        std::string description = safeString(received[i].value);
        auto separator = description.find(';');
        option.label = description.substr(0, separator);

        if (separator != std::string::npos) {
            auto choices = description.substr(separator + 1);
            choices.erase(0, choices.find_first_not_of(' '));
            size_t start = 0;
            while (start <= choices.size()) {
                auto end = choices.find('|', start);
                if (end == std::string::npos) end = choices.size();
                if (end > start) {
                    option.values.push_back(choices.substr(start, end - start));
                    option.valueLabels.push_back(option.values.back());
                }
                start = end + 1;
            }
        }

        registerOption(option);
    }

    return true;
}

bool Environment::environment_handle_set_core_options(const struct retro_core_option_definition* definitions) {
    if (definitions == nullptr) return false;
    for (int i = 0; definitions[i].key != nullptr; i++) {
        registerOption(optionFromV1(definitions[i], nullptr));
    }
    return true;
}

bool Environment::environment_handle_set_core_options_intl(const struct retro_core_options_intl* intl) {
    if (intl == nullptr || intl->us == nullptr) return false;
    for (int i = 0; intl->us[i].key != nullptr; i++) {
        auto local = findDefinition(intl->local, std::string(intl->us[i].key));
        registerOption(optionFromV1(intl->us[i], local));
    }
    return true;
}

bool Environment::environment_handle_set_core_options_v2(const struct retro_core_options_v2* options) {
    if (options == nullptr || options->definitions == nullptr) return false;
    for (int i = 0; options->definitions[i].key != nullptr; i++) {
        registerOption(optionFromV2(*options, options->definitions[i], nullptr));
    }
    return true;
}

bool Environment::environment_handle_set_core_options_v2_intl(const struct retro_core_options_v2_intl* intl) {
    if (intl == nullptr || intl->us == nullptr || intl->us->definitions == nullptr) return false;
    for (int i = 0; intl->us->definitions[i].key != nullptr; i++) {
        registerOption(optionFromV2(*intl->us, intl->us->definitions[i], intl->local));
    }
    return true;
}

// A core changing one of its own options: keep our copy in sync, without flagging an update.
bool Environment::environment_handle_set_variable(const struct retro_variable* variable) {
    if (variable == nullptr) return true;
    if (variable->key == nullptr || variable->value == nullptr) return false;

    auto found = variables.find(std::string(variable->key));
    if (found == variables.end()) return false;

    const auto& values = found->second.values;
    if (!values.empty() && std::find(values.begin(), values.end(), variable->value) == values.end()) {
        return false;
    }
    found->second.value = variable->value;
    return true;
}

bool Environment::updateCoreOptionsDisplay() {
    if (core_options_update_display_callback == nullptr) return false;
    return core_options_update_display_callback();
}

bool Environment::environment_handle_set_core_options_display(const struct retro_core_option_display* display) {
    if (display == nullptr || display->key == nullptr) return false;
    auto found = variables.find(std::string(display->key));
    if (found == variables.end()) return false;
    found->second.visible = display->visible;
    return true;
}

bool Environment::environment_handle_get_variable(struct retro_variable* requested) {
    LOGD("Variable requested %s", requested->key);
    auto foundVariable = variables.find(std::string(requested->key));

    if (foundVariable == variables.end()) {
        return false;
    }

    requested->value = foundVariable->second.value.c_str();
    return true;
}

bool Environment::environment_handle_set_controller_info(const struct retro_controller_info* received) {
    controllers.clear();

    unsigned player = 0;
    while (received[player].types != nullptr) {

        auto currentPlayer = received[player];

        controllers.emplace_back();

        unsigned controller = 0;
        while (controller < currentPlayer.num_types && currentPlayer.types[controller].desc != nullptr) {
            auto currentController = currentPlayer.types[controller];
            LOGD("Received controller for player %d: %d %s", player, currentController.id, currentController.desc);

            controllers[player].push_back(Controller { currentController.id, currentController.desc });
            controller++;
        }

        player++;
    }

    return true;
}

bool Environment::environment_handle_set_hw_render(struct retro_hw_render_callback* hw_render_callback) {
    // Cores ask from retro_load_game, which GLRetroView runs in onSurfaceCreated on the GL thread with the view's
    // context current, so the version queried here is the one the core will render with.
    GLint glMajor = 0, glMinor = 0;
    glGetIntegerv(GL_MAJOR_VERSION, &glMajor);
    glGetIntegerv(GL_MINOR_VERSION, &glMinor);
    if (glMajor == 0) {
        // No current context on this thread: assume what the view asks EGL for (3.x on every supported device).
        glMajor = 3;
        glMinor = 0;
    }
    if (!libretrodroid::canServeHwContext(hw_render_callback->context_type, hw_render_callback->version_major,
                                          hw_render_callback->version_minor, glMajor, glMinor, preferVulkan)) {
        LOGI("Refusing SET_HW_RENDER: context type %u (%u.%u) on GLES %d.%d",
             hw_render_callback->context_type, hw_render_callback->version_major,
             hw_render_callback->version_minor, glMajor, glMinor);
        return false;
    }

    useHWAcceleration = true;
    useVulkan = hw_render_callback->context_type == RETRO_HW_CONTEXT_VULKAN;
    useDepth = hw_render_callback->depth;
    useStencil = hw_render_callback->stencil;
    bottomLeftOrigin = hw_render_callback->bottom_left_origin;

    hw_context_destroy = hw_render_callback->context_destroy;
    hw_context_reset = hw_render_callback->context_reset;
    hw_render_callback->get_current_framebuffer = callback_get_current_framebuffer;
    hw_render_callback->get_proc_address = &eglGetProcAddress;

    if (useVulkan) {
        // A Vulkan image's row 0 is the top of the picture whatever the core says (Dolphin always sets true);
        // RetroArch ignores bottom_left_origin for Vulkan too.
        bottomLeftOrigin = false;
        LOGI("SET_HW_RENDER: Vulkan accepted (core asked for API %u)", hw_render_callback->version_major);
    }

    return true;
}

bool Environment::environment_handle_get_vfs_interface(struct retro_vfs_interface_info* vfsInterfaceInfo) {
    if (!useVirtualFileSystem) {
        return false;
    }

    // The core asks for a minimum version; refuse one we can't serve, otherwise report the
    // version we actually provide so it can use every function up to it.
    if (vfsInterfaceInfo->required_interface_version > libretrodroid::VFS::SUPPORTED_VERSION) {
        LOGE("Core requires VFS v%u, only v%u is supported",
             vfsInterfaceInfo->required_interface_version,
             libretrodroid::VFS::SUPPORTED_VERSION);
        return false;
    }

    LOGI("Serving VFS v%u (core asked for v%u)",
         libretrodroid::VFS::SUPPORTED_VERSION,
         vfsInterfaceInfo->required_interface_version);
    vfsInterfaceInfo->required_interface_version = libretrodroid::VFS::SUPPORTED_VERSION;
    vfsInterfaceInfo->iface = libretrodroid::VFS::getInterface();
    return true;
}

bool Environment::environment_handle_get_microphone_interface(struct retro_microphone_interface* microphone_interface) {
    if (!enableMicrophone) {
        return false;
    }

    *microphone_interface = *libretrodroid::MicrophoneInterface::getInterface();
    return true;
}

void Environment::callback_retro_log(enum retro_log_level level, const char *fmt, ...) {
    va_list argptr;
    va_start(argptr, fmt);

    if (level == RETRO_LOG_WARN || level == RETRO_LOG_ERROR) {
        va_list kept;
        va_copy(kept, argptr);
        libretrodroid::CoreLog::append(level == RETRO_LOG_ERROR ? 'E' : 'W', fmt, kept);
        va_end(kept);
    }

    switch (level) {
#if VERBOSE_LOGGING
        case RETRO_LOG_DEBUG:
            if (nx_log_enabled()) __android_log_vprint(ANDROID_LOG_DEBUG, MODULE_NAME_CORE, fmt, argptr);
            break;
#endif
        // 2026-10-14: below an error, only with Settings → System → Debug log on (CoreLog above keeps its copy).
        case RETRO_LOG_INFO:
            if (nx_log_enabled()) __android_log_vprint(ANDROID_LOG_INFO, MODULE_NAME_CORE, fmt, argptr);
            break;
        case RETRO_LOG_WARN:
            if (nx_log_enabled()) __android_log_vprint(ANDROID_LOG_WARN, MODULE_NAME_CORE, fmt, argptr);
            break;
        case RETRO_LOG_ERROR:   // always, as LOGE (the Debug log setting keeps errors)
            __android_log_vprint(ANDROID_LOG_ERROR, MODULE_NAME_CORE, fmt, argptr);
            break;
        default:
            // Log nothing in here.
            break;
    }
}

bool Environment::callback_set_sensor_state(unsigned port, enum retro_sensor_action action, unsigned rate) {
    return libretrodroid::SensorState::get().setState(port, action);
}

float Environment::callback_get_sensor_input(unsigned port, unsigned id) {
    return libretrodroid::SensorState::get().input(port, id);
}

bool Environment::callback_set_rumble_state(unsigned port, enum retro_rumble_effect effect, uint16_t strength) {
    return Environment::getInstance().handle_callback_set_rumble_state(port, effect, strength);
}

bool Environment::handle_callback_set_rumble_state(unsigned port, enum retro_rumble_effect effect, uint16_t strength) {
    LOGV("Setting rumble strength for port %i to %i", port, strength);
    if (port < 0 || port > 3) return false;

    if (effect == RETRO_RUMBLE_STRONG) {
        rumbleStates[port].strengthStrong = strength;
    } else if (effect == RETRO_RUMBLE_WEAK) {
        rumbleStates[port].strengthWeak = strength;
    }

    return true;
}

bool Environment::callback_environment(unsigned cmd, void *data) {
    return Environment::getInstance().handle_callback_environment(cmd, data);
}

bool Environment::handle_callback_environment(unsigned cmd, void *data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            *((bool*) data) = true;
            return true;

        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            LOGD("Called SET_PIXEL_FORMAT");
            pixelFormat = *static_cast<enum retro_pixel_format *>(data);
            return true;
        }

        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
            LOGD("Called SET_INPUT_DESCRIPTORS");
            return false;

        case RETRO_ENVIRONMENT_GET_VARIABLE:
            LOGD("Called RETRO_ENVIRONMENT_GET_VARIABLE");
            return environment_handle_get_variable(static_cast<struct retro_variable*>(data));

        case RETRO_ENVIRONMENT_SET_VARIABLES:
            LOGD("Called RETRO_ENVIRONMENT_SET_VARIABLES");
            return environment_handle_set_variables(static_cast<const struct retro_variable*>(data));

        case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:
            LOGD("Called RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION");
            *((unsigned*) data) = 2;
            return true;

        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS:
            LOGD("Called RETRO_ENVIRONMENT_SET_CORE_OPTIONS");
            return environment_handle_set_core_options(static_cast<const struct retro_core_option_definition*>(data));

        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_INTL:
            LOGD("Called RETRO_ENVIRONMENT_SET_CORE_OPTIONS_INTL");
            return environment_handle_set_core_options_intl(static_cast<const struct retro_core_options_intl*>(data));

        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2:
            LOGD("Called RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2");
            return environment_handle_set_core_options_v2(static_cast<const struct retro_core_options_v2*>(data));

        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2_INTL:
            LOGD("Called RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2_INTL");
            return environment_handle_set_core_options_v2_intl(static_cast<const struct retro_core_options_v2_intl*>(data));

        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_DISPLAY:
            LOGD("Called RETRO_ENVIRONMENT_SET_CORE_OPTIONS_DISPLAY");
            return environment_handle_set_core_options_display(static_cast<const struct retro_core_option_display*>(data));

        case RETRO_ENVIRONMENT_SET_VARIABLE:
            LOGD("Called RETRO_ENVIRONMENT_SET_VARIABLE");
            return environment_handle_set_variable(static_cast<const struct retro_variable*>(data));

        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_UPDATE_DISPLAY_CALLBACK: {
            LOGD("Called RETRO_ENVIRONMENT_SET_CORE_OPTIONS_UPDATE_DISPLAY_CALLBACK");
            auto callback = static_cast<const struct retro_core_options_update_display_callback*>(data);
            core_options_update_display_callback = callback != nullptr ? callback->callback : nullptr;
            return true;
        }

        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE: {
            LOGD("Called RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE. Is dirty?: %d", dirtyVariables);
            *((bool*) data) = dirtyVariables;
            dirtyVariables = false;
            return true;
        }

        case RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER: {
            LOGD("Called RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER");
            *((unsigned*) data) = preferVulkan ? retro_hw_context_type::RETRO_HW_CONTEXT_VULKAN
                                               : retro_hw_context_type::RETRO_HW_CONTEXT_OPENGLES3;
            return true;
        }

        // Vulkan negotiation (libretro_vulkan.h), served only when Vulkan is offered or accepted.
        case RETRO_ENVIRONMENT_GET_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE_SUPPORT: {
            auto* query = static_cast<struct retro_hw_render_context_negotiation_interface*>(data);
            if (query == nullptr || !preferVulkan) return false;
            if (query->interface_type != RETRO_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE_VULKAN) {
                query->interface_version = 0;
                return true;
            }
            query->interface_version = RETRO_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE_VULKAN_VERSION;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE: {
            auto* negotiation = static_cast<const struct retro_hw_render_context_negotiation_interface*>(data);
            if (negotiation == nullptr || !useVulkan ||
                negotiation->interface_type != RETRO_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE_VULKAN) {
                return false;
            }
            libretrodroid::VulkanBridge::get().setNegotiation(
                static_cast<const struct retro_hw_render_context_negotiation_interface_vulkan*>(data));
            return true;
        }

        case RETRO_ENVIRONMENT_GET_HW_RENDER_INTERFACE: {
            if (data == nullptr || !useVulkan || !libretrodroid::VulkanBridge::get().isReady()) return false;
            *static_cast<const struct retro_hw_render_interface**>(data) =
                reinterpret_cast<const struct retro_hw_render_interface*>(libretrodroid::VulkanBridge::get().getInterface());
            // Once per session: Azahar asks every frame.
            if (!loggedVulkanInterface.exchange(true)) {
                LOGI("GET_HW_RENDER_INTERFACE: Vulkan interface v%u", RETRO_HW_RENDER_INTERFACE_VULKAN_VERSION);
            }
            return true;
        }

        case RETRO_ENVIRONMENT_SET_HW_RENDER:
            LOGD("Called RETRO_ENVIRONMENT_SET_HW_RENDER");
            return environment_handle_set_hw_render(static_cast<struct retro_hw_render_callback*>(data));

        // nx-mobile: Azahar (3DS) asks for a shared context before SET_HW_RENDER. The runner's GL context is the
        // core's alone already (one per game, nothing else drawing in it), and the frontend's draw restores the state
        // it touches (Video::renderFrame), so there is nothing to change: acknowledge it.
        case RETRO_ENVIRONMENT_SET_HW_SHARED_CONTEXT:
            LOGD("Called RETRO_ENVIRONMENT_SET_HW_SHARED_CONTEXT");
            return true;

        // The core wants to end (Dolphin: a game leaving for the Wii Menu, which is not installed): the host closes the
        // game as its own Quit would. May come from a core thread.
        case RETRO_ENVIRONMENT_SHUTDOWN:
            LOGI("Called RETRO_ENVIRONMENT_SHUTDOWN");
            shutdownRequest.request();
            return true;

        // nx-mobile 2026-10-13: contexts sharing the core's, for its worker threads (Dolphin's shader compilers);
        // libretro has no call for them. Only our patched cores ask (nxsharedgl.h).
        case NX_ENVIRONMENT_GET_SHARED_GL:
            LOGD("Called NX_ENVIRONMENT_GET_SHARED_GL");
            if (data == nullptr) return false;
            libretrodroid::SharedContexts::fill(static_cast<nx_shared_gl_interface*>(data));
            return true;

        case RETRO_ENVIRONMENT_SET_MESSAGE: {
            auto message = static_cast<const struct retro_message*>(data);
            if (message && message->msg) LOGI("Core message: %s", message->msg);
            return true;
        }

        case RETRO_ENVIRONMENT_SET_MESSAGE_EXT: {
            auto message = static_cast<const struct retro_message_ext*>(data);
            if (message && message->msg) LOGI("Core message: %s", message->msg);
            return true;
        }

        case RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE");
            ((struct retro_rumble_interface*) data)->set_rumble_state = &callback_set_rumble_state;
            return true;

        case RETRO_ENVIRONMENT_GET_SENSOR_INTERFACE: {
            LOGD("Called RETRO_ENVIRONMENT_GET_SENSOR_INTERFACE");
            if (data == nullptr) return false;
            auto* iface = static_cast<struct retro_sensor_interface*>(data);
            iface->set_sensor_state = &callback_set_sensor_state;
            iface->get_sensor_input = &callback_get_sensor_input;
            return true;
        }

        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_LOG_INTERFACE");
            ((struct retro_log_callback*) data)->log = &callback_retro_log;
            return true;

        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            LOGD("Called RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY");
            *(const char**) data = savesDirectory.c_str();
            return !savesDirectory.empty();

        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            LOGD("Called RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY");
            *(const char**) data = systemDirectory.c_str();
            return !systemDirectory.empty();

        case RETRO_ENVIRONMENT_SET_ROTATION: {
            LOGD("Called RETRO_ENVIRONMENT_SET_ROTATION");
            unsigned screenRotationIndex = (*static_cast<unsigned*>(data));
            screenRotation = screenRotationIndex * (float) (-M_PI / 2.0);
            screenRotationUpdated = true;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_DISK_CONTROL_INTERFACE: {
            LOGD("Called RETRO_ENVIRONMENT_SET_DISK_CONTROL_INTERFACE");
            // The core's struct is only valid for this call (SwanStation passes a temporary): keep a copy, as RetroArch does.
            auto* received = static_cast<const struct retro_disk_control_callback*>(data);
            hasDiskControl = received != nullptr;
            diskControl = hasDiskControl ? *received : retro_disk_control_callback {};
            return true;
        }

        case RETRO_ENVIRONMENT_GET_PERF_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_PERF_INTERFACE");
            return false;

        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO: {
            // A new AV info can change the frame rate too: flycast reports half its rate when a game renders every
            // other vblank (its retro_run runs until the game renders), so pacing at the old rate would run it 2x.
            // TODO: a changed timing.sample_rate is still ignored (Audio keeps the rate it was built with).
            auto *avInfo = static_cast<struct retro_system_av_info *>(data);
            if (avInfo->timing.fps > 1.0 && avInfo->timing.fps <= 240.0) {
                timingFps.store(avInfo->timing.fps, std::memory_order_relaxed);
                timingUpdated.store(true, std::memory_order_release);
            }
            // Pre-existing race, left as is: the geometry fields below are plain and read on the GL thread.
            gameGeometryHeight = avInfo->geometry.base_height;
            gameGeometryWidth = avInfo->geometry.base_width;
            gameGeometryAspectRatio = avInfo->geometry.aspect_ratio;
            gameGeometryUpdated = true;
            libretrodroid::VulkanBridge::get().setMaxFrame(avInfo->geometry.max_width, avInfo->geometry.max_height);
            return true;
        }

        case RETRO_ENVIRONMENT_SET_GEOMETRY: {
            struct retro_game_geometry *geometry = static_cast<struct retro_game_geometry *>(data);
            gameGeometryHeight = geometry->base_height;
            gameGeometryWidth = geometry->base_width;
            gameGeometryAspectRatio = geometry->aspect_ratio;
            gameGeometryUpdated = true;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
            LOGD("Called RETRO_ENVIRONMENT_SET_CONTROLLER_INFO");
            return environment_handle_set_controller_info(static_cast<const struct retro_controller_info*>(data));

        case RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE:
            LOGD("Called RETRO_ENVIRONMENT_GET_AUDIO_VIDEO_ENABLE");
            return false;

        case RETRO_ENVIRONMENT_GET_LANGUAGE:
            LOGD("Called RETRO_ENVIRONMENT_GET_LANGUAGE");
            *((unsigned*) data) = language;
            return true;

        case RETRO_ENVIRONMENT_GET_VFS_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_VFS_INTERFACE");
            return environment_handle_get_vfs_interface(static_cast<struct retro_vfs_interface_info*>(data));

        case RETRO_ENVIRONMENT_GET_MICROPHONE_INTERFACE:
            LOGD("Called RETRO_ENVIRONMENT_GET_MICROPHONE_INTERFACE");
            return environment_handle_get_microphone_interface(static_cast<struct retro_microphone_interface*>(data));

        case RETRO_ENVIRONMENT_SET_MEMORY_MAPS:
            LOGD("Called RETRO_ENVIRONMENT_SET_MEMORY_MAPS");
            libretrodroid::Achievements::getInstance().setMemoryMap(static_cast<const struct retro_memory_map*>(data));
            return true;

        case NL_ENVIRONMENT_SET_NETPACKET_INTERFACE:   // nx-mobile: the GBA link (netplay/linkhooks.h)
            LOGD("Called SET_NETPACKET_INTERFACE");
            return libretrodroid::gbalink::setNetpacket(static_cast<const nl_netpacket_callback*>(data));

        default:
            LOGD("callback environment has been called: %u", cmd);
            return false;
    }
}

void Environment::setLanguage(const std::string& androidLanguage) {
    // Android reports "ja" (not "jp"), and Chinese needs the script or region to pick Simplified or Traditional.
    auto mapped = libretrodroid::retroLanguageFor(androidLanguage);
    if (mapped) language = *mapped;
}

retro_hw_context_reset_t Environment::getHwContextReset() const {
    return hw_context_reset;
}

retro_hw_context_reset_t Environment::getHwContextDestroy() const {
    return hw_context_destroy;
}

struct retro_disk_control_callback* Environment::getRetroDiskControlCallback() const {
    return hasDiskControl ? const_cast<struct retro_disk_control_callback*>(&diskControl) : nullptr;
}

int Environment::getPixelFormat() const {
    return pixelFormat;
}

bool Environment::isUseHwAcceleration() const {
    return useHWAcceleration;
}

bool Environment::isUseDepth() const {
    return useDepth;
}

bool Environment::isUseStencil() const {
    return useStencil;
}

bool Environment::isBottomLeftOrigin() const {
    return bottomLeftOrigin;
}

float Environment::getScreenRotation() const {
    return screenRotation;
}

bool Environment::isGameGeometryUpdated() const {
    return gameGeometryUpdated;
}

void Environment::clearGameGeometryUpdated() {
    gameGeometryUpdated = false;
}

bool Environment::consumeTimingUpdate(double &fps) {
    if (!timingUpdated.exchange(false, std::memory_order_acquire)) return false;
    fps = timingFps.load(std::memory_order_relaxed);
    return true;
}

unsigned int Environment::getGameGeometryWidth() const {
    return gameGeometryWidth;
}

unsigned int Environment::getGameGeometryHeight() const {
    return gameGeometryHeight;
}

float Environment::getGameGeometryAspectRatio() const {
    return gameGeometryAspectRatio;
}

const std::vector<struct Variable> Environment::getVariables() const {
    std::vector<struct Variable> result;

    std::for_each(
        variables.begin(),
        variables.end(),
        [&](std::pair<std::string, struct Variable> item) {
            result.push_back(item.second);
        }
    );

    // Declared options keep the core's order; values the core never declared go last, by key.
    std::sort(
        result.begin(),
        result.end(),
        [](const struct Variable& v1, const struct Variable& v2) {
            if ((v1.order < 0) != (v2.order < 0)) return v2.order < 0;
            if (v1.order >= 0) return v1.order < v2.order;
            return v1.key < v2.key;
        }
    );

    return result;
}

const std::vector<std::vector<struct Controller>> &Environment::getControllers() const {
    return controllers;
}

float Environment::retrieveGameSpecificAspectRatio() {
    if (getGameGeometryAspectRatio() > 0) {
        return getGameGeometryAspectRatio();
    }

    if (getGameGeometryWidth() > 0 && getGameGeometryHeight() > 0) {
        return (float) getGameGeometryWidth() / (float) getGameGeometryHeight();
    }

    return -1.0f;
}

bool Environment::isScreenRotationUpdated() const {
    return screenRotationUpdated;
}

void Environment::clearScreenRotationUpdated() {
    screenRotationUpdated = false;
}

std::array<libretrodroid::RumbleState, 4>& Environment::getLastRumbleStates() {
    return rumbleStates;
}

void Environment::setEnableVirtualFileSystem(bool value) {
    this->useVirtualFileSystem = value;
}

void Environment::setEnableMicrophone(bool value) {
    this->enableMicrophone = value;
}
