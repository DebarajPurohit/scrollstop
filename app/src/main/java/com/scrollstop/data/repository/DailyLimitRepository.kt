package com.scrollstop.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository interface for managing user-configured daily limits for restricted apps.
 */
interface DailyLimitRepository {
    fun getLimitForPackage(packageName: String): Long
    fun setLimitForPackage(packageName: String, limitMs: Long)
    fun getAllLimits(): Map<String, Long>
    val limitsFlow: StateFlow<Map<String, Long>>
}

/**
 * In-memory implementation of [DailyLimitRepository] with default 2-minute POC limit.
 */
class InMemoryDailyLimitRepository(
    private val defaultLimitMs: Long = DEFAULT_POC_LIMIT_MS
) : DailyLimitRepository {

    companion object {
        /** Default temporary 2-minute limit for POC-03 (120,000 milliseconds). */
        const val DEFAULT_POC_LIMIT_MS = 2 * 60 * 1000L
    }

    private val _limits = MutableStateFlow<Map<String, Long>>(emptyMap())
    override val limitsFlow: StateFlow<Map<String, Long>> = _limits.asStateFlow()

    override fun getLimitForPackage(packageName: String): Long {
        return _limits.value[packageName] ?: defaultLimitMs
    }

    override fun setLimitForPackage(packageName: String, limitMs: Long) {
        val current = _limits.value.toMutableMap()
        current[packageName] = limitMs
        _limits.value = current
    }

    override fun getAllLimits(): Map<String, Long> {
        return _limits.value
    }
}
