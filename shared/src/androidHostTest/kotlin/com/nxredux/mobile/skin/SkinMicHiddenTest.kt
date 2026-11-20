package com.nxredux.mobile.skin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Every DS / 3DS drawing in the packs with its mic button hidden ([withoutControl]): the row closed up, nothing broken. */
class SkinMicHiddenTest {
    private val packs = File("../androidApp/src/main/assets/skins").listFiles()!!.filter { File(it, "skin.json").exists() }
        .map { assertNotNull(SkinPackManifest.parse(File(it, "skin.json").readText())) }

    @Test
    fun everyMicRowClosesUpInsideItsBox() {
        val bad = mutableListOf<String>()
        var seen = 0
        packs.forEach { pack ->
            pack.withoutControl("MIC").layouts.zip(pack.layouts).forEach { (out, was) ->
                out.boxes.zip(was.boxes).forEach { (b, wb) ->
                    b.sizes.zip(wb.sizes).forEach { (z, wz) ->
                        if (wz.controls.none { it.id == "MIC" }) return@forEach
                        seen++
                        val at = "${pack.id} ${was.id} ${b.slot} ${z.w}x${z.h}"
                        if (z.controls.any { it.id == "MIC" }) bad += "$at: MIC kept"
                        if (z.pieces.any { it.w <= 0 || it.h <= 0 || it.acrossScale <= 0f }) bad += "$at: empty piece"
                        if (z.pieces.size >= wz.pieces.size + 2) bad += "$at: pieces ${wz.pieces.size} -> ${z.pieces.size}"
                        // the row's pieces and buttons inside where they were, no further out
                        fun ext(ps: List<PieceSpec>) = listOf(ps.minOf { it.x.toFloat() }, ps.minOf { it.y.toFloat() }, ps.maxOf { it.x + it.w * it.acrossScale }, ps.maxOf { it.y + it.h * it.scale })
                        val g = wz.pieces.firstOrNull { "MIC" in it.group.orEmpty().split('+') }?.group
                        val now = z.pieces.filter { it.group == g }
                        if (g != null && now.isNotEmpty()) {
                            val (a0, b0, c0, d0) = ext(wz.pieces.filter { it.group == g })
                            val (a1, b1, c1, d1) = ext(now)
                            if (a1 < a0 - 1 || b1 < b0 - 1 || c1 > c0 + 1 || d1 > d0 + 1) bad += "$at: row grew $a0,$b0,$c0,$d0 -> $a1,$b1,$c1,$d1"
                            g.split('+').filter { it != "MIC" }.forEach { id ->
                                val c = z.controls.firstOrNull { it.id == id } ?: return@forEach
                                if (c.x < a1 || c.x > c1 || c.y < b1 || c.y > d1) bad += "$at: $id off its well"
                            }
                        }
                    }
                }
            }
        }
        assertTrue(seen > 30, "mic drawings seen: $seen")
        assertTrue(bad.isEmpty(), bad.take(20).joinToString("\n"))
    }
}
