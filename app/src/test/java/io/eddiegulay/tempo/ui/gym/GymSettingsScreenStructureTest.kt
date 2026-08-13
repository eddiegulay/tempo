package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.gym.GymPreferences
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.gym.SpeechAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The three facts about `GYM.SETTINGS` and `GYM.SAFETY` that no return value can express.
 *
 * Each of them is a defect that shipped, and each is a rule about *where* something runs or *whether*
 * it runs at all:
 *
 * 1. **A preference write must outlive the page that started it.** `01-shell.md` :768 — "a row is never
 *    left showing a value that was not persisted" — is unkeepable on a scope that dies with the row.
 * 2. **`GYM.SAFETY` writes nothing.** §B GYM.SAFETY's Data line allows the acknowledgement only when the
 *    page is reached from the first-run note, and this page cannot tell.
 * 3. **The disabled 音声 row announces in §B's order**, which is a semantics *shape*, not a string.
 *
 * **Why the source text and not the composition.** This module's unit-test classpath is `junit` and
 * nothing else (`app/build.gradle.kts`): no Robolectric, no `compose-ui-test`, no
 * `kotlinx-coroutines-test`, and `00-plan.md` §2 forbids adding any of them. `ExerciseScreenStructureTest`
 * and `LibraryIndexScreenStructureTest` made the same trade for the same reason and the helpers below
 * are theirs. The assertions are about the presence of a call inside a named declaration, never about
 * formatting, so reindenting or renaming a local leaves them green. Where a shape *is* reachable — the
 * ViewModel's new method — it is pinned by reflection instead, as `GymApiContractTest` does.
 */
class GymSettingsScreenStructureTest {

    private val settings: String by lazy { readSource("ui/gym/GymSettingsScreen.kt") }
    private val safety: String by lazy { readSource("ui/gym/GymSafetyScreen.kt") }
    private val home: String by lazy { readSource("ui/gym/GymHomeScreen.kt") }
    private val viewModel: String by lazy { readSource("gym/GymViewModel.kt") }

    // ─── The write must outlive the page ────────────────────────────────────────────────────────

    @Test
    fun `a preference write runs on the ViewModel's scope, never on the composition's`() {
        // The defect: every row wrote on `rememberCoroutineScope()`, which is cancelled the instant the
        // page leaves composition. A tap followed by Back, by 安全のために or by a HOME press
        // (`onLeaveShell` rebases the stack and disposes the page) cancelled the `DataStore.edit` before
        // it committed — the row had already shown 入, nothing was persisted, and the page that owed the
        // revert and 保存できませんでした was gone. `BuilderScreen`'s save and every session write here
        // already state the rule; this page was the one path that did not follow it.
        val writer = declarationBody(settings, "private fun rememberSettingsWriter(")

        assertTrue(
            "the writer must delegate to GymViewModel.writePreference",
            writer.contains("gym::writePreference"),
        )
        assertTrue(
            "no composition scope may be created on this page",
            !settings.contains("rememberCoroutineScope()"),
        )
        assertTrue(
            "…and nothing on it may launch on one",
            !Regex("\\bscope\\.launch").containsMatchIn(settings),
        )
    }

    @Test
    fun `writePreference exists on the ViewModel and launches on viewModelScope`() {
        val write = GymViewModel::class.java.methods.single { it.name == "writePreference" }
        assertEquals("a body to run and a revert to run if it fails", 2, write.parameterCount)

        val body = declarationBody(viewModel, "fun writePreference(")
        assertTrue("the write runs on viewModelScope", body.contains("viewModelScope.launch"))
    }

    @Test
    fun `a cancellation is never reported to the user as a failed write`() {
        // `runCatching` swallowed the CancellationException and reported it as a store refusal. Now the
        // write cannot be cancelled by leaving the page at all — and if `viewModelScope` itself dies with
        // the Activity, there is no row left to revert and nobody to read 保存できませんでした, so the
        // cancellation is re-thrown rather than turned into a fault.
        val body = declarationBody(viewModel, "fun writePreference(")

        assertTrue("cancellation is caught apart from failure", body.contains("CancellationException"))
        assertTrue("…and re-thrown", Regex("throw\\s+\\w+").containsMatchIn(body))
        assertTrue(
            "the page must not re-wrap the body in a catch of its own",
            !declarationBody(settings, "private class SettingsWriter(").contains("runCatching"),
        )
    }

    // ─── GYM.SAFETY writes nothing ──────────────────────────────────────────────────────────────

