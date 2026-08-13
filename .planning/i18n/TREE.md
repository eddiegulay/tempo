# Tempo — the localisation tree

The single reference for the Japanese → multi-language migration. Built from eight parallel surveys;
the per-scope detail (every literal, every row, every `file:line`) lives in `inventory/`, and this
document is the map over them.

**Surveyed:** 137 files · **787 user-visible strings** · 45 non-visible Japanese literals ·
23 English names already in the database · **410 tests** asserting a Japanese literal.

Read this document top to bottom once. After that, §2 is the working tree and §4 is the list of things
that will hurt.

---

## 1. What this migration actually is

Not "add a locale." There is no i18n infrastructure to extend: `res/values/strings.xml` contains one
entry, `app_name`, and it is referenced twice from the manifest and never from Kotlin. Every other
string in the app is a Kotlin literal. The mechanism and the content have to be built together.

Three things make it harder than a string-table swap, and they are the reason this document exists:

1. **Copy has leaked out of the UI layer.** 138 user-visible strings live in `gym/` domain files and
   21 more in `data/` — models, formatters, chart geometry, filter enums. Roughly a fifth of the copy
   is not in a screen.
2. **A third of it is composed, not chosen.** `PlayerCopy.kt` produces 47 strings from 57 raw
   fragments; only 3 of its 47 outputs are a whole literal. Word order, counter suffixes and numeral
   orthography are baked into `+` concatenation across ~215 call sites.
3. **Some of it is not copy at all.** Japanese strings are used as database values, SQL `CHECK`
   constraints, `when` branch keys, and search-fold tables. Translating one of those is a data-loss
   bug, not a cosmetic one. §3 is the ledger that keeps them apart.

---

## 2. The tree

Counts are user-visible strings. `→` marks a page whose copy is owned by another file.

