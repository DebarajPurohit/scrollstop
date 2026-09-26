# Permissions

## Usage Access

Manifest declaration: `android.permission.PACKAGE_USAGE_STATS` (with `tools:ignore="ProtectedPermissions"`).

Purpose: Read application usage statistics required to calculate daily foreground usage for user-selected applications to enforce user-defined limits.

User-facing explanation: "Stop Doom Scroll needs Usage Access to measure how long you use the apps you choose to control."

User action: User grants Usage Access through Android settings via `Settings.ACTION_USAGE_ACCESS_SETTINGS`.

State detection: Verified at runtime using `AppOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, ...)` returning `AppOpsManager.MODE_ALLOWED`.

Failure: Usage tracking is halted, UI transitions to a dedicated permission-required state, and provides a direct recovery action to open Usage Access Settings.

## Accessibility Service

Purpose: Detect access to selected restricted applications and enforce
predefined blocking rules.

Restrictions: - No unrelated screen-content collection - No messages -
No keystroke collection - No autonomous decisions - No unrelated
monitoring

User action: User explicitly enables the service.

Failure: Protection is paused and the app provides a recovery path.

## Package Visibility

App discovery uses narrow launcher intent query visibility (`ACTION_MAIN` + `CATEGORY_LAUNCHER`) declared in `<queries>` within `AndroidManifest.xml`.
This approach avoids `QUERY_ALL_PACKAGES` permission and ensures strict Play Store compliance while discovering user-launchable apps.

## Principle

Every permission must have: 1. Clear user-facing explanation 2. Specific
product purpose 3. Failure state 4. Recovery flow 5. Required Play
disclosure/handling

