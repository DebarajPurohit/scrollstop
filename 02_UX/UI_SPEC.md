# UI Specification

## Screens

1.  Welcome
2.  Permission Guide
3.  Usage Access Guidance
4.  Accessibility Guidance
5.  App Selection
6.  Limit Configuration
7.  Dashboard
8.  App Detail
9.  Blocking Screen
10. History
11. Protection Health
12. Settings

## Dashboard

Must show:
- Protection health status & recovery actions
- Commitment Lock status card (Active / Inactive, scheduled unlock notice, Enable / Unlock / Cancel buttons)
- Selected apps count & entry button
- View usage button
- Accessibility guidance button
- Development status card (with `[DEBUG TEST] Reset Commitment Lock` button in debug builds only)

## Commitment Lock UI Elements

- **Consent Dialog:** Requires checkbox confirmation: "I understand and commit to my limits until midnight", explains restrictions cannot be relaxed today.
- **Unlock Confirmation Dialog:** Explains unlock takes effect at next local midnight reset (12:00 AM).
- **App Selection Screen:** Status banner showing locked state; unselecting protected apps shows notice and marks app with staged removal tag instead of immediate unselect; adding apps allowed immediately.
- **Usage Screen:** Limit configuration dialog; tightening applies immediately; relaxation shows deferred notice and stages limit for next midnight.

## Blocking Screen

Must clearly state: - App is currently blocked - Configured limit has
been reached - When the allowance resets (midnight reset) -
Protection/permission status if relevant

## UX Principles

-   Clear language
-   Minimal friction during setup
-   No deceptive UI
-   Accessible text and controls
-   Visible recovery path when protection is paused
