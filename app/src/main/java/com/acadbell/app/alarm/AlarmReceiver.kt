package com.acadbell.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.acadbell.app.di.ServiceLocator
import com.acadbell.app.domain.AlarmKind
import com.acadbell.app.domain.OccurrenceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives START / END / SNOOZE alarms. Runs even when the app was swiped away because
 * AlarmManager delivers to the manifest-declared receiver and wakes the process.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext

        if (intent.action == ACTION_RESCHEDULE_ALL) {
            rescheduleAll(appContext)
            return
        }
        if (intent.action != ACTION_ALARM) return

        val occurrenceId = intent.getLongExtra(EXTRA_OCCURRENCE_ID, -1L)
        val kind = runCatching { AlarmKind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: "START") }
            .getOrDefault(AlarmKind.START)
        if (occurrenceId <= 0L) return

        val pending = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                handleAlarm(appContext, occurrenceId, kind)
            } catch (t: Throwable) {
                ServiceLocator.repository(appContext).log("ALARM_FAILURE", "${kind.name}: ${t.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleAlarm(context: Context, occurrenceId: Long, kind: AlarmKind) {
        val repo = ServiceLocator.repository(context)
        val alarmScheduler = ServiceLocator.alarmScheduler(context)
        val notifications = ServiceLocator.notifications(context)

        val occurrence = repo.getOccurrence(occurrenceId) ?: return
        val session = repo.getSession(occurrence.sessionId)
        val course = session?.courseId?.let { repo.getCourse(it) }

        val title = session?.title ?: "Reading session"
        val courseLabel = course?.let { "${it.code} · ${it.title}" } ?: "General reading"
        val topics = session?.let { repo.getTopics(repo.topicIdsForSession(it.id)) } ?: emptyList()

        when (kind) {
            AlarmKind.START, AlarmKind.SNOOZE -> {
                // An alarm for a session that was deleted/edited away must not disturb the student.
                if (session == null || !session.enabled) return

                repo.markAlarmFired(alarmScheduler.requestCodeFor(occurrenceId, kind), kind)
                repo.log("ALARM_START_FIRED", "occurrence=$occurrenceId")

                notifications.showStartAlarm(
                    occurrenceId = occurrenceId,
                    sessionId = session.id,
                    title = title,
                    courseLabel = courseLabel,
                    topicCount = topics.size,
                    durationMinutes = ((occurrence.plannedEndMillis - occurrence.plannedStartMillis) / 60000).toInt(),
                    sound = session.alarmSound,
                    vibrate = session.vibration,
                    snoozeMinutes = session.snoozeMinutes
                )
            }

            AlarmKind.END -> {
                repo.markAlarmFired(alarmScheduler.requestCodeFor(occurrenceId, kind), kind)
                repo.log("ALARM_END_FIRED", "occurrence=$occurrenceId")

                // If a run is active for this occurrence the service finishes it; otherwise just notify.
                val activeRun = repo.getActiveRun()
                if (activeRun != null && activeRun.occurrenceId == occurrenceId) {
                    ReadingSessionService.finishFromAlarm(context, activeRun.id)
                } else {
                    notifications.showEndAlarm(
                        occurrenceId = occurrenceId,
                        title = title,
                        courseLabel = courseLabel,
                        sound = session?.alarmSound ?: "CLASSIC_BELL",
                        vibrate = session?.vibration ?: true,
                        snoozeMinutes = session?.snoozeMinutes ?: 5
                    )
                    if (occurrence.state == OccurrenceState.SCHEDULED) {
                        repo.log("END_WITHOUT_RUN", "occurrence=$occurrenceId ended with no active run")
                    }
                }
            }
        }
    }

    private fun rescheduleAll(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ServiceLocator.alarmScheduler(context).armAll()
            } catch (t: Throwable) {
                ServiceLocator.repository(context).log("RESCHEDULE_FAILURE", t.message ?: "unknown")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "com.acadbell.app.ACTION_ALARM"
        const val ACTION_RESCHEDULE_ALL = "com.acadbell.app.ACTION_RESCHEDULE_ALL"
        const val EXTRA_OCCURRENCE_ID = "extra_occurrence_id"
        const val EXTRA_KIND = "extra_kind"
    }
}
