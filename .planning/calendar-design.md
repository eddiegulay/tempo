# Tempo — Calendar (予定) design spec

Design-only document. No code in `app/` is touched by this file.

Everything below names the actual token (`c.ink`, `c.inkSoft`, `c.inkFaint`, `c.card`, `c.hair`,
`c.accent`, `c.enso`), the actual font (`Mincho` / `Gothic`), and concrete `sp` / `dp`, matched to the
density already used in `NotificationsScreen.kt` and `HomeScreen.kt`.

---

## 0. Scope & data source

- Read events from the device provider: `CalendarContract.Instances` (not `Events`) over a window of
  `now → now + 14 days`, so recurring series are already expanded into instances by the provider. This
  is the same store Google Calendar syncs into, so it is the same calendar the user's laptop shows.
- Permissions: `READ_CALENDAR`, `WRITE_CALENDAR` (runtime, requested from the page — **not** at launch).
- Writes: insert/update/delete on `CalendarContract.Events`. No account plumbing, no sync adapter.
- New `Screen` values: `Screen.Calendar`, `Screen.EventCompose`.

Two hard product rules that shape the whole spec:

1. **The Calendar page is not in the dock.** The only way in is tapping the Home top-right cluster.
2. **Tempo never silently mutates a recurring series.** Recurring instances are view-only in-app; they
   hand off to the system calendar app. (§4.3)

---

## 1. Home widget — the top-right cluster

### 1.1 What replaces what

Today `VerticalDate` renders three vertical-rl kanji columns at `TopEnd`, padded `top = 44.dp,
end = 30.dp`, columns spaced `13.dp`, each glyph `Mincho 19.sp` with `3.dp` inter-glyph spacing:

| position (visual) | content | token |
|---|---|---|
| left | `水曜日` | `c.inkFaint` |
| middle | `六月十七日` | `c.inkSoft` |
| right | `令和八年` | `c.inkSoft` |

**The date is replaced, not kept alongside.** Home has exactly one ambient text cluster in that
corner and it must stay a single quiet object — stacking event *and* date would make six columns and
turn the corner into a paragraph. The date does not disappear from the app: it still heads the
Notifications page and now the Calendar page (`令和八年 ・ 六月十七日`), and it comes straight back on
Home in every degraded state (§1.5). The date is the **fallback**; the next event is the **default**.

### 1.2 Composition (event state)

Same anchor, same geometry, three columns again — the silhouette of the corner is preserved on
purpose, so the change reads as "the same object now says something else", not as a new widget.

Vertical-rl means **the rightmost column is read first**. Columns, right → left:

```
                       ┌ hairline rule, 1dp × column height, c.hair, 10dp to the right of col 1
   S                   │
   t   九   今         │     read order: 今日 → 十九時三十分 → Standup
   a   時   日         │
   n   三             │
   d   十             │
   u   分             │
   p                  │
   ▏col3  col2  col1  │
   title  time  day   │
```

| col | content | font | size | letter-spacing | token |
|---|---|---|---|---|---|
| 1 (rightmost, read first) | day token — `今日` / `明日` / `水曜日` / `六月十九日` | Mincho | 15.sp | — | `c.inkFaint` |
| 2 | time — `十九時三十分`, or `終日` | Mincho | 17.sp | — | `c.inkSoft` |
| 3 (leftmost) | event title | Mincho Medium | 19.sp | — | `c.ink` |

- Column gap: `13.dp` (unchanged). Glyph gap within a column: `3.dp` (unchanged). Row is `Alignment.Top`.
- Right → left reading gives a grammatical Japanese phrase: **今日 十九時三十分 Standup**. This is also
  why the day is rightmost and the title leftmost: short tokens hug the screen edge, and the one column
  that can grow long (the title) hangs into open space toward the ensō, where there is room.
- The title is the only `c.ink` element in the cluster — it is the new information. It is still 19sp
  against a 104sp clock, so Home's hierarchy is untouched.
- **Imminent dot:** if the event starts within 30 minutes, a `4.dp` circle in `c.accent` sits `6.dp`
  above the first glyph of the title column. Static — no pulse, no animation. It is the only vermillion
  on Home besides the 静 seal, and it earns its place.

### 1.3 Vertical rendering of Latin text — **recommendation**

**Recommended: segmented hybrid — CJK/kana/short-digit runs upright, Latin word-runs rotated 90° clockwise.**

