package io.eddiegulay.tempo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.eddiegulay.tempo.i18n.Lang
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/** The two visual modes Tempo supports: light washi (Paper) and dark washi (Sumi). */
enum class TempoTheme { Paper, Sumi }

/** Stored settings read synchronously for the very first frame, before Compose draws. */
data class InitialSettings(
    val theme: TempoTheme,
    val onboardingComplete: Boolean,
    val lang: Lang,
)

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
    private val onboardingKey = booleanPreferencesKey("onboarding_complete")
    private val languageKey = stringPreferencesKey("language")

    val theme: Flow<TempoTheme> = context.tempoDataStore.data.map { prefs ->
        prefs[themeKey].toTheme()
    }

    /**
     * The UI language.
     *
     * Lives here rather than in a `LanguageRepository` for a hard reason, not a tidiness one:
     * `preferencesDataStore` permits **one owner per file per process**, so a second delegate over
     * `tempo_settings` throws — and `tempo_settings.preferences_pb` is named in both backup
     * include-lists, so a separate file would silently drop out of backup
     * (`.planning/i18n/DECISIONS.md` §L4).
     */
    val language: Flow<Lang> = context.tempoDataStore.data.map { prefs -> prefs.toLang() }

    /**
     * Whether the user has finished the first-launch permission walkthrough. Until this is true the
     * launcher shows the onboarding gate instead of Home, so we explain every access up front.
     */
    val onboardingComplete: Flow<Boolean> = context.tempoDataStore.data.map { prefs ->
        prefs[onboardingKey] ?: false
    }

    /**
     * One synchronous read of stored settings for the very first frame. The window background and
     * the initial theme/onboarding state must be correct *before* Compose draws — otherwise a
     * returning user briefly sees the wrong theme or a blank Home, which reads as the app
     * "forgetting" their choices. DataStore is file-backed, so this is a single fast read at cold
     * start, not the per-access main-thread read the old SharedPreferences code did on every get.
     */
    fun loadInitialSettings(): InitialSettings = runBlocking {
        val prefs = context.tempoDataStore.data.first()
        InitialSettings(
            theme = prefs[themeKey].toTheme(),
            onboardingComplete = prefs[onboardingKey] ?: false,
            lang = prefs.toLang(),
        )
    }

    suspend fun setLanguage(lang: Lang) {
        context.tempoDataStore.edit { prefs -> prefs[languageKey] = lang.tag }
    }

    suspend fun setTheme(theme: TempoTheme) {
        context.tempoDataStore.edit { prefs ->
            prefs[themeKey] = if (theme == TempoTheme.Sumi) VALUE_SUMI else VALUE_PAPER
        }
    }

    suspend fun setOnboardingComplete() {
        context.tempoDataStore.edit { prefs ->
            prefs[onboardingKey] = true
        }
    }

    // "amoled" is the legacy stored value (pre-Sumi); still read as dark so existing installs
    // keep their dark choice across the rename.
    private fun String?.toTheme(): TempoTheme =
        if (this == VALUE_SUMI || this == VALUE_LEGACY_DARK) TempoTheme.Sumi else TempoTheme.Paper

    /**
     * Resolve the language, disambiguating an absent key by whether this is a returning install
     * (`.planning/i18n/DECISIONS.md` §L5).
     *
     * A missing `language` key means two entirely different things, and nothing else on disk
     * distinguishes them — so this reads `onboarding_complete`, which does:
     *
     * - **already onboarded** → a user who has been running a Japanese app, possibly for years.
     *   They keep Japanese. Without this branch, every existing user on an English-locale phone
     *   would have their launcher silently change language on upgrade, which reads as the app
     *   breaking rather than the app gaining a feature.
     * - **not yet onboarded** → a genuinely fresh install, where the system locale is the best
     *   guess available and a Japanese device should still open in Japanese.
     *
     * Same shape as the `"amoled"` read above: an old value keeps meaning exactly what it always
     * meant, and the new behaviour applies only where there is nothing to preserve.
     */
    private fun Preferences.toLang(): Lang {
        Lang.fromTag(this[languageKey])?.let { return it }
        val returning = this[onboardingKey] ?: false
        if (returning) return Lang.Ja
        return if (Locale.getDefault().language == Lang.Ja.tag) Lang.Ja else Lang.En
    }

    private companion object {
        const val VALUE_PAPER = "paper"
        const val VALUE_SUMI = "sumi"
        const val VALUE_LEGACY_DARK = "amoled"
    }
}
