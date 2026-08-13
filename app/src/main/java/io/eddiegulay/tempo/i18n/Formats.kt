package io.eddiegulay.tempo.i18n

import io.eddiegulay.tempo.data.JapaneseDate
import io.eddiegulay.tempo.gym.clockDuration
import io.eddiegulay.tempo.gym.coefficientLabel
import io.eddiegulay.tempo.gym.durationKanji
import io.eddiegulay.tempo.gym.durationKanjiFromMs
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Numbers, dates and counters — the half of localisation that is behaviour rather than copy.
 *
 * A string table cannot hold any of this. `一分三十秒` is not a translatable phrase, it is the output of
 * a function over 90; `十四回` is a numeral and a counter glued together at ~215 call sites. So this is
 * an interface with the same two implementations the copy has, reached the same way — `strings.fmt`.
 *
 * **[JaFormats] delegates to the existing formatters and changes nothing.** `JapaneseDate` and
 * `Numerals` keep their behaviour, their KDoc and — the point — their tests: `JapaneseDateTest`,
 * `NumeralsTest`, `KanaFoldingTest` and `TategakiTest` are ~150 literals of tests where *Japanese is
 * the subject matter*, not the copy. Rewriting them to go through a table would have been a large
 * change that verified less. They stay exactly as they are.
 *
 * **Two rules die here, and they are named rather than quietly dropped** (§L7):
 *
 * 1. §Q4 — *a ticking value is arabic, a stopped value is kanji.* On PAUSED the same instant is drawn
 *    `0:23` and spoken `残り 二十三秒`, deliberately.
 * 2. §Q10 — *a duration you chose renders bare (`六十秒`), one the app measured is spelled out
 *    (`一分三十秒`).*
 *
 * Both are carried by **orthography**, and English has only one. So in English [count] and [duration]
 * are the same digits either way, and the distinction is gone — not translated, gone. Japanese keeps
 * both rules unchanged, which is why they still exist in [JaFormats].
 */
interface Formats {

    // ─── numerals ───────────────────────────────────────────────────────────────────────────────

    /**
     * A number that has stopped moving: a total, a tally, a count in a read-only row.
     *
     * Japanese renders kanji (§Q4's "stopped" half); English renders digits. **Never use this for a
     * value that is ticking** — a kanji column changing under the finger is unreadable, which is the
     * whole reason the rule exists. Ticking values go through [clock].
     */
    fun count(n: Int): String

    /** `m:ss`, for anything the user is watching change. Identical in both languages. */
    fun clock(millis: Long): String

    /** A duration you read rather than watch: `五秒` / `45s`, `一分三十秒` / `1m 30s`. */
    fun duration(totalSeconds: Int): String

    /** [duration] from a millisecond clock, truncated rather than rounded. */
    fun durationFromMs(millis: Long): String

    /** A difficulty coefficient: `一.〇` / `1.0`. Null, negative or non-finite renders as no value. */
    fun coefficient(value: Double?): String

    // ─── counters ───────────────────────────────────────────────────────────────────────────────

    /*
     * Japanese counters are suffixes on a numeral and never inflect. English needs a **different word
     * shape per count**, which is why each of these is a function rather than a `count(n) + suffix`
     * at the call site — the call site cannot know that "1 rep" and "2 reps" differ, and 回 alone
     * carries three distinct English plurals depending on what is being counted.
     */

    /** 回 — repetitions of a movement. */
    fun reps(n: Int): String

    /** 巡 — laps of a circuit. */
    fun rounds(n: Int): String

    /** 種目 — exercises within a routine. */
    fun stations(n: Int): String

    /** 秒 — a seconds value the user set, not one counting down. */
    fun seconds(n: Int): String

    /** 分 — a minutes value. */
    fun minutes(n: Int): String

    /** 日 — days. */
    fun days(n: Int): String

    /** 月 — months. */
    fun months(n: Int): String

