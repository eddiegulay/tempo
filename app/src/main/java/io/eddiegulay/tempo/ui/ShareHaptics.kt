package io.eddiegulay.tempo.ui

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi

/**
 * The share overlay's two touches: a double pulse when the card lifts, a single pulse when it
 * settles. Thirty percent of full amplitude — present, not a workout cue.
 *
 * Same vibrator lookup as the gym cue channel (API 31 manager / 29 service, 33+ attributes /
 * 29–32 AudioAttributes). Different usage: touch / notification, never alarm. Workout cues stay
 * on their own class.
 */
class ShareHaptics(context: Context) {

    private val appContext = context.applicationContext

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun pop() {
        if (!vibrator.hasVibrator()) return
        play(VibrationEffect.createWaveform(SHARE_POP_TIMINGS, SHARE_POP_AMPLITUDES, -1))
    }

    fun close() {
        if (!vibrator.hasVibrator()) return
        play(VibrationEffect.createOneShot(SHARE_CLOSE_MS, SHARE_HAPTIC_AMPLITUDE))
    }

    private fun play(effect: VibrationEffect) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, touchVibrationAttributes())
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, audioAttributes)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun touchVibrationAttributes(): VibrationAttributes =
        VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build()
}

/** Two 28ms pulses at 30% amplitude, 72ms apart. */
internal val SHARE_POP_TIMINGS = longArrayOf(0, 28, 72, 28)
internal val SHARE_POP_AMPLITUDES = intArrayOf(0, SHARE_HAPTIC_AMPLITUDE, 0, SHARE_HAPTIC_AMPLITUDE)

internal const val SHARE_CLOSE_MS = 36L
internal const val SHARE_HAPTIC_AMPLITUDE = 77
