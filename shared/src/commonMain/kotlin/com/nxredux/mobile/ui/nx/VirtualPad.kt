package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.nxredux.mobile.settings.PadGroup
import com.nxredux.mobile.settings.PadLayout
import com.nxredux.mobile.settings.PadButtons
import com.nxredux.mobile.settings.PadTheme
import com.nxredux.mobile.shortcuts.TouchSlot
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.pad_mic
import com.nxredux.mobile.shared.res.pad_ps_circle
import com.nxredux.mobile.shared.res.pad_ps_cross
import com.nxredux.mobile.shared.res.pad_ps_square
import com.nxredux.mobile.shared.res.pad_ps_triangle
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Neumorphic ("soft UI") pad styling, drawn entirely in code and inspired — for the look only, not
 * the artwork — by retro-device controller skins: a flat body into which each button is countersunk.
 * Depth comes from a "socket" groove ringing every button (shaded like a carved wall), with a domed,
 * top-lit face raised inside it — no outer drop shadow, so the body stays flat. Pressing a button is a
 * natural depression: the face sinks deeper into the socket and darkens with an inner top shadow.
 * The shapes and insets live here, shared by every theme; the colours live in [PadColors].
 */
private object PadStyle {
    val faceShape: Shape = CircleShape                  // A / B / X / Y face buttons
    val pillShape: Shape = RoundedCornerShape(50)       // shoulders + select / start / menu / toggle

    val faceInset: Dp = 3.dp        // resting: how far the face sits inside the socket
    val pressedInset: Dp = 5.dp     // pressed: sink the face a little deeper
    val faceLift: Dp = 0.75.dp      // shift the face up slightly so the groove looks even top-to-bottom
    val outlineWidth: Dp = 1.5.dp   // an outline button's stroke ([PadColors.outline]): thin, but not a hairline
}

/** The A / B / X / Y face buttons, which take the [PadColors.face] role; every other pad button is a [PadColors.pill]. */
private val FACE_BUTTONS = setOf(NxButton.A, NxButton.B, NxButton.X, NxButton.Y)

/**
 * The buttons drawn as pills (shoulders, SELECT / START, MENU), whose labels get their pill's width as room
 * ([pillLabelScale]); the rest are round ([faceLabelScale]). Decided by button, not by shape: [PadStyle.faceShape] and
 * [PadStyle.pillShape] are both 50% rounded and compare equal.
 */
internal fun isPill(button: NxButton): Boolean = button in PILL_BUTTONS
private val PILL_BUTTONS = setOf(NxButton.L1, NxButton.L2, NxButton.R1, NxButton.R2, NxButton.START, NxButton.SELECT, NxButton.MENU, NxButton.L3, NxButton.R3)

/**
 * One button role's raised face: a domed resting fill and rim, and the darker sunken fill and rim shown while held.
 * [tint]: a console's own face colour ([coloredFace]), which an outline pad strokes the button in ([PadColors.faceOf]).
 */
@Immutable
internal class PadFace(val raisedFill: Brush, val raisedRim: Brush, val pressedFill: Brush, val pressedRim: Brush, val tint: Color? = null)

/**
 * Every colour of the pad, per theme ([CharcoalPadColors], [RetroPadColors]). Buttons differ by role: [face] is
 * A / B / X / Y, [dpad] the d-pad cross and joystick knob, [pill] the shoulders, SELECT / START / MENU and the
 * d-pad / stick toggle. The engrave colours are the NX mark cut into the body ([NxEmblem]).
 */
@Immutable
internal class PadColors(
    /** The pad "body" the buttons are countersunk into. */
    val surface: Color,
    val face: PadFace,
    val dpad: PadFace,
    val pill: PadFace,
    /** The countersink groove around each button, kept an even tone so it reads as the same width all round. */
    val socketFill: Color,
    /** The groove's rim: a lit lower edge (Retro also shades the top lip), so it catches light along its bottom like the joystick well. */
    val socketRim: Brush,
    /** The joystick's outer well: a shallow bowl lit from the top (shadowed top wall, lit bottom wall). */
    val wellFill: Brush,
    val wellRim: Brush,
    /** The knob's soft drop shadow on the well floor (light from the top → shadow below). */
    val knobShadow: Brush,
    val label: Color,
    val engraveLitLip: Color,       // lower edge of the NX mark's cut, catching the light
    val engraveInnerShadow: Color,  // upper wall of the cut, in shadow
    val engraveFloor: Color,        // bottom of the groove, darker than the surface
    /**
     * An outline pad (Black's Outline buttons): the faces, well and knob are strokes ([PadStyle.outlineWidth]) on no
     * fill, with no socket, and sit centred (no [PadStyle.faceLift], which only evens out a groove).
     */
    val outline: Boolean = false,
    /**
     * A body as dark as the screen (Black): a hairline in this colour along its top edge, under a soft shadow it casts
     * up onto the screen ([padTopEdge]); null, the body's own tone parts it.
     */
    val topEdge: Color? = null,
)

/** The width of a face's, the d-pad cross's and the well's rim. */
private val PadColors.rimWidth: Dp get() = if (outline) PadStyle.outlineWidth else 1.dp

/** How far a face sits up in its socket; none on an outline pad, which has no groove to even out. */
private val PadColors.lift: Dp get() = if (outline) 0.dp else PadStyle.faceLift

/**
 * A console's own face colours ([override]: the N64's, Dreamcast's, GameCube's) on this pad: as they are, or on an
 * outline pad an outline in the face's [PadFace.tint] (the theme's own face if it has none), so the colours still tell
 * the buttons apart without a filled button on the outline pad.
 */
internal fun PadColors.faceOf(override: PadFace): PadFace =
    if (!outline) override else override.tint?.let { outlineFace(it, pressedFill = it.copy(alpha = 0.3f), pressedRim = it) } ?: face   // role: an outline pad button's pressed fill

/** The label colour on a button drawn with [override]'s face and [labelColor]: on an outline pad, the face's tint (a dark letter meant for a filled yellow face would vanish on black). */
internal fun PadColors.labelOf(override: PadFace?, labelColor: Color?): Color =
    (if (outline) override?.tint else null) ?: labelColor ?: label

/** An outline face: no fill at rest, a [stroke] rim; held, a faint [pressedFill] and a [pressedRim]. */
private fun outlineFace(stroke: Color, pressedFill: Color, pressedRim: Color) = PadFace(
    raisedFill = SolidColor(Color.Transparent),
    raisedRim = SolidColor(stroke),
    pressedFill = SolidColor(pressedFill),
    pressedRim = SolidColor(pressedRim),
)

/** The Charcoal face: one domed, top-lit grey for every role (the pad's original look). */
private val CharcoalFace = PadFace(
    // Raised: a domed face — bright highlight up top falling off fast to a dark base — with a strong
    // top rim highlight so it pops out of the dark socket.
    raisedFill = Brush.verticalGradient(listOf(PadPalettes.Charcoal.faceTop, PadPalettes.Charcoal.faceMid, PadPalettes.Charcoal.faceBottom)),
    raisedRim = Brush.verticalGradient(listOf(PadPalettes.Charcoal.faceRimTop, PadPalettes.whiteClear)),
    // Pressed: the face sinks deeper (a larger inset) and darkens with an inner top shadow — a natural
    // depression rather than a glow.
    pressedFill = Brush.verticalGradient(listOf(PadPalettes.Charcoal.facePressedTop, PadPalettes.Charcoal.facePressedBottom)),
    pressedRim = Brush.verticalGradient(listOf(PadPalettes.pressedRimTop, PadPalettes.blackClear)),
)

/** Charcoal: a flat mid-dark charcoal body (never pure black, so the soft shadows read) with dark sockets. The default. */
internal val CharcoalPadColors = PadColors(
    surface = PadPalettes.Charcoal.surface,
    face = CharcoalFace,
    dpad = CharcoalFace,
    pill = CharcoalFace,
    socketFill = PadPalettes.Charcoal.socketFill,
    socketRim = Brush.verticalGradient(listOf(PadPalettes.whiteClear, PadPalettes.Charcoal.socketRimBottom)),
    wellFill = Brush.verticalGradient(listOf(PadPalettes.Charcoal.wellTop, PadPalettes.Charcoal.wellMid, PadPalettes.Charcoal.wellBottom)),
    wellRim = Brush.verticalGradient(listOf(PadPalettes.Charcoal.wellRimTop, PadPalettes.Charcoal.wellRimBottom)),
    // Kept light so it grounds the knob without swallowing its bottom edge.
    knobShadow = Brush.radialGradient(0f to PadPalettes.Charcoal.knobShadowCore, 0.8f to PadPalettes.Charcoal.knobShadowMid, 1f to PadPalettes.blackClear),
    label = PadPalettes.Charcoal.label,      // light grey caps
    engraveLitLip = PadPalettes.Charcoal.engraveLitLip,
    engraveInnerShadow = PadPalettes.Charcoal.engraveInnerShadow,
    engraveFloor = PadPalettes.Charcoal.engraveFloor,
)

/**
 * Retro: a warm light-grey body, like a classic handheld's plastic, with a near-black d-pad and knob, deep magenta
 * A / B / X / Y and Charcoal-face pills. The sockets are a near-dark warm gap (the shadowed slit between button and
 * shell cut-out), not black, rimmed like the joystick well: a shadowed top lip and a lit lower edge.
 */
internal val RetroPadColors = PadColors(
    surface = PadPalettes.Retro.surface,
    face = PadFace(
        raisedFill = Brush.verticalGradient(listOf(PadPalettes.Retro.faceTop, PadPalettes.Retro.faceMid, PadPalettes.Retro.faceBottom)),
        raisedRim = Brush.verticalGradient(listOf(PadPalettes.softRimTop, PadPalettes.whiteClear)),
        pressedFill = Brush.verticalGradient(listOf(PadPalettes.Retro.facePressedTop, PadPalettes.Retro.facePressedBottom)),
        pressedRim = Brush.verticalGradient(listOf(PadPalettes.pressedRimTop, PadPalettes.blackClear)),
    ),
    dpad = PadFace(
        raisedFill = Brush.verticalGradient(listOf(PadPalettes.Retro.dpadTop, PadPalettes.Retro.dpadMid, PadPalettes.Retro.dpadBottom)),
        raisedRim = Brush.verticalGradient(listOf(PadPalettes.Retro.dpadRimTop, PadPalettes.whiteClear)),
        pressedFill = Brush.verticalGradient(listOf(PadPalettes.Retro.dpadPressedTop, PadPalettes.Retro.dpadPressedBottom)),
        pressedRim = Brush.verticalGradient(listOf(PadPalettes.Retro.dpadPressedRimTop, PadPalettes.blackClear)),
    ),
    pill = CharcoalFace,            // the Charcoal theme's button face, shared so the two stay in sync
    socketFill = PadPalettes.Retro.socketFill,
    // The well's rim treatment (shadowed top lip, lit lower edge). The lit stop is stronger than the well's #80FFFFFF
    // so that over the darker groove it lands on the same tone as the well's lit edge (about #E7E5E1).
    socketRim = Brush.verticalGradient(listOf(PadPalettes.Retro.socketRimTop, PadPalettes.Retro.socketRimBottom)),
    wellFill = Brush.verticalGradient(listOf(PadPalettes.Retro.wellTop, PadPalettes.Retro.wellMid, PadPalettes.Retro.wellBottom)),
    wellRim = Brush.verticalGradient(listOf(PadPalettes.Retro.wellRimTop, PadPalettes.Retro.wellRimBottom)),
    knobShadow = Brush.radialGradient(0f to PadPalettes.Retro.knobShadowCore, 0.8f to PadPalettes.Retro.knobShadowMid, 1f to PadPalettes.blackClear),
    label = PadPalettes.Retro.label,      // light caps on the dark, magenta and grey buttons
    engraveLitLip = PadPalettes.Retro.engraveLitLip,
    engraveInnerShadow = PadPalettes.Retro.engraveInnerShadow,
    engraveFloor = PadPalettes.Retro.engraveFloor,
)

/** Black's body, shared by both button sets. */
private val BLACK_SURFACE = PadPalettes.Black.surface
/** Black's caps: Charcoal's light grey, which reads on black without glaring. */
private val BLACK_LABEL = PadPalettes.Black.label
/** The Outline set's stroke: a mid grey, quiet on black but clear of it. */
private val BLACK_STROKE = PadPalettes.Black.stroke
/** The Outline set's held stroke: brighter, with a faint white fill, so a press shows without a glow. */
private val BLACK_STROKE_PRESSED = PadPalettes.Black.strokePressed
private val BLACK_PRESSED_FILL = PadPalettes.Black.pressedFill

/**
 * Black, Grey buttons: Charcoal's domed grey buttons on a pure black body. Black has no darker tone for a groove, so
 * a socket is the body's black and shows only Charcoal's lit lower edge, with no grey halo; the joystick well is a
 * faint bowl rising out of black. The NX mark is a flat subtle grey, not cut: no lit lip or shadow, which read oddly on black.
 */
internal val BlackGreyPadColors = PadColors(
    surface = BLACK_SURFACE,
    face = CharcoalFace,
    dpad = CharcoalFace,
    pill = CharcoalFace,
    socketFill = BLACK_SURFACE,
    socketRim = CharcoalPadColors.socketRim,
    wellFill = Brush.verticalGradient(listOf(PadPalettes.Black.wellTop, PadPalettes.Black.wellMid, PadPalettes.Black.wellBottom)),
    wellRim = CharcoalPadColors.wellRim,
    knobShadow = CharcoalPadColors.knobShadow,
    label = BLACK_LABEL,
    engraveLitLip = Color.Transparent,
    engraveInnerShadow = Color.Transparent,
    engraveFloor = PadPalettes.Black.engraveFloor,
    topEdge = PadPalettes.Black.topEdge,
)

/**
 * Black, Outline buttons: every button, the d-pad cross, the joystick well and knob only a thin mid-grey stroke with
 * its label, on a pure black body with no sockets; a held button lights its stroke and fills faintly. The d-pad's held
 * arm fills without a rim of its own (the cross's outline is its edge).
 */
internal val BlackOutlinePadColors = PadColors(
    surface = BLACK_SURFACE,
    face = outlineFace(BLACK_STROKE, BLACK_PRESSED_FILL, BLACK_STROKE_PRESSED),
    dpad = outlineFace(BLACK_STROKE, BLACK_PRESSED_FILL, Color.Transparent),
    pill = outlineFace(BLACK_STROKE, BLACK_PRESSED_FILL, BLACK_STROKE_PRESSED),
    socketFill = Color.Transparent,
    socketRim = SolidColor(Color.Transparent),
    wellFill = SolidColor(Color.Transparent),
    wellRim = SolidColor(BLACK_STROKE),
    knobShadow = SolidColor(Color.Transparent),
    label = BLACK_LABEL,
    engraveLitLip = BlackGreyPadColors.engraveLitLip,
    engraveInnerShadow = BlackGreyPadColors.engraveInnerShadow,
    engraveFloor = BlackGreyPadColors.engraveFloor,
    outline = true,
    topEdge = PadPalettes.Black.topEdge,
)

/** How far up the screen a black body's shadow reaches ([PadColors.topEdge]). */
private val PAD_TOP_SHADOW = 16.dp

/**
 * [edge] (a black body's): the hairline along the top edge over the body, and the shadow the body casts up onto the
 * screen above it ([PAD_TOP_SHADOW], drawn outside the pad's bounds); null draws nothing.
 */
private fun Modifier.padTopEdge(edge: Color?): Modifier = if (edge == null) this else drawWithContent {
    drawContent()
    val reach = PAD_TOP_SHADOW.toPx()
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, PadPalettes.Black.topShadow), startY = -reach, endY = 0f),
        topLeft = Offset(0f, -reach), size = Size(size.width, reach),
    )
    drawRect(edge, size = Size(size.width, 1.dp.toPx()))
}

