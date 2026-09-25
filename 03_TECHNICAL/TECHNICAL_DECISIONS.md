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


