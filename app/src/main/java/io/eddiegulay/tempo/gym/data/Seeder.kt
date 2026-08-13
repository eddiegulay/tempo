package io.eddiegulay.tempo.gym.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/**
 * Writes [SeedCatalog] into the tables. Three rules, and the whole design hangs off them.
 *
 *  1. **Exercises are upserted by id.** A new one inserts; an existing one has its *descriptive*
 *     columns refreshed, and the row is never deleted — `routine_station` and `session_result` hold
 *     foreign keys into it, so retiring a movement means `archived = 1`. Updating `difficulty` is safe
 *     precisely because every historical result froze its own copy (§A.0.5), so a retuned coefficient
 *     changes future volume only.
 *
 *  2. **A changed built-in routine gets a NEW `routine_version`, never an UPDATE**, and the routine
 *     row's `head_version_id` is repointed. Every session performed before the update still references
 *     the old version and still describes what the user actually did. This is the March/April
 *     guarantee, and it costs one INSERT.
 *
 *  3. **User routines are never touched.** Every statement here is scoped by `built_in = 1`, so an
 *     upgrade to the shipped 七分間 flows to the preset and leaves a user's copy-on-write edit of it
 *     exactly as it was (§A.0.3).
 *
 * The routine pass is **content-addressed** and that is what makes reseeding idempotent — which
 * matters because `onOpen` may run this over a database a previously crashed upgrade already
 * half-seeded. Without the hash check, every launch after a partial write piles up another identical
 * version (§B.3).
 */
internal object Seeder {

    /** Applies every seed generation newer than [fromCatalogVersion], in one transaction. */
    fun applyTo(db: SQLiteDatabase, fromCatalogVersion: Int) {
        if (fromCatalogVersion >= SeedCatalog.VERSION) return
        db.transact {
            // routine and routine_version are mutually referential, and a first insert cannot satisfy
            // both statements in isolation. Deferring to commit is one line and is why §A.2 tolerates
            // the cycle at all.
            execSQL("PRAGMA defer_foreign_keys = ON")

            SeedCatalog.exercises
                .filter { it.catalogVersion > fromCatalogVersion }
                .forEach { upsertExercise(this, it) }

            SeedCatalog.programs
                .filter { it.catalogVersion > fromCatalogVersion }
                .forEach { upsertProgram(this, it) }

            SeedCatalog.routines
                .filter { it.catalogVersion > fromCatalogVersion }
                .forEach { upsertBuiltInRoutine(this, it) }

            Meta.write(this, Meta.SEED_CATALOG_VERSION, SeedCatalog.VERSION.toString())
            // A seed upgrade can change a coefficient, and the personal-record cache is derived from
            // frozen ones — so it is not wrong, but it is worth re-deriving once from truth (§A.9).
            Meta.write(this, Meta.PR_REBUILD_NEEDED, "1")
        }
    }

    private fun upsertExercise(db: SQLiteDatabase, seed: ExerciseSeed) {
        db.execSQL(
            """
            INSERT INTO $TABLE_EXERCISE
                (id, name_ja, name_en, pattern, seconds_per_rep, difficulty, is_isometric, cue,
                 ladder_id, catalog_version, archived)
            VALUES (?,?,?,?,?,?,?,?,?,?,0)
            ON CONFLICT(id) DO UPDATE SET
                name_ja = excluded.name_ja,
                name_en = excluded.name_en,
                pattern = excluded.pattern,
                seconds_per_rep = excluded.seconds_per_rep,
                difficulty = excluded.difficulty,
                is_isometric = excluded.is_isometric,
                cue = excluded.cue,
                ladder_id = excluded.ladder_id,
                catalog_version = excluded.catalog_version
            """.trimIndent(),
            arrayOf<Any?>(
                seed.id,
                seed.nameJa,
                seed.nameEn,
                seed.pattern.name,
                seed.secondsPerRep,
                seed.difficulty,
                if (seed.isIsometric) 1 else 0,
                seed.cue,
                seed.ladderId,
                seed.catalogVersion,
            ),
        )
    }