```
Tempo
│
├── First launch
│   └── OnboardingScreen ........................... 11    onboarding.*
│       └── ⚠ CONSENT: both permission rationales (:120,:132) are Japanese prose
│
├── Home  ......................................... 5      home.*
│   ├── clock + reading ........................... →  JapaneseDate.reading
│   ├── corner cluster (era/月日/weekday/time) ..... →  JapaneseDate       ⚠ H-1 unbounded stacking
│   ├── next event title .......................... →  Tategaki           ⚠ H-2 no Latin mode
│   └── 静 seal ................................... 1      decide: logo, not copy
│
├── Dock  ......................................... 5      app.dock.*
│   └── 4 icon-only buttons + long-press ........... all 5 are contentDescription only
│
├── Search (app drawer) ........................... 14     search.*
│   ├── theme toggle .............................. ← the app's only global setting lives here
│   └── ⚠ bare contains(ignoreCase) + lowercase() sort — no locale, no folding
│
├── Filter ........................................ 7      filter.*
│   └── ⚠ :62 hard-codes "10" where dialogs interpolate BLOCK_DAYS
│
├── Focus (pomodoro) .............................. 9      focus.*
│   └── ⚠ phase label 26sp @ letterSpacing 8sp (31% tracking)
│
├── Calendar ...................................... 14     calendar.*
│   ├── EventCompose .............................. 20     calendar.compose.*
│   │   └── ⚠ （無題） is written to the device calendar provider
│   └── CalendarFeedback .......................... 28     fault.calendar.* + fault.gym.*
│
├── Notifications ................................. 15     notifications.*
│
├── Dialogs
│   ├── ModeDialog ................................ 6      dialog.mode.*
│   ├── BlockConfirmDialog ........................ 4      dialog.block.*
│   └── BlockedInfoDialog ......................... 5      dialog.blocked.*
│
├── AppGlyph ...................................... 1      (+19 JP match keys — NOT copy)
│
└── 鍛錬 Gym
    │
    ├── GymHome ................................... 21     gym.home.*
    │   └── GymHomeCopy ........................... 31     ⚠ 17 fns, 0 constants — a rewrite
    │       └── stalenessLabel .................... 4-branch formatter, tail delegates
    │
    ├── Library
    │   ├── LibraryIndex .......................... 27     gym.library.*
    │   ├── LibraryDetail ......................... 38     gym.library.detail.*
    │   └── ⚠ routine names are user data — never translated
    │
    ├── Exercises
    │   ├── ExerciseIndex ......................... 9      gym.exercise.*
    │   ├── ExerciseDetail ........................ 18     gym.exercise.detail.*
    │   └── names ................................. →  catalog.*  (nameEn already exists)
    │
    ├── Builder ................................... 43     gym.builder.*
    │   ├── StationPicker ......................... 14     gym.builder.station.*
    │   └── ⚠ :966 dispatches wheel rows by when(row.label) on Japanese literals
    │
    ├── Settings .................................. 6      gym.settings.*
    │   ├── GymSettingsCopy ....................... 27     ✓ ~90% mechanical — the template
    │   ├── GymSafety ............................. 2
    │   └── ⚠ :775 probes TTS with a hard-coded Locale.JAPANESE
    │
    ├── Session player ............................ 160 total, gym.session.*
    │   ├── PlayerCopy ............................ 47 keys from 57 fragments  ⚠ a rewrite
    │   ├── Prepare / Work / Reps / Rest / Paused .. hero numerals, fixed slots
    │   │   └── ⚠ repHero cap = maxWidth/4.2 — "four glyphs", clipped not ellipsised
    │   ├── Complete .............................. ceremony copy
    │   ├── QuitSheet ............................. ⚠ つづける/やめる must NOT merge
    │   └── SessionUnrecoverable .................. fault.session.*
    │
    ├── Records
    │   ├── RecordsIndex .......................... gym.records.*   ⚠ 7 weekday letters in 20.dp cells
    │   ├── RecordsHistory ........................ gym.records.history.*
    │   ├── RecordsCharts ......................... gym.records.charts.*  → ChartGeometry
    │   ├── RecordsPr ............................. gym.records.pr.*      ⚠ 44.dp fixed underline
    │   ├── RecordSummary ......................... gym.records.summary.* ⚠ sizes text by .length
    │   └── SessionDetail ......................... gym.records.session.*
    │
    ├── Domain copy (not a screen) ................ 138
    │   ├── GymModels ............................. 23     ⚠ Tier.label IS the DB value
    │   ├── RecordCopy ............................ 24     gym.records.copy.*
    │   ├── ChartGeometry ......................... 21     gym.records.charts.axis.*
    │   ├── EngineRows ............................ 19     gym.engine.*
    │   ├── LibraryFilters ........................ 4      (+10 fold tables — NOT copy)
    │   └── 17 further files ...................... 47
    │
    ├── Cues (spoken) ............................. cue.*
    │   └── ⚠ Locale.JAPANESE + enum case named NoJapaneseVoice
    │
    ├── Notification (system shade) ............... 10     gym.notice.*
    │   └── ⚠ rendered outside the app theme; channel name 鍛錬 shows in system settings
    │
    └── Seeded catalogue (SQLite) ................. 71     catalog.*
        ├── exercise names ........................ 23 ja + 23 en ✓ already bilingual
        ├── exercise cues ......................... 17 ja only
        ├── routine names ......................... 9 ja only   ⚠ content-addressed
        ├── programme names ....................... 3 ja only
        ├── step labels ........................... 5 ja only
        └── station notes ......................... 4 ja only
```

### Formatters — the layer under the tree

Not keys. These produce strings from numbers and dates, and ~215 call sites depend on them.

```
fmt.*
├── JapaneseDate  (data/) ......... 11 public fns, 17 literals, ~85 kanjiExtended callers
│   ├── kanji / kanjiExtended ..... kanji numerals 0–9999
│   ├── era ....................... 令和 + offset — no English equivalent
│   ├── monthDay / dayOfWeek ...... single-char weekdays
│   ├── reading ................... spoken-style clock, no idiomatic English form
│   ├── dayToken / dayHeading ..... 今日/明日 (forward-looking)
│   └── time / clock .............. ✓ %02d:%02d — port unchanged
├── Numerals  (gym/) .............. 4 fns
│   ├── durationKanji ............. ⚠ drops seconds on whole minutes; HAS NO HOURS (6000s → 百分)
│   ├── coefficientLabel .......... 一.〇
│   └── clockDuration ............. ✓ the only formatter that ports unchanged
├── relativeDayJa  (GymHomeCopy) .. きょう/きのう (backward-looking)
└── counter suffixes .............. 回 巡 種目 秒 分 日 月 件 番目 第  — all via `+`
```

