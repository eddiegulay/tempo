# 鍛錬 — implementation plan and page tree

**This file is the grounding point.** Every implementation agent starts here, then reads the one part
file that owns its pages. `.planning/exercise-design.md` remains the *product* rationale — why the
ensō is the timer, why Cindy is 5/10/15, why a rep slide never auto-advances. This directory is the
*implementable* form of it, and where the two disagree, **this directory wins** (§2 lists every
divergence and why).

| file | owns |
|---|---|
| `00-plan.md` | this file — page tree, resolved decisions, prerequisites, build order |
| `01-shell.md` | navigation architecture, `GymViewModel`, `GYM.HOME`, `GYM.SETTINGS`, `GYM.SAFETY` |
| `02-data.md` | SQLite schema, migrations, seed data, repository API, the hard queries |
| `03-player.md` | `GYM.SESSION.*`, the timeline compiler, the state machine, the cue engine, lifecycle |
| `04-library-records.md` | `GYM.LIBRARY.*`, `GYM.RECORDS.*`, Canvas drawing, the Japanese string tables |

---

## 1. The two decisions that reshaped the design

### 1.1 The gym is an app within the app

鍛錬 is **not** more entries on the launcher's flat `Screen` enum. It is a shell that takes the whole
window, with its own back stack, its own tab bar, its own ViewModel, and its own lifetime. The
launcher gains exactly **one** enum value:

```kotlin
enum class Screen { Home, Search, Notifications, Filter, Focus, Calendar, EventCompose, Gym }
```

`Screen.Gym` means only *"the window currently belongs to the gym."* Where you are inside it is not
the launcher's business — exactly as `Screen.Focus` does not encode "clock vs pomodoro."

### 1.2 Persistence is SQLite

Platform `android.database.sqlite.SQLiteOpenHelper` — raw `SQLiteDatabase` / `Cursor` /
`ContentValues`. **Not Room**: it needs the KSP plugin and a new dependency, and `CONTRIBUTING.md`
forbids new third-party dependencies. Design §7.3's two JSON ledgers are superseded.

**Consequence nobody expected: `minSdk 29` pins SQLite to 3.28.** That rules out `STRICT` tables,
`RETURNING`, `DROP COLUMN`, generated columns, `sqrt()`, and — decisively — **JSON1**, which is only
enabled from API 30. The JSON-blob-column shortcut is not merely inelegant here; it is unqueryable.

**User preferences stay in DataStore**, not SQLite. They need the synchronous first-frame read that
`ThemeRepository.loadInitialSettings()` provides (`ThemeRepository.kt:59`), which an IO-dispatched
SQLite read cannot give without a flash of defaults. The single exception is `training_plan`, which
lands in the DB because it is date-versioned and must not retroactively rewrite a computed streak.

---

## 2. Divergences from `exercise-design.md`, and conflicts between parts

Every row here is a decision already made. Do not re-litigate them in implementation; if one turns
out to be wrong, change it here first.

