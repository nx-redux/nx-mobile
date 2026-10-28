package com.nxredux.mobile.ui.nx

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.nxredux.mobile.settings.PadButtons
import com.nxredux.mobile.settings.PadTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins every pad colour (themes x parts, console faces) to the literal it had before the palettes moved into one file. */
class PadPalettesTest {
    private fun c(argb: Long) = Color(argb)
    private fun v(vararg argb: Long): Brush = Brush.verticalGradient(argb.map { Color(it) })
    private fun solid(argb: Long): Brush = SolidColor(Color(argb))
    private fun face(raised: Brush, raisedRim: Brush, pressed: Brush, pressedRim: Brush, tint: Color? = null) =
        PadFace(raised, raisedRim, pressed, pressedRim, tint)

    private fun assertFace(label: String, want: PadFace, got: PadFace) {
        assertEquals(want.raisedFill, got.raisedFill, "$label raisedFill")
        assertEquals(want.raisedRim, got.raisedRim, "$label raisedRim")
        assertEquals(want.pressedFill, got.pressedFill, "$label pressedFill")
        assertEquals(want.pressedRim, got.pressedRim, "$label pressedRim")
        assertEquals(want.tint, got.tint, "$label tint")
    }

    private fun assertColors(label: String, want: PadColors, got: PadColors) {
        assertEquals(want.surface, got.surface, "$label surface")
        assertFace("$label face", want.face, got.face)
        assertFace("$label dpad", want.dpad, got.dpad)
        assertFace("$label pill", want.pill, got.pill)
        assertEquals(want.socketFill, got.socketFill, "$label socketFill")
        assertEquals(want.socketRim, got.socketRim, "$label socketRim")
        assertEquals(want.wellFill, got.wellFill, "$label wellFill")
        assertEquals(want.wellRim, got.wellRim, "$label wellRim")
        assertEquals(want.knobShadow, got.knobShadow, "$label knobShadow")
        assertEquals(want.label, got.label, "$label label")
        assertEquals(want.engraveLitLip, got.engraveLitLip, "$label engraveLitLip")
        assertEquals(want.engraveInnerShadow, got.engraveInnerShadow, "$label engraveInnerShadow")
        assertEquals(want.engraveFloor, got.engraveFloor, "$label engraveFloor")
        assertEquals(want.outline, got.outline, "$label outline")
    }

    private val charcoalFace = face(
        v(0xFF444B54, 0xFF2A2F36, 0xFF191D22), v(0x40FFFFFF, 0x00FFFFFF),
        v(0xFF141719, 0xFF23282E), v(0x80000000, 0x00000000),
    )

    private fun outline(stroke: Long, pressedFill: Long, pressedRim: Long) = face(
        SolidColor(Color.Transparent), solid(stroke), solid(pressedFill), solid(pressedRim),
    )

    private val charcoal = PadColors(
        surface = c(0xFF272B30), face = charcoalFace, dpad = charcoalFace, pill = charcoalFace,
        socketFill = c(0xFF0D0F12), socketRim = v(0x00FFFFFF, 0x26FFFFFF),
        wellFill = v(0xFF181B20, 0xFF23272D, 0xFF2D3239), wellRim = v(0x4D000000, 0x22FFFFFF),
        knobShadow = Brush.radialGradient(0f to c(0x66000000), 0.8f to c(0x26000000), 1f to c(0x00000000)),
        label = c(0xFFB4BAC2), engraveLitLip = c(0xFF40464E), engraveInnerShadow = c(0xFF15181B), engraveFloor = c(0xFF181B1F),
    )

    private val retro = PadColors(
        surface = c(0xFFC9C5BE),
        face = face(
            v(0xFFB0426A, 0xFF8E2A4E, 0xFF6A1C39), v(0x4DFFFFFF, 0x00FFFFFF),
            v(0xFF4E1229, 0xFF6E2040), v(0x80000000, 0x00000000),
        ),
        dpad = face(
            v(0xFF3A3A3E, 0xFF1E1E21, 0xFF111113), v(0x33FFFFFF, 0x00FFFFFF),
            v(0xFF08080A, 0xFF18181B), v(0x99000000, 0x00000000),
        ),
        pill = charcoalFace,
        socketFill = c(0xFF5A5550), socketRim = v(0x40000000, 0xD9FFFFFF),
        wellFill = v(0xFFA29E97, 0xFFB6B2AB, 0xFFCFCBC4), wellRim = v(0x40000000, 0x80FFFFFF),
        knobShadow = Brush.radialGradient(0f to c(0x59000000), 0.8f to c(0x1F000000), 1f to c(0x00000000)),
        label = c(0xFFEDE9E3), engraveLitLip = c(0xFFE2DED8), engraveInnerShadow = c(0xFFA6A29C), engraveFloor = c(0xFFB8B4AE),
    )

