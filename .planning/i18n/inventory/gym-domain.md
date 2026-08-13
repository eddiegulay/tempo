# 鍛錬 domain layer (`gym/` top level, `gym/cue/`, `gym/session/`)

**Files surveyed:** 41   **User-visible literals:** 138   **Non-visible JP literals:** 11

Scope: `app/src/main/java/io/eddiegulay/tempo/gym/` — the 25 top-level files plus `cue/` (11) and
`session/` (5). `gym/data/` is another agent's.

**The headline for the merge:** this is a domain layer that draws. Five files —
`GymModels.kt`, `RecordCopy.kt`, `EngineRows.kt`, `ChartGeometry.kt`, `LibraryFilters.kt` — hold 91
of the 138 literals between them, and none of them is a composable. 23 of those live in **enum
constructor arguments**, which cannot be re-resolved on a language change without a structural
edit. A further ~60 are produced by string concatenation around `JapaneseDate.kanjiExtended`, so
they are not table lookups at all — they are a formatter layer that does not exist yet. Roughly
55% of this fragment is a domain refactor, not a string swap.

---

## app/src/main/java/io/eddiegulay/tempo/gym/GymModels.kt

Purpose: the data-layer vocabulary — every enum and record type the repository signatures use. It
draws nothing itself, but six of its enums carry the words five screens print.

| key | ja | context | notes |
|---|---|---|---|
| `gym.engine.intervalCircuit` | 巡回 | `Engine.INTERVAL_CIRCUIT.label`, :48 | routine subtitle, filter chip, PR row meta |
| `gym.engine.amrap` | 時間内 | `Engine.AMRAP.label`, :49 | |
| `gym.engine.forTime` | 完走 | `Engine.FOR_TIME.label`, :50 | |
| `gym.engine.forTimeWithRest` | 完走 ・ 休息あり | `Engine.FOR_TIME_WITH_REST.label`, :51 | composed with ` ・ ` inside the literal |
| `gym.engine.emom` | 毎分 | `Engine.EMOM.label`, :52 | |
| `gym.engine.emomAscending` | 毎分増 | `Engine.EMOM_ASCENDING.label`, :53 | |
| `gym.engine.fixedSets` | 段階 | `Engine.FIXED_SETS.label`, :54 | |
| `gym.pattern.horizontalPush` | 押す | `Pattern.H_PUSH.label`, :76 | station picker headings, exercise detail |
| `gym.pattern.verticalPull` | 引く | `Pattern.V_PULL.label`, :77 | |
| `gym.pattern.squat` | しゃがむ | `Pattern.SQUAT.label`, :78 | |
| `gym.pattern.hinge` | 股関節 | `Pattern.HINGE.label`, :79 | |
| `gym.pattern.core` | 体幹 | `Pattern.CORE.label`, :80 | |
| `gym.pattern.locomotion` | 移動 | `Pattern.LOCOMOTION.label`, :81 | |
| `gym.pattern.plyo` | 跳ぶ | `Pattern.PLYO.label`, :82 | |
| `gym.measure.reps` | 回数 | `Measure.REPS.label`, :102 | builder's はかり方 chips |
| `gym.measure.duration` | 秒数 | `Measure.DURATION.label`, :103 | |
| `gym.measure.maxEffort` | 限界まで | `Measure.MAX_EFFORT.label`, :104 | |
| `gym.tier.beginner` | 入門 | `Tier.BEGINNER.label`, :125 | **also the DB column value** — see hazards |
| `gym.tier.intermediate` | 中級 | `Tier.INTERMEDIATE.label`, :126 | **also the DB column value** |
| `gym.tier.advanced` | 上級 | `Tier.ADVANCED.label`, :127 | **also the DB column value** |
| `gym.rating.easy` | 楽 | `Rating.EASY.label`, :198 | どうでしたか answers; history row rating line |
| `gym.rating.justRight` | ちょうど | `Rating.JUST_RIGHT.label`, :199 | |
| `gym.rating.hard` | きつい | `Rating.HARD.label`, :200 | |

`BestMetric` (:147) and `Phase` (:172) deliberately carry **no** label — their KDoc says so
explicitly, and the wording lives in `EngineRows.bestMetricLabel` / `TrainingNotice.noticePhaseLabel`
instead. Preserve that on migration; do not "tidy" labels back onto them.

---

## app/src/main/java/io/eddiegulay/tempo/gym/RecordCopy.kt

Purpose: every word a finished session gets, in `GYM.SESSION.COMPLETE` (live) and
`GYM.RECORDS.SESSION_DETAIL` / `GYM.RECORDS.HISTORY` (historical). One copy table behind one
component under a `RecordMode`.

| key | ja | context | notes |
|---|---|---|---|
| `fmt.gym.records.comparison.faster` | 前回より {duration} 速い | `comparisonCopy`, :81 | composed; the only comparison sentence that exists — no slower/level/AMRAP form exists and none may be invented |
| `gym.records.pr.current` | 自己最高 | `PrChip.CURRENT.label`, :101 | enum constructor |
| `gym.records.pr.former` | 当時の自己最高 | `PrChip.FORMER.label`, :104 | enum constructor |
| `gym.records.partial` | 途中まで | `partialChipCopy`, :132 | bare form when `stationsPlanned == 0` |
| `fmt.gym.records.partial.detail` | 途中まで ・ {n}種目中 {m} | `partialChipCopy`, :133–134 | composed from two `kanjiExtended` calls; 種目中 is a counter |
| `gym.exercise.unknown` | 不明な種目 | `breakdownRow`, :183 | catalogue no longer knows the id |
| `gym.records.breakdown.skipped` | とばした | `breakdownRow`, :185 | |
| `gym.records.breakdown.done` | 済 | `breakdownRow`, :199 | |
| `fmt.gym.records.reps.actualOfPrescribed` | {n}回 / {m}回 | `breakdownRow`, :191 | composed, ASCII slash |
| `fmt.gym.records.reps` | {n}回 | `breakdownRow`, :192–193 | counter 回 |
| `fmt.gym.records.detail.day` | {n}日 | `sessionRowLines`, :250 | day-of-month, kanji |
| `fmt.gym.records.detail.rounds` | {n}巡 | `sessionRowLines`, :251 | counter 巡; **omitted at zero** |
| `fmt.gym.records.detail.reps` | {n}回 | `sessionRowLines`, :252 | **omitted at zero** |
| `fmt.sep.middot` | ` ・ ` | `sessionRowLines`, :258 | joins the detail fragments |
| `fmt.gym.records.history.subtitle.filtered` | 「{routine}」{n}回 | `historySubtitle`, :282 | corner brackets are copy, not markup |
| `fmt.gym.records.history.subtitle` | {n}回 ・ {m}分 | `historySubtitle`, :285 | |
| `gym.records.streak.broken` | 連続は とぎれています | `streakCopy`, :350 | never 〇日 連続 — see KDoc |
| `fmt.gym.records.streak.days` | {n}日 連続 | `streakCopy`, :352 | note the space before 連続 |
| `fmt.gym.records.streak.forgiveness` | ゆるし {n}回 使いました | `streakCopy`, :355 | never states the remaining budget |
| `gym.records.streak.monotony` | 同じ調子が続いています | `streakCopy`, :364 | gated on `monotony7d > 2.0` and ≥14 days |
| `fmt.sep.ideographicComma` | 、 | `streakCopy`, :373; `bestValueCopy`, :490 | TalkBack semantics joiner |
| `fmt.gym.records.pr.meta` | {metric} ・ {engine} | `bestValueCopy`, :468 | |
| `fmt.gym.records.pr.count` | {n}回 | `bestValueCopy`, :470 | |
| `gym.records.pr.structureChanged` | 中身が変わっています | `bestValueCopy`, :471 | |
| `gym.records.pr.archived` | 削除済み | `bestValueCopy`, :489 | announced last, semantics only |