| # | Topic | Resolution | Why |
|---|---|---|---|
| 1 | **Keep-screen-on** | The **gym owns its own** `DisposableEffect`, releasing on `ON_PAUSE`. Design §8.2 and `03-player.md` §E.4 both said "extend `TempoApp.kt:94`" — **overruled**. | Whether the gym wants the screen awake depends on the `画面を消さない` preference *and* on whether a session is live. `TempoApp` cannot read either without instantiating `GymViewModel` on every launcher frame, which destroys the lazy-open guarantee (§2.3). Releasing on `ON_PAUSE` rather than on disposal is also the correct fix for the leak class commit `1f49dfc` addressed — disposal never runs when HOME is pressed and Tempo is not the default launcher. |
| 2 | **Recon Ron's 18-step table** | Use the **primary-source table in `02-data.md` §F.2**, transcribed from Pasieka's 1981 *Marine Corps Gazette* article. A second, plausible-looking table was generated during planning; it is rejected. | Both tables sum correctly and share endpoints (26 → 60, +2/step), but their middle rows differ. The source table increments in a clean rotation; the regenerated one loses that pattern around steps 15–17. **Verify against the source PDF before seeding.** §9 of the design doc refuses to invent numbers for RECONDO; the same restraint applies to a table we already have the source for. |
| 3 | **Package name** | `io.eddiegulay.tempo.gym`, with UI in `ui/gym/`. Not `exercise/`. | Three of the four part specs converged on `gym`; the shell defines the module boundary and it is the shell's package. Design §0's `exercise/` is superseded. |
| 4 | **Fault type** | One `sealed interface TempoFault` in `data/`, with `CalendarFault : TempoFault` and `GymFault : TempoFault`. `Loadable.Failed` takes `TempoFault`. | Two parts independently derived the same widening. `faultCopy` (`ui/CalendarFeedback.kt:49`) widens to `TempoFault` and gains gym branches, so `FaultStrip`/`FaultPanel` render gym faults with **zero new chrome**. Name it `GymFault`, not `ExerciseFault`. |
| 5 | **Built-in routines: constants or rows?** | **Rows, seeded from Kotlin constants.** Design §7.3's "Kotlin constants" is superseded for routines. | A `session` needs a foreign key to what it performed; a session pointing at a `val` has no referential integrity. The home list joins routines against session aggregates and PRs in one query — against a Kotlin list that is N+1. And copy-on-write user edits need built-ins and user routines to be the same shape. `BuiltInCatalog.kt` survives as the reviewable seed source. |
| 6 | **Exercise catalogue access** | Seeded into the DB **and** held in an in-memory map loaded once at repository construction, exposed as a **synchronous** `ExerciseCatalog.byId()`. | The data part needs FK integrity; the library part needs the station picker to filter on every keystroke with no Loading state. Both are satisfiable: 18 rows in memory costs nothing, and the picker page then genuinely has no Loading and no Error state — which is worth stating so nobody adds a spurious spinner. |
| 7 | **`GYM.SESSION.PREFLIGHT` ≡ `GYM.LIBRARY.DETAIL`** | **One page: `GYM.LIBRARY.DETAIL`.** `PREFLIGHT` is deleted as a route. | Both parts specced "the routine detail page with a 始める button." `LIBRARY.DETAIL` is the richer of the two (provenance, tier chips, PRs, attempt history, full action set); `PREFLIGHT` contributes its **start-guard** and **session-insert-before-navigate** logic, which move into `LIBRARY.DETAIL`'s 始める action. `03-player.md`'s PREFLIGHT spec is retained only for those two mechanisms. |
| 8 | **`GYM.SESSION.RESUME_PROMPT`** | **Not a route.** A modal presented over `GYM.HOME` (and over `GYM.LIBRARY.DETAIL` when it fires as a start-guard). | The shell models it as `GYM.HOME`'s `StaleSession` state; the player models it as a route. A modal is right — it is a question, not a place, and dismissing it must leave the つづき banner intact with nothing decided. It keeps the player's `resumability()` logic verbatim. |
| 9 | **`GYM.SESSION.COMPLETE` vs `GYM.RECORDS.SESSION_DETAIL`** | One component, `ui/gym/RecordSummary.kt`, parameterised by `RecordMode { Live, Historical }`. See `04-library-records.md` for the 11-row difference table. | Design §5 says "One component, two entry points." `RecordMode` is what makes that true without either page lying — the historical view must not replay the ensō ceremony, must not show today's streak, and must demote a PR chip that has since been beaten. |
| 10 | **Session ↔ routine binding** | `session.routine_version_id` FK to an **immutable** `routine_version`, **plus** a denormalised `routine_name` on the session row. | Three parts independently demanded "the session must not be re-interpreted by a later edit." The version FK is the mechanism; the denormalised name is for the one case the FK cannot serve — rendering a つづき banner for a session whose routine was deleted. |
| 11 | **`VibrationAttributes` API level** | `VibrationAttributes` on **API 33+**; the `AudioAttributes` overload on 29–32. Design §3.6's "31+" is wrong. | The class landed in API 30, but `vibrate(VibrationEffect, VibrationAttributes)` is only public from 33. At `minSdk 29` both paths are required. Both route as `USAGE_ALARM`, so cues survive silent mode either way. |
| 12 | **Completion tone** | Synthesize ~900ms of PCM16 (660/550/440 Hz, 20ms raised-cosine envelopes) through a one-shot `AudioTrack`. Fallback: `TONE_PROP_NACK`. | `ToneGenerator` has no pitch control, so design §10's "descending three-note" is not expressible with it. **Do not add an audio asset file** — that is a dependency in all but name and defeats the constraint the tone choice was made under. |
| 13 | **Weighted volume formula** | `volumeUnits(exercise, actualReps, actualDurationMs)`, frozen onto each result row. Design §7.4's bare `Σ reps × coefficient` is incomplete. | The bare formula scores a 60-second plank as **zero** — `actual_reps` is NULL for duration stations. That makes 七分間, which is one-third holds, look like half a workout. Isometrics convert at the exercise's own `secondsPerRep`, redefined for holds as "seconds that count as one unit." |
| 14 | **Calendar bucketing** | Computed in **Kotlin at write time** and stored as `local_date` / `local_week_start`. Never `date()` or `strftime()` in SQL. | SQLite's date functions are UTC. A 23:30 session in UTC+9 would land on the previous day, and `strftime('%W')` uses non-ISO week numbering. This is the most common bug in fitness-history code. |
| 15 | **Corruption handling** | Custom `DatabaseErrorHandler`: quarantine the file, raise a durable flag in DataStore, report `GymFault.StoreCorrupt`. | The default handler **deletes the database**, after which the history screen shows an empty state indistinguishable from a new install. That is a lie the user will believe, and it is precisely what the `Loadable` doctrine exists to prevent. |
| 16 | **Design §2's header actions** | `設定` and `作る`. 記録 is now a tab, so it leaves the header. | The tab bar (§3.2) subsumes it. Accent stays rationed to one word per header, as 予定 does. |
| 17 | **Pavel's 30-day table** | **Not seeded.** The `progression_program` row exists with zero step rows; no routine references it. | Circulating transcriptions disagree. Design §9's refusal to invent RECONDO numbers applies identically. Lands as `SeedCatalog.VERSION = 2` when sourced — which needs **no migration**, because the seed counter is separate from the schema counter. |
| 18 | **七分間's duration** | Render the computed **475s (≈ 約八分)**. Note the discrepancy in the catalog comment. | 12×30 + 11×10 + 5 prepare = 475. "7-minute workout" is ACSM's own branding rounding, not our arithmetic. |
| 19 | **`training_plan`** | New table, date-versioned, with a documented fallback. **Needs product sign-off** (§7). | Design §5.2's streak is literally unimplementable without a definition of "the plan." |
| 20 | **Two new exercises** | Add `crunch` (クランチ) and `pushup_rotation` (回旋腕立て伏せ) to §12's library. | The ACSM 七分間 circuit that §9 forbids reordering contains both. Without them the shipped circuit is not the published one. |

