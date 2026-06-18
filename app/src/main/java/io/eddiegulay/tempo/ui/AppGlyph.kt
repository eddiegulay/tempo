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
 * Glyphs are chosen by *function*, not by app. [glyphFor] resolves each app in four passes, most
 * precise first:
 *   1. [KNOWN_PACKAGES] — exact package id for popular apps whose id carries no obvious word.
 *   2. [PACKAGE_KEYWORDS] — a telling substring inside the package id.
 *   3. [LABEL_KEYWORDS] — a telling word in the display name (English *or* Japanese).
 *   4. [CATEGORY_GLYPHS] — the app's declared [ApplicationInfo] category.
 * Anything still unmatched falls back to a washi monogram tile of its first character, so the long
 * tail of niche apps still looks intentional. The base twelve glyphs are lifted from the prototype.
 */
object AppGlyphs {
    // ----- prototype base set -----
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

    // ----- everyday-phone extensions -----
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
    val Search = listOf("M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z", "M16.2 16.2l3.8 3.8")
    val VideoCall = listOf("M3.5 7.5h11a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1v-7a1 1 0 0 1 1-1z", "M15.5 10.5l5-2.5v8l-5-2.5z")
    val Cart = listOf("M3 4.5h2l2 10.5h9.5l1.7-7.5H6.2", "M9 19a1.3 1.3 0 1 0 0-2.6 1.3 1.3 0 0 0 0 2.6z", "M16 19a1.3 1.3 0 1 0 0-2.6 1.3 1.3 0 0 0 0 2.6z")
    val Car = listOf("M4 13.5l1.6-5a2 2 0 0 1 1.9-1.4h9a2 2 0 0 1 1.9 1.4l1.6 5", "M3.5 13.5h17v4.5a1 1 0 0 1-1 1H18a1 1 0 0 1-1-1v-1H7v1a1 1 0 0 1-1 1H4.5a1 1 0 0 1-1-1z", "M6.5 16h1", "M16.5 16h1")
    val Fork = listOf("M8 3.5v17", "M6 3.5v4.5", "M10 3.5v4.5", "M6 8h4", "M15 3.5c-1.4 0-2.3 1.8-2.3 4.5s.9 3.5 2.3 3.5v8.5")
    val Dumbbell = listOf("M4 9.5v5", "M6.5 7.5v9", "M17.5 7.5v9", "M20 9.5v5", "M6.5 12h11")
    val Translate = listOf("M4 6.5h7", "M7.5 4.5v2", "M9 6.5c0 4-3 6.5-5 7.5", "M5 10c1.5 1.5 4 2.5 5.5 2.5", "M12.5 19.5l3.5-8 3.5 8", "M13.7 16.5h4.6")
    val Qr = listOf("M4 4.5h5v5H4z", "M15 4.5h5v5h-5z", "M4 15.5h5v5H4z", "M15 15.5h2.5v2.5", "M19.5 15.5v2.5", "M15 19.5v.5", "M17.5 20h2.5")
    val Shield = listOf("M12 3.5l7 2.4v5.1c0 4.8-3.1 7.8-7 9.5-3.9-1.7-7-4.7-7-9.5V5.9z", "M9 11.8l2 2 4-4.2")
    val Code = listOf("M4.5 5.5h15a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1h-15a1 1 0 0 1-1-1v-11a1 1 0 0 1 1-1z", "M3.5 9h17", "M7.5 12l2 2-2 2", "M11.5 16h4")
    val News = listOf("M5 5.5h12a1 1 0 0 1 1 1v10.5a1.5 1.5 0 0 0 3 0V9", "M7.5 9h7", "M7.5 12h7", "M7.5 15h4.5")
    val Tv = listOf("M4 6.5h16a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1z", "M8.5 20.5h7", "M9 3.5l3 3 3-3")
    val Download = listOf("M12 3.5v11", "M7.5 10.5l4.5 4 4.5-4", "M5 19.5h14")
    val Key = listOf("M8.5 12.5a3.5 3.5 0 1 0 7 0 3.5 3.5 0 0 0-7 0z", "M15.5 12.5h5", "M18 12.5v3", "M20.5 12.5v2.2")
    val Compass = listOf("M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z", "M14.8 9.2l-1.8 4.6-4.6 1.8 1.8-4.6z")
    val Flashlight = listOf("M8 3.5h8l-1 4.5H9z", "M9 8h6l-.8 9.5a.7 .7 0 0 1-.7 .7h-3a.7 .7 0 0 1-.7-.7z", "M11 12h2")
    val Plane = listOf("M20.5 4L3.5 11l6 2.3L20.5 4z", "M9.5 13.3V19l3.3-3.3")
    val Bus = listOf("M5 5.5h14a1 1 0 0 1 1 1v9.5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6.5a1 1 0 0 1 1-1z", "M4.5 11.5h15", "M7.5 17v1.5", "M16.5 17v1.5", "M8 14h0", "M16 14h0")
    val Coffee = listOf("M5 8.5h11v5a4 4 0 0 1-4 4H9a4 4 0 0 1-4-4z", "M16 9.5h2.2a2 2 0 0 1 0 4H16", "M8 3.5c-.4 .9 .4 1.6 0 2.8", "M11.5 3.5c-.4 .9 .4 1.6 0 2.8")
    val Gift = listOf("M4.5 9.5h15v3.5h-15z", "M6 13h12v6.5a.5 .5 0 0 1-.5 .5h-11a.5 .5 0 0 1-.5-.5z", "M12 9.5V20", "M12 9.5C12 7.5 10.8 6 9.3 6.4 8 6.8 8.3 9 12 9.5z", "M12 9.5c0-2 1.2-3.5 2.7-3.1C16 6.8 15.7 9 12 9.5z")
    val Star = listOf("M12 3.5l2.6 5.3 5.8 .8-4.2 4.1 1 5.8L12 16.8l-5.2 2.7 1-5.8L2.6 9.6l5.8-.8z")
    val Pill = listOf("M5.5 13l7.5-7.5a3.6 3.6 0 0 1 5 5L10.5 18a3.6 3.6 0 0 1-5-5z", "M9.2 9.3l5 5")
    val Paint = listOf("M5 18.5c0-1.8 1.3-2.8 2.8-2.8 1.1 0 1.9 .8 1.9 1.9 0 1.6-1.5 2.2-3.2 2.2", "M9 15.5L18.5 6a1.9 1.9 0 0 0-2.7-2.7L6.3 12.8")
}

