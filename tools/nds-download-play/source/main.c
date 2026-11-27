// DS Download Play client. The core (our melonDS DS patch) runs the protocol; this program shows what it hears and
// says which host to join, through the mailbox (mailbox.h). Drawn the way NX Redux draws its menus: black, light text,
// the selected row a light pill. The bottom screen acts (the hosts, touch and pad); the top one informs (the picked
// game, button hints).
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
#define ROW_Y 12
#define ROW_H 34

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

static void name(char* out, int room, const NxDlpOffer* o) { ui_ascii(out, room, o->gameName, 48); }

typedef struct { const char* key; const char* action; } Hint;

// The app's button hints: each key in a light pill, its action after it, laid out from the right edge.
static void hints(const Hint* h, int n) {
    int x = 256 - 10;
    const int y = 192 - 20;
    for (int i = n - 1; i >= 0; i--) {
        x -= textWidth(&font2d_small, h[i].action);
        topText(&font2d_small, x, y, h[i].action, INK);
        const int kw = textWidth(&font2d_small, h[i].key);
        const int pw = kw + 10 < 15 ? 15 : kw + 10;
        x -= 4 + pw;
        topPill(x, y - 1, pw, 15, INK);
        topText(&font2d_small, x + (pw - kw) / 2, y, h[i].key, BG);
        x -= 12;
    }
}

/// The game's details on the top screen: big icon, name, host, players and description.
static void drawOffer(const NxDlpOffer* o) {
    char line[100];
    topIcon(o->icon, 16, 30, 2);
    char title[64], fit[64];
    name(title, sizeof title, o);
    const Font* f = textWidth(&font2d_big, title) <= 148 ? &font2d_big : &font2d_title;
    fitText(f, title, 148, fit, sizeof fit);
    topText(f, 92, 32, fit, INK);
    char host[16];
    ui_ascii(host, sizeof host, o->hostName, 10);
    topText(&font2d_body, 92, 58, host, HINT);
    snprintf(line, sizeof line, "%d of %d players", o->players, o->maxPlayers);
    topText(&font2d_small, 92, 76, line, HINT);
    // The description, broken at spaces, three lines at most.
    char desc[100];
    ui_ascii(desc, sizeof desc, o->description, 96);
    const char* p = desc;
    for (int l = 0; l < 3 && *p; l++) {
        int cut = (int)strlen(p);
        char part[100];
        for (;;) {
            memcpy(part, p, cut);
            part[cut] = 0;
            if (cut <= 1 || textWidth(&font2d_body, part) <= 224) break;
            int space = cut - 1;
            while (space > 0 && p[space] != ' ') space--;
            cut = space > 0 ? space : cut - 1;
        }
        topText(&font2d_body, 16, 112 + l * 16, part, INK);
        p += cut;
        while (*p == ' ') p++;
    }
}

static void drawTop(UiScreen screen, int count) {
    topClear(BG);
    topText(&font2d_small, 12, 8, "DS Download Play", HINT);
    if (screen == UI_LIST && count > 0) {
        drawOffer((const NxDlpOffer*)&box.offers[selected]);
        Hint h[] = {{"A", "Download"}, {"START", "Quit"}};
        hints(h, 2);
    } else if (screen == UI_JOINING || screen == UI_DOWNLOADING) {
        drawOffer(&joined);
        Hint h[] = {{"B", "Cancel"}, {"START", "Quit"}};
        hints(h, 2);
    } else if (screen == UI_FAILED) {
        drawOffer(&joined);
        Hint h[] = {{"B", "Back"}, {"START", "Quit"}};
        hints(h, 2);
    } else if (screen == UI_STARTING) {
        drawOffer(&joined);
    } else {
        Hint h[] = {{"START", "Quit"}};
        hints(h, 1);
    }
    topPresent();
}

