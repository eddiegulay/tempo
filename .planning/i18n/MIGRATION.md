# Migration brief — read this before touching anything

You are migrating one namespace of Tempo's copy out of hard-coded Japanese Kotlin literals and into
the two-language string table. Several agents are doing this at once on different namespaces.

## Read first

- `.planning/i18n/DECISIONS.md` — ten binding rulings. §L7 (numerals), §L10 (what must NOT be
  translated) and §L3 (why `Tier` stores Japanese forever) will each save you an hour.
- `.planning/i18n/TREE.md` — §3 is the stored-vs-drawn ledger and §4 the hazard register.
- `.planning/i18n/inventory/*.md` — the detailed survey. Find the fragment covering your files; it
  already lists your literals with proposed keys and quotes the hazards with `file:line`.

## The rule that prevents collisions

**You own exactly one file under `app/src/main/java/io/eddiegulay/tempo/i18n/`: your own
`<Namespace>Strings.kt`.** It holds the interface and both implementations together, so adding a
string is a single-file change.

**Never edit** `Strings.kt`, `StringsJa.kt`, `StringsEn.kt`, `Formats.kt`, `I18nGateTest.kt`, or any
other namespace's file. They are already wired to you — your objects are referenced, and an empty
interface compiles. Another agent is in those files right now. If you believe you need something from
one of them, say so in your report instead; do not reach across.

You additionally own the **page files in your scope** and **their tests**. Nothing else.

## How to migrate a string

1. Add a member to your interface: `val resumeSession: String`.
2. Implement it in `Ja<Namespace>` with the literal **transcribed exactly** — same characters, same
   spacing, same punctuation. This move must be behaviour-neutral for Japanese, and there are tests
   asserting these exact strings.
3. Implement it in `En<Namespace>`.
4. Replace the literal at the call site with `s.<namespace>.resumeSession`.
   - **Composables** read the table from `LocalStrings.current`, conventionally `val s`.
   - **Pure functions** take `strings: Strings` as a parameter. Do not reach for a global; the copy
     tests run on plain JUnit with no Compose and no `Context`, and that is deliberate.
5. Update the tests that asserted the literal. Most should keep asserting the Japanese, now sourced
   from `StringsJa` — that keeps them testing behaviour rather than transcription.

## Keys

Dotted meaning, not spelling. `gymSession.quitConfirm`, never `gymSession.honmatsu`. A key names what
the string *means*, so that the English implementation is obviously right or obviously wrong.

## Anything built from a number or a date is NOT a string

It goes through `strings.fmt` (`Formats`), which already exists and is complete. Use it:

| instead of | write |
|---|---|
| `JapaneseDate.kanjiExtended(n) + "回"` | `s.fmt.reps(n)` |
| `JapaneseDate.kanjiExtended(n) + "巡"` | `s.fmt.rounds(n)` |
| `JapaneseDate.kanjiExtended(n) + "種目"` | `s.fmt.stations(n)` |
| `durationKanji(sec)` | `s.fmt.duration(sec)` |
| `clockDuration(ms)` | `s.fmt.clock(ms)` |
| `coefficientLabel(c)` | `s.fmt.coefficient(c)` |
| `JapaneseDate.monthDay(t)` etc. | `s.fmt.monthDay(t)` etc. |
| a literal `" ・ "` separator | `s.fmt.separator` |
| a literal `"、"` | `s.fmt.listSeparator` |
| `"—"` for a missing value | `s.fmt.noValue` |

`Formats` has counters for 回 巡 種目 秒 分 日 月 件 番目, ordinals, durations, dates, relative days and
separators. **If you need a formatter that is not there, do not build one in your namespace** — report
it and use the nearest existing one, or leave that call site for a follow-up. A second numeral
formatter is explicitly forbidden (`Numerals.kt:9-13`) and that rule still holds.

`JapaneseDate` and `Numerals` themselves stay exactly as they are. They are `JaFormats`' internals now.
Do not edit them, and do not touch `JapaneseDateTest`, `NumeralsTest`, `KanaFoldingTest` or
`TategakiTest` — those test Japanese as a subject, not as copy.

## What must NOT be translated

Read §L10, but the short list: kana-folding tables, `AppGlyph`'s Japanese app-name keywords, `Tier`'s
storage tokens, `JapaneseDate`'s numeral and weekday char tables, and the two endonyms in the language
picker. Translating a lookup key or a stored value is a data-loss bug, not a cosmetic one.

**Before you translate any literal, ask whether it is drawn or stored.** If it reaches SQLite,
DataStore, an intent extra, a `when` branch, a map key or a `contains` match, it is code.

## Writing the English

Tempo's Japanese copy is quiet, plain and short. It does not exclaim, does not apologise, and does not
explain twice. English that reads like a product announcement is a faithful translation of the words
and a wrong translation of the app.

**Length is functional here, not stylistic.** Japanese is roughly half the width of English for the
same content, and several slots are measured in glyphs — the player's hero cap divides its width by
4.2 and *clips rather than ellipsises*. If your fragment flags a constrained slot, respect it and say
in your report if you could not.

**Do not fill a deliberate hole.** Four functions in the gym return `null` or `""` rather than invent a
sentence the specs never wrote. An English string in one of those positions re-introduces the exact
bug the null prevents. If you find a `null` branch with a comment explaining itself, leave it.

## Refuse rather than invent

If a string has no clear meaning, or a hazard needs a product decision, **do not guess**. Leave the
call site as it is, and escalate in your report with the `file:line` and what you would need to know.
An honest gap is worth more than a plausible invention — that has been the standing rule on this
project and it has caught real errors.

## Before you report

Run, from the repo root:

```
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
```

Both must pass. If a test outside your scope fails, that is a collision — say so in your report rather
than editing another agent's file to fix it.

## Report

Keep it short and factual:

- strings moved, and which files are now free of Japanese copy literals
- any file you could **not** finish, and precisely why
- escalations: hazards you hit, decisions you need, formatters you wanted and did not have
- anything you found that is a bug today, independent of language
