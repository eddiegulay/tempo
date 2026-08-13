package io.eddiegulay.tempo.gym

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.eddiegulay.tempo.MainActivity
import io.eddiegulay.tempo.R
import io.eddiegulay.tempo.data.ThemeRepository
import io.eddiegulay.tempo.i18n.Lang
import io.eddiegulay.tempo.i18n.Strings
import io.eddiegulay.tempo.i18n.StringsJa
import io.eddiegulay.tempo.i18n.stringsFor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * 鍛錬's health foreground service — `00-plan.md` §6 Phase 3, and the thing `03-player.md` §E.5
 * deliberately shipped without.
 *
 * §E.5 is worth re-reading before changing anything here, because it says what this service is *not*
 * for: the session is already correct across backgrounding, deep sleep and process death, because the
 * timeline is derived from `stateAt(elapsedRealtime)` and every transition is persisted. **What goes
 * quiet in a pocket is the cue channel**, and that is the whole of what a foreground process buys.
 * Nothing here writes to the store.
 *
 * **The player half is [TrainingHold], and without it this file buys nothing.** §E.5 says the service's
 * arrival changes exactly one thing in the player spec — §D.7's disarm matrix — and that change is
 * conditional on the service genuinely being up. `CueState.serviceHolding` reads this object;
 * `SessionController.onBackgrounded` and the host's tick loop are the two places it lands.
 *
 * ## Five properties, and the code that enforces each
 *
 * 1. **It never outlives a session.** `START_NOT_STICKY` (a restarted service with no session would
 *    post a notification for a workout that is not happening), `android:stopWithTask="true"`, and — the
 *    one that actually fires in practice — [sync] with a null notice from the mount's `onDispose`.
 *    Three independent stops, because *"a stuck foreground notification is worse than none"* and this
 *    is the failure mode users report rather than tolerate. **Not four**: `onTaskRemoved` is documented
 *    as *not delivered at all* once `FLAG_STOP_WITH_TASK` is set — the framework stops the service
 *    itself instead — so an override for it was dead code claiming a guarantee, which is worse than
 *    three honest ones. `TrainingManifestTest` pins that the two cannot both come back.
 * 2. **It never ticks.** [TrainingNotice] carries no time, so a caller projecting sixty frames a
 *    minute produces sixty equal values and the mount re-posts on none of them. The notification is
 *    rewritten on phase transitions, pauses and station changes, and on nothing else. The tripwire is
 *    `TrainingNoticeTest`'s `the notice carries no field that can tick`, which asserts over the type's
 *    **declared properties** — a `remainingMs` added with a default value would compile, and that test
 *    is what fails instead.
 * 3. **It is never a gate.** Every platform call that can refuse is wrapped. A device that will not
 *    let the service start loses the notification and keeps the workout — see [startForegroundOrStop].
 * 4. **It commands, it does not decide.** The two actions publish into [TrainingCommands] and the
 *    session host applies them through the ordinary `SessionActions`. The state machine stays the one
 *    authority on what a ┃┃ means (`03-player.md` §C), and a command that arrives with no host
 *    collecting is dropped rather than queued — no host means no session to pause.
 * 5. **It is not bound.** [onBind] returns null. A binding would give the service a second lifetime
 *    to reason about, and property 1 is worth more than the type-safety a `Binder` would buy.
 *
 * *Rejected* — a `MediaSession` and its transport controls, which is how most timer apps get a
 * lock-screen surface. It would put 鍛錬 in the volume rocker's now-playing slot and let a headset
 * button skip a station, and `03-player.md` §D.4 has already decided this feature's relationship with
 * the audio system: it borrows focus per cue window and gives it straight back, precisely so the
 * user's music is not the price of a workout.
 */

/**
 * The notification's two buttons, as events the player can collect.
 *
 * A process-wide object rather than something handed to the service, because the two ends never meet:
 * a notification action is delivered by the framework to a fresh `onStartCommand`, and the session
 * host is a composable in an Activity that may or may not be resumed. A `MutableSharedFlow` with no
 * replay is the honest shape of that — a command is meaningful *now* or not at all.
 *
 * `extraBufferCapacity = 1` with the default `BufferOverflow.SUSPEND` semantics on `tryEmit`: an emit
 * with no subscriber returns false and the command is dropped, which is correct. A replay buffer would
 * mean a 続ける tapped on a lock screen at 07:00 firing into whatever session was started at 19:00.
 */
