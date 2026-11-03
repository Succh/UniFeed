package com.succh.unifeed.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageCacheLimitTest {

    @Test
    fun slotBytesMatchSpec() {
        assertEquals(100L * 1024 * 1024, ImageCacheLimit.MB_100.maxBytes)
        assertEquals(500L * 1024 * 1024, ImageCacheLimit.MB_500.maxBytes)
        assertEquals(1024L * 1024 * 1024, ImageCacheLimit.GB_1.maxBytes)
        assertNull(ImageCacheLimit.UNLIMITED.maxBytes)
    }

    @Test
    fun fromNameFallsBackTo500Mb() {
        assertEquals(ImageCacheLimit.MB_100, ImageCacheLimit.fromName("MB_100"))
        assertEquals(ImageCacheLimit.MB_500, ImageCacheLimit.fromName("MB_500"))
        assertEquals(ImageCacheLimit.GB_1, ImageCacheLimit.fromName("GB_1"))
        assertEquals(ImageCacheLimit.UNLIMITED, ImageCacheLimit.fromName("UNLIMITED"))
        assertEquals(ImageCacheLimit.MB_500, ImageCacheLimit.fromName(null))
        assertEquals(ImageCacheLimit.MB_500, ImageCacheLimit.fromName("NOPE"))
    }

    @Test
    fun formatByteSize() {
        assertEquals("0 B", ByteSize.format(0))
        assertEquals("512 B", ByteSize.format(512))
        assertEquals("1.0 KB", ByteSize.format(1024))
        assertEquals("1.0 MB", ByteSize.format(1024L * 1024L))
        assertEquals("1.00 GB", ByteSize.format(1024L * 1024L * 1024L))
    }
}