    private val blackGrey = PadColors(
        surface = c(0xFF000000), face = charcoalFace, dpad = charcoalFace, pill = charcoalFace,
        socketFill = c(0xFF000000), socketRim = charcoal.socketRim,
        wellFill = v(0xFF000000, 0xFF0B0C0E, 0xFF15171A), wellRim = charcoal.wellRim, knobShadow = charcoal.knobShadow,
        label = c(0xFFB4BAC2), engraveLitLip = Color.Transparent, engraveInnerShadow = Color.Transparent, engraveFloor = c(0xFF17191C),
    )

    private val blackOutline = PadColors(
        surface = c(0xFF000000),
        face = outline(0xFF5A5F66, 0x1FFFFFFF, 0xFF8A9098),
        dpad = face(SolidColor(Color.Transparent), solid(0xFF5A5F66), solid(0x1FFFFFFF), SolidColor(Color.Transparent)),
        pill = outline(0xFF5A5F66, 0x1FFFFFFF, 0xFF8A9098),
        socketFill = Color.Transparent, socketRim = SolidColor(Color.Transparent), wellFill = SolidColor(Color.Transparent),
        wellRim = solid(0xFF5A5F66), knobShadow = SolidColor(Color.Transparent),
        label = c(0xFFB4BAC2), engraveLitLip = Color.Transparent, engraveInnerShadow = Color.Transparent, engraveFloor = c(0xFF17191C),
        outline = true,
    )

    private fun colored(top: Long, mid: Long, bottom: Long, pTop: Long, pBottom: Long) = face(
        v(top, mid, bottom), v(0x4DFFFFFF, 0x00FFFFFF), v(pTop, pBottom), v(0x80000000, 0x00000000), c(mid),
    )

    @Test fun every_pad_colour_is_unchanged() {
        assertColors("charcoal", charcoal, padColorsFor(PadTheme.CHARCOAL))
        assertColors("retro", retro, padColorsFor(PadTheme.RETRO))
        assertColors("black grey", blackGrey, padColorsFor(PadTheme.BLACK, PadButtons.GREY))
        assertColors("black outline", blackOutline, padColorsFor(PadTheme.BLACK, PadButtons.OUTLINE))
        // Charcoal and Retro ignore the button set.
        for (b in PadButtons.entries) {
            assertColors("charcoal $b", charcoal, padColorsFor(PadTheme.CHARCOAL, b))
            assertColors("retro $b", retro, padColorsFor(PadTheme.RETRO, b))
        }

        assertFace("coloredFace", colored(1, 2, 3, 4, 5), coloredFace(c(1), c(2), c(3), c(4), c(5)))

        assertFace("n64 a", colored(0xFF5C7CFA, 0xFF3B5BDB, 0xFF2B45B0, 0xFF1E3280, 0xFF2B45B0), N64PadColors.a)
        assertFace("n64 b", colored(0xFF51CF66, 0xFF2F9E44, 0xFF237A34, 0xFF175424, 0xFF237A34), N64PadColors.b)
        assertFace("n64 c", colored(0xFFFFD43B, 0xFFE0A800, 0xFFB38600, 0xFF7A5C00, 0xFFB38600), N64PadColors.c)
        assertEquals(NxDefaults.colors.main, N64PadColors.label)
        assertEquals(c(0xFF2B2826), N64PadColors.cLabel)

        assertFace("dc a", colored(0xFFF07A70, 0xFFE8594F, 0xFFC7443B, 0xFF8F2E28, 0xFFC7443B), DreamcastPadColors.a)
        assertFace("dc b", colored(0xFF6FA8EC, 0xFF3D8BE0, 0xFF2C6FBD, 0xFF1F4E86, 0xFF2C6FBD), DreamcastPadColors.b)
        assertFace("dc x", colored(0xFFF7DA7A, 0xFFF2C94C, 0xFFCFA830, 0xFF8C7020, 0xFFCFA830), DreamcastPadColors.x)
        assertFace("dc y", colored(0xFF93D89E, 0xFF6BC77A, 0xFF4FA35D, 0xFF346E3E, 0xFF4FA35D), DreamcastPadColors.y)
        assertEquals(NxDefaults.colors.main, DreamcastPadColors.label)
        assertEquals(c(0xFF2B2826), DreamcastPadColors.xLabel)

        assertFace("gc a", colored(0xFF6FD08A, 0xFF2FA85A, 0xFF22833F, 0xFF175A2C, 0xFF22833F), GameCubePadColors.a)
        assertFace("gc b", colored(0xFFF07A70, 0xFFE8594F, 0xFFC7443B, 0xFF8F2E28, 0xFFC7443B), GameCubePadColors.b)
        assertEquals(NxDefaults.colors.main, GameCubePadColors.label)
    }
}
