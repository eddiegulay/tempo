# 鍛錬 — the session player and the records pages

**Files surveyed:** 21   **User-visible literals:** 160   **Non-visible JP literals:** 0

Counted as table rows below: 160 rows, **157 distinct keys** (the three on
`SessionUnrecoverablePage.kt` are re-declarations of `QuitSheet.kt`'s and are listed in both files per
the contract). Six of the 21 files have zero user-visible Japanese and say so in one line.

The row count is the number of *user-facing strings*, not of source literals. `PlayerCopy.kt`'s 47
rows are assembled from **57 raw Japanese fragments**; Part B's formatters are similar. Where the two
counts differ the file section says so.

Two unrelated translation problems share this scope and are kept apart below.

- **Part A — the live player** (`ui/gym/session/`). A glanceable, immersive, portrait-locked screen the
  user reads mid-effort. Copy length is a functional constraint: almost every slot is fixed-height or
  `maxLines = 1` with `softWrap = false`, and the two hero numerals are capped by an arithmetic that
  encodes CJK glyph metrics. Its copy is already extracted into one module, `PlayerCopy.kt`.
- **Part B — the records pages** (`ui/gym/Records*.kt`, `RecordSummary.kt`, `SessionDetailScreen.kt`).
  Ordinary scrolling pages. Their problem is not length, it is that most of what they draw is a
  **formatter** — dates, durations, streaks, PR labels, counts — not a table entry.

Two conventions run through the whole scope and are the two largest hazards, both stated once in
**Hazards** rather than repeated per row:

1. `DECISIONS.md` §Q4's numeral split — *ticking values are arabic, settled values are kanji* — and
   §Q10's second split of the kanji half — *a duration you **chose** is bare seconds, a duration the
   app **measured** goes through `durationKanjiFromMs`*. Neither survives into English.
2. Every count-bearing string (回 / 巡 / 種目 / 日 / 分 / 秒 / 週) is pluralisation-free in Japanese.

---

# Part A — the live player (`ui/gym/session/`)

## app/src/main/java/io/eddiegulay/tempo/ui/gym/session/PlayerCopy.kt

Purpose: every word the five live pages and the quit sheet put on screen, as one Android-free module.
The user meets all of it inside `GYM.SESSION.*`.

**Shape — this decides whether migration is mechanical, and it is not.** The file is:

- 3 top-level `const val String` (lines 35, 38, 41);
- **25 top-level pure functions**, `fun name(args): String` or `String?`;
- 1 `data class QuitSheetOptions` (line 385) carrying *two* copy fields (`discardLabel`) alongside two
  booleans, plus the factory `quitOptions(resultsWritten: Int)` (line 392) that picks between two
  literals on a count.

There is **no single `when` over session state**. Dispatch is per function, and the branch keys differ:
`RestKind?` (`restLabel`, `skipDisabledDescription`, `extendDisabledDescription`), `Phase` +
nullability (`nextUpLabel`, `nextUpDescription`), `Segment?` field shape (`prescriptionLabel`), a
numeric window (`countdownAnnouncement`), `Int?` (`repHero`, `repDoneDescription`), `Long` sign
(`extendedSuffix`, `addedStateDescription`).

Parameters are **primitives and domain values, never a formatted string**: `Long` milliseconds, `Int`
counts, `String?` exercise names, `RestKind?`, `Segment?`, `Boolean`. Nine functions return `String?`
and **null means the page draws an empty fixed slot** — a documented behaviour, not an oversight
(`nextUpLabel`, `counterLabel`, `countdownAnnouncement`, `prescriptionLabel`, `pacerLabel`,
`extendedSuffix`, `addedStateDescription`, `skipDisabledDescription`, `extendDisabledDescription`,
`accruedLine`).

Only 3 of the 30 outputs are a whole literal. **The other 27 are composed** — kanji numeral +
counter suffix, or a fragment + a `chosenSecondsLabel(...)` + a `→` + an exercise name — assembled
with `+`, string templates, `buildString`, and `joinToString(" ・ ")` / `joinToString("、")`. The 30
rows below are built from **57 raw Japanese fragments** in the source. A table lookup replaces none of
them on its own; each function needs a parameterised message, and 8 of them need word-order freedom
English does not get from concatenation.

