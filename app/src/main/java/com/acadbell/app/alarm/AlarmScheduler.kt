package com.acadbell.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.acadbell.app.data.db.SessionOccurrenceEntity
import com.acadbell.app.domain.AlarmKind
import com.acadbell.app.domain.TimeUtils
import com.acadbell.app.repository.AcadBellRepository

/**
 * Owns every interaction with AlarmManager.
 *
 * Reliability decisions
 * ---------------------------------------------------------------------------
 * 1. **Exact alarms.** START alarms must fire on the minute, so we use
 *    `setExactAndAllowWhileIdle`. On Android 12+ this requires the
 *    SCHEDULE_EXACT_ALARM permission; we check `canScheduleExactAlarms()` every time and fall
 *    back to `setAndAllowWhileIdle` (inexact but never lost) rather than dropping the alarm.
 * 2. **Deterministic request codes.** requestCode = hash(occurrenceId, kind). Re-arming the same
 *    occurrence therefore REPLACES the existing PendingIntent (`FLAG_UPDATE_CURRENT`) instead of
 *    creating a duplicate. This is the duplicate-alarm prevention mechanism.
 * 3. **Idempotent arming.** [armAll] clears the whole alarm namespace first
 *    (see [AlarmReceiver.ACTION_RESCHEDULE_ALL]) and then arms from the database — so the DB,
 *    not the OS, is the source of truth.
 * 4. **Recovery.** BootCompletedReceiver + TimeChangeReceiver + a periodic WorkManager job all
 *    call [armAll].
 */
class AlarmScheduler(
    private val context: Context,
    private val repository: AcadBellRepository
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true

    /** Stable, collision-resistant request code for an (occurrence, kind) pair. */
    fun requestCodeFor(occurrenceId: Long, kind: AlarmKind): Int {
        val base = (occurrenceId * 10L + kind.ordinal).toInt()
        return base and 0x7FFFFFFF
    }

    private fun pendingIntentFor(occurrenceId: Long, kind: AlarmKind, flags: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM
            putExtra(AlarmReceiver.EXTRA_OCCURRENCE_ID, occurrenceId)
            putExtra(AlarmReceiver.EXTRA_KIND, kind.name)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(occurrenceId, kind),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Arms the START alarm for one occurrence. Returns true when it was actually scheduled. */
    suspend fun armStart(occurrence: SessionOccurrenceEntity): Boolean {
        val triggerAt = occurrence.plannedStartMillis
        if (triggerAt <= System.currentTimeMillis()) return false
        return schedule(occurrence.id, AlarmKind.START, triggerAt)
    }

    /** Arms the END alarm for one occurrence (used for pre-arming and after "extend"). */
    suspend fun armEnd(occurrence: SessionOccurrenceEntity, triggerAtMillis: Long = occurrence.plannedEndMillis): Boolean {
        if (triggerAtMillis <= System.currentTimeMillis()) return false
        return schedule(occurrence.id, AlarmKind.END, triggerAtMillis)
    }

    /** Used by the service for ad-hoc sessions that have no occurrence row. */
    suspend fun armAdHocEnd(occurrenceId: Long, triggerAtMillis: Long): Boolean =
        schedule(occurrenceId, AlarmKind.END, triggerAtMillis)

    fun armSnooze(occurrenceId: Long, triggerAtMillis: Long) {
        scheduleBlocking(occurrenceId, AlarmKind.SNOOZE, triggerAtMillis)
    }

    private suspend fun schedule(occurrenceId: Long, kind: AlarmKind, triggerAtMillis: Long): Boolean {
        val requestCode = requestCodeFor(occurrenceId, kind)
        val pi = pendingIntentFor(occurrenceId, kind, PendingIntent.FLAG_UPDATE_CURRENT)
        return try {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
            repository.recordAlarm(occurrenceId, null, kind, triggerAtMillis, requestCode)
            true
        } catch (se: SecurityException) {
            // Permission revoked between the check and the call — degrade gracefully.
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                repository.recordAlarm(occurrenceId, null, kind, triggerAtMillis, requestCode)
            }
            false
        } catch (t: Throwable) {
            repository.log("ALARM_ERROR", "${kind.name} ${t.message}")
            false
        }
    }

    private fun scheduleBlocking(occurrenceId: Long, kind: AlarmKind, triggerAtMillis: Long) {
        val pi = pendingIntentFor(occurrenceId, kind, PendingIntent.FLAG_UPDATE_CURRENT)
        runCatching {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        }
    }

    fun cancel(occurrenceId: Long, kind: AlarmKind) {
        val pi = pendingIntentFor(occurrenceId, kind, PendingIntent.FLAG_UPDATE_CURRENT)
        alarmManager.cancel(pi)
        pi.cancel()
    }

    /**
     * Clears every ACADBELL alarm known to the OS for occurrences we have on record, then
     * re-arms from the database. Called on boot, timezone change, clock change and after any
     * schedule edit.
     */
    suspend fun armAll() {
        val now = System.currentTimeMillis()
        val horizon = now + AcadBellRepository.MaterialiseAheadDays * 24L * 3600 * 1000

        // Cancel stale alarms first (recovery path: DB is authoritative).
        repository.getPendingAlarms().forEach { record ->
            record.occurrenceId?.let { cancel(it, record.kind) }
        }

        val occurrences = repository.occurrencesNeedingAlarms(now, horizon)
        var armed = 0
        occurrences.forEach { occ ->
            if (armStart(occ)) armed++
            // The end alarm only matters for the next ~48h; arming further ahead wastes slots.
            if (occ.plannedEndMillis < now + 48L * 3600 * 1000) {
                armEnd(occ)
            }
        }
        repository.log("ALARMS_ARMED", "$armed start alarms across ${occurrences.size} occurrences")
    }

    /** Diagnostics for the Settings screen: how many alarms the OS currently holds. */
    suspend fun armedAlarmSummary(): String {
        val pending = repository.getPendingAlarms()
        val next = pending.minByOrNull { it.triggerAtMillis }
        return buildString {
            append("${pending.size} alarm(s) armed")
            next?.let { append(" · next ${TimeUtils.formatDateTime(it.triggerAtMillis)}") }
            append(if (canScheduleExactAlarms()) " · exact ✔" else " · inexact (permission needed)")
        }
    }
}
