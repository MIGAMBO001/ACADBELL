package com.acadbell.app.domain

/** Lifecycle state of a single topic belonging to a course. */
enum class TopicStatus { PENDING, COMPLETED }

/** State of one concrete day-instance of a (possibly recurring) reading session. */
enum class OccurrenceState { SCHEDULED, DONE, MISSED, INTERRUPTED, SKIPPED }

/** How a performed reading session ended. */
enum class RunOutcome { COMPLETED, STOPPED_EARLY, INTERRUPTED, MISSED, EXTENDED, IN_PROGRESS }

/** START fires when reading should begin, END when allocated time is exhausted. */
enum class AlarmKind { START, END, SNOOZE }

/** Alarms the student can choose from, all synthesised locally (no audio assets needed). */
enum class AlarmSoundChoice(val label: String) {
    CLASSIC_BELL("Classic bell"),
    DIGITAL("Digital beep"),
    CHIME("Soft chime"),
    GENTLE("Gentle rise"),
    SILENT("Silent (vibrate only)")
}

/** Coarse status used by the Home dashboard tiles. */
enum class SessionUiState { UPCOMING, IN_PROGRESS, COMPLETED, MISSED, INTERRUPTED, RUNNING_NOW }
