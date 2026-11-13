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

// A Vulkan context for a libretro core, each finished frame copied into an AHardwareBuffer that the runner's GLES
// presentation samples as an ordinary GL texture, so shaders, overlays and screen regions stay in GL. Everything runs on the GL
// thread: Dolphin's GPU loop (and so its queue submits and its video_cb) runs inside retro_run there.

#ifndef LIBRETRODROID_VULKANBRIDGE_H
#define LIBRETRODROID_VULKANBRIDGE_H

#ifndef VK_USE_PLATFORM_ANDROID_KHR
#define VK_USE_PLATFORM_ANDROID_KHR 1
#endif
#include <vulkan/vulkan.h>
#include <android/hardware_buffer.h>
#include <GLES3/gl3.h>

#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

#include "libretro.h"
#include "libretro_vulkan.h"
#include "vulkandispatch.h"
#include "vulkanframe.h"

struct AImageReader;
struct ANativeWindow;

namespace libretrodroid {

class VulkanBridge {
public:
    static VulkanBridge& get();

    // Whether this device can run the bridge: a Vulkan loader and a GPU with AHardwareBuffer import. Checked before
    // Vulkan is offered, so a core that picks Vulkan can always get it. Cached after the first call.
    static bool isSupported();

    // Before any core is loaded: whether the custom driver [library] in [dir] (arguments as setDriver's) can start the
    // bridge. On its own table and the process-wide cached loader: instance with the bridge's instance extensions, the
    // /proc/self/maps check, a device with the bridge's device extensions, and one AHardwareBuffer allocated and imported
    // exactly as a frame slot is. Everything it made is destroyed; the loader stays open for the launch. False: the
    // reason logged. Any thread; never touches the bridge's own state.
    static bool probeCustomDriver(const std::string& dir, const std::string& library, const std::string& hookLibDir,
                                  const std::string& tmpDir);

    // From RETRO_ENVIRONMENT_SET_HW_RENDER_CONTEXT_NEGOTIATION_INTERFACE (the core keeps the struct alive).
    void setNegotiation(const retro_hw_render_context_negotiation_interface_vulkan* negotiation);

    // GL thread, before the core's context_reset: instance, offscreen surface, device (through the core's
    // create_device2/create_device). [surfaceWidth]x[surfaceHeight]: the size the core sizes its swapchain to (Dolphin:
    // its base geometry), also the largest region read from its images.
    bool initialize(unsigned surfaceWidth, unsigned surfaceHeight);
    bool isReady() const { return device != VK_NULL_HANDLE; }

    const retro_hw_render_interface_vulkan* getInterface() const { return &iface; }

    struct Frame {
        AHardwareBuffer* buffer = nullptr;
        uint64_t id = 0;        // changes whenever the buffer is (re)allocated: the GL side re-imports on a new id
        unsigned width = 0;
        unsigned height = 0;
        int syncFd = -1;        // sync_file signalled when the copy is done (owned by the receiver), -1 = already done
    };

    // In the core's video_cb (RETRO_HW_FRAME_BUFFER_VALID): copies the image from set_image into the next buffer.
    bool bridgeFrame(unsigned width, unsigned height, Frame& out);

    // A custom driver could not create or import the shared AHardwareBuffer before any frame was bridged (Mali's
    // malisx2: INVALID_EXTERNAL_HANDLE), so nothing it renders can reach the screen: the driver could not start. True
    // once, after the bridgeFrame that failed; the system driver never reports it (its failed frame is skipped, as before).
    bool takeCustomSlotFailure();

    // After the GL draw that sampled the last bridged buffer: fences it so the copy into it two frames later waits.
    void afterGlDraw();

    // A new EGL context replaced the one the GL fences were made in: forgets them (they died with it) without a GL call.
    void onGlContextLost();

    // After the core's context_destroy and retro_deinit (or a failed load). Safe to call when not initialized.
    void destroy();

    // The core's largest frame (retro_game_geometry max_width / max_height): at load and on SET_SYSTEM_AV_INFO.
    void setMaxFrame(unsigned width, unsigned height) { maxFrameWidth = width; maxFrameHeight = height; }
    // The core renders into a swapchain on the bridge's surface (Dolphin): frames are bounded by the surface instead.
    void setSwapchainFrames(bool value) { swapchainFrames = value; }
    // The next initialize() loads [library] from [dir] through libadrenotools instead of the system driver; empty [dir]
    // and [library]: the system driver. [hookLibDir]: the app's nativeLibraryDir; [tmpDir]: patched libraries below
    // API 29. One-shot like setSwapchainFrames: destroy() forgets it. A custom driver that fails to load fails
    // initialize(); the host decides whether to retry on the system driver.
    void setDriver(const std::string& dir, const std::string& library, const std::string& hookLibDir,
                   const std::string& tmpDir);

private:
    std::atomic<unsigned> maxFrameWidth { 0 };
    std::atomic<unsigned> maxFrameHeight { 0 };
    bool swapchainFrames = false;
    std::string driverDir;
    std::string driverLibrary;
    std::string driverHookLibDir;
    std::string driverTmpDir;

