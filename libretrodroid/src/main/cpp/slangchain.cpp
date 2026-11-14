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

#include "slangchain.h"

#include <GLES3/gl32.h>
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <stdexcept>

#include "log.h"

namespace libretrodroid {

namespace {

// A quad over the unit square in the fork's vertex order. A slang pass gets it as Position and as TexCoord: (0, 0)
// is the picture's top-left in every texture the chain owns.
const std::array<float, 12> unitQuad = {
    0.0F, 0.0F,
    0.0F, 1.0F,
    1.0F, 0.0F,
    1.0F, 0.0F,
    0.0F, 1.0F,
    1.0F, 1.0F,
};

const std::array<float, 12> clipQuad = {
    -1.0F, -1.0F,
    -1.0F, 1.0F,
    1.0F, -1.0F,
    1.0F, -1.0F,
    -1.0F, 1.0F,
    1.0F, 1.0F,
};

// Position (0..1) onto the whole target: texture row 0 is the picture's top row.
const float targetMatrix[16] = {
    2.0F, 0.0F, 0.0F, 0.0F,
    0.0F, 2.0F, 0.0F, 0.0F,
    0.0F, 0.0F, 1.0F, 0.0F,
    -1.0F, -1.0F, 0.0F, 1.0F,
};

const char* copyVertex =
    "#version 300 es\n"
    "layout(location = 0) in vec2 position;\n"
    "layout(location = 1) in vec2 coord;\n"
    "out vec2 uv;\n"
    "void main() {\n"
    "  uv = coord;\n"
    "  gl_Position = vec4(position, 0.0, 1.0);\n"
    "}\n";

// Alpha forced to 1: a core's frame may carry anything there.
const char* copyFragment =
    "#version 300 es\n"
    "precision highp float;\n"
    "uniform sampler2D source;\n"
    "in vec2 uv;\n"
    "out vec4 color;\n"
    "void main() {\n"
    "  color = vec4(texture(source, uv).rgb, 1.0);\n"
    "}\n";

std::string firstLine(const std::string& log, const char* fallback) {
    auto start = log.find_first_not_of(" \t\r\n");
    if (start == std::string::npos) return fallback;
    auto end = log.find_first_of("\r\n", start);
    return log.substr(start, end == std::string::npos ? std::string::npos : end - start);
}

GLuint compileStage(GLenum type, const std::string& source, std::string& error) {
    GLuint shader = glCreateShader(type);
    const char* text = source.c_str();
    glShaderSource(shader, 1, &text, nullptr);
    glCompileShader(shader);
    GLint compiled = GL_FALSE;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (compiled == GL_TRUE) return shader;

    GLint length = 0;
    glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &length);
    std::string log(std::max(length, 1), '\0');
    glGetShaderInfoLog(shader, length, nullptr, log.data());
    LOGE("Slang shader compile failed:\n%s", log.c_str());
    error = firstLine(log, type == GL_VERTEX_SHADER ? "vertex shader failed to compile" : "fragment shader failed to compile");
    glDeleteShader(shader);
    return 0;
}

// Attributes 0 and 1 are bound to names (empty to keep the shader's own locations). Returns 0 and sets error on failure.
GLuint linkProgram(const std::string& vertex, const std::string& fragment, const char* attribute0, const char* attribute1,
                   std::string& error) {
    GLuint vs = compileStage(GL_VERTEX_SHADER, vertex, error);
    if (!vs) return 0;
    GLuint fs = compileStage(GL_FRAGMENT_SHADER, fragment, error);
    if (!fs) {
        glDeleteShader(vs);
        return 0;
    }

    GLuint program = glCreateProgram();
    glAttachShader(program, vs);
    glAttachShader(program, fs);
    if (attribute0) glBindAttribLocation(program, 0, attribute0);
    if (attribute1) glBindAttribLocation(program, 1, attribute1);
    glLinkProgram(program);
    // The program keeps them alive while attached.
    glDeleteShader(vs);
    glDeleteShader(fs);

    GLint linked = GL_FALSE;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (linked == GL_TRUE) return program;

    GLint length = 0;
    glGetProgramiv(program, GL_INFO_LOG_LENGTH, &length);
    std::string log(std::max(length, 1), '\0');
    glGetProgramInfoLog(program, length, nullptr, log.data());
    LOGE("Slang program link failed:\n%s", log.c_str());
    error = firstLine(log, "program failed to link");
    glDeleteProgram(program);
    return 0;
}

bool hasExtension(const char* name) {
    auto* extensions = (const char*) glGetString(GL_EXTENSIONS);
    return extensions && std::strstr(extensions, name) != nullptr;
}

// GLES version as major * 100 + minor * 10 (320), 300 when the string cannot be read.
unsigned glesVersion() {
    auto* version = (const char*) glGetString(GL_VERSION);
    int major = 0, minor = 0;
    if (!version || std::sscanf(version, "OpenGL ES %d.%d", &major, &minor) != 2) return 300;
    return (unsigned) (major * 100 + minor * 10);
}

bool isInteger(GLenum format) {
    switch (format) {
        case GL_R8UI: case GL_R8I: case GL_RG8UI: case GL_RG8I: case GL_RGBA8UI: case GL_RGBA8I:
        case GL_R16UI: case GL_R16I: case GL_RG16UI: case GL_RG16I: case GL_RGBA16UI: case GL_RGBA16I:
        case GL_R32UI: case GL_R32I: case GL_RG32UI: case GL_RG32I: case GL_RGBA32UI: case GL_RGBA32I:
        case GL_RGB10_A2UI:
            return true;
        default:
            return false;
    }
}

bool is32Float(GLenum format) {
    return format == GL_R32F || format == GL_RG32F || format == GL_RGBA32F;
}

GLint mipLevels(unsigned width, unsigned height) {
    GLint levels = 1;
    for (unsigned size = std::max(width, height); size > 1; size >>= 1) ++levels;
    return levels;
}

// Unit 0 with tightly packed client-memory unpacking for the LUT uploads; puts back what a GL core left (active
// unit, its unit 0 binding, unpack state) when it goes out of scope, throw or not: GL cores cache that state.
class UploadState {
public:
    UploadState() {
        glGetIntegerv(GL_ACTIVE_TEXTURE, &activeTexture);
        glActiveTexture(GL_TEXTURE0);
        glGetIntegerv(GL_TEXTURE_BINDING_2D, &texture);
        glGetIntegerv(GL_PIXEL_UNPACK_BUFFER_BINDING, &unpackBuffer);
        glGetIntegerv(GL_UNPACK_ALIGNMENT, &alignment);
        glGetIntegerv(GL_UNPACK_ROW_LENGTH, &rowLength);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
    }
    ~UploadState() {
        glPixelStorei(GL_UNPACK_ROW_LENGTH, rowLength);
        glPixelStorei(GL_UNPACK_ALIGNMENT, alignment);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, (GLuint) unpackBuffer);
        glBindTexture(GL_TEXTURE_2D, (GLuint) texture);
        glActiveTexture((GLenum) activeTexture);
    }
    UploadState(const UploadState&) = delete;
    UploadState& operator=(const UploadState&) = delete;

private:
    GLint activeTexture = GL_TEXTURE0, texture = 0, unpackBuffer = 0, alignment = 4, rowLength = 0;
};

