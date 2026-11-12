#pragma once
// nx-mobile private libretro environment call: the core hands the frontend its local wireless room, so the frontend can
// follow it and leave it without waiting for a frame. *data is a nx_3ds_link_interface* the frontend fills in
// (route_socket), or NULL when the room is gone and the calls must not be made again. Only the nx-mobile runner answers it; only nx-mobile's patched Azahar sends
// it (its copy: src/citra_libretro/nx_3ds_link.h). The calls may come from any thread.
#define NX_ENVIRONMENT_SET_3DS_LINK (0x20000 /* RETRO_ENVIRONMENT_PRIVATE */ | 0x4e5a)

#define NX_3DS_LINK_OUT 0      // in no room and not looking for one
#define NX_3DS_LINK_SEEKING 1  // looking for a room, about to open one, or asking a session's host
#define NX_3DS_LINK_ROOM 2     // in a room

#define NX_3DS_CABLE_NONE 0    // no phone-to-phone cable
#define NX_3DS_CABLE_COMING 1  // one is coming up: a room found off it waits a few seconds for it
#define NX_3DS_CABLE_UP 2      // up: rooms are looked for on it too, and one found there is joined

struct nx_3ds_link_interface {
    int (*presence)(void);   // one of NX_3DS_LINK_*
    void (*leave)(void);     // leave at once and stay out: until the game's wireless comes on again, or rejoin (a session)
    void (*rejoin)(void);    // a session goes back to its room after leave; nothing on the automatic route
    void (*set_cable)(int state, const char* broadcast);   // NX_3DS_CABLE_*, and the cable network's broadcast address (a /30)
    // Filled in by the frontend while it answers the call: puts a socket of the room on the cable's network
    // (cable != 0) or back on the Wi-Fi.
    void (*route_socket)(int fd, int cable);
};
