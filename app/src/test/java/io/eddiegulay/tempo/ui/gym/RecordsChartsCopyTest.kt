package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.calendar.Loadable
import io.eddiegulay.tempo.data.GymFault
import io.eddiegulay.tempo.gym.DayVolume
import io.eddiegulay.tempo.gym.TrainingLoad
import io.eddiegulay.tempo.gym.WeekPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The two decisions `GYM.RECORDS.CHARTS` makes for itself: whether 積み上げ has earned its place, and
 * where a day's tick belongs when the query returned no row for it.
 *
 * Everything else on that page is `gym/ChartGeometry.kt`'s and is tested there. What is here is the
 * seam between a store that answers per *session* and a chart that draws per *day* — the place where a
 * fortnight off silently closes up if nobody is watching — and the gate that must not confuse
 * "too early" with "nothing" or with "broken".
 */
class RecordsChartsCopyTest {

    // ─── The 28-day gate ────────────────────────────────────────────────────────────────────────

    @Test
    fun `an unread gate draws nothing about the user's history`() {
        // The trap this closes: collapsing an in-flight read into "suppressed" puts
        // 二十八日ぶん たまると 出ます in front of a user with two years of training for as long as the
        // query takes — a claim about their history assembled out of a pending read.
        assertEquals(VolumeGate.Loading, volumeGate(Loadable.Loading, trained))
    }

    @Test
    fun `a failed gate reports the store, never the suppression sentence`() {
        val gate = volumeGate(Loadable.Failed(GymFault.StoreCorrupt), trained)
        assertTrue(gate is VolumeGate.Failed)
        assertEquals(GymFault.StoreCorrupt, (gate as VolumeGate.Failed).fault)
    }

    @Test
    fun `below twenty-eight days the chart is suppressed with §6's own sentence`() {
        // Design §7.4: load surfaces stay suppressed until 28 days, because a weighted-volume trend
        // over six days is noise presented as insight.
        val gate = volumeGate(Loadable.Ready(load(historyDays = 27)), trained)
        assertEquals("二十八日ぶん たまると 出ます", (gate as VolumeGate.Suppressed).message)
    }

    @Test
    fun `twenty-eight days exactly opens the gate`() {
        assertEquals(VolumeGate.Open, volumeGate(Loadable.Ready(load(historyDays = 28)), trained))
    }

    @Test
    fun `a returning user is never told to accumulate history they already have`() {
        // `DECISIONS.md` §Q24, and the defect it was written over. `GymStore.trainingLoad()` answers
        // `Ready(null)` whenever the trailing 28 days are empty and never computes `historyDays` in
        // that branch, so a user with years of training who came back after a month away was told
        // 二十八日ぶん たまると 出ます — false for them, and it hid sessions `volumeSeries` had already
        // fetched (it queries 83 days back). A finished session exists and none of it is in the last
        // 28 days, therefore the first one is at least 28 days old: the gate is earned, provably.
        assertEquals(VolumeGate.Open, volumeGate(Loadable.Ready(null), Loadable.Ready(true)))
    }

    @Test
    fun `a user who has never trained keeps the suppression sentence`() {
        // The other half of the null case, and the one the sentence is true for.
        val gate = volumeGate(Loadable.Ready(null), Loadable.Ready(false))
        assertEquals("二十八日ぶん たまると 出ます", (gate as VolumeGate.Suppressed).message)
    }

    @Test
    fun `an unresolved everTrained is a wait, never a verdict about the user`() {
        // The gate's four states exist so an unknown answer is never rendered as a claim. An unread
        // feed waits; an unreadable one reports the store, which is what `VolumeGate.Failed` is for.
        assertEquals(VolumeGate.Loading, volumeGate(Loadable.Ready(null), Loadable.Loading))

        val failed = volumeGate(Loadable.Ready(null), Loadable.Failed(GymFault.StoreCorrupt))
        assertEquals(GymFault.StoreCorrupt, (failed as VolumeGate.Failed).fault)
    }

    // ─── The daily spine ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a rest day becomes a zero rather than closing the gap up`() {
        // `volumeSeries` is a GROUP BY, so a rest day produces no row. Drawing the list directly would
        // space the ticks by session rather than by day — and `trailingMean(daily, 7)` would then be
        // averaging seven *training* days, which is not a seven-day mean of anything.
        val spine = volumeSpine(
            series = listOf(
                DayVolume(LocalDate.of(2026, 6, 8), 12.0),
                DayVolume(LocalDate.of(2026, 6, 10), 4.0),
            ),
            days = 5,
            end = LocalDate.of(2026, 6, 10),
        )

        assertEquals(listOf(0.0, 0.0, 12.0, 0.0, 4.0), spine)
    }

    @Test
    fun `the spine is exactly as long as the range asks for`() {
        assertEquals(84, volumeSpine(emptyList(), days = 84, end = LocalDate.of(2026, 6, 10)).size)
        assertTrue(volumeSpine(emptyList(), days = 0, end = LocalDate.of(2026, 6, 10)).isEmpty())
    }

    @Test
    fun `a recorded day later than the supplied today is never dropped off the chart`() {
        // The end of the spine is the device's local date, and the repository's guarded clock is not
        // exposed. Extending to the last day that carries data bounds the damage to a longer empty
        // tail — a wound-back clock can lengthen the chart, never silently delete a session from it.
        val spine = volumeSpine(
            series = listOf(DayVolume(LocalDate.of(2026, 6, 10), 4.0)),
            days = 3,
            end = LocalDate.of(2026, 6, 8),
        )

        assertEquals(listOf(0.0, 0.0, 4.0), spine)
    }

    @Test
    fun `a day older than the window does not shift the days that are in it`() {
        val spine = volumeSpine(
            series = listOf(
                DayVolume(LocalDate.of(2026, 1, 1), 99.0),
                DayVolume(LocalDate.of(2026, 6, 10), 4.0),
            ),
            days = 3,
            end = LocalDate.of(2026, 6, 10),
        )

        assertEquals(listOf(0.0, 0.0, 4.0), spine)
    }

    // ─── The weekly series ──────────────────────────────────────────────────────────────────────

    @Test
    fun `a week with no sessions is a measured zero, never a gap`() {
        // `zeroFilledWeeks` exists to make an untrained week a zero rather than an absence, and
        // `linePoints` treats null as a gap and zero as a value. Reading the series as nullable here
        // would break the line at every quiet week and claim the data was missing.
        val points = listOf(week(sessions = 2, minutes = 40), week(sessions = 0, minutes = 0))

        assertEquals(listOf(2, 0), weeklySessionValues(points))
        assertEquals(listOf(40.0, 0.0), weeklyMinuteValues(points))
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────────────────────────

    /** The ordinary case for every test that is not about the gate's tie-breaker. */
    private val trained: Loadable<Boolean> = Loadable.Ready(true)

    private fun load(historyDays: Int) = TrainingLoad(
        recent = emptyList(),
        monotony7d = null,
        acwr = null,
        historyDays = historyDays,
    )

    private fun week(sessions: Int, minutes: Int) = WeekPoint(
        weekStart = LocalDate.of(2026, 3, 30),
        sessions = sessions,
        activeMinutes = minutes,
        completeSessions = sessions,
    )
}
