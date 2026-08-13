package io.eddiegulay.tempo.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three decisions `GYM.HOME` and `GYM.LIBRARY.INDEX`/`DETAIL` make before they draw.
 *
 * What is pinned here is not arithmetic — it is a set of *refusals*. A rebooted session must not offer
 * 続ける, a live session must not let a stale row draw a second banner, a filtered page must not leave
 * an excluded routine sitting in よく使う, and a routine with a hole in it must not reach the compiler.
 * Every one of those is invisible in the happy path and would be quietly re-decided by an
 * obvious-looking rewrite, which is the only reason a test can be written for it at all.
 */
class GymPageStateTest {

    // ─── GYM.HOME's preview widths ──────────────────────────────────────────────────────────────

    @Test
    fun `a first run sees six 型, and a trained user sees four`() {
        val six = (1..8).map { card("r_$it") }
        assertEquals(6, homeBuiltIn(feed(six, everTrained = false)).size)
        assertEquals(4, homeBuiltIn(feed(six, everTrained = true)).size)
    }

    @Test
    fun `a preview shorter than its width is not padded`() {
        val two = listOf(card("r_seven"), card("r_tabata"))
        assertEquals(2, homeBuiltIn(feed(two, everTrained = false)).size)
    }

    // ─── the つづき affordance ───────────────────────────────────────────────────────────────────

    @Test
    fun `nothing running and nothing stored owes no banner`() {
        assertEquals(ResumeAffordance.None, resumeAffordance(live = null, stale = null))
    }

    @Test
    fun `a live session wins the banner and suppresses the stale prompt`() {
        // §B edge case 1: a process that died mid-session and a session started before returning can
        // both be true. Two つづき banners would be the page contradicting itself.
        val affordance = resumeAffordance(
            live = ActiveSession(sessionId = 9L, routineId = "r_seven", routineName = "七分間"),
            stale = open(sessionId = 4L, resumability = Resumability.RESUMABLE),
        )
        assertEquals(ResumeAffordance.LiveBanner, affordance)
    }

    @Test
    fun `a resumable row asks all three questions`() {
        val stale = open(resumability = Resumability.RESUMABLE)
        assertEquals(ResumeAffordance.StalePrompt, resumeAffordance(live = null, stale = stale))
        assertEquals(ResumeOptions(canResume = true, canRecord = true, canDiscard = true), resumeOptions(stale))
    }

    @Test
    fun `a reboot removes 続ける, and removes it rather than disabling it`() {
        // `03-player.md` §E.3: a different boot means the elapsed clock is unreconstructable, and any
        // resume would fabricate duration. The affordance is a different case, not a flag.
        val stale = open(resumability = Resumability.REBOOTED)
        assertEquals(ResumeAffordance.StalePromptNoResume, resumeAffordance(live = null, stale = stale))
        assertFalse(resumeOptions(stale).canResume)
        assertTrue(resumeOptions(stale).canRecord)
    }

    @Test
    fun `a four-hour-old session is offered the same two doors as a rebooted one`() {
        val stale = open(resumability = Resumability.STALE)
        assertEquals(ResumeAffordance.StalePromptNoResume, resumeAffordance(live = null, stale = stale))
        assertEquals(ResumeOptions(canResume = false, canRecord = true, canDiscard = true), resumeOptions(stale))
    }

    @Test
    fun `a session with nothing closed loses 記録する, never 捨てる`() {
        // Auto-discarding is tempting and wrong: the user should learn this app never deletes silently.
        val stale = open(resumability = Resumability.NOTHING_TO_SAVE)
        assertEquals(ResumeOptions(canResume = false, canRecord = false, canDiscard = true), resumeOptions(stale))
    }

    @Test
    fun `a deleted routine loses 続ける but keeps 記録する and 捨てる`() {
        // §B edge case 2. There is nothing to resume *into*, and the banner renders from the session
        // row's denormalised name — the one case the version foreign key cannot serve.
        val stale = open(resumability = Resumability.RESUMABLE, routineExists = false)
        assertEquals(ResumeAffordance.StalePromptNoResume, resumeAffordance(live = null, stale = stale))
        assertEquals(ResumeOptions(canResume = false, canRecord = true, canDiscard = true), resumeOptions(stale))
    }

    // ─── 始める's preconditions ─────────────────────────────────────────────────────────────────

    @Test
    fun `an ordinary routine blocks nothing`() {
        assertEquals(StartBlock.None, startBlock(detail(stations = listOf(station("pushup")))))
    }

