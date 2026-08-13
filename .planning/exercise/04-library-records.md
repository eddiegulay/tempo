# 鍛錬 — Part 4: library, routine authoring, records

Read `00-plan.md` first. Merge decisions in its §2 override anything here — in particular **§2 row 7:
`GYM.SESSION.PREFLIGHT` is deleted and its start-guard and session-insert live in
`GYM.LIBRARY.DETAIL`'s 始める action** (mechanics in `03-player.md` §A).

Owns: `GYM.LIBRARY.*`, `GYM.RECORDS.*`, the Canvas drawing, and the Japanese string tables.

---

## 1. Deletion and archival semantics — stated once, referenced everywhere

**A session never joins to a live routine.** `session.routine_version_id` pins an immutable
`routine_version` (`02-data.md` §A.3), and `session.routine_name` is denormalised for the one case the
FK cannot serve. Every read-only historical surface renders from the pinned version.

1. **Editing a routine never rewrites history.** `saveRoutine` inserts a new version when
   `structural_hash` changes (engine, stations, rests, rounds — *not* name, *not* favourite) and
   repoints `head_version_id`.
2. **Deleting a user routine is `archive`, not delete.** `archived_at` is set; the row leaves the
   library index, よく使う, and the filter list. Its sessions stay in history, stay readable, and keep
   their PRs.
3. **An archived routine's detail page is still reachable** — only from a session, via 型を見る. It
   renders a `削除済み` chip; 始める / 編集 / よく使う are disabled; 写して作る and 元に戻す are enabled.
4. **`完全に削除` (purge) is offered only when `countForRoutine(id) == 0`.** When sessions exist the
   confirm says so in words, rather than offering a destructive choice whose blast radius the user
   cannot see.
5. **Built-ins are never archivable or editable.** Tapping 編集 on a built-in transparently becomes
   写して作る with the name pre-filled `七分間 の写し`.
