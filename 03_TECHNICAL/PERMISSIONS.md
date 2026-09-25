# Permissions

## Usage Access

Purpose: Read application usage statistics required to calculate daily
usage.

User action: User grants Usage Access through Android settings.

Failure: Protection is paused and the app explains how to restore
access.

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

