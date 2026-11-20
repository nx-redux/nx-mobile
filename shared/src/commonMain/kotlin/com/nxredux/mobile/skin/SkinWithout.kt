package com.nxredux.mobile.skin

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The pack with the control [id] taken out of every boxed layout ([BoxSize.withoutControl]): the DS / 3DS pads with
 * the mic button hidden. A layout without boxes (the wide ones) loses the control and its cap, its well left as drawn.
 */
fun SkinPackManifest.withoutControl(id: String): SkinPackManifest = copy(
    layouts = layouts.map { l ->
        if (l.controls.none { it.id == id } && l.boxes.all { b -> b.sizes.all { z -> z.controls.none { it.id == id } } }) l
        else l.copy(
            controls = l.controls.filter { it.id != id },
            pieces = l.controls.firstOrNull { it.id == id }?.let { gone -> l.pieces.filter { capHolds(it, gone) == false } } ?: l.pieces,
            boxes = l.boxes.map { b -> b.copy(sizes = b.sizes.map { it.withoutControl(id) }) },
        )
    },
)

/**
 * This drawing with the control [id] gone and its row closed up: the mates in its group (the group named with it, as
 * `MENU+MIC+SWAP`) slide in by one button's pitch from each side, half each way, so the row keeps its middle; [id]'s
 * cap pieces go; the well (the group's other pieces) loses a pitch's length at its middle, a stretched slice by
 * drawing narrower, art by cutting out a strip and keeping the two ends. A row when the mates share its height, else a
 * column. Unchanged (the same object) when it has no such control.
 */
fun BoxSize.withoutControl(id: String): BoxSize {
    val gone = controls.firstOrNull { it.id == id } ?: return this
    val inGroup = pieces.filter { p -> p.group?.split('+')?.contains(id) == true }
    val mateIds = inGroup.firstOrNull()?.group?.split('+')?.filter { it != id }.orEmpty().toSet()
    val mates = controls.filter { it.id in mateIds }
    if (mates.isEmpty()) return copy(pieces = pieces - inGroup.toSet(), controls = controls - gone)
    val across = mates.all { abs(it.y - gone.y) < 1f }
    fun along(c: ControlSpec) = if (across) c.x else c.y
    val pitch = mates.minOf { abs(along(it) - along(gone)) }
    val half = pitch / 2f

    // a cap: square art (the well's ends are half as wide as tall, its middles stretched) holding one of the group's buttons
    fun holds(p: PieceSpec, c: ControlSpec): Boolean {
        val w = p.w * p.acrossScale
        val h = p.h * p.scale
        return c.x > p.x && c.x < p.x + w && c.y > p.y && c.y < p.y + h
    }
    val group = mates + gone
    fun capOf(p: PieceSpec): ControlSpec? {
        if (p.scaleX > 0f || abs(p.w - p.h) > minOf(p.w, p.h) / 5) return null
        return group.filter { holds(p, it) }.singleOrNull()
    }
    fun moved(c: ControlSpec): Float = if (along(c) < along(gone)) half else -half

    val wells = inGroup.filter { capOf(it) == null }
    fun start(p: PieceSpec) = (if (across) p.x else p.y).toFloat()
    fun length(p: PieceSpec) = if (across) p.w * p.acrossScale else p.h * p.scale
    val middle = if (wells.isEmpty()) along(gone) else (wells.minOf { start(it) } + wells.maxOf { start(it) + length(it) }) / 2f
    val cutA = middle - half
    val cutB = middle + half
    fun close(v: Float) = (if (v <= cutA) v else if (v >= cutB) v - pitch else cutA) + half

    return copy(
        // in their drawing order
        pieces = pieces.flatMap { p ->
            val cap = if (p in inGroup) capOf(p) else null
            when {
                p !in inGroup -> listOf(p)
                cap === gone -> emptyList()
                cap != null -> listOf(p.shifted(across, moved(cap)))
                else -> cut(p, across, ::close, cutA, cutB)
            }
        },
        controls = controls.filter { it !== gone }.map { c ->
            if (c.id !in mateIds) c else if (across) c.copy(x = c.x + moved(c)) else c.copy(y = c.y + moved(c))
        },
    )
}

/**
 * Whether [p] is [c]'s cap in a layout without boxes: square art on the same anchor holding [c]'s centre (the well's
 * ends are half as wide as tall, its middle stretched).
 */
private fun capHolds(p: PieceSpec, c: ControlSpec): Boolean =
    p.glass == null && p.scaleX == 0f && abs(p.w - p.h) <= minOf(p.w, p.h) / 5 && p.anchor == (c.anchor ?: p.anchor) &&
        c.x > p.x && c.x < p.x + p.w * p.scale && c.y > p.y && c.y < p.y + p.h * p.scale

private fun PieceSpec.shifted(across: Boolean, by: Float): PieceSpec =
    if (across) copy(x = (x + by).roundToInt()) else copy(y = (y + by).roundToInt())

/** A well piece with the strip [cutA]..[cutB] (box px along the row) taken out, through [close]. */
private fun cut(p: PieceSpec, across: Boolean, close: (Float) -> Float, cutA: Float, cutB: Float): List<PieceSpec> {
    val s = if (across) p.acrossScale else p.scale
    val a = (if (across) p.x else p.y).toFloat()
    val b = a + (if (across) p.w else p.h) * s
    if (b <= cutA || a >= cutB) return listOf(p.shifted(across, close(a) - a))
    if (across && p.scaleX > 0f) {
        // a stretched slice: drawn narrower; cut away whole, gone (a scaleX of 0 would mean "unset", its natural width)
        val na = close(a)
        val across = (close(b) - na) / p.w
        return if (across * p.w < 0.5f) emptyList() else listOf(p.copy(x = na.roundToInt(), scaleX = across))
    }
    // art: the parts outside the strip kept, in the picture's own px
    val out = mutableListOf<PieceSpec>()
    if (a < cutA) {
        val n = ((cutA - a) / s).roundToInt()
        out += if (across) p.copy(w = n, x = close(a).roundToInt()) else p.copy(h = n, y = close(a).roundToInt())
    }
    if (b > cutB) {
        val skip = ((cutB - a) / s).roundToInt()
        val n = (if (across) p.w else p.h) - skip
        out += if (across) p.copy(sx = p.sx + skip, w = n, x = close(cutB).roundToInt())
            else p.copy(sy = p.sy + skip, h = n, y = close(cutB).roundToInt())
    }
    return out
}
