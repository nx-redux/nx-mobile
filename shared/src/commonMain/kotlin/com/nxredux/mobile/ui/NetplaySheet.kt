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
import com.nxredux.mobile.ui.nx.ModalActionWeight
import com.nxredux.mobile.ui.nx.NxModalAction
import com.nxredux.mobile.ui.nx.NxModalActionRow
import com.nxredux.mobile.ui.nx.NxModalControlHeight
import com.nxredux.mobile.ui.nx.NxPadRingRoom
import com.nxredux.mobile.ui.nx.nxPadRing
import com.nxredux.mobile.ui.nx.nxPadRings
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
            // The ring only where there is a pad to move it with, and only while the pad is what was last used.
            val focus = rows.getOrNull(page.selected).takeIf { page.pad && nxPadRings() }
            NxModal(
                // The title is the sheet's own: brighter than the modal caption, so the labels under it read as labels.
                caption = null, kind = ModalKind.MENU, onButton = onButton,
                // Host and Join on the panel, apart from the card's choices.
                below = {
                    // Host first and filled: a join needs a host, and the sheet opens on it. The rings' own room makes the gap.
                    val actions = listOf(NetplayRoleRow.HOST, NetplayRoleRow.JOIN)
                    NxModalActionRow(listOf(NetplayText.HOST, NetplayText.JOIN), actions.indexOf(focus)) { i ->
                        onSelect(rows.indexOf(actions[i])); onButton(NxButton.A)
                    }
                    if (NetplayRoleRow.WIFI in rows) {
                        NxModalAction(NetplayText.WIFI_SETTINGS, ModalActionWeight.QUIET, ring = focus == NetplayRoleRow.WIFI, modifier = Modifier.padding(horizontal = 4.dp).fillMaxWidth()) {
                            onSelect(rows.indexOf(NetplayRoleRow.WIFI)); onButton(NxButton.A)
                        }
                    }
                },
            ) {
                val tabs = NetplayRoleRow.CONNECTION in rows || NetplayRoleRow.EMULATOR in rows
                // Everything keeps the ring's room at its sides, so it lines up with the tab tracks.
                val inset = Modifier.padding(horizontal = NxPadRingRoom)
                Text(page.title, inset, color = colors.listText, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = nxModalText(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (page.needWifi && page.connection != NetplayLink.USB) {
                    Spacer(Modifier.height(4.dp))
                    Text(NetplayText.NEED_WIFI, inset, color = colors.hint, fontFamily = font, fontSize = nxDialogBody())
                }
                if (tabs) Spacer(Modifier.height(10.dp))
                if (NetplayRoleRow.CONNECTION in rows) {
                    SheetTabs(
                        NetplayText.CONNECTION, page.connections.map(::connectionLabel), page.connections.indexOf(page.connection),
                        ring = focus == NetplayRoleRow.CONNECTION, onPick = { i -> page.connections.getOrNull(i)?.let(onConnection) },
                    )
                    // Cable Link has no Emulator tabs: it says which emulator it runs instead.
                    if (page.connection == NetplayLink.CABLE_LINK_WIFI) SheetNote(NetplayText.CABLE_LINK_NOTE)
                    if (NetplayRoleRow.EMULATOR in rows) Spacer(Modifier.height(6.dp))
                }
                if (NetplayRoleRow.EMULATOR in rows) {
                    SheetTabs(NetplayText.EMULATOR, page.emulators.orEmpty(), page.pick, ring = focus == NetplayRoleRow.EMULATOR, onPick = onEmulator)
                    if (page.pick == 0) SheetNote(NetplayText.AUTO_NOTE)
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

/** A tab track's corners, and its plate's inside the track's 4 dp. */
private val SheetTrackCorner = 12.dp
private val SheetPlateCorner = 8.dp

/**
 * One choice of a few, as a segmented control under its [caption]: a sunken track, the picked one ([picked]) a raised
 * plate in the ink, never the accent (that is the sheet's one action's). [ring]: the pad is on it (LEFT / RIGHT move the
 * plate). A tap picks.
 */
@Composable
private fun SheetTabs(caption: String, labels: List<String>, picked: Int, ring: Boolean, onPick: (Int) -> Unit) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val plate = RoundedCornerShape(SheetPlateCorner)
    Text(caption, Modifier.padding(horizontal = NxPadRingRoom), color = colors.hint, fontFamily = font, fontSize = nxModalSmall())
    Row(
        Modifier.fillMaxWidth().nxPadRing(ring, SheetTrackCorner).background(colors.modalPanel, RoundedCornerShape(SheetTrackCorner)).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == picked
            Box(
                Modifier.weight(1f).heightIn(min = NxModalControlHeight * nxTextScale() - 8.dp).clip(plate)
                    .then(if (on) Modifier.background(colors.inkFaint, plate) else Modifier).clickable { onPick(i) }.padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, color = if (on) colors.listText else colors.hint, fontFamily = font,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, fontSize = nxModalButton(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A line about the pick above it, in its own faint box. */
@Composable
private fun SheetNote(text: String) {
    val colors = LocalNxColors.current
    Spacer(Modifier.height(2.dp))
    Text(
        text, Modifier.padding(horizontal = NxPadRingRoom).fillMaxWidth().border(1.dp, colors.inkLine, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        color = colors.hint, fontFamily = LocalNxFont.current, fontSize = nxModalSmall(),
    )
}
