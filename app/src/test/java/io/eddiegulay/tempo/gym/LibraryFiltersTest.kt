package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.i18n.StringsEn
import io.eddiegulay.tempo.i18n.StringsJa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Search, filter, rank and name — everything the library index does to a list before drawing it.
 *
 * The kana folding these all sit on has its own class; what is pinned here is the layer above it,
 * where the failures are about *policy* rather than about strings. A filter that quietly drops an
 * archived routine back into the list, a よく使う that pushes a manually favourited routine out at the
 * cap, a duplicate name that collides with the one it was supposed to avoid: each is a decision a
 * spec table made, and each would be silently re-decided by an obvious-looking rewrite.
 */
class LibraryFiltersTest {

    // ─── search ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a routine is found by a station's name, not only by its own`() {
        // §3 edge case 2's example: 懸垂 surfaces シンディ, whose name contains no kanji at all.
        assertTrue(
            matchRoutine(
                name = "シンディ",
                origin = "CrossFit, 2004-12-29",
                stationNames = listOf("懸垂", "腕立て伏せ", "スクワット"),
                query = "懸垂",
            ),
        )
    }

    @Test
    fun `a routine is found by its origin, case-insensitively`() {
        assertTrue(matchRoutine("シンディ", "CrossFit, 2004-12-29", emptyList(), "crossfit"))
        assertTrue(matchRoutine("シンディ", "CrossFit, 2004-12-29", emptyList(), "CROSSFIT"))
    }

    @Test
    fun `a routine with no origin is not a match for a latin query`() {
        // All user routines have a null origin (§3 edge case 3). A null must not read as a wildcard.
        assertFalse(matchRoutine("朝の五分", null, emptyList(), "crossfit"))
    }

    @Test
    fun `a blank query matches everything, so opening the filter row does not empty the list`() {
        assertTrue(matchRoutine("七分間", null, emptyList(), ""))
        assertTrue(matchRoutine("七分間", null, emptyList(), "   "))
    }

    @Test
    fun `romaji finds nothing, because there is no romaji index`() {
        // Stated as a test rather than left to be discovered: the spec's instruction is to not pretend
        // it works, and a test is the least ambiguous form of saying so.
        assertFalse(matchRoutine("スクワット", null, emptyList(), "sukuwatto"))
    }

    @Test
    fun `an exercise is found in either script and in english`() {
        val pushup = exercise(id = "pushup", ja = "腕立て伏せ", en = "push-up")
        assertTrue(matchExercise(pushup, "腕立て"))
        assertTrue(matchExercise(pushup, "PUSH"))
        assertFalse(matchExercise(pushup, "懸垂"))
    }

    @Test
    fun `an exercise's form cue is not searched`() {
        // A cue is a sentence. Matching prose returns half the catalogue for one common particle.
        val pushup = exercise(id = "pushup", ja = "腕立て伏せ", en = "push-up", cue = "肘は体の近くに")
        assertFalse(matchExercise(pushup, "肘"))
    }

    // ─── duration buckets ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a duration bucket's endpoint belongs to the lower bucket`() {
        // The three labels share their endpoints and a routine must land in exactly one. Five minutes
        // excluded from the chip that says 五分 is the kind of thing a user finds once and never
        // trusts again.
        assertEquals(DurationBucket.UNDER_FIVE, durationBucket(300))
        assertEquals(DurationBucket.FIVE_TO_FIFTEEN, durationBucket(301))
        assertEquals(DurationBucket.FIVE_TO_FIFTEEN, durationBucket(900))
        assertEquals(DurationBucket.OVER_FIFTEEN, durationBucket(901))
    }

    @Test
    fun `the shipped routines land where a user would look for them`() {
        assertEquals(DurationBucket.UNDER_FIVE, durationBucket(240))   // タバタ, 4:00
        assertEquals(DurationBucket.FIVE_TO_FIFTEEN, durationBucket(475)) // 七分間, computed
        assertEquals(DurationBucket.OVER_FIFTEEN, durationBucket(1200))   // シンディ, 20:00
    }

    @Test
    fun `a zero-length routine still has a bucket`() {
        assertEquals(DurationBucket.UNDER_FIVE, durationBucket(0))
    }

    @Test
    fun `each duration chip is its own string, not one wave dash moved around`() {
        // 〜五分 / 五〜十五分 / 十五分〜 carry the whole range on one character used leading, medially
        // and trailingly. English needs a different word in each position, so the three are three
        // members of the table — interpolating an endpoint into a shared 「〜{n}分」 would have
        // translated the typography and left `Under`, `to` and `over` with nowhere to live.
        assertEquals(
            listOf("〜五分", "五〜十五分", "十五分〜"),
            DurationBucket.entries.map { it.label(StringsJa) },
        )
        assertEquals(
            listOf("Under 5 min", "5–15 min", "15 min+"),
            DurationBucket.entries.map { it.label(StringsEn) },
        )
    }

    // ─── filters ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an empty filter changes nothing, including the order`() {
        val list = listOf(routine("a", name = "七分間"), routine("b", name = "タバタ"))
        assertEquals(list, applyFilters(list, RoutineFilter()))
    }

    @Test
    fun `filters compose, and an empty set is no constraint rather than no matches`() {
        val list = listOf(
            routine("a", name = "七分間", tier = Tier.BEGINNER, engine = Engine.INTERVAL_CIRCUIT, seconds = 475),
            routine("b", name = "シンディ", tier = Tier.INTERMEDIATE, engine = Engine.AMRAP, seconds = 1200),
            routine("c", name = "タバタ", tier = Tier.INTERMEDIATE, engine = Engine.INTERVAL_CIRCUIT, seconds = 240),
        )
        assertEquals(
            listOf("b", "c"),
            applyFilters(list, RoutineFilter(tiers = setOf(Tier.INTERMEDIATE))).map { it.routineId },
        )
        assertEquals(
            listOf("c"),
            applyFilters(
                list,
                RoutineFilter(tiers = setOf(Tier.INTERMEDIATE), engines = setOf(Engine.INTERVAL_CIRCUIT)),
            ).map { it.routineId },
        )
        assertEquals(
            listOf("a"),
            applyFilters(list, RoutineFilter(duration = DurationBucket.FIVE_TO_FIFTEEN)).map { it.routineId },
        )
    }

    @Test
    fun `an archived routine never comes back through a filter`() {
        // §1 rule 2: archiving takes a routine out of the index, よく使う and the filter list. The
        // repository query already excludes it; this is the second lock, because the one call site
        // that will ever hand an archived row in is the one that got it from a session.
        val list = listOf(routine("a", name = "七分間"), routine("b", name = "七分間 の写し", archivedAt = 1L))
        assertEquals(listOf("a"), applyFilters(list, RoutineFilter()).map { it.routineId })
        // The query matches both names, so only the archival rule can be what removes the second.
        assertEquals(listOf("a"), applyFilters(list, RoutineFilter(query = "七分")).map { it.routineId })
    }

    @Test
    fun `a filter that matches nothing returns nothing, rather than falling back to everything`() {
        // The NoMatch state renders 該当する型はありません with the filter row still open. A fallback to
        // the unfiltered list would make that state unreachable and the chips look broken.
        val list = listOf(routine("a", name = "七分間"))
        assertTrue(applyFilters(list, RoutineFilter(query = "ばーべる")).isEmpty())
    }

    // ─── よく使う ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `よく使う ranks by recent use, then lifetime count, then name`() {
        val list = listOf(
            routine("a", name = "あ"),
            routine("b", name = "い"),
            routine("c", name = "う"),
        )
        val ranked = rankFrequent(
            routines = list,
            recentUsage = mapOf("a" to 2, "b" to 5, "c" to 2),
            usageCounts = mapOf("a" to 40, "b" to 5, "c" to 3),
        )
        assertEquals(listOf("b", "a", "c"), ranked.map { it.routineId })
    }

    @Test
    fun `a manually favourited routine survives the cap without outranking anyone`() {
        // §3 edge case 5 gives the ranking and the guarantee as two separate clauses: "recentUsage(60)
        // desc, ties by usageCounts(), then name; unioned with manual favourites; capped at 4. A
        // manually favourited routine always appears even at zero usage." Favourite decides *who
        // stays* when the list is truncated; it is not a fourth sort key. Both halves are asserted
        // here because sorting favourites to the top satisfies the second and breaks the first.
        val list = listOf(
            routine("fav", name = "自分の型", favourite = true),
            routine("a", name = "あ"),
            routine("b", name = "い"),
            routine("c", name = "う"),
            routine("d", name = "え"),
        )
        val ranked = rankFrequent(
            routines = list,
            recentUsage = mapOf("a" to 9, "b" to 8, "c" to 7, "d" to 6),
            usageCounts = emptyMap(),
        )
        // Cap active: the zero-use favourite takes the slot 六回-used え would have had, and takes it
        // last, because it is last by the spec's own keys.
        assertEquals(listOf("a", "b", "c", "fav"), ranked.map { it.routineId })
    }

    @Test
    fun `a favourite does not jump the queue when there is room for everyone`() {
        // The common case, and the one the promotion broke: with nothing being dropped the guarantee
        // is not even active, so the section is exactly the spec's three keys.
        val list = listOf(
            routine("fav", name = "自分の型", favourite = true),
            routine("a", name = "あ"),
            routine("b", name = "い"),
        )
        val ranked = rankFrequent(
            routines = list,
            recentUsage = mapOf("a" to 9, "b" to 8),
            usageCounts = emptyMap(),
        )
        assertEquals(listOf("a", "b", "fav"), ranked.map { it.routineId })
    }

    @Test
    fun `a routine nobody has used and nobody favourited is not frequently used`() {
        val list = listOf(routine("a", name = "あ"), routine("b", name = "い"))
        assertEquals(listOf("a"), rankFrequent(list, mapOf("a" to 1), emptyMap()).map { it.routineId })
    }

    @Test
    fun `an archived routine is never frequently used, however often it was`() {
        val list = listOf(routine("a", name = "あ", archivedAt = 1L, favourite = true))
        assertTrue(rankFrequent(list, mapOf("a" to 30), mapOf("a" to 90)).isEmpty())
    }

    // ─── naming a copy ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the first copy is の写し and the second is の写し二`() {
        // §3 edge case 7. The counter starts at 二 because the first copy is unnumbered — の写し一
        // would be an off-by-one the user reads as "copy one" of a copy that does not exist.
        assertEquals("七分間 の写し", uniqueName("七分間", emptySet(), StringsJa))
        assertEquals("七分間 の写し二", uniqueName("七分間", setOf("七分間 の写し"), StringsJa))
        assertEquals(
            "七分間 の写し三",
            uniqueName("七分間", setOf("七分間 の写し", "七分間 の写し二"), StringsJa),
        )
    }

    @Test
    fun `a gap in the numbering is filled rather than skipped past`() {
        assertEquals("七分間 の写し二", uniqueName("七分間", setOf("七分間 の写し", "七分間 の写し三"), StringsJa))
    }

    @Test
    fun `an unrelated name never blocks a copy`() {
        assertEquals("七分間 の写し", uniqueName("七分間", setOf("タバタ の写し", "七分間"), StringsJa))
    }
}

