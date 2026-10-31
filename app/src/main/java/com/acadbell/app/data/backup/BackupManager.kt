package com.acadbell.app.data.backup

import android.content.Context
import android.net.Uri
import com.acadbell.app.di.ServiceLocator
import com.acadbell.app.data.db.CourseEntity
import com.acadbell.app.data.db.ReadingSessionEntity
import com.acadbell.app.data.db.SessionRunEntity
import com.acadbell.app.data.db.TopicEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Offline backup & restore using a plain JSON file the student owns.
 *
 * Why JSON and not a raw .db copy? A JSON export is human-readable, version-tolerant and can be
 * restored into a fresh install without matching SQLite page layouts. It needs no permissions
 * because the student picks the destination through the Storage Access Framework.
 */
class BackupManager(private val context: Context) {

    private val repo = ServiceLocator.repository(context)

    suspend fun exportTo(uri: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val db = ServiceLocator.database(context)
            val courses = db.courseDao().getAll()
            val topics: List<TopicEntity> = db.topicDao().observeAll().first()
            val sessions = db.readingSessionDao().getAll()
            val runs = db.sessionRunDao().getAll()

            val root = JSONObject().apply {
                put("format", "acadbell-backup")
                put("version", 1)
                put("exportedAt", System.currentTimeMillis())
                put("courses", JSONArray().apply {
                    courses.forEach { c ->
                        put(JSONObject().apply {
                            put("id", c.id); put("code", c.code); put("title", c.title)
                            put("lecturer", c.lecturer); put("colorHex", c.colorHex)
                            put("weeklyTargetMinutes", c.weeklyTargetMinutes)
                            put("createdAt", c.createdAt)
                        })
                    }
                })
                put("topics", JSONArray().apply {
                    topics.forEach { t ->
                        put(JSONObject().apply {
                            put("id", t.id); put("courseId", t.courseId); put("title", t.title)
                            put("note", t.note); put("estimatedMinutes", t.estimatedMinutes)
                            put("status", t.status.name); put("completedAt", t.completedAt ?: JSONObject.NULL)
                            put("sortOrder", t.sortOrder)
                        })
                    }
                })
                put("sessions", JSONArray().apply {
                    sessions.forEach { s ->
                        put(JSONObject().apply {
                            put("id", s.id); put("courseId", s.courseId ?: JSONObject.NULL)
                            put("title", s.title); put("startMinute", s.startMinute)
                            put("endMinute", s.endMinute); put("daysOfWeekMask", s.daysOfWeekMask)
                            put("isRecurring", s.isRecurring)
                            put("specificDateEpochDay", s.specificDateEpochDay ?: JSONObject.NULL)
                            put("alarmSound", s.alarmSound); put("vibration", s.vibration)
                            put("snoozeMinutes", s.snoozeMinutes); put("enabled", s.enabled)
                        })
                    }
                })
                put("runs", JSONArray().apply {
                    runs.forEach { r ->
                        put(JSONObject().apply {
                            put("courseCode", r.courseCode); put("courseTitle", r.courseTitle)
                            put("sessionTitle", r.sessionTitle); put("dateEpochDay", r.dateEpochDay)
                            put("startedAt", r.startedAt); put("endedAt", r.endedAt ?: JSONObject.NULL)
                            put("allocatedSeconds", r.allocatedSeconds); put("actualSeconds", r.actualSeconds)
                            put("outcome", r.outcome.name); put("status", r.status)
                            put("topicsCompletedCount", r.topicsCompletedCount)
                            put("topicsPendingCount", r.topicsPendingCount)
                        })
                    }
                })
            }

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(root.toString(2).toByteArray())
            } ?: error("Could not open destination file")

