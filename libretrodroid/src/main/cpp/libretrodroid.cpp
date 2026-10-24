/*
 *     Copyright (C) 2019  Filippo Scognamiglio
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

#include <jni.h>

#include <EGL/egl.h>

#include <cmath>
#include <string>
#include <utility>
#include <vector>
#include <unordered_set>

#include "gldebug.h"
#include "libretrodroid.h"
#include "utils/libretrodroidexception.h"
#include "log.h"
#include "core.h"
#include "audio.h"
#include "video.h"
#include "renderers/renderer.h"
#include "fpssync.h"
#include "input.h"
#include "rumble.h"
#include "shadermanager.h"
#include "utils/javautils.h"
#include "environment.h"
#include "corelog.h"
#include "renderers/es3/framebufferrenderer.h"
#include "renderers/es2/imagerendereres2.h"
#include "renderers/es3/imagerendereres3.h"
#include "utils/utils.h"
#include "utils/rect.h"
#include "errorcodes.h"
#include "vfs/vfs.h"
#include "achievements/achievements.h"
#include "teardowncontext.h"
#include "sharedcontexts.h"
#include "netplay/netplayhooks.h"
#include "netplay/linkhooks.h"
#include "sensorstate.h"
#include "vulkanbridge.h"

namespace libretrodroid {

uintptr_t LibretroDroid::callback_get_current_framebuffer() {
    return LibretroDroid::getInstance().handleGetCurrentFrameBuffer();
}

void LibretroDroid::callback_hw_video_refresh(
    const void *data,
    unsigned width,
    unsigned height,
    size_t pitch
) {
    LOGD("hw video refresh callback called %i %i", width, height);
    LibretroDroid::getInstance().handleVideoRefresh(data, width, height, pitch);
}

void LibretroDroid::callback_audio_sample(int16_t left, int16_t right) {
    LOGE("callback audio sample (left, right) has been called");
}

size_t LibretroDroid::callback_set_audio_sample_batch(const int16_t *data, size_t frames) {
    return LibretroDroid::getInstance().handleAudioCallback(data, frames);
}

void LibretroDroid::callback_retro_set_input_poll() {
    // Do nothing in here...
}

int16_t LibretroDroid::callback_set_input_state(
    unsigned int port,
    unsigned int device,
    unsigned int index,
    unsigned int id
) {
    return LibretroDroid::getInstance().handleSetInputState(port, device, index, id);
}

void LibretroDroid::updateAudioSampleRateMultiplier() {
    if (audio) {
        audio->setPlaybackSpeed(frameSpeed.load());
        audio->setFastForwardSpeed(fastForwardPlaybackSpeed.load());
    }
}

void LibretroDroid::updateFastForwardAudioSpeed() {
    // Audible FF plays at the game's achieved speed. Audio combines it with the pause speed, so a pause (0) set on the UI
    // thread mid-step still wins: the GL thread never writes the pause speed.
    if (audio) audio->setFastForwardSpeed(fastForwardPlaybackSpeed.load());
}

// TODO... Do we really need this?
void LibretroDroid::resetGlobalVariables() {
    core = nullptr;
    audio = nullptr;
    video = nullptr;
    fpsSync = nullptr;
    // Not dropped here (UI thread): the GL thread may be reading it. Its next access starts from a fresh state.
    inputResetPending = true;
    rumble = nullptr;
}

int LibretroDroid::availableDisks() {
    return Environment::getInstance().getRetroDiskControlCallback() != nullptr
           ? Environment::getInstance().getRetroDiskControlCallback()->get_num_images()
           : 0;
}

int LibretroDroid::currentDisk() {
    return Environment::getInstance().getRetroDiskControlCallback() != nullptr
           ? Environment::getInstance().getRetroDiskControlCallback()->get_image_index()
           : 0;
}

void LibretroDroid::changeDisk(unsigned int index) {
    // The rewind history is cleared with the swap; the lock keeps that off a running step's capture.
    std::lock_guard<std::mutex> lock(coreLock);

    if (Environment::getInstance().getRetroDiskControlCallback() == nullptr) {
        LOGE("Cannot swap disk. This platform does not support it.");
        return;
    }

    if (index < 0 || index >= Environment::getInstance().getRetroDiskControlCallback()->get_num_images()) {
        LOGE("Requested image index is not valid.");
        return;
    }

    if (Environment::getInstance().getRetroDiskControlCallback()->get_image_index() != index) {
        Environment::getInstance().getRetroDiskControlCallback()->set_eject_state(true);
        Environment::getInstance().getRetroDiskControlCallback()->set_image_index((unsigned) index);
        Environment::getInstance().getRetroDiskControlCallback()->set_eject_state(false);
        // Rewinding past the swap would restore states that expect the other disc.
        rewind.clear();
    }
}

void LibretroDroid::updateVariable(const Variable& variable) {
    Environment::getInstance().updateVariable(variable.key, variable.value);
}

std::vector<Variable> LibretroDroid::getVariables() {
    return Environment::getInstance().getVariables();
}

std::vector<std::vector<struct Controller>> LibretroDroid::getControllers() {
    return Environment::getInstance().getControllers();
}

void LibretroDroid::setControllerType(unsigned int port, unsigned int type) {
    std::lock_guard<std::mutex> lock(coreLock);

    if (core == nullptr) return;   // called on the UI thread: may race a destroy
    core->retro_set_controller_port_device(port, type);
}

void LibretroDroid::setStickyPointer(unsigned int port, bool sticky) {
    if (port < 4) stickyPointers[port] = sticky;
    currentInput()->setStickyPointer(port, sticky);
}

void LibretroDroid::setPointerVelocity(unsigned int port, float vx, float vy) {
    currentInput()->setPointerVelocity(port, vx, vy);
}

bool LibretroDroid::unserializeState(int8_t *data, size_t size) {
    std::lock_guard<std::mutex> lock(coreLock);

    if (core == nullptr) return false;
    // A core may run emulation (and GL) inside it (Azahar drains its async work with RunLoop): the paused view's context.
    TeardownContext context;
    if (!core->retro_unserialize(data, size)) return false;
    // The history led to the state just replaced: rewinding must not step back into it.
    rewind.clear();
    return true;
}

jboolean LibretroDroid::unserializeMemory(unsigned id, int8_t* data, size_t size) {
    std::lock_guard<std::mutex> lock(coreLock);

    if (core == nullptr) return false;

    size_t memorySize = core->retro_get_memory_size(id);
    void *memory = core->retro_get_memory_data(id);

    if (memory == nullptr) {
        LOGE("Cannot load memory %u: nullptr in retro_get_memory_data", id);
        return false;
    }

    // As minarch's SRAM_read: at most the core's size is read; the rest of a longer file (a VBA/mGBA clock footer after a
    // Game Boy save) is ignored, and the next write is the core's size.
    if (size > memorySize) {
        LOGI("Memory %u: file is %zu bytes, the core's is %zu; loading the first %zu", id, size, memorySize, memorySize);
        size = memorySize;
    }

    memcpy(memory, data, size);

    return true;
}

std::pair<int8_t*, size_t> LibretroDroid::serializeMemory(unsigned id) {
    std::lock_guard<std::mutex> lock(coreLock);

    // Empty (nullptr, 0) when there is no core (quit racing the native destroy) or no such memory.
    if (core == nullptr) return std::pair<int8_t*, size_t>(nullptr, 0);

    void* memory = core->retro_get_memory_data(id);
    size_t size = memory != nullptr ? core->retro_get_memory_size(id) : 0;
    if (size == 0) return std::pair<int8_t*, size_t>(nullptr, 0);

    auto* data = new int8_t[size];
    memcpy(data, memory, size);

    return std::pair(data, size);
}

JNIEXPORT jboolean JNICALL LibretroDroid::unserializeSRAM(int8_t* data, size_t size) { return unserializeMemory(RETRO_MEMORY_SAVE_RAM, data, size); }
std::pair<int8_t*, size_t> LibretroDroid::serializeSRAM() { return serializeMemory(RETRO_MEMORY_SAVE_RAM); }

// The clock (gambatte, mGBA, ...): minarch keeps it in Saves/<TAG>/<rom.ext>.rtc (ma_saves.c RTC_read/RTC_write).
jboolean LibretroDroid::unserializeRTC(int8_t* data, size_t size) {
    std::lock_guard<std::mutex> lock(coreLock);
    if (core == nullptr) return false;
    size_t rtcSize = core->retro_get_memory_size(RETRO_MEMORY_RTC);
    void* rtc = core->retro_get_memory_data(RETRO_MEMORY_RTC);
    if (rtc == nullptr || rtcSize == 0) return false;
    memcpy(rtc, data, std::min(size, rtcSize));   // minarch reads at most the clock's size
    return true;
}
std::pair<int8_t*, size_t> LibretroDroid::serializeRTC() { return serializeMemory(RETRO_MEMORY_RTC); }

void LibretroDroid::onSurfaceChanged(unsigned int width, unsigned int height) {
    LOGD("Performing libretrodroid onSurfaceChanged");
    if (video) video->updateScreenSize(width, height);   // no Video when the game failed to load
}

void LibretroDroid::onSurfaceCreated() {
    LOGD("Performing libretrodroid onSurfaceCreated");

    struct retro_system_av_info system_av_info {};
    core->retro_get_system_av_info(&system_av_info);

    const bool vulkan = Environment::getInstance().isUseVulkan();

    // A context created after the first one: the old one is gone with every GL name in it. Tell a GL core first, as
    // RetroArch does on a context loss, so its context_reset rebuilds instead of reusing dead names. Nothing exists in
    // the new context yet, so whatever it deletes there is a no-op. The old Video skips its GL deletes (not its context).
    // A Vulkan core's device does not die with the EGL context: only the Video (and its AHB imports) is rebuilt.
    if (!vulkan) {
        if (hwContextLive && Environment::getInstance().getHwContextDestroy() != nullptr) {
            Environment::getInstance().getHwContextDestroy()();
        }
        hwContextLive = false;
    }
    video = nullptr;
    TeardownContext::remember();

    // The old context's fences died with it (the Vulkan device lives on): never wait on them in the new one.
    if (vulkan) VulkanBridge::get().onGlContextLost();
    if (vulkan) VulkanBridge::get().setMaxFrame(system_av_info.geometry.max_width, system_av_info.geometry.max_height);
    if (vulkan && !VulkanBridge::get().isReady()) {
        // Before context_reset, where the core asks for the interface. Dolphin sizes its swapchain to its base geometry.
        if (!VulkanBridge::get().initialize(system_av_info.geometry.base_width, system_av_info.geometry.base_height)) {
            throw LibretroDroidError("Vulkan bridge initialization failed", ERROR_GL_NOT_COMPATIBLE);
        }
    }

    Video::RenderingOptions renderingOptions {
        Environment::getInstance().isUseHwAcceleration(),
        system_av_info.geometry.base_width,
        system_av_info.geometry.base_height,
        Environment::getInstance().isUseDepth(),
        Environment::getInstance().isUseStencil(),
        openglESVersion,
        Environment::getInstance().getPixelFormat(),
        vulkan
    };

    geometryRotation = Environment::getInstance().getScreenRotation();
    auto newVideo = new Video(
        renderingOptions,
        fragmentShaderConfig,
        Environment::getInstance().isBottomLeftOrigin(),
        Environment::getInstance().getScreenRotation(),
        skipDuplicateFrames,
        immersiveModeEnabled,
        viewportRect,
        immersiveModeConfig,
        viewportAlignment
    );

    video = std::unique_ptr<Video>(newVideo);
    video->updateContentSize(system_av_info.geometry.base_width, system_av_info.geometry.base_height);
    video->updateScaleMode(scaleMode);
    video->updateScreenOffset(screenOffsetX, screenOffsetY);
    video->updateScreenRegions(screenRegions);
    video->updateSecondaryRegions(secondaryRegions);
    video->setPresetChain(presetChain);
    geometryWidth = (float) system_av_info.geometry.base_width;
    geometryHeight = (float) system_av_info.geometry.base_height;
    // A re-created Video (new context after load) gets the running game's aspect ratio; resume() does it the first time.
    if (gameLoaded) {
        refreshAspectRatio();
        geometryChanged = true;
    }

    if (Environment::getInstance().getHwContextReset() != nullptr && !(vulkan && hwContextLive)) {
        Environment::getInstance().getHwContextReset()();
        hwContextLive = true;
    }
}

Input* LibretroDroid::currentInput() {
    if (inputResetPending.exchange(false) || !input) {
        auto fresh = std::make_unique<Input>();
        for (unsigned port = 0; port < 4; port++) {
            // Velocity is deliberately not carried: after a pause the pointer must not drift; the host re-sends it.
            PointerState old = input ? input->pointerOf(port) : PointerState();
            old.sticky = old.sticky || stickyPointers[port];
            fresh->restorePointer(port, carryOver(old));
        }
        input = std::move(fresh);
    }
    return input.get();
}

void LibretroDroid::onMotionEvent(
    unsigned int port,
    unsigned int source,
    float xAxis,
    float yAxis
) {
    LOGD("Received motion event: %d %.2f, %.2f", source, xAxis, yAxis);
    currentInput()->onMotionEvent(port, source, xAxis, yAxis);
}

void LibretroDroid::onTouchEvent(float xAxis, float yAxis) {
    LOGD("Received touch event: %.2f, %.2f", xAxis, yAxis);
    if (video) {
        auto [x, y] = video->getLayout().getRelativePosition(xAxis, yAxis);
        currentInput()->onMotionEvent(0, Input::MOTION_SOURCE_POINTER, x, y);
    }
}

void LibretroDroid::onTriggerEvent(unsigned int port, float l2, float r2) {
    currentInput()->onTriggerEvent(port, l2, r2);
}

void LibretroDroid::onKeyEvent(unsigned int port, int action, int keyCode) {
    LOGD("Received key event with action (%d) and keycode (%d)", action, keyCode);
    currentInput()->onKeyEvent(port, action, keyCode);
}

void LibretroDroid::create(
    unsigned int GLESVersion,
    const std::string& soFilePath,
    const std::string& systemDir,
    const std::string& savesDir,
    std::vector<Variable> variables,
    const ShaderManager::Config& shaderConfig,
    float refreshRate,
    bool lowLatencyAudio,
    bool enableVirtualFileSystem,
    bool enableMicrophone,
    bool duplicateFrames,
    std::optional<ImmersiveMode::Config> immersiveModeConfig,
    const std::string& language
) {
    LOGD("Performing libretrodroid create");

    CoreLog::clear();

    {
        // Achievements is a process-wide singleton that outlives a session: a previous view that was only stopped
        // (never destroyed) leaves its client, loaded game and region table here, all pointing into the core about to
        // be released. Drop them first so no later step() reads through the old core's memory. The host enables
        // achievements before the view exists, so an enabled client is replaced by a fresh one, not dropped.
        std::lock_guard<std::mutex> lock(coreLock);
        Achievements::getInstance().resetSession(true);
    }

    // Before resetGlobalVariables(), which would otherwise dlclose a leftover core without retro_deinit().
    releaseCore();

    resetGlobalVariables();

    Environment::getInstance().initialize(systemDir, savesDir, &callback_get_current_framebuffer);
    // A failed load never reaches destroy(); releaseCore() above deinitialised its core, drop its VFS files too.
    VFS::getInstance().deinitialize();
    // Again after releaseCore(): the old core may have set a memory map from retro_unload_game/retro_deinit, and a new
    // core that never sets one would otherwise build its region table from those stale descriptors.
    Achievements::getInstance().setMemoryMap(nullptr);
    Environment::getInstance().setLanguage(language);
    Environment::getInstance().setEnableVirtualFileSystem(enableVirtualFileSystem);
    Environment::getInstance().setEnableMicrophone(enableMicrophone);
    // A previous session that never reached destroy() (released above, bridge included) leaves no live context behind:
    // a new Vulkan core must get its context_reset, a GL core no stray context_destroy.
    hwContextLive = false;
    // One-shot: the host sets it before every view, so a later session never inherits it. Offered only where the bridge
    // can run, so a core that picks Vulkan always gets it; otherwise the core stays on GLES.
    const bool offerVulkan = preferVulkan && VulkanBridge::isSupported();
    preferVulkan = false;
    VulkanBridge::get().setSwapchainFrames(vulkanSwapchainFrames);
    vulkanSwapchainFrames = false;
    Environment::getInstance().setPreferVulkan(offerVulkan);

    openglESVersion = GLESVersion;
    screenRefreshRate = refreshRate;
    skipDuplicateFrames = duplicateFrames;
    immersiveModeEnabled = GLESVersion >= 3 && immersiveModeConfig.has_value();
    this->immersiveModeConfig = immersiveModeConfig.value_or(ImmersiveMode::Config{});
    audioEnabled = true;
    frameSpeed = 1;
    // A new view starts without FF; GLRetroView re-applies its own value right after create().
    fastForwardSpeed = 0;
    fastForwardAudible = false;
    fastForwardPacer.stop();
    appliedFastForwardSpeed = 0;
    appliedFastForwardAudible = false;
    fastForwardDropAudio = false;
    fastForwardPlaybackSpeed = 0.0;
    // Rewind likewise: off until GLRetroView re-applies it.
    rewindWanted = false;
    rewindingWanted = false;
    rewindAudible = false;
    rewindState = REWIND_STATUS_OFF;
    appliedRewinding = false;
    appliedRewindMuted = false;
    rewindEmptyLatched = false;
    rewindDropAudio = false;
    // scaleMode, screenOffset and viewportRect are not reset here: setters may arrive (on the GL thread) before
    // create(), and GLRetroView re-applies its own values right after create().
    geometryWidth = 0.0F;
    geometryHeight = 0.0F;
    geometryAspect = 0.0F;
    geometryRotation = 0.0F;
    geometryChanged = false;
    hardwareFrame.store(false);

    gbalink::forgetCore();   // nx-mobile: the new core registers its netpacket interface itself (retro_init or load)
    core = std::make_unique<Core>(soFilePath);

    core->retro_set_video_refresh(&callback_hw_video_refresh);
    core->retro_set_environment(&Environment::callback_environment);
    core->retro_set_audio_sample(&callback_audio_sample);
    core->retro_set_audio_sample_batch(&callback_set_audio_sample_batch);
    core->retro_set_input_poll(&callback_retro_set_input_poll);
    core->retro_set_input_state(&callback_set_input_state);

    std::for_each(variables.begin(), variables.end(), [&](const Variable& v) {
        updateVariable(v);
    });

    core->retro_init();
    coreInitialized = true;

    preferLowLatencyAudio = lowLatencyAudio;

    // HW accelerated cores are only supported on opengles 3.
    if (Environment::getInstance().isUseHwAcceleration() && openglESVersion < 3) {
        throw LibretroDroidError("OpenGL ES 3 is required for this Core", ERROR_GL_NOT_COMPATIBLE);
    }

    fragmentShaderConfig = shaderConfig;

    rumble = std::make_unique<Rumble>();
}

void LibretroDroid::loadGameFromPath(const std::string& gamePath) {
    LOGD("Performing libretrodroid loadGameFromPath");
    struct retro_system_info system_info {};
    core->retro_get_system_info(&system_info);

    this->gamePath = gamePath;
    struct retro_game_info game_info {};
    game_info.path = this->gamePath.c_str();
    game_info.meta = nullptr;

    if (system_info.need_fullpath) {
        game_info.data = nullptr;
        game_info.size = 0;
    } else {
        struct Utils::ReadResult file = Utils::readFileAsBytes(gamePath);
        gameFileData.reset(file.data);
        game_info.data = file.data;
        game_info.size = file.size;
    }

    bool result = core->retro_load_game(&game_info);
    if (!result) {
        LOGE("Cannot load game. Leaving.");
        throw std::runtime_error("Cannot load game");
    }

    afterGameLoad();
}

void LibretroDroid::loadGameFromBytes(std::unique_ptr<int8_t[]> data, size_t size) {
    LOGD("Performing libretrodroid loadGameFromBytes");
    gameBytes = std::move(data);

    struct retro_system_info system_info {};
    core->retro_get_system_info(&system_info);

    struct retro_game_info game_info {};
    game_info.path = nullptr;
    game_info.meta = nullptr;

    if (system_info.need_fullpath) {
        game_info.data = nullptr;
        game_info.size = 0;
    } else {
        game_info.data = gameBytes.get();
        game_info.size = size;
    }

    bool result = core->retro_load_game(&game_info);
    if (!result) {
        LOGE("Cannot load game. Leaving.");
        throw std::runtime_error("Cannot load game");
    }

    afterGameLoad();
}

void LibretroDroid::loadGameFromVirtualFiles(std::vector<VFSFile> virtualFiles) {
    LOGD("Performing libretrodroid loadGameFromVirtualFiles");
    struct retro_system_info system_info {};
    core->retro_get_system_info(&system_info);

    if (virtualFiles.empty()) {
        LOGE("Calling loadGameFromVirtualFiles without any file.");
        throw std::runtime_error("Calling loadGameFromVirtualFiles without any file.");
    }

    std::string firstFilePath = virtualFiles[0].getFileName();
    int firstFileFD = virtualFiles[0].getFD();

    bool loadUsingVFS = system_info.need_fullpath || virtualFiles.size() > 1;

    gamePath = firstFilePath;
    struct retro_game_info game_info {};
    game_info.path = gamePath.c_str();
    game_info.meta = nullptr;

    if (loadUsingVFS) {
        VFS::getInstance().initialize(std::move(virtualFiles));
    }

    if (loadUsingVFS) {
        game_info.data = nullptr;
        game_info.size = 0;
    } else {
        struct Utils::ReadResult file = Utils::readFileAsBytes(firstFileFD);
        gameFileData.reset(file.data);
        game_info.data = file.data;
        game_info.size = file.size;
        // readFileAsBytes already closed the fd via fclose. Release it here so the
        // VFSFile destructor doesn't close the same fd a second time.
        virtualFiles[0].releaseFD();
    }

    bool result = core->retro_load_game(&game_info);
    if (!result) {
        LOGE("Cannot load game. Leaving.");
        throw std::runtime_error("Cannot load game");
    }

    afterGameLoad();
}

void LibretroDroid::releaseCore() {
    if (core != nullptr) {
        if (gameLoaded) core->retro_unload_game();
        if (coreInitialized) core->retro_deinit();
    }
    // After retro_deinit, before the library closes: the bridge calls the core's destroy_device, and its negotiation
    // struct lives in the core's .so.
    VulkanBridge::get().destroy();

    core = nullptr;
    coreInitialized = false;
    gameLoaded = false;
    // Its rewind history and buffers too (the worker joined, the memory freed).
    rewind.configure(false, 0);
    rewindSize = 0;
    rewindGivenUp = false;
    rewindProbe.reset();
    rewindPlayed = 0;
    rewindBuilt = false;
    // The patches and the RAM they point into belong to that core.
    memoryCheats.clear();
    memoryCheatList.clear();
    cheatMemReady = false;
    cheatMemLogged = false;
    // After retro_unload_game: the core may point into these until then (also a load that failed).
    gamePath.clear();
    gameFileData.reset();
    gameBytes.reset();
}

void LibretroDroid::destroy() {
    netplay::shutdown();   // before the lock: it joins the engine thread; a no-op without a session
    gbalink::shutdown();   // the GBA link too: DISCONNECT, join its thread; a no-op without a session
    std::lock_guard<std::mutex> lock(coreLock);

    LOGD("Performing libretrodroid destroy");

    // GLSurfaceView paused first and left no context current: make the session's current again for the core's
    // teardown and our deletes. Without one (GL thread gone) the core tears down without a context, as upstream did.
    TeardownContext context;
    if (!context.isCurrent()) LOGW("Destroying the session without its GL context");

    if (hwContextLive && Environment::getInstance().getHwContextDestroy() != nullptr) {
        Environment::getInstance().getHwContextDestroy()();
    }
    hwContextLive = false;

    Achievements::getInstance().resetSession(false);   // under coreLock: no later step() can touch a client of a destroyed view

    gbalink::stopCore();   // the core hears the link end (disconnected, stop) before retro_unload_game
    releaseCore();
    SharedContexts::destroyAll();   // nx-mobile: worker contexts the core left, while the session's is still current
    gbalink::forgetCore();   // nx-mobile: no netpacket callback may point into an unloaded core

    video = nullptr;
    rumble = nullptr;
    fpsSync = nullptr;
    audio = nullptr;
    input = nullptr;

    Environment::getInstance().deinitialize();
    // Reset on unload only (initialize() runs deinitialize() too): the next core never sees a previous game's motion; the host sets it before each launch.
    SensorState::get().reset();
    stickyPointers.fill(false);   // likewise: the next game's view sets its own (Wii only) before its first frame
    VFS::getInstance().deinitialize();
    TeardownContext::forget();
}

void LibretroDroid::resume() {
    LOGD("Performing libretrodroid resume");

    inputResetPending = true;

    if (fpsSync) fpsSync->reset();
    if (audio) audio->start();
    // The Video is the GL thread's: the ratio is applied there at the next step.
    float aspect = getAspectRatio();
    if (aspect != geometryAspect) geometryChanged = true;
    geometryAspect = aspect;
    aspectRatioPending = true;
}

void LibretroDroid::pause() {
    LOGD("Performing libretrodroid pause");
    if (audio) audio->stop();

    // Keys held when the game paused are released when it resumes. The GL thread drops the state itself: queued
    // key events may be using it right now.
    inputResetPending = true;
}

void LibretroDroid::step() {
    std::unique_lock<std::mutex> lock(coreLock);

    LOGD("Stepping into retro_run()");

    if (video && aspectRatioPending.exchange(false)) video->updateAspectRatio(geometryAspect);

    // Read once: the UI thread can change it mid-step, and the run loop and the paused redraw must agree.
    unsigned int speed = frameSpeed;
    const bool lk = gbalink::active();               // nx-mobile GBA link: speed 1, held while either side pauses
    const bool np = !lk && netplay::active();        // nx-mobile netplay: one lockstep frame per step
    if (np || lk) speed = 1;

    // Neither rewind nor fast forward runs in netplay or a link session: the peers would desync. Rewinding wins over
    // FF, which stops (its clock resynced) and picks up again when the rewind ends. Paused while rewinding (speed 0)
    // takes the paused path below.
    applyRewindConfig();
    const bool rewindHeld = !np && !lk && rewindingWanted.load() && rewind.enabled();
    const bool rewinding = rewindHeld && speed > 0;
    if (!rewindHeld) rewindEmptyLatched = false;
    applyRewinding(rewinding);
    applyFastForward((np || lk || rewindHeld) ? 0 : fastForwardSpeed.load(), fastForwardAudible.load());
    // Paused during FF (speed 0) takes the normal paused path below; FF picks up again on resume, its clock resynced.
    const bool fastForward = fastForwardPacer.active() && speed > 0;

    unsigned frames = 1;
    if (fpsSync && !fastForward) {
        unsigned requestedFrames = fpsSync->advanceFrames();

        // If the application runs too slow it's better to just skip those frames. A rewind never catches up: one
        // step back per frame at most.
        frames = (np || lk || rewinding) ? 1 : std::min(requestedFrames, 2u);
    }
    if (np && !netplay::preFrame(*core, *currentInput())) speed = 0;   // waiting: redraw the last frame
    if (lk && !gbalink::preFrame()) speed = 0;                         // a pause: redraw the last frame

    // A stick-driven pointer moves once per emulated frame, so its speed is per second of game time.
    const float frameSeconds = fpsSync ? 1.0f / static_cast<float>(fpsSync->getContentRefreshRate()) : 1.0f / 60.0f;
    // FF runs the frames its pacer has due, never fpsSync's catch-up × a speed: that let the speed follow the display.
    unsigned runs = frames * speed;
    if (rewinding) {
        runs = stepRewinding(frameSeconds);
    } else {
        if (fastForward) {
            runs = fastForwardPacer.framesFor(std::chrono::steady_clock::now());
            if (appliedFastForwardAudible) {
                // The core's audio comes at the speed it actually ran; vsync mode had stretched the rate to the screen.
                const double stretch = fpsSync ? fpsSync->getTimeStretchFactor() : 1.0;
                fastForwardPlaybackSpeed = fastForwardPacer.effectiveSpeed() / stretch;
                updateFastForwardAudioSpeed();
            }
        }
        for (size_t i = 0; i < runs; i++) {
            runFrame(np, lk, frameSeconds);
            if (!np && !lk) rewind.capture(rewindSerialize);
        }
        rewindPlayed += runs;   // the probe's clock (RewindProbe): a paused step plays none
        if (speed > 0) rewindState = (np || lk) ? REWIND_STATUS_OFF : rewind.enabled() ? REWIND_STATUS_READY : offStatus();
    }
    auto& achievements = Achievements::getInstance();
    if (speed == 0 && achievements.isEnabled()) achievements.onFrame(true);   // menu open: keep the session alive
    if (np && speed) netplay::postFrame();

    if (video) {
        if (runs == 0) {
            // Paused, or an FF step with no frame due: the core doesn't run, so redraw its last frame with the current
            // shader and layout. GL cores otherwise draw only inside their video callback, leaving the picture stale.
            // An FF step that ran frames shows its last one like any step.
            video->renderPausedFrame();
        } else if (!video->rendersInVideoCallback()) {
            video->renderFrame();
        }
    }

    // The frame sleep runs without the core lock, so achievements and the off-thread save calls aren't held up by it.
    lock.unlock();
    if (fastForward) {
        auto ahead = fastForwardPacer.sleepFor(std::chrono::steady_clock::now());
        if (ahead > Duration::zero()) std::this_thread::sleep_for(ahead);
    } else if (fpsSync) {
        fpsSync->wait();
    }
    lock.lock();

    // A core that changed its frame rate mid-game (SET_SYSTEM_AV_INFO) is paced at the new rate from the next step.
    // Swapped after wait(): a fresh FPSSync has no start time until advanceFrames(), and sleep_until(min) never returns.
    // Audio keeps its stream: the sample rate is unchanged and its rate controller absorbs the vsync stretch change.
    double fps = 0.0;
    if (fpsSync && Environment::getInstance().consumeTimingUpdate(fps)) {
        if (std::abs(fps - fpsSync->getContentRefreshRate()) > 0.01) {
            LOGI("Core changed its frame rate from %f to %f", fpsSync->getContentRefreshRate(), fps);
            fpsSync = std::make_unique<FPSSync>(fps, screenRefreshRate);
            if (fastForwardPacer.active()) {
                fastForwardPacer.start(fps, appliedFastForwardSpeed, std::chrono::steady_clock::now());
            }
        }
    }

    if (rumble && rumbleEnabled) {
        rumble->fetchFromEnvironment();
    }

    // Some games override the core geometry at runtime. These fields get updated in retro_run().
    if (video && Environment::getInstance().isGameGeometryUpdated()) {
        Environment::getInstance().clearGameGeometryUpdated();

        video->updateRendererSize(
            Environment::getInstance().getGameGeometryWidth(),
            Environment::getInstance().getGameGeometryHeight()
        );
        geometryWidth = (float) Environment::getInstance().getGameGeometryWidth();
        geometryHeight = (float) Environment::getInstance().getGameGeometryHeight();
        geometryChanged = true;

        dirtyVideo = true;
    }

    if (video && Environment::getInstance().isScreenRotationUpdated()) {
        Environment::getInstance().clearScreenRotationUpdated();

        geometryRotation = Environment::getInstance().getScreenRotation();
        geometryChanged = true;
        video->updateRotation(geometryRotation);
    }

}

float LibretroDroid::getAspectRatio() {
    float gameAspectRatio = Environment::getInstance().retrieveGameSpecificAspectRatio();
    return gameAspectRatio > 0 ? gameAspectRatio : defaultAspectRatio;
}

void LibretroDroid::refreshAspectRatio() {
    float aspectRatio = getAspectRatio();
    geometryAspect = aspectRatio;
    if (video) video->updateAspectRatio(aspectRatio);
}

void LibretroDroid::setRumbleEnabled(bool enabled) {
    rumbleEnabled = enabled;
}

bool LibretroDroid::isRumbleEnabled() const {
    return rumbleEnabled;
}

void LibretroDroid::setFrameSpeed(unsigned int speed) {
    frameSpeed = speed;
    if (audio) audio->setPlaybackSpeed(speed);
}

void LibretroDroid::runFrame(bool np, bool lk, float frameSeconds) {
    currentInput()->advancePointers(frameSeconds);
    if (!np && !lk && !memoryCheats.empty()) applyMemoryCheats();   // never in netplay or link: the peer's RAM would differ
    core->retro_run();
    gldebug::pollErrors();
    rethrowVideoCallbackError();
    auto& achievements = Achievements::getInstance();
    if (achievements.isEnabled()) achievements.onFrame(false);
}

void LibretroDroid::setRewindEnabled(bool enabled) {
    rewindWanted = enabled;
}

void LibretroDroid::setRewinding(bool rewinding) {
    rewindingWanted = rewinding;
}

void LibretroDroid::setRewindAudio(bool audible) {
    rewindAudible = audible;
}

void LibretroDroid::applyRewindConfig() {
    if (!rewindWanted.load() || core == nullptr || !gameLoaded) {
        rewind.configure(false, rewindSize);   // a no-op once off
        rewindBuilt = false;
        return;
    }
    if (rewind.enabled() || rewindGivenUp) return;
    if (rewindBuilt) {
        // Built, then a capture was refused (Rewind turned itself off): counted like a refused probe.
        rewindBuilt = false;
        noteRewindRefusal();
        return;
    }
    // Built only once the core can serialize: booting cores (mupen64plus, pcsx) report 0 or refuse for a while.
    // Asked again every RewindProbe::INTERVAL played frames, not every frame, and less often after each refusal.
    if (!rewindProbe.advance(std::exchange(rewindPlayed, 0u))) return;
    const size_t size = core->retro_serialize_size();
    if (size == 0) {
        // One that stays at 0 has no states at all (melonDS DS in DSi mode): given up on, and the app hides Rewind.
        const double fps = fpsSync ? fpsSync->getContentRefreshRate() : 60.0;
        const auto limit = static_cast<unsigned>(REWIND_ZERO_SECONDS * std::max(fps, 1.0));
        if (rewindProbe.sizeZero(limit)) {
            LOGW("Rewind: no state after %u s of play; rewind off for this game", REWIND_ZERO_SECONDS);
            rewindGivenUp = true;
        }
        return;
    }
    rewindProbe.sizeKnown();
    if (rewindSize != 0 && size != rewindSize) {
        // Its last capture was refused because the state grew (or shrank): rebuilding per change would churn many MB.
        LOGW("Rewind: the state size changed from %zu to %zu bytes; rewind off for this game", rewindSize, size);
        rewindGivenUp = true;
        return;
    }
    // One serialize before building: a core that reports a size but refuses would otherwise build and free the whole
    // buffer (tens of MB, a thread) on every ask, a hitch on this thread each time.
    try {
        std::vector<uint8_t> probe(size);
        if (!core->retro_serialize(probe.data(), size)) {
            noteRewindRefusal();
            return;
        }
    } catch (const std::bad_alloc&) {
        LOGW("Rewind: no memory for a %zu-byte state; rewind off for this game", size);
        rewindGivenUp = true;
        return;
    }
    rewindSize = size;
    rewind.configure(true, size);
    rewindBuilt = rewind.enabled();
    // No room in RAM (or none for the buffers): asking again would only retry the same budget.
    if (!rewindBuilt) rewindGivenUp = true;
}

void LibretroDroid::noteRewindRefusal() {
    // 2, 4, 8 ... up to 32 probe intervals: a core still booting is soon caught, one that never serializes costs little.
    if (rewindProbe.refused()) {
        LOGW("Rewind: the core refused to serialize %u times; rewind off for this game", rewindProbe.refusalCount());
        rewindGivenUp = true;
    }
}

int LibretroDroid::offStatus() const {
    return rewindWanted.load() && rewindGivenUp ? REWIND_STATUS_UNAVAILABLE : REWIND_STATUS_OFF;
}

void LibretroDroid::applyRewinding(bool rewinding) {
    if (rewinding != appliedRewinding) {
        appliedRewinding = rewinding;
        rewindTick = 0;   // the first frame of a rewind steps back at once
    }
    const bool muted = rewinding && !rewindAudible.load();
    if (muted != appliedRewindMuted) {
        appliedRewindMuted = muted;
        rewindDropAudio = muted;
        if (audio) audio->setRewind(rewinding, !muted);
    }
}

unsigned LibretroDroid::stepRewinding(float frameSeconds) {
    // One snapshot per capture interval, as recorded: never faster than the game ran. In between, the last frame is
    // redrawn. The core isn't polled for input here, but our input state comes from key events queued between steps,
    // so a shortcut's release is seen whether or not the core runs.
    const unsigned interval = static_cast<unsigned>(std::max(rewind.interval(), 1));
    if (rewindTick++ % interval != 0) return 0;

    // EMPTY stays latched until the rewind ends: a held rewind on an empty buffer alternates EMPTY and REWOUND steps,
    // and the app (polling) must not miss it.
    switch (rewind.stepBack(rewindUnserialize)) {
        case Rewind::Step::REWOUND:
        case Rewind::Step::SKIPPED:
            rewindState = rewindEmptyLatched ? REWIND_STATUS_EMPTY : REWIND_STATUS_REWINDING;
            // A restored state draws nothing until the core runs: one frame shows it. Not captured, or the next step
            // would land on it again (after SKIPPED, on the refused state's successor: still not captured, so the
            // next step restores the state before the refused one).
            runFrame(false, false, frameSeconds);
            return 1;
        case Rewind::Step::EMPTY:
            rewindEmptyLatched = true;
            rewindState = REWIND_STATUS_EMPTY;
            // Nothing older: play this frame (captured, as in play) so a held rewind never freezes the game; the next
            // step goes back to it, alternating. A toggled rewind reads EMPTY and the app turns it off.
            runFrame(false, false, frameSeconds);
            rewind.capture(rewindSerialize);
            rewindTick = 0;
            return 1;
        case Rewind::Step::OFF:
            break;
    }
    rewindState = offStatus();
    runFrame(false, false, frameSeconds);
    return 1;
}

void LibretroDroid::setFastForward(unsigned maxSpeed, bool audible) {
    fastForwardSpeed = maxSpeed;
    fastForwardAudible = audible;
}

void LibretroDroid::applyFastForward(unsigned maxSpeed, bool audible) {
    if (maxSpeed == appliedFastForwardSpeed && audible == appliedFastForwardAudible) return;

    const bool on = maxSpeed > 0;
    if (on && maxSpeed != appliedFastForwardSpeed) {
        const double fps = fpsSync ? fpsSync->getContentRefreshRate() : 60.0;
        fastForwardPacer.start(fps, maxSpeed, std::chrono::steady_clock::now());
    } else if (!on && fastForwardPacer.active()) {
        fastForwardPacer.stop();
        // fpsSync's clock stood still through FF: restart it, or the first normal step sleeps off the gap.
        if (fpsSync) fpsSync->reset();
    }
    appliedFastForwardSpeed = maxSpeed;
    appliedFastForwardAudible = audible;

    fastForwardDropAudio = on && !audible;
    fastForwardPlaybackSpeed = on && audible ? fastForwardPacer.effectiveSpeed() : 0.0;
    Environment::getInstance().setFastForwarding(on);
    if (audio) audio->setFastForward(on, audible);
    updateFastForwardAudioSpeed();
}

void LibretroDroid::setAudioEnabled(bool enabled) {
    audioEnabled = enabled;
}

void LibretroDroid::setShaderConfig(ShaderManager::Config shaderConfig) {
    fragmentShaderConfig = std::move(shaderConfig);
    if (video) {
        video->updateShaderType(fragmentShaderConfig);
    }
}

void LibretroDroid::setPresetChain(std::optional<PresetChain> chain) {
    presetChain = std::move(chain);
    presetError.reset();
    if (!video) return;

    // Drop a stale error from an earlier build so it isn't reported against this chain.
    video->takePresetError();
    video->setPresetChain(presetChain);

    // GLSurfaceView runs queued events while paused with no context current; every GL call would no-op and the
    // build would fail with a bogus compile error. Leave it to the next renderFrame, whose error takePresetError reads.
    if (eglGetCurrentContext() == EGL_NO_CONTEXT) return;

    video->updatePresetRenderer();
    presetError = video->takePresetError();
    if (presetError) {
        // Video already fell back to the built-in shader; don't retry the broken chain on the next Video.
        presetChain.reset();
    }
}

void LibretroDroid::setPresetParameter(const std::string& id, float value) {
    if (presetChain) presetChain->params[id] = value;
    if (video) video->setPresetParameter(id, value);
}

std::optional<std::string> LibretroDroid::takePresetError() {
    auto error = presetError;
    presetError.reset();
    if (!error && video) error = video->takePresetError();
    return error;
}

void LibretroDroid::handleVideoRefresh(
    const void *data,
    unsigned int width,
    unsigned int height,
    size_t pitch
) {
    // Runs inside the core's retro_run: a C++ exception must not unwind through the core's C frames. Keep the
    // first one and rethrow it from step() once retro_run has returned.
    if (videoCallbackError) return;
    try {
        // A duplicate frame (data == nullptr) says nothing about how the core renders.
        if (data != nullptr) hardwareFrame.store(data == RETRO_HW_FRAME_BUFFER_VALID);
        gldebug::onVideoRefresh(data, width, height, video ? (unsigned) video->getCurrentFramebuffer() : 0);

        const bool vulkan = Environment::getInstance().isUseVulkan();
        if (video && vulkan && data == RETRO_HW_FRAME_BUFFER_VALID) {
            // Copy the core's image into the next AHardwareBuffer; Video draws that as the frame.
            VulkanBridge::Frame frame;
            if (VulkanBridge::get().bridgeFrame(width, height, frame)) {
                video->setExternalFrame(frame.buffer, frame.id, frame.width, frame.height, frame.syncFd);
                width = frame.width;
                height = frame.height;
            } else {
                data = nullptr;   // nothing bridged: redraw the last frame
            }
        }

        if (video) {
            video->onNewFrame(data, width, height, pitch);

            if (video->rendersInVideoCallback()) {
                video->renderFrame();
                if (vulkan) VulkanBridge::get().afterGlDraw();
            }
        }
    } catch (...) {
        LOGE("Exception in the video callback; reported after retro_run returns");
        videoCallbackError = std::current_exception();
    }
}

void LibretroDroid::rethrowVideoCallbackError() {
    if (!videoCallbackError) return;
    auto error = videoCallbackError;
    videoCallbackError = nullptr;
    std::rethrow_exception(error);
}

size_t LibretroDroid::handleAudioCallback(const int16_t *data, size_t frames) {
    gldebug::onAudio(data, frames);
    // Muted FF drops the samples but still reports them consumed: mupen64plus spins on a short count. The FIFO write
    // never blocks either; what doesn't fit (audible FF outrunning playback) is dropped.
    if (audio && audioEnabled && !fastForwardDropAudio.load() && !rewindDropAudio.load()) {
        audio->write(data, frames);
    }
    return frames;
}

int16_t LibretroDroid::handleSetInputState(
    unsigned int port,
    unsigned int device,
    unsigned int index,
    unsigned int id
) {
    if (netplay::active()) return netplay::inputState(port, device, index, id);
    return currentInput()->getInputState(port, device, index, id);
}

uintptr_t LibretroDroid::handleGetCurrentFrameBuffer() {
    if (video) {
        return video->getCurrentFramebuffer();
    }
    return 0;
}

void LibretroDroid::reset() {
    std::lock_guard<std::mutex> lock(coreLock);

    if (core == nullptr) return;
    core->retro_reset();
    // A bare retro_reset left the old game's snapshots to rewind into, and fpsSync's clock counting from before.
    rewind.clear();
    if (fpsSync) fpsSync->reset();
}

std::pair<int8_t*, size_t> LibretroDroid::serializeState() {
    std::lock_guard<std::mutex> lock(coreLock);

    // An empty result (nullptr, 0) means "no state": no core (quit racing the native destroy), or a core that
    // reports no size or refuses retro_serialize (mupen64plus/ppsspp while booting). Never hand back a buffer
    // the core did not fill, or it would be written as a save state.
    if (core == nullptr) return std::pair<int8_t*, size_t>(nullptr, 0);

    // A core may run emulation (and GL) inside retro_serialize_size (Azahar drains its async work with RunLoop): on
    // the GL thread while the view is paused (onStop's auto state), the paused view's context is made current for it.
    TeardownContext context;
    size_t size = core->retro_serialize_size();
    if (size == 0) return std::pair<int8_t*, size_t>(nullptr, 0);

    auto data = new int8_t[size]();

    if (!core->retro_serialize(data, size)) {
        LOGE("Cannot save state: retro_serialize refused");
        delete[] data;
        return std::pair<int8_t*, size_t>(nullptr, 0);
    }

    return std::pair(data, size);
}

void LibretroDroid::resetCheat() {
    std::lock_guard<std::mutex> lock(coreLock);

    if (core) core->retro_cheat_reset();
}

void LibretroDroid::setCheat(unsigned index, bool enabled, const std::string& code) {
    std::lock_guard<std::mutex> lock(coreLock);

    // The code is only read during the call (RetroArch passes a temporary too).
    if (core) core->retro_cheat_set(index, enabled, code.c_str());
}

void LibretroDroid::setMemoryCheats(std::vector<CheatMemPatch> patches) {
    std::lock_guard<std::mutex> lock(coreLock);

    memoryCheats = std::move(patches);
    memoryCheatList.clear();
    for (const auto& p : memoryCheats) memoryCheatList.push_back(&p);
}

// RetroArch's choice of RAM: the SYSTEM_RAM regions of the core's memory map in order, or else
// RETRO_MEMORY_SYSTEM_RAM. The database's addresses are offsets into that.
void LibretroDroid::applyMemoryCheats() {
    auto& achievements = Achievements::getInstance();
    if (!cheatMemReady || cheatMemMapVersion != achievements.memoryMapVersion()) {
        CheatMem_reset(&cheatMem);
        bool fromMap = false;
        unsigned dropped = 0;
        if (const auto* map = achievements.currentMemoryMap()) {
            for (unsigned i = 0; i < map->num_descriptors; i++) {
                const auto& d = map->descriptors[i];
                if ((d.flags & RETRO_MEMDESC_SYSTEM_RAM) && d.ptr && d.len && !CheatMem_addRegion(&cheatMem, d.ptr, d.len))
                    dropped++;
            }
            fromMap = cheatMem.count > 0;
        }
        if (!cheatMem.count && core->retro_get_memory_data && core->retro_get_memory_size)
            CheatMem_addRegion(&cheatMem, core->retro_get_memory_data(RETRO_MEMORY_SYSTEM_RAM),
                               core->retro_get_memory_size(RETRO_MEMORY_SYSTEM_RAM));
        if (!cheatMemLogged) {
            if (dropped) LOGW("cheats: %u system RAM region(s) past the first %d are left out", dropped, CHEAT_MEM_MAX_REGIONS);
            if (!cheatMem.count) LOGW("cheats: the core exposes no system RAM, RAM cheats can't apply");
            else LOGI("cheats: RAM cheats apply to %d region(s), %zu bytes (%s)", cheatMem.count, cheatMem.total,
                      fromMap ? "memory map" : "RETRO_MEMORY_SYSTEM_RAM");
            cheatMemLogged = true;
        }
        cheatMemReady = fromMap;   // nx-redux #262: only a memory map's regions stay put
        cheatMemMapVersion = achievements.memoryMapVersion();
    }
    CheatMem_apply(&cheatMem, memoryCheatList.data(), (int) memoryCheatList.size());
}

bool LibretroDroid::requiresVideoRefresh() const {
    return dirtyVideo;
}

void LibretroDroid::clearRequiresVideoRefresh() {
    dirtyVideo = false;
}

void LibretroDroid::afterGameLoad() {
    gameLoaded = true;

    struct retro_system_av_info system_av_info {};
    core->retro_get_system_av_info(&system_av_info);

    fpsSync = std::make_unique<FPSSync>(system_av_info.timing.fps, screenRefreshRate);

    double inputSampleRate = system_av_info.timing.sample_rate * fpsSync->getTimeStretchFactor();

    audio = std::make_unique<Audio>(
        (int32_t) std::lround(inputSampleRate),
        system_av_info.timing.fps,
        preferLowLatencyAudio
    );

    updateAudioSampleRateMultiplier();

    defaultAspectRatio = findDefaultAspectRatio(system_av_info);
    geometryWidth = (float) system_av_info.geometry.base_width;
    geometryHeight = (float) system_av_info.geometry.base_height;
    geometryAspect = defaultAspectRatio;

    Achievements::getInstance().setCoreMemoryAccessors(core->retro_get_memory_size, core->retro_get_memory_data);
}

void LibretroDroid::achievementsEnable(bool enabled) {
    std::lock_guard<std::mutex> lock(coreLock);
    if (enabled) Achievements::getInstance().enable(); else Achievements::getInstance().disable();
}

void LibretroDroid::achievementsLogin(const std::string& user, const std::string& token) {
    std::lock_guard<std::mutex> lock(coreLock);
    Achievements::getInstance().login(user, token);
}

void LibretroDroid::achievementsLoadGame(const std::string& hash, uint32_t consoleId) {
    std::lock_guard<std::mutex> lock(coreLock);
    Achievements::getInstance().loadGame(hash, consoleId);
}

void LibretroDroid::achievementsUnloadGame() {
    std::lock_guard<std::mutex> lock(coreLock);
    Achievements::getInstance().unloadGame();
}

std::vector<AchievementInfo> LibretroDroid::achievementsList() {
    std::lock_guard<std::mutex> lock(coreLock);
    return Achievements::getInstance().list();
}

AchievementSummary LibretroDroid::achievementsSummary() {
    std::lock_guard<std::mutex> lock(coreLock);
    return Achievements::getInstance().summary();
}

float LibretroDroid::findDefaultAspectRatio(const retro_system_av_info& system_av_info) {
    float result = system_av_info.geometry.aspect_ratio;
    // libretro: an aspect_ratio <= 0 means base_width / base_height (gearcoleco's default "1:1 PAR" reports 0).
    if (result <= 0) {
        result =
            (float) system_av_info.geometry.base_width / (float) system_av_info.geometry.base_height;
    }
    return result;
}

void LibretroDroid::handleRumbleUpdates(const std::function<void(int, float, float)> &handler) {
    if (rumble && rumbleEnabled) {
        rumble->handleRumbleUpdates(handler);
    }
}

void LibretroDroid::setViewport(Rect viewportRect) {
    this->viewportRect = viewportRect;

    if (video != nullptr) {
        video->updateViewportSize(viewportRect);
    }
}

void LibretroDroid::setViewportAlignment(unsigned int viewportAlignment) {
    this-> viewportAlignment = viewportAlignment;

    if (video) {
        this->video->updateViewportAlignment(viewportAlignment);
    }
}

void LibretroDroid::setScaleMode(unsigned int scaleMode) {
    this->scaleMode = scaleMode;

    if (video) {
        video->updateScaleMode(scaleMode);
    }
}

void LibretroDroid::setScreenOffset(float x, float y) {
    screenOffsetX = x;
    screenOffsetY = y;

    if (video) {
        video->updateScreenOffset(x, y);
    }
}

void LibretroDroid::setScreenRegions(std::vector<ScreenRegion> regions) {
    screenRegions = std::move(regions);

    if (video) {
        video->updateScreenRegions(screenRegions);
    }
}

void LibretroDroid::setSecondaryRegions(std::vector<ScreenRegion> regions) {
    secondaryRegions = std::move(regions);

    if (video) {
        video->updateSecondaryRegions(secondaryRegions);
    }
}

void LibretroDroid::renderSecondary(unsigned width, unsigned height) {
    std::lock_guard<std::mutex> lock(coreLock);
    if (!video) return;
    try {
        video->renderSecondary(width, height);
    } catch (const std::exception& e) {
        LOGE("Error rendering the second screen: %s", e.what());
    }
}

std::array<float, 4> LibretroDroid::getGameGeometry() const {
    return { geometryWidth.load(), geometryHeight.load(), geometryAspect.load(), geometryRotation.load() };
}

bool LibretroDroid::isHardwareRendered() const {
    return hardwareFrame.load();
}

} //namespace libretrodroid
