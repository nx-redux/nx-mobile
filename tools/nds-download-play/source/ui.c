#include "ui.h"

UiScreen ui_screen(const NxDlpMailbox* box) {
    switch (box->state) {
    case NXDLP_JOINING: return UI_JOINING;
    case NXDLP_DOWNLOADING: return UI_DOWNLOADING;
    case NXDLP_BOOTING: return UI_STARTING;
    case NXDLP_FAILED: return UI_FAILED;
    default: return ui_offer_count(box) > 0 ? UI_LIST : UI_LOOKING;
    }
}

int ui_offer_count(const NxDlpMailbox* box) {
    return box->offerCount > NXDLP_OFFERS ? NXDLP_OFFERS : (int)box->offerCount;
}

int ui_percent(uint32_t done, uint32_t total) {
    if (total == 0) return 0;
    if (done >= total) return 100;
    return (int)(100ull * done / total);
}

int ui_clamp(int selected, int count) {
    if (count <= 0 || selected < 0) return 0;
    return selected >= count ? count - 1 : selected;
}

const char* ui_failure_text(uint32_t reason) {
    switch (reason) {
    case NXDLP_HOST_GONE: return "The host left.";
    case NXDLP_ROOM_LOST: return "Connection lost.";
    default: return "The download failed.";
    }
}

void ui_ascii(char* out, size_t room, const uint16_t* in, size_t chars) {
    size_t n = 0;
    for (; n + 1 < room && n < chars && in[n]; n++) out[n] = in[n] >= 0x20 && in[n] < 0x7F ? (char)in[n] : '?';
    out[n] = 0;
}
