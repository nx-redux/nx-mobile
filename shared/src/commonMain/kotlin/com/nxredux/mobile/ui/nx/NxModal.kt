package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The one modal frame: scrim, a dark panel holding a lighter card (a small caption and [content]), and below the card
 * a footer that follows [LocalFooterMode]: tappable key hints when a pad or controller exists, real buttons otherwise
 * (never for a MENU). A tap on the scrim sends B (A for INFO), unless [scrimDismisses] is false (it then only blocks
 * the page below). Every hint or button tap is delivered through [onButton], exactly as a pad press would be.
 * A card taller than the room scrolls, its edges fading while there is more that way ([nxScrollFade]); with [scrollSteps] (the host's d-pad presses for it, -1 up and +1 down)
 * the key hints also lead with a d-pad Scroll hint while it overflows, whose tap steps down (back to the top at the end).
 */
@Composable
fun NxModal(
    caption: String?, kind: ModalKind, onButton: (NxButton) -> Unit,
    confirmLabel: String = "OK", cancelLabel: String = "Cancel", scrimDismisses: Boolean = true,
    maxWidth: Dp = 420.dp,
    scrollSteps: Flow<Int>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalNxColors.current
    val scroll = rememberScrollState()
    val base = modalFooter(kind, LocalFooterMode.current, confirmLabel, cancelLabel)
    val footer = if (scrollSteps != null && base is ModalFooter.Hints && scroll.maxValue > 0) ModalFooter.Hints(listOf(Hint(NxButton.DOWN, "Scroll")) + base.hints) else base
    val dismiss = if (kind == ModalKind.INFO) NxButton.A else NxButton.B
    val padVisible = LocalPadBelow.current
    val stepPx = with(LocalDensity.current) { SCROLL_STEP.toPx() }
    LaunchedEffect(scrollSteps) { scrollSteps?.collect { scroll.animateScrollBy(it * stepPx) } }
    val tapScope = rememberCoroutineScope()
    // The Scroll hint's tap is the modal's own: a host maps every button but B to confirm.
    val onFooter: (NxButton) -> Unit = { b ->
        if (b == NxButton.DOWN) tapScope.launch { if (scroll.canScrollForward) scroll.animateScrollBy(stepPx) else scroll.animateScrollTo(0) }
        else onButton(b)
    }
    // Landscape (no pad): the screen slot is the whole window, so imePadding lifts the panel exactly above the keyboard.
    // Portrait: the keyboard lands mostly on the pad band, so no IME padding (the card scrolls if it is ever covered).
    // A tap gesture, not clickable: no full-screen ripple, and the scrim is not focusable or clicked by a hardware Enter.
    Box(
        Modifier.fillMaxSize().then(if (!padVisible) Modifier.imePadding() else Modifier).background(LocalNxColors.current.scrimModal)
            .pointerInput(scrimDismisses) { detectTapGestures { if (scrimDismisses) onButton(dismiss) } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            // 24dp from every screen edge: a tall card (long notes in landscape) stops short of the top and bottom too.
            Modifier.padding(24.dp).widthIn(max = maxWidth).fillMaxWidth().background(LocalNxColors.current.modalPanel, RoundedCornerShape(24.dp))
                .pointerInput(Unit) { detectTapGestures { } }   // taps inside stay inside
                .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = if (footer is ModalFooter.None) 8.dp else 0.dp),
        ) {
            // The card scrolls and the footer stays pinned, so a landscape keyboard leaving ~125dp still shows the buttons.
            Column(Modifier.weight(1f, fill = false).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LocalNxColors.current.modalCard).nxScrollFade(scroll).verticalScroll(scroll).padding(16.dp)) {
                if (caption != null) {
                    Text(caption, color = colors.hint, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = nxModalSmall(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                }
                content()
            }
            NxModalFooter(footer, onFooter)
        }
    }
}

/** How far one d-pad press scrolls a modal's card: about three lines of dialog text. */
private val SCROLL_STEP = 72.dp

/** The footer strip on the panel below the card: bare key hints (start-aligned) or pill buttons (end-aligned); nothing for [ModalFooter.None]. */
@Composable
private fun NxModalFooter(footer: ModalFooter, onButton: (NxButton) -> Unit) {
    val colors = LocalNxColors.current
    when (footer) {
        // The vertical padding sits inside each hint's clickable: a 40dp tap target in a 44dp strip.
        is ModalFooter.Hints -> Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            footer.hints.inPadOrder().forEach { hint ->
                Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { onButton(hint.button) }.padding(horizontal = 4.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    NxButtonGlyph(hint.button, 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(hint.label, color = colors.listText, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = nxModalSmall(), maxLines = 1)
                }
            }
        }
        is ModalFooter.Buttons -> Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            footer.actions.forEach { action -> NxModalButton(action) { onButton(action.button) } }
        }
        ModalFooter.None -> Unit
    }
}

/** A 36dp pill: primary is the list pill's white on black text; secondary is outlined. */
@Composable
internal fun NxModalButton(action: FooterAction, onClick: () -> Unit) {
    val colors = LocalNxColors.current
    val shape = RoundedCornerShape(50)
    val base = Modifier.height(36.dp).clip(shape).clickable(onClick = onClick)
    val styled = if (action.primary) base.background(colors.main, shape) else base.border(1.dp, colors.inkRing, shape)
    Box(styled.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
        Text(action.label, color = if (action.primary) colors.listTextSelected else colors.listText, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = nxModalButton(), maxLines = 1)
    }
}
