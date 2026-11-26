// GPU drawing (citro2d): the emulator renders it at its 3D resolution, so text and shapes stay sharp when scaled up.
#pragma once

#include <citro2d.h>

#include "font.h"

typedef u32 Color; // C2D_Color32

void drawInit(void);
void drawExit(void);
// One frame: begin, then draw on the top screen, then the bottom, then end.
void frameBegin(Color background);
void onTop(void);
void onBottom(void);
void frameEnd(void);

void rect(float x, float y, float w, float h, Color c);
// A filled rectangle with round corners (radius r; h = 2r makes a pill).
void roundRect(float x, float y, float w, float h, float r, Color c);
// Text with its top at y; returns its width.
float text(const Font* f, float x, float y, const char* s, Color c);
float textWidth(const Font* f, const char* s);
// A 3DS icon (48x48 RGB565 in 8x8 Morton-ordered tiles, as SMDH and Download Play keep them) drawn size x size.
// slot: one of a few cached textures, so a list can show several icons.
void icon(int slot, const u16* src, float x, float y, float size);
