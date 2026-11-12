package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.nx.nxTextScale
import com.nxredux.mobile.ui.nx.nxDialogBody
import com.nxredux.mobile.ui.nx.nxModalSmall
import com.nxredux.mobile.ui.nx.nxModalText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.nxredux.mobile.ui.nx.nxHighlight
import com.nxredux.mobile.ui.nx.nxModalButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.netplay.NetplayText
import com.nxredux.mobile.netplay.usb.NetplayLink
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import com.nxredux.mobile.ui.nx.ModalKind
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxDialog
import com.nxredux.mobile.ui.nx.NxList
import com.nxredux.mobile.ui.nx.NxModal
import com.nxredux.mobile.ui.nx.NxRow
import com.nxredux.mobile.ui.nx.NxTextSizes
import com.nxredux.mobile.ui.nx.nxOptionsValueText

private val SheetRowHeight = 28.dp

/** A tab's and a button's tap target. */
private val SheetControlHeight = 40.dp

private fun connectionLabel(link: NetplayLink?): String = when (link) {
    NetplayLink.USB -> NetplayText.USB_CABLE
    NetplayLink.CABLE_LINK_WIFI -> NetplayText.CABLE_LINK_TAB
    NetplayLink.WIFI, null -> NetplayText.WIFI
}

/**
 * The Netplay sheet in the shared modal frame, drawn over the game list while [NetplayController.page] is set. Every tap
 * goes through [onButton] (as a pad press would), [onSelect] (a row highlight; a tap on the highlighted row is A), or
 * a tab's own [onConnection] / [onEmulator]. Role is the Connection tabs, the Emulator tabs under them, then Host Game
 * and Join Game side by side; Select Host is a menu like the context menu; hosting and joining are wait pages whose
 * only action is B Cancel; Join anyway and errors are dialogs.
 */
