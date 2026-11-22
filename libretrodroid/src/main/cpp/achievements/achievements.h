#ifndef LIBRETRODROID_ACHIEVEMENTS_H
#define LIBRETRODROID_ACHIEVEMENTS_H

#include <cstdint>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#include "libretro.h"
#include "rc_client.h"
#include "rc_libretro.h"
#include "memoryregiontable.h"

namespace libretrodroid {

// Event type values: rcheevos' own RC_CLIENT_EVENT_* for runtime events, 100+ for lifecycle results.
enum AchievementEventType : int {
    RA_EVENT_LOGIN_OK = 100,
    RA_EVENT_LOGIN_FAILED = 101,
    RA_EVENT_GAME_LOADED = 102,
    RA_EVENT_GAME_UNKNOWN = 103,
    RA_EVENT_GAME_LOAD_FAILED = 104,
};

struct AchievementEvent {
    int type = 0;
    uint32_t id = 0;
    std::string title;
    std::string description;
    std::string badge;
    uint32_t points = 0;
    std::string progress;
    float percent = 0.f;
    std::string extra;
};

struct AchievementServerCall {
    uint32_t requestId = 0;
    std::string url;
    std::string postData;
    std::string contentType;
};

struct AchievementInfo {
    uint32_t id; std::string title; std::string description; uint32_t points; std::string badge;
    int state; int64_t unlockTime; std::string progress; float percent; float rarity; int type; int category;
};

/**
 * Owns the rc_client. HTTP is delegated to the host: server_call queues an AchievementServerCall that the
 * JNI step flushes to Kotlin; Kotlin answers through serverResponse() from any thread and the answer is
 * applied on the emulation thread in onFrame(). Events are queued the same way. Hardcore is never enabled.
 */
class Achievements {
public:
    static Achievements& getInstance();

    void enable();
    void disable();
    bool isEnabled() const { return client != nullptr; }
    /**
     * Back to a clean slate for a new native session: unloads the game, destroys the client and forgets the core's
     * memory accessors, memory map and region table. Call it before the previous core is unloaded; this singleton
     * outlives LibretroDroid sessions. With keepEnabled a client that was enabled is replaced by a fresh one (no user,
     * no game): the host enables achievements before the view, and so the native session, exists.
     */
    void resetSession(bool keepEnabled);
    bool memoryRegionsReady() const { return regions.ready(); }   // public for the host tests

    /** Either change invalidates the region table; readMemory rebuilds it lazily from the current inputs. */
    void setMemoryMap(const struct retro_memory_map* map);
    /** The core's memory map (its own copy), or nullptr; [memoryMapVersion] changes with every setMemoryMap. */
    const struct retro_memory_map* currentMemoryMap() const { return haveMemoryMap ? &memoryMap : nullptr; }
    unsigned memoryMapVersion() const { return mapVersion; }
    void setCoreMemoryAccessors(size_t (*getSize)(unsigned), void* (*getData)(unsigned));

    void login(const std::string& user, const std::string& token);
    void loadGame(const std::string& hash, uint32_t consoleId);
    void unloadGame();

    void serverResponse(uint32_t requestId, int httpStatus, const std::string& body);
    /** Emulation thread only: applies queued responses, then do_frame (or idle when paused). */
    void onFrame(bool paused);

    std::vector<AchievementEvent> takeEvents();
    std::vector<AchievementServerCall> takeServerCalls();

    std::vector<AchievementInfo> list();
    /** Builds the signed award request; hardcore 0. Returns false when rcheevos rejects the parameters. */
    static bool buildAwardRequest(const std::string& user, const std::string& token, uint32_t achievementId,
                                  const std::string& hash, uint32_t secondsSinceUnlock, std::string& url, std::string& post);

    rc_client_t* rawClient() { return client; }

private:
    Achievements() = default;
    struct PendingCallback { rc_client_server_callback_t callback; void* callbackData; };
    struct QueuedResponse { uint32_t requestId; int status; std::string body; };

    static uint32_t readMemory(uint32_t address, uint8_t* buffer, uint32_t numBytes, rc_client_t* client);
    static void serverCall(const rc_api_request_t* request, rc_client_server_callback_t callback, void* callbackData, rc_client_t* client);
    static void eventHandler(const rc_client_event_t* event, rc_client_t* client);
    static void logMessage(const char* message, const rc_client_t* client);
    static void loginCallback(int result, const char* errorMessage, rc_client_t* client, void* userdata);
    static void loadGameCallback(int result, const char* errorMessage, rc_client_t* client, void* userdata);
    static void coreMemoryInfo(uint32_t id, rc_libretro_core_memory_info_t* info);

    void initMemoryRegions();
    void invalidateMemoryRegions();
    void pushEvent(AchievementEvent e);
    void failPendingCallbacks();

    rc_client_t* client = nullptr;

    std::vector<struct retro_memory_descriptor> descriptors;
    std::vector<std::string> descriptorStrings;
    struct retro_memory_map memoryMap {};
    bool haveMemoryMap = false;
    unsigned mapVersion = 0;
    MemoryRegionTable regions;
    bool regionsInitTried = false;   // readMemory's lazy init runs at most once per loadGame / invalidation
    uint32_t consoleId = 0;
    size_t (*coreGetMemorySize)(unsigned) = nullptr;
    void* (*coreGetMemoryData)(unsigned) = nullptr;

    std::mutex queueLock;   // guards pending, responses, outgoingCalls, events
    uint32_t nextRequestId = 1;
    std::unordered_map<uint32_t, PendingCallback> pending;
    std::vector<QueuedResponse> responses;
    std::vector<AchievementServerCall> outgoingCalls;
    std::vector<AchievementEvent> events;
};

}

#endif //LIBRETRODROID_ACHIEVEMENTS_H