6. **PRs are per-routine-id.** Scaled tiers (design §9's やさしい) are *separate stored routines*
   sharing a `scaled_from_routine_id`, not runtime-scaled variants — so a やさしい best never
   contaminates the Rx best, and the detail page can show both without a caveat.

---

## 2. Token extensions

Design §11's tables cover the player and the record summary. These are the library/records
equivalents, derived from `CalendarScreen.kt` and design §2 — nothing invented where an idiom applies.

| element | font | size | spacing | colour |
|---|---|---|---|---|
| page header title | Mincho | 26.sp | ls 3.sp | `c.ink` — pad start 28 / end 22 / top 24 / bottom 10 |
| page header subtitle | Mincho | 13.sp | ls 4.sp | `c.inkFaint` — 7.dp below title |
| header action | Mincho | 13.sp | ls 2.sp | `c.accent` primary / `c.inkFaint` secondary, `HeaderAction`, 48.dp |
| section heading | Mincho | 12.sp | ls 3.sp | `c.inkFaint` — pad start 18 / top 18 / bottom 6 |
| routine card | — | `RoundedCornerShape(18.dp)`, `c.card`, pad h 18 / v 16, item pad v 5 | — | list pad h 22 |
| routine name | Mincho | 16.sp | — | `c.ink` |
| routine detail line | Gothic | 13.sp | lh 19.5.sp | `c.inkSoft` |
| routine meta line | Mincho | 11.sp | ls 3.sp | `c.inkFaint` |
| trailing count (十四回) | Gothic | 11.sp | — | `c.inkFaint` |
| tier badge | Mincho | 11.sp | ls 2.sp | `c.inkFaint`; 上級 `c.inkSoft` |
| 出典 provenance | Gothic | 11.sp | ls 1.sp | `c.inkFaint` |
| filter chip | Mincho | 13.sp | ls 1.sp | selected `c.accent` / else `c.inkFaint`, `sizeIn(minHeight = 48.dp)`, pad h 10 |
| search field | Mincho | 18.sp | — | `c.ink`, cursor `c.accent`, 1.dp `c.hair` rule beneath |
| primary page button | Mincho | 20.sp | ls 4.sp | `c.accent` on `c.card`, 64.dp tall, width − 44.dp — the §11 済 row reused verbatim |
| centred action row | Mincho | 14.sp | ls 2.sp | `c.accent`, `minHeight 48.dp` |
| field label (builder) | Mincho | 13.sp | ls 3.sp | `c.inkFaint` |
| field value (builder) | Mincho | 18.sp | — | `c.ink` |
| routine name field | Mincho | 20.sp | — | `c.ink` — `TitleField` verbatim |
| drag handle ⋮⋮ | — | 24.dp glyph in 48.dp target | — | `c.inkFaint`; dragging `c.inkSoft` |
| warning line | Gothic | 12.sp | lh 18.sp | `c.inkFaint` |
| live estimate line | Mincho | 14.sp | ls 2.sp | `c.inkSoft`, above a 1.dp `c.hair` rule |
| month grid dot | — | 4.dp dia, cell 20.dp, 7 cols | — | `c.ink` @ 0.15 / 0.35 / 0.6 / 0.9 |
| month grid today ring | — | r 7.dp, stroke 1.dp | — | `c.hair` |
| weekday letter | Mincho | 10.sp | ls 1.sp | `c.inkFaint` |
| chart canvas | — | 96.dp tall, width − 44.dp | — | see §4 |
| chart bar | — | slot w/n, bar 42% of slot, clamped [3.dp, 10.dp] | — | `c.inkSoft` @0.75; current week `c.accent` |
| chart line | — | stroke 1.5.dp, cap/join Round | — | `c.inkSoft`; last point dot 3.dp `c.accent` |
| chart baseline | — | 1.dp | — | `c.hair` |
| chart caption | Gothic | 11.sp | — | `c.inkFaint` |
| PR value | Mincho | 22.sp | — | `c.ink` |
| PR label | Gothic | 11.sp | ls 1.sp | `c.inkFaint` |
| ladder spine | — | 1.dp vertical | — | `c.hair` |
| ladder rung dot | — | 5.dp | — | `c.inkFaint`; current `c.accent`, reached `c.inkSoft` |
| empty state | Mincho | 17.sp | ls 4.sp | `c.inkFaint` |
| list bottom padding | — | 88.dp with the tab bar, 40.dp without | — | — |

---

## 3. `GYM.LIBRARY.*`

### GYM.LIBRARY.INDEX — 型

**Purpose** — Browse, search and filter every routine the user can start.

**Entered from** — the 型 tab · builder save · detail back.
**Exits to** — `DETAIL` (tap a card) · `BUILDER` (作る, or long-press → 写して作る) · `EXERCISE_INDEX`
(種目を見る) · `GYM.SESSION.PREPARE` (long-press → 始める).

**Back behaviour** — If the search/filter row is open, back closes it **and clears the query** — one
back press, not two. Otherwise back rebases to `GYM.HOME` (the shell rule). Scroll and filters live in
`GymViewModel`, retained across tab switches, discarded on gym exit.

**Tab bar** — visible.

**Data in** — `routines` (a `StateFlow<Loadable<List<RoutineSummary>>>` of list projections, so the
index never inflates full station lists) · `recentUsage(60)` · `usageCounts()` ·
`ExerciseCatalog.byId` for searching over station names.
**Data out** — `setFavourite`. Filter state is view-model-only, never persisted.

```
┌──────────────────────────────────────────────────────────┐
│  型                                        探す    作る   │ ← Mincho 26.sp ls 3.sp c.ink
│  令和八年 ・ 六月十七日                                    │   探す c.inkFaint · 作る c.accent
├── unfolds, animateContentSize 220ms LinearOutSlowIn ─────┤
│  ▏さがす                                                  │ ← BasicTextField Mincho 18.sp c.ink
│  ────────────────────────────────────────────────        │ ← 1.dp c.hair
│  入門 中級 上級 │ 巡回 段階 毎分 完走 時間内 │ 〜五分 …   │ ← horizontalScroll chips, 48.dp tall
├──────────────────────────────────────────────────────────┤
│  よく使う                                                 │
│  ┌────────────────────────────────────────────────────┐  │
│  │ 七分間                                     入門    │  │
│  │ 十二種目 ・ 三十秒 / 十秒 ・ 約七分                 │  │
│  │ 巡回                                     十四回    │  │
│  └────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────┐  │
│  │ シンディ                                   中級    │  │
│  │ 三種目 ・ 二十分 ・ 時間内                          │  │
│  │ 最高 十七巡                              六回      │  │ ← 最高 Mincho 11.sp ls3 c.accent
│  └────────────────────────────────────────────────────┘  │
│  型                 … 七分間 ・ タバタ ・ リーコン・ロン …  │
│  自分の型           … or 型はまだありません                │
│                     種目を見る                            │ ← CenteredAction c.accent
└──────────────────────────────────────────────────────────┘
```

List geometry identical to `CalendarScreen.kt:145-147`, `contentPadding(bottom = 88.dp)`.

**States** — `Loading` (`読み込み中`; 作る disabled) · `Ready` (よく使う omitted below 2 cards — a
one-item "frequently used" is noise) · `Empty` (自分の型 only; built-ins can never be empty, so a
totally empty page is impossible outside Failed) · `Error` (`FaultPanel`, never a fall-through to an
empty list) · `NoMatch` (`該当する型はありません`, filter row stays visible so the user can widen it) ·
`FilteredToNothing` (same plus `絞り込みを外す`) · `MenuOpen` (a `DropdownMenu` anchored to the card,
the `SearchScreen.kt:239` idiom: 始める / 写して作る / よく使うに入れる｜から外す / 削除).

**Edge cases**

1. **Kana search.** スクワット must be found by typing すくわっと *and* by すく. `matchRoutine` folds
   katakana→hiragana on both sides before `contains`. Romaji is not supported (there is no romaji
   index) — say nothing about it, just do not pretend.
2. Search matches the routine name **and** its station names **and** `origin`, so typing 懸垂 surfaces
   シンディ. `origin` is Latin, so ASCII search is case-insensitive.
3. A routine referencing an exercise id the catalogue no longer knows still lists; its detail line
   reads `種目 一件が不明` and 始める is disabled **at the detail page**, not silently broken here.
4. Favouriting a **built-in** is allowed and stores a user-side flag on the routine row.
5. よく使う ranking = `recentUsage(60)` desc, ties by `usageCounts()`, then name; unioned with manual
   favourites; capped at 4. A manually favourited routine always appears even at zero usage.
6. 作る while the store is Failed would produce a save that cannot land — disabled until Ready.
7. A duplicate whose name would collide gets `の写し`, then `の写し二`, `の写し三`.
8. Rotation preserves query, filters and scroll — `rememberLazyListState` hoisted into `GymViewModel`,
   not `remember`.
9. The card estimate comes from the stored `est_duration_sec` projection, never recomputed per frame.

**Accessibility** — Each card is one node: `clearAndSetSemantics { contentDescription = "七分間、入門、
十二種目、約七分、巡回、十四回"; role = Role.Button }`, ordered name → tier → structure → estimate →
engine → count so the first two words disambiguate. **Long-press declares `onLongClick(label = "メニュー")`
and exposes all four menu items as `customActions`** — a screen-reader user must never need the
gesture. Filter chips carry `stateDescription = "選択中"`. Section headings are
`"よく使う、二件"`. Every target ≥ 48.dp; cards are ~86.dp naturally.

**Pure logic** — `foldKana`, `matchRoutine`, `durationBucket`, `derivedTier`, `rankFrequent`,
`uniqueName`, `applyFilters`.

`derivedTier`: `score = meanDifficulty × (1 + estimateMinutes / 20f)`; `< 0.9 → 入門`, `< 1.4 → 中級`,
else `上級`. A stored `tier` on a built-in always wins. Documented as a heuristic in the KDoc, with the
three boundary cases pinned by tests.

---

### GYM.LIBRARY.DETAIL — 型の中身

**Purpose** — Everything about one routine on one page: what it is, where it came from, how you have
done at it, and every action you can take — **including starting it.**

**Entered from** — library index · `GYM.HOME` card · `GYM.RECORDS.SESSION_DETAIL` 型を見る (may resolve
to an **archived** routine) · `GYM.RECORDS.PR` row · `GYM.LIBRARY.BUILDER` after 保存 (pops to here, not
to the index).

**Exits to** — `GYM.SESSION.PREPARE` (始める) · `BUILDER` (編集 / 写して作る) · `EXERCISE_DETAIL` (tap a
station) · `GYM.RECORDS.SESSION_DETAIL` (tap an attempt) · `GYM.RECORDS.HISTORY(routineId)` (すべて見る)
· caller (back, or after a confirmed 削除).

**Back behaviour** — Straight pop, no confirmation (nothing is dirty). If entered from a session detail,
back returns *there* — the stack is honest about the path taken. The chosen `ScalingTier` is remembered
per routine, so re-entering restores it.

**Tab bar** — visible.

**Data in** — `routineDetail(id)` · `scaledTiers(id)` · `attemptsForRoutine(id, 5)` ·
`countForRoutine(id)` · `routineBests()` filtered to this id · `ExerciseCatalog.byId` ·
`resumableSession()` for the start guard.
**Data out** — `setFavourite`, `archiveRoutine`, `restoreRoutine`, `purgeRoutine`, `duplicateRoutine`,
and — on 始める — `startSession` + `setLastTier` (`03-player.md` §A).

```
┌──────────────────────────────────────────────────────────┐
│  シンディ                                          とじる │ ← Mincho 26.sp ls 3.sp c.ink
│  時間内 ・ 中級                                           │ ← Mincho 13.sp ls 4.sp c.inkFaint
├──────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────┐  │
│  │                     始める                          │  │ ← 64.dp, width−44.dp, Mincho 20.sp
│  └────────────────────────────────────────────────────┘  │   ls4, c.accent on c.card
│  やさしい   Rx                                            │ ← tier chips, Mincho 13.sp ls1
│  組み立て                                                 │
│  一   懸垂             五回                    ›         │ ← index Gothic 11.sp c.inkFaint
│  二   腕立て伏せ       十回                    ›         │   name Mincho 15.sp c.ink
│  三   スクワット       十五回                  ›         │   presc Gothic 13.sp c.inkSoft
│  ──────────────────────────────────────────────          │ ← 1.dp c.hair between rows
│  制限時間            二十分                               │ ← read-only PickerRow-shaped rows
│  種目の間の休息       なし                                │
│  巡数                 時間内で                            │
│  約 二十分 ・ 六百回まで                                   │ ← Mincho 14.sp ls2 c.inkSoft
│  出典  CrossFit, 2004-12-29                               │ ← 出典 Mincho 11.sp ls3 c.inkFaint
│  最高                                                     │
│  ┌──────────────┬──────────────┬──────────────┐          │
│  │  十七巡      │  三百四回     │   六回        │          │ ← value Mincho 22.sp c.ink
│  │  最高巡数     │  最高反復     │  やった回数    │          │ ← label Gothic 11.sp c.inkFaint
│  └──────────────┴──────────────┴──────────────┘          │
│  これまで                                     すべて見る  │
│  六月十七日      十七巡      二十分     自己最高          │ ← Gothic 13.sp c.inkSoft;
│  六月十日        十五巡      二十分                       │   自己最高 Mincho 11.sp ls3 c.accent
│                     写して作る                            │ ← CenteredAction rows
│                     よく使うに入れる                       │
│                     編集                                  │   (user routines only)
│                     削除                                  │   (user routines only)
└──────────────────────────────────────────────────────────┘
```

Body is a `Column(verticalScroll).padding(horizontal = 26.dp)` — bounded content, ≤ ~25 station rows —
with `Spacer(96.dp)` at the foot.

**States** — `Loading` (title from the nav arg to avoid a flash) · `Ready` · `Archived` (`削除済み`
chip; 始める / 編集 / よく使う disabled in `c.inkFaint`; 元に戻す replaces 削除) · `BuiltIn` (編集 and
削除 **absent entirely**, not greyed — an action that will never be available is chrome, not
information) · `NoAttempts` (これまで replaced by `まだ やっていません`; 最高 tiles absent) ·
`UnknownExercise` (`不明な種目` row; 始める disabled with `種目が見つからないため 始められません`) ·
`Error` (`FaultPanel`; `GymFault.RoutineGone` **pops** rather than offering a retry that cannot
succeed — the `CalendarFault.EventGone → cancelCompose()` precedent) · `AttemptsError` (routine renders
fully; only the これまで block shows `記録を読めません ・ もう一度`. **A history read failure must not
blank a page the user can otherwise act on.**) · `Starting` (始める becomes a non-interactive 支度) ·
`BlockedByOpenSession` (the resume prompt as a modal; the start proceeds after it resolves) ·
`DeleteConfirm` · `TierSelected` (stations, rests, rounds, estimate, bests and attempts all re-render
for that tier's id).

**Actions**

| Trigger | Precondition | Effect | Persists? | Navigates to |
|---|---|---|---|---|
| Tap 始める | Ready, not archived, no unknown exercise, no live session | compile → `startSession` → `setLastTier` | **yes** — session row | `GYM.SESSION.PREPARE` |
| Tap 始める | a session is already live | present the resume prompt; proceed after it resolves | — | — |
| Tap tier chip | `scaledTiers` non-empty | swaps the rendered routine + bests + attempts | no | — |
| Tap station row | exercise known | — | no | `EXERCISE_DETAIL` |
| Tap attempt row | — | — | no | `GYM.RECORDS.SESSION_DETAIL` |
| Tap すべて見る | ≥1 attempt | — | no | `GYM.RECORDS.HISTORY(routineId)` |
| Tap 写して作る | Ready | `duplicateRoutine(id, uniqueName(…))` | **yes** | `BUILDER(newId)` |
| Tap 編集 | `!builtIn && !archived` | — | no | `BUILDER(id)` |
| Tap よく使う… | not archived | `setFavourite` | **yes** | — |
| Tap 削除 | `!builtIn && !archived` | opens confirm; copy branches on `countForRoutine` | no | — |
| Confirm, count > 0 | — | `archiveRoutine` | **yes** | `LIBRARY.INDEX` |
| Confirm, count == 0 | — | `purgeRoutine` | **yes** | `LIBRARY.INDEX` |
| Tap 元に戻す | archived | `restoreRoutine` | **yes** | — |

Delete confirm copy:
- count > 0 → `「シンディ」を削除しますか` / `これまでの六回の記録は残ります。型だけが一覧から消えます。`
  / `削除` / `やめる`.
- count == 0 → `やった記録はありません。完全に消えます。` / `完全に削除`.

**Edge cases**

1. The routine is archived by another shell state while open → `routineDetail()` emits
   `Failed(RoutineGone)` → pop with no dialog. The flow is the source of truth; the page never caches a
   stale copy.
2. A tier chip whose routine is itself archived is not shown.
3. `origin == null` (all user routines) → the 出典 block is absent, not `出典 —`.
4. AMRAP / FOR_TIME have no meaningful 巡数. `engineRows(routine)` is pure and decides which of
   制限時間 / 巡数 / 種目の間の休息 / 巡の間の休息 appear and with what labels.
5. `FIXED_SETS` (リーコン・ロン) renders its **current step**, not all 18: `第七段 ・ 七 六 五 四 四`
   with a `十八段のうち` caption, derived from `progression_state`; `第一段` with no history.
6. The 最高 tiles are engine-dependent — `bestTilesFor(engine, best)` is pure. AMRAP → 最高巡数/最高反復;
   FOR_TIME → 最速; INTERVAL_CIRCUIT/EMOM/FIXED_SETS → 最高負荷 (weighted volume).
7. A PR set *this month* draws `c.accent`; older PRs draw `c.ink`.
8. Long user names wrap to 2 lines; the header `Row` uses `Alignment.Top` so とじる stays pinned, as in
   `CalendarScreen.kt:102`.
9. Entering from a session whose pinned version differs from the head: the page shows the **current**
   routine plus a `c.inkFaint` line `この記録のときとは 中身が変わっています`. **It never re-renders the
   past from the present.**
10. Tapping 始める twice: guarded by `startInFlight`, and `idx_session_live` would refuse the second
    insert anyway.

**Accessibility** — Header + subtitle read as one node. 始める is
`contentDescription = "シンディを始める"` — the bare word is ambiguous once the title has scrolled off.
Tier chips are `Role.RadioButton`. Station rows: `"一番目、懸垂、五回"`, trailing `›` decorative. 最高
tiles read **label first** — `"最高巡数、十七巡"` — because value-then-label reads as a fragment. The
destructive confirm button is never the initially focused one.

**Pure logic** — `engineRows`, `bestTilesFor`, `stepFor`, `deleteCopy`, `structuralHash`,
`snapshotDiffers`, plus `03-player.md`'s `estimateMs` / `applyTier` / `previewRows` /
`prescriptionLabel`.

`previewRows` collapses long routines: マーフ's 100 pull-ups is **one row**, not 100 — the page shows the
prescription summary, never the expanded timeline.

---

### GYM.LIBRARY.BUILDER — 型を作る / 型を編集

**Purpose** — Author or edit a routine: name it, choose an engine, order its stations, set every
prescription and rest, and see what it will cost before committing.

**Entered from** — index 作る (new) · index long-press 写して作る (edit the copy) · detail 編集 · detail
写して作る.
**Exits to** — `STATION_PICKER` · `DETAIL(savedId)` when entered from detail · `INDEX` when entered from
index · caller on やめる / clean back.

**Back behaviour** — Dirty is `structuralHash(draft) != structuralHash(original) || draft.name !=
original.name` (a new routine is dirty as soon as the name is non-blank or a station exists). Clean →
straight pop. Dirty → the discard dialog (`AlertDialog`, `c.bgSolid`, `編集をやめますか` /
`保存していない変更は消えます。` / confirm `やめる` `c.accent` / dismiss `もどる` `c.inkFaint`).
**Returning from `STATION_PICKER` never triggers the prompt** — that is a forward-and-back within one
edit, and the draft lives in `GymViewModel`, not the composable.

**Tab bar** — hidden. `immersive`.

**Data in** — `routineDetail(id)` in edit mode · `ExerciseCatalog` (synchronous) · `routines` for name
collision. The draft is a `MutableStateFlow<RoutineDraft>` in `GymViewModel`, surviving rotation and the
picker round trip.
**Data out** — `saveRoutine(draft)` on 保存, and nothing else. **The builder writes exactly once, on an
explicit tap.**

```
┌──────────────────────────────────────────────────────────┐
│  型を作る                              やめる      保存   │ ← 保存 c.accent (c.inkFaint if !canSave)
├──────────────────────────────────────────────────────────┤
│  名前  ▏朝の五分                                          │ ← TitleField verbatim, Mincho 20.sp
│  ────────────────────────────────────────────────        │
│  方式                                        巡回   ›    │ ← PickerRow
│    ┌ unfolds in place (animateContentSize 220ms) ┐        │
│    │ 巡回   段階   毎分   毎分増   完走   時間内  │        │
│    └───────────────────────────────────────────┘        │
│  種目                                                     │
│  ┌────────────────────────────────────────────────────┐  │
│  │ 腕立て伏せ            二十回                  ⋮⋮  │  │ ← row height FIXED at 56.dp
│  │ ディップス            十回                    ⋮⋮  │  │   handle 24.dp in a 48.dp target
│  │   腕立て伏せ と ディップス は続けて置かない方がよい │  │ ← Gothic 12.sp lh18 c.inkFaint,
│  │ スクワット            三十秒                  ⋮⋮  │  │   inset under the SECOND of the pair
│  └────────────────────────────────────────────────────┘  │
│                                          ＋ 加える        │ ← Mincho 14.sp ls2 c.accent, 48.dp
│  種目の間の休息                              十五秒  ›    │ ← PickerRow → TempoValueWheel
│  巡の間の休息                                六十秒  ›    │
│  巡数                                          五巡  ›    │
│    ┌ TempoValueWheel, WHEEL_HEIGHT = 132.dp ┐            │
│    │           十秒                          │            │ ← 栞 rules: 2 × 1.dp c.hair
│    │        ▶  十五秒  ◀                     │            │   selected Mincho 22.sp c.ink
│    │           二十秒                        │            │   others  Mincho 16.sp c.inkFaint
│    └────────────────────────────────────────┘            │
│  ──────────────────────────────────────────────          │
│  約 十八分 ・ 三百回                                       │ ← updates live, no animation
└──────────────────────────────────────────────────────────┘
```

Body: `Column(verticalScroll).padding(horizontal = 26.dp).imePadding()` — `EventComposeScreen.kt:159,
200-206` verbatim. The station list is a **plain `Column`, not a `LazyColumn`** (edge case 1).

**States** — `Loading` · `Ready, new` (heading 型を作る, name auto-focused) · `Ready, editing` (heading
型を編集, name **not** auto-focused — the composer's stated reason: "on an edit the user came to change
something specific, and a keyboard in their face is presumptuous") · `EmptyStations` (list box absent;
＋ 加える directly under the heading with `種目を加えてください`) · `Dirty` · `Saving` (保存 becomes
保存中, header actions disabled, body untouched) · `Error` (`FaultStrip` **above** the fields, never
over them, `padding(bottom = 16.dp)`; `StoreFull` → 空き容量が足りません with **no retry action**, since
retry cannot help) · `Dragging` (the lifted row at `c.card` with the handle in `c.inkSoft`; the gap it
left collapses with `animateContentSize(tween(160, LinearOutSlowInEasing))`. **No elevation, no shadow,
no scale.**) · `Warning` (one `c.inkFaint` line per clashing adjacent pair; **warnings never disable
保存** — design §6: "The builder warns, it does not block") · `WheelOpen` (exactly one `PickerRow` may
be expanded at a time).

`canSave = name.isNotBlank() && stations.isNotEmpty() && !saving && routineLoaded`. Warnings do not
affect it.

**Edge cases**

1. **Drag-reorder without a library.** `CONTRIBUTING.md` forbids new dependencies, so `reorderable` is
   out and hand-rolled `LazyColumn` reordering is fragile. Station lists are bounded (cap 24), so the
   list is a **plain `Column`** inside the page's existing `verticalScroll`. Implementation:
   `Modifier.pointerInput(stations.size) { detectDragGesturesAfterLongPress(…) }` **on the handle
   only**; track `draggingIndex` + `dragOffsetPx`; the lifted row gets
   `graphicsLayer { translationY = dragOffsetPx }` and `zIndex(1f)`; every other row gets a
   `translationY` of ±rowHeight from the pure `reorderShift(i, from, to)`. **Row height is fixed at
   56.dp so the maths needs no measurement pass.** On drop, `moveItem(list, from, to)` and clear the
   offsets. Auto-scroll when the finger is within 48.dp of either viewport edge, at 6.dp per frame.
2. **Cap of 24 stations.** Beyond that the estimate line and the compiler both stop being useful;
   ＋ 加える greys out with `これ以上は加えられません`.
3. **The adjacent-pattern warning wraps.** When `rounds > 1`, the last station and the first are
   adjacent too. `adjacentPatternClashes` returns the wrap pair when `rounds > 1`. Design §6 misses
   this; the fatigue argument applies identically.
4. **Duplicate names are allowed.** Two routines called 朝の五分 are the user's business. A `c.inkFaint`
   `同じ名前の型があります` appears; 保存 stays enabled.
5. **Engine change is lossy and must be honest.** `migrateDraft(draft, newEngine)` is pure and explicit
   and returns the notices to display: 巡回→時間内 replaces 巡数 with 制限時間 (default 二十分); →段階
   clears rests and rounds and uses one station (extras kept but greyed with
   `段階では一種目だけ使われます`); →毎分 forces station rest to zero
   (`毎分では種目の間の休息はありません`). **Nothing is silently dropped without a visible line saying
   so.**
6. **The estimate on an AMRAP is a cap, not a duration** — `約 二十分 ・ 六百回まで`.
   `estimateLabel(engine, estimate)` picks the wording.
7. **`MaxEffort` has no rep estimate.** It contributes 0 reps and its *median historical* seconds (or
   45s with no history) to the duration, and the estimate line appends ` 目安`. **Never present a guess
   as arithmetic.**
8. **Saving over a routine with history** bumps `version`; a `c.inkFaint` line under 保存 reads
   `これまでの六回の記録はそのまま残ります` when `countForRoutine > 0` and the structure is dirty. The
   user should know editing is safe *before* they press it.
9. **Rotation mid-drag** cancels the drag and restores the pre-drag order — a half-completed reorder is
   worse than none.
10. **`imePadding()` is mandatory** — the name field is at the top but the wheels are at the bottom, and
    the keyboard must not shove the estimate line off-screen.
11. Wheel ranges: station rest `0,5,…,120s` (`なし` at 0); round rest `0,15,…,300s`; 巡数 `1..20`; reps
    `1..100`; seconds `5,10,…,300`. Rendered in kanji via `kanjiExtended` (prerequisite P5).

**Accessibility**

- **The drag gesture is invisible to TalkBack.** Every station row therefore declares
  `customActions = listOf("上へ動かす", "下へ動かす", "編集", "削除")`. **This is the single most
  important a11y item on the page**, following the precedent at `NotificationsScreen.kt:204`.
- Handle: `"腕立て伏せ の並べ替え"`, `stateDescription = "移動中"` while dragging, and the new position
  announced on drop (`"三番目に移動しました"`).
- Row body: `"一番目、腕立て伏せ、二十回"`, `Role.Button`.
- **Warning lines are separate `liveRegion = Polite` nodes**, not absorbed into the row, so a clash
  announces itself when the user reorders into one.
- Wheel: the wrapping `Box` carries `contentDescription` and `stateDescription`; the per-item `Text`s
  are `clearAndSetSemantics {}`-cleared so TalkBack does not read all 25 rows.
- 保存 disabled → `"保存、名前と種目が要ります"`, never an unexplained inert word.

**Pure logic** — `estimateRoutine`, `estimateLabel`, `adjacentPatternClashes`, `clashCopy`, `moveItem`,
`reorderShift`, `migrateDraft`, `canSave`, `isDirty`, `restOptions`, `repOptions`, `secondOptions`.

---

### GYM.LIBRARY.STATION_PICKER — 種目をえらぶ

**Purpose** — Choose the exercise for one station and set what it prescribes, in one page and one save.

**Entered from** — the builder only: ＋ 加える (`index = null`) or tapping a station (`index = i`).
**Exits to** — the builder, on 保存 / やめる / back / 削除.

**Back behaviour** — Back == やめる: pop, draft untouched. **No discard prompt** — a single station is a
small enough unit that a second dialog is friction without benefit, and the builder's own prompt still
guards the routine as a whole. A deliberate asymmetry, stated so a reviewer does not "fix" it.

**Tab bar** — hidden.

**Data in** — `ExerciseCatalog.byPattern()` (synchronous) · optional `movementBests()` for PB hints.
**Data out** — none directly; returns a `Station` to `GymViewModel.applyStation(index, station)`.

```
┌──────────────────────────────────────────────────────────┐
│  種目をえらぶ                          やめる      保存   │
├──────────────────────────────────────────────────────────┤
│  ▏さがす                                                  │
│  押す                                                     │ ← pattern heading Mincho 12.sp ls3
│  ● 腕立て伏せ                            一.〇           │ ← name Mincho 15.sp c.ink
│    膝つき腕立て                          〇.五           │   coefficient Gothic 11.sp c.inkFaint
│    ディップス                            一.二           │   selected dot 5.dp c.accent
│  引く                                                     │
│    懸垂                                  一.八           │
│  … しゃがむ ・ 体幹 ・ 移動 ・ 跳ぶ …                      │
├─ unfolds once an exercise is selected ───────────────────┤
│  腕立て伏せ                                               │ ← Mincho 20.sp c.ink
│  肘は体の近くに                                           │ ← cue Gothic 12.sp c.inkFaint
│  はかり方         回数    秒数    限界まで               │ ← chips Mincho 13.sp ls1
│    ┌ TempoValueWheel ┐                                   │
│    │  ▶  二十回  ◀   │                                   │
│    └─────────────────┘                                   │
│  目安 〇:四十                                             │ ← Gothic 13.sp c.inkFaint
│                     削除                                  │ ← edit mode only
└──────────────────────────────────────────────────────────┘
```

**States** — `NothingSelected` (list only; prescription block absent; 保存 disabled) · `Selected`
(prescription unfolds, `animateContentSize` 220ms) · `EditMode` (opens with the exercise selected **and
scrolled into view**, prescription pre-filled, 削除 present, 保存 enabled immediately) · `NoMatch` ·
`MaxEffortSelected` (the wheel is replaced by `できるところまで` and the 目安 line disappears — there is
nothing to estimate).

**There is no Loading and no Error state for the list.** The catalogue is an in-memory map
(`00-plan.md` §2 row 6). Stated explicitly to prevent someone adding a spurious spinner.

**Edge cases**

1. Switching 回数 → 秒数 does **not** carry the number across (20 reps ≠ 20 seconds). Each type
   remembers its own last value within the session; defaults 二十回 / 三十秒.
2. `限界まで` is unavailable on `EMOM` / `EMOM_ASCENDING` — those engines are *defined* by a fixed rep
   count. The chip renders `c.inkFaint` and unclickable, **with a one-line reason**.
3. Duration prescriptions on `FOR_TIME` are meaningless (the point is total reps) — likewise disabled
   with a reason.
4. Selecting the exercise already at a neighbouring index shows the clash warning **here**, inline,
   before the user commits — cheaper than discovering it back in the builder.
5. The 目安 uses `secondsPerRep` and is **labelled 目安 every single time it appears**. It never advances
   anything, and the label is what says so.
6. Search trims leading/trailing space before folding.
7. `imePadding()` plus `LaunchedEffect(selectedId) { scrollTo(prescriptionBlock) }` so the wheel is
   never born under the keyboard.

**Accessibility** — Rows are `Role.RadioButton`, `"腕立て伏せ、押す、難度 一.〇"`, `stateDescription =
"選択中"`. Disabled measure chips carry the reason in their description
(`"秒数、この方式では使えません"`). The prescription block announces itself on unfold via
`liveRegion = Polite`. 保存 disabled → `"保存、種目をえらんでください"`.

**Pure logic** — `allowedMeasures(engine)`, `defaultPrescription(measure)`, `paceEstimateSec(p, e)`,
`matchExercise`, `coefficientLabel`.

---

### GYM.LIBRARY.EXERCISE_INDEX — 種目

**Purpose** — The movement catalogue as a browsable thing: what exists, how hard each is, where it sits.

**Entered from** — library footer 種目を見る · `GYM.RECORDS.PR` すべての種目.
**Exits to** — `EXERCISE_DETAIL` · caller.
**Back** — straight pop; search state discarded. **Tab bar** — visible.

Cards match the library index geometry: name (Mincho 16.sp `c.ink`) + coefficient (Gothic 11.sp
`c.inkFaint`) / cue line / meta line `押す ・ 二.〇秒/回` with a trailing `最高 三十二回` in `c.accent`
when history exists.

**States** — `Ready` **always**; the catalogue is in-memory, so it is never Loading, never Empty, never
Failed. A PB-overlay failure simply omits the 最高 fragment — it is an enrichment, never a gate.
`NoMatch` · `SearchActive` (pattern headings collapse to a single flat list; grouping under a query is
noise).

**Edge cases**

1. Ordering within a pattern is **by difficulty ascending**, so a ladder reads top-to-bottom as it
   should be climbed. Ties by name.
2. Pattern order is fixed and matches the ACSM alternation logic in design §9: 押す → 引く → しゃがむ →
   股関節 → 体幹 → 移動 → 跳ぶ. Not alphabetical, not by count.
3. `cue == null` → the line is omitted, not blank.
4. No history → no 最高 fragment, not `最高 —`.
5. 走る has no rep semantics: its coefficient renders `—` and the `秒/回` fragment is suppressed.

---

### GYM.LIBRARY.EXERCISE_DETAIL — 種目の中身

**Purpose** — One movement in full: what it is, how to do it, what it is worth, where it sits on its
ladder, how far up you have climbed, and what uses it.

**Entered from** — exercise index · a station row · a PR row · a breakdown row.

**Back behaviour** — **Ladder taps replace rather than push** (`popUpTo(ExerciseDetail) { inclusive =
true }`). Walking a seven-rung ladder would otherwise build a seven-deep stack and make back a maze.
One back press always leaves the exercise, whichever rung you are standing on.

**Tab bar** — visible. **Data out** — none.

```
┌──────────────────────────────────────────────────────────┐
│  腕立て伏せ                                        とじる │ ← Mincho 26.sp ls 3.sp c.ink
│  押す ・ 難度 一.〇                                       │ ← Mincho 13.sp ls 4.sp c.inkFaint
├──────────────────────────────────────────────────────────┤
│  肘は体の近くに、体は一直線に                             │ ← Gothic 14.sp lh 21.sp c.inkSoft
│  最高                                                     │
│  ┌──────────────┬──────────────┬──────────────┐          │
│  │  三十二回     │   四百回      │  六月十日     │          │ ← value Mincho 22.sp c.ink
│  │  一度に       │  のべ回数     │   最後       │          │ ← label Gothic 11.sp c.inkFaint
│  └──────────────┴──────────────┴──────────────┘          │
│  段階                                                     │
│    ○  壁腕立て                            〇.二           │ ← spine 1.dp c.hair at x = 26+9 dp
│    │                                                     │   dot 5.dp; reached c.inkSoft,
│    ○  斜め腕立て                          〇.四           │   unreached c.inkFaint,
│    │                                                     │   current c.accent + ring
│    ○  膝つき腕立て                        〇.五           │
│    │                                                     │   name Mincho 15.sp; current row
│    ●  腕立て伏せ                          一.〇   いま    │   c.ink Medium, others c.inkSoft
│    │                                                     │ ← いま Mincho 11.sp ls3 c.accent
│    ○  足上げ腕立て                        一.三           │
│    ○  アーチャー腕立て                    一.六           │
│    ○  片手腕立て                          二.五           │
│  使われている型                                    四件   │
│  七分間 ・ シンディ ・ チェルシー ・ 朝の五分             │ ← Gothic 13.sp c.inkSoft, tappable
└──────────────────────────────────────────────────────────┘
```

**States** — `Ready` · `NoHistory` (最高 replaced by `まだ やっていません`; no rung marked いま; every
dot `c.inkFaint`) · `NoLadder` (a movement that is its own ladder — プランク, 走る — omits 段階 entirely)
· `Unused` (`どの型にも入っていません`) · `PbFailed` (最高 replaced by a one-line
`記録を読めません ・ もう一度`; the rest is unaffected, because the catalogue is in-memory and the page
can never be blank) · `UnknownId` (**immediate pop, no error page** — there is no user-reachable path
that produces this; it is a programming error and should fail fast rather than render a ghost).

**Edge cases**

1. **"Current rung" is defined, not vibes:** the hardest rung (max `difficulty`) performed in the **last
   90 days** with at least one completed set of ≥ 3 reps. Below that threshold, nothing is marked いま.
2. Rungs *harder* than the current one are `c.inkFaint`; rungs at or below it are `c.inkSoft` — the
   ladder reads as climbed-so-far without any gamified language.
3. A rung the user has performed but which is *easier* than their current one still counts as reached —
   regression to a warm-up variation must not un-climb the ladder.
4. **一度に (max reps in a single set) counts only sets where `actualReps` was recorded.**
   Prescribed-but-unverified reps never set a PB. Say so in the KDoc — this is the difference between a
   record and a wish.
5. のべ回数 caps its kanji display at 九千九百九十九 and switches to arabic beyond, because kanji at that
   magnitude is unreadable (prerequisite P5).
6. 使われている型 excludes archived routines but **includes** built-ins.
7. The ladder is drawn with **real composables** (a `Column` of `Row`s plus a
   `Box(width = 1.dp).background(c.hair)` spine behind them), **not** a `Canvas` — it needs text, taps
   and semantics per rung.

**Accessibility** — Rung: `Role.Button`, `"腕立て伏せ、難度 一.〇、いまここ"` or `"…、まだ"`. The spine
and dots are decorative; the parent `Row` uses `clearAndSetSemantics`. 最高 tiles label-first.
使われている型 is a `FlowRow` of individually tappable names, each `sizeIn(minHeight = 48.dp)`.

**Pure logic** — `currentRung(ladder, bests, today, window = 90)`, `reachedRungs`, `routinesUsing`,
`kanjiExtended`.

---

## 4. `GYM.RECORDS.*`

### GYM.RECORDS.INDEX — 記録

**Purpose** — The month at a glance in ink, the streak that survives a rest day, and the three numbers
worth knowing.

**Entered from** — the 記録 tab · `GYM.SESSION.COMPLETE` 記録を見る.
**Exits to** — `HISTORY` (すべて見る, or tapping the grid) · `PR` (最高) · `CHARTS` (詳しく, or the
sparkline) · `SESSION_DETAIL` (a 最近 row).

**Back behaviour** — Rebases to `GYM.HOME`. **Month paging resets to the current month on re-entry** —
a records page that opens on March because you looked at March last week is disorienting.

**Data in** — `summary()` · `monthLoad(month)` · `loadScale(90)` · `history(null, 3)` ·
`weeklySeries(12)` for the preview. **Data out** — none.

```
┌──────────────────────────────────────────────────────────┐
│  記録                                              詳しく │
│  令和八年 ・ 六月                                         │
├──────────────────────────────────────────────────────────┤
│                    ‹   六月   ›                           │ ← month pager, Mincho 15.sp ls3
│              日 月 火 水 木 金 土                          │ ← Mincho 10.sp ls1 c.inkFaint
│              ·  ·  ●  ·  ○  ●  ·                          │   cells 20.dp, dots 4.dp,
│              ●  ·  ●  ·  ●  ·  ·                          │   c.ink @ 0.15/0.35/0.6/0.9
│              ·  ●  ·  ●  ·  ·  ●                          │   today: 1.dp c.hair ring r 7.dp
│              ●  ·  ⊙  ·                                   │   ← ONE Canvas (§5.1)
│              十二日 ・ 六月                                │ ← Gothic 11.sp c.inkFaint
│  四日 連続                                                │ ← Mincho 14.sp ls2 c.inkSoft
│  ゆるし 一回 使いました                                   │ ← Mincho 12.sp ls2 c.inkFaint
│  同じ調子が続いています                                   │ ← Gothic 12.sp c.inkFaint (monotony)
│  ┌──────────────┬──────────────┬──────────────┐          │
│  │   十二回      │  二百四十分   │   八十六回    │          │ ← value Gothic 20.sp c.ink
│  │   今月        │   活動時間    │   これまで    │          │ ← label Gothic 11.sp c.inkFaint
│  └──────────────┴──────────────┴──────────────┘          │
│  週ごと                                          詳しく   │
│  ▁▃▂▅▃▄▁▆▄▃▅▂                                            │ ← Canvas 56.dp preview
│  最近                                            すべて見る│
│  六月十七日   七分間      六分十四秒   きつい             │ ← Gothic 13.sp c.inkSoft
│  最高                                                  ›  │
└──────────────────────────────────────────────────────────┘
```

**States**

- **Loading** — `EmptyState("読み込み中")` for the whole body. **Not a skeleton grid**: a skeleton grid
  is indistinguishable from a month with no sessions, which is exactly the `Loadable` doctrine's
  forbidden confusion.
- **Ready**.
- **Empty (zero sessions ever)** — `まだ 記録はありません` plus a single 型をえらぶ action. No empty
  grid, no zero tiles — **a wall of 〇 is a scolding**.
- **Ready, empty month (history exists)** — the grid renders with no dots and the caption reads
  `この月は 〇日`. **This is not the empty state**; the pager must remain usable.
- **Error** — `FaultPanel`. `StoreFull` → 空き容量が足りません with no retry.
- **StreakZero** — `連続は とぎれています` in `c.inkFaint`, never `〇日 連続`.
- **Forgiveness line** — present only when `forgivenessUsedThisMonth > 0`. **Never states how many
  remain** — that invites gaming the number.
- **Monotony nudge** — only when `monotony7d != null && monotony7d > 2.0 && historyDays >= 14`.
  Suppressed below 14 days, per §7.4's stance on premature metrics.
- **Chart preview failed** — the 週ごと block is omitted. A failed sparkline must not take down the page.

**Edge cases**

1. **Ink levels are relative, not absolute** — quartiles of the user's own non-zero daily loads over the
   trailing 90 days. Absolute thresholds would leave a 20-minute-session user permanently at level 1 and
   a marathoner permanently at 4. Below 8 non-zero days, fixed cutoffs at 15 / 30 / 45; document it.
2. **The grid does not scroll to a day.** A tap resolves to a date via the pure `dayAt(offset)`, but
   navigates to `HISTORY` anchored at that month, **not** a filtered single-day view. One list, one
   idiom.
3. Days *after* today draw nothing at all — no faint placeholder. An unlived day is not an unfilled one.
4. `‹` disabled at the first session's month, `›` at the current month. Disabled arrows draw `c.hair`,
   not `c.inkFaint`, and are non-clickable.
5. **The clock guard matters here**: the streak takes `today` from the repository's monotonic-guarded
   high-water mark, never a raw `LocalDate.now()`.
6. **A session that crosses midnight belongs to the day it started** — consistently in the grid, the
   streak and the history grouping. One rule, stated once.
7. Timezone change (travel) can put two sessions on the same local day. The grid sums their loads; the
   streak counts the day once.
8. The rating fragment is absent on unrated sessions — the 最近 row simply ends after the duration.

**Accessibility** — **The grid is one `Canvas` and therefore one TalkBack node**, carrying a spoken
*summary*: `"六月、十二日 鍛錬しました、いちばん多かったのは 六月十七日"`, `role = Role.Button`,
`onClick(label = "記録の一覧をひらく")`. Per-day access is provided by `HISTORY`, which is a real list
with real nodes. **This is the deliberate answer to "how does a blind user read a heatmap": you give
them the list, not a worse heatmap.** The streak + forgiveness + monotony lines are **one node** —
three announcements for one thought is worse than one. Tiles read label-first.

**Pure logic** — `currentStreak` (`02-data.md` §D.1), `inkLevel`, `loadScaleFrom`, `monthCells`,
`dayAt`, `sessionLoad`, `monotony`, `streakCopy`, `gridSemantics`.

---

### GYM.RECORDS.HISTORY — これまで

**Entered from** — records index · `LIBRARY.DETAIL` すべて見る (`routineId` filter) · a PR row's count
fragment.

**Back behaviour** — Straight pop. **The `routineId` filter is a nav argument and is not user-clearable
here** — a filtered history opened from a routine stays that routine's history. A 絞り込みを外す action
would silently change what page you are on. Scroll and loaded pages live in `GymViewModel` keyed by the
filter, so a round trip to a session detail does not re-page from the top.

```
┌──────────────────────────────────────────────────────────┐
│  これまで                                          とじる │
│  八十六回 ・ 二千四百分                                   │ ← filtered: 「七分間」十四回
├──────────────────────────────────────────────────────────┤
│  六月                                            十二回   │ ← Mincho 12.sp ls3 | Gothic 11.sp
│  ┌────────────────────────────────────────────────────┐  │
│  │ 七分間                              六分十四秒     │  │ ← name Mincho 16.sp c.ink
│  │ 十七日 ・ 三巡 ・ 三百二十回                       │  │   dur Gothic 12.sp c.inkFaint
│  │ きつい                            自己最高        │  │ ← Gothic 13.sp lh19.5 c.inkSoft
│  └────────────────────────────────────────────────────┘  │   自己最高 Mincho 11.sp ls3 c.accent
│  ┌────────────────────────────────────────────────────┐  │
│  │ シンディ                                二十分     │  │
│  │ ちょうど          途中まで ・ 三種目中 二          │  │ ← partial chip Mincho 11.sp ls3
│  └────────────────────────────────────────────────────┘  │   c.inkFaint
│  五月                                             九回    │
│                    読み込み中                             │ ← footer sentinel, 56.dp
└──────────────────────────────────────────────────────────┘
```

`item(key = "month:$ym")` headers + `items(sessions, key = { it.key })` — structurally identical to
`CalendarScreen.kt:148-158`. **Headers are plain items, not `stickyHeader`**: the calendar page sets the
precedent and `stickyHeader` is still experimental.

**States** — `Loading (first page)` · `Ready` · `Loading (next page)` (list intact, 56.dp footer
読み込み中) · `EndOfList` (footer becomes a 40.dp `Spacer`; no "no more items" text) · `Empty`
(`記録はありません`; filtered: `この型は まだ やっていません`) · `Error (first page)` (`FaultPanel`) ·
**`Error (next page)` — list intact, footer becomes a tappable もう一度 in `c.accent`. A paging failure
must never destroy what is already on screen.** · `DeleteConfirm`.

**Edge cases**

1. **Keyset pagination, never `OFFSET`.** `OFFSET` skips or duplicates rows when a session is deleted
   mid-scroll.
2. **Ties on `started_at`** (possible via a restored backup) break by id, so the cursor is
   `(started_at, id)` and the query `WHERE (started_at, id) < (?, ?)`. Say it — a single-column cursor
   silently drops one of the pair.
3. **Deleting the last session in a month must remove that month's header too.** The grouping is derived
   from the loaded list on every emission (`groupBy { it.month() }`), never incrementally patched.
4. A session whose routine was archived still shows its `routine_name` — **from the denormalised
   column, never a join**. この型を見る still works and lands on the archived detail.
5. **Swipe-to-dismiss is deliberately not used**, unlike `NotificationsScreen`. The reasoning is the
   calendar page's in kind: dismissing a notification is local and undoable; deleting a training record
   is neither. It goes behind a long-press and a confirmation.
6. Rotation with 5 pages loaded must not re-fetch them. Pages live in `GymViewModel`.
7. `anchorMonth` from the grid is honoured by paging until that month is loaded, then scrolling to its
   header — not by a jump-to-offset query.
8. The subtitle's totals come from `summary()`, not the loaded pages (which are partial).

**Accessibility** — Row: one node, `"七分間、六月十七日、六分十四秒、三巡、三百二十回、きつい、自己最高"`.
Month header one node. Long-press exposed as `customActions` **and** `onLongClick(label = "メニュー")`.
The paging footer is `liveRegion = Polite` so 読み込み中 → new rows is announced once, not per row.

**Pure logic** — `groupByMonth`, `nextCursor`, `shouldPrefetch`, `mergePage`, `historySubtitle`,
`sessionRowLines`.

---

### GYM.RECORDS.SESSION_DETAIL — 記録の中身

**Purpose** — Reopen one finished session, read-only, **honest about how it looks now rather than how it
looked then**.

**Entered from** — history · records index 最近 · an attempt row · a PR row's date fragment.
**Not** entered from `GYM.SESSION.COMPLETE` — that is the player's terminal state and pops to
`GYM.HOME`.

**Exits to** — `LIBRARY.DETAIL` (型を見る) · `EXERCISE_DETAIL` (a breakdown row) ·
`GYM.SESSION.PREPARE` via `LIBRARY.DETAIL` (もう一度) · caller.

**Back** — straight pop. An edited rating persists immediately on tap, so there is never a discard
prompt. **Tab bar** — visible.

#### How this differs from `GYM.SESSION.COMPLETE`

Same data, same design §4 layout, same §11 tokens. **One component,
`ui/gym/RecordSummary.kt`, parameterised by `RecordMode { Live, Historical }`** — design §5's "one
component, two entry points", made true without either page lying. Eleven deliberate differences, every
one a correctness or dignity point:

| | `COMPLETE` (Live) | `SESSION_DETAIL` (Historical) |
|---|---|---|
| **Data source** | the live segment list still in the view model | re-read from SQLite + the pinned `routine_version` |
| **Ensō closure** | animates closed once, 900ms — the one moment of ceremony | drawn **already closed**, no animation, ever. *Ceremony you can replay is not ceremony.* |
| **Rating** | the primary ask, unset, asked before the accolades so celebration cannot bias it | shows what was answered, editable by tapping another; no どうでしたか heading if already rated |
| **Rating unset** | possible (skippable) | shows the prompt, so an old skipped session can still be answered — later is better than never |
| **Comparison line** | vs. the session immediately before it | vs. the session immediately before **it** (`started_at <`) — **not** the most recent overall. Reopening a March record must not compare it to yesterday |
| **PR chip** | `自己最高` when it was a PR | `自己最高` **only when `isStillBest`**; otherwise `当時の自己最高` in `c.inkFaint`. A record since beaten must not keep claiming the crown |
| **Streak line** | the streak as of completion | **absent.** Showing today's streak on a historical page is a category error, and showing it as of that date means walking the whole history for one line |
| **予定に入れる** | present — scheduling the next belongs to the moment you finish | absent; replaced by もう一度 |
| **Header actions** | none — the page is terminal | とじる + a 削除 action at the foot |
| **Tab bar** | hidden | visible |
| **Back** | pops the whole player stack | pops one entry |
| **Partial sessions** | 途中まで chip, no PR chip, no closure | **identical** — the one thing deliberately the same, because a partial session is a real session and must read identically wherever it appears |

**Data out** — `setRating(key, rating)` (optimistic; **reverted on failure**, with a `FaultStrip` above
the rating row — an optimistic UI that silently keeps a value the store rejected is a lie in the record)
· `delete(key)`.

**Edge cases**

1. **The breakdown renders from `session_result` joined to the pinned `routine_version`, never to the
   live routine.** A station removed last week still appears in a session performed before it was
   removed. This is the entire justification for the version pin.
2. `isStillBest` is **recomputed on read, not stored**, so beating an old PR retroactively demotes the
   old record's chip with no migration.
3. The comparison baseline is scoped to the same `routine_id` **and** the same tier — comparing an Rx
   run to a やさしい run is meaningless.
4. Sessions predating the version pin (none at ship, but backups outlive schemas) fall back to the live
   routine and show `当時の内容は残っていません`. **Never fabricate a structure.**
5. A duration station shows `済` with no rep count; a rep station where actual == prescribed shows one
   number; where they differ shows `十八回 / 二十回`. `breakdownRow` is pure and covers all four shapes
   plus とばした.
6. `active_ms` excludes pauses; the hero is **always** active time, never wall-clock. If wall-clock is
   ever shown it needs a different label — do not silently swap them.
7. **Un-rating removes the session's contribution to load and therefore to monotony.** Correct: an
   unrated session has no CR10 and must not be assigned one.
8. Deleting the session holding a PR must make `routineBests()` re-emit. Bests are derived from the
   sessions table, not cached at the UI layer — one source of truth.
9. `GymFault.SessionGone` pops immediately (deleted in another shell state) rather than offering a retry.

**Accessibility** — The ensō is decorative (`clearAndSetSemantics {}`). Hero
`"活動時間 六分十四秒"`. Comparison + PR chip read as one node. Rating chips `Role.RadioButton`, 56.dp,
`onClick(label = "きついとして記録する")`. Breakdown rows one node each; skipped rows
`"バーピー、とばした"`. The 途中まで chip is announced as part of the header node, not stranded.

**Pure logic** — `comparisonCopy`, `prChip`, `breakdownRow`, `partialChipCopy`, `ensoSweep`, `heroTime`.

---

### GYM.RECORDS.PR — 最高

**Purpose** — Every best you hold, split by the two things that can improve: a routine, and a movement.

**Entered from** — records index 最高 · `LIBRARY.DETAIL` tiles.
**Exits to** — `LIBRARY.DETAIL` (routine row) · `EXERCISE_DETAIL` (movement row) · `SESSION_DETAIL`
(the date fragment) · `HISTORY(routineId)` (the count fragment) · `EXERCISE_INDEX` (すべての種目).

**Back** — straight pop; the tab selection resets to 型ごと. **Tab bar** — visible. **Data out** — none.

```
│      型ごと          動きごと                             │ ← segmented, Mincho 14.sp ls2
│      ──────                                              │   selected c.ink + 1.dp c.accent rule
│  ┌────────────────────────────────────────────────────┐  │
│  │ シンディ                              十七巡      │  │ ← value Mincho 22.sp c.accent
│  │ 最高巡数 ・ 時間内                                 │  │ ← meta Mincho 11.sp ls3 c.inkFaint
│  │ 六月十七日                            六回        │  │ ← both fragments tappable
│  └────────────────────────────────────────────────────┘  │
│  ── 動きごと ──                                           │
│  │ 腕立て伏せ                            三十二回     │  │
│  │ 一度に ・ のべ 四百回                              │  │
│  │ 六月十日                    いちばん上 足上げ腕立て│  │
```

**States** — `Loading` · `Ready` (sorted by `achieved_at` **descending** — a bests page should show
momentum, not an alphabet) · `Empty` (型ごと: `まだ 記録はありません`; 動きごと: same **plus**
`回数を数えた種目だけ ここに出ます`, because a user who only ever runs duration stations will otherwise
think it is broken) · `Error` **per tab independently** (a movement-bests failure must not hide routine
bests) · `ArchivedRoutineBest` (`削除済み` chip; still tappable).

**Edge cases**

1. **A PR requires `complete = 1`.** Partial sessions never set a best, in any engine.
2. **The metric is engine-determined** (`bestMetricFor`): FOR_TIME* → min `active_ms` (最速); AMRAP →
   max rounds then max reps (最高巡数); EMOM_ASCENDING → max rounds survived; INTERVAL_CIRCUIT / EMOM /
   FIXED_SETS → max weighted volume (最高負荷). Ties break toward the **earlier** session.
3. Scaled tiers are separate rows, labelled `シンディ ・ やさしい`.
4. A routine whose structure changed keeps its old best. **Deciding that a 12-station circuit's PR is
   invalidated by adding a 13th is a policy the app cannot get right**; a `c.inkFaint`
   `中身が変わっています` fragment is the honest amount to say.
5. 一度に counts only sets with a recorded `actual_reps`.
6. **Movement bests roll up the whole ladder** — 腕立て伏せ's row reports `いちばん上` as the hardest rung
   ever performed, keyed by ladder rather than variation. Seven near-identical push-up rows would be
   unreadable.
7. Weighted volume for duration prescriptions uses `volumeUnits` (`02-data.md` §D.5). It is never shown
   as a rep count, and 最高負荷 is deliberately unitless.
8. A best whose session was deleted simply becomes a different session's — the flow re-emits from
   surviving sessions. There is no orphan case.

**Accessibility** — Tabs `Role.Tab`, 48.dp. Row: one node,
`"シンディ、最高巡数 十七巡、時間内、六月十七日、六回"`. **The two tappable fragments are nested nodes
with `clearAndSetSemantics` on themselves and are exposed as `customActions` on the parent**
(`"この記録を見る"`, `"これまでを見る"`) — a nested-tappable row is otherwise unusable with TalkBack.

**Pure logic** — `bestMetricFor`, `routineBests`, `movementBests`, `isBetter`, `bestValueCopy`.

---

### GYM.RECORDS.CHARTS — 移り変わり

**Purpose** — Three quiet trends: how often, how long, how much — drawn in ink, with no library.

**Entered from** — records index 詳しく or the sparkline. **Exits to** — `HISTORY` · caller.
**Back** — straight pop; range resets to 十二週. **Tab bar** — visible. **Data out** — none.

```
│      十二週    二十六週    一年                           │ ← chips, 48.dp
│  週ごとの回数                                             │
│  ┌────────────────────────────────────────────────────┐  │
│  │        ▄     ▄      ▄  ▄     █     ▄               │  │ ← Canvas 96.dp, width − 44.dp
│  │  ▄  ▄  █  ▄  █  ▄   █  █  ▄  █  ▄  █               │  │   bars c.inkSoft@0.75,
│  └──────────────────────────────────────────────────┘    │   current week c.accent
│  ────────────────────────────────────────────────        │ ← baseline 1.dp c.hair
│  三月三十日                                     今週      │
│  いちばん多い週 六回 ・ ならして 三.四回                  │
│  活動時間                                                 │
│  │      ╱╲    ╱╲___╱╲___╱╲___╱   ╲___╱●              │  │ ← line 1.5.dp c.inkSoft,
│  合計 二千四百分 ・ ならして 二百分/週                    │   last point dot 3.dp c.accent
│  積み上げ                                                 │
│  │  ╷ ╷  ╷╷ ╷ ╷ ╷╷  ╷ ╷╷ ╷  ╷╷ ╷ ╷╷ ╷                │  │ ← daily ticks 1.dp c.hair
│  │  ─────────────────────╱───────────╱────            │  │ ← 7-day mean 1.5.dp c.inkSoft
│  日ごとの積み上げと 七日平均 ・ 目安                      │
│                   これまでを見る                          │
```

**States** — `Loading` (whole body; **never skeleton charts** — an empty chart and a loading chart look
identical, the doctrine's forbidden case) · `Ready` · `Empty` · **`ThinHistory (< 28 days)` — the
積み上げ chart is suppressed entirely with `二十八日ぶん たまると 出ます`.** Design §7.4 is explicit that
load surfaces stay suppressed until 28 days; a weighted-volume trend over 6 days is noise presented as
insight. · `Sparse` (zero weeks draw a 2.dp × 1.dp stub at `c.hair` so a gap reads as "zero", not
"missing"; the line **breaks** at gaps rather than interpolating) · `SinglePoint` (bars render one bar;
the line renders a single 3.dp `c.accent` dot and **no path** — never a horizontal line from one point)
· `Error` **per chart**.

**Edge cases**

1. **No chart animates.** Design §10's motion table has no entry for charts, and a growing-bars reveal
   is exactly the loops the doctrine forbids. Charts draw their end state on the first frame, including
   after a range change (which cross-fades the whole `Canvas` at 180ms and nothing more).
2. **The current week is partial** and is drawn in `c.accent` for that reason alone. It is **excluded
   from ならして** — a Monday must not drag the average down.
3. **The y-axis always starts at zero** and there is no axis label to say so, so it must be true.
   `maxValue = max(points.max(), 1)`.
4. **A dense range (一年 = 52 bars)** makes each bar sub-pixel. Below 4.dp of slot width the bar chart
   switches to the line renderer (`isDense`) and the caption says 週ごとの回数（折れ線）. Decided in a
   pure function, not at draw time.
5. **Weighted volume is labelled 目安 wherever it appears**, because duration stations enter it through
   an approximation.
6. **ACWR is computed but never drawn.** §7.4 is unambiguous: no injury-risk percentage, no ratio
   gauge. If a ramp warning is ever surfaced it is one `c.inkFaint` line of copy, not a chart. Stated
   here so nobody adds the fourth chart.
7. Screen width < 320.dp: charts keep 96.dp and the caption wraps to two lines. **Never shrink the
   plot.**
8. Every colour reads from `LocalTempoColors`; not one hardcoded value in any draw call.

**Accessibility** — Each `Canvas` is one node with a **spoken summary**, `role = Role.Image`, **no
click**: `"週ごとの回数、直近十二週、いちばん多い週は 六回、ならして 三.四回、今週は 二回"`. The caption
lines beneath are `clearAndSetSemantics {}`-cleared, since their content is already in the canvas node
— reading the same numbers twice is worse than not reading them. Charts are not clickable, so they carry
no `Role.Button` and no click label: **an a11y affordance for an action that does not exist is a bug.**

**Pure logic** — `barGeometry`, `linePoints`, `trailingMean`, `isDense`, `rangeWeeks`, `chartCaption`,
`chartSemantics`, `meanExcludingPartial`.

---

## 5. Canvas drawing

Three surfaces, all raw `androidx.compose.foundation.Canvas`. Every colour from `LocalTempoColors`,
every dimension through `Dp.toPx()`, **every piece of arithmetic in a pure function above** — the draw
scope only issues primitives. Precedents: `HomeScreen.Enso` (`drawArc` + `Stroke(cap = Round)`) and
`LineIcon` (`PathParser` + `withTransform`).

### 5.1 The month ink-grid

One `Canvas`, not 42 Boxes: 42 composables per month re-laid-out on every page, for content that is 42
circles — and a `Canvas` gives the today-ring sub-pixel control a `Modifier.border` on a 20.dp Box does
not.

```kotlin
Canvas(
    Modifier.width(cell * 7).height(cell * grid.rows)
        .clickable(onClick = onTap)
        .semantics { contentDescription = summary; role = Role.Button },
) {
    val cellPx = cell.toPx(); val dotR = 2.dp.toPx()      // 4.dp diameter
    val ringR = 7.dp.toPx(); val ringW = 1.dp.toPx()

    for (day in 1..grid.length) {
        val idx = grid.leadingBlank + day - 1
        val cx = (idx % 7) * cellPx + cellPx / 2f
        val cy = (idx / 7) * cellPx + cellPx / 2f
        val date = month.atDay(day)

        if (date == today) drawCircle(c.hair, ringR, Offset(cx, cy), style = Stroke(ringW))
        if (date.isAfter(today)) continue                 // an unlived day draws nothing

        when (inkLevel(load[date] ?: 0f, scale)) {         // pure, 0..4
            0 -> Unit                                      // rest day: blank
            1 -> drawCircle(c.ink.copy(alpha = 0.15f), dotR, Offset(cx, cy))
            2 -> drawCircle(c.ink.copy(alpha = 0.35f), dotR, Offset(cx, cy))
            3 -> drawCircle(c.ink.copy(alpha = 0.60f), dotR, Offset(cx, cy))
            else -> drawCircle(c.ink.copy(alpha = 0.90f), dotR, Offset(cx, cy))
        }
    }
}
```

- **Weekday letters** are a `Row` of seven `Text`s in 20.dp boxes **above** the Canvas — text inside a
  `DrawScope` needs a `TextMeasurer` and `drawText`, more machinery than a static header deserves.
- **Row count** is `ceil((leadingBlank + lengthOfMonth) / 7f)` — 4, 5 or 6. Wrap in
  `animateContentSize(tween(180, LinearOutSlowInEasing))` so a 5→6 row month change does not jump.
- **Week starts Sunday**, matching `JapaneseDate.DOW` (`JapaneseDate.kt:18`).
- **Alpha, not radius.** The four levels differ only in opacity. Do not "improve" this by scaling the
  dot — graduated sizes read as a bubble chart, and the whole point is that it is ink density.
- Write `dayAt(x, y, cellPx, grid)` even though the whole grid currently navigates to `HISTORY`. Four
  lines, and it is what makes the grid testable.

### 5.2 Sessions per week — bars

```kotlin
Canvas(Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = summary }) {
    val rects = barGeometry(values, size.width, size.height, barFraction = 0.42f,
                            minBarPx = 3.dp.toPx(), maxBarPx = 10.dp.toPx())
    rects.forEachIndexed { i, r ->
        val zero = values[i] == 0
        drawRect(
            color = when {
                zero -> c.hair
                i == rects.lastIndex -> c.accent           // the partial current week
                else -> c.inkSoft.copy(alpha = 0.75f)
            },
            topLeft = if (zero) Offset(r.left, size.height - 2.dp.toPx()) else r.topLeft,
            size    = if (zero) Size(r.width, 2.dp.toPx())  else r.size,
        )
    }
    drawLine(c.hair, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
}
```

- **`drawRect`, not `drawRoundRect`.** A rounded bar is a Material affectation; a flat ink bar is the
  app's register.
- **No axis, no gridlines, no in-canvas labels.** Two `Text`s beneath in a `Row(SpaceBetween)`, plus a
  third line carrying max and mean, all Gothic 11.sp `c.inkFaint`.
- **A zero week draws a 2.dp stub, not nothing.** "You did nothing that week" and "we have no data for
  that week" must not render identically — the `Loadable` doctrine, applied to pixels.

### 5.3 Active minutes — line

```kotlin
val segments = linePoints(values, size.width, size.height)   // pure; splits at nulls
val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
segments.forEach { seg ->
    when {
        seg.size == 1 -> drawCircle(c.inkSoft, 2.dp.toPx(), seg.first())
        seg.size > 1 -> drawPath(Path().apply {
            moveTo(seg.first().x, seg.first().y); seg.drop(1).forEach { lineTo(it.x, it.y) }
        }, color = c.inkSoft, style = stroke)
    }
}
segments.lastOrNull()?.lastOrNull()?.let { drawCircle(c.accent, 3.dp.toPx(), it) }
```

- **Straight `lineTo`, no smoothing.** A spline through weekly totals invents values that were never
  recorded; on a page whose purpose is honest records that is the wrong trade. Round joins already
  soften it into brushwork.
- **Gaps break the path** (`moveTo`), never an interpolated line across them. That is why `linePoints`
  returns `List<List<Offset>>`.
- **No area fill.** A gradient under the line would be the second-loudest thing in the app after a
  full-bleed red screen.
- Only the final point carries a dot, in `c.accent` — the same rationing of vermillion as everywhere.

### 5.4 Weighted volume — ticks + mean

Two passes: raw daily volume as 1.dp `c.hair` ticks (texture, not data you read off), then the 7-day
trailing mean drawn exactly like §5.3. `trailingMean(daily, 7)` returns nulls for the first 6 days, so
the mean line starts on day 7 — no ramp-in artefact, no fabricated early trend. The caption says
日ごとの積み上げと 七日平均 ・ 目安, and 目安 is not optional. Whole chart suppressed below 28 days.

**Shared rules:** zero baseline always; 96.dp (56.dp for the index preview); `fillMaxWidth()` inside the
page's 22.dp padding; **no animation ever**; one semantics node per canvas carrying a spoken summary;
not one hardcoded colour.

---

## 6. New Japanese strings

Beyond design §12. Format: purpose | string | notes.

### Library

| purpose | string | notes |
|---|---|---|
| library page title | `型` | design §12's kata; here a page, not a section |
| exercise index subtitle | `十六の動き` | count via `kanjiExtended` |
| search toggle | `探す` / `とじる` | |
| search placeholder | `さがす` | |
| no match (routines) | `該当する型はありません` | **never** 型はまだありません — that means something else |
| no match (exercises) | `該当する種目はありません` | |
| clear filters | `絞り込みを外す` | |
| duration buckets | `〜五分` / `五〜十五分` / `十五分〜` | single-select |
| tiers | `入門` / `中級` / `上級` | design §9 |
| engine: INTERVAL_CIRCUIT | `巡回` | design §6 already uses it |
| engine: FIXED_SETS | `段階` | dan — steps; Recon Ron is an 18-step ladder |
| engine: EMOM | `毎分` | |
| engine: EMOM_ASCENDING | `毎分増` | +1 rep each minute |
| engine: FOR_TIME | `完走` | the score is the clock |
| engine: FOR_TIME_WITH_REST | `完走 ・ 休息あり` | |
| engine: AMRAP | `時間内` | gloss `決めた時間で何巡できるか` on the detail page |
| provenance label | `出典` | value stays Latin |
| structure heading | `組み立て` | |
| start | `始める` | contentDescription `「シンディ」を始める` |
| time cap | `制限時間` | AMRAP only |
| rounds value for AMRAP | `時間内で` | in the 巡数 slot |
| attempts heading | `これまで` | |
| see all | `すべて見る` | |
| never attempted | `まだ やっていません` | |
| archived chip / restore | `削除済み` / `元に戻す` | |
| structure changed since | `この記録のときとは 中身が変わっています` | |
| unknown exercise | `不明な種目` / `種目が見つからないため 始められません` | |
| FIXED_SETS step | `第七段` / `十八段のうち` | |
| best tiles | `最高巡数` / `最高反復` / `最速` / `最高負荷` / `やった回数` | 最高負荷 deliberately unitless |
| favourite | `よく使うに入れる` / `よく使うから外す` | |
| duplicate suffix | `の写し` | collisions get の写し二 |
| edit | `編集` | matches 予定を編集 |
| builder title (edit) | `型を編集` | design §12 has 型を作る |
| builder: no stations | `種目を加えてください` | |
| builder: station cap | `これ以上は加えられません` | at 24 |
| builder: duplicate name | `同じ名前の型があります` | warns, never blocks |
| builder: history is safe | `これまでの六回の記録はそのまま残ります` | |
| builder: migration notices | `段階では一種目だけ使われます` / `毎分では種目の間の休息はありません` | one per lossy field |
| builder: rest zero | `なし` | |
| builder: discard | `編集をやめますか` / `保存していない変更は消えます。` / `やめる` / `もどる` | escape is **もどる** |
| station picker title | `種目をえらぶ` | |
| measure label | `はかり方` | |
| measures | `回数` / `秒数` / `限界まで` / `できるところまで` | |
| measure unavailable | `この方式では使えません` | on a disabled chip |
| exercise index entry | `種目を見る` | |
| difficulty | `難度` | `難度 一.〇` |
| ladder heading / current | `段階` / `いま` | いま is `c.accent` |
| lifetime / single-set / last | `のべ回数` / `一度に` / `最後` | |
| used by | `使われている型` / `どの型にも入っていません` | |
| per-rep pace | `二.〇秒/回` | meta only |
| patterns | `押す` `引く` `しゃがむ` `股関節` `体幹` `移動` `跳ぶ` | H_PUSH V_PULL SQUAT HINGE CORE LOCOMOTION PLYO |
| ladder members | `壁腕立て` `斜め腕立て` `足上げ腕立て` `アーチャー腕立て` `片手腕立て` | 0.2 / 0.4 / 1.3 / 1.6 / 2.5 |
| delete (has history) | `「シンディ」を削除しますか` / `これまでの六回の記録は残ります。型だけが一覧から消えます。` / `削除` / `やめる` | |
| delete (no history) | `やった記録はありません。完全に消えます。` / `完全に削除` | |

### Records

| purpose | string | notes |
|---|---|---|
| records / history / charts / PR titles | `記録` / `これまで` / `移り変わり` / `最高` | utsurikawari — how things shift |
| PR tabs | `型ごと` / `動きごと` | |
| PR: hardest reached | `いちばん上` | `いちばん上 足上げ腕立て` |
| PR: structure changed | `中身が変わっています` | |
| PR: empty explanation | `回数を数えた種目だけ ここに出ます` | why 動きごと can be empty |
| PR: see all exercises | `すべての種目` | |
| streak broken | `連続は とぎれています` | never `〇日 連続` |
| forgiveness used | `ゆるし 一回 使いました` | **never states how many remain** |
| tiles | `今月` / `活動時間` / `これまで` | |
| month caption | `十二日 ・ 六月` / `この月は 〇日` | the latter is not the empty state |
| recent heading | `最近` | |
| detail entry | `詳しく` | |
| chart preview heading | `週ごと` | |
| records empty | `まだ 記録はありません` + `型をえらぶ` | |
| history empty (filtered) | `この型は まだ やっていません` | |
| history: retry a page | `もう一度` | footer, `c.accent` |
| history menu | `記録を削除` / `この型を見る` | |
| delete session | `この記録を削除しますか` / `元に戻せません。` | |
| session detail actions | `もう一度` / `型を見る` | |
| was a PR then | `当時の自己最高` | `c.inkFaint` — since beaten |
| routine archived | `この型は削除されています` | もう一度 disabled |
| no snapshot | `当時の内容は残っていません` | backup-restore case only |
| chart ranges | `十二週` / `二十六週` / `一年` | |
| chart headings | `週ごとの回数` / `活動時間` / `積み上げ` | tsumiage — what you have stacked up |
| chart captions | `いちばん多い週 六回 ・ ならして 三.四回` / `合計 二千四百分 ・ ならして 二百分/週` / `日ごとの積み上げと 七日平均 ・ 目安` | 目安 mandatory |
| chart suppressed | `二十八日ぶん たまると 出ます` | the 28-day gate |
| this week | `今週` | |
| history subtitle | `八十六回 ・ 二千四百分` | |
| store full / unreadable | `空き容量が足りません` / `記録を読めません` + `もう一度` | |
| generic close | `とじる` | already used in `EventComposeScreen` |

**Numerals.** Counts, durations and coefficients render in kanji (`JapaneseDate.kanji`, extended by
`kanjiExtended` for 100..9999). Exceptions: the wheel mid-spin and any value above 9999 use arabic — a
kanji column changing under the finger is unreadable, the same argument design §14 Q3 makes about the
countdown. Coefficients render as `一.〇` / `〇.五` (kanji digits with a full stop), because a bare
`1.0` in a Mincho line reads as foreign matter.

---

## 7. Pure-logic inventory

All Android-free, in `app/src/test/java/io/eddiegulay/tempo/gym/`, matching `JapaneseDateTest`,
`NotificationGroupingTest`, `TategakiTest`, `CalendarFeedbackTest`.

| file | functions | notes |
|---|---|---|
| `LibraryFilters.kt` | `foldKana`, `matchRoutine`, `matchExercise`, `durationBucket`, `applyFilters`, `rankFrequent`, `uniqueName` | kana folding deserves its own test class |
| `RoutineTier.kt` | `derivedTier` | three boundary cases pinned |
| `RoutineEstimate.kt` | `estimateRoutine`, `estimateLabel`, `paceEstimateSec` | uses design §3.5's per-rep table |
| `PatternWarning.kt` | `adjacentPatternClashes`, `clashCopy` | **must** cover the round-wrap pair |
| `BuilderDraft.kt` | `moveItem`, `reorderShift`, `migrateDraft`, `canSave`, `isDirty`, `structuralHash`, `allowedMeasures`, `defaultPrescription`, `restOptions`, `repOptions`, `secondOptions` | |
| `EngineRows.kt` | `engineRows`, `bestTilesFor`, `stepFor` | one case per engine |
| `Streak.kt` | `currentStreak`, `streakCopy` | forgiveness, month boundaries, today-never-breaks, guarded-clock input |
| `InkDensity.kt` | `inkLevel`, `loadScaleFrom`, `monthCells`, `dayAt`, `gridSemantics` | 4/5/6-row months, leap February |
| `SessionLoad.kt` | `sessionLoad`, `monotony`, `volumeUnits` | Foster 4/7/9; monotony null when sd == 0 |
| `Bests.kt` | `bestMetricFor`, `routineBests`, `movementBests`, `isBetter`, `bestValueCopy`, `currentRung`, `reachedRungs` | partial sessions never set a best |
| `HistoryPaging.kt` | `groupByMonth`, `nextCursor`, `shouldPrefetch`, `mergePage` | composite `(started_at, id)` cursor |
| `RecordCopy.kt` | `comparisonCopy`, `prChip`, `breakdownRow`, `partialChipCopy`, `ensoSweep`, `heroTime`, `sessionRowLines`, `historySubtitle` | |
| `ChartGeometry.kt` | `barGeometry`, `linePoints`, `trailingMean`, `isDense`, `rangeWeeks`, `chartCaption`, `chartSemantics`, `meanExcludingPartial` | floats in, geometry out — **this is what makes a library-free chart testable** |
| `GymFaultCopy.kt` | `gymFaultCopy` | mirrors `CalendarFeedbackTest` exactly |
| `Numerals.kt` | `kanjiExtended`, `coefficientLabel`, `durationKanji` | extends `JapaneseDate.kanji` past 99 |
