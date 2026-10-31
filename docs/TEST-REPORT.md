# ACADBELL — Test Report (Phase 9)

Date: 2026-10-31 · Build: ACADBELL 1.0.0 (versionCode 1) · Target: Android 14 (API 34), minSdk 26

---

## 1. Build verification

| Check | Result |
|---|---|
| `:app:compileDebugKotlin` | ✅ PASS (0 errors) |
| `:app:assembleDebug` | ✅ PASS → `ACADBELL-v1.0.0-debug.apk`, 12 MB |
| `:app:assembleRelease` | ✅ PASS → `ACADBELL-v1.0.0-release.apk`, 7.8 MB, signed (v1+v2+) |
| `:app:testDebugUnitTest` | ✅ PASS — **25 tests, 0 failures, 0 errors, 0 skipped** |
| `aapt dump badging` | ✅ package `com.acadbell.app`, versionCode 1, versionName 1.0.0, minSdk 26, targetSdk 34 |
| Offline guarantee | ✅ **No `INTERNET` permission in the built manifest** |
| Components declared | ✅ MainActivity (launcher), AlarmReceiver, BootCompletedReceiver, TimeChangeReceiver, ReadingSessionService (mediaPlayback FG), WorkManager provider |

> Both APKs are signed and installable. The release APK is signed with `app/acadbell-release.jks`
> (certificate `CN=ACADBELL, C=NG`) — **replace that keystore before publishing to Play Store**.
> `apksigner verify` confirms the signature; `aapt dump permissions` confirms **zero INTERNET
> permissions** in both artifacts.

---

## 2. Unit test results (25/25)

### TopicParserTest — 9 tests ✅
The paste feature is ACADBELL's core, so it carries the most tests.

| Test | Verifies |
|---|---|
| splits a numbered list | `1.`, `2.`, `3.` prefixes stripped |
| strips bullets roman numerals and letters | `•`, `iii.`, `a)`, `-` all handled |
| removes duplicates case insensitively | "Deadlock"/"DEADLOCK"/"deadlock" → 1 entry, 2 duplicates reported |
| strips page references | "Process Scheduling (pp. 45-52)" → "Process Scheduling" |
| splits semicolon separated topics | inline `;` lists split |
| comma splitting only when it looks like a list | "a, b, c, d" splits; a long prose sentence does **not** |
| ignores blank lines and pure numbers | noise removed |
| handles topic prefix and chapter prefix | "Topic 1:", "Chapter 2:" stripped |
| estimate minutes stays in sane bounds | 5–45 min |

### TimeUtilsTest — 6 tests ✅
What makes alarms fire on the correct day.

| Test | Verifies |
|---|---|
| minute of day maps to formatted time | 1080 → "6:00 PM", 0 → "12:00 AM", 750 → "12:30 PM" |
| duration formatting | 0 → "0m", 45 → "45m", 75 → "1h 15m", 180 → "3h 0m" |
| seconds formatting | 0 → "00:00", 545 → "09:05", 3600 → "1:00:00" |
| weekday mask round trip | Mon/Wed/Fri set ↔ bitmask ↔ "Mon, Wed, Fri" |
| weekday and weekend masks produce friendly labels | → "Weekdays", "Every day" |
| millisAt anchors to local wall clock | 18:00 stays 18:00 local after zone resolution |

### ProgressCalculatorTest — 10 tests ✅
Proves §5H: statistics come from *actual* records, not scheduled time.

| Test | Verifies |
|---|---|
| total reading time uses actual seconds not allocated | 1800+900 actual (not 3600+3600 allocated) = 2700 s |
| in progress runs are excluded from totals | an ACTIVE run's seconds do not inflate stats |
| interrupted sessions counted separately from completed | STOPPED_EARLY + INTERRUPTED = 2 |
| topic counts split pending and completed | 2 done / 1 pending |
| streak counts backwards from today | days 100,99,98,96 → streak 3 |
| streak survives a day not read yet | today empty, yesterday+day-before → 2 |
| consistency counts performed against missed | 2 performed vs 2 missed → 50% |
| daily bars only include reading inside the window | a 50-day-old run is excluded from the 7-day chart |
| course completion percentage | 1 of 4 topics → 25% |
| empty data produces zeroes without dividing by zero | no crash on a fresh install |

