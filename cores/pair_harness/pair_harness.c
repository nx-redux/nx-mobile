/*
 * nx-mobile - pair_harness: a host-side driver for the mGBA libretro core's
 * pair mode. Loads the core, plays a fixed input stream on ports 0 and 1, and
 * prints a hash every N frames, so separate runs (and separate machines) can
 * be compared.
 * Copyright (C) 2026 Mohammad Syuhada.
 *
 *   pair_harness --core <lib> --rom <gba> [options]
 *     --frames N          frames to run (600)
 *     --every N           print a hash every N frames and after the last (60)
 *     --opt key=value     a core option (repeatable)
 *     --system DIR        system directory (BIOS)
 *     --save PATH         loaded into RETRO_MEMORY_SAVE_RAM before the first frame
 *     --hash pair|state   nx_pair_hash, or FNV-1a of retro_serialize (default)
 *     --state-out F:PATH  write retro_serialize after frame F
 *     --state-in PATH     retro_unserialize before the first frame
 *     --first-frame N     number of the first frame run (for --state-in)
 *     --save-ram-at F     print RETRO_MEMORY_SAVE_RAM's size and FNV-1a after
 *                         frame F (repeatable)
 *     --reset-at F        retro_reset after frame F
 *     --opposing 1        also press opposite directions together now and then
 *     --shot F:PATH       write frame F's picture as a BMP
 *     --count-log TEXT    print how many core log lines contained TEXT
 *
 * Output lines: "frame <n> <hash>", "save_ram <n> <size> <hash>",
 * "log_count <n>".
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file at the repository root.
 */
#include <dlfcn.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "libretro.h"

#define MAX_OPTS 32
#define MAX_SAVE_RAM_AT 8
#define FNV_OFFSET 0xCBF29CE484222325ULL

static struct {
	const char* key;
	const char* value;
} opts[MAX_OPTS];
static int nOpts;
static const char* systemDir = ".";
static long currentFrame;
static const char* countText;
static long countHits;
static bool opposing;
static bool variablesReported;

static uint16_t* lastFrame;
static unsigned lastWidth;
static unsigned lastHeight;

static uint64_t (*pairHash)(void);
static size_t (*serializeSize)(void);
static bool (*serialize)(void*, size_t);

static void die(const char* fmt, ...) {
	va_list args;
	va_start(args, fmt);
	fprintf(stderr, "pair_harness: ");
	vfprintf(stderr, fmt, args);
	fprintf(stderr, "\n");
	va_end(args);
	exit(2);
}

static uint64_t fnv(uint64_t hash, const void* data, size_t size) {
	const uint8_t* bytes = data;
	size_t i;
	for (i = 0; i < size; ++i) {
		hash ^= bytes[i];
		hash *= 0x100000001B3ULL;
	}
	return hash;
}

static void* readFile(const char* path, size_t* size) {
	FILE* f = fopen(path, "rb");
	if (!f) {
		die("cannot read %s", path);
	}
	long length = fseek(f, 0, SEEK_END) == 0 ? ftell(f) : -1;
	if (length < 0 || fseek(f, 0, SEEK_SET) != 0) {
		die("cannot size %s", path);
	}
	void* data = malloc(length ? length : 1);
	if (fread(data, 1, length, f) != (size_t) length) {
		die("short read on %s", path);
	}
	fclose(f);
	*size = length;
	return data;
}

static void writeFile(const char* path, const void* data, size_t size) {
	FILE* f = fopen(path, "wb");
	if (!f || fwrite(data, 1, size, f) != size) {
		die("cannot write %s", path);
	}
	fclose(f);
}

/* START and A in turns every second, B now and then, and a d-pad direction
 * that changes every 16 frames from a fixed LCG; the two ports differ. It
 * takes Mario Kart: Super Circuit through its link menus into a 2-player
 * game. */
