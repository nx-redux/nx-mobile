package com.nxredux.mobile.netplay

import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CableLinkOpenRomTest {
    private fun refusedText(open: () -> java.io.InputStream?): String =
        assertThrows(CableLinkRefused::class.java) { AndroidCableLink.openRom("game.gba", open) }.text

    @Test fun aMissingFileSaysItCannotBeOpened() {
        assertEquals(NetplayText.CABLE_LINK_OPEN_FAILED, refusedText { throw FileNotFoundException("gone") })
    }

    @Test fun aRevokedDocumentSaysItCannotBeOpened() {
        assertEquals(NetplayText.CABLE_LINK_OPEN_FAILED, refusedText { throw SecurityException("no grant") })
    }

    @Test fun noStreamSaysItCannotBeOpened() {
        assertEquals(NetplayText.CABLE_LINK_OPEN_FAILED, refusedText { null })
    }

    @Test fun aReadableFileOpens() {
        assertEquals(3, AndroidCableLink.openRom("game.gba") { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }.readBytes().size)
    }
}
