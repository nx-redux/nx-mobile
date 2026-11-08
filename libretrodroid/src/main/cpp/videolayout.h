/*
 *     Copyright (C) 2025  Filippo Scognamiglio
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

#ifndef LIBRETRODROID_VIDEOLAYOUT_H
#define LIBRETRODROID_VIDEOLAYOUT_H

#include <array>
#include <vector>

#include "utils/rect.h"
#include "screenregion.h"

#define V_ALIGN_CENTER  0
#define V_ALIGN_TOP     1
#define V_ALIGN_BOTTOM  2

#define SCALE_MODE_FIT      0
#define SCALE_MODE_INTEGER  1
#define SCALE_MODE_STRETCH  2

namespace libretrodroid {

class VideoLayout {
public:
    VideoLayout(bool bottomLeftOrigin, float rotation, Rect viewportRect, unsigned int viewportAlignment);

    void updateAspectRatio(float aspectRatio);

    void updateScreenSize(unsigned screenWidth, unsigned screenHeight);

    void updateViewportSize(Rect viewportRect);

    void updateViewportAlignment(unsigned int viewportAlignment);

    void updateRotation(float rotation);

    void updateScaleMode(unsigned int scaleMode);

    void updateScreenOffset(float x, float y);

    void updateContentSize(unsigned width, unsigned height);

    std::array<float, 12>& getForegroundVertices() { return foregroundVertices; }
    std::array<float, 12>& getBackgroundVertices() { return backgroundVertices; }
    std::array<float, 12>& getFramebufferVertices() { return framebufferVertices; }
    std::array<float, 12>& getTextureCoordinates() { return textureCoordinates; }
    std::array<float, 4>& getRelativeForegroundBounds() { return relativeForegroundBounds; }

    // The foreground quad in window pixels (x, y from the bottom-left, w, h), for viewport-scaled shader passes.
    std::array<int, 4> getForegroundRectPixels();

    int getScreenWidth() { return screenWidth; }

    int getScreenHeight() { return screenHeight; }

    std::pair<float, float> getRelativePosition(float touchX, float touchY);

    // Parts of the frame drawn at places in the viewport (DS screens); empty = the single foreground quad. Rebuilt with
    // the viewport. Drawn unrotated: no core that uses regions sets a rotation.
    void updateScreenRegions(std::vector<ScreenRegion> regions);
    const std::vector<RegionQuad>& getRegionQuads() const { return regionQuads; }
    bool isBottomLeftOrigin() const { return bottomLeftOrigin; }

private:
    void updateBuffers();

    void updateForegroundVertices();

    void updateBackgroundVertices();

    void updateRelativeForegroundBounds();

    void updateRegionQuads();

private:
    std::array<float, 12> foregroundVertices = {
        -1.0F,
        -1.0F,

        -1.0F,
        +1.0F,

        +1.0F,
        -1.0F,

        +1.0F,
        -1.0F,

        -1.0F,
        +1.0F,

        +1.0F,
        +1.0F,
    };

    std::array<float, 12> textureCoordinates {
        0.0F,
        0.0F,

        0.0F,
        1.0F,

        1.0F,
        0.0F,

        1.0F,
        0.0F,

        0.0F,
        1.0F,

        1.0F,
        1.0F,
    };

    std::array<float, 12> backgroundVertices = {
        -1.0F,
        -1.0F,

        -1.0F,
        +1.0F,

        +1.0F,
        -1.0F,

        +1.0F,
        -1.0F,

        -1.0F,
        +1.0F,

        +1.0F,
        +1.0F,
    };

    std::array<float, 12> framebufferVertices = {
        -1.0F,
        -1.0F,

        -1.0F,
        +1.0F,

        +1.0F,
        -1.0F,

        +1.0F,
        -1.0F,

        -1.0F,
        +1.0F,

        +1.0F,
        +1.0F,
    };

    std::array<float, 4> relativeForegroundBounds = {
        +0.0F,
        +0.0F,
        +1.0F,
        +1.0F,
    };

    bool bottomLeftOrigin = false;
    float rotation = 0.0F;
    float aspectRatio = 1;
    Rect viewportRect = Rect(0.0F, 0.0F, 1.0F, 1.0F);

    unsigned screenWidth = 0;
    unsigned screenHeight = 0;

    unsigned viewportAlignment = V_ALIGN_CENTER;
    unsigned scaleMode = SCALE_MODE_FIT;
    float screenOffsetX = 0.0F;
    float screenOffsetY = 0.0F;
    unsigned contentWidth = 0;
    unsigned contentHeight = 0;
    std::vector<ScreenRegion> screenRegions;
    std::vector<RegionQuad> regionQuads;
};

} // namespace libretrodroid

#endif //LIBRETRODROID_VIDEOLAYOUT_H
