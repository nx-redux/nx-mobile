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

#include "gldebug.h"

#include <atomic>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <chrono>
#include <cstdlib>
#include <libretro.h>
#include <algorithm>
#include <cstdio>
#include <android/log.h>
#include <cstring>

#define NXGL_LOG(prio, ...) __android_log_print(prio, "NXGL", __VA_ARGS__)

namespace libretrodroid::gldebug {

namespace {

// GL_KHR_debug tokens (GLES 3.2 core names without the suffix); spelled out, as gl3.h has none of them.
constexpr GLenum DEBUG_OUTPUT = 0x92E0;
constexpr GLenum DEBUG_OUTPUT_SYNCHRONOUS = 0x8242;
constexpr GLenum DEBUG_SEVERITY_HIGH = 0x9146;
constexpr GLenum DEBUG_SEVERITY_MEDIUM = 0x9147;
constexpr GLenum DEBUG_SEVERITY_LOW = 0x9148;
constexpr GLenum DEBUG_SEVERITY_NOTIFICATION = 0x826B;
constexpr GLenum DEBUG_TYPE_PERFORMANCE = 0x8250;

// Enough to see what a broken frame does without flooding logcat at 60 fps.
constexpr int MAX_MESSAGES = 400;
// Performance notes get their own small cap: Adreno emits enough of them to fill MAX_MESSAGES and mute real errors.
constexpr int MAX_PERFORMANCE_MESSAGES = 20;

typedef void (GL_APIENTRYP DebugProc)(GLenum, GLenum, GLuint, GLenum, GLsizei, const GLchar*, const void*);
typedef void (GL_APIENTRYP DebugMessageCallbackProc)(DebugProc, const void*);

std::atomic<bool> enabled { false };
std::atomic<bool> callbackInstalled { false };
std::atomic<int> logged { 0 };
std::atomic<int> loggedPerformance { 0 };

bool takeSlot() {
    int n = logged.fetch_add(1);
    if (n == MAX_MESSAGES) NXGL_LOG(ANDROID_LOG_WARN, "message cap (%d) reached; muting", MAX_MESSAGES);
    return n < MAX_MESSAGES;
}

bool takePerformanceSlot() {
    int n = loggedPerformance.fetch_add(1);
    if (n == MAX_PERFORMANCE_MESSAGES) {
        NXGL_LOG(ANDROID_LOG_WARN, "performance cap (%d) reached; muting", MAX_PERFORMANCE_MESSAGES);
    }
    return n < MAX_PERFORMANCE_MESSAGES;
}

const char* severityName(GLenum severity) {
    switch (severity) {
        case DEBUG_SEVERITY_HIGH: return "HIGH";
        case DEBUG_SEVERITY_MEDIUM: return "MEDIUM";
        case DEBUG_SEVERITY_LOW: return "LOW";
        case DEBUG_SEVERITY_NOTIFICATION: return "NOTE";
        default: return "?";
    }
}

void GL_APIENTRY onMessage(
    GLenum source, GLenum type, GLuint id, GLenum severity, GLsizei, const GLchar* message, const void*
) {
    if (severity == DEBUG_SEVERITY_NOTIFICATION) return;
    if (!(type == DEBUG_TYPE_PERFORMANCE ? takePerformanceSlot() : takeSlot())) return;
    int prio = severity == DEBUG_SEVERITY_HIGH ? ANDROID_LOG_ERROR : ANDROID_LOG_WARN;
    NXGL_LOG(prio, "[%s] source=0x%x type=0x%x id=%u: %s", severityName(severity), source, type, id, message);
}

}

void setEnabled(bool on) {
    enabled = on;
}

void install() {
    callbackInstalled = false;
    if (!enabled) return;
    logged = 0;
    loggedPerformance = 0;

    auto callback = (DebugMessageCallbackProc) eglGetProcAddress("glDebugMessageCallbackKHR");
    if (!callback) callback = (DebugMessageCallbackProc) eglGetProcAddress("glDebugMessageCallback");

    const char* extensions = (const char*) glGetString(GL_EXTENSIONS);
    bool hasKhrDebug = extensions && strstr(extensions, "GL_KHR_debug");
    if (!callback || !hasKhrDebug) {
        NXGL_LOG(ANDROID_LOG_INFO, "GL_KHR_debug unavailable (ext=%d proc=%d); polling glGetError after retro_run",
                 hasKhrDebug, callback != nullptr);
        return;
    }

    glEnable(DEBUG_OUTPUT);
    glEnable(DEBUG_OUTPUT_SYNCHRONOUS);
    callback(onMessage, nullptr);
    callbackInstalled = true;
    NXGL_LOG(ANDROID_LOG_INFO, "GL_KHR_debug callback installed");
}

namespace {
std::chrono::steady_clock::time_point windowStart = std::chrono::steady_clock::now();
int hwFrames = 0, dupeFrames = 0, swFrames = 0;
std::atomic<long> audioFrames { 0 };
std::atomic<int> audioPeak { 0 };
}

void onAudio(const int16_t* data, size_t frames) {
    if (!enabled || !data) return;
    int peak = audioPeak;
    for (size_t i = 0; i < frames * 2; i++) peak = std::max(peak, std::abs((int) data[i]));
    audioPeak = peak;
    audioFrames += (long) frames;
}

void onVideoRefresh(const void* data, unsigned width, unsigned height, unsigned framebuffer) {
    if (!enabled) return;
    if (data == nullptr) dupeFrames++;
    else if (data == RETRO_HW_FRAME_BUFFER_VALID) hwFrames++;
    else swFrames++;

    auto now = std::chrono::steady_clock::now();
    if (now - windowStart < std::chrono::seconds(5)) return;
    windowStart = now;

    char pixel[48] = "-";
    if (data == RETRO_HW_FRAME_BUFFER_VALID && width > 0 && height > 0) {
        GLint previous = 0;
        glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &previous);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
        GLubyte centre[4] = {0, 0, 0, 0}, quarter[4] = {0, 0, 0, 0};
        glReadPixels((GLint) width / 2, (GLint) height / 2, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, centre);
        glReadPixels((GLint) width / 4, (GLint) height / 4, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, quarter);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, (GLuint) previous);
        snprintf(pixel, sizeof(pixel), "%02x%02x%02x/%02x%02x%02x",
                 centre[0], centre[1], centre[2], quarter[0], quarter[1], quarter[2]);
    }
    NXGL_LOG(ANDROID_LOG_INFO, "5s: hw=%d dupe=%d sw=%d size=%ux%u fbo=%u pixel(centre/quarter)=%s audio=%ld peak=%d",
             hwFrames, dupeFrames, swFrames, width, height, framebuffer, pixel, audioFrames.load(), audioPeak.load());
    if (data == RETRO_HW_FRAME_BUFFER_VALID) {
        // The GL state the core hands back: the frontend's presentation pass draws on top of it.
        GLint vao = 0, arrayBuffer = 0, program = 0, drawFbo = 0, unpack = 0;
        GLboolean mask[4] = {1, 1, 1, 1};
        glGetIntegerv(GL_VERTEX_ARRAY_BINDING, &vao);
        glGetIntegerv(GL_ARRAY_BUFFER_BINDING, &arrayBuffer);
        glGetIntegerv(GL_CURRENT_PROGRAM, &program);
        glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &drawFbo);
        glGetIntegerv(GL_PIXEL_UNPACK_BUFFER_BINDING, &unpack);
        glGetBooleanv(GL_COLOR_WRITEMASK, mask);
        NXGL_LOG(ANDROID_LOG_INFO, "core GL state: vao=%d arrayBuffer=%d unpack=%d program=%d drawFbo=%d scissor=%d stencil=%d "
                 "blend=%d cull=%d depth=%d colorMask=%d%d%d%d",
                 vao, arrayBuffer, unpack, program, drawFbo, glIsEnabled(GL_SCISSOR_TEST), glIsEnabled(GL_STENCIL_TEST),
                 glIsEnabled(GL_BLEND), glIsEnabled(GL_CULL_FACE), glIsEnabled(GL_DEPTH_TEST),
                 mask[0], mask[1], mask[2], mask[3]);
    }
    hwFrames = dupeFrames = swFrames = 0;
    audioFrames = 0;
    audioPeak = 0;
}

void pollErrors() {
    if (!enabled || callbackInstalled) return;
    for (int i = 0; i < 8; i++) {
        GLenum error = glGetError();
        if (error == GL_NO_ERROR) return;
        if (takeSlot()) NXGL_LOG(ANDROID_LOG_ERROR, "glGetError after retro_run: 0x%x", error);
    }
}

}
