package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.gym.ActiveSession
import io.eddiegulay.tempo.gym.GymRoute

/**
 * Whether the window may hold `FLAG_KEEP_SCREEN_ON` right now.
 *
 * Three facts have to be true at once, and the third one is the fix: **a live session, the 画面を消さない
 * preference, and the session player actually on top.**
 *
 * `01-shell.md` §A.7 closes with "`GYM.SESSION.COMPLETE`, `GYM.LIBRARY.DETAIL` and the resume prompt
 * are excluded from `keepAwake`". The tempting reading is that `session != null` already delivers all
 * three, since the workout is over or has not started on each. It does not. 型の中身 is reachable
 * **with a live session**, by a route §B's own actions table describes: press HOME mid-session
 * (`onLeaveShell` resets the stack and deliberately keeps `_activeSession`), re-enter 鍛錬, tap a
 * routine card. `activeSession` is still non-null and the top route is now `RoutineDetail`, so a gate
 * of `session != null && keepScreenOn` holds the flag for as long as the user cares to read a routine
 * — the same leak class commit `1f49dfc` fixed, arrived at from the other direction.
 *
 * Adding `topRoute is GymRoute.Session` excludes 型の中身 and the resume prompt **structurally**:
 * neither is that route, so neither can hold the flag whatever the session state is.
 *
 * **`GYM.SESSION.COMPLETE` is the one exclusion this predicate cannot make, and that is by design.**
 * The completion page is a phase of the player and therefore renders under `GymRoute.Session` — the
 * phase is the player's state machine and is deliberately not in the route (`GymRoute.Session`'s own
 * KDoc). So the third exclusion stays an obligation on the player: `onSessionClosed()` at finish,
 * quit or discard, which nulls the session and drops the flag before 記録 is drawn. Stating it here
 * rather than implying the route gate covers all three is the point — an unstated obligation is one
 * nobody honours.
 *
 * *Rejected* — `!topRoute.immersive`, or a deny-list of routes. The builder and the station picker are
 * immersive too, and a deny-list is a list to forget to add to; the allow-list is one route long and
 * every route added later is excluded by default, which is the safe direction for a flag that burns
 * battery when it is wrong.
 *
 * Pure, and Android-free, so the case this exists for — a live session sitting on 型の中身 — is
 * asserted in `KeepAwakeTest` rather than reproduced by hand on a device.
 */
fun keepAwake(session: ActiveSession?, keepScreenOnPref: Boolean, topRoute: GymRoute): Boolean =
    session != null && keepScreenOnPref && topRoute is GymRoute.Session
