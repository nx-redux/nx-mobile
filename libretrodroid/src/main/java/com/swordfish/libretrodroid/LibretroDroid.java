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

package com.swordfish.libretrodroid;

import java.util.List;

public class LibretroDroid {

    static {
        System.loadLibrary("libretrodroid");
    }

    public static final int MOTION_SOURCE_DPAD = 0;
    public static final int MOTION_SOURCE_ANALOG_LEFT = 1;
    public static final int MOTION_SOURCE_ANALOG_RIGHT = 2;
    public static final int MOTION_SOURCE_POINTER = 3;

    public static final int SHADER_DEFAULT = 0;
    public static final int SHADER_CRT = 1;
    public static final int SHADER_LCD = 2;
    public static final int SHADER_SHARP = 3;
    public static final int SHADER_UPSCALE_CUT = 4;
    public static final int SHADER_UPSCALE_CUT2 = 5;
    public static final int SHADER_UPSCALE_CUT3 = 6;

    public static final String SHADER_DEFAULT_PARAM_LINEAR = "LINEAR";

    public static final int SCALE_MODE_FIT = 0;
    public static final int SCALE_MODE_INTEGER = 1;
    public static final int SCALE_MODE_STRETCH = 2;

    public static final String SHADER_UPSCALE_CUT_PARAM_USE_DYNAMIC_BLEND = "USE_DYNAMIC_BLEND";
    public static final String SHADER_UPSCALE_CUT_PARAM_BLEND_MIN_CONTRAST_EDGE = "BLEND_MIN_CONTRAST_EDGE";
    public static final String SHADER_UPSCALE_CUT_PARAM_BLEND_MAX_CONTRAST_EDGE = "BLEND_MAX_CONTRAST_EDGE";
    public static final String SHADER_UPSCALE_CUT_PARAM_BLEND_MIN_SHARPNESS = "BLEND_MIN_SHARPNESS";
    public static final String SHADER_UPSCALE_CUT_PARAM_BLEND_MAX_SHARPNESS = "BLEND_MAX_SHARPNESS";
    public static final String SHADER_UPSCALE_CUT_PARAM_STATIC_BLEND_SHARPNESS = "STATIC_BLEND_SHARPNESS";
    public static final String SHADER_UPSCALE_CUT_PARAM_EDGE_USE_FAST_LUMA = "EDGE_USE_FAST_LUMA";
    public static final String SHADER_UPSCALE_CUT_PARAM_EDGE_MIN_VALUE = "EDGE_MIN_VALUE";
    public static final String SHADER_UPSCALE_CUT_PARAM_EDGE_MIN_CONTRAST = "EDGE_MIN_CONTRAST";

    public static final String SHADER_UPSCALE_CUT2_PARAM_USE_DYNAMIC_BLEND = "USE_DYNAMIC_BLEND";
    public static final String SHADER_UPSCALE_CUT2_PARAM_BLEND_MIN_CONTRAST_EDGE = "BLEND_MIN_CONTRAST_EDGE";
    public static final String SHADER_UPSCALE_CUT2_PARAM_BLEND_MAX_CONTRAST_EDGE = "BLEND_MAX_CONTRAST_EDGE";
    public static final String SHADER_UPSCALE_CUT2_PARAM_BLEND_MIN_SHARPNESS = "BLEND_MIN_SHARPNESS";
    public static final String SHADER_UPSCALE_CUT2_PARAM_BLEND_MAX_SHARPNESS = "BLEND_MAX_SHARPNESS";
    public static final String SHADER_UPSCALE_CUT2_PARAM_STATIC_BLEND_SHARPNESS = "STATIC_BLEND_SHARPNESS";
    public static final String SHADER_UPSCALE_CUT2_PARAM_EDGE_USE_FAST_LUMA = "EDGE_USE_FAST_LUMA";
    public static final String SHADER_UPSCALE_CUT2_PARAM_SOFT_EDGES_SHARPENING = "SOFT_EDGES_SHARPENING";
    public static final String SHADER_UPSCALE_CUT2_PARAM_SOFT_EDGES_SHARPENING_AMOUNT = "SOFT_EDGES_SHARPENING_AMOUNT";
    public static final String SHADER_UPSCALE_CUT2_PARAM_HARD_EDGES_SEARCH_MAX_ERROR = "HARD_EDGES_SEARCH_MAX_ERROR";

