#include <nds.h>
#include <string.h>

#include "draw.h"

// Bottom-screen vertices are in half pixels, so a glyph drawn at twice its size maps one atlas pixel to one unit.
#define UNIT 2

static int atlasTex;
static int iconTex[ICON_SLOTS];
static u32 iconHash[ICON_SLOTS];
static int depth, polyId;
static u16 topBuf[256 * 192];
static u16* topVram;

static u32 hashIcon(const u8* icon) {
    u32 h = 2166136261u;
    for (int i = 0; i < 0x220; i++) h = (h ^ icon[i]) * 16777619u;
    return h | 1; // never 0, an empty slot's
}

void drawInit(void) {
    videoSetMode(MODE_0_3D);
    videoSetModeSub(MODE_5_2D);
    lcdMainOnBottom();
    vramSetBankA(VRAM_A_TEXTURE);
    vramSetBankB(VRAM_B_TEXTURE);
    vramSetBankC(VRAM_C_SUB_BG);
    vramSetBankD(VRAM_D_TEXTURE);
    vramSetBankE(VRAM_E_TEX_PALETTE);
    int bg = bgInitSub(3, BgType_Bmp16, BgSize_B16_256x256, 0, 0);
    topVram = bgGetGfxPtr(bg);

    glInit();
    glEnable(GL_TEXTURE_2D | GL_BLEND | GL_ANTIALIAS);
    glClearColor(0, 0, 0, 31);
    glClearPolyID(63);
    glClearDepth(GL_MAX_DEPTH);
    glViewport(0, 0, 255, 191);
    glMatrixMode(GL_PROJECTION);
    glLoadIdentity();
    glOrthof32(0, 256 * UNIT, 192 * UNIT, 0, -4096, 4096);
    glMatrixMode(GL_MODELVIEW);
    glLoadIdentity();

    // The atlas is white with the glyphs' alpha: one palette entry, 5-bit alpha per texel.
    static const u16 white[8] = {RGB15(31, 31, 31)};
    glGenTextures(1, &atlasTex);
    glBindTexture(0, atlasTex);
    glTexImage2D(0, 0, GL_RGB8_A5, TEXTURE_SIZE_1024, TEXTURE_SIZE_256, 0, TEXGEN_TEXCOORD, font3d_atlas);
    glColorTableEXT(0, 0, 8, 0, 0, white);
    glGenTextures(ICON_SLOTS, iconTex);
}

void bottomBegin(void) {
    depth = 0;
    polyId = 0;
    glMatrixMode(GL_MODELVIEW);
    glLoadIdentity();
}

void bottomEnd(void) {
    // In the order drawn: later shapes over earlier ones.
    glFlush(GL_TRANS_MANUALSORT);
}

static void nextPoly(void) {
    // Translucent shapes with one id never draw over each other: each gets its own.
    polyId = polyId % 62 + 1;
    glPolyFmt(POLY_ALPHA(31) | POLY_CULL_NONE | POLY_ID(polyId));
    depth += 2;
}

static void quad(int x, int y, int w, int h, int u, int v, int tw, int th, u16 color) {
    glColor(color);
    glBegin(GL_QUADS);
    glTexCoord2t16(inttot16(u), inttot16(v));
    glVertex3v16(x, y, depth);
    glTexCoord2t16(inttot16(u), inttot16(v + th));
    glVertex3v16(x, y + h, depth);
    glTexCoord2t16(inttot16(u + tw), inttot16(v + th));
    glVertex3v16(x + w, y + h, depth);
    glTexCoord2t16(inttot16(u + tw), inttot16(v));
    glVertex3v16(x + w, y, depth);
    glEnd();
}

void bottomRect(int x, int y, int w, int h, u16 color) {
    nextPoly();
    glBindTexture(0, 0);
    quad(x * UNIT, y * UNIT, w * UNIT, h * UNIT, 0, 0, 0, 0, color);
}

void bottomPill(int x, int y, int w, int h, u16 color) {
    nextPoly();
    glBindTexture(0, atlasTex);
    const int d = h * UNIT, r = d / 2, half = disc_d / 2;
    // The ends: halves of the atlas's disc; the middle: its centre column stretched.
    quad(x * UNIT, y * UNIT, r, d, disc_x, disc_y, half, disc_d, color);
    quad(x * UNIT + w * UNIT - r, y * UNIT, r, d, disc_x + half, disc_y, disc_d - half, disc_d, color);
    if (w * UNIT > d) quad(x * UNIT + r, y * UNIT, w * UNIT - d, d, disc_x + half - 1, disc_y, 2, disc_d, color);
}

void bottomText(const Font* f, int x, int y, const char* s, u16 color) {
    nextPoly();
    glBindTexture(0, atlasTex);
    int pen = x * f->scale;
    for (; *s; s++) {
        const char c = *s;
        if (c < 32 || c > 126) continue;
        const Glyph* g = &f->glyphs[c - 32];
        if (g->w) quad(pen + g->left, y * f->scale + g->top, g->w, g->h, g->x, g->y, g->w, g->h, color);
        pen += g->advance;
    }
}

