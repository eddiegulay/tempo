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
    is SQLiteFullException -> GymFault.StoreFull
    is SQLiteDatabaseCorruptException -> GymFault.StoreCorrupt
    is SQLiteConstraintException -> GymFault.Rejected
    is SQLiteDatabaseLockedException, is SQLiteDiskIOException -> GymFault.StoreUnavailable(message)
    is SQLiteException -> GymFault.StoreUnavailable(message)
    else -> GymFault.Unknown(message)
}
