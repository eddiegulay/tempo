package io.eddiegulay.tempo.gym.cue

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityManager
import io.eddiegulay.tempo.gym.SpeechAvailability
import io.eddiegulay.tempo.i18n.Lang
import java.util.Locale

/**
 * Is TalkBack (or any other touch-exploration service) driving the screen right now?
 *
 * `DECISIONS.md` §Q2 turns this one boolean into the speech default: cues stay off for everyone, and
 * switch on **for this session only, without writing the preference**, when touch exploration is
 * active. The alternative that was rejected — flipping the stored flag — leaves speech mysteriously
 * enabled after the user turns TalkBack off, with the settings switch and the behaviour disagreeing
 * and no way for them to work out why.
 *
 * Read at session start rather than observed: a service enabled mid-plank should not start narrating
 * a countdown that is already running, and a session is short enough that the next one will pick it
 * up.
 */
fun isTouchExplorationEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    return manager?.isTouchExplorationEnabled == true
}

/**
 * The voice a [Lang] is read in — **the one place the app decides which voice to ask for.**
 *
 * There were two hard-coded `Locale.JAPANESE`s: the player's channel ([GymSpeech.setLanguage]) and
 * `GYM.SETTINGS`' own probe. Two independent copies of one decision is how they came to disagree with
 * the UI language and could not stop disagreeing with each other; this is the shared answer, and the
 * settings probe should call it rather than keep a third.
 *
 * `Locale.ENGLISH` rather than a country-qualified locale on purpose. `TextToSpeech.setLanguage`
 * answers `LANG_AVAILABLE` for a language match and only `LANG_COUNTRY_AVAILABLE` for a country one,
 * and every value except `LANG_MISSING_DATA` / `LANG_NOT_SUPPORTED` counts as available — so asking
 * for the language alone accepts en-GB, en-IN and en-US alike. Asking for `en-US` would have made a
 * British voice a missing voice.
 */
fun ttsLocale(lang: Lang): Locale = when (lang) {
    Lang.Ja -> Locale.JAPANESE
    Lang.En -> Locale.ENGLISH
}

/**
 * The spoken channel: two words at a time, always interrupting, never asking for anything.
 *
 * Three rules from `03-player.md` §D.6, each of which is a failure someone has shipped before:
 *
 * 1. **`QUEUE_FLUSH`, always.** A stale 「半分」 arriving over the next station is worse than no cue at
 *    all, and a queue in a timer app is a queue that grows.
 * 2. **Silent fallback on a missing voice.** `LANG_MISSING_DATA` / `LANG_NOT_SUPPORTED` mark the
 *    channel unavailable and the player carries on with tones. It must **never** prompt for a voice
 *    download mid-workout — the one moment the user cannot deal with a dialog. That rule matters more
 *    now, not less: a Japanese-market phone very nearly always has a Japanese voice, and the same phone
 *    running an English UI may well have no English one, so this fallback is reached by a population it
 *    never was before. `Intent(ACTION_INSTALL_TTS_DATA)` is what it must not do, and it does not.
 * 3. **Never the 3-2-1.** Engine latency is variable and speech would drift audibly against a haptic
 *    that does not. That rule is enforced by the cue table, which gives [Cue.COUNT_TICK] no speech
 *    column at all, so there is nothing here to get wrong.
 *
 * The engine is created **lazily**, on the first session that has speech armed. A `TextToSpeech`
 * binds to another process; constructing one for every user of a launcher, most of whom will never
 * turn speech on, is a service binding held open for nothing.
 *
 * Speech owns its own audio-focus window, which is why [onWindowOpen] / [onWindowClose] exist rather
 * than the engine wrapping `speak` in a duck of some guessed length. §D.2's [Cue.LAST_ROUND] row reads
 * "speech only" and gives no numbers, and the honest window for an utterance is the utterance: open at
 * `speak`, close at `onDone`. Guessing a duration would have been inventing one.
 */