---

## 3. The page tree

**17 pages, 4 modals, 3 tabs, 1 launcher enum value.**

```
LAUNCHER (existing)
│
└─ Home ── long-press clock ──► ModeDialog ──┬── 集中 ──► Screen.Focus   (existing, unchanged)
                                             └── 鍛錬 ──► Screen.Gym
                                                            │
╔═══════════════════════════════════════════════════════════▼══════════════════════════════════╗
║ GYM SHELL — own back stack (List<GymRoute>), own tab bar, own GymViewModel                    ║
║ Back at any tab root that is not 鍛錬 rebases to 鍛錬. Back at 鍛錬 exits to LAUNCHER.Home.     ║
╠═══════════════════════════════════════════════════════════════════════════════════════════════╣
║                                                                                               ║
║  TAB 1 ─ 鍛錬 ──► GYM.HOME ················································· tab bar visible   ║
║    │     resume banner · frequently used · built-in preview · user routines                   ║
║    ├── modal ─► GYM.HOME.RESUME_PROMPT ──── 続ける / 記録する / 捨てる ······· stale session    ║
║    ├──────────► GYM.SETTINGS ───► GYM.SAFETY ······························ tab bar hidden    ║
║    ├──────────► GYM.LIBRARY.DETAIL                                                            ║
║    ├──────────► GYM.LIBRARY.BUILDER                                                           ║
║    └──────────► GYM.SESSION.PREPARE                        (resume a live session)            ║
║                                                                                               ║
║  TAB 2 ─ 型 ───► GYM.LIBRARY.INDEX ········································ tab bar visible   ║
║    │     search · tier/engine/duration filters · よく使う · 型 · 自分の型                       ║
║    ├──────────► GYM.LIBRARY.DETAIL ······································· tab bar visible   ║
║    │             │  structure · provenance · tier chips · bests · attempts                    ║
║    │             ├─► GYM.SESSION.PREPARE          ◄── 始める  (inserts the session row first)  ║
║    │             ├─► GYM.LIBRARY.BUILDER          ◄── 編集 / 写して作る                        ║
║    │             ├─► GYM.LIBRARY.EXERCISE_DETAIL  ◄── tap a station                           ║
║    │             ├─► GYM.RECORDS.SESSION_DETAIL   ◄── tap an attempt                          ║
║    │             ├─► GYM.RECORDS.HISTORY          ◄── すべて見る (routine-filtered)            ║
║    │             └─ modal ─► delete confirm (copy branches on session count)                  ║
║    ├──────────► GYM.LIBRARY.BUILDER ······································ tab bar HIDDEN    ║
║    │             │  name · engine · drag-ordered stations · rests · rounds · live estimate    ║
║    │             ├─► GYM.LIBRARY.STATION_PICKER ··························· tab bar HIDDEN    ║
║    │             │      exercise list by pattern · measure chips · value wheel · 削除          ║
║    │             └─ modal ─► discard prompt (only when dirty)                                 ║
║    └──────────► GYM.LIBRARY.EXERCISE_INDEX ································ tab bar visible   ║
║                  └─► GYM.LIBRARY.EXERCISE_DETAIL ·························· tab bar visible   ║
║                        cue · bests · progression ladder · routines using it                   ║
║                        (ladder taps REPLACE, never push — one back always exits)              ║
║                                                                                               ║
║  TAB 3 ─ 記録 ──► GYM.RECORDS.INDEX ······································· tab bar visible   ║
║    │     month ink-grid · streak with forgiveness · 3 tiles · sparkline · recent               ║
║    ├──────────► GYM.RECORDS.HISTORY ······································ tab bar visible   ║
║    │             │  month-grouped, keyset-paged, long-press to delete                         ║
║    │             └─► GYM.RECORDS.SESSION_DETAIL ··························· tab bar visible   ║
║    │                   RecordSummary(mode = Historical) · editable rating · もう一度            ║
║    ├──────────► GYM.RECORDS.PR ············································ tab bar visible   ║
║    │             型ごと / 動きごと tabs                                                        ║
║    └──────────► GYM.RECORDS.CHARTS ········································ tab bar visible   ║
║                  週ごとの回数 (bars) · 活動時間 (line) · 積み上げ (ticks + 7-day mean)          ║
║                                                                                               ║
║  ── SESSION PLAYER ── immersive, portrait-locked, tab bar hidden, own BackHandler ──           ║
║                                                                                               ║
║      GYM.SESSION.PREPARE ──► WORK ⇄ REST ──► COMPLETE                                         ║
║           支度                REPS ⇄ REST                                                      ║
║             │                  │      │                                                       ║
║             └──────────────────┴──────┴──► PAUSED ⇄ (back to phase)                           ║
║                                    └──────► QUIT_SHEET ─┬─► COMPLETE   (ここまでを記録する)     ║
║                                                         ├─► GYM.HOME   (記録せずに終える ×2)    ║
║                                                         └─► (back)     (つづける)              ║
║      GYM.SESSION.COMPLETE ─── 閉じる ──► GYM.HOME   (pops the whole player stack)              ║
║                           └── もう一度 ──► GYM.LIBRARY.DETAIL                                  ║
╚═══════════════════════════════════════════════════════════════════════════════════════════════╝
```

