package io.eddiegulay.tempo.gym.data

import io.eddiegulay.tempo.gym.Exercise
import io.eddiegulay.tempo.gym.displayCue
import io.eddiegulay.tempo.gym.displayName
import io.eddiegulay.tempo.i18n.CatalogStrings
import io.eddiegulay.tempo.i18n.EnCatalog
import io.eddiegulay.tempo.i18n.JaCatalog
import io.eddiegulay.tempo.i18n.Lang
import io.eddiegulay.tempo.i18n.stringsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seeded catalogue is localised on the **read** side, and this is what keeps the two sides honest.
 *
 * `SeedCatalog` writes Japanese into the tables and never varies by locale — it cannot, because the
 * built-in routine pass is content-addressed over the routine's name and every station note, so a
 * locale-dependent seed would insert a new `routine_version` on every flip of the language toggle.
 * English is therefore not in the database at all (except `exercise.name_en`, which predates this
 * work); it is in `CatalogStrings`, keyed by the row's seed id, applied when the row is drawn.
 *
 * The cost of that design is a second copy of the Japanese, in `JaCatalog`. These tests are what make
 * the copy free: **every Japanese value is asserted against the literal the seeder actually writes**,
 * read out of `SeedCatalog` rather than restated here. A cue corrected in `BuiltInCatalog.kt` and not
 * in `CatalogStrings.kt` fails on the JVM, not on a user's screen.
 *
 * The English half is asserted for *coverage and absence of Japanese* rather than for wording. A
 * translation nobody wrote is the failure this catches; a translation somebody wrote badly is a review.
 */
class SeedTranslationTest {

    // ─── Japanese must be a transcription, not a second authoring ───────────────────────────────

    @Test
    fun `every seeded cue is transcribed exactly`() {
        val cued = SeedCatalog.exercises.filter { it.cue != null }
        // Anti-vacuity: the assertion below is a loop, and an empty loop passes.
        assertEquals("§F.1 gives 17 of the 23 movements a cue", 17, cued.size)
        cued.forEach { seed ->
            assertEquals(
                "JaCatalog disagrees with the seeded cue for ${seed.id}",
                seed.cue,
                JaCatalog.exerciseCue(seed.id),
            )
        }
    }

    @Test
    fun `every built-in routine name is transcribed exactly`() {
        assertEquals("§F.5 ships nine built-ins", 9, SeedCatalog.routines.size)
        SeedCatalog.routines.forEach { seed ->
            assertEquals(
                "JaCatalog disagrees with the seeded name for ${seed.id}",
                seed.name,
                JaCatalog.routineName(seed.id),
            )
        }
    }

    @Test
    fun `every programme name, note, step label and step note is transcribed exactly`() {
        assertEquals("§F.2–F.4 ship three programmes", 3, SeedCatalog.programs.size)
        SeedCatalog.programs.forEach { program ->
            assertEquals(program.nameJa, JaCatalog.programName(program.id))
            assertEquals(program.noteJa, JaCatalog.programNote(program.id))
            program.steps.forEach { step ->
                assertEquals(
                    "step label ${program.id}/${step.stepIndex}",
                    step.labelJa,
                    JaCatalog.stepLabel(program.id, step.stepIndex),
                )
                assertEquals(
                    "step note ${program.id}/${step.stepIndex}",
                    step.noteJa,
                    JaCatalog.stepNote(program.id, step.stepIndex),
                )
            }
        }
    }

    @Test
    fun `every station note is transcribed exactly`() {
        val notes = SeedCatalog.routines.flatMap { routine ->
            routine.stations.mapIndexedNotNull { position, station ->
                station.note?.let { Triple(routine.id, position, it) }
            }
        }
        assertEquals("four seeded station notes, two distinct", 4, notes.size)
        notes.forEach { (routineId, position, note) ->
            assertEquals(
                "station note $routineId/$position",
                note,
                JaCatalog.stationNote(routineId, position),
            )
        }
    }

    @Test
    fun `a station with no seeded note has no translation to find`() {
        // The fallback is what carries a user's forked row through untouched, so a lookup that
        // invented a hit for an unnoted position would overwrite their data on screen.
        assertNull(JaCatalog.stationNote("r_seven_minute", 0))
        assertNull(JaCatalog.stationNote("r_murph", 1))
        assertNull(JaCatalog.stationNote("r_tabata", 1))
    }

    // ─── English must exist, and must not be Japanese ───────────────────────────────────────────

