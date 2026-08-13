# Phase 4 — what is specified, what is not, and what the gaps need

`00-plan.md` §6's Phase 4 sentence names five things:

> **Phase 4 — coaching.** Progression auto-advance for `FIXED_SETS`, double-progression on custom
> routines, the 10%-per-week ramp governor, deload prompts, ACWR as a governor (**never** displayed as
> risk).

Three of them are sourced and in hand. **Two are not specified anywhere**, and this file is the
evidence for that claim plus the exact list of decisions each one needs before anybody writes a line of
it. Nothing was stubbed for either: `app/src/main` gained no code in this unit. An unsourced feature
half-built is worse than an absent one, because the next reader cannot tell which parts were decided.

The one piece of real work here was verifying that the ACWR restraint actually holds in the shipped
code, and turning that verification into a test — §4.

---

## 1. Double progression on custom routines — **NOT SPECIFIED**

### 1.1 The exhaustive grep

Every occurrence of the phrase, anywhere under `.planning/`:

| file:line | text | what it is |
|---|---|---|
| `exercise-design.md:814` | "double-progression auto-advance on custom routines" | an item in the §13 phase roster |
| `00-plan.md:293` | "double-progression on custom routines" | the same item in the §6 build order |

That is the complete corpus. **Two listings of the feature's name and not one sentence about it.**
Searches that returned **zero** hits across the whole directory: `rep range`, `レンジ`, `二段階`,
`top of the range`, `double progression` outside those two lines. `02-data.md`, `03-player.md` and
`04-library-records.md` — the three files that own schema, player and copy — never mention it.

What *is* specified, and is often mistaken for it:

- `02-data.md:253` — `CHECK (advance_rule IN ('SESSIONS_COMPLETED','WEEKS_ELAPSED','ALL_SETS_MADE','MANUAL'))`.
  `ALL_SETS_MADE` is an enum value with no prose anywhere. It is the obvious mechanism for a
  double progression and it is **named, not defined**: nothing says what "made" means for a station
  whose `prescription_kind` is `DURATION` or `MAX_EFFORT`, and no seeded program uses it
  (`BuiltInCatalog.kt` seeds `WEEKS_ELAPSED`, `SESSIONS_COMPLETED`, `MANUAL` — never this one).
- `GymStore.kt:1729–1752` — `advanceProgression` already implements `SESSIONS_COMPLETED` and
  `WEEKS_ELAPSED` and explicitly does nothing for `ALL_SETS_MADE`, with the KDoc saying "the first
  because Phase 4 owns it". Correct, and the handover note is doing its job.
- `02-data.md:298–309` — `progression_state` carries `current_step_index`, `sessions_at_step`,
  `step_entered_at` and `cycle_day`. There is **no column for a rep target within a step**, which is
  the second variable a double progression needs.

### 1.2 The decisive structural fact

**Classic double progression varies two things: reps within a range at a fixed load, then load with
the reps reset to the bottom of the range. This app models one of them.** There is no weight, load,
band or resistance column anywhere in `Schema.kt` — a grep for `weight|load|kg|resistance` over the
schema returns nothing but `volume_units`, a computed output. `DECISIONS.md` §Q15 already recorded the
consequence in passing: マーフ's 20lb vest "is **not** modelled — it is optional in the source and there
is no load column."

So "double progression" here cannot mean what it means in a barbell program. It has to mean something
else — reps against a *harder variation* (the `H_PUSH` ladder rungs at `04-library-records.md:1154`:
壁腕立て 0.2 · 斜め腕立て 0.4 · 足上げ腕立て 1.3 · アーチャー腕立て 1.6 · 片手腕立て 2.5, around
腕立て伏せ at 1.0), or reps against added time, or something third. **Which of those it means is the
first decision, and no document makes it.**

### 1.3 A live defect this investigation turned up

