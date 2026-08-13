package io.eddiegulay.tempo.gym

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The XML the health foreground service cannot run without, pinned as text.
 *
 * Same technique and same reasoning as `gym/data/GymManifestTest.kt`: these failures are **silent
 * under `testDebugUnitTest`**. A missing `<service>` element does not fail to compile — `sync()` posts
 * an Intent that resolves to nothing and the notification simply never appears. A missing
 * `foregroundServiceType` is worse: the service starts, `startForeground` throws, and the catch that
 * exists to keep a workout alive swallows it. Neither is visible without a device.
 *
 * Reading the file as text is deliberate, exactly as it is there — the assertion is that a particular
 * line is present, and an XML parse would make the test about the parser.
 */
class TrainingManifestTest {

    private val manifest: String by lazy { repoFile("src/main/AndroidManifest.xml").readText() }

    @Test
    fun `the service is declared, unexported, typed health, and dies with the task`() {
        assertTrue("service element", manifest.contains("android:name=\".gym.TrainingService\""))
        // The type is what makes it a *health* service; without it the FOREGROUND_SERVICE_HEALTH
        // permission below is inert and startForeground is refused on API 34+.
        assertTrue("health type", manifest.contains("android:foregroundServiceType=\"health\""))
        // The third of the three guarantees that it never outlives a session; the other two are in
        // Kotlin (START_NOT_STICKY, and the mount's onDispose).
        assertTrue("stopWithTask", manifest.contains("android:stopWithTask=\"true\""))
    }

    @Test
    fun `stopWithTask and an onTaskRemoved override cannot both be claimed`() {
        // They contradict each other, and the contradiction is invisible: with FLAG_STOP_WITH_TASK set
        // the framework stops the service itself and `Service.onTaskRemoved` is documented as **not
        // delivered at all**, so an override for it is dead code — and the file header, this test and
        // the manifest comment were all counting it as a fourth independent guarantee. Nothing about
        // the app's behaviour was wrong; what was wrong is the thing a future agent would act on.
        //
        // Either arrangement is defensible, so the assertion is the exclusive-or rather than a
        // preference: keep the attribute and drop the override (what we do), or drop the attribute and
        // let the override actually fire.
        val declaresStopWithTask = manifest.contains("android:stopWithTask=\"true\"")
        val overridesOnTaskRemoved = repoFile("src/main/java/io/eddiegulay/tempo/gym/TrainingService.kt")
            .readText().contains("override fun onTaskRemoved")

        assertTrue(
            "stopWithTask=$declaresStopWithTask means onTaskRemoved is never delivered, yet it is overridden",
            !(declaresStopWithTask && overridesOnTaskRemoved),
        )
    }

    @Test
    fun `the two install-time foreground-service permissions are declared`() {
        listOf(
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_HEALTH",
        ).forEach { permission ->
            assertTrue(permission, manifest.contains("android:name=\"$permission\""))
        }
    }

    @Test
    fun `the two runtime permissions the service asks for are declared`() {
        // Declared here, requested in TrainingConsent at the API levels that need them, and never a
        // gate on training.
        listOf(
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.ACTIVITY_RECOGNITION",
        ).forEach { permission ->
            assertTrue(permission, manifest.contains("android:name=\"$permission\""))
        }
    }

    @Test
    fun `no sensor permission was taken to satisfy the health service type`() {
        // HIGH_SAMPLING_RATE_SENSORS and BODY_SENSORS both satisfy API 34's prerequisite, the first
        // without any prompt at all. Tempo reads no sensor, so neither may be *declared* — the names
        // do appear in the manifest, in the REJECTED note that records why they are not, which is why
        // this asserts on the declaration rather than on the substring.
        val declared = Regex("""<uses-permission[^>]*android:name="([^"]+)"""")
            .findAll(manifest)
            .map { it.groupValues[1] }
            .toList()
        listOf("SENSORS", "BODY", "HEART_RATE", "OXYGEN", "SKIN_TEMPERATURE").forEach { fragment ->
            assertTrue(
                "$fragment in $declared",
                declared.none { it.contains(fragment) },
            )
        }
    }

    @Test
    fun `the calendar hand-off added no permission of its own`() {
        // 予定に入れる writes through the same CalendarRepository the 予定 composer writes through, so
        // READ_CALENDAR and WRITE_CALENDAR are the whole of what it needs — and they predate 鍛錬.
        assertTrue(manifest.contains("android:name=\"android.permission.READ_CALENDAR\""))
        assertTrue(manifest.contains("android:name=\"android.permission.WRITE_CALENDAR\""))
    }

    @Test
    fun `the notification's small icon exists as a drawable`() {
        // NotificationCompat requires one, and a missing resource id is a crash at post time rather
        // than a compile error, because R constants are ints.
        assertTrue(repoFile("src/main/res/drawable/ic_training_notice.xml").isFile)
    }

    @Test
    fun `R8 keeps the service, and R8 is genuinely on for release`() {
        // Resolved from the manifest's own module directory rather than by name: the repository root
        // has a `build.gradle.kts` too, and the walker would find that one first when the tests are
        // launched from there.
        val module = generateSequence(repoFile("src/main/AndroidManifest.xml")) { it.parentFile }
            .first { File(it, "build.gradle.kts").isFile }
        val gradle = File(module, "build.gradle.kts").readText()
        assertTrue("minify", gradle.contains("isMinifyEnabled = true"))
        assertTrue(
            "keep rule",
            repoFile("proguard-rules.pro").readText()
                .contains("io.eddiegulay.tempo.gym.TrainingService"),
        )
    }

    /** Walks up from the test's working directory, so the test does not care where it is run from. */
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            File(dir, relative).takeIf { it.isFile }?.let { return it }
            File(dir, "app/$relative").takeIf { it.isFile }?.let { return it }
            dir = dir.parentFile
        }
        throw AssertionError("$relative not found from ${System.getProperty("user.dir")}")
    }
}
