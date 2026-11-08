package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A placement as JSON for nx-skins' preview_place.py (the format NxCarbonPackTest's SKIN_PREVIEW_DIR dumps write). */
internal fun skinPreviewJson(w: Int, h: Int, p: SkinPlacement?): String {
    val sb = StringBuilder()
    sb.append("{\"window\":[$w,$h],\"layout\":${p?.spec?.id?.let { "\"$it\"" }},\"image\":${p?.spec?.image?.let { "\"$it\"" }},\"cells\":[")
    p?.cells?.forEachIndexed { i, c ->
        if (i > 0) sb.append(',')
        sb.append("[${c.src.x},${c.src.y},${c.src.width},${c.src.height},${c.dst.x},${c.dst.y},${c.dst.width},${c.dst.height},${c.tileX},${c.tileY},${p.k}]")
    }
    sb.append("],\"parts\":${p?.spec?.parts?.let { "\"$it\"" }},\"pieces\":[")
    p?.pieces?.forEachIndexed { i, c ->
        if (i > 0) sb.append(',')
        sb.append("[${c.src.x},${c.src.y},${c.src.width},${c.src.height},${c.dst.x},${c.dst.y},${c.dst.width},${c.dst.height},${c.alt?.let { "\"$it\"" }}]")
    }
    sb.append("],\"glasses\":[")
    p?.glasses?.forEachIndexed { i, g -> if (i > 0) sb.append(','); sb.append("[\"${g.screen}\",${g.rect.x},${g.rect.y},${g.rect.width},${g.rect.height},${g.scale}]") }
    sb.append("],\"controls\":[")
    p?.controls?.forEachIndexed { i, c -> if (i > 0) sb.append(','); sb.append("[\"${c.spec.id}\",${c.x},${c.y},${c.spec.r},${c.spec.hw},${c.spec.hh},${c.spec.label?.let { "\"$it\"" }},${c.spec.labelDy},${c.spec.labelH},${c.spec.labelDx}]") }
    sb.append("]}")
    return sb.toString()
}

/**
 * Every well is a piece that moves with its buttons (the packs' backgrounds hold none): no button piece is left out of
 * a group, and every control sits on one of its pieces. The audit: with SKIN_PREVIEW_DIR set, each NX Carbon piece layout at its own size is written four ways, as laid
 * out (`_base`), with every group moved to its limit down right (`_plus`) and up left (`_minus`), and with no button
 * pieces at all (`_bare`: what a moved group leaves behind). A well the background holds shows in the bare one.
 */
class SkinWellsTest {
    private val packs = listOf("nx-carbon", "nx-onyx", "nx-obsidian", "nx-plasma", "nx-ember", "nx-volt", "nx-tide")

    @Test
    fun every_button_piece_moves_with_a_group_and_every_control_sits_on_a_piece() {
        for (id in packs) {
            val pack = assertNotNull(SkinPackManifest.parse(File("../androidApp/src/main/assets/skins/$id/skin.json").readText()), id)
            for (spec in pack.layouts.filter { it.frame != null }) {
                val grouped = SkinGroups.of(spec).flatMap { it.pieces }.toSet()
                val buttons = spec.pieces.indices.filter { spec.pieces[it].glass == null }
                // a piece in no group stays put when the groups move: a cap or well left behind
                assertTrue(buttons.all { it in grouped }, "$id ${spec.id}: pieces ${buttons.filter { it !in grouped }} in no group")
                val rects = buttons.map { val p = spec.pieces[it]; PxRect(p.x.toFloat(), p.y.toFloat(), p.w * p.scale, p.h * p.scale) }
                for (c in spec.controls) assertTrue(rects.any { c.x >= it.x && c.x <= it.x + it.width && c.y >= it.y && c.y <= it.y + it.height },
                    "$id ${spec.id}: ${c.id} at (${c.x}, ${c.y}) on no piece")
            }
        }
    }

    private val pack = assertNotNull(SkinPackManifest.parse(File("../androidApp/src/main/assets/skins/nx-carbon/skin.json").readText()))

    @Test
    fun dumpEveryLayoutAtItsLimits() {
        val out = System.getenv("SKIN_PREVIEW_DIR")?.let { File(it, "wells") } ?: return
        out.mkdirs()
        var n = 0
        for (spec in pack.layouts.filter { it.frame != null }) {
            val w = spec.refW; val h = spec.refH
            val p = SkinSolver.placePieces(spec, w.toFloat(), h.toFloat(), 1f) ?: continue
            val groups = SkinGroups.of(spec)
            fun at(sign: Float) = groups.associate { g ->
                g.id to if (g.free) SkinTweak(dx = sign * 0.25f * g.ref.width) else SkinTweaks.limit(SkinTweak(sign * 1e6f, sign * 1e6f), g)
            }
            File(out, "${spec.id}_base.json").writeText(skinPreviewJson(w, h, p))
            File(out, "${spec.id}_plus.json").writeText(skinPreviewJson(w, h, SkinTweaks.apply(p, at(1f), w.toFloat(), h.toFloat())))
            File(out, "${spec.id}_minus.json").writeText(skinPreviewJson(w, h, SkinTweaks.apply(p, at(-1f), w.toFloat(), h.toFloat())))
            File(out, "${spec.id}_bare.json").writeText(skinPreviewJson(w, h, p.copy(pieces = p.pieces.filter { it.piece < 0 && !it.strip }, controls = emptyList())))
            n++
        }
        assertTrue(n > 0)
    }
}
