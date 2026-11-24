package com.nxredux.mobile.storage

import com.nxredux.mobile.cheats.db.CheatDbManifest
import com.nxredux.mobile.storage.StorageLayout.Kind.CHEATS
import com.nxredux.mobile.storage.StorageLayout.Kind.MOVE
import com.nxredux.mobile.storage.StorageLayout.Kind.STAY
import kotlin.test.Test
import kotlin.test.assertEquals

class StorageLayoutTest {
    private fun kind(rel: String) = StorageLayout.kind(rel)

    @Test fun savesAndSharedMove() {
        assertEquals(MOVE, kind("Saves/GBA/Pokemon.srm")); assertEquals(MOVE, kind(".userdata/shared/MGBA-mgba/Pokemon.st0"))
        assertEquals(MOVE, kind(".userdata/shared/game_time.db")); assertEquals(MOVE, kind(".userdata/android/hidden.txt"))
        assertEquals(MOVE, kind(".userdata/android/badges.txt"))
        assertEquals(MOVE, kind(".userdata/android/GBA-mgba/minarch.cfg"))
    }

    @Test fun cheatsKeepTheirOwnRule() {
        assertEquals(CHEATS, kind("Cheats/GBA/x.cht")); assertEquals(CHEATS, kind(CheatDbManifest.PATH))
    }

    @Test fun cachesAndCredentialsStay() {
        listOf(".userdata/android/ra/cache/login.json", ".userdata/android/library.json", ".userdata/android/sidecars/a.json",
            ".userdata/android/toolpins.txt", "Overlays/GB/x.png", "Shaders/crt.glsl").forEach { assertEquals(STAY, kind(it)) }
    }
}
