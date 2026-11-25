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

#include "slangtranslate.h"

#include <cstdint>
#include <exception>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <utility>

#include "glslang/Public/ResourceLimits.h"
#include "glslang/Public/ShaderLang.h"
#include "SPIRV/GlslangToSpv.h"
#include "spirv_glsl.hpp"

namespace libretrodroid {

namespace {

const char* const MAGIC = "NXSLANG1";

// A slang uniform block is a few hundred bytes, a few kilobytes in the largest presets: past this a size is not one.
const unsigned MAX_BLOCK_BYTES = 64 * 1024;

std::string firstLine(const char* log, const char* fallback) {
    std::string text = log ? log : "";
    auto start = text.find_first_not_of(" \t\r\n");
    if (start == std::string::npos) return fallback;
    auto end = text.find_first_of("\r\n", start);
    return text.substr(start, end == std::string::npos ? std::string::npos : end - start);
}

bool toSpirv(EShLanguage language, const std::string& source, std::vector<uint32_t>& spirv, std::string& error) {
    // glslang keeps process-wide tables; they are never torn down, the process uses them until it dies.
    static std::once_flag once;
    std::call_once(once, [] { glslang::InitializeProcess(); });

    glslang::TShader shader(language);
    const char* text = source.c_str();
    shader.setStrings(&text, 1);
    shader.setEnvInput(glslang::EShSourceGlsl, language, glslang::EShClientVulkan, 100);
    shader.setEnvClient(glslang::EShClientVulkan, glslang::EShTargetVulkan_1_0);
    shader.setEnvTarget(glslang::EShTargetSpv, glslang::EShTargetSpv_1_0);
    auto messages = (EShMessages) (EShMsgSpvRules | EShMsgVulkanRules);
    if (!shader.parse(GetDefaultResources(), 100, false, messages)) {
        error = firstLine(shader.getInfoLog(), "does not compile");
        return false;
    }
    glslang::TProgram program;
    program.addShader(&shader);
    if (!program.link(messages)) {
        error = firstLine(program.getInfoLog(), "does not link");
        return false;
    }
    spirv.clear();
    glslang::GlslangToSpv(*program.getIntermediate(language), spirv);
    return !spirv.empty();
}

std::vector<SlangMember> blockMembers(spirv_cross::CompilerGLSL& compiler, const spirv_cross::Resource& block) {
    std::vector<SlangMember> members;
    auto& type = compiler.get_type(block.base_type_id);
    for (uint32_t i = 0; i < type.member_types.size(); ++i) {
        auto& memberType = compiler.get_type(type.member_types[i]);
        SlangMember member;
        member.name = compiler.get_member_name(block.base_type_id, i);
        member.offset = compiler.type_struct_member_offset(type, i);
        switch (memberType.basetype) {
            case spirv_cross::SPIRType::Float: member.base = 'f'; break;
            case spirv_cross::SPIRType::Int: member.base = 'i'; break;
            case spirv_cross::SPIRType::UInt: member.base = 'u'; break;
            // Nothing the frontend could fill (a struct, a double): left at zero.
            default: continue;
        }
        if (!memberType.array.empty() || member.name.empty()) continue;
        member.vecSize = memberType.vecsize;
        member.columns = memberType.columns;
        members.push_back(std::move(member));
    }
    return members;
}

// GLSL ES 3.00 has no arrays of arrays, so SPIRV-Cross flattens them (`a[3][4]` becomes `a[12]`, `a[w][z]` becomes
// `a[w * 4 + z]`) but refuses to build one from its rows (crt-geom's `vec3 mask[3][4] = { {...}, ... }`). Built here
// instead: the rows' elements in order, as one flat array.
class EsCompiler : public spirv_cross::CompilerGLSL {
public:
    using CompilerGLSL::CompilerGLSL;

