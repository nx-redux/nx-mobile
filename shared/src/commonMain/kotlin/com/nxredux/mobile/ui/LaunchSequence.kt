package com.nxredux.mobile.ui

/**
 * Where a launch sequence is ([AppState.launchSequence]). [SPREAD]: black spreads from the name over the menu, input
 * waits; the row opens at its end. [HANDED_OFF]: the game (or an app) has the screen; the overlay stays black under it
 * until the app is back in front. [RETURNING]: back in front (or an in-app tool opened under it): the overlay fades out.
 * [RETREAT]: refused before the game screen: the black retreats into the name, then any Problem shows.
 */
enum class LaunchPhase { SPREAD, HANDED_OFF, RETURNING, RETREAT }

/**
 * A launch from a Minimal page: its [id] (one per A), the [page] and row [index] it came from (as [MinimalOpening]'s), its
 * [phase], when it started ([startedAtMs], the state's clock) and whether it opens a [tool] rather than a game.
 */
data class LaunchSequence(val id: Long, val page: Any, val index: Int, val phase: LaunchPhase, val startedAtMs: Long, val tool: Boolean)
