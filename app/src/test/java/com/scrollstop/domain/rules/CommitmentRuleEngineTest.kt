package com.scrollstop.domain.rules

import com.scrollstop.domain.model.CommitmentLockState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommitmentRuleEngineTest {

    private lateinit var ruleEngine: CommitmentRuleEngine

    @Before
    fun setUp() {
        ruleEngine = CommitmentRuleEngine()
    }

    @Test
    fun `when not locked - all limit modifications are allowed immediately`() {
        val state = CommitmentLockState(isLocked = false)

        assertTrue(ruleEngine.canChangeLimitImmediately(currentLimitMs = 60_000L, newLimitMs = 30_000L, state = state))
        assertTrue(ruleEngine.canChangeLimitImmediately(currentLimitMs = 60_000L, newLimitMs = 120_000L, state = state))
        assertTrue(ruleEngine.canChangeLimitImmediately(currentLimitMs = 60_000L, newLimitMs = 60_000L, state = state))
    }

    @Test
    fun `when locked - tightening or matching limit is allowed immediately`() {
        val state = CommitmentLockState(isLocked = true)

        // Decreasing limit (tightening) is allowed immediately
        assertTrue(ruleEngine.canChangeLimitImmediately(currentLimitMs = 60_000L, newLimitMs = 30_000L, state = state))
        assertTrue(ruleEngine.canChangeLimitImmediately(currentLimitMs = 120_000L, newLimitMs = 60_000L, state = state))

        // Same limit is allowed immediately
        assertTrue(ruleEngine.canChangeLimitImmediately(currentLimitMs = 60_000L, newLimitMs = 60_000L, state = state))
    }

    @Test
    fun `when locked - increasing limit (relaxation) is NOT allowed immediately`() {
        val state = CommitmentLockState(isLocked = true)

        // Increasing limit is deferred
        assertFalse(ruleEngine.canChangeLimitImmediately(currentLimitMs = 30_000L, newLimitMs = 60_000L, state = state))
        assertFalse(ruleEngine.canChangeLimitImmediately(currentLimitMs = 60_000L, newLimitMs = 120_000L, state = state))
    }

    @Test
    fun `when not locked - unselecting apps is allowed immediately`() {
        val state = CommitmentLockState(isLocked = false)
        assertTrue(ruleEngine.canUnselectAppImmediately("com.instagram.android", state))
    }

    @Test
    fun `when locked - unselecting apps is blocked immediately`() {
        val state = CommitmentLockState(isLocked = true)
        assertFalse(ruleEngine.canUnselectAppImmediately("com.instagram.android", state))
    }

    @Test
    fun `selecting restricted apps is always allowed immediately (tightening)`() {
        assertTrue(ruleEngine.canSelectApp("com.tiktok.android", CommitmentLockState(isLocked = false)))
        assertTrue(ruleEngine.canSelectApp("com.tiktok.android", CommitmentLockState(isLocked = true)))
    }

    @Test
    fun `midnight transition - does not silently unlock commitment when no unlock was requested`() {
        val midnightMillis = 10_000_000L
        val nowMillis = midnightMillis + 5_000L // Past midnight

        val state = CommitmentLockState(
            isLocked = true,
            lockedAtMillis = 1_000L,
            pendingUnlock = false,
            pendingUnlockTargetMidnightMillis = 0L
        )

        val result = ruleEngine.evaluateMidnightTransitions(
            state = state,
            currentTimeMillis = nowMillis
        )

        // Commitment lock MUST remain active across midnight
        assertTrue(result.updatedState.isLocked)
        assertFalse(result.updatedState.pendingUnlock)
        assertTrue(result.appsToUnselect.isEmpty())
        assertTrue(result.limitsToApply.isEmpty())
        assertFalse(result.didUnlock)
    }

    @Test
    fun `midnight transition - unlocks commitment if pending unlock target midnight has passed`() {
        val targetMidnight = 10_000_000L
        val beforeMidnight = targetMidnight - 1_000L
        val afterMidnight = targetMidnight + 1_000L

        val state = CommitmentLockState(
            isLocked = true,
            lockedAtMillis = 1_000L,
            pendingUnlock = true,
            pendingUnlockTargetMidnightMillis = targetMidnight
        )

        // Before midnight: still locked
        val resultBefore = ruleEngine.evaluateMidnightTransitions(
            state = state,
            currentTimeMillis = beforeMidnight
        )
        assertTrue(resultBefore.updatedState.isLocked)
        assertTrue(resultBefore.updatedState.pendingUnlock)
        assertFalse(resultBefore.didUnlock)

        // After midnight: unlocks!
        val resultAfter = ruleEngine.evaluateMidnightTransitions(
            state = state,
            currentTimeMillis = afterMidnight
        )
        assertFalse(resultAfter.updatedState.isLocked)
        assertFalse(resultAfter.updatedState.pendingUnlock)
        assertEquals(0L, resultAfter.updatedState.pendingUnlockTargetMidnightMillis)
        assertTrue(resultAfter.didUnlock)
    }

    @Test
    fun `midnight transition - applies pending app removals and limit increases when target midnight reached`() {
        val targetMidnight = 10_000_000L
        val afterMidnight = targetMidnight + 5_000L

        val state = CommitmentLockState(
            isLocked = true,
            pendingUnlockTargetMidnightMillis = targetMidnight,
            pendingRemovedPackages = setOf("com.instagram.android"),
            pendingIncreasedLimits = mapOf("com.google.android.youtube" to 180_000L)
        )

        val result = ruleEngine.evaluateMidnightTransitions(
            state = state,
            currentTimeMillis = afterMidnight
        )

        // Still locked because pendingUnlock was false
        assertTrue(result.updatedState.isLocked)
        // Removals and limit increases are applied
        assertEquals(setOf("com.instagram.android"), result.appsToUnselect)
        assertEquals(mapOf("com.google.android.youtube" to 180_000L), result.limitsToApply)
        // Pending sets are now cleared in the updated state
        assertTrue(result.updatedState.pendingRemovedPackages.isEmpty())
        assertTrue(result.updatedState.pendingIncreasedLimits.isEmpty())
        assertFalse(result.didUnlock)
    }
}
