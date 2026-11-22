package com.nxredux.mobile.skin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SkinBoxFormatTest {
    // A layout's control boxes: a slot and its sizes, a compact one tagged for a device shape.
    @Test
    fun aLayoutWithBoxesParses() {
        val text = """{"format":1,"id":"t","name":"T","layouts":[{"id":"grip_t","family":"grip","variant":"menu","image":"t.webp",
            "width":10,"height":10,"tree":{"part":"a"},"glasses":[],"scales":[],"controls":[],
            "boxes":[{"slot":"right","sizes":[
              {"w":420,"h":980,"controls":[{"id":"A","shape":"circle","x":300,"y":200,"r":80}]},
              {"w":360,"h":980,"when":{"orientation":"portrait","minWdp":400,"maxWdp":460}}]}]}]}"""
        val box = assertNotNull(SkinPackManifest.parse(text)).layouts.single().boxes.single()
        assertEquals(BoxSlot.RIGHT, box.slot)
        assertEquals(2, box.sizes.size)
        assertEquals("A", box.sizes[0].controls.single().id)
        assertEquals(460f, assertNotNull(box.sizes[1].`when`).maxWdp)
        assertEquals("portrait", box.sizes[1].`when`?.orientation)
    }

    // A pack without boxes (every pack before them) still parses, every layout with none.
    @Test
    fun aLayoutWithoutBoxesStillParses() {
        val pack = skinPack()
        assertTrue(pack.layouts.isNotEmpty())
    }

    // The colour packs draw NX Carbon's layouts: they carry its boxes too, so they place alike. Each pack packs its own
    // parts picture, so only where a piece is in it may differ.
    @Test
    fun everyColourPackHasTheCarbonBoxes() {
        val dir = File("../androidApp/src/main/assets/skins")
        fun atlasFree(b: List<BoxSpec>) = b.map { bx -> bx.copy(sizes = bx.sizes.map { z -> z.copy(pieces = z.pieces.map { it.copy(sx = 0, sy = 0) },
            controls = z.controls.map { it.copy(markSx = 0, markSy = 0) }) }) }   // (a mark's art: where its own pack's picture has it)
        val carbon = assertNotNull(SkinPackManifest.parse(File(dir, "nx-carbon/skin.json").readText())).layouts.associate { it.id to atlasFree(it.boxes) }
        assertTrue(carbon.values.any { it.isNotEmpty() }, "NX Carbon has boxes")
        for (pack in dir.listFiles()!!.filter { it.isDirectory && it.name != "nx-carbon" }) {
            val layouts = assertNotNull(SkinPackManifest.parse(File(pack, "skin.json").readText()), pack.name).layouts
            for (l in layouts) assertEquals(carbon[l.id].orEmpty(), atlasFree(l.boxes), "${pack.name} ${l.id}")
        }
    }

    // A box's piece is in its own pack's parts picture: the picture of one of that pack's layout pieces (or a slice of
    // one, a well's ends and middle), not where NX Carbon's picture has it.
    @Test
    fun everyBoxPieceIsInItsOwnPacksParts() {
        val dir = File("../androidApp/src/main/assets/skins")
        for (pack in dir.listFiles()!!.filter { it.isDirectory }) {
            val layouts = assertNotNull(SkinPackManifest.parse(File(pack, "skin.json").readText()), pack.name).layouts
            val drawn = layouts.flatMap { it.pieces }
            for (l in layouts) for (bx in l.boxes) for (z in bx.sizes) for (p in z.pieces) assertTrue(drawn.any { q ->
                p.sx >= q.sx && p.sy >= q.sy && p.sx + p.w <= q.sx + q.w && p.sy + p.h <= q.sy + q.h && (p.w == q.w || p.h == q.h)
            }, "${pack.name} ${l.id} ${bx.slot} ${p.sx},${p.sy} ${p.w}x${p.h}")
        }
    }

    // The format this app writes and reads: the packs declare it; an older pack still reads (its newer fields absent), a
    // newer one does not (it may draw what this app cannot).
    @Test
    fun theFormatIsReadUpToThisAppsOwn() {
        val dir = File("../androidApp/src/main/assets/skins")
        for (pack in dir.listFiles()!!.filter { it.isDirectory }) assertEquals(SkinPackManifest.FORMAT, assertNotNull(SkinPackManifest.parse(File(pack, "skin.json").readText()), pack.name).format, pack.name)
        assertEquals(2, SkinPackManifest.FORMAT)
        fun pack(format: Int) = """{"format":$format,"id":"t","name":"T","layouts":[]}"""
        assertNotNull(SkinPackManifest.parse(pack(1)))
        assertNotNull(SkinPackManifest.parse(pack(2)))
        assertEquals(null, SkinPackManifest.parse(pack(3)))
        assertEquals(null, SkinPackManifest.parse(pack(0)))
    }

    // A pack's box list with a slot twice, or a box with no sizes, places as without them: the first box of a slot, the
    // empty one passed over (not the whole layout lost).
    @Test
    fun aDuplicateOrEmptyBoxIsPassedOver() {
        val pack = skinPack()
        val d = GOLDEN_DEVICES.first { it.name == "cover_land" }
        val before = assertNotNull(placeFor(pack, SkinVariant.SMS, d))
        val id = before.spec.id
        val odd = pack.copy(layouts = pack.layouts.map { l -> if (l.id != id) l else l.copy(boxes = l.boxes + l.boxes.first().copy(sizes = l.boxes.last().sizes) + BoxSpec(BoxSlot.TOP_ROW, emptyList())) })
        val after = assertNotNull(placeFor(odd, SkinVariant.SMS, d), "still placed")
        assertEquals(goldenOf(before), goldenOf(after))
    }

}
