cmake_minimum_required(VERSION 3.13.2)

# now build app's shared lib
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -Wall")

add_definitions("-DVFS_FRONTEND -DHAVE_STRL")

# Let's include oboe
set (OBOE_DIR oboe)
add_subdirectory (${OBOE_DIR} oboe)
include_directories (${OBOE_DIR}/include)
include_directories (${OBOE_DIR}/src)

include_directories("libretro/libretro-common/include")

# rcheevos (RetroAchievements runtime). No Lua; hashing enabled for rc_client.
set (RCHEEVOS_DIR rcheevos)
add_definitions("-DRC_DISABLE_LUA -DRC_CLIENT_SUPPORTS_HASH")
include_directories (${RCHEEVOS_DIR}/include ${RCHEEVOS_DIR}/src)
file (GLOB RCHEEVOS_RUNTIME ${RCHEEVOS_DIR}/src/rcheevos/*.c)
set (RCHEEVOS_SOURCES
        ${RCHEEVOS_DIR}/src/rc_client.c
        ${RCHEEVOS_DIR}/src/rc_compat.c
        ${RCHEEVOS_DIR}/src/rc_util.c
        ${RCHEEVOS_DIR}/src/rc_version.c
        ${RCHEEVOS_DIR}/src/rc_libretro.c
        ${RCHEEVOS_RUNTIME}
        ${RCHEEVOS_DIR}/src/rapi/rc_api_common.c
        ${RCHEEVOS_DIR}/src/rapi/rc_api_editor.c
        ${RCHEEVOS_DIR}/src/rapi/rc_api_info.c
        ${RCHEEVOS_DIR}/src/rapi/rc_api_runtime.c
        ${RCHEEVOS_DIR}/src/rapi/rc_api_user.c
        ${RCHEEVOS_DIR}/src/rhash/aes.c
        ${RCHEEVOS_DIR}/src/rhash/cdreader.c
        ${RCHEEVOS_DIR}/src/rhash/hash.c
        ${RCHEEVOS_DIR}/src/rhash/hash_disc.c
        ${RCHEEVOS_DIR}/src/rhash/hash_encrypted.c
        ${RCHEEVOS_DIR}/src/rhash/hash_rom.c
        ${RCHEEVOS_DIR}/src/rhash/hash_zip.c
        ${RCHEEVOS_DIR}/src/rhash/md5.c
)

# libchdr (CHD disc images for hashing). Static, bundled miniz/lzma/zstd, no tests, no shared lib.
set (BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set (CHDR_WANT_TESTS OFF CACHE BOOL "" FORCE)
set (INSTALL_STATIC_LIBS OFF CACHE BOOL "" FORCE)
add_subdirectory (libchdr EXCLUDE_FROM_ALL)
include_directories (libchdr/include)

# libsamplerate 0.2.2 (BSD-2-Clause): the audio resampler, its four converters (Audio Resampling Quality). Static, from
# its sources: upstream's CMake also builds tests and examples.
add_library (samplerate STATIC
        libsamplerate/src/samplerate.c
        libsamplerate/src/src_linear.c
        libsamplerate/src/src_sinc.c
        libsamplerate/src/src_zoh.c
)
target_include_directories (samplerate PUBLIC libsamplerate/include)
target_compile_definitions (samplerate PRIVATE HAVE_STDBOOL_H=1 ENABLE_SINC_FAST_CONVERTER ENABLE_SINC_BEST_CONVERTER
        CPU_CLIPS_POSITIVE=0 CPU_CLIPS_NEGATIVE=0 PACKAGE="libsamplerate" VERSION="0.2.2")

set (LIBRETRO_COMMON
        libretro/libretro-common/vfs/vfs_implementation.c
        libretro/libretro-common/string/stdstring.c
        libretro/libretro-common/encodings/encoding_utf.c
        libretro/libretro-common/file/file_path.c
        libretro/libretro-common/time/rtime.c
)

add_library(libretrodroid SHARED
        libretrodroidjni.h
        libretrodroidjni.cpp
        libretrodroid.h
        libretrodroid.cpp
        log.h
        core.h
        core.cpp
        coreretention.h
        coreretention.cpp
        video.h
        video.cpp
        gldebug.h
        gldebug.cpp
        logflag.h
        logflag.cpp
        immersivemode.h
        immersivemode.cpp
        videolayout.h
        videolayout.cpp
        screenregion.h
        screenregion.cpp
        cheatmem.cpp
        hwcontext.h
        hwcontext.cpp
        vulkanbridge.h
        vulkanbridge.cpp
        teardowncontext.h
        teardowncontext.cpp
        sharedcontexts.h
        sharedcontexts.cpp
        pointerstate.h
        pointerstate.cpp
        rewindring.h
        rewindring.cpp
        rewindbudget.h
        rewindbudget.cpp
        rewindprobe.h
        rewind.h
        rewind.cpp
        sensorstate.h
        sensorstate.cpp
        languagemap.h
        languagemap.cpp
        renderers/pixelconvert.h
        renderers/pixelconvert.cpp
        renderers/renderer.h
        renderers/renderer.cpp
        renderers/es3/es3utils.h
        renderers/es3/es3utils.cpp
        renderers/es3/framebufferrenderer.h
        renderers/es3/framebufferrenderer.cpp
        renderers/es3/ahbrenderer.h
        renderers/es3/ahbrenderer.cpp
        renderers/es2/imagerendereres2.h
        renderers/es2/imagerendereres2.cpp
        renderers/es3/imagerendereres3.h
        renderers/es3/imagerendereres3.cpp
        audio.h
        audio.cpp
        fpssync.h
        fpssync.cpp
        fastforward.h
        fastforward.cpp
        environment.h
        environment.cpp
        corelog.h
        corelog.cpp
        input.h
        input.cpp
        shadermanager.h
        shadermanager.cpp
        presetchain.h
        presetchain.cpp
        rumble.h
        rumble.cpp
        rumblestate.h
        rumblestate.cpp
        utils/javautils.h
        utils/javautils.cpp
        utils/utils.cpp
        utils/utils.h
        utils/jnistring.h
        utils/jnistring.cpp
        utils/utf8.h
        utils/utf8.cpp
        utils/libretrodroidexception.h
        utils/libretrodroidexception.cpp
        utils/rect.h
        utils/rect.cpp
        errorcodes.h
        errorcodes.cpp
        vfs/vfs.h
        vfs/vfs.cpp
        vfs/vfsfile.h
        vfs/vfsfile.cpp
        vfs/fdwrapper.h
        vfs/fdwrapper.cpp
        microphone/microphone.h
        microphone/microphone.cpp
        microphone/microphoneinterface.h
        microphone/microphoneinterface.cpp
        microphone/micfeed.h
        microphone/micfeed.cpp
        achievements/achievements.h
        achievements/achievements.cpp
        achievements/memoryregiontable.h
        achievements/memoryregiontable.cpp
        achievements/achievementshash.h
        achievements/achievementshash.cpp
        achievements/chdreader.h
        achievements/chdreader.cpp
        achievements/discfile.h
        achievements/discfile.cpp
        netplay/np_engine.h
        netplay/np_engine.c
        netplay/np_net.h
        netplay/np_net.c
        netplay/np_tcp.h
        netplay/np_tcp.c
        netplay/netplayhooks.h
        netplay/netplayhooks.cpp
        netplay/netplayjni.cpp
        netplay/np_link.h
        netplay/np_link.c
        netplay/nl_netpacket.h
        netplay/linkhooks.h
        netplay/linkhooks.cpp
        netplay/linkjni.cpp
        ${RCHEEVOS_SOURCES}
        ${LIBRETRO_COMMON}
        lz4/lz4.c
)

# Support 16k pages for Android 15
target_link_options(libretrodroid PRIVATE "-Wl,-z,max-page-size=16384")

# add lib dependencies
target_link_libraries(libretrodroid
                      android
                      log 
                      EGL
                      oboe
                      samplerate
                      chdr-static
                      GLESv3
                      vulkan
                      mediandk
                      nativewindow
)