/** The pad colours in effect; Charcoal unless a [PadThemeProvider] says otherwise (so [NxEmblem] elsewhere stays Charcoal). */
internal val LocalPadColors = staticCompositionLocalOf { CharcoalPadColors }

/** [theme]'s colours; [buttons] picks Black's button set and is ignored by the other themes. */
internal fun padColorsFor(theme: PadTheme, buttons: PadButtons = PadButtons.DEFAULT): PadColors = when (theme) {
    PadTheme.CHARCOAL -> CharcoalPadColors
    PadTheme.RETRO -> RetroPadColors
    PadTheme.BLACK -> when (buttons) {
        PadButtons.GREY -> BlackGreyPadColors
        PadButtons.OUTLINE -> BlackOutlinePadColors
    }
}

/**
 * Draws the portrait [VirtualPad] in [content] with [theme]'s colours ([buttons]: Black's button set). The landscape
 * clusters ignore it (always Charcoal).
 */
@Composable
fun PadThemeProvider(theme: PadTheme, buttons: PadButtons = PadButtons.DEFAULT, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPadColors provides padColorsFor(theme, buttons), content = content)
}

/**
 * A "+"-shaped d-pad outline — a vertical and a horizontal rounded bar, each one-third the box wide —
 * pulled in uniformly by [insetPx] on every edge. A constant inset (rather than a scaled-down cross)
 * keeps the socket groove the same width all the way round; 0 gives the full outer cross.
 */
private fun crossShape(insetPx: Float): Shape = GenericShape { size, _ ->
    val t = size.width / 3f
    val r = t * 0.28f
    val i = insetPx
    addRoundRect(RoundRect(t + i, i, 2f * t - i, size.height - i, CornerRadius(r, r)))
    addRoundRect(RoundRect(i, t + i, size.width - i, 2f * t - i, CornerRadius(r, r)))
}

/** The full d-pad cross (the socket outline). */
private val dpadCrossShape: Shape = crossShape(0f)

