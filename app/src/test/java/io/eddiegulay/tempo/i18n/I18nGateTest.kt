package io.eddiegulay.tempo.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **A migrated file does not go back.**
 *
 * The 鍛錬 → multi-language migration moves 787 user-visible strings out of Kotlin literals and into
 * [Strings]. That is a long job across many files, and the failure mode is not that it stalls — it is
 * that a file gets migrated, and then three weeks later someone adds one more `Text("追加")` to it
 * because every other line in the file used to look like that. Nothing breaks, nothing fails, and the
 * English build quietly grows a Japanese word.
 *
 * A checklist in a planning document is enforced by whoever last read it. This is the same rule as a
 * fact the JVM checks.
 *
 * **The default is "no Japanese", and every exception is argued for in writing.** The gate scans the
 * whole main source set and permits a Japanese literal only where [japaneseAllowed] gives a reason.
 * It was an append-only list of *migrated* files while the migration was in flight; a list of what is
 * done cannot see a file added next month, so it was inverted once the sweep was complete.
 *
 * Deliberately modelled on `AcwrRestraintTest` rather than on `GymShellTest` — it is the established
 * pattern in this repo for "a rule about the source text": comments stripped first, offenders named
 * in the failure message, an explicit allowlist, and an anti-vacuity test so the gate cannot be
 * satisfied by deleting the thing it guards.
 *
 * **Comments are stripped before scanning**, and that is load-bearing here more than anywhere else in
 * the repo: this codebase quotes its own Japanese copy in KDoc constantly — `Tier` explains its CHECK
 * constraint by spelling it — and a gate that failed on the explanation would teach the next author
 * to delete the explanation.
 */
class I18nGateTest {

    /**
     * The **only** files permitted to hold a Japanese string literal, each with the reason.
     *
     * This started life as an append-only list of files that had been migrated, which was the right
     * shape while most of the app was still Japanese and the list was a progress bar. It is the wrong
     * shape now: a list of what is *done* cannot see a new file, so a page added next month would be
     * gated by nothing at all. Inverted, the default is "no Japanese" and every exception has to be
     * argued for here, in writing.
     *
     * Every entry is code rather than copy. If you are about to add one for a *sentence*, the answer
     * is the string table.
     */
    private val japaneseAllowed = mapOf(
        "BuiltInCatalog.kt" to
            "seeded rows. The catalogue is stored in SQLite in Japanese and localised at read time " +
            "(CatalogStrings), because the seed is content-addressed over routine names — a " +
            "locale-varying seed would churn a routine_version on every language toggle",
        "AppGlyph.kt" to
            "match keys against *other apps'* display names, which stay Japanese on a Japanese " +
            "device whatever Tempo is set to. Translating them breaks icon resolution",
        "JapaneseDate.kt" to "the kanji numeral and weekday character tables — formatter data",
        "Numerals.kt" to "kanji numeral formatting — JaFormats' internals",
        "LibraryFilters.kt" to
            "the kana-folding conversion tables. Japanese search must keep working under an English " +
            "UI, because routine names are user data and stay as authored",
        "GymModels.kt" to
            "Tier's three storage tokens. The schema CHECK spells them, and SQLite 3.28 cannot alter " +
            "a CHECK without rebuilding a table with mutual foreign keys (§L3)",
        "Schema.kt" to "the tier CHECK constraint itself",
        "Migrations.kt" to "the same tier tokens, in a migration that has already shipped",
        "GymWrite.kt" to "an exception message. Never rendered",
        "LanguageDialog.kt" to "日本語 — the endonym. Deliberately untranslated (§L6)",
        "OnboardingScreen.kt" to "日本語 — the same endonym, on the first-run language row",
        "HomeScreen.kt" to
            "静 — the hanko. A seal is a mark rather than a word, and it is the same mark in both " +
            "languages",
    )

