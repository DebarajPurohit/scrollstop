package com.scrollstop.ui.screens.limits

import android.graphics.drawable.Drawable
import com.scrollstop.domain.model.CommitmentLockState
import com.scrollstop.domain.rules.LimitState

/**
 * Filter categories for the unified Limits & Apps catalogue.
 */
enum class LimitsFilter {
    ALL,
    CONTROLLED,
    SOCIAL_VIDEO
}

/**
 * Presentation model representing an individual application within the unified Limits catalogue.
 *
 * @property packageName Unique Android package identifier
 * @property appLabel Human-readable display label
 * @property icon Optional application icon drawable
 * @property isSelected Whether this application is currently monitored / controlled
 * @property totalTimeInForegroundMs Accumulated foreground usage for today in milliseconds
 * @property formattedUsage Formatted human-readable usage string (e.g., "23 min 41 sec" or "0 min")
 * @property limitDurationMs Configured daily limit allowance in milliseconds
 * @property formattedLimit Formatted human-readable limit string (e.g., "30 min")
 * @property remainingDurationMs Remaining duration allowance for today in milliseconds
 * @property formattedRemaining Formatted human-readable remaining string (e.g., "6 min 19 sec")
 * @property limitState Evaluated limit state (NOT_STARTED, WITHIN_LIMIT, LIMIT_REACHED)
 * @property isPendingRemoval Whether app removal is staged for local midnight reset under Commitment Lock
 * @property pendingLimitIncreaseMs Staged daily limit increase in milliseconds (if any)
 * @property formattedPendingLimit Formatted human-readable staged limit string (if any)
 */
data class LimitsAppItem(
    val packageName: String,
    val appLabel: String,
    val icon: Drawable? = null,
    val isSelected: Boolean = false,
    val totalTimeInForegroundMs: Long = 0L,
    val formattedUsage: String = "0 min",
    val limitDurationMs: Long = 0L,
    val formattedLimit: String = "0 min",
    val remainingDurationMs: Long = 0L,
    val formattedRemaining: String = "0 min",
    val limitState: LimitState = LimitState.NOT_STARTED,
    val isPendingRemoval: Boolean = false,
    val pendingLimitIncreaseMs: Long? = null,
    val formattedPendingLimit: String? = null
)

/**
 * UI State for the unified Limits & App Management experience.
 */
sealed interface LimitsUiState {
    /** Application catalogue discovery or usage calculation is currently in progress. */
    data object Loading : LimitsUiState

    /** Usage Access permission is required to query foreground usage. */
    data object PermissionRequired : LimitsUiState

    /** No launchable applications were discovered on the device. */
    data class Empty(
        val message: String = "No launchable apps discovered."
    ) : LimitsUiState

    /** An error occurred while discovering applications or querying data. */
    data class Error(
        val errorMessage: String
    ) : LimitsUiState

    /**
     * Successfully resolved and filtered application catalog state.
     *
     * @property apps Current filtered and search-matched list of [LimitsAppItem]
     * @property searchQuery Current search query applied to label or package name
     * @property activeFilter Current active filter category (ALL, CONTROLLED, SOCIAL_VIDEO)
     * @property isCommitmentLocked Whether Commitment Lock is currently active
     * @property commitmentState Observable domain state of Commitment Lock
     * @property lockedNotice Informational message explaining lock actions (e.g., staged removal/relaxation)
     * @property selectedCount Total count of monitored/controlled applications
     * @property totalDiscoveredCount Total count of discovered applications on the device
     */
    data class Success(
        val apps: List<LimitsAppItem> = emptyList(),
        val searchQuery: String = "",
        val activeFilter: LimitsFilter = LimitsFilter.ALL,
        val isCommitmentLocked: Boolean = false,
        val commitmentState: CommitmentLockState = CommitmentLockState(),
        val lockedNotice: String? = null,
        val selectedCount: Int = 0,
        val totalDiscoveredCount: Int = 0
    ) : LimitsUiState {
        val isFilteredEmpty: Boolean get() = apps.isEmpty()
    }
}