| key | ja | context | notes |
|---|---|---|---|
| `gym.session.reps.overrunAnnouncement` | `目安を過ぎました` | `const OVERRUN_ANNOUNCEMENT` :35 | polite TalkBack announcement, fired once on crossing the pacing estimate |
| `gym.session.rest.extend.label` | `＋二十秒` | `const EXTEND_REST_LABEL` :38 | **drawn**, 14.sp ls 2.sp, `RestPage.InlineControl`. Kanji numeral baked into the literal |
| `gym.session.rest.skip.label` | `とばす ▷` | `const SKIP_REST_LABEL` :41 | drawn, same control row. Glyph is part of the string |
| `fmt.session.chosenSeconds` | `{kanji}秒` | `chosenSecondsLabel` :72 | a duration the user **set**. §Q10: 六十秒, never 一分 |
| `gym.session.counter.lastRound` | `最後の巡` | `counterLabel` :101 | replaces the round clause on the final round |
| `fmt.session.counter.round` | `{kanji}巡目` | `counterLabel` :102 | dropped entirely when `totalRounds <= 1` |
| `fmt.session.counter.station` | `{kanji}種目中 {kanji}` | `counterLabel` :105 | "n-of-m" with the total **first** — reversed in English |
| `gym.session.counter.separator` | ` ・ ` | `counterLabel` :109 | joins the two clauses |
| `fmt.session.rounds.overflow` | `{kanji}巡目 / {kanji}巡` | `roundsOverflowLabel` :120 | replaces the dots past nine rounds |
| `fmt.session.rounds.dotsA11y` | `{kanji}巡目、{kanji}巡中` | `cycleDotsLabel` :124 | the dot row's one merged node |
| `fmt.session.progress` | `全体 {kanji}パーセント` | `progressLabel` :134 | the session hairline's `contentDescription`; percent spelled as katakana, not `%` |
| `gym.session.next.complete` | `次 ・ 完了` | `nextUpLabel` :146 | drawn in `NEXT_UP_SLOT` |
| `fmt.session.next.rest` | `次 ・ 休息 {secs}` (+ ` → {name}`) | `nextUpLabel` :148-149 | three-part composition; the `→` is load-bearing |
| `fmt.session.next.exercise` | `次 ・ {name}` | `nextUpLabel` :152 | |
| `gym.session.next.completeA11y` | `次、完了` | `nextUpDescription` :161 | `、` where the visible form uses ` ・ ` |
| `fmt.session.next.restA11y` | `次、休息 {secs}` (+ `、そのあと {name}`) | `nextUpDescription` :163-164 | `そのあと` spells out what `→` cannot be read as |
| `fmt.session.next.exerciseA11y` | `次、{name}` | `nextUpDescription` :167 | |
| `fmt.session.countdown.announce` | `残り {kanji}秒` | `countdownAnnouncement` :200,203 | only ever 三十 or 十; the kanji comes from `kanjiExtended(30)` / `(10)`, not a literal |
| `fmt.session.prescription.reps` | `{kanji}回` | `prescriptionLabel` :220 | |
| `gym.session.prescription.maxEffort` | `限界まで` | `prescriptionLabel` :221, `repHero` :229 | **also the REPS hero at 76.sp** — see hazard H3 |
| `fmt.session.reps.hero` | `{kanji}回` | `repHero` :229 | 76.sp hero |
| `fmt.session.reps.doneA11y` | `済、{kanji}回として記録` | `repDoneDescription` :238 | |
| `gym.session.reps.doneA11yBare` | `済` | `repDoneDescription` :238 | the 限界まで case; deliberately not given a sentence |
| `fmt.session.pacer.emom` | `残り {m:ss}` | `pacerLabel` :254 | arabic |
| `fmt.session.pacer.overrun` | `＋{m:ss}` | `pacerLabel` :256 | arabic, counts **up**, truncates |
| `fmt.session.pacer.estimate` | `目安 {m:ss}` | `pacerLabel` :257 | arabic |
| `gym.session.rest.round` | `巡の間` | `restLabel` :268 | ring label, 15.sp ls 6.sp |
| `gym.session.rest.mandated` | `決められた休息` | `restLabel` :269 | ring label — the **longest** of the four, 7 glyphs |
| `gym.session.rest.emomRemainder` | `残り` | `restLabel` :270 | ring label |
| `gym.session.rest.station` | `休息` | `restLabel` :271 | ring label, the default |
| `fmt.session.rest.extendedSuffix` | `＋{m:ss}` | `extendedSuffix` :276 | accent suffix beside the ring label |
| `fmt.session.rest.addedState` | `{kanji}秒 追加済み` | `addedStateDescription` :280 | `stateDescription` on ＋二十秒 |
| `fmt.session.rest.extendA11y` | `{kanji}秒 追加` | `extendRestDescription` :283 | always 二十; from `kanjiExtended(20)` |
| `gym.session.rest.skipDisabled` | `とばす、決められた休息のため使えません` | `skipDisabledDescription` :292 | disabled-control reason, MANDATED only |
| `gym.session.rest.disabledReason` | `、決められた休息のため使えません` | `extendDisabledDescription` :305 | **the same clause reused as a suffix** on the extend control |
| `fmt.session.paused.elapsed` | `{durationKanji} 経過` | `elapsedLine` :308 | measured duration → §Q10's other half |
| `fmt.session.paused.accruedStations` | `{kanji}種目 済` | `accruedLine` :313-314 | |
| `fmt.session.paused.accruedBoth` | `{kanji}種目 ・ {kanji}巡 済` | `accruedLine` :315 | the ` 済` binds to both clauses |
| `fmt.session.paused.announce` | `休止中、{durationKanji} 経過、{kanji}種目 済` | `pausedAnnouncement` :320-321 | three clauses, one utterance |
| `fmt.session.paused.numeralA11y` | `残り {kanji}秒、休止中` | `pausedNumeralDescription` :325 | the frozen countdown, spoken in **kanji** while drawn in **arabic** — see H1 |
| `fmt.session.prepare.announce` | `支度、{kanji}秒後に {name}` | `prepareAnnouncement` :329-330 | |
| `gym.session.prepare.announceBare` | `支度` | `prepareAnnouncement` :330 | when no exercise is known |
| `fmt.session.rest.announce` | `{restLabel} {secs}` + `、次は {name}` + `、{prescription}` + `、{cue}` | `restAnnouncement` :339-361 | `buildString` over four optional clauses; the `、次は ` fragment is :350 |
| `fmt.session.reps.heroA11y` | `{name}、{repHero}` | `repHeroDescription` :364-365 | `joinToString("、")` |
| `fmt.session.quit.summary` | `{durationKanji} ・ {kanji}種目中 {kanji}` | `quitSummaryLine` :368-371 | the quit sheet's subtitle |
| `gym.session.quit.discard` | `記録せずに終える` | `quitOptions` :396 | destructive row, when there is work to lose |
| `gym.session.quit.discardNothing` | `終える` | `quitOptions` :403 | the softened form at zero results |

47 rows. `formatCountdown` (:60) and `prepareNumeral` (:69) produce **no Japanese** — arabic `0:23` and
a bare integer — and are listed under Hazards rather than here.

## app/src/main/java/io/eddiegulay/tempo/ui/gym/session/LivePlayer.kt

Purpose: the shared chrome of the four live pages — ✕, the counter line, the ensō box, the fault line,
the three-glyph control bar — plus the sheet container and the invisible announcement node.

| key | ja | context | notes |
|---|---|---|---|
| `gym.session.quit.glyphA11y` | `鍛錬を終える` | `QuitGlyph` :508 | `contentDescription` on ✕; the glyph itself is not translated |
| `gym.session.controls.backA11y` | `前へ、二回押すと一つ戻る` | `liveControls` :605 | **the only way a TalkBack user learns ◁ is a double-tap** |
| `gym.session.controls.backPrepareA11y` | `戻る` | `liveControls` :605, :611 | used as both description and `disabledReason` in 支度 |
| `gym.session.controls.pauseA11y` | `休止` | `liveControls` :616 | on ┃┃ |
| `gym.session.controls.forwardA11y` | `とばす` | `liveControls` :621 | on ▷ |

The bar glyphs (`◁`, `┃┃`, `▷`, `▶`, `✕`, `→`, `‹`, `›`) are `BarControl.glyph` strings and are
**not** copy. `faultCopy(fault).message` (:338) is drawn here but owned by `ui/Fault*.kt`.

## app/src/main/java/io/eddiegulay/tempo/ui/gym/session/WorkPage.kt

Zero user-visible Japanese literals. Every word it draws comes from `PlayerCopy.kt` or from
`state.exercise?.nameJa`. It is nevertheless the file that fixes the player's three constraining
slots — `NEXT_UP_SLOT = 64.dp` (:114), `ROUND_INDICATOR_SLOT = 18.dp` (:193) and `ExerciseHeading`'s
`maxLines = 1` (:129) — all quoted under Hazards.

## app/src/main/java/io/eddiegulay/tempo/ui/gym/session/RepsPage.kt

Purpose: 運動・回数, the self-paced set. 済 is the only way forward; a long press opens the rep wheel.

| key | ja | context | notes |
|---|---|---|---|
| `gym.session.reps.done` | `済` | `DoneButton` :202 | 20.sp ls 4.sp, centred in a 64.dp × full-width button. One glyph |
| `gym.session.reps.adjust` | `回数を変える` | :191, :196, :197 | declared **three times** — `onLongClickLabel`, `onLongClick(label=)`, `CustomAccessibilityAction`. All three must stay identical |
| `gym.session.reps.adjustTitle` | `回数を変える` | `RepWheelSheet` :232 | same string, different role (sheet heading, 16.sp ls 2.sp) — merge may or may not want one key |
| `gym.session.reps.record` | `記録する` | :242, :249 | the wheel's confirm; label and `contentDescription` |

