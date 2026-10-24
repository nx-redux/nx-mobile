#ifndef LIBRETRODROID_LOGFLAG_H
#define LIBRETRODROID_LOGFLAG_H

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 2026-10-14: Settings → System → Debug log. false (the default) skips verbose, debug, info and warning logs; errors
 * always print. C linkage: netplay's C code (np_net.h) checks it too.
 */
bool nx_log_enabled(void);
void nx_log_set_enabled(bool enabled);

#ifdef __cplusplus
}
#endif

#endif // LIBRETRODROID_LOGFLAG_H
