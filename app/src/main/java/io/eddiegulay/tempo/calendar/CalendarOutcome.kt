package io.eddiegulay.tempo.calendar

import io.eddiegulay.tempo.data.TempoFault

/**
 * Everything that can go wrong between Tempo and the calendar provider.
 *
 * The set is deliberately small and phrased in terms of *what the user must do next*, not what the
 * platform threw: a SecurityException and a revoked toggle in Settings are the same fault, because
 * they have the same remedy. Anything genuinely unforeseen lands in [Unknown] and is still shown —
 * an unexplained failure the user can retry beats a silent one they can't.
 *
 * It is a [TempoFault] so that the calendar's failures and the gym's travel through the same
 * [Loadable] and out through the same strip. Widening the *carrier* rather than merging the two case
 * lists was deliberate: 予定 faults are named for calendar remedies and would be nonsense on a gym
 * page, and a single flat fault enum would have to be exhaustively matched by both features forever.
 */
sealed interface CalendarFault : TempoFault {

    /** Access was refused or revoked. Remedy: ask again, or open Settings if permanently denied. */
    data object PermissionLost : CalendarFault

    /** The device has no calendar that accepts new events — no account, or all of them read-only. */
    data object NoWritableCalendar : CalendarFault

    /** The row is gone: deleted on another device, or synced away while the composer was open. */
    data object EventGone : CalendarFault

    /** The provider refused the values. Almost always a malformed draft; retrying is still worth it. */
    data object Rejected : CalendarFault

    /** Nothing on the device can display an event, so the recurring hand-off has nowhere to go. */
    data object NoCalendarApp : CalendarFault

    data class Unknown(val cause: String?) : CalendarFault
}

/**
 * A value that has to be fetched and might not arrive.
 *
 * The point of the [Loading] case is that it is *not* [Ready] with an empty list. An agenda that
 * failed to load and an agenda with nothing in it are the same picture — an empty page — and telling
 * the user "予定はありません" when the truth is "we couldn't read your calendar" is the single most
 * damaging thing this feature could do, because they would trust it and miss a meeting.
 *
 * [Failed] carries a [TempoFault] rather than a [CalendarFault] because the promise above is not
 * about calendars — an unreadable training history rendering as 記録はありません is the same lie. The
 * type stayed here, in the calendar package, rather than moving to `data/`: it is used from every
 * calendar file and a move would have churned a dozen imports to say nothing new.
 */
sealed interface Loadable<out T> {

    data object Loading : Loadable<Nothing>

    data class Ready<out T>(val value: T) : Loadable<T>

    data class Failed(val fault: TempoFault) : Loadable<Nothing>

    /** The value if it arrived, otherwise null — for callers that can honestly degrade, like Home. */
    fun valueOrNull(): T? = (this as? Ready)?.value
}

/** The result of a write. [Failed] carries the reason so the composer can say it and offer a way out. */
sealed interface WriteOutcome {

    data object Ok : WriteOutcome

    data class Failed(val fault: CalendarFault) : WriteOutcome
}

/**
 * A change to the calendar that the user has asked for but not yet confirmed.
 *
 * Nothing in this app reaches the provider without passing through here first. A calendar is shared:
 * an event Tempo creates appears on the user's laptop, an event it moves moves for everyone invited,
 * and an event it deletes is withdrawn from all of them — with a notification. None of that is
 * undoable from a launcher, so each one is worth a deliberate second tap.
 */
sealed interface PendingWrite {

    data class Create(val draft: EventDraft) : PendingWrite

    data class Update(val event: CalendarEvent, val draft: EventDraft) : PendingWrite

    data class Delete(val event: CalendarEvent) : PendingWrite
}
