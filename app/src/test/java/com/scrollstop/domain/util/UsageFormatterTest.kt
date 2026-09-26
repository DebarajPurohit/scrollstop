package com.scrollstop.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageFormatterTest {

    @Test
    fun `formatDuration returns 0 min when duration is zero`() {
        val result = UsageFormatter.formatDuration(0L)
        assertEquals("0 min", result)
    }

    @Test
    fun `formatDuration returns 0 min when duration is negative`() {
        val result = UsageFormatter.formatDuration(-1000L)
        assertEquals("0 min", result)
    }

    @Test
    fun `formatDuration formats seconds correctly`() {
        val result = UsageFormatter.formatDuration(45_000L)
        assertEquals("45 sec", result)
    }

    @Test
    fun `formatDuration formats minutes only correctly`() {
        val result = UsageFormatter.formatDuration(7 * 60 * 1000L)
        assertEquals("7 min", result)
    }

    @Test
    fun `formatDuration formats minutes and seconds correctly`() {
        // 8 min 12 sec
        val millis8m12s = (8 * 60 + 12) * 1000L
        assertEquals("8 min 12 sec", UsageFormatter.formatDuration(millis8m12s))

        // 23 min 41 sec
        val millis23m41s = (23 * 60 + 41) * 1000L
        assertEquals("23 min 41 sec", UsageFormatter.formatDuration(millis23m41s))

        // 27 min 8 sec (formatted with leading zero for seconds)
        val millis27m08s = (27 * 60 + 8) * 1000L
        assertEquals("27 min 08 sec", UsageFormatter.formatDuration(millis27m08s))
    }

    @Test
    fun `formatDuration formats hours correctly`() {
        // 1 hour
        val millis1h = 3600 * 1000L
        assertEquals("1 hr", UsageFormatter.formatDuration(millis1h))

        // 1 hour 15 minutes
        val millis1h15m = (3600 + 15 * 60) * 1000L
        assertEquals("1 hr 15 min", UsageFormatter.formatDuration(millis1h15m))

        // 2 hours 5 minutes 3 seconds
        val millis2h5m3s = (2 * 3600 + 5 * 60 + 3) * 1000L
        assertEquals("2 hr 5 min 03 sec", UsageFormatter.formatDuration(millis2h5m3s))
    }
}
