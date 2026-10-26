/*
 *     Copyright (C) 2022  Filippo Scognamiglio
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

package com.swordfish.libretrodroid

import android.app.ActivityManager
import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.coroutineScope
import com.swordfish.libretrodroid.KtUtils.awaitUninterruptibly
import com.swordfish.libretrodroid.gamepad.GamepadsManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.properties.Delegates

class GLRetroView(
    context: Context,
    private val data: GLRetroViewData,
) : GLSurfaceView(context), LifecycleObserver {

    var audioEnabled: Boolean by Delegates.observable(true) { _, _, value ->
        LibretroDroid.setAudioEnabled(value)
    }

    var frameSpeed: Int by Delegates.observable(1) { _, _, value ->
        LibretroDroid.setFrameSpeed(value)
    }

    /**
     * Fast forward: 0 off, else the speed cap (2 = up to 2x the content's frame rate). Kept apart from [frameSpeed],
     * which pauses (0) and resumes (1) without touching it. Ignored in netplay and GBA link sessions. Main thread;
     * applied on the GL thread, and re-applied after create().
     */
    var fastForward: Int by Delegates.observable(0) { _, old, value ->
        if (old != value) pushFastForward()
    }

    /** Whether fast forward plays the game's audio (sped up) or mutes it. Main thread, like [fastForward]. */
    var fastForwardAudio: Boolean by Delegates.observable(false) { _, old, value ->
        if (old != value) pushFastForward()
    }

    private fun pushFastForward() {
        val speed = fastForward
        val audible = fastForwardAudio
        queueEvent { LibretroDroid.setFastForward(speed, audible) }
    }

    /**
     * Rewind snapshots: on, the game is snapshotted every frame (or every 2nd/4th for big states) once the core can
     * serialize; off frees the buffer. Ignored in netplay and GBA link sessions. Main thread; applied on the GL thread
     * and re-applied after create(), like [fastForward].
     */
    var rewindEnabled: Boolean by Delegates.observable(false) { _, old, value ->
        if (old != value) pushRewind()
    }

    /** Steps back through the snapshots instead of playing (wins over [fastForward]). Watch [rewindStatus] for EMPTY. */
    var rewinding: Boolean by Delegates.observable(false) { _, old, value ->
        if (old != value) pushRewind()
    }

    /** Whether rewound frames play their audio. */
    var rewindAudio: Boolean by Delegates.observable(false) { _, old, value ->
        if (old != value) pushRewind()
    }

    private fun pushRewind() {
        val enabled = rewindEnabled
        val active = rewinding
        val audible = rewindAudio
        queueEvent {
            LibretroDroid.setRewindAudio(audible)
            LibretroDroid.setRewindEnabled(enabled)
            LibretroDroid.setRewinding(active)
        }
    }

    /** What rewind did on the last frame. Any thread. */
    fun rewindStatus(): RewindStatus = RewindStatus.fromNative(LibretroDroid.rewindStatus())

    /** Applied on the emulation thread (the renderer reads it there); takes effect on the next drawn frame. */
    var shader: ShaderConfig by Delegates.observable(data.shader) { _, _, value ->
        val shader = buildShader(value)
        queueEvent { catchExceptions { LibretroDroid.setShaderConfig(shader) } }
    }

    var viewport: RectF by Delegates.observable(RectF(0f, 0f, 1f, 1f)) { _, _, value ->
        val left = value.left
        val top = value.top
        val width = value.width()
        val height = value.height()
        queueEvent { LibretroDroid.setViewport(left, top, width, height) }
    }

    /** Applied on the emulation thread, which owns the native Video. */
    var viewportAlignment: ViewportAlignment by Delegates.observable(ViewportAlignment.CENTER) { _, _, value ->
        queueEvent { LibretroDroid.setViewportAlignment(value.value) }
    }

    /** Applied on the emulation thread; takes effect on the next rendered (or paused-redrawn) frame. */
    var scaleMode: ScaleMode by Delegates.observable(ScaleMode.FIT) { _, _, value ->
        queueEvent { LibretroDroid.setScaleMode(value.value) }
    }

    /** Picture offset in physical pixels (positive = right / down), applied after scaling. */
    var screenOffset: PointF by Delegates.observable(PointF(0f, 0f)) { _, _, value ->
        val x = value.x
        val y = value.y
        queueEvent { LibretroDroid.setScreenOffset(x, y) }
    }

    /**
     * While true the view draws black instead of the picture: set before the game runs, it keeps the first frames from
     * showing at a provisional place until the frontend has laid the picture out from the core's geometry (set false
     * after that layout, which is queued first, so the first picture shown is at its place). Re-applied after create().
     */
    var pictureHeld: Boolean by Delegates.observable(false) { _, old, value ->
        if (old == value) return@observable
        queueEvent { LibretroDroid.setPictureHeld(value) }
    }

    /**
     * Parts of the core frame drawn at places in the view (a DS screen each); empty = the single picture. While set,
     * [scaleMode] and [screenOffset] don't apply; dst is relative to [viewport]. Touches map through the topmost region
     * ([ScreenRegions.mapTouch]). Main thread; applied on the GL thread, and re-applied after create().
     */
    var screenRegions: List<ScreenRegion> by Delegates.observable(emptyList()) { _, old, value ->
        if (old == value) return@observable
        val packed = ScreenRegions.pack(value)
        queueEvent { LibretroDroid.setScreenRegions(packed) }
    }

    /**
     * Parts of the core frame drawn on the second display's surface ([setSecondarySurface]); dst is 0..1 of that whole
     * surface. Main thread; applied on the GL thread, and re-applied after create().
     */
    var secondaryRegions: List<ScreenRegion> by Delegates.observable(emptyList()) { _, old, value ->
        if (old == value) return@observable
        val packed = ScreenRegions.pack(value)
        queueEvent { LibretroDroid.setSecondaryRegions(packed) }
    }

    // GL thread only.
    private val secondaryOutput = SecondaryOutput()

    /**
     * The second display's surface ([width] x [height] px), drawn with [secondaryRegions] after each frame; null tears
     * it down. Main thread. A null waits (briefly) for the GL thread to destroy the EGL surface, so the caller may
     * release the Surface right after; drawing to a surface that went bad stops until a new one is set.
     */
    fun setSecondarySurface(surface: Surface?, width: Int, height: Int) {
        // Setting a surface needn't wait; nor does a null on a detached view (its exited GL thread took the EGL surface).
        if (surface != null || emulationThreadGone) {
            queueEvent { secondaryOutput.setSurface(surface, width, height) }
            return
        }
        val latch = CountDownLatch(1)
        queueEvent {
            try {
                secondaryOutput.setSurface(null, 0, 0)
            } finally {
                latch.countDown()
            }
        }
        if (!latch.awaitUninterruptibly(SECONDARY_TEARDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            NxLog.w(TAG_LOG, "GL thread did not drop the second surface within $SECONDARY_TEARDOWN_TIMEOUT_MS ms")
        }
    }

    /** Where a touch at ([x01], [y01], 0..1 of the second surface) lands in the core frame; null outside a touch region. */
    fun mapSecondaryTouch(x01: Float, y01: Float): FramePoint? =
        ScreenRegions.mapTouch(secondaryRegions, ScreenRect(0f, 0f, 1f, 1f), x01, y01)

    /**
     * Keeps the touch pointer where it was when the finger lifts or leaves the picture, instead of releasing it. Set for
     * Wii, where Dolphin's IR pointer should stay aimed. Applies to every port (players 2-4 aim with their right stick),
     * so each keeps its pointer across a pause. Main thread; applied on the GL thread, and re-applied after create().
     */
    var stickyPointer: Boolean by Delegates.observable(false) { _, _, value ->
        queueEvent { setStickyPointers(value) }
    }

    /** GL thread: [stickyPointer] on all of the runner's pointer ports. */
    private fun setStickyPointers(sticky: Boolean) {
        for (port in 0 until POINTER_PORTS) LibretroDroid.setStickyPointer(port, sticky)
    }

    /** Moves the port's pointer by ([vx], [vy]) picture widths per second each frame (a stick); 0,0 stops it. */
    fun setPointerVelocity(port: Int, vx: Float, vy: Float) = queueEvent { LibretroDroid.setPointerVelocity(port, vx, vy) }

    /** The core's pointer at ([x01], [y01]) in 0..1 of the core frame, pressed; or released when not [pressed]. */
    fun sendPointer(x01: Float, y01: Float, pressed: Boolean) {
        if (pressed) sendMotionEvent(MOTION_SOURCE_POINTER, x01, y01) else sendMotionEvent(MOTION_SOURCE_POINTER, -1f, -1f)
    }

    /** A RetroArch GLSL chain drawn instead of [shader]; null = the built-in [shader]. Queued on the GL thread; survives create(). */
    var shaderChain: ShaderChainData? by Delegates.observable(null) { _, _, value ->
        shaderParameterOverrides.clear()
        queueEvent { pushShaderChain(value) }
    }

    /** Updates one preset parameter without recompiling. Kept until [shaderChain] is next assigned. */
    fun setShaderParameter(id: String, value: Float) {
        shaderParameterOverrides[id] = value
        queueEvent { LibretroDroid.setShaderParameter(id, value) }
    }

    // Main thread only. Merged into the chain when reapplyDisplaySettings re-pushes it, so tweaks survive create().
    private val shaderParameterOverrides = mutableMapOf<String, Float>()

    // GL thread only. A chain queued while no context was current builds with the next drawn frame; its error is
    // polled once after the first frame of each surface and after each push.
    private var shaderErrorPolled = false

    /**
     * Called on the main thread with "pass N: <log>" when a chain fails to build; the view then draws [shader].
     * The view keeps [shaderChain] as set, so the host is expected to set it to null, or the broken chain is
     * pushed (and fails) again whenever the display settings are reapplied.
     */
    var onShaderError: ((String) -> Unit)? = null

    private val openGLESVersion: Int

    private var isGameLoaded = false
    private var isEmulationReady = false
    private var isAborted = false

    // Mirrors the GLThread's lifetime: GLSurfaceView exits it on detach and recreates it on re-attach.
    @Volatile
    private var emulationThreadGone = false

    // nx-mobile: uptimeMillis when the GL thread entered the running step, 0 outside one. A core hung inside its frame
    // never leaves it (and keeps the core's lock): see [stepStalledMillis].
    @Volatile
    private var stepStartedAt = 0L

    /** nx-mobile: how long the GL thread has been inside the current step; 0 when it is not in one. Any thread. */
    fun stepStalledMillis(): Long {
        val t = stepStartedAt
        return if (t == 0L) 0L else SystemClock.uptimeMillis() - t
    }

    // Emitted with tryEmit from the GL thread every frame: no coroutine per frame, and a slow collector drops the oldest
    // FrameRendered instead of piling up suspended emitters. Replays the last event to a new collector, as before.
    private val retroGLEventsSubject = MutableSharedFlow<GLRetroEvents>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val retroGLIssuesErrors = MutableSharedFlow<Int>(1)
    // Its own flow, not retroGLEventsSubject: there a main thread busy for a few frames would drop it under FrameRendered.
    // One buffered change is enough, as the collector reads the geometry afresh.
    private val gameGeometryChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val rumbleEventsSubject = MutableSharedFlow<RumbleEvent>()
    private val shutdownRequests = MutableSharedFlow<Unit>(replay = 1)

    // Buffered so tryEmit from the GL thread never suspends; the host collects promptly.
    private val achievementEvents = MutableSharedFlow<AchievementEvent>(extraBufferCapacity = 64)
    // Unlimited channel: a server call must never be dropped, or rc_client waits for its answer forever.
    private val achievementServerCalls = Channel<AchievementServerCall>(Channel.UNLIMITED)

    private var lifecycle: Lifecycle? = null

    init {
        openGLESVersion = getGLESVersion(context)
        preserveEGLContextOnPause = true
        setEGLContextClientVersion(openGLESVersion)
        setRenderer(Renderer())
        keepScreenOn = true
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate(lifecycleOwner: LifecycleOwner) = catchExceptions {
        lifecycle = lifecycleOwner.lifecycle
        LibretroDroid.setGlDebugOutput(data.glDebugOutput)
        LibretroDroid.setPreferVulkan(data.preferVulkan)
        LibretroDroid.setVulkanSwapchainFrames(data.vulkanSwapchainFrames)
        LibretroDroid.setKeepCoreLoaded(data.keepCoreLoaded)
        LibretroDroid.create(
            openGLESVersion,
            data.coreFilePath,
            data.systemDirectory,
            data.savesDirectory,
            data.variables,
            buildShader(shader),
            getDefaultRefreshRate(),
            data.preferLowLatencyAudio,
            data.gameVirtualFiles.isNotEmpty(),
            data.enableMicrophone,
            data.skipDuplicateFrames,
            data.immersiveMode,
            getDeviceLanguage()
        )
        LibretroDroid.setRumbleEnabled(data.rumbleEventsEnabled)
        val alignment = data.viewportAlignment.value
        queueEvent { LibretroDroid.setViewportAlignment(alignment) }
        reapplyDisplaySettings()
        pushFastForward()
        pushRewind()
    }

    /**
     * Pushes this view's remembered display settings to native, queued on the GL thread after create(), so values
     * set at any time (even before create) win over whatever an earlier session left in the native singleton.
     */
    private fun reapplyDisplaySettings() {
        val rect = viewport
        val left = rect.left
        val top = rect.top
        val width = rect.width()
        val height = rect.height()
        val mode = scaleMode.value
        val offsetX = screenOffset.x
        val offsetY = screenOffset.y
        val regions = ScreenRegions.pack(screenRegions)
        val secondary = ScreenRegions.pack(secondaryRegions)
        val sticky = stickyPointer
        val held = pictureHeld
        val chain = shaderChain?.let { it.copy(params = it.params + shaderParameterOverrides) }
        queueEvent {
            LibretroDroid.setViewport(left, top, width, height)
            LibretroDroid.setScaleMode(mode)
            LibretroDroid.setScreenOffset(offsetX, offsetY)
            LibretroDroid.setPictureHeld(held)
            LibretroDroid.setScreenRegions(regions)
            LibretroDroid.setSecondaryRegions(secondary)
            setStickyPointers(sticky)
            pushShaderChain(chain)
        }
    }

    /**
     * Runs on the GL thread. With a Video the chain builds synchronously, so a failure is read back right away and
     * reported on the main thread; before the Video exists it is remembered and built (errors only logged) with it.
     */
    private fun pushShaderChain(chain: ShaderChainData?) = catchExceptions {
        if (chain == null) {
            LibretroDroid.clearShaderChain()
        } else {
            val passes = chain.passes
            val luts = chain.luts
            val params = chain.params.entries.toList()
            LibretroDroid.setShaderChain(
                passes.map { it.source }.toTypedArray(),
                passes.map { pass -> pass.filterLinear?.let { if (it) 1 else 0 } ?: -1 }.toIntArray(),
                passes.map { it.wrap.value }.toIntArray(),
                passes.map { it.scaleTypeX.value }.toIntArray(),
                passes.map { it.scaleTypeY.value }.toIntArray(),
                passes.map { it.scaleX }.toFloatArray(),
                passes.map { it.scaleY }.toFloatArray(),
                passes.map { it.frameCountMod }.toIntArray(),
                passes.map { it.alias }.toTypedArray(),
                luts.map { it.id }.toTypedArray(),
                luts.map { it.width }.toIntArray(),
                luts.map { it.height }.toIntArray(),
                luts.map { it.rgba }.toTypedArray(),
                luts.map { it.linear }.toBooleanArray(),
                luts.map { it.wrap.value }.toIntArray(),
                params.map { it.key }.toTypedArray(),
                params.map { it.value }.toFloatArray(),
            )
        }
        reportShaderError()
        // Built later if no context was current (paused); resuming may not recreate the surface, so poll again.
        shaderErrorPolled = false
    }

    private fun reportShaderError() {
        LibretroDroid.takeShaderError()?.let { message -> post { onShaderError?.invoke(message) } }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() = catchExceptions {
        LibretroNetplay.linkUnblock()   // nx-mobile: a held GBA link cable must not block the GL thread destroy waits on
        destroyOnEmulationThread()
        lifecycle = null
    }

    // Set by whichever runs the native destroy first: the queued one, or the caller after a timeout.
    private val nativeDestroyed = AtomicBoolean(false)

    private fun destroyNative() {
        if (nativeDestroyed.compareAndSet(false, true)) LibretroDroid.destroy()
    }

    /**
     * Runs the native destroy on the GL thread and waits for it, so the core's context_destroy, retro_unload_game and
     * retro_deinit run where the GL objects live (native makes the paused view's preserved context current) and no
     * queued GL-thread event uses the Video while it goes. The GL thread runs queued events while paused, and it
     * still exists at ON_DESTROY (the view detaches after). Falls back to this thread when it has exited, or after
     * [DESTROY_TIMEOUT_SECONDS] if it is wedged (the old behaviour); the queued one then does nothing.
     */
    private fun destroyOnEmulationThread() {
        if (emulationThreadGone || Thread.currentThread().name.startsWith("GLThread")) {
            destroyNative()
            return
        }
        val latch = CountDownLatch(1)
        var error: Throwable? = null
        queueEvent {
            try {
                destroyNative()
            } catch (t: Throwable) {
                error = t
            } finally {
                latch.countDown()
            }
        }
        if (!latch.awaitUninterruptibly(DESTROY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            NxLog.e(TAG_LOG, "GL thread did not run destroy within $DESTROY_TIMEOUT_SECONDS s; destroying here")
            destroyNative()
            return
        }
        error?.let { throw it }
    }

    // A BCP 47 tag ("ja-JP", "zh-Hant-TW"): native picks Simplified or Traditional Chinese from its script or region.
    private fun getDeviceLanguage() = Locale.getDefault().toLanguageTag()

    private fun getDefaultRefreshRate(): Float {
        return (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate
    }

    fun sendKeyEvent(action: Int, keyCode: Int, port: Int = 0) {
        queueEvent { LibretroDroid.onKeyEvent(port, action, keyCode) }
    }

    fun sendMotionEvent(source: Int, xAxis: Float, yAxis: Float, port: Int = 0) {
        queueEvent { LibretroDroid.onMotionEvent(port, source, xAxis, yAxis) }
    }

    /**
     * Analog L2/R2 pressure (0..1) for cores that read RETRO_DEVICE_INDEX_ANALOG_BUTTON. A non-zero value wins over
     * the L2/R2 key; at 0 (or before the first call) a pressed key reads as full.
     */
    fun sendTriggers(port: Int, l2: Float, r2: Float) {
        queueEvent { LibretroDroid.onTriggerEvent(port, l2, r2) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        emulationThreadGone = false
    }

    override fun onDetachedFromWindow() {
        emulationThreadGone = true
        super.onDetachedFromWindow()
    }

    // The finger that drives the pointer: the first one down; others are ignored until it lifts.
    private var touchPointerId = MotionEvent.INVALID_POINTER_ID

    /**
     * Touches at or below this y (view px) do not reach the game: the host's controls cover the picture there. A finger
     * that slides below it lifts the pointer. Main thread.
     */
    var touchCutoffY: Float = Float.POSITIVE_INFINITY

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event ?: return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (event.getY(0) < touchCutoffY) {
                touchPointerId = event.getPointerId(0)
                touchAt(event.getX(0), event.getY(0))
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(touchPointerId)
                if (index >= 0) touchAt(event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_POINTER_UP ->
                if (event.getPointerId(event.actionIndex) == touchPointerId) touchReleased()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchReleased()
        }
        return true
    }

    private fun touchAt(x: Float, y: Float) {
        if (y >= touchCutoffY) { liftPointer(); return }
        val regions = screenRegions
        if (regions.isEmpty()) {
            val position = normalizeTouchCoordinates(x, y)
            // Queued: native maps it through the Video's layout, which the GL thread owns.
            queueEvent { LibretroDroid.onTouchEvent(position.x, position.y) }
            return
        }
        if (width <= 0 || height <= 0) return
        val rect = viewport
        val point = ScreenRegions.mapTouch(regions, ScreenRect(rect.left, rect.top, rect.right, rect.bottom), x / width, y / height)
        if (point != null) sendPointer(point.x, point.y, true) else sendPointer(0f, 0f, false)
    }

    private fun touchReleased() {
        touchPointerId = MotionEvent.INVALID_POINTER_ID
        liftPointer()
    }

    private fun liftPointer() {
        if (screenRegions.isEmpty()) {
            if (stickyPointer) return   // the native pointer would ignore the release anyway
            queueEvent { LibretroDroid.onTouchEvent(TOUCH_EVENT_OUTSIDE.x, TOUCH_EVENT_OUTSIDE.y) }
        } else {
            sendPointer(0f, 0f, false)
        }
    }

    private fun clamp(x: Float, min: Float, max: Float) = minOf(maxOf(x, min), max)

    private fun normalizeTouchCoordinates(x: Float, y: Float): PointF {
        val x = clamp(2f * x / width - 1f, -1f, +1f)
        val y = clamp(2f * y / height - 1f, -1f, +1f)
        return PointF(x, y)
    }

    fun serializeState(useEmulationThread: Boolean = true): ByteArray {
        return runOnEmulationThread(useEmulationThread) {
            LibretroDroid.serializeState()
        }
    }

    fun setCheat(index: Int, enable: Boolean, code: String, useEmulationThread: Boolean = true) {
        runOnEmulationThread(useEmulationThread) {
            LibretroDroid.setCheat(index, enable, code)
        }
    }

    fun unserializeState(data: ByteArray, useEmulationThread: Boolean = true): Boolean {
        return runOnEmulationThread(useEmulationThread) {
            LibretroDroid.unserializeState(data)
        }
    }

    fun serializeSRAM(useEmulationThread: Boolean = true): ByteArray {
        return runOnEmulationThread(useEmulationThread) {
            LibretroDroid.serializeSRAM()
        }
    }

    fun unserializeSRAM(data: ByteArray, useEmulationThread: Boolean = true): Boolean {
        return runOnEmulationThread(useEmulationThread) {
            LibretroDroid.unserializeSRAM(data)
        }
    }

    /** The core's clock memory (`RETRO_MEMORY_RTC`); empty when it has none. */
    fun serializeRTC(useEmulationThread: Boolean = true): ByteArray =
        runOnEmulationThread(useEmulationThread) { LibretroDroid.serializeRTC() }

    /** Restarts the game (retro_reset), dropping the rewind history. On the GL thread between steps, never mid-frame. */
    fun reset(useEmulationThread: Boolean = true) = runOnEmulationThread(useEmulationThread) {
        LibretroDroid.reset()
    }

    fun getGLRetroEvents(): Flow<GLRetroEvents> {
        return retroGLEventsSubject
    }

    /**
     * Emits after a frame that changed [getGameGeometry] (the core's SET_GEOMETRY size and aspect, or its SET_ROTATION):
     * nothing per frame otherwise. Changes before a collector starts are not replayed.
     */
    fun getGameGeometryChanges(): Flow<Unit> = gameGeometryChanges

    fun getGLRetroErrors(): Flow<Int> {
        return retroGLIssuesErrors
    }

    /** The core asked to end (RETRO_ENVIRONMENT_SHUTDOWN): the host should close the game as its own Quit would. */
    fun getShutdownRequests(): Flow<Unit> = shutdownRequests

    fun getRumbleEvents(): Flow<RumbleEvent> {
        return rumbleEventsSubject
    }

    /**
     * rc_client events. Not buffered for late subscribers: events emitted before collection starts are lost,
     * so subscribe before calling `LibretroDroid.achievementsLogin`.
     */
    fun getAchievementEvents(): Flow<AchievementEvent> = achievementEvents

    /**
     * HTTP requests rc_client wants sent. Buffered until collected (single consumer); every call must be answered
     * with `LibretroDroid.achievementsServerResponse(requestId, …)` or rc_client keeps waiting for it.
     */
    fun getAchievementServerCalls(): Flow<AchievementServerCall> = achievementServerCalls.receiveAsFlow()

    fun getControllers(): Array<Array<Controller>> {
        return LibretroDroid.getControllers()
    }

    fun setControllerType(port: Int, type: Int) {
        LibretroDroid.setControllerType(port, type)
    }

    fun getVariables(): Array<Variable> {
        return LibretroDroid.getVariables()
    }

    /**
     * `[baseWidth, baseHeight, aspectRatio, rotation]` of the running game (rotation in radians, the core's SET_ROTATION);
     * zeros before it loads. Safe from any thread.
     */
    fun getGameGeometry(): FloatArray = LibretroDroid.getGameGeometry()

    /** True once the running core's frames come from its own GL framebuffer (a hardware-rendered core). Safe from any thread. */
    fun isHardwareRendered(): Boolean = LibretroDroid.isHardwareRendered()

    /** The core's last warning and error log lines ("E text" / "W text", oldest first), e.g. why it refused a game. Safe from any thread. */
    fun getRecentCoreErrors(): List<String> = LibretroDroid.getRecentCoreErrors().toList()

    fun updateVariables(vararg variables: Variable) {
        variables.forEach {
            LibretroDroid.updateVariable(it)
        }
    }

    /** Asks the core to re-evaluate which options are visible; returns true if any visibility changed. */
    fun refreshOptionsVisibility(useEmulationThread: Boolean = true): Boolean {
        return runOnEmulationThread(useEmulationThread) { LibretroDroid.updateCoreOptionsDisplay() }
    }

    fun getAvailableDisks(useEmulationThread: Boolean = true): Int {
        return runOnEmulationThread(useEmulationThread) { LibretroDroid.availableDisks() }
    }

    fun getCurrentDisk(useEmulationThread: Boolean = true): Int {
        return runOnEmulationThread(useEmulationThread) { LibretroDroid.currentDisk() }
    }

    fun changeDisk(index: Int, useEmulationThread: Boolean = true) {
        runOnEmulationThread(useEmulationThread) { LibretroDroid.changeDisk(index) }
    }

    private fun getGLESVersion(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return if (activityManager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000) {
            3
        } else {
            2
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val mappedKey = GamepadsManager.getGamepadKeyEvent(keyCode)
        val port = (event?.device?.controllerNumber ?: 0) - 1

        if (event != null && port >= 0 && keyCode in GamepadsManager.GAMEPAD_KEYS) {
            sendKeyEvent(KeyEvent.ACTION_DOWN, mappedKey, port)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val mappedKey = GamepadsManager.getGamepadKeyEvent(keyCode)
        val port = (event?.device?.controllerNumber ?: 0) - 1

        if (event != null && port >= 0 && keyCode in GamepadsManager.GAMEPAD_KEYS) {
            sendKeyEvent(KeyEvent.ACTION_UP, mappedKey, port)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        val port = (event?.device?.controllerNumber ?: 0) - 1
        if (port >= 0) {
            when (event?.source) {
                InputDevice.SOURCE_JOYSTICK -> {
                    sendMotionEvent(
                        MOTION_SOURCE_DPAD,
                        event.getAxisValue(MotionEvent.AXIS_HAT_X),
                        event.getAxisValue(MotionEvent.AXIS_HAT_Y),
                        port
                    )
                    sendMotionEvent(
                        MOTION_SOURCE_ANALOG_LEFT,
                        event.getAxisValue(MotionEvent.AXIS_X),
                        event.getAxisValue(MotionEvent.AXIS_Y),
                        port
                    )
                    sendMotionEvent(
                        MOTION_SOURCE_ANALOG_RIGHT,
                        event.getAxisValue(MotionEvent.AXIS_Z),
                        event.getAxisValue(MotionEvent.AXIS_RZ),
                        port
                    )
                }
            }
        }
        return super.onGenericMotionEvent(event)
    }

    // These functions are called only after the GLSurfaceView has been created.
    private inner class RenderLifecycleObserver : LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
        private fun resume() = catchExceptions {
            LibretroDroid.resume()
            onResume()
            isEmulationReady = true
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        private fun pause() = catchExceptions {
            isEmulationReady = false
            LibretroNetplay.linkUnblock()   // nx-mobile: a held GBA link cable must not block the GL thread this pause waits on
            onPause()
            LibretroDroid.pause()
        }
    }

    inner class Renderer : GLSurfaceView.Renderer {
        override fun onDrawFrame(gl: GL10) = catchExceptions {
            if (isEmulationReady) {
                stepStartedAt = SystemClock.uptimeMillis()
                try { LibretroDroid.step(this@GLRetroView) } finally { stepStartedAt = 0L }
                retroGLEventsSubject.tryEmit(GLRetroEvents.FrameRendered)
                if (LibretroDroid.takeShutdownRequest()) shutdownRequests.tryEmit(Unit)
                if (LibretroDroid.takeGameGeometryChanged()) gameGeometryChanges.tryEmit(Unit)
                if (!shaderErrorPolled) {
                    shaderErrorPolled = true
                    reportShaderError()
                }
                secondaryOutput.draw()
            }
        }

        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) = catchExceptions {
            Thread.currentThread().priority = Thread.MAX_PRIORITY
            LibretroDroid.onSurfaceChanged(width, height)
        }


        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) = catchExceptions {
            Thread.currentThread().priority = Thread.MAX_PRIORITY
            shaderErrorPolled = false
            // A new context: the second surface's EGL surface went with the old one; re-made on the next draw.
            secondaryOutput.contextLost()
            initializeCore()
            retroGLEventsSubject.tryEmit(GLRetroEvents.SurfaceCreated)
        }
    }

    // These functions are called from the GL thread.
    private fun initializeCore() = catchExceptions {
        if (isGameLoaded) {
            // GLSurfaceView calls onSurfaceCreated only for a new EGL context: the old one (and every GL name in it)
            // is gone, so native rebuilds its Video and resets a GL core's context.
            LibretroDroid.onSurfaceCreated()
            return@catchExceptions
        }
        when {
            data.gameFilePath != null -> loadGameFromPath(data.gameFilePath!!)
            data.gameFileBytes != null -> loadGameFromBytes(data.gameFileBytes!!)
            data.gameVirtualFiles.isNotEmpty() -> loadGameFromVirtualFiles(data.gameVirtualFiles)
        }
        // After load, before the first retro_run: cores such as flycast only plug a pad in at that point.
        data.controllerPorts.forEach { (port, type) -> LibretroDroid.setControllerType(port, type) }
        data.saveRAMState?.let {
            LibretroDroid.unserializeSRAM(data.saveRAMState)
            data.saveRAMState = null
        }
        data.rtcState?.let {
            LibretroDroid.unserializeRTC(it)
            data.rtcState = null
        }
        LibretroDroid.onSurfaceCreated()
        isGameLoaded = true

        KtUtils.runOnUIThread {
            lifecycle?.addObserver(RenderLifecycleObserver())
        }
    }

    private fun loadGameFromVirtualFiles(virtualFiles: List<VirtualFile>) {
        val detachedVirtualFiles = virtualFiles
            .map { DetachedVirtualFile(it.virtualPath, it.fileDescriptor.detachFd()) }
        LibretroDroid.loadGameFromVirtualFiles(detachedVirtualFiles)
    }

    private fun loadGameFromBytes(gameFileBytes: ByteArray) {
        LibretroDroid.loadGameFromBytes(gameFileBytes)
    }

    private fun loadGameFromPath(gameFilePath: String) {
        LibretroDroid.loadGameFromPath(gameFilePath)
    }

    private fun catchExceptions(block: () -> Unit) {
        try {
            if (isAborted) return
            block()
        } catch (e: RetroException) {
            GlobalScope.launch {
                retroGLIssuesErrors.emit(e.errorCode)
            }
            isAborted = true
        } catch (e: Exception) {
            NxLog.e(TAG_LOG, "Error in GLRetroView", e)
            GlobalScope.launch {
                retroGLIssuesErrors.emit(LibretroDroid.ERROR_GENERIC)
            }
        }
    }

    /**
     * Runs [block] on the GL (emulation) thread and waits for its result.
     *
     * @throws IllegalStateException if the view is detached, since its GL thread has exited and would never run
     * [block], or if the GL thread does not run [block] within [EMULATION_THREAD_TIMEOUT_SECONDS] seconds (queued
     * events also run while paused, so only a dead or wedged GL thread reaches the timeout). A request that timed out
     * is not cancelled and may still run if the GL thread recovers, so its effect can land after the caller gave up.
     * An exception thrown by [block] is caught on the GL thread (it would otherwise end it) and rethrown here.
     */
    private fun <T> runOnEmulationThread(useEmulationThread: Boolean, block: () -> T): T {
        if (!useEmulationThread || Thread.currentThread().name.startsWith("GLThread")) {
            return block()
        }

        if (emulationThreadGone) {
            throw IllegalStateException("GLRetroView is detached; the emulation thread has exited")
        }

        val latch = CountDownLatch(1)
        var result: Result<T>? = null
        queueEvent {
            result = try {
                Result.success(block())
            } catch (t: Throwable) {
                Result.failure(t)
            }
            latch.countDown()
        }

        if (!latch.awaitUninterruptibly(EMULATION_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw IllegalStateException(
                "emulation thread did not run the request within $EMULATION_THREAD_TIMEOUT_SECONDS s"
            )
        }
        return result!!.getOrThrow()
    }

    private fun buildShader(config: ShaderConfig): GLRetroShader {
        return when (config) {
            is ShaderConfig.Default -> GLRetroShader(
                LibretroDroid.SHADER_DEFAULT,
                if (config.linear) emptyMap() else mapOf(LibretroDroid.SHADER_DEFAULT_PARAM_LINEAR to "0"),
            )
            is ShaderConfig.CRT -> GLRetroShader(LibretroDroid.SHADER_CRT)
            is ShaderConfig.LCD -> GLRetroShader(LibretroDroid.SHADER_LCD)
            is ShaderConfig.Sharp -> GLRetroShader(LibretroDroid.SHADER_SHARP)
            is ShaderConfig.CUT -> GLRetroShader(
                LibretroDroid.SHADER_UPSCALE_CUT,
                buildParams(
                    LibretroDroid.SHADER_UPSCALE_CUT_PARAM_USE_DYNAMIC_BLEND to toParam(config.useDynamicBlend),
                    LibretroDroid.SHADER_UPSCALE_CUT_PARAM_BLEND_MIN_CONTRAST_EDGE to toParam(config.blendMinContrastEdge),
                    LibretroDroid.SHADER_UPSCALE_CUT_PARAM_BLEND_MAX_CONTRAST_EDGE to toParam(config.blendMaxContrastEdge),
                    LibretroDroid.SHADER_UPSCALE_CUT_PARAM_BLEND_MIN_SHARPNESS to toParam(config.blendMinSharpness),
                    LibretroDroid.SHADER_UPSCALE_CUT_PARAM_BLEND_MAX_SHARPNESS to toParam(config.blendMaxSharpness),
                    LibretroDroid.SHADER_UPSCALE_CUT_PARAM_STATIC_BLEND_SHARPNESS to toParam(config.staticSharpness),
                    LibretroDroid.SHADER_UPSCALE_CUT_PARAM_EDGE_USE_FAST_LUMA to toParam(config.edgeUseFastLuma),
                    LibretroDroid.SHADER_UPSCALE_CUT_PARAM_EDGE_MIN_VALUE to toParam(config.edgeMinValue),
                    LibretroDroid.SHADER_UPSCALE_CUT_PARAM_EDGE_MIN_CONTRAST to toParam(config.edgeMinContrast),
                )
            )

            is ShaderConfig.CUT2 -> GLRetroShader(
                LibretroDroid.SHADER_UPSCALE_CUT2,
                buildParams(
                    LibretroDroid.SHADER_UPSCALE_CUT2_PARAM_USE_DYNAMIC_BLEND to toParam(config.useDynamicBlend),
                    LibretroDroid.SHADER_UPSCALE_CUT2_PARAM_BLEND_MIN_CONTRAST_EDGE to toParam(config.blendMinContrastEdge),
                    LibretroDroid.SHADER_UPSCALE_CUT2_PARAM_BLEND_MAX_CONTRAST_EDGE to toParam(config.blendMaxContrastEdge),
                    LibretroDroid.SHADER_UPSCALE_CUT2_PARAM_BLEND_MIN_SHARPNESS to toParam(config.blendMinSharpness),
                    LibretroDroid.SHADER_UPSCALE_CUT2_PARAM_BLEND_MAX_SHARPNESS to toParam(config.blendMaxSharpness),
                    LibretroDroid.SHADER_UPSCALE_CUT2_PARAM_STATIC_BLEND_SHARPNESS to toParam(config.staticSharpness),
                    LibretroDroid.SHADER_UPSCALE_CUT2_PARAM_EDGE_USE_FAST_LUMA to toParam(config.edgeUseFastLuma),
                    LibretroDroid.SHADER_UPSCALE_CUT2_PARAM_SOFT_EDGES_SHARPENING to toParam(config.softEdgesSharpening),
                    LibretroDroid.SHADER_UPSCALE_CUT2_PARAM_SOFT_EDGES_SHARPENING_AMOUNT to toParam(config.softEdgesSharpeningAmount),
                    LibretroDroid.SHADER_UPSCALE_CUT2_PARAM_HARD_EDGES_SEARCH_MAX_ERROR to toParam(config.hardEdgesSearchMaxError),
                )
            )

            is ShaderConfig.CUT3 -> GLRetroShader(
                LibretroDroid.SHADER_UPSCALE_CUT3,
                buildParams(
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_USE_DYNAMIC_BLEND to toParam(config.useDynamicBlend),
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_BLEND_MIN_CONTRAST_EDGE to toParam(config.blendMinContrastEdge),
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_BLEND_MAX_CONTRAST_EDGE to toParam(config.blendMaxContrastEdge),
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_BLEND_MIN_SHARPNESS to toParam(config.blendMinSharpness),
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_BLEND_MAX_SHARPNESS to toParam(config.blendMaxSharpness),
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_STATIC_BLEND_SHARPNESS to toParam(config.staticSharpness),
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_EDGE_USE_FAST_LUMA to toParam(config.edgeUseFastLuma),
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_SOFT_EDGES_SHARPENING to toParam(config.softEdgesSharpening),
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_SOFT_EDGES_SHARPENING_AMOUNT to toParam(config.softEdgesSharpeningAmount),
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_HARD_EDGES_SEARCH_MAX_ERROR to toParam(config.hardEdgesSearchMaxError),
                    LibretroDroid.SHADER_UPSCALE_CUT3_PARAM_HARD_EDGES_SEARCH_MAX_DISTANCE to toParam(config.hardEdgesSearchMaxDistance),
                )
            )
        }
    }

    private fun toParam(param: Float): String {
        return param.toString()
    }

    private fun toParam(param: Boolean): String {
        return if (param) {
            "1"
        } else {
            "0"
        }
    }

    private fun toParam(param: Int): String {
        return param.toString()
    }

    private fun buildParams(vararg pairs: Pair<String, String?>): Map<String, String> {
        return pairs
            .filter { (key, value) -> value != null }
            .associate { (key, value) -> key to value!! }
    }

    /** This function gets called from the jni side.*/
    private fun sendRumbleEvent(port: Int, strengthWeak: Float, strengthStrong: Float) {
        lifecycle?.coroutineScope?.launch {
            rumbleEventsSubject.emit(RumbleEvent(port, strengthWeak, strengthStrong))
        }
    }

    /** Called from the jni side (emulation thread) after each step. */
    private fun sendAchievementEvent(
        type: Int, id: Int, title: String, description: String, badge: String,
        points: Int, progress: String, percent: Float, extra: String,
    ) {
        val event = AchievementEvent(type, id, title, description, badge, points, progress, percent, extra)
        if (!achievementEvents.tryEmit(event)) NxLog.w(TAG_LOG, "achievement event dropped: $type")
    }

    /** Called from the jni side (emulation thread) after each step. */
    private fun sendAchievementServerCall(requestId: Int, url: String, postData: String, contentType: String) {
        val call = AchievementServerCall(requestId, url, postData, contentType)
        if (achievementServerCalls.trySend(call).isFailure) NxLog.e(TAG_LOG, "achievement server call dropped: $requestId")
    }

    private fun refreshAspectRatio() {
        runOnEmulationThread(true) {
            LibretroDroid.refreshAspectRatio()
        }
    }

    sealed class GLRetroEvents {
        object FrameRendered : GLRetroEvents()
        object SurfaceCreated : GLRetroEvents()
    }

    companion object {
        private val TAG_LOG = GLRetroView::class.java.simpleName
        private const val EMULATION_THREAD_TIMEOUT_SECONDS = 10L
        private const val DESTROY_TIMEOUT_SECONDS = 5L
        private const val SECONDARY_TEARDOWN_TIMEOUT_MS = 500L
        private const val POINTER_PORTS = 4   // the native runner's per-port pointers (input.h)

        const val MOTION_SOURCE_DPAD = LibretroDroid.MOTION_SOURCE_DPAD
        const val MOTION_SOURCE_ANALOG_LEFT = LibretroDroid.MOTION_SOURCE_ANALOG_LEFT
        const val MOTION_SOURCE_ANALOG_RIGHT = LibretroDroid.MOTION_SOURCE_ANALOG_RIGHT
        const val MOTION_SOURCE_POINTER = LibretroDroid.MOTION_SOURCE_POINTER

        const val ERROR_LOAD_LIBRARY = LibretroDroid.ERROR_LOAD_LIBRARY
        const val ERROR_LOAD_GAME = LibretroDroid.ERROR_LOAD_GAME
        const val ERROR_GL_NOT_COMPATIBLE = LibretroDroid.ERROR_GL_NOT_COMPATIBLE
        const val ERROR_SERIALIZATION = LibretroDroid.ERROR_SERIALIZATION
        const val ERROR_CHEAT = LibretroDroid.ERROR_CHEAT
        const val ERROR_GENERIC = LibretroDroid.ERROR_GENERIC

        private val TOUCH_EVENT_OUTSIDE = PointF(-10f, 10f)
    }
}
