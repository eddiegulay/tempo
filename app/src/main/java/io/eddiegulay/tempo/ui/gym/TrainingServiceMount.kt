package io.eddiegulay.tempo.ui.gym

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.eddiegulay.tempo.calendar.findActivity
import io.eddiegulay.tempo.gym.TRAINING_CONSENT_DELAY_MS
import io.eddiegulay.tempo.gym.TrainingConsent
import io.eddiegulay.tempo.gym.TrainingNotice
import io.eddiegulay.tempo.gym.TrainingService
import io.eddiegulay.tempo.ui.gym.session.SessionOverlayUi
import io.eddiegulay.tempo.ui.gym.session.SessionUiState
import kotlinx.coroutines.delay

/*
 * How the health foreground service is attached to a live session, and when its permissions are asked
 * for — the Android half of `gym/TrainingService.kt` and `gym/TrainingConsent.kt`.
 *
 * Both composables here are **mounts**: they draw nothing, they own no state a page can see, and they
 * exist so that the two things a service needs — a lifetime and a grant — are each one line at the
 * call site rather than a lifecycle observer a page author has to remember to write.
 *
 * Both are mounted from `ui/gym/GymShell.kt`, inside the session player's `when (screen)`:
 * [TrainingServiceMount] on the `Live` arm, [TrainingConsentMount] on the `Complete` one. Neither is
 * mounted at the shell root, and the arms carry the argument — a mount states a *lifetime*, so where it
 * sits is the whole of what it claims, and a service held above the player would still be running over
 * 記録 and over the library afterwards.
 */

/**
 * `SessionUiState` → the four facts the notification draws.
 *
 * This is the projection that makes property 2 of [TrainingService] true: the live state changes on
 * every frame and this value changes on a phase transition, a pause, or a new station. Keeping it a
 * plain function rather than a `derivedStateOf` inside the mount means it is the caller's `remember`
 * that decides how often it runs, and the caller is the page that already has the state.
 *
 * **A quit sheet is not a pause, here.** `SessionOverlayUi.Quit` does stop the clock (§A WORK, "the
 * clock pauses the instant the sheet opens"), but the notification would then offer 続ける for a
 * session whose user is being asked 鍛錬を終えますか — two different questions, one of which is on the
 * screen they are looking at. The phase is left as it was; a 休止 tapped from the notification while
 * the sheet is open reaches a state machine that returns null for it, which §C is explicit is *"a
 * no-op, never an error"*.
 */
fun trainingNotice(state: SessionUiState): TrainingNotice = TrainingNotice(
    routineName = state.routineName,
    phase = state.phase,
    paused = state.overlay is SessionOverlayUi.Paused,
    exerciseName = state.exercise?.nameJa,
)

/**
 * Keeps the service alive for exactly as long as this composable is in the composition.
 *
 * Two effects, and the split is the point:
 *
 * - `LaunchedEffect(notice)` **posts**, and restarts only when the value actually differs. This is
 *   where "update on phase transitions, not every second" is enforced, by [TrainingNotice] having no
 *   field that ticks.
 * - `DisposableEffect(Unit)` **stops**, keyed on nothing so it survives every notice change and fires
 *   once, when the player leaves. That covers the three endings the brief names — finish, quit and
 *   discard — without this file knowing which one happened, because all three take `SessionScreen`
 *   off `Live` and unmount the caller.
 *
 * *Rejected* — one `DisposableEffect(notice)` doing both. Its `onDispose` runs on every key change, so
 * each phase transition would stop the service and start it again: a notification that blinks out and
 * back twelve times during 七分間, and twelve chances for the restart to land after a background
 * transition that forbids it.
 *
 * *Rejected* — a `LifecycleEventObserver` stopping on `ON_STOP`, by analogy with `GymShell`'s
 * keep-awake flag. That flag is a claim about a **window**; this is a claim about a **session**, and
 * `ON_STOP` is precisely the moment the service starts being useful.
 *
 * @param notice null suspends the service without unmounting — the caller does not need one, but it
 *   makes the type total and lets a page hold the mount while a session is still loading.
 */
@Composable
fun TrainingServiceMount(notice: TrainingNotice?) {
    val context = LocalContext.current

    LaunchedEffect(notice) { TrainingService.sync(context, notice) }

    DisposableEffect(Unit) {
        onDispose { TrainingService.sync(context, null) }
    }
}

/**
 * Asks — once, quietly, and only where it costs nothing — for the permissions the service needs.
 *
 * Mounted on `GYM.SESSION.COMPLETE`. [TRAINING_CONSENT_DELAY_MS] carries the full argument for that
 * placement; the short version is that the two moments this must not be — launch, and in front of a
 * workout — leave exactly one, and this is it. Nothing is gated on the answer: a user who declines
 * everything gets Phase 1's behaviour, which `03-player.md` §E.5 documents as correct.
 *
 * The permanent-denial detection is `CalendarPermissionState`'s, for the reason stated there: before
 * the first request `shouldShowRequestPermissionRationale` is *also* false, so the two are
 * indistinguishable without remembering that we asked. [TrainingConsent.shouldAutoRequest] turns that
 * pair plus the missing set into one predicate a JVM test can pin.
 *
 * *Rejected* — a 「通知を許可しますか」 row on the record screen, tapped rather than raised. It would be
 * a sentence no spec table contains, on the one screen `00-plan.md` §4.1's copy discipline is
 * strictest about, to explain a system dialog that explains itself.
 *
 * @param enabled false suppresses the ask entirely — for a caller that knows this record is not the
 *   end of a live session (`GYM.RECORDS.SESSION_DETAIL` reads the same component and must never ask).
 */
@Composable
fun TrainingConsentMount(enabled: Boolean = true) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // Survives a rotation, resets on process death — which is exactly the "once per process" term of
    // the predicate. Durability across processes is the rationale bit's job, not this flag's.
    var asked by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { asked = true }

    val missing = remember(asked) {
        TrainingConsent.required(Build.VERSION.SDK_INT).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }
    val anyRationale = remember(missing, activity) {
        activity != null && missing.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    }

    LaunchedEffect(enabled, missing, asked, anyRationale) {
        if (!enabled) return@LaunchedEffect
        if (!TrainingConsent.shouldAutoRequest(missing, asked, anyRationale)) return@LaunchedEffect
        delay(TRAINING_CONSENT_DELAY_MS)
        // Set before launching, not in the callback: the dialog pauses the Activity, and a
        // recomposition on the way back down must not queue a second request behind the first.
        asked = true
        launcher.launch(missing.toTypedArray())
    }
}
