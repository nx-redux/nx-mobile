/**
 * chdreader.h - CHD disc reader for rcheevos hashing (ported from nx-redux chd_reader.h)
 *
 * CD reader entry points that let rcheevos hash CHD disc images. The CHD is found through
 * AchievementsHash::lookup, so it is read from an fd rather than opened by path.
 */

#ifndef LIBRETRODROID_CHDREADER_H
#define LIBRETRODROID_CHDREADER_H

#include <cstddef>
#include <cstdint>

namespace libretrodroid {

/**
 * [path] is resolved through AchievementsHash::lookup; [track] is 1-based or an RC_HASH_CDTRACK_* value. nullptr when
 * the path is not a CHD, or on error.
 */
void* chdreader_open_track(const char* path, uint32_t track);

/** [sector] is an absolute disc LBA. Returns the bytes read. */
size_t chdreader_read_sector(void* track_handle, uint32_t sector, void* buffer, size_t requested_bytes);

/** Close a CHD track handle (closes the CHD and the FILE it was opened on). */
void chdreader_close_track(void* track_handle);

/** Absolute disc LBA of the track's first sector (0 for track 1 of a CD, 45000 for a GD-ROM high-density track). */
uint32_t chdreader_first_track_sector(void* track_handle);

}

#endif //LIBRETRODROID_CHDREADER_H
