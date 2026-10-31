package com.acadbell.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE archived = 0 ORDER BY code COLLATE NOCASE ASC")
    fun observeCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE id = :id")
    fun observeCourse(id: Long): Flow<CourseEntity?>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getCourse(id: Long): CourseEntity?

    @Query("SELECT * FROM courses ORDER BY code COLLATE NOCASE ASC")
    suspend fun getAll(): List<CourseEntity>

    @Query("SELECT COUNT(*) FROM courses WHERE archived = 0")
    fun observeCourseCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(course: CourseEntity): Long

    @Update
    suspend fun update(course: CourseEntity)

    @Delete
    suspend fun delete(course: CourseEntity)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(courses: List<CourseEntity>): List<Long>
}

@Dao
interface TopicDao {
    @Query("SELECT * FROM topics WHERE courseId = :courseId ORDER BY sortOrder ASC, id ASC")
    fun observeForCourse(courseId: Long): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE status = 'PENDING' ORDER BY courseId ASC, sortOrder ASC, id ASC")
    fun observePending(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE id = :id")
    suspend fun getTopic(id: Long): TopicEntity?

    @Query("SELECT * FROM topics WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<TopicEntity>

    @Query("SELECT COUNT(*) FROM topics WHERE courseId = :courseId")
    fun observeTopicCount(courseId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM topics WHERE courseId = :courseId AND status = 'COMPLETED'")
    fun observeCompletedCount(courseId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(topic: TopicEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(topics: List<TopicEntity>): List<Long>

    @Update
    suspend fun update(topic: TopicEntity)

    @Query("UPDATE topics SET status = :status, completedAt = :completedAt WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, completedAt: Long?)

    @Delete
    suspend fun delete(topic: TopicEntity)

    @Query("DELETE FROM topics WHERE courseId = :courseId")
    suspend fun deleteForCourse(courseId: Long)
}

@Dao
interface ReadingSessionDao {
    @Query("SELECT * FROM reading_sessions WHERE enabled = 1 ORDER BY startMinute ASC")
    fun observeEnabled(): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions ORDER BY startMinute ASC")
    fun observeAll(): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions ORDER BY startMinute ASC")
    suspend fun getAll(): List<ReadingSessionEntity>

    @Query("SELECT * FROM reading_sessions WHERE id = :id")
    suspend fun getById(id: Long): ReadingSessionEntity?

    @Query("SELECT * FROM reading_sessions WHERE id = :id")
    fun observeById(id: Long): Flow<ReadingSessionEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: ReadingSessionEntity): Long

    @Update
    suspend fun update(session: ReadingSessionEntity)

    @Query("DELETE FROM reading_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM session_topics WHERE sessionId = :sessionId")
    suspend fun getTopicIds(sessionId: Long): List<SessionTopicCrossRef>

    @Query("SELECT sessionId FROM session_topics WHERE topicId = :topicId")
    suspend fun getSessionIdsForTopic(topicId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun assignTopics(refs: List<SessionTopicCrossRef>)

    @Query("DELETE FROM session_topics WHERE sessionId = :sessionId")
    suspend fun clearTopics(sessionId: Long)

    @Query("DELETE FROM session_topics WHERE topicId = :topicId")
    suspend fun unassignTopic(topicId: Long)
}

@Dao
interface OccurrenceDao {
    @Query("SELECT * FROM session_occurrences WHERE dateEpochDay = :epochDay ORDER BY plannedStartMillis ASC")
    fun observeForDay(epochDay: Long): Flow<List<SessionOccurrenceEntity>>

    @Query("SELECT * FROM session_occurrences WHERE dateEpochDay BETWEEN :fromDay AND :toDay ORDER BY plannedStartMillis ASC")
    fun observeBetween(fromDay: Long, toDay: Long): Flow<List<SessionOccurrenceEntity>>

    @Query("SELECT * FROM session_occurrences WHERE dateEpochDay BETWEEN :fromDay AND :toDay ORDER BY plannedStartMillis ASC")
    suspend fun getBetween(fromDay: Long, toDay: Long): List<SessionOccurrenceEntity>

    @Query("SELECT * FROM session_occurrences WHERE sessionId = :sessionId AND dateEpochDay = :epochDay LIMIT 1")
    suspend fun find(sessionId: Long, epochDay: Long): SessionOccurrenceEntity?

    @Query("SELECT * FROM session_occurrences WHERE id = :id")
    suspend fun getById(id: Long): SessionOccurrenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(occurrence: SessionOccurrenceEntity): Long

    @Update
    suspend fun update(occurrence: SessionOccurrenceEntity)

    @Query("UPDATE session_occurrences SET state = :state, runId = :runId WHERE id = :id")
    suspend fun setState(id: Long, state: String, runId: Long?)

    @Query("SELECT * FROM session_occurrences WHERE state = 'SCHEDULED' AND plannedEndMillis < :now")
    suspend fun findOverdue(now: Long): List<SessionOccurrenceEntity>

    @Query("SELECT * FROM session_occurrences WHERE state = 'SCHEDULED' AND plannedStartMillis >= :now ORDER BY plannedStartMillis ASC")
    suspend fun findUpcoming(now: Long): List<SessionOccurrenceEntity>

    @Query("DELETE FROM session_occurrences WHERE sessionId = :sessionId AND dateEpochDay >= :fromDay")
    suspend fun deleteFutureForSession(sessionId: Long, fromDay: Long)

    @Query("DELETE FROM session_occurrences WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    @Query("DELETE FROM session_occurrences WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface SessionRunDao {
    @Query("SELECT * FROM session_runs WHERE status = 'ACTIVE' ORDER BY startedAt DESC")
    fun observeActive(): Flow<List<SessionRunEntity>>

    @Query("SELECT * FROM session_runs WHERE status = 'ACTIVE' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveRun(): SessionRunEntity?

    @Query("SELECT * FROM session_runs WHERE id = :id")
    suspend fun getById(id: Long): SessionRunEntity?

    @Query("SELECT * FROM session_runs WHERE id = :id")
    fun observeById(id: Long): Flow<SessionRunEntity?>

    @Query("SELECT * FROM session_runs WHERE status = 'FINISHED' ORDER BY startedAt DESC")
    fun observeFinished(): Flow<List<SessionRunEntity>>

    @Query("SELECT * FROM session_runs WHERE status = 'FINISHED' ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SessionRunEntity>>

    @Query("SELECT * FROM session_runs WHERE status = 'FINISHED' AND dateEpochDay BETWEEN :fromDay AND :toDay ORDER BY startedAt DESC")
    fun observeFinishedBetween(fromDay: Long, toDay: Long): Flow<List<SessionRunEntity>>

    @Query("SELECT * FROM session_runs ORDER BY startedAt DESC")
    suspend fun getAll(): List<SessionRunEntity>

    @Query("SELECT COALESCE(SUM(actualSeconds), 0) FROM session_runs WHERE status = 'FINISHED'")
    fun observeTotalActualSeconds(): Flow<Long>

    @Query("SELECT COUNT(*) FROM session_runs WHERE status = 'FINISHED'")
    fun observeFinishedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM session_runs WHERE status = 'FINISHED' AND outcome IN ('STOPPED_EARLY','INTERRUPTED')")
    fun observeInterruptedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM session_occurrences WHERE state = 'MISSED'")
    fun observeMissedCount(): Flow<Int>

    @Insert
    suspend fun insert(run: SessionRunEntity): Long

    @Update
    suspend fun update(run: SessionRunEntity)

    @Query("UPDATE session_runs SET status = 'FINISHED', outcome = :outcome, endedAt = :endedAt, actualSeconds = :actual, pausedSeconds = :paused, note = :note WHERE id = :id")
    suspend fun finish(id: Long, outcome: String, endedAt: Long, actual: Long, paused: Long, note: String)

    @Query("DELETE FROM session_runs WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarm_records WHERE cancelled = 0 ORDER BY triggerAtMillis ASC")
    fun observePending(): Flow<List<AlarmRecordEntity>>

    @Query("SELECT * FROM alarm_records WHERE cancelled = 0 ORDER BY triggerAtMillis ASC")
    suspend fun getPending(): List<AlarmRecordEntity>

    @Query("SELECT * FROM alarm_records WHERE occurrenceId = :occurrenceId")
    suspend fun getForOccurrence(occurrenceId: Long): List<AlarmRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: AlarmRecordEntity): Long

    @Query("UPDATE alarm_records SET cancelled = 1 WHERE occurrenceId = :occurrenceId")
    suspend fun cancelForOccurrence(occurrenceId: Long)

    @Query("UPDATE alarm_records SET firedAt = :firedAt WHERE requestCode = :requestCode AND kind = :kind")
    suspend fun markFired(requestCode: Int, kind: String, firedAt: Long)

    @Query("DELETE FROM alarm_records WHERE cancelled = 1 AND triggerAtMillis < :before")
    suspend fun purgeOld(before: Long)
}

@Dao
interface AppEventDao {
    @Query("SELECT * FROM app_events ORDER BY createdAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<AppEventEntity>>

    @Insert
    suspend fun insert(event: AppEventEntity)

    @Query("DELETE FROM app_events")
    suspend fun clear()

    @Query("DELETE FROM app_events WHERE createdAt < :before")
    suspend fun purgeOlderThan(before: Long)
}
