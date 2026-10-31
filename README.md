# ACADBELL

**Plan your reading. Stay consistent. Track your progress.**

A free, fully offline academic reading timetable, alarm and topic-tracking app for students.
Native Android · Kotlin · Jetpack Compose · Room · AlarmManager · DataStore · WorkManager.

> **No `INTERNET` permission.** Verified in the built APK. No accounts, no cloud, no ads, no tracking.

---

## 📱 Install

| File | Size | Use |
|---|---|---|
| **`dist/ACADBELL-v1.0.0-release.apk`** | 7.8 MB | **Install this** |
| `dist/ACADBELL-v1.0.0-debug.apk` | 12 MB | Debug build, installs alongside (`.debug` suffix) |

```bash
adb install -r dist/ACADBELL-v1.0.0-release.apk
```
Or copy the APK to your phone and tap it (allow "install from unknown sources").

**Works on Android 8.0 → 14** (minSdk 26, targetSdk 34).

---

## What it does

| Section | What you get |
|---|---|
| **Home** | Today's date, today's sessions, next alarm countdown, reading progress ring, completed vs pending, topics waiting |
| **Timetable** | Day / Week / Month views, multiple sessions per day, per-session start & end times, weekly recurrence, edit & delete |
| **My Courses** | Course code + title + lecturer + colour, per-course progress, topic lists |
| **Topics** | Add manually, **paste a whole outline and auto-split it**, review before saving, assign to sessions, tick off while reading, reschedule unfinished |
| **Progress** | Total reading hours (actual, not planned), completed/missed/interrupted sessions, topics done/left, per-course %, 7-day chart, streak and consistency |
| **Settings** | Theme (light/dark/system), alarm sound & vibration, snooze length, permission health checks, test alarm, backup & restore, diagnostics |

### The rule ACADBELL never breaks

> The timer expiring **never** marks a topic complete. Only your tap does.

When allocated time runs out you get a summary of what you actually read, a review list, and an
option to reschedule whatever is still pending into a catch-up session.

---

## Alarms that work when the app is closed

* `AlarmManager.setExactAndAllowWhileIdle` — fires with the app closed and in Doze.
* Start **and** end alarm per session; end alarm re-armed when you extend.
* Exact-alarm and notification permission handling with in-app fix-it screens.
* `BOOT_COMPLETED` + `MY_PACKAGE_REPLACED` receivers and a daily WorkManager job re-arm everything.
* Timezone / clock changes re-anchor sessions to local wall-clock time.
* Deterministic alarm request codes (`occurrenceId * 10 + kind`) make duplicate alarms impossible.
* Foreground service checkpoints your elapsed time to Room every 15 s, so a kill loses ≤ 15 s.
* Interrupted sessions are detected and offered back to you on relaunch.
* **"Test in 60s"** button so you can prove delivery on your own phone (important on
  Tecno / Infinix / Xiaomi / Huawei, which kill background apps aggressively).

---

## Project layout

```
acadbell/
├── dist/                        ← installable APKs
├── docs/
│   ├── ARCHITECTURE.md          ← data model, alarm strategy, design system
│   ├── BUILD-AND-TEST.md        ← build, install, verify every requirement
│   └── TEST-REPORT.md           ← 25/25 unit tests + manual matrix + known limits
└── app/src/main/java/com/acadbell/app/
    ├── AcadBellApp.kt           ← startup self-healing
    ├── MainActivity.kt          ← navigation host, permissions, deep links
    ├── alarm/                   ← AlarmScheduler, AlarmReceiver, ReadingSessionService,
    │                              Notifications, AlarmSoundPlayer, receivers, MaintenanceWorker
    ├── data/
    │   ├── db/                  ← Room entities, DAOs, converters, database
    │   ├── prefs/               ← DataStore preferences
    │   └── backup/              ← JSON export/import via SAF
    ├── domain/                  ← models, TimeUtils, TopicParser, ProgressCalculator
    ├── repository/              ← single source of truth for all data
    ├── di/ServiceLocator.kt     ← hand-rolled DI (no Hilt, no build risk)
    └── ui/                      ← theme, components, icons, navigation, screens
```

**Tests:** `./gradlew :app:testDebugUnitTest` → 25 tests, 0 failures.

---

## First-run checklist

1. Allow **notifications**.
2. Settings → Alarm reliability → allow **exact alarms** and **battery optimisation exemption**.
3. Tap **Test in 60s** and lock your screen. If it rings, you're good.
4. Add a course → paste your topic list → review → save.
5. Create a session with start/end times and repeat days.
6. Read, tick topics, review the summary, reschedule what's left.

Full instructions: [`docs/BUILD-AND-TEST.md`](docs/BUILD-AND-TEST.md)

---

## Honest limitations

* OEM battery managers can delay alarms unless the app is exempted — ACADBELL explains this in-app
  rather than pretending it can override it.
* `USE_EXACT_ALARM` is a Play-Store-restricted permission (alarm-clock class apps); the app degrades
  gracefully to inexact alarms if unavailable.
* Backup restore is additive: importing the same file twice duplicates courses.
* Replace `app/acadbell-release.jks` (password `acadbell`) before publishing anything.

---

Built free of subscriptions, advertisements and paid dependencies.
