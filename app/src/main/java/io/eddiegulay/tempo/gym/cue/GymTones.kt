package io.eddiegulay.tempo.gym.cue

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * The speaker half of the cue engine: four generated tones, one synthesized ceremony, and an audio
 * focus policy that exists to *not* be noticed.
 *
 * **Focus is requested per cue window and abandoned as soon as the window closes — never held for the
 * session.** That is the entire design. A twenty-minute duck is not a subtle bug: the user's music
 * goes quiet at 始める and stays quiet until they close the app, and they will blame the music app.
 * `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` is the case Android's own documentation names a fitness prompt
 * as the example of, and a window measured in hundreds of milliseconds is what makes it honest.
 *
 * Windows are **reference counted** rather than merged here. [cueSchedule] already merges the spans it
 * can see ahead — that is where §D.4's "the 3-2-1 and the interval end share one window, open T−3150,
 * close T+450" comes from — but the event-driven cues (a fail-out, a cap, 済) arrive without notice
 * and can land inside a window that is already open. A counter makes the overlap a no-op; a boolean
 * would abandon focus at the first close and leave the second cue playing over an un-ducked track.
 *
 * Caveat, from §D.4 and worth repeating where someone will read it: the system does **not** auto-duck
 * SPEECH content, so cues mix over a podcast rather than duck under it. Expected, not a bug to chase.
 *
 * *Rejected* — a `SoundPool` of pre-rendered clips. It is the obvious way to get pitched tones and it
 * needs asset files, which `00-plan.md` §2 row 12 rules out as a dependency in all but name.
 */
class GymTones(context: Context) : ToneSink {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    private var generator: ToneGenerator? = null
    private var completionTrack: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null

    /** How many cues currently believe they are inside a window. Focus drops only at zero. */
    private var openWindows = 0

    // Two tokens rather than one handler-wide sweep, because §D.7's skip row cancels pending *tones*
    // while explicitly keeping the window: `removeCallbacksAndMessages(null)` would also drop the
    // close that lifts the duck, and the user's music would stay under until the next cue happened to
    // close it.
    private val toneToken = Any()
    private val windowToken = Any()

    private val focusAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * Opens a window at the cue's −150ms, ducking whatever else is playing.
     *
     * **It does not build the `ToneGenerator`.** It used to, as pre-warming, and that was a live
     * `AudioTrack` held by a session with tones switched off entirely — speech's window callbacks land
     * here too, so a speech-only user paid for a generator that would never make a sound. §D.4 names a
     * leaked `ToneGenerator` as the standard way this component becomes a battery bug, which is reason
     * enough not to create one speculatively. [startTone] builds it on the first tone that needs it,
     * catching the construction failure a busy audio HAL can throw, so nothing was lost.
     */
    override fun openWindow() {
        if (openWindows == 0) requestFocus()
        openWindows++
    }

    /**
     * Closes a window [afterMs] from now — the tail §D.2 gives each cue, so the duck ramps back up
     * after the sound rather than under it.
     *
     * **The wait belongs here and not to the caller.** The engine's posts are keyed by segment and a
     * transition cancels that key set, while every window §D.4 merges closes *after* its segment ends;
     * a close posted on the segment's token is therefore deleted by the very transition it was meant to
     * survive, and the duck is never lifted. [windowToken] is untouched by any transition, and only
     * [stopAll] / [release] — both of which zero the count anyway — can drop it.
     */
    override fun closeWindow(afterMs: Long) {
        if (afterMs <= 0L) {
            releaseWindow()
        } else {
            handler.postDelayed({ releaseWindow() }, windowToken, afterMs)
        }
    }

    /**
     * Drops the tones a transition made obsolete without touching the duck.
     *
     * This is §D.7's skip row: the 3-2-1 belonged to a segment the user has left, so its remaining
     * beeps must not arrive over the next one — but the window is kept, because the segment being
     * landed in will want it and a re-request three hundred milliseconds later is the ramp thrash
     * §D.4 exists to avoid.
     */
    override fun cancelPendingTones() {
        handler.removeCallbacksAndMessages(toneToken)
        runCatching { generator?.stopTone() }
        // The completion track is deliberately left alone: it only ever plays on the way into 記録,
        // where §D.7 waits for it rather than cancelling it.
    }

    /**
     * Stops every sound and drops focus immediately, whatever the counter said.
     *
     * This is §D.7's `closeWindow(0)` cell, and it is deliberately not "close one window": a pause
     * that left a count of two would leave the user's music ducked until a segment that will never
     * resume closed the second one.
     */
    override fun stopAll() {
        handler.removeCallbacksAndMessages(toneToken)
        handler.removeCallbacksAndMessages(windowToken)
        runCatching { generator?.stopTone() }
        stopCompletionTrack()
        openWindows = 0
        abandonFocus()
    }

