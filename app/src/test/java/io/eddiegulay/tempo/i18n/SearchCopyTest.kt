package io.eddiegulay.tempo.i18n

import android.content.pm.ApplicationInfo
import io.eddiegulay.tempo.search.appCategoryLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Search and Search areas copy. Japanese stays the words the pages shipped with. English is
 * its own table, and the kana line is absent rather than translated.
 */
class SearchCopyTest {

    private val ja = stringsFor(Lang.Ja)
    private val en = stringsFor(Lang.En)

    @Test
    fun `search headings stay Japanese and switch in English`() {
        assertEquals("けんさく", ja.search.heading)
        assertEquals("検索", ja.search.placeholder)
        assertEquals("見つかりません", ja.search.empty)
        assertEquals("search", en.search.heading)
        assertEquals("Search", en.search.placeholder)
        assertEquals("Nothing found", en.search.empty)
    }

    @Test
    fun `search areas kana is absent in English rather than translated`() {
        assertEquals("はんい", ja.searchAreas.kana)
        assertEquals("検索の範囲", ja.searchAreas.title)
        assertNull(en.searchAreas.kana)
        assertEquals("Search areas", en.searchAreas.title)
    }

    @Test
    fun `area toggles and people actions exist in both languages`() {
        assertEquals("入", ja.searchAreas.toggleOn)
        assertEquals("切", ja.searchAreas.toggleOff)
        assertEquals("On", en.searchAreas.toggleOn)
        assertEquals("Off", en.searchAreas.toggleOff)
        assertEquals("ひと", ja.search.peopleSection)
        assertEquals("people", en.search.peopleSection)
        assertEquals("Spotify", ja.search.spotifySection)
        assertEquals("Spotify", en.search.spotifySection)
        assertEquals("もっと", ja.search.spotifyOpen)
        assertEquals("Open Spotify", en.search.spotifyOpen)
        assertEquals("Spotify", ja.searchAreas.spotify)
        assertEquals("Spotify", en.searchAreas.spotify)
        assertTrue(ja.searchAreas.subtitle.contains("Spotify"))
        assertTrue(en.searchAreas.subtitle.contains("Spotify"))
        assertEquals("メッセージ", ja.search.contactMessage)
        assertEquals("Message", en.search.contactMessage)
        assertNotNull(ja.searchAreas.contactsNeedsAccess)
        assertNotNull(en.searchAreas.contactsNeedsAccess)
    }

    @Test
    fun `app categories follow the UI language, not the device locale`() {
        assertEquals("仕事", appCategoryLabel(ApplicationInfo.CATEGORY_PRODUCTIVITY, ja.search))
        assertEquals("Productivity", appCategoryLabel(ApplicationInfo.CATEGORY_PRODUCTIVITY, en.search))
        assertEquals("ゲーム", appCategoryLabel(ApplicationInfo.CATEGORY_GAME, ja.search))
        assertEquals("Games", appCategoryLabel(ApplicationInfo.CATEGORY_GAME, en.search))
        assertNull(appCategoryLabel(ApplicationInfo.CATEGORY_UNDEFINED, en.search))
    }
}
