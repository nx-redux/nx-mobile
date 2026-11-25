/*
 * nx-mobile - core option tables captured by nxprobe, and their JSON writer.
 * Copyright (C) 2026 Mohammad Syuhada.
 *
 * Ported from NX Redux workspace/all/minarch/ma_opts_schema.c (same author).
 * Kept: the V1 / V2 / SET_VARIABLES capture rules (V2 uncategorized options
 * first, then one section per category in declaration order; "Desc; a|b|c"
 * with the first value as default; value-less options skipped).
 * Dropped: the int min/max/step compaction (every value is listed), the
 * 64-item section split and cJSON.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file at the repository root.
 */
#define _POSIX_C_SOURCE 200809L

#include "opts_json.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static char *dup_or_empty(const char *s) {
   return strdup(s ? s : "");
}

static int values_count(const struct retro_core_option_value *values) {
   int n = 0;
   if (values)
      while (n < RETRO_NUM_CORE_OPTION_VALUES_MAX && values[n].value)
         n++;
   return n;
}

static void item_free(OptItem *it) {
   free(it->key);
   free(it->label);
   free(it->label_cat);
   free(it->info);
   free(it->def);
   for (int i = 0; i < it->n; i++) {
      free(it->values[i]);
      free(it->labels[i]);
   }
   free(it->values);
   free(it->labels);
}

static void section_free(OptSection *sec) {
   for (int i = 0; i < sec->n; i++)
      item_free(&sec->items[i]);
   free(sec->items);
   free(sec->key);
   free(sec->label);
   free(sec->info);
}

void schema_free(OptSchema *schema) {
   if (!schema)
      return;
   for (int i = 0; i < schema->n; i++)
      section_free(&schema->sections[i]);
   free(schema->sections);
   free(schema->core);
   free(schema->emulator);
   free(schema);
}

static OptSchema *schema_new(const char *core, const char *emulator) {
   OptSchema *s = calloc(1, sizeof(*s));
   if (!s)
      return NULL;
   s->core = dup_or_empty(core);
   s->emulator = dup_or_empty(emulator);
   if (!s->core || !s->emulator) {
      schema_free(s);
      return NULL;
   }
   return s;
}

/* Appends a section; NULL on allocation failure. */
static OptSection *schema_add_section(OptSchema *s, const char *key, const char *label, const char *info) {
   OptSection *grown = realloc(s->sections, (size_t)(s->n + 1) * sizeof(*grown));
   if (!grown)
      return NULL;
   s->sections = grown;
   OptSection *sec = &s->sections[s->n];
   memset(sec, 0, sizeof(*sec));
   sec->key = dup_or_empty(key);
   sec->label = dup_or_empty(label);
   sec->info = dup_or_empty(info);
   s->n++;
   if (!sec->key || !sec->label || !sec->info)
      return NULL;
   return sec;
}

/* Appends an item with n values (labels may be NULL per value). A NULL label
 * falls back to the key, as in NX Redux. 0 on success, -1 on allocation
 * failure; a value-less option is skipped (returns 0). */
static int section_add_item(OptSection *sec, const char *key, const char *label, const char *label_cat,
                            const char *info, const struct retro_core_option_value *values, int n,
                            const char *def) {
   if (!key)
      return 0;
   if (n <= 0) {
      fprintf(stderr, "nxprobe: skipping option \"%s\" (no values)\n", key);
      return 0;
   }
   OptItem *grown = realloc(sec->items, (size_t)(sec->n + 1) * sizeof(*grown));
   if (!grown)
      return -1;
   sec->items = grown;
   OptItem *it = &sec->items[sec->n];
   memset(it, 0, sizeof(*it));
   sec->n++;
   it->key = strdup(key);
   it->label = dup_or_empty(label ? label : key);
   it->label_cat = dup_or_empty(label_cat);
   it->info = dup_or_empty(info);
   it->def = dup_or_empty(def ? def : values[0].value);
   it->values = calloc((size_t)n, sizeof(char *));
   it->labels = calloc((size_t)n, sizeof(char *));
   if (!it->key || !it->label || !it->label_cat || !it->info || !it->def || !it->values || !it->labels)
      return -1;
   for (int i = 0; i < n; i++) {
      it->values[i] = dup_or_empty(values[i].value);
      it->labels[i] = dup_or_empty(values[i].label);
      it->n = i + 1;
      if (!it->values[i] || !it->labels[i])
         return -1;
   }
   return 0;
}

