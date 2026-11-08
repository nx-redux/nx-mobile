package com.nxredux.mobile.ui

import androidx.compose.foundation.layout.Box
import com.nxredux.mobile.ui.nx.nxListInset
import com.nxredux.mobile.ui.nx.NxSettingsList
import com.nxredux.mobile.ui.nx.NxSettingEvent
import com.nxredux.mobile.ui.nx.NxSettingRow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.nxredux.mobile.sync.DeviceSyncController
import com.nxredux.mobile.sync.DeviceSyncPage
import com.nxredux.mobile.sync.DeviceSyncText
import com.nxredux.mobile.sync.SyncRole
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxDetailListBody
import com.nxredux.mobile.ui.nx.NxDialog
import com.nxredux.mobile.ui.nx.NxProgressBody
import com.nxredux.mobile.ui.nx.NxRow
import com.nxredux.mobile.ui.nx.NxRowKind

/**
 * Tools → Device Sync, in the Settings root's look ([SettingsRootScreen]), in the tool and setup's sync step alike:
 * bottom-left the small [SyncSetup.title] (the tool's name, or setup's step count), its head ("Sync with your handheld"
 * bold, what it syncs and the link's hint, [SettingsWordHead]), then the choices as large words: Sync ROMs with its
 * state (where available), the links, and in setup Skip (Continue after a sync) last ([onSetupTap] for their taps, with
 * [syncSetupRows]' indices). Done and the messages are the same page with their lines as the description; the
 * waiting, searching and syncing pages keep the tool page's bodies. State straight from the [controller].
 */
@Composable
fun DeviceSyncScreen(
    controller: DeviceSyncController, onHint: (NxButton) -> Unit, modifier: Modifier = Modifier,
    setup: SyncSetup? = null, onSetupTap: (index: Int, open: Boolean) -> Unit = { _, _ -> },
) {
    val page by controller.page.collectAsState()
    val chrome = setup
    val title = chrome?.title ?: DeviceSyncText.TITLE
    Box(modifier.fillMaxSize()) {
        when (val p = page) {
            is DeviceSyncPage.Ready -> {
                val skip = chrome?.inSetup == true
                val all = syncSetupRows(p, skip = skip)
                val off = if (p.romsAvailable) 1 else 0   // Sync ROMs, first in [syncSetupRows], is a row, not a word
                val words = all.drop(off).map {
                    when (it) {
                        is SyncSetupRow.Link -> DeviceSyncText.linkLabel(it.link)
                        else -> if (chrome?.synced == true) "Continue" else "Skip"
                    }
                }
                val focus = chrome?.focus ?: SyncFocus.LINK
                val at = syncSetupIndex(p, focus, skip = skip)
                val lines = listOf(if (p.roms) DeviceSyncText.HEADLINE_ROMS else DeviceSyncText.HEADLINE, DeviceSyncText.hint(p.link))
                val roms: (@Composable () -> Unit)? = if (!p.romsAvailable) null else ({ SyncRomsRow(p.roms, lit = focus == SyncFocus.ROMS) { open -> onSetupTap(0, open) } })
                SyncPage(
                    title, lines, words, if (focus == SyncFocus.ROMS) -1 else at - off,
                    listOf(Hint(NxButton.A, when (focus) { SyncFocus.LINK -> "Start"; SyncFocus.ROMS -> "Change"; SyncFocus.SKIP -> "Choose" }), Hint(NxButton.B, "Back")),
                    onHint, onTap = { onSetupTap(it + off, true) }, extra = roms,
                )
            }
            DeviceSyncPage.NoWifi -> message(title, listOf(DeviceSyncText.NO_WIFI), listOf(Hint(NxButton.B, "Back")), onHint)
            DeviceSyncPage.WaitingForCable -> NxProgressBody(title, DeviceSyncText.WAITING_CABLE, null, listOf(Hint(NxButton.B, "Back")),
                detail = DeviceSyncText.WAITING_CABLE_DETAIL, onHint = onHint)
            // The own IP under the status line, not in the detail: the detail is cut at three lines, and the sentence alone
            // can take them all on a narrow screen.
            is DeviceSyncPage.Searching -> NxProgressBody(title, "${DeviceSyncText.SEARCHING}\n${DeviceSyncText.ownIp(p.ownIp, p.link)}", null,
                listOf(Hint(NxButton.B, "Cancel")), detail = DeviceSyncText.searchingDetail(p.link), onHint = onHint)
            is DeviceSyncPage.Pairing -> if (p.confirmed) {
                message(title, listOf(DeviceSyncText.pairingCode(p.code), DeviceSyncText.WAITING_PEER), listOf(Hint(NxButton.B, "Cancel")), onHint)
            } else {
                message(title, listOf(DeviceSyncText.pairingCode(p.code)), listOf(Hint(NxButton.A, "Sync"), Hint(NxButton.B, "Cancel")), onHint)
            }
            is DeviceSyncPage.Syncing -> {
                val details = listOfNotNull(
                    DeviceSyncText.syncingWith(p.peerIp, p.link),
                    p.step?.let { DeviceSyncText.step(it, p.steps, p.console) },
                    if (p.role == SyncRole.CLIENT && p.step != null && p.total > 0) DeviceSyncText.files(p.done, p.total) else null,
                )
                NxDetailListBody(
                    title = title, details = details, rows = p.lines.asReversed().map { NxRow(it) }, selectedIndex = -1,   // newest first, nothing selected
                    hints = listOf(Hint(NxButton.B, "Stop")), onSelect = {}, onOpen = {}, onHint = onHint, rowKind = NxRowKind.OPTIONS,
                )
                if (p.confirmStop) NxDialog(DeviceSyncText.STOP_TITLE, DeviceSyncText.STOP_BODY,
                    onConfirm = { onHint(NxButton.A) }, confirmLabel = "Stop", onCancel = { onHint(NxButton.B) })
            }
            is DeviceSyncPage.Done -> if (chrome?.inSetup == true) {
                // Setup: Continue as the page's one word.
                SyncPage(title, listOf(DeviceSyncText.done(p.received, p.sent)) + p.notes, listOf("Continue"), 0,
                    listOf(Hint(NxButton.A, "Continue"), Hint(NxButton.B, "Back")), onHint, onTap = { onHint(NxButton.A) })
            } else message(title, listOf(DeviceSyncText.done(p.received, p.sent)) + p.notes,
                listOf(Hint(NxButton.A, if (chrome != null) "Continue" else "Sync again"), Hint(NxButton.B, "Back")), onHint)
            is DeviceSyncPage.Failed -> message(title, listOfNotNull(p.reason, p.detail), listOf(Hint(NxButton.A, "Retry"), Hint(NxButton.B, "Back")), onHint)
        }
    }
}

