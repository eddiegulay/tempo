# Platform & data layer

**Files surveyed:** 20   **User-visible literals:** 26 occurrences / 21 distinct   **Non-visible JP literals:** 0

Surveyed: `data/AppRepository.kt`, `data/BlockadeRepository.kt`, `data/JapaneseDate.kt`,
`data/TempoFault.kt`, `data/ThemeRepository.kt`, `calendar/CalendarModels.kt`,
`calendar/CalendarOutcome.kt`, `calendar/CalendarPermission.kt`, `calendar/CalendarRepository.kt`,
`notification/NotificationGrouping.kt`, `notification/NotificationRepository.kt`,
`notification/NotificationStore.kt`, `notification/TempoNotificationListener.kt`, `MainActivity.kt`,
`LauncherViewModel.kt`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/**` (whole tree),
`app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`.

Read beyond scope (read-only, for call sites and hazards): every `ui/` and `gym/` file that calls
`JapaneseDate`, `app/src/test/java/io/eddiegulay/tempo/data/JapaneseDateTest.kt`,
`gym/GymPreferencesRepository.kt`, `ui/EventComposeScreen.kt`, `ui/HomeScreen.kt`.

---

## The three answers asked for, up front

### 1. Locale plumbing today: none. Confirmed, not assumed.

Exhaustive search over `*.kt` / `*.kts` / `*.xml` / `*.toml` / `*.properties` (build dirs excluded)
for `resConfig`, `localeConfig`, `locales_config`, `AppCompatDelegate`, `LocaleManager`, `LocaleList`,
`setApplicationLocales`, `androidResources`, `generateLocaleConfig`, `Locale`. The complete set of hits:

```
app/src/main/java/io/eddiegulay/tempo/data/AppRepository.kt:77:
    // Labels are locale-sensitive; LauncherApps has no locale callback, so reload on locale change.
app/src/main/java/io/eddiegulay/tempo/gym/cue/GymSpeech.kt:11:   import java.util.Locale
app/src/main/java/io/eddiegulay/tempo/gym/cue/GymSpeech.kt:107:  val result = runCatching { engine.setLanguage(Locale.JAPANESE) }
app/src/main/java/io/eddiegulay/tempo/gym/data/GymMath.kt:64:    * ...rather than a locale's first-day-of-week... (prose)
app/src/main/java/io/eddiegulay/tempo/ui/gym/GymSettingsScreen.kt:70:   import java.util.Locale
app/src/main/java/io/eddiegulay/tempo/ui/gym/GymSettingsScreen.kt:775:  val result = runCatching { engine.setLanguage(Locale.JAPANESE) }
app/src/test/java/io/eddiegulay/tempo/gym/data/GymMathTest.kt:76:  (prose)
```

So, precisely:

- **No `resConfigs` / `resourceConfigurations`** anywhere. `app/build.gradle.kts` has no
  `androidResources { }` block at all; `defaultConfig` contains only `applicationId`, `minSdk`,
  `targetSdk`, `versionCode`, `versionName`, `testInstrumentationRunner` and an `ndk { abiFilters }`.
- **No `android:localeConfig`** on `<application>`. The manifest's `<application>` attributes are
  exactly: `allowBackup`, `dataExtractionRules`, `fullBackupContent`, `icon`, `label`, `roundIcon`,
  `supportsRtl="true"`, `enableOnBackInvokedCallback`, `theme`.
- **No `res/xml/locales_config.xml`.** `res/xml/` contains exactly two files: `backup_rules.xml` and
  `data_extraction_rules.xml`.
- **No per-app language support** — no `AppCompatDelegate.setApplicationLocales`, no `LocaleManager`,
  no `LocaleListCompat`. `appcompat` is not even a dependency (the app is pure Compose on
  `ComponentActivity`, parent theme `@android:style/Theme.Material.Light.NoActionBar`).
- **No localised resource folders.** `res/` has `values` and `values-night` only — no `values-ja`,
  no `values-en`. Both `values*` folders hold `colors.xml` + `themes.xml`; `values/strings.xml`
  additionally exists and is, in full:

  ```xml
  <resources>
      <string name="app_name">Tempo</string>
  </resources>
  ```

  That single `app_name` is referenced twice in the manifest (`android:label` on `<application>` and
  on the `TempoNotificationListener` service). Nothing in Kotlin reads `R.string`.
- The only locale *awareness* in the whole app is `AppRepository`'s `ACTION_LOCALE_CHANGED` receiver
  (it reloads app labels from the system, which is orthogonal to Tempo's own copy), and the two
  hard-coded `Locale.JAPANESE` calls in the gym's TTS engine (`gym/` scope — not mine, flagged here
  only because it is the sole place a language constant is written down anywhere in the app).

`res/font/` holds four Japanese typefaces, all referenced from `ui/theme/Type.kt`:
`shippori_mincho_regular.ttf`, `shippori_mincho_medium.ttf` (Mincho — the display face),
`zen_kaku_gothic_new_light.ttf`, `zen_kaku_gothic_new_regular.ttf` (Gothic — the text face). Both
families are CJK-first designs; see Hazards §H6.

### 2. `ThemeRepository` — the first-frame settings mechanism, in full

This is the template a language preference must follow, so here is the whole shape.

**The store.** One process-wide `Preferences` DataStore, declared as a *file-private* Context
extension:

```kotlin
private const val LEGACY_PREFS = "tempo"

private val Context.tempoDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tempo_settings",
    produceMigrations = { ctx -> listOf(SharedPreferencesMigration(ctx, LEGACY_PREFS)) },
)
```

On disk that is `filesDir/datastore/tempo_settings.preferences_pb`.

**The legacy SharedPreferences migration.** `produceMigrations` runs a stock
`SharedPreferencesMigration(ctx, "tempo")` the first time the DataStore is read. The pre-DataStore
build kept its settings in a SharedPreferences file named `tempo` with a key `"theme"` whose value was
a String. Because the DataStore key is *also* `stringPreferencesKey("theme")` with the same string
values, the default (key-set-unspecified) migration copies every key across verbatim, then deletes
the old SharedPreferences file. No key mapping, no value translation, no version stamp — the
migration works purely because the names and types were kept identical.

**A second, in-value legacy migration** rides on top of it. The dark theme used to be stored as
`"amoled"` and is now `"sumi"`; rather than rewriting stored rows, the *reader* accepts both:

```kotlin
private fun String?.toTheme(): TempoTheme =
    if (this == VALUE_SUMI || this == VALUE_LEGACY_DARK) TempoTheme.Sumi else TempoTheme.Paper

private companion object {
    const val VALUE_PAPER = "paper"
    const val VALUE_SUMI = "sumi"
    const val VALUE_LEGACY_DARK = "amoled"
}
```

`setTheme` only ever writes `"paper"` / `"sumi"`; `"amoled"` is read-only forever. Note the shape:
**unknown / absent value falls through to the safe default** rather than throwing.

**The keys and flows.**

```kotlin
private val themeKey = stringPreferencesKey("theme")
private val onboardingKey = booleanPreferencesKey("onboarding_complete")

val theme: Flow<TempoTheme> = context.tempoDataStore.data.map { prefs -> prefs[themeKey].toTheme() }
val onboardingComplete: Flow<Boolean> = context.tempoDataStore.data.map { prefs -> prefs[onboardingKey] ?: false }

suspend fun setTheme(theme: TempoTheme)          // edit { prefs[themeKey] = "paper" | "sumi" }
suspend fun setOnboardingComplete()              // edit { prefs[onboardingKey] = true }
```

**The synchronous first-frame read.** One data class and one blocking function:

```kotlin
data class InitialSettings(val theme: TempoTheme, val onboardingComplete: Boolean)

fun loadInitialSettings(): InitialSettings = runBlocking {
    val prefs = context.tempoDataStore.data.first()
    InitialSettings(
        theme = prefs[themeKey].toTheme(),
        onboardingComplete = prefs[onboardingKey] ?: false,
    )
}
```

`runBlocking` + `.first()` on the main thread, once, at cold start. Its own KDoc states the reason:
the window background and the initial theme/onboarding state must be correct *before* Compose draws,
or a returning user sees a flash of the wrong theme or a blank Home, which reads as the app forgetting
their choices.

**Where it is called, and in what order** — this is the part a language preference has to slot into:

1. `LauncherViewModelFactory.create()` builds `ThemeRepository(appContext)`
   (`LauncherViewModel.kt:523`).
2. `LauncherViewModel`'s **constructor body**, line 72, first statement:
   `private val initialSettings = themeRepository.loadInitialSettings()`.
3. Lines 74–82 seed both StateFlows from it, so the flows are *already* correct at frame zero and the
   live `Flow`s take over afterwards:
   ```kotlin
   val theme: StateFlow<TempoTheme> = themeRepository.theme
       .stateIn(viewModelScope, SharingStarted.Eagerly, initialSettings.theme)
   val onboardingComplete: StateFlow<Boolean> = themeRepository.onboardingComplete
       .stateIn(viewModelScope, SharingStarted.Eagerly, initialSettings.onboardingComplete)
   ```
   Note `SharingStarted.Eagerly`, not `WhileSubscribed` — the rest of the ViewModel's flows use
   `WhileSubscribed(5_000)`; these two do not, because they must never drop back to a default.
4. `MainActivity.onCreate` (`MainActivity.kt:54–55`) reads `viewModel.theme.value` **before**
   `setContent {}` and paints the raw window with a `ColorDrawable`, because the XML
   `windowBackground` only tracks the *system* day/night setting:
   ```kotlin
   val isDark = viewModel.theme.value == TempoTheme.Sumi
   window.setBackgroundDrawable(ColorDrawable(if (isDark) WINDOW_SUMI else WINDOW_PAPER))
   ```
   (`WINDOW_PAPER = 0xFFF2EEE4`, `WINDOW_SUMI = 0xFF1A1814`, mirroring `PaperColors.bgSolid` /
   `SumiColors.bgSolid`.)
5. `ui/TempoApp.kt:135` comments that the world-owning state is "Seeded synchronously from DataStore,
   so this is already correct on" the first frame.

**Backup.** `tempo_settings` is *explicitly named* in both backup rule files, so a stored preference
survives reinstall/restore:

```xml
<include domain="file" path="datastore/tempo_settings.preferences_pb"/>
```

in `res/xml/backup_rules.xml` (`<full-backup-content>`, API < 31) and `res/xml/data_extraction_rules.xml`
(`<cloud-backup>`, API 31+). Both are **include-lists**: anything not named is not restored. A language
key added to `tempo_settings` is covered automatically; a language key put in a *new* DataStore file
would silently fail to restore unless both files gain a line.

**The one-owner constraint** (documented at `gym/GymPreferencesRepository.kt:19-33`, and the reason the
gym has its own `tempo_gym` file): `preferencesDataStore` permits exactly one owner per file per
process, and declaring a second `preferencesDataStore(name = "tempo_settings")` anywhere else throws
*"There are multiple DataStores active for the same file"* on first read. Since `ThemeRepository` is
read on every cold start, that throw would be unconditional. **A language key therefore has to go
through `ThemeRepository` itself** (or the `private` delegate has to be widened to `internal` — the
gym file notes that exact one-word change as the merge path), not through a new repository of its own.

**Verdict on shape:** a language preference is a third key on the same store —
`stringPreferencesKey("language")`, a `Flow<TempoLanguage>`, a `suspend fun setLanguage`, a third field
on `InitialSettings`, a third `stateIn(..., Eagerly, initialSettings.language)` in `LauncherViewModel`,
and a tolerant `String?.toLanguage()` that falls through to a default on any unrecognised value. Nothing
new is needed on the backup or migration side.

### 3. Persisted / keyed / matched Japanese in this scope

**Nothing in this scope persists, keys on, or matches against a Japanese string — with one exception,
which is real and is the most important finding in this fragment.**

The clean cases, stated explicitly so the merge does not have to re-derive them:

| where | what is stored/keyed | Japanese? |
|---|---|---|
| `ThemeRepository` DataStore `tempo_settings` | keys `"theme"`, `"onboarding_complete"`; values `"paper"` / `"sumi"` / `"amoled"` / boolean | **No** — ASCII only. Safe to translate the theme's *labels* (they live in `ui/`) without touching storage. |
| `BlockadeRepository` `filesDir/blockade.json` | JSON keys `"blocks"`, `"lastSeen"`; map keys are package names; values are epoch millis | **No** |
| `AppInfo.key` (`AppRepository.kt:167-170`) | `componentName.flattenToShortString() + "#" + userSerial` | **No** |
| `CalendarEvent.key` (`CalendarModels.kt:40`) | `"$eventId:$begin"` | **No** |
| `NotificationGrouping.groupByApp` | buckets keyed on `packageName` | **No** — grouping is by package, and `appLabel` is only carried along for display. |
| `TempoNotification.key` | the platform's own `sbn.key` | **No** |
| `TempoFault` / `GymFault` / `CalendarFault` / `Loadable` / `WriteOutcome` / `PendingWrite` | Kotlin `data object` / `data class` identities; no `name`, `ordinal`, or string tag is ever stored | **No** — these are pure in-memory types. Renaming or translating fault *copy* cannot orphan anything. |

**The exception — `（無題）` round-trips into the user's real calendar.**

`CalendarRepository.kt:185` substitutes a display placeholder for a blank provider title:

```kotlin
title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "（無題）",
```

That value lands in `CalendarEvent.title`. `ui/EventComposeScreen.kt:100` prefills the editable title
field from it (`var title by remember(editing) { mutableStateOf(editing?.title.orEmpty()) }`), line 178
puts `title.trim()` into the `EventDraft`, and `CalendarRepository.toValues` writes it to
`CalendarContract.Events.TITLE` (line 334). So a user who opens an untitled event and saves any other
change **writes the literal `（無題）` into the calendar provider** — which the sync adapter then pushes
to Google, to every other device they own, and to any guests on the invite. It is a display default
that has an escape route into shared, remote, non-Tempo storage. Full detail in Hazards §H1.

---

## app/src/main/java/io/eddiegulay/tempo/data/JapaneseDate.kt

Purpose: the app's single date/time/numeral formatter. Not a string table — logic that *produces*
Japanese dates, weekdays, kanji numerals and relative-day words. Its output appears on Home, Calendar,
Notifications and across the whole of 鍛錬. It is the highest-traffic Japanese producer in the codebase.

### Complete public API

`object JapaneseDate` — 11 public functions, 2 private members.

| # | signature | returns | example |
|---|---|---|---|
| 1 | `fun kanji(n: Int): String` | kanji numeral, **0..99 only** | `0`→`〇`, `7`→`七`, `10`→`十`, `11`→`十一`, `20`→`二十`, `42`→`四十二`, `99`→`九十九` |
| 2 | `fun kanjiExtended(n: Int): String` | kanji numeral 100..9999; delegates `<100` to `kanji`; **arabic fallback outside `0..9999`** | `100`→`百`, `1000`→`千`, `123`→`百二十三`, `9999`→`九千九百九十九`, `10000`→`"10000"`, `-42`→`"-42"` |
| 3 | `fun time(now: LocalDateTime): String` | zero-padded 24h digits, `"%02d:%02d"` | `09:05` |
| 4 | `fun reading(now: LocalDateTime): String` | spoken-style 12h reading, meridiem + kanji hour + optional kanji minute | `午前九時五分`, `午後一時三十分`, `午前十二時` (minutes elided on the hour) |
| 5 | `fun era(now: LocalDateTime): String` | Reiwa era year, `令和 + kanji(year - 2018) + 年` | 2026→`令和八年`, 2019→`令和一年` |
| 6 | `fun monthDay(now: LocalDateTime): String` | `kanji(month) + 月 + kanji(day) + 日` | `六月十七日` |
| 7 | `fun dayOfWeek(now: LocalDateTime): String` | `dowChar + 曜日` | `水曜日`, `日曜日` |
| 8 | `fun eventTime(at: LocalDateTime): String` | **24-hour** kanji clock, minutes elided on the hour | `十九時三十分`, `十九時` |
| 9 | `fun clock(at: LocalDateTime): String` | plain digits, `"%02d:%02d"` | `09:30` |
| 10 | `fun dayToken(date: LocalDate, today: LocalDate): String` | collapsing relative day — a **4-branch cascade** | `今日` / `明日` / `水曜日` (within 7 days) / `六月十九日` (beyond) |
| 11 | `fun dayHeading(date: LocalDate, today: LocalDate): String` | fuller day-group header — a **3-branch cascade** | `今日` / `明日` / `水曜日 ・ 六月十九日` |

Private: `private val K = charArrayOf('〇','一','二','三','四','五','六','七','八','九')`;
`private val DOW = charArrayOf('日','月','火','水','木','金','土')` (Sunday-first, matching the design
prototype's JS `getDay()` indexing); `private fun dowChar(now: LocalDateTime): String` which maps
`java.time`'s MONDAY=1..SUNDAY=7 to that table via `dayOfWeek.value % 7`.

Note the deliberate split between #8 `eventTime` (kanji, 24h — Home's corner, "the kanji artefact")
and #9 `clock` (digits — the Calendar page, "a tool, not an artefact"); and between #4 `reading`
(12h with meridiem, for the clock's spoken line) and #8 (24h, no meridiem). Three different clock
renderings coexist on purpose.

### Literals it emits

| key | ja | context | notes |
|---|---|---|---|
| `fmt.numeral.digits` | `〇一二三四五六七八九` | `K`, line 15 | the digit table; feeds every numeral below |
| `fmt.numeral.ten` | `十` | `kanji`, lines 23, 26 | tens marker |
| `fmt.numeral.hundred` | `百` | `kanjiExtended`, line 66 | `1` elided: `百` never `一百` |
| `fmt.numeral.thousand` | `千` | `kanjiExtended`, line 61 | `1` elided: `千` never `一千` |
| `fmt.date.weekdays` | `日月火水木金土` | `DOW`, line 18 | Sunday-first table |
| `fmt.date.weekday.suffix` | `曜日` | `dayOfWeek`, line 95 | appended to the table char |
| `fmt.time.am` | `午前` | `reading`, line 82 | |
| `fmt.time.pm` | `午後` | `reading`, line 82 | |
| `fmt.time.hour` | `時` | `reading` line 84, `eventTime` line 114 | two call sites, same literal |
| `fmt.time.minute` | `分` | `reading` line 83, `eventTime` line 113 | two call sites; omitted entirely when minute == 0 |
| `fmt.date.era.reiwa` | `令和` | `era`, line 88 | |
| `fmt.date.year.suffix` | `年` | `era`, line 88 | |
| `fmt.date.month.suffix` | `月` | `monthDay`, line 92 | |
| `fmt.date.day.suffix` | `日` | `monthDay`, line 92 | |
| `fmt.date.today` | `今日` | `dayToken` line 125, `dayHeading` line 133 | |
| `fmt.date.tomorrow` | `明日` | `dayToken` line 126, `dayHeading` line 134 | |
| `fmt.sep.middot` | ` ・ ` | `dayHeading`, line 135 | space-middot-space, exactly |

21 literal occurrences, 17 distinct.

### Every call site, whole app

`time` (1): `ui/HomeScreen.kt:129` — the 104sp clock.

`reading` (1): `ui/HomeScreen.kt:141` — the spoken line under the clock.

`era` (9): `ui/CalendarScreen.kt:110`, `ui/HomeScreen.kt:187` (accessibility description),
`ui/HomeScreen.kt:295` (**vertical/tategaki line**), `ui/NotificationsScreen.kt:124`,
`ui/gym/GymHomeScreen.kt:161`, `ui/gym/RecordsIndexScreen.kt:244`, `ui/gym/SessionDetailScreen.kt:163`,
`ui/gym/LibraryIndexScreen.kt:367`. Almost always in the composition `"${era} ・ ${monthDay}"`.

`monthDay` (17): `ui/EventComposeScreen.kt:326`, `ui/CalendarScreen.kt:110`,
`ui/CalendarScreen.kt:208` (`"終日 ・ ${monthDay}まで"` — a **postposition glued to the output**),
`ui/HomeScreen.kt:187`, `ui/HomeScreen.kt:294` (**vertical**), `ui/NotificationsScreen.kt:124`,
`ui/CalendarFeedback.kt:303`, `ui/gym/GymHomeScreen.kt:161`, `ui/gym/RecordsIndexScreen.kt:187`,
`ui/gym/ExerciseDetailScreen.kt:216`, `ui/gym/LibraryDetailScreen.kt:316`,
`ui/gym/SessionDetailScreen.kt:163`, `ui/gym/LibraryIndexScreen.kt:367`,
`ui/gym/RecordsHistoryScreen.kt:405`, `ui/gym/RecordsPrScreen.kt:217`, `gym/ChartGeometry.kt:379`,
`gym/RecordCopy.kt:469`, `gym/InkDensity.kt:231`.

`dayOfWeek` (2 in main, 1 in test): `ui/HomeScreen.kt:187`, `ui/HomeScreen.kt:293` (**vertical**);
`test/.../RecordsIndexScreenTest.kt:281` calls it and then `.removeSuffix("曜日")` to recover the bare
weekday char — a test that **parses** the formatter's output (see §H4).

`eventTime` (2): `ui/HomeScreen.kt:190` (accessibility description), `ui/HomeScreen.kt:254`
(`EventColumns` — **rendered as vertical columns**).

`clock` (4): `ui/EventComposeScreen.kt:327`, `ui/CalendarScreen.kt:202`,
`ui/CalendarScreen.kt:213` (`"${clock(start)} – ${clock(end)}"`, en-dash), `ui/CalendarFeedback.kt:307`.

`dayToken` (3): `ui/TempoWheel.kt:211` (**wheel items — fixed-width picker**), `ui/HomeScreen.kt:189`,
`ui/HomeScreen.kt:255`.

`dayHeading` (1): `ui/CalendarScreen.kt:169` — day-group headers on the Calendar page.

`kanji` (10 in main): `ui/gym/RecordsIndexScreen.kt:222,244`, `ui/gym/BuilderScreen.kt:417,425`,
`gym/Numerals.kt:55,82,83`, `gym/RecordCopy.kt:250`, `gym/InkDensity.kt:196,197,217`,
`gym/HistoryPaging.kt:80`.

`kanjiExtended` (**~85 call sites in main**, essentially all of `gym/` and `ui/gym/`): counted across
`ui/gym/GymSettingsCopy.kt`, `RecordSummary.kt`, `RecordsIndexScreen.kt`, `ExerciseDetailScreen.kt`,
`BuilderScreen.kt`, `GymHomeCopy.kt`, `LibraryDetailScreen.kt`, `LibraryIndexScreen.kt`,
`ExerciseIndexScreen.kt`, `RecordsHistoryScreen.kt`, `RecordsPrScreen.kt`, `session/PlayerCopy.kt`,
and `gym/ChartGeometry.kt`, `RecordCopy.kt`, `RoutineEstimate.kt`, `BuilderDraft.kt`, `InkDensity.kt`,
`Numerals.kt`, `LibraryFilters.kt`, `EngineRows.kt`, `HistoryPaging.kt`, `data/GymStore.kt`. **Every
single one** is of the form `kanjiExtended(n) + <counter suffix>` — `回`, `秒`, `分`, `巡`, `日`, `件`,
`種目`, `巡目`, `番目`, `パーセント`. The gym and ui agents own those suffixes; this fragment owns the
numeral half. See §H2.

Tests: `test/.../data/JapaneseDateTest.kt` — 13 `@Test` methods, 131 lines, **all of them asserting
exact Japanese output strings**. Plus `test/.../ui/gym/RecordsIndexScreenTest.kt:266,281` and
`test/.../ui/gym/LibraryIndexScreenStructureTest.kt:118`. See §H4.

---

## app/src/main/java/io/eddiegulay/tempo/data/TempoFault.kt

Purpose: the app-wide error model. The supertype every failure travels through so that `faultCopy`,
`FaultStrip` and `FaultPanel` are written once, and a user learns what a fault looks like in this app
exactly once. Its own KDoc is explicit that error copy is never allowed to be silent.

**Zero Japanese string literals.** It is a `interface TempoFault` plus `sealed interface GymFault`
with eight `data object` / `data class` cases and no strings at all. The copy for these cases lives in
`ui/` (`faultCopy`) — the gym/ui agents own the words. What this file contributes to the migration is
the **key structure**: the `fault.*` root maps one-to-one onto these case names.

| key (proposed) | case | remedy the case exists to express |
|---|---|---|
| `fault.gym.storeCorrupt` | `GymFault.StoreCorrupt` | history is gone and quarantined; must NOT read as "none yet" |
| `fault.gym.storeUnavailable` | `GymFault.StoreUnavailable(cause: String?)` | disk full or file locked; retry is worth it. **Carries a raw platform `cause` string.** |
| `fault.gym.storeFull` | `GymFault.StoreFull` | remedy is free space, deliberately split from the above |
| `fault.gym.storeReset` | `GymFault.StoreReset` | older APK over a newer DB; remedy is reinstalling the newer build |
| `fault.gym.routineGone` | `GymFault.RoutineGone` | deleted while its page was open |
| `fault.gym.sessionGone` | `GymFault.SessionGone` | deleted from another page |
| `fault.gym.rejected` | `GymFault.Rejected` | a CHECK constraint refused the row |
| `fault.gym.unknown` | `GymFault.Unknown(cause: String?)` | **carries a raw platform `cause` string** |

The KDoc's own design note is load-bearing for the migration: it refuses a shared enum of remedies
because "it moves the decision about what to *say* away from the place that knows what happened", and
it notes `faultCopy`'s `else` branch "returns real words rather than falling through". A translation
table must preserve that — every branch, including the fallback, needs a translated string.

Japanese in this file is **prose in KDoc only**: `鍛錬` (line 34) and `記録はありません` (line 40, quoting
the copy that must not be shown falsely). Neither is a literal.

---

## app/src/main/java/io/eddiegulay/tempo/data/ThemeRepository.kt

Purpose: durable user settings (theme, onboarding-complete) with a synchronous first-frame read.
Described in full in §2 above.

**Zero user-visible Japanese.** Zero Japanese of any kind, including comments. Every stored value is
ASCII (`"paper"`, `"sumi"`, `"amoled"`). `TempoTheme.Paper` / `TempoTheme.Sumi` are enum constants,
never rendered — their user-facing labels live in `ui/`.

---

## app/src/main/java/io/eddiegulay/tempo/data/AppRepository.kt

Purpose: the process-wide live inventory of launchable apps behind Search and the app drawer; also
owns launch, app-info and uninstall actions and their failure toasts.

| key | ja | context | notes |
|---|---|---|---|
| `fault.app.launchFailed` | 起動できませんでした | `Toast` from `launch()`, line 135 (`ActivityNotFoundException`) | source comment glosses it "couldn't launch" |
| `fault.app.launchFailed` | 起動できませんでした | `Toast` from `launch()`, line 137 (`SecurityException`) | identical literal, second catch arm — two occurrences, one string |
| `fault.app.uninstallFailed` | アンインストールできませんでした | `Toast` from `requestUninstall()`, line 160 | |

3 occurrences, 2 distinct. These are the only three user-visible literals outside `JapaneseDate` in
the whole of `data/`.

Note: `AppInfo.category` (line 38) holds a **system-supplied** localized category title — the KDoc's
`"生産性"` is an illustrative example in a comment, not a literal. It comes from
`ApplicationInfo.getCategoryTitle(context, ...)` and follows the *system* locale, not Tempo's. See §H8.

---

## app/src/main/java/io/eddiegulay/tempo/data/BlockadeRepository.kt

Purpose: the 10-day app blockade ledger — hiding an app is a commitment that cannot be undone early
and best-effort survives reinstall.

**Zero user-visible Japanese literals** (and zero Japanese of any kind). All copy for the commitment
dialog and the countdown lives in `ui/`. Storage is `filesDir/blockade.json` with ASCII keys
(`"blocks"`, `"lastSeen"`), package-name map keys and epoch-millis values.

One number this file owns that the copy layer will need: `const val BLOCK_DAYS = 10`, referenced from
`LauncherViewModel.confirmBlock`'s KDoc. Any "10 days" / 「十日」 string in `ui/` is a composition over
this constant — a counter hazard for whoever owns that copy.

---

## app/src/main/java/io/eddiegulay/tempo/calendar/CalendarRepository.kt

Purpose: all reads and writes against `CalendarContract` — the agenda flow, the writable-calendar
list, and insert/update/delete.

| key | ja | context | notes |
|---|---|---|---|
| `calendar.event.untitled` | （無題） | `queryAgenda()`, line 185 — substituted when the provider's `TITLE` is null or blank | **full-width parentheses**, not ASCII. **Persistence hazard — see §H1.** |

1 occurrence, 1 distinct. `もう一度` at line 80 is prose in a KDoc quoting the retry button's label
(the real literal lives in `ui/`).

Non-string things worth recording for the migration: `GOOGLE_ACCOUNT = "com.google"` and
`CalendarContract.ACCOUNT_TYPE_LOCAL` drive calendar ordering — account identifiers, never translated.
`AGENDA_DAYS = 14`, `MAX_INSTANCES = 60`, `CHANGE_DEBOUNCE_MS = 300` are numbers, not copy.

---

## app/src/main/java/io/eddiegulay/tempo/calendar/CalendarOutcome.kt

Purpose: the calendar's fault vocabulary (`CalendarFault`), the `Loadable<T>` carrier that keeps a
failed load from rendering as an empty one, `WriteOutcome`, and `PendingWrite`.

**Zero Japanese string literals.** Same story as `TempoFault.kt`: pure types, copy lives in `ui/`.
Key structure it defines:

| key (proposed) | case | notes |
|---|---|---|
| `fault.calendar.permissionLost` | `CalendarFault.PermissionLost` | remedy: ask again, or Settings when permanently denied — **two different strings for one case**, see `CalendarPermission.kt` |
| `fault.calendar.noWritableCalendar` | `CalendarFault.NoWritableCalendar` | remedy routes to account settings |
| `fault.calendar.eventGone` | `CalendarFault.EventGone` | |
| `fault.calendar.rejected` | `CalendarFault.Rejected` | |
| `fault.calendar.noCalendarApp` | `CalendarFault.NoCalendarApp` | |
| `fault.calendar.unknown` | `CalendarFault.Unknown(cause: String?)` | **carries a raw platform `cause` string** |

Japanese in this file is **prose in KDoc only** (lines 15, 43, 47): `予定`, `予定はありません`,
`記録はありません` — all quoting copy defined elsewhere, all making the same architectural point that an
unread list must never render as an empty one. Not literals.

---

## app/src/main/java/io/eddiegulay/tempo/calendar/CalendarModels.kt

Purpose: `CalendarEvent`, `CalendarInfo`, `EventDraft`, and the all-day UTC↔local re-anchoring helpers.

**Zero Japanese, user-visible or otherwise.** `CalendarEvent.key = "$eventId:$begin"` is ASCII; the
`title`, `location` and `calendarName` fields carry *the user's own* provider data, which is never
translated by anyone. `EventDraft` is the write shape and holds nothing but user input.

---

## app/src/main/java/io/eddiegulay/tempo/calendar/CalendarPermission.kt

Purpose: the runtime calendar-permission "ask", the permanently-denied detection, the route into
account settings, and the shared `Context.findActivity()` helper.

**Zero Japanese, user-visible or otherwise.** Worth flagging for the copy owners though: the
`CalendarPermissionState.permanentlyDenied` flag exists specifically so the UI can **change its
words** — its KDoc says "a tap that silently throws the user into Settings with no warning reads as a
bug — the prompt has to change its words and say where it is taking them". That means the calendar
prompt has (at least) two distinct strings keyed on one boolean, and both need translating together.

---

## app/src/main/java/io/eddiegulay/tempo/notification/TempoNotificationListener.kt

Purpose: the bound `NotificationListenerService` that snapshots live device notifications into
`NotificationStore` for the 通知 screen, and fires inline actions and RemoteInput replies.

| key | ja | context | notes |
|---|---|---|---|
| `fmt.notification.yesterday` | 昨日 | `formatTime()`, line 159 — a notification posted yesterday | **the only relative-day word in the app that is not `JapaneseDate`'s.** `JapaneseDate` says `今日`/`明日`; this says `昨日` and there is no `JapaneseDate.dayToken` equivalent for the past. See §H3. |

1 occurrence, 1 distinct. The other two branches of the same `when` are format patterns, not
literals, and are hazards in their own right (§H3):

```kotlin
today            -> "%02d:%02d".format(posted.hour, posted.minute)
today.minusDays(1) -> "昨日"
else             -> "%d/%d".format(posted.monthValue, posted.dayOfMonth)
```

Note `"%d/%d"` — month/day, **unpadded, slash-separated, no year** — which is a US/JP ordering that
reads as day/month in most of Europe. Also note it is not `JapaneseDate.monthDay`, so the app has two
different month-day renderings.

Everything else the listener produces is **the source app's own text** — `title`, `body`,
`appLabel` (from `PackageManager.getApplicationLabel`), and action labels
(`action.title`). None of that is Tempo's copy and none of it is translatable by us. `appLabel`
falls back to the raw package name when the lookup fails (line 151), which is ASCII.

---

## app/src/main/java/io/eddiegulay/tempo/notification/NotificationStore.kt

Purpose: in-memory `StateFlow` bridge between the bound service and Compose; defines
`TempoNotification` and `TempoNotificationAction`.

**Zero Japanese.** All string fields (`title`, `body`, `time`, `appLabel`, action `title`) are
populated from the source app or from the listener's `formatTime` above.

## app/src/main/java/io/eddiegulay/tempo/notification/NotificationRepository.kt

Purpose: thin repository over `NotificationStore` + the live listener instance (dismiss, action,
reply, rebind).

**Zero Japanese.**

## app/src/main/java/io/eddiegulay/tempo/notification/NotificationGrouping.kt

Purpose: pure function bucketing a ranked notification list by source package into
`NotificationGroup`s.

**Zero Japanese.** Grouping key is `packageName`; `appLabel` is carried for display only. Its test
(`test/.../notification/NotificationGroupingTest.kt`) asserts **no Japanese at all** — verified — so it
survives the migration untouched.

---

## app/src/main/java/io/eddiegulay/tempo/MainActivity.kt

Purpose: the launcher's only Activity — HOME activity, edge-to-edge Compose host, owner of
`LauncherViewModel` and (lazily) `GymViewModel`, and the pre-Compose window painter.

**Zero user-visible Japanese literals.** The Japanese here is prose in KDoc: `鍛錬` (line 34), `続ける`
(line 79, quoting the gym's resume button). The only strings in the file are colour constants.

Relevant to the migration for a different reason: this is where the **first-frame** contract is
enforced (see §2, step 4), and its `android:configChanges` list in the manifest includes
`keyboard|keyboardHidden|navigation|orientation|screenSize|screenLayout|smallestScreenSize|density|uiMode`
— **`locale` and `layoutDirection` are not in that list**, so a system locale change today
recreates the Activity normally.

## app/src/main/java/io/eddiegulay/tempo/LauncherViewModel.kt

Purpose: the single source of truth for launcher UI state — screen, theme, onboarding gate, search
query, app inventory, blockade, notifications with the undo window, and the whole calendar surface
(agenda, writable calendars, composer, pending writes, faults).

**Zero user-visible Japanese literals.** Japanese here is prose in KDoc only: `鍛錬` (146, 150, 153),
`集中` (150, 188), `もう一度` (397, 400, naming the retry button whose literal lives in `ui/`).

What it contributes: it is where `loadInitialSettings()` is consumed (§2), and it is the *only* holder
of `themeRepository`, so a `setLanguage` / `language: StateFlow<…>` pair belongs here alongside
`toggleTheme()` (line 256) and `completeOnboarding()` (line 281).

---

## app/src/main/AndroidManifest.xml · res/** · build.gradle.kts

Purpose: app configuration and the (near-empty) resource tree.

**Zero Japanese string resources.** `res/values/strings.xml` is two lines of XML containing only
`<string name="app_name">Tempo</string>` — an ASCII product name, referenced from `android:label` on
both `<application>` and the notification-listener `<service>`. There is no `values-ja`, no
`values-en`, no `plurals`, no `string-array` anywhere in `res/`.

The manifest *does* contain a lot of Japanese — `鍛錬`, `予定`, `予定に入れる`, `通知` — but **all of it
is inside XML comments** explaining permission rationale. Not one Japanese character reaches an
attribute value. Same for `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`, whose
comments quote `振動 / 音 / 音声`, `支度の長さ`, `単位`, `画面を消さない`, `記録はありません` while their
actual `<include>` paths are all ASCII.

Gradle: `app/build.gradle.kts` touches resources only via `isShrinkResources = true` on the release
build type. No `resConfigs`, no `androidResources`, no locale filtering, no `generateLocaleConfig`.
`minSdk = 29`, `targetSdk = 36` — note that per-app language (`android:localeConfig` +
`LocaleManager.setApplicationLocales`) is API 33+, so on this `minSdk` a platform-native language
picker would need the AppCompat backport, which is not currently a dependency.

---

## Hazards

### H1. `（無題）` is a display default that writes itself into the user's real calendar — persistence

`app/src/main/java/io/eddiegulay/tempo/calendar/CalendarRepository.kt:185`

```kotlin
title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "（無題）",
```

Path to persistence, verified end to end:

1. `CalendarRepository.kt:185` — a blank provider title becomes the literal `（無題）` on `CalendarEvent.title`.
2. `ui/EventComposeScreen.kt:100` — `var title by remember(editing) { mutableStateOf(editing?.title.orEmpty()) }`
   prefills the editable field with it.
3. `ui/EventComposeScreen.kt:123` — `canSave = title.isNotBlank() && …`, so the placeholder makes the
   save button *enabled* where a genuinely blank title would not.
4. `ui/EventComposeScreen.kt:178` — `title = title.trim()` into the `EventDraft`.
5. `CalendarRepository.kt:334` — `put(CalendarContract.Events.TITLE, title)`.

The result leaves the device: the row is marked dirty and the Google sync adapter pushes it to the
user's account, their other devices, and any guests on the invite. This is **not** an in-app string
that can be swapped freely — once written it is third-party data in a store Tempo does not own, and
switching the app's language later will not (and must not) rewrite it.

Additionally, `test/.../ui/CalendarFeedbackTest.kt:195` asserts `assertEquals("（無題）", summary.title)`,
so this literal is also pinned by a test (§H4).

Recommended shape: keep the placeholder out of the model. Let `CalendarEvent.title` stay empty/null
and resolve the placeholder at draw time, so nothing that can be saved ever contains it. That is a
behaviour change, not a string swap, and it is the reason this is listed first.

### H2. The kanji numeral formatter is an orthography, not a translation — counters and numerals

`data/JapaneseDate.kt:21` (`kanji`) and `:53` (`kanjiExtended`).

- `kanji` covers **0..99 only** and would produce garbage outside it; `kanjiExtended` covers 0..9999
  and **deliberately falls back to arabic** above 9999 and for negatives. The `万` cut-off is an
  argument about *reading* Japanese, documented at length in the KDoc (lines 29-52). None of that
  reasoning transfers to a language that groups in thousands — English wants `12,345` with a group
  separator at exactly the boundary Japanese does not have one.
- Roughly **95 call sites** across `gym/` and `ui/gym/` compose the output as
  `kanjiExtended(n) + <counter>`, with counters `回`, `秒`, `分`, `巡`, `日`, `件`, `種目`, `巡目`,
  `番目`, `パーセント`. Every one of those is a **composed string** and a **pluralisation hazard**:
  Japanese has no plural, English does (`1 rep` / `2 reps`, `1 minute` / `2 minutes`), and several
  compose *two* numbers in one line — e.g. `ui/gym/session/PlayerCopy.kt:120`
  `kanjiExtended(round) + "巡目 / " + kanjiExtended(totalRounds) + "巡"`, and
  `gym/RecordCopy.kt:191` `kanjiExtended(actual) + "回 / " + kanjiExtended(prescribed) + "回"`.
- Word order flips. `gym/RecordCopy.kt:352` builds `kanjiExtended(days) + "日 連続"` — "N days
  running"; `gym/InkDensity.kt:227` builds `monthLabel + "、" + kanjiExtended(trained) + "日 鍛錬しました"`.
  A per-language formatter, not a per-language numeral table, is the only thing that survives this.
- `gym/Numerals.kt:55` builds a **decimal** out of the formatter:
  `kanjiExtended(whole) + "." + kanji(fraction)` — an ASCII decimal point between two kanji numerals.
  The decimal separator is a comma in much of Europe.
- `gym/LibraryFilters.kt:312` does `first + kanjiExtended(n)` to **disambiguate duplicate names** —
  the numeral is part of a generated identifier, not decoration.

The suffix literals themselves belong to the gym/ui agents. What belongs to this file is the decision
that a numeral has a *script*, and that decision has to be parameterised by language, not by table.

### H3. Two independent relative-day vocabularies, and they disagree — dates and relative time

- `JapaneseDate.dayToken` (`:124`) and `dayHeading` (`:132`) handle **today and forward only**:
  `今日` → `明日` → weekday within 7 days → kanji month-day beyond. There is no yesterday and no past.
- `TempoNotificationListener.formatTime` (`:153-162`) handles **today and backward**: `HH:mm` today,
  `昨日` yesterday, `M/D` beyond. It does not use `JapaneseDate` at all, and its month-day format
  (`"%d/%d"`, arabic, slash) is a *different rendering of the same information* from
  `JapaneseDate.monthDay` (`六月十七日`, kanji).
- A third variant exists in `ui/gym/GymHomeCopy.kt:225` — `kanjiExtended(days) + "日前"` ("N days ago")
  — whose test at `GymHomeCopyTest.kt:350` is literally named
  ``a session abandoned this morning is きょう at ten at night, not 二時間前 and not 昨日``, i.e. the
  boundary between these vocabularies has already been a bug once.

Any language design must decide whether these three collapse into one relative-day function or stay
apart, before any string is translated. Also note the cascade boundaries are themselves cultural:
`date.isBefore(today.plusDays(7))` renders a bare weekday name for the next week, which in English
("Wednesday") is ambiguous between the coming and the past Wednesday in a way `水曜日` in this context
is not.

### H4. 13 test methods plus 4 further assertion sites pin exact Japanese output — tests

- `app/src/test/java/io/eddiegulay/tempo/data/JapaneseDateTest.kt` — **13 `@Test` methods, 131 lines,
  every one asserting an exact Japanese string**: `〇`/`七`/`十`/`十一`/`二十`/`二十一`/`四十二`/`九十九`;
  `百`/`千`/`三百`/`六百`/`八百`/`三千`/`八千`/`百十`/`百一`/`二百`/`二百三十`/`千十`/`千一`/`千百`/
  `二千三百`/`五千五`/`百二十三`/`四百五十六`/`千二百三十四`/`九千八百七十六`/`九百九十九`/`九千九百九十九`;
  `令和八年`/`令和一年`; `六月十七日`/`一月一日`; `水曜日`/`日曜日`; `午前九時五分`/`午後一時三十分`/
  `午前十二時`/`午後十二時`. Plus the arabic-fallback assertions (`"10000"`, `"123456"`, `"-1"`, `"-42"`).
  Every one of these breaks the moment `JapaneseDate` becomes language-aware unless the tests are
  re-pointed at an explicitly-Japanese formatter instance.
- `app/src/test/java/io/eddiegulay/tempo/ui/CalendarFeedbackTest.kt:195` —
  `assertEquals("（無題）", summary.title)`, pinning the H1 literal.
- `app/src/test/java/io/eddiegulay/tempo/ui/gym/RecordsIndexScreenTest.kt:266` — `JapaneseDate.kanji(m) + "月"`.
- `app/src/test/java/io/eddiegulay/tempo/ui/gym/RecordsIndexScreenTest.kt:281` —
  `JapaneseDate.dayOfWeek(...).removeSuffix("曜日")`. This one **parses the formatter's output** to
  recover a weekday initial; it is not just an assertion, it is a consumer that assumes the string
  ends in `曜日`. It breaks in a way a simple expected-value update will not fix.
- `app/src/test/java/io/eddiegulay/tempo/ui/gym/LibraryIndexScreenStructureTest.kt:118` — asserts on
  the source-level template `"${JapaneseDate.era(now)} …"`.

`test/.../notification/NotificationGroupingTest.kt` and `androidTest/.../ExampleInstrumentedTest.kt`
contain **no Japanese** and are unaffected.

### H5. `JapaneseDate` output is drawn vertically (tategaki) and inside fixed-width wheels — measurement of text

- `ui/HomeScreen.kt:293-295` renders `dayOfWeek`, `monthDay` and `era` through `VerticalLine(...)` —
  Home's vertical kanji column. `ui/HomeScreen.kt:254` (`EventColumns`) does the same for `eventTime`
  and `dayToken`. There is a dedicated `test/.../ui/TategakiTest.kt`. Vertical text is glyph-by-glyph
  layout that assumes square, monospaced CJK glyphs; `Wednesday` stacked one Latin letter per line is
  not a layout, it is a failure.
- `ui/TempoWheel.kt:211` feeds `dayToken` output into a **picker wheel's item list**. Wheel items are
  fixed-height, fixed-width rows; `今日` (2 glyphs) and `六月十九日` (5 glyphs) both fit where
  `Wednesday 19 June` will not.
- `ui/gym/LibraryDetailScreen.kt:1019` renders a bare `kanjiExtended(position)` as a positional badge —
  a single-glyph slot for numbers up to `九千九百九十九`.

### H6. The type system is two CJK typefaces — fonts

`res/font/` contains only `shippori_mincho_{regular,medium}.ttf` and
`zen_kaku_gothic_new_{light,regular}.ttf`, wired up in `ui/theme/Type.kt:19-25`. Shippori Mincho and
Zen Kaku Gothic New both carry Latin, but their Latin is designed to sit beside CJK at CJK metrics —
half-width, comparatively small on the em, and with CJK-tuned vertical rhythm. Every sp size, line
height and letter-spacing in the app was chosen against those metrics. This is a genuine design
problem for a Latin language, not a resource swap, and it is independent of every string in this
inventory.

### H7. Faults carry raw platform strings that no table can translate

`GymFault.StoreUnavailable(cause: String?)`, `GymFault.Unknown(cause: String?)` and
`CalendarFault.Unknown(cause: String?)` all carry a `Throwable.message` (see
`CalendarRepository.kt:359-363`, `internal fun Throwable.toFault()`). Whatever the copy layer does
with `cause`, that fragment arrives in **the system's** language (often English), inside an otherwise
Japanese sentence. Whether it is shown at all is a copy decision the fault-copy owner has to make
deliberately rather than inherit.

### H8. System-supplied strings will not follow an in-app language toggle

Three sources of user-visible text in this scope come from the platform, not from Tempo, and will
follow the **system** locale no matter what Tempo's own language toggle says:

- `AppInfo.label` and `AppInfo.category` (`AppRepository.kt:104,108`) — from `LauncherApps` /
  `ApplicationInfo.getCategoryTitle`. `AppRepository` already registers an `ACTION_LOCALE_CHANGED`
  receiver (line 78-83) to reload them when the *system* locale changes — an in-app toggle will not
  fire that broadcast.
- `TempoNotification.appLabel` and action titles (`TempoNotificationListener.kt:102,148-151`) — from
  the posting app.
- `CalendarInfo.displayName` / `CalendarEvent.calendarName` (`CalendarRepository.kt:231,191`) — from
  the calendar provider.

An English-language Tempo on a Japanese phone will therefore show English chrome around Japanese app
names and category titles. That is expected and unavoidable, but it should be a stated decision rather
than a surprise.

### H9. Sorting is `lowercase()`-based and will not order kana

`AppRepository.kt:118` — `.sortedBy { it.label.lowercase() }`. `String.lowercase()` with no `Locale`
argument uses the default locale, and lexicographic ordering of `lowercase()`d labels does not produce
kana ordering (gojūon) for Japanese app names, nor a sensible mixed Latin/kana order. The app already
has a `test/.../gym/KanaFoldingTest.kt` and a `gym/LibraryFilters.kt` that take kana ordering seriously
— this line does not. Flagged because a language toggle is exactly when someone will look at it, and
because `lowercase()` without an explicit `Locale` is the classic Turkish-dotless-i bug.

### H10. `（無題）` uses full-width parentheses

`CalendarRepository.kt:185` — the literal is `（無題）` with U+FF08 / U+FF09, not ASCII `(` `)`. Worth
recording exactly, because a naive copy-paste into a resource file that "cleans up" punctuation
silently changes the string, and §H1 shows this one can be written to a remote store.

---

## Non-visible Japanese

**None.** Every Japanese literal in this scope reaches a user. There are no Japanese database column
values, no Japanese log tags, no Japanese enum storage keys, and no Japanese test fixtures in these
files.

For completeness, the Japanese in this scope that is **not a literal at all** — it is prose inside
KDoc and XML comments, and must not appear in any translation table:

| file | Japanese in comments | why it is there |
|---|---|---|
| `data/JapaneseDate.kt` | `一万二千三百四十五`, `万`, `負三`, `マイナス三`, `一百`, `一千`, `三百`/`六百`/`八百`/`三千`/`八千`, `さんびゃく`/`ろっぴゃく`/`はっぴゃく`/`さんぜん`/`はっせん`, `百十〇`, `千〇〇`, `午後九時一分`, `令和八年`, `六月十七日`, `水曜日`, `十九時三十分`, `十九時`, `今日`/`明日`, `六月十九日` | worked examples in the KDoc explaining the numeral and date rules |
| `data/TempoFault.kt` | `鍛錬`, `記録はありません` | naming the feature and quoting the copy the type exists to prevent lying with |
| `calendar/CalendarOutcome.kt` | `予定`, `予定はありません`, `記録はありません` | same argument, stated for the calendar |
| `calendar/CalendarRepository.kt` | `もう一度` | naming the retry button whose literal lives in `ui/` |
| `data/AppRepository.kt` | `生産性` | an illustrative example of a system-supplied category title |
| `MainActivity.kt` | `鍛錬`, `続ける` | feature name; quoting the gym's resume button |
| `LauncherViewModel.kt` | `鍛錬`, `集中`, `もう一度` | feature names; quoting buttons defined in `ui/` |
| `AndroidManifest.xml` | `鍛錬`, `予定`, `予定に入れる`, `通知` | permission-rationale comments |
| `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` | `鍛錬`, `振動`, `音`, `音声`, `支度の長さ`, `単位`, `画面を消さない`, `記録はありません` | comments explaining which DataStore files must be in the include-list |

Every `<include>` path, every attribute value and every stored key in those files is ASCII.
