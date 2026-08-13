# Tempo — Exercise mode (鍛錬) design spec

Design-only document. No code in `app/` is touched by this file.

Same contract as `calendar-design.md`: every element below names the actual token (`c.ink`, `c.inkSoft`,
`c.inkFaint`, `c.card`, `c.hair`, `c.accent`, `c.enso`), the actual font (`Mincho` / `Gothic`), and
concrete `sp` / `dp`, matched to the density already used in `HomeScreen.kt`, `FocusScreen.kt` and
`CalendarScreen.kt`.

Research backing every numeric claim is cited inline. Where the evidence is weak, it says so.

---

## 0. Scope, and four hard product rules

**What this is.** A guided workout player. You pick a routine, the phone walks you through a sequence
of exercises — work, recovery, work, recovery — keeping time, and at the end it writes a durable
record: date, duration, rounds, reps, and how it felt.

- New `Screen` values: `Screen.Exercise` (index), `Screen.Session` (player), `Screen.Record` (history),
  `Screen.RoutineCompose` (builder).
- New package `io/eddiegulay/tempo/exercise/`, mirroring `calendar/`.
- Persistence: two JSON ledgers in `filesDir`, modelled on `BlockadeRepository` — `routines.json`
  (user-authored routines) and `sessions.json` (append-only history). **No Room.** `CONTRIBUTING.md`
  forbids new third-party dependencies, and Room would be one.
- Permissions in Phase 1: **none**. (Phase 3 adds `POST_NOTIFICATIONS` + `FOREGROUND_SERVICE_HEALTH` +
  `ACTIVITY_RECOGNITION` — §8.4. It is deliberately not in the first release.)

**Four hard product rules that shape the whole spec:**

1. **鍛錬 is not in the dock.** The dock is three tabs and stays three tabs. Exercise mode is a *mode*,
   like Focus — you enter it deliberately, through the same gesture. (§1)
2. **The ensō is the timer.** Tempo already draws an ensō arc on Home. Every fitness app in existence
   signals work-vs-rest with a full-bleed red or blue screen; Tempo cannot and should not. The ring
   depletes instead. (§3.2)
3. **Nothing auto-advances past a rep-based exercise.** If the routine says 20 push-ups, the app waits
   for you. A countdown that moves on while you are still on rep 14 is a lie in the record. (§3.5)
4. **A partial session is a real session.** Quitting at minute six saves eight of twenty exercises and
   six minutes, honestly labelled. Apps that force save-or-lose train people to fake completions.

**Explicitly out of scope**, and staying out: accounts, cloud sync, social feeds, leaderboards, video
demonstrations, rep counting via camera or accelerometer, meal logging, body weight tracking.

---

## 1. Entry point — the mode gesture

### 1.1 Recommendation: extend the Focus long-press into a two-mode choice

Today, long-pressing the Home clock opens a confirm dialog and drops you into Focus. Exercise mode
takes the same door: **long-press the clock → the dialog now offers two modes.**

```
        ┌───────────────────────────────┐
        │                               │
        │   集中                    →   │   ← Mincho 17.sp, c.ink
        │   時計だけの画面                │      Gothic 12.sp, c.inkFaint
        │   ─────────────────────────   │   ← 1.dp c.hair
        │   鍛錬                    →   │
        │   体を動かす                   │
        │                               │
        │                     やめる     │   ← Mincho 13.sp, c.inkFaint
        └───────────────────────────────┘
```

**Why this and not the alternatives:**

- *A fourth dock tab.* The dock is a `RoundedCornerShape(percent = 50)` pill sized for three glyphs;
  a fourth compresses it and, worse, promotes a mode you use for 20 minutes three times a week to the
  same rank as Search, which you use forty times a day. Frequency should set prominence.
- *A new glyph on Home.* Home currently has exactly four marks: ensō, clock, the top-right cluster, and
  the 静 seal. `calendar-design.md §1.1` already fought this fight — "Home has exactly one ambient text
  cluster in that corner and it must stay a single quiet object." A fifth mark loses the argument for
  the same reason.
- *Reachable only from Search.* Buries it. Nobody would find it.

Grouping the two full-screen modes behind one gesture is also honest about what they are: both take
over the phone, both are things you *enter*, both end by returning to Home.

### 1.2 Dock and back behaviour

| Screen | Dock | Back |
|---|---|---|
| `Exercise` (index) | visible, frosted (`wetPaper`) | → Home |
| `Session` (player) | **hidden**, full-bleed, like Focus | → quit sheet (§3.7), never straight out |
| `Record` (history) | visible, frosted | → Exercise |
| `RoutineCompose` | **hidden** | → Exercise (discard prompt if dirty) |

`resetToHome()` in `LauncherViewModel.kt:205` must clear pending routine drafts and any quit-sheet
state — but **must not** discard a live session (§8.3).

---

## 2. The index page (鍛錬)

Header is the fixed idiom, identical geometry to `CalendarScreen.kt:98-122`: title `鍛錬` in Mincho
26.sp / ls 3.sp, subtitle `令和八年 ・ 六月十七日` at 13.sp / ls 4.sp, padding `start 28, end 22,
top 24, bottom 10`. Header action on the right: `作る` (make one) in `c.accent`.