    VulkanBridge() = default;

    // Every Vulkan call the bridge makes, from the loader this session runs on (initialize() to destroy()).
    VulkanDispatch vk;

    struct Slot {
        AHardwareBuffer* buffer = nullptr;
        VkImage image = VK_NULL_HANDLE;
        VkDeviceMemory memory = VK_NULL_HANDLE;
        unsigned width = 0;
        unsigned height = 0;
        uint64_t id = 0;
        bool released = false;   // released to the foreign queue family by an earlier copy
        GLsync glFence = nullptr;
    };

    static constexpr unsigned SYNC_COUNT = 2;

    bool createInstance();
    bool bindInstance();   // the instance-level table for [instance]; false when a required entry point is missing
    void logDriver();
    bool createSurface(unsigned width, unsigned height);
    bool createDevice();
    bool createFrameResources();
    bool allocateSlot(Slot& slot, unsigned width, unsigned height);
    void freeSlot(Slot& slot);
    bool deviceExtensionAvailable(const char* name) const;

    static VkInstance createInstanceWrapper(void* opaque, const VkInstanceCreateInfo* createInfo);
    static VkDevice createDeviceWrapper(VkPhysicalDevice gpu, void* opaque, const VkDeviceCreateInfo* createInfo);

    // retro_hw_render_interface_vulkan callbacks (handle = this)
    static void cbSetImage(void* handle, const retro_vulkan_image* image, uint32_t numSemaphores,
                           const VkSemaphore* semaphores, uint32_t srcQueueFamily);
    static uint32_t cbGetSyncIndex(void* handle);
    static uint32_t cbGetSyncIndexMask(void* handle);
    static void cbSetCommandBuffers(void* handle, uint32_t num, const VkCommandBuffer* cmd);
    static void cbWaitSyncIndex(void* handle);
    static void cbLockQueue(void* handle);
    static void cbUnlockQueue(void* handle);
    static void cbSetSignalSemaphore(void* handle, VkSemaphore semaphore);

    const retro_hw_render_context_negotiation_interface_vulkan* negotiation = nullptr;

    VkInstance instance = VK_NULL_HANDLE;
    bool ownsInstance = true;
    uint32_t instanceApiVersion = VK_API_VERSION_1_0;
    std::vector<std::string> instanceExtensions;
    VkPhysicalDevice gpu = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkQueue queue = VK_NULL_HANDLE;
    uint32_t queueFamily = 0;
    std::vector<std::string> availableDeviceExtensions;
    std::vector<std::string> enabledDeviceExtensions;   // what the device was created with (core's + ours)

    AImageReader* imageReader = nullptr;
    ANativeWindow* window = nullptr;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    unsigned surfaceWidth = 0;
    unsigned surfaceHeight = 0;

    retro_hw_render_interface_vulkan iface {};

    // Frame state set by the core, from any of its threads: guarded by frameMutex.
    std::mutex frameMutex;
    bool haveImage = false;
    retro_vulkan_image image {};
    std::vector<VkSemaphore> imageSemaphores;
    uint32_t imageSrcQueueFamily = VK_QUEUE_FAMILY_IGNORED;
    std::vector<VkCommandBuffer> pendingCommandBuffers;
    VkSemaphore signalSemaphore = VK_NULL_HANDLE;
    std::mutex queueMutex;

    // bridgeFrame's per-frame lists (render thread only), kept for their capacity.
    std::vector<VkSemaphore> takenSemaphores;
    std::vector<VkCommandBuffer> takenCommandBuffers;
    std::vector<VkPipelineStageFlags> submitWaitStages;

    // Frontend frame resources, one per sync index.
    VkCommandPool commandPool = VK_NULL_HANDLE;
    VkCommandBuffer commandBuffers[SYNC_COUNT] {};
    VkFence fences[SYNC_COUNT] {};
    bool fenceSubmitted[SYNC_COUNT] {};
    // Guards fences[i] (reset) and fenceSubmitted[i] against the core's wait_sync_index thread.
    // Lock order: queueMutex (held by the core via lock_queue) -> fenceMutex / frameMutex is allowed; the bridge never
    // takes queueMutex while holding fenceMutex or frameMutex.
    std::mutex fenceMutex[SYNC_COUNT];
    VkSemaphore exportSemaphores[SYNC_COUNT] {};
    std::atomic<uint32_t> syncIndex { 0 };

    Slot slots[SYNC_COUNT];
    unsigned slotIndex = 0;
    int lastSlot = -1;
    uint64_t nextSlotId = 1;
    bool bridgedAny = false;          // a frame has reached a slot since initialize()
    bool customSlotFailed = false;    // takeCustomSlotFailure's report, pending
    bool customSlotReported = false;  // reported once per initialize()

    bool useSyncFd = false;
    bool queueFamilyForeign = false;
    bool loggedFirstFrame = false;
};

}

#endif //LIBRETRODROID_VULKANBRIDGE_H