This is exactly what a Japanese typesetter does in *tategaki*: ideographs and kana stack upright; a
run of Latin (a word, an acronym, a product name) is set rotated a quarter-turn clockwise so the
reader tilts their head right; and a short numeral run (1–2 digits) is set upright as a unit
(*tate-chū-yoko*, 縦中横).

Algorithm for the title column:

1. Segment the string into runs: **CJK/kana** (incl. `、。・ー`), **Latin/Latin-punctuation**, **digits**.
2. CJK/kana run → one glyph per line, upright. (`Text(ch)` per char, as `VerticalLine` does today.)
3. Digit run of length ≤ 2 → upright, rendered as a **single** `Text` cell (`09`, `1:1`) —
   *tate-chū-yoko*. Longer digit runs join the Latin path.
4. Latin run → one `Text` for the whole run with `Modifier.rotate(90f)`, `maxLines = 1`,
   `TextOverflow.Ellipsis`, laid out with `layout {}` so its rendered *width* becomes the column's
   *height* contribution. Font stays `Mincho` — Shippori Mincho's Latin is a proper old-style serif and
   reads beautifully rotated.
5. Runs are stacked top→bottom in source order with the same `3.dp` gap.

Result for `"Standup"`: one rotated word, a clean 90° stroke of serif Latin — the "landscape-y"
effect the brief asks for. For `"1:1 with Mei"`: `1:1` upright, `with` rotated, `Mei` rotated —
a mixed rhythm that is *more* interesting than either pure approach. For `"チーム会議"`: five upright
kanji, indistinguishable from the date it replaced.

**Why not the alternatives:**

- *Rotate the whole string 90°.* Kills the effect for CJK titles (a sideways `会議` is simply wrong,
  and Japanese readers register it as broken) and destroys the visual kinship with the two upright
  columns beside it. It also makes the cluster read as a rotated label rather than as vertical text.
- *Stack every character upright, one per line.* `S / t / a / n / d / u / p` is a ransom note. Latin
  letterforms have no vertical center of gravity, the column becomes 7 cells tall for a 7-letter word,
  and lowercase ascenders/descenders make the rhythm ragged. It is the classic failure mode.
- The hybrid is the only option that is simultaneously typographically correct, compact for Latin,
  perfect for CJK, and — because rotated runs are a single `Text` — gives free ellipsis truncation.

### 1.4 Time format, sizing, truncation

- **Time is kanji, not digits: `十九時三十分`.** 24-hour, so no `午前` / `午後` prefix is needed (that
  would cost two glyphs and add nothing — the user knows whether it is morning). On the hour, drop the
  minutes: `十九時`. All-day: `終日`. Built from the existing `JapaneseDate.kanji(n)`.
  Rationale: the big clock already owns digits on Home. Reintroducing `19:30` in the corner would
  create a second competing numeral cluster four inches away. The corner is the *kanji artefact*; it
  stays kanji. (The Calendar **page** uses digits — see §2.4 — because that surface is a tool, not an
  artefact.)
- **Day token** collapses aggressively: today → `今日`; tomorrow → `明日`; within 7 days → `水曜日`;
  beyond → `六月十九日`.
- **Column height budget: 150.dp** for the title column (roughly the height of 7 upright glyphs, and
  slightly shorter than the 190dp at which the clock begins — the cluster must never visually collide
  with the clock's top line).
  - Upright runs: hard cap at **8 cells**; if the string still has content past cell 8, drop the
    remainder and render a final upright `…` cell in `c.inkFaint`.
  - A rotated Latin run gets a max width equal to the *remaining* height budget and truncates itself
    with `TextOverflow.Ellipsis`.
- **One event only.** Never a list. If two events start in the same minute, show the one from the
  first calendar alphabetically and move on. This is a launcher, not an agenda widget.

### 1.5 Degraded states (must be graceful)

| condition | Home renders |
|---|---|
| permission not granted | **the existing `VerticalDate`, exactly as today.** No prompt, no badge, no nag. Tapping still opens the Calendar page, which shows the permission gate. |
| permission granted, no event in the next 14 days | the existing `VerticalDate`. |
| permission granted, event exists | the event cluster (§1.2). |
| provider query in flight (first frame) | the existing `VerticalDate` — the date is the resting state, the event fades in over it when resolved (§5). Never a spinner, never a blank corner. |

The invariant: **the corner is never empty and never nags.** The date is the floor.

### 1.6 Tap target & affordance

- Wrap the cluster in a `Box` with `.padding(top = 44.dp, end = 30.dp)` preserved, plus an internal
  expansion so the hit rect is at least `56.dp` wide and `96.dp` tall and extends `12.dp` past the
  glyphs on every side.
- **No ripple.** Use `pointerInput { detectTapGestures(onTap = …) }`, exactly as the Home clock does.
  A ripple in this corner would be the loudest thing on the screen.
- Press feedback: `animateFloatAsState` scale `1f → 0.97f`, `tween(120, LinearOutSlowInEasing)` on press,
  `tween(180)` back on release. Same restraint as everything else.
- **Static affordance:** a `1.dp` × column-height vertical hairline in `c.hair`, sitting `10.dp` to the
  right of the rightmost column (i.e. between the cluster and the screen edge). It reads as a printed
  page's ruled margin, it is present in *both* the date and the event state (so nothing appears or
  disappears), and it is the one visual hint that this corner is an object you can touch. It is the
  same weight as the hairline under the search field and the reply field — already part of the language.

