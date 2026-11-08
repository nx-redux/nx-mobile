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

#include "presetchain.h"

#include <algorithm>
#include <cmath>
#include <limits>
#include <regex>
#include <stdexcept>

#include "log.h"

namespace libretrodroid {

namespace {

// Pass 0 samples the core frame with the layout's coordinates; FBO outputs are sampled whole, in the
// same vertex order as the fork's quads.
const std::array<float, 12> identityCoords = {
    0.0F, 0.0F,
    0.0F, 1.0F,
    1.0F, 0.0F,
    1.0F, 0.0F,
    0.0F, 1.0F,
    1.0F, 1.0F,
};

const float identityMatrix[16] = {
    1.0F, 0.0F, 0.0F, 0.0F,
    0.0F, 1.0F, 0.0F, 0.0F,
    0.0F, 0.0F, 1.0F, 0.0F,
    0.0F, 0.0F, 0.0F, 1.0F,
};

// The stock passthrough for blitting a scaled last pass, named like a RetroArch pass.
const char* blitVertex =
    "attribute vec4 VertexCoord;\n"
    "attribute vec2 TexCoord;\n"
    "varying mediump vec2 coords;\n"
    "void main() {\n"
    "  coords = TexCoord;\n"
    "  gl_Position = VertexCoord;\n"
    "}\n";

const char* blitFragment =
    "#ifdef GL_FRAGMENT_PRECISION_HIGH\n"
    "#define HIGHP highp\n"
    "#else\n"
    "#define HIGHP mediump\n"
    "#endif\n"
    "precision mediump float;\n"
    "uniform lowp sampler2D Texture;\n"
    "varying HIGHP vec2 coords;\n"
    "void main() {\n"
    "  gl_FragColor = vec4(texture2D(Texture, coords).rgb, 1.0);\n"
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
    LOGE("Preset shader compile failed:\n%s", log.c_str());
    error = firstLine(log, type == GL_VERTEX_SHADER ? "vertex shader failed to compile" : "fragment shader failed to compile");
    glDeleteShader(shader);
    return 0;
}

// Returns 0 and sets error on failure.
GLuint linkProgram(const std::string& vertex, const std::string& fragment, std::string& error) {
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
    LOGE("Preset program link failed:\n%s", log.c_str());
    error = firstLine(log, "program failed to link");
    glDeleteProgram(program);
    return 0;
}

// RetroArch shaders may carry a "ruby" prefix on every name.
GLint uniformLocation(GLuint program, const std::string& name) {
    GLint location = glGetUniformLocation(program, name.c_str());
    if (location == -1) location = glGetUniformLocation(program, ("ruby" + name).c_str());
    return location;
}

GLint attribLocation(GLuint program, const std::string& name) {
    GLint location = glGetAttribLocation(program, name.c_str());
    if (location == -1) location = glGetAttribLocation(program, ("ruby" + name).c_str());
    return location;
}

GLint glWrap(int wrap) {
    switch (wrap) {
        case 1: return GL_REPEAT;
        case 2: return GL_MIRRORED_REPEAT;
        default: return GL_CLAMP_TO_EDGE;
    }
}

void setSampling(bool linear, int wrap) {
    GLint filter = linear ? GL_LINEAR : GL_NEAREST;
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, glWrap(wrap));
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, glWrap(wrap));
}

void bindAttrib(GLint location, const std::array<float, 12>& data) {
    if (location == -1) return;
    glVertexAttribPointer(location, 2, GL_FLOAT, GL_FALSE, 0, data.data());
    glEnableVertexAttribArray(location);
}

void unbindAttrib(GLint location) {
    if (location != -1) glDisableVertexAttribArray(location);
}

// Selects unit 0 with tightly packed client-memory unpacking for the LUT uploads, and puts back whatever a
// GL core left (active unit, its unit 0 binding, unpack state) when it goes out of scope, throw or not:
// GL cores cache that state.
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

} // namespace

std::array<int, 4> foregroundRect(const std::array<float, 12>& vertices, int screenW, int screenH) {
    float minX = std::numeric_limits<float>::max(), maxX = std::numeric_limits<float>::lowest();
    float minY = std::numeric_limits<float>::max(), maxY = std::numeric_limits<float>::lowest();
    for (int i = 0; i < 12; i += 2) {
        minX = std::min(minX, vertices[i]); maxX = std::max(maxX, vertices[i]);
        minY = std::min(minY, vertices[i + 1]); maxY = std::max(maxY, vertices[i + 1]);
    }
    int x = (int) std::lround((minX + 1.0F) * 0.5F * (float) screenW);
    int y = (int) std::lround((minY + 1.0F) * 0.5F * (float) screenH);
    int w = (int) std::lround((maxX - minX) * 0.5F * (float) screenW);
    int h = (int) std::lround((maxY - minY) * 0.5F * (float) screenH);
    return { x, y, std::max(w, 1), std::max(h, 1) };
}

