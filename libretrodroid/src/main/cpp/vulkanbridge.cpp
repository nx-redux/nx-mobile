/*
 *     Copyright (C) 2026  Mohammad Syuhada
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

// No prototypes: every Vulkan call goes through the dispatch table (vk), and liblibretrodroid does not link libvulkan.
#define VK_NO_PROTOTYPES 1
#include "vulkanbridge.h"

#include <media/NdkImageReader.h>
#include <android/native_window.h>
#include <EGL/egl.h>
#include <unistd.h>

#include <algorithm>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <cstdlib>

#include "log.h"

namespace libretrodroid {

namespace {

// The last path component of [dir], without trailing slashes ("" for none).
std::string lastComponent(std::string dir) {
    while (!dir.empty() && dir.back() == '/') dir.pop_back();
    const size_t slash = dir.rfind('/');
    return slash == std::string::npos ? dir : dir.substr(slash + 1);
}

bool endsWith(const std::string& s, const std::string& suffix) {
    return s.size() >= suffix.size() && s.compare(s.size() - suffix.size(), suffix.size(), suffix) == 0;
}

// Whether the custom driver [library] from [driverDir] is mapped into this process. libadrenotools' hook falls back
// to the system ICD, silently, when the custom one fails to load, and still hands back a working loader: only the
// process's mappings tell the two apart (driverID cannot: a Qualcomm package reports the stock driver's). The linker
// may name the directory differently (/data/data/... for /data/user/0/...), so the match is on the directory's last
// component + library; a copy in [tmpDir] (patched libraries below API 29) counts too.
bool customDriverMapped(const std::string& driverDir, const std::string& library, const std::string& tmpDir) {
    const std::string driverSuffix = "/" + lastComponent(driverDir) + "/" + library;
    const std::string tmpLast = lastComponent(tmpDir);
    const std::string tmpSuffix = tmpLast.empty() ? std::string() : "/" + tmpLast + "/" + library;
    FILE* maps = fopen("/proc/self/maps", "re");
    if (maps == nullptr) {
        LOGE("Vulkan bridge: cannot read /proc/self/maps: %s", strerror(errno));
        return false;
    }
    bool found = false;
    char* line = nullptr;
    size_t capacity = 0;
    while (!found && getline(&line, &capacity, maps) > 0) {
        const char* slash = strchr(line, '/');
        if (slash == nullptr) continue;
        std::string path(slash);
        while (!path.empty() && (path.back() == '\n' || path.back() == ' ')) path.pop_back();
        const std::string deleted = " (deleted)";
        if (endsWith(path, deleted)) path.resize(path.size() - deleted.size());
        found = endsWith(path, driverSuffix) || (!tmpSuffix.empty() && endsWith(path, tmpSuffix));
    }
    free(line);
    fclose(maps);
    return found;
}

bool contains(const std::vector<std::string>& list, const char* name) {
    return std::find(list.begin(), list.end(), std::string(name)) != list.end();
}

const char* resultName(VkResult r) {
    switch (r) {
        case VK_SUCCESS: return "VK_SUCCESS";
        case VK_ERROR_OUT_OF_HOST_MEMORY: return "OUT_OF_HOST_MEMORY";
        case VK_ERROR_OUT_OF_DEVICE_MEMORY: return "OUT_OF_DEVICE_MEMORY";
        case VK_ERROR_INITIALIZATION_FAILED: return "INITIALIZATION_FAILED";
        case VK_ERROR_DEVICE_LOST: return "DEVICE_LOST";
        case VK_ERROR_EXTENSION_NOT_PRESENT: return "EXTENSION_NOT_PRESENT";
        case VK_ERROR_FEATURE_NOT_PRESENT: return "FEATURE_NOT_PRESENT";
        case VK_ERROR_INVALID_EXTERNAL_HANDLE: return "INVALID_EXTERNAL_HANDLE";
        case VK_TIMEOUT: return "TIMEOUT";
        default: return "?";
    }
}

// What the bridge needs on the core's device: AHardwareBuffer import (and what that extension depends on), plus
// sync_file export of a semaphore so GL can wait on the copy on the GPU instead of the CPU.
const char* const FRONTEND_DEVICE_EXTENSIONS[] = {
    "VK_ANDROID_external_memory_android_hardware_buffer",
    "VK_KHR_external_memory",
    "VK_KHR_get_memory_requirements2",
    "VK_KHR_sampler_ycbcr_conversion",
    "VK_KHR_maintenance1",
    "VK_KHR_bind_memory2",
    "VK_EXT_queue_family_foreign",
    "VK_KHR_dedicated_allocation",
    "VK_KHR_external_semaphore",
    "VK_KHR_external_semaphore_fd",
};

const char* const WANTED_INSTANCE_EXTENSIONS[] = {
    "VK_KHR_surface",
    "VK_KHR_android_surface",
    "VK_KHR_get_physical_device_properties2",
    "VK_KHR_external_memory_capabilities",
    "VK_KHR_external_semaphore_capabilities",
};

// Instance extensions [wanted] that the loader has, merged into [into] without duplicates.
void mergeNames(std::vector<const char*>& into, const std::vector<std::string>& wanted) {
    for (const auto& name : wanted) {
        bool present = false;
        for (const char* existing : into) {
            if (existing != nullptr && name == existing) { present = true; break; }
        }
        if (!present) into.push_back(name.c_str());
    }
}

}

VulkanBridge& VulkanBridge::get() {
    static VulkanBridge instance;
    return instance;
}

bool VulkanBridge::isSupported() {
    // Always the system driver: whether a custom one loads is decided per launch, by initialize().
    static const bool supported = [] {
        VulkanDispatch vk;
        if (!VulkanDispatch::loadSystem(vk)) {
            LOGI("Vulkan bridge: unsupported (no Vulkan loader)");
            return false;
        }
        VkApplicationInfo app { VK_STRUCTURE_TYPE_APPLICATION_INFO };
        app.apiVersion = VK_API_VERSION_1_1;
        VkInstanceCreateInfo info { VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO };
        info.pApplicationInfo = &app;
        VkInstance probe = VK_NULL_HANDLE;
        if (vk.createInstance(&info, nullptr, &probe) != VK_SUCCESS) {
            LOGI("Vulkan bridge: unsupported (no Vulkan 1.1 instance)");
            vk.close();
            return false;
        }
        // Core entry points only: the probe's instance has no surface extensions, so the surface pair is null here.
        vk.bindInstance(probe);
        if (const char* missing = vk.missingInstanceFunction()) {
            LOGI("Vulkan bridge: unsupported (loader lacks %s)", missing);
            if (vk.destroyInstance != nullptr) vk.destroyInstance(probe, nullptr);
            vk.close();
            return false;
        }
        uint32_t count = 0;
        vk.enumeratePhysicalDevices(probe, &count, nullptr);
        std::vector<VkPhysicalDevice> gpus(count);
        if (count > 0) vk.enumeratePhysicalDevices(probe, &count, gpus.data());
        bool found = false;
        if (!gpus.empty()) {
            uint32_t extCount = 0;
            vk.enumerateDeviceExtensionProperties(gpus[0], nullptr, &extCount, nullptr);
            std::vector<VkExtensionProperties> exts(extCount);
            vk.enumerateDeviceExtensionProperties(gpus[0], nullptr, &extCount, exts.data());
            for (const auto& ext : exts) {
                if (strcmp(ext.extensionName, "VK_ANDROID_external_memory_android_hardware_buffer") == 0) found = true;
            }
        }
        vk.destroyInstance(probe, nullptr);
        vk.close();
        LOGI("Vulkan bridge: %s", found ? "supported" : "unsupported (no GPU with AHardwareBuffer import)");
        return found;
    }();
    return supported;
}

void VulkanBridge::setDriver(const std::string& dir, const std::string& library, const std::string& hookLibDir,
                             const std::string& tmpDir) {
    driverDir = dir;
    driverLibrary = library;
    driverHookLibDir = hookLibDir;
    driverTmpDir = tmpDir;
}

void VulkanBridge::setNegotiation(const retro_hw_render_context_negotiation_interface_vulkan* value) {
    negotiation = value;
    if (value != nullptr) {
        LOGI("Vulkan bridge: negotiation interface v%u: get_application_info=%d create_device=%d destroy_device=%d "
             "create_instance=%d create_device2=%d",
             value->interface_version, value->get_application_info != nullptr, value->create_device != nullptr,
             value->destroy_device != nullptr,
             value->interface_version >= 2 && value->create_instance != nullptr,
             value->interface_version >= 2 && value->create_device2 != nullptr);
    }
}

bool VulkanBridge::createInstance() {
    VkApplicationInfo app { VK_STRUCTURE_TYPE_APPLICATION_INFO };
    app.pApplicationName = "nx-mobile";
    app.pEngineName = "libretrodroid";
    app.apiVersion = VK_API_VERSION_1_0;
    if (negotiation != nullptr && negotiation->get_application_info != nullptr) {
        const VkApplicationInfo* coreApp = negotiation->get_application_info();
        if (coreApp != nullptr) app = *coreApp;
    }

    // Android 9+ loaders are 1.1; 8.x is 1.0 (no vkEnumerateInstanceVersion).
    uint32_t loaderVersion = VK_API_VERSION_1_0;
    if (vk.enumerateInstanceVersion != nullptr) vk.enumerateInstanceVersion(&loaderVersion);
    // libretro_vulkan.h: a frontend may bump the instance version; 1.1 is what Android's AHB path is specified on.
    uint32_t wanted = std::min(loaderVersion, (uint32_t) VK_API_VERSION_1_1);
    if (app.apiVersion < wanted) app.apiVersion = wanted;
    instanceApiVersion = app.apiVersion;

    uint32_t count = 0;
    vk.enumerateInstanceExtensionProperties(nullptr, &count, nullptr);
    std::vector<VkExtensionProperties> available(count);
    vk.enumerateInstanceExtensionProperties(nullptr, &count, available.data());
    instanceExtensions.clear();
    for (const char* name : WANTED_INSTANCE_EXTENSIONS) {
        for (const auto& p : available) {
            if (strcmp(p.extensionName, name) == 0) { instanceExtensions.emplace_back(name); break; }
        }
    }
    if (!contains(instanceExtensions, "VK_KHR_surface") || !contains(instanceExtensions, "VK_KHR_android_surface")) {
        LOGE("Vulkan bridge: loader lacks VK_KHR_surface/VK_KHR_android_surface");
        return false;
    }

    if (negotiation != nullptr && negotiation->interface_version >= 2 && negotiation->create_instance != nullptr) {
        instance = negotiation->create_instance(vk.getInstanceProcAddr, &app, &createInstanceWrapper, this);
        ownsInstance = true;
        LOGI("Vulkan bridge: instance from the core's create_instance: %p", (void*) instance);
        return instance != VK_NULL_HANDLE && bindInstance();
    }

    std::vector<const char*> names;
    mergeNames(names, instanceExtensions);
    VkInstanceCreateInfo info { VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO };
    info.pApplicationInfo = &app;
    info.enabledExtensionCount = (uint32_t) names.size();
    info.ppEnabledExtensionNames = names.data();
    VkResult r = vk.createInstance(&info, nullptr, &instance);
    LOGI("Vulkan bridge: vkCreateInstance api %u.%u (loader %u.%u): %s", VK_VERSION_MAJOR(app.apiVersion),
         VK_VERSION_MINOR(app.apiVersion), VK_VERSION_MAJOR(loaderVersion), VK_VERSION_MINOR(loaderVersion),
         resultName(r));
    return r == VK_SUCCESS && bindInstance();
}

bool VulkanBridge::bindInstance() {
    vk.bindInstance(instance);
    const char* missing = vk.missingInstanceFunction();
    if (missing == nullptr) missing = vk.missingSurfaceFunction();
    if (missing != nullptr) {
        LOGE("Vulkan bridge: the %s loader lacks %s", vk.custom ? "custom driver's" : "system", missing);
        return false;
    }
    return true;
}

VkInstance VulkanBridge::createInstanceWrapper(void* opaque, const VkInstanceCreateInfo* createInfo) {
    auto* self = static_cast<VulkanBridge*>(opaque);
    VkInstanceCreateInfo info = *createInfo;
    std::vector<const char*> names(createInfo->ppEnabledExtensionNames,
                                   createInfo->ppEnabledExtensionNames + createInfo->enabledExtensionCount);
    mergeNames(names, self->instanceExtensions);
    info.enabledExtensionCount = (uint32_t) names.size();
    info.ppEnabledExtensionNames = names.data();
    VkInstance result = VK_NULL_HANDLE;
    VkResult r = self->vk.createInstance(&info, nullptr, &result);
    LOGI("Vulkan bridge: create_instance wrapper: %s", resultName(r));
    return r == VK_SUCCESS ? result : VK_NULL_HANDLE;
}

bool VulkanBridge::createSurface(unsigned width, unsigned height) {
    // Offscreen: the core (Dolphin) insists on a VkSurfaceKHR and queries it, but only ever "presents" through its
    // emulated swapchain (set_image), so the window is never drawn to. The GLSurfaceView's window stays EGL's.
    media_status_t status = AImageReader_newWithUsage(
        (int32_t) std::max(1u, width), (int32_t) std::max(1u, height), AIMAGE_FORMAT_RGBA_8888,
        AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT | AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE, 3, &imageReader);
    if (status != AMEDIA_OK || imageReader == nullptr) {
        LOGE("Vulkan bridge: AImageReader_newWithUsage failed: %d", (int) status);
        return false;
    }
    status = AImageReader_getWindow(imageReader, &window);
    if (status != AMEDIA_OK || window == nullptr) {
        LOGE("Vulkan bridge: AImageReader_getWindow failed: %d", (int) status);
        return false;
    }

    VkAndroidSurfaceCreateInfoKHR info { VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR };
    info.window = window;
    VkResult r = vk.createAndroidSurfaceKHR(instance, &info, nullptr, &surface);
    LOGI("Vulkan bridge: offscreen surface %ux%u (AImageReader): %s", width, height, resultName(r));
    return r == VK_SUCCESS;
}

bool VulkanBridge::deviceExtensionAvailable(const char* name) const {
    return contains(availableDeviceExtensions, name);
}

VkDevice VulkanBridge::createDeviceWrapper(VkPhysicalDevice physicalDevice, void* opaque,
                                           const VkDeviceCreateInfo* createInfo) {
    auto* self = static_cast<VulkanBridge*>(opaque);
    VkDeviceCreateInfo info = *createInfo;

    std::vector<const char*> names;
    for (uint32_t i = 0; i < createInfo->enabledExtensionCount; i++) names.push_back(createInfo->ppEnabledExtensionNames[i]);
    std::vector<std::string> ours;
    for (const char* name : FRONTEND_DEVICE_EXTENSIONS) {
        if (self->deviceExtensionAvailable(name)) ours.emplace_back(name);
        else LOGW("Vulkan bridge: device lacks %s", name);
    }
    mergeNames(names, ours);
    info.enabledExtensionCount = (uint32_t) names.size();
    info.ppEnabledExtensionNames = names.data();

    self->enabledDeviceExtensions.clear();
    std::string joined;
    for (const char* name : names) {
        self->enabledDeviceExtensions.emplace_back(name);
        joined += name;
        joined += ' ';
    }

    VkDevice result = VK_NULL_HANDLE;
    VkResult r = self->vk.createDevice(physicalDevice, &info, nullptr, &result);
    LOGI("Vulkan bridge: create_device wrapper (%u queue infos, family %u): %s; extensions: %s",
         info.queueCreateInfoCount, info.queueCreateInfoCount > 0 ? info.pQueueCreateInfos[0].queueFamilyIndex : 0,
         resultName(r), joined.c_str());
    return r == VK_SUCCESS ? result : VK_NULL_HANDLE;
}

bool VulkanBridge::createDevice() {
    uint32_t count = 0;
    vk.enumeratePhysicalDevices(instance, &count, nullptr);
    if (count == 0) {
        LOGE("Vulkan bridge: no Vulkan physical device");
        return false;
    }
    std::vector<VkPhysicalDevice> gpus(count);
    vk.enumeratePhysicalDevices(instance, &count, gpus.data());
    gpu = gpus[0];

    VkPhysicalDeviceProperties props {};
    vk.getPhysicalDeviceProperties(gpu, &props);
    LOGI("Vulkan bridge: GPU %s, Vulkan %u.%u.%u, driver 0x%08x", props.deviceName, VK_VERSION_MAJOR(props.apiVersion),
         VK_VERSION_MINOR(props.apiVersion), VK_VERSION_PATCH(props.apiVersion), props.driverVersion);

    uint32_t extCount = 0;
    vk.enumerateDeviceExtensionProperties(gpu, nullptr, &extCount, nullptr);
    std::vector<VkExtensionProperties> exts(extCount);
    vk.enumerateDeviceExtensionProperties(gpu, nullptr, &extCount, exts.data());
    availableDeviceExtensions.clear();
    for (const auto& e : exts) availableDeviceExtensions.emplace_back(e.extensionName);

    if (!deviceExtensionAvailable("VK_ANDROID_external_memory_android_hardware_buffer")) {
        LOGE("Vulkan bridge: device lacks VK_ANDROID_external_memory_android_hardware_buffer: no bridge");
        return false;
    }

    retro_vulkan_context context {};
    bool created = false;
    if (negotiation != nullptr && negotiation->interface_version >= 2 && negotiation->create_device2 != nullptr) {
        created = negotiation->create_device2(&context, instance, gpu, surface, vk.getInstanceProcAddr,
                                              &createDeviceWrapper, this);
        LOGI("Vulkan bridge: core create_device2: %d", created);
    } else if (negotiation != nullptr && negotiation->create_device != nullptr) {
        // v1: the core merges what we require into its own create info.
        std::vector<const char*> required;
        enabledDeviceExtensions.clear();
        for (const char* name : FRONTEND_DEVICE_EXTENSIONS) {
            if (deviceExtensionAvailable(name)) {
                required.push_back(name);
                enabledDeviceExtensions.emplace_back(name);
            }
        }
        // No features required, but as a struct, never null: ParaLLEl-RDP (Granite) copies *required_features unconditionally.
        const VkPhysicalDeviceFeatures noFeatures {};
        created = negotiation->create_device(&context, instance, gpu, surface, vk.getInstanceProcAddr,
                                             required.data(), (unsigned) required.size(), nullptr, 0, &noFeatures);
        LOGI("Vulkan bridge: core create_device (v1): %d", created);
    } else {
        LOGE("Vulkan bridge: the core gave no create_device: the bridge needs the core's own device");
        return false;
    }
    if (!created || context.device == VK_NULL_HANDLE) return false;

    gpu = context.gpu != VK_NULL_HANDLE ? context.gpu : gpu;
    device = context.device;
    queue = context.queue;
    queueFamily = context.queue_family_index;
    LOGI("Vulkan bridge: device %p queue %p family %u (presentation family %u)", (void*) device, (void*) queue, queueFamily,
         context.presentation_queue_family_index);

    vk.bindDevice(device);
    if (const char* missing = vk.missingDeviceFunction()) {
        LOGE("Vulkan bridge: the %s lacks %s", vk.custom ? "custom driver" : "system driver", missing);
        return false;
    }
    logDriver();
    queueFamilyForeign = contains(enabledDeviceExtensions, "VK_EXT_queue_family_foreign");

    if (vk.getAndroidHardwareBufferPropertiesANDROID == nullptr || !contains(enabledDeviceExtensions, "VK_ANDROID_external_memory_android_hardware_buffer")) {
        LOGE("Vulkan bridge: the core's device lacks AHardwareBuffer import (the core did not enable the frontend's required device extensions)");
        return false;
    }

    // GPU-side wait (sync_file into EGL) when the device can export one; a CPU fence wait otherwise.
    bool canExport = false;
    if (vk.getSemaphoreFdKHR != nullptr && contains(enabledDeviceExtensions, "VK_KHR_external_semaphore_fd")) {
        // The 1.1 core name, else the KHR one (bindInstance tries both).
        auto query = vk.getPhysicalDeviceExternalSemaphoreProperties;
        if (query != nullptr) {
            VkPhysicalDeviceExternalSemaphoreInfo semInfo { VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_SEMAPHORE_INFO };
            semInfo.handleType = VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT;
            VkExternalSemaphoreProperties semProps { VK_STRUCTURE_TYPE_EXTERNAL_SEMAPHORE_PROPERTIES };
            query(gpu, &semInfo, &semProps);
            canExport = (semProps.externalSemaphoreFeatures & VK_EXTERNAL_SEMAPHORE_FEATURE_EXPORTABLE_BIT) != 0;
        }
    }
    useSyncFd = canExport;
    LOGI("Vulkan bridge: sync=%s, queue_family_foreign=%d", useSyncFd ? "fd" : "cpu", queueFamilyForeign);
    return true;
}

void VulkanBridge::logDriver() {
    // driverName / driverInfo (Vulkan 1.2 or VK_KHR_driver_properties): tells Turnip from the stock driver, and a custom
    // driver whose hook silently fell back to the stock one.
    VkPhysicalDeviceProperties props {};
    vk.getPhysicalDeviceProperties(gpu, &props);
    const bool properties2 = instanceApiVersion >= VK_API_VERSION_1_1 ||
        contains(instanceExtensions, "VK_KHR_get_physical_device_properties2");
    const bool driverProperties = props.apiVersion >= VK_API_VERSION_1_2 ||
        contains(availableDeviceExtensions, "VK_KHR_driver_properties");
    if (!properties2 || !driverProperties || vk.getPhysicalDeviceProperties2 == nullptr) {
        LOGI("Vulkan bridge: %s driver (no driver properties)", vk.custom ? "custom" : "system");
        return;
    }
    VkPhysicalDeviceDriverProperties driver { VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DRIVER_PROPERTIES };
    VkPhysicalDeviceProperties2 props2 { VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2 };
    props2.pNext = &driver;
    vk.getPhysicalDeviceProperties2(gpu, &props2);
    LOGI("Vulkan bridge: %s driver: driverID %d, driverName '%s', driverInfo '%s'", vk.custom ? "custom" : "system",
         (int) driver.driverID, driver.driverName, driver.driverInfo);
}

bool VulkanBridge::createFrameResources() {
    VkCommandPoolCreateInfo poolInfo { VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO };
    poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    poolInfo.queueFamilyIndex = queueFamily;
    if (vk.createCommandPool(device, &poolInfo, nullptr, &commandPool) != VK_SUCCESS) return false;

    VkCommandBufferAllocateInfo alloc { VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO };
    alloc.commandPool = commandPool;
    alloc.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    alloc.commandBufferCount = SYNC_COUNT;
    if (vk.allocateCommandBuffers(device, &alloc, commandBuffers) != VK_SUCCESS) return false;

    for (unsigned i = 0; i < SYNC_COUNT; i++) {
        VkFenceCreateInfo fenceInfo { VK_STRUCTURE_TYPE_FENCE_CREATE_INFO };
        if (vk.createFence(device, &fenceInfo, nullptr, &fences[i]) != VK_SUCCESS) return false;
        fenceSubmitted[i] = false;
        if (useSyncFd) {
            VkExportSemaphoreCreateInfo exportInfo { VK_STRUCTURE_TYPE_EXPORT_SEMAPHORE_CREATE_INFO };
            exportInfo.handleTypes = VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT;
            VkSemaphoreCreateInfo semInfo { VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO };
            semInfo.pNext = &exportInfo;
            if (vk.createSemaphore(device, &semInfo, nullptr, &exportSemaphores[i]) != VK_SUCCESS) {
                LOGW("Vulkan bridge: exportable semaphore failed: falling back to sync=cpu");
                useSyncFd = false;
            }
        }
    }
    return true;
}

bool VulkanBridge::initialize(unsigned width, unsigned height) {
    if (isReady()) return true;
    surfaceWidth = width;
    surfaceHeight = height;

    // No fall-through to the system driver when a custom one fails: the host decides (and says so to the player).
    const bool customDriver = !driverDir.empty() || !driverLibrary.empty();
    const bool loaded = customDriver
        ? VulkanDispatch::loadCustom(vk, driverDir, driverLibrary, driverHookLibDir, driverTmpDir)
        : VulkanDispatch::loadSystem(vk);
    if (!loaded) {
        if (customDriver) LOGE("Vulkan bridge: custom driver %s could not be loaded", driverLibrary.c_str());
        else LOGE("Vulkan bridge: the system Vulkan loader could not be loaded");
        destroy();
        return false;
    }
    if (customDriver) LOGI("Vulkan bridge: loader for custom driver %s opened through libadrenotools", driverLibrary.c_str());

    if (!createInstance()) {
        LOGE("Vulkan bridge: initialization failed");
        destroy();
        return false;
    }
    // Every launch, also on a cached loader: the ICD is loaded by now (the loader opens it before an instance exists,
    // on the core's create_instance path too). Failing here is the same custom-driver start failure as any other.
    if (customDriver && !customDriverMapped(driverDir, driverLibrary, driverTmpDir)) {
        LOGE("Vulkan bridge: custom driver %s is not loaded (libadrenotools fell back to the system driver): failing "
             "the custom driver start", driverLibrary.c_str());
        destroy();
        return false;
    }
    if (!createSurface(width, height) || !createDevice() || !createFrameResources()) {
        LOGE("Vulkan bridge: initialization failed");
        destroy();
        return false;
    }

    iface = {};
    iface.interface_type = RETRO_HW_RENDER_INTERFACE_VULKAN;
    iface.interface_version = RETRO_HW_RENDER_INTERFACE_VULKAN_VERSION;
    iface.handle = this;
    iface.instance = instance;
    iface.gpu = gpu;
    iface.device = device;
    iface.get_device_proc_addr = vk.getDeviceProcAddr;
    iface.get_instance_proc_addr = vk.getInstanceProcAddr;
    iface.queue = queue;
    iface.queue_index = queueFamily;
    iface.set_image = &cbSetImage;
    iface.get_sync_index = &cbGetSyncIndex;
    iface.get_sync_index_mask = &cbGetSyncIndexMask;
    iface.set_command_buffers = &cbSetCommandBuffers;
    iface.wait_sync_index = &cbWaitSyncIndex;
    iface.lock_queue = &cbLockQueue;
    iface.unlock_queue = &cbUnlockQueue;
    iface.set_signal_semaphore = &cbSetSignalSemaphore;

    syncIndex = 0;
    slotIndex = 0;
    lastSlot = -1;
    bridgedAny = false;
    customSlotFailed = false;
    customSlotReported = false;
    {
        std::lock_guard<std::mutex> lock(frameMutex);
        haveImage = false;
    }
    loggedFirstFrame = false;
    LOGI("Vulkan bridge: bridge ready: surface %ux%u", width, height);
    return true;
}

bool VulkanBridge::allocateSlot(Slot& slot, unsigned width, unsigned height) {
    AHardwareBuffer_Desc desc {};
    desc.width = width;
    desc.height = height;
    desc.layers = 1;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE | AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT;
    if (AHardwareBuffer_allocate(&desc, &slot.buffer) != 0 || slot.buffer == nullptr) {
        LOGE("Vulkan bridge: AHardwareBuffer_allocate %ux%u failed", width, height);
        return false;
    }

    VkAndroidHardwareBufferFormatPropertiesANDROID formatProps {
        VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_FORMAT_PROPERTIES_ANDROID };
    VkAndroidHardwareBufferPropertiesANDROID props { VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID };
    props.pNext = &formatProps;
    VkResult r = vk.getAndroidHardwareBufferPropertiesANDROID(device, slot.buffer, &props);
    if (r != VK_SUCCESS) {
        LOGE("Vulkan bridge: vkGetAndroidHardwareBufferPropertiesANDROID: %s", resultName(r));
        return false;
    }
    if (formatProps.format != VK_FORMAT_R8G8B8A8_UNORM) {
        LOGW("Vulkan bridge: AHB reports Vulkan format %d (external format 0x%llx), expected R8G8B8A8_UNORM",
             (int) formatProps.format, (unsigned long long) formatProps.externalFormat);
    }

    VkExternalMemoryImageCreateInfo externalInfo { VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO };
    externalInfo.handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID;
    VkImageCreateInfo imageInfo { VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO };
    imageInfo.pNext = &externalInfo;
    imageInfo.imageType = VK_IMAGE_TYPE_2D;
    imageInfo.format = VK_FORMAT_R8G8B8A8_UNORM;
    imageInfo.extent = { width, height, 1 };
    imageInfo.mipLevels = 1;
    imageInfo.arrayLayers = 1;
    imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
    imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
    imageInfo.usage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    r = vk.createImage(device, &imageInfo, nullptr, &slot.image);
    if (r != VK_SUCCESS) {
        LOGE("Vulkan bridge: vkCreateImage (AHB): %s", resultName(r));
        return false;
    }

    VkImportAndroidHardwareBufferInfoANDROID importInfo { VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID };
    importInfo.buffer = slot.buffer;
    VkMemoryDedicatedAllocateInfo dedicated { VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO };
    dedicated.pNext = &importInfo;
    dedicated.image = slot.image;
    VkMemoryAllocateInfo allocInfo { VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO };
    allocInfo.pNext = &dedicated;
    allocInfo.allocationSize = props.allocationSize;
    allocInfo.memoryTypeIndex = 0;
    while (allocInfo.memoryTypeIndex < 32 && !(props.memoryTypeBits & (1u << allocInfo.memoryTypeIndex))) {
        allocInfo.memoryTypeIndex++;
    }
    r = vk.allocateMemory(device, &allocInfo, nullptr, &slot.memory);
    if (r != VK_SUCCESS) {
        LOGE("Vulkan bridge: vkAllocateMemory (AHB import): %s", resultName(r));
        return false;
    }
    r = vk.bindImageMemory(device, slot.image, slot.memory, 0);
    if (r != VK_SUCCESS) {
        LOGE("Vulkan bridge: vkBindImageMemory (AHB): %s", resultName(r));
        return false;
    }

    slot.width = width;
    slot.height = height;
    slot.id = nextSlotId++;
    slot.released = false;
    LOGI("Vulkan bridge: AHB slot %ux%u id %llu (allocation %llu bytes, memory types 0x%x)", width, height,
         (unsigned long long) slot.id, (unsigned long long) props.allocationSize, props.memoryTypeBits);
    return true;
}

void VulkanBridge::freeSlot(Slot& slot) {
    if (slot.glFence != nullptr && eglGetCurrentContext() != EGL_NO_CONTEXT) glDeleteSync(slot.glFence);
    slot.glFence = nullptr;
    if (slot.image != VK_NULL_HANDLE) vk.destroyImage(device, slot.image, nullptr);
    if (slot.memory != VK_NULL_HANDLE) vk.freeMemory(device, slot.memory, nullptr);
    if (slot.buffer != nullptr) AHardwareBuffer_release(slot.buffer);
    slot = Slot {};
}

bool VulkanBridge::bridgeFrame(unsigned width, unsigned height, Frame& out) {
    if (!isReady()) return false;
    {
        std::lock_guard<std::mutex> lock(frameMutex);
        if (!haveImage) return false;
    }

    // A swapchain core (Dolphin) is bounded by the surface its images were made for; any other by its max geometry, so
    // an upscaled or larger video mode is never cropped to the size it booted at.
    const bool bySurface = swapchainFrames || maxFrameWidth == 0 || maxFrameHeight == 0;
    FrameExtent extent = boundFrame(width, height, bySurface ? surfaceWidth : maxFrameWidth.load(),
                                    bySurface ? surfaceHeight : maxFrameHeight.load());
    unsigned w = extent.width;
    unsigned h = extent.height;

    Slot& slot = slots[slotIndex];
    if (slot.width != w || slot.height != h || slot.image == VK_NULL_HANDLE) {
        {
            std::lock_guard<std::mutex> lock(queueMutex);
            vk.queueWaitIdle(queue);
        }
        freeSlot(slot);
        if (!allocateSlot(slot, w, h)) {
            freeSlot(slot);
            // A custom driver that cannot share even the first frame with the GLES side: every frame would stay black.
            if (vk.custom && !bridgedAny && !customSlotReported) {
                LOGE("Vulkan bridge: the custom driver cannot share frames with the display: reported as a failed start");
                customSlotFailed = true;
                customSlotReported = true;
            }
            return false;
        }
    }

    // GL sampled this buffer two frames ago: that draw must be done before the copy overwrites it.
    if (slot.glFence != nullptr) {
        glClientWaitSync(slot.glFence, GL_SYNC_FLUSH_COMMANDS_BIT, 100 * 1000 * 1000);
        glDeleteSync(slot.glFence);
        slot.glFence = nullptr;
    }

    uint32_t index = syncIndex;
    {
        // The core may wait on this fence from its own thread (wait_sync_index): never reset under it.
        std::lock_guard<std::mutex> lock(fenceMutex[index]);
        if (fenceSubmitted[index]) {
            vk.waitForFences(device, 1, &fences[index], VK_TRUE, UINT64_MAX);
            fenceSubmitted[index] = false;
        }
        vk.resetFences(device, 1, &fences[index]);
    }

    retro_vulkan_image frameImage;
    // Swapped with the core-side lists: each keeps the other's capacity, so neither side allocates per frame.
    std::vector<VkSemaphore>& frameSemaphores = takenSemaphores;
    std::vector<VkCommandBuffer>& frameCommands = takenCommandBuffers;
    frameSemaphores.clear();
    frameCommands.clear();
    uint32_t frameSrcFamily;
    VkSemaphore frameSignal;
    {
        // PPSSPP sets these from its render thread, Azahar submits from a worker: one consistent frame. The set_image
        // semaphores are consumed once (libretro_vulkan.h: they are waited on once), as is the signal semaphore
        // (set_signal_semaphore: signalled by the next submit): both are taken here, never used by a later frame.
        std::lock_guard<std::mutex> lock(frameMutex);
        frameImage = image;
        frameSemaphores.swap(imageSemaphores);
        frameCommands.swap(pendingCommandBuffers);
        frameSrcFamily = imageSrcQueueFamily;
        frameSignal = signalSemaphore;
        signalSemaphore = VK_NULL_HANDLE;
    }

    VkCommandBuffer cmd = commandBuffers[index];
    vk.resetCommandBuffer(cmd, 0);
    VkCommandBufferBeginInfo begin { VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO };
    begin.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    vk.beginCommandBuffer(cmd, &begin);

    const uint32_t foreign = queueFamilyForeign ? VK_QUEUE_FAMILY_FOREIGN_EXT : VK_QUEUE_FAMILY_EXTERNAL;
    bool transferSource = frameSrcFamily != VK_QUEUE_FAMILY_IGNORED && frameSrcFamily != queueFamily;

    VkImageSubresourceRange srcRange = frameImage.create_info.subresourceRange;
    srcRange.levelCount = 1;
    srcRange.layerCount = 1;
    VkImageSubresourceRange dstRange { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 };

    VkImageMemoryBarrier pre[2] {};
    pre[0].sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    pre[0].srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT | VK_ACCESS_SHADER_WRITE_BIT;
    pre[0].dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
    pre[0].oldLayout = frameImage.image_layout;
    pre[0].newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    pre[0].srcQueueFamilyIndex = transferSource ? frameSrcFamily : VK_QUEUE_FAMILY_IGNORED;
    pre[0].dstQueueFamilyIndex = transferSource ? queueFamily : VK_QUEUE_FAMILY_IGNORED;
    pre[0].image = frameImage.create_info.image;
    pre[0].subresourceRange = srcRange;

    pre[1].sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    pre[1].srcAccessMask = 0;
    pre[1].dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    pre[1].oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;   // the whole image is overwritten
    pre[1].newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    pre[1].srcQueueFamilyIndex = slot.released ? foreign : VK_QUEUE_FAMILY_IGNORED;
    pre[1].dstQueueFamilyIndex = slot.released ? queueFamily : VK_QUEUE_FAMILY_IGNORED;
    pre[1].image = slot.image;
    pre[1].subresourceRange = dstRange;

    vk.cmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0,
                         0, nullptr, 0, nullptr, 2, pre);

    if (frameImage.create_info.format == VK_FORMAT_R8G8B8A8_UNORM) {
        VkImageCopy region {};
        region.srcSubresource = { VK_IMAGE_ASPECT_COLOR_BIT, srcRange.baseMipLevel, srcRange.baseArrayLayer, 1 };
        region.dstSubresource = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1 };
        region.extent = { w, h, 1 };
        vk.cmdCopyImage(cmd, frameImage.create_info.image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                       slot.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);
    } else {
        // BGRA8 (or anything else blittable): a blit converts the channel order.
        VkImageBlit blit {};
        blit.srcSubresource = { VK_IMAGE_ASPECT_COLOR_BIT, srcRange.baseMipLevel, srcRange.baseArrayLayer, 1 };
        blit.srcOffsets[1] = { (int32_t) w, (int32_t) h, 1 };
        blit.dstSubresource = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1 };
        blit.dstOffsets[1] = { (int32_t) w, (int32_t) h, 1 };
        vk.cmdBlitImage(cmd, frameImage.create_info.image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                       slot.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &blit, VK_FILTER_NEAREST);
    }

    VkImageMemoryBarrier post[2] {};
    post[0].sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    post[0].srcAccessMask = 0;
    post[0].dstAccessMask = 0;
    post[0].oldLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    post[0].newLayout = frameImage.image_layout;   // handed back as the core left it
    post[0].srcQueueFamilyIndex = transferSource ? queueFamily : VK_QUEUE_FAMILY_IGNORED;
    post[0].dstQueueFamilyIndex = transferSource ? frameSrcFamily : VK_QUEUE_FAMILY_IGNORED;
    post[0].image = frameImage.create_info.image;
    post[0].subresourceRange = srcRange;

    // Released to the foreign (GL) side: on Adreno this is where a compressed (UBWC) image is made readable outside
    // this queue. GENERAL is the layout an external consumer is assumed to read.
    post[1].sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    post[1].srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    post[1].dstAccessMask = 0;
    post[1].oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    post[1].newLayout = VK_IMAGE_LAYOUT_GENERAL;
    post[1].srcQueueFamilyIndex = queueFamily;
    post[1].dstQueueFamilyIndex = foreign;
    post[1].image = slot.image;
    post[1].subresourceRange = dstRange;

    vk.cmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT,
                         VK_PIPELINE_STAGE_ALL_COMMANDS_BIT | VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0,
                         0, nullptr, 0, nullptr, 2, post);
    vk.endCommandBuffer(cmd);

    frameCommands.push_back(cmd);   // after the core's own (set_command_buffers)

    submitWaitStages.assign(frameSemaphores.size(), VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
    VkSemaphore signals[2];
    uint32_t signalCount = 0;
    if (useSyncFd) signals[signalCount++] = exportSemaphores[index];
    if (frameSignal != VK_NULL_HANDLE) signals[signalCount++] = frameSignal;

    VkSubmitInfo submit { VK_STRUCTURE_TYPE_SUBMIT_INFO };
    submit.waitSemaphoreCount = (uint32_t) frameSemaphores.size();
    submit.pWaitSemaphores = frameSemaphores.data();
    submit.pWaitDstStageMask = submitWaitStages.data();
    submit.commandBufferCount = (uint32_t) frameCommands.size();
    submit.pCommandBuffers = frameCommands.data();
    submit.signalSemaphoreCount = signalCount;
    submit.pSignalSemaphores = signals;

    VkResult r;
    {
        std::lock_guard<std::mutex> lock(queueMutex);
        r = vk.queueSubmit(queue, 1, &submit, fences[index]);
    }
    if (r != VK_SUCCESS) {
        LOGE("Vulkan bridge: vkQueueSubmit: %s", resultName(r));
        return false;   // fenceSubmitted stays false: nobody waits on a fence that will never signal
    }
    {
        std::lock_guard<std::mutex> lock(fenceMutex[index]);
        fenceSubmitted[index] = true;
    }
    slot.released = true;

    out.syncFd = -1;
    if (useSyncFd) {
        VkSemaphoreGetFdInfoKHR getFd { VK_STRUCTURE_TYPE_SEMAPHORE_GET_FD_INFO_KHR };
        getFd.semaphore = exportSemaphores[index];
        getFd.handleType = VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_SYNC_FD_BIT;
        int fd = -1;
        r = vk.getSemaphoreFdKHR(device, &getFd, &fd);
        if (r != VK_SUCCESS) {
            LOGW("Vulkan bridge: vkGetSemaphoreFdKHR: %s; waiting on the CPU from now on", resultName(r));
            useSyncFd = false;
            vk.waitForFences(device, 1, &fences[index], VK_TRUE, UINT64_MAX);
        } else {
            out.syncFd = fd;
        }
    } else {
        vk.waitForFences(device, 1, &fences[index], VK_TRUE, UINT64_MAX);
    }

    out.buffer = slot.buffer;
    out.id = slot.id;
    out.width = w;
    out.height = h;

    if (!loggedFirstFrame) {
        loggedFirstFrame = true;
        LOGI("Vulkan bridge: first frame: core image %p format %d layout %d, frame %ux%u (reported %ux%u), %s",
             (void*) frameImage.create_info.image, (int) frameImage.create_info.format, (int) frameImage.image_layout, w, h,
             width, height, frameImage.create_info.format == VK_FORMAT_R8G8B8A8_UNORM ? "copy" : "blit");
    }

    lastSlot = (int) slotIndex;
    bridgedAny = true;
    slotIndex = (slotIndex + 1) % SYNC_COUNT;
    syncIndex = (index + 1) % SYNC_COUNT;

    return true;
}

bool VulkanBridge::takeCustomSlotFailure() {
    const bool failed = customSlotFailed;
    customSlotFailed = false;
    return failed;
}

void VulkanBridge::onGlContextLost() {
    for (auto& slot : slots) slot.glFence = nullptr;
}

void VulkanBridge::afterGlDraw() {
    if (lastSlot < 0) return;
    Slot& slot = slots[lastSlot];
    if (slot.glFence != nullptr) glDeleteSync(slot.glFence);
    slot.glFence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
}

void VulkanBridge::destroy() {
    if (device != VK_NULL_HANDLE) {
        if (vk.deviceWaitIdle != nullptr) {
            std::lock_guard<std::mutex> lock(queueMutex);
            vk.deviceWaitIdle(device);
        }
        for (auto& slot : slots) freeSlot(slot);
        for (unsigned i = 0; i < SYNC_COUNT; i++) {
            std::lock_guard<std::mutex> lock(fenceMutex[i]);
            if (fences[i] != VK_NULL_HANDLE) vk.destroyFence(device, fences[i], nullptr);
            if (exportSemaphores[i] != VK_NULL_HANDLE) vk.destroySemaphore(device, exportSemaphores[i], nullptr);
            fences[i] = VK_NULL_HANDLE;
            exportSemaphores[i] = VK_NULL_HANDLE;
            fenceSubmitted[i] = false;
        }
        if (commandPool != VK_NULL_HANDLE) vk.destroyCommandPool(device, commandPool, nullptr);
        commandPool = VK_NULL_HANDLE;
        // The core frees its auxiliary resources, then the device (the frontend's, per libretro_vulkan.h) goes.
        if (negotiation != nullptr && negotiation->destroy_device != nullptr) negotiation->destroy_device();
        // Null only when the driver lacked it (bindDevice): the device is then leaked rather than called through null.
        if (vk.destroyDevice != nullptr) vk.destroyDevice(device, nullptr);
        LOGI("Vulkan bridge: device destroyed");
    }
    device = VK_NULL_HANDLE;
    queue = VK_NULL_HANDLE;
    gpu = VK_NULL_HANDLE;
    if (surface != VK_NULL_HANDLE && instance != VK_NULL_HANDLE && vk.destroySurfaceKHR != nullptr) {
        vk.destroySurfaceKHR(instance, surface, nullptr);
    }
    surface = VK_NULL_HANDLE;
    if (instance != VK_NULL_HANDLE && ownsInstance && vk.destroyInstance != nullptr) vk.destroyInstance(instance, nullptr);
    instance = VK_NULL_HANDLE;
    if (imageReader != nullptr) AImageReader_delete(imageReader);   // also releases its window
    imageReader = nullptr;
    window = nullptr;

    negotiation = nullptr;
    {
        std::lock_guard<std::mutex> lock(frameMutex);
        haveImage = false;
        imageSemaphores.clear();
        pendingCommandBuffers.clear();
        signalSemaphore = VK_NULL_HANDLE;
    }
    enabledDeviceExtensions.clear();
    availableDeviceExtensions.clear();
    useSyncFd = false;
    lastSlot = -1;
    swapchainFrames = false;
    maxFrameWidth = 0;
    maxFrameHeight = 0;
    iface = {};
    // Last: nothing above may call through the table once it is reset (the system loader closed; a custom driver stays
    // loaded for the process, see VulkanDispatch::close).
    vk.close();
    driverDir.clear();
    driverLibrary.clear();
    driverHookLibDir.clear();
    driverTmpDir.clear();
}

// --- retro_hw_render_interface_vulkan ---

void VulkanBridge::cbSetImage(void* handle, const retro_vulkan_image* image, uint32_t numSemaphores,
                              const VkSemaphore* semaphores, uint32_t srcQueueFamily) {
    auto* self = static_cast<VulkanBridge*>(handle);
    std::lock_guard<std::mutex> lock(self->frameMutex);
    self->image = *image;
    self->haveImage = true;
    self->imageSemaphores.assign(semaphores, semaphores + numSemaphores);
    self->imageSrcQueueFamily = srcQueueFamily;
}

uint32_t VulkanBridge::cbGetSyncIndex(void* handle) {
    return static_cast<VulkanBridge*>(handle)->syncIndex;
}

uint32_t VulkanBridge::cbGetSyncIndexMask(void* handle) {
    return (1u << SYNC_COUNT) - 1;
}

void VulkanBridge::cbSetCommandBuffers(void* handle, uint32_t num, const VkCommandBuffer* cmd) {
    auto* self = static_cast<VulkanBridge*>(handle);
    std::lock_guard<std::mutex> lock(self->frameMutex);
    self->pendingCommandBuffers.insert(self->pendingCommandBuffers.end(), cmd, cmd + num);
}

void VulkanBridge::cbWaitSyncIndex(void* handle) {
    auto* self = static_cast<VulkanBridge*>(handle);
    uint32_t index = self->syncIndex;
    // Held across the GPU wait: bridgeFrame never takes queueMutex while holding it, so a core holding lock_queue is safe.
    std::lock_guard<std::mutex> lock(self->fenceMutex[index]);
    if (self->fenceSubmitted[index]) {
        self->vk.waitForFences(self->device, 1, &self->fences[index], VK_TRUE, UINT64_MAX);
    }
}

void VulkanBridge::cbLockQueue(void* handle) {
    static_cast<VulkanBridge*>(handle)->queueMutex.lock();
}

void VulkanBridge::cbUnlockQueue(void* handle) {
    static_cast<VulkanBridge*>(handle)->queueMutex.unlock();
}

void VulkanBridge::cbSetSignalSemaphore(void* handle, VkSemaphore semaphore) {
    auto* self = static_cast<VulkanBridge*>(handle);
    std::lock_guard<std::mutex> lock(self->frameMutex);
    self->signalSemaphore = semaphore;
}

}
