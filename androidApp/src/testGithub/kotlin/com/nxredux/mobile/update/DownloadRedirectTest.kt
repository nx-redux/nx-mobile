package com.nxredux.mobile.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class DownloadRedirectTest {
    private val from = "https://github.com/nx-redux/nx-mobile/releases/download/v0.3.0/NXReduxMobile-v0.3.0.apk"

    @Test fun githubsAssetRedirectIsFollowed() {
        val to = "https://release-assets.githubusercontent.com/github-production-release-asset/1/2?sp=r&sig=x"
        assertEquals(to, redirectTarget(from, to, local = false))
        val old = "https://objects.githubusercontent.com/github-production-release-asset-2e65be/1/2?X-Amz-Signature=x"
        assertEquals(old, redirectTarget(from, old, local = false))
    }

    @Test fun aRelativeRedirectStaysOnItsHost() =
        assertEquals("https://github.com/nx-redux/nx-mobile/releases/download/v0.3.1/a.apk", redirectTarget(from, "/nx-redux/nx-mobile/releases/download/v0.3.1/a.apk", local = false))

    @Test fun aRedirectToAnotherHostOrSchemeIsRefused() {
        for (to in listOf("https://example.com/x.apk", "http://objects.githubusercontent.com/x.apk", "http://localhost:8000/x.apk", "file:///sdcard/x.apk")) {
            assertThrows(to, IOException::class.java) { redirectTarget(from, to, local = false) }
        }
    }

    @Test fun aRedirectWithoutALocationIsRefused() {
        assertThrows(IOException::class.java) { redirectTarget(from, null, local = false) }
        assertThrows(IOException::class.java) { redirectTarget(from, "", local = false) }
    }

    @Test fun aLocalFeedMayRedirectOnLoopback() =
        assertEquals("http://localhost:8000/b.apk", redirectTarget("http://localhost:8000/a.apk", "/b.apk", local = true))
}
