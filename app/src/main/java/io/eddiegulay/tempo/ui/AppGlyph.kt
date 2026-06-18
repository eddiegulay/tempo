package io.eddiegulay.tempo.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.eddiegulay.tempo.data.AppInfo
import io.eddiegulay.tempo.ui.theme.LocalTempoColors
import io.eddiegulay.tempo.ui.theme.Mincho

/**
 * Tempo's internal, hand-drawn icon set — monochrome stroked line glyphs (24×24, round caps) in the
 * same spirit as [TempoIcons] and the design prototype. The drawer renders these instead of the
 * platform's colourful per-app icons so the whole list reads as one calm, paper-drawn sheet.
 *
 * Glyphs are chosen by *function*, not by app: a package/keyword/category resolver ([glyphFor])
 * maps each installed app to the closest glyph, and anything unmatched falls back to a washi
 * monogram tile of its first character. The base twelve are lifted verbatim from the prototype.
 */
object AppGlyphs {
    val Phone = listOf("M8 3.5h8a1.2 1.2 0 0 1 1.2 1.2v14.6a1.2 1.2 0 0 1-1.2 1.2H8a1.2 1.2 0 0 1-1.2-1.2V4.7A1.2 1.2 0 0 1 8 3.5z", "M10.4 18.2h3.2")
    val Message = listOf("M5 5.5h14a1 1 0 0 1 1 1V14a1 1 0 0 1-1 1h-7.6L7 18.5V15H5a1 1 0 0 1-1-1V6.5a1 1 0 0 1 1-1z")
    val Camera = listOf("M4.5 8.5h2.6l1.3-2h7.2l1.3 2h2.6a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1h-15a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1z", "M12 16.8a3.1 3.1 0 1 0 0-6.2 3.1 3.1 0 0 0 0 6.2z")
    val Photos = listOf("M5 4.5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-13a1 1 0 0 1 1-1z", "M4.5 16l4-4 3 3 4-4.5 4 4.5", "M9 9.7a1.4 1.4 0 1 0 0-2.8 1.4 1.4 0 0 0 0 2.8z")
    val Music = listOf("M9 17.5V6l9-2v11.5", "M9 17.5a2.2 2.2 0 1 1-4.4 0 2.2 2.2 0 0 1 4.4 0z", "M18 15.5a2.2 2.2 0 1 1-4.4 0 2.2 2.2 0 0 1 4.4 0z")
    val Calendar = listOf("M5 5.5h14a1 1 0 0 1 1 1v12.5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6.5a1 1 0 0 1 1-1z", "M4 9.5h16", "M8.5 3.5v4", "M15.5 3.5v4")
    val Weather = listOf("M7.5 18h8.5a3.2 3.2 0 0 0 .3-6.4 4.3 4.3 0 0 0-8.2-1A3.3 3.3 0 0 0 7.5 18z")
    val Map = listOf("M9 4.5L4.5 6.2v13.3l4.5-1.7 6 1.7 4.5-1.7V4.2l-4.5 1.7-6-1.7z", "M9 4.5v13.3", "M15 6.2v13.3")
    val Clock = listOf("M12 20.5a8.5 8.5 0 1 0 0-17 8.5 8.5 0 0 0 0 17z", "M12 7.5v5l3.2 1.9")
    val Calc = listOf("M6.5 3.5h11a1 1 0 0 1 1 1v15a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1v-15a1 1 0 0 1 1-1z", "M8 6.5h8v3.2H8z", "M9 14h0", "M12 14h0", "M15 14h0", "M9 17h0", "M12 17h0", "M15 17h0")
    val Settings = listOf("M12 15.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4z", "M19.4 12a7.4 7.4 0 0 0-.1-1.2l1.9-1.5-1.9-3.3-2.3 1a7.3 7.3 0 0 0-2-1.2l-.3-2.5h-3.4l-.3 2.5a7.3 7.3 0 0 0-2 1.2l-2.3-1L2.8 9.3l1.9 1.5a7.4 7.4 0 0 0 0 2.4l-1.9 1.5 1.9 3.3 2.3-1a7.3 7.3 0 0 0 2 1.2l.3 2.5h3.4l.3-2.5a7.3 7.3 0 0 0 2-1.2l2.3 1 1.9-3.3-1.9-1.5c.1-.4.1-.8.1-1.2z")
    val Books = listOf("M5 5.2A1.7 1.7 0 0 1 6.7 3.5H19v13H6.7A1.7 1.7 0 0 0 5 18.2z", "M5 18.2a1.7 1.7 0 0 0 1.7 1.7H19")

