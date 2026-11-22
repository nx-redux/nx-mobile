#include "achievements.h"
#include "achievementshash.h"

#include <algorithm>
#include <cstring>
#include <functional>
#include "../log.h"
#include "rc_api_runtime.h"
#include "rc_version.h"

namespace libretrodroid {

Achievements& Achievements::getInstance() {
    static Achievements instance;
    return instance;
}

void Achievements::enable() {
    if (client) return;
    client = rc_client_create(readMemory, serverCall);
    rc_client_set_userdata(client, this);
    rc_client_enable_logging(client, RC_CLIENT_LOG_LEVEL_INFO, logMessage);
    rc_client_set_event_handler(client, eventHandler);
    rc_client_set_hardcore_enabled(client, 0);   // softcore only: this frontend is not RA-approved for hardcore
    rc_client_set_hash_callbacks(client, AchievementsHash::callbacks());
    LOGI("achievements enabled (rcheevos %s)", RCHEEVOS_VERSION_STRING);
}

void Achievements::disable() {
    if (!client) return;
    rc_client_destroy(client);
    client = nullptr;
    regions.invalidate();
    failPendingCallbacks();
    std::lock_guard<std::mutex> lock(queueLock);
    responses.clear();
    outgoingCalls.clear();
    events.clear();
}

void Achievements::resetSession(bool keepEnabled) {
    bool wasEnabled = isEnabled();
    unloadGame();
    disable();
    setCoreMemoryAccessors(nullptr, nullptr);
    setMemoryMap(nullptr);
    regionsInitTried = false;
    consoleId = 0;
    if (keepEnabled && wasEnabled) enable();
}

void Achievements::failPendingCallbacks() {
    // rc_client_destroy already released the async handles; the stored callbacks must not be invoked now.
    std::lock_guard<std::mutex> lock(queueLock);
    pending.clear();
}

void Achievements::invalidateMemoryRegions() {
    // The table points into the core's memory as the old accessors / map described it: never read it again.
    regions.invalidate();
    regionsInitTried = false;
}

void Achievements::setMemoryMap(const struct retro_memory_map* map) {
    invalidateMemoryRegions();
    mapVersion++;
    descriptors.clear();
    descriptorStrings.clear();
    if (!map || !map->descriptors || map->num_descriptors == 0) { haveMemoryMap = false; return; }
    descriptors.assign(map->descriptors, map->descriptors + map->num_descriptors);
    // addrspace strings may live on the core's stack: keep our own copies.
    descriptorStrings.reserve(descriptors.size());
    for (auto& d : descriptors) {
        descriptorStrings.emplace_back(d.addrspace ? d.addrspace : "");
        d.addrspace = descriptorStrings.back().empty() ? nullptr : descriptorStrings.back().c_str();
    }
    memoryMap.descriptors = descriptors.data();
    memoryMap.num_descriptors = (unsigned) descriptors.size();
    haveMemoryMap = true;
    LOGI("achievements: memory map with %u descriptors", memoryMap.num_descriptors);
}

void Achievements::setCoreMemoryAccessors(size_t (*getSize)(unsigned), void* (*getData)(unsigned)) {
    invalidateMemoryRegions();
    coreGetMemorySize = getSize;
    coreGetMemoryData = getData;
}

void Achievements::coreMemoryInfo(uint32_t id, rc_libretro_core_memory_info_t* info) {
    auto& self = getInstance();
    info->data = self.coreGetMemoryData ? (uint8_t*) self.coreGetMemoryData(id) : nullptr;
    info->size = self.coreGetMemorySize ? self.coreGetMemorySize(id) : 0;
}

void Achievements::initMemoryRegions() {
    if (regions.build(haveMemoryMap ? &memoryMap : nullptr, coreMemoryInfo, consoleId)) {
        LOGI("achievements: %u memory regions, %zu bytes", regions.count(), regions.totalSize());
    } else {
        LOGW("achievements: rc_libretro_memory_init failed for console %u; using raw SYSTEM_RAM", consoleId);
    }
}

uint32_t Achievements::readMemory(uint32_t address, uint8_t* buffer, uint32_t numBytes, rc_client_t*) {
    auto& self = getInstance();
    if (!self.regions.ready() && !self.regionsInitTried) {
        // rc_client validates every achievement address before the load callback runs; never answer those
        // reads from the raw SYSTEM_RAM fallback when a region table can be built (mirrors RetroArch).
        self.regionsInitTried = true;
        self.initMemoryRegions();
    }
    if (self.regions.ready()) return self.regions.read(address, buffer, numBytes);
    // No region table: raw system RAM, then save RAM (as nx-redux ra_integration.c).
    for (unsigned id : { (unsigned) RETRO_MEMORY_SYSTEM_RAM, (unsigned) RETRO_MEMORY_SAVE_RAM }) {
        if (!self.coreGetMemoryData || !self.coreGetMemorySize) break;
        auto* data = (uint8_t*) self.coreGetMemoryData(id);
        size_t size = self.coreGetMemorySize(id);
        if (!data || size == 0) continue;
        if (address >= size || numBytes > size - address) return 0;
        memcpy(buffer, data + address, numBytes);
        return numBytes;
    }
    return 0;
}

void Achievements::serverCall(const rc_api_request_t* request, rc_client_server_callback_t callback, void* callbackData, rc_client_t*) {
    auto& self = getInstance();
    std::lock_guard<std::mutex> lock(self.queueLock);
    uint32_t id = self.nextRequestId++;
    self.pending[id] = { callback, callbackData };
    self.outgoingCalls.push_back({ id, request->url ? request->url : "", request->post_data ? request->post_data : "",
                                   request->content_type ? request->content_type : "" });
}

void Achievements::serverResponse(uint32_t requestId, int httpStatus, const std::string& body) {
    std::lock_guard<std::mutex> lock(queueLock);
    responses.push_back({ requestId, httpStatus, body });
}

void Achievements::onFrame(bool paused) {
    if (!client) return;
    std::vector<QueuedResponse> batch;
    {
        std::lock_guard<std::mutex> lock(queueLock);
        batch.swap(responses);
    }
    for (auto& r : batch) {
        PendingCallback cb {};
        {
            std::lock_guard<std::mutex> lock(queueLock);
            auto it = pending.find(r.requestId);
            if (it == pending.end()) continue;   // answered after disable(): drop
            cb = it->second;
            pending.erase(it);
        }
        rc_api_server_response_t response {};
        response.body = r.body.c_str();
        response.body_length = r.body.size();
        response.http_status_code = r.status;
        cb.callback(&response, cb.callbackData);
    }
    if (paused) rc_client_idle(client); else rc_client_do_frame(client);
}

void Achievements::pushEvent(AchievementEvent e) {
    std::lock_guard<std::mutex> lock(queueLock);
    events.push_back(std::move(e));
}

std::vector<AchievementEvent> Achievements::takeEvents() {
    std::lock_guard<std::mutex> lock(queueLock);
    std::vector<AchievementEvent> out;
    out.swap(events);
    return out;
}

std::vector<AchievementServerCall> Achievements::takeServerCalls() {
    std::lock_guard<std::mutex> lock(queueLock);
    std::vector<AchievementServerCall> out;
    out.swap(outgoingCalls);
    return out;
}

static AchievementEvent eventFor(const rc_client_achievement_t* a, int type) {
    AchievementEvent e;
    e.type = type;
    if (a) {
        e.id = a->id; e.title = a->title ? a->title : ""; e.description = a->description ? a->description : "";
        e.badge = a->badge_name; e.points = a->points; e.progress = a->measured_progress; e.percent = a->measured_percent;
    }
    return e;
}

void Achievements::eventHandler(const rc_client_event_t* event, rc_client_t*) {
    auto& self = getInstance();
    switch (event->type) {
        case RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED:
        case RC_CLIENT_EVENT_ACHIEVEMENT_CHALLENGE_INDICATOR_SHOW:
        case RC_CLIENT_EVENT_ACHIEVEMENT_CHALLENGE_INDICATOR_HIDE:
        case RC_CLIENT_EVENT_ACHIEVEMENT_PROGRESS_INDICATOR_SHOW:
        case RC_CLIENT_EVENT_ACHIEVEMENT_PROGRESS_INDICATOR_UPDATE:
            self.pushEvent(eventFor(event->achievement, (int) event->type));
            break;
        case RC_CLIENT_EVENT_ACHIEVEMENT_PROGRESS_INDICATOR_HIDE:
        case RC_CLIENT_EVENT_GAME_COMPLETED:
        case RC_CLIENT_EVENT_RESET:
        case RC_CLIENT_EVENT_DISCONNECTED:
        case RC_CLIENT_EVENT_RECONNECTED:
            self.pushEvent(eventFor(nullptr, (int) event->type));
            break;
        case RC_CLIENT_EVENT_SERVER_ERROR: {
            AchievementEvent e = eventFor(nullptr, (int) event->type);
            if (event->server_error) {
                e.extra = event->server_error->error_message ? event->server_error->error_message : "";
                e.id = event->server_error->related_id;
            }
            self.pushEvent(std::move(e));
            break;
        }
        default:
            LOGD("achievements: unhandled event %u", event->type);   // leaderboards and trackers are out of scope
            break;
    }
}

void Achievements::logMessage(const char* message, const rc_client_t*) { LOGI("rcheevos: %s", message); }

void Achievements::login(const std::string& user, const std::string& token) {
    if (!client) return;
    rc_client_begin_login_with_token(client, user.c_str(), token.c_str(), loginCallback, nullptr);
}

void Achievements::loginCallback(int result, const char* errorMessage, rc_client_t*, void*) {
    AchievementEvent e;
    e.type = result == RC_OK ? RA_EVENT_LOGIN_OK : RA_EVENT_LOGIN_FAILED;
    e.extra = errorMessage ? errorMessage : "";
    getInstance().pushEvent(std::move(e));
}

void Achievements::loadGame(const std::string& hash, uint32_t console) {
    if (!client) return;
    consoleId = console;
    regionsInitTried = true;
    initMemoryRegions();   // before rc_client_activate_game validates addresses through readMemory
    rc_client_begin_load_game(client, hash.c_str(), loadGameCallback, nullptr);
}

void Achievements::loadGameCallback(int result, const char* errorMessage, rc_client_t* client, void*) {
    auto& self = getInstance();
    AchievementEvent e;
    if (result == RC_NO_GAME_LOADED) {   // the server does not know this hash
        e.type = RA_EVENT_GAME_UNKNOWN;
        e.extra = errorMessage ? errorMessage : "";
        self.pushEvent(std::move(e));
        return;
    }
    if (result != RC_OK) {
        e.type = RA_EVENT_GAME_LOAD_FAILED;
        e.extra = errorMessage ? errorMessage : "";
        self.pushEvent(std::move(e));
        return;
    }
    const rc_client_game_t* game = rc_client_get_game_info(client);
    if (!game || game->id == 0) { e.type = RA_EVENT_GAME_UNKNOWN; self.pushEvent(std::move(e)); return; }
    self.consoleId = game->console_id ? game->console_id : self.consoleId;
    self.initMemoryRegions();   // emulation thread: this callback runs from onFrame()
    rc_client_user_game_summary_t summary {};
    rc_client_get_user_game_summary(client, &summary);
    e.type = RA_EVENT_GAME_LOADED;
    e.id = game->id;
    e.title = game->title ? game->title : "";
    e.badge = game->badge_name ? game->badge_name : "";
    e.points = summary.num_core_achievements;
    e.extra = std::to_string(summary.num_unlocked_achievements);
    self.pushEvent(std::move(e));
}

void Achievements::unloadGame() {
    if (!client) return;
    rc_client_unload_game(client);
    invalidateMemoryRegions();
}

std::vector<AchievementInfo> Achievements::list() {
    std::vector<AchievementInfo> out;
    if (!client || !rc_client_is_game_loaded(client)) return out;
    rc_client_achievement_list_t* list = rc_client_create_achievement_list(client, RC_CLIENT_ACHIEVEMENT_CATEGORY_CORE, RC_CLIENT_ACHIEVEMENT_LIST_GROUPING_LOCK_STATE);
    if (!list) return out;
    // In display order (the server's list order; ids follow creation), not grouped by lock state: rc_client keeps each
    // subset's achievements in one array in that order and the buckets point into it, so within a subset the address
    // is the position. Buckets come subset by subset.
    std::vector<std::pair<uint32_t, const rc_client_achievement_t*>> ordered;
    uint32_t subset = 0;
    for (uint32_t b = 0; b < list->num_buckets; b++) {
        const auto& bucket = list->buckets[b];
        if (b > 0 && bucket.subset_id != list->buckets[b - 1].subset_id) subset++;
        for (uint32_t i = 0; i < bucket.num_achievements; i++) ordered.emplace_back(subset, bucket.achievements[i]);
    }
    std::sort(ordered.begin(), ordered.end(), [](const auto& x, const auto& y) {
        return x.first != y.first ? x.first < y.first : std::less<const rc_client_achievement_t*>()(x.second, y.second);
    });
    for (const auto& entry : ordered) {
        const rc_client_achievement_t* a = entry.second;
        out.push_back({ a->id, a->title ? a->title : "", a->description ? a->description : "", a->points, a->badge_name,
                        a->state, (int64_t) a->unlock_time, a->measured_progress, a->measured_percent, a->rarity, a->type, a->category });
    }
    rc_client_destroy_achievement_list(list);
    return out;
}

bool Achievements::buildAwardRequest(const std::string& user, const std::string& token, uint32_t achievementId,
                                     const std::string& hash, uint32_t secondsSinceUnlock, std::string& url, std::string& post) {
    rc_api_award_achievement_request_t params {};
    params.username = user.c_str();
    params.api_token = token.c_str();
    params.achievement_id = achievementId;
    params.hardcore = 0;
    params.game_hash = hash.c_str();
    params.seconds_since_unlock = secondsSinceUnlock;
    rc_api_request_t request {};
    int result = rc_api_init_award_achievement_request(&request, &params);
    if (result != RC_OK) { rc_api_destroy_request(&request); return false; }
    url = request.url ? request.url : "";
    post = request.post_data ? request.post_data : "";
    rc_api_destroy_request(&request);
    return true;
}

}
