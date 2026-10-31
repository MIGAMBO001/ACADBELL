package com.acadbell.app.ui.screens.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acadbell.app.alarm.ReadingSessionService
import com.acadbell.app.data.db.SessionRunEntity
import com.acadbell.app.data.db.TopicEntity
import com.acadbell.app.di.ServiceLocator
import com.acadbell.app.domain.OccurrenceState
import com.acadbell.app.domain.RunOutcome
import com.acadbell.app.domain.TimeUtils
import com.acadbell.app.domain.TopicStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ActiveSessionUiState(
    val run: SessionRunEntity? = null,
    val topics: List<TopicEntity> = emptyList(),
    val serviceState: ReadingSessionService.UiState = ReadingSessionService.UiState(),
    val loading: Boolean = true
) {
    val completedCount: Int get() = topics.count { it.status == TopicStatus.COMPLETED }
    val pendingCount: Int get() = topics.count { it.status == TopicStatus.PENDING }
    val topicsFraction: Float
        get() = if (topics.isEmpty()) 0f else completedCount.toFloat() / topics.size
}

class SessionViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.repository(app)

    private val _uiState = MutableStateFlow(ActiveSessionUiState())
    val uiState: StateFlow<ActiveSessionUiState> = _uiState.asStateFlow()

    /** Mirror of the foreground service's live timer. */
    val serviceState: StateFlow<ReadingSessionService.UiState> = ReadingSessionService.state

    private var watchedRunId: Long = -1L

    fun load(runId: Long) {
        viewModelScope.launch {
            val run = repo.getRun(runId)
            if (run == null) {
                _uiState.value = _uiState.value.copy(loading = false)
                return@launch
            }
            watchedRunId = runId
            val sessionId = run.sessionId
            val topicIds = sessionId?.let { repo.topicIdsForSession(it) } ?: emptyList()
            val topics = repo.getTopics(topicIds)
            _uiState.value = ActiveSessionUiState(run = run, topics = topics, loading = false)

            // Keep the run row fresh (it is updated by the service).
            repo.observeRun(runId).collect { latest ->
                if (latest != null) {
                    _uiState.value = _uiState.value.copy(run = latest)
                }
            }
        }
    }

    /** Reloads topics so the summary and the active list always reflect the database. */
    fun refreshTopics() {
        viewModelScope.launch {
            val run = _uiState.value.run ?: return@launch
            val topicIds = run.sessionId?.let { repo.topicIdsForSession(it) } ?: return@launch
            _uiState.value = _uiState.value.copy(topics = repo.getTopics(topicIds))
        }
    }

    fun toggleTopic(topicId: Long, completed: Boolean) {
        viewModelScope.launch {
            repo.setTopicCompleted(topicId, completed)
            refreshTopics()
        }
    }

    fun addQuickTopic(title: String) {
        val run = _uiState.value.run ?: return
        val sessionId = run.sessionId ?: return
        if (title.isBlank()) return
        viewModelScope.launch {
            val courseId = run.courseId ?: return@launch
            val newId = repo.addTopic(courseId, title, "", 0)
            repo.assignTopicsToSession(sessionId, repo.topicIdsForSession(sessionId) + newId)
            refreshTopics()
        }
    }

    // ── Service controls ──

    fun pause() = ReadingSessionService.pause(getApplication())
    fun resume() = ReadingSessionService.resume(getApplication())
    fun extend(minutes: Int) = ReadingSessionService.extend(getApplication(), minutes)

    fun stop(onStopped: (Long) -> Unit) {
        ReadingSessionService.stop(getApplication())
        viewModelScope.launch {
            // Give the service a moment to persist, then read the finished run.
            kotlinx.coroutines.delay(400)
            val id = _uiState.value.run?.id ?: return@launch
            onStopped(id)
        }
    }

    /** Ends the run from the UI when the service is not running (e.g. after process death). */
    fun finishManually(outcome: RunOutcome, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val run = _uiState.value.run ?: return@launch
            repo.finishRun(run.id, outcome, "Ended from session screen")
            onDone(run.id)
        }
    }

    // ── Summary ──

    fun finaliseSummary(runId: Long, onReady: (SessionSummaryData) -> Unit) {
        viewModelScope.launch {
            val run = repo.getRun(runId) ?: return@launch
            val sessionId = run.sessionId
            val topicIds = sessionId?.let { repo.topicIdsForSession(it) } ?: emptyList()
            val topics = repo.getTopics(topicIds)
            val completed = topics.count { it.status == TopicStatus.COMPLETED }
            val pending = topics.count { it.status == TopicStatus.PENDING }
            // Record how many topics the student actually ticked for this run.
            repo.updateRunCounts(runId, completed, pending)

            onReady(
                SessionSummaryData(
                    run = run,
                    topics = topics,
                    completedCount = completed,
                    pendingCount = pending
                )
            )
        }
    }

    fun rescheduleUnfinished(
        runId: Long,
        dateEpochDay: Long,
        startMinute: Int,
        endMinute: Int,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val run = repo.getRun(runId) ?: return@launch
            val sessionId = run.sessionId ?: return@launch
            val topicIds = repo.topicIdsForSession(sessionId)
            val pendingTopics = repo.getTopics(topicIds).filter { it.status == TopicStatus.PENDING }
            if (pendingTopics.isEmpty()) return@launch
            repo.rescheduleUnfinishedTopics(
                topicIds = pendingTopics.map { it.id },
                dateEpochDay = dateEpochDay,
                startMinute = startMinute,
                endMinute = endMinute,
                courseId = run.courseId,
                title = "${run.courseCode} catch-up"
            )
            onDone()
        }
    }

    /** Marks an occurrence as done so it disappears from "pending" on Home. */
    fun closeOccurrence(occurrenceId: Long?) {
        if (occurrenceId == null) return
        viewModelScope.launch {
            val occ = repo.getOccurrence(occurrenceId) ?: return@launch
            repo.log("OCCURRENCE_REVIEWED", "state=${occ.state}")
        }
    }
}

data class SessionSummaryData(
    val run: SessionRunEntity,
    val topics: List<TopicEntity>,
    val completedCount: Int,
    val pendingCount: Int
) {
    val allocatedMinutes: Long get() = run.allocatedSeconds / 60
    val actualMinutes: Long get() = run.actualSeconds / 60
    val efficiencyPercent: Int
        get() = if (run.allocatedSeconds == 0L) 0
        else ((run.actualSeconds * 100) / run.allocatedSeconds).toInt().coerceIn(0, 200)
    val dateLabel: String get() = TimeUtils.formatDateShort(run.dateEpochDay)
}
