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

#include "vulkanreader.h"

#include <android/native_window.h>
#include <unistd.h>

#include <algorithm>

#include "log.h"

namespace libretrodroid {

namespace {
constexpr uint64_t ACQUIRE_TIMEOUT_NS = 2000000000ull;
}

void ReaderChain::releaseHeld() {
    for (AImage* image : releasing) AImage_delete(image);
    releasing.clear();
}

bool ReaderChain::ensure(const VulkanDispatch& vk, VkInstance instance, VkPhysicalDevice gpu, VkDevice device,
                         uint32_t queueFamily, unsigned w, unsigned h) {
    destroy(vk, instance, device);
    if (!vk.hasSwapchain()) {
        LOGE("Vulkan reader: the device has no swapchain entry points");
        return false;
    }

    media_status_t status = AImageReader_newWithUsage((int32_t) w, (int32_t) h, AIMAGE_FORMAT_RGBA_8888,
                                                      AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE, READER_IMAGES, &reader);
    ANativeWindow* window = nullptr;
    if (status != AMEDIA_OK || reader == nullptr || AImageReader_getWindow(reader, &window) != AMEDIA_OK) {
        LOGE("Vulkan reader: AImageReader %ux%u: %d", w, h, (int) status);
        destroy(vk, instance, device);
        return false;
    }

    VkAndroidSurfaceCreateInfoKHR surfaceInfo { VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR };
    surfaceInfo.window = window;
    VkResult r = vk.createAndroidSurfaceKHR(instance, &surfaceInfo, nullptr, &surface);
    VkBool32 supported = VK_FALSE;
    if (r == VK_SUCCESS) r = vk.getPhysicalDeviceSurfaceSupportKHR(gpu, queueFamily, surface, &supported);
    VkSurfaceCapabilitiesKHR caps {};
    if (r == VK_SUCCESS && supported) r = vk.getPhysicalDeviceSurfaceCapabilitiesKHR(gpu, surface, &caps);
    if (r != VK_SUCCESS || !supported) {
        LOGE("Vulkan reader: surface on the reader: result %d, queue family %u can present: %d", (int) r, queueFamily,
             (int) supported);
        destroy(vk, instance, device);
        return false;
    }

    VkSwapchainCreateInfoKHR info { VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR };
    info.surface = surface;
    info.minImageCount = std::max(3u, caps.minImageCount);
    info.imageFormat = VK_FORMAT_R8G8B8A8_UNORM;
    info.imageColorSpace = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
    info.imageExtent = { w, h };
    info.imageArrayLayers = 1;
    info.imageUsage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    info.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    info.preTransform = VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR;
    info.compositeAlpha = VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR;
    info.presentMode = VK_PRESENT_MODE_FIFO_KHR;
    r = vk.createSwapchainKHR(device, &info, nullptr, &chain);
    if (r != VK_SUCCESS) {
        // Some drivers offer only an opaque window.
        info.compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
        r = vk.createSwapchainKHR(device, &info, nullptr, &chain);
    }
    uint32_t count = 0;
    if (r == VK_SUCCESS) r = vk.getSwapchainImagesKHR(device, chain, &count, nullptr);
    images.resize(count);
    if (r == VK_SUCCESS && count > 0) r = vk.getSwapchainImagesKHR(device, chain, &count, images.data());
    VkSemaphoreCreateInfo semaphoreInfo { VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO };
    for (unsigned i = 0; i < SYNC_COUNT && r == VK_SUCCESS; i++) {
        r = vk.createSemaphore(device, &semaphoreInfo, nullptr, &acquired[i]);
        if (r == VK_SUCCESS) r = vk.createSemaphore(device, &semaphoreInfo, nullptr, &presentable[i]);
    }
    if (r != VK_SUCCESS || count == 0) {
        LOGE("Vulkan reader: swapchain %ux%u: result %d, %u images", w, h, (int) r, count);
        destroy(vk, instance, device);
        return false;
    }
    width = w;
    height = h;
    LOGI("Vulkan reader: swapchain %ux%u on an image reader, %u images", w, h, count);
    return true;
}

bool ReaderChain::acquire(const VulkanDispatch& vk, VkDevice device, uint32_t syncIndex, VkImage* image,
                          VkSemaphore* wait) {
    if (chain == VK_NULL_HANDLE) return false;
    uint32_t index = 0;
    VkResult r = vk.acquireNextImageKHR(device, chain, ACQUIRE_TIMEOUT_NS, acquired[syncIndex], VK_NULL_HANDLE, &index);
    if (r != VK_SUCCESS && r != VK_SUBOPTIMAL_KHR) {
        LOGE("Vulkan reader: vkAcquireNextImageKHR: %d", (int) r);
        return false;
    }
    imageIndex[syncIndex] = index;
    *image = images[index];
    *wait = acquired[syncIndex];
    return true;
}

bool ReaderChain::present(const VulkanDispatch& vk, VkQueue queue, std::mutex& queueMutex, uint32_t syncIndex,
                          ReaderFrame& out) {
    VkPresentInfoKHR info { VK_STRUCTURE_TYPE_PRESENT_INFO_KHR };
    info.waitSemaphoreCount = 1;
    info.pWaitSemaphores = &presentable[syncIndex];
    info.swapchainCount = 1;
    info.pSwapchains = &chain;
    info.pImageIndices = &imageIndex[syncIndex];
    VkResult r;
    {
        std::lock_guard<std::mutex> lock(queueMutex);
        r = vk.queuePresentKHR(queue, &info);
    }
    if (r != VK_SUCCESS && r != VK_SUBOPTIMAL_KHR) {
        LOGE("Vulkan reader: vkQueuePresentKHR: %d", (int) r);
        return false;
    }

    AImage* image = nullptr;
    int fd = -1;
    media_status_t status = AImageReader_acquireLatestImageAsync(reader, &image, &fd);
    AHardwareBuffer* buffer = nullptr;
    if (status == AMEDIA_OK && image != nullptr) AImage_getHardwareBuffer(image, &buffer);
    if (buffer == nullptr) {
        LOGE("Vulkan reader: no buffer from the reader after a present: %d", (int) status);
        if (image != nullptr) AImage_delete(image);
        if (fd >= 0) close(fd);
        return false;
    }
    held.push(image, releasing);
    releaseHeld();
    out.buffer = buffer;
    out.id = ids.idFor(buffer);
    out.oldestLiveId = ids.firstLive();
    out.syncFd = fd;
    return true;
}

void ReaderChain::destroy(const VulkanDispatch& vk, VkInstance instance, VkDevice device) {
    held.drain(releasing);
    releaseHeld();
    ids.clear();
    for (unsigned i = 0; i < SYNC_COUNT; i++) {
        if (acquired[i] != VK_NULL_HANDLE) vk.destroySemaphore(device, acquired[i], nullptr);
        if (presentable[i] != VK_NULL_HANDLE) vk.destroySemaphore(device, presentable[i], nullptr);
        acquired[i] = VK_NULL_HANDLE;
        presentable[i] = VK_NULL_HANDLE;
    }
    if (chain != VK_NULL_HANDLE) vk.destroySwapchainKHR(device, chain, nullptr);
    if (surface != VK_NULL_HANDLE) vk.destroySurfaceKHR(instance, surface, nullptr);
    if (reader != nullptr) AImageReader_delete(reader);
    chain = VK_NULL_HANDLE;
    surface = VK_NULL_HANDLE;
    reader = nullptr;
    images.clear();
    width = 0;
    height = 0;
}

bool probeReaderHandoff(const VulkanDispatch& vk, VkInstance instance, VkPhysicalDevice gpu, VkDevice device) {
    if (!vk.hasSwapchain() || vk.getDeviceQueue == nullptr) return false;
    const uint32_t family = 0;
    VkQueue queue = VK_NULL_HANDLE;
    vk.getDeviceQueue(device, family, 0, &queue);

    ReaderChain chain;
    VkCommandPool pool = VK_NULL_HANDLE;
    bool passed = false;
    [&] {
        if (queue == VK_NULL_HANDLE || !chain.ensure(vk, instance, gpu, device, family, 64, 64)) return;
        VkCommandPoolCreateInfo poolInfo { VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO };
        poolInfo.queueFamilyIndex = family;
        if (vk.createCommandPool(device, &poolInfo, nullptr, &pool) != VK_SUCCESS) { pool = VK_NULL_HANDLE; return; }
        VkCommandBufferAllocateInfo allocInfo { VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO };
        allocInfo.commandPool = pool;
        allocInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        allocInfo.commandBufferCount = 1;
        VkCommandBuffer cmd = VK_NULL_HANDLE;
        if (vk.allocateCommandBuffers(device, &allocInfo, &cmd) != VK_SUCCESS) return;

        VkImage image = VK_NULL_HANDLE;
        VkSemaphore wait = VK_NULL_HANDLE;
        if (!chain.acquire(vk, device, 0, &image, &wait)) return;

        VkCommandBufferBeginInfo begin { VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO };
        vk.beginCommandBuffer(cmd, &begin);
        VkImageSubresourceRange range { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 };
        VkImageMemoryBarrier barrier { VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER };
        barrier.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        barrier.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        barrier.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        barrier.image = image;
        barrier.subresourceRange = range;
        vk.cmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0,
                              nullptr, 1, &barrier);
        VkClearColorValue color {};
        vk.cmdClearColorImage(cmd, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, &color, 1, &range);
        barrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        barrier.dstAccessMask = 0;
        barrier.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        barrier.newLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        vk.cmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, 0, nullptr, 0,
                              nullptr, 1, &barrier);
        vk.endCommandBuffer(cmd);

        const VkPipelineStageFlags stage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        const VkSemaphore signal = chain.presentSemaphore(0);
        VkSubmitInfo submit { VK_STRUCTURE_TYPE_SUBMIT_INFO };
        submit.waitSemaphoreCount = 1;
        submit.pWaitSemaphores = &wait;
        submit.pWaitDstStageMask = &stage;
        submit.commandBufferCount = 1;
        submit.pCommandBuffers = &cmd;
        submit.signalSemaphoreCount = 1;
        submit.pSignalSemaphores = &signal;
        if (vk.queueSubmit(queue, 1, &submit, VK_NULL_HANDLE) != VK_SUCCESS) return;

        std::mutex queueMutex;
        ReaderFrame frame;
        if (!chain.present(vk, queue, queueMutex, 0, frame)) return;
        if (frame.syncFd >= 0) close(frame.syncFd);
        passed = true;
    }();

    if (queue != VK_NULL_HANDLE) vk.queueWaitIdle(queue);
    chain.destroy(vk, instance, device);
    if (pool != VK_NULL_HANDLE) vk.destroyCommandPool(device, pool, nullptr);
    return passed;
}

} // namespace libretrodroid
