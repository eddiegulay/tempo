# 鍛錬 — orchestrator decisions on the open questions

`00-plan.md` §7 left five questions open. Three of them blocked work. All three are now closed.
This file is binding in the same way `00-plan.md` §2 is: do not re-litigate it in implementation.

---

## Q3 — Recon Ron's 18-step table — **VERIFIED, seed it as written** (2026-08-13)

`00-plan.md` §2 row 2 required the `02-data.md` §F.2 transcription to be checked against the source
before seeding, because a second, plausible-looking table had been generated during planning and the
two disagreed in the middle rows.

**The check has been done.** The fuller 31-step 1st Recon progression chart was obtained
independently of the planning session. Our 18-step table is exactly **steps 11–28** of that chart:

| §F.2 step | totals | 31-step chart step |
|---:|---:|---:|
| 1 | 26 | 11 |
| 18 | 60 | 28 |

All eighteen rows match set-for-set, not merely in their totals. One row in the scraped 31-step copy
(its step 15) sums to 35 against a printed total of 34; §F.2's corresponding row 5 — `10 7 6 6 5` —
is the internally consistent one and is correct. Every §F.2 row satisfies both invariants stated in
that section: the five sets sum to the stated total, and the total is `24 + 2 × step`.

The **rejected** planning-generated table is confirmed wrong — it lost the rotational increment
pattern that the source chart carries continuously across all 31 steps.

**Action for Track A:** seed `02-data.md` §F.2 verbatim. Record this verification in
`BuiltInCatalog.kt`'s provenance comment, citing Pasieka, *Marine Corps Gazette*, December 1981, and
noting the 31-step corroboration.

