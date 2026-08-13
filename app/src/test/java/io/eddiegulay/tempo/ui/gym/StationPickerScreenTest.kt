package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.gym.Engine
import io.eddiegulay.tempo.gym.Exercise
import io.eddiegulay.tempo.gym.Measure
import io.eddiegulay.tempo.gym.Pattern
import io.eddiegulay.tempo.gym.RoutineDraft
import io.eddiegulay.tempo.gym.StationDraft
import io.eddiegulay.tempo.gym.allowedMeasures
import io.eddiegulay.tempo.gym.paceEstimateSec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GYM.LIBRARY.STATION_PICKER`'s own decisions.
 *
 * `allowedMeasures`, `defaultPrescription` and `paceEstimateSec` are `gym/`'s and are tested there.
 * What this file owns is what the page *says* about their answers — and the one derivation it makes:
 * showing the clash **before** the station is committed, which needs the draft the station would
 * produce rather than the one that exists.
 */
class StationPickerScreenTest {

    private fun exercise(
        id: String,
        name: String,
        pattern: Pattern,
        difficulty: Double = 1.0,
        secondsPerRep: Double = 2.0,
    ) = Exercise(
        id = id,
        nameJa = name,
        nameEn = id,
        pattern = pattern,
        secondsPerRep = secondsPerRep,
        difficulty = difficulty,
        isIsometric = false,
        cue = null,
        ladderId = null,
        catalogVersion = 1,
        archived = false,
    )

    private val pushup = exercise("pushup", "腕立て伏せ", Pattern.H_PUSH)
    private val dip = exercise("dip", "ディップス", Pattern.H_PUSH, difficulty = 1.2, secondsPerRep = 2.5)
    private val squat = exercise("squat", "スクワット", Pattern.SQUAT, difficulty = 0.5, secondsPerRep = 1.8)
    private val catalogue = mapOf(pushup.id to pushup, dip.id to dip, squat.id to squat)

    private fun draft(vararg ids: String, rounds: Int? = 1) = RoutineDraft(
        routineId = "r_user",
        name = "朝の五分",
        engine = Engine.INTERVAL_CIRCUIT,
        stations = ids.map { StationDraft(it, Measure.REPS, 20, null) },
        rounds = rounds,
        timeCapSeconds = null,
        intervalSeconds = null,
        restBetweenStations = 15,
        restBetweenRounds = 60,
        prepareSeconds = 5,
    )

    private fun clash(draft: RoutineDraft, index: Int?, exerciseId: String) = prospectiveClash(
        draft = draft,
        index = index,
        exerciseId = exerciseId,
        pattern = { catalogue[it]?.pattern },
        name = { catalogue[it]?.nameJa ?: UNKNOWN_EXERCISE },
    )

    // ─── The words ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a disabled save says what it is waiting for`() {
        assertEquals("保存", pickerSaveSemantics(canSave = true))
        assertEquals("保存、種目をえらんでください", pickerSaveSemantics(canSave = false))
    }

    @Test
    fun `a row announces its movement, its pattern and its difficulty`() {
        assertEquals("腕立て伏せ、押す、難度 一.〇", exerciseSemantics(pushup))
        assertEquals("スクワット、しゃがむ、難度 〇.五", exerciseSemantics(squat))
    }

    @Test
    fun `a disabled chip carries its reason and an enabled one carries nothing`() {
        // §3: 「秒数、この方式では使えません」 — 完走 is scored in reps, so a station that ends on a
        // timer is not part of the thing being measured.
        val forTime = allowedMeasures(Engine.FOR_TIME).associateBy { it.measure }
        assertEquals("秒数、この方式では使えません", measureSemantics(forTime.getValue(Measure.DURATION)))
        assertEquals("回数", measureSemantics(forTime.getValue(Measure.REPS)))

        // 毎分 is defined by a fixed rep count inside a fixed window, so an open set has no window.
        val emom = allowedMeasures(Engine.EMOM).associateBy { it.measure }
        assertEquals("限界まで、この方式では使えません", measureSemantics(emom.getValue(Measure.MAX_EFFORT)))
    }

    @Test
    fun `the pace is labelled 目安 every time it appears`() {
        // §3 picker edge case 5: it never advances anything, and the label is what says so.
        assertEquals("目安 四十秒", paceLine(paceEstimateSec(StationDraft("pushup", Measure.REPS, 20, null), pushup)))
        assertEquals("目安 一分三十秒", paceLine(90))
    }

    @Test
    fun `限界まで has no pace at all`() {
        // The wheel is replaced by できるところまで and the line disappears — there is nothing to
        // estimate, and a number here would be the app deciding what "to your limit" means.
        assertNull(paceLine(paceEstimateSec(StationDraft("run", Measure.MAX_EFFORT, null, null), null)))
    }

    // ─── The list ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `groups keep the catalogue's own order and drop what the search emptied`() {
        val byPattern = linkedMapOf(
            Pattern.H_PUSH to listOf(pushup, dip),
            Pattern.SQUAT to listOf(squat),
        )

        assertEquals(
            listOf(Pattern.H_PUSH to listOf(pushup, dip), Pattern.SQUAT to listOf(squat)),
            pickerGroups(byPattern, ""),
        )
        assertEquals(listOf(Pattern.SQUAT to listOf(squat)), pickerGroups(byPattern, "スクワット"))
        assertTrue(pickerGroups(byPattern, "けんすい").isEmpty())
    }

    @Test
    fun `the query is trimmed before it is folded`() {
        // §3 picker edge case 6. A Japanese IME leaves a space behind between conversions, and a search
        // that found nothing because of it looks like a movement is missing from the catalogue.
        val byPattern = linkedMapOf(Pattern.SQUAT to listOf(squat))

        assertEquals(listOf(Pattern.SQUAT to listOf(squat)), pickerGroups(byPattern, "  スクワット "))
    }

    // ─── The clash, before it is committed ──────────────────────────────────────────────────────

    @Test
    fun `choosing a movement beside its own pattern warns here, not back in the builder`() {
        assertEquals(
            "腕立て伏せ と ディップス は続けて置かない方がよい",
            clash(draft("pushup", "squat"), index = 1, exerciseId = "dip"),
        )
    }

    @Test
    fun `adding at the end warns about the station it will follow`() {
        assertEquals(
            "腕立て伏せ と ディップス は続けて置かない方がよい",
            clash(draft("squat", "pushup"), index = null, exerciseId = "dip"),
        )
    }

    @Test
    fun `the round-wrap pair warns too`() {
        // The list comes back around, so the last station is adjacent to the first — the pair everybody
        // misses, and the one §3 edge case 3 adds to design §6.
        assertEquals(
            "ディップス と 腕立て伏せ は続けて置かない方がよい",
            clash(draft("pushup", "squat", "squat", rounds = 3), index = 2, exerciseId = "dip"),
        )
    }

    @Test
    fun `a choice with no neighbouring pattern says nothing`() {
        assertNull(clash(draft("pushup", "squat"), index = 1, exerciseId = "squat"))
        assertNull(clash(draft("pushup"), index = null, exerciseId = "squat"))
    }

    @Test
    fun `a clash elsewhere in the routine is the builder's line, not this page's`() {
        // Two pushes at the top of the list are a clash the builder is already drawing; repeating it
        // under a squat the user is choosing would be a warning about something they did not do.
        assertNull(clash(draft("pushup", "dip", "squat"), index = 2, exerciseId = "squat"))
    }

    @Test
    fun `an index the draft does not have changes nothing`() {
        assertNull(clash(draft("pushup"), index = 7, exerciseId = "dip"))
    }

    // ─── What comes back ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a station carries the one number its measure permits`() {
        // The schema's CHECK refuses anything else: 限界まで with a rep count is a contradiction.
        assertEquals(
            StationDraft("pushup", Measure.REPS, 20, null),
            stationOf("pushup", Measure.REPS, reps = 20, seconds = 30),
        )
        assertEquals(
            StationDraft("plank", Measure.DURATION, null, 30),
            stationOf("plank", Measure.DURATION, reps = 20, seconds = 30),
        )
        assertEquals(
            StationDraft("run", Measure.MAX_EFFORT, null, null),
            stationOf("run", Measure.MAX_EFFORT, reps = 20, seconds = 30),
        )
    }

    @Test
    fun `editing a station keeps the note the routine had no other place to put`() {
        // `applyStation` REPLACES the station, so a note this page does not hand back is destroyed —
        // by opening the station and pressing 保存 with nothing changed. `BuiltInCatalog` gives マーフ's
        // two run legs `note = "一マイル"` and says outright that "there is no note column on a version,
        // and the station is the only place the routine has to say it".
        val existing = StationDraft("run", Measure.MAX_EFFORT, null, null, "一マイル")

        assertEquals(
            existing,
            stationOf("run", Measure.MAX_EFFORT, reps = 20, seconds = 30, note = carriedNote(existing, "run")),
        )
        // And the draft is therefore not made dirty by a round trip that changed nothing —
        // `structuralHash` includes the note, so losing it raises the discard prompt on the way out.
        assertEquals(existing.note, carriedNote(existing, "run"))
    }

    @Test
    fun `a note does not survive swapping the movement it was about`() {
        // 一マイル is a fact about running a mile. Carrying it onto a burpee would be worse than losing
        // it: the routine would then carry a sentence that is false.
        val existing = StationDraft("run", Measure.MAX_EFFORT, null, null, "一マイル")

        assertNull(carriedNote(existing, "burpee"))
        assertEquals(
            StationDraft("burpee", Measure.REPS, 20, null),
            stationOf("burpee", Measure.REPS, reps = 20, seconds = 30, note = carriedNote(existing, "burpee")),
        )
    }

    @Test
    fun `＋ 加える carries no note at all`() {
        // There is no station to take one from, and this page cannot author one.
        assertNull(carriedNote(null, "pushup"))
    }

    // ─── The measure the page opens on ──────────────────────────────────────────────────────────

    @Test
    fun `an edit opens on a measure the engine will actually accept`() {
        // `migrateDraft` does not rewrite station measures, so a routine with a 秒数 station switched
        // to 完走 opened this page with a chip that was greyed *and* 選択中 at once, the wheel for a
        // measure this engine refuses, and a live 保存 that re-committed it. The correction existed
        // only in `onSelect`, which an EditMode open never runs.
        assertEquals(Measure.REPS, seededMeasure(Engine.FOR_TIME, Measure.DURATION))
        assertEquals(Measure.REPS, seededMeasure(Engine.EMOM, Measure.MAX_EFFORT))
        // Where the engine allows it, the station's own measure is kept.
        assertEquals(Measure.DURATION, seededMeasure(Engine.INTERVAL_CIRCUIT, Measure.DURATION))
        assertEquals(Measure.MAX_EFFORT, seededMeasure(Engine.FOR_TIME, Measure.MAX_EFFORT))
        // ＋ 加える has nothing to correct and lands on the first allowed chip — 回数 for every engine.
        assertEquals(Measure.REPS, seededMeasure(Engine.EMOM, null))
    }
}
