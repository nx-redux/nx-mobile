// 3DS Download Play: lists the Download Play games hosted nearby, downloads the one picked and starts it. Drawn the
// way NX Redux draws its menus: black, light text, the selected row a light pill, button hints bottom right of the top
// screen; the bottom screen shows the picked game.
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include "dlp.h"
#include "draw.h"

#define MAX_HOSTS 5

#define BG C2D_Color32(0x00, 0x00, 0x00, 0xFF)
#define INK C2D_Color32(0xD8, 0xD8, 0xD8, 0xFF)
#define HINT C2D_Color32(0x99, 0x99, 0x99, 0xFF)
#define PILL C2D_Color32(0xE4, 0xE4, 0xE7, 0xFF)
#define ON_PILL C2D_Color32(0x00, 0x00, 0x00, 0xFF)
#define LINE C2D_Color32(0x26, 0x26, 0x26, 0xFF)
#define GOOD C2D_Color32(0x82, 0xDC, 0x82, 0xFF)

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

typedef struct { const char* key; const char* action; } Hint;

// The app's button hints: each key in a light pill, its action after it, laid out from the right edge.
static void hints(const Hint* h, int n) {
    float x = 400 - 14, y = 240 - 24;
    for (int i = n - 1; i >= 0; i--) {
        float aw = textWidth(&font_small, h[i].action);
        x -= aw;
        text(&font_small, x, y, h[i].action, INK);
        float kw = textWidth(&font_small, h[i].key);
        float pw = kw + 12 < 18 ? 18 : kw + 12;
        x -= 5 + pw;
        roundRect(x, y - 1, pw, 18, 9, INK);
        text(&font_small, x + (pw - kw) / 2, y, h[i].key, BG);
        x -= 16;
    }
}

static void drawTop(void) {
    onTop();
    text(&font_small, 16, 10, "3DS Download Play", HINT);
    char name[65];
    if (phase == LISTING) {
        if (host_count == 0) {
            const char* dots[] = {"", ".", "..", "..."};
            char line[48];
            snprintf(line, sizeof line, "Looking for games nearby%s", dots[(frame / 30) % 4]);
            text(&font_title, 24, 90, line, INK);
            text(&font_body, 24, 118, "Ask the other player to start", HINT);
            text(&font_body, 24, 138, "local multiplayer in their game.", HINT);
            Hint h[] = {{"START", "Quit"}};
            hints(h, 1);
            return;
        }
        for (int i = 0; i < host_count; i++) {
            float y = 34 + i * 36;
            bool on = i == picked;
            if (on) roundRect(10, y, 380, 34, 17, PILL);
            icon(i, hosts[i].icon, 18, y + 3, 28);
            ascii(name, hosts[i].title_short, 64);
            text(&font_title, 56, y + 7, name, on ? ON_PILL : INK);
            char size[24];
            snprintf(size, sizeof size, "%lu MB", (unsigned long)((hosts[i].size + 512 * 1024) / (1024 * 1024)));
            text(&font_small, 376 - textWidth(&font_small, size), y + 10, size, on ? ON_PILL : HINT);
        }
        Hint h[] = {{"A", "Download"}, {"START", "Quit"}};
        hints(h, 2);
        return;
    }
    ascii(name, hosts[picked].title_short, 64);
    icon(0, hosts[picked].icon, 24, 52, 72);
    text(&font_big, 112, 60, name, INK);
    if (phase == FAILED) {
        text(&font_body, 112, 94, message, INK);
        Hint h[] = {{"A", "Back to the list"}, {"START", "Quit"}};
        hints(h, 2);
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
    text(&font_body, 112, 94, line, phase == STARTING ? GOOD : HINT);
    roundRect(112, 124, 264, 6, 3, LINE);
    float fill = status.total ? 264.0f * status.downloaded / status.total : 0;
    if (phase == STARTING) fill = 264;
    if (fill >= 6) roundRect(112, 124, fill, 6, 3, PILL);
    if (phase == DOWNLOADING) {
        Hint h[] = {{"B", "Back"}, {"START", "Quit"}};
        hints(h, 2);
    }
}

static void drawBottom(void) {
    onBottom();
    if (phase != LISTING || host_count == 0) return;
    char name[129];
    ascii(name, hosts[picked].title_long, 128);
    icon(7, hosts[picked].icon, 136, 28, 48);
    // The long title centred, over two lines at most, broken at a space.
    char first[64] = {0};
    const char* rest = NULL;
    int n = (int)strlen(name);
    if (textWidth(&font_body, name) > 288) {
        int cut = n < (int)sizeof first - 1 ? n : (int)sizeof first - 1;
        while (cut > 0) {
            memcpy(first, name, cut);
            first[cut] = 0;
            if (textWidth(&font_body, first) <= 288 && (name[cut] == ' ' || name[cut] == 0)) break;
            cut--;
        }
        rest = name + cut + (name[cut] == ' ');
    } else {
        strcpy(first, name);
    }
    text(&font_body, 160 - textWidth(&font_body, first) / 2, 92, first, INK);
    if (rest) text(&font_body, 160 - textWidth(&font_body, rest) / 2, 112, rest, INK);
    const char* note = "The host sends it for this session.";
    text(&font_small, 160 - textWidth(&font_small, note) / 2, 160, note, HINT);
    const char* note2 = "Nothing of yours is needed.";
    text(&font_small, 160 - textWidth(&font_small, note2) / 2, 178, note2, HINT);
}

static void present(void) {
    frameBegin(BG);
    drawTop();
    drawBottom();
    frameEnd();
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
    drawInit();

    if (R_FAILED(dlpOpen())) {
        fail("Download Play could not start.");
        while (aptMainLoop()) {
            hidScanInput();
            if (hidKeysDown() & (KEY_START | KEY_A)) break;
            frameBegin(BG);
            onTop();
            text(&font_small, 16, 10, "3DS Download Play", HINT);
            text(&font_title, 24, 100, message, INK);
            Hint h[] = {{"START", "Quit"}};
            hints(h, 1);
            frameEnd();
        }
        drawExit();
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
    }

    dlpStopSession();
    dlpClose();
    drawExit();
    return 0;
}