## app/src/main/java/io/eddiegulay/tempo/ui/gym/session/RestPage.kt

Purpose: 休息, with the next movement promoted to hero. Pixel-identical chrome to 運動.

| key | ja | context | notes |
|---|---|---|---|
| `gym.session.rest.next` | `つぎ` | :119 | inside the ring, under the countdown. 12.sp ls 4.sp, hiragana |
| `gym.session.rest.skipA11y` | `とばす` | `InlineRestControls` :176 | `description` for the inline ▷; the *visible* label is `SKIP_REST_LABEL` |

## app/src/main/java/io/eddiegulay/tempo/ui/gym/session/PreparePage.kt

Purpose: 支度 — five seconds to put the phone down.

| key | ja | context | notes |
|---|---|---|---|
| `gym.session.prepare.title` | `支度` | :72 | inside the ring, 15.sp ls 6.sp, above an 88.sp bare integer |

## app/src/main/java/io/eddiegulay/tempo/ui/gym/session/PausedPage.kt

Purpose: 休止 — the session frozen. Replaces the body; never cross-faded.

| key | ja | context | notes |
|---|---|---|---|
| `gym.session.paused.title` | `休止` | :96 | ring label, 15.sp ls 6.sp |
| `gym.session.paused.stalled` | `長い間 動きがありません` | :143 | the 30-minute guard fired. 12.sp, no `maxLines`; the internal space is a deliberate break point |
| `gym.session.paused.resume` | `続ける` | :188, :217 | 20.sp ls 4.sp button label, **and** the ▶ control's description |
| `gym.session.paused.resumeNote` | `三秒の支度から` | :194 | second line inside the same 64.dp button, 11.sp |
| `gym.session.paused.resumeLongA11y` | `続ける、三秒の支度から` | :181 | the two lines as one node |

## app/src/main/java/io/eddiegulay/tempo/ui/gym/session/CompletePage.kt

Purpose: 記録, the player's terminal screen. Draws a header and delegates every other pixel to
`RecordSummary` in `RecordMode.Live`.

| key | ja | context | notes |
|---|---|---|---|
| `gym.session.complete.close` | `閉じる` | :83, :84 | label + description. **Deliberately not `とじる`** — `SessionDetailScreen` uses the hiragana form for the same act on the historical page. Two words, two pages, documented in both files' KDoc |

## app/src/main/java/io/eddiegulay/tempo/ui/gym/session/QuitSheet.kt

Purpose: 鍛錬を終えますか — three honest outcomes for a session in progress. The sheet a user is most
likely to mis-tap.

| key | ja | context | notes |
|---|---|---|---|
| `gym.session.quit.armed` | `本当に消しますか` | `const DISCARD_ARMED_LABEL` :57 | replaces the discard row's label for 3s after the first tap |
| `gym.session.quit.armedA11y` | `本当に消しますか、もう一度 押すと消えます` | `const DISCARD_ARMED_DESCRIPTION` :60 | **assertive** live region — the one place interrupting is right |
| `gym.session.quit.discardA11y` | `記録せずに終える、これまでの記録は消えます` | `const DISCARD_DESCRIPTION` :63 | composes `quitOptions`' label with its consequence — but as a *separate literal*, so the two can drift |
| `gym.session.quit.title` | `鍛錬を終えますか` | :181 | 16.sp ls 2.sp; TalkBack focus lands here, not on a destructive row |
| `gym.session.quit.nothingToSave` | `まだ 記録するものがありません` | :188 | subtitle when `resultsWritten == 0` |
| `gym.session.quit.save` | `ここまでを記録する` | :199, :200 | accent row |
| `gym.session.quit.continue` | `つづける` | :229, :230 | **never labelled `やめる`** — the calendar composer already uses やめる to mean "abandon". Explicitly do not "fix" this |
| `gym.session.quit.saveFailed` | `記録できませんでした` | :265 | polite live region |
| `gym.session.quit.saveFailedSeparator` | ` ・ ` | :270 | its own `Text` between the message and the retry |
| `gym.session.quit.retry` | `もう一度` | :274, :279 | |

## app/src/main/java/io/eddiegulay/tempo/ui/gym/session/SessionUnrecoverablePage.kt

Purpose: a session that exists on disk and cannot be replayed. The quit sheet's two outcomes with
つづける removed.

Its KDoc claims "not one string here is new" — true of the three constants it imports from
`QuitSheet.kt` and of `quitOptions`, but **three sentences are re-declared as literals here** and must
be merged to the same keys, not to new ones:

| key | ja | context | notes |
|---|---|---|---|
| `gym.session.quit.title` | `鍛錬を終えますか` | :88 | duplicate of `QuitSheet.kt:181` |
| `gym.session.quit.nothingToSave` | `まだ 記録するものがありません` | :94 | duplicate of `QuitSheet.kt:188` |
| `gym.session.quit.save` | `ここまでを記録する` | :103, :104 | duplicate of `QuitSheet.kt:199-200` |

`state.routineName` is drawn as the page heading (26.sp ls 3.sp) — a database value, see H9.

## Session files with zero user-visible Japanese

- `SessionClock.kt` — pure arithmetic over `elapsedRealtime`. Japanese appears only in KDoc.
- `SessionContract.kt` — the state/action types. Its own header comment states the rule: *"nothing in
  this file is a Japanese string"*, and that holds.
- `SessionHost.kt` — the controller and the immersive window. 1098 lines, no user-visible literal.
- `SessionProjection.kt` — one frame from `(timeline, elapsedMs)`.
- `SessionReplay.kt` — the host's pure arithmetic (`replayable`, `roundsCompletedOf`, `startsFinalRound`).

---

# Part B — the records pages

## app/src/main/java/io/eddiegulay/tempo/ui/gym/RecordSummary.kt

Purpose: **one component, two entry points** — mounted by `GYM.SESSION.COMPLETE` (`RecordMode.Live`)
and by `GYM.RECORDS.SESSION_DETAIL` (`RecordMode.Historical`). The ensō, the hero time, the three
tiles, the accolades, どうでしたか, the 内訳 and もう一度.

