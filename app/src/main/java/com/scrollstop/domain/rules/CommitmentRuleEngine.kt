package com.scrollstop.domain.rules

import com.scrollstop.domain.model.CommitmentLockState

/**
 * Pure, deterministic domain-level rules engine for Commitment Lock.
 *
 * Enforces:
 * - Immediate tightening: Selecting a new restricted app or reducing/tightening an existing
 *   daily limit is always permitted immediately, even when locked.
 * - Delayed relaxation: Unselecting a protected app, increasing an existing daily limit, or
 *   unlocking the commitment requires waiting until the local calendar midnight reset.
 * - Midnight reset independence: Midnight resets daily usage metrics, NOT the commitment itself.
 *   The commitment stays locked on subsequent days unless an explicit unlock request was staged
 *   and has reached its target midnight.
 */
class CommitmentRuleEngine {

    /**
     * Determines whether selecting a new application is allowed immediately.
     * Adding an app strengthens commitment and is always permitted.
     */
    fun canSelectApp(packageName: String, state: CommitmentLockState): Boolean = true

    /**
     * Determines whether unselecting an existing restricted application can take effect immediately.
     * When locked, removing an app is a relaxation and cannot be applied immediately.
     */
    fun canUnselectAppImmediately(packageName: String, state: CommitmentLockState): Boolean {
        return !state.isLocked
    }

    /**
     * Determines whether changing a daily limit takes effect immediately.
     * When locked:
     * - Reducing or matching the limit (tightening) is permitted immediately.
     * - Increasing the limit (relaxing) is blocked immediately and must be deferred to next midnight.
     */
    fun canChangeLimitImmediately(
        currentLimitMs: Long,
        newLimitMs: Long,
        state: CommitmentLockState
    ): Boolean {
        if (!state.isLocked) return true
        return newLimitMs <= currentLimitMs
    }

    /**
     * Represents the outcome of evaluating a midnight transition against the current wall-clock time.
     */
    data class MidnightTransitionResult(
        val updatedState: CommitmentLockState,
        val appsToUnselect: Set<String> = emptySet(),
        val limitsToApply: Map<String, Long> = emptyMap(),
        val didUnlock: Boolean = false
    )

    /**
     * Evaluates whether any staged relaxations or pending unlock requests have become eligible
     * as of [currentTimeMillis].
     *
     * @param state Current [CommitmentLockState]
     * @param currentTimeMillis Current wall-clock time from TimeProvider
     * @return [MidnightTransitionResult] with updated state and staged actions to apply
     */
    fun evaluateMidnightTransitions(
        state: CommitmentLockState,
        currentTimeMillis: Long
    ): MidnightTransitionResult {
        if (!state.isLocked) {
            return MidnightTransitionResult(updatedState = state)
        }

        val targetMidnight = state.pendingUnlockTargetMidnightMillis
        val isTargetReached = targetMidnight > 0L && currentTimeMillis >= targetMidnight

        if (!isTargetReached) {
            // Target midnight not yet reached; commitment and pending requests remain unchanged
            return MidnightTransitionResult(updatedState = state)
        }

        return if (state.pendingUnlock) {
            // Full unlock requested and eligible: unlock commitment, apply staged relaxations, clear pending state
            MidnightTransitionResult(
                updatedState = CommitmentLockState(
                    isLocked = false,
                    lockedAtMillis = 0L,
                    pendingUnlock = false,
                    pendingUnlockTargetMidnightMillis = 0L,
                    pendingRemovedPackages = emptySet(),
                    pendingIncreasedLimits = emptyMap()
                ),
                appsToUnselect = state.pendingRemovedPackages,
                limitsToApply = state.pendingIncreasedLimits,
                didUnlock = true
            )
        } else {
            // Individual relaxations were staged without unlocking overall commitment:
            // apply staged relaxations, but maintain isLocked = true
            MidnightTransitionResult(
                updatedState = state.copy(
                    pendingUnlockTargetMidnightMillis = 0L,
                    pendingRemovedPackages = emptySet(),
                    pendingIncreasedLimits = emptyMap()
                ),
                appsToUnselect = state.pendingRemovedPackages,
                limitsToApply = state.pendingIncreasedLimits,
                didUnlock = false
            )
        }
    }
}
