# Test surface, and where the language toggle goes

**Files surveyed:** 84 test sources (83 under `app/src/test`, 1 under `app/src/androidTest`) + 12 main sources for Job 2
**User-visible literals:** 0 — nothing in this scope reaches a user
**Non-visible JP literals:** 1,086 occurrences in test code (600 distinct strings), plus 546 comment-only lines

This fragment is a lens over two questions, not an inventory of copy. The copy itself belongs to the
page fragments; what is recorded here is *which tests hold that copy hostage*, and *where a switch to
change it can live*.

---

# Job 1 — the test surface

## 1. The count

Counted mechanically over `app/src/test/**/*.kt` (script: strip comments with a string-aware scanner,
then locate every `assert*`/`require`/`check` call span by paren-matching, then bucket every Japanese
string literal by whether it falls inside one).

| measure | count |
|---|---|
| `.kt` files under `app/src/test` | 83 |
| `@Test` functions | **1,333** |
| files containing any Japanese | 77 of 83 |
| **Japanese string literals inside an assertion** | **932** |
| Japanese string literals outside one (fixtures, consts, `private val`) | 154 |
| distinct Japanese string literals | 600 |
| **`@Test` functions that assert a Japanese literal** | **410** |
| `@Test` functions carrying Japanese only as fixture data | 30 |
| `@Test` functions with both | 47 |
| `@Test` functions with a Japanese literal anywhere in code | **440** (33 %) |
| `@Test` functions mentioning Japanese incl. comments and backticked test names | 709 |

`app/src/androidTest` is one file, `ExampleInstrumentedTest.kt`, 1 test, no Japanese. There is no
Compose UI test suite — every assertion in this repo is a JVM assertion over a pure function.

**The headline: 410 tests will fail the moment a copy function stops returning Japanese.** Not 1,300.

Per-file, `assert` / `fixture` literal counts:

| in assert | fixture | file (under `app/src/test/java/io/eddiegulay/tempo/`) |
|---|---|---|
| 81 | 1 | `gym/KanaFoldingTest.kt` |
| 66 | 13 | `ui/gym/LibraryDetailCopyTest.kt` |
| 62 | 4 | `gym/EngineRowsTest.kt` |
| 62 | 0 | `gym/session/ui/PlayerCopyTest.kt` |
| 48 | 15 | `ui/gym/ExerciseCatalogueCopyTest.kt` |
| 47 | 6 | `ui/gym/GymHomeCopyTest.kt` |
| 46 | 9 | `gym/RecordCopyTest.kt` |
| 46 | 3 | `ui/gym/BuilderScreenTest.kt` |
| 42 | 0 | `data/JapaneseDateTest.kt` |
| 38 | 1 | `ui/gym/GymSettingsCopyTest.kt` |
| 33 | 25 | `gym/LibraryFiltersTest.kt` |
| 29 | 2 | `ui/gym/RecordsIndexScreenTest.kt` |
| 27 | 4 | `gym/ui/RecordSummaryTest.kt` |
| 24 | 4 | `gym/BuilderDraftTest.kt` |
| 23 | 10 | `ui/gym/RecordsPrCopyTest.kt` |
| 22 | 0 | `gym/ui/RecordsHistoryScreenTest.kt` |
| 22 | 1 | `ui/CalendarFeedbackTest.kt` |
| 21 | 3 | `ui/gym/LibraryIndexScreenTest.kt` |
| 19 | 0 | `gym/NumeralsTest.kt` |
| 19 | 2 | `gym/TrainingNoticeTest.kt` |
| 16 | 0 | `gym/ChartGeometryTest.kt` |
| 16 | 0 | `ui/gym/RecordsIndexScreenStructureTest.kt` |
| 15 | 6 | `ui/gym/StationPickerScreenTest.kt` |
| 11 | 0 | `gym/GymContractTest.kt` |
| 11 | 7 | `ui/TategakiTest.kt` |
| 9 | 0 | `gym/InkDensityTest.kt` |
| 8 | 0 | `ui/gym/ExerciseScreenStructureTest.kt` |
| 7 | 3 | `gym/ui/SessionDetailScreenTest.kt` |
| 6 | 19 | `gym/GymPageStateTest.kt` |
| 6 | 1 | `gym/HistoryPagingTest.kt` |
| 6 | 1 | `gym/RoutineEstimateTest.kt` |
| 6 | 0 | `ui/gym/ScheduleNextAccessTest.kt` |
| 5 | 0 | `ui/gym/BuilderScreenStructureTest.kt` |
| 5 | 0 | `ui/gym/RecordsPrAndChartsStructureTest.kt` |
| 3 | 1 | `gym/PatternWarningTest.kt` |
| 3 | 1 | `gym/ScheduleNextTest.kt` |
| 3 | 3 | `gym/cue/CueScheduleTest.kt` |
| 3 | 0 | `gym/session/ui/DiscardArmTest.kt` |
| 3 | 0 | `ui/gym/LibraryIndexScreenStructureTest.kt` |
| 2 | 0 | `gym/data/SeedCatalogTest.kt` |
| 2 | 0 | `gym/session/ui/PlayerWiringTest.kt` |
| 2 | 0 | `ui/gym/GymSettingsScreenStructureTest.kt` |
| 2 | 0 | `ui/gym/GymShellTest.kt` |
| 2 | 0 | `ui/gym/RecordsChartsCopyTest.kt` |
| 1 | 0 | `gym/cue/CueDisarmTest.kt` |
| 1 | 1 | `gym/data/ExerciseCatalogTest.kt` |
| 1 | 0 | `gym/data/ProgressionAdvanceTest.kt` |
| 0 | 2 | `gym/RoutineTierTest.kt` |
| 0 | 3 | `gym/data/GymMathTest.kt` |
| 0 | 1 | `gym/session/TimelineFixtures.kt` |
| 0 | 1 | `gym/session/ui/SessionProjectionTest.kt` |
| 0 | 1 | `ui/gym/KeepAwakeTest.kt` |