| key | ja | context | notes |
|---|---|---|---|
| `gym.records.summary.heroLabel` | `活動時間` | `recordHeroLabel` :211 | under the 64.sp hero time |
| `gym.records.summary.heroLabelFailedOut` | `到達` | `recordHeroLabel` :211 | an `EMOM_ASCENDING` fail-out: the number means *how far up the ladder*, not *time spent* |
| `fmt.records.summary.failedOutChip` | `{durationKanji}で 力尽きた` | `failedOutChip` :219 | measured duration + a verb phrase |
| `fmt.records.streak` | `{kanji}日 連続` | `streakLine` :259 | null for `null` **and** for `<= 0` — never `〇日 連続` |
| `fmt.records.summary.tileStations` | `{kanji}種目` | `recordTiles`/`tileValue` :276, :282 | unit is in the value, not the label |
| `fmt.records.summary.tileRounds` | `{kanji}巡` | :277, :282 | |
| `fmt.records.summary.tileReps` | `{kanji}回` | :278, :282 | |
| `gym.records.summary.tileLabelStations` | `種目` | :276 | the label under the rule; **same glyphs as the value's suffix** |
| `gym.records.summary.tileLabelRounds` | `巡` | :277 | |
| `gym.records.summary.tileLabelReps` | `回` | :278 | |
| `gym.records.summary.ratingUnset` | `未評価` | `ratingGroupState` :361 | the radio group's `stateDescription` |
| `fmt.records.summary.ratingSelected` | `{rating} を選択` | `ratingGroupState` :361 | `rating.label` is 楽/ちょうど/きつい from `gym/GymModels.kt` (out of scope) |
| `fmt.records.summary.ratingOption` | `{rating}として記録する` | `ratingOptionLabel` :364 | the chip's `onClick` label. `として` is a case particle — pure suffix concatenation |
| `gym.records.summary.firstEver` | `はじめての記録` | :399, :790 | **declared twice** — once in `accoladeSemantics`, once in the `Accolades` composable |
| `gym.records.summary.breakdownHeading` | `内訳` | :541 | passed to `SectionLabel` |
| `gym.records.summary.ratingPrompt` | `どうでしたか` | :847 | 14.sp ls 3.sp heading |
| `gym.records.summary.repeat` | `もう一度` | :968, :974 | description + label |
| `gym.records.summary.routineArchived` | `この型は削除されています` | :986 | under a greyed もう一度 |

The four join-shaped semantics builders (`recordHeroSemantics` :381, `recordTilesSemantics` :393,
`accoladeSemantics` :397, `breakdownSemantics` :407) contain **no Japanese of their own** — they
`joinToString("、")` over values produced elsewhere. The `、` separator is itself a translation
decision (H7).

Values it draws that are owned elsewhere: `heroTime(activeMs)`, `partialChipCopy`, `comparisonCopy`,
`prChip(...).label`, `breakdownRow(...)` (name/duration/status/reps), `Rating.label` — all in
`gym/RecordCopy.kt` and `gym/Numerals.kt`, another agent's scope.

## app/src/main/java/io/eddiegulay/tempo/ui/gym/SessionDetailScreen.kt

Purpose: `GYM.RECORDS.SESSION_DETAIL` — 記録の中身, one finished session reopened. Mounts
`RecordSummary` in `Historical` and adds a header, a footer and a delete confirmation.

| key | ja | context | notes |
|---|---|---|---|
| `gym.records.detail.missingSnapshot` | `当時の内容は残っていません` | `missingSnapshotCopy` :145 | **backup-restore case only**; distinct from `中身が変わっています` on the PR page |
| `fmt.records.detail.date` | `{era} ・ {monthDay}` | `recordDateLine` :163 | `JapaneseDate.era(at)` → 令和八年, `monthDay` → 六月十七日. See H5 |
| `gym.records.detail.openRoutine` | `型を見る` | `RecordFooterAction.OpenRoutine` :176 | enum constructor arg — a `String` field on an enum, not a `@StringRes` |
| `gym.records.detail.delete` | `記録を削除` | `RecordFooterAction.Delete` :177 | same shape; also `RecordsHistoryScreen.HistoryMenuItem.Delete` |
| `gym.records.detail.title` | `記録の中身` | `const PAGE_TITLE` :237 | 26.sp ls 3.sp |
| `gym.records.detail.close` | `とじる` | :506 | **hiragana**, against `CompletePage`'s 閉じる. Deliberate |
| `gym.records.detail.loading` | `読み込み中` | `RecordLoading` :563 | |

The delete dialog draws `sessionDeleteCopy()` — defined in `RecordsHistoryScreen.kt`, listed there.

## app/src/main/java/io/eddiegulay/tempo/ui/gym/RecordsIndexScreen.kt

Purpose: `GYM.RECORDS.INDEX` — 記録. The ink grid, the streak block, three tiles, a sparkline, three
最近 rows.

| key | ja | context | notes |
|---|---|---|---|
| `fmt.records.index.tileMonthValue` | `{kanji}回` | `recordsTiles` :140 | |
| `fmt.records.index.tileActiveValue` | `{kanji}分` | :141 | **minutes truncate**, and there is no hours form anywhere in the app |
| `fmt.records.index.tileLifetimeValue` | `{kanji}回` | :142 | absent tile when the read has not answered — never `〇回` |
| `gym.records.index.tileMonth` | `今月` | `const TILE_MONTH` :147 | always the **current** month, never the paged one |
| `gym.records.index.tileActive` | `活動時間` | `const TILE_ACTIVE` :148 | same string as `RecordSummary.recordHeroLabel` |
| `gym.records.index.tileLifetime` | `これまで` | `const TILE_LIFETIME` :149 | same string as `RecordsHistoryScreen.PAGE_TITLE` and `RecordsPr/Charts`' `これまでを見る` stem |
| `fmt.records.index.monthPager` | `{kanji}月` | `monthPagerLabel` :222 | bare month while the year matches; falls through to `recordsSubtitle` otherwise |
| `fmt.records.subtitle` | `{era} ・ {kanji}月` | `recordsSubtitle` :244 | |
| `gym.records.index.weekdays` | `日 月 火 水 木 金 土` | `val RECORDS_WEEKDAYS` :254 | **seven single-glyph strings**, each centred in a `GRID_CELL = 20.dp` box. Pinned against `JapaneseDate.dayOfWeek` by `RecordsIndexScreenTest` |
| `gym.records.index.title` | `記録` | `const PAGE_TITLE` :317 | |
| `gym.records.index.weeklyHeading` | `週ごと` | `const HEADING_WEEKLY` :318 | |
| `gym.records.index.recentHeading` | `最近` | `const HEADING_RECENT` :319 | |
| `gym.records.index.detail` | `詳しく` | `const ACTION_DETAIL` :320 | header action **and** the sparkline's `onClickLabel` |
| `gym.records.index.seeAll` | `すべて見る` | `const ACTION_SEE_ALL` :321 | |
| `gym.records.index.bests` | `最高` | `const ACTION_BESTS` :322 | also `RecordsPrScreen.PAGE_TITLE` |
| `gym.records.index.gridA11y` | `記録の一覧をひらく` | `InkGrid` :743 | `clickable(onClickLabel = …)` on the 42-day canvas |
| `gym.records.index.loading` | `読み込み中` | :532, :952, :1057 | three separate call sites, three deliberately unshared composables |
| `gym.records.index.empty` | `まだ 記録はありません` | `RecordsEmpty` :1076 | **the longer form** — `RecordsHistoryScreen` has its own shorter 記録はありません |
| `gym.records.index.chooseRoutine` | `型をえらぶ` | :1081, :1086 | |
| `gym.records.index.historyLossDismiss` | `とじる` | :1153, :1158 | acknowledgement, **not** a retry |

`recentRowCopy` (:186) composes a row out of `JapaneseDate.monthDay`, `heroTime`,
`summary.rating?.label` and `partialChipCopy` joined with `、` — no literal of its own.
`streakCopy(...)` and `monthCaption(...)` come from `gym/RecordCopy.kt` / `gym/InkDensity.kt`.
`faultCopy(GymFault.StoreCorrupt).message` supplies 記録を読めません (`ui/Fault*.kt`).

