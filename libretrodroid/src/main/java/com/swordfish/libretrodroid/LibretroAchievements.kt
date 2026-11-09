package com.swordfish.libretrodroid

import android.os.ParcelFileDescriptor

/** Host-side helpers that need no GLRetroView. */
object LibretroAchievements {
    /**
     * Points the hasher at the Dolphin core ([path] is its .so), whose disc reader hashes Wii and GameCube images in any
     * format. Only stores the path (cheap, any thread); the library is loaded during a Wii/GameCube hash and unloaded after
     * it. A missing or unusable library is logged and those consoles keep the plain file reader.
     */
    fun setDiscLibrary(path: String) = LibretroDroid.achievementsSetDiscLibrary(path)

    /**
     * The RetroAchievements hash of [files] (the game first, then anything its sheet names) for the rcheevos console
     * [consoleId], or null when rcheevos cannot identify it. The descriptors are duplicated, so the caller keeps its own.
     */
    fun hash(consoleId: Int, files: List<VirtualFile>): String? {
        val detached = detachAll(files) {
            DetachedVirtualFile(it.virtualPath, ParcelFileDescriptor.dup(it.fileDescriptor.fileDescriptor).detachFd())
        }
        return handOver(detached) { LibretroDroid.achievementsHash(consoleId, it) }
    }
}
