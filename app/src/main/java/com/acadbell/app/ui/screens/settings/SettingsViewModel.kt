package com.acadbell.app.ui.screens.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acadbell.app.data.backup.BackupManager
import com.acadbell.app.data.backup.BackupSummary
import com.acadbell.app.di.ServiceLocator
import com.acadbell.app.domain.AlarmSoundChoice
import com.acadbell.app.domain.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionStatus(
    val notificationsGranted: Boolean = false,
    val exactAlarmsGranted: Boolean = false,
    val batteryOptimised: Boolean = false,
    val armedSummary: String = "",
    val nextAlarmLabel: String = ""
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.repository(app)
    private val prefs = ServiceLocator.preferences(app)
    private val scheduler = ServiceLocator.alarmScheduler(app)
    private val backupManager = BackupManager(app)

    val themeMode: StateFlow<String> = prefs.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "system")
    val defaultSound: StateFlow<String> = prefs.defaultSound.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmSoundChoice.CLASSIC_BELL.name)
    val defaultVibration: StateFlow<Boolean> = prefs.defaultVibration.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val defaultSnooze: StateFlow<Int> = prefs.defaultSnooze.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 5)
    val keepScreenOn: StateFlow<Boolean> = prefs.keepScreenOn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val events = repo.observeEvents()

    private val _permissions = MutableStateFlow(PermissionStatus())
    val permissions: StateFlow<PermissionStatus> = _permissions.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    fun refreshPermissions(context: android.content.Context) {
        val notifications = ServiceLocator.notifications(context)
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        val ignoring = pm.isIgnoringBatteryOptimizations(context.packageName)

        viewModelScope.launch {
            val armed = scheduler.armedAlarmSummary()
            val next = repo.getPendingAlarms().minByOrNull { it.triggerAtMillis }
            _permissions.value = PermissionStatus(
                notificationsGranted = notifications.canPostNotifications(),
                exactAlarmsGranted = scheduler.canScheduleExactAlarms(),
                batteryOptimised = ignoring,
                armedSummary = armed,
                nextAlarmLabel = next?.let { TimeUtils.formatDateTime(it.triggerAtMillis) } ?: "None scheduled"
            )
        }
    }

    fun setTheme(mode: String) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun setSound(sound: String) = viewModelScope.launch { prefs.setDefaultSound(sound) }
    fun setVibration(on: Boolean) = viewModelScope.launch { prefs.setDefaultVibration(on) }
    fun setSnooze(minutes: Int) = viewModelScope.launch { prefs.setDefaultSnooze(minutes) }
    fun setKeepScreenOn(on: Boolean) = viewModelScope.launch { prefs.setKeepScreenOn(on) }

    /** Fires a real alarm 60 seconds from now so the student can verify delivery end-to-end. */
    fun testAlarmNow() {
        viewModelScope.launch {
            val occurrence = repo.firstUpcomingOrTodayOccurrence()
            if (occurrence == null) {
                _message.value = "Create a reading session first, then test the alarm."
                return@launch
            }
            scheduler.armAdHocEnd(occurrence.id, System.currentTimeMillis() + 60_000)
            _message.value = "Test alarm scheduled for 60 seconds from now. Lock your screen to verify it fires."
        }
    }

    fun rescheduleAllAlarms() {
        viewModelScope.launch {
            scheduler.armAll()
            _message.value = "All alarms re-armed from the database."
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            backupManager.exportTo(uri)
                .onSuccess { _message.value = "Backup saved · ${it.describe()}" }
                .onFailure { _message.value = "Export failed: ${it.message}" }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            backupManager.importFrom(uri)
                .onSuccess { _message.value = "Restored · ${it.describe()}" }
                .onFailure { _message.value = "Restore failed: ${it.message}" }
        }
    }

    fun clearLogs() {
        viewModelScope.launch { repo.log("LOGS_CLEARED", "manual clear") }
    }
}
