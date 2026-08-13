package io.eddiegulay.tempo.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/** How far ahead the agenda looks. Instances requires a bounded window — there is no "all events". */
private const val AGENDA_DAYS = 14L

/** Instances materialises every occurrence in the window; cap what we hold in memory. */
private const val MAX_INSTANCES = 60

/** Sync adapters notify per row batch, so a single sync fires a burst of change callbacks. */
private const val CHANGE_DEBOUNCE_MS = 300L

private val INSTANCE_PROJECTION = arrayOf(
    CalendarContract.Instances.EVENT_ID,               // 0
    CalendarContract.Instances.TITLE,                  // 1
    CalendarContract.Instances.BEGIN,                  // 2
    CalendarContract.Instances.END,                    // 3
    CalendarContract.Instances.ALL_DAY,                // 4
    CalendarContract.Instances.EVENT_LOCATION,         // 5
    CalendarContract.Instances.CALENDAR_ID,            // 6
    CalendarContract.Instances.CALENDAR_DISPLAY_NAME,  // 7
    CalendarContract.Instances.DISPLAY_COLOR,          // 8
    CalendarContract.Instances.RRULE,                  // 9
    CalendarContract.Instances.CALENDAR_ACCESS_LEVEL,  // 10
    CalendarContract.Instances.SELF_ATTENDEE_STATUS,   // 11
)

private val CALENDAR_PROJECTION = arrayOf(
    CalendarContract.Calendars._ID,                   // 0
    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, // 1
    CalendarContract.Calendars.ACCOUNT_NAME,          // 2
    CalendarContract.Calendars.ACCOUNT_TYPE,          // 3
    CalendarContract.Calendars.CALENDAR_COLOR,        // 4
    CalendarContract.Calendars.IS_PRIMARY,            // 5
    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, // 6
)

/**
 * Reads and writes the device's calendar through [CalendarContract] — the same provider the Google
 * Calendar sync adapter writes into, so Tempo shows exactly what the user's other devices show, with
 * no OAuth, no network and no Play Services.
 *
 * Writes are ordinary provider mutations: the row is marked dirty and the sync adapter pushes it back
 * to Google on its own. Tempo never sets sync columns or CALLER_IS_SYNCADAPTER — doing so would bypass
 * that dirty-tracking and strand the change on-device.
 */
class CalendarRepository(context: Context) {

    private val appContext = context.applicationContext
    private val resolver get() = appContext.contentResolver

    /** Fires a fresh query on demand — the "もう一度" behind a failed agenda load. */
    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun retry() {
        retries.tryEmit(Unit)
    }