    @Test
    fun `an archived routine cannot be started`() {
        val blocked = detail(stations = listOf(station("pushup")), archivedAt = 1_000L)
        assertEquals(StartBlock.Archived, startBlock(blocked))
    }

    @Test
    fun `a station the catalogue no longer knows blocks the start on the page, not mid-session`() {
        // `04` §3's UnknownExercise state, and the reason `RoutineStation` keeps the id beside a null
        // exercise: the page can be specific about what is missing.
        val blocked = detail(stations = listOf(station("pushup"), station("ghost", known = false)))
        assertEquals(StartBlock.UnknownExercise, startBlock(blocked))
    }

    @Test
    fun `an empty routine is caught here rather than thrown by the compiler`() {
        assertEquals(StartBlock.NoStations, startBlock(detail(stations = emptyList())))
    }

    @Test
    fun `archival is reported ahead of a missing station, because it is the one the user can act on`() {
        val blocked = detail(stations = listOf(station("ghost", known = false)), archivedAt = 1_000L)
        assertEquals(StartBlock.Archived, startBlock(blocked))
    }

    // ─── the library index's three sections ─────────────────────────────────────────────────────

    @Test
    fun `the index splits built-ins from the user's own and counts what matched`() {
        val sections = libraryIndexRoutines(
            routines = listOf(summary("r_seven", "七分間"), summary("u_morning", "朝の五分", builtIn = false)),
            filter = RoutineFilter(),
            recentUsage = emptyMap(),
            usageCounts = emptyMap(),
        )
        assertEquals(listOf("七分間"), sections.builtIn.map { it.name })
        assertEquals(listOf("朝の五分"), sections.user.map { it.name })
        assertEquals(2, sections.matched)
    }

    @Test
    fun `a filter that excludes a routine excludes it from よく使う too`() {
        // Filter first, then rank. A filtered page whose top section still offers the excluded routine
        // is the filter appearing not to work.
        val tabata = summary("r_tabata", "タバタ", tier = Tier.INTERMEDIATE)
        val seven = summary("r_seven", "七分間", tier = Tier.BEGINNER)
        val sections = libraryIndexRoutines(
            routines = listOf(seven, tabata),
            filter = RoutineFilter(tiers = setOf(Tier.BEGINNER)),
            recentUsage = mapOf("r_seven" to 4, "r_tabata" to 9),
            usageCounts = mapOf("r_seven" to 4, "r_tabata" to 9),
        )
        assertEquals(listOf("七分間"), sections.builtIn.map { it.name })
        assertTrue(sections.frequent.none { it.routineId == "r_tabata" })
    }

    @Test
    fun `one frequently used routine is no section at all`() {
        // A one-item "frequently used" is noise (`04` §3's Ready state); the threshold lives in
        // LibraryFilters so both halves of the rule cannot disagree.
        val sections = libraryIndexRoutines(
            routines = listOf(summary("r_seven", "七分間"), summary("r_tabata", "タバタ")),
            filter = RoutineFilter(),
            recentUsage = mapOf("r_seven" to 3),
            usageCounts = mapOf("r_seven" to 3),
        )
        assertTrue(sections.frequent.isEmpty())
    }

    @Test
    fun `two frequently used routines are a section`() {
        val sections = libraryIndexRoutines(
            routines = listOf(summary("r_seven", "七分間"), summary("r_tabata", "タバタ")),
            filter = RoutineFilter(),
            recentUsage = mapOf("r_seven" to 3, "r_tabata" to 1),
            usageCounts = mapOf("r_seven" to 3, "r_tabata" to 1),
        )
        assertEquals(listOf("七分間", "タバタ"), sections.frequent.map { it.name })
    }

    @Test
    fun `a routine is searchable by a station name the projection does not carry`() {
        // §3 edge case 2: 懸垂 must surface シンディ, whose name contains no kanji at all. The names
        // arrive through the resolver because a RoutineSummary deliberately has no stations.
        val cindy = summary("r_cindy", "シンディ")
        val sections = libraryIndexRoutines(
            routines = listOf(cindy, summary("r_tabata", "タバタ")),
            filter = RoutineFilter(query = "懸垂"),
            recentUsage = emptyMap(),
            usageCounts = emptyMap(),
            stationNames = { if (it.routineId == "r_cindy") listOf("懸垂", "腕立て伏せ") else emptyList() },
        )
        assertEquals(listOf("シンディ"), sections.builtIn.map { it.name })
    }

