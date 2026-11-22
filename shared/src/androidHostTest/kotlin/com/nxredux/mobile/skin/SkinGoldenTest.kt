package com.nxredux.mobile.skin

import kotlin.test.Test

class SkinGoldenTest {
    private val pack = skinPack()

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