## app/src/main/java/io/eddiegulay/tempo/ui/gym/RecordsHistoryScreen.kt

Purpose: `GYM.RECORDS.HISTORY` — これまで. Every finished session, newest first, grouped by month, with
keyset paging.

| key | ja | context | notes |
|---|---|---|---|
| `gym.records.history.empty` | `記録はありません` | `historyEmptyCopy` :154 | unfiltered. **Not** the index's まだ 記録はありません |
| `gym.records.history.emptyFiltered` | `この型は まだ やっていません` | `historyEmptyCopy` :154 | a claim about **one routine**; the two must never be reachable through each other |
| `fmt.records.history.rowRounds` | `{kanji}巡` | `historyRowSemantics` :408 | dropped when 0 |
| `fmt.records.history.rowReps` | `{kanji}回` | :409 | dropped when 0 |
| `gym.records.history.menuDelete` | `記録を削除` | `HistoryMenuItem.Delete` :430 | enum field; same string as `SessionDetailScreen`'s footer |
| `gym.records.history.menuOpenRoutine` | `この型を見る` | `HistoryMenuItem.OpenRoutine` :431 | distinct from `SessionDetailScreen`'s 型を見る |
| `dialog.records.deleteSession.title` | `この記録を削除しますか` | `sessionDeleteCopy` :456 | shared with `SessionDetailScreen` |
| `dialog.records.deleteSession.body` | `元に戻せません。` | :457 | **trailing ideographic full stop** — the only one in this scope |
| `dialog.records.deleteSession.confirm` | `削除` | :458 | |
| `dialog.records.deleteSession.dismiss` | `やめる` | :459 | the calendar composer's abandon word — see the `つづける` note above |
| `gym.records.history.title` | `これまで` | `const PAGE_TITLE` :466 | |
| `gym.records.history.close` | `とじる` | :783 | label + description |
| `gym.records.history.menuA11y` | `メニュー` | `SessionCard` :889 | `onLongClick(label = …)`; katakana |
| `gym.records.history.loading` | `読み込み中` | :995, :999, :1034 | footer live region, footer label, page body |
| `gym.records.history.retry` | `もう一度` | :1009, :1015 | paging footer |

`historyMonthSemantics` (:416) joins `month.header` + `、` + `month.countLabel`; both come from
`gym/HistoryPaging.kt`. `sessionRowLines(summary)` supplies name/duration/detail/rating/chip.
`historySubtitleOrNull` (:252) delegates every word to `historySubtitle` in `gym/RecordCopy.kt`.

## app/src/main/java/io/eddiegulay/tempo/ui/gym/RecordsChartsScreen.kt

Purpose: `GYM.RECORDS.CHARTS` — 移り変わり. Three ink-drawn trends, no library, no taps on the canvases.

| key | ja | context | notes |
|---|---|---|---|
| `gym.records.charts.title` | `移り変わり` | `const PAGE_TITLE` :234 | |
| `gym.records.charts.close` | `とじる` | `const CLOSE` :236 | |
| `gym.records.charts.empty` | `まだ 記録はありません` | `const EMPTY` :239 | the **index's** form, reused here |
| `gym.records.charts.loading` | `読み込み中` | `const LOADING` :241 | |
| `gym.records.charts.seeHistory` | `これまでを見る` | `const SEE_HISTORY` :244 | also `RecordsPrScreen.ACTION_HISTORY` |
| `gym.records.charts.chipSelected` | `選択中` | `RangeChips` :395 | `stateDescription` on the chosen range chip; **no word exists for the unselected state and none is to be invented** |

Everything else on this page is another unit's: `chartHeading` (週ごとの回数 / 週ごとの回数（折れ線）/
活動時間 / 積み上げ), `chartCaption`, `chartSemantics`, `chartAxisLabels`, `chartSuppressionCopy`
(二十八日ぶん たまると 出ます) and `ChartRange.label` (十二週 / 二十六週 / 一年) — all `gym/`.

## app/src/main/java/io/eddiegulay/tempo/ui/gym/RecordsPrScreen.kt

Purpose: `GYM.RECORDS.PR` — 最高. Two tabs: bests by routine, bests by movement.

| key | ja | context | notes |
|---|---|---|---|
| `gym.records.pr.tabRoutines` | `型ごと` | `PrTab.Routines` :92 | enum field; declaration order **is** display order |
| `gym.records.pr.tabMovements` | `動きごと` | `PrTab.Movements` :93 | |
| `gym.records.pr.hardestReached` | `いちばん上` | `const HARDEST_REACHED` :191 | §Q9: this surface's word only; **must not be repurposed** as a routine tile |
| `fmt.records.pr.movementValue` | `{kanji}回` | `prMovementRow` :215 | the best **single set**, 22.sp accent |
| `fmt.records.pr.movementMeta` | `一度に ・ のべ {kanji}回` | :216 | two labels and a number in one line |
| `fmt.records.pr.movementA11ySingle` | `一度に {value}` | :232 | the semantics form splits the meta line differently from the visible one |
| `fmt.records.pr.movementA11yLifetime` | `のべ {kanji}回` | :233 | |
| `gym.records.pr.title` | `最高` | `const PAGE_TITLE` :258 | |
| `gym.records.pr.close` | `とじる` | `const CLOSE` :261 | |
| `gym.records.pr.empty` | `まだ 記録はありません` | `const EMPTY` :264 | |
| `gym.records.pr.movementsEmptyWhy` | `回数を数えた種目だけ ここに出ます` | `const MOVEMENTS_EMPTY_EXPLANATION` :267 | explains an empty tab; a third state beyond empty and failed |
| `gym.records.pr.allExercises` | `すべての種目` | `const ALL_EXERCISES` :270 | |
| `gym.records.pr.archived` | `削除済み` | `const ARCHIVED` :273 | chip; the row stays tappable |
| `gym.records.pr.loading` | `読み込み中` | `const LOADING` :275 | |
| `gym.records.pr.actionSession` | `この記録を見る` | `const ACTION_SESSION` :278 | `CustomAccessibilityAction` on the card |
| `gym.records.pr.actionHistory` | `これまでを見る` | `const ACTION_HISTORY` :279 | second custom action |

`bestValueCopy` / `bestMetricLabel` supply 最速 / 最高巡数 / 最高反復 / 最高負荷, the `中身が変わっています`
note, `row.copy.date`, `row.copy.count` and `row.copy.semantics` — all `gym/RecordCopy.kt` and
`gym/EngineRows.kt`, out of scope.

---

## Hazards

### H1 — the arabic/kanji numeral rule, which does not survive into English *(counters and numerals)*

`DECISIONS.md` §Q4: **a ticking value is arabic; anything that has stopped moving is kanji.**
§Q10 then splits the kanji half: a duration the user **chose** renders as bare kanji seconds, a
duration the app **measured** renders through `durationKanjiFromMs`. The rule is implemented at these
exact sites and nowhere else:

- `PlayerCopy.kt:60` `formatCountdown(remainingMs) = clockDuration(ceilSeconds(ms) * 1000)` — **arabic**
  `0:23`, the WORK/REST/PAUSED hero.
- `PlayerCopy.kt:69` `prepareNumeral` — **arabic bare integer** (`5`), deliberately not `0:05`.
- `PlayerCopy.kt:72` `chosenSecondsLabel = JapaneseDate.kanjiExtended(...) + "秒"` — **kanji**, the
  §Q10 "chosen" half. Documented as "六十秒, never 一分".
- `PlayerCopy.kt:256-257` `pacerLabel` — `＋0:07` and `目安 0:38`, **arabic**, because they tick.
- `PlayerCopy.kt:308, 320, 369` `durationKanjiFromMs` — **kanji**, the §Q10 "measured" half.
- `PlayerCopy.kt:325` `pausedNumeralDescription` — the same instant is drawn **arabic** (`0:23`,
  `PausedPage.kt:101`) and spoken **kanji** (`残り 二十三秒`). One value, two numeral systems, on purpose.
- `RepsPage.kt:236-239` — the rep wheel keeps **arabic** digits mid-spin (`TempoValueWheel(values = 0..99)`)
  and the recorded count reads back as `二十回` everywhere it is displayed. The comment states the
  reason: "a kanji column changing under the finger is unreadable".
- Everything kanji ultimately calls `JapaneseDate.kanjiExtended` (in `data/`, **out of this scope**),
  which is the single kanji numeral formatter for the whole app per §Q7.

**Nothing replaces this in English.** There is one numeral system. Something has to decide, for every
one of the ~25 composed strings in `PlayerCopy.kt` and the ~12 in the records pages, what the English
form is — plainly `0:23` for clocks, `20 s` / `6 min 14 s` for durations, bare integers for counts —
and the §Q4/§Q10 distinction becomes a *unit-formatting* decision rather than a script one. Until it
is made, every `kanjiExtended(x) + "回"` call site is unresolved.

### H2 — pluralisation *(count-bearing strings, all of them)*

Japanese has no plural. Every one of these is `{number}{counter}` with no agreement:
`回` (reps/sessions), `巡` (rounds), `種目` (stations), `日` (days), `分` (minutes), `秒` (seconds),
`パーセント`, `週`. Affected: `PlayerCopy.kt` :73, :102, :105, :120, :124, :134, :200, :203, :220,
:229, :238, :280, :283, :313, :315, :321, :325, :329, :370; `RecordSummary.kt` :259, :276-278;
`RecordsIndexScreen.kt` :140-142; `RecordsHistoryScreen.kt` :408-409; `RecordsPrScreen.kt` :215-216,
:233. English needs `1 round` / `2 rounds` on every one, which means these cannot be
`concat(number, suffix)` after migration.

### H3 — the REPS hero at 76.sp: `限界まで` vs "max effort" *(measurement of text)*

`RepsPage.kt:121-131`:

```kotlin
Text(
    text = repHero(reps),
    softWrap = false,
    maxLines = 1,
    style = TextStyle(fontFamily = Mincho, fontSize = heroSize(76.sp), color = c.ink),
)
```

`repHero` returns either `{kanji}回` (2–4 glyphs) or `限界まで` (4 glyphs). `heroSize` (`LivePlayer.kt:108`)
caps against `LocalHeroCap`, which is:

```kotlin
val cap = with(LocalDensity.current) { maxWidth.toSp() } / 4.2f   // LivePlayer.kt:168
LocalHeroCap provides if (cap.value < 88f) cap else 88.sp
```

**`4.2` is the number of CJK glyphs the designer budgeted** — its KDoc says "4.2 is four glyphs plus
slack for the colon". A CJK glyph advances ~1 em; a latin glyph advances ~0.5 em, so an English string
of 10+ characters ("max effort") at the same cap overflows, and with `softWrap = false` and no
`TextOverflow` it is **clipped, not ellipsised**. This is the single highest-risk string in the player.

### H4 — every other fixed slot and `maxLines` in the player *(measurement of text)*

| composable | file:line | constraint | string drawn |
|---|---|---|---|
| WORK countdown | `WorkPage.kt:75-86` | `heroSize(88.sp)`, `softWrap = false`, `maxLines = 1`, `tnum` | `formatCountdown` — digits only, safe |
| REST countdown | `RestPage.kt:106-117` | `heroSize(72.sp)`, `softWrap = false`, `maxLines = 1` | digits only |
| PREPARE numeral | `PreparePage.kt:76-89` | `heroSize(88.sp)`, `softWrap = false`, `maxLines = 1` | bare integer |
| PAUSED countdown | `PausedPage.kt:100-114` | `heroSize(88.sp)`, `softWrap = false`, `maxLines = 1` | digits only |
| `ExerciseHeading` | `WorkPage.kt:124-142` | 24.sp, **`maxLines = 1`, no `overflow`** → clipped | `exercise.nameJa` |
| next-up slot | `WorkPage.kt:93-102` + `NEXT_UP_SLOT = 64.dp` (:114) | fixed 64.dp box, 13.sp, centred, no `maxLines` | `nextUpLabel` — `次 ・ 休息 十五秒 → プランク` is already 13 glyphs; "next · rest 15 s → plank" is ~24 chars |
| round indicator | `WorkPage.kt:171` + `ROUND_INDICATOR_SLOT = 18.dp` (:193) | fixed 18.dp **height** | `roundsOverflowLabel` at 12.sp ls 3.sp |
| counter line | `LivePlayer.kt:284-295` | inside a fixed `height(72.dp)` header, centred between a 48.dp ✕ and 16.dp padding, 12.sp **ls 3.sp**, no `maxLines` | `counterLabel` — `三巡目 ・ 四種目中 三` = 9 glyphs at ls 3 ≈ 135 dp; "round 3 · station 3 of 4" is far wider and will wrap into the ✕'s row |
| `UpcomingBlock` | `LivePlayer.kt:395-435` | **three fixed `FixedLine` heights: 34.dp / 22.dp / 18.dp**, at 26.sp / 14.sp / 12.sp, none with `maxLines` | exercise name, `prescriptionLabel`, `exercise.cue`. A wrap is clipped by the box, and the block deliberately never re-centres |
| ring labels | `RestPage.kt:91-94`, `PreparePage.kt:71-74`, `PausedPage.kt:95-98` | 15.sp **ls 6.sp**, inside a 220.dp ring, in a `Row` with `extendedSuffix` on REST | `休息` (2) / `巡の間` (3) / `決められた休息` (7) / `支度` / `休止`. The 6.sp letter-spacing means 7 glyphs already ≈ 150 dp; "mandated rest" will not fit beside `＋0:20` |
| `InlineControl` ×2 | `RestPage.kt:158-182, 209-218` | two controls in a centred `Row` with a **fixed `Spacer(40.dp)`**, each `sizeIn(minWidth = 48.dp)` + 12.dp horizontal padding, 14.sp ls 2.sp | `＋二十秒` and `とばす ▷`. "+20 s" and "Skip ▷" are wider; the 40.dp gap is not flexible |
| `ResumeButton` | `PausedPage.kt:172-199` | fixed `height(NEXT_UP_SLOT)` = 64.dp holding a **two-line Column**: 20.sp + 11.sp | `続ける` / `三秒の支度から`. "Resume" over "from a 3-second countdown" wraps the second line and clips |
| `HeroTime` | `RecordSummary.kt:661-676` | `cap = (maxWidth.value / (glyphs * density.fontScale)).sp`, `maxLines = 1`, `softWrap = false` | **the divisor is `text.length`**, which its KDoc justifies with "a CJK glyph advances almost exactly one em". For a latin hero time the cap is over-tight — safe in direction, wrong in principle |
| `RECORDS_WEEKDAYS` | `RecordsIndexScreen.kt:254, 720-733` | each letter centred in `Box(Modifier.width(GRID_CELL))`, `GRID_CELL = 20.dp`, 10.sp | seven **one-glyph** strings. English `S M T W T F S` fits, but two pairs collide ambiguously and the list is pinned against `JapaneseDate.dayOfWeek` by a test |
| history/PR cards | `RecordsHistoryScreen.kt:902-908, 914-926`; `RecordsPrScreen.kt:509-514, 647-652, 678-684` | `maxLines = 1` / `maxLines = 2` **with `TextOverflow.Ellipsis`** | routine and exercise names — these degrade gracefully |

