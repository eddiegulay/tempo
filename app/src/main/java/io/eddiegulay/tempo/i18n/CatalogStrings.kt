package io.eddiegulay.tempo.i18n

/**
 * Seeded content from the database: exercise names, cues, routine names.
 *
 * **This file is owned by one migration unit.** Interface plus both implementations live together so
 * that adding a string is a single-file change and two people migrating different pages never touch
 * the same file. Add members here; never to `Strings.kt`, which only lists the namespaces.
 *
 * Rules for filling it, from `.planning/i18n/DECISIONS.md`:
 *
 * - A key names *what the string means*, never what it says.
 * - Japanese values are **transcribed, not authored** — the app ships them today and this move must be
 *   behaviour-neutral for Japanese.
 * - Anything built from a number or a date belongs on [Formats], not here.
 * - Do not fill a deliberate hole: several functions return null rather than invent a sentence the
 *   specs never wrote, and an English string in that position re-introduces the bug the null prevents.
 */
interface CatalogStrings

internal object JaCatalog : CatalogStrings

internal object EnCatalog : CatalogStrings
