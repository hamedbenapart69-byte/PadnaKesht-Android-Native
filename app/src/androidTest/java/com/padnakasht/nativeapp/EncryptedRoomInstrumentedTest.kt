package com.padnakasht.nativeapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.padnakasht.nativeapp.data.local.AppDatabaseFactory
import com.padnakasht.nativeapp.data.local.DatabaseCheckpoint
import com.padnakasht.nativeapp.data.local.TransactionEntity
import com.padnakasht.nativeapp.security.DatabasePassphraseProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EncryptedRoomInstrumentedTest {
    @Test
    fun roomFileDoesNotExposePlainSqliteHeader() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        System.loadLibrary("sqlcipher")
        val name = "encrypted-room-test.db"
        val file = context.getDatabasePath(name)
        context.deleteDatabase(name)
        val passphrase = DatabasePassphraseProvider.getOrCreate(context)
        val db = AppDatabaseFactory.open(context, name, passphrase.copyOf())
        try {
            db.transactionDao().insert(
                TransactionEntity(
                    amountMinor = 1234L,
                    kind = "INCOME",
                    category = "test",
                    note = "offline",
                    occurredAtEpochDay = 1L
                )
            )
            DatabaseCheckpoint.requireFullyCheckpointed(db.openHelper.writableDatabase)
            db.close()
            val header = ByteArray(16)
            file.inputStream().use { input ->
                var offset = 0
                while (offset < header.size) {
                    val count = input.read(header, offset, header.size - offset)
                    if (count < 0) break
                    offset += count
                }
            }
            assertEquals(false, String(header, Charsets.US_ASCII).startsWith("SQLite format 3"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun checkpointedEncryptedDatabaseCopyRestoresCommittedRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        System.loadLibrary("sqlcipher")
        val sourceName = "backup-source-test.db"
        val restoredName = "backup-restored-test.db"
        val backupFile = File(context.cacheDir, "backup-roundtrip-test.db")
        context.deleteDatabase(sourceName)
        context.deleteDatabase(restoredName)
        backupFile.delete()
        val passphrase = DatabasePassphraseProvider.getOrCreate(context)
        val source = AppDatabaseFactory.open(context, sourceName, passphrase.copyOf())
        try {
            source.transactionDao().insert(
                TransactionEntity(
                    amountMinor = 987654L,
                    kind = "INCOME",
                    category = "backup-test",
                    note = "committed-before-backup",
                    occurredAtEpochDay = 42L
                )
            )
            DatabaseCheckpoint.requireFullyCheckpointed(source.openHelper.writableDatabase)
        } finally {
            source.close()
        }
        try {
            context.getDatabasePath(sourceName).copyTo(backupFile, overwrite = true)
            backupFile.copyTo(context.getDatabasePath(restoredName), overwrite = true)
            val restored = AppDatabaseFactory.open(context, restoredName, passphrase.copyOf())
            try {
                val transactions = restored.transactionDao().getAll()
                assertEquals(1, transactions.size)
                assertEquals("committed-before-backup", transactions.single().note)
                assertEquals(987654L, transactions.single().amountMinor)
            } finally {
                restored.close()
            }
        } finally {
            context.deleteDatabase(sourceName)
            context.deleteDatabase(restoredName)
            backupFile.delete()
        }
    }
}
