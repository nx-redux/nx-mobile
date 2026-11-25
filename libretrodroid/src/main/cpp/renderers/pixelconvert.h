/*
 *     Copyright (C) 2026  Mohammad Syuhada
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

#ifndef LIBRETRODROID_PIXELCONVERT_H
#define LIBRETRODROID_PIXELCONVERT_H

#include <cstddef>
#include <cstdint>
#include <vector>

namespace libretrodroid {

// A software frame the GL formats can't take as it is, converted into a scratch buffer: the core's frame is const
// (a core may keep it and hand it back as a duplicate frame, which an in-place conversion would convert twice). Reads
// width x height pixels from rows pitch bytes apart and writes them packed (row stride = width pixels), resizing
// out as needed (it keeps its capacity across frames). Returns out.data().

// 0RGB1555 to RGB565: red and blue keep their 5 bits, green's 5 bits move up one (its low bit 0).
const uint16_t* convert0RGB1555ToRGB565(
    const void* data, unsigned width, unsigned height, size_t pitch, std::vector<uint16_t>& out);

}

#endif //LIBRETRODROID_PIXELCONVERT_H
