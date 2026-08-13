# 鍛錬 — Part 2: SQLite schema, migrations, repository, seed data

Read `00-plan.md` first. Merge decisions in its §2 override anything here.

Persistence only. Screens, layouts and navigation belong to the other parts.
Database file `exercise.db`, package `io.eddiegulay.tempo.gym.data`.

---

## 0. Platform baseline — read before writing any SQL

`minSdk = 29` fixes the SQLite floor at **3.28.0** (Android 10 ships 3.28; API 28 was 3.22). Every
statement below is checked against it.

| Feature | Needs | Verdict |
|---|---|---|
| Partial indices (`WHERE` on `CREATE INDEX`) | 3.8.0 | **use** |
| Recursive CTEs | 3.8.3 | available, mostly avoided (§D) |
| Upsert `ON CONFLICT … DO UPDATE` | 3.24 | **use** |
| Window functions (`NTILE`, `LAG`) | 3.25 | available, deliberately avoided (§D.3) |
| `ALTER TABLE … RENAME COLUMN` | 3.25 | **use** in migrations |
| Generated columns | 3.31 | **forbidden** |
| `RETURNING` | 3.35 | **forbidden** — use `insertOrThrow`'s rowid |
| `DROP COLUMN` | 3.35 | **forbidden** — migrate by table rebuild |
| `sqrt()` / `pow()` | 3.35 + compile flag | **absent** — this is why monotony is Kotlin (§D.6) |
| `STRICT` tables | 3.37 | **forbidden** |
| JSON1 (`json_extract`, `->>`) | enabled from API 30 | **forbidden at 29** — an independent reason not to use blob columns (§A.0.2) |

Two more platform facts that shape the schema:

- **`PRAGMA foreign_keys` is OFF by default.** Enable it in `onConfigure` via
  `db.setForeignKeyConstraintsEnabled(true)`. It cannot be set inside a transaction, which is exactly
  why `onConfigure` and not `onOpen`.
- **SQLite's `date()` / `strftime()` are UTC.** A session started 23:30 local in UTC+9 lands on the
  previous day if you bucket in SQL, and `strftime('%W')` uses non-ISO week numbering. **All calendar
  bucketing is done in Kotlin at write time and stored** (`local_date`, `local_week_start`). Not an
  optimisation — a correctness requirement, and the most common bug in fitness-history code.

---

## A. Schema

`SCHEMA_VERSION` is derived from the migration list (§B.1), never hand-maintained.

### A.0 Six decisions, decided

**1. Built-in routines live in the DB, seeded — not as Kotlin constants.** Design §7.3 said constants,
right for a JSON world, wrong here: a `session` needs a **foreign key** to what it performed, and a
session pointing at a Kotlin `val` has no referential integrity — nothing stops a later app version
editing that constant and silently re-interpreting a March session. The home list joins routines
against session aggregates and PRs in **one query**; against a Kotlin list that is N+1 or a Kotlin-side
join over the whole session table. And copy-on-write user edits need both kinds to be the same shape.
`BuiltInCatalog.kt` survives as the **seed source of truth** — reviewable in a diff, written into the
DB by the seeder.

**2. Station results are normalised rows, not a JSON blob.** The blob is tempting — one write per
session, no join — and loses on every read the feature performs. §7.4's headline metric is
`Σ reps × difficulty` over *all history*; with a blob that is "deserialize 500 JSON documents on every
chart render." Per-exercise bests are `GROUP BY exercise_id`, impossible without rows. And
`json_extract` is **not available at API 29**, so you cannot even push extraction into SQL as a
compromise. Volume is trivial: 12–40 rows per session, 500 sessions ≈ 20k rows.

**3. A user's edit to a built-in is copy-on-write into a new user routine.** Editing 七分間 creates a
new `routine` row with `built_in = 0` and `derived_from_routine_id = 'r_seven_minute'`. The built-in
stays pristine, so a later app version can update the shipped preset without fighting the user's edit;
§6's 写して作る is *literally the same operation*, so there is one code path; and the copy is listed
under 自分の型 where the user will look for it. The UI performs the copy silently on first edit of a
built-in and adopts the returned id.

**4. Routine versioning: an immutable `routine_version` snapshot, referenced by the session.** This is
the March/April trap. `routine` holds identity and `head_version_id`; `routine_version` +
`routine_station` hold an **immutable** parameter set. Any edit inserts a new version and repoints the
head. `session.routine_version_id` pins the exact shape performed, forever. Versions are never updated
and never deleted (the FK would refuse anyway).
*Rejected alternative* — freezing a JSON snapshot onto each session: correct, but duplicates the same
12 stations across 200 sessions and re-introduces decision 2's problem for the breakdown screen.

**5. Anything that feeds a metric is frozen onto the result row at write time.**
`session_result.difficulty_coef`, `session_result.volume_units`, `session.rating_cr10`. If v1.4 retunes
push-up difficulty from 1.0 to 0.9, or remaps きつい from CR10 9 to 8, **every historical chart must not
move**. The library row is the current truth for *new* work; the result row is the historical truth for
*past* work. Same principle as version-pinning, one level down.

**6. Preferences go to DataStore; only data-layer state goes in the DB.** See `01-shell.md` §A.10. What
*does* go in the DB is `exercise_meta`: the monotonic clock high-water mark, the seed catalog version,
and the live-session pointer — all of which must be transactionally consistent with the rows they
describe. *What would change this:* if a preference ever has to be read atomically with session data
(a per-routine auto-advance override), it moves into the DB as a `routine_pref` table.

### A.1 `exercise`

Read-only from the user's perspective in Phase 1–2. Never deleted (FKs from `routine_station` and
`session_result`); retired entries get `archived = 1`. Also loaded once into an in-memory map at
repository construction so `ExerciseCatalog.byId()` is synchronous (`00-plan.md` §2 row 6).

```sql
CREATE TABLE exercise (
    id                TEXT    NOT NULL PRIMARY KEY,   -- 'pushup' — stable, never renumbered
    name_ja           TEXT    NOT NULL,               -- 腕立て伏せ
    name_en           TEXT    NOT NULL,               -- contentDescription + search
    pattern           TEXT    NOT NULL,
    seconds_per_rep   REAL    NOT NULL,               -- pacer estimate ONLY; for isometrics, the
                                                      -- seconds that count as one volume unit
    difficulty        REAL    NOT NULL,               -- volume coefficient; CURRENT value only
    is_isometric      INTEGER NOT NULL DEFAULT 0,     -- plank/wall-sit: DURATION prescriptions only
    cue               TEXT,                           -- one line, shown on the rest slide
    catalog_version   INTEGER NOT NULL,
    archived          INTEGER NOT NULL DEFAULT 0,
    CHECK (pattern IN ('H_PUSH','V_PULL','SQUAT','HINGE','CORE','LOCOMOTION','PLYO')),
    CHECK (seconds_per_rep > 0),
    CHECK (difficulty > 0),
    CHECK (is_isometric IN (0,1)),
    CHECK (archived IN (0,1))
);

CREATE INDEX idx_exercise_pattern ON exercise(pattern, archived);
```

`idx_exercise_pattern` serves §6's adjacent-pattern warning and the builder's picker grouped by
pattern. Without it the picker table-scans on every keystroke.

*Note on the enum:* §7.1 lists no horizontal-pull pattern, so 斜め懸垂 (ring row) is seeded as
`V_PULL`. Correct for the one thing `pattern` is used for — adjacency warning — because a ring row and
a pull-up load the same lats and *should* warn when stacked. Do not add `H_PULL` unless a second
consumer appears.

### A.2 `routine` — identity

```sql
CREATE TABLE routine (
    id                      TEXT    NOT NULL PRIMARY KEY,  -- 'r_seven_minute' seeded, 'u_<uuid>' user
    head_version_id         INTEGER NOT NULL,              -- the version shown and launched today
    built_in                INTEGER NOT NULL,
    tier                    TEXT,                          -- '入門' | '中級' | '上級' | NULL
    derived_from_routine_id TEXT,                          -- copy-on-write / 写して作る provenance
    scaled_from_routine_id  TEXT,                          -- 「やさしい」 variants
    origin                  TEXT,                          -- 'CrossFit.com, 2004-12-29'
    catalog_version         INTEGER NOT NULL DEFAULT 0,    -- 0 for user routines
    sort_order              INTEGER NOT NULL DEFAULT 0,
    favourite               INTEGER NOT NULL DEFAULT 0,
    created_at              INTEGER NOT NULL,              -- guarded epoch millis (§E.4)
    archived_at             INTEGER,                       -- NULL = live; soft delete
    FOREIGN KEY (head_version_id)         REFERENCES routine_version(id) ON DELETE RESTRICT,
    FOREIGN KEY (derived_from_routine_id) REFERENCES routine(id)         ON DELETE SET NULL,
    FOREIGN KEY (scaled_from_routine_id)  REFERENCES routine(id)         ON DELETE SET NULL,
    CHECK (built_in IN (0,1)),
    CHECK (favourite IN (0,1)),
    CHECK (tier IS NULL OR tier IN ('入門','中級','上級'))
);

CREATE INDEX idx_routine_section ON routine(archived_at, built_in, sort_order);
```

`idx_routine_section` serves the library index in one ordered scan — `WHERE archived_at IS NULL ORDER
BY built_in, sort_order` produces the 型 block then the 自分の型 block with no filesort.

