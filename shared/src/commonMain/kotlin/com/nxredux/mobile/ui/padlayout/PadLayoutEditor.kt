package com.nxredux.mobile.ui.padlayout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.layout.BesidePreview
import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.layout.besidePreview
import com.nxredux.mobile.layout.portraitSplit
import com.nxredux.mobile.layout.screenClass
import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.settings.PadGroup
import com.nxredux.mobile.settings.PadLayout
import com.nxredux.mobile.settings.PadShape
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import com.nxredux.mobile.ui.nx.ModalKind
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxModal
import com.nxredux.mobile.ui.nx.PadEvent
import com.nxredux.mobile.ui.nx.PadMotion
import com.nxredux.mobile.ui.nx.PadProfile
import com.nxredux.mobile.ui.nx.PadTools
import com.nxredux.mobile.ui.nx.VirtualPad
import com.nxredux.mobile.ui.nx.VirtualPadLeft
import com.nxredux.mobile.ui.nx.VirtualPadRight
import com.nxredux.mobile.ui.nx.besideSecondRow
import com.nxredux.mobile.ui.nx.faceWidthUnits
import com.nxredux.mobile.ui.nx.systemRowPills
import kotlin.math.min
import kotlin.math.roundToInt

private val GameBlock = Color(0xFF3A3A3A)
private val OverlapRed = Color(0xFFE5484D)
private val HandleRadius = 11.dp
private val HandleHit = 28.dp   // a touch this near the handle resizes rather than moves

/**
 * The window facts the editor sizes its pad by, read as the game reads them: the [screen] class, the display's [ydpi]
 * (the button unit) and the display cutout's insets, px.
 */
data class PadEditorWindow(val screen: ScreenClass, val ydpi: Float, val cutoutTop: Float, val cutoutLeft: Float, val cutoutRight: Float)

/**
 * Controls → Pad layout's editor for [shape]: a grey block for the game and the real pad ([profile]'s buttons) at
 * [state]'s layout, both touch shortcut pieces drawn as labelled stand-ins (in game each shows only once it has an
 * action). A tap selects a group, a drag moves it (clamped, snapping home), its corner handle resizes it and the toolbar
 * resets it; overlapping groups are outlined red. The pad's own buttons do not press here: the edit layer takes every
 * touch. [onClose] leaves without saving (after "Discard changes?" when something changed).
 * Both shapes are laid out as the game lays them out on this [window] (null: from the editor's size alone): portrait's
 * band at the game's height under its game area ([portraitSplit]); landscape's halves beside a typical picture
 * ([besidePreview]), with STYLUS / SWAP when the console has them ([tools]).
 */
