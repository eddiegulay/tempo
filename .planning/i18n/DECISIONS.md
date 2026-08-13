# i18n — binding decisions

Rulings that the migration follows. Each one resolves something the survey escalated. Numbered `L`
for language, to stay distinct from the gym feature's `Q` series.

---

## §L1 — Japanese and English only, and the store knows it

**Two languages.** The database's existing `name_ja` / `name_en` column pair fits as-is, the four
bundled TTFs already cover English fully (ASCII 95/95, full Latin-1), and pluralisation stays at
one/other. A third language is a schema migration and a `Strings` implementation, and that is an
acceptable price for not building a keyed translation table nobody needs yet.

Consequence accepted: `Exercise.nameJa` / `nameEn` stay as field names. They encode the two-language
assumption honestly rather than pretending to be general.

---

## §L2 — A Kotlin table, not Android string resources

`res/values-en/strings.xml` is the platform answer and it is the wrong one here, for one decisive
reason: **the domain layer has no `Context`.** 138 user-visible strings live in `gym/` — `RecordCopy`,
`EngineRows`, `ChartGeometry`, `GymModels` — and 21 more in `data/`. These are pure Kotlin, tested by
1,333 plain JUnit tests. Reading a string resource needs a `Context`, and reading one *in a unit test*
needs Robolectric, which `CONTRIBUTING.md:38` forbids. Resources would push `Context` into the domain
layer and take the copy tests off the JVM.

The table is therefore an **interface with two implementations**:

```kotlin
interface Strings { val lang: Lang; val home: HomeStrings; … }
object Ja : Strings { … }
object En : Strings { … }
```

The property this buys, which resources cannot: **a missing translation is a compile error.** An
unimplemented interface member does not build. Android resources fall back to the default language
silently, which for this app means silently shipping Japanese to an English user — the exact failure
the migration exists to prevent.

Grouped into nested interfaces per namespace (`§CONTRACT` roots), one file per group, so 787 strings
stay navigable and two people can migrate different pages without conflicting.

---

## §L3 — `Tier` keeps storing Japanese; only its label moves

The survey's top data-loss finding: `Tier.storageValue = label`, with `Schema.kt:141` enforcing
`CHECK (tier IS NULL OR tier IN ('入門','中級','上級'))`.

**The stored value does not change.** `入門` becomes an opaque token that happens to be spelled in
Japanese, exactly like `"amoled"` is an opaque token in `ThemeRepository`. The CHECK constraint keeps
working, every existing row keeps working, and there is **no migration** — which matters, because
SQLite 3.28 cannot alter a CHECK without rebuilding a table that has mutual foreign keys.

```kotlin
enum class Tier(val storageValue: String) { BEGINNER("入門"), … }
```

`label` is deleted from the enum and served from the table. This is what the enum's own KDoc
anticipated: *"so the day someone regrets it there is one place to change."*

The same reasoning applies to every label-carrying enum (13 of them): **a label in a constructor
argument is fixed at class-init and cannot be re-resolved when the user flips a switch.** All 13 lose
their `label` property. Where the label is also the stored value, storage keeps the Japanese token.

---

## §L4 — Resolution is a `staticCompositionLocalOf`, mirroring the theme

Language follows the theme's path hop for hop, because that path already solves the hard part — being
correct on the *first frame*:

```
DataStore stringPreferencesKey("language")   → ThemeRepository (NOT a new repository: one
                                                DataStore owner per file per process, and
                                                tempo_settings is in both backup include-lists)
  → InitialSettings.lang, read by loadInitialSettings()'s existing runBlocking
  → LauncherViewModel.lang: StateFlow, stateIn(…, Eagerly, initialSettings.lang)
  → TempoApp: CompositionLocalProvider(LocalStrings provides strings)
```

`staticCompositionLocalOf`, like `LocalTempoColors` — it does not track reads, so a change recomposes
the provider's whole content lambda. That is what makes a language switch instant and total.

**No per-app locale, no activity recreate.** The manifest's `configChanges` does not list `locale`, so
`LocaleManager` would recreate the activity on every switch. A recomposition is cheaper and does not
interrupt a running workout.

**Domain code takes `Strings` as a parameter.** It is a plain object; only composables read it from
the local. This is what keeps the copy tests on the JVM.

---

## §L5 — First-run default follows the system; existing installs stay Japanese

An unset language key is ambiguous: a fresh install and a two-year-old install look identical.
Resolved by reading `onboardingComplete`, which already distinguishes them:

| stored language | onboarding complete | resolves to |
|---|---|---|
| present | — | the stored value |
| absent | `true` — an existing install | **Japanese**, preserving what the user has been using |
| absent | `false` — a fresh install | the system locale: `ja` → Japanese, anything else → English |

Without the second row, every existing user on an English-locale device would have their app silently
change language on upgrade. This mirrors `ThemeRepository`'s existing `"amoled"` read-side migration:
the old value keeps meaning what it always meant.

