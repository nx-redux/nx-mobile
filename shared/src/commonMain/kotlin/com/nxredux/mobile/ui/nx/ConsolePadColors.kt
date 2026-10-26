package com.nxredux.mobile.ui.nx

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** A face button in its own colours: a [top]-[mid]-[bottom] raised gradient, [pressedTop]-[pressedBottom] when pressed; [mid] is its outline pad tint. */
internal fun coloredFace(top: Color, mid: Color, bottom: Color, pressedTop: Color, pressedBottom: Color) = PadFace(
    raisedFill = Brush.verticalGradient(listOf(top, mid, bottom)),
    raisedRim = Brush.verticalGradient(listOf(PadPalettes.softRimTop, PadPalettes.whiteClear)),
    pressedFill = Brush.verticalGradient(listOf(pressedTop, pressedBottom)),
    pressedRim = Brush.verticalGradient(listOf(PadPalettes.pressedRimTop, PadPalettes.blackClear)),
    tint = mid,
)

/** A [PadPalettes.FaceStops] as a [coloredFace]. */
internal fun coloredFace(s: PadPalettes.FaceStops) = coloredFace(s.top, s.mid, s.bottom, s.pressedTop, s.pressedBottom)

/** The N64 cluster's own colours (blue A, green B, yellow C), in every pad theme; change them here. */
internal object N64PadColors {
    val a = coloredFace(PadPalettes.N64.a)
    val b = coloredFace(PadPalettes.N64.b)
    val c = coloredFace(PadPalettes.N64.c)
    val label = NxDefaults.colors.main  // A and B letters, in the UI ink
    val cLabel = PadPalettes.yellowFaceLabel      // C arrows on yellow
}

/**
 * The Dreamcast pad's face colours (soft pastels like the real controller), in every pad theme, named by the letter
 * shown: red A (bottom), blue B (right), yellow X (left), green Y (top).
 */
internal object DreamcastPadColors {
    val a = coloredFace(PadPalettes.Dreamcast.a)
    val b = coloredFace(PadPalettes.Dreamcast.b)
    val x = coloredFace(PadPalettes.Dreamcast.x)
    val y = coloredFace(PadPalettes.Dreamcast.y)
    val label = NxDefaults.colors.main  // A, B and Y letters, in the UI ink
    val xLabel = PadPalettes.yellowFaceLabel      // X letter on yellow
}

/** The GameCube pad's own A and B (green A, red B, like the real controller), in every pad theme; X and Y stay on theme. */
internal object GameCubePadColors {
    val a = coloredFace(PadPalettes.GameCube.a)
    val b = coloredFace(PadPalettes.GameCube.b)
    val label = NxDefaults.colors.main  // A and B letters, in the UI ink
}

/**
 * The face colour and letter colour of [button] on this profile's diamond, or null to use the pad theme's. The
 * GameCube has its own A and B ([GameCubePadColors]); so has the Dreamcast (by position: bottom B is the red "A", right A the blue "B", left Y the yellow "X", top X the
 * green "Y"); the N64 cluster uses [N64PadColors] directly.
 */
internal fun PadProfile.faceColors(button: NxButton): Pair<PadFace, Color>? =
    if (kind == PadProfile.Kind.GC) when (button) {
        NxButton.A -> GameCubePadColors.a to GameCubePadColors.label
        NxButton.B -> GameCubePadColors.b to GameCubePadColors.label
        else -> null
    } else if (kind != PadProfile.Kind.DREAMCAST) null else when (button) {
        NxButton.B -> DreamcastPadColors.a to DreamcastPadColors.label
        NxButton.A -> DreamcastPadColors.b to DreamcastPadColors.label
        NxButton.Y -> DreamcastPadColors.x to DreamcastPadColors.xLabel
        NxButton.X -> DreamcastPadColors.y to DreamcastPadColors.label
        else -> null
    }
