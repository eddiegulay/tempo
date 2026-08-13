# 鍛錬 — home, library, exercises, builder, settings

**Files surveyed:** 18   **User-visible literals:** 248   **Non-visible JP literals:** 0

Counts are **distinct literals or literal templates per file** (one table row = one count). A string
that appears twice in one file — as a visible label and again as its own `contentDescription` — is one
row, with the duplication noted. The same string appearing in two files is counted in both, per the
contract.

Five files in scope hold **zero** user-visible Japanese and are listed at the end.

Key root note: the station picker has no root of its own in the contract table. It edits one station of
the builder's draft and never touches the database, so it is filed under `gym.builder.picker.*`.

---

## app/src/main/java/io/eddiegulay/tempo/ui/gym/GymHomeCopy.kt

Purpose: the extracted copy module for `GYM.HOME` and the resume prompt — **the closest thing in the
project to a translation table**, and the natural template for the real one.

### Its existing shape, precisely

It is **not** a table of `const val`. It is a module of **17 top-level functions and 3 data classes**,
almost all of which take parameters and compose:

| form | members | migration cost |
|---|---|---|
| plain `fun` returning a composed `String` | `lastResultLine`, `bestLine`, `timesDoneLabel`, `formatElapsedJa`, `relativeDayJa`, `progressLine`, `resumeBannerDescription`, `routineCardDescription`, `stalenessLabel` | rewrite — each interpolates a number or another string |
| `fun` returning a data class of strings | `deleteRoutineCopy` → `DeleteRoutineCopy(title, body, confirm)`; `resumePromptCopy` → `ResumePromptCopy(title, detail, staleness, note)` | rewrite — both branch on a count/enum *and* interpolate |
| `fun` returning `List<data class>` with labels | `homeSections` → `List<HomeSection>` with `label` | mechanical — three fixed literals |
| `private const val` | `JUST_NOW_MS`, `ONE_HOUR_BUCKET_MS`, `RESUMABLE_HORIZON_MS` | numeric only, not copy |
| `enum class` carrying copy | none — `HomeSectionKind` carries no label; the label is on `HomeSection` | — |

There is **no `const val` string in this file at all**, and no `when` expression over a state that maps
cleanly to keys. Every user-visible string is either a bare literal inside a `+` concatenation or a
branch arm of a `when`/`if`. `bestLine` is the one clean `when` over an enum (`BestMetric`) — and even
it appends `JapaneseDate.kanjiExtended(...)` to each arm.

**Verdict: the migration of this file is a rewrite, not a mechanical swap.** It is the right shape for
a *pure, testable* copy layer (`GymHomeCopyTest` asserts it directly, Android-free) and the wrong shape
for a *key/value* copy layer. The recommended move is to keep the function boundary — call sites depend
on it — and replace each body with a lookup plus explicit argument substitution.

| key | ja | context | notes |
|---|---|---|---|
| `gym.home.section.frequent` | よく使う | section heading, `homeSections` :103 | fixed literal, mechanical |
| `gym.home.section.builtIn` | 型 | section heading :109 | same word as `gym.library.title`; deliberately two constants elsewhere |
| `gym.home.section.user` | 自分の型 | section heading :113 | |
| `fmt.gym.home.lastResult` | 前回 {value} ・ {day} | card line 3, `lastResultLine` :144 | **composed**: prefix + value + ` ・ ` + relative day |
| `fmt.gym.count.rounds` | {n}巡 | :139 | kanji numeral + counter |
| `gym.home.best.rounds` | 最高 {n}巡 | `bestLine` :166 | |
| `gym.home.best.reps` | 最高 {n}回 | :167 | |
| `gym.home.best.time` | 最速 {duration} | :168 | different word from 最高 — "fastest", not "most" |
| `gym.home.best.volume` | 最高負荷 {n} | :169 | deliberately unitless |
| `fmt.gym.home.timesDone` | {n}回 | `timesDoneLabel` :198 | null at zero, never 〇回 |
| `fmt.relativeDay.today` | きょう | `relativeDayJa` :223 | hiragana here, kanji 今日 on the calendar page |
| `fmt.relativeDay.yesterday` | きのう | :224 | |
| `fmt.relativeDay.daysAgo` | {n}日前 | :225 | kanji count + 日前 |
| `fmt.gym.home.progress` | {n}種目まで進んだ | `progressLine` :256 | full sentence built from a counter |
| `gym.home.resume.banner` | つづき | `resumeBannerDescription` :302 | also drawn in `GymHomeScreen` |
| `gym.home.resume.continue` | 続ける | :306 | announced only when resumable |
| `fmt.gym.home.elapsedSuffix` | {elapsed} 経過 | :304 | note the space before 経過 |
| `dialog.gym.deleteRoutine.title` | 「{name}」を削除しますか | `deleteRoutineCopy` :342 | corner brackets around a user-authored name |
| `dialog.gym.deleteRoutine.body.archive` | これまでの{n}回の記録は残ります。型だけが一覧から消えます。 | :344 | two sentences, one count |
| `dialog.gym.deleteRoutine.body.purge` | やった記録はありません。完全に消えます。 | :346 | |
| `dialog.gym.deleteRoutine.confirm.archive` | 削除 | :348 | |
| `dialog.gym.deleteRoutine.confirm.purge` | 完全に削除 | :348 | the branch is load-bearing: archive vs purge |
| `fmt.gym.staleness.justNow` | さっき | `stalenessLabel` :448 | see hazard H1 |
| `fmt.gym.staleness.oneHour` | 一時間前 | :449 | kanji numeral inside a fixed word |
| `fmt.gym.staleness.twoHours` | 二時間前 | :450 | |
| `gym.home.resumePrompt.title.open` | 途中の 鍛錬があります | `resumePromptCopy` :498 | note the internal space |
| `gym.home.resumePrompt.title.stopped` | 途中の 鍛錬が 残っています | :498 | two internal spaces |
| `gym.home.resumePrompt.note` | 続きからは できません | :501 | present only for REBOOTED/STALE |
| `fmt.gym.resumePrompt.stations` | {n}種目 | :495 | |
| `app.separator.speech` / `app.separator.visual` | 、 / ・ | :307, :327, :496 | structural: `joinToString("、")` for TalkBack, `" ・ "` for the eye. `routineCardDescription` :324 **rewrites one into the other** with `replace(" ・ ", "、")` |

---

## app/src/main/java/io/eddiegulay/tempo/ui/gym/GymSettingsCopy.kt

Purpose: the extracted copy module for `GYM.SETTINGS` — the **second** near-table, and structurally very
different from `GymHomeCopy.kt`.

### Its existing shape, precisely

| form | members | migration cost |
|---|---|---|
| `enum class` with a copy constructor arg | `SettingSection(val heading: String)` — 4 entries; `SettingRow(val label: String, val section: SettingSection)` — 9 entries | **mechanical** — 13 fixed literals, one per enum entry, no parameters |
| `private const val` | `SUB_SPEECH`, `SUB_NO_JAPANESE_VOICE`, `SUB_NO_TTS_ENGINE`, `SUB_AUTO_ADVANCE`, `SUB_KEEP_SCREEN_ON`, `SUB_SILENT_MODE`, `SUB_NOT_THIS_SESSION` — 7 | **mechanical** — genuine constants, no interpolation |
| `const val` (public) | `REST_DEFAULTS_FOOTNOTE`, `WRITE_FAILED_LINE` | **mechanical** |
| `when` over state → `RowState(enabled, subtitle)` | `speechRowState` over `SpeechAvailability?` (4 arms incl. `null`); `settingsRowStates` → `Map<SettingRow, RowState>` | mechanical — the arms select among the constants above, they do not build strings |
| `fun` returning a branch literal | `toggleWord(on) = 入 / 切` | mechanical — two fixed words |
| `fun` composing | `settingsSecondsLabel(seconds)` = なし \| {kanji}秒; `settingsRowDescription(label, value, subtitle)` = `joinToString("、")` | **rewrite** — the only two composing members |