/* Drops sections without items. */
static void schema_compact(OptSchema *s) {
   int w = 0;
   for (int r = 0; r < s->n; r++) {
      if (s->sections[r].n == 0)
         section_free(&s->sections[r]);
      else
         s->sections[w++] = s->sections[r];
   }
   s->n = w;
}

static OptSchema *fail(OptSchema *s) {
   fprintf(stderr, "nxprobe: out of memory building the option table\n");
   schema_free(s);
   return NULL;
}

OptSchema *schema_from_v1(const struct retro_core_option_definition *defs, const char *core, const char *emulator) {
   OptSchema *s = schema_new(core, emulator);
   if (!s)
      return fail(NULL);
   OptSection *sec = schema_add_section(s, "", "", "");
   if (!sec)
      return fail(s);
   for (int i = 0; defs && defs[i].key; i++) {
      const struct retro_core_option_definition *d = &defs[i];
      if (section_add_item(sec, d->key, d->desc, NULL, d->info, d->values, values_count(d->values), d->default_value))
         return fail(s);
   }
   schema_compact(s);
   return s;
}

static int v2_has_category(const struct retro_core_option_v2_category *cats, const char *key) {
   if (!cats || !key)
      return 0;
   for (int i = 0; cats[i].key; i++)
      if (!strcmp(cats[i].key, key))
         return 1;
   return 0;
}

static int v2_add(OptSection *sec, const struct retro_core_option_v2_definition *d) {
   const char *info = d->info ? d->info : d->info_categorized;
   return section_add_item(sec, d->key, d->desc, d->desc_categorized, info, d->values,
                           values_count(d->values), d->default_value);
}

OptSchema *schema_from_v2(const struct retro_core_options_v2 *v2, const char *core, const char *emulator) {
   OptSchema *s = schema_new(core, emulator);
   if (!s)
      return fail(NULL);
   if (!v2 || !v2->definitions)
      return s;
   const struct retro_core_option_v2_category *cats = v2->categories;
   const struct retro_core_option_v2_definition *defs = v2->definitions;

   /* uncategorized first: no category_key, or one naming no declared category */
   OptSection *sec = schema_add_section(s, "", "", "");
   if (!sec)
      return fail(s);
   for (int i = 0; defs[i].key; i++)
      if (!v2_has_category(cats, defs[i].category_key) && v2_add(sec, &defs[i]))
         return fail(s);

   /* then one section per category, in declaration order */
   for (int c = 0; cats && cats[c].key; c++) {
      sec = schema_add_section(s, cats[c].key, cats[c].desc, cats[c].info);
      if (!sec)
         return fail(s);
      for (int i = 0; defs[i].key; i++)
         if (defs[i].category_key && !strcmp(defs[i].category_key, cats[c].key) && v2_add(sec, &defs[i]))
            return fail(s);
   }
   schema_compact(s);
   return s;
}

OptSchema *schema_from_vars(const struct retro_variable *vars, const char *core, const char *emulator) {
   OptSchema *s = schema_new(core, emulator);
   if (!s)
      return fail(NULL);
   OptSection *sec = schema_add_section(s, "", "", "");
   if (!sec)
      return fail(s);
   for (int i = 0; vars && vars[i].key; i++) {
      if (!vars[i].value)
         continue;
      /* "Desc; a|b|c": split at "; ", then at '|'. No "; " = the key is the
       * label and the whole string is the value list. */
      char *buf = strdup(vars[i].value);
      if (!buf)
         return fail(s);
      const char *label = vars[i].key;
      char *list = buf;
      char *semi = strchr(buf, ';');
      if (semi && semi[1] == ' ') {
         *semi = '\0';
         label = buf;
         list = semi + 2;
      }
      struct retro_core_option_value values[RETRO_NUM_CORE_OPTION_VALUES_MAX + 1];
      int n = 0;
      for (char *tok = list; tok && n < RETRO_NUM_CORE_OPTION_VALUES_MAX;) {
         char *bar = strchr(tok, '|');
         if (bar)
            *bar = '\0';
         values[n].value = tok;
         values[n].label = NULL;
         n++;
         tok = bar ? bar + 1 : NULL;
      }
      int rc = section_add_item(sec, vars[i].key, label, NULL, NULL, values, n, NULL);
      free(buf);
      if (rc)
         return fail(s);
   }
   schema_compact(s);
   return s;
}

