package com.scrollstop.domain.rules

/**
 * Deterministic limit evaluation state for an application usage rule.
 */
enum class LimitState {
    /** Usage is zero milliseconds for today. */
    NOT_STARTED,

    /** Usage is greater than zero but strictly less than the configured daily limit. */
    WITHIN_LIMIT,

    /** Usage is equal to or greater than the configured daily limit. */
    LIMIT_REACHED
}
