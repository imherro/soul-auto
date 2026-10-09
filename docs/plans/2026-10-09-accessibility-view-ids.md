# Android view ID reporting correction

## Runtime evidence

SoulBot 1.1.1 on MuMu Android 15 repeatedly reports visibleNames=0 and visibleBadges=0, despite Soul 5.29.0 displaying unread conversations. ID-based queries can still recognize the list, but the new traversal reads AccessibilityNodeInfo.viewIdResourceName. The service XML omitted flagReportViewIds, which Android requires to populate that property.

## Implementation and validation

1. Request flagReportViewIds in the existing service configuration; log its effective value when the service connects.
2. Add stopped-state, read-only list verification using the same row resolver as production scanning. Log only node and row counts, without names or chat text. Do not click, generate, or send replies during verification.
3. Bump version to 1.1.2, run the unit suite and lint, build both standard and parallel APKs, and update the installed parallel package while preserving its private settings and databases.
4. Verify reportsViewIds=true and nonzero visible names/rows from the actual emulator accessibility service. Inspect unread counts while stopped. This check is distinct from an end-to-end message-send test.
5. Review and synchronize only the source changes to the authorized imherro/soul-auto fork.

Reference: https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_REPORT_VIEW_IDS

## Verified result

- Both APK variants build successfully; parallel-build unit suite: 18 suites, 104 tests, zero failures/errors. Final lint: zero errors and 154 warnings. Both APK signatures verify.
- Installed parallel APK reports versionName 1.1.2 / versionCode 4. Settings and database files were compared before and after the in-place update and preserved; the task remained stopped during installation.
- Actual service reports `reportsViewIds=true`. Stopped-state verification resolves 13 visible conversation rows, compared with zero in 1.1.1.
- The user subsequently started the floating task. At 22:46:10 on 2026-10-09, it scanned 13 rows and selected two unread candidates. Runtime confirmed successful replies at 22:46:27 and 22:46:56, with model HTTP 200 and `conversation reply ended; sent=true` for both. No names, message text, credentials, or full device logs are included here.