### 1.7 Accessibility

`clearAndSetSemantics` on the cluster (the per-glyph `Text`s must not each be a TalkBack node — a
column of single characters is unreadable to a screen reader):

- event state: `contentDescription = "次の予定、今日 十九時三十分、Standup"`
  (`"次の予定、${dayToken}、${timeReading}、${title}"`, using `JapaneseDate.reading`-style kanji time so
  TTS speaks it naturally)
- date state: `contentDescription = "令和八年 六月十七日 水曜日"`
- both states: `onClick(label = "予定")`, `role = Role.Button`

---

## 2. Calendar page (予定)

### 2.1 Title: **予定** (yotei), not 暦

`暦` means *almanac / the calendar system* — months, dates, seasons. This page is not a month grid; it
is a list of *upcoming appointments*. `予定` is the exact word, it is a two-kanji noun with the same
rhythm and letter-spacing as `通知`, and it lets the corner keep its identity: Home already *is* the
暦 (the era, the month-day, the day-of-week are the almanac). The page is what you have *planned*.

### 2.2 Header — identical structure to `NotificationsScreen`

`Row(fillMaxWidth, padding(start = 28, end = 22, top = 24, bottom = 10), SpaceBetween, Alignment.Top)`

- Left `Column`:
  - `予定` — Mincho, `26.sp`, `letterSpacing 3.sp`, `c.ink`
  - `Spacer(7.dp)`
  - `令和八年 ・ 六月十七日` — Mincho, `13.sp`, `letterSpacing 4.sp`, `c.inkFaint`
    (`"${JapaneseDate.era(now)} ・ ${JapaneseDate.monthDay(now)}"` — verbatim from Notifications)
- Right (only when permission is granted): **加える** — Mincho, `13.sp`, `letterSpacing 2.sp`,
  `c.accent`, in a `sizeIn(minWidth = 48.dp, minHeight = 48.dp)` box, `role = Role.Button`,
  `contentDescription = "予定を加える"`. Accent (not `inkFaint` like すべて消去) because this is the page's
  one creative action.

### 2.3 Grouping & scroll

- `LazyColumn(fillMaxSize().padding(horizontal = 22.dp, vertical = 6.dp))`, bottom
  `contentPadding = 96.dp` so the last card clears the floating dock.
- Window: **now → +14 days**, cap 60 instances. Ongoing events (started, not ended) sort first, under 今日.
- **Day-grouped washi cards**, not a timeline. A timeline rail implies duration and gaps — it would fill
  the page with empty vertical distance, which is the opposite of calm. Groups are exactly the
  Notifications `GroupHeader` idiom, so the two pages feel like siblings.
- **Group header** — `Row(padding(start = 18, end = 18, top = 18, bottom = 6))`,
  `clearAndSetSemantics { contentDescription = "${dayLabel}、${n}件" }`:
  - day label — Mincho, `12.sp`, `letterSpacing 3.sp`, `c.inkFaint`.
    `今日` / `明日` / `水曜日 ・ 六月十九日` (weekday + kanji date beyond tomorrow)
  - count — Gothic, `11.sp`, `letterSpacing 1.sp`, `c.inkFaint`
  - Non-sticky, exactly like Notifications. Do not add sticky headers; the list is short.