// The uniform buffer bindings a chain draw uses (the generic one and points 0 and 1), put back for a GL core.
class UniformBufferState {
public:
    explicit UniformBufferState(bool active) : active(active) {
        if (!active) return;
        glGetIntegerv(GL_UNIFORM_BUFFER_BINDING, &generic);
        for (GLuint point = 0; point < 2; ++point) {
            glGetIntegeri_v(GL_UNIFORM_BUFFER_BINDING, point, &buffer[point]);
            glGetInteger64i_v(GL_UNIFORM_BUFFER_START, point, &start[point]);
            glGetInteger64i_v(GL_UNIFORM_BUFFER_SIZE, point, &size[point]);
        }
    }
    ~UniformBufferState() {
        if (!active) {
            glBindBuffer(GL_UNIFORM_BUFFER, 0);
            return;
        }
        for (GLuint point = 0; point < 2; ++point) {
            // A deleted buffer name would be silently re-created as a fresh, empty buffer.
            auto name = (GLuint) buffer[point];
            if (name != 0 && !glIsBuffer(name)) name = 0;
            if (name != 0 && size[point] > 0) {
                glBindBufferRange(GL_UNIFORM_BUFFER, point, name, (GLintptr) start[point], (GLsizeiptr) size[point]);
            } else {
                glBindBufferBase(GL_UNIFORM_BUFFER, point, name);
            }
        }
        auto name = (GLuint) generic;
        glBindBuffer(GL_UNIFORM_BUFFER, name != 0 && glIsBuffer(name) ? name : 0);
    }
    UniformBufferState(const UniformBufferState&) = delete;
    UniformBufferState& operator=(const UniformBufferState&) = delete;

private:
    bool active;
    GLint generic = 0;
    GLint buffer[2] = { 0, 0 };
    GLint64 start[2] = { 0, 0 }, size[2] = { 0, 0 };
};

void bindQuad(GLuint location, const std::array<float, 12>& data) {
    glVertexAttribPointer(location, 2, GL_FLOAT, GL_FALSE, 0, data.data());
    glEnableVertexAttribArray(location);
}

} // namespace

GLenum slangFramebufferFormat(const std::string& name) {
    static const std::pair<const char*, GLenum> formats[] = {
        { "R8_UNORM", GL_R8 }, { "R8_UINT", GL_R8UI }, { "R8_SINT", GL_R8I },
        { "R8G8_UNORM", GL_RG8 }, { "R8G8_UINT", GL_RG8UI }, { "R8G8_SINT", GL_RG8I },
        { "R8G8B8A8_UNORM", GL_RGBA8 }, { "R8G8B8A8_UINT", GL_RGBA8UI }, { "R8G8B8A8_SINT", GL_RGBA8I },
        { "R8G8B8A8_SRGB", GL_SRGB8_ALPHA8 },
        { "A2B10G10R10_UNORM_PACK32", GL_RGB10_A2 }, { "A2B10G10R10_UINT_PACK32", GL_RGB10_A2UI },
        { "R16_UINT", GL_R16UI }, { "R16_SINT", GL_R16I }, { "R16_SFLOAT", GL_R16F },
        { "R16G16_UINT", GL_RG16UI }, { "R16G16_SINT", GL_RG16I }, { "R16G16_SFLOAT", GL_RG16F },
        { "R16G16B16A16_UINT", GL_RGBA16UI }, { "R16G16B16A16_SINT", GL_RGBA16I }, { "R16G16B16A16_SFLOAT", GL_RGBA16F },
        { "R32_UINT", GL_R32UI }, { "R32_SINT", GL_R32I }, { "R32_SFLOAT", GL_R32F },
        { "R32G32_UINT", GL_RG32UI }, { "R32G32_SINT", GL_RG32I }, { "R32G32_SFLOAT", GL_RG32F },
        { "R32G32B32A32_UINT", GL_RGBA32UI }, { "R32G32B32A32_SINT", GL_RGBA32I }, { "R32G32B32A32_SFLOAT", GL_RGBA32F },
    };
    for (auto& format : formats) {
        if (name == format.first) return format.second;
    }
    return GL_RGBA8;
}

