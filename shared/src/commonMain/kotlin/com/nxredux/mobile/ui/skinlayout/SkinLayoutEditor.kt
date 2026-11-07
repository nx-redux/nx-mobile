package com.nxredux.mobile.ui.skinlayout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.skin.SkinTweak
import com.nxredux.mobile.skin.bare
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import com.nxredux.mobile.ui.nx.ModalKind
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxModal
import com.nxredux.mobile.ui.nx.SkinArt
import kotlin.math.min
import kotlin.math.roundToInt

private val HandleRadius = 11.dp
private val HandleHit = 28.dp   // a touch this near the handle resizes rather than moves

/**
 * The skin layout editor over the whole window [state] was made for: the [backdrop] (the paused game's frame) fitted
 * into the main screen, or into the window when [bare] (the game runs full-screen there), else black; the skin from
 * [picture] / [parts] as [state] places it (only its buttons at [bareAlpha] when [bare], as the overlay draws them); the
 * no-go zone tinted; the selected group outlined with its resize handle. A tap selects a group, a drag moves it, a drag
 * from the handle resizes it. The toolbar saves ([onSave] gets the tweaks), resets the group or all, or cancels
 * ([onClose], after "Discard changes?" when something changed). The skin's buttons do not press here.
 */
@Composable
fun SkinLayoutEditor(
    state: SkinEditorState, picture: ImageBitmap, parts: ImageBitmap?, bare: Boolean, backdrop: ImageBitmap?, title: String,
    onSave: (Map<String, SkinTweak>) -> Unit, onClose: () -> Unit, bareAlpha: Float = 0.6f,
) {
    val colors = LocalNxColors.current
    val placed = state.placed()
    val main = placed.glasses.firstOrNull()?.rect
    var size by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }   // the editor's root offset (0 in a full-screen host)
    var downAt by remember { mutableStateOf(Offset.Zero) }   // the current touch's first point
    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { size = it }.onPlaced { origin = it.positionInRoot() }) {
        val window = PxRect(0f, 0f, size.width.toFloat(), size.height.toFloat())
        if (backdrop != null) {
            val into = if (bare || main == null) window else main
            Canvas(Modifier.fillMaxSize()) {
                val r = fitted(backdrop.width.toFloat(), backdrop.height.toFloat(), into)
                drawImage(
                    backdrop, srcSize = IntSize(backdrop.width, backdrop.height),
                    dstOffset = IntOffset(r.x.roundToInt(), r.y.roundToInt()), dstSize = IntSize(r.width.roundToInt(), r.height.roundToInt()),
                    filterQuality = FilterQuality.Low,
                )
            }
        }
        if (bare) SkinArt(remember(placed) { placed.bare() }, picture, parts, Modifier.fillMaxSize().graphicsLayer(alpha = bareAlpha))
        else SkinArt(placed, picture, parts, Modifier.fillMaxSize().graphicsLayer())
        // the edit layer: takes every touch (taps select, drags move or resize)
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(state) {
                    detectTapGestures { p ->
                        // a tap on the selected group's handle (it reaches past the group's corner) keeps it selected
                        val onHandle = state.selected?.let(state::boxOf)?.let { r ->
                            (p - handle(r, Offset(size.width / 2f, size.height / 2f))).getDistance() <= HandleHit.toPx()
                        } == true
                        if (!onHandle) state.select(p)
                    }
                }
                // where the touch went down (not consumed): a drag picks the group there, not at the point past touch slop
                .pointerInput(state) { awaitEachGesture { downAt = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position } }
                .pointerInput(state) {
                    // a drag from the selected group's handle resizes it: the size follows the touch's distance from the
                    // group's centre, against where it started
                    var dragging = false
                    var resizing = false
                    var startScale = 1f
                    var centre = Offset.Zero
                    var startDist = 1f
                    detectDragGestures(
                        onDragStart = {
                            val rect = state.selected?.let(state::boxOf)
                            val c = Offset(size.width / 2f, size.height / 2f)
                            if (rect != null && (downAt - handle(rect, c)).getDistance() <= HandleHit.toPx()) {
                                resizing = true
                                startScale = state.scaleOf(state.selected!!)
                                centre = Offset(rect.x + rect.width / 2f, rect.y + rect.height / 2f)
                                startDist = (downAt - centre).getDistance().coerceAtLeast(1f)
                            } else {
                                state.select(downAt)
                                dragging = state.selected != null
                            }
                        },
                        onDragEnd = { dragging = false; resizing = false },
                        onDragCancel = { dragging = false; resizing = false },
                        onDrag = { change, delta ->
                            // every drag is the edit layer's, even one from empty space: its lift is not a tap
                            change.consume()
                            val id = state.selected
                            if (resizing && id != null) {
                                val want = startScale * (change.position - centre).getDistance() / startDist
                                state.resize(want / state.scaleOf(id))
                            } else if (dragging) state.drag(delta)
                        },
                    )
                },
        ) {
            val stroke = 2.dp.toPx()
            for (z in state.noGo) {
                drawRect(colors.editorOverlap.copy(alpha = 0.18f), Offset(z.x, z.y), Size(z.width, z.height))   // role: where no group may go, faint
                drawRect(colors.editorOverlap, Offset(z.x, z.y), Size(z.width, z.height), style = Stroke(width = stroke))
            }
            state.selected?.let(state::boxOf)?.let { r ->
                drawRect(colors.accent, Offset(r.x, r.y), Size(r.width, r.height), style = Stroke(width = 3.dp.toPx()))
                val h = handle(r, Offset(size.width / 2f, size.height / 2f))
                drawCircle(colors.accent, radius = HandleRadius.toPx(), center = h)
                drawCircle(Color.White, radius = HandleRadius.toPx(), center = h, style = Stroke(width = 2.dp.toPx()))
            }
        }
        // the toolbar over the main screen's middle (the window's when there is none, or when bare)
        val area = if (bare || main == null) window else main
        Box(Modifier.placedPx(area)) {
            EditorToolbar(
                title, state, Rect(origin, Size(size.width.toFloat(), size.height.toFloat())), onSave = { onSave(state.tweaks) }, onCancel = { if (state.requestCancel()) onClose() },
                Modifier.align(Alignment.Center),
            )
        }
        if (state.confirmDiscard) {
            NxModal(caption = null, kind = ModalKind.CONFIRM, onButton = { b -> if (b == NxButton.A) onClose() else state.keepEditing() }, confirmLabel = "Discard", cancelLabel = "Keep editing") {
                Text("Discard changes?", color = colors.listText, fontFamily = LocalNxFont.current, fontSize = 18.sp)
            }
        }
    }
}

