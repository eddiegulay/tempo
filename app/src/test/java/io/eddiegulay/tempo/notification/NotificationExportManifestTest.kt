package io.eddiegulay.tempo.notification

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * FileProvider wiring, pinned as text. A missing `<provider>` does not fail to compile — copy
 * writes a cache file and the clipboard URI resolves to nothing, so paste is silently empty.
 */
class NotificationExportManifestTest {

    private val manifest: String by lazy { repoFile("src/main/AndroidManifest.xml").readText() }

    @Test
    fun `FileProvider is declared unexported with a grant and the cache path xml`() {
        assertTrue("provider", manifest.contains("androidx.core.content.FileProvider"))
        assertTrue("authority", manifest.contains("android:authorities=\"\${applicationId}.fileprovider\""))
        assertTrue("unexported", manifest.contains("android:exported=\"false\""))
        assertTrue("grants", manifest.contains("android:grantUriPermissions=\"true\""))
        assertTrue("paths", manifest.contains("@xml/tempo_file_paths"))
    }

    @Test
    fun `a Pictures write is pending until the bytes are in`() {
        val export = repoFile("src/main/java/io/eddiegulay/tempo/notification/NotificationCardExport.kt").readText()
        assertTrue(export.contains("IS_PENDING, 1"))
        assertTrue(export.contains("IS_PENDING, 0"))
        assertTrue(export.contains("resolver.delete(uri"))
    }

    @Test
    fun `the paths xml exposes only the notification cache directory`() {
        val paths = repoFile("src/main/res/xml/tempo_file_paths.xml").readText()
        assertTrue(paths.contains("cache-path"))
        assertTrue(paths.contains("path=\"notifications/\""))
        assertTrue("no external-path", !paths.contains("external-path"))
    }

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
