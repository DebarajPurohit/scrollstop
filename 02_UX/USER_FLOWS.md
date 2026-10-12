# User Flows

## First Run

Open app → Explain value → Explain permissions → Grant Usage Access →
Enable Accessibility Service → Select apps → Set limits → Optional
Commitment Lock → Dashboard.

## Daily Use

Dashboard → View remaining time → Use selected app → Warning → Limit
reached → Blocking screen.

## Recovery

Protection paused → Identify missing permission/service → Explain impact
→ Open appropriate Android settings → Recheck → Protection active.

## App Selection

Discover eligible apps → Search/browse → Select → Configure limit →
Save.

## Limit Change (Uncommitted)

Select app → Edit limit → Confirm → Rules Engine uses new rule immediately.

## Commitment Lock Activation

Dashboard → Tap "Enable Commitment Lock" → Dialog explains commitment & midnight restriction rule → Check affirmative consent box ("I understand and commit to my limits until midnight") → Confirm → Status updates to "COMMITMENT LOCK: ACTIVE".

## Restriction Modifications While Locked

- **Tightening (Allowed Immediately):** Add new restricted app or decrease daily limit → Changes take effect immediately in Rules Engine.
- **Relaxing (Deferred to Midnight):** Attempt to unselect protected app or increase daily limit → User informed change takes effect at next local midnight (12:00 AM) → Staged as pending change in state → User can cancel staged relaxation anytime before midnight.
- **Requesting Unlock:** Tap "Request Unlock for Midnight" → Confirm in dialog → Status shows "Unlock scheduled for next midnight" → User can cancel request anytime before midnight.
- **Midnight Transition:** Wall clock reaches 12:00 AM → Daily usage resets to 0 ms → Any pending unlock deactivates lock; pending relaxations are applied → If no unlock was requested, commitment lock remains active on subsequent day.

## Developer Safe Test Reset (Debug Builds Only)

Dashboard → Scroll to Development Status → Tap "[DEBUG TEST] Reset Commitment Lock" → Commitment and pending relaxations instantly cleared without waiting for midnight → App returned to configurable state. (Strictly excluded from release builds).

## Edge Cases

-   Service disabled
-   Usage Access removed / query failed (halts live loop safely without assuming zero usage)
-   Device reboot (commitment and pending state restored from persistent storage)
-   Battery restriction
-   App update
-   Midnight reset
-   Date/time change / timezone shift
-   Restricted app already open
-   Repeated launch after blocking
-   Screen lock/unlock
