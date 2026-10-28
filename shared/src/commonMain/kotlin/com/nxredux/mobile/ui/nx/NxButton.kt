package com.nxredux.mobile.ui.nx

enum class NxButton { UP, DOWN, LEFT, RIGHT, A, B, X, Y, L1, R1, L2, R2, START, SELECT, MENU, C_UP, C_DOWN, C_LEFT, C_RIGHT,
    /** The Wii pads' Home button (the core's R3, Dolphin's Home). */
    HOME,
    /** The PS2 pad's stick clicks, their own small pills (the core's L3 / R3). */
    L3, R3;
    val isDirection: Boolean get() = this == UP || this == DOWN || this == LEFT || this == RIGHT
}

sealed interface PadEvent {
    val button: NxButton
    data class Down(override val button: NxButton) : PadEvent
    data class Up(override val button: NxButton) : PadEvent
}

/** The same event (a Down stays a Down, an Up an Up) for the button [f] maps this one to. */
fun PadEvent.map(f: (NxButton) -> NxButton): PadEvent = when (this) {
    is PadEvent.Down -> PadEvent.Down(f(button))
    is PadEvent.Up -> PadEvent.Up(f(button))
}