// RetroArch's gl2 driver prefix. Removes the file's own "#version N" line, emits the GLES mapping first,
// then the stage defines, PARAMETER_UNIFORM and one <alias>_ALIAS define per non-empty alias.
std::string PresetChainRenderer::prefixSource(const std::string& source, bool vertex, const std::vector<std::string>& aliases) {
    std::string body = source;
    std::string versionLine;
    static const std::regex versionRe(R"(^[ \t]*#version[ \t]+(\d+)[^\n]*\n?)", std::regex::multiline);
    std::smatch m;
    if (std::regex_search(body, m, versionRe)) {
        // A number too long for stoul is still "newer than 330".
        unsigned long v = 0;
        try {
            v = std::stoul(m[1]);
        } catch (const std::exception&) {
            v = std::numeric_limits<unsigned long>::max();
        }
        if (v == 100) versionLine = "#version 100\n";
        else if (v == 300 || v == 310 || v == 320) versionLine = "#version " + std::to_string(v) + " es\n";
        else if (v >= 110 && v <= 329) versionLine = "#version 300 es\n";
        else if (v == 330) versionLine = "#version 310 es\n";
        else versionLine = "#version 320 es\n";
        body.erase(m.position(0), m.length(0));
    }
    // "#extension" must precede any non-preprocessor token, so hoist leading ones above the preludes.
    std::string extensions;
    std::string kept;
    std::string::size_type pos = 0;
    while (pos < body.size()) {
        std::string::size_type eol = body.find('\n', pos);
        std::string::size_type next = eol == std::string::npos ? body.size() : eol + 1;
        std::string line = body.substr(pos, next - pos);
        std::string::size_type first = line.find_first_not_of(" \t\r\n");
        if (first == std::string::npos || line.compare(first, 2, "//") == 0) {
            kept += line;
        } else if (line.compare(first, 10, "#extension") == 0) {
            extensions += line.substr(first);
            if (extensions.back() != '\n') extensions += '\n';
        } else {
            break;
        }
        pos = next;
    }
    body = kept + body.substr(pos);
    std::string out = versionLine + extensions;
    out += vertex ? "#define VERTEX\n" : "#define FRAGMENT\n";
    out += "#define PARAMETER_UNIFORM\n";
    for (auto& a : aliases) out += "#define " + a + "_ALIAS\n";
    // Redeclaring a default precision later is legal, so files that set their own are unaffected.
    if (vertex) {
        // ESSL int defaults to highp in vertex and mediump in fragment stages, yet a uniform declared in both
        // (FrameCount, FrameDirection) must have one precision, so give the vertex stage the fragment default.
        out += "#ifdef GL_ES\n"
               "precision mediump int;\n"
               "#endif\n";
    } else {
        // ESSL fragment stages have no default float precision, and a 3.00 file may declare "out vec4 FragColor"
        // before its own precision statement.
        out += "#ifdef GL_ES\n"
               "#ifdef GL_FRAGMENT_PRECISION_HIGH\n"
               "precision highp float;\n"
               "#else\n"
               "precision mediump float;\n"
               "#endif\n"
               "#endif\n";
    }
    out += body;
    return out;
}

unsigned PresetChainRenderer::passSize(int scaleType, float scale, unsigned previous, unsigned viewport, unsigned maxSize) {
    float v;
    switch (scaleType) {
        case 2: v = scale * (float) viewport; break;
        case 3: v = scale; break;
        case 1: default: v = scale * (float) previous; break;
    }
    unsigned r = (unsigned) std::lround(std::max(v, 1.0f));
    return std::min(r, maxSize);
}

