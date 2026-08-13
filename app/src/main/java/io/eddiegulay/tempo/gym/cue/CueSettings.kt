package io.eddiegulay.tempo.gym.cue

import io.eddiegulay.tempo.gym.EffectiveGymPreferences
import io.eddiegulay.tempo.gym.SpeechAvailability

/**
 * Which of the three channels are actually armed for this session.
 *
 * Three booleans rather than a `Set` of channel constants, because every consumer asks about one
 * channel at a time and `settings.tones` reads better at a call site than `CueChannel.Tones in set`.
 *
 * This is the **engine's** answer, and it is not the same question `01-shell.md` §B's
 * `effectiveCues(prefs, availability)` answers. That one decides what `GYM.SETTINGS` renders — which
 * rows are on, which are disabled and why. This one decides what the speaker and the vibrator are
 * asked to do, and is the only one that consults the hardware. Keeping them separate is deliberate:
 * a settings row must still be visible and switchable on a phone with no vibrator, while the engine
 * must not queue a vibration for it.
 */
data class CueSettings(
    val haptics: Boolean,
    val tones: Boolean,
    val speech: Boolean,
) {
    companion object {
        /** Everything off — the honest state before preferences have been read. */
        val Silent = CueSettings(haptics = false, tones = false, speech = false)
    }
}

/**
 * Arms the channels from the in-force preferences, the device's voices, and its motor.
 *
 * Takes [EffectiveGymPreferences] rather than `GymPreferences` on purpose. `DECISIONS.md` §Q2 makes
 * speech default off but auto-enabled under TalkBack **without writing the preference**, so the
 * engine must read the in-force value; reading the stored one here would leave a TalkBack user with a
 * silent player, and writing the stored one would leave speech mysteriously on after they switch
 * TalkBack off. Both failures are unrecoverable by the user, because in both the switch and the
 * behaviour disagree.
 *
 * A missing voice is a third state again, not a preference: §D.6 says to fall back silently to tones
 * and **never prompt for a voice download mid-workout**. So availability disarms the channel here and
 * says nothing; `GYM.SETTINGS` is where the reason is shown, at a moment the user is not mid-plank.
 *
 * **"A voice" now means "a voice for the language in force"** — see [SpeechSink.setLanguage]. That
 * widens who reaches this branch rather than narrowing it: a Japanese-market device almost always has
 * a Japanese voice and may well have no English one, so an English UI on the same phone lands here for
 * the first time. The behaviour is unchanged and that is the point; only the population is new.
 */
fun armCues(
    prefs: EffectiveGymPreferences,
    speechAvailability: SpeechAvailability,
    hasVibrator: Boolean,
): CueSettings = CueSettings(
    haptics = prefs.stored.haptics && hasVibrator,
    tones = prefs.stored.tones,
    speech = prefs.speech && speechAvailability == SpeechAvailability.Available,
)
