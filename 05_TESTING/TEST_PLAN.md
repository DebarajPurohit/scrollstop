# Test Plan

## Unit

-   Usage calculations
-   Remaining time
-   Limit comparisons
-   Daily reset
-   Timezone/date handling
-   Rule evaluation

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
