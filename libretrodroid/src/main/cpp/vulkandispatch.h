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

// The Vulkan entry points the bridge calls, as a table of function pointers: from the system loader (dlopen of
// libvulkan.so) or from a custom driver (Turnip) that libadrenotools loads into its own linker namespace. liblibretrodroid
// does not link libvulkan, so a vk* call that bypasses this table fails to link.

#ifndef LIBRETRODROID_VULKANDISPATCH_H
#define LIBRETRODROID_VULKANDISPATCH_H

#ifndef VK_USE_PLATFORM_ANDROID_KHR
#define VK_USE_PLATFORM_ANDROID_KHR 1
#endif
#include <vulkan/vulkan.h>

#include <string>

namespace libretrodroid {

// X(member, vkName). Global: through getInstanceProcAddr(VK_NULL_HANDLE, ...) right after the loader is opened.
#define LIBRETRODROID_VK_GLOBAL(X) \
    X(createInstance, vkCreateInstance) \
    X(enumerateInstanceExtensionProperties, vkEnumerateInstanceExtensionProperties)
// Global, may be null (Android 8.x loaders are 1.0).
#define LIBRETRODROID_VK_GLOBAL_OPTIONAL(X) \
    X(enumerateInstanceVersion, vkEnumerateInstanceVersion)
// Instance-level, core 1.0: bindInstance().
#define LIBRETRODROID_VK_INSTANCE(X) \
    X(destroyInstance, vkDestroyInstance) \
    X(enumeratePhysicalDevices, vkEnumeratePhysicalDevices) \
    X(enumerateDeviceExtensionProperties, vkEnumerateDeviceExtensionProperties) \
    X(getPhysicalDeviceProperties, vkGetPhysicalDeviceProperties) \
    X(createDevice, vkCreateDevice) \
    X(getDeviceProcAddr, vkGetDeviceProcAddr)
// Instance-level, VK_KHR_surface / VK_KHR_android_surface: bindInstance(). A loader returns null for these on an instance
// that did not enable the two extensions (the support probe's), so only an instance created with them checks them.
#define LIBRETRODROID_VK_SURFACE(X) \
    X(createAndroidSurfaceKHR, vkCreateAndroidSurfaceKHR) \
    X(destroySurfaceKHR, vkDestroySurfaceKHR)
// Instance-level, may be null: 1.1 core or their KHR extension names (tried second).
#define LIBRETRODROID_VK_INSTANCE_OPTIONAL(X) \
    X(getPhysicalDeviceExternalSemaphoreProperties, vkGetPhysicalDeviceExternalSemaphoreProperties) \
    X(getPhysicalDeviceProperties2, vkGetPhysicalDeviceProperties2)
// Device-level: bindDevice().
#define LIBRETRODROID_VK_DEVICE(X) \
    X(destroyDevice, vkDestroyDevice) \
    X(deviceWaitIdle, vkDeviceWaitIdle) \
    X(queueWaitIdle, vkQueueWaitIdle) \
    X(queueSubmit, vkQueueSubmit) \
    X(createCommandPool, vkCreateCommandPool) \
    X(destroyCommandPool, vkDestroyCommandPool) \
    X(allocateCommandBuffers, vkAllocateCommandBuffers) \
    X(beginCommandBuffer, vkBeginCommandBuffer) \
    X(endCommandBuffer, vkEndCommandBuffer) \
    X(resetCommandBuffer, vkResetCommandBuffer) \
    X(cmdPipelineBarrier, vkCmdPipelineBarrier) \
    X(cmdCopyImage, vkCmdCopyImage) \
    X(cmdBlitImage, vkCmdBlitImage) \
    X(createImage, vkCreateImage) \
    X(destroyImage, vkDestroyImage) \
    X(allocateMemory, vkAllocateMemory) \
    X(freeMemory, vkFreeMemory) \
    X(bindImageMemory, vkBindImageMemory) \
    X(createFence, vkCreateFence) \
    X(destroyFence, vkDestroyFence) \
    X(resetFences, vkResetFences) \
    X(waitForFences, vkWaitForFences) \
    X(createSemaphore, vkCreateSemaphore) \
    X(destroySemaphore, vkDestroySemaphore)
// Device-level, may be null: the extensions the bridge checks for itself.
#define LIBRETRODROID_VK_DEVICE_OPTIONAL(X) \
    X(getAndroidHardwareBufferPropertiesANDROID, vkGetAndroidHardwareBufferPropertiesANDROID) \
    X(getSemaphoreFdKHR, vkGetSemaphoreFdKHR)

struct VulkanDispatch {
    PFN_vkGetInstanceProcAddr getInstanceProcAddr = nullptr;
#define LIBRETRODROID_VK_MEMBER(member, name) PFN_##name member = nullptr;
    LIBRETRODROID_VK_GLOBAL(LIBRETRODROID_VK_MEMBER)
    LIBRETRODROID_VK_GLOBAL_OPTIONAL(LIBRETRODROID_VK_MEMBER)
    LIBRETRODROID_VK_INSTANCE(LIBRETRODROID_VK_MEMBER)
    LIBRETRODROID_VK_SURFACE(LIBRETRODROID_VK_MEMBER)
    LIBRETRODROID_VK_INSTANCE_OPTIONAL(LIBRETRODROID_VK_MEMBER)
    LIBRETRODROID_VK_DEVICE(LIBRETRODROID_VK_MEMBER)
    LIBRETRODROID_VK_DEVICE_OPTIONAL(LIBRETRODROID_VK_MEMBER)
#undef LIBRETRODROID_VK_MEMBER

    // Loaded through libadrenotools (a custom driver), not the system loader.
    bool custom = false;

    // dlopen("libvulkan.so"): the system loader. False (out left closed) when it or a global entry point is missing.
    static bool loadSystem(VulkanDispatch& out);
    // libadrenotools: [library] from [driverDir], hooked through the hook libraries in [hookLibDir] (the app's
    // nativeLibraryDir); [tmpDir] holds patched libraries below API 29 (memfd from 29). Directories may omit the trailing
    // slash. A driver this process loaded before is not opened again: its cached handle is reused. False (out left
    // closed, the reason logged) when anything fails: the caller decides what happens next.
    static bool loadCustom(VulkanDispatch& out, const std::string& driverDir, const std::string& library,
                           const std::string& hookLibDir, const std::string& tmpDir);

    void bindInstance(VkInstance instance);   // instance-level pointers via getInstanceProcAddr
    void bindDevice(VkDevice device);         // device-level pointers via getDeviceProcAddr
    // The first required entry point still null after bindInstance / bindDevice (nullptr: all there). The surface pair
    // is checked only by missingSurfaceFunction(): on an instance created with the surface extensions enabled.
    const char* missingInstanceFunction() const;
    const char* missingSurfaceFunction() const;
    const char* missingDeviceFunction() const;
    // Every pointer back to null. dlcloses the system loader; a custom driver's handle stays loaded for the life of the
    // process (cached for its next launch): libadrenotools drivers are not written to be unloaded.
    void close();

private:
    void* handle = nullptr;
    bool ownsHandle = false;                  // true for the system loader only: close() may dlclose it
    bool bindGlobal();
};

}

#endif //LIBRETRODROID_VULKANDISPATCH_H
