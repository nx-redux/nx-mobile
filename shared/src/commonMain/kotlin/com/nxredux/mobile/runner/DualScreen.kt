package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.NdsLayoutChoice
import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.NdsSettings
import com.nxredux.mobile.options.PxRect
import kotlin.math.min

/**
 * What a second display is used for: [OFF] nothing; [SPLIT] DS/3DS, one screen per display; [PANEL_UP] DS/3DS with
 * the panel on the second display and both screens side by side on the main one; [PANEL] a regular game with the
 * panel on the second display.
 */
enum class DualMode { OFF, SPLIT, PANEL_UP, PANEL }

/**
 * Which display is the picture screen (the wider one, by width/height): the game picture, or the DS/3DS top screen, goes
 * there; the other display is the touch screen (the DS/3DS bottom screen, or the panel).
 */
enum class PictureOn { MAIN, SECOND }

/**
 * Which display is the bottom (touch) screen: [AUTO] goes by shape ([DualScreen.pictureOn]); [MAIN] or [SECOND] is the
 * user's choice (Settings → Second screen → Bottom screen, or the console's override), the picture going on the other.
 */
enum class BottomScreen(
    /** As stored: the system setting's pref and the console override ([com.nxredux.mobile.options.BottomScreenOptions]). */
    val value: String,
) {
    AUTO("auto"), MAIN("main"), SECOND("second");

    companion object {
        /** A stored [value]; anything else (the override's `default` too) is [AUTO]. */
        fun parse(value: String?): BottomScreen = entries.firstOrNull { it.value == value } ?: AUTO

        /** The console's [override] when it picks a screen (main/second), else the [system] setting. */
        fun resolve(override: String?, system: BottomScreen): BottomScreen = parse(override).takeIf { it != AUTO } ?: system
    }
}

/** Which screen goes where when a second display is connected: the decision, and the DS/3DS screen layouts. */
object DualScreen {
    private const val DS_ASPECT = 4f / 3f
    private const val N3DS_TOP_ASPECT = 5f / 3f
    private const val SWAP_SCREEN = "citra_swap_screen"
    private const val SWAP_SCREEN_MODE = "citra_swap_screen_mode"

    /** The 3DS frame in Azahar's "default" layout at proportion 1, unswapped: 400s x 480s, the bottom screen centred. */
    private val N3DS_TOP_SRC = FrameRect(0f, 0f, 1f, 0.5f)
    private val N3DS_BOTTOM_SRC = FrameRect(0.1f, 0.5f, 0.9f, 1f)

    /** A DS/3DS game ([dualGame]) splits (or shows both screens with [panelUp]); any other game gets the panel. */
    fun mode(secondConnected: Boolean, settingOn: Boolean, dualGame: Boolean, panelUp: Boolean): DualMode = when {
        !secondConnected || !settingOn -> DualMode.OFF
        dualGame -> if (panelUp) DualMode.PANEL_UP else DualMode.SPLIT
        else -> DualMode.PANEL
    }

    /**
     * The picture screen: [PictureOn.SECOND] when the second display is at least as wide for its height as the main one
     * (both in px, the main as landscape), else [PictureOn.MAIN]. No second size yet: the main display. A [rotated] core
     * (SET_ROTATION: vertical arcade games, WonderSwan) stays on main, where the picture is drawn turned. A chosen
     * [bottom] screen puts the picture on the other one, whatever the shapes.
     */
    fun pictureOn(
        mainW: Int, mainH: Int, secondW: Int, secondH: Int, rotated: Boolean = false, bottom: BottomScreen = BottomScreen.AUTO,
    ): PictureOn = when {
        rotated -> PictureOn.MAIN
        secondW <= 0 || secondH <= 0 || mainW <= 0 || mainH <= 0 -> PictureOn.MAIN
        bottom == BottomScreen.MAIN -> PictureOn.SECOND
        bottom == BottomScreen.SECOND -> PictureOn.MAIN
        secondW.toLong() * mainH >= mainW.toLong() * secondH -> PictureOn.SECOND
        else -> PictureOn.MAIN
    }

    /** The main menu goes on the second display when the picture (the top screen) is on the main one. */
    fun menuOnSecond(
        mainW: Int, mainH: Int, secondW: Int, secondH: Int, rotated: Boolean, bottom: BottomScreen,
    ): Boolean = pictureOn(mainW, mainH, secondW, secondH, rotated, bottom) == PictureOn.MAIN

    /** Split, the DS/3DS screen on the main display: the [picture] screen's own when the picture is on main, else the other. */
    fun splitMain(picture: NdsScreen, pictureOn: PictureOn): NdsScreen = if (pictureOn == PictureOn.MAIN) picture else picture.other

    /**
     * Which display the DS stylus cursor goes on: the one showing the bottom screen. The [second] display's screens (split,
     * or both while the panel is on main) come first; else the [main] display's; null when neither shows it.
     */
    fun penOn(main: List<NdsRegion>, second: List<NdsRegion>): PictureOn? = when {
        second.any { it.screen == NdsScreen.BOTTOM } -> PictureOn.SECOND
        main.any { it.screen == NdsScreen.BOTTOM } -> PictureOn.MAIN
        else -> null
    }

