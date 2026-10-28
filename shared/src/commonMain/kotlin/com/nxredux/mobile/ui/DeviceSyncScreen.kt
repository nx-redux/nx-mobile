package com.nxredux.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nxredux.mobile.sync.DeviceSyncController
import com.nxredux.mobile.sync.DeviceSyncPage
import com.nxredux.mobile.sync.DeviceSyncText
import com.nxredux.mobile.sync.SyncLink
import com.nxredux.mobile.sync.SyncRole
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.nav_icon_roms
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.HintTouch
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxDetailListBody
import com.nxredux.mobile.ui.nx.NxDialog
import com.nxredux.mobile.ui.nx.NxProgressBody
import com.nxredux.mobile.ui.nx.NxRow
import com.nxredux.mobile.ui.nx.NxRowKind
import com.nxredux.mobile.ui.nx.NxSetupLandscapeWidth

/** X on Ready: ROMs on / off; touch, an icon at the title's right, lit while ROMs are on. */
internal fun deviceSyncRomsHint(on: Boolean): Hint = Hint(NxButton.X, DeviceSyncText.romsHint(on), HintTouch.Icon(Res.drawable.nav_icon_roms, lit = on))

/**
 * Tools → Device Sync, in the handheld's tool-page shape; state straight from the [controller]. As setup's sync step
 * ([setup]), the setup page title and a "Sync with your handheld" headline; Ready lists the links as pill rows (no ring) and
 * Skip (Continue after a sync) at the title's right ([onSetupTap] for its taps), Done's A continues.
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
            is DeviceSyncPage.Ready -> if (chrome != null) {
                // Sync ROMs as an option row (where available), then the links (and Skip in setup).
                val rows = syncSetupRows(p, skip = chrome.inSetup).map {
                    when (it) {
                        SyncSetupRow.Roms -> NxRow("Sync ROMs", secondary = if (p.roms) "On" else "Off", cycleable = true)
                        is SyncSetupRow.Link -> NxRow(DeviceSyncText.linkLabel(it.link))
                        SyncSetupRow.Skip -> NxRow(if (chrome.synced) "Continue" else "Skip")
                    }
                }
                // The links as pill rows under the option; setup's Skip at the title's right, the links without the ring.
                val top = if (chrome.inSetup) 1 else 0
                val actions = rows.size - top - (if (p.romsAvailable) 1 else 0)
                val lines = listOf(if (p.roms) DeviceSyncText.HEADLINE_ROMS else DeviceSyncText.HEADLINE, DeviceSyncText.hint(p.link))
                NxDetailListBody(
                    title = title,
                    // The headline and its dim line as the other tool pages' (Cheats & Patches'), not a caption.
                    details = if (chrome.inSetup) listOf("Sync with your handheld") + lines else lines,
                    rows = rows, selectedIndex = syncSetupIndex(p, chrome.focus, skip = chrome.inSetup),
                    hints = listOf(Hint(NxButton.A, if (chrome.focus == SyncFocus.LINK) "Start" else "Choose"), Hint(NxButton.B, "Back")),
                    onSelect = { onSetupTap(it, false) }, onOpen = { onSetupTap(it, true) }, onHint = onHint,
                    rowKind = if (p.romsAvailable) NxRowKind.OPTIONS else NxRowKind.PILL, actionRows = if (p.romsAvailable) actions else 0,
                    titleActions = top, actionsOutlined = false,   // the links as plain pill rows, no ring, in setup and the tool alike
                    landscapeWidth = if (chrome.inSetup) NxSetupLandscapeWidth else 1f,
                )
            } else NxDetailListBody(
                title = title, details = listOf(if (p.roms) DeviceSyncText.HEADLINE_ROMS else DeviceSyncText.HEADLINE, DeviceSyncText.hint(p.link)),
                rows = SyncLink.entries.map { NxRow(DeviceSyncText.linkLabel(it)) }, selectedIndex = p.link.ordinal,
                hints = listOfNotNull(Hint(NxButton.A, "Start"), if (p.romsAvailable) deviceSyncRomsHint(p.roms) else null, Hint(NxButton.B, "Back")),
                onSelect = { controller.pick(SyncLink.entries[it]) }, onOpen = { controller.pick(SyncLink.entries[it]); onHint(NxButton.A) },
                onHint = onHint,
            )
            DeviceSyncPage.NoWifi -> message(title, listOf(DeviceSyncText.NO_WIFI), listOf(Hint(NxButton.B, "Back")), onHint)
            DeviceSyncPage.WaitingForCable -> NxProgressBody(title, DeviceSyncText.WAITING_CABLE, null, listOf(Hint(NxButton.B, "Back")),
                detail = DeviceSyncText.WAITING_CABLE_DETAIL, onHint = onHint)
            // The own IP under the status line, not in the detail: the detail is cut at three lines, and the sentence alone
            // can take them all on a narrow screen.
            is DeviceSyncPage.Searching -> NxProgressBody(title, "${DeviceSyncText.SEARCHING}\n${DeviceSyncText.ownIp(p.ownIp, p.link)}", null,
                listOf(Hint(NxButton.B, "Cancel")), detail = DeviceSyncText.searchingDetail(p.link), onHint = onHint)
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
                // Setup: Continue at the title's right, as the other setup pages have it.
                NxDetailListBody(
                    title = title, details = listOf(DeviceSyncText.done(p.received, p.sent)) + p.notes, rows = listOf(NxRow("Continue")),
                    selectedIndex = 0, hints = listOf(Hint(NxButton.A, "Continue"), Hint(NxButton.B, "Back")),
                    onSelect = {}, onOpen = { onHint(NxButton.A) }, onHint = onHint, titleActions = 1,
                    landscapeWidth = NxSetupLandscapeWidth,
                )
            } else message(title, listOf(DeviceSyncText.done(p.received, p.sent)) + p.notes,
                listOf(Hint(NxButton.A, if (chrome != null) "Continue" else "Sync again"), Hint(NxButton.B, "Back")), onHint)
            is DeviceSyncPage.Failed -> message(title, listOfNotNull(p.reason, p.detail), listOf(Hint(NxButton.A, "Retry"), Hint(NxButton.B, "Back")), onHint)
        }
    }
}

/** A page that only says something: the lines as details, no rows. */
@Composable
private fun message(title: String, lines: List<String>, hints: List<Hint>, onHint: (NxButton) -> Unit) =
    NxDetailListBody(title = title, details = lines, rows = emptyList(), selectedIndex = -1, hints = hints,
        onSelect = {}, onOpen = {}, onHint = onHint)
