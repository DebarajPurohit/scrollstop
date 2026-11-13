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

### Usage Calculation Audit Test Suite (UsageCalculationAuditTest)

1.  **Regression 01**: Confirmtkt multiple foreground sessions with duplicate pause/stop events accurately calculates exact active time (8m), eliminating 85h inflation bug.
2.  **Regression 02**: Total device screen time with multiple apps and pause/stop events accurately calculates interactive screen time (15m), eliminating 22h 27m / 23h 59m bug.
3.  **Invariant 01**: Usage for one package on one calendar day cannot exceed the elapsed time in that day.
4.  **Invariant 02**: Total phone usage cannot exceed elapsed interactive device screen time.
5.  **Invariant 03**: Sum of non-overlapping app foreground durations must not exceed total interactive device screen time.
6.  **Invariant 04**: Every session is counted exactly once; duplicate pause, stop, and destroy events do not duplicate duration.
7.  **Invariant 05**: No usage is attributed before its actual start; orphan pause and stop events without resume attribute 0 time.
8.  **Invariant 06**: No usage is attributed after its actual end.
9.  **Invariant 07**: Today's usage strictly excludes yesterday's completed session duration.
10. **Invariant 08**: Continuous foreground sessions active across midnight contribute only post-midnight duration to the new day.
11. **Edge Case 01**: Multiple internal activities within the same application calculate a continuous single session.
12. **Edge Case 02**: Screen off and on intervals accurately exclude non-interactive screen time.
13. **Edge Case 03**: Keyguard lock (KEYGUARD_SHOWN) and unlock (KEYGUARD_HIDDEN) pause and resume sessions accurately.
14. **Edge Case 04**: Switching from Monitored App A to Monitored App B closes App A's session immediately without overlap.
15. **Edge Case 05**: Seven-day chart daily records return true historical daily usage and never default missing days to 23h 59m.

### Phase 1 Welcome & Mandatory Usage Access Gate Test Suite (WelcomeAndUsageGateTest)

1.  **Welcome UI (Light Theme)**: App identity ("STOP DOOM SCROLL"), motivational copy, privacy badge ("100% Local-First • Zero Cloud Sync"), and both CTA buttons render correctly with accessible touch targets ($\ge 48\text{dp}$).
2.  **Welcome UI (Dark Theme)**: Elements render properly in Dark theme mode with adequate WCAG AA contrast.
3.  **Guest Entry**: Tapping "Continue as Guest" completes onboarding and invokes callback.
4.  **Standard Entry**: Tapping "Get Started" records standard session intent and invokes callback.
5.  **Usage Gate Education**: Renders 4 educational cards ("Why It's Needed", "Powers Your Dashboard", "100% Local-First Privacy", "Separate from Accessibility"), headline, and action buttons when permission is ungranted.
6.  **Usage Gate Grant Action**: Tapping "Grant Usage Access" opens Android system usage access settings intent.
7.  **Usage Gate Recheck Action**: Tapping "Recheck Permission" evaluates platform permission status dynamically and returns fresh boolean.
8.  **Usage Gate Guidance Banner**: Displays actionable guidance banner when permission recheck fails ("Permission not detected yet. In Android Settings...").
9.  **Onboarding Repository Default**: Defaults to `hasCompletedWelcome = false` and `sessionType = guest` for new installs.
10. **Onboarding Repository Persistence**: Persists welcome completion state and session type (`standard` or `guest`) across app restarts.
11. **Navigation Initial Launch**: First-time launch without onboarding completed unconditionally routes to `Screen.Welcome`.
12. **Navigation Welcome Completed Without Permission**: User who finished Welcome but lacks Usage Access routes directly to `Screen.UsageAccessGate`.
13. **Navigation Returning User With Permission**: User who finished Welcome and granted Usage Access bypasses gates directly to `Screen.Main` (Home dashboard).
14. **Navigation Guest Transition**: Tapping "Continue as Guest" immediately transitions to `Screen.UsageAccessGate` if permission is missing.
15. **Navigation Get Started Transition**: Tapping "Get Started" immediately transitions to `Screen.UsageAccessGate` if permission is missing.
16. **Gate Visible Top Bar & Back Chevron**: Renders persistent `ScrollStopTopBar` with accessible back chevron (`contentDescription = "Navigate back"`, $\ge 48\text{dp}$ touch target).
17. **Gate Back Navigation (First-Time Onboarding - System Back)**: Pressing physical/system Back from Usage Access Gate during first-time onboarding returns to Welcome screen and resets welcome completion.
18. **Gate Back Navigation (First-Time Onboarding - Visible Back)**: Tapping visible back arrow from Usage Access Gate during first-time onboarding returns to Welcome screen and resets welcome completion.
19. **Gate Back Navigation (First-Time Onboarding - Return from Settings)**: Returning from Android Settings without granting permission preserves the Welcome origin, allowing the user to navigate back to Welcome.
20. **Gate Back Navigation (Returning User Revocation - System Back)**: Pressing system Back for a returning user with revoked permission cleanly finishes the activity (`activity.finish()`), minimizing/exiting the app.
21. **Gate Back Navigation (Returning User Revocation - Visible Back)**: Tapping visible back arrow for a returning user with revoked permission cleanly finishes the activity (`activity.finish()`), minimizing/exiting the app.
22. **Gate Back Security Invariant**: Neither back mechanism can navigate to Home without verified platform Usage Access.
23. **Recheck Permission (Missing)**: Recheck action with missing permission displays guidance banner and remains on gate.
24. **Recheck Permission (Granted)**: Recheck action with granted permission routes immediately to Main dashboard without showing guidance banner.
25. **Lifecycle ON_RESUME Auto-Routing**: Returning to app with granted permission automatically routes from gate to Main dashboard.
26. **Lifecycle ON_RESUME Revocation Redirect**: Background revocation of Usage Access immediately redirects from Main dashboard back to mandatory gate.

