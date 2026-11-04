package com.nxredux.mobile.runner

/**
 * Which key code a controller's face buttons send by position. [XBOX] is Android's usual convention (bottom BUTTON_A,
 * right B, left X, top Y); [NINTENDO] has the letters where a Nintendo pad prints them (bottom BUTTON_B, right A, left Y,
 * top X). Detection says where a pad's keycodes sit; Controls → Controller Layout can name other printed letters
 * ([PhysicalKeys]).
 */
enum class PadFaceLayout {
    XBOX, NINTENDO;

    companion object {
        /** Nintendo's USB vendor id: its own pads and third-party pads in Switch mode report it. */
        const val NINTENDO_VENDOR_ID = 0x057e

        /**
         * Nintendo-layout pads that report another vendor id, as InputDevice name fragments (matched like
         * [PadBandRule.isClipOn]: ignoring case, spaces, hyphens and underscores). Kept small: add a pad once seen on
         * hardware. The Console Settings override covers any pad this gets wrong.
         */
        val NintendoNames: List<String> = listOf(
            "GameSir-Pocket", // GameSir Pocket Taco: InputDevice name "GameSir-Pocket 1", observed on hardware
        )

        private fun squash(s: String): String = s.lowercase().filterNot { it == ' ' || it == '-' || it == '_' }

        /**
         * The layout of the device a key event came from, or null for no physical pad: a negative [deviceId] or a
         * virtual device (adb-injected events carry deviceId -1, a Virtual device), whose keys are sent as is.
         */
        fun detectDevice(deviceId: Int, isVirtual: Boolean, vendorId: Int, name: String): PadFaceLayout? =
            if (deviceId < 0 || isVirtual) null else detect(vendorId, name)

        /** [NINTENDO] for Nintendo's vendor id or a [NintendoNames] name, else [XBOX]. */
        fun detect(vendorId: Int, name: String): PadFaceLayout {
            if (vendorId == NINTENDO_VENDOR_ID) return NINTENDO
            val n = squash(name)
            return if (NintendoNames.any { squash(it) in n }) NINTENDO else XBOX
        }
    }
}