    // A constant table (`const vec3 mask[2][4] = ...`) becomes a one-dimensional constant of all its elements, which
    // the flattened variable it initialises takes as it is.
    void flattenConstantTables() {
        std::vector<spirv_cross::SPIRConstant*> tables;
        ir.for_each_typed_id<spirv_cross::SPIRConstant>([&](uint32_t, spirv_cross::SPIRConstant& c) {
            if (!c.specialization && !c.replicated && get<spirv_cross::SPIRType>(c.constant_type).array.size() > 1) {
                tables.push_back(&c);
            }
        });
        for (auto* table : tables) {
            std::vector<uint32_t> elements;
            if (!leaves(table->self, elements)) continue;
            auto& type = get<spirv_cross::SPIRType>(table->constant_type);
            uint32_t elementType = type.parent_type;
            while (!get<spirv_cross::SPIRType>(elementType).array.empty()) {
                elementType = get<spirv_cross::SPIRType>(elementType).parent_type;
            }
            auto flat = type;
            flat.array = { (uint32_t) elements.size() };
            flat.array_size_literal = { true };
            flat.parent_type = elementType;
            uint32_t flatId = ir.increase_bound_by(1);
            set<spirv_cross::SPIRType>(flatId, flat);
            // increase_bound_by may grow the id table, so the constant is looked up again.
            auto& constant = get<spirv_cross::SPIRConstant>(table->self);
            constant.constant_type = flatId;
            constant.subconstants.clear();
            for (auto e : elements) constant.subconstants.push_back(e);
        }
    }

protected:
    void emit_instruction(const spirv_cross::Instruction& instruction) override {
        if (options.flatten_multidimensional_arrays && instruction.op == spv::OpCompositeConstruct && instruction.length > 2) {
            const uint32_t* ops = stream(instruction);
            auto& type = get<spirv_cross::SPIRType>(ops[0]);
            if (type.array.size() > 1) {
                emitFlatConstruct(ops[0], ops[1], ops + 2, instruction.length - 2, type);
                return;
            }
        }
        CompilerGLSL::emit_instruction(instruction);
    }

private:
    // The non-array constants under a table, in order; false when one is not a plain constant.
    bool leaves(uint32_t id, std::vector<uint32_t>& out) {
        auto* c = maybe_get<spirv_cross::SPIRConstant>(id);
        if (!c || c->specialization || c->replicated) return false;
        if (get<spirv_cross::SPIRType>(c->constant_type).array.empty()) {
            out.push_back(id);
            return true;
        }
        for (auto sub : c->subconstants) {
            if (!leaves(sub, out)) return false;
        }
        return true;
    }

