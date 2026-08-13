package io.eddiegulay.tempo.ui.gym.session

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.session.RestKind
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.ui.Enso
import io.eddiegulay.tempo.ui.faultCopy
import io.eddiegulay.tempo.ui.theme.Gothic
import io.eddiegulay.tempo.ui.theme.InkPressIndication
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes
import io.eddiegulay.tempo.ui.theme.pressable

/*
 * The live half of the session player: which of the five pages is on screen, and the chrome all of
 * them share.
 *
 * **The chrome is shared because §3.1's argument is that it must be pixel-stable.** WORK, REPS and
 * REST are one layout with three bodies — the ensō at the same centre, the counter on the same line,
 * the controls at the same height — so that nothing jumps when a station becomes a rest. Three pages
 * each drawing their own ✕, hairline and bottom bar would agree on the day they were written and
 * drift by 2.dp within a month, and the drift would only ever be visible *during* a transition, which
 * is the one moment nobody screenshots.
 */

/**
 * Whether tweens run at all — §A PREPARE edge case 1 and §A COMPLETE edge case 1.
 *
 * `ANIMATOR_DURATION_SCALE == 0` is an accessibility setting and a user who set it is telling every
 * app that motion is unwelcome. Compose's own animations honour it, but only by completing on their
 * first frame, which is exactly what is wanted here: the phase cross-fade must **draw its end state
 * immediately**, not fade at some other speed. Read once at composition — it is a system setting, and
 * a value re-read every frame would be a `ContentResolver` call twenty times a second.
 *
 * The ensō is unaffected either way. It is a value redraw driven by `stateAt`, never an `Animatable`,
 * which is the whole reason a depleting ring survives a doze.
 */
internal val LocalGymAnimations = staticCompositionLocalOf { true }

/**
 * The largest a hero numeral may be on this screen — §A WORK edge case 7.
 *
 * `min(88.sp, availableWidth / 4.2)`, measured across the **page**, not across the ring: the ring is
 * 220.dp and dividing that would cap every numeral at half the size the mock draws. 4.2 is four
 * glyphs plus slack for the colon, and `Dp.toSp()` is what makes the cap honour the font scale — at
 * scale 2.0 the same width buys half as many sp, which is precisely the case the rule exists for.
 *
 * `softWrap = false` goes with it at every call site. **The numeral must never wrap**; a countdown
 * that breaks across two lines is worse than one that is slightly small.
 */
internal val LocalHeroCap = staticCompositionLocalOf { 88.sp }

/** The base size a page asks for, reduced to whatever fits (see [LocalHeroCap]). */
@Composable
internal fun heroSize(base: TextUnit): TextUnit {
    val cap = LocalHeroCap.current
    return if (base.value <= cap.value) base else cap
}

/**
 * Which body is on screen. The cross-fade's identity, and deliberately **not** [Phase].
 *
 * 支度 is not a phase of its own once a session is under way: the three seconds back in from a long
 * pause render over whatever segment the session is resuming into (§C.1 row 19), so the body has to be
 * chosen from [SessionUiState.prepare] rather than from `segment.phase`.
 */
private enum class PlayerBody { Prepare, Work, Reps, Rest }

private fun bodyOf(state: SessionUiState): PlayerBody = when {
    state.prepare != null || state.phase == Phase.PREPARE -> PlayerBody.Prepare
    state.phase == Phase.REST -> PlayerBody.Rest
    state.phase == Phase.REPS -> PlayerBody.Reps
    else -> PlayerBody.Work
}

/**
 * The live player: one of five pages, plus the quit sheet when it is open.
 *
 * `03-player.md` §A gives 休止 and 鍛錬を終えますか different treatment and this is where that shows.
 * **休止 replaces the body and does not fade into it** — §A PAUSED says the ensō freezes and "the
 * freeze *is* the confirmation and there is no animation", so a 180ms cross-fade on ┃┃ would animate
 * the one moment specified not to. The quit sheet, by contrast, is drawn **over** the phase, because
 * the session underneath is still the thing being decided about.
 *
 * The cross-fade keeps each outgoing body's **final frame** rather than re-rendering it against the
 * new segment's numbers. Compose hands the content only its key, so without the little cache below,
 * the fading-out 運動 body would spend 180ms drawing the *rest's* countdown under 運動's layout —
 * a glitch that is invisible in a screenshot and obvious in the hand.
 */
