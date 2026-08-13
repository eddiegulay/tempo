package io.eddiegulay.tempo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The four corners Tempo draws, named once.
 *
 * Every pressable control has to hand [pressable] the *same* shape it fills or borders with,
 * or the wash and the fill disagree by a pixel or two at every corner. Before this file that shape was
 * a literal at each call site — `RoundedCornerShape(18.dp)` here, `(14.dp)` there — and a control's
 * press radius agreed with its card radius only for as long as nobody edited one of them. These are
 * the numbers the app already used; what is new is that there is one copy of each.
 *
 * There are four because the app has four kinds of touchable thing, and no more: a card, a row, a
 * word, and a glyph in a box. A fifth entry should be treated as evidence that a control is fighting
 * the design rather than as a gap in this list.
 *
 * *Rejected* — a Material 3 `Shapes` in a `MaterialTheme`. Tempo deliberately provides no `ColorScheme`
 * (see [InkPress]); adopting Material's shape scale would mean adopting its `extraSmall … extraLarge`
 * vocabulary, which names sizes rather than things, and would tempt every Material component the app
 * does use into taking its corners from here too.
 */
object TempoShapes {

    /**
     * A washi card: a notification, a calendar event. The largest radius in the app, because a card is
     * a discrete object floating on the paper and the corner is what separates it from its neighbour.
     */
    val Card: RoundedCornerShape = RoundedCornerShape(18.dp)

    /**
     * A row in a list, or a field in a form — anything that spans the content width and is separated
     * from its neighbours by a hairline rather than by a gap.
     *
     * Deliberately the *same object* as [InkPress.DefaultShape]: a `pressable` that names no shape and
     * a row that names this one must not drift apart, and the row is the case the default was chosen
     * for.
     */
    val Row: RoundedCornerShape = InkPress.DefaultShape

    /**
     * A bare word that can be pressed: a header action, a page's one centred verb, a notification's
     * inline action, a calendar chip. These have no fill and no border of their own, so the press wash
     * is the only shape they ever show — and a lozenge is what a word on paper wants to be, a soft
     * smudge of ink gathered round it rather than a box drawn about it.
     *
     * `percent = 50` resolves against the *shorter* side, so this is a true pill on any control that
     * has met the 48dp floor: a two-character label and a full-width verb read as the same gesture.
     */
    val Word: RoundedCornerShape = RoundedCornerShape(percent = 50)

    /**
     * A glyph centred in its 48dp touch target: the dock tabs and the Search header icons.
     *
     * Not currently handed to a press — those controls carry `indication = null` on purpose, so the
     * dock and the page chrome stay silent under a finger — but it is the corner they clip with, and
     * stating it here keeps the number from being re-guessed if one of them ever grows a wash.
     */
    val Glyph: RoundedCornerShape = RoundedCornerShape(16.dp)
}
