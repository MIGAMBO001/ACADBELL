package com.acadbell.app.ui.screens.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.acadbell.app.domain.RunOutcome
import com.acadbell.app.domain.TimeUtils
import com.acadbell.app.domain.TopicStatus
import com.acadbell.app.ui.components.AcadCard
import com.acadbell.app.ui.components.AcadPill
import com.acadbell.app.ui.components.ProgressRing
import com.acadbell.app.ui.components.ProgressTrack
import com.acadbell.app.ui.components.SectionHeader
import com.acadbell.app.ui.components.StatTile
import com.acadbell.app.ui.components.TopicCheckbox
import com.acadbell.app.ui.screens.courses.AcadTopBar
import com.acadbell.app.ui.screens.timetable.TimePickerDialog
import com.acadbell.app.ui.theme.Shape
import com.acadbell.app.ui.icons.AcadIcons

/**
 * §5G — Session completion.
 *
 * Shows what actually happened (allocated vs actual), lets the student review the topics they
 * covered, and offers to reschedule whatever is still pending. Unfinished topics are NEVER
 * auto-completed.
 */
@Composable
fun SessionSummaryScreen(
    runId: Long,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: SessionViewModel = viewModel()
) {
    var summary by remember { mutableStateOf<SessionSummaryData?>(null) }
    var showReschedule by remember { mutableStateOf(false) }
    var rescheduled by remember { mutableStateOf(false) }

    LaunchedEffect(runId) { viewModel.finaliseSummary(runId) { summary = it } }

    val data = summary
    val topics by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AcadTopBar(title = "Session summary", onBack = onBack) }
    ) { padding ->
        if (data == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Hero ──
            AcadCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(
                        fraction = (data.actualMinutes.toFloat() / data.allocatedMinutes.coerceAtLeast(1)).coerceIn(0f, 1f),
                        size = 68.dp,
                        label = "${data.efficiencyPercent}%"
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            outcomeLabel(data.run.outcome),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${data.run.courseCode} · ${data.run.sessionTitle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        AcadPill(
                            text = "Read ${TimeUtils.formatDuration(data.actualMinutes)} of " +
                                TimeUtils.formatDuration(data.allocatedMinutes),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = TimeUtils.formatDuration(data.actualMinutes),
                    label = "Actual reading",
                    modifier = Modifier.weight(1f),
                    icon = AcadIcons.Timer
                )
                StatTile(
                    value = TimeUtils.formatDuration(data.allocatedMinutes),
                    label = "Allocated",
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.onSurfaceVariant,
                    icon = AcadIcons.Schedule
                )
                StatTile(
                    value = "${data.completedCount}",
                    label = "Topics ticked",
                    modifier = Modifier.weight(1f),
                    accent = Color(0xFF2E7D32),
                    icon = AcadIcons.CheckCircle
                )
            }

            // ── Review topics ──
            SectionHeader(
                title = "Review topics",
                actionLabel = if (data.pendingCount > 0) "Reschedule ${data.pendingCount}" else null,
                onAction = if (data.pendingCount > 0) ({ showReschedule = true }) else null
            )

            Text(
                "Tick anything you covered. ACADBELL never marks topics for you — the timer expiring " +
                    "does not mean the topic is finished.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val list = (viewModel.uiState.collectAsStateWithLifecycle().value.uiStateTopicsOrNull() ?: data.topics)
            if (list.isEmpty()) {
                AcadCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(AcadIcons.Topic, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "No topics were assigned to this session.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                list.forEach { topic ->
                    val done = topic.status == TopicStatus.COMPLETED
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Shape.cardSmall)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, Shape.cardSmall)
                            .clickable { viewModel.toggleTopic(topic.id, !done); summary = summary }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TopicCheckbox(checked = done, onToggle = {
                            viewModel.toggleTopic(topic.id, !done)
                        })
                        Spacer(Modifier.width(12.dp))
                        Text(
                            topic.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                            modifier = Modifier.weight(1f)
                        )
                        if (done) AcadPill("Completed", color = Color(0xFF2E7D32))
                        else AcadPill("Pending", color = Color(0xFFEF6C00))
                    }
                }
            }

            // ── Unfinished topics ──
            if (data.pendingCount > 0) {
                AcadCard(background = MaterialTheme.colorScheme.primaryContainer, border = false) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(AcadIcons.EventAvailable, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${data.pendingCount} topic(s) still pending",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (rescheduled) "Rescheduled — check your timetable."
                                else "They stay in your course. Move them to another reading block.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!rescheduled) {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { showReschedule = true },
                            shape = Shape.button,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Reschedule unfinished topics") }
                    }
                }
            }

            // ── Actions ──
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    viewModel.refreshTopics()
                    onDone()
                },
                shape = Shape.button,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) { Text("Done", fontWeight = FontWeight.SemiBold) }

            OutlinedButton(
                onClick = onBack,
                shape = Shape.button,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Back to session") }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showReschedule) {
        RescheduleDialog(
            defaultStart = data?.run?.let { 18 * 60 } ?: 18 * 60,
            onDismiss = { showReschedule = false },
            onConfirm = { day, startMinute, endMinute ->
                showReschedule = false
                viewModel.rescheduleUnfinished(runId, day, startMinute, endMinute) {
                    rescheduled = true
                    viewModel.finaliseSummary(runId) { summary = it }
                }
            }
        )
    }
}

