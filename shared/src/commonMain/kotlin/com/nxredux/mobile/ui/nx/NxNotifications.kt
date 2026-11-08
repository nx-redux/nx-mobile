package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val NotificationShape = RoundedCornerShape(10.dp)

/**
 * Where the in-game notifications sit ([pill] / [toasts] are their anchors). [CORNERS]: the progress pill top-left, the
 * toasts bottom-left. [TOP_CENTRE]: both top-centre, the toasts stacked under the pill, clear of the landscape
 * overlay pad's system row in the top corners and its d-pad bottom-left.
 */
enum class NxNotificationPlacement(val pill: Alignment, val toasts: Alignment) {
    CORNERS(Alignment.TopStart, Alignment.BottomStart),
    TOP_CENTRE(Alignment.TopCenter, Alignment.TopCenter),
}

/**
 * The sides [placement] lets the safe-area inset reach (on top of the host's safe sides): top-centre only the vertical
 * ones, so a cutout on one side cannot push the column off the screen's centre into the far system row
 * ([nxTopCentreMaxWidth] already keeps clear of the cutout on both); the corners every side.
 */
fun nxNotificationInsetSides(placement: NxNotificationPlacement): WindowInsetsSides =
    if (placement == NxNotificationPlacement.TOP_CENTRE) WindowInsetsSides.Vertical
    else WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical

/** [NxNotificationPlacement.TOP_CENTRE] while the landscape overlay pad (the clusters over the game) is shown, else the corners. */
fun nxNotificationPlacement(overlayPadShown: Boolean): NxNotificationPlacement =
    if (overlayPadShown) NxNotificationPlacement.TOP_CENTRE else NxNotificationPlacement.CORNERS

/**
 * The in-game notification overlay: the progress [pill] and the [toasts] (newest at the bottom) where [placement] puts
 * them; a single anchor (top-centre) stacks them at most [topCentreMaxWidth] wide. Display only but a toast's button
 * ([NxToast.action], its tap to [onAction] with the toast's id) and its Close ([onDismiss]): nothing else consumes pointer input, so touches reach the
 * game and the virtual pad beneath it. The content keeps inside the safe area (cutout and
 * system bars) on [safeSides] before the 12 dp margin; a host whose overlay does not reach a screen edge leaves that
 * side out, so it is not inset twice. Sizes are physical, not a share of the screen height.
 */
@Composable
fun NxNotifications(
    toasts: List<NxToast>, pill: NxProgressPill?, modifier: Modifier = Modifier,
    safeSides: WindowInsetsSides = WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical,
    placement: NxNotificationPlacement = NxNotificationPlacement.CORNERS,
    topCentreMaxWidth: Dp = Dp.Infinity,
    onAction: (Long) -> Unit = {},
    onDismiss: (Long) -> Unit = {},
) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val text = NxTextSizes.notice
    val padding = Modifier.padding(horizontal = 10.dp, vertical = 6.25.dp)
    val gap = 5.dp
    val pillContent: @Composable () -> Unit = {
        if (pill != null) {
            Row(
                Modifier.background(colors.scrimNotice, NotificationShape).then(padding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pill.badgePath != null) {
                    NxBadge(pill.badgePath, 22.5.dp)
                    Spacer(Modifier.width(7.5.dp))
                }
                Text(pill.title, color = colors.listText, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = text, maxLines = 1)
                Spacer(Modifier.width(7.5.dp))
                Text(pill.value, color = colors.hint, fontFamily = font, fontSize = text, maxLines = 1)
            }
        }
    }
    val toastsContent: @Composable () -> Unit = {
        toasts.forEach { toast ->
            if (toast.action != null) {
                // a toast with buttons (the layout tip, user): its words wrapping, Close and its button on a row under them
                Column(Modifier.background(colors.scrimNotice, NotificationShape).then(padding)) {
                    Text(toast.text, color = colors.listText, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = text)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NoticeButton(NOTICE_CLOSE, text) { onDismiss(toast.id) }
                        NoticeButton(toast.action, text) { onAction(toast.id) }
                    }
                }
            } else Row(
                Modifier.background(colors.scrimNotice, NotificationShape).then(padding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (toast.badgePath != null) {   // session notices carry no badge: the text starts flush
                    NxBadge(toast.badgePath, 35.dp)   // about the height of the two-line unlock text
                    Spacer(Modifier.width(7.5.dp))
                }
                Text(toast.text, color = colors.listText, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = text)
            }
        }
    }
    val safe = WindowInsets.displayCutout.union(WindowInsets.systemBars).only(safeSides).only(nxNotificationInsetSides(placement))
    Box(modifier.fillMaxSize().windowInsetsPadding(safe).padding(12.dp)) {
        if (placement.pill == placement.toasts) {
            // One anchor: a single column, the pill on top and the toasts under it.
            // At most [topCentreMaxWidth] wide ([nxTopCentreMaxWidth]): a long toast wraps between the pad's system rows.
            Column(
                Modifier.align(placement.pill).widthIn(max = topCentreMaxWidth),
                verticalArrangement = Arrangement.spacedBy(gap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                pillContent()
                toastsContent()
            }
        } else {
            if (pill != null) Box(Modifier.align(placement.pill)) { pillContent() }
            if (toasts.isNotEmpty()) {
                Column(Modifier.align(placement.toasts), verticalArrangement = Arrangement.spacedBy(gap)) { toastsContent() }
            }
        }
    }
}

/** A toast's button ([NxNotifications]): an outlined pill, its [word] at the notices' [size]; a tap calls [onTap]. */
@Composable
private fun NoticeButton(word: String, size: androidx.compose.ui.unit.TextUnit, onTap: () -> Unit) {
    val colors = LocalNxColors.current
    Text(
        word, color = colors.listText, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = size, maxLines = 1,
        modifier = Modifier.border(1.dp, colors.listText.copy(alpha = 0.5f), NotificationShape)   // role: the button's edge
            .clickable(onClick = onTap).padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

/** The word on a toast's Close button. */
private const val NOTICE_CLOSE = "Close"
