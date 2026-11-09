/*
 *     Copyright (C) 2026  nx-mobile contributors
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

#include <jni.h>

#include <exception>
#include <string>

#include "../log.h"
#include "../errorcodes.h"
#include "../utils/javautils.h"
#include "../utils/jnistring.h"

extern "C" {
#include "np_engine.h"
}

namespace libretrodroid {

extern "C" {

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroNetplay_start(
    JNIEnv* env,
    jclass obj,
    jint role,
    jstring peer,
    jint port
) {
    try {
        if (role != NP_ROLE_HOST && role != NP_ROLE_CLIENT) return JNI_FALSE;
        if (port <= 0 || port > 65535) return JNI_FALSE;
        std::string ip = peer == nullptr ? std::string() : JniString(env, peer).stdString();
        return np_start((np_role) role, ip.c_str(), (uint16_t) port, nullptr) == 0 ? JNI_TRUE : JNI_FALSE;
    } catch (std::exception &exception) {
        LOGE("Error in netplay start: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
        return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroNetplay_setPresyncFrames(
    JNIEnv* env,
    jclass obj,
    jint frames
) {
    np_set_presync_frames((int) frames);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroNetplay_pause(
    JNIEnv* env,
    jclass obj
) {
    try {
        np_pause();
    } catch (std::exception &exception) {
        LOGE("Error in netplay pause: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroNetplay_resume(
    JNIEnv* env,
    jclass obj
) {
    try {
        np_resume();
    } catch (std::exception &exception) {
        LOGE("Error in netplay resume: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroNetplay_leave(
    JNIEnv* env,
    jclass obj
) {
    try {
        np_leave();
    } catch (std::exception &exception) {
        LOGE("Error in netplay leave: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroNetplay_shutdown(
    JNIEnv* env,
    jclass obj
) {
    try {
        np_shutdown();
    } catch (std::exception &exception) {
        LOGE("Error in netplay shutdown: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT jintArray JNICALL Java_com_swordfish_libretrodroid_LibretroNetplay_status(
    JNIEnv* env,
    jclass obj
) {
    try {
        int seconds = 0;
        np_reason reason = NP_REASON_NONE;
        np_state state = np_get_state(&seconds, &reason);
        jint values[3] = { (jint) state, (jint) seconds, (jint) reason };
        jintArray result = env->NewIntArray(3);
        if (result == nullptr) return nullptr;   // OutOfMemoryError pending
        env->SetIntArrayRegion(result, 0, 3, values);
        return result;
    } catch (std::exception &exception) {
        LOGE("Error in netplay status: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
    return nullptr;
}

}

}
