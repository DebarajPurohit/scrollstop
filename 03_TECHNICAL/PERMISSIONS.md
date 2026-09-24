# Permissions

## Usage Access

Purpose: Read application usage statistics required to calculate daily
usage.

User action: User grants Usage Access through Android settings.

Failure: Protection is paused and the app explains how to restore
access.

## Accessibility Service

Purpose: Detect access to selected restricted applications and enforce
predefined blocking rules.

Restrictions: - No unrelated screen-content collection - No messages -
No keystroke collection - No autonomous decisions - No unrelated
monitoring

User action: User explicitly enables the service.

Failure: Protection is paused and the app provides a recovery path.

## Package Visibility

App discovery must use the narrowest appropriate Android package
visibility approach. Avoid broad installed-app access unless it is
demonstrably required and permitted.

## Principle

Every permission must have: 1. Clear user-facing explanation 2. Specific
product purpose 3. Failure state 4. Recovery flow 5. Required Play
disclosure/handling