**Verdict: this file is ~90% mechanical.** 22 of its 27 strings are `const val` or enum constructor
args with no parameters, and the branching lives in `when` expressions that *select* constants rather
than building them. It is the closest existing thing to a real translation table in the project and is
the shape the migration should generalise — **not** `GymHomeCopy.kt`'s.

| key | ja | context | notes |
|---|---|---|---|
| `gym.settings.section.cues` | 合図 | `SettingSection.Cues` :43 | enum ctor arg |
| `gym.settings.section.progress` | 進行 | :44 | |
| `gym.settings.section.restDefaults` | 休息の初期値 | :45 | |
| `gym.settings.section.display` | 表示 | :46 | |
| `gym.settings.row.haptics` | 振動 | `SettingRow.Haptics` :56 | |
| `gym.settings.row.tones` | 音 | :57 | one character; see hazard H9 (layout) |
| `gym.settings.row.speech` | 音声 | :58 | |
| `gym.settings.row.autoAdvanceReps` | 目安で自動的に進む | :59 | longest row label |
| `gym.settings.row.prepareSeconds` | 支度の長さ | :60 | |
| `gym.settings.row.stationRest` | 種目の間 | :61 | |
| `gym.settings.row.roundRest` | 巡の間 | :62 | |
| `gym.settings.row.keepScreenOn` | 画面を消さない | :63 | |
| `gym.settings.row.units` | 単位 | :64 | |
| `gym.settings.sub.speech` | 種目の名前を読み上げる | `SUB_SPEECH` :77 | |
| `gym.settings.sub.noJapaneseVoice` | 日本語の音声が入っていません | `SUB_NO_JAPANESE_VOICE` :78 | **names Japanese explicitly** — see hazard H6 |
| `gym.settings.sub.noTtsEngine` | 読み上げ機能がありません | `SUB_NO_TTS_ENGINE` :79 | |
| `gym.settings.sub.autoAdvance` | 回数の種目でも時間が来たら次へ | `SUB_AUTO_ADVANCE` :80 | |
| `gym.settings.sub.keepScreenOn` | 運動中だけ | `SUB_KEEP_SCREEN_ON` :81 | |
| `gym.settings.sub.silentMode` | マナーモードでも鳴ります | `SUB_SILENT_MODE` :82 | マナーモード is a Japan-specific device concept |
| `gym.settings.sub.notThisSession` | いまの鍛錬には反映されません | `SUB_NOT_THIS_SESSION` :85 | |
| `gym.settings.restDefaults.footnote` | これから作る型に使われます | `REST_DEFAULTS_FOOTNOTE` :88 | public const, read by the screen |
| `fault.gym.settings.writeFailed` | 保存できませんでした | `WRITE_FAILED_LINE` :91 | same sentence `DECISIONS.md` §Q6 binds `Rejected` to |
| `gym.settings.toggle.on` | 入 | `toggleWord` :181 | **both** the visible label and the `stateDescription` |
| `gym.settings.toggle.off` | 切 | :181 | |
| `gym.settings.seconds.none` | なし | `settingsSecondsLabel` :220 | zero is a real, selectable wheel row |
| `fmt.gym.settings.seconds` | {n}秒 | :220 | bare chosen seconds, never `durationKanji` |
| `fmt.gym.settings.rowDescription` | {label}、{value}、{subtitle} | `settingsRowDescription` :255 | §B pins the order: label → state → reason |

---

## app/src/main/java/io/eddiegulay/tempo/ui/gym/GymHomeScreen.kt

Purpose: 鍛錬 — the gym's front door; the tab-1 root. Resume banner, three routine sections, first-run
safety footnote, long-press menu and delete dialog.

| key | ja | context | notes |
|---|---|---|---|
| `gym.home.title` | 鍛錬 | page heading :155 | Mincho 26.sp, letterSpacing 3.sp |
| `fmt.app.header.date` | {era} ・ {monthDay} | header sub-line :161 | **composed from two formatters** (`JapaneseDate.era`, `.monthDay`) — hazard H3 |
| `app.action.settings` | 設定 | header action :169 | label and description are the same literal |
| `gym.home.action.create` | 作る | header action :175 | |
| `gym.builder.title.create` | 型を作る | that action's description :176 | matches the page it opens |
| `app.state.loading` | 読み込み中 | body :195 | |
| `gym.home.resume.banner` | つづき | section header over the banner :356, :372 | |
| `fmt.gym.home.elapsed` | {elapsed} 経過 | banner :434 | `"$it 経過"` — composed at the call site, not in the copy module |
| `gym.home.resume.continueArrow` | 続ける → | banner :453 | word + glyph in one literal |
| `gym.home.seeAll` | すべて見る | section header :484 (desc), :488 (label) | |
| `gym.home.userEmpty.description` | 型はまだありません、型を作る | empty-state node :632 | composed of two other strings, joined with 、 |
| `gym.home.userEmpty` | 型はまだありません | :638 | **must never stand in for a failed read** |
| `gym.home.userEmpty.action` | 作る → | :642 | |
| `gym.safety.line` | 痛みを感じたらやめる | first-run footnote :676 | also `GymSafetyScreen.kt` |
| `gym.safety.footnote.description` | 痛みを感じたらやめる、安全のために | :682 | composed |
| `app.action.close` | 閉じる | footnote dismiss :687/:691; write-fault dismiss :720/:724 | four occurrences of one word |
| `gym.home.card.longPress` | 型の操作 | `onLongClick(label = …)` :550 | |
| `gym.library.menu.edit` | 編集 | customAction :533, MenuRow :763 | |
| `gym.library.menu.duplicate` | 写して作る | :534, :764 | |
| `gym.library.menu.delete` | 削除 | :535, :765 | |
| `app.action.cancel` | やめる | menu dismiss :771; delete dismiss :838 | |

---

## app/src/main/java/io/eddiegulay/tempo/ui/gym/LibraryIndexScreen.kt

Purpose: 型 — the routine library (tab 2 root). Search field, three chip groups, three sections of
routine cards, long-press menu, delete confirm.

