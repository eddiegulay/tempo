package io.eddiegulay.tempo.gym.cue

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.eddiegulay.tempo.gym.EffectiveGymPreferences
import io.eddiegulay.tempo.gym.SpeechAvailability
import io.eddiegulay.tempo.i18n.Strings
import io.eddiegulay.tempo.i18n.StringsJa

/**
 * The cue engine of `03-player.md` §D: one object that turns a compiled segment into vibration, tone
 * and speech, and that goes silent on command.
 *
 * **Cues are derived from the timeline, not from ticks** (§D.1). On entering a segment the whole
 * schedule is computed once and posted; nothing consults the 20fps render loop. A tick-driven engine
 * fires late by up to a frame every single time, and fires twice whenever two ticks straddle the same
 * millisecond — both of which are audible in a 3-2-1 and neither of which is reproducible.
 *
 * Posts are keyed by segment ordinal so that a transition cancels exactly the cues that belonged to
 * the segment being left. That is what makes §A's WORK edge case 8 fall out for free: pressing ▷
 * during the last three seconds cancels the pending interval end rather than firing it twice.
 *
 * **What a segment token must NOT own: the close of a focus window.** Every merged window in §D.4
 * outlives its segment — COUNT_TICK's span runs to `planned + 450` and INTERVAL_END's to the same — so
 * at the transition the close is still in the future, and the first thing the next segment does is
 * cancel this segment's key set. Posting the close under the segment token therefore deleted it, the
 * count never came back to zero, and the duck was held for the whole session: the exact twenty-minute
 * failure §D.4 exists to prevent. The open is posted here; the **close is handed to [ToneSink] at the
 * moment the window opens**, so it rides that sink's own token and no transition can reach it.
 *
 * The engine holds **no phase state of its own**. It is told what happened and it obeys; the state
 * machine in §C is the single source of truth about where the session is, and a second copy here
 * would eventually disagree with it in exactly the situations nobody tests.
 *
 * *Rejected* — a coroutine per cue on the player's scope. `delay()` uses a timebase that is fine here,
 * but cancellation would then be structured per-cue rather than per-segment, and the cancel-the-whole-
 * key-set operation §D.1 asks for would have to be rebuilt out of job bookkeeping. A [CuePoster] token
 * already is that operation.
 *
 * @param onSpeechAvailabilityChanged fired once the TTS engine answers. See [arm] for why the engine
 *   cannot simply be asked.
 * @param strings the language in force at construction. Kept current by [setLanguage]; see it for why
 *   this is a mutable field on the engine rather than an argument to [enterSegment].
 */
