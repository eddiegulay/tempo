package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.i18n.StringsJa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The builder's one piece of coaching, and the adjacency everybody forgets.
 *
 * Design §6 states the rule for the list as written — two adjacent stations sharing a pattern means
 * station two starts on an already-fatigued muscle — and `04-library-records.md` §3's edge case 3 adds
 * the case §6 misses: **when the routine repeats, the last station is adjacent to the first.** A
 * circuit of 腕立て伏せ ・ スクワット ・ ディップス reads clean straight down the page and puts two
 * pushes back to back at every round boundary but the last. That pair is the one this file exists for.
 */
class PatternWarningTest {

    @Test
    fun `two adjacent pushes clash`() {
        assertEquals(
            listOf(PatternClash(0, 1, Pattern.H_PUSH)),
            adjacentPatternClashes(listOf(Pattern.H_PUSH, Pattern.H_PUSH, Pattern.SQUAT), wraps = false),
        )
    }

    @Test
    fun `an alternating circuit clashes nowhere`() {
        assertTrue(
            adjacentPatternClashes(
                listOf(Pattern.H_PUSH, Pattern.SQUAT, Pattern.V_PULL, Pattern.CORE),
                wraps = false,
            ).isEmpty(),
        )
    }

    @Test
    fun `the round-wrap pair is reported when the routine repeats`() {
        // THE case §6 misses. 押す / しゃがむ / 押す reads clean top to bottom and puts two pushes
        // together at every round boundary — which is most of the routine, not an edge of it.
        val patterns = listOf(Pattern.H_PUSH, Pattern.SQUAT, Pattern.H_PUSH)
        assertEquals(
            listOf(PatternClash(2, 0, Pattern.H_PUSH)),
            adjacentPatternClashes(patterns, wraps = true),
        )
    }

    @Test
    fun `a routine that runs once does not wrap`() {
        // One round is a straight line: the last station is followed by the finish, not by the first.
        val patterns = listOf(Pattern.H_PUSH, Pattern.SQUAT, Pattern.H_PUSH)
        assertTrue(adjacentPatternClashes(patterns, wraps = false).isEmpty())
    }

    @Test
    fun `the wrap pair is reported in loop order, so the warning sits under the station it harms`() {
        // (last, first), not (first, last). The station that starts fatigued is the *first*, and §3's
        // layout insets the line under the second of the pair — which is index 0 for a wrap.
        val clash = adjacentPatternClashes(
            listOf(Pattern.CORE, Pattern.SQUAT, Pattern.CORE),
            wraps = true,
        ).single()
        assertEquals(2, clash.firstIndex)
        assertEquals(0, clash.secondIndex)
    }

    @Test
    fun `a one-station routine never clashes with itself`() {
        // タバタ is eight rounds of one movement and 段階 is one movement by construction. Warning
        // that a routine is adjacent to itself would fire on two of the three shipped built-ins and
        // say nothing the user did not choose deliberately.
        assertTrue(adjacentPatternClashes(listOf(Pattern.SQUAT), wraps = true).isEmpty())
        assertTrue(adjacentPatternClashes(listOf(Pattern.SQUAT), wraps = false).isEmpty())
    }

    @Test
    fun `two stations that repeat produce one warning, not the same pair twice`() {
        // A B A B: the pair (0,1) and the wrap (1,0) are the same two rows in the same routine, and
        // two lines saying 腕立て伏せ と ディップス and ディップス と 腕立て伏せ under one another read
        // as two problems. One adjacency of a pair is one warning.
        assertEquals(
            listOf(PatternClash(0, 1, Pattern.H_PUSH)),
            adjacentPatternClashes(listOf(Pattern.H_PUSH, Pattern.H_PUSH), wraps = true),
        )
    }

    @Test
    fun `an unresolvable station clashes with nothing`() {
        // Its pattern is unknown, and a warning about a movement the app cannot name is a warning the
        // user cannot act on. The detail page's 不明な種目 is where a missing exercise is reported.
        assertTrue(
            adjacentPatternClashes(listOf(Pattern.H_PUSH, null, Pattern.H_PUSH), wraps = false).isEmpty(),
        )
    }

    @Test
    fun `every adjacent pair is checked, not only the first`() {
        val clashes = adjacentPatternClashes(
            listOf(Pattern.H_PUSH, Pattern.H_PUSH, Pattern.SQUAT, Pattern.SQUAT),
            wraps = false,
        )
        assertEquals(
            listOf(PatternClash(0, 1, Pattern.H_PUSH), PatternClash(2, 3, Pattern.SQUAT)),
            clashes,
        )
    }

    @Test
    fun `an empty routine has nothing to warn about`() {
        assertTrue(adjacentPatternClashes(emptyList(), wraps = true).isEmpty())
    }

    @Test
    fun `the warning is design §6's sentence, verbatim`() {
        assertEquals(
            "腕立て伏せ と ディップス は続けて置かない方がよい",
            clashCopy("腕立て伏せ", "ディップス", StringsJa),
        )
    }

    // ─── Which routines come back around ────────────────────────────────────────────────────────

    @Test
    fun `more than one round wraps and exactly one does not`() {
        assertFalse(routineWraps(Engine.INTERVAL_CIRCUIT, rounds = 1))
        assertTrue(routineWraps(Engine.INTERVAL_CIRCUIT, rounds = 2))
        assertTrue(routineWraps(Engine.EMOM, rounds = 30))
        assertFalse(routineWraps(Engine.FOR_TIME, rounds = 1))
    }

    @Test
    fun `an AMRAP wraps although its round count is unknown`() {
        // シンディ has `rounds = null` because the answer is 時間内で — as many as fit. It is the most
        // repeated shape the app has, and reading its null as "one round" would silence the warning
        // on exactly the engine that needs it most.
        assertTrue(routineWraps(Engine.AMRAP, rounds = null))
        assertTrue(routineWraps(Engine.EMOM_ASCENDING, rounds = null))
    }

    @Test
    fun `an unknown round count on a counted engine does not wrap`() {
        // A draft mid-edit whose 巡数 has not been set yet. One round is the honest floor, and a
        // warning that appears before the user has said the routine repeats would be the app
        // inventing the premise of its own advice.
        assertFalse(routineWraps(Engine.FOR_TIME_WITH_REST, rounds = null))
    }

    @Test
    fun `the draft form resolves patterns through the catalogue and wraps on its own engine`() {
        val patterns = mapOf("pushup" to Pattern.H_PUSH, "dip" to Pattern.H_PUSH, "squat" to Pattern.SQUAT)
        val draft = RoutineDraft(
            routineId = null,
            name = "朝の五分",
            engine = Engine.AMRAP,
            stations = listOf(station("pushup"), station("squat"), station("dip")),
            rounds = null,
            timeCapSeconds = 1200,
            intervalSeconds = null,
            restBetweenStations = 0,
            restBetweenRounds = 0,
            prepareSeconds = 5,
        )
        // 押す / しゃがむ / 押す inside an AMRAP: only the wrap pair clashes, and it does clash.
        assertEquals(
            listOf(PatternClash(2, 0, Pattern.H_PUSH)),
            adjacentPatternClashes(draft) { patterns[it] },
        )
    }
}

private fun station(id: String) = StationDraft(id, Measure.REPS, 10, null)
