# Floating start reliability

## Evidence

The compact handle expands the panel; only the expanded action button starts/stops automation. Runtime has start and stop records one millisecond apart (22:33:40.127 / .128 and 22:33:44.514 / .515). A live toggle can therefore undo a start when clicks are delivered repeatedly. Another gap: start() sets the requested run flag but returns while an interrupted old worker is still alive, leaving a stopped-looking panel and treating its cleanup as an unexpected exit with a three-second restart delay.

## Correction

- Debounce the expanded action using monotonic time for 500 ms; rejected clicks do not extend the interval. Keep the compact handle's expand-only behavior.
- Queue one requested start while the previous worker is unwinding. Display 正在启动 and allow cancellation. Start immediately after cleanup, preserving the existing recovery delay for genuine unexpected exits.
- Serialize lifecycle requests and final ownership changes; an old service/worker must not reset a newer worker's global state.
- Version 1.1.3; test duplicate delivery, later deliberate stop, queued start cancellation, stale service ownership, recovery timing and visible pending status. Build, update the installed parallel package without clearing data, and synchronize the source changes to the authorized fork.

Do not initiate real Soul messaging during this control fix's verification.

## Validation

19 suites / 115 tests passed with zero failures or errors. Both APK variants build and verify their signatures. Final lint: zero errors, 154 warnings. The installed parallel package is confirmed as versionName 1.1.3 / versionCode 5; settings and database contents were checked before and after the update and preserved. Regression coverage verifies the control policies and displayed pending state; no real messaging worker was started by the agent for this fix.