### 3.1 Route model

```kotlin
enum class GymTab(val label: String) { Train("鍛錬"), Library("型"), Records("記録") }

sealed interface GymRoute {
    val tab: GymTab? get() = null          // non-null only for the three tab roots
    val immersive: Boolean get() = false   // whole window, no tab bar, own insets
    val singleTop: Boolean get() = true

    data object Home    : GymRoute { override val tab = GymTab.Train }
    data object Library : GymRoute { override val tab = GymTab.Library }
    data object Records : GymRoute { override val tab = GymTab.Records }

    data class RoutineDetail(val routineId: String) : GymRoute
    data class Builder(val editingId: String? = null) : GymRoute {
        override val immersive = true
        override val singleTop = false     // 写して作る from a detail may stack twice
    }
    data class StationPicker(val index: Int?) : GymRoute { override val immersive = true }
    data object ExerciseIndex : GymRoute
    data class ExerciseDetail(val exerciseId: String) : GymRoute
    data class Session(val routineId: String, val resume: Boolean = false) : GymRoute {
        override val immersive = true
    }
    data class Record(val sessionKey: String) : GymRoute
    data class History(val routineId: String? = null, val anchorMonth: YearMonth? = null) : GymRoute
    data object Bests : GymRoute
    data object Charts : GymRoute
    data object Settings : GymRoute
    data object Safety : GymRoute
}
```

