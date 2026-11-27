// DS Download Play client. The core (our melonDS DS patch) runs the protocol; this program shows what it hears and
// says which host to join, through the mailbox (mailbox.h).
#include <nds.h>
#include <stdio.h>
#include <string.h>

#include "mailbox.h"

static NxDlpMailbox box __attribute__((aligned(32)));
// The core knows this program by the magic in its binary, so it stays a plain constant, not folded into code.
static const char magic[sizeof(box.magic)] __attribute__((used)) = NXDLP_MAGIC;

int main(void) {
    consoleDemoInit();
    memcpy(box.magic, (const void* volatile)magic, sizeof(box.magic));
    uint32_t seenSeq = ~0u;
    bool asked = false;
    while (pmMainLoop()) {
        swiWaitForVBlank();
        scanKeys();
        if (keysDown() & KEY_START) break;
        if (!asked && box.offerCount > 0) {
            memcpy(box.joinMac, box.offers[0].mac, sizeof(box.joinMac));
            box.command = NXDLP_CMD_JOIN;
            box.clientSeq++;
            asked = true;
        }
        if (box.coreSeq != seenSeq) seenSeq = box.coreSeq;
        printf("\x1b[0;0Hstate %lu reason %lu   \noffers %lu   \n%lu / %lu packets   \n", (unsigned long)box.state,
               (unsigned long)box.reason, (unsigned long)box.offerCount, (unsigned long)box.done, (unsigned long)box.total);
    }
    return 0;
}