`NO_VALUE = "—"` (:28) is an em-dash placeholder, not Japanese; it appears in `BreakdownRow.duration`
for a skipped station. Leave as-is.

---

## app/src/main/java/io/eddiegulay/tempo/gym/EngineRows.kt

Purpose: what `GYM.LIBRARY.DETAIL` says about a routine's shape — the read-only rows, the 最高 tiles,
and the 段階 progression line.

| key | ja | context | notes |
|---|---|---|---|
| `gym.library.detail.timeCap` | 制限時間 | `engineRows`, :52 | AMRAP only |
| `gym.library.detail.restBetweenStations` | 種目の間の休息 | `engineRows`, :55 | |
| `gym.library.detail.restBetweenRounds` | 巡の間の休息 | `engineRows`, :61 | |
| `gym.library.detail.rounds` | 巡数 | `engineRows`, :66, :68 | row label |
| `gym.library.detail.rounds.withinCap` | 時間内で | `engineRows`, :66 | AMRAP's answer to "how many rounds" |
| `fmt.gym.rounds` | {n}巡 | `engineRows`, :68 | counter 巡 |
| `gym.rest.none` | なし | `restLabel`, :108 | zero rest is a prescription, never 〇秒 |
| `fmt.gym.rest.seconds` | {n}秒 | `restLabel`, :108 | **bare seconds** — a duration the user *chose*, per DECISIONS §Q10 |
| `gym.library.detail.timesDone` | やった回数 | `bestTilesFor`, :159 | third tile, engine-independent |
| `fmt.gym.timesDone` | {n}回 | `bestTilesFor`, :159 | |
| `gym.records.metric.bestTime` | 最速 | `bestMetricLabel`, :171 | shared by tile and PR row |
| `gym.records.metric.mostRounds` | 最高巡数 | `bestMetricLabel`, :172 | |
| `gym.records.metric.mostReps` | 最高反復 | `bestMetricLabel`, :173 | |
| `gym.records.metric.mostVolume` | 最高負荷 | `bestMetricLabel`, :174 | deliberately unitless |
| `fmt.gym.bestValue.rounds` | {n}巡 | `bestValueLabel`, :190 | |
| `fmt.gym.bestValue.reps` | {n}回 | `bestValueLabel`, :191 | |
| `gym.library.step.unit.step` | 段 | `stepFor`, :234 | `StepUnit.STEP` |
| `gym.library.step.unit.day` | 日 | `stepFor`, :235 | `StepUnit.DAY` |
| `fmt.gym.library.step.title` | 第{n}{unit} | `stepFor`, :238 | prefix 第 + numeral + unit — three-part composition |
| `fmt.gym.library.step.caption` | {n}{unit}のうち | `stepFor`, :248 | |

`bestMetricLabel(HIGHEST_STEP)` returns **null** by decision (`DECISIONS.md` §Q9) — there is no
documented label and none may be invented. That null must survive migration; an empty-string default
in a resource table would silently print a blank heading.

---

## app/src/main/java/io/eddiegulay/tempo/gym/ChartGeometry.kt

Purpose: the three charts of `GYM.RECORDS.CHARTS`, as arithmetic — plus their headings, captions,
axis labels and the one spoken node each carries. Compose-free by design; the caller draws.

| key | ja | context | notes |
|---|---|---|---|
| `gym.records.charts.range.12w` | 十二週 | `ChartRange.TWELVE.label`, :43 | enum constructor; **not derived from `weeks`** |
| `gym.records.charts.range.26w` | 二十六週 | `ChartRange.TWENTY_SIX.label`, :44 | enum constructor |
| `gym.records.charts.range.year` | 一年 | `ChartRange.YEAR.label`, :45 | enum constructor; deliberately not 五十二週 |
| `gym.records.charts.volume.suppressed` | 二十八日ぶん たまると 出ます | `chartSuppressionCopy`, :78 | rendered *instead of* the chart below 28 days |
| `gym.records.charts.weeklySessions` | 週ごとの回数 | `ChartKind.WEEKLY_SESSIONS.heading`, :249 | enum constructor |
| `gym.records.charts.activeMinutes` | 活動時間 | `ChartKind.ACTIVE_MINUTES.heading`, :250 | enum constructor |
| `gym.records.charts.volume` | 積み上げ | `ChartKind.VOLUME.heading`, :251 | enum constructor |
| `gym.records.charts.heading.denseSuffix` | （折れ線） | `chartHeading`, :261 | **appended** to the heading when bars degrade to a line; full-width parens |
| `gym.records.charts.volume.caption` | 日ごとの積み上げと 七日平均 ・ 目安 | `chartCaption`, :312 | constant; 目安 is not optional |
| `fmt.gym.records.charts.caption.max` | いちばん多い週 {n}回 | `chartCaption`, :317 | |
| `fmt.gym.records.charts.caption.mean` | ならして {d}回 | `chartCaption`, :318 | decimal via `coefficientLabel` → 三.四 |
| `fmt.gym.records.charts.caption.total` | 合計 {n}分 | `chartCaption`, :324 | |
| `fmt.gym.records.charts.caption.meanPerWeek` | ならして {n}分/週 | `chartCaption`, :325 | ASCII slash + 週 |
| `fmt.gym.records.charts.semantics.head` | {heading}、直近{range} | `chartSemantics`, :348 | |
| `fmt.gym.records.charts.semantics.max` | 、いちばん多い週は {n}回 | `chartSemantics`, :356 | note は, absent from the drawn caption |
| `fmt.gym.records.charts.semantics.mean` | 、ならして {d}回 | `chartSemantics`, :357 | |
| `fmt.gym.records.charts.semantics.thisWeek` | 、今週は {n}回 | `chartSemantics`, :358 | weekly-sessions chart only |
| `gym.records.charts.axis.thisWeek` | 今週 | `chartAxisLabels`, :379 | right-hand footer label under the plot |

