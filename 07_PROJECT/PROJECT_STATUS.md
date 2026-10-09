# Project Status

**Date:** 2026-10-09\
**Stage:** POC-06 Follow-up — Keyboard-Open Live Monitoring Fix Implemented (PHYSICAL REGRESSION VALIDATION PENDING)

## Completed

- Product concept and MVP scope defined
- Core architecture selected (MVVM + Pragmatic Clean Architecture)
- Documentation source of truth created and organized
- **POC-01 Android Foundation Established**: PASS
- **POC-02A App Discovery Established**: PASS
- **POC-02B Usage Tracking Feasibility**: PASS
- **POC-03 Usage Limit + Rules Engine Feasibility**: PASS
- **POC-04 Accessibility Service Enforcement Trigger & Narrow App Detection**: PASS
- **POC-05 Blocking UI & Enforcement Loop**: PASS
- **POC-05A Live Limit-Crossing Continuous Enforcement**: IMPLEMENTED
- **POC-06 Live Enforcement Failure Investigation & Fix**:
  - Physical test results:
    - PASS — Amazon live limit crossing.
    - PASS — YouTube continuous enforcement and reopening.
    - PASS — Unselected apps remain usable.
    - PASS — Selected apps under their limits remain usable.
    - FOLLOW-UP RESOLUTION — When keyboard is open during Amazon usage, live monitoring stopped or failed to update.
  - Root cause diagnosed:
    1. Package visibility in Android 11+ (API 30+) hid installed IMEs from `InputMethodManager.getEnabledInputMethodList()`, causing keyboard window events to be treated as unselected packages and killing `monitoringJob`.
    2. Faulty condition in `AndroidUsageStatsRepository.calculateUsageFromEvents` (`!removed && activeSet.size == 1`) cleared ongoing sessions when older activities emitted `ACTIVITY_STOPPED` during multi-activity transitions (such as tapping Amazon search bar to launch `SearchActivity`).
  - Follow-up fix implemented:
    - Added `<intent><action android:name="android.view.InputMethod" /></intent>` to `<queries>` in `AndroidManifest.xml`.
    - Created `ImePackageDetector` with multi-layered detection (O(1) system checks, keyboard signatures, naming patterns, `InputMethodManager`, `PackageManager`, and `Settings.Secure`).
    - Corrected `calculateUsageFromEvents` in `AndroidUsageStatsRepository` to preserve active activities across activity transitions.
    - Propagated dynamic `isTransientPackage` updates to cached engine instances in `ServiceLocator`.
  - Automated verification:
    - 10 new regression tests in `POC06KeyboardOpenMonitoringTest` passing 100%.
    - New ongoing multi-activity transition tests in `AndroidUsageStatsRepositoryTest` passing 100%.
    - `ImePackageDetectorTest` passing 100%.
    - Full project test suite passing 100% (122 tests completed, 0 failures).
    - `./gradlew assembleDebug` compiles cleanly with zero errors.

## Pending

- Physical device regression validation on target hardware:
  - Test 1: Amazon continuous foreground limit crossing with keyboard open (search bar typing).
  - Test 2: YouTube continuous enforcement and reopening.
  - Test 3: Unselected apps remain usable.
  - Test 4: Selected apps under their limits remain usable.
- Commitment Lock (Phase 2)
- Advanced anti-bypass protection & OEM battery restriction handling (Phase 2)

## Current Next Action

Execute physical device regression validation on target hardware for Amazon with keyboard open.

## Documentation Rule

Update this file whenever project stage, major milestone, blocker or next action changes.

