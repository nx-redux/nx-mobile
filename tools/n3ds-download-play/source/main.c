// 3DS Download Play: lists the Download Play games hosted nearby, downloads the one picked and starts it.
// Top screen: text (list, progress, messages). Bottom screen: the picked game's icon and a progress bar; a tap there
// does what A does.
#include <stdio.h>
#include <string.h>

#include "dlp.h"

#define MAX_HOSTS 8

typedef enum { LISTING, DOWNLOADING, FAILED } Phase;

static DlpTitleInfo hosts[MAX_HOSTS];
static int host_count, picked;
static PrintConsole top;

static void ascii(char* out, const u16* in, int n) {
    int i = 0;
    for (; i < n - 1 && in[i]; i++) out[i] = in[i] >= 0x20 && in[i] < 0x7F ? (char)in[i] : '?';
    out[i] = 0;
}

// The bottom framebuffer is stored rotated: column-major, 240 pixels per column, bottom row first.
static void pixel(u8* fb, int x, int y, u8 r, u8 g, u8 b) {
    if (x < 0 || x >= 320 || y < 0 || y >= 240) return;
    u8* p = fb + ((x * 240) + (239 - y)) * 3;
    p[0] = b; p[1] = g; p[2] = r;
}

static void fill(u8* fb, int x0, int y0, int w, int h, u8 r, u8 g, u8 b) {
    for (int x = x0; x < x0 + w; x++)
        for (int y = y0; y < y0 + h; y++) pixel(fb, x, y, r, g, b);
}

// 3DS icons keep 8x8 tiles, each in Morton (Z) order.
static void icon(u8* fb, const u16* src, int x0, int y0, int scale) {
    int i = 0;
    for (int ty = 0; ty < 48; ty += 8)
        for (int tx = 0; tx < 48; tx += 8)
            for (int k = 0; k < 64; k++, i++) {
                int x = (k & 1) | ((k >> 1) & 2) | ((k >> 2) & 4);
                int y = ((k >> 1) & 1) | ((k >> 2) & 2) | ((k >> 3) & 4);
                u16 c = src[i];
                u8 r = (c >> 11) << 3, g = ((c >> 5) & 0x3F) << 2, b = (c & 0x1F) << 3;
                fill(fb, x0 + (tx + x) * scale, y0 + (ty + y) * scale, scale, scale, r, g, b);
            }
}

static void drawBottom(Phase phase, const DlpStatus* st) {
    u8* fb = gfxGetFramebuffer(GFX_BOTTOM, GFX_LEFT, NULL, NULL);
    fill(fb, 0, 0, 320, 240, 24, 24, 28);
    if (host_count > 0) icon(fb, hosts[picked].icon, 160 - 72, 30, 3);
    if (phase == DOWNLOADING && st && st->total > 0) {
        int w = (int)(280ULL * st->downloaded / st->total);
        fill(fb, 20, 200, 280, 14, 60, 60, 66);
        fill(fb, 20, 200, w, 14, 90, 200, 120);
    }
    gfxFlushBuffers();
    gfxSwapBuffers();
}

static void drawList(void) {
    consoleSelect(&top);
    consoleClear();
    printf("\x1b[1;2H3DS Download Play\n\n");
    if (host_count == 0) {
        printf("  Looking for games...\n\n");
        printf("  Ask the other player to start\n");
        printf("  Download Play in their game.\n");
    } else {
        for (int i = 0; i < host_count; i++) {
            char name[65];
            ascii(name, hosts[i].title_short, 64);
            printf(" %s %-34.34s\n", i == picked ? ">" : " ", name);
        }
        printf("\n  A: download    UP/DOWN: choose\n");
    }
    printf("\x1b[29;2HSTART: quit");
    drawBottom(LISTING, NULL);
}

static void drawProgress(const DlpStatus* st) {
    char name[65];
    ascii(name, hosts[picked].title_short, 64);
    consoleSelect(&top);
    consoleClear();
    printf("\x1b[1;2H3DS Download Play\n\n  %s\n\n", name);
    if (st->total == 0)
        printf("  Waiting for the host to start the game.\n");
    else
        printf("  Downloading... %lu%%\n", (unsigned long)(100ULL * st->downloaded / st->total));
    printf("\x1b[29;2HB: back    START: quit");
    drawBottom(DOWNLOADING, st);
}

