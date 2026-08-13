package io.eddiegulay.tempo.gym.data

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * A draft version is swept only once it is a week old, so a builder left open overnight is never
 * collected out from under the user (§B.4).
 */
private const val DRAFT_GRACE_MS = 7L * 24 * 60 * 60 * 1000

/** A quarantined corrupt file is evidence for a month, then it is just disk (§B.4). */
private const val QUARANTINE_GRACE_MS = 30L * 24 * 60 * 60 * 1000

/** Keeping more than one is hoarding: the second copy has never once answered a question. */
private const val QUARANTINE_KEEP = 1

private const val QUARANTINE_PREFIX = "exercise-corrupt-"

/**
 * A durable flag that outlives the database, because the database is the thing that failed.
 *
 * It lives in DataStore rather than in `exercise_meta` for exactly that reason, and it is a separate
 * store from `tempo_settings` because `preferencesDataStore` permits one instance per file per process
 * and `ThemeRepository` already owns that one.
 *
 * *Rejected* — a marker file in `filesDir`. It would work, and it would be a third persistence
 * mechanism in a repo that already has two.
 */
private val Context.gymStoreDataStore: DataStore<Preferences> by preferencesDataStore(name = "gym_store")

internal object CorruptionFlag {

    private val key = booleanPreferencesKey("history_lost")

    /**
     * Called from `onCorruption`, which is a synchronous platform callback on whatever thread was
     * opening the database. Blocking is the correct trade here: the alternative is launching into a
     * scope that may not outlive the crash we are in the middle of, and losing the one durable record
     * that the user's history was destroyed.
     *
     * **[HistoryLoss.raise] comes first, and the order is the fix.** The durable write is what survives
     * the process; the in-memory flag is what the read path can see *now*. Raise the durable one only
     * and a corruption discovered after construction never reaches `observeHistory` — the helper
     * reopens, the seeder repopulates, and a destroyed year renders as 記録はありません for the rest of
     * the process (§7.1, §E.6).
     */
    fun raise(context: Context) {
        HistoryLoss.raise()
        runCatching {
            runBlocking { context.gymStoreDataStore.edit { it[key] = true } }
        }
    }

    fun isRaised(context: Context): Boolean = runCatching {
        runBlocking { context.gymStoreDataStore.data.first()[key] }
    }.getOrNull() ?: false

    /** Paired with [HistoryLoss.clear] by the store's acknowledgement, never by finishing a session. */
    suspend fun clear(context: Context) {
        runCatching { context.gymStoreDataStore.edit { it.remove(key) } }
    }
}

/**
 * Corruption recovery that keeps the evidence and tells the truth.
 *
 * The platform's `DefaultDatabaseErrorHandler` **deletes the database file**. For a launcher that
 * silently vaporises a year of training history and then renders 記録はありません — the exact failure
 * design §7.1 declares is the most damaging thing this feature could do, because it is a lie the user
 * will believe. Instead: quarantine the file, let the next open create a clean one, and raise a durable
 * flag so the history surfaces report `GymFault.StoreCorrupt` rather than an empty state.
 *
 * After recovery the seeder repopulates the library and the presets, so the user lands on a working
 * 鍛錬 index with an honest fault strip saying the *history* is gone — materially better than a blank
 * screen (§E.6).
 */
internal class ExerciseCorruptionHandler(private val appContext: Context) : DatabaseErrorHandler {

    override fun onCorruption(db: SQLiteDatabase) {
        val path = db.path
        runCatching { db.close() }
        if (path != null) {
            val quarantine = File(appContext.filesDir, "$QUARANTINE_PREFIX${System.currentTimeMillis()}.db")
            runCatching { File(path).renameTo(quarantine) }
                // Recovery must happen even if the rename fails, or the next open finds the same
                // corrupt file and the app never starts again.
                .onFailure { runCatching { File(path).delete() } }
            // The journal siblings must go too, or the fresh database inherits a poisoned WAL.
            runCatching { File("$path-wal").delete() }
            runCatching { File("$path-shm").delete() }
        }
        CorruptionFlag.raise(appContext)
    }
}