/** A soft, extruded (or, when [pressed], sunken) surface in [shape] carrying [content]. */
@Composable
private fun NeuSurface(
    shape: Shape,
    face: PadFace,
    pressed: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = LocalPadColors.current
    val inset = if (pressed) PadStyle.pressedInset else PadStyle.faceInset
    Box(modifier, contentAlignment = Alignment.Center) {
        // Flat body: the countersink groove carries the depth, no outer halo.
        if (!colors.outline) Box(Modifier.matchParentSize().background(colors.socketFill, shape).border(1.dp, colors.socketRim, shape))
        // The raised face; on press it sinks deeper (larger inset) and darkens with an inner shadow.
        // The face sits [faceLift] higher in the socket so the groove looks even top-to-bottom.
        Box(
            Modifier.matchParentSize()
                .padding(start = inset, top = inset - colors.lift, end = inset, bottom = inset + colors.lift)
                .background(if (pressed) face.pressedFill else face.raisedFill, shape)
                .border(colors.rimWidth, if (pressed) face.pressedRim else face.raisedRim, shape),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/**
 * How far past its touch box a held button stays held, as a share of the button's height (a face cluster's: of one face
 * button), so a thumb drifting while it holds does not drop the button.
 */
private const val PAD_SLOP = 0.5f

/** The d-pad's dead zone round its centre, as a share of one arm's width (a third of the cross). */
private const val DPAD_DEAD_ZONE = 0.25f

/**
 * The shared press state of a set of pad buttons (a face cluster, the d-pad, one lone button): counts the pointers on
 * each button ([PadPressCount]) so each sends one Down and one Up, and holds what is pressed for drawing. [haptic] is
 * the pad's tick ([rememberPadHaptic]), which [padPointers] plays after an event's Downs went out ([padHapticTick]), so
 * the press does not wait on the vibrator.
 */
@Stable
private class PadPressGroup(private val emit: (PadEvent) -> Unit, val haptic: () -> Unit) {
    private val count = PadPressCount()
    var pressed by mutableStateOf(emptySet<NxButton>())
        private set

    /** One pointer goes from holding [old] to holding [new]. */
    fun move(old: Set<NxButton>, new: Set<NxButton>) {
        if (old == new) return
        count.move(old, new, emit)
        pressed = count.pressed
    }
}

@Composable
private fun rememberPressGroup(emit: (PadEvent) -> Unit): PadPressGroup {
    val haptic = rememberPadHaptic()
    val latestEmit by rememberUpdatedState(emit)
    return remember { PadPressGroup({ latestEmit(it) }, haptic) }
}

/**
 * Tracks every pointer that lands on this element, through the whole of its press. [resolve] maps a pointer's position
 * (this element's px), what it held and whether it just went down to what it holds now, on its down and on every move;
 * [group] turns the changes into Down / Up. Every Down gets its Up: each pointer's hold is released when it lifts, when
 * the gesture is cancelled, and when the element leaves composition (e.g. rotate to landscape: the finally block).
 * [key] restarts the handler when the geometry [resolve] reads changes.
 */
private fun Modifier.padPointers(
    group: PadPressGroup,
    key: Any?,
    resolve: (position: Offset, held: Set<NxButton>, down: Boolean) -> Set<NxButton>,
) = pointerInput(group, key) {
    awaitEachGesture {
        val holds = mutableMapOf<PointerId, Set<NxButton>>()
        try {
            do {
                val event = awaitPointerEvent()
                var tick = false
                for (change in event.changes) {
                    val old = holds[change.id] ?: emptySet()
                    val new = if (change.pressed) resolve(change.position, old, change.changedToDown()) else emptySet()
                    group.move(old, new)
                    tick = tick || padHapticTick(old, new)
                    if (change.pressed) holds[change.id] = new else holds.remove(change.id)
                }
                if (tick) group.haptic()   // once per event, after its Downs went out
            } while (event.changes.any { it.pressed })
        } finally {
            for (old in holds.values) group.move(old, emptySet())
        }
    }
}

/**
 * A face cluster's shared touch: its [group], its buttons' [boxes] in the cluster's px, and the [slop] a held button
 * keeps. Each button's element tracks the pointers that land on it across the whole cluster, so a thumb sliding from B
 * onto A releases B and presses A.
 */
private class ClusterTouch(val group: PadPressGroup, val boxes: List<PadHitBox>, val slop: Float)

/**
 * A round button's label ("Fire" on the Atari 2600 face button) keeps full size while its [textWidth] at full size fits 0.7
 * of the [diameter] (the text line across the middle); beyond that it shrinks to fit, down to 30%. Same unit (px).
 * Single letters and symbols are not measured: they always keep full size.
 */
internal fun faceLabelScale(textWidth: Float, diameter: Float): Float {
    val room = diameter * 0.7f
    return if (textWidth <= room) 1f else maxOf(room / textWidth, 0.3f)
}

/**
 * A pill's label ("Insert" on R1, "Option" on START) keeps full size while its [textWidth] at full size fits the pill's
 * width less its rounded ends (0.3 of its height), so a wider pill fits a longer label; beyond that it shrinks to fit,
 * down to 30%. All in the same unit (px).
 */
internal fun pillLabelScale(textWidth: Float, pillWidth: Float, pillHeight: Float): Float {
    val room = pillWidth - pillHeight * 0.3f
    return if (textWidth <= room) 1f else maxOf(room / textWidth, 0.3f)
}

/** The vector symbol drawn on a face button for [PadIcon] (Kenney Input Prompts, CC0), tinted with the label colour. */
private fun PadIcon.padDrawable(): DrawableResource = when (this) {
    PadIcon.PS_CROSS -> Res.drawable.pad_ps_cross
    PadIcon.PS_CIRCLE -> Res.drawable.pad_ps_circle
    PadIcon.PS_TRIANGLE -> Res.drawable.pad_ps_triangle
    PadIcon.PS_SQUARE -> Res.drawable.pad_ps_square
}

/**
 * A [PadIcon]'s box as a share of the label font size: the symbols fill 24 of the drawable's 28 units, so 0.82 em puts
 * them at about a capital letter's height, the size of the letters on other consoles' face buttons.
 */
private const val PAD_ICON_EM = 0.82f

/**
 * One pressable pad button (face / shoulder / pill), countersunk and sinking on press. [icon], when given, is drawn in
 * place of the [label] text. [lit] draws it pressed with no finger on it (a latched GameCube trigger).
 */
@Composable
private fun PadButton(
    button: NxButton,
    size: Dp,
    onEvent: (PadEvent) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = PadStyle.faceShape,
    label: String = button.glyph(),
    width: Dp = size,
    fontSize: TextUnit = NxTextSizes.padLabel,
    face: PadFace? = null,
    labelColor: Color? = null,
    icon: PadIcon? = null,
    touch: ClusterTouch? = null,
    lit: Boolean = false,
) {
    val colors = LocalPadColors.current
    // A lone button is its own one-button cluster: held within the slop of its own box, pressed again if the thumb
    // comes back onto it.
    val group = touch?.group ?: rememberPressGroup(onEvent)
    val (wPx, hPx) = with(LocalDensity.current) { width.toPx() to size.toPx() }
    val boxes = touch?.boxes ?: listOf(PadHitBox(button, Rect(0f, 0f, wPx, hPx)))
    val slop = touch?.slop ?: (hPx * PAD_SLOP)
    // Where this element sits in the cluster: its pointers' positions are moved by it into the cluster's px.
    val origin = boxes.firstOrNull { it.button == button }?.rect?.topLeft ?: Offset.Zero
    NeuSurface(
        shape = shape,
        face = face?.let(colors::faceOf) ?: if (button in FACE_BUTTONS) colors.face else colors.pill,
        pressed = lit || button in group.pressed,
        modifier = modifier.width(width).height(size)
            .padPointers(group, listOf(button, boxes, slop)) { p, held, down ->
                // A pointer's first touch is on this button by definition, even on a box edge's rounding.
                setOfNotNull(padButtonAt(p + origin, boxes, held.firstOrNull(), slop) ?: button.takeIf { down })
            },
    ) {
        val color = colors.labelOf(face, labelColor)
        if (icon != null) {
            Image(
                painterResource(icon.padDrawable()), contentDescription = label,
                modifier = Modifier.size(with(LocalDensity.current) { (fontSize.pinned() * PAD_ICON_EM).toDp() }),
                colorFilter = ColorFilter.tint(color),
            )
        } else {
            val font = LocalNxFont.current
            // A single letter or symbol always keeps full size; a longer label is measured against the button's width.
            val scale = if (label.length <= 1) 1f else {
                val measurer = rememberTextMeasurer()
                val style = LocalTextStyle.current.merge(TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = fontSize.pinned()))
                val textWidth = remember(label, style, measurer) {
                    measurer.measure(label, style, maxLines = 1, softWrap = false).size.width.toFloat()
                }
                with(LocalDensity.current) {
                    if (isPill(button)) pillLabelScale(textWidth, width.toPx(), size.toPx())
                    else faceLabelScale(textWidth, minOf(width, size).toPx())
                }
            }
            val drawn = fontSize.pinned() * scale
            // An all-lowercase label (the Wii Classic Controller's a b x y) has no capital height, so the line box
            // centres it low: it is lifted by a share of its size to sit in the middle of the button.
            val lift = if (isLowercaseLabel(label)) with(LocalDensity.current) { drawn.toDp() } * LOWERCASE_LIFT else 0.dp
            Text(
                label,
                modifier = Modifier.offset(y = -lift),
                color = color,
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                fontSize = drawn,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** A label of letters that are all lowercase ([PadButton] lifts it). */
internal fun isLowercaseLabel(label: String): Boolean = label.any { it.isLetter() } && label.none { it.isUpperCase() }

/** How far an all-lowercase label is lifted, as a share of its font size. */
private const val LOWERCASE_LIFT = 0.1f

/**
 * The face cluster for [profile]: the X/Y/B/A diamond in a [size]×3 square, the N64's A, B and C diamond in a box
 * [N64Cluster.WIDTH] × [N64Cluster.HEIGHT] of [size] (wider, so it grows left toward the d-pad / stick), or the Sega
 * 6-button pad in a box [SegaCluster.WIDTH] × [SegaCluster.HEIGHT] of [size]. Each button is drawn in its
 * [faceClusterHitBoxes] box, and the buttons share one [ClusterTouch]: a thumb slides from one to the next, and two
 * thumbs hold two. Touches between the buttons still pass through (only the buttons take a first touch).
 */
@Composable
private fun FaceClusterBox(
    profile: PadProfile,
    size: Dp,
    glyph: TextUnit,
    emit: (PadEvent) -> Unit,
    modifier: Modifier = Modifier,
    onCStick: ((Float, Float) -> Unit)? = null,
    cStick: CStickLayout? = null,
) {
    val layout = cStick?.takeIf { profile.cStick && onCStick != null }   // the New 3DS cluster: diamond dropped clear of the stick
    val box = if (layout != null) modifier.size(width = size * layout.width, height = size * layout.height)
    else modifier.size(width = size * faceWidthUnits(profile), height = size * faceHeightUnits(profile))
    val unitBoxes = remember(profile, layout) { faceClusterHitBoxes(profile, layout) }
    val sizePx = with(LocalDensity.current) { size.toPx() }
    val group = rememberPressGroup(emit)
    val touch = remember(group, unitBoxes, sizePx) { ClusterTouch(group, unitBoxes.scaled(sizePx), sizePx * PAD_SLOP) }
    Box(box) {
        for (hit in unitBoxes) {
            val b = hit.button
            val d = hit.rect.width
            val place = Modifier.offset(x = size * hit.rect.left, y = size * hit.rect.top)
            when (profile.cluster) {
                FaceCluster.DIAMOND -> {
                    val colors = profile.faceColors(b)    // null off Dreamcast: the pad theme's face
                    PadButton(
                        b, size, emit, place,
                        label = profile.label(b), fontSize = glyph, face = colors?.first, labelColor = colors?.second,
                        icon = profile.icon(b), touch = touch,
                    )
                }
                FaceCluster.N64 -> {
                    val isC = b != NxButton.A && b != NxButton.B
                    PadButton(
                        b, size * d, emit, place,
                        label = profile.label(b),
                        fontSize = if (isC) glyph * 0.8f else glyph * d,   // letters and arrows scale with their button
                        face = when (b) { NxButton.A -> N64PadColors.a; NxButton.B -> N64PadColors.b; else -> N64PadColors.c },
                        labelColor = if (isC) N64PadColors.cLabel else N64PadColors.label,
                        touch = touch,
                    )
                }
                // 3-button: A B C, centred ([SegaCluster.visibleSlots]).
                FaceCluster.SEGA -> PadButton(b, size * d, emit, place, label = profile.label(b), fontSize = glyph, touch = touch)
            }
        }
        // The New 3DS C-stick: a small analog stick in the cluster's top-left corner, sent as the right stick.
        if (layout != null && onCStick != null) {
            val c = layout.stick
            NeuJoystick(size * (c.width / 3f), emit, Modifier.offset(x = size * c.left, y = size * c.top), analog = true, onStick = onCStick)
        }
    }
}

/**
 * One side of the PS2 pad ([dualStickCluster]) at [unit]: the d-pad ([PadGroup.DPAD], left) or the face diamond
 * ([PadGroup.FACE], right) by the screen edge, and toward the middle the stick with its L3 / R3 pill over it
 * ([PadGroup.L_STICK] / [PadGroup.R_STICK]), each group its own. Both are always drawn: no d-pad / stick toggle. The left
 * stick is the core's left analog, the right one its right ([onStick] each).
 */
@Composable
private fun DualStickSide(
    side: PadSide,
    profile: PadProfile,
    unit: Dp,
    emit: (PadEvent) -> Unit,
    onStick: ((Float, Float) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = dualStickCluster(side)
    val left = side == PadSide.LEFT
    Box(modifier.size(width = unit * c.width, height = unit * c.height)) {
        val main = c.main
        val mainUnit = unit * (main.width / 3f)
        val mainAt = Modifier.offset(x = unit * main.left, y = unit * main.top)
        if (left) PadGroupBox(PadGroup.DPAD, mainAt) { NeuDpad(mainUnit, emit) }
        else PadGroupBox(PadGroup.FACE, mainAt) { FaceClusterBox(profile, mainUnit, (mainUnit.value * 0.46f).sp, emit) }
        StickWithThumb(side, profile, unit, StickPiece(c.stick, c.thumb), emit, onStick)
    }
}

/**
 * A PS2 stick with its L3 / R3 pill ([StickPiece], pad units from the enclosing box's top-left, at [unit]), one group
 * ([PadGroup.L_STICK] / [PadGroup.R_STICK]) the Pad layout editor moves as one.
 */
@Composable
private fun StickWithThumb(side: PadSide, profile: PadProfile, unit: Dp, piece: StickPiece, emit: (PadEvent) -> Unit, onStick: ((Float, Float) -> Unit)?) {
    val g = piece.group
    PadGroupBox(if (side == PadSide.LEFT) PadGroup.L_STICK else PadGroup.R_STICK, Modifier.offset(x = unit * g.left, y = unit * g.top)) {
        Box(Modifier.size(width = unit * g.width, height = unit * g.height)) {
            val click = if (side == PadSide.LEFT) NxButton.L3 else NxButton.R3
            PadButton(
                click, unit * piece.thumb.height, emit, Modifier.offset(x = unit * (piece.thumb.left - g.left), y = unit * (piece.thumb.top - g.top)),
                shape = PadStyle.pillShape, width = unit * piece.thumb.width, label = profile.label(click),
            )
            NeuJoystick(
                unit * (piece.stick.width / 3f), emit, Modifier.offset(x = unit * (piece.stick.left - g.left), y = unit * (piece.stick.top - g.top)),
                analog = true, onStick = onStick,
            )
        }
    }
}

/**
 * One arm of the connected cross, drawn only (the cross takes the touch, [NeuDpad]). While [pressed] the arm sinks — a
 * darker inset panel (rounded only on its outer end, via [pressedShape]) drops onto the cross face.
 * The panel and the arrow sit on the arm's visible face — from the face's outer edge to the hub line (the
 * other bar's rim, [PadStyle.faceInset] past the box's inner end), less the groove at the sides, raised
 * [PadStyle.faceLift] like the face — so the arrow is centred on what the user sees, resting or pressed.
 */
@Composable
private fun BoxScope.DpadArm(button: NxButton, size: Dp, alignment: Alignment, pressedShape: Shape, pressed: Boolean) {
    val colors = LocalPadColors.current
    val g = PadStyle.faceInset
    val lift = colors.lift
    val rim = 1.dp      // the hub line's half that falls on this arm (the cross border straddles the other bar's edge)
    // Slide the box in toward the hub by the groove width, then trim the groove off the sides and the rim off the inner end.
    val armFace = when (button) {
        NxButton.UP -> Modifier.offset(y = g - lift).padding(start = g, end = g, bottom = rim)
        NxButton.DOWN -> Modifier.offset(y = -g - lift).padding(start = g, top = rim, end = g)
        NxButton.LEFT -> Modifier.offset(x = g, y = -lift).padding(top = g, end = rim, bottom = g)
        else -> Modifier.offset(x = -g, y = -lift).padding(start = rim, top = g, bottom = g)
    }
    Box(Modifier.align(alignment).size(size)) {
        Box(Modifier.matchParentSize().then(armFace)) {
            if (pressed) {
                // The arm face sunk a further (pressedInset − faceInset) all round.
                Box(
                    Modifier.matchParentSize().padding(PadStyle.pressedInset - PadStyle.faceInset)
                        .background(colors.dpad.pressedFill, pressedShape)
                        .border(1.dp, colors.dpad.pressedRim, pressedShape),
                )
            }
            DpadArrow(button, size * 0.36f, colors.label, Modifier.matchParentSize())
        }
    }
}

/**
 * The arm's arrow: a triangle [width] across its base, pointing toward [button]'s direction and centred in
 * [modifier]'s bounds. Drawn rather than set as a text glyph ("▲▼◀▶"), whose metrics and font fallback put
 * the ink off-centre in the line box.
 */
@Composable
private fun DpadArrow(button: NxButton, width: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = width.toPx()
        val h = w * 0.86f      // near-equilateral, like the old glyph
        val c = center
        // Drawn pointing up with its bounding box on the centre, then turned about the centre for the other arms.
        val path = Path().apply {
            moveTo(c.x, c.y - h / 2f)
            lineTo(c.x + w / 2f, c.y + h / 2f)
            lineTo(c.x - w / 2f, c.y + h / 2f)
            close()
        }
        val degrees = when (button) {
            NxButton.DOWN -> 180f
            NxButton.LEFT -> 270f
            NxButton.RIGHT -> 90f
            else -> 0f
        }
        rotate(degrees) { drawPath(path, color) }
    }
}

/**
 * The connected-cross d-pad: one soft "+" body with four directional arms (no centre hub). The whole 3 × 3 box takes the
 * touch: each pointer presses the 8-way direction of its angle from the centre ([dpadDirections], a small dead zone in
 * the middle), recomputed as it moves, so a thumb rolls from UP through UP+RIGHT to RIGHT, and the arms of the held
 * directions sink.
 */
@Composable
private fun NeuDpad(unit: Dp, onEvent: (PadEvent) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPadColors.current
    val insetPx = with(LocalDensity.current) { PadStyle.faceInset.toPx() }
    val unitPx = with(LocalDensity.current) { unit.toPx() }
    val faceCross = remember(insetPx) { crossShape(insetPx) }
    val armR = unit * 0.24f
    val group = rememberPressGroup(onEvent)
    val held = group.pressed
    val deadZone = unitPx * DPAD_DEAD_ZONE
    Box(
        modifier.size(unit * 3).padPointers(group, deadZone) { p, _, _ ->
            dpadDirections(p.x - unitPx * 1.5f, p.y - unitPx * 1.5f, deadZone)
        },
    ) {
        // The countersink groove (full cross), then the raised face — a uniform inset cross so the
        // groove is the same width on every side.
        if (!colors.outline) Box(Modifier.matchParentSize().background(colors.socketFill, dpadCrossShape).border(1.dp, colors.socketRim, dpadCrossShape))
        Box(Modifier.matchParentSize().offset(y = -colors.lift).background(colors.dpad.raisedFill, faceCross).border(colors.rimWidth, colors.dpad.raisedRim, faceCross))
        // The four arms; each held one presses in with its outer end rounded.
        DpadArm(NxButton.UP, unit, Alignment.TopCenter, RoundedCornerShape(topStart = armR, topEnd = armR), NxButton.UP in held)
        DpadArm(NxButton.DOWN, unit, Alignment.BottomCenter, RoundedCornerShape(bottomStart = armR, bottomEnd = armR), NxButton.DOWN in held)
        DpadArm(NxButton.LEFT, unit, Alignment.CenterStart, RoundedCornerShape(topStart = armR, bottomStart = armR), NxButton.LEFT in held)
        DpadArm(NxButton.RIGHT, unit, Alignment.CenterEnd, RoundedCornerShape(topEnd = armR, bottomEnd = armR), NxButton.RIGHT in held)
    }
}

/**
 * An analog-style thumbstick in a recessed well. A touch anywhere on the well puts the knob under the thumb (the touch
 * from the centre, clamped to a radius; no slop) and it follows the thumb until it lifts, then springs back to centre.
 * Emission depends on [analog]: when false (the default) it emits the dominant direction as a held [NxButton]
 * (up/down/left/right, [stickSector] with hysteresis) so it drives menu navigation, ticking the pad haptic on each
 * direction it enters; when true it reports the true analog axes via [onStick] (a normalised x/y in [-1, 1]) for
 * in-game use, silently.
 */
@Composable
private fun NeuJoystick(
    unit: Dp,
    onEvent: (PadEvent) -> Unit,
    modifier: Modifier = Modifier,
    analog: Boolean = false,
    onStick: ((Float, Float) -> Unit)? = null,
) {
    val colors = LocalPadColors.current
    val maxRadiusPx = with(LocalDensity.current) { (unit * 0.6f).toPx() }   // keep the knob inside the well
    val shadowDropPx = with(LocalDensity.current) { (unit * 0.08f).toPx() } // how far the knob's shadow sits below it
    var knob by remember { mutableStateOf(Offset.Zero) }
    var heldDir by remember { mutableStateOf<NxButton?>(null) }
    var heldSector by remember { mutableStateOf<Int?>(null) }   // the menu sector behind heldDir ([stickSector])
    // The gesture outlives a composition: it reads these through their latest values, never a first composition's.
    // The haptic ticks only in the menu, once per direction entered ([menuStickTick]); the analog stick in game is silent.
    val haptic = rememberPadHaptic()
    val latestOnEvent by rememberUpdatedState(onEvent)
    val latestOnStick by rememberUpdatedState(onStick)
    val latestAnalog by rememberUpdatedState(analog)
    val latestRadius by rememberUpdatedState(maxRadiusPx)

    fun setDir(next: NxButton?) {
        if (next == heldDir) return
        heldDir?.let { latestOnEvent(PadEvent.Up(it)) }
        heldDir = next
        next?.let { latestOnEvent(PadEvent.Down(it)) }
    }
    fun onKnob(o: Offset) {
        if (latestAnalog) {
            val (x, y) = normalizeStick(o.x, o.y, latestRadius)
            latestOnStick?.invoke(x, y)
        } else {
            val (x, y) = normalizeStick(o.x, o.y, latestRadius)
            val next = stickSector(x, y, heldSector, MENU_STICK_SECTORS)
            val tick = menuStickTick(menu = !latestAnalog, old = heldSector, new = next)
            heldSector = next
            setDir(menuStickButton(next))
            if (tick) haptic()   // after the Down went out
        }
    }
    fun recenter() {
        knob = Offset.Zero
        if (latestAnalog) latestOnStick?.invoke(0f, 0f)
        heldSector = null
        setDir(null)    // a no-op unless a digital direction is held
    }

    // Recenter if the stick leaves composition mid-tilt (e.g. toggled back to d-pad): release a held
    // direction, or zero the analog axes.
    DisposableEffect(Unit) { onDispose { recenter() } }

    Box(
        modifier.size(unit * 3).pointerInput(Unit) {
            // The first pointer on the well drives the knob until it lifts; a second finger is ignored.
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false)
                val centre = Offset(size.width / 2f, size.height / 2f)
                fun follow(p: Offset) { knob = clampKnob(p - centre, latestRadius); onKnob(knob) }
                try {
                    follow(first.position)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == first.id } ?: continue
                        if (!change.pressed) break
                        follow(change.position)
                    }
                } finally {
                    recenter()
                }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        // Outer well = the range the knob can move: a shallow bowl, dark at the top rim and lighter at
        // the bottom (lit from the top), so it reads as a natural depression rather than a hard ring.
        Box(Modifier.matchParentSize().background(colors.wellFill, CircleShape).border(colors.rimWidth, colors.wellRim, CircleShape))
        // The knob's soft drop shadow, offset below it (light from the top), grounding the raised stick.
        Box(
            Modifier.offset { IntOffset(knob.x.roundToInt(), (knob.y + shadowDropPx).roundToInt()) }
                .size(unit * 1.95f)
                .background(colors.knobShadow, CircleShape),
        )
        // The knob gets the exact same countersunk, domed treatment as every button — socket ring and
        // all, in the d-pad's colours — so it belongs with the rest of the pad. The well takes the touch.
        NeuSurface(
            shape = CircleShape,
            face = colors.dpad,
            pressed = false,
            modifier = Modifier
                .offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }
                .size(unit * 1.7f),
        )
    }
}

/** A tap toggle styled like a pad button (its own press feedback), used to switch d-pad / joystick. */
@Composable
private fun ToggleButton(label: String, onClick: () -> Unit, height: Dp, width: Dp) =
    ToolButton(label, height, width, onTap = onClick)

/**
 * The Wii pad's MOTION button: [on] says whether the phone's motion is driving the Wiimote; a tap flips it
 * ([onTap]), a long press recentres it ([onLongPress]).
 */
data class PadMotion(val on: Boolean, val onTap: () -> Unit, val onLongPress: () -> Unit)

/** The MOTION pill: the normal face while motion is on, dimmed while off. Tap toggles, long-press recentres. */
@Composable
private fun MotionButton(motion: PadMotion, height: Dp, width: Dp) =
    ToolButton("MOTION", height, width, alpha = if (motion.on) 1f else 0.45f, onTap = motion.onTap, onLongPress = motion.onLongPress)

/**
 * The DS / 3DS pad's mic button: [on] says whether the game hears the phone's mic (the session's Mic toggle), a tap
 * flips it ([onTap]). Not a [PadGroup]: fixed whatever the Pad layout (the portrait pad's bottom left, right of L on the
 * landscape and half-folded halves), drawn above every group and taking a touch on one it overlaps.
 */
data class PadMic(val on: Boolean, val onTap: () -> Unit)

/** The mic button's diameter on a pad whose unit is [unit] dp. */
internal fun micButtonSize(unit: Float): Float = unit * 0.9f

/** The mic button's inset from the pad's bottom (and, on the portrait pad, its left) edge, dp. */
internal fun micButtonInset(unit: Float): Float = unit * 0.15f

/**
 * How far the portrait mic button's centre sits above its pad's bottom edge, dp, on a pad whose unit is [unit] dp
 * (the 16 dp padding included): the band's corner button, or with the band hidden ([hiddenBand]) the first of
 * [PortraitOverlayPad]'s bottom row. The host lines the band's hide / show icon up with it.
 */
fun portraitMicCentre(unit: Float, hiddenBand: Boolean): Float =
    CLUSTER_PADDING + if (hiddenBand) unit * OVERLAY_SYSTEM_UNITS / 2f else micButtonInset(unit) + micButtonSize(unit) / 2f

/**
 * How far the portrait mic button's left edge sits from the screen's left, dp, as [portraitMicCentre]: the band's
 * corner inset, or the hidden band's overlay row's edge nudge. The host mirrors it for the hide / show icon's right.
 */
fun portraitMicEdge(unit: Float, hiddenBand: Boolean): Float =
    CLUSTER_PADDING + if (hiddenBand) unit * OVERLAY_EDGE_NUDGE else micButtonInset(unit)

/**
 * How far the landscape left half's mic button ends past the edge nudge, dp: [shoulders] (L1 [unit] wide, L2 [trigger],
 * 8 dp apart), then the button 8 dp on and [micButtonSize] across. Inside the d-pad's 3 units, so the halves' measured
 * reach (its groups) already holds it, and the picture or the half-folded touch screen keeps clear of it.
 */
internal fun shoulderMicEnd(shoulders: List<NxButton>, unit: Float, trigger: Float): Float =
    shoulders.sumOf { (if (it == NxButton.L1) unit else trigger).toDouble() }.toFloat() + 8f * shoulders.size + micButtonSize(unit)

/**
 * [mic]'s round button in its pad's root box, [start] dp from the left and [micButtonInset] up from the bottom: the pad's
 * pill face with the mic symbol, dimmed while off (as MOTION), pressed in while on. A tap toggles; there is no hold.
 */
@Composable
private fun BoxScope.MicButton(mic: PadMic, unit: Dp, start: Dp) =
    MicFace(
        mic, micButtonSize(unit.value).dp,
        Modifier.align(Alignment.BottomStart).zIndex(1f).padding(start = start, bottom = micButtonInset(unit.value).dp),
    )

/** The mic button's round face, [size] across, wherever [modifier] puts it ([MicButton]; first in [PortraitOverlayPad]'s row). */
@Composable
private fun MicFace(mic: PadMic, size: Dp, modifier: Modifier = Modifier) {
    val haptic = rememberPadHaptic()
    val latestTap by rememberUpdatedState(mic.onTap)
    var pressed by remember { mutableStateOf(false) }
    NeuSurface(
        shape = CircleShape,
        face = LocalPadColors.current.pill,
        pressed = pressed || mic.on,
        modifier = modifier
            .size(size)
            .then(if (mic.on) Modifier else Modifier.alpha(0.45f))   // role: the mic pill while off
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        haptic()
                        try { tryAwaitRelease() } finally { pressed = false }
                    },
                    onTap = { latestTap() },
                )
            },
    ) {
        Image(
            painterResource(Res.drawable.pad_mic), contentDescription = "Mic",
            modifier = Modifier.size(size * 0.5f),
            colorFilter = ColorFilter.tint(LocalPadColors.current.label),
        )
    }
}

