# Seeded content — the database layer (`gym/data/`)

**Files surveyed:** 12   **User-visible literals:** 71 Japanese (+ 23 English `name_en` already stored)   **Non-visible JP literals:** 4

Every literal in this scope is a **row**, not a UI constant. Nothing here is swapped by a language
toggle: the seeds are compiled into the APK, written into `exercise.db` on first open, and thereafter
the *database* is the source of truth that the app reads. Two consequences shape everything below.

1. A toggle cannot change what is in a table. Content translation is a **seed + schema** problem with
   its own release cadence (`SeedCatalog.VERSION`), not a resource problem.
2. Some of these rows become **user data** the moment the user copies or performs them, and those
   copies are permanently outside the reach of any future seed.

---

## Q1 — What is already localised, and how big is the head start

### Which tables carry a localised-name column

| table | column(s) holding a name/label | languages present |
|---|---|---|
| `exercise` | `name_ja`, `name_en`, `cue` | **JA + EN** for the name; cue is JA only |
| `routine_version` | `name` | one column, JA only |
| `routine_station` | `note` | one column, JA only |
| `progression_program` | `name_ja`, `note_ja` | JA only (the `_ja` suffix is the whole schema) |
| `progression_step` | `label_ja`, `note_ja` | JA only |
| `session` | `routine_name` | JA only, and a **frozen historical copy** |
| `routine` | `tier` | JA only, and constrained by a CHECK (see Q6) |
| `personal_record`, `session_result`, `training_plan`, `progression_set`, `exercise_meta` | — | no user-visible text at all |

`Schema.kt:56-75` is the only table in the schema with a second language:

```
CREATE TABLE exercise (
    id                TEXT    NOT NULL PRIMARY KEY,
    name_ja           TEXT    NOT NULL,
    name_en           TEXT    NOT NULL,      -- Schema.kt:60
    ...
    cue               TEXT,
```

Every other name column is singular (`routine_version.name`, `Schema.kt:89`) or explicitly
Japanese-suffixed (`progression_program.name_ja`, `Schema.kt:182`; `progression_step.label_ja`,
`Schema.kt:201`).

### What is actually SELECTed today

`GymStore.kt:946-952` — the exercise read, and it **does** project `name_en`:

```
SELECT id, name_ja, name_en, pattern, seconds_per_rep, difficulty, is_isometric, cue,
       ladder_id, catalog_version, archived
FROM exercise
```

It is bound to the model at `GymStore.kt:957` (`nameEn = c.getString(2)`), so the English name is in
memory for every exercise, on every launch, already.

**How large the head start is — precisely.** English exercise names are stored, read, and reach the
model. They are **never rendered**. The only consumer in the entire app is search matching:

- `LibraryFilters.kt:167` — `foldKana(exercise.nameJa).contains(needle) || foldKana(exercise.nameEn).contains(needle)`

Every *display* path in the app uses `nameJa` and nothing else — the exercise index, the station
picker, the detail screen, all five player pages, the record breakdown, the builder (23 call sites
outside `gym/data/`, e.g. `ExerciseIndexScreen.kt:187`, `StationPickerScreen.kt:547`,
`session/WorkPage.kt:54`, `ui/gym/RecordSummary.kt:444`). Inside this scope,
`GymStore.kt:1465`, `:1507` and `:1514` build `MovementBest.exerciseName` from `nameJa` too.

So: **23 of the 23 built-in exercise names are already translated to English and already loaded into
memory; zero of them are ever shown to a user.** Rendering them is a read-site change, not a data
change. That is the single largest piece of ready work in this scope, and it is limited to exercise
*names* — the 17 `cue` strings, all 9 routine names, all 3 programme names, all 5 step labels and
every note have no English at all and are net-new content.

---

## Q2 — Every column holding user-visible text, table by table