    /**
     * Japanese literals that are **not copy** and must survive in a migrated file.
     *
     * Two kinds, and both are recorded in `.planning/i18n/DECISIONS.md` §L10:
     *
     * - **Endonyms.** 日本語 is the language picker naming itself. It is deliberately untranslated —
     *   a row reading "Japanese" under an English UI is addressed to the one user who does not need
     *   it. This is the only place in the app where an untranslated string is the correct answer.
     * - **Formatter patterns.** `"M月d日"` is a `DateTimeFormatter` pattern, not a sentence. It is a
     *   genuine outstanding hazard (H-16 — it is the only hard-coded date format in the app, and it
     *   disagrees with `JapaneseDate.monthDay` about whether a month-day uses arabic or kanji), but it
     *   belongs to the `fmt.*` layer, not to a string table. Listed here so it is *known* rather than
     *   *missed*, and it comes out when the formatter layer lands.
     */
    private val notCopy = setOf("日本語", "M月d日")

    @Test
    fun `no file outside the allowlist holds a Japanese literal`() {
        val offenders = mainSources()
            .filterKeys { it !in japaneseAllowed }
            .mapValues { (_, source) -> japaneseLiterals(source) }
            .filterValues { it.isNotEmpty() }
            .map { (name, found) -> "$name → ${found.take(4)}" }
            .sorted()

        assertEquals(
            "a Japanese string literal appeared outside the string table. It belongs in the file's " +
                "namespace under i18n/, not in the page. If it is genuinely not copy — a lookup key, " +
                "a stored value, a formatter table, an endonym — add it to `japaneseAllowed` with " +
                "the reason, and read DECISIONS §L10 first, because most candidates are ruled on " +
                "there already",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the allowlist has no stale entries`() {
        // The other half, and the one that keeps the list honest. An entry whose file no longer holds
        // any Japanese is an exemption nobody needs, still granting permission — and the next author
        // to add a sentence to that file gets no complaint from this test.
        val stale = japaneseAllowed.keys
            .filter { name ->
                val source = mainSources()[name]
                source != null && japaneseLiterals(source).isEmpty()
            }
            .sorted()

        assertEquals("an allowlisted file no longer holds Japanese — drop its entry", emptyList<String>(), stale)

        val missing = japaneseAllowed.keys.filterNot { it in mainSources() }.sorted()
        assertEquals("the allowlist names a file that does not exist", emptyList<String>(), missing)
    }

    @Test
    fun `the table itself is not empty`() {
        // The anti-vacuity half. Every assertion above is an *absence*, and an absence passes
        // trivially once the thing is gone: strip the app of all copy and the gate goes green while
        // the product renders nothing. These assertions are what make the absences mean something.
        //
        // **Scans the whole i18n package, not just StringsJa.kt / StringsEn.kt.** An earlier version
        // of this test read those two files only, which was correct when they held every namespace
        // inline and became a hole the moment the namespaces were split into one file each: the two
        // root files then held nothing but delegation, so `EnFault`, `EnCalendar` and fifteen others
        // were gated by nothing at all. The fix is to derive the set from the tree rather than name
        // it — the same reason `GymShellTest`'s orphan check walks the directory.
        val ja = i18nSources().filterKeys { it.startsWith("Strings") || it.startsWith("Ja") }
        assertTrue(
            "no Japanese copy found anywhere in the i18n package — the migration moves strings, it " +
                "does not delete them",
            ja.values.sumOf { japaneseLiterals(it).size } > 20,
        )

        // Every `En<Namespace>` object, wherever it lives. A Japanese literal inside an English
        // implementation is a copy-paste that no compiler catches and no Japanese-language test run
        // would ever surface.
        val leaked = i18nSources()
            .mapValues { (_, source) -> englishBlockJapanese(source).filterNot { it in notCopy } }
            .filterValues { it.isNotEmpty() }
            .map { (name, found) -> "$name → $found" }
            .sorted()

        assertEquals(
            "an English implementation holds a Japanese literal. Either it was never translated, or " +
                "it is genuinely not copy — in which case add it to `notCopy` with the reason",
            emptyList<String>(),
            leaked,
        )
    }

    @Test
    fun `every namespace is implemented in both languages`() {
        // The compiler already refuses a missing member, so this does not re-check that. What it
        // catches is the shape one step out: a namespace file that declares an interface and only one
        // of the two objects, which compiles fine as long as nothing references the missing one — and
        // then someone wires it up later and inherits an empty English table.
        val orphans = i18nSources()
            .filterKeys { it.endsWith("Strings.kt") && it != "Strings.kt" }
            .filterValues { source ->
                val hasJa = Regex("""\bobject\s+Ja\w+""").containsMatchIn(source)
                val hasEn = Regex("""\bobject\s+En\w+""").containsMatchIn(source)
                hasJa != hasEn
            }
            .keys.sorted()

        assertEquals(
            "a namespace file implements one language but not the other",
            emptyList<String>(),
            orphans,
        )
    }

    @Test
    fun `every language resolves to a table, and they are distinct`() {
        // Guards the `when` in `stringsFor`: a third Lang added without a table is a compile error
        // there, but a third Lang *mapped to the wrong table* is not. Comparing a string both tables
        // define catches the copy-paste.
        val tables = Lang.entries.map { stringsFor(it) }
        assertEquals("each Lang must resolve to its own table", Lang.entries.toList(), tables.map { it.lang })
        assertEquals(
            "two languages resolved to the same copy — check the `when` in stringsFor",
            tables.size,
            tables.map { it.app.dockHome }.toSet().size,
        )
    }

    // ─── reading the source ─────────────────────────────────────────────────────────────────────

    /** Every Japanese-bearing string literal in a source file, comments removed first. */
    private fun japaneseLiterals(file: File): List<String> = japaneseLiterals(stripComments(file.readText()))

    private fun japaneseLiterals(code: String): List<String> {
        return LITERAL.findAll(code)
            .map { it.value.trim('"') }
            .filter { JAPANESE.containsMatchIn(it) }
            .distinct()
            .toList()
    }

    /** Every `.kt` in the app's main source set except the string tables, comment-free, by file name. */
    private fun mainSources(): Map<String, String> {
        val root = sourceNamed("Strings.kt").parentFile!!.parentFile!!
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.parentFile?.name != "i18n" }
            .associate { it.name to stripComments(it.readText()) }
    }

    /** Every `.kt` in the i18n package, comment-free, keyed by file name. */
    private fun i18nSources(): Map<String, String> =
        sourceNamed("Strings.kt").parentFile!!
            .listFiles { f: File -> f.isFile && f.extension == "kt" }
            .orEmpty()
            .associate { it.name to stripComments(it.readText()) }

    /**
     * Japanese literals appearing inside an `object En…` declaration.
     *
     * Brace-matched from the object header, because a namespace file holds the interface, the
     * Japanese object and the English object side by side — scanning the whole file would flag every
     * correctly-transcribed Japanese value in `Ja…` as a leak.
     */
    private fun englishBlockJapanese(source: String): List<String> {
        val header = Regex("""\bobject\s+En\w+[^{]*\{""").find(source) ?: return emptyList()
        var depth = 0
        var end = header.range.last
        for (i in header.range.last until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) { end = i; break }
                }
            }
        }
        val body = source.substring(header.range.last, end)
        return LITERAL.findAll(body)
            .map { it.value.trim('"') }
            .filter { JAPANESE.containsMatchIn(it) }
            .distinct()
            .toList()
    }

    private fun stripComments(source: String): String = source
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""//[^\n]*"""), " ")

    /**
     * Locate a source file by name anywhere under the main source set.
     *
     * By file name rather than by path so that moving a page between packages does not silently drop
     * it out of the gate — the thing being pinned is the file, not its address. `AcwrRestraintTest`'s
     * working-directory walk-up, for the same reason it has one: Gradle and IDE runners disagree
     * about the working directory.
     */
    private fun sourceNamed(name: String): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            for (prefix in listOf("app/src/main/java", "src/main/java")) {
                val root = File(dir, prefix)
                if (root.exists()) {
                    root.walkTopDown().firstOrNull { it.isFile && it.name == name }?.let { return it }
                }
            }
            dir = dir.parentFile
        }
        throw IllegalStateException("could not locate $name from ${File("").absolutePath}")
    }

    private companion object {
        val JAPANESE = Regex("[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FFF]")
        /**
         * Both string forms, **raw triple-quoted first** so its body is consumed whole rather than
         * being re-scanned as a run of ordinary literals.
         *
         * The raw form is not an edge case here: `Schema.kt` keeps the entire SQL schema in one, and
         * the tier CHECK constraint spells 入門/中級/上級 inside it. A gate that only understood
         * `"…"` reported that file as clean, which is the one place a Japanese string is load-bearing
         * *and* invisible.
         */
        val LITERAL = Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\"")
    }
}