SlangChainRenderer::SlangChainRenderer(const PresetChain& chain) : params(chain.params) {
    try {
        GLint maxSize = 0;
        glGetIntegerv(GL_MAX_TEXTURE_SIZE, &maxSize);
        if (maxSize > 0) maxTextureSize = (unsigned) maxSize;
        borderClamp = glesVersion() >= 320 || hasExtension("GL_EXT_texture_border_clamp") ||
            hasExtension("GL_OES_texture_border_clamp");
        // Without it a 32-bit float texture cannot be sampled linearly: such a pass gets 16-bit floats instead.
        bool floatLinear = hasExtension("GL_OES_texture_float_linear");

        std::vector<std::string> aliases, lutIds;
        for (auto& pass : chain.slangPasses) aliases.push_back(pass.alias);
        for (auto& lut : chain.luts) lutIds.push_back(lut.id);

        for (auto& lut : chain.luts) {
            if (lut.width == 0 || lut.height == 0 || lut.rgba.size() != (size_t) lut.width * lut.height * 4) {
                throw std::runtime_error("lut " + lut.id + ": pixel data does not match its size");
            }
        }
        if (!chain.luts.empty()) {
            UploadState state;
            for (auto& lut : chain.luts) {
                Lut entry;
                entry.width = lut.width;
                entry.height = lut.height;
                entry.linear = lut.linear;
                entry.mipmap = lut.mipmap;
                entry.wrap = lut.wrap;
                glGenTextures(1, &entry.texture);
                luts.push_back(entry);
                glBindTexture(GL_TEXTURE_2D, entry.texture);
                glTexStorage2D(GL_TEXTURE_2D, lut.mipmap ? mipLevels(lut.width, lut.height) : 1, GL_RGBA8,
                               (GLsizei) lut.width, (GLsizei) lut.height);
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, (GLsizei) lut.width, (GLsizei) lut.height, GL_RGBA,
                                GL_UNSIGNED_BYTE, lut.rgba.data());
                if (lut.mipmap) glGenerateMipmap(GL_TEXTURE_2D);
            }
        }

        passes.resize(chain.slangPasses.size());
        for (size_t index = 0; index < passes.size(); ++index) {
            Pass& pass = passes[index];
            pass.cfg = chain.slangPasses[index];
            auto& program = pass.cfg.program;

            std::string error;
            pass.program = linkProgram(
                program.vertex.glsl, program.fragment.glsl,
                (std::string(slangnames::ATTRIBUTE_PREFIX) + "0").c_str(),
                (std::string(slangnames::ATTRIBUTE_PREFIX) + "1").c_str(),
                error
            );
            if (!pass.program) throw std::runtime_error("pass " + std::to_string(index) + ": " + error);

            pass.format = slangFramebufferFormat(pass.cfg.format);
            if (is32Float(pass.format) && !floatLinear) {
                pass.format = pass.format == GL_R32F ? GL_R16F : pass.format == GL_RG32F ? GL_RG16F : GL_RGBA16F;
            }
            pass.integerFormat = isInteger(pass.format);

            for (int s = 0; s < 2; ++s) {
                const SlangStage& source = s == 0 ? program.vertex : program.fragment;
                Stage& stage = pass.stages[s];
                const char* blockName = s == 0 ? slangnames::UBO_VERTEX : slangnames::UBO_FRAGMENT;
                std::string pushName = s == 0 ? slangnames::PUSH_VERTEX : slangnames::PUSH_FRAGMENT;

                if (source.uboSize > 0) {
                    stage.blockIndex = glGetUniformBlockIndex(pass.program, blockName);
                    if (stage.blockIndex != GL_INVALID_INDEX) {
                        glUniformBlockBinding(pass.program, stage.blockIndex, (GLuint) s);
                        stage.data.assign(source.uboSize, 0);
                        for (auto& member : source.ubo) {
                            unsigned count = member.vecSize * member.columns;
                            if ((size_t) member.offset + (size_t) count * 4 > stage.data.size()) continue;
                            stage.block.push_back({ valueFor(member.name, index, aliases, lutIds), member.offset, member.base, count });
                        }
                    }
                }
                for (auto& member : source.push) {
                    GLint location = glGetUniformLocation(pass.program, (pushName + "." + member.name).c_str());
                    if (location == -1) continue;   // the stage does not read it
                    stage.push.push_back({ valueFor(member.name, index, aliases, lutIds), location, member.base,
                                           member.vecSize, member.columns });
                }
                for (auto& texture : source.textures) {
                    auto ref = slangTextureFor(texture.name, index, aliases, lutIds);
                    if (!ref) {
                        throw std::runtime_error("pass " + std::to_string(index) + ": unknown texture " + texture.name);
                    }
                    GLint location = glGetUniformLocation(pass.program, texture.name.c_str());
                    if (location == -1) continue;
                    bool listed = std::any_of(pass.samplers.begin(), pass.samplers.end(),
                                              [&](const Sampler& sampler) { return sampler.location == location; });
                    if (listed) continue;
                    noteTexture(*ref);
                    pass.samplers.push_back({ *ref, location, (unsigned) pass.samplers.size() });
                }
            }
            if (pass.samplers.size() > MAX_TEXTURE_UNITS) {
                throw std::runtime_error("pass " + std::to_string(index) + ": samples too many textures");
            }
            unitsUsed = std::max(unitsUsed, (unsigned) pass.samplers.size());
        }

        for (size_t index = 0; index < passes.size(); ++index) {
            Pass& pass = passes[index];
            bool last = index + 1 == passes.size();
            // Integer textures have no mipmaps to generate.
            pass.mipmapOutput = !last && passes[index + 1].cfg.mipmapInput && !pass.integerFormat;
            // A last pass that is kept for the next frame, or drawn at its own size, goes through a texture.
            pass.toScreen = last && pass.cfg.scaleTypeX == 0 && pass.cfg.scaleTypeY == 0 && !pass.feedback;
        }

        std::string error;
        copyProgram = linkProgram(copyVertex, copyFragment, nullptr, nullptr, error);
        if (!copyProgram) throw std::runtime_error("copy: " + error);
        copyTexture = glGetUniformLocation(copyProgram, "source");
    } catch (...) {
        release();
        throw;
    }
}

