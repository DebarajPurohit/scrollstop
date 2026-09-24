# Product Specification

## Product

**Stop Doom Scroll**

## Vision

Help Android users follow intentional daily limits on selected
social/video apps through reliable, transparent enforcement.

## Problem

Users often exceed intended usage because conventional timers can be
dismissed or overridden.

## Core Value

**Set your limit. When your time is up, we stop you.**

## Target User

Android users who repeatedly exceed intended time on selected
social/video apps and want stronger commitment to self-set limits.

## MVP

-   Onboarding and permission guidance
-   App discovery and selection
-   Daily limits
-   Usage tracking
-   Remaining-time display and warnings
-   Blocking after limit
-   Daily reset
-   Dashboard
-   7-day history
-   Commitment Lock
-   Protection/service health

## Explicitly Deferred

AI chatbot, social features, leaderboards, iOS, cloud sync, parental
controls, website blocking, complex analytics.

## Technical Foundation

UsageStatsManager is the usage source of truth. AccessibilityService is
a narrow enforcement mechanism for selected apps. Core enforcement is
deterministic and does not depend on AI.

## Product Principles

1.  Reliability over feature count.
2.  User-defined rules drive enforcement.
3.  Local-first MVP.
4.  Minimal data collection.
5.  Transparent permission and protection-health states.
6.  No claims of unbreakable protection or medical outcomes.

## Primary Technical KPI

Successful enforcement rate of intended blocking events.

## Success Metrics

Activation, retention, enforcement reliability, crashes, battery impact,
reviews, premium conversion and revenue.

## Current Stage

Feasibility / MVP preparation.
