package com.nxredux.mobile.runner

import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The 3DS 3D Resolution loop end to end, simulated: Azahar applies a sent factor and a layout switch some frames late
 * (each its own delay), the app derives the factor from the frame ([N3ds3dResolution.frameFactor]), holds its readings
 * while a layout switch's frame is awaited (until the frame size next changes) and sends through [Nds3dSender]. A layout
 * frame lands between a send and its frame. Whatever the delays, it settles in at most one send after the layout frame
 * arrives and never cycles, and the old layout's frame alone never starts a send.
 */
class N3ds3dSettleTest {
    private data class Shape(val w: Int, val h: Int)

    private val stacked = Shape(400, 480)
    private val sideBySide = Shape(720, 240)
    private val large = Shape(400, 360)

    /** The picture width a frame of [shape] gets in a [spaceW] x [spaceH] space (fitted at its aspect). */
    private fun pictureWidth(shape: Shape, spaceW: Float, spaceH: Float) = min(spaceW, spaceH * shape.w / shape.h)

    private class Pending(val atStep: Int, val factor: Int?, val shape: Shape?)

    /**
     * Sends made before and after the layout frame arrived; fails on a send in the last 20 steps (still moving: a cycle).
     */
    private fun run(
        values: Map<String, String>, displayMax: Int, launch: Int, inFlight: Int,
        from: Shape, to: Shape, space: Pair<Float, Float>, sendDelay: Int, layoutDelay: Int,
    ): Pair<Int, Int> {
        var shape = from
        var factor = launch
        var derived = launch
        val sender = Nds3dSender()
        sender.offer(launch, sizeChange = false, nowMs = 0)
        val pending = mutableListOf<Pending>()
        // A send in flight at the switch: its frame comes after the switch, before or after the layout frame.
        sender.offer(inFlight, sizeChange = false, nowMs = 0)?.let { pending += Pending(sendDelay, it, null) }
        pending += Pending(layoutDelay, null, to)   // the rotation's layout switch
        var watching = true                         // the layout switch's geometry watch
        var layoutArrivedAt: Int? = null
        var sendsBefore = 0
        var sendsAfter = 0
        val steps = 80
        for (step in 1..steps) {
            val before = shape.w * factor to shape.h * factor
            pending.filter { it.atStep == step }.forEach { p -> p.factor?.let { factor = it }; p.shape?.let { shape = it } }
            pending.removeAll { it.atStep == step }
            val frame = shape.w * factor to shape.h * factor
            if (frame != before) watching = false
            if (shape == to && layoutArrivedAt == null && step >= layoutDelay) layoutArrivedAt = step
            if (watching) continue
            N3ds3dResolution.frameFactor(frame.first.toFloat(), frame.second.toFloat())?.let { derived = it }
            val current = N3ds3dResolution.currentScale(pictureWidth(shape, space.first, space.second), frame.first.toFloat(), derived)
            val value = Nds3dResolution.valueFor(values, current, displayMax, Res3dConsole.N3DS)
            sender.offer(value, sizeChange = false, nowMs = step.toLong())?.let {
                if (step > steps - 20) fail("still sending at step $step: a cycle")
                if (layoutArrivedAt != null) sendsAfter++ else sendsBefore++
                pending += Pending(step + sendDelay, it, null)
            }
        }
        return sendsBefore to sendsAfter
    }

    private fun check(values: Map<String, String>, displayMax: Int) {
        val spaces = listOf(1848f to 2448f, 2448f to 1848f, 1080f to 2400f, 2400f to 1080f, 904f to 2316f, 2316f to 904f)
        for (space in spaces) for ((from, to) in listOf(stacked to sideBySide, sideBySide to stacked, stacked to large, large to stacked))
            for (launch in 1..displayMax) for (inFlight in 1..displayMax) for (sendDelay in 1..3) for (layoutDelay in 1..4) {
                val (before, after) = run(values, displayMax, launch, inFlight, from, to, space, sendDelay, layoutDelay)
                val case = "$space $from->$to launch $launch in flight $inFlight delays $sendDelay/$layoutDelay"
                assertTrue(after <= 1, "$after sends after the layout settled: $case")
                // Nothing in flight: the old layout's frame, held, never starts a send.
                if (inFlight == launch) assertTrue(before == 0, "$before sends from the old layout's frame: $case")
            }
    }

    @Test
    fun qualityAtDisplayMax6Settles() = check(mapOf(Res3dConsole.N3DS.modeKey to Nds3dMode.QUALITY.value), 6)

    @Test
    fun qualityAtDisplayMax8Settles() = check(mapOf(Res3dConsole.N3DS.modeKey to Nds3dMode.QUALITY.value), 8)

    @Test
    fun autoCappedAt3Settles() = check(mapOf(Res3dConsole.N3DS.modeKey to Nds3dMode.AUTO.value), 6)
}
