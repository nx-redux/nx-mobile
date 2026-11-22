package com.nxredux.mobile.achievements

import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.runner.ContentRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RaBadgesTest {
    private fun home() = RaHomeTree(raHomeSource()) { ContentRef("home") }
    private val folder = RaBadgeFolders.segments("Game Boy (GB)", 42)!!

    @Test fun fetches_both_variants_into_the_game_folder_once() = runTest {
        val http = FakeRaHttp().apply { getBytesResponder = { 200 to "PNG".encodeToByteArray() } }
        val tree = home(); val badges = RaBadges(tree, http)
        badges.ensureAll(folder, listOf("00001", "00001"))
        assertEquals(setOf("00001.png", "00001_lock.png"), tree.listing(folder).keys)
        badges.ensureAll(folder, listOf("00001"))
        assertEquals(2, http.gets.size)   // present files are not fetched again
    }
    @Test fun a_404_is_remembered_and_nothing_is_written() = runTest {
        val http = FakeRaHttp().apply { getBytesResponder = { 404 to ByteArray(0) } }
        val tree = home(); val badges = RaBadges(tree, http)
        val names = listOf("00001", "00002", "00003")
        badges.ensureAll(folder, names); badges.ensureAll(folder, names)
        val urls = names.flatMap { listOf("${RaRequests.MEDIA_BADGE}$it.png", "${RaRequests.MEDIA_BADGE}${it}_lock.png") }
        assertEquals(urls.sorted(), http.gets.sorted()); assertEquals(emptyMap(), tree.listing(folder))
    }
    @Test fun transport_errors_and_server_errors_are_not_remembered() = runTest {
        var answer: suspend () -> Pair<Int, ByteArray> = { throw RaHttpException("offline") }
        val http = FakeRaHttp().apply { getBytesResponder = { answer() } }
        val tree = home(); val badges = RaBadges(tree, http)
        badges.ensureAll(folder, listOf("00001"))
        answer = { 503 to ByteArray(0) }
        badges.ensureAll(folder, listOf("00001"))
        assertEquals(emptyMap(), tree.listing(folder))
        answer = { 200 to "PNG".encodeToByteArray() }
        badges.ensureAll(folder, listOf("00001"))
        assertEquals(setOf("00001.png", "00001_lock.png"), tree.listing(folder).keys)
        assertEquals(6, http.gets.size)
    }
    @Test fun no_home_means_no_folder() = runTest {
        val noHome = RaHomeTree(FakeRomsSource(emptyMap())) { null }
        val p = RaGamePointer("GB", "home", "Roms/Game Boy (GB)", "T.gb", "T")
        assertNull(RaBadges(noHome, FakeRaHttp()).folder(p, 42))
    }
    @Test fun folder_needs_a_pointer_and_a_game_id() = runTest {
        val badges = RaBadges(home(), FakeRaHttp())
        val p = RaGamePointer("GB", "home", "Roms/Game Boy (GB)", "T.gb", "T")
        assertEquals(folder, badges.folder(p, 42)); assertNull(badges.folder(p, 0)); assertNull(badges.folder(null, 42))
    }
    @Test fun ensureAll_makes_each_folder_level_once() = runTest {
        val src = raHomeSource().apply { yieldInCreate = true }
        val http = FakeRaHttp().apply { getBytesResponder = { 200 to "PNG".encodeToByteArray() } }
        val tree = RaHomeTree(src) { ContentRef("home") }
        RaBadges(tree, http).ensureAll(folder, listOf("00001", "00002", "00003"))
        val levels = folder.indices.drop(1).map { "home/" + folder.take(it + 1).joinToString("/") }
        assertEquals(levels.associateWith { 1 }, src.createDirectoryCalls)
        assertEquals(6, tree.listing(folder).size)
    }
    @Test fun ensureAll_fetches_nothing_when_the_folder_cannot_be_made() = runTest {
        val http = FakeRaHttp().apply { getBytesResponder = { 200 to "PNG".encodeToByteArray() } }
        val readOnly = RaHomeTree(FakeRomsSource(mapOf("home" to listOf("Roms"), "home/Roms" to emptyList()), writable = false)) { ContentRef("home") }
        RaBadges(readOnly, http).ensureAll(folder, listOf("00001"))
        assertTrue(http.gets.isEmpty())
    }
    @Test fun a_read_only_home_shows_its_badges_and_fetches_none() = runTest {
        // MainActivity falls back to a read grant when write is refused: badges already on the card still show.
        val dir = "home/" + folder.joinToString("/")
        val dirs = folder.indices.associate { i ->
            "home/" + folder.take(i + 1).joinToString("/") to listOfNotNull(folder.getOrNull(i + 1))
        } + mapOf("home" to listOf("Roms"), dir to listOf("00001.png", "00001_lock.png"))
        val src = FakeRomsSource(dirs, texts = mapOf("$dir/00001.png" to "PNG", "$dir/00001_lock.png" to "PNG"), writable = false)
        val http = FakeRaHttp().apply { getBytesResponder = { 200 to "PNG".encodeToByteArray() } }
        val tree = RaHomeTree(src) { ContentRef("home") }; val badges = RaBadges(tree, http)
        val p = RaGamePointer("GB", "home", "Roms/Game Boy (GB)", "T.gb", "T")
        assertEquals(folder, badges.folder(p, 42))
        assertEquals(setOf("00001.png", "00001_lock.png"), tree.listing(folder).keys)
        badges.ensureAll(folder, listOf("00001", "00002"))
        assertTrue(http.gets.isEmpty())   // nothing could be stored, so nothing is downloaded
    }
}
