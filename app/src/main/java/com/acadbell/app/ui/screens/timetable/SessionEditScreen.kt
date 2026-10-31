package com.acadbell.app.ui.screens.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.acadbell.app.domain.AlarmSoundChoice
import com.acadbell.app.domain.TimeUtils
import com.acadbell.app.domain.TopicStatus
import com.acadbell.app.ui.components.AcadCard
import com.acadbell.app.ui.components.AcadPill
import com.acadbell.app.ui.components.CourseDot
import com.acadbell.app.ui.components.SectionHeader
import com.acadbell.app.ui.components.TopicCheckbox
import com.acadbell.app.ui.screens.courses.AcadTopBar
import com.acadbell.app.ui.theme.Shape
import java.time.DayOfWeek
import com.acadbell.app.ui.icons.AcadIcons

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionEditScreen(
    sessionId: Long,
    presetDateEpochDay: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: TimetableViewModel = viewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    val allTopics by viewModel.allTopics.collectAsStateWithLifecycle()

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) { viewModel.loadSession(sessionId, presetDateEpochDay) }

    val courseTopics = remember(form.courseId, allTopics) {
        allTopics.filter { it.courseId == form.courseId && it.status == TopicStatus.PENDING }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AcadTopBar(
                title = if (sessionId > 0) "Edit session" else "New reading session",
                onBack = onBack,
                actions = {
                    if (sessionId > 0) {
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
            OutlinedTextField(
                value = form.title,
                onValueChange = { v -> viewModel.updateForm { it.copy(title = v) } },
                label = { Text("Session title") },
                placeholder = { Text("e.g. Physics revision") },
                singleLine = true,
                shape = Shape.button,
                modifier = Modifier.fillMaxWidth()
            )

            SectionHeader(title = "Course")
            if (courses.isEmpty()) {
                AcadCard(padding = 12.dp) {
                    Text(
                        "No courses yet — this session will be saved as general reading. Add a course to attach topics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(courses, key = { it.id }) { course ->
                        val selected = form.courseId == course.id
                        Row(
                            modifier = Modifier
                                .clip(Shape.pill)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    Shape.pill
                                )
                                .clickable {
                                    viewModel.updateForm {
                                        it.copy(courseId = if (selected) null else course.id, selectedTopicIds = emptySet())
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CourseDot(course.colorHex)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                course.code,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            SectionHeader(title = "Time")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeField(
                    label = "Starts",
                    value = TimeUtils.formatTime(form.startMinute),
                    modifier = Modifier.weight(1f)
                ) { showStartPicker = true }
                TimeField(
                    label = "Ends",
                    value = TimeUtils.formatTime(form.endMinute),
                    modifier = Modifier.weight(1f)
                ) { showEndPicker = true }
            }
            AcadCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Duration",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    AcadPill(
                        text = TimeUtils.formatDuration(form.durationMinutes.toLong()),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (form.endMinute <= form.startMinute) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "End time must be after the start time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            SectionHeader(title = "Repeat")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AcadIcons.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (form.recurring) "Recurring weekly" else "One-off session",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (form.recurring) "Rings on the days you pick, every week"
                        else "Rings once on ${form.specificDateEpochDay?.let { TimeUtils.formatDateShort(it) } ?: "the chosen date"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = form.recurring,
                    onCheckedChange = { v -> viewModel.updateForm { it.copy(recurring = v) } }
                )
            }

            if (form.recurring) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        val selected = form.days.contains(day)
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.toggleDay(day) },
                            label = { Text(TimeUtils.weekdayShort(day).take(3)) },
                            shape = Shape.pill
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        viewModel.updateForm { it.copy(days = DayOfWeek.entries.toSet()) }
                    }) { Text("Every day") }
                    TextButton(onClick = {
                        viewModel.updateForm {
                            it.copy(days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))
                        }
                    }) { Text("Weekdays") }
                }
            } else {
                val date = form.specificDateEpochDay ?: TimeUtils.todayEpochDay()
                AcadCard(padding = 12.dp) {
                    Text("Date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            viewModel.updateForm { it.copy(specificDateEpochDay = date - 1) }
                        }) { Text("− day") }
                        Text(
                            TimeUtils.formatDateShort(date),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        TextButton(onClick = {
                            viewModel.updateForm { it.copy(specificDateEpochDay = date + 1) }
                        }) { Text("+ day") }
                    }
                }
            }

            SectionHeader(title = "Alarm")
            AcadCard(padding = 12.dp) {
                Text("Sound", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AlarmSoundChoice.entries.forEach { choice ->
                        FilterChip(
                            selected = form.alarmSound == choice.name,
                            onClick = { viewModel.updateForm { it.copy(alarmSound = choice.name) } },
                            label = { Text(choice.label) },
                            shape = Shape.pill
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Vibration", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Buzz with the reading alarm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = form.vibration,
                        onCheckedChange = { v -> viewModel.updateForm { it.copy(vibration = v) } }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("Snooze length", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 15, 20).forEach { mins ->
                        FilterChip(
                            selected = form.snoozeMinutes == mins,
                            onClick = { viewModel.updateForm { it.copy(snoozeMinutes = mins) } },
                            label = { Text("${mins}m") },
                            shape = Shape.pill
                        )
                    }
                }
            }

            if (courseTopics.isNotEmpty()) {
                SectionHeader(title = "Topics for this session (${form.selectedTopicIds.size})")
                AcadCard(padding = 12.dp) {
                    Text(
                        "Topics are assigned for this session. You still tick them off manually while reading.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    courseTopics.forEach { topic ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                            TopicCheckbox(
                                checked = form.selectedTopicIds.contains(topic.id),
                                onToggle = { viewModel.toggleTopicSelection(topic.id) },
                                size = 22.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                topic.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 2
                            )
                            if (topic.estimatedMinutes > 0) {
                                AcadPill("${topic.estimatedMinutes}m", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            if (sessionId > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Session enabled", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Turn off to keep history but stop future alarms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = form.enabled,
                        onCheckedChange = {
                            viewModel.updateForm { f -> f.copy(enabled = it) }
                            viewModel.setSessionEnabled(it)
                        }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { viewModel.saveSession(onSaved) },
                enabled = form.isValid,
                shape = Shape.button,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(if (sessionId > 0) "Save session" else "Create session", fontWeight = FontWeight.SemiBold)
            }
            Text(
                "ACADBELL arms a start alarm and an end alarm for every session. Alarms fire even when the app is closed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showStartPicker) {
        TimePickerDialog(
            initialMinute = form.startMinute,
            onDismiss = { showStartPicker = false },
            onConfirm = { minute ->
                showStartPicker = false
                viewModel.updateForm {
                    // Keep the session at least 15 minutes long when the start moves past the end.
                    val newEnd = if (it.endMinute <= minute) (minute + 60).coerceAtMost(23 * 60 + 59) else it.endMinute
                    it.copy(startMinute = minute, endMinute = newEnd)
                }
            }
        )
    }

    if (showEndPicker) {
        TimePickerDialog(
            initialMinute = form.endMinute,
            onDismiss = { showEndPicker = false },
            onConfirm = { minute ->
                showEndPicker = false
                viewModel.updateForm { it.copy(endMinute = minute) }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this session?") },
            text = { Text("All upcoming alarms for this session will be cancelled. Past reading records stay in your history.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteSession(onBack)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TimeField(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(Shape.cardSmall)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, Shape.cardSmall)
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = false
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) }
    )
}
