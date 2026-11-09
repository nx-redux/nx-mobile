package com.nxredux.mobile.netplay

import com.nxredux.mobile.netplay.usb.UsbFail
import com.nxredux.mobile.netplay.usb.UsbLinkState

/** Netplay copy, worded as the NX Redux handheld wizard (nx-redux wizard_net.c / wizard.c). */
object NetplayText {
    const val TITLE = "Netplay"
    const val HOST = "Host Game"
    const val JOIN = "Join Game"
    const val EMULATOR = "Emulator"
    const val AUTO = "Auto (match the other device)"
    const val NEED_WIFI = "Connect to Wi-Fi first"
    const val WIFI_SETTINGS = "Wi-Fi settings"
    const val SAME_WIFI = "Other device must be on the same Wi-Fi"
    const val WAITING_PLAYER = "Waiting for player..."
    const val PLAYER_CONNECTED = "Player connected"
    const val SELECT_HOST = "Select Host"
    const val SEARCHING = "Searching for hosts..."
    const val NO_HOST = "No host found.\n\nMake sure the host has\nstarted hosting first."
    const val SENDING = "Sending saves..."
    const val RECEIVING = "Receiving saves..."
    const val STARTING = "Connected - starting game..."
    const val CONNECTING = "Connecting..."
    /** While the GBA game's link mode is read, before the Role page. */
    const val READING_GAME = "Reading the game..."
    const val HOST_FAILED = "Could not start hosting.\n\nPlease try again."
    const val NO_ANSWER = "The host did not answer.\n\nPlease try again."
    const val NOT_WAITING = "That device is not waiting\nfor a netplay player."
    const val CONNECT_FAILED = "Connection failed.\n\nThe host may have stopped\nwaiting."
    const val UNEXPECTED = "Unexpected reply from the host."
    const val TOO_MANY_SAVES = "The host offered more saves\nthan expected."
    const val UNEXPECTED_FILE = "The host offered an\nunexpected file."
    const val STOPPED_RESPONDING = "The host stopped responding."
    const val COPY_FAILED = "Could not copy the host's saves."
    /** The Hosting page's one-line status after a joiner's save copy failed (the handheld's two-line notice, on one line). */
    const val SAVE_TRANSFER_FAILED = "Save transfer failed - waiting for another player"
    const val WIFI_LOST = "Wi-Fi disconnected."
    /** One side picked Cable Link over Wi-Fi and the other did not send the capability; both screens show it. */
    const val CABLE_LINK_NEEDS_UPDATE = "Cable Link over Wi-Fi needs the other phone on the latest NX Redux."
    const val SENDING_GAME = "Sending game…"
    const val RECEIVING_GAME = "Receiving game…"
    const val CABLE_LINK_COPY_FAILED = "Could not swap games and saves\nwith the other phone.\n\nPlease try again."
    const val GAME_DAMAGED = "The other phone's game\narrived damaged.\n\nPlease try again."
    const val CONNECTION = "Connection"
    const val WIFI = "Wi-Fi"
    const val USB_CABLE = "USB Cable"
    const val USB_CONNECT = "Connect the phone to the handheld's bottom port, or to another phone."
    const val USB_STARTING = "Starting USB link..."
    const val USB_VPN_NOTE = "Android will ask to allow a VPN. It only carries the cable link."
    const val USB_NO_LINK = "No USB link.\n\nA handheld: use its bottom port and\npick USB Cable on it.\n\nTwo phones: on one phone, open the USB\nnotification and switch \"USB controlled by\"."
    const val USB_PEER_HOST = "No USB link: the other device took\ncontrol of the USB connection.\n\nOpen this phone's USB notification\nand switch \"USB controlled by\"."
    const val USB_NO_LINK_HANDHELD = "No USB link.\n\nUse the handheld's bottom port and\npick USB Cable on the handheld."
    const val USB_VERSION = "Both devices need the same\nNX Redux version."
    const val USB_VPN_BLOCKED = "Another VPN app is active.\n\nTurn it off to use the USB cable."
    const val USB_LOST = "USB cable disconnected."
    const val USB_UNAVAILABLE = "USB link unavailable\non this device."
    /** VPN consent or USB permission refused (Android reports an always-on VPN's block as a refusal). */
    const val USB_DENIED = "USB Cable needs Android's permission.\n\nIf another VPN app is always on,\nturn it off first."
    /** The Hosting page over the cable: large, then medium. */
    const val USB_ADDRESS = "USB"
    const val USB_CONNECTED = "Connected by USB cable"
    /** A cable failure as the Netplay sheet and Device Sync show it. */
    fun usbFail(f: UsbLinkState.Failed): String = when (f.reason) {
        UsbFail.NO_LINK -> if (f.handheld) USB_NO_LINK_HANDHELD else USB_NO_LINK
        UsbFail.PEER_HOST -> USB_PEER_HOST
        UsbFail.VERSION -> USB_VERSION
        UsbFail.VPN_BLOCKED -> USB_VPN_BLOCKED
        UsbFail.UNSUPPORTED -> USB_UNAVAILABLE
        UsbFail.DENIED -> USB_DENIED
    }
    const val LEAVE_TITLE = "Leave netplay?"
    const val LEAVE_TEXT = "Leaving ends the netplay session."
    const val LEAVE_LINK_TEXT = "Leaving ends the link. You keep playing on your own save."
    const val CONTINUE = "Continue"
    const val LEAVE = "Leave"
    const val ENDED = "Netplay ended"
    const val ENDED_DETAIL = "The other player left or the connection was lost."
    const val ENDED_COPY = "Netplay ended - progress here won't be saved."
    const val FAILED = "Netplay failed"
    const val OWN_COPY_FAILED = "Could not copy your saves - starting without them."
    const val FAILED_MISMATCH = "Netplay failed - the other device's emulator doesn't match."
    const val FAILED_CONNECT = "Could not reach the other device."
    const val FAILED_TIMEOUT = "The other device stopped responding."
    const val FAILED_IO = "The connection was lost."
    fun waiting(seconds: Int) = "Waiting for the other player... ($seconds s)"
    fun peerPaused(seconds: Int) = "Other player paused ($seconds s)"
    fun usingCopy(coreName: String) = "Using $coreName for this match - progress won't be saved."
    const val SAVES_NOT_COPIED = "Saves not copied - playing on empty saves."
    /** The host runs another emulator (the handheld's wording); [tag] is the host's folder, null when it sent none. */
    fun coreMismatch(name: String, tag: String?): String =
        "The host is using\n${if (tag.isNullOrEmpty()) name else "$name ($tag folder)"}.\n\n$SAME_FOLDER"
    /** This device has the host's emulator, but its Emulator row pins another. */
    fun setEmulator(name: String) = "The host is using $name.\nSet Emulator to $name."
    /** Host or Join over Wi-Fi with the Emulator row pinned to a core that links this game over the cable alone ([name]: mGBA). */
    fun cableOnly(name: String) = "$name links this game over the USB cable only.\n\nPick USB Cable, or another emulator."
    /** Opening the sheet on a device without a USB link for a game that [name] (mGBA) links over the cable alone. */
    fun cableOnlyNoUsb(name: String) = "$name links this game over the USB cable only, which this device does not have."
    /** mGBA off the cable against a side without the Wi-Fi link modes (the handheld's WIZ_NET_MGBA_WL_TEXT). */
    const val NO_WIFI_MODES = "The other device can't link\nmGBA over Wi-Fi.\n\nUpdate it, or use the USB Cable."
    /** The handheld's WIZ_NET_CORE_HINT. */
    const val SAME_FOLDER = "Pick the game from the same\nfolder on both devices."
    /** What a player calls a game's Wi-Fi link mode ([GbaWifiLink] token); "" for none. */
    fun linkModeName(wl: String?): String = when (wl) {
        "rfu" -> "Wireless Adapter"
        "poke" -> "Pokémon cable"
        "aw1" -> "Advance Wars cable"
        "aw2" -> "Advance Wars 2 cable"
        else -> ""
    }
    /**
     * gpSP and mGBA in Wi-Fi modes that can't link (the handheld's `link-` REJECT text): [theirs] the other side's mode,
     * [ours] this one's; the same mode on both only fails over the USB cable. Worded for either side of the refusal.
     */
    fun linkModes(theirs: String?, ours: String?, hint: String = SAME_FOLDER): String =
        if (theirs != ours) "The other player's game links\nwith the ${linkModeName(theirs)},\nthis one with the ${linkModeName(ours)}.\n\n$hint"
        else "With two different emulators,\nthis game links only\nover Wi-Fi.\n\n$hint"
    /** Flashed on the host's Wi-Fi waiting page when a joiner whose game links in another mode was refused. */
    const val LINK_TURNED_AWAY = "A player whose game links in another mode was turned away."
    /** Flashed on the host's waiting page when a joiner on another core was refused. */
    fun coreTurnedAway(name: String) = "A player using $name was turned away."
    fun joinAnyway(host: String, own: String) =
        "The host is running\n${elide(host)}\n\nYou are running\n${elide(own)}\n\nJoin anyway?"
    fun youArePlayer(n: Int) = "You are Player $n\nWaiting for host to start..."
    fun reject(reason: RejectReason): String = when (reason) {
        RejectReason.VERSION -> "The host is running a\ndifferent netplay version."
        RejectReason.GAME -> "The host is running a\ndifferent game."
        RejectReason.ROLE -> "Both devices are hosting.\n\nOne of them must join instead."
        RejectReason.SYNC -> "The host could not share\nits saves."
        RejectReason.CABLE_LINK -> CABLE_LINK_NEEDS_UPDATE
        RejectReason.CORE, RejectReason.LINK, RejectReason.OTHER -> "The host refused the connection."
    }
    /** Titles in prompts are cut to 40 characters plus "..." (the handheld's PROMPT_TITLE_MAX). */
    fun elide(title: String): String = if (title.length <= 40) title else title.take(40) + "..."
}
