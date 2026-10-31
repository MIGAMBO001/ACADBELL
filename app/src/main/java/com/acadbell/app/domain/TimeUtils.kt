package com.acadbell.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Every date/time helper ACADBELL needs, expressed in the device's current time zone.
 *
 * Design note: times-of-day are stored as *minutes since midnight* on the session template,
 * while concrete occurrences store absolute epoch millis. Occurrences are always recomputed
 * from the template against the CURRENT zone, so a timezone change or DST shift re-anchors
 * sessions to local wall-clock time instead of drifting.
 */
object TimeUtils {

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun todayEpochDay(): Long = LocalDate.now(zone()).toEpochDay()

    fun epochDayOf(date: LocalDate): Long = date.toEpochDay()

    fun dateOf(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    /** Local midnight of [epochDay] as epoch millis. */
    fun startOfDayMillis(epochDay: Long): Long =
        dateOf(epochDay).atStartOfDay(zone()).toInstant().toEpochMilli()

    /** Combines a date and a minute-of-day into absolute millis in the current zone. */
    fun millisAt(epochDay: Long, minuteOfDay: Int): Long =
        dateOf(epochDay)
            .atTime(LocalTime.of((minuteOfDay / 60).coerceIn(0, 23), (minuteOfDay % 60).coerceIn(0, 59)))
            .atZone(zone())
            .toInstant()
            .toEpochMilli()

    fun nowMinuteOfDay(): Int {
        val now = LocalTime.now(zone())
        return now.hour * 60 + now.minute
    }

    fun formatTime(minuteOfDay: Int): String {
        val h = (minuteOfDay / 60).coerceIn(0, 23)
        val m = (minuteOfDay % 60).coerceIn(0, 59)
        val t = LocalTime.of(h, m)
        return t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    }

    fun formatTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone()).toLocalTime()
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

    fun formatDateTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone())
            .format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault()))

    fun formatDateLong(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault()))

    fun formatDateShort(epochDay: Long): String =
        dateOf(epochDay).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

    fun formatRange(startMinute: Int, endMinute: Int): String =
        "${formatTime(startMinute)} – ${formatTime(endMinute)}"

    /** "2h 15m" / "45m" / "0m" — used everywhere durations are shown. */
    fun formatDuration(totalMinutes: Long): String {
        if (totalMinutes <= 0) return "0m"
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    fun formatDurationSeconds(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0)
        val h = safe / 3600
        val m = (safe % 3600) / 60
        val s = safe % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun formatHours(seconds: Long): String {
        val hours = seconds / 3600.0
        return if (hours >= 10) String.format(Locale.US, "%.1f", hours)
        else String.format(Locale.US, "%.1f", hours)
    }

    fun weekdayShort(day: DayOfWeek): String =
        day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    /** Monday-first bit index: Mon = bit 0 … Sun = bit 6. */
    fun bitIndexFor(day: DayOfWeek): Int = day.value - 1

    fun maskForDays(days: Set<DayOfWeek>): Int =
        days.fold(0) { acc, d -> acc or (1 shl bitIndexFor(d)) }

    fun daysFromMask(mask: Int): List<DayOfWeek> =
        DayOfWeek.entries.filter { mask and (1 shl bitIndexFor(it)) != 0 }

    fun maskContains(mask: Int, date: LocalDate): Boolean =
        mask and (1 shl bitIndexFor(date.dayOfWeek)) != 0

    fun maskLabel(mask: Int): String {
        val days = daysFromMask(mask)
        return when {
            days.isEmpty() -> "One-off"
            days.size == 7 -> "Every day"
            days.map { it.value }.sorted() == listOf(1, 2, 3, 4, 5) -> "Weekdays"
            days.map { it.value }.sorted() == listOf(6, 7) -> "Weekends"
            else -> days.joinToString(", ") { weekdayShort(it).take(3) }
        }
    }

    /** Localised "Monday" etc. */
    fun dayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, Locale.getDefault())

    fun startOfWeek(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

    fun currentDateTime(): LocalDateTime = LocalDateTime.now(zone())

    fun millisNow(): Long = System.currentTimeMillis()

    /** True when [endMillis] is in the past. */
    fun isPast(endMillis: Long, now: Long = System.currentTimeMillis()): Boolean = endMillis < now

    /**
     * How many minutes until [targetMillis]; negative when overdue.
     * Used for the "starts in 25 min" label on Home.
     */
    fun minutesUntil(targetMillis: Long, now: Long = System.currentTimeMillis()): Long =
        (targetMillis - now) / 60_000L
}
