package io.eddiegulay.tempo.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The house press indication: a faint wash of the theme's own ink, drawn **inside the control's
 * shape**, that soaks in under a fingertip and dries slowly when it lifts.
 *
 * It exists because Tempo never sets a Material 3 `ColorScheme` — the palette travels through
 * [LocalTempoColors] instead — so every un-styled `clickable` fell back to Material's default
 * indication and painted an M3 grey rectangle: a colour from no theme in this app, with hard right
 * angles, on a design whose whole language is soft and brushed. Both faults are fixed here rather
 * than at 79 call sites: [Default] is provided through `LocalIndication` in `TempoApp`, which
 * corrects the colour app-wide in one line, and [Modifier.pressable] corrects the bounds wherever a
 * call site is migrated.
 *
 * ### Why a wash and not a ripple
 *
 * A ripple is a *point* event — it announces where the finger landed, expanding from it. Ink on
 * washi does not do that; it darkens the fibres under the whole area and stops at the edge of the
 * stroke. The whole-shape wash also degrades gracefully: at these alphas a ripple's leading edge
 * would be invisible and only its aftermath would read, which is a ripple that looks broken.
 *
 * *Rejected* — reusing Material's `ripple()` with a `RippleConfiguration` carrying [TempoColors.ink].
 * It fixes the colour and nothing else: the ripple still paints the composable's raw rectangle
 * (`ripple()` is bounded by the layer, not by a shape it is told about), it still expands from the
 * touch point, and it drags Material's press/release timing along with it. Ripple configuration is
 * also experimental in this Compose version, so the "cheap" option would have been the unstable one.
 *
 * ### Why the release is slower than the press
 *
 * Everywhere else in Tempo leaving is faster than arriving — `GymMotion` encodes exactly that, and a
 * page that exits over 90ms while entering over 240ms is the house rule. A press is the one place
 * that rule is inverted, and deliberately: the finger's release is not a departure, it is the moment
 * the ink stops being fed and starts drying. [SoakMillis] in, [LiftMillis] out, both on
 * `LinearOutSlowInEasing` so the wash arrives decelerating and leaves with a long faint tail. A
 * symmetric fade — and worse, an instant one — is what makes a press read as a light switch, which
 * is the complaint this file answers.
 *
 * ### Why so faint
 *
 * Material's pressed state layer sits at 0.10–0.12 alpha. [PaperAlpha] is roughly half that. This is
 * a launcher whose brief is restraint; the wash has to be findable by a finger that is already on
 * the glass, not legible from across a room. At 0.06 over the Paper cream the composite moves about
 * ten sRGB levels — plainly visible in hand, invisible in a screenshot at a glance, which is the
 * intent.
 */
object InkPress {

    /**
     * The corner used when a call site does not name a shape.
     *
     * 14dp is the radius the app's cards and dock buttons already use, and it is a
     * [RoundedCornerShape] rather than a `CircleShape` because most pressables here are rows and
     * cards, not glyph buttons. `CornerBasedShape` scales its corners down when they do not fit, so
     * this default is also safe on a control shorter than 28dp: a 20dp-tall chip presses as a 10dp
     * round, not as a mis-shapen blob.
     *
     * *Rejected* — `RectangleShape` as the default with rounding opt-in. That is the current
     * behaviour and the reason this file exists; the default must be the one that looks right when
     * nobody thought about it.
     */
    val DefaultShape: RoundedCornerShape = RoundedCornerShape(14.dp)

    /** Pressed-wash alpha on the Paper (cream) theme, over [TempoColors.ink]. */
    const val PaperAlpha: Float = 0.06f

    /**
     * Pressed-wash alpha on the Sumi (charcoal) theme — deliberately *below* [PaperAlpha].
     *
     * Sumi's ink is a light bone over a near-black wash, and light added to dark reads stronger than
     * the same proportion of dark added to light. Matching the two numbers would make the dark theme
     * the louder one; 0.05 against 0.06 is what makes them feel like the same gesture.
     */
    const val SumiAlpha: Float = 0.05f

    /** A press is the full wash — the unit the other two states are quoted against. */
    const val PressWeight: Float = 1f

    /**
     * Focus is half a press. It persists — a focused control stays washed until focus moves — so it
     * can afford to be quieter than a touch that lasts 200ms, while still being the only focus cue
     * the app has for hardware keyboards and switch access.
     */
    const val FocusWeight: Float = 0.5f

    /** Hover is a whisper: a mouse or stylus that has not committed to anything yet. */
    const val HoverWeight: Float = 0.35f

    /**
     * States stack — pressing a focused control must visibly *add* ink, or a keyboard user gets no
     * feedback from Enter at all — but they stack up to a ceiling, so a focused, hovered, pressed
     * control cannot reach 1.85× and become the loudest thing on the page.
     */
    const val MaxWeight: Float = 1.5f