@Composable
fun LivePlayer(state: SessionUiState, actions: SessionActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animations = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) != 0f
    }

    val lastFrames = remember { HashMap<PlayerBody, SessionUiState>() }
    val body = bodyOf(state)
    lastFrames[body] = state

    // Which screen the quit sheet is covering. `SessionOverlayUi.Quit` cannot say — the machine keeps
    // `pausedBeforeSheet` for its own row 21 and does not publish it — but the sheet must not change
    // what is behind it: ┃┃ then ✕ then つづける returns to 休止 (row 21 again), and a page that
    // flipped to 運動 under the sheet and back would animate a phase change that never happened.
    val memo = remember { OverlayMemo() }
    if (state.overlay !is SessionOverlayUi.Quit) memo.lastPaused = state.overlay as? SessionOverlayUi.Paused
    val paused = state.overlay as? SessionOverlayUi.Paused
        ?: memo.lastPaused.takeIf { state.overlay is SessionOverlayUi.Quit }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val cap = with(LocalDensity.current) { maxWidth.toSp() } / 4.2f
        CompositionLocalProvider(
            LocalGymAnimations provides animations,
            LocalHeroCap provides if (cap.value < 88f) cap else 88.sp,
        ) {
            if (paused != null) {
                PausedPage(state, paused, actions)
            } else {
                Crossfade(
                    targetState = body,
                    animationSpec = tween(
                        durationMillis = if (animations) PHASE_SWAP_MS else 0,
                        easing = LinearOutSlowInEasing,
                    ),
                    label = "phase",
                ) { key ->
                    val frame = if (key == body) state else lastFrames[key] ?: state
                    when (key) {
                        PlayerBody.Prepare -> PreparePage(frame, actions)
                        PlayerBody.Work -> WorkPage(frame, actions)
                        PlayerBody.Reps -> RepsPage(frame, actions)
                        PlayerBody.Rest -> RestPage(frame, actions)
                    }
                }
            }

            (state.overlay as? SessionOverlayUi.Quit)?.let { quit ->
                QuitSheet(state, quit, actions)
            }
        }
    }
}

/** §A PREPARE state `Zero`: "the 180ms phase-swap cross-fade has already started". */
private const val PHASE_SWAP_MS = 180

/**
 * The one thing [LivePlayer] remembers between frames, and deliberately not snapshot state.
 *
 * It is written and read within a single composition pass — the overlay it derives from is what
 * triggered that pass — so making it observable would only add a recomposition to observe a value
 * that has already been used. Everything else on this screen is a pure function of the host's frame,
 * and this is the one fact the frame does not carry.
 */
private class OverlayMemo {
    var lastPaused: SessionOverlayUi.Paused? = null
}

/** How a control in the bottom bar is offered — or refused (§A REST states 4 and 5). */
internal enum class ControlState {
    /** Drawn, dead, and **audibly** dead: `disabled()` plus a reason, never a silent no-op. */
    Disabled,

    /** Not drawn at all. Reserved for the EMOM remainder, where "only ┃┃ and ✕ remain". */
    Hidden,
    Enabled,
}

/**
 * One of the three glyphs at the foot of the player.
 *
 * [description] is mandatory and is the *sentence*, not the glyph: 「前へ、二回押すと一つ戻る」 is the only
 * way a TalkBack user can discover that ◁ is a double-tap. [disabledReason] replaces it when the
 * control is dead, because "why not" is the useful half then.
 */
internal data class BarControl(
    val glyph: String,
    val description: String,
    val state: ControlState = ControlState.Enabled,
    val disabledReason: String? = null,
    val onClick: () -> Unit = {},
)

/** The bar's three slots, named. The middle one is ┃┃ on every live phase and ▶ on 休止. */
internal data class BarControls(
    val back: BarControl,
    val middle: BarControl,
    val forward: BarControl,
)

/**
 * The chrome every live page draws, and the reason they all look like one screen.
 *
 * Fixed slots throughout: the counter line keeps its height when it has nothing to say, the ring box
 * is 220.dp regardless of what is inside it, and the bottom bar is 96.dp whether its controls are
 * live, dead or hidden. Layout stability beats vertical balance — §A REST edge case 5 says so for the
 * form-cue line and the same rule governs the whole frame.
 *
 * @param announcement the page's one polite live-region utterance, spoken when it *changes*. Every
 *   page passes a value that is constant within a segment, which is what makes "announce at phase
 *   transitions only" (`00-plan.md` §4.1 rule 5) a property of the data rather than a promise.
 */
