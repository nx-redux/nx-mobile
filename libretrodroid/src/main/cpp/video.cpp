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

#include <GLES2/gl2.h>
#include <GLES3/gl3.h>
#include <EGL/egl.h>
#include <cstdlib>
#include <string>
#include <cmath>
#include <utility>
#include <sstream>
#include <stdexcept>

#include "log.h"

#include "video.h"
#include "gldebug.h"
#include "renderers/es3/framebufferrenderer.h"
#include "renderers/es3/ahbrenderer.h"
#include <unistd.h>
#include "renderers/es3/imagerendereres3.h"
#include "renderers/es2/imagerendereres2.h"

namespace libretrodroid {

static void printGLString(const char *name, GLenum s) {
    const char *v = (const char *) glGetString(s);
    LOGI("GL %s = %s\n", name, v);
}

GLuint loadShader(GLenum shaderType, const char* pSource) {
    GLuint shader = glCreateShader(shaderType);
    if (shader) {
        glShaderSource(shader, 1, &pSource, nullptr);
        glCompileShader(shader);
        GLint compiled = 0;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
        if (!compiled) {
            GLint infoLen = 0;
            glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
            if (infoLen) {
                char* buf = (char*) malloc(infoLen);
                if (buf) {
                    glGetShaderInfoLog(shader, infoLen, nullptr, buf);
                    LOGE("Could not compile shader %d:\n%s\n",
                         shaderType, buf);
                    free(buf);
                }
                glDeleteShader(shader);
                shader = 0;
            }
        }
    }
    return shader;
}

GLuint createProgram(const char* pVertexSource, const char* pFragmentSource) {
    GLuint vertexShader = loadShader(GL_VERTEX_SHADER, pVertexSource);
    if (!vertexShader) {
        return 0;
    }

    GLuint pixelShader = loadShader(GL_FRAGMENT_SHADER, pFragmentSource);
    if (!pixelShader) {
        glDeleteShader(vertexShader);
        return 0;
    }

    GLuint program = glCreateProgram();
    if (program) {
        glAttachShader(program, vertexShader);
        glAttachShader(program, pixelShader);
        glLinkProgram(program);
        GLint linkStatus = GL_FALSE;
        glGetProgramiv(program, GL_LINK_STATUS, &linkStatus);
        if (linkStatus != GL_TRUE) {
            GLint bufLength = 0;
            glGetProgramiv(program, GL_INFO_LOG_LENGTH, &bufLength);
            if (bufLength) {
                char* buf = (char*) malloc(bufLength);
                if (buf) {
                    glGetProgramInfoLog(program, bufLength, nullptr, buf);
                    LOGE("Could not link program:\n%s\n", buf);
                    free(buf);
                }
            }
            glDeleteProgram(program);
            program = 0;
        }
    }
    // Flagged for deletion now: a linked program keeps them alive until glDeleteProgram, then both go.
    glDeleteShader(vertexShader);
    glDeleteShader(pixelShader);
    return program;
}

void Video::updateProgram() {
    if (loadedShaderType.has_value() && loadedShaderType.value() == requestedShaderConfig) {
        return;
    }

    // Marked loaded before building: a config that fails is not retried on every frame, only when a new one is set.
    loadedShaderType = requestedShaderConfig;

    auto shaders = ShaderManager::getShader(requestedShaderConfig);
    std::vector<ShaderChainEntry> chain;
    try {
        for (const auto& pass : shaders.passes) chain.push_back(createShaderChainEntry(pass));
    } catch (const std::exception& e) {
        // A shader the driver rejects falls back to the built-in default instead of ending the session.
        for (const auto& entry : chain) glDeleteProgram(entry.gProgram);
        chain.clear();
        if (requestedShaderConfig.type == ShaderManager::Type::SHADER_DEFAULT) throw;
        LOGE("Shader failed (%s); falling back to the default shader", e.what());
        shaders = ShaderManager::getShader({ ShaderManager::Type::SHADER_DEFAULT, { } });
        for (const auto& pass : shaders.passes) chain.push_back(createShaderChainEntry(pass));
    }

    linearTexture = shaders.linearTexture;
    for (const auto& entry : shadersChain) glDeleteProgram(entry.gProgram);
    shadersChain = std::move(chain);

    renderer->setShaders(shaders);
}

Video::ShaderChainEntry Video::createShaderChainEntry(const ShaderManager::Pass& pass) {
    auto shader = ShaderChainEntry { };

    shader.gProgram = createProgram(pass.vertex.data(), pass.fragment.data());
    if (!shader.gProgram) {
        LOGE("Could not create gl program.");
        throw std::runtime_error("Cannot create gl program");
    }

    shader.gvPositionHandle = glGetAttribLocation(shader.gProgram, "vPosition");

    shader.gvCoordinateHandle = glGetAttribLocation(shader.gProgram, "vCoordinate");

    shader.gTextureHandle = glGetUniformLocation(shader.gProgram, "texture");

    shader.gPreviousPassTextureHandle = glGetUniformLocation(shader.gProgram, "previousPass");

    shader.gTextureSizeHandle = glGetUniformLocation(shader.gProgram, "textureSize");

    shader.gScreenDensityHandle = glGetUniformLocation(shader.gProgram, "screenDensity");

    return shader;
}

void Video::setPresetChain(std::optional<PresetChain> chain) {
    if (!presetSupported) {
        if (chain) LOGW("Preset chains need OpenGL ES 3");
        return;
    }
    requestedPreset = std::move(chain);
    presetDirty = true;
    isDirty = true;
}

void Video::setPresetParameter(const std::string& id, float value) {
    // Not presetDirty: a parameter change updates the live renderer instead of rebuilding it.
    if (requestedPreset) requestedPreset->params[id] = value;
    if (presetRenderer) presetRenderer->setParameter(id, value);
    if (secondaryPresetRenderer) secondaryPresetRenderer->setParameter(id, value);
    isDirty = true;
}

std::optional<std::string> Video::takePresetError() {
    auto e = presetError;
    presetError.reset();
    return e;
}

void Video::updatePresetRenderer() {
    if (!presetDirty) return;
    presetDirty = false;
    presetRenderer.reset();
    secondaryPresetRenderer.reset();
    secondaryPresetFailed = false;
    if (!requestedPreset) return;
    try {
        presetRenderer = std::make_unique<PresetChainRenderer>(*requestedPreset);
        presetRenderer->setPreserveTextureBindings(rendersInVideoCallback());
    } catch (const std::exception& e) {
        LOGE("Preset chain failed: %s", e.what());
        presetError = e.what();
        requestedPreset.reset();
        presetDirty = true;
    }
}

// A GL core draws inside its video callback and hands back whatever state it left: flycast leaves GL_ARRAY_BUFFER
// bound, which turns the client-side vertex pointers of these passes into offsets into its buffer (nothing shows).
// While alive (and active), this neutralises that state and then restores it, so the core resumes with exactly the
// state it left (GL cores cache their state).
class CoreGlStateGuard {
public:
    explicit CoreGlStateGuard(bool active) : active(active) {
        if (!active) return;
        scissor = glIsEnabled(GL_SCISSOR_TEST);
        blend = glIsEnabled(GL_BLEND);
        stencil = glIsEnabled(GL_STENCIL_TEST);
        cull = glIsEnabled(GL_CULL_FACE);
        depth = glIsEnabled(GL_DEPTH_TEST);
        glGetIntegerv(GL_ARRAY_BUFFER_BINDING, &arrayBuffer);
        glGetBooleanv(GL_COLOR_WRITEMASK, colorMask);
        // Only a GL core (ES3 framebuffer renderer) can leave a vertex array object bound; a bound VAO would reject
        // the client-side attribute arrays (and the draw would clobber the core's VAO).
        glGetIntegerv(GL_VERTEX_ARRAY_BINDING, &vertexArray);

        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_BLEND);
        glDisable(GL_STENCIL_TEST);
        glDisable(GL_CULL_FACE);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
        glBindVertexArray(0);
    }

