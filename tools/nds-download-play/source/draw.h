// Drawing for the client: everything on the top screen, with the 3D engine (sharp at the emulator's 3D resolution); the
// bottom screen stays black. Colours are RGB15; [zoom] is in percent of a face's size.
#ifndef NXDLP_DRAW_H
#define NXDLP_DRAW_H

#include <nds/ndstypes.h>

#include "font.h"

#define ICON_SLOTS 9

void drawInit(void);
void frameBegin(void);
void frameEnd(void);
void roundRect(int x, int y, int w, int h, u16 color);
void text(const Font* f, int x, int y, const char* s, u16 color);
void textZoom(const Font* f, int zoom, int x, int y, const char* s, u16 color);
/// [icon]: a cartridge icon (palette, then 4bpp tiles), kept in [slot] while it stays the same.
void icon(int slot, const u8* icon, int x, int y, int size);

/// On screen, in pixels.
int textWidth(const Font* f, const char* s);
int textWidthZoom(const Font* f, int zoom, const char* s);
/// [s] cut with "..." to fit [width] on screen at [zoom], into [out].
void fitText(const Font* f, int zoom, const char* s, int width, char* out, int room);

#endif