| key | ja | context | notes |
|---|---|---|---|
| `gym.library.title` | 型 | `PAGE_TITLE` :292 | deliberately a separate constant from the section heading |
| `gym.library.section.frequent` | よく使う | `HEADING_FREQUENT` :293 | |
| `gym.library.section.builtIn` | 型 | `HEADING_BUILT_IN` :294 | same word, second constant, by design |
| `gym.library.section.user` | 自分の型 | `HEADING_USER` :295 | |
| `gym.library.best.prefix` | 最高 | `BEST_PREFIX` :136 | prefixed to `bestValueLabel(...)` |
| `fmt.gym.library.stationCount` | {n}種目 | `routineCardCopy` :173 | |
| `fmt.gym.library.timesDone` | {n}回 | :189 | |
| `fmt.gym.library.sectionCount` | {label}、{n}件 | `sectionSemantics` :228 | **spoken only, never drawn** — see hazard H10 |
| `gym.library.menu.start` | 始める | `RoutineMenuItem.Start` :244 | enum ctor arg |
| `gym.library.menu.duplicate` | 写して作る | :245 | |
| `gym.library.menu.favourite` | よく使うに入れる | :246 | |
| `gym.library.menu.unfavourite` | よく使うから外す | :247 | |
| `gym.library.menu.delete` | 削除 | :248 | |
| `fmt.app.header.date` | {era} ・ {monthDay} | :367 | same composition as home |
| `app.action.closeSearch` | とじる | :374/:375 | |
| `app.action.search` | 探す | :374/:375 | the word states the *action*, not the state |
| `gym.library.action.create` | 作る | :380 | |
| `gym.builder.title.create` | 型を作る | that action's description :383 | |
| `app.search.placeholder` | さがす | placeholder :533 and `contentDescription` :526 | hiragana here, kanji 探す on the button |
| `app.state.selected` | 選択中 | chip `stateDescription` :597 | no word for unselected, deliberately |
| `app.action.menu` | メニュー | `onLongClick(label = …)` :766 | note: `GymHomeScreen` uses 型の操作 for the same gesture — **two words, one affordance** |
| `gym.library.userEmpty` | 型はまだありません | inline empty :650 | |
| `gym.library.action.exercises` | 種目を見る | centred action :655 | |
| `app.state.loading` | 読み込み中 | :941 | |
| `gym.library.noMatch` | 該当する型はありません | :960 | §6 flags in bold: **never** 型はまだありません |
| `gym.library.clearFilters` | 絞り込みを外す | :964 | only when a chip is active |
| `app.action.cancel` | やめる | delete dismiss :923 | |

Chip labels (`Tier.label`, `Engine.label`, `DurationBucket.label`) are **out of scope** — see
"External copy this scope renders".

---

## app/src/main/java/io/eddiegulay/tempo/ui/gym/LibraryDetailScreen.kt

Purpose: 型の中身 — one routine in full, and the only place a session is started. The densest copy file
in scope alongside `BuilderScreen.kt`.

| key | ja | context | notes |
|---|---|---|---|
| `app.value.none` | — | `NO_VALUE` :111 | em dash, not a word; still a rendered literal |
| `dialog.gym.deleteRoutine.title` | 「{name}」を削除しますか | `detailDeleteCopy` :184 | **third independent copy** of this dialog — hazard H12 |
| `dialog.gym.deleteRoutine.body.archive` | これまでの{n}回の記録は残ります。型だけが一覧から消えます。 | :190 | |
| `dialog.gym.deleteRoutine.body.purge` | やった記録はありません。完全に消えます。 | :199 | |
| `dialog.gym.deleteRoutine.confirm.archive` | 削除 | :192 | |
| `dialog.gym.deleteRoutine.confirm.purge` | 完全に削除 | :201 | |
| `app.action.cancel` | やめる | :193, :202, :206, :211 | four occurrences, one per `DetailDeleteState` |
| `app.state.loading` | 読み込み中 | delete dialog `Waiting` body :206; body slot :567 | |
| `fmt.gym.library.prescription.reps` | {n}回 | `prescriptionLabel` :232 | |
| `fmt.gym.library.prescription.seconds` | {n}秒 | :233 | **bare** seconds, never `durationKanji` |
| `gym.library.prescription.maxEffort` | 限界まで | :234 | borrowed from the picker's table |
| `fmt.gym.library.stationRow` | {n}番目、{name}、{prescription} | `stationRowSemantics` :244 | ordinal counter 番目 |
| `fault.gym.start.unknownExercise` | 種目が見つからないため 始められません | `startBlockCopy` :261 | internal space |
| `fault.gym.start.noStations` | 種目を加えてください | :262 | borrowed from builder copy; flagged as a gap in-file |
| `fmt.gym.library.startDescription` | 「{name}」を始める | `startButtonDescription` :284 | the *description* never changes; the label does |
| `gym.session.prepare` | 支度 | in-flight button label :913, appended to the description :285 | |
| `fmt.gym.attempt.rounds` | {n}巡 | `attemptLine` :318 | |
| `fmt.gym.attempt.reps` | {n}回 | :319 | |
| `fmt.gym.library.subtitle` | {engine} ・ {tier} | `detailSubtitle` :366 | both fragments are external enum labels |
| `gym.library.gloss.amrap` | 決めた時間で何巡できるか | `detailEngineGloss` :379 | the only engine with a gloss |
| `gym.library.action.duplicate` | 写して作る | `detailActions` :474 | |
| `gym.library.action.favourite` | よく使うに入れる | `detailFavouriteLabel` :509 | |
| `gym.library.action.unfavourite` | よく使うから外す | :509 | |
| `gym.library.action.edit` | 編集 | :477 | |
| `gym.library.action.restore` | 元に戻す | :479 | |
| `gym.library.action.delete` | 削除 | :481 | |
| `app.action.close` | とじる | header action :711 | |
| `gym.library.archived` | 削除済み | chip beside the subtitle :684 | |
| `gym.library.section.structure` | 組み立て | :782 | |
| `gym.library.section.bests` | 最高 | :855 | |
| `gym.library.section.attempts` | これまで | :1140 | |
| `gym.library.seeAll` | すべて見る | :1145 | |
| `gym.library.noAttempts` | まだ やっていません | :1150 | internal space; a fact about the *routine*, never the store |
| `gym.library.origin` | 出典 | :843 | |
| `gym.library.unknownExercise` | 不明な種目 | station row fallback :1003 | shares the `UNKNOWN_EXERCISE` value defined in `BuilderScreen.kt` but re-typed here as a literal |
| `app.glyph.chevron` | › | station row trailing glyph :1035 | `clearAndSetSemantics` — decorative |
| `fmt.app.row.description` | {label}、{value} | `ReadOnlyRow` :1057 | |
| `fmt.gym.library.tileDescription` | {label}、{value} | `BestTiles` :1092 | label-first, deliberately not visual order |

Row labels/values from `engineRows(snapshot)` (制限時間, 巡数, 種目の間の休息, …) are **out of scope**
(`gym/EngineRows.kt`); so are `heroTime`, `estimateLabel`, `prChip().label` (自己最高), `partialChipCopy`
(途中まで), `bestTilesFor` tile labels and `stepFor` (第七段 / 十八段のうち).

---

## app/src/main/java/io/eddiegulay/tempo/ui/gym/ExerciseIndexScreen.kt

Purpose: 種目 — the movement catalogue, pushed from 型's 種目を見る. No Loading/Empty/Error states by
spec; the PB fragment is an overlay.

| key | ja | context | notes |
|---|---|---|---|
| `gym.exercise.title` | 種目 | `PAGE_TITLE` :265 | |
| `fmt.gym.exercise.subtitle` | {n}の動き | `exerciseIndexSubtitle` :146 | counts the catalogue, never the matches |
| `fmt.gym.exercise.pace` | {coefficient}秒/回 | `paceLabel` :132 | coefficient is kanji-digit (一.〇), see hazard H4 |
| `gym.exercise.best.reps` | 最高 {n}回 | `bestRepsLabel` :136 | absent, never 最高 — , when there is no history |
| `fmt.gym.exercise.difficulty` | 難度 {coefficient} | card description :196 | borrowed from the station picker's documented sentence |
| `app.action.closeSearch` | とじる | :328/:329 | |
| `app.action.search` | 探す | :328/:329 | |
| `app.search.placeholder` | さがす | :410 (desc), :417 (placeholder) | |
| `gym.exercise.noMatch` | 該当する種目はありません | :531 | never 型はまだありません's shape |

`Pattern.label` (押す/引く/しゃがむ/股関節/体幹/移動/跳ぶ), `Exercise.nameJa`, `Exercise.cue` and
`coefficientLabel` are out of scope.

---