    /** Press-in duration: quick enough to feel attached to the finger, slow enough not to snap. */
    const val SoakMillis: Int = 120

    /** Release duration. See the class note: drying is slower than soaking, on purpose. */
    const val LiftMillis: Int = 280

    /** The full-strength wash alpha for the active theme. */
    fun baseAlpha(colors: TempoColors): Float = if (colors.isDark) SumiAlpha else PaperAlpha

    /**
     * Wash alpha for a combination of interaction states, in [0, [baseAlpha] × [MaxWeight]].
     *
     * Returns exactly `0f` when nothing is active — the draw pass tests for that and skips entirely,
     * so an idle control costs one float comparison and no drawing at all.
     */
    fun targetAlpha(
        colors: TempoColors,
        pressed: Boolean,
        focused: Boolean = false,
        hovered: Boolean = false,
    ): Float {
        var weight = 0f
        if (pressed) weight += PressWeight
        if (focused) weight += FocusWeight
        if (hovered) weight += HoverWeight
        if (weight == 0f) return 0f
        return baseAlpha(colors) * weight.coerceAtMost(MaxWeight)
    }

    /**
     * The spec for a wash that is gaining ink ([rising] true) or losing it.
     *
     * Both easings are `LinearOutSlowInEasing`, which is the only easing this app uses; the asymmetry
     * is carried entirely by the durations so there is one fewer thing to get wrong.
     */
    fun spec(rising: Boolean): TweenSpec<Float> =
        if (rising) tween(SoakMillis, easing = LinearOutSlowInEasing)
        else tween(LiftMillis, easing = LinearOutSlowInEasing)

    /**
     * The app-wide instance, provided through `LocalIndication` in `TempoApp`.
     *
     * A single shared object rather than one per call site: [InkPressIndication] reads its colour
     * from the composition at draw time, so nothing about it is theme-specific, and provisioning a
     * constant means a theme switch does not invalidate every `clickable` in the tree.
     */
    val Default: Indication = InkPressIndication(DefaultShape)
}

/**
 * [InkPress] as an [Indication]. Use [Modifier.pressable] instead unless you are attaching it to a
 * foundation modifier this file does not wrap — `toggleable`, `selectable`, a bare
 * `Modifier.indication` — in which case construct one with the same shape you clip with.
 *
 * Equality is by [shape] alone, which matters more than it looks: `clickable` compares the
 * `Indication` it is handed and only rebuilds its node when the comparison fails, so a fresh
 * `InkPressIndication(shape)` allocated on every recomposition costs nothing beyond the allocation.
 * That is what lets [Modifier.pressable] be an ordinary function instead of a `@Composable` one, and
 * so lets it be called from non-composable modifier chains and `remember`ed factories alike.
 */
@Stable
class InkPressIndication(
    private val shape: Shape = InkPress.DefaultShape,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        InkPressNode(interactionSource, shape)

    override fun equals(other: Any?): Boolean =
        this === other || (other is InkPressIndication && other.shape == shape)

    override fun hashCode(): Int = shape.hashCode()
}

/**
 * The drawing half. A `Modifier.Node` — not the deprecated `IndicationInstance` — so the wash costs
 * no composition: `create` is called once per attached control and the animation runs on the node's
 * own scope.
 *
 * The colour is read with [currentValueOf] at draw time rather than captured in the factory. That is
 * what lets one shared [InkPress.Default] serve both themes: the same object draws sumi ink on
 * charcoal and warm ink on cream depending only on where in the tree it is attached.
 */