```
  鍛錬                                        記録   作る
  令和八年 ・ 六月十七日
  ─────────────────────────────────────────────────────

  つづき                                              ← only when a session was interrupted
  ┌───────────────────────────────────────────────┐
  │  七分間                          六分十四秒 経過 │
  │  八種目まで進んだ                     続ける →   │
  └───────────────────────────────────────────────┘

  よく使う                                            ← Mincho 12.sp ls 3.sp, c.inkFaint
  ┌───────────────────────────────────────────────┐
  │  七分間                                        │  ← Mincho 16.sp c.ink
  │  十二種目 ・ 三十秒 / 十秒 ・ 約七分            │  ← Gothic 13.sp c.inkSoft
  │                                    十四回      │  ← Gothic 11.sp c.inkFaint (times done)
  └───────────────────────────────────────────────┘
  ┌───────────────────────────────────────────────┐
  │  シンディ                                      │
  │  懸垂五 ・ 腕立て十 ・ スクワット十五 ・ 二十分  │
  │                          最高 十七巡           │  ← PR line, c.accent when set this month
  └───────────────────────────────────────────────┘

  型                                                  ← "forms" — the shipped presets
  … Recon Ron, Tabata, Murph, Barbara …

  自分の型                                            ← user-authored routines
  …
```

Card geometry matches `EventCard`: `RoundedCornerShape(18.dp)`, `c.card` fill, `padding(h 18, v 16)`,
item `padding(v 5.dp)`, list `horizontal 22.dp` with bottom `contentPadding 96.dp` for the dock.

Empty state for 自分の型: `型はまだありません` in Mincho 17.sp ls 4.sp `c.inkFaint`, mirroring
`予定はありません`.

---

## 3. The session player — the core of the feature

### 3.1 Four states, one chrome

The player has four phases: **支度** (prepare), **運動** (work), **休息** (rest), **完了** (complete).
The top bar, ensō, and bottom control bar are **pixel-identical** across 運動 and 休息 — only the
centre text and the ring colour change. Layout that jumps every thirty seconds is nauseating at speed,
and that is a real finding from interval-timer usability work, not a preference.

Portrait, immersive (`FocusScreen.kt:86-113` supplies the bar-hiding and the
`OnWindowFocusChangeListener` re-hide fix verbatim). **Portrait, not landscape like Focus** — the phone
is on the floor and you are looking down at it from a plank.

### 3.2 The ensō is the timer — recommendation

Every reference app makes the whole screen red for work and blue for rest, on the reasoning that
"people process colour faster than text." Tempo has a two-colour palette and a single rationed accent;
a full-bleed vermillion screen would be the loudest thing the app has ever done and would break the
washi ground that `Modifier.tempoBackground` establishes everywhere else.

**The ensō solves it.** `HomeScreen.kt:162` already draws an ensō arc with `Canvas` / `drawArc`. In the
player it becomes a **220.dp ring, 3.dp stroke, that depletes clockwise from the 12 o'clock gap** as the
interval runs.

| phase | ring | ring behaviour |
|---|---|---|
| 支度 | `c.inkFaint` | depletes over the 5s prepare |
| 運動 | `c.accent` | depletes over the work interval |
| 休息 | `c.enso` | depletes over the rest interval |
| 完了 | `c.accent`, full | closed circle, drawn once, held |

The ensō's open gap is the natural progress origin, and a closing circle as the workout completes is
the single best piece of visual language this app could possibly have for "you finished." It costs one
`drawArc` sweep-angle parameter.

Additionally: a **1.dp hairline session bar** (`c.hair`, filled `c.inkSoft`) sits directly under the top
bar, showing progress through the *whole* routine. Two progress tracks — one for the interval, one for
the session — is the consensus recommendation, and the two here are typographically distinct enough that
they never compete.

### 3.3 The 運動 (work) slide

```
┌────────────────────────────────────────────────┐
│  ✕                 三巡目 ・ 四種目中 三      │  ← Mincho 12.sp ls 3.sp, c.inkFaint
├────────────────────────────────────────────────┤
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░░░░░░░░ │  ← 1.dp session hairline
│                                                │
│                                                │
│              ╭───────────────╮                 │
│             ╱                 ╲                │
│            │      腕立て伏せ    │               │  ← Mincho Medium 24.sp ls 2.sp, c.ink
│            │                   │               │
│            │        0:23       │               │  ← Mincho 88.sp tabular, c.ink
│            │                   │               │
│             ╲                 ╱                │
│              ╰───────────────╯                 │  ← ensō ring, 220.dp, c.accent, depleting
│                                                │
│                 ● ● ● ○ ○                      │  ← CycleDots: rounds, 6.dp, 12.dp gap
│                                                │
│                                                │
│         次 ・ 休息 十五秒 → プランク            │  ← Gothic 13.sp, c.inkFaint
│                                                │
├────────────────────────────────────────────────┤
│     ◁              ┃┃              ▷           │  ← bottom 96.dp, targets 64.dp
└────────────────────────────────────────────────┘
```

- Countdown numerals: **Mincho 88.sp, tabular figures**, `c.ink`. Legibility target is arm's length on
  the floor, ~1.5 m.
- **`FlipClock` is deliberately not used here.** It is the right component for Focus, where a digit
  changes once a minute. A split-flap animation firing every second for a thirty-second interval is
  thirty animations per station and roughly four hundred per session, in an app whose motion doctrine
  (`calendar-design.md §5`) is "nothing bounces, nothing overshoots, nothing loops." Plain numerals
  cross-fading at 120ms is the correct register.
- Exercise name is Japanese where a natural term exists (腕立て伏せ, 懸垂, 空気椅子), katakana where the
  loanword is what people actually say (スクワット, プランク, バーピー). §12 has the full table.
- Round dots reuse `CycleDots` (`FocusScreen.kt:210`) verbatim.
- Next-up is **one muted line**, never a card. It is orientation, not content.

### 3.4 The 休息 (rest) slide

Same chrome, same ensō position, same dots. What changes: the ring is `c.enso`, the countdown drops to
72.sp, and **the next exercise is promoted to hero**, because the entire job of a rest interval is to
get you ready for the next thing.