PresetChainRenderer::PresetChainRenderer(const PresetChain& chain) : params(chain.params) {
    try {
        GLint maxSize = 0;
        glGetIntegerv(GL_MAX_TEXTURE_SIZE, &maxSize);
        if (maxSize > 0) maxTextureSize = (unsigned) maxSize;

        std::vector<std::string> aliases;
        for (auto& pass : chain.passes) {
            if (!pass.alias.empty()) aliases.push_back(pass.alias);
        }

        for (auto& lut : chain.luts) {
            if (lut.width == 0 || lut.height == 0 || lut.rgba.size() != (size_t) lut.width * lut.height * 4) {
                throw std::runtime_error("lut " + lut.id + ": pixel data does not match its size");
            }
        }
        if (!chain.luts.empty()) {
            UploadState state;
            for (auto& lut : chain.luts) {
                GLuint texture = 0;
                glGenTextures(1, &texture);
                lutTextures.push_back(texture);
                glBindTexture(GL_TEXTURE_2D, texture);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, (GLsizei) lut.width, (GLsizei) lut.height, 0,
                             GL_RGBA, GL_UNSIGNED_BYTE, lut.rgba.data());
                setSampling(lut.linear, lut.wrap);
            }
        }

        for (size_t index = 0; index < chain.passes.size(); ++index) {
            Pass pass;
            pass.cfg = chain.passes[index];

            std::string error;
            pass.program = linkProgram(
                prefixSource(pass.cfg.source, true, aliases),
                prefixSource(pass.cfg.source, false, aliases),
                error
            );
            if (!pass.program) throw std::runtime_error("pass " + std::to_string(index) + ": " + error);
            passes.push_back(pass);
            Pass& p = passes.back();
            GLuint program = p.program;

            p.vertexCoord = attribLocation(program, "VertexCoord");
            p.color = attribLocation(program, "Color");
            p.lutTexCoord = attribLocation(program, "LUTTexCoord");
            std::vector<std::string> texCoordNames = { "TexCoord", "OrigTexCoord" };

            p.mvpMatrix = uniformLocation(program, "MVPMatrix");
            p.texture = uniformLocation(program, "Texture");
            p.textureSize = uniformLocation(program, "TextureSize");
            p.inputSize = uniformLocation(program, "InputSize");
            p.outputSize = uniformLocation(program, "OutputSize");
            p.frameCount = uniformLocation(program, "FrameCount");
            p.frameDirection = uniformLocation(program, "FrameDirection");
            p.origTexture = uniformLocation(program, "OrigTexture");
            p.origTextureSize = uniformLocation(program, "OrigTextureSize");
            p.origInputSize = uniformLocation(program, "OrigInputSize");

            // Earlier outputs: Pass{k} is pass k-1's output, PassPrev{k} the output k passes back.
            auto addOutput = [&](const std::string& prefix, unsigned source) {
                OutputRef ref {
                    source,
                    uniformLocation(program, prefix + "Texture"),
                    uniformLocation(program, prefix + "TextureSize"),
                    uniformLocation(program, prefix + "InputSize"),
                };
                if (ref.texture != -1 || ref.textureSize != -1 || ref.inputSize != -1) p.outputs.push_back(ref);
                texCoordNames.push_back(prefix + "TexCoord");
            };
            for (unsigned k = 1; k <= index; ++k) {
                addOutput("Pass" + std::to_string(k), k - 1);
                addOutput("PassPrev" + std::to_string(k), (unsigned) index - k);
            }
            for (unsigned earlier = 0; earlier < index; ++earlier) {
                auto& alias = chain.passes[earlier].alias;
                if (!alias.empty()) addOutput(alias, earlier);
            }

            for (auto& name : texCoordNames) {
                GLint location = attribLocation(program, name);
                if (location != -1 && std::find(p.texCoords.begin(), p.texCoords.end(), location) == p.texCoords.end()) {
                    p.texCoords.push_back(location);
                }
            }

            for (auto& lut : chain.luts) p.luts.push_back(uniformLocation(program, lut.id));
            for (auto& param : chain.params) {
                GLint location = uniformLocation(program, param.first);
                if (location != -1) p.params.emplace_back(&params.at(param.first), location);
            }
        }

        if (!passes.empty()) {
            Pass& last = passes.back();
            last.toScreen = last.cfg.scaleTypeX == 0 && last.cfg.scaleTypeY == 0;
            if (!last.toScreen) {
                std::string error;
                Blit b;
                b.program = linkProgram(blitVertex, blitFragment, error);
                if (!b.program) throw std::runtime_error("blit: " + error);
                b.vertexCoord = glGetAttribLocation(b.program, "VertexCoord");
                b.texCoord = glGetAttribLocation(b.program, "TexCoord");
                b.texture = glGetUniformLocation(b.program, "Texture");
                blit = b;
            }
        }
    } catch (...) {
        release();
        throw;
    }
}

PresetChainRenderer::~PresetChainRenderer() {
    release();
}

