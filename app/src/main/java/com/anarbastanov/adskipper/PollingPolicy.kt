package com.anarbastanov.adskipper

/**
 * Adaptive polling cadence for the accessibility monitor.
 *
 * Keep the menu transition responsive without running the most frequent
 * accessibility-tree checks during the less time-sensitive verification phase.
 */
internal object PollingPolicy {
    const val MENU_INTERVAL_MS = 75L
    const val VERIFICATION_INTERVAL_MS = 125L
    const val FOREGROUND_INTERVAL_MS = 500L
    const val IDLE_INTERVAL_MS = 1_500L

    fun nextDelay(
        menuPending: Boolean,
        verificationPending: Boolean,
        youtubeForeground: Boolean
    ): Long = when {
        menuPending -> MENU_INTERVAL_MS
        verificationPending -> VERIFICATION_INTERVAL_MS
        youtubeForeground -> FOREGROUND_INTERVAL_MS
        else -> IDLE_INTERVAL_MS
    }
}
