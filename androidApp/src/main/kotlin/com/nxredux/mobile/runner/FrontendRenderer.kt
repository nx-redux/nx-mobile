package com.nxredux.mobile.runner

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.nxredux.mobile.R
import com.nxredux.mobile.layout.CoreFrame
import com.nxredux.mobile.layout.DsKind
import com.nxredux.mobile.layout.TabletopDsMode
import com.nxredux.mobile.layout.TabletopDsPlacement
import com.nxredux.mobile.layout.PadRoom
import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.layout.consoleScale
import com.nxredux.mobile.layout.overlayClear
import com.nxredux.mobile.layout.picture
import com.nxredux.mobile.layout.pictureSpace
import com.nxredux.mobile.layout.placedFor
import com.nxredux.mobile.layout.setAsidePx
import com.nxredux.mobile.options.BRICK_HEIGHT
import com.nxredux.mobile.options.FrontendSettings
import com.nxredux.mobile.options.NdsOptions
import com.nxredux.mobile.layout.hiddenBandReach
import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.NdsSettings
import com.nxredux.mobile.options.OverlayFile
import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.options.OverlayResolver
import com.nxredux.mobile.options.OverlaySets
import com.nxredux.mobile.options.ScreenScaling
import com.nxredux.mobile.options.ShaderOptions
import com.nxredux.mobile.options.frontendLayout
import com.nxredux.mobile.runner.GameActivity.Companion.EXTRA_TAG
import com.nxredux.mobile.shaders.GlslPreset
import com.nxredux.mobile.shaders.ScaleType
import com.nxredux.mobile.shaders.ShaderLut
import com.nxredux.mobile.shaders.ShaderParam
import com.nxredux.mobile.shaders.WrapMode
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.NxLog
import com.swordfish.libretrodroid.ScreenRect
import com.swordfish.libretrodroid.ScreenRegion
import com.swordfish.libretrodroid.ShaderChainData
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.ShaderLutData
import com.swordfish.libretrodroid.ShaderPassData
import com.swordfish.libretrodroid.ShaderScaleType
import com.swordfish.libretrodroid.ShaderWrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * What [FrontendRenderer] needs from [GameActivity]: the activity for its views, the game's identity, view and settings,
 * the second display's state (owned elsewhere: read, never copied), and the in-game menu to re-feed.
 */
internal interface FrontendHost : LifecycleOwner {
    val activity: ComponentActivity
    val retroView: GLRetroView?
    val gameTag: String
    val gameCoreId: String
    val frontendValues: Map<String, String>
    val gameReady: Boolean
    val menuOpen: Boolean
    val isNds: Boolean
    val isN3ds: Boolean
    /** Overlays and shaders: app-private for good ([com.nxredux.mobile.storage.DataRoot.private]); set at launch, before any of this runs. */
    val privateRoot: File
    /** Where shader presets are read from: the bundled set and the home's own ([Shaders.Roots]); set at launch. */
    val shaderRoots: Shaders.Roots
    val dualMode: DualMode
    val pictureOn: PictureOn
    val n3dsPicture: com.nxredux.mobile.options.NdsScreen
    fun isLandscape(): Boolean
    /** The DS/3DS screens cut for the second display, and what it draws ([GameActivity]'s second-screen block). */
    fun secondDsRegions(g: FloatArray): List<NdsRegion>?
    fun secondRegions(g: FloatArray, ds: List<NdsRegion>?): List<ScreenRegion>
    /** The open in-game menu's shader rows, and its frontend rows (overlay list, locks, overlay availability), again. */
    fun publishMenuShaders()
    fun publishMenuFrontend()
    fun toast(msg: String): android.widget.Toast
    /** The core's geometry changed to [g]: a dual mode checks which display the picture belongs on. */
    fun recheckPictureOn(g: FloatArray)
    /** The core is held (the in-game menu, another app focused): it makes no new frame meanwhile. */
    fun corePaused(): Boolean
    /**
     * A DS or 3DS game's screens were laid out again: the scale they show ([Nds3dResolution.currentScale],
     * [N3ds3dResolution.currentScale] / [N3ds3dResolution.regionScale]; null: none placed), the biggest any layout reaches
     * on the displays in use (the console's displayMax), and whether only the game area's size changed since the last
     * layout ([Nds3dArea.isSizeChangeFrom]).
     */
    fun onNds3dLayout(current: Int?, displayMax: Int, sizeChange: Boolean)
    /** Where the second display draws the whole frame (px of its surface; the 3DS picture there), else null. [g]: the game geometry. */
    fun secondWholeFrame(g: FloatArray): PxRect?
    /**
     * A 3DS game was laid out flat in [FrontendRenderer.n3dsPictureSpace]: Large screen in portrait sends the proportion
     * that space gives when it is not the one the core has.
     */
    fun onN3dsPictureSpace()
}

/**
 * How the picture is drawn: the overlay (resolved, decoded, laid out over the picture), the shader preset (parsed, its
 * LUTs decoded) and its parameters, the GL viewport and offsets ([relayoutFrontend]), the DS screens as regions of the
 * view, and the Debug HUD.
 */