    /**
     * Programs, their steps and their sets.
     *
     * Steps are **upserted by `(program_id, step_index)` rather than deleted and reinserted**, because
     * `session.progression_step_id` is an `ON DELETE RESTRICT` foreign key: once anyone has performed a
     * step, a wholesale delete cannot succeed. Keying on the pair is what keeps a step's rowid stable
     * across a reseed, which is the property §A.5 relies on. Sets underneath *are* replaced wholesale —
     * nothing references a set.
     */
    private fun upsertProgram(db: SQLiteDatabase, seed: ProgramSeed) {
        db.execSQL(
            """
            INSERT INTO $TABLE_PROGRESSION
                (id, name_ja, step_unit, step_count, advance_rule, advance_param, cycle_days, origin,
                 note_ja, catalog_version)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
                name_ja = excluded.name_ja,
                step_unit = excluded.step_unit,
                step_count = excluded.step_count,
                advance_rule = excluded.advance_rule,
                advance_param = excluded.advance_param,
                cycle_days = excluded.cycle_days,
                origin = excluded.origin,
                note_ja = excluded.note_ja,
                catalog_version = excluded.catalog_version
            """.trimIndent(),
            arrayOf<Any?>(
                seed.id,
                seed.nameJa,
                seed.stepUnit.name,
                seed.stepCount,
                seed.advanceRule.name,
                seed.advanceParam,
                seed.cycleDays,
                seed.origin,
                seed.noteJa,
                seed.catalogVersion,
            ),
        )

        seed.steps.forEach { step ->
            db.execSQL(
                """
                INSERT INTO $TABLE_PROGRESSION_STEP
                    (program_id, step_index, label_ja, total_reps, shape, rest_sec, note_ja)
                VALUES (?,?,?,?,?,?,?)
                ON CONFLICT(program_id, step_index) DO UPDATE SET
                    label_ja = excluded.label_ja,
                    total_reps = excluded.total_reps,
                    shape = excluded.shape,
                    rest_sec = excluded.rest_sec,
                    note_ja = excluded.note_ja
                """.trimIndent(),
                arrayOf<Any?>(
                    seed.id,
                    step.stepIndex,
                    step.labelJa,
                    step.totalReps,
                    step.shape.name,
                    step.restSeconds,
                    step.noteJa,
                ),
            )
            val stepId = db.rawQuery(
                "SELECT id FROM $TABLE_PROGRESSION_STEP WHERE program_id = ? AND step_index = ?",
                arrayOf(seed.id, step.stepIndex.toString()),
            ).use { c -> if (c.moveToFirst()) c.getLong(0) else null } ?: return@forEach

            db.delete(TABLE_PROGRESSION_SET, "step_id = ?", arrayOf(stepId.toString()))
            step.sets.forEach { set ->
                db.insertOrThrow(
                    TABLE_PROGRESSION_SET,
                    null,
                    ContentValues().apply {
                        put("step_id", stepId)
                        put("set_index", set.setIndex)
                        put("reps", set.reps)
                        put("variant", set.variant?.name)
                    },
                )
            }
        }

        // The user's position in a programme is theirs, so this only ever creates a missing row — a
        // reseed must never send someone back to step one.
        db.execSQL(
            """
            INSERT OR IGNORE INTO $TABLE_PROGRESSION_STATE
                (program_id, current_step_index, sessions_at_step, step_entered_at, cycle_day)
            VALUES (?, 1, 0, ?, 1)
            """.trimIndent(),
            arrayOf<Any?>(seed.id, System.currentTimeMillis()),
        )
    }

