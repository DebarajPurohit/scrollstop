# scrollstop
# 🚫 Stop Doom Scroll

**Set your limit. When your time is up, we stop you.**

Stop Doom Scroll is an Android app designed to help users reduce compulsive scrolling by setting daily usage limits on selected apps and enforcing those limits when the allotted time is exhausted.

Unlike traditional screen-time dashboards that mainly report usage, Stop Doom Scroll is built around **active enforcement and commitment**.

---

## 🎯 The Problem

Social media and short-form video apps are designed for continuous engagement.

Users may set Android screen-time limits, but they can often extend, dismiss, or ignore those limits.

The result is a familiar cycle:

**Open → Scroll → Lose track of time → Exceed intended usage → Repeat**

Stop Doom Scroll aims to break this cycle by turning an intention into an enforced rule.

---

## 💡 Core Idea

The user chooses:

* Which apps should be controlled
* How much time they can spend on each app per day
* Whether to enable Commitment Lock

The app then:

1. Tracks actual usage
2. Calculates remaining time
3. Warns the user as the limit approaches
4. Detects when the limit is reached
5. Blocks access to the selected app
6. Resets the allowance each day

### Core principle

> **The user sets the rule. The app enforces the rule.**

---

## 🚀 MVP

### Onboarding

* Explain the purpose of the app
* Explain required permissions
* Guide users through setup

### App Selection

* Discover installed applications
* Allow users to select apps they want to control
* Configure individual limits

### Usage Tracking

* Track application usage
* Display today's usage
* Calculate remaining allowance
* Provide usage warnings

### App Blocking

* Detect launches of restricted applications
* Enforce configured limits
* Display a clear blocking screen
* Prevent normal access after the limit is reached

### Commitment Lock

A stronger protection mode designed to make impulsive overrides more difficult.

### Dashboard

* Today's usage
* Remaining time
* Controlled applications
* Protection/service health
* Current blocking status

### History

* 7-day usage history
* Per-app usage
* Limit vs. actual usage

### Protection Health

Clearly inform the user when protection is weakened or paused because of:

* Usage Access permission removal
* Accessibility Service interruption
* Required permission changes
* Service restrictions
* Other device-level issues

---

## 🏗️ Technical Architecture

```text
                 ┌────────────────────┐
                 │    Jetpack Compose │
                 │         UI         │
                 └─────────┬──────────┘
                           │
                           ▼
                 ┌────────────────────┐
                 │   ViewModel / UI   │
                 │      State         │
                 └─────────┬──────────┘
                           │
                           ▼
                 ┌────────────────────┐
                 │    Rules Engine    │
                 │                    │
                 │ Limits / Warnings  │
                 │ Blocking Decisions │
                 │ Commitment Rules   │
                 └───────┬──────┬─────┘
                         │      │
             ┌───────────┘      └────────────┐
             ▼                               ▼
 ┌─────────────────────┐          ┌─────────────────────┐
 │   Usage Engine      │          │ AccessibilityService│
 │                     │          │                     │
 │ UsageStatsManager   │          │ Detect restricted   │
 │ Usage Calculation   │          │ app access          │
 └──────────┬──────────┘          └──────────┬──────────┘
            │                                │
            ▼                                ▼
 ┌─────────────────────┐          ┌─────────────────────┐
 │        Room         │          │   Blocking Layer    │
 │ Local Persistence   │          │                     │
 └─────────────────────┘          └─────────────────────┘
```

### Technology Stack

| Area            | Technology                          |
| --------------- | ----------------------------------- |
| Language        | Kotlin                              |
| UI              | Jetpack Compose                     |
| Architecture    | MVVM / Pragmatic Clean Architecture |
| Database        | Room / SQLite                       |
| Preferences     | DataStore                           |
| Async           | Kotlin Coroutines                   |
| Navigation      | Jetpack Navigation                  |
| Background Work | WorkManager where appropriate       |
| Usage Tracking  | Android UsageStatsManager           |
| Enforcement     | Android AccessibilityService        |
| Minimum Scope   | Android                             |
| Storage Model   | Local-first                         |

---

## 🔐 Privacy & Security

Privacy is a core product requirement.

Stop Doom Scroll should collect only the information necessary to provide usage tracking and enforcement.

### The app should NOT collect:

* Messages
* Keystrokes
* Passwords
* Screen recordings
* Unrelated screen contents
* Personal communications
* Unnecessary device data

The Accessibility Service has a **narrow and deterministic purpose**:

> Detect access to user-selected restricted applications and enforce predefined blocking rules.

The application should clearly explain why Accessibility Service access is required and obtain the user's affirmative consent.

No attempt should be made to modify another application's password, authentication or security mechanisms.

---

## 📁 Project Documentation

The repository maintains the following documents as the project's source of truth:

```text
PROJECT/
│
├── PRODUCT_SPEC.md
├── USER_FLOWS.md
├── UI_SPEC.md
├── DATABASE_SCHEMA.md
├── SECURITY_RULES.md
├── TEST_PLAN.md
└── DEVELOPMENT_RULES.md
```

### Documentation responsibilities

**PRODUCT_SPEC.md**
Product vision, target users, MVP scope, approved/deferred features, decisions and KPIs.

**USER_FLOWS.md**
Onboarding, permissions, app selection, limits, warnings, blocking, recovery and edge cases.

**UI_SPEC.md**
Screens, navigation, components, states, actions, errors and accessibility requirements.

**DATABASE_SCHEMA.md**
Entities, fields, relationships, constraints, indexes, migrations and retention.

**SECURITY_RULES.md**
Permissions, privacy, Accessibility Service restrictions, storage, logging and Play compliance.