/** At [r] (px, in the editor), sized to it exactly. */
private fun Modifier.placedPx(r: PxRect): Modifier = offset { IntOffset(r.x.roundToInt(), r.y.roundToInt()) }.layout { measurable, _ ->
    val placeable = measurable.measure(Constraints.fixed(r.width.roundToInt().coerceAtLeast(0), r.height.roundToInt().coerceAtLeast(0)))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/** A [w] x [h] picture fitted whole into [r], centred. */
private fun fitted(w: Float, h: Float, r: PxRect): PxRect {
    if (w <= 0f || h <= 0f) return r
    val s = min(r.width / w, r.height / h)
    return PxRect(r.x + (r.width - w * s) / 2f, r.y + (r.height - h * s) / 2f, w * s, h * s)
}

/** The resize handle: the corner of [r] nearest [centre] (the window's middle), so it stays on screen. */
private fun handle(r: PxRect, centre: Offset): Offset =
    listOf(Offset(r.x, r.y), Offset(r.x + r.width, r.y), Offset(r.x, r.y + r.height), Offset(r.x + r.width, r.y + r.height))
        .minBy { (it - centre).getDistanceSquared() }

/**
 * The editor's floating panel: what is edited (or the selected group's size) over a row of small buttons. A drag on the
 * panel (its grip, or anywhere off a button) moves it, kept inside [editor] (root px), so it can be put aside when it covers a
 * group.
 */
@Composable
private fun EditorToolbar(title: String, state: SkinEditorState, editor: Rect, onSave: () -> Unit, onCancel: () -> Unit, modifier: Modifier) {
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
                    // keep the whole panel on screen: the room left on each side bounds this step
                    val dx = delta.x.coerceIn(minOf(0f, editor.left - placed.left), (editor.right - placed.right).coerceAtLeast(0f))
                    val dy = delta.y.coerceIn(minOf(0f, editor.top - placed.top), (editor.bottom - placed.bottom).coerceAtLeast(0f))
                    moved += Offset(dx, dy)
                }
            }
            .background(colors.editorPanel, RoundedCornerShape(14.dp))
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // role: the toolbar's drag handle, faint white
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 28.dp, height = 4.dp).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(50)))
        Text(
            if (sel == null) title else "Size ${(state.scaleOf(sel) * 100).roundToInt()}% · drag the corner",
            color = colors.hint, fontFamily = font, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolbarButton("Reset group", enabled = sel != null, onClick = state::resetGroup)
            ToolbarButton("Reset all", onClick = state::resetAll)
            ToolbarButton("Cancel", onClick = onCancel)
            ToolbarButton("Save", primary = true, onClick = onSave)
        }
    }
}

@Composable
private fun ToolbarButton(label: String, enabled: Boolean = true, primary: Boolean = false, onClick: () -> Unit) {
    val colors = LocalNxColors.current
    val bg = if (primary) colors.accent else colors.editorButton
    val fg = if (primary) colors.onAccent else colors.listText
    Box(
        Modifier.background(bg.copy(alpha = if (enabled) 1f else 0.4f), RoundedCornerShape(50))   // role: a disabled toolbar button
            .pointerInput(enabled) { detectTapGestures { if (enabled) onClick() } }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(label, color = fg, fontFamily = LocalNxFont.current, fontSize = 13.sp) }
}
