package com.nxredux.mobile.kids

/** The guard's ongoing notification while it watches the front app. */
const val KIDS_GUARD_NOTICE = "Kids Mode is on"

/** Android's system UI: the shade, the lock screen, the volume panel. Never sent back. */
const val SYSTEM_UI_PACKAGE = "com.android.systemui"

/** What a setup step opens for a grant (usage access, exact alarms): left alone only while [KidsController.granting]. */
val GRANT_SCREEN_PACKAGES: Set<String> = setOf(
    "com.android.settings",
    "com.google.android.permissioncontroller",
    "com.android.permissioncontroller",
)

/**
 * The guard's decision for the app last seen in front ([pkg]; null: none seen lately): send it back to the tiles screen
 * unless it is NX Redux itself ([own]), on the parent's list ([allowedPkgs]) or a system surface ([exempt]).
 */
fun guardShouldBounce(pkg: String?, own: String, allowedPkgs: Set<String>, exempt: Set<String>): Boolean =
    !pkg.isNullOrEmpty() && pkg != own && pkg !in allowedPkgs && pkg !in exempt

/** The packages of the allowed apps' ids ("pkg/activity"). */
fun guardAllowedPackages(appIds: Collection<String>): Set<String> =
    appIds.mapNotNullTo(HashSet()) { id -> id.substringBefore('/').takeIf { it.isNotEmpty() } }

/**
 * The surfaces never sent back: the keyboard ([defaultIme], Android's "pkg/.Service" id, or null), system UI, and the
 * grant screens while [granting].
 */
fun guardExempt(defaultIme: String?, granting: Boolean): Set<String> = buildSet {
    defaultIme?.substringBefore('/')?.takeIf { it.isNotEmpty() }?.let(::add)
    add(SYSTEM_UI_PACKAGE)
    if (granting) addAll(GRANT_SCREEN_PACKAGES)
}

/**
 * The guard runs only while Locked, with Usage access (it cannot see the front app without it), and while no menu is
 * shown (the tiles screen is in front, nothing to watch).
 */
fun guardRuns(mode: KidsMode, usageAccess: Boolean, menuShown: Boolean): Boolean =
    mode is KidsMode.Locked && usageAccess && !menuShown
