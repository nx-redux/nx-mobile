package com.nxredux.mobile.achievements

import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.runner.ContentRef

fun fakePlatform(
    store: FakeRaStore = FakeRaStore(), http: FakeRaHttp = FakeRaHttp(), hasher: FakeRaHasher = FakeRaHasher(), connectivity: FakeConnectivity = FakeConnectivity(),
    home: RaHomeTree = RaHomeTree(raHomeSource()) { ContentRef("home") },
) = RaPlatform(store, http, hasher, FakeRaSigner(), connectivity, home)

/** A home tree's source: "home" holding Roms and, in it, an empty folder for each of [roms]. */
fun raHomeSource(vararg roms: String) =
    FakeRomsSource(mapOf("home" to listOf("Roms"), "home/Roms" to roms.toList()) + roms.associate { "home/Roms/$it" to emptyList<String>() })
