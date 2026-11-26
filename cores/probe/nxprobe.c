/*
 * nx-mobile - nxprobe: loads a libretro core without running a game and
 * writes the option table it declares as JSON.
 * Copyright (C) 2026 Mohammad Syuhada.
 *
 * Ported from NX Redux workspace/all/minarch/ma_opts_dump.c (same author).
 *
 *   nxprobe <core.so> <out.json> <systemdir> [game]
 *
 * Exit 0 when a table was written, 2 when the core declared no options, 1 on
 * a usage, load, missing-symbol or write error, or when a game was given and
 * retro_load_game returned false (even if a table was written before that).
 *
 * NXPROBE_SKIP_INIT=1 (no game only) skips retro_init/retro_deinit, for a core
 * that declares its table in retro_set_environment and whose retro_init cannot
 * run outside an app (PPSSPP probes Vulkan there, which crashes a shell process).
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file at the repository root.
 */
#define _POSIX_C_SOURCE 200809L

#include "libretro.h"
#include "opts_json.h"

#include <dlfcn.h>
#include <errno.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

/* Keys never written to the table: per-game options stay in game. */
static const char *const dropped_prefixes[] = {"fbneo-dipswitch-", NULL};

static const char *out_path;
static const char *system_dir;
static char *save_dir;
static char *core_name;
static char emulator[256];
static int wrote;
static int write_failed;

/* The latest capture, unfiltered: GET_VARIABLE answers from it, so a core
 * reading a dropped (DIP) key still gets its declared default. Superseded
 * captures are kept until exit because a core may hold a returned value. */
static OptSchema *captured;
static OptSchema **retired;
static int n_retired;

static void probe_log(enum retro_log_level level, const char *fmt, ...) {
   (void)level;
   va_list args;
   va_start(args, fmt);
   vfprintf(stderr, fmt, args);
   va_end(args);
}

/* Replaces the in-memory table and rewrites the JSON at once, so a core that
 * crashes later still leaves its table. */
static void capture(OptSchema *(*build)(const void *, const char *, const char *), const void *data) {
   OptSchema *full = build(data, core_name, emulator);
   OptSchema *table = build(data, core_name, emulator);
   if (!full || !table) {
      schema_free(full);
      schema_free(table);
      return;
   }
   if (captured) {
      OptSchema **grown = realloc(retired, (size_t)(n_retired + 1) * sizeof(*grown));
      if (grown) {
         retired = grown;
         retired[n_retired++] = captured;
      }
   }
   captured = full;

   for (int i = 0; dropped_prefixes[i]; i++)
      schema_drop_prefix(table, dropped_prefixes[i]);
   if (schema_item_count(table) > 0) {
      if (schema_write_json(table, out_path) == 0)
         wrote = 1;
      else
         write_failed = 1;
   } else {
      fprintf(stderr, "nxprobe: capture holds no options to write\n");
   }
   schema_free(table);
}

static OptSchema *build_v1(const void *data, const char *core, const char *emu) {
   return schema_from_v1((const struct retro_core_option_definition *)data, core, emu);
}
static OptSchema *build_v2(const void *data, const char *core, const char *emu) {
   return schema_from_v2((const struct retro_core_options_v2 *)data, core, emu);
}
static OptSchema *build_vars(const void *data, const char *core, const char *emu) {
   return schema_from_vars((const struct retro_variable *)data, core, emu);
}

/* Answers what option registration needs, and accepts (and ignores) the setters
 * a real retro_load_game makes, so a core that declares its options at load
 * time gets that far. Anything else is refused, notably SET_HW_RENDER and
 * GET_PREFERRED_HW_RENDER, so a core falls back to software rendering. */
static bool probe_env(unsigned cmd, void *data) {
   switch (cmd) {
   case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:
      if (!data)
         return false;
      *(unsigned *)data = 2;
      return true;
   case RETRO_ENVIRONMENT_SET_CORE_OPTIONS:
      if (data)
         capture(build_v1, data);
      return true;
   case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_INTL: {
      const struct retro_core_options_intl *intl = data;
      if (intl && intl->us)
         capture(build_v1, intl->us);
      return true;
   }
   case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2:
      if (data)
         capture(build_v2, data);
      return true;
   case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2_INTL: {
      const struct retro_core_options_v2_intl *intl = data;
      if (intl && intl->us)
         capture(build_v2, intl->us);
      return true;
   }
   case RETRO_ENVIRONMENT_SET_VARIABLES:
      if (data)
         capture(build_vars, data);
      return true;
   case RETRO_ENVIRONMENT_GET_VARIABLE: {
      struct retro_variable *var = data;
      if (!var || !var->key)
         return false;
      var->value = schema_default(captured, var->key);
      return var->value != NULL;
   }
   case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
      struct retro_log_callback *cb = data;
      if (!cb)
         return false;
      cb->log = probe_log;
      return true;
   }
   case RETRO_ENVIRONMENT_GET_LANGUAGE:
      if (!data)
         return false;
      *(unsigned *)data = RETRO_LANGUAGE_ENGLISH;
      return true;
   case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
      if (!data)
         return false;
      *(const char **)data = system_dir;
      return true;
   case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
      if (!data)
         return false;
      *(const char **)data = save_dir;
      return true;
   case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
   case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
   case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
   case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME:
   case RETRO_ENVIRONMENT_SET_MEMORY_MAPS:
   case RETRO_ENVIRONMENT_SET_SUBSYSTEM_INFO:
   case RETRO_ENVIRONMENT_SET_GEOMETRY:
   case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO:
   case RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS:
   case RETRO_ENVIRONMENT_SET_PERFORMANCE_LEVEL:
   case RETRO_ENVIRONMENT_SET_ROTATION:
   case RETRO_ENVIRONMENT_SET_SERIALIZATION_QUIRKS:
      return true;
   case RETRO_ENVIRONMENT_SET_HW_RENDER:
   case RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER:
   default:
      return false;
   }
}

