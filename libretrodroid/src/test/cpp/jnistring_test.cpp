// Host check for JniString against a fake JNIEnv (any JDK's jni.h). From the repo root:
//   jdk=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home out=$(mktemp -d)/jnistring_test && clang++ -std=c++17 -Ilibretrodroid/src/main/cpp \
//     -I"$jdk/include" -I"$jdk/include/darwin" libretrodroid/src/test/cpp/jnistring_test.cpp \
//     libretrodroid/src/main/cpp/utils/jnistring.cpp libretrodroid/src/main/cpp/utils/utf8.cpp -o "$out" && "$out"
#include "utils/jnistring.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <type_traits>

static int failures = 0;
static void check(bool ok, const char* what) { if (!ok) { std::printf("FAIL %s\n", what); failures++; } }

// The JDK and the NDK name the function table differently; the field's type works for both.
using FunctionTable = std::remove_const_t<std::remove_pointer_t<decltype(JNIEnv::functions)>>;

static int getCalls = 0;
static int releaseCalls = 0;
static int nullCalls = 0;

static const char* JNICALL fakeGet(JNIEnv*, jstring s, jboolean*) {
    getCalls++;
    if (s == nullptr) { nullCalls++; return nullptr; }
    return reinterpret_cast<const char*>(s);
}

static void JNICALL fakeRelease(JNIEnv*, jstring s, const char*) {
    releaseCalls++;
    if (s == nullptr) nullCalls++;
}

int main() {
    FunctionTable table {};
    table.GetStringUTFChars = fakeGet;
    table.ReleaseStringUTFChars = fakeRelease;
    JNIEnv env {};
    env.functions = &table;

    // A jstring here is the address of its text: the fake hands it straight back.
    static const char text[] = "core_option";
    {
        JniString s(&env, reinterpret_cast<jstring>(const_cast<char*>(text)));
        check(s.stdString() == "core_option", "a string reads back");
        const char* copy = s.cString();
        check(copy != nullptr && std::strcmp(copy, "core_option") == 0, "cString copies it");
        std::free(const_cast<char*>(copy));
    }
    check(getCalls == 1 && releaseCalls == 1, "a string is got and released once");

    getCalls = releaseCalls = 0;
    {
        JniString s(&env, nullptr);
        check(s.stdString().empty(), "null reads as empty");
        const char* copy = s.cString();
        check(copy != nullptr && copy[0] == '\0', "null's cString is an empty copy");
        std::free(const_cast<char*>(copy));
    }
    check(nullCalls == 0, "null is never handed to JNI");
    check(getCalls == 0 && releaseCalls == 0, "null is neither got nor released");

    if (failures == 0) std::printf("jnistring_test: all passed\n");
    return failures == 0 ? 0 : 1;
}