Left-hand axis label is `JapaneseDate.monthDay(weekStart)` — a date formatter, not a literal.

---

## app/src/main/java/io/eddiegulay/tempo/gym/LibraryFilters.kt

Purpose: search, filter, rank and name for `GYM.LIBRARY.INDEX`. Four fifths of the file is kana
folding, which is algorithm data and must not be touched.

| key | ja | context | notes |
|---|---|---|---|
| `gym.library.filter.duration.under5` | 〜五分 | `DurationBucket.UNDER_FIVE.label`, :181 | enum constructor; wave dash is copy |
| `gym.library.filter.duration.5to15` | 五〜十五分 | `DurationBucket.FIVE_TO_FIFTEEN.label`, :182 | enum constructor |
| `gym.library.filter.duration.over15` | 十五分〜 | `DurationBucket.OVER_FIFTEEN.label`, :183 | enum constructor |
| `fmt.gym.library.copyName` | {name} の写し | `uniqueName`, :308 | the space is deliberate (`04` §1 verbatim); collisions append 二, 三 … |

---

## app/src/main/java/io/eddiegulay/tempo/gym/Numerals.kt

Purpose: **the single biggest formatter hazard in the project.** Two numeral shapes 鍛錬 needed that
the launcher did not: a kanji-decimal difficulty coefficient, and a duration you read rather than
watch. It delegates all digit→kanji work to `io.eddiegulay.tempo.data.JapaneseDate`.

Full public API:

| function | signature | converts | callers |
|---|---|---|---|
| `coefficientLabel` | `(Double?) → String` | a difficulty coefficient to kanji digits with an **ASCII full stop**: `一.〇`, `〇.五`, `二.五`, `十.〇`. Integer part via `JapaneseDate.kanjiExtended`, fraction via `JapaneseDate.kanji`. Null / non-finite / negative → `—` | `ChartGeometry.chartCaption` :318, `chartSemantics` :357 (the ならして decimal); `ui/gym/ExerciseIndexScreen.kt` :118, :132; `ui/gym/StationPickerScreen.kt` :111, :554 |
| `durationKanji` | `(Int seconds) → String` | seconds to `五秒` / `一分三十秒` / `二十分`. Whole minutes **drop** their seconds. **No hours ever** — 6000s is 百分. Negative → `〇秒` | `EngineRows.engineRows` :52 (制限時間); `RecordCopy.comparisonCopy` :81; `ui/gym/StationPickerScreen.paceLine` :136 |
| `durationKanjiFromMs` | `(Long millis) → String` | the same, from a millisecond clock, **truncated not rounded** (6:14.9 → 六分十四秒) | `EngineRows.bestValueLabel` :189; `RecordCopy.heroTime` :213; `ui/gym/GymHomeCopy.kt` :141, :168, :208; `ui/gym/RecordSummary.failedOutChip` :219; `ui/gym/session/PlayerCopy.kt` :308, :320, :369 |
| `clockDuration` | `(Long millis) → String` | arabic `m:ss` — `0:41`, `12:03`. Deliberately **not** kanji; minutes are neither zero-padded nor capped at 59 | `RecordCopy.breakdownRow` :198 (内訳 column); `ui/gym/session/PlayerCopy.kt` :60, :256, :276 |

| key | ja | context | notes |
|---|---|---|---|
| `fmt.duration.seconds` | {n}秒 | `durationKanji`, :82–83, :87 | counter 秒 |
| `fmt.duration.minutes` | {n}分 | `durationKanji`, :86 | counter 分 |

`NO_VALUE = "—"` (:22). `coefficientLabel`'s separator is an **ASCII** `.` (:55), chosen over `。`
deliberately.

The governing rule these encode (`DECISIONS.md` §Q4): **a ticking value stays arabic; anything that
has stopped moving is kanji.** And §Q10: **a duration the user *chose* renders as bare seconds
(六十秒); a duration the app *measured* renders through `durationKanji` (一分三十秒).** Both rules are
about the *act*, not the number, and neither survives a naive "format durations with ICU".

---

## app/src/main/java/io/eddiegulay/tempo/gym/cue/GymSpeech.kt

Purpose: the spoken cue channel — a `TextToSpeech` wrapper with its own audio-focus window
accounting. **Zero string literals of its own**; the phrases come from `Cue.speech` and from
exercise names. But it hard-codes the voice locale.

`engine.setLanguage(Locale.JAPANESE)` at **:107**. `LANG_MISSING_DATA` / `LANG_NOT_SUPPORTED` from
that call is what sets `SpeechAvailability.NoJapaneseVoice` (:110–111), which in turn disarms the
speech channel in `CueSettings.armCues` and drives a disabled, explained row on `GYM.SETTINGS`.
Every one of those three surfaces reads "Japanese" today.

Related: `SpeechAvailability.NoJapaneseVoice` (`GymPreferences.kt:22`) is a *constant name* that
encodes the language; the settings copy that renders it lives in the UI fragment.

---

## app/src/main/java/io/eddiegulay/tempo/gym/cue/Cue.kt

Purpose: `03-player.md` §D.2's cue table as data — haptic, tone and **spoken phrase** per cue. The
spoken column is a separate translation surface from anything drawn.

