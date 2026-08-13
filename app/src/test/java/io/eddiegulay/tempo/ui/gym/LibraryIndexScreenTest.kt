package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.i18n.StringsEn
import io.eddiegulay.tempo.i18n.StringsJa
import io.eddiegulay.tempo.gym.BestMetric
import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.RoutineBest
import io.eddiegulay.tempo.gym.RoutineSummary
import io.eddiegulay.tempo.gym.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What `GYM.LIBRARY.INDEX` says, as opposed to how it is laid out.
 *
 * The page's searching, ranking and folding are `LibraryFiltersTest`'s and `GymPageStateTest`'s; what
 * is left to check here is the copy — that a card states its engine exactly once, that a number the
 * projection cannot support is never printed, and that 削除 tells the truth about what it is about to
 * do. All three are the kind of mistake that compiles perfectly and ships.
 */
class LibraryIndexScreenTest {

    // 七分間, as `RoutineSummary` carries it: twelve duration stations, 12×30 + 11×10 + 5 = 475s.
    private val sevenMinute = summary(
        routineId = "r_seven_minute",
        name = "七分間",
        engine = Engine.INTERVAL_CIRCUIT,
        builtIn = true,
        tier = Tier.BEGINNER,
        stationCount = 12,
        estimatedDurationSeconds = 475,
        timesDone = 14,
    )

    // シンディ: a twenty-minute AMRAP over three stations, seventeen rounds at best.
    private val cindy = summary(
        routineId = "r_cindy",
        name = "シンディ",
        engine = Engine.AMRAP,
        builtIn = true,
        tier = Tier.INTERMEDIATE,
        stationCount = 3,
        timeCapSeconds = 20 * 60,
        estimatedDurationSeconds = 20 * 60,
        timesDone = 6,
        best = best(BestMetric.MOST_ROUNDS, 17.0),
    )

    @Test
    fun `a circuit states its shape, its estimate, and the engine on the line below`() {
        val copy = routineCardCopy(sevenMinute, StringsJa)

        assertEquals("入門", copy.tier)
        assertEquals("十二種目 ・ 約 八分", copy.detail)
        assertEquals("巡回", copy.engine)
        assertNull(copy.best)
        assertEquals("十四回", copy.count)
    }

    @Test
    fun `七分間 rounds to eight minutes, because 約 is not a licence to repeat the marketing`() {
        // `00-plan.md` §2 row 18: the routine is named 七分間 and the app says 約 八分, deliberately.
        assertTrue(routineCardCopy(sevenMinute, StringsJa).detail.contains("約 八分"))
    }

    @Test
    fun `a record takes the meta line and the engine moves up, so it is stated exactly once`() {
        val copy = routineCardCopy(cindy, StringsJa)

        assertEquals("三種目 ・ 二十分 ・ 時間内", copy.detail)
        assertEquals("最高 十七巡", copy.best)
        assertNull(copy.engine)
    }

    @Test
    fun `a time cap is the routine's duration and is never restated as an estimate`() {
        // Without a record the engine stays below, and 二十分 appears once — never beside 約 二十分.
        val copy = routineCardCopy(cindy.copy(best = null), StringsJa)

        assertEquals("三種目 ・ 二十分", copy.detail)
        assertEquals("時間内", copy.engine)
        assertFalse(copy.detail.contains("約"))
    }

    @Test
    fun `a cap on an engine that has no 制限時間 row is not printed`() {
        // §6 marks 制限時間 AMRAP-only and `EngineRows` honours that; the card must not disagree with
        // the detail page about which routines have a clock.
        val copy = routineCardCopy(
            cindy.copy(engine = Engine.FOR_TIME, best = null, estimatedDurationSeconds = 2700),
            StringsJa,
        )

        assertEquals("三種目 ・ 約 四十五分", copy.detail)
    }

    @Test
    fun `a routine nobody has done yet carries no count`() {
        val copy = routineCardCopy(sevenMinute.copy(timesDone = 0), StringsJa)

        assertNull(copy.count)
        assertFalse(copy.description.contains("〇"))
    }

    @Test
    fun `a best whose metric has no documented label is no best at all`() {
        // HIGHEST_STEP is the one `BestMetric` with no Japanese word anywhere in the specs, and
        // `DECISIONS.md` §Q9 forbids inventing one. The card falls back to its engine rather than
        // printing a bare number under a heading that does not exist.
        val copy = routineCardCopy(sevenMinute.copy(best = best(BestMetric.HIGHEST_STEP, 9.0)), StringsJa)

        assertNull(copy.best)
        assertEquals("巡回", copy.engine)
    }

    @Test
    fun `a user routine with no tier says nothing about its difficulty`() {
        val copy = routineCardCopy(sevenMinute.copy(tier = null, builtIn = false), StringsJa)

        assertNull(copy.tier)
        assertFalse(copy.description.contains("入門"))
    }