## 2. The classification — this is the deliverable

Four classes, not two. The prompt's "copy vs behaviour" split is real but it hides a third and fourth
population that need completely different treatment.

### Class A — copy assertions. Migrate to keys. (~28 files, ~590 literals, ~300 tests)

The assertion *is* the string: `assertEquals("種目の名前を読み上げる", row.subtitle)`
(`ui/gym/GymSettingsCopyTest.kt:47`). Every one of these is a claim about wording. After migration
they must assert a **key**, and a separate small table-integrity suite must assert that the key
resolves to that Japanese string in the `ja` table. Splitting it that way is what turns 590 brittle
literals into ~590 one-line table rows plus ~300 stable key assertions.

The functions under assertion, from the `assertEquals("<ja>", X)` sites, are all pure copy builders —
this is the whole reason the migration is tractable:

| file | asserted producers |
|---|---|
| `gym/session/ui/PlayerCopyTest.kt` | `restLabel`, `countdownAnnouncement`, `prescriptionLabel`, `chosenSecondsLabel`, `counterLabel`, `progressLabel`, `nextUpLabel`, `nextUpDescription`, `prepareAnnouncement`, `restAnnouncement`, `repHero`, `repDoneDescription`, `quitSummaryLine`, `pacerLabel`, … (33 imported copy functions) |
| `ui/gym/GymHomeCopyTest.kt` | `staleness`×9, `bestLine`, `relativeDayJa`, `resumeBannerDescription`, `stalenessLabel`, `deleteRoutineCopy`, `timesDoneLabel` |
| `ui/gym/LibraryDetailCopyTest.kt` | `prescriptionLabel`, `startButtonDescription`, `stationRowSemantics`, `detailSubtitle`, `detailFavouriteLabel`, `detailHeaderTitle`, dialog `copy.title/body/confirm/cancel` |
| `gym/RecordCopyTest.kt` | `row`, `historySubtitle`, `comparisonCopy`, `partialChipCopy`, `heroTime`, `PrChip.CURRENT.label` |
| `ui/gym/GymSettingsCopyTest.kt` | `settingsSecondsLabel`, `settingsRowDescription`, `toggleWord`, `prepareOptions`, `SettingSection.*.heading` |
| `ui/gym/ExerciseCatalogueCopyTest.kt` | `exerciseCardCopy`, `exerciseIndexSubtitle`, `exerciseDetailSubtitle`, `rungSemantics`, `usedByCount` |
| `ui/gym/BuilderScreenTest.kt` | `stationValueLabel`, `saveDescription`, `stationSemantics`, `moveAnnouncementWith`, `builderTitle`, `restWheelValueLabel` |
| `gym/ui/RecordSummaryTest.kt` | `recordHeroLabel`, `recordHeaderChip`, `ratingGroupState`, `accoladeSemantics`, `streakLine`, `ratingOptionLabel` |
| `ui/CalendarFeedbackTest.kt` | `faultCopy`×14 — the app-wide error table |
| `gym/ChartGeometryTest.kt` | `chartCaption`, `chartSemantics`, `chartHeading`, `chartSuppressionCopy` |
| `gym/InkDensityTest.kt` | `monthCaption`, `gridSemantics` |
| `gym/TrainingNoticeTest.kt` | `noticeText`, `noticePhaseLabel`, `noticeControlLabel`, `noticeTitle`, `noticeSemantics` |
| `ui/gym/RecordsIndexScreenTest.kt`, `ui/gym/RecordsPrCopyTest.kt`, `ui/gym/StationPickerScreenTest.kt`, `ui/gym/LibraryIndexScreenTest.kt`, `gym/ui/RecordsHistoryScreenTest.kt`, `gym/ui/SessionDetailScreenTest.kt`, `gym/BuilderDraftTest.kt`, `gym/EngineRowsTest.kt`, `gym/RoutineEstimateTest.kt`, `gym/HistoryPagingTest.kt`, `gym/PatternWarningTest.kt`, `gym/cue/CueScheduleTest.kt`, `gym/cue/CueDisarmTest.kt`, `gym/session/ui/DiscardArmTest.kt`, `ui/gym/RecordsChartsCopyTest.kt` | as named in each file |

### Class B — behaviour, phrased in Japanese. Safe *if* it goes through the table.

Two sub-shapes, and both are fine as-is:

- **Which-of-two-labels.** `gym/GymPageStateTest.kt` is the model: `assertEquals(ResumeAffordance.StalePromptNoResume, resumeAffordance(...))` — the state is an enum, the Japanese lives only in the backticked test name (`` `a reboot removes 続ける, and removes it rather than disabling it` ``) and comments. 20 tests, 6 in-assert literals, 19 fixture literals. **Nothing here breaks.** This file is the shape the Class A files should be refactored towards where a key is not enough.
- **Silence assertions.** `assertNull(bestLine(...))`, `assertNull(row.subtitle)` — an omitted line is the page's answer. These are pure behaviour and survive untouched. `ui/gym/GymSettingsCopyTest.kt` has several; `ui/gym/GymHomeCopyTest.kt`'s KDoc calls out three explicitly.

Also safe: 19 assertion-**message** strings (`assertTrue("削除済み must be spoken…", …)`) — Japanese in a
failure message costs nothing and should be left alone rather than churned.

### Class C — Japanese is the subject matter, not the copy. Needs its own plan, not a key table.

These are algorithms *over* Japanese text. A key table is meaningless for them; they either stay
Japanese-only and are gated on locale, or they gain a sibling implementation.

