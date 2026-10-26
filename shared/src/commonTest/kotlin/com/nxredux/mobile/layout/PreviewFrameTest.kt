package com.nxredux.mobile.layout

import com.nxredux.mobile.options.NdsSettings
import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.NdsLayout
import com.nxredux.mobile.runner.shownRect
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Pad layout editor previews the landscape pad around each console's own picture, placed as the game places it. */
class PreviewFrameTest {
    // A regular phone's landscape space beside the pad, and a Fold inner screen's (stubby: Large screen for DS / 3DS).
    private val phone = PxRect(300f, 0f, 1800f, 1080f)
    private val fold = PxRect(302f, 0f, 1845f, 1848f)

    @Test fun every_native_and_fill_console_has_its_own_frame() {
        for (c in CoreRegistry.consoles) {
            val scale = consoleScale(c.tags.first())
            if (scale == ConsoleScale.NATIVE || scale == ConsoleScale.FILL) assertTrue(c.id in CONSOLE_FRAMES, c.id)
        }
    }

    @Test fun handhelds_show_their_native_screen_at_a_whole_number_scale() {
        for ((tag, w, h) in listOf(Triple("GBA", 240f, 160f), Triple("GB", 160f, 144f), Triple("GBC", 160f, 144f), Triple("WSC", 224f, 144f))) {
            val p = previewPicture(tag, phone, ScreenClass.REGULAR_PHONE)
            val k = p.width / w
            assertEquals(floor(k), k, tag)
            assertEquals(k * h, p.height, 0.01f, tag)
        }
        // GBA on the phone: 6x (1440 x 960) in the 1800 x 1080 space.
        assertEquals(PxRect(480f, 60f, 1440f, 960f), previewPicture("GBA", phone, ScreenClass.REGULAR_PHONE))
    }

    @Test fun home_consoles_take_a_whole_multiple_of_their_lines_at_4_3() {
        for ((tag, lines) in listOf("SFC" to 224f, "MD" to 224f, "FC" to 240f, "SMS" to 192f)) {
            val p = previewPicture(tag, phone, ScreenClass.REGULAR_PHONE)
            val k = p.height / lines
            assertEquals(floor(k), k, tag)
            assertEquals(p.height * 4f / 3f, p.width, 0.01f, tag)
        }
    }

    @Test fun three_d_consoles_fill_at_their_shape() {
        val n64 = previewPicture("N64", phone, ScreenClass.REGULAR_PHONE)
        assertEquals(1080f, n64.height)
        assertEquals(1440f, n64.width)
        val psp = previewPicture("PSP", phone, ScreenClass.REGULAR_PHONE)
        assertEquals(1800f, psp.width)
        assertEquals(480f / 272f, psp.width / psp.height, 1e-3f)
    }

    @Test fun ds_screens_sit_as_their_default_landscape_layout_places_them() {
        for ((space, screen) in listOf(phone to ScreenClass.REGULAR_PHONE, fold to ScreenClass.STUBBY_PHONE)) {
            val s = NdsSettings()
            val regions = NdsLayout.regions(space, s.layoutShown(true, false, screen), true, screen, s, 0f)
            assertEquals(regions.shownRect(), previewPicture("NDS", space, screen), "$screen")
        }
        // The phone: Side by side, two 256 x 192 screens at 3x (1536 x 576).
        val sideBySide = previewPicture("NDS", phone, ScreenClass.REGULAR_PHONE)
        assertEquals(2f * 256f * 3f, sideBySide.width)
        assertEquals(192f * 3f, sideBySide.height)
    }

    @Test fun the_3ds_frame_follows_the_screen_default_landscape_layout() {
        assertEquals(CoreFrame(720f, 240f, 0f, false), previewFrame("3DS", ScreenClass.REGULAR_PHONE))   // Side by side
        assertEquals(CoreFrame(400f, 360f, 0f, false), previewFrame("3DS", ScreenClass.STUBBY_PHONE))    // Large screen
        assertEquals(CoreFrame(400f, 360f, 0f, false), previewFrame("3DS", ScreenClass.WIDE_TABLET))
        val p = previewPicture("3DS", phone, ScreenClass.REGULAR_PHONE)
        assertEquals(3f, p.width / p.height, 1e-3f)
    }

    @Test fun an_unknown_tag_and_the_shared_layout_take_the_typical_224_line_picture() {
        assertEquals(TYPICAL_FRAME, previewFrame("", ScreenClass.REGULAR_PHONE))
        assertEquals(TYPICAL_FRAME, previewFrame("NOPE", ScreenClass.REGULAR_PHONE))
        val p = previewPicture("", phone, ScreenClass.REGULAR_PHONE)
        assertEquals(224f * 4f, p.height)
    }
}
