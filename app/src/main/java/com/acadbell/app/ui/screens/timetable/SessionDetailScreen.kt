package com.acadbell.app.ui.screens.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.acadbell.app.data.db.SessionOccurrenceEntity
import com.acadbell.app.di.ServiceLocator
import com.acadbell.app.domain.OccurrenceState
import com.acadbell.app.domain.TimeUtils
import com.acadbell.app.domain.TopicStatus
import com.acadbell.app.repository.model.OccurrenceWithSession
import com.acadbell.app.ui.components.AcadCard
import com.acadbell.app.ui.components.AcadPill
import com.acadbell.app.ui.components.CourseDot
import com.acadbell.app.ui.components.InfoBanner
import com.acadbell.app.ui.components.SectionHeader
import com.acadbell.app.ui.components.parseHexColor
import com.acadbell.app.ui.screens.courses.AcadTopBar
import com.acadbell.app.ui.theme.Shape
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.acadbell.app.ui.icons.AcadIcons

class SessionDetailViewModel(app: android.app.Application) : androidx.lifecycle.AndroidViewModel(app) {
    private val repo = ServiceLocator.repository(app)

    private val _occurrence = MutableStateFlow<SessionOccurrenceEntity?>(null)
    val occurrence = _occurrence.asStateFlow()

    private val _session = MutableStateFlow<com.acadbell.app.data.db.ReadingSessionEntity?>(null)
    val session = _session.asStateFlow()

    private val _courseCode = MutableStateFlow("GEN")
    val courseCode = _courseCode.asStateFlow()

    private val _courseTitle = MutableStateFlow("General reading")
    val courseTitle = _courseTitle.asStateFlow()

    private val _colorHex = MutableStateFlow("#C62828")
    val colorHex = _colorHex.asStateFlow()

    private val _topics = MutableStateFlow<List<com.acadbell.app.data.db.TopicEntity>>(emptyList())
    val topics = _topics.asStateFlow()

    fun load(occurrenceId: Long) {
        viewModelScope.launch {
            val occ = repo.getOccurrence(occurrenceId) ?: return@launch
            _occurrence.value = occ
            val session = repo.getSession(occ.sessionId) ?: return@launch
            _session.value = session
            val course = session.courseId?.let { repo.getCourse(it) }
            _courseCode.value = course?.code ?: "GEN"
            _courseTitle.value = course?.title ?: "General reading"
            _colorHex.value = course?.colorHex ?: "#C62828"
            _topics.value = repo.getTopics(repo.topicIdsForSession(session.id))
        }
    }

    fun toggleTopic(id: Long, done: Boolean) {
        viewModelScope.launch {
            repo.setTopicCompleted(id, done)
            _topics.value = repo.getTopics(_topics.value.map { it.id })
        }
    }

    /** Deletes the whole series (template + future alarms). */
    fun deleteSessionAndFinish(onDone: () -> Unit) {
        viewModelScope.launch {
            _session.value?.let { repo.deleteSession(it.id) }
            onDone()
        }
    }

    fun markCompleted() {
        viewModelScope.launch {
            _occurrence.value?.let { repo.markOccurrenceState(it.id, OccurrenceState.DONE) }
            _occurrence.value?.let { load(it.id) }
        }
    }
}

