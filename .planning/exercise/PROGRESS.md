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