static uint16_t mash(long frame, unsigned port) {
	uint16_t buttons = 0;
	long phase = (frame + port * 7) % 60;
	if (phase < 4) {
		buttons |= 1 << RETRO_DEVICE_ID_JOYPAD_START;
	} else if (phase >= 30 && phase < 34) {
		buttons |= 1 << RETRO_DEVICE_ID_JOYPAD_A;
	} else if (phase >= 45 && phase < 47 && (frame / 60) % 3 == 2) {
		buttons |= 1 << RETRO_DEVICE_ID_JOYPAD_B;
	}
	uint32_t seed = (uint32_t) (frame / 16) * 2654435761u + port * 40503u;
	seed = seed * 1103515245u + 12345u;
	static const unsigned dirs[] = {
		RETRO_DEVICE_ID_JOYPAD_UP, RETRO_DEVICE_ID_JOYPAD_DOWN,
		RETRO_DEVICE_ID_JOYPAD_LEFT, RETRO_DEVICE_ID_JOYPAD_RIGHT,
	};
	unsigned pick = (seed >> 16) % 6;
	if (pick < 4) {
		buttons |= 1 << dirs[pick];
	}
	if (opposing && frame % 50 < 5) {
		buttons |= 1 << RETRO_DEVICE_ID_JOYPAD_LEFT | 1 << RETRO_DEVICE_ID_JOYPAD_RIGHT;
		buttons |= 1 << RETRO_DEVICE_ID_JOYPAD_UP | 1 << RETRO_DEVICE_ID_JOYPAD_DOWN;
	}
	return buttons;
}

static void logPrintf(enum retro_log_level level, const char* fmt, ...) {
	char message[1024];
	va_list args;
	va_start(args, fmt);
	vsnprintf(message, sizeof(message), fmt, args);
	va_end(args);
	if (countText && strstr(message, countText)) {
		++countHits;
	}
	if (level >= RETRO_LOG_WARN) {
		fprintf(stderr, "core: %s", message);
	}
}

static bool environment(unsigned cmd, void* data) {
	switch (cmd) {
	case RETRO_ENVIRONMENT_GET_VARIABLE: {
		struct retro_variable* var = data;
		int i;
		for (i = 0; i < nOpts; ++i) {
			if (strcmp(opts[i].key, var->key) == 0) {
				var->value = opts[i].value;
				return true;
			}
		}
		var->value = NULL;
		return false;
	}
	case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
	case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
		*(const char**) data = systemDir;
		return true;
	case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
		return *(enum retro_pixel_format*) data == RETRO_PIXEL_FORMAT_RGB565;
	case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS:
		return true;
	case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
		((struct retro_log_callback*) data)->log = logPrintf;
		return true;
	case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
		/* once, as a frontend does after the first frame's options are set */
		*(bool*) data = !variablesReported;
		variablesReported = true;
		return true;
	default:
		return false;
	}
}

static void videoRefresh(const void* data, unsigned width, unsigned height, size_t pitch) {
	if (!data) {
		return;
	}
	if (!lastFrame || width * height > lastWidth * lastHeight) {
		free(lastFrame);
		lastFrame = malloc(width * height * sizeof(uint16_t));
	}
	unsigned y;
	for (y = 0; y < height; ++y) {
		memcpy(&lastFrame[y * width], (const uint8_t*) data + y * pitch, width * sizeof(uint16_t));
	}
	lastWidth = width;
	lastHeight = height;
}

static size_t audioBatch(const int16_t* data, size_t frames) {
	(void) data;
	return frames;
}

static void audioSample(int16_t left, int16_t right) {
	(void) left;
	(void) right;
}

static void inputPoll(void) {
}

static int16_t inputState(unsigned port, unsigned device, unsigned index, unsigned id) {
	(void) index;
	if (device != RETRO_DEVICE_JOYPAD || port > 1) {
		return 0;
	}
	uint16_t buttons = mash(currentFrame, port);
	if (id == RETRO_DEVICE_ID_JOYPAD_MASK) {
		return (int16_t) buttons;
	}
	return (buttons >> id) & 1;
}

