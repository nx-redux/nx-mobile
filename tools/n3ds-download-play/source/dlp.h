// The emulated Download Play client service (dlp:CLNT) and the two ns:s calls that hand a downloaded child its host.
#pragma once

#include <3ds.h>

// One host's offer as dlp:CLNT reports it (5032 bytes).
typedef struct {
    u32 unique_id;
    u32 variation;
    u8 mac[6];
    u16 version;
    u8 age_ratings[16];
    u16 title_short[64];
    u16 title_long[128];
    u16 icon[48 * 48]; // RGB565 in 8x8 tiles, as 3DS icons are stored
    u32 size;
    u8 unk2, unk3;
    u16 padding;
} DlpTitleInfo;

typedef struct {
    u32 state;      // 9: the child is installed
    bool connected; // to the host's network
    u32 total, downloaded;
} DlpStatus;

#define DLP_STATE_COMPLETE 9

Result dlpOpen(void);
void dlpClose(void);
Result dlpStartScan(void);
Result dlpStopScan(void);
// The next host since the last call with restart set; false when there is none.
bool dlpNextTitle(DlpTitleInfo* out, bool restart);
Result dlpStartSession(const u8 mac[6], u64 child);
Result dlpStatus(DlpStatus* out);
Result dlpPassphrase(u8 out[9]);
Result dlpStopSession(void);

// The child's title id for a host's offer.
u64 dlpChildOf(const DlpTitleInfo* info);
// Hands the child its host (MAC and passphrase) and restarts into it.
Result dlpStartChild(const u8 mac[6], const u8 passphrase[9], u64 child);
// Azahar downloads a child only when none is installed, so one is kept between sessions only with a record, written
// once its download completed, of the size the host offered: a half-installed child, or one from another version of
// the host's game, is removed so the session downloads it afresh.
void dlpCheckKeptChild(u64 child, u32 size);
void dlpRememberChild(u64 child, u32 size);
// Removes a child and its record: one that would not start.
void dlpForgetChild(u64 child);
