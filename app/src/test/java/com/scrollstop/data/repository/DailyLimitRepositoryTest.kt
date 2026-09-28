package com.scrollstop.data.repository

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DailyLimitRepositoryTest {

    private lateinit var repository: InMemoryDailyLimitRepository

    @Before
    fun setUp() {
        repository = InMemoryDailyLimitRepository()
    }

    @Test
    fun `getLimitForPackage returns default 2-minute POC limit when unconfigured`() {
        val limit = repository.getLimitForPackage("com.google.android.youtube")
        assertEquals(120_000L, limit)
    }

    @Test
    fun `setLimitForPackage updates limit correctly`() {
        repository.setLimitForPackage("com.instagram.android", 300_000L)
        val limit = repository.getLimitForPackage("com.instagram.android")
        assertEquals(300_000L, limit)
    }

    @Test
    fun `getAllLimits returns set limits`() {
        repository.setLimitForPackage("com.google.android.youtube", 120_000L)
        repository.setLimitForPackage("com.instagram.android", 180_000L)

        val limits = repository.getAllLimits()
        assertEquals(2, limits.size)
        assertEquals(120_000L, limits["com.google.android.youtube"])
        assertEquals(180_000L, limits["com.instagram.android"])
    }
}
