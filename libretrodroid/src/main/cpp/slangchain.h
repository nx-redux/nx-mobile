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

#ifndef LIBRETRODROID_SLANGCHAIN_H
#define LIBRETRODROID_SLANGCHAIN_H

#include <GLES3/gl3.h>
#include <array>
#include <cstdint>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

#include "presetchain.h"
#include "slangsemantics.h"

namespace libretrodroid {

// The GL internal format for a slang framebuffer format name, GL_RGBA8 for one GLES cannot render to.
GLenum slangFramebufferFormat(const std::string& name);

// A compiled RetroArch slang chain. Construct on the GL thread; throws std::runtime_error with
// "pass N: <first line of the info log>" when a pass fails to compile or link.
class SlangChainRenderer : public ChainRenderer {
public:
    // A slang pass binds its textures below 16, and each pass uses the units from 0 again.
    static constexpr unsigned MAX_TEXTURE_UNITS = 16;

    explicit SlangChainRenderer(const PresetChain& chain);
    ~SlangChainRenderer() override;

    SlangChainRenderer(const SlangChainRenderer&) = delete;
    SlangChainRenderer& operator=(const SlangChainRenderer&) = delete;

    void setParameter(const std::string& id, float value) override;
    void setPreserveTextureBindings(bool preserve) override { preserveBindings = preserve; }
    unsigned textureUnits() const override { return unitsUsed; }
    void abandonGlObjects() override;

    // Each slot keeps its own frames (history, feedback, pass outputs), sized for the (frame, viewport) it was last
    // drawn with: two DS screens are two pictures with two pasts.
    void render(GLuint source, unsigned srcW, unsigned srcH, unsigned frameW, unsigned frameH,
                const std::array<float, 12>& sourceCoords,
                const std::array<float, 12>& foreground, const std::array<float, 12>& screenQuad,
                int screenW, int screenH, bool defaultLinear, unsigned frameCount,
                bool bottomLeftOrigin, unsigned slot) override;

private:
    enum class ValueKind { Zero, Mvp, OutputSize, FinalViewportSize, FrameCount, TextureSize, Parameter, Constant, Aspect };

    // What a uniform block or push member is filled with each frame.
    struct Value {
        ValueKind kind = ValueKind::Zero;
        SlangTextureRef texture;         // TextureSize
        const float* parameter = nullptr; // Parameter: into params (stable: unordered_map never moves its elements)
        float constant = 0.0F;           // Constant
    };

    struct BlockMember {
        Value value;
        unsigned offset;
        char base;
        unsigned components;
    };

    struct PushMember {
        Value value;
        GLint location;
        char base;
        unsigned vecSize, columns;
    };

    struct Stage {
        std::vector<BlockMember> block;
        std::vector<PushMember> push;
        GLuint blockIndex = GL_INVALID_INDEX;
        GLuint buffer = 0;
        std::vector<uint8_t> data;       // the block as the shader reads it
        std::vector<uint8_t> uploaded;   // what the buffer holds, to skip an unchanged upload
    };

    struct Sampler {
        SlangTextureRef texture;
        GLint location;
        unsigned unit;
    };

    struct Pass {
        SlangPass cfg;
        GLuint program = 0;
        Stage stages[2];                 // vertex, fragment
        std::vector<Sampler> samplers;
        bool samplersSet = false;
        GLenum format = GL_RGBA8;
        bool integerFormat = false;
        bool feedback = false;           // a pass reads this one's last frame
        bool mipmapOutput = false;       // the next pass wants its input mipmapped
        bool toScreen = false;           // drawn straight onto framebuffer 0
    };

    struct Target {
        GLuint framebuffer = 0, texture = 0;
        unsigned width = 0, height = 0;
    };

    struct Slot {
        std::vector<Target> history;     // ring of the last frames; history[head] is this frame's Original
        unsigned head = 0;
        std::vector<Target> outputs;     // per pass; empty target for a pass drawn to the screen
        std::vector<Target> feedback;    // per pass; only for passes with feedback
        std::vector<std::array<unsigned, 2>> sizes;   // per pass output size
        std::optional<std::array<unsigned, 4>> sizedFor;
    };

    struct Lut {
        GLuint texture = 0;
        unsigned width = 0, height = 0;
        bool linear = true, mipmap = false;
        int wrap = 0;
    };

    // What the uniforms of one pass draw are computed from.
    struct Frame {
        const Slot* slot;
        size_t pass;
        const float* mvp;
        unsigned frameW, frameH, viewportW, viewportH, frameCount;
    };

    Value valueFor(const std::string& name, size_t pass, const std::vector<std::string>& aliases,
                   const std::vector<std::string>& lutIds);
    void noteTexture(const SlangTextureRef& ref);
    std::array<unsigned, 2> sizeOf(const SlangTextureRef& ref, const Frame& frame) const;
    GLuint textureOf(const SlangTextureRef& ref, const Frame& frame) const;
    GLuint samplerOf(const SlangTextureRef& ref, size_t pass, bool defaultLinear);
    GLuint samplerObject(bool linear, bool mipmap, int wrap);
    unsigned components(const Value& value, const Frame& frame, float* out) const;
    void fillUniforms(Pass& pass, const Frame& frame);

    Target createTarget(unsigned width, unsigned height, GLenum format, bool mipmap) const;
    static void destroyTarget(Target& target);
    void resize(Slot& slot, unsigned frameW, unsigned frameH, unsigned viewportW, unsigned viewportH);
    void releaseSlot(Slot& slot);
    void release();
    void copy(GLuint texture, GLuint sampler, const std::array<float, 12>& positions, const std::array<float, 12>& coords);

    std::vector<Pass> passes;
    std::vector<Lut> luts;
    std::unordered_map<std::string, float> params;
    std::vector<Slot> slots;

    GLuint copyProgram = 0;
    GLint copyTexture = -1;
    std::array<GLuint, 16> samplerObjects{};   // by linear, mipmap, wrap

    unsigned historyDepth = 0;           // the oldest OriginalHistoryN a pass reads
    unsigned unitsUsed = 1;
    unsigned maxTextureSize = 4096;
    bool borderClamp = false;            // GLES 3.2 (or the extension): clamp_to_border exists
    bool preserveBindings = true;
    // Reused by render() so a frame allocates nothing.
    std::vector<GLint> savedBindings;
};

}

#endif //LIBRETRODROID_SLANGCHAIN_H
