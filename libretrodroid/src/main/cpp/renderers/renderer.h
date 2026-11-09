/*
 *     Copyright (C) 2019  Filippo Scognamiglio
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

#ifndef LIBRETRODROID_RENDERER_H
#define LIBRETRODROID_RENDERER_H

#include <cstdint>
#include <utility>
#include <vector>
#include <optional>

#include "../shadermanager.h"

namespace libretrodroid {

class Renderer {
public:
    struct PassData {
        std::optional<unsigned int> framebuffer = std::nullopt;
        std::optional<unsigned int> texture = std::nullopt;
        std::optional<unsigned int> width = std::nullopt;
        std::optional<unsigned int> height = std::nullopt;
    };

public:
    virtual uintptr_t getFramebuffer() = 0;
    virtual uintptr_t getTexture() = 0;
    virtual void updateRenderedResolution(unsigned width, unsigned height) = 0;
    virtual void setPixelFormat(int pixelFormat) = 0;
    virtual void onNewFrame(const void *data, unsigned width, unsigned height, size_t pitch);
    // The frame is drawn from inside the core's video callback (frame timing), not the frontend's own draw.
    virtual bool rendersInVideoCallback() = 0;
    // The core draws with this GL context (a GL hw core), so a draw has to shield the state it leaves bound. A
    // Vulkan core never touches this context: its frames arrive as buffers, like a software frame's.
    virtual bool hasCoreGlState() { return false; }
    virtual void setShaders(ShaderManager::Chain shaders) = 0;
    virtual PassData getPassData(unsigned int layer) = 0;
    // The texture's size in texels. A GL core's framebuffer is sized to the largest frame it has delivered (at least its
    // base geometry, clamped to the GPU limit), and a frame (lastFrameSize) fills only its bottom-left corner; a
    // software frame's texture is the frame's size.
    virtual std::pair<int, int> getTextureSize() { return lastFrameSize; }

    // Deletes the renderer's GL objects. Only with the context that created them current (Video checks); the
    // destructor never touches GL, so a renderer whose context is gone is simply dropped.
    virtual void releaseGlObjects() {}

    virtual ~Renderer() = default;

public:
    std::pair<int, int> lastFrameSize;
};

}


#endif //LIBRETRODROID_RENDERER_H
