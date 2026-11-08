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
#include "np_link.h"
#include "gwl_mode.h"
}

namespace libretrodroid {

extern "C" {

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroNetplay_linkStart(
    JNIEnv* env,
    jclass obj,
    jint role,
    jstring peer,
    jint port,
    jstring mode
) {
    try {
        if (role != NL_ROLE_HOST && role != NL_ROLE_CLIENT) return JNI_FALSE;
        if (port <= 0 || port > 65535) return JNI_FALSE;
        std::string ip = peer == nullptr ? std::string() : JniString(env, peer).stdString();
        std::string m = mode == nullptr ? std::string() : JniString(env, mode).stdString();
        return nl_start((nl_role) role, ip.c_str(), (uint16_t) port, m.c_str(), nullptr) == 0 ? JNI_TRUE : JNI_FALSE;
    } catch (std::exception &exception) {
        LOGE("Error in link start: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
        return JNI_FALSE;
    }
}

JNIEXPORT jstring JNICALL Java_com_swordfish_libretrodroid_LibretroNetplay_linkHostMode(
    JNIEnv* env,
    jclass obj
) {
    try {
        char mode[NL_MODE_MAX + 1];
        if (nl_host_mode(mode, sizeof(mode)) != 1) return nullptr;
        return newSafeJString(env, mode);   // the peer's bytes as they came off the network: any of them
    } catch (std::exception &exception) {
        LOGE("Error in link host mode: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
        return nullptr;
    }
}

JNIEXPORT jstring JNICALL Java_com_swordfish_libretrodroid_LibretroNetplay_gbaLinkMode(
    JNIEnv* env,
    jclass obj,
    jstring path
) {
    try {
        JniString romPath(env, path);
        return newSafeJString(env, gwl_mode_token(gwl_probe_file(romPath.stdString().c_str())));
    } catch (std::exception &exception) {
        LOGE("Error in GBA link mode: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
        return nullptr;
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroNetplay_linkPause(
    JNIEnv* env,
    jclass obj
) {
    try {
        nl_pause();
    } catch (std::exception &exception) {
        LOGE("Error in link pause: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroNetplay_linkResume(
    JNIEnv* env,
    jclass obj
) {
    try {
        nl_resume();
    } catch (std::exception &exception) {
        LOGE("Error in link resume: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroNetplay_linkLeave(
    JNIEnv* env,
    jclass obj
) {
    try {
        nl_leave();
    } catch (std::exception &exception) {
        LOGE("Error in link leave: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroNetplay_linkUnblock(
    JNIEnv* env,
    jclass obj
) {
    nl_release_hold();   // sets a flag; never blocks, never throws
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroNetplay_linkShutdown(
    JNIEnv* env,
    jclass obj
) {
    try {
        nl_shutdown();
    } catch (std::exception &exception) {
        LOGE("Error in link shutdown: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT jintArray JNICALL Java_com_swordfish_libretrodroid_LibretroNetplay_linkStatus(
    JNIEnv* env,
    jclass obj
) {
    try {
        int seconds = 0;
        nl_reason reason = NL_REASON_NONE;
        nl_state state = nl_get_state(&seconds, &reason);
        jint values[3] = { (jint) state, (jint) seconds, (jint) reason };
        jintArray result = env->NewIntArray(3);
        if (result == nullptr) return nullptr;   // OutOfMemoryError pending
        env->SetIntArrayRegion(result, 0, 3, values);
        return result;
    } catch (std::exception &exception) {
        LOGE("Error in link status: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
    return nullptr;
}

}

}
