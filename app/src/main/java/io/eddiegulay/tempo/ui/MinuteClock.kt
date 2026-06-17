package io.eddiegulay.tempo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * A lifecycle-aware, minute-aligned clock.
 *
 * The launcher only displays HH:mm and a minute-resolution kanji reading, so we tick once per
 * minute — not per second — and only while the launcher is at least STARTED. When Tempo is
 * backgrounded the loop suspends (no CPU wakeups); on return it re-reads the time immediately.
 *
 * Reading the returned [State] inside a single screen scopes recomposition to that screen, so the
 * dock and other layers don't recompose on each tick.
 */
@Composable
fun rememberMinuteTime(): State<LocalDateTime> {
    val time = remember { mutableStateOf(LocalDateTime.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = LocalDateTime.now()
                time.value = now
                // Sleep until the next minute boundary so the display flips exactly on the minute.
                val millisIntoMinute = now.second * 1_000L + now.nano / 1_000_000L
                delay(60_000L - millisIntoMinute)
            }
        }
    }
    return time
}
