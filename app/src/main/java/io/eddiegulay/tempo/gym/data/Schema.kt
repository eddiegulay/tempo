package io.eddiegulay.tempo.gym.data

/*
 * 鍛錬's schema, as DDL, checked line by line against SQLite **3.28** — the version Android 10 ships
 * and therefore the floor at `minSdk 29`.
 *
 * What that floor rules out is worth restating where the SQL lives, because every one of these is a
 * feature a reviewer will reach for: no `STRICT` tables (3.37), no `RETURNING` (3.35), no
 * `DROP COLUMN` (3.35), no generated columns (3.31), no `sqrt()` (3.35 plus a compile flag), and — the
 * decisive one — **no JSON1**, which Android only enables from API 30. The last is why there is not a
 * single blob column here: a JSON station list would be unqueryable on the very devices this schema
 * exists to support, so the normalised rows are not a style preference (§0, §A.0.2).
 *
 * What it does allow, and what this file uses: partial indices (3.8), upsert (3.24), expression
 * indices (3.9), and `ALTER TABLE … RENAME COLUMN` for future migrations (3.25).
 *
 * The schema is **not phased** — only the UI is. Every table below is created in v1, including
 * `training_plan`, which `DECISIONS.md` §Q1 says no code will write a row into during Phases 1–3. An
 * unused table costs nothing and means the picker, when it lands, needs no migration.
 */

internal const val DB_NAME = "exercise.db"

internal const val TABLE_EXERCISE = "exercise"
internal const val TABLE_ROUTINE = "routine"
internal const val TABLE_ROUTINE_VERSION = "routine_version"
internal const val TABLE_ROUTINE_STATION = "routine_station"
internal const val TABLE_PROGRESSION = "progression_program"
internal const val TABLE_PROGRESSION_STEP = "progression_step"
internal const val TABLE_PROGRESSION_SET = "progression_set"
internal const val TABLE_PROGRESSION_STATE = "progression_state"
internal const val TABLE_SESSION = "session"
internal const val TABLE_SESSION_RESULT = "session_result"
internal const val TABLE_TRAINING_PLAN = "training_plan"
internal const val TABLE_PERSONAL_RECORD = "personal_record"
internal const val TABLE_META = "exercise_meta"

/**
 * Every statement that builds schema v1, in dependency order.
 *
 * `routine` and `routine_version` are mutually referential and cannot be ordered — SQLite tolerates
 * that because foreign keys are resolved at DML time, not at `CREATE TABLE` time, and the one write
 * that has to satisfy both at once wraps itself in `PRAGMA defer_foreign_keys = ON` (§A.2).
 */
