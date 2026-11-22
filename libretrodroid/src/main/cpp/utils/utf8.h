/*
 *     Copyright (C) 2026  Mohammad Syuhada
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

#ifndef LIBRETRODROID_UTF8_H
#define LIBRETRODROID_UTF8_H

#include <cstddef>
#include <string>

namespace libretrodroid {

/**
 * Text from outside the app (core option labels and controller names, RetroAchievements titles, a netplay peer's bytes)
 * on its way to JNI. NewStringUTF takes *modified* UTF-8 and CheckJNI (on in the debuggable Dev build) aborts the process
 * on anything else: a Latin-1 byte, a torn sequence, and on older releases a 4-byte sequence (an emoji). No JNI here, so
 * the host check builds it alone; newSafeJString (jnistring.h) is the JNI side.
 */
namespace Utf8 {

/**
 * [s] (its first [n] bytes) is well-formed UTF-8 of 1 to 3 byte sequences only, which NewStringUTF takes as it is.
 * 4-byte sequences differ in modified UTF-8 (a surrogate pair of 3-byte halves), so they count as not safe.
 */
bool isJniSafe(const char* s, size_t n);

/**
 * [s] as UTF-16 for NewString: 1 to 3 byte sequences decoded, a well-formed 4-byte sequence (an emoji) as its surrogate
 * pair, and every byte that starts no well-formed sequence (Latin-1, a torn tail) as '?'.
 */
std::u16string toUtf16(const char* s, size_t n);

}

}

#endif //LIBRETRODROID_UTF8_H
