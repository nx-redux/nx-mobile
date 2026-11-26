package com.nxredux.mobile.storage

import com.nxredux.mobile.runner.CoreRegistry

/**
 * [inner] as the menu reads it while Save on quit may be off ([saveOnQuit], read per call): the hidden save state
 * ([NxPaths.AUTO_SLOT]) is left on disk but neither resumes nor shows, so games start fresh. A manual slot still resumes.
 * A console whose core has no save states ([com.nxredux.mobile.runner.CoreDescriptor.saveStates]) never resumes: a slot
 * file left there could not be loaded.
 */
class AutoSlotGate(private val inner: UserDataStore, private val saveOnQuit: () -> Boolean) : UserDataStore by inner {
    override suspend fun resumeInfo(tag: String, fileName: String): ResumeInfo {
        if (!statesFor(tag)) return ResumeInfo.NONE
        val info = inner.resumeInfo(tag, fileName)
        return if (info.slot == NxPaths.AUTO_SLOT && !saveOnQuit()) ResumeInfo.NONE else info
    }

    override suspend fun hiddenStateShot(tag: String, fileName: String): String? =
        if (saveOnQuit() && statesFor(tag)) inner.hiddenStateShot(tag, fileName) else null

    private fun statesFor(tag: String) = CoreRegistry.forTag(tag)?.saveStates != false
}