    @Test
    fun `the spoken card keeps one order whether or not a record exists`() {
        // §3's accessibility line fixes it: name → tier → structure → estimate → engine → count, "so
        // the first two words disambiguate". A description that reordered itself when a PR landed
        // would be two readings of one card.
        assertEquals("七分間、入門、十二種目、約 八分、巡回、十四回", routineCardCopy(sevenMinute, StringsJa).description)
        assertEquals("シンディ、中級、三種目、二十分、時間内、最高 十七巡、六回", routineCardCopy(cindy, StringsJa).description)
    }

    @Test
    fun `a section heading counts what is under it`() {
        assertEquals("よく使う、二件", sectionSemantics("よく使う", 2, StringsJa))
        assertEquals("Frequent, 2 items", sectionSemantics("Frequent", 2, StringsEn))
    }

    @Test
    fun `the card's punctuation follows the language, not the Japanese typography`() {
        // 「 ・ 」 is a katakana middle dot and 「、」 an ideographic comma; both are `fmt`'s and both
        // have to become ASCII in a Latin build. A separator hard-coded at the join — which is how
        // this line was written — ships a CJK glyph into English and is invisible in a Japanese test.
        val detail = routineCardCopy(cindy.copy(best = null), StringsEn).detail
        assertFalse(detail.contains("・"))
        assertTrue(detail.contains(" · "))
        assertFalse(routineCardCopy(cindy, StringsEn).description.contains("、"))
    }

    @Test
    fun `an empty section announces its name alone`() {
        // 自分の型 is the one heading that survives having nothing under it, and 〇件 would scold a
        // user who is about to be told 型はまだありません on the next line anyway.
        assertEquals("自分の型", sectionSemantics("自分の型", 0, StringsJa))
    }

    @Test
    fun `a built-in offers no 削除`() {
        // §1 rule 5: built-ins are never archivable. 写して作る stays, because copying one is how you
        // get a routine you can edit.
        val items = routineMenuItems(sevenMinute)

        assertFalse(items.contains(RoutineMenuItem.Delete))
        assertTrue(items.contains(RoutineMenuItem.Duplicate))
        assertTrue(items.contains(RoutineMenuItem.Start))
    }

    @Test
    fun `a user routine can be deleted`() {
        assertTrue(routineMenuItems(sevenMinute.copy(builtIn = false)).contains(RoutineMenuItem.Delete))
    }

    @Test
    fun `the よく使う item states the direction it will move the routine`() {
        assertEquals("よく使うに入れる", RoutineMenuItem.Favourite.label(StringsJa))
        assertEquals("よく使うから外す", RoutineMenuItem.Unfavourite.label(StringsJa))
        assertTrue(routineMenuItems(sevenMinute).contains(RoutineMenuItem.Favourite))
        assertTrue(
            routineMenuItems(sevenMinute.copy(favourite = true)).contains(RoutineMenuItem.Unfavourite),
        )
    }

    // 削除's two-branch confirm is `deleteRoutineConfirm`'s, shared with `GYM.LIBRARY.DETAIL` and
    // pinned by `LibraryDetailCopyTest` — this page reads it and adds no second implementation to
    // test.

    private fun summary(
        routineId: String,
        name: String,
        engine: Engine,
        builtIn: Boolean,
        tier: Tier?,
        stationCount: Int,
        estimatedDurationSeconds: Int,
        timesDone: Int,
        timeCapSeconds: Int? = null,
        best: RoutineBest? = null,
    ) = RoutineSummary(
        routineId = routineId,
        versionId = 1L,
        name = name,
        engine = engine,
        builtIn = builtIn,
        tier = tier,
        origin = null,
        favourite = false,
        sortOrder = 0,
        rounds = 1,
        timeCapSeconds = timeCapSeconds,
        intervalSeconds = null,
        restBetweenStations = 10,
        restBetweenRounds = 60,
        stationCount = stationCount,
        estimatedDurationSeconds = estimatedDurationSeconds,
        estimatedTotalReps = 0,
        primaryMetric = BestMetric.MOST_VOLUME,
        timesDone = timesDone,
        lastStartedAt = null,
        lastActiveMs = null,
        best = best,
        archivedAt = null,
    )

    private fun best(metric: BestMetric, value: Double) = RoutineBest(
        routineId = "r_cindy",
        routineName = "シンディ",
        engine = Engine.AMRAP,
        metric = metric,
        value = value,
        tiebreak = 0.0,
        sessionId = 1L,
        achievedAt = 0L,
        localDate = LocalDate.of(2026, 6, 17),
        timesDone = 6,
        routineArchived = false,
        structureChanged = false,
    )
}
