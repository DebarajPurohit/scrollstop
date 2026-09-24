# Development Rules

## Workflow

Inspect → Plan → Implement → Build → Test → Review → Fix → Retest →
Document.

## Before Major Changes

-   Inspect existing code
-   Read relevant specification files
-   Identify affected systems
-   Check security/privacy implications
-   Minimize dependencies and scope

## Coding

-   Kotlin idioms
-   Clear naming
-   Small testable components
-   Deterministic domain logic
-   Avoid unnecessary abstraction
-   Handle lifecycle and failure states explicitly

## Testing

Compilation is not sufficient. Critical enforcement paths require
automated and real-device testing.

## Documentation

If a change affects product, UX, database, security, testing,
architecture, roadmap or decisions, update the relevant source-of-truth
document in the same change.

## Definition of Done

Code implemented + build passes + relevant tests pass + critical edge
cases reviewed + security/privacy checked + documentation updated.
