package com.acadbell.app.ui.screens.home

import androidx.compose.foundation.background
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.acadbell.app.domain.OccurrenceState
import com.acadbell.app.domain.TimeUtils
import com.acadbell.app.repository.model.HomeSessionItem
import com.acadbell.app.ui.components.AcadCard
import com.acadbell.app.ui.components.AcadPill
import com.acadbell.app.ui.components.CourseDot
import com.acadbell.app.ui.components.EmptyState
import com.acadbell.app.ui.components.ProgressRing
import com.acadbell.app.ui.components.ProgressTrack
import com.acadbell.app.ui.components.SectionHeader
import com.acadbell.app.ui.components.StatTile
import com.acadbell.app.ui.components.parseHexColor
import com.acadbell.app.ui.theme.Shape
import com.acadbell.app.ui.icons.AcadIcons

@Composable
fun HomeScreen(
    onOpenTimetable: () -> Unit,
    onOpenCourses: () -> Unit,
    onCreateSession: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenActiveSession: (Long) -> Unit,
    onOpenProgress: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateSession,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = Shape.pill,
                icon = { Icon(AcadIcons.Add, contentDescription = null) },
                text = { Text("New session", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { HomeHeader(state) }
            item { TodayOverviewCard(state) }

            if (state.activeRunId != null) {
                item {
                    LiveSessionBanner(
                        runId = state.activeRunId!!,
                        onClick = { onOpenActiveSession(state.activeRunId!!) }
                    )
                }
            }

            item { NextSessionCard(state, onOpenTimetable) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        value = TimeUtils.formatDuration(state.snapshot.minutesToday.toLong()),
                        label = "Read today",
                        modifier = Modifier.weight(1f),
                        icon = AcadIcons.Schedule
                    )
                    StatTile(
                        value = "${state.snapshot.completedToday}/${state.todayItems.size}",
                        label = "Sessions today",
                        modifier = Modifier.weight(1f),
                        accent = Color(0xFF2E7D32),
                        icon = AcadIcons.CheckCircle
                    )
                    StatTile(
                        value = "${state.snapshot.topicsRemaining}",
                        label = "Topics left",
                        modifier = Modifier.weight(1f),
                        accent = Color(0xFFEF6C00),
                        icon = AcadIcons.Pending
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Today's sessions",
                    actionLabel = "Timetable",
                    onAction = onOpenTimetable
                )
            }

            if (state.todayItems.isEmpty()) {
                item {
                    EmptyState(
                        icon = AcadIcons.Calendar,
                        title = "No reading planned today",
                        message = "Create a session to get a start and end alarm. Consistency starts with one block.",
                        actionLabel = "Plan a session",
                        onAction = onCreateSession
                    )
                }
            } else {
                items(state.todayItems, key = { it.occurrenceId ?: it.startMillis }) { item ->
                    TodaySessionRow(item) { item.occurrenceId?.let(onOpenSession) }
                }
            }

            if (state.pendingTopics.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Topics waiting",
                        actionLabel = "All courses",
                        onAction = onOpenCourses
                    )
                }
                items(state.pendingTopics, key = { it.id }) { topic ->
                    val course = state.courseLookup[topic.courseId]
                    AcadCard(padding = 12.dp, onClick = onOpenCourses) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CourseDot(course?.colorHex ?: "#C62828")
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    topic.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    course?.code ?: "—",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AcadPill(text = "${topic.estimatedMinutes}m", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                AcadCard(onClick = onOpenProgress) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(
                            fraction = if (state.snapshot.topicsCompleted + state.snapshot.topicsRemaining == 0) 0f
                            else state.snapshot.topicsCompleted.toFloat() /
                                (state.snapshot.topicsCompleted + state.snapshot.topicsRemaining),
                            size = 48.dp,
                            label = null
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Academic progress",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${state.snapshot.topicsCompleted} topics completed · " +
                                    "${TimeUtils.formatDuration(state.snapshot.totalActualSeconds / 60)} read in total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(AcadIcons.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(state: HomeUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = state.greeting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "ACADBELL",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = androidx.compose.ui.unit.TextUnit(-0.5f, androidx.compose.ui.unit.TextUnitType.Sp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = state.dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(AcadIcons.BellActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TodayOverviewCard(state: HomeUiState) {
    val total = state.todayItems.size
    val done = state.todayItems.count {
        it.state == OccurrenceState.DONE.name || it.state == OccurrenceState.SKIPPED.name
    }
    val fraction = if (total == 0) 0f else done.toFloat() / total

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shape.card)
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.82f))
                )
            )
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "READING PROGRESS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${TimeUtils.formatDuration(state.snapshot.minutesToday.toLong())} of " +
                        TimeUtils.formatDuration(state.scheduledMinutesToday.toLong()),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "read today vs scheduled",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(12.dp))
                ProgressTrack(
                    fraction = fraction,
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    height = 6.dp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "$done of $total session(s) completed",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            Spacer(Modifier.width(16.dp))
            ProgressRing(
                fraction = fraction,
                size = 74.dp,
                strokeWidth = 8.dp,
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                label = "${(fraction * 100).toInt()}%",
                labelColor = Color.White
            )
        }
    }
}

@Composable
private fun LiveSessionBanner(runId: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shape.card)
            .background(Color(0xFF1B5E20))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(AcadIcons.Play, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Session in progress", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("Tap to return to your reading timer", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
        }
        Icon(AcadIcons.ArrowForward, contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun NextSessionCard(state: HomeUiState, onOpenTimetable: () -> Unit) {
    val next = state.nextItem
    if (next == null) {
        AcadCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AcadIcons.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        if (state.todayItems.isEmpty()) "Nothing scheduled today" else "All sessions for today are done",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Next alarm appears here as soon as you plan it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    AcadCard(onClick = onOpenTimetable) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("NEXT SESSION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            val mins = state.nextStartsInMinutes ?: 0
            AcadPill(
                text = if (mins <= 0) "starting now" else "in ${TimeUtils.formatDuration(mins)}",
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            next.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CourseDot(next.colorHex)
            Spacer(Modifier.width(6.dp))
            Text(
                "${next.courseCode} · ${next.courseTitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AcadIcons.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                TimeUtils.formatRange(next.startMinute, next.endMinute),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            Icon(AcadIcons.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TodaySessionRow(item: HomeSessionItem, onClick: () -> Unit) {
    val accent = parseHexColor(item.colorHex)
    val isDone = item.state == OccurrenceState.DONE.name
    val isMissed = item.state == OccurrenceState.MISSED.name
    val isRunning = item.state == "RUNNING_NOW"

    AcadCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .width(52.dp)
                    .clip(Shape.cardSmall)
                    .background(accent.copy(alpha = 0.10f))
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    TimeUtils.formatTime(item.startMinute).substringBefore(" "),
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    TimeUtils.formatTime(item.startMinute).substringAfter(" "),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${item.courseCode} · ${TimeUtils.formatRange(item.startMinute, item.endMinute)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            when {
                isRunning -> AcadPill(text = "LIVE", color = Color.White, background = Color(0xFF2E7D32))
                isDone -> AcadPill(text = "Done", color = Color(0xFF2E7D32))
                isMissed -> AcadPill(text = "Missed", color = Color(0xFFC62828))
                else -> AcadPill(text = "Planned", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
