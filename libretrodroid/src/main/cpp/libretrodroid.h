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

#ifndef LIBRETRODROID_LIBRETRODROID_H
#define LIBRETRODROID_LIBRETRODROID_H

#include <jni.h>

#include <EGL/egl.h>

#include <string>
#include <vector>
#include <unordered_set>
#include <mutex>
#include <memory>
#include <optional>
#include <array>
#include <atomic>
#include <exception>

#include "log.h"
#include "core.h"
#include "audio.h"
#include "video.h"
#include "renderers/renderer.h"
#include "fpssync.h"
#include "fastforward.h"
#include "input.h"
#include "rumble.h"
#include "shadermanager.h"
#include "utils/javautils.h"
#include "environment.h"
#include "vfs/vfsfile.h"
#include "renderers/es3/framebufferrenderer.h"
#include "renderers/es2/imagerendereres2.h"
#include "renderers/es3/imagerendereres3.h"
#include "utils/rect.h"
#include "achievements/achievements.h"
#include "cheatmem.h"

namespace libretrodroid {

class LibretroDroid {
public:
    static LibretroDroid& getInstance()
    {
        static LibretroDroid instance;
        return instance;
    }
    LibretroDroid(LibretroDroid const&) = delete;
    void operator=(LibretroDroid const&) = delete;

    void setViewport(Rect viewportRect);

private:
    LibretroDroid() {}

public:
    void setCheat(unsigned index, bool enabled, const std::string& code);
    void resetCheat();
    /** RetroArch RAM-patch cheats (handler 1), applied to the core's system RAM before every retro_run; empty = none. */
    void setMemoryCheats(std::vector<CheatMemPatch> patches);

    // serializeState/serializeSRAM/serializeRTC/serializeMemory return a new[] buffer the caller must delete[], or (nullptr, 0) when there is
    // nothing to save (no core, no SRAM, or the core refused to serialize).
    std::pair<int8_t*, size_t> serializeState();
    bool unserializeState(int8_t *data, size_t size);

    std::pair<int8_t *, size_t> serializeSRAM();
    jboolean unserializeSRAM(int8_t *data, size_t size);

    // The clock (RETRO_MEMORY_RTC). Like the battery save (and minarch), a longer file loads only the core's size;
    // unserializeRTC is quiet when a core has no clock, which most cores don't.
    std::pair<int8_t *, size_t> serializeRTC();
    jboolean unserializeRTC(int8_t *data, size_t size);

    std::pair<int8_t *, size_t> serializeMemory(unsigned id);
    jboolean unserializeMemory(unsigned id, int8_t *data, size_t size);

    // GL thread, on every new EGL context (GLSurfaceView only calls it for one): (re)creates the Video and resets a GL
    // core's context. A second call means the old context was lost with every GL name in it.
    void onSurfaceCreated();
    void onSurfaceChanged(unsigned int width, unsigned int height);

    void create(
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
    );
    // resume() and pause() run on the UI thread while the GL thread is paused, though its queued events still run:
    // they touch no GL-thread object, only flag the input reset and the aspect ratio for the GL thread to apply.
    void resume();
    void step();
    void pause();
    // GL thread (GLRetroView queues it and waits) so the core's context_destroy, retro_unload_game and retro_deinit
    // run with the session's context current; on the caller's thread only when the GL thread has exited.
    void destroy();

    void reset();

    void loadGameFromPath(const std::string &gamePath);
    // Takes the buffer: it lives until retro_unload_game, as the core may keep pointing into it.
    void loadGameFromBytes(std::unique_ptr<int8_t[]> data, size_t size);
    void loadGameFromVirtualFiles(std::vector<VFSFile> virtualFiles);

    // GL thread (queued): the input state is the GL thread's, read by the core inside retro_run.
    void onKeyEvent(unsigned int port, int action, int keyCode);
    void onMotionEvent(unsigned int port, unsigned int source, float xAxis, float yAxis);
    void onTouchEvent(float xAxis, float yAxis);
    void onTriggerEvent(unsigned int port, float l2, float r2);

    void refreshAspectRatio();
    float getAspectRatio();

    bool requiresVideoRefresh() const;
    void clearRequiresVideoRefresh();

    std::vector<Variable> getVariables();
    void updateVariable(const Variable& variable);

    std::vector<std::vector<struct Controller>> getControllers();
    void setControllerType(unsigned int port, unsigned int type);