```
│              ╭───────────────╮                 │
│             ╱      休息       ╲                │  ← Mincho 15.sp ls 6.sp, c.inkFaint
│            │                   │               │
│            │        0:08       │               │  ← Mincho 72.sp, c.inkSoft
│            │                   │               │
│             ╲     つぎ         ╱                │
│              ╰───────────────╯                 │
│                                                │
│                  プランク                       │  ← Mincho Medium 26.sp, c.ink
│                  三十秒                         │  ← Gothic 14.sp, c.inkSoft
│               肘は肩の真下に                     │  ← Gothic 12.sp, c.inkFaint — one form cue
│                                                │
│              ＋二十秒        とばす ▷            │  ← Mincho 14.sp ls 2.sp, c.accent
```

**`＋二十秒` is not optional.** Add-time on the rest screen is the single most-requested control in
every logging app surveyed. It is also what makes autoregulation possible without a settings trip.

### 3.5 Rep-based exercises — recommendation: soft-timed, self-paced

This is the hardest problem in the spec and the reference apps genuinely disagree. Freeletics and Nike
Training Club are fully self-paced (you tap to advance); Seven time-boxes everything at 30s and treats
reps as advisory; Peloton just keeps moving.

**Recommendation: a rep slide shows an estimated countdown that paces you but does not gate you.**
The clock runs and displays. At zero it does **not** advance — it flips to counting up, quietly, and
waits for `済` (done).

```
│            │      腕立て伏せ    │               │
│            │                   │               │
│            │       二十回       │               │  ← Mincho 76.sp, c.ink — REPS is the hero
│            │                   │               │
│             ╲   目安 0:38      ╱                │  ← Gothic 13.sp c.inkFaint; "+0:07" once over
│              ╰───────────────╯                 │     ring is c.accent, depleting
│                                                │
│         ┌────────────────────────────┐         │
│         │            済               │         │  ← full width, 64.dp tall, Mincho 20.sp
│         └────────────────────────────┘         │     ls 4.sp, c.accent on c.card
```

- The estimate is `reps × secondsPerRep` from a per-exercise table (push-up 2.0s, pull-up 3.0s, squat
  1.8s, sit-up 1.7s, dip 2.5s, burpee 4.0s), used **only** for the pacer and the routine's "about 七分"
  estimate. It never advances anything.
- **Long-press `済` opens a rep adjustment** — a `TempoWheel` (extracted from
  `EventComposeScreen.kt:496`, currently `private`) pre-set to the prescribed count. This is what turns
  Exercise mode from a stopwatch into a data source: prescribed vs actual, per station.
- A setting, `目安で自動的に進む` (auto-advance on the estimate), default **off**. Some people want the
  metronome. They should have to ask for it.
- **Tap-to-count is rejected outright.** Essentially nobody ships it as the primary mechanic, because it
  adds motor and cognitive load exactly when you have none spare.

### 3.6 Cues — haptics first, tones second, speech last

Tempo has no audio of any kind today, and its whole identity is quietness. But a workout where the phone
is on the floor and your face is in a plank is exactly the case where the screen cannot be the interface.
The resolution is a layered cue vocabulary with conservative defaults.

| cue | when | haptic (always) | tone (default **on**) | speech (default **off**) |
|---|---|---|---|---|
| session start | after 支度 | one long 400ms | low–high pair | exercise name |
| last three seconds | T−3, −2, −1 | 3 × 60ms @ 940ms | three short beeps | — |
| interval end | T = 0 | 400ms one-shot | one long, distinct timbre | next exercise name |
| halfway | 50% of work, **≥20s only** | 2 × 40ms | single soft tone | 「半分」 |
| last round | start of final round | 2 × 100ms | — | 「最後の巡」 |
| session complete | 完了 | 600ms | descending three-note | 「終わり」 |

Implementation notes that matter:

- **Never use TTS for the 3-2-1.** Engine latency is variable and it will drift audibly. Tones come from
  `ToneGenerator(STREAM_NOTIFICATION, …)` with `TONE_PROP_BEEP` / `TONE_PROP_BEEP2` — zero assets, zero
  dependencies, which is the only option `CONTRIBUTING.md` permits anyway.
- **Audio focus:** request `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` **per cue**, then abandon — never hold it
  for the whole session, or you stop the user's music for twenty minutes. Android's own docs name a
  fitness app's prompt as the canonical ducking example. Caveat worth knowing: the system does **not**
  auto-duck when the user is listening to *speech* content, so cues will mix over a podcast rather than
  duck under it.
- **Haptics:** `VibratorManager` on API 31+, `VIBRATOR_SERVICE` below (minSdk is 29, so both paths are
  needed). Attach `VibrationAttributes` with `USAGE_ALARM` on 31+ so cues survive silent mode — a
  workout cue is alarm-class, not notification-class.
- Speech, if enabled, is `TextToSpeech` with `QUEUE_FLUSH` (a stale 「半分」 must never delay the next
  cue), an `utteranceId`, and a graceful fallback to tones when no Japanese voice is installed.

### 3.7 Pause, skip, quit

- **休止** (pause) is the centre control, 64.dp, always visible. Pausing freezes the ensō — the freeze
  *is* the confirmation. Paused state shows elapsed session time and 続ける.
- **◁ / ▷** flank it. `◁` behaves like a music player: first tap restarts the current interval, a second
  tap within 2s goes back one. This is the most forgiving affordance for a mid-set fat-finger.
- **✕** is top-left, deliberately as far from the thumb zone as the screen allows, and opens a
  **three-way sheet — never a two-way dialog**:

```
        ┌───────────────────────────────┐
        │   鍛錬を終えますか              │  ← Mincho 16.sp ls 2.sp, c.inkSoft
        │   六分十四秒 ・ 二十種目中 八    │  ← Gothic 13.sp, c.inkFaint
        │                               │
        │   ここまでを記録する        →  │  ← primary, c.accent
        │   記録せずに終える          →  │  ← destructive, c.inkFaint
        │   つづける                  →  │  ← escape — NOT 「やめる」
        └───────────────────────────────┘
```

