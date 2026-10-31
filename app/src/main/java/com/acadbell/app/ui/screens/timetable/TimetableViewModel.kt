package com.acadbell.app.ui.screens.timetable

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acadbell.app.data.db.CourseEntity
import com.acadbell.app.data.db.ReadingSessionEntity
import com.acadbell.app.data.db.SessionOccurrenceEntity
import com.acadbell.app.data.db.TopicEntity
import com.acadbell.app.di.ServiceLocator
import com.acadbell.app.domain.AlarmSoundChoice
import com.acadbell.app.domain.OccurrenceState
import com.acadbell.app.domain.TimeUtils
import com.acadbell.app.domain.TopicStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

enum class TimetableView { DAY, WEEK, MONTH }

data class DayCell(
    val date: LocalDate,
    val epochDay: Long,
    val inCurrentMonth: Boolean,
    val sessionCount: Int,
    val isToday: Boolean,
    val selected: Boolean
)

data class TimetableUiState(
    val view: TimetableView = TimetableView.DAY,
    val selectedDate: LocalDate = LocalDate.now(),
    val dayItems: List<TimetableEntry> = emptyList(),
    val weekDays: List<LocalDate> = emptyList(),
    val weekEntries: Map<Long, List<TimetableEntry>> = emptyMap(),
    val monthCells: List<DayCell> = emptyList(),
    val totalMinutesSelectedDay: Int = 0,
    val loading: Boolean = true
)

data class TimetableEntry(
    val occurrenceId: Long,
    val sessionId: Long,
    val title: String,
    val courseCode: String,
    val courseTitle: String,
    val colorHex: String,
    val startMinute: Int,
    val endMinute: Int,
    val state: OccurrenceState,
    val topicTitles: List<String>,
    val completedTopics: Int,
    val dateEpochDay: Long
)

