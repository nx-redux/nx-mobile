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
 * Every activity still in front from [events] (oldest first): each resume with no later pause or stop of that same
 * activity, oldest first. With more than one display several apps are resumed at once, and the last resume alone may
 * be an allowed app on one display while a blocked one stays resumed on the other.
 */
fun guardFronts(events: List<GuardEvent>): List<GuardEvent> {
    val fronts = LinkedHashMap<Pair<String, String?>, GuardEvent>()
    for (e in events) {
        val key = e.pkg to e.cls
        when (e.type) {
            GuardEventType.RESUMED -> { fronts.remove(key); fronts[key] = e }
            GuardEventType.PAUSED, GuardEventType.STOPPED -> fronts.remove(key)
        }
    }
    return fronts.values.toList()
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
 * The guard runs only while Locked, with Usage access (it cannot see the front app without it), and while something
 * other than the menu may be in front: no menu shown, or an app open on the other display ([appOnOtherDisplay], dual
 * mode's top screen), where a link out of it opens beside the shown menu.
 */
fun guardRuns(mode: KidsMode, usageAccess: Boolean, menuShown: Boolean, appOnOtherDisplay: Boolean = false): Boolean =
    mode is KidsMode.Locked && usageAccess && (!menuShown || appOnOtherDisplay)
