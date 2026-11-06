package com.nxredux.mobile.kids

import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.storage.HomeLocator
import java.io.File

/** [KidsFlag] as a plain file at the home root (homes are file refs, read through All files access or the media folder). */
class AndroidKidsFlag(private val locator: HomeLocator) : KidsFlag {
    private fun home(): File? = locator.current()?.takeIf(FileRefs::isFile)?.let { File(FileRefs.path(it)) }

    override fun homeKey(): String? = locator.current()?.value

    override fun seen(): FlagSeen {
        val dir = home() ?: return FlagSeen.UNREADABLE
        if (!dir.isDirectory || dir.list() == null) return FlagSeen.UNREADABLE
        return if (File(dir, NAME).exists()) FlagSeen.PRESENT else FlagSeen.MISSING
    }

    override fun write(): Boolean = runCatching {
        val dir = home() ?: return false
        File(dir, NAME).apply { if (!exists()) writeText("Delete this file to turn Kids Mode off.\n") }.exists()
    }.getOrDefault(false)

    override fun delete() {
        runCatching { home()?.let { File(it, NAME).delete() } }
    }

    override fun flagPath(): String? = home()?.let { File(it, NAME).path }

    private companion object {
        const val NAME = "kids-mode.flag"
    }
}
