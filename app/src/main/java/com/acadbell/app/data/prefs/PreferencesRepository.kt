package com.acadbell.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.acadbell.app.domain.AlarmSoundChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "acadbell_prefs")

/** All user preferences. DataStore is async and survives process death. */
class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")           // system | light | dark
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val DEFAULT_SOUND = stringPreferencesKey("default_sound")
        val DEFAULT_VIBRATION = booleanPreferencesKey("default_vibration")
        val DEFAULT_SNOOZE = intPreferencesKey("default_snooze")
        val DEFAULT_DURATION = intPreferencesKey("default_duration_min")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SESSIONS_COMPLETED_LIFETIME = intPreferencesKey("lifetime_sessions")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    }

    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "system" }
    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDED] ?: false }
    val defaultSound: Flow<String> = context.dataStore.data.map { it[Keys.DEFAULT_SOUND] ?: AlarmSoundChoice.CLASSIC_BELL.name }
    val defaultVibration: Flow<Boolean> = context.dataStore.data.map { it[Keys.DEFAULT_VIBRATION] ?: true }
    val defaultSnooze: Flow<Int> = context.dataStore.data.map { it[Keys.DEFAULT_SNOOZE] ?: 5 }
    val defaultDuration: Flow<Int> = context.dataStore.data.map { it[Keys.DEFAULT_DURATION] ?: 60 }
    val keepScreenOn: Flow<Boolean> = context.dataStore.data.map { it[Keys.KEEP_SCREEN_ON] ?: true }

    suspend fun setThemeMode(mode: String) = context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    suspend fun setOnboarded(value: Boolean) = context.dataStore.edit { it[Keys.ONBOARDED] = value }
    suspend fun setDefaultSound(sound: String) = context.dataStore.edit { it[Keys.DEFAULT_SOUND] = sound }
    suspend fun setDefaultVibration(value: Boolean) = context.dataStore.edit { it[Keys.DEFAULT_VIBRATION] = value }
    suspend fun setDefaultSnooze(minutes: Int) = context.dataStore.edit { it[Keys.DEFAULT_SNOOZE] = minutes }
    suspend fun setDefaultDuration(minutes: Int) = context.dataStore.edit { it[Keys.DEFAULT_DURATION] = minutes }
    suspend fun setKeepScreenOn(value: Boolean) = context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = value }
}