`head_version_id` and `routine_version.routine_id` are mutually referential. SQLite tolerates this
because FKs are checked per-statement; wrap both inserts in a transaction with
`PRAGMA defer_foreign_keys = ON`. One line in `saveRoutine`.

### A.3 `routine_version` — the immutable snapshot

```sql
CREATE TABLE routine_version (
    id                      INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    routine_id              TEXT    NOT NULL,
    version_number          INTEGER NOT NULL,        -- 1, 2, 3 … per routine
    name                    TEXT    NOT NULL,        -- versioned: a rename must not rewrite history
    engine                  TEXT    NOT NULL,
    rounds                  INTEGER,                 -- NULL for AMRAP / FOR_TIME
    time_cap_sec            INTEGER,                 -- AMRAP / EMOM total; NULL otherwise
    interval_sec            INTEGER,                 -- EMOM window; NULL otherwise
    rest_between_stations   INTEGER NOT NULL DEFAULT 0,
    rest_between_rounds     INTEGER NOT NULL DEFAULT 0,
    prepare_sec             INTEGER NOT NULL DEFAULT 5,
    progression_program_id  TEXT,                    -- FIXED_SETS only
    primary_metric          TEXT    NOT NULL,        -- which PR the card shows
    -- denormalised, computed once at version creation:
    station_count           INTEGER NOT NULL,
    est_duration_sec        INTEGER NOT NULL,
    est_total_reps          INTEGER NOT NULL,
    structural_hash         INTEGER NOT NULL,        -- content address; makes reseeding idempotent
    created_at              INTEGER NOT NULL,
    FOREIGN KEY (routine_id)             REFERENCES routine(id)             ON DELETE RESTRICT,
    FOREIGN KEY (progression_program_id) REFERENCES progression_program(id) ON DELETE RESTRICT,
    CHECK (engine IN ('INTERVAL_CIRCUIT','AMRAP','FOR_TIME','FOR_TIME_WITH_REST',
                      'EMOM','EMOM_ASCENDING','FIXED_SETS')),
    CHECK (primary_metric IN ('BEST_TIME','MOST_ROUNDS','MOST_REPS','MOST_VOLUME','HIGHEST_STEP')),
    CHECK (rest_between_stations >= 0 AND rest_between_rounds >= 0 AND prepare_sec >= 0),
    CHECK (rounds IS NULL OR rounds >= 1),
    CHECK (time_cap_sec IS NULL OR time_cap_sec > 0),
    CHECK (rounds IS NOT NULL OR time_cap_sec IS NOT NULL),   -- something must bound the session
    CHECK ((engine = 'FIXED_SETS') = (progression_program_id IS NOT NULL))
);

CREATE UNIQUE INDEX idx_version_routine_number ON routine_version(routine_id, version_number);
```

The denormalised `station_count` / `est_duration_sec` / `est_total_reps` are **not premature
optimisation**: the library card renders 「十二種目 ・ 三十秒 / 十秒 ・ 約七分」 for *every* row in a
scrolling list, and computing that from `routine_station` is an N+1. They are safe to denormalise
precisely because the version is immutable — there is no staleness window.

### A.4 `routine_station`

```sql
CREATE TABLE routine_station (
    routine_version_id INTEGER NOT NULL,
    position           INTEGER NOT NULL,        -- 0-based order within one round
    exercise_id        TEXT    NOT NULL,
    prescription_kind  TEXT    NOT NULL,        -- 'REPS' | 'DURATION' | 'MAX_EFFORT'
    prescribed_reps    INTEGER,
    prescribed_sec     INTEGER,
    note               TEXT,                    -- e.g. '一マイル' for マーフ's run legs
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
);
```

Two nullable columns plus a coherence `CHECK` beats a single `value` + `unit` pair, because the
database itself refuses `REPS` with a seconds value: a malformed builder draft fails at the write, not
three screens later in the timeline compiler. The composite PK **is** the ordering index — no separate
`CREATE INDEX`, and it serves both the detail read and the compiler's station walk. `ON DELETE CASCADE`
is the only cascade from a version, and is safe because a version is only ever deleted as garbage
collection of a never-performed draft (§B.4).

### A.5 Progression tables

Recon Ron's 18 steps, Armstrong's 5-day week, Pavel's 30-day cycle: all three are "a table of sets,
indexed by step or day, with an advancement rule."

```sql
CREATE TABLE progression_program (
    id              TEXT    NOT NULL PRIMARY KEY,      -- 'p_recon_ron', 'p_armstrong', 'p_fighter'
    name_ja         TEXT    NOT NULL,
    step_unit       TEXT    NOT NULL,                  -- 'STEP' | 'DAY'
    step_count      INTEGER NOT NULL,
    advance_rule    TEXT    NOT NULL,
    advance_param   INTEGER,                           -- sessions/weeks required to advance
    cycle_days      INTEGER,                           -- 7 Armstrong, 30 Pavel; NULL Recon Ron
    origin          TEXT    NOT NULL,
    note_ja         TEXT,
    catalog_version INTEGER NOT NULL,
    CHECK (step_unit IN ('STEP','DAY')),
    CHECK (advance_rule IN ('SESSIONS_COMPLETED','WEEKS_ELAPSED','ALL_SETS_MADE','MANUAL')),
    CHECK (step_count >= 1)
);

CREATE TABLE progression_step (
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    program_id  TEXT    NOT NULL,
    step_index  INTEGER NOT NULL,                      -- 1..step_count
    label_ja    TEXT,                                  -- '第一日' for day-based programs
    total_reps  INTEGER,                               -- the published total; asserted by test
    shape       TEXT NOT NULL DEFAULT 'FIXED',
    rest_sec    INTEGER NOT NULL DEFAULT 90,
    note_ja     TEXT,
    FOREIGN KEY (program_id) REFERENCES progression_program(id) ON DELETE RESTRICT,
    CHECK (step_index >= 1),
    CHECK (shape IN ('FIXED','MAX_EFFORT','PYRAMID','LADDER','GRIP_ROTATION'))
);

CREATE UNIQUE INDEX idx_prog_step ON progression_step(program_id, step_index);

CREATE TABLE progression_set (
    step_id    INTEGER NOT NULL,
    set_index  INTEGER NOT NULL,                       -- 1..5 for Recon Ron
    reps       INTEGER,                                -- NULL when the step's shape is MAX_EFFORT
    variant    TEXT,                                   -- 'OVERHAND'|'CLOSE'|'REVERSE' Armstrong day 3
    PRIMARY KEY (step_id, set_index),
    FOREIGN KEY (step_id) REFERENCES progression_step(id) ON DELETE CASCADE,
    CHECK (set_index >= 1)
);
```

`progression_step.total_reps` exists **only** so the regression test can be a plain SQL assertion
rather than a hand-maintained Kotlin fixture:

```sql
-- must return zero rows
SELECT s.step_index, s.total_reps, SUM(ps.reps) AS actual
FROM progression_step s JOIN progression_set ps ON ps.step_id = s.id
WHERE s.program_id = 'p_recon_ron'
GROUP BY s.id HAVING SUM(ps.reps) != s.total_reps;
```

Plus a second assertion that `total_reps = 24 + 2 * step_index` for every Recon Ron row.

```sql
CREATE TABLE progression_state (
    program_id         TEXT    NOT NULL PRIMARY KEY,
    current_step_index INTEGER NOT NULL DEFAULT 1,
    sessions_at_step   INTEGER NOT NULL DEFAULT 0,
    step_entered_at    INTEGER NOT NULL,               -- guarded millis; drives WEEKS_ELAPSED
    last_session_id    INTEGER,
    cycle_day          INTEGER NOT NULL DEFAULT 1,
    FOREIGN KEY (program_id)      REFERENCES progression_program(id) ON DELETE CASCADE,
    FOREIGN KEY (last_session_id) REFERENCES session(id)             ON DELETE SET NULL,
    CHECK (current_step_index >= 1 AND sessions_at_step >= 0)
);
```

`progression_step` / `progression_set` are replaced wholesale on reseed rather than versioned, because
no `session` FK points at a *set* — only at a `progression_step`, whose id is stable when keyed by
`(program_id, step_index)`. **If a shipped progression table's numbers are ever corrected, add a new
program id (`p_recon_ron_v2`); do not edit the rows** — editing retroactively changes what a past
session prescribed. Note this in the catalog file.

### A.6 `session`

The centre of the schema. Note how many columns exist purely so a query never has to guess.

