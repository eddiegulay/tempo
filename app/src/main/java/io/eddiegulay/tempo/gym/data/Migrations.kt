package io.eddiegulay.tempo.gym.data

import android.database.sqlite.SQLiteDatabase

/**
 * One irreversible forward step of the schema.
 *
 * Migrations are pure DDL/DML. They must never read app state, never touch [SeedCatalog], and — the
 * rule with teeth — **never be edited after they have shipped**: an installed device has already run
 * this exact statement list and will never run it again, so a "small correction" to a shipped
 * migration produces two populations with different tables and no way to tell them apart.
 */
internal interface Migration {
    val version: Int
    fun apply(db: SQLiteDatabase)
}

/** Schema v1 — every table in §A. The schema is not phased; only the UI is (`00-plan.md` §6). */
internal object V1CreateSchema : Migration {
    override val version: Int = 1
    override fun apply(db: SQLiteDatabase) {
        V1_STATEMENTS.forEach(db::execSQL)
    }
}

/** Ordered, gapless, append-only. */
internal val MIGRATIONS: List<Migration> = listOf(V1CreateSchema)

/**
 * Derived from the list, never hand-maintained.
 *
 * The bug this closes is the one that ships an app which opens a v2 database with v1 tables: bump a
 * constant, forget the migration, and nothing complains until a user's device does. Deriving it makes
 * the two impossible to get out of step.
 *
 * It is deliberately **not** the same counter as [SeedCatalog.VERSION]. Shipping バーバラ in Phase 2
 * requires no DDL at all, and if seeding lived in `onUpgrade` that release would have to invent an
 * empty migration purely to get the data in — a lie about what changed, and eventually a migration
 * list of no-ops. Two counters, one for structure and one for content, is the correct factoring
 * (§B.2, `00-plan.md` §2 row 17).
 */
internal val SCHEMA_VERSION: Int = MIGRATIONS.last().version

/**
 * Thrown by `onDowngrade` when an older APK has been installed over a newer database.
 *
 * The platform default throws too, but it throws where nothing can catch it and the process dies on
 * boot. For a **launcher** that is a device with no home screen, so it is caught in `openOrRecover`,
 * the file is quarantined, and the user gets `GymFault.StoreReset` on a working launcher instead.
 */
internal class SchemaDowngrade(val oldVersion: Int, val newVersion: Int) :
    RuntimeException("鍛錬 database is from a newer build: $oldVersion → $newVersion")
