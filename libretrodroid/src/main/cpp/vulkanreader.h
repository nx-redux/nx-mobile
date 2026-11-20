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

#ifndef LIBRETRODROID_VULKANREADER_H
#define LIBRETRODROID_VULKANREADER_H

#include <android/hardware_buffer.h>
#include <media/NdkImageReader.h>

#include <cstdint>
#include <mutex>
#include <vector>

#include "vulkandispatch.h"
#include "vulkanhandoff.h"

namespace libretrodroid {

// What the GLES side gets for a frame that went through the reader.
struct ReaderFrame {
    AHardwareBuffer* buffer = nullptr;
    uint64_t id = 0;     // per buffer, see BufferIds
    int syncFd = -1;     // signalled when the driver is done with the buffer (owned by the receiver), -1: already done
    uint64_t oldestLiveId = 0;   // ids below it are of a swapchain that has been replaced
};

// The Reader hand-off: a swapchain on an AImageReader's window. The driver's own presentation code fills the window's
// buffers, so nothing has to be imported; the reader gives each presented buffer back as an AHardwareBuffer the GLES
// side samples. One thread drives it (the one that bridges frames).
class ReaderChain {
public:
    static constexpr unsigned SYNC_COUNT = 2;

    // (Re)makes the reader, its surface, the swapchain and the semaphores for [width] x [height]. The caller has idled
    // the queue. False leaves nothing behind.
    bool ensure(const VulkanDispatch& vk, VkInstance instance, VkPhysicalDevice gpu, VkDevice device, uint32_t queueFamily,
                unsigned width, unsigned height);
    bool matches(unsigned w, unsigned h) const { return chain != VK_NULL_HANDLE && width == w && height == h; }

    // The image this frame is copied into (left in PRESENT_SRC by the caller), and the semaphore its submit waits on.
    bool acquire(const VulkanDispatch& vk, VkDevice device, uint32_t syncIndex, VkImage* image, VkSemaphore* wait);
    // The semaphore that submit signals: present() waits on it.
    VkSemaphore presentSemaphore(uint32_t syncIndex) const { return presentable[syncIndex]; }
    // Presents the image acquire() gave for [syncIndex] and takes the newest buffer from the reader.
    bool present(const VulkanDispatch& vk, VkQueue queue, std::mutex& queueMutex, uint32_t syncIndex, ReaderFrame& out);

    // The GL draw that samples the newest buffer has been issued: older buffers can go back to the swapchain.
    void afterGlDraw() { held.markDrawn(); }
    // The caller has idled the queue.
    void destroy(const VulkanDispatch& vk, VkInstance instance, VkDevice device);

private:
    // More than the swapchain's minimum of 3 plus what HELD keeps with the GLES side.
    static constexpr int32_t READER_IMAGES = 5;
    static constexpr size_t HELD = 3;

    AImageReader* reader = nullptr;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkSwapchainKHR chain = VK_NULL_HANDLE;
    std::vector<VkImage> images;
    unsigned width = 0;
    unsigned height = 0;
    VkSemaphore acquired[SYNC_COUNT] {};
    VkSemaphore presentable[SYNC_COUNT] {};
    uint32_t imageIndex[SYNC_COUNT] {};
    HeldImages<AImage*> held { HELD };
    BufferIds ids;
    std::vector<AImage*> releasing;   // scratch for held's releases, kept for its capacity

    void releaseHeld();
};

// One cleared 64x64 frame through a chain of its own on [device] (queue family 0, created with VK_KHR_swapchain): true
// when the reader hands a buffer back. What a driver's pre-check runs when it cannot import a shared buffer.
bool probeReaderHandoff(const VulkanDispatch& vk, VkInstance instance, VkPhysicalDevice gpu, VkDevice device);

} // namespace libretrodroid

#endif // LIBRETRODROID_VULKANREADER_H
