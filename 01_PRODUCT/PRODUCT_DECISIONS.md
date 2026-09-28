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

### Change Log

  Date         Change                       Reason
  ------------ ---------------------------- ------------------
  2026-09-24   Initial decisions recorded   Project baseline
  2026-09-28   Recorded PD-005              POC-03 Rules Engine feasibility