/**
 * The 3DS pad's own STYLUS and SWAP pills, the on-screen forms of its SELECT + L1 / SELECT + R1 chords: [stylusOn] says
 * whether stylus mode is on (the pill stays pressed in), a STYLUS tap flips it ([onStylus]), and SWAP holds the core's
 * swap-screens key while pressed ([onSwap] down, then up), so it works in Azahar's toggle and hold swap modes alike.
 */
data class PadTools(val stylusOn: Boolean, val onStylus: () -> Unit, val onSwap: (Boolean) -> Unit)

/**
 * A pill button styled like a pad button, with its own press feedback (the [PadTools] pills, the d-pad / stick toggle,
 * MOTION, the touch shortcuts): [label] on the pill face at the pad's label size, shrunk to fit a narrow pill
 * ([pillLabelScale]: "Hold RW", or STYLUS in the New 3DS's portrait top row), pressed in while [latched], drawn at
 * [alpha] when given; [onDown] / [onUp] around a press, [onTap] after a tap, [onLongPress] after a
 * long press (a pill without one never waits for it). The tap handler outlives a composition: it calls the latest
 * callbacks (a hoisted choice changes them).
 */
@Composable
private fun ToolButton(
    label: String,
    height: Dp,
    width: Dp,
    latched: Boolean = false,
    alpha: Float? = null,
    onTap: () -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    onDown: () -> Unit = {},
    onUp: () -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current.merge(TextStyle(fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = NxTextSizes.padLabel.pinned()))
    val textWidth = remember(label, style, measurer) { measurer.measure(label, style, maxLines = 1, softWrap = false).size.width.toFloat() }
    val labelScale = with(LocalDensity.current) { pillLabelScale(textWidth, width.toPx(), height.toPx()) }
    val haptic = rememberPadHaptic()
    val latestTap by rememberUpdatedState(onTap)
    val latestLong by rememberUpdatedState(onLongPress)
    val latestDown by rememberUpdatedState(onDown)
    val latestUp by rememberUpdatedState(onUp)
    val hasLongPress = onLongPress != null
    var pressed by remember { mutableStateOf(false) }
    NeuSurface(
        shape = PadStyle.pillShape,
        face = LocalPadColors.current.pill,
        pressed = pressed || latched,
        modifier = Modifier.width(width).height(height).then(if (alpha != null) Modifier.alpha(alpha) else Modifier).pointerInput(hasLongPress) {   // role: a pad toggle pill while off (MOTION)
            detectTapGestures(
                onPress = {
                    pressed = true
                    haptic()
                    latestDown()
                    try { tryAwaitRelease() } finally {
                        pressed = false
                        latestUp()
                    }
                },
                onTap = { latestTap() },
                onLongPress = if (hasLongPress) { _ -> latestLong?.invoke() } else null,
            )
        },
    ) {
        Text(
            label,
            color = LocalPadColors.current.label,
            fontFamily = LocalNxFont.current,
            fontWeight = FontWeight.SemiBold,
            fontSize = NxTextSizes.padLabel.pinned() * labelScale,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun StylusButton(tools: PadTools, height: Dp, width: Dp) =
    ToolButton("STYLUS", height, width, latched = tools.stylusOn, onTap = tools.onStylus)

@Composable
private fun SwapButton(tools: PadTools, height: Dp, width: Dp) =
    ToolButton("SWAP", height, width, onDown = { tools.onSwap(true) }, onUp = { tools.onSwap(false) })

/**
 * A touch shortcut button: [shortcut]'s label on a MENU-sized pill, pressed in while its toggle is on. Every action gets
 * its press and its release ([onShortcut] true, then false, also when the finger slides off or the button leaves the
 * pad): a hold runs while the finger stays, a toggle flips (and Reset asks) on the press. A null [shortcut] is the Pad
 * layout editor's labelled stand-in for [slot] (the editor takes its touches).
 */
@Composable
private fun ShortcutButton(slot: TouchSlot, shortcut: TouchShortcut?, onShortcut: (TouchSlot, Boolean) -> Unit, height: Dp, width: Dp) {
    val label = shortcut?.let { touchShortcutLabel(it.action) } ?: touchSlotPlaceholder(slot)
    ToolButton(
        label, height, width, latched = shortcut?.on == true,
        onDown = { onShortcut(slot, true) }, onUp = { onShortcut(slot, false) },
    )
}

/**
 * [slot]'s touch shortcut as its own Pad layout piece ([TouchSlot.padGroup]): drawn while [shortcuts] gives it an action,
 * or always as a stand-in when [placeholders] (the editor). Nothing at all otherwise, so the stock pad looks as before.
 */
@Composable
private fun ShortcutPiece(
    slot: TouchSlot,
    shortcuts: Map<TouchSlot, TouchShortcut>,
    placeholders: Boolean,
    onShortcut: (TouchSlot, Boolean) -> Unit,
    height: Dp,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val shortcut = shortcuts[slot]
    if ((shortcut == null && !placeholders) || width <= 0.dp) return
    PadGroupBox(slot.padGroup, modifier) { ShortcutButton(slot, shortcut, onShortcut, height, width) }
}

/**
 * The d-pad / stick choice per game for the process's life. A host that rebuilds the pad per layout (rotate, fold: a
 * new ComposeView each time, so nothing remembered inside the pad survives) passes `PadStickModes[game]` as the pad's
 * `useJoystick` and `{ PadStickModes[game] = it }` as its `onUseJoystickChange`, and the choice outlives the rebuild.
 * Snapshot state: a pad reading it recomposes when it changes.
 */
object PadStickModes {
    private val byGame = mutableStateMapOf<String, Boolean>()

    /** The stick (true) or the d-pad (false, the default) for [game]. */
    operator fun get(game: String): Boolean = byGame[game] ?: false

    /** The player's choice for [game], or [default] until they make one (the pad's [PadProfile.stickByDefault]). */
    operator fun get(game: String, default: Boolean): Boolean = byGame[game] ?: default

    operator fun set(game: String, useJoystick: Boolean) {
        byGame[game] = useJoystick
    }
}

/** The pad's d-pad / stick choice: the host's [hoisted] value when it passes one, else the pad's own. */
@Stable
private class StickChoice(private val hoisted: Boolean?, private val onChange: (Boolean) -> Unit, private val local: MutableState<Boolean>) {
    val useJoystick: Boolean get() = hoisted ?: local.value

    fun toggle() {
        val next = !useJoystick
        if (hoisted == null) local.value = next
        onChange(next)
    }
}

/**
 * [StickChoice] for a pad given the host's [useJoystick] (null: the pad keeps its own, reset whenever the pad is
 * rebuilt) and [onUseJoystickChange], told of every toggle.
 */
@Composable
private fun rememberStickChoice(useJoystick: Boolean?, onUseJoystickChange: (Boolean) -> Unit): StickChoice {
    val local = remember { mutableStateOf(false) }
    return StickChoice(useJoystick, onUseJoystickChange, local)
}

/**
 * The portrait pad. [useJoystick] / [onUseJoystickChange] hoist the d-pad / stick choice to the host (see
 * [PadStickModes]); left null, the pad keeps it itself. [latched] are shoulders drawn pressed without a finger on them
 * (the GameCube's soft-pressed L / R). [tools] (DS / 3DS STYLUS and SWAP) and [motion] (the Wii's MOTION) join SELECT /
 * START in the top row ([portraitTopPills]). [shortcuts] are the touch shortcut buttons to draw ([visibleTouchShortcuts]),
 * either side of MENU and the toggle, each pressed and released through [onShortcut]; [shortcutPlaceholders] draws both
 * as stand-ins (the Pad layout editor). [mic] (DS / 3DS) is the fixed mic button at the bottom left ([MicButton]).
 */
@Composable
fun VirtualPad(
    onEvent: (PadEvent) -> Unit,
    modifier: Modifier = Modifier,
    analog: Boolean = false,
    onStick: ((Float, Float) -> Unit)? = null,
    profile: PadProfile = PadProfile.STANDARD,
    useJoystick: Boolean? = null,
    onUseJoystickChange: (Boolean) -> Unit = {},
    onCStick: ((Float, Float) -> Unit)? = null,
    motion: PadMotion? = null,
    latched: Set<NxButton> = emptySet(),
    layout: PadLayout = PadLayout.EMPTY,
    onGroupBounds: PadGroupBoundsListener? = null,
    tools: PadTools? = null,
    shortcuts: Map<TouchSlot, TouchShortcut> = emptyMap(),
    onShortcut: (TouchSlot, Boolean) -> Unit = { _, _ -> },
    shortcutPlaceholders: Boolean = false,
    mic: PadMic? = null,
) {
    // Track which buttons this pad currently holds so we can release them if the pad
    // leaves composition mid-hold. A duplicate Up (also emitted by the gesture's finally
    // block) is harmless: the router ignores an Up for a button it is not holding.
    val emit = rememberEmit(onEvent)
    val stick = rememberStickChoice(useJoystick, onUseJoystickChange)
    BoxWithConstraints(modifier.fillMaxSize().padTopEdge(LocalPadColors.current.topEdge).background(LocalPadColors.current.surface).padding(16.dp)) {
        // Button size unit. The column stacks 6.12 units at a middle-row scale of 1 (shoulders 1 + cluster 3 +
        // NX mark 1.1 + gap 0.3 + MENU row 0.72), so divide the height by 6.2 to keep it inside a short band without clipping.
        val unit = minOf(maxWidth / 9f, maxHeight / 6.2f)
        val arrange = rememberPadArrange(layout, with(LocalDensity.current) { unit.toPx() }, onGroupBounds)
        val bumper = unit * 1.0f      // L1 / R1: the shoulders these systems actually use — bigger
        val trigger = unit * 0.72f    // L2 / R2: rarely used on the supported systems — smaller
        val sysH = unit * 0.72f
        val sysW = unit * 1.4f
        // The middle row (d-pad / stick and the face diamond) grows into the spare room, as far as it fits.
        // The d-pad / stick is sized like on every console; the face cluster takes its own scale beside it.
        val w = maxWidth / unit
        val h = maxHeight / unit
        val padW = maxWidth.value
        val stickScaleValue = midRowScale(w, h)
        val stickMid = unit * stickScaleValue
        val faceMid = unit * faceClusterScale(w, h, stickScaleValue, faceWidthUnits(profile), faceHeightUnits(profile))
        val glyph = (faceMid.value * 0.46f).sp      // bigger face-button letters
        // The New 3DS C-stick under SELECT / START; on a pad too narrow for it between the clusters, the clusters drop.
        val cStick = if (profile.cStick && onCStick != null) portraitCStick(w, h, stickScaleValue) else null
        val clusterDrop = cStick?.clusterDrop ?: 0f
        ProvidePadArrange(arrange) {
        Box(Modifier.fillMaxSize().padArrangeRoot(arrange)) {
        // The NX mark above MENU's row, drawn first and under everything (zIndex), so the dropped clusters, the C-stick
        // or a group moved over it (Pad layout) sit on top. It takes no room in the column: the GameCube's and the New
        // 3DS's dropped clusters ([portraitCStick]) use its height, and the mark still shows between them.
        NxEmblem(
            Modifier.align(Alignment.BottomCenter).padding(bottom = unit * (0.72f + PORTRAIT_EMBLEM_GAP))
                .size(width = unit * 1.15f, height = unit * PORTRAIT_EMBLEM_H).zIndex(-1f),
        )
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            // top row: L1 L2  |  STYLUS SELECT HOME START MOTION SWAP  |  R2 R1. The shoulders sit in equal halves either
            // side, so the pills stay centred (under them the C-stick) on a pad whose shoulders differ (the GameCube's L
            // against R + Z, a sideways Wiimote's Shake alone); they narrow where a long row would not fit beside them.
            val topPills = portraitTopPills(profile, tools != null, motion != null)
            val leftShoulders = padShoulders(PadSide.LEFT, profile)
            val rightShoulders = padShoulders(PadSide.RIGHT, profile)
            fun shouldersWidth(buttons: List<NxButton>): Dp =
                buttons.fold(0.dp) { w, b -> w + if (b == NxButton.L1 || b == NxButton.R1) bumper else trigger } +
                    8.dp * (buttons.size - 1).coerceAtLeast(0)
            val topW = portraitTopPillWidth(
                sysW.value, padW, maxOf(shouldersWidth(leftShoulders), shouldersWidth(rightShoulders)).value,
                topPills.size, PORTRAIT_TOP_GAP,
            ).dp
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    PadGroupBox(PadGroup.L_SHOULDERS) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ShoulderButtons(leftShoulders, profile, bumper, trigger, emit, latched)
                        }
                    }
                }
                PadGroupBox(PadGroup.SYSTEM_RIGHT) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        topPills.forEachIndexed { i, pill ->
                            if (i > 0) Spacer(Modifier.width(PORTRAIT_TOP_GAP.dp))
                            when (pill) {
                                PortraitTopPill.STYLUS -> StylusButton(tools!!, sysH, topW)
                                PortraitTopPill.SELECT -> PadButton(NxButton.SELECT, sysH, emit, shape = PadStyle.pillShape, width = topW, label = profile.label(NxButton.SELECT))
                                PortraitTopPill.HOME -> PadButton(NxButton.HOME, sysH, emit, shape = PadStyle.pillShape, width = topW, label = profile.label(NxButton.HOME))
                                PortraitTopPill.START -> PadButton(NxButton.START, sysH, emit, shape = PadStyle.pillShape, width = topW, label = profile.label(NxButton.START))
                                PortraitTopPill.MOTION -> MotionButton(motion!!, sysH, topW)
                                PortraitTopPill.SWAP -> SwapButton(tools!!, sysH, topW)
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    PadGroupBox(PadGroup.R_SHOULDERS) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ShoulderButtons(rightShoulders, profile, bumper, trigger, emit, latched)
                        }
                    }
                }
            }
            // middle row: d-pad / joystick  |  X Y B A diamond (inset a little from the edges)
            val dropped = if (clusterDrop > 0f) Modifier.padding(top = unit * clusterDrop, bottom = unit * cStick!!.clusterBottomPad) else Modifier
            // The PS2 keeps both full size here (its d-pad, never the stick); its sticks sit below them ([portraitDualSticks]).
            Row(Modifier.fillMaxWidth().padding(horizontal = unit * 0.55f).then(dropped), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PadGroupBox(PadGroup.DPAD) {
                    if (stick.useJoystick && !profile.dualSticks) NeuJoystick(stickMid, emit, analog = analog, onStick = onStick) else NeuDpad(stickMid, emit)
                }
                PadGroupBox(PadGroup.FACE) { FaceClusterBox(profile, faceMid, glyph, emit) }    // the diamond as on every pad: the C-stick sits apart
            }
            // bottom: the NX mark's room (drawn above), then MENU + d-pad/stick toggle, under the rows above (zIndex), so a
            // group moved over MENU's row (Pad layout) sits on top of it.
            Column(Modifier.fillMaxWidth().zIndex(-1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (clusterDrop == 0f) Spacer(Modifier.height(unit * (PORTRAIT_EMBLEM_H + PORTRAIT_EMBLEM_GAP)))   // the dropped clusters take it
                // The buttons actually shown (a touch shortcut only where assigned, MENU + the toggle always) centred as one
                // row, so a single shortcut no longer leaves MENU off the middle of what is drawn.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    portraitBottomRow(shortcuts, shortcutPlaceholders).forEach { piece ->
                        when (piece) {
                            PortraitBottomPiece.SHORTCUT_1 ->
                                ShortcutPiece(TouchSlot.ONE, shortcuts, shortcutPlaceholders, onShortcut, sysH, sysW, Modifier.padding(end = PORTRAIT_SHORTCUT_GAP.dp))
                            PortraitBottomPiece.SYSTEM -> PadGroupBox(PadGroup.SYSTEM_LEFT) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PadButton(NxButton.MENU, sysH, emit, shape = PadStyle.pillShape, width = sysW)
                                    if (!profile.dualSticks) {   // the PS2 draws both: nothing to toggle
                                        Spacer(Modifier.width(12.dp))
                                        ToggleButton(if (stick.useJoystick) "D-PAD" else "STICK", stick::toggle, sysH, sysW)
                                    }
                                }
                            }
                            PortraitBottomPiece.SHORTCUT_2 ->
                                ShortcutPiece(TouchSlot.TWO, shortcuts, shortcutPlaceholders, onShortcut, sysH, sysW, Modifier.padding(start = PORTRAIT_SHORTCUT_GAP.dp))
                        }
                    }
                }
            }
        }
        // The New 3DS C-stick: centred under SELECT / START, between the clusters, sent as the right stick.
        if (cStick != null) {
            val c = cStick.box
            if (c.width > 0f) {
                // Drawn last, so above the clusters; clipped round, so only a touch inside the well ([cStickWellHit]) is
                // its (the drag then stays with it) and one on the box's corners falls through to the cluster below.
                PadGroupBox(PadGroup.C_STICK, Modifier.offset(x = unit * c.left, y = unit * c.top)) {
                    NeuJoystick(unit * (c.width / 3f), emit, Modifier.clip(CircleShape), analog = true, onStick = onCStick)
                }
            }
        }
        // The PS2's sticks, each under its side's d-pad / diamond by the pad's edge (left stick: the core's left analog).
        if (profile.dualSticks) {
            val (l, r) = portraitDualSticks(w, h, stickScaleValue)
            StickWithThumb(PadSide.LEFT, profile, unit, l, emit, onStick)
            StickWithThumb(PadSide.RIGHT, profile, unit, r, emit, onCStick)
        }
        // The mic button in the bottom-left corner, under the d-pad's row (which ends at least 1.92 units up: over MENU's
        // row and the NX mark's room, or the dropped clusters' padding) and left of a touch shortcut 1.
        mic?.let { MicButton(it, unit, micButtonInset(unit.value).dp) }
        }   // Box (arrange root)
        }   // ProvidePadArrange
    }
}

