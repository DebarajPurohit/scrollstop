# Product Decisions

## Decision Log

### PD-001 --- Product Positioning

**Status:** Approved\
**Decision:** Position as an anti-doom-scrolling commitment tool, not a
generic screen-time dashboard.\
**Reason:** The core value is enforcement of user-defined limits.

### PD-002 --- Local-first MVP

**Status:** Approved\
**Decision:** No accounts or cloud sync in MVP.\
**Reason:** Reduce complexity and data collection while validating the
core product.

### PD-003 --- AI Deferred

**Status:** Approved\
**Decision:** AI is not part of core enforcement or MVP.\
**Reason:** Enforcement must remain deterministic and reliable.

### PD-004 --- Commitment Lock

**Status:** Approved for MVP validation\
**Decision:** Include Commitment Lock in the enforcement/product
validation phase.\
**Reason:** It directly supports the product's commitment proposition.

### PD-005 --- Deterministic Rules Engine Feasibility & Temporary 2-Minute Limits

**Status:** Approved (POC-03)\
**Decision:** Evaluate daily usage against user-configured daily limits using a pure, deterministic domain-level rules engine (`UsageRuleEngine`). For POC-03 feasibility validation, adopt a 2-minute temporary daily limit per selected application.\
**Reason:** Ensures enforcement decision logic is 100% testable, pure, and decoupled from Android framework APIs and UI layers before implementing AccessibilityService enforcement.

### PD-006 --- AccessibilityService Narrow Foreground Detection Trigger

**Status:** Approved (POC-04)\
**Decision:** AccessibilityService is utilized strictly as a narrow, deterministic foreground-app entry detection trigger for the user's selected restricted apps, requiring explicit in-app prominent disclosure and affirmative consent.\
**Reason:** Stop Doom Scroll is NOT an accessibility tool for users with disabilities. AccessibilityService is used only to extract `packageName` on `TYPE_WINDOW_STATE_CHANGED` events to evaluate `UsageRuleEngine` limit states, without inspecting screen content, text, passwords, or keystrokes.

### PD-007 --- POC-05 Deterministic Blocking UI & Activity Enforcement Loop

**Status:** Approved (POC-05)\
**Decision:** When `RestrictedAppAccessibilityService` detects entry into a selected restricted package and `UsageRuleEngine` returns `LIMIT_REACHED`, `RestrictedAppAccessibilityService` emits an `EnforcementTrigger` and immediately launches Stop Doom Scroll's `BlockingScreen` (`MainActivity`) with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP`. The blocking screen presents "Time's up", app label, today's usage, daily limit, and reset notice. Pressing Back safely retains the user within Stop Doom Scroll dashboard without exposing the restricted app.\
**Reason:** Proves feasibility of real-time activity foreground blocking while adhering strictly to Google Play Accessibility policies and avoiding forbidden anti-patterns (e.g. `SYSTEM_ALERT_WINDOW` overlays, screen scraping, password modification, device admin abuse).

### PD-008 --- Live Limit-Crossing Continuous Monitoring (POC-05A)

**Status:** Approved (POC-05A)
**Decision:** When a selected restricted app is opened below its daily limit and remains continuously in the foreground, `RestrictedAppAccessibilityService` starts a lifecycle-safe live monitoring loop via `LiveLimitEnforcementEngine` that re-evaluates authoritative `UsageStatsManager` usage against `UsageRuleEngine` every 5 seconds. When the accumulated usage crosses the daily limit, `EnforcementTrigger` is emitted, triggering `BlockingScreen` without requiring the user to leave or reopen the app.
**Reason:** Resolves physical test limitation where continuous foreground app usage was not blocked until a subsequent window transition. Establishes live limit-crossing enforcement while preserving `UsageStatsManager` as the sole authority and avoiding high-frequency polling.

### Change Log

  Date         Change                       Reason
  ------------ ---------------------------- ------------------
  2026-09-24   Initial decisions recorded   Project baseline
  2026-09-28   Recorded PD-005              POC-03 Rules Engine feasibility
  2026-09-29   Recorded PD-006              POC-04 AccessibilityService narrow trigger
  2026-10-08   Recorded PD-007              POC-05 Blocking UI & Enforcement Loop
  2026-10-08   Recorded PD-008              POC-05A Live Limit-Crossing Enforcement
