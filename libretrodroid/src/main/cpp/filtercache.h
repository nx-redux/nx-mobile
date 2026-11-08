/*
 * The filter last set on a texture within one draw, so setting the same one again can be skipped. Only valid while
 * every filter change on that texture goes through it: the owner invalidates it at the start of each draw (a texture
 * may have been re-created, and a new one can reuse a deleted one's name) and after any code that sets the filter on
 * its own.
 */
#pragma once

namespace libretrodroid {

class TextureFilterCache {
public:
    // True when texture does not have filter yet: the caller sets it now, and it is remembered as set.
    bool needsSet(unsigned texture, int filter) {
        if (known && texture == lastTexture && filter == lastFilter) return false;
        known = true;
        lastTexture = texture;
        lastFilter = filter;
        return true;
    }

    void invalidate() { known = false; }

private:
    bool known = false;
    unsigned lastTexture = 0;
    int lastFilter = 0;
};

}