    // nx-mobile (Wii): GL thread (queued), like the other input setters.
    void setStickyPointer(unsigned int port, bool sticky);
    void setPointerVelocity(unsigned int port, float vx, float vy);

    int availableDisks();
    int currentDisk();
    void changeDisk(unsigned int index);

    void setRumbleEnabled(bool enabled);
    bool isRumbleEnabled() const;

    // The next create() offers the core Vulkan (bridged into the GLES presentation). Set before create().
    void setPreferVulkan(bool value) { preferVulkan = value; }
    void setVulkanSwapchainFrames(bool value) { vulkanSwapchainFrames = value; }
    void handleRumbleUpdates(const std::function<void(int, float, float)> &handler);

    void setFrameSpeed(unsigned int speed);

    // nx-mobile 2026-10-24: fast forward up to maxSpeed × the content's rate (0 = off), with or without its audio.
    // Separate from frameSpeed, which stays pause (0) / run (1): a pause and resume never drop FF. Not applied in
    // netplay or a GBA link session. Any thread; step() picks it up.
    void setFastForward(unsigned maxSpeed, bool audible);

    // RetroAchievements: taken under the core lock so they never race the run loop's onFrame().
    void achievementsEnable(bool enabled);
    void achievementsLogin(const std::string& user, const std::string& token);
    void achievementsLoadGame(const std::string& hash, uint32_t consoleId);
    void achievementsUnloadGame();
    std::vector<AchievementInfo> achievementsList();
    AchievementSummary achievementsSummary();

    void setAudioEnabled(bool enabled);

    void setShaderConfig(ShaderManager::Config shaderConfig);

    // GL thread only. Remembered so a re-created Video draws the same chain; with a Video the chain builds now,
    // so takePresetError can be read right after. A chain that fails to build is forgotten.
    void setPresetChain(std::optional<PresetChain> chain);
    void setPresetParameter(const std::string& id, float value);
    // The last chain build error, cleared on read. GL thread only.
    std::optional<std::string> takePresetError();

    void setViewportAlignment(unsigned int viewportAlignment);

    void setScaleMode(unsigned int scaleMode);

    void setScreenOffset(float x, float y);

    // Regions of the core frame drawn at places in the view (empty = one picture). Remembered, like the viewport, so a
    // Video created later (a new surface) gets them. GL thread.
    void setScreenRegions(std::vector<ScreenRegion> regions);

    // Regions drawn on a second surface (dst 0..1 of that whole surface). Remembered like setScreenRegions. GL thread.
    void setSecondaryRegions(std::vector<ScreenRegion> regions);

    // Draws the last frame's secondary regions into the current default framebuffer, width x height, after step().
    // Never runs the core; errors are logged, never thrown (the second screen must not end the game). GL thread.
    void renderSecondary(unsigned width, unsigned height);

    // Base width, base height, aspect ratio and the core's rotation (radians, SET_ROTATION) of the running game; zeros
    // before it loads. Any thread.
    std::array<float, 4> getGameGeometry() const;

    // True when getGameGeometry() changed (SET_GEOMETRY's size, SET_ROTATION) since the last call; cleared by this call.
    // Read after step(), whose own aspect refresh has run by then. Any thread.
    bool takeGameGeometryChanged() { return geometryChanged.exchange(false); }

    // True once the running core delivered a frame as RETRO_HW_FRAME_BUFFER_VALID (its own GL framebuffer); false
    // before any frame and while frames are software. Any thread.
    bool isHardwareRendered() const;

    void resetGlobalVariables();

    // Handle callbacks
    void handleVideoRefresh(const void *data, unsigned width, unsigned height, size_t pitch);
    void rethrowVideoCallbackError();
    size_t handleAudioCallback(const int16_t* data, size_t frames);
    int16_t handleSetInputState(unsigned port, unsigned device, unsigned index, unsigned id);
    uintptr_t handleGetCurrentFrameBuffer();

private:
    void updateAudioSampleRateMultiplier();
    // GL thread, in step(): starts/stops the pacer and tells audio and the core (GET_FASTFORWARDING) on a change.
    void applyFastForward(unsigned maxSpeed, bool audible);
    float findDefaultAspectRatio(const retro_system_av_info &system_av_info);
    void afterGameLoad();
    void releaseCore();
    // The input state, replaced by a fresh one when pause()/resume() asked for it. GL thread.
    Input* currentInput();

protected:
    static void callback_hw_video_refresh(const void *data, unsigned width, unsigned height, size_t pitch);
    static size_t callback_set_audio_sample_batch(const int16_t* data, size_t frames);
    static void callback_audio_sample(int16_t left, int16_t right);
    static int16_t callback_set_input_state(unsigned port, unsigned device, unsigned index, unsigned id);
    static uintptr_t callback_get_current_framebuffer();
    static void callback_retro_set_input_poll();

private:
    unsigned int frameSpeed = 1;
    bool audioEnabled = true;
    bool preferLowLatencyAudio = false;
    bool rumbleEnabled = false;
    bool preferVulkan = false;
    bool vulkanSwapchainFrames = false;