### Daily Midnight Reset Regression Test Suite (MidnightResetRegressionTest)

1.  **Regression 01**: Monitored app usage from previous day (prior to local midnight) is strictly excluded after midnight reset.
2.  **Regression 02**: Overall phone usage and per-app usage use the identical local calendar day boundary (`timeProvider.getStartOfDayMillis()`).
3.  **Regression 03**: Stale daily-limit enforcement triggers from previous days automatically expire and clear on the new day.
4.  **Regression 04**: An app blocked yesterday is immediately usable today once today's usage starts at 0 below the daily limit.
5.  **Regression 05**: Continuous foreground session active across midnight is accurately split at 00:00:00 local time, attributing only post-midnight duration to the new day.
6.  **Regression 06**: When new day's usage reaches the configured daily limit, the app is blocked automatically without restarting the app.
7.  **Regression 07**: Staged Commitment Lock limit increases activate at midnight boundary while preserving the lock itself.
8.  **Regression 08**: Staged Commitment Lock app removals activate at midnight boundary.
9.  **Regression 09**: Cancelled staged changes remain cancelled and do not take effect when midnight arrives.
10. **Regression 10**: App process death or AccessibilityService recreation after midnight does not restore yesterday's blocking state.
11. **Regression 11**: Manual date changes and timezone shifts (e.g. DST, travel) are dynamically handled without stale cached offsets.
12. **Regression 12**: Both 15-minute preset and custom-duration limit enforcement continue to function reliably.

### Phase 2: Refined Onboarding & Home Dashboard Test Suite (MainScreenHealthUiTest, MainViewModelTest & AppNavigationTest)

1. **Onboarding Flow - Welcome -> Usage Access Gate -> Optional Accessibility Consent -> Home**:
   - Fresh install displays Welcome screen ("STOP DOOM SCROLL", "Continue as Guest", "Get Started").
   - Tapping "Continue as Guest" routes to Usage Access Gate ("USAGE ACCESS REQUIRED").
   - Granting Usage Access and clicking "Recheck Permission" routes to Optional Accessibility Setup ("Accessibility Service Prominent Disclosure" with secondary "Not Now" button).
   - Tapping "Not Now" skips accessibility, marks `hasCompletedAccessibilityOnboarding = true`, and enters Home Dashboard in view-only mode.
   - Returning user with Usage Access lands directly on Home Dashboard, bypassing onboarding.
