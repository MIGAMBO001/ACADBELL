package com.acadbell.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.acadbell.app.domain.AlarmKind
import com.acadbell.app.domain.OccurrenceState
import com.acadbell.app.domain.RunOutcome
import com.acadbell.app.domain.TopicStatus

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val code: String,
    val title: String,
    val lecturer: String = "",
    val colorHex: String = "#C62828",
    val weeklyTargetMinutes: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "topics",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("courseId"), Index("status")]
)
data class TopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val courseId: Long,
    val title: String,
    val note: String = "",
    val estimatedMinutes: Int = 0,
    val status: TopicStatus = TopicStatus.PENDING,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
)

/**
 * A reading-session *template*. Recurring sessions encode [daysOfWeekMask]
 * (bit 0 = Monday ... bit 6 = Sunday); one-off sessions use [specificDateEpochDay].
 */
@Entity(
    tableName = "reading_sessions",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("courseId"), Index("daysOfWeekMask")]
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val courseId: Long? = null,
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val daysOfWeekMask: Int = 0,
    val isRecurring: Boolean = true,
    val specificDateEpochDay: Long? = null,
    val alarmSound: String = "CLASSIC_BELL",
    val vibration: Boolean = true,
    val snoozeMinutes: Int = 5,
    val enabled: Boolean = true,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** Topic ↔ session assignment (explicit many-to-many join table). */
@Entity(
    tableName = "session_topics",
    primaryKeys = ["sessionId", "topicId"],
    foreignKeys = [
        ForeignKey(
            entity = ReadingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("topicId")]
)
data class SessionTopicCrossRef(
    val sessionId: Long,
    val topicId: Long
)

/**
 * One concrete day-instance of a session.
 *
 * The occurrence id seeds the alarm request code, so re-scheduling the same day
 * REPLACES the pending alarm instead of stacking a duplicate.
 */
@Entity(
    tableName = "session_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = ReadingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId", "dateEpochDay"], unique = true), Index("state")]
)
data class SessionOccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val dateEpochDay: Long,
    val plannedStartMillis: Long,
    val plannedEndMillis: Long,
    val state: OccurrenceState = OccurrenceState.SCHEDULED,
    val runId: Long? = null
)

/**
 * A *performed* reading session — the honest record.
 * [allocatedSeconds] is what the plan said; [actualSeconds] is what the student really read.
 */
@Entity(
    tableName = "session_runs",
    indices = [Index("occurrenceId"), Index("dateEpochDay"), Index("status")]
)
data class SessionRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val occurrenceId: Long? = null,
    val sessionId: Long? = null,
    val courseId: Long? = null,
    val courseCode: String = "",
    val courseTitle: String = "",
    val sessionTitle: String = "",
    val dateEpochDay: Long = 0L,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val allocatedSeconds: Long = 0L,
    val actualSeconds: Long = 0L,
    val pausedSeconds: Long = 0L,
    val extendedSeconds: Long = 0L,
    val topicsCompletedCount: Int = 0,
    val topicsPendingCount: Int = 0,
    val outcome: RunOutcome = RunOutcome.IN_PROGRESS,
    val status: String = "ACTIVE", // ACTIVE | FINISHED
    val note: String = ""
)

/** Mirrors a scheduled alarm so cancellation is precise and duplicates are detectable. */
@Entity(
    tableName = "alarm_records",
    indices = [Index("occurrenceId"), Index(value = ["requestCode", "kind"], unique = true)]
)
data class AlarmRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val occurrenceId: Long? = null,
    val sessionId: Long? = null,
    val kind: AlarmKind,
    val triggerAtMillis: Long,
    val requestCode: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val firedAt: Long? = null,
    val cancelled: Boolean = false
)

/** Lightweight audit log used by the in-app diagnostics screen. */
@Entity(tableName = "app_events")
data class AppEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,
    val message: String,
    val createdAt: Long = System.currentTimeMillis()
)
