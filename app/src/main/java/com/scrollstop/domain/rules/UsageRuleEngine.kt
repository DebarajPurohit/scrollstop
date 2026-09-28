package com.scrollstop.domain.rules

/**
 * Pure, deterministic domain-level Rules Engine for evaluating daily usage against limits.
 *
 * This component is completely decoupled from Android APIs, Context, wall-clock time, timers,
 * and UI frameworks. It evaluates raw millisecond durations directly.
 */
class UsageRuleEngine {

    /**
     * Evaluates today's usage for a package against its configured limit duration.
     *
     * Rules:
     * - usage < limit => remaining = limit - usage; state = WITHIN_LIMIT (or NOT_STARTED if usage == 0)
     * - usage == limit => remaining = 0; state = LIMIT_REACHED
     * - usage > limit => remaining = 0; state = LIMIT_REACHED
     * - negative values are normalized safely to 0
     * - remaining duration is guaranteed never negative
     * - internal evaluation preserves exact millisecond precision without rounding
     *
     * @param packageName Unique Android package identifier
     * @param usedDurationMs Today's accumulated foreground usage in milliseconds
     * @param limitDurationMs Configured daily limit allowance in milliseconds
     * @return [LimitEvaluationResult] containing exact durations and deterministic [LimitState]
     */
    fun evaluate(
        packageName: String,
        usedDurationMs: Long,
        limitDurationMs: Long
    ): LimitEvaluationResult {
        val safeUsedMs = maxOf(0L, usedDurationMs)
        val safeLimitMs = maxOf(0L, limitDurationMs)

        val remainingMs = maxOf(0L, safeLimitMs - safeUsedMs)

        val state = when {
            safeLimitMs <= 0L -> LimitState.LIMIT_REACHED
            safeUsedMs >= safeLimitMs -> LimitState.LIMIT_REACHED
            safeUsedMs == 0L -> LimitState.NOT_STARTED
            else -> LimitState.WITHIN_LIMIT
        }

        return LimitEvaluationResult(
            packageName = packageName,
            usedDurationMs = safeUsedMs,
            limitDurationMs = safeLimitMs,
            remainingDurationMs = remainingMs,
            limitState = state
        )
    }

    /**
     * Evaluates a set of package usage durations against corresponding configured limits.
     *
     * @param usageMap Map of packageName to used duration in milliseconds
     * @param limitsMap Map of packageName to limit duration in milliseconds
     * @return Map of packageName to [LimitEvaluationResult]
     */
    fun evaluateAll(
        usageMap: Map<String, Long>,
        limitsMap: Map<String, Long>
    ): Map<String, LimitEvaluationResult> {
        return limitsMap.mapValues { (pkg, limitMs) ->
            val usedMs = usageMap[pkg] ?: 0L
            evaluate(pkg, usedMs, limitMs)
        }
    }
}