The escape is 続ける, not やめる. "Cancel the cancel" is the classic failure of this exact pattern, and
Tempo already uses やめる to mean "abandon" in the composer — reusing it here to mean "don't abandon"
would be a genuine bug in the language.

---

## 4. The record screen (記録) — post-session

One hero number, not six equal tiles. Comparison beats absolutes.

```
              ╭───────────────╮
             ╱                 ╲                    ← ensō closes: 900ms, LinearOutSlowInEasing,
            │       七分間       │                     drawn once, then still. No confetti.
            │                   │
            │      六分十四秒     │                  ← Mincho 64.sp, c.ink — HERO
             ╲     活動時間      ╱                    ← Gothic 12.sp ls 3.sp, c.inkFaint
              ╰───────────────╯

     二十種目        五巡        三百二十回
     ─────────────────────────────────────         ← three tiles, Gothic 20.sp / label 11.sp

     前回より 二十二秒 速い                          ← comparison, Mincho 14.sp, c.accent
     四日 連続                                       ← streak, c.inkSoft
     自己最高                                        ← PR chip, c.accent — only when real

     どうでしたか                                    ← Mincho 14.sp ls 3.sp, c.inkFaint
     ┌────────┐ ┌────────┐ ┌────────┐
     │  楽    │ │ ちょうど │ │ きつい  │              ← one tap, skippable, 56.dp tall
     └────────┘ └────────┘ └────────┘

     内訳                                            ← per-station breakdown, scrollable
     腕立て伏せ      0:41   済   二十回
     プランク        0:30   済
     スクワット      0:38   済   十八回              ← actual ≠ prescribed shows both
     バーピー        —      とばした                  ← skipped, c.inkFaint
```

- **The rating is asked immediately, before the streak and the PR chip** — while the sensation is fresh,
  and before the celebratory content biases it.
- **Three options, not a 1–10 scale.** Full RPE post-workout is too much friction, and the RIR/RPE
  literature is explicit that self-rating accuracy *degrades* as rep counts rise — which is exactly the
  territory bodyweight circuits live in. Three buckets map to CR10 ≈ 4 / 7 / 9 internally, which is
  enough for the load maths in §7.4.
- **Calories are not shown.** Stanford measured consumer trackers at 27–93% median error on energy
  expenditure while the same devices did heart rate to under 5%. Tempo has no heart rate at all. A
  number that wrong, presented that confidently, is the kind of thing this app exists in opposition to.
  (If it is ever added: `kcal/min = MET × 3.5 × kg / 200`, MET 8.0 for vigorous circuits, 7.5 for
  calisthenics, labelled 目安.)
- **Partial sessions** render the same screen with a `途中まで ・ 二十種目中 八` chip under the title,
  no PR chips, no ensō closure — dignified, not punitive.
- Bottom: `予定に入れる` — add the next session to the device calendar. Tempo already reads and writes
  `CalendarContract` (commit `764c31d`), so this is a differentiated CTA that costs almost nothing.

---

## 5. History and records (記録 index)

Reached from the 鍛錬 header. Four things, in this order:

**5.1 The month grid.** A calendar heatmap, but rendered as Tempo would: a 7-column grid of `4.dp` ink
dots, opacity scaled by session load (`c.ink` at 0.15 / 0.35 / 0.6 / 0.9), rest days blank, today
ringed in `c.hair`. Not coloured squares — ink density is the same information in this app's language.

**5.2 Streak (連続) with forgiveness.** The streak counts **days you honoured the plan**, not days you
exercised, so a correctly-taken rest day extends it rather than breaking it. Two missed *planned* days
per month are forgiven. A streak that punishes a deload is actively harmful — deloading is training.

**5.3 Per-routine bests.** For each routine: best time (for-time), most rounds (AMRAP), most reps,
hardest variation reached. Auto-detected, shown as `最高`.

**5.4 Two charts, no more.** Sessions per week (bars), total active minutes (line). Both raw `Canvas`
in the ensō/`LineIcon` idiom — there are no chart primitives in the project and there should not be a
dependency for two charts. Fitness apps drown in analytics nobody reads.

Tapping any history row reopens the §4 record screen. One component, two entry points.

---

## 6. Customisation — the routine builder (型を作る)

A screen, not a sheet — same argument as `calendar-design.md §3.1`.

```
  型を作る                              やめる  保存

  名前          ______________________          ← Mincho 20.sp, like 題名
  方式          [ 巡回 ]                        ← engine picker (§7.2)

  種目                                          ← reorderable list
  ┌──────────────────────────────────────┐
  │ 腕立て伏せ        二十回        ⋮⋮   │     ← ⋮⋮ = drag handle
  │ 懸垂              十回          ⋮⋮   │
  │ スクワット        三十秒        ⋮⋮   │
  └──────────────────────────────────────┘
                                    ＋ 加える

  種目の間の休息      十五秒                     ← TempoWheel
  巡の間の休息        六十秒
  巡数                五巡

  ─────────────────────────────────────
  約 十八分 ・ 三百回                            ← live estimate from §3.5's per-rep table
```

- Each 種目 row taps into a picker: exercise from the library (§12), then either 回数 (reps) or 秒数
  (seconds) via `TempoWheel`.
- **The builder warns, it does not block.** If two adjacent stations share a movement pattern, a
  `c.inkFaint` line appears: `腕立て伏せ と ディップス は続けて置かない方がよい` — stacking two pushes
  means station two starts on an already-fatigued muscle and a locally depleted phosphocreatine pool, so
  you get less total work for the same cost. The whole point of a circuit is that the rest for muscle A
  is the work interval for muscle B. But it is the user's routine, and a warning is where that ends.