    ShaderManager::Config fragmentShaderConfig = ShaderManager::Config {
        ShaderManager::Type::SHADER_DEFAULT, { }
    };

    std::optional<PresetChain> presetChain;
    std::optional<std::string> presetError;

    Rect viewportRect = Rect(0.0F, 0.0F, 1.0F, 1.0F);
    unsigned int viewportAlignment = V_ALIGN_CENTER;

    // Remembered so values set before the Video exists apply once it is created.
    unsigned int scaleMode = SCALE_MODE_FIT;
    float screenOffsetX = 0.0F;
    float screenOffsetY = 0.0F;
    std::vector<ScreenRegion> screenRegions;
    std::vector<ScreenRegion> secondaryRegions;

    std::atomic<float> geometryWidth { 0.0F };
    std::atomic<float> geometryHeight { 0.0F };
    std::atomic<float> geometryAspect { 0.0F };
    std::atomic<float> geometryRotation { 0.0F };
    std::atomic<bool> geometryChanged { false };
    std::atomic<bool> hardwareFrame { false };
    // Set on the GL thread by handleVideoRefresh, rethrown by step() after retro_run returns.
    std::exception_ptr videoCallbackError = nullptr;

    float screenRefreshRate = 60.0;
    int openglESVersion = 2;
    bool skipDuplicateFrames = false;
    bool immersiveModeEnabled = false;
    ImmersiveMode::Config immersiveModeConfig {};

    float defaultAspectRatio = 1.0;
    bool dirtyVideo = false;

    std::mutex coreLock;

    // Under coreLock. A memory map's RAM is found on first use and again when the map changes; without one,
    // RETRO_MEMORY_SYSTEM_RAM is asked for every frame, in case the core moves it.
    std::vector<CheatMemPatch> memoryCheats;
    std::vector<const CheatMemPatch*> memoryCheatList;
    CheatMem cheatMem {};
    bool cheatMemReady = false;
    bool cheatMemLogged = false;
    unsigned cheatMemMapVersion = 0;
    void applyMemoryCheats();

    std::unique_ptr<Core> core;
    bool coreInitialized = false;
    bool gameLoaded = false;
    std::unique_ptr<Audio> audio;
    std::unique_ptr<Video> video;
    std::unique_ptr<FPSSync> fpsSync;

    // Fast forward as asked (setFastForward, any thread) and as applied by step() (GL thread).
    std::atomic<unsigned> fastForwardSpeed { 0 };
    std::atomic<bool> fastForwardAudible { false };
    FastForwardPacer fastForwardPacer;
    unsigned appliedFastForwardSpeed = 0;
    bool appliedFastForwardAudible = false;
    // Read by the audio callback, which threaded cores call off the GL thread: drop the samples (muted FF).
    std::atomic<bool> fastForwardDropAudio { false };
    // Audio playback speed while audible FF runs (the game's achieved speed); 0 otherwise.
    std::atomic<double> fastForwardPlaybackSpeed { 0.0 };
    std::unique_ptr<Input> input;
    std::array<bool, 4> stickyPointers {};   // survive an input reset (pause/resume), see currentInput()
    std::unique_ptr<Rumble> rumble;

    // Set by pause()/resume() (UI thread): the GL thread drops every held key on its next input access.
    std::atomic<bool> inputResetPending { false };
    // Set by resume() (UI thread): the GL thread pushes geometryAspect to the Video on its next step.
    std::atomic<bool> aspectRatioPending { false };
    // context_reset was called and context_destroy not yet. GL thread.
    bool hwContextLive = false;

    // What the loaded game's retro_game_info points at, kept until retro_unload_game.
    std::string gamePath;
    std::unique_ptr<char[]> gameFileData;
    std::unique_ptr<int8_t[]> gameBytes;
};

} //namespace libretrodroid

#endif //LIBRETRODROID_LIBRETRODROID_H
