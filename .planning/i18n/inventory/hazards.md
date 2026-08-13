# Hazards — cross-cutting lens

**Files surveyed:** 132 (all `.kt` under `app/src/main/java` grepped; 27 read in full or in the relevant
part; 4 `res/font/*.ttf` binaries inspected with fontTools)
**User-visible literals:** 0 — deliberately out of scope; the per-directory fragments own them
**Non-visible JP literals:** 9 (listed at the end)

This fragment answers one question: **what breaks when the language is no longer Japanese, in a way
that swapping a string table will not fix?** Findings are ranked by severity, not by file.

---

## Hazards

### S1 — Visibly breaks. The screen is wrong, unreadable, or silent.

---

#### H-1. `VerticalLine` stacks one character per row, unconditionally, and Home's corner uses it for three of its four columns

`app/src/main/java/io/eddiegulay/tempo/ui/HomeScreen.kt:298-313`

```kotlin
@Composable
private fun VerticalLine(text: String, color: Color, size: TextUnit = 19.sp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        text.forEach { ch ->
            Text(
                text = ch.toString(),
```

This is not `TategakiText` — it has no upright/rotated distinction, no 縦中横, no cell cap and no
overflow marker. It stacks **every** `Char`, one 19.sp `Text` per glyph, with 3.dp between them.

Call sites, all on Home's top-right corner cluster:

- `HomeScreen.kt:293` — `VerticalLine(JapaneseDate.dayOfWeek(now), colors.inkFaint)` → 水曜日, 3 cells.
  English `Wednesday` is **9 cells** ≈ 9 × (19sp + 3dp) ≈ 190dp of column.
- `HomeScreen.kt:294` — `VerticalLine(JapaneseDate.monthDay(now), colors.inkSoft)` → 六月十七日, 5 cells.
  `June 17` is 7 cells *including the space*, which renders as a blank row.
- `HomeScreen.kt:295` — `VerticalLine(JapaneseDate.era(now), colors.inkSoft)` → 令和八年, 4 cells.
- `HomeScreen.kt:282` — `VerticalLine(time, colors.inkSoft, size = 17.sp)` → 十九時三十分, 6 cells.
  `19:30` is 5 cells but the `:` gets its own row.
- `HomeScreen.kt:283` — `VerticalLine(day, colors.inkFaint, size = 15.sp)` → 今日, 2 cells.
  `Tomorrow` is 8.

The sibling column right next to these is bounded — `HomeScreen.kt:279`,
`modifier = Modifier.heightIn(max = 150.dp)` on the `TategakiText` — but `VerticalLine` has **no height
bound at all**. Three unbounded ransom-note columns will run down the screen into the 104sp clock.
Latin in `VerticalLine` is the exact failure mode `Tategaki.kt:29-31` names and rejects:

> Stacking every Latin letter — S / t / a / n / d / u / p — is a ransom note: seven cells for a
> seven-letter word, with no vertical centre of gravity.

The fix is not a string table. Either the corner stops being vertical for Latin locales, or
`VerticalLine` is retired in favour of `TategakiText` and given a height budget.

---

#### H-2. Tategaki itself is a Japanese typesetting mode with no Latin equivalent

`app/src/main/java/io/eddiegulay/tempo/ui/Tategaki.kt`

What it does, precisely:

1. `layoutTategaki(text, maxCells = 8)` (`Tategaki.kt:113-141`) segments the string into maximal
   upright and sideways runs. The classifier is one line:
   `Tategaki.kt:148` — `private fun isUpright(ch: Char): Boolean = ch.code >= 0x2E80`
2. Each **upright** char becomes its own cell. Each **sideways** run becomes one cell.
3. `Tategaki.kt:137` — `val upright = run.length <= 2 && run.all { it.isDigit() }` — a one- or
   two-digit number stays upright as a single cell (縦中横).
4. `Tategaki.kt:120` — after `maxCells` (default 8) it stops and sets `truncated`, which draws a `…`
   cell (`Tategaki.kt:70`).
5. `Modifier.quarterTurn()` (`Tategaki.kt:84-101`) rotates a Latin run 90° clockwise **and swaps its
   reported size**, because `Modifier.rotate` alone is a draw-time transform.

Callers: exactly one — `HomeScreen.kt:269`, the next calendar event's title on Home.

For an English event title the whole string is one run, so it degenerates to a single
quarter-turned `Text` — the user reads `Design review` by tilting their head right, in a column beside
what are now three ransom-note columns (H-1). That is *correct tategaki* and *wrong English*. There is
no Latin equivalent of vertical-rl; the answer is a horizontal corner cluster for non-Japanese
locales, which is a layout change, not a copy change.

Note the `maxCells = 8` budget is a **glyph count**, and in Japanese 8 glyphs is a whole title. In
English 8 characters is `Design r…`.

`TategakiTest.kt` (88 lines) pins the segmentation behaviour and will need rewriting or gating.

---

#### H-3. Text-to-speech is hard-wired to `Locale.JAPANESE` in two places, and the unavailability model is *named* for Japanese

`app/src/main/java/io/eddiegulay/tempo/gym/cue/GymSpeech.kt:107`

```kotlin
val result = runCatching { engine.setLanguage(Locale.JAPANESE) }
    .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
availability = when (result) {
    TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED ->
        SpeechAvailability.NoJapaneseVoice
    else -> SpeechAvailability.Available
}
```

`app/src/main/java/io/eddiegulay/tempo/ui/gym/GymSettingsScreen.kt:775` — the settings-page **probe**,
a second independent copy of the same decision:

```kotlin
val result = runCatching { engine.setLanguage(Locale.JAPANESE) }
```
`GymSettingsScreen.kt:778` — `TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> SpeechAvailability.NoJapaneseVoice`

**Does the model generalise?** Structurally yes; nominally no.

`app/src/main/java/io/eddiegulay/tempo/gym/GymPreferences.kt:22`

```kotlin
enum class SpeechAvailability { Available, NoJapaneseVoice, NoEngine }
```

The three-state model (`Available` / voice-missing / no-engine) plus the nullable "not probed yet"
fourth state is exactly right for any language — the shape survives. What does not survive:

- the **enum case name** `NoJapaneseVoice`, threaded through 8 files (`CueSettings.kt:52`,
  `CueSinks.kt:78`, `CueEngine.kt:71`, `GymSettingsCopy.kt:160`, both probes, `GymPreferences.kt:22`);