- Any shipped 型 can be long-pressed → 写して作る (duplicate and edit). This is how most custom routines
  will actually be born.

---

## 7. Data model and engines

### 7.1 Models

Plain data classes, epoch-millis storage with `java.time` accessors, computed `key` for list keys —
styled exactly on `CalendarModels.kt:18-53`. `@Immutable` on everything UI-facing.

```kotlin
@Immutable
data class Exercise(
    val id: String,              // "pushup", "pullup", …
    val name: String,            // 腕立て伏せ
    val pattern: Pattern,        // H_PUSH, V_PULL, SQUAT, HINGE, CORE, LOCOMOTION, PLYO
    val secondsPerRep: Float,    // pacer estimate only — never advances anything
    val difficulty: Float,       // volume coefficient, §7.4
    val cue: String?,            // one-line form cue, shown on the rest slide
)

@Immutable
data class Station(
    val exerciseId: String,
    val prescription: Prescription,   // Reps(20) | Duration(30.sec) | MaxEffort
)

@Immutable
data class Routine(
    val id: String,
    val name: String,
    val engine: Engine,
    val stations: List<Station>,
    val restBetweenStations: Int,     // seconds
    val restBetweenRounds: Int,
    val rounds: Int,                  // or timeCapSec for AMRAP
    val origin: String?,              // "CrossFit, 2004-12-29" — provenance, shown on the card
    val builtIn: Boolean,
)

@Immutable
data class Session(
    val routineId: String,
    val startedAt: Long,              // epoch millis
    val activeMs: Long,               // excludes pauses
    val completedStations: List<StationResult>,
    val roundsCompleted: Int,
    val complete: Boolean,
    val rating: Rating?,              // Easy | JustRight | Hard
) {
    val key: String get() = "$routineId:$startedAt"
    fun date(): LocalDate = …
    val totalReps: Int get() = completedStations.sumOf { it.actualReps ?: 0 }
}
```

Reuse `Loadable<T>` and the `WriteOutcome` / fault shapes from `calendar/CalendarOutcome.kt` rather
than inventing parallel ones. The doctrine there is load-bearing and test-enforced: **loading ≠ empty
≠ failed.** An unreadable `sessions.json` must never render as 記録はありません.

### 7.2 Engines — seven shapes, one player

All the researched protocols collapse into seven engines. One timeline compiler handles all of them,
which is the single most important structural decision in this document.

| engine | shape | covers |
|---|---|---|
| `INTERVAL_CIRCUIT` | work s / rest s / stations / rounds | 7-minute HICT, Tabata, most custom routines |
| `AMRAP` | fixed time cap, unlimited rounds, score = rounds + reps | Cindy |
| `FOR_TIME` | fixed total reps, count **up**, no cap | Murph, Angie |
| `FOR_TIME_WITH_REST` | fixed rounds + mandatory timed rest between them | Barbara |
| `EMOM` | fixed interval, fixed reps, N rounds; rest = the remainder | Chelsea |
| `EMOM_ASCENDING` | +1 rep each interval until you cannot finish in time | Death By |
| `FIXED_SETS` | day/step table of sets × reps, advancement rule | Recon Ron, Armstrong, Fighter Pull-Up |

Ship `INTERVAL_CIRCUIT` + `FIXED_SETS` in Phase 1, the rest in Phase 2 (§13).

### 7.3 Persistence

Two files in `filesDir`, both `org.json`, both following `BlockadeRepository.kt` — synchronous seed of a
`MutableStateFlow` in `init`, writes on `Dispatchers.IO`, `res/xml/backup_rules.xml` inclusion.

- `routines.json` — user-authored routines only. Small, rewritten whole.
- `sessions.json` — append-only history. ~400 bytes per session; 500 sessions ≈ 200 KB, which is fine
  to hold in memory and rewrite. **If it passes 2 MB, shard by year** (`sessions-2026.json`) rather than
  reaching for a database.
- Built-in routines are Kotlin constants, not JSON — they ship with the binary and are never written.

Borrow the **monotonic clock guard** from `BlockadeRepository.kt:49`: store a `lastSeen` high-water mark
so winding the system clock back cannot fabricate streaks or PRs.

### 7.4 What gets computed, and what does not

**Difficulty-weighted volume is the headline metric, not raw reps.**
`weightedVolume = Σ (reps × difficulty(variation))`. This matters more than it sounds: when someone
progresses from knee push-ups (0.5) to full (1.0) to feet-elevated (1.3), their *raw* rep count falls
and every chart shows regression at the exact moment they got stronger. The coefficient fixes it.

**Session load** uses Foster's session-RPE, which is validated specifically for high-intensity
functional training: `load = CR10 × durationMinutes`, with the three-button rating mapping to 4 / 7 / 9.
From that: `weeklyLoad`, and `monotony = mean(dailyLoad) / sd(dailyLoad)` over 7 days including rest days
as zero. Monotony above 2.0 is a genuine warning and, unusually for a computed metric, produces
*actionable* copy: 「同じ調子が続いています」 — vary the hard/easy pattern.

**ACWR is computed but not displayed as risk.** The 0.8–1.3 "sweet spot" is widely cited and has
substantially failed to replicate; it is mathematically coupled and was built on arbitrary bucketing.
Use it internally as a ramp-rate governor with the boring, defensible 10%-per-week cap, and surface at
most a soft nudge. **Never** show an injury-risk percentage. Suppress entirely until 28 days of history
exist.

**Not computed:** calories (§4), a composite "fitness score" (opaque scores lose all trust the first
time they disagree with how you feel), lifetime totals on the dashboard.

---

## 8. Timing architecture and lifecycle

### 8.1 The one rule: never accumulate ticks

