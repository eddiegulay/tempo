package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.i18n.StringsEn
import io.eddiegulay.tempo.i18n.StringsJa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The builder without a builder: everything `GYM.LIBRARY.BUILDER` and `GYM.LIBRARY.STATION_PICKER`
 * decide, decided in functions a JVM test can call.
 *
 * Three of these carry a cost that is not proportional to their size, and they are why the file is as
 * long as it is:
 *
 * 1. **[reorderShift] is the drag-reorder library.** `CONTRIBUTING.md` forbids new dependencies, so
 *    there is no `reorderable` and there will not be one — the page translates rows by row-heights and
 *    asks this function which way each one goes. It is tested exhaustively rather than illustratively,
 *    because a wrong answer is a row that visibly jumps under the user's finger and there is no other
 *    test that would catch it.
 * 2. **[isDirty] is the discard prompt's only input**, so a false negative *silently discards the
 *    user's work*. Every field is checked, including the two changes that must read **clean** — a
 *    reorder dragged back to where it started, and a rename typed back to the original name — because
 *    an implementation that tracks "was anything touched" rather than "is anything different" prompts
 *    on those and teaches the user to dismiss the prompt without reading it.
 * 3. **[structuralHash] decides what counts as a new shape**, and it is wrong in both directions.
 *    Too sensitive and every rename forks a version; too blunt and an April edit reinterprets a March
 *    session, which is the trap `00-plan.md` §2 row 10 exists to close.
 */
class BuilderDraftTest {

