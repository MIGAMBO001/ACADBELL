package com.acadbell.app.di

import android.content.Context
import com.acadbell.app.alarm.AlarmScheduler
import com.acadbell.app.alarm.Notifications
import com.acadbell.app.data.db.AcadBellDatabase
import com.acadbell.app.data.prefs.PreferencesRepository
import com.acadbell.app.repository.AcadBellRepository

/**
 * Minimal manual dependency injection.
 *
 * Chosen over Hilt/Koin deliberately: it is one small file, has zero build-plugin risk,
 * works in every Android process (activity, receiver, service, worker) and keeps the APK lean.
 * Everything is lazily created against the application context.
 */
object ServiceLocator {

    @Volatile private var database: AcadBellDatabase? = null
    @Volatile private var repositoryInstance: AcadBellRepository? = null
    @Volatile private var alarmSchedulerInstance: AlarmScheduler? = null
    @Volatile private var notificationsInstance: Notifications? = null
    @Volatile private var preferencesInstance: PreferencesRepository? = null

    fun database(context: Context): AcadBellDatabase =
        database ?: synchronized(this) {
            database ?: AcadBellDatabase.get(context.applicationContext).also { database = it }
        }

    fun repository(context: Context): AcadBellRepository =
        repositoryInstance ?: synchronized(this) {
            repositoryInstance ?: AcadBellRepository(database(context)).also {
                it.onScheduleChanged = { alarmScheduler(context).armAll() }
                repositoryInstance = it
            }
        }

    fun alarmScheduler(context: Context): AlarmScheduler =
        alarmSchedulerInstance ?: synchronized(this) {
            alarmSchedulerInstance ?: AlarmScheduler(context.applicationContext, repository(context))
                .also { alarmSchedulerInstance = it }
        }

    fun notifications(context: Context): Notifications =
        notificationsInstance ?: synchronized(this) {
            notificationsInstance ?: Notifications(context.applicationContext)
                .also { notificationsInstance = it }
        }

    fun preferences(context: Context): PreferencesRepository =
        preferencesInstance ?: synchronized(this) {
            preferencesInstance ?: PreferencesRepository(context.applicationContext)
                .also { preferencesInstance = it }
        }
}
