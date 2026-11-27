// Glyphs gen_font.py renders out of the app's own font into one atlas (2 bits a texel), at each face's scale.
#ifndef NXDLP_FONT_H
#define NXDLP_FONT_H

#include <nds/ndstypes.h>

typedef struct {
    u16 x, y, w, h;  // in the atlas, at the face's scale
    s16 left, top;   // from the pen (top: from the line's top), at the face's scale
    u16 advance;     // at the face's scale
} Glyph;

typedef struct {
    const Glyph* glyphs; // ASCII 32-126
    u8 scale;            // atlas pixels per screen pixel
    s16 line_height;     // on screen
    s16 ascent;
} Font;

extern const u16 font3d_w, font3d_h;
// A white disc in the atlas, disc_d across, for round corners.
extern const u16 disc_x, disc_y, disc_d;
extern const u8 font3d_atlas[];
extern const Font font3d_small, font3d_body, font3d_title;

#endif