- the **copy** it selects, `GymSettingsCopy.kt:160` →
  `RowState(enabled = false, subtitle = SUB_NO_JAPANESE_VOICE)`. That subtitle names Japanese. When
  the UI language is English and the device has no English voice, this string is a lie about which
  voice is missing;
- the fact that **the probe language must now track the selected UI language**, and the probe result
  must be re-run when the language changes. Today `rememberSpeechAvailability()`
  (`GymSettingsScreen.kt:724`) probes once with a constant.

**The concrete new failure**: today a Japanese device almost always has a Japanese voice. An English
UI on a Japanese-market device may well have no English voice — so the 音声 row will be *disabled by
default* for a population it never was before, with a subtitle that says the wrong language is
missing. The graceful degradation (`GymSpeech.kt:38-40`: silent fallback to tones, never prompt for a
voice download mid-workout) does hold, so this degrades rather than crashes — but it is silently
disabled speech, which is an S1 for a workout timer.

Every spoken string is also composed, not looked up — see H-4.

---

#### H-4. Kanji numerals and counter suffixes: ~180 call sites implementing a rule that is meaningless outside Japanese

The rule, stated at `app/src/main/java/io/eddiegulay/tempo/gym/Numerals.kt:60-63`:

> This is the counts-are-kanji half of `DECISIONS.md` §Q4. A *ticking* value stays arabic — the
> player's countdown, the breakdown's `0:41`, the wheel mid-spin — because a kanji column changing
> under the finger is unreadable. Everything that has stopped moving is kanji.

**The two formatters.**

`app/src/main/java/io/eddiegulay/tempo/data/JapaneseDate.kt:21-27` — `kanji(n)` for 0..99, table
`private val K = charArrayOf('〇','一','二','三','四','五','六','七','八','九')` (`JapaneseDate.kt:15`).

`JapaneseDate.kt:53-72` — `kanjiExtended(n)` for 100..9999 with the 千/百 elisions, falling back to
`n.toString()` above 9999 and below 0. This is *the* kanji numeral formatter; `Numerals.kt:9-13`
explicitly forbids a second one.

`Numerals.kt:48-56` — `coefficientLabel(Double?)` → `一.〇`, `〇.五`. Kanji digits with an **ASCII full
stop** between them. In English this is simply `1.0`.

`Numerals.kt:81-88` — `durationKanji(Int)` → `五秒` / `一分三十秒` / `二十分`. Two hard rules that do not
port: **whole minutes drop their seconds** (1200 → 二十分, not 二十分〇秒), and **there are no hours**
(6000 s → 百分, not 一時間四十分, `Numerals.kt:71-77`). "100 minutes" is not acceptable English.

`Numerals.kt:98-99` — `durationKanjiFromMs(Long)`, truncating not rounding.

`Numerals.kt:112-115` — `clockDuration(Long)` → `"${totalSeconds / 60}:%02d".format(...)`. The arabic
half. **This one ports unchanged** and is the only numeral function in the app that does.

**The counter suffixes.** Every call site is `formatter(n) + <counter>`, string concatenation, where
the counter is a Japanese measure word with no English equivalent and English needs pluralisation
instead. Counters found and their sites (non-exhaustive but representative — I counted ~180
`kanji`/`kanjiExtended`/`durationKanji` call sites across 24 files):

| counter | means | example sites |
|---|---|---|
| `回` | times / reps | `RecordsIndexScreen.kt:140`, `ExerciseDetailScreen.kt:207,212`, `BuilderScreen.kt:410,626`, `RecordCopy.kt:191-193,252,285,355,470`, `PlayerCopy.kt:220,229,238`, `HistoryPaging.kt:81`, `ChartGeometry.kt:317,324` |
| `巡` | rounds/laps | `GymHomeCopy.kt:139,166`, `BuilderScreen.kt:629`, `PlayerCopy.kt:102,120,124,315`, `EngineRows.kt:68,190`, `LibraryDetailScreen.kt:318` |
| `種目` | stations/events | `PlayerCopy.kt:105,313,370`, `LibraryIndexScreen.kt:173`, `GymHomeCopy.kt:256,495`, `GymStore.kt:2069`, `RecordCopy.kt:133` |
| `秒` | seconds | `BuilderScreen.kt:411,620,623`, `GymSettingsCopy.kt:220`, `EngineRows.kt:108`, `BuilderDraft.kt:429,442`, `PlayerCopy.kt:73,200,203,280,283,325,329`, `GymStore.kt:2072` |
| `分` | minutes | `RecordsIndexScreen.kt:141`, `ChartGeometry.kt:324-325`, `RoutineEstimate.kt:207`, `RecordCopy.kt:285`, `GymStore.kt:2082` |
| `日` | days | `RecordSummary.kt:259`, `RecordCopy.kt:250,352`, `InkDensity.kt:196-197,227`, `GymHomeCopy.kt:225` |
| `月` | months | `RecordsIndexScreen.kt:222,244`, `HistoryPaging.kt:80`, `InkDensity.kt:197,217` |
| `件` | generic items | `ExerciseDetailScreen.kt:269`, `LibraryIndexScreen.kt:228` |
| `番目` | ordinal position | `BuilderScreen.kt:417,425`, `LibraryDetailScreen.kt:244` |
| `第…` | ordinal prefix | `EngineRows.kt:238` — `"第" + JapaneseDate.kanjiExtended(state.currentStepIndex) + unit` |

Four representative compositions, each of which is a word-order problem as well as a numeral one:

- `RecordSummary.kt:219` — `fun failedOutChip(activeMs: Long): String = durationKanjiFromMs(activeMs) + "で 力尽きた"`
- `PlayerCopy.kt:105` — `JapaneseDate.kanjiExtended(stationsPerRound) + "種目中 " + JapaneseDate.kanjiExtended(station + 1)` (`N of M`, reversed)
- `GymHomeCopy.kt:344` — `"これまでの" + JapaneseDate.kanjiExtended(timesDone) + "回の記録は残ります。型だけが一覧から消えます。"` (number infixed mid-sentence)
- `EngineRows.kt:246` — `title + " ・ " + reps.joinToString(" ") { JapaneseDate.kanjiExtended(it) }` (a *list* of kanji numerals)

**Pluralisation is a new requirement everywhere in that table.** Japanese has none; every one of
these becomes a plural-aware format in English.

