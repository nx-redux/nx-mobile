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
        val pack = assertNotNull(SkinPackManifest.parse(File("../androidApp/src/main/assets/skins/nx-carbon/skin.json").readText()))
        assertTrue(pack.layouts.isNotEmpty())
    }

    // The colour packs draw NX Carbon's layouts: they carry its boxes too, so they place alike.
    @Test
    fun everyColourPackHasTheCarbonBoxes() {
        val dir = File("../androidApp/src/main/assets/skins")
        val carbon = assertNotNull(SkinPackManifest.parse(File(dir, "nx-carbon/skin.json").readText())).layouts.associate { it.id to it.boxes }
        assertTrue(carbon.values.any { it.isNotEmpty() }, "NX Carbon has boxes")
        for (pack in dir.listFiles()!!.filter { it.isDirectory && it.name != "nx-carbon" }) {
            val layouts = assertNotNull(SkinPackManifest.parse(File(pack, "skin.json").readText()), pack.name).layouts
            for (l in layouts) assertEquals(carbon[l.id].orEmpty(), l.boxes, "${pack.name} ${l.id}")
        }
    }
}
