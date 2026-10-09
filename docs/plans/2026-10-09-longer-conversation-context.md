# Longer Conversation Context Implementation Plan

**Goal:** Reply with up to 60 recent messages from the visible chat and saved history, supplemented by a persistent per-contact summary and relevant older messages.

**Architecture:** Keep pending-message detection and send confirmation based on the live screen. Build model context separately from saved history with overlap handling and a character budget. Store derived rolling summaries in a new database table; periodically summarize new records through the configured model, retaining attribution and explicit uncertainty. Failed summaries leave existing memory usable.

**Tech Stack:** Kotlin, Android accessibility, SQLiteOpenHelper, existing OpenAI-compatible model router, JUnit.

## Implementation

1. Add pure context policies and regression tests: 60-message limit, suffix merging without duplicate messages, contained older viewports, latest-message preservation under a character budget, incremental summary batches, update threshold, retry cooldown and structured summary validation.
2. Migrate conversation_memory.db from schema 1 to 2 without deleting messages. Add summary state and a message-ID cursor; expose per-contact recent history, summary batches, summary persistence and clearing. Keep raw-message JSON backups compatible; summaries are derived from those records and rebuilt when needed.
3. Assemble reply input from local history plus the live thread. Add cached summaries to personalization and generate incremental updates only when at least 12 new records exist. Bound each summary request to 15 seconds, failures to a 10-minute retry cooldown, and recent context to 12,000 characters. Keep latest incoming messages explicit and protect live send checks.
4. Show rolling-summary counts and the 60-message context behavior in learning settings. Bump to 1.1.4 / code 6.
5. Run testDebugUnitTest, assembleDebug and lintDebug for the parallel package; build the standard package and verify signatures. Update the existing parallel installation with settings and databases preserved; verify real schema migration and stopped-mode compatibility without sending a test message.
6. Inspect the final diff and publish only project source/tests/docs to origin/master. Keep APKs, app data and credentials outside Git.

## Verification commands

Run from android-app using the existing JDK 17, SDK 35 and Gradle 8.9:

```powershell
gradle.bat testDebugUnitTest assembleDebug lintDebug '-PsoulbotApplicationId=com.soulbot.app.unreadfix' --console=plain
gradle.bat assembleDebug --console=plain
```

Live checks: installed version 1.1.4; original settings retained except a deliberate stopped task; raw SQLite message rows preserved; schema 2 and conversation_summaries exist; accessibility and floating control enabled; no assistant-initiated message sent.
