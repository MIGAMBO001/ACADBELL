package com.acadbell.app.alarm

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.acadbell.app.di.ServiceLocator
import com.acadbell.app.domain.TimeUtils
import com.acadbell.app.repository.AcadBellRepository
import java.util.concurrent.TimeUnit

/**
 * Background upkeep that keeps ACADBELL trustworthy over weeks of use:
 *  • extends the materialised occurrence window (so alarms always exist ahead)
 *  • marks overdue occurrences MISSED
 *  • re-arms alarms (safety net if an OEM cleared them)
 *  • purges stale alarm rows / old audit entries
 *
 * Runs daily. WorkManager is used (not AlarmManager) because this work is deferrable —
 * it must never compete with the student's actual reading alarms, which use exact scheduling.
 */
class MaintenanceWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = ServiceLocator.repository(applicationContext)
        return try {
            val today = TimeUtils.todayEpochDay()
            repo.materialiseRange(today, today + AcadBellRepository.MaterialiseAheadDays)
            repo.reconcileState()
            ServiceLocator.alarmScheduler(applicationContext).armAll()
            repo.purgeOldAlarms()
            repo.log("MAINTENANCE", "daily upkeep complete")
            Result.success()
        } catch (t: Throwable) {
            repo.log("MAINTENANCE_FAILED", t.message ?: "unknown")
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_NAME = "acadbell_daily_maintenance"
        private const val ONE_SHOT_NAME = "acadbell_immediate_maintenance"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Called right after install/onboarding so the first day works even before midnight. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<MaintenanceWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