**The two numeral rules that have no English successor.** §Q4: a *ticking* value is arabic, a
*stopped* value is kanji — on PAUSED the same instant is drawn `0:23` and spoken `残り 二十三秒`, on
purpose. §Q10: a duration the user *chose* renders bare (`六十秒`), one the app *measured* goes through
`durationKanji` (`一分三十秒`). Both distinctions are about the act, not the number. English has no
orthographic axis to carry them, so both collapse — and collapsing them deletes a documented behaviour
rather than translating it. **This needs a decision before any of the ~215 numeral sites can move.**

Likewise `今日/明日` (forward, kanji) vs `きょう/きのう` (backward, hiragana) are documented as two
deliberate vocabularies. In English both are "Today". One of the two behaviours is being deleted.

---

## 3. The ledger — stored vs drawn

The single most important table here. A string in the left column is **code**. Translating it corrupts
data or breaks dispatch.

| Japanese string | where | what it really is | if translated |
|---|---|---|---|
| `入門` `中級` `上級` | `GymModels.kt:124-135` + `Schema.kt:141` | `Tier.storageValue = label`, enforced by a live `CHECK` | **every routine's tier orphaned; next write violates the constraint** |
| `engineRows` labels | `BuilderScreen.kt:966` | `when (row.label)` branch keys | three settings silently become read-only — no compile error |
| kana fold tables | `LibraryFilters.kt:23-39` | Unicode conversion data | Japanese search breaks — and routine names stay Japanese forever |
| app-name keywords | `AppGlyph.kt:277-297` | matched against *other apps'* labels | icon resolution breaks for Japanese-named apps |
| `〇一二三…` `日月火水木金土` | `JapaneseDate.kt:15,18` | numeral + weekday tables | formatter data, not copy |
| `—` (em dash) | `Numerals.kt:22` | `NO_VALUE` placeholder | nothing; do not touch it |
| `（無題）` | `CalendarRepository.kt:185` | **display default that is persisted** | see below — this is a live bug |

Safe: every other enum (`Engine`, `Pattern`, `Measure`, `BestMetric`, `Phase`, `Rating`, `Units`,
`ScalingTier`) round-trips through `.name`. `session.tier` is a *different* column holding ASCII enum
names — do not conflate it with `routine.tier`.

**Three places our copy leaves the app**, where a language switch cannot reach it:

- `（無題）` → written to `Events.TITLE` (`CalendarRepository.kt:334`), syncs to Google and to guests.
  Editing any untitled event does this. **This is a bug today, before any i18n work.**
- Routine names → written to the device calendar by `ScheduleNextAction`.
- `session.routine_name` and pinned `routine_version` snapshots → **history cannot be retranslated.**
  Past sessions keep their Japanese names forever after a switch. That follows from the existing
  (correct) decision to freeze history against routine edits, but it is user-visible and needs signing
  off, not discovering later.

Also persisted: station `note`s feed `structuralHash`, so translating one dirties every routine; and
built-in routine names are content-addressed, so **seed text that varied by locale would churn a
`routine_version` on every language toggle.** Localisation must be resolved at display time, never
baked into the seed.

---

## 4. Hazard register

22 hazards ranked by the cross-cutting sweep. Full detail with quoted code in `inventory/hazards.md`.

### S1 — visibly breaks