```sql
CREATE TABLE session (
    id                  INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    routine_id          TEXT    NOT NULL,
    routine_version_id  INTEGER NOT NULL,        -- THE anti-March/April pin
    routine_name        TEXT    NOT NULL,        -- denormalised: the つづき banner survives a delete
    progression_step_id INTEGER,
    compiled_hash       INTEGER NOT NULL,        -- asserts the timeline recompiles identically

    started_at          INTEGER NOT NULL,        -- guarded wall-clock millis (§E.4)
    finished_at         INTEGER,                 -- NULL ⇒ live or stale; the resume pointer
    local_date          TEXT    NOT NULL,        -- 'YYYY-MM-DD', computed in Kotlin (§0)
    local_week_start    TEXT    NOT NULL,        -- ISO Monday
    tz_id               TEXT    NOT NULL,        -- audit: which zone produced local_date

    started_at_elapsed  INTEGER NOT NULL,        -- SystemClock.elapsedRealtime()
    paused_accum_ms     INTEGER NOT NULL DEFAULT 0,
    paused_at_elapsed   INTEGER,                 -- non-NULL ⇒ killed while paused
    paused_at_wall      INTEGER,
    boot_anchor_ms      INTEGER NOT NULL,        -- currentTimeMillis − elapsedRealtime
    last_write_elapsed  INTEGER NOT NULL,
    last_write_wall     INTEGER NOT NULL,
    active_ms           INTEGER NOT NULL DEFAULT 0,   -- excludes pauses; monotonic-derived
    wall_ms             INTEGER NOT NULL DEFAULT 0,   -- including pauses, for honesty

    rounds_planned      INTEGER,
    rounds_completed    INTEGER NOT NULL DEFAULT 0,
    stations_planned    INTEGER NOT NULL,        -- frozen: the 「二十種目中 八」 denominator
    stations_completed  INTEGER NOT NULL DEFAULT 0,
    complete            INTEGER NOT NULL DEFAULT 0,
    reached_time_cap    INTEGER NOT NULL DEFAULT 0,   -- AMRAP PR eligibility (§D.2)
    tier                TEXT,                          -- which scaled tier was performed

    rating              TEXT,                    -- 'EASY'|'JUST_RIGHT'|'HARD', nullable
    rating_cr10         INTEGER,                 -- FROZEN mapping 4/7/9

    clock_delta_ms      INTEGER NOT NULL DEFAULT 0,   -- guardedNow − systemNow at start
    created_by_version  INTEGER NOT NULL,        -- app versionCode, for forensics

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
);

CREATE INDEX idx_session_date        ON session(local_date DESC);
CREATE INDEX idx_session_routine     ON session(routine_id, started_at DESC);
CREATE INDEX idx_session_week        ON session(local_week_start);
CREATE UNIQUE INDEX idx_session_live ON session(finished_at) WHERE finished_at IS NULL;
```

| Index | Query it serves |
|---|---|
| `idx_session_date` | the history list paged by month, the month grid, the streak day-facts scan. Range scan on a `TEXT` date is exact because ISO-8601 sorts lexicographically. |
| `idx_session_routine` | the 「十四回」 times-done count, the 「前回より 二十二秒 速い」 baseline lookup, every per-routine PR query. |
| `idx_session_week` | the two weekly charts (§D.4). |
| `idx_session_live` | the resumable-session lookup. **Partial index, so it contains at most one row: it is both the fastest possible lookup and a hard constraint that two sessions can never be live at once.** Without it, a crash mid-session followed by a fresh start leaves two unfinished rows and the resume banner picks arbitrarily. The index makes that state unrepresentable. |

`stations_planned` is frozen on the row rather than derived, because open rep segments and skips can
change the denominator the user was shown. The chip says 「二十種目中 八」 — 20 must be the number that
was on screen.

### A.7 `session_result`

```sql
CREATE TABLE session_result (
    id                 INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    session_id         INTEGER NOT NULL,
    ordinal            INTEGER NOT NULL,          -- the timeline segment ordinal
    round_index        INTEGER NOT NULL,          -- 0-based; AMRAP's partial final round lives here
    station_order      INTEGER NOT NULL,
    phase              TEXT    NOT NULL,          -- 'WORK' | 'REPS' | 'REST'
    exercise_id        TEXT,                      -- NULL for rest segments

    prescription_kind  TEXT,
    prescribed_reps    INTEGER,
    prescribed_sec     INTEGER,

    actual_reps        INTEGER,                   -- from the 済 long-press wheel
    actual_duration_ms INTEGER NOT NULL DEFAULT 0,-- monotonic-derived, excludes pauses
    added_ms           INTEGER NOT NULL DEFAULT 0,-- ＋二十秒
    skipped            INTEGER NOT NULL DEFAULT 0,

    difficulty_coef    REAL    NOT NULL DEFAULT 0,-- FROZEN copy of exercise.difficulty
    volume_units       REAL    NOT NULL DEFAULT 0,-- FROZEN Σ contribution (§D.5)

    closed_at_wall     INTEGER NOT NULL,
    closed_at_elapsed  INTEGER NOT NULL,

    FOREIGN KEY (session_id)  REFERENCES session(id)  ON DELETE CASCADE,
    FOREIGN KEY (exercise_id) REFERENCES exercise(id) ON DELETE RESTRICT,
    CHECK (round_index >= 0 AND station_order >= 0),
    CHECK (skipped IN (0,1)),
    CHECK (actual_reps IS NULL OR actual_reps >= 0),
    CHECK (actual_duration_ms >= 0),
    CHECK (volume_units >= 0),
    CHECK (skipped = 0 OR (actual_reps IS NULL))   -- とばした means nothing was recorded
);

CREATE UNIQUE INDEX idx_result_session  ON session_result(session_id, ordinal);
CREATE INDEX        idx_result_exercise ON session_result(exercise_id, session_id);
```

`idx_result_session` serves the 内訳 breakdown, read in exactly the order it renders. Being **unique on
`(session_id, ordinal)`** makes a phase-transition write naturally idempotent under `INSERT OR REPLACE`
— a retry after a crash cannot double-count, which is what makes the player's per-transition
persistence safe. `idx_result_exercise` serves per-exercise bests and the weighted-volume aggregate.

`ON DELETE CASCADE` from `session` is correct: 記録せずに終える must remove the row and its children
atomically.

### A.8 `training_plan` — what "honoured the plan" means

Design §5.2 requires a plan and never defines one; without it the streak is unimplementable. This table
defines it, versioned by effective date so **changing your plan does not retroactively rewrite last
month's streak** — the same trap as routine versioning, one table over. **Needs product sign-off**
(`00-plan.md` §7 Q1).

```sql
CREATE TABLE training_plan (
    id                    INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    effective_from        TEXT    NOT NULL,   -- 'YYYY-MM-DD' local
    days_mask             INTEGER NOT NULL,   -- bit 0 = Monday … bit 6 = Sunday; 0 = no explicit plan
    forgiveness_per_month INTEGER NOT NULL DEFAULT 2,
    CHECK (days_mask BETWEEN 0 AND 127),
    CHECK (forgiveness_per_month >= 0),
    CHECK (length(effective_from) = 10)
);

CREATE UNIQUE INDEX idx_plan_from ON training_plan(effective_from);
```

**Default when the user has never set a plan** — the common case, and the one that must not fabricate an
infinite streak: no row exists, and the fold falls back to *a day is honoured if you trained, or if you
trained the day before* — one earned rest day after each training day. Plain, explainable in one
sentence of Japanese, and it delivers exactly §5.2's promise that a correctly-taken rest day extends
the streak. Forgiveness applies only to **missed planned days**; under the fallback there are no planned
days, so the streak simply ends at the second consecutive untrained day.

### A.9 `personal_record` — a derived cache, explicitly

```sql
CREATE TABLE personal_record (
    routine_id   TEXT    NOT NULL,
    metric       TEXT    NOT NULL,
    value        REAL    NOT NULL,           -- ms for BEST_TIME, count otherwise
    tiebreak     REAL    NOT NULL DEFAULT 0, -- AMRAP partial reps
    session_id   INTEGER NOT NULL,
    achieved_at  INTEGER NOT NULL,
    local_date   TEXT    NOT NULL,           -- drives "c.accent when set this month"
    PRIMARY KEY (routine_id, metric),
    FOREIGN KEY (routine_id) REFERENCES routine(id) ON DELETE CASCADE,
    FOREIGN KEY (session_id) REFERENCES session(id) ON DELETE CASCADE,
    CHECK (metric IN ('BEST_TIME','MOST_ROUNDS','MOST_REPS','MOST_VOLUME','HIGHEST_STEP'))
);

CREATE INDEX idx_pr_recent ON personal_record(local_date DESC);
```

This table holds **no information that cannot be recomputed**. It exists because the library index
renders 最高 on every row and the record screen needs the 自己最高 chip decided *inside* the finish
transaction. `rebuildPersonalRecords()` (§C.6) regenerates it from truth after any corruption recovery
or seed upgrade.

### A.10 `exercise_meta`

```sql
CREATE TABLE exercise_meta (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL);
```

Keys: `last_seen_millis` (monotonic guard), `seed_catalog_version`, `pr_rebuild_needed`,
`schema_created_by_version`. Key-value rather than one wide row, so a new key is a write, not a
migration.

---

## B. Migrations

### B.1 Structure

```kotlin
/**
 * One irreversible forward step of the schema. Migrations are pure DDL/DML; they must never read app
 * state, never touch SeedCatalog, and never be edited after they have shipped — an installed device
 * has already run them and will not run them again.
 */
internal interface Migration { val version: Int; fun apply(db: SQLiteDatabase) }

/** Ordered, gapless, append-only. */
internal val MIGRATIONS: List<Migration> = listOf(V1CreateSchema)
internal val SCHEMA_VERSION: Int = MIGRATIONS.last().version
```

Deriving `SCHEMA_VERSION` from the list makes it impossible to bump the version and forget the
migration — the bug class that ships an app which opens a v2 database with v1 tables.

### B.2 The helper

