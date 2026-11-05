package com.padnakasht.nativeapp.data.local

import android.content.Context
import androidx.room.Room
import com.padnakasht.nativeapp.security.DatabasePassphraseProvider
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Single source of truth for opening the encrypted business database.
 * Activities and background workers must share the same passphrase and migrations.
 */
object AppDatabaseFactory {
    fun open(
        context: Context,
        databaseName: String = "padnakasht.db",
        passphrase: ByteArray = DatabasePassphraseProvider.getOrCreate(context.applicationContext)
    ): AppDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, databaseName)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13
            )
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
    }
}
