package com.nxredux.mobile.runner

import com.nxredux.mobile.library.ArcadeSets

/** assets/system/fbneo/sets.tsv (staged by the cores pipeline), parsed once per process; empty when the build lacks it. */
object ArcadeSetsAsset : ParsedAsset<ArcadeSets>("system/fbneo/sets.tsv", ArcadeSets.EMPTY, { ArcadeSets.parse(it) })
