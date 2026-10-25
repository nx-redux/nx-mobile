/*
 *     Copyright (C) 2021  Filippo Scognamiglio
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

#include <EGL/egl.h>

#include <algorithm>
#include <initializer_list>
#include <memory>
#include <string>
#include <vector>
#include <unordered_set>
#include <mutex>
#include <optional>

#include "coreretention.h"
#include "gldebug.h"
#include "libretrodroid.h"
#include "log.h"
#include "core.h"
#include "audio.h"
#include "video.h"
#include "renderers/renderer.h"
#include "fpssync.h"
#include "input.h"
#include "sensorstate.h"
#include "rumble.h"
#include "shadermanager.h"
#include "utils/javautils.h"
#include "errorcodes.h"
#include "environment.h"
#include "renderers/es3/framebufferrenderer.h"
#include "renderers/es2/imagerendereres2.h"
#include "renderers/es3/imagerendereres3.h"
#include "utils/jnistring.h"
#include "vulkanbridge.h"
#include "achievements/achievements.h"
#include "achievements/achievementshash.h"
#include "microphone/microphoneinterface.h"

namespace libretrodroid {

extern "C" {
#include "utils/utils.h"
#include "../../libretro-common/include/libretro.h"
#include "utils/libretrodroidexception.h"
}

namespace {

// Null arrays read as empty so a length guard catches them.
jsize presetArrayLength(JNIEnv* env, jarray array) {
    return array == nullptr ? 0 : env->GetArrayLength(array);
}

std::vector<std::string> presetStrings(JNIEnv* env, jobjectArray array) {
    std::vector<std::string> result;
    jsize length = presetArrayLength(env, array);
    for (jsize i = 0; i < length; i++) {
        auto value = (jstring) env->GetObjectArrayElement(array, i);
        if (value == nullptr) {
            result.emplace_back();
            continue;
        }
        result.push_back(JniString(env, value).stdString());
        env->DeleteLocalRef(value);
    }
    return result;
}

std::vector<jint> presetInts(JNIEnv* env, jintArray array) {
    std::vector<jint> result(presetArrayLength(env, array));
    if (!result.empty()) env->GetIntArrayRegion(array, 0, (jsize) result.size(), result.data());
    return result;
}

std::vector<jfloat> presetFloats(JNIEnv* env, jfloatArray array) {
    std::vector<jfloat> result(presetArrayLength(env, array));
    if (!result.empty()) env->GetFloatArrayRegion(array, 0, (jsize) result.size(), result.data());
    return result;
}

std::vector<jboolean> presetBooleans(JNIEnv* env, jbooleanArray array) {
    std::vector<jboolean> result(presetArrayLength(env, array));
    if (!result.empty()) env->GetBooleanArrayRegion(array, 0, (jsize) result.size(), result.data());
    return result;
}

}

extern "C" {

JNIEXPORT jint JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_availableDisks(
    JNIEnv* env,
    jclass obj
) {
    return LibretroDroid::getInstance().availableDisks();
}

JNIEXPORT jint JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_currentDisk(
    JNIEnv* env,
    jclass obj
) {
    return LibretroDroid::getInstance().currentDisk();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_changeDisk(
    JNIEnv* env,
    jclass obj,
    jint index
) {
    return LibretroDroid::getInstance().changeDisk(index);
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_updateCoreOptionsDisplay(
    JNIEnv* env,
    jclass obj
) {
    return Environment::getInstance().updateCoreOptionsDisplay();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_updateVariable(
    JNIEnv* env,
    jclass obj,
    jobject variable
) {
    Variable v = JavaUtils::variableFromJava(env, variable);
    Environment::getInstance().updateVariable(v.key, v.value);
}

namespace {

jobjectArray toJavaStringArray(JNIEnv* env, const std::vector<std::string>& values) {
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(values.size(), stringClass, nullptr);
    for (size_t i = 0; i < values.size(); i++) {
        jstring value = newSafeJString(env, values[i]);   // a core's option values and labels: any bytes
        env->SetObjectArrayElement(result, i, value);
        env->DeleteLocalRef(value);
    }
    env->DeleteLocalRef(stringClass);
    return result;
}

void setStringField(JNIEnv* env, jobject obj, jfieldID field, const std::string& value) {
    jstring jValue = newSafeJString(env, value);   // a core's text: any bytes (Latin-1 labels)
    env->SetObjectField(obj, field, jValue);
    env->DeleteLocalRef(jValue);
}

}

JNIEXPORT jobjectArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_getVariables(
    JNIEnv* env,
    jclass obj
) {
    jclass variableClass = env->FindClass("com/swordfish/libretrodroid/Variable");
    jmethodID variableMethodID = env->GetMethodID(variableClass, "<init>", "()V");

    jfieldID jKeyField = env->GetFieldID(variableClass, "key", "Ljava/lang/String;");
    jfieldID jValueField = env->GetFieldID(variableClass, "value", "Ljava/lang/String;");
    jfieldID jDescriptionField = env->GetFieldID(variableClass, "description", "Ljava/lang/String;");
    jfieldID jLabelField = env->GetFieldID(variableClass, "label", "Ljava/lang/String;");
    jfieldID jLabelCategorizedField = env->GetFieldID(variableClass, "labelCategorized", "Ljava/lang/String;");
    jfieldID jInfoField = env->GetFieldID(variableClass, "info", "Ljava/lang/String;");
    jfieldID jCategoryField = env->GetFieldID(variableClass, "category", "Ljava/lang/String;");
    jfieldID jCategoryLabelField = env->GetFieldID(variableClass, "categoryLabel", "Ljava/lang/String;");
    jfieldID jCategoryInfoField = env->GetFieldID(variableClass, "categoryInfo", "Ljava/lang/String;");
    jfieldID jValuesField = env->GetFieldID(variableClass, "values", "[Ljava/lang/String;");
    jfieldID jValueLabelsField = env->GetFieldID(variableClass, "valueLabels", "[Ljava/lang/String;");
    jfieldID jDefaultValueField = env->GetFieldID(variableClass, "defaultValue", "Ljava/lang/String;");
    jfieldID jVisibleField = env->GetFieldID(variableClass, "visible", "Z");

    auto variables = Environment::getInstance().getVariables();
    jobjectArray result = env->NewObjectArray(variables.size(), variableClass, nullptr);

    for (int i = 0; i < variables.size(); i++) {
        const auto& variable = variables[i];
        jobject jVariable = env->NewObject(variableClass, variableMethodID);

        setStringField(env, jVariable, jKeyField, variable.key);
        setStringField(env, jVariable, jValueField, variable.value);
        setStringField(env, jVariable, jDescriptionField, variable.description);

        // Values the frontend set but the core never declared carry no metadata.
        if (variable.order >= 0) {
            setStringField(env, jVariable, jLabelField, variable.label);
            setStringField(env, jVariable, jLabelCategorizedField, variable.labelCategorized);
            setStringField(env, jVariable, jInfoField, variable.info);
            setStringField(env, jVariable, jCategoryField, variable.category);
            setStringField(env, jVariable, jCategoryLabelField, variable.categoryLabel);
            setStringField(env, jVariable, jCategoryInfoField, variable.categoryInfo);
            setStringField(env, jVariable, jDefaultValueField, variable.defaultValue);

            jobjectArray jValues = toJavaStringArray(env, variable.values);
            env->SetObjectField(jVariable, jValuesField, jValues);
            env->DeleteLocalRef(jValues);

            jobjectArray jValueLabels = toJavaStringArray(env, variable.valueLabels);
            env->SetObjectField(jVariable, jValueLabelsField, jValueLabels);
            env->DeleteLocalRef(jValueLabels);
        }
        env->SetBooleanField(jVariable, jVisibleField, variable.visible);

        env->SetObjectArrayElement(result, i, jVariable);
        env->DeleteLocalRef(jVariable);
    }
    return result;
}

JNIEXPORT jobjectArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_getControllers(
    JNIEnv* env,
    jclass obj
) {
    jclass variableClass = env->FindClass("[Lcom/swordfish/libretrodroid/Controller;");

    auto controllers = Environment::getInstance().getControllers();
    jobjectArray result = env->NewObjectArray(controllers.size(), variableClass, nullptr);

    for (int i = 0; i < controllers.size(); i++) {
        jclass variableClass2 = env->FindClass("com/swordfish/libretrodroid/Controller");
        jobjectArray controllerArray = env->NewObjectArray(
            controllers[i].size(),
            variableClass2,
            nullptr
        );
        jmethodID variableMethodID = env->GetMethodID(variableClass2, "<init>", "()V");

        for (int j = 0; j < controllers[i].size(); j++) {
            jobject jController = env->NewObject(variableClass2, variableMethodID);

            jfieldID jIdField = env->GetFieldID(variableClass2, "id", "I");
            jfieldID jDescriptionField = env->GetFieldID(
                variableClass2,
                "description",
                "Ljava/lang/String;"
            );

            env->SetIntField(jController, jIdField, (int) controllers[i][j].id);
            env->SetObjectField(
                jController,
                jDescriptionField,
                newSafeJString(env, controllers[i][j].description));

            env->SetObjectArrayElement(controllerArray, j, jController);
        }

        env->SetObjectArrayElement(result, i, controllerArray);
    }
    return result;
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setControllerType(
    JNIEnv* env,
    jclass obj,
    jint port,
    jint type
) {
    LibretroDroid::getInstance().setControllerType(port, type);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setStickyPointer(
    JNIEnv* env,
    jclass obj,
    jint port,
    jboolean sticky
) {
    LibretroDroid::getInstance().setStickyPointer(port, sticky);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setPointerVelocity(
    JNIEnv* env,
    jclass obj,
    jint port,
    jfloat vx,
    jfloat vy
) {
    LibretroDroid::getInstance().setPointerVelocity(port, vx, vy);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setSensorAvailable(
    JNIEnv* env,
    jclass obj,
    jboolean accel,
    jboolean gyro
) {
    SensorState::get().setAvailable(accel, gyro);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setSensorEnabled(
    JNIEnv* env,
    jclass obj,
    jint port,
    jboolean on
) {
    SensorState::get().setEnabled(port, on);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setSensorValues(
    JNIEnv* env,
    jclass obj,
    jint port,
    jfloat ax,
    jfloat ay,
    jfloat az,
    jfloat gx,
    jfloat gy,
    jfloat gz
) {
    SensorState::get().setValues(port, ax, ay, az, gx, gy, gz);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_achievementsEnable(
    JNIEnv* env,
    jclass obj,
    jboolean enabled
) {
    LibretroDroid::getInstance().achievementsEnable(enabled);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_achievementsLogin(
    JNIEnv* env,
    jclass obj,
    jstring user,
    jstring token
) {
    JniString u(env, user);
    JniString t(env, token);
    LibretroDroid::getInstance().achievementsLogin(u.stdString(), t.stdString());
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_achievementsLoadGame(
    JNIEnv* env,
    jclass obj,
    jstring hash,
    jint consoleId
) {
    JniString h(env, hash);
    LibretroDroid::getInstance().achievementsLoadGame(h.stdString(), (uint32_t) consoleId);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_achievementsUnloadGame(
    JNIEnv* env,
    jclass obj
) {
    LibretroDroid::getInstance().achievementsUnloadGame();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_achievementsServerResponse(
    JNIEnv* env,
    jclass obj,
    jint requestId,
    jint status,
    jstring body
) {
    JniString b(env, body);
    Achievements::getInstance().serverResponse((uint32_t) requestId, (int) status, b.stdString());
}

JNIEXPORT jobjectArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_achievementsList(
    JNIEnv* env,
    jclass obj
) {
    auto items = LibretroDroid::getInstance().achievementsList();
    jclass cls = env->FindClass("com/swordfish/libretrodroid/AchievementInfo");
    jmethodID ctor = env->GetMethodID(cls, "<init>", "(ILjava/lang/String;Ljava/lang/String;ILjava/lang/String;IJLjava/lang/String;FFII)V");
    jobjectArray out = env->NewObjectArray((jsize) items.size(), cls, nullptr);
    for (jsize i = 0; i < (jsize) items.size(); i++) {
        auto& a = items[i];
        // The server's titles and descriptions: an emoji is common there.
        jstring title = newSafeJString(env, a.title), desc = newSafeJString(env, a.description),
                badge = newSafeJString(env, a.badge), progress = newSafeJString(env, a.progress);
        jobject item = env->NewObject(cls, ctor, (jint) a.id, title, desc, (jint) a.points, badge, (jint) a.state, (jlong) a.unlockTime,
                                      progress, (jfloat) a.percent, (jfloat) a.rarity, (jint) a.type, (jint) a.category);
        env->SetObjectArrayElement(out, i, item);
        env->DeleteLocalRef(item); env->DeleteLocalRef(title); env->DeleteLocalRef(desc); env->DeleteLocalRef(badge); env->DeleteLocalRef(progress);
    }
    env->DeleteLocalRef(cls);
    return out;
}

JNIEXPORT jintArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_achievementsSummary(
    JNIEnv* env,
    jclass obj
) {
    auto s = LibretroDroid::getInstance().achievementsSummary();
    jint values[3] = { (jint) s.unlocked, (jint) s.total, (jint) s.points };
    jintArray out = env->NewIntArray(3);
    env->SetIntArrayRegion(out, 0, 3, values);
    return out;
}

JNIEXPORT jobjectArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_achievementsBuildAwardRequest(
    JNIEnv* env,
    jclass obj,
    jstring user,
    jstring token,
    jint achievementId,
    jstring hash,
    jint seconds
) {
    JniString u(env, user);
    JniString t(env, token);
    JniString h(env, hash);
    std::string url, post;
    if (!Achievements::buildAwardRequest(u.stdString(), t.stdString(), (uint32_t) achievementId, h.stdString(), (uint32_t) seconds, url, post)) {
        return nullptr;
    }
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray out = env->NewObjectArray(2, stringClass, nullptr);
    jstring urlString = env->NewStringUTF(url.c_str());
    jstring postString = env->NewStringUTF(post.c_str());
    env->SetObjectArrayElement(out, 0, urlString);
    env->SetObjectArrayElement(out, 1, postString);
    env->DeleteLocalRef(urlString); env->DeleteLocalRef(postString); env->DeleteLocalRef(stringClass);
    return out;
}

JNIEXPORT jstring JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_achievementsHash(
    JNIEnv* env,
    jclass obj,
    jint consoleId,
    jobject fileList
) {
    jclass dvf = env->FindClass("com/swordfish/libretrodroid/DetachedVirtualFile");
    jmethodID getPath = env->GetMethodID(dvf, "getVirtualPath", "()Ljava/lang/String;");
    jmethodID getFd = env->GetMethodID(dvf, "getFileDescriptor", "()I");
    std::vector<std::pair<std::string, int>> files;
    JavaUtils::forEachOnJavaIterable(env, fileList, [&](jobject item) {
        JniString path(env, (jstring) env->CallObjectMethod(item, getPath));
        files.emplace_back(path.stdString(), (int) env->CallIntMethod(item, getFd));
    });
    env->DeleteLocalRef(dvf);
    std::string hash = AchievementsHash::hash((uint32_t) consoleId, std::move(files));
    return hash.empty() ? nullptr : env->NewStringUTF(hash.c_str());
}

JNIEXPORT jobjectArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_chdReadSectors(
    JNIEnv* env,
    jclass obj,
    jint fd,
    jintArray sectors
) {
    std::vector<uint32_t> wanted;
    jsize n = env->GetArrayLength(sectors);
    jint* raw = env->GetIntArrayElements(sectors, nullptr);
    for (jsize i = 0; i < n; i++) wanted.push_back((uint32_t) raw[i]);
    env->ReleaseIntArrayElements(sectors, raw, JNI_ABORT);
    bool ok = false;
    auto read = AchievementsHash::chdSectors((int) fd, wanted, &ok);
    if (!ok) return nullptr;
    jclass bytes = env->FindClass("[B");
    jobjectArray result = env->NewObjectArray((jsize) read.size(), bytes, nullptr);
    for (size_t i = 0; i < read.size(); i++) {
        if (read[i].empty()) continue;
        jbyteArray b = env->NewByteArray((jsize) read[i].size());
        env->SetByteArrayRegion(b, 0, (jsize) read[i].size(), reinterpret_cast<const jbyte*>(read[i].data()));
        env->SetObjectArrayElement(result, (jsize) i, b);
        env->DeleteLocalRef(b);
    }
    env->DeleteLocalRef(bytes);
    return result;
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_achievementsSetDiscLibrary(
    JNIEnv* env,
    jclass obj,
    jstring path
) {
    JniString p(env, path);
    AchievementsHash::setDiscLibraryPath(p.stdString());
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_unserializeState(
    JNIEnv* env,
    jclass obj,
    jbyteArray state
) {
    try {
        jboolean isCopy = JNI_FALSE;
        jbyte* data = env->GetByteArrayElements(state, &isCopy);
        jsize size = env->GetArrayLength(state);

        bool result = LibretroDroid::getInstance().unserializeState(data, size);
        env->ReleaseByteArrayElements(state, data, JNI_ABORT);

        return result ? JNI_TRUE : JNI_FALSE;

    } catch (std::exception &exception) {
        LOGE("Error in unserializeState: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_SERIALIZATION);
        return JNI_FALSE;
    }
}

JNIEXPORT jbyteArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_serializeState(
    JNIEnv* env,
    jclass obj
) {
    try {
        auto [data, size] = LibretroDroid::getInstance().serializeState();
        std::unique_ptr<int8_t[]> owned(data);

        jbyteArray result = env->NewByteArray(size);
        if (result != nullptr && size > 0) env->SetByteArrayRegion(result, 0, size, owned.get());

        return result;

    } catch (std::exception &exception) {
        LOGE("Error in serializeState: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_SERIALIZATION);
    }

    return nullptr;
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setCheat(
    JNIEnv* env,
    jclass obj,
    jint index,
    jboolean enabled,
    jstring code
) {
    try {
        auto codeString = JniString(env, code);
        LibretroDroid::getInstance().setCheat(index, enabled, codeString.stdString());
    } catch (std::exception &exception) {
        LOGE("Error in setCheat: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_CHEAT);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setMemoryCheats(
    JNIEnv* env,
    jclass obj,
    jintArray fields
) {
    // nx-mobile: 9 ints per patch, in CheatMemPatch order from address (MemoryPatch.packed()).
    constexpr jsize PER_PATCH = 9;
    try {
        jsize n = fields ? env->GetArrayLength(fields) : 0;
        std::vector<jint> v(n);
        if (n) env->GetIntArrayRegion(fields, 0, n, v.data());
        std::vector<CheatMemPatch> patches;
        for (jsize i = 0; i + PER_PATCH <= n; i += PER_PATCH) {
            CheatMemPatch p;
            CheatMemPatch_init(&p);
            p.memory = 1;
            p.address = (unsigned) v[i];
            p.value = (unsigned) v[i + 1];
            p.size = (unsigned) v[i + 2];
            p.type = (unsigned) v[i + 3];
            p.bit_mask = (unsigned) v[i + 4];
            p.big_endian = v[i + 5];
            p.repeat_count = (unsigned) v[i + 6];
            p.repeat_add_to_value = (unsigned) v[i + 7];
            p.repeat_add_to_address = (unsigned) v[i + 8];
            patches.push_back(p);
        }
        LibretroDroid::getInstance().setMemoryCheats(std::move(patches));
    } catch (std::exception &exception) {
        LOGE("Error in setMemoryCheats: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_CHEAT);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_resetCheat(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().resetCheat();
    } catch (std::exception &exception) {
        LOGE("Error in resetCheat: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_CHEAT);
    }
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_unserializeSRAM(
    JNIEnv* env,
    jclass obj,
    jbyteArray sram
) {
    try {
        jboolean isCopy = JNI_FALSE;
        jbyte* data = env->GetByteArrayElements(sram, &isCopy);
        jsize size = env->GetArrayLength(sram);

        LibretroDroid::getInstance().unserializeSRAM(data, size);

        env->ReleaseByteArrayElements(sram, data, JNI_ABORT);

    } catch (std::exception &exception) {
        LOGE("Error in unserializeSRAM: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_SERIALIZATION);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

JNIEXPORT jbyteArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_serializeSRAM(
    JNIEnv* env,
    jclass obj
) {
    try {
        auto [data, size] = LibretroDroid::getInstance().serializeSRAM();
        std::unique_ptr<int8_t[]> owned(data);

        jbyteArray result = env->NewByteArray(size);
        if (result != nullptr && size > 0) env->SetByteArrayRegion(result, 0, size, (jbyte *) owned.get());

        return result;

    } catch (std::exception &exception) {
        LOGE("Error in serializeSRAM: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_SERIALIZATION);
    }

    return nullptr;
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_unserializeRTC(
    JNIEnv* env,
    jclass obj,
    jbyteArray rtc
) {
    try {
        jboolean isCopy = JNI_FALSE;
        jbyte* data = env->GetByteArrayElements(rtc, &isCopy);
        jsize size = env->GetArrayLength(rtc);

        LibretroDroid::getInstance().unserializeRTC(data, size);

        env->ReleaseByteArrayElements(rtc, data, JNI_ABORT);

    } catch (std::exception &exception) {
        LOGE("Error in unserializeRTC: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_SERIALIZATION);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

JNIEXPORT jbyteArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_serializeRTC(
    JNIEnv* env,
    jclass obj
) {
    try {
        auto [data, size] = LibretroDroid::getInstance().serializeRTC();
        std::unique_ptr<int8_t[]> owned(data);

        jbyteArray result = env->NewByteArray(size);
        if (result != nullptr && size > 0) env->SetByteArrayRegion(result, 0, size, (jbyte *) owned.get());

        return result;

    } catch (std::exception &exception) {
        LOGE("Error in serializeRTC: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_SERIALIZATION);
    }

    return nullptr;
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_reset(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().reset();
    } catch (std::exception &exception) {
        LOGE("Error in clear: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onSurfaceChanged(
    JNIEnv* env,
    jclass obj,
    jint width,
    jint height
) {
    LibretroDroid::getInstance().onSurfaceChanged(width, height);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onSurfaceCreated(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().onSurfaceCreated();
    } catch (libretrodroid::LibretroDroidError& exception) {
        LOGE("Error in onSurfaceCreated: %s", exception.what());
        JavaUtils::throwRetroException(env, exception.getErrorCode());
    } catch (std::exception &exception) {
        LOGE("Error in onSurfaceCreated: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onMotionEvent(
    JNIEnv* env,
    jclass obj,
    jint port,
    jint source,
    jfloat xAxis,
    jfloat yAxis
) {
    LibretroDroid::getInstance().onMotionEvent(port, source, xAxis, yAxis);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onTouchEvent(
    JNIEnv* env,
    jclass obj,
    jfloat xAxis,
    jfloat yAxis
) {
    LibretroDroid::getInstance().onTouchEvent(xAxis, yAxis);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onTriggerEvent(
    JNIEnv* env,
    jclass obj,
    jint port,
    jfloat l2,
    jfloat r2
) {
    LibretroDroid::getInstance().onTriggerEvent(port, l2, r2);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_onKeyEvent(
    JNIEnv* env,
    jclass obj,
    jint port,
    jint action,
    jint keyCode
) {
    LibretroDroid::getInstance().onKeyEvent(port, action, keyCode);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_create(
    JNIEnv* env,
    jclass obj,
    jint GLESVersion,
    jstring soFilePath,
    jstring systemDir,
    jstring savesDir,
    jobjectArray jVariables,
    jobject shaderConfig,
    jfloat refreshRate,
    jboolean preferLowLatencyAudio,
    jboolean enableVirtualFileSystem,
    jboolean enableMicrophone,
    jboolean skipDuplicateFrames,
    jobject immersiveMode,
    jstring language
) {
    try {
        auto corePath = JniString(env, soFilePath);
        auto deviceLanguage = JniString(env, language);
        auto systemDirectory = JniString(env, systemDir);
        auto savesDirectory = JniString(env, savesDir);

        std::vector<Variable> variables;
        int size = env->GetArrayLength(jVariables);
        for (int i = 0; i < size; i++) {
            auto jVariable = (jobject) env->GetObjectArrayElement(jVariables, i);
            auto variable = JavaUtils::variableFromJava(env, jVariable);
            variables.push_back(variable);
        }

        std::optional<ImmersiveMode::Config> parsedConfig = std::nullopt;
        if (immersiveMode != nullptr) {
            jclass configClass = env->GetObjectClass(immersiveMode);
            jfieldID downscaledWidthField = env->GetFieldID(configClass, "downscaledWidth", "I");
            jfieldID downscaledHeightField = env->GetFieldID(configClass, "downscaledHeight", "I");
            jfieldID blurMaskSizeField = env->GetFieldID(configClass, "blurMaskSize", "I");
            jfieldID blurBrightnessField = env->GetFieldID(configClass, "blurBrightness", "F");
            jfieldID blurSkipUpdateField = env->GetFieldID(configClass, "blurSkipUpdate", "I");
            jfieldID blendFactorField = env->GetFieldID(configClass, "blendFactor", "F");

            ImmersiveMode::Config config {};
            config.downscaledWidth = env->GetIntField(immersiveMode, downscaledWidthField);
            config.downscaledHeight = env->GetIntField(immersiveMode, downscaledHeightField);
            config.blurMaskSize = env->GetIntField(immersiveMode, blurMaskSizeField);
            config.blurBrightness = env->GetFloatField(immersiveMode, blurBrightnessField);
            config.blurSkipUpdate = env->GetIntField(immersiveMode, blurSkipUpdateField);
            config.blendFactor = env->GetFloatField(immersiveMode, blendFactorField);
            parsedConfig = config;
        }

        LibretroDroid::getInstance().create(
            GLESVersion,
            corePath.stdString(),
            systemDirectory.stdString(),
            savesDirectory.stdString(),
            variables,
            JavaUtils::shaderFromJava(env, shaderConfig),
            refreshRate,
            preferLowLatencyAudio,
            enableVirtualFileSystem,
            enableMicrophone,
            skipDuplicateFrames,
            parsedConfig,
            deviceLanguage.stdString()
        );

    } catch (libretrodroid::LibretroDroidError& exception) {
        LOGE("Error in create: %s", exception.what());
        JavaUtils::throwRetroException(env, exception.getErrorCode());
    } catch (std::exception &exception) {
        LOGE("Error in create: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_LOAD_LIBRARY);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_loadGameFromPath(
    JNIEnv* env,
    jclass obj,
    jstring gameFilePath
) {
    auto gamePath = JniString(env, gameFilePath);

    try {
        LibretroDroid::getInstance().loadGameFromPath(gamePath.stdString());
    } catch (std::exception &exception) {
        LOGE("Error in loadGameFromPath: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_LOAD_GAME);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_loadGameFromBytes(
    JNIEnv* env,
    jclass obj,
    jbyteArray gameFileBytes
) {
    try {
        size_t size = env->GetArrayLength(gameFileBytes);
        auto data = std::make_unique<int8_t[]>(size);
        env->GetByteArrayRegion(
            gameFileBytes,
            0,
            size,
            reinterpret_cast<jbyte*>(data.get())
        );
        // Owned by LibretroDroid until retro_unload_game.
        LibretroDroid::getInstance().loadGameFromBytes(std::move(data), size);
    } catch (std::exception &exception) {
        LOGE("Error in loadGameFromBytes: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_LOAD_GAME);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_loadGameFromVirtualFiles(
        JNIEnv* env,
        jclass obj,
        jobject virtualFileList
) {

    try {
        jmethodID getVirtualFileMethodID = env->GetMethodID(
                env->FindClass("com/swordfish/libretrodroid/DetachedVirtualFile"),
                "getVirtualPath",
                "()Ljava/lang/String;"
        );
        jmethodID getFileDescriptorMethodID = env->GetMethodID(
                env->FindClass("com/swordfish/libretrodroid/DetachedVirtualFile"),
                "getFileDescriptor",
                "()I"
        );

        std::vector<VFSFile> virtualFiles;

        JavaUtils::forEachOnJavaIterable(env, virtualFileList, [&](jobject item) {
            JniString virtualFileName(env,(jstring) env->CallObjectMethod(
                item,
                getVirtualFileMethodID
            ));

            int fileDescriptor = env->CallIntMethod(item, getFileDescriptorMethodID);
            virtualFiles.emplace_back(VFSFile(virtualFileName.stdString(), fileDescriptor));
        });

        LibretroDroid::getInstance().loadGameFromVirtualFiles(std::move(virtualFiles));
    } catch (std::exception &exception) {
        LOGE("Error in loadGameFromDescriptors: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_LOAD_GAME);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_destroy(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().destroy();
    } catch (std::exception &exception) {
        LOGE("Error in destroy: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_resume(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().resume();
    } catch (std::exception &exception) {
        LOGE("Error in resume: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_pause(
    JNIEnv* env,
    jclass obj
) {
    try {
        LibretroDroid::getInstance().pause();
    } catch (std::exception &exception) {
        LOGE("Error in pause: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_step(
    JNIEnv* env,
    jclass obj,
    jobject glRetroView
) {
    try {
        LibretroDroid::getInstance().step();
    } catch (std::exception &exception) {
        LOGE("Error in step: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
        return;
    }

    auto& achievements = Achievements::getInstance();
    if (achievements.isEnabled()) {
        auto calls = achievements.takeServerCalls();
        auto events = achievements.takeEvents();
        if (!calls.empty() || !events.empty()) {
            jclass cls = env->GetObjectClass(glRetroView);
            jmethodID sendCall = env->GetMethodID(cls, "sendAchievementServerCall", "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
            jmethodID sendEvent = env->GetMethodID(cls, "sendAchievementEvent", "(IILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;FLjava/lang/String;)V");
            for (auto& c : calls) {
                jstring url = newSafeJString(env, c.url), post = newSafeJString(env, c.postData), type = newSafeJString(env, c.contentType);
                env->CallVoidMethod(glRetroView, sendCall, (jint) c.requestId, url, post, type);
                env->DeleteLocalRef(url); env->DeleteLocalRef(post); env->DeleteLocalRef(type);
            }
            for (auto& e : events) {
                // The server's text (an emoji in a title): never straight to NewStringUTF.
                jstring title = newSafeJString(env, e.title), desc = newSafeJString(env, e.description), badge = newSafeJString(env, e.badge),
                        progress = newSafeJString(env, e.progress), extra = newSafeJString(env, e.extra);
                env->CallVoidMethod(glRetroView, sendEvent, (jint) e.type, (jint) e.id, title, desc, badge, (jint) e.points, progress, (jfloat) e.percent, extra);
                env->DeleteLocalRef(title); env->DeleteLocalRef(desc); env->DeleteLocalRef(badge); env->DeleteLocalRef(progress); env->DeleteLocalRef(extra);
            }
            env->DeleteLocalRef(cls);
        }
    }

    if (LibretroDroid::getInstance().requiresVideoRefresh()) {
        LibretroDroid::getInstance().clearRequiresVideoRefresh();
        jclass cls = env->GetObjectClass(glRetroView);
        jmethodID requestAspectRatioUpdate = env->GetMethodID(cls, "refreshAspectRatio", "()V");
        env->CallVoidMethod(glRetroView, requestAspectRatioUpdate);
    }

    if (LibretroDroid::getInstance().isRumbleEnabled()) {
        LibretroDroid::getInstance().handleRumbleUpdates([&](int port, float weak, float strong) {
            jclass cls = env->GetObjectClass(glRetroView);
            jmethodID sendRumbleStrengthMethodID = env->GetMethodID(cls, "sendRumbleEvent", "(IFF)V");
            env->CallVoidMethod(glRetroView, sendRumbleStrengthMethodID, port, weak, strong);
        });
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setRumbleEnabled(
    JNIEnv* env,
    jclass obj,
    jboolean enabled
) {
    LibretroDroid::getInstance().setRumbleEnabled(enabled);
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_takeShutdownRequest(
    JNIEnv* env,
    jclass obj
) {
    return Environment::getInstance().takeShutdownRequest();
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_takeGameGeometryChanged(
    JNIEnv* env,
    jclass obj
) {
    return LibretroDroid::getInstance().takeGameGeometryChanged();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setPreferVulkan(
    JNIEnv* env,
    jclass obj,
    jboolean enabled
) {
    LibretroDroid::getInstance().setPreferVulkan(enabled);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setVulkanSwapchainFrames(
    JNIEnv* env,
    jclass obj,
    jboolean enabled
) {
    LibretroDroid::getInstance().setVulkanSwapchainFrames(enabled);
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_isVulkanSupported(
    JNIEnv* env,
    jclass obj
) {
    return VulkanBridge::isSupported();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setGlDebugOutput(
    JNIEnv* env,
    jclass obj,
    jboolean enabled
) {
    gldebug::setEnabled(enabled);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setLogEnabled(
    JNIEnv* env,
    jclass obj,
    jboolean enabled
) {
    nx_log_set_enabled(enabled);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setKeepCoreLoaded(
    JNIEnv* env,
    jclass obj,
    jboolean keep
) {
    coreretention::setKeepLoaded(keep);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setFrameSpeed(
    JNIEnv* env,
    jclass obj,
    jint speed
) {
    LibretroDroid::getInstance().setFrameSpeed(speed);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setAudioEnabled(
    JNIEnv* env,
    jclass obj,
    jboolean enabled
) {
    LibretroDroid::getInstance().setAudioEnabled(enabled);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setFastForward(
    JNIEnv* env,
    jclass obj,
    jint maxSpeed,
    jboolean audible
) {
    LibretroDroid::getInstance().setFastForward(maxSpeed > 0 ? (unsigned) maxSpeed : 0u, audible);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setMicrophoneSource(
    JNIEnv* env,
    jclass obj,
    jint source
) {
    MicrophoneInterface::setSource(source);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setRewindEnabled(
    JNIEnv* env,
    jclass obj,
    jboolean enabled
) {
    LibretroDroid::getInstance().setRewindEnabled(enabled);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setRewinding(
    JNIEnv* env,
    jclass obj,
    jboolean rewinding
) {
    LibretroDroid::getInstance().setRewinding(rewinding);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setRewindAudio(
    JNIEnv* env,
    jclass obj,
    jboolean audible
) {
    LibretroDroid::getInstance().setRewindAudio(audible);
}

JNIEXPORT jint JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_rewindStatus(
    JNIEnv* env,
    jclass obj
) {
    return LibretroDroid::getInstance().rewindStatus();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setShaderConfig(
    JNIEnv* env,
    jclass obj,
    jobject shaderConfig
) {
    try {
        LibretroDroid::getInstance().setShaderConfig(JavaUtils::shaderFromJava(env, shaderConfig));
    } catch (std::exception &exception) {
        LOGE("Error in setShaderConfig: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setViewport(
    JNIEnv* env,
    jclass obj,
    jfloat x,
    jfloat y,
    jfloat width,
    jfloat height
) {
    LibretroDroid::getInstance().setViewport(Rect(x, y, width, height));
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setViewportAlignment(
        JNIEnv* env,
        jclass obj,
        jint viewportAlignment
) {
    LibretroDroid::getInstance().setViewportAlignment(viewportAlignment);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_refreshAspectRatio(
    JNIEnv* env,
    jclass obj
) {
    LibretroDroid::getInstance().refreshAspectRatio();
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setScaleMode(
    JNIEnv* env,
    jclass obj,
    jint scaleMode
) {
    LibretroDroid::getInstance().setScaleMode(scaleMode);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setScreenOffset(
    JNIEnv* env,
    jclass obj,
    jfloat x,
    jfloat y
) {
    LibretroDroid::getInstance().setScreenOffset(x, y);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setScreenRegions(
    JNIEnv* env,
    jclass obj,
    jfloatArray packed
) {
    jsize count = packed != nullptr ? env->GetArrayLength(packed) : 0;
    std::vector<float> data((size_t) count);
    if (count > 0) env->GetFloatArrayRegion(packed, 0, count, data.data());
    LibretroDroid::getInstance().setScreenRegions(ScreenRegion::unpack(data.data(), data.size()));
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setSecondaryRegions(
    JNIEnv* env,
    jclass obj,
    jfloatArray packed
) {
    jsize count = packed != nullptr ? env->GetArrayLength(packed) : 0;
    std::vector<float> data((size_t) count);
    if (count > 0) env->GetFloatArrayRegion(packed, 0, count, data.data());
    LibretroDroid::getInstance().setSecondaryRegions(ScreenRegion::unpack(data.data(), data.size()));
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_renderSecondary(
    JNIEnv* env,
    jclass obj,
    jint width,
    jint height
) {
    if (width <= 0 || height <= 0) return;
    LibretroDroid::getInstance().renderSecondary((unsigned) width, (unsigned) height);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setShaderChain(
    JNIEnv* env,
    jclass obj,
    jobjectArray sources,
    jintArray filterLinear,
    jintArray wrap,
    jintArray scaleTypeX,
    jintArray scaleTypeY,
    jfloatArray scaleX,
    jfloatArray scaleY,
    jintArray frameCountMod,
    jobjectArray alias,
    jobjectArray lutIds,
    jintArray lutWidth,
    jintArray lutHeight,
    jobjectArray lutRgba,
    jbooleanArray lutLinear,
    jintArray lutWrap,
    jobjectArray paramIds,
    jfloatArray paramValues
) {
    try {
        jsize passCount = presetArrayLength(env, sources);
        for (jarray array : std::initializer_list<jarray> {
            filterLinear, wrap, scaleTypeX, scaleTypeY, scaleX, scaleY, frameCountMod, alias
        }) {
            if (presetArrayLength(env, array) != passCount) {
                LOGE("setShaderChain: pass arrays differ in length");
                return;
            }
        }
        jsize lutCount = presetArrayLength(env, lutIds);
        for (jarray array : std::initializer_list<jarray> { lutWidth, lutHeight, lutRgba, lutLinear, lutWrap }) {
            if (presetArrayLength(env, array) != lutCount) {
                LOGE("setShaderChain: lut arrays differ in length");
                return;
            }
        }
        if (presetArrayLength(env, paramValues) != presetArrayLength(env, paramIds)) {
            LOGE("setShaderChain: param arrays differ in length");
            return;
        }

        auto sourceValues = presetStrings(env, sources);
        auto filterValues = presetInts(env, filterLinear);
        auto wrapValues = presetInts(env, wrap);
        auto scaleTypeXValues = presetInts(env, scaleTypeX);
        auto scaleTypeYValues = presetInts(env, scaleTypeY);
        auto scaleXValues = presetFloats(env, scaleX);
        auto scaleYValues = presetFloats(env, scaleY);
        auto frameCountModValues = presetInts(env, frameCountMod);
        auto aliasValues = presetStrings(env, alias);

        PresetChain chain;
        for (jsize i = 0; i < passCount; i++) {
            PresetPass pass;
            pass.source = sourceValues[i];
            pass.filterLinear = filterValues[i] < 0 ? std::nullopt : std::optional<bool>(filterValues[i] != 0);
            pass.wrap = wrapValues[i];
            pass.scaleTypeX = scaleTypeXValues[i];
            pass.scaleTypeY = scaleTypeYValues[i];
            pass.scaleX = scaleXValues[i];
            pass.scaleY = scaleYValues[i];
            pass.frameCountMod = (unsigned) std::max(0, (int) frameCountModValues[i]);
            pass.alias = aliasValues[i];
            chain.passes.push_back(std::move(pass));
        }

        auto lutIdValues = presetStrings(env, lutIds);
        auto lutWidthValues = presetInts(env, lutWidth);
        auto lutHeightValues = presetInts(env, lutHeight);
        auto lutLinearValues = presetBooleans(env, lutLinear);
        auto lutWrapValues = presetInts(env, lutWrap);

        for (jsize i = 0; i < lutCount; i++) {
            auto rgba = (jbyteArray) env->GetObjectArrayElement(lutRgba, i);
            jsize rgbaLength = presetArrayLength(env, rgba);
            int width = lutWidthValues[i];
            int height = lutHeightValues[i];
            if (width <= 0 || height <= 0 || (int64_t) rgbaLength != (int64_t) width * height * 4) {
                LOGE("setShaderChain: lut %s has %d bytes for %dx%d", lutIdValues[i].c_str(), rgbaLength, width, height);
                if (rgba != nullptr) env->DeleteLocalRef(rgba);
                return;
            }

            PresetLut lut;
            lut.id = lutIdValues[i];
            lut.width = (unsigned) width;
            lut.height = (unsigned) height;
            lut.rgba.resize(rgbaLength);
            env->GetByteArrayRegion(rgba, 0, rgbaLength, reinterpret_cast<jbyte*>(lut.rgba.data()));
            env->DeleteLocalRef(rgba);
            lut.linear = lutLinearValues[i] == JNI_TRUE;
            lut.wrap = lutWrapValues[i];
            chain.luts.push_back(std::move(lut));
        }

        auto paramIdValues = presetStrings(env, paramIds);
        auto paramFloatValues = presetFloats(env, paramValues);
        for (size_t i = 0; i < paramIdValues.size(); i++) {
            chain.params[paramIdValues[i]] = paramFloatValues[i];
        }

        LibretroDroid::getInstance().setPresetChain(std::move(chain));
    } catch (std::exception &exception) {
        LOGE("Error in setShaderChain: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_clearShaderChain(
    JNIEnv* env,
    jclass obj
) {
    LibretroDroid::getInstance().setPresetChain(std::nullopt);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setShaderParameter(
    JNIEnv* env,
    jclass obj,
    jstring id,
    jfloat value
) {
    if (id == nullptr) return;
    JniString idString(env, id);
    LibretroDroid::getInstance().setPresetParameter(idString.stdString(), value);
}

JNIEXPORT jstring JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_takeShaderError(
    JNIEnv* env,
    jclass obj
) {
    auto error = LibretroDroid::getInstance().takePresetError();
    return error ? newSafeJString(env, *error) : nullptr;   // may quote a preset's file names and lines
}

JNIEXPORT jfloatArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_getGameGeometry(
    JNIEnv* env,
    jclass obj
) {
    auto geometry = LibretroDroid::getInstance().getGameGeometry();
    jfloatArray result = env->NewFloatArray(geometry.size());
    env->SetFloatArrayRegion(result, 0, geometry.size(), geometry.data());
    return result;
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_isHardwareRendered(
    JNIEnv* env,
    jclass obj
) {
    return LibretroDroid::getInstance().isHardwareRendered() ? JNI_TRUE : JNI_FALSE;
}

}

}
