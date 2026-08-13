# 鍛錬 — Part 3: the session player

Read `00-plan.md` first. Merge decisions in its §2 override anything here — in particular **§2 row 1
(keep-screen-on is gym-owned, not `TempoApp.kt:94`)** and **§2 row 7 (`PREFLIGHT` is deleted; its
start-guard and session-insert move into `GYM.LIBRARY.DETAIL`'s 始める)**.

Owns: `GYM.SESSION.*`, the timeline compiler, the state machine, the cue engine, and lifecycle.

---

## Shared vocabulary

```kotlin
enum class Phase { PREPARE, WORK, REPS, REST, COMPLETE }
enum class RestKind { STATION, ROUND, MANDATED, EMOM_REMAINDER }
enum class Gate { AUTO, MANUAL, AUTO_AT_CAP }
enum class ScalingTier { EASY, RX, HARD }              // やさしい / 基本 / きつい
enum class Rating(val cr10: Int) { EASY(4), JUST_RIGHT(7), HARD(9) }
```

Every page renders on `Modifier.tempoBackground(colors)` with `LocalTempoColors.current` as `c`, fonts
`Mincho` / `Gothic`. Kanji numerals come from `JapaneseDate.kanji` (extended by `kanjiExtended`,
prerequisite P5) — do not write a second one. **Countdown numerals stay arabic**; design §14.3 settles
this deliberately.

The player is **portrait-locked and immersive**. Reuse `FocusScreen.kt:86-113` verbatim for bar-hiding
and the `OnWindowFocusChangeListener` re-hide fix, but set `SCREEN_ORIENTATION_PORTRAIT` instead of
Focus's landscape lock, restoring `originalOrientation` on dispose exactly as Focus does. The phone is
on the floor and you look down at it from a plank.

---

## A. Page specs

### The start path (formerly PREFLIGHT)

`GYM.LIBRARY.DETAIL` owns the routine-detail UI (see `04-library-records.md` §2). Its 始める button
carries these two mechanisms, which are the player's:

**1. Start guard.** If `resumableSession()` returns non-null for **any** routine, do not silently
clobber it. Present the resume prompt (§A.7) as a modal over the detail page; once the user picks
記録する or 捨てる, the start proceeds automatically.

**2. Insert before navigating.** On 始める, in this order, *before* any navigation:

```kotlin
// 1. Compile first — a routine with zero stations must fail here, not mid-session.
val timeline = compile(routine, tier, ExerciseCatalog.byId)
// 2. Insert. A session that exists in the UI but not in the DB is the one state process death
//    cannot recover from, so navigation is blocked on this returning.
val sessionId = repository.startSession(routineId, tier, roundsPlanned).valueOrThrow()
// 3. Remember the tier for next time.
repository.setLastTier(routineId, tier)
```

Budget: a single `INSERT`, sub-millisecond. Show no spinner under 250ms, then replace the 始める label
with a non-interactive 支度 in `c.inkFaint`. Re-entrancy guard (`startInFlight`) so a double-tap cannot
create two sessions — though `idx_session_live` would refuse the second anyway.

---

### GYM.SESSION.PREPARE — 支度 (get ready)

**Purpose** — Five seconds to put the phone down and get into position.

**Entered from** — `GYM.LIBRARY.DETAIL` 始める · the resume prompt's 続ける (3s, not 5s).
**Exits to** — `WORK` / `REPS` (countdown reaches 0) · `QUIT_SHEET` (✕ or back) · `PAUSED` (┃┃).

**Back behaviour** — Opens `QUIT_SHEET`. Never pops directly, even here where nothing has happened —
one back path for the whole player is the point. The sheet's 記録せずに終える deletes the session row
(zero segments = nothing worth keeping).

**Tab bar** — hidden. Immersive.

**Data out** — Nothing. Prepare is not a segment result; it is timeline offset `[0, prepareMs)` and is
excluded from `activeMs`. The clock anchor was written at insert.

```
┌────────────────────────────────────────────────┐
│  ✕                                            │  ← 48.dp target at start 16, top 12; c.inkSoft
├────────────────────────────────────────────────┤
│ ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ │  ← session hairline 1.dp c.hair (0% here)
│              ╭───────────────╮                 │
│             ╱      支度       ╲                │  ← Mincho 15.sp ls 6.sp, c.inkFaint
│            │         3         │               │  ← Mincho 88.sp tabular, c.ink — bare integer,
│             ╲                 ╱                │     not 0:03
│              ╰───────────────╯                 │  ← ensō 220.dp, stroke 3.dp, c.inkFaint, depleting
│                                                │
│                 ジャンピングジャック             │  ← Mincho Medium 26.sp c.ink
│                 三十秒                          │  ← Gothic 14.sp c.inkSoft
│              肘は肩の真下に                      │  ← Gothic 12.sp c.inkFaint (omitted if no cue)
├────────────────────────────────────────────────┤
│     ◁              ┃┃              ▷           │  ← bottom bar 96.dp, targets 64.dp
└────────────────────────────────────────────────┘
```

`◁` is disabled (`c.inkFaint`, `enabled = false`) — there is nothing behind prepare.

**States** — `Counting` (5→1) · `Paused` (hands to `PAUSED`; the ensō freezes) · `Zero` (never visually
distinct; the 180ms phase-swap cross-fade has already started) · `Skipped` (`▷`: jump to segment 1, one
CLICK haptic, no animation-in).

**Edge cases**

1. `ANIMATOR_DURATION_SCALE == 0`: the ensō is a value redraw driven by `stateAt`, so it is unaffected
   — but the phase-swap cross-fade must draw its end state immediately. Read the setting once at
   composition and set every `tween` duration to 0.
2. Backgrounding during prepare: `elapsedRealtime` keeps running, so returning after 8s lands
   mid-first-interval. Correct and intentional — no special case.
3. The session-start cue fires at the **end** of prepare, not the start.
4. On resume, prepare is 3s and the hero line shows the exercise the session was interrupted *inside*,
   not the first one.
5. `prepareSeconds == 0` (a legal setting): the compiler must emit **no** 支度 segment rather than a
   zero-length one — a zero-duration segment divides by zero in the ensō sweep.

**Accessibility** — One announcement on entry, `liveRegion = Polite`, on a container described
`"支度、五秒後に ジャンピングジャック"`. **The numeral must not be a live region**: announcing 5,4,3,2,1
collides with the tone cues and is worse than silence. `✕ = "鍛錬を終える"`, `┃┃ = "休止"`,
`▷ = "とばす"`, `◁ = "戻る"` (disabled state announced).

**Pure logic** — `prepareDurationMs(resumed)`, `ensoSweep(fractionRemaining): Float // → 0f..312f`.

---

### GYM.SESSION.WORK — 運動 (time-based interval)

**Entered from** — `PREPARE` · `REST` expiring · `REPS` closing with zero inter-station rest · `PAUSED`
· `◁`/`▷` landing on a time-based segment.
**Exits to** — `REST` / `WORK` / `REPS` / `COMPLETE` at zero · `PAUSED` · `QUIT_SHEET`.

**Back behaviour** — `QUIT_SHEET`. **The clock pauses the instant the sheet opens** and resumes only on
つづける. Not optional: a sheet that lets the timer run while you decide produces a record that includes
the deciding.

**Data out** — **On the transition out** (not during), one transaction:

```kotlin
suspend fun recordSegment(sessionId: Long, r: SegmentResultDraft)   // + checkpoint(clock), same txn

data class SegmentResultDraft(
    val ordinal: Int, val phase: Phase, val stationIndex: Int?, val exerciseId: String?,
    val round: Int, val prescribedReps: Int?, val actualReps: Int?,
    val plannedMs: Long, val actualMs: Long, val addedMs: Long, val skipped: Boolean,
    val closedAtWallMs: Long, val closedAtElapsedMs: Long,
)
```

Roughly 2–4 writes per minute for a circuit — trivially cheap, and it buys full process-death recovery.

```
┌────────────────────────────────────────────────┐
│  ✕                 三巡目 ・ 四種目中 三        │  ← Mincho 12.sp ls 3.sp, c.inkFaint
├────────────────────────────────────────────────┤
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░░░░░░░░ │  ← 1.dp; track c.hair, fill c.inkSoft
│              ╭───────────────╮                 │
│            │      腕立て伏せ    │               │  ← Mincho Medium 24.sp ls 2.sp, c.ink
│            │        0:23       │               │  ← Mincho 88.sp, FontFeatureSetting("tnum")
│             ╲                 ╱                │     c.ink
│              ╰───────────────╯                 │  ← ensō 220.dp, stroke 3.dp, c.accent,
│                 ● ● ● ○ ○                      │     depleting clockwise from the gap
│                                                │  ← CycleDots 6.dp, gap 12.dp
│         次 ・ 休息 十五秒 → プランク            │     done c.accent / pending c.hair
├────────────────────────────────────────────────┤  ← Gothic 13.sp c.inkFaint — one line, never a card
│     ◁              ┃┃              ▷           │
└────────────────────────────────────────────────┘
```

**`CycleDots` reuse (prerequisite P3).** `FocusScreen.kt:210` is `private`, hardcodes 4 dots at 9.dp
with `inkFaint` pending; design §11 wants 6.dp and `c.hair`. Extract as
`CycleDots(total, filled, dotSize = 9.dp, gap = 12.dp, filledColor, pendingColor)`; **Focus keeps its
current defaults so Focus is pixel-unchanged**; the player passes `dotSize = 6.dp, pendingColor =
c.hair`. When `total > 9`, render `三巡目 / 十二巡` as text — twelve dots at 6.dp + 12.dp gap is 210.dp
and overflows.

**Ensō geometry (prerequisite P4).** Copy `HomeScreen.kt:162` but parameterise: `startAngle = -60f`
stays (the gap sits upper-right and is the natural progress origin), `sweepAngle = 312f *
fractionRemaining`, `Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)`, radius from a fixed 220.dp
box rather than `size.minDimension` ratios. Ring colour via `animateColorAsState(target, tween(200,
LinearOutSlowInEasing))` — the only colour animation in the feature.

**States**

1. `Running`.
2. `LastThree` (T−3s..0) — **no layout change, cue only.** Do not turn the numeral red; the design has
   one accent and the ring already owns it.
3. `Halfway` — instantaneous cue at 50% when `plannedMs >= 20_000`. No visual.
4. `LastRound` — the first WORK segment of the final round: the counter reads `最後の巡 ・ 四種目中 三`
   in `c.accent` instead of `c.inkFaint`, for that segment only.
5. `Paused`.
6. `NoNextUp` — final segment: `次 ・ 完了`.
7. `ZeroRest` — next segment is another WORK: `次 ・ プランク` (no 休息 clause).
8. `Skipped` — `▷` fired; the result is written with `skipped = true, actualMs = elapsedInSegment`.

**Actions**

| Trigger | Precondition | Effect | Persists? | Navigates to |
|---|---|---|---|---|
| remaining → 0 | `gate == AUTO` | close segment, advance frontier, fire interval-end cue | **yes** | next phase |
| Tap `▷` | — | close with `skipped = true`, seek to next segment start | **yes** | next phase |
| Tap `◁` (1st) | — | `seekTo(currentSegment.startMs)`; arm a 2s window | no | `WORK`, restarted |
| Tap `◁` (2nd, ≤2000ms) | a previous segment exists | seek to previous start; **DELETE** its result | **yes** | previous phase |
| Tap `┃┃` | — | `clock.pause()` | **yes** — clock | `PAUSED` |
| Tap `✕` / Back | — | pause, open sheet | **yes** — clock | `QUIT_SHEET` |
| Long-press ensō | — | no-op (reserved; do not add a hidden gesture) | — | — |

**Edge cases**

1. **Segment shorter than 3s** (a 2s station in a custom routine): suppress the 3-2-1 entirely rather
   than firing it late. Rule: `if (plannedMs < 3500) skip COUNT_TICK`.
2. **Halfway on a 20s interval** fires at exactly 10s, 7s before the 3-2-1 ladder. Below 20s it is
   suppressed. No overlap possible.
3. **Deep sleep / doze while backgrounded** — the single most important correctness path in the
   player. `elapsedRealtime` includes deep sleep, so on return the frontier may be several segments
   ahead; `stateAt` resolves it correctly, but the intervening results were never written. On
   foreground, run `reconcile(elapsedMs)`: back-fill every passed segment with `actualMs = plannedMs`,
   `skipped = false`, `closedAtElapsedMs` interpolated.
4. **The frontier passed the end of the timeline while backgrounded** — reconcile all, then land on
   `COMPLETE` with `autoCompleted = true`.
5. `▷` on the last segment → `COMPLETE`, not a crash on `segments[i+1]`.
6. Rapid `▷▷▷` — each tap is a discrete seek; debounce at 250ms so a stutter tap does not skip three
   stations.
7. **Font scale 2.0 breaks 88.sp** — cap at `min(88.sp, availableWidth / 4.2)` and set
   `softWrap = false`. Never let the numeral wrap.
8. `▷` during the last 3s — the interval-end cue must be **cancelled**, not fired twice. The cue engine
   keys on segment ordinal (§D).

**Accessibility**

- **The countdown is not a live region.** A single invisible live-region node announces at **30s, 10s,
  and 0 only**: `"残り 三十秒"`, `"残り 十秒"`, `"次、休息 十五秒、プランク"`. Anything finer fights the
  tones and never finishes a phrase before the next.
- Exercise name: `heading()` + `liveRegion = Polite`, announced once per segment.
- Session hairline: `progressSemantics(fraction)`, `"全体 四十パーセント"`.
- `CycleDots`: one merged node, `"三巡目、五巡中"`; individual dots `clearAndSetSemantics {}`.
- Controls: `◁ = "前へ、二回押すと一つ戻る"`, `┃┃ = "休止"`, `▷ = "とばす"`. 64.dp targets.
- Next-up: `"次、休息 十五秒、そのあと プランク"` — spell out 「そのあと」, because `→` reads as nothing.

**Pure logic** — `formatCountdown(remainingMs)`, `nextUpLabel(tl, i, lib)`, `counterLabel(round,
totalRounds, station, totalStations)`, `isLastRound(seg, tl)`, `shouldFireHalfway(plannedMs)`,
`reconcile(tl, fromOrdinal, elapsedMs)`.

---

### GYM.SESSION.REPS — 運動・回数 (self-paced rep slide)

**Purpose** — Show a prescribed rep count with a pacing estimate that never gates, and wait for `済`.

**Entered from** — the same set as WORK, whenever the landed segment has `open = true`.
**Exits to** — `REST` / `WORK` / `REPS` / `COMPLETE` after `済` · `PAUSED` · `QUIT_SHEET` ·
`COMPLETE` on an EMOM_ASCENDING fail-out.

**Data out** — On `済` (or skip, or fail-out): one result with `actualMs = elapsedInSegment` (**not**
the estimate) and `actualReps` = adjusted count or `prescribedReps`. Then `timeline.close(ordinal,
actualMs)` shifts every downstream elastic segment (§B.4), and `checkpoint` records the new derived
offsets — same transaction.

```
┌────────────────────────────────────────────────┐
│  ✕                 三巡目 ・ 四種目中 三        │
├────────────────────────────────────────────────┤
│ ▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ │
│              ╭───────────────╮                 │
│            │      腕立て伏せ    │               │  ← Mincho Medium 24.sp ls 2.sp, c.ink
│            │       二十回       │               │  ← Mincho 76.sp, c.ink — REPS is the hero
│             ╲   目安 0:38      ╱                │     kanji, via JapaneseDate.kanji
│              ╰───────────────╯                 │  ← Gothic 13.sp c.inkFaint;
│                 ● ● ● ○ ○                      │     over estimate → "＋0:07" in c.accent
│         ┌────────────────────────────┐         │
│         │            済               │         │  ← Mincho 20.sp ls 4.sp, c.accent on c.card
│         └────────────────────────────┘         │     64.dp tall, width = fillMaxWidth − 44.dp
├────────────────────────────────────────────────┤     RoundedCornerShape(18.dp)
│     ◁              ┃┃              ▷           │
└────────────────────────────────────────────────┘
```

The next-up line is **replaced** by the `済` button at the same vertical anchor, so the chrome stays
pixel-stable between WORK and REPS — design §3.1's whole argument.

**States**

1. `Pacing` — estimate counting down, ring depleting.
2. `Overrun` — past the estimate. The ring **holds at empty**: it does not refill and does not pulse
   (§10 forbids loops). The 目安 line becomes `＋0:07` in `c.accent`, counting up. The 済 button is
   unchanged; **nothing about overrun is a failure.**
3. `MaxEffort` — hero reads `限界まで`, no estimate line, ring is a static full `c.enso` circle.
4. `AutoAdvanceArmed` — the `目安で自動的に進む` setting (default off): at zero the segment auto-closes
   with `actualReps = prescribed`. The only visible difference is that the state ends itself.
5. `EmomWindow` — the estimate is replaced by the **minute remaining**, `目安` becomes `残り 0:22`, and
   the ring depletes over the minute, not the estimate. Overrun is impossible; the boundary decides.
6. `RepAdjusting` — the long-press wheel is open.
7. `Paused`.

**Rep adjustment (long-press `済`)** — a bottom sheet, 240ms slide + fade, matching
`BlockConfirmDialog`. Single-column `TempoValueWheel` (prerequisite P2) over 0..99, pre-set to
prescribed, `記録する` in `c.accent`. The clock keeps running while it is open.

**Actions**

| Trigger | Precondition | Effect | Persists? | Navigates to |
|---|---|---|---|---|
| Tap `済` | Pacing / Overrun / MaxEffort | close at now, `actualReps = prescribed`, shift downstream | **yes** | next phase |
| Long-press `済` | same | open the rep wheel | no | — |
| Wheel → `記録する` | — | close with the chosen `actualReps` | **yes** | next phase |
| Wheel dismissed | — | sheet closes, segment still open | no | — |
| estimate → 0 | `autoAdvance` | close with prescribed reps | **yes** | next phase |
| estimate → 0 | `!autoAdvance` | enter `Overrun`; **nothing advances** | no | — |
| minute boundary | EMOM* and still open | fail-out (§C.3) | **yes** — `skipped = true` | per engine |
| Tap `▷` | — | close with `skipped = true`, `actualReps = null` | **yes** | next phase |
| `◁` ×1 / ×2 | — | as WORK | ×2 persists a DELETE | — |
| Tap `┃┃` | — | pause; the open segment's accrual stops | **yes** | `PAUSED` |

**Edge cases**

1. **A rep slide running far past its estimate** (user rests mid-set, phone face-down) stays open
   indefinitely by design. Guard at **30 minutes** of continuous overrun: auto-pause and show `PAUSED`
   with `長い間 動きがありません`. Without this, a forgotten phone produces a 9-hour "session."
2. **Overrun shifts everything downstream**, so the session hairline must recompute its denominator
   from the **current** timeline, not the compiled one, or it reads over 100%.
3. `actualReps > prescribed` is legal and interesting — that is §3.5's data-source argument. No clamp,
   no warning.
4. `actualReps == 0` via the wheel is treated as `skipped = true`, rendered `とばした`.
5. Long-press then rotate: the wheel is a `rememberSaveable` sheet state; the clock is unaffected.
6. `済` double-tap: the second lands on the *next* segment's UI. Debounce 300ms **and** key the close on
   segment ordinal so a duplicate close is a no-op.
7. AMRAP: closing the last station of a round appends the next round (§B.3) — the user must never see
   完了 mid-cap.
8. The haptic on `済` is a light CLICK, **not** the heavy interval-end waveform — the user is holding
   the phone and the two must feel different.

**Accessibility** — Hero `"腕立て伏せ、二十回"`, `heading()`, announced once. The 目安 line is **not** a
live region while pacing (it changes every second); on crossing into Overrun, one polite
`"目安を過ぎました"`, then silent. `済` is `"済、二十回として記録"` with
`onLongClick(label = "回数を変える")` — **TalkBack surfaces long-press only when labelled, so this is
mandatory.** `済` is the largest target in the app at 64.dp × (width − 44.dp), correctly, because it is
pressed with a shaking hand.

**Pure logic** — `repEstimateMs(reps, secondsPerRep)`, `pacerLabel(estimateMs, elapsedMs)`,
`repHero(prescription)`, `isStalled(openForMs)`.

---

### GYM.SESSION.REST — 休息 (recovery)

**Purpose** — Count down recovery while promoting the next exercise to hero, and offer `＋二十秒`.

**Data out** — a result on exit. `＋二十秒` writes `added_ms` **and** immediately checkpoints — a
session recovered after process death must remember the added time or the record lies.

Chrome is **pixel-identical** to WORK. Only the ring colour, numeral size, and the block below the ring
change.

```
│              ╭───────────────╮                 │
│             ╱      休息       ╲                │  ← Mincho 15.sp ls 6.sp, c.inkFaint
│            │        0:08       │               │  ← Mincho 72.sp, c.inkSoft
│             ╲     つぎ         ╱                │     (smaller + softer than 運動 — deliberate)
│              ╰───────────────╯                 │  ← Mincho 12.sp ls 4.sp c.inkFaint
│                                                │  ← ensō 220.dp, stroke 3.dp, c.enso
│                  プランク                       │  ← Mincho Medium 26.sp, c.ink
│                  三十秒                         │  ← Gothic 14.sp, c.inkSoft
│               肘は肩の真下に                     │  ← Gothic 12.sp, c.inkFaint — exactly one cue
│              ＋二十秒        とばす ▷            │  ← Mincho 14.sp ls 2.sp, c.accent
│                                                │     48.dp targets, gap 40.dp
```

Two `▷` affordances coexist deliberately: the inline `とばす ▷` is the semantic "I'm ready, go" (the
common case, thumb-reachable), the bar `▷` is the structural skip. Keep both — rest is the one phase
where skipping forward is the *expected* action rather than a correction.

**Round dots are hidden when `RestKind == ROUND`.** The round has just ended and the dot count is
mid-transition; showing it flicker between n and n+1 during the round rest is the one moment the dots
lie. The ring label reads `巡の間` instead.

**States**

1. `Counting`.
2. `Extended` — `＋二十秒` tapped ≥1 time. The 休息 label gains a `＋0:20` suffix in `c.accent` 12.sp so
   the added time is visible in the record's mental model. Cumulative.
3. `RoundRest` — label `巡の間`, dots hidden, hero is the **first** exercise of the coming round.
4. `MandatedRest` — Barbara: label `決められた休息`; `＋二十秒` and **both** `▷` are **disabled**
   (`c.inkFaint`). The rest is part of the prescription; skipping it makes the record incomparable.
   This is the one place the player refuses a skip, and it must be **visibly disabled rather than
   silently inert**.
5. `EmomRemainder` — label `残り`; `＋二十秒` and `とばす` hidden (the grid is anchored). Only `┃┃` and
   `✕` remain.
6. `Paused`. 7. `LastThree` — cue only.

**Actions** — expiry closes and advances · `＋二十秒` grows `plannedMs` and shifts downstream elastic
segments · `とばす`/`▷` closes with `skipped = true` · `┃┃` · `◁` ×1/×2 · `✕`.

**Edge cases**

1. **`＋二十秒` at 0:02 remaining** — the addition must land before the auto-advance. The handler
   recomputes `stateAt` *after* mutating the timeline, in the same frame; if the frontier had already
   advanced, the tap applies to the **new** segment only if it is also a REST, else it is dropped with
   a negative CLICK haptic. **Do not retroactively rewind.**
2. **Repeated `＋二十秒`** — no cap. Autoregulation is the point. The 30-minute stall guard still
   applies.
3. **`MANDATED` with controls disabled** — the label must still be *rendered* (greyed), not removed. A
   control that vanishes teaches the user it was never there.
4. **A rest shorter than 4s** (3s transitions in custom routines) — suppress the 3-2-1 by the same
   `plannedMs < 3500` rule; still render at 72.sp, no special small layout.
5. **The incoming exercise has no cue** — the third line is omitted and the block does **not**
   re-centre. Fixed three-line slot, blank third line. Layout stability beats vertical balance.
6. **Backgrounded through an entire rest** — reconcile writes it with `actualMs = plannedMs`,
   `skipped = false`.

**Accessibility** — One polite announcement on entry: `"休息 十五秒、次は プランク、三十秒"`, with the
form cue appended (it is one clause and it is the useful part). Numeral not a live region; 10s and 0
only. `＋二十秒` is `"二十秒 追加"`, and after tapping `stateDescription = "四十秒 追加済み"`. Disabled
controls carry `disabled()` **plus** the reason: `"とばす、決められた休息のため使えません"` — never a
silent no-op. 48.dp minimum on the inline row (the text is 14.sp, so pad to target).

**Pure logic** — `restLabel(kind)`, `canExtend(kind)`, `canSkip(kind)`, `applyExtension(tl, ordinal,
deltaMs)`, `restHero(tl, ordinal, lib)`.

---

### GYM.SESSION.PAUSED — 休止

**Purpose** — Freeze the session honestly, show what has accrued, get back in with one tap.

**Back behaviour** — `QUIT_SHEET`. Back does **not** resume; a back press must never restart a timer the
user cannot see.

**Data out** — On entry: `checkpoint(pausedAtElapsed = now, pausedAtWall = wallNow)`. On resume:
`pausedAccumulatedMs += (now − pausedAtElapsed)`, clear the anchors. Both mandatory — the paused-at
anchor is what makes a process death *during a pause* recoverable.

The ensō **freezes at its current sweep**; the freeze is the confirmation and there is no animation.

```
│              ╭───────────────╮                 │
│             ╱      休止       ╲                │  ← Mincho 15.sp ls 6.sp, c.inkFaint
│            │        0:23       │               │  ← Mincho 88.sp, c.inkSoft — the ONE token
│             ╲   腕立て伏せ     ╱                │     that changes on pause (was c.ink)
│              ╰───────────────╯                 │  ← ring frozen, colour unchanged
│              六分十四秒 経過                    │  ← Gothic 13.sp, c.inkSoft — session active time
│              八種目 ・ 二巡 済                   │  ← Gothic 11.sp, c.inkFaint
│         ┌────────────────────────────┐         │
│         │           続ける            │         │  ← Mincho 20.sp ls 4.sp, c.accent on c.card
│         └────────────────────────────┘         │     64.dp tall
├────────────────────────────────────────────────┤
│     ◁              ▶               ▷           │  ← ┃┃ becomes ▶; same 64.dp target
```

The countdown numeral **keeps its size and position** and only drops from `c.ink` to `c.inkSoft`.
Moving or shrinking it on pause would be exactly the layout jump §3.1 forbids.

**States** — `Paused` · `PausedLong` (>60s: 続ける gains `三秒の支度から` in `c.inkFaint` 11.sp and
resuming routes through a 3s PREPARE) · `Stalled` (the 30-minute guard fired: `長い間 動きがありません`
above 続ける, and the quit sheet's primary is pre-highlighted — **no auto-save; the user decides**) ·
`PausedDuringPrepare` (`◁` and the elapsed lines hidden; nothing has elapsed).

**Skip-while-paused stays paused.** A user who paused to re-plan should be able to re-plan without the
clock starting under them.

**Edge cases**

1. Pausing an **open** REPS segment: accrual stops; on resume `startedAtElapsed` is unchanged and
   `pausedAccumulatedMs` absorbs the gap. `elapsedMs()` already handles this.
2. Pausing an **anchored** EMOM segment: the whole grid shifts with the pause, because the grid is
   expressed in session-elapsed time, not wall time. Correct — an EMOM paused for a phone call resumes
   at the same point in the minute.
3. Process death while paused: `pausedAtElapsed` is on disk, so `elapsedMs()` reconstructs identically.
4. Reboot while paused: detected by the boot anchor (§E.3) → the session becomes stale → the resume
   prompt on next cold start, with elapsed recovered from `pausedAtWall`.
5. **The cue engine must be fully disarmed on pause and re-armed on resume.** A pending 3-2-1
   `postDelayed` that fires during a pause is the most jarring bug this feature can ship.

**Accessibility** — `"休止中、六分十四秒 経過、八種目 済"` announced once. `続ける` is the default
TalkBack focus target (`FocusRequester` in a `LaunchedEffect`). The frozen numeral gets
`"残り 二十三秒、休止中"` and is **not** a live region.

**Pure logic** — `SessionClock.pause(nowElapsed)`, `SessionClock.resume(nowElapsed)`,
`needsPrepareOnResume(pausedForMs)`, `activeSummary(results)`.

---

### GYM.SESSION.QUIT_SHEET — 鍛錬を終えますか

**Purpose** — Three honest outcomes for an in-progress session, never two.

**Back behaviour** — Back dismisses the sheet = つづける. The clock stays paused until the sheet is
dismissed, then resumes. **Back on the sheet must never be a discard.**

```
        ┌───────────────────────────────────┐
        │   鍛錬を終えますか                  │  ← Mincho 16.sp ls 2.sp, c.inkSoft
        │   六分十四秒 ・ 二十種目中 八       │  ← Gothic 13.sp, c.inkFaint
        │   ─────────────────────────────   │  ← 1.dp c.hair
        │   ここまでを記録する            →  │  ← Mincho 16.sp ls 2.sp, c.accent — PRIMARY
        │   ─────────────────────────────   │
        │   記録せずに終える              →  │  ← c.inkFaint — destructive
        │   ─────────────────────────────   │
        │   つづける                      →  │  ← c.inkSoft — ESCAPE
        └───────────────────────────────────┘
```

Sheet: `c.card` over a `c.bgSolid @ 0.62` scrim, `RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)`,
`padding(h 24, v 20)`, rows 56.dp.

**The escape is つづける, not やめる.** Tempo already uses やめる to mean "abandon" in the calendar
composer; reusing it here to mean "don't abandon" is a genuine bug in the language. **Do not "fix" this
to match the composer.**

**Data out** — `ここまでを記録する`: close the current segment as `skipped = true` with its partial
`actualMs`, then `finishSession(sessionId, complete = false)`, one transaction. `記録せずに終える`:
`discardSession` — a hard DELETE with cascade. **Two-step only when ≥1 segment is complete**: the row
becomes `本当に消しますか` for 3 seconds requiring a second tap; a single mis-tap must not destroy 14
minutes of work. Under one completed segment it deletes immediately.

**States** — `Standard` · `NothingToSave` (zero completed segments: 記録する omitted, subtitle
`まだ 記録するものがありません`, the destructive row softens to `終える`) · `ConfirmDiscard` (3s window)
· `Saving` (rows non-interactive) · `SaveFailed` — **the sheet stays open** with
`記録できませんでした ・ もう一度`. **Never navigate away on a failed save.**

**Edge cases**

1. Sheet open when the app is killed: the session survives as an open row with `pausedAtElapsed` set →
   resume prompt on cold start. No extra handling.
2. **`ここまでを記録する` on an open REPS segment mid-set** — the partial gets `actualReps = null,
   skipped = true`. **Do not guess a rep count from the elapsed pacer** — that fabricates data, the
   exact failure hard rule 3 exists to prevent.
3. Scrim tap = つづける (the safest option, per the pattern's own logic).
4. Rotation resets the 3s confirm window. Acceptable — a rotate is not a tap.
5. A partial save on the first segment of AMRAP with `roundsCompleted = 0` is legal, saved, and
   rendered as 途中まで with zero rounds.

**Accessibility** — Focus lands on the **title**, not on a destructive row. Rows 56.dp, `Role.Button`,
full-row targets. The destructive row is `"記録せずに終える、これまでの記録は消えます"`; when armed,
`"本当に消しますか、もう一度 押すと消えます"` via `liveRegion = Assertive` — this one *is* assertive,
being a destructive confirmation. Ensure nesting order so the player's own quit-sheet handler is
disabled while the sheet is shown.

**Pure logic** — `quitSummaryLine(activeMs, done, total)`, `quitOptions(completedCount)`,
`partialResultFor(open, elapsedInSegMs)`.

---

### GYM.SESSION.COMPLETE — 記録 (post-session)

**Purpose** — Close the ensō, show one hero number, capture the rating while the sensation is fresh.

**Entered from** — the final segment closing · the quit sheet's 記録する · EMOM_ASCENDING fail-out ·
AMRAP cap · the resume prompt's 記録する · a backgrounded reconcile that ran past the end.

**Back behaviour** — Pops the **entire player stack** to `GYM.HOME` (`popUpTo(Home) { inclusive =
false }`), so back can never re-enter a finished session. Back is allowed with the rating unset — it is
skippable by design.

**Tab bar** — **hidden.** This is the end of the session, not a browsing destination; `閉じる` is the
only way back into the shell. (`GYM.RECORDS.SESSION_DETAIL` renders the same component with the tab bar
*visible* — see `04-library-records.md` §3 for the full difference table.)

**Data out** — nothing on entry (the session was finished by whatever transitioned here).
`rateSession` on a rating tap — immediate, no confirm; a failure surfaces as a `c.accent`
`保存できませんでした` line under the buttons rather than silently reverting.

```
┌────────────────────────────────────────────────┐
│                                          閉じる │  ← Mincho 14.sp ls 2.sp, c.inkSoft, 48.dp
│              ╭───────────────╮                 │  ← ensō CLOSES: sweep 0 → 360, 900ms,
│             ╱      七分間      ╲                │     LinearOutSlowInEasing, drawn once, then
│            │     六分十四秒     │               │     STILL. No confetti. c.accent.
│             ╲     活動時間     ╱                │  ← Mincho 64.sp c.ink — HERO
│              ╰───────────────╯                 │  ← Gothic 12.sp ls 3.sp c.inkFaint
│   ┌ 途中まで ・ 二十種目中 八 ┐                  │  ← partial only; Mincho 12.sp ls 3.sp
│   └───────────────────────┘                    │     c.inkFaint chip on c.card, 28.dp
│     二十種目        五巡        三百二十回      │  ← value Gothic 20.sp c.ink
│     ────────────────────────────────────       │     label Gothic 11.sp c.inkFaint
│       種目           巡          回             │     1.dp c.hair rule above labels
│     前回より 二十二秒 速い                       │  ← Mincho 14.sp ls 2.sp, c.accent
│     四日 連続                                    │  ← Mincho 14.sp ls 2.sp, c.inkSoft
│     ┌ 自己最高 ┐                                │  ← Mincho 12.sp ls 3.sp, c.accent chip
│     └─────────┘                                 │
│     どうでしたか                                 │  ← Mincho 14.sp ls 3.sp, c.inkFaint
│     ┌────────┐ ┌────────┐ ┌────────┐           │  ← 56.dp tall, RoundedCornerShape(16.dp)
│     │   楽   │ │ ちょうど │ │  きつい │           │     Mincho 15.sp ls 2.sp, c.inkSoft on c.card
│     └────────┘ └────────┘ └────────┘           │     selected c.accent + 1.dp c.accent border
│     内訳                                        │
│     腕立て伏せ      0:41   済   二十回          │  ← Gothic 13.sp, c.inkSoft
│     プランク        0:30   済                   │
│     スクワット      0:38   済   十八回 / 二十回  │  ← actual ≠ prescribed shows both
│     バーピー        —      とばした              │  ← whole row c.inkFaint
│     もう一度              予定に入れる           │  ← Mincho 15.sp ls 2.sp, c.accent / c.inkSoft
└────────────────────────────────────────────────┘
```

**Ordering is load-bearing.** Design §4 places the rating after the comparison lines in *layout* but is
explicit that it must be asked *before* the streak and PR chip can bias it. Resolution: the page opens
scrolled with the **rating row at the vertical centre**, and the comparison/streak/PR block **fades in
400ms after the first rating tap or after 6 seconds, whichever is first** (240ms fade,
`LinearOutSlowInEasing`). Final scroll order stays as drawn. Satisfies both the layout and the "ask
before you flatter" requirement, and costs one `AnimatedVisibility`.

**States** — `Loading` (hero slot blank at 120.dp, no spinner) · `Complete` · `Partial` (途中まで chip;
**no** ensō closure — the ring draws at its final partial sweep and holds; no PR chip; comparison
suppressed) · `FailedOut` (EMOM_ASCENDING: chip `十七分で 力尽きた`, hero label becomes `到達`; treated
as **complete** for streak purposes — reaching failure is the protocol succeeding) · `Rated` (block
does not collapse; the user may change their mind) · `RatingFailed` · `NoComparison` (first ever
session: `はじめての記録` in `c.inkSoft`) · `SummaryFailed` (`記録を読み込めませんでした` + もう一度,
**plus `記録は保存されています` in `c.inkFaint`** — the session *is* saved, so say so).

**Edge cases**

1. `ANIMATOR_DURATION_SCALE == 0`: the ensō renders **closed immediately**, and the delayed block
   renders immediately. The 6s timer is not an animation and stays.
2. Entering while the screen is off (auto-completed in the background): the completion cue fires, but
   the 900ms closure must run on next foreground. **Gate the closure on `ON_RESUME`, not on
   composition.**
3. Rating tapped, then process death before the write lands: it is a single-row update on
   `viewModelScope`. Accept the tiny loss; do not add a queue.
4. `totalReps == 0` (a pure time circuit): the third tile shows `—`, not `〇回`. Zero is not a result
   here, it is an inapplicability.
5. PR detection uses the **monotonic clock guard** — a system clock wound backwards must not fabricate
   a PR.
6. **A session shorter than 30s suppresses the PR chip and the comparison line regardless of the
   numbers.** A 12-second "session" that beats a real one is noise.
7. The streak line calls `streakDays()`; if it returns null the line is **omitted**, not rendered as
   `—`.
8. **The screen-on flag must be released on entering COMPLETE.** The workout is over. Key the gym's
   `keepAwake` on the *live* player routes only.

**Accessibility** — The ensō closure is decorative: `clearAndSetSemantics {}` on the Canvas, hero text
carries all meaning. Hero `"活動時間 六分十四秒"`. Tiles are **one merged node** — three separate nodes
read as three orphan numbers. Rating: `selectableGroup()`, `Role.RadioButton`, group
`stateDescription = "未評価"` / `"ちょうど を選択"`. The delayed block is `liveRegion = Polite` so it is
announced when it appears rather than silently inserted below the reading position.

**Pure logic** — `heroDuration(activeMs)`, `tiles(s)`, `comparisonLine(cur, prev)`,
`isPersonalBest(cur, best, engine)`, `breakdownRows(results, lib)`, `partialChip(s)`,
`shouldSuppressAccolades(s)`.

---

### The resume prompt — つづき (modal, not a route)

Per `00-plan.md` §2 row 8 this is a modal over `GYM.HOME` (or over `GYM.LIBRARY.DETAIL` when it fires
as a start-guard), not a `GymRoute`. `01-shell.md` §B owns its placement; the logic is here.

```kotlin
data class OpenSessionRecord(
    val sessionId: Long, val routineId: String, val routineName: String,
    val tier: ScalingTier, val engine: Engine,
    val startedAtWallMs: Long, val lastWriteWallMs: Long,
    val clock: PersistedClock, val results: List<SegmentResult>,
)
data class PersistedClock(
    val startedAtElapsedMs: Long, val pausedAccumulatedMs: Long,
    val pausedAtElapsedMs: Long?, val pausedAtWallMs: Long?,
    val bootAnchorMs: Long,        // System.currentTimeMillis() − SystemClock.elapsedRealtime()
    val anchorWallMs: Long,
)
```

```
        ┌───────────────────────────────────┐
        │   途中の 鍛錬があります             │  ← Mincho 16.sp ls 2.sp, c.inkSoft
        │   七分間 ・ 六分十四秒 ・ 八種目     │  ← Gothic 13.sp, c.inkFaint
        │   二時間前                          │  ← Gothic 11.sp, c.inkFaint — how stale
        │   ─────────────────────────────   │
        │   続ける                        →  │  ← c.accent
        │   記録する                      →  │  ← c.inkSoft
        │   捨てる                        →  │  ← c.inkFaint
        └───────────────────────────────────┘
```

**States** — `Resumable` (same boot, staleness < 4h) · `RebootedOrStale` (**続ける is removed, not
disabled** — a different boot means the elapsed clock is unreconstructable and any resume would
fabricate time; title becomes `途中の 鍛錬が 残っています`, subtitle notes `続きからは できません`) ·
`NothingWorthSaving` (zero completed segments: 記録する removed, leaving 捨てる + a そのまま dismiss —
**auto-discard is tempting and wrong; the user should learn the app never deletes silently**) ·
`Loading` (no flash: the sheet does not appear until the query resolves) · `ActionFailed`.

**Edge cases**

1. Reboot detection — the whole reason this exists; mechanics in §E.3.
2. Two open sessions (should be impossible; `idx_session_live` enforces it): take the newest, auto-
   discard the older, log.
3. **`続ける` on a session whose routine was edited or deleted** — resume against the **pinned
   `routine_version`**, never the live routine. If the routine row is gone, fall back to the
   denormalised `routine_name` for the banner and remove 続ける.
4. Staleness copy uses coarse buckets: `さっき` (<10m), `一時間前`, `二時間前`, `昨日`, `三日前`. Never
   `2時間14分前`.
5. Scoped to the gym shell's start destination only — never over onboarding or the launcher.
6. **Recovered `activeMs` when the paused anchor is absent** (killed while running): the time between
   the last write and now is **unknowable** — it may have been in a pocket. Rule:
   `recoveredActiveMs = elapsedAtLastWrite`, crediting only up to the last persisted transition.
   **Never credit the gap.** This is why persisting on every transition matters.

**Pure logic** — `resumability(rec, nowWall, nowElapsed)`, `stalenessLabel(deltaMs)`,
`recoveredActiveMs(rec, nowElapsed, sameBoot)`, `replayFrontier(tl, results)`,
`resumeOptions(rec, resumability)`.

---

## B. The timeline compiler

Pure Kotlin, zero Android imports, at `gym/engine/Timeline.kt`, JVM-tested.

### B.1 Types

```kotlin
enum class Engine { INTERVAL_CIRCUIT, AMRAP, FOR_TIME, FOR_TIME_WITH_REST, EMOM, EMOM_ASCENDING, FIXED_SETS }

sealed interface Prescription {
    data class Reps(val count: Int) : Prescription
    data class Duration(val seconds: Int) : Prescription
    data object MaxEffort : Prescription
}

/**
 * One cell of the compiled session.
 *
 * `open`     — self-paced; ends only when the user taps 済 (or a cap fires). `plannedMs` is a pacing
 *              estimate and is NOT authoritative.
 * `anchored` — belongs to a fixed grid (EMOM minutes, an AMRAP cap). Anchored segments never shift;
 *              closing an open anchored segment converts the remainder to rest instead of shifting
 *              everything downstream.
 * `closed` / `actualMs` — set by [Timeline.close]; until then an open segment absorbs all elapsed time.
 */
data class Segment(
    val ordinal: Int, val phase: Phase,
    val startMs: Long, val plannedMs: Long,
    val open: Boolean = false, val anchored: Boolean = false, val gate: Gate = Gate.AUTO,
    val stationIndex: Int? = null, val exerciseId: String? = null, val round: Int = 1,
    val prescribedReps: Int? = null, val restKind: RestKind? = null,
    val closed: Boolean = false, val actualMs: Long? = null, val addedMs: Long = 0,
) {
    val effectiveMs: Long get() = (actualMs ?: plannedMs) + addedMs
    val endMs: Long get() = startMs + effectiveMs
}

data class Timeline(
    val routineId: String, val engine: Engine, val segments: List<Segment>,
    val totalRounds: Int, val stationsPerRound: Int,
    val capMs: Long? = null,          // AMRAP cap; EMOM* grid end
    val extensible: Boolean = false,  // AMRAP: append rounds on demand
    val prepareMs: Long = 5_000,
) {
    /** The first open segment not yet closed; everything after it is provisional. */
    val frontier: Int get() = segments.indexOfFirst { it.open && !it.closed }
    val plannedTotalMs: Long get() = segments.lastOrNull()?.endMs ?: 0L
}
```

### B.2 Entry point

```kotlin
fun compile(routine: Routine, tier: ScalingTier, lib: Map<String, Exercise>): Timeline {
    require(routine.stations.isNotEmpty()) { "routine ${routine.id} has no stations" }
    val stations = applyTier(routine.stations, routine.tierOverrides[tier].orEmpty())
    val b = Builder(routine, stations, lib)
    if (routine.prepareSec > 0) b.prepare()      // 00-plan §2: prepareSec == 0 emits NO segment
    when (routine.engine) {
        Engine.INTERVAL_CIRCUIT   -> b.intervalCircuit()
        Engine.AMRAP              -> b.amrap()
        Engine.FOR_TIME           -> b.forTime()
        Engine.FOR_TIME_WITH_REST -> b.forTimeWithRest()
        Engine.EMOM               -> b.emom(ascending = false)
        Engine.EMOM_ASCENDING     -> b.emom(ascending = true)
        Engine.FIXED_SETS         -> b.fixedSets()
    }
    b.stripTrailingRest()
    return b.build()
}
```

`Builder.add(phase, ms, …)` appends at the cursor and advances it. `estimateMs(station)` returns
`seconds × 1000` for `Duration`, `count × secondsPerRep × 1000` for `Reps`, and
`DEFAULT_MAX_EFFORT_ESTIMATE_MS = 45_000` for `MaxEffort` — **pacer only, never authoritative**.
`stripTrailingRest()` drops trailing REST segments: dead time between the last effort and 完了.

### B.3 The seven engines

Gate semantics: `AUTO` ends itself at `plannedMs`; `MANUAL` ends only on 済; `AUTO_AT_CAP` is open but a
hard cap fires the engine's fail policy.

**1. `INTERVAL_CIRCUIT`** — everything time-boxed and auto-advancing. Rep-prescribed stations inside a
circuit still get a fixed work box; the reps are advisory, matching the protocol. Station rest between
stations (not after the last), round rest between rounds (not after the last).

**2. `AMRAP`** — `capMs` set, `extensible = true`. Every station is open. Materialise
`(capMs / roundEstimate) + 2` rounds, minimum 3, and extend on demand as the frontier nears the end.

**3. `FOR_TIME`** — every station open, one pass, no rounds. The player counts **up**; the ensō tracks
station progress rather than time, because there is no time to deplete against.

**4. `FOR_TIME_WITH_REST`** — rounds of open work plus a **mandated** fixed rest between them
(`RestKind.MANDATED`). Exactly 3:00 for Barbara. Not skippable, not extendable — skipping it makes the
resulting time incomparable to anyone else's Barbara.

**5/6. `EMOM` / `EMOM_ASCENDING`** — the minute grid is **anchored**:

```kotlin
fun Builder.emom(ascending: Boolean) {
    val window = routine.intervalSec * 1000L
    capMs = window * routine.rounds
    for (m in 1..routine.rounds) {
        val minuteStart = cursor
        stations.forEachIndexed { i, st ->
            val reps = (st.prescription as? Prescription.Reps)?.count
                ?.let { if (ascending) it + (m - 1) else it }
            // One open, anchored cell per station, sharing the minute. Its plannedMs is the whole
            // remaining window: whichever station is unfinished when the window ends is the fail-out.
            add(Phase.REPS, window - (cursor - minuteStart), open = true, anchored = true,
                gate = Gate.AUTO_AT_CAP,
                stationIndex = i, exerciseId = st.exerciseId, round = m, reps = reps)
        }
        // The remainder cell. Its span is rewritten by close(); zero-duration cells are never shown.
        add(Phase.REST, 0L, anchored = true, restKind = RestKind.EMOM_REMAINDER, round = m)
        cursor = minuteStart + window          // re-anchor: the grid is authoritative
    }
}
```

**7. `FIXED_SETS`** — a day/step table: N open sets of prescribed reps with a fixed elastic inter-set
rest. `routine.setTable` comes from `progression_step` + `progression_set` (e.g. Recon Ron step 1 →
`[7,6,5,4,4]`).

### B.4 Closing an open segment, and the downstream shift

```kotlin
/**
 * Closes the open segment at [ordinal] with its true duration and re-flows everything after it.
 *
 * Elastic (non-anchored) segments shift by the delta — a set that took 12s longer moves the rest of
 * the session 12s later, which is honest because the clock is real.
 *
 * Anchored segments (EMOM minutes) do NOT shift. Closing an anchored open cell instead grows the
 * following remainder-rest cell to fill what is left of the window, so the grid stays true.
 */
fun Timeline.close(ordinal: Int, actualMs: Long, actualReps: Int? = null, skipped: Boolean = false): Timeline

/** ＋二十秒, or any planned-duration mutation of a not-yet-reached segment. Anchored segments refuse. */
fun Timeline.extend(ordinal: Int, deltaMs: Long): Timeline

/** The exact inverse of close, for skip-back. Property-test `close ∘ reopen == identity`. */
fun Timeline.reopen(ordinal: Int): Timeline

/** AMRAP ran out of materialised rounds. Append one more at the tail cursor. */
fun Timeline.extendOneRound(routine: Routine, lib: Map<String, Exercise>): Timeline
```

### B.5 `stateAt` — the pure state function

```kotlin
data class PlayerState(
    val ordinal: Int, val segment: Segment,
    val elapsedInSegmentMs: Long,
    val remainingMs: Long,        // negative when an open segment is past its estimate
    val overrunMs: Long,          // 0 unless open and past estimate
    val ringFraction: Float,      // 1f full → 0f empty, clamped
    val sessionFraction: Float,   // the hairline
    val finished: Boolean,
    val capExceeded: Boolean,     // AMRAP cap reached, or an EMOM window closed with work open
)

/**
 * The current state is a pure function of elapsed milliseconds. Ticks render; they never advance
 * state. Rotation, backgrounding, resume and "seek to station 4" all fall out for free.
 *
 * The one non-linearity: an OPEN segment not yet closed absorbs all time beyond its start. Downstream
 * offsets are provisional until it closes, so the search must never walk past the frontier.
 */
fun Timeline.stateAt(elapsedMs: Long): PlayerState {
    val f = frontier
    var lo = 0; var hi = segments.lastIndex; var found = 0
    while (lo <= hi) {                                     // binary search on startMs
        val mid = (lo + hi) ushr 1
        if (segments[mid].startMs <= elapsedMs) { found = mid; lo = mid + 1 } else { hi = mid - 1 }
    }
    val idx = if (f >= 0) minOf(found, f) else found       // never walk past the frontier
    val seg = segments[idx]

    val inSeg = (elapsedMs - seg.startMs).coerceAtLeast(0)
    val planned = seg.effectiveMs
    val remaining = planned - inSeg
    val overrun = if (seg.open && !seg.closed && remaining < 0) -remaining else 0L

    val ring = when {
        seg.open && overrun > 0 -> 0f                       // held empty, never refilled
        planned <= 0 -> 0f
        engine == Engine.FOR_TIME -> 1f - sessionProgress(idx)   // count-up: ring tracks stations
        else -> (remaining.toFloat() / planned).coerceIn(0f, 1f)
    }

    val finished = f < 0 && idx == segments.lastIndex && remaining <= 0
    val capExceeded = when (engine) {
        Engine.AMRAP -> capMs != null && elapsedMs >= prepareMs + capMs!!
        Engine.EMOM, Engine.EMOM_ASCENDING ->
            seg.anchored && seg.open && !seg.closed && remaining <= 0
        else -> false
    }
    return PlayerState(idx, seg, inSeg, remaining, overrun, ring, sessionProgress(idx), finished, capExceeded)
}

/** Hairline denominator: closed segments count their real duration, the rest their estimate. */
private fun Timeline.sessionProgress(idx: Int): Float {
    val total = segments.sumOf { it.effectiveMs }.coerceAtLeast(1)
    return (segments.take(idx).sumOf { it.effectiveMs }.toFloat() / total).coerceIn(0f, 1f)
}
```

**Render loop** — the only Android-touching part:

```kotlin
while (isActive) {
    _playerState.value = timeline.stateAt(clock.elapsedMs())
    delay(50)                       // 20fps of state; the ring is a value redraw, not an Animatable
}
```

50ms is chosen so a 1-second numeral flip is never more than 50ms late and the cue schedule has
sub-frame resolution. **Never accumulate ticks.** `SystemClock.elapsedRealtime()` is the only clock,
because `Handler.postDelayed`'s `uptimeMillis` timebase **stops during deep sleep** and a backgrounded
session would silently under-count.

### B.6 Compiler invariants (all unit-testable)

1. `ordinal` equals list index, always, after every mutation.
2. `segments[i].startMs == segments[i-1].endMs` for every consecutive pair **except** across an
   anchored boundary.
3. `stripTrailingRest` leaves the last segment as WORK or REPS.
4. No zero-duration segment is ever *rendered*; the EMOM remainder may be zero and `stateAt` never
   lands on it (start == end).
5. `compile()` is deterministic — same routine + tier + library ⇒ identical timeline. This is what
   `session.compiled_hash` asserts.
6. Every engine produces at least one non-PREPARE segment or throws.
7. **Recon Ron regression**: every row of the 18-step table has five sets summing to its stated total,
   and every total equals `24 + 2 × step_index`.

---

## C. The state machine

### C.1 Transitions

| # | From | Trigger | Guard | Effect | To |
|---|---|---|---|---|---|
| 1 | — | 始める on `GYM.LIBRARY.DETAIL` | routine loaded, no live session | compile, INSERT session, anchor clock | PREPARE |
| 2 | PREPARE | elapsed ≥ prepareMs | — | fire SESSION_START | WORK / REPS |
| 3 | PREPARE | `▷` | — | seek to prepareMs | WORK / REPS |
| 4 | WORK | remaining ≤ 0 | `gate == AUTO` | write result, fire INTERVAL_END | REST / WORK / REPS / COMPLETE |
| 5 | WORK | `▷` | — | write result `skipped = true` | next phase |
| 6 | REPS | `済` | segment open | `close(ordinal, elapsed)`, shift downstream | next phase |
| 7 | REPS | long-press → 記録する | — | close with adjusted `actualReps` | next phase |
| 8 | REPS | remaining ≤ 0 | `autoAdvance` | close with prescribed reps | next phase |
| 9 | REPS | remaining ≤ 0 | `!autoAdvance` | enter Overrun; **no transition** | REPS |
| 10 | REPS | window end, EMOM | still open | close `skipped = true`, note the miss | REST (next window) |
| 11 | REPS | window end, EMOM_ASCENDING | still open | close `skipped = true`, `failedAtRound` | COMPLETE (FailedOut) |
| 12 | REPS | AMRAP cap | `elapsed ≥ prepareMs + capMs` | close partial, record partial-round reps | COMPLETE |
| 13 | REPS | AMRAP, last round closed | `extensible` | `extendOneRound()`; **no phase change** | REPS |
| 14 | REST | remaining ≤ 0 | — | write result | WORK / REPS / COMPLETE |
| 15 | REST | `とばす` / `▷` | `canSkip(restKind)` | write `skipped = true` | WORK / REPS |
| 16 | REST | `＋二十秒` | `canExtend(restKind)` | `extend(ordinal, 20_000)` | REST |
| 17 | any live | `┃┃` | — | pause, disarm cues, checkpoint | PAUSED |
| 18 | PAUSED | 続ける, paused ≤ 60s | — | resume, re-arm cues | prior phase |
| 19 | PAUSED | 続ける, paused > 60s | — | resume, insert a 3s prepare | PREPARE → prior phase |
| 20 | any live / PAUSED | `✕` / back | — | pause, open sheet | QUIT_SHEET |
| 21 | QUIT_SHEET | つづける / back / scrim | — | resume | originating phase |
| 22 | QUIT_SHEET | ここまでを記録する | ≥1 result | close partial, finish incomplete | COMPLETE (Partial) |
| 23 | QUIT_SHEET | 記録せずに終える ×2 | armed | DELETE | GYM.HOME |
| 24 | any live | `◁` 1st tap | — | seek to segment start, arm 2s | same phase, restarted |
| 25 | any live | `◁` 2nd tap ≤2000ms | `ordinal > firstRealSegment` | seek to prev start, **DELETE** its result, `reopen` | previous phase |
| 26 | WORK/REPS/REST | last segment closes | — | `finishSession(complete = true)` | COMPLETE |
| 27 | any live | foreground regain, frontier moved | — | `reconcile()` back-fills | wherever `stateAt` says |
| 28 | any live | open segment held > 30 min | — | auto-pause | PAUSED (Stalled) |
| 29 | cold start | an open session exists | — | — | resume prompt (modal) |
| 30 | prompt | 続ける | same boot ∧ < 4h | replay results, re-anchor | PREPARE (3s) |
| 31 | prompt | 記録する | ≥1 result | finish incomplete | COMPLETE (Partial) |
| 32 | prompt | 捨てる | — | DELETE | GYM.HOME |
| 33 | COMPLETE | 閉じる / back | — | pop the player stack | GYM.HOME |
| 34 | COMPLETE | もう一度 | — | — | GYM.LIBRARY.DETAIL |

### C.2 Diagram

```
                            ┌──────────────────┐
   cold start, open ───────►│  RESUME PROMPT   │
   session found            └──┬────┬─────┬────┘
                    続ける(30) │    │(31) │(32) 捨てる
                               │    │記録する  └──────────────► GYM.HOME
   LIBRARY.DETAIL ─始める(1)─┐ │    └──────────────┐
                            ▼ ▼                    │
                       ┌─────────────┐             │
                       │   PREPARE   │  支度        │
                       └──────┬──────┘             │
                       (2)(3) │                    │
             ┌────────────────┼───────────────┐    │
             ▼                                ▼    │
      ┌─────────────┐                  ┌───────────┴─┐
      │    WORK     │◄─────(14)────────│    REST     │
      │    運動      │──────(4)────────►│    休息      │
      └──┬───┬──────┘                  └──┬──┬───────┘
         │   │(5) とばす                  │  │(15) とばす
         │   └────────────────────────────┘  │(16) ＋二十秒 ↺ self
         │  ┌─────────────┐                  │
         └─►│    REPS     │◄─────(14)────────┘
    (4) w/  │  運動・回数   │──(6)(7)(8)──────►  next phase
    zero    │             │──(9) overrun ↺ self
    rest    │             │──(13) AMRAP extend ↺ self
            └──┬────┬─────┘
      (11) EMOM│    │(12) AMRAP cap
      fail-out ▼    ▼
   ────────────────────────────────────────────────────────
   from ANY live phase (PREPARE / WORK / REPS / REST):

        ┃┃ (17)                    ✕ or back (20)
           ▼                              ▼
      ┌─────────┐  続ける (18)(19)  ┌──────────────┐
      │ PAUSED  │◄─────────────────►│  QUIT_SHEET  │
      │  休止    │──────(20)────────►│              │
      └─────────┘                   └──┬────┬──────┘
                       つづける (21) ───┘    │(22) ここまでを記録する
                                             │(23) 記録せずに終える ×2 → GYM.HOME
                                             ▼
   ────────────────────────────────────────────────────────
                          ┌──────────────┐
     last segment (26) ──►│   COMPLETE   │──(33) 閉じる───► GYM.HOME
     EMOM fail-out (11)──►│     記録      │──(34) もう一度─► LIBRARY.DETAIL
     AMRAP cap (12) ─────►└──────────────┘
     partial save (22) ──►
```

### C.3 Gating per engine

| Engine | Work segments | Rest segments | Cap | Fail-out |
|---|---|---|---|---|
| `INTERVAL_CIRCUIT` | AUTO at `plannedMs` | AUTO | — | — |
| `AMRAP` | MANUAL (済) | AUTO | hard cap ends the session mid-segment; partial reps captured via the wheel | — |
| `FOR_TIME` | MANUAL | none | none — counts up | — |
| `FOR_TIME_WITH_REST` | MANUAL | AUTO, **not skippable, not extendable** | — | — |
| `EMOM` | AUTO_AT_CAP (open inside the minute) | AUTO (the remainder) | window end closes the cell | `MARK_MISSED` — recorded, session continues |
| `EMOM_ASCENDING` | AUTO_AT_CAP | AUTO | window end closes the cell | `END_SESSION` — **this is the protocol's terminating condition** |
| `FIXED_SETS` | MANUAL | AUTO | — | — |

The `目安で自動的に進む` setting upgrades MANUAL to AUTO on REPS segments for `AMRAP`, `FOR_TIME`,
`FOR_TIME_WITH_REST` and `FIXED_SETS` only. It never applies to EMOM (the grid already gates) and is
meaningless for INTERVAL_CIRCUIT.

### C.4 Skip-back double-tap

```kotlin
/** Pure. First tap restarts the current segment; a second within the window steps back one. */
class BackTapResolver(private val windowMs: Long = 2_000) {
    private var lastTapAt: Long? = null
    fun resolve(nowMs: Long): BackAction {
        val prev = lastTapAt
        lastTapAt = nowMs
        return if (prev != null && nowMs - prev <= windowMs) {
            lastTapAt = null                       // consume, so a third tap restarts the cycle
            BackAction.PREVIOUS_SEGMENT
        } else BackAction.RESTART_SEGMENT
    }
}
```

`PREVIOUS_SEGMENT` on the first real segment degrades to `RESTART_SEGMENT` — never back into 支度.
Stepping back **deletes** the previous segment's result before re-seeking, or the record double-counts
it, and calls `Timeline.reopen(ordinal)` to un-shift downstream by the same delta.

---

## D. The cue engine

At `gym/cue/CueEngine.kt`, owned by `GymViewModel`, disarmed on pause / background / stop.

### D.1 Scheduling

Cues are **derived from the timeline, not from ticks.** On entering each segment, compute its cue
schedule once and post each with a single `Handler.postDelayed` keyed by `(segmentOrdinal, cueId)`;
cancel the whole key set on any transition, pause, or skip. Recompute after `extend()` — ＋二十秒 moves
the 3-2-1.

### D.2 The table

| Cue | Trigger offset | Haptic | Tone (default on) | Speech (default off) | Audio focus |
|---|---|---|---|---|---|
| `SESSION_START` | end of PREPARE | one-shot 400ms amp 200 | `TONE_PROP_ACK` 300ms | exercise name | request −150ms, abandon +450ms |
| `COUNT_TICK` | `plannedMs − 3000`; suppressed when `plannedMs < 3500` | one waveform `[0,60,940,60,940,60]` / `[0,120,0,120,0,120]` | 3 × `TONE_PROP_BEEP` 80ms at +0/+1000/+2000 | — | request −150ms, **hold through `INTERVAL_END`**, abandon +3450ms |
| `INTERVAL_END` | `plannedMs` | one-shot 400ms amp 255 | `TONE_PROP_BEEP2` 300ms | next exercise name | shares the `COUNT_TICK` window; if that was suppressed, −150ms / +450ms |
| `HALFWAY` | `plannedMs / 2`, only when `≥ 20_000` and phase == WORK | `[0,40,120,40]` / `[0,90,0,90]` | `TONE_PROP_BEEP` 60ms | 「半分」 | −150ms / +350ms |
| `LAST_ROUND` | start of the final round's first segment | `[0,100,140,100]` / `[0,160,0,160]` | — | 「最後の巡」 | speech only |
| `REP_DONE` | on 済 | `EFFECT_CLICK` | — | — | none |
| `EMOM_FAIL` | window closes with work open | `[0,200,100,200,100,200]` / full amp | `TONE_PROP_NACK` 400ms | 「時間切れ」 | 0 / +550ms |
| `AMRAP_CAP` | `prepareMs + capMs` | one-shot 600ms amp 255 | `TONE_PROP_BEEP2` ×2 | 「終わり」 | 0 / +750ms |
| `SESSION_COMPLETE` | entering COMPLETE (complete sessions only; partial gets `REP_DONE` alone) | one-shot 600ms amp 255 | descending three-note (§D.5) | 「終わり」 | 0 / +900ms |
| `EXTEND_ACK` | ＋二十秒 | `EFFECT_TICK` | — | — | none |
| `SKIP_BACK_ARMED` | first `◁` tap | `EFFECT_TICK` | — | — | none |

**Never use TTS for the 3-2-1.** Engine latency is variable and it will drift audibly against the
haptic.

### D.3 Haptics — the API split, concretely

```kotlin
class GymHaptics(context: Context) {
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)                    // API 31
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    // Cues are alarm-class, not notification-class: a workout cue must survive silent mode.
    //
    // CORRECTION to design §3.6, which says "VibrationAttributes on 31+". The class landed in API 30,
    // but vibrate(VibrationEffect, VibrationAttributes) is only public from API 33. So:
    // VibrationAttributes on 33+, and the AudioAttributes overload (API 26+) for 29–32, which achieves
    // the same USAGE_ALARM routing. Both paths are required at minSdk 29.
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val vibAttrs by lazy {
        VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build()
    }
    private val audioAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun play(effect: VibrationEffect) {
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) vibrator.vibrate(effect, vibAttrs)
        else @Suppress("DEPRECATION") vibrator.vibrate(effect, audioAttrs)
    }

    fun cancel() = vibrator.cancel()   // MUST be called on pause, quit sheet, and ON_STOP
}
```

**The 3-2-1 is one waveform fired once at T−3000, not three scheduled callbacks.** Three `postDelayed`s
accumulate scheduler jitter against a tone sequence that does not; one waveform cannot drift against
itself. Amplitude control requires `hasAmplitudeControl()` — when false, `createWaveform` silently falls
back to on/off at full strength, which is acceptable; do not branch.

*Optional progressive enhancement (API 30+):* where `areAllPrimitivesSupported(PRIMITIVE_TICK,
PRIMITIVE_THUD)`, replace `countTick` with a `VibrationEffect.Composition` of three ticks at scale 0.6
and `intervalEnd` with a thud at 1.0. Keep the waveform path as the fallback; do not make it the only
path.

### D.4 Tones and audio focus

```kotlin
/**
 * Focus is requested PER CUE WINDOW and abandoned as soon as the window closes — never held for the
 * whole session, or the user's music stops for twenty minutes. AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK is
 * the case Android's own docs name a fitness prompt as the example of.
 *
 * Caveat: the system does NOT auto-duck when the user is listening to SPEECH content, so cues mix over
 * a podcast rather than duck under it. Expected behaviour, not a bug to chase.
 */
fun openWindow() { /* AudioFocusRequest(GAIN_TRANSIENT_MAY_DUCK), lazily create ToneGenerator */ }
fun closeWindow(afterMs: Long) { /* abandonAudioFocusRequest on a delayed post */ }
fun release() { /* remove callbacks, abandon focus, tg.release() */ }
```

`openWindow()` at the window's −150ms, `closeWindow(tail)` after its last tone. `release()` on
`ON_STOP` and on `onCleared` — **a leaked `ToneGenerator` holds an `AudioTrack` and is the standard way
this component becomes a battery bug.**

**The 3-2-1 and the interval-end share one focus window** (open T−3150, close T+450). Four
request/abandon cycles inside three seconds thrashes the ducking ramp and sounds like a stutter.

### D.5 The completion tone

`ToneGenerator` has no pitch control, so design §10's "descending three-note" is not expressible with
it. Per `00-plan.md` §2 row 12:

1. **Synthesize.** ~900ms of PCM16 — three 260ms sine segments at 660 / 550 / 440 Hz with 20ms
   raised-cosine envelopes — into a `ShortArray`, played through a one-shot `AudioTrack` in
   `MODE_STATIC` with `USAGE_ASSISTANCE_SONIFICATION`. Zero assets, zero dependencies, about 40 lines,
   and it is the one moment of ceremony the feature has.
2. **Fallback.** `TONE_PROP_NACK` (a descending pair) at 400ms. Honestly close.

**Do not add an audio asset file** — a dependency in all but name, defeating the constraint the tone
choice was made under.

### D.6 Speech

Default **off** (design §3.6), `QUEUE_FLUSH` always — a stale 「半分」 must never delay the next cue —
with an `utteranceId` per cue so `UtteranceProgressListener` can drop the focus window at `onDone`. On
`LANG_MISSING_DATA` / `LANG_NOT_SUPPORTED` for `Locale.JAPANESE`, fall back silently to tones and never
prompt for a voice download mid-workout.

**Recommendation for §14.2's open question** (`00-plan.md` §7 Q2): keep the default off, but
**auto-enable when TalkBack is active** (`AccessibilityManager.isTouchExplorationEnabled`). That
resolves the accessibility case without changing the default for everyone else.

### D.7 Disarm matrix

| Event | `haptics.cancel()` | cancel pending posts | `tones.closeWindow(0)` | `tts.stop()` |
|---|---|---|---|---|
| pause | yes | yes | yes | yes |
| quit sheet opens | yes | yes | yes | yes |
| skip fwd/back | no | yes (rearm for the new segment) | no | yes |
| `ON_STOP` | yes | yes | `release()` | `stop()` |
| COMPLETE | after the completion cue | yes | after +900ms | no |

---

## E. Lifecycle and process death

### E.1 What is persisted, at which transition

| Transition | Writes |
|---|---|
| 始める | INSERT session (routineId, version pin, name, tier, engine, `compiled_hash`, `started_at`, `started_at_elapsed`, `boot_anchor_ms`) |
| every segment close (auto, 済, skip) | INSERT `session_result` **+** UPDATE clock anchors, **one transaction** |
| ＋二十秒 | UPDATE the pending segment's `added_ms` + clock anchors |
| skip-back ×2 | DELETE the previous result + UPDATE clock |
| pause | UPDATE `paused_at_elapsed`, `paused_at_wall`, anchors |
| resume | UPDATE `paused_accum_ms`, clear `paused_at_*`, anchors |
| ここまでを記録する | close partial + UPDATE `complete = 0, finished_at, active_ms, rounds_completed` |
| 記録せずに終える | DELETE session + cascade |
| last segment closes | close + UPDATE `complete = 1, …` |
| rating tap | UPDATE `rating`, `rating_cr10` |
| foreground reconcile | INSERT n back-filled results + UPDATE clock, one transaction |

Roughly 4 transitions/minute, each one small transaction. **The invariant: the DB is authoritative for
everything up to the last transition. Nothing between transitions is recoverable, and the recovery code
must not pretend otherwise.**

### E.2 Cold-start reconstruction

```kotlin
suspend fun reconstruct(rec: OpenSessionRecord, lib: Map<String, Exercise>): Reconstruction {
    // 1. Recompile from the PINNED routine_version, never from the live routine. A routine edited
    //    since the session started must not retro-change the record.
    val tl0 = compile(rec.routineSnapshot, rec.tier, lib)
    require(tl0.hash() == rec.compiledHash) { "timeline drift" }      // else → not resumable

    // 2. Replay the persisted results to move the frontier and re-shift the elastic segments.
    var tl = tl0
    for (r in rec.results.sortedBy { it.ordinal }) {
        tl = if (tl.segments[r.ordinal].open) tl.close(r.ordinal, r.actualMs, r.actualReps, r.skipped)
             else tl.markClosed(r.ordinal, r.actualMs, r.addedMs)
    }

    // 3. Re-anchor the clock and derive the phase from stateAt.
    val clock = reanchor(rec.clock, SystemClock.elapsedRealtime(), System.currentTimeMillis())
    return Reconstruction(tl, clock, tl.stateAt(clock.elapsedMs()))
}
```

**There is no phase field to restore.** The phase is derived, always, from `stateAt(elapsed)` against a
replayed timeline — the same code path that runs live. One source of truth, and process death does not
get a second one.

### E.3 Reboot detection

`elapsedRealtime()` counts from boot and includes deep sleep — which is exactly why it is the session
clock — but it resets to ~0 on reboot, so a stored `startedAtElapsedMs` from a previous boot is
meaningless and makes elapsed run *backwards*. Every clock write therefore persists a wall anchor
beside it:

```kotlin
/** currentTimeMillis − elapsedRealtime ≈ the wall-clock instant this device booted. Stable within a
 *  boot to a few ms; jumps on reboot, and drifts by exactly the amount of any clock adjustment. */
fun bootAnchor(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

private const val BOOT_TOLERANCE_MS = 5_000L      // NTP nudges and rounding, not a reboot
private const val STALE_AFTER_MS = 4 * 60 * 60 * 1000L

fun resumability(rec: OpenSessionRecord, nowWall: Long, nowElapsed: Long): Resumability {
    val sameBoot = abs((nowWall - nowElapsed) - rec.clock.bootAnchorMs) < BOOT_TOLERANCE_MS
    // Second, independent check: within a boot, elapsedRealtime can only move forward.
    val monotonic = nowElapsed >= rec.clock.startedAtElapsedMs
    val ageMs = nowWall - rec.lastWriteWallMs
    return when {
        !sameBoot || !monotonic -> Resumability.REBOOTED       // 続ける removed
        ageMs >= STALE_AFTER_MS -> Resumability.STALE          // same
        rec.results.isEmpty()   -> Resumability.NOTHING_TO_SAVE
        else                    -> Resumability.RESUMABLE
    }
}
```

**Both checks are needed.** The boot anchor catches a reboot even when the wall clock also changed; the
monotonicity check catches the pathological case where a clock adjustment happens to make the anchors
agree across a reboot.

For `REBOOTED` or `STALE`, `recoveredActiveMs` is computed purely from stored values
(`lastWriteElapsed − startedAtElapsed − pausedAccumMs`) with **no reference to the current
`elapsedRealtime` at all**. The gap between the last write and the reboot is unknowable and is never
credited.

For `RESUMABLE`, re-anchoring keeps the same origin — nothing needs rewriting, because
`startedAtElapsedMs` is still valid within this boot. A session killed while **running** therefore
returns with the wall-clock gap counted as active time. Deliberate, and it matches the
`elapsedRealtime` doctrine — but it is also why the resume prompt exists: the user gets to say whether
that time was real. 続ける accepts it; 記録する uses the conservative last-transition figure instead.

### E.4 Screen-on

Per `00-plan.md` §2 row 1, the gym owns this (see `01-shell.md` §A.7). `keepAwake` is true only for the
**live player routes** — PREPARE, WORK, REPS, REST, PAUSED, QUIT_SHEET. `COMPLETE`,
`GYM.LIBRARY.DETAIL` and the resume prompt are excluded. `FLAG_KEEP_SCREEN_ON` only, never a
`SCREEN_BRIGHT_WAKE_LOCK`.

### E.5 Phase 1 has no foreground service — the consequences, stated

The screen stays on and the timeline is derived from a monotonic clock, so a backgrounded session is
**correct on return**; only the audio cues go quiet. Concretely in Phase 1:

- `ON_STOP` → cancel pending cues, `haptics.cancel()`, `tones.release()`, `tts.stop()`.
- `ON_START` → `reconcile()` (transition 27), then re-arm cues for the segment `stateAt` lands on.
- No notification, no `POST_NOTIFICATIONS`, no `FOREGROUND_SERVICE_HEALTH`, no `ACTIVITY_RECOGNITION`,
  no new manifest entries, no R8 keep rule.

When the `health` service lands in Phase 3, **the only thing that changes in this spec is the disarm
matrix (§D.7)** — the state machine, the timeline, and the persistence schedule are already
service-agnostic. That is the point of deriving everything from `stateAt`.
