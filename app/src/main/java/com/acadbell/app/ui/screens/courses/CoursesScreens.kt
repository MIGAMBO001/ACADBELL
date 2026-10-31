package com.acadbell.app.ui.screens.courses

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.acadbell.app.data.db.TopicEntity
import com.acadbell.app.domain.TopicStatus
import com.acadbell.app.ui.components.AcadCard
import com.acadbell.app.ui.components.AcadPill
import com.acadbell.app.ui.components.CircleIconButton
import com.acadbell.app.ui.components.EmptyState
import com.acadbell.app.ui.components.ProgressTrack
import com.acadbell.app.ui.components.ProgressRing
import com.acadbell.app.ui.components.SectionHeader
import com.acadbell.app.ui.components.TopicCheckbox
import com.acadbell.app.ui.components.parseHexColor
import com.acadbell.app.ui.theme.Shape
import com.acadbell.app.ui.theme.CoursePalette
import com.acadbell.app.ui.icons.AcadIcons

// ═══════════════════════════════ COURSE LIST ═══════════════════════════════

@Composable
fun CoursesScreen(
    onOpenCourse: (Long) -> Unit,
    onCreateCourse: () -> Unit,
    viewModel: CoursesViewModel = viewModel()
) {
    val courses by viewModel.courses.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateCourse,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = Shape.pill,
                icon = { Icon(AcadIcons.Add, contentDescription = null) },
                text = { Text("Add course", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 12.dp, 20.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text("My Courses", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Add a course, paste its topic list, then build your reading timetable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (courses.isEmpty()) {
                item {
                    EmptyState(
                        icon = AcadIcons.Book,
                        title = "No courses yet",
                        message = "Courses group your topics and reading sessions. Create your first one to begin.",
                        actionLabel = "Create a course",
                        onAction = onCreateCourse
                    )
                }
            } else {
                items(courses, key = { it.course.id }) { card ->
                    CourseListCard(card) { onOpenCourse(card.course.id) }
                }
            }
        }
    }
}

@Composable
private fun CourseListCard(card: CourseCardState, onClick: () -> Unit) {
    val accent = parseHexColor(card.course.colorHex)
    AcadCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(Shape.cardSmall)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    card.course.code.take(3),
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(card.course.code, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
                Text(
                    card.course.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                if (card.course.lecturer.isNotBlank()) {
                    Text(card.course.lecturer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            ProgressRing(fraction = card.percent / 100f, size = 40.dp, strokeWidth = 5.dp, color = accent, label = "${card.percent}%")
        }
        Spacer(Modifier.height(12.dp))
        ProgressTrack(fraction = card.percent / 100f, color = accent)
        Spacer(Modifier.height(8.dp))
        Row {
            AcadPill("${card.topicsDone}/${card.topicCount} topics", color = accent)
            Spacer(Modifier.width(6.dp))
            AcadPill("${card.sessionCount} session(s)", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ═══════════════════════════════ COURSE EDITOR ═══════════════════════════════

@Composable
fun CourseEditScreen(
    courseId: Long,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: CoursesViewModel = viewModel()
) {
    val form by viewModel.courseForm.collectAsStateWithLifecycle()
    LaunchedEffect(courseId) { viewModel.loadCourse(courseId) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AcadTopBar(
                title = if (courseId > 0) "Edit course" else "New course",
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = form.code,
                onValueChange = { v -> viewModel.updateForm { it.copy(code = v) } },
                label = { Text("Course code") },
                placeholder = { Text("e.g. CSC 301") },
                singleLine = true,
                shape = Shape.button,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.title,
                onValueChange = { v -> viewModel.updateForm { it.copy(title = v) } },
                label = { Text("Course title") },
                placeholder = { Text("e.g. Operating Systems") },
                singleLine = false,
                shape = Shape.button,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.lecturer,
                onValueChange = { v -> viewModel.updateForm { it.copy(lecturer = v) } },
                label = { Text("Lecturer (optional)") },
                singleLine = true,
                shape = Shape.button,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = if (form.weeklyTargetMinutes == 0) "" else (form.weeklyTargetMinutes / 60).toString(),
                onValueChange = { v ->
                    val hours = v.filter { it.isDigit() }.take(2).toIntOrNull() ?: 0
                    viewModel.updateForm { it.copy(weeklyTargetMinutes = hours * 60) }
                },
                label = { Text("Weekly reading target (hours, optional)") },
                singleLine = true,
                shape = Shape.button,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Colour", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(CoursePalette) { hex ->
                    val selected = form.colorHex == hex
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(parseHexColor(hex))
                            .then(
                                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .clickable { viewModel.updateForm { it.copy(colorHex = hex) } }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { viewModel.saveCourse(onSaved) },
                enabled = form.canSave,
                shape = Shape.button,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(if (courseId > 0) "Save changes" else "Create course", fontWeight = FontWeight.SemiBold)
            }
            if (!form.canSave) {
                Text(
                    "Course code and title are required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ═══════════════════════════════ COURSE DETAIL ═══════════════════════════════

@Composable
fun CourseDetailScreen(
    courseId: Long,
    onBack: () -> Unit,
    onEditCourse: (Long) -> Unit,
    onPasteTopics: (Long) -> Unit,
    onEditTopic: (Long, Long?) -> Unit,
    onDeleted: () -> Unit,
    viewModel: CoursesViewModel = viewModel()
) {
    val course by viewModel.courseDetail.collectAsStateWithLifecycle()
    val topics by viewModel.topics.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showAddTopic by remember { mutableStateOf(false) }

    LaunchedEffect(courseId) { viewModel.openCourse(courseId) }

    val pending = topics.filter { it.status == TopicStatus.PENDING }
    val done = topics.filter { it.status == TopicStatus.COMPLETED }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AcadTopBar(
                title = course?.code ?: "Course",
                onBack = onBack,
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(AcadIcons.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Edit course") },
                                leadingIcon = { Icon(AcadIcons.Edit, contentDescription = null) },
                                onClick = { menuOpen = false; onEditCourse(courseId) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete course") },
                                leadingIcon = { Icon(AcadIcons.Delete, contentDescription = null) },
                                onClick = { menuOpen = false; confirmDelete = true }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddTopic = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = Shape.pill,
                icon = { Icon(AcadIcons.Add, contentDescription = null) },
                text = { Text("Topic", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    course?.title ?: "",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                course?.lecturer?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                AcadCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(
                            fraction = if (topics.isEmpty()) 0f else done.size.toFloat() / topics.size,
                            size = 54.dp,
                            color = parseHexColor(course?.colorHex ?: "#C62828"),
                            label = "${if (topics.isEmpty()) 0 else done.size * 100 / topics.size}%"
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${done.size} of ${topics.size} topics completed",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${pending.size} still pending",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onPasteTopics(courseId) },
                        shape = Shape.button,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(AcadIcons.Paste, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Paste list", maxLines = 1)
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = { showAddTopic = true },
                        shape = Shape.button,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(AcadIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add topic", maxLines = 1)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            TabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Pending (${pending.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Completed (${done.size})") })
            }

            val list = if (tab == 0) pending else done
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(20.dp)) {
                    EmptyState(
                        icon = AcadIcons.Book,
                        title = if (tab == 0) "No pending topics" else "Nothing completed yet",
                        message = if (tab == 0)
                            "Paste your course outline and ACADBELL will split it into individual topics."
                        else
                            "Tick topics while reading — only you can mark a topic complete.",
                        actionLabel = if (tab == 0) "Paste topics" else null,
                        onAction = if (tab == 0) ({ onPasteTopics(courseId) }) else null
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 12.dp, 20.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(list, key = { it.id }) { topic ->
                        TopicRow(
                            topic = topic,
                            onToggle = { viewModel.toggleTopic(topic.id, it) },
                            onClick = { onEditTopic(courseId, topic.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddTopic) {
        AddTopicDialog(
            onDismiss = { showAddTopic = false },
            onAdd = { title, note, minutes ->
                viewModel.addTopic(courseId, title, note, minutes)
                showAddTopic = false
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this course?") },
            text = { Text("Its topics and reading sessions will also be removed. Reading history you already recorded is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteCourse(courseId, onDeleted)
                }) { Text("Delete", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TopicRow(topic: TopicEntity, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val isDone = topic.status == TopicStatus.COMPLETED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shape.cardSmall)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, Shape.cardSmall)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopicCheckbox(checked = isDone, onToggle = { onToggle(!isDone) })
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                topic.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 2
            )
            if (topic.note.isNotBlank()) {
                Text(topic.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        if (topic.estimatedMinutes > 0) {
            AcadPill(text = "${topic.estimatedMinutes}m", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AddTopicDialog(onDismiss: () -> Unit, onAdd: (String, String, Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add topic") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Topic title") }, singleLine = false, shape = Shape.button
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Note (optional)") }, singleLine = true, shape = Shape.button
                )
                OutlinedTextField(
                    value = minutes, onValueChange = { minutes = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Estimated minutes (optional)") }, singleLine = true, shape = Shape.button
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(title, note, minutes.toIntOrNull() ?: 0) },
                enabled = title.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ═══════════════════════════════ SHARED TOP BAR ═══════════════════════════════

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AcadTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(AcadIcons.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}