    public static final String SHADER_UPSCALE_CUT3_PARAM_USE_DYNAMIC_BLEND = "USE_DYNAMIC_BLEND";
    public static final String SHADER_UPSCALE_CUT3_PARAM_BLEND_MIN_CONTRAST_EDGE = "BLEND_MIN_CONTRAST_EDGE";
    public static final String SHADER_UPSCALE_CUT3_PARAM_BLEND_MAX_CONTRAST_EDGE = "BLEND_MAX_CONTRAST_EDGE";
    public static final String SHADER_UPSCALE_CUT3_PARAM_BLEND_MIN_SHARPNESS = "BLEND_MIN_SHARPNESS";
    public static final String SHADER_UPSCALE_CUT3_PARAM_BLEND_MAX_SHARPNESS = "BLEND_MAX_SHARPNESS";
    public static final String SHADER_UPSCALE_CUT3_PARAM_STATIC_BLEND_SHARPNESS = "STATIC_BLEND_SHARPNESS";
    public static final String SHADER_UPSCALE_CUT3_PARAM_EDGE_USE_FAST_LUMA = "EDGE_USE_FAST_LUMA";
    public static final String SHADER_UPSCALE_CUT3_PARAM_SOFT_EDGES_SHARPENING = "SOFT_EDGES_SHARPENING";
    public static final String SHADER_UPSCALE_CUT3_PARAM_SOFT_EDGES_SHARPENING_AMOUNT = "SOFT_EDGES_SHARPENING_AMOUNT";
    public static final String SHADER_UPSCALE_CUT3_PARAM_HARD_EDGES_SEARCH_MAX_ERROR = "HARD_EDGES_SEARCH_MAX_ERROR";
    public static final String SHADER_UPSCALE_CUT3_PARAM_HARD_EDGES_SEARCH_MAX_DISTANCE = "HARD_EDGES_SEARCH_MAX_DISTANCE";

    public static final int ERROR_LOAD_LIBRARY = 0;
    public static final int ERROR_LOAD_GAME = 1;
    public static final int ERROR_GL_NOT_COMPATIBLE = 2;
    public static final int ERROR_SERIALIZATION = 3;
    public static final int ERROR_CHEAT = 4;
    public static final int ERROR_GENERIC = -1;

    public static native void create(
        int GLESVersion,
        String coreFilePath,
        String systemDir,
        String savesDir,
        Variable[] variables,
        GLRetroShader shaderConfig,
        float refreshRate,
        boolean preferLowLatencyAudio,
        boolean enableVirtualFileSystem,
        boolean enableMicrophone,
        boolean skipDuplicateFrames,
        ImmersiveMode immersiveMode,
        String language
    );

    public static native void loadGameFromPath(String gameFilePath);
    public static native void loadGameFromBytes(byte[] gameFileBytes);
    public static native void loadGameFromVirtualFiles(List<DetachedVirtualFile> virtualFiles);
    public static native void resume();

    public static native void onSurfaceCreated();
    public static native void onSurfaceChanged(int width, int height);

    public static native void pause();
    public static native void destroy();

    public static native void step(GLRetroView retroView);

    public static native void reset();

    public static native void setRumbleEnabled(boolean enabled);
    /** The core asked to end (RETRO_ENVIRONMENT_SHUTDOWN) since the last call; cleared by this call. */
    public static native boolean takeShutdownRequest();
    /** getGameGeometry() changed (the core's SET_GEOMETRY size, SET_ROTATION) since the last call; cleared by this call. */
    public static native boolean takeGameGeometryChanged();
    /** Offer the next core Vulkan (bridged into the GLES presentation through AHardwareBuffers). Set before create(). */
    public static native void setPreferVulkan(boolean enabled);
    /** The next core renders into a swapchain on the bridge's surface (Dolphin): its frames are bounded by that surface. Set before create(). */
    public static native void setVulkanSwapchainFrames(boolean enabled);
    /** Whether this device can run the Vulkan bridge (a Vulkan 1.1 GPU with AHardwareBuffer import). Cached; any thread. */
    public static native boolean isVulkanSupported();
    /** With Settings → System → Debug log on at launch: log GL driver messages (GL_KHR_debug, else glGetError) to tag NXGL. Set before create(). */
    public static native void setGlDebugOutput(boolean enabled);
    /** Settings → System → Debug log: false skips the runner's and cores' logs below an error. */
    public static native void setLogEnabled(boolean enabled);
    /** Keep the next core's library loaded after its session (RTLD_NODELETE), so it is never reloaded elsewhere. Set before create(). */
    public static native void setKeepCoreLoaded(boolean keep);
    public static native void setFrameSpeed(int speed);
    public static native void setAudioEnabled(boolean enabled);
    public static native void setShaderConfig(GLRetroShader shader);
    public static native void setViewport(float x, float y, float width, float height);
    public static native void setViewportAlignment(int viewportAlignment);
    public static native void setScaleMode(int scaleMode);
    public static native void setScreenOffset(float x, float y);
    /** 11 floats per region: src l,t,r,b (0..1 of the frame), dst l,t,r,b (0..1 of the viewport), shaded, touch (0/1), alpha (0..1). */
    public static native void setScreenRegions(float[] packed);
    /** Same packing as setScreenRegions; dst is 0..1 of the whole second surface. GL thread. */
    public static native void setSecondaryRegions(float[] packed);
    /** Draws the last frame's secondary regions into the current default framebuffer (width x height); never runs the core. GL thread. */
    public static native void renderSecondary(int width, int height);
    public static native float[] getGameGeometry();

    public static native boolean isHardwareRendered();