/**
 * Resolve an app to a line glyph, or `null` to fall back to a monogram. See [AppGlyphs] for the
 * four-pass order (known package → package keyword → label keyword → category).
 */
fun glyphFor(packageName: String, label: String, categoryId: Int): List<String>? {
    val pkg = packageName.lowercase()
    KNOWN_PACKAGES[pkg]?.let { return it }
    PACKAGE_KEYWORDS.firstOrNull { (kw, _) -> pkg.contains(kw) }?.let { return it.second }
    val lbl = label.lowercase()
    LABEL_KEYWORDS.firstOrNull { (kw, _) -> lbl.contains(kw) }?.let { return it.second }
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
    val paths = remember(app.packageName, app.label, app.categoryId) {
        glyphFor(app.packageName, app.label, app.categoryId)
    }
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

/**
 * Popular apps keyed by exact package id — the precise tier, for brands whose id carries no telling
 * word (a bank's `com.monzo.app`) or whose generic word would mis-resolve. Grouped by function.
 */
private val KNOWN_PACKAGES: Map<String, List<String>> = buildMap {
    // messaging & social
    putAll(AppGlyphs.Message to listOf("com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms", "com.facebook.orca", "com.facebook.mlite", "com.facebook.katana", "com.reddit.frontpage", "com.discord", "com.tencent.mm", "jp.naver.line.android", "com.kakao.talk", "com.viber.voip", "com.twitter.android", "com.Slack", "com.google.android.apps.messaging", "com.samsung.android.messaging"))
    putAll(AppGlyphs.Camera to listOf("com.instagram.android", "com.instagram.lite", "com.snapchat.android", "com.bereal.bereal", "com.adobe.lrmobile", "com.google.android.GoogleCamera", "org.codeaurora.snapcam"))
    putAll(AppGlyphs.Play to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.google.android.youtube", "com.netflix.mediaclient", "com.disney.disneyplus", "com.amazon.avod.thirdpartyclient", "com.wbd.stream", "tv.twitch.android.app", "com.google.android.apps.youtube.kids"))
    putAll(AppGlyphs.Photos to listOf("com.google.android.apps.photos", "com.pinterest"))
    putAll(AppGlyphs.Person to listOf("com.linkedin.android", "com.google.android.contacts", "com.samsung.android.app.contacts"))
    putAll(AppGlyphs.VideoCall to listOf("com.skype.raider", "us.zoom.videomeetings", "com.microsoft.teams", "com.google.android.apps.tachyon", "com.google.android.apps.meetings"))
    // media
    putAll(AppGlyphs.Music to listOf("com.spotify.music", "com.soundcloud.android", "com.amazon.mp3", "deezer.android.app", "com.aspiro.tidal", "com.google.android.apps.youtube.music", "com.shazam.android", "com.bandcamp.android"))
    // maps / travel / rides / food
    putAll(AppGlyphs.Map to listOf("com.google.android.apps.maps", "com.waze", "org.osmand.plus", "com.here.app.maps", "com.booking", "com.tripadvisor.tripadvisor"))
    putAll(AppGlyphs.Car to listOf("com.ubercab", "me.lyft.android", "ee.mtakso.client", "com.grabtaxi.passenger", "com.didiglobal.passenger", "com.gett.android", "com.free2move.android"))
    putAll(AppGlyphs.Fork to listOf("com.ubercab.eats", "com.doordash.consumer", "com.grubhub.android", "com.deliveroo.orderapp", "com.application.zomato", "com.global.foodpanda.android"))
    putAll(AppGlyphs.Plane to listOf("com.airbnb.android", "com.expedia.bookings", "com.skyscanner.android.main"))
    // money
    putAll(AppGlyphs.Card to listOf("com.paypal.android.p2pmobile", "com.venmo", "com.squareup.cash", "com.revolut.revolut", "com.transferwise.android", "com.wise.android", "com.coinbase.android", "com.binance.dev", "com.google.android.apps.walletnfcrel", "com.google.android.apps.nbu.paisa.user", "com.monzo.app", "com.starlingbank.android"))
    // shopping
    putAll(AppGlyphs.Cart to listOf("com.amazon.mShop.android.shopping", "com.ebay.mobile", "com.alibaba.aliexpresshd", "com.einnovation.temu", "com.contextlogic.wish", "com.etsy.android", "com.shopee.app", "com.shopify.mobile"))
    // work / files
    putAll(AppGlyphs.Note to listOf("notion.id", "com.todoist", "com.trello", "com.evernote", "com.microsoft.office.word", "com.microsoft.office.onenote", "com.google.android.keep"))
    putAll(AppGlyphs.Folder to listOf("com.dropbox.android", "com.microsoft.skydrive", "com.google.android.apps.docs", "com.google.android.apps.nbu.files", "com.box.android"))
    putAll(AppGlyphs.Calc to listOf("com.microsoft.office.excel", "com.google.android.calculator", "com.android.calculator2"))
    // mail / web
    putAll(AppGlyphs.Mail to listOf("com.google.android.gm", "com.google.android.gm.lite", "com.microsoft.office.outlook", "com.yahoo.mobile.client.android.mail", "ch.protonmail.android", "com.fsck.k9"))
    putAll(AppGlyphs.Globe to listOf("com.android.chrome", "org.mozilla.firefox", "com.brave.browser", "com.opera.browser", "com.opera.mini.native", "com.microsoft.emmx", "com.sec.android.app.sbrowser", "com.duckduckgo.mobile.android", "com.google.android.googlequicksearchbox"))
    // utilities / system
    putAll(AppGlyphs.Settings to listOf("com.android.settings", "com.android.vending.settings"))
    putAll(AppGlyphs.Bag to listOf("com.android.vending", "com.amazon.venezia", "com.sec.android.app.samsungapps", "org.fdroid.fdroid", "com.aurora.store"))
    putAll(AppGlyphs.Translate to listOf("com.google.android.apps.translate", "com.deepl.mobiletranslator", "com.duolingo"))
    putAll(AppGlyphs.Key to listOf("com.x8bit.bitwarden", "com.lastpass.lpandroid", "com.authy.authy", "com.google.android.apps.authenticator2", "com.agilebits.onepassword", "org.keepassdroid"))
    putAll(AppGlyphs.Shield to listOf("com.nordvpn.android", "com.expressvpn.vpn", "com.protonvpn.android", "com.wireguard.android"))
    // entertainment / reading / health
    putAll(AppGlyphs.Gamepad to listOf("com.valvesoftware.android.steam.community", "com.roblox.client", "com.mojang.minecraftpe", "com.epicgames.fortnite"))
    putAll(AppGlyphs.Books to listOf("com.amazon.kindle", "com.google.android.apps.books", "com.wattpad", "ru.androidpit", "com.google.android.apps.magazines"))
    putAll(AppGlyphs.Heart to listOf("com.fitbit.FitbitMobile", "com.myfitnesspal.android", "com.google.android.apps.fitness", "com.samsung.android.app.shealth"))
    putAll(AppGlyphs.Dumbbell to listOf("com.strava", "com.nike.ntc", "com.freeletics.lite"))
}

/** Helper: register every package id in [ids] against [glyph]. */
private fun MutableMap<String, List<String>>.putAll(entry: Pair<List<String>, List<String>>) {
    val (glyph, ids) = entry
    ids.forEach { put(it, glyph) }
}

/** Substring → glyph, scanned over the package id in order; specific before generic. */
private val PACKAGE_KEYWORDS: List<Pair<String, List<String>>> = listOf(
    "dialer" to AppGlyphs.Phone,
    "incallui" to AppGlyphs.Phone,
    "contact" to AppGlyphs.Person,
    "ubereats" to AppGlyphs.Fork,
    "uber.eats" to AppGlyphs.Fork,
    "deliver" to AppGlyphs.Fork,
    "telegram" to AppGlyphs.Message,
    "signal" to AppGlyphs.Message,
    "whatsapp" to AppGlyphs.Message,
    "messag" to AppGlyphs.Message,
    ".sms" to AppGlyphs.Message,
    ".mms" to AppGlyphs.Message,
    "camera" to AppGlyphs.Camera,
    "gallery" to AppGlyphs.Photos,
    "photo" to AppGlyphs.Photos,
    "youtube.music" to AppGlyphs.Music,
    "spotify" to AppGlyphs.Music,
    "soundcloud" to AppGlyphs.Music,
    "music" to AppGlyphs.Music,
    "audio" to AppGlyphs.Music,
    "podcast" to AppGlyphs.Mic,
    "recorder" to AppGlyphs.Mic,
    "youtube" to AppGlyphs.Play,
    "netflix" to AppGlyphs.Play,
    "twitch" to AppGlyphs.Play,
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
    "scanner" to AppGlyphs.Qr,
    "translate" to AppGlyphs.Translate,
    "authenticat" to AppGlyphs.Key,
    "password" to AppGlyphs.Key,
    "vpn" to AppGlyphs.Shield,
    "wallet" to AppGlyphs.Card,
    "crypto" to AppGlyphs.Card,
    "docs" to AppGlyphs.Folder,
    "document" to AppGlyphs.Folder,
    "dropbox" to AppGlyphs.Folder,
    "file" to AppGlyphs.Folder,
    "drive" to AppGlyphs.Folder,
    "note" to AppGlyphs.Note,
    "keep" to AppGlyphs.Note,
    "memo" to AppGlyphs.Note,
    "wear" to AppGlyphs.Bag, // wearable companion stores; rare, harmless
    "store" to AppGlyphs.Bag,
    "market" to AppGlyphs.Bag,
    "shop" to AppGlyphs.Cart,
    "health" to AppGlyphs.Heart,
    "fitness" to AppGlyphs.Dumbbell,
    "workout" to AppGlyphs.Dumbbell,
    "game" to AppGlyphs.Gamepad,
    "book" to AppGlyphs.Books,
    "kindle" to AppGlyphs.Books,
    "reader" to AppGlyphs.Books,
    "flashlight" to AppGlyphs.Flashlight,
    "compass" to AppGlyphs.Compass,
    "code" to AppGlyphs.Code,
    "terminal" to AppGlyphs.Code,
)

/**
 * Telling word → glyph, scanned over the lowercased display name; catches apps whose function lives
 * in the name rather than the package id. Mixes English and Japanese; specific before generic, and
 * deliberately uses longer words to avoid false positives (e.g. "card" must not match "Car").
 */
private val LABEL_KEYWORDS: List<Pair<String, List<String>>> = listOf(
    // Japanese
    "電話" to AppGlyphs.Phone,
    "連絡先" to AppGlyphs.Person,
    "メッセージ" to AppGlyphs.Message,
    "カメラ" to AppGlyphs.Camera,
    "写真" to AppGlyphs.Photos,
    "音楽" to AppGlyphs.Music,
    "天気" to AppGlyphs.Weather,
    "地図" to AppGlyphs.Map,
    "時計" to AppGlyphs.Clock,
    "電卓" to AppGlyphs.Calc,
    "設定" to AppGlyphs.Settings,
    "翻訳" to AppGlyphs.Translate,
    "銀行" to AppGlyphs.Card,
    "財布" to AppGlyphs.Card,
    "地下鉄" to AppGlyphs.Bus,
    "電車" to AppGlyphs.Bus,
    "天気" to AppGlyphs.Weather,
    "ニュース" to AppGlyphs.News,
    "読書" to AppGlyphs.Books,
    "ゲーム" to AppGlyphs.Gamepad,
    "翻訳" to AppGlyphs.Translate,
    // English — specific first
    "video call" to AppGlyphs.VideoCall,
    "calculator" to AppGlyphs.Calc,
    "calendar" to AppGlyphs.Calendar,
    "weather" to AppGlyphs.Weather,
    "camera" to AppGlyphs.Camera,
    "gallery" to AppGlyphs.Photos,
    "podcast" to AppGlyphs.Mic,
    "voice record" to AppGlyphs.Mic,
    "music" to AppGlyphs.Music,
    "translate" to AppGlyphs.Translate,
    "dictionary" to AppGlyphs.Translate,
    "navigation" to AppGlyphs.Map,
    "browser" to AppGlyphs.Globe,
    "messenger" to AppGlyphs.Message,
    "message" to AppGlyphs.Message,
    "contacts" to AppGlyphs.Person,
    "calls" to AppGlyphs.Phone,
    "dialer" to AppGlyphs.Phone,
    "wallet" to AppGlyphs.Card,
    "bank" to AppGlyphs.Card,
    "finance" to AppGlyphs.Card,
    "scanner" to AppGlyphs.Qr,
    "authenticator" to AppGlyphs.Key,
    "password" to AppGlyphs.Key,
    "flashlight" to AppGlyphs.Flashlight,
    "compass" to AppGlyphs.Compass,
    "fitness" to AppGlyphs.Dumbbell,
    "workout" to AppGlyphs.Dumbbell,
    "pharmacy" to AppGlyphs.Pill,
    "shopping" to AppGlyphs.Cart,
    "store" to AppGlyphs.Bag,
    "settings" to AppGlyphs.Settings,
    "news" to AppGlyphs.News,
    "weather" to AppGlyphs.Weather,
    "clock" to AppGlyphs.Clock,
    "alarm" to AppGlyphs.Clock,
    "notes" to AppGlyphs.Note,
    "files" to AppGlyphs.Folder,
    "drive" to AppGlyphs.Folder,
    "mail" to AppGlyphs.Mail,
    "coffee" to AppGlyphs.Coffee,
    "transit" to AppGlyphs.Bus,
    "metro" to AppGlyphs.Bus,
    "flight" to AppGlyphs.Plane,
    "travel" to AppGlyphs.Plane,
    "music" to AppGlyphs.Music,
    "maps" to AppGlyphs.Map,
    "photos" to AppGlyphs.Photos,
)

/** Declared app category → glyph (last resort before the monogram). */
private val CATEGORY_GLYPHS: Map<Int, List<String>> = mapOf(
    ApplicationInfo.CATEGORY_AUDIO to AppGlyphs.Music,
    ApplicationInfo.CATEGORY_VIDEO to AppGlyphs.Play,
    ApplicationInfo.CATEGORY_IMAGE to AppGlyphs.Photos,
    ApplicationInfo.CATEGORY_SOCIAL to AppGlyphs.Message,
    ApplicationInfo.CATEGORY_NEWS to AppGlyphs.News,
    ApplicationInfo.CATEGORY_MAPS to AppGlyphs.Map,
    ApplicationInfo.CATEGORY_PRODUCTIVITY to AppGlyphs.Note,
    ApplicationInfo.CATEGORY_GAME to AppGlyphs.Gamepad,
    ApplicationInfo.CATEGORY_ACCESSIBILITY to AppGlyphs.Settings,
)
