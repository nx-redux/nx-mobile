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

#ifndef LIBRETRODROID_VIDEO_H
#define LIBRETRODROID_VIDEO_H

#include <GLES2/gl2.h>
#include <EGL/egl.h>
#include <android/hardware_buffer.h>
#include <optional>
#include <array>
#include <memory>
#include <string>
#include <vector>

#include "renderers/renderer.h"
#include "shadermanager.h"
#include "utils/rect.h"
#include "immersivemode.h"
#include "videolayout.h"
#include "presetchain.h"

namespace libretrodroid {

class Video {
public:

    struct RenderingOptions {
        bool hardwareAccelerated = false;
        unsigned int width;
        unsigned int height;
        bool useDepth;
        bool useStencil;
        int openglESVersion;
        int pixelFormat;
        bool vulkanBridge = false;   // frames arrive as AHardwareBuffers (AhbRenderer)
    };

    struct ShaderChainEntry {
        GLint gProgram = 0;
        GLint gvPositionHandle = 0;
        GLint gvCoordinateHandle = 0;
        GLint gTextureHandle = 0;
        GLint gPreviousPassTextureHandle = 0;
        GLint gScreenDensityHandle = 0;
        GLint gTextureSizeHandle = 0;
    };

    Video(
        RenderingOptions renderingOptions,
        ShaderManager::Config shaderConfig,
        bool bottomLeftOrigin,
        float rotation,
        bool skipDuplicateFrames,
        bool immersiveMode,
        Rect viewportRect,
        ImmersiveMode::Config immersiveModeConfig,
        unsigned int viewportAlignment
    );

    // Deletes the GL objects when the context that made them is current; otherwise (no context, or a new one after
    // the old was lost) only the CPU side is freed: the names died with their context or now belong to another's.
    ~Video();
    Video(const Video&) = delete;
    Video& operator=(const Video&) = delete;

    VideoLayout& getLayout() { return videoLayout; }

    void updateAspectRatio(float aspectRatio);
    void updateScreenSize(unsigned screenWidth, unsigned screenHeight);
    void updateViewportSize(Rect viewportRect);
    void updateViewportAlignment(unsigned int viewportAlignment);
    void updateRendererSize(unsigned width, unsigned height);
    void updateRotation(float rotation);
    void updateShaderType(ShaderManager::Config shaderConfig);
    void updateScaleMode(unsigned int scaleMode);
    void updateScreenOffset(float x, float y);
    // Held: frames clear to black and draw no picture (the frontend hasn't placed it yet). Release redraws.
    void setPictureHeld(bool held);
    void updateContentSize(unsigned width, unsigned height);
    void updateScreenRegions(std::vector<ScreenRegion> regions);
    // Regions drawn on a second surface by renderSecondary; dst is 0..1 of that whole surface. Doesn't redraw the main
    // output.
    void updateSecondaryRegions(std::vector<ScreenRegion> regions);

    // Draws the last frame's secondary regions into the bound default framebuffer, width x height, shielding a GL core's
    // state as renderFrame does. Never runs the core nor touches the main output's state (dirty flag, frame count,
    // layout, preset FBOs). Black when there are no secondary regions or no frame yet.
    void renderSecondary(unsigned width, unsigned height);

    // force: draw even when skipDuplicateFrames would skip an unchanged frame.
    void renderFrame(bool force = false);

    // Redraws the last frame while the core is paused, shielding the core's GL state it doesn't expect changed.
    void renderPausedFrame();

    void onNewFrame(const void *data, unsigned width, unsigned height, size_t pitch);

    // The next frame's buffer, for a Video created with vulkanBridge. Owns [syncFd].
    void setExternalFrame(AHardwareBuffer* buffer, uint64_t id, unsigned width, unsigned height, int syncFd);

    uintptr_t getCurrentFramebuffer() {
        return renderer->getFramebuffer();
    };

    bool rendersInVideoCallback() {
        return renderer->rendersInVideoCallback();
    }

    // A RetroArch preset chain drawn instead of the built-in shader; nullopt returns to it.
    void setPresetChain(std::optional<PresetChain> chain);
    void setPresetParameter(const std::string& id, float value);
    // The last chain build error, cleared on read. Empty when the chain built.
    std::optional<std::string> takePresetError();
    // Builds the requested chain now (on the GL thread) so a caller can read takePresetError right after.
    void updatePresetRenderer();

private:
    void drawFrame(bool force);
    void updateProgram();

    float getScreenDensity(float screenWidth, float screenHeight);
    // The last frame's size: the part of the texture the core drew.
    float getTextureWidth();
    float getTextureHeight();
    // The texture's own size in texels (larger than the frame for a GL core rendering a smaller frame).
    float getTextureStorageWidth();
    float getTextureStorageHeight();
    // coordinates (0..1 of the frame) mapped onto the frame's corner of the texture.
    std::array<float, 12> frameCoordinates(const std::array<float, 12>& coordinates);

    void initializeRenderer(RenderingOptions renderingOptions);

    // Compiles one shader pass and looks up the handles the built-in draw loops use. Throws when the program fails.
    static ShaderChainEntry createShaderChainEntry(const ShaderManager::Pass& pass);

    // layout's region quads on framebuffer 0 at layout's screen size; preset (may be null) draws the shaded ones.
    void renderRegions(VideoLayout& layout, PresetChainRenderer* preset);
    void drawBuiltInChain(const std::array<float, 12>& vertices, const std::array<float, 12>& coordinates,
                          unsigned screenWidth, unsigned screenHeight);
    void drawPlain(const RegionQuad& quad, unsigned screenWidth, unsigned screenHeight);
    // Builds secondaryPresetRenderer from the requested chain when the main one is live and it isn't yet.
    void updateSecondaryPresetRenderer();

private:
    ShaderManager::Config requestedShaderConfig = ShaderManager::Config {
        ShaderManager::Type::SHADER_DEFAULT
    };
    std::optional<ShaderManager::Config> loadedShaderType = std::nullopt;

    bool isDirty = false;
    bool pictureHeld = false;
    bool skipDuplicateFrames = false;
    bool linearTexture = true;

    std::vector<ShaderChainEntry> shadersChain;

    bool presetSupported = true;
    std::optional<PresetChain> requestedPreset;
    bool presetDirty = false;
    std::unique_ptr<PresetChainRenderer> presetRenderer;
    std::optional<std::string> presetError;
    unsigned frameCount = 0;
    // The nearest default shader for unshaded regions, built on first use.
    std::optional<ShaderChainEntry> plainShader;

    bool immersiveModeEnabled = false;
    ImmersiveMode immersiveMode;
    VideoLayout videoLayout;

    // The second surface: its own layout (viewport = the whole surface) and its own preset chain instance, so its
    // size never resizes the main chain's FBOs. The chain is built on the first secondary draw that needs it, not
    // with the main one: no second compile while there is no second surface.
    VideoLayout secondaryLayout;
    std::unique_ptr<PresetChainRenderer> secondaryPresetRenderer;
    bool secondaryPresetFailed = false;   // don't retry a failed build every frame; cleared with the main chain

    std::unique_ptr<Renderer> renderer;
    class AhbRenderer* ahbRenderer = nullptr;   // renderer, when it is one
    // The context current when this Video was made: its GL objects live there.
    EGLContext glContext = EGL_NO_CONTEXT;
};

}

#endif //LIBRETRODROID_VIDEO_H
