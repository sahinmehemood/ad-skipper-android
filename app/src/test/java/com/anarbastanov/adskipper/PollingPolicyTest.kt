package com.anarbastanov.adskipper

import org.junit.Assert.assertEquals
import org.junit.Test

class PollingPolicyTest {
    @Test
    fun menuUsesFastestPollingInterval() {
        assertEquals(
            PollingPolicy.MENU_INTERVAL_MS,
            PollingPolicy.nextDelay(
                menuPending = true,
                verificationPending = false,
                youtubeForeground = true
            )
        )
    }

    @Test
    fun verificationUsesFastButLighterPollingInterval() {
        assertEquals(
            PollingPolicy.VERIFICATION_INTERVAL_MS,
            PollingPolicy.nextDelay(
                menuPending = false,
                verificationPending = true,
                youtubeForeground = true
            )
        )
    }

    @Test
    fun menuTakesPrecedenceOverVerification() {
        assertEquals(
            PollingPolicy.MENU_INTERVAL_MS,
            PollingPolicy.nextDelay(
                menuPending = true,
                verificationPending = true,
                youtubeForeground = false
            )
        )
    }

    @Test
    fun foregroundAndIdleUseLowFrequencyFallbacks() {
        assertEquals(
            PollingPolicy.FOREGROUND_INTERVAL_MS,
            PollingPolicy.nextDelay(
                menuPending = false,
                verificationPending = false,
                youtubeForeground = true
            )
        )
        assertEquals(
            PollingPolicy.IDLE_INTERVAL_MS,
            PollingPolicy.nextDelay(
                menuPending = false,
                verificationPending = false,
                youtubeForeground = false
            )
        )
    }
}