/**
 * The `exercise.db` helper.
 *
 * Three of its four overrides exist to take a decision away from the platform, and each one is a
 * failure the default behaviour would turn into something worse than the fault itself.
 */
internal class ExerciseDb(
    private val appContext: Context,
    private val notifier: TableChangeNotifier,
    private val appVersionCode: Int,
) : SQLiteOpenHelper(
    appContext,
    DB_NAME,
    null,
    SCHEMA_VERSION,
    ExerciseCorruptionHandler(appContext),
) {

    init {
        // WAL is what lets history render while a live session writes phase transitions behind it:
        // readers get a consistent snapshot concurrent with the writer, which is why reads take no
        // lock at all (§E.1).
        setWriteAheadLoggingEnabled(true)
    }

    /**
     * `PRAGMA foreign_keys` is **off by default** on Android and cannot be set inside a transaction —
     * which is exactly why this is `onConfigure` and not `onOpen`. Every `ON DELETE RESTRICT` in §A is
     * decorative without it.
     */
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        MIGRATIONS.forEach { it.apply(db) }
        Meta.write(db, Meta.SCHEMA_CREATED_BY_VERSION, appVersionCode.toString())
        Seeder.applyTo(db, fromCatalogVersion = 0)
    }

    /**
     * Only the migrations this database has not seen.
     *
     * Seeding is deliberately **not** done here. The seed catalog can grow in an app update that
     * changes no schema at all — a new preset needs no new column — and such an update never calls
     * `onUpgrade` (§B.2).
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        MIGRATIONS.filter { it.version in (oldVersion + 1)..newVersion }.forEach { it.apply(db) }
    }

    /**
     * A downgrade means an older APK was sideloaded over a newer database. The platform default throws
     * and crashes on boot, which for a launcher leaves a device with no home screen.
     */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SchemaDowngrade(oldVersion, newVersion)
    }

    override fun onOpen(db: SQLiteDatabase) {
        if (db.isReadOnly) return
        val installed = Meta.readInt(db, Meta.SEED_CATALOG_VERSION, default = 0)
        if (installed < SeedCatalog.VERSION) {
            Seeder.applyTo(db, fromCatalogVersion = installed)
            notifier.notify(TABLE_ROUTINE, TABLE_EXERCISE, TABLE_PROGRESSION)
        }
        sweep(db)
    }

    /**
     * Opportunistic garbage collection, bounded (§B.4).
     *
     * A `routine_version` that is nobody's head and that no session references is a discarded builder
     * draft. It cannot be swept eagerly on discard because a draft is also what an in-flight builder
     * looks like, hence the seven-day grace: the sweep is for versions the user has demonstrably
     * abandoned, not for the one they are editing.
     *
     * `routine_station` follows by cascade — the only cascade from a version, and safe for precisely
     * this reason.
     */
    private fun sweep(db: SQLiteDatabase) {
        runCatching {
            db.execSQL(
                """
                DELETE FROM $TABLE_ROUTINE_VERSION
                WHERE id NOT IN (SELECT head_version_id FROM $TABLE_ROUTINE)
                  AND id NOT IN (SELECT DISTINCT routine_version_id FROM $TABLE_SESSION)
                  AND created_at < ?
                """.trimIndent(),
                arrayOf<Any>(System.currentTimeMillis() - DRAFT_GRACE_MS),
            )
        }
        runCatching { sweepQuarantine() }
    }

    /** The same pass retires quarantined corrupt files: older than thirty days, and at most one kept. */
    private fun sweepQuarantine() {
        val files = appContext.filesDir
            .listFiles { f -> f.name.startsWith(QUARANTINE_PREFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        val cutoff = System.currentTimeMillis() - QUARANTINE_GRACE_MS
        files.drop(QUARANTINE_KEEP).forEach { runCatching { it.delete() } }
        files.take(QUARANTINE_KEEP).filter { it.lastModified() < cutoff }
            .forEach { runCatching { it.delete() } }
    }
}
