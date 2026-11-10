package com.crylo.counter.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val hapticOnTap: Boolean = true,
    val soundOnTarget: Boolean = false,
    val volumeKeys: Boolean = false,
    val keepScreenOn: Boolean = true,
    val amoledBlack: Boolean = false,
    val lastCounterId: Long? = null,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val haptic = booleanPreferencesKey("haptic_on_tap")
        val sound = booleanPreferencesKey("sound_on_target")
        val volume = booleanPreferencesKey("volume_keys")
        val screenOn = booleanPreferencesKey("keep_screen_on")
        val amoled = booleanPreferencesKey("amoled_black")
        val lastCounter = longPreferencesKey("last_counter_id")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            hapticOnTap = p[Keys.haptic] ?: d.hapticOnTap,
            soundOnTarget = p[Keys.sound] ?: d.soundOnTarget,
            volumeKeys = p[Keys.volume] ?: d.volumeKeys,
            keepScreenOn = p[Keys.screenOn] ?: d.keepScreenOn,
            amoledBlack = p[Keys.amoled] ?: d.amoledBlack,
            lastCounterId = p[Keys.lastCounter],
        )
    }

    suspend fun setHaptic(on: Boolean) = set(Keys.haptic, on)
    suspend fun setSound(on: Boolean) = set(Keys.sound, on)
    suspend fun setVolumeKeys(on: Boolean) = set(Keys.volume, on)
    suspend fun setKeepScreenOn(on: Boolean) = set(Keys.screenOn, on)
    suspend fun setAmoled(on: Boolean) = set(Keys.amoled, on)

    suspend fun setLastCounter(id: Long?) {
        context.dataStore.edit { if (id == null) it.remove(Keys.lastCounter) else it[Keys.lastCounter] = id }
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