    ~CoreGlStateGuard() {
        if (!active) return;
        // Re-bind the core's objects only while they still exist: binding a deleted vertex array raises
        // GL_INVALID_OPERATION (and a deleted buffer name would be silently re-created as a fresh, empty buffer).
        auto savedVertexArray = static_cast<GLuint>(vertexArray);
        auto savedArrayBuffer = static_cast<GLuint>(arrayBuffer);
        glBindVertexArray(savedVertexArray != 0 && glIsVertexArray(savedVertexArray) ? savedVertexArray : 0);
        glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        glBindBuffer(GL_ARRAY_BUFFER, savedArrayBuffer != 0 && glIsBuffer(savedArrayBuffer) ? savedArrayBuffer : 0);
        if (scissor) glEnable(GL_SCISSOR_TEST);
        if (blend) glEnable(GL_BLEND);
        if (stencil) glEnable(GL_STENCIL_TEST);
        if (cull) glEnable(GL_CULL_FACE);
        if (depth) glEnable(GL_DEPTH_TEST);  // the draws disable it
    }

    CoreGlStateGuard(const CoreGlStateGuard&) = delete;
    CoreGlStateGuard& operator=(const CoreGlStateGuard&) = delete;

private:
    bool active;
    GLboolean scissor = GL_FALSE, blend = GL_FALSE, stencil = GL_FALSE, cull = GL_FALSE, depth = GL_FALSE;
    GLint arrayBuffer = 0;
    GLboolean colorMask[4] = { GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE };
    GLint vertexArray = 0;
};

