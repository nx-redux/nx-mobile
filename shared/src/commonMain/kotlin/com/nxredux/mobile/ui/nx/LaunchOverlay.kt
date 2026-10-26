package com.nxredux.mobile.ui.nx

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.runner.LaunchLine
import com.nxredux.mobile.runner.LaunchName
import com.nxredux.mobile.ui.LaunchPhase
import com.nxredux.mobile.ui.LaunchSequence
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/*
 * The launch sequence's overlay (AppState's launchSequence), one for every page: black spreads from the selected name
 * over the whole screen, the name (a copy, on top) turning to the accent, and is the last thing left; the game screen then
 * draws the same name on the same pixels ([LaunchName]). Pages own nothing of it: their selected name reports itself
 * ([LaunchTargets]), read once as a sequence starts. Minimal reports this round; the other layouts launch as before.
 */

/**
 * The selected name a launch starts from: its FULL [text] (never the ellipsized one, no tag), its Text's [layout] and
 * [coords] as last laid out (null while not), its [style], its [color] as drawn and the [scale] it is drawn at (the
 * selection's grow). Read once, as a sequence starts ([LaunchOverlay]) and as the runner is called ([toLaunchName]).
 */
class LaunchTarget(
    val text: AnnotatedString, val layout: () -> TextLayoutResult?, val coords: () -> LayoutCoordinates?,
    val style: TextStyle, val color: Color, val scale: () -> Float,
)

/** The page showing reports its selected name here ([report]; null: none); [current] is the last one. */
class LaunchTargets {
    var current: LaunchTarget? = null
        private set

    fun report(target: LaunchTarget?) { current = target }
}

/** App's [LaunchTargets], for the pages to report to. */
val LocalLaunchTargets = staticCompositionLocalOf { LaunchTargets() }

/** Inline content's stand-ins in a text (a tag's placeholder): never part of the name. */
private val PLACEHOLDERS = charArrayOf('\uFFFD', '\uFFFC')

/**
 * The launch copy's lines in window px: one line (the copy never wraps), [text] (the full name, any tag placeholder
 * dropped) from [lineLeft] on [baseline], both in the Text's own px, drawn at [scale] from its top left at [originInScreen] (its place in the window).
 * None for a blank name.
 */
internal fun launchLines(text: String, lineLeft: Float, baseline: Float, originInScreen: Offset, scale: Float): List<LaunchLine> {
    val name = text.filterNot { it in PLACEHOLDERS }.trim()
    if (name.isEmpty()) return emptyList()
    return listOf(LaunchLine(name, originInScreen.x + lineLeft * scale, originInScreen.y + baseline * scale))
}

/** [launchLines] from the on-screen [layout]'s first line (its start x and baseline): the copy starts where the name does. */
internal fun launchLines(text: String, layout: TextLayoutResult, originInScreen: Offset, scale: Float): List<LaunchLine> =
    launchLines(text, layout.getLineLeft(0), layout.getLineBaseline(0), originInScreen, scale)

/**
 * The name for the game screen to draw on the same pixels, in the [accent] (it ends on it); null while the name isn't laid
 * out. In window px ([LayoutCoordinates.localToWindow]); the game screen's hold takes its own place in its window off, so
 * the two line up whenever the menu's window and the game's sit at the same place on the screen (full screen, or the same
 * freeform / split-screen bounds). Size and spacing after the selection's [LaunchTarget.scale].
 */
fun LaunchTarget.toLaunchName(accent: Color): LaunchName? {
    val layout = layout() ?: return null
    val coords = coords()?.takeIf { it.isAttached } ?: return null
    val s = scale()
    val lines = launchLines(text.text, layout, coords.localToWindow(Offset.Zero), s)
    if (lines.isEmpty()) return null
    val d = layout.layoutInput.density
    val sizePx = with(d) { style.fontSize.toPx() }
    val spacing = style.letterSpacing
    val spacingPx = when (spacing.type) {
        TextUnitType.Em -> spacing.value * sizePx
        TextUnitType.Sp -> with(d) { spacing.toPx() }
        else -> 0f
    }
    return LaunchName(lines, sizePx * s, spacingPx * s, (style.fontWeight ?: FontWeight.Normal) >= FontWeight.SemiBold, accent.toArgb())
}

/**
 * A sequence's start, read once: the copy's [layout] (the full name, one line, unbounded) at [topLeft] drawn at [scale],
 * in the overlay's px; the spread's [centre] (the name's as the player saw it) and [end] radius; the copy's [from] colour
 * and [to] (the accent); [reduced] motion.
 */
private class LaunchSnap(
    val layout: TextLayoutResult?, val topLeft: Offset, val scale: Float, val centre: Offset, val end: Float,
    val from: Color, val to: Color, val reduced: Boolean,
)

/** What the overlay draws this frame: the spread's [radius], the copy's [tint] (0 captured to 1 accent), the black's [black] alpha (reduced motion's fade) and the whole's [fade]. */
private class LaunchAnim {
    var radius by mutableFloatStateOf(0f)
    var tint by mutableFloatStateOf(0f)
    var black by mutableFloatStateOf(0f)
    var fade by mutableFloatStateOf(1f)
}

