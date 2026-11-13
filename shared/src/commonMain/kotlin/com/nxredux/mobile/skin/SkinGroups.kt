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
    /** The id of the touch shortcut strip's group (TOUCH_1..4): the frame's strip, or a piece's. */
    const val STRIP = "STRIP"

    /**
     * The groups of [spec]: its button pieces that overlap ([joined]), each with the controls it holds, and the frame strip.
     */
    fun of(spec: SkinLayoutSpec): List<SkinGroup> {
        val idx = spec.pieces.indices.filter { spec.pieces[it].glass == null }
        val rects = idx.associateWith { rect(spec.pieces[it]) }
        // union-find over the pieces by overlapping rects
        val parent = idx.associateWith { it }.toMutableMap()
        fun root(i: Int): Int { var r = i; while (parent.getValue(r) != r) r = parent.getValue(r); return r }
        for (a in idx) for (b in idx) if (a < b && joined(rects.getValue(a), rects.getValue(b))) parent[root(a)] = root(b)
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
        // the touch shortcut strip: the frame's, or a piece's (the large layout's, under its touch screen)
        val stripIds = stripsOf(spec).flatMap { st -> st.labels.map { it.id } }
        val groups = clusters.indices.filter { ids[it].isNotEmpty() }.map { i ->
            SkinGroup(ids[i].joinToString("+"), clusters[i], ids[i].toList(), unions[i], free = false)
        }
        return if (stripIds.isEmpty()) groups else groups + SkinGroup(STRIP, emptyList(), stripIds, stripRef(spec), free = true)
    }

    /** A piece's drawn rect in the render (its [PieceSpec.scale] applied). */
    private fun rect(p: PieceSpec) = PxRect(p.x.toFloat(), p.y.toFloat(), p.w * p.scale, p.h * p.scale)

    /**
     * Two pieces are one cluster when their rects overlap by at least [JOIN_SHARE] of the smaller one: a button in its
     * ring, or pieces drawn as one. A sliver (a bar reaching into the empty corners of a round piece's box) is not.
     */
    private fun joined(a: PxRect, b: PxRect): Boolean {
        val w = minOf(a.x + a.width, b.x + b.width) - maxOf(a.x, b.x)
        val h = minOf(a.y + a.height, b.y + b.height) - maxOf(a.y, b.y)
        if (w <= 0f || h <= 0f) return false
        val smaller = minOf(a.width * a.height, b.width * b.height)
        return smaller > 0f && w * h >= JOIN_SHARE * smaller
    }

    /** The share of the smaller piece two pieces must overlap by to be one cluster ([joined]). */
    private const val JOIN_SHARE = 0.25f

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
        val f = spec.frame
        f?.strip?.let { s ->
            val cy = f.y + f.h - s.y.toFloat()
            return PxRect((f.x + f.glass.x).toFloat(), cy - s.hh, f.glass.w.toFloat(), s.hh * 2f)
        }
        // a piece's strip, across the frame's inside under its glass ([SkinSolver]'s piece strip)
        val p = spec.pieces.firstOrNull { it.strip != null } ?: return PxRect(0f, 0f, 0f, 0f)
        val s = p.strip!!
        val inner = p.glass?.let { maxOf(it.w, p.w - 2 * 20) } ?: p.w
        val x = p.x + (p.glass?.let { (p.w - inner) / 2f } ?: 0f) * p.scale
        val cy = p.y + (p.h - s.y) * p.scale
        return PxRect(x, cy - s.hh * p.scale, inner * p.scale, s.hh * 2f * p.scale)
    }

    /** [spec]'s touch shortcut strips: the frame's, then its pieces' (the order [SkinSolver] places their buttons in). */
    fun stripsOf(spec: SkinLayoutSpec): List<StripSpec> = listOfNotNull(spec.frame?.strip) + spec.pieces.mapNotNull { it.strip }
}