@Composable
fun NetplaySheet(
    page: NetplayPage, onButton: (NxButton) -> Unit, onSelect: (Int) -> Unit,
    onConnection: (NetplayLink) -> Unit = {}, onEmulator: (Int) -> Unit = {},
) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    when (page) {
        is NetplayPage.Role -> {
            val rows = page.rows
            val focus = rows.getOrNull(page.selected)
            NxModal(
                caption = page.title, kind = ModalKind.MENU, onButton = onButton,
                // Host and Join on the panel, apart from the card's choices.
                below = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(NetplayRoleRow.HOST to NetplayText.HOST, NetplayRoleRow.JOIN to NetplayText.JOIN).forEach { (row, label) ->
                            SheetAction(label, focused = focus == row, modifier = Modifier.weight(1f)) { onSelect(rows.indexOf(row)); onButton(NxButton.A) }
                        }
                    }
                    if (NetplayRoleRow.WIFI in rows) {
                        Spacer(Modifier.height(8.dp))
                        SheetAction(NetplayText.WIFI_SETTINGS, focused = focus == NetplayRoleRow.WIFI, modifier = Modifier.fillMaxWidth(), quiet = true) {
                            onSelect(rows.indexOf(NetplayRoleRow.WIFI)); onButton(NxButton.A)
                        }
                    }
                },
            ) {
                val tabs = NetplayRoleRow.CONNECTION in rows || NetplayRoleRow.EMULATOR in rows
                if (page.needWifi && page.connection != NetplayLink.USB) {
                    Text(NetplayText.NEED_WIFI, color = colors.listText, fontFamily = font, fontSize = nxDialogBody())
                    if (tabs) Spacer(Modifier.height(8.dp))
                }
                if (NetplayRoleRow.CONNECTION in rows) {
                    SheetTabs(
                        NetplayText.CONNECTION, page.connections.map(::connectionLabel), page.connections.indexOf(page.connection),
                        focused = focus == NetplayRoleRow.CONNECTION, onPick = { i -> page.connections.getOrNull(i)?.let(onConnection) },
                    )
                    if (NetplayRoleRow.EMULATOR in rows) Spacer(Modifier.height(12.dp))
                }
                if (NetplayRoleRow.EMULATOR in rows) {
                    SheetTabs(NetplayText.EMULATOR, page.emulators.orEmpty(), page.pick, focused = focus == NetplayRoleRow.EMULATOR, onPick = onEmulator)
                    if (page.pick == 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(NetplayText.AUTO_NOTE, color = colors.hint, fontFamily = font, fontSize = nxModalSmall())
                    }
                }
            }
        }
        is NetplayPage.Hosting -> NxModal(caption = page.title, kind = ModalKind.WAIT, onButton = onButton, scrimDismisses = false) {
            // The handheld's three tiers: this device (large), the Wi-Fi or cable reminder (medium), the status (small).
            Text(page.address, color = colors.listText, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = nxModalText() * 1.25f,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Spacer(Modifier.height(12.dp))
            Text(page.medium, color = colors.listText, fontFamily = font, fontSize = nxDialogBody(), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text(page.status, color = colors.hint, fontFamily = font, fontSize = nxModalSmall(), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        }
        is NetplayPage.Hosts -> NxModal(caption = NetplayText.SELECT_HOST, kind = ModalKind.MENU, onButton = onButton) {
            if (page.hosts.isEmpty()) {
                Text(page.empty, color = colors.listText, fontFamily = font, fontSize = nxDialogBody(), textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
            } else {
                SheetList(page.hosts.map { NxRow("${it.game} (${it.ip})") }, page.selected, onButton, onSelect)
            }
        }
        is NetplayPage.JoinAnyway -> NxDialog(
            NetplayText.TITLE, page.text, onConfirm = { onButton(NxButton.A) }, confirmLabel = "Join",
            onCancel = { onButton(NxButton.B) }, cancelLabel = "Back",
        )
        // "Starting game" cannot be cancelled (the controller ignores B there): no Cancel hint.
        is NetplayPage.Progress -> NxModal(caption = page.title, kind = if (page.line == NetplayText.STARTING) ModalKind.BUSY else ModalKind.WAIT, onButton = onButton, scrimDismisses = false) {
            Text(page.line, color = colors.listText, fontFamily = font, fontSize = nxDialogBody(), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
        }
        is NetplayPage.Error -> NxDialog(NetplayText.TITLE, page.text, onConfirm = { onButton(NxButton.A) })
    }
}

/** The sheet's row list, as the context menu's: a tap highlights a row, a tap on the highlighted row presses A. */
@Composable
private fun SheetList(rows: List<NxRow>, selected: Int, onButton: (NxButton) -> Unit, onSelect: (Int) -> Unit) {
    NxList(
        rows, selected, onSelect = onSelect, onOpen = { i -> onSelect(i); onButton(NxButton.A) },
        modifier = Modifier.height(SheetRowHeight * nxTextScale() * minOf(rows.size, MODAL_ROWS)),
        rowHeightOverride = SheetRowHeight * nxTextScale(), pillPaddingOverride = 12.dp, textSizeOverride = nxModalText(),
    )
}

/**
 * One choice of a few, as tabs in a row under their [caption]: the picked one ([picked]) filled, on the accent while the
 * pad is on the tabs ([focused], LEFT / RIGHT change it), the others the word alone. A tap picks.
 */
@Composable
private fun SheetTabs(caption: String, labels: List<String>, picked: Int, focused: Boolean, onPick: (Int) -> Unit) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val shape = RoundedCornerShape(50)
    Text(caption, color = colors.hint, fontFamily = font, fontSize = nxModalSmall())
    Spacer(Modifier.height(4.dp))
    Row(
        Modifier.fillMaxWidth().border(1.dp, colors.inkRing, shape).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == picked
            val ground = when {
                on && focused -> Modifier.background(nxHighlight(), shape)
                on -> Modifier.background(colors.inkRing, shape)
                else -> Modifier
            }
            Box(
                Modifier.weight(1f).heightIn(min = SheetControlHeight * nxTextScale() - 6.dp).clip(shape).then(ground).clickable { onPick(i) }.padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, color = if (on && focused) colors.onAccent else if (on) colors.listText else colors.hint, fontFamily = font,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, fontSize = nxModalButton(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A button of the Role page: on the accent pill while the pad is on it ([focused]), else outlined ([quiet]: the word alone). A tap presses it. */
@Composable
private fun SheetAction(label: String, focused: Boolean, modifier: Modifier = Modifier, quiet: Boolean = false, onClick: () -> Unit) {
    val colors = LocalNxColors.current
    val shape = RoundedCornerShape(50)
    val ground = when {
        focused -> Modifier.background(nxHighlight(), shape)
        quiet -> Modifier
        else -> Modifier.border(1.dp, colors.inkRing, shape)
    }
    Box(modifier.heightIn(min = SheetControlHeight * nxTextScale()).clip(shape).then(ground).clickable(onClick = onClick).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(
            label, color = if (focused) colors.onAccent else colors.listText, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold,
            fontSize = nxModalButton(), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