private class InkPressNode(
    private val interactionSource: InteractionSource,
    private val shape: Shape,
) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    private val wash = Animatable(0f)

    /**
     * Interaction *counts*, not booleans. A control can hold two presses (a second finger) or two
     * focus interactions, and an un-counted flag would clear the wash on the first release while the
     * other finger is still down.
     */
    private var presses = 0
    private var hovers = 0
    private var focuses = 0

    /** The alpha the last interaction change asked for; see [settle] for why it is remembered. */
    private var target = 0f

    // Outlines are cached because `Shape.createOutline` allocates, and draw runs every frame the
    // wash is animating. Size and direction are the only inputs that can change under a live node.
    private var outline: Outline? = null
    private var outlineSize: Size? = null
    private var outlineDirection: LayoutDirection? = null

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses++
                    is PressInteraction.Release, is PressInteraction.Cancel -> presses--
                    is HoverInteraction.Enter -> hovers++
                    is HoverInteraction.Exit -> hovers--
                    is FocusInteraction.Focus -> focuses++
                    is FocusInteraction.Unfocus -> focuses--
                    // Drag, and anything foundation adds later, is not a press.
                    else -> return@collect
                }
                settle()
            }
        }
    }

    /**
     * Animate towards the alpha the current interaction state calls for.
     *
     * Each change gets its own coroutine and `Animatable`'s mutex cancels whatever was running, so a
     * press landing mid-release simply takes over from wherever the wash had got to — no queueing,
     * no jump back to zero.
     *
     * The extra step on the way down is the **flick guard**: a tap can press and release inside 40ms,
     * and fading out from wherever the soak happened to have reached gives the fastest, most
     * confident taps the faintest response — exactly backwards. So a release that interrupts an
     * unfinished soak finishes it first, then dries. The ink always lands before it lifts.
     *
     * *Rejected* — a minimum press duration enforced by delaying the release. It holds the wash at
     * full for a fixed window, which reads as lag on a control that has already launched an app;
     * finishing the soak keeps the motion continuous and adds at most [InkPress.SoakMillis].
     */
    private fun settle() {
        val colors = currentValueOf(LocalTempoColors)
        val from = target
        val to = InkPress.targetAlpha(
            colors = colors,
            pressed = presses > 0,
            focused = focuses > 0,
            hovered = hovers > 0,
        )
        if (to == from) return
        target = to
        coroutineScope.launch {
            if (to > wash.value) {
                wash.animateTo(to, InkPress.spec(rising = true))
            } else {
                if (wash.value < from) wash.animateTo(from, InkPress.spec(rising = true))
                wash.animateTo(to, InkPress.spec(rising = false))
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val alpha = wash.value
        if (alpha <= 0f) return
        drawOutline(
            outline = outline(),
            color = currentValueOf(LocalTempoColors).ink,
            alpha = alpha,
        )
    }

    private fun ContentDrawScope.outline(): Outline {
        val cached = outline
        if (cached != null && outlineSize == size && outlineDirection == layoutDirection) return cached
        return shape.createOutline(size, layoutDirection, this).also {
            outline = it
            outlineSize = size
            outlineDirection = layoutDirection
        }
    }
}

/**
 * Clip to [shape], click, and press with [InkPress] — the one call every pressable control in Tempo
 * should use in place of `Modifier.clickable`.
 *
 * ```
 * Modifier
 *     .background(c.card, shape)
 *     .pressable(shape, role = Role.Button, onClickLabel = s.app.open) { onOpen() }
 * ```
 *
 * Pass the **same shape** you filled or bordered with. The wash draws inside its own outline, so the
 * clip is not what makes the press rounded — it is there so the control's own content and any
 * background applied after this modifier are bounded the same way, and so a migrated call site can
 * drop a separate `.clip(shape)` rather than keeping two sources of truth for one corner.
 *
 * [role] defaults to `null`, matching `clickable`: a wrong role announced by TalkBack is worse than a
 * missing one, and this modifier cannot know whether it is wrapping a button, a tab or a list item.
 * Pass `Role.Button` at the call sites that are buttons — nearly all of them are.
 *
 * *Rejected* — making this `@Composable` so it could read `LocalIndication` and honour an override.
 * The override would be this indication in every case; the cost would be that `pressable` could no
 * longer be used inside a non-composable modifier factory, which several screens build.
 *
 * @param shape the outline the press is drawn inside and the content is clipped to.
 * @param enabled when false, no click and no wash — `clickable` stops emitting interactions.
 * @param onClickLabel the TalkBack verb for the tap ("開く" rather than "activate").
 * @param interactionSource supply one only when the call site also reacts to presses itself (a
 *   scale-settle, say); left null, foundation makes and owns one.
 */
fun Modifier.pressable(
    shape: Shape = InkPress.DefaultShape,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = this
    .clip(shape)
    .clickable(
        interactionSource = interactionSource,
        indication = InkPressIndication(shape),
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick,
    )

/**
 * [pressable] with a long-press, and a double-tap if a call site ever needs one — the replacement for
 * `Modifier.combinedClickable` on app rows, notification rows and routine cards.
 *
 * The `@OptIn` lives here rather than at every call site: `combinedClickable` is still experimental
 * in this Compose version, and one opt-in in the primitive is one place to revisit when it is not.
 *
 * Haptics are **not** performed here. `combinedClickable` does not perform them either, and the call
 * sites that long-press already fire `HapticFeedbackType.LongPress` themselves; doing it in the
 * modifier would need `LocalHapticFeedback` and so a `@Composable` modifier (see [pressable]), and
 * would double up wherever a call site kept its own.
 *
 * @param onLongClickLabel the TalkBack verb for the long press; supply it whenever [onLongClick] is
 *   non-null, or the gesture is invisible to a screen reader.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedPressable(
    shape: Shape = InkPress.DefaultShape,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier = this
    .clip(shape)
    .combinedClickable(
        interactionSource = interactionSource,
        indication = InkPressIndication(shape),
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        onClick = onClick,
    )
