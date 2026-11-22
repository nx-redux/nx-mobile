// Host check for Utf8 (outside text on its way to JNI: newSafeJString). From the repo root:
//   out=$(mktemp -d)/utf8_test && clang++ -std=c++17 -Ilibretrodroid/src/main/cpp \
//     libretrodroid/src/test/cpp/utf8_test.cpp libretrodroid/src/main/cpp/utils/utf8.cpp -o "$out" && "$out"
#include "utils/utf8.h"

#include <cstdio>
#include <cstring>
#include <string>

#include "check.h"

using libretrodroid::Utf8::isJniSafe;
using libretrodroid::Utf8::toUtf16;

static bool safe(const char* s) { return isJniSafe(s, std::strlen(s)); }
static std::u16string wide(const char* s) { return toUtf16(s, std::strlen(s)); }

int main() {
    // NewStringUTF straight: ASCII, 2- and 3-byte sequences.
    check(safe(""), "empty is safe");
    check(safe("Super Mario"), "ascii is safe");
    check(safe("Pok\xC3\xA9mon \xE3\x83\x9D"), "2- and 3-byte sequences are safe");
    // NewString instead: a Latin-1 byte, a torn tail, an emoji (4 bytes), an overlong lead.
    check(!safe("Pok\xE9mon"), "a Latin-1 byte is not safe");
    check(!safe("abc\xE3\x83"), "a torn tail is not safe");
    check(!safe("Star \xF0\x9F\x8C\x9F"), "an emoji is not safe");
    check(!safe("\xC0\x80"), "an overlong lead is not safe");

    check(wide("Pok\xC3\xA9mon") == u"Pokémon", "2-byte decodes");
    check(wide("\xE3\x83\x9D") == u"ポ", "3-byte decodes");
    // U+1F31F: the surrogate pair, not '?'.
    std::u16string star = wide("Star \xF0\x9F\x8C\x9F!");
    check(star == std::u16string(u"Star ") + char16_t(0xD83C) + char16_t(0xDF1F) + u"!", "an emoji becomes its surrogate pair");
    // Each byte that starts no sequence is one '?'; the rest of the text survives.
    check(wide("Pok\xE9mon") == u"Pok?mon", "a Latin-1 byte becomes ?");
    check(wide("abc\xE3\x83") == u"abc??", "a torn tail becomes ? per byte");
    check(wide("\x80x") == u"?x", "a stray continuation becomes ?");
    check(wide("\xF4\x90\x80\x80") == u"?", "past U+10FFFF becomes one ?");

    if (failures == 0) std::printf("utf8_test: OK\n");
    return failures == 0 ? 0 : 1;
}