    /** Plays one cue's tone, if it has one. §D.5's fallback is applied here, not by the caller. */
    override fun play(pattern: TonePattern?) {
        when (pattern) {
            null -> return
            is TonePattern.Single -> startTone(pattern.tone, pattern.durationMs)
            is TonePattern.Repeat -> {
                // The count-down's tones are posted from one origin so they cannot drift apart from
                // each other; they still can drift from the haptic, which is exactly why the haptic
                // is one waveform rather than three posts (§D.3).
                for (i in 0 until pattern.count) {
                    val at = i.toLong() * pattern.periodMs
                    if (at == 0L) {
                        startTone(pattern.tone, pattern.durationMs)
                    } else {
                        handler.postDelayed(
                            { startTone(pattern.tone, pattern.durationMs) },
                            toneToken,
                            at,
                        )
                    }
                }
            }
            TonePattern.Completion -> if (!playCompletionTone()) {
                // §D.5's fallback: a descending pair. Honestly close, and it is the only tone the
                // generator has that falls rather than rises.
                startTone(ToneId.NACK, durationMs = 400)
            }
        }
    }

    /**
     * Abandons focus and releases the generator and the ceremony track.
     *
     * `ON_STOP` and `onCleared` both call this. §D.4 is blunt about why: **a leaked `ToneGenerator`
     * holds an `AudioTrack` and is the standard way this component becomes a battery bug.**
     */
    override fun release() {
        stopAll()
        runCatching { generator?.release() }
        generator = null
        completionTrack?.let { runCatching { it.release() } }
        completionTrack = null
    }

    // ── internals ───────────────────────────────────────────────────────────────────────────────

    private fun ensureGenerator() {
        if (generator != null) return
        // STREAM_ALARM, because §D.3 settles that cues are alarm-class: they must survive silent mode,
        // for the same reason the haptics route as USAGE_ALARM. MAX_VOLUME is the platform's own
        // ceiling constant rather than a level this file picked — the user's alarm volume still scales
        // it, which is the control they already know how to reach.
        generator = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }
            .getOrNull()
    }

    private fun startTone(tone: ToneId, durationMs: Int) {
        ensureGenerator()
        runCatching { generator?.startTone(tone.platformTone, durationMs) }
    }

    private fun requestFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(focusAttributes)
            .build()
        focusRequest = request
        runCatching { audioManager.requestAudioFocus(request) }
    }

    private fun releaseWindow() {
        if (openWindows > 0) openWindows--
        if (openWindows == 0) abandonFocus()
    }

    private fun abandonFocus() {
        val request = focusRequest ?: return
        focusRequest = null
        runCatching { audioManager.abandonAudioFocusRequest(request) }
    }

    private fun stopCompletionTrack() {
        val track = completionTrack ?: return
        runCatching { if (track.state == AudioTrack.STATE_INITIALIZED) track.stop() }
    }

    /**
     * The synthesized descending three-note, through a one-shot `MODE_STATIC` track.
     *
     * One-shot: the buffer is written once, played once, and the track is replaced on the next
     * completion rather than reused. A session ends at most once, so pooling would be caching for a
     * cache's sake, and a static track that has already played needs `reloadStaticData()` to play
     * again — a call that has quietly failed on enough devices that it is not worth the saving.
     *
     * @return false when the track could not be built or written, so the caller can fall back.
     */
    private fun playCompletionTone(): Boolean {
        completionTrack?.let { runCatching { it.release() } }
        completionTrack = null

        val samples = CompletionTone.synthesize()
        val byteCount = samples.size * Short.SIZE_BYTES
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(CompletionTone.SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(byteCount)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        }.getOrNull() ?: return false

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { track.release() }
            return false
        }
        val written = runCatching { track.write(samples, 0, samples.size) }.getOrDefault(0)
        if (written < samples.size) {
            runCatching { track.release() }
            return false
        }
        return runCatching {
            track.play()
            completionTrack = track
            true
        }.getOrElse {
            runCatching { track.release() }
            false
        }
    }
}

/**
 * The `ToneGenerator` constant behind each of our four names.
 *
 * The mapping lives here, not on [ToneId], so that the cue table stays Android-free — the same bargain
 * [HapticPattern] makes. There is no fifth entry because the ceremony is synthesized, not generated.
 */
private val ToneId.platformTone: Int
    get() = when (this) {
        ToneId.ACK -> ToneGenerator.TONE_PROP_ACK
        ToneId.BEEP -> ToneGenerator.TONE_PROP_BEEP
        ToneId.BEEP2 -> ToneGenerator.TONE_PROP_BEEP2
        ToneId.NACK -> ToneGenerator.TONE_PROP_NACK
    }
