package com.acadbell.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.acadbell.app.di.ServiceLocator
import com.acadbell.app.domain.RunOutcome
import com.acadbell.app.domain.TimeUtils
import com.acadbell.app.repository.AcadBellRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-arms every alarm after a reboot, app update or full device restore.
 * AlarmManager forgets ALL alarms on reboot — this receiver is the recovery path.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ServiceLocator.repository(context)
                repo.materialiseRange(
                    TimeUtils.todayEpochDay(),
                    TimeUtils.todayEpochDay() + AcadBellRepositoryAhead
                )
                repo.reconcileState()
                ServiceLocator.alarmScheduler(context).armAll()
                repo.log("BOOT_RESCHEDULED", "alarms re-armed after $action")
            } catch (t: Throwable) {
                ServiceLocator.repository(context).log("BOOT_FAILURE", t.message ?: "unknown")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val AcadBellRepositoryAhead = 30L
    }
}

/**
 * Handles `TIMEZONE_CHANGED`, `TIME_SET` and `DATE_CHANGED`.
 *
 * A session stored as "18:00 local" must still ring at 18:00 local after the student flies
 * to another timezone or changes the clock manually. Because occurrences store absolute millis,
 * the correct response is to re-materialise them from the templates and re-arm.
 */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED -> Unit
            else -> return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ServiceLocator.repository(context)
                val today = TimeUtils.todayEpochDay()
                // Rebuild the next 30 days of occurrences against the new zone/clock.
                repo.materialiseRange(today, today + 30)
                repo.reconcileState()
                ServiceLocator.alarmScheduler(context).armAll()
                repo.log("TIME_CHANGE_RECOVERED", intent.action ?: "")
            } catch (t: Throwable) {
                ServiceLocator.repository(context).log("TIME_CHANGE_FAILURE", t.message ?: "unknown")
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Closes a session that was interrupted by a process kill or an OEM task cleaner.
 * Runs on app launch (via MainActivity) as well as after boot.
 */
object InterruptedSessionRecovery {

    suspend fun run(context: Context) {
        val repo = ServiceLocator.repository(context)
        val active = repo.getActiveRun() ?: return
        val now = System.currentTimeMillis()
        val ageMinutes = (now - active.startedAt) / 60_000

        // If the service is genuinely running, leave the session alone.
        if (ReadingSessionService.state.value.runId == active.id &&
            ReadingSessionService.state.value.isRunning
        ) return

        if (ageMinutes > AcadBellRepository.MaxPlausibleRunMinutes) {
            repo.finishRun(active.id, RunOutcome.INTERRUPTED, "Recovered after app restart")
            ServiceLocator.notifications(context).showSimple(
                "Session recovered",
                "\"${active.sessionTitle}\" was closed as interrupted. ${active.actualSeconds / 60} min of reading was saved."
            )
            repo.log("RUN_RECOVERED", "run=${active.id} age=${ageMinutes}m")
        }
    }
}
