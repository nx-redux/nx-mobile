#pragma once
// nx-mobile private libretro environment call: PSP ad hoc over Wi-Fi. The core hands the frontend its ad hoc link, so
// the frontend can follow it and leave it as it does Azahar's room (nx_3ds_link.h), and tells the core which device runs
// the ad hoc server: the frontend runs the finder and the election (psl_finder.h), the core runs the server and the
// client. *data is a nx_psp_link_interface* whose frontend half the frontend fills in, or NULL when the link is gone and
// the calls must not be made again. Only the nx-mobile runner answers it; only nx-mobile's patched PPSSPP sends it (its
// copy must stay byte for byte the same). The calls may come from any thread unless noted.
#include <stdint.h>

#define NX_ENVIRONMENT_SET_PSP_LINK (0x20000 /* RETRO_ENVIRONMENT_PRIVATE */ | 0x4e5b)

struct nx_psp_link_interface {
    /* The core's, set before the call. Any thread. */
    int  (*presence)(void);   /* NX_3DS_LINK_OUT / SEEKING / ROOM numbering (nx_3ds_link.h) */
    void (*leave)(void);      /* tell the game it is disconnected, leave the server, stay out */
    void (*rejoin)(void);     /* a session comes back after leave */
    void (*serve)(int on);    /* start or stop the built-in ad hoc server; returns once it listens or has stopped */
    void (*relogin)(void);    /* the server changed or is gone: drop its connection, tell the game if it was in a group */
    /* The frontend's, filled in while it answers the call. */
    int  (*server)(uint32_t* ip, uint32_t* self);  /* blocks up to PSL_LISTEN_MS + 500; 1: *ip is the server, *self this
                                                      device's address on the routed network (network order); 0: none */
    /* active: the game reached Scan/Create/Connect/Join, 0 once it left ad hoc. grouped: the game is in a group, or
     * this device's server has a user other than this device, so the server must not stop; it can stay 1 with active
     * 0 while others play on the server of a game that left ad hoc. Only changes are told, from any thread. */
    void (*active)(int active, int grouped);
    void (*route_socket)(int fd, int cable);
};
/* *data NULL: the core's link is gone; make no further calls. */