| key | ja | context | notes |
|---|---|---|---|
| `cue.halfway` | 半分 | `Cue.HALFWAY.speech`, :129 | spoken at `plannedMs / 2` on WORK ≥20s |
| `cue.lastRound` | 最後の巡 | `Cue.LAST_ROUND.speech`, :139 | **speech-only cue** — no tone; its focus window is the utterance itself |
| `cue.emomFail` | 時間切れ | `Cue.EMOM_FAIL.speech`, :158 | EMOM window closed over unfinished work |
| `cue.amrapCap` | 終わり | `Cue.AMRAP_CAP.speech`, :168 | |
| `cue.sessionComplete` | 終わり | `Cue.SESSION_COMPLETE.speech`, :175 | same word, different cue — do **not** dedupe into one key: they may need to diverge |

`SESSION_START` (:102) and `INTERVAL_END` (:121) carry `speech = null` **on purpose** — their phrase
is the next exercise's Japanese name, supplied per segment from
`CueSegment.nextExerciseNameJa` (`CueSchedule.kt:44`) and resolved from the catalogue. Those are
`catalog.*` strings, not `cue.*` ones. `COUNT_TICK` is never spoken by rule (engine latency drift).

---

## app/src/main/java/io/eddiegulay/tempo/gym/TrainingNotice.kt

Purpose: what the health foreground service's notification says — **rendered by SystemUI in the
shade and on the lock screen, outside the app's own theme, font and layout.** Every word is a word
the player already says; nothing here was written for a notification.

| key | ja | context | notes |
|---|---|---|---|
| `gym.session.phase.paused` | 休止 | `noticePhaseLabel`, :85 | wins over the phase — one line, so it carries the fact that changed |
| `gym.session.phase.prepare` | 支度 | `noticePhaseLabel`, :86 | |
| `gym.session.phase.work` | 運動 | `noticePhaseLabel`, :87 | |
| `gym.session.phase.reps` | 運動・回数 | `noticePhaseLabel`, :88 | note: **no spaces** around ・ here, unlike everywhere else |
| `gym.session.phase.rest` | 休息 | `noticePhaseLabel`, :89 | |
| `gym.session.phase.complete` | 記録 | `noticePhaseLabel`, :90 | exists for exhaustiveness; never posted |
| `fmt.gym.session.notice.text` | {phase} ・ {exercise} | `noticeText`, :113 | exercise clause dropped on 支度, every rest, and any pause |
| `gym.session.control.pause` | 休止 | `noticeControlLabel`, :122 | notification action button label |
| `gym.session.control.resume` | 続ける | `noticeControlLabel`, :123 | notification action button label |
| `fmt.gym.session.notice.semantics` | {title}、{text} | `noticeSemantics`, :134 | |

The notification **title** is `notice.routineName` verbatim (:100) — a `catalog.*` or user-authored
string, never translated here.

---

## app/src/main/java/io/eddiegulay/tempo/gym/TrainingService.kt

Purpose: the health foreground service itself. One literal.

| key | ja | context | notes |
|---|---|---|---|
| `app.notificationChannel.gym` | 鍛錬 | `CHANNEL_NAME`, :315 | the channel name **the user reads in Android's own system settings**. Same word as `GymTab.Train` and the launcher's mode dialog |

`CHANNEL_ID = "gym_session"` (:314), the three `ACTION_*` and four `EXTRA_*` constants are ASCII and
are IPC keys — never translate.

---

## app/src/main/java/io/eddiegulay/tempo/gym/BuilderDraft.kt

Purpose: everything `GYM.LIBRARY.BUILDER` and `GYM.LIBRARY.STATION_PICKER` decide without a
composable — reorder maths, dirty tracking, engine migration notices, and the four wheels.

| key | ja | context | notes |
|---|---|---|---|
| `gym.builder.migration.singleStation` | 段階では一種目だけ使われます | `SINGLE_STATION_NOTICE`, :270 | fires only when stations are actually lost |
| `gym.builder.migration.noStationRest` | 毎分では種目の間の休息はありません | `NO_STATION_REST_NOTICE`, :273 | fires only when a rest is actually forced to zero |
| `gym.builder.measure.unavailable` | この方式では使えません | `MEASURE_UNAVAILABLE`, :278 | travels *with* the disabled chip as its reason; also the chip's whole content description |
| `fmt.gym.builder.wheel.reps` | {n}回 | `REP_OPTIONS`, :428 | 100 pre-built rows, 1..100 |
| `fmt.gym.builder.wheel.seconds` | {n}秒 | `SECOND_OPTIONS`, :429 | 60 pre-built rows, 5..300 step 5 |
| `fmt.gym.builder.wheel.rounds` | {n}巡 | `ROUND_OPTIONS`, :430 | 20 pre-built rows |
| `gym.rest.none` | なし | `restWheelLabel`, :442 | duplicate of `EngineRows.restLabel`'s なし — two private functions bound by §Q10, each pinned by its own test |

`NAME_EXCLUDED = ""` (:121) is a hash placeholder, not copy.

---

## app/src/main/java/io/eddiegulay/tempo/gym/InkDensity.kt

Purpose: the month ink-grid on `GYM.RECORDS.INDEX` — cell geometry, hit testing, ink levels, and the
one spoken node the whole 42-cell canvas carries.

| key | ja | context | notes |
|---|---|---|---|
| `fmt.gym.records.month.caption.empty` | この月は 〇日 | `monthCaption`, :196 | `JapaneseDate.kanji(0)` → 〇; a month with no sessions is **not** the empty state |
| `fmt.gym.records.month.caption` | {n}日 ・ {m}月 | `monthCaption`, :197 | month via `JapaneseDate.kanji(monthValue)` |
| `fmt.gym.records.month.semantics.empty` | {m}月、{caption} | `gridSemantics`, :219 | |
| `fmt.gym.records.month.semantics` | {m}月、{n}日 鍛錬しました | `gridSemantics`, :227 | |
| `fmt.gym.records.month.semantics.busiest` | 、いちばん多かったのは {monthDay} | `gridSemantics`, :231 | appended fragment |

---

## app/src/main/java/io/eddiegulay/tempo/gym/RoutineEstimate.kt

Purpose: what a routine will cost before you do it — the builder's live line and the detail page's
summary.

