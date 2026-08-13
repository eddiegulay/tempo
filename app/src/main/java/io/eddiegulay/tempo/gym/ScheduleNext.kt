package io.eddiegulay.tempo.gym

import io.eddiegulay.tempo.calendar.EventDraft
import io.eddiegulay.tempo.calendar.atLeastOneHourAfter
import java.time.LocalDateTime

/*
 * 予定に入れる — the arithmetic behind `GYM.SESSION.COMPLETE`'s calendar hand-off.
 *
 * `00-plan.md` §6 defers this to Phase 3 and `04-library-records.md` §4 row 8 says what it is for:
 * *"present — scheduling the next belongs to the moment you finish"*. Everything Android touches lives
 * in `ui/gym/ScheduleNextAction.kt`; what is here is the three questions the hand-off has to answer
 * before it can propose anything, and all three are answerable without a device.
 *
 * **Nothing here invents a date.** That is the whole reason this file is small. The one number a
 * "schedule the next one" button naturally wants — *how far ahead* — has no source: no spec table says
 * tomorrow, no spec table says in three days, and a training cadence is not something Phase 3 can read
 * (`DECISIONS.md` §Q1 explicitly ships **no** training plan). So the hand-off does not guess: it opens
 * on the same next-clean-half-hour the app's own composer opens on and hands the user the wheel.
 *
 * *Rejected* — deriving the offset from the user's median inter-session gap. It is computable from
 * `session.local_date`, and it would still be a number the user never asked for, presented as though
 * the app knew their week. §Q1 declined to make the user declare a schedule; inferring one silently is
 * the same claim made without asking.
 */

/*
 * **The one word this hand-off puts on screen is not here any more.** It was `SCHEDULE_ACTION_LABEL`,
 * a `const val` holding 予定に入れる (`03-player.md` §A COMPLETE's mock, `04` §4 row 8) — and a `const`
 * is resolved at compile time, which is the strongest possible version of the problem every label in
 * this feature had: it cannot be re-resolved when the user changes language, and it cannot even be
 * made to by a getter. It is `strings.gymShared.scheduleNext`.
 *
 * Nothing else in this file is copy. `scheduleDraft` sets the event's title to the routine's own name
 * and its location to the empty string, and neither is ours to translate — the title is user or
 * catalogue data, and it leaves the app for the device calendar, where no toggle of ours reaches it
 * (`.planning/i18n/DECISIONS.md` §L10).
 */

/*
 * **There is deliberately no opening-time rule in this file.** `03-player.md` §A COMPLETE's hand-off
 * opens on the next clean half-hour, and the app already had that rule — `ui/EventComposeScreen.kt`'s
 * `defaultStart`, whose own comment is *"nobody schedules anything at 14:07"*. It was restated here as
 * a verbatim second copy; `DECISIONS.md` §Q7 rules that one implementation is authoritative and the
 * other delegates, so the copy is gone, `defaultStart` is `internal`, and `ScheduleNextAction` calls
 * it. Two implementations that agree today is the state §Q7 calls "a divergence bug waiting to happen",
 * and it says so about a pair that agreed for months.
 */

/**
 * How long to book: **as long as the session that just ended actually took**, rounded up to the minute.
 *
 * This is a measurement, not an estimate. `RoutineEstimate` exists and was rejected for this slot: the
 * user has this second finished the very routine they are scheduling, so their own clock is a strictly
 * better predictor of their next run than the catalogue's seconds-per-rep, and it needs no lookup.
 *
 * Rounding **up** to the whole minute, because a calendar block is minute-granular and a six-minute
 * fourteen-second workout that books six minutes books less time than it takes. Whole minutes also
 * keep `JapaneseDate.clock` honest — it prints `HH:mm` and would otherwise silently truncate the tail.
 *
 * The degenerate case (a session with no measured time at all) falls through to
 * [atLeastOneHourAfter] — the calendar package's own nudge, whose entire job is *"nudges a draft's end
 * past its start"*. A zero-length event renders nowhere, and inventing a minimum here would be
 * inventing a number the app already has an answer for.
 */
fun scheduleEnd(start: LocalDateTime, activeMs: Long): LocalDateTime =
    start.plusMinutes(ceilMinutes(activeMs)).atLeastOneHourAfter(start)

private fun ceilMinutes(ms: Long): Long = if (ms <= 0L) 0L else (ms + 59_999L) / 60_000L

/**
 * The proposal, in the shape the existing composer's own confirm dialog already knows how to restate.
 *
 * @param routineName becomes the event's title verbatim. The record screen is a finish, not a form,
 *   so there is no 題名 field: the title is the routine the user just did, `EventConfirmDialog` names
 *   it before anything is written, and 予定 can rename it afterwards like any other event.
 * @param calendarId the best writable calendar, which `CalendarRepository.writableCalendars` already
 *   sorts to the front. **Never negative** at the call site — the hand-off refuses to build a draft it
 *   has nowhere to put and shows `CalendarFault.NoWritableCalendar` instead.
 *
 * `allDay = false` and `location = ""` are not omissions: a workout is an appointment with a clock,
 * and a launcher has no business guessing where the user trains.
 */
fun scheduleDraft(
    routineName: String,
    activeMs: Long,
    start: LocalDateTime,
    calendarId: Long,
): EventDraft = EventDraft(
    title = routineName,
    start = start,
    end = scheduleEnd(start, activeMs),
    allDay = false,
    location = "",
    calendarId = calendarId,
)