| # | what | where |
|---|---|---|
| H-1 | `VerticalLine` stacks every char, **no height bound**, 5 call sites on Home's corner. "Wednesday" = 9 cells into the 104sp clock. `Tategaki.kt:29-31` names this failure ("a ransom note") and rejects it — this helper does it anyway | `HomeScreen.kt:298-313` |
| H-2 | Tategaki is correct Japanese typesetting with no Latin equivalent; an English title becomes one quarter-turned run read head-tilted. `maxCells = 8` is a glyph budget — 8 chars is `Design r…` | `Tategaki.kt` |
| H-3 | TTS hard-wired to `Locale.JAPANESE` in **two independent copies**; `SpeechAvailability.NoJapaneseVoice` threaded through 8 files, and its subtitle names the wrong language under an English UI | `GymSpeech.kt:107`, `GymSettingsScreen.kt:775` |
| H-4 | ~215 numeral/counter sites implementing a rule with no English successor; pluralisation needed everywhere | `Numerals.kt`, 24 files |
| H-5 | Era, weekday, month-day, relative words all composed from kanji tables; two disagreeing relative-day vocabularies | `JapaneseDate.kt`, `GymHomeCopy.kt:220` |
| H-6 | Wheel day column already ellipsises Japanese; `Wednesday 31 December` becomes `Wednesday 3…` while being dialled | `TempoWheel.kt:210-217` |

### S2 — renders, but wrong

| # | what | measured |
|---|---|---|
| H-7 | `HeroTime` sizes by `text.length` because "a CJK glyph advances almost exactly one em" — **verified true** (分, あ = 1000/1000) and **false for Latin** (A=762, i=317, 0=520). Hero time renders ~40% of intended size in English | `RecordSummary.kt:661-675` |
| H-8 | Player hero cap divides by 4.2 = "four glyphs plus slack" | `LivePlayer.kt:167-171` |
| H-9 | `letterSpacing` ≥4sp at **55 sites**; worst 31–43% tracking on labels that become English words | 30 files |
| H-10 | Fonts: ASCII 95/95 and full Latin-1 ✓ (EN/FR/DE/ES/IT/PT/Nordic covered) · `ş ğ ł ż ą ę ć ń ź ř č š ž` **missing from all four** · **zero Cyrillic** · line box 1.448em (CJK-tall) | 4 TTFs |
| H-11 | App-drawer search is bare `contains(ignoreCase)` — default-locale folding, i.e. the Turkish dotless-i bug the moment a toggle exists. The good kana-aware matcher exists but only gym uses it | `SearchScreen.kt:88-92` |
| H-12 | ✓ **`Exercise.nameEn` exists, is seeded, is queried — and only ever used for search matching.** Never rendered | `GymStore.kt:957` |
| H-13 | No `Collator` anywhere; drawer sorts by `lowercase()` | `AppRepository.kt:118` |
| H-14 | Fixed cells sized for one glyph: 7 weekday letters in 20.dp, 44.dp PR underline, 50.dp seal | 3 sites |
| H-15 | 10 × `maxLines = 1` with **no overflow** → clipped, not ellipsised, on strings that grow | incl. `WorkPage.kt:124` |

### S3 — untidy

H-16 the only `DateTimeFormatter` in the app embeds `"M月d日"` · H-17 Japanese in identifiers
(`NoJapaneseVoice`, `relativeDayJa`, `nameJa`/`nameEn`, `JapaneseDate` itself, imported in 24 files) ·
H-18 `DurationBucket` labels are kanji inside enum constants with a wave dash used leading, medial and
trailing · H-19 `uniqueName` composes ` の写し` + kanji counter **and persists it** · H-20 monogram
takes `firstOrNull()` (half a surrogate pair on emoji names) · H-21 Sunday-first weekday indexing
duplicated across two files, one citing the other by line number · H-22 `clockDuration` ports
unchanged — the only one.

**Confirmed absent** (checked, clean): no `.length`-driven truncation or branching · no
`substring`/`take`/`pad` on copy · no `Collator` · no plurals machinery · no RTL work · `MinuteClock`
has no text at all · `FlipClock` is per-char but both callers feed it only digits.

---

## 5. Test surface

1,333 tests across 83 files. Measured with a string-aware comment stripper and paren-matched assertion
spans — **410 tests assert a Japanese literal** (932 asserted literals, 600 distinct). Not 1,333.

| class | size | disposition |
|---|---|---|
| **A** — asserts copy directly | ~28 files, ~590 literals, ~300 tests | migrate to keys |
| **B** — behaviour that happens to be phrased in Japanese | — | survives untouched if it goes through the table |
| **C** — Japanese *is* the subject matter | `KanaFoldingTest` (82), `JapaneseDateTest` (42), `NumeralsTest`, `TategakiTest` | a key table is meaningless here; needs its own plan |
| **D** — structural, reads Kotlin source | 23 files | most likely to be silently invalidated |

