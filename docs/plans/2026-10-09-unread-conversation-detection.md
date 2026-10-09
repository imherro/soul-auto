# Unread Conversation Detection Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Recognize unread Soul conversations when `item_content_root` is absent, and compile an APK containing the fix.

**Architecture:** Resolve rows from the accessibility ancestry of visible contact-name nodes rather than requiring one row resource ID. A pure Kotlin policy associates badges and timestamps only inside a container with exactly one visible contact name; scanning and opening use the same resolver. Preserve the existing conversation verification before sending.

**Tech Stack:** Kotlin, Android AccessibilityService, JUnit 4, Android SDK 35, JDK 17, Gradle 8.9.

---

## Evidence and scope

- MuMu Android 15, Soul 5.29.0, installed SoulBot versionName 1.1.
- Runtime repeatedly reports `visibleRows=0; visibleBadges=1; candidates=0`.
- `scanUnread()` and `findConversation()` both require `item_content_root`.
- Fix row association and add diagnostics; do not change model configuration or enable new messaging modes.

### Task 1: Regression coverage

Create `android-app/app/src/test/java/com/soulbot/app/ConversationRowPolicyTest.kt`.
Cover missing legacy IDs, legacy layout, badges on avatar siblings, adjacent rows, hidden recycled nodes, duplicate names, zero badges, and an ambiguous shared parent. Use synthetic node bounds and ancestry, without real chat content.

### Task 2: Shared row resolver

Create `android-app/app/src/main/java/com/soulbot/app/ConversationRowPolicy.kt`.
Represent node roles, parent IDs, bounds, visibility and clickability. Stop row ancestry at list/root boundaries or a parent containing multiple contacts. Resolve unread badges only through actual descendants; skip duplicate contact names because existing reply routing uses names.

Modify `android-app/app/src/main/java/com/soulbot/app/SoulBotService.kt`.
Read one bounded tree snapshot on the list, then use the policy in both `scanUnread()` and `findConversation()`. Include visible-name and fallback-row counts in privacy-safe logs. Scan row badges even when the bottom navigation dot is absent.

### Task 3: Build and deliver

Update `android-app/app/build.gradle` to versionCode 3 / versionName 1.1.1, document the compatibility behavior in both READMEs.

Run with the existing local JDK/SDK:

```powershell
gradle.bat testDebugUnitTest assembleDebug lintDebug --console=plain
```

Check test reports, lint results, APK metadata and signature. Copy the APK and a short validation report to the workspace delivery folder. Compilation does not constitute an end-to-end messaging test; do not claim that without runtime evidence.

The installed APK and the local debug keystore have different certificates. Keep the standard APK and also build a parallel-install APK with `-PsoulbotApplicationId=com.soulbot.app.unreadfix`; do not uninstall or overwrite the existing app. Allow an optional application ID and manifest label for this build, preserving the default ID. Its model configuration and permissions are independent of the existing app.

### Task 4: Synchronize the fork

Review the diff, commit only this fix, verify `origin` is `imherro/soul-auto`, fetch remote changes, and push a fast-forward update to the fork's default branch. Keep artifacts, credentials, local SDK paths and emulator data out of Git.
