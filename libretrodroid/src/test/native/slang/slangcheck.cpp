// Host checks for the slang translator (see CMakeLists.txt for how to build and run).
//   slangcheck                 fixed checks: a small pass translates, reflects and survives encode/decode; slang
//                              texture names resolve
//   slangcheck <dir> [es]      every <name>.vert + <name>.frag pair in <dir>: translate to GLSL ES [es] (default 320)
//                              and compile the result as GLSL ES; prints the failures grouped by message

#include <cstdio>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <map>
#include <sstream>
#include <string>
#include <vector>

#include "glslang/Public/ResourceLimits.h"
#include "glslang/Public/ShaderLang.h"
#include "slangsemantics.h"
#include "slangtranslate.h"

using namespace libretrodroid;

namespace {

int failures = 0;

void check(bool ok, const char* what) {
    if (ok) return;
    std::printf("FAIL: %s\n", what);
    ++failures;
}

std::string readFile(const std::filesystem::path& path) {
    std::ifstream in(path, std::ios::binary);
    std::ostringstream out;
    out << in.rdbuf();
    return out.str();
}

// Whether the pair is a valid GLSL ES program: what the phone's driver will be asked to compile.
bool compilesAsEs(const SlangProgram& program, unsigned esVersion, std::string& error) {
    auto messages = (EShMessages) EShMsgDefault;
    glslang::TShader vertex(EShLangVertex), fragment(EShLangFragment);
    const char* vs = program.vertex.glsl.c_str();
    const char* fs = program.fragment.glsl.c_str();
    vertex.setStrings(&vs, 1);
    fragment.setStrings(&fs, 1);
    for (auto* shader : { &vertex, &fragment }) {
        if (!shader->parse(GetDefaultResources(), (int) esVersion, EEsProfile, false, false, messages)) {
            error = shader->getInfoLog();
            return false;
        }
    }
    glslang::TProgram linked;
    linked.addShader(&vertex);
    linked.addShader(&fragment);
    if (!linked.link(messages)) {
        error = linked.getInfoLog();
        return false;
    }
    return true;
}

std::string firstLine(const std::string& text) {
    auto start = text.find_first_not_of(" \t\r\n");
    if (start == std::string::npos) return "(no message)";
    auto end = text.find_first_of("\r\n", start);
    return text.substr(start, end == std::string::npos ? std::string::npos : end - start);
}

const char* const HEADER =
    "#version 450\n"
    "layout(push_constant) uniform Push { vec4 SourceSize; vec4 OutputSize; uint FrameCount; float GLOW; } params;\n"
    "layout(std140, set = 0, binding = 0) uniform UBO { mat4 MVP; vec4 FinalViewportSize; } global;\n";

const char* const VERTEX =
    "layout(location = 0) in vec4 Position;\n"
    "layout(location = 1) in vec2 TexCoord;\n"
    "layout(location = 0) out vec2 vTexCoord;\n"
    "void main() { gl_Position = global.MVP * Position; vTexCoord = TexCoord; }\n";

const char* const FRAGMENT =
    "layout(location = 0) in vec2 uv;\n"
    "layout(location = 0) out vec4 FragColor;\n"
    "layout(set = 0, binding = 2) uniform sampler2D Source;\n"
    "layout(set = 0, binding = 3) uniform sampler2D PassFeedback1;\n"
    "void main() { FragColor = texture(Source, uv) * params.GLOW + texture(PassFeedback1, uv * params.SourceSize.zw)"
    " + vec4(float(params.FrameCount)) * global.FinalViewportSize; }\n";

const SlangMember* member(const std::vector<SlangMember>& members, const char* name) {
    for (auto& m : members) if (m.name == name) return &m;
    return nullptr;
}

void fixedChecks() {
    std::string vertex = std::string(HEADER) + VERTEX, fragment = std::string(HEADER) + FRAGMENT;
    for (unsigned version : { 300u, 310u, 320u }) {
        SlangProgram program;
        std::string error;
        bool ok = translateSlang(vertex, fragment, version, program, error);
        if (!ok) std::printf("  es %u: %s\n", version, error.c_str());
        check(ok, "a small pass translates");
        if (!ok) continue;

        bool valid = compilesAsEs(program, version, error);
        if (!valid) std::printf("  es %u: %s\n%s\n%s\n", version, error.c_str(), program.vertex.glsl.c_str(), program.fragment.glsl.c_str());
        check(valid, "the translated pass is valid GLSL ES");
        check(program.vertex.uboSize == 80 && program.fragment.uboSize == 80, "block size is the std140 size");
        auto* mvp = member(program.vertex.ubo, "MVP");
        check(mvp && mvp->offset == 0 && mvp->columns == 4 && mvp->vecSize == 4 && mvp->base == 'f', "MVP is a mat4 at 0");
        auto* viewport = member(program.fragment.ubo, "FinalViewportSize");
        check(viewport && viewport->offset == 64 && viewport->vecSize == 4, "FinalViewportSize is a vec4 at 64");
        auto* count = member(program.fragment.push, "FrameCount");
        check(count && count->base == 'u' && count->vecSize == 1, "FrameCount is a uint push member");
        auto* glow = member(program.fragment.push, "GLOW");
        check(glow && glow->base == 'f', "a parameter is a float push member");
        check(program.fragment.textures.size() == 2, "both samplers are listed");
        for (auto& t : program.fragment.textures) {
            check((t.name == "Source" && t.binding == 2) || (t.name == "PassFeedback1" && t.binding == 3), "sampler bindings");
        }
        check(program.vertex.textures.empty(), "the vertex stage samples nothing");
        check(program.vertex.glsl.find(slangnames::UBO_VERTEX) != std::string::npos, "vertex block name");
        check(program.fragment.glsl.find(slangnames::UBO_FRAGMENT) != std::string::npos, "fragment block name");
        check(program.fragment.glsl.find(std::string(slangnames::PUSH_FRAGMENT) + ".GLOW") != std::string::npos, "push instance name");
        check(program.vertex.glsl.find("nx_a0") != std::string::npos && program.vertex.glsl.find("nx_a1") != std::string::npos,
              "vertex inputs are named by location");
        check(program.vertex.glsl.find("nx_v0") != std::string::npos && program.fragment.glsl.find("nx_v0") != std::string::npos,
              "a varying has one name in both stages");

        SlangProgram decoded;
        check(decodeSlangProgram(encodeSlangProgram(program), decoded) && decoded == program, "encode/decode round trip");
    }

    SlangProgram program;
    std::string error;
    check(!translateSlang(vertex, std::string(HEADER) + "void main() { nope(); }\n", 320, program, error), "a broken stage fails");
    check(error.rfind("fragment: ", 0) == 0, "the error names the stage");
    check(!decodeSlangProgram("", program) && !decodeSlangProgram("NXSLANG1\nglsl 99999\nx", program), "garbage does not decode");
    SlangProgram good;
    check(translateSlang(vertex, fragment, 320, good, error), "the small pass translates again");
    std::string huge = encodeSlangProgram(good);
    auto at = huge.find("ubosize 80");
    check(at != std::string::npos, "the encoded block size is where expected");
    if (at != std::string::npos) huge.replace(at, 10, "ubosize 4000000000");
    check(!decodeSlangProgram(huge, program), "an absurd block size does not decode");

    // A block that ends on a scalar is rounded up to the 16 bytes std140 gives it.
    std::string odd = "#version 450\nlayout(std140, set = 0, binding = 0) uniform UBO { mat4 MVP; float A; } global;\n";
    check(translateSlang(odd + VERTEX, odd + "layout(location = 0) in vec2 uv;\nlayout(location = 0) out vec4 FragColor;\n"
          "void main() { FragColor = vec4(global.A) + vec4(uv, 0.0, 1.0); }\n", 320, program, error) && program.fragment.uboSize == 80,
          "a block's size is a multiple of 16");
}

void semanticChecks() {
    using Kind = SlangTextureKind;
    const std::vector<std::string> aliases = { "", "Blur", "", "Final" };
    const std::vector<std::string> luts = { "Mask", "Blur2" };
    auto is = [&](const char* name, size_t pass, Kind kind, unsigned index) {
        auto ref = slangTextureFor(name, pass, aliases, luts);
        return ref && ref->kind == kind && ref->index == index;
    };
    check(is("Original", 2, Kind::Original, 0) && is("Source", 2, Kind::Source, 0), "Original and Source");
    check(is("OriginalHistory0", 0, Kind::Original, 0) && is("OriginalHistory3", 0, Kind::History, 3), "history by depth");
    check(is("PassOutput1", 2, Kind::Output, 1), "an earlier pass's output");
    check(is("PassOutput2", 2, Kind::Feedback, 2) && is("PassOutput3", 2, Kind::Feedback, 3),
          "this or a later pass's output is last frame's");
    check(is("PassFeedback0", 2, Kind::Feedback, 0) && is("PassFeedback3", 0, Kind::Feedback, 3), "feedback of any pass");
    check(is("Blur", 3, Kind::Output, 1) && is("Blur", 1, Kind::Feedback, 1) && is("BlurFeedback", 3, Kind::Feedback, 1),
          "aliases stand for their pass");
    check(is("Mask", 0, Kind::Lut, 0) && is("Blur2", 0, Kind::Lut, 1), "a LUT by its id, not a longer alias");
    check(!slangTextureFor("PassOutput4", 0, aliases, luts) && !slangTextureFor("PassFeedback9", 0, aliases, luts),
          "a pass that does not exist");
    check(!slangTextureFor("Nope", 0, aliases, luts) && !slangTextureFor("PassOutput", 0, aliases, luts) &&
          !slangTextureFor("PassOutput1x", 2, aliases, luts) && !slangTextureFor("Feedback", 0, aliases, luts) &&
          !slangTextureFor("OriginalHistory999", 0, aliases, luts), "names that are no texture");
}

int checkDirectory(const std::string& dir, unsigned esVersion) {
    std::vector<std::filesystem::path> vertexFiles;
    for (auto& entry : std::filesystem::directory_iterator(dir)) {
        if (entry.path().extension() == ".vert") vertexFiles.push_back(entry.path());
    }
    std::map<std::string, std::vector<std::string>> byError;
    size_t passed = 0;
    for (auto& vertexFile : vertexFiles) {
        auto fragmentFile = vertexFile;
        fragmentFile.replace_extension(".frag");
        SlangProgram program;
        std::string error;
        bool ok = translateSlang(readFile(vertexFile), readFile(fragmentFile), esVersion, program, error);
        if (ok) {
            std::string esError;
            if (!compilesAsEs(program, esVersion, esError)) {
                ok = false;
                error = "es: " + firstLine(esError);
            }
        }
        SlangProgram decoded;
        if (ok && !(decodeSlangProgram(encodeSlangProgram(program), decoded) && decoded == program)) {
            ok = false;
            error = "encode/decode round trip";
        }
        if (ok) ++passed; else byError[error].push_back(vertexFile.stem().string());
    }
    std::printf("es %u: %zu of %zu passes translate\n", esVersion, passed, vertexFiles.size());
    for (auto& [error, names] : byError) {
        std::printf("  %zu x %s   (e.g. %s)\n", names.size(), error.c_str(), names.front().c_str());
    }
    return 0;
}

}

int main(int argc, char** argv) {
    glslang::InitializeProcess();
    if (argc >= 2) return checkDirectory(argv[1], argc >= 3 ? (unsigned) std::atoi(argv[2]) : 320);
    fixedChecks();
    semanticChecks();
    if (failures) {
        std::printf("%d check(s) failed\n", failures);
        return 1;
    }
    std::printf("slangcheck: all checks passed\n");
    return 0;
}
