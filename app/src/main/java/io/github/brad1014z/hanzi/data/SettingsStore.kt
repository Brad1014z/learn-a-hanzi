package io.github.brad1014z.hanzi.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * Small settings over DataStore Preferences (spec 01) — replaces the prototype's
 * in-memory toggles so they survive restarts (M1).
 */
class SettingsStore(private val context: Context) {

    private val autoPlayKey = booleanPreferencesKey("autoPlayAudio")
    private val soundKey = booleanPreferencesKey("soundEffects")

    /** "Auto-play audio" (spec 07): speak the character on intro open / demo start. */
    val autoPlay: Flow<Boolean> = context.settingsDataStore.data.map { it[autoPlayKey] ?: true }

    /** Verdict sound effects on/off (spec 07). */
    val sound: Flow<Boolean> = context.settingsDataStore.data.map { it[soundKey] ?: true }

    suspend fun setAutoPlay(value: Boolean) {
        context.settingsDataStore.edit { it[autoPlayKey] = value }
    }

    suspend fun setSound(value: Boolean) {
        context.settingsDataStore.edit { it[soundKey] = value }
    }
}
