# UI Specification

## 1. Visual Architecture & Design Principles

- **Visual Style**: Premium minimalist — calm, distraction-free, intentional typography, uncluttered surfaces, and purposeful whitespace.
- **Theme Behavior**: Follows Android system theme by default (Material You dynamic wallpaper coloring disabled to preserve brand integrity). Manual override available in Settings: **System Default**, **Light**, or **Dark**.
- **Mandatory Usage Access Gate**: **Usage Access is mandatory prior to entering Home.** Immediately after selecting "Continue with Google" or "Continue as guest", the user must grant Usage Access before Home can be accessed. If access is not confirmed, the user remains on the permission gate screen.
- **Separation of Permissions**:
  - **Usage Access**: Prerequisite for Home; required to compute daily screen time, render the 7-day total usage chart, and populate app usage statistics.
  - **Accessibility Service**: Separate, subsequent setup step; required specifically for foreground app detection and blocking enforcement, not for reading usage stats.
- **Data Integrity & Non-Fabrication Rules**:
  - **Never display 0 min or zero usage when data is unavailable or permissions are missing.** All components must explicitly render distinct loading, permission-denied, unavailable-data, empty, or error states.
  - **Never fabricate usage values for missing history.** For first-time users or newly initialized devices lacking seven full days of operating system usage history:
    - Available days must be displayed with exact recorded data.
    - Missing days must be explicitly indicated as unrecorded/building history (e.g., subtle dashed/muted bars with label "No record"), NOT shown as 0 minutes of actual usage.
    - History status badge must be displayed (e.g., "3 of 7 days recorded — history building").
    - Averages must be computed strictly across days with valid records and explicitly labeled with the sample size (e.g., "Daily avg (3 days): 2h 15m").
    - In app breakdowns, multi-day cumulative totals must indicate the actual available observation window (e.g., "1h 30m (over 3 days)").
- **Local-First Boundary**: All usage data and limit evaluation remain strictly on-device. Optional Google sign-in does NOT introduce cloud synchronization or remote telemetry.
- **Accessibility**: Minimum touch targets $\ge 48\times 48\,\text{dp}$, WCAG AA contrast compliance across both themes, full dynamic font scaling support (up to 1.5x) without layout clipping.

---

## 2. Navigation Architecture

Persistent bottom navigation with four primary top-level destinations (unlocked once Usage Access is granted):

1. **Home (Dashboard)**: Daily overview, 7-day total device usage chart, app breakdown, and real-time protection health.
2. **Limits**: Searchable app catalog, daily limit configuration (presets + custom), and Commitment Lock status/management.
3. **History**: 7-day historical usage trends, daily app breakdowns, and behavioral patterns.
4. **Settings**: Theme selection, account / Google sign-in status, protection health diagnostic links, privacy policy, and about.

Entry & Gating Flow:
- **Welcome / Entry Screen**: Presented on first launch: "Continue with Google" or "Continue as guest".
- **Usage Access Permission Gate Screen**: Enforced permission gate immediately following entry. Blocks access to Home until permission is verified.
- **Blocking Screen**: Enforced modal intercept when a restricted app exceeds its daily limit. Intercepts back navigation to keep user in safe Stop Doom Scroll environment.
- **Accessibility Prominent Disclosure Screen**: Dedicated contextual disclosure detailing narrow accessibility usage and privacy guarantees prior to launching system settings.

---

## 3. Screen Specifications

### 3.1 Welcome & Entry Screen
- **Purpose**: Low-friction entry offering optional identity without blocking app utility.
- **Primary Actions**:
  - `Continue with Google` (One-tap / Credential Manager sign-in).
  - `Continue as guest` (Direct entry to Usage Access gate, zero personal data collected).
- **Key UX Rules**:
  - No forced account creation, passwords, or intrusive forms.
  - Guest users receive identical local functionality.
  - Explanatory subtext: "Stop Doom Scroll works completely offline. Sign-in is optional."
  - Selecting either option transitions immediately to the **Usage Access Permission Gate Screen**.

### 3.2 Usage Access Permission Gate Screen (Mandatory Prerequisite)
- **Purpose**: Educate user on the necessity of Usage Access and ensure the permission is granted before Home is accessed.
- **Key UX Rules**:
  - Enforced for both Guests and Google-authenticated users.
  - **Strict Navigation Guard**: Home, Limits, History, and Settings cannot be accessed until Usage Access is verified.
