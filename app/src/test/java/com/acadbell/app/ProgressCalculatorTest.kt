package com.acadbell.app

import com.acadbell.app.data.db.SessionRunEntity
import com.acadbell.app.data.db.TopicEntity
import com.acadbell.app.domain.RunOutcome
import com.acadbell.app.domain.TopicStatus
import com.acadbell.app.domain.stats.ProgressCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies §5H: statistics come from ACTUAL saved records, never from scheduled time.
 */
class ProgressCalculatorTest {

    private fun run(
        id: Long = 0,
        day: Long = 100,
        actual: Long = 0,
        allocated: Long = 3600,
        outcome: RunOutcome = RunOutcome.COMPLETED,
        status: String = "FINISHED",
        courseId: Long = 1
    ) = SessionRunEntity(
        id = id,
        courseId = courseId,
        courseCode = "CSC301",
        courseTitle = "Operating Systems",
        sessionTitle = "Evening reading",
        dateEpochDay = day,
        startedAt = 0,
        endedAt = 1000,
        allocatedSeconds = allocated,
        actualSeconds = actual,
        outcome = outcome,
        status = status
    )

    private fun topic(id: Long, courseId: Long = 1, completed: Boolean) = TopicEntity(
        id = id,
        courseId = courseId,
        title = "Topic $id",
        status = if (completed) TopicStatus.COMPLETED else TopicStatus.PENDING
    )

    @Test
    fun `total reading time uses actual seconds not allocated`() {
        val runs = listOf(
            run(id = 1, actual = 1800, allocated = 3600),
            run(id = 2, actual = 900, allocated = 3600)
        )
        val snapshot = ProgressCalculator.snapshot(runs, emptyList(), scheduledToday = 0)
        assertEquals(2700L, snapshot.totalActualSeconds)
    }

    @Test
    fun `in progress runs are excluded from totals`() {
        val runs = listOf(
            run(id = 1, actual = 600),
            run(id = 2, actual = 5000, status = "ACTIVE", outcome = RunOutcome.IN_PROGRESS)
        )
        val snapshot = ProgressCalculator.snapshot(runs, emptyList(), scheduledToday = 0)
        assertEquals(600L, snapshot.totalActualSeconds)
    }

    @Test
    fun `interrupted sessions are counted separately from completed`() {
        val runs = listOf(
            run(id = 1, actual = 1200, outcome = RunOutcome.COMPLETED),
            run(id = 2, actual = 300, outcome = RunOutcome.STOPPED_EARLY),
            run(id = 3, actual = 200, outcome = RunOutcome.INTERRUPTED)
        )
        val snapshot = ProgressCalculator.snapshot(runs, emptyList(), scheduledToday = 0)
        assertEquals(2, snapshot.interruptedSessions)
        assertEquals(3, snapshot.completedSessions) // all three had reading time
    }

    @Test
    fun `topic counts split pending and completed`() {
        val topics = listOf(
            topic(1, completed = true),
            topic(2, completed = true),
            topic(3, completed = false)
        )
        val snapshot = ProgressCalculator.snapshot(emptyList(), topics, scheduledToday = 0)
        assertEquals(2, snapshot.topicsCompleted)
        assertEquals(1, snapshot.topicsRemaining)
    }

    @Test
    fun `streak counts backwards from today`() {
        val days = listOf(100L, 99L, 98L, 96L)
        assertEquals(3, ProgressCalculator.streak(days, todayEpochDay = 100))
    }

    @Test
    fun `streak survives a day that has not been read yet`() {
        // Today has no session yet, but yesterday and the day before do.
        val days = listOf(99L, 98L)
        assertEquals(2, ProgressCalculator.streak(days, todayEpochDay = 100))
    }

    @Test
    fun `consistency counts performed against missed`() {
        val runs = listOf(
            run(id = 1, day = 100, outcome = RunOutcome.COMPLETED),
            run(id = 2, day = 99, outcome = RunOutcome.STOPPED_EARLY),
            run(id = 3, day = 98, outcome = RunOutcome.MISSED, actual = 0),
            run(id = 4, day = 97, outcome = RunOutcome.MISSED, actual = 0)
        )
        assertEquals(50, ProgressCalculator.consistency(runs, todayEpochDay = 100))
    }

    @Test
    fun `daily bars only include reading inside the window`() {
        val runs = listOf(
            run(id = 1, day = 100, actual = 1800),
            run(id = 2, day = 99, actual = 3600),
            run(id = 3, day = 50, actual = 9999) // outside 7-day window
        )
        val bars = ProgressCalculator.dailyBars(runs, todayEpochDay = 100, days = 7)
        assertEquals(7, bars.size)
        assertEquals(30, bars.last().minutes)
        assertEquals(60, bars[5].minutes)
        assertEquals(0, bars[0].minutes)
    }

    @Test
    fun `course completion percentage`() {
        val courses = listOf(
            com.acadbell.app.data.db.CourseEntity(id = 1, code = "CSC301", title = "OS")
        )
        val topics = listOf(
            topic(1, completed = true),
            topic(2, completed = false),
            topic(3, completed = false),
            topic(4, completed = false)
        )
        val progress = ProgressCalculator.courseProgress(courses, topics, listOf(run(actual = 600)))
        assertEquals(25, progress.first().completionPercent)
        assertEquals(600L, progress.first().actualSeconds)
    }

    @Test
    fun `empty data produces zeroes without dividing by zero`() {
        val snapshot = ProgressCalculator.snapshot(emptyList(), emptyList(), scheduledToday = 0)
        assertEquals(0L, snapshot.totalActualSeconds)
        assertEquals(0, snapshot.consistencyPercent)
        assertEquals(0, snapshot.activeStudyDayStreak)
        assertTrue(ProgressCalculator.courseProgress(emptyList(), emptyList(), emptyList()).isEmpty())
    }
}
