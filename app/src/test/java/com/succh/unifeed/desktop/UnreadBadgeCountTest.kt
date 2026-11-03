package com.succh.unifeed.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class UnreadBadgeCountTest {

    @Test
    fun disabledAlwaysZero() {
        assertEquals(0, UnreadBadgeCount.display(12, enabled = false))
        assertEquals(0, UnreadBadgeCount.display(0, enabled = false))
    }

    @Test
    fun enabledPassesUnreadCount() {
        assertEquals(12, UnreadBadgeCount.display(12, enabled = true))
        assertEquals(0, UnreadBadgeCount.display(0, enabled = true))
    }

    @Test
    fun negativeTreatedAsZero() {
        assertEquals(0, UnreadBadgeCount.display(-1, enabled = true))
    }
}
