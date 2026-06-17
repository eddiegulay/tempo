package io.eddiegulay.tempo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The two visual modes Tempo supports. */
enum class TempoTheme { Paper, Amoled }

private const val LEGACY_PREFS = "tempo"

// Single process-wide DataStore. The migration imports the old SharedPreferences "theme" key
// (same name + string value), so existing installs keep their choice without a reset.
private val Context.tempoDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tempo_settings",
    produceMigrations = { ctx -> listOf(SharedPreferencesMigration(ctx, LEGACY_PREFS)) },
)

/**
 * Durable storage for user settings, backed by Jetpack DataStore (async, transactional, Flow-based).
 * Replaces the earlier synchronous SharedPreferences read on the main thread.
 */
class ThemeRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme")

    val theme: Flow<TempoTheme> = context.tempoDataStore.data.map { prefs ->
        if (prefs[themeKey] == VALUE_AMOLED) TempoTheme.Amoled else TempoTheme.Paper
    }

    suspend fun setTheme(theme: TempoTheme) {
        context.tempoDataStore.edit { prefs ->
            prefs[themeKey] = if (theme == TempoTheme.Amoled) VALUE_AMOLED else VALUE_PAPER
        }
    }

    private companion object {
        const val VALUE_PAPER = "paper"
        const val VALUE_AMOLED = "amoled"
    }
}
