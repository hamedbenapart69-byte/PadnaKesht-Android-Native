package com.padnakasht.nativeapp.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Refuses to copy a WAL-backed database until every committed page is in the main database file.
 * A non-WAL database reports -1 for the frame counts and is already self-contained.
 */
object DatabaseCheckpoint {
    fun requireFullyCheckpointed(database: SupportSQLiteDatabase) {
        val result = database.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
            check(cursor.moveToFirst()) { "SQLite did not return a WAL checkpoint result" }
            Triple(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2))
        }
        check(result.first == 0) { "Database checkpoint is busy; refusing to copy an inconsistent backup" }
        check(result.second < 0 || result.second == result.third) {
            "Database checkpoint did not copy every WAL frame; refusing to copy an inconsistent backup"
        }
    }
}
