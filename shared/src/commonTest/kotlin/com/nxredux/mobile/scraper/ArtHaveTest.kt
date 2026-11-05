package com.nxredux.mobile.scraper

import com.nxredux.mobile.library.ArtRefs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Has art: a box (boxart or a 2D box, a grid among them on disk) and a screenshot (a hero among them on disk). */
class ArtHaveTest {
    @Test fun boxAndScreenshotIsComplete() = assertTrue(ArtHave(box = true, screenshot = true).complete)

    @Test fun gridIsNotABox() {
        val shotOnly = ArtHave.of(ArtRefs(screenshot = "s"))
        assertFalse(shotOnly.box)
        assertFalse(shotOnly.complete)
        // A grid is written as the 2D box: that is the box.
        assertTrue(ArtHave.of(ArtRefs(box2d = "b", screenshot = "s")).complete)
    }

    @Test fun heroShotIsNoMixInput() {
        assertFalse(ArtHave.of(ArtRefs(screenshot = "s", heroShot = true)).mixInput)
        assertTrue(ArtHave.of(ArtRefs(screenshot = "s")).mixInput)
        // A hero still counts as the screenshot for has-art.
        assertTrue(ArtHave.of(ArtRefs(box2d = "b", screenshot = "s", heroShot = true)).complete)
    }

    @Test fun screenshotAloneIsNotComplete() {
        assertFalse(ArtHave(screenshot = true).complete)
        assertFalse(ArtHave.of(ArtRefs(screenshot = "s.png", heroShot = true)).complete)
    }

    @Test fun boxAloneIsNotComplete() {
        assertFalse(ArtHave(box = true).complete)
        assertFalse(ArtHave.of(ArtRefs(boxart = "b.png", box2d = "c.jpg")).complete)
    }

    @Test fun withGenerateMixAGameNeedsItsMix() {
        val art = ArtRefs(boxart = "b.png", screenshot = "s.png")
        assertTrue(ArtHave.of(art).complete)
        assertFalse(ArtHave.of(art, needMix = true).complete)
        assertTrue(ArtHave.of(art.copy(mix = "m.png"), needMix = true).complete)
        assertFalse(ArtHave.of(ArtRefs(mix = "m.png"), needMix = true).complete)   // a mix alone is not art
    }

    @Test fun box2dAndWheelNeverCount() {
        val have = ArtHave.of(ArtRefs(box2d = "c.png", wheel = "w.png"))
        assertTrue(have.box2d && have.wheel && have.box)
        assertFalse(ArtHave.of(ArtRefs(wheel = "w.png", screenshot = "s.png")).complete)
    }
}