void Video::renderFrame(bool force) {
    // Same for the paused redraw.
    CoreGlStateGuard guard(rendersInVideoCallback());
    drawFrame(force);
}

void Video::drawFrame(bool force) {
    if (skipDuplicateFrames && !isDirty && !force) return;
    isDirty = false;

    glDisable(GL_DEPTH_TEST);

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    // Not placed yet: black, as the surface was, rather than the picture somewhere it is about to leave.
    if (pictureHeld) return;

    // Immersive mode's blurred background samples the whole texture, not the frame's sub-rectangle (frameCoordinates).
    if (immersiveModeEnabled) {
        immersiveMode.renderBackground(
            videoLayout.getScreenWidth(),
            videoLayout.getScreenHeight(),
            videoLayout.getBackgroundVertices(),
            videoLayout.getRelativeForegroundBounds(),
            videoLayout.getFramebufferVertices().data(),
            renderer->getTexture()
        );
    }

    updateProgram();
    updatePresetRenderer();
    // Set per draw, not only when a renderer (re)creates its texture: a paused frame and a GL core's
    // framebuffer keep their texture, and a sharpness change must still show on them.
    // Bind on unit 0: a GL core may leave another unit active with its own texture bound, and it caches
    // those bindings. Unit 0 is rebound and cleared by the pass loop below anyway.
    GLint filter = linearTexture ? GL_LINEAR : GL_NEAREST;
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, renderer->getTexture());
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
    glBindTexture(GL_TEXTURE_2D, 0);

    if (!videoLayout.getRegionQuads().empty()) {
        renderRegions(videoLayout, presetRenderer.get());
        frameCount++;
        return;
    }

    if (presetRenderer) {
        presetRenderer->render(
            renderer->getTexture(), (unsigned) getTextureStorageWidth(), (unsigned) getTextureStorageHeight(),
            (unsigned) getTextureWidth(), (unsigned) getTextureHeight(),
            frameCoordinates(videoLayout.getTextureCoordinates()), videoLayout.getForegroundVertices(),
            videoLayout.getFramebufferVertices(), videoLayout.getScreenWidth(), videoLayout.getScreenHeight(),
            linearTexture, frameCount);
        frameCount++;
        return;
    }

    drawBuiltInChain(videoLayout.getForegroundVertices(), frameCoordinates(videoLayout.getTextureCoordinates()),
                     videoLayout.getScreenWidth(), videoLayout.getScreenHeight());
    frameCount++;
}

