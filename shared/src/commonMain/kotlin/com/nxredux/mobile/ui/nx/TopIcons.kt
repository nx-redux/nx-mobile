package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.nav_icon_recent
import com.nxredux.mobile.shared.res.nav_icon_search
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** The device has a touchscreen ([rememberHasTouchscreen]); App provides it. A game list's title icons follow it. */
val LocalHasTouchscreen = compositionLocalOf { false }

/** Each icon's tap target: the tab row's height, so the whole row height takes the tap. */
private val TOP_ICON_TARGET = MENU_TAB_ROW_HEIGHT

/** The drawn icon inside its target. */
private val TOP_ICON_SIZE = 22.dp

/** How far the drawn icon sits inside its target's edge: the gutter takes this back so the icon ends on it. */
internal val TOP_ICON_INSET = (TOP_ICON_TARGET - TOP_ICON_SIZE) / 2

/** One icon at a title's right: [art], [description] (also its accessibility label), [lit] (a toggle that is on). */
data class TopIconAction(val art: DrawableResource, val description: String, val lit: Boolean = false, val onClick: () -> Unit)

/**
 * A touch-only screen's icons at the right of the tab row or a page title ([LauncherChrome.topIcons],
 * [HintTouch.Icon]). In the unselected tab words' [NxColors.inkDim]; a lit one in the full ink.
 */
@Composable
fun NxTopIcons(actions: List<TopIconAction>, modifier: Modifier = Modifier) {
    if (actions.isEmpty()) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) { actions.forEach { TopIcon(it) } }
}

@Composable
private fun TopIcon(action: TopIconAction) {
    Box(Modifier.size(TOP_ICON_TARGET).clip(CircleShape).clickable(onClick = action.onClick), contentAlignment = Alignment.Center) {
        Image(
            painterResource(action.art), contentDescription = action.description,
            modifier = Modifier.size(TOP_ICON_SIZE),
            colorFilter = ColorFilter.tint(LocalNxColors.current.let { if (action.lit) it.main else it.inkDim }),
        )
    }
}

/**
 * The launcher's Recent and Search icons for [chrome] (Search only with games to search, [canSearch]); none without
 * [onHint]. [recent] false: no Recent icon whatever the chrome (inside Recent, the switcher).
 */
fun launcherTopActions(chrome: LauncherChrome, onHint: ((NxButton) -> Unit)?, canSearch: Boolean, recent: Boolean = true): List<TopIconAction> {
    val hint = onHint ?: return emptyList()
    return buildList {
        if (recent && chrome.recentIcon) add(TopIconAction(Res.drawable.nav_icon_recent, "Recent") { hint(NxButton.SELECT) })
        if (chrome.searchIcon && canSearch) add(TopIconAction(Res.drawable.nav_icon_search, "Search") { hint(NxButton.START) })
    }
}

/** [launcherTopActions] then [extra] (a page's own [HintTouch.Icon]s). */
@Composable
fun LauncherTopIcons(
    chrome: LauncherChrome, onHint: ((NxButton) -> Unit)?, canSearch: Boolean, modifier: Modifier = Modifier,
    extra: List<TopIconAction> = emptyList(),
) = NxTopIcons(launcherTopActions(chrome, onHint, canSearch) + extra, modifier)
