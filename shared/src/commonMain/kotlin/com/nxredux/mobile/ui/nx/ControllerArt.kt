package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.shared.res.*
import com.nxredux.mobile.ui.ANDROID_TAG
import org.jetbrains.compose.resources.DrawableResource
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A console's controller (or handheld) for the main menu: [res] in colour on transparent, its cable cut
 * (`tools/controller-art/make_pads.py`, from ScreenScraper and user-supplied art: see THIRD_PARTY_ART.md). [brightness]
 * evens the set out: the mean luminance of the PlayStation pad over its own, clamped 0.5..2 (gba, fc, psp, fbn and
 * android set by eye).
 */
data class ConsolePad(val res: DrawableResource, val brightness: Float)

/**
 * Console id to pad. Add-ons and siblings reuse one: Super Game Boy the SNES pad, Sega CD and 32X the Mega Drive pad,
 * Neo Geo Pocket the Color's. Doom and Pico-8 use the VIC-20; Arcade the Competition Pro.
 */
internal val consolePadsById: Map<String, ConsolePad> = run {
    val sfc = ConsolePad(Res.drawable.menu_pad_sfc, 0.71f)
    val md = ConsolePad(Res.drawable.menu_pad_md, 1.80f)
    val ngpc = ConsolePad(Res.drawable.menu_pad_ngpc, 0.89f)
    val vic20 = ConsolePad(Res.drawable.menu_pad_vic20, 0.81f)
    mapOf(
        "nds" to ConsolePad(Res.drawable.menu_pad_nds, 0.65f), "3ds" to ConsolePad(Res.drawable.menu_pad_3ds, 2.00f),
        "n64" to ConsolePad(Res.drawable.menu_pad_n64, 0.79f),
        "psp" to ConsolePad(Res.drawable.menu_pad_psp, 1.50f), "ps" to ConsolePad(Res.drawable.menu_pad_ps, 1.00f),
        "ps2" to ConsolePad(Res.drawable.menu_pad_ps2, 2.00f),
        "gb" to ConsolePad(Res.drawable.menu_pad_gb, 0.77f), "gbc" to ConsolePad(Res.drawable.menu_pad_gbc, 1.26f),
        "gba" to ConsolePad(Res.drawable.menu_pad_gba, 1.15f), "fc" to ConsolePad(Res.drawable.menu_pad_fc, 0.80f),
        "sfc" to sfc, "sgb" to sfc, "md" to md, "segacd" to md, "32x" to md,
        "sms" to ConsolePad(Res.drawable.menu_pad_sms, 2.00f), "gg" to ConsolePad(Res.drawable.menu_pad_gg, 2.00f),
        "sg1000" to ConsolePad(Res.drawable.menu_pad_sg1000, 1.06f), "fds" to ConsolePad(Res.drawable.menu_pad_fds, 1.12f),
        "ngpc" to ngpc, "ngp" to ngpc,
        "pce" to ConsolePad(Res.drawable.menu_pad_pce, 0.75f), "vb" to ConsolePad(Res.drawable.menu_pad_vb, 2.00f),
        "lynx" to ConsolePad(Res.drawable.menu_pad_lynx, 2.00f), "a2600" to ConsolePad(Res.drawable.menu_pad_a2600, 2.00f),
        "a5200" to ConsolePad(Res.drawable.menu_pad_a5200, 2.00f), "a7800" to ConsolePad(Res.drawable.menu_pad_a7800, 1.12f),
        "pkm" to ConsolePad(Res.drawable.menu_pad_pkm, 1.18f), "wsc" to ConsolePad(Res.drawable.menu_pad_wsc, 2.00f),
        "coleco" to ConsolePad(Res.drawable.menu_pad_coleco, 2.00f), "dc" to ConsolePad(Res.drawable.menu_pad_dc, 0.64f),
        "fbn" to ConsolePad(Res.drawable.menu_pad_fbn, 0.80f), "prboom" to vic20, "p8" to vic20,
        "gc" to ConsolePad(Res.drawable.menu_pad_gc, 1.21f), "wii" to ConsolePad(Res.drawable.menu_pad_wii, 2.00f),
    )
}

/** The Android games console's pad ([ANDROID_TAG], not a registry console): a generic controller, as Android games are played with one. */
internal val androidPad = ConsolePad(Res.drawable.menu_pad_android, 1.50f)

/**
 * [tag]'s console pad (SFC and SUPA share one; so do MD and GPGX), or null for a tag no console claims. The Android
 * games console has its own ([androidPad]), as it has its own logo.
 */
