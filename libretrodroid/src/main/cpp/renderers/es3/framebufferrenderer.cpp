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

#include "framebufferrenderer.h"

#include <algorithm>
#include "es3utils.h"
#include "../../log.h"

namespace libretrodroid {

namespace {

// The core renders into the main framebuffer and the intermediate passes into their own: only a change in
// the intermediate passes needs them rebuilt. The final sampling filter is set per draw by Video.
bool sameIntermediatePasses(const ShaderManager::Chain& a, const ShaderManager::Chain& b) {
    if (a.passes.size() != b.passes.size()) return false;
    for (size_t i = 0; i + 1 < a.passes.size(); ++i) {
        if (!(a.passes[i] == b.passes[i])) return false;
    }
    return true;
}

}

FramebufferRenderer::FramebufferRenderer(
    unsigned width,
    unsigned height,
    bool depth,
    bool stencil,
    ShaderManager::Chain shaders
) {
    this->depth = depth;
    this->stencil = stencil;
    this->baseWidth = width;
    this->baseHeight = height;
    this->shaders = std::move(shaders);

    // Both limits bind: the colour texture and the depth/stencil renderbuffer share the size.
    GLint maxTexture = 0, maxRenderbuffer = 0;
    glGetIntegerv(GL_MAX_TEXTURE_SIZE, &maxTexture);
    glGetIntegerv(GL_MAX_RENDERBUFFER_SIZE, &maxRenderbuffer);
    maxSize = (unsigned) std::max(1, std::min(maxTexture, maxRenderbuffer));

    updateSize();
    isDirty = false;
    initializeBuffers();
}

bool FramebufferRenderer::updateSize() {
    // max(base geometry, the largest frame delivered), clamped to what the GPU allows. Not max_width/max_height: cores
    // report huge ones (melonDS DS 8198x4608 over every layout at 8x, flycast a square of its widest render size).
    unsigned newWidth = std::min(std::max(baseWidth, largestFrameWidth), maxSize);
    unsigned newHeight = std::min(std::max(baseHeight, largestFrameHeight), maxSize);
    if (newWidth == width && newHeight == height) return false;
    width = newWidth;
    height = newHeight;
    return true;
}

void FramebufferRenderer::onNewFrame(const void *data, unsigned width, unsigned height, size_t pitch) {
    Renderer::onNewFrame(data, width, height, pitch);

    // A frame larger than the framebuffer grows it. The core has already drawn this one (clipped) into the old
    // framebuffer, so one frame shows blank; the next draws into the new one.
    if (data != nullptr && (width > largestFrameWidth || height > largestFrameHeight)) {
        largestFrameWidth = std::max(largestFrameWidth, width);
        largestFrameHeight = std::max(largestFrameHeight, height);
        if (updateSize()) isDirty = true;
    }

    if (isDirty) {
        initializeBuffers();
        isDirty = false;
    }
}

void FramebufferRenderer::initializeBuffers() {
    LOGI("Core framebuffer %u x %u (base %u x %u, largest frame %u x %u, GL limit %u)",
         width, height, baseWidth, baseHeight, largestFrameWidth, largestFrameHeight, maxSize);
    // The pass framebuffers being replaced are deleted, not just dropped: their GL names would leak.
    for (auto& pass : *framebuffers) {
        ES3Utils::deleteFramebuffer(std::move(pass));
    }
    framebuffers = ES3Utils::buildShaderPasses(width, height, shaders);

    ES3Utils::deleteFramebuffer(std::move(framebuffer));
    framebuffer = ES3Utils::createFramebuffer(
        width,
        height,
        shaders.linearTexture,
        false,
        depth,
        stencil
    );
}

void FramebufferRenderer::releaseGlObjects() {
    for (auto& pass : *framebuffers) {
        ES3Utils::deleteFramebuffer(std::move(pass));
    }
    framebuffers->clear();
    ES3Utils::deleteFramebuffer(std::move(framebuffer));
    framebuffer = std::make_unique<ES3Utils::Framebuffer>();
}

uintptr_t FramebufferRenderer::getTexture() {
    return framebuffer->texture;
}

uintptr_t FramebufferRenderer::getFramebuffer() {
    return framebuffer->framebuffer;
}

void FramebufferRenderer::setPixelFormat(int pixelFormat) {
    // TODO... Here we should handle 32bit framebuffers.
}

void FramebufferRenderer::updateRenderedResolution(unsigned int width, unsigned int height) {
    baseWidth = width;
    baseHeight = height;
    if (updateSize()) isDirty = true;
}

bool FramebufferRenderer::rendersInVideoCallback() {
    return true;
}

void FramebufferRenderer::setShaders(ShaderManager::Chain shaders) {
    bool rebuild = !sameIntermediatePasses(this->shaders, shaders);
    this->shaders = std::move(shaders);
    if (rebuild) {
        isDirty = true;
    }
}

std::pair<int, int> FramebufferRenderer::getTextureSize() {
    return { (int) framebuffer->width, (int) framebuffer->height };
}

Renderer::PassData FramebufferRenderer::getPassData(unsigned int layer) {
    return ES3Utils::passData(*framebuffers, layer);
}

} //namespace libretrodroid
