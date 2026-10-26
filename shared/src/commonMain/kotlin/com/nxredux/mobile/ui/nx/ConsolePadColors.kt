package com.nxredux.mobile.ui.nx

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** A face button in its own colours: a [top]-[mid]-[bottom] raised gradient, [pressedTop]-[pressedBottom] when pressed; [mid] is its outline pad tint. */
internal fun coloredFace(top: Color, mid: Color, bottom: Color, pressedTop: Color, pressedBottom: Color) = PadFace(
    raisedFill = Brush.verticalGradient(listOf(top, mid, bottom)),
    raisedRim = Brush.verticalGradient(listOf(Color(0x4DFFFFFF), Color(0x00FFFFFF))),
    pressedFill = Brush.verticalGradient(listOf(pressedTop, pressedBottom)),
    pressedRim = Brush.verticalGradient(listOf(Color(0x80000000), Color(0x00000000))),
    tint = mid,
)

/** The N64 cluster's own colours (blue A, green B, yellow C), in every pad theme; change them here. */
internal object N64PadColors {
    val a = coloredFace(Color(0xFF5C7CFA), Color(0xFF3B5BDB), Color(0xFF2B45B0), Color(0xFF1E3280), Color(0xFF2B45B0))
    val b = coloredFace(Color(0xFF51CF66), Color(0xFF2F9E44), Color(0xFF237A34), Color(0xFF175424), Color(0xFF237A34))
    val c = coloredFace(Color(0xFFFFD43B), Color(0xFFE0A800), Color(0xFFB38600), Color(0xFF7A5C00), Color(0xFFB38600))
    val label = NxDefaults.colors.main  // A and B letters, in the UI ink
    val cLabel = Color(0xFF2B2826)      // C arrows on yellow
}

/**
 * The Dreamcast pad's face colours (soft pastels like the real controller), in every pad theme, named by the letter
 * shown: red A (bottom), blue B (right), yellow X (left), green Y (top).
 */
internal object DreamcastPadColors {
    val a = coloredFace(Color(0xFFF07A70), Color(0xFFE8594F), Color(0xFFC7443B), Color(0xFF8F2E28), Color(0xFFC7443B))
    val b = coloredFace(Color(0xFF6FA8EC), Color(0xFF3D8BE0), Color(0xFF2C6FBD), Color(0xFF1F4E86), Color(0xFF2C6FBD))
    val x = coloredFace(Color(0xFFF7DA7A), Color(0xFFF2C94C), Color(0xFFCFA830), Color(0xFF8C7020), Color(0xFFCFA830))
    val y = coloredFace(Color(0xFF93D89E), Color(0xFF6BC77A), Color(0xFF4FA35D), Color(0xFF346E3E), Color(0xFF4FA35D))
    val label = NxDefaults.colors.main  // A, B and Y letters, in the UI ink
    val xLabel = Color(0xFF2B2826)      // X letter on yellow
}

/** The GameCube pad's own A and B (green A, red B, like the real controller), in every pad theme; X and Y stay on theme. */
internal object GameCubePadColors {
    val a = coloredFace(Color(0xFF6FD08A), Color(0xFF2FA85A), Color(0xFF22833F), Color(0xFF175A2C), Color(0xFF22833F))
    val b = coloredFace(Color(0xFFF07A70), Color(0xFFE8594F), Color(0xFFC7443B), Color(0xFF8F2E28), Color(0xFFC7443B))
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