    /**
     * The agenda for the next [AGENDA_DAYS], re-emitted whenever the calendar database changes —
     * our own writes, an edit made in the Google Calendar app, or a sync pulling down a remote change.
     *
     * Emits [Loadable.Loading] until the first query lands, and [Loadable.Failed] — never an empty
     * list — when the provider can't be read. The distinction is the whole point: an empty agenda is
     * a fact about the user's day, and we may only state it when we actually know it to be true.
     *
     * The caller should only collect this once READ_CALENDAR is granted, but permission can also be
     * revoked while the process is alive, so a mid-flight SecurityException is classified rather than
     * swallowed and surfaced as [CalendarFault.PermissionLost].
     */
    @OptIn(FlowPreview::class)
    fun events(): Flow<Loadable<List<CalendarEvent>>> = callbackFlow {
        val trigger = Channel<Unit>(Channel.CONFLATED)

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trigger.trySend(Unit)
            }
        }
        // Observe the provider root with notifyForDescendants: the provider notifies changes against
        // descendant URIs, and a time-windowed Instances URI is never itself notified.
        resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)

        val scope = CoroutineScope(coroutineContext)
        val pump = scope.launch {
            trigger.consumeAsFlow()
                .debounce(CHANGE_DEBOUNCE_MS)
                .collect { send(runCatching { Loadable.Ready(queryAgenda()) }.getOrElse { Loadable.Failed(it.toFault()) }) }
        }
        // A retry has to bypass the debounce, or the first tap after a failure appears to do nothing.
        val retryPump = scope.launch {
            retries.collect { send(runCatching { Loadable.Ready(queryAgenda()) }.getOrElse { Loadable.Failed(it.toFault()) }) }
        }
        trigger.trySend(Unit) // initial load

        awaitClose {
            pump.cancel()
            retryPump.cancel()
            resolver.unregisterContentObserver(observer)
        }
    }
        .onStart { emit(Loadable.Loading) }
        // The flow itself failing (the observer registration, say) is still a fault the user can see
        // and retry — it must never collapse into an innocent-looking empty agenda.
        .catch { emit(Loadable.Failed(it.toFault())) }
        .flowOn(Dispatchers.IO)

    /** @throws SecurityException when access was revoked mid-flight; classified by the caller. */
    private fun queryAgenda(): List<CalendarEvent> {
        val zone = ZoneId.systemDefault()
        // Pad the window by a day on each side: an all-day event's UTC anchor can fall outside a
        // window built from local day boundaries. We re-anchor and filter precisely below.
        val today = LocalDate.now(zone)
        val from = today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(AGENDA_DAYS + 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val cutoff = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val horizon = today.plusDays(AGENDA_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, from)
            ContentUris.appendId(this, to)
        }.build()

        // VISIBLE respects the user's own calendar checkboxes; without it we'd resurrect the spam
        // "Holidays in ..." calendar they unticked. Cancelled occurrences of a series are tombstones.
        val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND " +
            "(${CalendarContract.Instances.STATUS} IS NULL OR " +
            "${CalendarContract.Instances.STATUS} != ${CalendarContract.Events.STATUS_CANCELED})"

        val cursor = resolver.query(
            uri,
            INSTANCE_PROJECTION,
            selection,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        ) ?: return emptyList()

        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    // Declined meetings are hidden by Google Calendar itself; an agenda that shows
                    // them is noise.
                    if (c.getInt(11) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue

                    val allDay = c.getInt(4) == 1
                    val begin = c.getLong(2).let { if (allDay) allDayToLocal(it, zone) else it }
                    val end = c.getLong(3).let { if (allDay) allDayToLocal(it, zone) else it }

                    // Drop yesterday's padding and anything past the real horizon; keep events that
                    // are already running (they are the most relevant thing on the page).
                    if (end <= cutoff || begin >= horizon) continue

                    val access = c.getInt(10)
                    add(
                        CalendarEvent(
                            eventId = c.getLong(0),
                            // The provider's title as it stands, blank included. A placeholder here
                            // would be prefilled into the composer, would be what enabled 保存, and
                            // would be written straight back to Events.TITLE — so opening an untitled
                            // event to move it by an hour would name it, and the sync adapter would
                            // carry that name to the user's account, their other devices and every
                            // guest on the invite. What a blank title looks like is decided where it
                            // is drawn: see [displayTitle].
                            title = c.getString(1).orEmpty(),
                            begin = begin,
                            end = end,
                            allDay = allDay,
                            location = c.getString(5)?.takeIf { it.isNotBlank() },
                            calendarId = c.getLong(6),
                            calendarName = c.getString(7).orEmpty(),
                            // DISPLAY_COLOR frequently arrives with a zero alpha byte; force opacity
                            // or the dot paints as nothing.
                            color = c.getInt(8) or (0xFF shl 24),
                            recurring = !c.getString(9).isNullOrEmpty(),
                            writable = access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                        ),
                    )
                }
            }.take(MAX_INSTANCES)
        }
    }

    /**
     * Calendars that can host a new event, best default first.
     *
     * A [Loadable.Ready] with an empty list is a real answer — the device has an account but nothing
     * writable, or no account at all — and is distinct from [Loadable.Failed], where we simply don't
     * know. The composer says something different, and offers a different way out, for each.
     */
    suspend fun writableCalendars(): Loadable<List<CalendarInfo>> = withContext(Dispatchers.IO) {
        runCatching { queryCalendars() }
            .fold({ Loadable.Ready(it) }, { Loadable.Failed(it.toFault()) })
    }

    private fun queryCalendars(): List<CalendarInfo> {
        val cursor = resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            CALENDAR_PROJECTION,
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            null,
        ) ?: throw IllegalStateException("calendar provider returned no cursor")

        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        CalendarInfo(
                            id = c.getLong(0),
                            displayName = c.getString(1).orEmpty(),
                            accountName = c.getString(2).orEmpty(),
                            accountType = c.getString(3).orEmpty(),
                            color = c.getInt(4) or (0xFF shl 24),
                            // Some OEM providers omit IS_PRIMARY; fall back to the account-name match
                            // the platform itself uses.
                            isPrimary = c.getInt(5) == 1 || c.getString(1) == c.getString(2),
                            accessLevel = c.getInt(6),
                        ),
                    )
                }
            }
        }
            .filter { it.writable }
            // A Google primary calendar is what every other app writes to. A LOCAL calendar never
            // leaves the device and vanishes on factory reset, so it goes last.
            .sortedBy {
                when {
                    it.isPrimary && it.accountType == GOOGLE_ACCOUNT -> 0
                    it.isPrimary -> 1
                    it.accountType == GOOGLE_ACCOUNT -> 2
                    it.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL -> 4
                    else -> 3
                }
            }
    }

    /** Creates an event. A null insert URI means the provider took the values and refused them. */
    suspend fun insert(draft: EventDraft): WriteOutcome = withContext(Dispatchers.IO) {
        runCatching {
            resolver.insert(CalendarContract.Events.CONTENT_URI, draft.toValues(includeCalendar = true))
        }.fold(
            { uri -> if (uri?.lastPathSegment?.toLongOrNull() != null) WriteOutcome.Ok else WriteOutcome.Failed(CalendarFault.Rejected) },
            { WriteOutcome.Failed(it.toFault()) },
        )
    }

    /**
     * Updates an existing event in place. The id goes in the URI, not a selection — the provider
     * requires it that way for a single-row update.
     *
     * Only ever called for non-recurring events: updating a series row would retroactively rewrite
     * every occurrence, which is why [CalendarEvent.editable] excludes them.
     *
     * Nought rows updated means the event no longer exists — deleted on another device while the
     * composer was open. The user's edit is gone and they must be told, or they will walk away
     * believing they rescheduled a meeting that they didn't.
     */
    suspend fun update(eventId: Long, draft: EventDraft): WriteOutcome = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        runCatching {
            resolver.update(uri, draft.toValues(includeCalendar = false), null, null)
        }.fold(
            { rows -> if (rows > 0) WriteOutcome.Ok else WriteOutcome.Failed(CalendarFault.EventGone) },
            { WriteOutcome.Failed(it.toFault()) },
        )
    }

    /**
     * Deletes an event. As an app (not a sync adapter) this is a soft delete: the provider tombstones
     * the row so the sync adapter can propagate the deletion to Google before really removing it.
     *
     * Unlike [update], nought rows here is success, not a fault: the event is already gone, which is
     * precisely what the user asked for. Nagging them about a deletion that has, in effect, happened
     * would be pedantry.
     */
    suspend fun delete(eventId: Long): WriteOutcome = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        runCatching { resolver.delete(uri, null, null) }
            .fold({ WriteOutcome.Ok }, { WriteOutcome.Failed(it.toFault()) })
    }

    /**
     * Hands an event to the system calendar app, landing on the correct occurrence of a recurring
     * series. This is Tempo's escape hatch for everything its own composer deliberately can't do —
     * recurrence, guests, reminders — and it is how repeating events get edited at all.
     *
     * @return false when no calendar app is installed, so the caller can stay quiet rather than crash.
     */
    fun openInCalendarApp(context: Context, event: CalendarEvent): Boolean {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private companion object {
        const val GOOGLE_ACCOUNT = "com.google"
    }
}