**The arabic/kanji split has one more consequence nobody has costed**: in English there is no split
left. `clockDuration` and `durationKanji` collapse into the same thing, and the three or four
deliberately-divergent twins that `GymSettingsCopy.kt:199-217` documents (§Q10's "a duration the user
*chose*" vs "a duration the app *measured*") lose their reason to differ. `GymSettingsCopy.kt:210`
warns: *"If they are ever unified, unify them onto §Q10's sentence and never onto `durationKanji`."*
That instruction is about Japanese and does not survive translation — but the four twins
(`BuilderDraft.restWheelLabel`, `EngineRows.restLabel`, `GymSettingsCopy.settingsSecondsLabel`,
`ui.gym.session.chosenSecondsLabel`) each pin 六十秒 in their own test, so all four tests break.

---

#### H-5. Dates: era, weekday, month-day and relative words are all composed from kanji tables

`app/src/main/java/io/eddiegulay/tempo/data/JapaneseDate.kt`

| line | function | output | why it does not port |
|---|---|---|---|
| `:18` | `DOW` table | `charArrayOf('日','月','火','水','木','金','土')` | one character per weekday. Latin weekday names are 6–9 characters. Sunday-first, JS `getDay()` indexing |
| `:88` | `era(now)` | `"令和" + kanji(now.year - 2018) + "年"` → 令和八年 | **Reiwa era**. There is no English equivalent; `2026` is the replacement, and the hard-coded `2018` offset silently breaks at the next era change regardless of language |
| `:91-92` | `monthDay(now)` | `kanji(month) + "月" + kanji(day) + "日"` → 六月十七日 | month-then-day order is not universal |
| `:95` | `dayOfWeek(now)` | `dowChar(now) + "曜日"` → 水曜日 | |
| `:78-85` | `reading(now)` | `午前/午後 + kanji(h12) + "時" + kanji(m) + "分"` → 午後九時一分 | a *spoken-style* reading of the clock, shown under Home's 104sp digits. English has no idiomatic equivalent that is not just re-reading the digits |
| `:112-115` | `eventTime(at)` | `kanji(hour) + "時" + minutes` → 十九時三十分 | 24-hour kanji |
| `:124-129` | `dayToken` | `今日` / `明日` / weekday / month-day | relative words |
| `:132-136` | `dayHeading` | `dayOfWeek(...) + " ・ " + monthDay(...)` | the `・` separator is a CJK middle dot |

`JapaneseDate.time` (`:75`) and `JapaneseDate.clock` (`:118`) are `%02d:%02d` and port unchanged.

**A second, independent relative-day vocabulary exists** —
`app/src/main/java/io/eddiegulay/tempo/ui/gym/GymHomeCopy.kt:220-227`:

```kotlin
fun relativeDayJa(then: LocalDate, today: LocalDate): String {
    val days = today.toEpochDay() - then.toEpochDay()
    return when {
        days <= 0L -> "きょう"
        days == 1L -> "きのう"
        else -> JapaneseDate.kanjiExtended(days...) + "日前"
    }
}
```

`GymHomeCopy.kt:213-215` documents this as deliberate: hiragana きょう/きのう looking **backwards**,
kanji 今日/明日 looking **forwards**, "two surfaces, two vocabularies, both documented." In English
both are `Today`. That distinction has no target and the two functions collapse — which means one of
the two documented behaviours is being deleted, not translated. Flag for a decision.

The function name `relativeDayJa` and `formatElapsedJa` (`GymHomeCopy.kt:208`) carry `Ja` in their
identifiers.

**Callers of `JapaneseDate.*` date functions** (35 sites): `HomeScreen.kt:129,141,187,189,190,254,255,293,294,295`,
`CalendarScreen.kt:110,169,202,208,213`, `NotificationsScreen.kt:124`, `EventComposeScreen.kt:326,327`,
`CalendarFeedback.kt:303,307`, `TempoWheel.kt:211`, `GymHomeScreen.kt:161`, `LibraryIndexScreen.kt:367`,
`SessionDetailScreen.kt:163`, `RecordsIndexScreen.kt:187,222,244`, `RecordsHistoryScreen.kt:405`,
`RecordsPrScreen.kt:217`, `ExerciseDetailScreen.kt:216`, `LibraryDetailScreen.kt:316`,
`RecordCopy.kt:250,469`, `InkDensity.kt:231`, `ChartGeometry.kt:379`, `HistoryPaging.kt:80`.

`JapaneseDateTest.kt` is 131 lines and pins these outputs.

---

#### H-6. `TempoWheel`'s day column is a fixed-height row that already ellipsises Japanese

`app/src/main/java/io/eddiegulay/tempo/ui/TempoWheel.kt:43-44`

```kotlin
internal const val WHEEL_ROW = 44
internal const val WHEEL_HEIGHT = WHEEL_ROW * 3
```

`TempoWheel.kt:284-294` — each row is a `maxLines = 1, overflow = TextOverflow.Ellipsis` `Text` at
22.sp selected / 16.sp unselected, in a `Box(...height(WHEEL_ROW.dp))`.

The day column (`TempoWheel.kt:210-217`) is fed `JapaneseDate.dayToken(it, today)` — today's longest
value is `十二月三十一日` (7 glyphs). The column is `WheelWidth.Fill` sharing what the two
`WheelWidth.Fixed(58.dp)` hour/minute columns leave (`TempoWheel.kt:224,232`). `Wednesday 31 December`
in that residual width at 22.sp will ellipsise to something like `Wednesday 3…`, which is a value the
user cannot read while dialling it.

The hour/minute columns are `"%02d".format(it)` and port unchanged. The 58.dp fixed width is sized
for two digits and is fine.

---

### S2 — Looks wrong. It renders, but the design is broken.

---

#### H-7. `HeroTime` sizes text by `String.length`, and says in its own comment why that only works for CJK

`app/src/main/java/io/eddiegulay/tempo/ui/gym/RecordSummary.kt:661-675`

```kotlin
BoxWithConstraints {
    val glyphs = text.length.coerceAtLeast(1)
    val cap = (maxWidth.value / (glyphs * density.fontScale)).sp
    val size: TextUnit = if (cap < 64.sp) cap else 64.sp
```

`RecordSummary.kt:654-656` states the assumption outright:

> Dividing by **the string's own length** instead invents no number at all and is the more accurate
> form anyway, **because a CJK glyph advances almost exactly one em.**

I verified this against the shipped fonts. In `shippori_mincho_regular.ttf` (unitsPerEm 1000): `分`
advances **1000**, `あ` advances **1000** — the comment is exactly right. But `A` advances **762**,
`0` advances **520**, `i` advances **317**, `W` advances **1022**. Latin advance varies over 3× and
averages roughly 0.5 em.

Consequence: the hero session time — the largest number on the session-complete screen — will be
sized at roughly **half** the width it should be for an English string. `六分十四秒` (5 glyphs) caps
at `width/5`; `6 min 14 sec` (12 chars) caps at `width/12`, producing a hero number about 40% of its
intended size on the same phone. It will not overflow — `maxLines = 1, softWrap = false`
(`RecordSummary.kt:671-672`) — it will just be small and wrong.

The fix is `TextMeasurer`, or Compose's `BasicText(autoSize = …)`. Not a string table.

---

#### H-8. The player's hero-numeral cap divides by a hard-coded 4.2, which is a glyph count

`app/src/main/java/io/eddiegulay/tempo/ui/gym/session/LivePlayer.kt:167-171`

```kotlin
BoxWithConstraints(modifier.fillMaxSize()) {
    val cap = with(LocalDensity.current) { maxWidth.toSp() } / 4.2f
    CompositionLocalProvider(
        LocalGymAnimations provides animations,
        LocalHeroCap provides if (cap.value < 88f) cap else 88.sp,
```

`LivePlayer.kt:96-99` explains: *"4.2 is four glyphs plus slack for the colon"*, encoding the shape of
`0:23`.

This one is **less bad than H-7**, because the value it caps is always `clockDuration`-shaped
(`%d:%02d`) and therefore always ASCII digits — so its content does not change with language. But
`LocalHeroCap` is consumed by `heroSize()` (`LivePlayer.kt:107-111`) and applied to **every** hero
numeral on `PreparePage.kt:79`, `WorkPage.kt:78`, `RepsPage.kt:124`, `RestPage.kt:109`,
`PausedPage.kt:103`. If any of those grows a word ("3 reps left" rather than a bare numeral) the 4.2
divisor is immediately wrong. Also note 4.2 was tuned for Mincho *digit* metrics — `0` is 0.52 em, so
4 digits + colon is ≈2.4 em, not 4.2. The constant already carries generous slack for CJK-ish
proportions; it is not a measurement.

---

#### H-9. `letterSpacing` tuned for CJK — 55 sites at 4sp or more, some at 6–8sp

CJK typography routinely tracks headings by 20–30% of the em because every glyph is a full square.
Latin at the same tracking looks like `s p a c e d   o u t` text. The prevailing idiom in this
codebase is `fontSize = 17.sp, letterSpacing = 4.sp` — **24% tracking** — and it appears ~30 times.

The worst offenders, all on labels that will become English words:

- `app/src/main/java/io/eddiegulay/tempo/ui/FocusScreen.kt:171-178` — the Pomodoro phase label:
  ```kotlin
  text = controller.phase.label,
  style = TextStyle(fontFamily = Mincho, fontSize = 26.sp, letterSpacing = 8.sp, ...)
  ```
  **31% tracking at 26.sp.** The Japanese label is two glyphs; `Focus` / `Break` at 8sp tracking is
  unreadable as a word.
- `app/src/main/java/io/eddiegulay/tempo/ui/SearchScreen.kt:113-116` — `"けんさく"` at
  `fontSize = 14.sp, letterSpacing = 6.sp` — **43% tracking**.
- `app/src/main/java/io/eddiegulay/tempo/ui/FilterScreen.kt:53` — same, `letterSpacing = 6.sp` at 14.sp.
- `app/src/main/java/io/eddiegulay/tempo/ui/OnboardingScreen.kt:101` — `fontSize = 30.sp, letterSpacing = 6.sp`
  and `:257` — `fontSize = 16.sp, letterSpacing = 6.sp` (37%).
- `app/src/main/java/io/eddiegulay/tempo/ui/gym/session/PreparePage.kt:73`,
  `RestPage.kt:93`, `PausedPage.kt:97` — `fontSize = 15.sp, letterSpacing = 6.sp` (40%) on the
  phase captions.
- `app/src/main/java/io/eddiegulay/tempo/ui/gym/RecordSummary.kt:607` — `letterSpacing = 6.sp`.
- `app/src/main/java/io/eddiegulay/tempo/ui/gym/GymTabBar.kt:126-133` — the three tab words at
  `fontSize = 13.sp, letterSpacing = 3.sp` (23%), each in a `Modifier.weight(1f)` third of the screen
  (`GymTabBar.kt:74`). 鍛錬 / 型 / 記録 are 2/1/2 glyphs; `Training` / `Forms` / `Records` at 23%
  tracking in a 1/3-screen column will not fit and there is no `maxLines`/`overflow` on that `Text`.

Full count: `letterSpacing` appears **212 times** across 30 files; **55** of those are ≥ 4.sp.
This needs a locale-conditional type scale in `ui/theme/Type.kt`, which currently declares only two
`FontFamily` values and no `TextStyle`s at all.

---

#### H-10. Fonts: full ASCII, patchy Latin-Extended, **zero Cyrillic**, and CJK vertical metrics

`app/src/main/java/io/eddiegulay/tempo/ui/theme/Type.kt` declares two families over four bundled TTFs.
Measured with fontTools:

| font | total cmap | ASCII | Latin-Ext-A/B (U+00C0–017F) | Cyrillic (U+0400–045F) |
|---|---|---|---|---|
| `shippori_mincho_regular.ttf` | 2676 | **95/95** | 67/192 | **0** |
| `shippori_mincho_medium.ttf` | 2676 | **95/95** | 67/192 | **0** |
| `zen_kaku_gothic_new_light.ttf` | 2717 | **95/95** | 72/192 | **0** |
| `zen_kaku_gothic_new_regular.ttf` | 2717 | **95/95** | 72/192 | **0** |

Present: `à á â ä ã è é ê ë ì í î ï ò ó ô ö õ ù ú û ü ñ ç ß å æ ø œ` and their capitals — so
**English, French, German, Spanish, Italian, Portuguese and the Nordics are fully covered.**

Missing in **all four** fonts: `ş ğ ł ż ą ę ć ń ź ř č š ž`. Turkish, Polish, Czech, Slovak, Croatian,
Romanian and Vietnamese will fall back to the platform Noto **per glyph, mid-word**, producing
visibly mixed type inside a single word. Russian/Ukrainian fall back entirely.

**Metrics.** All four fonts: `unitsPerEm 1000`, `hhea.ascender 1160`, `hhea.descender -288`,
`lineGap 0` → a default line box of **1.448 em**, which is CJK-normal and unusually tall for Latin.
Combined with the explicit `lineHeight` overrides that exist (e.g. `HomeScreen.kt:134`
`lineHeight = 94.sp` under `fontSize = 104.sp` — a *negative* leading tuned for square glyphs;
`CalendarScreen.kt:334` `fontSize = 18.sp, lineHeight = 28.sp`), multi-line English body copy will
have conspicuously loose leading, and the clock's negative leading may clip Latin descenders and
accents.

Also `HomeScreen.kt:135` — `letterSpacing = (-1).sp` on the 104sp clock, negative tracking tuned to
square digits.

Recommendation: this is fixable without dropping the design — bundle a Latin companion (the same
foundries ship Latin-covering siblings) and select it by locale in `Type.kt` — but it is a real
asset-and-code change, not a table swap.

---

#### H-11. The app-drawer search is a plain substring match — the kana-aware matcher exists but the launcher does not use it

Two search implementations coexist and only one is script-aware.

**The good one**, `app/src/main/java/io/eddiegulay/tempo/gym/LibraryFilters.kt:71-113`, `foldKana`:
half-width katakana widened and **composed** with their voicing marks, katakana lowered to hiragana
(`LibraryFilters.kt:108` — `ch in 'ァ'..'ヶ' || ch in 'ヽ'..'ヾ' -> folded.append(ch - 0x60)`), prolonged
sound mark dropped, full-width Latin narrowed (`:91` — `ch in '！'..'～' -> ch - 0xFEE0`), lowercased.
Used by `matchRoutine` (`:139-150`) and `matchExercise` (`:164-168`).

**The bad one**, `app/src/main/java/io/eddiegulay/tempo/ui/SearchScreen.kt:88-92`:

```kotlin
val filtered = remember(query, apps) {
    val q = query.trim()
    if (q.isEmpty()) apps
    else apps.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
}
```

No folding at all. This is the **app drawer** — the launcher's primary surface.

Why this is a hazard for i18n rather than a pre-existing bug: `ignoreCase = true` on a bare
`String.contains` uses the **default locale**, and Kotlin's default-locale case folding is the source
of the Turkish dotless-i problem (`I`.lowercase() is `ı` under a Turkish locale, so searching `i`
stops matching `Instagram`). Adding a language toggle that changes the default locale activates this.

`foldKana`'s Latin handling also has a gap worth recording: it narrows full-width Latin and lowercases,
but does **no** Unicode normalisation or diacritic folding, so `café` will not be found by typing
`cafe`, and an NFD-decomposed `café` pasted from elsewhere will not match an NFC one.

`LibraryFilters.kt:138` also states: *"**Romaji is not supported** … `sukuwatto` will not find
スクワット."* Once the UI is English, a user typing `squat` will not find スクワット either — but see
H-12: the data to fix that already exists.

`KanaFoldingTest.kt` (184 lines) and `LibraryFiltersTest.kt` (299 lines) pin this behaviour.

---

#### H-12. `Exercise.nameEn` exists, is seeded, is queried — and is **never rendered**

`app/src/main/java/io/eddiegulay/tempo/gym/GymModels.kt:232` — `val nameEn: String,`
`app/src/main/java/io/eddiegulay/tempo/gym/data/BuiltInCatalog.kt:36` — `val nameEn: String,`
`app/src/main/java/io/eddiegulay/tempo/gym/data/Seeder.kt:80` — seeded into the DB
`app/src/main/java/io/eddiegulay/tempo/gym/data/GymStore.kt:957` — `nameEn = c.getString(2),`

Its **only** consumer is `LibraryFilters.kt:167`:
```kotlin
return foldKana(exercise.nameJa).contains(needle) || foldKana(exercise.nameEn).contains(needle)
```

Meanwhile `nameJa` is rendered at ~30 sites (`ExerciseIndexScreen.kt:187`, `StationPickerScreen.kt:547,605`,
`ExerciseDetailScreen.kt:323,637`, `WorkPage.kt:54`, `RestPage.kt:130`, `PausedPage.kt:115`,
`LibraryDetailScreen.kt:1003`, `BuilderScreen.kt:865`, `SessionDetailScreen.kt:128,375`,
`RecordSummary.kt:444`, `TrainingServiceMount.kt:61`, `SessionReplay.kt:145,225`, …).

**This is the single most encouraging finding in the survey.** The catalogue already carries an
English name for every seeded exercise; the migration is a selector, not a translation project — for
the catalogue. Note the field name hard-codes a two-language assumption (`nameJa`/`nameEn` rather than
a keyed table), so a third language means a schema migration.

---

#### H-13. Sorting is codepoint-ordered everywhere; there is no `Collator` in the codebase

Zero occurrences of `Collator` or `java.text.Collator` in `app/src/main/java`.

- `app/src/main/java/io/eddiegulay/tempo/data/AppRepository.kt:118` — `.sortedBy { it.label.lowercase() }`
  — the **app drawer's** order. Two problems on translation: `.lowercase()` is default-locale (see
  H-11's Turkish note), and codepoint order puts `Zebra` before `Ärger`, `École` after `Zoo`.
- `app/src/main/java/io/eddiegulay/tempo/ui/gym/ExerciseIndexScreen.kt:233` —
  `.sortedWith(compareBy({ it.pattern.ordinal }, { it.difficulty }, { it.nameJa }))` — sorts Japanese
  names by codepoint (i.e. by kanji block, which is arbitrary to a reader). `ExerciseIndexScreen.kt:216`
  acknowledges `nameJa` is only a tie-break. If the list switches to `nameEn` the *sections* stay put
  but the within-section order changes — which is fine, but it is a behaviour change, and its test
  will need updating.
- `app/src/main/java/io/eddiegulay/tempo/gym/data/ExerciseCatalogSource.kt:56` —
  `.sortedWith(compareBy({ it.difficulty }, { it.nameJa }))` — same.
- `app/src/main/java/io/eddiegulay/tempo/gym/LibraryFilters.kt:280` — `.thenBy { it.name }`, the third
  tie-break of three in よく使う. `LibraryFilters.kt:263-265` already calls this "at least an order",
  so this is untidy rather than broken.

Every other `sortedBy` in the app is over a numeric or timestamp key and is locale-independent
(`HistoryPaging.kt:101,171`, `CueSchedule.kt:166,190,197`, `TimelineCompiler.kt:60,86`,
`Timeline.kt:368`, `GymStore.kt:275,1475,1517,1976`, `RecordsPrScreen.kt:157,252`,
`TempoNotificationListener.kt:83`, `InkDensity.kt:223`, `ExerciseDb.kt:206`, `BuilderScreen.kt:605`,
`CalendarRepository.kt:247`). No action needed there.

---

#### H-14. Fixed-cell layouts sized for single-character labels

**The records ink-density grid.**
`app/src/main/java/io/eddiegulay/tempo/ui/gym/RecordsIndexScreen.kt:254`

```kotlin
val RECORDS_WEEKDAYS: List<String> = listOf("日", "月", "火", "水", "木", "金", "土")
```

`RecordsIndexScreen.kt:325` — `private val GRID_CELL = 20.dp`
`RecordsIndexScreen.kt:720-733` — each letter is centred in a `Box(Modifier.width(GRID_CELL))` at
`fontSize = 10.sp, letterSpacing = 1.sp`, with **no `maxLines` and no `overflow`**.
`RecordsIndexScreen.kt:741` — the canvas beneath is `.width(GRID_CELL * 7)` = 140.dp.

`M T W T F S S` fits; `Mon Tue Wed…` does not. 20.dp at 10.sp holds roughly 3–4 Latin characters. The
canvas width is derived from the same constant, so widening the cells widens the whole calendar. This
is a genuine layout decision (single-letter English weekday initials, which collide: T/T and S/S).

`RecordsIndexScreen.kt:249-253` notes `RecordsIndexScreenTest` **pins these seven letters against
`JapaneseDate.dayOfWeek`** so the two tables cannot drift — that assertion breaks on migration by
construction.

**The 静 seal.** `app/src/main/java/io/eddiegulay/tempo/ui/HomeScreen.kt:316-336` — a single glyph at
`fontSize = 30.sp` centred in a `Modifier.size(50.dp)` rotated bordered square. This is a *hanko*, a
Japanese seal; it is arguably a logo rather than copy and may simply stay. Flag for a decision — but
if anyone tries to put a word in it, the box is 50.dp.

**The PR-screen tab underline.** `app/src/main/java/io/eddiegulay/tempo/ui/gym/RecordsPrScreen.kt:420-426`

```kotlin
Box(
    Modifier
        .width(if (isSelected) 44.dp else 0.dp)
        .height(1.dp)
```

A **hard-coded 44.dp** underline under a `fontSize = 14.sp, letterSpacing = 2.sp` tab label
(`:408-417`). 44.dp is two Mincho glyphs plus tracking. Under an English tab word the rule will be
conspicuously shorter than the word it underlines. Should be `Modifier.fillMaxWidth()` inside a
label-width `Column`, or measured.

---

#### H-15. `maxLines = 1` with no `overflow` — 10 sites

34 `maxLines = 1` sites total; 24 pair with `TextOverflow.Ellipsis`; **10 do not**. Of those, five are
hero numerals with a deliberate `softWrap = false` and a width-derived cap (`PreparePage.kt:78-79`,
`WorkPage.kt:77-78`, `RepsPage.kt:123-124`, `RestPage.kt:108-109`, `PausedPage.kt:102-103`,
`RecordSummary.kt:671-672`) and are intentional. The remainder are real, and all sit on strings that
grow under translation:

- `app/src/main/java/io/eddiegulay/tempo/ui/gym/session/WorkPage.kt:124-129` — `ExerciseHeading`, the
  exercise name at 24.sp, `textAlign = TextAlign.Center, maxLines = 1` with no overflow. An exercise
  name is the longest string on the player. Silently clipped rather than ellipsised.
- `app/src/main/java/io/eddiegulay/tempo/ui/gym/session/PausedPage.kt:115-120` — the same name at
  13.sp, `letterSpacing = 2.sp`, `maxLines = 1`, no overflow.
- `app/src/main/java/io/eddiegulay/tempo/ui/gym/RecordsIndexScreen.kt:1013-1016` — `copy.partial`
  ("途中まで…") at `fontSize = 11.sp, letterSpacing = 3.sp`, `maxLines = 1`, no overflow, in a `Row`
  that has already given `weight(1f)` to the routine name (`:1000`). This one will clip first.
- `app/src/main/java/io/eddiegulay/tempo/ui/Tategaki.kt:63-66` — the upright cell, which is one
  character by construction. Not a hazard.

---

### S3 — Untidy. Correct output, wrong identifiers or a stale rationale.

---

#### H-16. A `DateTimeFormatter` pattern with Japanese literals baked into it

`app/src/main/java/io/eddiegulay/tempo/ui/SearchScreen.kt:62-63`

```kotlin
/** Japanese month/day for the app subtitle, e.g. "6月10日". */
private val updatedFormatter = DateTimeFormatter.ofPattern("M月d日")
```

The **only** `DateTimeFormatter`/`SimpleDateFormat`/`ofPattern` in the entire codebase — everything
else goes through `JapaneseDate`. Two problems: the pattern embeds Japanese literals, and
`ofPattern(String)` with no `Locale` argument resolves against `Locale.getDefault(FORMAT)`, so its
behaviour already varies by device. Note this one uses **arabic** digits where `JapaneseDate.monthDay`
uses kanji, so the launcher already has two spellings of a month-day.

#### H-17. Japanese vocabulary in non-string identifiers

These do not render, but they encode the assumption and will read as wrong after migration:

- `SpeechAvailability.NoJapaneseVoice` (`GymPreferences.kt:22`) — see H-3.
- `relativeDayJa`, `formatElapsedJa` (`GymHomeCopy.kt:208,220`).
- `durationKanji`, `durationKanjiFromMs`, `kanji`, `kanjiExtended` (`Numerals.kt`, `JapaneseDate.kt`).
- The object name `JapaneseDate` itself, imported in 24 files.
- `Exercise.nameJa` / `nameEn` (`GymModels.kt:232`) — a two-language schema.
- `foldKana` (`LibraryFilters.kt:71`).

#### H-18. `DurationBucket` labels are kanji numerals inside enum constants

`app/src/main/java/io/eddiegulay/tempo/gym/LibraryFilters.kt:180-184`

```kotlin
enum class DurationBucket(val label: String, val upperBoundSeconds: Int) {
    UNDER_FIVE("〜五分", 5 * 60),
    FIVE_TO_FIFTEEN("五〜十五分", 15 * 60),
    OVER_FIFTEEN("十五分〜", Int.MAX_VALUE),
}
```

The `〜` is a **wave dash (U+301C)**, a CJK range marker, and it appears leading, medial and trailing.
English wants `Under 5 min` / `5–15 min` / `15 min+` — different words in each position, not one
symbol. A label baked into an `enum` constructor also cannot be re-resolved when the language changes
at runtime; it is fixed at class-init.

#### H-19. `uniqueName` composes a copy-suffix with a kanji counter

`app/src/main/java/io/eddiegulay/tempo/gym/LibraryFilters.kt:307-316`

```kotlin
fun uniqueName(base: String, existing: Set<String>): String {
    val first = "$base の写し"
    if (first !in existing) return first
    for (n in 2..existing.size + 2) {
        val candidate = first + JapaneseDate.kanjiExtended(n)
```

`七分間 の写し` → `七分間 の写し二` → `七分間 の写し三`. Three things do not port: the ` の写し` suffix
(a postposition, so English wants `Copy of X` — prefix), the counter starting at 二 rather than 一
(`LibraryFilters.kt:301-302`), and the deliberate space before の (`:296-298` insists it is not a typo).

**And this one is persisted.** The name it returns is written to the routines table, so names created
under one language survive a language switch and will read as a mix. That is correct behaviour — a
user's routine name is their data — but it should be a stated decision, not an accident.

#### H-20. `AppGlyph`'s label matcher and monogram

`app/src/main/java/io/eddiegulay/tempo/ui/AppGlyph.kt:275-347` — `LABEL_KEYWORDS` already mixes
Japanese and English and matches on `label.lowercase()` (`:97-98`). It is 21 Japanese keys +
~45 English keys. This one is **already multilingual and needs no work** — but note `.lowercase()`
is default-locale, same Turkish caveat as H-11 and H-13, and every non-EN/JA locale gets no label-tier
match at all (falling through to `CATEGORY_GLYPHS` and then the monogram).

`app/src/main/java/io/eddiegulay/tempo/ui/AppGlyph.kt:126`

```kotlin
val ch = remember(label) { label.trim().firstOrNull()?.toString() ?: "・" }
```

The monogram fallback takes the **first `Char`** — not the first grapheme cluster. It renders at
`(size.value * 0.5f).sp` in a `Modifier.size(size)` (26.dp default) square, which is a design tuned to
one square CJK glyph. A Latin `S` at 13.sp in a 26.dp box is a small letter floating in a large tile
rather than a filled monogram. It works; it looks thin. Also `firstOrNull()` on a surrogate pair
(an emoji app name) yields half a codepoint.

#### H-21. Sunday-first weekday indexing is a design-doc convention, not a locale one

`app/src/main/java/io/eddiegulay/tempo/data/JapaneseDate.kt:17-18`

```kotlin
// Sunday-first, matching the prototype's JS `getDay()` indexing.
private val DOW = charArrayOf('日', '月', '火', '水', '木', '金', '土')
```

`JapaneseDate.kt:98-100` — `val idx = now.dayOfWeek.value % 7` (java.time MONDAY=1..SUNDAY=7, mod 7
maps SUNDAY→0).

Replicated in `app/src/main/java/io/eddiegulay/tempo/gym/InkDensity.kt:50,67` (which cites
`JapaneseDate.kt:18` by line number) and consumed by `RECORDS_WEEKDAYS` (H-14) and `monthCells`'
`leadingBlank`. Sunday-first is correct for Japan and the US, and wrong for most of Europe (ISO
Monday-first). Whether the ink grid should follow the locale is a product decision; recording it here
because the constant is duplicated in two files and one of them references the other by line number.

#### H-22. `Numerals.clockDuration` is the only formatter that survives translation unchanged

`app/src/main/java/io/eddiegulay/tempo/gym/Numerals.kt:112-115`

```kotlin
fun clockDuration(millis: Long): String {
    val totalSeconds = if (millis <= 0L) 0L else millis / 1000L
    return "${totalSeconds / 60}:%02d".format(totalSeconds % 60)
}
```

Noting it as a positive finding: pure `m:ss`, no locale, no Japanese, minutes uncapped at 59 by
design. Same for `JapaneseDate.time` (`:75`) and `JapaneseDate.clock` (`:118`), both `"%02d:%02d"`.
Caveat: `String.format` without a `Locale` uses the default locale, so under `ar`/`fa` these would
emit Eastern Arabic numerals. Not in scope for the languages currently contemplated, but worth one
line in the formatter layer.

---

### Confirmed absences — categories that turned out empty

These were searched for and are **not** present. A confirmed absence is a real finding.

- **`ui/MinuteClock.kt` is not a hazard.** Despite the name it contains no text, no formatting and no
  layout — only `rememberMinuteTime()` and `rememberSecondTime()`, two lifecycle-aware coroutine loops
  returning `State<LocalDateTime>`. Fully language-neutral.
- **`ui/FlipClock.kt` is not a per-glyph hazard in practice.** It does iterate per character
  (`FlipClock.kt:56` — `text.forEach { ch -> ... }`) and does put each in a fixed
  `Modifier.size(cardWidth, cardHeight)` cell (`:86`), which is textbook fixed-cell rendering. But its
  only two callers feed it pure digits and colons: `FocusScreen.kt:149-152`
  (`"%02d:%02d:%02d".format(...)`) and `FocusScreen.kt:182-183` (`"%02d:%02d".format(minutes, seconds)`).
  Content does not change with language. It is a latent hazard only if someone ever passes it a word.
- **No `String.length` drives a truncation or a branch.** Only two `.length` sites touch user-visible
  strings: `RecordSummary.kt:666` (H-7, sizing) and `Tategaki.kt:119,131,137` (segmentation, correct
  by design). `RecordsIndexScreen.kt:751` and `InkDensity.kt:101` read `grid.length` — a `MonthGrid`
  property (days in the month), **not** a string. `LibraryFilters.kt:72,100,118,120` are
  `StringBuilder` capacity and indices inside `foldKana`, internal to the fold.
- **No `.substring`, `.take(n)`, `padStart` or `padEnd` on user-visible text anywhere.** The single
  `substring` (`Tategaki.kt:132`) is run segmentation. Nothing truncates copy by character count.
- **No `Locale` plumbing of any kind.** Five `Locale` references total in `app/src/main/java`, all
  five in the two TTS files (H-3). No `Locale.getDefault()`, no `LocaleList`, no `Configuration`
  reads, no `LocalConfiguration` locale checks, no `Locale`-aware `String.format` overloads.
- **No `Collator`, no `java.text.Collator`, no ICU import.** Zero occurrences.
- **No RTL work.** No `LayoutDirection`, no `Rtl`, no `start`/`end` vs `left`/`right` audit needed —
  but also no evidence anyone has considered it. Out of scope for EN/JA; would be S1 for AR/HE.
- **No pluralisation machinery.** No `plurals` resource, no `MessageFormat`, no ICU plural selection.
  `res/values/strings.xml` has exactly one entry. Every count-bearing string in H-4 needs one.
- **`res/font/` has no `font-family` XML** and no weight/style variants beyond the four TTFs —
  `Type.kt` builds `FontFamily` in Kotlin directly, so a locale-conditional family swap is a
  one-file change once the assets exist.

---

### Tests that break on migration

The contract counts these as a hazard. Measured:

- **77 test files** under `app/src/test` + `app/src/androidTest` contain Japanese literals.
- **1593 lines** across those files contain Japanese literals.

The five that pin the hazards in this fragment directly:

| file | lines | pins |
|---|---|---|
| `app/src/test/java/io/eddiegulay/tempo/data/JapaneseDateTest.kt` | 131 | H-4, H-5 |
| `app/src/test/java/io/eddiegulay/tempo/gym/NumeralsTest.kt` | 102 | H-4 |
| `app/src/test/java/io/eddiegulay/tempo/ui/TategakiTest.kt` | 88 | H-2 |
| `app/src/test/java/io/eddiegulay/tempo/gym/KanaFoldingTest.kt` | 184 | H-11 |
| `app/src/test/java/io/eddiegulay/tempo/gym/LibraryFiltersTest.kt` | 299 | H-11, H-18, H-19 |

Plus `RecordsIndexScreenTest`, which `RecordsIndexScreen.kt:249-253` says *"pins them against
`JapaneseDate.dayOfWeek` so the two tables cannot drift apart"* — an assertion that is guaranteed to
fail once weekday labels are localised (H-14, H-21).

---

## Non-visible Japanese

Literals that are Japanese but that a user never reads as copy. Listed with the reason they stay.

| where | literal | reason it stays |
|---|---|---|
| `ui/AppGlyph.kt:277-297` | `"電話"`, `"連絡先"`, `"メッセージ"`, `"カメラ"`, `"写真"`, `"音楽"`, `"天気"`, `"地図"`, `"時計"`, `"電卓"`, `"設定"`, `"翻訳"`, `"銀行"`, `"財布"`, `"地下鉄"`, `"電車"`, `"ニュース"`, `"読書"`, `"ゲーム"` | **Match keys, not copy.** They are matched against *other apps'* display names, which stay Japanese on a Japanese device regardless of Tempo's UI language. Removing them would break icon resolution for Japanese-named apps. The English keys at `:299-347` sit beside them and both tiers should stay. |
| `gym/LibraryFilters.kt:23-32` | `HALF_WIDTH_KANA`, `FULL_WIDTH_KANA`, `DAKUTEN_BASE`, `DAKUTEN_VOICED`, `HANDAKUTEN_BASE`, `HANDAKUTEN_VOICED` | **Unicode conversion tables.** Data, not copy. They must stay for as long as any user can type Japanese into the search box — which is forever, since routine names are user data. |
| `gym/LibraryFilters.kt:34-39` | `'ﾞ'`, `'ﾟ'`, `'゙'`, `'゚'`, `'ー'`, `'　'` | Codepoint constants for the same fold. |
| `data/JapaneseDate.kt:15` | `charArrayOf('〇','一','二','三','四','五','六','七','八','九')` | Numeral table. Stays if any surface keeps kanji numerals; deleted with `JapaneseDate` otherwise. Data either way. |
| `data/JapaneseDate.kt:18` | `charArrayOf('日','月','火','水','木','金','土')` | Weekday table. Same. |
| `gym/Numerals.kt:22` | `private const val NO_VALUE = "—"` | An **em dash**, not Japanese. Language-neutral placeholder; listed only because it reads as a Japanese-design token and someone will try to translate it. Do not. |
| `ui/AppGlyph.kt:126` | `"・"` | Katakana middle dot, the monogram fallback when a label is empty. A **typographic** fallback, not a word — but it is CJK and should become `·` or a drawn glyph in Latin locales. Borderline: see H-20. |
| separator throughout | `" ・ "` | The CJK middle dot used as a field separator in ~20 composed strings (`CalendarScreen.kt:110`, `RecordsIndexScreen.kt:244`, `SessionDetailScreen.kt:163`, `PlayerCopy.kt:315,369`, `RecordCopy.kt:285`, `InkDensity.kt:197`, `GymHomeCopy.kt`, `ChartGeometry.kt:318,325`, `LibraryIndexScreen.kt:228`, …). Not translatable *content*, but it is a punctuation choice that Latin typography spells `·` or ` — ` or `, `. It belongs in the formatter layer as a locale-dependent separator constant, not in 20 string concatenations. |
| `gym/LibraryFilters.kt:181-183` | `"〜"` (U+301C wave dash) | Range marker inside `DurationBucket`. Punctuation, but locale-dependent — see H-18. |

---

## What I would do first

1. **H-3 (TTS locale)** — smallest change, largest silent failure. Parameterise the probe language and
   rename `NoJapaneseVoice` → `NoVoiceForLanguage`.
2. **H-1 / H-2 (Home's vertical corner)** — this is the one screen that is *unusable*, not merely
   ugly, in Latin. It needs a horizontal variant, which is a design decision someone has to make
   before any code is written.
3. **H-4 / H-5 (formatter layer)** — ~215 call sites across 24 files. This is the bulk of the work and
   it wants a real `fmt.*` layer with plural support behind it, per the contract's `fmt.*` root. Note
   `clockDuration` is the one thing that already ports.
4. **H-12 (`nameEn`)** — free win. The catalogue is already bilingual; wire the selector.
5. **H-9 / H-10 (type)** — a locale-conditional type scale in `Type.kt` plus a Latin-covering font.
   Nothing else in the app touches typography, so this is genuinely one file plus assets.
