package com.succh.unifeed.data.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshIntervalTest {

    @Test
    fun periodicSlotsAreAtLeast15Minutes() {
        RefreshInterval.entries.filter { it.isPeriodic }.forEach { slot ->
            assertTrue(slot.minutes >= 15L)
        }
        assertEquals(15L, RefreshInterval.MINUTES_15.minutes)
        assertEquals(30L, RefreshInterval.MINUTES_30.minutes)
        assertEquals(60L, RefreshInterval.HOUR_1.minutes)
        assertEquals(0L, RefreshInterval.MANUAL.minutes)
        assertFalse(RefreshInterval.MANUAL.isPeriodic)
    }

    @Test
    fun migrateDisabledOrNonPositiveHoursToManual() {
        assertEquals(RefreshInterval.MANUAL, RefreshInterval.fromLegacyHours(false, 6))
        assertEquals(RefreshInterval.MANUAL, RefreshInterval.fromLegacyHours(false, 2))
        assertEquals(RefreshInterval.MANUAL, RefreshInterval.fromLegacyHours(true, 0))
        assertEquals(RefreshInterval.MANUAL, RefreshInterval.fromLegacyHours(true, -3))
    }

    @Test
    fun migrateLegacyHourSlotsToOneHour() {
        listOf(1L, 2L, 6L, 12L, 24L).forEach { hours ->
            assertEquals(
                "hours=$hours",
                RefreshInterval.HOUR_1,
                RefreshInterval.fromLegacyHours(true, hours)
            )
        }
    }

    @Test
    fun fromNameFallsBackToManual() {
        assertEquals(RefreshInterval.MINUTES_15, RefreshInterval.fromName("MINUTES_15"))
        assertEquals(RefreshInterval.MINUTES_30, RefreshInterval.fromName("MINUTES_30"))
        assertEquals(RefreshInterval.HOUR_1, RefreshInterval.fromName("HOUR_1"))
        assertEquals(RefreshInterval.MANUAL, RefreshInterval.fromName("MANUAL"))
        assertEquals(RefreshInterval.MANUAL, RefreshInterval.fromName(null))
        assertEquals(RefreshInterval.MANUAL, RefreshInterval.fromName("NOPE"))
    }
}
