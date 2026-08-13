package io.eddiegulay.tempo.gym.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The three XML files 鍛錬 depends on and no Kotlin test would otherwise touch.
 *
 * Both failures these pin are **silent**. A missing `VIBRATE` does not crash: `Vibrator.vibrate` is a
 * no-op without it, so the whole haptic channel is simply dead, and the only thing that notices is
 * `lintDebug` — which is not what `testDebugUnitTest` runs. And the backup files are *include-lists*:
 * anything not named is not restored, so an omitted DataStore is a cloud restore that brings a year of
 * training back with every 鍛錬 preference reset and the history-lost flag dropped, which is a state
 * the user cannot tell from a fresh install.
 *
 * Reading the files as text is deliberate. Parsing them adds nothing — the assertion is that a
 * particular line is present — and an XML parse would make the test about the parser.
 */
class GymManifestTest {

    @Test
    fun `the manifest declares VIBRATE, which the session player's haptics call unconditionally`() {
        // A normal install-time permission: granted at install, no runtime prompt, no user-visible
        // ceremony — which is why it does not engage `03-player.md` §E.5's Phase-1 refusal of runtime
        // and foreground-service entries.
        assertTrue(
            "AndroidManifest.xml must declare android.permission.VIBRATE",
            repoFile("src/main/AndroidManifest.xml").readText()
                .contains("android:name=\"android.permission.VIBRATE\""),
        )
    }

    @Test
    fun `both backup rule files carry every store 鍛錬 writes`() {
        // exercise.db is the history; tempo_gym holds 振動 / 音 / 音声, 支度の長さ, both rest defaults,
        // 単位, 画面を消さない and the safety acknowledgement; gym_store holds the durable
        // history-lost flag, which must travel with the database or a restored corrupt history
        // renders as 記録はありません (§7.1, §E.6, §E.7).
        listOf("backup_rules.xml", "data_extraction_rules.xml").forEach { name ->
            val text = repoFile("src/main/res/xml/$name").readText()
            listOf(
                "datastore/tempo_settings.preferences_pb",
                "datastore/tempo_gym.preferences_pb",
                "datastore/gym_store.preferences_pb",
                "exercise.db",
            ).forEach { path ->
                assertTrue("$name must include $path", text.contains("path=\"$path\""))
            }
            // Never the journal siblings: a WAL restored without its exact matching main file is an
            // inconsistent pair (§E.7).
            assertTrue(name, !text.contains("exercise.db-wal") && !text.contains("exercise.db-shm"))
        }
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
