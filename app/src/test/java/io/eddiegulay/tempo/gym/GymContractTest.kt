package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.data.GymFault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract layer carries no behaviour, but it does carry four facts that every other track will
 * be wrong about silently if they ever change.
 *
 * Three of them are values a spec table froze — the CR10 mapping, the tier strings the schema's CHECK
 * spells, the seven engines the schema will accept — and each fails in the same quiet way: a build
 * that compiles, writes a row the database refuses or reads one it cannot classify, and reports it as
 * an ordinary fault a month later. The fourth is the stored-versus-effective split for speech cues,
 * where the failure is a switch and a behaviour that disagree, and neither is recoverable by the user.
 */
class GymContractTest {

    @Test
    fun `every engine name is the token the schema's CHECK constraint permits`() {
        // routine_version's CHECK spells these seven exactly. The store reads and writes them with
        // name/valueOf and no mapping table, so a renamed constant is an unreadable row — and all
        // seven exist from schema v1, because a Phase 2 seed must be readable by a Phase 1 build that
        // has already stored it.
        assertEquals(
            listOf(
                "INTERVAL_CIRCUIT", "AMRAP", "FOR_TIME", "FOR_TIME_WITH_REST",
                "EMOM", "EMOM_ASCENDING", "FIXED_SETS",
            ),
            Engine.entries.map { it.name },
        )
    }

    @Test
    fun `an engine token the catalogue does not know reads as null, not as a crash`() {
        assertEquals(Engine.AMRAP, Engine.fromStorage("AMRAP"))
        // A row from a future seed must surface as a fault to report, never as a throw on a read path.
        assertNull(Engine.fromStorage("TABATA"))
        assertNull(Engine.fromStorage(null))
    }

    @Test
    fun `the CR10 mapping is the frozen four seven nine`() {
        // session.rating_cr10 is a frozen copy of this. Retuning it later changes future load only —
        // every historical chart must stay exactly where the user last saw it.
        assertEquals(4, Rating.EASY.cr10)
        assertEquals(7, Rating.JUST_RIGHT.cr10)
        assertEquals(9, Rating.HARD.cr10)
    }

    @Test
    fun `a tier stores the Japanese the CHECK constraint spells`() {
        // Unusually the storage value is the label, because tier IN ('入門','中級','上級') is what the
        // schema says. An ASCII constant name here would be refused at the write.
        assertEquals("入門", Tier.BEGINNER.storageValue)
        assertEquals("中級", Tier.INTERMEDIATE.storageValue)
        assertEquals("上級", Tier.ADVANCED.storageValue)
        assertEquals(Tier.ADVANCED, Tier.fromStorage("上級"))
        assertNull(Tier.fromStorage(null))
    }

    @Test
    fun `pattern order is the alternation, not the alphabet`() {
        // Sorting this list would quietly undo design §9's decision about which muscle groups recover
        // while others work — the one thing the ACSM circuit forbids reordering.
        assertEquals(
            listOf("押す", "引く", "しゃがむ", "股関節", "体幹", "移動", "跳ぶ"),
            Pattern.entries.map { it.label },
        )
    }

    @Test
    fun `touch exploration turns speech on for the session without turning the switch on`() {
        val stored = GymPreferences()
        val effective = EffectiveGymPreferences(stored, touchExplorationEnabled = true)

        // The cue engine hears speech…
        assertTrue(effective.speech)
        // …and the settings toggle still shows what is on disk, which is off.
        assertFalse(effective.stored.speech)
        assertTrue(effective.speechOverriddenByTouchExploration)
    }

    @Test
    fun `a user who asked for speech is not told TalkBack did it for them`() {
        val effective = EffectiveGymPreferences(
            GymPreferences(speech = true),
            touchExplorationEnabled = true,
        )
        assertTrue(effective.speech)
        // The subtitle explaining the override must not appear when the user set it themselves.
        assertFalse(effective.speechOverriddenByTouchExploration)
    }

    @Test
    fun `speech stays off when nothing asks for it`() {
        val effective = EffectiveGymPreferences(GymPreferences(), touchExplorationEnabled = false)
        assertFalse(effective.speech)
        assertFalse(effective.speechOverriddenByTouchExploration)
    }

    @Test
    fun `a failed write yields no value rather than a plausible empty one`() {
        val failed: GymWrite<Long> = GymWrite.Failed(GymFault.StoreFull)
        assertNull(failed.valueOrNull())
        assertEquals(7L, GymWrite.Ok(7L).valueOrNull())
    }

    @Test
    fun `the start path's valueOrThrow refuses to invent a session id`() {
        // A session that exists in the UI but not in the database is the one state process death
        // cannot recover from, so the player blocks navigation on this rather than defaulting.
        val failed: GymWrite<Long> = GymWrite.Failed(GymFault.Rejected)
        val thrown = runCatching { failed.valueOrThrow() }.exceptionOrNull()
        assertTrue(thrown is IllegalStateException)
        assertEquals(7L, GymWrite.Ok(7L).valueOrThrow())
    }
}