**Invariant:** the stack is never empty and `stack.first().tab != null`. Every mutation is a pure
function (`back`, `push`, `selectTab`, `tabBarVisible`), so the whole navigation model is JVM-testable
with no Android on the classpath — the same bargain the repo already makes for `groupByApp` and
`layoutTategaki`.

**Tab bar visibility is one rule:** `stack.size == 1`. No per-page opt-outs to forget.

### 3.2 The tab bar must not be mistaken for the launcher dock

The dock is a floating `RoundedCornerShape(percent = 50)` pill of three line glyphs on `c.card` with a
0.5.dp accent border, frosted with `wetPaper` over sub-screens. The gym bar inverts all of it:
**seated and full-width** (not floating), **words not glyphs** (鍛錬 / 型 / 記録, Mincho 13.sp ls 3.sp),
**a top hairline** in `c.hair`, **no frosting**, 64.dp tall plus `navigationBarsPadding()`, active tab
in `c.accent` with a 4.dp dot above the label. One glance distinguishes the two modes.

Lists inside the shell use `contentPadding(bottom = 88.dp)` — smaller than the dock's 96.dp because
the bar is seated rather than floating.

---

## 4. Behaviour and action reference

One row per page. The **detail** column names the part file that carries its full spec — layout with
tokens, every state, the complete actions table, edge cases, accessibility, and testable pure logic.