void Video::renderRegions(VideoLayout& layout, PresetChainRenderer* preset) {
    auto screenW = (unsigned) layout.getScreenWidth();
    auto screenH = (unsigned) layout.getScreenHeight();
    auto texW = (unsigned) getTextureWidth();
    auto texH = (unsigned) getTextureHeight();
    for (const auto& quad : layout.getRegionQuads()) {
        if (!quad.shaded) {
            drawPlain(quad, screenW, screenH);
        } else if (preset) {
            // One chain serves every shaded region in turn: it keeps no frame history, and it resizes its FBOs only
            // when the region's size changes (the frontend gives shaded regions one size). Only pass 0 reads the
            // region's texture coordinates: later passes that sample Original (or the source) use whole-texture
            // coordinates, so they see the whole frame (both DS screens), not just this region.
            auto frameW = (unsigned) std::lround(quad.srcWidth * (float) texW);
            auto frameH = (unsigned) std::lround(quad.srcHeight * (float) texH);
            preset->render(
                renderer->getTexture(), (unsigned) getTextureStorageWidth(), (unsigned) getTextureStorageHeight(),
                frameW, frameH, frameCoordinates(quad.coordinates), quad.vertices, layout.getFramebufferVertices(),
                (int) screenW, (int) screenH, linearTexture, frameCount);
        } else {
            // Built-in shaders: single-pass ones (the app only uses SHADER_DEFAULT) draw each region exactly; the
            // multi-pass upscalers are not region-aware.
            // A downscaled region samples linearly whatever the sharpness; put back after, as drawPlain does.
            bool down = regionDownscales(quad, (float) texW, (float) texH, (float) screenW, (float) screenH);
            GLint filter = (linearTexture || down) ? GL_LINEAR : GL_NEAREST;
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, renderer->getTexture());
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
            glBindTexture(GL_TEXTURE_2D, 0);
            drawBuiltInChain(quad.vertices, frameCoordinates(quad.coordinates), screenW, screenH);
        }
    }
    GLint filter = linearTexture ? GL_LINEAR : GL_NEAREST;
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, renderer->getTexture());
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
    glBindTexture(GL_TEXTURE_2D, 0);
}

void Video::drawBuiltInChain(const std::array<float, 12>& vertices, const std::array<float, 12>& coordinates,
                             unsigned screenWidth, unsigned screenHeight) {
    for (int i = 0; i < shadersChain.size(); ++i) {
        auto shader = shadersChain[i];
        auto passData = renderer->getPassData(i);
        auto isLastPass = i == shadersChain.size() - 1;

        glBindFramebuffer(GL_FRAMEBUFFER, passData.framebuffer.value_or(0));

        glViewport(
            0,
            0,
            passData.width.value_or(screenWidth),
            passData.height.value_or(screenHeight)
        );

        glUseProgram(shader.gProgram);

        const auto& passVertices = isLastPass ? vertices : videoLayout.getFramebufferVertices();
        glVertexAttribPointer(shader.gvPositionHandle, 2, GL_FLOAT, GL_FALSE, 0, passVertices.data());
        glEnableVertexAttribArray(shader.gvPositionHandle);

        glVertexAttribPointer(shader.gvCoordinateHandle, 2, GL_FLOAT, GL_FALSE, 0, coordinates.data());
        glEnableVertexAttribArray(shader.gvCoordinateHandle);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, renderer->getTexture());
        glUniform1i(shader.gTextureHandle, 0);

        if (shader.gPreviousPassTextureHandle != -1 && passData.texture.has_value()) {
            glActiveTexture(GL_TEXTURE0 + 1);
            glBindTexture(GL_TEXTURE_2D, passData.texture.value());
            glUniform1i(shader.gPreviousPassTextureHandle, 1);
        }

        glUniform2f(shader.gTextureSizeHandle, getTextureStorageWidth(), getTextureStorageHeight());

        glUniform1f(shader.gScreenDensityHandle, getScreenDensity((float) screenWidth, (float) screenHeight));

        glDrawArrays(GL_TRIANGLES, 0, 6);

        glDisableVertexAttribArray(shader.gvPositionHandle);
        glDisableVertexAttribArray(shader.gvCoordinateHandle);

        if (shader.gPreviousPassTextureHandle != -1 && passData.texture.has_value()) {
            glActiveTexture(GL_TEXTURE0 + 1);
            glBindTexture(GL_TEXTURE_2D, 0);
        }
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, 0);

        glUseProgram(0);
    }
}

