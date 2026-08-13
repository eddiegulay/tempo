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

## Q4 — Numerals — **as specced, no change**

Counts render kanji, countdowns render arabic, wheels render arabic mid-spin. This matches the flip
clock, which already renders arabic for a ticking value and is the closest existing precedent.

## Q5 — What 鍛錬 owes Home — **nothing, in Phases 1–4**

Focus leaves no trace on Home and neither does 鍛錬. `calendar-design.md`'s rule 1 fought to keep
that corner quiet and the next-event cluster already spent the budget there. Revisit only if the user
asks for it.
