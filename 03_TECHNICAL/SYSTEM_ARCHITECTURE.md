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

AccessibilityService → Rules Engine → Blocking Layer

## Responsibilities

### UsageStatsManager

Source of truth for usage measurements.

### Usage Engine

Transforms Android usage data into daily per-app usage and remaining
allowance.

### Rules Engine

Deterministic evaluation of user-configured limits and protection state.

### AccessibilityService

Narrow detection/enforcement mechanism for selected restricted packages.
It must not collect unrelated screen content, messages or keystrokes.

### Blocking Layer

Displays/enforces the user-facing blocked state.

## Architecture Principles

-   Keep enforcement independent of AI.
-   Minimize dependencies.
-   Prefer explicit state transitions.
-   Make permission/service failure states observable.
-   Keep domain rules testable without Android UI.