/** The gap between a portrait touch shortcut and MENU's row, dp: the row's own pill gap. */
private const val PORTRAIT_SHORTCUT_GAP = 12f

/** A piece of the portrait pad's bottom row ([portraitBottomRow]). */
internal enum class PortraitBottomPiece { SHORTCUT_1, SYSTEM, SHORTCUT_2 }

/**
 * The portrait bottom row's pieces, left to right, that are drawn: MENU with the toggle ([PortraitBottomPiece.SYSTEM])
 * always, a touch shortcut only where [shortcuts] assigns it (or both as stand-ins with [placeholders], the Pad layout
 * editor). The row centres what it holds, so one shortcut does not push MENU off the middle.
 */
internal fun portraitBottomRow(shortcuts: Map<TouchSlot, TouchShortcut>, placeholders: Boolean): List<PortraitBottomPiece> = listOfNotNull(
    PortraitBottomPiece.SHORTCUT_1.takeIf { placeholders || shortcuts[TouchSlot.ONE] != null },
    PortraitBottomPiece.SYSTEM,
    PortraitBottomPiece.SHORTCUT_2.takeIf { placeholders || shortcuts[TouchSlot.TWO] != null },
)

/** The portrait NX mark's height and its gap over MENU's row, units. */
private const val PORTRAIT_EMBLEM_H = 1.1f
private const val PORTRAIT_EMBLEM_GAP = 0.3f

/** The gap between the portrait top row's pills, and the least between them and the shoulders, dp. */
internal const val PORTRAIT_TOP_GAP = 8f

/** A pill of the portrait pad's top row ([portraitTopPills]). */
internal enum class PortraitTopPill { STYLUS, SELECT, HOME, START, MOTION, SWAP }

/**
 * The portrait top row's pills, left to right: SELECT (unless [profile] hides it), the Wii's HOME and, right after it,
 * MOTION ([motion], where [profile] has one), START; around them the pills that would otherwise share MENU's row, so the
 * touch shortcuts always sit right beside MENU and the toggle there: the DS / 3DS STYLUS first and SWAP last ([tools]).
 * The Wii's row so reads − HOME MOTION +.
 */
internal fun portraitTopPills(profile: PadProfile, tools: Boolean, motion: Boolean): List<PortraitTopPill> = listOfNotNull(
    PortraitTopPill.STYLUS.takeIf { tools },
    PortraitTopPill.SELECT.takeIf { !profile.isHidden(NxButton.SELECT) },
    PortraitTopPill.HOME.takeIf { profile.homeButton },
    PortraitTopPill.MOTION.takeIf { motion && profile.motionButton },
    PortraitTopPill.START,
    PortraitTopPill.SWAP.takeIf { tools },
)

/**
 * The width of each of the portrait top row's [pills], centred in a row [rowWidth] wide between equal halves that each
 * hold the wider shoulder group ([sideWidth]): [pillWidth] (MENU's) where that leaves [gap] between the pills and to
 * either shoulder group, else what is left (the New 3DS's and the Nunchuk Wii's four pills between all four shoulders,
 * about 1.1 units on the Fold's cover), so the row never reaches the shoulders. All in dp.
 */
internal fun portraitTopPillWidth(pillWidth: Float, rowWidth: Float, sideWidth: Float, pills: Int, gap: Float): Float {
    if (pills <= 0) return pillWidth
    return minOf(pillWidth, (rowWidth - 2 * (sideWidth + gap) - (pills - 1) * gap) / pills).coerceAtLeast(0f)
}

private const val MID_SCALE = 1.25f       // target growth of the portrait pad's middle row
private const val MID_MIN_GAP = 0.5f      // units kept between the d-pad and the face diamond
private const val MID_MIN_SLACK = 0.4f    // units of vertical space kept free in the column

/** The face cluster's width in face-button units: 3 for the X/Y/B/A diamond, else its cluster's WIDTH. */
fun faceWidthUnits(profile: PadProfile): Float = when (profile.cluster) {
    FaceCluster.DIAMOND -> 3f
    FaceCluster.N64 -> N64Cluster.WIDTH
    FaceCluster.SEGA -> SegaCluster.WIDTH
}

/** The face cluster's height in face-button units: 3 for the X/Y/B/A diamond, else its cluster's HEIGHT. */
internal fun faceHeightUnits(profile: PadProfile): Float = when (profile.cluster) {
    FaceCluster.DIAMOND -> 3f
    FaceCluster.N64 -> N64Cluster.HEIGHT
    FaceCluster.SEGA -> SegaCluster.HEIGHT
}

/**
 * The portrait pad's d-pad / stick scale for a pad [widthUnits] x [heightUnits] button units, the same on every
 * console: [MID_SCALE], or less so the row (0.55-unit side padding each side, the 3-unit d-pad / stick, the 3-unit
 * face diamond, [MID_MIN_GAP] between) fits the width and the column (3.12 fixed units + the 3-unit row, scaled,
 * + [MID_MIN_SLACK]) fits the height. Never below 1. The layout model (hidden-band overlay height) uses it too.
 */
fun midRowScale(widthUnits: Float, heightUnits: Float): Float =
    minOf(MID_SCALE, (widthUnits - 1.1f - MID_MIN_GAP) / 6f, (heightUnits - 3.12f - MID_MIN_SLACK) / 3f).coerceAtLeast(1f)

/**
 * The face cluster's scale beside a d-pad / stick at [stickScale] ([midRowScale]). The 3 x 3 diamond takes exactly
 * [stickScale], so those pads are unchanged. A wider or taller cluster ([faceWidthUnits] x [faceHeightUnits], the N64's)
 * fits the width left beside the full-size stick and the column height: at most [stickScale], never below
 * 3 / [faceHeightUnits], so at the floor it stands no taller than the diamond does at scale 1 and the column (sized
 * for 6.12 units) cannot overflow. The floor cannot overflow the width either: with a pad at least 9 units wide
 * ([widthUnits] >= 9) and [stickScale] <= [MID_SCALE] = 1.25, the N64 row at the floor is
 * 1.6 + 3 x 1.25 + 3.75 x 0.87 = 8.61 <= 9. The Sega pad ([SegaCluster], 3.75 x 2.8) floors at 3 / 2.8 ≈ 1.071, which
 * on a width-bound pad can eat into [MID_MIN_GAP] (at most ~0.32 units at 9 units wide); nothing clips.
 */
internal fun faceClusterScale(
    widthUnits: Float,
    heightUnits: Float,
    stickScale: Float,
    faceWidthUnits: Float,
    faceHeightUnits: Float,
): Float {
    if (faceWidthUnits == 3f && faceHeightUnits == 3f) return stickScale
    return minOf(
        stickScale,
        (widthUnits - 1.1f - MID_MIN_GAP - 3f * stickScale) / faceWidthUnits,
        (heightUnits - 3.12f - MID_MIN_SLACK) / faceHeightUnits,
    ).coerceAtLeast(3f / faceHeightUnits)
}

/** The portrait C-stick's clear space to SELECT / START, both clusters' touch boxes and the NX mark, in pad units. */
internal const val PORTRAIT_C_STICK_GAP = 0.2f

/** The portrait top row's bottom edge, units: SELECT / START (0.72 tall) centred in the 1-unit shoulders' row. */
private const val TOP_PILLS_BOTTOM = 0.86f

/**
 * The portrait pad's New 3DS C-stick: its [box] in pad units from the padded pad's top-left, and how far down
 * ([clusterDrop], pad units, 0 for not) the middle row is padded so the stick sits above both clusters.
 */
internal data class PortraitCStick(val box: Rect, val clusterDrop: Float, val clusterBottomPad: Float = 0f)

/** How far (pad units, ~50 dp on the Fold's cover) the dropped clusters sit above their lowest place over MENU / toggle. */
internal const val PORTRAIT_C_STICK_RAISE = 1.0f

/**
 * The portrait C-stick ([PortraitCStick]) on a pad [widthUnits] x [heightUnits] with both clusters at [stickScale]:
 * [portraitCStickBox], between the clusters, where it is full size there (the Fold's inner screen). Else (the Fold's
 * cover, the clusters 1.12 units apart) the middle row drops into the NX mark's room, which it then takes (the mark is
 * still drawn, under them: there is no room for it under the clusters with the gaps, and between them it is a hair too
 * wide, so their corners and the stick may cover it): the clusters
 * keep their size and sit [PORTRAIT_C_STICK_RAISE] above their lowest place ([PORTRAIT_C_STICK_GAP] over MENU /
 * toggle), the padding below them taking the rest of the column. The stick, centred [PORTRAIT_C_STICK_GAP] under
 * SELECT / START, is full size where it clears their buttons ([portraitCStickOverClusters]; its well may cover the
 * clusters' empty corners). Kept only when that stick is bigger than the one between the undropped clusters.
 */
internal fun portraitCStick(widthUnits: Float, heightUnits: Float, stickScale: Float): PortraitCStick {
    val between = portraitCStickBox(widthUnits, heightUnits, stickScale)
    val full = C_STICK_DIAMETER * stickScale
    if (between.width >= full - 1e-4f) return PortraitCStick(between, 0f)
    // Top row 1 + the padded middle row (drop + 3 x scale + bottom padding) + MENU row 0.72 fill the height exactly.
    val room = heightUnits - 1f - 0.72f - 3f * stickScale
    val drop = room - PORTRAIT_C_STICK_GAP - PORTRAIT_C_STICK_RAISE
    if (drop <= 0f) return PortraitCStick(between, 0f)
    val box = portraitCStickOverClusters(widthUnits, stickScale, clusterTop = 1f + drop)
    if (box.width <= between.width) return PortraitCStick(between, 0f)
    return PortraitCStick(box, drop, room - drop)
}

/** The portrait C-stick's least clear space to an actual button (X, Y, a d-pad arm, the left stick's well), pad units. */
internal const val PORTRAIT_C_STICK_BUTTON_GAP = 0.1f

/**
 * The actual buttons near the portrait C-stick, pad units, with both clusters at [scale] and their tops at [clusterTop]
 * on a pad [widthUnits] wide: the d-pad's up and right arms (rects), and as circles (centre, radius) the left stick's
 * well (its whole 3 x [scale] box) and the diamond's X and Y.
 */
internal fun portraitCStickNeighbours(widthUnits: Float, scale: Float, clusterTop: Float): Pair<List<Rect>, List<Pair<Offset, Float>>> {
    val l = 0.55f
    val faceL = widthUnits - 0.55f - 3f * scale
    val arms = listOf(
        Rect(l + scale, clusterTop, l + 2f * scale, clusterTop + scale),                       // up
        Rect(l + 2f * scale, clusterTop + scale, l + 3f * scale, clusterTop + 2f * scale),     // right
    )
    val circles = listOf(
        Offset(l + 1.5f * scale, clusterTop + 1.5f * scale) to 1.5f * scale,                   // the left stick's well
        Offset(faceL + 1.5f * scale, clusterTop + 0.5f * scale) to 0.5f * scale,               // X
        Offset(faceL + 0.5f * scale, clusterTop + 1.5f * scale) to 0.5f * scale,               // Y
    )
    return arms to circles
}

/**
 * The portrait C-stick over the clusters' tops ([portraitCStick]) at [clusterTop], pad units: centred across a pad
 * [widthUnits] wide, [PORTRAIT_C_STICK_GAP] under SELECT / START, [C_STICK_DIAMETER] x [scale] across where its well
 * keeps [PORTRAIT_C_STICK_BUTTON_GAP] off every button of [portraitCStickNeighbours], else as big as keeps it. The well
 * may cover the clusters' empty corners: it is drawn above them and takes the touches that start inside it.
 */
internal fun portraitCStickOverClusters(widthUnits: Float, scale: Float, clusterTop: Float): Rect {
    val top = TOP_PILLS_BOTTOM + PORTRAIT_C_STICK_GAP
    val cx = widthUnits / 2f
    val (rects, circles) = portraitCStickNeighbours(widthUnits, scale, clusterTop)
    fun clear(r: Float): Boolean {
        val c = Offset(cx, top + r)
        return rects.all { it.distanceTo(c) >= r + PORTRAIT_C_STICK_BUTTON_GAP } &&
            circles.all { (o, rr) -> (o - c).getDistance() >= r + rr + PORTRAIT_C_STICK_BUTTON_GAP }
    }
    var r = C_STICK_DIAMETER * scale / 2f
    if (!clear(r)) {
        var lo = 0f
        var hi = r
        repeat(30) { val mid = (lo + hi) / 2f; if (clear(mid)) lo = mid else hi = mid }
        r = lo
    }
    return Rect(cx - r, top, cx + r, top + 2f * r)
}

/** Whether a touch at [p] starts on the C-stick whose square box is [box]: inside its round well, not its box's corners. */
internal fun cStickWellHit(box: Rect, p: Offset): Boolean = (p - box.center).getDistance() <= box.width / 2f

