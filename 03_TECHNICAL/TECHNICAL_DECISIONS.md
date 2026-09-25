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
