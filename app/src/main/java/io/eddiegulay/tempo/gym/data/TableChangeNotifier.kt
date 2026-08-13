package io.eddiegulay.tempo.gym.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

/** A `finishSession` transaction touches four tables; a home list observing all four must query once. */
private const val INVALIDATION_DEBOUNCE_MS = 80L

/**
 * In-process table-change bus — what 鍛錬 has instead of Room's `InvalidationTracker`.
 *
 * Every write announces the tables it touched; every read-flow re-runs its query when a table it
 * depends on is announced. That is the whole mechanism, and it is about thirty lines because the hard
 * part of Room's version — surviving multiple processes and other connections to the same file — is a
 * problem this app does not have.
 *
 * **Correct ONLY because Tempo is a single process.** `TempoNotificationListener` declares no
 * `android:process` and shares the app's. If a second process is ever introduced, this bus goes silent
 * across it **with no error at all** — the flow would simply stop updating, which is the worst failure
 * mode a cache invalidator can have. That is why it is written down here rather than assumed (§E.3).
 *
 * The debounce is the medicine `CalendarRepository` already takes for sync-adapter bursts
 * (`CHANGE_DEBOUNCE_MS = 300`). Eighty milliseconds rather than three hundred because these are our
 * own writes and the user is looking at the result of their own tap.
 *
 * *Rejected* — a per-table `MutableStateFlow<Int>` version counter. It reads simpler until a query
 * depends on four tables, at which point every reader needs a `combine` of four counters and the
 * debounce has nowhere to live.
 */
internal class TableChangeNotifier {

    private val changes = MutableSharedFlow<String>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** The もう一度 behind a `FaultPanel`. Kept apart from [changes] so it bypasses the debounce. */
    private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Announces a commit. **Call after `endTransaction()`, never inside it** — a reader woken while the
     * transaction is still open takes a snapshot that does not include the commit and renders the
     * pre-write state, which looks exactly like a lost write (§E.3).
     */
    fun notify(vararg tables: String) {
        tables.forEach { changes.tryEmit(it) }
    }

    /** Re-runs every observing query, failed or not. Bypasses the debounce, or the first tap after a
     *  failure appears to do nothing — the same reasoning as `CalendarRepository.retry`. */
    fun retry() {
        retries.tryEmit(Unit)
    }

    @OptIn(FlowPreview::class)
    fun <T> observing(vararg tables: String, query: suspend () -> T): Flow<T> =
        merge(
            changes.filter { it in tables }.debounce(INVALIDATION_DEBOUNCE_MS),
            retries.map { "" },
        )
            .onStart { emit("") }
            .map { query() }
            .flowOn(Dispatchers.IO)
}
