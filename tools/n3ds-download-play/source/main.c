// 3DS Download Play: lists the Download Play games hosted nearby, downloads the one picked and starts it. Drawn the
// way NX Redux draws its menus: black, light text, the selected row a light pill, button hints at the bottom.
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include "dlp.h"
#include "draw.h"

#define MAX_HOSTS 6

static const Rgb BG = {0, 0, 0};
static const Rgb INK = {0xD8, 0xD8, 0xD8};
static const Rgb HINT = {0x99, 0x99, 0x99};
static const Rgb PILL = {0xE4, 0xE4, 0xE7};
static const Rgb ON_PILL = {0, 0, 0};
static const Rgb LINE = {0x1F, 0x1F, 0x1F};
static const Rgb GOOD = {0x82, 0xDC, 0x82};

// Where the app learns which game is about to start, so it can apply that game's own settings first.
#define PICK_DIR "sdmc:/nx-redux"
#define PICK_FILE PICK_DIR "/download-play.txt"
#define PICK_ACK PICK_DIR "/download-play.ack"

typedef enum { LISTING, DOWNLOADING, STARTING, FAILED } Phase;

static DlpTitleInfo hosts[MAX_HOSTS];
static int host_count, picked;
static Phase phase;
static DlpStatus status;
static const char* message;
static u32 frame;

static void ascii(char* out, const u16* in, int n) {
    int i = 0;
    for (; i < n - 1 && in[i]; i++) out[i] = in[i] >= 0x20 && in[i] < 0x7F ? (char)in[i] : '?';
    out[i] = 0;
}

// A button glyph as the app's hints draw it: the label in a light circle, then the action.
static int hint(Canvas c, int x, int y, const char* key, const char* action) {
    int kw = textWidth(&font_small, key);
    int d = kw > 10 ? kw + 10 : 18;
    roundRect(c, x, y, d, 18, 9, INK);
    text(c, &font_small, x + (d - kw) / 2, y + 1, key, BG);
    return d + 5 + text(c, &font_small, x + d + 5, y + 1, action, INK) + 16;
}

static void hintsRight(Canvas c, const char* k1, const char* a1, const char* k2, const char* a2) {
    // Laid out right to left: measure first.
    int w = 0;
    const char* keys[2] = {k1, k2};
    const char* acts[2] = {a1, a2};
    for (int i = 0; i < 2; i++)
        if (keys[i]) {
            int kw = textWidth(&font_small, keys[i]);
            w += (kw > 10 ? kw + 10 : 18) + 5 + textWidth(&font_small, acts[i]) + 16;
        }
    int x = c.w - 12 - (w - 16);
    for (int i = 0; i < 2; i++)
        if (keys[i]) x += hint(c, x, c.h - 26, keys[i], acts[i]);
}

static void header(Canvas c, const char* title) {
    text(c, &font_small, 16, 10, title, HINT);
}