| file | tests | JP literals | what it actually is |
|---|---|---|---|
| `gym/KanaFoldingTest.kt` | 18 | 82 | `foldKana` — search normalisation: script equivalence, half/full width, long vowels, voicing marks, decomposed dakuten. 27 `assertEquals("<ja>", foldKana(…))`. Under English search this function is a no-op; the tests are correct forever, but **the folding is a hazard: latin folds case and width, kana folds four ways, and a mixed-language index needs both.** |
| `data/JapaneseDateTest.kt` | 13 | 42 | `JapaneseDate.kanjiExtended`×25, `.kanji`×7, `.reading`×4, `.era`×2, `.monthDay`, `.dayOfWeek`. This is the `fmt.*` root in one file. Every assertion is a formatter output. |
| `gym/NumeralsTest.kt` | 12 | 19 | `durationKanji`×9, `durationKanjiFromMs`×3, `coefficientLabel`×7 — arabic↔kanji numeral rendering. |
| `ui/TategakiTest.kt` | 11 | 18 | `layoutTategaki` — Home's vertical corner. See Hazards. |

### Class D — structural tests that read Kotlin source. See §3.

**23 files.** Of these, 14 assert a Japanese literal *against source text*, which is the class most
likely to be silently invalidated: they will keep passing while asserting nothing, or fail for a
reason that reads as unrelated.

## 3. Structural tests — the full list, and how they read source

Every test file that opens a `.kt` (or `.xml`) from `app/src/main` at runtime:

| file | tests | reads | asserts a JP literal against source? |
|---|---|---|---|
| `ui/gym/GymShellTest.kt` | 7 | `gym/GymRoute.kt`, `ui/gym/GymShell.kt`, **whole `ui/gym` tree** | yes (2) |
| `gym/AcwrRestraintTest.kt` | 5 | **whole `ui/` tree and whole main source set**, comments stripped | no |
| `gym/GymViewModelInitOrderTest.kt` | 2 | `gym/GymViewModel.kt` | no |
| `ui/gym/RecordsIndexScreenStructureTest.kt` | 13 | `ui/gym/RecordsIndexScreen.kt` | **yes (16), incl. a literal count** |
| `ui/gym/RecordsPrAndChartsStructureTest.kt` | 16 | `ui/gym/RecordsPrScreen.kt`, `RecordsChartsScreen.kt` | **yes (5), incl. a literal count** |
| `ui/gym/ExerciseScreenStructureTest.kt` | 14 | `ui/gym/ExerciseIndexScreen.kt`, `ExerciseDetailScreen.kt` | **yes (8), incl. a literal count** |
| `ui/gym/BuilderScreenStructureTest.kt` | 11 | `ui/gym/BuilderScreen.kt`, `StationPickerScreen.kt` | yes (5) |
| `ui/gym/GymSettingsScreenStructureTest.kt` | 8 | `ui/gym/GymSettingsScreen.kt`, `GymSafetyScreen.kt`, `GymHomeScreen.kt`, `gym/GymViewModel.kt` | yes (2) |
| `ui/gym/LibraryIndexScreenStructureTest.kt` | 5 | `ui/gym/LibraryIndexScreen.kt` | **yes (3), asserts KDoc prose** |
| `ui/gym/ScheduleNextAccessTest.kt` | 2 | `ui/gym/ScheduleNextAction.kt`, `ui/CalendarScreen.kt` | **yes (6)** |
| `ui/gym/session/SessionHostTest.kt` | 8 | `ui/gym/session/SessionHost.kt` | no |
| `gym/session/ui/PlayerWiringTest.kt` | 6 | `ui/gym/session/WorkPage.kt`, `SessionHost.kt` | yes (2) |
| `gym/ui/RecordAnnouncerTest.kt` | 5 | `ui/gym/RecordSummary.kt` | no |
| `gym/ui/RecordsHistoryScreenTest.kt` | 37 | `ui/gym/RecordsHistoryScreen.kt` | yes — mixed copy + structural |
| `gym/ui/SessionDetailScreenTest.kt` | 20 | `ui/gym/SessionDetailScreen.kt` | yes — mixed |
| `gym/ScheduleNextTest.kt` | 13 | walks a tree for `.kt` | yes (3) |
| `gym/TrainingNoticeTest.kt` | 15 | `gym/TrainingNotice.kt` | yes — mixed |
| `gym/TrainingManifestTest.kt` | 8 | `src/main/AndroidManifest.xml`, `gym/TrainingService.kt`, `res/drawable/…`, `proguard-rules.pro` | no |
| `gym/data/GymManifestTest.kt` | 2 | `AndroidManifest.xml`, `res/xml/*` | no |
| `gym/data/DeleteResultTest.kt` | 3 | `gym/data/GymStore.kt` | no |
| `gym/data/LifetimeSummaryTest.kt` | 4 | `gym/GymRepository.kt`, `GymViewModel.kt`, `data/GymStore.kt` | no |
| `gym/data/ProgressionAdvanceTest.kt` | 30 | `gym/data/GymStore.kt` | yes (1) |
| `gym/data/StoreGuardsTest.kt` | 9 | `gym/data/DbSupport.kt` | no |

### How they locate and read source — the pattern a new test must follow

There are two resolvers and they agree in behaviour. **Neither depends on the working directory**,
because Gradle sets it to the module dir and IDE runners do not.

Resolver A (17 files, e.g. `ui/gym/GymShellTest.kt:259-269`):

```kotlin
private fun sourceFile(relative: String): File {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
        for (prefix in listOf("app/src/main/java/io/eddiegulay/tempo/", "src/main/java/io/eddiegulay/tempo/")) {
            val candidate = File(dir, prefix + relative)
            if (candidate.exists()) return candidate
        }
        dir = dir.parentFile
    }
    throw IllegalStateException("could not locate $relative from ${File("").absolutePath}")
}
```

