/*
 *     Copyright (C) 2019  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#ifndef LIBRETRODROID_LOG_H
#define LIBRETRODROID_LOG_H

#define MODULE_NAME "libretrodroid"

#define VERBOSE_LOGGING false

#include <android/log.h>
#include "logflag.h"

// The Debug log setting (logflag.h) gates everything below an error. An expression, not do {} while (0),
// so a LOGx in an expression position still compiles.
#define NX_LOG_GATED(prio, ...) ((void) (nx_log_enabled() && __android_log_print(prio, MODULE_NAME, __VA_ARGS__)))

#if VERBOSE_LOGGING

#define LOGV(...) NX_LOG_GATED(ANDROID_LOG_VERBOSE, __VA_ARGS__)
#define LOGD(...) NX_LOG_GATED(ANDROID_LOG_DEBUG, __VA_ARGS__)
#define LOGI(...) NX_LOG_GATED(ANDROID_LOG_INFO, __VA_ARGS__)
#define LOGW(...) NX_LOG_GATED(ANDROID_LOG_WARN, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, MODULE_NAME, __VA_ARGS__)
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL, MODULE_NAME, __VA_ARGS__)

#else

#define LOGV(...)
#define LOGD(...)
#define LOGI(...) NX_LOG_GATED(ANDROID_LOG_INFO, __VA_ARGS__)
#define LOGW(...) NX_LOG_GATED(ANDROID_LOG_WARN, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, MODULE_NAME, __VA_ARGS__)
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL, MODULE_NAME, __VA_ARGS__)

#endif

#endif //LIBRETRODROID_LOG_H