- No month view. No week view. No "+" FAB. Adding is the header word.

### 2.4 Event card

Mirrors `NotifRow` geometry exactly, so an engineer can copy the shape:

```
Column
  .fillMaxWidth()
  .padding(vertical = 5.dp)
  .clip(RoundedCornerShape(18.dp))
  .background(c.card)
```
inner `Row(padding(horizontal = 18.dp, vertical = 16.dp), spacedBy(16.dp))`, `.clickable(onClick = onEdit)`
(default ripple is fine here — `NotifRow` keeps it; the ban on ripple is a Home-surface rule).

- **Leading 20.dp slot** (the same slot Notifications gives the app icon): a `6.dp` circle in the
  calendar's `CALENDAR_COLOR`, `alpha = 0.75f` so it sinks into the washi instead of glowing.
  Centered in the slot, `top padding 6.dp` to sit on the title's optical baseline.
  If only one calendar exists on the device, still draw the dot — it is the page's only color and it
  reads as a seal-dot, not as a legend.
- **Column** (`spacedBy(4.dp)`):
  1. `Row(spacedBy(10.dp), Alignment.Top)`
     - title — Mincho, `16.sp`, `c.ink`, `maxLines = 1`, `Ellipsis`, `weight(1f)`
     - start time — Gothic, `12.sp`, `c.inkFaint`, `09:30` (or `終日`)
  2. detail line — Gothic, `13.sp`, `lineHeight 19.5.sp`, `c.inkSoft`, `maxLines = 2`, `Ellipsis`:
     `09:30 – 10:00` · location appended with ` ・ ` when present → `09:30 – 10:00 ・ 会議室 A`.
     All-day: `終日` (or `終日 ・ 六月十九日まで` for multi-day).
     Omit the line entirely when it would only repeat the trailing time (never happens in practice —
     the end time is always additional information).
  3. calendar display name — Mincho, `11.sp`, `letterSpacing 3.sp`, `c.inkFaint` (the `appLabel` slot)
- **Ongoing event** (now is between start and end): the leading dot becomes `c.accent` at full alpha and
  the trailing time is replaced by `いま` in Mincho `12.sp` `c.accent`. Nothing else changes — no border,
  no fill change.
- **Recurring instance:** a `10.sp` Mincho `c.inkFaint` `⟳`-less marker — literally the character `繰`
  is too heavy; instead render the calendar name line as `カレンダー名 ・ 繰り返し`. Cheap, quiet, and it
  sets the expectation before the user taps into a read-only composer (§4.3).
- Card horizontal padding on the page is `22.dp` (LazyColumn) so cards are inset the same as notifications.

### 2.5 No swipe-to-dismiss

`NotificationsScreen` swipes to dismiss because dismissing a notification is local and reversible
(hence `UndoStrip`). Deleting a calendar event is a **remote, syncing, other-people-see-it** mutation.
A stray thumb must not be able to cancel a meeting. **Deletion lives only inside the composer, behind a
two-step confirmation** (§4.2). This is a deliberate divergence from the sibling page and should not be
"fixed" later.

### 2.6 Permission gate

Identical composable shape to `EnableAccessPrompt` — `Box(fillMaxSize().padding(40.dp), Center)`,
`Column(CenterHorizontally, spacedBy(14.dp))`:

- `予定へのアクセス` — Mincho, `18.sp`, `letterSpacing 4.sp`, `c.inkSoft`
- `タップして許可` — Mincho, `15.sp`, `letterSpacing 3.sp`, `c.accent`, `.clickable`

Tap → `ActivityResultContracts.RequestMultiplePermissions` for `READ_CALENDAR` + `WRITE_CALENDAR`.
If the system reports "don't ask again" (`!shouldShowRequestPermissionRationale` after a denial), the
same tap instead opens `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`. Re-check on `ON_RESUME` with a
`LifecycleEventObserver`, exactly as `NotificationsScreen` re-checks listener access. The 加える control
is hidden in this state.

### 2.7 Empty state

`Box(fillMaxSize().padding(40.dp), Center)`:

- `予定はありません` — Mincho, `17.sp`, `letterSpacing 4.sp`, `c.inkFaint`

No illustration, no "add your first event" CTA. 加える is already in the header.

### 2.8 Dock behaviour on this page

