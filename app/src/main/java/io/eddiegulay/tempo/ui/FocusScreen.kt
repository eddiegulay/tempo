package io.eddiegulay.tempo.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewTreeObserver
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.eddiegulay.tempo.i18n.Lang
import io.eddiegulay.tempo.i18n.LocalStrings
import io.eddiegulay.tempo.i18n.Strings
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoShapes
import io.eddiegulay.tempo.ui.theme.pressable
import io.eddiegulay.tempo.ui.theme.TempoColors
import java.util.Locale
import kotlinx.coroutines.delay

// Classic Pomodoro cadence, in seconds. A long break replaces the short one every fourth focus.
private const val FOCUS_SEC = 25 * 60
private const val SHORT_SEC = 5 * 60
private const val LONG_SEC = 15 * 60
private const val LONG_EVERY = 4

private enum class FocusMode { Clock, Pomodoro }

private enum class PomodoroPhase(val durationSec: Int) {
    Focus(FOCUS_SEC),
    ShortBreak(SHORT_SEC),
    LongBreak(LONG_SEC),
}

/**
 * The word a user reads for this phase.
 *
 * An extension rather than a constructor argument, for the same reason `Tier.label(Strings)` is one:
 * a label baked into an enum constant is resolved at class-init and cannot be re-resolved when the
 * user changes language, so the clock would keep saying 集中 in an English app until the process died
 * (`.planning/i18n/DECISIONS.md` §L3).
 */
private fun PomodoroPhase.label(strings: Strings): String = when (this) {
    PomodoroPhase.Focus -> strings.focus.phaseFocus
    PomodoroPhase.ShortBreak -> strings.focus.phaseShortBreak
    PomodoroPhase.LongBreak -> strings.focus.phaseLongBreak
}

/**
 * The full-screen Focus surface: a landscape split-flap clock that doubles as a Pomodoro timer.
 *
 * Gestures keep it bare — a single **tap** toggles seconds (clock) or start/pause (pomodoro), a
 * **long-press** flips between the two modes. Entering locks the Activity to landscape and hides the
 * system bars; both are restored when this composable leaves the composition (Back or a HOME press,
 * which routes through [io.eddiegulay.tempo.LauncherViewModel.resetToHome]).
 */
@Composable
fun FocusScreen(modifier: Modifier = Modifier) {
    val c = LocalTempoColors.current
    val s = LocalStrings.current
    val haptics = LocalHapticFeedback.current

    var mode by rememberSaveable { mutableStateOf(FocusMode.Clock) }
    var showSeconds by rememberSaveable { mutableStateOf(false) }
    val pomodoro = rememberPomodoroController()

    // While Focus is on screen: lock landscape and hold the system bars hidden; both are undone on
    // dispose. Keep-awake is NOT handled here — it's driven from TempoApp keyed on the Focus screen
    // state, so the wake flag is cleared even when this composable never gets to dispose (e.g. HOME
    // press while Tempo isn't the default launcher). See the DisposableEffect in TempoApp.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = view.context.findActivity()
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        // BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE: a deliberate swipe peeks the bars, then they auto-hide.
        fun hideBars() = controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        hideBars()

        // hide() is silently reverted whenever the window loses then regains focus — the notification
        // shade, a toast, a permission prompt, or returning from another app all do this. Re-hide on
        // every focus regain so the status bar stays actively hidden instead of creeping back.
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) hideBars()
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)

        onDispose {
            view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation =
                originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (mode == FocusMode.Clock) showSeconds = !showSeconds
                        else pomodoro.startPause()
                    },
                    onLongPress = {
                        mode = if (mode == FocusMode.Clock) FocusMode.Pomodoro else FocusMode.Clock
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when (mode) {
            FocusMode.Clock -> ClockFace(showSeconds = showSeconds, colors = c)
            FocusMode.Pomodoro -> PomodoroFace(controller = pomodoro, colors = c)
        }

        Text(
            text = if (mode == FocusMode.Clock) s.focus.hintClock else s.focus.hintPomodoro,
            style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 1.sp, color = c.inkFaint),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        )
    }
}

// ----- clock face -----

@Composable
private fun ClockFace(showSeconds: Boolean, colors: TempoColors) {
    val now by rememberSecondTime()
    // Locale.ROOT: String.format without one uses the default locale, which renders Arabic-Indic
    // digits on an ar-* device and would feed FlipClock characters it has no cards for (§L8).
    val text = if (showSeconds) {
        "%02d:%02d:%02d".format(Locale.ROOT, now.hour, now.minute, now.second)
    } else {
        "%02d:%02d".format(Locale.ROOT, now.hour, now.minute)
    }
    FlipClock(
        text = text,
        inkColor = colors.ink,
        cardColor = colors.card,
        dividerColor = colors.hair,
        digitSize = 96.sp,
        cardWidth = 96.dp,
        cardHeight = 144.dp,
    )
}

