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

#include "pixelconvert.h"


namespace libretrodroid {

const uint16_t* convert0RGB1555ToRGB565(
    const void* data, unsigned width, unsigned height, size_t pitch, std::vector<uint16_t>& out
) {
    out.resize((size_t) width * height);
    auto bytes = static_cast<const uint8_t*>(data);
    for (unsigned y = 0; y < height; ++y) {
        auto row = reinterpret_cast<const uint16_t*>(bytes + pitch * y);
        uint16_t* target = out.data() + (size_t) width * y;
        for (unsigned x = 0; x < width; ++x) {
            uint16_t pixel = row[x];
            target[x] = (uint16_t) ((0x1Fu & pixel) | (((0x1Fu << 5) & pixel) << 1) | (((0x1Fu << 10) & pixel) << 1));
        }
    }
    return out.data();
}

}