SlangChainRenderer::~SlangChainRenderer() {
    release();
}

void SlangChainRenderer::noteTexture(const SlangTextureRef& ref) {
    if (ref.kind == SlangTextureKind::History) historyDepth = std::max(historyDepth, ref.index);
    if (ref.kind == SlangTextureKind::Feedback && ref.index < passes.size()) passes[ref.index].feedback = true;
}

SlangChainRenderer::Value SlangChainRenderer::valueFor(const std::string& name, size_t pass,
                                                       const std::vector<std::string>& aliases,
                                                       const std::vector<std::string>& lutIds) {
    Value value;
    auto constant = [&](float v) {
        value.kind = ValueKind::Constant;
        value.constant = v;
        return value;
    };
    if (name == "MVP") { value.kind = ValueKind::Mvp; return value; }
    if (name == "OutputSize") { value.kind = ValueKind::OutputSize; return value; }
    if (name == "FinalViewportSize") { value.kind = ValueKind::FinalViewportSize; return value; }
    if (name == "FrameCount") { value.kind = ValueKind::FrameCount; return value; }
    if (name == "FrameDirection") return constant(1.0F);
    if (name == "OriginalAspect" || name == "OriginalAspectRotated") { value.kind = ValueKind::Aspect; return value; }
    if (name == "OriginalFPS") return constant(60.0F);
    if (name == "FrameTimeDelta") return constant(16667.0F);
    if (name == "TotalSubFrames" || name == "CurrentSubFrame") return constant(1.0F);

    auto parameter = params.find(name);
    if (parameter != params.end()) {
        value.kind = ValueKind::Parameter;
        value.parameter = &parameter->second;
        return value;
    }

    const std::string suffix = "Size";
    if (name.size() > suffix.size() && name.compare(name.size() - suffix.size(), suffix.size(), suffix) == 0) {
        auto ref = slangTextureFor(name.substr(0, name.size() - suffix.size()), pass, aliases, lutIds);
        if (ref) {
            // A size alone does not make the frontend keep the texture, except feedback: its size is the pass's.
            if (ref->kind == SlangTextureKind::History) historyDepth = std::max(historyDepth, ref->index);
            value.kind = ValueKind::TextureSize;
            value.texture = *ref;
            return value;
        }
    }
    // Rotation, HDR and sensor semantics: nothing to report, zero is their neutral value.
    return value;
}

void SlangChainRenderer::setParameter(const std::string& id, float value) {
    auto parameter = params.find(id);
    // Only a known id: a new key could rehash the map under the pointers the passes hold.
    if (parameter != params.end()) parameter->second = value;
}

void SlangChainRenderer::destroyTarget(Target& target) {
    if (target.framebuffer) glDeleteFramebuffers(1, &target.framebuffer);
    if (target.texture) glDeleteTextures(1, &target.texture);
    target = Target{};
}

void SlangChainRenderer::releaseSlot(Slot& slot) {
    for (auto& target : slot.history) destroyTarget(target);
    for (auto& target : slot.outputs) destroyTarget(target);
    for (auto& target : slot.feedback) destroyTarget(target);
    slot = Slot{};
}

void SlangChainRenderer::release() {
    for (auto& slot : slots) releaseSlot(slot);
    slots.clear();
    for (auto& pass : passes) {
        if (pass.program) glDeleteProgram(pass.program);
        for (auto& stage : pass.stages) {
            if (stage.buffer) glDeleteBuffers(1, &stage.buffer);
        }
    }
    passes.clear();
    for (auto& lut : luts) {
        if (lut.texture) glDeleteTextures(1, &lut.texture);
    }
    luts.clear();
    if (copyProgram) glDeleteProgram(copyProgram);
    copyProgram = 0;
    for (auto& sampler : samplerObjects) {
        if (sampler) glDeleteSamplers(1, &sampler);
        sampler = 0;
    }
}