    /** The core's recent warning and error log lines since the core was created, oldest first, as "E text" or "W text". */
    public static native String[] getRecentCoreErrors();

    public static native void setShaderChain(
        String[] sources, int[] filterLinear /* -1 unspecified, 0, 1 */, int[] wrap, int[] scaleTypeX, int[] scaleTypeY,
        float[] scaleX, float[] scaleY, int[] frameCountMod, String[] alias,
        String[] lutIds, int[] lutWidth, int[] lutHeight, byte[][] lutRgba, boolean[] lutLinear, int[] lutWrap,
        String[] paramIds, float[] paramValues);
    public static native void clearShaderChain();
    public static native void setShaderParameter(String id, float value);
    /** Null when no chain build failed since the last call. */
    public static native String takeShaderError();

    public static native byte[] serializeState();
    public static native boolean unserializeState(byte[] state);

    public static native void setCheat(int index, boolean enable, String code);
    public static native void resetCheat();
    // nx-mobile: RetroArch RAM-patch cheats, 9 ints each (see libretrodroidjni.cpp); an empty array clears them.
    public static native void setMemoryCheats(int[] fields);

    public static native byte[] serializeSRAM();
    public static native boolean unserializeSRAM(byte[] sram);
    public static native byte[] serializeRTC();
    public static native boolean unserializeRTC(byte[] rtc);

    public static native void updateVariable(Variable variable);
    public static native Variable[] getVariables();
    public static native boolean updateCoreOptionsDisplay();

    public static native int availableDisks();
    public static native int currentDisk();
    public static native void changeDisk(int index);

    public static native void onMotionEvent(int port, int motionSource, float xAxis, float yAxis);
    public static native void onTouchEvent(float xAxis, float yAxis);

    public static native void onKeyEvent(int port, int action, int keyCode);

    /** Analog trigger pressure 0..1 for L2/R2, answered to RETRO_DEVICE_INDEX_ANALOG_BUTTON reads. */
    public static native void onTriggerEvent(int port, float l2, float r2);

    public static native void refreshAspectRatio();

    public static native Controller[][] getControllers();
    public static native void setControllerType(int port, int type);
    public static native void setStickyPointer(int port, boolean sticky);
    public static native void setPointerVelocity(int port, float vx, float vy);

    // The phone's motion for the libretro sensor interface (port 0): accelerometer in g, gyroscope in rad/s.
    public static native void setSensorAvailable(boolean accel, boolean gyro);
    public static native void setSensorEnabled(int port, boolean on);
    public static native void setSensorValues(int port, float ax, float ay, float az, float gx, float gy, float gz);

    // RetroAchievements (rcheevos rc_client in the runner; HTTP and persistence belong to the host).
    public static final int RA_EVENT_ACHIEVEMENT_TRIGGERED = 1;
    public static final int RA_EVENT_CHALLENGE_SHOW = 5;
    public static final int RA_EVENT_CHALLENGE_HIDE = 6;
    public static final int RA_EVENT_PROGRESS_SHOW = 7;
    public static final int RA_EVENT_PROGRESS_HIDE = 8;
    public static final int RA_EVENT_PROGRESS_UPDATE = 9;
    public static final int RA_EVENT_RESET = 14;
    public static final int RA_EVENT_GAME_COMPLETED = 15;
    public static final int RA_EVENT_SERVER_ERROR = 16;
    public static final int RA_EVENT_DISCONNECTED = 17;
    public static final int RA_EVENT_RECONNECTED = 18;
    public static final int RA_EVENT_LOGIN_OK = 100;
    public static final int RA_EVENT_LOGIN_FAILED = 101;
    public static final int RA_EVENT_GAME_LOADED = 102;
    public static final int RA_EVENT_GAME_UNKNOWN = 103;
    public static final int RA_EVENT_GAME_LOAD_FAILED = 104;

    public static native void achievementsEnable(boolean enabled);
    public static native void achievementsLogin(String user, String token);
    public static native void achievementsLoadGame(String hash, int consoleId);
    public static native void achievementsUnloadGame();
    public static native void achievementsServerResponse(int requestId, int httpStatus, String body);
    public static native AchievementInfo[] achievementsList();
    public static native int[] achievementsSummary();
    /** {url, postData} for a softcore award of [achievementId], signed by rcheevos; null when the parameters are rejected. */
    public static native String[] achievementsBuildAwardRequest(String user, String token, int achievementId, String gameHash, int secondsSinceUnlock);
    /** md5 of the game for RetroAchievements, or null. [files] as loadGameFromVirtualFiles; the fds are closed by the call. */
    public static native String achievementsHash(int consoleId, List<DetachedVirtualFile> files);
    /** Where the Dolphin core is, for hashing Wii/GameCube discs; only stored here, loaded per hash. */
    public static native void achievementsSetDiscLibrary(String path);
    /** A CD CHD's first data track [sectors], 2048 bytes of user data each (null: unreadable), or null when [fd] is not a CHD. Closes [fd]. */
    public static native byte[][] chdReadSectors(int fd, int[] sectors);
}