    @Test
    fun `the safety page never acknowledges the first-run note itself`() {
        // §B GYM.SAFETY's Data line: "none, except setSafetyNoteAcknowledged() when reached from the
        // first-run note". Writing it on arrival whenever the stored flag was false meant a new user who
        // opened 鍛錬 → 設定 → 安全のために permanently suppressed the 痛みを感じたらやめる line on
        // GYM.HOME that they were meant to see before their first workout.
        val page = declarationBody(safety, "fun GymSafetyScreen(")

        assertTrue("no write on arrival", !page.contains("setSafetyNoteAcknowledged"))
        assertTrue("nothing to run on arrival at all", !page.contains("LaunchedEffect"))
        assertTrue("and no preference write of any kind", !page.contains("gym.preferences"))
    }

    @Test
    fun `the note itself is what carries the acknowledgement`() {
        // The other half of the rule, and the reason deleting the page's write loses nothing: GYM.HOME's
        // first-run footnote writes the flag on both of its exits — 開く before it navigates here, and
        // the dismissal. That is the mechanism §B specifies.
        assertEquals(
            "the footnote acknowledges on open and on dismiss",
            2,
            Regex("setSafetyNoteAcknowledged\\(\\)").findAll(
                declarationBody(home, "fun GymHomeScreen("),
            ).count(),
        )
    }

    // ─── What TalkBack hears on a toggle ────────────────────────────────────────────────────────

    @Test
    fun `a toggle row announces its label, then its state, then its reason`() {
        // §B pins the disabled row verbatim: 「音声、切、日本語の音声が入っていません」.
        // `semantics(mergeDescendants = true)` builds the node's name from its children in *visual*
        // order and appends the state description last, so the row announced the reason before the state
        // it explains. `settingsRowDescription` was written for this shape and was used only by ValueRow.
        val row = declarationBody(settings, "private fun ToggleRow(")

        assertTrue("the row owns its whole announcement", row.contains("clearAndSetSemantics {"))
        assertTrue("children must not compose it in visual order", !row.contains("mergeDescendants"))
        assertTrue(
            "and it is assembled by the one helper the spec string is pinned against",
            row.contains("contentDescription = settingsRowDescription(row.label, word, state.subtitle)"),
        )
    }

    @Test
    fun `the 音声 row still draws the stored flag and not the effective one`() {
        // `DECISIONS.md` §Q2: speech is auto-enabled under TalkBack **without writing the preference**,
        // so a row showing the effective value would appear to switch itself on and switching it off
        // again would do nothing visible. The semantics rewrite above touches the row's announcement and
        // must not touch what it announces *about* — the word comes from `prefs.speech`.
        // The page's KDoc names `EffectiveGymPreferences` to say it is not read, so the assertion is on
        // the composable's body rather than on the file.
        val page = declarationBody(settings, "fun GymSettingsScreen(")

        assertTrue("the page reads the stored preferences and nothing else", !page.contains("Effective"))
        assertTrue(
            "the 音声 row is wired to the stored flag",
            page.contains("ToggleRow(SettingRow.Speech, prefs.speech, rows)"),
        )
    }

    @Test
    fun `the disabled speech row assembles §B's sentence from the values the row is wired with`() {
        // The value half of the test above: the three arguments the composable passes, run through the
        // same helper, must be §B's string exactly — label, state, reason.
        val state = settingsRowStates(
            prefs = GymPreferences(),
            speech = SpeechAvailability.NoJapaneseVoice,
            sessionLive = false,
        )[SettingRow.Speech]!!

        assertEquals(
            "音声、切、日本語の音声が入っていません",
            settingsRowDescription(SettingRow.Speech.label, toggleWord(false), state.subtitle),
        )
    }

    // ─── reading the source ─────────────────────────────────────────────────────────────────────

    /** The declaration's body, brace-matched from its header — `LibraryIndexScreenStructureTest`'s. */
    private fun declarationBody(source: String, header: String): String {
        val start = source.indexOf(header)
        require(start >= 0) { "declaration not found: $header" }
        val open = source.indexOf('{', start)
        require(open >= 0) { "no body for: $header" }
        var depth = 0
        for (i in open until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open + 1, i)
                }
            }
        }
        throw IllegalStateException("unbalanced braces reading: $header")
    }

    private fun readSource(relative: String): String {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "app/src/main/java/io/eddiegulay/tempo/$relative")
            if (candidate.isFile) return candidate.readText()
            val inModule = File(dir, "src/main/java/io/eddiegulay/tempo/$relative")
            if (inModule.isFile) return inModule.readText()
            dir = dir.parentFile
        }
        throw IllegalStateException("could not locate $relative from ${File("").absolutePath}")
    }
}
