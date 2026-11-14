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

#ifndef LIBRETRODROID_PRESETCHAIN_H
#define LIBRETRODROID_PRESETCHAIN_H

#include <GLES3/gl3.h>
#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include "slangtranslate.h"

namespace libretrodroid {

struct PresetPass {
    std::string source;              // pragma lines already stripped
    std::optional<bool> filterLinear;
    int wrap;                        // 0 edge, 1 repeat, 2 mirrored
    int scaleTypeX, scaleTypeY;      // 0 none, 1 source, 2 viewport, 3 absolute
    float scaleX, scaleY;
    unsigned frameCountMod;
    std::string alias;

    // Written out: defaulted comparisons need C++20 and the fork builds as C++17.
    bool operator==(const PresetPass& o) const {
        return source == o.source && filterLinear == o.filterLinear && wrap == o.wrap &&
            scaleTypeX == o.scaleTypeX && scaleTypeY == o.scaleTypeY && scaleX == o.scaleX &&
            scaleY == o.scaleY && frameCountMod == o.frameCountMod && alias == o.alias;
    }
    bool operator!=(const PresetPass& o) const { return !(*this == o); }
};

struct PresetLut {
    std::string id;
    unsigned width, height;
    std::vector<uint8_t> rgba;
    bool linear;
    int wrap;                        // as PresetPass, plus 3 border (slang only)
    bool mipmap = false;             // slang only

    bool operator==(const PresetLut& o) const {
        return id == o.id && width == o.width && height == o.height && rgba == o.rgba &&
            linear == o.linear && wrap == o.wrap && mipmap == o.mipmap;
    }
    bool operator!=(const PresetLut& o) const { return !(*this == o); }
};

// One pass of a RetroArch slang preset, already translated to GLSL ES (slangtranslate.h).
struct SlangPass {
    SlangProgram program;
    std::optional<bool> filterLinear;
    int wrap;                        // 0 edge, 1 repeat, 2 mirrored, 3 border
    int scaleTypeX, scaleTypeY;      // 0 none, 1 source, 2 viewport, 3 absolute
    float scaleX, scaleY;
    unsigned frameCountMod;
    std::string alias;
    std::string format;              // the framebuffer format by its slang name, e.g. R8G8B8A8_UNORM
    bool mipmapInput;

    bool operator==(const SlangPass& o) const {
        return program == o.program && filterLinear == o.filterLinear && wrap == o.wrap &&
            scaleTypeX == o.scaleTypeX && scaleTypeY == o.scaleTypeY && scaleX == o.scaleX && scaleY == o.scaleY &&
            frameCountMod == o.frameCountMod && alias == o.alias && format == o.format && mipmapInput == o.mipmapInput;
    }
    bool operator!=(const SlangPass& o) const { return !(*this == o); }
};

// A preset of either format: legacy GLSL in passes, or slang in slangPasses (then passes is empty).
struct PresetChain {
    std::vector<PresetPass> passes;
    std::vector<SlangPass> slangPasses;
    std::vector<PresetLut> luts;
    std::unordered_map<std::string, float> params;

    bool isSlang() const { return !slangPasses.empty(); }

    // Used by Video to skip a rebuild.
    bool operator==(const PresetChain& o) const {
        return passes == o.passes && slangPasses == o.slangPasses && luts == o.luts && params == o.params;
    }
    bool operator!=(const PresetChain& o) const { return !(*this == o); }
};

// What Video draws a preset with, whichever its format.
class ChainRenderer {
public:
    virtual ~ChainRenderer() = default;

    virtual void setParameter(const std::string& id, float value) = 0;

    // Whether render() saves and restores the GL bindings it uses: needed only while a GL core owns them (it caches
    // them). Default on.
    virtual void setPreserveTextureBindings(bool preserve) = 0;

    // The texture units render() samples, from unit 0.
    virtual unsigned textureUnits() const = 0;

    // Forgets the GL names without deleting them: their context is gone, and deleting them would hit whatever
    // reuses those names in the current one.
    virtual void abandonGlObjects() = 0;

    // Draws the chain: source is the core frame texture (srcW x srcH texels); the part being drawn is frameW x frameH of
    // it at sourceCoords (the whole texture without regions, one DS screen with them), its rows bottom-up when
    // bottomLeftOrigin. The last pass lands on framebuffer 0 inside the foreground rect (clip-space vertices, 12
    // floats). defaultLinear is the Screen Sharpness filter used where a pass leaves filter_linear unspecified. One
    // instance may be run for several regions per frame: slot says which, for a chain that keeps frames between draws.
    virtual void render(GLuint source, unsigned srcW, unsigned srcH, unsigned frameW, unsigned frameH,
                        const std::array<float, 12>& sourceCoords,
                        const std::array<float, 12>& foreground, const std::array<float, 12>& screenQuad,
                        int screenW, int screenH, bool defaultLinear, unsigned frameCount,
                        bool bottomLeftOrigin, unsigned slot) = 0;
};

// The renderer for chain, built on the GL thread: throws std::runtime_error with "pass N: <reason>" when a pass
// fails to compile or link.
std::unique_ptr<ChainRenderer> makeChainRenderer(const PresetChain& chain);

// An upper bound of textureUnits() for the renderer chain would build.
unsigned chainTextureUnits(const PresetChain& chain);

// The bounding box of a clip-space quad (12 floats) in window pixels: x, y from the bottom-left, w, h.
std::array<int, 4> foregroundRect(const std::array<float, 12>& vertices, int screenW, int screenH);

// A compiled RetroArch legacy-GLSL chain. Construct on the GL thread; throws std::runtime_error with
// "pass N: <first line of the info log>" when a pass fails to compile or link.
class PresetChainRenderer : public ChainRenderer {
public:
    explicit PresetChainRenderer(const PresetChain& chain);
    ~PresetChainRenderer() override;

