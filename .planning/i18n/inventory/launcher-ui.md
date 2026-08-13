# Launcher UI (non-gym)

**Files surveyed:** 25   **User-visible literals:** 144   **Non-visible JP literals:** 21

Scope: `app/src/main/java/io/eddiegulay/tempo/ui/*.kt` plus `ui/theme/*.kt`. Nothing under `ui/gym/`
was read. `CalendarFeedback.kt` is in this scope but holds the 鍛錬 fault copy too; those rows are
marked so the merge step can hand them to the gym fragment without losing them.

Nine of the twenty-five files contain no user-visible Japanese at all; they are listed at the end in
one line each, because several of them are the *reason* the hazards below exist.

---

## app/src/main/java/io/eddiegulay/tempo/ui/OnboardingScreen.kt

Purpose: the first-launch gate — the first screen any user ever sees. It names each special access
Tempo relies on, explains why, and will not let 始める fire until each is granted or deferred.

| key | ja | context | notes |
|---|---|---|---|
| `onboarding.welcome` | ようこそ | page title, :100 | |
| `onboarding.intro` | はじめる前に、Tempo が使う権限をお知らせします。いずれも端末の中だけで使われ、外部へ送信されることはありません。 | lede, :105–106 | **consent copy.** Two source lines concatenated with `+`; one sentence pair. Contains the product name "Tempo" mid-sentence |
| `onboarding.access.home.title` | 既定のホーム | first access item, :119 | |
| `onboarding.access.home.rationale` | ホームボタンを押したときに Tempo が開くようにします。ランチャーとしての基本的な動作に必要です。 | :120–121 | **consent copy.** Concatenated over two lines; embeds "Tempo" |
| `onboarding.access.notifications.title` | 通知へのアクセス | second access item, :131 | same literal as `notifications.access.title` (NotificationsScreen :511) |
| `onboarding.access.notifications.rationale` | 受信した通知を読み取り、通知画面（通知）に静かに表示します。内容が端末の外に出ることはありません。 | :132–133 | **consent copy.** Concatenated; contains a self-reference to the 通知 page in full-width parens |
| `onboarding.access.granted` | 許可済み | settled state line, :189 | |
| `onboarding.access.grant` | 許可 | grant action, :191 | also used as the button's `contentDescription` via `TextAction`, :221 |
| `onboarding.access.defer` | 後で | defer action, :193 | |
| `onboarding.access.deferred` | 後で設定 | settled-deferred line, :195 | |
| `onboarding.begin` | 始める | primary button, :252 and :256 | the literal appears twice — once as `contentDescription`, once as the drawn label |

**This is the flagged file.** Both rationales are permission explanations. If the language toggle
lives behind onboarding (it currently does not exist at all), an English-locale user is shown two
Japanese paragraphs explaining that Tempo will read every notification on their device and become
their home app, and then a Japanese button that grants it. That is a consent defect, not a cosmetic
one: the user cannot have understood what they agreed to. Whatever else ships first, onboarding must
be able to render in the user's language *before* the first 許可 is pressable — which means the
language choice must be resolvable at or before first launch, not from a settings page reached later.

---

## app/src/main/java/io/eddiegulay/tempo/ui/HomeScreen.kt

Purpose: the home layer — ensō ring, the next calendar event set vertically in the top-right corner,
the big mincho clock, and the vermillion 静 seal.

| key | ja | context | notes |
|---|---|---|---|
| `home.mode.chooseLabel` | モードを選ぶ | long-press semantics label on the clock, :125 | accessibility-only; never drawn |
| `home.corner.nextEvent` | 次の予定、$day、$time、${event.title} | corner `contentDescription`, :191 | **composed.** Four parts joined by 、; `day`/`time` come from `JapaneseDate`; `title` is user data |
| `home.corner.date` | `${era} ${monthDay} ${dayOfWeek}` | corner description when no event, :187 | **composed** from three `JapaneseDate` outputs, space-separated |
| `home.event.allDay` | 終日 | corner time column, :190 (description) and :254 (drawn) | same literal as `calendar.event.allDay` |
| `home.corner.open` | 予定 | `onClick` semantics label, :214 | accessibility-only |
| `home.seal` | 静 | the vermillion seal, :327 | a *mark*, not a word — see hazards |

---

## app/src/main/java/io/eddiegulay/tempo/ui/SearchScreen.kt

Purpose: Search (検索), which doubles as the app drawer — a mincho input over a live-filtered list of
every installed app, with a per-row long-press menu.

