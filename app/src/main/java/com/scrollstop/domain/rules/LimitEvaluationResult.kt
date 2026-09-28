package com.scrollstop.domain.rules

/**
 * Result of evaluating an application's today foreground usage against its configured daily limit.
 *
 * @property packageName Unique package identifier of the application
 * @property usedDurationMs Duration spent in foreground today in milliseconds
 * @property limitDurationMs Configured daily limit in milliseconds
 * @property remainingDurationMs Remaining time allowance in milliseconds (guaranteed >= 0)
 * @property limitState Deterministic limit evaluation state ([LimitState.NOT_STARTED], [LimitState.WITHIN_LIMIT], or [LimitState.LIMIT_REACHED])
 */
data class LimitEvaluationResult(
    val packageName: String,
    val usedDurationMs: Long,
    val limitDurationMs: Long,
    val remainingDurationMs: Long,
    val limitState: LimitState
)
