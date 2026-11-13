#pragma once
// nx-mobile private libretro environment call: the core tells the frontend what the emulated DS radio is doing;
// *data is a const unsigned* holding one of the NX_NDS_RADIO_ values. Only the nx-mobile runner answers it; only
// nx-mobile's patched melonDS DS sends it, from the emulation thread.
#define NX_ENVIRONMENT_SET_NDS_RADIO (0x20000 /* RETRO_ENVIRONMENT_PRIVATE */ | 0x4e59)
#define NX_NDS_RADIO_OFF 0      // the game powered the radio off, or the game ended
#define NX_NDS_RADIO_ON 1       // the game powered the radio on
#define NX_NDS_RADIO_HOSTING 2  // the game sent a beacon: it created a group (once per ON)
