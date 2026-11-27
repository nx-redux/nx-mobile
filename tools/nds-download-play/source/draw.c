#include <nds.h>
#include <string.h>

#include "draw.h"

// Vertices are in half pixels, so a glyph drawn at twice its size maps one atlas pixel to one unit.
#define UNIT 2

static int atlasTex;
static int iconTex[ICON_SLOTS];
static u32 iconHash[ICON_SLOTS];
static int depth, polyId;

static u32 hashIcon(const u8* icon) {
    u32 h = 2166136261u;
    for (int i = 0; i < 0x220; i++) h = (h ^ icon[i]) * 16777619u;
    return h | 1; // never 0, an empty slot's
}

void drawInit(void) {
    videoSetMode(MODE_0_3D);
    videoSetModeSub(MODE_0_2D);
    lcdMainOnTop();
    BG_PALETTE_SUB[0] = RGB15(0, 0, 0);
    vramSetBankA(VRAM_A_TEXTURE);
    vramSetBankB(VRAM_B_TEXTURE);
    vramSetBankD(VRAM_D_TEXTURE);
    vramSetBankE(VRAM_E_TEX_PALETTE);

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

void frameBegin(void) {
    depth = 0;
    polyId = 0;
    glMatrixMode(GL_MODELVIEW);
    glLoadIdentity();
}

void frameEnd(void) {
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

void roundRect(int x, int y, int w, int h, u16 color) {
    nextPoly();
    glBindTexture(0, atlasTex);
    const int d = h * UNIT, r = d / 2, half = disc_d / 2;
    // The ends: halves of the atlas's disc; the middle: its centre column stretched.
    quad(x * UNIT, y * UNIT, r, d, disc_x, disc_y, half, disc_d, color);
    quad(x * UNIT + w * UNIT - r, y * UNIT, r, d, disc_x + half, disc_y, disc_d - half, disc_d, color);
    if (w * UNIT > d) quad(x * UNIT + r, y * UNIT, w * UNIT - d, d, disc_x + half - 1, disc_y, 2, disc_d, color);
}

void textZoom(const Font* f, int zoom, int x, int y, const char* s, u16 color) {
    nextPoly();
    glBindTexture(0, atlasTex);
    // Units per atlas pixel, in percent: a face drawn at UNIT times its size is 100.
    const int k = zoom * UNIT / f->scale;
    int pen = x * UNIT * 100;
    for (; *s; s++) {
        const char c = *s;
        if (c < 32 || c > 126) continue;
        const Glyph* g = &f->glyphs[c - 32];
        if (g->w)
            quad((pen + g->left * k) / 100, (y * UNIT * 100 + g->top * k) / 100, g->w * k / 100, g->h * k / 100,
                 g->x, g->y, g->w, g->h, color);
        pen += g->advance * k;
    }
}

void text(const Font* f, int x, int y, const char* s, u16 color) { textZoom(f, 100, x, y, s, color); }

void icon(int slot, const u8* icon, int x, int y, int size) {
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

int textWidthZoom(const Font* f, int zoom, const char* s) {
    int w = 0;
    for (; *s; s++)
        if (*s >= 32 && *s <= 126) w += f->glyphs[*s - 32].advance;
    return (w * zoom / 100 + f->scale - 1) / f->scale;
}

int textWidth(const Font* f, const char* s) { return textWidthZoom(f, 100, s); }

void fitText(const Font* f, int zoom, const char* s, int width, char* out, int room) {
    int n = (int)strlen(s);
    if (n > room - 1) n = room - 1;
    memcpy(out, s, n);
    out[n] = 0;
    if (textWidthZoom(f, zoom, out) <= width) return;
    while (n > 0) {
        n--;
        if (n + 4 > room) continue;
        memcpy(out + n, "...", 4);
        if (textWidthZoom(f, zoom, out) <= width) return;
    }
}
