package com.acadbell.app.repository

import com.acadbell.app.data.db.AcadBellDatabase
import com.acadbell.app.data.db.AlarmRecordEntity
import com.acadbell.app.data.db.AppEventEntity
import com.acadbell.app.data.db.CourseEntity
import com.acadbell.app.data.db.ReadingSessionEntity
import com.acadbell.app.data.db.SessionOccurrenceEntity
import com.acadbell.app.data.db.SessionRunEntity
import com.acadbell.app.data.db.SessionTopicCrossRef
import com.acadbell.app.data.db.TopicEntity
import com.acadbell.app.domain.AlarmKind
import com.acadbell.app.domain.OccurrenceState
import com.acadbell.app.domain.RunOutcome
import com.acadbell.app.domain.TimeUtils
import com.acadbell.app.domain.topic.TopicParser
import com.acadbell.app.domain.TopicStatus
import com.acadbell.app.repository.model.OccurrenceWithSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * The one place that reads and writes ACADBELL data.
 *
 * Invariants enforced here (not in the UI):
 *  • Occurrences are materialised idempotently: (sessionId, epochDay) is unique.
 *  • Closing a run NEVER completes topics — only [setTopicCompleted] does that.
 *  • Every destructive session edit re-materialises future occurrences and asks the
 *    alarm layer to re-arm, so alarms can never point at a deleted session.
 */
class AcadBellRepository(private val db: AcadBellDatabase) {

    private val courseDao = db.courseDao()
    private val topicDao = db.topicDao()
    private val sessionDao = db.readingSessionDao()
    private val occurrenceDao = db.occurrenceDao()
    private val runDao = db.sessionRunDao()
    private val alarmDao = db.alarmDao()
    private val eventDao = db.appEventDao()

    /** Set by ServiceLocator after construction to avoid a circular dependency. */
    var onScheduleChanged: (suspend () -> Unit)? = null

    // ───────────────────────── Courses ─────────────────────────

    fun observeCourses(): Flow<List<CourseEntity>> = courseDao.observeCourses()
    fun observeCourse(id: Long): Flow<CourseEntity?> = courseDao.observeCourse(id)
    fun observeCourseCount(): Flow<Int> = courseDao.observeCourseCount()

    suspend fun getCourse(id: Long): CourseEntity? = courseDao.getCourse(id)

    suspend fun createCourse(code: String, title: String, lecturer: String, colorHex: String, weeklyTargetMinutes: Int = 0): Long {
        val id = courseDao.insert(
            CourseEntity(
                code = code.trim().uppercase(),
                title = title.trim(),
                lecturer = lecturer.trim(),
                colorHex = colorHex,
                weeklyTargetMinutes = weeklyTargetMinutes
            )
        )
        log("COURSE_CREATED", "$code — $title")
        return id
    }

    suspend fun updateCourse(course: CourseEntity) {
        courseDao.update(course.copy(code = course.code.trim().uppercase(), title = course.title.trim()))
    }

    /** Deleting a course cascades to its topics and unlinks its sessions (FK SET_NULL). */
    suspend fun deleteCourse(id: Long) {
        courseDao.deleteById(id)
        onScheduleChanged?.invoke()
        log("COURSE_DELETED", "courseId=$id")
    }

    // ───────────────────────── Topics ─────────────────────────

    fun observeTopics(courseId: Long): Flow<List<TopicEntity>> = topicDao.observeForCourse(courseId)
    fun observeAllTopics(): Flow<List<TopicEntity>> = topicDao.observeAll()
    fun observePendingTopics(): Flow<List<TopicEntity>> = topicDao.observePending()
    fun observeTopicCount(courseId: Long): Flow<Int> = topicDao.observeTopicCount(courseId)
    fun observeCompletedTopicCount(courseId: Long): Flow<Int> = topicDao.observeCompletedCount(courseId)

    suspend fun getTopics(ids: List<Long>): List<TopicEntity> =
        if (ids.isEmpty()) emptyList() else topicDao.getByIds(ids)

    suspend fun addTopic(courseId: Long, title: String, note: String = "", estimatedMinutes: Int = 0): Long {
        val order = topicDao.observeForCourse(courseId).let { 0 } // sortOrder is append-only
        return topicDao.insert(
            TopicEntity(
                courseId = courseId,
                title = title.trim(),
                note = note.trim(),
                estimatedMinutes = estimatedMinutes,
                sortOrder = order
            )
        )
    }

