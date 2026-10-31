# ACADBELL — Build, Install & Test Guide

> **Plan your reading. Stay consistent. Track your progress.**

Everything below is offline. ACADBELL has **no `INTERNET` permission** — verified in the built APK.

---

## 1. What you have

| File | Purpose |
|---|---|
| `dist/ACADBELL-v1.0.0-debug.apk` | **Install this on your phone** |
| `app/` | Full Kotlin / Jetpack Compose source |
| `docs/ARCHITECTURE.md` | Architecture & design decisions |
| `docs/TEST-REPORT.md` | What was tested and the results |

Install by copying the APK to the phone and tapping it (allow "install from unknown sources"),
or over USB:

```bash
adb install -r dist/ACADBELL-v1.0.0-debug.apk
```

---

## 2. Requirements to rebuild from source

| Tool | Version |
|---|---|
| JDK | 17 or 21 |
| Android SDK | Platform 34, Build-Tools 34.0.0 |
| Gradle | 8.7 (or use the wrapper / Android Studio) |
| Android Studio | Ladybug or newer (optional) |

`local.properties` must point at your SDK:

```properties
sdk.dir=/path/to/Android/Sdk
```

Build commands:

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:assembleRelease      # release APK (signed with app/acadbell-release.jks)
./gradlew :app:testDebugUnitTest    # 25 unit tests
```

> The bundled keystore (`app/acadbell-release.jks`, password `acadbell`) exists only so the project
> produces an installable release APK out of the box. **Replace it before publishing to Play Store.**

Opening the project in Android Studio: *File → Open → select the `acadbell` folder*. Studio will
offer to generate a Gradle wrapper; accept it.

---

## 3. Supported devices

* **minSdk 26** (Android 8.0) → **targetSdk 34** (Android 14)
* Covers essentially every phone in use today, including low-RAM Tecno / Infinix / itel devices.
* No Google Play Services required. No network required at any point.

---

## 4. First run — the 5-minute setup

1. **Allow notifications** when prompted. This is required for start/end alarms.
2. Go to **Settings → Alarm reliability**:
   * Tap **Exact alarms → Allow** (Android 12+).
   * Tap **Battery optimisation → Allow** (critical on Tecno/Infinix/Xiaomi/Huawei —
     these brands aggressively kill background alarms).
3. Tap **Test in 60s**, lock the screen, and confirm the alarm fires. This proves the whole chain
   works on *your* phone before you rely on it.
4. **My Courses → Add course** → enter code + title.
5. Open the course → **Paste list** → paste your outline → **Review** → **Save**.
6. **Timetable → Session** → set title, course, start/end time, repeat days, assign topics → **Create**.
7. At the start time an alarm rings with **Start reading / Snooze / Dismiss**.

---

## 5. Verifying every required feature

| # | Requirement (§9) | How to verify |
|---|---|---|
| 1 | Create courses | My Courses → Add course |
| 2 | Paste course topics | Course → Paste list → Review screen shows each topic, editable and individually tickable |
| 3 | Multiple daily reading sessions | Timetable → Session, add 2–3 for the same day |
| 4 | Start & end alarms | Set a session 2 minutes from now; the START alarm rings, then the END alarm |
| 5 | Monitor reading duration | Tap **Start reading now** on an occurrence → live countdown |
| 6 | Mark completed topics | Tick a checkbox in the active session |
| 7 | Reschedule unfinished topics | Let the timer expire → summary screen → **Reschedule unfinished topics** |
| 8 | View statistics | Progress tab: total hours, completed/missed/interrupted, course %, 7-day chart, consistency |
| 9 | Data survives restart | Force-stop the app, reboot the phone, reopen — data and alarms intact |

**Alarm with the app closed:** open ACADBELL → create a session 3 minutes out → press Home →
swipe ACADBELL out of recents → wait. The alarm still fires (AlarmManager + manifest receiver).

**Alarm after reboot:** create a session, reboot the phone, don't open ACADBELL. The alarm fires,
because `BootCompletedReceiver` re-arms everything.

---

## 6. The one rule the app never breaks

> **Expiring the timer never completes a topic.**

When the end alarm fires you get a summary with what you actually read and a review list. Topics
you did not tick stay `PENDING` and can be rescheduled to a catch-up session. Only the checkbox
writes `Topic.status = COMPLETED`.

---

## 7. Backup & restore

**Settings → Backup & data**

* **Export** writes a JSON file (courses, topics, sessions, history) wherever you choose via the
  system file picker — no storage permissions needed.
* **Restore** merges that file back in. Safe to run on a fresh install (course IDs are remapped).

The database also survives app updates, force-stops and reboots. It is only lost if you uninstall
the app or clear its data — export first if you care about your history.

---

## 8. Troubleshooting

| Symptom | Fix |
|---|---|
| Alarm rings late | Exact-alarm permission off, or battery optimisation on. Settings → Alarm reliability. |
| No alarm at all | Notifications blocked for ACADBELL, or session not enabled. Check Diagnostics. |
| Phone rebooted and no alarm | Open the app once — `BootCompletedReceiver` + WorkManager will re-arm. Then check Diagnostics. |
| Timezone change | Sessions are re-anchored to local wall-clock time automatically (`TimeChangeReceiver`). |
| Two sessions at once | Prevented by design: starting a session while one is active resumes the existing one. |
| Timer shows "Not running" | The foreground service was killed. Tap **Resume timing** — the checkpoint keeps your minutes. |

---

## 9. Architecture notes for future maintenance

* `ServiceLocator` holds the single database/repository/alarm instances per process.
* The repository is the only writer to Room. UI → ViewModel → repository.
* Occurrences (per-day rows) are **materialised 30 days ahead** and re-materialised on boot, on
  timezone change, and on every app launch. Unique `(sessionId, epochDay)` makes it idempotent.
* Alarm request codes are `occurrenceId * 10 + kind`, so re-arming replaces rather than duplicates.
* The foreground service checkpoints elapsed seconds every 15 s, so a kill loses ≤ 15 s of reading.