| key | ja | context | notes |
|---|---|---|---|
| `fmt.app.updatedDate` | M月d日 | `DateTimeFormatter` pattern, :63 | **format pattern**, not a string; produces "6月10日" |
| `search.kana` | けんさく | faint kana super-title, :114 | hiragana gloss of the page name; has no English analogue |
| `search.field.placeholder` | 検索 | text-field placeholder, :147 | |
| `search.header.hidden` | 非表示アプリ | filter-button `contentDescription`, :123 | |
| `search.header.theme.toLight` | ライトテーマに切り替え | theme-toggle description when dark, :127 | |
| `search.header.theme.toDark` | ダークテーマに切り替え | theme-toggle description when light, :127 | |
| `search.loading` | ・・・ | list placeholder while apps load, :168 | three ideographic middle dots, not an ASCII ellipsis |
| `search.empty` | 見つかりません | no-results state, :168 | |
| `search.row.launch` | 起動 | `onClickLabel` on an app row, :201 | accessibility-only |
| `search.row.menu` | メニュー | `onLongClickLabel` on an app row, :202 | accessibility-only |
| `fmt.app.updatedPrefix` | 更新 ⟨space⟩ | prefix before the formatted date, :226 | **composed**: `"更新 " + formattedDate`, then joined to the category with " · " |
| `search.menu.appInfo` | アプリ情報 | dropdown item, :241 | |
| `search.menu.hide` | 非表示にする | dropdown item, :248 | |
| `search.menu.uninstall` | アンインストール | dropdown item, :255 | katakana transliteration of "uninstall" |

---

## app/src/main/java/io/eddiegulay/tempo/ui/FilterScreen.kt

Purpose: the hidden-apps page (非表示アプリ), reached from Search. Hiding an app is a 10-day
commitment; blocked rows show a countdown and refuse to un-hide until it elapses.

| key | ja | context | notes |
|---|---|---|---|
| `filter.kana` | ひひょうじ | faint kana super-title, :53 | |
| `filter.title` | 非表示アプリ | page title, :58 | |
| `filter.subtitle` | 非表示にすると10日間は解除できません | rule under the title, :62 | **the 10 is baked into the string** while the dialog reads it from `BlockadeRepository.BLOCK_DAYS`; they can disagree |
| `filter.row.unlockable` | 解除できます | row subtitle once the block elapsed, :109 | |
| `fmt.filter.remaining` | あと${remainingLabel(remaining)} | row subtitle while blocked, :110 | **composed**: prefix + a unit-bearing fragment |
| `fmt.filter.remaining.days` | ${n}日 | :158 | counter suffix 日; no plural |
| `fmt.filter.remaining.hours` | ${n}時間 | :158 | counter suffix 時間; no plural |

---

## app/src/main/java/io/eddiegulay/tempo/ui/FocusScreen.kt

Purpose: 集中 — the full-screen landscape split-flap clock that doubles as a Pomodoro timer. Entered
from the Home clock long-press via `ModeDialog`.

| key | ja | context | notes |
|---|---|---|---|
| `focus.phase.focus` | 集中 | `PomodoroPhase.Focus.label`, :57 | **an enum constructor argument** — the copy lives on the enum, not at the draw site (:173) |
| `focus.phase.shortBreak` | 休憩 | `PomodoroPhase.ShortBreak.label`, :58 | same |
| `focus.phase.longBreak` | 長休憩 | `PomodoroPhase.LongBreak.label`, :59 | same |
| `focus.hint.clock` | タップで秒 ・ 長押しでポモドーロ | gesture hint in clock mode, :136 | one string containing its own " ・ " separator |
| `focus.hint.pomodoro` | タップで開始 / 一時停止 ・ 長押しで時計 | gesture hint in pomodoro mode, :137 | contains both " / " and " ・ " |
| `focus.control.reset` | リセット | control label, :202 | |
| `focus.control.running` | 計測中 | start/pause control while running, :205 | |
| `focus.control.paused` | 停止中 | start/pause control while paused, :205 | |
| `focus.control.skip` | スキップ | control label, :208 | |

Note: the three control labels are drawn in a fixed `Row` with 28.dp spacing at 16.sp mincho, sized
by content — see hazards on the landscape width budget.

---

## app/src/main/java/io/eddiegulay/tempo/ui/CalendarScreen.kt

Purpose: Calendar (予定) — the next fortnight from the device calendar provider, grouped by day.
Reached only by tapping Home's top-right cluster; it has no dock tab.

| key | ja | context | notes |
|---|---|---|---|
| `calendar.title` | 予定 | page title, :105 | |
| `calendar.header.date` | `${era} ・ ${monthDay}` | sub-header, :110 | **composed** from two `JapaneseDate` outputs around " ・ " |
| `calendar.add` | 加える | header action label, :116 | |
| `calendar.add.description` | 予定を加える | header action `contentDescription`, :117 | same literal as `compose.heading.create` |
| `calendar.loading` | 読み込み中 | loading state, :140 | |
| `calendar.empty` | 予定はありません | empty state, :142 | deliberately never shown for a *failed* read — see `CalendarFeedback` |
| `fmt.calendar.dayHeader` | $label、${count}件 | day-divider `contentDescription`, :175 | **composed + counter 件**; `label` is `JapaneseDate.dayHeading` |
| `calendar.event.allDay` | 終日 | card time label, :201 | |
| `fmt.calendar.allDaySpan` | 終日 ・ ${monthDay}まで | multi-day all-day detail, :208 | **composed**, with the postposition まで appended to a formatted date |
| `calendar.event.recurring` | 繰り返し | source line on a recurring event, :221 | joined to `calendarName` with " ・ " at :222 |
| `calendar.event.now` | いま | ongoing badge, :226 (description) and :267 (drawn) | |
| `calendar.access.title` | 予定へのアクセス | permission gate heading, :333 | |
| `calendar.access.deniedTitle` | 設定から許可してください | gate heading once permanently denied, :333 | |
| `calendar.access.tapToGrant` | タップして許可 | gate action, :337 | same literal as `notifications.access.action` |
| `calendar.access.openSettings` | 設定を開く | gate action once permanently denied, :337 | |

