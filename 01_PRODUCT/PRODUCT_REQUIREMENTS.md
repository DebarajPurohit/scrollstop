# Product Requirements

## Functional Requirements

### PR-001 Onboarding

Explain the product, required permissions, protection limitations and
setup steps.

### PR-002 App Selection

Show eligible user-selectable apps and allow the user to choose
restricted apps.

### PR-003 Daily Limits

Allow a user to configure a daily time limit per selected app.

### PR-004 Usage

Calculate today's usage and remaining allowance.

### PR-005 Warnings

Warn before the configured limit is reached.

### PR-006 Enforcement

When a restricted app is accessed after its limit is reached, enforce
the predefined blocking rule.

### PR-007 Daily Reset

Start a new allowance at the user's local calendar day boundary.

### PR-008 Dashboard

Show current protection state, selected apps, usage and remaining time.

### PR-009 History

Show at least 7 days of usage/limit history.

### PR-010 Commitment Lock

Provide a stronger user-configured commitment mode while respecting
Android and Play constraints.

### PR-011 Health

Detect important permission/service failures and clearly show when
protection is paused.

## Non-functional Requirements

-   Deterministic core enforcement
-   Local-first MVP
-   Minimal permissions/data
-   Robust lifecycle handling
-   Clear recovery states
-   No collection of messages, keystrokes or unrelated screen contents
-   Test representative Android OEMs and supported OS versions

## Acceptance Principle

A feature is not complete merely because it compiles; it must satisfy
functional, edge-case, security/privacy, performance and regression
tests.
