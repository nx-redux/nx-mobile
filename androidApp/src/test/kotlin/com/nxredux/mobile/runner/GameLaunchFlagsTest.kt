package com.nxredux.mobile.runner

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class GameLaunchFlagsTest {
    @Test fun launcherModeGivesTheGameItsOwnClearedTask() =
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION, gameLaunchFlags(ownTask = true))

    @Test fun otherwiseTheGameSharesTheMenusTask() = assertEquals(0, gameLaunchFlags(ownTask = false))
}
