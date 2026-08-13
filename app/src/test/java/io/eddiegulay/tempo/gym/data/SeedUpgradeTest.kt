package io.eddiegulay.tempo.gym.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The property Phase 2 was allowed to exist on: **the seed counter is not the schema counter.**
 *
 * `00-plan.md` §2 row 17 and `02-data.md` §B.2 both hang on it. Shipping バーバラ adds rows and no
 * columns, so if seeding lived in `onUpgrade` the release would have to invent an empty migration
 * purely to get the data in — a lie about what changed, and, far worse, a lie that does not even work:
 * an app update whose `SCHEMA_VERSION` is unchanged **never calls `onUpgrade` at all**, so the six new
 * routines would silently fail to arrive for every existing install while appearing correctly on every
 * fresh one. Two counters, `SCHEMA_VERSION` for structure and [SeedCatalog.VERSION] for content, is what
 * makes a content-only release possible.
 *
 * What is asserted here is everything about that separation reachable without a database — which is more
 * than it sounds, because the interesting failures are decisions rather than SQL results. A v1 → v2
 * upgrade must **add** and nothing else: it must not revisit a routine the database already holds, must
 * not re-derive a frozen estimate, must not send anyone back to step one of リーコン・ロン, and must not
 * resurrect a built-in the user archived. Three of those four are properties of [SeedCatalog.planFrom];
 * the fourth is a property of one UPDATE statement, which is held as a constant so it can be one.
 *
 * `SQLiteDatabase` is not on the JVM classpath and Robolectric is a new dependency, which
 * `CONTRIBUTING.md` forbids — so the seam was moved rather than the test framework. That is the same
 * bargain `StoreGuardsTest` makes for corruption and `GymNav` makes for the whole back stack.
 */
class SeedUpgradeTest {

    private val phaseTwo = listOf(
        "r_cindy", "r_cindy_scaled", "r_chelsea", "r_barbara", "r_murph", "r_death_by",
    )

    private val phaseOne = listOf("r_seven_minute", "r_tabata", "r_recon_ron")

    // ── The two counters ────────────────────────────────────────────────────────────────────────

    @Test
    fun `Phase 2's content arrives without a schema migration`() {
        // The load-bearing assertion of this file. Six new built-ins, and the DDL list is untouched:
        // one migration, still at version 1, still the only thing SCHEMA_VERSION is derived from.
        assertEquals(2, SeedCatalog.VERSION)
        assertEquals(1, SCHEMA_VERSION)
        assertEquals(1, MIGRATIONS.size)
        assertEquals(1, MIGRATIONS.single().version)
    }

    @Test
    fun `the schema version is derived from the migrations, so the two can never drift`() {
        // Deriving it is what closes the bug class that ships an app opening a v2 database with v1
        // tables. It also means this file cannot be satisfied by editing a constant: adding a migration
        // to "paper over" a seed bump would move SCHEMA_VERSION and fail the assertion above.
        assertEquals(MIGRATIONS.maxOf { it.version }, SCHEMA_VERSION)
        assertEquals(MIGRATIONS.map { it.version }, MIGRATIONS.map { it.version }.sorted())
        assertEquals(MIGRATIONS.indices.map { it + 1 }, MIGRATIONS.map { it.version })
    }

    // ── v1 → v2, on a database that already has a year of history ───────────────────────────────

    @Test
    fun `upgrading from v1 plans the six new routines and nothing else`() {
        val plan = SeedCatalog.planFrom(1)

        assertEquals(phaseTwo, plan.routines.map { it.id })
        // No exercise and no programme changed, so neither table is touched. An upgrade that rewrote
        // exercise rows would be safe — every historical result froze its own coefficient — but it would
        // still be a write nobody asked for, over rows the user's sessions hold foreign keys into.
        assertTrue(plan.exercises.isEmpty())
        assertTrue(plan.programs.isEmpty())
    }

    @Test
    fun `nothing the database already holds is revisited, archived or not`() {
        // This is the archived-built-in case, at the level where it is decided. The user who deleted
        // 七分間 in March keeps it deleted in April: the routine is never *visited* by a v2 upgrade, so
        // no statement runs against its row at all — not the head repoint, not the metadata refresh.
        val planned = SeedCatalog.planFrom(1).routines.map { it.id }
        phaseOne.forEach { assertFalse(it, it in planned) }
    }

    @Test
    fun `a v1 routine's frozen columns are never re-derived by a later generation`() {
        // A `routine_version` is immutable and its est_duration_sec / est_total_reps / structural_hash
        // were computed once, against the pace table of the day. Re-deriving them on upgrade would
        // insert a new version and repoint the head — which is the March/April failure wearing a
        // maintenance hat, since the numbers would change under sessions that already reference them.
        assertTrue(SeedCatalog.planFrom(1).routines.none { it.catalogVersion <= 1 })
    }