/**
 * The portrait pad's New 3DS C-stick box in pad units from the padded pad's top-left, on a pad [widthUnits] x
 * [heightUnits] with the d-pad / stick and the face diamond both at [stickScale]: centred across the pad,
 * [PORTRAIT_C_STICK_GAP] under SELECT / START, between the two clusters. Its well is [C_STICK_DIAMETER] face-button
 * units ([stickScale] each) where that keeps the gap to both clusters' square touch boxes (3 x [stickScale], 0.55 in
 * from the sides, their top [rowTop], by default where the column puts them) and to the NX mark above MENU; else as
 * big as keeps it (its circle may reach down between the clusters' top corners).
 */
internal fun portraitCStickBox(
    widthUnits: Float,
    heightUnits: Float,
    stickScale: Float,
    rowTop: Float = 1f + (heightUnits - 3.12f - 3f * stickScale) / 2f,   // SpaceBetween: the free height split in two
): Rect {
    val g = PORTRAIT_C_STICK_GAP
    val top = TOP_PILLS_BOTTOM + g
    val half = widthUnits / 2f - (0.55f + 3f * stickScale)              // the centre to either cluster's inner edge
    val emblemTop = heightUnits - 2.12f
    fun fits(r: Float): Boolean {
        val cy = top + r
        val dy = rowTop - cy                                             // the clusters' top corners above the centre
        val toClusters = if (dy > 0f) hypot(half, dy) else half
        return toClusters >= r + g && cy + r <= emblemTop - g
    }
    var r = C_STICK_DIAMETER * stickScale / 2f
    if (!fits(r)) {
        var lo = 0f
        var hi = r
        repeat(30) { val mid = (lo + hi) / 2f; if (fits(mid)) lo = mid else hi = mid }
        r = lo
    }
    val cx = widthUnits / 2f
    return Rect(cx - r, top, cx + r, top + 2f * r)
}

/**
 * The share of the screen's width each landscape cluster's box takes: half, so the two never overlap. The box does
 * not size the cluster: [clusterUnit] comes from the height, and the content (Start / End aligned, touch only on its
 * buttons, the rest passes through to the game) is only as wide as that unit needs.
 */
const val CLUSTER_MAX_WIDTH_SHARE = 0.5f
/** A landscape cluster's padding on each side inside its box, dp. */
internal const val CLUSTER_PADDING = 16f
/** A landscape cluster's content in units: 4.5 wide (the 3-unit d-pad / 3.75-unit N64 or Sega face plus its nudge), 6 tall. */
private const val CLUSTER_W_UNITS = 4.5f
private const val CLUSTER_H_UNITS = 6f

/**
 * A landscape cluster's unit inside its padded box ([innerW] × [innerH] dp): the height's (innerH / 6), as phones get,
 * unless the box (half the screen, [CLUSTER_MAX_WIDTH_SHARE]) is narrower than 4.5 of those: then, and only then, the
 * unit shrinks to fit the width, so the two clusters never overlap. A narrow screen shows the clusters over more of
 * the game, not smaller.
 */
internal fun clusterUnit(innerW: Float, innerH: Float): Float = minOf(innerH / CLUSTER_H_UNITS, innerW / CLUSTER_W_UNITS)

/**
 * [clusterUnit] for a cluster box [h] dp tall on a screen [screenW] dp wide, a display cutout [inset] dp on its own edge
 * (over the game, [symmetricCutoutInset] on both clusters' edges, so both boxes stay mirrored).
 * 800 × 360: (360 − 32) / 6 ≈ 54.7; the Flip's cover, 474 wide: two 4.5 × 53 + 32 = 270.5 dp clusters would meet, so
 * each fits half, (237 − 32) / 4.5 ≈ 45.6.
 */
internal fun landscapeClusterUnit(screenW: Float, h: Float, inset: Float = 0f): Float =
    clusterUnit(screenW * CLUSTER_MAX_WIDTH_SHARE - inset - 2 * CLUSTER_PADDING, h - 2 * CLUSTER_PADDING)

/** The PS2 cluster's d-pad / face diamond and stick boxes, units square, and the gap between the two, units ([dualStickCluster]). */
private const val DUAL_MAIN_UNITS = 2.1f
private const val DUAL_STICK_UNITS = 2.1f
private const val DUAL_GAP_UNITS = 0.1f
/** The L3 / R3 pill, units, and its gap over the stick's box. */
private const val THUMB_PILL_W_UNITS = 1f
private const val THUMB_PILL_H_UNITS = 0.6f
private const val THUMB_PILL_GAP_UNITS = 0.15f

/**
 * One side of the PS2 pad ([PadProfile.dualSticks]) in pad units, [width] x [height] (4.3 x 3: the landscape cluster's
 * 3-unit d-pad row, 4.5 units less the edge nudge). [main] is the d-pad (left) or the face diamond (right) in the top
 * corner by the screen edge, [stick] the stick below it toward the screen's middle (a DualShock 2's left stick sits
 * below-right of its d-pad, the right stick below-left of the face buttons), and [thumb] its L3 / R3 pill centred over
 * the stick. Side by side, not stacked, the two keep their size in the d-pad's row: about 70 % of the other pads' d-pad.
 * [stickGroup] (the stick and its pill, [PadGroup.L_STICK] / [PadGroup.R_STICK]) keeps clear of [main]'s box.
 */
internal data class DualStickCluster(val width: Float, val height: Float, val main: Rect, val stick: Rect, val thumb: Rect) {
    val stickGroup: Rect get() = Rect(stick.left, thumb.top, stick.right, stick.bottom)
}

internal fun dualStickCluster(side: PadSide): DualStickCluster {
    val w = DUAL_MAIN_UNITS + DUAL_GAP_UNITS + DUAL_STICK_UNITS
    val h = 3f
    val main = if (side == PadSide.LEFT) Rect(0f, 0f, DUAL_MAIN_UNITS, DUAL_MAIN_UNITS) else Rect(w - DUAL_MAIN_UNITS, 0f, w, DUAL_MAIN_UNITS)
    val stickLeft = if (side == PadSide.LEFT) w - DUAL_STICK_UNITS else 0f
    val stick = Rect(stickLeft, h - DUAL_STICK_UNITS, stickLeft + DUAL_STICK_UNITS, h)
    val thumbTop = stick.top - THUMB_PILL_GAP_UNITS - THUMB_PILL_H_UNITS
    val thumb = Rect(stick.center.x - THUMB_PILL_W_UNITS / 2f, thumbTop, stick.center.x + THUMB_PILL_W_UNITS / 2f, thumbTop + THUMB_PILL_H_UNITS)
    return DualStickCluster(w, h, main, stick, thumb)
}

/** A stick and its L3 / R3 pill centred over it, pad units; [group] is the box round both. */
internal data class StickPiece(val stick: Rect, val thumb: Rect) {
    val group: Rect get() = Rect(minOf(stick.left, thumb.left), thumb.top, maxOf(stick.right, thumb.right), stick.bottom)
}

/** The portrait PS2 stick's well across, units at a middle-row scale of 1 (the user's own layout on the Fold's cover: 1.35). */
internal const val PS2_PORTRAIT_STICK_UNITS = 1.4f
/** The portrait PS2 sticks' gap under the d-pad / diamond row, units. */
private const val PS2_PORTRAIT_STICK_DROP = 0.1f

/**
 * The portrait PS2 sticks ([StickPiece], left then right) on a pad [widthUnits] x [heightUnits] whose d-pad and diamond
 * are at [stickScale], pad units from the padded pad's top-left: each [PS2_PORTRAIT_STICK_UNITS] x [stickScale] across,
 * its outer edge 0.55 in from the side like the clusters above, under them by the NX mark's room and MENU's row (which
 * stay in the middle). Its pill sits over it, in the cluster's empty lower corner, the group [PS2_PORTRAIT_STICK_DROP]
 * under the row, or higher where the pad's bottom would cut it.
 */
internal fun portraitDualSticks(widthUnits: Float, heightUnits: Float, stickScale: Float): Pair<StickPiece, StickPiece> {
    val d = PS2_PORTRAIT_STICK_UNITS * stickScale
    val rowBottom = 1f + (heightUnits - 3.12f - 3f * stickScale) / 2f + 3f * stickScale   // SpaceBetween, as [portraitCStickBox]
    val groupH = THUMB_PILL_H_UNITS + THUMB_PILL_GAP_UNITS + d
    val top = minOf(rowBottom + PS2_PORTRAIT_STICK_DROP, heightUnits - groupH)
    fun piece(left: Float): StickPiece {
        val stick = Rect(left, top + THUMB_PILL_H_UNITS + THUMB_PILL_GAP_UNITS, left + d, top + groupH)
        val thumb = Rect(stick.center.x - THUMB_PILL_W_UNITS / 2f, top, stick.center.x + THUMB_PILL_W_UNITS / 2f, top + THUMB_PILL_H_UNITS)
        return StickPiece(stick, thumb)
    }
    return piece(0.55f) to piece(widthUnits - 0.55f - d)
}

/**
 * The PS2 sides' scale ([dualStickCluster]) in a portrait middle row [rowWidthUnits] wide (inside its edge insets) whose
 * d-pad is at [stickScale]: that scale, or less so both sides fit the row [MID_MIN_GAP] apart. Their 3 units tall then
 * never pass the row's 3 x [stickScale].
 */
internal fun dualStickScale(rowWidthUnits: Float, stickScale: Float): Float =
    minOf(stickScale, (rowWidthUnits - MID_MIN_GAP) / (2f * DUAL_STICK_CLUSTER_W_UNITS)).coerceAtLeast(0f)

/** The PS2 cluster's width, units ([dualStickCluster]): the landscape reach estimate's face width for that pad. */
internal val DUAL_STICK_CLUSTER_W_UNITS: Float = DUAL_MAIN_UNITS + DUAL_GAP_UNITS + DUAL_STICK_UNITS

/**
 * The width a landscape half's cluster takes, in face-button units, for the reach estimate before the drawn groups are
 * measured: [faceWidthUnits], or the PS2's d-pad / face beside its stick ([dualStickCluster]).
 */
fun landscapeClusterUnits(profile: PadProfile): Float = if (profile.dualSticks) DUAL_STICK_CLUSTER_W_UNITS else faceWidthUnits(profile)

/** A system pill's width in units (MENU, the toggle, SELECT, START), and the gap between two, dp. */
private const val SYSTEM_PILL_W_UNITS = 1.4f
private const val SYSTEM_PILL_GAP = 12f

/** An overlay cluster's inset from its screen edge, units: the right cluster's, which the left mirrors over the game. */
internal const val OVERLAY_EDGE_NUDGE = 0.2f

/**
 * How far an over-the-game cluster's system row (two pills) reaches in from its screen edge on a [screenW] ×
 * [h] dp screen with a cutout [inset] dp: the inset, the cluster padding, the nudge, the pills and the gap between them.
 */
internal fun overlaySystemRowReach(screenW: Float, h: Float, inset: Float): Float {
    val unit = landscapeClusterUnit(screenW, h, inset)
    return inset + CLUSTER_PADDING + unit * (OVERLAY_EDGE_NUDGE + 2 * SYSTEM_PILL_W_UNITS) + SYSTEM_PILL_GAP
}

/**
 * The widest the top-centre in-game notifications may be over the landscape overlay pad, so they stay
 * between its two system rows (MENU / STICK top-left, SELECT / START top-right) and a long toast wraps instead.
 */
fun nxTopCentreMaxWidth(screenW: Float, h: Float, inset: Float): Float =
    (screenW - 2 * overlaySystemRowReach(screenW, h, inset)).coerceAtLeast(0f)

/** The shoulder row's reach from its screen edge beyond the edge margin, units: nudge, bumper, trigger and the gap. */
private const val SHOULDER_REACH_UNITS = OVERLAY_EDGE_NUDGE + 1f + 0.72f + 0.35f

/**
 * How far the shoulders (L1 L2 / R2 R1 in the top corners) of a pad beside the picture reach in from their screen edge:
 * the [edge] margin (padding and any cutout) plus the shoulders' 0.2 + 1 + 0.72 + 0.35 [unit]s. Same length unit as both.
 */
fun shoulderReach(unit: Float, edge: Float): Float = edge + unit * SHOULDER_REACH_UNITS

/**
 * The widest the top-centre notifications may be on a [screenW] wide screen so they clear only the shoulders in the top
 * corners ([shoulderReach]), not the system rows, which sit lower beside the picture; a long toast wraps instead.
 */
fun topCentreMaxWidth(screenW: Float, shoulderReach: Float): Float = (screenW - 2 * shoulderReach).coerceAtLeast(0f)

/** The width a cluster at [unit] takes, its padding and a cutout [inset] on its edge included, dp. */
internal fun landscapeClusterWidth(unit: Float, inset: Float = 0f): Float = CLUSTER_W_UNITS * unit + 2 * CLUSTER_PADDING + inset

/**
 * The inset both landscape clusters over the game keep from their screen edge: the deeper of the display
 * cutout's [start] and [end] insets (0 without a cutout), so a camera hole on one side pushes both clusters in alike
 * and the controls sit equally far from either edge.
 */
fun symmetricCutoutInset(start: Float, end: Float): Float = maxOf(start, end)

/** Held-set tracking + guaranteed release on dispose, shared by the whole pad and the split clusters. */
@Composable
private fun rememberEmit(onEvent: (PadEvent) -> Unit): (PadEvent) -> Unit {
    val held = remember { mutableSetOf<NxButton>() }
    DisposableEffect(Unit) { onDispose { held.toList().forEach { onEvent(PadEvent.Up(it)) }; held.clear() } }
    return { event ->
        when (event) { is PadEvent.Down -> held += event.button; is PadEvent.Up -> held -= event.button }
        onEvent(event)
    }
}

/** A landscape cluster's small system buttons: MENU, the d-pad / stick toggle, SELECT, the Wii's HOME, START and MOTION. */
internal enum class PadSystemKey { MENU, TOGGLE, SELECT, HOME, START, MOTION }

/** Which landscape cluster a system row belongs to. */
internal enum class PadSide { LEFT, RIGHT }

/**
 * The system buttons of [side]'s cluster, in order. Left: MENU then the d-pad / stick toggle (none on the PS2's pad,
 * which draws both, [PadProfile.dualSticks]); right: SELECT (unless [profile] hides it), HOME (when [profile] has one)
 * then START. The Wii's MOTION button follows the toggle when [motion] is given and [profile] has one.
 */
internal fun padSystemRow(side: PadSide, profile: PadProfile = PadProfile.STANDARD, motion: Boolean = false): List<PadSystemKey> =
    when (side) {
        PadSide.LEFT -> listOfNotNull(
            PadSystemKey.MENU, PadSystemKey.TOGGLE.takeIf { !profile.dualSticks }, PadSystemKey.MOTION.takeIf { motion && profile.motionButton },
        )
        PadSide.RIGHT -> listOfNotNull(
            PadSystemKey.SELECT.takeIf { !profile.isHidden(NxButton.SELECT) }, PadSystemKey.HOME.takeIf { profile.homeButton }, PadSystemKey.START,
        )
    }

/**
 * The pill count of the widest landscape system row [profile]'s pad draws ([padSystemRow], the Wii's MOTION when
 * [motion]): 3 for the Wii's SELECT HOME START (and its MENU STICK MOTION), else 2. The layout model's
 * reach estimate sizes the clusters' boxes by it until the drawn groups are measured.
 */
fun systemRowPills(profile: PadProfile, motion: Boolean): Int =
    maxOf(padSystemRow(PadSide.LEFT, profile, motion).size, padSystemRow(PadSide.RIGHT, profile).size)

/**
 * Beside the picture, how many pills in from the screen edge [side]'s touch shortcut sits on the second row: left
 * under the d-pad / stick toggle (BUTTON 1 under STICK), right under SELECT (BUTTON 2 under SELECT, so on the Wii's
 * SELECT HOME START the spots under HOME and START stay empty); on a pad without SELECT, one in from START.
 */
internal fun besideShortcutFromEdge(side: PadSide, keys: List<PadSystemKey>): Int = when (side) {
    PadSide.LEFT -> keys.indexOf(PadSystemKey.TOGGLE).coerceAtLeast(0)
    PadSide.RIGHT -> keys.indexOf(PadSystemKey.SELECT).let { i -> if (i >= 0) keys.lastIndex - i else 1 }
}

