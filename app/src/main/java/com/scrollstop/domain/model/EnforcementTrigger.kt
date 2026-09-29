package com.scrollstop.domain.model

import com.scrollstop.domain.rules.LimitState

/**
 * Internal deterministic signal produced when a restricted application is opened
 * and its current usage state is evaluated as [LimitState.LIMIT_REACHED].
 *
 * @property packageName Unique Android package name of the detected application.
 * @property reason Deterministic limit evaluation state (defaults to [LimitState.LIMIT_REACHED]).
 * @property detectedAtElapsedRealtimeMs Monotonic timestamp (ms) when the detection occurred.
 */
data class EnforcementTrigger(
    val packageName: String,
    val reason: LimitState = LimitState.LIMIT_REACHED,
    val detectedAtElapsedRealtimeMs: Long = 0L
)