## app/src/main/java/io/eddiegulay/tempo/ui/gym/ExerciseDetailScreen.kt

Purpose: 種目の中身 — one movement: cue, bests, progression ladder, and which routines use it.

| key | ja | context | notes |
|---|---|---|---|
| `app.value.none` | — | `NO_VALUE` :88 | |
| `fmt.gym.exercise.detailSubtitle` | {pattern} ・ 難度 {coefficient} | `exerciseDetailSubtitle` :92 | |
| `fmt.gym.exercise.rungHere` | {name}、難度 {coef}、いまここ | `rungSemantics` :172 | |
| `fmt.gym.exercise.rungNotYet` | {name}、難度 {coef}、まだ | :173 | exactly two forms, by spec |
| `fmt.gym.exercise.reps` | {n}回 | `movementTiles` :206, :212 | used for both tiles |
| `gym.exercise.tile.singleSet` | 一度に | :218 | |
| `gym.exercise.tile.lifetime` | のべ回数 | :219 | |
| `gym.exercise.tile.last` | 最後 | :220 | |
| `fmt.gym.exercise.usedByCount` | {n}件 | `usedByCount` :269 | **the one count the specs do print visibly** |
| `gym.exercise.section.bests` | 最高 | `HEADING_BESTS` :275 | |
| `gym.exercise.section.ladder` | 段階 | `HEADING_LADDER` :276 | |
| `gym.exercise.section.usedBy` | 使われている型 | `HEADING_USED_BY` :277 | |
| `app.action.close` | とじる | :495 | |
| `app.state.loading` | 読み込み中 | :347, :703 | two call sites, two different sections |
| `gym.exercise.noHistory` | まだ やっていません | :354 | |
| `gym.exercise.noRoutines` | どの型にも入っていません | :707 | reachable only from `Ready` + empty |
| `gym.exercise.ladder.current` | いま | rung marker :655 | the block's only accent word |
| `fmt.gym.exercise.tileDescription` | {label}、{value} | :546 | label-first |
| `app.separator.visual` | ` ・ ` | used-by list :729 | **appended to the preceding routine name** so it cannot wrap alone |

---

## app/src/main/java/io/eddiegulay/tempo/ui/gym/StationPickerScreen.kt

Purpose: 種目をえらぶ — picks one exercise and its prescription for the builder's draft. Writes only to
the draft, never to the database.

| key | ja | context | notes |
|---|---|---|---|
| `gym.builder.picker.title` | 種目をえらぶ | :371 | |
| `app.action.save` | 保存 | `pickerSaveSemantics` :107, header :378 | |
| `gym.builder.picker.saveBlocked` | 保存、種目をえらんでください | `pickerSaveSemantics` :107 | a disabled word says why |
| `fmt.gym.exercise.rowDescription` | {name}、{pattern}、難度 {coef} | `exerciseSemantics` :111 | |
| `fmt.gym.builder.picker.measureDescription` | {measure}、{reason} | `measureSemantics` :120 | |
| `fmt.gym.builder.picker.pace` | 目安 {duration} | `paceLine` :136 | via `durationKanji` — a duration the app **computed** |
| `gym.builder.picker.openEnded` | できるところまで | `OPEN_ENDED` :254 | replaces the wheel for MAX_EFFORT |
| `app.action.cancel` | やめる | :376 (label and description) | |
| `app.search.placeholder` | さがす | :485 (desc), :492 (placeholder) | |
| `gym.builder.picker.measureLabel` | はかり方 | :625 | |
| `gym.builder.picker.measureRefused` | この方式では使えません | :641 | drawn once under the chips; each disabled chip repeats it in its own description |
| `app.state.selected` | 選択中 | :537, :718 | |
| `gym.builder.picker.remove` | 削除 | :694 (desc), :699 (label) | edit mode only |
| `gym.library.noMatch.exercise` | 該当する種目はありません | :788 | |
| `gym.library.unknownExercise` | 不明な種目 | via `UNKNOWN_EXERCISE` :598 | imported from `BuilderScreen.kt` |

`Measure.label` (回数 / 秒数 / 限界まで), `MeasureOption.reason`, `clashCopy(...)` and `Pattern.label`
are out of scope.

---

## app/src/main/java/io/eddiegulay/tempo/ui/gym/BuilderScreen.kt

Purpose: 型を作る / 型を編集 — the routine builder. Name field, engine chips, drag-reorderable station
list, four wheel rows, live estimate, discard dialog. Also hosts `BuilderDraftHolder` (the draft
ViewModel) and `UNKNOWN_EXERCISE`, which the picker and detail page both read.

| key | ja | context | notes |
|---|---|---|---|
| `gym.builder.title.create` | 型を作る | `builderTitle` :392 | |
| `gym.builder.title.edit` | 型を編集 | :392 | |
| `gym.library.unknownExercise` | 不明な種目 | `UNKNOWN_EXERCISE` :395 | `internal const val` — the one true const-string in the file |
| `fmt.gym.builder.stationValue.reps` | {n}回 | `stationValueLabel` :410 | empty string, never 〇回, when null |
| `fmt.gym.builder.stationValue.seconds` | {n}秒 | :411 | |
| `fmt.gym.builder.stationRow` | {n}番目、{name}、{value} | `stationSemantics` :417 | uses `JapaneseDate.kanji`, **not** `kanjiExtended` |
| `fmt.gym.builder.handle` | {name} の並べ替え | `handleSemantics` :422 | note the space before の |
| `fmt.gym.builder.moved` | {n}番目に移動しました | `moveAnnouncement` :425 | spoken on drop and on a TalkBack move |
| `fmt.gym.builder.movedWithClash` | {move}、{clash} | `moveAnnouncementWith` :442 | live-region composition |
| `fmt.gym.builder.historySafe` | これまでの{n}回の記録はそのまま残ります | `historySafeLine` :454 | |
| `app.action.save` | 保存 | `saveSemantics` :472, header :822 | |
| `gym.builder.saveBlocked` | 保存、名前と種目が要ります | `saveSemantics` :472 | |
| `app.state.saving` | 保存中 | `saveDescription` :484, header label :822 | |
| `gym.builder.wheel.none` | なし | `restWheelValueLabel` :620 | zero rest |
| `fmt.gym.builder.wheel.seconds` | {n}秒 | `restWheelValueLabel` :620, `secondWheelValueLabel` :623 | **two functions, same template** |
| `fmt.gym.builder.wheel.reps` | {n}回 | `repWheelValueLabel` :626 | |
| `fmt.gym.builder.wheel.rounds` | {n}巡 | `roundWheelValueLabel` :629 | |
| `gym.builder.row.stationRest` | 種目の間の休息 | `ROW_STATION_REST` :661 | **matched by `==` against `engineRows`' own label** — hazard H8 |
| `gym.builder.row.roundRest` | 巡の間の休息 | `ROW_ROUND_REST` :662 | same |
| `gym.builder.row.rounds` | 巡数 | `ROW_ROUNDS` :663 | same |
| `app.action.cancel` | やめる | header :815, discard confirm :1668 | |
| `gym.builder.field.name` | 名前 | label :1052 and `contentDescription` :1064 | |
| `gym.builder.duplicateName` | 同じ名前の型があります | :940 | warns, never blocks |
| `gym.builder.field.engine` | 方式 | :1104 | |
| `fmt.gym.builder.engineDescription` | 方式、{engine} | :1098 | string template with `${engine.label}` |
| `app.state.selected` | 選択中 | :1126 | |
| `gym.builder.field.stations` | 種目 | `FieldLabel` :951 | |
| `gym.builder.emptyStations` | 種目を加えてください | :1185 | also borrowed by `LibraryDetailScreen.startBlockCopy` |
| `gym.builder.addStation` | ＋ 加える | :1450 (desc), :1455 (label) | full-width ＋ plus a space |
| `gym.builder.stationCapReached` | これ以上は加えられません | :1464 | |
| `gym.builder.action.moveUp` | 上へ動かす | customAction :1335 | |
| `gym.builder.action.moveDown` | 下へ動かす | :1336 | |
| `gym.library.menu.edit` | 編集 | :1337 | |
| `gym.library.menu.delete` | 削除 | :1338 | |
| `app.state.dragging` | 移動中 | handle `stateDescription` :1402 | |
| `app.glyph.dragHandle` | ⋮⋮ | :1407 | two U+22EE, in Gothic |
| `fmt.app.row.description` | {label}、{value} | `WheelRow` :1510, `ReadOnlyRow` :1553 | |
| `app.state.loading` | 読み込み中 | :1632 | |
| `dialog.gym.builder.discard.title` | 編集をやめますか | :1652 | |
| `dialog.gym.builder.discard.body` | 保存していない変更は消えます。 | :1656 | |
| `dialog.gym.builder.discard.back` | もどる | :1673 | the escape, deliberately the dismiss button |

