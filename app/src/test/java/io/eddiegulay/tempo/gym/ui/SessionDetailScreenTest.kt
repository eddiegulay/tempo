package io.eddiegulay.tempo.gym.ui

import io.eddiegulay.tempo.data.GymFault
import io.eddiegulay.tempo.gym.BestMetric
import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Exercise
import io.eddiegulay.tempo.gym.Measure
import io.eddiegulay.tempo.gym.Pattern
import io.eddiegulay.tempo.gym.Phase
import io.eddiegulay.tempo.gym.PrChip
import io.eddiegulay.tempo.gym.Rating
import io.eddiegulay.tempo.gym.RoutineSnapshot
import io.eddiegulay.tempo.gym.RoutineStation
import io.eddiegulay.tempo.gym.SegmentResult
import io.eddiegulay.tempo.gym.SessionDetail
import io.eddiegulay.tempo.gym.SessionSummary
import io.eddiegulay.tempo.i18n.StringsEn
import io.eddiegulay.tempo.i18n.StringsJa
import io.eddiegulay.tempo.ui.gym.RatingEdit
import io.eddiegulay.tempo.ui.gym.RecordFooterAction
import io.eddiegulay.tempo.ui.gym.RecordMode
import io.eddiegulay.tempo.ui.gym.breakdownRows
import io.eddiegulay.tempo.ui.gym.historicalData
import io.eddiegulay.tempo.ui.gym.label
import io.eddiegulay.tempo.ui.gym.missingSnapshotCopy
import io.eddiegulay.tempo.ui.gym.pinnedExerciseNames
import io.eddiegulay.tempo.ui.gym.playsEnsoClosure
import io.eddiegulay.tempo.ui.gym.popsOnFault
import io.eddiegulay.tempo.ui.gym.ratingPromptVisible
import io.eddiegulay.tempo.ui.gym.recordAccolades
import io.eddiegulay.tempo.ui.gym.recordDateLine
import io.eddiegulay.tempo.ui.gym.settledEdit
import io.eddiegulay.tempo.ui.gym.shownRating
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GYM.RECORDS.SESSION_DETAIL`'s half of the eleven-row difference table.
 *
 * `RecordSummaryTest` already pins the rows that live inside the shared component. What is pinned here
 * is the *mount* — that this page hands `RecordMode.Historical` a [io.eddiegulay.tempo.gym.SessionDetail]
 * narrowed in the way the table requires, and that the four rows the page owns land. The failure mode
 * is silent in exactly the way §4 warns: a historical record that replays the ceremony, or shows
 * today's streak, or keeps claiming a crown it has since lost, looks completely fine in a screenshot.
 *
 * Row 12 — **a partial session is a real session, identically in both modes** — is the one thing
 * deliberately the same, and it is asserted here rather than assumed because this is the page where a
 * well-meaning "historical records are read-only, so soften them" edit would land.
 */
class SessionDetailScreenTest {

    // ─── row 1: the data source ─────────────────────────────────────────────────────────────────

    @Test
    fun `the historical record takes isStillBest from the store, not from optimism`() {
        // Row 6. Live passes `true` unconditionally because a record set thirty seconds ago is still
        // standing; this page must not, or a beaten record keeps claiming the crown.
        assertFalse(historicalData(detail(isStillBest = false)).isStillBest)
        assertTrue(historicalData(detail(isStillBest = true)).isStillBest)
    }

    @Test
    fun `a record since beaten is demoted to 当時の自己最高`() {
        // Row 6 end to end: the chip the mounted component will actually draw, in `c.inkFaint`.
        val beaten = recordAccolades(RecordMode.Historical, historicalData(detail(isStillBest = false)), StringsJa)
        assertEquals(PrChip.FORMER, beaten.chip)

        val standing = recordAccolades(RecordMode.Historical, historicalData(detail(isStillBest = true)), StringsJa)
        assertEquals(PrChip.CURRENT, standing.chip)
    }

    @Test
    fun `today's streak is never carried onto a historical record`() {
        // Row 7, enforced twice on purpose: nothing is passed, and `recordAccolades` would discard it
        // in Historical even if something were. Showing today's streak on a March record is a category
        // error, and computing March's means walking the whole history for one line.
        assertNull(historicalData(detail()).streakDays)
        assertNull(recordAccolades(RecordMode.Historical, historicalData(detail()), StringsJa).streak)
    }

    @Test
    fun `the ensō is drawn already closed and never animates`() {
        // Row 2. Ceremony you can replay is not ceremony.
        assertFalse(playsEnsoClosure(RecordMode.Historical, summary()))
        assertFalse(playsEnsoClosure(RecordMode.Historical, summary(complete = false)))
    }

    @Test
    fun `an answered rating drops the question, an unanswered one still asks`() {
        // Rows 3 and 4: a record you are reading is not being asked anything, but an old skipped
        // session can still be answered — later is better than never.
        assertFalse(ratingPromptVisible(RecordMode.Historical, Rating.HARD))
        assertTrue(ratingPromptVisible(RecordMode.Historical, null))
    }

    @Test
    fun `a fail-out cannot be reconstructed and is reported as an ordinary complete session`() {
        // `CompletionReason` is the player's in-memory answer and nothing stores it, so a reopened
        // デス・バイ loses only its 力尽きた chip and its 到達 label. Guessing at the reason from the
        // row's shape would be this page inventing the one fact it exists to report.
        assertFalse(historicalData(detail()).failedOut)
    }

    @Test
    fun `an archived routine travels through so もう一度 can be greyed with its reason`() {
        assertTrue(historicalData(detail(routineArchived = true)).routineArchived)
        assertFalse(historicalData(detail()).routineArchived)
    }

    @Test
    fun `the comparison baseline is whatever the store scoped, never re-derived here`() {
        // Row 5: `previous` arrives scoped to the same routine **and** tier, and to `started_at <` —
        // reopening a March record must not compare it to yesterday.
        val earlier = summary(sessionId = 7L, activeMs = 400_000L)
        assertEquals(earlier, historicalData(detail(previous = earlier)).previous)
        assertNull(historicalData(detail(previous = null)).previous)
    }

    // ─── row 12: the one thing deliberately the same ────────────────────────────────────────────

    @Test
    fun `a partial session reads identically here — chip kept, accolades withheld`() {
        val partial = detail(summary = summary(complete = false, stationsCompleted = 8, stationsPlanned = 20))
        val accolades = recordAccolades(RecordMode.Historical, historicalData(partial), StringsJa)
        assertNull(accolades.chip)
        assertNull(accolades.comparison)
        assertFalse(accolades.firstEver)
    }

    // ─── the pinned version ─────────────────────────────────────────────────────────────────────

    @Test
    fun `breakdown names come from the pinned version, not the live catalogue`() {
        // §4 edge case 1, which is the entire justification for the version pin.
        val names = pinnedExerciseNames(snapshot())
        assertEquals("腕立て伏せ", names["e_pushup"])

        val rows = breakdownRows(listOf(result()), { id -> names[id] }, StringsJa)
        assertEquals(listOf("腕立て伏せ"), rows.map { it.name })
    }

    @Test
    fun `a record with no pinned version resolves nothing and says so`() {
        // §4 edge case 4, and §6's note that 当時の内容は残っていません is the **backup-restore case
        // only**. Never fabricate a structure — and never claim one is missing when it is pinned.
        assertEquals(emptyMap<String, String>(), pinnedExerciseNames(null))
        assertEquals("当時の内容は残っていません", missingSnapshotCopy(null, StringsJa))
        assertNull(missingSnapshotCopy(snapshot(), StringsJa))
    }

    @Test
    fun `an unresolvable station is named 不明な種目 rather than left blank`() {
        // The fallback is the caller's live-catalogue lookup returning nothing; `breakdownRow` owns the
        // word, and a nameless row in a list of names looks like a rendering bug rather than a fact.
        val rows = breakdownRows(listOf(result()), { null }, StringsJa)
        assertEquals(listOf("不明な種目"), rows.map { it.name })
    }

    // ─── the page's own copy ────────────────────────────────────────────────────────────────────

    @Test
    fun `the header dates the record by the day it started`() {
        // §4 edge case 6, stated once for the whole feature: a session that crosses midnight belongs to
        // the day it started, in the grid, in the streak, in the grouping — and here.
        assertEquals("令和八年 ・ 六月十七日", recordDateLine(detail(), StringsJa))
    }

    @Test
    fun `the foot carries 型を見る and 記録を削除`() {
        assertEquals(listOf("型を見る", "記録を削除"), RecordFooterAction.entries.map { it.label(StringsJa) })
        assertEquals(
            listOf("See the routine", "Delete record"),
            RecordFooterAction.entries.map { it.label(StringsEn) },
        )
    }

    @Test
    fun `only a deleted session pops the page`() {
        // §4 edge case 9. Every other fault keeps the page and its もう一度, because 記録を読めません is
        // a store problem and re-asking is the right remedy.
        assertTrue(popsOnFault(GymFault.SessionGone))
        assertFalse(popsOnFault(GymFault.StoreCorrupt))
        assertFalse(popsOnFault(GymFault.StoreUnavailable(null)))
        // Not by sniffing a message: `DECISIONS.md` §Q23 gave `SessionMissing` its own arm in
        // `toGymFault`, so the deleted-session case now arrives *as* `SessionGone`. An `Unknown` that
        // merely mentions the words is some other failure and keeps its retry.
        assertFalse(popsOnFault(GymFault.Unknown("session gone: 4")))
        assertFalse(popsOnFault(null))
    }

    // ─── the optimistic rating ──────────────────────────────────────────────────────────────────

    @Test
    fun `an un-rate in flight renders unrated, not the value it is clearing`() {
        // The box exists so that "no edit in flight" and "edited to unrated" cannot share a value, and
        // the read used to undo it: `edit?.rating ?: stored` mapped `RatingEdit(null)` — the un-rate the
        // user just tapped — straight back to the stored rating. The chip stayed lit until the store
        // round-tripped, and a rejected write reverted to what was already on screen, so the failure was
        // invisible. §4 edge case 7 makes un-rating a real answer.
        assertNull(shownRating(RatingEdit(null), Rating.HARD))
    }

    @Test
    fun `an edit in flight outranks the stored answer, and no edit falls through to it`() {
        assertEquals(Rating.EASY, shownRating(RatingEdit(Rating.EASY), Rating.HARD))
        assertEquals(Rating.HARD, shownRating(null, Rating.HARD))
        assertNull(shownRating(null, null))
    }

    @Test
    fun `the box is dropped only once the store agrees with it`() {
        // Dropping it on `GymWrite.Ok` is the same bug wearing a different hat: the write lands before
        // the read re-emits, so an un-rate would flash its old chip back on for a frame.
        assertEquals(RatingEdit(null), settledEdit(RatingEdit(null), Rating.HARD))
        assertNull(settledEdit(RatingEdit(null), null))
        assertEquals(RatingEdit(Rating.EASY), settledEdit(RatingEdit(Rating.EASY), Rating.HARD))
        assertNull(settledEdit(RatingEdit(Rating.EASY), Rating.EASY))
        assertNull(settledEdit(null, Rating.HARD))
    }

    // ─── the delete ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `もう一度 on a failed delete re-attempts the delete, not the reads`() {
        // `gym.retry()` re-runs every observing *query* and does nothing whatever about a
        // `discardSession` that was rejected — the strip dismissed itself and the record stayed.
        // Read from source for `LifetimeSummaryTest`'s reason: this is a composable's wiring, and there
        // is no Robolectric on this classpath.
        val source = readSource("ui/gym/SessionDetailScreen.kt")
        assertTrue(
            "the strip must re-run the delete",
            source.contains("if (sessionId != null) scope.launch { runDelete(sessionId) }"),
        )
        assertTrue(
            "and a landed delete must re-read the lifetime totals it changed",
            source.contains("gym.refreshLifetimeSummary()"),
        )
    }

    @Test
    fun `the delete comment describes the pop the page actually performs`() {
        // A comment that says the opposite of its code is how the next agent restores the wrong branch:
        // it claimed "the page does not pop on its own" directly above the line that pops on `Ok`.
        val source = readSource("ui/gym/SessionDetailScreen.kt")
        assertFalse(
            "the page does pop on a confirmed delete",
            source.contains("the page does not pop on its own"),
        )
        assertTrue(
            "and the comment must say so",
            source.contains("**Never optimistic, and the pop is the write's.**"),
        )
    }
}