static void writeShot(const char* path) {
	if (!lastFrame) {
		die("no picture to write");
	}
	unsigned rowSize = (lastWidth * 3 + 3) & ~3u;
	size_t size = 54 + (size_t) rowSize * lastHeight;
	uint8_t* bmp = calloc(1, size);
	bmp[0] = 'B';
	bmp[1] = 'M';
	memcpy(&bmp[2], &(uint32_t) { (uint32_t) size }, 4);
	memcpy(&bmp[10], &(uint32_t) { 54 }, 4);
	memcpy(&bmp[14], &(uint32_t) { 40 }, 4);
	memcpy(&bmp[18], &(int32_t) { (int32_t) lastWidth }, 4);
	memcpy(&bmp[22], &(int32_t) { -(int32_t) lastHeight }, 4); /* top-down */
	memcpy(&bmp[26], &(uint16_t) { 1 }, 2);
	memcpy(&bmp[28], &(uint16_t) { 24 }, 2);
	unsigned x, y;
	for (y = 0; y < lastHeight; ++y) {
		for (x = 0; x < lastWidth; ++x) {
			uint16_t c = lastFrame[y * lastWidth + x];
			uint8_t* p = &bmp[54 + y * rowSize + x * 3];
			p[0] = (c & 0x1F) << 3;
			p[1] = ((c >> 5) & 0x3F) << 2;
			p[2] = (c >> 11) << 3;
		}
	}
	writeFile(path, bmp, size);
	free(bmp);
}

static void printHash(long frame) {
	uint64_t hash;
	if (pairHash) {
		hash = pairHash();
	} else {
		size_t size = serializeSize();
		void* state = malloc(size);
		if (!serialize(state, size)) {
			die("retro_serialize failed");
		}
		hash = fnv(FNV_OFFSET, state, size);
		free(state);
	}
	printf("frame %ld %016llx\n", frame, (unsigned long long) hash);
}

static void saveState(const char* path) {
	size_t size = serializeSize();
	void* state = malloc(size);
	if (!serialize(state, size)) {
		die("retro_serialize failed");
	}
	writeFile(path, state, size);
	free(state);
}

static bool parseAt(const char* arg, long* frame, const char** path) {
	const char* colon = strchr(arg, ':');
	if (!colon) {
		return false;
	}
	*frame = strtol(arg, NULL, 10);
	*path = colon + 1;
	return true;
}