The builder offers 段階 (`FIXED_SETS`) as an authorable engine — `BuilderScreen.kt:646`,
`ENGINE_CHOICES` — and a routine authored on it **cannot be saved**. `GymStore.insertVersion` writes
`putNull("progression_program_id")` (`GymStore.kt:1678`, with a comment asserting "the builder cannot
offer that engine" — it can), and `migrateDraft` nulls both `rounds` and `timeCapSeconds` for
`FIXED_SETS` (`BuilderDraft.kt:252`). That trips **two** `routine_version` CHECK constraints:

- `Schema.kt:114` — `CHECK ((engine = 'FIXED_SETS') = (progression_program_id IS NOT NULL))`
- `Schema.kt:112` — `CHECK (rounds IS NOT NULL OR time_cap_sec IS NOT NULL)`

`canSave` (`BuilderDraft.kt:177`) checks only name and stations, so nothing blocks the attempt. The
user picks 段階, fills in a station, presses 保存, and gets `保存できませんでした` with no explanation
and no way to succeed. No test covers it: `SeedCatalogTest` asserts the invariant over *seeded*
routines only.

This is not a Phase 4 gap — it is a Phase 2 bug — but it is squarely inside the double-progression
blast radius, because giving a custom routine a progression is precisely what would make 段階
authorable. **It is reported, not fixed**: this unit owns a test file and this document, and a fix
touching the builder, the store or the schema belongs to whoever owns those files, with a decision
attached (does 段階 leave the builder, or does a custom routine gain a program?).

### 1.4 What a person must decide before this can be built

1. **What the two progressed variables are**, given there is no load column. Reps × variation-rung?
   Reps × seconds? Reps × sets? Whatever the answer, it determines a schema change — `progression_state`
   has nowhere to put the current rep target.
2. **The rep range**: bottom and top, as literal numbers. There is no default in this project to fall
   back on, and inventing one would be the RECONDO error (design §9, `00-plan.md` §2 row 2,
   `DECISIONS.md` §Q3).
3. **Which stations it applies to.** `prescription_kind` is one of `REPS`, `DURATION`, `MAX_EFFORT`
   (`Schema.kt:165`). A rep range is meaningless on `MAX_EFFORT` — the prescription *is* "as many as
   you can" — and ambiguous on `DURATION`. Does a double-progressing routine refuse non-`REPS`
   stations, or does `DURATION` get a seconds range with its own bounds to source?
4. **What happens at the top of the range.** The whole feature is this one rule and it is unwritten:
   does the routine advance to the next variation rung and reset to the bottom, add a set, add a round,
   or stop and wait for the user? If it advances a rung, which ladder — and what happens on a station
   whose exercise has no ladder above it (片手腕立て is the top; スクワット has no ladder at all)?
5. **Whether every set must be made, or the last set only**, i.e. what `ALL_SETS_MADE` actually asserts.
   *(Corrected: an earlier draft of this file said the finish transaction advances progression for
   partial sessions too. That is false — `GymStore.finishSession` has always read
   `if (complete) advanceProgression(…)`, and `git show HEAD` confirms it predates Phase 4. A partial
   session cannot advance anything, which is `00-plan.md` §4.1 rule 2 and the same line `prEligible`
   draws for records. The open question is only the first half of this item.)*
6. **Whether it applies to built-ins as well as custom routines.** The plan says "custom routines", but
   リーコン・ロン is `WEEKS_ELAPSED` and アームストロング is `SESSIONS_COMPLETED`; if this is only for
   user routines, no shipped routine ever exercises the code path.
7. **Every string.** See §3.

---

## 2. Deload prompts — **NOT SPECIFIED, AND NO COPY EXISTS**

### 2.1 The exhaustive grep

`deload` appears **three times** in the entire `.planning/` directory:

| file:line | text | what it is |
|---|---|---|
| `exercise-design.md:394` | "A streak that punishes a deload is actively harmful — deloading is training." | §5.2, an argument for streak *forgiveness*. Not a prompt. |
| `exercise-design.md:815` | "deload prompts" | an item in the §13 phase roster |
| `00-plan.md:294` | "deload prompts" | the same item in the §6 build order |

The first is the only sentence in the project that says anything *about* deloading, and what it says is
that the streak must not punish one — which is already implemented, as forgiveness.

### 2.2 There is no deload copy anywhere. Plainly.

`04-library-records.md` §6 (`:1100–1195`) is the string table for the whole library and records
surface, and `exercise-design.md` §12 (`:733–784`) is the design-side table. **Neither contains a
deload string.** Searches over the entire directory for the Japanese a deload prompt would plausibly
use — 軽い, 休む, 休みま, やすむ, 減らす, 落とす, 回復, 軽め, 抜く週 — return **zero hits, all of them.**

`同じ調子が続いています` (`exercise-design.md:781`, rendered at `04-library-records.md:648`) is the
**monotony nudge** and nothing else. It is already implemented, already gated on
`monotony7d > 2.0 && historyDays >= 14` (`RecordCopy.kt:312–361`), and it says "the same tempo keeps
going" — vary the hard/easy pattern. That is not "take an easier week", and reusing it for a deload
would make one sentence carry two different pieces of advice with two different triggers, so a user
could not tell which one fired. **There is no second string, and one must not be borrowed.**

### 2.3 What a person must decide before this can be built

1. **What triggers a prompt.** Four candidate signals exist and none is nominated: ACWR above some
   threshold, monotony, consecutive-week volume ramp, or weeks since the last light week. A threshold
   number is required for whichever is chosen, and §7.4 has already disqualified ACWR's own published
   bands (0.8–1.3) as unreplicated — so an ACWR-triggered prompt needs a *new* defensible cutoff, not
   the literature's.
2. **What it says** — a Japanese sentence, in `04-library-records.md` §6's table, in the register the
   rest of the feature uses. This is the hard part and it is not a translation exercise: the monotony
   nudge is one twelve-character `c.inkFaint` line that states a fact and implies an action without
   instructing. A deload prompt has to do the same thing.
3. **Where it appears.** `04-library-records.md:958` already forecloses one option — "If a ramp warning
   is ever surfaced it is one `c.inkFaint` line of copy, not a chart." Remaining candidates:
   `GYM.RECORDS.INDEX` beneath the streak block (where the monotony nudge lives), `GYM.HOME`, or
   `GYM.SESSION.COMPLETE`. Each is a different claim about when the user should hear it.
4. **What the user can do about it.** Is it a line of copy with no affordance (like the monotony
   nudge and the forgiveness line), or does it have an action — and if so, what does the action *do*?
   There is no "light week" concept in the data model to switch into. `00-plan.md` §4.1's rationed-copy
   restraint and `DECISIONS.md` §Q17's ruling both argue against chrome, so "one line, no action" is
   the likeliest right answer — but it is a decision, not a default.
5. **Its dismissal and repeat behaviour.** Does it persist while the condition holds, appear once per
   week, or dismiss permanently? Nothing in the feature has a dismissible nudge yet, so this would be a
   new interaction pattern and a new DataStore key.
6. **Whether it may be shown at all below 28 days of history**, if ACWR is the trigger. §7.4 suppresses
   the ratio entirely below that, so an ACWR-triggered prompt inherits the suppression.

---

## 3. Strings and numbers that could not be sourced

Nothing in this unit shipped an unsourced string or number, because nothing in this unit shipped a
string or number at all. The complete list of what **would** have had to be invented, and was not:

| needed for | what | status |
|---|---|---|
| double progression | the rep range's bottom and top | no source |
| double progression | the label for a rep range on `GYM.LIBRARY.DETAIL` / the builder | no source |
| double progression | the wheel range for authoring a rep range | no source — and `DECISIONS.md` §Q19 already ruled that an undocumented wheel range is not to be invented |
| double progression | what advances at the top of the range | no source |
| double progression | copy for "you advanced" / "you moved up a rung" | no source |
| deload prompt | the trigger metric | no source |
| deload prompt | the trigger threshold | no source |
| deload prompt | **the sentence itself** | no source — the string table has no deload entry, and 同じ調子が続いています is the monotony nudge |
| deload prompt | its dismissal semantics | no source |

---

## 4. The ACWR restraint — audited, and now guarded

### 4.1 The rule

`exercise-design.md:546–549` (§7.4) and `04-library-records.md:958–960` (§5 edge case 6) say the same
thing twice: **ACWR is computed and never drawn.** No injury-risk percentage, no ratio gauge, no fourth
chart. It is a governor, suppressed entirely below 28 days of history, and at most a soft nudge.

### 4.2 The audit — the restraint holds

Every page and every chart was checked. `acwr` appears in exactly four main-source files, and three of
them once comments are stripped:

| file | what it does |
|---|---|
| `gym/data/GymMath.kt:211` | `fun acwr(spine)` — computes it; returns null below `ACWR_CHRONIC_DAYS` |
| `gym/GymModels.kt:935` | `TrainingLoad.acwr` — carries it, with the restraint in its KDoc |
| `gym/data/GymStore.kt:764` | populates it, gated on `historyDays >= ACWR_CHRONIC_DAYS` |
| `gym/GymRepository.kt:243` | a KDoc sentence only — no code |

**No file under `ui/` references it at all.** The two pages that read `trainingLoad()` —
`RecordsIndexScreen.kt:376` and `RecordsChartsScreen.kt:316` — take `monotony7d` and `historyDays` by
name and nothing else. `ChartGeometry.kt` has no ratio geometry; `RecordCopy.kt` has no ratio copy; the
three charts on `GYM.RECORDS.CHARTS` are 週ごとの回数, 活動時間 and 積み上げ, exactly as `04` §5 specifies.
No violation found.

### 4.3 The guard

`app/src/test/java/io/eddiegulay/tempo/gym/AcwrRestraintTest.kt` — five tests, modelled on
`GymShellTest`'s orphan check: derive the claim from the source tree rather than from a list a person
maintains.

| test | what it fails on |
|---|---|
| `the ratio is still computed` | the anti-vacuity half. Delete `acwr` from `GymMath` and every other test here passes while the governor evaporates. |
| `no page in 鍛錬 reads the ratio` | any file under `ui/` naming `acwr` in any casing. Comments are stripped first, so a page may still *document* why it declines to draw it. |
| `no page reaches the ratio positionally` | `component3()`, or any positional destructuring in a `ui/` file that touches training load — the one route to the ratio that never types its name. |
| `the ratio is confined to the files that compute it` | the set of main sources naming ACWR changing. A Phase 4 governor may deliberately widen it; `RecordCopy.kt` or `ChartGeometry.kt` appearing in it cannot be an accident. |
| `the twenty-eight day suppression is wired at the store` | the `historyDays >= ACWR_CHRONIC_DAYS` gate going missing. The pure function's own guard does **not** cover this: the spine is always 28 days wide by construction (zero-filled), so `GymMath.acwr` alone never suppresses for a four-day user with 24 zeros. This is `DECISIONS.md` §Q24's distinction pointed the other way. |

All five were mutation-checked: adding `acwrGauge` to `RecordsChartsScreen`, destructuring a
`TrainingLoad` in `RecordsIndexScreen`, adding `acwrLine` to `RecordCopy`, and removing the store's
`historyDays` gate each turn the suite red.

---

## 5. Summary for the next agent

- **Build:** nothing more of Phase 4 than what is already sourced. The ramp governor and
  `ALL_SETS_MADE` auto-advance belong to another unit.
- **Do not build:** double progression, deload prompts. Both need product decisions listed above, and
  the deload prompt needs a Japanese sentence that does not exist yet.
- **Fix, with a decision attached:** §1.3's 段階-in-the-builder defect. Either the engine leaves
  `ENGINE_CHOICES` or a custom routine gains a progression program — and the second of those *is*
  double progression, which is why the two are entangled.
- **Never:** draw ACWR. `AcwrRestraintTest` now says so in a form that outlives every conversation
  about it.
