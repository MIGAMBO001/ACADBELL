package com.acadbell.app

import com.acadbell.app.domain.TimeUtils
import com.acadbell.app.domain.topic.TopicParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

/**
 * Unit tests for the two pure-logic areas that most affect correctness:
 *  • Topic pasting (the app's core feature)
 *  • Time/recurrence maths (what makes alarms fire on the right day)
 */
class TopicParserTest {

    @Test
    fun `splits a numbered list`() {
        val raw = """
            1. Introduction to Operating Systems
            2. Process Management
            3. CPU Scheduling
        """.trimIndent()
        val result = TopicParser.parse(raw)
        assertEquals(3, result.topics.size)
        assertEquals("Introduction to Operating Systems", result.topics[0])
        assertEquals("CPU Scheduling", result.topics[2])
    }

    @Test
    fun `strips bullets roman numerals and letters`() {
        val raw = """
            • Deadlock
            iii. Memory Paging
            a) File Systems
            - I/O Management
        """.trimIndent()
        val result = TopicParser.parse(raw)
        assertEquals(listOf("Deadlock", "Memory Paging", "File Systems", "I/O Management"), result.topics)
    }

    @Test
    fun `removes duplicates case insensitively`() {
        val raw = "Deadlock\nDEADLOCK\ndeadlock"
        val result = TopicParser.parse(raw)
        assertEquals(1, result.topics.size)
        assertEquals(2, result.duplicatesRemoved)
    }

    @Test
    fun `strips page references`() {
        val result = TopicParser.parse("Process Scheduling (pp. 45-52)")
        assertEquals("Process Scheduling", result.topics.first())
    }

    @Test
    fun `splits semicolon separated topics`() {
        val result = TopicParser.parse("Mitosis; Meiosis; Osmosis")
        assertEquals(3, result.topics.size)
    }

    @Test
    fun `comma splitting only when it looks like a list`() {
        val list = TopicParser.parse("Cell structure, organelles, mitosis, meiosis", splitCommas = true)
        assertEquals(4, list.topics.size)

        val sentence = TopicParser.parse(
            "Explain in detail how the process scheduler decides which task runs next, and why",
            splitCommas = true
        )
        assertEquals(1, sentence.topics.size)
    }

    @Test
    fun `ignores blank lines and pure numbers`() {
        val raw = "\n\n42\n\nReal Topic\n\n"
        val result = TopicParser.parse(raw)
        assertEquals(listOf("Real Topic"), result.topics)
    }

    @Test
    fun `handles topic prefix and chapter prefix`() {
        val result = TopicParser.parse("Topic 1: Kinematics\nChapter 2: Dynamics")
        assertEquals(listOf("Kinematics", "Dynamics"), result.topics)
    }

    @Test
    fun `estimate minutes stays in sane bounds`() {
        assertTrue(TopicParser.estimateMinutes("Integration") in 5..45)
        assertTrue(TopicParser.estimateMinutes("A very long topic title describing several related concepts in depth") in 5..45)
    }
}

class TimeUtilsTest {

    @Test
    fun `minute of day maps to formatted time`() {
        assertEquals("6:00 PM", TimeUtils.formatTime(18 * 60))
        assertEquals("12:00 AM", TimeUtils.formatTime(0))
        assertEquals("12:30 PM", TimeUtils.formatTime(12 * 60 + 30))
    }

    @Test
    fun `duration formatting`() {
        assertEquals("0m", TimeUtils.formatDuration(0))
        assertEquals("45m", TimeUtils.formatDuration(45))
        assertEquals("1h 15m", TimeUtils.formatDuration(75))
        assertEquals("3h 0m", TimeUtils.formatDuration(180))
    }

    @Test
    fun `seconds formatting`() {
        assertEquals("00:00", TimeUtils.formatDurationSeconds(0))
        assertEquals("09:05", TimeUtils.formatDurationSeconds(545))
        assertEquals("1:00:00", TimeUtils.formatDurationSeconds(3600))
    }

    @Test
    fun `weekday mask round trip`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val mask = TimeUtils.maskForDays(days)
        assertEquals(days, TimeUtils.daysFromMask(mask).toSet())
        assertEquals("Mon, Wed, Fri", TimeUtils.maskLabel(mask))
    }

    @Test
    fun `weekday and weekend masks produce friendly labels`() {
        val weekdays = TimeUtils.maskForDays(
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        )
        assertEquals("Weekdays", TimeUtils.maskLabel(weekdays))
        assertEquals("Every day", TimeUtils.maskLabel(0b1111111))
    }

    @Test
    fun `millisAt anchors to local wall clock`() {
        val day = 20000L // arbitrary epoch day
        val millis = TimeUtils.millisAt(day, 18 * 60)
        val localTime = java.time.Instant.ofEpochMilli(millis)
            .atZone(TimeUtils.zone()).toLocalTime()
        assertEquals(18, localTime.hour)
        assertEquals(0, localTime.minute)
    }
}