| table.column | seeded | user-authored | notes |
|---|---|---|---|
| `exercise.name_ja` | ✅ | ❌ | No write path to `exercise` exists outside `Seeder`. 100 % translatable. |
| `exercise.name_en` | ✅ | ❌ | Same. Populated, never displayed. |
| `exercise.cue` | ✅ | ❌ | 17 of 23 rows non-null. Spoken-adjacent: read by TalkBack via `contentDescription` on the player pages. |
| `routine_version.name` | ✅ (9 built-ins) | ✅ (builder + duplicate) | **The hard case.** Same column, both populations, distinguished only by joining to `routine.built_in`. |
| `routine_station.note` | ✅ (4 rows) | ⚠️ carried, never authored | The picker "cannot author one" (`StationPickerScreen.kt:225-236`); it only *carries* an existing note across an edit. So seeded notes migrate into user rows verbatim. |
| `progression_program.name_ja` | ✅ | ❌ | 3 rows. No write path. |
| `progression_program.note_ja` | ✅ | ❌ | 1 non-null row. |
| `progression_step.label_ja` | ✅ | ❌ | 5 non-null rows (Armstrong's days). |
| `progression_step.note_ja` | ✅ | ❌ | 5 non-null rows. |
| `session.routine_name` | ➖ | ➖ | Neither: it is a **denormalised snapshot** taken at `startSession` (`GymStore.kt:341`) so the resume banner survives the routine's deletion. Whatever the routine was called *that day*, in whatever language, is frozen on the row forever. |
| `routine.origin` | ✅ | ⚠️ inherited | Latin-script bibliographic citations ("Klika & Jordan, ACSM's Health & Fitness Journal, 2013-05"). Rendered on the detail page (`LibraryDetailScreen.kt:839-847`). Copied onto user routines at `GymStore.kt:799` and `:832`. Almost certainly **must not** be translated — a citation is a citation. |
| `routine.tier` | ✅ | ⚠️ inherited | Japanese *values* — see Q6. |
| `exercise_meta.key` / `.value` | machine | machine | ASCII keys only (`Meta.kt:20-29`, plus `"last_touched_$routineId"` at `GymStore.kt:900`). Never rendered. |

### Where the "both" column bites

`routine_version.name` is written from three places:

- `Seeder.kt:197` — `put("name", seed.name)`, only ever under `built_in = 1`
- `GymStore.kt:1672` — `put("name", draft.name)`, the builder's free-text field
- `GymStore.kt:822` — `insertVersion(this, targetId, snapshot.toDraft(newName))`, the duplicate path

The duplicate path is the worst of the three, because the name it writes is **composed in Japanese by
the app**: `LibraryFilters.kt:294` builds `「七分間 の写し」`, then `「七分間 の写し二」`, and
`GymViewModel.kt:673` hands that string to the store, which persists it. A user who duplicates a
routine while the app is in Japanese owns a row named 「七分間 の写し」 for the rest of the install's
life; switching to English cannot touch it, and re-seeding never will (`built_in = 0`).

---

## Q3 — How the seeder decides to write

### Exercises: unconditional overwrite of descriptive columns, but only for new generations

`Seeder.kt:63-68`:

```
INSERT INTO exercise
    (id, name_ja, name_en, pattern, seconds_per_rep, difficulty, is_isometric, cue,
     ladder_id, catalog_version, archived)
VALUES (?,?,?,?,?,?,?,?,?,?,0)
ON CONFLICT(id) DO UPDATE SET
    name_ja = excluded.name_ja,
    name_en = excluded.name_en,
    ...
```

**Does re-seeding overwrite a row the user edited?** For `exercise`, the question does not arise:
there is no user edit path to that table anywhere in the app. Every exercise row is the app's, so the
overwrite is safe and is exactly the mechanism a translation update would ride on.

But the upsert only ever *sees* rows the generation filter emits. `Seeder.kt:39-41` returns early if
`fromCatalogVersion >= SeedCatalog.VERSION`, and `SeedCatalog.planFrom` (`BuiltInCatalog.kt:154-158`)
filters with strict `>`:

```
exercises = exercises.filter { it.catalogVersion > fromCatalogVersion },
```

Every current exercise carries the default `catalogVersion = 1` (`BuiltInCatalog.kt:43`). So on an
existing install at generation 2, **bumping `SeedCatalog.VERSION` to 3 alone ships nothing**: the
exercise rows are still stamped 1 and `planFrom(2)` skips them. To deliver translations for existing
rows you must bump *both* `SeedCatalog.VERSION` **and** the `catalogVersion` on every row you want
rewritten. That is a documented-but-easy-to-miss property; `SeedUpgradeTest` pins the filter.

### Routines: content-addressed, so a rename creates a new version

`Seeder.kt:180-182`:

```
private fun upsertBuiltInRoutine(db: SQLiteDatabase, seed: RoutineSeed) {
    val hash = seed.structuralHash()
    if (queryHeadStructuralHash(db, seed.id) == hash) return
```

and the hash includes the **name** and every station **note** (`GymMath.kt:398-408`). Change a
built-in's name from シンディ to "Cindy" and the seeder inserts a whole new `routine_version` row and
repoints `routine.head_version_id` via `SQL_REPOINT_BUILT_IN` (`Seeder.kt:316-321`), scoped
`WHERE id = ? AND built_in = 1`. Historic sessions keep pointing at the old version, so **a March
session keeps rendering the March-language name** — by design (that is the March/April guarantee), but
it means history is permanently mixed-language after any content translation.

`SQL_REPOINT_BUILT_IN` names neither `favourite` nor `archived_at`, so a translation bump cannot
resurrect a built-in the user archived. `SQL_ENSURE_PROGRESSION_STATE` (`Seeder.kt:330-335`) is
`INSERT OR IGNORE`, so it cannot reset the user's rung.

### A third language, on existing installs

If a future seed adds e.g. `name_fr`:

- fresh installs get everything: `onCreate` runs `Seeder.applyTo(db, fromCatalogVersion = 0)` (`ExerciseDb.kt:144`).
- existing installs need **two counters to move**: `SCHEMA_VERSION` (so `onUpgrade` adds the column,
  `ExerciseDb.kt:154-156`) *and* `SeedCatalog.VERSION` plus per-row `catalogVersion` (so `onOpen`
  actually writes values into it, `ExerciseDb.kt:166-174`). Seeding is deliberately **not** done in
  `onUpgrade`, so a migration alone leaves every existing row with the column's default and no data.
- user-authored `routine_version.name` rows get nothing, in every language, forever. There is no
  candidate value to write.

---

## Q4 — Migrations: what an additional language column can and cannot do

SQLite 3.28 at `minSdk 29`. The floor is documented at `Schema.kt:3-20` and the exclusions are real:
no `STRICT` (3.37), no `RETURNING` (3.35), no `DROP COLUMN` (3.35), no generated columns (3.31), no
`sqrt()`, **no JSON1**. What is available: partial indices, upsert, expression indices, and
`ALTER TABLE … RENAME COLUMN` (3.25).

Today `MIGRATIONS` holds exactly one entry (`Migrations.kt:27`), `SCHEMA_VERSION` is derived from it
(`Migrations.kt:42`), and a migration is contractually immutable once shipped (`Migrations.kt:5-12`).

**What a per-language-column migration would have to look like:**

```kotlin
internal object V2AddFrenchNames : Migration {
    override val version = 2
    override fun apply(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE exercise ADD COLUMN name_fr TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE exercise ADD COLUMN cue_fr TEXT")
        db.execSQL("ALTER TABLE progression_program ADD COLUMN name_fr TEXT NOT NULL DEFAULT ''")
        // … and one per name-bearing column on five tables
    }
}
```

**What it could not do:**

- **Not `NOT NULL` without a default.** `ALTER TABLE ADD COLUMN` with `NOT NULL` requires a non-null
  DEFAULT. So a new language column is either nullable or defaulted to `''`, and the reader must then
  handle "column exists, value empty" — a state `name_en TEXT NOT NULL` does not have today.
- **Not reversible.** No `DROP COLUMN` at 3.28. Every language ever shipped is a permanent column on
  the exercise table for every user, forever. Removing one means the 12-step table-rebuild dance.
- **Not a CHECK change.** Adding, relaxing or removing a CHECK (the `tier` one in Q6, or a new
  `lang IN (…)`) requires recreating the table. For `routine` that means dropping and rebuilding a
  table that is mutually referential with `routine_version` and is the RESTRICT target of `session`
  and `personal_record` — with foreign keys enabled globally at `ExerciseDb.kt:137-139`. That is the
  single riskiest DDL in this codebase and should be treated as off the table.
- **Not a generated / computed "current language" column.** Not available at 3.31−.
- **Not a JSON translations blob.** No JSON1 at API 29 — this is exactly why `Schema.kt:11-12` says
  there is not a single blob column in the schema.
- **Not `routine_version.name`.** Even with columns per language, existing rows have only the one
  string that was typed or seeded; there is nothing to back-fill for user rows.

**The additive alternative that does not repeat per language** — a side table, which needs one
migration ever:

```
CREATE TABLE exercise_text (
    exercise_id TEXT NOT NULL,
    lang        TEXT NOT NULL,      -- BCP-47
    name        TEXT NOT NULL,
    cue         TEXT,
    PRIMARY KEY (exercise_id, lang),
    FOREIGN KEY (exercise_id) REFERENCES exercise(id) ON DELETE CASCADE
)
```

Adding a language then becomes rows (a content bump, `onOpen`), not DDL (`onUpgrade`) — which matters
because `onUpgrade` never fires for an app update that changes no schema (`ExerciseDb.kt:148-153`,
`BuiltInCatalog.kt:130-137`). `name_ja` / `name_en` would stay as the NOT NULL fallback pair so no
existing read breaks. This is a recommendation, not a finding; the finding is that the column-per-language
route is irreversible and touches five tables per language.

---

## Q5 — User-created content: rows that can never be translated

These render as-authored in every language, always:

| table.column | how it becomes user data |
|---|---|
| `routine_version.name` where the owning `routine.built_in = 0` | builder save (`GymStore.kt:1672`), copy-on-write edit of a built-in (`GymStore.kt:783-788` — editing a preset *forks* it), duplicate (`GymStore.kt:816-840`) |
| `routine_station.note` on a user routine's version | carried verbatim from the seeded original through copy-on-write (`GymStore.kt:1727`, `StationPickerScreen.kt:236`) |
| `session.routine_name` | frozen at `startSession` (`GymStore.kt:341`), for every session ever run |
| `routine.origin` on a user routine | inherited from the forked built-in (`GymStore.kt:799`, `:832`) |
| `routine.tier` on a user routine | inherited from the forked built-in (`GymStore.kt:798`, `:831`) |

Nothing else in the schema accepts user-authored text. There is no notes field on a session, no free
text on a result, no user-editable exercise. `session.rating` is an enum (`EASY`/`JUST_RIGHT`/`HARD`,
`Schema.kt:291`), not prose.

The copy-on-write rule at `GymStore.kt:783-788` is the one to internalise: **the moment a user edits a
built-in, the preset's Japanese name and notes become user data in a new row that the seeder is
forbidden to touch** (`built_in = 1` scope, `Seeder.kt:320`). A user who tidied up 七分間 in Japanese
last year will still see Japanese there next year in an English app, and that is correct behaviour —
it is their routine.

---

## Q6 — Japanese literals used *in SQL* — the highest-risk category

There is exactly one, and it is a CHECK constraint. `Schema.kt:141`:

```
CHECK (tier IS NULL OR tier IN ('入門','中級','上級'))
```

Three Japanese string literals baked into the DDL of the `routine` table. Everything about them is
load-bearing:

- The column stores the Japanese label itself. `Tier.storageValue` is defined as `= label`
  (`GymModels.kt:131`), and `GymModels.kt:117-119` says so explicitly: *"the stored value is the
  Japanese label, because that is what the schema's CHECK constraint spells."*
- Written by the seeder at `Seeder.kt:243` and `Seeder.kt:258` (`put("tier", seed.tier?.storageValue)`).
- Read back and matched **by string equality** at `GymStore.kt:1018` and `GymStore.kt:1066` via
  `Tier.fromStorage` (`GymModels.kt:134`: `entries.firstOrNull { it.storageValue == raw }`).
- Copied verbatim onto user routines at `GymStore.kt:798` and `GymStore.kt:831`.
- Pinned by test at `GymContractTest.kt:58-60`.

**Translate `Tier.label` and two things break at once.** Every write of a tier fails the CHECK with
`SQLiteConstraintException` → `GymFault.Rejected` (`DbSupport.kt:113`), and every read of an existing
row returns `null` from `fromStorage`, silently erasing the difficulty band from every card in the
library. Fixing it properly requires a table rebuild (see Q4) — which is why the label and the storage
value must be **decoupled first** (make `storageValue` return `BEGINNER`/`INTERMEDIATE`/`ADVANCED`,
migrate the rows, then let the label float). That is a schema change and it belongs on the critical
path of this migration, not after it.

**Exhaustive check of everything else.** I grepped every SQL string in the package for CJK, for
`COLLATE`, `LIKE`, `GLOB`, and for ORDER BY over a text column:

- Zero Japanese in any WHERE clause. Every `WHERE` in `GymStore.kt` compares ids (ASCII, `u_<uuid>` or
  `r_*`/`p_*` seed ids), ISO dates, integers, or enum `.name` values.
- Zero Japanese in any DEFAULT. Every column default in `Schema.kt` is `0`, `5`, `90`, `2`, `'FIXED'`
  or NULL.
- Every other CHECK enumerates ASCII enum names — `pattern IN ('H_PUSH',…)` (`Schema.kt:69`),
  `engine IN ('INTERVAL_CIRCUIT',…)` (`Schema.kt:107`), `prescription_kind IN ('REPS','DURATION','MAX_EFFORT')`
  (`Schema.kt:165`), `shape IN ('FIXED',…)` (`Schema.kt:208`), `rating IN ('EASY','JUST_RIGHT','HARD')`
  (`Schema.kt:291`), `metric IN ('BEST_TIME',…)` (`Schema.kt:394`).
- One inline literal in a projection, ASCII: `SUM(CASE WHEN prescription_kind = 'DURATION' …)`
  (`GymStore.kt:2107`).
- `session.tier` is a **different** tier from `routine.tier` and is ASCII: it stores
  `ScalingTier.name` (`GymViewModel.kt:795` passes `request.tier.name` → `EASY`/`RX`/`HARD`), has no
  CHECK (`Schema.kt:276`), and is compared as a string at `GymStore.kt:1228`
  (`AND s.tier = ?`). Its *labels* やさしい/基本/きつい live in `Timeline.kt:71-75` and are never stored.
  Do not conflate the two when translating.
- No `LIKE`, no `GLOB`, no `COLLATE` anywhere in the package. Search is done in Kotlin
  (`LibraryFilters.foldKana`), never in SQL.

---

## Q7 — Sort order

**Routines** are ordered by integers, not by text. `GymStore.kt:1005`:

```
ORDER BY r.built_in ASC, r.sort_order ASC
```

`sort_order` is seeded 0–8 in the catalogue's own curated sequence (`BuiltInCatalog.kt:396`, `:429`,
`:460`, `:499`, `:535`, `:561`, `:586`, `:626`, `:660`) and user routines get `MAX+1`
(`GymStore.kt:2011-2014`). This is served by `idx_routine_section` (`Schema.kt:145`). **English
ordering is a non-problem for routines** — the order is editorial and language-independent.