    /** Bulk insert used by the paste-review screen. Returns how many rows were written. */
    suspend fun addTopics(courseId: Long, titles: List<String>): Int {
        if (titles.isEmpty()) return 0
        val rows = titles.mapIndexed { index, title ->
            TopicEntity(
                courseId = courseId,
                title = title.trim(),
                estimatedMinutes = TopicParser.estimateMinutes(title),
                sortOrder = index
            )
        }
        topicDao.insertAll(rows)
        log("TOPICS_PASTED", "${rows.size} topics → course $courseId")
        return rows.size
    }

    suspend fun updateTopic(topic: TopicEntity) {
        topicDao.update(topic.copy(title = topic.title.trim()))
    }

    suspend fun deleteTopic(id: Long) {
        topicDao.getTopic(id)?.let { topicDao.delete(it) }
    }

    /**
     * THE ONLY PATH THAT MARKS A TOPIC COMPLETE.
     * Called exclusively from explicit student actions (checkbox tap / detail toggle).
     */
    suspend fun setTopicCompleted(topicId: Long, completed: Boolean) {
        val topic = topicDao.getTopic(topicId) ?: return
        topicDao.setStatus(
            id = topicId,
            status = if (completed) TopicStatus.COMPLETED.name else TopicStatus.PENDING.name,
            completedAt = if (completed) System.currentTimeMillis() else null
        )
        log(
            if (completed) "TOPIC_COMPLETED" else "TOPIC_REOPENED",
            "topic=${topic.title.take(60)}"
        )
    }

    suspend fun moveTopic(topicId: Long, newSessionId: Long) {
        sessionDao.unassignTopic(topicId)
        sessionDao.assignTopics(listOf(SessionTopicCrossRef(newSessionId, topicId)))
    }

    suspend fun assignTopicsToSession(sessionId: Long, topicIds: List<Long>) {
        sessionDao.clearTopics(sessionId)
        if (topicIds.isNotEmpty()) {
            sessionDao.assignTopics(topicIds.map { SessionTopicCrossRef(sessionId, it) })
        }
    }

    suspend fun topicIdsForSession(sessionId: Long): List<Long> =
        sessionDao.getTopicIds(sessionId).map { it.topicId }

    // ───────────────────── Reading session templates ─────────────────────

    fun observeSessions(): Flow<List<ReadingSessionEntity>> = sessionDao.observeAll()
    fun observeSession(id: Long): Flow<ReadingSessionEntity?> = sessionDao.observeById(id)
    suspend fun getSession(id: Long): ReadingSessionEntity? = sessionDao.getById(id)

    suspend fun createSession(session: ReadingSessionEntity, topicIds: List<Long> = emptyList()): Long {
        val id = sessionDao.insert(session)
        if (topicIds.isNotEmpty()) assignTopicsToSession(id, topicIds)
        materialiseRange(TimeUtils.todayEpochDay(), TimeUtils.todayEpochDay() + MaterialiseAheadDays)
        onScheduleChanged?.invoke()
        log("SESSION_CREATED", "${session.title} ${TimeUtils.formatRange(session.startMinute, session.endMinute)}")
        return id
    }

    suspend fun updateSession(session: ReadingSessionEntity, topicIds: List<Long>? = null) {
        sessionDao.update(session)
        topicIds?.let { assignTopicsToSession(session.id, it) }
        // Drop future, not-yet-started occurrences so the template change takes effect cleanly.
        occurrenceDao.deleteFutureForSession(session.id, TimeUtils.todayEpochDay())
        materialiseRange(TimeUtils.todayEpochDay(), TimeUtils.todayEpochDay() + MaterialiseAheadDays)
        onScheduleChanged?.invoke()
        log("SESSION_UPDATED", "id=${session.id}")
    }

    suspend fun deleteSession(id: Long) {
        sessionDao.deleteById(id)   // occurrences + topic links cascade
        onScheduleChanged?.invoke()
        log("SESSION_DELETED", "id=$id")
    }

