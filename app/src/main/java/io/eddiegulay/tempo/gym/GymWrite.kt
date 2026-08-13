package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.data.GymFault

/**
 * The result of a write into 鍛錬's store.
 *
 * It carries a value where [io.eddiegulay.tempo.calendar.WriteOutcome] carries none, and that is the
 * whole reason it is a second type rather than a reuse. Half the gym's writes exist *to* return
 * something the caller cannot proceed without: `startSession` returns the row id the player then
 * writes every segment against, `saveRoutine` returns the id an edited built-in was silently copied
 * into, `finishSession` returns the summary the record screen renders instead of re-querying. A
 * `WriteOutcome.Ok` with a nullable side-channel would make every one of those call sites assert.
 *
 * *Rejected* — `kotlin.Result<T>`. It carries a `Throwable`, which is exactly the vocabulary
 * [GymFault] exists to get rid of: the point of the fault list is that a full disk and a corrupt file
 * are different *remedies*, not different exception classes, and `Result` would push that
 * classification out to every caller instead of doing it once at the repository boundary. `Result`
 * also cannot be used as a return type of a suspend function without an opt-in, and it discards the
 * distinction the moment anyone calls `getOrNull()`.
 *
 * *Rejected* — folding this into `Loadable`. A read that has not happened yet is a real state a
 * screen must render; a write that has not happened yet is not — the caller is suspended inside it.
 * Sharing the type would give every write path a `Loading` branch that can never be taken.
 *
 * See `.planning/exercise/02-data.md` §C.0.
 */
sealed interface GymWrite<out T> {

    /**
     * Declared `out T` rather than §C.0's bare `T` so that this mirrors
     * [io.eddiegulay.tempo.calendar.Loadable.Ready], which the repo already proves can carry a
     * covariant payload through [valueOrNull] without an unchecked cast. Nothing else changes.
     */
    data class Ok<out T>(val value: T) : GymWrite<T>

    data class Failed(val fault: GymFault) : GymWrite<Nothing>

    /** The value if the write landed, otherwise null — for callers that can honestly degrade. */
    fun valueOrNull(): T? = (this as? Ok)?.value

    /**
     * The value, or a throw.
     *
     * Only for the paths where a failed write means the next line of code is meaningless — the
     * player's start path (`03-player.md` §A) is the one in the specs: a session that exists in the
     * UI but not in the database is the single state process death cannot recover from, so
     * navigation is blocked on this and there is nothing sensible to do with a null. Everywhere else
     * the fault is copy on a screen, and calling this instead is a bug.
     */
    fun valueOrThrow(): T = when (this) {
        is Ok -> value
        is Failed -> throw IllegalStateException("鍛錬 write failed: $fault")
    }
}
