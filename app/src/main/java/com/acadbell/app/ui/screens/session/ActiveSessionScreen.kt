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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.acadbell.app.domain.RunOutcome
import com.acadbell.app.domain.TimeUtils
import com.acadbell.app.domain.TopicStatus
import com.acadbell.app.ui.components.AcadCard
import com.acadbell.app.ui.components.AcadPill
import com.acadbell.app.ui.components.EmptyState
import com.acadbell.app.ui.components.ProgressTrack
import com.acadbell.app.ui.components.TopicCheckbox
import com.acadbell.app.ui.theme.Shape
import com.acadbell.app.ui.icons.AcadIcons

/**
 * The active reading screen.
 *
 * Visual priority: the countdown. Everything else (topics, controls) is one thumb-reach away.
 * The screen never marks a topic complete on its own — only the checkbox does that.
 */
@Composable
fun ActiveSessionScreen(
    runId: Long,
    onBack: () -> Unit,
    onFinished: (Long) -> Unit,
    viewModel: SessionViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val live by viewModel.serviceState.collectAsStateWithLifecycle()

    var showStopConfirm by remember { mutableStateOf(false) }
    var showExtend by remember { mutableStateOf(false) }
    var showAddTopic by remember { mutableStateOf(false) }

    LaunchedEffect(runId) { viewModel.load(runId) }

    val run = state.run
    // Prefer live service numbers; fall back to the persisted run when the service is gone.
    val isServiceLive = live.runId == runId
    val accumulated = if (isServiceLive) live.accumulatedSeconds else (run?.actualSeconds ?: 0)
    val allocated = if (isServiceLive) live.allocatedSeconds else (run?.allocatedSeconds ?: 1)
    val remaining = (allocated - accumulated).coerceAtLeast(0)
    val isRunning = if (isServiceLive) live.isRunning else false
    val progress = if (allocated <= 0) 0f else (accumulated.toFloat() / allocated).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.primary,
                    0.42f to MaterialTheme.colorScheme.primary,
                    0.421f to MaterialTheme.colorScheme.background,
                    1f to MaterialTheme.colorScheme.background
                )
            )
    ) {
        Column(Modifier.fillMaxSize()) {
            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AcadIcons.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        run?.sessionTitle ?: "Reading session",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        run?.let { "${it.courseCode} · ${it.courseTitle}" } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1
                    )
                }
                AcadPill(
                    text = if (!isServiceLive) "Not running"
                    else if (isRunning) "Reading" else "Paused",
                    color = Color.White,
                    background = Color.White.copy(alpha = 0.2f)
                )
            }

            // ── Countdown ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (remaining > 0) TimeUtils.formatDurationSeconds(remaining) else "00:00",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
                Text(
                    text = when {
                        !isServiceLive -> "Start the session to begin timing"
                        remaining > 0 -> "remaining of ${TimeUtils.formatDuration(allocated / 60)}"
                        else -> "Allocated time finished"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(16.dp))
                Box(Modifier.padding(horizontal = 24.dp)) {
                    ProgressTrack(
                        fraction = progress,
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.28f),
                        height = 8.dp
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AcadPill(
                        text = "Read ${TimeUtils.formatDuration(accumulated / 60)}",
                        color = Color.White,
                        background = Color.White.copy(alpha = 0.18f)
                    )
                    if ((live.extendedSeconds) > 0) {
                        AcadPill(
                            text = "+${live.extendedSeconds / 60}m added",
                            color = Color.White,
                            background = Color.White.copy(alpha = 0.18f)
                        )
                    }
                    AcadPill(
                        text = "${state.completedCount}/${state.topics.size} topics",
                        color = Color.White,
                        background = Color.White.copy(alpha = 0.18f)
                    )
                }
            }

            // ── Controls ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isServiceLive) {
                    Button(
                        onClick = { viewModel.resume() },
                        shape = Shape.button,
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Icon(AcadIcons.Play, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Resume timing")
                    }
                } else {
                    Button(
                        onClick = { if (isRunning) viewModel.pause() else viewModel.resume() },
                        shape = Shape.button,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                            contentColor = if (isRunning) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Icon(if (isRunning) AcadIcons.Pause else AcadIcons.Play, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (isRunning) "Pause" else "Resume")
                    }
                }
                Button(
                    onClick = { showExtend = true },
                    shape = Shape.button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(AcadIcons.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Extend")
                }
                Button(
                    onClick = { showStopConfirm = true },
                    shape = Shape.button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(0.8f).height(50.dp)
                ) {
                    Icon(AcadIcons.Stop, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Stop")
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Topics ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Topics for this session",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showAddTopic = true }) { Text("Add topic") }
            }

            if (state.topics.isEmpty()) {
                Box(Modifier.padding(20.dp)) {
                    EmptyState(
                        icon = AcadIcons.Topic,
                        title = "No topics assigned",
                        message = "You can still read now and tick topics later, or add one on the fly.",
                        actionLabel = "Add a topic",
                        onAction = { showAddTopic = true }
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 4.dp, 20.dp, 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.topics, key = { it.id }) { topic ->
                        val done = topic.status == TopicStatus.COMPLETED
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(Shape.cardSmall)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, Shape.cardSmall)
                                .clickable { viewModel.toggleTopic(topic.id, !done) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TopicCheckbox(checked = done, onToggle = { viewModel.toggleTopic(topic.id, !done) })
                            Spacer(Modifier.width(12.dp))
                            Text(
                                topic.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                                modifier = Modifier.weight(1f),
                                maxLines = 2
                            )
                            if (done) {
                                AcadPill("Done", color = Color(0xFF2E7D32))
                            }
                        }
                    }
                    item {
                        Text(
                            "Ticking a topic marks it complete for your course. Nothing is ticked automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text("Stop this session?") },
            text = {
                Text(
                    "Your reading time (${TimeUtils.formatDuration(accumulated / 60)}) is saved. " +
                        "Topics you have not ticked stay pending and can be rescheduled."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    viewModel.stop { id -> onFinished(id) }
                }) { Text("Stop & review") }
            },
            dismissButton = { TextButton(onClick = { showStopConfirm = false }) { Text("Keep reading") } }
        )
    }

    if (showExtend) {
        val options = listOf(5, 10, 15, 30)
        AlertDialog(
            onDismissRequest = { showExtend = false },
            title = { Text("Extend session") },
            text = {
                Column {
                    Text(
                        "Add more reading time. The end alarm moves to the new finish time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { mins ->
                            Box(
                                modifier = Modifier
                                    .clip(Shape.pill)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable {
                                        viewModel.extend(mins)
                                        showExtend = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("+${mins}m", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showExtend = false }) { Text("Cancel") } }
        )
    }

    if (showAddTopic) {
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTopic = false },
            title = { Text("Add topic to this session") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Topic title") },
                    shape = Shape.button,
                    singleLine = false
                )
            },
            confirmButton = {
                TextButton(
                    enabled = title.isNotBlank(),
                    onClick = {
                        viewModel.addQuickTopic(title)
                        showAddTopic = false
                    }
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddTopic = false }) { Text("Cancel") } }
        )
    }
}