### H5 — dates and relative time *(dates)*

- `SessionDetailScreen.kt:161-164` `recordDateLine` = `JapaneseDate.era(at) + " ・ " + JapaneseDate.monthDay(at)`
  → `令和八年 ・ 六月十七日`. **Japanese imperial era.** There is no English equivalent that is not a
  different calendar.
- `RecordsIndexScreen.kt:243-244` `recordsSubtitle` = `era + " ・ " + kanji(month) + "月"`.
- `RecordsIndexScreen.kt:220-225` `monthPagerLabel` — a **conditional format**: bare `{kanji}月` while
  the year matches the clock's, and the full `recordsSubtitle` form otherwise. The condition exists
  because two Junes rendered identically was a shipped bug; the same condition has to survive.
- `RecordsIndexScreen.kt:254` `RECORDS_WEEKDAYS` — Sunday-first, matching `monthCells`' `leadingBlank`.
  A locale that starts the week on Monday needs the *grid arithmetic* changed too, not just the labels.
- `RecordsIndexScreen.kt:189`, `RecordsHistoryScreen.kt:405`, `RecordsPrScreen.kt:217` all call
  `JapaneseDate.monthDay(...)` → `六月十七日`.
- `gym/RecordCopy.kt`'s `chartAxisLabels` produces `三月三十日` / `今週` (a relative-time word) and is
  drawn by `RecordsChartsScreen.ChartAxisRow` :763-775.

All of `JapaneseDate` is in `data/` — **out of this scope**, and it is the single largest shared
dependency of Part B.

### H6 — composed strings whose word order will not survive *(composed strings)*

Recorded as full compositions, not fragments:

- `counterLabel` (`PlayerCopy.kt:92-110`) — `{n}種目中 {m}` puts the **total before the index**.
  English is "m of n". The `listOfNotNull(rounds, stations).joinToString(" ・ ")` also means the two
  clauses are independently droppable, which a single message string cannot express.
- `quitSummaryLine` (:368-371) — same `{total}種目中 {done}` inversion, plus a duration in front.
- `nextUpLabel` / `nextUpDescription` (:145-169) — four branches each, one of which nests a
  `chosenSecondsLabel` and an arrow between two nouns.
- `restAnnouncement` (:339-361) — `buildString` over **four** optional clauses appended with `、`.
- `prepareAnnouncement` (:328-331) — `支度、{n}秒後に {name}`: the postposition `後に` binds to the
  number, and the name follows. English reverses this ("{name} in {n} seconds").
- `accruedLine` (:311-316) — the trailing ` 済` binds to *both* preceding clauses.
- `extendDisabledDescription` (:304-305) — deliberately built by **appending a reason clause to another
  function's output** (`extendRestDescription() + "、決められた…"`). Two translation units glued at run time.
- `ratingOptionLabel` / `ratingGroupState` (`RecordSummary.kt:360-364`) — `{rating.label}として記録する`
  and `{rating.label} を選択` append case particles to an enum's display name.
- `prMovementRow` (`RecordsPrScreen.kt:213-238`) — the *visible* meta line and the *spoken* one split
  the same two facts differently (`一度に ・ のべ {n}回` vs `一度に {v}` + `のべ {n}回`).

### H7 — the two separators are copy *(composed strings)*

` ・ ` (visible) and `、` (spoken) are used as structural joiners in at least 20 places:
`PlayerCopy.kt` :109, :148, :315, :350, :364, :369; `QuitSheet.kt:270` (its own `Text` node);
`RecordSummary.kt` :385, :394, :403, :410; `RecordsIndexScreen.kt` :197, :244; `RecordsHistoryScreen.kt`
:412, :416; `SessionDetailScreen.kt:163`; `RecordsPrScreen.kt:236`. The ideographic comma and the
katakana middle dot are Japanese punctuation; English wants `, ` and ` · ` or ` — `, and the *spoken*
separator for TalkBack may want to differ from the visible one in a different way than it does now.
`RecordsHistoryScreen.kt:457`'s `元に戻せません。` is the only trailing `。` in the scope.

### H8 — one string, two words, on purpose — do not merge *(translation policy)*

Three pairs in this scope are deliberately different Japanese words for what English would render
identically. Merging them during migration would silently undo a documented decision:

- `閉じる` (`CompletePage.kt:83`) vs `とじる` (`SessionDetailScreen.kt:506`, `RecordsHistoryScreen.kt:783`,
  `RecordsChartsScreen.kt:236`, `RecordsPrScreen.kt:261`, `RecordsIndexScreen.kt:1158`). Both "close".
- `つづける` (`QuitSheet.kt:229`) vs `やめる` (`RecordsHistoryScreen.kt:459`). `QuitSheet`'s KDoc is
  explicit: *"do not 'fix' this to match the composer"* — the escape from the quit sheet must never be
  labelled with the word that means "abandon" elsewhere.
- `まだ 記録はありません` (`RecordsIndexScreen.kt:1076`, `RecordsChartsScreen.kt:239`, `RecordsPrScreen.kt:264`)
  vs `記録はありません` (`RecordsHistoryScreen.kt:154`). Two lengths, two pages, sourced separately.
- `型を見る` (`SessionDetailScreen.kt:176`) vs `この型を見る` (`RecordsHistoryScreen.kt:431`).

Conversely, **four strings are genuinely duplicated and should collapse to one key**: 鍛錬を終えますか,
まだ 記録するものがありません and ここまでを記録する across `QuitSheet.kt` and
`SessionUnrecoverablePage.kt`; はじめての記録 twice inside `RecordSummary.kt` (:399, :790).

### H9 — Japanese *values* from the database, drawn by every file in this scope *(persistence)*