/** Ticks [tick] with the ms since its first frame, every frame for [ms] (wall-clock: a slow device gets fewer frames, not a longer run). */
private suspend fun frames(ms: Long, tick: (Long) -> Unit) {
    val start = withFrameNanos { it }
    tick(0L)
    while (true) {
        val e = (withFrameNanos { it } - start) / 1_000_000L
        tick(min(e, ms))
        if (e >= ms) return
    }
}

/**
 * The launch sequence over the whole screen ([modifier]'s box: above the tabs, stats, icons and content), for [sequence]
 * (null: nothing). [LaunchPhase.SPREAD]: black opaque inside r, clear by r + [LaunchTimeline.FEATHER_DP], r from 0 to the
 * farthest corner ([launchEndRadius]) over [LaunchTimeline.SPREAD_MS] round the name's centre, the copy turning from its
 * captured colour to the accent ([launchColorFraction]); [LaunchPhase.HANDED_OFF]: held, black with the name; [LaunchPhase.RETREAT]: r back
 * to 0 over [LaunchTimeline.RETREAT_MS], the colour back; [LaunchPhase.RETURNING]: the black (no copy) fades out over
 * [LaunchTimeline.RETURN_FADE_MS]. Under [reducedMotion] no spread: the black fades in over [LaunchTimeline.REDUCED_FADE_MS]
 * under the copy. The name and the motion setting are read once, as the sequence starts ([targets]).
 */
@Composable
fun LaunchOverlay(sequence: LaunchSequence?, targets: LaunchTargets, modifier: Modifier = Modifier, reducedMotion: () -> Boolean = { false }) {
    val density = LocalDensity.current
    val accent = LocalNxColors.current.accent
    val measurer = rememberTextMeasurer()
    val box = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val id = sequence?.id
    val featherPx = with(density) { LaunchTimeline.FEATHER_DP.dp.toPx() }
    val snap = remember(id) {
        if (id == null) return@remember null
        val b = box[0]?.takeIf { it.isAttached }
        val w = b?.size?.width?.toFloat() ?: 0f
        val h = b?.size?.height?.toFloat() ?: 0f
        val target = targets.current
        val at = target?.coords()?.takeIf { it.isAttached && b != null }?.let { b!!.localBoundingBoxOf(it, clipBounds = false) }
        val centre = at?.center ?: Offset(w / 2f, h / 2f)
        val copy = if (target != null && at != null) measurer.measure(AnnotatedString(target.text.text.filterNot { it in PLACEHOLDERS }), target.style.copy(drawStyle = Fill), maxLines = 1, softWrap = false) else null
        LaunchSnap(
            copy, at?.topLeft ?: Offset.Zero, target?.scale() ?: 1f, centre, launchEndRadius(centre.x, centre.y, w, h, featherPx),
            target?.color ?: accent, accent, reducedMotion(),
        )
    }
    val anim = remember(id) { LaunchAnim() }
    val phase by rememberUpdatedState(sequence?.phase)
    LaunchedEffect(id) {
        val s = snap ?: return@LaunchedEffect
        val spread = launch {
            frames(max(LaunchTimeline.SPREAD_MS, LaunchTimeline.COLOR_DELAY_MS + LaunchTimeline.COLOR_MS)) { e ->
                anim.radius = launchRadius(e, s.end)
                anim.tint = launchColorFraction(e)
                anim.black = if (s.reduced) min(1f, e.toFloat() / LaunchTimeline.REDUCED_FADE_MS) else 1f
            }
        }
        snapshotFlow { phase }.collect { p ->
            when (p) {
                LaunchPhase.RETREAT -> {
                    spread.cancel()
                    val r0 = anim.radius; val t0 = anim.tint; val b0 = anim.black
                    // The spread played backwards: its ease-out reversed.
                    frames(LaunchTimeline.RETREAT_MS) { e ->
                        val k = EaseOutCubic.transform(1f - e.toFloat() / LaunchTimeline.RETREAT_MS)
                        anim.radius = r0 * k; anim.tint = t0 * k
                        if (s.reduced) anim.black = b0 * k
                    }
                }
                LaunchPhase.RETURNING -> {
                    spread.cancel()
                    val f0 = anim.fade
                    frames(LaunchTimeline.RETURN_FADE_MS) { e -> anim.fade = f0 * (1f - e.toFloat() / LaunchTimeline.RETURN_FADE_MS) }
                }
                else -> Unit   // SPREAD runs on; HANDED_OFF holds what the spread ended on
            }
        }
    }
    Canvas(modifier.fillMaxSize().onGloballyPositioned { box[0] = it }) {
        val s = snap ?: return@Canvas
        val fade = anim.fade
        if (fade <= 0f) return@Canvas
        if (s.reduced) drawRect(Color.Black, alpha = anim.black * fade)
        else {
            val r = anim.radius
            val outer = r + featherPx
            drawRect(
                Brush.radialGradient(0f to Color.Black, r / outer to Color.Black, 1f to Color.Transparent, center = s.centre, radius = outer),
                alpha = fade,
            )
        }
        // On the way back the black fades alone: the game screen drew the name last, and a copy here would show it twice
        // (or, after a rotation during play, at a stale place).
        if (phase != LaunchPhase.RETURNING) s.layout?.let { copy ->
            scale(s.scale, pivot = s.topLeft) { drawText(copy, color = lerp(s.from, s.to, anim.tint), topLeft = s.topLeft, alpha = fade) }
        }
    }
}
