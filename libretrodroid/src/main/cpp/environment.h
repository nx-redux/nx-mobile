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

#ifndef LIBRETRODROID_ENVIRONMENT_H
#define LIBRETRODROID_ENVIRONMENT_H

#define MODULE_NAME_CORE "Libretro Core"

#include <vector>
#include <string>
#include <cstring>
#include <cmath>
#include <EGL/egl.h>
#include <unordered_map>
#include <array>
#include <atomic>
#include <mutex>
#include <optional>

#include "shutdownrequest.h"

#include "../../libretro-common/include/libretro.h"
#include "log.h"
#include "rumblestate.h"

class Environment {
public:
    static Environment& getInstance()
    {
        static Environment instance;
        return instance;
    }
    Environment(Environment const&) = delete;
    void operator=(Environment const&) = delete;

    static void callback_retro_log(enum retro_log_level level, const char *fmt, ...);

    static bool callback_set_sensor_state(unsigned port, enum retro_sensor_action action, unsigned rate);

    static float callback_get_sensor_input(unsigned port, unsigned id);

    static bool callback_set_rumble_state(
        unsigned port,
        enum retro_rumble_effect effect,
        uint16_t strength
    );

    static bool callback_environment(unsigned cmd, void *data);

    void setEnableVirtualFileSystem(bool value);
    void setEnableMicrophone(bool value);
    // Answer GET_PREFERRED_HW_RENDER with Vulkan and accept a Vulkan SET_HW_RENDER. Set after initialize().
    void setPreferVulkan(bool value) { preferVulkan = value; }
    bool isUseVulkan() const { return useVulkan; }
    // The core's RETRO_ENVIRONMENT_SHUTDOWN since the last take (any thread), cleared on take.
    bool takeShutdownRequest() { return shutdownRequest.take(); }
    // nx-mobile 2026-10-24: whether fast forward runs, for GET_FASTFORWARDING (flycast paces itself and only pushes
    // audio at FF speed once it can read it). Set by step() on the GL thread; cores read it from their own threads.
    void setFastForwarding(bool value) { fastForwarding.store(value); }
    // The core's last SET_FASTFORWARDING_OVERRIDE (mupen64plus asks for it). Stored only: the host's FF state wins.
    bool getFastForwardingOverride(struct retro_fastforwarding_override &out);

private:
    Environment() {}

public:
    void initialize(
        const std::string &requiredSystemDirectory,
        const std::string &requiredSavesDirectory,
        retro_hw_get_current_framebuffer_t required_callback_get_current_framebuffer
    );

    void deinitialize();

    void updateVariable(const std::string &key, const std::string &value);

    void setLanguage(const std::string &androidLanguage);

    float retrieveGameSpecificAspectRatio();

    bool handle_callback_set_rumble_state(
        unsigned port,
        enum retro_rumble_effect effect,
        uint16_t strength
    );

    bool handle_callback_environment(unsigned cmd, void *data);

    retro_hw_context_reset_t getHwContextReset() const;
    retro_hw_context_reset_t getHwContextDestroy() const;

    struct retro_disk_control_callback* getRetroDiskControlCallback() const;

    int getPixelFormat() const;
    bool isUseHwAcceleration() const;
    bool isUseDepth() const;
    bool isUseStencil() const;
    bool isBottomLeftOrigin() const;

    float getScreenRotation() const;
    bool isScreenRotationUpdated() const;
    void clearScreenRotationUpdated();

    unsigned int getGameGeometryWidth() const;
    unsigned int getGameGeometryHeight() const;
    float getGameGeometryAspectRatio() const;
    bool isGameGeometryUpdated() const;
    void clearGameGeometryUpdated();

    // Content frame rate from RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO. True once per new report, with its fps in [fps].
    // Thread-safe: flycast reports it from its emulation thread (spg.cpp CalculateSync) while step() reads it on the GL thread.
    bool consumeTimingUpdate(double &fps);

