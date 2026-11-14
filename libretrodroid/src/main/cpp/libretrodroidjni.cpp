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

#include "debugprop.h"
#include <jni.h>

#include <EGL/egl.h>

#include <algorithm>
#include <initializer_list>
#include <limits>
#include <unistd.h>
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
// Outside the namespace block below: these headers open namespace libretrodroid themselves. Included inside it, the
// exception became libretrodroid::libretrodroid::LibretroDroidError, a type nothing throws, so every
// catch (LibretroDroidError&) here missed and its error code reached Kotlin as ERROR_GENERIC.
#include "utils/utils.h"
#include "utils/libretrodroidexception.h"

namespace libretrodroid {

extern "C" {
#include "../../libretro-common/include/libretro.h"
}

namespace {

// Null arrays read as empty so a length guard catches them.
jsize javaArrayLength(JNIEnv* env, jarray array) {
    return array == nullptr ? 0 : env->GetArrayLength(array);
}

std::vector<std::string> javaStrings(JNIEnv* env, jobjectArray array) {
    std::vector<std::string> result;
    jsize length = javaArrayLength(env, array);
    for (jsize i = 0; i < length; i++) {
        auto value = (jstring) env->GetObjectArrayElement(array, i);
        result.push_back(JniString(env, value).stdString());
        env->DeleteLocalRef(value);
    }
    return result;
}

std::vector<jint> javaInts(JNIEnv* env, jintArray array) {
    std::vector<jint> result(javaArrayLength(env, array));
    if (!result.empty()) env->GetIntArrayRegion(array, 0, (jsize) result.size(), result.data());
    return result;
}

std::vector<jfloat> javaFloats(JNIEnv* env, jfloatArray array) {
    std::vector<jfloat> result(javaArrayLength(env, array));
    if (!result.empty()) env->GetFloatArrayRegion(array, 0, (jsize) result.size(), result.data());
    return result;
}

std::vector<jboolean> javaBooleans(JNIEnv* env, jbooleanArray array) {
    std::vector<jboolean> result(javaArrayLength(env, array));
    if (!result.empty()) env->GetBooleanArrayRegion(array, 0, (jsize) result.size(), result.data());
    return result;
}

// fn(data, size) over array's bytes, released (unchanged) however fn exits. False for a null array or when the bytes
// could not be pinned (an OutOfMemoryError is then pending).
template <typename Fn>
jboolean withBytes(JNIEnv* env, jbyteArray array, Fn&& fn) {
    if (array == nullptr) return JNI_FALSE;
    jbyte* data = env->GetByteArrayElements(array, nullptr);
    if (data == nullptr) return JNI_FALSE;
    struct Release {
        JNIEnv* env; jbyteArray array; jbyte* data;
        ~Release() { env->ReleaseByteArrayElements(array, data, JNI_ABORT); }
    } release { env, array, data };
    return fn(reinterpret_cast<int8_t*>(data), (size_t) env->GetArrayLength(array)) ? JNI_TRUE : JNI_FALSE;
}

bool fitsJavaArray(size_t size) {
    return size <= (size_t) std::numeric_limits<jsize>::max();
}

// A copy of the memory a serializeSRAM-like call reads inside its lock, as a Java array: empty when there is none, null
// with an OutOfMemoryError pending.
template <typename Serialize>
jbyteArray memoryToJava(JNIEnv* env, Serialize&& serialize) {
    jbyteArray result = nullptr;
    serialize([&](const int8_t* data, size_t size) {
        if (!fitsJavaArray(size)) {
            LOGE("Memory of %zu bytes does not fit a Java array", size);
            return;
        }
        result = env->NewByteArray((jsize) size);
        if (result != nullptr) env->SetByteArrayRegion(result, 0, (jsize) size, data);
    });
    if (result != nullptr || env->ExceptionCheck()) return result;
    return env->NewByteArray(0);
}

// The Java byte[] a save state is serialized straight into, so a state of tens of MB (PS2, Wii) is never held twice.
// NewByteArray zeroes it, as serializeState's sink must. GetByteArrayElements, not GetPrimitiveArrayCritical: a core
// may run emulation inside retro_serialize (Azahar drains its async work), calling into Java or waiting on threads that
// allocate, which a critical region forbids. ART keeps an array this large in its non-moving large-object space, so the
// elements are the array itself; a small one may be a copy, written back on release.
class StateArray {
public:
    explicit StateArray(JNIEnv* env) : env(env) {}
    ~StateArray() { release(JNI_ABORT); }