void SlangChainRenderer::abandonGlObjects() {
    slots.clear();
    for (auto& pass : passes) {
        pass.program = 0;
        for (auto& stage : pass.stages) stage.buffer = 0;
    }
    for (auto& lut : luts) lut.texture = 0;
    copyProgram = 0;
    samplerObjects.fill(0);
}

// Storage that cannot be rendered to on this GPU falls back to 16-bit float, then to RGBA8.
SlangChainRenderer::Target SlangChainRenderer::createTarget(unsigned width, unsigned height, GLenum format, bool mipmap) const {
    // An integer format has no float stand-in: the pass's shader writes integers.
    std::vector<GLenum> candidates = { format };
    if (!isInteger(format)) {
        if (format != GL_RGBA16F && format != GL_RGBA8) candidates.push_back(GL_RGBA16F);
        if (format != GL_RGBA8) candidates.push_back(GL_RGBA8);
    }
    for (GLenum candidate : candidates) {
        Target target;
        target.width = width;
        target.height = height;
        glGenTextures(1, &target.texture);
        glBindTexture(GL_TEXTURE_2D, target.texture);
        glTexStorage2D(GL_TEXTURE_2D, mipmap ? mipLevels(width, height) : 1, candidate, (GLsizei) width, (GLsizei) height);
        glGenFramebuffers(1, &target.framebuffer);
        glBindFramebuffer(GL_FRAMEBUFFER, target.framebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target.texture, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) {
            // A pass may read last frame's picture before any was drawn: transparent black, not memory.
            glViewport(0, 0, (GLsizei) width, (GLsizei) height);
            if (isInteger(candidate)) {
                const GLuint zero[4] = { 0, 0, 0, 0 };
                glClearBufferuiv(GL_COLOR, 0, zero);
            } else {
                glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                glClear(GL_COLOR_BUFFER_BIT);
            }
            if (mipmap) glGenerateMipmap(GL_TEXTURE_2D);
            return target;
        }
        LOGW("Slang target %u x %u in format 0x%x is not renderable", width, height, candidate);
        destroyTarget(target);
    }
    throw std::runtime_error("no renderable framebuffer format");
}

void SlangChainRenderer::resize(Slot& slot, unsigned frameW, unsigned frameH, unsigned viewportW, unsigned viewportH) {
    bool frameChanged = !slot.sizedFor || (*slot.sizedFor)[0] != frameW || (*slot.sizedFor)[1] != frameH ||
        slot.history.size() != historyDepth + 1;
    if (frameChanged) {
        for (auto& target : slot.history) destroyTarget(target);
        slot.history.clear();
        bool mipmap = passes.front().cfg.mipmapInput;
        for (unsigned i = 0; i <= historyDepth; ++i) slot.history.push_back(createTarget(frameW, frameH, GL_RGBA8, mipmap));
        slot.head = 0;
    }

    for (auto& target : slot.outputs) destroyTarget(target);
    for (auto& target : slot.feedback) destroyTarget(target);
    slot.outputs.assign(passes.size(), Target{});
    slot.feedback.assign(passes.size(), Target{});
    slot.sizes.assign(passes.size(), { 0, 0 });

    auto scaled = [&](int type, float scale, unsigned previous, unsigned viewport) {
        float value;
        switch (type) {
            case 2: value = scale * (float) viewport; break;
            case 3: value = scale; break;
            default: value = scale * (float) previous; break;
        }
        return std::min((unsigned) std::lround(std::max(value, 1.0F)), maxTextureSize);
    };

    unsigned previousW = frameW, previousH = frameH;
    for (size_t i = 0; i < passes.size(); ++i) {
        Pass& pass = passes[i];
        bool last = i + 1 == passes.size();
        int typeX = pass.cfg.scaleTypeX, typeY = pass.cfg.scaleTypeY;
        float scaleX = pass.cfg.scaleX, scaleY = pass.cfg.scaleY;
        // Unscaled: the last pass fills the viewport, any other keeps its input's size. One axis alone keeps the source.
        if (typeX == 0 && typeY == 0 && last) {
            typeX = typeY = 2;
            scaleX = scaleY = 1.0F;
        }
        if (typeX == 0) { typeX = 1; scaleX = 1.0F; }
        if (typeY == 0) { typeY = 1; scaleY = 1.0F; }
        unsigned width = scaled(typeX, scaleX, previousW, viewportW);
        unsigned height = scaled(typeY, scaleY, previousH, viewportH);
        slot.sizes[i] = { width, height };
        if (!pass.toScreen) {
            slot.outputs[i] = createTarget(width, height, pass.format, pass.mipmapOutput);
            if (pass.feedback) slot.feedback[i] = createTarget(width, height, pass.format, pass.mipmapOutput);
        }
        previousW = width;
        previousH = height;
    }
}

std::array<unsigned, 2> SlangChainRenderer::sizeOf(const SlangTextureRef& ref, const Frame& frame) const {
    switch (ref.kind) {
        case SlangTextureKind::Original:
        case SlangTextureKind::History:
            return { frame.frameW, frame.frameH };
        case SlangTextureKind::Source:
            return frame.pass == 0 ? std::array<unsigned, 2>{ frame.frameW, frame.frameH } : frame.slot->sizes[frame.pass - 1];
        case SlangTextureKind::Output:
        case SlangTextureKind::Feedback:
            return frame.slot->sizes[ref.index];
        case SlangTextureKind::Lut:
            return { luts[ref.index].width, luts[ref.index].height };
    }
    return { 1, 1 };
}

