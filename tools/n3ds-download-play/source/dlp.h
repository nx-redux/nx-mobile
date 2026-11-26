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
// Keeps an installed child only when it is the version the host offers (Azahar downloads a child only when none is
// installed): an older one is removed so the next session downloads the host's. True when it was removed.
bool dlpDropStaleChild(u64 child, u16 host_version);
