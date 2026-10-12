package com.scrollstop.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.scrollstop.data.time.TimeProvider
import com.scrollstop.domain.model.EnforcementTrigger
import com.scrollstop.domain.rules.LimitState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MidnightTriggerExpirationTest {

    private lateinit var context: Context
    private val prefName = "test_enforcement_midnight_prefs"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun todayTrigger_persistsAndLoadsCleanly() {
        val fakeTimeProvider = FakeTimeProvider(
            currentTime = 1728500000000L, // Today 2:00 PM
            startOfDay = 1728450000000L   // Today 00:00:00
        )

        val repo = SharedPreferencesEnforcementTriggerRepository(
            context = context,
            prefName = prefName,
            timeProvider = fakeTimeProvider
        )

        val trigger = EnforcementTrigger(
            packageName = "com.amazon.mShop.android.shopping",
            reason = LimitState.LIMIT_REACHED,
            detectedAtElapsedRealtimeMs = 50000L
        )

        repo.emitTrigger(trigger)

        assertEquals("com.amazon.mShop.android.shopping", repo.latestTrigger.value?.packageName)

        // Recreate repo to simulate app restart on the same day
        val reloadedRepo = SharedPreferencesEnforcementTriggerRepository(
            context = context,
            prefName = prefName,
            timeProvider = fakeTimeProvider
        )

        assertNotNull(reloadedRepo.latestTrigger.value)
        assertEquals("com.amazon.mShop.android.shopping", reloadedRepo.latestTrigger.value?.packageName)
    }

    @Test
    fun yesterdayTrigger_automaticallyExpiresOnReloadAcrossMidnight() {
        val yesterdayTimeProvider = FakeTimeProvider(
            currentTime = 1728440000000L, // Yesterday 11:00 PM
            startOfDay = 1728363600000L   // Yesterday 00:00:00
        )

        val repoYesterday = SharedPreferencesEnforcementTriggerRepository(
            context = context,
            prefName = prefName,
            timeProvider = yesterdayTimeProvider
        )

        val trigger = EnforcementTrigger(
            packageName = "com.google.android.youtube",
            reason = LimitState.LIMIT_REACHED,
            detectedAtElapsedRealtimeMs = 45000L
        )
        repoYesterday.emitTrigger(trigger)

        // Midnight passes -> New day starts
        val todayTimeProvider = FakeTimeProvider(
            currentTime = 1728460000000L, // Today 3:00 AM
            startOfDay = 1728450000000L   // Today 00:00:00
        )

        // App starts on the new day
        val repoToday = SharedPreferencesEnforcementTriggerRepository(
            context = context,
            prefName = prefName,
            timeProvider = todayTimeProvider
        )

        // Stale yesterday trigger should be expired and null
        assertNull(repoToday.latestTrigger.value)
    }

    @Test
    fun checkAndExpireStaleTriggers_clearsTriggerWhenMidnightBoundaryCrosses() {
        val initialTimeProvider = MutableFakeTimeProvider(
            currentTime = 1728445000000L, // Yesterday 11:30 PM
            startOfDay = 1728363600000L   // Yesterday 00:00:00
        )

        val repo = SharedPreferencesEnforcementTriggerRepository(
            context = context,
            prefName = prefName,
            timeProvider = initialTimeProvider
        )

        val trigger = EnforcementTrigger(
            packageName = "com.instagram.android",
            reason = LimitState.LIMIT_REACHED,
            detectedAtElapsedRealtimeMs = 60000L
        )
        repo.emitTrigger(trigger)
        assertNotNull(repo.latestTrigger.value)

        // Midnight arrives while app is in background
        initialTimeProvider.currentTime = 1728455000000L // Today 1:00 AM
        initialTimeProvider.startOfDay = 1728450000000L   // Today 00:00:00

        // Lifecycle resume calls checkAndExpireStaleTriggers()
        repo.checkAndExpireStaleTriggers()

        assertNull(repo.latestTrigger.value)
    }

    private class FakeTimeProvider(
        private val currentTime: Long,
        private val startOfDay: Long,
        private val nextMidnight: Long = startOfDay + 86_400_000L
    ) : TimeProvider {
        override fun currentTimeMillis(): Long = currentTime
        override fun getStartOfDayMillis(): Long = startOfDay
        override fun getNextMidnightMillis(): Long = nextMidnight
    }

    private class MutableFakeTimeProvider(
        var currentTime: Long,
        var startOfDay: Long,
        var nextMidnight: Long = startOfDay + 86_400_000L
    ) : TimeProvider {
        override fun currentTimeMillis(): Long = currentTime
        override fun getStartOfDayMillis(): Long = startOfDay
        override fun getNextMidnightMillis(): Long = nextMidnight
    }
}