object TrainingCommands {
    private val _events = MutableSharedFlow<TrainingControl>(extraBufferCapacity = 1)

    /** Collect from the session host; each event maps onto one `SessionActions` call. */
    val events: SharedFlow<TrainingControl> = _events.asSharedFlow()

    internal fun publish(kind: TrainingControl) {
        _events.tryEmit(kind)
    }
}

/**
 * Whether a foreground service is, right now, holding a session — the fact `03-player.md` §E.5's one
 * sanctioned change to §D.7 turns on.
 *
 * **It reports the platform's answer, not our intention.** Set true only after `startForeground` has
 * returned without throwing, and false on every path out of the service. That distinction is the whole
 * value of the flag: three separate refusals are possible ([TrainingService.startForegroundOrStop]
 * lists them), and on a device that refuses, the process is *not* protected. A cue channel left armed
 * there is `03-player.md` §D.4's leaked `ToneGenerator` in a process the system is about to freeze —
 * the battery bug the disarm matrix exists to prevent. So the fallback is Phase 1's behaviour, exactly,
 * and it is chosen by the same fact that would have chosen the new behaviour.
 *
 * A process-wide object for [TrainingCommands]'s reason: the service and the player never meet. Both
 * live in the one process (no `android:process` on the `<service>`), so this is a plain read.
 *
 * *Rejected* — binding to the service and asking it. That is a second lifetime to reason about, which
 * [TrainingService] property 5 already declined, to answer a question that is one boolean.
 *
 * *Rejected* — deriving it from the mount instead, i.e. "held while `TrainingServiceMount` is in the
 * composition". It would be true on a device that never managed to start anything, which is the one
 * case that must not keep its cues.
 */
object TrainingHold {
    private val _held = MutableStateFlow(false)

    /** Collect in the player; read `.value` anywhere a decision needs the fact synchronously. */
    val held: StateFlow<Boolean> = _held.asStateFlow()

    internal fun set(value: Boolean) {
        _held.value = value
    }
}

class TrainingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Post, update, or refuse — and never crash, never linger.
     *
     * A null intent is the system restarting us despite `START_NOT_STICKY` (it can still happen after
     * a process kill under memory pressure on some OEM builds); there is no session to describe, so we
     * leave rather than post an empty notification.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                TrainingCommands.publish(TrainingControl.PAUSE)
                return START_NOT_STICKY
            }

            ACTION_RESUME -> {
                TrainingCommands.publish(TrainingControl.RESUME)
                return START_NOT_STICKY
            }

            ACTION_SYNC -> {
                val notice = intent.toNotice()
                if (notice == null) {
                    stopNow()
                    return START_NOT_STICKY
                }
                startForegroundOrStop(notice, intent.toStrings())
                return START_NOT_STICKY
            }

            else -> {
                stopNow()
                return START_NOT_STICKY
            }
        }
    }

    /**
     * The service is going away by any route — including the one the framework takes on its own.
     *
     * `android:stopWithTask="true"` means a swiped-away task stops this service *without* delivering
     * `onTaskRemoved`, so this is the only hook that covers every ending. It is here for [TrainingHold]
     * and nothing else: the notification is already gone by the time it runs.
     */
    override fun onDestroy() {
        TrainingHold.set(false)
        super.onDestroy()
    }

    /**
     * `startForeground`, with every way the platform can say no folded into "then don't".
     *
     * Three different refusals live across three API levels and they do not share a supertype: a
     * `SecurityException` from API 34's `health` type prerequisite (see [TrainingConsent]), a
     * `ForegroundServiceStartNotAllowedException` from API 31's background-start rules — a class that
     * **does not exist below API 31**, so it cannot be named in a `catch` this file compiles at
     * `minSdk 29` — and an `IllegalArgumentException` from a type/manifest mismatch. Catching
     * `Exception` is the narrowest clause that covers all three, and the recovery is identical for
     * every one of them: stop, and let the session carry on exactly as it did in Phase 1.
     *
     * **Stopping is mandatory, not tidy.** A service that has been started but never reaches
     * `startForeground` is killed with an ANR by the framework's own five-second timer, which would
     * turn a missing notification into a crash in the middle of a workout.
     */
    private fun startForegroundOrStop(notice: TrainingNotice, strings: Strings) {
        val notification = buildNotification(notice, strings)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                } else {
                    0
                },
            )
            // Only here, after the call that can refuse has returned. See [TrainingHold]: this flag is
            // what tells the cue engine it may keep a ToneGenerator alive in a backgrounded process,
            // and it must never be set on the strength of having *asked* for a foreground service.
            TrainingHold.set(true)
        } catch (_: Exception) {
            stopNow()
        }
    }

    private fun stopNow() {
        TrainingHold.set(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * The notification: the routine, the phase, one button, and no chrome.
     *
     * - `IMPORTANCE_LOW` on the channel and `setSilent(true)` on the post. The session's own cue engine
     *   owns every sound and every buzz this feature makes (`03-player.md` §D), and it routes them as
     *   `USAGE_ALARM` so they survive silent mode. A notification channel making a second, unrelated
     *   noise at each phase change would double every cue.
     * - `setOnlyAlertOnce(true)` for the same reason, one layer down.
     * - `setShowWhen(false)`. The default is the post time, which after a phase change reads as *"this
     *   session started ninety seconds ago"* — a wrong number, free, and therefore not worth having.
     * - `setOngoing(true)`. It is a live workout; it should not be swipeable into nothing while the
     *   clock runs.
     */
    private fun buildNotification(notice: TrainingNotice, strings: Strings): Notification {
        ensureChannel(strings)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_training_notice)
            .setContentTitle(noticeTitle(notice))
            .setContentText(noticeText(notice, strings))
            .setContentIntent(openPlayerIntent())
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(controlAction(noticeControl(notice), strings))
            .build()
    }

    /**
     * Tapping the body returns to the session.
     *
     * `MainActivity` is `launchMode="singleTask"` and a `category.HOME` activity, so this resumes the
     * existing task rather than creating anything: the gym shell is still on its own stack with the
     * player on top, exactly where the user left it. No extras, and deliberately no deep link — a
     * route argument here would be a second opinion about which screen is current, and the shell's
     * stack is the only one that can be right.
     */
    private fun openPlayerIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun controlAction(control: TrainingControl, strings: Strings): NotificationCompat.Action {
        val action = when (control) {
            TrainingControl.PAUSE -> ACTION_PAUSE
            TrainingControl.RESUME -> ACTION_RESUME
        }
        val intent = PendingIntent.getService(
            this,
            REQUEST_CONTROL,
            Intent(this, TrainingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // No icon: NotificationCompat.Action requires the parameter, and every surface that still
        // renders one (pre-Nougat) is below minSdk. Passing 0 draws the label alone, which is what
        // this app's own buttons are — words, never glyphs, outside the player's own control bar.
        return NotificationCompat.Action.Builder(0, noticeControlLabel(control, strings), intent).build()
    }

    /**
     * 鍛錬 — one channel, named for the mode it belongs to.
     *
     * The name is what the user reads in system settings, and it is the same word the launcher's mode
     * dialog and the shell's first tab use (`00-plan.md` §3.2). Created on every post rather than in
     * `onCreate`: channel creation is idempotent and cheap, and doing it at the point of use means a
     * user who cleared the channel between sessions gets it back.
     *
     * **That per-post creation is also the whole of what makes the name follow a language change.**
     * Android stores the name against the channel id; `createNotificationChannel` on an id that already
     * exists updates the name (importance it will only ever lower). So the channel's name catches up
     * **at the start of the next session and not before** — a user who switches language and then opens
     * Android's own notification settings without training again sees the old word there. That is not a
     * bug we can fix from here without posting a notification nobody asked for, and it is the reason
     * this call is not hoisted into `onCreate` where it would look tidier.
     */
    private fun ensureChannel(strings: Strings) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, strings.gymCue.channelName, NotificationManager.IMPORTANCE_LOW),
        )
    }

    /**
     * The language the sender was showing, or Japanese if the extra is missing.
     *
     * It travels on the intent rather than being read here because **this end has no good moment to
     * read it**: a `Service` has no lifecycle scope, and `loadInitialSettings`' `runBlocking` on
     * `onStartCommand`'s main thread would be a disk read on every phase transition. [sync] runs in the
     * app process, where the value is already resolved and where one read is cheap.
     *
     * A missing extra means a sender older than this field, which cannot happen inside one process —
     * so the fallback is the app's original language rather than an error path.
     */
    private fun Intent.toStrings(): Strings =
        stringsFor(Lang.fromTag(getStringExtra(EXTRA_LANG)) ?: Lang.Ja)

    private fun Intent.toNotice(): TrainingNotice? {
        val routine = getStringExtra(EXTRA_ROUTINE) ?: return null
        val phase = Phase.fromStorage(getStringExtra(EXTRA_PHASE)) ?: return null
        return TrainingNotice(
            routineName = routine,
            phase = phase,
            paused = getBooleanExtra(EXTRA_PAUSED, false),
            exerciseName = getStringExtra(EXTRA_EXERCISE),
        )
    }

    companion object {
        private const val CHANNEL_ID = "gym_session"
        private const val NOTIFICATION_ID = 0x936D
        private const val REQUEST_OPEN = 1
        private const val REQUEST_CONTROL = 2

        private const val ACTION_SYNC = "io.eddiegulay.tempo.gym.SYNC"
        private const val ACTION_PAUSE = "io.eddiegulay.tempo.gym.PAUSE"
        private const val ACTION_RESUME = "io.eddiegulay.tempo.gym.RESUME"

        private const val EXTRA_ROUTINE = "routine"
        private const val EXTRA_PHASE = "phase"
        private const val EXTRA_PAUSED = "paused"
        private const val EXTRA_EXERCISE = "exercise"
        private const val EXTRA_LANG = "lang"

        /**
         * The whole API: **one idempotent call**, non-null to start or update, null to stop.
         *
         * A single entry point rather than `start`/`update`/`stop` because the caller is a composable
         * effect keyed on a value, and three methods would give it three chances to get the ordering
         * wrong at exactly the moments that matter — a session ending while a phase transition is in
         * flight, a rotation, a process death. `startForegroundService` on an already-foreground
         * service is a plain `onStartCommand`, so start and update are genuinely the same call.
         *
         * `runCatching` guards `ForegroundServiceStartNotAllowedException` (API 31+): the mount only
         * ever calls this from a resumed player, but a session that transitions in the same frame the
         * user presses HOME is a real race, and losing the notification there must not take the
         * workout with it.
         */
        fun sync(context: Context, notice: TrainingNotice?) {
            val app = context.applicationContext
            val intent = Intent(app, TrainingService::class.java)
            if (notice == null) {
                runCatching { app.stopService(intent) }
                return
            }
            intent.action = ACTION_SYNC
            intent.putExtra(EXTRA_ROUTINE, notice.routineName)
            intent.putExtra(EXTRA_PHASE, notice.phase.name)
            intent.putExtra(EXTRA_PAUSED, notice.paused)
            intent.putExtra(EXTRA_EXERCISE, notice.exerciseName)
            intent.putExtra(EXTRA_LANG, languageTag(app))
            runCatching { ContextCompat.startForegroundService(app, intent) }
        }

        /**
         * The stored UI language, read on the app side so the service never has to.
         *
         * `runCatching` because this is a disk read on the caller's thread and a notification is not
         * worth a crash: `loadInitialSettings` is the same synchronous read `LauncherViewModel` already
         * makes at cold start, so by the time a session is running DataStore has the value in memory,
         * but a corrupt preferences file must lose the translation and not the workout.
         *
         * Read per call rather than cached: a notice is re-synced on phase transitions, pauses and
         * station changes — a handful of times a minute — and caching would pin the notification to
         * whatever language the process started in, which is the bug this whole namespace is fixing.
         */
        private fun languageTag(context: Context): String =
            runCatching { ThemeRepository(context).loadInitialSettings().lang }
                .getOrDefault(StringsJa.lang)
                .tag
    }
}
