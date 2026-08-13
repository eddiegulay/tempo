package io.eddiegulay.tempo.gym

import android.content.Context
import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.gym.data.GymStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.YearMonth

/**
 * 鍛錬's store, as everything above it sees it.
 *
 * An interface rather than the class itself, and that is the one structural decision in this file.
 * `CalendarRepository` is a concrete class because a `ContentResolver` is already an interface to
 * something else's process — there is nothing left to fake. Here the implementation is a
 * `SQLiteOpenHelper`, a seeder, a corruption handler and a write mutex, none of which exist on the
 * JVM, and every page in `01`, `03` and `04` is specified against these signatures rather than
 * against SQL. Splitting the contract from the store is what lets four tracks compile in parallel and
 * what lets a screen's states be exercised without a device.
 *
 * *Rejected* — one repository per screen family (`RoutineRepository`, `SessionRepository`,
 * `RecordsRepository`). It reads tidier and breaks the single most important property this store has:
 * `finishSession` closes the clock, advances a progression, evaluates PRs and reads the previous
 * session's baseline **in one transaction** (§E.5), and every read flow re-runs off one in-process
 * table-change bus (§E.3). Three objects would either share those privately — the split then being a
 * fiction — or lose atomicity to make an org chart happy.
 *
 * *Rejected* — suspend functions returning `Loadable` everywhere for symmetry. A `Flow` is used
 * exactly where something can change under the reader, and `suspend` where it provably cannot:
 * [routineVersion] reads an immutable snapshot, so a flow there would be a subscription that can
 * never emit twice. Where the spec chose a flow over a suspend it was for a reason worth keeping —
 * [sessionDetail] is a flow because the rating is editable on that very screen.
 *
 * **Threading.** `Dispatchers.IO` lives in this implementation and nowhere else; flows carry
 * `.flowOn(Dispatchers.IO)`, matching `CalendarRepository.events()`. No `Cursor` ever crosses a
 * dispatcher boundary — every query materialises its rows inside `cursor.use { }` before returning,
 * because a `Cursor` handed to the main thread is a leaked native resource waiting for a `StrictMode`
 * violation (§E.2).
 *
 * **Faults.** Reads report through `Loadable.Failed`; writes through [GymWrite.Failed]. Neither ever
 * degrades to an empty result. Design §7.1: *an unreadable store must never render as
 * 記録はありません* — that is what `GymFault.StoreCorrupt` is for, and it is the reason the corruption
 * handler quarantines the file instead of letting the platform delete it (§E.6).
 *
 * See `.planning/exercise/02-data.md` §C. The KDoc on each member is that section's, carried over,
 * because it states invariants rather than describing code.
 */
interface GymRepository {

    // ─── C.1 Reads ──────────────────────────────────────────────────────────────────────────────

    /** Everything GYM.HOME renders, in one read. Re-emits on any routine or session write. */
    fun homeFeed(recentLimit: Int = 3, builtInPreview: Int = 4): Flow<Loadable<GymHomeFeed>>

    /** The library index: built-in ∪ user, archived excluded, as list projections. */
    val routines: StateFlow<Loadable<List<RoutineSummary>>>

    fun usageCounts(): Flow<Map<String, Int>>

    fun recentUsage(days: Int): Flow<Map<String, Int>>

    /** One routine at its head version, stations resolved, plus PRs and the last attempts. */
    fun routineDetail(routineId: String): Flow<Loadable<RoutineDetail>>

    fun scaledTiers(routineId: String): Flow<List<RoutineSummary>>

    /**
     * The exact shape a session performed, from session.routine_version_id. This is what the record
     * screen renders from history: a March session must describe March's routine even if it was
     * rewritten in April. Immutable by construction, so `suspend`, not `Flow`.
     */
    suspend fun routineVersion(versionId: Long): Loadable<RoutineSnapshot>

    // ─── C.2 The live session ───────────────────────────────────────────────────────────────────

    /**
     * A session interrupted by a quit, a crash, or process death. [ResumableSession.resumability] is
     * computed by the player's pure resumability() (03 §E.3) from the boot anchor and the age.
     * One-shot: read once at cold start and once on entering GYM.HOME, and idx_session_live guarantees
     * at most one candidate exists.
     */
    suspend fun resumableSession(): Loadable<ResumableSession?>

