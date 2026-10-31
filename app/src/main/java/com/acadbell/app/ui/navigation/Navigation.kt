package com.acadbell.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import com.acadbell.app.ui.icons.AcadIcons

/** The five primary destinations (§4). */
enum class MainTab(
    val route: String,
    val label: String,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector
) {
    HOME("home", "Home", AcadIcons.Home, AcadIcons.HomeOutlined),
    TIMETABLE("timetable", "Timetable", AcadIcons.Calendar, AcadIcons.CalendarOutlined),
    COURSES("courses", "Courses", AcadIcons.Book, AcadIcons.BookOutlined),
    PROGRESS("progress", "Progress", AcadIcons.Insights, AcadIcons.InsightsOutlined),
    SETTINGS("settings", "Settings", AcadIcons.Settings, AcadIcons.SettingsOutlined)
}

/**
 * Every route in the app. Detail/session routes are full-screen (no bottom bar)
 * so the student stays focused while reading.
 */
object Routes {
    const val HOME = "home"
    const val TIMETABLE = "timetable"
    const val COURSES = "courses"
    const val PROGRESS = "progress"
    const val SETTINGS = "settings"

    const val COURSE_DETAIL = "course/{courseId}"
    const val COURSE_EDIT = "course/edit?courseId={courseId}"
    const val TOPIC_EDIT = "topic/edit?courseId={courseId}&topicId={topicId}"
    const val PASTE_TOPICS = "topics/paste/{courseId}"
    const val TOPIC_REVIEW = "topics/review/{courseId}"

    const val SESSION_EDIT = "session/edit?sessionId={sessionId}&dateEpochDay={dateEpochDay}"
    const val SESSION_DETAIL = "session/{occurrenceId}"
    const val ACTIVE_SESSION = "active/{runId}"
    const val SESSION_SUMMARY = "summary/{runId}"
    const val HISTORY = "history"
    const val PERMISSIONS = "settings/permissions"
    const val BACKUP = "settings/backup"
    const val DIAGNOSTICS = "settings/diagnostics"
    const val PROGRESS_DETAIL = "progress/course/{courseId}"

    fun courseDetail(id: Long) = "course/$id"
    fun courseEdit(id: Long? = null) = if (id == null) "course/edit?courseId=-1" else "course/edit?courseId=$id"
    fun topicEdit(courseId: Long, topicId: Long? = null) =
        "topic/edit?courseId=$courseId&topicId=${topicId ?: -1}"
    fun pasteTopics(courseId: Long) = "topics/paste/$courseId"
    fun topicReview(courseId: Long) = "topics/review/$courseId"
    fun sessionEdit(sessionId: Long? = null, dateEpochDay: Long? = null) =
        "session/edit?sessionId=${sessionId ?: -1}&dateEpochDay=${dateEpochDay ?: -1}"
    fun sessionDetail(occurrenceId: Long) = "session/$occurrenceId"
    fun activeSession(runId: Long) = "active/$runId"
    fun summary(runId: Long) = "summary/$runId"
    fun progressDetail(courseId: Long) = "progress/course/$courseId"
}

/** Routes without the bottom bar. */
val fullScreenRoutes = setOf(
    Routes.COURSE_DETAIL, Routes.COURSE_EDIT, Routes.TOPIC_EDIT, Routes.PASTE_TOPICS,
    Routes.TOPIC_REVIEW, Routes.SESSION_EDIT, Routes.SESSION_DETAIL, Routes.ACTIVE_SESSION,
    Routes.SESSION_SUMMARY, Routes.HISTORY, Routes.PERMISSIONS, Routes.BACKUP,
    Routes.DIAGNOSTICS, Routes.PROGRESS_DETAIL
)
