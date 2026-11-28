// The client's screen model, from the mailbox the core fills in.
extern "C" {
#include "ui.h"
}
#include <cstdio>
#include <cstring>

extern int failures;
#define CHECK(...) do { if (!(__VA_ARGS__)) { std::printf("  FAIL %s:%d: %s\n", __FILE__, __LINE__, #__VA_ARGS__); failures++; } } while (0)

void ui_state_from_mailbox() {
    NxDlpMailbox box {};
    CHECK(ui_screen(&box) == UI_LOOKING);
    box.state = NXDLP_SCANNING;
    CHECK(ui_screen(&box) == UI_LOOKING);
    box.offerCount = 2;
    CHECK(ui_screen(&box) == UI_LIST);
    box.state = NXDLP_JOINING;
    CHECK(ui_screen(&box) == UI_JOINING);
    box.state = NXDLP_DOWNLOADING;
    CHECK(ui_screen(&box) == UI_DOWNLOADING);
    box.state = NXDLP_BOOTING;
    CHECK(ui_screen(&box) == UI_STARTING);
    box.state = NXDLP_FAILED;
    box.reason = NXDLP_HOST_GONE;
    CHECK(ui_screen(&box) == UI_FAILED);
    CHECK(std::strcmp(ui_failure_text(box.reason), "The host left.") == 0);
    CHECK(std::strcmp(ui_failure_text(NXDLP_ROOM_LOST), "Connection lost.") == 0);
    CHECK(std::strcmp(ui_failure_text(NXDLP_BAD_DOWNLOAD), "The download failed.") == 0);
    box.offerCount = 99; // more than the mailbox holds
    box.state = NXDLP_SCANNING;
    CHECK(ui_offer_count(&box) == NXDLP_OFFERS);
}

void ui_progress_percent() {
    CHECK(ui_percent(1264, 2528) == 50);
    CHECK(ui_percent(0, 0) == 0);
    CHECK(ui_percent(3000, 2528) == 100);
}

void ui_selection_clamps() {
    CHECK(ui_clamp(3, 2) == 1);
    CHECK(ui_clamp(-1, 2) == 0);
    CHECK(ui_clamp(1, 0) == 0);
}

void ui_ascii_from_ucs2() {
    const uint16_t name[10] = {'K', 'o', 't', 'a', 'k', 'N', 'o', 'v', 'a', 'X'}; // ten characters, no NUL
    char out[16];
    ui_ascii(out, sizeof out, name, 10);
    CHECK(std::strcmp(out, "KotakNovaX") == 0);
    const uint16_t accented[] = {'P', 'o', 'k', 0xE9, 0};
    ui_ascii(out, sizeof out, accented, 5);
    CHECK(std::strcmp(out, "Pok?") == 0);
}
