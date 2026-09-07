package io.github.brad1014z.hanzi.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
    private val pilotExportConsentKey = booleanPreferencesKey("pilotExportConsent")
    private val pilotFacilitatorLabelKey = stringPreferencesKey("pilotFacilitatorLabel")

    /** "Auto-play audio" (spec 07): speak the character on intro open / demo start. */
    val autoPlay: Flow<Boolean> = context.settingsDataStore.data.map { it[autoPlayKey] ?: true }

    /** Verdict sound effects on/off (spec 07). */
    val sound: Flow<Boolean> = context.settingsDataStore.data.map { it[soundKey] ?: true }

    /** Debug/pilot only. No export occurs until this explicit local consent is true. */
    val pilotExportConsent: Flow<Boolean> =
        context.settingsDataStore.data.map { it[pilotExportConsentKey] ?: false }

    val pilotFacilitatorLabel: Flow<String> =
        context.settingsDataStore.data.map { it[pilotFacilitatorLabelKey] ?: "unlabeled" }

    suspend fun setAutoPlay(value: Boolean) {
        context.settingsDataStore.edit { it[autoPlayKey] = value }
    }

    suspend fun setSound(value: Boolean) {
        context.settingsDataStore.edit { it[soundKey] = value }
    }

    suspend fun setPilotExportConsent(value: Boolean) {
        context.settingsDataStore.edit { it[pilotExportConsentKey] = value }
    }

    suspend fun setPilotFacilitatorLabel(value: String) {
        require(value in PILOT_LABELS)
        context.settingsDataStore.edit { it[pilotFacilitatorLabelKey] = value }
    }

    companion object {
        val PILOT_LABELS = setOf("unlabeled", "honest", "wrong-direction-order")
    }
}