The card `contentDescription` at :226 is `listOfNotNull(title, detail, source, いま).joinToString("、")`
— a four-part composition around the ideographic comma.

---

## app/src/main/java/io/eddiegulay/tempo/ui/CalendarFeedback.kt

Purpose: the single place a `TempoFault` becomes words (`faultCopy`), the strip and panel that render
one, and the confirmation dialog that gates every calendar write. Shared by the calendar *and* by
鍛錬 — the gym rows below belong to the gym fragment's key space, not the launcher's.

| key | ja | context | notes |
|---|---|---|---|
| `fault.unknownFamily.message` | うまくいきませんでした | catch-all branch, :65 | unreachable today; kept so no fault is silent |
| `fault.retry` | もう一度 | action word, :65 :79 :85 :110 | one literal, five sites — the merge step should collapse it |
| `fault.calendar.permissionLost.message` | カレンダーへのアクセスが必要です | :70 | |
| `fault.calendar.permissionLost.action` | 許可する | :70 | **pinned by `CalendarFeedbackTest` :67** |
| `fault.calendar.noWritableCalendar.message` | 書き込めるカレンダーがありません | :73 | |
| `fault.calendar.noWritableCalendar.action` | アカウントを追加 | :73 | **pinned by `CalendarFeedbackTest` :72** |
| `fault.calendar.eventGone.message` | この予定はもうありません。ほかの端末で削除されたようです | :76 | two sentences in one literal |
| `fault.calendar.eventGone.action` | 予定へ戻る | :76 | |
| `fault.calendar.rejected.message` | この予定を保存できませんでした | :79 | |
| `fault.calendar.noCalendarApp.message` | カレンダーのアプリが見つかりません | :82 | the one fault with no action |
| `fault.calendar.unknown.message` | カレンダーにつながりません | :85 | |
| `gym.fault.storeUnreadable.message` | 記録を読めません | :110 | **gym-owned.** Pinned five times by `CalendarFeedbackTest` (:86–89, :137, :148), including an assertion that it does *not* contain ありません |
| `gym.fault.storeFull.message` | 空き容量が足りません | :114 | **gym-owned.** Pinned at `CalendarFeedbackTest` :90, :111 |
| `gym.fault.routineGone.message` | この型は削除されています | :118 | **gym-owned.** Pinned :91 |
| `gym.fault.sessionGone.message` | この記録は削除されています | :121 | **gym-owned.** Pinned :92 |
| `gym.fault.rejected.message` | 保存できませんでした | :125 | **gym-owned.** Pinned :93 |
| `dialog.eventWrite.create.heading` | 予定を加えますか | :227 | |
| `dialog.eventWrite.update.heading` | 予定を変えますか | :228 | |
| `dialog.eventWrite.delete.heading` | 予定を削除しますか | :229 | |
| `dialog.eventWrite.create.confirm` | 加える | :232 | |
| `dialog.eventWrite.update.confirm` | 変える | :233 | |
| `dialog.eventWrite.delete.confirm` | 削除する | :234 | |
| `dialog.eventWrite.create.consequence` | ほかの端末のカレンダーにも表示されます。 | :239 | |
| `dialog.eventWrite.update.consequence` | 変更はほかの端末のカレンダーにも反映されます。 | :240 | |
| `dialog.eventWrite.delete.consequence` | ほかの端末のカレンダーからも消えます。元に戻せません。 | :241 | two sentences |
| `dialog.eventWrite.cancel` | やめる | :283 | |
| `calendar.draft.untitled` | （無題） | blank-title fallback, :294 | full-width parentheses. **Pinned by `CalendarFeedbackTest` :195** |
| `fmt.calendar.span.allDay` | `${monthDay} ・ 終日` | :305 | **composed** |
| `fmt.calendar.span.timed` | `${monthDay} ・ ${clock} – ${clock}` | :307 | **composed**, with an en dash. Pinned loosely by `CalendarFeedbackTest` :165 |

---

## app/src/main/java/io/eddiegulay/tempo/ui/EventComposeScreen.kt

Purpose: the event composer — add (予定を加える), edit (予定を編集) or view (予定) a calendar event.
Reached by tapping a card on Calendar or the 加える header action.