/** Small adapter so the summary can show live topic state after edits. */
private fun ActiveSessionUiState.uiStateTopicsOrNull() = topics

@Composable
private fun RescheduleDialog(
    defaultStart: Int,
    onDismiss: () -> Unit,
    onConfirm: (Long, Int, Int) -> Unit
) {
    val today = TimeUtils.todayEpochDay()
    var dayOffset by remember { mutableStateOf(1L) }
    var startMinute by remember { mutableStateOf(defaultStart) }
    var endMinute by remember { mutableStateOf(defaultStart + 60) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reschedule unfinished topics") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This creates a one-off catch-up session with the pending topics attached.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0L to "Today", 1L to "Tomorrow", 2L to "In 2 days").forEach { (offset, label) ->
                        FilterChip(
                            selected = dayOffset == offset,
                            onClick = { dayOffset = offset },
                            label = { Text(label) },
                            shape = Shape.pill
                        )
                    }
                }
                Text(
                    TimeUtils.formatDateLong(TimeUtils.dateOf(today + dayOffset)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f), shape = Shape.button) {
                        Text("Start ${TimeUtils.formatTime(startMinute)}")
                    }
                    OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f), shape = Shape.button) {
                        Text("End ${TimeUtils.formatTime(endMinute)}")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(today + dayOffset, startMinute, endMinute) },
                enabled = endMinute > startMinute,
                shape = Shape.button
            ) { Text("Create catch-up") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showStartPicker) {
        TimePickerDialog(
            initialMinute = startMinute,
            onDismiss = { showStartPicker = false },
            onConfirm = { m ->
                showStartPicker = false
                startMinute = m
                if (endMinute <= m) endMinute = (m + 60).coerceAtMost(23 * 60 + 59)
            }
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            initialMinute = endMinute,
            onDismiss = { showEndPicker = false },
            onConfirm = { m -> showEndPicker = false; endMinute = m }
        )
    }
}

private fun outcomeLabel(outcome: RunOutcome): String = when (outcome) {
    RunOutcome.COMPLETED -> "Reading complete"
    RunOutcome.EXTENDED -> "Reading complete (extended)"
    RunOutcome.STOPPED_EARLY -> "Stopped early"
    RunOutcome.INTERRUPTED -> "Session interrupted"
    RunOutcome.MISSED -> "Session missed"
    RunOutcome.IN_PROGRESS -> "Still reading"
}
