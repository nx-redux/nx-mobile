package com.nxredux.mobile

import kotlin.coroutines.cancellation.CancellationException

/*
 * Failure handling that never swallows cancellation: a coroutine cancelled inside [block] must stop, not read the
 * CancellationException as an ordinary failure. Only [Exception]s are caught; an Error still propagates.
 */

/** [block]'s result, or [fallback]'s for any exception but cancellation. */
inline fun <T> tryOr(fallback: (Exception) -> T, block: () -> T): T =
    try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { fallback(e) }

/** [block]'s result, or null for any exception but cancellation. */
inline fun <T> tryOrNull(block: () -> T): T? = tryOr({ null }, block)

/** Runs [block], ignoring any exception but cancellation. */
inline fun tryQuietly(block: () -> Unit) = tryOr({}, block)
