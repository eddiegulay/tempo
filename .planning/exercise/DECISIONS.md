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

## Q4 — Numerals — **as specced, no change**

Counts render kanji, countdowns render arabic, wheels render arabic mid-spin. This matches the flip
clock, which already renders arabic for a ticking value and is the closest existing precedent.

## Q5 — What 鍛錬 owes Home — **nothing, in Phases 1–4**

Focus leaves no trace on Home and neither does 鍛錬. `calendar-design.md`'s rule 1 fought to keep
that corner quiet and the next-event cluster already spent the budget there. Revisit only if the user
asks for it.
