package com.acadbell.app.domain.stats

import com.acadbell.app.data.db.SessionRunEntity
import com.acadbell.app.data.db.TopicEntity
import com.acadbell.app.domain.RunOutcome
import com.acadbell.app.domain.TimeUtils
import com.acadbell.app.domain.TopicStatus
import com.acadbell.app.repository.model.CourseProgress
import com.acadbell.app.repository.model.DayBar
import com.acadbell.app.repository.model.ProgressSnapshot
import java.time.LocalDate
import java.util.Locale

/**
 * Pure functions that turn stored records into statistics.
 *
 * HARD RULE (§5H): every number here is derived from `SessionRun.actualSeconds` and real
 * topic/topic-status rows — never from `allocatedSeconds`. Scheduled time is shown separately
 * and labelled as scheduled so students are never flattered by a number they did not earn.
 */
object ProgressCalculator {

    fun snapshot(
        runs: List<SessionRunEntity>,
        topics: List<TopicEntity>,
        scheduledToday: Int,
        nowEpochDay: Long = TimeUtils.todayEpochDay()
    ): ProgressSnapshot {
        val finished = runs.filter { it.status == "FINISHED" }
        val todayRuns = finished.filter { it.dateEpochDay == nowEpochDay }

        val weekStart = LocalDate.ofEpochDay(nowEpochDay).with(java.time.DayOfWeek.MONDAY).toEpochDay()
        val weekEnd = weekStart + 6

        val totalSeconds = finished.sumOf { it.actualSeconds }

        val missed = runs.count { it.outcome == RunOutcome.MISSED }
        val interrupted = finished.count {
            it.outcome == RunOutcome.STOPPED_EARLY || it.outcome == RunOutcome.INTERRUPTED
        }

        val completedTopics = topics.count { it.status == TopicStatus.COMPLETED }
        val remainingTopics = topics.count { it.status == TopicStatus.PENDING }

        val activeDays = finished.map { it.dateEpochDay }.distinct()

        return ProgressSnapshot(
            totalActualSeconds = totalSeconds,
            completedSessions = finished.count { it.outcome != RunOutcome.MISSED },
            missedSessions = missed,
            interruptedSessions = interrupted,
            topicsCompleted = completedTopics,
            topicsRemaining = remainingTopics,
            scheduledSessionsToday = scheduledToday,
            completedToday = todayRuns.size,
            minutesToday = (todayRuns.sumOf { it.actualSeconds } / 60).toInt(),
            minutesThisWeek = (finished
                .filter { it.dateEpochDay in weekStart..weekEnd }
                .sumOf { it.actualSeconds } / 60).toInt(),
            activeStudyDayStreak = streak(activeDays, nowEpochDay),
            consistencyPercent = consistency(runs, nowEpochDay)
        )
    }

    /**
     * Consecutive-day streak counting backwards from today.
     * Today not yet read? The streak is not broken — yesterday still counts.
     */
    fun streak(activeDays: List<Long>, todayEpochDay: Long): Int {
        if (activeDays.isEmpty()) return 0
        val set = activeDays.toSet()
        var day = if (set.contains(todayEpochDay)) todayEpochDay else todayEpochDay - 1
        var count = 0
        while (set.contains(day)) {
            count++
            day--
        }
        return count
    }

    /**
     * Consistency = performed sessions ÷ (performed + missed), last 28 days.
     * Interrupted and early-stopped sessions count as performed (you showed up).
     */
    fun consistency(runs: List<SessionRunEntity>, todayEpochDay: Long, window: Int = 28): Int {
        val from = todayEpochDay - window
        val relevant = runs.filter { it.dateEpochDay in from..todayEpochDay }
        val performed = relevant.count { it.status == "FINISHED" && it.outcome != RunOutcome.MISSED }
        val missed = relevant.count { it.outcome == RunOutcome.MISSED }
        val total = performed + missed
        return if (total == 0) 0 else ((performed * 100) / total).coerceIn(0, 100)
    }

    /** Last [days] days of *actual* reading minutes, oldest first. */
    fun dailyBars(runs: List<SessionRunEntity>, todayEpochDay: Long, days: Int = 7): List<DayBar> {
        val finished = runs.filter { it.status == "FINISHED" }
        return (days - 1 downTo 0).map { offset ->
            val day = todayEpochDay - offset
            val minutes = (finished.filter { it.dateEpochDay == day }.sumOf { it.actualSeconds } / 60).toInt()
            DayBar(
                epochDay = day,
                label = LocalDate.ofEpochDay(day)
                    .dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
                    .take(2),
                minutes = minutes,
                isToday = day == todayEpochDay
            )
        }
    }

    fun courseProgress(
        courses: List<com.acadbell.app.data.db.CourseEntity>,
        topics: List<TopicEntity>,
        runs: List<SessionRunEntity>
    ): List<CourseProgress> = courses.map { course ->
        val courseTopics = topics.filter { it.courseId == course.id }
        val courseRuns = runs.filter { it.courseId == course.id && it.status == "FINISHED" }
        CourseProgress(
            courseId = course.id,
            code = course.code,
            title = course.title,
            colorHex = course.colorHex,
            topicsTotal = courseTopics.size,
            topicsDone = courseTopics.count { it.status == TopicStatus.COMPLETED },
            actualSeconds = courseRuns.sumOf { it.actualSeconds },
            sessionsDone = courseRuns.count { it.outcome != RunOutcome.MISSED }
        )
    }

    /** "0m", "1h 05m", "3h 20m" for big totals. */
    fun hoursLabel(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        return TimeUtils.formatDuration(minutes)
    }

    fun hoursDecimal(totalSeconds: Long): String =
        String.format(Locale.US, "%.1f", totalSeconds / 3600.0)
}