/**
 * A Device Sync page in the Settings root's look: [title], the head ("Sync with your handheld", [lines]), [extra] (the
 * Sync ROMs row) and [words].
 */
@Composable
private fun SyncPage(
    title: String, lines: List<String>, words: List<String>, selected: Int, hints: List<Hint>,
    onHint: (NxButton) -> Unit, onTap: (Int) -> Unit = {}, extra: (@Composable () -> Unit)? = null,
) = SettingsRootScreen(
    title, words, emptyList(), selected, onTap, hintBar = true, onHint = onHint,
    headline = DeviceSyncText.ASK, description = lines, hints = hints, extra = extra,
)

/**
 * Sync ROMs as a Settings options row (an Off / On switch) above the words, its text on the words' edge, [lit] while the
 * pad is on it. A tap selects it ([onTap] false); A, or a tap on the switch's other side, flips it ([onTap] true).
 */
@Composable
private fun SyncRomsRow(on: Boolean, lit: Boolean, onTap: (open: Boolean) -> Unit) {
    val row = NxSettingRow("Sync ROMs", choices = listOf("Off", "On"), index = if (on) 1 else 0)
    Box(Modifier.offset(x = -nxListInset()).widthIn(max = SETTINGS_FRAME_MAX).padding(bottom = 12.dp)) {
        NxSettingsList(
            listOf(row), if (lit) 0 else -1, onSelect = { onTap(false) }, onOpen = { onTap(true) },
            onEvent = { e -> if (e is NxSettingEvent.Choose && e.index != row.index) onTap(true) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A page that only says something: its [lines] as the head's description, no words. */
@Composable
private fun message(title: String, lines: List<String>, hints: List<Hint>, onHint: (NxButton) -> Unit) =
    SyncPage(title, lines, emptyList(), -1, hints, onHint)