Resolver B (3 files — `gym/GymViewModelInitOrderTest.kt:28-32`, `gym/TrainingManifestTest.kt:127`,
`gym/data/GymManifestTest.kt:57`) walks up from `System.getProperty("user.dir")` with
`generateSequence(…) { it.parentFile }.first { … }`, and is the one to copy when the target is
repo-relative rather than package-relative (manifest, proguard, `res/`).

On top of the resolver, four reusable readers:

1. **Whole-tree scan** — `ui/gym/GymShellTest.kt:217-220`:
   `sourceDir("ui/gym").walkTopDown().filter { it.isFile && it.extension == "kt" }.associate { it.name to it.readText() }`
2. **Whole main source set, comment-stripped** — `gym/AcwrRestraintTest.kt:172-192`:
   `sourcesUnder(".")` walks everything under `…/io/eddiegulay/tempo/`, and `stripComments` removes
   block comments first, then line comments (order matters — a `//` inside a `/* */` would otherwise
   leave an unterminated opener).
3. **Brace-matched declaration body** — `declarationBody(source, header)`, duplicated verbatim in 7
   files (`GymShellTest.kt:233-249`, `BuilderScreenStructureTest.kt`,
   `RecordsIndexScreenStructureTest.kt`, `ExerciseScreenStructureTest.kt`,
   `RecordsPrAndChartsStructureTest.kt`, `GymSettingsScreenStructureTest.kt`, `SessionHostTest.kt`).
   Finds the header, finds the first `{`, counts depth, returns the body; throws on imbalance rather
   than truncating.
4. **Declaration enumeration** — `GymShellTest.kt:223-226`:
   `Regex("(?m)^(?:private |internal |)fun ([A-Z]\\w*)\\(")` → name-to-body map, then a BFS over
   `Regex("\\b([A-Z][A-Za-z0-9]*)\\s*\\(")` call sites to compute reachability.

**The model for a "no Japanese literal outside the translation table" test is `AcwrRestraintTest`, not
`GymShellTest`.** It is already exactly the right shape and should be copied wholesale:

- it walks the **entire** main source set, not one feature (`mainSources` = `sourcesUnder(".")`);
- it **strips comments first**, and its KDoc argues at length for why: "The restraint deserves to be
  written down at the page that declines to draw it… a test that fails on the explanation teaches the
  next author to delete the explanation." Tempo's KDoc quotes its own copy constantly
  (`LibraryIndexScreenStructureTest.kt:104-105` asserts on KDoc prose), so a naive scanner would fail
  on every well-documented file;
- it asserts `assertEquals(<message>, emptyList<String>(), offenders)` so the failure *names the
  offending files*;
- it carries a **deliberate allowlist** (`private val computeSites = setOf("GymMath.kt", …)`) with a
  KDoc paragraph on what adding an entry means. The i18n version's allowlist is the translation
  table's own files plus `gym/data/SeedCatalog.kt` (catalog data), and the same "adding a file here is
  a deliberate act with a reviewer attached" framing applies verbatim;
- it includes an **anti-vacuity test** (`` `the ratio is still computed` ``) because "every other test
  here asserts an *absence*, and an absence passes trivially once the thing is gone". The i18n
  equivalent must assert the table is non-empty and that at least one page reads from it — otherwise
  deleting the whole feature turns the gate green.

### What breaks silently

- **Literal-count assertions.** Three tests assert a Japanese sentence appears *exactly once* in a
  source file: `RecordsIndexScreenStructureTest.kt:122-127` (`Regex("\"まだ 記録はありません\"").findAll(source).count()` == 1),
  `RecordsPrAndChartsStructureTest.kt:130-138` (same sentence, == 1 in each of two files),
  `ExerciseScreenStructureTest.kt:251-252` (`drawn()` counts `ExerciseNotice("$sentence")`). After
  migration the literal appears **zero** times in every page — these fail immediately, which is the
  good case. Their *intent* ("one state's wording is drawn from one place") is exactly what a key
  table enforces structurally, so they should be deleted, not ported.
- **Negative literal assertions.** `RecordsPrAndChartsStructureTest.kt:145-159`
  (`!charts.contains("text = \"二十八日")`, `!charts.contains("\"目安")`),
  `ExerciseScreenStructureTest.kt:76` (`!index.contains("読み込み中")`),
  `RecordsIndexScreenStructureTest.kt:96,131,135` (`!body.contains("ありません")`),
  `ScheduleNextAccessTest.kt:54` (`!source.contains("\"許可する\"")`). These become **vacuously true**
  the moment the literal leaves the file and will pass forever while enforcing nothing. This is the
  single most dangerous population in the suite: 8 assertions that go green and stay green.
- **Positive literal assertions against source.** `ScheduleNextAccessTest.kt:49,52`
  (`source.contains("\"設定を開く\"")`), `RecordsIndexScreenStructureTest.kt:43`
  (`body.contains("onClickLabel = \"記録の一覧をひらく\"")`), `:95,121,130,152`. These fail loudly. Port
  them to assert the *key* appears in the file.
- **`GymShellTest`'s orphan check** is not literal-sensitive (it reads function names) and survives.
  So does `AcwrRestraintTest` and `GymViewModelInitOrderTest`. But `GymShellTest.kt:167-176`
  (`` `予定に入れる is on the record screen, and only the live one` ``) asserts on
  `complete.contains("ScheduleNextAction(")` — safe — while its assertion *message* names the
  Japanese; leave it.

## 4. Counts, character counts and widths

