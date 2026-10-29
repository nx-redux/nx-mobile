package com.nxredux.mobile.shortcuts

/**
 * The SELECT / START / MENU page's state, for one pad [model]. PRESS: the user presses the three, each ticks off
 * ([found]) as it's recognised; any other button is "not one of the three" ([lastOther]), A, B, Back and the d-pad
 * stay the page's (its rows). All three found: DONE. Otherwise the "I've pressed them all" row ([pressedAll]) goes to BIND, which takes the next fresh press as each missing role in turn ([binding]), never B or Back
 * (they cancel back to PRESS), never A or the d-pad (the page's rows), never a button another role already has ([lastTaken]). MENU can be skipped
 * (the Skip row, [skipMenu]): SELECT+START opens the menu instead.
 */
class SystemButtonsPage(val model: PadModel, map: SystemButtonMap) {
    enum class Phase { PRESS, BIND, DONE }

    var phase: Phase = Phase.PRESS
        private set
    var found: Set<SystemRole> = emptySet()
        private set
    var binding: SystemRole? = null
        private set
    var lastOther: Boolean = false
        private set
    /** BIND's last press was a button [SystemRole] already has; null otherwise. */
    var lastTaken: SystemRole? = null
        private set
    var map: SystemButtonMap = map
        private set
    var menuSkipped: Boolean = false
        private set

    // Keys whose press the page used: their repeats and release are the page's too.
    private val taken = HashSet<PhysicalKey>()
    // Keys down when BIND started (START held to get there): their repeats do nothing until released.
    private val heldIntoBind = HashSet<PhysicalKey>()

    /** A key from the pad; [repeat]: an auto-repeat. True when the page used it (the menu must not see it). */
    fun onKey(key: PhysicalKey, down: Boolean, repeat: Boolean): Boolean {
        if (!down) { heldIntoBind.remove(key); return taken.remove(key) }
        if (repeat) return key in heldIntoBind || key in taken
        val used = when (phase) {
            Phase.PRESS -> press(key)
            Phase.BIND -> bind(key)
            Phase.DONE -> false
        }
        if (used) taken += key
        return used
    }

    private fun press(key: PhysicalKey): Boolean {
        val role = SystemButtons.roleOf(map, model.key, key)
        if (role == null) {
            if (key.keyCode in PAGE_KEYS) return false
            lastOther = true
            return true
        }
        lastOther = false
        found = found + role
        if (found.size == SystemRole.entries.size) finish()
        return true
    }

    private fun bind(key: PhysicalKey): Boolean {
        val role = binding ?: return false
        if (key.keyCode == KeyCodes.BUTTON_B || key.keyCode == KeyCodes.BACK) { cancelBind(); return true }
        // A and the d-pad choose the page's rows (Skip, Cancel): a highlighted row's A must not become MENU.
        if (key.keyCode in PAGE_KEYS) return false
        val holder = SystemButtons.roleOf(map, model.key, key)
        if (holder != null && holder != role) { lastTaken = holder; return true }
        lastTaken = null
        map = SystemButtons.bind(map, model.key, role, key)
        found = found + role
        next()
        return true
    }

    /** "I've pressed them all": BIND for the first missing role (SELECT, START, MENU order); none missing is DONE. */
    fun pressedAll() {
        if (phase != Phase.PRESS) return
        lastOther = false
        next()
    }

    /** Only while BIND waits for MENU: done without one (SELECT+START opens the menu). */
    fun skipMenu() {
        if (phase != Phase.BIND || binding != SystemRole.MENU) return
        menuSkipped = true
        finish()
    }

    /** B or Back in BIND: back to PRESS, the binds made so far kept. */
    fun cancelBind() {
        if (phase != Phase.BIND) return
        phase = Phase.PRESS
        binding = null
        lastTaken = null
    }

    private fun next() {
        val missing = SystemRole.entries.firstOrNull { it !in found }
        if (missing == null) return finish()
        if (phase == Phase.PRESS) heldIntoBind += taken
        phase = Phase.BIND
        binding = missing
    }

    private fun finish() {
        phase = Phase.DONE
        binding = null
    }

    private companion object {
        /** The page's own keys in PRESS: its rows' navigation and A / B. */
        val PAGE_KEYS = setOf(
            KeyCodes.BUTTON_A, KeyCodes.BUTTON_B, KeyCodes.BACK,
            KeyCodes.DPAD_UP, KeyCodes.DPAD_DOWN, KeyCodes.DPAD_LEFT, KeyCodes.DPAD_RIGHT,
        )
    }
}
