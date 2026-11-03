#include "perfhint.h"

#include <cstring>
#include <dlfcn.h>
#include <sys/system_properties.h>

#include "log.h"

namespace libretrodroid {

namespace {

// The NDK's APerformanceHint calls (android/performance_hint.h, API 33), as opaque pointers.
using GetManager = void* (*)();
using CreateSession = void* (*)(void* manager, const int32_t* threadIds, size_t size, int64_t initialTargetWorkDurationNanos);
using UpdateTarget = int (*)(void* session, int64_t targetDurationNanos);
using ReportActual = int (*)(void* session, int64_t actualDurationNanos);
using CloseSession = void (*)(void* session);

struct Api {
    GetManager getManager = nullptr;
    CreateSession createSession = nullptr;
    UpdateTarget updateTarget = nullptr;
    ReportActual reportActual = nullptr;
    CloseSession closeSession = nullptr;

    bool loaded() const { return getManager && createSession && updateTarget && reportActual && closeSession; }
};

const Api& api() {
    static const Api loaded = [] {
        Api a;
        void* lib = dlopen("libandroid.so", RTLD_NOW | RTLD_NOLOAD);
        if (lib == nullptr) lib = dlopen("libandroid.so", RTLD_NOW);
        if (lib == nullptr) return a;
        a.getManager = reinterpret_cast<GetManager>(dlsym(lib, "APerformanceHint_getManager"));
        a.createSession = reinterpret_cast<CreateSession>(dlsym(lib, "APerformanceHint_createSession"));
        a.updateTarget = reinterpret_cast<UpdateTarget>(dlsym(lib, "APerformanceHint_updateTargetWorkDuration"));
        a.reportActual = reinterpret_cast<ReportActual>(dlsym(lib, "APerformanceHint_reportActualWorkDuration"));
        a.closeSession = reinterpret_cast<CloseSession>(dlsym(lib, "APerformanceHint_closeSession"));
        return a;
    }();
    return loaded;
}

bool disabledByProperty() {
    char value[PROP_VALUE_MAX] = {};
    return __system_property_get("debug.nx.adpf", value) > 0 && std::strcmp(value, "0") == 0;
}

}

bool PerfHint::open(const std::vector<int>& threads, int64_t targetNanos) {
    close();
    if (threads.empty() || targetNanos <= 0) return false;
    if (disabledByProperty()) {
        LOGI("ADPF: off (debug.nx.adpf=0)");
        return false;
    }
    const Api& a = api();
    if (!a.loaded()) {
        LOGI("ADPF: not available on this Android");
        return false;
    }
    void* manager = a.getManager();
    if (manager == nullptr) {
        LOGI("ADPF: no performance hint manager");
        return false;
    }
    std::vector<int32_t> ids(threads.begin(), threads.end());
    session = a.createSession(manager, ids.data(), ids.size(), targetNanos);
    target = targetNanos;
    LOGI("ADPF: session %s for %zu threads, target %.2f ms", session ? "open" : "refused", ids.size(), targetNanos / 1e6);
    return session != nullptr;
}

void PerfHint::report(int64_t actualNanos) {
    if (session == nullptr || actualNanos <= 0) return;
    api().reportActual(session, actualNanos);
}

void PerfHint::updateTarget(int64_t targetNanos) {
    if (session == nullptr || targetNanos <= 0 || targetNanos == target) return;
    target = targetNanos;
    api().updateTarget(session, targetNanos);
}

void PerfHint::close() {
    if (session == nullptr) return;
    api().closeSession(session);
    session = nullptr;
}

}