The dock still renders (`frosted = true`, as on every sub-screen) with **no tab active** — Calendar has
no dock button by design. Tapping ホーム returns. `BackHandler` already routes any non-Home, non-Filter
screen to Home, so Calendar needs no new back rule.

---

## 3. Add-event flow (予定を加える)

### 3.1 It is a screen, not a sheet

Add `Screen.EventCompose`. It renders **inside the existing `AnimatedContent`**, so it inherits the
260ms `fadeIn` + `scaleIn(0.97f)` for free, and it is dismissed by Back like every other sub-screen.

A Material `ModalBottomSheet` is rejected: it drags in a scrim, a drag handle, Material's own
elevation/shape system and its own motion curve — four pieces of foreign chrome. A full screen also
gives the composer room to breathe, which is the point of the aesthetic.

**The dock is hidden on `EventCompose`** (`if (screen != Screen.EventCompose) Dock(...)`). The composer
is a committed task; navigating away mid-write should be a deliberate Back, not a stray tab tap.

### 3.2 Header

Same `Row` geometry as every other page header (`start = 28, end = 22, top = 24, bottom = 10`):

- Left: `予定を加える` (or `予定を編集` in edit mode) — Mincho, `26.sp`, `letterSpacing 3.sp`, `c.ink`
- Right: `Row(spacedBy(20.dp), CenterVertically)`, each in a `sizeIn(minWidth = 48.dp, minHeight = 48.dp)` box:
  - `やめる` — Mincho, `13.sp`, `letterSpacing 2.sp`, `c.inkFaint`
  - `保存` — Mincho, `13.sp`, `letterSpacing 2.sp`, `c.accent` when the title is non-blank,
    `c.inkFaint` when disabled

### 3.3 Fields — five, and no more

Body: `Column(padding(horizontal = 26.dp), spacedBy(0.dp))`, fields separated by a `1.dp` `c.hair` rule
that runs the full content width. Each field row has a `48.dp` minimum height. Vertical padding
`14.dp` per row.

1. **題名** — `BasicTextField`, Mincho `20.sp` `c.ink`, `cursorBrush = SolidColor(c.accent)`,
   `singleLine`, placeholder `題名` in Mincho `20.sp` `c.inkFaint`, hairline underline (`1.dp`, `c.hair`).
   Autofocused on add (`FocusRequester`, as `ReplyField` does); **not** autofocused on edit.
   `imePadding()` on the root, as `SearchScreen` does.
2. **終日** — label `終日` Mincho `15.sp` `c.inkSoft` on the left; on the right a two-state word:
   `する` (Mincho `14.sp`, `c.accent`) / `しない` (Mincho `14.sp`, `c.inkFaint`). Tap the row to toggle.
   `role = Role.Switch`, `stateDescription`. **No Material `Switch`** — it is the single most
   recognisably-Material widget there is.
3. **開始** — label `開始` Mincho `13.sp` `letterSpacing 3.sp` `c.inkFaint` on the left; value on the
   right, Mincho `18.sp` `c.ink`: `六月十七日 ・ 09:30` (kanji date, digit time — see below). Tapping the
   row expands the inline picker (§3.4) beneath it and collapses 終了's picker if open.
4. **終了** — same as 開始. Auto-tracks 開始 `+1h` until the user touches it once (then it is pinned;
   if 開始 is later moved past 終了, 終了 is shunted forward preserving duration). When 終日 is on, both
   rows show only the kanji date and the time part is dropped.
5. **カレンダー** — rendered **only if the device has more than one writable calendar**. A `FlowRow`
   (`spacedBy(20.dp)`) of name-chips: `6.dp` colored dot + name in Mincho `14.sp`; selected is `c.ink`,
   unselected is `c.inkFaint`. Tap to select. No dropdown, no menu. Defaults to the account's primary
   calendar (`IS_PRIMARY`, falling back to the first `CALENDAR_ACCESS_LEVEL >= CAL_ACCESS_CONTRIBUTOR`).

Then **場所** — `BasicTextField`, Gothic `15.sp` `c.ink`, placeholder `場所` Gothic `15.sp` `c.inkFaint`,
hairline underline. (Listed last in the layout: it is optional and rarely filled.)

**Explicitly out of scope:** description/notes, guests, reminders/alerts, recurrence, availability,
visibility, attachments, timezone. Every one of them is available in the real calendar app. Tempo's
composer exists to capture a thing you just agreed to in ten seconds. If a user needs the rest,
`カレンダーで開く` (§4.3) is the pressure valve.

