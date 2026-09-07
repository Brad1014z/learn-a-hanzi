package io.github.brad1014z.hanzi.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

    // Opt-in daily reminder (spec 10 guardrail 5). Default time is chosen once, on
    // first offer, by whatever the clock says then (see ReminderOfferSheet) — there is
    // no "6pm for everyone" default baked in here.
    private val reminderEnabledKey = booleanPreferencesKey("reminderEnabled")
    private val reminderHourKey = intPreferencesKey("reminderHour")
    private val reminderMinuteKey = intPreferencesKey("reminderMinute")
    // Whether the one-time post-chest offer has been shown. Never reset, never
    // repeated — matches the sign-in entry-point pattern in spec 12.
    private val reminderOfferShownKey = booleanPreferencesKey("reminderOfferShown")

    /** "Auto-play audio" (spec 07): speak the character on intro open / demo start. */
    val autoPlay: Flow<Boolean> = context.settingsDataStore.data.map { it[autoPlayKey] ?: true }

    /** Verdict sound effects on/off (spec 07). */
    val sound: Flow<Boolean> = context.settingsDataStore.data.map { it[soundKey] ?: true }

    /** Debug/pilot only. No export occurs until this explicit local consent is true. */
    val pilotExportConsent: Flow<Boolean> =
        context.settingsDataStore.data.map { it[pilotExportConsentKey] ?: false }

    val pilotFacilitatorLabel: Flow<String> =
        context.settingsDataStore.data.map { it[pilotFacilitatorLabelKey] ?: "unlabeled" }

    val reminderEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[reminderEnabledKey] ?: false }

    val reminderHour: Flow<Int> = context.settingsDataStore.data.map { it[reminderHourKey] ?: 18 }
    val reminderMinute: Flow<Int> = context.settingsDataStore.data.map { it[reminderMinuteKey] ?: 0 }

    val reminderOfferShown: Flow<Boolean> =
        context.settingsDataStore.data.map { it[reminderOfferShownKey] ?: false }

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

    suspend fun setReminderEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[reminderEnabledKey] = value }
    }

    suspend fun setReminderTime(hour: Int, minute: Int) {
        require(hour in 0..23) { "hour must be 0..23" }
        require(minute in 0..59) { "minute must be 0..59" }
        context.settingsDataStore.edit {
            it[reminderHourKey] = hour
            it[reminderMinuteKey] = minute
        }
    }

    suspend fun setReminderOfferShown(value: Boolean) {
        context.settingsDataStore.edit { it[reminderOfferShownKey] = value }
    }

    companion object {
        val PILOT_LABELS = setOf("unlabeled", "honest", "wrong-direction-order")
    }
}
