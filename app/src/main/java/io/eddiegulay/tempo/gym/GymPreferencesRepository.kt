package io.eddiegulay.tempo.gym

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException

// 鍛錬's own preferences file, and the one place this unit knowingly diverges from `01-shell.md`
// §A.10, which asks for these keys inside the existing `tempo_settings` store.
//
// The reason is mechanical rather than a preference of mine. `ThemeRepository`'s delegate is
// `private val Context.tempoDataStore` — file-private — and a second `preferencesDataStore(name =
// "tempo_settings")` declared anywhere else is not a second view of the same file, it is a second
// *owner* of it: DataStore keeps a process-wide set of active files and throws
// "There are multiple DataStores active for the same file" the moment both are read. Since
// `ThemeRepository` is read on every cold start, that throw would be unconditional.
//
// Merging back into `tempo_settings` needs one word changed in a file this unit does not own
// (`data/ThemeRepository.kt`: `private` → `internal`), plus the two `res/xml` backup rules gaining
// `datastore/tempo_gym.preferences_pb`. Every key below is already prefixed `gym_`, so that merge is
// a delete of this delegate and nothing else — no migration, no reset, no lost settings.
private val Context.gymDataStore: DataStore<Preferences> by preferencesDataStore(name = "tempo_gym")

/**
 * The DataStore half of [GymPreferencesRepository], shaped after `ThemeRepository` down to its
 * synchronous first read.
 *
 * [loadInitial] is the whole reason these nine values are not in SQLite with the routines. The cue
 * engine and the prepare countdown need them **before the player's first frame**, and an
 * IO-dispatched read cannot deliver that without a visible flash of defaults — a 支度 that starts at
 * five seconds and jumps to zero because the user had set it to zero is the app forgetting itself in
 * front of them. `ThemeRepository.loadInitialSettings` already solved this for the theme, at cold
 * start, on the main thread, and the argument is identical here (`00-plan.md` §1.2, `01-shell.md`
 * §A.10).
 *
 * *Rejected* — a nine-row key/value table in the gym database. It buys one store instead of two and
 * costs the synchronous read, a schema liability, and a corruption mode: the gym database can be
 * quarantined (`02-data.md` §E.6) and a user who lost their history should not also find their cue
 * settings reset.
 *
 * **A read failure yields defaults, never an exception.** Every default in [GymPreferences] is safe
 * on its own, so a settings page rendering defaults is strictly better than one that cannot render —
 * and unlike the record screens there is no `Loadable` distinction to protect here, because nothing
 * on this page is a claim about the user's history (`01-shell.md` §B, edge case 10).
 */
class GymPreferencesStore(private val context: Context) : GymPreferencesRepository {

    override val preferences: Flow<GymPreferences> = context.gymDataStore.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { it.toGymPreferences() }

    override fun loadInitial(): GymPreferences = runBlocking {
        runCatching { context.gymDataStore.data.first().toGymPreferences() }
            .getOrDefault(GymPreferences())
    }

    override suspend fun setHaptics(on: Boolean) = put(HAPTICS, on)

    override suspend fun setTones(on: Boolean) = put(TONES, on)

    override suspend fun setSpeech(on: Boolean) = put(SPEECH, on)

    override suspend fun setAutoAdvanceReps(on: Boolean) = put(AUTO_ADVANCE_REPS, on)

    override suspend fun setPrepareSeconds(seconds: Int) = put(PREPARE_SECONDS, clampPrepareSeconds(seconds))

    override suspend fun setDefaultStationRest(seconds: Int) = put(STATION_REST, clampStationRest(seconds))

    override suspend fun setDefaultRoundRest(seconds: Int) = put(ROUND_REST, clampRoundRest(seconds))

    override suspend fun setUnits(units: Units) = put(UNITS, units.name)

    override suspend fun setKeepScreenOn(on: Boolean) = put(KEEP_SCREEN_ON, on)