GLuint SlangChainRenderer::textureOf(const SlangTextureRef& ref, const Frame& frame) const {
    const Slot& slot = *frame.slot;
    auto history = [&](unsigned back) {
        auto count = (unsigned) slot.history.size();
        return slot.history[(slot.head + count - back % count) % count].texture;
    };
    switch (ref.kind) {
        case SlangTextureKind::Original: return history(0);
        case SlangTextureKind::History: return history(ref.index);
        case SlangTextureKind::Source: return frame.pass == 0 ? history(0) : slot.outputs[frame.pass - 1].texture;
        case SlangTextureKind::Output: return slot.outputs[ref.index].texture;
        case SlangTextureKind::Feedback: return slot.feedback[ref.index].texture;
        case SlangTextureKind::Lut: return luts[ref.index].texture;
    }
    return 0;
}

GLuint SlangChainRenderer::samplerObject(bool linear, bool mipmap, int wrap) {
    if (wrap < 0 || wrap > 3) wrap = 0;
    GLuint& sampler = samplerObjects[(linear ? 8 : 0) + (mipmap ? 4 : 0) + wrap];
    if (sampler) return sampler;
    glGenSamplers(1, &sampler);
    GLint magFilter = linear ? GL_LINEAR : GL_NEAREST;
    GLint minFilter = !mipmap ? magFilter : linear ? GL_LINEAR_MIPMAP_LINEAR : GL_NEAREST_MIPMAP_NEAREST;
    GLint mode;
    switch (wrap) {
        case 1: mode = GL_REPEAT; break;
        case 2: mode = GL_MIRRORED_REPEAT; break;
        // The border is the default colour, transparent black: what slang's clamp_to_border means.
        case 3: mode = borderClamp ? GL_CLAMP_TO_BORDER : GL_CLAMP_TO_EDGE; break;
        default: mode = GL_CLAMP_TO_EDGE; break;
    }
    glSamplerParameteri(sampler, GL_TEXTURE_MIN_FILTER, minFilter);
    glSamplerParameteri(sampler, GL_TEXTURE_MAG_FILTER, magFilter);
    glSamplerParameteri(sampler, GL_TEXTURE_WRAP_S, mode);
    glSamplerParameteri(sampler, GL_TEXTURE_WRAP_T, mode);
    return sampler;
}

// A texture is sampled the way the pass that takes it as its input asks: Source by this pass, Original and the
// history by the first pass, a pass's output by the pass after it. A LUT carries its own.
GLuint SlangChainRenderer::samplerOf(const SlangTextureRef& ref, size_t pass, bool defaultLinear) {
    if (ref.kind == SlangTextureKind::Lut) {
        const Lut& lut = luts[ref.index];
        return samplerObject(lut.linear, lut.mipmap, lut.wrap);
    }
    // Mipmaps exist exactly where the reader asked for them (see resize).
    size_t reader = pass;
    bool integer = false, mipmap = false;
    switch (ref.kind) {
        case SlangTextureKind::Original:
        case SlangTextureKind::History:
            reader = 0;
            mipmap = passes[0].cfg.mipmapInput;
            break;
        case SlangTextureKind::Source:
            integer = pass > 0 && passes[pass - 1].integerFormat;
            mipmap = passes[pass].cfg.mipmapInput;
            break;
        default:
            reader = std::min((size_t) ref.index + 1, passes.size() - 1);
            integer = passes[ref.index].integerFormat;
            mipmap = passes[ref.index].mipmapOutput;
            break;
    }
    const SlangPass& cfg = passes[reader].cfg;
    // An integer texture is incomplete under a linear filter.
    bool linear = !integer && cfg.filterLinear.value_or(defaultLinear);
    return samplerObject(linear, mipmap, cfg.wrap);
}

unsigned SlangChainRenderer::components(const Value& value, const Frame& frame, float* out) const {
    auto size = [&](unsigned width, unsigned height) {
        out[0] = (float) width;
        out[1] = (float) height;
        out[2] = 1.0F / (float) std::max(width, 1U);
        out[3] = 1.0F / (float) std::max(height, 1U);
        return 4U;
    };
    switch (value.kind) {
        case ValueKind::Mvp:
            std::memcpy(out, frame.mvp, 16 * sizeof(float));
            return 16;
        case ValueKind::OutputSize: {
            auto output = frame.slot->sizes[frame.pass];
            return size(output[0], output[1]);
        }
        case ValueKind::FinalViewportSize:
            return size(frame.viewportW, frame.viewportH);
        case ValueKind::TextureSize: {
            auto texture = sizeOf(value.texture, frame);
            return size(texture[0], texture[1]);
        }
        case ValueKind::FrameCount: {
            unsigned mod = passes[frame.pass].cfg.frameCountMod;
            out[0] = (float) (mod ? frame.frameCount % mod : frame.frameCount);
            return 1;
        }
        case ValueKind::Aspect:
            out[0] = (float) frame.frameW / (float) std::max(frame.frameH, 1U);
            return 1;
        case ValueKind::Parameter:
            out[0] = *value.parameter;
            return 1;
        case ValueKind::Constant:
            out[0] = value.constant;
            return 1;
        case ValueKind::Zero:
            break;
    }
    return 0;
}

