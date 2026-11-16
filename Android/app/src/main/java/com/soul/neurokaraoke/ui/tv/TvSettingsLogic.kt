package com.soul.neurokaraoke.ui.tv

/**
 * Pure, unit-testable helpers for the TV settings crossfade row. Kept separate from the
 * composable (mirrors [TvQueryEditor]) so the clamping/formatting is covered by fast JVM tests.
 */
object TvSettingsLogic {
    const val CROSSFADE_MIN: Int = 0
    const val CROSSFADE_MAX: Int = 12

    /** Next crossfade value after a D-pad Left (delta -1) / Right (delta +1), clamped to bounds. */
    fun stepCrossfade(current: Int, delta: Int): Int =
        (current + delta).coerceIn(CROSSFADE_MIN, CROSSFADE_MAX)

    /** Right-hand value label for the crossfade row. */
    fun crossfadeLabel(seconds: Int): String =
        if (seconds <= 0) "Off" else "${seconds}s"
}