Engine chip labels come from `Engine.label` via `ENGINE_CHOICES` :644; clash lines come from
`clashCopy`; the estimate line from `estimateLabel`; migration notices from `migrateDraft` — all out of
scope.

---

## app/src/main/java/io/eddiegulay/tempo/ui/gym/GymSettingsScreen.kt

Purpose: 設定 — the gym's preference page. Every row writes on the tap that made it; the words are
`GymSettingsCopy.kt`'s. Also owns `GymBackHeader`, which `GymSafetyScreen` reuses.

| key | ja | context | notes |
|---|---|---|---|
| `gym.settings.title` | 設定 | :133 | |
| `gym.settings.subtitle` | 鍛錬のふるまい | :134 | |
| `app.action.close` | とじる | `GymBackHeader` back-glyph description :278 | in-file KDoc flags this as a **gap**: no spec supplies a spoken label for ←, so とじる is borrowed |
| `app.glyph.back` | ← | :282 | |
| `app.glyph.forward` | → | `ValueRow` :511, `SafetyRow` :605 | |
| `gym.safety.title` | 安全のために | `SafetyRow` :595 (desc), :601 (label) | |

Everything else this page draws comes from `GymSettingsCopy.kt` (`section.heading`, `row.label`,
`RowState.subtitle`, `toggleWord`, `WRITE_FAILED_LINE`, `REST_DEFAULTS_FOOTNOTE`,
`settingsSecondsLabel`, `settingsRowDescription`) or from `Units.label` (out of scope).

---

## app/src/main/java/io/eddiegulay/tempo/ui/gym/GymSafetyScreen.kt

Purpose: 安全のために — the "not medical advice" page. Deliberately a heading and one sourced line; the
four paragraphs the spec calls for do not exist yet.

| key | ja | context | notes |
|---|---|---|---|
| `gym.safety.title` | 安全のために | header :58 | |
| `gym.safety.line` | 痛みを感じたらやめる | body :82 | also drawn in `GymHomeScreen` :676 |

**Note for the merge:** this is the page whose body is *missing*. When the four safety paragraphs are
written they land in `SafetyBody` — and safety prose is the one category of copy where a
machine-translated string is a liability. Flag it for human translation.

---

## app/src/main/java/io/eddiegulay/tempo/ui/gym/ResumePrompt.kt

Purpose: つづき — the modal a session left open raises, over whichever page is beneath it. Title/detail/
staleness/note come from `GymHomeCopy.resumePromptCopy`; the option words are here.

| key | ja | context | notes |
|---|---|---|---|
| `dialog.gym.resume.discardWarning` | 元に戻せません。 | second confirm :268 | in-file note: the resume prompt's second confirm has **no copy of its own in any spec**; this is §6's nearest documented line |
| `gym.session.resume` | 続ける | :276 | |
| `gym.session.record` | 記録する | :284 | |
| `gym.session.discard` | 捨てる | option :295, confirm button :314 | |
| `app.action.cancel` | やめる | :326 | |
| `gym.session.leaveAsIs` | そのまま | :334 | the only exit when 捨てる is the sole option |
| `app.glyph.forward` | → | `PromptOption` trailing glyph :373 | |

---

## app/src/main/java/io/eddiegulay/tempo/ui/gym/ScheduleNextAction.kt

Purpose: 予定に入れる — the calendar hand-off on `GYM.SESSION.COMPLETE`. It renders the routine's name
into a **device calendar event**.

| key | ja | context | notes |
|---|---|---|---|
| `gym.session.scheduleNext` | 予定に入れる | label :253 and `contentDescription` :244 | value is `SCHEDULE_ACTION_LABEL`, `gym/ScheduleNext.kt:28` — out of scope, rendered here |
| `fault.calendar.openSettings` | 設定を開く | fault strip action :282 | only when permanently denied |
| `app.action.cancel` | やめる | :304 (label and description) | |
| `app.state.saving` | 保存中 | :311 | |
| `app.action.save` | 保存 | :311 (label), :313 (description) | the description stays 保存 while the label says 保存中 |

The `WhenLine` text (`九月十日 ・ 19:30 – 19:37`) comes from `draftSummary(...)` in `ui/` — out of scope,
and a **mixed kanji-date / arabic-clock composition**.

---

## Files with zero user-visible Japanese

- **`GymShell.kt`** — zero. `gymPagePlaceholderTitle` returns `null` for every route (Phase 3 emptied
  the list), so `GymPagePlaceholder(title)` is never reached. The only string literals are the
  `AnimatedContent(label = "gym-route")` debug label. *Important for the merge:* this function is the
  designated home for placeholder page titles and will re-acquire copy the next time a route is added.
- **`GymTabBar.kt`** — zero. The three tab words (鍛錬 / 型 / 記録) come from
  `GymTab(val label: String)` in `gym/GymRoute.kt:15`, out of scope. See hazard H9 — this file *lays
  out* those words under tight constraints even though it does not own them.
- **`KeepAwake.kt`** — zero. One pure predicate.
- **`GymMotion.kt`** — zero. Durations and easings only.
- **`TrainingServiceMount.kt`** — zero. It projects `SessionUiState` into a `TrainingNotice` whose
  `routineName` and `exerciseName` are Japanese **data**, but composes no literal. The notification's own
  words (┃┃, 続ける, 休止) are `gym/TrainingService.kt`'s.

---

## Hazards

### H1 — `stalenessLabel` is a formatter with a delegating tail (`GymHomeCopy.kt:445-456`)

Not a table. Four branches over an elapsed-millisecond delta, the fourth of which **hands off to a
different formatter over a different input type**:

```kotlin
fun stalenessLabel(startedAtWallMs: Long, nowWallMs: Long, zone: ZoneId): String {
    val deltaMs = (nowWallMs - startedAtWallMs).coerceAtLeast(0L)
    return when {
        deltaMs < JUST_NOW_MS          -> "さっき"        // < 10m
        deltaMs < ONE_HOUR_BUCKET_MS   -> "一時間前"       // < 2h
        deltaMs < RESUMABLE_HORIZON_MS -> "二時間前"       // < 4h
        else -> relativeDayJa(                            // ≥ 4h: calendar days, not hours
            then  = Instant.ofEpochMilli(startedAtWallMs).atZone(zone).toLocalDate(),
            today = Instant.ofEpochMilli(nowWallMs).atZone(zone).toLocalDate(),
        )
    }
}
```