/** As `LifetimeSummaryTest`'s. */
private fun readSource(relative: String): String {
    var dir: java.io.File? = java.io.File("").absoluteFile
    while (dir != null) {
        for (prefix in listOf("app/src/main/java/io/eddiegulay/tempo/", "src/main/java/io/eddiegulay/tempo/")) {
            val candidate = java.io.File(dir, prefix + relative)
            if (candidate.exists()) return candidate.readText()
        }
        dir = dir.parentFile
    }
    throw IllegalStateException("could not locate $relative from ${java.io.File("").absolutePath}")
}

// ─── fixtures ───────────────────────────────────────────────────────────────────────────────────

private fun summary(
    sessionId: Long = 1L,
    activeMs: Long = 374_000L,
    complete: Boolean = true,
    stationsCompleted: Int = 12,
    stationsPlanned: Int = 12,
) = SessionSummary(
    sessionId = sessionId,
    routineId = "r_seven",
    routineVersionId = 1L,
    routineName = "七分間",
    engine = Engine.INTERVAL_CIRCUIT,
    tier = null,
    startedAt = 0L,
    finishedAt = activeMs,
    localDate = LocalDate.of(2026, 6, 17),
    activeMs = activeMs,
    wallMs = activeMs,
    complete = complete,
    reachedTimeCap = false,
    roundsPlanned = 1,
    roundsCompleted = 3,
    stationsPlanned = stationsPlanned,
    stationsCompleted = stationsCompleted,
    totalReps = 320,
    volumeUnits = 0.0,
    rating = Rating.HARD,
    personalBest = true,
)

