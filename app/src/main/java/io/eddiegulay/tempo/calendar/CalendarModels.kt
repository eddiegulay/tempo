package io.eddiegulay.tempo.calendar

import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * One occurrence of a calendar event, as expanded by the provider's Instances table.
 *
 * A recurring series is a single Events row carrying an RRULE, so a "weekly standup" created years
 * ago would never match a query for today. Instances is the provider-maintained expansion of that
 * series into concrete occurrences, which is what an agenda actually needs — hence every field here
 * describes *this occurrence*, while [eventId] points back at the row that owns it.
 */
data class CalendarEvent(
    /** Events._ID — the row to update/delete. Shared by every occurrence of a recurring series. */
    val eventId: Long,
    val title: String,
    /** Local wall-clock start. All-day events are re-anchored to local midnight (see [allDayToLocal]). */
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String?,
    val calendarId: Long,
    val calendarName: String,
    /** Packed ARGB from the provider's DISPLAY_COLOR (event colour if set, else the calendar's). */
    val color: Int,
    /** True when this occurrence belongs to a repeating series. */
    val recurring: Boolean,
    /** True when the owning calendar grants write access (a subscribed holiday feed does not). */
    val writable: Boolean,
) {
    /**
     * Stable identity for list keys. Instances._ID is a row id in a table the provider regenerates,
     * so it must never be persisted — event id plus occurrence start is the durable pair.
     */
    val key: String get() = "$eventId:$begin"

    /** Editable in Tempo's own composer. Recurring series hand off to the system calendar instead. */
    val editable: Boolean get() = writable && !recurring

    fun startDateTime(): LocalDateTime = toLocal(begin)

    fun endDateTime(): LocalDateTime = toLocal(end)

    /** The local date this occurrence sits under in a day-grouped agenda. */
    fun date(): LocalDate = startDateTime().toLocalDate()

    fun isOngoing(nowMillis: Long): Boolean = begin <= nowMillis && end > nowMillis
}

/** A calendar on the device — one per synced account, plus any local or subscribed ones. */
data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val isPrimary: Boolean,
    val accessLevel: Int,
) {
    /** Only CONTRIBUTOR and above may host new events; anything less is a read-only feed. */
    val writable: Boolean
        get() = accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
}

/** The fields Tempo's composer can set. Everything else is left to the real calendar app. */
data class EventDraft(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val location: String,
    val calendarId: Long,
)

private fun toLocal(millis: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

/**
 * All-day events store midnight *UTC* on the event's date, not local midnight — an all-day event is
 * a date, not an instant, and must land on the same date in every timezone. Read naively in UTC+3 a
 * July 12th all-day event renders at 03:00; in UTC-5 it renders on July *11th*. Re-anchor to local
 * midnight so date grouping and rendering are correct everywhere.
 */
fun allDayToLocal(utcMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(utcMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

/** The inverse of [allDayToLocal]: a local date back to the midnight-UTC anchor the provider wants. */
fun localDateToAllDayUtc(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun LocalDateTime.toEpochMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    atZone(zone).toInstant().toEpochMilli()