    // ─── moveItem ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `moving down puts the row after the one it was dropped on`() {
        assertEquals(listOf("b", "c", "a", "d"), moveItem(listOf("a", "b", "c", "d"), from = 0, to = 2))
    }

    @Test
    fun `moving up puts the row before the one it was dropped on`() {
        assertEquals(listOf("a", "d", "b", "c"), moveItem(listOf("a", "b", "c", "d"), from = 3, to = 1))
    }

    @Test
    fun `a drop where it started changes nothing`() {
        val list = listOf("a", "b", "c")
        assertEquals(list, moveItem(list, from = 1, to = 1))
    }

    @Test
    fun `both ends survive the round trip`() {
        assertEquals(listOf("b", "c", "a"), moveItem(listOf("a", "b", "c"), from = 0, to = 2))
        assertEquals(listOf("c", "a", "b"), moveItem(listOf("a", "b", "c"), from = 2, to = 0))
    }

    @Test
    fun `an out-of-range index returns the list untouched`() {
        // A drag released past the end of the list, or one that outlived the row it started on. The
        // page has already been rotated or recomposed at that point; losing a station to a
        // `IndexOutOfBoundsException` would be the drag deleting work.
        val list = listOf("a", "b", "c")
        assertEquals(list, moveItem(list, from = 0, to = 3))
        assertEquals(list, moveItem(list, from = -1, to = 1))
        assertEquals(list, moveItem(list, from = 5, to = 0))
        assertEquals(emptyList<String>(), moveItem(emptyList<String>(), from = 0, to = 0))
    }

    // ─── reorderShift ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `dragging down lifts every row it passes, and only those`() {
        // from 1 to 3, over a list of five: rows 2 and 3 move up one, rows 0 and 4 stay, and the
        // dragged row itself is 0 because it is following the finger, not the layout.
        val shifts = (0..4).map { reorderShift(it, from = 1, to = 3) }
        assertEquals(listOf(0, 0, -1, -1, 0), shifts)
    }

    @Test
    fun `dragging up pushes every row it passes down one`() {
        val shifts = (0..4).map { reorderShift(it, from = 3, to = 1) }
        assertEquals(listOf(0, 1, 1, 0, 0), shifts)
    }

    @Test
    fun `a drag that has not left its slot shifts nothing`() {
        assertEquals(listOf(0, 0, 0, 0), (0..3).map { reorderShift(it, from = 2, to = 2) })
    }

    @Test
    fun `a drag from the top to the bottom shifts every other row`() {
        assertEquals(listOf(0, -1, -1, -1), (0..3).map { reorderShift(it, from = 0, to = 3) })
    }

    @Test
    fun `a drag from the bottom to the top shifts every other row`() {
        assertEquals(listOf(1, 1, 1, 0), (0..3).map { reorderShift(it, from = 3, to = 0) })
    }

    @Test
    fun `a one-step drag moves exactly one neighbour`() {
        assertEquals(listOf(0, -1, 0), (0..2).map { reorderShift(it, from = 0, to = 1) })
        assertEquals(listOf(1, 0, 0), (0..2).map { reorderShift(it, from = 1, to = 0) })
    }

    @Test
    fun `a negative index shifts nothing`() {
        // Nothing is known about a row that does not exist, so the honest translation is zero. The
        // page computes `to` from a pixel offset, and a fast fling produces a negative one before the
        // caller clamps it.
        assertEquals(0, reorderShift(-1, from = 0, to = 2))
        assertEquals(0, reorderShift(1, from = -1, to = 2))
        assertEquals(0, reorderShift(1, from = 0, to = -2))
    }

    @Test
    fun `the shifts agree with the list moveItem would produce`() {
        // The two functions are the same operation seen during and after the drag, and the whole
        // illusion depends on them agreeing: the row that slid up one must be the row that ends up
        // one earlier. This is that cross-check, over every pair of indices in a five-row list.
        val list = listOf("a", "b", "c", "d", "e")
        for (from in list.indices) {
            for (to in list.indices) {
                val moved = moveItem(list, from, to)
                for (index in list.indices) {
                    if (index == from) continue
                    val expected = moved.indexOf(list[index]) - index
                    assertEquals(
                        "row $index while dragging $from to $to",
                        expected,
                        reorderShift(index, from, to),
                    )
                }
            }
        }
    }

    // ─── structuralHash ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `two drafts differing only in name have the same structure`() {
        // A rename is not a new shape. If it were, every typo in the name field while editing a
        // routine with history would ask the user to accept a new version of a routine they did not
        // restructure — and the 「これまでの六回の記録はそのまま残ります」 line, which is keyed on this
        // hash, would appear on a pure rename.
        val a = circuit()
        assertEquals(structuralHash(a), structuralHash(a.copy(name = "夜の五分")))
    }

    @Test
    fun `two drafts differing only in station order do not`() {
        // Order is the intervention in an alternating circuit. Blunt the hash here and an April edit
        // that only reorders is not recorded as a new version, so a March session — pinned to the
        // version id — is re-read against April's order (`00-plan.md` §2 row 10).
        val a = circuit()
        val reordered = a.copy(stations = moveItem(a.stations, from = 0, to = 2))
        assertNotEquals(structuralHash(a), structuralHash(reordered))
    }

    @Test
    fun `every field a version stores moves the hash`() {
        val base = circuit()
        val mutations = mapOf(
            "engine" to base.copy(engine = Engine.AMRAP),
            "a station's exercise" to base.copy(stations = base.stations.map { it.copy(exerciseId = "burpee") }),
            "a station's measure" to base.copy(
                stations = base.stations.map { it.copy(measure = Measure.MAX_EFFORT, reps = null) },
            ),
            "a rep count" to base.copy(stations = base.stations.map { it.copy(reps = 11) }),
            "a duration" to base.copy(
                stations = base.stations.map { it.copy(measure = Measure.DURATION, reps = null, seconds = 30) },
            ),
            "a note" to base.copy(stations = base.stations.map { it.copy(note = "一マイル") }),
            "a station added" to base.copy(stations = base.stations + reps("burpee", 5)),
            "a station removed" to base.copy(stations = base.stations.dropLast(1)),
            "rounds" to base.copy(rounds = 4),
            "the time cap" to base.copy(timeCapSeconds = 1200),
            "the interval" to base.copy(intervalSeconds = 60),
            "the rest between stations" to base.copy(restBetweenStations = 20),
            "the rest between rounds" to base.copy(restBetweenRounds = 90),
            "the prepare" to base.copy(prepareSeconds = 10),
        )
        mutations.forEach { (what, changed) ->
            assertNotEquals(what, structuralHash(base), structuralHash(changed))
        }
    }

    @Test
    fun `the same shape hashes the same however it was assembled`() {
        // Two independently constructed equal drafts, because this value is compared across a
        // process boundary in the store and a hash that folded in an identity would be unstable.
        assertEquals(structuralHash(circuit()), structuralHash(circuit()))
        assertEquals(structuralHash(circuit()), structuralHash(circuit().copy(routineId = "r_other")))
    }

    // ─── isDirty ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an untouched edit is clean`() {
        val original = circuit()
        assertFalse(isDirty(original, original))
        assertFalse(isDirty(original.copy(), original))
    }

    @Test
    fun `every field the user can change makes the draft dirty`() {
        // The list is the same one `structuralHash` is pinned against, plus the name — which the
        // hash deliberately ignores and `isDirty` therefore has to check itself. A field missing from
        // one of these two lists is a field whose edit is discarded without a prompt.
        val original = circuit()
        val mutations = mapOf(
            "the name" to original.copy(name = "夜の五分"),
            "the engine" to original.copy(engine = Engine.AMRAP),
            "a station's exercise" to original.copy(
                stations = original.stations.map { it.copy(exerciseId = "burpee") },
            ),
            "a station's measure" to original.copy(
                stations = original.stations.map { it.copy(measure = Measure.MAX_EFFORT, reps = null) },
            ),
            "a rep count" to original.copy(stations = original.stations.map { it.copy(reps = 11) }),
            "a note" to original.copy(stations = original.stations.map { it.copy(note = "一マイル") }),
            "a station added" to original.copy(stations = original.stations + reps("burpee", 5)),
            "a station removed" to original.copy(stations = original.stations.dropLast(1)),
            "the order" to original.copy(stations = moveItem(original.stations, from = 0, to = 2)),
            "the rounds" to original.copy(rounds = 4),
            "the time cap" to original.copy(timeCapSeconds = 1200),
            "the interval" to original.copy(intervalSeconds = 60),
            "the rest between stations" to original.copy(restBetweenStations = 20),
            "the rest between rounds" to original.copy(restBetweenRounds = 90),
            "the prepare" to original.copy(prepareSeconds = 10),
        )
        mutations.forEach { (what, changed) -> assertTrue(what, isDirty(changed, original)) }
    }

    @Test
    fun `a reorder dragged back to where it started is clean`() {
        // The single most likely false positive, and it is a false positive that trains the user to
        // dismiss the prompt unread — after which the next one, which is real, is dismissed too.
        val original = circuit()
        val thereAndBack = original.copy(
            stations = moveItem(moveItem(original.stations, from = 0, to = 2), from = 2, to = 0),
        )
        assertEquals(original.stations, thereAndBack.stations)
        assertFalse(isDirty(thereAndBack, original))
    }

    @Test
    fun `a name typed back to the original is clean`() {
        val original = circuit()
        val retyped = original.copy(name = "").copy(name = original.name)
        assertFalse(isDirty(retyped, original))
    }

    @Test
    fun `identity is not content`() {
        // `saveRoutine` may hand back a different routine id than the one passed in — editing a
        // built-in is a copy-on-write. That is not an edit the user made and must not raise a prompt.
        val original = circuit()
        assertFalse(isDirty(original.copy(routineId = "r_copy"), original))
    }

    @Test
    fun `a new routine is dirty once it has a name or a station`() {
        // §3: "a new routine is dirty as soon as the name is non-blank or a station exists".
        val blank = empty()
        assertFalse(isDirty(blank, null))
        assertTrue(isDirty(blank.copy(name = "朝"), null))
        assertTrue(isDirty(blank.copy(stations = listOf(reps("pushup", 10))), null))
    }

    @Test
    fun `a name of only spaces is not a name`() {
        // The same blankness test 保存 uses. A back press over a field holding a space should not
        // raise a dialog about work that could not have been saved.
        assertFalse(isDirty(empty().copy(name = "  "), null))
    }

    // ─── canSave ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `saving needs a name and at least one station`() {
        assertTrue(canSave(circuit()))
        assertFalse(canSave(circuit().copy(name = "")))
        assertFalse(canSave(circuit().copy(name = "  ")))
        assertFalse(canSave(circuit().copy(stations = emptyList())))
    }

    @Test
    fun `saving is blocked while a save is in flight or the routine has not loaded`() {
        assertFalse(canSave(circuit(), saving = true))
        assertFalse(canSave(circuit(), routineLoaded = false))
    }

    @Test
    fun `a warning never blocks a save`() {
        // Design §6: "The builder warns, it does not block." §3 says it again about `canSave`, and it
        // is the kind of rule that gets quietly reversed by someone adding one `&&`.
        val clashing = circuit().copy(
            stations = listOf(reps("pushup", 10), reps("dip", 10)),
        )
        assertTrue(adjacentPatternClashes(clashing) { Pattern.H_PUSH }.isNotEmpty())
        assertTrue(canSave(clashing))
    }

    @Test
    fun `the station cap is twenty-four`() {
        assertEquals(24, STATION_CAP)
        assertTrue(canAddStation(circuit()))
        assertFalse(canAddStation(circuit().copy(stations = List(STATION_CAP) { reps("pushup", 10) })))
    }

    // ─── migrateDraft ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `migrating to the engine it already has changes nothing and says nothing`() {
        val draft = circuit()
        val migrated = migrateDraft(draft, Engine.INTERVAL_CIRCUIT, StringsJa)
        assertEquals(draft, migrated.draft)
        assertTrue(migrated.notices.isEmpty())
    }

    @Test
    fun `巡回 to 時間内 swaps the round count for a twenty-minute cap`() {
        // §3 edge case 5, verbatim: "巡回→時間内 replaces 巡数 with 制限時間 (default 二十分)".
        val migrated = migrateDraft(circuit(), Engine.AMRAP, StringsJa)
        assertEquals(Engine.AMRAP, migrated.draft.engine)
        assertNull(migrated.draft.rounds)
        assertEquals(20 * 60, migrated.draft.timeCapSeconds)
    }

    @Test
    fun `a cap the user already set survives the migration`() {
        val had = circuit().copy(timeCapSeconds = 600)
        assertEquals(600, migrateDraft(had, Engine.AMRAP, StringsJa).draft.timeCapSeconds)
    }

    @Test
    fun `leaving 時間内 drops the cap, because no other engine has a row for it`() {
        val amrap = migrateDraft(circuit(), Engine.AMRAP, StringsJa).draft
        val back = migrateDraft(amrap, Engine.INTERVAL_CIRCUIT, StringsJa).draft
        assertNull(back.timeCapSeconds)
        assertEquals(1, back.rounds)
    }

    @Test
    fun `段階 clears the rests and the round count and says one station will be used`() {
        val migrated = migrateDraft(circuit(), Engine.FIXED_SETS, StringsJa)
        assertEquals(0, migrated.draft.restBetweenStations)
        assertEquals(0, migrated.draft.restBetweenRounds)
        assertNull(migrated.draft.rounds)
        assertEquals(listOf("段階では一種目だけ使われます"), migrated.notices)
    }

    @Test
    fun `段階 keeps the extra stations rather than deleting them`() {
        // §3: "extras kept but greyed". Deleting three stations because a chip was tapped is not
        // something a 元に戻す exists for here, and the notice is what makes keeping them honest.
        val migrated = migrateDraft(circuit(), Engine.FIXED_SETS, StringsJa)
        assertEquals(circuit().stations, migrated.draft.stations)
    }

    @Test
    fun `段階 says nothing about extras when there is only one station`() {
        // "One per lossy field" — nothing is lost, so there is nothing to say. A notice that always
        // appears is chrome, and the user stops reading the ones that mean something.
        val single = circuit().copy(stations = listOf(reps("pullup", 10)))
        assertTrue(migrateDraft(single, Engine.FIXED_SETS, StringsJa).notices.isEmpty())
    }

    @Test
    fun `毎分 forces the between-station rest to zero and says so`() {
        val migrated = migrateDraft(circuit(), Engine.EMOM, StringsJa)
        assertEquals(0, migrated.draft.restBetweenStations)
        assertEquals(listOf("毎分では種目の間の休息はありません"), migrated.notices)
    }

    @Test
    fun `毎分 says nothing when there was no rest to lose`() {
        val restless = circuit().copy(restBetweenStations = 0)
        assertTrue(migrateDraft(restless, Engine.EMOM, StringsJa).notices.isEmpty())
    }

    @Test
    fun `毎分 gets a minute to work in`() {
        // 毎分 *is* the window: an EMOM with no interval estimates nothing and compiles to nothing.
        // Sixty seconds is the engine's own name, not a chosen number.
        assertEquals(60, migrateDraft(circuit(), Engine.EMOM, StringsJa).draft.intervalSeconds)
        assertEquals(60, migrateDraft(circuit(), Engine.EMOM_ASCENDING, StringsJa).draft.intervalSeconds)
    }

    @Test
    fun `毎分増 runs to failure, so it carries no round count`() {
        assertNull(migrateDraft(circuit(), Engine.EMOM_ASCENDING, StringsJa).draft.rounds)
    }

    @Test
    fun `leaving 毎分 drops the interval it can no longer state`() {
        val emom = migrateDraft(circuit(), Engine.EMOM, StringsJa).draft
        assertNull(migrateDraft(emom, Engine.INTERVAL_CIRCUIT, StringsJa).draft.intervalSeconds)
    }

    @Test
    fun `an engine that counts rounds never lands on a null count`() {
        val amrap = migrateDraft(circuit(), Engine.AMRAP, StringsJa).draft
        assertNull(amrap.rounds)
        assertEquals(1, migrateDraft(amrap, Engine.EMOM, StringsJa).draft.rounds)
        assertEquals(1, migrateDraft(amrap, Engine.FOR_TIME_WITH_REST, StringsJa).draft.rounds)
        assertEquals(1, migrateDraft(amrap, Engine.FOR_TIME, StringsJa).draft.rounds)
    }

    @Test
    fun `a migration never touches the name or the stations' prescriptions`() {
        val migrated = migrateDraft(circuit(), Engine.AMRAP, StringsJa).draft
        assertEquals(circuit().name, migrated.name)
        assertEquals(circuit().stations, migrated.stations)
    }

    // ─── The station picker ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the measures read 回数 秒数 限界まで, in that order`() {
        assertEquals(
            listOf("回数", "秒数", "限界まで"),
            allowedMeasures(Engine.INTERVAL_CIRCUIT, StringsJa).map { it.measure.label(StringsJa) },
        )
        assertTrue(allowedMeasures(Engine.INTERVAL_CIRCUIT, StringsJa).all { it.enabled })
        assertTrue(allowedMeasures(Engine.INTERVAL_CIRCUIT, StringsJa).all { it.reason == null })
    }

    @Test
    fun `毎分 cannot take 限界まで, and says why`() {
        // §3's picker edge case 2: those engines are *defined* by a fixed rep count. The chip is
        // rendered inert with a reason rather than removed, so the user learns the rule.
        listOf(Engine.EMOM, Engine.EMOM_ASCENDING).forEach { engine ->
            val maxEffort = allowedMeasures(engine, StringsJa).single { it.measure == Measure.MAX_EFFORT }
            assertFalse(engine.name, maxEffort.enabled)
            assertEquals("この方式では使えません", maxEffort.reason)
            assertTrue(engine.name, allowedMeasures(engine, StringsJa).single { it.measure == Measure.DURATION }.enabled)
        }
    }

    @Test
    fun `完走 cannot take 秒数, and says why`() {
        val duration = allowedMeasures(Engine.FOR_TIME, StringsJa).single { it.measure == Measure.DURATION }
        assertFalse(duration.enabled)
        assertEquals("この方式では使えません", duration.reason)
    }

    @Test
    fun `完走 keeps 限界まで, because マーフ's runs need it`() {
        // A `DURATION` with a null second count is refused by the schema's CHECK, so a one-mile run
        // is a `MAX_EFFORT` with a note (`DECISIONS.md` §Q15). Disabling it on FOR_TIME would make
        // the shipped routine unbuildable in the builder that is supposed to be able to author it.
        assertTrue(allowedMeasures(Engine.FOR_TIME, StringsJa).single { it.measure == Measure.MAX_EFFORT }.enabled)
    }

    @Test
    fun `the defaults are twenty reps and thirty seconds`() {
        assertEquals(Prescription(reps = 20, seconds = null), defaultPrescription(Measure.REPS))
        assertEquals(Prescription(reps = null, seconds = 30), defaultPrescription(Measure.DURATION))
        assertEquals(Prescription(reps = null, seconds = null), defaultPrescription(Measure.MAX_EFFORT))
    }

    @Test
    fun `a number never crosses between 回数 and 秒数`() {
        // §3's picker edge case 1: 20 reps is not 20 seconds. Each measure remembers its own last
        // value, and switching back and forth must not carry one into the other.
        assertEquals(
            Prescription(reps = null, seconds = 45),
            defaultPrescription(Measure.DURATION, lastReps = 20, lastSeconds = 45),
        )
        assertEquals(
            Prescription(reps = 12, seconds = null),
            defaultPrescription(Measure.REPS, lastReps = 12, lastSeconds = 45),
        )
        assertEquals(
            Prescription(reps = null, seconds = 30),
            defaultPrescription(Measure.DURATION, lastReps = 20, lastSeconds = null),
        )
    }

    // ─── The wheels ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the station rest wheel runs to two minutes in fives and starts at なし`() {
        val options = restOptions(RestSlot.BETWEEN_STATIONS, StringsJa)
        assertEquals((0..120 step 5).toList(), options.map { it.value })
        assertEquals(WheelOption(0, "なし"), options.first())
        assertEquals(WheelOption(120, "百二十秒"), options.last())
    }

    @Test
    fun `the round rest wheel runs to five minutes in fifteens`() {
        val options = restOptions(RestSlot.BETWEEN_ROUNDS, StringsJa)
        assertEquals((0..300 step 15).toList(), options.map { it.value })
        assertEquals(WheelOption(0, "なし"), options.first())
        assertEquals(WheelOption(300, "三百秒"), options.last())
    }

    @Test
    fun `a rest reads as the seconds it was set to, never as minutes`() {
        // `DECISIONS.md` §Q10, and this is the surface the rule was written about: the wheel says
        // 六十秒, so the settings row and the detail page must too. `durationKanji` would say 一分.
        val label = { seconds: Int ->
            restOptions(RestSlot.BETWEEN_ROUNDS, StringsJa).single { it.value == seconds }.label
        }
        assertEquals("六十秒", label(60))
        assertEquals("九十秒", label(90))
        assertEquals("百八十秒", label(180))
    }

    @Test
    fun `the rep wheel runs one to a hundred and counts in 回`() {
        val options = repOptions(StringsJa)
        assertEquals((1..100).toList(), options.map { it.value })
        assertEquals(WheelOption(1, "一回"), options.first())
        assertEquals(WheelOption(20, "二十回"), options[19])
        assertEquals(WheelOption(100, "百回"), options.last())
    }

    @Test
    fun `the seconds wheel runs five to three hundred in fives, and never says なし`() {
        // A station cannot be zero seconds long — 限界まで is what an open-ended one is — so the
        // wheel starts at five rather than borrowing the rest wheel's なし.
        val options = secondOptions(StringsJa)
        assertEquals((5..300 step 5).toList(), options.map { it.value })
        assertEquals(WheelOption(5, "五秒"), options.first())
        assertEquals(WheelOption(30, "三十秒"), options[5])
        assertEquals(WheelOption(300, "三百秒"), options.last())
    }

    @Test
    fun `the round wheel runs one to twenty and counts in 巡`() {
        val options = roundOptions(StringsJa)
        assertEquals((1..20).toList(), options.map { it.value })
        assertEquals(WheelOption(1, "一巡"), options.first())
        assertEquals(WheelOption(20, "二十巡"), options.last())
    }

    @Test
    fun `every wheel offers the same values in both languages`() {
        // `DECISIONS.md` §Q21 is a rule about the **value** set: a wheel opened on a value it cannot
        // represent silently rewrites it, and `mergedWheelOptions` repairs that by comparing on
        // `value`. Only the labels are localised, so a range that drifted by language would make the
        // repair itself language-dependent — and would move which built-ins are safe to open.
        val ja = listOf(
            restOptions(RestSlot.BETWEEN_STATIONS, StringsJa),
            restOptions(RestSlot.BETWEEN_ROUNDS, StringsJa),
            repOptions(StringsJa),
            secondOptions(StringsJa),
            roundOptions(StringsJa),
        )
        val en = listOf(
            restOptions(RestSlot.BETWEEN_STATIONS, StringsEn),
            restOptions(RestSlot.BETWEEN_ROUNDS, StringsEn),
            repOptions(StringsEn),
            secondOptions(StringsEn),
            roundOptions(StringsEn),
        )

        assertEquals(ja.map { list -> list.map { it.value } }, en.map { list -> list.map { it.value } })
        // …and the labels genuinely moved, so the assertion above is not passing by the wheels having
        // stayed Japanese.
        for ((j, e) in ja.zip(en)) assertNotEquals(j.map { it.label }, e.map { it.label })
    }

    @Test
    fun `the English wheels read as durations and counts, never as kanji`() {
        // §L7: §Q10's chosen-versus-measured split is carried entirely by orthography, and English has
        // one — so the chosen form collapses onto `fmt.duration`, exactly as
        // `GymSettingsCopy.settingsSecondsLabel` rules for the row this wheel sets. Keeping
        // `fmt.seconds` would have printed `300s` at the bottom of the round-rest wheel.
        val rounds = restOptions(RestSlot.BETWEEN_ROUNDS, StringsEn).associate { it.value to it.label }
        assertEquals("None", rounds.getValue(0))
        assertEquals("1m", rounds.getValue(60))
        assertEquals("1m 30s", rounds.getValue(90))
        assertEquals("5m", rounds.getValue(300))

        // A count is a counter and takes a plural rather than a duration.
        assertEquals("1 rep", repOptions(StringsEn).first().label)
        assertEquals("100 reps", repOptions(StringsEn).last().label)
        assertEquals("1 round", roundOptions(StringsEn).first().label)
        assertEquals("20 rounds", roundOptions(StringsEn).last().label)

        // 秒数's wheel still has no zero row, so it never says None in either language.
        assertFalse(secondOptions(StringsEn).any { it.label == StringsEn.gymShared.restNone })
    }

    @Test
    fun `every default the builder hands a wheel is a value that wheel offers`() {
        // A wheel scrolled to a value it does not contain lands on its first row instead — the
        // `TempoWheelColumn` seeds its position from `indexOf`, which is −1 for a missing value. That
        // would silently turn a 二十回 default into 一回.
        assertTrue(repOptions(StringsJa).any { it.value == defaultPrescription(Measure.REPS).reps })
        assertTrue(secondOptions(StringsJa).any { it.value == defaultPrescription(Measure.DURATION).seconds })
        assertTrue(restOptions(RestSlot.BETWEEN_STATIONS, StringsJa).any { it.value == 0 })
        assertTrue(restOptions(RestSlot.BETWEEN_ROUNDS, StringsJa).any { it.value == 60 })
        assertTrue(roundOptions(StringsJa).any { it.value == 1 })
    }
}

// ─── fixtures ───────────────────────────────────────────────────────────────────────────────────

private fun reps(id: String, count: Int) = StationDraft(id, Measure.REPS, count, null)

/** A three-station 巡回 with every field populated, so a mutation of any one of them is visible. */
private fun circuit() = RoutineDraft(
    routineId = "r_morning",
    name = "朝の五分",
    engine = Engine.INTERVAL_CIRCUIT,
    stations = listOf(reps("pushup", 10), reps("squat", 15), reps("pullup", 5)),
    rounds = 3,
    timeCapSeconds = null,
    intervalSeconds = null,
    restBetweenStations = 15,
    restBetweenRounds = 60,
    prepareSeconds = 5,
)

/** What 作る opens with: nothing chosen, nothing typed. */
private fun empty() = RoutineDraft(
    routineId = null,
    name = "",
    engine = Engine.INTERVAL_CIRCUIT,
    stations = emptyList(),
    rounds = 1,
    timeCapSeconds = null,
    intervalSeconds = null,
    restBetweenStations = 0,
    restBetweenRounds = 0,
    prepareSeconds = 5,
)