| site | what it measures | breaks on a longer language? |
|---|---|---|
| `ui/TategakiTest.kt:65-76` | `layoutTategaki("一二三四五六七八九十", maxCells = 8)` → 8 cells + `truncated=true` | **Yes, and worse.** This is Home's vertical corner. `maxCells` is a hard glyph budget; a Japanese title of 8 kanji becomes 8 upright cells, and an English title of any length becomes **one rotated cell** (`:22-33`: `layoutTategaki("Design review").cells == listOf("Design review")`, `rotated=true`). So English never truncates and never overflows — it just renders as one long rotated run of unbounded pixel width. The truncation contract is meaningless in English and the layout has no width guard at all. |
| `ui/TategakiTest.kt:43-55` | 縦中横: `"第2会議室"` → 5 upright cells; `"2026年"` → `["2026"(rotated), "年"]`. Rule is "short number upright, long number rotated" | The digit-run threshold is tuned for a CJK grid. In English every run is latin and the rule never fires. |
| `ui/CycleDotsTest.kt:27` | comment records a rejected width predicate that "admitted seventeen dots at the player's 6.dp geometry" | No — dots, not text. |
| `gym/InkDensityTest.kt:34,53,58` | `grid.length` — **days in the month**, not a string length. `MonthGrid.length` | No. False positive; noting it so the next reader does not re-flag it. |
| `gym/data/GymMathTest.kt:85` | `assertEquals(10, storedDate(date).length) // the schema's CHECK asserts this width` | No — `YYYY-MM-DD`, a DB column width. Non-visible. |
| `gym/GymPageStateTest.kt:19-32` | section header says "preview widths"; the tests count **list items** (6 built-ins on first run, 4 after) | No. Another false positive. |
| `ui/gym/RecordsPrAndChartsStructureTest.kt:184-196,243` | `LIST_BOTTOM = 40.dp`, `minHeight = 48.dp` | No — padding and touch targets, not text extents. |

**Nothing in the suite asserts a text pixel width, a `maxLines`, or a `TextOverflow`.** There is not a
single `maxLines` or `ellipsis` assertion in `app/src/test`. The suite is blind to overflow, which
means a longer language will produce clipped screens that stay green. That is a gap to fill, not a
breakage to fix.

## Hazards

1. **`ui/TategakiTest.kt` / `ui/Tategaki.kt`** — `layoutTategaki(maxCells = …)`. Per-glyph vertical
   rendering with a fixed cell budget. English collapses to a single rotated cell of unbounded width;
   the `truncated` flag can never fire. Not a string swap; a layout redesign.
2. **`gym/KanaFoldingTest.kt` / `foldKana`** — search normalisation folds kana four ways and latin two
   (case + width, `:114`). A bilingual index needs both folds applied to both scripts, and
   `GymPageStateTest.kt:180-192` shows the search path already resolves station names through a
   resolver (`懸垂` must surface `シンディ`), so the fold sits on a real cross-field lookup.
3. **Kanji numerals are asserted everywhere.** `gym/NumeralsTest.kt` (`durationKanji`,
   `coefficientLabel`), `gym/InkDensityTest.kt:174,182,191` (`"三日 ・ 六月"`,
   `"六月、三日 鍛錬しました、いちばん多かったのは 六月十七日"`), `data/JapaneseDateTest.kt`
   (`kanjiExtended`×25). Every one is a *composition* of a numeral and a counter, and the
   arabic-vs-kanji rule is itself asserted (`gym/session/ui/PlayerCopyTest.kt`, `DECISIONS.md` §Q4's
   arabic countdown). Word order and pluralisation both differ in English.
4. **`gym/GymContractTest.kt:` `Tier.BEGINNER.storageValue` etc. are Japanese and asserted.** Three
   assertions pin Japanese strings that are written to the database. Persistence hazard — these must
   never be translated (see Non-visible Japanese).
5. **Assertion messages containing Japanese (19 sites)** will read oddly after migration but cost
   nothing. Do not churn them.
6. **Backticked test names contain Japanese (169 lines).** e.g.
   `` fun `予定に入れる is on the record screen, and only the live one` ``,
   `` fun `a reboot removes 続ける, and removes it rather than disabling it` ``. A mechanical
   find-and-replace over `app/src/test` **must not** touch these — they are documentation of intent,
   not copy, and renaming them destroys the git blame that explains why each test exists. Every
   large-refactor script must exclude backtick-delimited identifiers and comments.
