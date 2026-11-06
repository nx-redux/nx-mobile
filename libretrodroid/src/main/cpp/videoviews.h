// nx-mobile: libretro video views (RetroArch master's libretro.h, experimental): a core reports where each of its screens
// sits in its frame, and the frontend lays them out. The vendored libretro.h predates the calls.
#ifndef LIBRETRODROID_VIDEOVIEWS_H
#define LIBRETRODROID_VIDEOVIEWS_H

#include <climits>
#include <vector>
#include "../../libretro-common/include/libretro.h"

#ifndef RETRO_ENVIRONMENT_SET_VIDEO_VIEWS
#define RETRO_ENVIRONMENT_SET_VIDEO_VIEWS (95 | RETRO_ENVIRONMENT_EXPERIMENTAL)
#define RETRO_ENVIRONMENT_GET_VIDEO_VIEWS_STATUS (96 | RETRO_ENVIRONMENT_EXPERIMENTAL)
#define RETRO_VIDEO_VIEWS_STATUS_PRESENTS (1 << 0)
#define RETRO_VIDEO_VIEWS_STATUS_STEREO   (1 << 1)
#define RETRO_VIDEO_VIEWS_STATUS_HMD      (1 << 2)
#define RETRO_VIDEO_VIEW_EYE_NONE  0
#define RETRO_VIDEO_VIEW_EYE_LEFT  1
#define RETRO_VIDEO_VIEW_EYE_RIGHT 2
#define RETRO_VIDEO_VIEWS_MAX      8

enum retro_vr_reference_space {
    RETRO_VR_REFERENCE_SPACE_LOCAL = 0,
    RETRO_VR_REFERENCE_SPACE_STAGE = 1,
    RETRO_VR_REFERENCE_SPACE_DUMMY = INT_MAX
};

struct retro_video_view {
    unsigned x;
    unsigned y;
    unsigned width;
    unsigned height;
    unsigned screen;
    unsigned eye;
    float aspect_ratio;
};

struct retro_video_views {
    const struct retro_video_view *views;
    unsigned num_views;
    unsigned flags;
    enum retro_vr_reference_space reference_space;
    unsigned recommended_view_width;
    unsigned recommended_view_height;
};
#endif

namespace libretrodroid {

struct VideoView {
    unsigned x, y, width, height, screen, eye;
    bool operator==(const VideoView& o) const {
        return x == o.x && y == o.y && width == o.width && height == o.height && screen == o.screen && eye == o.eye;
    }
};

namespace VideoViews {
// The map's rules (libretro.h SET_VIDEO_VIEWS): false leaves the previous map in effect. num_views 0 is a valid clear.
bool validate(const retro_video_views& in, std::vector<VideoView>& out);
}

}

#endif