// ─── fixtures ───────────────────────────────────────────────────────────────────────────────────

private fun routine(
    id: String,
    name: String,
    tier: Tier? = null,
    engine: Engine = Engine.INTERVAL_CIRCUIT,
    seconds: Int = 475,
    favourite: Boolean = false,
    archivedAt: Long? = null,
) = RoutineSummary(
    routineId = id,
    versionId = 1L,
    name = name,
    engine = engine,
    builtIn = true,
    tier = tier,
    origin = null,
    favourite = favourite,
    sortOrder = 0,
    rounds = 1,
    timeCapSeconds = null,
    intervalSeconds = null,
    restBetweenStations = 10,
    restBetweenRounds = 60,
    stationCount = 12,
    estimatedDurationSeconds = seconds,
    estimatedTotalReps = 0,
    primaryMetric = BestMetric.MOST_VOLUME,
    timesDone = 0,
    lastStartedAt = null,
    lastActiveMs = null,
    best = null,
    archivedAt = archivedAt,
)

private fun exercise(
    id: String,
    ja: String,
    en: String,
    cue: String? = null,
    secondsPerRep: Double = 2.0,
) = Exercise(
    id = id,
    nameJa = ja,
    nameEn = en,
    pattern = Pattern.H_PUSH,
    secondsPerRep = secondsPerRep,
    difficulty = 1.0,
    isIsometric = false,
    cue = cue,
    ladderId = null,
    catalogVersion = 1,
    archived = false,
)
