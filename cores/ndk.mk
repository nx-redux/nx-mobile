# The NDK and its tools, for every Makefile that cross-builds for the app (cores/, tools/rsync).
# NDK r29, the one libretro's buildbot builds its Android cores with (libretro-build-android), and
# flycast's and ARMSX2's own CI. r26's libc++ std::ranges::binary_search compared against the first element (Dolphin
# flushed on most draws, ~600 glFlush a frame in Super Mario Galaxy 2).
NDK_VERSION ?= 29.0.14206865
NDK ?= $(or $(ANDROID_NDK_HOME),$(if $(ANDROID_HOME),$(ANDROID_HOME)/ndk/$(NDK_VERSION),$(HOME)/Library/Android/sdk/ndk/$(NDK_VERSION)))
UNAME_S := $(shell uname -s)
HOST_TAG := $(if $(filter Darwin,$(UNAME_S)),darwin-x86_64,linux-x86_64)
LLVM_BIN := $(NDK)/toolchains/llvm/prebuilt/$(HOST_TAG)/bin
STRIP := $(LLVM_BIN)/llvm-strip
READELF := $(LLVM_BIN)/llvm-readelf
PROCS ?= -j$(shell sysctl -n hw.ncpu 2>/dev/null || nproc)