**No literal in this scope is written to the database.** `SessionHost.runWrites` (:667-755) persists
`SegmentResult` drafts, clock checkpoints, `finishSession`, `discardSession` and `rateSession` — the
last stores `rating?.name` (`EASY`/`JUST_RIGHT`/`HARD`, ASCII) plus a frozen `cr10`, never
`Rating.label`. `ScalingTier` matches on `it.name`. Routes carry ids and keys.

But Japanese **content** reaches these screens from storage and from the seeded catalogue, and no
string table in this scope can translate it:

- `exercise.nameJa` and `exercise.cue` — drawn at 24-26.sp on WORK, REPS, REST, PREPARE, PAUSED, and
  inside `nextUpLabel`, `restAnnouncement`, `repHeroDescription`, the 内訳 rows.
- `summary.routineName` — the record hero (`RecordSummary.kt:603`), the unrecoverable page's heading
  (`SessionUnrecoverablePage.kt:82`), every history and 最近 row. This is a **denormalised column on the
  `session` row**, frozen at write time precisely so an archived or renamed routine still names itself
  in history (`RecordsHistoryScreen.kt:610` reads it off the row, never a join).
- `SessionDetailScreen.pinnedExerciseNames` (:126-129) resolves the breakdown from the **pinned
  `routine_version` snapshot**, falling back to the live catalogue.

Consequence: switching language cannot retranslate history. A record performed in Japanese keeps a
Japanese routine name in its row for ever, and the pinned snapshot keeps Japanese exercise names.
Either catalogue content becomes a `catalog.*` key resolved at *read* time (which the denormalised
`routine_name` column defeats) or history is accepted as mixed-language. This decision belongs above
this scope but is forced by it.

### H10 — TTS and spoken phrasing *(speech)*

Nothing in this scope hard-codes a `Locale`. The player's spoken layer is split in two and both halves
are here:

- **TalkBack** — `Announcement` (`LivePlayer.kt:373-383`) is a 1.dp node whose `contentDescription`
  changes at most once per segment; `RecordSummary.AccoladeAnnouncer` (:746-758) is the same trick.
  Every `*Description` / `*Announcement` function in `PlayerCopy.kt` feeds these. They are phrased for
  *hearing*, not reading — `、` instead of ` ・ `, `そのあと` instead of `→` — so they translate as
  separate strings from their visible twins, never as the same key.
- **The cue engine** — `SessionReplay.cueSpeechFor` (:213-217) and `upcomingExerciseName` (:220-225)
  hand the engine an `exercise.nameJa` to *speak*. The engine and its fixed phrases are `gym/cue/`,
  out of scope, but this is the seam: a spoken exercise name in the wrong language will be
  mispronounced by the TTS voice regardless of what the string table says.

### H11 — tests asserting Japanese literals *(tests)*

These break on migration. Counted by Japanese-bearing string literals, not by test methods:

| test | JP literals | covers |
|---|---|---|
| `app/src/test/java/io/eddiegulay/tempo/gym/session/ui/PlayerCopyTest.kt` | **62** | every function in `PlayerCopy.kt` |
| `app/src/test/java/io/eddiegulay/tempo/gym/session/ui/DiscardArmTest.kt` | 3 | the quit-sheet constants |
| `app/src/test/java/io/eddiegulay/tempo/gym/session/ui/PlayerWiringTest.kt` | 2 | |
| `app/src/test/java/io/eddiegulay/tempo/gym/session/ui/SessionProjectionTest.kt` | 1 | fixture name only |
| `app/src/test/java/io/eddiegulay/tempo/gym/ui/RecordSummaryTest.kt` | 32 | `RecordSummary.kt`'s pure half |
| `app/src/test/java/io/eddiegulay/tempo/gym/ui/RecordsHistoryScreenTest.kt` | 22 | empty copy, delete dialog, row semantics |
| `app/src/test/java/io/eddiegulay/tempo/gym/ui/SessionDetailScreenTest.kt` | 10 | date line, missing-snapshot copy |
| `app/src/test/java/io/eddiegulay/tempo/ui/gym/RecordsIndexScreenTest.kt` | 31 | tiles, pager label, subtitle, weekdays |
| `app/src/test/java/io/eddiegulay/tempo/ui/gym/RecordsIndexScreenStructureTest.kt` | 18 | asserts the unshared-state strings exist in source |
| `app/src/test/java/io/eddiegulay/tempo/ui/gym/RecordsPrCopyTest.kt` | 33 | movement rows, tab labels |
| `app/src/test/java/io/eddiegulay/tempo/ui/gym/RecordsPrAndChartsStructureTest.kt` | 5 | |
| `app/src/test/java/io/eddiegulay/tempo/ui/gym/RecordsChartsCopyTest.kt` | 2 | |

**221 Japanese literal assertions across 12 test files.** `PlayerCopyTest.kt` alone is a
line-by-line transcription of the copy module and will need rewriting rather than updating.
`SessionHostTest.kt`, `SessionClockTest.kt`, `SessionReplayTest.kt` and `RecordAnnouncerTest.kt`
contain **zero** and are unaffected.

Two of the structure tests (`RecordsIndexScreenStructureTest`, `RecordsPrAndChartsStructureTest`)
assert on the **source text** of the screen files, so they break on the migration edit itself, not
only on behaviour.

### H12 — fonts *(fonts)*

Every `Text` in this scope names `Mincho` or `Gothic` explicitly (`ui/theme/`), and the player leans
on `letterSpacing` values tuned for square CJK advances — 6.sp on the ring labels, 4.sp on 済 and
つぎ, 3.sp on the counter and the page titles, 2.sp elsewhere. Applied to latin text these read as
tracked-out display type, not as body copy. `fontFeatureSettings = "tnum"` on the four hero numerals
(`WorkPage.kt:83`, `RestPage.kt:114`, `PreparePage.kt:86`, `PausedPage.kt:110`) is correct for digits
in any script and should stay.

### H13 — sorting and filtering

Nothing in this scope sorts or filters on text. `prRoutineRows` / `prMovementRows`
(`RecordsPrScreen.kt:142, :251`) sort by timestamp — explicitly *"momentum, not an alphabet"*.
`groupByMonth` groups by date. `HistoryLoad` pages by a `(started_at, id)` keyset. No kana ordering
and no substring matching anywhere in Part A or Part B.

## Non-visible Japanese

**None.** Nothing in these 21 files is a Japanese literal that a user cannot reach. There are no
Japanese log tags, no Japanese enum storage keys, no Japanese DB column values written from here, and
no Japanese test fixtures inside `app/src/main`. Every literal listed above is drawn, spoken by
TalkBack, or both.

The nearest thing to an exception is `SessionContract.kt` and `SessionHost.kt`, which are dense with
Japanese in **KDoc and comments** (`鍛錬を終えますか`, `休止`, `目安で自動的に進む`, `続ける`) — prose
about the UI, never a value. It is not user-visible and is out of the migration's path, but a
find-and-replace over these files will hit it, and a merge step that greps for Japanese rather than
for string literals will produce hundreds of false positives here.
