// Antialiased text from glyph tables gen_font.py renders out of the app's own font.
#pragma once

#include <3ds.h>

typedef struct {
    u32 offset;     // into the face's alpha data
    u8 w, h;
    s8 left, top;   // from the pen position (top: from the line's top)
    u8 advance;
} Glyph;

typedef struct {
    const Glyph* glyphs; // ASCII 32-126
    const u8* data;
    u8 line_height;
    u8 ascent;
} Font;

extern const Font font_small, font_body, font_title, font_big;
