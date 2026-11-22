#ifndef LIBRETRODROID_DISCFILE_H
#define LIBRETRODROID_DISCFILE_H

#include <cstddef>
#include <cstdint>

namespace libretrodroid {

/** The raw-disc reader the Dolphin core exports (nx_disc_*): reads the logical disc whatever the image format. */
struct DiscApi {
    void* (*open)(const char* path);
    int64_t (*size)(void* disc);
    int64_t (*read)(void* disc, uint64_t offset, void* buf, int64_t len);
    void (*close)(void* disc);
    void* (*open_fd)(int fd);   // null on a core older than nx_disc_open_fd
};

/**
 * An rc filereader over that reader, so Wii and GameCube discs hash the same from ISO, GCZ, RVZ, WIA or CISO.
 * Pure: no rcheevos or Android headers. seek/tell/read/close match rc_hash_filereader_t; the disc is opened from the
 * app's SAF descriptor (the app has no storage permission, so reopening /proc/self/fd/<fd> by name can be refused).
 */
namespace discfile {

/** Registers the reader (null clears it). The pointee must outlive its use. */
void setApi(const DiscApi* api);

/** The disc behind [fd] (the caller keeps and closes it), through open_fd; an older core gets /proc/self/fd/<fd>. */
void* open(int fd);
void seek(void* handle, int64_t offset, int origin);
int64_t tell(void* handle);
size_t read(void* handle, void* buffer, size_t bytes);
void close(void* handle);

/**
 * 16 (GameCube) or 19 (Wii) by the disc magic read through the reader, else [requested]. [found], when given, says
 * whether a magic was seen (false when the disc cannot be opened or has neither).
 */
uint32_t consoleFor(int fd, uint32_t requested, bool* found = nullptr);

}
}

#endif //LIBRETRODROID_DISCFILE_H