```kotlin
internal class ExerciseDb private constructor(
    private val appContext: Context,
    private val notifier: TableChangeNotifier,
) : SQLiteOpenHelper(appContext, DB_NAME, null, SCHEMA_VERSION, ExerciseCorruptionHandler(appContext)) {

    init { setWriteAheadLoggingEnabled(true) }

    // PRAGMA foreign_keys is off by default on Android and cannot be set inside a transaction,
    // which is exactly why this is onConfigure and not onOpen.
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        MIGRATIONS.forEach { it.apply(db) }
        Seeder.applyTo(db, fromCatalogVersion = 0)
    }

    /**
     * Only the migrations this database has not seen. Seeding is deliberately NOT done here — the
     * seed catalog can grow in an app update that changes no schema at all (a new preset needs no new
     * column), and such an update never calls onUpgrade.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        MIGRATIONS.filter { it.version in (oldVersion + 1)..newVersion }.forEach { it.apply(db) }
    }

    /**
     * A downgrade means an older APK was sideloaded over a newer database. The default behaviour
     * throws and crashes on boot, which for a *launcher* is unacceptable. Quarantine and start clean,
     * surfacing GymFault.StoreReset.
     */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SchemaDowngrade(oldVersion, newVersion)   // caught in openOrRecover()
    }

    override fun onOpen(db: SQLiteDatabase) {
        if (db.isReadOnly) return
        val installed = Meta.readInt(db, Meta.SEED_CATALOG_VERSION, default = 0)
        if (installed < SeedCatalog.VERSION) {
            Seeder.applyTo(db, fromCatalogVersion = installed)
            notifier.notify(TABLE_ROUTINE, TABLE_EXERCISE, TABLE_PROGRESSION)
        }
    }
}
```

**The split between `onUpgrade` (schema) and `onOpen` (seed) is the load-bearing decision here.**
Shipping バーバラ in Phase 2 requires no DDL. If seeding lived in `onUpgrade` you would have to bump
`SCHEMA_VERSION` with an empty migration just to get the data in — a lie about what changed, and
eventually a migration list full of no-ops. Two independent counters, `SCHEMA_VERSION` for structure
and `SeedCatalog.VERSION` for content, is the correct factoring.

### B.3 The seeder

```kotlin
/**
 * Applies every seed generation newer than [fromCatalogVersion], in one transaction. Three rules, and
 * the whole design hangs off them:
 *
 *  1. Exercises are upserted by id. A new one inserts; an existing one has its *descriptive* columns
 *     refreshed (name, cue, pattern) but the row is never deleted — session_result and
 *     routine_station hold FKs into it. Retiring sets archived = 1. Updating `difficulty` is safe
 *     precisely because every historical result froze its own copy (§A.0.5), so a retuned coefficient
 *     changes future volume only.
 *
 *  2. A changed built-in routine gets a NEW routine_version, never an UPDATE; the routine row's
 *     head_version_id is repointed. Every session performed before the update still references the
 *     old version and still describes what the user actually did. This is the March/April guarantee,
 *     and it costs one INSERT.
 *
 *  3. User routines are never touched. They are built_in = 0 and every statement is scoped by
 *     built_in = 1, so an upgrade to the shipped 七分間 flows to the preset and leaves the user's
 *     copy-on-write edit exactly as it was.
 */
internal object Seeder {
    fun applyTo(db: SQLiteDatabase, fromCatalogVersion: Int) { /* one transaction, four passes */ }

    /** Content-addressed: identical stations + parameters produce no new version, so a no-op reseed is free. */
    private fun upsertBuiltInRoutine(db: SQLiteDatabase, seed: RoutineSeed) {
        if (queryHeadStructuralHash(db, seed.id) == seed.structuralHash()) return
        val versionId = insertVersion(db, seed)
        insertStations(db, versionId, seed.stations)
        upsertRoutineRow(db, seed, headVersionId = versionId)
    }
}
```

The hash check makes reseeding **idempotent**, which matters because `onOpen` may run the seeder on a
database a previous crashed upgrade already half-seeded. Without it, every launch after a partial write
piles up identical versions.

### B.4 Garbage collection

A `routine_version` with no `session` rows that is not any routine's head is a discarded draft. Sweep
opportunistically on `onOpen`, bounded:

```sql
DELETE FROM routine_version
WHERE id NOT IN (SELECT head_version_id FROM routine)
  AND id NOT IN (SELECT DISTINCT routine_version_id FROM session)
  AND created_at < ?;   -- older than 7 days, so an in-flight builder draft is never swept
```

`routine_station` follows by cascade. Quarantined corrupt DB files (§E.6) are swept by the same pass
after 30 days, capped at one file.

---

## C. Repository API

### C.0 Faults

Per `00-plan.md` §2 row 4, `Loadable.Failed` widens to `TempoFault` and `faultCopy` gains gym branches,
so `FaultStrip`/`FaultPanel` render gym faults with zero new chrome.

```kotlin
sealed interface GymFault : TempoFault {
    /** The database was unreadable and has been quarantined. History is gone; say so, don't imply "none yet". */
    data object StoreCorrupt : GymFault
    /** Disk full, or the file is locked. Retrying is worth it. */
    data class StoreUnavailable(val cause: String?) : GymFault
    data object StoreFull : GymFault          // SQLiteFullException — distinct remedy: free space
    data object StoreReset : GymFault         // an older APK was installed over a newer database
    data object RoutineGone : GymFault
    data object SessionGone : GymFault
    data object Rejected : GymFault           // a CHECK failed — a malformed draft
    data class Unknown(val cause: String?) : GymFault
}

sealed interface GymWrite<out T> {
    data class Ok<T>(val value: T) : GymWrite<T>
    data class Failed(val fault: GymFault) : GymWrite<Nothing>
}
```

This is not decoration. Design §7.1: *"An unreadable `sessions.json` must never render as
記録はありません."* `StoreCorrupt` is what keeps that promise for SQLite.

### C.1 Reads

```kotlin
/** Everything GYM.HOME renders, in one read. Re-emits on any routine or session write. */
fun homeFeed(recentLimit: Int = 3, builtInPreview: Int = 4): Flow<Loadable<GymHomeFeed>>

/** The library index: built-in ∪ user, archived excluded, as list projections. */
val routines: StateFlow<Loadable<List<RoutineSummary>>>
fun usageCounts(): Flow<Map<String, Int>>
fun recentUsage(days: Int): Flow<Map<String, Int>>

/** One routine at its head version, stations resolved, plus PRs and the last attempts. */
fun routineDetail(routineId: String): Flow<Loadable<RoutineDetail>>
fun scaledTiers(routineId: String): Flow<List<RoutineSummary>>

/**
 * The exact shape a session performed, from session.routine_version_id. This is what the record
 * screen renders from history: a March session must describe March's routine even if it was
 * rewritten in April. Immutable by construction, so `suspend`, not `Flow`.
 */
suspend fun routineVersion(versionId: Long): Loadable<RoutineSnapshot>

/** Synchronous — backed by the in-memory map loaded at construction (00-plan §2 row 6). */
object ExerciseCatalog {
    fun all(): List<Exercise>
    fun byId(id: String): Exercise?
    fun ladder(exerciseId: String): List<Exercise>   // ordered easiest → hardest
    fun byPattern(): Map<Pattern, List<Exercise>>
}
```

### C.2 The live session

```kotlin
/**
 * A session interrupted by a quit, a crash, or process death. [ResumableSession.resumability] is
 * computed by the player's pure resumability() (03 §E.3) from the boot anchor and the age.
 * One-shot: read once at cold start and once on entering GYM.HOME, and idx_session_live guarantees
 * at most one candidate exists.
 */
suspend fun resumableSession(): Loadable<ResumableSession?>

/**
 * Opens a session and returns its id. Fails with GymFault.Rejected if one is already live — enforced
 * by idx_session_live, not by a check-then-act race. Stamps started_at from the guarded clock,
 * freezes stations_planned, pins routine_version_id and routine_name.
 */
suspend fun startSession(routineId: String, tier: String?, roundsPlanned: Int?): GymWrite<Long>

/**
 * Persists one completed segment. Called on every phase transition — a few writes per minute.
 * Idempotent under idx_result_session, so a retry after a crash cannot double-count.
 */
suspend fun recordSegment(sessionId: Long, r: SegmentResultDraft): GymWrite<Unit>

/** Clock columns only. Called on pause/resume so process death loses at most one phase. */
suspend fun checkpoint(sessionId: Long, c: PersistedClock, roundsCompleted: Int): GymWrite<Unit>

/**
 * Closes the session in ONE transaction: clock, completion flags, progression advance, PR evaluation,
 * and the previous-session baseline. A partial session is a first-class outcome, not a failure: it
 * finishes with complete = 0, is fully recorded, and is excluded only from PR eligibility.
 * @return everything the record screen needs, so it never queries after a finish.
 */
suspend fun finishSession(sessionId: Long, complete: Boolean): GymWrite<SessionOutcome>

/**
 * Records どうでしたか. Separate from finishSession because the rating is asked *after* the session is
 * saved and is optional — the record must survive its absence. Writes rating_cr10 from the frozen
 * 4/7/9 mapping and re-evaluates load-derived metrics.
 */
suspend fun rateSession(sessionId: Long, rating: Rating?): GymWrite<Unit>

suspend fun discardSession(sessionId: Long): GymWrite<Unit>
```

### C.3 History and records

