package com.acadbell.app.ui.screens.progress

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acadbell.app.data.db.SessionRunEntity
import com.acadbell.app.di.ServiceLocator
import com.acadbell.app.domain.OccurrenceState
import com.acadbell.app.domain.TimeUtils
import com.acadbell.app.domain.stats.ProgressCalculator
import com.acadbell.app.repository.model.CourseProgress
import com.acadbell.app.repository.model.DayBar
import com.acadbell.app.repository.model.HistoryItem
import com.acadbell.app.repository.model.ProgressSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProgressUiState(
    val snapshot: ProgressSnapshot = ProgressSnapshot(),
    val bars: List<DayBar> = emptyList(),
    val courses: List<CourseProgress> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val scheduledVsActual: String = "",
    val totalScheduledMinutes: Int = 0,
    val monthBars: List<DayBar> = emptyList()
)

class ProgressViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.repository(app)
    private val today = TimeUtils.todayEpochDay()

    val uiState: StateFlow<ProgressUiState> = combine(
        repo.observeFinishedRuns(),
        repo.observeAllTopics(),
        repo.observeCourses(),
        repo.observeOccurrencesBetween(today - 60, today + 7)
    ) { runs: List<SessionRunEntity>, topics, courses, occurrences ->
        val missedOccurrences = occurrences.count { it.state == OccurrenceState.MISSED }
        val snapshot = ProgressCalculator.snapshot(
            runs = runs,
            topics = topics,
            scheduledToday = occurrences.count { it.dateEpochDay == today },
            nowEpochDay = today
        ).copy(missedSessions = missedOccurrences)

        val courseProgress = ProgressCalculator.courseProgress(courses, topics, runs)
        val history = runs.take(30).map { run ->
            HistoryItem(
                runId = run.id,
                title = run.sessionTitle,
                courseCode = run.courseCode.ifBlank { "GEN" },
                dateLabel = TimeUtils.formatDateShort(run.dateEpochDay) + " · " + TimeUtils.formatTime(run.startedAt),
                actualSeconds = run.actualSeconds,
                allocatedSeconds = run.allocatedSeconds,
                outcome = run.outcome.name,
                topicsCompleted = run.topicsCompletedCount,
                topicsPending = run.topicsPendingCount
            )
        }

        val scheduledMinutes = occurrences.filter {
            it.dateEpochDay in (today - 28)..today
        }.sumOf { ((it.plannedEndMillis - it.plannedStartMillis) / 60_000).toInt() }

        ProgressUiState(
            snapshot = snapshot,
            bars = ProgressCalculator.dailyBars(runs, today, 7),
            monthBars = ProgressCalculator.dailyBars(runs, today, 28),
            courses = courseProgress,
            history = history,
            totalScheduledMinutes = scheduledMinutes,
            scheduledVsActual = "${snapshot.minutesThisWeek / 60}h ${snapshot.minutesThisWeek % 60}m this week"
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    fun reconcile() {
        viewModelScope.launch { repo.reconcileState() }
    }
}
