package io.eddiegulay.tempo.gym.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

/**
 * `exercise_meta` — the handful of scalars that must be transactionally consistent with the rows they
 * describe, and are therefore the only things in the database that are not user data.
 *
 * Everything else the user can set lives in DataStore, because a preference needs the synchronous
 * first-frame read `ThemeRepository.loadInitialSettings()` provides and an IO-dispatched SQLite read
 * cannot give without a flash of defaults (§A.0.6). These four are the exceptions precisely because
 * they are *about* the database: the monotonic high-water mark has to advance in the same transaction
 * as the row it stamped, and the seed catalog version has to advance in the same transaction as the
 * rows it seeded, or a crash between the two leaves the store lying about itself.
 */
internal object Meta {

    /** Monotonic clock high-water mark (§E.4). Advances on every write; read once at construction. */
    const val LAST_SEEN_MILLIS = "last_seen_millis"

    /** The seed generation installed. Separate from the schema version, on purpose (§B.2). */
    const val SEED_CATALOG_VERSION = "seed_catalog_version"

    /** Set after a corruption recovery or a seed upgrade; cleared once the cache is regenerated. */
    const val PR_REBUILD_NEEDED = "pr_rebuild_needed"

    /** The app versionCode that created the file. Forensics only — never branched on. */
    const val SCHEMA_CREATED_BY_VERSION = "schema_created_by_version"

    fun read(db: SQLiteDatabase, key: String): String? =
        db.rawQuery("SELECT value FROM $TABLE_META WHERE key = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    fun readLong(db: SQLiteDatabase, key: String, default: Long): Long =
        read(db, key)?.toLongOrNull() ?: default

    fun readInt(db: SQLiteDatabase, key: String, default: Int): Int =
        read(db, key)?.toIntOrNull() ?: default

    fun write(db: SQLiteDatabase, key: String, value: String) {
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        db.insertWithOnConflict(TABLE_META, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
