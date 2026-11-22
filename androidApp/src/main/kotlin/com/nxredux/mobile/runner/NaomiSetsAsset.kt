package com.nxredux.mobile.runner

import com.nxredux.mobile.library.NaomiSets
import com.swordfish.libretrodroid.NxLog

/** assets/system/flycast/sets.tsv (staged by the cores pipeline), parsed once per process; empty when the build lacks it. */
object NaomiSetsAsset : ParsedAsset<NaomiSets>("system/flycast/sets.tsv", NaomiSets.EMPTY, { NaomiSets.parse(it) }) {
    override fun onUnreadable(e: Throwable) {
        NxLog.w("NaomiSetsAsset", "system/flycast/sets.tsv unreadable: NAOMI/Atomiswave titles and BIOS checks are off", e)
    }
}