private fun detail(
    summary: SessionSummary = summary(),
    previous: SessionSummary? = null,
    isStillBest: Boolean = true,
    routineArchived: Boolean = false,
) = SessionDetail(
    summary = summary,
    results = listOf(result()),
    snapshot = snapshot(),
    previous = previous,
    best = null,
    isStillBest = isStillBest,
    routineArchived = routineArchived,
)

private fun snapshot() = RoutineSnapshot(
    versionId = 1L,
    routineId = "r_seven",
    versionNumber = 1,
    name = "七分間",
    engine = Engine.INTERVAL_CIRCUIT,
    rounds = 1,
    timeCapSeconds = null,
    intervalSeconds = null,
    restBetweenStations = 10,
    restBetweenRounds = 0,
    prepareSeconds = 5,
    progressionProgramId = null,
    primaryMetric = BestMetric.MOST_VOLUME,
    stationCount = 1,
    estimatedDurationSeconds = 475,
    estimatedTotalReps = 0,
    structuralHash = 0,
    createdAt = 0L,
    stations = listOf(
        RoutineStation(
            position = 0,
            exerciseId = "e_pushup",
            exercise = Exercise(
                id = "e_pushup",
                nameJa = "腕立て伏せ",
                nameEn = "Push-up",
                pattern = Pattern.H_PUSH,
                secondsPerRep = 2.0,
                difficulty = 1.0,
                isIsometric = false,
                cue = null,
                ladderId = "push",
                catalogVersion = 1,
                archived = false,
            ),
            measure = Measure.REPS,
            prescribedReps = 20,
            prescribedSeconds = null,
            note = null,
        ),
    ),
)

private fun result() = SegmentResult(
    ordinal = 1,
    phase = Phase.REPS,
    roundIndex = 0,
    stationOrder = 0,
    exerciseId = "e_pushup",
    measure = Measure.REPS,
    prescribedReps = 20,
    prescribedSeconds = null,
    actualReps = 18,
    actualMs = 41_000L,
    addedMs = 0L,
    skipped = false,
    difficultyCoefficient = 1.0,
    volumeUnits = 18.0,
    closedAtWallMs = 0L,
    closedAtElapsedMs = 0L,
)