    suspend fun setSessionEnabled(id: Long, enabled: Boolean) {
        sessionDao.getById(id)?.let { sessionDao.update(it.copy(enabled = enabled)) }
        occurrenceDao.deleteFutureForSession(id, TimeUtils.todayEpochDay())
        materialiseRange(TimeUtils.todayEpochDay(), TimeUtils.todayEpochDay() + MaterialiseAheadDays)
        onScheduleChanged?.invoke()
    }

    // ───────────────────────── Occurrences ─────────────────────────

    fun observeOccurrencesForDay(epochDay: Long): Flow<List<SessionOccurrenceEntity>> =
        occurrenceDao.observeForDay(epochDay)

    fun observeOccurrencesBetween(from: Long, to: Long): Flow<List<SessionOccurrenceEntity>> =
        occurrenceDao.observeBetween(from, to)

    fun observeOccurrencesWithSession(from: Long, to: Long): Flow<List<OccurrenceWithSession>> =
        combine(
            occurrenceDao.observeBetween(from, to),
            sessionDao.observeAll()
        ) { occurrences, sessions ->
            val byId = sessions.associateBy { it.id }
            occurrences.mapNotNull { occ ->
                byId[occ.sessionId]?.let { OccurrenceWithSession(occ, it) }
            }
        }

    /**
     * Creates the concrete daily rows for every enabled session between [fromDay] and [toDay].
     * Safe to call repeatedly — the unique (sessionId, epochDay) index makes it idempotent.
     */
    suspend fun materialiseRange(fromDay: Long, toDay: Long) {
        val sessions = sessionDao.getAll().filter { it.enabled }
        for (session in sessions) {
            var day = fromDay
            while (day <= toDay) {
                val date = LocalDate.ofEpochDay(day)
                val matches = if (session.isRecurring) {
                    session.daysOfWeekMask == 0 || TimeUtils.maskContains(session.daysOfWeekMask, date)
                } else {
                    session.specificDateEpochDay == day
                }
                if (matches) {
                    val existing = occurrenceDao.find(session.id, day)
                    if (existing == null) {
                        occurrenceDao.insert(
                            SessionOccurrenceEntity(
                                sessionId = session.id,
                                dateEpochDay = day,
                                plannedStartMillis = TimeUtils.millisAt(day, session.startMinute),
                                plannedEndMillis = TimeUtils.millisAt(day, session.endMinute)
                            )
                        )
                    }
                }
                day++
            }
        }
    }

    /** Recomputes overdue occurrences as MISSED and closes any run left ACTIVE by a process kill. */
    suspend fun reconcileState() {
        val now = System.currentTimeMillis()
        occurrenceDao.findOverdue(now).forEach { occ ->
            occurrenceDao.setState(occ.id, OccurrenceState.MISSED.name, null)
            log("OCCURRENCE_MISSED", "occurrence=${occ.id}")
        }
        // A run that is still ACTIVE but whose occurrence is long gone = interrupted session.
        runDao.getActiveRun()?.let { run ->
            val ageMinutes = (now - run.startedAt) / 60_000
            if (ageMinutes > MaxPlausibleRunMinutes) {
                finishRunInternal(
                    run = run,
                    outcome = RunOutcome.INTERRUPTED,
                    note = "Auto-closed after app restart"
                )
            }
        }
    }

    suspend fun getOccurrence(id: Long): SessionOccurrenceEntity? = occurrenceDao.getById(id)

    suspend fun markOccurrenceState(id: Long, state: OccurrenceState) {
        occurrenceDao.setState(id, state.name, null)
        log("OCCURRENCE_${state.name}", "occurrence=$id")
    }

    /** Removes a single day-instance without touching the recurring template. */
    suspend fun skipOccurrence(occurrence: SessionOccurrenceEntity) {
        occurrenceDao.setState(occurrence.id, OccurrenceState.SKIPPED.name, null)
        occurrenceDao.deleteById(occurrence.id)
        onScheduleChanged?.invoke()
        log("OCCURRENCE_SKIPPED", "date=${occurrence.dateEpochDay}")
    }

    suspend fun upcomingOccurrences(after: Long, limit: Int = 5): List<SessionOccurrenceEntity> =
        occurrenceDao.findUpcoming(after).take(limit)