internal val V1_STATEMENTS: List<String> = listOf(

    // ── exercise (§A.1) ──────────────────────────────────────────────────────────────────────────
    // `ladder_id` is not in §A.1's DDL and has to be: §F.1 puts the seven push-up variations in one
    // 'push' family, and both ExerciseCatalog.ladder() and 動きごと's いちばん上 roll-up are keyed on
    // it (04 §4 edge case 6). Without the column neither is implementable.
    //
    // `seconds_per_rep` means two different things by design. For a rep movement it is a pacer
    // estimate that nothing scores from. For an isometric it is redefined as *the seconds that count
    // as one volume unit* — ten — which is what makes volumeUnits() score a thirty-second plank as
    // three rather than as zero (§F.1, §D.5).
    """
    CREATE TABLE exercise (
        id                TEXT    NOT NULL PRIMARY KEY,
        name_ja           TEXT    NOT NULL,
        name_en           TEXT    NOT NULL,
        pattern           TEXT    NOT NULL,
        seconds_per_rep   REAL    NOT NULL,
        difficulty        REAL    NOT NULL,
        is_isometric      INTEGER NOT NULL DEFAULT 0,
        cue               TEXT,
        ladder_id         TEXT,
        catalog_version   INTEGER NOT NULL,
        archived          INTEGER NOT NULL DEFAULT 0,
        CHECK (pattern IN ('H_PUSH','V_PULL','SQUAT','HINGE','CORE','LOCOMOTION','PLYO')),
        CHECK (seconds_per_rep > 0),
        CHECK (difficulty > 0),
        CHECK (is_isometric IN (0,1)),
        CHECK (archived IN (0,1))
    )
    """,
    // Serves §6's adjacent-pattern warning and the picker grouped by pattern. Without it the picker
    // table-scans on every keystroke.
    "CREATE INDEX idx_exercise_pattern ON exercise(pattern, archived)",
    "CREATE INDEX idx_exercise_ladder ON exercise(ladder_id)",

    // ── routine_version (§A.3) ───────────────────────────────────────────────────────────────────
    // Immutable. Never updated, never deleted except as garbage collection of a draft nothing ever
    // performed (§B.4). This is the anti-March/April pin: a session references a version, so a routine
    // rewritten in April cannot re-interpret what March did.
    """
    CREATE TABLE routine_version (
        id                      INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
        routine_id              TEXT    NOT NULL,
        version_number          INTEGER NOT NULL,
        name                    TEXT    NOT NULL,
        engine                  TEXT    NOT NULL,
        rounds                  INTEGER,
        time_cap_sec            INTEGER,
        interval_sec            INTEGER,
        rest_between_stations   INTEGER NOT NULL DEFAULT 0,
        rest_between_rounds     INTEGER NOT NULL DEFAULT 0,
        prepare_sec             INTEGER NOT NULL DEFAULT 5,
        progression_program_id  TEXT,
        primary_metric          TEXT    NOT NULL,
        station_count           INTEGER NOT NULL,
        est_duration_sec        INTEGER NOT NULL,
        est_total_reps          INTEGER NOT NULL,
        structural_hash         INTEGER NOT NULL,
        created_at              INTEGER NOT NULL,
        FOREIGN KEY (routine_id)             REFERENCES routine(id)             ON DELETE RESTRICT,
        FOREIGN KEY (progression_program_id) REFERENCES progression_program(id) ON DELETE RESTRICT,
        CHECK (engine IN ('INTERVAL_CIRCUIT','AMRAP','FOR_TIME','FOR_TIME_WITH_REST',
                          'EMOM','EMOM_ASCENDING','FIXED_SETS')),
        CHECK (primary_metric IN ('BEST_TIME','MOST_ROUNDS','MOST_REPS','MOST_VOLUME','HIGHEST_STEP')),
        CHECK (rest_between_stations >= 0 AND rest_between_rounds >= 0 AND prepare_sec >= 0),
        CHECK (rounds IS NULL OR rounds >= 1),
        CHECK (time_cap_sec IS NULL OR time_cap_sec > 0),
        CHECK (rounds IS NOT NULL OR time_cap_sec IS NOT NULL),
        CHECK ((engine = 'FIXED_SETS') = (progression_program_id IS NOT NULL))
    )
    """,
    "CREATE UNIQUE INDEX idx_version_routine_number ON routine_version(routine_id, version_number)",

    // ── routine (§A.2) ───────────────────────────────────────────────────────────────────────────
    // Identity only. Everything performable lives on the version, so a rename, a favourite or an
    // archive touches no history.
    """
    CREATE TABLE routine (
        id                      TEXT    NOT NULL PRIMARY KEY,
        head_version_id         INTEGER NOT NULL,
        built_in                INTEGER NOT NULL,
        tier                    TEXT,
        derived_from_routine_id TEXT,
        scaled_from_routine_id  TEXT,
        origin                  TEXT,
        catalog_version         INTEGER NOT NULL DEFAULT 0,
        sort_order              INTEGER NOT NULL DEFAULT 0,
        favourite               INTEGER NOT NULL DEFAULT 0,
        created_at              INTEGER NOT NULL,
        archived_at             INTEGER,
        FOREIGN KEY (head_version_id)         REFERENCES routine_version(id) ON DELETE RESTRICT,
        FOREIGN KEY (derived_from_routine_id) REFERENCES routine(id)         ON DELETE SET NULL,
        FOREIGN KEY (scaled_from_routine_id)  REFERENCES routine(id)         ON DELETE SET NULL,
        CHECK (built_in IN (0,1)),
        CHECK (favourite IN (0,1)),
        CHECK (tier IS NULL OR tier IN ('入門','中級','上級'))
    )
    """,
    // One ordered scan produces the 型 block then the 自分の型 block with no filesort.
    "CREATE INDEX idx_routine_section ON routine(archived_at, built_in, sort_order)",

    // ── routine_station (§A.4) ───────────────────────────────────────────────────────────────────
    // Two nullable value columns plus a coherence CHECK, rather than one `value` + `unit` pair: the
    // database itself refuses REPS with a seconds value, so a malformed draft fails at the write and
    // not three screens later inside the timeline compiler. The composite primary key IS the ordering
    // index — it serves the detail read and the compiler's station walk, so there is no second index.
    """
    CREATE TABLE routine_station (
        routine_version_id INTEGER NOT NULL,
        position           INTEGER NOT NULL,
        exercise_id        TEXT    NOT NULL,
        prescription_kind  TEXT    NOT NULL,
        prescribed_reps    INTEGER,
        prescribed_sec     INTEGER,
        note               TEXT,
        PRIMARY KEY (routine_version_id, position),
        FOREIGN KEY (routine_version_id) REFERENCES routine_version(id) ON DELETE CASCADE,
        FOREIGN KEY (exercise_id)        REFERENCES exercise(id)        ON DELETE RESTRICT,
        CHECK (position >= 0),
        CHECK (prescription_kind IN ('REPS','DURATION','MAX_EFFORT')),
        CHECK (
            (prescription_kind = 'REPS'       AND prescribed_reps > 0 AND prescribed_sec IS NULL) OR
            (prescription_kind = 'DURATION'   AND prescribed_sec  > 0 AND prescribed_reps IS NULL) OR
            (prescription_kind = 'MAX_EFFORT' AND prescribed_reps IS NULL AND prescribed_sec IS NULL)
        )
    )
    """,

    // ── progressions (§A.5) ──────────────────────────────────────────────────────────────────────
    // Recon Ron's eighteen steps, Armstrong's five-day week and Pavel's thirty-day cycle are all "a
    // table of sets, indexed by step or day, with an advancement rule". `shape` is what earns the
    // schema its keep: four of Armstrong's five days are rules rather than numbers, and a flat rep
    // table could represent one fifth of the programme (§F.3).
    """
    CREATE TABLE progression_program (
        id              TEXT    NOT NULL PRIMARY KEY,
        name_ja         TEXT    NOT NULL,
        step_unit       TEXT    NOT NULL,
        step_count      INTEGER NOT NULL,
        advance_rule    TEXT    NOT NULL,
        advance_param   INTEGER,
        cycle_days      INTEGER,
        origin          TEXT    NOT NULL,
        note_ja         TEXT,
        catalog_version INTEGER NOT NULL,
        CHECK (step_unit IN ('STEP','DAY')),
        CHECK (advance_rule IN ('SESSIONS_COMPLETED','WEEKS_ELAPSED','ALL_SETS_MADE','MANUAL')),
        CHECK (step_count >= 1)
    )
    """,
    """
    CREATE TABLE progression_step (
        id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
        program_id  TEXT    NOT NULL,
        step_index  INTEGER NOT NULL,
        label_ja    TEXT,
        total_reps  INTEGER,
        shape       TEXT NOT NULL DEFAULT 'FIXED',
        rest_sec    INTEGER NOT NULL DEFAULT 90,
        note_ja     TEXT,
        FOREIGN KEY (program_id) REFERENCES progression_program(id) ON DELETE RESTRICT,
        CHECK (step_index >= 1),
        CHECK (shape IN ('FIXED','MAX_EFFORT','PYRAMID','LADDER','GRIP_ROTATION'))
    )
    """,
    // The unique key is also what keeps a step's rowid stable across a reseed, which is what lets a
    // session hold an FK to a step while the catalogue is upgraded around it (§A.5).
    "CREATE UNIQUE INDEX idx_prog_step ON progression_step(program_id, step_index)",
    """
    CREATE TABLE progression_set (
        step_id    INTEGER NOT NULL,
        set_index  INTEGER NOT NULL,
        reps       INTEGER,
        variant    TEXT,
        PRIMARY KEY (step_id, set_index),
        FOREIGN KEY (step_id) REFERENCES progression_step(id) ON DELETE CASCADE,
        CHECK (set_index >= 1)
    )
    """,
    """
    CREATE TABLE progression_state (
        program_id         TEXT    NOT NULL PRIMARY KEY,
        current_step_index INTEGER NOT NULL DEFAULT 1,
        sessions_at_step   INTEGER NOT NULL DEFAULT 0,
        step_entered_at    INTEGER NOT NULL,
        last_session_id    INTEGER,
        cycle_day          INTEGER NOT NULL DEFAULT 1,
        FOREIGN KEY (program_id)      REFERENCES progression_program(id) ON DELETE CASCADE,
        FOREIGN KEY (last_session_id) REFERENCES session(id)             ON DELETE SET NULL,
        CHECK (current_step_index >= 1 AND sessions_at_step >= 0)
    )
    """,

    // ── session (§A.6) ───────────────────────────────────────────────────────────────────────────
    // Note how many columns exist purely so that a query never has to guess. `routine_name` is
    // denormalised so a つづき banner survives the routine's deletion; `stations_planned` is frozen so
    // the 「二十種目中 八」 chip reports the twenty that was on screen, which a derived denominator
    // would not after a skip; `local_date` and `local_week_start` are computed in Kotlin because
    // SQLite's date functions are UTC (§0).
    """
    CREATE TABLE session (
        id                  INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
        routine_id          TEXT    NOT NULL,
        routine_version_id  INTEGER NOT NULL,
        routine_name        TEXT    NOT NULL,
        progression_step_id INTEGER,
        compiled_hash       INTEGER NOT NULL,

        started_at          INTEGER NOT NULL,
        finished_at         INTEGER,
        local_date          TEXT    NOT NULL,
        local_week_start    TEXT    NOT NULL,
        tz_id               TEXT    NOT NULL,

        started_at_elapsed  INTEGER NOT NULL,
        paused_accum_ms     INTEGER NOT NULL DEFAULT 0,
        paused_at_elapsed   INTEGER,
        paused_at_wall      INTEGER,
        boot_anchor_ms      INTEGER NOT NULL,
        last_write_elapsed  INTEGER NOT NULL,
        last_write_wall     INTEGER NOT NULL,
        active_ms           INTEGER NOT NULL DEFAULT 0,
        wall_ms             INTEGER NOT NULL DEFAULT 0,

        rounds_planned      INTEGER,
        rounds_completed    INTEGER NOT NULL DEFAULT 0,
        stations_planned    INTEGER NOT NULL,
        stations_completed  INTEGER NOT NULL DEFAULT 0,
        complete            INTEGER NOT NULL DEFAULT 0,
        reached_time_cap    INTEGER NOT NULL DEFAULT 0,
        tier                TEXT,

        rating              TEXT,
        rating_cr10         INTEGER,

        clock_delta_ms      INTEGER NOT NULL DEFAULT 0,
        created_by_version  INTEGER NOT NULL,

        FOREIGN KEY (routine_id)          REFERENCES routine(id)          ON DELETE RESTRICT,
        FOREIGN KEY (routine_version_id)  REFERENCES routine_version(id)  ON DELETE RESTRICT,
        FOREIGN KEY (progression_step_id) REFERENCES progression_step(id) ON DELETE RESTRICT,
        CHECK (active_ms >= 0 AND wall_ms >= 0 AND active_ms <= wall_ms),
        CHECK (rounds_completed >= 0 AND stations_completed >= 0),
        CHECK (complete IN (0,1) AND reached_time_cap IN (0,1)),
        CHECK (finished_at IS NULL OR finished_at >= started_at),
        CHECK (rating IS NULL OR rating IN ('EASY','JUST_RIGHT','HARD')),
        CHECK ((rating IS NULL) = (rating_cr10 IS NULL)),
        CHECK (rating_cr10 IS NULL OR rating_cr10 BETWEEN 1 AND 10),
        CHECK (length(local_date) = 10 AND length(local_week_start) = 10)
    )
    """,
    // ISO-8601 sorts lexicographically, so a month range on TEXT is an exact index range.
    "CREATE INDEX idx_session_date ON session(local_date DESC)",
    "CREATE INDEX idx_session_routine ON session(routine_id, started_at DESC)",
    "CREATE INDEX idx_session_week ON session(local_week_start)",
    // **This index is a constraint, not an optimisation.** It is what makes "two live sessions"
    // structurally unrepresentable: without it, a crash mid-session followed by a fresh start leaves
    // two unfinished rows and the resume banner picks one arbitrarily (§A.6).
    //
    // §A.6 writes it as `ON session(finished_at) WHERE finished_at IS NULL`, and that form enforces
    // **nothing**: SQLite treats every NULL in a unique index as distinct from every other NULL, so a
    // column whose indexed rows are all NULL can never collide. Indexing the *expression* is the form
    // that actually holds — every indexed row stores the value 1, so the second one is rejected. The
    // partial WHERE is kept so the index still contains at most one row and still serves the
    // resumable-session lookup as the spec intends.
    "CREATE UNIQUE INDEX idx_session_live ON session((finished_at IS NULL)) WHERE finished_at IS NULL",

    // ── session_result (§A.7) ────────────────────────────────────────────────────────────────────
    // Normalised rows rather than a JSON blob per session. The blob wins one write and loses every
    // read the feature performs: §7.4's headline metric is a sum over all history, per-exercise bests
    // are a GROUP BY, and `json_extract` does not exist at API 29 so the extraction cannot even be
    // pushed into SQL as a compromise (§A.0.2).
    """
    CREATE TABLE session_result (
        id                 INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
        session_id         INTEGER NOT NULL,
        ordinal            INTEGER NOT NULL,
        round_index        INTEGER NOT NULL,
        station_order      INTEGER NOT NULL,
        phase              TEXT    NOT NULL,
        exercise_id        TEXT,

        prescription_kind  TEXT,
        prescribed_reps    INTEGER,
        prescribed_sec     INTEGER,

        actual_reps        INTEGER,
        actual_duration_ms INTEGER NOT NULL DEFAULT 0,
        added_ms           INTEGER NOT NULL DEFAULT 0,
        skipped            INTEGER NOT NULL DEFAULT 0,

        difficulty_coef    REAL    NOT NULL DEFAULT 0,
        volume_units       REAL    NOT NULL DEFAULT 0,

        closed_at_wall     INTEGER NOT NULL,
        closed_at_elapsed  INTEGER NOT NULL,

        FOREIGN KEY (session_id)  REFERENCES session(id)  ON DELETE CASCADE,
        FOREIGN KEY (exercise_id) REFERENCES exercise(id) ON DELETE RESTRICT,
        CHECK (round_index >= 0 AND station_order >= 0),
        CHECK (skipped IN (0,1)),
        CHECK (actual_reps IS NULL OR actual_reps >= 0),
        CHECK (actual_duration_ms >= 0),
        CHECK (volume_units >= 0),
        CHECK (skipped = 0 OR (actual_reps IS NULL))
    )
    """,
    // Unique on (session_id, ordinal) is what makes a phase-transition write idempotent under
    // INSERT OR REPLACE: a retry after a crash cannot double-count, which is the whole basis of the
    // player's per-transition persistence.
    "CREATE UNIQUE INDEX idx_result_session ON session_result(session_id, ordinal)",
    "CREATE INDEX idx_result_exercise ON session_result(exercise_id, session_id)",

    // ── training_plan (§A.8) ─────────────────────────────────────────────────────────────────────
    // Created in v1 and deliberately never written to. `DECISIONS.md` §Q1 ships the documented
    // fallback — a day is honoured if you trained, or if you trained the day before — and defers the
    // picker, because asking a user to declare a schedule before they have trained once is the setup
    // ceremony this product refuses everywhere else. Versioned by effective date so that adding the
    // picker later cannot retroactively rewrite last month's streak.
    """
    CREATE TABLE training_plan (
        id                    INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
        effective_from        TEXT    NOT NULL,
        days_mask             INTEGER NOT NULL,
        forgiveness_per_month INTEGER NOT NULL DEFAULT 2,
        CHECK (days_mask BETWEEN 0 AND 127),
        CHECK (forgiveness_per_month >= 0),
        CHECK (length(effective_from) = 10)
    )
    """,
    "CREATE UNIQUE INDEX idx_plan_from ON training_plan(effective_from)",

    // ── personal_record (§A.9) ───────────────────────────────────────────────────────────────────
    // A cache, explicitly: it holds nothing that cannot be recomputed, and rebuildPersonalRecords()
    // regenerates it from truth. It exists because the library index renders 最高 on every row and
    // because the record screen needs 自己最高 decided *inside* the finish transaction.
    """
    CREATE TABLE personal_record (
        routine_id   TEXT    NOT NULL,
        metric       TEXT    NOT NULL,
        value        REAL    NOT NULL,
        tiebreak     REAL    NOT NULL DEFAULT 0,
        session_id   INTEGER NOT NULL,
        achieved_at  INTEGER NOT NULL,
        local_date   TEXT    NOT NULL,
        PRIMARY KEY (routine_id, metric),
        FOREIGN KEY (routine_id) REFERENCES routine(id) ON DELETE CASCADE,
        FOREIGN KEY (session_id) REFERENCES session(id) ON DELETE CASCADE,
        CHECK (metric IN ('BEST_TIME','MOST_ROUNDS','MOST_REPS','MOST_VOLUME','HIGHEST_STEP'))
    )
    """,
    "CREATE INDEX idx_pr_recent ON personal_record(local_date DESC)",

    // ── exercise_meta (§A.10) ────────────────────────────────────────────────────────────────────
    // Key-value rather than one wide row, so a new key is a write and not a migration.
    "CREATE TABLE exercise_meta (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)",
)
