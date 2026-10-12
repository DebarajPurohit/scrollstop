package com.scrollstop.data.repository

import com.scrollstop.domain.model.CommitmentLockState
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for managing and persisting Commitment Lock state.
 */
interface CommitmentLockRepository {
    /** Observable state flow of the current [CommitmentLockState]. */
    val commitmentState: StateFlow<CommitmentLockState>

    /**
     * Activates Commitment Lock globally across all selected applications.
     * Requires explicit, affirmative user consent.
     *
     * @param consentConfirmed True if user affirmatively confirmed consent
     * @return True if successfully activated, false otherwise
     */
    fun enableCommitmentLock(consentConfirmed: Boolean): Boolean

    /**
     * Requests to unlock the commitment.
     * When locked, unlocking is deferred until the local midnight reset.
     *
     * @return True if unlock was staged, false if not locked
     */
    fun requestUnlock(): Boolean

    /**
     * Cancels a previously requested pending unlock before midnight arrives.
     *
     * @return True if cancelled, false if no pending unlock existed
     */
    fun cancelUnlockRequest(): Boolean

    /**
     * Stages a protected application for removal at the next midnight reset.
     */
    fun stageAppRemoval(packageName: String): Boolean

    /**
     * Cancels a previously staged application removal.
     */
    fun cancelStagedAppRemoval(packageName: String): Boolean

    /**
     * Stages an increased daily limit for an application to take effect at the next midnight reset.
     */
    fun stageLimitIncrease(packageName: String, newLimitMs: Long): Boolean

    /**
     * Cancels a previously staged limit increase.
     */
    fun cancelStagedLimitIncrease(packageName: String): Boolean

    /**
     * Evaluates whether current wall-clock time has crossed the local midnight boundary,
     * applying any eligible pending unlock or relaxation requests.
     */
    fun checkAndApplyMidnightTransitions()

    /**
     * Developer test-only mechanism to clear all Commitment Lock state and pending requests immediately.
     * Restores normal configurable state without waiting for midnight.
     */
    fun debugClearCommitmentState()
}