| key | ja | context | notes |
|---|---|---|---|
| `fmt.gym.estimate.minutes` | 約 {n}分 | `estimateLabel`, :207 | 約 + space; floors at 約 一分, never 約 〇分 |
| `fmt.gym.estimate.reps` | {n}回 | `estimateLabel`, :209 | **omitted at zero** |
| `fmt.gym.estimate.reps.cap` | {n}回まで | `estimateLabel`, :210 | AMRAP only — the reps are an upper bound |
| `fmt.sep.middot` | ` ・ ` | `estimateLabel`, :213 | |
| `gym.estimate.approximate` | 目安 | `estimateLabel`, :214 | appended with a leading space when anything was a guess |

Returns the **empty string** for an unbounded routine (デス・バイ) and the caller omits the line;
there is no documented copy for "unbounded".

---

## app/src/main/java/io/eddiegulay/tempo/gym/session/Timeline.kt

Purpose: the compiled session and `stateAt`. One enum draws.

| key | ja | context | notes |
|---|---|---|---|
| `gym.scalingTier.easy` | やさしい | `ScalingTier.EASY.label`, :72 | enum constructor |
| `gym.scalingTier.rx` | 基本 | `ScalingTier.RX.label`, :73 | enum constructor |
| `gym.scalingTier.hard` | きつい | `ScalingTier.HARD.label`, :74 | enum constructor. **Not to be confused with `Tier` (入門/中級/上級)** — different concept, and きつい also collides with `Rating.HARD` |

`ScalingTier.name` (not `.label`) is what reaches `session.tier` — see
`GymViewModel.proceedWithStart` :795. Safe.

---

## app/src/main/java/io/eddiegulay/tempo/gym/GymRoute.kt

Purpose: the gym's three tabs and every route inside 鍛錬.

| key | ja | context | notes |
|---|---|---|---|
| `app.gym.tab.train` | 鍛錬 | `GymTab.Train.label`, :15 | the bar renders **words, not glyphs** — there is no icon fallback |
| `app.gym.tab.library` | 型 | `GymTab.Library.label`, :15 | one character; the bar's layout is built around that |
| `app.gym.tab.records` | 記録 | `GymTab.Records.label`, :15 | |

---

## app/src/main/java/io/eddiegulay/tempo/gym/GymPreferences.kt

Purpose: the stored preference record and its two enums.

| key | ja | context | notes |
|---|---|---|---|
| `gym.settings.units.metric` | メートル法 | `Units.Metric.label`, :12 | enum constructor |
| `gym.settings.units.imperial` | ヤード・ポンド法 | `Units.Imperial.label`, :13 | enum constructor; the ・ is inside the word |

`Units.name` (not `.label`) is the DataStore value — `GymPreferencesRepository.kt:82`, read back at
:110. Safe.

---

## app/src/main/java/io/eddiegulay/tempo/gym/HistoryPaging.kt

Purpose: `GYM.RECORDS.HISTORY`'s keyset paging and its month grouping.

| key | ja | context | notes |
|---|---|---|---|
| `fmt.gym.records.history.monthHeader` | {m}月 | `groupByMonth`, :80 | month alone — the rows beneath already say 十七日 |
| `fmt.gym.records.history.monthCount` | {n}回 | `groupByMonth`, :81 | counts **loaded** rows, self-correcting as the user scrolls |

---

## app/src/main/java/io/eddiegulay/tempo/gym/PatternWarning.kt

Purpose: the builder's one piece of coaching — adjacent stations sharing a movement pattern.

| key | ja | context | notes |
|---|---|---|---|
| `fmt.gym.builder.patternClash` | {first} と {second} は続けて置かない方がよい | `clashCopy`, :122 | design §6 verbatim. Two exercise names interpolated, joined by ` と `, with a trailing clause. The pattern's own label (押す) is deliberately **not** in the sentence |

---

## app/src/main/java/io/eddiegulay/tempo/gym/ScheduleNext.kt

Purpose: the arithmetic behind `GYM.SESSION.COMPLETE`'s calendar hand-off.

| key | ja | context | notes |
|---|---|---|---|
| `gym.session.scheduleNext` | 予定に入れる | `SCHEDULE_ACTION_LABEL`, :28 | the one word this hand-off puts on screen |

`scheduleDraft` sets `title = routineName` verbatim and `location = ""` — neither is copy.

---

## Files with zero user-visible Japanese

Stated one line each, as required.

- **`ExerciseCatalog.kt`** — zero. Pure delegation to `ExerciseCatalogSource`; the Japanese it serves (`Exercise.nameJa`, `Exercise.cue`) is `catalog.*` seed data owned by the `gym/data` fragment.
- **`GymNav.kt`** — zero. Back/push/replaceTop/selectTab arithmetic only.
- **`GymPageState.kt`** — zero. Its KDoc explicitly states the rule: "every function here … returns a *shape*, never a string". Note `StartBlock` (:139) names the reasons 始める is blocked; the copy for each lives on the page.
- **`GymPreferencesRepository.kt`** — zero. Ten ASCII DataStore keys (`gym_haptics`, `gym_units`, …) and three clamps.
- **`GymRepository.kt`** — zero in code. Two literals at :151–152 quote `GYM.LIBRARY.DETAIL`'s delete-confirm copy inside KDoc; that copy belongs to the UI fragment.
- **`GymViewModel.kt`** — zero. The one JP-looking line (:59) is prose in a KDoc.
- **`RoutineTier.kt`** — zero. Returns `Tier` values; the words are `GymModels.kt`'s.
- **`TrainingConsent.kt`** — zero. Permission names and an API-level gate.
- **`cue/CompletionTone.kt`** — zero. Sine synthesis.
- **`cue/CueDisarm.kt`** — zero. The §D.7 disarm matrix as a pure function.
- **`cue/CueEngine.kt`** — zero. Scheduling and cancellation; utterance ids are ASCII (`"${cue.name}@$key"`).
- **`cue/CueSchedule.kt`** — zero. Offsets and focus-window merging.
- **`cue/CueSettings.kt`** — zero. Three booleans.
- **`cue/CueSinks.kt`** — zero. Four interfaces.
- **`cue/GymHaptics.kt`** — zero. Vibration effects.
- **`cue/GymTones.kt`** — zero. `ToneGenerator` / `AudioTrack` / audio focus.
- **`cue/SpeechWindows.kt`** — zero. Reference counting.
- **`session/BackTapResolver.kt`** — zero. Double-tap window.
- **`session/Reconcile.kt`** — zero. Back-fill walk.
- **`session/SessionMachine.kt`** — zero. 34 rules as pure transitions; the one JP line (:907) is a KDoc quote.
- **`session/TimelineCompiler.kt`** — zero. Seven engine layouts.
- **`GymWrite.kt`** — zero user-visible; one non-visible (below).