`Handler.postDelayed` drifts cumulatively and its `uptimeMillis` timebase **stops during deep sleep**.
A backgrounded session would silently under-count. `SystemClock.elapsedRealtime()` is monotonic and
includes deep sleep. That is the clock.

The session compiles to a **timeline** — a `List<Segment>` of `(startMs, endMs, phase, stationIndex,
round)` — and the current state is a **pure function of elapsed milliseconds**:

```kotlin
data class SessionClock(
    val startedAtElapsed: Long,               // SystemClock.elapsedRealtime()
    val pausedAccumulatedMs: Long = 0,
    val pausedAtElapsed: Long? = null,
) {
    fun elapsedMs(now: Long = SystemClock.elapsedRealtime()): Long =
        (pausedAtElapsed ?: now) - startedAtElapsed - pausedAccumulatedMs
}

// Ticks render. They never advance state.
while (isActive) {
    _playerState.value = timeline.stateAt(clock.elapsedMs())
    delay(50)
}
```

Rotation, backgrounding, resume, and "jump to station 4" all fall out for free, and `stateAt` is a pure
Android-free function — which is exactly the testing strategy the repo already uses (`groupByApp`,
`layoutTategaki`, `faultCopy` are all pure and JVM-tested for the same reason).

Self-paced rep slides break purity, so model them as **open-ended segments** closed by the `済` tap,
after which downstream offsets shift. The resulting `List<CompletedSegment>` **is** the summary data —
§4's breakdown is a render of it, not a separate calculation.

### 8.2 Screen-on

Extend the existing `DisposableEffect` at `TempoApp.kt:94` — keyed on `screen == Screen.Focus ||
screen == Screen.Session` — rather than adding a new one. The comment there is bug history: commit
`1f49dfc` fixed a disposal-scoped release leaking the flag when HOME is pressed and Tempo is not the
default launcher. `FLAG_KEEP_SCREEN_ON` only, never a `SCREEN_BRIGHT_WAKE_LOCK`.

### 8.3 Process death

A `ViewModel` survives rotation but not process death. Persist `startedAtElapsed`,
`pausedAccumulatedMs`, and completed segments to `sessions.json` **on every phase transition** (a few
writes per minute, trivially cheap). Also persist a `System.currentTimeMillis()` anchor: because
`elapsedRealtime` is uptime-relative, a reboot makes it run backwards, which is how you detect a stale
session and offer 続ける / 記録する / 捨てる on cold start.

### 8.4 The foreground service — deliberately deferred to Phase 3

**Phase 1 ships without one.** The screen stays on, the timeline is derived from a monotonic clock, so
even if the user backgrounds the app the state is *correct on return* — only the audio cues go quiet.
For a launcher whose exercise screen you are looking at, that is an acceptable first release, and it
avoids a Play Console declaration, a demo video, and three new permissions for v1.

When it is added, the research is unambiguous about the shape:

- Type **`health`** — the docs name "exercise trackers" explicitly. `shortService` has a hard ~3-minute
  cap and is unusable; `mediaPlayback` would be a lie; `specialUse` is the fallback if review pushes back.
- `health` requires a runtime prerequisite. Use **`ACTIVITY_RECOGNITION`** — it is a normal runtime
  permission with a plausible rationale, and it avoids the `BODY_SENSORS` / `READ_HEART_RATE` family,
  which are while-in-use restricted and would block starting the service from the background.
- Start it **lazily**, from `onStop()` of the session screen, and stop it on return. Started from a
  visible activity, which is the only path needed and the only one Android 15 reliably allows.
- Notification uses `setUsesChronometer(true)` + `setChronometerCountDown(true)` with
  `when = currentTimeMillis + remainingMs`, so it ticks **without** a per-second update — a large battery
  win — plus 休止 / とばす / 終える actions.
