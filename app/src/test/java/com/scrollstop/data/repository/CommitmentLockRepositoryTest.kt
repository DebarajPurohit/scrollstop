package com.scrollstop.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.scrollstop.data.time.TimeProvider
import com.scrollstop.domain.model.CommitmentLockState
import com.scrollstop.domain.rules.CommitmentRuleEngine
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
class CommitmentLockRepositoryTest {

    private lateinit var context: Context
    private lateinit var selectedAppsRepository: InMemorySelectedAppsRepository
    private lateinit var dailyLimitRepository: InMemoryDailyLimitRepository
    private lateinit var fakeTimeProvider: FakeTestTimeProvider
    private lateinit var commitmentRuleEngine: CommitmentRuleEngine

    private class FakeTestTimeProvider(
        var currentTime: Long = 1_000_000L,
        var nextMidnight: Long = 2_000_000L
    ) : TimeProvider {
        override fun currentTimeMillis(): Long = currentTime
        override fun getStartOfDayMillis(): Long = currentTime - (currentTime % 86_400_000L)
        override fun getNextMidnightMillis(): Long = nextMidnight
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        selectedAppsRepository = InMemorySelectedAppsRepository()
        dailyLimitRepository = InMemoryDailyLimitRepository()
        fakeTimeProvider = FakeTestTimeProvider(currentTime = 1_000_000L, nextMidnight = 2_000_000L)
        commitmentRuleEngine = CommitmentRuleEngine()
    }

    private fun createRepository(prefName: String = "test_commitment_prefs"): SharedPreferencesCommitmentLockRepository {
        return SharedPreferencesCommitmentLockRepository(
            context = context,
            selectedAppsRepository = selectedAppsRepository,
            dailyLimitRepository = dailyLimitRepository,
            commitmentRuleEngine = commitmentRuleEngine,
            timeProvider = fakeTimeProvider,
            prefName = prefName
        )
    }

    @Test
    fun `enableCommitmentLock requires affirmative consent`() {
        val repo = createRepository("pref_consent_test")

        assertFalse(repo.commitmentState.value.isLocked)

        // Without affirmative consent: fails
        val resultNoConsent = repo.enableCommitmentLock(consentConfirmed = false)
        assertFalse(resultNoConsent)
        assertFalse(repo.commitmentState.value.isLocked)

        // With affirmative consent: succeeds
        val resultWithConsent = repo.enableCommitmentLock(consentConfirmed = true)
        assertTrue(resultWithConsent)
        assertTrue(repo.commitmentState.value.isLocked)
        assertEquals(1_000_000L, repo.commitmentState.value.lockedAtMillis)
    }

    @Test
    fun `commitment state persists across repository recreation (process death simulation)`() {
        val prefName = "pref_persistence_test"
        val repo1 = createRepository(prefName)
        repo1.enableCommitmentLock(consentConfirmed = true)
        repo1.stageAppRemoval("com.instagram.android")
        repo1.stageLimitIncrease("com.google.android.youtube", 180_000L)

        // Recreate repository from same SharedPreferences
        val repo2 = createRepository(prefName)
        val state = repo2.commitmentState.value

        assertTrue(state.isLocked)
        assertEquals(1_000_000L, state.lockedAtMillis)
        assertTrue(state.pendingRemovedPackages.contains("com.instagram.android"))
        assertEquals(180_000L, state.pendingIncreasedLimits["com.google.android.youtube"])
    }

    @Test
    fun `stage and cancel staged app removals and limit increases`() {
        val repo = createRepository("pref_staging_test")
        repo.enableCommitmentLock(consentConfirmed = true)

        repo.stageAppRemoval("com.twitter.android")
        assertTrue(repo.commitmentState.value.pendingRemovedPackages.contains("com.twitter.android"))

        repo.cancelStagedAppRemoval("com.twitter.android")
        assertFalse(repo.commitmentState.value.pendingRemovedPackages.contains("com.twitter.android"))

        repo.stageLimitIncrease("com.twitter.android", 300_000L)
        assertEquals(300_000L, repo.commitmentState.value.pendingIncreasedLimits["com.twitter.android"])

        repo.cancelStagedLimitIncrease("com.twitter.android")
        assertFalse(repo.commitmentState.value.pendingIncreasedLimits.containsKey("com.twitter.android"))
    }