- **Components**:
  1. **Header & Icon**: Clean shield/data icon + "Enable Usage Access to Continue".
  2. **Clear Explanation Card**:
     - Why it is needed: "Stop Doom Scroll measures active screen time locally to show your 7-day device trends and enforce daily limits."
     - Privacy reassurance: "Usage statistics stay strictly on your device. We never upload or share your app activity."
  3. **Primary Action**:
     - `Open Android Settings` button: Deep-links directly to `Settings.ACTION_USAGE_ACCESS_SETTINGS`.
  4. **Secondary / Verification Action**:
     - `Check Again` button: Manually triggers an immediate query to `AppOpsManager`.
  5. **Auto-Verification on Return**:
     - Automatically verifies permission state via lifecycle `ON_RESUME` when the user navigates back from system Settings.
     - If verified: Automatically routes forward to the **Home Dashboard**.
     - If not verified: Remains on screen and displays guidance: "Permission not detected yet. Please locate Stop Doom Scroll in the list and toggle 'Permit usage access' on."

### 3.3 Home Dashboard Screen (First Main Screen Post-Permission)
- **Purpose**: Central command center providing instant visibility into device screen time, monitored app consumption, and enforcement status.
- **Prerequisite**: Usage Access is guaranteed active upon reaching this screen.
- **Components**:
  1. **Top Header**: App title ("Stop Doom Scroll"), quick Theme toggle / Profile icon leading to Settings.
  2. **Protection Status Card**:
     - `PROTECTED`: Green pill banner, active monitored app count, "All limits active".
     - `ACCESSIBILITY REQUIRED`: Amber alert banner with "Enable Accessibility Service" CTA button.
     - `ACCESSIBILITY INTERRUPTED`: Alert banner with "Re-enable Service" CTA button.
     - `SETUP REQUIRED`: Contextual checklist guiding first-time setup (App Selection $\rightarrow$ Limits $\rightarrow$ Accessibility).
  3. **Total Device Usage Chart (7 Calendar Days)**:
     - Prominent bar chart showing total phone screen time across all applications for the last 7 calendar days.
     - Clear date labels (e.g., "Mon 7", "Tue 8", ..., "Today").
     - Highlighted bar for the current day with exact hours/minutes.
     - **Incomplete History Handling**:
       - For days with recorded usage: Renders proportional solid bars with exact time.
       - For days predating records / missing history: Renders muted dashed/stippled outline with label "No data". Never renders artificial 0-min bars.
       - Status badge: Displays "X of 7 days recorded" when history is incomplete.
       - Baseline average: Computes daily average exclusively across days with valid records, explicitly annotated (e.g., "Avg (3 days): 2h 15m").
  4. **Today's Total Metric**:
     - Large, clean typography showing today's total active screen time (e.g., "3h 42m today").
  5. **Monitored & Installed Apps Breakdown**:
     - Ranked list showing apps by consumption.
     - Row details: App icon, app label, today's usage (e.g., "45 min"), and cumulative usage annotated with the actual available timeframe (e.g., "4h 15m (7d)" or "1h 30m (3d of data)").
     - Visual progress bar showing consumption relative to limit (if configured) or relative to total device time.
     - Status badges: `Within limit`, `Nearing limit (>80%)`, `Limit reached`, or `Unrestricted`.
  6. **Quick Configuration CTA**:
     - Floating or inline button: "Configure Limits & Apps" -> navigates directly to Limits tab.

### 3.4 Guided Protection Setup (Contextual & Non-Blocking Post-Home)
- Contextual checklist embedded on Home dashboard for completing protection:
  1. *Step 1: App Selection* -> Choose distracting apps to control.
  2. *Step 2: Daily Limits* -> Set intentional daily allowances.
  3. *Step 3: Accessibility Detection* -> Transparent disclosure and service enablement for real-time blocking.
  4. *Step 4: Active Protection* -> Verification that limits and accessibility are active.
- Non-blocking: Users can explore the dashboard and available historical stats immediately because Usage Access is already granted.