```kotlin
/** Keyset-paged by (started_at, id) DESC. Never OFFSET — see 04 §3. */
fun history(cursor: Cursor?, limit: Int = 30): Flow<Loadable<List<SessionSummary>>>
fun attemptsForRoutine(routineId: String, limit: Int): Flow<Loadable<List<SessionSummary>>>
fun countForRoutine(routineId: String): Flow<Int>
suspend fun populatedMonths(): Loadable<List<YearMonth>>

/** Flow, not suspend — the rating is editable on this very screen. */
fun sessionDetail(sessionId: Long): Flow<Loadable<SessionDetail>>

fun monthLoad(month: YearMonth): Flow<Loadable<Map<LocalDate, DayLoad>>>
fun loadScale(days: Int = 90): Flow<LoadScale>          // quartile thresholds for the ink levels
fun streak(): Flow<Loadable<Streak>>
fun routineBests(): Flow<Loadable<List<RoutineBest>>>
fun movementBests(): Flow<Loadable<List<MovementBest>>>

/** Both charts in one read — they share a GROUP BY and splitting them would double the scan. */
fun weeklySeries(weeks: Int = 12): Flow<Loadable<List<WeekPoint>>>
fun volumeSeries(days: Int): Flow<Loadable<List<DayVolume>>>

/**
 * Foster load and 7-day monotony. Null until enough rated sessions exist — an unrated week has no
 * load, and inventing one puts a meaningless number on screen. ACWR is suppressed below 28 days.
 */
fun trainingLoad(): Flow<Loadable<TrainingLoad?>>
```

### C.4 Authoring

```kotlin
/**
 * A new routine inserts identity + version 1; an edit inserts a new version and repoints the head,
 * leaving every past session pinned to what it performed. Editing a built-in silently copies first
 * and returns the NEW id — the caller must adopt it, because the built-in is unchanged.
 */
suspend fun saveRoutine(draft: RoutineDraft): GymWrite<String>
suspend fun duplicateRoutine(routineId: String, newName: String): GymWrite<String>
/** Soft delete. Never hard: sessions hold an FK and history must stay readable. */
suspend fun archiveRoutine(routineId: String): GymWrite<Unit>
suspend fun restoreRoutine(routineId: String): GymWrite<Unit>
/** Offered only when countForRoutine == 0. */
suspend fun purgeRoutine(routineId: String): GymWrite<Unit>
suspend fun setFavourite(routineId: String, favourite: Boolean): GymWrite<Unit>
suspend fun touchRoutine(routineId: String)

fun progression(programId: String): Flow<Loadable<ProgressionState>>
/** Manual override — a user who knows they belong on step 9 should not grind to it. */
suspend fun setProgressionStep(programId: String, stepIndex: Int): GymWrite<Unit>

fun retry()
suspend fun rebuildPersonalRecords(): GymWrite<Unit>

/** The one settings write that lands in SQLite, because it is date-versioned (§A.8). */
suspend fun setTrainingPlan(daysMask: Int, forgivenessPerMonth: Int): GymWrite<Unit>
```

---

## D. The hard queries

### D.1 Streak with forgiveness — SQL for facts, Kotlin for the fold

```sql
SELECT local_date, COUNT(*) AS sessions, SUM(active_ms) AS active_ms, MAX(complete) AS any_complete
FROM session
WHERE finished_at IS NOT NULL
  AND local_date >= ?            -- today − 400 days; a longer streak is not worth a scan
GROUP BY local_date ORDER BY local_date DESC;

SELECT effective_from, days_mask, forgiveness_per_month FROM training_plan ORDER BY effective_from DESC;
```

**Kotlin does the walk**, deliberately — not a shortcut:

- The forgiveness budget is **stateful across the scan** (2 per calendar month, refreshed when the walk
  crosses a month boundary). In SQL that needs a recursive CTE carrying a running budget and a month
  key: writable, unreadable, effectively untestable.
- The plan is **date-versioned**, so the mask in force varies as the walk moves backwards. In SQL that
  is a correlated lookup per day.
- Design §13 puts streak-with-forgiveness in `app/src/test/` alongside `groupByApp` and
  `layoutTategaki`. A pure function over a `Set<LocalDate>` is trivially testable at every boundary; a
  recursive CTE is testable only against a real database on a device.

```kotlin
/**
 * 連続: consecutive days *the plan was honoured*, walking back from [today].
 *
 * A day is honoured when it was trained, or when nothing was planned for it. A missed planned day is
 * absorbed by that calendar month's forgiveness budget (default 2); the streak ends when the month's
 * budget is exhausted. Crossing into an earlier month refreshes the budget, which is what "two per
 * month" plainly means.
 *
 * With no plan on file the fallback is: honoured if trained, or if the day before was. One earned rest
 * day per training day — §5.2's promise, with no hidden state.
 *
 * Today itself never breaks a streak: an untrained today is "not yet", not "missed".
 */
fun currentStreak(
    today: LocalDate,
    trained: Set<LocalDate>,
    plans: List<PlanWindow>,
    maxLookbackDays: Int = 400,
): Streak {
    var day = today; var length = 0
    var monthKey = YearMonth.from(today)
    var budget = plans.inForce(today)?.forgivenessPerMonth ?: 0
    var forgiven = 0

    while (ChronoUnit.DAYS.between(day, today) < maxLookbackDays) {
        if (YearMonth.from(day) != monthKey) {
            monthKey = YearMonth.from(day)
            budget = plans.inForce(day)?.forgivenessPerMonth ?: 0
            forgiven = 0
        }
        val plan = plans.inForce(day)
        val planned = plan?.let { it.daysMask and (1 shl day.dayOfWeek.ordinal) != 0 }
            ?: (day in trained || day.minusDays(1) in trained)      // fallback rule
        val didTrain = day in trained

        when {
            didTrain -> length++
            !planned -> length++                                    // a rest day extends it
            day == today -> Unit                                    // today is not yet a miss
            forgiven < budget -> { forgiven++; length++ }           // absorbed
            else -> return Streak(length, forgivenThisMonth = forgiven, endedOn = day)
        }
        day = day.minusDays(1)
    }
    return Streak(length, forgiven, endedOn = null)
}
```

`today` comes from the repository's **monotonic-guarded** `lastSeen` high-water mark, never a raw
`LocalDate.now()` — a system clock wound backwards must not fabricate a streak.

### D.2 Per-routine personal bests

The eligibility predicates are the substance; the ordering is the easy part. **A PR requires
`complete = 1`** — a quit AMRAP is not a round count, a quit for-time is not a time, and a PR system
that rewards quitting early is worse than none.

**`FOR_TIME` / `FOR_TIME_WITH_REST` → fastest**

```sql
SELECT s.id, s.started_at, s.local_date, s.active_ms AS value
FROM session s
WHERE s.routine_id = ? AND s.finished_at IS NOT NULL AND s.complete = 1
ORDER BY s.active_ms ASC, s.started_at ASC LIMIT 1;
```

**`AMRAP` → most rounds, tiebroken on partial reps.** Only sessions that ran the full cap; stopping at
minute 12 of a 20-minute Cindy is not a rounds record.

```sql
SELECT s.id, s.started_at, s.local_date,
       s.rounds_completed AS value,
       (SELECT COALESCE(SUM(r.actual_reps), 0) FROM session_result r
         WHERE r.session_id = s.id AND r.round_index = s.rounds_completed AND r.skipped = 0) AS tiebreak
FROM session s
WHERE s.routine_id = ? AND s.finished_at IS NOT NULL AND s.reached_time_cap = 1
ORDER BY value DESC, tiebreak DESC, s.started_at ASC LIMIT 1;
```

**Most reps** — partials count; reps done are reps done.

```sql
SELECT s.id, s.started_at, s.local_date, CAST(SUM(COALESCE(r.actual_reps,0)) AS REAL) AS value
FROM session s JOIN session_result r ON r.session_id = s.id
WHERE s.routine_id = ? AND s.finished_at IS NOT NULL AND r.skipped = 0
GROUP BY s.id ORDER BY value DESC, s.started_at ASC LIMIT 1;
```

**Most weighted volume** — the §7.4-correct version, and the one to show when a routine mixes rep and
hold stations:

```sql
SELECT s.id, s.started_at, s.local_date, SUM(r.volume_units) AS value
FROM session s JOIN session_result r ON r.session_id = s.id
WHERE s.routine_id = ? AND s.finished_at IS NOT NULL AND r.skipped = 0
GROUP BY s.id ORDER BY value DESC, s.started_at ASC LIMIT 1;
```

**Highest step reached** (`FIXED_SETS`)

```sql
SELECT s.id, s.started_at, s.local_date, CAST(ps.step_index AS REAL) AS value
FROM session s JOIN progression_step ps ON ps.id = s.progression_step_id
WHERE s.routine_id = ? AND s.finished_at IS NOT NULL AND s.complete = 1
ORDER BY value DESC, s.started_at ASC LIMIT 1;
```

Ties break toward the **earlier** session — you set the record the first time you hit it. Dispatch on
`routine_version.primary_metric`. All five run inside `finishSession`'s transaction against the
just-written session only (`WHERE s.id = ?`, compared to the cached `personal_record.value`), so the
finish path is O(1). The full queries above are the **rebuild** path.

### D.3 Month-grid density buckets

Two decisions before the SQL.

**Density is active minutes, not Foster load.** §5.1 says "session load", but the rating is
*skippable* — so load is NULL for any unrated session, and `COALESCE(rating_cr10, 7)` would be
fabricating the user's perceived exertion to colour a dot. Active minutes are always known and never
invented. The grid is a glanceable "how much did I do"; minutes answer that honestly, and load stays
where it belongs, in the monotony maths where a real rating exists.

**Thresholds are computed once over a trailing 90 days and passed in as arguments, not recomputed per
query with `NTILE`.** Window functions exist at 3.28, but a per-query `NTILE` means March's dots
visibly change when April turns out to be heavy — the grid would rewrite its own history.

