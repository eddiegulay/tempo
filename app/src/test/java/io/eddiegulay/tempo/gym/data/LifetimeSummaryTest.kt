package io.eddiegulay.tempo.gym.data

import io.eddiegulay.tempo.gym.GymRepository
import io.eddiegulay.tempo.gym.GymViewModel
import io.eddiegulay.tempo.gym.LifetimeSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `summary()` — the read `02-data.md` §C never declared and two shipped pages cannot draw without.
 *
 * `04-library-records.md` §4's mock and §6's tile table both specify **three** tiles on
 * `GYM.RECORDS.INDEX` (今月 / 活動時間 / これまで), and §4's HISTORY mock carries the subtitle
 * 八十六回 ・ 二千四百分. Both are lifetime totals; nothing on [GymRepository] could answer them, so the
 * INDEX page shipped two tiles and the HISTORY page shipped no subtitle. Both refusals were correct —
 * `DECISIONS.md` §Q22 examined the three available derivations and every one is wrong by a different
 * amount — and §Q22 is the ruling that adds the read.
 *
 * What is pinned here is the same thing `GymApiContractTest` pins and for the same reason: `GymStore`
 * is an `SQLiteOpenHelper` and there is no Robolectric on this classpath (`00-plan.md` §2), so the
 * query cannot be run. The defect was never an arithmetic error — it was a **missing signature** and,
 * once written, the difference between a total that can say "I could not read this" and one that
 * cannot. Both are visible without a database: one by reflection, one by reading the source.
 */
class LifetimeSummaryTest {

    @Test
    fun `the repository can be asked what has ever been done`() {
        val read = GymRepository::class.java.methods.singleOrNull { it.name == "summary" }
        assertTrue("GymRepository must declare summary()", read != null)
        // The `Continuation` only — it takes no window, no range and no routine. A lifetime total that
        // accepted a window would be the same mistake `volumeGate` made one page over (§Q24): an
        // absence of *recent* rows reported as an absence of history.
        assertEquals(1, read!!.parameterCount)
    }

    @Test
    fun `a lifetime total that cannot be read must not arrive as zero`() {
        // The doctrine, as a signature. 〇回 is a claim about the user's life, and a `LifetimeSummary`
        // returned bare would make "never trained" and "the store is unreadable" the same value on the
        // one tile nobody can sanity-check. This feature has now caught that collapse five times, which
        // is why §Q22 spends its last paragraph on it.
        // Read from the source, not by reflection: a `suspend` function's compiled return type is
        // `Object` — the value comes back through the `Continuation` — so the one thing this test is
        // about is the one thing the classpath cannot see. `countForRoutine` could be reflected because
        // it is a `Flow`; this cannot.
        val declaration = snippet(readSource("gym/GymRepository.kt"), "suspend fun summary(", chars = 60)
        assertTrue(
            "summary() must be a Loadable, not a bare total: $declaration",
            declaration.contains("Loadable<LifetimeSummary>"),
        )

        val exposed = GymViewModel::class.java.getMethod("getLifetimeSummary")
        assertTrue(
            "the ViewModel must not unwrap it either: ${exposed.genericReturnType.typeName}",
            exposed.genericReturnType.typeName.contains("Loadable"),
        )
        assertTrue(
            "and it must carry LifetimeSummary: ${exposed.genericReturnType.typeName}",
            exposed.genericReturnType.typeName.contains(LifetimeSummary::class.java.name),
        )
    }

    @Test
    fun `subscribing performs the read, so neither page can leave the tile on 読み込み中`() {
        // A bare `suspend` call would have made each page responsible for kicking its own
        // `LaunchedEffect`, and a page that forgets renders 読み込み中 forever — the same collapse as an
        // unread store rendering 記録はありません, wearing loading's clothes instead of empty's. `stateIn`
        // means the subscription *is* the request.
        val body = snippet(readSource("gym/GymViewModel.kt"), "val lifetimeSummary")
        assertTrue("lifetimeSummary must read through the repository: $body", body.contains("repository.summary()"))
        assertTrue("and be shared rather than re-read per collector: $body", body.contains("stateIn("))
        assertTrue("seeded Loading, never a zero total: $body", body.contains("Loadable.Loading"))
    }

    @Test
    fun `the store answers it in one scan, and a quarantined store refuses to answer at all`() {
        val body = snippet(readSource("gym/data/GymStore.kt"), "override suspend fun summary(", chars = 700)

        // One pass over `session`: `COUNT(*)` and `SUM(active_ms)`, no new table and no schema change.
        assertTrue("summary must count sessions: $body", body.contains("COUNT(*)"))
        assertTrue("summary must sum active_ms: $body", body.contains("SUM(active_ms)"))
        // SUM over zero rows is NULL, not 0 — a first-run user reads the null, not an empty total.
        assertTrue("SUM over no rows is NULL and must be coalesced: $body", body.contains("COALESCE"))
        // An open session is a workout in progress, not a record of one; counting it would make これまで
        // tick upward mid-session.
        assertTrue("finished sessions only: $body", body.contains("finished_at IS NOT NULL"))

        // The load-bearing one. `read` answers honestly over a quarantined database — which has been
        // emptied — and honest is exactly wrong here: `Ready(LifetimeSummary(0, 0))` renders 〇回 over a
        // destroyed decade. `readHistory` reports `StoreCorrupt` instead, as `observeHistory` does for
        // every flow-shaped history read (§E.6, §Q22).
        assertTrue("summary must go through readHistory, never plain read: $body", body.contains("readHistory"))
    }

    // ─── reading the source ─────────────────────────────────────────────────────────────────────

    /**
     * The declaration and what follows it, up to [chars].
     *
     * A brace-match would be wrong for both of these: the store's `summary()` is `readHistory { … }`,
     * so the helper that matters is *outside* the first brace, and the ViewModel's `lifetimeSummary` is
     * a property whose first brace belongs to a `map` lambda. A window is the honest tool, and it
     * throws rather than silently matching nothing when the declaration moves.
     */
    private fun snippet(source: String, header: String, chars: Int = 400): String {
        val start = source.indexOf(header)
        require(start >= 0) { "declaration not found: $header" }
        return source.substring(start, minOf(source.length, start + chars))
    }

    private fun readSource(relative: String): String {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            for (prefix in listOf("app/src/main/java/io/eddiegulay/tempo/", "src/main/java/io/eddiegulay/tempo/")) {
                val candidate = File(dir, prefix + relative)
                if (candidate.exists()) return candidate.readText()
            }
            dir = dir.parentFile
        }
        throw IllegalStateException("could not locate $relative from ${File("").absolutePath}")
    }
}