void Video::drawPlain(const RegionQuad& quad, unsigned screenWidth, unsigned screenHeight) {
    if (!plainShader) {
        auto chain = ShaderManager::getShader({ ShaderManager::Type::SHADER_DEFAULT, { { "LINEAR", "0" } } });
        plainShader = createShaderChainEntry(chain.passes.front());
    }
    const auto& s = *plainShader;

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, (GLsizei) screenWidth, (GLsizei) screenHeight);
    glUseProgram(s.gProgram);

    glVertexAttribPointer(s.gvPositionHandle, 2, GL_FLOAT, GL_FALSE, 0, quad.vertices.data());
    glEnableVertexAttribArray(s.gvPositionHandle);
    auto coordinates = frameCoordinates(quad.coordinates);
    glVertexAttribPointer(s.gvCoordinateHandle, 2, GL_FLOAT, GL_FALSE, 0, coordinates.data());
    glEnableVertexAttribArray(s.gvCoordinateHandle);

    // The unshaded (inset) screen samples nearest, or linearly when it is downscaled; the sharpness the other regions
    // use is put back afterwards.
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, renderer->getTexture());
    GLint plainFilter = regionDownscales(quad, getTextureWidth(), getTextureHeight(),
                                         (float) screenWidth, (float) screenHeight) ? GL_LINEAR : GL_NEAREST;
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, plainFilter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, plainFilter);
    glUniform1i(s.gTextureHandle, 0);
    glUniform2f(s.gTextureSizeHandle, getTextureStorageWidth(), getTextureStorageHeight());
    glUniform1f(s.gScreenDensityHandle, getScreenDensity((float) screenWidth, (float) screenHeight));

    // A translucent region (the inset) is blended over what the regions before it drew. Only the blend state is
    // touched, and put back as found: a GL core caches its own.
    bool translucent = quad.alpha < 1.0F;
    GLboolean blend = GL_FALSE;
    GLint blendSrcRgb = GL_ONE, blendDstRgb = GL_ZERO, blendSrcAlpha = GL_ONE, blendDstAlpha = GL_ZERO;
    GLint blendEquationRgb = GL_FUNC_ADD, blendEquationAlpha = GL_FUNC_ADD;
    GLfloat blendColor[4] = { 0.0F, 0.0F, 0.0F, 0.0F };
    if (translucent) {
        blend = glIsEnabled(GL_BLEND);
        glGetIntegerv(GL_BLEND_SRC_RGB, &blendSrcRgb);
        glGetIntegerv(GL_BLEND_DST_RGB, &blendDstRgb);
        glGetIntegerv(GL_BLEND_SRC_ALPHA, &blendSrcAlpha);
        glGetIntegerv(GL_BLEND_DST_ALPHA, &blendDstAlpha);
        glGetFloatv(GL_BLEND_COLOR, blendColor);
        glGetIntegerv(GL_BLEND_EQUATION_RGB, &blendEquationRgb);
        glGetIntegerv(GL_BLEND_EQUATION_ALPHA, &blendEquationAlpha);
        glEnable(GL_BLEND);
        glBlendEquation(GL_FUNC_ADD);
        glBlendColor(0.0F, 0.0F, 0.0F, quad.alpha);
        glBlendFunc(GL_CONSTANT_ALPHA, GL_ONE_MINUS_CONSTANT_ALPHA);
    }

    glDrawArrays(GL_TRIANGLES, 0, 6);

    if (translucent) {
        glBlendColor(blendColor[0], blendColor[1], blendColor[2], blendColor[3]);
        glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
        if (!blend) glDisable(GL_BLEND);
    }

    glDisableVertexAttribArray(s.gvPositionHandle);
    glDisableVertexAttribArray(s.gvCoordinateHandle);
    GLint filter = linearTexture ? GL_LINEAR : GL_NEAREST;
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
    glBindTexture(GL_TEXTURE_2D, 0);
    glUseProgram(0);
}

void Video::updateScreenRegions(std::vector<ScreenRegion> regions) {
    videoLayout.updateScreenRegions(std::move(regions));
    isDirty = true;
}

void Video::updateSecondaryRegions(std::vector<ScreenRegion> regions) {
    // Not isDirty: the main output is unchanged; the second surface is redrawn every frame anyway.
    secondaryLayout.updateScreenRegions(std::move(regions));
}