7. **546 comment-only Japanese lines.** The KDoc in this repo quotes the copy it is arguing about, at
   length and deliberately (`AcwrRestraintTest`'s KDoc makes the case explicitly). A "no Japanese
   outside the table" gate that does not strip comments first will fail on ~546 lines of prose and the
   fix will be to delete the prose. Strip comments. Copy `AcwrRestraintTest.stripComments`.

## Non-visible Japanese

| literal(s) | where | why it stays Japanese |
|---|---|---|
| 入門 / 中級 / 上級 | `Tier.storageValue`, pinned by `gym/GymContractTest.kt` (3 assertions) | Written into SQLite. Translating orphans every stored row. |
| `"七分間"`, `"タバタ"`, `"シンディ"`, `"朝の五分"`, `"腕立て伏せ"`, `"懸垂"`, `"バーピー"` | fixtures across `gym/GymPageStateTest.kt` (19), `gym/LibraryFiltersTest.kt` (25), `ui/gym/ExerciseCatalogueCopyTest.kt` (15), `ui/gym/LibraryDetailCopyTest.kt` (13), `ui/gym/RecordsPrCopyTest.kt` (10) | Seeded catalog content (`catalog.*` root). These are *data*, and the tests use them as identity — `assertEquals(listOf("七分間"), sections.builtIn.map { it.name })` asserts *which routine matched*, not what it says. They stay as fixtures; whether the catalog itself translates is the `catalog.*` fragment's call, and if it does, these 154 fixture literals become the join key that breaks. |
| `it.note`, `station.note` | `gym/data/SeedCatalogTest.kt` (2) | Seed data. |
| test-name and comment Japanese (169 + 546 lines) | everywhere | Documentation. Never rendered. |
| `foldKana` inputs/outputs (82) | `gym/KanaFoldingTest.kt` | Algorithm fixtures for a Japanese-text function. |
| assertion failure messages (19) | across the suite | Developer-facing. |

---

# Job 2 — where the toggle goes

## 1. The settings surfaces that exist

**There is no app-wide settings page in the launcher.** `Screen` (`ui/TempoApp.kt:50`) is
`Home, Search, Notifications, Filter, Focus, Calendar, EventCompose, Gym` — none of them is settings.
The `Dock` (`ui/Dock.kt:91-94`) has exactly four buttons: ホーム / 検索 / 通知 / 鍛錬.

The **only** app-wide user setting the launcher exposes is the theme, and it lives in the **Search
screen's header row**, `app/src/main/java/io/eddiegulay/tempo/ui/SearchScreen.kt:117-130`:

```kotlin
// Trailing controls: hidden-apps filter page, then the theme toggle (relocated from the
// dock). Both stay faint, mirroring the prototype's quiet chrome.
Row(verticalAlignment = Alignment.CenterVertically) {
    HeaderIconButton(
        paths = TempoIcons.EyeOff,
        contentDescription = "非表示アプリ",
        onClick = onOpenFilter,
    )
    HeaderIconButton(
        paths = if (isDark) TempoIcons.Sun else TempoIcons.Moon,
        contentDescription = if (isDark) "ライトテーマに切り替え" else "ダークテーマに切り替え",
        onClick = onToggleTheme,
    )
}
```

`HeaderIconButton` is `SearchScreen.kt:267`, private to that file. The screen takes
`isDark: Boolean` and `onToggleTheme: () -> Unit` as parameters (`SearchScreen.kt:75-76`), wired from
`ui/TempoApp.kt:193-198`:

```kotlin
Screen.Search -> SearchScreen(
    viewModel = viewModel,
    isDark = isDark,
    onToggleTheme = viewModel::toggleTheme,
    onOpenFilter = viewModel::goFilter,
)
```

Note the comment "relocated from the dock" — this control has already moved once, and the header is
where it landed. It is a **blind toggle**, not a picker: one tap flips Paper↔Sumi
(`LauncherViewModel.kt:256-261`).

`ui/gym/GymSettingsScreen.kt` (803 lines, `fun GymSettingsScreen` at `:102`, reached via
`GymRoute.Settings` → `GymShell.kt:368`) is 鍛錬's page and scoped by its own KDoc to "everything that
changes how a workout behaves". Its rows are an enum, `SettingRow` in
`ui/gym/GymSettingsCopy.kt:55-65` (振動, 音, 音声, 目安で自動的に進む, 支度の長さ, 種目の間, 巡の間,
画面を消さない, 単位) grouped into `SettingSection` (`:42-47`: 合図, 進行, 休息の初期値, 表示). Adding a
language row there would (a) be unreachable to a user who never opens 鍛錬, (b) require editing two
enums that `ui/gym/GymSettingsCopyTest.kt` (17 tests) and `ui/gym/GymSettingsScreenStructureTest.kt`
(8 tests) both enumerate.

## 2. `ui/ModeDialog.kt` — not a settings surface

It is the **mode gate**: an `AlertDialog` with two rows, 集中 ("時計だけの画面") and 鍛錬 ("体を動かす"),
plus a やめる dismiss. Opened by long-pressing Home's clock (`pendingMode` in `LauncherViewModel`,
rendered at `ui/TempoApp.kt:232-238`), it calls `viewModel::confirmMode` with a `LauncherMode`.

Its KDoc rules itself out explicitly (`ModeDialog.kt:29-41`):

> It stays a gate rather than becoming a menu you can browse. Both modes take the whole window and
> change what the hardware does… **Rejected:** a dock tab or a Home glyph for 鍛錬 — the gym is a mode,
> and modes are entered deliberately or they are not modes.

Putting language in it would be exactly the "menu you can browse" it refuses. **But its *shape* is the
right one to copy**: a bordered `AlertDialog` of self-labelling rows, each with `onClickLabel = title`
and a gloss, is precisely what a language picker needs.

## 3. `ui/OnboardingScreen.kt` — first launch

Today: a scrolling `Column` (`:92-150`) — 「ようこそ」, a paragraph of rationale, then two `AccessItem`s:

- **既定のホーム** — HOME role, granted via `onRequestDefault` (`RoleManager`, `MainActivity.kt:105`)
- **通知へのアクセス** — notification listener, opens system Settings

each with a status dot, a rationale paragraph, and 許可 / 後で actions; deferral is local `remember`
state that never persists (`:84-86`). `始める` (`BeginButton`, `:242`) is inert until
`canBegin = launcherSettled && notifSettled` (`:88-90`), then calls `onComplete` →
`LauncherViewModel.completeOnboarding()` (`:281-283`) → `ThemeRepository.setOnboardingComplete()`.
The gate is chosen at `ui/TempoApp.kt:137-142` (`Layer.Onboarding`) and seeded synchronously from
DataStore so a returning user never sees it flash.

**A language step fits structurally and is needed semantically** — but not as a third `AccessItem`.
The whole page is Japanese prose; a user who cannot read Japanese cannot read the explanation of the
control that would let them fix that. So a language row must sit **above 「ようこそ」**, as a bare
self-labelling pair (日本語 / English) with no rationale text — the only widget on the page that is
legible before the choice is made. That is a new small composable, not an `AccessItem`.

Note also: onboarding is shown **once**. It cannot be the only home for the toggle.

## 4. How a theme change reaches Compose — the exact mechanism

This is the path language must follow. Six hops, no activity recreate, no `Configuration`.

**(a) Storage — `data/ThemeRepository.kt`.** One process-wide DataStore, `name = "tempo_settings"`
(`:26-29`), with a `SharedPreferencesMigration` from the legacy `"tempo"` prefs.

