# Technical Decisions

### TD-001 --- UsageStatsManager as Source of Truth

**Status:** Approved\
**Decision:** UsageStatsManager is the authoritative usage source.\
**Reason:** It is the Android platform mechanism for application usage
statistics.

### TD-002 --- AccessibilityService for Enforcement

**Status:** Approved for feasibility validation\
**Decision:** Use a narrowly scoped AccessibilityService to detect
access to selected restricted apps and enforce predefined rules.\
**Reason:** It provides the required enforcement trigger.

### TD-003 --- Local-first

**Status:** Approved\
**Decision:** Store MVP data locally.\
**Reason:** Avoid unnecessary accounts/cloud infrastructure and reduce
data collection.

### TD-004 --- Deterministic Rules

**Status:** Approved\
**Decision:** Core blocking decisions are deterministic and
user-configured.\
**Reason:** Reliability, testability, transparency and compliance.

### TD-005 --- No Password Modification

**Status:** Approved\
**Decision:** Never attempt to change or control another app's
password/security credentials.\
**Reason:** Security and product boundaries.

### TD-006 --- Android Build Stack & Minimum Target APIs

**Status:** Approved\
**Decision:** Standardize on Application ID `com.scrollstop`, MinSDK 26 (Android 8.0), TargetSDK/CompileSDK 36 (Android 16), Kotlin 1.9.23, Gradle 8.7, and Jetpack Compose with Material 3.\
**Reason:** Ensures compliance with Google Play Store target API policies requiring target API 36 (Android 16) or higher as of August 31, 2026, while supporting over 95% of active Android devices and providing full compatibility with `UsageStatsManager` and `AccessibilityService` APIs.

### TD-007 --- Launcher Intent Package Visibility & Intent Resolution Flags for App Discovery

**Status:** Approved\
**Decision:** Implement app discovery using `Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)` with `PackageManager.queryIntentActivities(intent, 0)` (flag `0`), backed by explicit `<queries>` launcher declaration in `AndroidManifest.xml` without requesting `QUERY_ALL_PACKAGES` permission.\
**Reason:** Complies with Android 11+ (API 30+) package visibility requirements and Google Play Store policy. Using flag `0` instead of `MATCH_DEFAULT_ONLY` (`0x10000`) is essential because launcher activities in major third-party applications (e.g. Instagram `com.instagram.android`, Facebook `com.facebook.katana`) declare `ACTION_MAIN` + `CATEGORY_LAUNCHER` without `CATEGORY_DEFAULT` in their main launcher intent-filter. Flag `0` matches all user-launchable activities identically to Android Home Launchers while preserving strict Play Store policy compliance.

### TD-008 --- UsageStatsManager Today Usage Query & Time Provider Abstraction Pattern

**Status:** Approved (POC-02B)\
**Decision:** Query today's foreground usage via `UsageStatsManager.queryAndAggregateUsageStats(beginTime, endTime)` where `beginTime` is local midnight 00:00:00 of the device's current timezone and `endTime` is `System.currentTimeMillis()`. Abstract time calculation into `TimeProvider` and UsageStats access into `UsageStatsRepository`.\
**Reason:** `UsageStatsManager` provides the authoritative platform total foreground time metric (`UsageStats.totalTimeInForeground`). Using local midnight respects user calendar days without hardcoding UTC. Abstracting both time calculation and `UsageStatsManager` API calls allows 100% deterministic unit testing without depending on physical hardware or test device state.

### TD-009 --- Pure Domain-Level Rules Engine Architecture & In-Memory Limit Repository

**Status:** Approved (POC-03)\
**Decision:** Implement `UsageRuleEngine` as a pure Kotlin component in `com.scrollstop.domain.rules` with zero dependencies on Android framework (`android.*`), Jetpack Compose (`androidx.compose.*`), wall-clock time, timers, background loops, or network calls. The engine takes `packageName`, `usedDurationMs`, and `limitDurationMs` and returns a deterministic `LimitEvaluationResult` with `LimitState` (`NOT_STARTED`, `WITHIN_LIMIT`, `LIMIT_REACHED`). Daily limits are configured via `DailyLimitRepository` (defaulting to 2 minutes / 120,000 ms for POC feasibility).\
**Reason:** Decoupling rules evaluation into a pure domain layer guarantees 100% reproducible unit testing, millisecond precision preservation, and zero risk of UI/Android framework side effects before integrating enforcement/blocking triggers.

### TD-010 --- Narrow AccessibilityService Architecture, SharedPreferences Persistence, & Event Filtering

**Status:** Approved (POC-04)\
**Decision:** 
1. Implement `RestrictedAppAccessibilityService` configured with `TYPE_WINDOW_STATE_CHANGED` events only and `canRetrieveWindowContent="false"`.
2. Persist selected apps and limits via `SharedPreferencesSelectedAppsRepository` and `SharedPreferencesDailyLimitRepository` so background services access the same state as UI ViewModels.
3. On window state event, extract `event.packageName` ONLY, check selected packages, query `UsageStatsRepository`, and evaluate using `UsageRuleEngine`. If `LIMIT_REACHED`, emit `EnforcementTrigger`.
4. Dynamically update package filtering in `setServiceInfo(...)` based on selected packages flow.
5. Provide `AccessibilityServiceHealthRepository` to observe service states (`ACCESSIBILITY_NOT_GRANTED`, `ACCESSIBILITY_ACTIVE`, `ACCESSIBILITY_INTERRUPTED`).\
**Reason:** Strict privacy and Play Store compliance. Does NOT inspect window text, view hierarchy, messages, passwords, or keystrokes. Does NOT run background loops or duplicate usage timing.

### TD-010B --- Persistent EnforcementTriggerRepository & System-Level Accessibility Event Delivery Fix (POC-04 Debug)

**Status:** Approved (POC-04 Debug)\
**Decision:**
1. Replace `InMemoryEnforcementTriggerRepository` with `SharedPreferencesEnforcementTriggerRepository` so emitted `EnforcementTrigger` state persists across Android Activity lifecycles, backgrounding, UI recreation, and process restarts.
2. In `RestrictedAppAccessibilityService`, set `AccessibilityServiceInfo.packageNames = null` so system_server unconditionally delivers `TYPE_WINDOW_STATE_CHANGED` events for all foreground app window changes. `RestrictedAppDetectionEngine` performs safe, instant O(1) package filtering in Kotlin without relying on fragile OS-level dynamic `setServiceInfo` package list mutation.
3. Add minimal, privacy-compliant debug logging via `android.util.Log` tracing event receipt, package name, selection check, rule evaluation, trigger emission, and UI receipt without logging screen text, node info, passwords, or keystrokes.\
**Reason:** Resolves physical device failure where dynamic `setServiceInfo` package list updates failed at the OS system_server level and in-memory trigger state was dropped when the app UI was backgrounded or process was recreated.
