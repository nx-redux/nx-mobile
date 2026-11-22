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

#ifndef LIBRETRODROID_AHBRENDERER_H
#define LIBRETRODROID_AHBRENDERER_H

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <android/hardware_buffer.h>

#include <memory>
#include <vector>

#include "../renderer.h"
#include "es3utils.h"

namespace libretrodroid {

// Stands in for FramebufferRenderer when a Vulkan core's frames arrive as AHardwareBuffers (VulkanBridge).
// Each buffer is imported once into this context (EGLImage -> GL_TEXTURE_2D) and drawn by Video's usual path, so
// layout, shaders and presets work unchanged. Row 0 of the buffer is the top of the picture (Vulkan's origin).
class AhbRenderer: public Renderer {
public:
    explicit AhbRenderer(ShaderManager::Chain shaders);

    // The buffer to draw next. [syncFd] (owned, -1 = none): GL waits on it (GPU side) before sampling. [liveBuffers]:
    // how many buffers the bridge cycles through; that many imports are kept. [oldestLiveId]: imports with a lower id
    // are of buffers the bridge has replaced, and are dropped at once (0: none), so they do not stay pinned.
    void setFrame(AHardwareBuffer* buffer, uint64_t id, unsigned width, unsigned height, int syncFd, unsigned liveBuffers,
                  uint64_t oldestLiveId);

    // True once after a buffer that could not be imported as an EGL image: nothing of that frame is drawn.
    bool takeImportFailure() { const bool failed = importFailed; importFailed = false; return failed; }

    uintptr_t getTexture() override { return currentTexture; }
    uintptr_t getFramebuffer() override { return 0; }
    void onNewFrame(const void *data, unsigned width, unsigned height, size_t pitch) override;
    void setPixelFormat(int pixelFormat) override {}
    void updateRenderedResolution(unsigned int width, unsigned int height) override {}
    bool rendersInVideoCallback() override { return true; }
    void setShaders(ShaderManager::Chain shaders) override;
    PassData getPassData(unsigned int layer) override;
    std::pair<int, int> getTextureSize() override { return { (int) textureWidth, (int) textureHeight }; }
    void releaseGlObjects() override;

private:
    struct Import {
        uint64_t id = 0;
        EGLImageKHR image = EGL_NO_IMAGE_KHR;
        GLuint texture = 0;
    };

    void waitSyncFd(int fd);
    void deleteImport(Import& import);
    void rebuildPasses();

    std::vector<Import> imports;
    bool importFailed = false;
    GLuint currentTexture = 0;
    unsigned textureWidth = 0;
    unsigned textureHeight = 0;
    bool passesDirty = true;

    ShaderManager::Chain shaders;
    std::unique_ptr<ES3Utils::Framebuffers> framebuffers = std::make_unique<ES3Utils::Framebuffers>();
};

}

#endif //LIBRETRODROID_AHBRENDERER_H