    @Test
    fun `the head repoint cannot reach anything that is the user's`() {
        // The one statement an upgrade *does* run against an existing routine row. It names the version
        // pointer and the four shipped-metadata columns, and it must never name `archived_at` or
        // `favourite`: a preset upgrade that un-hid a routine the user archived is the app overruling
        // them on a launch they did not ask for, and it would read as a bug in the library filter.
        assertFalse(SQL_REPOINT_BUILT_IN.contains("archived_at"))
        assertFalse(SQL_REPOINT_BUILT_IN.contains("favourite"))
        assertFalse(SQL_REPOINT_BUILT_IN.contains("created_at"))
        // And it is scoped to built-ins, so a copy-on-write edit of 七分間 — a different row with
        // built_in = 0 — is out of reach however the ids happen to line up (§A.0.3).
        assertTrue(SQL_REPOINT_BUILT_IN.contains("built_in = 1"))
        assertTrue(SQL_REPOINT_BUILT_IN.trimStart().startsWith("UPDATE"))
        val assigned = SQL_REPOINT_BUILT_IN.substringAfter("SET").substringBefore("WHERE")
        assertEquals(
            listOf("head_version_id", "tier", "origin", "catalog_version", "sort_order"),
            Regex("(\\w+) =").findAll(assigned).map { it.groupValues[1] }.toList(),
        )
    }

    @Test
    fun `a reseed never sends anyone back to step one`() {
        // `progression_state` is where the user is in リーコン・ロン, and it is theirs. INSERT OR IGNORE
        // is what makes the seeder able to create the row for a *new* programme without resetting the
        // position in an old one — a plain INSERT would fail the transaction and a REPLACE would quietly
        // put someone on step nine back at 第一段 on the launch that shipped バーバラ.
        assertTrue(SQL_ENSURE_PROGRESSION_STATE.trimStart().startsWith("INSERT OR IGNORE"))
        assertFalse(SQL_ENSURE_PROGRESSION_STATE.contains("REPLACE"))
    }

    // ── Running it twice ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a database already at the current generation plans nothing at all`() {
        // Idempotence at the catalog level. `onOpen` guards on `installed < VERSION` and would not call
        // the seeder at all — but the seeder is also called from `onCreate`, and after a crashed
        // half-seed the stored version may be anything, so the plan itself has to be empty rather than
        // merely unreached.
        assertTrue(SeedCatalog.planFrom(SeedCatalog.VERSION).isEmpty)
    }

    @Test
    fun `a database from a newer build than this one plans nothing`() {
        // A sideloaded older APK over a newer database is `onDowngrade`'s business, and it quarantines.
        // Until it does, a seeder that read `>` as `!=` would rewrite the newer generation's rows with
        // this build's — so the comparison is asserted, not assumed.
        assertTrue(SeedCatalog.planFrom(SeedCatalog.VERSION + 1).isEmpty)
    }

    @Test
    fun `a fresh install plans the whole catalogue`() {
        // `onCreate` seeds from zero. Every generation, in one transaction, including the ones an
        // upgrading device got earlier — which is why the two populations end up with identical rows
        // and why nothing in the catalogue may be phrased as a delta.
        val plan = SeedCatalog.planFrom(0)
        assertEquals(SeedCatalog.exercises, plan.exercises)
        assertEquals(SeedCatalog.programs, plan.programs)
        assertEquals(SeedCatalog.routines, plan.routines)
        assertEquals(phaseOne + phaseTwo, plan.routines.map { it.id })
    }

    @Test
    fun `every generation of the plan is a suffix of the one before it`() {
        // The general form of "an upgrade adds": for any two generations, the newer plan is contained in
        // the older, and everything planned is stamped newer than the database asking. A row stamped
        // with a version that already shipped — the copy-paste that forgets `catalogVersion = 2` — is
        // caught here as well as by its absence from the v1 → v2 plan.
        (0..SeedCatalog.VERSION).forEach { from ->
            val plan = SeedCatalog.planFrom(from)
            assertTrue(from.toString(), plan.exercises.all { it.catalogVersion > from })
            assertTrue(from.toString(), plan.programs.all { it.catalogVersion > from })
            assertTrue(from.toString(), plan.routines.all { it.catalogVersion > from })
            if (from > 0) {
                val wider = SeedCatalog.planFrom(from - 1)
                assertTrue(from.toString(), wider.routines.containsAll(plan.routines))
                assertTrue(from.toString(), wider.exercises.containsAll(plan.exercises))
                assertTrue(from.toString(), wider.programs.containsAll(plan.programs))
            }
        }
    }

    @Test
    fun `no seed is stamped ahead of the counter that ships it`() {
        // A row stamped 3 in a v2 build is invisible on every device until the counter moves, and
        // invisible in a way nothing reports: it is not missing, it is simply never planned.
        assertTrue(SeedCatalog.exercises.all { it.catalogVersion in 1..SeedCatalog.VERSION })
        assertTrue(SeedCatalog.programs.all { it.catalogVersion in 1..SeedCatalog.VERSION })
        assertTrue(SeedCatalog.routines.all { it.catalogVersion in 1..SeedCatalog.VERSION })
    }

    @Test
    fun `every station of every planned routine names a movement the same plan can rely on`() {
        // `routine_station.exercise_id` is an ON DELETE RESTRICT foreign key, so a Phase 2 routine
        // naming an exercise that is not in the catalogue is a constraint failure inside the seed
        // transaction — on the launcher, on first launch, with no history screen to fall back to.
        // Phase 2 adds no exercises, so every station it introduces must resolve against v1's rows.
        val v1Exercises = SeedCatalog.planFrom(0).exercises
            .filter { it.catalogVersion == 1 }
            .map { it.id }
            .toSet()
        SeedCatalog.planFrom(1).routines.forEach { routine ->
            routine.stations.forEach { station ->
                assertTrue("${routine.id}/${station.exerciseId}", station.exerciseId in v1Exercises)
            }
        }
    }
}
