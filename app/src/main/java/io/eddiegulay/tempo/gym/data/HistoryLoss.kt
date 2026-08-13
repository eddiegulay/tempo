package io.eddiegulay.tempo.gym.data

/**
 * Whether this process believes the training history has been destroyed — and the one place that
 * answer lives.
 *
 * It exists because corruption is discovered **after** construction far more often than during it, and
 * the version this replaces could only ever see the first case. `historyLost` was read once in
 * `GymStore`'s `init` from the durable flag; `ExerciseCorruptionHandler` held nothing but a `Context`,
 * so a mid-process `onCorruption` quarantined the file and wrote DataStore while the store's own field
 * stayed `false`. The failing query surfaced `StoreCorrupt` exactly once, `writableDatabase` then
 * reopened, `onCreate` ran, the seeder repopulated — and every read after that emitted
 * `Loadable.Ready(emptyList())`. History, records, streak, charts and `GymHomeFeed.everTrained` all
 * rendered a destroyed year as 記録はありません, which is design §7.1's named worst case and the exact
 * lie `00-plan.md` §2 row 15 replaced the default error handler to prevent.
 *
 * So the flag is raised **synchronously, on the thread that discovered the corruption, before** the
 * DataStore write that makes it durable — [CorruptionFlag.raise] does both, in that order. The write
 * can be slow, can be blocked, can lose the race with the process dying; the in-memory half cannot,
 * and it is the half the read path consults.
 *
 * [watch] is the second half and it is not optional. Setting a flag that nobody re-reads leaves every
 * flow already collecting `Ready` sitting on its stale value until something else happens to change a
 * table. The listener notifies the history tables so those collectors re-run and re-emit `Failed`.
 *
 * *Rejected* — a `StateFlow`. The raise happens on a platform callback in the middle of a database
 * failure, and the store already owns a change notifier whose whole job is waking readers; a second
 * fan-out mechanism for one boolean would be a parallel channel to keep consistent.
 *
 * *Rejected* — keeping the boolean on `GymStore` and passing a setter down. It works, and it makes the
 * value unreadable from [CorruptionFlag], which is where the durable half is written. One owner.
 */
internal object HistoryLoss {

    @Volatile
    private var lost: Boolean = false

    @Volatile
    private var onRaised: (() -> Unit)? = null

    /** What the history surfaces gate on. Cheap enough to read per emission. */
    val isLost: Boolean get() = lost

    /**
     * Seeds the flag from the durable one at process start, **without** waking anybody: at that point
     * nothing is collecting, and `observeHistory`'s `onStart` will report the loss on its own.
     */
    fun restore(raised: Boolean) {
        lost = raised
    }

    /** Installed once, by the store that owns the notifier. A second call replaces the first. */
    fun watch(listener: () -> Unit) {
        onRaised = listener
    }

    /**
     * Called from `onCorruption`. Sets the flag first and announces second, so a listener that throws
     * cannot leave the store reporting a healthy history over a quarantined file.
     */
    fun raise() {
        lost = true
        runCatching { onRaised?.invoke() }
    }

    /**
     * Only [GymStore.acknowledgeHistoryLoss] calls this. §E.6 keeps the fault raised until the user
     * acknowledges it — one new session does not restore a year.
     */
    fun clear() {
        lost = false
    }
}
