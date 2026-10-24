package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.nx.nxTextScale
import com.nxredux.mobile.ui.nx.nxDialogBody
import com.nxredux.mobile.ui.nx.nxModalSmall
import com.nxredux.mobile.ui.nx.nxModalText
import androidx.compose.foundation.layout.Spacer
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

/**
 * The Netplay sheet in the shared modal frame, drawn over the game list while [NetplayController.page] is set. Every tap
 * goes through [onButton] (as a pad press would) or [onSelect] (a row highlight; a tap on the highlighted row is A).
 * Role and Select Host are menus like the context menu; hosting and joining are wait pages whose only action is B
 * Cancel; Join anyway and errors are dialogs.
 */
@Composable
fun NetplaySheet(page: NetplayPage, onButton: (NxButton) -> Unit, onSelect: (Int) -> Unit) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    when (page) {
        is NetplayPage.Role -> NxModal(caption = page.title, kind = ModalKind.MENU, onButton = onButton) {
            if (page.needWifi && page.connection != NetplayLink.USB) {
                Text(NetplayText.NEED_WIFI, color = colors.listText, fontFamily = font, fontSize = nxDialogBody())
                Spacer(Modifier.height(8.dp))
            }
            val rows = page.rows.mapIndexed { i, row ->
                when (row) {
                    NetplayRoleRow.CONNECTION -> NxRow(
                        NetplayText.CONNECTION, cycleable = true,
                        secondary = nxOptionsValueText(if (page.connection == NetplayLink.USB) NetplayText.USB_CABLE else NetplayText.WIFI, selected = i == page.selected, cycleable = true),
                    )
                    NetplayRoleRow.HOST -> NxRow(NetplayText.HOST)
                    NetplayRoleRow.JOIN -> NxRow(NetplayText.JOIN)
                    NetplayRoleRow.EMULATOR -> NxRow(
                        NetplayText.EMULATOR, cycleable = true,
                        secondary = nxOptionsValueText(page.emulators?.getOrNull(page.pick).orEmpty(), selected = i == page.selected, cycleable = true),
                    )
                    NetplayRoleRow.WIFI -> NxRow(NetplayText.WIFI_SETTINGS)
                }
            }
            SheetList(rows, page.selected, onButton, onSelect)
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
