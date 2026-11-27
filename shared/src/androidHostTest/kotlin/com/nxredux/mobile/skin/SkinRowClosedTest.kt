package com.nxredux.mobile.skin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Every DS / 3DS drawing in the packs with a button taken out ([withoutControl]): the mic button while hidden, SWAP
 * always (it sits in the strip, [withStripSwap]); the row closed up, nothing broken.
 */
class SkinRowClosedTest {
    private val packs = File("../androidApp/src/main/assets/skins").listFiles()!!.filter { File(it, "skin.json").exists() }
        .map { assertNotNull(SkinPackManifest.parse(File(it, "skin.json").readText())) }

    @Test
    fun everyMicRowClosesUpInsideItsBox() = closesUp("MIC", minSeen = 30) { it.withoutControl("MIC") }

    @Test
    fun everySwapRowClosesUpInsideItsBox() = closesUp("SWAP", minSeen = 30) { it.withStripSwap(micHidden = false) }

    @Test
    fun everyMicRowClosesUpWithoutSwapToo() = closesUp("MIC", minSeen = 30) { it.withStripSwap(micHidden = true) }

    /** Every two-screen game layout keeps the touch shortcut strip SWAP moved into, and no SWAP of its own. */
    @Test
    fun everyTwoScreenLayoutHasTheStripAndNoPadSwap() {
        val bad = mutableListOf<String>()
        var seen = 0
        packs.forEach { pack ->
            pack.withStripSwap(micHidden = true).layouts.filter { it.variant.swapInStrip }.forEach { l ->
                seen++
                val at = "${pack.id} ${l.id}"
                val all = l.controls + l.boxes.flatMap { b -> b.sizes.flatMap { it.controls } }
                if (all.any { it.id == STRIP_SWAP }) bad += "$at: SWAP on the pad"
                val slots = l.controls.any { it.id.startsWith("TOUCH_") } || l.boxes.any { b -> b.sizes.all { z -> z.controls.any { it.id.startsWith("TOUCH_") } } }
                if (!slots && (l.frame?.strip ?: l.pieces.firstNotNullOfOrNull { it.strip }) == null) bad += "$at: no strip"
            }
        }
        assertTrue(seen > 30, "two-screen layouts seen: $seen")
        assertTrue(bad.isEmpty(), bad.take(20).joinToString("\n"))
    }

    private fun closesUp(id: String, minSeen: Int, take: (SkinPackManifest) -> SkinPackManifest) {
        val bad = mutableListOf<String>()
        var seen = 0
        packs.forEach { pack ->
            take(pack).layouts.zip(pack.layouts).forEach { (out, was) ->
                if (out.controls.any { it.id == id }) bad += "${pack.id} ${was.id}: $id kept"
                out.boxes.zip(was.boxes).forEach { (b, wb) ->
                    b.sizes.zip(wb.sizes).forEach { (z, wz) ->
                        if (wz.controls.none { it.id == id }) return@forEach
                        seen++
                        val at = "${pack.id} ${was.id} ${b.slot} ${z.w}x${z.h}"
                        if (z.controls.any { it.id == id }) bad += "$at: $id kept"
                        if (z.pieces.any { it.w <= 0 || it.h <= 0 || it.acrossScale <= 0f }) bad += "$at: empty piece"
                        if (z.pieces.size >= wz.pieces.size + 2) bad += "$at: pieces ${wz.pieces.size} -> ${z.pieces.size}"
                        // the row's pieces and buttons inside where they were, no further out
                        fun ext(ps: List<PieceSpec>) = listOf(ps.minOf { it.x.toFloat() }, ps.minOf { it.y.toFloat() }, ps.maxOf { it.x + it.w * it.acrossScale }, ps.maxOf { it.y + it.h * it.scale })
                        val g = wz.pieces.firstOrNull { id in it.group.orEmpty().split('+') }?.group
                        val now = z.pieces.filter { it.group == g }
                        if (g != null && now.isNotEmpty()) {
                            val (a0, b0, c0, d0) = ext(wz.pieces.filter { it.group == g })
                            val (a1, b1, c1, d1) = ext(now)
                            if (a1 < a0 - 1 || b1 < b0 - 1 || c1 > c0 + 1 || d1 > d0 + 1) bad += "$at: row grew $a0,$b0,$c0,$d0 -> $a1,$b1,$c1,$d1"
                            g.split('+').filter { it != id }.forEach { mate ->
                                val c = z.controls.firstOrNull { it.id == mate } ?: return@forEach
                                if (c.x < a1 || c.x > c1 || c.y < b1 || c.y > d1) bad += "$at: $mate off its well"
                            }
                        }
                    }
                }
            }
        }
        assertTrue(seen > minSeen, "$id drawings seen: $seen")
        assertTrue(bad.isEmpty(), bad.take(20).joinToString("\n"))
    }
}
