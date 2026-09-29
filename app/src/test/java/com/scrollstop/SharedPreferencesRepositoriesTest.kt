package com.scrollstop

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.scrollstop.data.repository.SharedPreferencesDailyLimitRepository
import com.scrollstop.data.repository.SharedPreferencesSelectedAppsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesRepositoriesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `test persistent selected apps repository reads and writes set correctly`() {
        val repo = SharedPreferencesSelectedAppsRepository(context, prefName = "test_selected_apps")

        assertTrue(repo.getSelectedPackages().isEmpty())

        repo.setSelectedPackages(setOf("com.google.android.youtube", "com.instagram.android"))

        assertEquals(2, repo.getSelectedPackages().size)
        assertTrue(repo.isSelected("com.google.android.youtube"))
        assertTrue(repo.isSelected("com.instagram.android"))

        repo.toggleAppSelection("com.instagram.android")
        assertFalse(repo.isSelected("com.instagram.android"))
        assertEquals(1, repo.getSelectedPackages().size)

        // Create new instance reading same prefs
        val reloadedRepo = SharedPreferencesSelectedAppsRepository(context, prefName = "test_selected_apps")
        assertTrue(reloadedRepo.isSelected("com.google.android.youtube"))
        assertFalse(reloadedRepo.isSelected("com.instagram.android"))
    }

    @Test
    fun `test persistent daily limit repository returns default limit and custom limits`() {
        val repo = SharedPreferencesDailyLimitRepository(context, defaultLimitMs = 120_000L, prefName = "test_daily_limits")

        // Unset app gets default limit
        assertEquals(120_000L, repo.getLimitForPackage("com.google.android.youtube"))

        // Set custom limit
        repo.setLimitForPackage("com.google.android.youtube", 300_000L)
        assertEquals(300_000L, repo.getLimitForPackage("com.google.android.youtube"))

        // Reload from prefs
        val reloadedRepo = SharedPreferencesDailyLimitRepository(context, defaultLimitMs = 120_000L, prefName = "test_daily_limits")
        assertEquals(300_000L, reloadedRepo.getLimitForPackage("com.google.android.youtube"))
    }
}
