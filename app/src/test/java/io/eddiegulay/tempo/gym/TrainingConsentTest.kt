package io.eddiegulay.tempo.gym

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which permissions the health service asks for, at which API level, and how often — `gym/TrainingConsent.kt`.
 *
 * Two things are pinned here that a reviewer would otherwise have to take on trust: that a user below
 * Android 14 is **never** shown a physical-activity prompt, and that the automatic ask happens at most
 * once, without this feature owning a stored flag.
 */
class TrainingConsentTest {

    private val post = Manifest.permission.POST_NOTIFICATIONS
    private val activity = Manifest.permission.ACTIVITY_RECOGNITION

    // ── required(sdkInt) ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `below Tiramisu nothing is asked for at all`() {
        // minSdk is 29. A notification needs no grant here and the health foreground-service type
        // imposes no prerequisite until API 34, so the service simply works.
        assertEquals(emptyList<String>(), TrainingConsent.required(Build.VERSION_CODES.Q))
        assertEquals(emptyList<String>(), TrainingConsent.required(Build.VERSION_CODES.S))
    }

    @Test
    fun `from Tiramisu the notification needs a grant, and only the notification`() {
        assertEquals(listOf(post), TrainingConsent.required(Build.VERSION_CODES.TIRAMISU))
    }

    @Test
    fun `from Upside Down Cake the health service type needs its own prerequisite as well`() {
        // Android 14: a foregroundServiceType="health" service may only call startForeground while the
        // app holds one of BODY_SENSORS / READ_HEART_RATE / READ_SKIN_TEMPERATURE /
        // READ_OXYGEN_SATURATION / ACTIVITY_RECOGNITION. Without one, the service cannot be created.
        assertEquals(
            listOf(post, activity),
            TrainingConsent.required(Build.VERSION_CODES.UPSIDE_DOWN_CAKE),
        )
    }

    @Test
    fun `physical activity is never requested on a device that does not require it`() {
        // The declaration is only honest if the behaviour matches it. An Android 10 user sees no
        // physical-activity prompt, ever.
        (Build.VERSION_CODES.Q until Build.VERSION_CODES.UPSIDE_DOWN_CAKE).forEach { sdk ->
            assertFalse("$sdk", TrainingConsent.required(sdk).contains(activity))
        }
    }

    @Test
    fun `no sensor permission is ever in the list`() {
        // HIGH_SAMPLING_RATE_SENSORS would satisfy API 34's prerequisite by manifest declaration alone,
        // with no prompt — and was rejected precisely because it cannot be declined. If it ever appears
        // here, that decision has been reversed by accident.
        val everything = (Build.VERSION_CODES.Q..Build.VERSION_CODES.VANILLA_ICE_CREAM)
            .flatMap { TrainingConsent.required(it) }
            .toSet()
        assertTrue(
            everything.toString(),
            everything.none { it.contains("SENSOR") || it.contains("BODY") },
        )
    }

    // ── shouldAutoRequest ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a fresh install is asked exactly once`() {
        assertTrue(
            TrainingConsent.shouldAutoRequest(
                missing = listOf(post),
                askedThisProcess = false,
                anyRationale = false,
            ),
        )
    }

    @Test
    fun `having asked in this process, it does not ask again`() {
        assertFalse(
            TrainingConsent.shouldAutoRequest(
                missing = listOf(post),
                askedThisProcess = true,
                anyRationale = false,
            ),
        )
    }

    @Test
    fun `a user who has declined once is never asked again, across process death`() {
        // This is the term that gives the rule durability without a stored flag: declining flips
        // shouldShowRequestPermissionRationale to true and it stays true.
        assertFalse(
            TrainingConsent.shouldAutoRequest(
                missing = listOf(post),
                askedThisProcess = false,
                anyRationale = true,
            ),
        )
    }

    @Test
    fun `nothing missing asks nothing`() {
        assertFalse(
            TrainingConsent.shouldAutoRequest(
                missing = emptyList(),
                askedThisProcess = false,
                anyRationale = false,
            ),
        )
    }

    @Test
    fun `the wait is the ensō's own closure, so the ceremony is never interrupted`() {
        // A permission dialog pauses the Activity, and `RecordSummary`'s closure is gated on ON_RESUME.
        // 900ms is `03-player.md` §A COMPLETE's figure, reused rather than chosen.
        assertEquals(900L, TRAINING_CONSENT_DELAY_MS)
    }
}
