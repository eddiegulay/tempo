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
