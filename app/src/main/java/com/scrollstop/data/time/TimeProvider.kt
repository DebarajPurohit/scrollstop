package com.scrollstop.data.time

import java.util.Calendar
import java.util.TimeZone

/**
 * Interface providing current time and midnight start-of-today timestamps.
 * Abstracted to support deterministic unit testing.
 */
interface TimeProvider {
    fun currentTimeMillis(): Long
    fun getStartOfDayMillis(): Long
}

/**
 * Standard implementation using the device's system clock and local timezone.
 */
class SystemTimeProvider(
    private val timeZone: TimeZone = TimeZone.getDefault()
) : TimeProvider {

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun getStartOfDayMillis(): Long {
        val calendar = Calendar.getInstance(timeZone).apply {
            timeInMillis = currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}
