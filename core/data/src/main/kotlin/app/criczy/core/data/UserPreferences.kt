package app.criczy.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val onboardingDone: Boolean = false,
    val alertsEnabled: Boolean = true,
    val alertMatchStart: Boolean = true,
    val alertWickets: Boolean = false,
    val alertResults: Boolean = true,
    val confirmExit: Boolean = false,
)

private val Context.userPreferencesStore by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.userPreferencesStore)

    val preferences: Flow<UserPreferences> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            UserPreferences(
                themeMode = p[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM,
                dynamicColor = p[DYNAMIC] ?: false,
                onboardingDone = p[ONBOARDED] ?: false,
                alertsEnabled = p[ALERTS] ?: true,
                alertMatchStart = p[ALERT_START] ?: true,
                alertWickets = p[ALERT_WICKETS] ?: false,
                alertResults = p[ALERT_RESULTS] ?: true,
                confirmExit = p[CONFIRM_EXIT] ?: false,
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[THEME] = mode.name }.let { }
    suspend fun setDynamicColor(on: Boolean) = set(DYNAMIC, on)
    suspend fun setOnboardingDone() = set(ONBOARDED, true)
    suspend fun setAlertsEnabled(on: Boolean) = set(ALERTS, on)
    suspend fun setAlertMatchStart(on: Boolean) = set(ALERT_START, on)
    suspend fun setAlertWickets(on: Boolean) = set(ALERT_WICKETS, on)
    suspend fun setAlertResults(on: Boolean) = set(ALERT_RESULTS, on)
    suspend fun setConfirmExit(on: Boolean) = set(CONFIRM_EXIT, on)

    private suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        store.edit { it[key] = value }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val ONBOARDED = booleanPreferencesKey("onboarding_done")
        val ALERTS = booleanPreferencesKey("alerts_enabled")
        val ALERT_START = booleanPreferencesKey("alert_match_start")
        val ALERT_WICKETS = booleanPreferencesKey("alert_wickets")
        val ALERT_RESULTS = booleanPreferencesKey("alert_results")
        val CONFIRM_EXIT = booleanPreferencesKey("confirm_exit")
    }
}