static void drawTop(void) {
    Canvas c = canvasOf(GFX_TOP);
    clear(c, BG);
    header(c, "3DS Download Play");
    char name[65];
    if (phase == LISTING) {
        if (host_count == 0) {
            const char* dots[] = {"", ".", "..", "..."};
            char line[48];
            snprintf(line, sizeof line, "Looking for games nearby%s", dots[(frame / 30) % 4]);
            text(c, &font_title, 24, 92, line, INK);
            text(c, &font_body, 24, 122, "Ask the other player to open", HINT);
            text(c, &font_body, 24, 142, "Download Play in their game.", HINT);
            return;
        }
        for (int i = 0; i < host_count; i++) {
            int y = 34 + i * 34;
            bool on = i == picked;
            if (on) roundRect(c, 10, y, 380, 32, 16, PILL);
            ascii(name, hosts[i].title_short, 64);
            text(c, &font_title, 26, y + 6, name, on ? ON_PILL : INK);
            char size[24];
            snprintf(size, sizeof size, "%lu MB", (unsigned long)((hosts[i].size + 512 * 1024) / (1024 * 1024)));
            int sw = textWidth(&font_small, size);
            text(c, &font_small, 380 - sw, y + 9, size, on ? ON_PILL : HINT);
        }
        return;
    }
    ascii(name, hosts[picked].title_short, 64);
    icon(c, hosts[picked].icon, 24, 50, 2);
    text(c, &font_big, 132, 62, name, INK);
    if (phase == FAILED) {
        text(c, &font_body, 132, 96, message, INK);
        return;
    }
    const char* line;
    char pct[40];
    if (phase == STARTING) line = "Starting...";
    else if (status.total == 0) line = "Waiting for the host to start the game.";
    else {
        snprintf(pct, sizeof pct, "Downloading  %lu%%", (unsigned long)(100ULL * status.downloaded / status.total));
        line = pct;
    }
    text(c, &font_body, 132, 96, line, phase == STARTING ? GOOD : HINT);
    roundRect(c, 132, 128, 244, 8, 4, LINE);
    int fill = status.total ? (int)(244ULL * status.downloaded / status.total) : 0;
    if (phase == STARTING) fill = 244;
    if (fill > 8) roundRect(c, 132, 128, fill, 8, 4, PILL);
}

static void drawBottom(void) {
    Canvas c = canvasOf(GFX_BOTTOM);
    clear(c, BG);
    if (phase == LISTING && host_count > 0) {
        char name[129];
        ascii(name, hosts[picked].title_long, 128);
        icon(c, hosts[picked].icon, 16, 20, 1);
        // The long title over two lines at most, broken at a space.
        char first[64] = {0};
        const char* rest = name;
        int n = (int)strlen(name);
        if (textWidth(&font_body, name) > 230) {
            int cut = n < (int)sizeof first - 1 ? n : (int)sizeof first - 1;
            while (cut > 0) {
                memcpy(first, name, cut);
                first[cut] = 0;
                if (textWidth(&font_body, first) <= 230 && (name[cut] == ' ' || name[cut] == 0)) break;
                cut--;
            }
            rest = name + cut + (name[cut] == ' ');
            text(c, &font_body, 76, 22, first, INK);
            text(c, &font_body, 76, 42, rest, INK);
        } else {
            text(c, &font_body, 76, 30, name, INK);
        }
        text(c, &font_small, 16, 84, "Sent by the host for this session. Nothing", HINT);
        text(c, &font_small, 16, 102, "of yours is needed.", HINT);
        hintsRight(c, "A", "Download", "START", "Quit");
    } else if (phase == DOWNLOADING) {
        hintsRight(c, "B", "Back", "START", "Quit");
    } else if (phase == FAILED) {
        hintsRight(c, "A", "Back to the list", "START", "Quit");
    } else if (phase == LISTING) {
        hintsRight(c, NULL, NULL, "START", "Quit");
    }
}

static void present(void) {
    drawTop();
    drawBottom();
    gfxFlushBuffers();
    gfxSwapBuffers();
}

// Adds or refreshes the hosts dlp:CLNT has seen since the last refresh.
static void refreshHosts(void) {
    static DlpTitleInfo t;
    bool restart = true;
    while (dlpNextTitle(&t, restart)) {
        restart = false;
        int i = 0;
        while (i < host_count && memcmp(hosts[i].mac, t.mac, 6) != 0) i++;
        if (i == host_count) {
            if (host_count == MAX_HOSTS) continue;
            host_count++;
        }
        hosts[i] = t;
    }
}

static void backToList(void) {
    phase = LISTING;
    host_count = 0;
    picked = 0;
    dlpStartScan();
}

static void fail(const char* text) {
    phase = FAILED;
    message = text;
}

