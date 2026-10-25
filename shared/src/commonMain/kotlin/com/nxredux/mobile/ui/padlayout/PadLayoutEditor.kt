package com.nxredux.mobile.ui.padlayout

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.settings.PadGroup
import com.nxredux.mobile.settings.PadLayout
import com.nxredux.mobile.settings.PadShape
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import com.nxredux.mobile.ui.nx.ModalKind
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxModal
import com.nxredux.mobile.ui.nx.PadEvent
import com.nxredux.mobile.ui.nx.PadMic
import com.nxredux.mobile.ui.nx.PadProfile
import com.nxredux.mobile.ui.nx.VirtualPad
import com.nxredux.mobile.ui.nx.VirtualPadLeft
import com.nxredux.mobile.ui.nx.VirtualPadRight
import kotlin.math.roundToInt

private val GameBlock = Color(0xFF3A3A3A)
private val OverlapRed = Color(0xFFE5484D)
private val HandleRadius = 11.dp
private val HandleHit = 28.dp   // a touch this near the handle resizes rather than moves

/**
 * Controls → Pad layout's editor for [shape]: a grey block for the game and the real pad ([profile]'s buttons) at
 * [state]'s layout, both touch shortcut pieces drawn as labelled stand-ins (in game each shows only once it has an
 * action). A tap selects a group, a drag moves it (clamped, snapping home), its corner handle resizes it and the toolbar
 * resets it; overlapping groups are outlined red. The pad's own buttons do not press here: the edit layer takes every
 * touch. [onClose] leaves without saving (after "Discard changes?" when something changed). [showMic] draws the DS / 3DS
 * fixed mic button ([padLayoutShowsMic]), so the player sees its spot; it is no group, so it is never selected.
 */
@Composable
fun PadLayoutEditor(shape: PadShape, profile: PadProfile, title: String, state: PadEditorState, onSave: (PadLayout) -> Unit, onClose: () -> Unit, showMic: Boolean = false) {
    val colors = LocalNxColors.current
    val noPad: (PadEvent) -> Unit = {}
    val mic = if (showMic) PadMic(on = false, onTap = {}) else null
    val cStick: ((Float, Float) -> Unit)? = if (profile.cStick) { _, _ -> } else null
    var origin by remember { mutableStateOf(Offset.Zero) }   // the edit layer's root offset (0 in a full-screen host)
    var downAt by remember { mutableStateOf(Offset.Zero) }   // the current touch's first point, edit layer px
    var layerCentre by remember { mutableStateOf(Offset.Zero) }   // the edit layer's middle, edit layer px
    var screenSize by remember { mutableStateOf(IntSize.Zero) }   // the editor's whole area, px (the toolbar stays inside)
    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { screenSize = it }) {
        when (shape) {
            PadShape.PORTRAIT -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(GameBlock))
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    VirtualPad(noPad, Modifier.fillMaxSize(), profile = profile, onCStick = cStick, layout = state.layout, onGroupBounds = state, shortcutPlaceholders = true, mic = mic)
                }
            }
            PadShape.OVERLAY -> Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxHeight().aspectRatio(4f / 3f).align(Alignment.Center).background(GameBlock))
                Row(Modifier.fillMaxSize()) {
                    VirtualPadLeft(noPad, { _, _ -> }, Modifier.weight(1f).fillMaxHeight(), profile = profile, overGame = true, layout = state.layout, onGroupBounds = state, shortcutPlaceholders = true, mic = mic)
                    VirtualPadRight(noPad, Modifier.weight(1f).fillMaxHeight(), profile = profile, overGame = true, onCStick = cStick, layout = state.layout, onGroupBounds = state, shortcutPlaceholders = true)
                }
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
        // OVERLAY's top corners hold its MENU / SELECT / START groups, so its toolbar sits over the game block's middle.
        // PORTRAIT's sits at the game block's foot, just above the pad, clear of a top camera cutout.
        val toolbar = @Composable { m: Modifier ->
            EditorToolbar(title, shape, state, screenSize, onSave = { onSave(state.layout) }, onCancel = { if (state.requestCancel()) onClose() }, m)
        }
        when (shape) {
            PadShape.OVERLAY -> toolbar(Modifier.align(Alignment.Center))
            PadShape.PORTRAIT -> Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) { toolbar(Modifier.align(Alignment.BottomCenter)) }
        }
        if (state.confirmDiscard) {
            NxModal(caption = null, kind = ModalKind.CONFIRM, onButton = { b -> if (b == NxButton.A) onClose() else state.keepEditing() }, confirmLabel = "Discard", cancelLabel = "Keep editing") {
                Text("Discard changes?", color = colors.listText, fontFamily = LocalNxFont.current, fontSize = 18.sp)
            }
        }
    }
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

/**
 * Whether the editor for the console of [tag] shows the fixed mic button: a console whose core hears the mic (DS and
 * DSi, 3DS: [com.nxredux.mobile.runner.CoreDescriptor.microphone]), and Shared ([tag] empty), which they inherit.
 */
fun padLayoutShowsMic(tag: String): Boolean = tag.isBlank() || CoreRegistry.forTag(tag)?.microphone == true
