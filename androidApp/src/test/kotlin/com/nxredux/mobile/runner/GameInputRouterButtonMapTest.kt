package com.nxredux.mobile.runner

import android.view.KeyEvent
import com.nxredux.mobile.ui.nx.NxButton
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The console's button mapping on the game's physical keys. [GameInputRouter] needs a GLRetroView and its host, so these
 * test the pure pieces it maps with: [mappedKey] for a key event, [axisJobKey] for a trigger axis, and the [SentKeys] a
 * key's release reads back.
 */
class GameInputRouterButtonMapTest {
    @Test fun mappedKeyFollowsMap() {
        val map = ButtonMap.DEFAULT.bind(MapKey.A, MapKey.R2)
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, mappedKey(map, KeyEvent.KEYCODE_BUTTON_R2, physical = true))
        assertEquals(KeyEvent.KEYCODE_BUTTON_R2, mappedKey(map, KeyEvent.KEYCODE_BUTTON_R2, physical = false))
        assertEquals(KeyEvent.KEYCODE_BUTTON_START, mappedKey(map, KeyEvent.KEYCODE_BUTTON_START, physical = true))
    }

    @Test fun triggerAxisFollowsMap() {
        val map = ButtonMap.DEFAULT.bind(MapKey.A, MapKey.L2)
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, axisJobKey(map, NxButton.L2))
        assertEquals(NxButton.A, PadKeymap.buttonFor(axisJobKey(map, NxButton.L2)))
        assertEquals(KeyEvent.KEYCODE_BUTTON_R2, axisJobKey(map, NxButton.R2))
        assertEquals(KeyEvent.KEYCODE_BUTTON_L2, axisJobKey(ButtonMap.DEFAULT, NxButton.L2))
    }

    @Test fun releasesUseRecordedKey() {
        val m1 = ButtonMap.DEFAULT.bind(MapKey.A, MapKey.R2)
        val m2 = ButtonMap.DEFAULT.bind(MapKey.B, MapKey.R2)
        val sent = SentKeys<Pair<Int, Int>>()
        val held = 7 to KeyEvent.KEYCODE_BUTTON_R2   // the raw physical key, as runnerKey records it
        sent.down(held, mappedKey(m1, KeyEvent.KEYCODE_BUTTON_R2, physical = true))
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, sent.up(held, mappedKey(m2, KeyEvent.KEYCODE_BUTTON_R2, physical = true)))
    }

    @Test fun triggerOnStickClickJobDedupesBeforeItsJob() {
        val map = ButtonMap.DEFAULT.bind(MapKey.R3, MapKey.R2)
        val r2 = KeyEvent.KEYCODE_BUTTON_R2
        val trigger = physicalTrigger(r2)
        assertEquals(NxButton.R2, trigger)
        val wiiHome = thumbKeyRoute(mappedKey(map, r2, physical = true), wii = true, n3ds = false)
        assertEquals(ThumbKeyRoute.WII_HOME, wiiHome)
        assertEquals(true, dedupesBeforeThumb(wiiHome, trigger))
        val n3dsSwap = thumbKeyRoute(mappedKey(ButtonMap.DEFAULT.bind(MapKey.L3, MapKey.L2), KeyEvent.KEYCODE_BUTTON_L2, physical = true), wii = false, n3ds = true)
        assertEquals(true, dedupesBeforeThumb(n3dsSwap, physicalTrigger(KeyEvent.KEYCODE_BUTTON_L2)))
        // The key marks the trigger as key-driven, so its axis twin no longer presses (the job runs once).
        val axes = TriggerAxes()
        axes.onKey(NxButton.R2, down = true)
        assertEquals(0, axes.onAxes(0f, 1f))
        // Elsewhere the dedupe stays at its place after the shortcuts.
        assertEquals(false, dedupesBeforeThumb(ThumbKeyRoute.CORE, trigger))
        assertEquals(false, dedupesBeforeThumb(ThumbKeyRoute.WII_HOME, physicalTrigger(KeyEvent.KEYCODE_BUTTON_THUMBR)))
    }
}
