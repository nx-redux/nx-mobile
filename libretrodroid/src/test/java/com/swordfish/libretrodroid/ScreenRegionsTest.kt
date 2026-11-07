package com.swordfish.libretrodroid

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenRegionsTest {
    private val full = ScreenRect(0f, 0f, 1f, 1f)
    private val topSrc = ScreenRect(0f, 0f, 1f, 0.5f)
    private val bottomSrc = ScreenRect(0f, 0.5f, 1f, 1f)
    // Portrait PiP with the bottom screen big over the view's top 60%, the top screen inset in its bottom-right corner.
    private val big = ScreenRegion(bottomSrc, ScreenRect(0f, 0f, 1f, 0.6f), shaded = true, touch = true)
    private val inset = ScreenRegion(topSrc, ScreenRect(0.6f, 0.35f, 0.95f, 0.55f), shaded = false, touch = false)

    @Test fun a_touch_on_a_touch_region_maps_into_its_part_of_the_frame() {
        val p = ScreenRegions.mapTouch(listOf(big, inset), full, 0.25f, 0.3f)!!
        assertEquals(0.25f, p.x, 1e-6f)
        assertEquals(0.75f, p.y, 1e-6f)   // halfway down the big screen = halfway down the frame's bottom half
    }

    @Test fun the_inset_over_a_touch_region_swallows_the_tap() {
        assertNull(ScreenRegions.mapTouch(listOf(big, inset), full, 0.7f, 0.4f))
    }

    @Test fun outside_every_region_and_on_a_non_touch_region_is_nothing() {
        assertNull(ScreenRegions.mapTouch(listOf(big, inset), full, 0.5f, 0.9f))
        val stacked = listOf(
            ScreenRegion(topSrc, ScreenRect(0f, 0f, 1f, 0.5f), shaded = true, touch = false),
            ScreenRegion(bottomSrc, ScreenRect(0f, 0.5f, 1f, 1f), shaded = true, touch = true),
        )
        assertNull(ScreenRegions.mapTouch(stacked, full, 0.5f, 0.25f))
        assertEquals(0.75f, ScreenRegions.mapTouch(stacked, full, 0.5f, 0.75f)!!.y, 1e-6f)
    }

    @Test fun dst_is_relative_to_the_viewport() {
        val right = ScreenRect(0.5f, 0f, 1f, 1f)
        val whole = listOf(ScreenRegion(bottomSrc, full, shaded = true, touch = true))
        val p = ScreenRegions.mapTouch(whole, right, 0.75f, 0.5f)!!
        assertEquals(0.5f, p.x, 1e-6f)
        assertEquals(0.75f, p.y, 1e-6f)
        assertNull(ScreenRegions.mapTouch(whole, right, 0.25f, 0.5f))
    }

    // The second display's touches: dst is 0..1 of the whole second surface (GLRetroView.mapSecondaryTouch).
    @Test fun whole_surface_viewport_maps_bottom_half() {
        val bottom = listOf(ScreenRegion(ScreenRect(0f, 0.5f, 1f, 1f), full, shaded = true, touch = true))
        assertEquals(FramePoint(0.5f, 0.75f), ScreenRegions.mapTouch(bottom, ScreenRect(0f, 0f, 1f, 1f), 0.5f, 0.5f))
    }

    @Test fun no_regions_or_an_empty_viewport_map_nothing() {
        assertNull(ScreenRegions.mapTouch(emptyList(), full, 0.5f, 0.5f))
        assertNull(ScreenRegions.mapTouch(listOf(big), ScreenRect(0.5f, 0.5f, 0.5f, 0.5f), 0.5f, 0.5f))
    }

    @Test fun packs_eleven_floats_per_region_in_order() {
        assertArrayEquals(
            floatArrayOf(
                0f, 0.5f, 1f, 1f, 0f, 0f, 1f, 0.6f, 1f, 1f, 1f,
                0f, 0f, 1f, 0.5f, 0.6f, 0.35f, 0.95f, 0.55f, 0f, 0f, 1f,
            ),
            ScreenRegions.pack(listOf(big, inset)),
            0f,
        )
        assertEquals(0, ScreenRegions.pack(emptyList()).size)
    }

    @Test fun pack_appends_alpha_last() {
        val packed = ScreenRegions.pack(listOf(big, inset.copy(alpha = 0.5f)))
        assertEquals(22, packed.size)
        assertEquals(1f, packed[10], 0f)    // the default
        assertEquals(0.5f, packed[21], 0f)
    }

    // A 3DS Stacked composition (400x480 frame) built from a packed frame whose bottom view sits at (40,240,320,240).
    private val composition = FrameComposition(
        400, 480,
        listOf(
            ScreenRegion(ScreenRect(0f, 0f, 1f, 0.5f), ScreenRect(0f, 0f, 1f, 0.5f), shaded = false, touch = false),
            ScreenRegion(ScreenRect(0.1f, 0.5f, 0.9f, 1f), ScreenRect(0.1f, 0.5f, 0.9f, 1f), shaded = false, touch = true),
        ),
    )

    @Test fun a_touch_on_the_composed_bottom_screen_maps_to_the_packed_bottom_view() {
        val p = ScreenRegions.mapCompositionTouch(composition, FramePoint(0.5f, 0.75f))!!
        assertEquals(0.5f, p.x, 1e-6f)
        assertEquals(0.75f, p.y, 1e-6f)
    }

    @Test fun a_touch_on_a_composed_band_lands_nowhere() {
        assertNull(ScreenRegions.mapCompositionTouch(composition, FramePoint(0.05f, 0.75f)))
    }

    @Test fun a_moved_packed_view_moves_the_mapped_point() {
        val moved = composition.copy(parts = listOf(
            ScreenRegion(ScreenRect(0f, 0.6f, 0.8f, 1f), ScreenRect(0.1f, 0.5f, 0.9f, 1f), shaded = false, touch = true),
        ))
        val p = ScreenRegions.mapCompositionTouch(moved, FramePoint(0.5f, 0.75f))!!
        assertEquals(0.4f, p.x, 1e-6f)
        assertEquals(0.8f, p.y, 1e-6f)
    }

    @Test fun a_second_display_touch_on_a_composed_frame_maps_to_the_packed_bottom_view() {
        val sideBySide = FrameComposition(720, 240, listOf(
            ScreenRegion(ScreenRect(0f, 0f, 1f, 0.5f), ScreenRect(0f, 0f, 400f / 720f, 1f), shaded = true, touch = false),
            ScreenRegion(ScreenRect(0.1f, 0.5f, 0.9f, 1f), ScreenRect(400f / 720f, 0f, 1f, 1f), shaded = false, touch = true),
        ))
        val whole = listOf(ScreenRegion(ScreenRect(0f, 0f, 1f, 1f), ScreenRect(0f, 0f, 1f, 1f), shaded = true, touch = true))
        val p = ScreenRegions.mapSecondTouch(whole, sideBySide, 0.8f, 0.5f)!!
        assertEquals(0.1f + (0.8f - 400f / 720f) / (320f / 720f) * 0.8f, p.x, 1e-5f)
        assertEquals(0.75f, p.y, 1e-6f)
        assertNull(ScreenRegions.mapSecondTouch(whole, sideBySide, 0.2f, 0.5f))
    }

    @Test fun a_second_display_touch_without_a_composition_maps_through_its_regions_only() {
        val whole = listOf(ScreenRegion(ScreenRect(0f, 0f, 1f, 1f), ScreenRect(0f, 0f, 1f, 1f), shaded = true, touch = true))
        assertEquals(FramePoint(0.3f, 0.6f), ScreenRegions.mapSecondTouch(whole, null, 0.3f, 0.6f))
    }
}