The whole branch structure, recorded:

| branch | edge | ja | shape |
|---|---|---|---|
| 1 | Δ < 10 min | さっき | adverb, no number |
| 2 | 10 min ≤ Δ < 2 h | 一時間前 | **kanji numeral inside a fixed word** — it does not mean "1 hour ago", it is a bucket name |
| 3 | 2 h ≤ Δ < 4 h | 二時間前 | same; stops at `RESUMABLE_HORIZON_MS`, restated from `GymMath.STALE_AFTER_MS` |
| 4 | Δ ≥ 4 h | delegates to `relativeDayJa` | きょう / きのう / {n}日前 — **calendar-day arithmetic in the caller's zone**, not an hour count |

Three separate translation problems here:

1. 一時間前 and 二時間前 are **bucket labels that happen to look like measurements**. A language with
   plurals must not render branch 3 as "2 hours ago" — the bucket spans 2 h to 4 h. The in-file KDoc
   argues at length that a coarse word "may round; it may not multiply", and the English rendering has
   to preserve that (something like "earlier today" / "a while ago"), which means the key is
   `fmt.gym.staleness.twoHours` in name only and its English value is not a translation of 二 + 時間 + 前.
2. Branch 4 crosses into **calendar-day** semantics, so the two halves of one function have different
   argument types (a delta vs. two `LocalDate`s in a zone). A single template cannot express both.
3. `relativeDayJa` itself (`:220-227`) is a second formatter: きょう / きのう / `kanjiExtended(n) + 日前`.
   Its third arm is a **count-bearing string with no plural**. English needs "1 day ago" / "N days ago".
   It also deliberately reads きょう for a future date rather than a negative count.

`GymHomeCopyTest` pins these edges against `resumability`'s own; any change breaks the test.

### H2 — Kanji numerals everywhere: `JapaneseDate.kanjiExtended` / `.kanji`

Every count in this scope is rendered as a kanji numeral, not an arabic one. Call sites in scope:
`GymHomeCopy` (6 uses), `LibraryIndexScreen` (3), `LibraryDetailScreen` (5), `ExerciseIndexScreen` (2),
`ExerciseDetailScreen` (3), `StationPickerScreen` (via `coefficientLabel`), `BuilderScreen` (7),
`GymSettingsCopy` (1).

`kanjiExtended` caps kanji at 九千九百九十九 and falls back to arabic above (`ExerciseDetailScreen.kt:191`
documents this for のべ回数). `BuilderScreen.stationSemantics` :417 uses plain `JapaneseDate.kanji` while
`LibraryDetailScreen.stationRowSemantics` :244 uses `kanjiExtended` for **the same 番目 ordinal** — a
pre-existing inconsistency the migration will surface.

Under translation the entire mechanism is dead: an English build renders arabic digits and needs no
formatter at all, which means every `kanjiExtended(n) + "counter"` site is a **template with an argument**,
not a string.

### H3 — Counter suffixes do not survive translation

The counters in scope, with every call site:

| counter | means | sites |
|---|---|---|
| 回 | reps / times performed | `GymHomeCopy.timesDoneLabel`, `bestLine`, `lastResultLine`; `LibraryIndexScreen.routineCardCopy`; `LibraryDetailScreen.prescriptionLabel`/`attemptLine`; `ExerciseIndexScreen.bestRepsLabel`; `ExerciseDetailScreen.movementTiles`; `BuilderScreen.stationValueLabel`/`repWheelValueLabel`; the two delete-dialog bodies; `historySafeLine` |
| 巡 | rounds/circuits | `GymHomeCopy.lastResultLine`/`bestLine`; `LibraryDetailScreen.attemptLine`; `BuilderScreen.roundWheelValueLabel` |
| 秒 | chosen seconds | `GymSettingsCopy.settingsSecondsLabel`; `BuilderScreen.restWheelValueLabel`/`secondWheelValueLabel`; `LibraryDetailScreen.prescriptionLabel` |
| 種目 | stations/movements | `GymHomeCopy.progressLine`, `resumePromptCopy`; `LibraryIndexScreen.routineCardCopy` |
| 件 | items | `LibraryIndexScreen.sectionSemantics`; `ExerciseDetailScreen.usedByCount` |
| 番目 | ordinal position | `LibraryDetailScreen.stationRowSemantics`; `BuilderScreen.stationSemantics`, `moveAnnouncement` |
| 日前 | days ago | `GymHomeCopy.relativeDayJa` |
| の動き | "movements" | `ExerciseIndexScreen.exerciseIndexSubtitle` |
| 秒/回 | seconds per rep | `ExerciseIndexScreen.paceLabel` |

**English requires pluralisation for 回, 巡, 種目, 件, 日前 and の動き** (Japanese has none), and 番目
becomes an ordinal suffix that itself varies (1st/2nd/3rd). Every one of these is a count-bearing string
and therefore a hazard by contract rule 4.

Two specific traps recorded in-file:

- **零/〇 is never printed.** `timesDoneLabel`, `progressLine`, `bestLine`, `stationValueLabel`,
  `attemptLine.score`, `bestRepsLabel` all return `null`/`""` at zero rather than 〇回 — because zero is
  "inapplicable", not "a result". A naive template that always renders will reintroduce 〇回 in every
  language.
- **なし at zero, not 〇秒.** `settingsSecondsLabel` (`GymSettingsCopy.kt:220`) and
  `restWheelValueLabel` (`BuilderScreen.kt:620`) branch to なし at ≤ 0; `secondWheelValueLabel` :623
  deliberately has **no** なし branch. Three near-identical functions with two different zero rules.

### H4 — The wheel editor (`BuilderScreen.kt`, `StationPickerScreen.kt`)

Four wheels, four label functions (`BuilderScreen.kt:619-629`), each producing a **list of hundreds of
pre-rendered strings**:

```kotlin
internal fun restWheelValueLabel(seconds: Int): String =
    if (seconds <= 0) "なし" else JapaneseDate.kanjiExtended(seconds) + "秒"
internal fun secondWheelValueLabel(seconds: Int): String = JapaneseDate.kanjiExtended(seconds) + "秒"
internal fun repWheelValueLabel(reps: Int): String = JapaneseDate.kanjiExtended(reps) + "回"
internal fun roundWheelValueLabel(rounds: Int): String = JapaneseDate.kanjiExtended(rounds) + "巡"
```

Three compounding problems:

1. The labels are the wheel's **only** content, so translation changes every row of a scrollable column
   whose width is laid out for 2-4 kanji. 二百回 is 4 glyphs; "200 reps" is 8 characters.
2. `mergedWheelOptions` (`:597`) uses `labelFor` to synthesise the one row a built-in routine needs that
   the standard range does not contain (チェルシー's 30 rounds, マーフ's 200/300 reps, タバタ's 10 s
   rest). That synthesised label must come from the **same** localised template as the pre-built list, or
   one row in the column will be in the wrong language.
3. The same values are also rendered by `restOptions`/`secondOptions`/`repOptions`/`roundOptions` in
   `gym/BuilderDraft.kt` (out of scope) — the file's own KDoc says these four functions are **duplicates
   kept deliberately** because that file belongs to another unit. There are therefore **eight**
   implementations of "{n} + counter" for four wheels across two units, plus a ninth in
   `GymSettingsCopy.settingsSecondsLabel` and a tenth in `ui.gym.session.chosenSecondsLabel`. All must
   move together.

`StationPickerScreen.ValueWheel` :759 falls back to `selected.toString()` (arabic, unlabelled) when a
value has no label — a latent mixed-script path.

### H5 — Composed strings joined by 、 and ・

