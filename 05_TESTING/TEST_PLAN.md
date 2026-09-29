# Test Plan

## Unit Tests

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

### POC-04 Accessibility Detection & Trigger Test Suite

1.  Detection Engine: null event / null package -> ignored (returns null)
2.  Detection Engine: wrong event type (non-TYPE_WINDOW_STATE_CHANGED) -> ignored (returns null)
3.  Detection Engine: empty or blank package -> ignored (returns null)
4.  Detection Engine: Stop Doom Scroll self-package (`com.scrollstop`) -> ignored (returns null)
5.  Detection Engine: unselected package -> ignored (returns null)
6.  Detection Engine: selected package + NOT_STARTED -> no enforcement trigger (returns null)
7.  Detection Engine: selected package + WITHIN_LIMIT -> no enforcement trigger (returns null)
8.  Detection Engine: selected package + LIMIT_REACHED -> produces deterministic EnforcementTrigger
9.  Detection Engine: selected package with usage over limit -> produces deterministic EnforcementTrigger
10. Detection Engine: repeated identical event evaluated deterministically without runaway loops
11. Detection Engine: multiple selected apps evaluated independently
12. Detection Engine: changing selected-app set is respected dynamically
13. Service Security: service configuration explicitly prohibits window content retrieval (`canRetrieveWindowContent == false`)
14. Service Configuration: service subscribes only to `TYPE_WINDOW_STATE_CHANGED` and narrow package filtering
15. Privacy Boundary: event processing accesses ONLY packageName string
16. Health Repository: service health correctly reports active, unavailable, and interrupted states

## Integration

-   UsageStatsManager → Usage Engine
-   Usage Engine → Rules Engine
-   AccessibilityEvent → RestrictedAppDetectionEngine → UsageRuleEngine → EnforcementTrigger
-   SharedPreferencesSelectedAppsRepository & SharedPreferencesDailyLimitRepository persistence
-   Permission & Service health checks

## UI

-   MainScreen navigation to App Selection, Usage Tracking, and Accessibility Disclosure
-   Prominent Disclosure UI displaying non-accessibility-tool disclosure and privacy guarantee
-   Grant Accessibility Permission button opening system settings
-   Internal EnforcementTrigger detection display indicator

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

Verify no unrelated accessibility data, messages, text, passwords, keystrokes, screenshots or unnecessary logs are collected.

## Performance

Measure event handling speed (< 5ms per event), memory usage, and battery impact. Zero background loops or timers.

## Release Gate

No known critical enforcement failure.
