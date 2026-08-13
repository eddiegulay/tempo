# i18n inventory — shared contract

You are one of several agents surveying Tempo for a Japanese → multi-language migration. This file is
the contract every fragment obeys so the fragments merge into one tree without a second pass.

## The situation

Tempo is a Japanese minimalist Android launcher. **Every user-visible string is a hard-coded Japanese
literal in Kotlin.** `res/values/strings.xml` contains exactly one entry (`app_name`). There is no
`Locale` plumbing, no string resources, no formatter layer. We are adding a language toggle and a
single source of truth for translations.

## Your job

Survey your assigned scope and produce an inventory. **Research only — you must not edit, create or
delete anything under `app/src`.** Your only write is your own fragment file.

## Output

Write exactly one file: `.planning/i18n/inventory/<your-slug>.md`. Nothing else. Use this shape:

```markdown
# <Scope name>

**Files surveyed:** N   **User-visible literals:** N   **Non-visible JP literals:** N

## <path/to/File.kt>

Purpose: one line — what page/component this is and where the user meets it.

| key | ja | context | notes |
|---|---|---|---|
| `gym.home.resume` | 続きから | button on 鍛錬 home | only when a session is resumable |

## Hazards

- anything that is not a plain string swap (see below)

## Non-visible Japanese

Literals that are Japanese but never reach a user — DB column values, log tags, test fixtures,
enum storage keys. List them with the reason they stay Japanese.
```

## Key naming

Dotted, lowercase, dot-separated segments, camelCase inside a segment. Roots:

| root | covers |
|---|---|
| `app.*` | app-wide chrome, dock, nav |
| `home.*` `search.*` `filter.*` `focus.*` `calendar.*` `notifications.*` `onboarding.*` | launcher pages |
| `dialog.*` | modal dialogs |
| `gym.home.*` `gym.library.*` `gym.exercise.*` `gym.builder.*` `gym.records.*` `gym.session.*` `gym.settings.*` | 鍛錬 pages |
| `catalog.*` | seeded exercise/routine content from the database |
| `fault.*` | error and failure copy |
| `cue.*` | spoken/audio cue text |
| `fmt.*` | anything produced by a formatter rather than picked from a table |

A key names *what the string means*, never what it says. `gym.session.quit.confirm`, not
`gym.session.honmatsu`.

## What counts as a hazard

Flag anything where translation is **not** a table lookup. Be specific and cite `file:line`:

1. **Composed strings** — a literal concatenated with a number, a name, or another literal. Word
   order differs by language; record the full composition, not the fragment.
2. **Counters and numerals** — kanji numerals, counter suffixes (回/本/分), the arabic-vs-kanji rule.
3. **Dates and relative time** — きょう/きのう, weekday names, month formats.
4. **Pluralisation** — Japanese has none; English does. Any count-bearing string is a hazard.
5. **Measurement of text** — layout that assumes short strings: fixed widths, `maxLines`, truncation,
   vertical text (tategaki), character-by-character animation, per-glyph rendering.
6. **Fonts** — anything assuming Japanese glyph metrics.
7. **Speech / TTS** — hard-coded `Locale.JAPANESE`, spoken phrasing.
8. **Sorting and filtering** — kana ordering, search that matches Japanese substrings.
9. **Persistence** — a Japanese string written to the database or DataStore, where changing language
   must not orphan stored rows.
10. **Tests** — an existing test asserting a Japanese literal. These break on migration; count them.

## Rules

- Read the whole file. Do not sample.
- Quote the literal exactly, including punctuation and spacing.
- If one literal appears in several files, still list it in each; deduplication is the merge step's job.
- Do **not** invent English translations. The inventory records what exists and what it means.
- If you cannot tell whether a literal is user-visible, say so in the notes rather than guessing.
