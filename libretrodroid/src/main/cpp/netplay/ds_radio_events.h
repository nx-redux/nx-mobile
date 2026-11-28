// The DS core's radio reports (NX_ENVIRONMENT_SET_NDS_RADIO) split between the room, which follows the radio, and the
// app, which follows a game received over DS Download Play.
#ifndef LIBRETRODROID_DS_RADIO_EVENTS_H
#define LIBRETRODROID_DS_RADIO_EVENTS_H

#include "nx_nds_radio.h"

struct DsRadioEvents {
    int hostLeft = 0;
    bool dlpStarted = false;

    /// True when [state] is the radio's (off, on, hosting), for the room.
    bool apply(unsigned state) {
        switch (state) {
        case NX_NDS_RADIO_OFF:
        case NX_NDS_RADIO_ON:
        case NX_NDS_RADIO_HOSTING:
            return true;
        case NX_NDS_RADIO_DLP_STARTED:
            dlpStarted = true;
            return false;
        case NX_NDS_RADIO_HOST_LEFT:
            hostLeft++;
            return false;
        default:
            return false;
        }
    }

    void reset() { *this = DsRadioEvents {}; }
};

#endif
