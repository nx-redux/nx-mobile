// What the client shows, from the mailbox the core fills in. No drawing here: the host tests run it too.
#ifndef NXDLP_UI_H
#define NXDLP_UI_H

#include <stddef.h>
#include <stdint.h>

#include "mailbox.h"

typedef enum { UI_LOOKING, UI_LIST, UI_JOINING, UI_DOWNLOADING, UI_STARTING, UI_FAILED } UiScreen;

UiScreen ui_screen(const NxDlpMailbox* box);
/// The offers to list: never more than the mailbox holds, whatever the count says.
int ui_offer_count(const NxDlpMailbox* box);
int ui_percent(uint32_t done, uint32_t total);
/// [selected] kept on a row that exists.
int ui_clamp(int selected, int count);
const char* ui_failure_text(uint32_t reason);
/// UCS-2 to the ASCII the client's font has ('?' for anything else), up to the first NUL or [chars].
void ui_ascii(char* out, size_t room, const uint16_t* in, size_t chars);

#endif