            BackupSummary(
                courses = courses.size,
                topics = topics.size,
                sessions = sessions.size,
                runs = runs.size
            )
        }
    }

    /** Merges a backup file into the current database (additive — nothing is deleted). */
    suspend fun importFrom(uri: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Could not read the selected file")
            val root = JSONObject(text)
            require(root.optString("format") == "acadbell-backup") { "This is not an ACADBELL backup file" }

            val db = ServiceLocator.database(context)
            var courseCount = 0
            var topicCount = 0
            var sessionCount = 0
            var runCount = 0

            // Map old course ids → new ids so topics land on the right course.
            val idMap = HashMap<Long, Long>()
            val coursesJson = root.optJSONArray("courses") ?: JSONArray()
            for (i in 0 until coursesJson.length()) {
                val o = coursesJson.getJSONObject(i)
                val newId = db.courseDao().insert(
                    CourseEntity(
                        code = o.getString("code"),
                        title = o.getString("title"),
                        lecturer = o.optString("lecturer"),
                        colorHex = o.optString("colorHex", "#C62828"),
                        weeklyTargetMinutes = o.optInt("weeklyTargetMinutes")
                    )
                )
                idMap[o.getLong("id")] = newId
                courseCount++
            }

            val topicsJson = root.optJSONArray("topics") ?: JSONArray()
            val topicIdMap = HashMap<Long, Long>()
            for (i in 0 until topicsJson.length()) {
                val o = topicsJson.getJSONObject(i)
                val mappedCourse = idMap[o.getLong("courseId")] ?: continue
                val newId = db.topicDao().insert(
                    TopicEntity(
                        courseId = mappedCourse,
                        title = o.getString("title"),
                        note = o.optString("note"),
                        estimatedMinutes = o.optInt("estimatedMinutes"),
                        status = runCatching {
                            com.acadbell.app.domain.TopicStatus.valueOf(o.optString("status", "PENDING"))
                        }.getOrDefault(com.acadbell.app.domain.TopicStatus.PENDING)
                    )
                )
                topicIdMap[o.getLong("id")] = newId
                topicCount++
            }

            val sessionsJson = root.optJSONArray("sessions") ?: JSONArray()
            for (i in 0 until sessionsJson.length()) {
                val o = sessionsJson.getJSONObject(i)
                db.readingSessionDao().insert(
                    ReadingSessionEntity(
                        courseId = o.optLong("courseId").takeIf { it > 0 }?.let { idMap[it] },
                        title = o.getString("title"),
                        startMinute = o.getInt("startMinute"),
                        endMinute = o.getInt("endMinute"),
                        daysOfWeekMask = o.optInt("daysOfWeekMask"),
                        isRecurring = o.optBoolean("isRecurring", true),
                        specificDateEpochDay = o.optLong("specificDateEpochDay").takeIf { o.has("specificDateEpochDay") && !o.isNull("specificDateEpochDay") },
                        alarmSound = o.optString("alarmSound", "CLASSIC_BELL"),
                        vibration = o.optBoolean("vibration", true),
                        snoozeMinutes = o.optInt("snoozeMinutes", 5),
                        enabled = o.optBoolean("enabled", true)
                    )
                )
                sessionCount++
            }

            val runsJson = root.optJSONArray("runs") ?: JSONArray()
            for (i in 0 until runsJson.length()) {
                val o = runsJson.getJSONObject(i)
                db.sessionRunDao().insert(
                    SessionRunEntity(
                        courseCode = o.optString("courseCode"),
                        courseTitle = o.optString("courseTitle"),
                        sessionTitle = o.optString("sessionTitle"),
                        dateEpochDay = o.optLong("dateEpochDay"),
                        startedAt = o.optLong("startedAt", System.currentTimeMillis()),
                        endedAt = if (o.isNull("endedAt")) null else o.optLong("endedAt"),
                        allocatedSeconds = o.optLong("allocatedSeconds"),
                        actualSeconds = o.optLong("actualSeconds"),
                        outcome = runCatching {
                            com.acadbell.app.domain.RunOutcome.valueOf(o.optString("outcome", "COMPLETED"))
                        }.getOrDefault(com.acadbell.app.domain.RunOutcome.COMPLETED),
                        status = o.optString("status", "FINISHED"),
                        topicsCompletedCount = o.optInt("topicsCompletedCount"),
                        topicsPendingCount = o.optInt("topicsPendingCount")
                    )
                )
                runCount++
            }

            val repo = ServiceLocator.repository(context)
            repo.materialiseRange(
                com.acadbell.app.domain.TimeUtils.todayEpochDay(),
                com.acadbell.app.domain.TimeUtils.todayEpochDay() + 30
            )
            ServiceLocator.alarmScheduler(context).armAll()
            repo.log("BACKUP_RESTORED", "$courseCount courses, $topicCount topics")

            BackupSummary(courseCount, topicCount, sessionCount, runCount)
        }
    }
}

data class BackupSummary(
    val courses: Int,
    val topics: Int,
    val sessions: Int,
    val runs: Int
) {
    fun describe(): String = "$courses course(s), $topics topic(s), $sessions session(s), $runs history record(s)"
}
