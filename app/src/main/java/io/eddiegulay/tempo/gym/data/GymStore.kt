package io.eddiegulay.tempo.gym.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.data.GymFault
import io.eddiegulay.tempo.data.JapaneseDate
import io.eddiegulay.tempo.gym.AdvanceRule
import io.eddiegulay.tempo.gym.BestMetric
import io.eddiegulay.tempo.gym.DailyLoad
import io.eddiegulay.tempo.gym.DayLoad
import io.eddiegulay.tempo.gym.DayVolume
import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Exercise
import io.eddiegulay.tempo.gym.GymHomeFeed
import io.eddiegulay.tempo.gym.GymRepository
import io.eddiegulay.tempo.gym.GymWrite
import io.eddiegulay.tempo.gym.HistoryCursor
import io.eddiegulay.tempo.gym.LastResult
import io.eddiegulay.tempo.gym.LifetimeSummary
import io.eddiegulay.tempo.gym.LoadScale
import io.eddiegulay.tempo.gym.Measure
import io.eddiegulay.tempo.gym.MovementBest
import io.eddiegulay.tempo.gym.Pattern
import io.eddiegulay.tempo.gym.PersistedClock
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.ProgressionSet
import io.eddiegulay.tempo.gym.ProgressionState
import io.eddiegulay.tempo.gym.ProgressionStep
import io.eddiegulay.tempo.gym.Rating
import io.eddiegulay.tempo.gym.ResumableSession
import io.eddiegulay.tempo.gym.RoutineBest
import io.eddiegulay.tempo.gym.RoutineCard
import io.eddiegulay.tempo.gym.RoutineDetail
import io.eddiegulay.tempo.gym.RoutineDraft
import io.eddiegulay.tempo.gym.RoutineSnapshot
import io.eddiegulay.tempo.gym.RoutineStation
import io.eddiegulay.tempo.gym.RoutineSummary
import io.eddiegulay.tempo.gym.SegmentResult
import io.eddiegulay.tempo.gym.SegmentResultDraft
import io.eddiegulay.tempo.gym.SessionDetail
import io.eddiegulay.tempo.gym.SessionOutcome
import io.eddiegulay.tempo.gym.SessionSummary
import io.eddiegulay.tempo.gym.SetVariant
import io.eddiegulay.tempo.gym.StationDraft
import io.eddiegulay.tempo.gym.StepShape
import io.eddiegulay.tempo.gym.StepUnit
import io.eddiegulay.tempo.gym.Streak
import io.eddiegulay.tempo.gym.Tier
import io.eddiegulay.tempo.gym.TrainingLoad
import io.eddiegulay.tempo.gym.WeekPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.roundToInt

/**
 * 鍛錬's SQLite store — the implementation behind [GymRepository].
 *
 * Everything structural about it is argued in `02-data.md` §E and summarised here because the three
 * decisions interlock:
 *
 * **One mutex for all writes, no lock at all for reads.** `SQLiteDatabase` serialises statements, but
 * that does not make a read-modify-write atomic; transactions give atomicity and a single [Mutex]
 * gives single-writer discipline, which is what keeps a phase-transition write from racing a
 * `rateSession` into `SQLiteDatabaseLockedException` under an unlucky interleaving. Reads take no lock
 * because WAL hands them a consistent snapshot concurrent with the writer — that is the entire reason
 * write-ahead logging is on: history must render while a live session writes behind it (§E.1).
 *
 * **`Dispatchers.IO` lives here and nowhere else.** Flows carry `.flowOn(Dispatchers.IO)` inside
 * [TableChangeNotifier.observing], matching `CalendarRepository.events()`; suspending reads and writes
 * carry `withContext`. **No `Cursor` ever crosses a dispatcher boundary** — every query materialises
 * its rows inside `use { }` before returning, because a cursor handed to the main thread is a leaked
 * native resource waiting for a `StrictMode` violation (§E.2).
 *
 * **Nothing degrades to empty.** Reads report through `Loadable.Failed`, writes through
 * [GymWrite.Failed]. Design §7.1: *an unreadable store must never render as 記録はありません* — which is
 * also why the corruption handler quarantines the file instead of letting the platform delete it, and
 * why [historyLost] makes the history surfaces fail loudly while the library still works (§E.6).
 */