    /**
     * Opens a session and returns its id. Fails with GymFault.Rejected if one is already live —
     * enforced by idx_session_live, not by a check-then-act race. Stamps started_at from the guarded
     * clock, freezes stations_planned, pins routine_version_id and routine_name.
     */
    suspend fun startSession(routineId: String, tier: String?, roundsPlanned: Int?): GymWrite<Long>

    /**
     * Persists one completed segment. Called on every phase transition — a few writes per minute.
     * Idempotent under idx_result_session, so a retry after a crash cannot double-count.
     */
    suspend fun recordSegment(sessionId: Long, r: SegmentResultDraft): GymWrite<Unit>

    /** Clock columns only. Called on pause/resume so process death loses at most one phase. */
    suspend fun checkpoint(sessionId: Long, c: PersistedClock, roundsCompleted: Int): GymWrite<Unit>

    /**
     * Closes the session in ONE transaction: clock, completion flags, progression advance, PR
     * evaluation, and the previous-session baseline. A partial session is a first-class outcome, not a
     * failure: it finishes with complete = 0, is fully recorded, and is excluded only from PR
     * eligibility.
     *
     * @return everything the record screen needs, so it never queries after a finish.
     */
    suspend fun finishSession(sessionId: Long, complete: Boolean): GymWrite<SessionOutcome>

    /**
     * Records どうでしたか. Separate from finishSession because the rating is asked *after* the session
     * is saved and is optional — the record must survive its absence. Writes rating_cr10 from the
     * frozen 4/7/9 mapping and re-evaluates load-derived metrics.
     */
    suspend fun rateSession(sessionId: Long, rating: Rating?): GymWrite<Unit>

    suspend fun discardSession(sessionId: Long): GymWrite<Unit>

    // ─── C.3 History and records ────────────────────────────────────────────────────────────────

    /** Keyset-paged by (started_at, id) DESC. Never OFFSET — see 04 §3. */
    fun history(cursor: HistoryCursor?, limit: Int = 30): Flow<Loadable<List<SessionSummary>>>

    fun attemptsForRoutine(routineId: String, limit: Int): Flow<Loadable<List<SessionSummary>>>

    /**
     * How many sessions this routine has finished — as a [Loadable], because 削除 branches on it.
     *
     * **A bare `Flow<Int>` cannot say "I do not know yet", and this is the one read where that
     * matters.** `GYM.LIBRARY.DETAIL`'s 削除 confirm chooses between an archive ("記録は残ります") and an
     * irreversible purge ("やった記録はありません。完全に消えます。") from this number. Implemented with a
     * fallback of zero — which is what `02-data.md` §C.1's original signature invited — "not read yet"
     * and "the read failed" both arrive as *zero*, and the page renders an ありません-as-emptiness claim
     * about the user's records over an unreadable store, then offers 完全に削除 on top of it. That is
     * exactly what `00-plan.md` §4.1 rule 1 forbids and what `DECISIONS.md` §Q6 pins
     * (記録を読めません must never be misreadable as 記録はありません), on the only irreversible dialog in
     * the feature.
     *
     * So: `Ready(0)` earns the purge wording, `Ready(n > 0)` the archive wording, and
     * `Loading` / `Failed` must not offer the destructive branch at all.
     *
     * *Rejected* — reading it with the `suspend` path at the moment 削除 is tapped. It answers the
     * doctrine too, but the count also has to be *live*: a session finishing while the detail page is
     * open must move the routine from purgeable to archivable without a re-entry, which a one-shot read
     * at tap time cannot do without re-querying inside a dialog's click handler.
     */
    fun countForRoutine(routineId: String): Flow<Loadable<Int>>

    suspend fun populatedMonths(): Loadable<List<YearMonth>>

    /** Flow, not suspend — the rating is editable on this very screen. */
    fun sessionDetail(sessionId: Long): Flow<Loadable<SessionDetail>>

    fun monthLoad(month: YearMonth): Flow<Loadable<Map<LocalDate, DayLoad>>>

    /** Quartile thresholds for the ink levels. */
    fun loadScale(days: Int = 90): Flow<LoadScale>

    fun streak(): Flow<Loadable<Streak>>

    fun routineBests(): Flow<Loadable<List<RoutineBest>>>

    fun movementBests(): Flow<Loadable<List<MovementBest>>>

    /** Both charts in one read — they share a GROUP BY and splitting them would double the scan. */
    fun weeklySeries(weeks: Int = 12): Flow<Loadable<List<WeekPoint>>>

