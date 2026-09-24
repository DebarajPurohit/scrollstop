# Database Schema

## Planned Entities

### RestrictedApp

-   id
-   packageName
-   displayName
-   enabled
-   createdAt
-   updatedAt

### DailyLimit

-   id
-   restrictedAppId
-   limitMinutes
-   commitmentLockEnabled
-   enabled
-   createdAt
-   updatedAt

### DailyUsage

-   id
-   restrictedAppId
-   localDate
-   usageSeconds
-   limitSeconds
-   blockedEvents
-   updatedAt

### AppSettings

-   id
-   timezoneId
-   onboardingCompleted
-   protectionEnabled
-   createdAt
-   updatedAt

## Constraints

-   packageName unique
-   one active daily limit per restricted app
-   one daily usage record per app/date
-   foreign keys enforced
-   timestamps stored consistently

## Retention

MVP retains only data needed for current dashboard/history. Retention
period should be finalized before release.

Schema changes require migrations and regression tests.