**Exercises** are ordered by `difficulty`, with a Japanese-string tiebreak. `ExerciseCatalogSource.kt:56`:

```kotlin
.sortedWith(compareBy({ it.difficulty }, { it.nameJa }))
```

and `ExerciseCatalogSource.kt:64-69` groups by `Pattern` declaration order (押す → 引く → …) and sorts
each group by `difficulty` only. Outside this scope, `ExerciseIndexScreen.kt:233` repeats the pattern:
`compareBy({ it.pattern.ordinal }, { it.difficulty }, { it.nameJa })`.

The `nameJa` tiebreak is Kotlin's `String` natural order — UTF-16 code-unit order, not kana collation
and not a `Collator`. It is a **tiebreak only**, reached when two exercises share a difficulty, so it
is barely observable today. But it is the one place ordering depends on the language of the content:
an English UI sorting a difficulty tie by `nameJa` orders visually-invisible strings. Switching it to
`nameEn` (or to a locale `Collator` over the displayed name) is a small change and belongs with the
read-site work in Q1.

**There is no kana reading column** anywhere in the schema — no `name_kana`, no `reading`, no sort key.
Nothing to migrate; also nothing to fall back on if proper Japanese collation is ever wanted.

Everything else sorts on time, position or number: `ORDER BY s.started_at DESC, s.id DESC`
(`GymStore.kt:589`, `:1212`, `:1239`), `position ASC` (`:1084`), `ordinal ASC` (`:1274`),
`achieved_at DESC` (`:1391`), `set_index ASC` (`:1630`), `local_date` / `local_week_start` — which sort
lexicographically only because they are ISO-8601 (`Schema.kt:297`, `GymMath.kt:51-59`).