@Composable
internal fun PlayerFrame(
    state: SessionUiState,
    actions: SessionActions,
    ringColor: Color,
    ringSweep: Float,
    counter: String?,
    controls: BarControls,
    modifier: Modifier = Modifier,
    counterAccent: Boolean = false,
    announcement: String? = null,
    ringContent: @Composable ColumnScope.() -> Unit,
    below: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalTempoColors.current

    Column(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        ) {
            QuitGlyph(onClick = actions::onQuit, modifier = Modifier.align(Alignment.CenterStart))
            if (counter != null) {
                Text(
                    text = counter,
                    modifier = Modifier.align(Alignment.Center),
                    style = TextStyle(
                        fontFamily = Mincho,
                        fontSize = 12.sp,
                        letterSpacing = 3.sp,
                        color = if (counterAccent) c.accent else c.inkFaint,
                    ),
                )
            }
        }

        SessionHairline(state.sessionFraction)

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(RING_DIAMETER), contentAlignment = Alignment.Center) {
                // "Ring colour via animateColorAsState(target, tween(200, LinearOutSlowInEasing)) —
                // **the only colour animation in the feature**" (§A WORK, ensō geometry). The sweep is
                // never animated: it is read from `stateAt`, so a doze or a seek lands it instantly
                // and an `Animatable` would spend 200ms lying about where the session is.
                val ink by animateColorAsState(
                    targetValue = ringColor,
                    animationSpec = tween(
                        durationMillis = if (LocalGymAnimations.current) 200 else 0,
                        easing = LinearOutSlowInEasing,
                    ),
                    label = "ring",
                )
                Enso(
                    color = ink,
                    modifier = Modifier.fillMaxSize().clearAndSetSemantics { },
                    sweepAngle = ringSweep,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    content = ringContent,
                )
            }
            Spacer(Modifier.height(22.dp))
            below()
        }

        state.fault?.let { fault ->
            // A write failed and the session did **not** stop. §A COMPLETE's own precedent for a
            // failed write on a live screen: one accent line rather than a dialog, stated where it
            // happened, dismissed by touching it. There is no もう一度 because the only retry the host
            // exposes is for a finish, which surfaces on the quit sheet instead.
            val message = faultCopy(fault, LocalStrings.current).message
            Text(
                text = message,
                modifier = Modifier
                    .fillMaxWidth()
                    // The line was a full-bleed strip 34.dp tall — under the 48.dp floor, and its
                    // press would have washed corner to corner across the screen. The outer 16.dp
                    // inset makes it an object with edges; the 22.dp the mock asks for between the
                    // screen and the sentence is the two paddings together, so the text has not moved.
                    .padding(horizontal = 16.dp)
                    // A bare accent sentence with no fill and no border, so it takes the lozenge —
                    // `Word` resolves 50% against the shorter side, which on a full-width line is a
                    // long capsule rather than a circle.
                    .playerPressable(TempoShapes.Word, role = Role.Button) { actions.onDismissFault() }
                    .padding(horizontal = 6.dp, vertical = 16.dp)
                    .semantics {
                        contentDescription = message
                        liveRegion = LiveRegionMode.Polite
                    },
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = Mincho, fontSize = 13.sp, letterSpacing = 2.sp, color = c.accent),
            )
        }

        ControlBar(controls)
    }

    Announcement(announcement)
}

/** The ensō's own box. Fixed, per §A: 220.dp with a 3.dp stroke, on every phase. */
internal val RING_DIAMETER = 220.dp

/**
 * An invisible node that speaks once, when its text changes.
 *
 * This is the whole of the player's accessibility strategy for moving values: **no ticking value is a
 * live region** (`00-plan.md` §4.1 rule 5), because per-second announcements are unusable and steal
 * the TTS engine the cues need. A single node, holding a string that only changes at a transition,
 * gives TalkBack one utterance per event and nothing in between.
 */
@Composable
internal fun Announcement(text: String?) {
    if (text.isNullOrBlank()) return
    Box(
        Modifier
            .size(1.dp)
            .clearAndSetSemantics {
                contentDescription = text
                liveRegion = LiveRegionMode.Polite
            },
    )
}

/**
 * The movement you are about to make: its name, what it asks for, and one form cue.
 *
 * **Three fixed slots, and a missing cue leaves its line blank rather than re-centring the block** —
 * §A REST edge case 5, verbatim, and 支度 draws the same block for the same reason. The two screens
 * that show it are the two where the user is *not* moving and is reading; a block that shifted up
 * when a movement happened to have no cue would move the exercise name between one station and the
 * next.
 */
