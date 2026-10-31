package com.acadbell.app

import android.app.Application
import androidx.work.Configuration
import com.acadbell.app.alarm.MaintenanceWorker
import com.acadbell.app.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AcadBellApp : Application(), Configuration.Provider {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // WorkManager is created manually so we can inject a worker factory-free config
        // and avoid double initialisation from the startup provider.
        MaintenanceWorker.schedulePeriodic(this)

        appScope.launch {
            runCatching {
                val repo = ServiceLocator.repository(this@AcadBellApp)
                // Self-healing on every cold start: fill the occurrence window, resolve
                // overdue occurrences, re-arm any missing alarms.
                repo.materialiseRange(
                    com.acadbell.app.domain.TimeUtils.todayEpochDay(),
                    com.acadbell.app.domain.TimeUtils.todayEpochDay() +
                        com.acadbell.app.repository.AcadBellRepository.MaterialiseAheadDays
                )
                repo.reconcileState()
                ServiceLocator.alarmScheduler(this@AcadBellApp).armAll()
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.WARN)
            .build()
}
