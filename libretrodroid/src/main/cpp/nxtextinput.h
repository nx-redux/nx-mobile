#pragma once
#include <stdbool.h>
// nx-mobile private libretro environment call: a core's text entry (the Wii U software keyboard) opened or closed, so the
// frontend can show its own text box and type the answer through the keyboard callback. *data is a
// struct nx_text_input*; true: the frontend shows its box (the core need not explain its own way of typing).
// Only the nx-mobile runner answers it; only nx-mobile's patched cores send it.
#define NX_ENVIRONMENT_SET_TEXT_INPUT (0x20000 /* RETRO_ENVIRONMENT_PRIVATE */ | 0x4e5c)
struct nx_text_input {
    bool open;               // false: the entry closed (the strings may be NULL)
    const char* text;        // UTF-8: what the entry holds as it opens
    const char* name;        // UTF-8: the answer when nothing is typed (the account's name), or NULL
    unsigned max_length;     // UTF-16 units the entry takes; 0: not known
};
