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
 * **The list grows, and that is the design.** [MIGRATED] names the files that are done. Adding a file
 * to it is the last step of migrating that file, and from then on the file is pinned. The list is
 * therefore also the honest progress bar: `MIGRATED.size` against the survey's 137.
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
     * Files whose copy has moved into [Strings]. **Append only.**
     *
     * Each entry is a promise that the file contains no user-visible Japanese string literal. Removing
     * an entry to make a build go green is the one edit this test cannot catch, and is exactly the
     * thing it exists to make someone argue for out loud.
     */
    private val migrated = setOf(
        "Dock.kt",
        "ModeDialog.kt",
        "OnboardingScreen.kt",
        "SearchScreen.kt",
        "LanguageDialog.kt",
        "TempoApp.kt",
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
    fun `no migrated file holds a Japanese copy literal`() {
        val offenders = migrated.sorted().mapNotNull { name ->
            val found = japaneseLiterals(sourceNamed(name)).filterNot { it in notCopy }
            if (found.isEmpty()) null else "$name → $found"
        }

        assertEquals(
            "a migrated file grew a Japanese string literal again. It belongs in Strings/StringsJa/" +
                "StringsEn, not in the page. If it is genuinely not copy — an endonym, a formatter " +
                "pattern, a lookup key — add it to `notCopy` with the reason, and check " +
                "DECISIONS §L10 first, because most candidates are already ruled on there",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the table itself is not empty`() {
        // The anti-vacuity half. Every assertion above is an *absence*, and an absence passes
        // trivially once the thing is gone: strip the app of all copy and the gate goes green while
        // the product renders nothing. These two assertions are what make the absences mean something.
        assertTrue(
            "StringsJa must hold Japanese copy — the migration moves strings, it does not delete them",
            japaneseLiterals(sourceNamed("StringsJa.kt")).size > 20,
        )
        assertTrue(
            "StringsEn must NOT hold Japanese copy beyond the documented endonyms",
            japaneseLiterals(sourceNamed("StringsEn.kt")).all { it in notCopy },
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

    @Test
    fun `the migration list is honest`() {
        // A named file that does not exist is a silent hole: it can never fail, so it reads as
        // progress while guarding nothing. This is the failure mode of every hand-maintained list.
        val missing = migrated.filter { runCatching { sourceNamed(it) }.isFailure }
        assertEquals("MIGRATED names a file that does not exist", emptyList<String>(), missing.sorted())
    }

    // ─── reading the source ─────────────────────────────────────────────────────────────────────

    /** Every Japanese-bearing string literal in a source file, comments removed first. */
    private fun japaneseLiterals(file: File): List<String> {
        val code = stripComments(file.readText())
        return LITERAL.findAll(code)
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
        val LITERAL = Regex(""""(?:\\.|[^"\\\n])*"""")
    }
}
