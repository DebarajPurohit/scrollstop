# Data Classification

## User Configuration

Examples: selected package names, limits, Commitment Lock state.
Classification: Product configuration. Handling: Local storage; minimize
exposure.

## Usage Data

Examples: per-app usage duration, date, blocked events. Classification:
Sensitive personal usage information. Handling: Local-first; minimal
retention; no unnecessary sharing.

## Permission State

Examples: Usage Access enabled, Accessibility Service enabled.
Classification: Device/protection state. Handling: Local; used for
health checks.

## Diagnostics

Examples: crash/technical events. Classification: Technical telemetry.
Handling: Minimize; avoid usage-content details unless strictly
necessary.

## Rule

If data is not necessary for a product requirement, do not collect it.