**Silent-invalidation risk:** 8 negative assertions (`!charts.contains("目安")`, …) go **vacuously
true** the moment copy leaves the file, and then pass forever enforcing nothing. Three tests assert a
literal appears exactly once in a source file — those fail loudly and should be deleted rather than
ported, since a key table enforces their intent structurally.

**No test in the suite asserts a text width, `maxLines`, or `TextOverflow`.** The suite is blind to
exactly the failure mode §4 says is most likely.

The model for a "no Japanese literal outside the table" gate is **`AcwrRestraintTest`, not
`GymShellTest`** — it already walks the whole main source set, strips comments first, names offenders
in the failure message, carries a deliberate allowlist, and includes an anti-vacuity test so deleting
the feature cannot turn the gate green.

---

## 6. Where the toggle goes

**There is no app-wide settings page.** The only global setting is the theme, and it lives in the
Search screen header (`SearchScreen.kt:117-130`), commented "relocated from the dock". `ModeDialog` is
a mode gate whose own KDoc refuses to become a browsable menu.

**The path language must follow**, traced end to end:

```
DataStore stringPreferencesKey("theme")            tempo_settings.preferences_pb
  → ThemeRepository.theme: Flow + loadInitialSettings()   runBlocking { data.first() }
  → LauncherViewModel.theme: StateFlow                    stateIn(…, Eagerly, initialSettings.theme)
  → MainActivity.kt:54                                    paints the window BEFORE setContent
  → TempoApp.kt:73                                        collectAsStateWithLifecycle()
  → TempoApp.kt:144                                       CompositionLocalProvider(LocalTempoColors)
```

The mechanism is that `LocalTempoColors` is **`staticCompositionLocalOf`** (`TempoTheme.kt:77`) — it
does not track reads, so a change recomposes the provider's entire content lambda. `GymShell` composes
inside that provider, so 鍛錬 inherits it for free.

Two constraints: `preferencesDataStore` allows **one owner per file per process**, and
`tempo_settings.preferences_pb` is named in both backup include-lists — so the language key goes
through `ThemeRepository` itself, not a new repository. And the manifest's `configChanges` does **not**
list `locale`, so per-app locales would force an activity recreate on every switch; a CompositionLocal
swap is a recomposition instead.

**Recommended:** a third `HeaderIconButton` in the Search header opening a `LanguageDialog` shaped like
`ModeDialog` — a *picker*, not a toggle, since the rows must be readable before the choice is made —
plus a bare language row above 「ようこそ」 on onboarding, because the consent gate is Japanese prose
and shows only once. Zero structural cost: no new `Screen`, no `BackHandler` arm, no dock button.

**Rejected:** a new `Screen.Settings`. Architecturally where this ends up, but it costs an enum value,
a back arm, an entry point (the dock's four buttons are deliberate), and a page of *new untranslated
copy*. Build it at three-plus app-wide settings, and move the theme toggle onto it then.

---

## 7. Order of work

1. **Decouple `Tier` from storage.** Nothing else can move safely first.
2. **Build the store + toggle**, and wire the `AcwrRestraintTest`-shaped gate before any copy moves,
   so the migration is enforced rather than remembered.
3. **`catalog.*` via `nameEn`** — the free win; a read-site change, not a data migration.
4. **`GymSettingsCopy`-shaped files** — mechanical, and they prove the table's shape.
5. **The `fmt.*` layer** — the bulk, and blocked on the §Q4/§Q10 decision.
6. **`GymHomeCopy` / `PlayerCopy`** — rewrites, not swaps. Last, when the table is proven.
7. **Layout and type** (H-1, H-7, H-9, H-10) — independent of copy; can run in parallel throughout.

Open decisions that block work, listed so they are not discovered late: the §Q4/§Q10 numeral collapse
(blocks 5) · Home's vertical corner in Latin (blocks H-1/H-2) · whether history retranslates (it
cannot — needs sign-off) · which languages ship, given the font coverage in H-10.