Every accessibility node in scope is built by `joinToString("、")` or `" ・ "`. Word order is fixed by
the Japanese specs and **will not survive translation**:

- `GymHomeCopy.resumeBannerDescription` :301 — `つづき、{name}、{elapsed} 経過、{progress}、続ける`
- `GymHomeCopy.routineCardDescription` :321 — and it **re-punctuates** `lastLine` by
  `replace(" ・ ", "、")` :324, so the visible and spoken forms of one line are produced from one string.
- `LibraryIndexScreen.routineCardCopy` :207 — 8-fragment description in a spec-fixed order
- `LibraryDetailScreen.startButtonDescription` :283, `stationRowSemantics` :244, `detailSubtitle` :366
- `ExerciseDetailScreen.rungSemantics` :172, tile descriptions :546
- `StationPickerScreen.exerciseSemantics` :111, `measureSemantics` :120
- `BuilderScreen.stationSemantics` :417, `moveAnnouncementWith` :442
- `GymSettingsCopy.settingsRowDescription` :255 — §B pins the order as label → state → reason, and
  `ToggleRow` (`GymSettingsScreen.kt:440`) uses `clearAndSetSemantics` **specifically** to force it,
  because `mergeDescendants` produced the fragments in the wrong order.

`、` is an ideographic comma (U+3001) and `・` is a katakana middle dot (U+30FB). Both must become ASCII
punctuation in a Latin build, and the separator itself is therefore a translatable token, not a constant.

### H6 — Speech / TTS: hard-coded `Locale.JAPANESE`

**`GymSettingsScreen.kt:775`**, inside `probeResult`:

```kotlin
val result = runCatching { engine.setLanguage(Locale.JAPANESE) }
    .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
```

This is the probe that decides whether the 音声 row is enabled at all. It reaches the UI as three
strings in `GymSettingsCopy.kt`:

- `SpeechAvailability.NoJapaneseVoice` → 日本語の音声が入っていません (`:78`) — **the copy names Japanese
  explicitly**, so translating the sentence without changing the probe produces "no English voice
  installed" for a probe that asked about Japanese.
- `SpeechAvailability.NoEngine` → 読み上げ機能がありません (`:79`)
- `SpeechAvailability.Available` / not-yet-probed → 種目の名前を読み上げる (`:77`)

The probe must take the app's selected language, and the sub-line must interpolate the language name.
Note the sibling copy at `gym/GymSpeech.kt` (out of scope) does the same mapping for the *player*'s
engine — the two must change together or the settings page will report a capability the cue engine does
not have.

Related: `SUB_SPEECH` = 種目の名前を読み上げる promises the *exercise names* are spoken. Exercise names
are `Exercise.nameJa` — Japanese catalogue data (see H11) — so with a Latin UI and a Japanese catalogue
the TTS engine is being asked to read Japanese under an English label.

### H7 — Persistence: Japanese written to the database, DataStore, and the device calendar

| what | where | why it matters |
|---|---|---|
| **Routine name** (user-typed) | `BuilderScreen.NameField` :1055 → `holder.save` :310 → `repository.saveRoutine` | Stored in SQLite as typed. `duplicateName` :465 compares `it.name == trimmed` — an **exact string match** on a persisted name. A user who authors in Japanese and switches the UI to English keeps Japanese routine names, and every 型 card, header and dialog title will be mixed-script. |
| **Station `note`** | `StationPickerScreen.carriedNote` :233 → `stationOf` :212 → `applyStation` → `saveRoutine` | Built-in stations carry Japanese notes (`一マイル`, `種目は自由に` per the in-file KDoc). The note is **threaded through unchanged** and is included in `structuralHash`, so translating it would make every existing routine read as dirty and would create a new `routine_version` on save. **Do not translate persisted notes.** |
| **Built-in routine names** | seeded (`BuiltInCatalog`, out of scope), rendered everywhere in this scope | 七分間, シンディ, タバタ, マーフ, チェルシー, リーコン・ロン, バーバラ, デス・バイ. These are `catalog.*` per the contract and are DB rows, not UI literals. Changing them orphans `routine.name` and every `session_result` that pins a version. |
| **Exercise names / cues** | `Exercise.nameJa`, `Exercise.cue` — in-memory catalogue, rendered by `ExerciseIndexScreen`, `ExerciseDetailScreen`, `StationPickerScreen`, `LibraryDetailScreen`, `BuilderScreen` | The field is literally called `nameJa`. Adding a second language means a second column, not a translation of the existing one. |
| **Routine name → device calendar event title** | `ScheduleNextAction.kt:178` → `scheduleDraft(routineName, …)` → `CalendarRepository.insert` | Writes Japanese into a **shared, external** store that syncs to the user's other devices. Nothing here can be retroactively re-localised. |
| **Units enum** | `GymSettingsScreen` → `setUnits` | **Safe**: persisted as `units.name` (`GymPreferencesRepository.kt:82`), an ASCII enum name. Only `Units.label` (メートル法 / ヤード・ポンド法) is Japanese, and it is display-only. This is the pattern the other stores should follow. |

### H8 — String-matched row dispatch (`BuilderScreen.kt:966-1016`)

```kotlin
rows.forEach { row ->
    when (row.label) {
        ROW_STATION_REST -> WheelRow(...)   // "種目の間の休息"
        ROW_ROUND_REST   -> WheelRow(...)   // "巡の間の休息"
        ROW_ROUNDS       -> if (draft.engine == Engine.AMRAP) ReadOnlyRow(...) else WheelRow(...)
        else             -> ReadOnlyRow(row.label, row.value)   // 制限時間 and anything unmatched
    }
}
```

**A Japanese literal is control flow here.** `engineRows` (`gym/EngineRows.kt`, out of scope) produces
the labels; this file re-declares the same three literals as `private const val` and dispatches on
equality. Translate `engineRows` and every wheel row silently degrades into a read-only row: the
builder's three dialable settings become undialable, with no compile error and no test failure unless
`BuilderScreenTest`'s assertions happen to cover it. **This must be converted to a stable key/enum
before any string moves.**

### H9 — Layout that assumes short strings

- **`GymTabBar.kt:127-132`** — three tab words at Mincho 13.sp with `letterSpacing = 3.sp` in a fixed
  64.dp bar, each in `Modifier.weight(1f)`, with **no `maxLines` and no overflow handling**. 鍛錬 / 型 /
  記録 are 2, 1 and 2 glyphs. "Training" / "Routines" / "Records" will not fit at 3.sp tracking.
- **`BuilderScreen.kt:1344`** — `STATION_ROW_HEIGHT = 56.dp`, fixed, with `maxLines = 1` +
  `TextOverflow.Ellipsis` on the name (:1366). The KDoc states outright that **the drag maths depends on
  every row being exactly this height** — a row that wrapped would put every other row's `translationY`
  out by the difference. Longer exercise names in any language will ellipsise, not wrap; that is a
  deliberate trade, but the ellipsis point moves.
- **`BuilderScreen.kt:1285`** — warning lines are *hidden during a drag* for the same reason.
- `maxLines = 1` with ellipsis on card names/summaries: `GymHomeScreen` :427/:560/:566/:584,
  `LibraryIndexScreen` :781/:803(2 lines)/:817, `ExerciseIndexScreen` :479/:491/:505,
  `ExerciseDetailScreen` :486(2)/:639, `StationPickerScreen` :548, `LibraryDetailScreen` :665(2)/:1024.
- **`letterSpacing` of 2-4.sp on Mincho** appears on ~40 `TextStyle`s across the scope. Japanese
  tracking of 3-4.sp is elegant; applied to Latin text it reads as broken. Every one of these is a style
  that must become language-conditional.