| key | ja | context | notes |
|---|---|---|---|
| `compose.heading.view` | 予定 | read-only heading, :126 | |
| `compose.heading.edit` | 予定を編集 | edit heading, :127 | |
| `compose.heading.create` | 予定を加える | create heading, :128 | |
| `compose.close` | とじる | header action when read-only, :163–164 | label and description are the same literal |
| `compose.cancel` | やめる | header action otherwise, :163–164 | |
| `compose.saving` | 保存中 | save label while writing, :170 | |
| `compose.save` | 保存 | save label and its description, :170–171 | description stays 保存 even while the label says 保存中 |
| `compose.recurringNotice` | 繰り返しの予定 | shown on a read-only recurring event, :219 | |
| `compose.field.allDay` | 終日 | toggle row label + `contentDescription`, :228 | |
| `compose.toggle.on` | する | toggle value and `stateDescription`, :422 :433 | |
| `compose.toggle.off` | しない | toggle value and `stateDescription`, :422 :433 | |
| `compose.field.start` | 開始 | picker row label, :240 | |
| `compose.field.end` | 終了 | picker row label, :258 | |
| `fmt.compose.pickerRow` | $label、$value | picker row `contentDescription`, :462 | **composed** around 、 |
| `compose.field.title` | 題名 | placeholder :366 and `contentDescription` :361 | |
| `compose.field.location` | 場所 | placeholder :399 and `contentDescription` :395 | |
| `compose.field.calendar` | カレンダー | chip-group label, :492 | |
| `compose.chip.selected` | 選択中 | chip `stateDescription`, :506 | empty string when unselected |
| `compose.openInCalendarApp` | カレンダーで開く | read-only escape hatch, :284 | |
| `compose.delete` | 削除 | destructive action, :292 | |

`formatValue` (:325–328) composes the field value as `${monthDay}` or `${monthDay} ・ ${clock}`.

---

## app/src/main/java/io/eddiegulay/tempo/ui/NotificationsScreen.kt

Purpose: Notifications (通知) — the device's current notifications as washi cards, grouped per app,
swipe-to-dismiss with an undo strip. Third dock tab.

| key | ja | context | notes |
|---|---|---|---|
| `notifications.title` | 通知 | page title, :119 | |
| `notifications.header.date` | `${era} ・ ${monthDay}` | sub-header, :124 | **composed**, identical shape to Calendar's |
| `notifications.row.dismiss` | 消去 | `CustomAccessibilityAction` label, :212 | accessibility-only; the swipe gesture is invisible to TalkBack without it |
| `notifications.row.open` | 開く | `onClick` semantics label, :249 | accessibility-only |
| `notifications.reply.description` | 返信を入力 | reply field `contentDescription`, :380 | |
| `notifications.reply.placeholder` | 返信 | reply field placeholder, :386 | |
| `fmt.notifications.groupHeader` | ${appLabel}、${size}件 | group header `contentDescription`, :410 | **composed + counter 件** |
| `fmt.notifications.more` | 他 $hiddenCount 件 | collapse toggle when collapsed, :438 | **composed + counter 件**, number interpolated mid-string |
| `notifications.collapse` | 折りたたむ | collapse toggle when expanded, :438 | |
| `notifications.clearAll` | すべて消去 | header action, :462 and :467 | literal appears twice (description + label) |
| `fmt.notifications.undoCount` | $count 件を消去 | undo strip, :486 | **composed + counter 件**; also a *past-tense claim* about N items |
| `notifications.undo` | 元に戻す | undo action, :493 and :498 | literal twice (description + label) |
| `notifications.access.title` | 通知へのアクセス | permission gate, :511 | same literal as `onboarding.access.notifications.title` |
| `notifications.access.action` | タップして許可 | permission gate action, :515 | same literal as `calendar.access.tapToGrant` |
| `notifications.empty` | 通知はありません | quiet state, :528 | |

The row `contentDescription` at :206–209 joins `appLabel`, `title`, `body`, `time` with 、 — all four
parts are foreign app data, so only the separator is ours.

---

## app/src/main/java/io/eddiegulay/tempo/ui/ModeDialog.kt

Purpose: the gate out of the launcher, raised by long-pressing the Home clock. Two rows: 集中 (Focus)
and 鍛錬 (the gym), each with a one-line gloss.

| key | ja | context | notes |
|---|---|---|---|
| `dialog.mode.focus.title` | 集中 | first row, :55 | also used as the row's `onClickLabel` (:104), so TalkBack says "double tap to 集中" |
| `dialog.mode.focus.subtitle` | 時計だけの画面 | gloss, :56 | |
| `dialog.mode.gym.title` | 鍛錬 | second row, :67 | same double duty as the `onClickLabel` |
| `dialog.mode.gym.subtitle` | 体を動かす | gloss, :68 | |
| `dialog.mode.cancel` | やめる | dismiss button, :76 | |
| — | → | trailing arrow, :121 | not text to translate; already stripped from semantics via `clearAndSetSemantics` |

---

## app/src/main/java/io/eddiegulay/tempo/ui/BlockConfirmDialog.kt

Purpose: the commitment gate for hiding an app. Confirm stays disabled until the acknowledgement
checkbox is ticked.

