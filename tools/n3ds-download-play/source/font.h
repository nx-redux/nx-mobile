// Glyphs gen_font.py renders out of the app's own font into one atlas, at FONT_SCALE times their size on screen.
#pragma once

#include <3ds.h>

#define FONT_SCALE 3

typedef struct {
    u16 x, y, w, h;      // in the atlas, at FONT_SCALE
    s16 left, top;       // from the pen (top: from the line's top), at FONT_SCALE
    u16 advance;         // at FONT_SCALE
} Glyph;

typedef struct {
    const Glyph* glyphs; // ASCII 32-126
    float line_height;   // on screen
    float ascent;
} Font;

extern const u16 font_atlas_w, font_atlas_h;
// A white disc in the atlas, disc_d across, for round corners.
extern const u16 disc_x, disc_y, disc_d;
extern const u8 font_atlas[];
extern const Font font_small, font_body, font_title, font_big;
