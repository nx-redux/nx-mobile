/*
 *     Copyright (C) 2021  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include "vfs.h"

#include <unistd.h>
#include <sys/stat.h>
#include <optional>
#include <cstdint>
#include <cstddef>
#include <cstdlib>

#include "vfs/vfs_implementation.h"
#include "../log.h"
#include "../utils/utils.h"

namespace libretrodroid {

// struct retro_vfs_interface is all function pointers (v3 has 19), so upstream's v4 struct
// places stat_64 at sizeof(v3 struct). If the submodule ever grows the struct (a libretro-common
// bump bringing the real stat_64), the first assert fires: switch to the upstream member then.
static_assert(sizeof(struct retro_vfs_interface) == 19 * sizeof(void*),
              "retro_vfs_interface is no longer the v3 layout; use the upstream v4 member");
static_assert(offsetof(nx_retro_vfs_interface_v4, v3) == 0,
              "v4 interface must start with the v3 interface");
static_assert(offsetof(nx_retro_vfs_interface_v4, stat_64) == sizeof(struct retro_vfs_interface),
              "stat_64 must directly follow the v3 members, as in upstream's v4 struct");

namespace {

// Real-filesystem stat with a 64-bit size. Same flags as libretro-common's
// retro_vfs_stat_impl (the "every other platform" branch): valid, directory, character special.
int realStat64(const char *path, int64_t *size) {
    if (path == nullptr || *path == '\0') {
        return 0;
    }

    struct stat statBuf {};
    if (::stat(path, &statBuf) < 0) {
        return 0;
    }

    if (size != nullptr) {
        *size = (int64_t) statBuf.st_size;
    }

    int result = RETRO_VFS_STAT_IS_VALID;
    if (S_ISDIR(statBuf.st_mode)) {
        result |= RETRO_VFS_STAT_IS_DIRECTORY;
    }
    if (S_ISCHR(statBuf.st_mode)) {
        result |= RETRO_VFS_STAT_IS_CHARACTER_SPECIAL;
    }
    return result;
}

} // namespace

const char *VFS::path(struct retro_vfs_file_handle* stream) {
    LOGV("VFS Calling path");
    return retro_vfs_file_get_path_impl(stream);
}

struct retro_vfs_file_handle* VFS::open(const char* path, unsigned int mode, unsigned int hints) {
    LOGV("VFS Calling open: %s %i", path, mode);
    auto result = VFS::getInstance().virtualOpen(path, mode, hints);
    if (result != nullptr) {
        return result;
    }

    return retro_vfs_file_open_impl(path, mode, hints);
}

int VFS::close(struct retro_vfs_file_handle *stream) {
    LOGV("VFS Calling close");
    {
        auto& vfs = VFS::getInstance();
        std::lock_guard<std::mutex> lock(vfs.virtualOffsetsMutex);
        vfs.virtualOffsets.erase(stream);
    }
    return retro_vfs_file_close_impl(stream);
}

int64_t VFS::size(struct retro_vfs_file_handle *stream) {
    LOGV("VFS Calling size");
    return retro_vfs_file_size_impl(stream);
}

int64_t VFS::tell(struct retro_vfs_file_handle *stream) {
    LOGV("VFS Calling tell");
    if (auto offset = VFS::getInstance().virtualOffset(stream)) {
        return *offset;
    }
    return retro_vfs_file_tell_impl(stream);
}

// A virtual handle's seek moves only its own offset; 0 on success, -1 before the start, as retro_vfs_file_seek_impl.
int64_t VFS::seek(struct retro_vfs_file_handle *stream, int64_t offset, int seek_position) {
    LOGV("VFS Calling seek");
    auto& vfs = VFS::getInstance();
    {
        std::lock_guard<std::mutex> lock(vfs.virtualOffsetsMutex);
        auto it = vfs.virtualOffsets.find(stream);
        if (it != vfs.virtualOffsets.end()) {
            int64_t base = 0;
            if (seek_position == RETRO_VFS_SEEK_POSITION_CURRENT) base = it->second;
            else if (seek_position == RETRO_VFS_SEEK_POSITION_END) base = stream->size;
            else if (seek_position != RETRO_VFS_SEEK_POSITION_START) return -1;
            if (base + offset < 0) return -1;
            it->second = base + offset;
            return 0;
        }
    }
    return retro_vfs_file_seek_impl(stream, offset, seek_position);
}

int64_t VFS::read(struct retro_vfs_file_handle *stream, void *s, uint64_t len) {
    LOGV("VFS Calling read");
    auto& vfs = VFS::getInstance();
    auto offset = vfs.virtualOffset(stream);
    if (!offset) {
        return retro_vfs_file_read_impl(stream, s, len);
    }
    if (s == nullptr) {
        return -1;
    }
    ssize_t n = pread64(fileno(stream->fp), s, (size_t) len, *offset);
    if (n < 0) {
        return -1;
    }
    std::lock_guard<std::mutex> lock(vfs.virtualOffsetsMutex);
    auto it = vfs.virtualOffsets.find(stream);
    if (it != vfs.virtualOffsets.end()) {
        it->second = *offset + n;
    }
    return n;
}

int64_t VFS::write(struct retro_vfs_file_handle *stream, const void *s, uint64_t len) {
    LOGV("VFS Calling write");
    return retro_vfs_file_write_impl(stream, s, len);
}

int VFS::flush(struct retro_vfs_file_handle *stream) {
    LOGV("VFS Calling flush");
    return retro_vfs_file_flush_impl(stream);
}

int VFS::remove(const char *path) {
    LOGV("VFS Calling remove");
    return retro_vfs_file_remove_impl(path);
}

int VFS::rename(const char *old_path, const char *new_path) {
    LOGV("VFS Calling rename");
    return retro_vfs_file_rename_impl(old_path, new_path);
}

int64_t VFS::truncate(struct retro_vfs_file_handle* stream, int64_t length) {
    LOGV("VFS Calling truncate");
    return retro_vfs_file_truncate_impl(stream, length);
}

// v3 stat shares the v4 path and saturates sizes of 2 GiB or more to INT32_MAX, matching upstream
// libretro-common's retro_vfs_stat_impl (which wraps retro_vfs_stat_64_impl the same way), so a
// caller never sees a wrapped negative size.
int VFS::stat(const char *path, int32_t *size) {
    LOGV("VFS Calling stat: %s", path);
    int64_t size64 = 0;
    int result = statInternal(path, size != nullptr ? &size64 : nullptr);
    if (size != nullptr) {
        *size = size64 > (int64_t) INT32_MAX ? INT32_MAX : (int32_t) size64;
    }
    return result;
}

int VFS::stat64(const char *path, int64_t *size) {
    LOGV("VFS Calling stat_64: %s", path);
    return statInternal(path, size);
}

int VFS::statInternal(const char *path, int64_t *size) {
    auto result = VFS::getInstance().virtualStat(path, size);
    if (result.has_value()) {
        return result.value();
    }

    return realStat64(path, size);
}

int VFS::mkdir(const char *dir) {
    LOGV("VFS Calling mkdir: %s", dir);
    return retro_vfs_mkdir_impl(dir);
}

// Directory listing goes to the real filesystem only. Virtual files have no real parent
// directory, so listing "/rom" (or wherever the virtual path points) fails or omits them,
// the same as RetroArch's implementation would for a path that isn't on disk.
struct retro_vfs_dir_handle* VFS::opendir(const char *dir, bool include_hidden) {
    LOGV("VFS Calling opendir: %s", dir);
    return retro_vfs_opendir_impl(dir, include_hidden);
}

bool VFS::readdir(struct retro_vfs_dir_handle *dirstream) {
    LOGV("VFS Calling readdir");
    return retro_vfs_readdir_impl(dirstream);
}

const char* VFS::direntGetName(struct retro_vfs_dir_handle *dirstream) {
    LOGV("VFS Calling dirent_get_name");
    return retro_vfs_dirent_get_name_impl(dirstream);
}

bool VFS::direntIsDir(struct retro_vfs_dir_handle *dirstream) {
    LOGV("VFS Calling dirent_is_dir");
    return retro_vfs_dirent_is_dir_impl(dirstream);
}

int VFS::closedir(struct retro_vfs_dir_handle *dirstream) {
    LOGV("VFS Calling closedir");
    return retro_vfs_closedir_impl(dirstream);
}

retro_vfs_interface * VFS::getInterface() {
    static nx_retro_vfs_interface_v4 vfsInterface {
        {
            /* Introduced in VFS API v1 */
            &VFS::path,
            &VFS::open,
            &VFS::close,
            &VFS::size,
            &VFS::tell,
            &VFS::seek,
            &VFS::read,
            &VFS::write,
            &VFS::flush,
            &VFS::remove,
            &VFS::rename,

            /* Introduced in VFS API v2 */
            &VFS::truncate,

            /* Introduced in VFS API v3 */
            &VFS::stat,
            &VFS::mkdir,
            &VFS::opendir,
            &VFS::readdir,
            &VFS::direntGetName,
            &VFS::direntIsDir,
            &VFS::closedir
        },

        /* Introduced in VFS API v4 */
        &VFS::stat64
    };
    // Cores see the v3 view; one that negotiated v4 reads stat_64 past its end.
    return &vfsInterface.v3;
}

