#include "videoviews.h"

namespace libretrodroid::VideoViews {

bool validate(const retro_video_views& in, std::vector<VideoView>& out) {
    out.clear();
    if (in.num_views == 0) return true;
    if (in.num_views > RETRO_VIDEO_VIEWS_MAX || in.views == nullptr) return false;

    unsigned eyes[RETRO_VIDEO_VIEWS_MAX][3] = {};
    unsigned maxScreen = 0;
    for (unsigned i = 0; i < in.num_views; i++) {
        const retro_video_view& v = in.views[i];
        if (v.width == 0 || v.height == 0) return false;
        if (v.screen >= RETRO_VIDEO_VIEWS_MAX || v.eye > RETRO_VIDEO_VIEW_EYE_RIGHT) return false;
        if (v.aspect_ratio > 0.0f && (v.aspect_ratio < 0.01f || v.aspect_ratio > 100.0f)) return false;
        eyes[v.screen][v.eye]++;
        if (v.screen > maxScreen) maxScreen = v.screen;
        out.push_back({ v.x, v.y, v.width, v.height, v.screen, v.eye });
    }
    for (unsigned s = 0; s <= maxScreen; s++) {
        const unsigned none = eyes[s][RETRO_VIDEO_VIEW_EYE_NONE];
        const unsigned left = eyes[s][RETRO_VIDEO_VIEW_EYE_LEFT];
        const unsigned right = eyes[s][RETRO_VIDEO_VIEW_EYE_RIGHT];
        const bool single = none == 1 && left == 0 && right == 0;
        const bool pair = none == 0 && left == 1 && right == 1;
        if (!single && !pair) { out.clear(); return false; }
        if (pair) {
            const VideoView* l = nullptr;
            const VideoView* r = nullptr;
            for (const auto& v : out) {
                if (v.screen != s) continue;
                if (v.eye == RETRO_VIDEO_VIEW_EYE_LEFT) l = &v; else if (v.eye == RETRO_VIDEO_VIEW_EYE_RIGHT) r = &v;
            }
            if (l->width != r->width || l->height != r->height) { out.clear(); return false; }
        }
    }
    return true;
}

}
