package com.nxredux.mobile.runner

/** assets/wii/controls.tsv (tools/gametdb), parsed once per process; empty when missing. */
object WiiControlsAsset : ParsedAsset<WiiControlsTable>("wii/controls.tsv", WiiControlsTable.EMPTY, { WiiControlsTable.parse(it) })