### H10 — Spoken-only strings that differ from the visible ones

- `LibraryIndexScreen.sectionSemantics` :228 — 「よく使う、二件」 is **announced but never drawn**; the
  KDoc records that a visible count was shipped and then rejected. Translating the visible heading
  without the description leaves a mismatch nobody can see.
- `LibraryDetailScreen.startButtonDescription` :283 — the **visible** label changes (始める → 支度) while
  the description deliberately does not.
- `ScheduleNextAction.kt:311/:313` — label 保存中, description 保存.
- `BuilderScreen.saveDescription` :484 — the disabled reason is suppressed while saving.
- `GymHomeCopy.routineCardDescription` :324 — one line, two punctuations.

### H11 — Search, sorting and matching over Japanese

- `matchExercise(exercise, query)` — called from `ExerciseIndexScreen.exerciseSections` :232 and
  `StationPickerScreen.pickerGroups` :156. Implementation is `gym/LibraryFilters.kt` (out of scope) and
  does **kana folding**; the picker trims the query first (:154) specifically because "leading space is
  what a Japanese IME leaves behind between conversions".
- `ExerciseIndexScreen.exerciseSections` :233 sorts ties by `nameJa` — a Japanese collation.
- `Pattern.entries` ordinal order is a **deliberate non-alphabetical** ordering (fatigue alternation);
  it must not become alphabetical when the labels change language.
- `BuilderScreen.duplicateName` :465 — exact-match on trimmed names, no normalisation.

### H12 — One dialog, three independent implementations

`「{name}」を削除しますか` + its two bodies + its two confirm words exist **three times**:

1. `GymHomeCopy.deleteRoutineCopy` :341 → `DeleteRoutineCopy(title, body, confirm)`
2. `LibraryIndexScreen` — calls (1), plus its own やめる at :923
3. `LibraryDetailScreen.detailDeleteCopy` :183 → `DetailDeleteState` sealed interface with **three**
   states (`Confirm` / `Waiting` / `Unreadable`), deliberately named apart to avoid a redeclaration

The three disagree on shape (data class vs. sealed interface), on the count's type (`Int` vs.
`Loadable<Int>`) and on whether やめる is inside the copy. The strings are identical. Each is separately
pinned by tests. Migrating them independently guarantees drift; the merge step should unify them onto
one key set.

### H13 — Faults are a separate copy layer this scope only calls

`FaultStrip` / `FaultPanel` / `faultCopy(fault)` supply 記録を読めません, もう一度, 保存できませんでした,
空き容量が足りません, この型は削除されています. They live in `ui/` and `data/` (out of scope) but are
rendered at 14 sites in this scope. `GymHomeCopy.writeFaultStuck` :389 **branches on
`faultCopy(fault).action == null`** — i.e. on whether a fault has an action *word* — so the fault copy
table's shape is load-bearing for this scope's layout, not just its text.

### H14 — Tests asserting Japanese literals

**222 assertions across 15 test files** will break on migration:

| test file | JP-literal assertions |
|---|---|
| `GymHomeCopyTest.kt` | 40 |
| `LibraryDetailCopyTest.kt` | 40 |
| `BuilderScreenTest.kt` | 34 |
| `ExerciseCatalogueCopyTest.kt` | 34 |
| `GymSettingsCopyTest.kt` | 32 |
| `LibraryIndexScreenTest.kt` | 19 |
| `StationPickerScreenTest.kt` | 12 |
| `ExerciseScreenStructureTest.kt` | 3 |
| `GymShellTest.kt` | 3 |
| `LibraryIndexScreenStructureTest.kt` | 2 |
| `ScheduleNextAccessTest.kt` | 2 |
| `BuilderScreenStructureTest.kt` | 1 |
| `GymMotionTest.kt`, `KeepAwakeTest.kt`, `GymSettingsScreenStructureTest.kt`, `ResumePromptTest.kt` | 0 |

(All under `app/src/test/java/io/eddiegulay/tempo/ui/gym/`. Counted as assertion lines containing a
Japanese string literal; comments excluded.)

---

## Non-visible Japanese

**None in this scope.** Checked explicitly:

- `LazyColumn`/`LazyListScope` item keys are ASCII throughout (`"frequent"`, `"builtin"`, `"user"`,
  `"resume"`, `"write-fault"`, `"head:$keyPrefix"`, `"exercise:${it.id}"`, `"action:exercises"`).
- `HomeSection.keyPrefix` (`GymHomeCopy.kt:77-81`) returns ASCII deliberately.
- Animation and state labels are ASCII (`"gym-route"`, `"toggle-word"`).
- `ExerciseIndexScreen.RUN_ID = "run"` — the one catalogue id in any UI file, ASCII.
- `Units` is persisted by enum `.name`, not by its Japanese label.
- No log tags, no test fixtures and no DB column values are authored in these files.

Two adjacent categories that are **not** literals in this scope but must not be translated, recorded
here so the merge does not miss them: the **station `note`** threaded through
`StationPickerScreen.carriedNote` :233 (a persisted DB value, included in `structuralHash`), and the
**routine/exercise names** rendered from the database and the catalogue (`catalog.*` per the contract).
Both are covered in hazard H7.

---

## External copy this scope renders but does not own

Listed so the merge can attribute them once. Every one of these is drawn by a file in this scope.

| source | strings |
|---|---|
| `gym/GymRoute.kt:15` `GymTab` | 鍛錬 / 型 / 記録 (the tab bar's entire content) |
| `gym/GymModels.kt:47` `Engine` | 巡回 / 段階 / 毎分 / 毎分増 / 完走 / 完走 ・ 休息あり / 時間内 |
| `gym/GymModels.kt:75` `Pattern` | 押す / 引く / しゃがむ / 股関節 / 体幹 / 移動 / 跳ぶ |
| `gym/GymModels.kt:101` `Measure` | 回数 / 秒数 / 限界まで |
| `gym/GymModels.kt:124` `Tier` | 初級 / 中級 / 上級 (tier chips) |
| `gym/GymPreferences.kt:11` `Units` | メートル法 / ヤード・ポンド法 |
| `gym/LibraryFilters.kt:180` `DurationBucket` | duration chip labels |
| `gym/EngineRows.kt` | `EngineRow.label`/`.value` (制限時間, 巡数, 種目の間の休息, 巡の間の休息, 時間内で), `BestTile.label`, `bestMetricLabel`, `bestValueLabel` |
| `gym/RecordCopy.kt:99` `PrChip` | 自己最高 / 当時の自己最高; `partialChipCopy` → 途中まで |
| `gym/Numerals.kt` | `durationKanji`, `durationKanjiFromMs`, `coefficientLabel` (一.〇 / 〇.五 / —), `heroTime` |
| `data/JapaneseDate.kt` | `kanjiExtended`, `kanji`, `era`, `monthDay`, `dayToken`, `clock` |
| `gym/BuilderDraft.kt` | `restOptions`, `secondOptions`, `repOptions`, `roundOptions` wheel labels; `estimateLabel` (約七分, 目安) |
| `gym/PatternWarning.kt` | `clashCopy` → 「{a} と {b} は続けて置かない方がよい」 |
| `gym/ScheduleNext.kt:28` | 予定に入れる |
| `gym/Progression.kt` | `stepFor` → 第七段 / 十八段のうち |
| `ui/` chrome | `faultCopy`, `FaultStrip`, `FaultPanel`, `HeaderAction`, `TempoValueWheel`, `TempoDateTimeWheel`, `EventConfirmDialog`, `draftSummary` |