void SlangChainRenderer::fillUniforms(Pass& pass, const Frame& frame) {
    float values[16];
    for (int s = 0; s < 2; ++s) {
        Stage& stage = pass.stages[s];
        for (auto& member : stage.block) {
            std::memset(values, 0, sizeof(values));
            components(member.value, frame, values);
            uint8_t* at = stage.data.data() + member.offset;
            for (unsigned c = 0; c < member.components; ++c) {
                if (member.base == 'f') {
                    std::memcpy(at + c * 4, &values[c], 4);
                } else if (member.base == 'i') {
                    auto v = (int32_t) std::lround(values[c]);
                    std::memcpy(at + c * 4, &v, 4);
                } else {
                    auto v = (uint32_t) std::max(std::lround(values[c]), 0L);
                    std::memcpy(at + c * 4, &v, 4);
                }
            }
        }
        if (stage.blockIndex != GL_INVALID_INDEX) {
            if (!stage.buffer) {
                glGenBuffers(1, &stage.buffer);
                glBindBuffer(GL_UNIFORM_BUFFER, stage.buffer);
                glBufferData(GL_UNIFORM_BUFFER, (GLsizeiptr) stage.data.size(), stage.data.data(), GL_DYNAMIC_DRAW);
                stage.uploaded = stage.data;
            } else if (stage.uploaded != stage.data) {
                glBindBuffer(GL_UNIFORM_BUFFER, stage.buffer);
                glBufferSubData(GL_UNIFORM_BUFFER, 0, (GLsizeiptr) stage.data.size(), stage.data.data());
                stage.uploaded = stage.data;
            }
            glBindBufferBase(GL_UNIFORM_BUFFER, (GLuint) s, stage.buffer);
        }

        for (auto& member : stage.push) {
            std::memset(values, 0, sizeof(values));
            components(member.value, frame, values);
            if (member.columns == 4 && member.vecSize == 4 && member.base == 'f') {
                glUniformMatrix4fv(member.location, 1, GL_FALSE, values);
            } else if (member.base == 'f') {
                switch (member.vecSize) {
                    case 1: glUniform1fv(member.location, 1, values); break;
                    case 2: glUniform2fv(member.location, 1, values); break;
                    case 3: glUniform3fv(member.location, 1, values); break;
                    default: glUniform4fv(member.location, 1, values); break;
                }
            } else if (member.base == 'i') {
                GLint v[4];
                for (int c = 0; c < 4; ++c) v[c] = (GLint) std::lround(values[c]);
                switch (member.vecSize) {
                    case 1: glUniform1iv(member.location, 1, v); break;
                    case 2: glUniform2iv(member.location, 1, v); break;
                    case 3: glUniform3iv(member.location, 1, v); break;
                    default: glUniform4iv(member.location, 1, v); break;
                }
            } else {
                GLuint v[4];
                for (int c = 0; c < 4; ++c) v[c] = (GLuint) std::max(std::lround(values[c]), 0L);
                switch (member.vecSize) {
                    case 1: glUniform1uiv(member.location, 1, v); break;
                    case 2: glUniform2uiv(member.location, 1, v); break;
                    case 3: glUniform3uiv(member.location, 1, v); break;
                    default: glUniform4uiv(member.location, 1, v); break;
                }
            }
        }
    }
}

void SlangChainRenderer::copy(GLuint texture, GLuint sampler, const std::array<float, 12>& positions,
                              const std::array<float, 12>& coords) {
    glUseProgram(copyProgram);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, texture);
    glBindSampler(0, sampler);
    glUniform1i(copyTexture, 0);
    bindQuad(0, positions);
    bindQuad(1, coords);
    glDrawArrays(GL_TRIANGLES, 0, 6);
}