    @Test
    fun `english covers every seeded string`() {
        SeedCatalog.exercises.filter { it.cue != null }.forEach {
            assertTrue("no English cue for ${it.id}", EnCatalog.exerciseCue(it.id) != null)
        }
        SeedCatalog.routines.forEach {
            assertTrue("no English name for ${it.id}", EnCatalog.routineName(it.id) != null)
            it.stations.forEachIndexed { position, station ->
                if (station.note != null) {
                    assertTrue(
                        "no English station note for ${it.id}/$position",
                        EnCatalog.stationNote(it.id, position) != null,
                    )
                }
            }
        }
        SeedCatalog.programs.forEach { program ->
            assertTrue("no English name for ${program.id}", EnCatalog.programName(program.id) != null)
            if (program.noteJa != null) {
                assertTrue("no English note for ${program.id}", EnCatalog.programNote(program.id) != null)
            }
            program.steps.forEach { step ->
                if (step.labelJa != null) {
                    assertTrue(
                        "no English label for ${program.id}/${step.stepIndex}",
                        EnCatalog.stepLabel(program.id, step.stepIndex) != null,
                    )
                }
                if (step.noteJa != null) {
                    assertTrue(
                        "no English note for ${program.id}/${step.stepIndex}",
                        EnCatalog.stepNote(program.id, step.stepIndex) != null,
                    )
                }
            }
        }
    }

    @Test
    fun `no English value holds a Japanese character`() {
        val offenders = allValues(EnCatalog).filter { JAPANESE.containsMatchIn(it) }
        assertEquals("EnCatalog is still shipping Japanese", emptyList<String>(), offenders)
        // The mirror of it, so the pair above cannot be satisfied by emptying the table.
        assertTrue(allValues(JaCatalog).all { JAPANESE.containsMatchIn(it) })
    }

    @Test
    fun `the two tables agree on nothing but proper nouns`() {
        // Six of the nine routine names are transliterations coming home, so they differ. The point of
        // the assertion is the other direction: no key may have been left un-translated by copy-paste.
        val untranslated = SeedCatalog.exercises.filter { it.cue != null }
            .filter { EnCatalog.exerciseCue(it.id) == JaCatalog.exerciseCue(it.id) }
        assertEquals("an English cue is still the Japanese one", emptyList<String>(), untranslated.map { it.id })
    }

    // ─── The read-side selectors ────────────────────────────────────────────────────────────────

    @Test
    fun `an exercise renders its own column per language`() {
        val pushup = seedExercise("pushup")
        assertEquals("腕立て伏せ", pushup.displayName(stringsFor(Lang.Ja)))
        assertEquals("Push-up", pushup.displayName(stringsFor(Lang.En)))
        assertEquals("体は一直線に", pushup.displayCue(stringsFor(Lang.Ja)))
        assertEquals("Body in a straight line", pushup.displayCue(stringsFor(Lang.En)))
    }

    @Test
    fun `a movement with no cue has none in either language`() {
        // §F.1 writes "—" for 走る, which means *none*. An English sentence here would be invented.
        val run = seedExercise("run")
        assertNull(run.displayCue(stringsFor(Lang.Ja)))
        assertNull(run.displayCue(stringsFor(Lang.En)))
    }

    @Test
    fun `a row the catalogue does not know falls back to what is stored`() {
        // The user-content case: a routine the user built, or a movement from a future seed this
        // build has no translation for. The stored string is the answer in every language.
        Lang.entries.map { stringsFor(it) }.forEach { strings ->
            assertNull(strings.catalog.routineName("u_1f0c9e2a"))
            assertNull(strings.catalog.exerciseCue("no_such_movement"))
            assertNull(strings.catalog.stepLabel("p_recon_ron", 1))
        }
    }

    private fun seedExercise(id: String): Exercise = SeedCatalog.exercises.first { it.id == id }.let {
        Exercise(
            id = it.id,
            nameJa = it.nameJa,
            nameEn = it.nameEn,
            pattern = it.pattern,
            secondsPerRep = it.secondsPerRep,
            difficulty = it.difficulty,
            isIsometric = it.isIsometric,
            cue = it.cue,
            ladderId = it.ladderId,
            catalogVersion = it.catalogVersion,
            archived = false,
        )
    }

    /** Every string a table can produce, reached through the same dispatch the app uses. */
    private fun allValues(catalog: CatalogStrings): List<String> = buildList {
        SeedCatalog.exercises.forEach { catalog.exerciseCue(it.id)?.let(::add) }
        SeedCatalog.routines.forEach { routine ->
            catalog.routineName(routine.id)?.let(::add)
            routine.stations.indices.forEach { catalog.stationNote(routine.id, it)?.let(::add) }
        }
        SeedCatalog.programs.forEach { program ->
            catalog.programName(program.id)?.let(::add)
            catalog.programNote(program.id)?.let(::add)
            program.steps.forEach { step ->
                catalog.stepLabel(program.id, step.stepIndex)?.let(::add)
                catalog.stepNote(program.id, step.stepIndex)?.let(::add)
            }
        }
    }

    private companion object {
        val JAPANESE = Regex("[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FFF]")
    }
}
