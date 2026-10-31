package com.acadbell.app.ui.screens.courses

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acadbell.app.data.db.CourseEntity
import com.acadbell.app.data.db.TopicEntity
import com.acadbell.app.di.ServiceLocator
import com.acadbell.app.domain.topic.TopicParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CourseCardState(
    val course: CourseEntity,
    val topicCount: Int,
    val topicsDone: Int,
    val sessionCount: Int
) {
    val percent: Int get() = if (topicCount == 0) 0 else topicsDone * 100 / topicCount
}

@OptIn(ExperimentalCoroutinesApi::class)
class CoursesViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.repository(app)

    val courses: StateFlow<List<CourseCardState>> = combine(
        repo.observeCourses(),
        repo.observeAllTopics(),
        repo.observeSessions()
    ) { courseList, topics, sessions ->
        courseList.map { course ->
            val courseTopics = topics.filter { it.courseId == course.id }
            CourseCardState(
                course = course,
                topicCount = courseTopics.size,
                topicsDone = courseTopics.count { it.status == com.acadbell.app.domain.TopicStatus.COMPLETED },
                sessionCount = sessions.count { it.courseId == course.id }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Edit / create ──
    private val _courseForm = MutableStateFlow(CourseForm())
    val courseForm: StateFlow<CourseForm> = _courseForm.asStateFlow()

    fun loadCourse(id: Long) {
        if (id <= 0) {
            _courseForm.value = CourseForm()
            return
        }
        viewModelScope.launch {
            repo.getCourse(id)?.let {
                _courseForm.value = CourseForm(
                    id = it.id, code = it.code, title = it.title,
                    lecturer = it.lecturer, colorHex = it.colorHex,
                    weeklyTargetMinutes = it.weeklyTargetMinutes
                )
            }
        }
    }

    fun updateForm(block: (CourseForm) -> CourseForm) {
        _courseForm.value = block(_courseForm.value)
    }

    fun saveCourse(onDone: (Long) -> Unit) {
        val form = _courseForm.value
        if (form.code.isBlank() || form.title.isBlank()) return
        viewModelScope.launch {
            val id = if (form.id <= 0) {
                repo.createCourse(form.code, form.title, form.lecturer, form.colorHex, form.weeklyTargetMinutes)
            } else {
                repo.getCourse(form.id)?.let {
                    repo.updateCourse(
                        it.copy(
                            code = form.code, title = form.title, lecturer = form.lecturer,
                            colorHex = form.colorHex, weeklyTargetMinutes = form.weeklyTargetMinutes
                        )
                    )
                    form.id
                } ?: form.id
            }
            onDone(id)
        }
    }

    fun deleteCourse(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteCourse(id)
            onDone()
        }
    }

    // ── Course detail: topics ──
    private val _courseId = MutableStateFlow(0L)

    val courseDetail: StateFlow<CourseEntity?> = _courseId.flatMapLatest { id ->
        if (id <= 0) kotlinx.coroutines.flow.flowOf(null) else repo.observeCourse(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val topics: StateFlow<List<TopicEntity>> = _courseId.flatMapLatest { id ->
        if (id <= 0) kotlinx.coroutines.flow.flowOf(emptyList()) else repo.observeTopics(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openCourse(id: Long) { _courseId.value = id }

    fun toggleTopic(topicId: Long, completed: Boolean) {
        viewModelScope.launch { repo.setTopicCompleted(topicId, completed) }
    }

    fun addTopic(courseId: Long, title: String, note: String, minutes: Int) {
        if (title.isBlank()) return
        viewModelScope.launch { repo.addTopic(courseId, title, note, minutes) }
    }

    fun updateTopic(topic: TopicEntity) {
        viewModelScope.launch { repo.updateTopic(topic) }
    }

    fun deleteTopic(id: Long) {
        viewModelScope.launch { repo.deleteTopic(id) }
    }

    // ── Paste & review ──
    private val _pasteRaw = MutableStateFlow("")
    val pasteRaw: StateFlow<String> = _pasteRaw.asStateFlow()

    private val _splitCommas = MutableStateFlow(false)
    val splitCommas: StateFlow<Boolean> = _splitCommas.asStateFlow()

    private val _reviewDrafts = MutableStateFlow<List<TopicDraft>>(emptyList())
    val reviewDrafts: StateFlow<List<TopicDraft>> = _reviewDrafts.asStateFlow()

    private val _parseSummary = MutableStateFlow("")
    val parseSummary: StateFlow<String> = _parseSummary.asStateFlow()

    fun setPasteRaw(text: String) {
        _pasteRaw.value = text
        recomputePreview()
    }

    fun setSplitCommas(value: Boolean) {
        _splitCommas.value = value
        recomputePreview()
    }

    private fun recomputePreview() {
        val result = TopicParser.parse(_pasteRaw.value, _splitCommas.value)
        _reviewDrafts.value = result.topics.map { TopicDraft(it) }
        _parseSummary.value = buildString {
            append("${result.topics.size} topic(s) detected")
            if (result.duplicatesRemoved > 0) append(" · ${result.duplicatesRemoved} duplicate(s) removed")
            append(" · from ${result.rawLineCount} line(s)")
        }
    }

    fun toggleDraft(index: Int) {
        _reviewDrafts.value = _reviewDrafts.value.toMutableList().also {
            it[index] = it[index].copy(selected = !it[index].selected)
        }
    }

    fun editDraft(index: Int, newTitle: String) {
        _reviewDrafts.value = _reviewDrafts.value.toMutableList().also {
            it[index] = it[index].copy(title = newTitle)
        }
    }

    fun removeDraft(index: Int) {
        _reviewDrafts.value = _reviewDrafts.value.toMutableList().also { it.removeAt(index) }
    }

    fun addManualDraft(title: String) {
        if (title.isBlank()) return
        _reviewDrafts.value = _reviewDrafts.value + TopicDraft(title.trim())
    }

    fun saveReviewedTopics(courseId: Long, onDone: (Int) -> Unit) {
        val titles = _reviewDrafts.value.filter { it.selected && it.title.isNotBlank() }.map { it.title }
        viewModelScope.launch {
            val count = repo.addTopics(courseId, titles)
            _pasteRaw.value = ""
            _reviewDrafts.value = emptyList()
            onDone(count)
        }
    }
}

data class CourseForm(
    val id: Long = 0L,
    val code: String = "",
    val title: String = "",
    val lecturer: String = "",
    val colorHex: String = "#C62828",
    val weeklyTargetMinutes: Int = 0
) {
    val canSave: Boolean get() = code.isNotBlank() && title.isNotBlank()
}

data class TopicDraft(
    val title: String,
    val selected: Boolean = true
)
