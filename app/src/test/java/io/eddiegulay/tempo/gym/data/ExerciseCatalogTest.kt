package io.eddiegulay.tempo.gym.data

import io.eddiegulay.tempo.gym.Exercise
import io.eddiegulay.tempo.gym.ExerciseCatalog
import io.eddiegulay.tempo.gym.Pattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ExerciseCatalog] is the contract eight call sites read, and for a while all four of its bodies were
 * `TODO()`.
 *
 * That is a failure with no visible symptom until it runs: the backing [ExerciseCatalogSource] was
 * written, installed by `GymStore`'s constructor and tested, and only the delegation was missing — so
 * the code compiled, the store worked, and the station picker, the exercise index, the ladder block and
 * every `byId` in the timeline compiler threw `NotImplementedError` on first touch. These tests read
 * through the public object precisely because that is the surface that was empty; asserting on the
 * source object alone is what let it ship.
 */
class ExerciseCatalogTest {

    @Before
    fun install() {
        // The same list the store loads out of the `exercise` table, plus one retired row, which is
        // the distinction every list-shaped read here turns on.
        ExerciseCatalogSource.install(SeedCatalog.exercises.map { it.toExercise() } + retired)
    }

    @Test
    fun `all returns the seeded movements instead of throwing`() {
        val all = ExerciseCatalog.all()
        assertEquals(SeedCatalog.exercises.size, all.size)
        assertTrue(all.any { it.id == "pushup" })
        assertTrue(all.any { it.id == "burpee" })
    }

    @Test
    fun `a retired movement is excluded from the lists and still resolvable by id`() {
        // It stays in the table for its foreign keys: a session that performed it still has to name it.
        assertFalse(ExerciseCatalog.all().any { it.id == retired.id })
        assertEquals(retired, ExerciseCatalog.byId(retired.id))
    }

    @Test
    fun `byId resolves a seeded movement and is null for one the catalogue does not know`() {
        assertEquals("腕立て伏せ", ExerciseCatalog.byId("pushup")?.nameJa)
        // Null rather than a throw — the caller renders 不明な種目 (§C.1).
        assertNull(ExerciseCatalog.byId("no_such_movement"))
    }

    @Test
    fun `the push-up ladder reads easiest to hardest`() {
        val ladder = ExerciseCatalog.ladder("pushup").map { it.id }
        assertEquals(
            listOf(
                "wall_pushup",
                "incline_pushup",
                "knee_pushup",
                "pushup",
                "feet_elevated_pushup",
                "archer_pushup",
                "one_arm_pushup",
            ),
            ladder,
        )
    }

    @Test
    fun `a movement that is its own ladder returns empty, not a ladder of one`() {
        // What lets EXERCISE_DETAIL omit the 段階 block entirely rather than drawing a single rung.
        assertTrue(ExerciseCatalog.ladder("plank").isEmpty())
        assertTrue(ExerciseCatalog.ladder("run").isEmpty())
    }

    @Test
    fun `byPattern groups every live movement in the enum's own order`() {
        val grouped = ExerciseCatalog.byPattern()
        assertNotNull(grouped[Pattern.H_PUSH])
        assertEquals(ExerciseCatalog.all().size, grouped.values.sumOf { it.size })
        // Design §9's alternation, not the alphabet and not by count: the map must preserve the order
        // the enum declares, which is what the picker's headings render.
        assertEquals(
            Pattern.entries.filter { pattern -> grouped.containsKey(pattern) },
            grouped.keys.toList(),
        )
        assertFalse(grouped.values.any { members -> members.any { it.id == retired.id } })
    }

    private val retired = Exercise(
        id = "retired_movement",
        nameJa = "廃止された種目",
        nameEn = "Retired movement",
        pattern = Pattern.CORE,
        secondsPerRep = 2.0,
        difficulty = 1.0,
        isIsometric = false,
        cue = null,
        ladderId = null,
        catalogVersion = 1,
        archived = true,
    )

    private fun ExerciseSeed.toExercise() = Exercise(
        id = id,
        nameJa = nameJa,
        nameEn = nameEn,
        pattern = pattern,
        secondsPerRep = secondsPerRep,
        difficulty = difficulty,
        isIsometric = isIsometric,
        cue = cue,
        ladderId = ladderId,
        catalogVersion = catalogVersion,
        archived = false,
    )
}