    void emitFlatConstruct(uint32_t resultType, uint32_t id, const uint32_t* rows, uint32_t count,
                           const spirv_cross::SPIRType& type) {
        // type.array runs innermost first; the last entry is the row count, the rest make up one row.
        uint32_t perRow = 1;
        for (size_t i = 0; i + 1 < type.array.size(); ++i) perRow *= to_array_size_literal(type, (uint32_t) i);
        if (perRow == 0 || count != to_array_size_literal(type, (uint32_t) type.array.size() - 1)) {
            throw std::runtime_error("array of arrays with an unsized dimension");
        }
        bool forward = true;
        std::string args;
        for (uint32_t r = 0; r < count; ++r) {
            forward = forward && should_forward(rows[r]);
            auto row = to_enclosed_expression(rows[r]);
            for (uint32_t k = 0; k < perRow; ++k) {
                if (!args.empty()) args += ", ";
                args += row + "[" + std::to_string(k) + "]";
            }
        }
        // type_to_glsl names the element type; the array sizes are written separately.
        emit_op(resultType, id, type_to_glsl(type) + "[" + std::to_string(perRow * count) + "](" + args + ")", forward);
        for (uint32_t r = 0; r < count; ++r) inherit_expression_dependencies(id, rows[r]);
    }
};

void toGlslEs(std::vector<uint32_t> spirv, bool vertex, unsigned esVersion, SlangStage& out) {
    EsCompiler compiler(std::move(spirv));
    auto resources = compiler.get_shader_resources();

    // The two stages name a varying independently and match it by location, which GLSL ES 3.00 cannot express: one
    // name per location instead.
    for (auto& varying : vertex ? resources.stage_outputs : resources.stage_inputs) {
        compiler.set_name(varying.id, "nx_v" + std::to_string(compiler.get_decoration(varying.id, spv::DecorationLocation)));
    }
    if (vertex) {
        for (auto& input : resources.stage_inputs) {
            compiler.set_name(input.id, slangnames::ATTRIBUTE_PREFIX +
                std::to_string(compiler.get_decoration(input.id, spv::DecorationLocation)));
        }
    }

    if (resources.uniform_buffers.size() > 1) throw std::runtime_error("more than one uniform block");
    if (resources.push_constant_buffers.size() > 1) throw std::runtime_error("more than one push constant block");
    for (auto& block : resources.uniform_buffers) {
        // The declared size ends at the last member; std140 rounds a block up to a multiple of 16, which is what the
        // driver expects the bound buffer to hold.
        auto declared = compiler.get_declared_struct_size(compiler.get_type(block.base_type_id));
        if (declared > MAX_BLOCK_BYTES) throw std::runtime_error("uniform block too large");
        out.uboSize = ((unsigned) declared + 15U) & ~15U;
        out.ubo = blockMembers(compiler, block);
        compiler.set_name(block.base_type_id, vertex ? slangnames::UBO_VERTEX : slangnames::UBO_FRAGMENT);
        compiler.set_name(block.id, vertex ? "nx_ubo_v" : "nx_ubo_f");
    }
    for (auto& block : resources.push_constant_buffers) {
        out.push = blockMembers(compiler, block);
        compiler.set_name(block.base_type_id, vertex ? "NxPushTypeV" : "NxPushTypeF");
        compiler.set_name(block.id, vertex ? slangnames::PUSH_VERTEX : slangnames::PUSH_FRAGMENT);
    }
    for (auto& image : resources.sampled_images) {
        out.textures.push_back({ compiler.get_name(image.id), compiler.get_decoration(image.id, spv::DecorationBinding) });
    }

    spirv_cross::CompilerGLSL::Options options;
    options.version = esVersion;
    options.es = true;
    options.vulkan_semantics = false;
    options.enable_420pack_extension = false;
    options.vertex.fixup_clipspace = false;
    // GLSL ES 3.00 has no arrays of arrays.
    options.flatten_multidimensional_arrays = esVersion < 310;
    // Slang shaders are written for desktop-class precision; mediump breaks the CRT maths on phone GPUs.
    options.fragment.default_float_precision = spirv_cross::CompilerGLSL::Options::Highp;
    options.fragment.default_int_precision = spirv_cross::CompilerGLSL::Options::Highp;
    compiler.set_common_options(options);
    if (options.flatten_multidimensional_arrays) compiler.flattenConstantTables();
    out.glsl = compiler.compile();
}

bool translateStage(bool vertex, const std::string& source, unsigned esVersion, SlangStage& out, std::string& error) {
    const char* stage = vertex ? "vertex: " : "fragment: ";
    std::vector<uint32_t> spirv;
    std::string detail;
    if (!toSpirv(vertex ? EShLangVertex : EShLangFragment, source, spirv, detail)) {
        error = stage + detail;
        return false;
    }
    try {
        toGlslEs(std::move(spirv), vertex, esVersion, out);
    } catch (const std::exception& e) {
        error = stage + firstLine(e.what(), "cannot be translated");
        return false;
    }
    return true;
}

void encodeMembers(std::ostringstream& out, const char* tag, const std::vector<SlangMember>& members) {
    out << tag << ' ' << members.size() << '\n';
    for (auto& m : members) {
        out << m.name << ' ' << m.offset << ' ' << m.base << ' ' << m.vecSize << ' ' << m.columns << '\n';
    }
}

void encodeStage(std::ostringstream& out, const SlangStage& stage) {
    out << "glsl " << stage.glsl.size() << '\n' << stage.glsl << '\n';
    out << "ubosize " << stage.uboSize << '\n';
    encodeMembers(out, "ubo", stage.ubo);
    encodeMembers(out, "push", stage.push);
    out << "tex " << stage.textures.size() << '\n';
    for (auto& t : stage.textures) out << t.name << ' ' << t.binding << '\n';
}

bool expect(std::istringstream& in, const char* tag) {
    std::string word;
    return (in >> word) && word == tag;
}

// A count read from a cache file is not trusted: a block has at most a few hundred members.
const size_t MAX_ENTRIES = 4096;

bool decodeMembers(std::istringstream& in, const char* tag, std::vector<SlangMember>& members) {
    size_t count = 0;
    if (!expect(in, tag) || !(in >> count) || count > MAX_ENTRIES) return false;
    members.resize(count);
    for (auto& m : members) {
        if (!(in >> m.name >> m.offset >> m.base >> m.vecSize >> m.columns)) return false;
        if (m.base != 'f' && m.base != 'i' && m.base != 'u') return false;
        if (m.vecSize < 1 || m.vecSize > 4 || m.columns < 1 || m.columns > 4) return false;
    }
    return true;
}

bool decodeStage(std::istringstream& in, SlangStage& stage) {
    size_t length = 0;
    if (!expect(in, "glsl") || !(in >> length)) return false;
    if (in.get() != '\n') return false;
    auto remaining = (std::streamoff) in.rdbuf()->in_avail();
    if (remaining < 0 || length > (size_t) remaining) return false;
    stage.glsl.resize(length);
    if (length && !in.read(stage.glsl.data(), (std::streamsize) length)) return false;
    if (!expect(in, "ubosize") || !(in >> stage.uboSize) || stage.uboSize > MAX_BLOCK_BYTES) return false;
    if (!decodeMembers(in, "ubo", stage.ubo) || !decodeMembers(in, "push", stage.push)) return false;
    size_t count = 0;
    if (!expect(in, "tex") || !(in >> count) || count > MAX_ENTRIES) return false;
    stage.textures.resize(count);
    for (auto& t : stage.textures) {
        if (!(in >> t.name >> t.binding)) return false;
    }
    // A member written past the block would be a write past the renderer's buffer.
    for (auto& m : stage.ubo) {
        if ((size_t) m.offset + (size_t) m.vecSize * m.columns * 4 > stage.uboSize) return false;
    }
    return true;
}

} // namespace

bool translateSlang(const std::string& vertexSource, const std::string& fragmentSource, unsigned esVersion,
                    SlangProgram& out, std::string& error) {
    SlangProgram program;
    if (!translateStage(true, vertexSource, esVersion, program.vertex, error)) return false;
    if (!translateStage(false, fragmentSource, esVersion, program.fragment, error)) return false;
    out = std::move(program);
    return true;
}

std::string encodeSlangProgram(const SlangProgram& program) {
    std::ostringstream out;
    out << MAGIC << '\n';
    encodeStage(out, program.vertex);
    encodeStage(out, program.fragment);
    return out.str();
}

bool decodeSlangProgram(const std::string& bytes, SlangProgram& out) {
    std::istringstream in(bytes);
    SlangProgram program;
    if (!expect(in, MAGIC)) return false;
    if (!decodeStage(in, program.vertex) || !decodeStage(in, program.fragment)) return false;
    out = std::move(program);
    return true;
}

}