    /** Used by the "test alarm" button: the soonest occurrence today, else the next one. */
    suspend fun firstUpcomingOrTodayOccurrence(): SessionOccurrenceEntity? {
        val today = TimeUtils.todayEpochDay()
        materialiseRange(today, today + 7)
        return occurrenceDao.getBetween(today, today + 7)
            .firstOrNull { it.plannedEndMillis > System.currentTimeMillis() }
            ?: occurrenceDao.getBetween(today, today).firstOrNull()
    }

    // ───────────────────────── Session runs ─────────────────────────

    fun observeActiveRuns(): Flow<List<SessionRunEntity>> = runDao.observeActive()
    fun observeRun(id: Long): Flow<SessionRunEntity?> = runDao.observeById(id)
    fun observeFinishedRuns(): Flow<List<SessionRunEntity>> = runDao.observeFinished()
    fun observeRecentRuns(limit: Int = 20): Flow<List<SessionRunEntity>> = runDao.observeRecent(limit)
    fun observeTotalActualSeconds(): Flow<Long> = runDao.observeTotalActualSeconds()
    fun observeFinishedCount(): Flow<Int> = runDao.observeFinishedCount()
    fun observeInterruptedCount(): Flow<Int> = runDao.observeInterruptedCount()
    fun observeMissedCount(): Flow<Int> = runDao.observeMissedCount()
    suspend fun getRun(id: Long): SessionRunEntity? = runDao.getById(id)
    suspend fun getActiveRun(): SessionRunEntity? = runDao.getActiveRun()

    /**
     * Starts reading. Either from a scheduled occurrence or ad-hoc ("start now").
     * Returns the new run id.
     */
    suspend fun startRun(occurrenceId: Long?, extraSeconds: Long = 0): Long {
        val occurrence = occurrenceId?.let { occurrenceDao.getById(it) }
        val session = occurrence?.let { sessionDao.getById(it.sessionId) }
        val course = session?.courseId?.let { courseDao.getCourse(it) }

        val allocated = if (occurrence != null) {
            (occurrence.plannedEndMillis - occurrence.plannedStartMillis) / 1000
        } else {
            (session?.let { (it.endMinute - it.startMinute) * 60L } ?: 3600L)
        }

        val run = SessionRunEntity(
            occurrenceId = occurrenceId,
            sessionId = session?.id,
            courseId = course?.id,
            courseCode = course?.code ?: "GEN",
            courseTitle = course?.title ?: "General Reading",
            sessionTitle = session?.title ?: "Ad-hoc reading",
            dateEpochDay = TimeUtils.todayEpochDay(),
            startedAt = System.currentTimeMillis(),
            allocatedSeconds = allocated + extraSeconds,
            outcome = RunOutcome.IN_PROGRESS,
            status = "ACTIVE"
        )
        val id = runDao.insert(run)
        if (occurrence != null) {
            occurrenceDao.setState(occurrence.id, OccurrenceState.SCHEDULED.name, id)
        }
        log("RUN_STARTED", "${run.courseCode} allocated=${run.allocatedSeconds}s")
        return id
    }

    /** Persists elapsed seconds so a process death never loses reading time. */
    suspend fun checkpointRun(runId: Long, actualSeconds: Long, pausedSeconds: Long) {
        runDao.getById(runId)?.let {
            runDao.update(it.copy(actualSeconds = actualSeconds, pausedSeconds = pausedSeconds))
        }
    }

    suspend fun extendRun(runId: Long, extraSeconds: Long) {
        runDao.getById(runId)?.let {
            runDao.update(it.copy(allocatedSeconds = it.allocatedSeconds + extraSeconds, extendedSeconds = it.extendedSeconds + extraSeconds))
        }
        log("RUN_EXTENDED", "run=$runId +${extraSeconds}s")
    }

    /**
     * Ends a run. Topics are deliberately untouched here — an expired timer must never
     * complete topics on the student's behalf.
     */
    suspend fun finishRun(runId: Long, outcome: RunOutcome, note: String = "") {
        val run = runDao.getById(runId) ?: return
        finishRunInternal(run, outcome, note)
    }

