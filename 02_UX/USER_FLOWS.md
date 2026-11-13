# User Flows

## 1. Welcome and Mandatory Usage Access Entry Flow

```
[App Launched]
      │
      ├──────────────────────────────┬──────────────────────────────┐
      ▼                              ▼                              ▼
[First Launch: Welcome Screen]  [Returning User: Standard] [Returning User: Guest]
      │                              │                              │
      ├───────────────┐              │                              │
      ▼               ▼              │                              │
[Get Started]   [Continue as         │                              │
                   Guest]            │                              │
      │               │              │                              │
      └───────┬───────┘              │                              │
              ▼                      │                              │
[Usage Access Permission Gate] ◄─────┴──────────────────────────────┘
  (Verify Usage Access Status)
      │
      ├─── Already Granted ───────────► [Home Dashboard]
      │
      └─── Not Granted ───────────────► [Usage Access Explanation & Permission Screen]
                                              │
                                              ├── Tap "Open Settings" ──► Android System Settings
                                              │                                 │
                                              │   (User returns from Settings)  │
                                              │   ◄─────────────────────────────┘
                                              │
                                              ├── Auto-Verify on ON_RESUME / Tap "Check Again"
                                              │         │
                                              │         ├── Granted ───► [Home Dashboard]
                                              │         └── Denied ────► Remain on screen (Show error/guidance)
                                              │
                                              └── Cannot bypass or enter Home until granted
```

### Step-by-Step Flow:
1. **Welcome Screen**:
   - Choice A: **Get Started** — Initiates standard offline protection setup without implying remote authentication.
   - Choice B: **Continue as guest** — Instant guest session initialization without an account.
2. **Mandatory Usage Access Gate**:
   - Immediately following either choice (and on subsequent app launches if Usage Access is missing), the application routes to the **Usage Access Permission Screen**.
   - **Gating Rule**: The user **must** grant Usage Access before entering Home.
   - **Explanation Provided**: Clearly explains that Stop Doom Scroll requires Usage Access strictly on-device to measure daily screen time and compute 7-day usage trends. Reassures user that data remains private and local.
   - Action: "Grant Usage Access" deep-links directly to `Settings.ACTION_USAGE_ACCESS_SETTINGS`.
3. **Verification on Return & Optional Accessibility Setup**:
   - On returning from Android Settings (via lifecycle `ON_RESUME`), the app immediately queries `AppOpsManager` to verify permission state.
   - An explicit "Recheck Permission" action is also provided.
   - If granted on first-time onboarding: Transitions to the **Accessibility Setup Screen** with a secondary "Not Now" action ($\ge 48\times 48\text{dp}$).
     - If the user chooses "Not Now", onboarding records completion (`hasCompletedAccessibilityOnboarding = true`) and transitions into the **Home Dashboard** in view-only mode.
     - If the user enables the service, onboarding records completion and transitions to the **Home Dashboard** with active protection.
   - If NOT granted: App remains on the permission screen, displays clear guidance on how to enable the toggle in Android Settings, and refuses navigation into Home.
4. **Returning Users**:
   - If a returning user (Standard or Guest) already has Usage Access granted, they bypass onboarding and gate screens and land directly on the Home Dashboard.
   - If a returning user previously revoked Usage Access in Android Settings, they are caught by the gate on resume and must re-grant it before accessing Home.
5. **Back Navigation Rules at Permission Gate (Visible UI & Android System Back)**:
   - **Visible Back Arrow**: A persistent top navigation bar with an accessible 48x48dp back arrow chevron is rendered on the Usage Access Gate.
   - **First-time Onboarding**: Both tapping the visible back arrow and pressing Android system Back (physical/gesture) safely return the user to the Welcome screen and reset welcome completion (`setWelcomeCompleted(false)`) so they can re-select their entry preference. Returning from Android Settings without granting permission preserves the Welcome origin so Back remains available.
   - **Returning Users (Revoked Permission)**: Both tapping the visible back arrow and pressing Android system Back explicitly finish/minimize the application (`activity.finish()`), allowing the user to exit cleanly without being trapped on the gate.
   - **Strict Security Guard**: Neither back mechanism can navigate to the Home Dashboard without verified platform Usage Access.

---

## 2. Refined Home Dashboard Hierarchy Flow

```
[Home Dashboard Loaded (Usage Access Active)]
      │
      ├───► 1. Compact App Header (App title, Refresh, Settings / Theme)
      │
      ├───► 2. Today's Total Phone Usage Hero Metric (Label: "Total phone usage today")
      │
      ├───► 3. 7-Day Total Phone Usage Bar Chart (7 Calendar Days + Honest Incomplete Handling)
      │
      ├───► 4. Top Five Apps Today Breakdown (Top 5 apps with >0ms usage, 40dp icons, + Add / Monitored badge)
      │           │
      │           ├─── Tap "+ Add" ───────────────► Immediate toggle to monitored app list
      │           └─── Tap "View All Apps" ───────► Limits Tab
      │
      ├───► 5. Commitment Lock Compact Card (Active / Inactive, Accessibility notice, Midnight staged changes)
      │
      └───► 6. Compact Protection Health Card (Bottom indicator: PROTECTED, ACCESSIBILITY REQUIRED, DEGRADED, SETUP REQUIRED)
```

- **Separation of Permissions**:
  - **Usage Access**: Mandatory prerequisite for entering Home; enables device screen time calculation, 7-day total chart rendering, and app usage breakdown.
  - **Accessibility Service**: Required strictly for detecting restricted app entry and enforcing blocking, not for reading usage statistics.
