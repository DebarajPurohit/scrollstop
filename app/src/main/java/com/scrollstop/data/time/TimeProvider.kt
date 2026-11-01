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
    fun getNextMidnightMillis(): Long
}

/**
 * Standard implementation using the device's system clock and local timezone.
 */
class SystemTimeProvider(
    private val timeZoneSupplier: () -> TimeZone = { TimeZone.getDefault() }
) : TimeProvider {

    constructor(timeZone: TimeZone) : this({ timeZone })

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun getStartOfDayMillis(): Long {
        val calendar = Calendar.getInstance(timeZoneSupplier()).apply {
            timeInMillis = currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    override fun getNextMidnightMillis(): Long {
        val calendar = Calendar.getInstance(timeZoneSupplier()).apply {
            timeInMillis = currentTimeMillis()
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}
