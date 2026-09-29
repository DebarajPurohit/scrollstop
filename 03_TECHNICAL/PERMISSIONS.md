# Permissions

## Usage Access

Manifest declaration: `android.permission.PACKAGE_USAGE_STATS` (with `tools:ignore="ProtectedPermissions"`).

Purpose: Read application usage statistics required to calculate daily foreground usage for user-selected applications to enforce user-defined limits.

User-facing explanation: "Stop Doom Scroll needs Usage Access to measure how long you use the apps you choose to control."

User action: User grants Usage Access through Android settings via `Settings.ACTION_USAGE_ACCESS_SETTINGS`.

State detection: Verified at runtime using `AppOpsManager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, ...)` returning `AppOpsManager.MODE_ALLOWED`.

Failure: Usage tracking is halted, UI transitions to a dedicated permission-required state, and provides a direct recovery action to open Usage Access Settings.

## Accessibility Service

Manifest declaration: `android.permission.BIND_ACCESSIBILITY_SERVICE` on `RestrictedAppAccessibilityService`.

Purpose: Detect entry into user-selected restricted applications on `TYPE_WINDOW_STATE_CHANGED` events to evaluate predefined rules and produce deterministic enforcement triggers.

Non-Accessibility Tool Disclosure: Stop Doom Scroll is NOT an accessibility tool for users with disabilities (`isAccessibilityTool="true"` is NOT set).

Explicit Consent: Prominent disclosure screen (`AccessibilityConsentScreen`) must be displayed and affirmative consent given BEFORE directing the user to `Settings.ACTION_ACCESSIBILITY_SETTINGS`.

Restrictions:
- `canRetrieveWindowContent="false"` explicitly enforced in XML and service configuration.
- No unrelated screen-content collection.
- No messages, passwords, text, or keystroke collection.
- No autonomous actions, gestures, touch exploration, or system settings modification.
- Package filtering dynamically restricted to user-selected package set.

Service Health States:
- `ACCESSIBILITY_NOT_GRANTED`: Service disabled in settings or permission missing. Protection is paused.
- `ACCESSIBILITY_ACTIVE`: Service connected and actively evaluating foreground app entries.
- `ACCESSIBILITY_INTERRUPTED`: Service connected but temporarily interrupted by system. Protection is paused.

User action: User reads prominent disclosure and explicitly clicks consent button to open Android Accessibility settings.

Failure / Service Interruption: System detects status as `ACCESSIBILITY_NOT_GRANTED` or `ACCESSIBILITY_INTERRUPTED`, pauses protection, and displays a prominent recovery button to re-enable service.

## Package Visibility

App discovery uses narrow launcher intent query visibility (`ACTION_MAIN` + `CATEGORY_LAUNCHER`) declared in `<queries>` within `AndroidManifest.xml`.
This approach avoids `QUERY_ALL_PACKAGES` permission and ensures strict Play Store compliance while discovering user-launchable apps.

## Principle

Every permission must have:
1. Clear user-facing explanation
2. Specific product purpose
3. Failure state
4. Recovery flow
5. Required Play disclosure/handling