---

# Files

## app/src/main/java/io/eddiegulay/tempo/gym/data/BuiltInCatalog.kt

Purpose: the shipped catalogue as reviewable Kotlin — every exercise, programme and built-in routine
the seeder writes into the database. **All 71 user-visible Japanese literals in this scope are in this
file.** The user meets them everywhere in 鍛錬: the exercise index, the station picker, every player
page, the library, the record breakdown.

### Exercise names — `exercise.name_ja` / `name_en` (23 rows, lines 171–205)

| key | ja | context | notes |
|---|---|---|---|
| `catalog.exercise.pushup.name` | 腕立て伏せ | exercise row | `name_en` already seeded: "Push-up" (`:171`) |
| `catalog.exercise.knee_pushup.name` | 膝つき腕立て | exercise row | en "Knee push-up" (`:172`) |
| `catalog.exercise.pushup_rotation.name` | 回旋腕立て伏せ | exercise row | en "Push-up with rotation" (`:173`) |
| `catalog.exercise.pullup.name` | 懸垂 | exercise row | en "Pull-up" (`:174`) |
| `catalog.exercise.ring_row.name` | 斜め懸垂 | exercise row | en "Ring row" (`:175`) |
| `catalog.exercise.squat.name` | スクワット | exercise row | en "Air squat" — note the JA and EN are not the same movement word (`:176`) |
| `catalog.exercise.wall_sit.name` | 空気椅子 | exercise row | en "Wall sit" (`:177`) |
| `catalog.exercise.lunge.name` | ランジ | exercise row | en "Lunge" (`:178`) |
| `catalog.exercise.step_up.name` | 踏み台昇降 | exercise row | en "Step-up" (`:179`) |
| `catalog.exercise.situp.name` | 腹筋 | exercise row | en "Sit-up" (`:180`) |
| `catalog.exercise.crunch.name` | クランチ | exercise row | en "Crunch" (`:181`) |
| `catalog.exercise.plank.name` | プランク | exercise row | en "Plank" (`:182`) |
| `catalog.exercise.side_plank.name` | 横プランク | exercise row | en "Side plank" (`:183`) |
| `catalog.exercise.dip.name` | ディップス | exercise row | en "Triceps dip" (`:184`) |
| `catalog.exercise.burpee.name` | バーピー | exercise row | en "Burpee" (`:185`) |
| `catalog.exercise.jumping_jack.name` | ジャンピングジャック | exercise row | en "Jumping jacks" (`:186`) |
| `catalog.exercise.high_knees.name` | もも上げ | exercise row | en "High knees" (`:187`) |
| `catalog.exercise.run.name` | 走る | exercise row | en "Run"; the only row with a null cue (`:189`) |
| `catalog.exercise.wall_pushup.name` | 壁腕立て | ladder rung | en "Wall push-up" (`:201`) |
| `catalog.exercise.incline_pushup.name` | 斜め腕立て | ladder rung | en "Incline push-up" (`:202`) |
| `catalog.exercise.feet_elevated_pushup.name` | 足上げ腕立て | ladder rung | en "Feet-elevated push-up" (`:203`) |
| `catalog.exercise.archer_pushup.name` | アーチャー腕立て | ladder rung | en "Archer push-up" (`:204`) |
| `catalog.exercise.one_arm_pushup.name` | 片手腕立て | ladder rung | en "One-arm push-up" (`:205`) |