- Android 15's 6-hour FGS timeout applies to `dataSync` / `mediaProcessing`, not `health`.
- New manifest `<service>`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH`, `ACTIVITY_RECOGNITION`,
  `POST_NOTIFICATIONS`, **and an R8 keep rule in `proguard-rules.pro`** — the project has exactly one
  such rule today (for `TempoNotificationListener`) and a framework-instantiated Service needs its own.

---

## 9. What ships in the box

Every preset carries its real provenance on the card. Two corrections to the original brief are baked in
here, and both matter:

**シンディ (Cindy) is 5 / 10 / 15, not 10 / 15 / 15.** 5 pull-ups, 10 push-ups, 15 air squats, AMRAP
20 minutes; first posted on CrossFit.com 2004-12-29. Tom Holland does genuinely do it and quotes it
correctly in interviews — "you don't have to think about anything, you just count your rounds" —
claiming 27 rounds. The 10/15/15 version has no source; it is a garbled recollection. Ship the real one.

**"Recont" is Recon Ron, and it is a pull-up program.** Not "twenty push-ups and a recovery." The
original is LtCol Stanley Pasieka's *Over the Top on "Dead Hang" Pull-Ups*, Marine Corps Gazette,
December 1981: five sets daily, eighteen steps from 26 total reps to 60, two weeks per step, one chosen
day each week at one third volume. **There is no documented RECONDO push-up sequence with real numbers**
and this spec will not invent one. If a push-up ladder with recovery is what is wanted, the honest
matches are Stew Smith's Death by Push-ups (10 EMOM inside a ten-minute plank = 100 reps) or Armstrong's
three max-effort morning sets.

| 型 | engine | structure | tier |
|---|---|---|---|
| 七分間 | `INTERVAL_CIRCUIT` | 12 stations, 30s / 10s, 1–3 circuits | 入門 |
| タバタ | `INTERVAL_CIRCUIT` | 20s / 10s × 8 = 4:00 | 中級 |
| リーコン・ロン | `FIXED_SETS` | 5 sets, 18 steps, 26→60 reps | 上級 |
| シンディ | `AMRAP` | 5 懸垂 / 10 腕立て / 15 スクワット, 20:00 | 中級 |
| チェルシー | `EMOM` | same 5/10/15, EMOM × 30 | 上級 |
| バーバラ | `FOR_TIME_WITH_REST` | 20/30/40/50 × 5 rounds, exactly 3:00 rest | 上級 |
| マーフ | `FOR_TIME` | 1mi / 100 / 200 / 300 / 1mi | 上級 |
| デス・バイ | `EMOM_ASCENDING` | +1 rep per minute until failure | scales itself |

The 七分間 circuit is the ACSM one (Klika & Jordan, *ACSM's Health & Fitness Journal*, May 2013) in its
published order — jumping jacks, wall sit, push-up, crunch, step-up, squat, triceps dip, plank, high
knees, lunge, push-up with rotation, side plank — which deliberately alternates total-body → lower →
upper → core so opposing groups recover while others work. Do not reorder it.

Every preset needs a **scaled tier**. Cindy's official beginner scaling is a 12-minute AMRAP of 3 ring
rows, 6 assisted push-ups, 9 air squats — ship that as 「やさしい」 rather than letting a beginner bounce
off the Rx version.

---

## 10. Motion

Everything is `LinearOutSlowInEasing`. Nothing bounces, nothing overshoots, nothing loops — same
doctrine as `calendar-design.md §5`.

| what | duration | notes |
|---|---|---|
| enter 鍛錬 from the mode dialog | 260ms fade + `scaleIn(0.97f)` | the existing `AnimatedContent` spec, unchanged |
| enter the player | 320ms fade, dock fades out over 160ms | |
| ensō sweep | continuous, driven by `stateAt` | not an `Animatable` — it is a value, redrawn |
| ring colour change on phase | 200ms `animateColorAsState` | the only colour animation in the feature |
| countdown numeral change | 120ms cross-fade | **not** a flip; see §3.3 |
| phase text swap (運動 ↔ 休息) | 180ms fade through | |
| pause | ring freezes, no animation | the freeze is the affordance |
| quit sheet | 240ms slide + fade | matches `BlockConfirmDialog` |
| ensō closure on 完了 | 900ms, drawn once, then still | the one moment of ceremony |

No confetti. Honour `ANIMATOR_DURATION_SCALE` = 0 by drawing every end state immediately.

---

## 11. Token reference (implementer's table)

### Player
| element | font | size | spacing | colour |
|---|---|---|---|---|
| round / station counter | Mincho | 12.sp | ls 3.sp | `c.inkFaint` |
| session hairline | — | 1.dp | full width | `c.hair` / fill `c.inkSoft` |
| ensō ring | — | 220.dp, stroke 3.dp | — | `c.accent` (運動) / `c.enso` (休息) |
| exercise name | Mincho Medium | 24.sp | ls 2.sp | `c.ink` |
| countdown (運動) | Mincho | 88.sp tabular | — | `c.ink` |
| countdown (休息) | Mincho | 72.sp tabular | — | `c.inkSoft` |
| rep hero | Mincho | 76.sp | — | `c.ink` |
| 休息 label | Mincho | 15.sp | ls 6.sp | `c.inkFaint` |
| pacer 目安 | Gothic | 13.sp | — | `c.inkFaint` |
| next-up line | Gothic | 13.sp | — | `c.inkFaint` |
| next exercise (rest hero) | Mincho Medium | 26.sp | — | `c.ink` |
| form cue | Gothic | 12.sp | — | `c.inkFaint` |
| 済 button | Mincho | 20.sp | ls 4.sp | `c.accent` on `c.card`, 64.dp tall, full width − 44.dp |
| ＋二十秒 / とばす | Mincho | 14.sp | ls 2.sp | `c.accent` |
| round dots | — | 6.dp, gap 12.dp | — | `c.accent` (done) / `c.hair` (pending) |
| bottom controls | — | 96.dp bar, targets 64.dp | — | `c.inkSoft` |

### Record
| element | font | size | spacing | colour |
|---|---|---|---|---|
| hero time | Mincho | 64.sp | — | `c.ink` |
| hero label | Gothic | 12.sp | ls 3.sp | `c.inkFaint` |
| tile value / label | Gothic | 20.sp / 11.sp | — | `c.ink` / `c.inkFaint` |
| comparison line | Mincho | 14.sp | ls 2.sp | `c.accent` |
| streak line | Mincho | 14.sp | ls 2.sp | `c.inkSoft` |
| 自己最高 chip | Mincho | 12.sp | ls 3.sp | `c.accent` |
| rating buttons | Mincho | 15.sp | ls 2.sp | `c.inkSoft`; selected `c.accent`, 56.dp tall |
| breakdown row | Gothic | 13.sp | — | `c.inkSoft`; skipped `c.inkFaint` |
| 途中まで chip | Mincho | 12.sp | ls 3.sp | `c.inkFaint` |
| month grid dot | — | 4.dp, 7 cols | — | `c.ink` @ 0.15 / 0.35 / 0.6 / 0.9 |

---

## 12. Japanese strings

| purpose | string | notes |
|---|---|---|
| page title | `鍛錬` | tanren — forging. Reads as discipline, not "fitness" |
| mode dialog: focus | `集中` / `時計だけの画面` | existing mode, now labelled |
| mode dialog: exercise | `鍛錬` / `体を動かす` | |
| header action: build | `作る` | contentDescription `型を作る` |
| header action: history | `記録` | |
| section: frequently used | `よく使う` | |
| section: built-in routines | `型` | kata — form |
| section: user routines | `自分の型` | |
| resume banner | `つづき` / `続ける` | |
| prepare phase | `支度` | shitaku |
| work phase | `運動` | |
| rest phase | `休息` | |
| complete | `完了` | |
| round counter | `三巡目 ・ 四種目中 三` | 巡 = circuit/lap, 種目 = station |
| next | `次` / `つぎ` | |
| done (rep slide) | `済` | |
| add time | `＋二十秒` | |
| skip | `とばす` | |
| pause / resume | `休止` / `続ける` | |
| last round cue | `最後の巡` | |
| halfway cue | `半分` | |
| quit sheet title | `鍛錬を終えますか` | |
| quit: save | `ここまでを記録する` | primary |
| quit: discard | `記録せずに終える` | destructive |
| quit: escape | `つづける` | **not** `やめる` — see §3.7 |
| record hero label | `活動時間` | |
| tiles | `種目` / `巡` / `回` | |
| comparison | `前回より 二十二秒 速い` | |
| streak | `四日 連続` | |
| personal best | `自己最高` | |
| partial chip | `途中まで ・ 二十種目中 八` | |
| rating prompt | `どうでしたか` | |
| rating options | `楽` / `ちょうど` / `きつい` | → CR10 4 / 7 / 9 |
| breakdown heading | `内訳` | |
| skipped | `とばした` | |
| add to calendar | `予定に入れる` | reuses the existing calendar write path |
| builder title | `型を作る` | |
| builder: name / engine | `名前` / `方式` | |
| builder: stations | `種目` / `＋ 加える` | 加える matches the calendar composer |
| builder: rests | `種目の間の休息` / `巡の間の休息` | |
| builder: rounds | `巡数` | |
| builder estimate | `約 十八分 ・ 三百回` | |
| duplicate | `写して作る` | |
| empty routines | `型はまだありません` | mirrors `予定はありません` |
| monotony nudge | `同じ調子が続いています` | |
| scaled tier | `やさしい` | |
| stale session prompt | `続ける` / `記録する` / `捨てる` | |

**Exercise library** (Japanese where a natural term exists, katakana where the loanword is what people
actually say): 腕立て伏せ (push-up) ・ 膝つき腕立て (knee push-up) ・ 懸垂 (pull-up) ・ 斜め懸垂 (ring
row) ・ スクワット (air squat) ・ 空気椅子 (wall sit) ・ 腹筋 (sit-up) ・ プランク (plank) ・ 横プランク
(side plank) ・ ランジ (lunge) ・ ディップス (dip) ・ バーピー (burpee) ・ もも上げ (high knees) ・
ジャンピングジャック (jumping jacks) ・ 踏み台昇降 (step-up) ・ 走る (run).

---

## 13. Delivery

Four phases, each independently shippable and independently useful. Phase 1 is the whole of the
original ask; everything after it is depth.

**Phase 1 — the player.** `Screen.Exercise` + `Screen.Session` + `Screen.Record`. `INTERVAL_CIRCUIT`
and `FIXED_SETS` engines. The timeline compiler and `stateAt` (pure, JVM-tested). Ensō timer, work/rest
slides, soft-timed rep slides, pause/skip/quit, partial saves. Haptics + tones. `sessions.json`.
Presets: 七分間, タバタ, リーコン・ロン. Screen-on via the existing `DisposableEffect`. **No new
permissions, no new dependencies, no service.**

**Phase 2 — customisation and the rest of the engines.** `RoutineCompose` builder, `routines.json`,
`TempoWheel` extracted from `EventComposeScreen.kt` to a shared file. `AMRAP`, `FOR_TIME`,
`FOR_TIME_WITH_REST`, `EMOM`, `EMOM_ASCENDING`. Presets: シンディ, チェルシー, バーバラ, マーフ,
デス・バイ. Scaled tiers.

**Phase 3 — records with depth.** Month grid, forgiving streak, per-routine bests, the two charts,
weighted volume, session load and monotony. `予定に入れる` calendar hand-off. The `health` foreground
service and its notification controls.

**Phase 4 — coaching.** Progression tables for `FIXED_SETS` (Recon Ron's 18 steps, Armstrong's 5-day
week, Pavel's 30-day cycle), double-progression auto-advance on custom routines, the 10%-per-week ramp
governor, deload prompts, adjacent-pattern warnings in the builder.

**Testing.** Everything interesting here is pure and belongs in `app/src/test/`, matching the repo's
existing strategy: timeline compilation for all seven engines, `stateAt` across boundaries and pauses,
open-segment closure, streak-with-forgiveness, PR detection, weighted volume, monotony, the estimate
calculator, and the Recon Ron step table (every row's five sets must sum to its stated total — the
published table is verified correct, so this is a real regression test).

**Also needed at ship:** a `CHANGELOG.md` entry in the existing user-facing prose style, a section in
`docs/USER_GUIDE.md`, and a short safety line in the app — 「痛みを感じたらやめる」, plus a one-time
onboarding note that this is not medical advice.

---

## 14. Open questions

1. **Mode gesture (§1.1)** — is folding 鍛錬 into the Focus long-press right, or should Focus keep its
   direct route and 鍛錬 get its own? Folding is cleaner but adds a tap to a gesture that currently has
   none to spare.
2. **Speech cues (§3.6)** — default off is proposed on identity grounds, but a phone on the floor is
   precisely where an eyes-free interface earns its keep, and TTS is also the TalkBack story. Worth
   reconsidering as default-on with a prominent off switch.
3. **Numerals** — the spec writes counts as kanji (三巡目, 二十回) to match the rest of the app, but the
   countdown as arabic (`0:23`), on the grounds that a kanji countdown changing every second is
   unreadable. That inconsistency is deliberate; it should be sanity-checked against how the flip clock
   already reads.
4. **How much does 記録 owe the Home screen?** Focus leaves no trace on Home. Should a training streak?
   Rule 2 of `calendar-design.md` fought to keep that corner quiet, and the next-event cluster already
   won it.