    /** 件 — a generic count of things in a list. */
    fun items(n: Int): String

    /** 番目 — position in a sequence: `三番目` / `3rd`. */
    fun ordinal(n: Int): String

    /** 回 as *occasions* rather than repetitions: 「これまでの十四回」 / "14 times". */
    fun times(n: Int): String

    // ─── dates and clocks ───────────────────────────────────────────────────────────────────────

    /** `21:04` — the big clock's digits. Identical in both languages. */
    fun time(now: LocalDateTime): String

    /** The gloss under the clock: `午後九時四分` / `9:04 pm`. */
    fun reading(now: LocalDateTime): String

    /** `令和八年` / `Reiwa 8` — see §L9 for why English keeps the era rather than showing 2026. */
    fun era(now: LocalDateTime): String

    /** `六月十七日` / `17 June`. */
    fun monthDay(now: LocalDateTime): String

    /** `水曜日` / `Wednesday`. */
    fun dayOfWeek(now: LocalDateTime): String

    /**
     * The seven column headers on the records ink grid, Sunday first.
     *
     * A list rather than seven strings because the grid iterates it, and because the cell is 20.dp
     * wide — one CJK glyph, or about three Latin characters. English cannot use full weekday names
     * here and the initials collide (T/T, S/S); that is a known and accepted trade for the layout.
     */
    fun weekdayInitials(): List<String>

    /** An event's start time: `十九時三十分` / `19:30`. */
    fun eventTime(at: LocalDateTime): String

    /** `09:30` — the Calendar page's cards, which are a tool rather than an artefact. */
    fun clockAt(at: LocalDateTime): String

    /** How a **future** date reads: `今日` / `明日` / weekday / month-day. */
    fun dayToken(date: LocalDate, today: LocalDate): String

    /** A day-group header: `今日` / `水曜日 ・ 六月十九日`. */
    fun dayHeading(date: LocalDate, today: LocalDate): String

    /**
     * How a **past** date reads: `きょう` / `きのう` / `三日前`.
     *
     * Japanese keeps two vocabularies on purpose — hiragana looking backwards, kanji looking forwards
     * — which `GymHomeCopy` documents at length. **English has one word for both**, so [dayToken] and
     * this function return the same "Today". That collapse deletes a documented distinction rather
     * than translating it (§L7).
     */
    fun relativeDay(then: LocalDate, today: LocalDate): String

    // ─── punctuation ────────────────────────────────────────────────────────────────────────────

    /**
     * The separator between fields of a composed line: ` ・ ` / ` · `.
     *
     * A CJK middle dot in ~20 concatenations today. It is punctuation rather than content, but it is
     * *locale-dependent* punctuation, so it belongs here and not hard-coded twenty times.
     */
    val separator: String

    /** The separator inside an enumeration: `、` / `, `. */
    val listSeparator: String

    /** What a value renders as when there is none. An em dash in both languages — never `〇`, never blank. */
    val noValue: String
}

/**
 * Japanese — a thin façade over the formatters that already existed.
 *
 * Every method here delegates. That is the point: the behaviour, the edge cases and the tests are
 * unchanged, and this file adds an interface in front of them rather than a second implementation.
 * `Numerals.kt:9-13` forbids a second kanji numeral formatter and it is still right.
 */
internal object JaFormats : Formats {

    override fun count(n: Int): String = JapaneseDate.kanjiExtended(n)
    override fun clock(millis: Long): String = clockDuration(millis)
    override fun duration(totalSeconds: Int): String = durationKanji(totalSeconds)
    override fun durationFromMs(millis: Long): String = durationKanjiFromMs(millis)
    override fun coefficient(value: Double?): String = coefficientLabel(value)