**TEST_PLAN.md**
Functional, UI, integration, security, performance, battery, regression and device testing.

**DEVELOPMENT_RULES.md**
Architecture, coding standards, dependencies, development workflow, testing and Definition of Done.

---

## 🧪 Reliability Requirements

Blocking reliability is the most important technical requirement.

Critical scenarios include:

* Limit reached while restricted app is open
* Repeated app launches
* App switching
* Device lock/unlock
* Device reboot
* Foreground/background transitions
* Midnight reset
* Permission removal
* Accessibility Service interruption
* Usage Access removal
* Battery optimization
* Background restrictions
* App updates
* Android OS updates
* Date/time changes

### Primary Technical KPI

**Successful enforcement rate**

> Percentage of intended blocking events where the restricted application was successfully blocked according to the user's configured rule.

A blocking failure is treated as a **critical issue**.

---

## 📱 Device Compatibility

Testing should include representative devices from major Android manufacturers, including:

* Google Pixel
* Samsung
* Xiaomi / Redmi
* OnePlus
* OPPO / Realme
* Motorola

Testing should cover supported Android versions and manufacturer-specific background/service restrictions.

---

## 🛠️ Development Workflow

Every significant feature follows:

```text
Inspect
   ↓
Plan
   ↓
Implement
   ↓
Build
   ↓
Test
   ↓
Review
   ↓
Fix
   ↓
Retest
   ↓
Document
```

Code is not considered complete merely because it compiles.

Before major changes:

1. Inspect the existing implementation
2. Review relevant specification files
3. Identify affected components
4. Plan the smallest appropriate change
5. Implement
6. Build and test
7. Review security and reliability implications
8. Update documentation
9. Retest

---

## 📊 Product Priorities

Development priorities are:

```text
1. User Value
2. Reliability
3. Privacy & Security
4. Google Play Compliance
5. Retention
6. Monetization
7. Growth
```

Reliability, privacy or compliance should not be sacrificed simply to add features.

---

## 💰 Monetization Direction

The initial business model is planned around a **Free + Premium** structure.

### Free

Potentially includes:

* Limited number of controlled apps
* Basic daily limits
* Basic usage tracking
* Basic history

### Premium

Potential future features:

* Unlimited controlled apps
* Commitment Lock
* Advanced schedules
* Detailed insights
* Reports
* AI-powered coaching

Premium scope will be validated against actual user willingness to pay before significant expansion.

---

## 📈 Product Metrics

Key metrics to evaluate after launch:

### Activation

* Onboarding completion
* Permissions completed
* First app configured
* First limit created

### Engagement

* Daily active users
* Weekly active users
* Number of protected apps
* Limits reached

### Reliability

* Successful enforcement rate
* Service interruption rate
* Permission failure rate
* Crash rate

### Retention

* Day 1 retention
* Day 7 retention
* Day 30 retention

### Business

* Free → Premium conversion
* Subscription retention
* Revenue
* Refund rate

### User Feedback

* Play Store rating
* Reviews
* Support requests
* Blocking failure reports

---

## 🗺️ Development Roadmap

### Phase 1 — Feasibility

Prove:

* UsageStatsManager usage tracking
* App discovery
* App selection
* Short test limits
* AccessibilityService detection
* Reliable blocking

### Phase 2 — Foundation

Build:

* Project architecture
* Database
* Navigation
* Permission flow
* App discovery
* Basic settings

### Phase 3 — Usage Engine

Implement:

* Usage calculation
* Daily limits
* Remaining time
* Warnings
* Daily reset
* Usage history

### Phase 4 — Enforcement

Implement:

* Restricted-app detection
* Rules Engine
* Blocking layer
* Commitment Lock
* Protection health checks

### Phase 5 — UX

Build:

* Onboarding
* Dashboard
* App selection
* Limit configuration
* History
* Error and recovery states

### Phase 6 — Reliability

Test:

* Multiple Android versions
* OEM-specific behavior
* Reboots
* Battery restrictions
* Permission removal
* Service interruptions
* Updates
* Edge cases

### Phase 7 — Beta

Measure:

* Enforcement reliability
* Crashes
* Battery impact
* Activation
* Retention
* User feedback
* Willingness to pay

### Phase 8 — Release

Prepare:

* Google Play declarations
* Accessibility disclosure
* Privacy policy
* Data Safety information
* Permission explanations
* Signing
* Production testing
* Store listing

---

## ⚠️ Important Product Principles

Stop Doom Scroll is **not** intended to promise:

* "Unbreakable" blocking
* Impossible-to-bypass protection
* Medical treatment
* Clinical outcomes

The product should communicate honestly about Android limitations and clearly show users when protection is not active.

---

## 🤝 Development Philosophy

The goal is not to build another screen-time dashboard.

The goal is to build a **reliable commitment tool** that helps users follow the limits they intentionally set for themselves.

```text
Intent
  ↓
Set Limit
  ↓
Track Usage
  ↓
Warn
  ↓
Limit Reached
  ↓
Enforce
  ↓
Reset
  ↓
Repeat
```

---

## 📌 Current Status

**Project:** Stop Doom Scroll
**Platform:** Android
**Stage:** Product validation / MVP development
**Architecture:** Kotlin + Jetpack Compose + MVVM
**Storage:** Local-first
**Primary enforcement:** UsageStatsManager + AccessibilityService

### Current priority

> Prove that the core enforcement loop works reliably on real Android devices before expanding the product.

---

## 📄 License

License to be determined.

---

## ⚠️ Disclaimer

Stop Doom Scroll is a productivity and digital-wellbeing tool. It is not a medical or clinical product.

Android operating-system behavior, device manufacturers and system restrictions may affect enforcement reliability. The application should communicate such limitations transparently.
