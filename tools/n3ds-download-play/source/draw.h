// Software drawing on the 3DS framebuffers (BGR8, stored rotated: column-major, bottom row first).
#pragma once

#include "font.h"

typedef struct {
    u8* px;
    int w, h; // as seen: 400x240 top, 320x240 bottom
} Canvas;

typedef struct { u8 r, g, b; } Rgb;

Canvas canvasOf(gfxScreen_t screen);
void clear(Canvas c, Rgb color);
void rect(Canvas c, int x, int y, int w, int h, Rgb color);
// A filled rectangle with round corners of radius r, edges antialiased.
void roundRect(Canvas c, int x, int y, int w, int h, int r, Rgb color);
// Text with its top at y; returns its width.
int text(Canvas c, const Font* f, int x, int y, const char* s, Rgb color);
int textWidth(const Font* f, const char* s);
// A 48x48 3DS icon (RGB565 in 8x8 Morton-ordered tiles) at scale.
void icon(Canvas c, const u16* src, int x, int y, int scale);