// Tells the app the game about to start and waits briefly for it to apply that game's settings.
static void announce(u64 child) {
    mkdir(PICK_DIR, 0777);
    unlink(PICK_ACK);
    FILE* f = fopen(PICK_FILE, "w");
    if (!f) return;
    fprintf(f, "%016llX\n", child);
    fclose(f);
    for (int i = 0; i < 90 && aptMainLoop(); i++) { // up to 1.5 s
        if (access(PICK_ACK, F_OK) == 0) break;
        gspWaitForVBlank();
    }
    unlink(PICK_ACK);
}

int main(void) {
    gfxInitDefault();
    gfxSetDoubleBuffering(GFX_TOP, true);
    gfxSetDoubleBuffering(GFX_BOTTOM, true);

    if (R_FAILED(dlpOpen())) {
        fail("Download Play could not start.");
        host_count = 0;
        while (aptMainLoop()) {
            hidScanInput();
            if (hidKeysDown() & (KEY_START | KEY_A)) break;
            Canvas c = canvasOf(GFX_TOP);
            clear(c, BG);
            header(c, "3DS Download Play");
            text(c, &font_title, 24, 100, message, INK);
            Canvas b = canvasOf(GFX_BOTTOM);
            clear(b, BG);
            hintsRight(b, NULL, NULL, "START", "Quit");
            gfxFlushBuffers();
            gfxSwapBuffers();
            gspWaitForVBlank();
        }
        gfxExit();
        return 0;
    }
    dlpRemoveOldChildren(); // a child only works with its host: last time's is no use
    dlpStartScan();

    bool was_connected = false;
    u64 phase_started = osGetTime(), last_refresh = 0;

    while (aptMainLoop()) {
        hidScanInput();
        u32 down = hidKeysDown();
        bool tapped = (down & KEY_TOUCH) != 0;
        if (down & KEY_START) break;

        if (phase == LISTING) {
            if (osGetTime() - last_refresh > 500) {
                last_refresh = osGetTime();
                refreshHosts();
            }
            if (host_count > 0 && (down & KEY_DOWN)) picked = (picked + 1) % host_count;
            if (host_count > 0 && (down & KEY_UP)) picked = (picked + host_count - 1) % host_count;
            if (host_count > 0 && (down & KEY_A || tapped)) {
                dlpStopScan();
                if (R_SUCCEEDED(dlpStartSession(hosts[picked].mac, dlpChildOf(&hosts[picked])))) {
                    phase = DOWNLOADING;
                    was_connected = false;
                    phase_started = osGetTime();
                    memset(&status, 0, sizeof status);
                } else {
                    fail("Could not reach that game. Try again.");
                }
            }
        } else if (phase == DOWNLOADING) {
            DlpStatus now;
            if (R_SUCCEEDED(dlpStatus(&now))) {
                status = now;
                was_connected = was_connected || now.connected;
                if (now.state == DLP_STATE_COMPLETE) {
                    phase = STARTING;
                    present();
                    u8 pass[9], mac[6];
                    memcpy(mac, hosts[picked].mac, 6);
                    u64 child = dlpChildOf(&hosts[picked]);
                    dlpPassphrase(pass);
                    dlpStopSession();
                    announce(child);
                    dlpStartChild(mac, pass, child);
                    // The restart takes over from here.
                    for (int i = 0; i < 600 && aptMainLoop(); i++) gspWaitForVBlank();
                    fail("The game did not start.");
                } else if ((was_connected && !now.connected) || (!now.connected && osGetTime() - phase_started > 10000)) {
                    dlpStopSession();
                    fail(was_connected ? "The host left." : "Could not reach that game. Try again.");
                }
            }
            if (down & KEY_B) {
                dlpStopSession();
                backToList();
            }
        } else if (phase == FAILED) {
            if (down & KEY_A || tapped) backToList();
        }
        frame++;
        present();
        gspWaitForVBlank();
    }

    dlpStopSession();
    dlpClose();
    gfxExit();
    return 0;
}
