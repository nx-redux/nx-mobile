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

#include "imagerendereres3.h"
#include "../../libretro-common/include/libretro.h"
#include "es3utils.h"
#include "../pixelconvert.h"

namespace libretrodroid {

ImageRendererES3::ImageRendererES3() {
    glGenTextures(1, &currentTexture);
    glBindTexture(GL_TEXTURE_2D, currentTexture);
}

void ImageRendererES3::onNewFrame(const void *data, unsigned width, unsigned height, size_t pitch) {
    // Converted into a scratch buffer, never in place: the core's frame is const and may come back as a duplicate.
    if (pixelFormat == RETRO_PIXEL_FORMAT_0RGB1555) {
        data = convert0RGB1555ToRGB565(data, width, height, pitch, convertedPixels);
        pitch = (size_t) width * bytesPerPixel;
    }

    if (lastFrameSize.first != width || lastFrameSize.second != height || isDirty) {
        initializeTextures(width, height);
    }

    glBindTexture(GL_TEXTURE_2D, currentTexture);

    glPixelStorei(GL_UNPACK_ALIGNMENT, bytesPerPixel);
    glPixelStorei(GL_UNPACK_ROW_LENGTH, pitch / bytesPerPixel);

    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, glFormat, glType, data);

    glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);

    glBindTexture(GL_TEXTURE_2D, 0);

    Renderer::onNewFrame(data, width, height, pitch);
}

void ImageRendererES3::initializeTextures(unsigned int width, unsigned int height) {
    for (auto& i : *framebuffers) {
        ES3Utils::deleteFramebuffer(std::move(i));
    }
    framebuffers = libretrodroid::ES3Utils::buildShaderPasses(width, height, shaders);

    glBindTexture(GL_TEXTURE_2D, currentTexture);
    glTexImage2D(GL_TEXTURE_2D, 0, glInternalFormat, width, height, 0, glFormat, glType, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, shaders.linearTexture ? GL_LINEAR : GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, shaders.linearTexture ? GL_LINEAR : GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    if (swapRedAndBlueChannels) {
        applyGLSwizzle(GL_BLUE, GL_GREEN, GL_RED, GL_ALPHA);
    } else {
        applyGLSwizzle(GL_RED, GL_GREEN, GL_BLUE, GL_ALPHA);
    }

    glBindTexture(GL_TEXTURE_2D, 0);

    isDirty = false;
}

void ImageRendererES3::applyGLSwizzle(int r, int g, int b, int a) {
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_R, r);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_G, g);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_B, b);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_A, a);
}

void ImageRendererES3::releaseGlObjects() {
    for (auto& framebuffer : *framebuffers) {
        ES3Utils::deleteFramebuffer(std::move(framebuffer));
    }
    framebuffers->clear();
    if (currentTexture != 0) glDeleteTextures(1, &currentTexture);
    currentTexture = 0;
}

uintptr_t ImageRendererES3::getTexture() {
    return currentTexture;
}

uintptr_t ImageRendererES3::getFramebuffer() {
    return 0; // ImageRender does not really expose a framebuffer.
}

void ImageRendererES3::setPixelFormat(int pixelFormat) {
    this->pixelFormat = pixelFormat;

    switch (pixelFormat) {

        case RETRO_PIXEL_FORMAT_XRGB8888:
            this->glInternalFormat = GL_RGBA;
            this->glFormat = GL_RGBA;
            this->glType = GL_UNSIGNED_BYTE;
            this->bytesPerPixel = 4;
            this->swapRedAndBlueChannels = true;
            break;

        default:
        case RETRO_PIXEL_FORMAT_0RGB1555:
        case RETRO_PIXEL_FORMAT_RGB565:
            this->glInternalFormat = GL_RGB565;
            this->glFormat = GL_RGB;
            this->glType = GL_UNSIGNED_SHORT_5_6_5;
            this->bytesPerPixel = 2;
            this->swapRedAndBlueChannels = false;
            break;
    }
}

void ImageRendererES3::updateRenderedResolution(unsigned int width, unsigned int height) {}

bool ImageRendererES3::rendersInVideoCallback() {
    return false;
}

void ImageRendererES3::setShaders(ShaderManager::Chain newShaders) {
    this->shaders = newShaders;
    this->isDirty = true;
}

Renderer::PassData ImageRendererES3::getPassData(unsigned int layer) {
    return ES3Utils::passData(*framebuffers, layer);
}

} //namespace libretrodroid