| Page | Entered from | Primary actions | Writes | Detail |
|---|---|---|---|---|
| `GYM.HOME` | ModeDialog · tab · back-rebase · pops | tap routine → detail · 続ける (resume) · 設定 · 作る · すべて見る · long-press → 写して作る/編集/削除 | `touchRoutine`, and the stale-prompt outcomes | `01` §B |
| `GYM.HOME.RESUME_PROMPT` (modal) | cold start with an open session · つづき banner · 始める start-guard | 続ける · 記録する · 捨てる · dismiss (decides nothing) | commit-as-partial or delete | `01` §B, `03` §A |
| `GYM.SETTINGS` | `GYM.HOME` 設定 | toggle 振動/音/音声 · 目安で自動的に進む · 支度の長さ · rest defaults · 単位 · 画面を消さない · 安全のために | every row writes on tap, DataStore | `01` §B |
| `GYM.SAFETY` | `GYM.SETTINGS` · first-run note | read · back | `setSafetyNoteAcknowledged` | `01` §B |
| `GYM.LIBRARY.INDEX` | tab · builder save · detail back | search · filter by tier/engine/duration · tap card · long-press menu · 作る · 種目を見る | `setFavourite` | `04` §2 |
| `GYM.LIBRARY.DETAIL` | index · home card · session detail · PR row · builder save | **始める** (inserts session, then navigates) · tier chips · 編集 · 写して作る · よく使う · 削除 · tap station · tap attempt | duplicate/favourite/archive/purge, and the session insert | `04` §2 + `03` §A |
| `GYM.LIBRARY.BUILDER` | index 作る · detail 編集 · duplicate | name · engine picker · **drag-reorder stations** · tap station → picker · rest/round wheels · 保存 · やめる | `save(routine)` — exactly once, on 保存 | `04` §2 |
| `GYM.LIBRARY.STATION_PICKER` | builder ＋加える or tap a station | search · select exercise · 回数/秒数/限界まで · value wheel · 保存 · 削除 | draft only, never the DB | `04` §2 |
| `GYM.LIBRARY.EXERCISE_INDEX` | library footer · PR page | search · tap row | — | `04` §2 |
| `GYM.LIBRARY.EXERCISE_DETAIL` | exercise index · station row · PR row · breakdown row | tap a ladder rung (**replaces**, never pushes) · tap a routine using it | — | `04` §2 |
| `GYM.SESSION.PREPARE` 支度 | 始める · resume | ▷ skip · ┃┃ pause · ✕ quit | clock anchors | `03` §A |
| `GYM.SESSION.WORK` 運動 | prepare · rest · reps · paused · skip | auto-advance at 0 · ▷ · ◁ (1st restarts, 2nd within 2s steps back) · ┃┃ · ✕ | one `SegmentResult` + clock, per transition | `03` §A |
| `GYM.SESSION.REPS` 運動・回数 | as WORK, when the segment is open | **済** (the gate) · long-press 済 → rep wheel · ▷ · ◁ · ┃┃ · ✕ | `SegmentResult` with real duration and actual reps | `03` §A |
| `GYM.SESSION.REST` 休息 | any work segment closing into a rest | ＋二十秒 · とばす · ▷ · ◁ · ┃┃ · ✕ (＋二十秒 and skip **disabled** on a mandated rest) | `SegmentResult` + `addedMs` | `03` §A |
| `GYM.SESSION.PAUSED` 休止 | ┃┃ from any live phase · 30-min stall guard | 続ける (via 3s prepare if paused > 60s) · ◁ / ▷ while staying paused · ✕ | pause/resume clock anchors | `03` §A |
| `GYM.SESSION.QUIT_SHEET` (modal) | ✕ or back from any live phase | ここまでを記録する · 記録せずに終える (×2 when work exists) · **つづける** (the escape — never やめる) | finish-as-partial, or delete | `03` §A |
| `GYM.SESSION.COMPLETE` 記録 | last segment · quit sheet · fail-out · cap · reconcile past the end | rating (asked before the accolades appear) · もう一度 · 予定に入れる (Phase 3) · 閉じる | `rateSession` | `03` §A |
| `GYM.RECORDS.INDEX` | tab · session complete | month pager · tap grid → history · 詳しく → charts · 最高 → PR · tap a recent row | — | `04` §3 |
| `GYM.RECORDS.HISTORY` | records index · routine detail · PR row | scroll (keyset paging) · tap row · long-press → 記録を削除 / この型を見る | `delete(key)` | `04` §3 |
| `GYM.RECORDS.SESSION_DETAIL` | history · records index · attempt row · PR row | tap a rating (editable, and re-tap un-rates) · tap breakdown row · もう一度 · 型を見る · 記録を削除 | `setRating`, `delete` | `04` §3 |
| `GYM.RECORDS.PR` | records index · routine detail tiles | 型ごと / 動きごと · tap row · tap date → session · tap count → history | — | `04` §3 |
| `GYM.RECORDS.CHARTS` | records index 詳しく | range chips 十二週/二十六週/一年 · これまでを見る. **Charts are not tappable** — no tooltips, no scrubbing | — | `04` §3 |

