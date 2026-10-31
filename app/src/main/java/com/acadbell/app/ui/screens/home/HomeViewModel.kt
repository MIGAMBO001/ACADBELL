package com.acadbell.app.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acadbell.app.data.db.CourseEntity
import com.acadbell.app.data.db.SessionOccurrenceEntity
import com.acadbell.app.data.db.SessionRunEntity
import com.acadbell.app.data.db.TopicEntity
import com.acadbell.app.di.ServiceLocator
import com.acadbell.app.domain.OccurrenceState
import com.acadbell.app.domain.TimeUtils
import com.acadbell.app.domain.TopicStatus
import com.acadbell.app.domain.stats.ProgressCalculator
import com.acadbell.app.repository.model.HomeSessionItem
import com.acadbell.app.repository.model.ProgressSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val dateLabel: String = "",
    val greeting: String = "",
    val todayItems: List<HomeSessionItem> = emptyList(),
    val nextItem: HomeSessionItem? = null,
    val nextStartsInMinutes: Long? = null,
    val snapshot: ProgressSnapshot = ProgressSnapshot(),
    val scheduledMinutesToday: Int = 0,
    val activeRunId: Long? = null,
    val pendingTopics: List<TopicEntity> = emptyList(),
    val courseLookup: Map<Long, CourseEntity> = emptyMap(),
    val hasCourses: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.repository(app)
    private val todayEpochDay = TimeUtils.todayEpochDay()

    val uiState: StateFlow<HomeUiState> = combine(
        repo.observeOccurrencesBetween(todayEpochDay, todayEpochDay),
        repo.observeSessions(),
        repo.observeCourses(),
        repo.observeRecentRuns(200),
        repo.observeAllTopics(),
        repo.observeActiveRuns()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val occurrences = values[0] as List<SessionOccurrenceEntity>
        @Suppress("UNCHECKED_CAST")
        val sessions = values[1] as List<com.acadbell.app.data.db.ReadingSessionEntity>
        @Suppress("UNCHECKED_CAST")
        val courses = values[2] as List<CourseEntity>
        @Suppress("UNCHECKED_CAST")
        val runs = values[3] as List<SessionRunEntity>
        @Suppress("UNCHECKED_CAST")
        val topics = values[4] as List<TopicEntity>
        @Suppress("UNCHECKED_CAST")
        val activeRuns = values[5] as List<SessionRunEntity>

        val courseById = courses.associateBy { it.id }
        val sessionById = sessions.associateBy { it.id }
        val runByOccurrence = activeRuns.associateBy { it.occurrenceId }

        val items = occurrences.map { occ ->
            val session = sessionById[occ.sessionId]
            val course = session?.courseId?.let { courseById[it] }
            val liveRun = runByOccurrence[occ.id]
            HomeSessionItem(
                occurrenceId = occ.id,
                sessionId = occ.sessionId,
                title = session?.title ?: "Reading session",
                courseCode = course?.code ?: "GEN",
                courseTitle = course?.title ?: "General reading",
                colorHex = course?.colorHex ?: "#C62828",
                startMinute = ((occ.plannedStartMillis % 86_400_000) / 60_000).toInt(),
                endMinute = ((occ.plannedEndMillis % 86_400_000) / 60_000).toInt(),
                startMillis = occ.plannedStartMillis,
                endMillis = occ.plannedEndMillis,
                state = if (liveRun != null) "RUNNING_NOW" else occ.state.name,
                topicCount = session?.let { 0 } ?: 0,
                topicsDone = 0
            )
        }.sortedBy { it.startMillis }

        val now = System.currentTimeMillis()
        val next = items.firstOrNull { it.startMillis > now && it.state != OccurrenceState.DONE.name }

        val snapshot = ProgressCalculator.snapshot(
            runs = runs,
            topics = topics,
            scheduledToday = items.size,
            nowEpochDay = todayEpochDay
        )

        HomeUiState(
            loading = false,
            dateLabel = TimeUtils.formatDateLong(java.time.LocalDate.now()),
            greeting = greetingForNow(),
            todayItems = items,
            nextItem = next,
            nextStartsInMinutes = next?.let { TimeUtils.minutesUntil(it.startMillis, now) },
            snapshot = snapshot,
            scheduledMinutesToday = items.sumOf { ((it.endMillis - it.startMillis) / 60_000).toInt() },
            activeRunId = activeRuns.firstOrNull()?.id,
            pendingTopics = topics.filter { it.status == TopicStatus.PENDING }.take(5),
            courseLookup = courseById,
            hasCourses = courses.isNotEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Keeps occurrence rows and "missed" flags correct the moment Home is opened. */
    fun refresh() {
        viewModelScope.launch {
            repo.materialiseRange(todayEpochDay, todayEpochDay + 7)
            repo.reconcileState()
        }
    }

    private fun greetingForNow(): String {
        val hour = java.time.LocalTime.now().hour
        return when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            hour < 21 -> "Good evening"
            else -> "Late night reading"
        }
    }
}
