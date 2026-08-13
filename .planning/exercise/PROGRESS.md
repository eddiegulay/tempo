# 鍛錬 — progress

The running record of what has actually landed on `feature/gym`, unit by unit. `00-plan.md` says what
*should* happen; this file says what *has*. Notable changes only — a row goes in when a work unit is
complete and its gate has passed, not when it is started.

Format is loosely [Keep a Changelog](https://keepachangelog.com/en/1.1.0/): newest phase last, one
row per unit, and the wording is aimed at the next agent who has to build on top of the row.

**Appending a row** — add to the bottom of the table, keep the unit id from `00-plan.md` §5 (Phase 0)
or §6 (later phases), and put in **notes** only what someone would otherwise have to read your diff to
discover: public API another unit will call, a decision you took that the spec left open, and anything
you deliberately did *not* do. "Done" is not a note.

**Gate** is the command every unit must pass before its row exists:
`./gradlew --no-daemon assembleDebug testDebugUnitTest`. `lintDebug` is the orchestrator's, run once.

## Phase 0 — prerequisites

Cross-cutting refactors. **No user-visible change except the mode dialog (P6)** — if a Phase 0 row
moved a pixel outside that dialog, it is a bug, not a row.

| unit | agent | files | gate | notes |
|---|---|---|---|---|
| P1 — widen `Loadable.Failed` to `TempoFault` | wf-0 A | +`data/TempoFault.kt`, ~`calendar/CalendarOutcome.kt`, ~`ui/CalendarFeedback.kt`, ~`ui/EventComposeScreen.kt`, ~`test/ui/CalendarFeedbackTest.kt` | ✅ | **`TempoFault` is NOT sealed and cannot be** — Kotlin requires direct subtypes of a sealed declaration to share its package, and `CalendarFault` must stay in `calendar/`. Each *family* stays sealed and `faultCopy` dispatches `is CalendarFault` / `is GymFault` to private per-family helpers with exhaustive inner `when`s. `00-plan.md` §2 row 4 as literally written does not compile; do not "fix" this. **`GymFault` lives in `io.eddiegulay.tempo.data`, not `gym/`** — import it, do not redeclare it. `GymWrite` was NOT created; it is Track A's. All eight cases are `02` §C.0 verbatim. Copy is `DECISIONS.md` §Q6, every string sourced. `CalendarScreen.kt` needed no edit. |
| P2 — extract `TempoWheel` | wf-0 A | +`ui/TempoWheel.kt`, ~`ui/EventComposeScreen.kt` | ✅ | Public: `TempoWheel(columns: List<TempoWheelColumn>, modifier, spacing = 24.dp)`, `TempoValueWheel(values: List<Int> | IntProgression, selected, onSelect, modifier, label)`, `TempoDateTimeWheel(current, allDay, onChange)`, `WheelWidth.Fill / Fixed(Dp)`. `WheelColumn` stays private and moved byte-for-byte — **the skip-first-emission guard is intact**; mounting is not choosing. `TempoWheelColumn.initialIndex` seeds `rememberLazyListState` once and never re-scrolls: to move a wheel programmatically, re-key it. Date picker pixel-identical (132.dp box, 24.dp spacing, `weight(1f)` day, two 58.dp columns). |
| P3 — extract `CycleDots` | wf-0 B | +`ui/CycleDots.kt`, ~`ui/FocusScreen.kt`, +`test/ui/CycleDotsTest.kt` | ✅ | Public: `@Composable CycleDots(total, filled, filledColor, pendingColor, modifier, dotSize = 9.dp, gap = 12.dp, label: String? = null)`, plus pure `cycleDotsOverflow(total) = total > 9` and pure `cycleDotsFilled(total, filled)`. Defaults are Focus's geometry, so Focus is pixel-identical; the player passes `dotSize = 6.dp, pendingColor = c.hair`. **The `total > 9` word fallback is NOT inside the component** — 巡 is caller copy, so the player renders the text and asks `cycleDotsOverflow` when to. A width-based predicate was shipped first and rejected in review (`DECISIONS.md` §Q8): it admitted seventeen dots. Dots carry `clearAndSetSemantics {}`; pass `label` for the single merged node. |
| P4 — parameterise `Enso` | wf-0 C | +`ui/Enso.kt`, ~`ui/HomeScreen.kt`, +`test/ui/EnsoTest.kt` | ✅ | Public: `Enso(sweepAngle, color, modifier, diameter: Dp = Dp.Unspecified, strokeWidth = 3.dp)`, `ensoSweep(fraction)`, `ENSO_START_ANGLE = -60f`, `ENSO_SWEEP_DEGREES = 312f`. **`diameter` is the diameter of the *path*, not of the ink** — a stroke is centred on its path, so an explicit diameter draws `d + strokeWidth` of ink and the caller is expected to have chosen that inset (Home passes 202.dp into a 252.dp box). The unspecified path insets by half the stroke so the mark fits the box it was given. `ensoSweep` clamps and maps NaN → 0f, so a zero-length segment draws nothing rather than a full ring. |
| P5 — `kanjiExtended` | wf-0 D | ~`data/JapaneseDate.kt`, ~`test/data/JapaneseDateTest.kt` | ✅ | `JapaneseDate.kanjiExtended(n)` covers 100..9999 with the 百/千 elisions, delegates 0..99 to the existing `kanji`, and falls back to arabic above 9999. **`gym/Numerals.kt` must NOT redeclare it** (`DECISIONS.md` §Q7) — `04` §7 lists it there, `00-plan.md` §5 P5 puts it here, and P5 wins because it shipped. `Numerals.kt` owns only `coefficientLabel` and `durationKanji`. |
| P6 — `ModeDialog` | wf-0 C | +`ui/ModeDialog.kt`, −`ui/FocusConfirmDialog.kt`, ~`LauncherViewModel.kt`, ~`ui/HomeScreen.kt`, ~`ui/TempoApp.kt` | ✅ | `enum class LauncherMode { Focus, Gym }`; `_pendingFocus` → `_pendingMode`, `confirmFocus` → `confirmMode(LauncherMode)`. `Screen.Gym` exists on the enum. Home long-press semantics label is now `モードを選ぶ`. **`Screen.Gym` is a reachable dead end until Track B lands** — `TempoApp`'s branch renders nothing, so tapping 鍛錬 shows bare wallpaper. Recoverable via Back and the dock, but it means **Phase 0 is not independently shippable**, contrary to `00-plan.md` §6. Do not cut a build at this boundary. |

## Phase 1 — the spine

Schema, store, the library pages and a player that runs a real session end to end.

| unit | agent | files | gate | notes |
|---|---|---|---|---|
| Foundation — schema, store, seed | wf-1 A/B | +`gym/data/{Schema,GymStore,Seeder,BuiltInCatalog,Migrations,DbSupport,Meta,ExerciseDb,ExerciseCatalogSource,HistoryLoss,TableChangeNotifier}.kt`, +`gym/{GymModels,GymRepository,GymWrite}.kt` | ✅ | SQLite via the platform `SQLiteOpenHelper`, **not Room** — Room needs KSP and a dependency, and `CONTRIBUTING.md` forbids both. minSdk 29 pins SQLite **3.28**: no `STRICT`, no `RETURNING`, no `DROP COLUMN`, no generated columns, no `sqrt()`, no JSON1. Every migration is additive and irreversible. `exercise` carries **`name_en` from v1** — seeded, projected, and (until v0.2.0) never rendered. `routine.tier` stores the Japanese label under a live `CHECK`; see `DECISIONS.md` §L3 for why that stayed. |
| Library pages | wf-1 C | +`ui/gym/{LibraryIndexScreen,LibraryDetailScreen,ExerciseIndexScreen,ExerciseDetailScreen}.kt`, +`gym/LibraryFilters.kt` | ✅ | `foldKana` normalises half/full-width, dakuten and katakana→hiragana for search; **romaji is deliberately unsupported**. Shipped once with **zero call sites** — see the note under Phase 3. |
| Player | wf-1 D | +`ui/gym/session/**`, +`gym/session/{Timeline,TimelineCompiler,SessionMachine,Reconcile,BackTapResolver}.kt` | ✅ | Timing is a precompiled `List<Segment>` plus a pure `stateAt(elapsedMs)` over `SystemClock.elapsedRealtime()`. The 50 ms tick **re-reads** state and never advances it, so a dropped frame cannot lose a second. **The ensō is the timer**, not decoration beside one. Nothing auto-advances past a rep station: 済 is the gate. |

## Phase 2–3 — building, records, cues

Run concurrently. `GymShell.kt` route wiring was pulled into a single pass first, because it was the only true cross-phase dependency and it had serialised the previous three units.

| unit | agent | files | gate | notes |
|---|---|---|---|---|
| Builder + station picker | wf-2 A | +`ui/gym/{BuilderScreen,StationPickerScreen}.kt`, +`gym/BuilderDraft.kt` | ✅ | §Q21 — **a wheel must contain the value it is editing**, or unfolding it silently rewrites what it was showing. Three silent data-loss bugs were found and fixed here, all triggered by *browsing* rather than by a destructive act. |
| Records + charts | wf-3 A | +`ui/gym/{RecordsIndexScreen,RecordsHistoryScreen,RecordsChartsScreen,RecordsPrScreen,RecordSummary,SessionDetailScreen}.kt`, +`gym/{RecordCopy,ChartGeometry,InkDensity,HistoryPaging}.kt` | ✅ | Foster sRPE = CR10 × minutes; monotony = mean/sd over 7 days. **ACWR is computed and never drawn** — `AcwrRestraintTest` enforces that structurally, including positional destructuring, because `component3()` is the ratio wearing another name. |
| Cues + foreground service | wf-3 B | +`gym/cue/**`, +`gym/{TrainingNotice,TrainingService,TrainingConsent}.kt` | ✅ | `CueState.serviceHolding` and `sessionShouldTick(ticking, visible, serviceHeld)` exist to stop the notification freezing when the service holds a session the UI has left. Wiring the service without the disarm matrix shows a permanently frozen notification. |
| Dock entry + init-order crash | orchestrator | ~`ui/{Dock,LineIcon}.kt`, ~`LauncherViewModel.kt`, ~`gym/GymViewModel.kt`, +`test/gym/GymViewModelInitOrderTest.kt` | ✅ | 鍛錬 gets a fourth dock button rather than a long-press: the pill's `Row` already claims long-press for the default-home role. **`init` must stay last in `GymViewModel`'s class body** — Kotlin runs initialisers in declaration order, so an `init` above a `private val _x = MutableStateFlow(…)` sees `_x` as null. A launched coroutine hid it until first resume. Pinned structurally. |

## Phase 4 — coaching

| unit | agent | files | gate | notes |
|---|---|---|---|---|
| Progression + ramp governor | wf-4 A | ~`gym/data/{GymMath,GymStore}.kt` | ✅ | `ALL_SETS_MADE` completes the arm the schema already carried. The four progression rules were lifted out of `GymStore` into pure functions first — `SQLiteDatabase` is not on the unit-test classpath, so that was the only way to make any of them testable, and it made the two pre-existing rules testable as a side effect. `rampAllowed` compares a rep-count fraction against a Foster-load fraction: a deliberate unit mismatch, sound because it is a controller reading its own output. |
| Scope audit | wf-4 B | +`.planning/exercise/PHASE-4-GAPS.md`, +`test/gym/AcwrRestraintTest.kt` | ✅ | Double progression and deload prompts are **not built** — no rep range, no trigger, no copy exists in any spec. The soft monotony nudge was also declined: reusing 同じ調子が続いています would make twelve characters carry two pieces of advice with two triggers, and the user could not tell which fired. |
