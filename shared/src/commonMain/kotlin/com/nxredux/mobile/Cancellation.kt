package com.nxredux.mobile

import kotlin.coroutines.cancellation.CancellationException

/*
 * Failure handling that never swallows cancellation: a coroutine cancelled inside [block] must stop, not read the
 * CancellationException as an ordinary failure. Only [Exception]s are caught and an Error still propagates, except
 * through [tryOrAny].
 */

/** [block]'s result, or [fallback]'s for any exception but cancellation. */
inline fun <T> tryOr(fallback: (Exception) -> T, block: () -> T): T =
    try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { fallback(e) }

/** [block]'s result, or null for any exception but cancellation. */
inline fun <T> tryOrNull(block: () -> T): T? = tryOr({ null }, block)

/** Runs [block], ignoring any exception but cancellation. */
inline fun tryQuietly(block: () -> Unit) = tryOr({}, block)

/**
 * [tryOr] that takes an Error as a failure too: for a launch on a scope with no handler, or a call whose failure can
 * be a linkage or memory Error.
 */
inline fun <T> tryOrAny(fallback: (Throwable) -> T, block: () -> T): T =
    try { block() } catch (e: CancellationException) { throw e } catch (e: Throwable) { fallback(e) }
