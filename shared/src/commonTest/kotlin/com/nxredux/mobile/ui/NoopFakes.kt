package com.nxredux.mobile.ui

import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.EmulatorRunner
import com.nxredux.mobile.runner.GameSession
import com.nxredux.mobile.settings.FolderPicker
import com.nxredux.mobile.storage.Recent

internal object NoopFolderPicker : FolderPicker {
    override fun pickHome() {}
    override fun pickExtra() {}
    override fun forget(ref: ContentRef) {}
}

internal object NoopRunner : EmulatorRunner {
    override suspend fun launch(session: GameSession, recent: Recent) {}
}