The five ladder rungs' English names are ratified source content (`BuiltInCatalog.kt:193-200`:
*"`name_en` is NOT NULL because TalkBack reads it"* — an intent the code does not yet honour, since no
TalkBack path reads `nameEn`; see Q1).

### Exercise cues — `exercise.cue` (17 non-null rows)

| key | ja | context | notes |
|---|---|---|---|
| `cue.exercise.pushup` | 体は一直線に | form cue under the movement name (`:171`) | sentence-shaped; also read aloud by TalkBack via `contentDescription` |
| `cue.exercise.knee_pushup` | 腰を落とさない | (`:172`) | identical string to `side_plank`'s cue |
| `cue.exercise.pushup_rotation` | 上げた手を目で追う | (`:173`) | |
| `cue.exercise.pullup` | 肩を下げてから引く | (`:174`) | |
| `cue.exercise.ring_row` | 体は板のまま | (`:175`) | |
| `cue.exercise.squat` | 膝は爪先の向きに | (`:176`) | |
| `cue.exercise.wall_sit` | 膝は九十度 | (`:177`) | **kanji numeral** 九十度 = 90° — a numeral hazard, see Hazards |
| `cue.exercise.lunge` | 前膝を爪先より前に出さない | (`:178`) | longest cue, 14 chars → ~40 chars in English |
| `cue.exercise.step_up` | 足の裏全体で乗る | (`:179`) | |
| `cue.exercise.situp` | 反動を使わない | (`:180`) | |
| `cue.exercise.crunch` | 腰は床につけたまま | (`:181`) | |
| `cue.exercise.plank` | 肘は肩の真下に | (`:182`) | |
| `cue.exercise.side_plank` | 腰を落とさない | (`:183`) | duplicate of `knee_pushup`'s; dedupe is the merge step's job |
| `cue.exercise.dip` | 肘は後ろへ | (`:184`) | |
| `cue.exercise.burpee` | 着地は柔らかく | (`:185`) | |
| `cue.exercise.jumping_jack` | 肩の力を抜く | (`:186`) | |
| `cue.exercise.high_knees` | 腿は腰の高さまで | (`:187`) | |

`run` deliberately carries **no** cue — `BuiltInCatalog.kt:188` records that §F.1's "—" means *none*,
not a cue. Do not let a translation pass invent one. The five ladder rungs are null for the same
reason (`:199-200`).

### Programme names and notes — `progression_program`

| key | ja | context | notes |
|---|---|---|---|
| `catalog.program.p_recon_ron.name` | リーコン・ロン | programme title on the library detail (`:248`) | transliteration of "Recon Ron"; English should almost certainly be the source name, not a re-translation |
| `catalog.program.p_recon_ron.note` | 週に一日は三分の一の回数で | programme note (`:258`) | **kanji numerals + counters**: 一日 / 三分の一 / 回数 |
| `catalog.program.p_armstrong.name` | アームストロング | (`:286`) | transliteration of "Armstrong" |
| `catalog.program.p_fighter.name` | ファイター懸垂 | (`:329`) | "Fighter Pull-Up Program"; half loanword, half kanji |

`origin` on all three (`:254`, `:292`, `:335`) is a Latin-script citation — leave untranslated.

### Programme step labels and notes — `progression_step` (Armstrong only)

