package io.eddiegulay.tempo.gym.data

import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import io.eddiegulay.tempo.data.GymFault

/**
 * The transaction wrapper every write in this package goes through.
 *
 * `beginTransactionNonExclusive` rather than `beginTransaction`, because the database runs in WAL mode:
 * the exclusive form takes a lock that blocks readers, which would defeat the one property WAL was
 * turned on for — history rendering while a live session writes phase transitions behind it (§E.1).
 *
 * Note what it does **not** do: announce the change. `notify` belongs after `endTransaction`, in the
 * caller, because a reader woken while the transaction is still open takes a snapshot without the
 * commit in it and renders the pre-write state (§E.3).
 */
internal inline fun <T> SQLiteDatabase.transact(body: SQLiteDatabase.() -> T): T {
    beginTransactionNonExclusive()
    return try {
        val result = body()
        setTransactionSuccessful()
        result
    } finally {
        endTransaction()
    }
}

internal fun Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

internal fun Cursor.intOrNull(index: Int): Int? = if (isNull(index)) null else getInt(index)

internal fun Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)

internal fun Cursor.bool(index: Int): Boolean = getInt(index) == 1

/** Materialises every row inside `use`, because no [Cursor] may cross a dispatcher boundary (§E.2). */
internal inline fun <T> Cursor.mapRows(row: (Cursor) -> T): List<T> = use { c ->
    buildList { while (c.moveToNext()) add(row(c)) }
}

/** The first row mapped, or null. Same rule: the cursor is closed before the value is returned. */
internal inline fun <T> Cursor.firstRow(row: (Cursor) -> T): T? = use { c ->
    if (c.moveToFirst()) row(c) else null
}

/**
 * A hard delete refused because the routine still has sessions pointing at it (§C.4).
 *
 * It is thrown *inside* the transaction, before either delete, rather than left to the deferred foreign
 * key that fires at COMMIT. Same outcome for the user — [GymFault.Rejected] — but the rollback is
 * deterministic, the invariant is readable at the statement that would violate it, and it does not
 * depend on `PRAGMA defer_foreign_keys` having been honoured.
 */
internal class RoutineHasHistory(val routineId: String) :
    RuntimeException("routine $routineId still has finished sessions")

/**
 * A read that named a routine the store no longer holds — archived out from under a page, or purged
 * from another shell state (§C.4).
 *
 * It lives **here, beside [toGymFault], and is `internal`** rather than nested privately inside
 * `GymStore`, for the reason `DECISIONS.md` §Q23 records: while it was private there was no arm for it
 * below and it fell to [GymFault.Unknown], so the page that already knew how to handle
 * [GymFault.RoutineGone] never saw one. A thrown type whose classification lives in another file is a
 * type that gets classified by accident; putting the two within a screen of each other is what makes
 * the omission visible.
 */
internal class RoutineMissing(val routineId: String) : RuntimeException("routine gone: $routineId")

/**
 * The same, for a session row: a record opened from a list that has since been deleted.
 *
 * `04-library-records.md` §4 edge case 9 is the whole customer — `SessionDetailScreen.popsOnFault`
 * pops on [GymFault.SessionGone] and nothing else, and `DECISIONS.md` §Q6 gives that fault the words
 * この記録は削除されています with **no** action. Unmapped it arrived as [GymFault.Unknown], which renders
 * 記録を読めません with a もう一度 that re-reads a row that is gone and fails again forever.
 */
internal class SessionMissing(val sessionId: Long) : RuntimeException("session gone: $sessionId")

/**
 * Turns whatever SQLite threw into something the user can act on.
 *
 * The classification is by **remedy**, not by exception class, which is the whole reason
 * [GymFault] exists: a full disk is fixed by deleting photos, a corrupt file is not fixable at all and
 * costs the user their history, a downgrade is fixed by reinstalling the newer build, and a failed
 * CHECK is a malformed draft that a retry will not help. One word for all four would make three of
 * them a lie.
 *
 * Ordering matters — [SQLiteFullException], [SQLiteConstraintException] and the rest all extend
 * [SQLiteException], so the general case has to come last or it swallows every specific one.
 */
internal fun Throwable.toGymFault(): GymFault = when (this) {
    is SchemaDowngrade -> GymFault.StoreReset
    // 保存できませんでした with no もう一度: the delete was refused, and retrying refuses it again.
    is RoutineHasHistory -> GymFault.Rejected
    // The two "it is gone" faults, and they must precede the SQLite cases for the ordering reason
    // below — both are plain `RuntimeException`s, so nothing else here would have caught them either
    // way, but the group reads as one rule: a row that is absent is not a store that is broken.
    // `DECISIONS.md` §Q6 gives both of these copy with **no action word**, and §Q23 is the ruling that
    // put them here: without these two arms both fell to [GymFault.Unknown], which offers a もう一度
    // that can only fail again on a row nothing will bring back.
    is RoutineMissing -> GymFault.RoutineGone
    is SessionMissing -> GymFault.SessionGone
    is SQLiteFullException -> GymFault.StoreFull
    is SQLiteDatabaseCorruptException -> GymFault.StoreCorrupt
    is SQLiteConstraintException -> GymFault.Rejected
    is SQLiteDatabaseLockedException, is SQLiteDiskIOException -> GymFault.StoreUnavailable(message)
    is SQLiteException -> GymFault.StoreUnavailable(message)
    else -> GymFault.Unknown(message)
}
