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

#include "screenregion.h"

#include <algorithm>
#include <cmath>

namespace libretrodroid {

std::vector<ScreenRegion> ScreenRegion::unpack(const float* data, size_t count) {
    std::vector<ScreenRegion> out;
    if (data == nullptr) return out;
    for (size_t i = 0; i + PACKED_SIZE <= count; i += PACKED_SIZE) {
        const float* d = data + i;
        float alpha = std::isnan(d[10]) ? 1.0F : std::clamp(d[10], 0.0F, 1.0F);
        out.push_back({ d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7], d[8] != 0.0F, d[9] != 0.0F, alpha });
    }
    return out;
}

RegionQuad buildRegionQuad(const ScreenRegion& r, float vpX, float vpY, float vpW, float vpH, bool bottomLeftOrigin, int quarterTurns) {
    // View position (0..1, y down) to clip space (-1..1, y up).
    auto clipX = [&](float x) { return 2.0F * (vpX + x * vpW) - 1.0F; };
    auto clipY = [&](float y) { return 1.0F - 2.0F * (vpY + y * vpH); };
    // Frame row (y down) to texture v: a GL core's framebuffer has its origin at the bottom.
    auto texV = [&](float y) { return bottomLeftOrigin ? 1.0F - y : y; };

    float left = clipX(r.dstLeft), right = clipX(r.dstRight), top = clipY(r.dstTop), bottom = clipY(r.dstBottom);
    float u0 = r.srcLeft, u1 = r.srcRight, vTop = texV(r.srcTop), vBottom = texV(r.srcBottom);
    // The frame's corners clockwise from its top-left; turned counterclockwise k quarters, view corner i shows corner i + k.
    const float corners[4][2] = { { u0, vTop }, { u1, vTop }, { u1, vBottom }, { u0, vBottom } };
    const int k = ((quarterTurns % 4) + 4) % 4;
    const float* tl = corners[k];
    const float* tr = corners[(1 + k) % 4];
    const float* br = corners[(2 + k) % 4];
    const float* bl = corners[(3 + k) % 4];
    return RegionQuad {
        { left, top,  left, bottom,  right, top,  right, top,  left, bottom,  right, bottom },
        { tl[0], tl[1],  bl[0], bl[1],  tr[0], tr[1],  tr[0], tr[1],  bl[0], bl[1],  br[0], br[1] },
        r.shaded,
        r.alpha,
        r.srcRight - r.srcLeft,
        r.srcBottom - r.srcTop,
    };
}

bool regionDownscales(const RegionQuad& quad, float texW, float texH, float viewW, float viewH) {
    // Clip space spans 2 units: vertex 0 is (left, top), vertex 1 (left, bottom), vertex 2 (right, top).
    float dstW = std::fabs(quad.vertices[4] - quad.vertices[0]) / 2.0F * viewW;
    float dstH = std::fabs(quad.vertices[1] - quad.vertices[3]) / 2.0F * viewH;
    float srcW = quad.srcWidth * texW;
    float srcH = quad.srcHeight * texH;
    return srcW > dstW + 0.5F || srcH > dstH + 0.5F;
}

}