### 3.4 The picker — a "shiori" snap-wheel

Material's `TimePicker` / `DatePicker` are absolutely not usable here: they bring Material's own type
scale, its tonal surfaces, its clock dial, and its dialog chrome. Nothing in Tempo looks like that.

Build one component, `TempoWheel`, and use it for both 開始 and 終了.

- **Shape:** an inline panel, `132.dp` tall (three `44.dp` rows), expanding beneath its field row with
  `animateContentSize(tween(220, LinearOutSlowInEasing))`. Not a dialog; not an overlay.
- **Columns:** `日` / `時` / `分` — three `LazyColumn`s side by side, `Arrangement.spacedBy(24.dp)`,
  each with `rememberSnapFlingBehavior(rememberLazyListState())` and `contentPadding` of `44.dp`
  top/bottom so the selected row sits centered.
  - 日 column: 14 entries — `今日`, `明日`, then `六月十九日(金)` … Mincho.
  - 時 column: `00`–`23`. Mincho (its digits are the same ones that draw the 104sp clock).
  - 分 column: `00, 05, 10 … 55` — **five-minute granularity**. A launcher does not need 12:37. It
    halves the column length and removes a whole class of fiddly scrolling.
  - Hidden when 終日 is on (the 時/分 columns collapse out; the 日 column stays).
- **Selection band:** the centre `44.dp` row is bracketed by two full-width `1.dp` `c.hair` rules — a
  paper bookmark (栞) laid across the column. The centered item renders Mincho `22.sp` `c.ink`; the
  rows above/below render Mincho `16.sp` `c.inkFaint`. Nothing else: no highlight fill, no chevrons.
- **Feel:** one `HapticFeedbackType.TextHandleMove` tick per snapped item change. The wheel is the only
  place in Tempo where a value is dialed, and the tick is what makes it feel like paper detents rather
  than a dropdown.
- **Never opens a keyboard.** Both pickers can be open at once? No — opening one collapses the other.

### 3.5 Save

`保存` is enabled iff the title is non-blank. On tap: insert into `CalendarContract.Events`
(`DTSTART`, `DTEND`, `TITLE`, `EVENT_LOCATION`, `ALL_DAY`, `CALENDAR_ID`, `EVENT_TIMEZONE`), then
`goCalendar()`. The list re-queries on resume, and the new card fades in (§5). No toast, no snackbar.

---

## 4. Edit / update / delete

### 4.1 Edit

Tapping an event card opens the **same** `EventCompose` screen, prefilled, header title `予定を編集`,
title field not autofocused, `保存` writes an `update()` against the event `_ID`. Back or `やめる`
discards. No dirty-state dialog — a minimalist launcher does not interrogate you on the way out.

### 4.2 Delete

Only present in edit mode. A lone centered word beneath the last field, with `32.dp` of space above it:

- `削除` — Mincho, `14.sp`, `letterSpacing 2.sp`, `c.accent`, in a `48.dp`-min-height full-width
  `Box(contentAlignment = Center)`, `role = Role.Button`.

Tapping it does **not** delete. It swaps the row in place (`AnimatedContent`, `fadeIn(160)`) for a
confirmation row that mirrors the `UndoStrip` idiom — `Row(fillMaxWidth, SpaceBetween,
padding(horizontal = 6.dp, vertical = 14.dp))`:

- `この予定を削除しますか` — Mincho, `14.sp`, `letterSpacing 2.sp`, `c.inkSoft`
- `Row(spacedBy(20.dp))`: `やめる` (Mincho `14.sp` `c.inkFaint`) · `削除する` (Mincho `14.sp` `c.accent`)

The confirmation row reverts to the plain `削除` word after **5 seconds** of no interaction. Two taps,
no `AlertDialog`, no scrim, no Material buttons. `削除する` deletes and returns to the Calendar page.

### 4.3 Recurring events

An instance whose `Events.RRULE` is non-null (or which has an `ORIGINAL_ID`) is **view-only in Tempo**.

Opening it shows the composer in a read-only skin:

- All fields render with their values but are non-editable: `BasicTextField` → plain `Text` with the
  same style, picker rows are inert (`c.inkSoft` instead of `c.ink`, no expand affordance).
