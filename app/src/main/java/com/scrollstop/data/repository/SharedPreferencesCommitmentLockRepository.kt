package com.scrollstop.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.scrollstop.data.time.SystemTimeProvider
import com.scrollstop.data.time.TimeProvider
import com.scrollstop.domain.model.CommitmentLockState
import com.scrollstop.domain.rules.CommitmentRuleEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [SharedPreferences]-backed persistent implementation of [CommitmentLockRepository].
 *
 * Persists commitment status and staged relaxations across app restarts, process termination,
 * and device reboots.
 */
class SharedPreferencesCommitmentLockRepository(
    context: Context,
    private val selectedAppsRepository: SelectedAppsRepository,
    private val dailyLimitRepository: DailyLimitRepository,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
    private val commitmentRuleEngine: CommitmentRuleEngine = CommitmentRuleEngine(),
    prefName: String = PREF_NAME
) : CommitmentLockRepository {

    companion object {
        const val PREF_NAME = "scrollstop_commitment_lock_prefs"
        private const val KEY_IS_LOCKED = "is_locked"
        private const val KEY_LOCKED_AT_MILLIS = "locked_at_millis"
        private const val KEY_PENDING_UNLOCK = "pending_unlock"
        private const val KEY_TARGET_MIDNIGHT = "target_midnight_millis"
        private const val KEY_PENDING_REMOVED_PACKAGES = "pending_removed_packages"
        private const val KEY_PENDING_LIMIT_PREFIX = "pending_limit_ms_"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
    private val lock = Any()

    private val _commitmentState = MutableStateFlow(readFromPrefs())
    override val commitmentState: StateFlow<CommitmentLockState> = _commitmentState.asStateFlow()

    init {
        // Evaluate any midnight transitions that occurred while app was dead
        checkAndApplyMidnightTransitions()
    }

    private fun readFromPrefs(): CommitmentLockState {
        val isLocked = prefs.getBoolean(KEY_IS_LOCKED, false)
        val lockedAt = prefs.getLong(KEY_LOCKED_AT_MILLIS, 0L)
        val pendingUnlock = prefs.getBoolean(KEY_PENDING_UNLOCK, false)
        val targetMidnight = prefs.getLong(KEY_TARGET_MIDNIGHT, 0L)
        val removedPackages = prefs.getStringSet(KEY_PENDING_REMOVED_PACKAGES, emptySet())?.toSet() ?: emptySet()

        val increasedLimits = mutableMapOf<String, Long>()
        for ((key, value) in prefs.all) {
            if (key.startsWith(KEY_PENDING_LIMIT_PREFIX) && value is Long) {
                val pkg = key.removePrefix(KEY_PENDING_LIMIT_PREFIX)
                increasedLimits[pkg] = value
            }
        }

        return CommitmentLockState(
            isLocked = isLocked,
            lockedAtMillis = lockedAt,
            pendingUnlock = pendingUnlock,
            pendingUnlockTargetMidnightMillis = targetMidnight,
            pendingRemovedPackages = removedPackages,
            pendingIncreasedLimits = increasedLimits
        )
    }

    private fun writeToPrefs(state: CommitmentLockState) {
        val editor = prefs.edit()
            .putBoolean(KEY_IS_LOCKED, state.isLocked)
            .putLong(KEY_LOCKED_AT_MILLIS, state.lockedAtMillis)
            .putBoolean(KEY_PENDING_UNLOCK, state.pendingUnlock)
            .putLong(KEY_TARGET_MIDNIGHT, state.pendingUnlockTargetMidnightMillis)
            .putStringSet(KEY_PENDING_REMOVED_PACKAGES, state.pendingRemovedPackages)

        // Clear existing pending limits then rewrite current ones
        for (key in prefs.all.keys) {
            if (key.startsWith(KEY_PENDING_LIMIT_PREFIX)) {
                editor.remove(key)
            }
        }
        for ((pkg, limitMs) in state.pendingIncreasedLimits) {
            editor.putLong(KEY_PENDING_LIMIT_PREFIX + pkg, limitMs)
        }

        editor.apply()
        _commitmentState.value = state
    }

    override fun enableCommitmentLock(consentConfirmed: Boolean): Boolean {
        if (!consentConfirmed) return false
        synchronized(lock) {
            val newState = _commitmentState.value.copy(
                isLocked = true,
                lockedAtMillis = timeProvider.currentTimeMillis()
            )
            writeToPrefs(newState)
            return true
        }
    }

    override fun requestUnlock(): Boolean {
        synchronized(lock) {
            val current = _commitmentState.value
            if (!current.isLocked) return false

            val targetMidnight = if (current.pendingUnlockTargetMidnightMillis > 0L) {
                current.pendingUnlockTargetMidnightMillis
            } else {
                timeProvider.getNextMidnightMillis()
            }

            val updated = current.copy(
                pendingUnlock = true,
                pendingUnlockTargetMidnightMillis = targetMidnight
            )
            writeToPrefs(updated)
            return true
        }
    }

    override fun cancelUnlockRequest(): Boolean {
        synchronized(lock) {
            val current = _commitmentState.value
            if (!current.pendingUnlock) return false

            val hasOtherPendings = current.pendingRemovedPackages.isNotEmpty() || current.pendingIncreasedLimits.isNotEmpty()
            val targetMidnight = if (hasOtherPendings) current.pendingUnlockTargetMidnightMillis else 0L

            val updated = current.copy(
                pendingUnlock = false,
                pendingUnlockTargetMidnightMillis = targetMidnight
            )
            writeToPrefs(updated)
            return true
        }
    }

    override fun stageAppRemoval(packageName: String): Boolean {
        synchronized(lock) {
            val current = _commitmentState.value
            if (!current.isLocked) return false

            val targetMidnight = if (current.pendingUnlockTargetMidnightMillis > 0L) {
                current.pendingUnlockTargetMidnightMillis
            } else {
                timeProvider.getNextMidnightMillis()
            }

            val updatedRemovals = current.pendingRemovedPackages + packageName
            val updated = current.copy(
                pendingUnlockTargetMidnightMillis = targetMidnight,
                pendingRemovedPackages = updatedRemovals
            )
            writeToPrefs(updated)
            return true
        }
    }

    override fun cancelStagedAppRemoval(packageName: String): Boolean {
        synchronized(lock) {
            val current = _commitmentState.value
            if (!current.pendingRemovedPackages.contains(packageName)) return false

            val updatedRemovals = current.pendingRemovedPackages - packageName
            val hasOtherPendings = current.pendingUnlock || updatedRemovals.isNotEmpty() || current.pendingIncreasedLimits.isNotEmpty()
            val targetMidnight = if (hasOtherPendings) current.pendingUnlockTargetMidnightMillis else 0L

            val updated = current.copy(
                pendingUnlockTargetMidnightMillis = targetMidnight,
                pendingRemovedPackages = updatedRemovals
            )
            writeToPrefs(updated)
            return true
        }
    }

    override fun stageLimitIncrease(packageName: String, newLimitMs: Long): Boolean {
        synchronized(lock) {
            val current = _commitmentState.value
            if (!current.isLocked) return false

            val targetMidnight = if (current.pendingUnlockTargetMidnightMillis > 0L) {
                current.pendingUnlockTargetMidnightMillis
            } else {
                timeProvider.getNextMidnightMillis()
            }

            val updatedLimits = current.pendingIncreasedLimits + (packageName to newLimitMs)
            val updated = current.copy(
                pendingUnlockTargetMidnightMillis = targetMidnight,
                pendingIncreasedLimits = updatedLimits
            )
            writeToPrefs(updated)
            return true
        }
    }

    override fun cancelStagedLimitIncrease(packageName: String): Boolean {
        synchronized(lock) {
            val current = _commitmentState.value
            if (!current.pendingIncreasedLimits.containsKey(packageName)) return false

            val updatedLimits = current.pendingIncreasedLimits - packageName
            val hasOtherPendings = current.pendingUnlock || current.pendingRemovedPackages.isNotEmpty() || updatedLimits.isNotEmpty()
            val targetMidnight = if (hasOtherPendings) current.pendingUnlockTargetMidnightMillis else 0L

            val updated = current.copy(
                pendingUnlockTargetMidnightMillis = targetMidnight,
                pendingIncreasedLimits = updatedLimits
            )
            writeToPrefs(updated)
            return true
        }
    }

    override fun checkAndApplyMidnightTransitions() {
        synchronized(lock) {
            val current = _commitmentState.value
            val result = commitmentRuleEngine.evaluateMidnightTransitions(
                state = current,
                currentTimeMillis = timeProvider.currentTimeMillis()
            )

            // Apply any staged removals to selected apps
            if (result.appsToUnselect.isNotEmpty()) {
                val currentSelected = selectedAppsRepository.getSelectedPackages()
                val updatedSelected = currentSelected - result.appsToUnselect
                selectedAppsRepository.setSelectedPackages(updatedSelected)
            }

            // Apply any staged increased limits
            for ((pkg, limitMs) in result.limitsToApply) {
                dailyLimitRepository.setLimitForPackage(pkg, limitMs)
            }

            if (result.updatedState != current) {
                writeToPrefs(result.updatedState)
            }
        }
    }

    override fun debugClearCommitmentState() {
        synchronized(lock) {
            prefs.edit().clear().apply()
            _commitmentState.value = CommitmentLockState()
        }
    }
}
