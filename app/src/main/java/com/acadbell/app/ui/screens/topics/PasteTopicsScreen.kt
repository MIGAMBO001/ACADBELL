package com.acadbell.app.ui.screens.topics

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.acadbell.app.ui.components.AcadCard
import com.acadbell.app.ui.components.InfoBanner
import com.acadbell.app.ui.components.SectionHeader
import com.acadbell.app.ui.components.TopicCheckbox
import com.acadbell.app.ui.screens.courses.AcadTopBar
import com.acadbell.app.ui.screens.courses.CoursesViewModel
import com.acadbell.app.ui.theme.Shape
import com.acadbell.app.ui.icons.AcadIcons

/**
 * Two-step topic import:
 *  1. Paste the raw outline (works with numbered lists, bullets, roman numerals, semicolons).
 *  2. Review: every detected topic is shown as an editable, individually-ticked row.
 *
 * Nothing is saved until the student confirms on the review screen.
 */
@Composable
fun PasteTopicsScreen(
    courseId: Long,
    onBack: () -> Unit,
    onSaved: (Int) -> Unit,
    viewModel: CoursesViewModel = viewModel()
) {
    val raw by viewModel.pasteRaw.collectAsStateWithLifecycle()
    val drafts by viewModel.reviewDrafts.collectAsStateWithLifecycle()
    val summary by viewModel.parseSummary.collectAsStateWithLifecycle()
    val splitCommas by viewModel.splitCommas.collectAsStateWithLifecycle()

    var showReview by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AcadTopBar(title = if (showReview) "Review topics" else "Paste topic list", onBack = onBack) }
    ) { padding ->
        if (!showReview) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoBanner(
                    title = "Paste the whole outline",
                    message = "Numbered lists, bullet points, roman numerals and semicolons are separated automatically. " +
                        "You will review every entry before anything is saved.",
                    icon = AcadIcons.Info
                )

                OutlinedTextField(
                    value = raw,
                    onValueChange = viewModel::setPasteRaw,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 380.dp),
                    label = { Text("Course topics") },
                    placeholder = {
                        Text(
                            "1. Introduction to Process Management\n" +
                                "2. CPU Scheduling Algorithms\n" +
                                "• Deadlock & Avoidance\n" +
                                "iii. Memory Paging"
                        )
                    },
                    shape = Shape.cardSmall
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Split comma-separated lists", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Turn \"Mitosis, Meiosis, Osmosis\" into three topics.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = splitCommas, onCheckedChange = viewModel::setSplitCommas)
                }

                if (raw.isNotBlank()) {
                    AcadCard(padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AcadIcons.Paste, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { showReview = true },
                    enabled = drafts.isNotEmpty(),
                    shape = Shape.button,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("Review ${drafts.size} topic(s)", fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${drafts.count { it.selected }} of ${drafts.size} selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        drafts.indices.forEach { i -> if (drafts[i].selected) viewModel.toggleDraft(i) }
                    }) { Text("Clear all") }
                    TextButton(onClick = {
                        drafts.indices.forEach { i -> if (!drafts[i].selected) viewModel.toggleDraft(i) }
                    }) { Text("Select all") }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 0.dp, 20.dp, 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(drafts) { index, draft ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(Shape.cardSmall)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, Shape.cardSmall)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TopicCheckbox(checked = draft.selected, onToggle = { viewModel.toggleDraft(index) }, size = 22.dp)
                            Spacer(Modifier.width(10.dp))
                            BasicTextField(
                                value = draft.title,
                                onValueChange = { viewModel.editDraft(index, it) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.removeDraft(index) }) {
                                Icon(AcadIcons.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Column(Modifier.padding(20.dp)) {
                    Button(
                        onClick = { viewModel.saveReviewedTopics(courseId) { count -> onSaved(count) } },
                        enabled = drafts.any { it.selected },
                        shape = Shape.button,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Save ${drafts.count { it.selected }} topic(s)", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
