package io.eddiegulay.tempo.search

import io.eddiegulay.tempo.i18n.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spotify in Search: when the area may appear, how iTunes JSON becomes title + artist,
 * and the URIs that hand the words to Spotify. No network in these tests.
 */
class SpotifySearchTest {

    @Test
    fun `a song-shaped query is offered when the area is on`() {
        assertTrue(shouldOfferSpotify("Bohemian", SearchAreas()))
        assertTrue(shouldOfferSpotify("宇多田", SearchAreas()))
        assertFalse(shouldOfferSpotify("B", SearchAreas()))
        assertFalse(shouldOfferSpotify("0712 345 678", SearchAreas()))
        assertFalse(shouldOfferSpotify("mina@ground.work", SearchAreas()))
        assertFalse(shouldOfferSpotify("Bohemian", SearchAreas(spotify = false)))
    }

    @Test
    fun `a confident app match hides the Spotify block`() {
        val chrome = listOf(AppMatch("Chrome", "com.android.chrome"))
        assertFalse(shouldOfferSpotify("Chr", SearchAreas(), chrome))
        assertFalse(shouldOfferSpotify("Chrome", SearchAreas(), chrome))
        assertTrue(shouldOfferSpotify("Queen", SearchAreas(), chrome))
    }

    @Test
    fun `itunes songs keep title and artist and drop duplicates`() {
        val json = """
            {
              "resultCount": 3,
              "results": [
                {"kind":"song","trackName":"Bohemian Rhapsody","artistName":"Queen"},
                {"kind":"song","trackName":"Bohemian Rhapsody","artistName":"Queen"},
                {"kind":"feature-movie","trackName":"Bohemian Rhapsody","artistName":"A film"},
                {"kind":"song","trackName":"Bohemian Rhapsody","artistName":"Panic! At the Disco"}
              ]
            }
        """.trimIndent()
        assertEquals(
            listOf(
                SpotifyHit("Bohemian Rhapsody", "Queen"),
                SpotifyHit("Bohemian Rhapsody", "Panic! At the Disco"),
            ),
            parseItunesSongs(json),
        )
    }

    @Test
    fun `itunes parse stays empty on junk`() {
        assertTrue(parseItunesSongs("{}").isEmpty())
        assertTrue(MusicCatalog(get = { "not-json" }).search("queen", Lang.Ja).isEmpty())
        assertTrue(MusicCatalog(get = { null }).search("queen", Lang.En).isEmpty())
    }

    @Test
    fun `catalog search uses the injected body`() {
        val catalog = MusicCatalog(
            get = { url ->
                assertTrue(url.contains("term=queen"))
                assertTrue(url.contains("country=JP"))
                assertTrue(url.contains("lang=ja_jp"))
                """{"results":[{"kind":"song","trackName":"Queen Of Peace","artistName":"Florence + The Machine"}]}"""
            },
        )
        assertEquals(
            listOf(SpotifyHit("Queen Of Peace", "Florence + The Machine")),
            catalog.search("queen", Lang.Ja),
        )
    }

    @Test
    fun `the catalog store follows Tempo language not the device locale`() {
        assertEquals(CatalogStore("JP", "ja_jp"), catalogStore(Lang.Ja))
        assertEquals(CatalogStore("US", "en_us"), catalogStore(Lang.En))
        val english = itunesSearchUrl("queen", Lang.En)
        assertTrue(english.contains("country=US"))
        assertTrue(english.contains("lang=en_us"))
        val japanese = itunesSearchUrl("宇多田", Lang.Ja)
        assertTrue(japanese.contains("country=JP"))
        assertTrue(japanese.contains("lang=ja_jp"))
        assertTrue(japanese.contains("term="))
    }

    @Test
    fun `spotify uris encode the query and keep title with artist`() {
        assertEquals("spotify:search:Bohemian%20Rhapsody", spotifyAppSearchUri("Bohemian Rhapsody"))
        assertEquals(
            "https://open.spotify.com/search/Bohemian%20Rhapsody",
            spotifyWebSearchUri("Bohemian Rhapsody"),
        )
        assertEquals(
            "Bohemian Rhapsody Queen",
            spotifyTrackQuery(SpotifyHit("Bohemian Rhapsody", "Queen")),
        )
    }

    @Test
    fun `the open intent prefers the Spotify scheme then the website`() {
        val intents = repoFile("src/main/java/io/eddiegulay/tempo/search/SearchHandOffIntents.kt").readText()
        val start = intents.indexOf("fun launchSpotifySearch(")
        val end = intents.indexOf("\nfun launchContactWhatsApp(", start)
        val body = intents.substring(start, end)
        assertTrue(body.contains("spotifyAppSearchUri"))
        assertTrue(body.contains("spotifyWebSearchUri"))
        assertFalse("must not pin Spotify's package", body.contains("setPackage"))
    }

    @Test
    fun `search rows are title and artist only`() {
        val screen = repoFile("src/main/java/io/eddiegulay/tempo/ui/SearchScreen.kt").readText()
        val start = screen.indexOf("private fun SpotifyHitRow(")
        val end = screen.indexOf("\n@Composable\nprivate fun CalendarHitRow(", start)
        val body = screen.substring(start, end)
        assertTrue(body.contains("hit.title"))
        assertTrue(body.contains("hit.artist"))
        assertFalse(body.contains("artwork"))
        assertFalse(body.contains("LineIcon"))
        assertFalse(body.contains("AppGlyphs"))
    }

    @Test
    fun `a language switch refetches the catalog`() {
        val vm = repoFile("src/main/java/io/eddiegulay/tempo/LauncherViewModel.kt").readText()
        val start = vm.indexOf("combine(_searchQuery, searchAreas, visibleApps, lang)")
        assertTrue("Spotify combine must include lang", start >= 0)
        val body = vm.substring(start, start + 700)
        assertTrue(body.contains("musicCatalog.search(query, language)"))
    }

    private fun repoFile(relative: String): java.io.File {
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            java.io.File(dir, relative).takeIf { it.isFile }?.let { return it }
            java.io.File(dir, "app/$relative").takeIf { it.isFile }?.let { return it }
            dir = dir.parentFile
        }
        throw AssertionError("$relative not found from ${System.getProperty("user.dir")}")
    }
}