    private suspend fun finishRunInternal(run: SessionRunEntity, outcome: RunOutcome, note: String) {
        val endedAt = System.currentTimeMillis()
        runDao.finish(run.id, outcome.name, endedAt, run.actualSeconds, run.pausedSeconds, note)

        val occurrence = run.occurrenceId?.let { occurrenceDao.getById(it) }
        if (occurrence != null) {
            val occState = when (outcome) {
                RunOutcome.COMPLETED, RunOutcome.EXTENDED -> OccurrenceState.DONE
                RunOutcome.STOPPED_EARLY, RunOutcome.INTERRUPTED -> OccurrenceState.INTERRUPTED
                RunOutcome.MISSED -> OccurrenceState.MISSED
                RunOutcome.IN_PROGRESS -> OccurrenceState.SCHEDULED
            }
            occurrenceDao.setState(occurrence.id, occState.name, run.id)
        }
        log("RUN_FINISHED", "run=${run.id} outcome=$outcome actual=${run.actualSeconds}s")
    }

    /** Called by SessionSummary after the student reviews topics. */
    suspend fun updateRunCounts(runId: Long, completed: Int, pending: Int) {
        runDao.getById(runId)?.let {
            runDao.update(it.copy(topicsCompletedCount = completed, topicsPendingCount = pending))
        }
    }

    /** Re-schedules every unfinished topic of a finished run to a new date + time window. */
    suspend fun rescheduleUnfinishedTopics(
        topicIds: List<Long>,
        dateEpochDay: Long,
        startMinute: Int,
        endMinute: Int,
        courseId: Long?,
        title: String
    ): Long {
        require(topicIds.isNotEmpty()) { "No topics to reschedule" }
        val sessionId = sessionDao.insert(
            ReadingSessionEntity(
                courseId = courseId,
                title = title,
                startMinute = startMinute,
                endMinute = endMinute,
                isRecurring = false,
                specificDateEpochDay = dateEpochDay,
                daysOfWeekMask = 0
            )
        )
        assignTopicsToSession(sessionId, topicIds)
        materialiseRange(dateEpochDay, dateEpochDay)
        onScheduleChanged?.invoke()
        log("TOPICS_RESCHEDULED", "${topicIds.size} topics → ${LocalDate.ofEpochDay(dateEpochDay)}")
        return sessionId
    }

    // ───────────────────────── Alarms ─────────────────────────

    fun observePendingAlarms(): Flow<List<AlarmRecordEntity>> = alarmDao.observePending()
    suspend fun getPendingAlarms(): List<AlarmRecordEntity> = alarmDao.getPending()

    suspend fun recordAlarm(occurrenceId: Long?, sessionId: Long?, kind: AlarmKind, triggerAt: Long, requestCode: Int) {
        alarmDao.upsert(
            AlarmRecordEntity(
                occurrenceId = occurrenceId,
                sessionId = sessionId,
                kind = kind,
                triggerAtMillis = triggerAt,
                requestCode = requestCode
            )
        )
    }

    suspend fun cancelAlarmsForOccurrence(occurrenceId: Long) = alarmDao.cancelForOccurrence(occurrenceId)

    suspend fun markAlarmFired(requestCode: Int, kind: AlarmKind) =
        alarmDao.markFired(requestCode, kind.name, System.currentTimeMillis())

    suspend fun purgeOldAlarms() =
        alarmDao.purgeOld(System.currentTimeMillis() - 7L * 24 * 3600 * 1000)

    /** Occurrences in the near future that still need alarms armed. */
    suspend fun occurrencesNeedingAlarms(from: Long, to: Long): List<SessionOccurrenceEntity> =
        occurrenceDao.getBetween(from, to).filter { it.state == OccurrenceState.SCHEDULED }

    // ───────────────────────── Diagnostics ─────────────────────────

    fun observeEvents(): Flow<List<AppEventEntity>> = eventDao.observeRecent()

    suspend fun log(type: String, message: String) {
        runCatching { eventDao.insert(AppEventEntity(type = type, message = message)) }
    }

    fun observeTopicProgress(): Flow<List<Pair<Long, Int>>> =
        topicDao.observeAll().map { topics ->
            topics.groupBy { it.courseId }.map { (courseId, list) ->
                courseId to list.count { it.status == TopicStatus.COMPLETED }
            }
        }

    companion object {
        /** How many days ahead occurrences (and therefore alarms) are kept armed. */
        const val MaterialiseAheadDays = 30L

        /** Guards against a run "left open" for an implausible amount of time. */
        const val MaxPlausibleRunMinutes = 12 * 60L
    }
}
