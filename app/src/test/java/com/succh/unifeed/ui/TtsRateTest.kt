package com.succh.unifeed.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsRateTest {

    @Test
    fun labelsMatchRequiredGears() {
        assertEquals(
            listOf("0.75x", "1x", "1.25x", "1.5x"),
            TtsRate.entries.map { it.label }
        )
        assertEquals(
            listOf(0.75f, 1.0f, 1.25f, 1.5f),
            TtsRate.entries.map { it.multiplier }
        )
    }

    @Test
    fun fromNameFallsBackToNormal() {
        assertEquals(TtsRate.FAST, TtsRate.fromName("FAST"))
        assertEquals(TtsRate.NORMAL, TtsRate.fromName(null))
        assertEquals(TtsRate.NORMAL, TtsRate.fromName("nope"))
    }
}