void Video::updateSecondaryPresetRenderer() {
    if (!presetRenderer || secondaryPresetRenderer || secondaryPresetFailed || !requestedPreset) return;
    try {
        secondaryPresetRenderer = std::make_unique<PresetChainRenderer>(*requestedPreset);
        secondaryPresetRenderer->setPreserveTextureBindings(rendersInVideoCallback());
    } catch (const std::exception& e) {
        // The main chain built from the same source, so this is unexpected; the second surface then draws its shaded
        // regions with the built-in shader. Not reported as presetError: that belongs to the main chain.
        LOGE("Secondary preset chain failed: %s", e.what());
        secondaryPresetFailed = true;
    }
}

void Video::renderSecondary(unsigned width, unsigned height) {
    if (width == 0 || height == 0) return;
    if ((unsigned) secondaryLayout.getScreenWidth() != width || (unsigned) secondaryLayout.getScreenHeight() != height) {
        secondaryLayout.updateScreenSize(width, height);
    }

    CoreGlStateGuard guard(rendersInVideoCallback());

    glDisable(GL_DEPTH_TEST);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, (GLsizei) width, (GLsizei) height);
    glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    // Nothing to sample before the core's first frame, nor before the main draw built the built-in shader.
    if (secondaryLayout.getRegionQuads().empty() || shadersChain.empty()) return;
    if (getTextureWidth() <= 0.0F || getTextureHeight() <= 0.0F) return;

    updateSecondaryPresetRenderer();
    renderRegions(secondaryLayout, secondaryPresetRenderer.get());
}

void Video::renderPausedFrame() {
    renderFrame(true);
}

float Video::getScreenDensity(float screenWidth, float screenHeight) {
    return std::min(screenWidth / getTextureWidth(), screenHeight / getTextureHeight());
}

float Video::getTextureWidth() {
    return renderer->lastFrameSize.first;
}

float Video::getTextureHeight() {
    return renderer->lastFrameSize.second;
}

float Video::getTextureStorageWidth() {
    return renderer->getTextureSize().first;
}

float Video::getTextureStorageHeight() {
    return renderer->getTextureSize().second;
}

std::array<float, 12> Video::frameCoordinates(const std::array<float, 12>& coordinates) {
    // A GL core draws its frame into the bottom-left width x height texels of a framebuffer sized to the largest
    // frame it has delivered (at least its base geometry, clamped to the GPU limit; not libretro's max_width x
    // max_height), so sampling the whole texture would show the frame shrunk into a corner. Both origins agree: texel row 0 is the frame's first row in memory either way.
    float storageW = getTextureStorageWidth();
    float storageH = getTextureStorageHeight();
    float scaleX = storageW > 0.0F ? std::min(1.0F, getTextureWidth() / storageW) : 1.0F;
    float scaleY = storageH > 0.0F ? std::min(1.0F, getTextureHeight() / storageH) : 1.0F;
    if (scaleX <= 0.0F || scaleY <= 0.0F) return coordinates;  // no frame yet
    std::array<float, 12> result = coordinates;
    for (size_t i = 0; i < result.size(); i += 2) {
        result[i] *= scaleX;
        result[i + 1] *= scaleY;
    }
    return result;
}

void Video::onNewFrame(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (data != nullptr) {
        renderer->onNewFrame(data, width, height, pitch);
        isDirty = true;
    }
}

void Video::setExternalFrame(AHardwareBuffer* buffer, uint64_t id, unsigned width, unsigned height, int syncFd) {
    if (ahbRenderer == nullptr) {
        if (syncFd >= 0) close(syncFd);
        return;
    }
    ahbRenderer->setFrame(buffer, id, width, height, syncFd);
}

void Video::updateScreenSize(unsigned width, unsigned height) {
    videoLayout.updateScreenSize(width, height);
}

void Video::updateViewportSize(Rect viewportRect) {
    videoLayout.updateViewportSize(viewportRect);
}

void Video::updateViewportAlignment(unsigned int viewportAlignment) {
    videoLayout.updateViewportAlignment(viewportAlignment);
}

