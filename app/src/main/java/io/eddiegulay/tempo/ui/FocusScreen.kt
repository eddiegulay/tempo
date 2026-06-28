package io.eddiegulay.tempo.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho
import io.eddiegulay.tempo.ui.theme.TempoColors
import kotlinx.coroutines.delay

// Classic Pomodoro cadence, in seconds. A long break replaces the short one every fourth focus.
private const val FOCUS_SEC = 25 * 60
private const val SHORT_SEC = 5 * 60
private const val LONG_SEC = 15 * 60
private const val LONG_EVERY = 4

private enum class FocusMode { Clock, Pomodoro }

private enum class PomodoroPhase(val label: String, val durationSec: Int) {
    Focus("集中", FOCUS_SEC),
    ShortBreak("休憩", SHORT_SEC),
    LongBreak("長休憩", LONG_SEC),
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
    val haptics = LocalHapticFeedback.current

    var mode by rememberSaveable { mutableStateOf(FocusMode.Clock) }
    var showSeconds by rememberSaveable { mutableStateOf(false) }
    val pomodoro = rememberPomodoroController()

    // Force landscape + immersive while Focus is on screen; undo on dispose.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = view.context.findActivity()
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activity?.requestedOrientation =
                originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
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
            text = if (mode == FocusMode.Clock) "タップで秒 ・ 長押しでポモドーロ"
            else "タップで開始 / 一時停止 ・ 長押しで時計",
            style = TextStyle(fontFamily = Mincho, fontSize = 12.sp, letterSpacing = 1.sp, color = c.inkFaint),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        )
    }
}

// ----- clock face -----

@Composable
private fun ClockFace(showSeconds: Boolean, colors: TempoColors) {
    val now by rememberSecondTime()
    val text = if (showSeconds) {
        "%02d:%02d:%02d".format(now.hour, now.minute, now.second)
    } else {
        "%02d:%02d".format(now.hour, now.minute)
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
    val minutes = controller.remaining / 60
    val seconds = controller.remaining % 60
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = controller.phase.label,
            style = TextStyle(
                fontFamily = Mincho,
                fontSize = 26.sp,
                letterSpacing = 8.sp,
                color = if (controller.phase == PomodoroPhase.Focus) colors.accent else colors.inkSoft,
            ),
        )
        Spacer(Modifier.height(18.dp))
        FlipClock(
            text = "%02d:%02d".format(minutes, seconds),
            inkColor = colors.ink,
            cardColor = colors.card,
            dividerColor = colors.hair,
            digitSize = 88.sp,
            cardWidth = 88.dp,
            cardHeight = 132.dp,
        )
        Spacer(Modifier.height(20.dp))
        CycleDots(filled = controller.completedFocus % LONG_EVERY, accent = colors.accent, faint = colors.inkFaint)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            ControlLabel(text = "リセット", color = colors.inkSoft, onClick = controller::reset)
            ControlLabel(
                text = if (controller.running) "計測中" else "停止中",
                color = if (controller.running) colors.accent else colors.inkFaint,
                onClick = controller::startPause,
            )
            ControlLabel(text = "スキップ", color = colors.inkSoft, onClick = controller::skip)
        }
    }
}

/** Four dots tracking progress toward the next long break. */
@Composable
private fun CycleDots(filled: Int, accent: Color, faint: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(LONG_EVERY) { i ->
            Box(
                Modifier
                    .size(9.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (i < filled) accent else faint),
            )
        }
    }
}

@Composable
private fun ControlLabel(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = TextStyle(fontFamily = Mincho, fontSize = 16.sp, letterSpacing = 2.sp, color = color),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
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