### 4.1 Rules that hold on every page

1. **`Loadable` doctrine: loading ≠ empty ≠ failed.** An unreadable store never renders 記録はありません.
   Every page below names its Loading, Empty, and Failed states separately, and none of them share a
   composable.
2. **A partial session is a real session.** Same treatment wherever it appears: a `途中まで` chip, no PR
   chip, no ensō ceremony, the honest numbers. Dignified, not punitive.
3. **Never `stickyHeader`.** Month and section headers are plain items — the calendar page sets the
   precedent and `stickyHeader` is still experimental foundation API.
4. **Long-press is invisible to TalkBack.** Every long-press affordance declares
   `onLongClick(label = …)` **and** exposes its menu items as `customActions`. This follows the
   explicit note at `NotificationsScreen.kt:204` and is the single most-repeated a11y requirement in
   this plan.
5. **No countdown or ticking value is an `accessibilityLiveRegion`.** Per-second announcements are
   unusable and steal the TTS engine the cues need. Announce at phase transitions only.
6. **Every colour comes from `LocalTempoColors`.** Not one hardcoded value in any draw call, or Sumi
   breaks silently.
7. **All UI copy is Japanese**, hard-coded in Kotlin. `strings.xml` holds only `app_name` and stays
   that way.

---

## 5. Cross-cutting prerequisites

These land **before** any page work, in one preparatory PR. Three of the four parts independently
depend on them, so doing them once up front is strictly cheaper than three merge conflicts.

| # | Change | Why | Touches |
|---|---|---|---|
| P1 | **Widen `Loadable.Failed` to `TempoFault`** | Two features now need it. `faultCopy` widens and gains gym branches; `FaultStrip`/`FaultPanel` then work on gym pages unchanged. | `calendar/CalendarOutcome.kt`, `ui/CalendarFeedback.kt`, `ui/CalendarScreen.kt`, `ui/EventComposeScreen.kt`, `CalendarFeedbackTest.kt` |
| P2 | **Extract `TempoWheel`** | `EventComposeScreen.kt:496` is `private` and `LocalDateTime`-specific. Needed by `GYM.SETTINGS`, `GYM.LIBRARY.BUILDER`, `GYM.LIBRARY.STATION_PICKER`, and the player's rep-adjust sheet. Keep `WheelColumn`'s skip-first-emission guard verbatim — "mounting is not choosing" is a real bug fix, not an accident. | new `ui/TempoWheel.kt`; one call site renamed to `TempoDateTimeWheel` |
| P3 | **Extract `CycleDots`** | `FocusScreen.kt:210` is `private`, hardcodes 4 dots at 9.dp with `inkFaint` pending. Parameterise `total`/`dotSize`/`pendingColor`; **Focus's call site keeps its current defaults so Focus is pixel-unchanged.** | new `ui/CycleDots.kt`, `ui/FocusScreen.kt` |
| P4 | **Parameterise `Enso`** | `HomeScreen.kt:162` is `private` and derives its radius from `size.minDimension`. The player needs `sweepAngle`, an explicit colour, and a fixed 220.dp / 3.dp geometry. | new `ui/Enso.kt`, `ui/HomeScreen.kt` |
| P5 | **`kanjiExtended(n)`** | `JapaneseDate.kanji` covers 0..99. Rep counts, volumes and lifetime totals exceed that. Above 9999, fall back to arabic — kanji at that magnitude is unreadable. | `data/JapaneseDate.kt` + test |
| P6 | **`ModeDialog`** | Replaces `FocusConfirmDialog`; `_pendingFocus` → `_pendingMode`, `confirmFocus` → `confirmMode(LauncherMode)`. The Home long-press semantics label changes to `モードを選ぶ`. | `LauncherViewModel.kt`, `ui/HomeScreen.kt`, `ui/FocusConfirmDialog.kt` |