    // Extensions beyond the prototype, for fuller everyday-phone coverage.
    val Globe = listOf("M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M3.2 9.5h17.6", "M3.2 14.5h17.6", "M12 3c2.6 2.4 2.6 15.6 0 18", "M12 3c-2.6 2.4-2.6 15.6 0 18")
    val Mail = listOf("M4 6.5h16a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1z", "M3.4 7.2l8.6 6 8.6-6")
    val Folder = listOf("M4 7.5a1 1 0 0 1 1-1h3.6l2 2H19a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z")
    val Person = listOf("M12 12.5a3.6 3.6 0 1 0 0-7.2 3.6 3.6 0 0 0 0 7.2z", "M5.5 19.2a6.5 6.5 0 0 1 13 0")
    val Note = listOf("M6 3.5h8l4 4v13a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-16a1 1 0 0 1 1-1z", "M13.5 3.7v4h4", "M8 12.5h8", "M8 16h6")
    val Play = listOf("M5 5.5h14a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-11a1 1 0 0 1 1-1z", "M10.4 9.3l4.6 2.7-4.6 2.7z")
    val Bag = listOf("M6.2 8h11.6l-1 11.5H7.2L6.2 8z", "M9 8a3 3 0 0 1 6 0")
    val Card = listOf("M3.5 6.5h17a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1h-17a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1z", "M2.5 10.5h19", "M6 14.5h4")
    val Heart = listOf("M12 19.5l-1.3-1.2C6.3 14.3 3.5 11.8 3.5 8.6A4.1 4.1 0 0 1 12 6.1a4.1 4.1 0 0 1 8.5 2.5c0 3.2-2.8 5.7-7.2 9.7L12 19.5z")
    val Mic = listOf("M12 14.5a3 3 0 0 0 3-3v-4a3 3 0 0 0-6 0v4a3 3 0 0 0 3 3z", "M6.5 11.3a5.5 5.5 0 0 0 11 0", "M12 17v3", "M9 20.5h6")
    val Gamepad = listOf("M7.6 8.5h8.8a4 4 0 0 1 4 4v.4a3.4 3.4 0 0 1-6 2.2l-.4-.5H9.6l-.4.5a3.4 3.4 0 0 1-6-2.2v-.4a4 4 0 0 1 4-4z", "M7 11v3", "M5.5 12.5h3", "M15.6 12h0", "M17.4 13.5h0")
}

/**
 * Resolve an app to a line glyph, or `null` to fall back to a monogram. Order: known package →
 * keyword in the package id → declared [ApplicationInfo] category.
 */
fun glyphFor(packageName: String, categoryId: Int): List<String>? {
    val pkg = packageName.lowercase()
    KNOWN_PACKAGES[pkg]?.let { return it }
    KEYWORDS.firstOrNull { (kw, _) -> pkg.contains(kw) }?.let { return it.second }
    return CATEGORY_GLYPHS[categoryId]
}

/**
 * A single drawer/app icon: the resolved line glyph, or — for an app we can't classify — a faint
 * washi monogram tile of its first character, so unknown apps still look intentional.
 */
@Composable
fun AppGlyph(
    app: AppInfo,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
) {
    val paths = remember(app.packageName, app.categoryId) { glyphFor(app.packageName, app.categoryId) }
    if (paths != null) {
        LineIcon(paths = paths, color = color, modifier = modifier, size = size, strokeWidth = 1.5.dp)
    } else {
        Monogram(label = app.label, color = color, modifier = modifier, size = size)
    }
}

@Composable
private fun Monogram(label: String, color: Color, modifier: Modifier, size: Dp) {
    val c = LocalTempoColors.current
    val ch = remember(label) { label.trim().firstOrNull()?.toString() ?: "・" }
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .border(1.dp, c.hair, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ch,
            style = TextStyle(fontFamily = Mincho, fontSize = (size.value * 0.5f).sp, color = color),
        )
    }
}

