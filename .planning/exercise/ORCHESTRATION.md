# 鍛錬 — orchestration prompt

Hand the block below to an agent supervisor. It assumes repo access and the ability to spawn agents.

---

# Mission

Implement the 鍛錬 (Exercise/Gym) feature in the Tempo Android launcher at
`/Users/eddiegulay/Documents/Work/Ground/Tempo`, working from an existing, approved specification.
Decompose the work, run agents in parallel where the dependency graph allows, and keep going until
the feature is delivered and verified. You are the orchestrator: you assign, you verify, you
integrate. You do not write feature code yourself.

# Source of truth

Read these before assigning anything. They are the specification; you are not designing.

1. `.planning/exercise/00-plan.md` — **start here.** Page tree, resolved decisions, prerequisites,
   build order. Its §2 is a table of decisions already made; §3 is the complete page tree; §4 is a
   behaviour/action reference per page; §5 lists the prerequisite refactors; §6 is the phase order.
2. `.planning/exercise/01-shell.md` — navigation architecture, `GymViewModel`, `GYM.HOME`, `GYM.SETTINGS`
3. `.planning/exercise/02-data.md` — SQLite schema, migrations, repository API, seed data, the hard queries
4. `.planning/exercise/03-player.md` — `GYM.SESSION.*`, timeline compiler, state machine, cue engine, lifecycle
5. `.planning/exercise/04-library-records.md` — `GYM.LIBRARY.*`, `GYM.RECORDS.*`, Canvas drawing, Japanese strings
6. `.planning/exercise-design.md` — the *product* rationale (why the ensō is the timer, why a rep slide
   never auto-advances). Where it conflicts with `.planning/exercise/`, **the latter wins** — `00-plan.md`
   §2 enumerates every divergence and why.

Also read `CONTRIBUTING.md` and skim `app/src/main/java/io/eddiegulay/tempo/calendar/` — the calendar
feature is the house-style reference the whole spec is written against.

# Hard constraints — violations are rejected work, not review comments

1. **No new dependencies.** `CONTRIBUTING.md` forbids them. No Room, no KSP, no navigation-compose, no
   chart library, no reorderable-list library, no audio assets. Everything is AndroidX + platform APIs
   that already ship. If an agent claims it needs one, it is wrong — the spec solves every such case
   without one, and you should point them at the relevant section.
2. **`minSdk 29`.** SQLite is pinned at 3.28: no `STRICT` tables, no `RETURNING`, no `DROP COLUMN`, no
   generated columns, no `sqrt()`, **no JSON1**. `VibrationAttributes`' vibrate overload is API 33+;
   29–32 uses the `AudioAttributes` overload. See `02-data.md` §0 and `03-player.md` §D.3.
3. **All UI copy is Japanese**, hard-coded in Kotlin. `strings.xml` holds only `app_name` and stays that
   way. String tables are in `exercise-design.md` §12 and `04-library-records.md` §6. Do not invent
   strings that aren't in a table — escalate instead.
4. **The `Loadable` doctrine: loading ≠ empty ≠ failed.** An unreadable store must never render
   「記録はありません」. Every page spec names these states separately and they must not share a composable.
5. **Every colour from `LocalTempoColors`.** Not one hardcoded value in any draw call, or the dark
   theme breaks silently.
6. **Pure logic goes in Android-free functions with JUnit4 tests**, matching the repo's existing
   strategy (`groupByApp`, `layoutTategaki`, `faultCopy`). Each part file ends with its testable-function
   inventory. This is not optional; it is how the spec is verifiable at all.
7. **Do not re-litigate `00-plan.md` §2.** Those twenty decisions are settled, with reasoning. If an
   agent believes one is wrong, it escalates to you and you escalate to the human — it does not
   silently do something else.
8. **Never invent numbers.** The spec refuses to fabricate a RECONDO table or Pavel's 30-day ladder;
   hold that line everywhere. If a value is missing, escalate.

# Working method

- Branch: `feature/gym` off `main`. One commit per completed work unit, message describing the unit.
  **No GitHub, no PRs, no issues.** Local git only.
- Verification gate, run by you after every unit lands:
  `./gradlew --no-daemon assembleDebug lintDebug testDebugUnitTest`
  A unit is not done until this passes. If it fails, the owning agent fixes it before you assign
  anything that depends on it.
- Update `CHANGELOG.md` (Keep-a-Changelog, user-facing prose) once per phase, not per unit.

# Decomposition

## Phase 0 — prerequisites (blocks everything)

Six refactors from `00-plan.md` §5. Five can run in parallel; **P4 and P6 both touch `HomeScreen.kt`,
so give them to one agent or serialize them.**