static void drawBottom(UiScreen screen, int count) {
    bottomBegin();
    char text[64];
    if (screen == UI_LOOKING) {
        const char* dots[] = {"", ".", "..", "..."};
        snprintf(text, sizeof text, "Looking for games nearby%s", dots[(frame / 30) % 4]);
        bottomText(&font3d_title, 16, 70, text, INK);
        bottomText(&font3d_body, 16, 94, "Ask the other player to start", HINT);
        bottomText(&font3d_body, 16, 110, "Download Play in their game.", HINT);
    } else if (screen == UI_LIST) {
        for (int row = 0; row < ROWS && firstRow + row < count; row++) {
            const int i = firstRow + row;
            const NxDlpOffer* o = (const NxDlpOffer*)&box.offers[i];
            const int y = ROW_Y + row * ROW_H;
            const bool on = i == selected;
            if (on) bottomPill(6, y, 244, ROW_H - 2, PILL);
            bottomIcon(i, o->icon, 14, y + 4, 24);
            char title[64];
            name(title, sizeof title, o);
            fitText(&font3d_title, title, 196, text, sizeof text);
            bottomText(&font3d_title, 46, y + 3, text, on ? ON_PILL : INK);
            char host[16];
            ui_ascii(host, sizeof host, o->hostName, 10);
            snprintf(text, sizeof text, "%s  %d/%d", host, o->players, o->maxPlayers);
            bottomText(&font3d_small, 46, y + 18, text, on ? ON_PILL : HINT);
        }
    } else {
        bottomIcon(ICON_SLOTS - 1, joined.icon, 16, 40, 48);
        char title[64];
        name(title, sizeof title, &joined);
        fitText(&font3d_title, title, 168, text, sizeof text);
        bottomText(&font3d_title, 74, 42, text, INK);
        const int pct = ui_percent(box.done, box.total);
        if (screen == UI_FAILED) {
            bottomText(&font3d_body, 74, 64, ui_failure_text(box.reason), INK);
        } else {
            if (screen == UI_STARTING) snprintf(text, sizeof text, "Starting...");
            else if (screen == UI_JOINING || box.total == 0) snprintf(text, sizeof text, "Joining...");
            else snprintf(text, sizeof text, "Downloading  %d%%", pct);
            bottomText(&font3d_body, 74, 64, text, screen == UI_STARTING ? GOOD : HINT);
            bottomPill(74, 88, 166, 6, LINE);
            const int fill = screen == UI_STARTING ? 166 : 166 * pct / 100;
            if (fill >= 6) bottomPill(74, 88, fill, 6, PILL);
        }
    }
    bottomEnd();
}

int main(void) {
    memcpy((void*)box.magic, (const void* volatile)magic, sizeof(box.magic));
    drawInit();
    u32 seenSeq = ~0u;
    while (pmMainLoop()) {
        scanKeys();
        const u32 down = keysDown();
        if (down & KEY_START) systemShutDown();
        const UiScreen screen = ui_screen((const NxDlpMailbox*)&box);
        const int count = ui_offer_count((const NxDlpMailbox*)&box);
        if (box.coreSeq != seenSeq) {
            seenSeq = box.coreSeq;
            selected = ui_clamp(selected, count);
        }
        if (screen == UI_LIST) {
            if (down & KEY_UP) selected = ui_clamp(selected - 1, count);
            if (down & KEY_DOWN) selected = ui_clamp(selected + 1, count);
            bool join = down & KEY_A;
            if (down & KEY_TOUCH) {
                touchPosition t;
                touchRead(&t);
                const int row = (t.py - ROW_Y) / ROW_H;
                if (t.py >= ROW_Y && row < ROWS && firstRow + row < count) {
                    selected = firstRow + row;
                    join = true;
                }
            }
            if (selected < firstRow) firstRow = selected;
            if (selected >= firstRow + ROWS) firstRow = selected - ROWS + 1;
            if (join && count > 0) {
                memcpy(&joined, (const void*)&box.offers[selected], sizeof joined);
                send(NXDLP_CMD_JOIN, joined.mac);
            }
        } else if ((screen == UI_JOINING || screen == UI_DOWNLOADING || screen == UI_FAILED) && (down & KEY_B)) {
            send(NXDLP_CMD_CANCEL, NULL);
        }
        drawTop(screen, count);
        drawBottom(screen, count);
        frame++;
        swiWaitForVBlank();
    }
    return 0;
}