---

## §L6 — The toggle is a picker in the Search header, plus a row on onboarding

`SearchScreen.kt:117-130` already hosts the app's only global setting (the theme), commented
"relocated from the dock". A third `HeaderIconButton` opens a `LanguageDialog` shaped like
`ModeDialog`.

**A picker, not a toggle.** Each row is written in its own language — 日本語 and English — so both are
readable *before* the choice is made. A blind toggle is acceptable for theme, where the result is
visible instantly and reversible by eye; it is not acceptable for language, where guessing wrong may
leave a user unable to read the control that would undo it.

**And a language row on onboarding, above 「ようこそ」.** This is not a convenience. Onboarding's two
permission rationales (`OnboardingScreen.kt:120-121`, `:132-133`) explain in Japanese prose that Tempo
will read every notification and become the home app. A user who cannot read that is not consenting;
they are pressing a button. The row shows once, at the only moment it is load-bearing.

Rejected: a new `Screen.Settings`. Architecturally where this ends up, but it costs an enum value, a
back arm, an entry point, and a page of new untranslated copy. Build it at three-plus app-wide
settings and move the theme toggle onto it then.

---

## §L7 — The numeral rules collapse, and we say which behaviour is being deleted

`DECISIONS.md` §Q4 (a *ticking* value is arabic, a *stopped* value is kanji) and §Q10 (a duration the
user *chose* renders bare, one the app *measured* is spelled out) are both carried by **orthography** —
kanji versus arabic. English has no second orthography, so both distinctions have nowhere to go.

Ruling: **in English both collapse to arabic digits with a unit word.** `残り 二十三秒` → `23s left`;
`六十秒` and `一分三十秒` both become `1:30`-style or `60s`-style by the same rule.

This deletes a documented behaviour rather than translating it, and that is stated here so it is a
decision rather than an accident. Japanese keeps both rules exactly as they are — §Q4 and §Q10 remain
binding for `Ja`, and the four twin functions they govern keep their Japanese tests.

Same ruling for the two relative-day vocabularies: `今日/明日` (forward, kanji) and `きょう/きのう`
(backward, hiragana) are one word in English. `Today` is `Today`.

---

## §L8 — The formatter layer is separate from the copy table

`fmt.*` is not a string table; it is behaviour. `JapaneseDate` (11 functions, ~85 `kanjiExtended`
callers) and `Numerals` (4 functions) become an interface with a `Ja` and an `En` implementation, held
on `Strings` so one lookup gets both copy and formatting.

Two notes carried from the survey:

- `Numerals.clockDuration`, `JapaneseDate.time` and `JapaneseDate.clock` port **unchanged** — they are
  already `%02d:%02d`. Guard: `String.format` without a `Locale` uses the default, so they take an
  explicit `Locale.ROOT`.
- `durationKanji` has **no hours** — 6000s renders `百分` ("100 minutes"). That is pre-existing and
  stays for Japanese; the English implementation does not inherit it.

The `・` separator used in ~20 composed strings is a locale-dependent punctuation constant on the
formatter, not 20 hard-coded literals.

---

## §L9 — Home's corner is horizontal in English; tategaki stays for Japanese

`TategakiText` keeps its exactly-correct Japanese behaviour and its tests. English gets a horizontal
corner cluster. `VerticalLine` (`HomeScreen.kt:298-313`) — which stacks every char with no height
bound and none of Tategaki's logic — is **retired**, not translated: it is the failure mode
`Tategaki.kt:29-31` names and rejects.

The era line renders `Reiwa 8` in English rather than `2026`, keeping the app's character. One
constant if that is ever regretted.

---

## §L10 — What is not translated, and is not allowed to be

From the survey's ledger. These are code:

- the kana fold tables (`LibraryFilters.kt:23-39`) — Japanese search must keep working, because
  routine names are user data and stay Japanese under an English UI;
- `AppGlyph`'s 19 Japanese keywords — matched against *other apps'* labels, which do not follow our
  toggle;
- `JapaneseDate`'s numeral and weekday char tables;
- every `Tier` storage token (§L3);
- `NO_VALUE = "—"`, an em dash that is not Japanese and not copy.

And three surfaces where our copy leaves the app entirely, which no toggle can reach: the device
calendar (`Events.TITLE`, and routine names via `ScheduleNextAction`), the system notification shade,
and **session history**. History is denormalised at write time and pinned to a `routine_version`
snapshot, so sessions recorded in Japanese stay Japanese forever. That follows from the existing and
correct decision to freeze history against routine edits. It is accepted, not fixed.

Separately: `（無題）` (`CalendarRepository.kt:185`) is a display placeholder that reaches
`Events.TITLE` at `:334` and syncs to Google and to event guests. **That is a bug today**, independent
of this work, and is fixed as part of it.
