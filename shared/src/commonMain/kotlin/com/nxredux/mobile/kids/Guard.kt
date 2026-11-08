package com.nxredux.mobile.kids

/** The guard's ongoing notification while it watches the front app. */
const val KIDS_GUARD_NOTICE = "Kids Mode is on"

/** Android's system UI: the shade, the lock screen, the volume panel. Never sent back. */
const val SYSTEM_UI_PACKAGE = "com.android.systemui"

/**
 * System surfaces an allowed app opens over itself, never sent back: its runtime permission dialog (the permission
 * controller) and the share sheet / app chooser (the resolver in "android", or the intent resolver module).
 */
val ALWAYS_EXEMPT_PACKAGES: Set<String> = setOf(
    "com.google.android.permissioncontroller",
    "com.android.permissioncontroller",
    "android",
    "com.android.intentresolver",
)

/** What a setup step opens for a grant (usage access, exact alarms): Settings, left alone only while [KidsController.granting]. */
val GRANT_SCREEN_PACKAGES: Set<String> = setOf("com.android.settings")

/**
 * The guard's decision for the activity last seen in front ([pkg], [className]; null: none seen lately): send it back to
 * the tiles screen unless it is on the parent's list ([allowedPkgs]) or a system surface ([exempt]). NX Redux itself
 * ([own]) is never sent back, except its game screen ([gameClass]): while Locked a game resumed from Recents (Android
 * killed `:game` and recreates it from its saved resume slot) is locked as a game in front is.
 */
fun guardShouldBounce(pkg: String?, className: String?, own: String, gameClass: String, allowedPkgs: Set<String>, exempt: Set<String>): Boolean {
    if (pkg.isNullOrEmpty()) return false
    if (pkg == own) return className == gameClass
    return pkg !in allowedPkgs && pkg !in exempt
}

/** A usage event the guard reads: an activity resumed, paused or stopped. */
enum class GuardEventType { RESUMED, PAUSED, STOPPED }

data class GuardEvent(val type: GuardEventType, val pkg: String, val cls: String?, val at: Long)

/**
 * The activity in front from [events] (oldest first): the last resume with no later pause or stop of that same
 * activity. Null when there is none (the front activity was resumed before the events start, or has since paused
 * with nothing resumed after it).
 */
fun guardFront(events: List<GuardEvent>): GuardEvent? {
    var front: GuardEvent? = null
    for (e in events) when (e.type) {
        GuardEventType.RESUMED -> front = e
        GuardEventType.PAUSED, GuardEventType.STOPPED -> if (front != null && front.pkg == e.pkg && front.cls == e.cls) front = null
    }
    return front
}

/** The packages of the allowed apps' ids ("pkg/activity"). */
fun guardAllowedPackages(appIds: Collection<String>): Set<String> =
    appIds.mapNotNullTo(HashSet()) { id -> id.substringBefore('/').takeIf { it.isNotEmpty() } }

/**
 * The surfaces never sent back: the keyboard ([defaultIme], Android's "pkg/.Service" id, or null), system UI, the
 * permission dialog and share sheet ([ALWAYS_EXEMPT_PACKAGES]), and Settings while [granting].
 */
fun guardExempt(defaultIme: String?, granting: Boolean): Set<String> = buildSet {
    defaultIme?.substringBefore('/')?.takeIf { it.isNotEmpty() }?.let(::add)
    add(SYSTEM_UI_PACKAGE)
    addAll(ALWAYS_EXEMPT_PACKAGES)
    if (granting) addAll(GRANT_SCREEN_PACKAGES)
}

/**
 * The guard runs only while Locked, with Usage access (it cannot see the front app without it), and while no menu is
 * shown (the tiles screen is in front, nothing to watch).
 */
fun guardRuns(mode: KidsMode, usageAccess: Boolean, menuShown: Boolean): Boolean =
    mode is KidsMode.Locked && usageAccess && !menuShown