---

## 3. Behavioural verification (manual test matrix)

These are the checks to run on a device; the logic behind each is implemented and unit-tested where
it is pure.

| # | Scenario | Expected behaviour | Mechanism |
|---|---|---|---|
| 1 | Create course + paste 40-topic outline | Review screen lists 40 editable rows; save creates 40 topics | `TopicParser` + `TopicReview` |
| 2 | Create 3 sessions on the same day | All 3 appear on Home and Timetable/day view | unique `(sessionId, epochDay)` |
| 3 | Session starts in 1 min, app **closed** | START alarm fires with Start/Snooze/Dismiss actions | `AlarmManager.setExactAndAllowWhileIdle` + `AlarmReceiver` |
| 4 | Tap "Start reading" | Foreground timer starts, notification mirrors countdown | `ReadingSessionService` |
| 5 | Lock screen / switch app for 10 min | Timer keeps counting; no drift | service ticker + 15 s Room checkpoint |
| 6 | Pause for 2 min then resume | Paused time excluded from `actualSeconds` | pause stops accumulation |
| 7 | Extend +10 min | Countdown grows, END alarm re-armed at new finish time | `extend()` cancels + re-arms END |
| 8 | Timer reaches zero | END alarm sounds, run saved as COMPLETED | alarm + local safety net |
| 9 | **Check topics are not auto-completed** | Unticked topics remain `PENDING` | no write path from timer to topic status |
| 10 | Reschedule pending topics | One-off catch-up session created with those topics attached | `rescheduleUnfinishedTopics` |
| 11 | Force-stop app, reboot phone, don't open app | Alarm still fires | `BootCompletedReceiver` + WorkManager |
| 12 | Change timezone | Sessions re-anchor to local wall clock | `TimeChangeReceiver` re-materialises occurrences |
| 13 | Try to start a second session while one runs | Existing run resumes instead of duplicating | guard in `startNew()` |
| 14 | Kill app mid-session, reopen | "Resume timing" keeps saved minutes; stale runs auto-close as INTERRUPTED | `InterruptedSessionRecovery` |
| 15 | Daily use over a week | Progress uses actual minutes only; missed occurrences counted | `ProgressCalculator` |
| 16 | Export → wipe → restore | Courses, topics, sessions and history return | `BackupManager` (JSON, SAF, no permissions) |

---

## 4. Defects found and fixed during development

| # | Defect | Fix |
|---|---|---|
| 1 | `typealias Color` broke Compose's `Color(Long)` overload in the theme | removed alias, used `Color(0xFF…)` explicitly |
| 2 | `Composable` lambda typed as `Column.()` instead of `ColumnScope.()` | corrected receiver type |
| 3 | Alarm request-code helper collided for large occurrence ids | `occurrenceId * 10 + kind.ordinal`, masked to positive Int |
| 4 | `LocalBinder` inner-class init error in the service | replaced with a plain `Binder` |
| 5 | Nullable `dateEpochDay` comparison in navigation | passed `Long?` explicitly |
| 6 | `material-icons-extended` (≈15 MB of dex) OOM-killed dexing on constrained builds | replaced with a hand-built 43-glyph `AcadIcons` set (also shrinks the APK) |
| 7 | WorkManager double-initialisation warning | `AcadBellApp : Configuration.Provider` + startup provider removed from the manifest |
| 8 | Topic links not shown on session rows | sessions now read `session_topics` through the repository |

---

## 5. Known limitations (stated honestly)

1. **OEM battery managers.** On Tecno/Infinix/Xiaomi/Huawei, aggressive task killers can still delay
   alarms unless the app is exempted. ACADBELL explains this in Settings and provides a **Test in 60s**
   button so the student can verify before relying on it. No app can override this from code.
2. **`USE_EXACT_ALARM` policy.** Google Play restricts this permission to alarm-clock/calendar apps.
   ACADBELL qualifies, but if publishing, be ready to justify it in the Play Console declaration.
   The app degrades gracefully to inexact alarms if the permission is unavailable.
3. **No instrumentation tests.** UI was verified by compilation and code review rather than on-device
   automation, because no emulator/device was available in this environment.
4. **Backup restores additively.** Importing the same file twice duplicates courses. This is
   intentional (no silent data destruction), and the export timestamp is shown in the summary.