| key | ja | context | notes |
|---|---|---|---|
| `fmt.dialog.block.heading` | ${days}日間ふうじる | title :48 and confirm button :85 | **composed + counter 日間**; `days` is `BlockadeRepository.BLOCK_DAYS` |
| `fmt.dialog.block.body` | 「$appLabel」を非表示にすると、${days}日間は元に戻せません。アプリを削除して入れ直しても、期間が終わるまで解除されません。 | :55–56 | **composed** across two concatenated lines, two interpolations, corner brackets around user data, two sentences |
| `dialog.block.acknowledge` | 理解しました | checkbox label, :76 | |
| `dialog.block.cancel` | やめる | dismiss, :92 | |

---

## app/src/main/java/io/eddiegulay/tempo/ui/BlockedInfoDialog.kt

Purpose: shown when the user taps an app whose block has not elapsed — a live per-second countdown to
the unlock moment.

| key | ja | context | notes |
|---|---|---|---|
| `dialog.blocked.heading` | まだ解除できません | title, :50 | |
| `fmt.dialog.blocked.body` | 「$appLabel」のふうじが解けるまで | lead line, :57 | **composed**; corner brackets around user data, and the clause ends on まで expecting the countdown below it to complete the sentence |
| `fmt.dialog.blocked.countdown` | ${days}日 HH:mm:ss | countdown, :103 | **composed + counter 日**; falls back to bare `HH:mm:ss` under a day |
| `dialog.blocked.footnote` | アプリを削除しても期間は続きます | footnote, :69 | |
| `dialog.blocked.dismiss` | わかりました | confirm button, :78 | |

---

## app/src/main/java/io/eddiegulay/tempo/ui/Dock.kt

Purpose: the floating bottom pill — Home / Search / Notifications / 鍛錬 — plus a long-press on the
capsule itself that requests the default-home role.

| key | ja | context | notes |
|---|---|---|---|
| `app.dock.setDefaultHome` | Tempoを既定のホームに設定 | `onLongClick` semantics label on the pill, :81 | accessibility-only, and **the only announcement of this gesture anywhere** — the pill has no visible affordance for it. Embeds the product name with no particle before を |
| `app.dock.home` | ホーム | Home tab `contentDescription`, :91 | |
| `app.dock.search` | 検索 | Search tab `contentDescription`, :92 | |
| `app.dock.notifications` | 通知 | Notifications tab `contentDescription`, :93 | |
| `app.dock.gym` | 鍛錬 | 鍛錬 tab `contentDescription`, :94 | the recently added fourth button; icon-only, so this description is the sole naming of it |

All four tabs render a `LineIcon` only — no drawn text — so these four strings are what a screen
reader user navigates the entire launcher by. The long-press label at :81 is registered only when
`!isDefaultLauncher`, matching the gesture's own guard at :76.

---

## app/src/main/java/io/eddiegulay/tempo/ui/AppGlyph.kt

Purpose: Tempo's internal hand-drawn icon set and the four-pass resolver that maps an installed app to
a glyph (known package → package keyword → **display-label keyword** → declared category), falling
back to a monogram tile of the app's first character.

| key | ja | context | notes |
|---|---|---|---|
| `app.glyph.monogramFallback` | ・ | monogram when the label is empty, :126 | ideographic middle dot; the only drawn literal in the file |

The 21 Japanese entries in `LABEL_KEYWORDS` (:277–297) are matching keys, not copy — listed under
Non-visible Japanese below. They are still a migration hazard: see hazard 8.

---

## Files with no user-visible Japanese

- `TempoApp.kt` — zero. Routing, layers, back handling, wake-lock, dialog hosting; Japanese appears only in KDoc.
- `Tategaki.kt` — zero Japanese literals. It renders one non-Japanese literal, `"…"` (:70), as the truncation marker.
- `FlipClock.kt` — zero. Renders whatever `text` it is given, one character per card.
- `MinuteClock.kt` — zero. Two tickers (`rememberMinuteTime`, `rememberSecondTime`); no strings.
- `Enso.kt` — zero. One `drawArc`.
- `Background.kt` — zero. Gradient, grain tile, wet-paper dock fill.
- `CycleDots.kt` — zero drawn strings. Its `label` parameter is caller-supplied; the kanji in its KDoc ("三巡目 / 十二巡") are documentation of what a caller must pass, not code.
- `LineIcon.kt` — zero. SVG path data only.
- `TempoWheel.kt` — zero literals of its own. It renders `List<String>` columns its callers build, plus `"%02d"` hour/minute formats (:222, :230).
- `theme/TempoTheme.kt` — zero. Colour tokens only.
- `theme/Type.kt` — zero strings, but it is the font hazard (below).

---

## Hazards

### 1. Vertical text (tategaki) — an open design question, not a translation