| key | ja | context | notes |
|---|---|---|---|
| `catalog.program.p_armstrong.step.1.label` | 第一日 | step label (`:295`) | **kanji ordinal day counter** — "Day 1"; the series 第一日…第五日 is a formatter, not five strings |
| `catalog.program.p_armstrong.step.1.note` | 全力五組 | (`:295`) | kanji numeral + counter 組 = "5 sets, all-out" |
| `catalog.program.p_armstrong.step.2.label` | 第二日 | (`:296`) | |
| `catalog.program.p_armstrong.step.2.note` | 段を上げて限界まで | (`:296`) | |
| `catalog.program.p_armstrong.step.3.label` | 第三日 | (`:298`) | |
| `catalog.program.p_armstrong.step.3.note` | 順手・狭手・逆手 | (`:298`) | three grip names joined by `・`; maps to the `SetVariant` enum OVERHAND/CLOSE/REVERSE (`:300-308`) — the enum is stored, the label is not |
| `catalog.program.p_armstrong.step.4.label` | 第四日 | (`:311`) | |
| `catalog.program.p_armstrong.step.4.note` | 一番きつい日 | (`:311`) | |
| `catalog.program.p_armstrong.step.5.label` | 第五日 | (`:312`) | |
| `catalog.program.p_armstrong.step.5.note` | 第四日をもう一度 | (`:312`) | **cross-references another label** — "repeat Day 4". A translation that renumbers days breaks this note. |

Recon Ron's 18 steps carry `labelJa = null` and `noteJa = null` (`:262`, `:265`) — the UI numbers them.
Fighter has no steps at all (`:337`).

### Routine names — `routine_version.name` (9 rows)

| key | ja | context | notes |
|---|---|---|---|
| `catalog.routine.r_seven_minute.name` | 七分間 | library card + session header (`:392`) | **kanji numeral + counter**: "seven minutes". The routine's own estimate is 475 s ≈ 約八分, so the name and the estimate disagree by design (`:384-387`) — a translation must not "fix" that to "8-minute" |
| `catalog.routine.r_tabata.name` | タバタ | (`:424`) | proper noun (Tabata) |
| `catalog.routine.r_recon_ron.name` | リーコン・ロン | (`:456`) | must match the programme name above |
| `catalog.routine.r_cindy.name` | シンディ | (`:495`) | proper noun (Cindy) |
| `catalog.routine.r_cindy_scaled.name` | シンディ（やさしい） | (`:531`) | **composed**: proper noun + full-width parens + the word やさしい, which is also `ScalingTier.EASY.label`. Two systems, one string |
| `catalog.routine.r_chelsea.name` | チェルシー | (`:557`) | proper noun (Chelsea) |
| `catalog.routine.r_barbara.name` | バーバラ | (`:582`) | proper noun (Barbara) |
| `catalog.routine.r_murph.name` | マーフ | (`:622`) | proper noun (Murph) |
| `catalog.routine.r_death_by.name` | デス・バイ | (`:656`) | transliteration of "Death By"; the `・` is a katakana-compound separator that has no English equivalent |

`origin` on all nine (`:395`, `:427`, `:459`, `:498`, `:534`, `:559`, `:585`, `:625`, `:659`) is a
Latin-script citation, already language-neutral, rendered on the detail page. Leave as-is.

### Station notes — `routine_station.note` (4 rows, 2 distinct)

| key | ja | context | notes |
|---|---|---|---|
| `catalog.routine.r_tabata.station.0.note` | 種目は自由に | station note (`:434`) | "the movement is yours to choose" |
| `catalog.routine.r_death_by.station.0.note` | 種目は自由に | station note (`:666`) | deliberately the *same words* as タバタ's (`:643-645`) — keep them identical after translation |
| `catalog.routine.r_murph.station.0.note` | 一マイル | first run leg (`:631`) | **kanji numeral + imperial unit**: "one mile". Distance is deliberately a note and not a column (`:608-611`) |
| `catalog.routine.r_murph.station.4.note` | 一マイル | last run leg (`:635`) | same string, second row |

## app/src/main/java/io/eddiegulay/tempo/gym/data/Schema.kt

Purpose: the DDL for all thirteen tables. No user-visible copy. Carries the localised-name columns
mapped in Q1 and the one Japanese-bearing constraint in Q6.

Nothing to translate. **One thing not to translate:** `CHECK (tier IS NULL OR tier IN ('入門','中級','上級'))`
at `Schema.kt:141`.

## app/src/main/java/io/eddiegulay/tempo/gym/data/Seeder.kt

Purpose: writes `SeedCatalog` into the tables on first open and on every catalog bump. No literals of
its own — it is the *mechanism* by which the catalogue's strings become rows. Analysed in Q3.

Relevant lines: the exercise upsert `Seeder.kt:59-90` (overwrites `name_ja`, `name_en`, `cue`), the
programme upsert `:101-177` (overwrites `name_ja`, `note_ja`, `label_ja`), the content-addressed
routine pass `:180-268` (a name change forces a new version), `SQL_REPOINT_BUILT_IN` `:316-321`
(scoped `built_in = 1`), `SQL_ENSURE_PROGRESSION_STATE` `:330-335` (`INSERT OR IGNORE`).

## app/src/main/java/io/eddiegulay/tempo/gym/data/GymStore.kt

Purpose: the SQLite store behind `GymRepository` — every read and write in 鍛錬. It holds **one**
user-visible copy site: a card summary composed in Japanese inside the data layer.