    /** Content-addressed: identical stations + parameters produce no new version, so a no-op reseed is free. */
    private fun upsertBuiltInRoutine(db: SQLiteDatabase, seed: RoutineSeed) {
        val hash = seed.structuralHash()
        if (queryHeadStructuralHash(db, seed.id) == hash) return

        val now = System.currentTimeMillis()
        val versionNumber = db.rawQuery(
            "SELECT COALESCE(MAX(version_number), 0) + 1 FROM $TABLE_ROUTINE_VERSION WHERE routine_id = ?",
            arrayOf(seed.id),
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 1 }

        val versionId = db.insertOrThrow(
            TABLE_ROUTINE_VERSION,
            null,
            ContentValues().apply {
                put("routine_id", seed.id)
                put("version_number", versionNumber)
                put("name", seed.name)
                put("engine", seed.engine.name)
                put("rounds", seed.rounds)
                put("time_cap_sec", seed.timeCapSeconds)
                put("interval_sec", seed.intervalSeconds)
                put("rest_between_stations", seed.restBetweenStations)
                put("rest_between_rounds", seed.restBetweenRounds)
                put("prepare_sec", seed.prepareSeconds)
                put("progression_program_id", seed.progressionProgramId)
                put("primary_metric", seed.primaryMetric.name)
                put("station_count", seed.stations.size)
                put("est_duration_sec", seed.estimatedDuration(db))
                put("est_total_reps", estimatedTotalReps(seed.shapes(), seed.rounds))
                put("structural_hash", hash)
                put("created_at", now)
            },
        )

        seed.stations.forEachIndexed { position, station ->
            db.insertOrThrow(
                TABLE_ROUTINE_STATION,
                null,
                ContentValues().apply {
                    put("routine_version_id", versionId)
                    put("position", position)
                    put("exercise_id", station.exerciseId)
                    put("prescription_kind", station.measure.name)
                    put("prescribed_reps", station.reps)
                    put("prescribed_sec", station.seconds)
                    put("note", station.note)
                },
            )
        }

        val existing = db.rawQuery(
            "SELECT 1 FROM $TABLE_ROUTINE WHERE id = ?",
            arrayOf(seed.id),
        ).use { it.moveToFirst() }

        if (existing) {
            // Only the version pointer and the shipped metadata. `favourite` and `archived_at` are the
            // user's, and a preset upgrade that un-hid a routine they deleted would be the app
            // overruling them.
            db.execSQL(
                """
                UPDATE $TABLE_ROUTINE
                   SET head_version_id = ?, tier = ?, origin = ?, catalog_version = ?, sort_order = ?
                 WHERE id = ? AND built_in = 1
                """.trimIndent(),
                arrayOf<Any?>(
                    versionId,
                    seed.tier?.storageValue,
                    seed.origin,
                    seed.catalogVersion,
                    seed.sortOrder,
                    seed.id,
                ),
            )
        } else {
            db.insertOrThrow(
                TABLE_ROUTINE,
                null,
                ContentValues().apply {
                    put("id", seed.id)
                    put("head_version_id", versionId)
                    put("built_in", 1)
                    put("tier", seed.tier?.storageValue)
                    put("scaled_from_routine_id", seed.scaledFromRoutineId)
                    put("origin", seed.origin)
                    put("catalog_version", seed.catalogVersion)
                    put("sort_order", seed.sortOrder)
                    put("favourite", 0)
                    put("created_at", System.currentTimeMillis())
                },
            )
        }
    }

    private fun queryHeadStructuralHash(db: SQLiteDatabase, routineId: String): Int? =
        db.rawQuery(
            """
            SELECT v.structural_hash FROM $TABLE_ROUTINE r
            JOIN $TABLE_ROUTINE_VERSION v ON v.id = r.head_version_id
            WHERE r.id = ?
            """.trimIndent(),
            arrayOf(routineId),
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else null }

    private fun RoutineSeed.structuralHash(): Int = structuralHashOf(
        name = name,
        engine = engine,
        rounds = rounds,
        timeCapSeconds = timeCapSeconds,
        intervalSeconds = intervalSeconds,
        restBetweenStations = restBetweenStations,
        restBetweenRounds = restBetweenRounds,
        prepareSeconds = prepareSeconds,
        progressionProgramId = progressionProgramId,
        stations = shapes(),
    )

    private fun RoutineSeed.estimatedDuration(db: SQLiteDatabase): Int = estimatedDurationSeconds(
        stations = shapes(),
        // Read from the table rather than from SeedCatalog, so a routine seeded in a later generation
        // is estimated against the exercise rows this database actually holds.
        secondsPerRep = { id -> secondsPerRepOf(db, id) },
        rounds = rounds,
        timeCapSeconds = timeCapSeconds,
        restBetweenStations = restBetweenStations,
        restBetweenRounds = restBetweenRounds,
        prepareSeconds = prepareSeconds,
    )

    private fun secondsPerRepOf(db: SQLiteDatabase, exerciseId: String): Double? =
        db.rawQuery(
            "SELECT seconds_per_rep FROM $TABLE_EXERCISE WHERE id = ?",
            arrayOf(exerciseId),
        ).use { c -> if (c.moveToFirst()) c.getDouble(0) else null }
}