    override fun reps(n: Int): String = count(n) + "回"
    override fun rounds(n: Int): String = count(n) + "巡"
    override fun stations(n: Int): String = count(n) + "種目"
    override fun seconds(n: Int): String = count(n) + "秒"
    override fun minutes(n: Int): String = count(n) + "分"
    override fun days(n: Int): String = count(n) + "日"
    override fun months(n: Int): String = count(n) + "月"
    override fun items(n: Int): String = count(n) + "件"
    override fun ordinal(n: Int): String = count(n) + "番目"
    override fun times(n: Int): String = count(n) + "回"

    override fun time(now: LocalDateTime): String = JapaneseDate.time(now)
    override fun reading(now: LocalDateTime): String = JapaneseDate.reading(now)
    override fun era(now: LocalDateTime): String = JapaneseDate.era(now)
    override fun monthDay(now: LocalDateTime): String = JapaneseDate.monthDay(now)
    override fun dayOfWeek(now: LocalDateTime): String = JapaneseDate.dayOfWeek(now)
    override fun weekdayInitials(): List<String> = JA_WEEKDAYS
    override fun eventTime(at: LocalDateTime): String = JapaneseDate.eventTime(at)
    override fun clockAt(at: LocalDateTime): String = JapaneseDate.clock(at)
    override fun dayToken(date: LocalDate, today: LocalDate): String = JapaneseDate.dayToken(date, today)
    override fun dayHeading(date: LocalDate, today: LocalDate): String = JapaneseDate.dayHeading(date, today)

    override fun relativeDay(then: LocalDate, today: LocalDate): String {
        val days = today.toEpochDay() - then.toEpochDay()
        return when {
            days <= 0L -> "きょう"
            days == 1L -> "きのう"
            else -> count(days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) + "日前"
        }
    }

    override val separator = " ・ "
    override val listSeparator = "、"
    override val noValue = "—"

    /** Sunday-first, matching `JapaneseDate.kt:18`. */
    private val JA_WEEKDAYS = listOf("日", "月", "火", "水", "木", "金", "土")
}

/**
 * English.
 *
 * Compact on purpose. Length is a functional constraint in this app rather than a stylistic one —
 * several slots are measured in glyphs, and the player's hero cap divides its width by 4.2 ("four
 * glyphs plus slack") and **clips rather than ellipsises**. `6m 14s` fits where `6 minutes 14 seconds`
 * does not, and the compact form is also what a training app reads like.
 */
internal object EnFormats : Formats {

    override fun count(n: Int): String = n.toString()
    override fun clock(millis: Long): String = clockDuration(millis)

