package io.eddiegulay.tempo.gym.cue

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi

/**
 * The vibrator, as the cue engine needs it: alarm-class, one call, three shapes.
 *
 * **The API split is the whole point of this class.** `03-player.md` §D.3 and `00-plan.md` §2 row 11
 * correct design §3.6's claim that `VibrationAttributes` is usable from API 31: the *class* landed in
 * API 30, but `vibrate(VibrationEffect, VibrationAttributes)` is only public from **API 33**. At
 * `minSdk 29` that makes both paths mandatory — the `AudioAttributes` overload on 29–32 and the
 * `VibrationAttributes` one on 33+ — and there is no version of this file with only one of them that
 * is correct on every device the app ships to. `VibratorManager` splits at a third level again (API
 * 31), which is why the service lookup has its own guard.
 *
 * Both paths route as `USAGE_ALARM`. That is not a detail: a workout cue must fire while the phone is
 * in silent mode, because the phone is on the floor and the user is face-down in a plank. Routing
 * these as notification-class haptics would make the feature silently useless for anyone who keeps
 * their phone quiet, which is most people who train at home.
 *
 * *Rejected* — branching on `hasAmplitudeControl()`. Where it is false, `createWaveform` degrades to
 * on/off at full strength on its own; §D.3 says do not branch, and two code paths that mostly agree
 * carry more risk than one path that is occasionally blunter.
 *
 * *Rejected* — §D.3's `by lazy` field for the API-33 attributes. A field whose declared type does not
 * exist on the oldest device the app supports is a verification hazard in exchange for skipping one
 * builder allocation, at a cue rate of roughly four per minute. The version-guarded factory below
 * costs nothing measurable and keeps every API-33 type inside a method the platform never resolves
 * below 33.
 */
class GymHaptics(context: Context) : HapticSink {

    /**
     * **The application context, not the one handed in** — matching [GymTones] and [GymSpeech].
     *
     * `SystemVibrator` retains the `Context` it was resolved from. A [CueEngine] is owned by a
     * `ViewModel` and outlives configuration changes, so resolving from an `Activity` — the natural
     * call site — would pin that `Activity` for the whole engine's life and leak one per rotation.
     */
    private val appContext = context.applicationContext

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Probed once. False on a device with no motor, which is how the caller disarms the channel. */
    override val available: Boolean = vibrator.hasVibrator()

    /** Tri-state cache: null until asked, because the query itself does not exist on every device. */
    private var primitivesSupported: Boolean? = null

    /**
     * Plays one cue's haptic. Cues with no haptic column, and a device with no motor, are no-ops.
     *
     * The composition branch is §D.3's optional progressive enhancement, kept optional: where the
     * device advertises `PRIMITIVE_TICK` and `PRIMITIVE_THUD`, the 3-2-1 and the interval end are
     * drawn with the platform's tuned primitives rather than raw millisecond spans, which on a modern
     * actuator is the difference between a tap and a rattle. The waveform path stays as the fallback
     * and is never removed — the spec is explicit that the enhancement must not become the only path,
     * because most of the fleet supports no primitives at all.
     *
     * **§D.3 says "API 30+"; the gate here is 31**, and it is the same class of correction
     * `00-plan.md` §2 row 11 made for `VibrationAttributes`. `areAllPrimitivesSupported` and
     * `PRIMITIVE_TICK` landed in API 30, but `PRIMITIVE_THUD` is API 31 — and its constant is inlined
     * at compile time, so on an API 30 device we would be asking the platform about a primitive id it
     * has never heard of. Gating the whole enhancement at 31 costs one API level of a path that is
     * optional by construction, and buys not having to reason about what an unknown id does.
     */
    override fun play(cue: Cue) {
        val pattern = cue.haptic ?: return
        if (!available) return
        if (playAsComposition(cue)) return
        play(pattern.toEffect())
    }

    /**
     * Stops whatever is running. Called from every row of §D.7 except a skip.
     *
     * The failure this exists to prevent: a 2060ms 3-2-1 waveform outliving the segment it belonged
     * to, so the user pauses at T−2s and the phone counts down to nothing in their hand.
     */
    override fun cancel() {
        if (available) vibrator.cancel()
    }

    private fun play(effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, alarmVibrationAttributes())
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, audioAttributes)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun alarmVibrationAttributes(): VibrationAttributes =
        VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build()

    private fun playAsComposition(cue: Cue): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        if (cue != Cue.COUNT_TICK && cue != Cue.INTERVAL_END) return false
        if (!hasPrimitives()) return false
        play(composeFor(cue))
        return true
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun hasPrimitives(): Boolean = primitivesSupported ?: vibrator.areAllPrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_TICK,
        VibrationEffect.Composition.PRIMITIVE_THUD,
    ).also { primitivesSupported = it }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun composeFor(cue: Cue): VibrationEffect = when (cue) {
        // Three ticks on the cue table's own 0 / +1000 / +2000 grid, so the enhanced path and the
        // waveform path are the same rhythm — a device with primitives must not count down at a
        // different tempo from one without.
        Cue.COUNT_TICK -> VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 0)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 1_000)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 1_000)
            .compose()

        else -> VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 0)
            .compose()
    }
}

/**
 * Turns one row of the cue table into the platform's own type.
 *
 * Kept out of [Cue] so the table stays Android-free and JVM-testable; kept out of [GymHaptics]'s body
 * so the API-level story in that class is not diluted by three constructor calls.
 */
private fun HapticPattern.toEffect(): VibrationEffect = when (this) {
    is HapticPattern.OneShot -> VibrationEffect.createOneShot(durationMs, amplitude)
    is HapticPattern.Waveform ->
        VibrationEffect.createWaveform(timingsMs.toLongArray(), amplitudes.toIntArray(), -1)
    is HapticPattern.Predefined -> VibrationEffect.createPredefined(
        when (effect) {
            PredefinedHaptic.CLICK -> VibrationEffect.EFFECT_CLICK
            PredefinedHaptic.TICK -> VibrationEffect.EFFECT_TICK
        },
    )
}
