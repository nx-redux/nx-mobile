package com.nxredux.mobile.ui.nx

import androidx.compose.ui.graphics.Color

/**
 * Every colour literal of the on-screen pad, by theme and console, in one place. [VirtualPad] and ConsolePadColors.kt
 * build their gradients and faces from these; change a colour here. Gradient stops are named top to bottom.
 */
internal object PadPalettes {
    /** The soft top-lit rim of a coloured or magenta face: white fading out downwards. */
    val softRimTop = Color(0x4DFFFFFF)
    /** White, fully clear: the bottom stop of a rim that fades out. */
    val whiteClear = Color(0x00FFFFFF)
    /** The shadowed rim of a pressed face: black fading out downwards. */
    val pressedRimTop = Color(0x80000000)
    /** Black, fully clear: the bottom stop of a shadow that fades out. */
    val blackClear = Color(0x00000000)

    /** Charcoal (the default theme): a flat mid-dark body, one domed grey for every button role, dark sockets. */
    object Charcoal {
        val surface = Color(0xFF272B30)
        val faceTop = Color(0xFF444B54)
        val faceMid = Color(0xFF2A2F36)
        val faceBottom = Color(0xFF191D22)
        val faceRimTop = Color(0x40FFFFFF)
        val facePressedTop = Color(0xFF141719)
        val facePressedBottom = Color(0xFF23282E)
        val socketFill = Color(0xFF0D0F12)
        val socketRimBottom = Color(0x26FFFFFF)
        val wellTop = Color(0xFF181B20)
        val wellMid = Color(0xFF23272D)
        val wellBottom = Color(0xFF2D3239)
        val wellRimTop = Color(0x4D000000)
        val wellRimBottom = Color(0x22FFFFFF)
        val knobShadowCore = Color(0x66000000)
        val knobShadowMid = Color(0x26000000)
        val label = Color(0xFFB4BAC2)
        val engraveLitLip = Color(0xFF40464E)
        val engraveInnerShadow = Color(0xFF15181B)
        val engraveFloor = Color(0xFF181B1F)
    }

    /** Retro: a warm light-grey body, magenta A / B / X / Y, a near-black d-pad and knob, a warm dark socket. */
    object Retro {
        val surface = Color(0xFFC9C5BE)
        val faceTop = Color(0xFFB0426A)
        val faceMid = Color(0xFF8E2A4E)
        val faceBottom = Color(0xFF6A1C39)
        val facePressedTop = Color(0xFF4E1229)
        val facePressedBottom = Color(0xFF6E2040)
        val dpadTop = Color(0xFF3A3A3E)
        val dpadMid = Color(0xFF1E1E21)
        val dpadBottom = Color(0xFF111113)
        val dpadRimTop = Color(0x33FFFFFF)
        val dpadPressedTop = Color(0xFF08080A)
        val dpadPressedBottom = Color(0xFF18181B)
        val dpadPressedRimTop = Color(0x99000000)
        val socketFill = Color(0xFF5A5550)
        val socketRimTop = Color(0x40000000)
        val socketRimBottom = Color(0xD9FFFFFF)
        val wellTop = Color(0xFFA29E97)
        val wellMid = Color(0xFFB6B2AB)
        val wellBottom = Color(0xFFCFCBC4)
        val wellRimTop = Color(0x40000000)
        val wellRimBottom = Color(0x80FFFFFF)
        val knobShadowCore = Color(0x59000000)
        val knobShadowMid = Color(0x1F000000)
        val label = Color(0xFFEDE9E3)
        val engraveLitLip = Color(0xFFE2DED8)
        val engraveInnerShadow = Color(0xFFA6A29C)
        val engraveFloor = Color(0xFFB8B4AE)
    }

    /** Black (both button sets): a pure black body, Charcoal's caps, a faint well and flat NX mark; Outline adds grey strokes. */
    object Black {
        val surface = Color(0xFF000000)
        val label = Color(0xFFB4BAC2)
        /** The Outline set's stroke: a mid grey, quiet on black but clear of it. */
        val stroke = Color(0xFF5A5F66)
        /** The Outline set's held stroke: brighter, so a press shows without a glow. */
        val strokePressed = Color(0xFF8A9098)
        /** The Outline set's faint white fill while held. */
        val pressedFill = Color(0x1FFFFFFF)
        val wellTop = Color(0xFF000000)
        val wellMid = Color(0xFF0B0C0E)
        val wellBottom = Color(0xFF15171A)
        val engraveFloor = Color(0xFF17191C)
    }

    /** A letter on a yellow face (N64 C arrows, Dreamcast X), dark so it reads. */
    val yellowFaceLabel = Color(0xFF2B2826)

    /** The N64 cluster's own face colours, each: top, mid (also the outline tint), bottom, pressed top, pressed bottom. */
    object N64 {
        val a = FaceStops(0xFF5C7CFA, 0xFF3B5BDB, 0xFF2B45B0, 0xFF1E3280, 0xFF2B45B0)
        val b = FaceStops(0xFF51CF66, 0xFF2F9E44, 0xFF237A34, 0xFF175424, 0xFF237A34)
        val c = FaceStops(0xFFFFD43B, 0xFFE0A800, 0xFFB38600, 0xFF7A5C00, 0xFFB38600)
    }

    /** The Dreamcast pad's soft pastel faces, named by the letter shown. */
    object Dreamcast {
        val a = FaceStops(0xFFF07A70, 0xFFE8594F, 0xFFC7443B, 0xFF8F2E28, 0xFFC7443B)
        val b = FaceStops(0xFF6FA8EC, 0xFF3D8BE0, 0xFF2C6FBD, 0xFF1F4E86, 0xFF2C6FBD)
        val x = FaceStops(0xFFF7DA7A, 0xFFF2C94C, 0xFFCFA830, 0xFF8C7020, 0xFFCFA830)
        val y = FaceStops(0xFF93D89E, 0xFF6BC77A, 0xFF4FA35D, 0xFF346E3E, 0xFF4FA35D)
    }

    /** The GameCube pad's own green A and red B. */
    object GameCube {
        val a = FaceStops(0xFF6FD08A, 0xFF2FA85A, 0xFF22833F, 0xFF175A2C, 0xFF22833F)
        val b = FaceStops(0xFFF07A70, 0xFFE8594F, 0xFFC7443B, 0xFF8F2E28, 0xFFC7443B)
    }

    /** The five colours of a coloured face: raised [top]-[mid]-[bottom], held [pressedTop]-[pressedBottom]. */
    class FaceStops(top: Long, mid: Long, bottom: Long, pressedTop: Long, pressedBottom: Long) {
        val top = Color(top)
        val mid = Color(mid)
        val bottom = Color(bottom)
        val pressedTop = Color(pressedTop)
        val pressedBottom = Color(pressedBottom)
    }
}