---

## Hazards

Cited `file:line`. Ordered by how much they cost to get wrong.

### 1. Persistence — `Tier.label` **is** the database column value

`GymModels.kt:124–135`. `Tier` is the one enum in this feature whose stored value is the Japanese
label:

```kotlin
enum class Tier(val label: String) {
    BEGINNER("入門"), INTERMEDIATE("中級"), ADVANCED("上級");
    val storageValue: String get() = label
    fun fromStorage(raw: String?): Tier? = entries.firstOrNull { it.storageValue == raw }
}
```

Confirmed against the schema: `gym/data/Schema.kt:141` —
`CHECK (tier IS NULL OR tier IN ('入門','中級','上級'))`. Written at `gym/data/Seeder.kt:243, :258`;
read back at `gym/data/GymStore.kt:1018, :1066`.

**Translating `label` silently orphans every routine's tier and violates a live CHECK constraint on
the next write.** The fix is to break the `storageValue = label` identity *before* the migration:
`storageValue` becomes `name` (or a frozen ASCII token), with a schema migration rewriting the
column and relaxing the CHECK. `storageValue` already exists precisely so "there is one place to
change" — this is that day. This is the single most valuable line in this fragment.

Every other enum in scope is safe on this axis and should be verified as such, not assumed:
`Engine`, `Pattern`, `Measure`, `BestMetric`, `Phase`, `Rating` all round-trip through `.name`
(`GymModels.kt:59, 86, 108, 156, 181, 204`); `Units` through `.name` (`GymPreferencesRepository.kt:82,
110`); `ScalingTier` through `.name` (`GymViewModel.kt:795`).

### 2. Enums carrying display labels in the constructor — 13 instances

An enum whose label is a constructor argument is resolved **once, at class-init**, and cannot be
re-resolved when the language changes. Every one of these needs the label removed from the
constructor and replaced by a lookup keyed on the constant.

| file:line | enum | labels |
|---|---|---|
| `GymModels.kt:47` | `Engine(val label: String)` | 7 |
| `GymModels.kt:75` | `Pattern(val label: String)` | 7 |
| `GymModels.kt:101` | `Measure(val label: String)` | 3 |
| `GymModels.kt:124` | `Tier(val label: String)` | 3 — **and hazard 1** |
| `GymModels.kt:197` | `Rating(val cr10: Int, val label: String)` | 3 — label sits beside a *frozen numeric* mapping; do not disturb `cr10` |
| `GymPreferences.kt:11` | `Units(val label: String)` | 2 |
| `GymRoute.kt:15` | `GymTab(val label: String)` | 3 |
| `ChartGeometry.kt:42` | `ChartRange(val label: String, val weeks: Int)` | 3 — label is deliberately **not** derived from `weeks` |
| `ChartGeometry.kt:248` | `ChartKind(val heading: String)` | 3 |
| `LibraryFilters.kt:180` | `DurationBucket(val label: String, val upperBoundSeconds: Int)` | 3 |
| `RecordCopy.kt:99` | `PrChip(val label: String)` | 2 |
| `session/Timeline.kt:71` | `ScalingTier(val label: String)` | 3 |
| `cue/Cue.kt:91` | `Cue(haptic, tone, val speech: String?)` | 5 spoken phrases (2 distinct rows share 終わり) |

Three of these are read *through the enum* by domain code that would otherwise be language-free:
`RecordCopy.kt:259` (`summary.rating?.label`), `:262` (`prChip(...)?.label`), `:468` and `:483`
(`best.engine.label`), and `ChartGeometry.kt:348` (`range.label`). Those call sites move to the
resolver too.

`cue/Cue.kt` additionally has a **class-init-time read of the table by another file**:
`CueDisarm.kt:108–113` reads `Cue.SESSION_COMPLETE.haptic` and `focusSpanFor(...)` into top-level
`private val`s. Those are durations, not text, so they are safe — but any resolver bolted onto `Cue`
must not make the enum's construction depend on a `Context`.

### 3. Counters and numerals — `Numerals.kt` and ~35 `kanjiExtended` call sites

`Numerals.kt` is described in full above. The wider hazard is the pattern it enables: **34 places in
this fragment build a user-visible string as `JapaneseDate.kanjiExtended(n) + "<counter>"`.** Every
one is a plural/counter hazard in any language with number agreement.

The counters in use, and the concept each counts:

- 回 — repetitions, and also *times done*, and also *number of sessions*. **Three different English
  plurals from one Japanese counter.** `EngineRows.kt:159, 191`; `RecordCopy.kt:191–193, 252, 282,
  285, 470`; `ChartGeometry.kt:317, 318, 356, 357, 358`; `RoutineEstimate.kt:209`;
  `HistoryPaging.kt:81`; `BuilderDraft.kt:428`.
- 巡 — rounds. `EngineRows.kt:68, 190`; `RecordCopy.kt:251`; `BuilderDraft.kt:430`.
- 秒 / 分 — `Numerals.kt:82–87`; `EngineRows.kt:108`; `BuilderDraft.kt:429, 442`;
  `RecordCopy.kt:285`; `ChartGeometry.kt:324, 325`; `RoutineEstimate.kt:207`.
- 日 — days, in three distinct senses: day-of-month (`RecordCopy.kt:250`), a count of trained days
  (`InkDensity.kt:196–197, 227`), a streak length (`RecordCopy.kt:352`), and a progression *step
  unit* (`EngineRows.kt:235`). Same glyph, four meanings, four keys.
- 種目 — stations (`RecordCopy.kt:133`).
- 段 — progression steps (`EngineRows.kt:234`).
- 週 — weeks (`ChartGeometry.kt:43–45, 325`).
- 月 — months (`InkDensity.kt:197, 217`; `HistoryPaging.kt:80`).

**Zero is systematically omitted rather than printed**, at seven sites, and each omission is a
documented decision: `RecordCopy.sessionRowLines` :251–252, `RoutineEstimate.estimateLabel` :208,
`EngineRows.bestTilesFor` :158, `RecordCopy.streakCopy` :350 (`連続は とぎれています`, never 〇日 連続),
`EngineRows.restLabel` :108 (`なし`, never 〇秒), `BuilderDraft.restWheelLabel` :442,
`InkDensity.monthCaption` :196 (`この月は 〇日` — the one place zero *is* printed). English's "0
sessions" / "no rest" split is not the same split; every one of these needs re-deciding, not
translating.

