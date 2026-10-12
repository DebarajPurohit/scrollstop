package com.scrollstop.domain.model

/**
 * Immutable state model representing the active status and pending relaxation requests
 * for Commitment Lock.
 *
 * Rules:
 * - When [isLocked] is true, the commitment is active globally across all selected restricted apps.
 * - Tightening restrictions (reducing limit, adding restricted apps) takes effect immediately.
 * - Relaxing restrictions (increasing limit, removing apps, unlocking commitment) requires
 *   waiting until the next local calendar midnight reset.
 * - [pendingUnlockTargetMidnightMillis] marks the local midnight timestamp at or after which
 *   pending relaxation requests become eligible.
 * - Daily midnight resets usage metrics, NOT the commitment itself. The commitment remains
 *   active across days until an explicit unlock request reaches its target midnight.
 */
data class CommitmentLockState(
    val isLocked: Boolean = false,
    val lockedAtMillis: Long = 0L,
    val pendingUnlock: Boolean = false,
    val pendingUnlockTargetMidnightMillis: Long = 0L,
    val pendingRemovedPackages: Set<String> = emptySet(),
    val pendingIncreasedLimits: Map<String, Long> = emptyMap()
) {
    /** True if any relaxation request is awaiting midnight eligibility. */
    val isRelaxationPending: Boolean
        get() = pendingUnlock || pendingRemovedPackages.isNotEmpty() || pendingIncreasedLimits.isNotEmpty()
}
