# rsync for netplay's save copy (nx-redux wizard_sync.c's command lines), cross-built with the Android NDK as a PIE
# executable and shipped as jniLibs/arm64-v8a/librsync_exec.so (Android only executes files it extracted at install:
# androidApp sets useLegacyPackaging). GPL-3.0-or-later; rsync's licence ships as licenses/rsync.txt. GNU make >= 3.82.
NDK_VERSION ?= 29.0.14206865
NDK ?= $(or $(ANDROID_NDK_HOME),$(if $(ANDROID_HOME),$(ANDROID_HOME)/ndk/$(NDK_VERSION),$(HOME)/Library/Android/sdk/ndk/$(NDK_VERSION)))
UNAME_S := $(shell uname -s)
HOST_TAG := $(if $(filter Darwin,$(UNAME_S)),darwin-x86_64,linux-x86_64)
LLVM_BIN := $(NDK)/toolchains/llvm/prebuilt/$(HOST_TAG)/bin
API := 26
CC := $(LLVM_BIN)/aarch64-linux-android$(API)-clang
STRIP := $(LLVM_BIN)/llvm-strip
READELF := $(LLVM_BIN)/llvm-readelf
PROCS ?= -j$(shell sysctl -n hw.ncpu 2>/dev/null || nproc)

RSYNC_VERSION = 3.4.1
RSYNC_TARBALL = rsync-$(RSYNC_VERSION).tar.gz
RSYNC_URL = https://download.samba.org/pub/rsync/src/$(RSYNC_TARBALL)
RSYNC_SHA256 = 2924bcb3a1ed8b551fc101f740b9f0fe0a202b115027647cf69850d65fd88c52

SRC = src/rsync-$(RSYNC_VERSION)
OUT = output
BIN = $(OUT)/librsync_exec.so
LICENSE = $(OUT)/licenses/rsync.txt
JNILIBS_DIR ?= ../../androidApp/src/main/jniLibs/arm64-v8a

# The spec's flags, plus what the NDK sysroot needs: popt and zlib bundled (no popt in the NDK), no iconv (bionic has it only
# from API 28), no ACLs or xattrs (a one-file pull never uses them). The AC_RUN_IFELSE checks cannot run when cross-compiling;
# bionic has a working socketpair, a C99 vsnprintf, a secure mkstemp and gettimeofday's tz argument.
CONFIGURE_FLAGS = --host=aarch64-linux-android --disable-openssl --disable-xxhash --disable-zstd --disable-lz4 --disable-md2man \
  --with-included-popt --with-included-zlib --disable-iconv --disable-acl-support --disable-xattr-support
CONFIGURE_CACHE = rsync_cv_HAVE_SOCKETPAIR=yes rsync_cv_HAVE_C99_VSNPRINTF=yes rsync_cv_HAVE_SECURE_MKSTEMP=yes \
  rsync_cv_HAVE_GETTIMEOFDAY_TZ=yes

.DEFAULT_GOAL := build
.PHONY: build verify install clean nuke

src/$(RSYNC_TARBALL):
	mkdir -p src
	curl -fsSL -o $@.tmp $(RSYNC_URL)
	echo "$(RSYNC_SHA256)  $@.tmp" | shasum -a 256 -c -
	mv $@.tmp $@

$(SRC)/.configured: src/$(RSYNC_TARBALL)
	rm -rf $(SRC)
	tar -xzf src/$(RSYNC_TARBALL) -C src
	cd $(SRC) && $(CONFIGURE_CACHE) CC=$(CC) AR=$(LLVM_BIN)/llvm-ar RANLIB=$(LLVM_BIN)/llvm-ranlib \
	  CFLAGS="-O2 -fPIE" LDFLAGS="-pie -Wl,-z,max-page-size=16384" ./configure $(CONFIGURE_FLAGS)
	touch $@

$(BIN): $(SRC)/.configured
	$(MAKE) -C $(SRC) $(PROCS) rsync
	mkdir -p $(OUT)
	$(STRIP) --strip-unneeded -o $@ $(SRC)/rsync

$(LICENSE): src/$(RSYNC_TARBALL)
	mkdir -p $(OUT)/licenses
	{ echo "rsync $(RSYNC_VERSION) ($(RSYNC_URL), sha256 $(RSYNC_SHA256))"; \
	  for f in COPYING popt/COPYING zlib/README; do printf '\n# %s\n\n' "$$f"; tar -xzOf src/$(RSYNC_TARBALL) rsync-$(RSYNC_VERSION)/$$f; done; \
	} > $@.tmp
	mv $@.tmp $@

build: $(BIN) $(LICENSE)

# AArch64 and 16 KB LOAD alignment (cores/verify.sh), and an executable: it has a PT_INTERP (/system/bin/linker64).
verify: build
	JNILIBS_DIR=/nonexistent sh ../../cores/verify.sh $(READELF) $(BIN)
	$(READELF) -l $(BIN) | grep -q INTERP

install: verify
	mkdir -p $(JNILIBS_DIR)
	cp $(BIN) $(JNILIBS_DIR)/

clean:
	rm -rf $(OUT) $(SRC)
nuke:
	rm -rf $(OUT) src
