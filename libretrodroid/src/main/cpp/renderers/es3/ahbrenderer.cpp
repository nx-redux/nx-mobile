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

#include "ahbrenderer.h"

#include <GLES2/gl2ext.h>
#include <poll.h>
#include <unistd.h>

#include "../../log.h"

namespace libretrodroid {

namespace {

struct EglProcs {
    PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC getNativeClientBuffer = nullptr;
    PFNEGLCREATEIMAGEKHRPROC createImage = nullptr;
    PFNEGLDESTROYIMAGEKHRPROC destroyImage = nullptr;
    PFNGLEGLIMAGETARGETTEXTURE2DOESPROC imageTargetTexture = nullptr;
    PFNEGLCREATESYNCKHRPROC createSync = nullptr;
    PFNEGLWAITSYNCKHRPROC waitSync = nullptr;
    PFNEGLDESTROYSYNCKHRPROC destroySync = nullptr;
    bool loaded = false;
};

EglProcs& procs() {
    static EglProcs p;
    if (!p.loaded) {
        p.loaded = true;
        p.getNativeClientBuffer = reinterpret_cast<PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC>(
            eglGetProcAddress("eglGetNativeClientBufferANDROID"));
        p.createImage = reinterpret_cast<PFNEGLCREATEIMAGEKHRPROC>(eglGetProcAddress("eglCreateImageKHR"));
        p.destroyImage = reinterpret_cast<PFNEGLDESTROYIMAGEKHRPROC>(eglGetProcAddress("eglDestroyImageKHR"));
        p.imageTargetTexture = reinterpret_cast<PFNGLEGLIMAGETARGETTEXTURE2DOESPROC>(
            eglGetProcAddress("glEGLImageTargetTexture2DOES"));
        p.createSync = reinterpret_cast<PFNEGLCREATESYNCKHRPROC>(eglGetProcAddress("eglCreateSyncKHR"));
        p.waitSync = reinterpret_cast<PFNEGLWAITSYNCKHRPROC>(eglGetProcAddress("eglWaitSyncKHR"));
        p.destroySync = reinterpret_cast<PFNEGLDESTROYSYNCKHRPROC>(eglGetProcAddress("eglDestroySyncKHR"));
        LOGI("AHB renderer: EGL procs: nativeClientBuffer=%d createImage=%d imageTarget=%d createSync=%d waitSync=%d",
             p.getNativeClientBuffer != nullptr, p.createImage != nullptr, p.imageTargetTexture != nullptr,
             p.createSync != nullptr, p.waitSync != nullptr);
    }
    return p;
}

}

AhbRenderer::AhbRenderer(ShaderManager::Chain shaders) : shaders(std::move(shaders)) {
    procs();
}

void AhbRenderer::waitSyncFd(int fd) {
    if (fd < 0) return;
    auto& p = procs();
    EGLDisplay display = eglGetCurrentDisplay();
    if (p.createSync != nullptr && p.waitSync != nullptr && p.destroySync != nullptr) {
        EGLint attribs[] = { EGL_SYNC_NATIVE_FENCE_FD_ANDROID, fd, EGL_NONE };
        EGLSyncKHR sync = p.createSync(display, EGL_SYNC_NATIVE_FENCE_ANDROID, attribs);
        if (sync != EGL_NO_SYNC_KHR) {
            // EGL owns the fd now. A server-side wait: GL commands after this run once the Vulkan copy is done.
            p.waitSync(display, sync, 0);
            p.destroySync(display, sync);
            return;
        }
        static bool warned = false;
        if (!warned) {
            warned = true;
            LOGW("AHB renderer: eglCreateSyncKHR(NATIVE_FENCE) failed (0x%x): CPU-waiting the sync_file", eglGetError());
        }
    }
    pollfd pfd { fd, POLLIN, 0 };
    poll(&pfd, 1, 1000);
    close(fd);
}

void AhbRenderer::setFrame(AHardwareBuffer* buffer, uint64_t id, unsigned width, unsigned height, int syncFd) {
    Import* found = nullptr;
    for (auto& import : imports) {
        if (import.id == id) { found = &import; break; }
    }

    if (found == nullptr) {
        auto& p = procs();
        if (p.getNativeClientBuffer == nullptr || p.createImage == nullptr || p.imageTargetTexture == nullptr) {
            LOGE("AHB renderer: EGL lacks AHardwareBuffer import");
            if (syncFd >= 0) close(syncFd);
            return;
        }
        // At most the bridge's two live buffers: older imports are from buffers it has since replaced.
        while (imports.size() >= 2) {
            deleteImport(imports.front());
            imports.erase(imports.begin());
        }

        Import import;
        import.id = id;
        EGLClientBuffer clientBuffer = p.getNativeClientBuffer(buffer);
        EGLint attribs[] = { EGL_IMAGE_PRESERVED_KHR, EGL_TRUE, EGL_NONE };
        import.image = p.createImage(eglGetCurrentDisplay(), EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID, clientBuffer,
                                     attribs);
        if (import.image == EGL_NO_IMAGE_KHR) {
            LOGE("AHB renderer: eglCreateImageKHR(AHB) failed: 0x%x", eglGetError());
            if (syncFd >= 0) close(syncFd);
            return;
        }
        glGenTextures(1, &import.texture);
        glBindTexture(GL_TEXTURE_2D, import.texture);
        p.imageTargetTexture(GL_TEXTURE_2D, (GLeglImageOES) import.image);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glBindTexture(GL_TEXTURE_2D, 0);
        GLenum error = glGetError();
        LOGI("AHB renderer: imported AHB id %llu %ux%u into GL texture %u (glError 0x%x)", (unsigned long long) id, width,
             height, import.texture, error);
        imports.push_back(import);
        found = &imports.back();
    }

    waitSyncFd(syncFd);

    currentTexture = found->texture;
    if (textureWidth != width || textureHeight != height) {
        textureWidth = width;
        textureHeight = height;
        passesDirty = true;
    }
}

void AhbRenderer::onNewFrame(const void *data, unsigned width, unsigned height, size_t pitch) {
    Renderer::onNewFrame(data, width, height, pitch);
    if (passesDirty) rebuildPasses();
}

void AhbRenderer::rebuildPasses() {
    passesDirty = false;
    for (auto& pass : *framebuffers) ES3Utils::deleteFramebuffer(std::move(pass));
    framebuffers->clear();
    if (textureWidth == 0 || textureHeight == 0) return;
    framebuffers = ES3Utils::buildShaderPasses(textureWidth, textureHeight, shaders);
    passWidth = textureWidth;
    passHeight = textureHeight;
}

void AhbRenderer::setShaders(ShaderManager::Chain value) {
    shaders = std::move(value);
    passesDirty = true;
    rebuildPasses();
}

Renderer::PassData AhbRenderer::getPassData(unsigned int layer) {
    return ES3Utils::passData(*framebuffers, layer);
}

void AhbRenderer::deleteImport(Import& import) {
    if (import.texture != 0) glDeleteTextures(1, &import.texture);
    if (import.image != EGL_NO_IMAGE_KHR && procs().destroyImage != nullptr) {
        procs().destroyImage(eglGetCurrentDisplay(), import.image);
    }
    if (currentTexture == import.texture) currentTexture = 0;
    import = Import {};
}

void AhbRenderer::releaseGlObjects() {
    for (auto& import : imports) deleteImport(import);
    imports.clear();
    for (auto& pass : *framebuffers) ES3Utils::deleteFramebuffer(std::move(pass));
    framebuffers->clear();
}

}