- **Refined Home Architecture**:
  - Multi-step `GuidedProtectionSetupCard` checklist is completely removed from Home.
  - Users who skip Accessibility during onboarding enter Home in view-only mode.
  - Compact Protection Health card at the bottom clearly indicates "Accessibility required — app blocking is inactive." with a one-tap "Enable Accessibility" button.

---

## 3. Limits & App Management Flow

```
[Limits Screen]
      │
      ├───► [Search / Filter Apps] (e.g., "Insta" -> Instagram)
      │
      ├───► [Inspect App Status] (Today's time, 7-day total, active limit)
      │
      ├───► [Tap App to Configure Limit]
      │           │
      │           ▼
      │     [Limit Configuration Sheet]
      │           ├── Select Preset (15m, 30m, 45m, 1h, 1.5h, 2h) or Custom
      │           │
      │           └── Confirm Limit
      │                     │
      │                     ├── Commitment Lock Inactive: RuleEngine updates immediately
      │                     │
      │                     └── Commitment Lock Active:
      │                               ├── Tightening (reduced limit): Applies immediately
      │                               └── Relaxing (increased limit): Staged for next midnight (12:00 AM)
      │
      └───► [Commitment Lock Management]
                  │
                  ├── Tap "Enable Commitment Lock" ──► Affirmative Consent Dialog ──► Active
                  └── Tap "Request Midnight Unlock" ──► Unlock Dialog ──► Scheduled for 12:00 AM
```

### Commitment Lock Transition Rules
- **Immediate Tightening**: Selecting a new app or reducing an existing limit takes effect immediately in `UsageRuleEngine`.
- **Deferred Relaxation**: Unselecting an app, increasing a limit, or requesting unlock is staged until local calendar midnight (12:00 AM).
- **Cancellation of Staged Changes**: The user can cancel pending removals or pending limit increases at any time before midnight.
- **Midnight Boundary**: Daily usage resets to 0 ms. Any pending unlock deactivates lock; pending relaxations are applied. If no unlock was requested, commitment remains active on the subsequent day.

---

## 4. History Flow

```
[History Tab]
      │
      ├───► Displays 7-Day Total Device Usage Bar Chart
      │
      ├───► User taps specific calendar day bar (e.g., "Thursday 10")
      │
      └───► Daily Breakdown list updates to show:
                  • Total device usage for that day
                  • Monitored apps vs unmonitored apps
                  • Limit adherence indicators (Respected vs Limit Reached)
```

---

## 5. Daily Enforcement & Blocking Flow

```
[User opens Restricted App (e.g., YouTube)]
      │
      ▼
[RestrictedAppAccessibilityService receives TYPE_WINDOW_STATE_CHANGED]
      │
      ├── Check SelectedAppsRepository & UsageRuleEngine
      │
      ├── Limit NOT REACHED:
      │         └── Start 5-second LiveLimitEnforcementEngine loop in foreground
      │                   │
      │                   ├── App backgrounded -> Cancel monitoring loop cleanly
      │                   │
      │                   └── Cumulative usage crosses limit -> LIMIT_REACHED
      │                                 │
      ▼                                 ▼
[LIMIT_REACHED Trigger Emitted] ◄───────┘
      │
      ▼
[Launch BlockingScreen (MainActivity with EXTRA_BLOCKED_PACKAGE)]
      │
      ├── Displays: "Time's up", App name, configured limit reached, midnight reset notice
      │
      └── Back Button Intercepted:
                └── Pressing physical back returns user to Stop Doom Scroll Dashboard,
                    NEVER back to the restricted application.
```

---

## 6. Settings & Theme Selection Flow

```
[Settings Tab]
      │
      ├───► Appearance: Tap Theme -> Select [System Default | Light | Dark]
      │           └── Saved immediately to SharedPreferences; updates Compose theme dynamically
      │
      ├───► Account:
      │           ├── If Guest: Tap "Link Google Account" -> Trigger Google Sign-In
      │           └── If Google: Displays profile name/email + "Sign Out"
      │
      ├───► Protection Health:
      │           ├── Displays Usage Access & Accessibility status
      │           └── Direct one-tap buttons to open respective Android settings
      │
      └───► Privacy & Disclosures:
                  └── Full transparent disclosure of narrow AccessibilityService usage
```

---

## 7. Edge Cases & Protection Recovery

- **Usage Access Revoked in Background**: If user navigates to Android Settings while the app is backgrounded and revokes Usage Access:
  - On `ON_RESUME`, the app detects revocation and redirects immediately to the **Usage Access Permission Gate Screen**.
  - All background live monitoring loops halt cleanly without assuming zero usage.
- **Accessibility Service Interrupted**: System pauses service (e.g., aggressive OEM battery killer); app transitions health state to `ACCESSIBILITY_INTERRUPTED` and displays recovery button on Dashboard.
- **Process Termination / Device Reboot**: Commitment lock state, configured limits, and staged relaxations are restored from `SharedPreferencesCommitmentLockRepository`.
- **OS Force-Stop Boundary**: If user force-stops app via Android Settings, the app enters the OS `STOPPED` state. The user must relaunch Stop Doom Scroll via home screen launcher to resume service.
- **Midnight Boundary**: Triggers expire automatically; staged relaxations take effect; daily usage counter resets to 0.