2. **Dashboard Health Card - Active Protection**: Renders "PROTECTION ACTIVE" banner with active app count when health is `PROTECTED` and apps have configured limits.
3. **Dashboard Health Card - Actionable Recovery**: Renders warning banner and dedicated recovery button for `USAGE_ACCESS_REQUIRED`, `ACCESSIBILITY_REQUIRED` ("Accessibility required — app blocking is inactive." with "Enable Accessibility" button), and `ACCESSIBILITY_INTERRUPTED`.
4. **Dashboard Health Card - Setup Required**: Renders `SETUP REQUIRED` state with direct "Configure Limits" button when 0 apps have configured limits.
5. **Today's Total Phone Usage Hero Metric**: Renders authoritative total device usage duration (`todayScreenTimeMs`), clear "Total phone usage today" label, and handles loading ("Calculating…") and permission required states. Comparison badges and device allowances removed.
6. **7-Day Bar Chart Integration**: Reusable bar chart renders solid bars for recorded days and honest "No record" for unrecorded days, with "X of 7 days recorded" badge.
7. **Top Five Apps Today**:
   - Queries today's usage for all discovered apps and populates `topApps` with apps having `todayUsageMs > 0`, sorted descending, capped at 5.
   - Monitored apps render green "Monitored" badge.
   - Unmonitored apps render "+ Add" action button ($\ge 48\text{dp}$ touch target). Tapping "+ Add" toggles package into monitored list.
   - "View All Apps" button navigates to Limits tab.
   - When no app usage is recorded today, renders clean empty state ("No application usage recorded today").
8. **Commitment Lock Card - Positioned Below Top Apps**: Displays active/inactive lock state, rules explanation, accessibility warning note if inactive, and allows enable, request midnight unlock, and cancel unlock actions.
9. **Commitment Lock Card - Staged Midnight Relaxations**: Renders dedicated pending changes container displaying all staged app removals and limit increases taking effect at midnight.
10. **Theme Engine & Appearance**: Supports Light, Dark, and System Default themes without hardcoded hex colors, with dialog theme switcher in TopBar.

### Phase 2 Protection Status & Accessibility Completion Logic Audit Test Suite

1. **Guided Setup Accessibility Predicate (`isStep3AccessibilityActive`)**:
   - Evaluates authoritatively to `true` ONLY when `healthState == ProtectionHealthState.PROTECTED`.
   - Explicitly evaluates to `false` for `USAGE_ACCESS_REQUIRED`, preventing false-positive completion checkmarks during onboarding or permission revocation.
   - Evaluates to `false` for `ACCESSIBILITY_REQUIRED`, `ACCESSIBILITY_INTERRUPTED`, and `DEGRADED`.
2. **Protection Status Accuracy - Selected Apps Without Configured Limits**:
   - When applications are selected but have no configured daily limits (`dailyLimitMs == null` or `0`), the dashboard must NOT claim `PROTECTION ACTIVE`.
   - Displays `SETUP REQUIRED` state with clear description: "Configure daily limits for your selected apps to enable protection." and direct "Configure Limits" CTA button.
3. **Monitored App Count Reflects Enforced Apps Only**:
   - Protection banner displays active limit count (`enforcedAppCount`) instead of simple selected app count (e.g. 3 apps selected but only 1 with configured limit displays "Enforcing daily limits for 1 selected apps.").
4. **Setup Completion Predicate (`isSetupComplete`)**:
   - Evaluates to `true` if and only if: `isStep1AppsSelected` (`selectedAppCount > 0`), `isStep2LimitsConfigured` (`enforcedAppCount > 0`), and `isStep3AccessibilityActive` (`healthState == PROTECTED`).

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