void VFS::add(std::vector<VFSFile> files) {
    for (auto& file : files) virtualFiles.push_back(std::move(file));
}

void VFS::deinitialize() {
    virtualFiles.clear();
}

struct retro_vfs_file_handle* VFS::virtualOpen(const char *path, unsigned int mode, unsigned int hints) {
    LOGV("VFS Calling open: %s %i", path, mode);

    if (path == nullptr) {
        return nullptr;
    }

    VFSFile* virtualFile = findVirtualFile(path);

    if (virtualFile == nullptr) {
        return nullptr;
    }

    LOGD("VFS Performing virtual file open: %s", virtualFile->getFileName().data());

    int duplicateFD = dup(virtualFile->getFD());
    if (duplicateFD < 0) {
        LOGE("VFS Cannot duplicate the virtual file's descriptor: %s", path);
        return nullptr;
    }
    FILE* file = fdopen(duplicateFD, "rb");
    if (file == nullptr) {
        LOGE("VFS Cannot open the virtual file's descriptor: %s", path);
        ::close(duplicateFD);
        return nullptr;
    }
    size_t size = Utils::getFileSize(file);

    LOGV("VFS Virtual file size: %zu", size);

    // calloc, not new: retro_vfs_file_close_impl releases the handle with free(). Zeroed, so fields this code doesn't
    // set (the CD-ROM state when built with it) start empty.
    auto stream = static_cast<retro_vfs_file_handle*>(calloc(1, sizeof(retro_vfs_file_handle)));
    if (stream == nullptr) {
        fclose(file);
        return nullptr;
    }

