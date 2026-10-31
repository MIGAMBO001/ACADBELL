package com.acadbell.app.ui.screens.timetable

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.acadbell.app.domain.OccurrenceState
import com.acadbell.app.domain.TimeUtils
import com.acadbell.app.ui.components.AcadCard
import com.acadbell.app.ui.components.AcadPill
import com.acadbell.app.ui.components.EmptyState
import com.acadbell.app.ui.components.SectionHeader
import com.acadbell.app.ui.components.parseHexColor
import com.acadbell.app.ui.theme.Shape
import java.time.LocalDate
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import com.acadbell.app.ui.icons.AcadIcons

@Composable
fun TimetableScreen(
    onOpenEntry: (Long) -> Unit,
    onCreateSession: (Long?) -> Unit,
    onStartSession: (Long) -> Unit,
    viewModel: TimetableViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onCreateSession(state.selectedDate.toEpochDay()) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = Shape.pill,
                icon = { Icon(AcadIcons.Add, contentDescription = null) },
                text = { Text("Session", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    "Timetable",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Plan your reading. Stay consistent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                ViewSwitcher(state.view, onSelect = viewModel::setView)
                Spacer(Modifier.height(14.dp))
                PeriodHeader(
                    label = periodLabel(state),
                    onPrev = { viewModel.shiftPeriod(-1) },
                    onNext = { viewModel.shiftPeriod(1) },
                    onToday = viewModel::goToToday
                )
                Spacer(Modifier.height(12.dp))
            }

            when (state.view) {
                TimetableView.DAY -> DayView(state, onOpenEntry, onStartSession)
                TimetableView.WEEK -> WeekView(state, onSelectDate = viewModel::selectDate, onOpenEntry = onOpenEntry)
                TimetableView.MONTH -> MonthView(state, onSelectDate = viewModel::selectDate)
            }
        }
    }
}

@Composable
private fun ViewSwitcher(current: TimetableView, onSelect: (TimetableView) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shape.pill)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TimetableView.entries.forEach { view ->
            val selected = view == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(Shape.pill)
                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(view) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    view.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

private fun periodLabel(state: TimetableUiState): String = when (state.view) {
    TimetableView.DAY -> state.selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()))
    TimetableView.WEEK -> {
        val start = TimeUtils.startOfWeek(state.selectedDate)
        val end = start.plusDays(6)
        "${start.dayOfMonth} ${start.month.getDisplayName(JTextStyle.SHORT, Locale.getDefault())} – " +
            "${end.dayOfMonth} ${end.month.getDisplayName(JTextStyle.SHORT, Locale.getDefault())}"
    }
    TimetableView.MONTH -> state.selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
}

@Composable
private fun PeriodHeader(label: String, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        AcadPill(text = "Today", modifier = Modifier.clickable { onToday() })
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { onPrev() },
            contentAlignment = Alignment.Center
        ) { Icon(AcadIcons.ChevronLeft, contentDescription = "Previous", modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { onNext() },
            contentAlignment = Alignment.Center
        ) { Icon(AcadIcons.ChevronRight, contentDescription = "Next", modifier = Modifier.size(18.dp)) }
    }
}

// ═══════════════════════════════ DAY ═══════════════════════════════

@Composable
private fun DayView(
    state: TimetableUiState,
    onOpenEntry: (Long) -> Unit,
    onStartSession: (Long) -> Unit
) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 0.dp, 20.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            AcadCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AcadIcons.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "${state.dayItems.size} session(s) planned",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${TimeUtils.formatDuration(state.totalMinutesSelectedDay.toLong())} of reading time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (state.dayItems.isEmpty()) {
            item {
                EmptyState(
                    icon = AcadIcons.EventBusy,
                    title = "Nothing planned for this day",
                    message = "Add a reading session to this date, or pick a recurring day pattern.",
                    actionLabel = null,
                    onAction = null
                )
            }
        } else {
            items(state.dayItems, key = { it.occurrenceId }) { entry ->
                EntryCard(entry, onClick = { onOpenEntry(entry.occurrenceId) }, onStart = { onStartSession(entry.occurrenceId) })
            }
        }
    }
}

