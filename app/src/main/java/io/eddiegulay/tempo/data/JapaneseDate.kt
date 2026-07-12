package io.eddiegulay.tempo.data

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Japanese calendar / clock formatting, ported verbatim from the Tempo design prototype.
 *
 * The launcher shows time in kanji ("reading"), the date as a Reiwa-era string in vertical
 * kanji, and the day of week — all derived from these helpers so Home and Notifications stay
 * in lockstep with the design's wording.
 */
object JapaneseDate {

    private val K = charArrayOf('〇', '一', '二', '三', '四', '五', '六', '七', '八', '九')

    // Sunday-first, matching the prototype's JS `getDay()` indexing.
    private val DOW = charArrayOf('日', '月', '火', '水', '木', '金', '土')

    /** Kanji numeral for 0..99 (the only range the launcher needs). */
    fun kanji(n: Int): String {
        if (n < 10) return K[n].toString()
        if (n < 20) return "十" + if (n % 10 != 0) K[n % 10].toString() else ""
        val tens = n / 10
        val units = n % 10
        return K[tens].toString() + "十" + if (units != 0) K[units].toString() else ""
    }

    /** Digital HH:mm — used for the big clock numerals. */
    fun time(now: LocalDateTime): String = "%02d:%02d".format(now.hour, now.minute)

    /** Spoken-style reading, e.g. 午後九時一分 (afternoon, 9 o'clock, 1 minute). */
    fun reading(now: LocalDateTime): String {
        val h = now.hour
        val m = now.minute
        val h12 = (h % 12).let { if (it == 0) 12 else it }
        val meridiem = if (h < 12) "午前" else "午後"
        val minutes = if (m == 0) "" else kanji(m) + "分"
        return meridiem + kanji(h12) + "時" + minutes
    }

    /** Reiwa era year, e.g. 令和八年 for 2026. */
    fun era(now: LocalDateTime): String = "令和" + kanji(now.year - 2018) + "年"

    /** Month/day in kanji, e.g. 六月十七日. */
    fun monthDay(now: LocalDateTime): String =
        kanji(now.monthValue) + "月" + kanji(now.dayOfMonth) + "日"

    /** Day-of-week label, e.g. 水曜日. */
    fun dayOfWeek(now: LocalDateTime): String = dowChar(now) + "曜日"

    private fun dowChar(now: LocalDateTime): String {
        // java.time: MONDAY=1 .. SUNDAY=7; mod 7 maps SUNDAY->0 to match the design's table.
        val idx = now.dayOfWeek.value % 7
        return DOW[idx].toString()
    }

    // ----- calendar -----

    /**
     * An event's start in kanji, 24-hour: 十九時三十分, or 十九時 on the hour.
     *
     * Home's corner is the kanji artefact — the 104sp clock already owns digits there, and a second
     * numeral cluster four inches away would compete with it. The Calendar *page* uses digits
     * ([clock]) because that surface is a tool, not an artefact.
     */
    fun eventTime(at: LocalDateTime): String {
        val minutes = if (at.minute == 0) "" else kanji(at.minute) + "分"
        return kanji(at.hour) + "時" + minutes
    }

    /** Plain 09:30, for the Calendar page's event cards. */
    fun clock(at: LocalDateTime): String = "%02d:%02d".format(at.hour, at.minute)

    /**
     * How a date reads relative to today, collapsing as it gets closer: 今日 / 明日 / 水曜日 within the
     * week, and the kanji month-day beyond it.
     */
    fun dayToken(date: LocalDate, today: LocalDate): String = when {
        date == today -> "今日"
        date == today.plusDays(1) -> "明日"
        date.isBefore(today.plusDays(7)) -> dayOfWeek(date.atStartOfDay())
        else -> monthDay(date.atStartOfDay())
    }

    /** The fuller label a day-group header carries: 今日 / 明日 / 水曜日 ・ 六月十九日. */
    fun dayHeading(date: LocalDate, today: LocalDate): String = when {
        date == today -> "今日"
        date == today.plusDays(1) -> "明日"
        else -> dayOfWeek(date.atStartOfDay()) + " ・ " + monthDay(date.atStartOfDay())
    }
}
