package com.nxredux.mobile.runner

/**
 * The phone's microphone permission (Android's RECORD_AUDIO), for Settings → Library → Microphone access. [usable]: the
 * device has a mic and is not a TV; false hides the row. [granted] is read again at every resume.
 */
interface MicAccess {
    val usable: Boolean
    fun granted(): Boolean
    /** Shows the platform's prompt when it would still appear, else opens the app's system settings page. */
    fun request()
}
