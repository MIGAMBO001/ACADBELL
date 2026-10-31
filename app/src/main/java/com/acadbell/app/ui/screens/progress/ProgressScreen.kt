package com.acadbell.app.ui.screens.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.acadbell.app.domain.RunOutcome
import com.acadbell.app.domain.TimeUtils
import com.acadbell.app.repository.model.CourseProgress
import com.acadbell.app.repository.model.DayBar
import com.acadbell.app.repository.model.HistoryItem
import com.acadbell.app.ui.components.AcadCard
import com.acadbell.app.ui.components.AcadPill
import com.acadbell.app.ui.components.EmptyState
import com.acadbell.app.ui.components.ProgressRing
import com.acadbell.app.ui.components.ProgressTrack
import com.acadbell.app.ui.components.SectionHeader
import com.acadbell.app.ui.components.StatTile
import com.acadbell.app.ui.components.parseHexColor
import com.acadbell.app.ui.theme.Shape
import com.acadbell.app.ui.icons.AcadIcons

@Composable
fun ProgressScreen(
    onOpenCourseProgress: (Long) -> Unit,
    viewModel: ProgressViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.reconcile() }
    val s = state.snapshot

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 12.dp, 20.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    Text("Progress", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Everything here is calculated from reading you actually did — not from time you planned.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { ReadingTimeHero(s.totalActualSeconds, state.bars) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        value = "${s.completedSessions}",
                        label = "Sessions completed",
                        modifier = Modifier.weight(1f),
                        accent = Color(0xFF2E7D32),
                        icon = AcadIcons.CheckCircle
                    )
                    StatTile(
                        value = "${s.missedSessions}",
                        label = "Missed",
                        modifier = Modifier.weight(1f),
                        accent = Color(0xFFC62828),
                        icon = AcadIcons.Cancel
                    )
                    StatTile(
                        value = "${s.interruptedSessions}",
                        label = "Interrupted",
                        modifier = Modifier.weight(1f),
                        accent = Color(0xFFEF6C00),
                        icon = AcadIcons.PauseCircle
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        value = "${s.topicsCompleted}",
                        label = "Topics completed",
                        modifier = Modifier.weight(1f),
                        accent = Color(0xFF2E7D32),
                        icon = AcadIcons.Topic
                    )
                    StatTile(
                        value = "${s.topicsRemaining}",
                        label = "Topics remaining",
                        modifier = Modifier.weight(1f),
                        accent = Color(0xFFEF6C00),
                        icon = AcadIcons.Topic
                    )
                    StatTile(
                        value = TimeUtils.formatDuration(s.minutesThisWeek.toLong()),
                        label = "This week",
                        modifier = Modifier.weight(1f),
                        icon = AcadIcons.Timer
                    )
                }
            }

            item { ConsistencyCard(streak = s.activeStudyDayStreak, consistency = s.consistencyPercent) }

            item { SectionHeader(title = "Last 7 days") }
            item { WeekChart(state.bars) }

            item { SectionHeader(title = "Course completion") }
            if (state.courses.isEmpty()) {
                item {
                    EmptyState(
                        icon = AcadIcons.Insights,
                        title = "No courses yet",
                        message = "Add courses and topics to see per-course completion percentages."
                    )
                }
            } else {
                items(state.courses, key = { it.courseId }) { course ->
                    CourseProgressCard(course) { onOpenCourseProgress(course.courseId) }
                }
            }

            item { SectionHeader(title = "Reading history") }
            if (state.history.isEmpty()) {
                item {
                    EmptyState(
                        icon = AcadIcons.Timer,
                        title = "No completed sessions yet",
                        message = "Your finished reading sessions will appear here with actual minutes and topics covered.",
                        actionLabel = null
                    )
                }
            } else {
                items(state.history, key = { it.runId }) { item -> HistoryRow(item) }
            }
        }
    }
}

@Composable
private fun ReadingTimeHero(totalSeconds: Long, bars: List<DayBar>) {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shape.card)
            .background(MaterialTheme.colorScheme.primary)
            .padding(18.dp)
    ) {
        Column {
            Text("TOTAL READING TIME", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$hours", style = MaterialTheme.typography.displayLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "h $minutes" + "m",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            // Mini sparkline from actual minutes.
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val max = (bars.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(1)
                bars.forEach { bar ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight((bar.minutes.toFloat() / max).coerceIn(0.04f, 1f))
                                .clip(Shape.cardSmall)
                                .background(Color.White.copy(alpha = if (bar.isToday) 1f else 0.55f))
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                bars.forEach { bar ->
                    Text(
                        bar.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsistencyCard(streak: Int, consistency: Int) {
    AcadCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(fraction = consistency / 100f, size = 56.dp, label = "$consistency%")
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Reading consistency", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(
                    if (consistency == 0) "Complete a session to start tracking consistency."
                    else "You showed up for $consistency% of the sessions you planned in the last 4 weeks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                AcadPill(text = "🔥 $streak day streak", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun WeekChart(bars: List<DayBar>) {
    val max = (bars.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(1)
    AcadCard {
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            bars.forEach { bar ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        if (bar.minutes > 0) "${bar.minutes}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(((bar.minutes.toFloat() / max) * 0.85f).coerceIn(0.02f, 1f))
                            .clip(Shape.cardSmall)
                            .background(
                                if (bar.isToday) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            )
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        bar.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (bar.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (bar.isToday) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Minutes of actual reading per day",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CourseProgressCard(course: CourseProgress, onClick: () -> Unit) {
    val accent = parseHexColor(course.colorHex)
    AcadCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(Shape.cardSmall).background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(course.code.take(3), style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(course.code, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
                Text(course.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            }
            Text(
                "${course.completionPercent}%",
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(10.dp))
        ProgressTrack(fraction = course.completionPercent / 100f, color = accent)
        Spacer(Modifier.height(8.dp))
        Row {
            AcadPill("${course.topicsDone}/${course.topicsTotal} topics", color = accent)
            Spacer(Modifier.width(6.dp))
            AcadPill(TimeUtils.formatDuration(course.actualSeconds / 60), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            AcadPill("${course.sessionsDone} session(s)", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem) {
    val (label, color) = when (item.outcome) {
        RunOutcome.COMPLETED.name -> "Completed" to Color(0xFF2E7D32)
        RunOutcome.EXTENDED.name -> "Extended" to Color(0xFF2E7D32)
        RunOutcome.STOPPED_EARLY.name -> "Stopped early" to Color(0xFFEF6C00)
        RunOutcome.INTERRUPTED.name -> "Interrupted" to Color(0xFFEF6C00)
        else -> "Missed" to Color(0xFFC62828)
    }
    AcadCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${item.courseCode} · ${item.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(item.dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    TimeUtils.formatDuration(item.actualSeconds / 60),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                AcadPill(label, color = color)
            }
        }
        if (item.topicsCompleted > 0 || item.topicsPending > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "${item.topicsCompleted} topic(s) ticked · ${item.topicsPending} left pending",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
