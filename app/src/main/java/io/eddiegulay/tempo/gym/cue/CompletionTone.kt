package io.eddiegulay.tempo.gym.cue

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The descending three-note that ends a completed session, as arithmetic.
 *
 * `ToneGenerator` has **no pitch control**, so design §10's descending three-note cannot be played
 * with the generator every other cue uses (`00-plan.md` §2 row 12). The two ways out were an audio
 * asset and forty lines of sine — and an asset is a dependency in all but name, which defeats the
 * constraint the tone choice was made under in the first place. So: three sine segments, twenty
 * milliseconds of raised cosine at each end of each, into a `ShortArray`.
 *
 * Everything here is Android-free on purpose. A synthesized tone is the one piece of audio code whose
 * failure is inaudible rather than silent — an envelope that does not reach zero *clicks*, a buffer
 * one sample short *clicks*, an amplitude that wraps *cracks* — and none of those show up on a device
 * as anything more diagnostic than "sounds a bit cheap". They show up here as a failing assertion.
 *
 * *Rejected* — dithering, anti-aliasing, and a stereo buffer. The tone is three pure sines under a
 * kilohertz played once; none of the three would be audible and all three would be code nobody can
 * check by ear.
 */
object CompletionTone {

    /**
     * 44.1 kHz because it is the one rate every Android device is required to support for playback —
     * `AudioTrack` will resample anything else, and a resampler between us and a 440 Hz sine is a
     * source of artefacts we would then have to reason about. Not a design parameter; a floor.
     */
    const val SAMPLE_RATE_HZ = 44_100

    /** §D.5: three 260ms segments at 660 / 550 / 440 Hz. Descending, so it reads as an ending. */
    val FREQUENCIES_HZ = listOf(660.0, 550.0, 440.0)
    const val SEGMENT_MS = 260

    /**
     * §D.5's 20ms raised cosine. Long enough to kill the click of a hard start, short enough that a
     * 260ms note still has an attack rather than a swell.
     */
    const val ENVELOPE_MS = 20

    /**
     * The buffer's real length: 780ms.
     *
     * §D.5 and `00-plan.md` §2 row 12 both describe this as "~900ms", and three times 260 is 780. The
     * approximation is theirs and the arithmetic is ours — the segments are the specified figure, and
     * padding to exactly 900 would mean inventing 120ms of something. The cue's focus window closes at
     * +900ms (§D.2), which covers 780 with room, so nothing downstream depends on the difference.
     */
    const val TOTAL_MS = SEGMENT_MS * 3

    /**
     * The whole tone, one sample per array slot, signed 16-bit, mono.
     *
     * Segments are laid end to end with no gap. They still read as three notes because each one
     * envelopes down to silence and the next envelopes up from it — 40ms of zero between pitches is
     * plainly a boundary to a listener, and a gap would only have added a number the spec does not
     * give.
     */
    fun synthesize(sampleRateHz: Int = SAMPLE_RATE_HZ): ShortArray {
        val segmentSamples = sampleRateHz * SEGMENT_MS / 1000
        val envelopeSamples = sampleRateHz * ENVELOPE_MS / 1000
        val out = ShortArray(segmentSamples * FREQUENCIES_HZ.size)

        var cursor = 0
        for (frequency in FREQUENCIES_HZ) {
            val radiansPerSample = 2.0 * PI * frequency / sampleRateHz
            for (i in 0 until segmentSamples) {
                val value = sin(radiansPerSample * i) * envelope(i, segmentSamples, envelopeSamples)
                // A single sine never exceeds full scale, so there is no headroom factor to pick and
                // no invented gain: the peak is Short.MAX_VALUE by construction. coerceIn guards the
                // one-ULP rounding case rather than a design decision.
                out[cursor + i] = (value * Short.MAX_VALUE)
                    .roundToInt()
                    .coerceIn(-Short.MAX_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            cursor += segmentSamples
        }
        return out
    }

    /**
     * The raised-cosine window at [indexInSegment] — 0.0 at both ends of the segment, 1.0 between.
     *
     * `min(rise, fall)` rather than `rise * fall` so that a segment shorter than two envelopes
     * degrades to a triangle that still starts and ends at zero, instead of a curve that never leaves
     * the noise floor. Nothing in this file produces such a segment; the guard is there because the
     * function is public and the failure mode of getting it wrong is a click, which is not something
     * a caller would think to check for.
     */
    fun envelope(indexInSegment: Int, segmentSamples: Int, envelopeSamples: Int): Double {
        if (envelopeSamples <= 0 || segmentSamples <= 1) return 1.0
        val fromStart = indexInSegment
        val fromEnd = segmentSamples - 1 - indexInSegment
        val rise = raisedCosine(fromStart, envelopeSamples)
        val fall = raisedCosine(fromEnd, envelopeSamples)
        return min(rise, fall)
    }

    private fun raisedCosine(distance: Int, envelopeSamples: Int): Double = when {
        distance <= 0 -> 0.0
        distance >= envelopeSamples -> 1.0
        else -> 0.5 * (1.0 - cos(PI * distance / envelopeSamples))
    }
}
