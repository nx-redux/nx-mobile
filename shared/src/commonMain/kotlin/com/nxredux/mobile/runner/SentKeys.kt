package com.nxredux.mobile.runner

/**
 * What each held key was sent to the core as, so its key-up releases the same code even if the mapping changed while it
 * was held. Keyed by (device id, keycode) for physical keys and by the on-screen button for the pad.
 */
class SentKeys<K> {
    private val sent = HashMap<K, Int>()

    fun down(key: K, sent: Int): Int { this.sent[key] = sent; return sent }

    /** What a held [key]'s down sent, without releasing it (a key-repeat sends the same); null when it is not held. */
    fun held(key: K): Int? = sent[key]

    fun up(key: K, fallback: Int): Int = sent.remove(key) ?: fallback

    fun clear() = sent.clear()
}
