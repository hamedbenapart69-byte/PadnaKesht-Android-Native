package com.padnakasht.nativeapp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.padnakasht.nativeapp.data.local.AppDatabase
import com.padnakasht.nativeapp.data.local.TransactionEntity
import com.padnakasht.nativeapp.security.DatabasePassphraseProvider
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

@RunWith(AndroidJUnit4::class)
class EncryptedRoomInstrumentedTest {
    @Test
    fun roomFileDoesNotExposePlainSqliteHeader() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        System.loadLibrary("sqlcipher")
        val name = "encrypted-room-test.db"
        val file = context.getDatabasePath(name)
        file.delete()
        val passphrase = DatabasePassphraseProvider.getOrCreate(context)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(passphrase, null, true))
            .build()
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
            db.close()
            val header = ByteArray(16)
            FileInputStream(file).use { input ->
                var offset = 0
                while (offset < header.size) {
                    val count = input.read(header, offset, header.size - offset)
                    if (count < 0) break
                    offset += count
                }
            }
            assertFalse(String(header, Charsets.US_ASCII).startsWith("SQLite format 3"))
        } finally {
            db.close()
            file.delete()
        }
    }
}
