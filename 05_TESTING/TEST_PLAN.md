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
-   UsageStats result containing unrelated packages
-   Timezone and daylight-saving transition handling

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
