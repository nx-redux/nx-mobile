// Drawing for the client. The bottom screen is the 3D engine's (sharp at the emulator's 3D resolution); the top screen
// is a 2D bitmap at the DS's own resolution. Colours are RGB15.
#ifndef NXDLP_DRAW_H
#define NXDLP_DRAW_H

#include <nds/ndstypes.h>

#include "font.h"

#define ICON_SLOTS 8

void drawInit(void);

// Bottom screen.
void bottomBegin(void);
void bottomEnd(void);
void bottomRect(int x, int y, int w, int h, u16 color);
void bottomPill(int x, int y, int w, int h, u16 color);
void bottomText(const Font* f, int x, int y, const char* s, u16 color);
/// [icon]: a cartridge icon (palette, then 4bpp tiles), kept in [slot] while it stays the same.
void bottomIcon(int slot, const u8* icon, int x, int y, int size);

// Top screen: drawn into memory, shown by topPresent.
void topClear(u16 color);
void topRect(int x, int y, int w, int h, u16 color);
void topPill(int x, int y, int w, int h, u16 color);
void topText(const Font* f, int x, int y, const char* s, u16 color);
void topIcon(const u8* icon, int x, int y, int scale);
void topPresent(void);

/// On screen, in pixels.
int textWidth(const Font* f, const char* s);
/// [s] cut with "..." to fit [width] on screen, into [out].
void fitText(const Font* f, const char* s, int width, char* out, int room);

#endif