### 4. Composed strings — ~30 concatenations where word order is Japanese

These are not fragments to be looked up; the whole composition is the string. Recording the full
shape:

- `RecordCopy.kt:81` — `"前回より " + durationKanji(seconds) + " 速い"`. Adverb-final; English is
  "{duration} faster than last time".
- `RecordCopy.kt:133–134` — `"途中まで ・ " + kanjiExtended(planned) + "種目中 " + kanjiExtended(done)`.
  The **denominator precedes the numerator**: 二十種目中 八 is "8 of 20". Reversing the two numbers is
  the obvious bug here.
- `EngineRows.kt:238, :248` — `"第" + numeral + unit` and `numeral + unit + "のうち"`. A circumfix and
  a postfix around the same unit word.
- `ChartGeometry.kt:317–318, 324–325, 348, 356–358` — seven captions/semantics built by `+` and
  `buildString`.
- `InkDensity.kt:196–197, 219, 227, 231` — five.
- `RecordCopy.kt:250–258, 282, 285, 468, 470` — six.
- `TrainingNotice.kt:113, 134` — `"$phase ・ $exercise"`, `title + "、" + text`.
- `PatternWarning.kt:122` — `"$first と $second は続けて置かない方がよい"`, two interpolations.
- `LibraryFilters.kt:308` — `"$base の写し"` then `first + kanjiExtended(n)`.
- `RoutineEstimate.kt:207–214`.
- `ChartGeometry.kt:261` — `kind.heading + "（折れ線）"`, a **suffix appended to another translatable
  string**.
- `GymModels.kt:51` — `完走 ・ 休息あり` is itself a composition frozen into an enum label.

**Separators are copy, not punctuation.** ` ・ ` (space-middot-space) joins list fragments at
`RecordCopy.kt:258`, `RoutineEstimate.kt:213`, `EngineRows.kt:246` (bare space), `TrainingNotice.kt:113`,
`InkDensity.kt:197`, `RecordCopy.kt:133, 285, 468`. `、` joins spoken/semantics fragments at
`RecordCopy.kt:373, 490`, `InkDensity.kt:219, 227, 231`, `ChartGeometry.kt:348, 356–358, 361`,
`TrainingNotice.kt:134`. `「」` wrap a routine name at `RecordCopy.kt:282`. `TrainingNotice.kt:88`
uses `運動・回数` with **no surrounding spaces**, inconsistent with every other site — likely
deliberate (it is one label, not a join), but it needs a decision rather than a global replace.

### 5. Speech / TTS

- `cue/GymSpeech.kt:107` — `engine.setLanguage(Locale.JAPANESE)`, hard-coded. Its result drives
  `SpeechAvailability.NoJapaneseVoice` (`GymPreferences.kt:22`), the disarm in
  `cue/CueSettings.kt:52`, and a disabled explained row on `GYM.SETTINGS`.
- The five spoken phrases (`cue/Cue.kt:129, 139, 158, 168, 175`) are a **separate surface from drawn
  text** and may need different phrasing: they are heard once, mid-effort, over music, at
  `USAGE_ASSISTANCE_SONIFICATION`. 終わり appears on two rows; keep two keys.
- `Cue.LAST_ROUND` is **speech-only** — no tone (`cue/Cue.kt:138`), and its audio-focus window is
  the utterance's own duration (`cue/CueSchedule.kt:101`, opened/closed by
  `SpeechWindows`). A longer translated phrase therefore *lengthens the duck*, which is correct
  behaviour but worth knowing.
- Dynamic speech is the exercise's Japanese name (`CueSchedule.kt:44`, `CueEngine.kt:240`), fed from
  the catalogue — a `catalog.*` concern.
- `QUEUE_FLUSH` always (`GymSpeech.kt:140`): a longer phrase is truncated by the next cue rather
  than queued. Verbose translations will be cut off mid-word at close cue spacing (the 3-2-1 window
  is 3.45s).

### 6. Sorting and filtering — kana folding must **not** be translated

`LibraryFilters.kt:23–39` holds six kana conversion tables and four character constants. These are
**algorithm data**: `foldKana` maps half-width katakana to full-width, composes dakuten/handakuten,
lowers katakana to hiragana, drops the prolonged sound mark, and narrows full-width latin. A
translator or a bulk string-extraction pass that touched these would break search silently for every
user, in a way no test outside `KanaFoldingTest` would catch.

They are also *not dead* under a language toggle: user routine names and the seeded catalogue stay
Japanese regardless of UI language, so folding must keep working. What *does* need a decision is
`matchRoutine` / `matchExercise` (`:139`, `:164`) — they search `nameJa`, `nameEn` and `origin`, and
under an English UI the ranking between the two name fields probably wants to flip.
`uniqueName`'s `rankFrequent` tie-break sorts by `it.name` (`:280`) — a `compareBy` on a raw Kotlin
`String`, i.e. codepoint order, not collation. Already imperfect for Japanese; it becomes visibly
wrong when the list mixes scripts.

### 7. Measurement of text

- **`ChartGeometry.kt` draws into a measured canvas.** `chartAxisLabels` (:377) returns two footer
  labels laid out as a `Row(SpaceBetween)` under the plot — the left one a date, the right one 今週
  (2 chars). "This week" is 9. `chartHeading` (:261) appends （折れ線） to a heading that already sits
  above a fixed-width plot. `chartCaption` (:311) produces a single line under the canvas; the
  ACTIVE_MINUTES form is already the longest string on that page. The file's own KDoc notes the axis
  labels are *text rather than in-canvas* precisely to avoid a `TextMeasurer` — that decision holds
  only while the strings stay short.
- `MonthGrid` (`InkDensity.kt:34`) is 42 cells of fixed size with a weekday header row of
  single characters (`JapaneseDate.DOW`, one glyph each). Not in this fragment, but the grid's
  geometry assumes it.
- `BuilderDraft.kt:426–430` pre-builds 180 wheel labels at class-init. A `TempoValueWheel` row is
  sized for 2–4 kanji.
- `TrainingNotice` text is one line in the shade; `noticeText` (:110) already drops the exercise
  clause when there is nothing to say, but has no length budget.

