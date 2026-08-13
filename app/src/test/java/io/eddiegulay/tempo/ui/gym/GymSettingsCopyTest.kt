package io.eddiegulay.tempo.ui.gym

import io.eddiegulay.tempo.gym.GymPreferences
import io.eddiegulay.tempo.gym.RestSlot
import io.eddiegulay.tempo.gym.SpeechAvailability
import io.eddiegulay.tempo.gym.Units
import io.eddiegulay.tempo.gym.clampPrepareSeconds
import io.eddiegulay.tempo.gym.clampRoundRest
import io.eddiegulay.tempo.gym.clampStationRest
import io.eddiegulay.tempo.gym.restOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GYM.SETTINGS`' decisions, checked without a composable.
 *
 * The three that carry the most weight, and why each is here rather than left to the page:
 *
 * 1. **音声 reflects the stored flag** (`DECISIONS.md` §Q2). It is the one row whose correct behaviour
 *    looks like a bug — the toggle stays 切 while the player speaks — so a test that fixes it in place
 *    is the only thing standing between it and a well-meaning "fix".
 * 2. **Unknown is not unavailable.** A TTS probe answers late, and a null availability greying the row
 *    would be the `Loadable` doctrine broken on a settings page.
 * 3. **六十秒 stays 六十秒** (§Q10). Three files now spell that rule and each pins it in its own test.
 */
class GymSettingsCopyTest {

    private val defaults = GymPreferences()

    private fun states(
        prefs: GymPreferences = defaults,
        speech: SpeechAvailability? = SpeechAvailability.Available,
        sessionLive: Boolean = false,
        tonesJustEnabled: Boolean = false,
        touchExploration: Boolean = false,
    ) = settingsRowStates(prefs, speech, sessionLive, tonesJustEnabled, touchExploration)

    // ─── 音声 ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an unprobed speech engine leaves the row live rather than greying it`() {
        val row = states(speech = null)[SettingRow.Speech]!!
        assertTrue(row.enabled)
        assertEquals("種目の名前を読み上げる", row.subtitle)
    }

    @Test
    fun `a missing Japanese voice disables the row and says which absence it is`() {
        val row = states(speech = SpeechAvailability.NoJapaneseVoice)[SettingRow.Speech]!!
        assertFalse(row.enabled)
        assertEquals("日本語の音声が入っていません", row.subtitle)
    }

    @Test
    fun `no engine at all is a different sentence from no voice`() {
        val row = states(speech = SpeechAvailability.NoEngine)[SettingRow.Speech]!!
        assertFalse(row.enabled)
        assertEquals("読み上げ機能がありません", row.subtitle)
    }

    @Test
    fun `touch exploration never enables the row it only explains it`() {
        // §Q2's whole point: the stored flag is false, TalkBack speaks anyway, and the toggle must not
        // appear to have switched itself on. `RowState` carries no on-ness, so the assertion that
        // matters is that nothing here consults the *effective* value — the page reads `prefs.speech`.
        val overridden = states(touchExploration = true)[SettingRow.Speech]!!
        assertTrue(overridden.enabled)
        assertNotNull(overridden.subtitle)
        assertFalse(defaults.speech)
    }

    @Test
    fun `a row the user has already switched on needs no explaining`() {
        val row = states(prefs = defaults.copy(speech = true))[SettingRow.Speech]!!
        assertNull(row.subtitle)
    }

    // ─── The other sub-lines ────────────────────────────────────────────────────────────────────

    @Test
    fun `only the durations a live session has already compiled carry the session notice`() {
        val live = states(sessionLive = true)
        val notice = "いまの鍛錬には反映されません"
        assertEquals(notice, live[SettingRow.PrepareSeconds]!!.subtitle)
        assertEquals(notice, live[SettingRow.StationRest]!!.subtitle)
        assertEquals(notice, live[SettingRow.RoundRest]!!.subtitle)
        // 合図 and 表示 take effect immediately mid-session, which is why Settings is reachable at all.
        assertNull(live[SettingRow.Haptics]!!.subtitle)
        assertEquals("運動中だけ", live[SettingRow.KeepScreenOn]!!.subtitle)
    }

    @Test
    fun `the silent-mode line appears on enabling sound and not before`() {
        assertNull(states()[SettingRow.Tones]!!.subtitle)
        assertEquals("マナーモードでも鳴ります", states(tonesJustEnabled = true)[SettingRow.Tones]!!.subtitle)
    }

    @Test
    fun `every row has a state, so the page never falls back to a default it invented`() {
        val states = states()
        for (row in SettingRow.entries) assertNotNull("no state for $row", states[row])
    }

    // ─── The words ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a toggle is a word and the word is the state description`() {
        assertEquals("入", toggleWord(true))
        assertEquals("切", toggleWord(false))
    }

    @Test
    fun `units cycle rather than opening a picker`() {
        assertEquals(Units.Imperial, nextUnits(Units.Metric))
        assertEquals(Units.Metric, nextUnits(Units.Imperial))
    }

    @Test
    fun `a value row reads as its label then its value`() {
        assertEquals("支度の長さ、五秒", settingsRowDescription("支度の長さ", "五秒"))
        assertEquals(
            "音声、切、日本語の音声が入っていません",
            settingsRowDescription("音声", "切", "日本語の音声が入っていません"),
        )
    }

    // ─── The wheels ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a rest the user chose renders as the seconds they chose`() {
        // `DECISIONS.md` §Q10, pinned here as `BuilderDraft` and `EngineRows` pin it in theirs: 60 is
        // 六十秒 on the settings row, on the builder's wheel and on the detail page — never 一分.
        assertEquals("六十秒", settingsSecondsLabel(60))
        assertEquals("五秒", settingsSecondsLabel(5))
        assertEquals("なし", settingsSecondsLabel(0))
        assertEquals("なし", settingsSecondsLabel(-1))
    }

    @Test
    fun `wheelSteps walks the range and refuses to hang on a zero step`() {
        assertEquals(listOf(0, 5, 10, 15), wheelSteps(0..15, 5))
        assertEquals(16, wheelSteps(0..15, 1).size)
        assertEquals(emptyList<Int>(), wheelSteps(0..15, 0))
        assertEquals(emptyList<Int>(), wheelSteps(0..15, -5))
    }

    @Test
    fun `the prepare wheel spans exactly the bound the store clamps to`() {
        val values = prepareOptions().map { it.value }
        assertEquals(clampPrepareSeconds(0), values.first())
        assertEquals(clampPrepareSeconds(Int.MAX_VALUE), values.last())
        // Every offered value survives the clamp, so a row can never show what was not written.
        for (v in values) assertEquals(v, clampPrepareSeconds(v))
        assertEquals("なし", prepareOptions().first().label)
        assertEquals("十五秒", prepareOptions().last().label)
    }

    @Test
    fun `every default sits on its own wheel`() {
        // `TempoValueWheel` seeds its position with `values.indexOf(selected)`, which is −1 for a value
        // the list does not contain — the wheel then opens on its first row, so a default that is not
        // on its own wheel silently becomes なし. Checked for all three dialable durations.
        assertTrue(prepareOptions().any { it.value == defaults.prepareSeconds })
        assertTrue(restOptions(RestSlot.BETWEEN_STATIONS).any { it.value == defaults.defaultStationRest })
        assertTrue(restOptions(RestSlot.BETWEEN_ROUNDS).any { it.value == defaults.defaultRoundRest })
    }

    @Test
    fun `the rest wheels are the builder's own and stay inside the store's bounds`() {
        // Reused rather than restated (`04-library-records.md` §3 edge case 11 owns the ranges), and
        // every row of both is a value `GymPreferencesStore` will accept unchanged.
        val stations = restOptions(RestSlot.BETWEEN_STATIONS)
        val rounds = restOptions(RestSlot.BETWEEN_ROUNDS)
        for (option in stations) assertEquals(option.value, clampStationRest(option.value))
        for (option in rounds) assertEquals(option.value, clampRoundRest(option.value))
        assertEquals("なし", stations.first().label)
        assertEquals("なし", rounds.first().label)
        assertEquals("六十秒", rounds.first { it.value == 60 }.label)
    }

    // ─── The rows' own vocabulary ───────────────────────────────────────────────────────────────

    @Test
    fun `every row and section carries the word its spec mock draws`() {
        assertEquals("合図", SettingSection.Cues.heading)
        assertEquals("進行", SettingSection.Progress.heading)
        assertEquals("休息の初期値", SettingSection.RestDefaults.heading)
        assertEquals("表示", SettingSection.Display.heading)

        assertEquals("振動", SettingRow.Haptics.label)
        assertEquals("音", SettingRow.Tones.label)
        assertEquals("音声", SettingRow.Speech.label)
        assertEquals("目安で自動的に進む", SettingRow.AutoAdvanceReps.label)
        assertEquals("支度の長さ", SettingRow.PrepareSeconds.label)
        assertEquals("種目の間", SettingRow.StationRest.label)
        assertEquals("巡の間", SettingRow.RoundRest.label)
        assertEquals("画面を消さない", SettingRow.KeepScreenOn.label)
        assertEquals("単位", SettingRow.Units.label)

        // The two lines that are not rows: a footnote about what a default *is* (§B edge case 6), and
        // the one sentence a failed write is allowed to say (§B's `Error`, `DECISIONS.md` §Q6).
        assertEquals("これから作る型に使われます", REST_DEFAULTS_FOOTNOTE)
        assertEquals("保存できませんでした", WRITE_FAILED_LINE)
    }
}