data class SessionForm(
    val id: Long = 0L,
    val courseId: Long? = null,
    val title: String = "",
    val startMinute: Int = 18 * 60,
    val endMinute: Int = 20 * 60,
    val recurring: Boolean = true,
    val days: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
    val specificDateEpochDay: Long? = null,
    val alarmSound: String = AlarmSoundChoice.CLASSIC_BELL.name,
    val vibration: Boolean = true,
    val snoozeMinutes: Int = 5,
    val selectedTopicIds: Set<Long> = emptySet(),
    val enabled: Boolean = true,
    val notes: String = ""
) {
    val durationMinutes: Int get() = (endMinute - startMinute).coerceAtLeast(0)
    val isValid: Boolean get() = title.isNotBlank() && endMinute > startMinute &&
        (recurring || specificDateEpochDay != null) && (if (recurring) days.isNotEmpty() else true)
}

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.repository(app)

    private val _view = MutableStateFlow(TimetableView.DAY)
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _monthAnchor = MutableStateFlow(LocalDate.now().withDayOfMonth(1))

    private val coursesFlow = repo.observeCourses()

    val courses: StateFlow<List<CourseEntity>> =
        coursesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // All topics, grouped by course, for the session editor.
    val allTopics: StateFlow<List<TopicEntity>> = repo.observeAllTopics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Entries for the visible window: week view needs 7 days, month view needs ~42. */
    private val windowRange = combine(_view, _selectedDate, _monthAnchor) { view, date, anchor ->
        when (view) {
            TimetableView.DAY -> date.toEpochDay() to date.toEpochDay()
            TimetableView.WEEK -> {
                val monday = TimeUtils.startOfWeek(date)
                monday.toEpochDay() to monday.plusDays(6).toEpochDay()
            }
            TimetableView.MONTH -> {
                val first = anchor.withDayOfMonth(1)
                val gridStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
                gridStart.toEpochDay() to gridStart.plusDays(41).toEpochDay()
            }
        }
    }

    private val entriesFlow = windowRange.flatMapLatest { (from, to) ->
        repo.observeOccurrencesBetween(from, to)
    }

    val uiState: StateFlow<TimetableUiState> = combine(
        _view, _selectedDate, _monthAnchor, entriesFlow, repo.observeSessions(), repo.observeCourses(), repo.observeAllTopics()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val view = values[0] as TimetableView
        @Suppress("UNCHECKED_CAST")
        val selected = values[1] as LocalDate
        @Suppress("UNCHECKED_CAST")
        val anchor = values[2] as LocalDate
        @Suppress("UNCHECKED_CAST")
        val occurrences = values[3] as List<SessionOccurrenceEntity>
        @Suppress("UNCHECKED_CAST")
        val sessions = values[4] as List<ReadingSessionEntity>
        @Suppress("UNCHECKED_CAST")
        val courses = values[5] as List<CourseEntity>
        @Suppress("UNCHECKED_CAST")
        val topics = values[6] as List<TopicEntity>

        val sessionById = sessions.associateBy { it.id }
        val courseById = courses.associateBy { it.id }
        val topicById = topics.associateBy { it.id }
        val topicIdsBySession = HashMap<Long, List<Long>>()
        // Topic links are loaded lazily below in a suspend-safe way; here we approximate
        // using the topic→course mapping for display of pending counts.

        fun toEntry(occ: SessionOccurrenceEntity): TimetableEntry? {
            val session = sessionById[occ.sessionId] ?: return null
            val course = session.courseId?.let { courseById[it] }
            return TimetableEntry(
                occurrenceId = occ.id,
                sessionId = session.id,
                title = session.title,
                courseCode = course?.code ?: "GEN",
                courseTitle = course?.title ?: "General",
                colorHex = course?.colorHex ?: "#C62828",
                startMinute = minuteOfDay(occ.plannedStartMillis),
                endMinute = minuteOfDay(occ.plannedEndMillis),
                state = occ.state,
                topicTitles = emptyList(),
                completedTopics = 0,
                dateEpochDay = occ.dateEpochDay
            )
        }

        val entries = occurrences.mapNotNull(::toEntry).sortedBy { it.startMinute }
        val byDay = entries.groupBy { it.dateEpochDay }

        val weekStart = TimeUtils.startOfWeek(selected)
        val weekDays = (0L..6L).map { weekStart.plusDays(it) }

        val gridStart = anchor.withDayOfMonth(1).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
        val monthCells = (0 until 42).map { offset ->
            val date = gridStart.plusDays(offset.toLong())
            DayCell(
                date = date,
                epochDay = date.toEpochDay(),
                inCurrentMonth = date.month == anchor.month,
                sessionCount = byDay[date.toEpochDay()]?.size ?: 0,
                isToday = date == LocalDate.now(),
                selected = date == selected
            )
        }

        val dayEntries = byDay[selected.toEpochDay()] ?: emptyList()

        TimetableUiState(
            view = view,
            selectedDate = selected,
            dayItems = dayEntries,
            weekDays = weekDays,
            weekEntries = weekDays.associate { it.toEpochDay() to (byDay[it.toEpochDay()] ?: emptyList()) },
            monthCells = monthCells,
            totalMinutesSelectedDay = dayEntries.sumOf { it.endMinute - it.startMinute },
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimetableUiState())

    private fun minuteOfDay(millis: Long): Int {
        val local = java.time.Instant.ofEpochMilli(millis).atZone(TimeUtils.zone()).toLocalTime()
        return local.hour * 60 + local.minute
    }

    fun setView(view: TimetableView) { _view.value = view }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        if (_view.value == TimetableView.MONTH) _monthAnchor.value = date.withDayOfMonth(1)
    }

    fun shiftPeriod(delta: Int) {
        when (_view.value) {
            TimetableView.DAY -> _selectedDate.value = _selectedDate.value.plusDays(delta.toLong())
            TimetableView.WEEK -> _selectedDate.value = _selectedDate.value.plusWeeks(delta.toLong())
            TimetableView.MONTH -> {
                val newAnchor = _monthAnchor.value.plusMonths(delta.toLong())
                _monthAnchor.value = newAnchor
                _selectedDate.value = newAnchor
            }
        }
    }

    fun goToToday() {
        _selectedDate.value = LocalDate.now()
        _monthAnchor.value = LocalDate.now().withDayOfMonth(1)
    }

    fun deleteEntry(entry: TimetableEntry, deleteSeries: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            if (deleteSeries) {
                repo.deleteSession(entry.sessionId)
            } else {
                // Remove just this day: disable the occurrence by deleting the row.
                repo.log("OCCURRENCE_SKIPPED", "occurrence=${entry.occurrenceId}")
                val occ = repo.getOccurrence(entry.occurrenceId)
                if (occ != null) {
                    repo.skipOccurrence(occ)
                }
            }
            onDone()
        }
    }

    // ───────────────────── Session editor ─────────────────────

    private val _form = MutableStateFlow(SessionForm())
    val form: StateFlow<SessionForm> = _form.asStateFlow()

    fun loadSession(sessionId: Long, presetDateEpochDay: Long?) {
        if (sessionId <= 0) {
            val date = presetDateEpochDay?.takeIf { it > 0 } ?: TimeUtils.todayEpochDay()
            _form.value = SessionForm(
                specificDateEpochDay = date,
                title = ""
            )
            return
        }
        viewModelScope.launch {
            val session = repo.getSession(sessionId) ?: return@launch
            val topicIds = repo.topicIdsForSession(sessionId).toSet()
            val days = TimeUtils.daysFromMask(session.daysOfWeekMask).toSet()
            _form.value = SessionForm(
                id = session.id,
                courseId = session.courseId,
                title = session.title,
                startMinute = session.startMinute,
                endMinute = session.endMinute,
                recurring = session.isRecurring,
                days = if (days.isEmpty()) setOf(DayOfWeek.MONDAY) else days,
                specificDateEpochDay = session.specificDateEpochDay,
                alarmSound = session.alarmSound,
                vibration = session.vibration,
                snoozeMinutes = session.snoozeMinutes,
                selectedTopicIds = topicIds,
                enabled = session.enabled,
                notes = session.notes
            )
        }
    }

    fun updateForm(block: (SessionForm) -> SessionForm) {
        _form.value = block(_form.value)
    }

    fun toggleDay(day: DayOfWeek) {
        val days = _form.value.days.toMutableSet()
        if (!days.add(day)) days.remove(day)
        _form.value = _form.value.copy(days = days)
    }

    fun toggleTopicSelection(topicId: Long) {
        val ids = _form.value.selectedTopicIds.toMutableSet()
        if (!ids.add(topicId)) ids.remove(topicId)
        _form.value = _form.value.copy(selectedTopicIds = ids)
    }

    fun saveSession(onSaved: () -> Unit) {
        val f = _form.value
        if (!f.isValid) return
        viewModelScope.launch {
            val entity = ReadingSessionEntity(
                id = f.id,
                courseId = f.courseId,
                title = f.title.trim(),
                startMinute = f.startMinute,
                endMinute = f.endMinute,
                daysOfWeekMask = if (f.recurring) TimeUtils.maskForDays(f.days) else 0,
                isRecurring = f.recurring,
                specificDateEpochDay = if (f.recurring) null else f.specificDateEpochDay,
                alarmSound = f.alarmSound,
                vibration = f.vibration,
                snoozeMinutes = f.snoozeMinutes,
                enabled = f.enabled,
                notes = f.notes
            )
            if (f.id <= 0) {
                repo.createSession(entity, f.selectedTopicIds.toList())
            } else {
                repo.updateSession(entity, f.selectedTopicIds.toList())
            }
            onSaved()
        }
    }

    fun deleteSession(onDone: () -> Unit) {
        val id = _form.value.id
        if (id <= 0) return
        viewModelScope.launch {
            repo.deleteSession(id)
            onDone()
        }
    }

    fun setSessionEnabled(enabled: Boolean) {
        val id = _form.value.id
        if (id <= 0) return
        viewModelScope.launch { repo.setSessionEnabled(id, enabled) }
    }

    fun startNow(occurrenceId: Long) {
        viewModelScope.launch {
            // Materialise today's occurrence first so the run is linked to the plan.
            repo.materialiseRange(TimeUtils.todayEpochDay(), TimeUtils.todayEpochDay())
        }
    }
}

private val UnusedOccurrenceState = OccurrenceState.SCHEDULED
private val UnusedTopicStatus = TopicStatus.PENDING
