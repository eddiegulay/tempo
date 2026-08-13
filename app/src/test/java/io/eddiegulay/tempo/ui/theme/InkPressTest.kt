package io.eddiegulay.tempo.ui.theme

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The choices behind the press wash, as assertions.
 *
 * None of this is testable by looking at it: an alpha of 0.06 and an alpha of 0.05 are the same
 * number to a reviewer, a 120/280 pair reads as arbitrary, and the one property that actually caused
 * the bug — that the default shape is *not* a rectangle — is invisible in a diff that only shows a
 * `.clickable`. Compose UI tests are not available in this project (no Robolectric, instrumentation
 * out of scope), so the drawing itself is unverified here; everything feeding it is not.
 */
class InkPressTest {

    @Test
    fun `an idle control is exactly transparent, so it draws nothing at all`() {
        // The draw pass short-circuits on `alpha <= 0f`. If the resting state ever came back as a
        // very small non-zero — a weight that summed to 0.0001, say — every idle pressable in the
        // app would draw an invisible outline every frame it was invalidated.
        assertEquals(0f, InkPress.targetAlpha(PaperColors, pressed = false), 0f)
        assertEquals(0f, InkPress.targetAlpha(SumiColors, pressed = false), 0f)
    }

    @Test
    fun `a press is the full wash on both themes`() {
        assertEquals(InkPress.PaperAlpha, InkPress.targetAlpha(PaperColors, pressed = true), 1e-6f)
        assertEquals(InkPress.SumiAlpha, InkPress.targetAlpha(SumiColors, pressed = true), 1e-6f)
    }

    @Test
    fun `sumi presses fainter than paper, because light on dark reads louder`() {
        // Not a typo and not a copy-paste slip: matching the two numbers makes the dark theme the
        // louder of the pair. This assertion is the reason the constants differ.
        assertTrue(InkPress.SumiAlpha < InkPress.PaperAlpha)
    }

    @Test
    fun `the wash stays well under Material's pressed state layer`() {
        // Material sits at 0.10-0.12. The brief for this launcher is restraint; if a later tune-up
        // pushes these past 0.08 it is no longer the quiet thing that was asked for.
        assertTrue(InkPress.PaperAlpha <= 0.08f)
        assertTrue(InkPress.SumiAlpha <= 0.08f)
    }

    @Test
    fun `hover is quieter than focus, which is quieter than a press`() {
        val hovered = InkPress.targetAlpha(PaperColors, pressed = false, hovered = true)
        val focused = InkPress.targetAlpha(PaperColors, pressed = false, focused = true)
        val pressed = InkPress.targetAlpha(PaperColors, pressed = true)
        assertTrue(hovered < focused)
        assertTrue(focused < pressed)
        assertTrue(hovered > 0f)
    }

    @Test
    fun `pressing an already focused control still adds ink`() {
        // A keyboard or switch-access user focuses first and then activates. If the states did not
        // stack, Enter on a focused control would change nothing on screen and the activation would
        // have no feedback whatsoever.
        val focused = InkPress.targetAlpha(PaperColors, pressed = false, focused = true)
        val both = InkPress.targetAlpha(PaperColors, pressed = true, focused = true)
        assertTrue(both > focused)
        assertTrue(both > InkPress.targetAlpha(PaperColors, pressed = true))
    }

    @Test
    fun `stacked states are capped, so nothing can reach three times the wash`() {
        val all = InkPress.targetAlpha(PaperColors, pressed = true, focused = true, hovered = true)
        assertEquals(InkPress.PaperAlpha * InkPress.MaxWeight, all, 1e-6f)
        // The uncapped sum would be 1.85x; the cap is what keeps a pointer device from turning a
        // press into the loudest mark on the page.
        assertTrue(all < InkPress.PaperAlpha * 1.85f)
    }

    @Test
    fun `the release is slower than the press`() {
        // The asymmetry is the whole feel: a press that fades out as fast as it fades in reads as a
        // light switch. This inverts the app's usual "leaving is faster than arriving" on purpose.
        val soak = InkPress.spec(rising = true)
        val lift = InkPress.spec(rising = false)
        assertTrue(lift.durationMillis > soak.durationMillis)
        assertTrue(lift.durationMillis >= soak.durationMillis * 2)
    }

    @Test
    fun `both halves ease the house way and neither is delayed`() {
        // "Everything is LinearOutSlowInEasing. Nothing bounces, overshoots or loops." A delay on
        // the soak would detach the wash from the fingertip.
        val soak = InkPress.spec(rising = true)
        val lift = InkPress.spec(rising = false)
        assertEquals(LinearOutSlowInEasing, soak.easing)
        assertEquals(LinearOutSlowInEasing, lift.easing)
        assertEquals(0, soak.delay)
        assertEquals(0, lift.delay)
    }

    @Test
    fun `a press is quick enough to feel attached to the finger`() {
        assertTrue(InkPress.SoakMillis in 60..160)
    }

    @Test
    fun `the default shape is round, which is the bug this file was written for`() {
        // The complaint was "solid blocky grey right angled". A rectangle default would reintroduce
        // it for every call site that does not name a shape.
        val outline = InkPress.DefaultShape.createOutline(
            size = Size(200f, 96f),
            layoutDirection = LayoutDirection.Ltr,
            density = Density(1f),
        )
        assertTrue(outline is Outline.Rounded)
        val radius = (outline as Outline.Rounded).roundRect.topLeftCornerRadius
        assertEquals(14f, radius.x, 0.01f)
        assertEquals(14f, radius.y, 0.01f)
    }

    @Test
    fun `the default corner scales down rather than deforming a short control`() {
        // A 20dp-tall chip cannot carry a 14dp round on both ends. CornerBasedShape scales the
        // corners to fit, which is why 14dp is safe as a blanket default; asserting it here so the
        // default is not "fixed" by someone who assumes it clips.
        val outline = InkPress.DefaultShape.createOutline(
            size = Size(200f, 20f),
            layoutDirection = LayoutDirection.Ltr,
            density = Density(1f),
        ) as Outline.Rounded
        assertEquals(10f, outline.roundRect.topLeftCornerRadius.x, 0.01f)
    }

    @Test
    fun `two indications for the same shape are equal, so clickable never rebuilds its node`() {
        // pressable() allocates a fresh InkPressIndication on every recomposition. That is only free
        // because clickable compares them; if equality were identity, every recomposition of every
        // pressable control would tear down and rebuild its indication node mid-press.
        assertEquals(InkPressIndication(InkPress.DefaultShape), InkPressIndication(InkPress.DefaultShape))
        assertEquals(
            InkPressIndication(InkPress.DefaultShape).hashCode(),
            InkPressIndication(InkPress.DefaultShape).hashCode(),
        )
        assertNotEquals(
            InkPressIndication(InkPress.DefaultShape),
            InkPressIndication(RoundedCornerShape(4.dp)),
        )
    }
}
