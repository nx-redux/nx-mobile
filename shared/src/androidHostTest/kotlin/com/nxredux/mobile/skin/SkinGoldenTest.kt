package com.nxredux.mobile.skin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull

class SkinGoldenTest {
    private val pack = assertNotNull(SkinPackManifest.parse(File("../androidApp/src/main/assets/skins/nx-carbon/skin.json").readText()))

    // The legacy fixtures (skin-golden): each console as its rules placed it, recorded once while it still had them, for
    // the bake tests. SKIN_GOLDEN_RECORD=1 writes the missing ones.
    @Test
    fun legacyFixturesAreRecorded() {
        val record = System.getenv("SKIN_GOLDEN_RECORD") == "1"
        for ((v, file) in BOXED) {
            if (record) { if (!goldenExists(file)) recordGolden(file, pack, v) else recordMissing(file, pack, v, fromBoxes = v == SkinVariant.N64); continue }
            kotlin.test.assertTrue(goldenExists(file), "$file: no legacy fixture")
        }
    }

    // The pack's boxes place as recorded (skin-pack) on every device class: a change to the boxes, the box solver or the
    // layout choice shows here. SKIN_PACK_RECORD=1 re-records them from the pack (after a deliberate change).
    @Test
    fun thePackPlacesAsRecorded() {
        val record = System.getenv("SKIN_PACK_RECORD") == "1"
        for ((v, file) in BOXED) {
            if (record || !packSnapshotExists(file)) { recordPackSnapshot(file, pack, v); continue }
            val snap = readPackSnapshot(file)
            for (d in GOLDEN_DEVICES) assertMatchesGolden(snap.getValue(d.name), placeFor(pack, v, d), label = "$file ${d.name}")
        }
    }
}
