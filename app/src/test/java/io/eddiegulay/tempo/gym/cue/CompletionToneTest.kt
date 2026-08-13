package io.eddiegulay.tempo.gym.cue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The synthesized ending, checked as arithmetic because it cannot be checked by ear.
 *
 * Every defect a hand-rolled tone can have sounds like the same thing on a device — "a bit cheap" —
 * and is a distinct, testable mistake here: an envelope that does not reach zero **clicks**, a buffer
 * whose segments are the wrong length plays the wrong intervals, and an amplitude that overflows a
 * `Short` wraps to the opposite rail and **cracks**. This is the whole reason `00-plan.md` §2 row 12
 * could reject an audio asset without accepting a quality regression: a library-free tone that is
 * pure arithmetic is a tone a test can hear.
 */
class CompletionToneTest {

    private val sampleRate = CompletionTone.SAMPLE_RATE_HZ
    private val segmentSamples = sampleRate * CompletionTone.SEGMENT_MS / 1000
    private val envelopeSamples = sampleRate * CompletionTone.ENVELOPE_MS / 1000

    @Test
    fun `the buffer is three segments long, and no longer`() {
        // 3 x 260ms = 780ms. §D.5 calls it "~900ms"; the segments are the specified figure and the
        // approximation is the spec's. Padding to exactly 900 would mean inventing 120ms of something.
        assertEquals(segmentSamples * 3, CompletionTone.synthesize().size)
        assertEquals(780, CompletionTone.TOTAL_MS)
    }

    @Test
    fun `it starts in silence and ends in silence`() {
        // The 20ms raised cosine at each end exists for exactly this. A buffer that starts at full
        // amplitude clicks, and the click is louder than the note.
        val samples = CompletionTone.synthesize()
        assertEquals(0.toShort(), samples.first())
        assertEquals(0.toShort(), samples.last())
    }

    @Test
    fun `each note lands in silence before the next one starts`() {
        // Segments are laid end to end with no gap; they still read as three notes because each one
        // envelopes down to zero and the next envelopes up from it. If that were not true, the tone
        // would be one glissando with two clicks in it.
        val samples = CompletionTone.synthesize()
        assertEquals(0.toShort(), samples[segmentSamples - 1])
        assertEquals(0.toShort(), samples[segmentSamples])
        assertEquals(0.toShort(), samples[segmentSamples * 2 - 1])
        assertEquals(0.toShort(), samples[segmentSamples * 2])
    }

    @Test
    fun `no sample clips`() {
        // Short.MIN_VALUE is what a wrapped +32768 becomes, so its absence is the real overflow check;
        // MAX_VALUE alone would pass on a buffer that had wrapped every peak.
        val samples = CompletionTone.synthesize()
        assertTrue(samples.none { it == Short.MIN_VALUE })
        assertTrue(samples.all { abs(it.toInt()) <= Short.MAX_VALUE.toInt() })
    }

    @Test
    fun `it is loud enough to be a ceremony`() {
        // The counterpart to the clipping check: a buffer of zeroes passes every assertion above.
        // A single sine reaches full scale by construction, so anything much under it means the
        // envelope never opened.
        val peak = CompletionTone.synthesize().maxOf { abs(it.toInt()) }
        assertTrue("peak was $peak", peak > 32_000)
    }

    @Test
    fun `the three notes descend`() {
        // Descending is what makes it read as an ending rather than an alert. §D.5: 660 / 550 / 440.
        assertEquals(listOf(660.0, 550.0, 440.0), CompletionTone.FREQUENCIES_HZ)
        assertEquals(
            CompletionTone.FREQUENCIES_HZ.sortedDescending(),
            CompletionTone.FREQUENCIES_HZ,
        )
    }

    @Test
    fun `the envelope opens from zero and closes to zero`() {
        assertEquals(0.0, CompletionTone.envelope(0, segmentSamples, envelopeSamples), 0.0)
        assertEquals(
            0.0,
            CompletionTone.envelope(segmentSamples - 1, segmentSamples, envelopeSamples),
            0.0,
        )
    }

    @Test
    fun `the envelope is fully open through the body of the note`() {
        assertEquals(1.0, CompletionTone.envelope(segmentSamples / 2, segmentSamples, envelopeSamples), 0.0)
        assertEquals(1.0, CompletionTone.envelope(envelopeSamples, segmentSamples, envelopeSamples), 0.0)
    }

    @Test
    fun `the envelope is a raised cosine, not a straight line`() {
        // Half way up the attack a raised cosine is at exactly 0.5, and its slope at both ends is
        // zero — which is the property that removes the click. A linear ramp would also read 0.5 here,
        // so the quarter point is what tells them apart: a cosine is well below the line there.
        val half = CompletionTone.envelope(envelopeSamples / 2, segmentSamples, envelopeSamples)
        assertEquals(0.5, half, 0.001)
        val quarter = CompletionTone.envelope(envelopeSamples / 4, segmentSamples, envelopeSamples)
        assertTrue("quarter was $quarter", quarter < 0.25)
    }

    @Test
    fun `the envelope never exceeds one`() {
        for (i in 0 until segmentSamples step 97) {
            val value = CompletionTone.envelope(i, segmentSamples, envelopeSamples)
            assertTrue("index $i gave $value", value in 0.0..1.0)
        }
    }

    @Test
    fun `a segment shorter than two envelopes still starts and ends at zero`() {
        // Nothing in this file produces such a segment. The guard is there because the function is
        // public and its failure mode is a click, which is not something a caller thinks to check.
        val short = 100
        assertEquals(0.0, CompletionTone.envelope(0, short, envelopeSamples), 0.0)
        assertEquals(0.0, CompletionTone.envelope(short - 1, short, envelopeSamples), 0.0)
        assertTrue(CompletionTone.envelope(short / 2, short, envelopeSamples) > 0.0)
        assertTrue(CompletionTone.envelope(short / 2, short, envelopeSamples) < 1.0)
    }

    @Test
    fun `a degenerate envelope width leaves the note untouched rather than silent`() {
        assertEquals(1.0, CompletionTone.envelope(0, segmentSamples, envelopeSamples = 0), 0.0)
    }

    @Test
    fun `a different sample rate scales the buffer rather than the tone`() {
        // The rate is a platform floor, not a design parameter, so changing it must change only how
        // many samples the same 780ms is made of.
        val samples = CompletionTone.synthesize(sampleRateHz = 16_000)
        assertEquals(16_000 * CompletionTone.SEGMENT_MS / 1000 * 3, samples.size)
        assertEquals(0.toShort(), samples.first())
        assertEquals(0.toShort(), samples.last())
    }
}