`ui/Tategaki.kt` is a complete Japanese vertical-typesetting engine, and it is **called from exactly
one place**: `HomeScreen.kt:269`, which sets the next calendar event's title in the top-right corner.
Nothing else in the app calls `TategakiText`; the only other references to `layoutTategaki` are KDoc
citations in `CycleDots.kt`, `GymRoute.kt`, `GymMath.kt` and `ui/gym/session/SessionReplay.kt` naming
it as the repo's precedent for testable pure functions.

What it actually renders (`layoutTategaki`, `Tategaki.kt:113–148`):
- Every character with `ch.code >= 0x2E80` (`isUpright`, :148) becomes **one upright cell**, one per
  glyph, stacked in a `Column` with 3.dp between cells.
- Every maximal run *below* U+2E80 — Latin, digits, ASCII punctuation, and the spaces between them —
  becomes **one cell rotated 90° clockwise** (`quarterTurn()`, :84–101, which swaps the measured
  width/height so the rotated node actually occupies a rotated box).
- Exception (縦中横, :137): a run of **one or two digits** stays upright as a single cell —
  `val upright = run.length <= 2 && run.all { it.isDigit() }`.
- After `maxCells` (default 8) it stops and appends a `"…"` cell (:69–71).

So for an English user the corner's behaviour is already defined and already strange: an English
event title is a single sideways strip of text the reader must tilt their head to read, capped by
`heightIn(max = 150.dp)` (`HomeScreen.kt:279`) and ellipsised. A title like `1:1 with Mei` is one
rotated cell (asserted in `TategakiTest`:59). A mixed title splits: `会議 Standup` → 会 / 議 /
`Standup` sideways (`TategakiTest`:37).

**This is not fixable by a string table.** Someone has to decide what the Home corner *is* in English:
horizontal text in the same corner (which changes the layout — the corner is a `Row` of columns read
right-to-left, `HomeScreen.kt:229`), a shorter truncation, or keeping the rotation as a deliberate
stylistic choice. Record it as a design decision with a fallback, not as a key.

Related: `TategakiTest.kt` (11 tests) pins the segmentation rule with Japanese fixtures. The rule
itself is language-agnostic and the tests survive translation — but if the corner stops using
tategaki in English, those tests describe a code path with no caller.

### 2. Per-glyph and per-character rendering — one character, one cell

Four separate places assume a character is a square cell that can be drawn, measured or animated on
its own. Latin breaks all four.

- **`HomeScreen.kt:299–313`, `VerticalLine`** — `text.forEach { ch -> Text(text = ch.toString(), …) }`.
  Every character of the date/time column becomes its own `Text` node in a `Column`. It is fed
  `JapaneseDate.dayOfWeek` / `monthDay` / `era` (:293–295) and the event's time/day tokens (:282–283).
  Given "Wednesday" it renders nine stacked letters. There is no `isUpright` logic here at all — this
  is the naive stack that `Tategaki.kt`'s own KDoc (:29–31) calls "a ransom note".
- **`FlipClock.kt:56–71`** — `text.forEach { ch -> … FlipDigit(digit = ch, …) }`, each character
  placed in a fixed `Box(cardWidth, cardHeight)` (:86) with an `AnimatedContent` flip keyed on the
  character. Callers pass only `"%02d:%02d"`-shaped strings (`FocusScreen.kt:150–152`, :183), so this
  is safe *today* — but any translated label routed through `FlipClock` would be sliced into
  fixed-width cards. Card sizes are hard-coded (96.dp / 88.dp) and sized for one digit.
- **`AppGlyph.kt:124–140`, `Monogram`** — `label.trim().firstOrNull()` drawn at `size * 0.5f` inside a
  26.dp bordered tile. One kanji fills that tile; one Latin capital sits in a lot of whitespace, and a
  Latin app name gives no more information than its first letter does. This is the fallback for every
  app the four-pass resolver cannot classify.
- **`Tategaki.kt:113–141`** — as above.

### 3. Composed strings

Every one of these is assembled at runtime; word order is not preserved across languages.

- `HomeScreen.kt:191` — `"次の予定、$day、$time、${event.title}"`, four parts.
- `HomeScreen.kt:187` — era + monthDay + dayOfWeek, space-joined.
- `CalendarScreen.kt:110` and `NotificationsScreen.kt:124` — `"${era} ・ ${monthDay}"`.
- `CalendarScreen.kt:175` — `"$label、${count}件"`.
- `CalendarScreen.kt:208` — `"終日 ・ ${monthDay}まで"`: a postposition glued to a formatted date.
- `CalendarScreen.kt:215, :222, :226` — three separate `joinToString(" ・ ")` / `joinToString("、")`
  compositions building the card detail, source line and `contentDescription`.
- `CalendarFeedback.kt:305, :307` — the write-confirmation span.
- `EventComposeScreen.kt:327` — `"$date ・ ${clock}"`; :462 — `"$label、$value"`.
- `NotificationsScreen.kt:206–209, :410, :438, :486` — row description, group header, 他 N 件, N 件を消去.
- `SearchScreen.kt:226–228` — `"更新 " + date` then `listOfNotNull(category, date).joinToString(" · ")`.
- `FilterScreen.kt:110` — `"あと${remainingLabel(remaining)}"`.
- `BlockConfirmDialog.kt:55–56` and `BlockedInfoDialog.kt:57, :103` — the two block dialogs, both
  wrapping user data in 「」 corner brackets.

