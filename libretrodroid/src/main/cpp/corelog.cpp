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

#include "corelog.h"

#include <jni.h>
#include <cstdio>

#include "utils/jnistring.h"

namespace libretrodroid {

std::mutex CoreLog::lock;
std::deque<std::string> CoreLog::lines;

void CoreLog::clear() {
    std::lock_guard<std::mutex> guard(lock);
    lines.clear();
}

void CoreLog::add(char level, const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    append(level, fmt, args);
    va_end(args);
}

void CoreLog::append(char level, const char* fmt, va_list args) {
    if (fmt == nullptr) return;
    char buffer[1024];
    int n = vsnprintf(buffer, sizeof(buffer), fmt, args);
    if (n < 0) return;
    std::string line = std::string(1, level) + " " + buffer;
    while (!line.empty() && (line.back() == '\n' || line.back() == '\r' || line.back() == ' ' || line.back() == '\t')) {
        line.pop_back();
    }
    if (line.size() <= 2) return;
    std::lock_guard<std::mutex> guard(lock);
    if (!lines.empty() && lines.back() == line) return;
    lines.push_back(std::move(line));
    while (lines.size() > CAPACITY) lines.pop_front();
}

std::vector<std::string> CoreLog::recent() {
    std::lock_guard<std::mutex> guard(lock);
    return {lines.begin(), lines.end()};
}

}

extern "C" {

JNIEXPORT jobjectArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_getRecentCoreErrors(
    JNIEnv* env,
    jclass obj
) {
    return newSafeJStringArray(env, libretrodroid::CoreLog::recent());
}

}
