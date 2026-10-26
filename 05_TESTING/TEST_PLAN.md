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

### Phase 4 Protection Health & Reliability Test Suite

1.  Protection Health: both Usage Access granted and Accessibility active -> PROTECTED
2.  Protection Health: Usage Access revoked while Accessibility active -> USAGE_ACCESS_REQUIRED
3.  Protection Health: Usage Access granted while Accessibility disabled -> ACCESSIBILITY_REQUIRED
4.  Protection Health: Usage Access granted while Accessibility interrupted -> ACCESSIBILITY_INTERRUPTED
5.  Protection Health: both Usage Access and Accessibility disabled -> DEGRADED
6.  Protection Health: dynamic state change when AccessibilityService connects/disconnects
7.  Protection Health: refreshHealth re-queries runtime permissions and updates state
8.  Midnight Boundary: trigger emitted on the current calendar day persists and reloads
9.  Midnight Boundary: trigger emitted prior to midnight (yesterday) automatically expires on reload
10. Midnight Boundary: checkAndExpireStaleTriggers() clears expired triggers across midnight boundary
11. Main ViewModel: uiState reflects protection health and selected app count
12. Main ViewModel: uiState reactively updates when protection health transitions
13. Main ViewModel: openUsageAccessSettings and openAccessibilitySettings execute safely
14. Main UI: renders PROTECTED banner with active app count when protection is active
15. Main UI: renders USAGE_ACCESS_REQUIRED banner with one-tap recovery button when usage access revoked
16. Main UI: renders ACCESSIBILITY_INTERRUPTED banner with re-enable recovery button when service interrupted

### Commitment Lock & Midnight Schedule Test Suite

1.  Commitment Lock: enabling requires affirmative user consent dialog confirmation
2.  Commitment Lock: immediate tightening — selecting newly restricted app applies immediately
3.  Commitment Lock: immediate tightening — reducing daily limit applies immediately
4.  Commitment Lock: deferred relaxation — unselecting protected app is staged until next calendar midnight
5.  Commitment Lock: deferred relaxation — increasing daily limit is staged until next calendar midnight
6.  Commitment Lock: unlock request scheduled for target midnight and cancellable prior to boundary
7.  Commitment Lock: midnight boundary applies staged app removals and increased limits, clearing pending requests
8.  Commitment Lock: midnight boundary resets daily usage metrics, NOT the commitment itself
9.  Commitment Lock: SharedPreferences persistence preserves lock state across process death and device reboots
10. Commitment Lock: developer-only `debugClearCommitmentState()` clears commitment state without midnight wait (gated by `BuildConfig.DEBUG`, stripped in release builds)

### Enforcement Recovery & Restart Test Suite

1.  Enforcement Recovery: `RestrictedAppAccessibilityService.onServiceConnected()` rechecks Usage Access permission and updates protection health
2.  Enforcement Recovery: missing Usage Access permission reports `USAGE_ACCESS_REQUIRED` and pauses monitoring loops safely
3.  Enforcement Recovery: `evaluateCurrentForegroundApp()` detects when a restricted app is already in the foreground on service connection
4.  Enforcement Recovery: over-limit restricted app already in foreground emits `EnforcementTrigger` and launches blocking UI immediately
5.  Enforcement Recovery: under-limit restricted app already in foreground immediately initiates 5-second continuous live monitoring
6.  Enforcement Recovery: service reconnection cancels prior package observation jobs without creating duplicate collection loops
7.  Enforcement Recovery: `onInterrupt()` and `onUnbind()` cleanly cancel live monitoring and transition health states (`ACCESSIBILITY_INTERRUPTED` / `ACCESSIBILITY_NOT_GRANTED`)
8.  Enforcement Recovery: `ServiceLocator.getLiveLimitEnforcementEngine()` validates coroutine scope activity and recreates engine cleanly on scope changes
9.  Enforcement Recovery: process recreation from SharedPreferences preserves configured daily limits and commitment lock state
10. Enforcement Recovery: stale triggers from previous calendar day expire cleanly across day boundaries upon service reconnection


### Custom-Duration Limit Enforcement Test Suite

1. Custom Duration Calculation: hours and minutes converted to milliseconds via safe 64-bit Long arithmetic.
2. Custom Duration Persistence: exact custom limit stored and retrieved from `SharedPreferencesDailyLimitRepository`.
3. Navigation Resilience: custom limit preserved in `LimitsViewModel` when navigating away and returning.
4. Process Recreation: custom limit reloaded accurately from disk across simulated process death.
5. Immediate Enforcement: custom limit set below current daily usage triggers blocker immediately upon app foregrounding.
6. Non-Premature Enforcement: custom limit set above current usage allows foreground usage and triggers only when limit is reached.
7. Preset Baseline Parity: existing presets (15m, 30m, etc.) continue to trigger accurately at boundary.
8. Commitment Lock Interaction: custom limit increases staged until midnight; custom tightening applies immediately; staged changes cancellable.
9. Preset & Custom Equivalence: equivalent preset (15m) and custom duration (0h 15m) evaluate to identical limit and enforcement states.
10. Input Validation & Boundaries: zero, negative, >24h, and >=60m values rejected with user-facing validation errors; 1m and 24h accepted.

### Physical Device Validation Results (Owner-Confirmed)

