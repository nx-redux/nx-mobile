package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect

/**
 * One group of a layout's buttons: the [pieces] (indexes into [SkinLayoutSpec.pieces]) that touch, the [controls] (ids)
 * inside them, and [ref], their union in picture px. [id] is the control ids sorted and joined with `+`, or
 * [SkinGroups.STRIP]. [free]: the group has no pieces to measure by (the frame's strip).
 */
data class SkinGroup(
    val id: String,
    val pieces: List<Int>,
    val controls: List<String>,
    val ref: PxRect,
    val free: Boolean,
)

object SkinGroups {
    /** The id of the frame strip's group (SAVE LOAD REWIND FAST, or TOUCH_1..4 on a DS layout). */
    const val STRIP = "STRIP"

    /** The groups of [spec]: its button pieces that overlap or touch, each with the controls it holds, and the frame strip. */
    fun of(spec: SkinLayoutSpec): List<SkinGroup> {
        val idx = spec.pieces.indices.filter { spec.pieces[it].glass == null }
        val rects = idx.associateWith { rect(spec.pieces[it]) }
        // union-find over the pieces by touching rects
        val parent = idx.associateWith { it }.toMutableMap()
        fun root(i: Int): Int { var r = i; while (parent.getValue(r) != r) r = parent.getValue(r); return r }
        for (a in idx) for (b in idx) if (a < b && touch(rects.getValue(a), rects.getValue(b))) parent[root(a)] = root(b)
        val clusters = idx.groupBy { root(it) }.values.map { it.sorted() }.sortedBy { it.first() }
        val unions = clusters.map { c -> union(c.map { rects.getValue(it) }) }
        val ids = Array(clusters.size) { sortedSetOf<String>() }
        for (c in spec.controls) {
            // a control belongs to the cluster holding its centre in one of its pieces, else in the union, else (a few
            // layouts draw START beside its piece) to the cluster whose piece is nearest
            val at = clusters.indexOfFirst { cl -> cl.any { contains(rects.getValue(it), c.x, c.y) } }
                .takeIf { it >= 0 } ?: unions.indexOfFirst { contains(it, c.x, c.y) }.takeIf { it >= 0 }
                ?: clusters.indices.minByOrNull { i -> clusters[i].minOf { gap(rects.getValue(it), c.x, c.y) } } ?: -1
            if (at >= 0) ids[at].add(c.id)
        }
        val stripIds = spec.frame?.strip?.labels?.map { it.id }.orEmpty()
        val groups = clusters.indices.filter { ids[it].isNotEmpty() }.map { i ->
            SkinGroup(ids[i].joinToString("+"), clusters[i], ids[i].toList(), unions[i], free = false)
        }
        return if (stripIds.isEmpty()) groups else groups + SkinGroup(STRIP, emptyList(), stripIds, stripRef(spec), free = true)
    }

    /** A piece's drawn rect in the render (its [PieceSpec.scale] applied). */
    private fun rect(p: PieceSpec) = PxRect(p.x.toFloat(), p.y.toFloat(), p.w * p.scale, p.h * p.scale)

    private fun touch(a: PxRect, b: PxRect) =
        a.x <= b.x + b.width && b.x <= a.x + a.width && a.y <= b.y + b.height && b.y <= a.y + a.height

    private fun contains(r: PxRect, x: Float, y: Float) = x >= r.x && x <= r.x + r.width && y >= r.y && y <= r.y + r.height

    private fun gap(r: PxRect, x: Float, y: Float): Float {
        val dx = maxOf(r.x - x, 0f, x - (r.x + r.width)); val dy = maxOf(r.y - y, 0f, y - (r.y + r.height))
        return dx * dx + dy * dy
    }

    private fun union(rs: List<PxRect>): PxRect {
        val l = rs.minOf { it.x }; val t = rs.minOf { it.y }
        return PxRect(l, t, rs.maxOf { it.x + it.width } - l, rs.maxOf { it.y + it.height } - t)
    }

    /** The strip's place in the render: [StripSpec.y] above the frame's bottom, across its glass (as the solver shares it out), [StripSpec.hh] each way tall. */
    private fun stripRef(spec: SkinLayoutSpec): PxRect {
        val f = spec.frame ?: return PxRect(0f, 0f, 0f, 0f)
        val s = f.strip ?: return PxRect(0f, 0f, 0f, 0f)
        val cy = f.y + f.h - s.y.toFloat()
        return PxRect((f.x + f.glass.x).toFloat(), cy - s.hh, f.glass.w.toFloat(), s.hh * 2f)
    }
}
