// DS Download Play: lists the Download Play games hosted nearby, downloads the one picked and starts it. The core (our
// melonDS DS patch) runs the protocol; this program shows what it hears and says which host to join, through the
// mailbox (mailbox.h). Drawn as the 3DS client's top screen is, on the top screen only: black, light text, the selected
// row a light pill, button hints bottom right.
#include <nds.h>
#include <stdio.h>
#include <string.h>

#include "draw.h"
#include "mailbox.h"
#include "ui.h"

#define BG RGB15(0, 0, 0)
#define INK RGB15(27, 27, 27)
#define HINT RGB15(19, 19, 19)
#define PILL RGB15(28, 28, 29)
#define ON_PILL RGB15(0, 0, 0)
#define LINE RGB15(5, 5, 5)
#define GOOD RGB15(16, 27, 16)

#define ROWS 5
#define ROW_Y 22
#define ROW_H 28
// The picked game's name on the download screen: the title face, larger, as the 3DS client's big face.
#define BIG 130

static volatile NxDlpMailbox box __attribute__((aligned(32)));
// The core knows this program by the magic in its binary, so it stays a plain constant, not folded into code.
static const char magic[sizeof(box.magic)] __attribute__((used)) = NXDLP_MAGIC;

static int selected, firstRow;
static u32 frame;
// The offer joined: kept, as the list under it can change while it downloads.
static NxDlpOffer joined;

static void send(u32 command, const u8* mac) {
    if (mac) memcpy((void*)box.joinMac, mac, sizeof(box.joinMac));
    box.command = command;
    box.clientSeq = box.clientSeq + 1;
}

typedef struct { const char* key; const char* action; } Hint;

// The app's button hints: each key in a light pill, its action after it, laid out from the right edge.
static void hints(const Hint* h, int n) {
    int x = 256 - 10;
    const int y = 192 - 20;
    for (int i = n - 1; i >= 0; i--) {
        x -= textWidth(&font3d_small, h[i].action);
        text(&font3d_small, x, y, h[i].action, INK);
        const int kw = textWidth(&font3d_small, h[i].key);
        const int pw = kw + 10 < 15 ? 15 : kw + 10;
        x -= 4 + pw;
        roundRect(x, y - 1, pw, 15, INK);
        text(&font3d_small, x + (pw - kw) / 2, y, h[i].key, BG);
        x -= 12;
    }
}

static void drawList(int count) {
    char buf[64], fit[64], host[16];
    for (int row = 0; row < ROWS && firstRow + row < count; row++) {
        const int i = firstRow + row;
        const NxDlpOffer* o = (const NxDlpOffer*)&box.offers[i];
        const int y = ROW_Y + row * ROW_H;
        const bool on = i == selected;
        if (on) roundRect(6, y, 244, ROW_H - 2, PILL);
        icon(i, o->icon, 12, y + 3, 20);
        ui_ascii(host, sizeof host, o->hostName, 10);
        const int hw = textWidth(&font3d_small, host);
        text(&font3d_small, 242 - hw, y + 7, host, on ? ON_PILL : HINT);
        ui_ascii(buf, sizeof buf, o->gameName, 48);
        fitText(&font3d_title, 100, buf, 242 - hw - 10 - 38, fit, sizeof fit);
        text(&font3d_title, 38, y + 4, fit, on ? ON_PILL : INK);
    }
}

static void drawSession(UiScreen screen) {
    char buf[64], fit[64];
    icon(ICON_SLOTS - 1, joined.icon, 16, 32, 48);
    ui_ascii(buf, sizeof buf, joined.gameName, 48);
    fitText(&font3d_title, BIG, buf, 166, fit, sizeof fit);
    textZoom(&font3d_title, BIG, 74, 36, fit, INK);
    ui_ascii(buf, sizeof buf, joined.hostName, 10);
    text(&font3d_small, 74, 62, buf, HINT);
    if (screen == UI_FAILED) {
        text(&font3d_body, 16, 100, ui_failure_text(box.reason), INK);
        return;
    }
    const int pct = ui_percent(box.done, box.total);
    if (screen == UI_STARTING) snprintf(buf, sizeof buf, "Starting...");
    else if (screen == UI_JOINING || box.total == 0) snprintf(buf, sizeof buf, "Waiting for the host to start the game.");
    else snprintf(buf, sizeof buf, "Downloading  %d%%", pct);
    text(&font3d_body, 16, 100, buf, screen == UI_STARTING ? GOOD : HINT);
    roundRect(16, 124, 224, 5, LINE);
    const int fill = screen == UI_STARTING ? 224 : 224 * pct / 100;
    if (fill >= 5) roundRect(16, 124, fill, 5, PILL);
}

static void draw(UiScreen screen, int count) {
    frameBegin();
    text(&font3d_small, 10, 6, "DS Download Play", HINT);
    if (screen == UI_LOOKING) {
        const char* dots[] = {"", ".", "..", "..."};
        char line[48];
        snprintf(line, sizeof line, "Looking for games nearby%s", dots[(frame / 30) % 4]);
        text(&font3d_title, 16, 70, line, INK);
        text(&font3d_body, 16, 94, "Ask the other player to start", HINT);
        text(&font3d_body, 16, 110, "Download Play in their game.", HINT);
        Hint h[] = {{"START", "Quit"}};
        hints(h, 1);
    } else if (screen == UI_LIST) {
        drawList(count);
        Hint h[] = {{"A", "Download"}, {"START", "Quit"}};
        hints(h, 2);
    } else {
        drawSession(screen);
        if (screen == UI_FAILED) {
            Hint h[] = {{"A", "Back to the list"}, {"START", "Quit"}};
            hints(h, 2);
        } else if (screen != UI_STARTING) {
            Hint h[] = {{"B", "Back"}, {"START", "Quit"}};
            hints(h, 2);
        }
    }
    frameEnd();
}

int main(void) {
    memcpy((void*)box.magic, (const void* volatile)magic, sizeof(box.magic));
    drawInit();
    u32 seenSeq = ~0u;
    while (pmMainLoop()) {
        scanKeys();
        const u32 down = keysDown();
        // As on the 3DS client, a tap anywhere is A.
        const bool confirm = down & (KEY_A | KEY_TOUCH);
        if (down & KEY_START) systemShutDown();
        const UiScreen screen = ui_screen((const NxDlpMailbox*)&box);
        const int count = ui_offer_count((const NxDlpMailbox*)&box);
        if (box.coreSeq != seenSeq) {
            seenSeq = box.coreSeq;
            selected = ui_clamp(selected, count);
        }
        if (screen == UI_LIST) {
            if (down & KEY_DOWN) selected = (selected + 1) % count;
            if (down & KEY_UP) selected = (selected + count - 1) % count;
            if (selected < firstRow) firstRow = selected;
            if (selected >= firstRow + ROWS) firstRow = selected - ROWS + 1;
            if (confirm) {
                memcpy(&joined, (const void*)&box.offers[selected], sizeof joined);
                send(NXDLP_CMD_JOIN, joined.mac);
            }
        } else if ((screen == UI_JOINING || screen == UI_DOWNLOADING) && (down & KEY_B)) {
            send(NXDLP_CMD_CANCEL, NULL);
        } else if (screen == UI_FAILED && confirm) {
            send(NXDLP_CMD_CANCEL, NULL);
        }
        draw(screen, count);
        frame++;
        swiWaitForVBlank();
    }
    return 0;
}