class CueEngine(
    private val haptics: HapticSink,
    private val tones: ToneSink,
    private val speech: SpeechSink,
    private val poster: CuePoster = HandlerCuePoster(),
    private val onSpeechAvailabilityChanged: (SpeechAvailability) -> Unit = {},
    strings: Strings = StringsJa,
) {

    /**
     * The table every fixed phrase is resolved from, and the language the voice is probed for.
     *
     * **One field, two consumers, and that is the point.** The phrase and the voice have to be the same
     * language or the channel reads English words in a Japanese voice; holding them apart would make
     * that a state the type permits. [setLanguage] moves both or neither.
     */
    var strings: Strings = strings
        private set

    init {
        speech.onAvailabilityChanged = { onSpeechAvailabilityChanged(it) }
        // The sink is constructed with this language by the factory below; the call is here for the
        // constructor a test uses, where the sink cannot have been told.
        speech.setLanguage(this.strings.lang)
    }

    /**
     * The token every post of the current segment is filed under.
     *
     * §D.1 keys cues by `(segmentOrdinal, cueId)` and cancels the whole key set on any transition —
     * and since a transition is the only thing that cancels, one token per segment *is* the key set.
     * Per-cue tokens would let a caller cancel half a segment, which is a capability nothing wants and
     * a bug everything could have.
     */
    private var segmentToken: Any? = null

    /** Makes every fired utterance id distinct, so two of the same cue cannot share a focus window. */
    private var fireCounter = 0

    /** Whether this device can vibrate at all — the caller needs it to build [CueSettings]. */
    val hasVibrator: Boolean get() = haptics.available

    /** `NoEngine` until the TTS engine has answered [SpeechSink.prepare]. Never a reason not to probe. */
    val speechAvailability: SpeechAvailability get() = speech.availability

    var settings: CueSettings = CueSettings.Silent
        private set

    /**
     * Arms the channels for this session.
     *
     * **[wantsSpeech] is deliberately not [CueSettings.speech], and the difference is a deadlock.**
     * `armCues` can only set `speech = true` once availability is `Available`; availability is only
     * ever resolved inside the TTS init callback; that callback only runs from [SpeechSink.prepare];
     * and prepare used to be called only when `settings.speech` was already true. Nothing could ever
     * make the first move, so §D.6's whole channel — 半分 / 最後の巡 / 時間切れ / 終わり — and
     * `DECISIONS.md` §Q2's TalkBack auto-enable were dead on arrival. **We probe on intent.** The user
     * wanting speech is what binds the engine; what the engine then reports decides whether anything is
     * spoken, and it arrives through [onSpeechAvailabilityChanged] so the owner can re-arm.
     *
     * Probing here rather than in the constructor still holds: a `TextToSpeech` binds to another
     * process, and a launcher whose user never turns speech on should never hold that binding.
     */
    fun arm(settings: CueSettings, wantsSpeech: Boolean) {
        this.settings = settings
        if (wantsSpeech) speech.prepare()
    }

    /**
     * Points the whole channel at another language: the phrases, and the voice that reads them.
     *
     * **A field on the engine rather than an argument threaded through [enterSegment] and [fire].** The
     * two are not equivalent. A language change has to reach the *voice* as well as the words, and the
     * voice is a bound `TextToSpeech` in another process that must be re-probed — which is an event,
     * not a parameter. Passing [Strings] per call would have moved the words on the next segment and
     * left the voice on the old language until something happened to re-bind it.
     *
     * Re-probing may find no voice for the new language, which is the failure this whole change exists
     * to make visible: a Japanese-market device almost always has a Japanese voice and may well have no
     * English one. That answer arrives through [onSpeechAvailabilityChanged], the owner re-arms, and
     * §D.6's silent fallback to tones takes it from there. Nothing prompts for a download.
     *
     * The current segment's schedule is **not** recomputed. Its cues were placed on the segment's clock
     * when it was entered and re-deriving them here would re-fire the ones already past; the next
     * segment picks up the new language, which is at most one station away.
     */
    fun setLanguage(strings: Strings) {
        if (this.strings.lang == strings.lang) return
        this.strings = strings
        speech.setLanguage(strings.lang)
    }

    /**
     * Arms from the in-force preferences, resolving [CueSettings] against what this device can do.
     *
     * The overload that call sites should prefer: it reads `wantsSpeech` off the same object `armCues`
     * reads, so the two cannot be passed out of step, and it is the call to repeat from
     * [onSpeechAvailabilityChanged] once the engine has answered.
     */
    fun arm(prefs: EffectiveGymPreferences) {
        arm(armCues(prefs, speechAvailability, hasVibrator), wantsSpeech = prefs.speech)
    }

    /**
     * Cancels the previous segment's cues and schedules this one's.
     *
     * @param elapsedInSegmentMs where in the segment we are landing. Zero for a normal transition;
     *   non-zero for a resume, a seek, or `ON_START` after the frontier moved while backgrounded
     *   (§E.5). Cues already past are dropped rather than fired late — a 半分 that arrives after the
     *   station ended is worse than no cue, and this is the only place that can know.
     *
     * ＋二十秒 re-enters through here too, with the segment's new `plannedMs`: §D.1 says the schedule
     * is recomputed after `extend()`, because twenty seconds moves the 3-2-1 and a patched-up pending
     * post is how it ends up firing in the wrong place.
     */
    fun enterSegment(segment: CueSegment, elapsedInSegmentMs: Long = 0L) {
        cancelPending()
        if (settings == CueSettings.Silent) return

        val token = Any()
        segmentToken = token

        // Distinct per *entry*, not merely per segment: re-entering ordinal 4 (a ◁ restart, a ＋二十秒
        // re-schedule) would otherwise reuse "INTERVAL_END@s4" and ask the speech channel to open a
        // second window under an id it already holds.
        val key = "s${segment.ordinal}#${fireCounter++}"

        val schedule = cueSchedule(segment, strings)
        for (item in schedule) {
            val delay = item.atMs - elapsedInSegmentMs
            if (delay < 0L) continue
            poster.post(token, delay) { play(item.cue, item.speech, key) }
        }

        // Focus windows are posted separately from the cues they cover, because §D.4's merging means
        // one window can span several cues and the open must not be re-requested per cue.
        //
        // ONE post, not two. The close is issued by the open, so it lives on the tone sink's token and
        // survives the transition that cancels `token` — see this class's KDoc.
        for (window in focusWindows(schedule, settings.tones)) {
            val closeIn = window.closeAtMs - elapsedInSegmentMs
            if (closeIn <= 0L) continue
            val openIn = (window.openAtMs - elapsedInSegmentMs).coerceAtLeast(0L)
            poster.post(token, openIn) {
                tones.openWindow()
                tones.closeWindow(closeIn - openIn)
            }
        }
    }

    /**
     * Fires a cue that no schedule could have predicted — 済, ＋二十秒, a first ◁, an EMOM fail-out, an
     * AMRAP cap, the completion ceremony.
     *
     * These carry their own focus window inline (open now, close after the row's tail) rather than
     * going through the merge, because there is nothing to merge with: by definition we learned about
     * the event at the moment it happened.
     */
    fun fire(cue: Cue, speechText: String? = null) {
        if (settings == CueSettings.Silent) return
        val span = focusSpanFor(cue, 0L)
        if (settings.tones && cue.tone != null && span != null) {
            tones.openWindow()
            tones.closeWindow(span.closeAtMs.coerceAtLeast(0L))
        }
        play(cue, speechText ?: cue.speech(strings), key = "f${fireCounter++}")
    }

    /**
     * Applies one row of §D.7's disarm matrix.
     *
     * The decision itself is [disarmPlan], which is pure and has a test per cell. This method only
     * carries it out — which is the split that makes the matrix reviewable at all, because the part
     * that can be wrong is the part that has no `Context` in it.
     *
     * **Order at COMPLETE:** call this *before* [fire]`(Cue.SESSION_COMPLETE)`. The row deliberately
     * waits 900ms before dropping the window so the ceremony can finish inside it, and a disarm that
     * arrived after the tone started would be cancelling the thing it was waiting for.
     */
    fun handle(event: CueEvent, state: CueState) {
        val plan = disarmPlan(event, settings, state)

        if (plan.cancelPending) cancelPending()
        // Only the skip row needs the tones cancelled here. Every other row either stops them now —
        // CLOSE_WINDOW at zero, or RELEASE — or is COMPLETE, which is deliberately waiting for a
        // ceremony that this call would otherwise cut off.
        if (plan.cancelPending && plan.tones == ToneDisarm.KEEP) tones.cancelPendingTones()
        if (plan.cancelHaptics) {
            if (plan.cancelHapticsAfterMs > 0L) {
                poster.post(disarmToken, plan.cancelHapticsAfterMs) { haptics.cancel() }
            } else {
                haptics.cancel()
            }
        }
        when (plan.tones) {
            ToneDisarm.KEEP -> Unit
            ToneDisarm.CLOSE_WINDOW ->
                if (plan.toneDelayMs > 0L) poster.post(disarmToken, plan.toneDelayMs) { tones.stopAll() }
                else tones.stopAll()
            ToneDisarm.RELEASE -> tones.release()
        }
        if (plan.stopSpeech) speech.stop()
    }

    /** `onCleared`, and any teardown that is not coming back. Idempotent. */
    fun release() {
        cancelPending()
        poster.cancelAll()
        haptics.cancel()
        tones.release()
        speech.shutdown()
    }

    // ── internals ───────────────────────────────────────────────────────────────────────────────

    /**
     * The disarm matrix's own two delayed calls, on a token of their own.
     *
     * They must not ride a segment token — COMPLETE's waits exist precisely because the session is
     * ending, and the segment they were issued from is the one being left.
     */
    private val disarmToken = Any()

    /** Drops the current segment's whole key set. §D.1's "cancel on any transition, pause, or skip". */
    private fun cancelPending() {
        segmentToken?.let { poster.cancel(it) }
        segmentToken = null
    }

    private fun play(cue: Cue, speechText: String?, key: String) {
        if (settings.haptics) haptics.play(cue)
        if (settings.tones) tones.play(cue.tone)
        if (settings.speech && speechText != null) {
            // The id is per cue AND per entry: a flushed utterance's terminal callback must be
            // distinguishable from the one that replaced it, or the focus window count drifts and the
            // duck never lifts.
            speech.speak(speechText, utteranceId = "${cue.name}@$key")
        }
    }

    companion object {

        /**
         * The device engine — the constructor every call site outside a test should use.
         *
         * A factory rather than a secondary constructor because [GymSpeech] needs the [GymTones]
         * instance at *its* construction (speech opens and closes the tone channel's focus window, per
         * §D.6), and a `this(...)` delegation has nowhere to hold the intermediate value.
         */
        operator fun invoke(
            context: Context,
            strings: Strings = StringsJa,
            onSpeechAvailabilityChanged: (SpeechAvailability) -> Unit = {},
        ): CueEngine {
            val tones = GymTones(context)
            return CueEngine(
                haptics = GymHaptics(context),
                tones = tones,
                speech = GymSpeech(
                    context = context,
                    onWindowOpen = { tones.openWindow() },
                    onWindowClose = { tones.closeWindow(0L) },
                    language = strings.lang,
                ),
                onSpeechAvailabilityChanged = onSpeechAvailabilityChanged,
                strings = strings,
            )
        }
    }
}

/**
 * [CuePoster] on the main looper — the only implementation that ships.
 *
 * `Handler`'s token overload is the reason §D.1's "cancel the whole key set" is one call rather than a
 * map of `Runnable`s the engine would have to keep in step with itself.
 */
class HandlerCuePoster(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : CuePoster {

    override fun post(token: Any, delayMs: Long, action: () -> Unit) {
        handler.postDelayed(action, token, delayMs)
    }

    override fun cancel(token: Any) {
        handler.removeCallbacksAndMessages(token)
    }

    override fun cancelAll() {
        handler.removeCallbacksAndMessages(null)
    }
}
