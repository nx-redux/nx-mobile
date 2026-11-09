package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A scroll that a controller's UP / DOWN step by [step] px a press: counted presses ([seq], [dir] -1 up, +1 down) or a
 * host's stream of them ([steps]). Each press cancels the last animation part-way and heads for a running target
 * instead of from where that animation stopped, so a held key still moves a whole step a press and stops when it is let
 * go (a press never waits behind earlier ones). A touch drag drops the target, so the next press steps from where the
 * finger left it.
 */
@Composable
internal fun rememberStepScroll(step: Float, seq: Int = 0, dir: Int = 0, steps: Flow<Int>? = null): StepScroll {
    val state = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scroll = remember(state, scope, step) { StepScroll(state, scope, step) }
    LaunchedEffect(scroll) {
        state.interactionSource.interactions.collect { if (it is DragInteraction.Start) scroll.dropTarget() }
    }
    LaunchedEffect(seq) { if (seq > 0) scroll.stepBy(dir) }
    LaunchedEffect(steps) { steps?.collect { scroll.stepBy(it) } }
    return scroll
}

/** A [rememberStepScroll] scroll: its [state] for the scrolling content, and the presses and Scroll hint that move it. */
@Stable
internal class StepScroll(val state: ScrollState, private val scope: CoroutineScope, private val step: Float) {
    /** Where the scroll is headed; null while it rests. */
    private var target: Int? = null
    private var job: Job? = null

    /** One press: [dir] -1 up, +1 down. */
    fun stepBy(dir: Int) = go(stepScrollTarget(target ?: state.value, dir, step, state.maxValue))

    /** The Scroll hint's tap: a step down, or back to the top once at the end. */
    fun onScrollHint() = go(scrollHintTarget(target ?: state.value, step, state.maxValue))

    internal fun dropTarget() { target = null }

    private fun go(to: Int) {
        target = to
        job?.cancel()
        job = scope.launch {
            state.animateScrollTo(to)
            // Arrived: the next press starts from the scroll itself (its end may have moved meanwhile).
            target = null
        }
    }
}

/** The scroll [step] px on from [from] toward [dir], within 0..[max]. */
internal fun stepScrollTarget(from: Int, dir: Int, step: Float, max: Int): Int =
    (from + (dir * step).roundToInt()).coerceIn(0, max.coerceAtLeast(0))

/** The Scroll hint's target from [from]: [step] px down, or 0 once at [max]. */
internal fun scrollHintTarget(from: Int, step: Float, max: Int): Int =
    if (from >= max) 0 else stepScrollTarget(from, 1, step, max)