    // fdopen transfers ownership of duplicateFD to the FILE* below, and buffered
    // I/O goes through stream->fp. Leave stream->fd at 0 so retro_vfs_file_close_impl
    // closes the fd once via fclose(stream->fp) and skips its raw close(stream->fd),
    // which would otherwise close an fd owned by the FILE* (fatal under fdsan).
    stream->fd = 0;
    stream->hints = hints;
    stream->size = size;
    stream->buf = nullptr;
    stream->fp = file;
    stream->orig_path = strdup(virtualFile->getFileName().data());
    stream->mappos = 0;
    stream->mapsize = 0;
    stream->mapped = nullptr;
    stream->scheme = VFS_SCHEME_NONE;

    {
        std::lock_guard<std::mutex> lock(virtualOffsetsMutex);
        virtualOffsets[stream] = 0;
    }
    return stream;
}

std::optional<int> VFS::virtualStat(const char *path, int64_t *size) {
    if (path == nullptr) {
        return std::nullopt;
    }

    VFSFile* virtualFile = findVirtualFile(path);

    if (virtualFile == nullptr) {
        return std::nullopt;
    }

    struct stat fileStat {};
    if (fstat(virtualFile->getFD(), &fileStat) < 0) {
        LOGE("VFS Cannot stat virtual file: %s", path);
        return 0;
    }

    if (size != nullptr) {
        *size = (int64_t) fileStat.st_size;
    }

    LOGV("VFS Virtual file stat: %s %lld", path, (long long) fileStat.st_size);
    return RETRO_VFS_STAT_IS_VALID;
}

std::optional<int64_t> VFS::virtualOffset(struct retro_vfs_file_handle* stream) {
    std::lock_guard<std::mutex> lock(virtualOffsetsMutex);
    auto it = virtualOffsets.find(stream);
    if (it == virtualOffsets.end()) {
        return std::nullopt;
    }
    return it->second;
}

VFSFile* VFS::findVirtualFile(const char *path) {
    for (auto& virtualFile : virtualFiles) {
        if (strcmp(path, virtualFile.getFileName().data()) == 0) {
            return &virtualFile;
        }
    }
    return nullptr;
}

} // namespace libretrodroid