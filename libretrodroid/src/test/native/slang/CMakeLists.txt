# Host build of the slang translator and its checker:
#   cmake -S libretrodroid/src/test/native/slang -B <build dir> -DCMAKE_BUILD_TYPE=Release && cmake --build <build dir> -j
#   <build dir>/slangcheck            the fixed checks
#   <build dir>/slangcheck <dir> 320  every <name>.vert + <name>.frag pair in <dir> (see SlangLibraryDump in shared's tests)
cmake_minimum_required(VERSION 3.22.1)
project(slangcheck CXX)
set(CMAKE_CXX_STANDARD 17)
set(CPP ${CMAKE_CURRENT_SOURCE_DIR}/../../../main/cpp)
include(${CPP}/slang.cmake)
add_executable(slangcheck slangcheck.cpp ${CPP}/slangtranslate.cpp)
target_include_directories(slangcheck PRIVATE ${CPP})
target_link_libraries(slangcheck PRIVATE nxslangdeps)