```sql
-- 1. thresholds, once per screen entry; percentiles taken in Kotlin from the returned column
SELECT SUM(active_ms) AS day_ms FROM session
WHERE finished_at IS NOT NULL AND local_date >= ?   -- today − 90d
GROUP BY local_date ORDER BY day_ms;

-- 2. the grid; ?2/?3/?4 are the 40th/70th/90th percentiles from step 1
SELECT d.local_date, d.day_ms,
       CASE WHEN d.day_ms >= ?4 THEN 3
            WHEN d.day_ms >= ?3 THEN 2
            WHEN d.day_ms >= ?2 THEN 1 ELSE 0 END AS bucket
FROM (
    SELECT local_date, SUM(active_ms) AS day_ms FROM session
    WHERE finished_at IS NOT NULL AND local_date >= ? AND local_date < ?
    GROUP BY local_date
) d;
```

Buckets 0–3 map to `c.ink @ 0.15 / 0.35 / 0.6 / 0.9`. Days absent from the result are rest days and
render blank. The subquery is required because SQLite 3.28 will not let a `CASE` in the select list
reference an alias defined in the same list. With fewer than 8 non-zero days, fall back to fixed
cutoffs and document it.

### D.4 Weekly series — one scan, both charts

```sql
SELECT local_week_start,
       COUNT(*)               AS sessions,
       SUM(active_ms) / 60000 AS active_minutes,
       SUM(complete)          AS complete_sessions
FROM session
WHERE finished_at IS NOT NULL AND local_week_start >= ?
GROUP BY local_week_start ORDER BY local_week_start ASC;
```

Weeks with zero sessions do not appear. **Zero-filling is Kotlin**, not a recursive-CTE date spine: the
chart needs a contiguous 12-element array, `generateSequence` is two lines, and a CTE spine here is
showing off. This is also why `local_week_start` is a stored column — see §0.

### D.5 Difficulty-weighted volume — and the hole in §7.4's formula

Design §7.4 states `Σ reps × coefficient`. **That counts a 60-second plank as zero** — `actual_reps` is
NULL for a `DURATION` station — which makes 七分間, one-third holds, look like half a workout. The fix
is `session_result.volume_units`, computed in Kotlin at write time by one pure function:

```kotlin
/**
 * One station's contribution to weighted volume.
 *
 * Rep work is `reps × difficulty`, exactly as specified. Isometric holds convert at the exercise's own
 * secondsPerRep, which for a hold is defined as "seconds that count as one unit" (10s), so a
 * 30-second plank scores 3 units × its coefficient. Both are frozen onto the row: a later retune of a
 * coefficient must never move a chart the user has already seen.
 */
fun volumeUnits(exercise: Exercise, actualReps: Int?, actualDurationMs: Long): Double =
    if (exercise.isIsometric) (actualDurationMs / 1000.0) / exercise.secondsPerRep * exercise.difficulty
    else (actualReps ?: 0) * exercise.difficulty.toDouble()
```

The production query is then a flat `SUM(r.volume_units) GROUP BY s.local_week_start`. The reps-only
form is retained as an auditable cross-check.

### D.6 Foster load and monotony — SQL for load, Kotlin for monotony

```sql
SELECT local_date,
       SUM(rating_cr10 * (active_ms / 60000.0)) AS load,
       COUNT(*) AS sessions,
       SUM(CASE WHEN rating_cr10 IS NULL THEN 1 ELSE 0 END) AS unrated
FROM session
WHERE finished_at IS NOT NULL AND local_date >= ? AND local_date <= ?
GROUP BY local_date ORDER BY local_date ASC;
```

Monotony has **no SQL alternative on this platform**:

1. SQLite has no `stdev()`, and Android's public `SQLiteDatabase` exposes no way to register a custom
   function (that API is hidden).
2. The algebraic workaround `sqrt(avg(x*x) − avg(x)²)` needs `sqrt()`, which arrived in **3.35** *and*
   requires `SQLITE_ENABLE_MATH_FUNCTIONS` at compile time. Neither holds at API 29.
3. It requires a **7-row date spine with rest days as explicit zeros**, and `GROUP BY` cannot produce
   rows for days with no data.

```kotlin
/**
 * Foster monotony: mean/sd of daily load over 7 days, rest days counted as zero — the zeros are the
 * point, since they are what makes an easy day easy.
 *
 * Population SD, matching Foster's original formulation. Returns null when any day in the window has
 * an unrated session (the window's load is then partly unknown and a number would be a guess) or when
 * SD is zero (seven identical days: undefined, and the honest answer is no answer).
 *
 * Above 2.0, surface 「同じ調子が続いています」.
 */
fun monotony(window: List<DailyLoad>): Double? {
    require(window.size == 7)
    if (window.any { it.unrated > 0 }) return null
    val loads = window.map { it.load }
    val mean = loads.average()
    val sd = sqrt(loads.sumOf { (it - mean).pow(2) } / loads.size)
    return if (sd < 1e-9) null else mean / sd
}
```

**ACWR** is the same story — a 7-day mean over a 28-day mean, both needing zero-filled spines. Kotlin,
from the same query widened to 28 days. Computed, **never displayed as risk** (§7.4): use it as a
ramp-rate governor with the boring, defensible 10%-per-week cap, and surface at most a soft nudge.
Suppress entirely below 28 days of history.

### D.7 The home index — one query, no N+1

```sql
SELECT r.id, r.built_in, r.tier, r.origin, r.favourite,
       v.id AS version_id, v.name, v.engine, v.rounds, v.time_cap_sec,
       v.rest_between_stations, v.rest_between_rounds,
       v.station_count, v.est_duration_sec, v.est_total_reps, v.primary_metric,
       agg.times_done, agg.last_started_at, agg.last_active_ms,
       pr.value AS pr_value, pr.tiebreak AS pr_tiebreak, pr.local_date AS pr_date
FROM routine r
JOIN routine_version v ON v.id = r.head_version_id
LEFT JOIN (
    SELECT routine_id, COUNT(*) AS times_done, MAX(started_at) AS last_started_at,
           active_ms AS last_active_ms          -- bare column, see note
      FROM session WHERE finished_at IS NOT NULL GROUP BY routine_id
) agg ON agg.routine_id = r.id
LEFT JOIN personal_record pr ON pr.routine_id = r.id AND pr.metric = v.primary_metric
WHERE r.archived_at IS NULL
ORDER BY r.built_in ASC, r.sort_order ASC;
```

**Note the bare `active_ms` beside `MAX(started_at)`.** In standard SQL that is undefined; **SQLite
guarantees it** — when a query contains exactly one `min()` or `max()` aggregate, bare columns take
their values from the row that produced the extremum. It is documented behaviour, it gives the
「前回より 二十二秒 速い」 baseline for free, and **it must be commented at the call site** or the next
reader will "fix" it into a correlated subquery.

よく使う is the same query with `AND s.local_date >= ?` (trailing 90 days) inside `agg` and
`ORDER BY times_done DESC LIMIT 3` — habit, not lifetime totals.

---

## E. Threading, consistency, integrity

### E.1 Single-writer discipline

`SQLiteDatabase` serialises statements internally, but that does not make a read-modify-write sequence
atomic. Both mechanisms are required:

1. **Transactions** for atomicity — `finishSession`, `saveRoutine`, `Seeder.applyTo`.
2. **One `Mutex` for all writes.** Transactions alone would be correct but would let two writers
   contend on the WAL write lock and surface `SQLiteDatabaseLockedException` under an unlucky
   interleaving (a phase-transition write racing a `rateSession`). The mutex makes "single writer"
   structural rather than probabilistic, and makes every write path reviewable.

```kotlin
private val writeLock = Mutex()

private suspend fun <T> write(body: (SQLiteDatabase) -> T): GymWrite<T> =
    withContext(Dispatchers.IO) {
        writeLock.withLock {
            runCatching { db.writableDatabase.transact(body) }
                .fold({ GymWrite.Ok(it) }, { GymWrite.Failed(it.toGymFault()) })
        }
    }
```

Reads take **no lock**. WAL gives readers a consistent snapshot concurrent with the writer, which is
the entire reason `setWriteAheadLoggingEnabled(true)` is on: history must render while a live session
writes phase transitions behind it.

### E.2 Where `Dispatchers.IO` goes

Exactly one place: inside `write { }` and `read { }` in the repository. Not in the ViewModel, not in
collectors — flows use `.flowOn(Dispatchers.IO)`, matching `CalendarRepository.events()` at line 134.
**No `Cursor` ever crosses a dispatcher boundary**; every query fully materialises its rows into data
classes inside `cursor.use { }` before returning. A `Cursor` handed to the main thread is a leaked
native resource waiting for a `StrictMode` violation.

One deliberate non-exception: nothing here is read synchronously at cold start. The gym index is never
the first frame — the user reaches it through a long-press — so there is no flash to prevent and no
justification for a `runBlocking`.

### E.3 Flow invalidation without Room

```kotlin
/**
 * In-process table-change bus. Every write announces the tables it touched; every read-flow re-runs
 * its query when a table it depends on is announced.
 *
 * Correct ONLY because Tempo is a single process: TempoNotificationListener declares no
 * android:process and shares the app's. If a second process is ever introduced, this bus goes silent
 * across it with NO ERROR — the flow would simply stop updating. Guard it with a comment and a test.
 */
internal class TableChangeNotifier {
    private val changes = MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = DROP_OLDEST)

    fun notify(vararg tables: String) = tables.forEach { changes.tryEmit(it) }

    fun <T> observing(vararg tables: String, query: suspend () -> T): Flow<T> =
        changes.filter { it in tables }
            .debounce(INVALIDATION_DEBOUNCE_MS)      // 80ms
            .onStart { emit("") }
            .map { query() }
            .flowOn(Dispatchers.IO)
}
```