@Composable
internal fun UpcomingBlock(
    name: String?,
    prescription: String?,
    cue: String?,
    modifier: Modifier = Modifier,
) {
    val c = LocalTempoColors.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FixedLine(34.dp) {
            Text(
                text = name.orEmpty(),
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontFamily = Mincho,
                    fontWeight = FontWeight.Medium,
                    fontSize = 26.sp,
                    color = c.ink,
                ),
            )
        }
        FixedLine(22.dp) {
            Text(
                text = prescription.orEmpty(),
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = Gothic, fontSize = 14.sp, color = c.inkSoft),
            )
        }
        FixedLine(18.dp) {
            Text(
                text = cue.orEmpty(),
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = Gothic, fontSize = 12.sp, color = c.inkFaint),
            )
        }
    }
}

@Composable
private fun FixedLine(height: Dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) { content() }
}

/**
 * A sheet over the player: `c.card` on a `c.bgSolid @ 0.62` scrim, rounded 22.dp at the top.
 *
 * Both sheets in the player use it — 鍛錬を終えますか and the rep wheel — and both slide up over 240ms
 * with a fade, which is `03-player.md` §A REPS' figure. (It says "matching `BlockConfirmDialog`";
 * that component is an `AlertDialog` and has no sheet geometry to match, so what is taken from it is
 * the duration and the idiom of a modal that dims what it interrupts, not its layout.)
 *
 * @param dismissable the scrim's tap. False while a write is in flight — §A QUIT_SHEET's `Saving`
 *   makes the rows non-interactive and **does not dismiss the sheet**, and a scrim that still
 *   dismissed would be a fourth outcome nobody chose.
 */
@Composable
internal fun PlayerSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalTempoColors.current
    val millis = if (LocalGymAnimations.current) SHEET_MS else 0
    val spec = tween<Float>(durationMillis = millis, easing = LinearOutSlowInEasing)

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(spec), exit = fadeOut(spec)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.bgSolid.copy(alpha = 0.62f))
                    .pointerInput(dismissable) {
                        detectTapGestures { if (dismissable) onDismiss() }
                    }
                    .clearAndSetSemantics { },
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                animationSpec = tween(millis, easing = LinearOutSlowInEasing),
                initialOffsetY = { it },
            ) + fadeIn(spec),
            exit = slideOutVertically(
                animationSpec = tween(millis, easing = LinearOutSlowInEasing),
                targetOffsetY = { it },
            ) + fadeOut(spec),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(c.card)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                content = content,
            )
        }
    }
}

private const val SHEET_MS = 240

/**
 * [pressable], with the ink laid on **twice** — the player's press, and the only place in Tempo that
 * asks for more than the house one.
 *
 * `InkPress`'s alpha is tuned for a launcher held still in one hand and read at rest: half of
 * Material's press layer, "findable by a finger that is already on the glass". This screen is the one
 * that is read under physical effort — a thumb that is sweaty, a phone propped against a water bottle
 * at arm's length, and a control that must confirm it took the tap before the user commits to the next
 * rep. At the house alpha a ┃┃ that registered and a ┃┃ that missed look the same from a metre away.
 *
 * Two [InkPressIndication]s over one shared [MutableInteractionSource] give the same wash, the same
 * shape and the same asymmetric timing, composited over itself: ~0.116 on Paper against 0.06, which
 * lands just above Material's own 0.10–0.12 and is still the house gesture rather than a new one. The
 * alternative — a second alpha constant in `InkPress` — would have put a "player" special case in a
 * file whose whole argument is that there is one press, and every other screen would have had to learn
 * to ignore it.
 *
 * *Rejected* — scaling the control on press, or swapping its colour. Both are motions this app does
 * not make anywhere else, and §A's layout-stability rule forbids anything in this frame changing size.
 */
@Composable
internal fun Modifier.playerPressable(
    shape: Shape,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .indication(source, InkPressIndication(shape))
        .pressable(
            shape = shape,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            interactionSource = source,
            onClick = onClick,
        )
}

