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

// No prototypes: every Vulkan call in this file goes through a pointer it resolved.
#define VK_NO_PROTOTYPES 1
#include "vulkandispatch.h"

#include <android/api-level.h>
#include <dlfcn.h>
#include <sys/stat.h>

#include <cerrno>
#include <cstring>

#ifdef LIBRETRODROID_ADRENOTOOLS
#include <adrenotools/driver.h>
#endif

#include "log.h"

namespace libretrodroid {

namespace {

// libadrenotools joins its directory arguments to file names with no separator.
std::string withSlash(const std::string& dir) {
    return (!dir.empty() && dir.back() != '/') ? dir + "/" : dir;
}

}

bool VulkanDispatch::bindGlobal() {
    if (getInstanceProcAddr == nullptr) return false;
#define LIBRETRODROID_VK_BIND(member, name) \
    member = reinterpret_cast<PFN_##name>(getInstanceProcAddr(VK_NULL_HANDLE, #name));
    LIBRETRODROID_VK_GLOBAL(LIBRETRODROID_VK_BIND)
    LIBRETRODROID_VK_GLOBAL_OPTIONAL(LIBRETRODROID_VK_BIND)
#undef LIBRETRODROID_VK_BIND
#define LIBRETRODROID_VK_CHECK(member, name) \
    if (member == nullptr) { LOGE("Vulkan bridge: loader lacks %s", #name); return false; }
    LIBRETRODROID_VK_GLOBAL(LIBRETRODROID_VK_CHECK)
#undef LIBRETRODROID_VK_CHECK
    return true;
}

bool VulkanDispatch::loadSystem(VulkanDispatch& out) {
    out.close();
    out.handle = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (out.handle == nullptr) {
        const char* err = dlerror();
        LOGE("Vulkan bridge: dlopen(libvulkan.so) failed: %s", err != nullptr ? err : "(no reason)");
        return false;
    }
    out.getInstanceProcAddr = reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(out.handle, "vkGetInstanceProcAddr"));
    if (out.getInstanceProcAddr == nullptr) LOGE("Vulkan bridge: libvulkan.so has no vkGetInstanceProcAddr");
    if (!out.bindGlobal()) {
        out.close();
        return false;
    }
    out.custom = false;
    return true;
}

bool VulkanDispatch::loadCustom(VulkanDispatch& out, const std::string& driverDir, const std::string& library,
                                const std::string& hookLibDir, const std::string& tmpDir) {
    out.close();
    if (driverDir.empty() || library.empty() || hookLibDir.empty()) {
        LOGE("Vulkan bridge: custom driver needs a directory, a library and the hook directory (dir '%s', library '%s', "
             "hooks '%s')", driverDir.c_str(), library.c_str(), hookLibDir.c_str());
        return false;
    }
#ifndef LIBRETRODROID_ADRENOTOOLS
    LOGE("Vulkan bridge: built without libadrenotools (arm64-v8a only): no custom driver");
    (void) tmpDir;
    return false;
#else
    const std::string dir = withSlash(driverDir);
    const std::string hooks = withSlash(hookLibDir);
    // libadrenotools returns null for this and many other reasons without saying which: check the file first.
    const std::string path = dir + library;
    struct stat st {};
    if (stat(path.c_str(), &st) != 0) {
        LOGE("Vulkan bridge: custom driver %s: %s", path.c_str(), strerror(errno));
        return false;
    }
    // Patched libraries go to a file below API 29; from 29 libadrenotools uses memfd and ignores the directory.
    std::string tmp;
    const bool useTmp = android_get_device_api_level() < 29;
    if (useTmp) {
        tmp = withSlash(tmpDir);
        if (tmp.empty()) {
            LOGE("Vulkan bridge: custom driver needs a temporary directory below API 29");
            return false;
        }
        if (mkdir(tmp.c_str(), 0700) != 0 && errno != EEXIST) {
            LOGE("Vulkan bridge: mkdir %s: %s", tmp.c_str(), strerror(errno));
            return false;
        }
    }

    LOGI("Vulkan bridge: adrenotools_open_libvulkan driver %s%s, hooks %s, tmp %s", dir.c_str(), library.c_str(),
         hooks.c_str(), useTmp ? tmp.c_str() : "(memfd)");
    dlerror();
    errno = 0;
    out.handle = adrenotools_open_libvulkan(RTLD_NOW | RTLD_LOCAL, ADRENOTOOLS_DRIVER_CUSTOM,
                                            useTmp ? tmp.c_str() : nullptr, hooks.c_str(), dir.c_str(),
                                            library.c_str(), nullptr, nullptr);
    if (out.handle == nullptr) {
        // Null below API 28 (linkernsbypass cannot run), or a namespace / hook / driver load failure.
        const char* err = dlerror();
        LOGE("Vulkan bridge: adrenotools_open_libvulkan returned null (API %d): dlerror %s, errno %d (%s)",
             android_get_device_api_level(), err != nullptr ? err : "(none)", errno, strerror(errno));
        return false;
    }
    out.getInstanceProcAddr = reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(out.handle, "vkGetInstanceProcAddr"));
    if (out.getInstanceProcAddr == nullptr) {
        const char* err = dlerror();
        LOGE("Vulkan bridge: custom loader has no vkGetInstanceProcAddr: %s", err != nullptr ? err : "(none)");
    }
    if (!out.bindGlobal()) {
        out.close();
        return false;
    }
    out.custom = true;
    return true;
#endif
}

void VulkanDispatch::bindInstance(VkInstance instance) {
    if (getInstanceProcAddr == nullptr) return;
#define LIBRETRODROID_VK_BIND(member, name) \
    member = reinterpret_cast<PFN_##name>(getInstanceProcAddr(instance, #name));
    LIBRETRODROID_VK_INSTANCE(LIBRETRODROID_VK_BIND)
#undef LIBRETRODROID_VK_BIND
#define LIBRETRODROID_VK_BIND_OPTIONAL(member, name) \
    member = reinterpret_cast<PFN_##name>(getInstanceProcAddr(instance, #name)); \
    if (member == nullptr) member = reinterpret_cast<PFN_##name>(getInstanceProcAddr(instance, #name "KHR"));
    LIBRETRODROID_VK_INSTANCE_OPTIONAL(LIBRETRODROID_VK_BIND_OPTIONAL)
#undef LIBRETRODROID_VK_BIND_OPTIONAL
}

void VulkanDispatch::bindDevice(VkDevice device) {
    if (getDeviceProcAddr == nullptr) return;
#define LIBRETRODROID_VK_BIND(member, name) \
    member = reinterpret_cast<PFN_##name>(getDeviceProcAddr(device, #name));
    LIBRETRODROID_VK_DEVICE(LIBRETRODROID_VK_BIND)
    LIBRETRODROID_VK_DEVICE_OPTIONAL(LIBRETRODROID_VK_BIND)
#undef LIBRETRODROID_VK_BIND
}

const char* VulkanDispatch::missingInstanceFunction() const {
#define LIBRETRODROID_VK_MISSING(member, name) if (member == nullptr) return #name;
    LIBRETRODROID_VK_INSTANCE(LIBRETRODROID_VK_MISSING)
#undef LIBRETRODROID_VK_MISSING
    return nullptr;
}

const char* VulkanDispatch::missingDeviceFunction() const {
#define LIBRETRODROID_VK_MISSING(member, name) if (member == nullptr) return #name;
    LIBRETRODROID_VK_DEVICE(LIBRETRODROID_VK_MISSING)
#undef LIBRETRODROID_VK_MISSING
    return nullptr;
}

void VulkanDispatch::close() {
    if (handle != nullptr) dlclose(handle);
    *this = VulkanDispatch {};
}

}
