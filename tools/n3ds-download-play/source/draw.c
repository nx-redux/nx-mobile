#include <string.h>

#include "draw.h"

#define ICON_SLOTS 8

static C3D_RenderTarget *top, *bottom;
static C3D_Tex atlas;
static C3D_Tex icon_tex[ICON_SLOTS];
// What each slot holds, by content: a list slot's host can change under the same address.
static u32 icon_hash[ICON_SLOTS];

static u32 iconHash(const u16* src) {
    u32 h = 2166136261u;
    for (int i = 0; i < 48 * 48; i++) h = (h ^ src[i]) * 16777619u;
    return h | 1; // never 0, an empty slot's
}

// Where (x, y) of a w-wide texture lands in the GPU's tiled layout: 8x8 tiles, Morton order inside each.
static inline u32 tiled(u32 x, u32 y, u32 w) {
    return (((y >> 3) * (w >> 3) + (x >> 3)) << 6) |
           ((x & 1) | ((y & 1) << 1) | ((x & 2) << 1) | ((y & 2) << 2) | ((x & 4) << 2) | ((y & 4) << 3));
}

void drawInit(void) {
    gfxInitDefault();
    C3D_Init(C3D_DEFAULT_CMDBUF_SIZE);
    C2D_Init(C2D_DEFAULT_MAX_OBJECTS * 4);
    C2D_Prepare();
    top = C2D_CreateScreenTarget(GFX_TOP, GFX_LEFT);
    bottom = C2D_CreateScreenTarget(GFX_BOTTOM, GFX_LEFT);

    // The atlas as white with the glyphs' alpha (RGBA8, as every renderer reads it alike).
    C3D_TexInit(&atlas, font_atlas_w, font_atlas_h, GPU_RGBA8);
    C3D_TexSetFilter(&atlas, GPU_LINEAR, GPU_LINEAR);
    u32* px = (u32*)atlas.data;
    for (u32 y = 0; y < font_atlas_h; y++)
        for (u32 x = 0; x < font_atlas_w; x++)
            px[tiled(x, y, font_atlas_w)] = 0xFFFFFF00u | font_atlas[y * font_atlas_w + x]; // RGBA
    C3D_TexFlush(&atlas);
    for (int i = 0; i < ICON_SLOTS; i++) {
        C3D_TexInit(&icon_tex[i], 64, 64, GPU_RGB565);
        C3D_TexSetFilter(&icon_tex[i], GPU_LINEAR, GPU_LINEAR);
    }
}

void drawExit(void) {
    for (int i = 0; i < ICON_SLOTS; i++) C3D_TexDelete(&icon_tex[i]);
    C3D_TexDelete(&atlas);
    C2D_Fini();
    C3D_Fini();
    gfxExit();
}

static Color clear_color;

void frameBegin(Color background) {
    clear_color = background;
    C3D_FrameBegin(C3D_FRAME_SYNCDRAW);
    C2D_TargetClear(top, background);
    C2D_TargetClear(bottom, background);
}

void onTop(void) { C2D_SceneBegin(top); }
void onBottom(void) { C2D_SceneBegin(bottom); }
void frameEnd(void) { C3D_FrameEnd(0); }

void rect(float x, float y, float w, float h, Color c) { C2D_DrawRectSolid(x, y, 0, w, h, c); }

void roundRect(float x, float y, float w, float h, float r, Color c) {
    if (r * 2 > h) r = h / 2;
    if (r * 2 > w) r = w / 2;
    C2D_DrawRectSolid(x + r, y, 0, w - 2 * r, h, c);
    C2D_DrawRectSolid(x, y + r, 0, r, h - 2 * r, c);
    C2D_DrawRectSolid(x + w - r, y + r, 0, r, h - 2 * r, c);
    // The corners from the atlas's disc: smooth at any scale the emulator renders at.
    Tex3DS_SubTexture sub = {disc_d, disc_d, (float)disc_x / font_atlas_w, 1.0f - (float)disc_y / font_atlas_h,
                             (float)(disc_x + disc_d) / font_atlas_w, 1.0f - (float)(disc_y + disc_d) / font_atlas_h};
    C2D_Image img = {&atlas, &sub};
    C2D_ImageTint tint;
    C2D_PlainImageTint(&tint, c, 1.0f);
    float s = 2 * r / disc_d;
    C2D_DrawImageAt(img, x, y, 0, &tint, s, s);
    C2D_DrawImageAt(img, x + w - 2 * r, y, 0, &tint, s, s);
    C2D_DrawImageAt(img, x, y + h - 2 * r, 0, &tint, s, s);
    C2D_DrawImageAt(img, x + w - 2 * r, y + h - 2 * r, 0, &tint, s, s);
}

static const Glyph* glyphOf(const Font* f, char ch) {
    unsigned u = (unsigned char)ch;
    return &f->glyphs[(u < 32 || u > 126 ? '?' : u) - 32];
}

float textWidth(const Font* f, const char* s) {
    float w = 0;
    for (; *s; s++) w += glyphOf(f, *s)->advance;
    return w / FONT_SCALE;
}

float text(const Font* f, float x, float y, const char* s, Color c) {
    C2D_ImageTint tint;
    C2D_PlainImageTint(&tint, c, 1.0f);
    float pen = x;
    for (; *s; s++) {
        const Glyph* g = glyphOf(f, *s);
        if (g->w) {
            Tex3DS_SubTexture sub = {
                g->w, g->h,
                (float)g->x / font_atlas_w, 1.0f - (float)g->y / font_atlas_h,
                (float)(g->x + g->w) / font_atlas_w, 1.0f - (float)(g->y + g->h) / font_atlas_h,
            };
            C2D_Image img = {&atlas, &sub};
            C2D_DrawImageAt(img, pen + (float)g->left / FONT_SCALE, y + (float)g->top / FONT_SCALE, 0, &tint,
                            1.0f / FONT_SCALE, 1.0f / FONT_SCALE);
        }
        pen += (float)g->advance / FONT_SCALE;
    }
    return pen - x;
}

void icon(int slot, const u16* src, float x, float y, float size) {
    if (slot < 0 || slot >= ICON_SLOTS) return;
    C3D_Tex* t = &icon_tex[slot];
    u32 h = iconHash(src);
    if (icon_hash[slot] != h) {
        // Decode the icon's own tiling, then lay it out as the top-left 48x48 of a 64x64 texture.
        u16* px = (u16*)t->data;
        memset(px, 0, 64 * 64 * 2);
        int n = 0;
        for (int ty = 0; ty < 48; ty += 8)
            for (int tx = 0; tx < 48; tx += 8)
                for (int k = 0; k < 64; k++, n++) {
                    int ix = tx + ((k & 1) | ((k >> 1) & 2) | ((k >> 2) & 4));
                    int iy = ty + (((k >> 1) & 1) | ((k >> 2) & 2) | ((k >> 3) & 4));
                    px[tiled(ix, iy, 64)] = src[n];
                }
        C3D_TexFlush(t);
        icon_hash[slot] = h;
    }
    Tex3DS_SubTexture sub = {48, 48, 0.0f, 1.0f, 48.0f / 64, 1.0f - 48.0f / 64};
    C2D_Image img = {t, &sub};
    C2D_DrawImageAt(img, x, y, 0, NULL, size / 48, size / 48);
}