static void drawMessage(const char* text) {
    consoleSelect(&top);
    consoleClear();
    printf("\x1b[1;2H3DS Download Play\n\n  %s\n\n  A: back to the list    START: quit", text);
    drawBottom(FAILED, NULL);
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

int main(void) {
    gfxInitDefault();
    consoleInit(GFX_TOP, &top);
    gfxSetDoubleBuffering(GFX_BOTTOM, false);

    if (R_FAILED(dlpOpen())) {
        drawMessage("Download Play could not start.");
        while (aptMainLoop()) {
            hidScanInput();
            if (hidKeysDown() & (KEY_START | KEY_A)) break;
            gspWaitForVBlank();
        }
        gfxExit();
        return 0;
    }
    dlpRemoveOldChildren(); // a child only works with its host: last time's is no use
    dlpStartScan();

    Phase phase = LISTING;
    DlpStatus st = {0};
    bool was_connected = false;
    u64 phase_started = osGetTime();
    u64 last_refresh = 0;
    drawList();

    while (aptMainLoop()) {
        hidScanInput();
        u32 down = hidKeysDown();
        bool tapped = (down & KEY_TOUCH) != 0;
        if (down & KEY_START) break;

        if (phase == LISTING) {
            if (osGetTime() - last_refresh > 500) {
                last_refresh = osGetTime();
                int before = host_count;
                refreshHosts();
                if (host_count != before) drawList();
            }
            if (host_count > 0 && (down & KEY_DOWN)) { picked = (picked + 1) % host_count; drawList(); }
            if (host_count > 0 && (down & KEY_UP)) { picked = (picked + host_count - 1) % host_count; drawList(); }
            if (host_count > 0 && ((down & KEY_A) || tapped)) {
                dlpStopScan();
                if (R_SUCCEEDED(dlpStartSession(hosts[picked].mac, dlpChildOf(&hosts[picked])))) {
                    phase = DOWNLOADING;
                    was_connected = false;
                    phase_started = osGetTime();
                    memset(&st, 0, sizeof st);
                    drawProgress(&st);
                } else {
                    phase = FAILED;
                    drawMessage("Could not reach that game. Try again.");
                }
            }
        } else if (phase == DOWNLOADING) {
            DlpStatus now;
            if (R_SUCCEEDED(dlpStatus(&now))) {
                if (now.state != st.state || now.downloaded != st.downloaded || now.total != st.total) {
                    st = now;
                    drawProgress(&st);
                }
                was_connected = was_connected || now.connected;
                if (now.state == DLP_STATE_COMPLETE) {
                    u8 pass[9];
                    u8 mac[6];
                    memcpy(mac, hosts[picked].mac, 6);
                    u64 child = dlpChildOf(&hosts[picked]);
                    dlpPassphrase(pass);
                    dlpStopSession();
                    consoleSelect(&top);
                    consoleClear();
                    printf("\x1b[1;2H3DS Download Play\n\n  Starting...\n");
                    dlpStartChild(mac, pass, child);
                    // The restart takes over from here.
                    for (int i = 0; i < 600 && aptMainLoop(); i++) gspWaitForVBlank();
                    phase = FAILED;
                    drawMessage("The game did not start.");
                } else if ((was_connected && !now.connected) ||
                           (!now.connected && osGetTime() - phase_started > 10000)) {
                    dlpStopSession();
                    phase = FAILED;
                    drawMessage(was_connected ? "The host left." : "Could not reach that game. Try again.");
                }
            }
            if (down & KEY_B) {
                dlpStopSession();
                phase = LISTING;
                host_count = 0;
                picked = 0;
                dlpStartScan();
                drawList();
            }
        } else { // FAILED
            if ((down & KEY_A) || tapped) {
                phase = LISTING;
                host_count = 0;
                picked = 0;
                dlpStartScan();
                drawList();
            }
        }
        gspWaitForVBlank();
    }

    dlpStopSession();
    dlpClose();
    gfxExit();
    return 0;
}
