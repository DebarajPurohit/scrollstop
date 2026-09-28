# Test Plan

## Unit

-   App discovery mapping and deduplication
-   Deterministic alphabetical sorting of discovered apps
-   Self-package (`com.scrollstop`) exclusion
-   Package-to-usage mapping (`AppUsageInfo`)
-   Selected app with usage duration formatting
-   Selected app with zero usage ("0 min")
-   Multiple selected apps sorting & rendering
-   Unselected app exclusion from usage results
-   Missing package safe fallback (0 min)
-   Duration formatting (`UsageFormatter`: sec, min, hr)
-   Today's start/end midnight time calculation (`SystemTimeProvider`)
-   Permission-denied state (`AppOpsManager.MODE_IGNORED`)
-   Empty UsageStats result handling
-   Timezone and daylight-saving transition handling
-   Rules Engine: usage 0 ms, limit 2 min (NOT_STARTED, remaining 2 min)
-   Rules Engine: usage 30 sec, limit 2 min (WITHIN_LIMIT, remaining 1 min 30 sec)
-   Rules Engine: usage exactly 2 min (LIMIT_REACHED, remaining 0 min)
-   Rules Engine: usage greater than 2 min (LIMIT_REACHED, remaining 0 min)
-   Rules Engine: usage 1 ms below limit (WITHIN_LIMIT, remaining 1 ms)
-   Rules Engine: usage 1 ms above limit (LIMIT_REACHED, remaining 0 min)
-   Rules Engine: remaining duration never negative (guaranteed >= 0 ms)
-   Rules Engine: multiple selected apps evaluated independently
-   Rules Engine: unselected apps excluded from evaluation
-   Rules Engine: UsageStats data maps with exact millisecond precision
-   Rules Engine: repeated evaluation produces identical deterministic output
-   Rules Engine: zero Android framework or UI dependencies in core domain engine


## Integration

-   UsageStatsManager → Usage Engine
-   Usage Engine → Rules Engine
-   Accessibility event → Rules Engine
-   Rules Engine → Blocking Layer
-   Permission health checks

## UI

-   Onboarding
-   Permission guidance
-   App selection
-   Limit configuration
-   Dashboard
-   Blocking screen
-   History
-   Recovery states

## Critical Enforcement

-   Limit reached while app is open
-   Reopen/repeated launch
-   Lock/unlock
-   Reboot
-   Background/foreground
-   Midnight reset
-   Permission removal
-   Accessibility interruption
-   Usage Access removal
-   Battery restrictions
-   App/OS updates
-   Date/time changes

## Security/Privacy

Verify no unrelated accessibility data, messages, keystrokes or
unnecessary logs are collected.

## Performance

Measure startup, blocking response and battery impact.

## Release Gate

No known critical enforcement failure.
