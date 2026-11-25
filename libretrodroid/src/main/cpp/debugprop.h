// The debug.nx.* switches that turn a pacing aid off for side-by-side checks
// (adb shell setprop debug.nx.adpf 0, debug.nx.presenttime 0). Unset or anything but "0" leaves the aid on.
// debugPropertyOn: a device-test aid that is off unless set to "1".
#ifndef LIBRETRODROID_DEBUGPROP_H
#define LIBRETRODROID_DEBUGPROP_H

#include <cstring>
#include <sys/system_properties.h>

namespace libretrodroid {

inline bool debugPropertyOff(const char* name) {
    char value[PROP_VALUE_MAX] = {};
    return __system_property_get(name, value) > 0 && std::strcmp(value, "0") == 0;
}

inline bool debugPropertyOn(const char* name) {
    char value[PROP_VALUE_MAX] = {};
    return __system_property_get(name, value) > 0 && std::strcmp(value, "1") == 0;
}

}

#endif