void bottomIcon(int slot, const u8* icon, int x, int y, int size) {
    if (slot < 0 || slot >= ICON_SLOTS) return;
    const u32 h = hashIcon(icon);
    if (iconHash[slot] != h) {
        // Cartridge icons are 8x8 tiles; a texture is rows: the same 4bpp texels, rearranged.
        static u8 linear[32 * 32 / 2];
        const u8* tiles = icon + 0x20;
        for (int ty = 0; ty < 4; ty++)
            for (int tx = 0; tx < 4; tx++)
                for (int row = 0; row < 8; row++)
                    memcpy(&linear[((ty * 8 + row) * 32 + tx * 8) / 2], &tiles[(ty * 4 + tx) * 32 + row * 4], 4);
        glBindTexture(0, iconTex[slot]);
        glTexImage2D(0, 0, GL_RGB16, TEXTURE_SIZE_32, TEXTURE_SIZE_32, 0, TEXGEN_TEXCOORD | GL_TEXTURE_COLOR0_TRANSPARENT, linear);
        glColorTableEXT(0, 0, 16, 0, 0, (const u16*)icon);
        iconHash[slot] = h;
    }
    nextPoly();
    glBindTexture(0, iconTex[slot]);
    quad(x * UNIT, y * UNIT, size * UNIT, size * UNIT, 0, 0, 32, 32, RGB15(31, 31, 31));
}

int textWidth(const Font* f, const char* s) {
    int w = 0;
    for (; *s; s++)
        if (*s >= 32 && *s <= 126) w += f->glyphs[*s - 32].advance;
    return (w + f->scale - 1) / f->scale;
}

void fitText(const Font* f, const char* s, int width, char* out, int room) {
    int n = (int)strlen(s);
    if (n > room - 1) n = room - 1;
    memcpy(out, s, n);
    out[n] = 0;
    if (textWidth(f, out) <= width) return;
    while (n > 0) {
        n--;
        if (n + 4 > room) continue;
        memcpy(out + n, "...", 4);
        if (textWidth(f, out) <= width) return;
    }
}

void topClear(u16 color) {
    const u16 c = color | BIT(15);
    for (int i = 0; i < 256 * 192; i++) topBuf[i] = c;
}

void topRect(int x, int y, int w, int h, u16 color) {
    for (int j = y < 0 ? 0 : y; j < y + h && j < 192; j++)
        for (int i = x < 0 ? 0 : x; i < x + w && i < 256; i++) topBuf[j * 256 + i] = color | BIT(15);
}

static void blend(int x, int y, u16 color, int alpha) {
    if (x < 0 || x >= 256 || y < 0 || y >= 192 || alpha == 0) return;
    u16* p = &topBuf[y * 256 + x];
    const int r0 = *p & 31, g0 = (*p >> 5) & 31, b0 = (*p >> 10) & 31;
    const int r1 = color & 31, g1 = (color >> 5) & 31, b1 = (color >> 10) & 31;
    *p = BIT(15) | ((r0 + (r1 - r0) * alpha / 255)) | ((g0 + (g1 - g0) * alpha / 255) << 5) |
         ((b0 + (b1 - b0) * alpha / 255) << 10);
}

void topPill(int x, int y, int w, int h, u16 color) {
    const int r = h / 2;
    for (int j = 0; j < h; j++)
        for (int i = 0; i < w; i++) {
            // Coverage at the round ends, from the distance to the end's centre, 4x over each pixel.
            const int cx = i < r ? r : (i >= w - r ? w - r - 1 : i);
            int hits = 0;
            for (int s = 0; s < 4; s++) {
                const int dx = (i * 2 + (s & 1)) - (cx * 2 + 1), dy = (j * 2 + (s >> 1)) - (r * 2 - 1);
                hits += dx * dx + dy * dy <= r * r * 4;
            }
            blend(x + i, y + j, color, hits * 255 / 4);
        }
}

void topText(const Font* f, int x, int y, const char* s, u16 color) {
    int pen = x;
    for (; *s; s++) {
        if (*s < 32 || *s > 126) continue;
        const Glyph* g = &f->glyphs[*s - 32];
        for (int j = 0; j < g->h; j++)
            for (int i = 0; i < g->w; i++)
                blend(pen + g->left + i, y + g->top + j, color, font2d_atlas[(g->y + j) * font2d_w + g->x + i]);
        pen += g->advance;
    }
}

void topIcon(const u8* icon, int x, int y, int scale) {
    const u16* palette = (const u16*)icon;
    const u8* tiles = icon + 0x20;
    for (int py = 0; py < 32; py++)
        for (int px = 0; px < 32; px++) {
            const u8 b = tiles[((py / 8) * 4 + px / 8) * 32 + (py % 8) * 4 + (px % 8) / 2];
            const int c = px & 1 ? b >> 4 : b & 15;
            if (c == 0) continue; // transparent
            topRect(x + px * scale, y + py * scale, scale, scale, palette[c]);
        }
}

void topPresent(void) {
    DC_FlushRange(topBuf, sizeof topBuf);
    dmaCopy(topBuf, topVram, sizeof topBuf);
}