/** Popular apps whose package id doesn't carry an obvious keyword. */
private val KNOWN_PACKAGES: Map<String, List<String>> = mapOf(
    "com.google.android.gm" to AppGlyphs.Mail,
    "com.microsoft.office.outlook" to AppGlyphs.Mail,
    "com.android.vending" to AppGlyphs.Bag,
    "com.whatsapp" to AppGlyphs.Message,
    "com.facebook.orca" to AppGlyphs.Message,
    "com.facebook.katana" to AppGlyphs.Message,
    "com.instagram.android" to AppGlyphs.Camera,
    "com.zhiliaoapp.musically" to AppGlyphs.Play,
    "com.twitter.android" to AppGlyphs.Message,
    "com.snapchat.android" to AppGlyphs.Camera,
    "com.reddit.frontpage" to AppGlyphs.Message,
    "com.discord" to AppGlyphs.Message,
    "com.netflix.mediaclient" to AppGlyphs.Play,
    "com.google.android.gm.lite" to AppGlyphs.Mail,
    "com.google.android.apps.docs" to AppGlyphs.Folder,
    "com.google.android.apps.nbu.files" to AppGlyphs.Folder,
    "com.google.android.apps.fitness" to AppGlyphs.Heart,
)

/** Substring → glyph, scanned in order; first hit wins, so put the specific before the generic. */
private val KEYWORDS: List<Pair<String, List<String>>> = listOf(
    "dialer" to AppGlyphs.Phone,
    "incallui" to AppGlyphs.Phone,
    "contact" to AppGlyphs.Person,
    "messag" to AppGlyphs.Message,
    "telegram" to AppGlyphs.Message,
    "signal" to AppGlyphs.Message,
    "whatsapp" to AppGlyphs.Message,
    ".sms" to AppGlyphs.Message,
    ".mms" to AppGlyphs.Message,
    "camera" to AppGlyphs.Camera,
    "gallery" to AppGlyphs.Photos,
    "photo" to AppGlyphs.Photos,
    "spotify" to AppGlyphs.Music,
    "youtube.music" to AppGlyphs.Music,
    "music" to AppGlyphs.Music,
    "audio" to AppGlyphs.Music,
    "podcast" to AppGlyphs.Mic,
    "youtube" to AppGlyphs.Play,
    "video" to AppGlyphs.Play,
    "player" to AppGlyphs.Play,
    "movie" to AppGlyphs.Play,
    "calendar" to AppGlyphs.Calendar,
    "weather" to AppGlyphs.Weather,
    "maps" to AppGlyphs.Map,
    "navigation" to AppGlyphs.Map,
    "deskclock" to AppGlyphs.Clock,
    "clock" to AppGlyphs.Clock,
    "alarm" to AppGlyphs.Clock,
    "calcul" to AppGlyphs.Calc,
    "setting" to AppGlyphs.Settings,
    "chrome" to AppGlyphs.Globe,
    "firefox" to AppGlyphs.Globe,
    "browser" to AppGlyphs.Globe,
    "brave" to AppGlyphs.Globe,
    "opera" to AppGlyphs.Globe,
    "mail" to AppGlyphs.Mail,
    "email" to AppGlyphs.Mail,
    "outlook" to AppGlyphs.Mail,
    "file" to AppGlyphs.Folder,
    "document" to AppGlyphs.Folder,
    "drive" to AppGlyphs.Folder,
    "note" to AppGlyphs.Note,
    "keep" to AppGlyphs.Note,
    "memo" to AppGlyphs.Note,
    "wallet" to AppGlyphs.Card,
    "pay" to AppGlyphs.Card,
    "bank" to AppGlyphs.Card,
    "finance" to AppGlyphs.Card,
    "store" to AppGlyphs.Bag,
    "market" to AppGlyphs.Bag,
    "shop" to AppGlyphs.Bag,
    "health" to AppGlyphs.Heart,
    "fit" to AppGlyphs.Heart,
    "workout" to AppGlyphs.Heart,
    "game" to AppGlyphs.Gamepad,
    "book" to AppGlyphs.Books,
    "read" to AppGlyphs.Books,
    "kindle" to AppGlyphs.Books,
)

/** Declared app category → glyph (last resort before the monogram). */
private val CATEGORY_GLYPHS: Map<Int, List<String>> = mapOf(
    ApplicationInfo.CATEGORY_AUDIO to AppGlyphs.Music,
    ApplicationInfo.CATEGORY_VIDEO to AppGlyphs.Play,
    ApplicationInfo.CATEGORY_IMAGE to AppGlyphs.Photos,
    ApplicationInfo.CATEGORY_SOCIAL to AppGlyphs.Message,
    ApplicationInfo.CATEGORY_NEWS to AppGlyphs.Books,
    ApplicationInfo.CATEGORY_MAPS to AppGlyphs.Map,
    ApplicationInfo.CATEGORY_PRODUCTIVITY to AppGlyphs.Note,
    ApplicationInfo.CATEGORY_GAME to AppGlyphs.Gamepad,
    ApplicationInfo.CATEGORY_ACCESSIBILITY to AppGlyphs.Settings,
)