int main(int argc, char** argv) {
	const char* corePath = NULL;
	const char* romPath = NULL;
	const char* savePath = NULL;
	const char* hashMode = "state";
	const char* stateIn = NULL;
	const char* stateOutPath = NULL;
	const char* shotPath = NULL;
	long frames = 600, every = 60, firstFrame = 1, stateOutFrame = -1, shotFrame = -1, resetFrame = -1;
	long saveRamAt[MAX_SAVE_RAM_AT];
	int nSaveRamAt = 0;
	int i;
	for (i = 1; i < argc; i += 2) {
		const char* arg = argv[i];
		const char* value = argv[i + 1];
		if (!value) {
			die("%s needs a value", arg);
		}
		if (strcmp(arg, "--core") == 0) {
			corePath = value;
		} else if (strcmp(arg, "--rom") == 0) {
			romPath = value;
		} else if (strcmp(arg, "--frames") == 0) {
			frames = strtol(value, NULL, 10);
		} else if (strcmp(arg, "--every") == 0) {
			every = strtol(value, NULL, 10);
		} else if (strcmp(arg, "--opt") == 0) {
			const char* eq = strchr(value, '=');
			if (!eq || nOpts == MAX_OPTS) {
				die("bad --opt %s", value);
			}
			opts[nOpts].key = strndup(value, eq - value);
			opts[nOpts].value = eq + 1;
			++nOpts;
		} else if (strcmp(arg, "--system") == 0) {
			systemDir = value;
		} else if (strcmp(arg, "--save") == 0) {
			savePath = value;
		} else if (strcmp(arg, "--hash") == 0) {
			hashMode = value;
		} else if (strcmp(arg, "--state-out") == 0) {
			if (!parseAt(value, &stateOutFrame, &stateOutPath)) {
				die("bad --state-out %s", value);
			}
		} else if (strcmp(arg, "--state-in") == 0) {
			stateIn = value;
		} else if (strcmp(arg, "--first-frame") == 0) {
			firstFrame = strtol(value, NULL, 10);
		} else if (strcmp(arg, "--save-ram-at") == 0) {
			if (nSaveRamAt == MAX_SAVE_RAM_AT) {
				die("too many --save-ram-at");
			}
			saveRamAt[nSaveRamAt++] = strtol(value, NULL, 10);
		} else if (strcmp(arg, "--shot") == 0) {
			if (!parseAt(value, &shotFrame, &shotPath)) {
				die("bad --shot %s", value);
			}
		} else if (strcmp(arg, "--count-log") == 0) {
			countText = value;
		} else if (strcmp(arg, "--reset-at") == 0) {
			resetFrame = strtol(value, NULL, 10);
		} else if (strcmp(arg, "--opposing") == 0) {
			opposing = strcmp(value, "1") == 0;
		} else {
			die("unknown option %s", arg);
		}
	}
	if (!corePath || !romPath) {
		die("--core and --rom are required");
	}

	void* lib = dlopen(corePath, RTLD_NOW | RTLD_LOCAL);
	if (!lib) {
		die("%s", dlerror());
	}
#define SYM(RET, NAME, ARGS) RET (*NAME) ARGS = (RET (*) ARGS) dlsym(lib, #NAME); if (!NAME) die("missing %s", #NAME)
	SYM(void, retro_set_environment, (retro_environment_t));
	SYM(void, retro_set_video_refresh, (retro_video_refresh_t));
	SYM(void, retro_set_audio_sample, (retro_audio_sample_t));
	SYM(void, retro_set_audio_sample_batch, (retro_audio_sample_batch_t));
	SYM(void, retro_set_input_poll, (retro_input_poll_t));
	SYM(void, retro_set_input_state, (retro_input_state_t));
	SYM(void, retro_init, (void));
	SYM(bool, retro_load_game, (const struct retro_game_info*));
	SYM(void, retro_run, (void));
	SYM(void, retro_reset, (void));
	SYM(size_t, retro_serialize_size, (void));
	SYM(bool, retro_serialize, (void*, size_t));
	SYM(bool, retro_unserialize, (const void*, size_t));
	SYM(void*, retro_get_memory_data, (unsigned));
	SYM(size_t, retro_get_memory_size, (unsigned));
	SYM(void, retro_unload_game, (void));
	SYM(void, retro_deinit, (void));
#undef SYM
	serializeSize = retro_serialize_size;
	serialize = retro_serialize;
	if (strcmp(hashMode, "pair") == 0) {
		pairHash = (uint64_t (*)(void)) dlsym(lib, "nx_pair_hash");
		if (!pairHash) {
			die("the core has no nx_pair_hash");
		}
	}

	retro_set_environment(environment);
	retro_set_video_refresh(videoRefresh);
	retro_set_audio_sample(audioSample);
	retro_set_audio_sample_batch(audioBatch);
	retro_set_input_poll(inputPoll);
	retro_set_input_state(inputState);
	retro_init();

	size_t romSize;
	void* rom = readFile(romPath, &romSize);
	struct retro_game_info game = { .path = romPath, .data = rom, .size = romSize };
	if (!retro_load_game(&game)) {
		die("retro_load_game failed");
	}
	if (savePath) {
		size_t saveSize;
		void* save = readFile(savePath, &saveSize);
		size_t room = retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
		memcpy(retro_get_memory_data(RETRO_MEMORY_SAVE_RAM), save, saveSize < room ? saveSize : room);
		free(save);
	}
	if (stateIn) {
		size_t size;
		void* state = readFile(stateIn, &size);
		if (!retro_unserialize(state, size)) {
			die("retro_unserialize failed");
		}
		free(state);
		/* the loaded state's own hash, as the saving run printed it */
		printHash(firstFrame - 1);
		if (stateOutFrame == firstFrame - 1) {
			saveState(stateOutPath);
		}
	}

	long last = firstFrame + frames - 1;
	for (currentFrame = firstFrame; currentFrame <= last; ++currentFrame) {
		retro_run();
		if (currentFrame == stateOutFrame) {
			saveState(stateOutPath);
		}
		if (currentFrame == shotFrame) {
			writeShot(shotPath);
		}
		if (currentFrame == resetFrame) {
			retro_reset();
		}
		int j;
		for (j = 0; j < nSaveRamAt; ++j) {
			if (currentFrame == saveRamAt[j]) {
				size_t size = retro_get_memory_size(RETRO_MEMORY_SAVE_RAM);
				printf("save_ram %ld %zu %016llx\n", currentFrame, size,
				       (unsigned long long) fnv(FNV_OFFSET, retro_get_memory_data(RETRO_MEMORY_SAVE_RAM), size));
			}
		}
		if (currentFrame % every == 0 || currentFrame == last) {
			printHash(currentFrame);
		}
	}
	if (countText) {
		printf("log_count %ld\n", countHits);
	}
	fflush(stdout);
	retro_unload_game();
	retro_deinit();
	free(rom);
	return 0;
}
