package com.acadbell.app.repository.model

import com.acadbell.app.data.db.ReadingSessionEntity
import com.acadbell.app.data.db.SessionOccurrenceEntity
import com.acadbell.app.data.db.TopicEntity

/** An occurrence joined with its session template — what the timetable and Home actually render. */
data class OccurrenceWithSession(
    val occurrence: SessionOccurrenceEntity,
    val session: ReadingSessionEntity
)

/** A topic with the course code it belongs to, for cross-course lists. */
data class TopicWithCourse(
    val topic: TopicEntity,
    val courseCode: String,
    val courseTitle: String,
    val colorHex: String
)

/** One entry on the Home dashboard. */
data class HomeSessionItem(
    val occurrenceId: Long?,
    val sessionId: Long?,
    val title: String,
    val courseCode: String,
    val courseTitle: String,
    val colorHex: String,
    val startMinute: Int,
    val endMinute: Int,
    val startMillis: Long,
    val endMillis: Long,
    val state: String,
    val topicCount: Int,
    val topicsDone: Int
)

/** Aggregate numbers shown on Home and Progress. */
data class ProgressSnapshot(
    val totalActualSeconds: Long = 0,
    val completedSessions: Int = 0,
    val missedSessions: Int = 0,
    val interruptedSessions: Int = 0,
    val topicsCompleted: Int = 0,
    val topicsRemaining: Int = 0,
    val scheduledSessionsToday: Int = 0,
    val completedToday: Int = 0,
    val minutesToday: Int = 0,
    val minutesThisWeek: Int = 0,
    val activeStudyDayStreak: Int = 0,
    val consistencyPercent: Int = 0
)

/** Per-course row on the Progress screen. */
data class CourseProgress(
    val courseId: Long,
    val code: String,
    val title: String,
    val colorHex: String,
    val topicsTotal: Int,
    val topicsDone: Int,
    val actualSeconds: Long,
    val sessionsDone: Int
) {
    val completionPercent: Int
        get() = if (topicsTotal == 0) 0 else (topicsDone * 100) / topicsTotal
}

/** A single bar in the 7-day chart. */
data class DayBar(
    val epochDay: Long,
    val label: String,
    val minutes: Int,
    val isToday: Boolean
)

/** One row of session history. */
data class HistoryItem(
    val runId: Long,
    val title: String,
    val courseCode: String,
    val dateLabel: String,
    val actualSeconds: Long,
    val allocatedSeconds: Long,
    val outcome: String,
    val topicsCompleted: Int,
    val topicsPending: Int
)
