package com.soul.neurokaraoke.ui.tv

/**
 * Pure, unit-testable logic for the "tap the version 7 times in 5 seconds" developer-options
 * unlock (the classic Android build-number easter egg, adapted for the TV remote). Kept out of
 * the composable so the sliding-window counting is covered by fast JVM tests, mirroring
 * [TvSettingsLogic] / [TvQueryEditor].
 */
object TvDevUnlock {
    const val REQUIRED_TAPS = 7
    const val WINDOW_MS = 5000L

    data class Result(
        /** The retained tap timestamps (only those still inside the window). */
        val taps: List<Long>,
        /** True once [REQUIRED_TAPS] taps have landed within [WINDOW_MS]. */
        val unlocked: Boolean,
        /** Taps still needed to unlock (0 once unlocked). */
        val remaining: Int
    )

    /**
     * Register a tap at [now]. Drops any earlier taps that have aged out of the window, then
     * reports whether the unlock threshold is met and how many taps remain.
     */
    fun registerTap(previous: List<Long>, now: Long): Result {
        val recent = (previous + now).filter { now - it < WINDOW_MS }
        val unlocked = recent.size >= REQUIRED_TAPS
        val remaining = (REQUIRED_TAPS - recent.size).coerceAtLeast(0)
        return Result(recent, unlocked, remaining)
    }
}