### 3.5 Limits & App Management Screen
- **Purpose**: Search, select, and configure daily allowances for distracting applications.
- **Components**:
  1. **Search & Filter Bar**: Real-time search query filtering installed launchable applications by name or package; filter chips (`All`, `Controlled`, `Social & Video`).
  2. **Commitment Lock Summary Banner**:
     - Displays current lock state: `Inactive`, `Active (Locked until Midnight)`, or `Unlock Pending (Midnight)`.
     - Direct button to Enable Lock or Request Midnight Unlock.
  3. **App Row**:
     - App icon, label, package name (subtle secondary text).
     - Today's usage and cumulative total with observation window.
     - Current limit badge (e.g., "30m daily") or "No limit".
     - Pending change badge (e.g., "Removal at midnight" or "New limit 45m at midnight").
     - Toggle checkbox / gear edit icon.
  4. **Limit Configuration Modal / Sheet**:
     - Quick preset duration pills: `15 min`, `30 min`, `45 min`, `1 hour`, `1.5 hours`, `2 hours`.
     - Custom time selector (hour / minute stepper or input).
     - Plain-language Commitment Lock notice:
       - *Tightening*: "Decreasing your limit takes effect immediately."
       - *Relaxing while locked*: "Increasing your limit will take effect at next midnight (12:00 AM) to protect your commitment."

### 3.6 History Screen
- **Purpose**: Multi-day retrospective analysis of screen time patterns and limit adherence.
- **Components**:
  1. **Interactive 7-Day Chart**: Reusable bar chart synchronized with selected day. Tapping an available day inspects historical usage for that calendar date. Days without history are disabled with an informative tooltip ("No data recorded for this date").
  2. **Daily Breakdown List**: App-by-app usage for the selected calendar day, indicating whether daily limits were respected or exceeded on that day.
  3. **Weekly Totals & Insights**: Total screen time across available days, daily average of recorded days, and top 3 most used apps.

### 3.7 Settings Screen
- **Purpose**: User preferences, account management, diagnostic transparency, and privacy controls.
- **Components**:
  1. **Appearance**: Theme selection segmented control or dialog:
     - `System default` (default).
     - `Light`.
     - `Dark`.
  2. **Account & Identity**:
     - Guest state: "Signed in as Guest (Offline)" + "Link Google Account" button.
     - Google state: User profile name/email + "Sign Out" button.
     - Explanatory footnote: "Stop Doom Scroll stores your usage exclusively on your device. Account linking is for identity only."
  3. **Protection Health & Diagnostics**:
     - Status of Usage Access (`Granted`).
     - Detailed status of Accessibility Service (`Active` / `Interrupted` / `Disabled`).
     - Battery optimization guide / OEM background guidance link.
  4. **Commitment Lock Rules & Status**:
     - Detailed explanation of midnight reset rules, immediate tightening, and deferred relaxation.
  5. **Privacy & About**:
     - Non-accessibility disclaimer, privacy guarantee (no keystrokes, no screen scraping, local-only processing).
     - App version, open source licenses, feedback link.
  6. **Developer Controls Boundary**:
     - `[DEBUG TEST] Reset Commitment Lock` is strictly excluded from release builds (`BuildConfig.DEBUG == true`) and placed in a dedicated debug drawer, never in primary user paths.

### 3.8 Blocking Screen (Enforced Limit-Reached Intercept)
- **Purpose**: Calm, firm pause screen preventing access to an over-limit application.
- **Components**:
  - Mindful headline: "Time's up" or "Daily limit reached".
  - Target app icon and app label.
  - Clear usage statistics: "You've used your configured limit of [X min] for [App Name] today."
  - Reset availability time: "Resets at local midnight (12:00 AM)."
  - Primary button: "Go to Dashboard" (returns user safely to Stop Doom Scroll).
  - Safety rule: Intercepts physical back button to prevent return to restricted app. Zero bypass escape routes.

---

## 4. Semantic Color Tokens