```kotlin
enum class TempoTheme { Paper, Sumi }                                    // :17
data class InitialSettings(val theme: TempoTheme, val onboardingComplete: Boolean)  // :20

private val themeKey = stringPreferencesKey("theme")                     // :37
val theme: Flow<TempoTheme> = context.tempoDataStore.data.map { it[themeKey].toTheme() }  // :40-42
fun loadInitialSettings(): InitialSettings = runBlocking {               // :59-65
    val prefs = context.tempoDataStore.data.first()
    InitialSettings(theme = prefs[themeKey].toTheme(), onboardingComplete = prefs[onboardingKey] ?: false)
}
suspend fun setTheme(theme: TempoTheme) { context.tempoDataStore.edit { it[themeKey] = … } }  // :67-71
```

Stored values are `"paper"` / `"sumi"`, with `"amoled"` still read as dark (`:79-88`) — the file
already carries a worked example of migrating a stored enum value without orphaning installs, which is
the precedent for the language key.

**(b) ViewModel — `LauncherViewModel.kt:69-75`.** Activity-scoped
(`MainActivity.kt:31`, `by viewModels { LauncherViewModelFactory(applicationContext) }`).

```kotlin
private val initialSettings = themeRepository.loadInitialSettings()      // :72
val theme: StateFlow<TempoTheme> = themeRepository.theme
    .stateIn(viewModelScope, SharingStarted.Eagerly, initialSettings.theme)  // :74-75
```

The synchronous read is *only* the `initialValue` of the `stateIn`; the DataStore `Flow` remains the
live source. **The write does not touch the StateFlow** — `toggleTheme()` (`:256-261`) writes to
DataStore and lets the Flow round-trip back:

```kotlin
fun toggleTheme() {
    viewModelScope.launch {
        val next = if (theme.value == TempoTheme.Sumi) TempoTheme.Paper else TempoTheme.Sumi
        themeRepository.setTheme(next)
    }
}
```

**(c) Pre-Compose frame — `MainActivity.kt:51-55`.** Before `setContent`, the window is painted from
`viewModel.theme.value` so a returning user never sees a flash of the wrong colour:

```kotlin
val isDark = viewModel.theme.value == TempoTheme.Sumi
window.setBackgroundDrawable(ColorDrawable(if (isDark) WINDOW_SUMI else WINDOW_PAPER))
```

(`WINDOW_PAPER`/`WINDOW_SUMI` at `:118-122`, hand-mirroring `PaperColors.bgSolid`/`SumiColors.bgSolid`.)

**(d) Collection — `ui/TempoApp.kt:73`.**
`val theme by viewModel.theme.collectAsStateWithLifecycle()`

**(e) Selection — `ui/TempoApp.kt:82-83`.**
`val isDark = theme == TempoTheme.Sumi` / `val colors = if (isDark) SumiColors else PaperColors`

**(f) Distribution — `ui/TempoApp.kt:144`.**

```kotlin
CompositionLocalProvider(LocalTempoColors provides colors) {
    Box(Modifier.fillMaxSize().tempoBackground(colors)) { … }
}
```

`LocalTempoColors` is **`staticCompositionLocalOf { PaperColors }`** (`ui/theme/TempoTheme.kt:77`).
That choice is the whole mechanism: a *static* local does not track individual reads, so changing its
value **invalidates and recomposes the entire content lambda of the provider** rather than only the
composables that read it. Every screen does `val c = LocalTempoColors.current` and is repainted. The
`Layer.Gym` branch (`TempoApp.kt:163`) is composed *inside* that provider, so 鍛錬 inherits it for
free — there is no second provider anywhere in the app.

Two side channels ride along: `SideEffect` at `TempoApp.kt:99-105` sets
`isAppearanceLightStatusBars`/`isAppearanceLightNavigationBars` from `isDark`, and `isDark` is passed
explicitly as a parameter to `SearchScreen` so its icon can flip.

**What language must do, mechanism for mechanism:**

| theme | language |
|---|---|
| `enum class TempoTheme { Paper, Sumi }` (`ThemeRepository.kt:17`) | `enum class TempoLanguage { Ja, En }` in the same file |
| `stringPreferencesKey("theme")`, values `"paper"`/`"sumi"` | `stringPreferencesKey("language")`, values `"ja"`/`"en"`; absent → device-locale default, then persisted on first explicit choice |
| `InitialSettings(theme, onboardingComplete)` (`:20`) | add a third field — it is already the "synchronously for the first frame" struct, and the first frame must not be Japanese-then-flip |
| `val theme: Flow<TempoTheme>` + `suspend fun setTheme` | `val language: Flow<TempoLanguage>` + `suspend fun setLanguage` |
| `val theme: StateFlow<…> = …stateIn(…, initialSettings.theme)` (`LauncherViewModel.kt:74`) | `val language: StateFlow<TempoLanguage> = …stateIn(…, initialSettings.language)` |
| `fun toggleTheme()` writes to DataStore only (`:256`) | `fun setLanguage(l: TempoLanguage)` — a setter, not a toggle |
| `collectAsStateWithLifecycle()` (`TempoApp.kt:73`) | same line, one below |
| `staticCompositionLocalOf { PaperColors }` (`TempoTheme.kt:77`) | `staticCompositionLocalOf { JaStrings }` — a `Strings` table object. Static is correct and matches precedent: a language change must repaint everything, and per-read tracking would buy nothing. |
| `CompositionLocalProvider(LocalTempoColors provides colors)` (`TempoApp.kt:144`) | add `LocalStrings provides strings` to the **same call** — one provider, one recomposition, gym included |
| `MainActivity`'s pre-Compose window paint (`:54`) | no analogue needed; language paints no pre-Compose surface |

Three consumers sit **outside** the composition and cannot use the local — they must read
`LauncherViewModel.language.value` or take the repository directly:
`gym/TrainingNotice.kt` (the foreground-service notification, tested by `gym/TrainingNoticeTest.kt`),
`gym/cue/GymSpeech.kt:107` and `ui/gym/GymSettingsScreen.kt:775` — both of which hard-code
`engine.setLanguage(Locale.JAPANESE)` and are the only two `Locale.` references in the whole main
source set.

