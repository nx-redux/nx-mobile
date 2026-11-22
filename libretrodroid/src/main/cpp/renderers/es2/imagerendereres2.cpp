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

#include "imagerendereres2.h"
#include "../../libretro-common/include/libretro.h"
#include "../pixelconvert.h"

namespace libretrodroid {

ImageRendererES2::ImageRendererES2() {
    glGenTextures(1, &currentTexture);
    glBindTexture(GL_TEXTURE_2D, currentTexture);
}

void ImageRendererES2::onNewFrame(const void *data, unsigned width, unsigned height, size_t pitch) {
    glBindTexture(GL_TEXTURE_2D, currentTexture);

    glPixelStorei(GL_UNPACK_ALIGNMENT, bytesPerPixel);

    // Converted into a scratch buffer, never in place: the core's frame is const and may come back as a duplicate.
    // The converted frame is packed, so it uploads in one call.
    if (pixelFormat == RETRO_PIXEL_FORMAT_XRGB8888) {
        data = convertXRGB8888ToRGBA(data, width, height, pitch, convertedPixels32);
        pitch = (size_t) width * bytesPerPixel;
    } else if (pixelFormat == RETRO_PIXEL_FORMAT_0RGB1555) {
        data = convert0RGB1555ToRGB565(data, width, height, pitch, convertedPixels16);
        pitch = (size_t) width * bytesPerPixel;
    }

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, linear ? GL_LINEAR : GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, linear ? GL_LINEAR : GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    if (lastFrameSize.first != width || lastFrameSize.second != height) {
        glTexImage2D(GL_TEXTURE_2D, 0, glInternalFormat, width, height, 0, glFormat, glType, nullptr);
    }

    if (bytesPerPixel * width == pitch) {
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, glFormat, glType, data);
    } else {
        // ES2 has no GL_UNPACK_ROW_LENGTH: a padded frame uploads row by row.
        for (int i = 0; i < height; i++) {
            auto row = (char*) data + (pitch) * i;
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, i, width, 1, glFormat, glType, row);
        }
    }

    glBindTexture(GL_TEXTURE_2D, 0);

    Renderer::onNewFrame(data, width, height, pitch);
}

void ImageRendererES2::releaseGlObjects() {
    if (currentTexture != 0) glDeleteTextures(1, &currentTexture);
    currentTexture = 0;
}

uintptr_t ImageRendererES2::getTexture() {
    return currentTexture;
}

uintptr_t ImageRendererES2::getFramebuffer() {
    return 0; // ImageRender does not really expose a framebuffer.
}

void ImageRendererES2::setPixelFormat(int pixelFormat) {
    this->pixelFormat = pixelFormat;

    switch (pixelFormat) {
        case RETRO_PIXEL_FORMAT_XRGB8888:
            this->glInternalFormat = GL_RGBA;
            this->glFormat = GL_RGBA;
            this->glType = GL_UNSIGNED_BYTE;
            this->bytesPerPixel = 4;
            break;

        default:
        case RETRO_PIXEL_FORMAT_0RGB1555:
        case RETRO_PIXEL_FORMAT_RGB565:
            this->glInternalFormat = GL_RGB;
            this->glFormat = GL_RGB;
            this->glType = GL_UNSIGNED_SHORT_5_6_5;
            this->bytesPerPixel = 2;
            break;
    }
}

void ImageRendererES2::updateRenderedResolution(unsigned int width, unsigned int height) {}

bool ImageRendererES2::rendersInVideoCallback() {
    return false;
}

void ImageRendererES2::setShaders(ShaderManager::Chain shaders) {
    this->linear = shaders.linearTexture;
}

// No multiple passes on ES2.
Renderer::PassData ImageRendererES2::getPassData(unsigned int layer) {
    return { };
}

} //namespace libretrodroid