void SlangChainRenderer::render(
    GLuint source,
    unsigned /*srcW*/,
    unsigned /*srcH*/,
    unsigned frameW,
    unsigned frameH,
    const std::array<float, 12>& sourceCoords,
    const std::array<float, 12>& foreground,
    const std::array<float, 12>& /*screenQuad*/,
    int screenW,
    int screenH,
    bool defaultLinear,
    unsigned frameCount,
    bool bottomLeftOrigin,
    unsigned slotIndex
) {
    // No frame yet: nothing to draw, and no point sizing targets from it.
    if (passes.empty() || frameW == 0 || frameH == 0 || slotIndex > 64) return;
    frameW = std::min(frameW, maxTextureSize);
    frameH = std::min(frameH, maxTextureSize);

    // A GL core caches its bindings: put back what it left on every unit and buffer point the chain touches.
    if (preserveBindings) {
        savedBindings.resize(unitsUsed);
        for (unsigned unit = 0; unit < unitsUsed; ++unit) {
            glActiveTexture(GL_TEXTURE0 + unit);
            glGetIntegerv(GL_TEXTURE_BINDING_2D, &savedBindings[unit]);
        }
    }
    {
        UniformBufferState uniformBuffers(preserveBindings);
        glActiveTexture(GL_TEXTURE0);

        auto rect = foregroundRect(foreground, screenW, screenH);
        auto viewportW = (unsigned) rect[2];
        auto viewportH = (unsigned) rect[3];

        if (slots.size() <= slotIndex) slots.resize(slotIndex + 1);
        Slot& slot = slots[slotIndex];
        std::array<unsigned, 4> inputs = { frameW, frameH, viewportW, viewportH };
        if (slot.sizedFor != inputs) {
            try {
                resize(slot, frameW, frameH, viewportW, viewportH);
            } catch (const std::exception& e) {
                LOGE("Slang chain could not size its targets: %s", e.what());
                releaseSlot(slot);
            }
            if (slot.history.empty()) {
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glBindTexture(GL_TEXTURE_2D, preserveBindings ? (GLuint) savedBindings[0] : 0);
                return;
            }
            slot.sizedFor = inputs;
        }

        // The picture's corners in the source texture and on screen: its top row is the texture's last with a
        // bottom-left origin, and the layout may have turned the quad.
        float u0 = sourceCoords[0], u1 = sourceCoords[0], v0 = sourceCoords[1], v1 = sourceCoords[1];
        for (size_t i = 0; i < sourceCoords.size(); i += 2) {
            u0 = std::min(u0, sourceCoords[i]); u1 = std::max(u1, sourceCoords[i]);
            v0 = std::min(v0, sourceCoords[i + 1]); v1 = std::max(v1, sourceCoords[i + 1]);
        }
        float top = bottomLeftOrigin ? v1 : v0, bottom = bottomLeftOrigin ? v0 : v1;
        std::array<float, 2> topLeft = { foreground[0], foreground[1] }, topRight = topLeft, bottomLeft = topLeft;
        for (size_t i = 0; i < sourceCoords.size(); i += 2) {
            bool left = sourceCoords[i] == u0, up = sourceCoords[i + 1] == top;
            std::array<float, 2> at = { foreground[i], foreground[i + 1] };
            if (left && up) topLeft = at;
            if (!left && up) topRight = at;
            if (left && !up) bottomLeft = at;
        }
        const float screenMatrix[16] = {
            topRight[0] - topLeft[0], topRight[1] - topLeft[1], 0.0F, 0.0F,
            bottomLeft[0] - topLeft[0], bottomLeft[1] - topLeft[1], 0.0F, 0.0F,
            0.0F, 0.0F, 1.0F, 0.0F,
            topLeft[0], topLeft[1], 0.0F, 1.0F,
        };

        // This frame becomes Original, the ones before it move one step back in the history.
        slot.head = (slot.head + 1) % (unsigned) slot.history.size();
        Target& original = slot.history[slot.head];
        glBindFramebuffer(GL_FRAMEBUFFER, original.framebuffer);
        glViewport(0, 0, (GLsizei) original.width, (GLsizei) original.height);
        const std::array<float, 12> frameCoords = {
            u0, top,
            u0, bottom,
            u1, top,
            u1, top,
            u0, bottom,
            u1, bottom,
        };
        copy(source, samplerObject(false, false, 0), clipQuad, frameCoords);
        if (passes.front().cfg.mipmapInput) {
            glBindTexture(GL_TEXTURE_2D, original.texture);
            glGenerateMipmap(GL_TEXTURE_2D);
        }

        // What each feedback pass drew last frame is read this frame, while it draws into the other texture.
        for (size_t i = 0; i < passes.size(); ++i) {
            if (passes[i].feedback) std::swap(slot.outputs[i], slot.feedback[i]);
        }

        for (size_t i = 0; i < passes.size(); ++i) {
            Pass& pass = passes[i];
            Frame frame { &slot, i, pass.toScreen ? screenMatrix : targetMatrix, frameW, frameH, viewportW, viewportH, frameCount };

            if (pass.toScreen) {
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glViewport(0, 0, screenW, screenH);
            } else {
                glBindFramebuffer(GL_FRAMEBUFFER, slot.outputs[i].framebuffer);
                glViewport(0, 0, (GLsizei) slot.outputs[i].width, (GLsizei) slot.outputs[i].height);
            }

            glUseProgram(pass.program);
            for (auto& sampler : pass.samplers) {
                glActiveTexture(GL_TEXTURE0 + sampler.unit);
                glBindTexture(GL_TEXTURE_2D, textureOf(sampler.texture, frame));
                glBindSampler(sampler.unit, samplerOf(sampler.texture, i, defaultLinear));
                if (!pass.samplersSet) glUniform1i(sampler.location, (GLint) sampler.unit);
            }
            pass.samplersSet = true;
            fillUniforms(pass, frame);

            bindQuad(0, unitQuad);
            bindQuad(1, unitQuad);
            glDrawArrays(GL_TRIANGLES, 0, 6);

            if (pass.mipmapOutput) {
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, slot.outputs[i].texture);
                glGenerateMipmap(GL_TEXTURE_2D);
            }
        }

        Pass& last = passes.back();
        if (!last.toScreen) {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, screenW, screenH);
            std::array<float, 12> positions{};
            for (size_t i = 0; i < unitQuad.size(); i += 2) {
                float x = unitQuad[i], y = unitQuad[i + 1];
                positions[i] = topLeft[0] + x * (topRight[0] - topLeft[0]) + y * (bottomLeft[0] - topLeft[0]);
                positions[i + 1] = topLeft[1] + x * (topRight[1] - topLeft[1]) + y * (bottomLeft[1] - topLeft[1]);
            }
            copy(slot.outputs.back().texture, samplerObject(defaultLinear && !last.integerFormat, false, 0), positions, unitQuad);
        }

        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
        for (unsigned unit = 0; unit < unitsUsed; ++unit) {
            glBindSampler(unit, 0);
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_2D, preserveBindings ? (GLuint) savedBindings[unit] : 0);
        }
        glActiveTexture(GL_TEXTURE0);
        glUseProgram(0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }
}

}
