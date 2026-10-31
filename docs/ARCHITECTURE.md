# ACADBELL — Architecture & Design Specification

> **Plan your reading. Stay consistent. Track your progress.**

ACADBELL is a free, offline-first academic reading timetable, alarm and topic-tracking app
for university students. Nothing leaves the device. No accounts, no cloud, no ads, no ads SDKs.

---

## 1. Technology Stack (all free, all offline)

| Concern | Choice | Why |
|---|---|---|
| Language | Kotlin 1.9.24 | Native, coroutines, null-safe |
| UI | Jetpack Compose + Material 3 | Single declarative UI tree, first-class dark mode |
| Persistence | Room 2.6.1 (SQLite) | Real relational store, survives process death & reboot |
| Preferences | DataStore (Preferences) | Theme, defaults, onboarding flags |
| Alarms | `AlarmManager` (`setExactAndAllowWhileIdle`) | Only API that fires with the app closed |
| Notifications | `NotificationCompat` + channels | Heads-up alarm + completion notices |
| Background upkeep | WorkManager (periodic) | Re-arm alarms after reboot / timezone / clock change |
| Foreground session | `LifecycleService` + foreground notification | Keeps an active session alive and observable |
| DI | Hand-rolled `ServiceLocator` | Avoids Hilt/KSP overhead and build fragility |
| Build | AGP 8.4.2, Gradle 8.7, JDK 17 target, minSdk 24 / target 34 | Broad device coverage (Android 7 → 14) |

**Zero network permissions are declared in the manifest** — the app physically cannot phone home.

---

## 2. Layered Architecture

```
┌──────────────────────────────────────────────────────────────┐
│  UI  (Compose)  screens/  components/  theme/                 │
│      Home · Timetable · Courses · Progress · Settings         │
│      Session (active reading) · Topics · Review              │
└───────────────▲──────────────────────────────┬───────────────┘
                │ StateFlow<UiState>           │ intents
┌───────────────┴──────────────────────────────▼───────────────┐
│  ViewModel layer  (AndroidViewModel, StateFlow)              │
│  HomeVM · TimetableVM · CourseVM · TopicVM · SessionVM ·     │
│  ProgressVM · SettingsVM · OnboardingVM                      │
└───────────────▲──────────────────────────────┬───────────────┘
                │ Flow<T>                      │ suspend calls
┌───────────────┴──────────────────────────────▼───────────────┐
│  Domain / Repository                                         │
│  AcadBellRepository · ProgressCalculator · TopicParser       │
└───────────────▲──────────────────────────────┬───────────────┘
                │                              │
┌───────────────┴──────────────┐  ┌────────────▼───────────────┐
│  Room (DAOs + entities)      │  │  Platform services         │
│  courses, topics, sessions,  │  │  AlarmScheduler            │
│  alarms, occurrences, runs,  │  │  AlarmReceiver             │
│  session_topics, app events  │  │  ReadingSessionService     │
└──────────────────────────────┘  │  BootRescheduleWorker      │
                                  │  Notifications             │
                                  └────────────────────────────┘
```

Two rules the codebase enforces:

1. **The UI never talks to Room or AlarmManager directly.** It goes through a ViewModel → repository.
2. **A scheduled session and a performed session are different records.** `ReadingSession` (the plan)
   vs `SessionRun` (the reality: startedAt, endedAt, actualSeconds, outcome). This is what makes
   statistics honest.

---

## 3. Data Model (Room, schema v1)

```
Course            (id, code, title, lecturer, colourHex, createdAt, archived)
   │ 1
   │ n
Topic             (id, courseId→, title, note, estimatedMinutes, status, completedAt,
   │               lastSessionId, createdAt)      status = PENDING | COMPLETED
   │ n
   │ n
ReadingSession    (id, courseId→, title, startMinuteOfDay, endMinuteOfDay, daysOfWeek (bitmask),
   │               isRecurring, specificDate, topicIdsCsv, status, alarmSound, vibration,
   │               snoozeMinutes, createdAt)
   │ 1
   │ n
SessionOccurrence (id, sessionId→, dateEpochDay, plannedStart, plannedEnd, state,
   │               runId)   state = SCHEDULED | DONE | MISSED | INTERRUPTED | SKIPPED
   │ 1
   │ 1
SessionRun        (id, courseId, occurrenceId, sessionId, startedAt, endedAt,
                   allocatedSeconds, actualSeconds, pausedSeconds, completedTopics,
                   pendingTopics, outcome, note)
                   outcome = COMPLETED | STOPPED_EARLY | MISSED | INTERRUPTED | EXTENDED
AlarmRecord       (id, occurrenceId, kind (START|END), triggerAt, requestCode, firedAt, cancelled)
AppEvent          (id, type, message, createdAt)   // audit trail / diagnostics
```

### Why `SessionOccurrence` exists
A recurring session ("Physics, Mon–Fri 18:00–20:00") is a *template*. Every concrete day becomes an
**occurrence** with its own PendingIntent request code. That gives:

* **Duplicate alarm prevention** — occurrence id (not session id) seeds the request code, so
  re-scheduling the same day twice cancels/replaces rather than stacking.