void Video::updateRendererSize(unsigned int width, unsigned int height) {
    LOGD("Updating renderer size: %d x %d", width, height);
    renderer->updateRenderedResolution(width, height);
    videoLayout.updateContentSize(width, height);
}

void Video::updateScaleMode(unsigned int scaleMode) {
    videoLayout.updateScaleMode(scaleMode);
}

void Video::setPictureHeld(bool held) {
    if (pictureHeld == held) return;
    pictureHeld = held;
    isDirty = true;   // released while paused or with duplicate frames skipped: draw the picture now
}

void Video::updateScreenOffset(float x, float y) {
    videoLayout.updateScreenOffset(x, y);
}

void Video::updateContentSize(unsigned width, unsigned height) {
    videoLayout.updateContentSize(width, height);
}

void Video::updateRotation(float rotation) {
    videoLayout.updateRotation(rotation);
}

Video::Video(
    RenderingOptions renderingOptions,
    ShaderManager::Config shaderConfig,
    bool bottomLeftOrigin,
    float rotation,
    bool skipDuplicateFrames,
    bool immersiveModeEnabled,
    Rect viewportRect,
    ImmersiveMode::Config immersiveModeConfig,
    unsigned int viewportAlignment
) :
    requestedShaderConfig(std::move(shaderConfig)),
    skipDuplicateFrames(skipDuplicateFrames),
    immersiveModeEnabled(immersiveModeEnabled),
    immersiveMode(immersiveModeConfig),
    videoLayout(bottomLeftOrigin, rotation, viewportRect, viewportAlignment),
    secondaryLayout(bottomLeftOrigin, 0.0F, Rect(0.0F, 0.0F, 1.0F, 1.0F), V_ALIGN_CENTER) {

    glContext = eglGetCurrentContext();

    printGLString("Version", GL_VERSION);
    printGLString("Vendor", GL_VENDOR);
    printGLString("Renderer", GL_RENDERER);
    printGLString("Extensions", GL_EXTENSIONS);
    gldebug::install();

    LOGI("Initializing graphics");

    glViewport(0, 0, videoLayout.getScreenWidth(), videoLayout.getScreenHeight());

    glUseProgram(0);

    initializeRenderer(renderingOptions);
}

Video::~Video() {
    if (glContext != EGL_NO_CONTEXT && eglGetCurrentContext() == glContext) {
        for (const auto& entry : shadersChain) glDeleteProgram(entry.gProgram);
        if (plainShader) glDeleteProgram(plainShader->gProgram);
        immersiveMode.releaseGlObjects();
        if (renderer) renderer->releaseGlObjects();
    } else {
        if (presetRenderer) presetRenderer->abandonGlObjects();
        if (secondaryPresetRenderer) secondaryPresetRenderer->abandonGlObjects();
    }
    // Destroyed after the checks above: a chain deletes its objects in its destructor unless abandoned.
    presetRenderer.reset();
    secondaryPresetRenderer.reset();
}

void Video::updateShaderType(ShaderManager::Config shaderConfig) {
    requestedShaderConfig = std::move(shaderConfig);
}

void Video::initializeRenderer(RenderingOptions renderingOptions) {
    auto shaders = ShaderManager::getShader(requestedShaderConfig);

    if (renderingOptions.vulkanBridge) {
        auto ahb = std::make_unique<AhbRenderer>(std::move(shaders));
        ahbRenderer = ahb.get();
        renderer = std::move(ahb);
    } else if (renderingOptions.hardwareAccelerated) {
        renderer = std::make_unique<FramebufferRenderer>(
            renderingOptions.width,
            renderingOptions.height,
            renderingOptions.useDepth,
            renderingOptions.useStencil,
            std::move(shaders)
        );
    } else {
        if (renderingOptions.openglESVersion >= 3) {
            renderer = std::make_unique<ImageRendererES3>();
        } else {
            renderer = std::make_unique<ImageRendererES2>();
        }
    }

    // Preset chains render through ES3 framebuffers.
    presetSupported = renderingOptions.openglESVersion >= 3;

    renderer->setPixelFormat(renderingOptions.pixelFormat);
    updateProgram();
}

void Video::updateAspectRatio(float aspectRatio) {
    videoLayout.updateAspectRatio(aspectRatio);
}

} //namespace libretrodroid