---

## 6. Build order

Each phase is independently shippable and independently useful. Phase 1 is the whole of the original
ask.

**Phase 0 — prerequisites.** P1–P6 above. No user-visible change except the mode dialog.

**Phase 1 — the player.** Shell + `GYM.HOME` + `GYM.LIBRARY.INDEX`/`DETAIL` + the full session player
+ `GYM.SESSION.COMPLETE`. Engines `INTERVAL_CIRCUIT` and `FIXED_SETS`. Schema v1 complete (all tables —
the schema is not phased, only the UI is). Seed 七分間, タバタ, リーコン・ロン. Haptics + tones.
**No new permissions, no new dependencies, no foreground service.**

**Phase 2 — authoring and the rest of the engines.** `GYM.LIBRARY.BUILDER`, `STATION_PICKER`,
`EXERCISE_INDEX`, `EXERCISE_DETAIL`, `GYM.SETTINGS`, `GYM.SAFETY`. Engines `AMRAP`, `FOR_TIME`,
`FOR_TIME_WITH_REST`, `EMOM`, `EMOM_ASCENDING`. Seed シンディ (+ やさしい), チェルシー, バーバラ,
マーフ, デス・バイ — `SeedCatalog.VERSION = 2`, **no migration needed**.

**Phase 3 — records with depth.** `GYM.RECORDS.*` in full: month ink-grid, forgiving streak, PR page,
the three charts, session load and monotony. `予定に入れる` calendar hand-off. The `health` foreground
service and its notification controls (`FOREGROUND_SERVICE_HEALTH` + `ACTIVITY_RECOGNITION` +
`POST_NOTIFICATIONS`, an R8 keep rule, and a Play Console declaration with a demo video).

**Phase 4 — coaching.** Progression auto-advance for `FIXED_SETS`, double-progression on custom
routines, the 10%-per-week ramp governor, deload prompts, ACWR as a governor (**never** displayed as
risk).

---

## 7. Open questions — these need a decision, not an implementation

1. **`training_plan` (blocking Phase 3's streak).** Design §5.2 promises "days you honoured the plan"
   but never defines the plan. `02-data.md` §A.9 proposes a date-versioned `days_mask` with a
   fallback rule — *a day is honoured if you trained, or if you trained the day before* — which
   delivers §5.2's promise with no plan model at all. Ship the fallback and defer the picker, or
   build a picker into `GYM.SETTINGS`?
2. **Speech cues default.** Design §14.2 left this open; `03-player.md` proposes keeping the default
   **off** but **auto-enabling when TalkBack is active** (`isTouchExplorationEnabled`). That resolves
   the accessibility case without changing the default for everyone else. Accept?
3. **Recon Ron's table.** §2 row 2 — verify the transcription against the source PDF before seeding.
   Whoever does this should record the check in the catalog file's comment.
4. **Numerals.** Counts render kanji (二十回), countdowns render arabic (`0:23`), wheels render arabic
   mid-spin. Deliberate and documented, but worth one sanity check against how the flip clock already
   reads.
5. **How much does 鍛錬 owe Home?** Focus leaves no trace. Should a streak? `calendar-design.md`'s rule
   1 fought to keep that corner quiet, and the next-event cluster already won it.