Sources: [1st Recon Pull-Up Program chart](https://pdfcoffee.com/1st-recon-pull-up-program-pdf-free.html) ·
[Pasieka, "Over the Top on 'Dead Hang' Pull-Ups"](https://sustainableevolution.wordpress.com/wp-content/uploads/2013/06/recon-ron-pullup.pdf) ·
[Recon Ron Pull-up Program](https://en-academic.com/dic.nsf/enwiki/7956045)

---

## Q1 — `training_plan` — **ship the documented fallback, defer the picker**

`02-data.md` §A.9's rule stands: *a day is honoured if you trained, or if you trained the day before.*

The table is created in schema v1 exactly as §A.9 specifies — the schema is not phased, only the UI
is — but **no picker is built into `GYM.SETTINGS`**, and no row is ever written in Phases 1–3. The
streak reads the fallback.

Why: the fallback delivers design §5.2's promise ("days you honoured the plan") with no plan model,
and a picker asks the user to declare a training schedule before they have trained once — which is
exactly the kind of setup ceremony this product refuses elsewhere. The table existing unused costs
nothing and means adding the picker later needs no migration.

## Q2 — Speech cues — **default off, auto-enabled under TalkBack**

`03-player.md` §D.6's proposal is accepted unchanged. The stored preference defaults to `false`;
`AccessibilityManager.isTouchExplorationEnabled` at session start enables speech for that session
without writing the preference. The `GYM.SETTINGS` toggle reflects the stored value, not the
effective one — with a subtitle explaining the override when touch exploration is on.

## Q6 — `GymFault` copy — **the binding mapping** (raised in Phase 0 review)

The P1 agent claimed only `StoreFull` had documented copy and gave six cases an invented holding
string. That claim was wrong; review found the strings. This table is now binding — every entry is
sourced, and no case is left to a placeholder.

| case | message | action | source |
|---|---|---|---|
| `StoreCorrupt` | `記録を読めません` | `もう一度` | `04` §6 :1190 |
| `StoreUnavailable` | `記録を読めません` | `もう一度` | `04` §6 :1190 |
| `StoreReset` | `記録を読めません` | `もう一度` | `04` §6 :1190 |
| `Unknown` | `記録を読めません` | `もう一度` | `04` §6 :1190 |
| `StoreFull` | `空き容量が足りません` | **none** | `04` §6 :1190, and :370 / :671 both state no retry |
| `RoutineGone` | `この型は削除されています` | **none** | `04` §6 :1182 (もう一度 disabled) |
| `SessionGone` | `この記録は削除されています` | **none** | see below |
| `Rejected` | `保存できませんでした` | **none** | `01` :768, `03` :558 |

`SessionGone` is the only case §6 does not carry outright. It takes `04` §6 :1182's sentence with its
noun swapped 型 → 記録 — both nouns are the string table's own vocabulary (`この記録を削除しますか`,
`記録の中身`), and the grammar is unchanged. That is applying documented copy, not inventing it, and
it is authorised here so the case is not shipped behind a placeholder. Everything else stays verbatim.

**`記録を読めません` is what keeps the `Loadable` promise.** It does not contain ありません-as-emptiness
and cannot be misread as 記録はありません. `CalendarFeedbackTest` pins that distinction — keep it pinned.

## Q7 — `Numerals.kt` must not redeclare `kanjiExtended` (raised in Phase 0 review)

`04` §7 lists `kanjiExtended` under `gym/Numerals.kt`; `00-plan.md` §5 P5 and `03` :22 put it on
`data/JapaneseDate.kt`. **P5 wins — it already shipped there.** `Numerals.kt` owns only
`coefficientLabel` and `durationKanji`, and delegates to `JapaneseDate.kanjiExtended`. A second
formatter is a divergence bug waiting to happen.

## Q8 — `CycleDots` overflow — **the spec's rule, not a width test** (raised in Phase 0 review)

`03` :172 states the rule plainly: **when `total > 9`, render `三巡目 / 十二巡` as text.** The P3 agent
shipped `cycleDotsFit(total, dotSize, gap, available)`, a width predicate that admits up to seventeen
dots at a normal portrait width. Delete it. The predicate is `total > 9` and nothing else — the
countability half of the rationale is the operative half, and the width figure in `03` :172 (210.dp)
is itself arithmetically loose, since `Arrangement.spacedBy` yields `n − 1` gaps, not `n`.

## Q9 — リーコン・ロン's `primary_metric` — **MOST_VOLUME, not HIGHEST_STEP** (Phase 1 review)

`02` §F.5 :1429 seeds リーコン・ロン with `primary_metric = HIGHEST_STEP`. `04` contradicts it twice
independently — :281 and :885 both map `FIXED_SETS → 最高負荷` (weighted volume) — and `04` §6 :1131's
best-tile list has **no tile for `HIGHEST_STEP` at all**: 最高巡数 / 最高反復 / 最速 / 最高負荷 / やった回数.
`04` :1164's いちばん上 is the *movement ladder's* hardest rung (`いちばん上 足上げ腕立て`), a different
surface; it is not a routine tile and must not be repurposed as one.

Two files say 最高負荷 and one says HIGHEST_STEP, so **`02` §F.5 is the outlier and it loses.** Change
the seed to `MOST_VOLUME`.

Nothing is lost by this. The step you have reached is still rendered — by `stepFor`, in the
progression block, as 第九段 / 十八段のうち, which is where a ladder belongs. The 最高 tiles are a
different element answering a different question, and before this ruling リーコン・ロン's tile block
rendered **empty forever**: the only `personal_record` row it could produce was a `HIGHEST_STEP` row
that `bestTilesFor` had no label for and dropped. A user with fifty sessions saw exactly what a user
with zero saw. That is the `Loadable` doctrine failing in the direction `00-plan` §2 row 15 exists to
prevent, on one of three shipped built-ins.

`HIGHEST_STEP` stays in the CHECK constraint and the `BestMetric` enum — the column is not the
problem — but **nothing seeds it in v1**, and no Japanese label is to be invented for it.

**Also binding, and separate:** `bestTilesFor` must **fail open, not closed.** The やった回数 tile is
engine-independent and always sourced from `timesDone`; it must be emitted whenever `timesDone > 0`
even if no metric tile resolves. The real NoAttempts condition is `timesDone <= 0 && bests.isEmpty()`.

## Q10 — A rest renders as the seconds you set it to — **bare seconds, not `durationKanji`**

`EngineRows.restLabel` renders rests through `durationKanji`, so a 60-second round rest reads 一分 on
`GYM.LIBRARY.DETAIL` while the builder wheel (`04` :350) and the settings row (`01` :735) both show
六十秒 for the same value. The user sets 六十秒 and reads 一分 for the same routine.

**The rule, stated once:** a duration the user **chose** renders as the value they chose — bare
seconds via `kanjiExtended(n) + "秒"`, `なし` at zero. A duration the app **measured** renders through
`durationKanji` (一分三十秒), which is `01` :826's `secondsLabelJa` contract and stays untouched.
Setting and reading are different acts, and a wheel that says 六十秒 must not be echoed back as 一分.

Rests are set. They render as seconds, everywhere. Pin `restLabel(60)` in a test so it cannot drift.

## Q11 — `loadScale` below eight non-zero days — **the flag is the deliverable, Phase 3 honours it**

`02` §D.3 ends "with fewer than 8 non-zero days, fall back to fixed cutoffs and document it" but names
no cutoffs. Three millisecond thresholds are exactly the kind of number `00-plan` §2 forbids
inventing, so Track A was right to refuse and right to keep `usedFallback`.

**Resolution:** `usedFallback` is a live contract, not a dangling flag. Phase 3's month ink-grid must
render every non-zero day at a **single mid level** when it is true, rather than ranking days against
a sample of two. That is not a fallback cutoff; it is declining to rank an insufficient sample, which
is what the fixed cutoffs were there to avoid in the first place. Percentile ranking resumes at eight
non-zero days. Record this in the ink-grid's KDoc when Phase 3 builds it.

## Q12 — The five ladder rungs' `sec/rep` and `name_en` — **ratified as written**

`02` §F.1 gives the five push-up ladder rungs a difficulty and nothing else, while `name_en` is
`NOT NULL` (it is what TalkBack reads) and `seconds_per_rep` is needed by `RoutineEstimate`. Track A
supplied `secondsPerRep = 2.0` for all five — 腕立て伏せ's own value reused across its family, rather
than five separately invented estimates — and direct translations for the names. Both were disclosed
rather than slipped in.

**Ratified.** Reusing one measured value across a movement family is a defensible derivation and is
strictly better than five guesses; a translation of a documented Japanese name invents no fact. Added
to `02` §F.1's table so the seed's provenance is complete.

## Q13 — Staleness copy above two hours — **delegate to `relativeDayJa`** (pages review)

`03` §A edge case 4 lists five words — `さっき` (<10m), `一時間前`, `二時間前`, `昨日`, `三日前` — and the
HOME agent correctly reported that **between 4h and 24h none of them is true**: 二時間前 is false for a
five-hour-old session and 昨日 is false for one begun this morning. It refused to invent a sixth word
and escalated. Right call.

**The sixth word already exists and is sourced.** `01` :642 declares
`relativeDayJa(then, now, zone): String  // "三日前" / "きのう" / "きょう"` — the same feature's own
relative-day formatter, used by `lastResultLine` for the routine cards on this very page.

**The rule:** below two hours the label is elapsed-time-shaped; at and above it, the label is
day-shaped and comes from `relativeDayJa`.

| age | label | source |
|---|---|---|
| < 10m | `さっき` | `03` §A edge 4 |
| < 2h | `一時間前` | `03` §A edge 4 |
| < 4h (the resumability horizon) | `二時間前` | `03` §A edge 4 |
| ≥ 4h | `relativeDayJa(...)` → `きょう` / `きのう` / `三日前` | `01` :642 |

This fixes the five-hour case (→ きょう, which is true) and it resolves the 昨日 / きのう split that
`03` §A edge 4 and `01` :642 would otherwise have shipped side by side on one screen. **One formatter
is authoritative**, exactly as `kanjiExtended` and `ensoSweep` were made authoritative rather than
duplicated (§Q7).

Past the horizon the day boundary is a **calendar** boundary in the device's zone, never an hour
count — the same reason `00-plan` §2 row 14 computes every date bucket in Kotlin rather than in UTC
SQL. What that buys, and what no set of hour edges can: a **five**-hour-old session reads きのう while
a **fourteen**-hour-old one reads きょう, because midnight separates them and duration does not.

Below the horizon elapsed time still wins, deliberately. Twenty minutes after midnight, きのう is true
and useless — the fact the user wants is that they stopped a moment ago. The day words take over only
once the elapsed number has stopped being the more informative of the two.

## Q14 — One resume prompt on screen — **the mount is idempotent, all three pages keep theirs**

The review contradicted itself: the `LIBRARY.INDEX` blocker said *add* a third `ResumePromptHost`
mount, two minors said hoist a single one into `GymShell` and delete both page mounts. The plumbing
agent declined to do half of a two-sided fix and escalated. Also right — landing the shell half alone
would have produced doubled dialogs.

**Ruling: the HOME agent's `ResumePromptMount` is the answer.** The host claims the modal and only the
first mounted one draws, so every page may mount it unconditionally and at most one dialog is ever on
screen. No shell edit, no cross-page rule for a future page author to forget, and no page can deadlock
by omitting the mount — which was the actual `LIBRARY.INDEX` blocker: `startSession` publishes the
prompt and **deliberately holds the start lock**, so a page that raises it without drawing it wedges
始める permanently.

Every page that can call `startSession` mounts it. That is the rule, and it is one sentence.

## Q15 — Phase 2's five benchmark routines — **verified, seed `02` §F.5 as written**

Checked independently of the planning session, because `00-plan` §2 forbids inventing numbers and
these are the numbers most often misremembered. Every row of `02` §F.5's Phase 2 table is confirmed:

| routine | prescription | confirmed |
|---|---|---|
| シンディ | AMRAP 20 min — 5 pull-ups / 10 push-ups / 15 air squats | ✓ and **5/10/15, not 10/15/15** |
| チェルシー | EMOM 30 — the same 5/10/15 | ✓ |
| バーバラ | 5 rounds for time — 20 / 30 / 40 / 50, **3 min rest between rounds** | ✓ |
| マーフ | 1 mile run · 100 / 200 / 300 · 1 mile run | ✓ |
| デス・バイ | +1 rep per minute until failure | ✓ as a *format*, not a fixed workout |

Two notes for the seeding agent:

**マーフ's run legs** are already solved in `02` §F.5 and the solution stands: `DURATION` with a NULL
`prescribed_sec` violates the CHECK, so they are `MAX_EFFORT` with `note = '一マイル'`, and the player
treats a `MAX_EFFORT` `LOCOMOTION` station as an open segment closed by 済. No schema change, and the
distance stays a note rather than becoming a column the rest of the feature would have to understand.
The 20lb/14lb vest is **not** modelled — it is optional in the source and there is no load column.

**デス・バイ is a format, not a canonical workout.** `02` §F.5 picks `burpee` and that choice is ours,
exactly as タバタ's exercise choice is ours (the 1996 protocol was a cycle ergometer). Say so in the
catalog comment, the way the タバタ entry already says 種目は自由に.

Sources: [The Girls / benchmark workouts](https://journal.crossfit.com/article/benchmark-workouts-2) ·
[Murph](https://www.crossfit.com/murph-workout) ·
[Chelsea and Barbara](https://www.boxrox.com/crossfit-benchmark-workouts-the-girls/)

## Q16 — 毎分's window is sixty seconds, and is not authorable (Phase 2 groundwork)

The builder-logic agent reported that **no spec table gives a label for an EMOM's interval**, and
concluded that a non-60-second EMOM is therefore unauthorable. It refused to invent the string. Right
on both counts, and the conclusion is not a gap — it is the answer.

**毎分 means "every minute." The name is the interval.** `04` §6's own table glosses 毎分増 as
"+1 rep each minute". An interval wheel would let a user build a 毎分 routine that runs every forty
seconds, at which point the engine's name on its own card is false. Both seeded EMOM routines —
チェルシー and デス・バイ — are sixty seconds, because that is what the format is.

So: `interval_sec` stays a **column** (a stored routine carries it, the compiler reads it, and a
future engine may vary it), but the builder **offers no wheel for it** and every routine authored in
Phase 2 gets 60. No string is needed, because nothing is being labelled. `engineRows(EMOM)` renders
毎分 and that is the complete prescription.

If a variable window is ever wanted it needs a name of its own in `04` §6 first — it would be a
different engine, not a parameter of this one.

## Q17 — Migration notices: two, not three (Phase 2 groundwork)

`04` §6 :1140 documents exactly two engine-change notices (the 段階 and 毎分 ones). The agent needed a
third — for dropping a time cap or an interval — and wrote none, flagging it instead.

**Ratified: there is no third notice.** The two documented losses are invisible (a rest silently
becoming zero, sets moving to a progression table); dropping a cap or an interval removes a row that
is on screen, under the finger that just changed the engine. A notice explaining a change the user
watched happen is noise, and `00-plan` §4.1's restraint about rationed copy applies. The KDoc records
the reasoning at the site.

## Q18 — デス・バイ's `rounds` — **ratified as a derived materialisation bound**

`02` §F.5 gives デス・バイ neither `rounds` nor `time_cap_sec`, correctly: the format has neither, and
`03` §C.3 makes the fail-out the terminating condition. But
`CHECK (rounds IS NOT NULL OR time_cap_sec IS NOT NULL)` demands one and `Builder.emom` lays `1..rounds`
with no `extendOneRound` — only AMRAP is `extensible` — so a number is structurally mandatory.

The seeding agent **derived** rather than chose: minute *m* asks for *m* burpees, §F.1 measures a
burpee at 4.0 s/rep, so `floor(60 / 4.0) = 15` is the last minute whose prescription still fits inside
its own window at catalogue pace. It is written as an expression (`ascendingMinuteBound`), not a
literal, and documented as a **materialisation bound, not a prescription**.

**Ratified.** This is the same move §Q12 sanctioned: a value computed from a catalogued measurement
beats a guess, and expressing it as a formula means it tracks the catalogue if that 4.0 is ever
retuned. Nobody reaches minute 15 of death-by-burpees anyway — the bound exists so the compiler has a
list to lay, not to tell the user when to stop.

Rejected, correctly: Stew Smith's ten-minute Death by Push-ups. That is a real sourced number
belonging to a **different workout**, and borrowing it would be the RECONDO error in miniature.

## Q19 — The time cap is not authorable in Phase 2 (builder review)

`04` §3 edge case 11 gives wheel ranges for station rest, round rest, 巡数, 回数 and 秒数 — and **none
for a 制限時間**. The builder agent therefore built no cap wheel and left every authored AMRAP on
`migrateDraft`'s documented 二十分, read-only. Correct, and the same shape as §Q16.

**Ratified.** A range invented here would silently become the ceiling on every AMRAP anyone ever
writes. The row renders the stored value and does not open. A documented range in `04` §3 unlocks it.

## Q20 — Two ratifications from the builder unit

**The estimate renders `目安 四十秒`, not the mock's `目安 〇:四十`.** §Q10 already governs: a duration the
app **computed** goes through `durationKanji`. The mock's glyph form is produced by no formatter in
this app and appears in no string table, so transcribing it would have meant writing a third numeral
format for one line. The agent disclosed rather than transcribed — right call.

**The engine row offers seven chips where the mock draws six.** `FOR_TIME_WITH_REST`
(`完走 ・ 休息あり`, `04` §6 :1117) is included because Phase 2 seeds バーバラ with it, and omitting it
makes a shipped built-in unbuildable — a user could open バーバラ in 写して作る and be unable to save
what they were looking at. The mock predates the seed table; the seed table wins.

## Q21 — A wheel must be able to show the value it is editing (builder review)

Three wheels could not represent values that **shipped built-ins already hold**: 巡数 offers 1..20 but
チェルシー is 30 rounds; 巡の間の休息 steps by 15 but タバタ rests 10; 回数 offers 1..100 but マーフ has
stations at 200 and 300. `TempoValueWheel` resolves an absent value with
`values.indexOf(selected).coerceAtLeast(0)` — it silently seeds **row 0**.

So opening 編集 or 写して作る on any of those three and merely unfolding the row rewrites the value,
with no warning and no undo. That is data loss triggered by looking at something.

**The rule, and it generalises past this bug:** a wheel opened on an existing value must contain that
value. Merge the current value into the option list when it is absent, rather than widening every
range to cover every seedable number — the ranges are a sensible authoring vocabulary and the
built-ins are deliberately outside it. `TempoValueWheel`'s `indexOf(...).coerceAtLeast(0)` is the
underlying hazard; anywhere a caller can pass a value outside its own list, this bug is latent.

## Q22 — `GymRepository.summary()` — **add it; two pages are incomplete without it**

`04` §4's mock and §6's tile table both specify **three** tiles on `GYM.RECORDS.INDEX`
(今月 / 活動時間 / これまで), and §4's HISTORY mock has the subtitle 八十六回 ・ 二千四百分. Both need
lifetime totals. `02` §C never declared a read for them, so nothing implements one — the INDEX agent
shipped two tiles and the HISTORY agent shipped no subtitle, each refusing to fake it.

Both refusals were right. The INDEX agent named three derivations it rejected and every one is wrong:
`routines`' `timesDone` misses archived routines, `routineBests` misses routines only ever done
partially, and `weeklySeries(52)` is a year rather than a lifetime. A number that is nearly the
lifetime total is worse than no tile, because nobody can tell it is wrong.

**Add the read.** `suspend fun summary(): Loadable<LifetimeSummary>` on `GymRepository`, backed by a
`COUNT(*)` and a `SUM(active_ms)` over `session` — one scan, no new table, no schema change. This is
not inventing a number; it is implementing a documented UI element the data layer was simply never
asked for. It must be a `Loadable` like every other read: a lifetime total that silently reads zero
over a quarantined store is the empty-versus-failed confusion this feature has now caught five times.

## Q23 — `SessionMissing` must map to `GymFault.SessionGone`

`04` §4 edge case 9 is currently dead code. `GymStore` throws a private `SessionMissing`, and
`toGymFault` has no arm for it, so it falls through to `GymFault.Unknown` — meaning a session deleted
in another shell state renders 記録を読めません with a もう一度 that can only fail again. That is exactly
the outcome edge case 9 exists to prevent, and §Q6 already ratified
`この記録は削除されています` with **no action word** for this case.

Add the arm. The fault type, the copy and the page's `popsOnFault` check were all already written and
correct — only the mapping between them was missing, which is why nothing caught it.

## Q24 — The 28-day chart gate must not accuse someone of not training

`volumeGate` reads `trainingLoad()`, whose store implementation returns `Ready(null)` whenever the
**last 28 days** are empty, never computing `historyDays`. So a user with years of history who comes
back after a month away is told 二十八日ぶん たまると 出ます — "it will appear once 28 days have
accumulated". That is false for them, and it hides data they actually have: `volumeSeries` queries
back 83 days by default.

**The gate is about whether enough history EXISTS, not about whether the recent window is busy.** A
returning user must see their chart, and the sentence must only appear for someone who genuinely has
not accumulated 28 days of history. Fix it where the fact lives — `historyDays` must be computed from
the user's actual span, not left at zero because a recent window happened to be empty.

This is the same failure as §Q9 and the corruption bug: an absence of recent rows being reported as an
absence of history.

## Q4 — Numerals — **as specced, no change**

Counts render kanji, countdowns render arabic, wheels render arabic mid-spin. This matches the flip
clock, which already renders arabic for a ticking value and is the closest existing precedent.

## Q5 — What 鍛錬 owes Home — **nothing, in Phases 1–4**

Focus leaves no trace on Home and neither does 鍛錬. `calendar-design.md`'s rule 1 fought to keep
that corner quiet and the next-event cluster already spent the budget there. Revisit only if the user
asks for it.
