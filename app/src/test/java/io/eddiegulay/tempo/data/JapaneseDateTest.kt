package io.eddiegulay.tempo.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests for [JapaneseDate] — the pure date/time → kanji formatting that drives the Home and
 * Notifications screens. No Android dependencies, so it runs as a fast JVM test.
 */
class JapaneseDateTest {

    @Test
    fun kanji_numerals_cover_zero_through_ninety_nine() {
        assertEquals("〇", JapaneseDate.kanji(0))
        assertEquals("七", JapaneseDate.kanji(7))
        assertEquals("十", JapaneseDate.kanji(10))
        assertEquals("十一", JapaneseDate.kanji(11))
        assertEquals("二十", JapaneseDate.kanji(20))
        assertEquals("二十一", JapaneseDate.kanji(21))
        assertEquals("四十二", JapaneseDate.kanji(42))
        assertEquals("九十九", JapaneseDate.kanji(99))
    }

    @Test
    fun kanjiExtended_delegates_the_first_hundred_to_kanji() {
        // The two functions must never disagree; there is only one numeral formatter in this app.
        for (n in 0..99) {
            assertEquals(JapaneseDate.kanji(n), JapaneseDate.kanjiExtended(n))
        }
    }

    @Test
    fun kanjiExtended_elides_the_one_before_hundred_and_thousand() {
        assertEquals("百", JapaneseDate.kanjiExtended(100))
        assertEquals("千", JapaneseDate.kanjiExtended(1000))
    }

    @Test
    fun kanjiExtended_writes_the_irregular_hundreds_and_thousands_plainly() {
        // さんびゃく / ろっぴゃく / はっぴゃく / さんぜん / はっせん are sound changes only — the
        // written form takes no variant, which is exactly the trap this test pins shut.
        assertEquals("三百", JapaneseDate.kanjiExtended(300))
        assertEquals("六百", JapaneseDate.kanjiExtended(600))
        assertEquals("八百", JapaneseDate.kanjiExtended(800))
        assertEquals("三千", JapaneseDate.kanjiExtended(3000))
        assertEquals("八千", JapaneseDate.kanjiExtended(8000))
    }

    @Test
    fun kanjiExtended_omits_the_units_of_a_trailing_zero() {
        assertEquals("百十", JapaneseDate.kanjiExtended(110))
        assertEquals("百一", JapaneseDate.kanjiExtended(101))
        assertEquals("二百", JapaneseDate.kanjiExtended(200))
        assertEquals("二百三十", JapaneseDate.kanjiExtended(230))
        assertEquals("千十", JapaneseDate.kanjiExtended(1010))
        assertEquals("千一", JapaneseDate.kanjiExtended(1001))
        assertEquals("千百", JapaneseDate.kanjiExtended(1100))
        assertEquals("二千三百", JapaneseDate.kanjiExtended(2300))
        assertEquals("五千五", JapaneseDate.kanjiExtended(5005))
    }

    @Test
    fun kanjiExtended_composes_every_place_when_every_place_is_filled() {
        assertEquals("百二十三", JapaneseDate.kanjiExtended(123))
        assertEquals("四百五十六", JapaneseDate.kanjiExtended(456))
        assertEquals("千二百三十四", JapaneseDate.kanjiExtended(1234))
        assertEquals("九千八百七十六", JapaneseDate.kanjiExtended(9876))
    }

    @Test
    fun kanjiExtended_holds_at_every_boundary() {
        assertEquals("九十九", JapaneseDate.kanjiExtended(99))
        assertEquals("百", JapaneseDate.kanjiExtended(100))
        assertEquals("九百九十九", JapaneseDate.kanjiExtended(999))
        assertEquals("千", JapaneseDate.kanjiExtended(1000))
        assertEquals("九千九百九十九", JapaneseDate.kanjiExtended(9999))
        // 万 is where Japanese switches from thousands to ten-thousands, and where kanji stops
        // being readable at a glance. Arabic from here up.
        assertEquals("10000", JapaneseDate.kanjiExtended(10000))
        assertEquals("123456", JapaneseDate.kanjiExtended(123456))
        assertEquals(Int.MAX_VALUE.toString(), JapaneseDate.kanjiExtended(Int.MAX_VALUE))
    }

    @Test
    fun kanjiExtended_shows_a_negative_as_arabic_rather_than_dressing_up_a_bug() {
        assertEquals("-1", JapaneseDate.kanjiExtended(-1))
        assertEquals("-42", JapaneseDate.kanjiExtended(-42))
        assertEquals(Int.MIN_VALUE.toString(), JapaneseDate.kanjiExtended(Int.MIN_VALUE))
    }

    @Test
    fun era_uses_reiwa_offset() {
        // 2019 = Reiwa 1, so 2026 = Reiwa 8.
        assertEquals("令和八年", JapaneseDate.era(at(2026, 6, 17, 9, 0)))
        assertEquals("令和一年", JapaneseDate.era(at(2019, 5, 1, 0, 0)))
    }

    @Test
    fun monthDay_is_kanji() {
        assertEquals("六月十七日", JapaneseDate.monthDay(at(2026, 6, 17, 9, 0)))
        assertEquals("一月一日", JapaneseDate.monthDay(at(2026, 1, 1, 0, 0)))
    }

    @Test
    fun dayOfWeek_maps_sunday_first() {
        // 2026-06-17 is a Wednesday.
        assertEquals("水曜日", JapaneseDate.dayOfWeek(at(2026, 6, 17, 9, 0)))
        // 2026-06-21 is a Sunday.
        assertEquals("日曜日", JapaneseDate.dayOfWeek(at(2026, 6, 21, 9, 0)))
    }

    @Test
    fun time_is_zero_padded_24h() {
        assertEquals("09:05", JapaneseDate.time(at(2026, 6, 17, 9, 5)))
        assertEquals("00:00", JapaneseDate.time(at(2026, 6, 17, 0, 0)))
        assertEquals("23:59", JapaneseDate.time(at(2026, 6, 17, 23, 59)))
    }

    @Test
    fun reading_uses_meridiem_and_drops_zero_minutes() {
        assertEquals("午前九時五分", JapaneseDate.reading(at(2026, 6, 17, 9, 5)))
        assertEquals("午後一時三十分", JapaneseDate.reading(at(2026, 6, 17, 13, 30)))
        // On the hour: minutes are omitted.
        assertEquals("午前十二時", JapaneseDate.reading(at(2026, 6, 17, 0, 0)))
        assertEquals("午後十二時", JapaneseDate.reading(at(2026, 6, 17, 12, 0)))
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): LocalDateTime =
        LocalDateTime.of(year, month, day, hour, minute)
}
