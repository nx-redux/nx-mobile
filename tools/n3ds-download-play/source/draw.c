#include "draw.h"

Canvas canvasOf(gfxScreen_t screen) {
    Canvas c = {gfxGetFramebuffer(screen, GFX_LEFT, NULL, NULL), screen == GFX_TOP ? 400 : 320, 240};
    return c;
}

static inline u8* at(Canvas c, int x, int y) { return c.px + ((x * 240) + (239 - y)) * 3; }

static inline void blend(Canvas c, int x, int y, Rgb col, int a) {
    if (x < 0 || x >= c.w || y < 0 || y >= c.h || a <= 0) return;
    u8* p = at(c, x, y);
    if (a >= 255) { p[0] = col.b; p[1] = col.g; p[2] = col.r; return; }
    p[0] = (u8)((col.b * a + p[0] * (255 - a)) / 255);
    p[1] = (u8)((col.g * a + p[1] * (255 - a)) / 255);
    p[2] = (u8)((col.r * a + p[2] * (255 - a)) / 255);
}

void clear(Canvas c, Rgb color) { rect(c, 0, 0, c.w, c.h, color); }

void rect(Canvas c, int x, int y, int w, int h, Rgb color) {
    for (int i = x < 0 ? 0 : x; i < x + w && i < c.w; i++)
        for (int j = y < 0 ? 0 : y; j < y + h && j < c.h; j++) {
            u8* p = at(c, i, j);
            p[0] = color.b; p[1] = color.g; p[2] = color.r;
        }
}

void roundRect(Canvas c, int x, int y, int w, int h, int r, Rgb color) {
    for (int j = 0; j < h; j++)
        for (int i = 0; i < w; i++) {
            // Distance from the nearest corner centre, for pixels in a corner square.
            int cx = i < r ? r : i >= w - r ? w - r - 1 : i;
            int cy = j < r ? r : j >= h - r ? h - r - 1 : j;
            int a = 255;
            if (cx != i || cy != j) {
                float dx = i + 0.5f - (cx + 0.5f), dy = j + 0.5f - (cy + 0.5f);
                float d = dx * dx + dy * dy, rr = (float)r;
                if (d >= (rr + 0.5f) * (rr + 0.5f)) continue;
                if (d > (rr - 0.5f) * (rr - 0.5f)) {
                    // Coverage of the edge pixel, roughly linear across the last pixel.
                    float dist = __builtin_sqrtf(d);
                    a = (int)((rr + 0.5f - dist) * 255.0f);
                }
            }
            blend(c, x + i, y + j, color, a);
        }
}

int textWidth(const Font* f, const char* s) {
    int w = 0;
    for (; *s; s++) {
        unsigned ch = (unsigned char)*s;
        if (ch < 32 || ch > 126) ch = '?';
        w += f->glyphs[ch - 32].advance;
    }
    return w;
}

int text(Canvas c, const Font* f, int x, int y, const char* s, Rgb color) {
    int pen = x;
    for (; *s; s++) {
        unsigned ch = (unsigned char)*s;
        if (ch < 32 || ch > 126) ch = '?';
        const Glyph* g = &f->glyphs[ch - 32];
        const u8* a = f->data + g->offset;
        for (int j = 0; j < g->h; j++)
            for (int i = 0; i < g->w; i++) blend(c, pen + g->left + i, y + g->top + j, color, a[j * g->w + i]);
        pen += g->advance;
    }
    return pen - x;
}

void icon(Canvas c, const u16* src, int x0, int y0, int scale) {
    int n = 0;
    for (int ty = 0; ty < 48; ty += 8)
        for (int tx = 0; tx < 48; tx += 8)
            for (int k = 0; k < 64; k++, n++) {
                int x = (k & 1) | ((k >> 1) & 2) | ((k >> 2) & 4);
                int y = ((k >> 1) & 1) | ((k >> 2) & 2) | ((k >> 3) & 4);
                u16 v = src[n];
                Rgb col = {(u8)((v >> 11) << 3), (u8)(((v >> 5) & 0x3F) << 2), (u8)((v & 0x1F) << 3)};
                for (int i = 0; i < scale; i++)
                    for (int j = 0; j < scale; j++) blend(c, x0 + (tx + x) * scale + i, y0 + (ty + y) * scale + j, col, 255);
            }
}