void schema_drop_prefix(OptSchema *schema, const char *prefix) {
   if (!schema || !prefix || !*prefix)
      return;
   size_t plen = strlen(prefix);
   for (int si = 0; si < schema->n; si++) {
      OptSection *sec = &schema->sections[si];
      int w = 0;
      for (int r = 0; r < sec->n; r++) {
         if (!strncmp(sec->items[r].key, prefix, plen))
            item_free(&sec->items[r]);
         else
            sec->items[w++] = sec->items[r];
      }
      sec->n = w;
   }
   schema_compact(schema);
}

int schema_item_count(const OptSchema *schema) {
   int n = 0;
   for (int i = 0; schema && i < schema->n; i++)
      n += schema->sections[i].n;
   return n;
}

const char *schema_default(const OptSchema *schema, const char *key) {
   if (!schema || !key)
      return NULL;
   for (int si = 0; si < schema->n; si++)
      for (int i = 0; i < schema->sections[si].n; i++)
         if (!strcmp(schema->sections[si].items[i].key, key))
            return schema->sections[si].items[i].def;
   return NULL;
}

/* JSON string: '"' and '\' escaped, control characters as \uXXXX, other
 * bytes (UTF-8 included) copied as they are. */
static void put_str(FILE *f, const char *s) {
   fputc('"', f);
   for (const unsigned char *p = (const unsigned char *)s; *p; p++) {
      if (*p == '"' || *p == '\\')
         fprintf(f, "\\%c", *p);
      else if (*p < 0x20)
         fprintf(f, "\\u%04x", *p);
      else
         fputc(*p, f);
   }
   fputc('"', f);
}

static void put_field(FILE *f, const char *name, const char *value, int comma) {
   if (comma)
      fputc(',', f);
   put_str(f, name);
   fputc(':', f);
   put_str(f, value);
}

static void put_array(FILE *f, const char *name, char *const *list, int n) {
   fputc(',', f);
   put_str(f, name);
   fputs(":[", f);
   for (int i = 0; i < n; i++) {
      if (i)
         fputc(',', f);
      put_str(f, list[i]);
   }
   fputc(']', f);
}

/* One item per line so a committed table diffs well. */
static void put_schema(FILE *f, const OptSchema *s) {
   fputc('{', f);
   put_field(f, "core", s->core, 0);
   put_field(f, "emulator", s->emulator, 1);
   fputs(",\"sections\":[", f);
   for (int si = 0; si < s->n; si++) {
      const OptSection *sec = &s->sections[si];
      fputs(si ? ",\n{" : "\n{", f);
      put_field(f, "key", sec->key, 0);
      put_field(f, "label", sec->label, 1);
      put_field(f, "info", sec->info, 1);
      fputs(",\"items\":[", f);
      for (int i = 0; i < sec->n; i++) {
         const OptItem *it = &sec->items[i];
         fputs(i ? ",\n  {" : "\n  {", f);
         put_field(f, "key", it->key, 0);
         put_field(f, "label", it->label, 1);
         put_field(f, "labelCategorized", it->label_cat, 1);
         put_field(f, "info", it->info, 1);
         put_array(f, "values", it->values, it->n);
         put_array(f, "labels", it->labels, it->n);
         put_field(f, "default", it->def, 1);
         fputc('}', f);
      }
      fputs("]}", f);
   }
   fputs("]}\n", f);
}

int schema_write_json(const OptSchema *schema, const char *path) {
   if (!schema || !path)
      return -1;
   size_t len = strlen(path) + sizeof(".tmp");
   char *tmp = malloc(len);
   if (!tmp)
      return -1;
   snprintf(tmp, len, "%s.tmp", path);
   FILE *f = fopen(tmp, "wb");
   if (!f) {
      fprintf(stderr, "nxprobe: cannot write %s\n", tmp);
      free(tmp);
      return -1;
   }
   put_schema(f, schema);
   int ok = !ferror(f);
   if (fclose(f) != 0)
      ok = 0;
   if (ok && rename(tmp, path) != 0)
      ok = 0;
   if (!ok) {
      fprintf(stderr, "nxprobe: cannot write %s\n", path);
      remove(tmp);
   }
   free(tmp);
   return ok ? 0 : -1;
}

char *schema_core_name(const char *so_path) {
   if (!so_path)
      return NULL;
   const char *base = strrchr(so_path, '/');
   base = base ? base + 1 : so_path;
   char *name = strdup(base);
   if (!name)
      return NULL;
   char *cut = strstr(name, "_libretro");
   if (cut) {
      *cut = '\0';
   } else {
      size_t n = strlen(name);
      if (n > 3 && !strcmp(name + n - 3, ".so"))
         name[n - 3] = '\0';
   }
   return name;
}