    @Test
    fun `request unlock sets pending flag with target midnight and cancel reverts it`() {
        val repo = createRepository("pref_unlock_request_test")
        repo.enableCommitmentLock(consentConfirmed = true)

        assertTrue(repo.requestUnlock())
        val state = repo.commitmentState.value
        assertTrue(state.pendingUnlock)
        assertEquals(2_000_000L, state.pendingUnlockTargetMidnightMillis)

        assertTrue(repo.cancelUnlockRequest())
        assertFalse(repo.commitmentState.value.pendingUnlock)
        assertEquals(0L, repo.commitmentState.value.pendingUnlockTargetMidnightMillis)
    }

    @Test
    fun `midnight transition applies pending app removals and limit increases`() {
        val repo = createRepository("pref_midnight_apply_test")
        selectedAppsRepository.setSelectedPackages(setOf("com.google.android.youtube", "com.instagram.android"))
        dailyLimitRepository.setLimitForPackage("com.google.android.youtube", 120_000L)

        repo.enableCommitmentLock(consentConfirmed = true)
        repo.stageAppRemoval("com.instagram.android")
        repo.stageLimitIncrease("com.google.android.youtube", 240_000L)

        // Advance time past midnight
        fakeTimeProvider.currentTime = 2_005_000L
        repo.checkAndApplyMidnightTransitions()

        // Verify changes applied to actual repositories
        assertFalse(selectedAppsRepository.isSelected("com.instagram.android"))
        assertTrue(selectedAppsRepository.isSelected("com.google.android.youtube"))
        assertEquals(240_000L, dailyLimitRepository.getLimitForPackage("com.google.android.youtube"))

        // Commitment lock itself remains active
        assertTrue(repo.commitmentState.value.isLocked)
        assertTrue(repo.commitmentState.value.pendingRemovedPackages.isEmpty())
        assertTrue(repo.commitmentState.value.pendingIncreasedLimits.isEmpty())
    }

    @Test
    fun `midnight transition unlocks commitment when pending unlock target has passed`() {
        val repo = createRepository("pref_midnight_unlock_test")
        repo.enableCommitmentLock(consentConfirmed = true)
        repo.requestUnlock()

        // Advance time past midnight
        fakeTimeProvider.currentTime = 2_005_000L
        repo.checkAndApplyMidnightTransitions()

        // Now unlocked
        assertFalse(repo.commitmentState.value.isLocked)
        assertFalse(repo.commitmentState.value.pendingUnlock)
    }

    @Test
    fun `debugClearCommitmentState restores normal unlocked state without waiting for midnight`() {
        val prefName = "pref_debug_clear_test"
        val repo = createRepository(prefName)
        selectedAppsRepository.setSelectedPackages(setOf("com.google.android.youtube"))
        dailyLimitRepository.setLimitForPackage("com.google.android.youtube", 120_000L)

        repo.enableCommitmentLock(consentConfirmed = true)
        repo.stageAppRemoval("com.google.android.youtube")
        repo.stageLimitIncrease("com.google.android.youtube", 300_000L)
        repo.requestUnlock()

        assertTrue(repo.commitmentState.value.isLocked)

        // Clear test commitment
        repo.debugClearCommitmentState()

        val clearedState = repo.commitmentState.value
        assertFalse(clearedState.isLocked)
        assertFalse(clearedState.pendingUnlock)
        assertTrue(clearedState.pendingRemovedPackages.isEmpty())
        assertTrue(clearedState.pendingIncreasedLimits.isEmpty())

        // App selection and limits remain preserved
        assertTrue(selectedAppsRepository.isSelected("com.google.android.youtube"))
        assertEquals(120_000L, dailyLimitRepository.getLimitForPackage("com.google.android.youtube"))

        // Verify persisted state across reload
        val reloadedRepo = createRepository(prefName)
        assertFalse(reloadedRepo.commitmentState.value.isLocked)
    }
}