/** ✕ — 48.dp target at start 16 / top 12, `c.inkSoft`. One way out of the player, on every page. */
@Composable
private fun QuitGlyph(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    val label = LocalStrings.current.gymSession.quitGlyphAction
    Box(
        modifier = modifier
            .size(48.dp)
            // `Glyph` and not `Word`: ✕ sits at the *start* of its 48.dp box (the target reaches
            // right, into dead space, so the glyph itself stays on the 16.dp margin the mock draws),
            // and a capsule around an off-centre mark reads as a mis-drawn button. The squircle reads
            // as the target it is.
            .playerPressable(TempoShapes.Glyph, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "✕",
            style = TextStyle(fontFamily = Mincho, fontSize = 17.sp, color = c.inkSoft),
        )
    }
}

/**
 * The session hairline: 1.dp, track `c.hair`, fill `c.inkSoft`.
 *
 * Its denominator comes from the **current** timeline (`stateAt`'s `sessionFraction`), never from the
 * compiled one — an overrun rep slide grows numerator and denominator together, which is what keeps
 * it under 100% (§A REPS edge case 2).
 */
@Composable
private fun SessionHairline(fraction: Float) {
    val c = LocalTempoColors.current
    val safe = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    val label = progressLabel(LocalStrings.current, safe)
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(c.hair)
            .progressSemantics(safe)
            .semantics { contentDescription = label },
    ) {
        Box(Modifier.fillMaxWidth(safe).fillMaxHeight().background(c.inkSoft))
    }
}

/** 96.dp tall, three 64.dp targets, and the middle one never moves whatever the sides are doing. */
@Composable
private fun ControlBar(controls: BarControls) {
    Row(
        modifier = Modifier.fillMaxWidth().height(96.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(controls.back, controls.middle, controls.forward).forEach { control ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ControlGlyph(control)
            }
        }
    }
}

@Composable
private fun ControlGlyph(control: BarControl) {
    val c = LocalTempoColors.current
    if (control.state == ControlState.Hidden) {
        Spacer(Modifier.size(64.dp))
        return
    }
    val enabled = control.state == ControlState.Enabled
    val label = if (enabled) control.description else (control.disabledReason ?: control.description)
    Box(
        modifier = Modifier
            .size(64.dp)
            // The bar's three glyphs are the controls a set is driven with, so they take the whole
            // 64.dp slot as their press rather than the ~24.dp the mark occupies. Disabled, the wash
            // is silent — `pressable` stops emitting interactions — which is what keeps a mandated
            // rest's dead ▷ visibly dead rather than merely unresponsive.
            .playerPressable(TempoShapes.Glyph, enabled = enabled, onClick = control.onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = control.glyph,
            style = TextStyle(
                fontFamily = Mincho,
                fontSize = 20.sp,
                letterSpacing = 1.sp,
                color = if (enabled) c.inkSoft else c.inkFaint,
            ),
        )
    }
}

/**
 * ◁, ┃┃ and ▷ as the four live phases offer them — the one place the bar's rules are written down.
 *
 * - **◁ is dead in 支度.** There is nothing behind a countdown, and §C.4 refuses to step back into one.
 * - **▷ obeys [SessionUiState.canSkipForward], and the two refusals differ.** A mandated rest shows a
 *   dead control with its reason (§A REST state 4: "visibly disabled rather than silently inert"); an
 *   EMOM remainder hides ◁ and ▷ outright, because §A REST state 5 says only ┃┃ and ✕ remain — the
 *   grid is anchored and there is nothing there for either to act on.
 */
@Composable
internal fun liveControls(state: SessionUiState, actions: SessionActions): BarControls {
    val strings = LocalStrings.current
    val s = strings.gymSession
    val emomRemainder = state.restKind == RestKind.EMOM_REMAINDER
    val inPrepare = state.prepare != null || state.phase == Phase.PREPARE
    val back = BarControl(
        glyph = "◁",
        description = if (inPrepare) s.controlsBackPrepare else s.controlsBack,
        state = when {
            emomRemainder -> ControlState.Hidden
            inPrepare -> ControlState.Disabled
            else -> ControlState.Enabled
        },
        disabledReason = if (inPrepare) s.controlsBackPrepare else null,
        onClick = actions::onBackTap,
    )
    val pause = BarControl(
        glyph = "┃┃",
        description = s.controlsPause,
        onClick = actions::onPause,
    )
    val forward = BarControl(
        glyph = "▷",
        description = s.controlsForward,
        state = when {
            emomRemainder -> ControlState.Hidden
            state.canSkipForward -> ControlState.Enabled
            else -> ControlState.Disabled
        },
        disabledReason = skipDisabledDescription(strings, state.restKind),
        onClick = actions::onSkipForward,
    )
    return BarControls(back, pause, forward)
}
