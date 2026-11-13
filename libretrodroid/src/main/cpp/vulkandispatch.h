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
// Instance-level: bindInstance().
#define LIBRETRODROID_VK_INSTANCE(X) \
    X(destroyInstance, vkDestroyInstance) \
    X(enumeratePhysicalDevices, vkEnumeratePhysicalDevices) \
    X(enumerateDeviceExtensionProperties, vkEnumerateDeviceExtensionProperties) \
    X(getPhysicalDeviceProperties, vkGetPhysicalDeviceProperties) \
    X(createDevice, vkCreateDevice) \
    X(getDeviceProcAddr, vkGetDeviceProcAddr) \
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
    // slash. False (out left closed, the reason logged) when anything fails: the caller decides what happens next.
    static bool loadCustom(VulkanDispatch& out, const std::string& driverDir, const std::string& library,
                           const std::string& hookLibDir, const std::string& tmpDir);

    void bindInstance(VkInstance instance);   // instance-level pointers via getInstanceProcAddr
    void bindDevice(VkDevice device);         // device-level pointers via getDeviceProcAddr
    // The first required entry point still null after bindInstance / bindDevice (nullptr: all there).
    const char* missingInstanceFunction() const;
    const char* missingDeviceFunction() const;
    void close();                             // dlclose the handle; every pointer back to null

private:
    void* handle = nullptr;
    bool bindGlobal();
};

}

#endif //LIBRETRODROID_VULKANDISPATCH_H