* **Honest "missed" tracking** — an occurrence that never started is `MISSED`, which is *not* the
  same as a session that started and was abandoned (`INTERRUPTED`).
* **Idempotent history** — one row per day, no double counting when the app restarts.

### Topic completion rule (hard requirement §5G)
`ReadingSession`/`SessionRun` completion **never** writes `Topic.status = COMPLETED`.
The only writers are: (a) the student tapping a checkbox in the active session, (b) an explicit
toggle in My Courses / Topics. Expired timers leave topics `PENDING` and offer rescheduling.

---

## 4. Alarm Reliability Strategy

| Risk | Mitigation |
|---|---|
| App closed / swiped away | `AlarmManager.setExactAndAllowWhileIdle` — OS-level, process-independent |
| Doze mode | `setExactAndAllowWhileIdle` + `AlarmManager.canScheduleExactAlarms()` gate |
| Android 12+ exact-alarm denial | Detect, degrade to `setAndAllowWhileIdle`, surface a Settings banner deep-linking to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` |
| Android 13+ notifications blocked | Runtime `POST_NOTIFICATIONS` request on first run + Settings health card |
| Device reboot | `BOOT_COMPLETED` receiver → WorkManager one-shot → re-arms every future occurrence |
| Timezone change | `TIMEZONE_CHANGED` / `TIME_SET` receiver → full reschedule |
| Duplicate alarms | Deterministic `requestCode` from `occurrenceId` (hash) + `FLAG_UPDATE_CURRENT`; alarms also mirrored in `AlarmRecord` so cancellation is targeted |
| OEM battery optimisation (Xiaomi, Huawei, Tecno, Infinix, itel) | Explain in-app; offer `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`; "Test alarm in 1 minute" self-check |
| Session interrupted by kill | `SessionRun` persisted on every state change (start/pause/resume/tick-save); on relaunch, an open run with no END alarm is offered **Resume** or **Close as interrupted** |

End alarm is scheduled *at session start*, so an end alarm exists even if the app dies mid-session.

---

## 5. Visual Design System

Inspired by the reference: **confident red, generous white, calm grey, rounded everything.**

```
Primary        #C62828  (ACADBELL Red)
PrimaryDark    #8E1B1B
Accent/Cont.   #FFEBEE  (red 50 tint for selected states)
Surface        #FFFFFF  (light)      /  #121212 (dark)
SurfaceAlt     #F5F5F7  (light)      /  #1E1E20 (dark)
Outline        #E4E4E8  (light)      /  #2C2C30 (dark)
TextPrimary    #1A1A1F  / #F2F2F5
TextSecondary  #6B6B76  / #A0A0AC
Success        #2E7D32   Warning #F9A825   Danger #C62828
Corner radii   20dp cards · 14dp buttons · 28dp pills · 28dp FAB
Type           Display/Headline: semi-bold, tight tracking
               Body: system sans (Roboto), 14–16sp, 1.35 line height
Spacing        4dp grid; screen gutter 20dp; card padding 16dp
```

**Layout language**

* Slim top area: greeting + date, with a circular "today's completion" ring.
* Cards float on `SurfaceAlt` with 20dp radius, no heavy shadows — 1dp hairline `Outline`.
* The active session is the only place with a saturated red surface (`Primary` fill, white text).
* Bottom navigation: 5 items, filled icons when selected, red indicator pill.
* Dark mode mirrors the palette; red is lightened to `#EF5350` for contrast on dark surfaces.

---

## 6. Navigation Map

```
Bottom nav ── Home ─────── SessionDetail / ActiveSession / SessionSummary
           ├─ Timetable ── AddEditSession / Week & Month views / SessionDetail
           ├─ My Courses ─ CourseDetail / AddEditCourse / TopicList
           │                 └── PasteTopics → TopicReview
           ├─ Progress ─── CourseProgressDetail
           └─ Settings ─── Permissions & Reliability / Backup & Restore / About
```

---

## 7. Permissions Declared

```xml
POST_NOTIFICATIONS            (33+, runtime)
SCHEDULE_EXACT_ALARM          (31–32 granted at install, 33+ user-toggle)
USE_EXACT_ALARM               (33+, alarm-clock class app, auto-granted)
RECEIVE_BOOT_COMPLETED
VIBRATE
WAKE_LOCK
FOREGROUND_SERVICE
FOREGROUND_SERVICE_MEDIA_PLAYBACK  (34+ for session service)
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
```

No `INTERNET`. No `READ/WRITE_EXTERNAL_STORAGE` (backup uses app-private files + SAF `CreateDocument`).

---

## 8. Development Phases

| Phase | Scope | Deliverable |
|---|---|---|
| 1 | Project structure, design system, theme, navigation shell | compiles |
| 2 | Data layer + Home dashboard | live data on Home |
| 3 | Course & Topic management (incl. paste parser + review) | full CRUD |
| 4 | Reading timetable (day / week / month, recurrence) | scheduling UI |
| 5 | Alarm system (start/end, boot recovery, permissions) | fires with app closed |
| 6 | Active reading session (timer, topics, pause/extend) | foreground session |
| 7 | Completion, history, rescheduling unfinished topics | session summaries |
| 8 | Statistics & progress dashboard | real numbers |
| 9 | Testing, debugging, APK generation | installable APK |
