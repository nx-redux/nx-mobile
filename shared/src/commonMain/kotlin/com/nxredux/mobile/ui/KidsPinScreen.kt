package com.nxredux.mobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.kids.LocalNow
import com.nxredux.mobile.kids.PinPurpose
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import com.nxredux.mobile.ui.nx.nxHighlight
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/** A key on the PIN pad. */
internal sealed interface PinKey {
    data class Digit(val d: Int) : PinKey
    data object Back : PinKey
    data object Enter : PinKey
}

/** The PIN pad's keys in reading order, three to a row: 1–9, then Back, 0, Enter. */
internal val KIDS_PIN_KEYS: List<PinKey> = (1..9).map { PinKey.Digit(it) } + PinKey.Back + PinKey.Digit(0) + PinKey.Enter
internal const val KIDS_PIN_COLUMNS = 3
internal const val KIDS_PIN_LENGTH = 4

/** A PIN prompt's title for [purpose]. */
internal fun kidsPinTitle(purpose: PinPurpose): String = when (purpose) {
    PinPurpose.UNLOCK -> "Parent PIN"
    PinPurpose.SETUP_NEW, PinPurpose.CHANGE -> "Choose a PIN"
    PinPurpose.SETUP_CONFIRM -> "Enter the PIN again"
}

/**
 * The line under the dots: the wait after five wrong tries ("Try again in 12 s", whole seconds up), "Wrong PIN" after a
 * wrong one until a digit is typed, else nothing.
 */
internal fun kidsPinStatus(s: Screen.KidsPin, nowMs: Long): String? = when {
    nowMs < s.waitUntil -> "Try again in ${ceil((s.waitUntil - nowMs) / 1000.0).toInt()} s"
    s.wrong > 0 && s.typed.isEmpty() -> "Wrong PIN"
    else -> null
}

/**
 * Kids Mode's PIN prompt: its title, four dots (filled as digits are typed) and a 3 × 4 pad of 1–9, Back, 0 and Enter,
 * the keys named in words. A wrong PIN shakes the dots; the wait after the fifth counts down in place of the keys' use.
 * Taps press keys; the d-pad's key ([selectedIndex]) is lit as the menu's selection is.
 */
@Composable
internal fun KidsPinScreen(
    s: Screen.KidsPin, selectedIndex: Int, now: () -> LocalNow?, onKey: (PinKey) -> Unit, modifier: Modifier = Modifier,
) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val clock = { now()?.epochMs ?: 0L }
    // The wait's count: re-read every quarter second while one runs.
    val nowMs by produceState(clock(), s.waitUntil) { while (value < s.waitUntil) { delay(250); value = clock() } }
    val status = kidsPinStatus(s, nowMs)
    val shake = remember { Animatable(0f) }
    LaunchedEffect(s.wrong) {
        if (s.wrong == 0) return@LaunchedEffect
        shake.animateTo(0f, keyframes { durationMillis = 360; -14f at 60; 12f at 120; -8f at 180; 6f at 240; -3f at 300 })
    }
    BoxWithConstraints(modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
        val landscape = maxWidth > maxHeight
        // Four rows of keys fit the height (beside the dots in landscape, under them in portrait) and three the width.
        val keyRoom = if (landscape) maxHeight - 32.dp else maxHeight * 0.62f
        val gap = 12.dp
        val key = min(((keyRoom - gap * 3) / 4).value, ((if (landscape) maxWidth / 2 else maxWidth - 32.dp) - gap * 2).value / 3).coerceIn(40f, 76f).dp
        val head: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(kidsPinTitle(s.purpose), color = colors.listText, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(KIDS_PIN_LENGTH) { i ->
                        val filled = i < s.typed.length
                        Box(Modifier.size(16.dp).clip(CircleShape).then(if (filled) Modifier.background(colors.main) else Modifier.border(2.dp, colors.hint, CircleShape)))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(status ?: " ", color = if (status != null) colors.warning else colors.hint, fontFamily = font, fontSize = 15.sp)
            }
        }
        val pad: @Composable () -> Unit = { PinPad(selectedIndex, key, gap, onKey) }
        if (landscape) Row(verticalAlignment = Alignment.CenterVertically) { head(); Spacer(Modifier.width(48.dp)); pad() }
        else Column(horizontalAlignment = Alignment.CenterHorizontally) { head(); Spacer(Modifier.height(28.dp)); pad() }
    }
}

/** The 3 × 4 keys, [key] square, [gap] apart. */
@Composable
private fun PinPad(selectedIndex: Int, key: Dp, gap: Dp, onKey: (PinKey) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        KIDS_PIN_KEYS.chunked(KIDS_PIN_COLUMNS).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEachIndexed { c, k -> PinKeyButton(k, lit = r * KIDS_PIN_COLUMNS + c == selectedIndex, key) { onKey(k) } }
            }
        }
    }
}

@Composable
private fun PinKeyButton(k: PinKey, lit: Boolean, size: Dp, onTap: () -> Unit) {
    val colors = LocalNxColors.current
    val content = if (lit) colors.onAccent else colors.listText
    val label = when (k) {
        is PinKey.Digit -> k.d.toString()
        PinKey.Back -> "Back"
        PinKey.Enter -> "Enter"
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 4))
            .then(if (lit) Modifier.background(nxHighlight()) else Modifier.background(colors.inkEdge))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        val digit = k is PinKey.Digit
        Text(
            label, color = content, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold,
            fontSize = if (digit) (size.value * 0.4f).roundToInt().sp else 14.sp,
        )
    }
}
