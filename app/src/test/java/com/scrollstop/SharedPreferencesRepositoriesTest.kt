package com.scrollstop

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.scrollstop.data.repository.SharedPreferencesDailyLimitRepository
import com.scrollstop.data.repository.SharedPreferencesEnforcementTriggerRepository
import com.scrollstop.data.repository.SharedPreferencesSelectedAppsRepository
import com.scrollstop.domain.model.EnforcementTrigger
import com.scrollstop.domain.rules.LimitState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun `test persistent enforcement trigger repository emits, persists and clears trigger`() {
        val repo = SharedPreferencesEnforcementTriggerRepository(context, prefName = "test_trigger_prefs")

        assertNull(repo.latestTrigger.value)

        val trigger = EnforcementTrigger(
            packageName = "com.google.android.youtube",
            reason = LimitState.LIMIT_REACHED,
            detectedAtElapsedRealtimeMs = 12345L
        )

        repo.emitTrigger(trigger)

        val current = repo.latestTrigger.value
        assertNotNull(current)
        assertEquals("com.google.android.youtube", current?.packageName)
        assertEquals(LimitState.LIMIT_REACHED, current?.reason)
        assertEquals(12345L, current?.detectedAtElapsedRealtimeMs)

        // Reload from prefs (simulating process restart)
        val reloadedRepo = SharedPreferencesEnforcementTriggerRepository(context, prefName = "test_trigger_prefs")
        val restored = reloadedRepo.latestTrigger.value
        assertNotNull(restored)
        assertEquals("com.google.android.youtube", restored?.packageName)
        assertEquals(LimitState.LIMIT_REACHED, restored?.reason)
        assertEquals(12345L, restored?.detectedAtElapsedRealtimeMs)

        // Clear trigger
        reloadedRepo.clearLatestTrigger()
        assertNull(reloadedRepo.latestTrigger.value)

        val clearedReloaded = SharedPreferencesEnforcementTriggerRepository(context, prefName = "test_trigger_prefs")
        assertNull(clearedReloaded.latestTrigger.value)
    }
}