    @Test
    fun `a query that matches nothing is an empty match count, not an unfiltered list`() {
        val sections = libraryIndexRoutines(
            routines = listOf(summary("r_seven", "七分間")),
            filter = RoutineFilter(query = "ばーぴー"),
            recentUsage = emptyMap(),
            usageCounts = emptyMap(),
        )
        assertEquals(0, sections.matched)
    }
}

// ─── fixtures ───────────────────────────────────────────────────────────────────────────────────

private fun card(id: String) = RoutineCard(
    routineId = id,
    name = id,
    stationCount = 0,
    workSeconds = null,
    restBetweenStations = 0,
    estimatedDurationSeconds = 0,
    timesDone = 0,
    lastResult = null,
    best = null,
    builtIn = true,
    origin = null,
)

private fun feed(builtIn: List<RoutineCard>, everTrained: Boolean) = GymHomeFeed(
    recent = emptyList(),
    builtIn = builtIn,
    builtInTotal = builtIn.size,
    userRoutines = emptyList(),
    everTrained = everTrained,
)

private fun open(
    sessionId: Long = 1L,
    resumability: Resumability = Resumability.RESUMABLE,
    routineExists: Boolean = true,
) = ResumableSession(
    sessionId = sessionId,
    routineId = "r_seven",
    routineVersionId = 1L,
    routineName = "七分間",
    routineExists = routineExists,
    tier = null,
    engine = Engine.INTERVAL_CIRCUIT,
    startedAtWallMs = 0L,
    lastWriteWallMs = 0L,
    stationsPlanned = 12,
    roundsPlanned = 1,
    clock = PersistedClock(
        startedAtElapsedMs = 0L,
        pausedAccumulatedMs = 0L,
        pausedAtElapsedMs = null,
        pausedAtWallMs = null,
        bootAnchorMs = 0L,
        anchorWallMs = 0L,
    ),
    results = emptyList(),
    resumability = resumability,
)

private fun station(exerciseId: String, known: Boolean = true) = RoutineStation(
    position = 0,
    exerciseId = exerciseId,
    exercise = if (!known) {
        null
    } else {
        Exercise(
            id = exerciseId,
            nameJa = "腕立て伏せ",
            nameEn = "push-up",
            pattern = Pattern.H_PUSH,
            secondsPerRep = 2.0,
            difficulty = 1.0,
            isIsometric = false,
            cue = null,
            ladderId = "push",
            catalogVersion = 1,
            archived = false,
        )
    },
    measure = Measure.REPS,
    prescribedReps = 10,
    prescribedSeconds = null,
    note = null,
)

private fun detail(
    stations: List<RoutineStation>,
    archivedAt: Long? = null,
) = RoutineDetail(
    routineId = "r_seven",
    builtIn = true,
    tier = Tier.BEGINNER,
    origin = null,
    favourite = false,
    derivedFromRoutineId = null,
    scaledFromRoutineId = null,
    createdAt = 0L,
    archivedAt = archivedAt,
    snapshot = RoutineSnapshot(
        versionId = 1L,
        routineId = "r_seven",
        versionNumber = 1,
        name = "七分間",
        engine = Engine.INTERVAL_CIRCUIT,
        rounds = 1,
        timeCapSeconds = null,
        intervalSeconds = 30,
        restBetweenStations = 10,
        restBetweenRounds = 60,
        prepareSeconds = 5,
        progressionProgramId = null,
        primaryMetric = BestMetric.MOST_VOLUME,
        stationCount = stations.size,
        estimatedDurationSeconds = 475,
        estimatedTotalReps = 0,
        structuralHash = 0,
        createdAt = 0L,
        stations = stations,
    ),
    bests = emptyList(),
    recentAttempts = emptyList(),
    timesDone = 0,
)

private fun summary(
    id: String,
    name: String,
    builtIn: Boolean = true,
    tier: Tier? = null,
    favourite: Boolean = false,
) = RoutineSummary(
    routineId = id,
    versionId = 1L,
    name = name,
    engine = Engine.INTERVAL_CIRCUIT,
    builtIn = builtIn,
    tier = tier,
    origin = null,
    favourite = favourite,
    sortOrder = 0,
    rounds = 1,
    timeCapSeconds = null,
    intervalSeconds = 30,
    restBetweenStations = 10,
    restBetweenRounds = 60,
    stationCount = 12,
    estimatedDurationSeconds = 475,
    estimatedTotalReps = 0,
    primaryMetric = BestMetric.MOST_VOLUME,
    timesDone = 0,
    lastStartedAt = null,
    lastActiveMs = null,
    best = null,
    archivedAt = null,
)