internal class FrontendRenderer(
    private val host: FrontendHost,
    private val input: GameInputRouter,
    private val padLayout: PadLayoutController,
) {

    companion object {
        private const val NDS_INSET_MARGIN_DP = 8f   // the PiP inset's margin inside the big screen
    }

    private val activity get() = host.activity
    private val resources get() = host.activity.resources
    private val intent get() = host.activity.intent
    private fun <T : View> findViewById(id: Int): T = host.activity.findViewById(id)
    private val lifecycle get() = host.lifecycle
    private val lifecycleScope get() = host.lifecycleScope
    private val retroView get() = host.retroView
    private val gameTag get() = host.gameTag
    private val gameCoreId get() = host.gameCoreId
    private val frontendValues get() = host.frontendValues
    private val gameReady get() = host.gameReady
    private val menuOpen get() = host.menuOpen
    private val isNds get() = host.isNds
    private val isN3ds get() = host.isN3ds
    private val isPs2 get() = Res3dConsole.forTag(gameTag) == Res3dConsole.PS2
    private val privateRoot get() = host.privateRoot
    private val dualMode get() = host.dualMode
    private val pictureOn get() = host.pictureOn
    private val n3dsPicture get() = host.n3dsPicture
    private fun isLandscape() = host.isLandscape()
    private fun secondDsRegions(g: FloatArray) = host.secondDsRegions(g)
    private fun secondRegions(g: FloatArray, ds: List<NdsRegion>?) = host.secondRegions(g, ds)
    private fun toast(msg: String) = host.toast(msg)

    var overlaySets = OverlaySets()
    private var overlayView: ImageView? = null
    private var overlayFile: OverlayFile? = null            // resolved for the current orientation; null = none
    var overlayBitmap: Pair<String, Bitmap>? = null // decoded overlay (its path) shown in overlayView
    private var overlayGen = 0                              // drops decodes overtaken by a newer overlay change

    var ndsRegions: List<NdsRegion> = emptyList()   // the DS screens as laid out now; empty = one picture
        private set
    var secondNdsRegions: List<NdsRegion> = emptyList()   // the DS screens on the second surface (px of it); empty = none
        internal set   // dropSecondSurface clears it
    var ndsSplit = false                            // regions are on: the overlay is off and shown unavailable
        internal set   // the launch sets it (a DS game starts split)
    private var ndsFrameWarned = false                      // the "not 256x384" warning is logged once
    private var nds3dArea: Nds3dArea? = null                // the game area the DS 3D scale was last reported for
    private var nds3dMaxFor: Triple<Float, Float, ScreenClass>? = null   // the area [nds3dMainMax] was worked out for
    private var nds3dMainMax = 1                            // the console's displayMax of that area
    private var n3dsFactor = Res3dConsole.N3DS.defaultCap   // the factor the last 3DS frame read was rendered at (N3ds3dResolution.frameFactor)

    /**
     * The 3DS second screen's proportion the core was last sent ([N3dsOptions.STACKED_SMALL_PROPORTION]): the frame is read
     * against its shapes ([N3ds3dResolution.frameFactor]); a send's frame comes later, awaited by its [watchGeometry].
     */
    var n3dsProportion = "2"

    /**
     * The space the 3DS picture was last fitted in on the main display (Screen Scaling Auto, flat, the picture there); null
     * otherwise or before the first frame. Large screen in portrait fills its height ([N3dsOptions.stackedSmallProportion]).
     */
    var n3dsPictureSpace: PxRect? = null
        private set

    /** The game area turned: [n3dsPictureSpace] was the other orientation's, unknown until the next layout. */
    fun forgetN3dsPictureSpace() {
        n3dsPictureSpace = null
    }

    // ---- shader presets ----
    var shaderValues: Map<String, String> = ShaderOptions.fromConfig(emptyMap())   // nx_shader + stored nx_shader_param_*
    var shaderParams: List<ShaderParam> = emptyList()                              // of the active preset (empty for None)
    var presetNames: List<String> = emptyList()
    private var shaderGeneration = 0          // drops preset loads overtaken by a newer preset change
    private var loadedPreset: String? = null  // the preset whose chain the view has; null = none
    // The preset last asked for; differs from loadedPreset exactly while its load is in flight.
    private var requestedPreset: String? = null
    private var lastViewport: RectF? = null
    /**
     * Where the picture sits, in window pixels (the whole game area while the DS screens are split): the speed indicator
     * goes at its top-right corner. Snapshot state, read in the notifications overlay's composition.
     */
    val pictureBox = androidx.compose.runtime.mutableStateOf<android.graphics.Rect?>(null)
    /** The single picture as last laid out (game-area px, offsets included); null while the DS screens are split. */
    private var shownPicture: PxRect? = null
    private var hudJob: Job? = null

    /** The overlay file names offered for this console (the Overlay setting's list). */
    fun overlayNames(): List<String> = OverlayResolver.names(overlaySets)

    /** The Default shader with the chosen sharpness; a preset ([applyShader]) replaces it through [GLRetroView.shaderChain]. */
    fun shaderFor(s: FrontendSettings): ShaderConfig = ShaderConfig.Default(linear = s.linear)

    /**
     * Applies shader [values]: a changed preset is parsed and its LUTs decoded off the main thread, then handed to
     * the view; a changed parameter goes straight to the view. A stale load (a newer preset requested meanwhile)
     * is dropped. Failures toast and leave the stored value as it is (shown raw in the menu).
     */
    fun applyShader(values: Map<String, String>) {
        val previous = shaderValues
        shaderValues = values
        val view = retroView ?: return
        val preset = ShaderOptions.preset(values)
        // Compared with the last request, not the last completed load: any change (None included) must cancel a
        // pending load, or it would land over the newer choice.
        if (preset != requestedPreset) {
            requestedPreset = preset
            val generation = ++shaderGeneration
            if (preset == null) {
                dropShaderChain()
                return
            }
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val chain = GlslPreset.parse(preset) { Shaders.read(host.shaderRoots, it) }
                        chain.warnings.forEach { NxLog.i(TAG_LOG, "shader $preset: $it") }
                        val luts = chain.luts.mapNotNull { lut -> Shaders.file(host.shaderRoots, lut.path)?.let { decodeLut(it, lut) } }
                        chain to luts
                    }
                }
                if (generation != shaderGeneration) return@launch
                val (chain, luts) = result.getOrElse { e ->
                    NxLog.w(TAG_LOG, "shader $preset failed", e)
                    toast("Shader: ${e.message ?: e.javaClass.simpleName}")
                    requestedPreset = null   // this load is the newest request, so forget it for a retry
                    dropShaderChain()
                    return@launch
                }
                loadedPreset = preset
                shaderParams = chain.params
                // Every parameter with its value: the view reads one absent from the map as 0, not its default.
                view.shaderChain = ShaderChainData(
                    passes = chain.passes.map { p ->
                        ShaderPassData(p.source, p.filterLinear, wrapOf(p.wrap), scaleOf(p.scaleTypeX), scaleOf(p.scaleTypeY), p.scaleX, p.scaleY, p.frameCountMod, p.alias)
                    },
                    luts = luts,
                    params = ShaderOptions.paramValues(shaderValues, chain.params),
                )
                host.publishMenuShaders()
            }
            return
        }
        // Same request, still loading: the load reads shaderValues when it lands, so the new values go with it.
        if (preset != loadedPreset) return
        // Same preset: push only the parameters that changed.
        val current = ShaderOptions.paramValues(values, shaderParams)
        val before = ShaderOptions.paramValues(previous, shaderParams)
        current.forEach { (id, v) -> if (before[id] != v) view.setShaderParameter(id, v) }
    }

    /**
     * Back to the default shader with no parameters, re-feeding the menu; [shaderValues] is kept (the stored
     * preset is shown raw). Also forgets the request, so re-selecting a preset that failed retries it, unless a
     * newer load is pending: that one still lands (the generation is not bumped).
     */
    fun dropShaderChain() {
        if (requestedPreset == loadedPreset) requestedPreset = null
        loadedPreset = null
        shaderParams = emptyList()
        retroView?.shaderChain = null
        host.publishMenuShaders()
    }

    private fun wrapOf(w: WrapMode) = when (w) { WrapMode.EDGE -> ShaderWrap.EDGE; WrapMode.REPEAT -> ShaderWrap.REPEAT; WrapMode.MIRRORED -> ShaderWrap.MIRRORED }
    private fun scaleOf(s: ScaleType) = when (s) { ScaleType.NONE -> ShaderScaleType.NONE; ScaleType.SOURCE -> ShaderScaleType.SOURCE; ScaleType.VIEWPORT -> ShaderScaleType.VIEWPORT; ScaleType.ABSOLUTE -> ShaderScaleType.ABSOLUTE }

    /** RGBA8 bytes of a LUT PNG (Bitmap ARGB_8888 is RGBA in memory order); null with a log when unreadable. */
    private fun decodeLut(file: File, lut: ShaderLut): ShaderLutData? {
        var bmp = runCatching { BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inPremultiplied = false }) }.getOrNull()
        if (bmp == null) { NxLog.w(TAG_LOG, "shader texture ${lut.id} unreadable: ${file.path}"); return null }
        // The decoder may ignore inPreferredConfig (16-bit, hardware); the view needs exactly 4 bytes per pixel.
        if (bmp.config != Bitmap.Config.ARGB_8888) {
            val copy = bmp.copy(Bitmap.Config.ARGB_8888, false)
            bmp.recycle()
            if (copy == null) { NxLog.w(TAG_LOG, "shader texture ${lut.id} could not be converted: ${file.path}"); return null }
            bmp = copy
        }
        if (bmp.byteCount != bmp.width * bmp.height * 4) {
            NxLog.w(TAG_LOG, "shader texture ${lut.id} has an unexpected size: ${file.path}")
            bmp.recycle()
            return null
        }
        val buf = ByteBuffer.allocate(bmp.byteCount)
        bmp.copyPixelsToBuffer(buf)
        val data = ShaderLutData(lut.id, bmp.width, bmp.height, buf.array(), lut.linear, wrapOf(lut.wrap))
        bmp.recycle()
        return data
    }

    /** Resolves the overlay for the current orientation and shows it; an unreadable file counts as None. */
    fun updateOverlay(s: FrontendSettings) {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        // Split DS screens have no single picture to frame: no overlay (the menu shows the row as unavailable).
        val file = if (ndsSplit) null else OverlayResolver.resolve(gameTag, overlaySets, s.overlay, landscape)
        if (!ndsSplit && s.overlay != null && file == null) NxLog.w(TAG_LOG, "overlay '${s.overlay}' not found in any overlay set; showing none")
        overlayFile = file
        val iv = ensureOverlayView()
        val gen = ++overlayGen
        val cached = overlayBitmap
        when {
            file == null -> { iv.setImageDrawable(null); iv.visibility = View.GONE }
            cached != null && cached.first == file.path -> { iv.setImageBitmap(cached.second); iv.visibility = View.VISIBLE }
            else -> {
                // Hide the old art until the new one is decoded, so it is never stretched into the new box.
                iv.setImageDrawable(null)
                iv.visibility = View.GONE
                lifecycleScope.launch {
                    val bmp = withContext(Dispatchers.IO) { decodeOverlay(file) }
                    if (gen != overlayGen) return@launch
                    if (bmp == null) {
                        overlayFile = null
                        relayoutFrontend()
                    } else {
                        overlayBitmap = file.path to bmp
                        iv.setImageBitmap(bmp)
                        iv.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    /** Decodes an overlay PNG; null (logged) when missing or unreadable. */
    fun decodeOverlay(file: OverlayFile): Bitmap? =
        runCatching { BitmapFactory.decodeFile(File(privateRoot, file.path).absolutePath) }.getOrNull()
            .also { if (it == null) NxLog.w(TAG_LOG, "overlay not readable: ${file.path}") }

    /** The overlay image: inside the game container above the GLRetroView, so pads and the menu stay above it. */
    fun ensureOverlayView(): ImageView = overlayView ?: ImageView(activity).also { iv ->
        iv.scaleType = ImageView.ScaleType.FIT_XY
        iv.visibility = View.GONE
        findViewById<FrameLayout>(R.id.gameContainer).addView(iv, FrameLayout.LayoutParams(0, 0, Gravity.TOP or Gravity.START))
        overlayView = iv
    }

    private val geometryWatch = GeometryWatch()
    private val geometryFrame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val view = retroView
            if (view == null || !view.isAttachedToWindow || activity.isDestroyed) { cancelGeometryWatch(); return }
            val old = geometryWatch.before
            val g = view.getGameGeometry()
            when (geometryWatch.tick(g)) {
                GeometryWatch.Tick.WAITING -> Choreographer.getInstance().postFrameCallback(this)
                GeometryWatch.Tick.CHANGED -> {
                    NxLog.i(TAG_LOG, "3DS frame: new geometry after ${geometryWatch.framesWaited} frames, ${old.fmt()} -> ${g.fmt()}; laying out again")
                    layOutFromCore(g)
                    endGeometryHold()
                }
                GeometryWatch.Tick.GAVE_UP -> {
                    val paused = host.corePaused()
                    NxLog.i(TAG_LOG, "3DS frame: no new geometry in ${geometryWatch.framesWaited} frames (${g.fmt()})${if (paused && geometryHold.held) ", core paused: picture held until it runs" else ""}")
                    if (geometryHold.onGaveUp(paused)) showHeldPicture()
                    // The 3D scale held for the layout's frame ([reportN3ds3d]) is read from the frame there is.
                    if (watchIsLayoutSwitch) relayoutFrontend(g)
                }
                GeometryWatch.Tick.IDLE -> {}
            }
        }
    }

    private fun FloatArray?.fmt() = this?.joinToString(prefix = "[", postfix = "]") { "%.2f".format(it) } ?: "none"

    /**
     * After a 3DS layout switch: reads the core's geometry each frame (Choreographer) for up to [frames] frames and lays
     * out again the first time it differs from the one before the switch; Azahar reports the new frame size late. A
     * safety net behind the view's geometry flow ([onCoreGeometry] ends the watch when that already caught the change), so
     * one change lays out once. A newer switch restarts it; [cancelGeometryWatch] ends it. [holdPicture]: the picture is
     * held black ([GLRetroView.pictureHeld]) until the new frame size is laid out (or the watch gives up), as when a fold
     * or unfold moves the screens' regions at once and the old frame would show cut by them for those frames; a watch
     * that gives up while the core is paused keeps it held until the core runs ([onCoreResumed]) or its geometry arrives
     * ([GeometryHold]). False: nothing is watched (no view, or the core has no geometry yet).
     */
    fun watchGeometry(frames: Int = 90, holdPicture: Boolean = false, why: String = "3DS layout switched", layoutSwitch: Boolean = true): Boolean {
        val view = retroView ?: return false
        val before = view.getGameGeometry()
        if (!geometryWatch.start(before, frames)) return false   // the core has no geometry yet (still launching)
        watchIsLayoutSwitch = layoutSwitch
        if (holdPicture) {
            geometryHold.start()
            view.pictureHeld = true
        }
        NxLog.i(TAG_LOG, "$why: watching the frame size for up to $frames frames from ${before.fmt()}${if (holdPicture) ", picture held" else ""}")
        Choreographer.getInstance().removeFrameCallback(geometryFrame)
        Choreographer.getInstance().postFrameCallback(geometryFrame)
        return true
    }

    /** The core runs again (the menu closed, the focus back): a hold that outlasted its watch while paused watches again. */
    fun onCoreResumed() {
        if (!geometryHold.onCoreRunning()) return
        if (!watchGeometry(holdPicture = true)) endGeometryHold()
    }

    /** Ends a [watchGeometry] under way (the game is closing). */
    fun cancelGeometryWatch() {
        geometryWatch.cancel()
        Choreographer.getInstance().removeFrameCallback(geometryFrame)
        endGeometryHold()
    }

    /** The view's geometry flow fired with [g]: lays out from it, and a [watchGeometry] waiting for this change is done. */
    fun onCoreGeometry(g: FloatArray) {
        val before = geometryWatch.before
        val seen = geometryWatch.seen(g)
        if (seen) NxLog.i(TAG_LOG, "3DS frame: new geometry from the core after ${geometryWatch.framesWaited} frames, ${before.fmt()} -> ${g.fmt()}; laying out again")
        layOutFromCore(g)
        // A hold that outlasted its watch (paused) ends with the geometry it waited for, whichever path brings it.
        if (seen || geometryHold.awaitsRun) endGeometryHold()
    }

    /** The [watchGeometry] under way waits for a layout switch's frame (not a 3D resolution's): the 3DS 3D scale is held meanwhile. */
    private var watchIsLayoutSwitch = false

    /** A [watchGeometry] holds the picture until its new frame size is laid out ([endGeometryHold]). */
    private val geometryHold = GeometryHold()

    /** The watch that held the picture is over: shown again (after the layout queued before it), once the game runs. */
    private fun endGeometryHold() {
        if (geometryHold.end()) showHeldPicture()
    }

    private fun showHeldPicture() {
        retroView?.let { if (!PictureHold.held(frameKnown = true, gameReady)) it.pictureHeld = false }
    }

    /** The one way a changed core geometry is laid out (the watch's and the flow's), once per distinct geometry. */
    private fun layOutFromCore(g: FloatArray) {
        if (!geometryWatch.shouldLayOut(g)) return
        host.recheckPictureOn(g)
        relayoutFrontend(g)
    }

    /**
     * Lays the picture out in the game area and the pad around it. The space the pad leaves ([PadRoom]: the set-aside
     * width each side of landscape clusters, the area above the portrait band, the hidden band's space, else the whole
     * area) holds the picture: under Screen Scaling Auto at its console's scale ([picture]); Native, Aspect and Fullscreen
     * size it in the whole area as before. DS games (a plain 256x384 frame) get their screens as regions of the view
     * ([NdsLayout], in that same space), with the viewport the whole area and no offsets or overlay: the fork ignores the
     * screen offset and the scaling mode for regions. Half-folded, DS / 3DS screens follow the fold instead
     * ([tabletopDsLayout]). Other games: the picture (or its overlay box) becomes the GL
     * viewport and the overlay view's bounds; the offsets (scaled to the box) go to the view and move the picture after
     * its placement. The pad is then fitted to the final picture ([PadLayoutController.placeClusters]). No-op until laid
     * out. [geometry]: the view's, when the caller has just read it.
     */
    fun relayoutFrontend(geometry: FloatArray? = null) {
        val view = retroView ?: return
        if (!view.isAttachedToWindow) return   // the GL thread is gone once detached
        val game = findViewById<FrameLayout>(R.id.gameContainer)
        val w = game.width.toFloat()
        val h = game.height.toFloat()
        if (w <= 0f || h <= 0f) return
        val g = geometry ?: view.getGameGeometry()
        val origin = IntArray(2).also { game.getLocationInWindow(it) }
        val frame = coreFrame(g)   // null before the first frame (0x0)
        frame?.let(padLayout::onFrame)
        val room = padLayout.padRoom()
        val shape = activity.windowShape()   // one window reading for this layout
        val pad = padLayout.landscapePad(shape)
        val screen = shape.screenClass
        val overlayH = padLayout.hiddenOverlayHeightPx()
        val setAside = pad?.let { setAsidePx(screen, it.reach) } ?: 0f
        // The DS / 3DS may reach a third into the hidden band's overlay (the Fold's 6x + 3x), the rest a quarter.
        val space = pictureSpace(room, w, h, setAside, overlayH, hiddenBandReach(NdsOptions.appliesTo(gameTag) || N3dsOptions.appliesTo(gameTag)))
        val tabletop = tabletopDsLayout(shape, g)
        val nds = tabletop?.regions ?: ndsLayout(w, h, g, space, room, overlayH, screen) ?: n3dsSplitLayout(w, h)
        // An area too small for one DS screen pixel (NdsLayout returns no regions) is only ever a transient mid-resize
        // size. Keep whatever is drawn now rather than letting an empty list flip a split game to the whole 256x384
        // frame (and the overlay back on) for that moment; the next size change lays it out again.
        if (nds != null && nds.isEmpty()) return
        // Likewise a half-folded screen with no pixel (a hinge reading leaving no room above it): keep what is drawn.
        if (tabletop != null && listOf(tabletop.placement.top, tabletop.placement.bottom).any { it.width <= 0f || it.height <= 0f }) return
        // Before its first frame a DS game's frame size is unknown (0x0): it stays split as it started, overlay off. The
        // clusters still take their unit and rows (hidden), so they are measured by the first frame.
        if (nds == null && ndsSplit && !gameReady) {
            pad?.let { padLayout.placeClusters(null, it) }
            return
        }
        ndsRegions = nds ?: emptyList()
        val secondDs = secondDsRegions(g)
        secondNdsRegions = secondDs.takeIf { isNds }.orEmpty()
        if (isNds) reportNds3d(w, h, screen, halfFolded = tabletop != null)
        if (isN3ds && nds != null) reportN3ds3d(w, h, screen, halfFolded = tabletop != null, g, flat = null, secondDs)
        input.positionPen()   // on whichever display shows the bottom screen
        setNdsSplit(nds != null)
        view.secondaryRegions = secondRegions(g, secondDs)
        if (nds != null) {
            val whole = RectF(0f, 0f, 1f, 1f)
            if (lastViewport != whole) {
                lastViewport = whole
                view.viewport = whole
            }
            view.screenOffset = PointF(0f, 0f)
            val shown = nds.shownRect()
            // Half-folded split: the speed indicator goes on the screen above the hinge (the Flip stacks both there).
            val splitTop = tabletop?.placement?.takeIf { it.mode == TabletopDsMode.SPLIT }?.top
            pictureBox.value = windowRect(origin, splitTop ?: shown)
            shownPicture = null
            view.screenRegions = nds.map { it.toScreenRegion(w, h) }
            // Half-folded, the pad below the hinge is placed from the placement, not beside the screens.
            fitPad(if (tabletop != null) PadRoom.NONE else room, pad, shown, frameKnown = true, h, overlayH)
            val scales = nds.joinToString(" + ") { "${it.screen.value}${if (it.inset) " inset" else ""} %.2fx".format(java.util.Locale.US, it.dst.height / 192f) }
            if (tabletop != null) {
                val p = tabletop.placement
                logLayout(w, h, shape, "TABLETOP_DS", tabletop.pad.takeIf { p.left != null }, 0f, "top=${p.top.short()} bottom=${p.bottom.short()}", "${p.mode} ${if (isN3ds) "3ds" else "ds"} $scales")
            } else {
                logLayout(w, h, shape, room.name, pad, setAside, spaceAndPicture(space, shown), "ds $scales")
            }
            return
        }
        if (view.screenRegions.isNotEmpty()) view.screenRegions = emptyList()
        val s = FrontendSettings.parse(frontendValues)
        val overlayAspect = overlayFile?.aspect
        val box: PxRect
        val shown: PxRect
        val offset: PointF
        val foldTop = halfFoldedTopArea(shape, g, w)
        if (isN3ds) {
            val auto = foldTop == null && s.scaling == ScreenScaling.AUTO && frame != null
            n3dsPictureSpace = space.takeIf { auto && (dualMode == DualMode.OFF || pictureOn == PictureOn.MAIN) }
        }
        if (foldTop != null) {
            // Half-folded with a DS frame that is not the plain stack: drawn whole above the hinge, not across it.
            val fit = DualScreen.fullFrame(foldTop.width, foldTop.height, g[2])
            box = fit.copy(y = fit.y + foldTop.y)
            offset = PointF(0f, 0f)
            shown = box
        } else if (s.scaling == ScreenScaling.AUTO && frame != null) {
            val placed = placedFor(room, picture(space, frame, consoleScale(gameTag), overlayAspect), h, overlayH)
            // The offsets as Native / Aspect / Fullscreen scale them: to the overlay's box, else the game area.
            val unit = (if (overlayAspect != null && overlayAspect > 0f) placed.height else h) / BRICK_HEIGHT
            box = placed
            offset = PointF(s.offsetX * unit, s.offsetY * unit)
            shown = placed.copy(x = placed.x + offset.x, y = placed.y + offset.y)
        } else {
            // The overrides, as before; Auto too before the first frame, which has nothing to place yet.
            val layout = frontendLayout(w, h, g[0].toInt(), g[1].toInt(), g[2], s.scaling, s.offsetX, s.offsetY, overlayAspect)
            box = layout.box
            offset = PointF(layout.offsetX, layout.offsetY)
            shown = layout.content
        }
        val viewport = RectF(box.x / w, box.y / h, (box.x + box.width) / w, (box.y + box.height) / h)
        pictureBox.value = windowRect(origin, if (s.scaling == ScreenScaling.AUTO) shown else box)
        shownPicture = shown
        if (isN3ds) {
            reportN3ds3d(w, h, screen, halfFolded = false, g, flat = shown, secondDs)
            host.onN3dsPictureSpace()   // after the report: it read this frame against the proportion it was drawn at
        }
        if (isPs2) reportPs2Resolution(w, h, screen, shown)
        if (viewport != lastViewport) {
            lastViewport = viewport
            view.viewport = viewport
        }
        view.screenOffset = offset
        overlayView?.layoutParams = FrameLayout.LayoutParams(box.width.roundToInt(), box.height.roundToInt()).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = box.x.roundToInt()
            topMargin = box.y.roundToInt()
        }
        // A bezel counts as picture: the pad over its border is see-through. Under Auto the shown rect is that box (offset).
        val covered = if (s.scaling != ScreenScaling.AUTO && overlayAspect != null && overlayAspect > 0f) box else shown
        fitPad(room, pad, covered, frameKnown = frame != null, h, overlayH)
        val scale = frame?.let { shown.height / (if (it.rotated) it.width else it.height) }
        logLayout(w, h, shape, room.name, pad, setAside, spaceAndPicture(space, shown), "${s.scaling} frame=${frame?.let { "${it.width.roundToInt()}x${it.height.roundToInt()}" }} scale=${scale?.let { "%.2f".format(java.util.Locale.US, it) }}")
    }

    /**
     * The DS 3D scale's inputs to the host ([FrontendHost.onNds3dLayout]) for the layout just made in the [w] x [h] game
     * area: the shown scale of every DS screen (both displays', or both halves when half-folded), and the display maximum,
     * the main display's (worked out once per area) or with a second display drawing DS screens, the bigger of it and
     * those screens' scale (one screen fills it, so that is the most it reaches).
     */
    private fun reportNds3d(w: Float, h: Float, screen: ScreenClass, halfFolded: Boolean) {
        val second = Nds3dResolution.currentScale(secondNdsRegions) ?: 1
        report3d(w, h, screen, halfFolded, Nds3dResolution.currentScale(ndsRegions + secondNdsRegions), second) {
            Nds3dResolution.displayMax(w, h, screen)
        }
    }

    /**
     * The PS2 3D scale's inputs to the host, as [reportNds3d]: the [shown] picture's height over the 448 px frame, and the
     * window's display maximum ([Ps2Resolution.displayMax]). Nothing before the first frame (the launch's value stands).
     */
    private fun reportPs2Resolution(w: Float, h: Float, screen: ScreenClass, shown: PxRect) {
        if (!gameReady || shown.height <= 0f) return
        report3d(w, h, screen, halfFolded = false, Ps2Resolution.currentScale(shown.height.roundToInt()), second = 1) {
            Ps2Resolution.displayMax(w.roundToInt(), h.roundToInt())
        }
    }

    /**
     * The 3DS 3D scale's inputs to the host, as [reportNds3d]: flat, the [flat] picture's scale at the factor the frame
     * was rendered at, read from its size ([N3ds3dResolution.frameFactor]; an unknown frame keeps the last one read);
     * half-folded or split ([flat] null), the screens' regions ([N3ds3dResolution.regionScale]). A second display drawing
     * 3DS screens ([secondDs]) or the whole frame counts as for the DS. Nothing before the first frame (the launch's value
     * stands), nor while a layout switch's frame is awaited ([watchGeometry]): the old layout's frame laid out in the new
     * space would start a send for a scale never shown; the new frame (or the watch giving up) reports. [g]: the game
     * geometry laid out.
     */
    private fun reportN3ds3d(w: Float, h: Float, screen: ScreenClass, halfFolded: Boolean, g: FloatArray, flat: PxRect?, secondDs: List<NdsRegion>?) {
        if (g[0] <= 0f || g[1] <= 0f) return
        if (geometryWatch.active && watchIsLayoutSwitch) return
        N3ds3dResolution.frameFactor(g[0], g[1], n3dsProportion.toFloatOrNull())?.let { n3dsFactor = it }
        val factor = n3dsFactor
        val main = if (flat != null) N3ds3dResolution.currentScale(flat.width, g[0], factor) else N3ds3dResolution.regionScale(ndsRegions)
        val second = secondDs?.let(N3ds3dResolution::regionScale)
            ?: host.secondWholeFrame(g)?.let { N3ds3dResolution.currentScale(it.width, g[0], factor) }
        report3d(w, h, screen, halfFolded, listOfNotNull(main, second).maxOrNull(), second ?: 1) {
            N3ds3dResolution.displayMax(w, h, screen)
        }
    }

    /**
     * [current] and the display maximum to the host: the main display's ([mainMax], worked out once per area) or the
     * second display's [second] scale when bigger; with whether only the game area's size changed ([Nds3dArea]).
     */
    private fun report3d(w: Float, h: Float, screen: ScreenClass, halfFolded: Boolean, current: Int?, second: Int, mainMax: () -> Int) {
        val key = Triple(w, h, screen)
        if (key != nds3dMaxFor) {
            nds3dMaxFor = key
            nds3dMainMax = mainMax()
        }
        val area = Nds3dArea(w, h, screen, retroView?.display?.displayId, isLandscape(), halfFolded, dualMode)
        val sizeChange = area.isSizeChangeFrom(nds3dArea)
        nds3dArea = area
        host.onNds3dLayout(current, maxOf(nds3dMainMax, second), sizeChange)
    }

    /** The 3DS game launched with internal resolution [factor]: what a frame of a size no layout explains is taken to carry. */
    fun startN3dsFactor(factor: Int) {
        n3dsFactor = factor
    }

    /**
     * [factor] went to Azahar, whose frame size follows a frame or more later: watched for ([watchGeometry]) so the new
     * frame is laid out and measured. A layout switch's watch under way is left as it is (it ends on the next change too).
     */
    fun onN3dsFactorSent(factor: Int) {
        if (!geometryWatch.active) watchGeometry(why = "3DS 3D resolution ${factor}x sent", layoutSwitch = false)
    }

    /**
     * The pad fitted to the final [picture] (game-area px; the area is the window in landscape, where the clusters are):
     * the clusters beside it, or the hidden band's overlay solid when the picture stops above it. A [frameKnown] picture
     * lets the pads that waited for it show.
     */
    private fun fitPad(room: PadRoom, pad: LandscapePad?, picture: PxRect, frameKnown: Boolean, areaH: Float, overlayH: Float) {
        when (room) {
            PadRoom.BESIDE -> pad?.let { padLayout.placeClusters(picture.takeIf { frameKnown }, it) }
            PadRoom.HIDDEN_BAND -> padLayout.setOverlayClear(frameKnown && overlayClear(picture.y + picture.height, areaH, overlayH))
            PadRoom.BAND, PadRoom.NONE -> Unit
        }
        if (frameKnown) padLayout.onPictureKnown()
        // After the viewport and regions above: queued on the GL thread in order, so the first picture drawn is placed.
        // A fold's 3DS switch keeps it held until the core's new frame size is laid out ([watchGeometry]).
        retroView?.let { if (!geometryHold.held && !PictureHold.held(frameKnown, gameReady)) it.pictureHeld = false }
    }

    /** The core's frame from [g] (the view's geometry), or null before it is known (0x0 until the first frame). */
    private fun coreFrame(g: FloatArray): CoreFrame? =
        if (g[0] > 0f && g[1] > 0f) CoreFrame(g[0], g[1], g[2], rotated = abs(sin(g[3])) > 0.5f) else null

    private fun windowRect(origin: IntArray, r: PxRect) = android.graphics.Rect(
        origin[0] + r.x.roundToInt(), origin[1] + r.y.roundToInt(),
        origin[0] + (r.x + r.width).roundToInt(), origin[1] + (r.y + r.height).roundToInt(),
    )

    /** The relayout's inputs and outcome, once per change, for the Debug log (the device checks read the spec's tables from it). */
    private var lastLayoutLog: String? = null

    private fun spaceAndPicture(space: PxRect, picture: PxRect) = "space=${space.short()} picture=${picture.short()}"

    /** [rects]: where the picture went ([spaceAndPicture]; half-folded, the two screens' rects). */
    private fun logLayout(w: Float, h: Float, shape: WindowShape, room: String, pad: LandscapePad?, setAside: Float, rects: String, what: String) {
        val reach = pad?.let { "reach=${it.reach.edgePx.roundToInt()}+${it.reach.buttonsPx.roundToInt()}${if (it.measured) " measured" else " estimated"} unit=${"%.1f".format(java.util.Locale.US, it.unitPx)}" } ?: "no clusters"
        val line = "relayout: window=${shape.widthPx}x${shape.heightPx} ${shape.screenClass} area=${w.roundToInt()}x${h.roundToInt()} room=$room $reach setAside=${setAside.roundToInt()} " +
            "$rects $what"
        if (line == lastLayoutLog) return
        lastLayoutLog = line
        NxLog.i(TAG_LOG, line)
    }

    private fun PxRect.short() = "${x.roundToInt()},${y.roundToInt()} ${width.roundToInt()}x${height.roundToInt()}"

    /**
     * The DS screen regions for a [w] x [h] game area in [space] (the pad's room, [room]; with the band hidden placed by
     * the quarter-pad rule against an overlay [overlayH] tall), or null: not NDS, or the frame is not the plain DS stack
     * (256x384, or 256N x 384N from the GL renderer). The dual-screen modes keep their own layouts.
     * Empty when the area is too small for a screen pixel (the caller keeps the current layout then).
     */
    private fun ndsLayout(w: Float, h: Float, g: FloatArray, space: PxRect, room: PadRoom, overlayH: Float, screen: ScreenClass): List<NdsRegion>? {
        if (!NdsOptions.appliesTo(gameTag)) return null
        val frameW = g[0].roundToInt()
        val frameH = g[1].roundToInt()
        if (!NdsLayout.isDsFrame(frameW, frameH)) {
            // Safety net (0x0 before the first frame; anything else is a core or option change): one picture, as for any core.
            if (frameW > 0 && frameH > 0 && !ndsFrameWarned) {
                ndsFrameWarned = true
                NxLog.w(TAG_LOG, "NDS frame is ${frameW}x$frameH, not a 256x384 stack: drawing it as one picture")
            }
            return null
        }
        if (dualMode == DualMode.SPLIT) return DualScreen.ndsScreen(w, h, DualScreen.splitMain(NdsSettings.parse(frontendValues).big, pictureOn))
        if (dualMode == DualMode.PANEL_UP) return DualScreen.ndsPanelUp(w, h, NdsSettings.parse(frontendValues))
        val landscape = isLandscape()
        val settings = NdsSettings.parse(frontendValues)
        val regions = NdsLayout.regions(
            space, settings.layoutShown(landscape, ndsControllerMode(), screen), landscape, screen, settings,
            NDS_INSET_MARGIN_DP * resources.displayMetrics.density,
        )
        if (room != PadRoom.HIDDEN_BAND || regions.isEmpty()) return regions
        // The band hidden: the screens as one picture, at the top when they reach into the overlay, else centred above it.
        val shown = regions.shownRect()
        val dy = (placedFor(room, shown, h, overlayH).y - shown.y).roundToInt().toFloat()
        return regions.map { it.copy(dst = it.dst.copy(y = it.dst.y + dy)) }
    }

    /** The half-folded DS / 3DS layout: the [placement] (game-area px), its screens as [regions], and the [pad] measures it used. */
    private class HalfFolded(val placement: TabletopDsPlacement, val regions: List<NdsRegion>, val pad: LandscapePad)

    /**
     * Half-folded DS / 3DS ([PadLayoutController.tabletop]): on a tablet-class window ([TabletopPad.DS], the game view
     * spanning the window) the top screen above the hinge and the touch screen below it; on a phone-shaped one (the game
     * view above the hinge) both screens stacked there. [PadLayoutController.tabletopDsPlacement] places them, in window
     * px moved into the game view's; the player's flat layout choice does not apply. Null otherwise: not half-folded, another console, a Dual screen mode
     * (its own layouts come first), or a DS frame that is not the plain 256x384 stack (one picture, as [ndsLayout] warns).
     */
    private fun tabletopDsLayout(shape: WindowShape, g: FloatArray): HalfFolded? {
        if (dualMode != DualMode.OFF) return null
        val kind = when {
            isN3ds -> DsKind.N3DS
            NdsOptions.appliesTo(gameTag) && NdsLayout.isDsFrame(g[0].roundToInt(), g[1].roundToInt()) -> DsKind.DS
            else -> return null
        }
        val tt = padLayout.tabletop() ?: return null
        val pad = padLayout.tabletopDsPad(shape)
        val p = padLayout.tabletopDsPlacement(shape, tt, pad)
        val dy = tt.gameTop.toFloat()   // window px -> the game view's (it starts at gameTop)
        val placement = p.copy(top = p.top.copy(y = p.top.y - dy), bottom = p.bottom.copy(y = p.bottom.y - dy))
        // The 3DS core's swap is pinned while half-folded (its frame stays unswapped under the cuts): SWAP is the app's.
        val big = if (kind == DsKind.DS) NdsSettings.parse(frontendValues).big else padLayout.n3dsTabletopBig
        return HalfFolded(placement, tabletopRegions(placement, kind, big), pad)
    }

    /**
     * The area above the hinge (game-view px, [w] wide) while the Fold is half-folded with a DS frame that is not the
     * plain 256 x 384 stack (another core layout): that frame is drawn whole there. Null otherwise.
     */
    private fun halfFoldedTopArea(shape: WindowShape, g: FloatArray, w: Float): PxRect? {
        if (dualMode != DualMode.OFF || !NdsOptions.appliesTo(gameTag)) return null
        if (g[0] <= 0f || g[1] <= 0f || NdsLayout.isDsFrame(g[0].roundToInt(), g[1].roundToInt())) return null
        val tt = padLayout.tabletop() ?: return null
        if (tt.pad != TabletopPad.DS) return null   // the Flip's game view is already the area above the hinge
        val top = (shape.cutoutTop - tt.gameTop).toFloat()
        return PxRect(0f, top, w, (tt.menuBottom - tt.gameTop).toFloat() - top)
    }

    /**
     * Whether the half-folded DS / 3DS layout places the screens now, so the layout shortcut does nothing: half-folded
     * with no second screen in use, a 3DS, or a DS whose frame is the plain stack (or not known yet). A DS frame of
     * another shape is drawn whole above the hinge instead ([halfFoldedTopArea]), and the shortcut works as flat.
     */
    fun halfFoldedLayoutInUse(): Boolean {
        if (dualMode != DualMode.OFF || padLayout.tabletop() == null) return false
        if (isN3ds) return true
        if (!NdsOptions.appliesTo(gameTag)) return false
        val g = retroView?.getGameGeometry() ?: return true
        return g[0] <= 0f || g[1] <= 0f || NdsLayout.isDsFrame(g[0].roundToInt(), g[1].roundToInt())
    }

    /**
     * 3DS SWAP (the pill, SELECT + R, a pad's left-stick click) [down] or up while half-folded with the screens as regions
     * ([tabletopDsLayout]): a press trades the screens above and below the hinge (touch follows the bottom screen) and the
     * core never sees it. False otherwise: the key is someone else's (split's, else Azahar's own).
     */
    fun onTabletopN3dsSwap(down: Boolean): Boolean {
        if (!isN3ds || dualMode != DualMode.OFF || padLayout.tabletop() == null) return false
        if (down) {
            padLayout.swapTabletopN3ds()   // the halves move with the screens' new sizes
            relayoutFrontend()
        }
        return true
    }

    /**
     * Portrait with the pad band dropped for a non-clip-on controller ([PadBandRule]): the DS layout and SELECT + Left/Right
     * use the controller portrait layout then. [applyLayout] relays out on connect/disconnect, so this follows live.
     */
    fun ndsControllerMode(): Boolean {
        val tag = intent.getStringExtra(EXTRA_TAG) ?: gameTag   // set before startEmulator's gameTag, for onCreate's layout
        val portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        return PadBandRule.controllerLayout(tag, portrait, padLayout.layoutControllerNames())
    }

    /** 3DS split across the displays: the main display's screen alone, as a region of Azahar's fixed stacked frame; else null. */
    private fun n3dsSplitLayout(w: Float, h: Float): List<NdsRegion>? =
        if (isN3ds && dualMode == DualMode.SPLIT) DualScreen.n3dsScreen(w, h, DualScreen.splitMain(n3dsPicture, pictureOn)) else null

    /** Split DS screens turn the overlay off and the menu shows it unavailable; both follow when that changes. */
    private fun setNdsSplit(split: Boolean) {
        if (split == ndsSplit) return
        ndsSplit = split
        updateOverlay(FrontendSettings.parse(frontendValues))
        host.publishMenuFrontend()
    }

    /** The Debug HUD (top-left): fps, core, core resolution and the scaled picture size, once a second. */
    fun setHud(on: Boolean) {
        val label = findViewById<TextView>(R.id.fps)
        if (!on) {
            hudJob?.cancel()
            hudJob = null
            label.visibility = View.GONE
            return
        }
        if (hudJob != null) return
        val view = retroView ?: return
        label.text = ""
        label.visibility = View.VISIBLE
        var frames = 0
        var windowStart = SystemClock.elapsedRealtime()
        var pausedShown = false
        var pausedAt = 0L
        hudJob = lifecycleScope.launch {
            view.getGLRetroEvents().flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED).collect { event ->
                if (event !is GLRetroView.GLRetroEvents.FrameRendered) return@collect
                val now = SystemClock.elapsedRealtime()
                if (menuOpen) {
                    // Paused: these frames only redraw the last picture, so don't count them as the game's rate.
                    // Still refresh the sizes about once a second: the adjust strip changes them.
                    if (!pausedShown || now - pausedAt >= 1000) {
                        label.text = hudText(view, null)
                        pausedShown = true
                        pausedAt = now
                    }
                    frames = 0
                    windowStart = now
                    return@collect
                }
                pausedShown = false
                frames++
                if (now - windowStart < 1000) return@collect
                val fps = frames * 1000f / (now - windowStart)
                label.text = hudText(view, fps)
                NxLog.i(TAG_LOG, String.format(java.util.Locale.US, "fps=%.1f core=%s", fps, gameCoreId))
                frames = 0
                windowStart = now
            }
        }
    }

    /** [fps] null = paused (the menu is open). */
    private fun hudText(view: GLRetroView, fps: Float?): String {
        val g = view.getGameGeometry()
        val coreW = g[0].toInt()
        val coreH = g[1].toInt()
        val game = findViewById<FrameLayout>(R.id.gameContainer)
        // Split DS screens: each screen's size as drawn (the big one first), not the single picture's.
        val shown = if (ndsRegions.isNotEmpty()) {
            ndsRegions.sortedBy { it.inset }.joinToString(" + ") { "${it.screen.value} ${it.dst.width.roundToInt()}x${it.dst.height.roundToInt()}" }
        } else {
            val out = shownPicture ?: FrontendSettings.parse(frontendValues).let { s ->
                frontendLayout(game.width.toFloat(), game.height.toFloat(), coreW, coreH, g[2], s.scaling, s.offsetX, s.offsetY, overlayFile?.aspect).content
            }
            "${out.width.roundToInt()}x${out.height.roundToInt()}"
        }
        val rate = if (fps == null) "paused" else String.format(java.util.Locale.US, "%.1f fps", fps)
        return String.format(java.util.Locale.US, "%s  %s  %dx%d → %s", rate, gameCoreId, coreW, coreH, shown)
    }
}

/** An [NdsRegion] in the fork's terms: dst as 0..1 of the [w] x [h] game area (the view's whole viewport). */
internal fun NdsRegion.toScreenRegion(w: Float, h: Float) = ScreenRegion(
    src = ScreenRect(src.left, src.top, src.right, src.bottom),
    dst = ScreenRect(dst.x / w, dst.y / h, (dst.x + dst.width) / w, (dst.y + dst.height) / h),
    shaded = shaded,
    touch = touch,
    alpha = alpha,
)