    /**
     * `45s` · `20m` · `6m 14s`.
     *
     * Inherits two of `durationKanji`'s rules and refuses the third. **Whole minutes drop their
     * seconds** — 1200 is `20m`, not `20m 0s` — which is the same judgement in both languages. A
     * non-positive value reads as `0s`, because durations are differences of monotonic clocks and a
     * negative one has already been mismeasured.
     *
     * What it does **not** inherit is the absence of hours. `durationKanji` renders 6000 seconds as
     * 百分 — literally "100 minutes" — which the Japanese specs chose deliberately and which is simply
     * not acceptable English. Past an hour this says `1h 40m`.
     */
    override fun duration(totalSeconds: Int): String {
        if (totalSeconds <= 0) return "0s"
        if (totalSeconds < 60) return "${totalSeconds}s"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 && minutes == 0 -> "${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            seconds == 0 -> "${minutes}m"
            else -> "${minutes}m ${seconds}s"
        }
    }

    override fun durationFromMs(millis: Long): String =
        duration(if (millis <= 0L) 0 else (millis / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())

    override fun coefficient(value: Double?): String {
        if (value == null || !value.isFinite() || value < 0.0) return noValue
        return "%.1f".format(java.util.Locale.ROOT, value)
    }

    // Plurals, spelled out rather than run through a rule. There are ten of them and the rule would
    // be "add s except when it is not", which is not less code and is harder to read.
    override fun reps(n: Int) = plural(n, "rep", "reps")
    override fun rounds(n: Int) = plural(n, "round", "rounds")
    override fun stations(n: Int) = plural(n, "station", "stations")
    override fun seconds(n: Int) = "${n}s"
    override fun minutes(n: Int) = "${n}m"
    override fun days(n: Int) = plural(n, "day", "days")
    override fun months(n: Int) = plural(n, "month", "months")
    override fun items(n: Int) = plural(n, "item", "items")
    override fun times(n: Int) = plural(n, "time", "times")

    /**
     * `1st` · `2nd` · `3rd` · `4th` · `11th` · `21st`.
     *
     * The teens are the whole difficulty and are the reason this is not `n + "th"`: 11, 12 and 13 take
     * `th` despite ending in 1, 2 and 3, and they are exactly the range a routine's station list
     * reaches.
     */
    override fun ordinal(n: Int): String {
        val suffix = when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }

    override fun time(now: LocalDateTime): String = JapaneseDate.time(now)

    /**
     * A 12-hour gloss under the 24-hour clock: `9:04 pm`.
     *
     * The Japanese is 午後九時四分, a *spoken-style* reading of the digits above it. English has no
     * idiomatic equivalent that is not just re-reading them, so this does the nearest honest thing —
     * the same instant in the other clock convention, which is genuinely additional information to a
     * reader who thinks in twelves. Lower case, because it is the quiet line under a 104sp number.
     */
    override fun reading(now: LocalDateTime): String {
        val h12 = (now.hour % 12).let { if (it == 0) 12 else it }
        val meridiem = if (now.hour < 12) "am" else "pm"
        return "%d:%02d %s".format(java.util.Locale.ROOT, h12, now.minute, meridiem)
    }

    /** `Reiwa 8` — kept rather than replaced with `2026`; see §L9. */
    override fun era(now: LocalDateTime): String = "Reiwa ${now.year - 2018}"

    override fun monthDay(now: LocalDateTime): String = "${now.dayOfMonth} ${MONTHS[now.monthValue - 1]}"
    override fun dayOfWeek(now: LocalDateTime): String = DAYS[now.dayOfWeek.value % 7]
    override fun weekdayInitials(): List<String> = EN_WEEKDAYS
    override fun eventTime(at: LocalDateTime): String = JapaneseDate.clock(at)
    override fun clockAt(at: LocalDateTime): String = JapaneseDate.clock(at)

    override fun dayToken(date: LocalDate, today: LocalDate): String = when {
        date == today -> "Today"
        date == today.plusDays(1) -> "Tomorrow"
        date.isBefore(today.plusDays(7)) -> dayOfWeek(date.atStartOfDay())
        else -> monthDay(date.atStartOfDay())
    }

    override fun dayHeading(date: LocalDate, today: LocalDate): String = when {
        date == today -> "Today"
        date == today.plusDays(1) -> "Tomorrow"
        else -> dayOfWeek(date.atStartOfDay()) + separator + monthDay(date.atStartOfDay())
    }

    override fun relativeDay(then: LocalDate, today: LocalDate): String {
        val days = today.toEpochDay() - then.toEpochDay()
        return when {
            days <= 0L -> "Today"
            days == 1L -> "Yesterday"
            else -> "${days} days ago"
        }
    }

    override val separator = " · "
    override val listSeparator = ", "
    override val noValue = "—"

    private fun plural(n: Int, one: String, many: String): String = "$n ${if (n == 1) one else many}"

    private val MONTHS = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    private val DAYS = listOf(
        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday",
    )

    /**
     * Sunday first, matching the Japanese grid rather than the ISO Monday-first convention English
     * readers in Europe expect. Deliberate: the ink grid's shape is the same artefact in both
     * languages, and `InkDensity` derives its leading blank from the same indexing. Changing the week
     * start is a product decision about the grid, not about the language (H-21).
     */
    private val EN_WEEKDAYS = listOf("S", "M", "T", "W", "T", "F", "S")
}