- **Test Device Validation (Confirmed PASS)**:
  - PASS — Debug-only Commitment Lock reset: `[DEBUG TEST] Reset Commitment Lock` clears state cleanly on test hardware without midnight delays.
  - PASS — Backgrounding & returning: protection active and window event delivery intact.
  - PASS — App process death / swiping Stop Doom Scroll from Recents: service rebinds and enforcement recovers.
  - PASS — Device restart / reboot: service reconnects upon unlock and evaluates foreground app.
  - PASS — Opening restricted app after restart: blocks immediately once limit is reached without requiring Stop Doom Scroll to be launched manually.
- **Android Force-Stop OS Boundary**:
  - Explicit force-stop via Android Settings (`am force-stop`) places package into the `STOPPED` state (`FLAG_EXCLUDE_STOPPED_PACKAGES`).
  - Standard OS behavior prohibits background startup and accessibility binding until the user manually launches the app from the launcher. Documented as standard OS boundary.
- **Multi-OEM Reliability Testing (Pending Phase 6)**:
  - Device matrix testing across OEM-specific battery management (Xiaomi MIUI/HyperOS, Samsung OneUI, Oppo ColorOS, Vivo FuntouchOS) remains open.

## Integration

-   UsageStatsManager → Usage Engine
-   Usage Engine → Rules Engine
-   AccessibilityEvent → RestrictedAppDetectionEngine → UsageRuleEngine → EnforcementTrigger
-   SharedPreferencesSelectedAppsRepository & SharedPreferencesDailyLimitRepository persistence
-   Permission & Service health checks
-   ProtectionHealthRepository reactive synchronization

## UI & Presentation Layer Tests

### Redesigned UI Architecture Tests
- **Welcome & Entry UI**:
  - Displays "Continue with Google" and "Continue as guest" options with zero friction.
  - Tapping either option navigates directly to the mandatory Usage Access gate screen.
- **Mandatory Usage Access Gate UI**:
  - Enforces permission verification prior to allowing access to Home.
  - Tapping "Open Settings" launches `Settings.ACTION_USAGE_ACCESS_SETTINGS`.
  - Returning from Settings with permission granted automatically routes to Home.
  - Returning without permission blocks navigation into Home and presents instructional guidance.
  - "Check Again" triggers an immediate re-query of `AppOpsManager`.
- **Home Dashboard UI**:
  - Displays 7-day bar chart of total device usage across all applications.
  - Renders today's total active screen time metric accurately.
  - App breakdown displays today's usage and 7-day cumulative total per app.
  - **Incomplete History Handling**: For devices with fewer than 7 days of history, verifies that available days render accurately, missing days display as "No data" (never fabricated as 0 min), "X of 7 days recorded" badge is shown, and average benchmarks reflect only recorded days.
  - **Data Integrity / Missing Permission Rule**: Asserts that when Usage Access is revoked or data query fails, the UI renders the permission-recovery state and **never** displays "0 min" or zero usage.
  - Renders `ProtectionStatusCard` showing real-time health state (`PROTECTED`, `USAGE_ACCESS_REQUIRED`, `ACCESSIBILITY_REQUIRED`, `DEGRADED`) with direct recovery actions.
  - Displays non-blocking contextual guided setup cards for first-time or incomplete setup.
- **Persistent 4-Tab Navigation**:
  - Tab transitions between Home, Limits, History, and Settings preserve active state and backstack.
- **Limits & App Management UI**:
  - Real-time search query filters installed applications dynamically.
  - App row displays today's usage, 7-day cumulative usage, and active daily limit.
  - Limit configuration sheet offers preset duration pills (15m, 30m, 45m, 1h, 1.5h, 2h) and custom duration entry.
  - Commitment Lock: Tightening rules confirmed as immediate; relaxation shows midnight deferral notice.
  - Pending change badges ("Removal at midnight", "Limit change at midnight") rendered accurately with cancellation actions.
- **History UI**:
  - Interactive 7-day total usage chart allows selecting calendar days to inspect historical daily breakdowns.
  - Accurately renders day-by-day app consumption and limit compliance indicators.
- **Settings UI**:
  - Theme selection segment / dialog: verifies toggling between System Default, Light, and Dark applies theme state dynamically.
  - Account section renders Guest status with "Link Google Account" or Google profile details.
  - Production build gate: verifies `[DEBUG TEST] Reset Commitment Lock` is strictly absent in non-debug UI.
- **Blocking Screen UI**:
  - Renders calm, distraction-free "Time's up" screen with app label, reached limit, and midnight reset notice.
  - BackHandler intercepts physical back button and navigates safely to Home Dashboard.

## Critical Enforcement

-   Limit reached while app is open
-   Reopen/repeated launch
-   Lock/unlock
-   Reboot
-   Background/foreground
-   Midnight reset
-   Permission removal & restoration
-   Accessibility interruption & rebind
-   Usage Access removal & recovery
-   Battery restrictions
-   App/OS updates
-   Date/time changes

## Security/Privacy

Verify no unrelated accessibility data, messages, text, passwords, keystrokes, screenshots or unnecessary logs are collected.

## Performance

Measure event handling speed (< 5ms per event), memory usage, and battery impact. Zero background loops or timers.

## Release Gate

No known critical enforcement failure.