    /** The whole core frame fitted at [aspect] in the area, centred, in whole px; empty without an area or aspect. */
    fun fullFrame(areaW: Float, areaH: Float, aspect: Float): PxRect {
        if (areaW <= 0f || areaH <= 0f || aspect <= 0f) return PxRect(0f, 0f, 0f, 0f)
        val w = NdsLayout.wholePx(min(areaW, areaH * aspect))
        val h = NdsLayout.wholePx(min(areaH, w / aspect))
        return PxRect(NdsLayout.wholePx((areaW - w) / 2f), NdsLayout.wholePx((areaH - h) / 2f), w, h)
    }

    /**
     * Split, DS screen [which] alone on a display's area (the main display's screen, or its `other` on the second one):
     * fitted 4:3 and centred, shaded; touch only on the bottom screen.
     */
    fun ndsScreen(areaW: Float, areaH: Float, which: NdsScreen): List<NdsRegion> = single(areaW, areaH, which, NdsLayout.src(which), DS_ASPECT)

    /**
     * Both DS screens side by side on the main display (the panel is on the second one); touch on the bottom screen.
     * Side by side is forced; the screen class only matters for Single screen, so a regular phone it is.
     */
    fun ndsPanelUp(areaW: Float, areaH: Float, settings: NdsSettings): List<NdsRegion> = NdsLayout.regions(
        PxRect(0f, 0f, areaW, areaH), NdsLayoutChoice.SIDE_BY_SIDE, landscape = true, ScreenClass.REGULAR_PHONE, settings, insetMarginPx = 0f,
    )

    /** [screen]'s rectangle in the 3DS frame in the split layout. */
    private fun n3dsSrc(screen: NdsScreen): FrameRect = if (screen == NdsScreen.TOP) N3DS_TOP_SRC else N3DS_BOTTOM_SRC

    /** Split, 3DS screen [which] alone on a display's area, as [ndsScreen]: the top fitted 5:3, the bottom 4:3, centred. */
    fun n3dsScreen(areaW: Float, areaH: Float, which: NdsScreen): List<NdsRegion> =
        single(areaW, areaH, which, n3dsSrc(which), if (which == NdsScreen.TOP) N3DS_TOP_ASPECT else DS_ASPECT)

    /**
     * The Azahar options a mode needs, or null to put back the usual ones ([DualMode.OFF], [DualMode.PANEL]: the caller
     * re-applies its layout and the user's stored swap values).
     */
    fun n3dsCoreOptions(mode: DualMode): Map<String, String>? = when (mode) {
        DualMode.SPLIT -> mapOf(
            "citra_layout_option" to "default", "citra_stacked_small_proportion" to "1",
            SWAP_SCREEN to "Top", SWAP_SCREEN_MODE to "Hold",
        )
        // Panel up, the whole frame is drawn and SWAP is Azahar's own key again: Toggle, so one press swaps the screens.
        DualMode.PANEL_UP -> mapOf(
            "citra_layout_option" to "side_by_side",
            SWAP_SCREEN to "Top", SWAP_SCREEN_MODE to "Toggle",
        )
        DualMode.OFF, DualMode.PANEL -> null
    }

    /** The swap options [n3dsCoreOptions] overrides while split, which leaving it must put back. */
    val N3DS_SWAP_KEYS: Set<String> = setOf(SWAP_SCREEN, SWAP_SCREEN_MODE)

    /**
     * The swap values to put back when split ends: what the core was [running] before it began, else the user's [stored]
     * ones, else Azahar's defaults (Top / Toggle).
     */
    fun n3dsSwapRestore(running: Map<String, String>?, stored: Map<String, String>): Map<String, String> = mapOf(
        SWAP_SCREEN to (running?.get(SWAP_SCREEN) ?: stored[SWAP_SCREEN] ?: "Top"),
        SWAP_SCREEN_MODE to (running?.get(SWAP_SCREEN_MODE) ?: stored[SWAP_SCREEN_MODE] ?: "Toggle"),
    )

    /** Azahar's touch-main-only rule: split, the app's regions decide where a finger touches, so the core takes it anywhere. */
    fun n3dsTouchMainOnly(mode: DualMode, padOverGame: Boolean): String =
        N3dsOptions.touchMainOnly(padOverGame && mode != DualMode.SPLIT)

    private fun single(w: Float, h: Float, screen: NdsScreen, src: FrameRect, aspect: Float): List<NdsRegion> {
        if (w <= 0f || h <= 0f) return emptyList()
        val size = NdsLayout.ScreenSize(min(w, h * aspect), aspect)
        if (size.width <= 0f || size.height <= 0f) return emptyList()
        val dst = PxRect(NdsLayout.wholePx((w - size.width) / 2f), NdsLayout.wholePx((h - size.height) / 2f), size.width, size.height)
        return listOf(NdsRegion(screen, src, dst, shaded = true, touch = screen == NdsScreen.BOTTOM))
    }
}

/**
 * The density the Compose UI on a second display lays out at. A big monitor (a 4K or 2560x1600 USB-C display) at its own
 * density is thousands of dp wide and everything looks tiny: when the short side is wider than [SHORT_SIDE_DP] dp the
 * density is raised so the short side is exactly [SHORT_SIDE_DP] dp; a handheld's second screen keeps its own.
 */
object SecondScreenScale {
    const val SHORT_SIDE_DP = 720f

    /** The density for a display of [widthPx] x [heightPx] at [density] (px per dp); [density] when the size is unknown. */
    fun density(widthPx: Int, heightPx: Int, density: Float): Float =
        if (widthPx <= 0 || heightPx <= 0) density else maxOf(density, minOf(widthPx, heightPx) / SHORT_SIDE_DP)
}
