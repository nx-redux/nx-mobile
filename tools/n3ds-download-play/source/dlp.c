// Command layouts follow Azahar's dlp:CLNT and ns:s handlers (src/core/hle/service/dlp, apt/apt.cpp).
#include <malloc.h>
#include <string.h>

#include "dlp.h"

#define DLP_CHILD_HIGH 0x0004000100000000ULL
#define SHARED_SIZE 0x10000

static Handle dlp, ns, shm, ev;
static void* shm_buf;
static u32 channel;

static Result call(Handle h) {
    Result r = svcSendSyncRequest(h);
    return R_FAILED(r) ? r : (Result)getThreadCommandBuffer()[1];
}

Result dlpOpen(void) {
    Result r = srvGetServiceHandle(&dlp, "dlp:CLNT");
    if (R_FAILED(r)) return r;
    if (R_FAILED(r = srvGetServiceHandle(&ns, "ns:s"))) return r;
    shm_buf = memalign(0x1000, SHARED_SIZE);
    if (!shm_buf) return -1;
    memset(shm_buf, 0, SHARED_SIZE);
    if (R_FAILED(r = svcCreateMemoryBlock(&shm, (u32)shm_buf, SHARED_SIZE, MEMPERM_READ | MEMPERM_WRITE,
                                          MEMPERM_READ | MEMPERM_WRITE)))
        return r;
    if (R_FAILED(r = svcCreateEvent(&ev, RESET_ONESHOT))) return r;

    u32* c = getThreadCommandBuffer();
    c[0] = IPC_MakeHeader(0x1, 3, 3); // Initialize
    c[1] = SHARED_SIZE;
    c[2] = 4; // beacons kept
    c[3] = 0; // constant memory
    c[4] = IPC_Desc_SharedHandles(2);
    c[5] = shm;
    c[6] = ev;
    if (R_FAILED(r = call(dlp))) return r;

    c = getThreadCommandBuffer();
    c[0] = IPC_MakeHeader(0x4, 0, 0); // GetChannels
    if (R_FAILED(r = call(dlp))) return r;
    channel = c[2] & 0xFFFF;
    return 0;
}

void dlpClose(void) {
    u32* c = getThreadCommandBuffer();
    c[0] = IPC_MakeHeader(0x2, 0, 0); // Finalize
    call(dlp);
    svcCloseHandle(ev);
    svcCloseHandle(shm);
    free(shm_buf);
    svcCloseHandle(ns);
    svcCloseHandle(dlp);
}

Result dlpStartScan(void) {
    u32* c = getThreadCommandBuffer();
    c[0] = IPC_MakeHeader(0x5, 6, 0);
    c[1] = channel;
    c[2] = 0; c[3] = 0; // any title
    c[4] = 0; c[5] = 0; // any host
    c[6] = 0;
    return call(dlp);
}

Result dlpStopScan(void) {
    u32* c = getThreadCommandBuffer();
    c[0] = IPC_MakeHeader(0x6, 0, 0);
    return call(dlp);
}

bool dlpNextTitle(DlpTitleInfo* out, bool restart) {
    u32* c = getThreadCommandBuffer();
    c[0] = IPC_MakeHeader(0x9, 1, 0); // GetTitleInfoInOrder
    c[1] = restart ? 1 : 0;
    u32* sb = getThreadStaticBuffers();
    u32 saved0 = sb[0], saved1 = sb[1];
    sb[0] = IPC_Desc_StaticBuffer(sizeof(DlpTitleInfo), 0);
    sb[1] = (u32)out;
    Result r = call(dlp);
    sb[0] = saved0;
    sb[1] = saved1;
    return R_SUCCEEDED(r);
}

Result dlpStartSession(const u8 mac[6], u64 child) {
    u32* c = getThreadCommandBuffer();
    c[0] = IPC_MakeHeader(0xD, 4, 0); // StartTitleDownload
    c[1] = 0; c[2] = 0;
    memcpy(&c[1], mac, 6);
    c[3] = (u32)child;
    c[4] = (u32)(child >> 32);
    return call(dlp);
}

Result dlpStatus(DlpStatus* out) {
    u32* c = getThreadCommandBuffer();
    c[0] = IPC_MakeHeader(0xE, 0, 0); // GetMyStatus
    Result r = call(dlp);
    if (R_SUCCEEDED(r)) {
        out->state = c[2] >> 24;
        out->connected = ((c[2] >> 16) & 0xFF) != 0;
        out->total = c[3];
        out->downloaded = c[4];
    }
    return r;
}

Result dlpPassphrase(u8 out[9]) {
    u32* c = getThreadCommandBuffer();
    c[0] = IPC_MakeHeader(0x11, 0, 0);
    Result r = call(dlp);
    if (R_SUCCEEDED(r)) memcpy(out, &c[2], 9);
    return r;
}

Result dlpStopSession(void) {
    u32* c = getThreadCommandBuffer();
    c[0] = IPC_MakeHeader(0x12, 0, 0);
    return call(dlp);
}

u64 dlpChildOf(const DlpTitleInfo* info) {
    return DLP_CHILD_HIGH | ((u64)info->unique_id << 8) | (info->variation & 0xFF);
}

Result dlpStartChild(const u8 mac[6], const u8 passphrase[9], u64 child) {
    // What the child reads back with APT GetWirelessRebootInfo: the host's MAC, then the passphrase.
    static u8 info[16];
    memset(info, 0, sizeof info);
    memcpy(info, mac, 6);
    memcpy(info + 6, passphrase, 9);
    u32* c = getThreadCommandBuffer();
    c[0] = IPC_MakeHeader(0x6, 1, 2); // SetWirelessRebootInfo
    c[1] = sizeof info;
    c[2] = IPC_Desc_StaticBuffer(sizeof info, 0);
    c[3] = (u32)info;
    Result r = call(ns);
    if (R_FAILED(r)) return r;

    bool n3ds = false;
    APT_CheckNew3DS(&n3ds);
    c = getThreadCommandBuffer();
    c[0] = IPC_MakeHeader(0x10, 6, 0); // RebootSystem
    c[1] = 1;                          // into a title
    c[2] = (u32)child;
    c[3] = (u32)(child >> 32);
    c[4] = MEDIATYPE_NAND; // Download Play children are NAND titles
    c[5] = 0;
    c[6] = n3ds ? 6 : 0; // memory layout: New 3DS / Old 3DS
    return call(ns);
}

int dlpRemoveOldChildren(void) {
    if (R_FAILED(amInit())) return 0;
    int removed = 0;
    u32 count = 0;
    if (R_SUCCEEDED(AM_GetTitleCount(MEDIATYPE_NAND, &count)) && count > 0) {
        u64* ids = malloc(count * sizeof(u64));
        u32 read = 0;
        if (ids && R_SUCCEEDED(AM_GetTitleList(&read, MEDIATYPE_NAND, count, ids))) {
            for (u32 i = 0; i < read; i++) {
                if ((ids[i] >> 32) == (DLP_CHILD_HIGH >> 32) && R_SUCCEEDED(AM_DeleteTitle(MEDIATYPE_NAND, ids[i])))
                    removed++;
            }
        }
        free(ids);
    }
    amExit();
    return removed;
}
