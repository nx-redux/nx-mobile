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

#ifndef LIBRETRODROID_SLANGTRANSLATE_H
#define LIBRETRODROID_SLANGTRANSLATE_H

#include <string>
#include <vector>

namespace libretrodroid {

// One member of a slang pass's uniform block or push-constant block: a semantic (SourceSize, MVP, FrameCount...) or a
// parameter id, with the type the shader declared it as.
struct SlangMember {
    std::string name;
    unsigned offset = 0;      // std140 byte offset; unused for push members (plain uniforms in GLSL ES)
    char base = 'f';          // 'f' float, 'i' int, 'u' uint
    unsigned vecSize = 1;     // 1..4
    unsigned columns = 1;     // 4 for a mat4

    bool operator==(const SlangMember& o) const {
        return name == o.name && offset == o.offset && base == o.base && vecSize == o.vecSize && columns == o.columns;
    }
};

struct SlangTexture {
    std::string name;         // the semantic: Source, Original, PassOutput2, a LUT id, an alias...
    unsigned binding = 0;

    bool operator==(const SlangTexture& o) const { return name == o.name && binding == o.binding; }
};

// One stage of a pass, translated: GLSL ES text and what it reads. The names the translator gives things are fixed
// (see the constants below), so the renderer finds them without reading the text.
struct SlangStage {
    std::string glsl;
    unsigned uboSize = 0;                 // bytes; 0 = the stage has no uniform block
    std::vector<SlangMember> ubo;
    std::vector<SlangMember> push;        // uniforms named <push instance>.<member>
    std::vector<SlangTexture> textures;

    bool operator==(const SlangStage& o) const {
        return glsl == o.glsl && uboSize == o.uboSize && ubo == o.ubo && push == o.push && textures == o.textures;
    }
};

struct SlangProgram {
    SlangStage vertex, fragment;

    bool operator==(const SlangProgram& o) const { return vertex == o.vertex && fragment == o.fragment; }
    bool operator!=(const SlangProgram& o) const { return !(*this == o); }
};

namespace slangnames {
// Each stage gets its own block and instance names: the two stages of a pass may declare different blocks, and GLSL
// links same-named ones only when they match exactly.
constexpr const char* UBO_VERTEX = "NxUboV";
constexpr const char* UBO_FRAGMENT = "NxUboF";
constexpr const char* PUSH_VERTEX = "NxPushV";
constexpr const char* PUSH_FRAGMENT = "NxPushF";
// Vertex inputs by location: 0 = Position (vec4), 1 = TexCoord (vec2).
constexpr const char* ATTRIBUTE_PREFIX = "nx_a";
}

// Translates one slang pass (each stage a complete "#version 450" Vulkan GLSL source, includes expanded and pragmas
// removed) into GLSL ES [esVersion] (300, 310 or 320). Device-independent and free of GL calls: any thread. Returns
// false with a one-line [error] when a stage does not compile or uses something GLSL ES [esVersion] lacks.
bool translateSlang(const std::string& vertexSource, const std::string& fragmentSource, unsigned esVersion,
                    SlangProgram& out, std::string& error);

// A translated pass as bytes, for a disk cache and for crossing JNI. decode returns false for anything encode did
// not write.
std::string encodeSlangProgram(const SlangProgram& program);
bool decodeSlangProgram(const std::string& bytes, SlangProgram& out);

}

#endif //LIBRETRODROID_SLANGTRANSLATE_H
