package io.eddiegulay.tempo.search

import io.eddiegulay.tempo.i18n.Lang
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Spotify in Search: a few title + artist rows, then a word that opens Spotify for the rest.
 *
 * Spotify's own search API needs a client id and secret. Tempo does not ship those. The rows
 * come from Apple's public iTunes Search catalog (no key). The store and the result language
 * follow Tempo's UI language — Japanese asks the JP store in ja_jp, English asks US in en_us —
 * the same switch that redraws Search. Tapping a row or もっと hands the same words to Spotify
 * via `spotify:search:` / open.spotify.com. Contacts and the agenda still never leave the device.
 */
data class SpotifyHit(
    val title: String,
    val artist: String,
)

const val SPOTIFY_RESULT_LIMIT = 5
const val SPOTIFY_DEBOUNCE_MS = 320L

fun shouldOfferSpotify(
    query: String,
    areas: SearchAreas,
    apps: List<AppMatch> = emptyList(),
): Boolean {
    if (!areas.spotify) return false
    val q = query.trim()
    if (q.length < 2) return false
    if (isNumberShaped(q) || isEmailShaped(q)) return false
    if (hasConfidentAppMatch(q, apps)) return false
    return true
}

fun encodeSearchQuery(raw: String): String =
    java.net.URLEncoder.encode(displayQuery(raw), Charsets.UTF_8.name()).replace("+", "%20")

fun spotifyAppSearchUri(query: String): String = "spotify:search:${encodeSearchQuery(query)}"

fun spotifyWebSearchUri(query: String): String =
    "https://open.spotify.com/search/${encodeSearchQuery(query)}"

fun spotifyTrackQuery(hit: SpotifyHit): String = displayQuery("${hit.title} ${hit.artist}")

/** Store and result language for the catalog, keyed on Tempo's UI language, not the device locale. */
data class CatalogStore(
    val country: String,
    val lang: String,
)

fun catalogStore(lang: Lang): CatalogStore = when (lang) {
    Lang.Ja -> CatalogStore(country = "JP", lang = "ja_jp")
    Lang.En -> CatalogStore(country = "US", lang = "en_us")
}

fun itunesSearchUrl(query: String, lang: Lang, limit: Int = SPOTIFY_RESULT_LIMIT): String {
    val store = catalogStore(lang)
    val q = encodeSearchQuery(query)
    return "https://itunes.apple.com/search?term=$q&media=music&entity=song&limit=$limit&country=${store.country}&lang=${store.lang}"
}

fun parseItunesSongs(json: String, limit: Int = SPOTIFY_RESULT_LIMIT): List<SpotifyHit> {
    val array = jsonArrayBody(json, "results") ?: return emptyList()
    val out = ArrayList<SpotifyHit>(limit)
    val seen = HashSet<String>()
    for (obj in jsonObjects(array)) {
        if (out.size >= limit) break
        if (jsonString(obj, "kind") != "song") continue
        val title = jsonString(obj, "trackName")?.trim().orEmpty()
        val artist = jsonString(obj, "artistName")?.trim().orEmpty()
        if (title.isEmpty() || artist.isEmpty()) continue
        val key = title.lowercase(Locale.ROOT) + "\u0000" + artist.lowercase(Locale.ROOT)
        if (!seen.add(key)) continue
        out.add(SpotifyHit(title, artist))
    }
    return out
}

/** Flat string fields from a JSON object. JVM unit tests cannot use Android's stub `org.json`. */
internal fun jsonArrayBody(json: String, key: String): String? {
    val needle = "\"$key\""
    var from = 0
    while (true) {
        val at = json.indexOf(needle, from)
        if (at < 0) return null
        var i = skipWs(json, at + needle.length)
        if (i >= json.length || json[i] != ':') {
            from = at + 1
            continue
        }
        i = skipWs(json, i + 1)
        if (i >= json.length || json[i] != '[') {
            from = at + 1
            continue
        }
        val end = matchingBracket(json, i, '[', ']')
        return if (end < 0) null else json.substring(i + 1, end)
    }
}

internal fun jsonObjects(arrayBody: String): List<String> {
    val out = ArrayList<String>()
    var i = 0
    while (i < arrayBody.length) {
        i = skipWs(arrayBody, i)
        if (i >= arrayBody.length) break
        if (arrayBody[i] == ',') {
            i++
            continue
        }
        if (arrayBody[i] != '{') break
        val end = matchingBracket(arrayBody, i, '{', '}')
        if (end < 0) break
        out.add(arrayBody.substring(i, end + 1))
        i = end + 1
    }
    return out
}

internal fun jsonString(obj: String, key: String): String? {
    val needle = "\"$key\""
    var from = 0
    while (true) {
        val at = obj.indexOf(needle, from)
        if (at < 0) return null
        var i = skipWs(obj, at + needle.length)
        if (i >= obj.length || obj[i] != ':') {
            from = at + 1
            continue
        }
        i = skipWs(obj, i + 1)
        if (i >= obj.length || obj[i] != '"') {
            from = at + 1
            continue
        }
        return decodeJsonString(obj, i + 1)
    }
}

private fun matchingBracket(s: String, openAt: Int, open: Char, close: Char): Int {
    var depth = 0
    var i = openAt
    var inString = false
    var escape = false
    while (i < s.length) {
        val c = s[i]
        if (inString) {
            when {
                escape -> escape = false
                c == '\\' -> escape = true
                c == '"' -> inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        i++
    }
    return -1
}

private fun decodeJsonString(s: String, start: Int): String {
    val out = StringBuilder()
    var i = start
    var escape = false
    while (i < s.length) {
        val c = s[i]
        if (escape) {
            when (c) {
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'u' -> {
                    if (i + 4 < s.length) {
                        out.append(s.substring(i + 1, i + 5).toInt(16).toChar())
                        i += 4
                    }
                }
                else -> out.append(c)
            }
            escape = false
        } else if (c == '\\') {
            escape = true
        } else if (c == '"') {
            return out.toString()
        } else {
            out.append(c)
        }
        i++
    }
    return out.toString()
}

private fun skipWs(s: String, start: Int): Int {
    var i = start
    while (i < s.length && s[i].isWhitespace()) i++
    return i
}

class MusicCatalog(
    private val get: (String) -> String? = ::httpGet,
) {
    fun search(query: String, lang: Lang): List<SpotifyHit> {
        val q = displayQuery(query)
        if (q.length < 2) return emptyList()
        val body = get(itunesSearchUrl(q, lang)) ?: return emptyList()
        return try {
            parseItunesSongs(body)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

internal fun httpGet(url: String): String? {
    val connection = URL(url).openConnection() as HttpURLConnection
    return try {
        connection.connectTimeout = 4_000
        connection.readTimeout = 6_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Tempo")
        connection.instanceFollowRedirects = true
        if (connection.responseCode !in 200..299) return null
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}