/**
 * EVENT_TIMEZONE is mandatory on a direct insert — omitting it throws IllegalArgumentException. (The
 * Intent-based path silently supplies a default, which is why this trips people up.)
 *
 * All-day events are stored as midnight-UTC on their date, and DTEND is exclusive: a one-day event
 * on the 12th ends at midnight-UTC on the 13th. Getting that wrong yields a zero-length event that
 * renders nowhere.
 *
 * **An untitled event stays untitled.** A blank title is written as NULL — which is what the provider
 * holds for an event nobody named, and what every other calendar app writes — rather than as a word
 * Tempo chose to stand in for the absence. This is not a display decision; it is a decision about
 * somebody else's data, on somebody else's server, in front of somebody else's guests.
 */
private fun EventDraft.toValues(includeCalendar: Boolean): ContentValues = ContentValues().apply {
    if (includeCalendar) put(CalendarContract.Events.CALENDAR_ID, calendarId)
    put(CalendarContract.Events.TITLE, title.ifBlank { null })
    put(CalendarContract.Events.EVENT_LOCATION, location)
    put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
    if (allDay) {
        val startDate = start.toLocalDate()
        val endDate = maxOf(end.toLocalDate(), startDate)
        put(CalendarContract.Events.DTSTART, localDateToAllDayUtc(startDate))
        put(CalendarContract.Events.DTEND, localDateToAllDayUtc(endDate.plusDays(1)))
        put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
    } else {
        val endAt = if (end.isAfter(start)) end else start.plusHours(1)
        put(CalendarContract.Events.DTSTART, start.toEpochMillis())
        put(CalendarContract.Events.DTEND, endAt.toEpochMillis())
        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
    }
}

/**
 * Turns whatever the provider threw into something the user can act on.
 *
 * SecurityException is the only one we can name with certainty, and it is also the only one with a
 * remedy the user controls. IllegalArgumentException means we handed the provider values it wouldn't
 * take — a bug on our side, but a retry costs nothing and the alternative is a dead screen. The rest
 * are reported honestly as unknown rather than dressed up as something we understand.
 */
internal fun Throwable.toFault(): CalendarFault = when (this) {
    is SecurityException -> CalendarFault.PermissionLost
    is IllegalArgumentException -> CalendarFault.Rejected
    else -> CalendarFault.Unknown(message)
}

/** Nudges a draft's end past its start, preserving the previous duration where possible. */
fun LocalDateTime.atLeastOneHourAfter(start: LocalDateTime): LocalDateTime =
    if (isAfter(start)) this else start.plusHours(1)