### 8. Dates and relative time

`RecordCopy.kt:250` prints the bare day (`十七日`) because the list is grouped under a month header
(`HistoryPaging.kt:80`), while `RecordCopy.kt:469` prints the full `monthDay` because the PR list is
not grouped. `InkDensity.kt:197, 217` compose month numbers with 月.
`ChartGeometry.kt:379` and `InkDensity.kt:231` call `JapaneseDate.monthDay`. All of these depend on
`io.eddiegulay.tempo.data.JapaneseDate`, which is outside this fragment and is the shared date
formatter for the whole app — the merge should treat it as a single owned dependency.

`InkDensity.monthCells` (:65) is **Sunday-first** to match `JapaneseDate.DOW`, while
`weekStartOf` (used by the charts) is **ISO Monday**. The file's KDoc states this divergence is
deliberate. A locale-aware first-day-of-week would break one or the other; do not "fix" it as part of
i18n.

### 9. Copy gaps that must not be filled by a translator

Four places return **null or empty** rather than inventing a string, each on a documented ruling. A
migration that fills these in with a translated guess would be re-introducing the bug the null exists
to prevent:

- `RecordCopy.comparisonCopy` (:61) — no slower / level / AMRAP comparison sentence exists. The KDoc
  says: *"If one string is ever added to this feature, make it the slower case."*
- `EngineRows.bestMetricLabel(HIGHEST_STEP)` (:178) — returns null; §Q9 forbids inventing one.
- `EngineRows.engineRows` (:44 KDoc) — an EMOM's interval window has no row label; a non-60-second
  EMOM renders nothing about it. Flagged as needing one string.
- `RoutineEstimate.estimateLabel` (:205) — returns `""` for an unbounded routine.

### 10. Tests

**402 Japanese string literals across 21 test files** in this scope. Every one is either an
assertion on copy or a fixture name; all of them need review, and the assertion ones break on
migration.

| test file | JP literals |
|---|---|
| `EngineRowsTest.kt` | 67 |
| `LibraryFiltersTest.kt` | 58 |
| `KanaFoldingTest.kt` | 56 — **fixtures, not copy**; these must survive unchanged |
| `RecordCopyTest.kt` | 55 |
| `BuilderDraftTest.kt` | 29 |
| `GymPageStateTest.kt` | 25 |
| `TrainingNoticeTest.kt` | 22 |
| `NumeralsTest.kt` | 19 |
| `ChartGeometryTest.kt` | 16 |
| `GymContractTest.kt` | 11 |
| `InkDensityTest.kt` | 9 |
| `HistoryPagingTest.kt` | 7 |
| `RoutineEstimateTest.kt` | 7 |
| `cue/CueScheduleTest.kt` | 6 |
| `PatternWarningTest.kt` | 4 |
| `ScheduleNextTest.kt` | 4 |
| `session/SessionMachineTest.kt` | 2 |
| `RoutineTierTest.kt` | 2 |
| `cue/CueDisarmTest.kt` | 1 |
| `session/TimelineFixtures.kt` | 1 |
| `session/TimelineMutationTest.kt` | 1 |

Two cross-file consistency tests are worth naming because they will need to be re-pointed rather
than deleted:

- `RecordCopyTest` asserts, **for every engine**, that `bestMetricFor` names the metric
  `bestTilesFor` puts first (`RecordCopy.kt:396` KDoc). Three functions over one engine table are
  held together by that test alone.
- `EngineRows.restLabel` (:107) and `BuilderDraft.restWheelLabel` (:441) are two private functions
  producing the same string, deliberately unshared, each pinned by its own test to 六十秒 so a
  divergence fails the build. If they are ever unified, `BuilderDraft`'s KDoc says: unify onto §Q10's
  rule, **not** onto `durationKanji`.

---

## Non-visible Japanese

Literals that are Japanese but never reach a user. **A string here must not be translated**;
translating one is a data-loss or a broken-search bug, not a cosmetic one.

| file:line | literal | why it stays Japanese |
|---|---|---|
| `LibraryFilters.kt:24` | `HALF_WIDTH_KANA` — `｡｢｣､･ｦ…ﾝ` | Codepoint table. Index-aligned with the line below; it is matched against, never drawn |
| `LibraryFilters.kt:26` | `FULL_WIDTH_KANA` — `。「」、・ヲ…ン` | The other half of the same index-aligned pair |
| `LibraryFilters.kt:29` | `DAKUTEN_BASE` | Voicing composition table; matched against |
| `LibraryFilters.kt:30` | `DAKUTEN_VOICED` | Index-aligned with the above |
| `LibraryFilters.kt:31` | `HANDAKUTEN_BASE` | Same |
| `LibraryFilters.kt:32` | `HANDAKUTEN_VOICED` | Same |
| `LibraryFilters.kt:34–35` | `'ﾞ'`, `'ﾟ'` | Half-width voicing marks, matched in `foldKana`'s `when` |
| `LibraryFilters.kt:36–37` | `'゙'`, `'゚'` | Combining U+3099/U+309A, from NFD input |
| `LibraryFilters.kt:38` | `'ー'` | Prolonged sound mark, **dropped** by the folder |
| `LibraryFilters.kt:39` | `'　'` | Ideographic space, narrowed to ASCII space |
| `GymWrite.kt:53` | `"鍛錬 write failed: $fault"` | Developer-facing `IllegalStateException` message. Never shown; `valueOrThrow` is only for the start path where a null is meaningless |

**Dual-purpose, listed in both places:** `Tier.BEGINNER/INTERMEDIATE/ADVANCED` labels
(`GymModels.kt:125–127`) are *both* user-visible chip text *and* the literal contents of the
`routine.tier` SQLite column under a CHECK constraint. They appear in the user-visible table above;
they are repeated here because the storage half is the part that will break. See hazard 1.

**Deliberately ASCII, do not "localise":** `TrainingService.kt:314, 320–327` (channel id, three
intent actions, four extras), `HistoryPaging.kt:46` (`"month:$month"` list key),
`GymPreferencesRepository.kt:119–128` (ten `gym_*` DataStore keys), `CueEngine.kt:129, 240`
(`"s${ordinal}#${n}"`, `"${cue.name}@$key"` utterance ids), `GymPageState.kt` KDoc's
`"builtin:$id"` / `"user:$id"` Compose list keys.