fun consolePad(tag: String): ConsolePad? =
    if (tag == ANDROID_TAG) androidPad else CoreRegistry.consoleOf(tag)?.id?.let(consolePadsById::get)

/** Carousel: the focused console's pad, greyed to [PAD_SATURATION] then at this times its brightness factor. */
internal const val CAROUSEL_PAD_BRIGHTNESS = 0.32f
internal const val PAD_SATURATION = 0.55f
/** List: the pad alone on the right, at this times its factor (full colour), at [LIST_PAD_ALPHA]. */
internal const val LIST_PAD_BRIGHTNESS = 0.55f
internal const val LIST_PAD_ALPHA = 0.35f

/**
 * A Compose ColorMatrix (4×5, row-major, no offsets) that sets [saturation] (CSS `saturate()`, luminance weights
 * 0.213 / 0.715 / 0.072) and then scales R, G and B by [brightness] (CSS `brightness()`); alpha is kept.
 */
internal fun padColorMatrix(brightness: Float, saturation: Float): FloatArray {
    val s = saturation
    val r = floatArrayOf(0.213f + 0.787f * s, 0.715f - 0.715f * s, 0.072f - 0.072f * s)
    val g = floatArrayOf(0.213f - 0.213f * s, 0.715f + 0.285f * s, 0.072f - 0.072f * s)
    val b = floatArrayOf(0.213f - 0.213f * s, 0.715f - 0.715f * s, 0.072f + 0.928f * s)
    fun row(c: FloatArray) = floatArrayOf(c[0] * brightness, c[1] * brightness, c[2] * brightness, 0f, 0f)
    return row(r) + row(g) + row(b) + floatArrayOf(0f, 0f, 0f, 1f, 0f)
}

/** A pad's box, dp: [w] × [h], its centre [dy] below the slot's centre; the image is Fit inside it. */
internal data class PadBox(val w: Float, val h: Float, val dy: Float)

/** How [SlotTile] draws a tile's pad: in [box], at [alpha] (read while drawing). */
internal class PadFrame(val box: PadBox, val alpha: () -> Float)

/** A console's pad box, times its logo slot's height: [PAD_W_K] wide, [PAD_H_K] tall (the row and the stack alike). */
internal const val PAD_W_K = 2.8f
internal const val PAD_H_K = 1.8f

/**
 * Portrait: the selected console (its logo slot, its pad and the row's gaps, all together) is shrunk just
 * enough that its pad is at most this share of the body's width.
 */
internal const val CONSOLE_PAD_MAX_SHARE = 0.6f

/** The factor ([CONSOLE_PAD_MAX_SHARE]) a logo slot [itemH] tall shrinks by on a portrait body [bodyW] wide; never grown. */
internal fun consolePortraitFit(itemH: Float, bodyW: Float): Float =
    if (itemH <= 0f) 1f else min(1f, CONSOLE_PAD_MAX_SHARE * bodyW / (PAD_W_K * itemH))

/** The horizontal Carousel's pad: [PAD_W_K] × [PAD_H_K] the slot's height, centred on the slot (the neighbours do not move). */
internal fun rowPadBox(itemH: Float): PadBox = PadBox(itemH * PAD_W_K, itemH * PAD_H_K, 0f)

/** Only the focused console shows its pad: 0.6 at the selection, gone one step away. */
internal fun padAlpha(distance: Float): Float = 0.6f * max(0f, 1f - abs(distance))

/** Clearance between the selected console's pad (as drawn) and its neighbours in the vertical stack, dp. */
internal const val PAD_CLEAR = 10f

/**
 * The vertical stack's pad: the row's size ([rowPadBox]; the stack's slot is the row's),
 * centred on the logo and its count as one block (below the slot's centre).
 */
internal fun stackPadBox(slotW: Float, slotH: Float, reach: StackReach): PadBox =
    PadBox(slotH * PAD_W_K, slotH * PAD_H_K, (reach.downAt(1f) - reach.upAt(1f)) / 2)

/** [this] reach with its pad drawn Fit in [box] ([padAspect]: height / width; null: half the box tall) plus [PAD_CLEAR] either side. */
internal fun StackReach.withPad(box: PadBox, padAspect: Float?): StackReach {
    val half = logoDrawnHeight(box.w, box.h, padAspect) / 2
    return copy(padUp = half - box.dy + PAD_CLEAR, padDown = half + box.dy + PAD_CLEAR)
}