class GymSpeech(
    context: Context,
    onWindowOpen: () -> Unit,
    onWindowClose: () -> Unit,
    language: Lang = Lang.Ja,
) : SpeechSink {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var initialised = false

    /** The language every probe asks about and every utterance is read in. See [setLanguage]. */
    private var lang: Lang = language

    /** Unknown until [prepare] has run and the engine has called back. Callers disarm on anything else. */
    override var availability: SpeechAvailability = SpeechAvailability.NoEngine
        private set(value) {
            val changed = field != value
            field = value
            if (changed) onAvailabilityChanged(value)
        }

    /**
     * How the caller learns that the probe finished — and the half of the deadlock that was missing.
     *
     * `armCues` cannot enable speech until availability is `Available`, and availability cannot become
     * `Available` until someone calls [prepare]. The engine now probes on **intent** (see
     * [CueEngine.arm]), which breaks the cycle in one direction; this closes it in the other, because
     * an optimistic probe with no answer is still a channel nobody can switch on. Fired on the main
     * thread, from the init callback, exactly when the answer changes.
     */
    override var onAvailabilityChanged: (SpeechAvailability) -> Unit = {}

    /** One window per utterance id, counted where it can be tested. See [SpeechWindows]. */
    private val windows = SpeechWindows(onOpen = onWindowOpen, onClose = onWindowClose)

    /**
     * Starts the engine if it is not already starting. Idempotent, and cheap to call again.
     *
     * Called when a session arms the speech channel, not from a constructor: the probe is the
     * expensive part and it is only owed by a user who will hear the result.
     */
    override fun prepare() {
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            handler.post { onEngineReady(status) }
        }
    }

    /**
     * Re-points the voice at [lang], and re-probes if the engine is already up.
     *
     * Idempotent, and cheap when nothing changed. The probe is the whole of the work: `setLanguage`
     * *is* the question ("do you have a voice for this?") as well as the instruction, so there is no
     * separate query to make and no state to invalidate beyond [initialised].
     *
     * Called with no engine bound — the common case, because a launcher whose user never turns speech
     * on never binds one — this only records the answer to give at [prepare] time.
     *
     * It deliberately does **not** stop what is being said. A `setLanguage` applies to subsequent
     * utterances, so at worst one already-queued phrase finishes in the outgoing voice; cutting it off
     * would mean a cue vanishing mid-word, which §D.6 treats as worse than a cue that is merely late.
     */
    override fun setLanguage(lang: Lang) {
        if (this.lang == lang) return
        this.lang = lang
        val engine = tts ?: return
        applyLanguage(engine)
    }

    private fun onEngineReady(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            availability = SpeechAvailability.NoEngine
            return
        }
        applyLanguage(engine)
        runCatching {
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
        }
        engine.setOnUtteranceProgressListener(listener)
    }

    /**
     * Asks the engine for a voice in [lang] and records the answer — the one probe, in one place.
     *
     * It was two copies of the same `when` before ([onEngineReady] and a settings-page probe), which is
     * how a hard-coded `Locale.JAPANESE` came to exist twice. This is the engine's half; the settings
     * page keeps its own because it must express a fourth state (*not answered yet*) that this field
     * cannot — but both now have to ask about the same language, and this is the one the player obeys.
     *
     * [initialised] is set from the result rather than alongside it, so a language change that loses
     * the voice also stops [speak] from handing text to an engine that would read it in the wrong one.
     */
    private fun applyLanguage(engine: TextToSpeech) {
        val result = runCatching { engine.setLanguage(ttsLocale(lang)) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        availability = when (result) {
            // The case still spells "Japanese" and no longer means it: this arm is reached for whatever
            // [lang] is. Renaming it to `NoVoiceForLanguage` is one token in `GymPreferences.kt` plus
            // every `when` over the enum, and `speechRowState` is exhaustive with no `else`, so the
            // rename has to land in one commit across files this unit does not own. Reported, not done.
            TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED ->
                SpeechAvailability.NoVoiceForLanguage
            else -> SpeechAvailability.Available
        }
        initialised = availability == SpeechAvailability.Available
    }

    /**
     * Says [text], cutting off whatever was being said.
     *
     * [utteranceId] is per cue and per segment — `"$cue@$ordinal"` at the call site — so that a
     * flushed utterance's terminal callback can be told apart from the one that replaced it. Without
     * distinct ids the focus window count drifts, and a drifted count is a duck that never lifts.
     */
    override fun speak(text: String, utteranceId: String) {
        val engine = tts ?: return
        if (!initialised) return
        // The window is taken by the SET, not by the call: a repeated id (a re-entered segment) must
        // not take a second window that only one terminal callback could ever give back.
        windows.open(utteranceId)
        val queued = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }.getOrDefault(TextToSpeech.ERROR)
        if (queued != TextToSpeech.SUCCESS) windows.close(utteranceId)
    }

    /** §D.7: every row but COMPLETE stops speech. 「終わり」 is allowed to finish. */
    override fun stop() {
        runCatching { tts?.stop() }
        windows.drain()
    }

    /** `onCleared`. A `TextToSpeech` left un-shut-down keeps its service binding alive indefinitely. */
    override fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
        initialised = false
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = post(utteranceId)

        @Deprecated("Kept because the platform still calls it on older engines.")
        override fun onError(utteranceId: String?) = post(utteranceId)
        override fun onError(utteranceId: String?, errorCode: Int) = post(utteranceId)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = post(utteranceId)

        // Callbacks arrive on a binder thread; the window counter and the pending set are only ever
        // touched on the main thread, which is cheaper and clearer than making both concurrent for a
        // handful of events per minute.
        private fun post(utteranceId: String?) {
            handler.post { windows.close(utteranceId ?: return@post) }
        }
    }
}
