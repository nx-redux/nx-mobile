package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.ui.Alignment
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where the in-game notifications sit. Over the landscape overlay pad the top corners hold its system row
 * (STICK / MENU left, SELECT / START right) and the bottom-left its d-pad, so the pill and the toasts go top-centre, the
 * toasts under the pill, in portrait over a pad too; anywhere else (tabletop, a controller attached) the corners.
 */
class NotificationPlacementTest {
    @Test fun overTheLandscapeOverlayPadEverythingIsTopCentre() {
        val p = nxNotificationPlacement(overlayPadShown = true)
        assertEquals(NxNotificationPlacement.TOP_CENTRE, p)
        assertEquals(Alignment.TopCenter to Alignment.TopCenter, p.pill to p.toasts)
    }

    @Test fun withoutItThePillIsTopLeftAndTheToastsBottomLeft() {
        val p = nxNotificationPlacement(overlayPadShown = false)
        assertEquals(NxNotificationPlacement.CORNERS, p)
        assertEquals(Alignment.TopStart to Alignment.BottomStart, p.pill to p.toasts)
    }

    // In portrait over a pad the toasts go top-centre with the pill, not down on a DS / 3DS touch screen.
    @Test fun inPortraitOverAPadEverythingIsTopCentre() {
        assertEquals(NxNotificationPlacement.TOP_CENTRE, nxNotificationPlacement(overlayPadShown = true, onPicture = true))
        assertEquals(NxNotificationPlacement.TOP_CENTRE, nxNotificationPlacement(overlayPadShown = false, onPicture = true))
    }

    // A cutout on one side only would pad the column off centre, into the far row ([nxTopCentreMaxWidth] already keeps
    // both rows' symmetric inset): top-centre keeps only the vertical safe sides, so it stays centred on the screen.
    @Test fun topCentreIsNotInsetSideways() {
        assertEquals(WindowInsetsSides.Vertical, nxNotificationInsetSides(NxNotificationPlacement.TOP_CENTRE))
        assertEquals(WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical, nxNotificationInsetSides(NxNotificationPlacement.CORNERS))
    }
}

/**
 * The top-centre notifications keep between the overlay clusters' system rows (MENU / STICK, SELECT /
 * START): the screen width less each row's reach from its edge (cutout inset, cluster padding, nudge, two pills, gap).
 */
class TopCentreWidthTest {
    @Test fun aPhoneInLandscapeLeavesTheMiddle() {
        // unit (360 − 32) / 6 ≈ 54.67; each row reaches 16 + 3.0 · 54.67 + 12 ≈ 192 dp.
        assertEquals(416f, nxTopCentreMaxWidth(800f, 360f, inset = 0f), 0.1f)
    }

    @Test fun theFlipCoverLeavesLittle() {
        // The cover's clusters fit half its width: unit (237 − 32) / 4.5 ≈ 45.56; each row ≈ 164.7 dp.
        assertEquals(144.7f, nxTopCentreMaxWidth(474f, 400f, inset = 0f), 0.1f)
    }

    @Test fun aCutoutPushesBothRowsIn() {
        assertEquals(356f, nxTopCentreMaxWidth(800f, 360f, inset = 30f), 0.1f)
    }
}