| Semantic Token | Light Theme | Dark Theme | Purpose |
| :--- | :--- | :--- | :--- |
| `colorBackground` | `#F8FAFC` (Slate 50) | `#0B0F19` (Slate 950) | Root window background |
| `colorSurface` | `#FFFFFF` (White) | `#111827` (Slate 900) | Top app bar, cards, sheets |
| `colorSurfaceVariant` | `#F1F5F9` (Slate 100) | `#1E293B` (Slate 800) | App list items, search container |
| `colorOutline` | `#E2E8F0` (Slate 200) | `#334155` (Slate 700) | Borders, subtle dividers |
| `colorPrimary` | `#059669` (Emerald 600) | `#10B981` (Emerald 500) | Active protection, primary buttons |
| `colorOnPrimary` | `#FFFFFF` | `#022C22` (Deep Emerald) | Text on primary buttons |
| `colorSecondary` | `#475569` (Slate 600) | `#94A3B8` (Slate 400) | Secondary subtitles, metadata |
| `colorCommitment` | `#2563EB` (Blue 600) | `#3B82F6` (Blue 500) | Commitment Lock cards and badges |
| `colorWarning` | `#D97706` (Amber 600) | `#F59E0B` (Amber 500) | Nearing limit (>80%), pending midnight changes |
| `colorError` | `#DC2626` (Red 600) | `#EF4444` (Red 500) | Blocking screen, missing permissions |
| `colorErrorContainer` | `#FEE2E2` (Red 100) | `#450A0A` (Deep Red 950) | Permission alert card background |
| `colorOnErrorContainer`| `#991B1B` (Red 800) | `#FCA5A5` (Red 300) | Text on permission alert cards |

---

## 5. UI State & Feedback Matrix

| State Type | Usage Access Gate Screen | Home Dashboard | Limits Screen | History Screen |
| :--- | :--- | :--- | :--- | :--- |
| **Loading** | Spinner during permission check | Shimmer chart bars, placeholder cards | Shimmer list rows, disabled search | Shimmer chart bars and list |
| **Permission Denied / Missing** | Explanatory card, "Open Settings" CTA, "Check Again" CTA | *Screen cannot be accessed* (caught by gate) | Warning header; apps listed without usage stats | "Usage Access required" redirect |
| **Partial / Incomplete History (< 7 Days)** | N/A | Renders valid days accurately; missing days shown as unrecorded; badge: "X of 7 days recorded" | Cumulative totals explicitly show recorded window (e.g., "3 days of data") | Unrecorded days disabled with "No data" status; average computed only on valid days |
| **Empty** | N/A | "No apps monitored yet. Configure limits below." | "No apps found matching '[query]'" | "No usage recorded for this calendar day." |
| **Data Unavailable** | N/A | **Never show 0 min!** Shows "Usage data unavailable" + Refresh CTA | Shows "-- min" badge instead of 0 min | Shows "Unavailable" for queried dates |
| **Error** | "Unable to check permission status. Tap to retry." | "Unable to read usage stats. Tap to retry." | "Failed to discover apps. Tap to retry." | "Unable to load historical records." |

---

## 6. Acceptance Criteria for Redesigned UI

1. **Mandatory Usage Access Gate**:
   - Immediately following "Continue with Google" or "Continue as guest", the user is routed to the Usage Access permission gate.
   - Access to Home, Limits, History, and Settings is strictly blocked until Usage Access is verified.
   - Returning from Android Settings re-evaluates permission state automatically (`ON_RESUME`). If denied, the user remains on the gate screen with clear instructions and an explicit "Check Again" action.
2. **First-Time Users & Incomplete History Handling**:
   - For users with fewer than 7 days of device history, available days are displayed accurately.
   - Days with no history are clearly indicated as unrecorded/building history and **never** fabricated or rendered as 0-minute usage.
   - The chart displays an informative badge (e.g. "X of 7 days recorded — history building").
   - Daily average benchmarks are calculated strictly over days with recorded data and explicitly labeled with the sample size.
   - App breakdown rows show cumulative totals reflecting the available window (e.g., "over 3 days").
3. **Data Integrity & Non-Fabrication**:
   - Under no circumstances is 0 minutes displayed when data query fails or data is unavailable.
   - Distinct loading, unavailable, empty, and error states are rendered.
4. **Separation of Permissions**:
   - Usage Access unlocks Home and reading screen time.
   - Accessibility Service is presented subsequently as a contextual step on Home specifically for blocking enforcement.
5. **Theme System**:
   - Defaults to Android system theme.
   - Settings provides manual selection: System Default, Light, Dark.
   - Dynamic wallpaper coloring is disabled to preserve brand slate/emerald tokens.
6. **Persistent 4-Tab Navigation**:
   - Seamless switching between Home, Limits, History, and Settings preserving backstack and state.
7. **Preserved Enforcement**:
   - Zero changes to `UsageStatsManager`, `UsageRuleEngine`, `CommitmentRuleEngine`, `RestrictedAppAccessibilityService`, or midnight boundaries.
   - All 179 automated tests continue to pass.