/**
 * Where an over-the-game cluster's touch shortcut sits: under the pill [fromEdge] pills in from the screen edge of its
 * system row ([padSystemRow]), or, when not [under], on that row's line in the place after its last pill. Left: under the
 * d-pad / stick toggle. Right: under SELECT; on a pad without one (GameCube, N64, Sega 3-button) in SELECT's empty place
 * beside START, as there is no room under the row there: those clusters' shoulders sit higher (the C-stick's and the
 * N64's taller face clusters) or a 0.72-unit pill would take the room SELECT leaves.
 */
internal data class OverlayShortcutPlace(val fromEdge: Int, val under: Boolean)

internal fun overlayShortcutPlace(side: PadSide, keys: List<PadSystemKey>): OverlayShortcutPlace = when (side) {
    PadSide.LEFT -> OverlayShortcutPlace(keys.indexOf(PadSystemKey.TOGGLE).coerceAtLeast(0), under = true)
    PadSide.RIGHT -> keys.indexOf(PadSystemKey.SELECT).let { i ->
        if (i >= 0) OverlayShortcutPlace(keys.lastIndex - i, under = true) else OverlayShortcutPlace(keys.size, under = false)
    }
}

/** The gap between an over-the-game cluster's system row and the touch shortcut under it, units ([overlayShortcutTop]). */
internal const val OVERLAY_SHORTCUT_GAP = 0.2f

/**
 * How far below its cluster's top an over-the-game touch shortcut at [place] sits, units: 0 on the system row's line, else
 * under the row's 0.72-unit pills: on the line of STYLUS / SWAP ([tools], 0.35 under the row) where the DS / 3DS draws
 * them, else [OVERLAY_SHORTCUT_GAP], which keeps a height-bound cluster (6 units) clear: the pill ends 1.64 units down,
 * above the 1.65 where the d-pad's or diamond's shoulders start and the 1.79 of their 0.72-unit triggers.
 */
internal fun overlayShortcutTop(place: OverlayShortcutPlace, tools: Boolean): Float = when {
    !place.under -> 0f
    tools -> 0.72f + 0.35f
    else -> 0.72f + OVERLAY_SHORTCUT_GAP
}

/**
 * The shoulder buttons [side] draws, outside in: L1 then L2 on the left, R2 then R1 on the right, each unless [profile]
 * hides it (the sideways and upright Wiimotes have no L1 / R1 / L2), so a hidden shoulder leaves no gap.
 */
internal fun padShoulders(side: PadSide, profile: PadProfile = PadProfile.STANDARD): List<NxButton> =
    when (side) {
        PadSide.LEFT -> listOf(NxButton.L1, NxButton.L2)
        PadSide.RIGHT -> listOf(NxButton.R2, NxButton.R1)
    }.filterNot(profile::isHidden)

/** [buttons] (from [padShoulders]) in a row, 8 dp apart: the bumpers L1 / R1 at [bumper] size, the triggers at [trigger]. */
@Composable
private fun ShoulderButtons(
    buttons: List<NxButton>, profile: PadProfile, bumper: Dp, trigger: Dp, emit: (PadEvent) -> Unit, latched: Set<NxButton>,
) {
    buttons.forEachIndexed { i, button ->
        if (i > 0) Spacer(Modifier.width(8.dp))
        val size = if (button == NxButton.L1 || button == NxButton.R1) bumper else trigger
        // The shoulders are big discs, on the band as over the game: the label follows the disc, not the small pill size.
        val font = (size.value * SHOULDER_LABEL_OF_DISC).sp
        PadButton(button, size, emit, shape = PadStyle.pillShape, label = profile.label(button), fontSize = font, lit = button in latched)
    }
}

/**
 * How far the right shoulders drop beside the C-stick cluster, in units: the cluster's C_STICK_LANDSCAPE_DROP less the
 * part of it the d-pad's box makes up on the left (measured on the Fold's cover screen: 0.5 put R / ZR 0.09 low).
 */
private const val C_STICK_SHOULDER_DROP = 0.41f

/** A shoulder label's size as a share of its disc ([ShoulderButtons]). */
private const val SHOULDER_LABEL_OF_DISC = 0.36f

/**
 * The left cluster's inset from its screen edge, in units. Over the game it mirrors the right cluster's
 * 0.2; elsewhere (main menu, flex-mode pad) it stays 0.6, shifting the cluster inward off the edge.
 */
internal fun leftClusterNudge(overGame: Boolean): Float = if (overGame) OVERLAY_EDGE_NUDGE else 0.6f

/** A landscape cluster's rows. */
internal enum class PadRow { SYSTEM, SHOULDERS, CLUSTER }

/**
 * A landscape cluster's rows, top to bottom. Over the game ([overGame]) the system row goes to the top of
 * the box (the screen's top corner, away from the thumbs that kept touching it) and the rest stays bottom-anchored;
 * elsewhere (main menu, flex-mode pad under the hinge) it stays lowest, under the d-pad / face cluster. Beside the
 * picture ([besidePicture], which wins over [overGame]: the pad has its own column) the system row is lowest too.
 */
internal fun clusterRows(overGame: Boolean, besidePicture: Boolean = false): List<PadRow> =
    if (overGame && !besidePicture) listOf(PadRow.SYSTEM, PadRow.SHOULDERS, PadRow.CLUSTER)
    else listOf(PadRow.SHOULDERS, PadRow.CLUSTER, PadRow.SYSTEM)

/**
 * Whether a pad beside the picture draws its second row (STYLUS / BUTTON 1 left, BUTTON 2 / SWAP right) for this
 * half's [tools], [shortcuts] and [placeholders]. The caller ORs both halves' answers into each half's `secondRow`, so
 * L1 / R1 and the d-pad / face cluster line up whichever half has the row.
 */
internal fun padSecondRow(tools: PadTools?, shortcuts: Map<TouchSlot, TouchShortcut>, placeholders: Boolean): Boolean =
    tools != null || shortcuts.isNotEmpty() || placeholders

/**
 * The `secondRow` both halves of a pad beside the picture take: either half's [padSecondRow], the left with STYLUS and
 * touch shortcut 1, the right with SWAP and touch shortcut 2, so a row on one side keeps the other's rows level.
 */
fun besideSecondRow(tools: PadTools?, shortcuts: Map<TouchSlot, TouchShortcut>, placeholders: Boolean): Boolean =
    padSecondRow(tools, shortcuts.filterKeys { it == TouchSlot.ONE }, placeholders) ||
        padSecondRow(tools, shortcuts.filterKeys { it == TouchSlot.TWO }, placeholders)

/**
 * The row of [rows] ([clusterRows]) whose spacer above takes the free height: over the game ([overGame])
 * the one under the system row (pushing the rest to the bottom, touches there pass to the game), found by the row, not
 * by its place; none elsewhere, or when nothing is under it.
 */
internal fun flexSpacerRow(rows: List<PadRow>, overGame: Boolean): Int? =
    if (!overGame) null else rows.indexOf(PadRow.SYSTEM).takeIf { it in 0 until rows.lastIndex }?.plus(1)

/** The spacer above a row: the free height when [flex] ([flexSpacerRow]), else [gap]. */
private fun ColumnScope.rowSpacer(flex: Boolean, gap: Dp): Modifier =
    if (flex) Modifier.weight(1f) else Modifier.height(gap)

/** The [keys] of a [padSystemRow] as pills [sysH] x [sysW], 12 dp apart; the toggle flips [useJoystick] via [onToggle]. */
@Composable
private fun SystemRow(
    keys: List<PadSystemKey>,
    emit: (PadEvent) -> Unit,
    profile: PadProfile,
    sysH: Dp,
    sysW: Dp,
    useJoystick: Boolean,
    onToggle: () -> Unit,
    motion: PadMotion?,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        keys.forEachIndexed { i, key ->
            if (i > 0) Spacer(Modifier.width(SYSTEM_PILL_GAP.dp))
            when (key) {
                PadSystemKey.MENU -> PadButton(NxButton.MENU, sysH, emit, shape = PadStyle.pillShape, width = sysW)
                PadSystemKey.TOGGLE -> ToggleButton(if (useJoystick) "D-PAD" else "STICK", onToggle, sysH, sysW)
                PadSystemKey.MOTION -> motion?.let { MotionButton(it, sysH, sysW) }
                PadSystemKey.SELECT -> PadButton(NxButton.SELECT, sysH, emit, shape = PadStyle.pillShape, width = sysW, label = profile.label(NxButton.SELECT))
                PadSystemKey.HOME -> PadButton(NxButton.HOME, sysH, emit, shape = PadStyle.pillShape, width = sysW, label = profile.label(NxButton.HOME))
                PadSystemKey.START -> PadButton(NxButton.START, sysH, emit, shape = PadStyle.pillShape, width = sysW, label = profile.label(NxButton.START))
            }
        }
    }
}

/** The hidden-band overlay's rows in units: the shoulders, the system row, and the gap between each pair of rows. */
internal const val OVERLAY_SHOULDER_UNITS = 1f
internal const val OVERLAY_SYSTEM_UNITS = 0.72f
internal const val OVERLAY_GAP_UNITS = 0.35f

/**
 * The hidden-band overlay's height in button units, inside its padding: the shoulders, the middle row at [midScale]
 * ([midRowScale]) 3 units tall, the system row and the two gaps between the rows. The layout model's overlay height
 * (`overlayHeightPx`) is this times the unit plus the padding.
 */
internal fun portraitOverlayUnits(midScale: Float): Float =
    OVERLAY_SHOULDER_UNITS + 2 * OVERLAY_GAP_UNITS + 3f * midScale + OVERLAY_SYSTEM_UNITS

/**
 * The touch boxes beside the hidden-band overlay's C-stick ([overlayCStick]), in middle-row units (from the row's left
 * end, after the edge nudge, its top 0): the d-pad / stick's whole 3 x [stickScale] square on the left (the d-pad takes
 * touches across its box, not only on its arms; so does the stick's well) and the X/Y/B/A diamond's 3 x [faceScale]
 * box on the right of a row [rowWidthUnits] wide, both centred in the row's 3 x [stickScale] height.
 */
internal fun overlayClusterBoxes(rowWidthUnits: Float, stickScale: Float, faceScale: Float): List<Rect> {
    val faceT = 1.5f * (stickScale - faceScale)
    return listOf(
        Rect(0f, 0f, 3f * stickScale, 3f * stickScale),
        Rect(rowWidthUnits - 3f * faceScale, faceT, rowWidthUnits, faceT + 3f * faceScale),
    )
}

/**
 * The hidden-band overlay's New 3DS C-stick box in middle-row units ([overlayClusterBoxes]' frame): centred across the
 * row between the d-pad / stick and the diamond, its well's top at the row's top. [C_STICK_DIAMETER] x [stickScale]
 * across where its well keeps [PORTRAIT_C_STICK_BUTTON_GAP] off both clusters' touch boxes, else as big as keeps it
 * (the gap between the boxes less that margin each side). Inside the row's height, so the overlay keeps its
 * [portraitOverlayUnits] height; only a touch inside the round well is the stick's.
 */
internal fun overlayCStick(rowWidthUnits: Float, stickScale: Float, faceScale: Float): Rect {
    val boxes = overlayClusterBoxes(rowWidthUnits, stickScale, faceScale)
    val cx = (boxes[0].right + boxes[1].left) / 2f
    val room = (boxes[1].left - boxes[0].right) / 2f - PORTRAIT_C_STICK_BUTTON_GAP
    val r = minOf(C_STICK_DIAMETER * stickScale / 2f, room).coerceAtLeast(0f)
    return Rect(cx - r, 0f, cx + r, 2f * r)
}

/**
 * How wide one side's group of the hidden-band overlay's top row is, dp: its [shoulders] (L1 / R1 [unit] wide, the
 * triggers [trigger], 8 dp apart), and with [tools] the STYLUS or SWAP pill ([pill] wide) [gap] from them.
 */
internal fun overlayTopSideWidth(shoulders: List<NxButton>, unit: Float, trigger: Float, pill: Float, gap: Float, tools: Boolean): Float {
    val discs = shoulders.sumOf { (if (it == NxButton.L1 || it == NxButton.R1) unit else trigger).toDouble() }.toFloat() +
        8f * (shoulders.size - 1).coerceAtLeast(0)
    return discs + if (tools) gap + pill else 0f
}

/**
 * The hidden-band overlay's touch shortcut width, dp: [pill] (MENU's), unless a button beside each side's group
 * ([sideWidth], [overlayTopSideWidth]), [gap] from it, would leave less than a [gap] between the two in a row
 * [rowWidth] wide (the New 3DS's ZL / ZR and STYLUS / SWAP on a phone): then what keeps that gap.
 */
internal fun overlayTopShortcutWidth(rowWidth: Float, pill: Float, gap: Float, sideWidth: Float): Float =
    minOf(pill, (rowWidth - 2 * (sideWidth + gap) - gap) / 2).coerceAtLeast(0f)

/**
 * The portrait pad with its band hidden: one see-through piece for the bottom of the screen. The shoulders sit in the top
 * corners (the DS / 3DS STYLUS and SWAP beside them, [tools]), each with its touch shortcut beside it toward the
 * middle when assigned ([shortcuts]); the d-pad / stick and the face cluster under them; and one left-aligned row of
 * MENU, the toggle, SELECT and START; the show / hide chevron is the host's, at the row's right end. [unit] and the
 * middle row's scale (from [bandWidthUnits] x [bandHeightUnits], the band this replaces) match the band's buttons on
 * this screen; the height is
 * [portraitOverlayUnits] units plus the padding. No background, and only the buttons take touches: the gaps pass to
 * the game. The New 3DS C-stick ([onCStick], sent as the right stick) sits in the middle row between the clusters
 * ([overlayCStick]), inside the row's height. [analog] is the joystick's mode, as on the portrait band. [mic] (DS / 3DS)
 * is the fixed mic button, first in the bottom row.
 */