The `debounce` is the medicine `CalendarRepository` already takes for sync-adapter bursts
(`CHANGE_DEBOUNCE_MS = 300`, line 39): a `finishSession` transaction touches `session`,
`session_result`, `personal_record` and `progression_state`, and a home list observing all four must
query once, not four times. 80ms rather than 300ms because these are our own writes and the user is
looking at the result of a tap.

**Announce inside the write helper, after `setTransactionSuccessful()` and after `endTransaction()`** —
never inside the transaction, or a reader wakes and takes a snapshot that does not yet include the
commit.

### E.4 The monotonic clock guard

Ported from `BlockadeRepository.kt:49`, and it protects *more* here.

```kotlin
/** Guarded "now": never earlier than the highest time we have previously observed. */
fun now(): Long = maxOf(System.currentTimeMillis(), lastSeen)
```

`lastSeen` lives in `exercise_meta.last_seen_millis`, advances on every write, and is read once at
construction. Three properties fall out:

1. **Streaks cannot be fabricated by winding the clock back.** `started_at` and `local_date` are
   stamped from `now()`, so a session can never be back-dated into a gap. Wind the clock to last
   Tuesday, work out, and the session still lands today.
2. **PRs cannot be fabricated at all.** `active_ms` and `actual_duration_ms` derive from
   `elapsedRealtime()` deltas, which are monotonic, include deep sleep, and are untouched by the system
   clock. A wall-clock change cannot make a for-time PR faster by one millisecond. A happy consequence
   of the player's timing architecture, not extra work.
3. **The anomaly is auditable.** `clock_delta_ms` records `now() − System.currentTimeMillis()` at
   start. Normally 0. Non-zero marks a session recorded during a clock excursion — exactly the
   breadcrumb you want when a user reports "my streak is wrong."

Forward jumps are **not** guarded and must not be: a user flying east genuinely crosses into a new day.
`tz_id` records which zone produced `local_date`, so history stays interpretable.

### E.5 The finish transaction

```kotlin
db.transact {
    // 1. Close the clock. active_ms/wall_ms come from the monotonic clock, not from now().
    execSQL("UPDATE session SET finished_at = ?, active_ms = ?, wall_ms = ?, rounds_completed = ?, " +
            "stations_completed = ?, complete = ?, reached_time_cap = ? " +
            "WHERE id = ? AND finished_at IS NULL", …)
    //    Zero rows updated ⇒ already finished (a double-tap, or a resumed screen). Abort — do not
    //    double-count into progression and PRs.

    // 2. Flush any segment results the player buffered but had not yet transitioned past.
    // 3. Advance progression_state if this session satisfied the program's advance_rule.
    // 4. Evaluate PRs against the cached personal_record rows (§D.2); upsert winners.
    // 5. Read the previous session for the 「前回より」 comparison — INSIDE the transaction, so the
    //    baseline is the snapshot that existed *before* this session landed.
}
// 6. AFTER commit: notifier.notify(SESSION, SESSION_RESULT, PERSONAL_RECORD, PROGRESSION_STATE)
```

Step 1's `AND finished_at IS NULL` guard is what makes the whole thing idempotent: `finishSession`
called twice — a real possibility when the quit sheet and the completion path race on a fast final
station — advances progression exactly once.

Step 5 inside the transaction is subtle and matters: read the previous session *after* commit and you
get your own session back as its own baseline, and the record screen renders 「前回より 0秒 速い」.

### E.6 Corrupt database

`SQLiteOpenHelper`'s default `DefaultDatabaseErrorHandler` **deletes the database file** on corruption.
For a launcher that silently vaporises a year of training history and then renders 記録はありません —
the exact failure §7.1 declares is the most damaging thing this feature could do.

```kotlin
/**
 * Corruption recovery that keeps the evidence and tells the truth.
 *
 * The default handler deletes the file, after which the history screen shows an empty state
 * indistinguishable from a new install. That is a lie the user will believe. Instead: quarantine the
 * file, let the next open create a clean one, and raise a durable flag so every Loadable read reports
 * GymFault.StoreCorrupt until the user acknowledges it.
 */
internal class ExerciseCorruptionHandler(private val appContext: Context) : DatabaseErrorHandler {
    override fun onCorruption(db: SQLiteDatabase) {
        val path = db.path
        runCatching { db.close() }
        val quarantine = File(appContext.filesDir, "exercise-corrupt-${System.currentTimeMillis()}.db")
        runCatching { File(path).renameTo(quarantine) }
            .onFailure { runCatching { File(path).delete() } }   // must recover even if rename fails
        // -wal and -shm siblings must go too, or the fresh database inherits a poisoned journal.
        runCatching { File("$path-wal").delete(); File("$path-shm").delete() }
        CorruptionFlag.raise(appContext)                          // DataStore — survives the DB being gone
    }
}
```

The flag lives in DataStore precisely because the database is the thing that failed. After recovery the
seeder repopulates the library and presets from `SeedCatalog`, so the user lands on a working 鍛錬 index
with their presets intact and an honest fault strip saying the *history* is gone — materially better
than a blank screen. `rebuildPersonalRecords()` runs on the first open after any recovery.

### E.7 Backup

Add to both `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`:

```xml
<include domain="database" path="exercise.db"/>
```

**Do not include `exercise.db-wal` or `exercise.db-shm`.** Backing up a WAL sibling without its exact
matching main file restores an inconsistent pair. Android's backup agent copies the file while the app
is not running, and an unclean WAL is recovered on next open — including only the main file is the
safe, standard choice. Note it in the XML comment, in the same explanatory register the existing file
uses.

---

## F. Seed data

`SeedCatalog.VERSION = 1` covers Phase 1 and Phase 2. **[added]** marks rows not in design §12;
**[estimate]** marks a `seconds_per_rep` this spec chose because §3.5 lists only six.

### F.1 `exercise`

| id | name_ja | name_en | pattern | sec/rep | difficulty | iso | cue |
|---|---|---|---|---|---|---|---|
| `pushup` | 腕立て伏せ | Push-up | H_PUSH | 2.0 | 1.0 | 0 | 体は一直線に |
| `knee_pushup` | 膝つき腕立て | Knee push-up | H_PUSH | 1.8 **[est]** | 0.5 | 0 | 腰を落とさない |
| `pushup_rotation` **[added]** | 回旋腕立て伏せ | Push-up with rotation | H_PUSH | 3.0 **[est]** | 1.2 | 0 | 上げた手を目で追う |
| `pullup` | 懸垂 | Pull-up | V_PULL | 3.0 | 2.0 | 0 | 肩を下げてから引く |
| `ring_row` | 斜め懸垂 | Ring row | V_PULL | 2.2 **[est]** | 0.8 | 0 | 体は板のまま |
| `squat` | スクワット | Air squat | SQUAT | 1.8 | 1.0 | 0 | 膝は爪先の向きに |
| `wall_sit` | 空気椅子 | Wall sit | SQUAT | 10.0 | 0.8 | **1** | 膝は九十度 |
| `lunge` | ランジ | Lunge | SQUAT | 2.2 **[est]** | 1.0 | 0 | 前膝を爪先より前に出さない |
| `step_up` | 踏み台昇降 | Step-up | SQUAT | 2.0 **[est]** | 0.8 | 0 | 足の裏全体で乗る |
| `situp` | 腹筋 | Sit-up | CORE | 1.7 | 1.0 | 0 | 反動を使わない |
| `crunch` **[added]** | クランチ | Crunch | CORE | 1.5 **[est]** | 0.8 | 0 | 腰は床につけたまま |
| `plank` | プランク | Plank | CORE | 10.0 | 1.0 | **1** | 肘は肩の真下に |
| `side_plank` | 横プランク | Side plank | CORE | 10.0 | 1.1 | **1** | 腰を落とさない |
| `dip` | ディップス | Triceps dip | H_PUSH | 2.5 | 1.5 | 0 | 肘は後ろへ |
| `burpee` | バーピー | Burpee | PLYO | 4.0 | 1.6 | 0 | 着地は柔らかく |
| `jumping_jack` | ジャンピングジャック | Jumping jacks | PLYO | 0.8 **[est]** | 0.5 | 0 | 肩の力を抜く |
| `high_knees` | もも上げ | High knees | LOCOMOTION | 10.0 | 0.7 | **1** | 腿は腰の高さまで |
| `run` | 走る | Run | LOCOMOTION | 10.0 | 1.0 | **1** | — |

Progression-ladder members needed by `GYM.LIBRARY.EXERCISE_DETAIL`: `wall_pushup` 壁腕立て 0.2,
`incline_pushup` 斜め腕立て 0.4, `feet_elevated_pushup` 足上げ腕立て 1.3, `archer_pushup`
アーチャー腕立て 1.6, `one_arm_pushup` 片手腕立て 2.5 — all `H_PUSH`, sharing `ladder_id = 'push'`.

**On isometric `seconds_per_rep = 10.0`:** for a hold this column is repurposed as "seconds that count
as one volume unit" (§D.5). Ten seconds is defensible — a 30s plank scores 3 — and it is the only place
the number is used for holds, since a hold's pacer is its prescribed duration. Document it on the
column.

