package io.eddiegulay.tempo.notification

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.PersistableBundle
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

/** FileProvider authority — must match the `<provider>` in AndroidManifest. */
fun fileProviderAuthority(context: Context): String = "${context.packageName}.fileprovider"

/**
 * PNG bytes from a Compose [ImageBitmap].
 *
 * `GraphicsLayer.toImageBitmap()` often yields a HARDWARE bitmap, which [Bitmap.compress] refuses.
 * Copying to ARGB_8888 is the documented way through; JPEG is never used because it would paint
 * the rounded corners black.
 */
fun ImageBitmap.toPngBytes(): ByteArray {
    val source = asAndroidBitmap()
    val software = if (source.config == Bitmap.Config.HARDWARE || source.config == null) {
        source.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        source
    }
    val out = ByteArrayOutputStream()
    check(software.compress(Bitmap.CompressFormat.PNG, 100, out)) { "png compress failed" }
    return out.toByteArray()
}

/**
 * Writes [bytes] into cache and puts a `content://` URI on the clipboard as `image/png`.
 *
 * Android will not take raw image bytes on the clipboard; recipients read the FileProvider URI.
 * The clip is marked sensitive on API 33+ because the pixels are a notification.
 */
fun copyPngToClipboard(context: Context, bytes: ByteArray, label: String) {
    val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
    val file = File(dir, CACHE_FILE)
    file.writeBytes(bytes)
    val uri = FileProvider.getUriForFile(context, fileProviderAuthority(context), file)
    val clip = ClipData.newUri(context.contentResolver, label, uri)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

/**
 * Inserts the PNG into `Pictures/Tempo` via MediaStore. No storage permission on API 29+.
 *
 * @return the new content URI, or null if the insert was refused.
 */
fun savePngToPictures(context: Context, bytes: ByteArray, displayName: String): android.net.Uri? {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, PICTURES_RELATIVE)
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    return try {
        val stream = resolver.openOutputStream(uri)
        if (stream == null) {
            resolver.delete(uri, null, null)
            return null
        }
        stream.use {
            it.write(bytes)
            it.flush()
        }
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null,
        )
        uri
    } catch (_: Exception) {
        resolver.delete(uri, null, null)
        null
    }
}

private const val CACHE_DIR = "notifications"
private const val CACHE_FILE = "share.png"
private const val PICTURES_RELATIVE = "Pictures/Tempo"
