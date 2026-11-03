// nx-mobile 2026-11-03: the process's threads (/proc/self/task), so the performance hint session can name the threads a
// core started (Dolphin's CPU thread, PPSSPP's and ARMSX2's workers) without the core telling us.
#ifndef LIBRETRODROID_THREADLIST_H
#define LIBRETRODROID_THREADLIST_H

#include <vector>

namespace libretrodroid {

class ThreadList {
public:
    // The thread ids in a task folder, sorted; empty when it cannot be read.
    static std::vector<int> list(const char* taskDir = "/proc/self/task");
    // The ids in after that were not in before (both sorted).
    static std::vector<int> added(const std::vector<int>& before, const std::vector<int>& after);
    // The emulation threads among ids, by their names (taskDir/<id>/comm): audio, Java runtime, binder and networking
    // threads are left out, as is a thread that has exited. A performance hint session should cover the game's work only.
    static std::vector<int> emulation(const std::vector<int>& ids, const char* taskDir = "/proc/self/task");
};

}

#endif