| key | ja | context | notes |
|---|---|---|---|
| `fmt.routine.card.stations` | 種目 | counter suffix on the library card's one-line summary (`:2069`) | composed as `kanjiExtended(n) + "種目"` |
| `fmt.routine.card.seconds` | 秒 | work/rest fragment (`:2072`, `:2074`) | composed as `kanjiExtended(work) + "秒" + " / " + kanjiExtended(rest) + "秒"` |
| `fmt.routine.card.approxMinutes` | 約…分 | estimate fragment (`:2082`) | composed as `"約" + kanjiExtended(min) + "分"` — a **circumfix**, prefix + suffix around a numeral |
| `fmt.routine.card.separator` | ` ・ ` | joins the fragments (`:2088`) | full-width middle dot with spaces; the whole line reads 「十二種目 ・ 三十秒 / 十秒 ・ 約八分」 |

Everything else in the file is SQL, ids, enum names and numbers. The two big projections
(`SUMMARY_SQL` `:2148-2162`, `OPEN_SQL` `:2172-2178`) are positionally indexed and carry no copy.

## app/src/main/java/io/eddiegulay/tempo/gym/data/GymMath.kt

Purpose: everything in the store decidable without a database — calendar bucketing, streaks, volume,
load/monotony/ACWR, the ramp governor, the structural hash. **No string literals of any kind** beyond
the `U+0001` field separator (`:411`). Nothing to translate.

One relevance: `structuralHashOf` (`:387-408`) hashes the routine **name** and every station **note**,
which is why translating a built-in's name produces a new `routine_version` (Q3).

## app/src/main/java/io/eddiegulay/tempo/gym/data/ExerciseCatalogSource.kt

Purpose: the in-memory mirror of the `exercise` table that the picker and the timeline compiler read
synchronously. No literals. Relevant only for sort order — `sortedWith(compareBy({ it.difficulty }, { it.nameJa }))`
at `:56` (Q7).

## app/src/main/java/io/eddiegulay/tempo/gym/data/ExerciseDb.kt

Purpose: the `SQLiteOpenHelper` — create, upgrade, corruption quarantine, draft sweep. No user-visible
literals. Relevant because it decides *when* seeds land: `onCreate` seeds from 0 (`:144`), `onUpgrade`
deliberately does **not** seed (`:148-156`), `onOpen` seeds on a catalog bump (`:166-174`). Q3 and Q4
turn on those three lines.

## app/src/main/java/io/eddiegulay/tempo/gym/data/Migrations.kt

Purpose: the migration list and derived `SCHEMA_VERSION`. One Japanese literal, non-visible: the
`SchemaDowngrade` exception message at `:52`.

## app/src/main/java/io/eddiegulay/tempo/gym/data/Meta.kt

Purpose: the `exercise_meta` key-value scalars. Four ASCII keys (`:20-29`). Nothing to report.

## app/src/main/java/io/eddiegulay/tempo/gym/data/DbSupport.kt