    PresetChainRenderer(const PresetChainRenderer&) = delete;
    PresetChainRenderer& operator=(const PresetChainRenderer&) = delete;

    void setParameter(const std::string& id, float value) override;

    // Whether render() saves and restores the texture bound on each unit it uses: needed only while a GL core owns
    // those bindings (it caches them). Default on.
    void setPreserveTextureBindings(bool preserve) override { preserveTextureBindings = preserve; }

    // The texture units render() samples, from unit 0: Texture, Orig, the LUTs, then each pass's output.
    unsigned textureUnits() const override { return 2 + (unsigned) lutTextures.size() + (unsigned) passes.size(); }

    // Forgets the GL names without deleting them: their context is gone, and deleting them would hit whatever
    // reuses those names in the current one.
    void abandonGlObjects() override;

    // Draws the chain: source is the core frame texture (srcW x srcH texels); the part being drawn is frameW x frameH of
    // it at sourceCoords (the whole texture without regions, one DS screen with them). The last pass lands on framebuffer
    // 0 inside the foreground rect (clip-space vertices, 12 floats). Pass 0's TextureSize and Orig's texture size are the
    // texture's; InputSize, OrigInputSize and "source" scaling use the frame size. defaultLinear is the Screen Sharpness
    // filter used where a pass leaves filter_linear unspecified and for the final blit of a scaled last pass. One
    // instance may be run for several regions per frame: it keeps no frame history, and its FBOs follow the last
    // (frame size, viewport size) it was run with. bottomLeftOrigin and slot are not needed: the picture's row order
    // comes with the coordinates, and nothing is kept between draws.
    void render(GLuint source, unsigned srcW, unsigned srcH, unsigned frameW, unsigned frameH,
                const std::array<float, 12>& sourceCoords,
                const std::array<float, 12>& foreground, const std::array<float, 12>& screenQuad,
                int screenW, int screenH, bool defaultLinear, unsigned frameCount,
                bool bottomLeftOrigin, unsigned slot) override;

    // Pure helpers, exposed for review: version rewrite and pass sizing.
    static std::string prefixSource(const std::string& source, bool vertex, const std::vector<std::string>& aliases);
    static unsigned passSize(int scaleType, float scale, unsigned previous, unsigned viewport, unsigned maxSize);

private:
    // A reference from a pass to an earlier pass's output (Pass{k}, PassPrev{k} or an alias).
    struct OutputRef {
        unsigned pass;
        GLint texture, textureSize, inputSize;
    };

    struct Pass {
        PresetPass cfg;
        GLuint program = 0;

        GLint vertexCoord = -1, color = -1, lutTexCoord = -1;
        // TexCoord and every per-frame texcoord attribute: all fed the pass's input coordinates.
        std::vector<GLint> texCoords;

        GLint mvpMatrix = -1, texture = -1, textureSize = -1, inputSize = -1, outputSize = -1;
        GLint frameCount = -1, frameDirection = -1;
        GLint origTexture = -1, origTextureSize = -1, origInputSize = -1;
        std::vector<OutputRef> outputs;
        std::vector<GLint> luts;                          // sampler per chain LUT, in order
        // Each parameter's value in PresetChainRenderer::params (stable: unordered_map never moves its elements).
        std::vector<std::pair<const float*, GLint>> params;

        bool toScreen = false;                            // last pass without a scale: no FBO
        GLuint fbo = 0, fboTexture = 0;
        unsigned width = 0, height = 0;
    };

    struct Blit {
        GLuint program = 0;
        GLint vertexCoord = -1, texCoord = -1, texture = -1;
    };

    void resize(unsigned srcW, unsigned srcH, unsigned viewportW, unsigned viewportH);
    void release();

    std::vector<Pass> passes;
    std::vector<GLuint> lutTextures;
    std::unordered_map<std::string, float> params;
    std::optional<Blit> blit;

    unsigned maxTextureSize = 4096;
    // Inputs the FBOs were last sized for; any change rebuilds them.
    std::optional<std::array<unsigned, 4>> sizedFor;

    bool preserveTextureBindings = true;
    // Reused by render() so a frame allocates nothing.
    std::vector<GLint> savedBindings;
};

}

#endif //LIBRETRODROID_PRESETCHAIN_H
