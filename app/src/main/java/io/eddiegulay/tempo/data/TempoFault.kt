package io.eddiegulay.tempo.data

/**
 * The supertype of everything Tempo can fail at while fetching something it then has to render.
 *
 * It exists so that [io.eddiegulay.tempo.calendar.Loadable] can carry a second feature's failures
 * without either feature learning the other's vocabulary, and — more to the point — so that
 * `faultCopy`, `FaultStrip` and `FaultPanel` are written once. A gym page that cannot read its store
 * gets the same strip, in the same place, with the same way out, as a calendar page that cannot read
 * the provider. That is not code thrift; it is the reason a user only has to learn what a fault looks
 * like in this app once.
 *
 * *Rejected* — parameterising `Loadable<T, F>` on its fault type. It types the same thing more
 * precisely and costs a type argument at every single use site, including the many that only ever
 * call `valueOrNull()` and do not care what went wrong. The whole doctrine here is that a failure is
 * never dropped on the floor; nothing about that needs the compiler to know *whose* failure it is.
 *
 * *Rejected* — a shared enum of remedies (`Retry`, `Grant`, `Settings`, `Dead`) with each feature
 * mapping onto it. It reads well until the first fault that has words but no button, and it moves the
 * decision about what to *say* away from the place that knows what happened.
 *
 * *Not `sealed`, and not by choice.* Kotlin requires the direct subtypes of a sealed declaration to
 * live in its own package, and [io.eddiegulay.tempo.calendar.CalendarFault] belongs beside the
 * repository that raises it. The alternative — dragging `CalendarFault` into this package to satisfy
 * a compiler rule — would rewrite imports across the calendar feature and say nothing new about the
 * code. The exhaustiveness that `sealed` would have bought is bought instead one level down: each
 * *family* is sealed, `faultCopy` matches each family exhaustively, and its one `else` returns real
 * words rather than falling through, so the worst a future third family can do is be vague. It cannot
 * be silent, which is the only property that actually matters here.
 */
interface TempoFault

/**
 * Everything that can go wrong between 鍛錬 and its own store, phrased — like
 * [io.eddiegulay.tempo.calendar.CalendarFault] before it — in terms of the remedy rather than the
 * exception.
 *
 * [StoreCorrupt] is the case the whole type exists for. The platform's default `DatabaseErrorHandler`
 * *deletes* an unreadable database, after which the history screen renders an empty state that the
 * user will believe. A quarantine plus this fault is what keeps 記録はありません a claim we only make
 * when we know it to be true.
 *
 * *Rejected* — folding the four store cases into one `StoreUnavailable`. They look alike from the
 * inside and are nothing alike from the outside: a full disk is fixed by deleting photos, a corrupt
 * file is not fixable at all and costs the user their history, and a downgrade is fixed by installing
 * the newer build back. One word for all four would make three of them a lie.
 *
 * See `.planning/exercise/02-data.md` §C.0 — this is that list, unabridged.
 */
sealed interface GymFault : TempoFault {

    /** The database was unreadable and has been quarantined. History is gone; say so, don't imply "none yet". */
    data object StoreCorrupt : GymFault

    /** Disk full, or the file is locked. Retrying is worth it. */
    data class StoreUnavailable(val cause: String?) : GymFault

    /** `SQLiteFullException` — kept apart from [StoreUnavailable] because the remedy is free space. */
    data object StoreFull : GymFault

    /** An older APK was installed over a newer database, so the schema is from the future. */
    data object StoreReset : GymFault

    /** The routine is gone — deleted while its page was open. */
    data object RoutineGone : GymFault

    /** The session is gone — deleted from another page while this one held its key. */
    data object SessionGone : GymFault

    /** A CHECK constraint refused the row. Almost always a malformed draft. */
    data object Rejected : GymFault

    data class Unknown(val cause: String?) : GymFault
}