Purpose: the transaction wrapper, cursor helpers, and `toGymFault`. No string literals reach a user —
the exception messages at `:62`, `:75`, `:85` are ASCII diagnostics, and the Japanese in the KDoc is
*quoting* copy that lives in `GymFault`/`RecordCopy` (another surveyor's scope). Nothing to report.

## app/src/main/java/io/eddiegulay/tempo/gym/data/HistoryLoss.kt

Purpose: the in-process "history was destroyed" flag. No literals (Japanese appears in KDoc only, as a
quotation of 記録はありません, which belongs to a UI scope). Nothing to report.

## app/src/main/java/io/eddiegulay/tempo/gym/data/TableChangeNotifier.kt

Purpose: the in-process table-change bus. No literals. Nothing to report.

---

## Hazards

1. **`routine.tier` stores Japanese and a CHECK constraint enforces it.** `Schema.kt:141` —
   `CHECK (tier IS NULL OR tier IN ('入門','中級','上級'))`, with `Tier.storageValue = label`
   (`GymModels.kt:131`). Translating the label breaks writes (constraint violation → `GymFault.Rejected`,
   `DbSupport.kt:113`) *and* reads (`Tier.fromStorage` returns null, `GymStore.kt:1018`, `:1066`).
   Fixing the column needs a full table rebuild at SQLite 3.28 on a table with mutual FKs. **Decouple
   `storageValue` from `label` before anything else in this migration.** (contract hazards 8, 9)

2. **Translating a built-in's name inserts a new `routine_version` on every install.** The seed hash
   covers the name and every station note (`GymMath.kt:398-408`, `Seeder.kt:180-182`). This is the
   supported delivery mechanism, but it means (a) the routine's *history* keeps the old-language name
   because sessions point at the old version, and (b) any design where the seed name varies **per
   selected locale** would churn a new version every time the user toggles language, permanently. The
   seed must stay locale-invariant; localisation has to be a separate column or table read at display
   time. (contract hazard 9)

3. **`session.routine_name` freezes the language of the day.** `GymStore.kt:341` denormalises the name
   at start. A user who trains in Japanese for a year and then switches to English sees a history list
   of Japanese names beside English ones. There is no correct fix — the column exists precisely so the
   banner survives the routine's deletion (`Schema.kt:240-243`) — but it must be a *stated* behaviour,
   not a bug report. (contract hazard 9)

4. **Duplicate names are Japanese-composed and then persisted.** `LibraryFilters.kt:294` builds
   「七分間 の写し」 / 「の写し二」 (a kanji ordinal!), `GymViewModel.kt:673` passes it, and
   `GymStore.kt:816-840` writes it into `routine_version.name` forever. The composed name outlives the
   language that composed it. (contract hazards 1, 2, 9)

5. **Seeded notes migrate into user rows.** `routine_station.note` cannot be authored
   (`StationPickerScreen.kt:225-236`) but *is* carried: a user editing マーフ forks it (`GymStore.kt:783-788`)
   and their private copy now owns the string 一マイル, which the seeder can never reach again
   (`built_in = 1` scope). A seeded string that has quietly become user data. (contract hazard 9)

6. **Kanji numerals and counters inside seeded content.** Not UI copy — *rows*:
   七分間 (`:392`), 一マイル ×2 (`:631`, `:635`), 週に一日は三分の一の回数で (`:258`), 全力五組 (`:295`),
   第一日…第五日 (`:295`–`:312`), 膝は九十度 (`:177`). English needs arabic numerals and, for 第N日, a
   *formatter* ("Day 1") rather than five independent strings. `第四日をもう一度` (`:312`)
   cross-references another label and breaks if days are renumbered. (contract hazard 2)

7. **The card summary is composed in the data layer.** `GymStore.kt:2067-2095` concatenates
   `JapaneseDate.kanjiExtended(n)` with 種目 / 秒 / 分 and wraps a numeral in the circumfix 約…分,
   joined by ` ・ `. Word order, the separator and the numeral system all differ in English, and the
   function's own KDoc (`:2059-2066`) says it should be calling a shared formatter that has not landed.
   This is a `fmt.*` job, not a table lookup. (contract hazards 1, 2)

8. **Pluralisation appears the moment names become English.** Every seeded counter (種目, 組, 回数,
   マイル, 分, 秒) is count-invariant in Japanese and count-sensitive in English. The seeded *content*
   carries these, so plural rules leak into the catalogue, not just the UI. (contract hazard 4)

9. **`squat` is 「スクワット」 but `name_en` is "Air squat"** (`:176`). The two columns are not
   translations of each other for every row — the English side is CrossFit's vocabulary, the Japanese
   side is the generic movement word. Do not assume `name_en` is a mechanical translation of `name_ja`
   when building a translation table from the existing pair.

10. **The ladder tie-break sorts by `nameJa`** (`ExerciseCatalogSource.kt:56`, and
    `ExerciseIndexScreen.kt:233` outside this scope) using UTF-16 code-unit order — no `Collator`, no
    kana reading column exists to fall back on. English UI ordering ties by an invisible string.
    (contract hazard 8)

11. **`name_en` already feeds search but nothing else** (`LibraryFilters.kt:167`). Exercise search
    already matches English; **routine** search does not — `matchRoutine` searches `summary.name`,
    `origin` and station names, all Japanese. Search behaviour will be asymmetric between exercises and
    routines the moment the UI is English. (contract hazard 8)

12. **Tests pin seeded Japanese literals.** Four assertions break the moment a seed string moves:
    `ExerciseCatalogTest.kt:51` (`"腕立て伏せ"`), `SeedCatalogTest.kt:297` (`"一マイル"`),
    `SeedCatalogTest.kt:324` (`"種目は自由に"`), `GymMathTest.kt:139-143` (`"シンディ"` / `"シンディ改"`
    as structural-hash inputs). `GymContractTest.kt:58-60` pins all three `Tier.storageValue`s.
    `SeedUpgradeTest` asserts on the text of `SQL_REPOINT_BUILT_IN` itself, so a migration that touches
    that statement breaks it. Test-method *names* in `SeedCatalogTest.kt` are also Japanese (13 of
    them) but are harmless. (contract hazard 10)

13. **A translation release needs two counters to move, and one of them is easy to miss.** A column
    added in `onUpgrade` is never populated by `onUpgrade` (`ExerciseDb.kt:148-156`); values arrive
    only via `onOpen` + `SeedCatalog.VERSION` (`ExerciseDb.kt:166-174`), and `planFrom`'s strict `>`
    (`BuiltInCatalog.kt:154-158`) means every row you want rewritten must *also* have its own
    `catalogVersion` bumped. Bump only `SeedCatalog.VERSION` and existing installs get nothing.

14. **Column-per-language is irreversible.** No `DROP COLUMN` at SQLite 3.28 (`Schema.kt:9`). Every
    language ever shipped is a permanent column on five tables. See Q4.

---

## Non-visible Japanese

Literals that are Japanese but never reach a user, with why they stay Japanese:

| location | literal | reason it stays |
|---|---|---|
| `Schema.kt:141` | `'入門'`, `'中級'`, `'上級'` (3 literals, in a CHECK constraint) | These are **storage values**, not copy. They are what `routine.tier` holds, what `Tier.fromStorage` string-matches, and what the constraint admits. Changing them without a table rebuild plus a data migration corrupts every tier on every install. They happen to be identical to the labels the user sees, which is the trap — see Hazard 1. |
| `Migrations.kt:52` | `"鍛錬 database is from a newer build: $oldVersion → $newVersion"` | Exception message, caught in `openOrRecover` and mapped to `GymFault.StoreReset` (`DbSupport.kt:100`). The message text never surfaces; the user sees the fault's own copy. Diagnostic only. |

Not counted, but worth stating: **`gym/data/` KDoc and comments are written in Japanese-inflected
prose throughout**, quoting real UI copy (記録はありません, つづき, 済, 最高負荷, 二十種目中 八, 約八分,
種目 一件が不明, 保存できませんでした, この記録は削除されています, もう一度, 途中まで, 第九段 / 十八段のうち,
とばす, ＋二十秒, 写して作る, 完全に削除, やった記録はありません。完全に消えます。, 記録を読めません,
読み込み中, 連続, よく使う, 動きごと, いちばん上, 段階, 型 / 自分の型, 押す・引く・しゃがむ・股関節・体幹・移動・跳ぶ).
**None of these are strings in this scope** — each is a quotation of copy owned by a UI surveyor, and
each is a useful cross-check that the UI fragments have found the real thing. They are documentation
and stay as they are.
