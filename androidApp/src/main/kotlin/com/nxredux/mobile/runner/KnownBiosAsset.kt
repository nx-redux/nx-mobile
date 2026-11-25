package com.nxredux.mobile.runner

import com.nxredux.mobile.bios.KnownBios

/** assets/bios/known.tsv (tools/bios-list), parsed once per process; empty when missing. */
object KnownBiosAsset : ParsedAsset<KnownBios>("bios/known.tsv", KnownBios.EMPTY, { KnownBios.parse(it) })