void PresetChainRenderer::release() {
    for (auto& pass : passes) {
        if (pass.program) glDeleteProgram(pass.program);
        if (pass.fbo) glDeleteFramebuffers(1, &pass.fbo);
        if (pass.fboTexture) glDeleteTextures(1, &pass.fboTexture);
    }
    passes.clear();
    if (!lutTextures.empty()) glDeleteTextures((GLsizei) lutTextures.size(), lutTextures.data());
    lutTextures.clear();
    if (blit) glDeleteProgram(blit->program);
    blit.reset();
    sizedFor.reset();
}

void PresetChainRenderer::abandonGlObjects() {
    for (auto& pass : passes) {
        pass.program = 0;
        pass.fbo = 0;
        pass.fboTexture = 0;
    }
    lutTextures.clear();
    if (blit) blit->program = 0;
}

void PresetChainRenderer::setParameter(const std::string& id, float value) {
    params[id] = value;
}

void PresetChainRenderer::resize(unsigned srcW, unsigned srcH, unsigned viewportW, unsigned viewportH) {
    unsigned previousW = srcW, previousH = srcH;
    for (size_t i = 0; i < passes.size(); ++i) {
        Pass& pass = passes[i];
        unsigned width, height;
        if (pass.toScreen) {
            width = viewportW;
            height = viewportH;
        } else {
            // An axis without a scale keeps its source size (RetroArch's RARCH_SCALE_INPUT), last pass included.
            int typeX = pass.cfg.scaleTypeX, typeY = pass.cfg.scaleTypeY;
            float scaleX = pass.cfg.scaleX, scaleY = pass.cfg.scaleY;
            if (typeX == 0) { typeX = 1; scaleX = 1.0F; }
            if (typeY == 0) { typeY = 1; scaleY = 1.0F; }
            width = passSize(typeX, scaleX, previousW, viewportW, maxTextureSize);
            height = passSize(typeY, scaleY, previousH, viewportH, maxTextureSize);

            if (!pass.fbo || width != pass.width || height != pass.height) {
                if (pass.fbo) glDeleteFramebuffers(1, &pass.fbo);
                if (pass.fboTexture) glDeleteTextures(1, &pass.fboTexture);

                glGenTextures(1, &pass.fboTexture);
                glBindTexture(GL_TEXTURE_2D, pass.fboTexture);
                glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA8, (GLsizei) width, (GLsizei) height);
                setSampling(false, 0);

                glGenFramebuffers(1, &pass.fbo);
                glBindFramebuffer(GL_FRAMEBUFFER, pass.fbo);
                glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, pass.fboTexture, 0);
                if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                    LOGE("Preset pass %zu framebuffer (%u x %u) is incomplete", i, width, height);
                }
                glBindTexture(GL_TEXTURE_2D, 0);
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
            }
        }
        pass.width = width;
        pass.height = height;
        previousW = width;
        previousH = height;
    }
}

