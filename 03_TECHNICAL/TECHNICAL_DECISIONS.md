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

### TD-011 --- Play-Compliant Activity Launching & Back Navigation Isolation for Blocking Loop

**Status:** Approved (POC-05)\
**Decision:**
1. On `LIMIT_REACHED` event in `RestrictedAppAccessibilityService`, invoke `startActivity(Intent(this, MainActivity::class.java))` with flags `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP` carrying `EXTRA_BLOCKED_PACKAGE`.
2. `MainActivity` handles `onNewIntent` to pass the blocked package to `AppNavigation`, rendering `BlockingScreen`.
3. `BlockingViewModel` loads empirical app label, today's usage, daily limit, and limit state directly from trusted repositories (`UsageStatsRepository`, `DailyLimitRepository`, `AppDiscoveryRepository`) and `UsageRuleEngine`.
4. `BlockingScreen` attaches `BackHandler` so pressing Back navigates to Stop Doom Scroll dashboard (`Screen.Main`), preventing return to the restricted application.
5. Explicitly avoids `SYSTEM_ALERT_WINDOW` overlays, screen node scraping, click automation, background timers, or device-admin API abuse.\
**Reason:** Complies fully with Google Play policies regarding AccessibilityServices launching their own Activity from background when handling user accessibility events, while establishing a deterministic, verifiable blocking loop.

### TD-012 --- Scoped Foreground Polling for Live Limit-Crossing Detection (POC-05A)

**Status:** Approved (POC-05A)
**Decision:**
1. Introduce `LiveLimitEnforcementEngine` in `domain.detection` to manage lifecycle-safe continuous foreground monitoring for selected restricted apps.
2. Architecture & Lifecycle rules:
   - On `TYPE_WINDOW_STATE_CHANGED` event in `RestrictedAppAccessibilityService`:
     - If the package is the Stop Doom Scroll self-package, cancel active monitoring immediately.
     - If the package is unselected, cancel active monitoring immediately.
     - If the package is already actively being monitored, ignore redundant events to prevent duplicate jobs.
     - Perform an immediate evaluation against `UsageStatsRepository` and `UsageRuleEngine`. If already over limit, emit `EnforcementTrigger` and launch `BlockingScreen` immediately (preserving POC-05 entry blocking).
     - If the app is within limit, start a scoped coroutine loop.
3. Interval Determination: Set check interval to **5,000 ms (5 seconds)**.
   - *Rationale*: A 5-second interval guarantees limit enforcement within 0–5 seconds of boundary crossing. With at most 12 lightweight IPC calls per minute strictly while inside a restricted app, battery overhead is negligible (<0.1% daily). Zero polling, zero wakeups, and zero background jobs run when the user is outside selected restricted apps or on the home screen.
4. Single Source of Truth: `UsageStatsManager` remains the sole authoritative source of usage data, and `UsageRuleEngine` remains the single source of truth for limit evaluation. No secondary usage timer, countdown, or local counter is maintained.
5. Immediate Cancellation: The monitoring coroutine job is cancelled immediately when:
   - User switches to any other application (selected or unselected) or launcher.
   - User navigates back to Stop Doom Scroll dashboard or blocking screen.
   - AccessibilityService is interrupted (`onInterrupt`), unbound (`onUnbind`), or destroyed (`onDestroy`).
   - Limit breach occurs and `EnforcementTrigger` is emitted.
6. Thread Safety: All state transitions (`currentMonitoredPackage`, `monitoringJob`) are guarded by an internal synchronization lock, preventing race conditions during rapid app switching.
**Reason:** Resolves physical device limitation where an in-use app exceeding its daily limit during continuous foreground usage would not be blocked until an explicit window state event occurred.

### TD-013 --- UsageStats Freshness via UsageEvents & Transient Window Isolation (POC-06 Fix)

**Status:** Approved (POC-06 Investigation & Fix)
**Decision:**
1. Authoritative Usage Freshness: `AndroidUsageStatsRepository` combines `UsageStatsManager.queryAndAggregateUsageStats(startTime, endTime)` with session-accurate interval reconstruction using `UsageStatsManager.queryEvents(startTime, endTime)`.
   - *Rationale*: Android's `UsageStatsDatabase` buffers usage events and only commits `totalTimeInForeground` when an application moves to the background or during periodic OS sync intervals (15–30+ minutes). For an application used continuously in the foreground starting at 0 usage (such as Amazon in POC-06 testing), `queryAndAggregateUsageStats` returns 0 ms until the app is closed.
   - *Fix*: Reconstruct active foreground sessions from `UsageEvents.Event.ACTIVITY_RESUMED` / `ACTIVITY_PAUSED` / `ACTIVITY_STOPPED`, tracking active activity sets per package to handle multi-activity transitions safely without premature session termination. The repository yields `maxOf(aggregatedMs, eventsCalculatedMs)`, ensuring `UsageStatsManager` remains the single authoritative source of truth with sub-second accuracy for ongoing foreground sessions.
