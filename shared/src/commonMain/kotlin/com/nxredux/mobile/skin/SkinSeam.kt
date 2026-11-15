package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect

/**
 * One draw over a fold board's hinge row when it is drawn without its hinge ([SkinPlacement.slab]): [cell] drawn as cells are
 * ([skinTiles]), upside down where [mirror], fading from opaque at its top to clear at its bottom where [fade].
 */
data class SkinSeamDraw(val cell: SkinCell, val mirror: Boolean, val fade: Boolean)

/**
 * The fold board as one slab: its lid and body joined over the hinge row, from the board's own picture. Per column of
 * the hinge row, the body's top is mirrored up over the hinge (meeting the body where it shows, so no seam there), and
 * the lid's last rows are drawn again over that, fading out, so the lid melts into the body.
 */
object SkinSeam {
    /** Picture px between the lid's bottom edge and the hinge row (the gap's dark lip, left out). */
    private const val LID_GAP = 8f
    /** Picture px of the lid that fade into the body. */
    private const val FADE = 72f
    /** Picture px into the body the mirror starts at: past the corners its top edge rounds. */
    private const val BODY_SKIP = 16f

    /** The draws over [p]'s hinge row, or empty when its layout has none ([stretchMax]: as for [skinTiles]). */
    fun draws(p: SkinPlacement, stretchMax: Float): List<SkinSeamDraw> {
        val rows = p.spec.tree.takeIf { it.split == SliceSplit.ROWS }?.parts ?: return emptyList()
        val at = rows.indexOfFirst { it.isHinge }
        if (at < 0) return emptyList()
        val hingeTop = rows.take(at).sumOf { it.len }.toFloat()
        val hingeBottom = hingeTop + rows[at].len
        val lidFrom = hingeTop - LID_GAP - FADE
        val lidTo = hingeTop - LID_GAP
        fun sameColumn(a: SkinCell, b: SkinCell) = kotlin.math.abs(a.src.x - b.src.x) < 0.5f && kotlin.math.abs(a.src.width - b.src.width) < 0.5f
        return p.cells.filter { kotlin.math.abs(it.src.y - hingeTop) < 0.5f }.flatMap { h ->
            val lid = p.cells.firstOrNull { sameColumn(it, h) && it.src.y <= lidFrom && it.src.y + it.src.height >= lidTo } ?: return@flatMap emptyList()
            // the body's first row that shows below the hinge (a crop row's top may be cut off), past its thin lip
            val body = p.cells.filter { sameColumn(it, h) && it.src.y >= hingeBottom && it.src.height > BODY_SKIP * 4 }.minByOrNull { it.src.y }
                ?: return@flatMap emptyList()
            val lidScale = lid.dst.height / lid.src.height
            val bodyScale = skinTiles(body, p.k, stretchMax).firstOrNull()?.let { it.dst.height / it.src.height } ?: return@flatMap emptyList()
            val top = lid.dst.y + (lidFrom - lid.src.y) * lidScale
            val fadeBottom = lid.dst.y + (lidTo - lid.src.y) * lidScale
            val bottom = body.dst.y + BODY_SKIP * bodyScale
            val mirrorRows = (bottom - top) / bodyScale
            // the picture runs on below a cut crop row as it shows (its last rows, then the rows under it), so the mirror
            // may reach past the row; a tiled one only within it
            val reach = if (body.tileY) body.src.height else p.spec.height - body.src.y
            if (mirrorRows <= 0f || BODY_SKIP + mirrorRows > reach) return@flatMap emptyList()
            listOf(
                SkinSeamDraw(SkinCell(PxRect(h.src.x, body.src.y + BODY_SKIP, h.src.width, mirrorRows), PxRect(h.dst.x, top, h.dst.width, bottom - top), h.tileX, false), mirror = true, fade = false),
                SkinSeamDraw(SkinCell(PxRect(h.src.x, lidFrom, h.src.width, FADE), PxRect(h.dst.x, top, h.dst.width, fadeBottom - top), h.tileX, false), mirror = false, fade = true),
            )
        }
    }
}