    fun volumeSeries(days: Int): Flow<Loadable<List<DayVolume>>>

    /**
     * Foster load and 7-day monotony. Null until enough rated sessions exist — an unrated week has no
     * load, and inventing one puts a meaningless number on screen. ACWR is suppressed below 28 days.
     */
    fun trainingLoad(): Flow<Loadable<TrainingLoad?>>

    // ─── C.4 Authoring ──────────────────────────────────────────────────────────────────────────

    /**
     * A new routine inserts identity + version 1; an edit inserts a new version and repoints the head,
     * leaving every past session pinned to what it performed. Editing a built-in silently copies first
     * and returns the NEW id — the caller must adopt it, because the built-in is unchanged.
     */
    suspend fun saveRoutine(draft: RoutineDraft): GymWrite<String>

    suspend fun duplicateRoutine(routineId: String, newName: String): GymWrite<String>

    /** Soft delete. Never hard: sessions hold an FK and history must stay readable. */
    suspend fun archiveRoutine(routineId: String): GymWrite<Unit>

    suspend fun restoreRoutine(routineId: String): GymWrite<Unit>

    /** Offered only when [countForRoutine] is `Ready(0)` — never on a count that is merely unread. */
    suspend fun purgeRoutine(routineId: String): GymWrite<Unit>

    suspend fun setFavourite(routineId: String, favourite: Boolean): GymWrite<Unit>

    suspend fun touchRoutine(routineId: String)

    fun progression(programId: String): Flow<Loadable<ProgressionState>>

    /** Manual override — a user who knows they belong on step 9 should not grind to it. */
    suspend fun setProgressionStep(programId: String, stepIndex: Int): GymWrite<Unit>

    /** Re-runs every failed read. The もう一度 behind a `FaultPanel`, exactly as the calendar's. */
    fun retry()

    /**
     * Lowers the quarantined-database fault, durably, because the user has read it (§E.6).
     *
     * **Not a retry, and that is why it is a separate method.** [retry] re-runs the reads and a
     * quarantined store fails them all again; this is the user saying they have understood that a year
     * of training is gone. It is the *only* way out of that state — finishing a session must not clear
     * it, because one new session does not bring the history back, and a corruption raised mid-session
     * would otherwise be erased by that same session's finish.
     *
     * Declared here rather than left on `GymStore` alone because the store is `internal` and reached
     * only through this interface, so an undeclared method is a method nothing can ever call. The
     * previous unit tried and reverted: Kotlin requires the implementation to carry `override`, which
     * is a one-word edit in a file it did not own. Both files land together here.
     *
     * **Still unwired on purpose.** The surface that owes it an affordance is the 記録 tab
     * (`GYM.RECORDS.INDEX`, Phase 3), whose fault strip is where `GymFault.StoreCorrupt` is read.
     * Bolting a dismissal onto a Phase 1 page would put the one irreversible acknowledgement in the
     * feature somewhere the spec never asked for it.
     */
    suspend fun acknowledgeHistoryLoss()

    suspend fun rebuildPersonalRecords(): GymWrite<Unit>

    /** The one settings write that lands in SQLite, because it is date-versioned (§A.8). */
    suspend fun setTrainingPlan(daysMask: Int, forgivenessPerMonth: Int): GymWrite<Unit>

    companion object {
        /**
         * The process-wide store.
         *
         * A singleton because the in-memory exercise catalogue, the write mutex and the table-change
         * bus are all *per-database* state and a second instance would silently split all three: two
         * mutexes are no mutex, and a flow observing a bus nobody writes to simply stops updating with
         * no error (§E.1, §E.3). Follows `BlockadeRepository.getInstance`.
         *
         * **Owner: Track A (the SQLite store).** This declaration exists so the shell, the player and
         * the library pages can be written against it now; the body is Track A's to replace with the
         * double-checked construction the rest of the repo uses, and to install
         * [ExerciseCatalog]'s map from at the same moment.
         *
         * Constructing it opens the database, runs any pending migration, seeds the catalogue on a
         * first launch and loads the exercise map — blocking, following `BlockadeRepository`, and safe
         * for the same reason: nothing here is on the first frame, because 鍛錬 is reached through a
         * long-press and two taps (§E.2). Do not call it from `TempoApp`'s composition.
         */
        fun getInstance(context: Context): GymRepository = GymStore.getInstance(context)
    }
}