@Composable
fun PortraitOverlayPad(
    onEvent: (PadEvent) -> Unit,
    onStick: (Float, Float) -> Unit,
    unit: Dp,
    modifier: Modifier = Modifier,
    analog: Boolean = false,
    profile: PadProfile = PadProfile.STANDARD,
    useJoystick: Boolean? = null,
    onUseJoystickChange: (Boolean) -> Unit = {},
    latched: Set<NxButton> = emptySet(),
    shortcuts: Map<TouchSlot, TouchShortcut> = emptyMap(),
    onShortcut: (TouchSlot, Boolean) -> Unit = { _, _ -> },
    bandWidthUnits: Float = 9f,
    bandHeightUnits: Float = 6.2f,
    tools: PadTools? = null,
    onCStick: ((Float, Float) -> Unit)? = null,
    mic: PadMic? = null,
) {
    val emit = rememberEmit(onEvent)
    val stick = rememberStickChoice(useJoystick, onUseJoystickChange)
    val mid = midRowScale(bandWidthUnits, bandHeightUnits)
    val faceScale = faceClusterScale(bandWidthUnits, bandHeightUnits, mid, faceWidthUnits(profile), faceHeightUnits(profile))
    // Always Charcoal, like the clusters over the game: the overlay is translucent over the picture.
    CompositionLocalProvider(LocalPadColors provides CharcoalPadColors) {
        Column(modifier.fillMaxSize().padding(CLUSTER_PADDING.dp), verticalArrangement = Arrangement.Bottom) {
            val gap = unit * OVERLAY_GAP_UNITS
            val sysH = unit * OVERLAY_SYSTEM_UNITS
            val pill = unit * 1.0f
            val nudge = unit * OVERLAY_EDGE_NUDGE
            val sides = Modifier.fillMaxWidth().padding(horizontal = nudge)
            BoxWithConstraints(sides.height(unit * OVERLAY_SHOULDER_UNITS)) {
                // Each touch button beside its side's STYLUS / SWAP (or shoulders), toward the middle: their own width,
                // narrowed where the two sides would come within a gap of each other ([overlayTopShortcutWidth]).
                val w = overlayTopShortcutWidth(
                    maxWidth.value, pill.value, gap.value,
                    PadSide.entries.maxOf { overlayTopSideWidth(padShoulders(it, profile), unit.value, sysH.value, pill.value, gap.value, tools != null) },
                ).dp
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShoulderButtons(padShoulders(PadSide.LEFT, profile), profile, unit, sysH, emit, latched)
                        if (tools != null) { Spacer(Modifier.width(gap)); StylusButton(tools, sysH, pill) }
                        ShortcutPiece(TouchSlot.ONE, shortcuts, false, onShortcut, sysH, w, Modifier.padding(start = gap))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShortcutPiece(TouchSlot.TWO, shortcuts, false, onShortcut, sysH, w, Modifier.padding(end = gap))
                        if (tools != null) { SwapButton(tools, sysH, pill); Spacer(Modifier.width(gap)) }
                        ShoulderButtons(padShoulders(PadSide.RIGHT, profile), profile, unit, sysH, emit, latched)
                    }
                }
            }
            Spacer(Modifier.height(gap))
            Box(sides.height(unit * 3f * mid)) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val stickMid = unit * mid
                    val faceMid = unit * faceScale
                    if (profile.dualSticks) {   // the PS2's sides, as on the band
                        val dual = unit * dualStickScale(bandWidthUnits - 2f * OVERLAY_EDGE_NUDGE, mid)
                        DualStickSide(PadSide.LEFT, profile, dual, emit, onStick)
                        DualStickSide(PadSide.RIGHT, profile, dual, emit, onCStick)
                    } else {
                        if (stick.useJoystick) NeuJoystick(stickMid, emit, analog = analog, onStick = onStick) else NeuDpad(stickMid, emit)
                        FaceClusterBox(profile, faceMid, (faceMid.value * 0.46f).sp, emit)
                    }
                }
                // The New 3DS C-stick between the clusters: drawn last, so above them; clipped round, so only a touch
                // inside the well ([cStickWellHit]) is its and one on the box's corners falls through to the cluster.
                if (profile.cStick && onCStick != null) {
                    val c = overlayCStick(bandWidthUnits - 2f * OVERLAY_EDGE_NUDGE, mid, faceScale)
                    if (c.width > 0f) {
                        NeuJoystick(unit * (c.width / 3f), emit, Modifier.offset(x = unit * c.left, y = unit * c.top).clip(CircleShape), analog = true, onStick = onCStick)
                    }
                }
            }
            Spacer(Modifier.height(gap))
            Row(Modifier.padding(start = nudge).height(sysH), verticalAlignment = Alignment.CenterVertically) {
                val g = SYSTEM_PILL_GAP.dp
                if (mic != null) { MicFace(mic, sysH); Spacer(Modifier.width(g)) }   // bottom left, the row's height
                PadButton(NxButton.MENU, sysH, emit, shape = PadStyle.pillShape, width = pill)
                Spacer(Modifier.width(g))
                if (!profile.dualSticks) {
                    ToggleButton(if (stick.useJoystick) "D-PAD" else "STICK", stick::toggle, sysH, pill)
                    Spacer(Modifier.width(g))
                }
                PadButton(NxButton.SELECT, sysH, emit, shape = PadStyle.pillShape, width = pill, label = profile.label(NxButton.SELECT))
                Spacer(Modifier.width(g))
                PadButton(NxButton.START, sysH, emit, shape = PadStyle.pillShape, width = pill, label = profile.label(NxButton.START))
            }
        }
    }
}

/**
 * Left cluster (landscape): [clusterRows] of MENU/toggle, shoulders L1/L2 and the d-pad/analog-stick, pushed to the
 * bottom-left so the d-pad sits low for the left thumb. Transparent. [overGame] (the clusters drawn over the game)
 * moves the MENU/toggle row to the top of the box. Touch shortcut 1 ([shortcuts], [onShortcut], [shortcutPlaceholders]
 * as on [VirtualPad]) sits under the toggle over the game ([overlayShortcutPlace]), else beside the shoulders, toward
 * the screen's middle. [mic] (DS / 3DS) is the fixed mic button, right of L ([shoulderMicEnd]).
 */
@Composable
fun VirtualPadLeft(
    onEvent: (PadEvent) -> Unit,
    onStick: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    profile: PadProfile = PadProfile.STANDARD,
    overGame: Boolean = false,
    useJoystick: Boolean? = null,
    onUseJoystickChange: (Boolean) -> Unit = {},
    motion: PadMotion? = null,
    latched: Set<NxButton> = emptySet(),
    layout: PadLayout = PadLayout.EMPTY,
    onGroupBounds: PadGroupBoundsListener? = null,
    tools: PadTools? = null,
    shortcuts: Map<TouchSlot, TouchShortcut> = emptyMap(),
    onShortcut: (TouchSlot, Boolean) -> Unit = { _, _ -> },
    shortcutPlaceholders: Boolean = false,
    besidePicture: Boolean = false,
    unitOverride: Dp? = null,
    secondRow: Boolean = false,
    mic: PadMic? = null,
) {
    val emit = rememberEmit(onEvent)
    val stick = rememberStickChoice(useJoystick, onUseJoystickChange)
    // Always Charcoal: the clusters sit translucent over the game, whatever the portrait Pad theme.
    CompositionLocalProvider(LocalPadColors provides CharcoalPadColors) {
        BoxWithConstraints(modifier.fillMaxSize().padding(CLUSTER_PADDING.dp)) {
            val unit = unitOverride ?: clusterUnit(maxWidth.value, maxHeight.value).dp
            val arrange = rememberPadArrange(layout, with(LocalDensity.current) { unit.toPx() }, onGroupBounds)
            val bumper = unit * 1.0f
            val trigger = unit * 0.72f
            val sysH = unit * 0.72f
            val sysW = unit * SYSTEM_PILL_W_UNITS
            val nudge = unit * leftClusterNudge(overGame || besidePicture)   // inset from the left screen edge
            val gap = unit * 0.35f
            val systemKeys = padSystemRow(PadSide.LEFT, profile, motion != null)
            ProvidePadArrange(arrange) {
            Box(Modifier.fillMaxSize().padArrangeRoot(arrange)) {
            // Over the game: touch shortcut 1 under the toggle, drawn first (under the rest, as the editor's hit order has it).
            if (overGame && !besidePicture) {
                val place = overlayShortcutPlace(PadSide.LEFT, systemKeys)
                ShortcutPiece(
                    TouchSlot.ONE, shortcuts, shortcutPlaceholders, onShortcut, sysH, sysW,
                    Modifier.padding(start = nudge + (sysW + SYSTEM_PILL_GAP.dp) * place.fromEdge, top = unit * overlayShortcutTop(place, tools != null)),
                )
            }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.Start) {
                val rows = clusterRows(overGame, besidePicture)
                val flex = flexSpacerRow(rows, overGame && !besidePicture)
                rows.forEachIndexed { i, row ->
                    if (i > 0) Spacer(rowSpacer(i == flex, gap))
                    when (row) {
                        PadRow.SYSTEM -> Box(Modifier.heightIn(min = if (besidePicture && secondRow) sysH * 2 + gap else 0.dp)) {
                            PadGroupBox(PadGroup.SYSTEM_LEFT, Modifier.padding(start = nudge)) {
                                Column(horizontalAlignment = Alignment.Start) {
                                    SystemRow(systemKeys, emit, profile, sysH, sysW, stick.useJoystick, stick::toggle, motion)
                                    if (tools != null) {   // under MENU
                                        Spacer(Modifier.height(gap))
                                        StylusButton(tools, sysH, sysW)
                                    }
                                }
                            }
                            // Beside the picture: touch shortcut 1 on the second row, under the toggle (the column stays
                            // whether or not STYLUS is there, so the halves keep their rows).
                            if (besidePicture && secondRow) {
                                ShortcutPiece(
                                    TouchSlot.ONE, shortcuts, shortcutPlaceholders, onShortcut, sysH, sysW,
                                    Modifier.padding(start = nudge + (sysW + SYSTEM_PILL_GAP.dp) * besideShortcutFromEdge(PadSide.LEFT, systemKeys), top = sysH + gap),
                                )
                            }
                        }
                        // With MENU's row under the d-pad (main menu, flex-mode pad), touch shortcut 1 sits beside the
                        // shoulders, toward the middle: the row is the shoulders' height already, so nothing else moves.
                        PadRow.SHOULDERS -> Row(verticalAlignment = Alignment.CenterVertically) {
                            PadGroupBox(PadGroup.L_SHOULDERS, Modifier.padding(start = nudge)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ShoulderButtons(padShoulders(PadSide.LEFT, profile), profile, bumper, trigger, emit, latched)
                                }
                            }
                            // The mic right of L (and ZL): no group, so it stays here whatever the Pad layout.
                            if (mic != null) MicFace(mic, micButtonSize(unit.value).dp, Modifier.padding(start = 8.dp).zIndex(1f))
                            if (!overGame && !besidePicture) ShortcutPiece(TouchSlot.ONE, shortcuts, shortcutPlaceholders, onShortcut, sysH, sysW, Modifier.padding(start = gap))
                        }
                        PadRow.CLUSTER -> if (profile.dualSticks) DualStickSide(PadSide.LEFT, profile, unit, emit, onStick, Modifier.padding(start = nudge))
                        else PadGroupBox(PadGroup.DPAD, Modifier.padding(start = nudge)) {
                            if (stick.useJoystick) NeuJoystick(unit, emit, analog = true, onStick = onStick)
                            else NeuDpad(unit, emit)
                        }
                    }
                }
            }
            }
            }
        }
    }
}

/**
 * Right cluster (landscape): [clusterRows] of SELECT/START, shoulders R1/R2 and the X/Y/B/A diamond, pushed to the
 * bottom-right so the face buttons sit low and toward the edge for the right thumb. Transparent. [overGame] moves
 * SELECT/START to the top of the box. Touch shortcut 2 ([shortcuts], [onShortcut], [shortcutPlaceholders] as on
 * [VirtualPad]) sits under SELECT over the game ([overlayShortcutPlace]), else beside the shoulders, toward the
 * screen's middle.
 */
@Composable
fun VirtualPadRight(
    onEvent: (PadEvent) -> Unit,
    modifier: Modifier = Modifier,
    profile: PadProfile = PadProfile.STANDARD,
    overGame: Boolean = false,
    onCStick: ((Float, Float) -> Unit)? = null,
    latched: Set<NxButton> = emptySet(),
    layout: PadLayout = PadLayout.EMPTY,
    onGroupBounds: PadGroupBoundsListener? = null,
    tools: PadTools? = null,
    shortcuts: Map<TouchSlot, TouchShortcut> = emptyMap(),
    onShortcut: (TouchSlot, Boolean) -> Unit = { _, _ -> },
    shortcutPlaceholders: Boolean = false,
    besidePicture: Boolean = false,
    unitOverride: Dp? = null,
    secondRow: Boolean = false,
) {
    val emit = rememberEmit(onEvent)
    // Always Charcoal: the clusters sit translucent over the game, whatever the portrait Pad theme.
    CompositionLocalProvider(LocalPadColors provides CharcoalPadColors) {
        BoxWithConstraints(modifier.fillMaxSize().padding(CLUSTER_PADDING.dp)) {
            val unit = unitOverride ?: clusterUnit(maxWidth.value, maxHeight.value).dp
            val arrange = rememberPadArrange(layout, with(LocalDensity.current) { unit.toPx() }, onGroupBounds)
            val bumper = unit * 1.0f
            val trigger = unit * 0.72f
            val sysH = unit * 0.72f
            val sysW = unit * SYSTEM_PILL_W_UNITS
            val glyph = (unit.value * 0.46f).sp
            val nudge = unit * OVERLAY_EDGE_NUDGE   // small inset from the right screen edge
            val gap = unit * 0.35f
            val systemKeys = padSystemRow(PadSide.RIGHT, profile)
            ProvidePadArrange(arrange) {
            Box(Modifier.fillMaxSize().padArrangeRoot(arrange)) {
            // Over the game: touch shortcut 2 under SELECT (in its place where the pad has none), drawn first, as on the left.
            if (overGame && !besidePicture) {
                val place = overlayShortcutPlace(PadSide.RIGHT, systemKeys)
                ShortcutPiece(
                    TouchSlot.TWO, shortcuts, shortcutPlaceholders, onShortcut, sysH, sysW,
                    Modifier.align(Alignment.TopEnd)
                        .padding(end = nudge + (sysW + SYSTEM_PILL_GAP.dp) * place.fromEdge, top = unit * overlayShortcutTop(place, tools != null)),
                )
            }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.End) {
                val rows = clusterRows(overGame, besidePicture)
                val flex = flexSpacerRow(rows, overGame && !besidePicture)
                rows.forEachIndexed { i, row ->
                    if (i > 0) Spacer(rowSpacer(i == flex, gap))
                    when (row) {
                        PadRow.SYSTEM -> Box(Modifier.heightIn(min = if (besidePicture && secondRow) sysH * 2 + gap else 0.dp)) {
                            PadGroupBox(PadGroup.SYSTEM_RIGHT, Modifier.padding(end = nudge).align(Alignment.TopEnd)) {
                                Column(horizontalAlignment = Alignment.End) {
                                    SystemRow(systemKeys, emit, profile, sysH, sysW, false, {}, null)
                                    if (tools != null) {   // under START
                                        Spacer(Modifier.height(gap))
                                        SwapButton(tools, sysH, sysW)
                                    }
                                }
                            }
                            // Beside the picture: touch shortcut 2 on the second row, under SELECT ([besideShortcutFromEdge]).
                            if (besidePicture && secondRow) {
                                ShortcutPiece(
                                    TouchSlot.TWO, shortcuts, shortcutPlaceholders, onShortcut, sysH, sysW,
                                    Modifier.align(Alignment.TopEnd).padding(end = nudge + (sysW + SYSTEM_PILL_GAP.dp) * besideShortcutFromEdge(PadSide.RIGHT, systemKeys), top = sysH + gap),
                                )
                            }
                        }
                        // With the C-stick the face cluster is taller, which would lift the shoulders above the left cluster's:
                        // drawn lower by the difference, level with L / ZL (right of the stick, above X).
                        // With SELECT's row under the face cluster (main menu, flex-mode pad), touch shortcut 2 sits beside
                        // them, toward the middle, as on the left; it does not drop with them, so it stays above the C-stick's well.
                        PadRow.SHOULDERS -> Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!overGame && !besidePicture) ShortcutPiece(TouchSlot.TWO, shortcuts, shortcutPlaceholders, onShortcut, sysH, sysW, Modifier.padding(end = gap))
                            PadGroupBox(
                                PadGroup.R_SHOULDERS,
                                Modifier.padding(end = nudge)
                                    .offset(y = if (profile.cStick && onCStick != null) unit * C_STICK_SHOULDER_DROP else 0.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    ShoulderButtons(padShoulders(PadSide.RIGHT, profile), profile, bumper, trigger, emit, latched)
                                }
                            }
                        }
                        PadRow.CLUSTER -> if (profile.dualSticks) DualStickSide(PadSide.RIGHT, profile, unit, emit, onCStick, Modifier.padding(end = nudge))
                        else PadGroupBox(PadGroup.FACE, Modifier.padding(end = nudge)) {
                            FaceClusterBox(
                                profile, unit, glyph, emit, onCStick = onCStick,
                                cStick = cStickLayout(C_STICK_LANDSCAPE_DROP),   // fits the 4.5 x 6 cluster: 3.49 + nudge wide, 5.92 tall
                            )
                        }
                    }
                }
            }
            }
            }
        }
    }
}