@Composable
fun PadLayoutEditor(
    shape: PadShape, profile: PadProfile, title: String, state: PadEditorState, onSave: (PadLayout) -> Unit, onClose: () -> Unit,
    window: PadEditorWindow? = null, tools: Boolean = false,
) {
    val colors = LocalNxColors.current
    val noPad: (PadEvent) -> Unit = {}
    val cStick: ((Float, Float) -> Unit)? = if (profile.cStick) { _, _ -> } else null
    // The Wii's MOTION pill where the game draws it ([PadProfile.motionButton]), so the system rows are as wide.
    val motion = remember(profile) { if (profile.motionButton) PadMotion(on = true, onTap = {}, onLongPress = {}) else null }
    var origin by remember { mutableStateOf(Offset.Zero) }   // the edit layer's root offset (0 in a full-screen host)
    var downAt by remember { mutableStateOf(Offset.Zero) }   // the current touch's first point, edit layer px
    var layerCentre by remember { mutableStateOf(Offset.Zero) }   // the edit layer's middle, edit layer px
    var screenSize by remember { mutableStateOf(IntSize.Zero) }   // the editor's whole area, px (the toolbar stays inside)
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { screenSize = it }) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val d = LocalDensity.current.density
        val ydpi = window?.ydpi ?: 0f   // outside 40..1200: the density's dpi, as the game falls back
        // The game area the toolbar sits in: portrait's above the band, landscape's typical picture.
        val gameArea: PxRect
        when (shape) {
            PadShape.PORTRAIT -> {
                val split = portraitSplit(w, h, window?.cutoutTop ?: 0f, ydpi, d)
                gameArea = PxRect(0f, split.gameTop, w, split.bandTop - split.gameTop)
                Box(Modifier.placedPx(gameArea).background(GameBlock))
                Box(Modifier.placedPx(PxRect(0f, split.bandTop, w, split.bandPx))) {
                    VirtualPad(noPad, Modifier.fillMaxSize(), profile = profile, onCStick = cStick, motion = motion, layout = state.layout, onGroupBounds = state, shortcutPlaceholders = true)
                }
            }
            PadShape.OVERLAY -> {
                val padTools = remember(tools) { if (tools) PadTools(stylusOn = false, onStylus = {}, onSwap = {}) else null }
                val secondRow = besideSecondRow(padTools, emptyMap(), placeholders = true)
                val preview = besidePreview(
                    window?.screen ?: screenClass(w.roundToInt(), h.roundToInt(), (min(w, h) / d).toInt()), w, h, ydpi, d,
                    window?.cutoutLeft ?: 0f, window?.cutoutRight ?: 0f, secondRow, faceWidthUnits(profile), systemRowPills(profile, motion = motion != null),
                )
                gameArea = preview.picture
                Box(Modifier.placedPx(gameArea).background(GameBlock))
                BesidePad(profile, state, cStick, motion, padTools, secondRow, preview)
            }
        }
        // The edit layer: over the pads, takes every touch (taps select, drags move).
        Canvas(
            Modifier.fillMaxSize()
                .onPlaced { origin = it.positionInRoot(); layerCentre = Offset(it.size.width / 2f, it.size.height / 2f) }
                .pointerInput(state) {
                    detectTapGestures { p ->
                        // A tap on the selected group's handle (it reaches past the group's corner) keeps it selected.
                        val onHandle = state.selected?.let(state::rectOf)?.let { r ->
                            (p + origin - resizeHandle(r, layerCentre + origin)).getDistance() <= HandleHit.toPx()
                        } == true
                        if (!onHandle) state.select(state.groupAt(p + origin))
                    }
                }
                // Where the touch went down (not consumed): a drag picks the group there, not at the point past touch slop.
                .pointerInput(state) { awaitEachGesture { downAt = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position } }
                .pointerInput(state) {
                    var dragging: PadGroup? = null
                    // A drag from the selected group's handle resizes it: the scale follows the touch's distance from
                    // the group's centre, against where it started.
                    var resizing: PadGroup? = null
                    var startScale = 1f
                    var centre = Offset.Zero
                    var startDist = 1f
                    detectDragGestures(
                        onDragStart = {
                            val down = downAt + origin
                            val sel = state.selected
                            val rect = sel?.let(state::rectOf)
                            if (sel != null && rect != null && (down - resizeHandle(rect, layerCentre + origin)).getDistance() <= HandleHit.toPx()) {
                                resizing = sel
                                state.beginResize()
                                startScale = state.scaleOf(sel)
                                centre = rect.center
                                startDist = (down - centre).getDistance().coerceAtLeast(1f)
                            } else {
                                dragging = state.groupAt(down)?.also { state.select(it) }
                            }
                        },
                        onDragEnd = { dragging?.let(state::endDrag); state.endResize(); dragging = null; resizing = null },
                        onDragCancel = { dragging?.let(state::endDrag); state.endResize(); dragging = null; resizing = null },
                        onDrag = { change, delta ->
                            // Every drag is the edit layer's, even one from empty space: its lift is not a tap.
                            change.consume()
                            if (resizing != null) state.setScale(startScale * (change.position + origin - centre).getDistance() / startDist)
                            else dragging?.let { state.dragBy(it, delta) }
                        },
                    )
                },
        ) {
            val overlap = state.overlapping()
            for (g in state.groups) {
                val r = state.rectOf(g) ?: continue
                val color = when {
                    g == state.selected -> colors.accent
                    g in overlap -> OverlapRed
                    else -> null
                } ?: continue
                drawRect(color, topLeft = r.topLeft - origin, size = r.size, style = Stroke(width = 3.dp.toPx()))
            }
            // The selected group's resize handle.
            state.selected?.let(state::rectOf)?.let { r ->
                val h = resizeHandle(r, layerCentre + origin) - origin
                drawCircle(colors.accent, radius = HandleRadius.toPx(), center = h)
                drawCircle(Color.White, radius = HandleRadius.toPx(), center = h, style = Stroke(width = 2.dp.toPx()))
            }
        }
        // OVERLAY's side columns hold its pad halves, so its toolbar sits over the game block's middle.
        // PORTRAIT's sits at the game block's foot, just above the pad, clear of a top camera cutout.
        Box(Modifier.placedPx(gameArea)) {
            EditorToolbar(
                title, shape, state, screenSize, onSave = { onSave(state.layout) }, onCancel = { if (state.requestCancel()) onClose() },
                Modifier.align(if (shape == PadShape.OVERLAY) Alignment.Center else Alignment.BottomCenter),
            )
        }
        if (state.confirmDiscard) {
            NxModal(caption = null, kind = ModalKind.CONFIRM, onButton = { b -> if (b == NxButton.A) onClose() else state.keepEditing() }, confirmLabel = "Discard", cancelLabel = "Keep editing") {
                Text("Discard changes?", color = colors.listText, fontFamily = LocalNxFont.current, fontSize = 18.sp)
            }
        }
    }
}

