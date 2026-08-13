package io.eddiegulay.tempo.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * The foreground notification's words and, more importantly, **when it changes** — `gym/TrainingNotice.kt`.
 *
 * The second half is the reason this file exists. "Must not tick every second" is easy to assert about
 * a data class and impossible to assert about a `NotificationManager`, so the property is enforced by
 * the type — a notice built from two different frames of the same segment is the *same value*.
 *
 * **The tripwire is [`the notice carries no field that can tick`], and it had to be rewritten to be
 * one.** It used to assert `notice() == notice()`, which is true of every data class in existence and
 * detects nothing: the threat it names is someone adding a `remainingMs`, and a fifth constructor
 * parameter with a default value leaves every call site and that assertion untouched. A test that
 * cannot fail for the reason it was written is worse than no test, because `TrainingService.kt`'s
 * header points a future agent at it. So the assertion is over the type's declared properties.
 */
class TrainingNoticeTest {

    private fun notice(
        routine: String = "七分間",
        phase: Phase = Phase.WORK,
        paused: Boolean = false,
        exercise: String? = "腕立て伏せ",
    ) = TrainingNotice(routine, phase, paused, exercise)

    // ── the no-ticking property ───────────────────────────────────────────────────────────────────

    @Test
    fun `the notice carries no field that can tick`() {
        // **This is the tripwire, and it has to look at the shape of the type rather than at two of
        // its values.** The obvious assertion — `assertEquals(notice(), notice())` — is true of every
        // data class ever written and says nothing at all about whether a time field exists: add a
        // fifth parameter `remainingMs: Long = 0` and it compiles unchanged, every call site keeps
        // working, and the notification starts being re-posted every second with the battery and jank
        // problem that `TrainingService` property 2 exists to prevent. Nothing else in the tree would
        // have noticed.
        // Instance fields only: the Compose compiler adds a static `$stable` to every class it sees,
        // and a field that ticks is by definition per-notice.
        val fields = TrainingNotice::class.java.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .map { it.name }

        assertEquals(listOf("routineName", "phase", "paused", "exerciseName"), fields)

        // The same claim in words, so the failure message says *why* rather than only *what*.
        val ticking = fields.map { it.lowercase() }.filter { name ->
            listOf("ms", "millis", "elapsed", "remaining", "fraction", "second", "time").any(name::contains)
        }
        assertEquals("a notification that ticks is a battery bug: $fields", emptyList<String>(), ticking)
    }

    @Test
    fun `two frames of the same segment are the same notice, so nothing is re-posted between them`() {
        // The companion to the test above, and only the companion: this is the property the user
        // experiences, that one is the property that keeps it true.
        assertEquals(notice(), notice())
    }

    @Test
    fun `a phase transition, a pause and a new station are each a different notice`() {
        val running = notice()
        assertNotEquals(running, notice(phase = Phase.REST, exercise = null))
        assertNotEquals(running, notice(paused = true))
        assertNotEquals(running, notice(exercise = "プランク"))
    }

    // ── phase labels ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `each phase reads as its own page's heading`() {
        assertEquals("支度", noticePhaseLabel(Phase.PREPARE, paused = false))
        assertEquals("運動", noticePhaseLabel(Phase.WORK, paused = false))
        assertEquals("運動・回数", noticePhaseLabel(Phase.REPS, paused = false))
        assertEquals("休息", noticePhaseLabel(Phase.REST, paused = false))
        assertEquals("記録", noticePhaseLabel(Phase.COMPLETE, paused = false))
    }

    @Test
    fun `a pause overrides every phase, because the fact that changed is that the clock stopped`() {
        Phase.entries.forEach { phase ->
            assertEquals(phase.name, "休止", noticePhaseLabel(phase, paused = true))
        }
    }

    @Test
    fun `no phase falls through to a default, so a sixth one breaks the build`() {
        // The labels were a flat `when { paused -> …; phase == … }` chain closed by `else -> "記録"`,
        // which is a bare `else` over an enum: a `Phase` added in Phase 4 would have rendered silently
        // as 記録 on a live notification rather than failing to compile. Exhaustiveness is not a thing
        // a test can assert about a compiled function, so it is asserted about the source — the same
        // technique, and the same class of invisible failure, as `TrainingManifestTest`.
        val body = declarationBody("fun noticePhaseLabel(")
        assertTrue("a bare else over Phase forfeits exhaustiveness: $body", !body.contains("else ->"))
        Phase.entries.forEach { phase ->
            assertTrue("$phase must have its own arm: $body", body.contains("Phase.${phase.name} ->"))
        }
    }

    // ── title and text ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the title is the routine, the same word the ensō carries`() {
        assertEquals("七分間", noticeTitle(notice()))
    }

    @Test
    fun `a running station names its movement after the phase`() {
        assertEquals("運動 ・ 腕立て伏せ", noticeText(notice()))
        assertEquals("運動・回数 ・ 腕立て伏せ", noticeText(notice(phase = Phase.REPS)))
    }

    @Test
    fun `a phase with no movement says only the phase`() {
        assertEquals("休息", noticeText(notice(phase = Phase.REST, exercise = null)))
        assertEquals("支度", noticeText(notice(phase = Phase.PREPARE, exercise = null)))
    }

    @Test
    fun `a paused session drops the movement, because 休止 is the whole of what is true`() {
        // 「休止 ・ 腕立て伏せ」 would read as a push-up in progress.
        assertEquals("休止", noticeText(notice(paused = true)))
    }

    @Test
    fun `a blank exercise name is treated as no exercise rather than a dangling separator`() {
        assertEquals("運動", noticeText(notice(exercise = "")))
    }

    // ── the control ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the control is always the reversible one and always the opposite of the clock`() {
        assertEquals(TrainingControl.PAUSE, noticeControl(notice()))
        assertEquals(TrainingControl.RESUME, noticeControl(notice(paused = true)))
    }

    @Test
    fun `the control's words are the player's own`() {
        // `03-player.md` §A WORK, accessibility: ┃┃ = 休止. `PausedPage` answers with 続ける.
        assertEquals("休止", noticeControlLabel(TrainingControl.PAUSE))
        assertEquals("続ける", noticeControlLabel(TrainingControl.RESUME))
    }

    @Test
    fun `no control is とばす and no control is 鍛錬を終える`() {
        // Both were considered and dropped: a skip writes a SegmentResult with no ensō to watch it go,
        // and a discard is the one thing `03` §A QUIT_SHEET makes the user confirm twice.
        val words = TrainingControl.entries.map { noticeControlLabel(it) }
        assertTrue(words.toString(), words.none { it.contains("とばす") || it.contains("終え") })
    }

    // ── the spoken form ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `the notification reads as one sentence, routine first`() {
        assertEquals("七分間、運動 ・ 腕立て伏せ", noticeSemantics(notice()))
    }

    // ── Source access ─────────────────────────────────────────────────────────────────────────────

    private val source: String by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            for (prefix in listOf("app/src/main/java/io/eddiegulay/tempo/", "src/main/java/io/eddiegulay/tempo/")) {
                File(dir, prefix + "gym/TrainingNotice.kt").takeIf { it.isFile }?.let { return@lazy it.readText() }
            }
            dir = dir.parentFile
        }
        throw AssertionError("TrainingNotice.kt not found from ${File("").absolutePath}")
    }

    private fun declarationBody(header: String): String {
        val at = source.indexOf(header)
        check(at >= 0) { "not found: $header" }
        val open = source.indexOf('{', at)
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
}