- Header: `保存` is **absent**; `やめる` becomes `とじる`.
- Directly beneath the title, a note — `繰り返しの予定` — Mincho `12.sp`, `letterSpacing 2.sp`, `c.inkFaint`.
- Where 削除 would sit: `カレンダーで開く` — Mincho `14.sp`, `letterSpacing 2.sp`, `c.accent`, centered.
  Fires `Intent(ACTION_VIEW, ContentUris.withAppendedId(Events.CONTENT_URI, eventId))` with
  `EXTRA_EVENT_BEGIN_TIME` / `EXTRA_EVENT_END_TIME` set to the instance's begin/end, so the system
  calendar opens on the *correct instance*.

**Why:** the provider's semantics for editing one instance of a series (write an exception row with
`ORIGINAL_INSTANCE_TIME`) vs. the whole series are subtle, and getting it wrong silently mutates or
deletes a recurring meeting for everyone on the invite. There is no version of "this event / this and
following / all events" that belongs in a minimalist launcher. Hand off. This is the correct, humble
answer, and the `・ 繰り返し` marker on the card (§2.4) means the user is never surprised by it.

Same handoff applies to any event where `CALENDAR_ACCESS_LEVEL < CAL_ACCESS_CONTRIBUTOR` (read-only
calendars: holidays, subscribed feeds, a colleague's shared calendar).

---

## 5. Motion & micro-interactions

Everything is `LinearOutSlowInEasing`. Nothing bounces, nothing overshoots, nothing loops.

| moment | spec |
|---|---|
| Home → Calendar, Calendar → Compose | the existing `AnimatedContent`: `fadeIn(tween(260, delay 40))` + `scaleIn(0.97f, tween(300, delay 40))`, exit `fadeOut(tween(80, FastOutLinearInEasing))`. Nothing new. |
| Home cluster: date → event (first resolve, or next event changes) | `AnimatedContent` keyed on the event id, `fadeIn(tween(400))` `togetherWith` `fadeOut(tween(240))` — **deliberately slower than screen nav.** It is ambient information arriving, not a page turn. It should read like ink soaking into paper, and the user should half-notice it. |
| Home cluster press | scale `0.97f`, `tween(120)`; release `tween(180)`. No ripple. |
| Event card tap | default ripple (matches `NotifRow`). |
| List entry / a saved event appearing | `Modifier.animateItem()` on the LazyColumn item, `tween(260)`. |
| Picker expand / collapse | `animateContentSize(tween(220))`. |
| Picker snap | `HapticFeedbackType.TextHandleMove` per item change. |
| 削除 → confirmation swap | `AnimatedContent`, `fadeIn(160)` / `fadeOut(120)`; auto-revert at 5s. |
| Imminent (<30min) accent dot | **no animation.** Static. Tempo does not pulse at you. |

---

## 6. Token reference (implementer's table)

### Home cluster
| element | font | size | spacing | color |
|---|---|---|---|---|
| day column glyph | Mincho | 15.sp | glyph gap 3.dp | `c.inkFaint` |
| time column glyph | Mincho | 17.sp | glyph gap 3.dp | `c.inkSoft` |
| title column glyph / rotated run | Mincho Medium | 19.sp | glyph gap 3.dp | `c.ink` |
| truncation `…` cell | Mincho | 19.sp | — | `c.inkFaint` |
| imminent dot | — | 4.dp circle | 6.dp above title | `c.accent` |
| margin hairline | — | 1.dp × col height | 10.dp right of col 1 | `c.hair` |
| cluster anchor | — | `TopEnd`, `padding(top = 44.dp, end = 30.dp)`, columns `spacedBy(13.dp)` | | |
| fallback date | *unchanged* — `c.inkFaint` / `c.inkSoft` / `c.inkSoft`, Mincho 19.sp | | | |

### Calendar page
| element | font | size | spacing | color |
|---|---|---|---|---|
| page title `予定` | Mincho | 26.sp | ls 3.sp | `c.ink` |
| date subtitle | Mincho | 13.sp | ls 4.sp | `c.inkFaint` |
| `加える` | Mincho | 13.sp | ls 2.sp | `c.accent` |
| day group label | Mincho | 12.sp | ls 3.sp | `c.inkFaint` |
| day group count | Gothic | 11.sp | ls 1.sp | `c.inkFaint` |
| card | — | `RoundedCornerShape(18.dp)`, `padding(h 18, v 16)`, item `padding(v 5.dp)` | | `c.card` |
| calendar dot | — | 6.dp circle in 20.dp slot | 16.dp gap to text | `CALENDAR_COLOR` @ 0.75α (accent @ 1.0 if ongoing) |
| event title | Mincho | 16.sp | — | `c.ink` |
| trailing time / `いま` | Gothic / Mincho | 12.sp | — | `c.inkFaint` / `c.accent` |
| detail line | Gothic | 13.sp | lh 19.5.sp | `c.inkSoft` |
| calendar name line | Mincho | 11.sp | ls 3.sp | `c.inkFaint` |
| empty state | Mincho | 17.sp | ls 4.sp | `c.inkFaint` |
| permission title | Mincho | 18.sp | ls 4.sp | `c.inkSoft` |
| permission action | Mincho | 15.sp | ls 3.sp | `c.accent` |
| list padding | — | `horizontal 22.dp, vertical 6.dp`, bottom contentPadding 96.dp | | |

### Composer
| element | font | size | spacing | color |
|---|---|---|---|---|
| page title | Mincho | 26.sp | ls 3.sp | `c.ink` |
| `やめる` / `とじる` | Mincho | 13.sp | ls 2.sp | `c.inkFaint` |
| `保存` | Mincho | 13.sp | ls 2.sp | `c.accent` (disabled: `c.inkFaint`) |
| 題名 field + placeholder | Mincho | 20.sp | — | `c.ink` / `c.inkFaint` |
| 場所 field + placeholder | Gothic | 15.sp | — | `c.ink` / `c.inkFaint` |
| cursor | — | — | — | `SolidColor(c.accent)` |
| field rule | — | 1.dp | — | `c.hair` |
| field label (開始/終了/カレンダー) | Mincho | 13.sp | ls 3.sp | `c.inkFaint` |
| field value | Mincho | 18.sp | — | `c.ink` (read-only: `c.inkSoft`) |
| 終日 label / state word | Mincho | 15.sp / 14.sp | — | `c.inkSoft` / `c.accent` or `c.inkFaint` |
| wheel selected item | Mincho | 22.sp | — | `c.ink` |
| wheel unselected item | Mincho | 16.sp | — | `c.inkFaint` |
| wheel band rules | — | 1.dp, rows 44.dp, panel 132.dp | — | `c.hair` |
| `繰り返しの予定` note | Mincho | 12.sp | ls 2.sp | `c.inkFaint` |
| `削除` / `削除する` / `カレンダーで開く` | Mincho | 14.sp | ls 2.sp | `c.accent` |
| delete confirm prompt | Mincho | 14.sp | ls 2.sp | `c.inkSoft` |
| body padding | — | `horizontal 26.dp`, row `vertical 14.dp`, min row height 48.dp | | |

---

## 7. Japanese strings

| purpose | string | notes |
|---|---|---|
| page title | `予定` | Calendar page header |
| add event (header action) | `加える` | contentDescription: `予定を加える` |
| composer title (add) | `予定を加える` | |
| composer title (edit) | `予定を編集` | |
| composer title (read-only) | `予定` | recurring / read-only calendar |
| save | `保存` | |
| cancel | `やめる` | matches `BlockConfirmDialog` |
| close (read-only composer) | `とじる` | |
| delete | `削除` | |
| delete confirm prompt | `この予定を削除しますか` | |
| delete confirm action | `削除する` | |
| empty state | `予定はありません` | mirrors `通知はありません` |
| permission title | `予定へのアクセス` | mirrors `通知へのアクセス` |
| permission action | `タップして許可` | verbatim from Notifications |
| title field label / placeholder | `題名` | |
| location field label / placeholder | `場所` | |
| all-day label | `終日` | also the card/widget value for all-day events |
| all-day on / off | `する` / `しない` | |
| start | `開始` | |
| end | `終了` | |
| calendar picker label | `カレンダー` | |
| recurring note | `繰り返しの予定` | |
| recurring marker on card | `繰り返し` | appended after the calendar name with ` ・ ` |
| open in system calendar | `カレンダーで開く` | |
| ongoing badge | `いま` | |
| today / tomorrow | `今日` / `明日` | day tokens, widget + group headers |
| Home widget a11y prefix | `次の予定` | e.g. `次の予定、今日、十九時三十分、Standup` |
| Home widget tap label | `予定` | `onClick(label = …)` |
| all-day multi-day detail | `終日 ・ 六月十九日まで` | |