### F.2 リーコン・ロン — the 18-step table

> **Provenance: LtCol Stanley J. Pasieka Jr., USMC (Ret.), "Over the Top on 'Dead Hang' Pull-Ups,"
> *Marine Corps Gazette*, December 1981.** Per `00-plan.md` §2 row 2, **verify this transcription
> against the source PDF before seeding, and record the check in this file's comment.** A second,
> plausible-looking table was generated during planning and rejected: it summed correctly and shared
> endpoints but lost the rotational increment pattern in the middle rows.

```
id            = 'p_recon_ron'          name_ja  = 'リーコン・ロン'
step_unit     = 'STEP'                 step_count = 18
advance_rule  = 'WEEKS_ELAPSED'        advance_param = 2        -- two weeks per step
cycle_days    = NULL
origin        = 'Pasieka, "Over the Top on Dead Hang Pull-Ups", Marine Corps Gazette, 1981-12'
note_ja       = '週に一日は三分の一の回数で'
```

Five sets, `rest_sec = 90`, exercise `pullup`. **Every row sums to its stated total, and every total is
exactly `24 + 2 × step_index`.**

| step | set 1 | set 2 | set 3 | set 4 | set 5 | total |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 7 | 6 | 5 | 4 | 4 | **26** |
| 2 | 8 | 6 | 5 | 5 | 4 | **28** |
| 3 | 8 | 7 | 5 | 5 | 5 | **30** |
| 4 | 9 | 7 | 6 | 5 | 5 | **32** |
| 5 | 10 | 7 | 6 | 6 | 5 | **34** |
| 6 | 10 | 8 | 6 | 6 | 6 | **36** |
| 7 | 11 | 8 | 7 | 6 | 6 | **38** |
| 8 | 12 | 8 | 7 | 7 | 6 | **40** |
| 9 | 12 | 9 | 7 | 7 | 7 | **42** |
| 10 | 13 | 9 | 8 | 7 | 7 | **44** |
| 11 | 14 | 9 | 8 | 8 | 7 | **46** |
| 12 | 14 | 10 | 8 | 8 | 8 | **48** |
| 13 | 15 | 10 | 9 | 8 | 8 | **50** |
| 14 | 16 | 10 | 9 | 9 | 8 | **52** |
| 15 | 16 | 11 | 9 | 9 | 9 | **54** |
| 16 | 17 | 11 | 10 | 9 | 9 | **56** |
| 17 | 18 | 11 | 10 | 10 | 9 | **58** |
| 18 | 18 | 12 | 10 | 10 | 10 | **60** |

The "one chosen day each week at one third volume" rule is a **session-level modifier**, not a table
row: the player multiplies each set by ⅓ (rounded down, minimum 1) when the user marks the day light.
Encoded as `note_ja` for now; if it needs enforcing, add `light_day_divisor` to `progression_program`
in schema v2.

### F.3 アームストロング — 5-day week

```
id = 'p_armstrong'   step_unit = 'DAY'   step_count = 5
advance_rule = 'SESSIONS_COMPLETED'   advance_param = 1   cycle_days = 7
origin = 'Maj. Charles Lewis Armstrong, USMC pull-up program'
```

Armstrong's days are **rules, not fixed numbers** — which is precisely why `progression_step.shape`
exists. Four of the five cannot be expressed as a rep table:

| day | label | shape | sets | rest | note_ja |
|---|---|---|---|---|---|
| 1 | 第一日 | `MAX_EFFORT` | 5 × max | 90s | 全力五組 |
| 2 | 第二日 | `PYRAMID` | 1,2,3,4,5… to failure | 10s × previous reps | 段を上げて限界まで |
| 3 | 第三日 | `GRIP_ROTATION` | 9 = 3 grips × 3 | 60s | 順手・狭手・逆手 |
| 4 | 第四日 | `MAX_EFFORT` | max sets at a chosen rep count | 60s | 一番きつい日 |
| 5 | 第五日 | `FIXED` (repeat) | — | — | 第四日をもう一度 |

Only day 3 gets `progression_set` rows — nine, carrying `variant` = OVERHAND ×3, CLOSE ×3, REVERSE ×3,
`reps` NULL and resolved at run time from the user's day-1 max. Days 1, 2, 4 carry zero set rows and
compile entirely from `shape`. **This is the schema earning its keep:** a design that modelled
progressions as a flat rep table could not represent four fifths of Armstrong. Day 5 stores the chosen
day in `progression_state.cycle_day`.

### F.4 ファイター懸垂 (Pavel) — **DO NOT SEED IN v1**

```
id = 'p_fighter'   step_unit = 'DAY'   step_count = 30   cycle_days = 7   shape = 'LADDER'
origin = 'Tsatsouline, Fighter Pull-Up Program'
```

The 30 daily ladders are structurally certain and numerically not. The programme is 5-on/2-off,
ladder-based, scaled to the trainee's current max — that much is well attested. The exact day-by-day
rung table varies between reproductions, and design §9 sets the standard: *"There is no documented
RECONDO push-up sequence with real numbers and this spec will not invent one."* The same restraint
applies.

Seed the `progression_program` row with `step_count = 30` and **zero `progression_step` rows**; no
routine references it, so nothing in the UI can reach an empty program. When a sourced transcription is
available it lands as `SeedCatalog.VERSION = 2` — **requiring no migration**, because §B.2 separates
the seed counter from the schema counter. That separation was designed for exactly this case.

### F.5 Built-in routines

**七分間** — `r_seven_minute`, `INTERVAL_CIRCUIT`, 入門, `catalog_version = 1`
`rounds = 1`, `rest_between_stations = 10`, `rest_between_rounds = 60`, `prepare_sec = 5`,
`primary_metric = MOST_VOLUME`,
`origin = "Klika & Jordan, ACSM's Health & Fitness Journal, 2013-05"`

Twelve stations at `DURATION 30` each, **in the published order** — design §9 forbids reordering,
because the sequence alternates total-body → lower → upper → core so opposing groups recover while
others work:

| pos | exercise | | pos | exercise |
|---:|---|---|---:|---|
| 0 | `jumping_jack` | | 6 | `dip` |
| 1 | `wall_sit` | | 7 | `plank` |
| 2 | `pushup` | | 8 | `high_knees` |
| 3 | `crunch` | | 9 | `lunge` |
| 4 | `step_up` | | 10 | `pushup_rotation` |
| 5 | `squat` | | 11 | `side_plank` |

Derived: `station_count = 12`, `est_duration_sec = 12×30 + 11×10 + 5 = 475` (**≈ 約八分**; per
`00-plan.md` §2 row 18, render the computed figure — "7-minute" is ACSM's own branding rounding).
§9's "1–3 circuits" is served by `rounds_planned` at launch, not by three seeded routines.

**タバタ** — `r_tabata`, `INTERVAL_CIRCUIT`, 中級, `primary_metric = MOST_VOLUME`
One station (`burpee`, DURATION 20), `rounds = 8`, `rest_between_stations = 0`,
`rest_between_rounds = 10`. `est_duration_sec = 8×20 + 7×10 + 5 = 235` — §9's "4:00" counts the
trailing rest; ours does not, which is the honest number. `origin = 'Tabata et al., Med Sci Sports
Exerc, 1996'`. The exercise choice is ours — the 1996 protocol was a cycle ergometer — so the routine
note says 種目は自由に.

**リーコン・ロン** — `r_recon_ron`, `FIXED_SETS`, 上級, `primary_metric = HIGHEST_STEP`,
`progression_program_id = 'p_recon_ron'`, one station `pullup` MAX_EFFORT (reps come from the step
table), `rest_between_stations = 90`, `rounds = 5`.

**Phase 2, `catalog_version = 2`:**

| id | name | engine | structure | primary_metric | origin |
|---|---|---|---|---|---|
| `r_cindy` | シンディ | AMRAP | `pullup` 5 / `pushup` 10 / `squat` 15, cap 1200s | MOST_ROUNDS | CrossFit.com, 2004-12-29 |
| `r_cindy_scaled` | シンディ（やさしい） | AMRAP | `ring_row` 3 / `knee_pushup` 6 / `squat` 9, cap 720s, `scaled_from_routine_id = r_cindy` | MOST_ROUNDS | CrossFit official scaling |
| `r_chelsea` | チェルシー | EMOM | same 5/10/15, `rounds = 30`, `interval_sec = 60` | MOST_ROUNDS | CrossFit benchmark |
| `r_barbara` | バーバラ | FOR_TIME_WITH_REST | 20/30/40/50, `rounds = 5`, `rest_between_rounds = 180` | BEST_TIME | CrossFit benchmark |
| `r_murph` | マーフ | FOR_TIME | run / 100 / 200 / 300 / run | BEST_TIME | CrossFit, in memory of Lt. Michael Murphy |
| `r_death_by` | デス・バイ | EMOM_ASCENDING | `burpee`, +1 rep per minute until failure | MOST_ROUNDS | Stew Smith / CrossFit tradition |

**Cindy is 5/10/15, not 10/15/15** — design §9's correction, honoured.

マーフ's run legs would be `DURATION` with a NULL `prescribed_sec`, which the CHECK forbids. They are
therefore `MAX_EFFORT` with `note = '一マイル'`, and the player treats a `MAX_EFFORT` `LOCOMOTION`
station as an open-ended segment closed by 済. Fits the model with no schema change; comment it in the
catalog.
