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

// Positions are the 3DS client's as both show on a 4:3 handheld (x 0.64, y 0.64 + 19), so the two look the same there.
#define ROWS 5
#define ROW_Y 41
#define ROW_H 23
// The picked game's name on the download screen: the title face, larger, as the 3DS client's big face.
#define BIG 130

static volatile NxDlpMailbox box __attribute__((aligned(32)));
// The core knows this program by the magic in its binary, so it stays a plain constant, not folded into code. One byte
// past a 32-byte boundary: the core looks for the mailbox only on such boundaries, and must never take this copy for it.
static const struct { char pad; char magic[sizeof(box.magic)]; } mark __attribute__((aligned(32), used)) = {0, NXDLP_MAGIC};

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
    int x = 256 - 9;
    const int y = 157;
    for (int i = n - 1; i >= 0; i--) {
        x -= textWidth(&font3d_small, h[i].action);
        text(&font3d_small, x, y, h[i].action, INK);
        const int kw = textWidth(&font3d_small, h[i].key);
        const int pw = kw + 8 < 12 ? 12 : kw + 8;
        x -= 3 + pw;
        roundRect(x, y - 1, pw, 12, INK);
        text(&font3d_small, x + (pw - kw) / 2, y, h[i].key, BG);
        x -= 10;
    }
}

static void drawList(int count) {
    char buf[64], fit[64], host[16];
    for (int row = 0; row < ROWS && firstRow + row < count; row++) {
        const int i = firstRow + row;
        const NxDlpOffer* o = (const NxDlpOffer*)&box.offers[i];
        const int y = ROW_Y + row * ROW_H;
        const bool on = i == selected;
        if (on) roundRect(6, y, 243, ROW_H - 1, PILL);
        icon(i, o->icon, 12, y + 2, 18);
        ui_ascii(host, sizeof host, o->hostName, 10);
        const int hw = textWidth(&font3d_small, host);
        text(&font3d_small, 241 - hw, y + 6, host, on ? ON_PILL : HINT);
        ui_ascii(buf, sizeof buf, o->gameName, 48);
        fitText(&font3d_title, 100, buf, 241 - hw - 8 - 36, fit, sizeof fit);
        text(&font3d_title, 36, y + 4, fit, on ? ON_PILL : INK);
    }
}

static void drawSession(UiScreen screen) {
    char buf[64], fit[64];
    icon(ICON_SLOTS - 1, joined.icon, 15, 45, 46);
    ui_ascii(buf, sizeof buf, joined.gameName, 48);
    fitText(&font3d_title, BIG, buf, 170, fit, sizeof fit);
    textZoom(&font3d_title, BIG, 72, 50, fit, INK);
    ui_ascii(buf, sizeof buf, joined.hostName, 10);
    text(&font3d_small, 72, 73, buf, HINT);
    if (screen == UI_FAILED) {
        text(&font3d_body, 15, 109, ui_failure_text(box.reason), INK);
        return;
    }
    const int pct = ui_percent(box.done, box.total);
    if (screen == UI_STARTING) snprintf(buf, sizeof buf, "Starting...");
    else if (screen == UI_JOINING || box.total == 0) snprintf(buf, sizeof buf, "Waiting for the host to start the game.");
    else snprintf(buf, sizeof buf, "Downloading  %d%%", pct);
    text(&font3d_body, 15, 109, buf, screen == UI_STARTING ? GOOD : HINT);
    roundRect(15, 128, 225, 4, LINE);
    const int fill = screen == UI_STARTING ? 225 : 225 * pct / 100;
    if (fill >= 4) roundRect(15, 128, fill, 4, PILL);
}

static void draw(UiScreen screen, int count) {
    frameBegin();
    text(&font3d_small, 10, 26, "DS Download Play", HINT);
    if (screen == UI_LOOKING) {
        const char* dots[] = {"", ".", "..", "..."};
        char line[48];
        snprintf(line, sizeof line, "Looking for games nearby%s", dots[(frame / 30) % 4]);
        text(&font3d_title, 15, 77, line, INK);
        text(&font3d_body, 15, 95, "Ask the other player to start", HINT);
        text(&font3d_body, 15, 108, "local multiplayer in their game.", HINT);
    } else if (screen == UI_LIST) {
        drawList(count);
        Hint h[] = {{"A", "Download"}};
        hints(h, 1);
    } else {
        drawSession(screen);
        if (screen == UI_FAILED) {
            Hint h[] = {{"A", "Back to the list"}};
            hints(h, 1);
        } else if (screen != UI_STARTING) {
            Hint h[] = {{"B", "Back"}};
            hints(h, 1);
        }
    }
    frameEnd();
}

int main(void) {
    memcpy((void*)box.magic, (const void* volatile)mark.magic, sizeof(box.magic));
    drawInit();
    u32 seenSeq = ~0u;
    while (pmMainLoop()) {
        scanKeys();
        const u32 down = keysDown();
        // As on the 3DS client, a tap anywhere is A.
        const bool confirm = down & (KEY_A | KEY_TOUCH);
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
            // A list that shrank shows as many rows as fit.
            if (firstRow > count - ROWS) firstRow = count > ROWS ? count - ROWS : 0;
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
