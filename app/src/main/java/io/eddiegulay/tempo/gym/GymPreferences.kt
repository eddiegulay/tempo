package io.eddiegulay.tempo.gym

import kotlinx.coroutines.flow.Flow

/**
 * How the app displays a distance. The only value Phase 1 has to convert is マーフ's mile, which is a
 * Phase 2 preset — the row ships early so it is not a schema-or-UI change later, and it promises
 * nothing it cannot do: it changes displayed distance only, never weights, which this app does not
 * track (`01-shell.md` §B, edge case 8).
 */
enum class Units(val label: String) {
    Metric("メートル法"),
    Imperial("ヤード・ポンド法"),
}

/**
 * Whether the device can actually speak Japanese. Probed once and cached — **not a preference**, and
 * the distinction matters: the stored 音声 flag stays `true` when a voice is uninstalled, so
 * reinstalling one restores speech without the user having to re-find the switch
 * (`01-shell.md` §B, edge case 4).
 */
enum class SpeechAvailability { Available, NoJapaneseVoice, NoEngine }

/**
 * The stored preferences — exactly what is on disk, and exactly what `GYM.SETTINGS` renders.
 *
 * Every default here is safe on its own, which is what lets a DataStore read failure fall back to
 * `GymPreferences()` rather than throwing: a settings page showing defaults beats one that cannot
 * render (`01-shell.md` §B, edge case 10).
 *
 * Two defaults are decisions rather than conveniences. [autoAdvanceReps] is off because a rep station
 * that advances itself has decided you finished; they should have to ask for it. [speech] is off per
 * `DECISIONS.md` §Q2 — see [EffectiveGymPreferences] for the accessibility override, which
 * deliberately does not write this field.
 *
 * These live in DataStore rather than SQLite because the player's cue engine and prepare countdown
 * need them **synchronously, before the first frame**, which is what `ThemeRepository`'s
 * `loadInitialSettings` already provides and an IO-dispatched SQLite read cannot without a flash of
 * defaults. The one exception in the whole feature is `training_plan`, which is date-versioned and
 * therefore belongs in the database (`01-shell.md` §A.10, `00-plan.md` §1.2).
 */
data class GymPreferences(
    val haptics: Boolean = true,
    val tones: Boolean = true,
    val speech: Boolean = false,
    val autoAdvanceReps: Boolean = false,
    val prepareSeconds: Int = 5,
    val defaultStationRest: Int = 15,
    val defaultRoundRest: Int = 60,
    val units: Units = Units.Metric,
    val keepScreenOn: Boolean = true,
    val safetyNoteAcknowledged: Boolean = false,
)

/**
 * The preferences as one session must obey them, which is not always what is stored.
 *
 * Exactly one field diverges, and modelling that divergence rather than collapsing it is
 * `DECISIONS.md` §Q2's requirement. Speech cues default off; `AccessibilityManager`'s
 * `isTouchExplorationEnabled` at session start enables them **for that session only, without writing
 * the preference**. So there are two different true answers to "is speech on" and they belong to
 * different questions:
 *
 * - the `GYM.SETTINGS` toggle asks what is *stored*, and reads [stored] — with a subtitle explaining
 *   the override when touch exploration is on, which is what [speechOverriddenByTouchExploration] is
 *   for;
 * - the cue engine asks what is *in force*, and reads [speech].
 *
 * Collapsing them either turns the toggle on behind the user's back — so switching TalkBack off later
 * leaves speech mysteriously enabled — or leaves a TalkBack user with a silent player. Neither is
 * recoverable by the user, because in both cases the switch and the behaviour disagree.
 *
 * Nothing else is overridden, and nothing else is mirrored here on purpose: a wrapper that forwarded
 * all ten fields would invite a second override to be added quietly. Read everything but speech from
 * [stored].
 *
 * Availability is *not* folded in. A missing Japanese voice is a third thing again — the row is shown
 * disabled with the reason, and cues fall back to tones — and `effectiveCues(prefs, availability)`
 * owns that decision, one layer up (`01-shell.md` §B).
 */
data class EffectiveGymPreferences(
    val stored: GymPreferences,
    val touchExplorationEnabled: Boolean,
) {
    /** What the cue engine obeys. */
    val speech: Boolean get() = stored.speech || touchExplorationEnabled

    /** True when speech is on only because TalkBack is. The settings subtitle exists to say so. */
    val speechOverriddenByTouchExploration: Boolean
        get() = !stored.speech && touchExplorationEnabled
}

/**
 * 鍛錬's slice of the existing `tempo_settings` DataStore.
 *
 * An interface for the same reason [GymRepository] is one: `GYM.SETTINGS` has seven states worth
 * exercising and the player reads these on its first frame, and neither should need a `Context` to be
 * tested. The implementation is a thin `Preferences` wrapper — there is nothing else in it.
 *
 * *Rejected* — putting these in SQLite beside the routines. They need [loadInitial]'s synchronous
 * first read, a nine-row key/value table would be a schema liability for no gain, and DataStore is
 * already transactional and already in `res/xml/backup_rules.xml` (`01-shell.md` §A.10).
 *
 * **Every setter writes on the tap that made it.** There is no 保存 button, no draft and no
 * confirmation, so there is no dirty state and the page registers no `BackHandler`. A failed write
 * reverts the row to its stored value and says 保存できませんでした — a row must never be left showing a
 * value that was not persisted (`01-shell.md` §B).
 */
interface GymPreferencesRepository {

    val preferences: Flow<GymPreferences>

    /**
     * One synchronous read for the player's first frame — mirrors `ThemeRepository.loadInitialSettings`.
     *
     * This is why `GYM.SETTINGS` has no Loading state at all: the defaults are total and this seeds
     * the real values before anything draws, so there is never a spinner that then jumps.
     */
    fun loadInitial(): GymPreferences

    suspend fun setHaptics(on: Boolean)

    suspend fun setTones(on: Boolean)

    /**
     * Writes the **stored** flag only. The TalkBack override is per-session and must never be
     * persisted through here — see [EffectiveGymPreferences] and `DECISIONS.md` §Q2.
     */
    suspend fun setSpeech(on: Boolean)

    suspend fun setAutoAdvanceReps(on: Boolean)

    /**
     * Clamped 0..15. Zero is legal, and the timeline compiler must then emit **no** 支度 segment
     * rather than a zero-length one — a zero-duration segment divides by zero in the ensō sweep
     * (`01-shell.md` §B, edge case 1).
     */
    suspend fun setPrepareSeconds(seconds: Int)

    /**
     * Clamped 0..300. A **default** for routines authored from now on, never an override: changing it
     * must not rewrite an existing routine's stored rests. 休息の初期値 and the
     * これから作る型に使われます footnote both exist to make that unambiguous, because "default rest" is
     * the most commonly misread setting in every logging app (`01-shell.md` §B, edge case 6).
     */
    suspend fun setDefaultStationRest(seconds: Int)

    /** Clamped 0..600. A default for new routines only — see [setDefaultStationRest]. */
    suspend fun setDefaultRoundRest(seconds: Int)

    suspend fun setUnits(units: Units)

    suspend fun setKeepScreenOn(on: Boolean)

    /**
     * Acknowledges the safety note. Written only when 安全のために is reached from the first-run line;
     * opening it from `GYM.SETTINGS` writes nothing, because reading it again is not a new consent.
     */
    suspend fun setSafetyNoteAcknowledged()
}