/* <dirname(out)>/nxprobe-save, created. */
static char *make_save_dir(const char *out) {
   const char *slash = strrchr(out, '/');
   size_t dlen = slash ? (size_t)(slash - out) : 1;
   const char *dir = slash ? out : ".";
   if (slash && dlen == 0)
      dlen = 1; /* "/x.json" -> "/" */
   size_t len = dlen + sizeof("/nxprobe-save");
   char *path = malloc(len);
   if (!path)
      return NULL;
   snprintf(path, len, "%.*s/nxprobe-save", (int)dlen, dir);
   if (mkdir(path, 0755) != 0 && errno != EEXIST)
      fprintf(stderr, "nxprobe: cannot create %s: %s\n", path, strerror(errno));
   return path;
}

static void *read_file(const char *path, size_t *size) {
   FILE *f = fopen(path, "rb");
   if (!f)
      return NULL;
   void *buf = NULL;
   long len;
   if (fseek(f, 0, SEEK_END) == 0 && (len = ftell(f)) >= 0 && fseek(f, 0, SEEK_SET) == 0) {
      buf = malloc(len > 0 ? (size_t)len : 1);
      if (buf && fread(buf, 1, (size_t)len, f) != (size_t)len) {
         free(buf);
         buf = NULL;
      } else if (buf) {
         *size = (size_t)len;
      }
   }
   fclose(f);
   return buf;
}

/* dlsym into a function pointer without a data-to-function cast warning. */
static int resolve(void *handle, const char *name, void *fn_out, size_t fn_size) {
   void *sym = dlsym(handle, name);
   if (!sym) {
      fprintf(stderr, "nxprobe: missing symbol %s\n", name);
      return 0;
   }
   memcpy(fn_out, &sym, fn_size);
   return 1;
}

int main(int argc, char **argv) {
   if (argc < 4 || argc > 5) {
      fprintf(stderr, "usage: %s <core.so> <out.json> <systemdir> [game]\n", argv[0]);
      return 1;
   }
   const char *core_path = argv[1];
   const char *game = argc == 5 ? argv[4] : NULL;
   out_path = argv[2];
   system_dir = argv[3];
   core_name = schema_core_name(core_path);
   save_dir = make_save_dir(out_path);
   if (!core_name || !save_dir) {
      fprintf(stderr, "nxprobe: out of memory\n");
      return 1;
   }

   void *handle = dlopen(core_path, RTLD_NOW | RTLD_LOCAL);
   if (!handle) {
      fprintf(stderr, "nxprobe: dlopen %s failed: %s\n", core_path, dlerror());
      return 1;
   }

   void (*get_system_info)(struct retro_system_info *);
   void (*set_environment)(retro_environment_t);
   void (*init)(void);
   void (*deinit)(void);
   bool (*load_game)(const struct retro_game_info *) = NULL;
   void (*unload_game)(void) = NULL;
   int ok = resolve(handle, "retro_get_system_info", &get_system_info, sizeof(get_system_info));
   ok &= resolve(handle, "retro_set_environment", &set_environment, sizeof(set_environment));
   ok &= resolve(handle, "retro_init", &init, sizeof(init));
   ok &= resolve(handle, "retro_deinit", &deinit, sizeof(deinit));
   if (game) {
      ok &= resolve(handle, "retro_load_game", &load_game, sizeof(load_game));
      ok &= resolve(handle, "retro_unload_game", &unload_game, sizeof(unload_game));
   }
   if (!ok) {
      dlclose(handle);
      return 1;
   }

   struct retro_system_info info;
   memset(&info, 0, sizeof(info));
   get_system_info(&info);
   snprintf(emulator, sizeof(emulator), "%s", info.library_name ? info.library_name : "");
   bool need_fullpath = info.need_fullpath;

   const char *skip = getenv("NXPROBE_SKIP_INIT");
   int skip_init = !game && skip && *skip && strcmp(skip, "0") != 0;

   set_environment(probe_env);
   if (!skip_init)
      init();

   int rc_load = 0;
   if (game) {
      struct retro_game_info gi;
      memset(&gi, 0, sizeof(gi));
      gi.path = game;
      void *data = NULL;
      if (!need_fullpath) {
         data = read_file(game, &gi.size);
         if (!data) {
            fprintf(stderr, "nxprobe: cannot read %s\n", game);
            rc_load = 1;
         }
         gi.data = data;
      }
      if (!rc_load) {
         if (load_game(&gi)) {
            unload_game();
         } else {
            /* A table captured before the failure may be partial: exit 1 so
             * a failed load never reads as a probe that worked. */
            fprintf(stderr, "nxprobe: retro_load_game(%s) returned false\n", game);
            rc_load = 1;
         }
      }
      free(data);
   }

   if (!skip_init)
      deinit();

   schema_free(captured);
   for (int i = 0; i < n_retired; i++)
      schema_free(retired[i]);
   free(retired);
   free(save_dir);
   free(core_name);

   int rc = 0;
   if (rc_load || (write_failed && !wrote))
      rc = 1;
   else if (!wrote) {
      fprintf(stderr, "nxprobe: %s declared no options\n", core_path);
      rc = 2;
   }
   /* Left loaded and ended with _exit: a core that keeps its library loaded for good (Cemu) aborts in the static
    * destructors that dlclose and a normal exit run. */
   fflush(NULL);
   _exit(rc);
}