    override suspend fun setSafetyNoteAcknowledged() = put(SAFETY_ACKNOWLEDGED, true)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.gymDataStore.edit { it[key] = value }
    }

    /**
     * Stored values are clamped again on the way *out*, not only on the way in.
     *
     * A value written by an older build, or restored from a backup taken before a bound moved, would
     * otherwise reach the timeline compiler unchecked — and an eleven-minute 支度 is not a cosmetic
     * bug, it is a workout that never starts. Units fall back rather than throw for the same reason
     * `Engine.fromStorage` returns null: an unreadable token is a fact to survive, not a crash.
     */
    private fun Preferences.toGymPreferences(): GymPreferences {
        val defaults = GymPreferences()
        return GymPreferences(
            haptics = this[HAPTICS] ?: defaults.haptics,
            tones = this[TONES] ?: defaults.tones,
            speech = this[SPEECH] ?: defaults.speech,
            autoAdvanceReps = this[AUTO_ADVANCE_REPS] ?: defaults.autoAdvanceReps,
            prepareSeconds = clampPrepareSeconds(this[PREPARE_SECONDS] ?: defaults.prepareSeconds),
            defaultStationRest = clampStationRest(this[STATION_REST] ?: defaults.defaultStationRest),
            defaultRoundRest = clampRoundRest(this[ROUND_REST] ?: defaults.defaultRoundRest),
            units = Units.entries.firstOrNull { it.name == this[UNITS] } ?: defaults.units,
            keepScreenOn = this[KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            safetyNoteAcknowledged = this[SAFETY_ACKNOWLEDGED] ?: defaults.safetyNoteAcknowledged,
        )
    }

    private companion object {
        // Prefixed so this file can be folded into `tempo_settings` later without a key collision or
        // a migration — see the note on the delegate above.
        val HAPTICS = booleanPreferencesKey("gym_haptics")
        val TONES = booleanPreferencesKey("gym_tones")
        val SPEECH = booleanPreferencesKey("gym_speech")
        val AUTO_ADVANCE_REPS = booleanPreferencesKey("gym_auto_advance_reps")
        val PREPARE_SECONDS = intPreferencesKey("gym_prepare_seconds")
        val STATION_REST = intPreferencesKey("gym_default_station_rest")
        val ROUND_REST = intPreferencesKey("gym_default_round_rest")
        val UNITS = stringPreferencesKey("gym_units")
        val KEEP_SCREEN_ON = booleanPreferencesKey("gym_keep_screen_on")
        val SAFETY_ACKNOWLEDGED = booleanPreferencesKey("gym_safety_note_acknowledged")
    }
}

/**
 * 支度's bound, 0..15 (`01-shell.md` §B).
 *
 * Public, and the settings page's wheel must call this rather than restate `0..15`. A bound spelled
 * at two call sites is the kind of disagreement nobody notices until they disagree — the same reason
 * `cycleDotsOverflow` is public — and here the disagreement is silent in the worse direction: the
 * wheel would offer a value the store then clamps away, so the row shows something that was never
 * written.
 *
 * **Zero is legal and is not "off".** The timeline compiler must then emit *no* 支度 segment rather
 * than a zero-length one, because a zero-duration segment divides by zero in the ensō sweep.
 */
fun clampPrepareSeconds(raw: Int): Int = raw.coerceIn(0, 15)

/** The between-stations rest bound, 0..300 (`01-shell.md` §B). A default for new routines only. */
fun clampStationRest(raw: Int): Int = raw.coerceIn(0, 300)

/** The between-rounds rest bound, 0..600 (`01-shell.md` §B). A default for new routines only. */
fun clampRoundRest(raw: Int): Int = raw.coerceIn(0, 600)

// The TalkBack half of `DECISIONS.md` §Q2 is deliberately NOT here. `gym.cue.isTouchExplorationEnabled`
// already owns that probe, and it belongs beside the cue engine that consumes it: touch exploration is
// not stored, is not a preference, and changes while the app is alive. A second copy in this file
// would put the two questions §Q2 exists to separate — what is *stored* (the settings toggle) and what
// is *in force* (the cue engine) — one autocomplete apart from each other.
