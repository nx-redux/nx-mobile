package com.nxredux.mobile.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncRomsTest {
    private val gb = ConsolePair("Game Boy (GB)", "GB Games (GB)", "GB")
    private fun unit(extra: Int, parent: String, landing: String, names: List<String> = listOf(landing), art: List<String> = emptyList(), tag: String = "GB") =
        ExtraUnit(extra, tag, parent, names, landing, ".sources/x/$landing", landing, art)
    private val home = listOf("/.retroachievements/hashes.json", "/.media/")
    private fun artPush(p: ConsolePair, phone: Boolean = false) = RomRun(SyncStep.SEND_ROMS, p.local, RomEnd.Home("${p.local}/.media"),
        RomEnd.Peer("roms", "${p.remote}/.media"), excludes = if (phone) emptyList() else listOf("/grid/", "/hero/"), ignoreExisting = true, pair = p.key)
    private fun artPull(p: ConsolePair) = RomRun(SyncStep.RECEIVE_ROMS, p.local, RomEnd.Peer("roms", "${p.remote}/.media"),
        RomEnd.Home("${p.local}/.media"), ignoreExisting = true, pair = p.key)

    @Test fun handheld_pair_pushes_then_pulls_the_home_folder() {
        val runs = SyncRoms.plan(Pairing(listOf(gb), emptyList()), ExtrasManifest(), ExtrasManifest(), mapOf(gb.local to emptySet()))
        assertEquals(listOf(
            RomRun(SyncStep.SEND_ROMS, gb.local, RomEnd.Home(gb.local), RomEnd.Peer("roms", gb.remote), excludes = home, pair = gb.key),
            artPush(gb),
            RomRun(SyncStep.RECEIVE_ROMS, gb.local, RomEnd.Peer("roms", gb.remote), RomEnd.Home(gb.local), excludes = home, pair = gb.key),
            artPull(gb),
        ), runs)
    }

    @Test fun own_extras_are_pushed_grouped_and_kept_out_of_the_pull() {
        val own = ExtrasManifest(units = listOf(unit(0, "GB", "a.gb"), unit(0, "GB", "b [!].gb"),
            unit(1, "", "c.gb", art = listOf(".sources/y/.media/boxart/c.png"))))
        val runs = SyncRoms.plan(Pairing(listOf(gb), emptyList()), own, ExtrasManifest(), mapOf(gb.local to emptySet()))
        assertTrue(RomRun(SyncStep.SEND_ROMS, gb.local, RomEnd.Extra(0, "GB"), RomEnd.Peer("roms", gb.remote), filesFrom = listOf("a.gb", "b [!].gb"), pair = gb.key) in runs)
        assertTrue(RomRun(SyncStep.SEND_ROMS, gb.local, RomEnd.Extra(1, ""), RomEnd.Peer("roms", gb.remote), filesFrom = listOf("c.gb"), pair = gb.key) in runs)
        assertTrue(RomRun(SyncStep.SEND_ROMS, gb.local, RomEnd.Home(".sources/y/.media"), RomEnd.Peer("roms", "${gb.remote}/.media"), filesFrom = listOf("boxart/c.png"), ignoreExisting = true, pair = gb.key) in runs)
        val pull = runs.first { it.step == SyncStep.RECEIVE_ROMS }
        assertEquals(home + listOf("/a.gb", "/b \\[!].gb", "/c.gb"), pull.excludes)
    }

    @Test fun the_home_copy_wins_over_an_extra_copy() {
        val own = ExtrasManifest(units = listOf(unit(0, "", "a.gb")))
        val runs = SyncRoms.plan(Pairing(listOf(gb), emptyList()), own, ExtrasManifest(), mapOf(gb.local to setOf("a.gb")))
        assertTrue(runs.none { it.from is RomEnd.Extra })
    }

    @Test fun peer_phone_extras_are_pulled_unless_this_phone_has_them() {
        val peer = ExtrasManifest(units = listOf(unit(2, "Games", "p.gb"), unit(2, "Games", "dup.gb"), unit(2, "Games", "home.gb")))
        val own = ExtrasManifest(units = listOf(unit(0, "", "dup.gb")))
        val runs = SyncRoms.plan(Pairing(listOf(gb), emptyList()), own, peer, mapOf(gb.local to setOf("home.gb")))
        assertTrue(RomRun(SyncStep.RECEIVE_ROMS, gb.local, RomEnd.Peer("extra-2", "Games"), RomEnd.Home(gb.local), only = listOf("p.gb"), pair = gb.key) in runs)
        val push = runs.first { it.step == SyncStep.SEND_ROMS && it.from == RomEnd.Home(gb.local) }
        // Every landing of the peer's extras, also home.gb, which `theirs` drops because this phone's home has it.
        assertEquals(home + listOf("/p.gb", "/dup.gb", "/home.gb"), push.excludes)
    }

    private val fbn = ConsolePair("Arcade (FBN)", "Arcade (FBN)", "FBN")
    private val mame = "Arcade (MAME)"

    /** Phone ↔ phone: the peer's extras of a sibling tag count as "has it", every name of a unit (a companion BIOS too). */
    @Test fun the_push_skips_what_a_peer_phone_keeps_in_an_extra_of_a_sibling_tag() {
        val peer = ExtrasManifest(units = listOf(unit(1, "MAME", "mslug.zip", names = listOf("mslug.zip", "neogeo.zip"), tag = "MAME"),
            unit(1, "MAME", "Garou.zip", tag = "MAME")))
        val own = ExtrasManifest(units = listOf(unit(0, "", "garou.zip", tag = "FBN"), unit(0, "", "kof.zip", tag = "FBN")))
        val local = mapOf(fbn.local to setOf("neogeo.zip", "mslug.zip", "pacman.zip"))
        val folders = listOf(fbn.remote, mame)
        val u = SyncRoms.units(fbn, own, peer, local, mapOf(fbn.remote to emptySet()), folders)
        assertEquals(listOf("kof.zip"), u.mine.map { it.landing })
        val push = SyncRoms.plan(Pairing(listOf(fbn), emptyList()), own, peer, local, mapOf(fbn.remote to emptySet()), peerIsPhone = true, peerFolders = folders)
            .first { it.step == SyncStep.SEND_ROMS && it.from == RomEnd.Home(fbn.local) }
        assertEquals(home + listOf("/mslug.zip", "/Garou.zip", "/neogeo.zip"), push.excludes)
    }

    /** A name the peer also has in its own `remote` folder is not "elsewhere"; a peer unit's landing is still excluded. */
    @Test fun a_peer_extra_name_also_in_the_peers_folder_is_not_held_back() {
        val peer = ExtrasManifest(units = listOf(unit(1, "MAME", "mslug.zip", names = listOf("mslug.zip", "neogeo.zip"), tag = "MAME")))
        val local = mapOf(fbn.local to setOf("neogeo.zip", "mslug.zip"))
        val push = SyncRoms.plan(Pairing(listOf(fbn), emptyList()), ExtrasManifest(), peer, local, mapOf(fbn.remote to setOf("neogeo.zip")),
            peerIsPhone = true, peerFolders = listOf(fbn.remote, mame)).first { it.step == SyncStep.SEND_ROMS && it.from == RomEnd.Home(fbn.local) }
        assertEquals(home + "/mslug.zip", push.excludes)
    }

    /** A peer extra of a tag with no folder among the peer's siblings of `remote` does not count. */
    @Test fun a_peer_extra_of_an_unrelated_tag_does_not_count() {
        val peer = ExtrasManifest(units = listOf(unit(1, "N", "neogeo.zip", tag = "NEOGEO")))
        val local = mapOf(fbn.local to setOf("neogeo.zip"))
        val push = SyncRoms.plan(Pairing(listOf(fbn), emptyList()), ExtrasManifest(), peer, local, emptyMap(),
            peerIsPhone = true, peerFolders = listOf(fbn.remote, "Neo Geo (NEOGEO)")).first { it.step == SyncStep.SEND_ROMS && it.from == RomEnd.Home(fbn.local) }
        assertEquals(home, push.excludes)
    }

    /** Phone ↔ phone: a folder's tag names only its console, so extras on another emulator of it move with its pair. */
    @Test fun a_peer_phone_pair_moves_extras_of_every_emulator_of_its_console() {
        val gba = ConsolePair("Game Boy Advance (GBA)", "Game Boy Advance (GBA)", "GBA")
        val own = ExtrasManifest(units = listOf(unit(0, "", "mine.gba", tag = "MGBA"), unit(0, "", "other.gb", tag = "GB")))
        val peer = ExtrasManifest(units = listOf(unit(2, "G", "theirs.gba", tag = "MGBA")))
        val u = SyncRoms.units(gba, own, peer, emptyMap())
        assertEquals(listOf("mine.gba"), u.mine.map { it.landing })
        assertEquals(listOf("theirs.gba"), u.theirs.map { it.landing })
        // A handheld's mapped pair pushes only its push tags' extras.
        assertTrue(SyncRoms.units(gba, own, ExtrasManifest(), emptyMap(), mapped = true).mine.isEmpty())
    }

    @Test fun pulled_games_take_their_handheld_folders_emulator() {
        val gba = "Game Boy Advance (GBA)"
        val mgba = ConsolePair(gba, "Game Boy Advance (MGBA)", "MGBA", setOf("MGBA"))
        val added = listOf("Emerald.gba", "Mine.gba", ".media", "map.txt")
        assertEquals(mapOf("Emerald.gba" to "MGBA"), SyncRoms.pulledGameTags(mgba, added, mapOf("mine.gba" to "GBA"), "GBA"))
        assertEquals(emptyMap(), SyncRoms.pulledGameTags(mgba, added, emptyMap(), "MGBA"))   // already the Default emulators pick
        val plain = ConsolePair(gba, gba, "GBA")
        assertEquals(mapOf("Emerald.gba" to "GBA"), SyncRoms.pulledGameTags(plain, listOf("Emerald.gba"), emptyMap(), "MGBA"))
        val gpgx = ConsolePair("Sega CD (SEGACD)", "Sega CD (GPGX)", "GPGX")
        assertEquals(mapOf("Sonic CD.chd" to "GPGX_SEGACD"), SyncRoms.pulledGameTags(gpgx, listOf("Sonic CD.chd"), emptyMap(), "SEGACD"))
    }

    /** A pulled disc folder's pick is its sheet's entry in the folder's own systems.txt. */
    @Test fun a_pulled_disc_folder_takes_its_handheld_folders_emulator_on_its_sheet() {
        val gpgx = ConsolePair("Sega CD (SEGACD)", "Sega CD (GPGX)", "GPGX")
        val discs = listOf("Snatcher (Disc 1).cue", "Snatcher (Disc 1).bin", "snatcher.M3U", "Snatcher.cue")
        assertEquals("snatcher.M3U" to "GPGX_SEGACD", SyncRoms.pulledDiscTag(gpgx, "Snatcher", discs, emptyMap(), "SEGACD"))
        assertEquals("Lunar.cue" to "GPGX_SEGACD", SyncRoms.pulledDiscTag(gpgx, "Lunar", listOf("Lunar.cue", "Lunar.bin"), emptyMap(), "SEGACD"))
        val psx = ConsolePair("Sony PlayStation (PS)", "Sony PlayStation (PSX)", "PSX")
        assertEquals("FF7.m3u" to "PSX", SyncRoms.pulledDiscTag(psx, "FF7", listOf("FF7.m3u", "FF7 (Disc 1).chd"), emptyMap(), "PS"))
        assertNull(SyncRoms.pulledDiscTag(psx, "FF7", listOf("FF7.m3u"), mapOf("ff7.m3u" to "PS"), "PS"))     // the user's pick
        assertNull(SyncRoms.pulledDiscTag(psx, "FF7", listOf("FF7.m3u"), emptyMap(), "PSX"))                  // already the default
        assertNull(SyncRoms.pulledDiscTag(psx, "Extras", listOf("disc.cue", "notes.txt"), emptyMap(), "PS"))  // no sheet named like it
        assertNull(SyncRoms.pulledDiscTag(psx, ".media", listOf(".media.m3u"), emptyMap(), "PS"))
    }

    @Test fun own_extras_are_not_pushed_where_the_peer_keeps_them_in_an_extra() {
        val own = ExtrasManifest(units = listOf(unit(0, "", "both.gb"), unit(0, "", "mine.gb")))
        val peer = ExtrasManifest(units = listOf(unit(3, "G", "both.gb")))
        val u = SyncRoms.units(gb, own, peer, emptyMap())
        assertEquals(listOf("mine.gb"), u.mine.map { it.landing })
        assertTrue(u.theirs.isEmpty())
    }

    @Test fun names_with_line_breaks_are_unsafe() {
        val own = ExtrasManifest(units = listOf(unit(0, "", "a\nb.gb"), unit(0, "", "c.gb", names = listOf("c\r.gb")), unit(0, "", "ok.gb")))
        assertEquals(listOf("ok.gb"), SyncRoms.units(gb, own, ExtrasManifest(), emptyMap()).mine.map { it.landing })
    }

    @Test fun all_pushes_come_before_all_pulls() {
        val fc = ConsolePair("Nintendo ES (FC)", "Nintendo ES (FC)", "FC")
        val runs = SyncRoms.plan(Pairing(listOf(gb, fc), emptyList()), ExtrasManifest(), ExtrasManifest(), emptyMap())
        assertEquals(List(4) { SyncStep.SEND_ROMS } + List(4) { SyncStep.RECEIVE_ROMS }, runs.map { it.step })
    }

    @Test fun excludes_escape_wildcards() {
        assertEquals("Tetris \\[!].gb", SyncRoms.escape("Tetris [!].gb"))
        assertEquals("a\\*b\\?c\\\\d", SyncRoms.escape("a*b?c\\d"))
        assertEquals("a\\b.gb", SyncRoms.escape("a\\b.gb"))
    }

    @Test fun peer_art_is_pulled_through_the_art_module() {
        val peer = ExtrasManifest(units = listOf(unit(1, "GB", "p.gb", art = listOf(".sources/y/GB/.media/boxart/p.png", ".sources/y/GB/.media/snap/p.png"))))
        val runs = SyncRoms.plan(Pairing(listOf(gb), emptyList()), ExtrasManifest(), peer, mapOf(gb.local to emptySet()))
        assertTrue(RomRun(SyncStep.RECEIVE_ROMS, gb.local, RomEnd.Peer("nx-art", "y/GB/.media"), RomEnd.Home("${gb.local}/.media"),
            only = listOf("boxart/p.png", "snap/p.png"), ignoreExisting = true, pair = gb.key) in runs)
    }

    @Test fun art_paths_with_dot_dot_are_dropped() {
        val art = listOf(".sources/../.media/boxart/x.png")
        val own = ExtrasManifest(units = listOf(unit(0, "", "a.gb", art = art)))
        val peer = ExtrasManifest(units = listOf(unit(1, "", "b.gb", art = art)))
        val runs = SyncRoms.plan(Pairing(listOf(gb), emptyList()), own, peer, mapOf(gb.local to emptySet()))
        assertTrue(runs.none { it.filesFrom == listOf("boxart/x.png") || it.only == listOf("boxart/x.png") })
    }

    @Test fun units_are_what_the_plan_sends_and_pulls() {
        val peer = ExtrasManifest(units = listOf(unit(2, "Games", "p.gb"), unit(2, "Games", "dup.gb"), unit(2, "Games", "home.gb"), unit(2, "..", "bad.gb")))
        val own = ExtrasManifest(units = listOf(unit(0, "", "dup.gb"), unit(0, "", "home.gb"), unit(0, "", "..")))
        val u = SyncRoms.units(gb, own, peer, mapOf(gb.local to setOf("home.gb")))
        assertEquals(emptyList(), u.mine.map { it.landing })   // dup.gb: the peer keeps it in an extra folder too
        assertEquals(listOf("p.gb"), u.theirs.map { it.landing })
    }

    @Test fun unsafe_units_are_dropped() {
        val peer = ExtrasManifest(units = listOf(
            unit(1, "../x", "a.gb"),
            unit(1, "ok/./x", "b.gb"),
            unit(1, "ok//x", "c.gb"),
            unit(1, "ok", ".."),
            unit(1, "ok", "d.gb", names = listOf("a/b")),
            unit(1, "ok", "e.gb", names = listOf("")),
        ))
        val own = ExtrasManifest(units = listOf(unit(0, "..", "f.gb"), unit(0, "ok", "g/h")))
        val runs = SyncRoms.plan(Pairing(listOf(gb), emptyList()), own, peer, mapOf(gb.local to emptySet()))
        assertEquals(listOf(
            RomRun(SyncStep.SEND_ROMS, gb.local, RomEnd.Home(gb.local), RomEnd.Peer("roms", gb.remote), excludes = home, pair = gb.key),
            artPush(gb),
            RomRun(SyncStep.RECEIVE_ROMS, gb.local, RomEnd.Peer("roms", gb.remote), RomEnd.Home(gb.local), excludes = home, pair = gb.key),
            artPull(gb),
        ), runs)
    }

    // Art fills gaps only.

    @Test fun art_moves_in_its_own_runs_that_never_replace_a_file() {
        val runs = SyncRoms.plan(Pairing(listOf(gb), emptyList()), ExtrasManifest(), ExtrasManifest(), mapOf(gb.local to emptySet()))
        assertTrue(runs.filter { it.from == RomEnd.Home(gb.local) || it.to == RomEnd.Home(gb.local) }.all { "/.media/" in it.excludes })
        assertTrue(artPush(gb) in runs)
        assertTrue(artPull(gb) in runs)
    }

    @Test fun grid_and_hero_go_to_a_phone_but_not_to_a_handheld() {
        val art = listOf(".sources/y/.media/boxart/c.png", ".sources/y/.media/grid/c.png", ".sources/y/.media/hero/c.png")
        val own = ExtrasManifest(units = listOf(unit(1, "", "c.gb", art = art), unit(1, "", "d.gb", art = listOf(".sources/z/.media/hero/d.png"))))
        val pairing = Pairing(listOf(gb), emptyList())
        val toHandheld = SyncRoms.plan(pairing, own, ExtrasManifest(), mapOf(gb.local to emptySet()), peerIsPhone = false)
        assertTrue(artPush(gb) in toHandheld)
        assertEquals(listOf(listOf("boxart/c.png")), toHandheld.filter { it.from == RomEnd.Home(".sources/y/.media") }.map { it.filesFrom })
        assertTrue(toHandheld.none { it.from == RomEnd.Home(".sources/z/.media") })
        val toPhone = SyncRoms.plan(pairing, own, ExtrasManifest(), mapOf(gb.local to emptySet()), peerIsPhone = true)
        assertTrue(artPush(gb, phone = true) in toPhone)
        assertTrue(RomRun(SyncStep.SEND_ROMS, gb.local, RomEnd.Home(".sources/y/.media"), RomEnd.Peer("roms", "${gb.remote}/.media"),
            filesFrom = listOf("boxart/c.png", "grid/c.png", "hero/c.png"), ignoreExisting = true, pair = gb.key) in toPhone)
        assertTrue(RomRun(SyncStep.SEND_ROMS, gb.local, RomEnd.Home(".sources/z/.media"), RomEnd.Peer("roms", "${gb.remote}/.media"),
            filesFrom = listOf("hero/d.png"), ignoreExisting = true, pair = gb.key) in toPhone)
    }

    // No second copy across emulator folders of one console.

    private val mgba = ConsolePair("Game Boy Advance (MGBA)", "Game Boy Advance (MGBA)", "MGBA")
    private val gbaFolder = "Game Boy Advance (GBA)"

    @Test fun the_push_skips_names_the_peer_keeps_in_a_sibling_folder() {
        val remote = mapOf(mgba.remote to setOf("both.gba"), gbaFolder to setOf("emerald.gba", "both.gba", "only-there.gba"))
        val local = mapOf(mgba.local to setOf("Emerald.gba", "both.gba", "new.gba"))
        val runs = SyncRoms.plan(Pairing(listOf(mgba), emptyList()), ExtrasManifest(), ExtrasManifest(), local, remote)
        val push = runs.first { it.step == SyncStep.SEND_ROMS && it.from == RomEnd.Home(mgba.local) }
        assertEquals(home + "/Emerald.gba", push.excludes)
    }

    @Test fun an_own_extra_is_not_pushed_when_the_peer_keeps_it_in_a_sibling_folder() {
        val own = ExtrasManifest(units = listOf(unit(0, "", "Ruby.gba", tag = "MGBA"), unit(0, "", "Zero.gba", tag = "MGBA")))
        val remote = mapOf(mgba.remote to emptySet(), gbaFolder to setOf("ruby.gba"))
        assertEquals(listOf("Zero.gba"), SyncRoms.units(mgba, own, ExtrasManifest(), emptyMap(), remote).mine.map { it.landing })
        val runs = SyncRoms.plan(Pairing(listOf(mgba), emptyList()), own, ExtrasManifest(), mapOf(mgba.local to emptySet()), remote)
        assertEquals(listOf(listOf("Zero.gba")), runs.filter { it.from is RomEnd.Extra }.map { it.filesFrom })
    }

    @Test fun the_pull_skips_names_this_phone_keeps_in_a_sibling_folder_or_a_sibling_tags_extra() {
        val local = mapOf(mgba.local to setOf("both.gba"), gbaFolder to setOf("Sapphire.gba", "both.gba"))
        val own = ExtrasManifest(units = listOf(unit(0, "GBA", "Ruby.gba", tag = "GBA"), unit(0, "GB", "Tetris.gb")))
        val peer = ExtrasManifest(units = listOf(unit(2, "G", "Sapphire.gba", tag = "MGBA"), unit(2, "G", "ruby.gba", tag = "MGBA"),
            unit(2, "G", "Leaf.gba", tag = "MGBA")))
        val runs = SyncRoms.plan(Pairing(listOf(mgba), emptyList()), own, peer, local)
        val pull = runs.first { it.step == SyncStep.RECEIVE_ROMS && it.from == RomEnd.Peer("roms", mgba.remote) }
        assertEquals(home + listOf("/Sapphire.gba", "/Ruby.gba"), pull.excludes)
        assertEquals(listOf("Leaf.gba"), SyncRoms.units(mgba, own, peer, local).theirs.map { it.landing })
    }

    /** A sibling's `map.txt`, `.media`, `.retroachievements` are not games: the console folder's own still travel. */
    @Test fun hidden_names_in_a_sibling_folder_are_not_games() {
        val hidden = setOf("map.txt", ".media", ".retroachievements", "systems.txt")
        val remote = mapOf(mgba.remote to emptySet(), gbaFolder to hidden)
        val local = mapOf(mgba.local to setOf("map.txt", "systems.txt"), gbaFolder to hidden)
        val runs = SyncRoms.plan(Pairing(listOf(mgba), emptyList()), ExtrasManifest(), ExtrasManifest(), local, remote)
        val homeRuns = runs.filter { it.from == RomEnd.Home(mgba.local) || it.to == RomEnd.Home(mgba.local) }
        assertEquals(2, homeRuns.size)
        homeRuns.forEach { assertEquals(home, it.excludes) }
    }

    @Test fun every_name_of_a_sibling_or_own_tag_extra_counts_so_a_companion_bios_is_not_pulled_again() {
        val fbn = ConsolePair("Arcade (FBN)", "Arcade (FBN)", "FBN")
        val local = mapOf(fbn.local to setOf("pacman.zip"), "Arcade (MAME)" to emptySet())
        val own = ExtrasManifest(units = listOf(
            unit(0, "", "mslug.zip", names = listOf("mslug.zip", "neogeo.zip"), tag = "MAME"),
            unit(0, "", "kof98.zip", names = listOf("kof98.zip", "neogeo.zip", "pacman.zip"), tag = "FBN"),
        ))
        val pull = SyncRoms.plan(Pairing(listOf(fbn), emptyList()), own, ExtrasManifest(), local)
            .first { it.step == SyncStep.RECEIVE_ROMS && it.from == RomEnd.Peer("roms", fbn.remote) }
        assertEquals(home + listOf("/kof98.zip", "/mslug.zip", "/neogeo.zip"), pull.excludes)
    }

    // A handheld's emulator folders of one console.

    private val gbaGba = ConsolePair(gbaFolder, gbaFolder, "GBA", setOf("GBA"))
    private val gbaMgba = ConsolePair(gbaFolder, mgba.remote, "MGBA", setOf("MGBA"))
    private val mapped = Pairing(listOf(gbaGba, gbaMgba), emptyList())
    private val mappedHome = mapOf(gbaFolder to setOf("A.gba", "B.gba"), mgba.local to emptySet())
    private val mappedTags = mapOf(gbaFolder to mapOf("B.gba" to "MGBA"))

    @Test fun each_pair_pushes_only_the_games_of_its_emulators() {
        val runs = SyncRoms.plan(mapped, ExtrasManifest(), ExtrasManifest(), mappedHome, gameTags = mappedTags, mapped = true)
        fun push(p: ConsolePair) = runs.single { it.step == SyncStep.SEND_ROMS && it.from == RomEnd.Home(gbaFolder) && it.to == RomEnd.Peer("roms", p.remote) }
        // Two folders into one: the badges move in their own never-replacing runs.
        val shared = listOf("/.retroachievements/", "/.media/")
        assertEquals(shared + "/B.gba", push(gbaGba).excludes)
        assertEquals(shared + "/A.gba", push(gbaMgba).excludes)
        assertEquals(gbaGba.key, push(gbaGba).pair)
        assertEquals(gbaMgba.key, push(gbaMgba).pair)
        val pulls = runs.filter { it.step == SyncStep.RECEIVE_ROMS && it.to == RomEnd.Home(gbaFolder) }
        assertEquals(listOf(RomEnd.Peer("roms", gbaFolder), RomEnd.Peer("roms", mgba.remote)), pulls.map { it.from })
    }

    @Test fun art_goes_only_with_its_game() {
        val art = mapOf(gbaFolder to listOf("boxart/A.png", "boxart/B.png", "screenshot/B.jpg", "grid/B.png", "bg.png"))
        val runs = SyncRoms.plan(mapped, ExtrasManifest(), ExtrasManifest(), mappedHome, gameTags = mappedTags, localArt = art, mapped = true)
        fun artPush(runs: List<RomRun>, p: ConsolePair) = runs.single { it.step == SyncStep.SEND_ROMS && it.to == RomEnd.Peer("roms", "${p.remote}/.media") }
        assertEquals(RomRun(SyncStep.SEND_ROMS, gbaFolder, RomEnd.Home("$gbaFolder/.media"), RomEnd.Peer("roms", "${mgba.remote}/.media"),
            filesFrom = listOf("boxart/B.png", "screenshot/B.jpg", "bg.png"), ignoreExisting = true, pair = gbaMgba.key), artPush(runs, gbaMgba))
        assertEquals(listOf("boxart/A.png", "bg.png"), artPush(runs, gbaGba).filesFrom)
        // A game the handheld already has in the pair's folder takes its art along too.
        val withA = SyncRoms.plan(mapped, ExtrasManifest(), ExtrasManifest(), mappedHome, mapOf(mgba.remote to setOf("a.gba")),
            gameTags = mappedTags, localArt = art, mapped = true)
        assertEquals(listOf("boxart/A.png", "boxart/B.png", "screenshot/B.jpg", "bg.png"), artPush(withA, gbaMgba).filesFrom)
    }

    /**
     * A game in both handheld GBA folders must not be pulled from each in turn on every sync. One pair pulls it: the one
     * this phone's copy pushes to (A.gba: the default GBA; B.gba: MGBA), else the first that has it.
     */
    @Test fun a_game_in_two_emulator_folders_is_pulled_by_one_pair_only() {
        val entries = mapOf(gbaFolder to setOf("A.gba", "b.GBA", "New.gba", "Only GBA.gba", ".retroachievements", ".media"),
            mgba.remote to setOf("a.gba", "B.gba", "New.gba", "Only MGBA.gba", ".retroachievements", ".media"))
        val lower = entries.mapValues { (_, v) -> v.mapTo(HashSet()) { it.lowercase() } }
        val runs = SyncRoms.plan(mapped, ExtrasManifest(), ExtrasManifest(), mappedHome, lower, gameTags = mappedTags, mapped = true,
            remoteEntries = entries)
        fun pull(p: ConsolePair) = runs.single { it.step == SyncStep.RECEIVE_ROMS && it.from == RomEnd.Peer("roms", p.remote) }
        val ra = listOf("/.retroachievements/", "/.media/")
        // A.gba (untagged: GBA) and the new game come from (GBA); B.gba (MGBA) from (MGBA), in the peer's spelling.
        assertEquals(ra + "/b.GBA", pull(gbaGba).excludes)
        assertEquals(ra + listOf("/a.gba", "/New.gba"), pull(gbaMgba).excludes)
        // Badges merge both ways, never replacing one the receiver has; hashes.json merges apart.
        for (p in listOf(gbaGba, gbaMgba)) {
            assertTrue(RomRun(SyncStep.RECEIVE_ROMS, gbaFolder, RomEnd.Peer("roms", "${p.remote}/.retroachievements"),
                RomEnd.Home("$gbaFolder/.retroachievements"), excludes = listOf("/hashes.json"), ignoreExisting = true, pair = p.key) in runs)
            assertTrue(RomRun(SyncStep.SEND_ROMS, gbaFolder, RomEnd.Home("$gbaFolder/.retroachievements"),
                RomEnd.Peer("roms", "${p.remote}/.retroachievements"), excludes = listOf("/hashes.json"), ignoreExisting = true, pair = p.key) in runs)
        }
        // Pushes are unchanged: each game to its own emulator folder only.
        val before = SyncRoms.plan(mapped, ExtrasManifest(), ExtrasManifest(), mappedHome, lower, gameTags = mappedTags, mapped = true)
        assertEquals(before.filter { it.step == SyncStep.SEND_ROMS }, runs.filter { it.step == SyncStep.SEND_ROMS })
    }

    @Test fun a_game_this_phone_lacks_comes_from_the_first_folder_that_has_it() {
        val p = gbaGba; val group = listOf(gbaGba, gbaMgba)
        val entries = mapOf(gbaFolder to setOf("X.gba", "map.txt"), mgba.remote to setOf("x.gba", "MAP.TXT", "Y.gba"))
        assertEquals(emptyList(), SyncRoms.pulledElsewhere(p, group, entries, emptyMap(), emptyList(), emptyMap()))
        assertEquals(listOf("x.gba", "MAP.TXT"), SyncRoms.pulledElsewhere(gbaMgba, group, entries, emptyMap(), emptyList(), emptyMap()))
        // Tagged MGBA here, so (MGBA) keeps it; with (MGBA) not holding it, the first holder does.
        assertEquals(listOf("X.gba"), SyncRoms.pulledElsewhere(p, group, entries, emptyMap(), listOf("X.gba"), mapOf("X.gba" to "MGBA")))
        val gbaOnly = mapOf(gbaFolder to setOf("Z.gba"), mgba.remote to emptySet())
        assertEquals(emptyList(), SyncRoms.pulledElsewhere(p, group, gbaOnly, emptyMap(), listOf("Z.gba"), mapOf("Z.gba" to "MGBA")))
        // A console with one handheld folder leaves nothing out.
        val single = SyncRoms.plan(Pairing(listOf(gbaGba), emptyList()), ExtrasManifest(), ExtrasManifest(), mappedHome,
            remoteEntries = entries, mapped = true)
        assertEquals(home, single.single { it.step == SyncStep.RECEIVE_ROMS && it.from == RomEnd.Peer("roms", gbaFolder) }.excludes)
    }

    /** BIOS: tag folders both sides have (ignoring case, each keeping its spelling); every push, then every pull; never replacing. */
    @Test fun bios_plan_pairs_tags_ignoring_case_pushes_then_pulls() {
        val runs = SyncRoms.biosPlan(local = listOf("PS", "fbn", "MD", ".hidden", "DC"), remote = listOf("ps", "FBN", "GBA", ".hidden"))
        assertEquals(listOf(
            RomRun(SyncStep.SEND_BIOS, "PS", RomEnd.Bios("PS"), RomEnd.Peer("bios", "ps"), ignoreExisting = true),
            RomRun(SyncStep.SEND_BIOS, "fbn", RomEnd.Bios("fbn"), RomEnd.Peer("bios", "FBN"), ignoreExisting = true),
            RomRun(SyncStep.RECEIVE_BIOS, "PS", RomEnd.Peer("bios", "ps"), RomEnd.Bios("PS"), ignoreExisting = true),
            RomRun(SyncStep.RECEIVE_BIOS, "fbn", RomEnd.Peer("bios", "FBN"), RomEnd.Bios("fbn"), ignoreExisting = true),
        ), runs)
    }

    @Test fun bios_plan_prefers_the_exact_name_and_drops_unsafe_names() {
        val runs = SyncRoms.biosPlan(local = listOf("PS", "..", "a/b", "x\ny"), remote = listOf("ps", "PS", "..", "a/b", "x\ny"))
        assertEquals(listOf(RomEnd.Peer("bios", "PS")), runs.filter { it.step == SyncStep.SEND_BIOS }.map { it.to })
        assertEquals(2, runs.size)
        assertTrue(SyncRoms.biosPlan(emptyList(), listOf("PS")).isEmpty())
    }
    @Test fun bios_plan_mapped_fills_the_console_folder_from_every_emulator_folder() {
        val runs = SyncRoms.biosPlanMapped(local = listOf("GBA", "PS"), remote = listOf("GBA", "MGBA", "PSX", "DCX"))
        fun push(l: String, r: String) = RomRun(SyncStep.SEND_BIOS, l, RomEnd.Bios(l), RomEnd.Peer("bios", r), ignoreExisting = true, pair = "$l\u0000$r")
        fun pull(r: String, l: String) = RomRun(SyncStep.RECEIVE_BIOS, l, RomEnd.Peer("bios", r), RomEnd.Bios(l), ignoreExisting = true, pair = "$l\u0000$r")
        assertEquals(listOf(
            push("GBA", "GBA"), push("GBA", "MGBA"), push("PS", "PSX"),
            pull("GBA", "GBA"), pull("MGBA", "GBA"), pull("PSX", "PS"),
        ), runs)
    }

    @Test fun bios_plan_mapped_pulls_into_the_console_tag_folder_and_pairs_unknown_folders_by_name() {
        // A leftover `MGBA` folder here does not push; the pulls land in `GBA`, which rsync creates. `xyz` (no console) meets `XYZ` by name.
        val runs = SyncRoms.biosPlanMapped(local = listOf("MGBA", "xyz", ".hidden", ".."), remote = listOf("MGBA", "XYZ", "DCX", ".hidden"))
        assertEquals(listOf(
            RomRun(SyncStep.SEND_BIOS, "xyz", RomEnd.Bios("xyz"), RomEnd.Peer("bios", "XYZ"), ignoreExisting = true, pair = "xyz\u0000XYZ"),
            RomRun(SyncStep.RECEIVE_BIOS, "GBA", RomEnd.Peer("bios", "MGBA"), RomEnd.Bios("GBA"), ignoreExisting = true, pair = "GBA\u0000MGBA"),
            RomRun(SyncStep.RECEIVE_BIOS, "xyz", RomEnd.Peer("bios", "XYZ"), RomEnd.Bios("xyz"), ignoreExisting = true, pair = "xyz\u0000XYZ"),
        ), runs)
    }

    /** Follow-up 5: beside `Bios/GBA`, a leftover `Bios/MGBA` stays on the phone; pulls are unchanged. */
    @Test fun bios_plan_mapped_pushes_only_the_console_tag_folder() {
        val runs = SyncRoms.biosPlanMapped(local = listOf("MGBA", "GBA", "PSX"), remote = listOf("GBA", "MGBA", "PS"))
        fun push(l: String, r: String) = RomRun(SyncStep.SEND_BIOS, l, RomEnd.Bios(l), RomEnd.Peer("bios", r), ignoreExisting = true, pair = "$l\u0000$r")
        fun pull(r: String, l: String) = RomRun(SyncStep.RECEIVE_BIOS, l, RomEnd.Peer("bios", r), RomEnd.Bios(l), ignoreExisting = true, pair = "$l\u0000$r")
        assertEquals(listOf(
            push("GBA", "GBA"), push("GBA", "MGBA"),
            pull("GBA", "GBA"), pull("MGBA", "GBA"), pull("PS", "PS"),
        ), runs)
        // Phone ↔ phone pairs by name, leftovers included.
        assertTrue(SyncRoms.biosPlan(listOf("MGBA"), listOf("MGBA")).any { it.step == SyncStep.SEND_BIOS })
    }
}
