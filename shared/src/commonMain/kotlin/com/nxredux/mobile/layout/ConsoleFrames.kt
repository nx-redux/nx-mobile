package com.nxredux.mobile.layout

private val FOUR_THREE = 4f / 3f

/**
 * Each console's usual core frame (its cores' default output at 1x, the aspect they report; 0 for square pixels), by
 * console id: the picture a skin's glass is checked against (SkinPictureAspectTest). DS and 3DS draw two screens and are left out.
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