@Composable
fun SessionDetailScreen(
    occurrenceId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onStart: (Long) -> Unit,
    onStartAdHoc: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: SessionDetailViewModel = viewModel()
) {
    val occurrence by viewModel.occurrence.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val topics by viewModel.topics.collectAsStateWithLifecycle()
    val courseCode by viewModel.courseCode.collectAsStateWithLifecycle()
    val courseTitle by viewModel.courseTitle.collectAsStateWithLifecycle()
    val colorHex by viewModel.colorHex.collectAsStateWithLifecycle()

    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(occurrenceId) { viewModel.load(occurrenceId) }

    val accent = parseHexColor(colorHex)
    val occ = occurrence
    val startMinute = occ?.let { localMinuteOfDay(it.plannedStartMillis) } ?: 0
    val endMinute = occ?.let { localMinuteOfDay(it.plannedEndMillis) } ?: 0
    val durationMinutes = ((occ?.plannedEndMillis ?: 0) - (occ?.plannedStartMillis ?: 0)) / 60000

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AcadTopBar(
                title = "Reading session",
                onBack = onBack,
                actions = {
                    if (session != null) {
                        IconButton(onClick = { onEdit(session!!.id) }) {
                            Icon(AcadIcons.Edit, contentDescription = "Edit session")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(AcadIcons.Delete, contentDescription = "Delete session")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AcadCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CourseDot(colorHex, size = 12.dp)
                    Spacer(Modifier.width(8.dp))
                    AcadPill(courseCode, color = accent)
                    Spacer(Modifier.weight(1f))
                    AcadPill(stateLabel(occ?.state), color = stateColor(occ?.state))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    session?.title ?: "Session",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(courseTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AcadIcons.Schedule, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        TimeUtils.formatRange(startMinute, endMinute),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    AcadPill(TimeUtils.formatDuration(durationMinutes), color = accent)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    occ?.let { TimeUtils.formatDateLong(TimeUtils.dateOf(it.dateEpochDay)) } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                session?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (it.isRecurring) "Repeats: ${TimeUtils.maskLabel(it.daysOfWeekMask)}"
                        else "One-off session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (occ?.state == OccurrenceState.SCHEDULED) {
                InfoBanner(
                    title = "Alarms are armed",
                    message = "A start alarm will ring at ${TimeUtils.formatTime(startMinute)} and an end alarm at " +
                        "${TimeUtils.formatTime(endMinute)} — even if ACADBELL is closed.",
                    icon = AcadIcons.CheckCircle,
                    tint = Color(0xFF2E7D32)
                )
            }

            Button(
                onClick = { onStart(occurrenceId) },
                shape = Shape.button,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(AcadIcons.Play, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start reading now", fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onStartAdHoc,
                shape = Shape.button,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Start an ad-hoc session instead") }

            if (occ?.state == OccurrenceState.SCHEDULED || occ?.state == OccurrenceState.MISSED) {
                OutlinedButton(
                    onClick = { viewModel.markCompleted() },
                    shape = Shape.button,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Mark this session as covered") }
            }

            SectionHeader(title = "Topics (${topics.size})")
            if (topics.isEmpty()) {
                AcadCard(padding = 14.dp) {
                    Text(
                        "No topics assigned. Edit the session to attach topics from ${courseCode}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                topics.forEach { topic ->
                    val done = topic.status == TopicStatus.COMPLETED
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, Shape.cardSmall)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(topic.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            if (topic.note.isNotBlank()) {
                                Text(topic.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        AcadPill(
                            if (done) "Completed" else "Pending",
                            color = if (done) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this session?") },
            text = { Text("All future alarms for this session will be cancelled. Reading history is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    session?.let { viewModel.deleteSessionAndFinish(onDeleted) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

private fun localMinuteOfDay(millis: Long): Int {
    val t = java.time.Instant.ofEpochMilli(millis).atZone(TimeUtils.zone()).toLocalTime()
    return t.hour * 60 + t.minute
}

private fun stateLabel(state: OccurrenceState?): String = when (state) {
    OccurrenceState.DONE -> "Completed"
    OccurrenceState.MISSED -> "Missed"
    OccurrenceState.INTERRUPTED -> "Interrupted"
    OccurrenceState.SKIPPED -> "Skipped"
    else -> "Planned"
}

private fun stateColor(state: OccurrenceState?): Color = when (state) {
    OccurrenceState.DONE -> Color(0xFF2E7D32)
    OccurrenceState.MISSED -> Color(0xFFC62828)
    OccurrenceState.INTERRUPTED -> Color(0xFFEF6C00)
    else -> Color(0xFF6B6B76)
}
