package com.scrollstop.data.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SystemTimeProviderTest {

    @Test
    fun `getStartOfDayMillis calculates 00 00 00 in local timezone`() {
        val timeZone = TimeZone.getTimeZone("America/New_York")
        val provider = SystemTimeProvider(timeZone)

        val startOfDay = provider.getStartOfDayMillis()
        val now = provider.currentTimeMillis()

        assertTrue("Start of day must be less than or equal to current time", startOfDay <= now)

        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = startOfDay
        }

        assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calendar.get(Calendar.MINUTE))
        assertEquals(0, calendar.get(Calendar.SECOND))
        assertEquals(0, calendar.get(Calendar.MILLISECOND))
    }

    @Test
    fun `getStartOfDayMillis differs appropriately across timezones`() {
        val nyZone = TimeZone.getTimeZone("America/New_York")
        val tokZone = TimeZone.getTimeZone("Asia/Tokyo")

        val nyProvider = SystemTimeProvider(nyZone)
        val tokProvider = SystemTimeProvider(tokZone)

        val nyStart = nyProvider.getStartOfDayMillis()
        val tokStart = tokProvider.getStartOfDayMillis()

        // Since NY and Tokyo have different time offsets, start of local day timestamps differ
        assertTrue("Tokyo start of day differs from New York start of day", nyStart != tokStart)
    }
}