void PresetChainRenderer::render(
    GLuint source,
    unsigned srcW,
    unsigned srcH,
    unsigned frameW,
    unsigned frameH,
    const std::array<float, 12>& sourceCoords,
    const std::array<float, 12>& foreground,
    const std::array<float, 12>& screenQuad,
    int screenW,
    int screenH,
    bool defaultLinear,
    unsigned frameCount
) {
    // No frame yet: nothing to draw, and no point sizing FBOs from it.
    if (passes.empty() || srcW == 0 || srcH == 0 || frameW == 0 || frameH == 0) return;

    // Units: 0 = Texture, 1 = Orig, 2.. = LUTs in order, then Pass1..PassN outputs.
    const unsigned lutBase = 2;
    const auto passBase = lutBase + (unsigned) lutTextures.size();
    const auto units = textureUnits();

    // A GL core caches its texture bindings: put back what it left on every unit the chain touches. A software
    // frame's units hold only our own bindings, so they are not queried every frame.
    if (preserveTextureBindings) {
        savedBindings.resize(units);
        for (unsigned unit = 0; unit < units; ++unit) {
            glActiveTexture(GL_TEXTURE0 + unit);
            glGetIntegerv(GL_TEXTURE_BINDING_2D, &savedBindings[unit]);
        }
        glActiveTexture(GL_TEXTURE0);
    }

    auto rect = foregroundRect(foreground, screenW, screenH);
    auto viewportW = (unsigned) rect[2];
    auto viewportH = (unsigned) rect[3];
    std::array<unsigned, 4> inputs = { frameW, frameH, viewportW, viewportH };
    if (sizedFor != inputs) {
        resize(frameW, frameH, viewportW, viewportH);
        sizedFor = inputs;
    }

    glActiveTexture(GL_TEXTURE1);
    glBindTexture(GL_TEXTURE_2D, source);
    for (size_t i = 0; i < lutTextures.size(); ++i) {
        glActiveTexture(GL_TEXTURE0 + lutBase + i);
        glBindTexture(GL_TEXTURE_2D, lutTextures[i]);
    }

    for (size_t i = 0; i < passes.size(); ++i) {
        Pass& pass = passes[i];
        GLuint input = i == 0 ? source : passes[i - 1].fboTexture;
        unsigned inputW = i == 0 ? frameW : passes[i - 1].width;
        unsigned inputH = i == 0 ? frameH : passes[i - 1].height;

        // The previous output stays bound on its PassN unit for every later pass.
        if (i > 0) {
            glActiveTexture(GL_TEXTURE0 + passBase + i - 1);
            glBindTexture(GL_TEXTURE_2D, input);
        }

        if (pass.toScreen) {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, screenW, screenH);
        } else {
            glBindFramebuffer(GL_FRAMEBUFFER, pass.fbo);
            glViewport(0, 0, (GLsizei) pass.width, (GLsizei) pass.height);
        }

        glUseProgram(pass.program);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, input);
        setSampling(pass.cfg.filterLinear.value_or(defaultLinear), pass.cfg.wrap);

        glUniform1i(pass.texture, 0);
        glUniform2f(pass.textureSize, (float) (i == 0 ? srcW : inputW), (float) (i == 0 ? srcH : inputH));
        glUniform2f(pass.inputSize, (float) inputW, (float) inputH);
        glUniform2f(pass.outputSize, (float) pass.width, (float) pass.height);
        glUniformMatrix4fv(pass.mvpMatrix, 1, GL_FALSE, identityMatrix);
        glUniform1i(pass.frameCount, (GLint) (pass.cfg.frameCountMod ? frameCount % pass.cfg.frameCountMod : frameCount));
        glUniform1i(pass.frameDirection, 1);

        glUniform1i(pass.origTexture, 1);
        glUniform2f(pass.origTextureSize, (float) srcW, (float) srcH);
        glUniform2f(pass.origInputSize, (float) frameW, (float) frameH);

        for (size_t l = 0; l < pass.luts.size(); ++l) {
            glUniform1i(pass.luts[l], (GLint) (lutBase + l));
        }
        for (auto& ref : pass.outputs) {
            auto& output = passes[ref.pass];
            glUniform1i(ref.texture, (GLint) (passBase + ref.pass));
            glUniform2f(ref.textureSize, (float) output.width, (float) output.height);
            glUniform2f(ref.inputSize, (float) output.width, (float) output.height);
        }
        for (auto& param : pass.params) {
            glUniform1f(param.second, *param.first);
        }

        bindAttrib(pass.vertexCoord, pass.toScreen ? foreground : screenQuad);
        // Only pass 0 reads sourceCoords (the frame's part of the texture). Later passes use identity coords: right for
        // earlier passes' outputs, but a later pass that samples Original (the core texture) sees the whole texture,
        // not the frame's sub-rectangle, so it is misaligned when a GL core's frame is smaller than its framebuffer.
        auto& coords = i == 0 ? sourceCoords : identityCoords;
        for (GLint location : pass.texCoords) bindAttrib(location, coords);
        bindAttrib(pass.lutTexCoord, identityCoords);
        if (pass.color != -1) {
            glDisableVertexAttribArray(pass.color);
            glVertexAttrib4f(pass.color, 1.0F, 1.0F, 1.0F, 1.0F);
        }

        glDrawArrays(GL_TRIANGLES, 0, 6);

        unbindAttrib(pass.vertexCoord);
        for (GLint location : pass.texCoords) unbindAttrib(location);
        unbindAttrib(pass.lutTexCoord);

        // The core texture is shared with the built-in shaders, which expect edge clamping.
        if (i == 0 && pass.cfg.wrap != 0) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        }
    }

    Pass& last = passes.back();
    if (!last.toScreen && blit) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, screenW, screenH);
        glUseProgram(blit->program);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, last.fboTexture);
        setSampling(defaultLinear, 0);
        glUniform1i(blit->texture, 0);

        bindAttrib(blit->vertexCoord, foreground);
        bindAttrib(blit->texCoord, identityCoords);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        unbindAttrib(blit->vertexCoord);
        unbindAttrib(blit->texCoord);
    }

    for (unsigned unit = 0; unit < units; ++unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, preserveTextureBindings ? (GLuint) savedBindings[unit] : 0);
    }
    glActiveTexture(GL_TEXTURE0);
    glUseProgram(0);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

}