internal class GymStore private constructor(
    private val appContext: Context,
    private val appVersionCode: Int,
) : GymRepository {

    private val notifier = TableChangeNotifier()
    private val helper = ExerciseDb(appContext, notifier, appVersionCode)
    private val writeLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Monotonic high-water mark of observed time; guards against system-clock rollback (§E.4). */
    @Volatile
    private var lastSeen: Long = 0L

    /**
     * True when an open found the file corrupt and quarantined it — held in [HistoryLoss] rather than
     * in a field here, because corruption is discovered by a platform callback that has no reference to
     * this object and most often fires long after construction.
     *
     * It gates the **history** surfaces only. §E.6's promise is that the user lands on a working 鍛錬
     * index with their presets intact and an honest fault strip saying the history is gone — failing
     * the whole feature would replace one lie with a dead screen.
     */
    private val historyLost: Boolean get() = HistoryLoss.isLost

    private val db: SQLiteDatabase get() = helper.writableDatabase

    init {
        // Blocking at construction, following `BlockadeRepository`, and safe for the same reason:
        // nothing here is on the first frame. The gym is reached through a long-press and two taps,
        // so there is no flash to prevent and no justification for a `runBlocking` anywhere else
        // (§E.2). This is also where the first-ever open runs `onCreate` and the seeder.
        HistoryLoss.restore(CorruptionFlag.isRaised(appContext))
        // Registered **before** the first open, because that open is itself a place corruption can be
        // discovered. A flag raised with nobody listening leaves every flow already collecting Ready
        // sitting on a stale value; this is what makes them re-run and report StoreCorrupt.
        HistoryLoss.watch {
            notifier.notify(TABLE_SESSION, TABLE_SESSION_RESULT, TABLE_PERSONAL_RECORD)
        }
        runCatching {
            val database = db
            lastSeen = maxOf(
                Meta.readLong(database, Meta.LAST_SEEN_MILLIS, 0L),
                System.currentTimeMillis(),
            )
            ExerciseCatalogSource.install(readExercises(database))
            if (Meta.readInt(database, Meta.PR_REBUILD_NEEDED, 0) == 1) {
                rebuildPersonalRecordsBlocking(database)
            }
        }
    }

    // ─── The guarded clock (§E.4) ───────────────────────────────────────────────────────────────

    /**
     * Guarded "now": never earlier than the highest time we have previously observed. Ported from
     * `BlockadeRepository.now()`, and it protects more here.
     *
     * Winding the system clock back cannot fabricate a streak, because `started_at` and `local_date`
     * are stamped from this and a session therefore always lands today. It cannot fabricate a personal
     * best either, but for a different and stronger reason: every duration is derived from
     * `elapsedRealtime()` deltas, which are monotonic, include deep sleep, and are untouched by the
     * system clock.
     *
     * Forward jumps are deliberately **not** guarded — a user flying east genuinely crosses into a new
     * day, and `tz_id` records which zone produced `local_date` so history stays interpretable.
     */
    private fun now(): Long = maxOf(System.currentTimeMillis(), lastSeen)

    /** Advances the high-water mark and persists it in the caller's transaction. */
    private fun SQLiteDatabase.touchNow(): Long {
        lastSeen = maxOf(System.currentTimeMillis(), lastSeen)
        Meta.write(this, Meta.LAST_SEEN_MILLIS, lastSeen.toString())
        return lastSeen
    }

    private fun today(): LocalDate = localDateOf(now(), ZoneId.systemDefault())

    // ─── Read and write plumbing ────────────────────────────────────────────────────────────────

    private fun <T> observe(vararg tables: String, query: (SQLiteDatabase) -> T): Flow<Loadable<T>> =
        notifier.observing(*tables) {
            runCatching { query(db) }
                .fold({ Loadable.Ready(it) }, { Loadable.Failed(it.toGymFault()) })
        }.onStart { emit(Loadable.Loading) }

    /** As [observe], but a quarantined database reports its loss rather than rendering as empty. */
    private fun <T> observeHistory(vararg tables: String, query: (SQLiteDatabase) -> T): Flow<Loadable<T>> =
        notifier.observing(*tables) {
            if (historyLost) {
                Loadable.Failed(GymFault.StoreCorrupt)
            } else {
                runCatching { query(db) }
                    .fold({ Loadable.Ready(it) }, { Loadable.Failed(it.toGymFault()) })
            }
        }.onStart { emit(Loadable.Loading) }

    private fun <T> observeRaw(vararg tables: String, fallback: T, query: (SQLiteDatabase) -> T): Flow<T> =
        notifier.observing(*tables) { runCatching { query(db) }.getOrDefault(fallback) }
            .onStart { emit(fallback) }

    private suspend fun <T> read(query: (SQLiteDatabase) -> T): Loadable<T> = withContext(Dispatchers.IO) {
        runCatching { query(db) }.fold({ Loadable.Ready(it) }, { Loadable.Failed(it.toGymFault()) })
    }

    /**
     * As [read], but a quarantined database reports its loss rather than answering honestly-and-wrongly
     * — the one-shot counterpart of [observeHistory], and it exists for the same reason.
     *
     * A quarantined store still opens: [HistoryLoss] means the *old* file was moved aside, so every
     * history query runs and returns nothing. For a list that is survivable, because a page can tell an
     * empty list is suspicious. For a **total** it is not: `Ready(LifetimeSummary(0, 0))` renders 〇回
     * over a destroyed decade and reads exactly like a user who has never trained (§E.6, and
     * `DECISIONS.md` §Q22's final paragraph).
     */
    private suspend fun <T> readHistory(query: (SQLiteDatabase) -> T): Loadable<T> =
        if (historyLost) Loadable.Failed(GymFault.StoreCorrupt) else read(query)

    /**
     * The one write path. Mutex, transaction, classify, and announce **after** the commit.
     *
     * Announcing inside the transaction is the subtle bug it exists to prevent: a reader woken while
     * the transaction is open takes a snapshot without the commit in it and renders the pre-write
     * state, which is indistinguishable from a lost write (§E.3).
     */
    private suspend fun <T> write(vararg tables: String, body: SQLiteDatabase.() -> T): GymWrite<T> =
        withContext(Dispatchers.IO) {
            writeLock.withLock {
                runCatching { db.transact { touchNow(); body() } }
                    .fold(
                        { notifier.notify(*tables); GymWrite.Ok(it) },
                        { GymWrite.Failed(it.toGymFault()) },
                    )
            }
        }

    override fun retry() = notifier.retry()

    /**
     * Lowers the history-loss fault, durably, because the user has read it.
     *
     * **Now declared on [GymRepository], which is the change that makes it reachable at all.** The
     * store is `internal` and reached only through that interface, so while the method lived here
     * alone nothing above the data layer could call it and the quarantine had no exit. See the
     * interface's KDoc for why it is not a retry and why the affordance still belongs to Phase 3's
     * 記録 tab rather than to any Phase 1 page.
     *
     * Acknowledgement is the *only* way out. It used to be finishing a session, which was wrong twice
     * over: one new session does not bring back a year of training, so the strip disappeared while the
     * history was still gone; and a corruption raised mid-session would have been cleared by that same
     * session's finish, hiding the very fault it had just caused (§E.6).
     */
    override suspend fun acknowledgeHistoryLoss() {
        HistoryLoss.clear()
        CorruptionFlag.clear(appContext)
        // The gate has moved, so the surfaces it gates have to be asked again.
        notifier.notify(TABLE_SESSION, TABLE_SESSION_RESULT, TABLE_PERSONAL_RECORD)
    }

    // ─── C.1 Reads ──────────────────────────────────────────────────────────────────────────────

    override val routines: StateFlow<Loadable<List<RoutineSummary>>> =
        observe(TABLE_ROUTINE, TABLE_ROUTINE_VERSION, TABLE_SESSION, TABLE_PERSONAL_RECORD) {
            readSummaries(it, archivedOnly = false)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), Loadable.Loading)

    override fun homeFeed(recentLimit: Int, builtInPreview: Int): Flow<Loadable<GymHomeFeed>> =
        observe(TABLE_ROUTINE, TABLE_ROUTINE_VERSION, TABLE_SESSION, TABLE_PERSONAL_RECORD) { database ->
            val all = readSummaries(database, archivedOnly = false)
            val lastResults = readLastResults(database)
            val recentCounts = readUsage(database, sinceDate = today().minusDays(RECENT_HABIT_DAYS))
            val work = readUniformWorkSeconds(database)
            val cards = all.associate {
                it.routineId to it.toCard(lastResults[it.routineId], work[it.versionId])
            }

            val recent = all
                .filter { (recentCounts[it.routineId] ?: 0) > 0 }
                .sortedByDescending { recentCounts[it.routineId] ?: 0 }
                .take(recentLimit)
                .mapNotNull { cards[it.routineId] }
            val builtIn = all.filter { it.builtIn }
            val user = all.filterNot { it.builtIn }

            GymHomeFeed(
                recent = recent,
                builtIn = builtIn.take(builtInPreview).mapNotNull { cards[it.routineId] },
                builtInTotal = builtIn.size,
                userRoutines = user.mapNotNull { cards[it.routineId] },
                everTrained = database.rawQuery(
                    "SELECT 1 FROM $TABLE_SESSION WHERE finished_at IS NOT NULL LIMIT 1",
                    null,
                ).use { it.moveToFirst() },
            )
        }

    override fun usageCounts(): Flow<Map<String, Int>> =
        observeRaw(TABLE_SESSION, fallback = emptyMap()) { readUsage(it, sinceDate = null) }

    override fun recentUsage(days: Int): Flow<Map<String, Int>> =
        observeRaw(TABLE_SESSION, fallback = emptyMap()) {
            readUsage(it, sinceDate = today().minusDays(days.toLong()))
        }

    override fun routineDetail(routineId: String): Flow<Loadable<RoutineDetail>> =
        observe(TABLE_ROUTINE, TABLE_ROUTINE_VERSION, TABLE_SESSION, TABLE_PERSONAL_RECORD) { database ->
            readRoutineDetail(database, routineId) ?: throw RoutineMissing(routineId)
        }

    override fun scaledTiers(routineId: String): Flow<List<RoutineSummary>> =
        observeRaw(TABLE_ROUTINE, TABLE_ROUTINE_VERSION, fallback = emptyList()) { database ->
            readSummaries(database, archivedOnly = false)
                .filter { it.routineId == routineId || it.scaledFrom(database) == routineId }
        }

    override suspend fun routineVersion(versionId: Long): Loadable<RoutineSnapshot> =
        read { database -> readSnapshot(database, versionId) ?: throw RoutineMissing("v$versionId") }

    // ─── C.2 The live session ───────────────────────────────────────────────────────────────────

    override suspend fun resumableSession(): Loadable<ResumableSession?> = read { database ->
        readLiveSession(database)
    }

    override suspend fun startSession(
        routineId: String,
        tier: String?,
        roundsPlanned: Int?,
    ): GymWrite<Long> = write(TABLE_SESSION) {
        val summary = readSummaries(this, archivedOnly = null)
            .firstOrNull { it.routineId == routineId } ?: throw RoutineMissing(routineId)
        val snapshot = readSnapshot(this, summary.versionId) ?: throw RoutineMissing(routineId)

        val startedAt = touchNow()
        val zone = ZoneId.systemDefault()
        val date = localDateOf(startedAt, zone)
        val elapsed = SystemClock.elapsedRealtime()
        val rounds = roundsPlanned ?: snapshot.rounds

        val values = ContentValues().apply {
            put("routine_id", routineId)
            put("routine_version_id", snapshot.versionId)
            // Denormalised on purpose: a つづき banner has to render for a session whose routine was
            // deleted, which is the one case the version foreign key cannot serve (§A.6).
            put("routine_name", snapshot.name)
            put("progression_step_id", currentStepId(this@write, snapshot.progressionProgramId))
            // The player owns the compiled timeline; the version's content address is what the store
            // can honestly assert about it, and it is what a recompile has to match.
            put("compiled_hash", snapshot.structuralHash)
            put("started_at", startedAt)
            put("local_date", storedDate(date))
            put("local_week_start", storedDate(weekStartOf(date)))
            put("tz_id", zone.id)
            put("started_at_elapsed", elapsed)
            put("boot_anchor_ms", System.currentTimeMillis() - elapsed)
            put("last_write_elapsed", elapsed)
            put("last_write_wall", startedAt)
            put("rounds_planned", rounds)
            // Frozen: open rep segments and skips can move a derived denominator, and the
            // 「二十種目中 八」 chip has to say the twenty that was on screen (§A.6).
            put("stations_planned", snapshot.stationCount * (rounds ?: 1).coerceAtLeast(1))
            put("tier", tier)
            // Normally zero. Non-zero is the breadcrumb for "my streak is wrong" (§E.4).
            put("clock_delta_ms", startedAt - System.currentTimeMillis())
            put("created_by_version", appVersionCode)
        }
        // A second live session is refused by idx_session_live rather than by a check-then-act read,
        // so two players can never both believe they own the clock (§A.6, §C.2).
        insertOrThrow(TABLE_SESSION, null, values)
    }

    override suspend fun recordSegment(sessionId: Long, r: SegmentResultDraft): GymWrite<Unit> =
        write(TABLE_SESSION, TABLE_SESSION_RESULT) {
            val exercise = r.exerciseId?.let { ExerciseCatalogSource.byId(it) }
            val values = ContentValues().apply {
                put("session_id", sessionId)
                put("ordinal", r.ordinal)
                put("round_index", r.round)
                // A rest segment has no station of its own; it is attributed to the station it follows
                // so the breakdown reads in the order it happened.
                put("station_order", r.stationIndex ?: 0)
                put("phase", r.phase.name)
                put("exercise_id", r.exerciseId)
                put("prescription_kind", prescriptionKindOf(r)?.name)
                put("prescribed_reps", r.prescribedReps)
                put("prescribed_sec", prescribedSecondsOf(r))
                put("actual_reps", if (r.skipped) null else r.actualReps)
                put("actual_duration_ms", r.actualMs)
                put("added_ms", r.addedMs)
                put("skipped", if (r.skipped) 1 else 0)
                // Frozen copies. A coefficient retuned in a later release must not move a chart the
                // user has already read (§A.0.5), and the player must not compute volume itself or
                // two code paths over one formula will drift (§D.5).
                put("difficulty_coef", exercise?.difficulty ?: 0.0)
                put(
                    "volume_units",
                    if (r.skipped || exercise == null) {
                        0.0
                    } else {
                        volumeUnits(exercise, r.actualReps, r.actualMs)
                    },
                )
                put("closed_at_wall", r.closedAtWallMs)
                put("closed_at_elapsed", r.closedAtElapsedMs)
            }
            // Idempotent under idx_result_session: a retry after a crash replaces its own row rather
            // than adding a second, which is what makes per-transition persistence safe (§A.7).
            insertWithOnConflict(TABLE_SESSION_RESULT, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            execSQL(
                "UPDATE $TABLE_SESSION SET last_write_elapsed = ?, last_write_wall = ? WHERE id = ?",
                arrayOf<Any?>(r.closedAtElapsedMs, r.closedAtWallMs, sessionId),
            )
            Unit
        }

    /**
     * §C.4's other half: ◁◁ steps back over a segment, and its row goes with it.
     *
     * The same two tables [recordSegment] announces, deliberately — a history surface that redrew on
     * the write must redraw on its withdrawal or it renders a result the store no longer holds — and
     * inside the same single-writer mutex, so a delete cannot interleave with the `recordSegment` the
     * user's next 済 is about to issue for the very same `(session_id, ordinal)`.
     *
     * **`last_write_elapsed` / `last_write_wall` are deliberately not moved.** They are the anchor
     * `recoveredActiveMs` credits time up to (§E.3 edge case 6), and there is no honest value to write:
     * the row being deleted carried the only timestamps involved, and stamping "now" would credit the
     * user for the seconds they spent stepping back. The transition that emits this effect emits
     * [checkpoint] immediately after it, which re-anchors the clock from the seek — so the pair leaves
     * recovery consistent without this statement guessing at a time.
     */
    override suspend fun deleteResult(sessionId: Long, ordinal: Int): GymWrite<Unit> =
        write(TABLE_SESSION, TABLE_SESSION_RESULT) {
            delete(TABLE_SESSION_RESULT, "session_id = ? AND ordinal = ?", arrayOf("$sessionId", "$ordinal"))
            Unit
        }

    override suspend fun checkpoint(
        sessionId: Long,
        c: PersistedClock,
        roundsCompleted: Int,
    ): GymWrite<Unit> = write(TABLE_SESSION) {
        execSQL(
            """
            UPDATE $TABLE_SESSION
               SET started_at_elapsed = ?, paused_accum_ms = ?, paused_at_elapsed = ?,
                   paused_at_wall = ?, boot_anchor_ms = ?, last_write_wall = ?,
                   last_write_elapsed = ?, rounds_completed = ?
             WHERE id = ? AND finished_at IS NULL
            """.trimIndent(),
            arrayOf<Any?>(
                c.startedAtElapsedMs,
                c.pausedAccumulatedMs,
                c.pausedAtElapsedMs,
                c.pausedAtWallMs,
                c.bootAnchorMs,
                c.anchorWallMs,
                SystemClock.elapsedRealtime(),
                roundsCompleted,
                sessionId,
            ),
        )
        Unit
    }

    /**
     * Closes the session in one transaction — clock, completion flags, progression advance, personal
     * records, and the previous-session baseline (§E.5).
     *
     * Two properties are load-bearing. The `AND finished_at IS NULL` guard makes the whole thing
     * idempotent, which is a real requirement rather than defensive coding: the quit sheet and the
     * completion path can genuinely race on a fast final station, and progression must advance exactly
     * once. And the previous session is read **inside** the transaction — read it after the commit and
     * this session is its own baseline, and the record screen renders 「前回より 0秒 速い」.
     *
     * `active_ms` is derived from the stored monotonic anchors rather than from a clock passed in,
     * because §C.2's signature carries none. That makes it the conservative last-transition figure —
     * exactly what `03-player.md` §E.3 specifies 記録する to use — and it means **the player must
     * `checkpoint` before finishing**, or the last segment's time is not yet on the row.
     */
    override suspend fun finishSession(sessionId: Long, complete: Boolean): GymWrite<SessionOutcome> =
        write(
            TABLE_SESSION,
            TABLE_SESSION_RESULT,
            TABLE_PERSONAL_RECORD,
            TABLE_PROGRESSION_STATE,
        ) {
            val open = readSessionRow(this, sessionId) ?: throw SessionMissing(sessionId)
            val results = readResults(this, sessionId)

            if (open.finishedAt == null) {
                val snapshot = readSnapshot(this, open.routineVersionId)
                val activeMs = recoveredActiveMs(open.clock, open.lastWriteElapsed)
                val wallMs = maxOf(activeMs, open.lastWriteWall - open.startedAt)
                val stationsCompleted = results.count {
                    (it.phase == Phase.WORK || it.phase == Phase.REPS) && !it.skipped
                }
                val roundsCompleted = maxOf(
                    open.roundsCompleted,
                    results.filter { it.phase != Phase.REST }.maxOfOrNull { it.roundIndex + 1 } ?: 0,
                )
                // An AMRAP that stopped at minute twelve of a twenty-minute cap is not a rounds
                // record, so the flag is only ever set by a session that ran the cap out (§D.2).
                val reachedCap = complete && snapshot?.timeCapSeconds != null

                execSQL(
                    """
                    UPDATE $TABLE_SESSION
                       SET finished_at = ?, active_ms = ?, wall_ms = ?, rounds_completed = ?,
                           stations_completed = ?, complete = ?, reached_time_cap = ?
                     WHERE id = ? AND finished_at IS NULL
                    """.trimIndent(),
                    arrayOf<Any?>(
                        touchNow(),
                        activeMs,
                        wallMs,
                        roundsCompleted,
                        stationsCompleted,
                        if (complete) 1 else 0,
                        if (reachedCap) 1 else 0,
                        sessionId,
                    ),
                )
                // Only a complete session advances anything — `00-plan.md` §4.1 rule 2, and the same
                // line `prEligible` draws for records. The rows are handed down rather than re-read:
                // `ALL_SETS_MADE` judges this session's own sets, and a second read inside the same
                // transaction would be a second scan of the table this function has already scanned.
                if (complete) advanceProgression(this, open, snapshot, results)
            }

            val summary = readSummary(this, sessionId) ?: throw SessionMissing(sessionId)
            val previous = readPreviousSession(this, summary)
            val newBests = if (open.finishedAt == null) evaluateBests(this, summary) else emptyList()

            // Note what is deliberately **not** here: clearing the corruption flag. Finishing one
            // session does not restore a year, §E.6 keeps the fault raised until it is acknowledged,
            // and clearing it from inside this transaction would also un-flag a corruption that
            // happened *during* this very session. See [acknowledgeHistoryLoss].

            SessionOutcome(
                summary = summary,
                results = readResults(this, sessionId),
                snapshot = readSnapshot(this, summary.routineVersionId),
                previous = previous,
                newBests = newBests,
                progression = readSnapshot(this, summary.routineVersionId)
                    ?.progressionProgramId
                    ?.let { readProgression(this, it) },
                streakDays = runCatching { readStreak(this).days }.getOrNull(),
            )
        }

    override suspend fun rateSession(sessionId: Long, rating: Rating?): GymWrite<Unit> =
        write(TABLE_SESSION) {
            execSQL(
                "UPDATE $TABLE_SESSION SET rating = ?, rating_cr10 = ? WHERE id = ?",
                // The CR10 mapping is frozen onto the row. If a later version decides きつい is an 8,
                // every historical chart must stay exactly where it is (§A.0.5).
                arrayOf<Any?>(rating?.name, rating?.cr10, sessionId),
            )
            Unit
        }

    override suspend fun discardSession(sessionId: Long): GymWrite<Unit> =
        write(TABLE_SESSION, TABLE_SESSION_RESULT, TABLE_PERSONAL_RECORD) {
            // session_result and personal_record follow by cascade: 記録せずに終える must remove the row
            // and its children atomically, or a record survives pointing at nothing (§A.7).
            delete(TABLE_SESSION, "id = ?", arrayOf(sessionId.toString()))
            // The cascade removes the record but cannot award it to whoever held it before, and
            // `04` §4 edge case 8 is explicit that there is no orphan case — a best whose session was
            // deleted simply becomes a different session's. The cache is derived, so re-deriving it is
            // the whole remedy.
            rebuildPersonalRecordsBlocking(this)
            Unit
        }

    // ─── C.3 History and records ────────────────────────────────────────────────────────────────

    override fun history(cursor: HistoryCursor?, limit: Int): Flow<Loadable<List<SessionSummary>>> =
        observeHistory(TABLE_SESSION, TABLE_SESSION_RESULT, TABLE_PERSONAL_RECORD) { database ->
            // Keyset, never OFFSET: OFFSET skips or duplicates rows when a session is deleted
            // mid-scroll, and both columns are needed because a restored backup can tie on started_at.
            val keyset = if (cursor == null) {
                "" to emptyArray<String>()
            } else {
                "AND (s.started_at < ? OR (s.started_at = ? AND s.id < ?))" to arrayOf(
                    cursor.startedAt.toString(),
                    cursor.startedAt.toString(),
                    cursor.sessionId.toString(),
                )
            }
            database.rawQuery(
                "$SUMMARY_SQL WHERE s.finished_at IS NOT NULL ${keyset.first} " +
                    "ORDER BY s.started_at DESC, s.id DESC LIMIT ?",
                keyset.second + limit.toString(),
            ).mapRows(::summaryRow)
        }

    override fun attemptsForRoutine(routineId: String, limit: Int): Flow<Loadable<List<SessionSummary>>> =
        observeHistory(TABLE_SESSION, TABLE_SESSION_RESULT, TABLE_PERSONAL_RECORD) { database ->
            readAttempts(database, routineId, limit)
        }

    /**
     * **[observeHistory], never [observeRaw].** This count is what 削除 branches on, and the fallback
     * this used to carry turned every unreadable store into a confident zero — the page then printed
     * 「やった記録はありません。完全に消えます。」 over a routine with a decade behind it and offered
     * 完全に削除 (see [GymRepository.countForRoutine], and the guard inside [purgeRoutine] that had to
     * defend the data from it). A `Loadable` lets the page say 記録を読めません instead.
     *
     * [observeHistory] rather than plain [observe] because the rows being counted are exactly the ones
     * a quarantined database has lost: on `historyLost` a truthful `SELECT COUNT(*)` returns zero and
     * would be indistinguishable from a routine that was never performed. `StoreCorrupt` is the honest
     * answer, and it withholds the destructive branch, which is the conservative direction.
     */
    override fun countForRoutine(routineId: String): Flow<Loadable<Int>> =
        observeHistory(TABLE_SESSION) { database ->
            database.rawQuery(
                "SELECT COUNT(*) FROM $TABLE_SESSION WHERE routine_id = ? AND finished_at IS NOT NULL",
                arrayOf(routineId),
            ).firstRow { it.getInt(0) } ?: 0
        }

    /**
     * One scan of `session`, two aggregates, no new table and no schema change (`DECISIONS.md` §Q22).
     *
     * `COALESCE(SUM(active_ms), 0)` because `SUM` over zero rows is **NULL**, not zero — a first-run
     * user would otherwise take the `getLong` on a null column, and the distinction that matters here
     * ("no sessions" versus "the store is unreadable") is [readHistory]'s to make, not a null's.
     *
     * `WHERE finished_at IS NOT NULL` matches every other history read in this file: an open row is a
     * session in progress, and counting it would make これまで tick upward mid-workout.
     *
     * *Rejected* — a maintained counter row in `meta`. It is the only way to answer this without a
     * scan, and it buys nothing real: the table is one row per session, the query is covered by the
     * ordinary session index, and a denormalised total is a second source of truth that goes wrong
     * silently on every delete path (`discardSession`, `purgeRoutine`, the corruption quarantine).
     */
    override suspend fun summary(): Loadable<LifetimeSummary> = readHistory { database ->
        database.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(active_ms), 0) FROM $TABLE_SESSION WHERE finished_at IS NOT NULL",
            null,
        ).firstRow { LifetimeSummary(sessions = it.getInt(0), totalActiveMs = it.getLong(1)) }
            ?: LifetimeSummary(sessions = 0, totalActiveMs = 0L)
    }

    override suspend fun populatedMonths(): Loadable<List<YearMonth>> = read { database ->
        // substr on an ISO date is a string operation, not a date function — no UTC involved (§0).
        database.rawQuery(
            """
            SELECT DISTINCT substr(local_date, 1, 7) FROM $TABLE_SESSION
            WHERE finished_at IS NOT NULL ORDER BY 1 DESC
            """.trimIndent(),
            null,
        ).mapRows { YearMonth.parse(it.getString(0)) }
    }

    override fun sessionDetail(sessionId: Long): Flow<Loadable<SessionDetail>> =
        observeHistory(TABLE_SESSION, TABLE_SESSION_RESULT, TABLE_PERSONAL_RECORD) { database ->
            val summary = readSummary(database, sessionId) ?: throw SessionMissing(sessionId)
            val best = readBests(database).firstOrNull { it.routineId == summary.routineId }
            SessionDetail(
                summary = summary,
                results = readResults(database, sessionId),
                snapshot = readSnapshot(database, summary.routineVersionId),
                previous = readPreviousSession(database, summary),
                best = best,
                // Decided on read rather than stored, so beating an old record demotes the old row's
                // chip with no migration and no orphan (§4 edge case 2).
                isStillBest = best?.sessionId == sessionId,
                routineArchived = database.rawQuery(
                    "SELECT archived_at FROM $TABLE_ROUTINE WHERE id = ?",
                    arrayOf(summary.routineId),
                ).firstRow { !it.isNull(0) } ?: true,
            )
        }

    override fun monthLoad(month: YearMonth): Flow<Loadable<Map<LocalDate, DayLoad>>> =
        observeHistory(TABLE_SESSION) { database ->
            val scale = readLoadScale(database, LOAD_SCALE_DAYS)
            val from = month.atDay(1)
            val to = month.plusMonths(1).atDay(1)
            database.rawQuery(
                """
                SELECT local_date, SUM(active_ms) AS day_ms, COUNT(*) AS sessions
                FROM $TABLE_SESSION
                WHERE finished_at IS NOT NULL AND local_date >= ? AND local_date < ?
                GROUP BY local_date
                """.trimIndent(),
                arrayOf(storedDate(from), storedDate(to)),
            ).mapRows { c ->
                val date = parseStoredDate(c.getString(0))
                val ms = c.getLong(1)
                date to DayLoad(activeMs = ms, sessions = c.getInt(2), bucket = bucketOf(ms, scale))
            }.mapNotNull { (date, load) -> date?.let { it to load } }.toMap()
        }

    override fun loadScale(days: Int): Flow<LoadScale> =
        observeRaw(TABLE_SESSION, fallback = loadScaleOf(emptyList())) { readLoadScale(it, days) }

    override fun streak(): Flow<Loadable<Streak>> =
        observeHistory(TABLE_SESSION, TABLE_TRAINING_PLAN) { readStreak(it) }

    override fun routineBests(): Flow<Loadable<List<RoutineBest>>> =
        observeHistory(TABLE_PERSONAL_RECORD, TABLE_SESSION, TABLE_ROUTINE) { readBests(it) }

    override fun movementBests(): Flow<Loadable<List<MovementBest>>> =
        observeHistory(TABLE_SESSION_RESULT, TABLE_SESSION) { readMovementBests(it) }

    override fun exerciseBests(): Flow<Loadable<List<MovementBest>>> =
        observeHistory(TABLE_SESSION_RESULT, TABLE_SESSION) { readExerciseBests(it) }

    override fun weeklySeries(weeks: Int): Flow<Loadable<List<WeekPoint>>> =
        observeHistory(TABLE_SESSION) { database ->
            val from = weekStartOf(today()).minusWeeks((weeks - 1).toLong())
            val rows = database.rawQuery(
                """
                SELECT local_week_start, COUNT(*) AS sessions,
                       SUM(active_ms) / 60000 AS active_minutes, SUM(complete) AS complete_sessions
                FROM $TABLE_SESSION
                WHERE finished_at IS NOT NULL AND local_week_start >= ?
                GROUP BY local_week_start ORDER BY local_week_start ASC
                """.trimIndent(),
                arrayOf(storedDate(from)),
            ).mapRows { c ->
                val monday = parseStoredDate(c.getString(0))
                monday to WeekPoint(
                    weekStart = monday ?: from,
                    sessions = c.getInt(1),
                    activeMinutes = c.getInt(2),
                    completeSessions = c.getInt(3),
                )
            }.mapNotNull { (monday, point) -> monday?.let { it to point } }.toMap()
            // Weeks with no sessions do not appear in the result and must still be drawn, or a
            // fortnight off silently closes up and reads as a fortnight on (§D.4).
            zeroFilledWeeks(today(), weeks, rows)
        }

    override fun volumeSeries(days: Int): Flow<Loadable<List<DayVolume>>> =
        observeHistory(TABLE_SESSION, TABLE_SESSION_RESULT) { database ->
            val from = today().minusDays((days - 1).toLong())
            database.rawQuery(
                """
                SELECT s.local_date, SUM(r.volume_units) AS volume
                FROM $TABLE_SESSION s JOIN $TABLE_SESSION_RESULT r ON r.session_id = s.id
                WHERE s.finished_at IS NOT NULL AND r.skipped = 0 AND s.local_date >= ?
                GROUP BY s.local_date ORDER BY s.local_date ASC
                """.trimIndent(),
                arrayOf(storedDate(from)),
            ).mapRows { c -> parseStoredDate(c.getString(0)) to c.getDouble(1) }
                .mapNotNull { (date, volume) -> date?.let { DayVolume(it, volume) } }
        }

    override fun trainingLoad(): Flow<Loadable<TrainingLoad?>> =
        observeHistory(TABLE_SESSION) { database ->
            val to = today()
            val from = to.minusDays((ACWR_CHRONIC_DAYS - 1).toLong())
            val byDate = readDailyLoad(database, from, to)
            val first = database.rawQuery(
                "SELECT MIN(local_date) FROM $TABLE_SESSION WHERE finished_at IS NOT NULL",
                null,
            ).firstRow { parseStoredDate(it.stringOrNull(0)) }
            if (byDate.isEmpty() || first == null) {
                null
            } else {
                val spine = zeroFilledLoad(from, to, byDate)
                val historyDays = ChronoUnit.DAYS.between(first, to).toInt() + 1
                TrainingLoad(
                    recent = spine,
                    monotony7d = monotony(spine.takeLast(MONOTONY_WINDOW_DAYS)),
                    // Suppressed entirely below twenty-eight days: a ratio computed from a fortnight
                    // is noise wearing a number's clothes.
                    acwr = if (historyDays >= ACWR_CHRONIC_DAYS) acwr(spine) else null,
                    historyDays = historyDays,
                )
            }
        }

    // ─── C.4 Authoring ──────────────────────────────────────────────────────────────────────────

    override suspend fun saveRoutine(draft: RoutineDraft): GymWrite<String> =
        write(TABLE_ROUTINE, TABLE_ROUTINE_VERSION) {
            execSQL("PRAGMA defer_foreign_keys = ON")
            val existing = draft.routineId?.let { readRoutineRow(this, it) }
            // Editing a built-in is a copy-on-write into a new user routine: the preset stays pristine
            // so a later app version can update it without fighting the user's edit, and 写して作る is
            // literally the same operation, so there is one code path (§A.0.3).
            val targetId = when {
                existing == null -> newRoutineId()
                existing.builtIn -> newRoutineId()
                else -> existing.id
            }
            val isCopy = existing != null && existing.builtIn
            val versionId = insertVersion(this, targetId, draft)
            if (existing == null || isCopy) {
                insertOrThrow(
                    TABLE_ROUTINE,
                    null,
                    ContentValues().apply {
                        put("id", targetId)
                        put("head_version_id", versionId)
                        put("built_in", 0)
                        put("tier", existing?.tier)
                        put("derived_from_routine_id", existing?.id)
                        put("origin", existing?.origin)
                        put("catalog_version", 0)
                        put("sort_order", nextUserSortOrder(this@write))
                        put("favourite", 0)
                        put("created_at", now())
                    },
                )
            } else {
                execSQL(
                    "UPDATE $TABLE_ROUTINE SET head_version_id = ? WHERE id = ?",
                    arrayOf<Any?>(versionId, targetId),
                )
            }
            targetId
        }

    override suspend fun duplicateRoutine(routineId: String, newName: String): GymWrite<String> =
        write(TABLE_ROUTINE, TABLE_ROUTINE_VERSION) {
            execSQL("PRAGMA defer_foreign_keys = ON")
            val source = readRoutineRow(this, routineId) ?: throw RoutineMissing(routineId)
            val snapshot = readSnapshot(this, source.headVersionId) ?: throw RoutineMissing(routineId)
            val targetId = newRoutineId()
            val versionId = insertVersion(this, targetId, snapshot.toDraft(newName))
            insertOrThrow(
                TABLE_ROUTINE,
                null,
                ContentValues().apply {
                    put("id", targetId)
                    put("head_version_id", versionId)
                    put("built_in", 0)
                    put("tier", source.tier)
                    put("derived_from_routine_id", source.id)
                    put("origin", source.origin)
                    put("catalog_version", 0)
                    put("sort_order", nextUserSortOrder(this@write))
                    put("favourite", 0)
                    put("created_at", now())
                },
            )
            targetId
        }

    override suspend fun archiveRoutine(routineId: String): GymWrite<Unit> = write(TABLE_ROUTINE) {
        // Soft delete, never hard: sessions hold a foreign key and history must stay readable (§C.4).
        execSQL(
            "UPDATE $TABLE_ROUTINE SET archived_at = ? WHERE id = ?",
            arrayOf<Any?>(now(), routineId),
        )
        Unit
    }

    override suspend fun restoreRoutine(routineId: String): GymWrite<Unit> = write(TABLE_ROUTINE) {
        execSQL("UPDATE $TABLE_ROUTINE SET archived_at = NULL WHERE id = ?", arrayOf<Any?>(routineId))
        Unit
    }

    override suspend fun purgeRoutine(routineId: String): GymWrite<Unit> =
        write(TABLE_ROUTINE, TABLE_ROUTINE_VERSION) {
            // The local guard, and it is not redundant with the foreign keys.
            //
            // The guard upstream is `countForRoutine == Ready(0)`, which since the count became a
            // `Loadable` can no longer confuse an unreadable store with an unperformed routine — but
            // it still counts only *finished* sessions, while this counts all of them, so an open
            // session on an otherwise-untouched routine reaches here and is refused. Without this the
            // invariant is enforced only by a deferred FK check raised at COMMIT, from inside
            // `transact`'s `finally` after `setTransactionSuccessful`, which is both a strange place to
            // discover it and unreadable at the point the deletes are written.
            val sessions = rawQuery(
                "SELECT COUNT(*) FROM $TABLE_SESSION WHERE routine_id = ?",
                arrayOf(routineId),
            ).firstRow { it.getInt(0) } ?: 0
            if (sessions > 0) throw RoutineHasHistory(routineId)

            // routine and routine_version RESTRICT each other, so neither delete can be first.
            // Deferring resolves both at commit — and the FK check stays as the backstop for anything
            // the count above cannot see.
            execSQL("PRAGMA defer_foreign_keys = ON")
            delete(TABLE_ROUTINE_VERSION, "routine_id = ?", arrayOf(routineId))
            delete(TABLE_ROUTINE, "id = ?", arrayOf(routineId))
            Unit
        }

    override suspend fun setFavourite(routineId: String, favourite: Boolean): GymWrite<Unit> =
        write(TABLE_ROUTINE) {
            execSQL(
                "UPDATE $TABLE_ROUTINE SET favourite = ? WHERE id = ?",
                arrayOf<Any?>(if (favourite) 1 else 0, routineId),
            )
            Unit
        }

    /**
     * Records that a routine was opened.
     *
     * **Nothing reads it yet, and that is deliberate.** よく使う is habit, so it is derived from
     * finished sessions over a trailing window (§D.7) rather than from taps — a routine you opened and
     * backed out of is not one you use. The mark is kept anyway, in `exercise_meta` where a new key is
     * a write rather than a migration, so a future "recently opened" ordering needs no schema change.
     */
    override suspend fun touchRoutine(routineId: String) {
        write(TABLE_ROUTINE) { Meta.write(this, "last_touched_$routineId", now().toString()) }
    }

    override fun progression(programId: String): Flow<Loadable<ProgressionState>> =
        observe(TABLE_PROGRESSION, TABLE_PROGRESSION_STEP, TABLE_PROGRESSION_STATE) { database ->
            readProgression(database, programId) ?: throw RoutineMissing(programId)
        }

    override suspend fun setProgressionStep(programId: String, stepIndex: Int): GymWrite<Unit> =
        write(TABLE_PROGRESSION_STATE) {
            execSQL(
                """
                UPDATE $TABLE_PROGRESSION_STATE
                   SET current_step_index = ?, sessions_at_step = 0, step_entered_at = ?
                 WHERE program_id = ?
                """.trimIndent(),
                arrayOf<Any?>(stepIndex, now(), programId),
            )
            Unit
        }

    override suspend fun rebuildPersonalRecords(): GymWrite<Unit> =
        write(TABLE_PERSONAL_RECORD) {
            rebuildPersonalRecordsBlocking(this)
            Unit
        }

    override suspend fun setTrainingPlan(daysMask: Int, forgivenessPerMonth: Int): GymWrite<Unit> =
        write(TABLE_TRAINING_PLAN) {
            // Keyed by the day it takes effect, so changing the plan cannot rewrite last month's
            // streak — the same trap as routine versioning, one table over (§A.8).
            insertWithOnConflict(
                TABLE_TRAINING_PLAN,
                null,
                ContentValues().apply {
                    put("effective_from", storedDate(today()))
                    put("days_mask", daysMask)
                    put("forgiveness_per_month", forgivenessPerMonth)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            Unit
        }

    // ─── Row readers ────────────────────────────────────────────────────────────────────────────

    private fun readExercises(database: SQLiteDatabase): List<Exercise> = database.rawQuery(
        """
        SELECT id, name_ja, name_en, pattern, seconds_per_rep, difficulty, is_isometric, cue,
               ladder_id, catalog_version, archived
        FROM $TABLE_EXERCISE
        """.trimIndent(),
        null,
    ).mapRows { c ->
        Exercise(
            id = c.getString(0),
            nameJa = c.getString(1),
            nameEn = c.getString(2),
            // An unclassifiable pattern is a corrupt row, not a default: CORE would quietly change the
            // builder's adjacency warning. It is dropped from the catalogue instead.
            pattern = Pattern.fromStorage(c.getString(3)) ?: return@mapRows null,
            secondsPerRep = c.getDouble(4),
            difficulty = c.getDouble(5),
            isIsometric = c.bool(6),
            cue = c.stringOrNull(7),
            ladderId = c.stringOrNull(8),
            catalogVersion = c.getInt(9),
            archived = c.bool(10),
        )
    }.filterNotNull()

    /**
     * §D.7's single join: identity, head version, session aggregate and record in one query rather
     * than an N+1 over routines.
     *
     * **Note the bare `active_ms` beside `MAX(started_at)` in the aggregate subquery.** In standard SQL
     * that is undefined; **SQLite guarantees it** — when a query contains exactly one `min()` or
     * `max()` aggregate, bare columns take their values from the row that produced the extremum. It is
     * documented behaviour and it gives the 「前回より 二十二秒 速い」 baseline for free. Do not "fix" it
     * into a correlated subquery.
     */
    private fun readSummaries(database: SQLiteDatabase, archivedOnly: Boolean?): List<RoutineSummary> {
        val filter = when (archivedOnly) {
            true -> "WHERE r.archived_at IS NOT NULL"
            false -> "WHERE r.archived_at IS NULL"
            null -> ""
        }
        return database.rawQuery(
            """
            SELECT r.id, r.built_in, r.tier, r.origin, r.favourite, r.sort_order, r.archived_at,
                   v.id, v.name, v.engine, v.rounds, v.time_cap_sec, v.interval_sec,
                   v.rest_between_stations, v.rest_between_rounds,
                   v.station_count, v.est_duration_sec, v.est_total_reps, v.primary_metric,
                   agg.times_done, agg.last_started_at, agg.last_active_ms,
                   pr.value, pr.tiebreak, pr.local_date, pr.session_id, pr.achieved_at,
                   (SELECT s2.routine_version_id FROM $TABLE_SESSION s2 WHERE s2.id = pr.session_id)
            FROM $TABLE_ROUTINE r
            JOIN $TABLE_ROUTINE_VERSION v ON v.id = r.head_version_id
            LEFT JOIN (
                SELECT routine_id, COUNT(*) AS times_done, MAX(started_at) AS last_started_at,
                       active_ms AS last_active_ms
                  FROM $TABLE_SESSION WHERE finished_at IS NOT NULL GROUP BY routine_id
            ) agg ON agg.routine_id = r.id
            LEFT JOIN $TABLE_PERSONAL_RECORD pr ON pr.routine_id = r.id AND pr.metric = v.primary_metric
            $filter
            ORDER BY r.built_in ASC, r.sort_order ASC
            """.trimIndent(),
            null,
        ).mapRows { c ->
            val engine = Engine.fromStorage(c.getString(9)) ?: return@mapRows null
            val metric = BestMetric.fromStorage(c.getString(18)) ?: return@mapRows null
            val timesDone = c.intOrNull(19) ?: 0
            RoutineSummary(
                routineId = c.getString(0),
                versionId = c.getLong(7),
                name = c.getString(8),
                engine = engine,
                builtIn = c.bool(1),
                tier = Tier.fromStorage(c.stringOrNull(2)),
                origin = c.stringOrNull(3),
                favourite = c.bool(4),
                sortOrder = c.getInt(5),
                rounds = c.intOrNull(10),
                timeCapSeconds = c.intOrNull(11),
                intervalSeconds = c.intOrNull(12),
                restBetweenStations = c.getInt(13),
                restBetweenRounds = c.getInt(14),
                stationCount = c.getInt(15),
                estimatedDurationSeconds = c.getInt(16),
                estimatedTotalReps = c.getInt(17),
                primaryMetric = metric,
                timesDone = timesDone,
                lastStartedAt = c.longOrNull(20),
                lastActiveMs = c.longOrNull(21),
                best = c.longOrNull(25)?.let { prSession ->
                    RoutineBest(
                        routineId = c.getString(0),
                        routineName = c.getString(8),
                        engine = engine,
                        metric = metric,
                        value = c.getDouble(22),
                        tiebreak = c.getDouble(23),
                        sessionId = prSession,
                        achievedAt = c.getLong(26),
                        localDate = parseStoredDate(c.getString(24)) ?: EPOCH_DATE,
                        timesDone = timesDone,
                        routineArchived = !c.isNull(6),
                        // The record was set on a version that is no longer the head. Whether that
                        // invalidates it is a policy the app cannot get right, so it states the fact
                        // and declines the judgement (§4 edge case 4).
                        structureChanged = c.longOrNull(27) != c.getLong(7),
                    )
                },
                archivedAt = c.longOrNull(6),
            )
        }.filterNotNull()
    }

    private fun readRoutineDetail(database: SQLiteDatabase, routineId: String): RoutineDetail? {
        val summary = readSummaries(database, archivedOnly = null)
            .firstOrNull { it.routineId == routineId } ?: return null
        val row = readRoutineRow(database, routineId) ?: return null
        val snapshot = readSnapshot(database, summary.versionId) ?: return null
        return RoutineDetail(
            routineId = routineId,
            builtIn = row.builtIn,
            tier = Tier.fromStorage(row.tier),
            origin = row.origin,
            favourite = summary.favourite,
            derivedFromRoutineId = row.derivedFrom,
            scaledFromRoutineId = row.scaledFrom,
            createdAt = row.createdAt,
            archivedAt = summary.archivedAt,
            snapshot = snapshot,
            bests = readBests(database).filter { it.routineId == routineId },
            recentAttempts = readAttempts(database, routineId, ATTEMPT_PREVIEW),
            timesDone = summary.timesDone,
        )
    }

    private fun readSnapshot(database: SQLiteDatabase, versionId: Long): RoutineSnapshot? {
        val stations = database.rawQuery(
            """
            SELECT position, exercise_id, prescription_kind, prescribed_reps, prescribed_sec, note
            FROM $TABLE_ROUTINE_STATION WHERE routine_version_id = ? ORDER BY position ASC
            """.trimIndent(),
            arrayOf(versionId.toString()),
        ).mapRows { c ->
            val exerciseId = c.getString(1)
            RoutineStation(
                position = c.getInt(0),
                exerciseId = exerciseId,
                // Null rather than a throw: a routine referencing an id the catalogue no longer knows
                // still has to list, and the detail page says 種目 一件が不明 and disables 始める.
                exercise = ExerciseCatalogSource.byId(exerciseId),
                measure = Measure.fromStorage(c.getString(2)) ?: return@mapRows null,
                prescribedReps = c.intOrNull(3),
                prescribedSeconds = c.intOrNull(4),
                note = c.stringOrNull(5),
            )
        }.filterNotNull()

        return database.rawQuery(
            """
            SELECT id, routine_id, version_number, name, engine, rounds, time_cap_sec, interval_sec,
                   rest_between_stations, rest_between_rounds, prepare_sec, progression_program_id,
                   primary_metric, station_count, est_duration_sec, est_total_reps, structural_hash,
                   created_at
            FROM $TABLE_ROUTINE_VERSION WHERE id = ?
            """.trimIndent(),
            arrayOf(versionId.toString()),
        ).firstRow { c ->
            RoutineSnapshot(
                versionId = c.getLong(0),
                routineId = c.getString(1),
                versionNumber = c.getInt(2),
                name = c.getString(3),
                engine = Engine.fromStorage(c.getString(4)) ?: Engine.INTERVAL_CIRCUIT,
                rounds = c.intOrNull(5),
                timeCapSeconds = c.intOrNull(6),
                intervalSeconds = c.intOrNull(7),
                restBetweenStations = c.getInt(8),
                restBetweenRounds = c.getInt(9),
                prepareSeconds = c.getInt(10),
                progressionProgramId = c.stringOrNull(11),
                primaryMetric = BestMetric.fromStorage(c.getString(12)) ?: BestMetric.MOST_VOLUME,
                stationCount = c.getInt(13),
                estimatedDurationSeconds = c.getInt(14),
                estimatedTotalReps = c.getInt(15),
                structuralHash = c.getInt(16),
                createdAt = c.getLong(17),
                stations = stations,
            )
        }
    }

    private data class RoutineRow(
        val id: String,
        val headVersionId: Long,
        val builtIn: Boolean,
        val tier: String?,
        val origin: String?,
        val derivedFrom: String?,
        val scaledFrom: String?,
        val createdAt: Long,
    )

    private fun readRoutineRow(database: SQLiteDatabase, routineId: String): RoutineRow? =
        database.rawQuery(
            """
            SELECT id, head_version_id, built_in, tier, origin, derived_from_routine_id,
                   scaled_from_routine_id, created_at
            FROM $TABLE_ROUTINE WHERE id = ?
            """.trimIndent(),
            arrayOf(routineId),
        ).firstRow { c ->
            RoutineRow(
                id = c.getString(0),
                headVersionId = c.getLong(1),
                builtIn = c.bool(2),
                tier = c.stringOrNull(3),
                origin = c.stringOrNull(4),
                derivedFrom = c.stringOrNull(5),
                scaledFrom = c.stringOrNull(6),
                createdAt = c.getLong(7),
            )
        }

    private fun RoutineSummary.scaledFrom(database: SQLiteDatabase): String? =
        readRoutineRow(database, routineId)?.scaledFrom

    private fun readUsage(database: SQLiteDatabase, sinceDate: LocalDate?): Map<String, Int> {
        val where = if (sinceDate == null) "" else "AND local_date >= ?"
        val args = if (sinceDate == null) null else arrayOf(storedDate(sinceDate))
        return database.rawQuery(
            """
            SELECT routine_id, COUNT(*) FROM $TABLE_SESSION
            WHERE finished_at IS NOT NULL $where GROUP BY routine_id
            """.trimIndent(),
            args,
        ).mapRows { it.getString(0) to it.getInt(1) }.toMap()
    }

    private fun readLastResults(database: SQLiteDatabase): Map<String, LastResult> = database.rawQuery(
        """
        SELECT s.routine_id, MAX(s.started_at) AS started_at, s.id, s.active_ms, s.rounds_completed,
               s.complete, v.engine
        FROM $TABLE_SESSION s JOIN $TABLE_ROUTINE_VERSION v ON v.id = s.routine_version_id
        WHERE s.finished_at IS NOT NULL
        GROUP BY s.routine_id
        """.trimIndent(),
        null,
    ).mapRows { c ->
        // Same single-max bare-column guarantee as §D.7's aggregate: every column here comes from the
        // most recent session's row, not from an arbitrary one.
        val engine = Engine.fromStorage(c.getString(6)) ?: return@mapRows null
        c.getString(0) to LastResult(
            sessionId = c.getLong(2),
            startedAt = c.getLong(1),
            activeMs = c.getLong(3),
            roundsCompleted = c.getInt(4),
            complete = c.bool(5),
            engine = engine,
        )
    }.filterNotNull().toMap()

    private fun readAttempts(
        database: SQLiteDatabase,
        routineId: String,
        limit: Int,
    ): List<SessionSummary> = database.rawQuery(
        "$SUMMARY_SQL WHERE s.routine_id = ? AND s.finished_at IS NOT NULL " +
            "ORDER BY s.started_at DESC, s.id DESC LIMIT ?",
        arrayOf(routineId, limit.toString()),
    ).mapRows(::summaryRow)

    private fun readSummary(database: SQLiteDatabase, sessionId: Long): SessionSummary? =
        database.rawQuery("$SUMMARY_SQL WHERE s.id = ?", arrayOf(sessionId.toString()))
            .firstRow(::summaryRow)

    /**
     * The session immediately before [of] **for the same routine and the same tier** — not the most
     * recent overall.
     *
     * Reopening a March record must not compare it to yesterday, and comparing an Rx run to a やさしい
     * run is meaningless (`04` §4 edge case 3).
     */
    private fun readPreviousSession(database: SQLiteDatabase, of: SessionSummary): SessionSummary? {
        val tierClause = if (of.tier == null) "AND s.tier IS NULL" else "AND s.tier = ?"
        val args = buildList {
            add(of.routineId)
            of.tier?.let { add(it) }
            add(of.startedAt.toString())
            add(of.startedAt.toString())
            add(of.sessionId.toString())
        }
        return database.rawQuery(
            "$SUMMARY_SQL WHERE s.routine_id = ? $tierClause AND s.finished_at IS NOT NULL " +
                "AND (s.started_at < ? OR (s.started_at = ? AND s.id < ?)) " +
                "ORDER BY s.started_at DESC, s.id DESC LIMIT 1",
            args.toTypedArray(),
        ).firstRow(::summaryRow)
    }

    private fun summaryRow(c: Cursor): SessionSummary = SessionSummary(
        sessionId = c.getLong(0),
        routineId = c.getString(1),
        routineVersionId = c.getLong(2),
        routineName = c.getString(3),
        engine = Engine.fromStorage(c.getString(4)) ?: Engine.INTERVAL_CIRCUIT,
        tier = c.stringOrNull(5),
        startedAt = c.getLong(6),
        finishedAt = c.longOrNull(7),
        localDate = parseStoredDate(c.getString(8)) ?: EPOCH_DATE,
        activeMs = c.getLong(9),
        wallMs = c.getLong(10),
        complete = c.bool(11),
        reachedTimeCap = c.bool(12),
        roundsPlanned = c.intOrNull(13),
        roundsCompleted = c.getInt(14),
        stationsPlanned = c.getInt(15),
        stationsCompleted = c.getInt(16),
        totalReps = c.getInt(18),
        volumeUnits = c.getDouble(19),
        rating = Rating.fromStorage(c.stringOrNull(17)),
        personalBest = c.getInt(20) > 0,
    )

    private fun readResults(database: SQLiteDatabase, sessionId: Long): List<SegmentResult> =
        database.rawQuery(
            """
            SELECT ordinal, phase, round_index, station_order, exercise_id, prescription_kind,
                   prescribed_reps, prescribed_sec, actual_reps, actual_duration_ms, added_ms,
                   skipped, difficulty_coef, volume_units, closed_at_wall, closed_at_elapsed
            FROM $TABLE_SESSION_RESULT WHERE session_id = ? ORDER BY ordinal ASC
            """.trimIndent(),
            arrayOf(sessionId.toString()),
        ).mapRows { c ->
            SegmentResult(
                ordinal = c.getInt(0),
                phase = Phase.fromStorage(c.getString(1)) ?: Phase.WORK,
                roundIndex = c.getInt(2),
                stationOrder = c.getInt(3),
                exerciseId = c.stringOrNull(4),
                measure = Measure.fromStorage(c.stringOrNull(5)),
                prescribedReps = c.intOrNull(6),
                prescribedSeconds = c.intOrNull(7),
                actualReps = c.intOrNull(8),
                actualMs = c.getLong(9),
                addedMs = c.getLong(10),
                skipped = c.bool(11),
                difficultyCoefficient = c.getDouble(12),
                volumeUnits = c.getDouble(13),
                closedAtWallMs = c.getLong(14),
                closedAtElapsedMs = c.getLong(15),
            )
        }

    private data class OpenRow(
        val id: Long,
        val routineId: String,
        val routineVersionId: Long,
        val routineName: String,
        val tier: String?,
        val startedAt: Long,
        val finishedAt: Long?,
        val lastWriteWall: Long,
        val lastWriteElapsed: Long,
        val stationsPlanned: Int,
        val roundsPlanned: Int?,
        val roundsCompleted: Int,
        val progressionStepId: Long?,
        val clock: PersistedClock,
    )

    private fun readSessionRow(database: SQLiteDatabase, sessionId: Long): OpenRow? =
        database.rawQuery("$OPEN_SQL WHERE s.id = ?", arrayOf(sessionId.toString())).firstRow(::openRow)

    private fun readLiveSession(database: SQLiteDatabase): ResumableSession? {
        // idx_session_live guarantees at most one candidate exists, so this needs no ordering and no
        // tie-break — the schema has already made "two live sessions" unrepresentable (§A.6).
        val row = database.rawQuery("$OPEN_SQL WHERE s.finished_at IS NULL", null)
            .firstRow(::openRow) ?: return null
        val results = readResults(database, row.id)
        val engine = database.rawQuery(
            "SELECT engine FROM $TABLE_ROUTINE_VERSION WHERE id = ?",
            arrayOf(row.routineVersionId.toString()),
        ).firstRow { Engine.fromStorage(it.getString(0)) } ?: Engine.INTERVAL_CIRCUIT

        return ResumableSession(
            sessionId = row.id,
            routineId = row.routineId,
            routineVersionId = row.routineVersionId,
            routineName = row.routineName,
            routineExists = database.rawQuery(
                "SELECT 1 FROM $TABLE_ROUTINE WHERE id = ? AND archived_at IS NULL",
                arrayOf(row.routineId),
            ).use { it.moveToFirst() },
            tier = row.tier,
            engine = engine,
            startedAtWallMs = row.startedAt,
            lastWriteWallMs = row.lastWriteWall,
            stationsPlanned = row.stationsPlanned,
            roundsPlanned = row.roundsPlanned,
            clock = row.clock,
            results = results,
            resumability = resumabilityOf(
                clock = row.clock,
                lastWriteWallMs = row.lastWriteWall,
                hasResults = results.isNotEmpty(),
                nowWall = System.currentTimeMillis(),
                nowElapsed = SystemClock.elapsedRealtime(),
            ),
        )
    }

    private fun openRow(c: Cursor): OpenRow = OpenRow(
        id = c.getLong(0),
        routineId = c.getString(1),
        routineVersionId = c.getLong(2),
        routineName = c.getString(3),
        tier = c.stringOrNull(4),
        startedAt = c.getLong(5),
        finishedAt = c.longOrNull(6),
        lastWriteWall = c.getLong(7),
        lastWriteElapsed = c.getLong(8),
        stationsPlanned = c.getInt(9),
        roundsPlanned = c.intOrNull(10),
        roundsCompleted = c.getInt(11),
        progressionStepId = c.longOrNull(12),
        clock = PersistedClock(
            startedAtElapsedMs = c.getLong(13),
            pausedAccumulatedMs = c.getLong(14),
            pausedAtElapsedMs = c.longOrNull(15),
            pausedAtWallMs = c.longOrNull(16),
            bootAnchorMs = c.getLong(17),
            anchorWallMs = c.getLong(7),
        ),
    )

    private fun readBests(database: SQLiteDatabase): List<RoutineBest> = database.rawQuery(
        """
        SELECT pr.routine_id, v.name, v.engine, pr.metric, pr.value, pr.tiebreak, pr.session_id,
               pr.achieved_at, pr.local_date, r.archived_at,
               (SELECT COUNT(*) FROM $TABLE_SESSION s WHERE s.routine_id = pr.routine_id
                 AND s.finished_at IS NOT NULL),
               (SELECT s2.routine_version_id FROM $TABLE_SESSION s2 WHERE s2.id = pr.session_id),
               r.head_version_id
        FROM $TABLE_PERSONAL_RECORD pr
        JOIN $TABLE_ROUTINE r ON r.id = pr.routine_id
        JOIN $TABLE_ROUTINE_VERSION v ON v.id = r.head_version_id
        ORDER BY pr.achieved_at DESC
        """.trimIndent(),
        null,
    ).mapRows { c ->
        RoutineBest(
            routineId = c.getString(0),
            routineName = c.getString(1),
            engine = Engine.fromStorage(c.getString(2)) ?: return@mapRows null,
            metric = BestMetric.fromStorage(c.getString(3)) ?: return@mapRows null,
            value = c.getDouble(4),
            tiebreak = c.getDouble(5),
            sessionId = c.getLong(6),
            achievedAt = c.getLong(7),
            localDate = parseStoredDate(c.getString(8)) ?: EPOCH_DATE,
            timesDone = c.getInt(10),
            routineArchived = !c.isNull(9),
            structureChanged = c.longOrNull(11) != c.longOrNull(12),
        )
    }.filterNotNull()

    /**
     * One row per **exercise_id** — what a single movement, and only that movement, is worth.
     *
     * Two flat queries rather than one, because SQLite's bare-column guarantee holds only when a query
     * has **exactly one** `min`/`max` aggregate: the first takes the best single set and the session it
     * happened in, the second the lifetime total and the most recent outing.
     *
     * `actual_reps IS NOT NULL` is the whole eligibility rule. A prescribed-but-unverified twenty is a
     * plan, not a record, which is also why 動きごと can be legitimately empty for someone who only
     * trains duration stations.
     *
     * **These rows carry no [MovementBest.hardestReachedExerciseId], deliberately.** いちばん上 is a
     * question about a *ladder* and one rung has no answer to it; [readMovementBests] rolls these up
     * and is where that field is filled. Splitting the read this way is also what stopped
     * `GYM.LIBRARY.EXERCISE_DETAIL` from labelling a family total 一度に / のべ回数 under one rung's
     * name — the per-exercise numbers were always computed here and thrown away by the `groupBy`.
     */
    private fun readExerciseBests(database: SQLiteDatabase): List<MovementBest> {
        data class BestSet(val reps: Int, val sessionId: Long)

        val bestSets = database.rawQuery(
            """
            SELECT r.exercise_id, MAX(r.actual_reps), r.session_id
            FROM $TABLE_SESSION_RESULT r JOIN $TABLE_SESSION s ON s.id = r.session_id
            WHERE r.exercise_id IS NOT NULL AND r.actual_reps IS NOT NULL AND r.skipped = 0
              AND s.finished_at IS NOT NULL
            GROUP BY r.exercise_id
            """.trimIndent(),
            null,
        ).mapRows { it.getString(0) to BestSet(it.getInt(1), it.getLong(2)) }.toMap()

        data class Lifetime(val reps: Int, val lastAt: Long, val lastDate: LocalDate?)

        val lifetimes = database.rawQuery(
            """
            SELECT r.exercise_id, SUM(r.actual_reps), MAX(s.started_at), s.local_date
            FROM $TABLE_SESSION_RESULT r JOIN $TABLE_SESSION s ON s.id = r.session_id
            WHERE r.exercise_id IS NOT NULL AND r.actual_reps IS NOT NULL AND r.skipped = 0
              AND s.finished_at IS NOT NULL
            GROUP BY r.exercise_id
            """.trimIndent(),
            null,
        ).mapRows {
            it.getString(0) to Lifetime(it.getInt(1), it.getLong(2), parseStoredDate(it.getString(3)))
        }.toMap()

        return bestSets.keys
            .mapNotNull { ExerciseCatalogSource.byId(it) }
            .map { exercise ->
                val best = bestSets[exercise.id]
                val lifetime = lifetimes[exercise.id]
                MovementBest(
                    ladderId = exercise.ladderId,
                    exerciseId = exercise.id,
                    exerciseName = exercise.nameJa,
                    singleSetReps = best?.reps ?: 0,
                    lifetimeReps = lifetime?.reps ?: 0,
                    sessionId = best?.sessionId,
                    lastPerformedAt = lifetime?.lastAt,
                    lastLocalDate = lifetime?.lastDate,
                    hardestReachedExerciseId = null,
                    hardestReachedExerciseName = null,
                )
            }
            .sortedByDescending { it.lastPerformedAt ?: 0L }
    }

    /**
     * 動きごと, rolled up by ladder — the same rows as [readExerciseBests], grouped.
     *
     * Rolling up in Kotlin is what keeps seven near-identical push-up rows from being unreadable
     * (§4 edge case 6), and the roll-up is **the whole content of this function**: the numbers come
     * from the per-exercise read so the two can never disagree about what a rep was worth.
     *
     * A caller that wants one movement's own record wants [readExerciseBests] instead. Every field
     * below is a *family* fact — `singleSetReps` is the family's best set on any rung, `lifetimeReps`
     * the family's sum — and attributing one of them to the rung the row happens to be named for is
     * the bug this split exists to close.
     */
    private fun readMovementBests(database: SQLiteDatabase): List<MovementBest> {
        val rows = readExerciseBests(database).associateBy { it.exerciseId }

        return rows.keys
            .mapNotNull { ExerciseCatalogSource.byId(it) }
            // A movement with no ladder is its own group, keyed by its own id.
            .groupBy { it.ladderId ?: it.id }
            .mapNotNull { (_, family) ->
                // The row is named for the rung the user actually trains, and reports the hardest one
                // they have reached as いちばん上.
                val representative = family.maxByOrNull { rows[it.id]?.lifetimeReps ?: 0 }
                    ?: return@mapNotNull null
                val hardest = family.maxByOrNull { it.difficulty }
                val best = family.mapNotNull { rows[it.id] }.maxByOrNull { it.singleSetReps }
                MovementBest(
                    ladderId = representative.ladderId,
                    exerciseId = representative.id,
                    exerciseName = representative.nameJa,
                    singleSetReps = best?.singleSetReps ?: 0,
                    lifetimeReps = family.sumOf { rows[it.id]?.lifetimeReps ?: 0 },
                    sessionId = best?.sessionId,
                    lastPerformedAt = family.mapNotNull { rows[it.id]?.lastPerformedAt }.maxOrNull(),
                    lastLocalDate = family.mapNotNull { rows[it.id]?.lastLocalDate }.maxOrNull(),
                    hardestReachedExerciseId = hardest?.takeIf { it.id != representative.id }?.id,
                    hardestReachedExerciseName = hardest?.takeIf { it.id != representative.id }?.nameJa,
                )
            }
            .sortedByDescending { it.lastPerformedAt ?: 0L }
    }

    private fun readLoadScale(database: SQLiteDatabase, days: Int): LoadScale {
        val from = today().minusDays(days.toLong())
        val dayMillis = database.rawQuery(
            """
            SELECT SUM(active_ms) AS day_ms FROM $TABLE_SESSION
            WHERE finished_at IS NOT NULL AND local_date >= ?
            GROUP BY local_date ORDER BY day_ms
            """.trimIndent(),
            arrayOf(storedDate(from)),
        ).mapRows { it.getLong(0) }
        return loadScaleOf(dayMillis)
    }

    private fun readStreak(database: SQLiteDatabase): Streak {
        val from = today().minusDays(MAX_STREAK_LOOKBACK_DAYS.toLong())
        val trained = database.rawQuery(
            """
            SELECT local_date FROM $TABLE_SESSION
            WHERE finished_at IS NOT NULL AND local_date >= ?
            GROUP BY local_date
            """.trimIndent(),
            arrayOf(storedDate(from)),
        ).mapRows { parseStoredDate(it.getString(0)) }.filterNotNull().toSet()

        val plans = database.rawQuery(
            "SELECT effective_from, days_mask, forgiveness_per_month FROM $TABLE_TRAINING_PLAN " +
                "ORDER BY effective_from DESC",
            null,
        ).mapRows { c ->
            parseStoredDate(c.getString(0))?.let { PlanWindow(it, c.getInt(1), c.getInt(2)) }
        }.filterNotNull()

        // `today` comes from the guarded clock, never LocalDate.now(): a system clock wound backwards
        // must not be able to fabricate a streak (§D.1, §E.4).
        return currentStreak(today = today(), trained = trained, plans = plans)
    }

    private fun readDailyLoad(
        database: SQLiteDatabase,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, DailyLoad> = database.rawQuery(
        """
        SELECT local_date, SUM(rating_cr10 * (active_ms / 60000.0)) AS load, COUNT(*) AS sessions,
               SUM(CASE WHEN rating_cr10 IS NULL THEN 1 ELSE 0 END) AS unrated
        FROM $TABLE_SESSION
        WHERE finished_at IS NOT NULL AND local_date >= ? AND local_date <= ?
        GROUP BY local_date ORDER BY local_date ASC
        """.trimIndent(),
        arrayOf(storedDate(from), storedDate(to)),
    ).mapRows { c ->
        parseStoredDate(c.getString(0))?.let { date ->
            date to DailyLoad(date = date, load = c.getDouble(1), sessions = c.getInt(2), unrated = c.getInt(3))
        }
    }.filterNotNull().toMap()

    private fun readProgression(database: SQLiteDatabase, programId: String): ProgressionState? {
        val state = database.rawQuery(
            """
            SELECT p.name_ja, p.step_unit, p.step_count, p.advance_rule, p.advance_param, p.cycle_days,
                   p.origin, p.note_ja, st.current_step_index, st.sessions_at_step, st.step_entered_at,
                   st.last_session_id, st.cycle_day
            FROM $TABLE_PROGRESSION p
            JOIN $TABLE_PROGRESSION_STATE st ON st.program_id = p.id
            WHERE p.id = ?
            """.trimIndent(),
            arrayOf(programId),
        ).firstRow { c ->
            ProgressionState(
                programId = programId,
                nameJa = c.getString(0),
                stepUnit = runCatching { StepUnit.valueOf(c.getString(1)) }.getOrDefault(StepUnit.STEP),
                stepCount = c.getInt(2),
                advanceRule = runCatching { AdvanceRule.valueOf(c.getString(3)) }
                    .getOrDefault(AdvanceRule.MANUAL),
                advanceParam = c.intOrNull(4),
                cycleDays = c.intOrNull(5),
                origin = c.getString(6),
                noteJa = c.stringOrNull(7),
                currentStepIndex = c.getInt(8),
                currentStep = null,
                sessionsAtStep = c.getInt(9),
                stepEnteredAt = c.getLong(10),
                lastSessionId = c.longOrNull(11),
                cycleDay = c.getInt(12),
            )
        } ?: return null
        // Resolved here rather than left to the caller: GYM.LIBRARY.DETAIL renders the current step's
        // sets inline, and re-reading the step table per frame for one line is a query behind a label.
        return state.copy(currentStep = readStep(database, programId, state.currentStepIndex))
    }

    private fun readStep(database: SQLiteDatabase, programId: String, stepIndex: Int): ProgressionStep? {
        val stepId = database.rawQuery(
            "SELECT id, label_ja, total_reps, shape, rest_sec, note_ja FROM $TABLE_PROGRESSION_STEP " +
                "WHERE program_id = ? AND step_index = ?",
            arrayOf(programId, stepIndex.toString()),
        ).firstRow { c ->
            c.getLong(0) to ProgressionStep(
                stepIndex = stepIndex,
                labelJa = c.stringOrNull(1),
                shape = runCatching { StepShape.valueOf(c.getString(3)) }.getOrDefault(StepShape.FIXED),
                totalReps = c.intOrNull(2),
                restSeconds = c.getInt(4),
                noteJa = c.stringOrNull(5),
                sets = emptyList(),
            )
        } ?: return null
        val sets = database.rawQuery(
            "SELECT set_index, reps, variant FROM $TABLE_PROGRESSION_SET WHERE step_id = ? " +
                "ORDER BY set_index ASC",
            arrayOf(stepId.first.toString()),
        ).mapRows { c ->
            ProgressionSet(
                setIndex = c.getInt(0),
                reps = c.intOrNull(1),
                variant = c.stringOrNull(2)?.let { v -> runCatching { SetVariant.valueOf(v) }.getOrNull() },
            )
        }
        return stepId.second.copy(sets = sets)
    }

    private fun currentStepId(database: SQLiteDatabase, programId: String?): Long? {
        if (programId == null) return null
        return database.rawQuery(
            """
            SELECT s.id FROM $TABLE_PROGRESSION_STEP s
            JOIN $TABLE_PROGRESSION_STATE st ON st.program_id = s.program_id
                AND st.current_step_index = s.step_index
            WHERE s.program_id = ?
            """.trimIndent(),
            arrayOf(programId),
        ).firstRow { it.getLong(0) }
    }

    // ─── Writes that need more than one statement ───────────────────────────────────────────────

    private fun insertVersion(database: SQLiteDatabase, routineId: String, draft: RoutineDraft): Long {
        val shapes = draft.stations.map {
            StationShape(it.exerciseId, it.measure, it.reps, it.seconds, it.note)
        }
        val versionNumber = database.rawQuery(
            "SELECT COALESCE(MAX(version_number), 0) + 1 FROM $TABLE_ROUTINE_VERSION WHERE routine_id = ?",
            arrayOf(routineId),
        ).firstRow { it.getInt(0) } ?: 1

        val versionId = database.insertOrThrow(
            TABLE_ROUTINE_VERSION,
            null,
            ContentValues().apply {
                put("routine_id", routineId)
                put("version_number", versionNumber)
                put("name", draft.name)
                put("engine", draft.engine.name)
                put("rounds", draft.rounds)
                put("time_cap_sec", draft.timeCapSeconds)
                put("interval_sec", draft.intervalSeconds)
                put("rest_between_stations", draft.restBetweenStations)
                put("rest_between_rounds", draft.restBetweenRounds)
                put("prepare_sec", draft.prepareSeconds)
                // FIXED_SETS is the only engine a progression attaches to, and the CHECK says so.
                // A user routine has none, which is why the builder cannot offer that engine.
                putNull("progression_program_id")
                put("primary_metric", defaultMetricFor(draft.engine).name)
                put("station_count", draft.stations.size)
                put(
                    "est_duration_sec",
                    estimatedDurationSeconds(
                        stations = shapes,
                        secondsPerRep = { ExerciseCatalogSource.byId(it)?.secondsPerRep },
                        rounds = draft.rounds,
                        timeCapSeconds = draft.timeCapSeconds,
                        restBetweenStations = draft.restBetweenStations,
                        restBetweenRounds = draft.restBetweenRounds,
                        prepareSeconds = draft.prepareSeconds,
                    ),
                )
                put("est_total_reps", estimatedTotalReps(shapes, draft.rounds))
                put(
                    "structural_hash",
                    structuralHashOf(
                        name = draft.name,
                        engine = draft.engine,
                        rounds = draft.rounds,
                        timeCapSeconds = draft.timeCapSeconds,
                        intervalSeconds = draft.intervalSeconds,
                        restBetweenStations = draft.restBetweenStations,
                        restBetweenRounds = draft.restBetweenRounds,
                        prepareSeconds = draft.prepareSeconds,
                        progressionProgramId = null,
                        stations = shapes,
                    ),
                )
                put("created_at", now())
            },
        )
        draft.stations.forEachIndexed { position, station ->
            database.insertOrThrow(
                TABLE_ROUTINE_STATION,
                null,
                ContentValues().apply {
                    put("routine_version_id", versionId)
                    put("position", position)
                    put("exercise_id", station.exerciseId)
                    put("prescription_kind", station.measure.name)
                    put("prescribed_reps", station.reps)
                    put("prescribed_sec", station.seconds)
                    put("note", station.note)
                },
            )
        }
        return versionId
    }

    /**
     * Advances the programme, and only ever by one step.
     *
     * The rules are `progression_program.advance_rule` and nothing else — [ruleSatisfied] holds all
     * four, lifted into `GymMath` so they are testable without a device. `SESSIONS_COMPLETED` counts
     * sessions at the current step, `WEEKS_ELAPSED` counts wall time since the step was entered (Recon
     * Ron's two weeks per rung), `ALL_SETS_MADE` asks [allSetsMade] of this session's own rows, and
     * `MANUAL` never advances from here because the user does.
     *
     * **Only a complete session ever gets here** — [finishSession] calls this under `complete` — which
     * is `00-plan.md` §4.1 rule 2 and the same judgement [prEligible] makes for records. A quit
     * session's rows are a partial prescription, not a made one.
     *
     * The advance is then run past [rampPermits], design §7.4's ramp governor. A held advance is not
     * cancelled: [progressionAfterSession] resets nothing when it does not advance, so the next
     * completed session asks again with a `sessions_at_step` that has kept climbing and a
     * `step_entered_at` that has kept ageing.
     *
     * Nothing seeded uses `ALL_SETS_MADE` (リーコン・ロン is `WEEKS_ELAPSED`, アームストロング is
     * `SESSIONS_COMPLETED`, ファイター懸垂 is `MANUAL` with no steps), so this arm completes a contract
     * the schema CHECK and [AdvanceRule] already carried rather than changing behaviour anyone can see.
     */
    private fun advanceProgression(
        database: SQLiteDatabase,
        session: OpenRow,
        snapshot: RoutineSnapshot?,
        results: List<SegmentResult>,
    ) {
        val programId = snapshot?.progressionProgramId ?: return
        val state = readProgression(database, programId) ?: return
        val nowMs = now()
        val earned = ruleSatisfied(
            rule = state.advanceRule,
            // Including this session, which is what `advance_param = 1` means.
            sessionsAtStep = state.sessionsAtStep + 1,
            advanceParam = state.advanceParam,
            stepEnteredAt = state.stepEnteredAt,
            nowMs = nowMs,
            results = results,
        )
        val next = progressionAfterSession(
            state = state,
            advance = earned && rampPermits(database, state),
            nowMs = nowMs,
        )
        database.execSQL(
            """
            UPDATE $TABLE_PROGRESSION_STATE
               SET current_step_index = ?, sessions_at_step = ?, step_entered_at = ?,
                   last_session_id = ?, cycle_day = ?
             WHERE program_id = ?
            """.trimIndent(),
            arrayOf<Any?>(
                next.stepIndex,
                next.sessionsAtStep,
                next.stepEnteredAt,
                session.id,
                // A day-based programme rotates rather than progresses; Armstrong's day 5 is stored
                // here so the fifth day knows which day it is repeating (§F.3).
                next.cycleDay,
                programId,
            ),
        )
    }

    /**
     * Design §7.4's ten-percent-per-week ramp governor, consulted on the advance path only.
     *
     * Three properties are load-bearing, and all three are about this running inside the single most
     * correctness-critical write in the app (§E.5):
     *
     *  - **It cannot fail the finish.** Every path is wrapped, and a throw resolves to *permitted*.
     *    Advancing a step too early costs one week that was easier than intended; letting a governor
     *    abort this transaction costs the user the entire record of the session they just did.
     *  - **It cannot lengthen the transaction materially.** The two lookups it needs happen only when
     *    a rule has already fired — the common finish does none of them — and it asks the cheap,
     *    purely local question first: an unknowable step increase (Armstrong's max-effort days, a
     *    programme with no next rung) short-circuits before the spine is ever queried.
     *  - **It never surfaces.** No fault, no field on [SessionOutcome], no string. §7.4 permits at most
     *    a soft nudge and this phase ships none, so a held step is simply a step that has not happened
     *    yet — which is exactly what it looks like on `GYM.LIBRARY.DETAIL` already.
     */
    private fun rampPermits(database: SQLiteDatabase, state: ProgressionState): Boolean =
        runCatching {
            val current = stepWorkUnits(state.currentStep) ?: return@runCatching true
            val nextStep = readStep(database, state.programId, state.currentStepIndex + 1)
            val increase = workIncrease(current, stepWorkUnits(nextStep)) ?: return@runCatching true
            if (increase <= 0.0) return@runCatching true
            rampAllowed(readGovernorSpine(database), increase)
        }.getOrDefault(true)

    /**
     * The zero-filled load spine the governor reads, ending on the **last complete day**.
     *
     * Two boundaries, both forced rather than chosen:
     *
     *  - **It ends yesterday.** The session being finished has no rating yet — 記録 asks for one after
     *    this write — so a window containing today always holds an unrated session, and [rampCap]
     *    refuses to compute over one. A window ending today would therefore silence the governor
     *    permanently rather than occasionally.
     *  - **It starts at the user's own first session**, capped at [ACWR_CHRONIC_DAYS]. §Q24's rule:
     *    the twenty-eight-day gate is about whether that much history *exists*, not about whether a
     *    recent window is busy. A short spine is how [rampCap] learns it has no opinion, and someone
     *    who has trained for years is never told they have not.
     */
    private fun readGovernorSpine(database: SQLiteDatabase): List<DailyLoad> {
        val to = today().minusDays(1)
        val first = database.rawQuery(
            "SELECT MIN(local_date) FROM $TABLE_SESSION WHERE finished_at IS NOT NULL",
            null,
        ).firstRow { parseStoredDate(it.stringOrNull(0)) } ?: return emptyList()
        if (first.isAfter(to)) return emptyList()
        val from = maxOf(first, to.minusDays((ACWR_CHRONIC_DAYS - 1).toLong()))
        return zeroFilledLoad(from, to, readDailyLoad(database, from, to))
    }

    /**
     * Evaluates the metrics this session is eligible for against the cached record, inside the finish
     * transaction, so the 自己最高 chip is decided before the record screen renders (§A.9, §D.2).
     *
     * The eligibility predicate is [prEligible] and it is asked twice — here for the whole session and
     * again per metric inside [candidateFor] — because both are entry points and only one of them is on
     * the finish path. §D.2's rule is a single sentence: **a PR requires `complete = 1`**, in every
     * engine, for every metric (`00-plan.md` §4.1 rule 2, `04` §4 edge case 1). A partial session
     * carries a 途中まで chip and no other.
     */
    private fun evaluateBests(database: SQLiteDatabase, summary: SessionSummary): List<RoutineBest> {
        // The choke point. Quitting 七分間 after four stations must not take 最高負荷, and MOST_VOLUME
        // and MOST_REPS have no per-metric guard of their own to fall back on.
        if (!summary.complete) return emptyList()
        val snapshot = readSnapshot(database, summary.routineVersionId) ?: return emptyList()
        return metricsFor(summary.engine, snapshot.primaryMetric).mapNotNull { metric ->
            val candidate = candidateFor(database, summary, metric) ?: return@mapNotNull null
            val standing = database.rawQuery(
                "SELECT value, tiebreak FROM $TABLE_PERSONAL_RECORD WHERE routine_id = ? AND metric = ?",
                arrayOf(summary.routineId, metric.name),
            ).firstRow { it.getDouble(0) to it.getDouble(1) }
            if (!isBetter(metric, candidate.first, candidate.second, standing)) return@mapNotNull null

            database.insertWithOnConflict(
                TABLE_PERSONAL_RECORD,
                null,
                ContentValues().apply {
                    put("routine_id", summary.routineId)
                    put("metric", metric.name)
                    put("value", candidate.first)
                    put("tiebreak", candidate.second)
                    put("session_id", summary.sessionId)
                    put("achieved_at", summary.startedAt)
                    put("local_date", storedDate(summary.localDate))
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            RoutineBest(
                routineId = summary.routineId,
                routineName = summary.routineName,
                engine = summary.engine,
                metric = metric,
                value = candidate.first,
                tiebreak = candidate.second,
                sessionId = summary.sessionId,
                achievedAt = summary.startedAt,
                localDate = summary.localDate,
                timesDone = 0,
                routineArchived = false,
                structureChanged = false,
            )
        }
    }

    /**
     * The value and tiebreak this session offers for [metric], or null when it is not eligible.
     *
     * The guard is [prEligible] and it is one line for all five metrics rather than four per-metric
     * `if`s, because the per-metric form is what let `MOST_REPS` and `MOST_VOLUME` through with no
     * `complete` check at all. It also fixes [rebuildPersonalRecordsBlocking], which shares this
     * function and would otherwise reinstate a partial session's record on the next rebuild.
     */
    private fun candidateFor(
        database: SQLiteDatabase,
        summary: SessionSummary,
        metric: BestMetric,
    ): Pair<Double, Double>? {
        if (!prEligible(metric, summary.complete, summary.reachedTimeCap)) return null
        return when (metric) {
            BestMetric.BEST_TIME -> summary.activeMs.toDouble() to 0.0
            BestMetric.MOST_ROUNDS -> {
                // Partial reps in the final round are the tiebreak, because most-rounds alone cannot
                // separate two identical scores and the finer answer is already recorded (§D.2).
                val partial = database.rawQuery(
                    """
                    SELECT COALESCE(SUM(actual_reps), 0) FROM $TABLE_SESSION_RESULT
                    WHERE session_id = ? AND round_index = ? AND skipped = 0
                    """.trimIndent(),
                    arrayOf(summary.sessionId.toString(), summary.roundsCompleted.toString()),
                ).firstRow { it.getDouble(0) } ?: 0.0
                summary.roundsCompleted.toDouble() to partial
            }
            BestMetric.MOST_REPS -> summary.totalReps.toDouble() to 0.0
            BestMetric.MOST_VOLUME -> summary.volumeUnits to 0.0
            BestMetric.HIGHEST_STEP -> database.rawQuery(
                """
                SELECT ps.step_index FROM $TABLE_SESSION s
                JOIN $TABLE_PROGRESSION_STEP ps ON ps.id = s.progression_step_id
                WHERE s.id = ?
                """.trimIndent(),
                arrayOf(summary.sessionId.toString()),
            ).firstRow { it.getDouble(0) to 0.0 }
        }
    }

    /**
     * Regenerates `personal_record` from truth (§A.9).
     *
     * Runs on the first open after a corruption recovery or a seed upgrade, and behind もう一度. It is
     * safe to run at any time and by definition cannot lose information, because the table holds none
     * that the sessions do not.
     */
    private fun rebuildPersonalRecordsBlocking(database: SQLiteDatabase) {
        database.transact {
            delete(TABLE_PERSONAL_RECORD, null, null)
            val routines = rawQuery(
                """
                SELECT r.id, v.engine, v.primary_metric FROM $TABLE_ROUTINE r
                JOIN $TABLE_ROUTINE_VERSION v ON v.id = r.head_version_id
                """.trimIndent(),
                null,
            ).mapRows { c ->
                Triple(
                    c.getString(0),
                    Engine.fromStorage(c.getString(1)),
                    BestMetric.fromStorage(c.getString(2)),
                )
            }
            routines.forEach { (routineId, engine, metric) ->
                if (engine == null || metric == null) return@forEach
                val attempts = readAttempts(this, routineId, REBUILD_SCAN_LIMIT)
                metricsFor(engine, metric).forEach { m ->
                    var bestSummary: SessionSummary? = null
                    var bestValue: Pair<Double, Double>? = null
                    // Ascending by start, and a tie never displaces the incumbent, so ties break
                    // toward the earlier session — you set the record the first time you hit it.
                    attempts.sortedBy { it.startedAt }.forEach { attempt ->
                        val candidate = candidateFor(this, attempt, m) ?: return@forEach
                        if (isBetter(m, candidate.first, candidate.second, bestValue)) {
                            bestValue = candidate
                            bestSummary = attempt
                        }
                    }
                    val winner = bestSummary
                    val value = bestValue
                    if (winner != null && value != null) {
                        insertWithOnConflict(
                            TABLE_PERSONAL_RECORD,
                            null,
                            ContentValues().apply {
                                put("routine_id", routineId)
                                put("metric", m.name)
                                put("value", value.first)
                                put("tiebreak", value.second)
                                put("session_id", winner.sessionId)
                                put("achieved_at", winner.startedAt)
                                put("local_date", storedDate(winner.localDate))
                            },
                            SQLiteDatabase.CONFLICT_REPLACE,
                        )
                    }
                }
            }
            Meta.write(this, Meta.PR_REBUILD_NEEDED, "0")
        }
    }

    // ─── Small helpers ──────────────────────────────────────────────────────────────────────────

    private fun newRoutineId(): String = "u_${UUID.randomUUID()}"

    private fun nextUserSortOrder(database: SQLiteDatabase): Int = database.rawQuery(
        "SELECT COALESCE(MAX(sort_order), 0) + 1 FROM $TABLE_ROUTINE WHERE built_in = 0",
        null,
    ).firstRow { it.getInt(0) } ?: 1

    /**
     * The metric a user-built routine's card shows, by engine, following `04` §4 edge case 2's
     * `bestMetricFor`: for-time engines are scored by time, round-counting engines by rounds, and
     * everything else by weighted volume — which is the only metric that means anything when a routine
     * mixes rep and hold stations.
     */
    private fun defaultMetricFor(engine: Engine): BestMetric = when (engine) {
        Engine.FOR_TIME, Engine.FOR_TIME_WITH_REST -> BestMetric.BEST_TIME
        Engine.AMRAP, Engine.EMOM_ASCENDING -> BestMetric.MOST_ROUNDS
        Engine.INTERVAL_CIRCUIT, Engine.EMOM, Engine.FIXED_SETS -> BestMetric.MOST_VOLUME
    }

    private fun prescriptionKindOf(r: SegmentResultDraft): Measure? = when {
        r.phase == Phase.REST || r.phase == Phase.PREPARE -> null
        r.prescribedReps != null -> Measure.REPS
        r.plannedMs > 0 -> Measure.DURATION
        else -> Measure.MAX_EFFORT
    }

    private fun prescribedSecondsOf(r: SegmentResultDraft): Int? =
        if (r.prescribedReps == null && r.plannedMs > 0) (r.plannedMs / 1000).toInt() else null

    private fun RoutineSnapshot.toDraft(newName: String) = RoutineDraft(
        routineId = null,
        name = newName,
        engine = engine,
        stations = stations.map {
            io.eddiegulay.tempo.gym.StationDraft(
                exerciseId = it.exerciseId,
                measure = it.measure,
                reps = it.prescribedReps,
                seconds = it.prescribedSeconds,
                note = it.note,
            )
        },
        rounds = rounds,
        timeCapSeconds = timeCapSeconds,
        intervalSeconds = intervalSeconds,
        restBetweenStations = restBetweenStations,
        restBetweenRounds = restBetweenRounds,
        prepareSeconds = prepareSeconds,
    )

    /**
     * The card, carrying the **pieces** of §A.3's sample line rather than the line itself.
     *
     * This used to compose 「十二種目 ・ 三十秒 / 十秒 ・ 約八分」 right here, because this is the only
     * place that has all four numbers. It cannot: the feed re-emits on a *table* change, so after a
     * language switch every card would keep its stale sentence until the next write. `GymHomeCopy`
     * builds it now, from these fields, where `LocalStrings` can re-resolve it.
     */
    private fun RoutineSummary.toCard(last: LastResult?, workSeconds: Int?): RoutineCard {
        return RoutineCard(
            routineId = routineId,
            name = name,
            stationCount = stationCount,
            workSeconds = workSeconds,
            restBetweenStations = restBetweenStations,
            estimatedDurationSeconds = estimatedDurationSeconds,
            timesDone = timesDone,
            lastResult = last,
            best = best,
            builtIn = builtIn,
            origin = origin,
        )
    }

    /**
     * The per-station work length, by version, when every station is a duration and they all share
     * one — otherwise null, and the fragment is simply omitted from the card.
     *
     * Read as a map rather than per row: the home feed renders a dozen cards, and this is the one
     * piece of a card that does not come off §D.7's version projection.
     */
    private fun readUniformWorkSeconds(database: SQLiteDatabase): Map<Long, Int?> = database.rawQuery(
        """
        SELECT routine_version_id, MIN(prescribed_sec), MAX(prescribed_sec), COUNT(*),
               SUM(CASE WHEN prescription_kind = 'DURATION' THEN 1 ELSE 0 END)
        FROM $TABLE_ROUTINE_STATION GROUP BY routine_version_id
        """.trimIndent(),
        null,
    ).mapRows { c ->
        val uniform = c.getInt(3) == c.getInt(4) && !c.isNull(1) && c.getInt(1) == c.getInt(2)
        c.getLong(0) to if (uniform) c.getInt(1) else null
    }.toMap()

    // `RoutineMissing` and `SessionMissing` used to be declared here, private. They now live in
    // `DbSupport.kt` beside `toGymFault`, which is the only thing that ever looks at them — see
    // `DECISIONS.md` §Q23 for why the distance was the bug rather than an accident of layout.

    companion object {
        /**
         * The date a corrupt `local_date` column falls back to.
         *
         * It is never rendered as a real date — a row that reaches it has already failed its CHECK —
         * and it exists so that one unparseable string cannot take a whole history list down with it.
         */
        private val EPOCH_DATE: LocalDate = LocalDate.of(1970, 1, 1)

        /** よく使う is habit, not lifetime totals — a trailing window, per §D.7's note. */
        private const val RECENT_HABIT_DAYS = 90L

        private const val LOAD_SCALE_DAYS = 90

        private const val ATTEMPT_PREVIEW = 5

        /** A rebuild reads a routine's attempts; nobody has more of one routine than this. */
        private const val REBUILD_SCAN_LIMIT = 2000

        /**
         * The projection every [SessionSummary] is read through. Numbered, because the reader indexes
         * it positionally and a silent renumbering is the failure mode.
         *
         *  0 id · 1 routine_id · 2 routine_version_id · 3 routine_name · 4 engine · 5 tier
         *  6 started_at · 7 finished_at · 8 local_date · 9 active_ms · 10 wall_ms · 11 complete
         * 12 reached_time_cap · 13 rounds_planned · 14 rounds_completed · 15 stations_planned
         * 16 stations_completed · 17 rating · 18 total_reps · 19 volume · 20 is_personal_best
         */
        private val SUMMARY_SQL = """
            SELECT s.id, s.routine_id, s.routine_version_id, s.routine_name, v.engine, s.tier,
                   s.started_at, s.finished_at, s.local_date, s.active_ms, s.wall_ms, s.complete,
                   s.reached_time_cap, s.rounds_planned, s.rounds_completed, s.stations_planned,
                   s.stations_completed, s.rating,
                   COALESCE(agg.total_reps, 0), COALESCE(agg.volume, 0),
                   (SELECT COUNT(*) FROM $TABLE_PERSONAL_RECORD pr WHERE pr.session_id = s.id)
            FROM $TABLE_SESSION s
            JOIN $TABLE_ROUTINE_VERSION v ON v.id = s.routine_version_id
            LEFT JOIN (
                SELECT session_id, SUM(COALESCE(actual_reps, 0)) AS total_reps,
                       SUM(volume_units) AS volume
                  FROM $TABLE_SESSION_RESULT WHERE skipped = 0 GROUP BY session_id
            ) agg ON agg.session_id = s.id
        """.trimIndent()

        /**
         * The open-session projection.
         *
         *  0 id · 1 routine_id · 2 routine_version_id · 3 routine_name · 4 tier · 5 started_at
         *  6 finished_at · 7 last_write_wall · 8 last_write_elapsed · 9 stations_planned
         * 10 rounds_planned · 11 rounds_completed · 12 progression_step_id · 13 started_at_elapsed
         * 14 paused_accum_ms · 15 paused_at_elapsed · 16 paused_at_wall · 17 boot_anchor_ms
         */
        private val OPEN_SQL = """
            SELECT s.id, s.routine_id, s.routine_version_id, s.routine_name, s.tier, s.started_at,
                   s.finished_at, s.last_write_wall, s.last_write_elapsed, s.stations_planned,
                   s.rounds_planned, s.rounds_completed, s.progression_step_id, s.started_at_elapsed,
                   s.paused_accum_ms, s.paused_at_elapsed, s.paused_at_wall, s.boot_anchor_ms
            FROM $TABLE_SESSION s
        """.trimIndent()

        @Volatile
        private var instance: GymStore? = null

        fun getInstance(context: Context): GymStore {
            val app = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: GymStore(app, versionCodeOf(app)).also { instance = it }
            }
        }

        /** Forensics only — `session.created_by_version`. A failure to read it must never block a start. */
        private fun versionCodeOf(context: Context): Int = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        }.getOrDefault(0)
    }
}
