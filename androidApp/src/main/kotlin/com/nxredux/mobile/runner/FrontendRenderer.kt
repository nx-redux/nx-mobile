package com.nxredux.mobile.runner

import com.nxredux.mobile.skin.GlassScreen
import com.nxredux.mobile.skin.SkinN3ds
import com.nxredux.mobile.skin.SkinDual
import com.swordfish.libretrodroid.FrameComposition
import android.content.Context
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
import com.nxredux.mobile.layout.PadRoom
import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.layout.overlayClear
import com.nxredux.mobile.layout.picture
import com.nxredux.mobile.layout.pictureSpace
import com.nxredux.mobile.layout.placedFor
import com.nxredux.mobile.options.BRICK_HEIGHT
import com.nxredux.mobile.options.FrontendSettings
import com.nxredux.mobile.options.NdsLayoutChoice
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
import com.nxredux.mobile.shaders.PresetException
import com.nxredux.mobile.shaders.ScaleType
import com.nxredux.mobile.shaders.ShaderParam
import com.nxredux.mobile.shaders.SlangPreset
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
import com.swordfish.libretrodroid.SlangPassData
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
    /** Whether a 3DS skin's core is pinned to the plain unswapped stack (not under netplay, which keeps the player's layout). */
    val n3dsStackPinned: Boolean
    fun isLandscape(): Boolean
    /** The DS/3DS screens cut for the second display, and what it draws ([DualScreenController.secondDsRegions]). */
    fun secondDsRegions(g: FloatArray, map: ScreenMap?): List<NdsRegion>?
    fun secondRegions(g: FloatArray, ds: List<NdsRegion>?): List<ScreenRegion>
    /** The open in-game menu's shader rows, and its frontend rows (overlay list, locks, overlay availability), again. */
    fun publishMenuShaders()
    fun publishMenuFrontend()
    fun toast(msg: String): android.widget.Toast
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
    /** Main shows no game picture: the panel modes with the picture on the second display. */
    fun mainShowsNoPicture(): Boolean
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
    private fun secondDsRegions(g: FloatArray) = host.secondDsRegions(g, screenMap)

    /** The core's video view map for the frame laid out last (DS / 3DS); null without one or when the frame doesn't fit it. */
    var screenMap: ScreenMap? = null
        private set

    private fun screenMapFor(view: GLRetroView, g: FloatArray): ScreenMap? {
        if (!NdsOptions.appliesTo(gameTag) && !isN3ds) return null
        val views = view.videoViews().map { MapView(it.x, it.y, it.width, it.height, it.screen, it.eye) }
        return ScreenMap.from(views, g[0].roundToInt(), g[1].roundToInt(), byShape = isN3ds)
    }

    /** The DS frame can be cut into its two screens: the core's map says where, or it is the plain 256 x 384 stack. */
    private fun dsScreensKnown(g: FloatArray): Boolean = screenMap != null || NdsLayout.isDsFrame(g[0].roundToInt(), g[1].roundToInt())
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

    /** 3DS under Azahar's video views: the core stays at Default, unswapped; the app draws the player's layout. */
    var n3dsViews = false
    /** The Azahar layout the player's row asks for ([N3dsOptions.coreLayoutFor]), drawn by the app under views. */
    var n3dsComposeLayout = "default"
    /** The app's 3DS swap under views ([N3dsSwap]). */
    var n3dsSwapped = false

    private fun n3dsComposition(): FrameComposition? {
        val map = screenMap ?: return null
        if (!isN3ds || !n3dsViews || dualMode == DualMode.SPLIT || padLayout.skinShown) return null
        val layout = if (dualMode == DualMode.PANEL_UP) "side_by_side" else n3dsComposeLayout
        val factor = N3dsSwap.factorFromTopHeight(map.topSizePx.second)
        val c = AzaharLayout.compose(layout, factor, n3dsProportion.toFloatOrNull() ?: 1f, n3dsSwapped)
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        return FrameComposition(c.width, c.height, c.placements.map { p ->
            val src = map.src(p.screen)
            ScreenRegion(
                ScreenRect(src.left, src.top, src.right, src.bottom),
                ScreenRect(p.dst.x / w, p.dst.y / h, (p.dst.x + p.dst.width) / w, (p.dst.y + p.dst.height) / h),
                shaded = NdsLayout.takesShader(p.screen), touch = p.screen == NdsScreen.BOTTOM,
            )
        })
    }

    /** 3DS SWAP under views on one display: the app's Toggle / Hold ([N3dsSwap]); false when another layout owns it. */
    fun onN3dsViewsSwap(down: Boolean, mode: String): Boolean {
        if (!isN3ds || !n3dsViews || screenMap == null || dualMode == DualMode.SPLIT || padLayout.skinShown) return false
        val next = N3dsSwap.next(n3dsSwapped, mode, down)
        if (next != n3dsSwapped) {
            n3dsSwapped = next
            relayoutFrontend()
        }
        return true
    }

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

    /** What a save state's picture keeps (game-area px): the one picture, or the DS / 3DS top screen ([thumbnailPicture]). */
    fun thumbnailPicture(): PxRect? = if (ndsRegions.isNotEmpty()) ndsRegions.thumbnailPicture() else shownPicture

    // The second surface's screens and whole frame as last laid out (px of it), for a save state's picture copied there.
    private var secondThumbScreens: List<NdsRegion>? = emptyList()
    private var secondThumbWhole: PxRect? = null

    /**
     * Where a save state's picture is copied from ([thumbnailSource]): main's surface, or the second display's while it
     * draws the picture ([secondDrawing]: the surface is there and the GL thread draws on it).
     */
    fun thumbnailSource(secondDrawing: Boolean): ThumbnailSource =
        thumbnailSource(ndsRegions, shownPicture, secondThumbScreens, secondThumbWhole, host.mainShowsNoPicture(), secondDrawing)

    /** Called after each relayout: [thumbnailPicture] may have moved (the resume cover follows it). */
    var onPictureLaidOut: (() -> Unit)? = null
    private var hudJob: Job? = null

    /** The overlay file names offered for this console (the Overlay setting's list). */
    fun overlayNames(): List<String> = OverlayResolver.names(overlaySets)

    /**
     * The built-in shader for sharpness [s]: SHARP, or NEAREST while a pad skin fills its screen at a fractional size
     * ([skinFill]: nearest would make pixels uneven there), takes sharp bilinear; else the Default with the chosen
     * sampling. A preset ([applyShader]) replaces it through [GLRetroView.shaderChain].
     */
    fun shaderFor(s: FrontendSettings): ShaderConfig =
        if (s.sharp || (skinFill && !s.linear)) ShaderConfig.Sharp else ShaderConfig.Default(linear = s.linear)

    /**
     * A picture at a fractional size: a pad skin's screen filled at the picture's shape (Auto / Aspect), or a picture
     * filling its space under Auto ([autoFill]): see [shaderFor].
     */
    private var skinFill = false

    /**
     * The picture is drawn at a fractional scale: the DS screens under Screen Scaling other than Native
     * ([NdsLayout.scaledRegions]), or any console's picture fitted under Auto ([picture]).
     */
    private var autoFill = false

    /** Counts [relayoutFrontend] calls: one that started another inside it (a 3DS proportion change) stops there. */
    private var layoutGeneration = 0

    /** The DS layout just made ([ndsLayout]) fills its space at a fractional scale; reset before each. */
    private var ndsFilled = false

    /** [autoFill] for the layout just made, the view's shader following it when it changes. */
    private fun setAutoFill(fill: Boolean) {
        if (fill == autoFill) return
        autoFill = fill
        retroView?.let { syncSkinFill(it, FrontendSettings.parse(frontendValues)) }
    }

    /** [skinFill] for this layout, the view's shader following it when it changes. */
    private fun syncSkinFill(view: GLRetroView, s: FrontendSettings) {
        val fill = autoFill || (padLayout.skinShown && padLayout.skinPlacement() != null &&
            (s.scaling == ScreenScaling.AUTO || s.scaling == ScreenScaling.ASPECT))
        if (fill == skinFill) return
        skinFill = fill
        val shader = shaderFor(s)
        if (view.shader != shader) view.shader = shader
    }

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
            val appContext = activity.applicationContext
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { loadChain(appContext, preset) }
                }
                if (generation != shaderGeneration) return@launch
                val loaded = result.getOrElse { e ->
                    NxLog.w(TAG_LOG, "shader $preset failed", e)
                    toast("Shader: ${e.message ?: e.javaClass.simpleName}")
                    requestedPreset = null   // this load is the newest request, so forget it for a retry
                    dropShaderChain()
                    return@launch
                }
                loadedPreset = preset
                shaderParams = loaded.params
                // Every parameter with its value: the view reads one absent from the map as 0, not its default.
                view.shaderChain = loaded.chain.copy(params = ShaderOptions.paramValues(shaderValues, loaded.params))
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

    /** A preset ready for the view (its parameter values still to fill in), with the parameters it declares. */
    private class LoadedChain(val chain: ShaderChainData, val params: List<ShaderParam>)

    /**
     * Reads and prepares [preset], GLSL or slang by its file name: parsed, its LUTs decoded, a slang pass translated
     * for this device (or read back from [SlangCache]). Blocking; throws with the reason shown to the player.
     */
    private fun loadChain(context: Context, preset: String): LoadedChain {
        val read: (String) -> String? = { Shaders.read(host.shaderRoots, it) }
        if (SlangPreset.isSlang(preset)) {
            val chain = SlangPreset.parse(preset, read)
            val es = SlangCache.esVersion(context)
            val passes = chain.passes.mapIndexed { n, p ->
                SlangPassData(
                    SlangCache.translate(context, p, n, es), p.filterLinear, wrapOf(p.wrap), scaleOf(p.scaleTypeX), scaleOf(p.scaleTypeY),
                    p.scaleX, p.scaleY, p.frameCountMod, p.alias, p.format, p.mipmapInput,
                )
            }
            // A slang pass that samples a texture the chain lacks does not build, so a missing LUT fails here, by name.
            val luts = chain.luts.map { lut ->
                Shaders.file(host.shaderRoots, lut.path)?.let { decodeLut(it, lut.id, lut.linear, lut.wrap, lut.mipmap) }
                    ?: throw PresetException("texture ${lut.id} unreadable: ${lut.path}")
            }
            return LoadedChain(ShaderChainData(emptyList(), luts, emptyMap(), passes), chain.params)
        }
        val chain = GlslPreset.parse(preset, read)
        chain.warnings.forEach { NxLog.i(TAG_LOG, "shader $preset: $it") }
        val luts = chain.luts.mapNotNull { lut -> Shaders.file(host.shaderRoots, lut.path)?.let { decodeLut(it, lut.id, lut.linear, lut.wrap, false) } }
        val passes = chain.passes.map { p ->
            ShaderPassData(p.source, p.filterLinear, wrapOf(p.wrap), scaleOf(p.scaleTypeX), scaleOf(p.scaleTypeY), p.scaleX, p.scaleY, p.frameCountMod, p.alias)
        }
        return LoadedChain(ShaderChainData(passes, luts, emptyMap()), chain.params)
    }

    private fun wrapOf(w: WrapMode) = when (w) { WrapMode.EDGE -> ShaderWrap.EDGE; WrapMode.REPEAT -> ShaderWrap.REPEAT; WrapMode.MIRRORED -> ShaderWrap.MIRRORED; WrapMode.BORDER -> ShaderWrap.BORDER }
    private fun scaleOf(s: ScaleType) = when (s) { ScaleType.NONE -> ShaderScaleType.NONE; ScaleType.SOURCE -> ShaderScaleType.SOURCE; ScaleType.VIEWPORT -> ShaderScaleType.VIEWPORT; ScaleType.ABSOLUTE -> ShaderScaleType.ABSOLUTE }

    /** RGBA8 bytes of a LUT PNG (Bitmap ARGB_8888 is RGBA in memory order); null with a log when unreadable. */
    private fun decodeLut(file: File, id: String, linear: Boolean, wrap: WrapMode, mipmap: Boolean): ShaderLutData? {
        var bmp = runCatching { BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inPremultiplied = false }) }.getOrNull()
        if (bmp == null) { NxLog.w(TAG_LOG, "shader texture $id unreadable: ${file.path}"); return null }
        // The decoder may ignore inPreferredConfig (16-bit, hardware); the view needs exactly 4 bytes per pixel.
        if (bmp.config != Bitmap.Config.ARGB_8888) {
            val copy = bmp.copy(Bitmap.Config.ARGB_8888, false)
            bmp.recycle()
            if (copy == null) { NxLog.w(TAG_LOG, "shader texture $id could not be converted: ${file.path}"); return null }
            bmp = copy
        }
        if (bmp.byteCount != bmp.width * bmp.height * 4) {
            NxLog.w(TAG_LOG, "shader texture $id has an unexpected size: ${file.path}")
            bmp.recycle()
            return null
        }
        val buf = ByteBuffer.allocate(bmp.byteCount)
        bmp.copyPixelsToBuffer(buf)
        val data = ShaderLutData(id, bmp.width, bmp.height, buf.array(), linear, wrapOf(wrap), mipmap)
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
        relayoutFrontend(g)
    }

    /**
     * Lays the picture out in the game area and the pad around it. The space the pad leaves ([PadRoom]: the space above
     * portrait's bare buttons, else the whole area) holds the picture: under Screen Scaling Auto at its console's scale ([picture]); Native, Aspect and Fullscreen
     * size it in the whole area. DS games (a plain 256x384 frame) get their screens as regions of the view
     * ([NdsLayout], in that same space), with the viewport the whole area and no offsets or overlay: the fork ignores the
     * screen offset and the scaling mode for regions. Under a pad skin, DS / 3DS screens go in its glasses instead
     * ([skinDsLayout]). Other games: the picture (or its overlay box) becomes the GL
     * viewport and the overlay view's bounds; the offsets (scaled to the box) go to the view and move the picture after
     * its placement. The bare buttons then follow the final picture ([PadLayoutController.setOverlayClear]). No-op until laid
     * out. [geometry]: the view's, when the caller has just read it.
     */
    fun relayoutFrontend(geometry: FloatArray? = null) {
        val generation = ++layoutGeneration
        val view = retroView ?: return
        if (!view.isAttachedToWindow) return   // the GL thread is gone once detached
        val game = findViewById<FrameLayout>(R.id.gameContainer)
        val w = game.width.toFloat()
        val h = game.height.toFloat()
        if (w <= 0f || h <= 0f) return
        val packed = geometry ?: view.getGameGeometry()
        view.mainCovered = host.mainShowsNoPicture()
        screenMap = screenMapFor(view, packed)
        // 3DS on one display (and the Panel Up frame) from the map: the app rebuilds Azahar's own layout from it, and
        // that composed frame is the frame every later step lays out.
        val composition = n3dsComposition()
        view.composition = composition
        val g = composition?.let { floatArrayOf(it.width.toFloat(), it.height.toFloat(), it.width / it.height.toFloat(), packed[3]) } ?: packed
        val origin = IntArray(2).also { game.getLocationInWindow(it) }
        val s = FrontendSettings.parse(frontendValues)   // once for this layout
        syncSkinFill(view, s)
        val frame = coreFrame(g)   // null before the first frame (0x0)
        frame?.let(padLayout::onFrame)
        val room = padLayout.padRoom()
        val shape = activity.windowShape()   // one window reading for this layout
        val screen = shape.screenClass
        val overlayH = padLayout.hiddenOverlayHeightPx()
        // The DS / 3DS may reach a third into portrait's bare-button band (the Fold's 6x + 3x), the rest a quarter.
        val space = pictureSpace(room, w, h, overlayH, hiddenBandReach(NdsOptions.appliesTo(gameTag) || N3dsOptions.appliesTo(gameTag)))
        val skinDs = skinDsLayout(g)
        ndsFilled = false
        val nds = skinDs?.regions ?: ndsLayout(w, h, g, space, room, overlayH, screen, s.scaling) ?: n3dsSplitLayout(w, h)
        // Auto fills the space at a fractional scale: the DS screens ([ndsLayout]), or one picture fitted ([picture]).
        setAutoFill(ndsFilled || (nds == null && !padLayout.skinShown && frame != null &&
            s.scaling == ScreenScaling.AUTO))
        // An area too small for one DS screen pixel (NdsLayout returns no regions) is only ever a transient mid-resize
        // size. Keep whatever is drawn now rather than letting an empty list flip a split game to the whole 256x384
        // frame (and the overlay back on) for that moment; the next size change lays it out again.
        if (nds != null && nds.isEmpty()) return
        // Likewise a skin glass with no pixel: keep what is drawn.
        if (skinDs != null && listOf(skinDs.top, skinDs.bottom).any { it.width <= 0f || it.height <= 0f }) return
        // Before its first frame a DS game's frame size is unknown (0x0): it stays split as it started, overlay off.
        if (nds == null && ndsSplit && !gameReady) return
        ndsRegions = nds ?: emptyList()
        // the bare pad over the DS / 3DS touch screen gives it the taps beside its buttons (not under a skin: its own glass)
        padLayout.setBareTouchScreen(ndsRegions.takeIf { skinDs == null }?.firstOrNull { it.touch }?.dst)
        val secondDs = secondDsRegions(g)
        secondNdsRegions = secondDs.takeIf { isNds }.orEmpty()
        secondThumbScreens = secondDs
        secondThumbWhole = if (secondDs == null) host.secondWholeFrame(g) else null
        if (isNds) reportNds3d(w, h, screen, halfFolded = skinDs != null)
        if (isN3ds && nds != null) reportN3ds3d(w, h, screen, halfFolded = skinDs != null, g, flat = null, secondDs)
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
            // Under a skin the speed indicator goes on the top glass's screen.
            pictureBox.value = windowRect(origin, skinDs?.top ?: shown)
            shownPicture = null
            view.screenRegions = nds.map { it.toScreenRegion(w, h) }
            // Under a skin the pad takes no room beside the screens.
            fitPad(if (skinDs != null) PadRoom.NONE else room, shown, frameKnown = true, h, overlayH)
            val scales = nds.joinToString(" + ") { "${it.screen.value}${if (it.inset) " inset" else ""} %.2fx".format(java.util.Locale.US, it.dst.height / 192f) }
            if (skinDs != null) {
                logLayout(w, h, shape, "SKIN_DS", "top=${skinDs.top.short()} bottom=${skinDs.bottom.short()}", "${if (isN3ds) "3ds" else "ds"} $scales")
            } else {
                logLayout(w, h, shape, room.name, spaceAndPicture(space, shown), "ds $scales")
            }
            onPictureLaidOut?.invoke()
            return
        }
        if (view.screenRegions.isNotEmpty()) view.screenRegions = emptyList()
        val overlayAspect = overlayFile?.aspect
        val box: PxRect
        val shown: PxRect
        val offset: PointF
        val skinGlass = if (padLayout.skinShown) padLayout.skinPlacement()?.glass(GlassScreen.PICTURE)?.rect else null
        if (isN3ds) {
            val auto = s.scaling == ScreenScaling.AUTO && frame != null
            n3dsPictureSpace = space.takeIf { auto && (dualMode == DualMode.OFF || pictureOn == PictureOn.MAIN) }
        }
        if (skinGlass != null) {
            // A pad skin: the picture fills its glass at its shape (Auto, Aspect; drawn sharp bilinear, [shaderFor]),
            // at the biggest whole multiple that fits (Native), or stretched over it (Fullscreen).
            box = when (s.scaling) {
                ScreenScaling.NATIVE -> wholeMultiple(skinGlass, fitAspect(skinGlass, g[2]), g)
                ScreenScaling.FULLSCREEN -> skinGlass
                else -> fitAspect(skinGlass, g[2])
            }
            offset = PointF(0f, 0f)
            shown = box
        } else if (s.scaling == ScreenScaling.AUTO && frame != null) {
            val placed = placedFor(room, picture(space, frame, overlayAspect), h, overlayH)
            // The offsets as Native / Aspect / Fullscreen scale them: to the overlay's box, else the game area.
            val unit = (if (overlayAspect != null && overlayAspect > 0f) placed.height else h) / BRICK_HEIGHT
            box = placed
            offset = PointF(s.offsetX * unit, s.offsetY * unit)
            shown = placed.copy(x = placed.x + offset.x, y = placed.y + offset.y)
        } else {
            // The overrides (Native, Aspect, Fullscreen); Auto too before the first frame, which has nothing to place yet.
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
            // It laid out again with a new proportion (the app's own 3DS frame): that layout stands, not this stale one.
            if (generation != layoutGeneration) return
        }
        if (isPs2) reportPs2Resolution(w, h, screen, shown, g)
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
        fitPad(room, covered, frameKnown = frame != null, h, overlayH)
        onPictureLaidOut?.invoke()
        val scale = frame?.let { shown.height / (if (it.rotated) it.width else it.height) }
        val n3ds = if (isN3ds) " 3ds views=${composition != null} proportion=$n3dsProportion" else ""
        logLayout(w, h, shape, room.name, spaceAndPicture(space, shown), "${s.scaling} frame=${frame?.let { "${it.width.roundToInt()}x${it.height.roundToInt()}" }} scale=${scale?.let { "%.2f".format(java.util.Locale.US, it) }}$n3ds")
    }

    /**
     * The DS 3D scale's inputs to the host ([FrontendHost.onNds3dLayout]) for the layout just made in the [w] x [h] game
     * area: the shown scale of every DS screen (both displays', or both glasses under a pad skin), and the display maximum,
     * the main display's (worked out once per area) or with a second display drawing DS screens, the bigger of it and
     * those screens' scale (one screen fills it, so that is the most it reaches).
     */
    private fun reportNds3d(w: Float, h: Float, screen: ScreenClass, halfFolded: Boolean) {
        val second = Nds3dResolution.currentScale(secondNdsRegions) ?: 1
        val shown = if (host.mainShowsNoPicture()) secondNdsRegions else ndsRegions + secondNdsRegions
        report3d(w, h, screen, halfFolded, Nds3dResolution.currentScale(shown), second) {
            Nds3dResolution.displayMax(w, h, screen)
        }
    }

    /**
     * The PS2 3D scale's inputs to the host, as [reportNds3d]: the [shown] picture's height over the 448 px frame, and the
     * window's display maximum ([Ps2Resolution.displayMax]); with the picture on a second display, its height there.
     * Nothing before the first frame (the launch's value stands).
     */
    private fun reportPs2Resolution(w: Float, h: Float, screen: ScreenClass, shown: PxRect, g: FloatArray) {
        if (!gameReady || shown.height <= 0f) return
        val main = shown.height.roundToInt().takeUnless { host.mainShowsNoPicture() }
        val (current, second) = Ps2Resolution.shownScales(main, host.secondWholeFrame(g)?.height?.roundToInt())
        report3d(w, h, screen, halfFolded = false, current, second) {
            Ps2Resolution.displayMax(w.roundToInt(), h.roundToInt())
        }
    }

    /**
     * The 3DS 3D scale's inputs to the host, as [reportNds3d]: flat, the [flat] picture's scale at the factor the frame
     * was rendered at, read from its size ([N3ds3dResolution.frameFactor]; an unknown frame keeps the last one read);
     * under a pad skin or split ([flat] null), the screens' regions ([N3ds3dResolution.regionScale]). A second display drawing
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
        val main = when {
            host.mainShowsNoPicture() -> null
            flat != null -> N3ds3dResolution.currentScale(flat.width, g[0], factor)
            else -> N3ds3dResolution.regionScale(ndsRegions)
        }
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
        // Under the panel the main display shows no screen: only the second display's reach counts.
        host.onNds3dLayout(current, if (host.mainShowsNoPicture()) second else maxOf(nds3dMainMax, second), sizeChange)
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
     * The pad fitted to the final [picture] (game-area px): portrait's bare buttons solid when the picture stops above
     * their band. A [frameKnown] picture lets the pads that waited for it show.
     */
    private fun fitPad(room: PadRoom, picture: PxRect, frameKnown: Boolean, areaH: Float, overlayH: Float) {
        // solid only while the picture clears the band and no button the player moved sits over it
        if (room == PadRoom.HIDDEN_BAND) padLayout.setOverlayClear(frameKnown && overlayClear(picture.y + picture.height, areaH, overlayH) && !padLayout.bareOver(picture))
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

    /** The relayout's inputs and outcome, once per change, for the Debug log. */
    private var lastLayoutLog: String? = null

    private fun spaceAndPicture(space: PxRect, picture: PxRect) = "space=${space.short()} picture=${picture.short()}"

    /** [rects]: where the picture went ([spaceAndPicture]; under a pad skin, the two screens' rects). */
    private fun logLayout(w: Float, h: Float, shape: WindowShape, room: String, rects: String, what: String) {
        val line = "relayout: window=${shape.widthPx}x${shape.heightPx} ${shape.screenClass} area=${w.roundToInt()}x${h.roundToInt()} room=$room " +
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
    private fun ndsLayout(w: Float, h: Float, g: FloatArray, space: PxRect, room: PadRoom, overlayH: Float, screen: ScreenClass, scaling: ScreenScaling): List<NdsRegion>? {
        if (!NdsOptions.appliesTo(gameTag)) return null
        val frameW = g[0].roundToInt()
        val frameH = g[1].roundToInt()
        if (!dsScreensKnown(g)) {
            // Safety net (0x0 before the first frame; anything else is a core or option change): one picture, as for any core.
            if (frameW > 0 && frameH > 0 && !ndsFrameWarned) {
                ndsFrameWarned = true
                NxLog.w(TAG_LOG, "NDS frame is ${frameW}x$frameH, not a 256x384 stack: drawing it as one picture")
            }
            return null
        }
        if (dualMode == DualMode.SPLIT) {
            val main = DualScreen.splitMain(NdsSettings.parse(frontendValues).big, pictureOn)
            // under a pad skin (landscape with a second display): the main display's screen in the skin's screen
            val glass = SkinDual.splitGlass(padLayout.skinPlacement()?.takeIf { padLayout.skinShown }, main)
            return (if (glass != null) DualScreen.ndsScreenIn(SkinDual.dsScreenIn(glass), main) else DualScreen.ndsScreen(w, h, main)).withMap(screenMap)
        }
        if (dualMode == DualMode.PANEL_UP) return DualScreen.ndsPanelUp(w, h, NdsSettings.parse(frontendValues)).withMap(screenMap)
        val landscape = isLandscape()
        val settings = NdsSettings.parse(frontendValues)
        val choice = settings.layoutShown(landscape, ndsControllerMode(), screen)
        // Screen Scaling Native: whole-number scales; the rest fill the space (Large in portrait: the top screen the full
        // width, the touch screen moved down under it).
        val native = scaling == ScreenScaling.NATIVE
        val regions = NdsLayout.scaledRegions(
            space, h, choice, landscape, screen, settings, NDS_INSET_MARGIN_DP * resources.displayMetrics.density, native,
        )
        ndsFilled = !native && regions.isNotEmpty()
        if (room != PadRoom.HIDDEN_BAND || regions.isEmpty()) return regions.withMap(screenMap)
        // The band hidden: the screens as one picture, at the top when they reach into the overlay, else centred above it.
        val shown = regions.shownRect()
        val dy = (placedFor(room, shown, h, overlayH).y - shown.y).roundToInt().toFloat()
        return regions.map { it.copy(dst = it.dst.copy(y = it.dst.y + dy)) }.withMap(screenMap)
    }

    /**
     * A DS or 3DS under a pad skin: its screens in the skin's glass (the top and touch glasses, or the halves of one tall
     * glass). Null without a skin, or for a DS frame that is not the plain stack.
     * A 3DS ([SkinN3ds]): each screen at its own shape (SWAP trades them, [PadLayoutController.n3dsSkinBig]); a frame
     * that isn't azahar's plain stack (netplay keeps the player's layout) is drawn whole in the top glass.
     */
    private fun skinDsLayout(g: FloatArray): SkinDs? {
        if (!padLayout.skinShown || dualMode != DualMode.OFF) return null
        if (isN3ds) return skinN3dsLayout(g)
        if (!NdsOptions.appliesTo(gameTag) || !dsScreensKnown(g)) return null
        val p = padLayout.skinPlacement() ?: return null
        // a regular phone's landscape: the player's layout in the skin's one glass (NdsOptions.skinLayout), as without a skin
        val glass = p.glass(GlassScreen.STACK)?.rect
        val choice = NdsOptions.skinLayout(frontendValues, isLandscape(), ndsControllerMode(), activity.windowShape().screenClass)
        if (glass != null && choice != null) {
            val laid = NdsLayout.regions(glass, choice, true, activity.windowShape().screenClass, NdsSettings.parse(frontendValues),
                NDS_INSET_MARGIN_DP * resources.displayMetrics.density)
            // Side by side fills the glass (a whole-number scale leaves wide margins); the others stay whole-number
            val regions = (if (choice == NdsLayoutChoice.SIDE_BY_SIDE) laid.filledTo(glass) else laid).withMap(screenMap)
            if (regions.isNotEmpty()) {
                val top = regions.firstOrNull { it.screen == NdsScreen.TOP }?.dst ?: regions.first().dst
                return SkinDs(top, regions.firstOrNull { it.screen == NdsScreen.BOTTOM }?.dst ?: top, regions)
            }
        }
        val stack = glass?.let { fitAspect(it, 256f / 384f) }   // the two screens' stack, centred in the glass
        val top = stack?.copy(height = stack.height / 2f) ?: p.glass(GlassScreen.TOP)?.rect?.let { fitAspect(it, 256f / 192f) } ?: return null   // the top screen, centred in its glass
        val bottom = stack?.copy(y = stack.y + stack.height / 2f, height = stack.height / 2f) ?: p.glass(GlassScreen.TOUCH)?.rect ?: return null
        val big = NdsSettings.parse(frontendValues).big
        return SkinDs(top, bottom, dsScreenRegions(top, bottom, DsKind.DS, big).withMap(screenMap))
    }

    private fun skinN3dsLayout(g: FloatArray): SkinDs? {
        val p = padLayout.skinPlacement() ?: return null
        val stack = p.glass(GlassScreen.STACK)?.rect
        val top = p.glass(GlassScreen.TOP)?.rect
        val fw = g[0].roundToInt()
        val fh = g[1].roundToInt()
        val regions = if (SkinN3ds.cutsStack(fw, fh, pinned = host.n3dsStackPinned)) {
            // a regular phone's landscape: the player's layout in the skin's one glass (N3dsOptions.skinLayout)
            val layout = N3dsOptions.skinLayout(frontendValues, isLandscape(), ndsControllerMode(), activity.windowShape().screenClass)
            SkinN3ds.regions(stack, top, p.glass(GlassScreen.TOUCH)?.rect, padLayout.n3dsSkinBig, layout)?.withMap(screenMap) ?: return null
        } else {
            val whole = SkinN3ds.whole(stack, top, fw, fh) ?: return null
            listOf(NdsRegion(NdsScreen.TOP, FrameRect(0f, 0f, 1f, 1f), whole, shaded = true, touch = false))
        }
        return SkinDs(regions[0].dst, regions.getOrNull(1)?.dst ?: regions[0].dst, regions)
    }

    /** [fit] (the picture at its shape in [glass]) cut to the biggest whole multiple of the frame's height, centred. */
    private fun wholeMultiple(glass: PxRect, fit: PxRect, g: FloatArray): PxRect {
        val frameH = g[1]
        if (frameH <= 0f) return fit
        val n = kotlin.math.floor(fit.height / frameH).coerceAtLeast(1f)
        val ph = frameH * n
        val pw = fit.width * (ph / fit.height)
        return PxRect(glass.x + (glass.width - pw) / 2f, glass.y + (glass.height - ph) / 2f, pw, ph)
    }

    /** The biggest [aspect] (width / height) rect centred in [r]; [r] itself for an unknown aspect. */
    private fun fitAspect(r: PxRect, aspect: Float): PxRect {
        if (aspect <= 0f || r.width <= 0f || r.height <= 0f) return r
        val w = minOf(r.width, r.height * aspect)
        val h = w / aspect
        return PxRect(r.x + (r.width - w) / 2f, r.y + (r.height - h) / 2f, w, h)
    }

    /** A DS / 3DS under a pad skin: the [top] and [bottom] screens' rects (game-area px) and its screens as [regions]. */
    private class SkinDs(val top: PxRect, val bottom: PxRect, val regions: List<NdsRegion>)

    /**
     * 3DS SWAP (the skin's SWAP, SELECT + L, a pad's left-stick click) [down] or up under a pad skin with the screens as regions
     * ([skinDsLayout]): a press trades the screens' glasses (touch follows the bottom screen) and the core never sees it.
     * False otherwise: the key is someone else's (split's, else Azahar's own).
     */
    fun onSkinN3dsSwap(down: Boolean): Boolean {
        if (!isN3ds || dualMode != DualMode.OFF || !padLayout.skinShown) return false
        if (down) {
            padLayout.swapSkinN3ds()
            relayoutFrontend()
        }
        return true
    }

    /**
     * Portrait with the pad band dropped for a non-clip-on controller ([PadBandRule]): the DS layout and SELECT + Left/Right
     * use the controller portrait layout then. [applyLayout] relays out on connect/disconnect, so this follows live.
     */
    fun ndsControllerMode(): Boolean {
        val tag = intent.getStringExtra(EXTRA_TAG) ?: gameTag   // set before the launch's gameTag, for onCreate's layout
        val portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        return PadBandRule.controllerLayout(tag, portrait, padLayout.layoutControllerNames())
    }

    /** 3DS split across the displays: the main display's screen alone, as a region of Azahar's fixed stacked frame; else null. */
    private fun n3dsSplitLayout(w: Float, h: Float): List<NdsRegion>? {
        if (!isN3ds || dualMode != DualMode.SPLIT) return null
        val main = DualScreen.splitMain(n3dsPicture, pictureOn)
        // under a pad skin (landscape with a second display): the main display's screen in the skin's screen, at its shape
        val glass = SkinDual.splitGlass(padLayout.skinPlacement()?.takeIf { padLayout.skinShown }, main)
            ?: return DualScreen.n3dsScreen(w, h, main).withMap(screenMap)
        return DualScreen.n3dsScreenIn(SkinDual.screenIn(glass, if (main == NdsScreen.TOP) 5f / 3f else 4f / 3f), main).withMap(screenMap)
    }

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