Note the two distinct separators in play: `" ・ "` (U+30FB) as a field separator and `"、"` (U+3001)
as a list comma. Both are Japanese punctuation and both need a per-language equivalent, so they are
part of the formatter layer rather than being embedded in each string.

### 4. Counters and numerals

- 件 — `CalendarScreen.kt:175`, `NotificationsScreen.kt:410, :438, :486`.
- 日 / 時間 — `FilterScreen.kt:158`, `BlockedInfoDialog.kt:103`.
- 日間 — `BlockConfirmDialog.kt:48, :85`.
- `FilterScreen.kt:62` hard-codes **10** inside `非表示にすると10日間は解除できません` while the dialogs
  interpolate `BlockadeRepository.BLOCK_DAYS`. A translation pass that keeps the literal number in the
  subtitle re-introduces the drift; make it a formatted key.
- All arabic numerals here reach the user through `%s`-style interpolation, but `JapaneseDate` (out of
  scope, `data/JapaneseDate.kt`) emits **kanji numerals** for `era`, `monthDay`, `dayOfWeek`,
  `reading`, `dayToken`, `dayHeading`, and those feed nine call sites across this scope. The
  arabic-vs-kanji rule is decided in that file, not here.

### 5. Pluralisation

Every counter above is count-bearing and has no plural form today: `他 3 件` and `他 1 件` are the same
string shape. English needs "1 more" / "3 more", "1 notification cleared" / "3 notifications cleared",
"1 day" / "6 days", "1 hour" / "5 hours". Affected: `NotificationsScreen.kt:438, :486, :410`,
`CalendarScreen.kt:175`, `FilterScreen.kt:158`, `BlockedInfoDialog.kt:103`, `BlockConfirmDialog.kt:48`.

### 6. Layout that assumes short strings

- **Vertical corner budget** — `HomeScreen.kt:279`, `heightIn(max = 150.dp)` on the tategaki title,
  with `maxCells = 8` as the second cap. Home's corner sits at `padding(top = 44.dp, end = 30.dp)`
  beside a 96.dp rule (:240) and above a 104.sp clock; there is no horizontal room to grow into.
- **Fixed clock cards** — `FocusScreen.kt:158–161, :187–189` pass `cardWidth`/`cardHeight` of
  96×144.dp and 88×132.dp to `FlipClock`.
- **`maxLines = 1` with ellipsis** — `CalendarScreen.kt:260` (event title), :286 (source line),
  `NotificationsScreen.kt:273` (notification title), `TempoWheel.kt:286` (every wheel row).
  `maxLines = 2` at `CalendarScreen.kt:279`, `maxLines = 3` at `NotificationsScreen.kt:285`.
- **Fixed wheel geometry** — `TempoWheel.kt:43–44`: `WHEEL_ROW = 44` dp rows, and the hour/minute
  columns are pinned at `WheelWidth.Fixed(58.dp)` (:224, :232), sized for two digits.
- **Large letter-spacing on short labels** — `letterSpacing` of 6.sp on `けんさく` / `ひひょうじ` /
  `始める`, 4.sp on the empty states, 3.sp on page titles. Latin text at 6.sp tracking is visibly
  wrong; the tracking is a Japanese-typesetting decision that travels with the string, not the style.
- **Focus's three-control row** — `FocusScreen.kt:201–209`, three labels with 28.dp spacing, unwrapped,
  in a locked-landscape window.
- **`FilterScreen.kt:53` / `SearchScreen.kt:114`** — the kana super-titles `ひひょうじ` and `けんさく`
  are a *typographic device* (a hiragana gloss above the kanji title) with no English equivalent;
  translating them literally produces "search" printed above "Search".

### 7. Fonts

`ui/theme/Type.kt` binds two bundled Japanese OFL families and nothing else: `Mincho` = Shippori
Mincho (Regular + Medium), `Gothic` = Zen Kaku Gothic New (Light + Regular). **Every `Text` in this
scope names one of them explicitly** — there is no unstyled text anywhere in the launcher UI. Both
families do carry Latin glyphs, so nothing will tofu, but their Latin is a secondary design in a CJK
face: the metrics, the x-height and the tracking are tuned for a square em. The 104.sp Home clock, the
seal, and the wheel all sit on those metrics. A Latin-language build needs an explicit decision about
whether these families still apply, and that decision has to be reachable — right now there is no
seam, only 200-odd `fontFamily = Mincho` sites.

### 8. Sorting, filtering and matching

- `SearchScreen.kt:91` — the drawer filters on
  `it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)`. Plain
  substring matching with no kana folding and no locale-aware casing (`ignoreCase` uses the default
  locale, which is the Turkish-İ class of bug). Note `gym/KanaFoldingTest.kt` exists, so the gym has a
  folding notion this screen does not use.
