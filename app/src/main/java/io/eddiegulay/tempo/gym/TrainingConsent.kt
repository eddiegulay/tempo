package io.eddiegulay.tempo.gym

import android.Manifest
import android.os.Build

/*
 * Which runtime permissions the health foreground service needs, at which API level, and when it is
 * decent to ask for them.
 *
 * **The service is a reliability improvement, not a gate.** Every function here is written so that a
 * user who refuses everything trains exactly as they did in Phase 1 — which `03-player.md` §E.5 already
 * documents as *correct*, not degraded: the screen stays on, the timeline is derived from a monotonic
 * clock, and a backgrounded session is right on return. The service buys the cue channel surviving a
 * pocket. Nothing here may ever stand between a user and a workout.
 *
 * ## Why ACTIVITY_RECOGNITION is declared, and why that is not a smell
 *
 * `00-plan.md` §6 lists it, and the instruction on this unit was to add it **only** if something in
 * this phase genuinely uses it. It does, and the use is the platform's own: from Android 14 a service
 * declaring `android:foregroundServiceType="health"` may only call `startForeground` if the app holds
 * at least one of `BODY_SENSORS`, `READ_HEART_RATE`, `READ_SKIN_TEMPERATURE`,
 * `READ_OXYGEN_SATURATION` or `ACTIVITY_RECOGNITION` — or declares `HIGH_SAMPLING_RATE_SENSORS`. With
 * none of them the foreground service **cannot be created at all**.
 *
 * `ACTIVITY_RECOGNITION` is the only one of those six that is even adjacent to what Tempo does, and it
 * is the one `00-plan.md` §6 named. The alternative that needs no runtime prompt —
 * `HIGH_SAMPLING_RATE_SENSORS`, a *normal* permission granted silently at install — was **rejected**:
 * Tempo reads no sensor at any rate, and taking a sensor permission by install-time default precisely
 * because it is the one the user cannot decline is worse than asking for the one they can.
 *
 * The declaration is still honest only if the *behaviour* matches, which is why [required] is API-gated
 * rather than blanket: below API 34 the platform imposes no such prerequisite and the permission is
 * therefore never requested, so a user on Android 10–13 sees no physical-activity prompt at all.
 *
 * Sources: [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types).
 */
object TrainingConsent {

    /**
     * The permissions this phase may ask for at [sdkInt], newest requirement last.
     *
     * Two independent gates, both genuinely level-bound:
     *
     * - **`POST_NOTIFICATIONS`, API 33+.** Below Tiramisu a notification needs no grant. A denial does
     *   not stop the service — a foreground service still runs with its notification suppressed — so
     *   this is asked for the notification's sake, never for the service's.
     * - **`ACTIVITY_RECOGNITION`, API 34+.** The `health` foreground-service-type prerequisite above.
     *   Below Upside-Down-Cake it buys nothing and is not requested.
     */
    fun required(sdkInt: Int): List<String> = buildList {
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) add(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    /**
     * Whether to raise the system dialog by ourselves, without the user having pressed anything.
     *
     * **At most one automatic dialog per install, with no stored flag**, which is the point of the
     * three-term predicate. Android's own rationale bit supplies the durability that
     * `rememberSaveable` cannot:
     *
     * | state | `missing` | `rationale` | asks? |
     * |---|---|---|---|
     * | fresh install, never asked | yes | false | **yes** — the one automatic ask |
     * | asked already in this process | yes | either | no ([askedThisProcess]) |
     * | user declined once | yes | **true** | no, ever again |
     * | user granted | empty | — | no |
     *
     * The middle row is the one that matters: a declined permission flips
     * `shouldShowRequestPermissionRationale` to true and it stays true, so a decline is durable across
     * process death without this feature owning a preference of its own. `GymPreferences` belongs to
     * another unit and a second DataStore for one boolean would be a second store to back up, to
     * restore, and to get wrong (`04` §7.1's backup list is already three files long).
     *
     * The remaining case — a *second* decline, after which the platform stops showing the dialog at
     * all — re-satisfies this predicate on a later process and launches a request that the system
     * auto-denies with no UI. That is one silent callback per process and no dialog, which is cheaper
     * than the storage it would take to avoid.
     */
    fun shouldAutoRequest(
        missing: List<String>,
        askedThisProcess: Boolean,
        anyRationale: Boolean,
    ): Boolean = missing.isNotEmpty() && !askedThisProcess && !anyRationale
}

/**
 * How long the record screen waits before it asks — the ensō's own 900ms.
 *
 * **The ask lives on `GYM.SESSION.COMPLETE`, and that placement is the requirement, not a convenience.**
 * The instruction on this unit was that `POST_NOTIFICATIONS` be requested *"not on launch, and not as
 * a wall in front of a workout"*, and those two exclusions leave exactly one moment in the feature
 * where the permission is both relevant and costless: the session is over, nothing is running, and the
 * only thing the grant can affect is the **next** session. A user who declines has already trained.
 *
 * The delay is `03-player.md` §A COMPLETE's closure duration, reused rather than chosen: a permission
 * dialog pauses the Activity, and the closure is gated on `ON_RESUME`
 * (`RecordSummary.RecordHero`), so a dialog raised on composition would hold the ensō open behind it
 * and then play the ceremony to a user who has just been interrupted. Waiting the closure out costs
 * 900ms and keeps *"the one moment of ceremony the feature has"* intact.
 *
 * *Rejected* — asking during 支度 instead, where the service is about to be needed. That is the wall
 * the brief forbids, and it would put a system dialog over a five-second countdown.
 */
const val TRAINING_CONSENT_DELAY_MS: Long = 900L