@Composable
private fun EntryCard(entry: TimetableEntry, onClick: () -> Unit, onStart: () -> Unit) {
    val accent = parseHexColor(entry.colorHex)
    AcadCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .width(56.dp)
                    .clip(Shape.cardSmall)
                    .background(accent.copy(alpha = 0.1f))
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    TimeUtils.formatTime(entry.startMinute).substringBefore(" "),
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    TimeUtils.formatTime(entry.startMinute).substringAfter(" "),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    "${entry.courseCode} · ends ${TimeUtils.formatTime(entry.endMinute)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (entry.state) {
                        OccurrenceState.DONE -> AcadPill("Completed", color = Color(0xFF2E7D32))
                        OccurrenceState.MISSED -> AcadPill("Missed", color = Color(0xFFC62828))
                        OccurrenceState.INTERRUPTED -> AcadPill("Interrupted", color = Color(0xFFEF6C00))
                        OccurrenceState.SKIPPED -> AcadPill("Skipped", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> AcadPill("Planned", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (entry.state == OccurrenceState.SCHEDULED) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .clickable { onStart() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AcadIcons.Play, contentDescription = "Start now", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ═══════════════════════════════ WEEK ═══════════════════════════════

@Composable
private fun WeekView(
    state: TimetableUiState,
    onSelectDate: (LocalDate) -> Unit,
    onOpenEntry: (Long) -> Unit
) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 0.dp, 20.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                state.weekDays.forEach { date ->
                    val entries = state.weekEntries[date.toEpochDay()].orEmpty()
                    val isToday = date == LocalDate.now()
                    val selected = date == state.selectedDate
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(Shape.cardSmall)
                            .background(
                                when {
                                    selected -> MaterialTheme.colorScheme.primary
                                    isToday -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            )
                            .border(
                                if (selected) 0.dp else 1.dp,
                                MaterialTheme.colorScheme.outline,
                                Shape.cardSmall
                            )
                            .clickable { onSelectDate(date) }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            date.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.getDefault()).take(2),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(3.dp))
                        if (entries.isNotEmpty()) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) Color.White else MaterialTheme.colorScheme.primary)
                            )
                        } else {
                            Spacer(Modifier.height(5.dp))
                        }
                    }
                }
            }
        }

        state.weekDays.forEach { date ->
            val entries = state.weekEntries[date.toEpochDay()].orEmpty()
            if (entries.isEmpty()) return@forEach
            item(key = "hdr-${date.toEpochDay()}") {
                SectionHeader(
                    title = date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMM", Locale.getDefault()))
                )
            }
            items(entries, key = { it.occurrenceId }) { entry ->
                EntryCard(entry, onClick = { onOpenEntry(entry.occurrenceId) }, onStart = {})
            }
        }

        if (state.weekEntries.values.all { it.isEmpty() }) {
            item {
                EmptyState(
                    icon = AcadIcons.EventBusy,
                    title = "Empty week",
                    message = "No reading sessions planned. Add one and it will repeat on the days you choose."
                )
            }
        }
    }
}

// ═══════════════════════════════ MONTH ═══════════════════════════════

@Composable
private fun MonthView(state: TimetableUiState, onSelectDate: (LocalDate) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        state.monthCells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { cell ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(Shape.cardSmall)
                            .background(
                                when {
                                    cell.selected -> MaterialTheme.colorScheme.primary
                                    cell.isToday -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            )
                            .border(
                                if (cell.selected) 0.dp else 1.dp,
                                MaterialTheme.colorScheme.outline,
                                Shape.cardSmall
                            )
                            .clickable { onSelectDate(cell.date) }
                            .padding(top = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            cell.date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                cell.selected -> Color.White
                                cell.inCurrentMonth -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            },
                            fontWeight = if (cell.isToday || cell.selected) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(Modifier.height(3.dp))
                        if (cell.sessionCount > 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                repeat(cell.sessionCount.coerceAtMost(3)) {
                                    Box(
                                        Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(if (cell.selected) Color.White else MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(10.dp))
        val selectedCount = state.monthCells.firstOrNull { it.selected }?.sessionCount ?: 0
        AcadCard(padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                AcadPill("$selectedCount session(s)")
            }
            Spacer(Modifier.height(6.dp))
            state.dayItems.forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(parseHexColor(entry.colorHex)))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${TimeUtils.formatTime(entry.startMinute)} · ${entry.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            if (state.dayItems.isEmpty()) {
                Text(
                    "No sessions on this date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