/**
 * The landscape pad as the game draws it beside [preview]'s picture: its unit, rows and second row, each half in its
 * box at a side edge with the d-pad / face row at mid-height. The second row is always there, for the touch shortcut
 * stand-ins.
 */
@Composable
private fun BoxScope.BesidePad(
    profile: PadProfile, state: PadEditorState, cStick: ((Float, Float) -> Unit)?, motion: PadMotion?, padTools: PadTools?, secondRow: Boolean, preview: BesidePreview,
) {
    val noPad: (PadEvent) -> Unit = {}
    val density = LocalDensity.current
    val p = preview.placement
    val unit = with(density) { preview.measures.unitPx.toDp() }
    val inset = with(density) { p.extraInsetPx.toDp() }
    val box = Modifier.offset { IntOffset(0, p.topMarginPx.roundToInt()) }.sizePx(p.boxWidthPx.roundToInt(), p.boxHeightPx.roundToInt())
    // Left first, as the game adds its views: the right half draws (and takes a touch) on top.
    VirtualPadLeft(
        noPad, { _, _ -> }, Modifier.align(Alignment.TopStart).then(box).padding(start = inset), profile = profile, overGame = true,
        motion = motion, layout = state.layout, onGroupBounds = state, tools = padTools, shortcutPlaceholders = true,
        besidePicture = true, unitOverride = unit, secondRow = secondRow,
    )
    VirtualPadRight(
        noPad, Modifier.align(Alignment.TopEnd).then(box).padding(end = inset), profile = profile, overGame = true, onCStick = cStick,
        layout = state.layout, onGroupBounds = state, tools = padTools, shortcutPlaceholders = true,
        besidePicture = true, unitOverride = unit, secondRow = secondRow,
    )
}

/** At [r] (px, in the editor), sized to it exactly, as the game places its views. */
private fun Modifier.placedPx(r: PxRect): Modifier =
    offset { IntOffset(r.x.roundToInt(), r.y.roundToInt()) }.sizePx(r.width.roundToInt(), r.height.roundToInt())

/** Exactly [w] x [h] px, as the game sizes a pad half's view (a dp size would round). */
private fun Modifier.sizePx(w: Int, h: Int): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints.fixed(w.coerceAtLeast(0), h.coerceAtLeast(0)))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/**
 * The editor's floating panel: what is edited (or the selected group's size) over a row of small buttons. A drag on
 * the panel (its grip, or anywhere off a button) moves it, kept inside [screenSize], so it can be put aside when it
 * covers a group.
 */
@Composable
private fun EditorToolbar(title: String, shape: PadShape, state: PadEditorState, screenSize: IntSize, onSave: () -> Unit, onCancel: () -> Unit, modifier: Modifier) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val sel = state.selected
    var moved by remember { mutableStateOf(Offset.Zero) }   // dragged from its starting spot, px
    var placed by remember { mutableStateOf(Rect.Zero) }    // where it is now, root px
    Column(
        modifier.padding(12.dp)
            .offset { IntOffset(moved.x.roundToInt(), moved.y.roundToInt()) }
            .onPlaced { placed = it.boundsInRoot() }
            .pointerInput(Unit) {
                detectDragGestures { change, delta ->
                    change.consume()
                    // Keep the whole panel on screen: the room left on each side bounds this step.
                    val dx = delta.x.coerceIn(-placed.left, (screenSize.width - placed.right).coerceAtLeast(0f))
                    val dy = delta.y.coerceIn(-placed.top, (screenSize.height - placed.bottom).coerceAtLeast(0f))
                    moved += Offset(dx, dy)
                }
            }
            .background(Color(0xE6121212), RoundedCornerShape(14.dp))
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 28.dp, height = 4.dp).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(50)))
        Text(
            if (sel == null) "${shape.label} · $title" else "Size ${(state.scaleOf(sel) * 100).roundToInt()}% · drag the corner",
            color = colors.hint, fontFamily = font, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolbarButton("Reset", enabled = sel != null) { state.resetGroup() }
            ToolbarButton("Reset all") { state.resetAll() }
            ToolbarButton("Cancel", onClick = onCancel)
            ToolbarButton("Save", primary = true, onClick = onSave)
        }
    }
}

@Composable
private fun ToolbarButton(label: String, enabled: Boolean = true, primary: Boolean = false, onClick: () -> Unit) {
    val colors = LocalNxColors.current
    val bg = if (primary) colors.accent else Color(0xFF2A2A2A)
    val fg = if (primary) colors.onAccent else colors.listText
    Box(
        Modifier.background(bg.copy(alpha = if (enabled) 1f else 0.4f), RoundedCornerShape(50))
            .pointerInput(enabled) { detectTapGestures { if (enabled) onClick() } }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(label, color = fg, fontFamily = LocalNxFont.current, fontSize = 13.sp) }
}