| unit | change | touches |
|---|---|---|
| P1 | Widen `Loadable.Failed` to a shared `TempoFault`; `faultCopy` gains gym branches | `calendar/CalendarOutcome.kt`, `ui/CalendarFeedback.kt`, `ui/CalendarScreen.kt`, `ui/EventComposeScreen.kt`, `CalendarFeedbackTest.kt` |
| P2 | Extract `TempoWheel` → `ui/TempoWheel.kt`, generic + a `TempoValueWheel`; rename the date call site | `ui/EventComposeScreen.kt` |
| P3 | Extract `CycleDots` → `ui/CycleDots.kt`, parameterised; **Focus keeps its current defaults, pixel-unchanged** | `ui/FocusScreen.kt` |
| P4 | Parameterise `Enso` → `ui/Enso.kt` (sweepAngle, colour, fixed 220.dp/3.dp) | `ui/HomeScreen.kt` |
| P5 | `kanjiExtended(n)` for 100..9999, arabic above | `data/JapaneseDate.kt` + test |
| P6 | `ModeDialog` replaces `FocusConfirmDialog`; `_pendingFocus` → `_pendingMode` | `LauncherViewModel.kt`, `ui/HomeScreen.kt`, `ui/FocusConfirmDialog.kt` |

Phase 0 must be green before Phase 1 starts. No user-visible change except the mode dialog.

## Phase 1 — the player

**First, serially: land the contracts.** One agent writes only the data classes and the
`GymRepository` / `GymPreferencesRepository` *interface* signatures from `02-data.md` §C, plus
`GymFault`/`GymWrite`, with no implementations. This is a small commit and it is what unlocks
parallelism — everything downstream compiles against interfaces.

**Then five tracks in parallel:**

- **Track A — data layer.** `02-data.md` in full: schema DDL, `ExerciseDb`, migrations, `Seeder`,
  `BuiltInCatalog` (七分間 / タバタ / リーコン・ロン), `TableChangeNotifier`, the monotonic clock guard,
  the corruption handler, backup rules, and the repository implementation.
- **Track B — shell and navigation.** `01-shell.md` §A: `GymRoute`, the four pure nav functions **with
  tests first**, `GymViewModel` + factory, `GymShell`, the tab bar, `BackHandler` composition, the
  keep-screen-on effect, `Screen.Gym` wiring in `TempoApp`/`MainActivity`.
- **Track C — the engine.** `03-player.md` §B and §C: `Segment`, `Timeline`, `compile()` for **all seven
  engines**, `close`/`extend`/`reopen`, `stateAt`, `BackTapResolver`, `reconcile`. Zero Android imports,
  test-first. This track needs nothing from anyone and should start immediately.
- **Track D — cues.** `03-player.md` §D: `GymHaptics` (with the 29–32 / 33+ split), `GymTones` (audio
  focus per cue window, never held), the synthesized completion tone, TTS with the TalkBack
  auto-enable, and the disarm matrix.
- **Track E — pure logic.** The testable-function inventories in `04-library-records.md` §7 that Phase 1
  needs: `Numerals`, `RecordCopy`, `EngineRows`, `RoutineEstimate`, `LibraryFilters`. Test-first.

**Then integration, which needs A+B+C+D+E:** `GYM.HOME`, `GYM.LIBRARY.INDEX`, `GYM.LIBRARY.DETAIL`
(including the start-guard and insert-before-navigate), the six player pages, the quit sheet,
`GYM.SESSION.COMPLETE`, and the resume prompt. Assign one agent per page or per small cluster; they all
depend on the same foundation so they parallelize cleanly.

## Phases 2–4

`00-plan.md` §6 defines them. Decompose the same way — contracts, parallel tracks, integration — when
Phase 1 is delivered and verified. Do not start Phase 2 work early; the phase boundaries are real
(Phase 2 adds five engines to the UI that Track C already built, Phase 3 adds the foreground service).

# Definition of done, per work unit

1. Code matches the spec section it implements — layout tokens, sp/dp, states, actions, edge cases.
2. Every edge case numbered in that section is either handled or explicitly noted as deferred with a
   reason, in a code comment.
3. The pure functions the section lists exist, are Android-free, and have JUnit4 tests covering the
   boundaries the spec calls out.
4. Accessibility as specified: content descriptions, `customActions` for every long-press,
   `Role`/`stateDescription`, target sizes, and **no live region on any ticking value**.
5. KDoc in house style — explain **why, and what was rejected**, not what the code does. This is the
   strongest convention in the repo; match it.
6. The verification gate passes.

# Escalate to the human, do not guess

Three open questions from `00-plan.md` §7 block specific work. Surface them early rather than at the
moment they block:

1. **`training_plan`** — blocks the streak (Phase 3). Ship the documented fallback, or build a picker?
2. **Speech cues default** — proposal is off, auto-enabled under TalkBack. Confirm before Track D
   finalizes.
3. **Recon Ron's 18-step table** — must be verified against the source PDF before seeding. Blocks
   Track A's seed data.

Also escalate: any spec section that is genuinely silent on something load-bearing; any place two part
files appear to contradict each other that isn't already resolved in `00-plan.md` §2; any temptation to
add a dependency.

Do **not** escalate routine judgment calls — variable names, private helper structure, test naming,
which file a small utility lives in. Decide those and move on.

# Reporting

After each unit lands, record one line in a running `.planning/exercise/PROGRESS.md`: unit, agent,
commit, gate result, anything the next agent needs to know. When a phase completes, summarize to the
human: what shipped, what was deferred and why, what the open questions now are.

If a unit turns out to be blocked or wrong, say so plainly with the evidence. Do not report a phase as
delivered when part of it was skipped — name what was left out.
