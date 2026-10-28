package com.nxredux.mobile.layout

import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.NdsSettings
import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.NdsLayout
import com.nxredux.mobile.runner.shownRect

/** The picture the Pad layout editor previews a console without a known one around: a 224-line 4:3 console. */
internal val TYPICAL_FRAME = CoreFrame(256f, 224f, 4f / 3f, rotated = false)

private val FOUR_THREE = 4f / 3f

/**
 * Each console's usual core frame (its cores' default output at 1x, the aspect they report; 0 for square pixels), by
 * console id: what the Pad layout editor previews the landscape pad around ([previewFrame]). A console left out takes
 * [TYPICAL_FRAME]; DS and 3DS draw two screens and are placed by their own layouts ([previewPicture]).
 */
internal val CONSOLE_FRAMES: Map<String, CoreFrame> = mapOf(
    // Whole-height home consoles and arcade: their lines at the core's aspect.
    "fc" to CoreFrame(256f, 240f, FOUR_THREE, false),
    "fds" to CoreFrame(256f, 240f, FOUR_THREE, false),
    "sfc" to CoreFrame(256f, 224f, FOUR_THREE, false),
    "md" to CoreFrame(320f, 224f, FOUR_THREE, false),
    "segacd" to CoreFrame(320f, 224f, FOUR_THREE, false),
    "32x" to CoreFrame(320f, 224f, FOUR_THREE, false),
    "sms" to CoreFrame(256f, 192f, FOUR_THREE, false),
    "sg1000" to CoreFrame(256f, 192f, FOUR_THREE, false),
    "coleco" to CoreFrame(256f, 192f, FOUR_THREE, false),
    "pce" to CoreFrame(256f, 240f, FOUR_THREE, false),
    "fbn" to CoreFrame(384f, 224f, FOUR_THREE, false),     // CPS-1 / CPS-2, the common horizontal board
    "prboom" to CoreFrame(320f, 200f, FOUR_THREE, false),
    // Native handhelds: their screens in square pixels.
    "gb" to CoreFrame(160f, 144f, 0f, false),
    "gbc" to CoreFrame(160f, 144f, 0f, false),
    "sgb" to CoreFrame(256f, 224f, 0f, false),            // with the Super Game Boy border
    "gba" to CoreFrame(240f, 160f, 0f, false),
    "gg" to CoreFrame(160f, 144f, 0f, false),
    "ngp" to CoreFrame(160f, 152f, 0f, false),
    "ngpc" to CoreFrame(160f, 152f, 0f, false),
    "wsc" to CoreFrame(224f, 144f, 0f, false),
    "lynx" to CoreFrame(160f, 102f, 0f, false),
    "pkm" to CoreFrame(96f, 64f, 0f, false),
    "vb" to CoreFrame(384f, 224f, 0f, false),
    "p8" to CoreFrame(128f, 128f, 0f, false),
    // 3D consoles fill at their shape: 4:3, PSP its 480 x 272 screen.
    "n64" to CoreFrame(320f, 240f, FOUR_THREE, false),
    "ps" to CoreFrame(320f, 240f, FOUR_THREE, false),
    "dc" to CoreFrame(640f, 480f, FOUR_THREE, false),
    "gc" to CoreFrame(640f, 480f, FOUR_THREE, false),
    "wii" to CoreFrame(640f, 480f, FOUR_THREE, false),
    "ps2" to CoreFrame(640f, 448f, FOUR_THREE, false),
    "psp" to CoreFrame(480f, 272f, 0f, false),
)

/**
 * The 3DS frame Azahar draws in landscape for the app's [layout] ([N3dsOptions.LAYOUT_CYCLE]) at 1x: the 400 x 240 top
 * screen and the 320 x 240 touch screen, top over bottom with the touch screen at half size (Large screen) or the same
 * size (Stacked), the top one alone (Single screen) or both beside each other (Side by side).
 */
internal fun n3dsPreviewFrame(layout: String): CoreFrame = when (layout) {
    "stacked" -> CoreFrame(400f, 480f, 0f, false)
    "single_screen" -> CoreFrame(400f, 240f, 0f, false)
    "side_by_side" -> CoreFrame(720f, 240f, 0f, false)
    else -> CoreFrame(400f, 360f, 0f, false)           // large
}

/**
 * The frame the Pad layout editor previews [tag]'s console with on [screen] in landscape: the 3DS frame of the screen's
 * default landscape layout ([N3dsOptions.defaultLayout]), else the console's [CONSOLE_FRAMES] entry, else
 * [TYPICAL_FRAME] (an unknown tag, the Shared layout).
 */
fun previewFrame(tag: String, screen: ScreenClass): CoreFrame {
    val id = CoreRegistry.consoleOf(tag)?.id ?: return TYPICAL_FRAME
    if (id == "3ds") return n3dsPreviewFrame(N3dsOptions.defaultLayout(screen, landscape = true))
    return CONSOLE_FRAMES[id] ?: TYPICAL_FRAME
}

/**
 * The picture [tag]'s console shows in landscape in [space] on [screen] under Auto, as the game places it: DS games
 * their screens in the screen's default landscape layout ([NdsLayout.regions], as one picture, [shownRect]); the rest
 * their [previewFrame] at their console's scale ([picture]).
 */
fun previewPicture(tag: String, space: PxRect, screen: ScreenClass): PxRect {
    val scale = consoleScale(tag)
    if (scale == ConsoleScale.DS) {
        val settings = NdsSettings()
        // The default landscape layouts (Large screen, Side by side) have no picture-in-picture inset: no margin needed.
        val regions = NdsLayout.regions(space, settings.layoutShown(inLandscape = true, controllerMode = false, screen), true, screen, settings, 0f)
        if (regions.isNotEmpty()) return regions.shownRect()
    }
    return picture(space, previewFrame(tag, screen), scale, overlayAspect = null)
}
