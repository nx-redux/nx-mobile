/*
 * The block our DS Download Play client keeps in the DS's main RAM for the emulator's core, which runs the protocol:
 * the core finds it by its magic, fills in the hosts it hears and how a download goes, and reads which host to join.
 * The same file is in the core's patch (net/wmb_mailbox.h); a host test keeps the two identical. Little-endian, as
 * the DS and the phones are.
 */
#ifndef NXDLP_MAILBOX_H
#define NXDLP_MAILBOX_H

#include <stdint.h>

#define NXDLP_MAGIC "NXREDUX-DSDLP-1"
#define NXDLP_OFFERS 8

enum { NXDLP_NONE, NXDLP_SCANNING, NXDLP_JOINING, NXDLP_DOWNLOADING, NXDLP_BOOTING, NXDLP_FAILED };
enum { NXDLP_OK, NXDLP_HOST_GONE, NXDLP_ROOM_LOST, NXDLP_BAD_DOWNLOAD };
enum { NXDLP_CMD_NONE, NXDLP_CMD_JOIN, NXDLP_CMD_CANCEL };

typedef struct {
    uint8_t mac[6];
    uint8_t players;
    uint8_t maxPlayers;
    uint16_t hostName[10];    /* UCS-2, NUL-terminated when shorter */
    uint16_t gameName[48];    /* UCS-2, NUL-terminated */
    uint16_t description[96]; /* UCS-2, NUL-terminated */
    uint8_t icon[0x220];      /* palette, then 4bpp tiles, as a cartridge icon */
} NxDlpOffer;

typedef struct {
    char magic[16];
    /* Written by the core. */
    uint32_t coreSeq;    /* bumped whenever the offers change */
    uint32_t state;      /* NXDLP_SCANNING .. NXDLP_FAILED */
    uint32_t reason;     /* with NXDLP_FAILED */
    uint32_t done, total; /* packets, while downloading */
    uint32_t offerCount;
    NxDlpOffer offers[NXDLP_OFFERS];
    /* Written by the client. */
    uint32_t clientSeq;  /* bumped with each command */
    uint32_t command;    /* NXDLP_CMD_JOIN (joinMac) or NXDLP_CMD_CANCEL */
    uint8_t joinMac[6];
    uint8_t pad[2];
} NxDlpMailbox;

#endif