## 5. Configuration changes and the manifest

`app/src/main/AndroidManifest.xml`, `MainActivity`:

```xml
android:launchMode="singleTask"
android:stateNotNeeded="true"
android:excludeFromRecents="true"
android:configChanges="keyboard|keyboardHidden|navigation|orientation|screenSize|screenLayout|smallestScreenSize|density|uiMode"
android:windowSoftInputMode="adjustResize"
```

**`locale` and `layoutDirection` are *not* in that list.** So:

- A **system** locale change today recreates `MainActivity`. Nothing observes it — there is no
  `onConfigurationChanged`, no `LocalConfiguration` read, no `res/values-*` string set to swap
  (`res/values/strings.xml` holds exactly one entry, `app_name`, and there is no `values-ja`). The
  recreate is currently harmless and invisible.
- If language were implemented via **per-app locales** (`AppCompatDelegate.setApplicationLocales` /
  `LocaleManager.setApplicationLocales`), the framework would fire a locale config change and
  **recreate the activity** on every switch. `LauncherViewModel` and `GymViewModel` survive
  (ViewModelStore outlives config change), so `_screen` and a live workout are safe; but `onCreate`
  re-runs, the window is repainted, `setContent` rebuilds the tree, and the user sees a flash. It also
  drags in an appcompat dependency the app does not currently have.
- **Recommended: do not go near per-app locales.** Every string is a Kotlin literal; the translation
  table is a Kotlin object; the switch is a `staticCompositionLocalOf` swap. That is a recomposition,
  not a recreate — no manifest change, no new dependency, no flash, and it works identically on every
  API level. `CONTRIBUTING.md`'s dependency discipline (cited by `GymViewModelInitOrderTest`'s KDoc as
  the reason it reads source rather than constructing a ViewModel) points the same way.
- The one thing that *would* need `configChanges` attention is RTL, if a right-to-left language is
  ever added: `android:supportsRtl="true"` is already set on `<application>`, but `layoutDirection` is
  not in `configChanges`, and no test asserts anything about direction. Out of scope today; worth a
  line in the plan.

## Recommendation

**Put the language control in the Search screen header, as a third `HeaderIconButton` that opens a
`LanguageDialog` — and put a bare language row at the very top of `OnboardingScreen`.**

Concretely:

1. `ui/LanguageDialog.kt`, modelled on `ui/ModeDialog.kt`: an `AlertDialog` with self-labelling rows
   (`日本語` / `English`), each `clickable(onClickLabel = title, role = Role.Button)`, dismiss on やめる.
   Self-labelling matters — the row must be readable *before* the language is chosen, which is why
   this is a picker and not a copy of the theme's blind toggle.
2. `LauncherViewModel` gains `pendingLanguage: StateFlow<Boolean>` + `setLanguage(…)`, mirroring
   `pendingMode`/`confirmMode`; `TempoApp` renders the dialog beside `ModeDialog` at `TempoApp.kt:232`.
3. `SearchScreen` gains `language: TempoLanguage` and `onOpenLanguage: () -> Unit`, wired at
   `TempoApp.kt:193-198` exactly as `isDark`/`onToggleTheme` already are.
4. `OnboardingScreen` gains a `LanguageRow` above 「ようこそ」 calling the same `setLanguage`.

Why:

- **It is where app-wide settings already live.** The theme toggle is the only precedent, and it is
  right there. A user who has learned that Tempo's global switches sit in the Search header finds the
  second one on the first try. The comment "relocated from the dock" says this placement was already
  argued once and settled.
- **Reachable in one tap from anywhere.** 検索 is a dock tab; every launcher screen is one tap from it.
  `GymSettingsScreen` requires entering a mode.
- **Zero structural cost.** No new `Screen` value, no `BackHandler` arm (`TempoApp.kt:123-133`), no
  dock button, no route in `GymRoute`, no new page of Japanese copy. `HeaderIconButton`
  (`SearchScreen.kt:267`) and the two-row `AlertDialog` both already exist.
- **Onboarding closes the bootstrap hole.** Without a first-launch row, a non-Japanese reader must
  navigate a Japanese onboarding gate, find a Japanese dock, and recognise an unlabelled icon. The
  header alone is not enough; onboarding alone is not enough (it shows once). Both, and the pair is
  four small composables.
- **It rides the theme's exact plumbing** (§4), which is already proven, already synchronous on the
  first frame, and already covers 鍛錬 through one `CompositionLocalProvider`.

**Second best: a new `Screen.Settings` page in the launcher.**

This is the architecturally correct answer and where this ends up eventually — a real app-wide
settings page owning language, theme, hidden apps and an onboarding replay, instead of three controls
scattered across a search header. Rejected **now** because:

- it costs a new `Screen` enum value, a `BackHandler` arm, an entry point (and the `Dock` has exactly
  four buttons by deliberate design — `ModeDialog`'s KDoc rejects a fifth for 鍛錬 on the same
  grounds), and a whole page of section headings and row labels;
- that page's copy would itself be new untranslated Japanese — the translation feature generating more
  translation work is a bad trade at the point where the table has one row in it;
- the app has reached 1,333 tests with no launcher settings page. Inventing one to host a single
  control is the browsable-menu chrome this codebase consistently argues against. Build it when there
  are three or more app-wide settings to put on it, and move the theme toggle onto it at the same time.

**Also rejected:** `ui/gym/GymSettingsScreen.kt` — an app-wide setting inside a feature, invisible to a
user who never trains, and it breaks two test files' enumerations of `SettingRow`/`SettingSection`.
**And** `ui/ModeDialog.kt` — its own KDoc refuses to become a menu; borrow its shape, not its slot.
