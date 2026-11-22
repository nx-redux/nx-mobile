// Host check for hwcontext.cpp (pure, no GL). From the repo root:
//   out=$(mktemp -d)/hwcontext_test && clang++ -std=c++17 -Ilibretrodroid/src/main/cpp \
//     -Ilibretrodroid/src/main/cpp/libretro/libretro-common/include \
//     libretrodroid/src/test/cpp/hwcontext_test.cpp libretrodroid/src/main/cpp/hwcontext.cpp -o "$out" && "$out"
#include "hwcontext.h"
#include "libretro.h"

#include <cstdio>

#include "check.h"

using namespace libretrodroid;

int main() {
    // A GLES 3.2 context (Adreno, ANGLE on Xclipse).
    check(canServeHwContext(RETRO_HW_CONTEXT_OPENGLES2, 0, 0, 3, 2), "GLES2 on 3.2");
    check(canServeHwContext(RETRO_HW_CONTEXT_OPENGLES3, 0, 0, 3, 2), "GLES3 on 3.2");
    check(canServeHwContext(RETRO_HW_CONTEXT_OPENGLES_VERSION, 3, 2, 3, 2), "GLES 3.2 on 3.2");
    check(canServeHwContext(RETRO_HW_CONTEXT_OPENGLES_VERSION, 3, 1, 3, 2), "GLES 3.1 on 3.2");
    check(!canServeHwContext(RETRO_HW_CONTEXT_OPENGLES_VERSION, 3, 2, 3, 1), "GLES 3.2 on 3.1 refused");
    check(!canServeHwContext(RETRO_HW_CONTEXT_OPENGLES_VERSION, 4, 0, 3, 2), "GLES 4.0 refused");
    check(!canServeHwContext(RETRO_HW_CONTEXT_OPENGLES3, 0, 0, 2, 0), "GLES3 on a 2.0 context refused");
    // Desktop GL and the rest: never served (we only ever create a GLES context).
    check(!canServeHwContext(RETRO_HW_CONTEXT_OPENGL, 0, 0, 3, 2), "desktop GL refused");
    check(!canServeHwContext(RETRO_HW_CONTEXT_OPENGL_CORE, 3, 2, 3, 2), "GL core refused");
    check(!canServeHwContext(RETRO_HW_CONTEXT_VULKAN, 0, 0, 3, 2), "Vulkan refused");
    check(!canServeHwContext(RETRO_HW_CONTEXT_NONE, 0, 0, 3, 2), "none refused");
    if (failures == 0) std::printf("OK\n");
    return failures == 0 ? 0 : 1;
}
