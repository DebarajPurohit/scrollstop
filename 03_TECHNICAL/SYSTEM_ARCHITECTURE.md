# System Architecture

## Stack

-   Kotlin
-   Jetpack Compose
-   MVVM / pragmatic clean architecture
-   Room/SQLite
-   DataStore
-   Coroutines
-   Navigation
-   WorkManager where appropriate
-   UsageStatsManager
-   AccessibilityService

## Core Flow

UsageStatsManager → Usage Engine → Rules Engine → UI

AccessibilityService (Foreground Window State Detection)
  → LiveLimitEnforcementEngine (Immediate check + Scoped 5s loop while restricted app is foreground)
  → UsageStatsManager (Authoritative query)
  → Rules Engine (Evaluate limit)
  → EnforcementTrigger
  → Blocking Layer (MainActivity BlockingScreen)

## Responsibilities

### UsageStatsManager

Single source of truth for accumulated usage measurements. No duplicate usage counter is maintained.

### Usage Engine

Transforms Android usage data into daily per-app usage and remaining
allowance.

### Rules Engine

Deterministic evaluation of user-configured limits and protection state (`UsageRuleEngine`). Single source of truth for `LIMIT_REACHED`.

### AccessibilityService & LiveLimitEnforcementEngine

Narrow detection and live monitoring mechanism for selected restricted packages:
- Detects foreground package changes via `TYPE_WINDOW_STATE_CHANGED`.
- Scopes continuous 5s UsageStats queries strictly to active foreground restricted app.
- Stops immediately upon app switch, unselected app, self-package, or service interruption.
- Strictly does not collect unrelated screen content, text, passwords, or keystrokes.

### Blocking Layer

Displays/enforces the user-facing blocked state (`BlockingScreen`).

## Architecture Principles

-   Keep enforcement independent of AI.
-   Minimize dependencies.
-   Prefer explicit state transitions.
-   Make permission/service failure states observable.
-   Keep domain rules testable without Android UI.
