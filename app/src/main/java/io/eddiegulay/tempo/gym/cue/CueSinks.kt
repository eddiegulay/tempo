package io.eddiegulay.tempo.gym.cue

import io.eddiegulay.tempo.gym.SpeechAvailability
import io.eddiegulay.tempo.i18n.Lang

/**
 * The three seams [CueEngine] is built on, and the reason it can be tested at all.
 *
 * Before these existed the engine reached straight for a `Handler`, a `Vibrator`, a `ToneGenerator` and
 * a `TextToSpeech`, which made every line of it device-only — and the two most expensive bugs the
 * feature has had (a focus window that was never closed, and a speech channel that could never be
 * armed) both lived in exactly that untested half. Neither was arithmetic; both were **sequences** —
 * open, transition, cancel — and a sequence is only observable if something can stand in for the
 * things being sequenced.
 *
 * *Rejected* — Robolectric. It is the obvious way to test a `Handler` and it is a new dependency,
 * which `00-plan.md` §2 row 12 rules out. Four small interfaces cost less than a test runtime and,
 * unlike one, they also document what the engine actually asks of the platform.
 *
 * *Rejected* — one big `CuePlatform` interface. The fakes would then have to implement tones to test
 * haptics. Three sinks means a test names the channel it is about.
 */
interface CuePoster {

    /**
     * Runs [action] after [delayMs], filed under [token] so [cancel] can drop it.
     *
     * The token is the whole point: `03-player.md` §D.1 cancels cues by key set, not one at a time.
     */
    fun post(token: Any, delayMs: Long, action: () -> Unit)

    /** Drops every pending post filed under [token]. Posts under other tokens are untouched. */
    fun cancel(token: Any)

    /** Drops everything, whatever its token. Teardown only — [CueEngine.release]. */
    fun cancelAll()
}

/** The vibrator, as the engine needs it. See [GymHaptics] for the API-level story. */
interface HapticSink {

    /** False on a device with no motor, which is how the caller disarms the channel. */
    val available: Boolean

    fun play(cue: Cue)

    fun cancel()
}

/**
 * The speaker, as the engine needs it.
 *
 * [closeWindow] takes a delay rather than closing now because **the implementation owns the wait**.
 * That is not a style choice: it is the fix for the focus leak. A close posted on the engine's
 * per-segment token dies with the segment, and every merged window in §D.4 closes *after* its segment
 * ends — so the transition that cancels the segment also cancels the close that lifts the duck, and
 * the count never returns to zero for the whole session. Handing the tail to the sink puts the close on
 * a token no transition touches.
 */
interface ToneSink {

    fun openWindow()

    fun closeWindow(afterMs: Long)

    fun play(pattern: TonePattern?)

    fun cancelPendingTones()

    fun stopAll()

    fun release()
}

/** The spoken channel, as the engine needs it. See [GymSpeech] for §D.6's three rules. */
interface SpeechSink {

    /** `NoEngine` until the engine has answered. Never a reason not to [prepare]. */
    val availability: SpeechAvailability

    /**
     * Which language the voice speaks, and therefore which voice is probed for.
     *
     * **It must track the UI language, and it must be re-probed when that changes.** The channel used
     * to probe for `Locale.JAPANESE` and nothing else, which was correct while there was one language
     * and is a silent failure with two: a phrase resolved from the English table, read out by a
     * Japanese voice, is unintelligible, and a probe that asks the wrong question answers it
     * confidently. [availability] therefore means *"a voice for [language]"*, never "a voice".
     *
     * Changing it re-probes and, if the answer moved, fires [onAvailabilityChanged] — the same callback
     * the owner already re-arms on, so a switch that costs the user their voice degrades through the
     * path §D.6 already specifies rather than through a new one.
     */
    fun setLanguage(lang: Lang)

    /**
     * Fired on the main thread when the engine answers, and the escape from the deadlock this used to
     * be: `armCues` needs [availability], [availability] needs [prepare], and nothing told the caller
     * it had resolved. The owner re-runs `armCues` here.
     */
    var onAvailabilityChanged: (SpeechAvailability) -> Unit

    /** Idempotent. Called on **intent** — because the user wants speech, not because it is known to work. */
    fun prepare()

    fun speak(text: String, utteranceId: String)

    fun stop()

    fun shutdown()
}