- `AppGlyph.kt:275–347`, `LABEL_KEYWORDS` — the third resolution pass matches **Japanese words against
  the app's display label**, which is itself locale-dependent (the same app is 設定 or "Settings"
  depending on device language). The English half of the same table already covers the other case, so
  today it degrades quietly to the monogram. The hazard is that this table is *language-detection
  logic disguised as a lookup table*, and a language toggle inside Tempo does not change what the
  platform reports as an app's label — so the table must stay bilingual regardless of Tempo's setting.

### 9. Speech / TTS

None in this scope. No `Locale.JAPANESE`, no `TextToSpeech`, no spoken cues under `ui/` outside
`ui/gym/`. The `cue.*` root does not apply here.

### 10. Persistence

No Japanese string in this scope is written to DataStore or a database. Everything persisted from
here is structural: theme enum, screen enum, onboarding-complete flag, package names and unlock
timestamps in the blockade store, and the calendar provider's own rows. Enum *labels* are a soft case
worth naming: `FocusScreen.kt:56–60` puts the copy on the enum constant
(`Focus("集中", FOCUS_SEC)`), so `PomodoroPhase.label` is a string field on a persisted-shaped type
even though this particular enum is only `rememberSaveable`-held. Move the label out of the enum
rather than translating it in place.

### 11. Accessibility strings — user-visible but never drawn

30 of the 144 literals are `contentDescription`, `onClickLabel`, `onLongClickLabel`, `stateDescription`
or `CustomAccessibilityAction` labels. They are easy to miss in a translation sweep because they never
appear in a screenshot, and three of them are the **only** naming of their control:

- `Dock.kt:81` `Tempoを既定のホームに設定` — the long-press gesture has no visual affordance at all.
- `Dock.kt:91–94` — all four dock tabs are icon-only.
- `NotificationsScreen.kt:212` `消去` — the swipe-to-dismiss gesture is otherwise invisible to TalkBack.

Also note several screens use `clearAndSetSemantics` to collapse a subtree into one announcement
(`HomeScreen.kt:211`, `CalendarScreen.kt:175, :236`, `NotificationsScreen.kt:247, :410`,
`CycleDots.kt:68`). Those single announcements are the composed strings from hazard 3, so they carry
the composition problem into the accessibility layer too.

### 12. Tests asserting Japanese literals

Two test files in this scope's package break on migration.

- `app/src/test/java/io/eddiegulay/tempo/ui/CalendarFeedbackTest.kt` — **13 assertions on exact
  Japanese strings**: 許可する (:67), アカウントを追加 (:72), もう一度 (:79, :98–101, :138),
  記録を読めません (:86–89, :137, :148), 空き容量が足りません (:90, :111), この型は削除されています
  (:91), この記録は削除されています (:92), 保存できませんでした (:93), （無題） (:195), plus a
  structural assertion that 記録を読めません does **not** contain ありません (:149) and one that a
  timed span contains `09:30 – 10:00` (:165). The ありません assertion is semantic, not textual — it
  encodes "an unreadable store must never read as an empty one", which must be restated per language
  rather than deleted.
- `app/src/test/java/io/eddiegulay/tempo/ui/TategakiTest.kt` — **11 tests**, all with Japanese
  fixtures (会議, 会議 Standup, 第2会議室, 2026年, 一二三四五六七八九十, 一二三四五六七八, `  会議　`
  including a full-width space). These test the segmentation *rule*, not copy, so they survive a
  string migration — but they do not survive a decision to stop rendering tategaki in English.

`app/src/test/java/io/eddiegulay/tempo/ui/EnsoTest.kt` and `CycleDotsTest.kt` assert numbers only and
are unaffected. There are no instrumented tests touching this scope
(`androidTest/` holds only `ExampleInstrumentedTest.kt`).

---

## Non-visible Japanese

21 literals, all in `AppGlyph.kt:277–297` — the Japanese half of `LABEL_KEYWORDS`, a lowercased
substring-match table from an installed app's display name to a line glyph. They are matching keys,
never rendered:

電話, 連絡先, メッセージ, カメラ, 写真, 音楽, 天気, 地図, 時計, 電卓, 設定, 翻訳, 銀行, 財布, 地下鉄,
電車, 天気 (duplicate, :293), ニュース, 読書, ゲーム, 翻訳 (duplicate, :297)

They stay Japanese because they match against **the platform's** app labels, not Tempo's UI language:
a device in Japanese reports 設定, and it must keep resolving to the settings glyph no matter which
language the user has chosen inside Tempo. They must not be moved into the translation table — doing
so would make glyph resolution follow Tempo's toggle instead of the device's locale and silently break
icons for bilingual users. Two entries are exact duplicates and are dead (天気 :293 after :283, 翻訳
:297 after :288); worth deleting, but that is a cleanup, not an i18n change.

No other non-visible Japanese exists in this scope: no Japanese log tags, no Japanese DataStore or DB
keys, no Japanese enum storage values. The Japanese in KDoc comments across all 25 files is
documentation and is out of the migration's path.
