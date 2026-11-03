package com.succh.unifeed.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleCleanupTest {

    @Test
    fun cutoffIsNullWhenNever() {
        assertNull(ArticleCleanup.cutoffMillis(ArticleRetention.NEVER, 1_700_000_000_000L))
    }

    @Test
    fun cutoffSubtractsRetentionDays() {
        val now = 1_700_000_000_000L
        assertEquals(now - 30L * ArticleCleanup.DAY_MS, ArticleCleanup.cutoffMillis(ArticleRetention.DAYS_30, now))
        assertEquals(now - 90L * ArticleCleanup.DAY_MS, ArticleCleanup.cutoffMillis(ArticleRetention.DAYS_90, now))
        assertEquals(now - 180L * ArticleCleanup.DAY_MS, ArticleCleanup.cutoffMillis(ArticleRetention.DAYS_180, now))
    }

    @Test
    fun unreadIsAlwaysKept() {
        assertFalse(
            ArticleCleanup.isEligible(
                isRead = false,
                isStarred = false,
                isReadLater = false,
                publishedAt = 1L,
                fetchedAt = 1L,
                cutoffMillis = 100L
            )
        )
    }

    @Test
    fun starredIsKeptEvenIfReadAndOld() {
        assertFalse(
            ArticleCleanup.isEligible(
                isRead = true,
                isStarred = true,
                isReadLater = false,
                publishedAt = 1L,
                fetchedAt = 1L,
                cutoffMillis = 100L
            )
        )
    }

    @Test
    fun readLaterIsKeptEvenIfReadAndOld() {
        assertFalse(
            ArticleCleanup.isEligible(
                isRead = true,
                isStarred = false,
                isReadLater = true,
                publishedAt = 1L,
                fetchedAt = 1L,
                cutoffMillis = 100L
            )
        )
    }

    @Test
    fun starredAndReadLaterAreKept() {
        assertFalse(
            ArticleCleanup.isEligible(
                isRead = true,
                isStarred = true,
                isReadLater = true,
                publishedAt = 1L,
                fetchedAt = 1L,
                cutoffMillis = 100L
            )
        )
    }

    @Test
    fun oldReadUnsavedIsEligible() {
        assertTrue(
            ArticleCleanup.isEligible(
                isRead = true,
                isStarred = false,
                isReadLater = false,
                publishedAt = 10L,
                fetchedAt = 10L,
                cutoffMillis = 100L
            )
        )
    }

    @Test
    fun boundaryPublishedAtIsKept() {
        assertFalse(
            ArticleCleanup.isEligible(
                isRead = true,
                isStarred = false,
                isReadLater = false,
                publishedAt = 100L,
                fetchedAt = 1L,
                cutoffMillis = 100L
            )
        )
    }

    @Test
    fun missingPublishedAtFallsBackToFetchedAt() {
        assertTrue(
            ArticleCleanup.isEligible(
                isRead = true,
                isStarred = false,
                isReadLater = false,
                publishedAt = 0L,
                fetchedAt = 10L,
                cutoffMillis = 100L
            )
        )
        assertFalse(
            ArticleCleanup.isEligible(
                isRead = true,
                isStarred = false,
                isReadLater = false,
                publishedAt = 0L,
                fetchedAt = 100L,
                cutoffMillis = 100L
            )
        )
    }

    @Test
    fun fromNameFallsBackToNever() {
        assertEquals(ArticleRetention.DAYS_30, ArticleRetention.fromName("DAYS_30"))
        assertEquals(ArticleRetention.DAYS_90, ArticleRetention.fromName("DAYS_90"))
        assertEquals(ArticleRetention.DAYS_180, ArticleRetention.fromName("DAYS_180"))
        assertEquals(ArticleRetention.NEVER, ArticleRetention.fromName("NEVER"))
        assertEquals(ArticleRetention.NEVER, ArticleRetention.fromName(null))
        assertEquals(ArticleRetention.NEVER, ArticleRetention.fromName("NOPE"))
    }
}