2. Transient System & IME Window Isolation:
   - While `LiveLimitEnforcementEngine` is actively monitoring a foreground restricted application, window state events originating from transient system overlays (`"android"`, `"com.android.systemui"`) and active input methods (IMEs / soft keyboards such as Gboard, Samsung Keyboard) are recognized as transient windows.
   - The engine ignores transient system/IME events without cancelling the active monitoring coroutine, preserving continuous enforcement when users type in search bars or system UI elements appear.
   - Switching to the home screen launcher or any unselected third-party application cancels active monitoring immediately.
3. Service Lifecycle & Scope Synchronization:
   - `ServiceLocator.getLiveLimitEnforcementEngine` validates coroutine scope activity (`scope.isActive`), recreating the engine with a fresh, active scope if the previous service instance was destroyed.
   - `RestrictedAppAccessibilityService.onDestroy()` explicitly invokes `ServiceLocator.resetLiveLimitEnforcementEngine()`.
4. Dev Diagnostics Enhancement:
   - Persist and display timestamped monitoring cycle fields (`lastMonitoringTimestamp`, `lastMonitoringPackage`, `lastMonitoringActive`, `lastMonitoringUsageMs`, `lastMonitoringLimitMs`, `lastMonitoringRuleState`, `lastMonitoringTriggerEmitted`, `lastMonitoringLaunchAttempted`) in `SharedPreferencesEnforcementTriggerRepository` and on the Dev Diagnostic card.
**Reason:** Eliminates live enforcement failure on physical hardware where continuous usage of a 0-usage app failed to trigger live limit crossing due to Android OS aggregation buffering and transient window cancellations.

### TD-014 --- Multi-Layered IME Detection & Multi-Activity Ongoing Session Preservation (POC-06 Follow-up)

**Status:** Approved (POC-06 Follow-up)
**Decision:**
1. Manifest Package Visibility for IMEs:
   - Added `<intent><action android:name="android.view.InputMethod" /></intent>` to the `<queries>` element in `AndroidManifest.xml`.
   - On Android 11+ (API 30+), package visibility restrictions prevent applications from querying third-party or OEM keyboards that do not directly interact with the app. Declaring the `InputMethod` action grants visibility across `PackageManager` and `InputMethodManager`.
2. Multi-Layered `ImePackageDetector`:
   - Created `com.scrollstop.util.ImePackageDetector` encapsulating six verification layers:
     - Fast O(1) checks for system UI overlays (`android`, `com.android.systemui`, `*.systemui`).
     - Signatures for dominant Android keyboards (Gboard, Samsung Honeyboard, SwiftKey, etc.).
     - Generic package name heuristics (`inputmethod`, `latinime`, `keyboard`, `*.ime`).
     - Dynamic `InputMethodManager.getEnabledInputMethodList` & `getInputMethodList` with 10-second cache.
     - Dynamic `PackageManager.queryIntentServices(Intent("android.view.InputMethod"), 0)`.
     - `Settings.Secure.ENABLED_INPUT_METHODS` and `DEFAULT_INPUT_METHOD` parsing.
   - Updated `ServiceLocator.getLiveLimitEnforcementEngine` to propagate `isTransientPackage` to existing cached engine instances.
3. Session-Preserving Activity Transition Handling in `AndroidUsageStatsRepository`:
   - Removed faulty `(!removed && activeSet.size == 1)` condition in `calculateUsageFromEvents`. When an app transitions internally (e.g. Amazon Home -> SearchActivity), the previous activity emits `ACTIVITY_STOPPED` after `ACTIVITY_PAUSED`. Removing an already-paused activity now safely leaves the active set intact.
   - The ongoing session is preserved until all activities of the package are paused/stopped or the app is closed.
**Reason:** Fixes physical device regression where opening the soft keyboard in Amazon (e.g., search bar) terminated live usage monitoring or froze usage accumulation.