// ----- pomodoro face -----

@Composable
private fun PomodoroFace(controller: PomodoroController, colors: TempoColors) {
    val s = LocalStrings.current
    val minutes = controller.remaining / 60
    val seconds = controller.remaining % 60
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = controller.phase.label(s),
            style = TextStyle(
                fontFamily = Mincho,
                fontSize = 26.sp,
                // 8.sp on 26.sp is 31% tracking — standard CJK heading typesetting, and tuned for a
                // two-glyph word where the space between glyphs is the only rhythm there is. Latin
                // already has spaces: at 31% "Focus" stops being a word and becomes five letters.
                // The tracking is a property of the script, not of the style, so it is switched here
                // rather than deleted — Japanese still needs every bit of it.
                letterSpacing = if (s.lang == Lang.Ja) 8.sp else 2.sp,
                color = if (controller.phase == PomodoroPhase.Focus) colors.accent else colors.inkSoft,
            ),
        )
        Spacer(Modifier.height(18.dp))
        FlipClock(
            text = "%02d:%02d".format(Locale.ROOT, minutes, seconds),
            inkColor = colors.ink,
            cardColor = colors.card,
            dividerColor = colors.hair,
            digitSize = 88.sp,
            cardWidth = 88.dp,
            cardHeight = 132.dp,
        )
        Spacer(Modifier.height(20.dp))
        // Focus keeps CycleDots' default 9.dp / 12.dp geometry — the shared component was extracted
        // for the session player, not to restyle this face.
        CycleDots(
            total = LONG_EVERY,
            filled = controller.completedFocus % LONG_EVERY,
            filledColor = colors.accent,
            pendingColor = colors.inkFaint,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            ControlLabel(text = s.focus.controlReset, color = colors.inkSoft, onClick = controller::reset)
            ControlLabel(
                text = if (controller.running) s.focus.controlRunning else s.focus.controlPaused,
                color = if (controller.running) colors.accent else colors.inkFaint,
                onClick = controller::startPause,
            )
            ControlLabel(text = s.focus.controlSkip, color = colors.inkSoft, onClick = controller::skip)
        }
    }
}

/**
 * One Pomodoro control: reset, run/pause, skip.
 *
 * These are the only three controls on the Focus surface, and until now they were the only three
 * *invisible* ones: a raw `pointerInput` publishes no click action, so a screen reader could read the
 * words and never learn they did anything, and at 16sp plus 6dp of padding they were a 33dp target on
 * a page read from across a desk. They are real buttons now — `pressable` gives them the click
 * semantics, a lozenge wash and the 48dp floor. It also carried a `clip(RoundedCornerShape(8.dp))`
 * that clipped nothing, which is the fossil of an indication that never arrived.
 *
 * A click modifier was avoidable here only while the alternative was Material's grey rectangle, which
 * on this near-empty landscape page would have been the loudest thing on screen; the ink wash is not
 * that, and a control the user is meant to find in a dim room should answer the finger that finds it.
 *
 * The surrounding surface keeps its `pointerInput`: a child click modifier consumes the press inside
 * its own bounds, so tapping a label no longer also toggles the timer behind it, and a long-press
 * anywhere else still flips modes.
 */
@Composable
private fun ControlLabel(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .pressable(TempoShapes.Word, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, letterSpacing = 2.sp, color = color),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

// ----- pomodoro state -----

/** Mutable Pomodoro state with a once-per-second ticker that advances phases and auto-pauses. */
private class PomodoroController {
    var phase by mutableStateOf(PomodoroPhase.Focus)
    var remaining by mutableIntStateOf(PomodoroPhase.Focus.durationSec)
    var running by mutableStateOf(false)
    var completedFocus by mutableIntStateOf(0)

    fun startPause() { running = !running }

    fun reset() {
        running = false
        remaining = phase.durationSec
    }

    /** Move to the next phase and stay paused, so the user starts each block deliberately. */
    fun advance() {
        running = false
        phase = when (phase) {
            PomodoroPhase.Focus -> {
                completedFocus += 1
                if (completedFocus % LONG_EVERY == 0) PomodoroPhase.LongBreak else PomodoroPhase.ShortBreak
            }
            PomodoroPhase.ShortBreak, PomodoroPhase.LongBreak -> PomodoroPhase.Focus
        }
        remaining = phase.durationSec
    }

    fun skip() = advance()
}

@Composable
private fun rememberPomodoroController(): PomodoroController {
    val controller = remember { PomodoroController() }
    val haptics = LocalHapticFeedback.current
    // Re-launched whenever running flips. While running, decrement each second; on reaching zero,
    // advance to the next phase (which sets running=false, ending this loop) and buzz once.
    LaunchedEffect(controller.running) {
        if (!controller.running) return@LaunchedEffect
        while (controller.remaining > 0) {
            delay(1_000L)
            controller.remaining -= 1
        }
        controller.advance()
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    return controller
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
