# Security Rules

## Data Minimization

Collect/store only what is required for usage tracking, limits,
enforcement and history.

## AccessibilityService Privacy & Security Boundaries

AccessibilityService (`RestrictedAppAccessibilityService`) is restricted by strict security rules:

**EXPLICITLY ALLOWED ACCESS:**
- `event.packageName` string on `TYPE_WINDOW_STATE_CHANGED` events.
- Verification of package against user-selected restricted package set.
- Emission of internal deterministic `EnforcementTrigger` when `UsageRuleEngine` evaluates `LIMIT_REACHED`.

**EXPLICITLY PROHIBITED ACCESS (Enforced by Configuration & Architecture):**
- Screen text extraction or window node inspection (`canRetrieveWindowContent="false"`).
- Reading user messages, chat content, emails, or personal documents.
- Capturing passwords, credentials, PINs, or sensitive fields.
- Keylogging or keystroke collection (`canRequestFilterKeyEvents="false"`).
- Touch exploration or screen gestures (`canPerformGestures="false"`).
- Screenshots or screen recording.
- Performing autonomous clicks, taps, or navigation inside third-party apps.
- Modifying system settings or device configuration.
- Transmitting accessibility event data over network (0 network permissions declared).

## Credentials

Never access, modify or store another application's passwords or
authentication data.

## Logging

Do not log sensitive usage details unnecessarily. Log only sanitized package names, event type, and deterministic state. Prefer no persistent logging.

## Local Storage

Protect stored data using Android platform security mechanisms (`SharedPreferences`). Avoid unnecessary export/share paths.

## Privacy

Explain data access and permission purpose in a prominent in-app disclosure before requesting sensitive capabilities. Require explicit affirmative consent.

## Local-First Onboarding & Optional Session Policy

- **Session Choice**: Users may choose "Continue with Google" or "Continue as Guest".
- **Zero Mandatory Authentication**: Account creation is never required to use any core functionality.
- **Strict Local-First Boundary**: Both Guest and Google session identifiers are stored strictly in local device storage (`SharedPreferencesOnboardingRepository`). No remote telemetry, analytics, cloud profile, or server accounts are created.
- **Usage Access Gate Isolation**:
  - Usage Access (`PACKAGE_USAGE_STATS`) is requested strictly for local elapsed time and session duration calculations on the client.
  - Usage data is never transmitted across network or exported to third parties.
  - The app declares 0 network permissions (`android.permission.INTERNET` is absent).
  - Users cannot bypass the Usage Access gate to access the dashboard or app limits. Revocation of Usage Access while backgrounded immediately diverts the app back to the gate screen upon resume.

## Enforcement

Blocking decisions must be based on explicit local rules evaluated by `UsageRuleEngine`, not hidden heuristics, AI, or background timers.

## Release

Privacy policy, Data Safety information and Play declarations must match actual implementation. Never set `isAccessibilityTool="true"`.