    std::array<libretrodroid::RumbleState, 4> & getLastRumbleStates();

    const std::vector<struct Variable> getVariables() const;
    bool updateCoreOptionsDisplay();

    const std::vector<std::vector<struct Controller>> &getControllers() const;

private:
    bool environment_handle_set_variables(const struct retro_variable* received);
    bool environment_handle_set_core_options(const struct retro_core_option_definition* definitions);
    bool environment_handle_set_core_options_intl(const struct retro_core_options_intl* intl);
    bool environment_handle_set_core_options_v2(const struct retro_core_options_v2* options);
    bool environment_handle_set_core_options_v2_intl(const struct retro_core_options_v2_intl* intl);
    bool environment_handle_set_core_options_display(const struct retro_core_option_display* display);
    bool environment_handle_set_variable(const struct retro_variable* variable);
    void registerOption(struct Variable option);
    bool environment_handle_get_variable(struct retro_variable* requested);
    bool environment_handle_set_controller_info(const struct retro_controller_info* received);
    bool environment_handle_set_hw_render(struct retro_hw_render_callback* hw_render_callback);
    bool environment_handle_get_vfs_interface(struct retro_vfs_interface_info* vfs_interface_info);
    bool environment_handle_get_microphone_interface(struct retro_microphone_interface* microphone_interface);

private:
    retro_hw_context_reset_t hw_context_reset = nullptr;
    retro_hw_context_reset_t hw_context_destroy = nullptr;
    struct retro_disk_control_callback diskControl {};
    bool hasDiskControl = false;

    std::string savesDirectory;
    std::string systemDirectory;
    retro_hw_get_current_framebuffer_t callback_get_current_framebuffer = nullptr;
    unsigned language = RETRO_LANGUAGE_ENGLISH;
    bool useVirtualFileSystem = false;
    bool enableMicrophone = false;
    libretrodroid::ShutdownRequest shutdownRequest;
    std::atomic<bool> fastForwarding { false };
    std::mutex fastForwardingOverrideMutex;
    std::optional<struct retro_fastforwarding_override> fastForwardingOverride;
    bool preferVulkan = false;
    bool useVulkan = false;
    std::atomic<bool> loggedVulkanInterface { false };   // GET_HW_RENDER_INTERFACE logged this session

    int pixelFormat = RETRO_PIXEL_FORMAT_RGB565;
    bool useHWAcceleration = false;
    bool useDepth = false;
    bool useStencil = false;
    bool bottomLeftOrigin = false;

    float screenRotation = 0;
    bool screenRotationUpdated = false;

    bool gameGeometryUpdated = false;
    unsigned gameGeometryWidth = 0;
    unsigned gameGeometryHeight = 0;
    float gameGeometryAspectRatio = -1.0f;

    // Written by the core's thread (fps, then the flag with release), read by the GL thread (flag with acquire, then fps).
    std::atomic<bool> timingUpdated { false };
    std::atomic<double> timingFps { 0.0 };

    std::array<libretrodroid::RumbleState, 4> rumbleStates;

    std::unordered_map<std::string, struct Variable> variables;
    bool dirtyVariables = false;
    int nextVariableOrder = 0;
    retro_core_options_update_display_callback_t core_options_update_display_callback = nullptr;

    std::vector<std::vector<struct Controller>> controllers;
};

struct Variable {
public:
    std::string key;
    std::string value;
    std::string description;

    // Rich core option metadata, filled when the core declares its options.
    std::string label;
    std::string labelCategorized;
    std::string info;
    std::string category;
    std::string categoryLabel;
    std::string categoryInfo;
    std::vector<std::string> values;
    std::vector<std::string> valueLabels;
    std::string defaultValue;
    bool visible = true;
    int order = -1;
};

struct Controller {
public:
    unsigned id;
    std::string description;
};

#endif //LIBRETRODROID_ENVIRONMENT_H

