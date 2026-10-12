package com.scrollstop

import com.scrollstop.data.repository.CommitmentLockRepository
import com.scrollstop.domain.model.CommitmentLockState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeCommitmentLockRepository : CommitmentLockRepository {

    private val _commitmentState = MutableStateFlow(CommitmentLockState())
    override val commitmentState: StateFlow<CommitmentLockState> = _commitmentState.asStateFlow()

    fun updateState(transform: (CommitmentLockState) -> CommitmentLockState) {
        _commitmentState.value = transform(_commitmentState.value)
    }

    override fun enableCommitmentLock(consentConfirmed: Boolean): Boolean {
        if (!consentConfirmed) return false
        _commitmentState.value = _commitmentState.value.copy(
            isLocked = true,
            lockedAtMillis = 100_000L
        )
        return true
    }

    override fun requestUnlock(): Boolean {
        if (!_commitmentState.value.isLocked) return false
        _commitmentState.value = _commitmentState.value.copy(
            pendingUnlock = true,
            pendingUnlockTargetMidnightMillis = 200_000L
        )
        return true
    }

    override fun cancelUnlockRequest(): Boolean {
        if (!_commitmentState.value.pendingUnlock) return false
        _commitmentState.value = _commitmentState.value.copy(
            pendingUnlock = false,
            pendingUnlockTargetMidnightMillis = 0L
        )
        return true
    }

    override fun stageAppRemoval(packageName: String): Boolean {
        _commitmentState.value = _commitmentState.value.copy(
            pendingRemovedPackages = _commitmentState.value.pendingRemovedPackages + packageName
        )
        return true
    }

    override fun cancelStagedAppRemoval(packageName: String): Boolean {
        _commitmentState.value = _commitmentState.value.copy(
            pendingRemovedPackages = _commitmentState.value.pendingRemovedPackages - packageName
        )
        return true
    }

    override fun stageLimitIncrease(packageName: String, newLimitMs: Long): Boolean {
        _commitmentState.value = _commitmentState.value.copy(
            pendingIncreasedLimits = _commitmentState.value.pendingIncreasedLimits + (packageName to newLimitMs)
        )
        return true
    }

    override fun cancelStagedLimitIncrease(packageName: String): Boolean {
        _commitmentState.value = _commitmentState.value.copy(
            pendingIncreasedLimits = _commitmentState.value.pendingIncreasedLimits - packageName
        )
        return true
    }

    override fun checkAndApplyMidnightTransitions() {
        // Can be simulated in tests by updateState
    }

    override fun debugClearCommitmentState() {
        _commitmentState.value = CommitmentLockState()
    }
}