    StateArray(const StateArray&) = delete;
    StateArray& operator=(const StateArray&) = delete;

    int8_t* take(size_t size) {
        if (!fitsJavaArray(size)) {
            LOGE("Cannot save state: %zu bytes do not fit a Java array", size);
            return nullptr;
        }
        array = env->NewByteArray((jsize) size);
        if (array == nullptr) return nullptr;   // OutOfMemoryError pending
        elements = env->GetByteArrayElements(array, nullptr);
        return reinterpret_cast<int8_t*>(elements);
    }

    // The filled array, else an empty one (no state). Null with a Java exception pending.
    jbyteArray finish(bool filled) {
        if (filled && elements != nullptr) {
            release(0);
            return array;
        }
        release(JNI_ABORT);
        if (array != nullptr) env->DeleteLocalRef(array);
        array = nullptr;
        return env->ExceptionCheck() ? nullptr : env->NewByteArray(0);
    }

private:
    void release(jint mode) {
        if (elements == nullptr) return;
        env->ReleaseByteArrayElements(array, elements, mode);
        elements = nullptr;
    }

    JNIEnv* env;
    jbyteArray array = nullptr;
    jbyte* elements = nullptr;
};

// Each DetachedVirtualFile of list as File(virtual path, file descriptor). Stops at a Java exception, which it leaves
// pending: the caller checks ExceptionCheck() and closes the files read so far. Each item read is marked taken, so the
// Java side closes only the fds of the items never reached.
template <typename File>
std::vector<File> virtualFilesFromJava(JNIEnv* env, jobject list) {
    std::vector<File> files;
    jclass dvf = env->FindClass("com/swordfish/libretrodroid/DetachedVirtualFile");
    if (dvf == nullptr) return files;
    jmethodID getPath = env->GetMethodID(dvf, "getVirtualPath", "()Ljava/lang/String;");
    jmethodID getFd = getPath != nullptr ? env->GetMethodID(dvf, "getFileDescriptor", "()I") : nullptr;
    jfieldID taken = getFd != nullptr ? env->GetFieldID(dvf, "taken", "Z") : nullptr;
    if (taken != nullptr) {
        JavaUtils::forEachOnJavaIterable(env, list, [&](jobject item) {
            auto path = (jstring) env->CallObjectMethod(item, getPath);
            if (env->ExceptionCheck()) return;
            std::string virtualPath = JniString(env, path).stdString();
            env->DeleteLocalRef(path);
            auto fd = (int) env->CallIntMethod(item, getFd);
            if (env->ExceptionCheck()) return;
            files.emplace_back(std::move(virtualPath), fd);
            env->SetBooleanField(item, taken, JNI_TRUE);
        });
    }
    env->DeleteLocalRef(dvf);
    return files;
}

// A Java exception from a GLRetroView callback is logged and dropped: the next JNI call must not see it pending.
void dropJavaException(JNIEnv* env) {
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
}

// cache, looked up on view's class the first time. The class is the app's, never unloaded, so the ID stays valid.
// Null (the lookup's NoSuchMethodError cleared) when the method is missing.
jmethodID viewMethod(JNIEnv* env, jobject view, jmethodID& cache, const char* name, const char* signature) {
    if (cache == nullptr) {
        jclass cls = env->GetObjectClass(view);
        cache = env->GetMethodID(cls, name, signature);
        env->DeleteLocalRef(cls);
        if (cache == nullptr) env->ExceptionClear();
    }
    return cache;
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
    env->DeleteLocalRef(variableClass);
    return result;
}

JNIEXPORT jobjectArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_getControllers(
    JNIEnv* env,
    jclass obj
) {
    jclass arrayClass = env->FindClass("[Lcom/swordfish/libretrodroid/Controller;");
    jclass controllerClass = env->FindClass("com/swordfish/libretrodroid/Controller");
    jmethodID controllerConstructor = env->GetMethodID(controllerClass, "<init>", "()V");
    jfieldID jIdField = env->GetFieldID(controllerClass, "id", "I");
    jfieldID jDescriptionField = env->GetFieldID(controllerClass, "description", "Ljava/lang/String;");

    auto controllers = Environment::getInstance().getControllers();
    jobjectArray result = env->NewObjectArray(controllers.size(), arrayClass, nullptr);

    for (int i = 0; i < controllers.size(); i++) {
        jobjectArray controllerArray = env->NewObjectArray(controllers[i].size(), controllerClass, nullptr);

        for (int j = 0; j < controllers[i].size(); j++) {
            jobject jController = env->NewObject(controllerClass, controllerConstructor);
            env->SetIntField(jController, jIdField, (int) controllers[i][j].id);
            setStringField(env, jController, jDescriptionField, controllers[i][j].description);
            env->SetObjectArrayElement(controllerArray, j, jController);
            env->DeleteLocalRef(jController);
        }

        env->SetObjectArrayElement(result, i, controllerArray);
        env->DeleteLocalRef(controllerArray);
    }
    env->DeleteLocalRef(controllerClass);
    env->DeleteLocalRef(arrayClass);
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
    auto files = virtualFilesFromJava<std::pair<std::string, int>>(env, fileList);
    if (env->ExceptionCheck()) {
        // hash() closes the fds it is given; these never reach it.
        for (auto& file : files) close(file.second);
        return nullptr;
    }
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
    for (jint sector : javaInts(env, sectors)) wanted.push_back((uint32_t) sector);
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
        return withBytes(env, state, [](int8_t* data, size_t size) {
            return LibretroDroid::getInstance().unserializeState(data, size);
        });
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
        StateArray state(env);
        bool filled = LibretroDroid::getInstance().serializeState([&](size_t size) { return state.take(size); });
        return state.finish(filled);
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
    // 9 ints per patch, in CheatMemPatch order from address (MemoryPatch.packed()).
    constexpr jsize PER_PATCH = 9;
    try {
        auto v = javaInts(env, fields);
        auto n = (jsize) v.size();
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
        return withBytes(env, sram, [](int8_t* data, size_t size) {
            return LibretroDroid::getInstance().unserializeSRAM(data, size);
        });
    } catch (std::exception &exception) {
        LOGE("Error in unserializeSRAM: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_SERIALIZATION);
        return JNI_FALSE;
    }
}

JNIEXPORT jbyteArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_serializeSRAM(
    JNIEnv* env,
    jclass obj
) {
    try {
        return memoryToJava(env, [](const LibretroDroid::MemoryReader& read) {
            return LibretroDroid::getInstance().serializeSRAM(read);
        });
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
        return withBytes(env, rtc, [](int8_t* data, size_t size) {
            return LibretroDroid::getInstance().unserializeRTC(data, size);
        });
    } catch (std::exception &exception) {
        LOGE("Error in unserializeRTC: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_SERIALIZATION);
        return JNI_FALSE;
    }
}

JNIEXPORT jbyteArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_serializeRTC(
    JNIEnv* env,
    jclass obj
) {
    try {
        return memoryToJava(env, [](const LibretroDroid::MemoryReader& read) {
            return LibretroDroid::getInstance().serializeRTC(read);
        });
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
            variables.push_back(JavaUtils::variableFromJava(env, jVariable));
            env->DeleteLocalRef(jVariable);
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

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_addVirtualFiles(
        JNIEnv* env,
        jclass obj,
        jobject virtualFileList
) {
    try {
        auto files = virtualFilesFromJava<VFSFile>(env, virtualFileList);
        if (env->ExceptionCheck()) return;
        LibretroDroid::getInstance().addVirtualFiles(std::move(files));
    } catch (std::exception &exception) {
        LOGE("Error in addVirtualFiles: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_LOAD_GAME);
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_loadGameFromVirtualFiles(
        JNIEnv* env,
        jclass obj,
        jobject virtualFileList
) {

    try {
        auto files = virtualFilesFromJava<VFSFile>(env, virtualFileList);
        if (env->ExceptionCheck()) return;
        LibretroDroid::getInstance().loadGameFromVirtualFiles(std::move(files));
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
        // A hw core's finished frame goes to the view's second surface from inside the frame.
        LibretroDroid::getInstance().setFrameDrawnHook([env, glRetroView]() {
            static jmethodID onFrame = nullptr;
            if (viewMethod(env, glRetroView, onFrame, "onCoreFrameDrawn", "()V") == nullptr) return;
            env->CallVoidMethod(glRetroView, onFrame);
            // Nothing may unwind through the core's frames.
            dropJavaException(env);
        });
        LibretroDroid::getInstance().step();
        LibretroDroid::getInstance().setFrameDrawnHook(nullptr);
    } catch (libretrodroid::LibretroDroidError& exception) {
        // Its own code: a custom Vulkan driver that cannot share frames is ERROR_GL_NOT_COMPATIBLE, a failed start.
        LibretroDroid::getInstance().setFrameDrawnHook(nullptr);
        LOGE("Error in step: %s", exception.what());
        JavaUtils::throwRetroException(env, exception.getErrorCode());
        return;
    } catch (std::exception &exception) {
        LibretroDroid::getInstance().setFrameDrawnHook(nullptr);
        LOGE("Error in step: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
        return;
    }

    auto coreMessages = Environment::getInstance().takeCoreMessages();
    static jmethodID sendCoreMessage = nullptr;
    if (!coreMessages.empty() &&
        viewMethod(env, glRetroView, sendCoreMessage, "sendCoreMessage", "(Ljava/lang/String;)V") != nullptr) {
        for (auto& m : coreMessages) {
            jstring text = newSafeJString(env, m);   // a core's text: never straight to NewStringUTF
            env->CallVoidMethod(glRetroView, sendCoreMessage, text);
            dropJavaException(env);
            env->DeleteLocalRef(text);
        }
    }

    auto& achievements = Achievements::getInstance();
    if (achievements.isEnabled()) {
        auto calls = achievements.takeServerCalls();
        auto events = achievements.takeEvents();
        static jmethodID sendCall = nullptr;
        static jmethodID sendEvent = nullptr;
        if (!calls.empty() && viewMethod(env, glRetroView, sendCall, "sendAchievementServerCall",
                                         "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V") != nullptr) {
            for (auto& c : calls) {
                jstring url = newSafeJString(env, c.url), post = newSafeJString(env, c.postData), type = newSafeJString(env, c.contentType);
                env->CallVoidMethod(glRetroView, sendCall, (jint) c.requestId, url, post, type);
                dropJavaException(env);
                env->DeleteLocalRef(url); env->DeleteLocalRef(post); env->DeleteLocalRef(type);
            }
        }
        if (!events.empty() && viewMethod(env, glRetroView, sendEvent, "sendAchievementEvent",
                                          "(IILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;FLjava/lang/String;)V") != nullptr) {
            for (auto& e : events) {
                // The server's text (an emoji in a title): never straight to NewStringUTF.
                jstring title = newSafeJString(env, e.title), desc = newSafeJString(env, e.description), badge = newSafeJString(env, e.badge),
                        progress = newSafeJString(env, e.progress), extra = newSafeJString(env, e.extra);
                env->CallVoidMethod(glRetroView, sendEvent, (jint) e.type, (jint) e.id, title, desc, badge, (jint) e.points, progress, (jfloat) e.percent, extra);
                dropJavaException(env);
                env->DeleteLocalRef(title); env->DeleteLocalRef(desc); env->DeleteLocalRef(badge); env->DeleteLocalRef(progress); env->DeleteLocalRef(extra);
            }
        }
    }

    if (LibretroDroid::getInstance().requiresVideoRefresh()) {
        LibretroDroid::getInstance().clearRequiresVideoRefresh();
        static jmethodID refreshAspectRatio = nullptr;
        if (viewMethod(env, glRetroView, refreshAspectRatio, "refreshAspectRatio", "()V") != nullptr) {
            env->CallVoidMethod(glRetroView, refreshAspectRatio);
            dropJavaException(env);
        }
    }

    if (LibretroDroid::getInstance().isRumbleEnabled()) {
        LibretroDroid::getInstance().handleRumbleUpdates([&](int port, float weak, float strong) {
            static jmethodID sendRumbleEvent = nullptr;
            if (viewMethod(env, glRetroView, sendRumbleEvent, "sendRumbleEvent", "(IFF)V") == nullptr) return;
            env->CallVoidMethod(glRetroView, sendRumbleEvent, port, weak, strong);
            dropJavaException(env);
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

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setVulkanDriver(
    JNIEnv* env,
    jclass obj,
    jstring driverDir,
    jstring libraryName,
    jstring hookLibDir,
    jstring tmpDir
) {
    LibretroDroid::getInstance().setVulkanDriver(JniString(env, driverDir).stdString(),
                                                 JniString(env, libraryName).stdString(),
                                                 JniString(env, hookLibDir).stdString(),
                                                 JniString(env, tmpDir).stdString());
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_probeVulkanDriver(
    JNIEnv* env,
    jclass obj,
    jstring driverDir,
    jstring libraryName,
    jstring hookLibDir,
    jstring tmpDir
) {
    try {
        return VulkanBridge::probeCustomDriver(JniString(env, driverDir).stdString(),
                                               JniString(env, libraryName).stdString(),
                                               JniString(env, hookLibDir).stdString(),
                                               JniString(env, tmpDir).stdString());
    } catch (std::exception& exception) {
        LOGE("Error in probeVulkanDriver: %s", exception.what());
        return false;
    }
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setVulkanSwapchainFrames(
    JNIEnv* env,
    jclass obj,
    jboolean enabled
) {
    LibretroDroid::getInstance().setVulkanSwapchainFrames(enabled);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setVideoViewsEnabled(
    JNIEnv* env,
    jclass obj,
    jboolean enabled
) {
    LibretroDroid::getInstance().setVideoViewsEnabled(enabled);
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_videoViewsOffered(
    JNIEnv* env,
    jclass obj
) {
    return debugPropertyOff("debug.nx.videoviews") ? JNI_FALSE : JNI_TRUE;
}

JNIEXPORT jintArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_getVideoViews(
    JNIEnv* env,
    jclass obj
) {
    auto views = LibretroDroid::getInstance().getVideoViews();
    std::vector<jint> flat;
    flat.reserve(views.size() * 6);
    for (const auto& v : views) {
        flat.insert(flat.end(), { (jint) v.x, (jint) v.y, (jint) v.width, (jint) v.height, (jint) v.screen, (jint) v.eye });
    }
    jintArray result = env->NewIntArray((jsize) flat.size());
    env->SetIntArrayRegion(result, 0, (jsize) flat.size(), flat.data());
    return result;
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

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setUsername(
    JNIEnv* env,
    jclass obj,
    jstring name
) {
    Environment::getInstance().setUsername(name == nullptr ? std::string() : JniString(env, name).stdString());
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setKeepCoreLoaded(
    JNIEnv* env,
    jclass obj,
    jboolean keep
) {
    coreretention::setKeepLoaded(keep);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setPresentationDeadline(
    JNIEnv* env,
    jclass obj,
    jlong nanos
) {
    LibretroDroid::getInstance().setPresentationDeadline(nanos);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setAudioBufferMs(
    JNIEnv* env,
    jclass obj,
    jint ms
) {
    LibretroDroid::getInstance().setAudioBufferMs(ms);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setFillAudioGaps(
    JNIEnv* env,
    jclass obj,
    jint mode
) {
    LibretroDroid::getInstance().setFillAudioGaps(mode);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setFrameSkip(
    JNIEnv* env,
    jclass obj,
    jboolean skip
) {
    LibretroDroid::getInstance().setFrameSkip(skip);
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

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setResamplingQuality(
    JNIEnv* env,
    jclass obj,
    jint quality
) {
    LibretroDroid::getInstance().setResamplingQuality(quality);
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

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setPictureHeld(
    JNIEnv* env,
    jclass obj,
    jboolean held
) {
    LibretroDroid::getInstance().setPictureHeld(held);
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setComposition(
    JNIEnv* env,
    jclass obj,
    jint width,
    jint height,
    jfloatArray packed
) {
    auto data = javaFloats(env, packed);
    LibretroDroid::getInstance().setComposition(width, height, ScreenRegion::unpack(data.data(), data.size()));
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setScreenRegions(
    JNIEnv* env,
    jclass obj,
    jfloatArray packed
) {
    auto data = javaFloats(env, packed);
    LibretroDroid::getInstance().setScreenRegions(ScreenRegion::unpack(data.data(), data.size()));
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setSecondaryRegions(
    JNIEnv* env,
    jclass obj,
    jfloatArray packed
) {
    auto data = javaFloats(env, packed);
    LibretroDroid::getInstance().setSecondaryRegions(ScreenRegion::unpack(data.data(), data.size()));
}

JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_renderSecondaryInFrame(
    JNIEnv* env,
    jclass obj,
    jint width,
    jint height
) {
    if (width <= 0 || height <= 0) return;
    LibretroDroid::getInstance().renderSecondaryInFrame((unsigned) width, (unsigned) height);
}

JNIEXPORT jboolean JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_rendersInVideoCallback(
    JNIEnv* env,
    jclass obj
) {
    return LibretroDroid::getInstance().rendersInVideoCallback();
}

JNIEXPORT jlong JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_getFrameDeadlineNanos(
    JNIEnv* env,
    jclass obj
) {
    return (jlong) LibretroDroid::getInstance().getFrameDeadlineNanos();
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

// The LUTs and parameters of a chain of either format, into chain. lutMipmap may be null (none mipmapped). False (logged)
// when the arrays disagree.
static bool readChainLutsAndParams(
    JNIEnv* env,
    PresetChain& chain,
    jobjectArray lutIds,
    jintArray lutWidth,
    jintArray lutHeight,
    jobjectArray lutRgba,
    jbooleanArray lutLinear,
    jintArray lutWrap,
    jbooleanArray lutMipmap,
    jobjectArray paramIds,
    jfloatArray paramValues
) {
    jsize lutCount = javaArrayLength(env, lutIds);
    for (jarray array : std::initializer_list<jarray> { lutWidth, lutHeight, lutRgba, lutLinear, lutWrap }) {
        if (javaArrayLength(env, array) != lutCount) {
            LOGE("setShaderChain: lut arrays differ in length");
            return false;
        }
    }
    if (javaArrayLength(env, paramValues) != javaArrayLength(env, paramIds)) {
        LOGE("setShaderChain: param arrays differ in length");
        return false;
    }
    if (lutMipmap != nullptr && javaArrayLength(env, lutMipmap) != lutCount) {
        LOGE("setShaderChain: lut arrays differ in length");
        return false;
    }

    auto lutIdValues = javaStrings(env, lutIds);
    auto lutWidthValues = javaInts(env, lutWidth);
    auto lutHeightValues = javaInts(env, lutHeight);
    auto lutLinearValues = javaBooleans(env, lutLinear);
    auto lutWrapValues = javaInts(env, lutWrap);
    std::vector<jboolean> lutMipmapValues;
    if (lutMipmap != nullptr) lutMipmapValues = javaBooleans(env, lutMipmap);

    for (jsize i = 0; i < lutCount; i++) {
        auto rgba = (jbyteArray) env->GetObjectArrayElement(lutRgba, i);
        jsize rgbaLength = javaArrayLength(env, rgba);
        int width = lutWidthValues[i];
        int height = lutHeightValues[i];
        if (width <= 0 || height <= 0 || (int64_t) rgbaLength != (int64_t) width * height * 4) {
            LOGE("setShaderChain: lut %s has %d bytes for %dx%d", lutIdValues[i].c_str(), rgbaLength, width, height);
            if (rgba != nullptr) env->DeleteLocalRef(rgba);
            return false;
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
        lut.mipmap = lutMipmap != nullptr && lutMipmapValues[i] == JNI_TRUE;
        chain.luts.push_back(std::move(lut));
    }

    auto paramIdValues = javaStrings(env, paramIds);
    auto paramFloatValues = javaFloats(env, paramValues);
    for (size_t i = 0; i < paramIdValues.size(); i++) {
        chain.params[paramIdValues[i]] = paramFloatValues[i];
    }
    return true;
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
        jsize passCount = javaArrayLength(env, sources);
        for (jarray array : std::initializer_list<jarray> {
            filterLinear, wrap, scaleTypeX, scaleTypeY, scaleX, scaleY, frameCountMod, alias
        }) {
            if (javaArrayLength(env, array) != passCount) {
                LOGE("setShaderChain: pass arrays differ in length");
                return;
            }
        }
        auto sourceValues = javaStrings(env, sources);
        auto filterValues = javaInts(env, filterLinear);
        auto wrapValues = javaInts(env, wrap);
        auto scaleTypeXValues = javaInts(env, scaleTypeX);
        auto scaleTypeYValues = javaInts(env, scaleTypeY);
        auto scaleXValues = javaFloats(env, scaleX);
        auto scaleYValues = javaFloats(env, scaleY);
        auto frameCountModValues = javaInts(env, frameCountMod);
        auto aliasValues = javaStrings(env, alias);

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

        if (!readChainLutsAndParams(env, chain, lutIds, lutWidth, lutHeight, lutRgba, lutLinear, lutWrap, nullptr, paramIds, paramValues)) return;

        LibretroDroid::getInstance().setPresetChain(std::move(chain));
    } catch (std::exception &exception) {
        LOGE("Error in setShaderChain: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

// A slang chain: each pass arrives translated (translateSlang), as the bytes that call returned.
JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_setSlangChain(
    JNIEnv* env,
    jclass obj,
    jobjectArray programs,
    jintArray filterLinear,
    jintArray wrap,
    jintArray scaleTypeX,
    jintArray scaleTypeY,
    jfloatArray scaleX,
    jfloatArray scaleY,
    jintArray frameCountMod,
    jobjectArray alias,
    jobjectArray format,
    jbooleanArray mipmapInput,
    jobjectArray lutIds,
    jintArray lutWidth,
    jintArray lutHeight,
    jobjectArray lutRgba,
    jbooleanArray lutLinear,
    jintArray lutWrap,
    jbooleanArray lutMipmap,
    jobjectArray paramIds,
    jfloatArray paramValues
) {
    try {
        jsize passCount = javaArrayLength(env, programs);
        for (jarray array : std::initializer_list<jarray> {
            filterLinear, wrap, scaleTypeX, scaleTypeY, scaleX, scaleY, frameCountMod, alias, format, mipmapInput
        }) {
            if (javaArrayLength(env, array) != passCount) {
                LOGE("setSlangChain: pass arrays differ in length");
                return;
            }
        }
        if (passCount == 0) {
            LOGE("setSlangChain: no passes");
            return;
        }

        auto filterValues = javaInts(env, filterLinear);
        auto wrapValues = javaInts(env, wrap);
        auto scaleTypeXValues = javaInts(env, scaleTypeX);
        auto scaleTypeYValues = javaInts(env, scaleTypeY);
        auto scaleXValues = javaFloats(env, scaleX);
        auto scaleYValues = javaFloats(env, scaleY);
        auto frameCountModValues = javaInts(env, frameCountMod);
        auto aliasValues = javaStrings(env, alias);
        auto formatValues = javaStrings(env, format);
        auto mipmapValues = javaBooleans(env, mipmapInput);

        PresetChain chain;
        for (jsize i = 0; i < passCount; i++) {
            auto bytes = (jbyteArray) env->GetObjectArrayElement(programs, i);
            jsize length = javaArrayLength(env, bytes);
            std::string encoded((size_t) length, '\0');
            if (length > 0) env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte*>(encoded.data()));
            if (bytes != nullptr) env->DeleteLocalRef(bytes);

            SlangPass pass;
            if (!decodeSlangProgram(encoded, pass.program)) {
                LOGE("setSlangChain: pass %d is not a translated program", (int) i);
                return;
            }
            pass.filterLinear = filterValues[i] < 0 ? std::nullopt : std::optional<bool>(filterValues[i] != 0);
            pass.wrap = wrapValues[i];
            pass.scaleTypeX = scaleTypeXValues[i];
            pass.scaleTypeY = scaleTypeYValues[i];
            pass.scaleX = scaleXValues[i];
            pass.scaleY = scaleYValues[i];
            pass.frameCountMod = (unsigned) std::max(0, (int) frameCountModValues[i]);
            pass.alias = aliasValues[i];
            pass.format = formatValues[i];
            pass.mipmapInput = mipmapValues[i] == JNI_TRUE;
            chain.slangPasses.push_back(std::move(pass));
        }

        if (!readChainLutsAndParams(env, chain, lutIds, lutWidth, lutHeight, lutRgba, lutLinear, lutWrap, lutMipmap, paramIds, paramValues)) return;

        LibretroDroid::getInstance().setPresetChain(std::move(chain));
    } catch (std::exception &exception) {
        LOGE("Error in setSlangChain: %s", exception.what());
        JavaUtils::throwRetroException(env, ERROR_GENERIC);
    }
}

// One slang pass (two "#version 450" stages) to GLSL ES esVersion (300, 310, 320), as bytes for setSlangChain and for a
// disk cache. No GL: any thread. Null on failure, with the reason in error[0].
JNIEXPORT jbyteArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_translateSlang(
    JNIEnv* env,
    jclass obj,
    jstring vertex,
    jstring fragment,
    jint esVersion,
    jobjectArray error
) {
    if (vertex == nullptr || fragment == nullptr) return nullptr;
    std::string reason;
    try {
        JniString vertexString(env, vertex);
        JniString fragmentString(env, fragment);
        SlangProgram program;
        if (translateSlang(vertexString.stdString(), fragmentString.stdString(), (unsigned) esVersion, program, reason)) {
            std::string encoded = encodeSlangProgram(program);
            jbyteArray result = env->NewByteArray((jsize) encoded.size());
            if (result != nullptr) {
                env->SetByteArrayRegion(result, 0, (jsize) encoded.size(), reinterpret_cast<const jbyte*>(encoded.data()));
            }
            return result;
        }
    } catch (std::exception &exception) {
        reason = exception.what();
    }
    if (error != nullptr && javaArrayLength(env, error) > 0) {
        jstring text = newSafeJString(env, reason);   // may quote the shader's own lines
        env->SetObjectArrayElement(error, 0, text);
        if (text != nullptr) env->DeleteLocalRef(text);
    }
    return nullptr;
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
