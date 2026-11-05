package com.padnakasht.nativeapp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.padnakasht.nativeapp.data.BusinessRepository
import com.padnakasht.nativeapp.data.local.AppDatabase
import com.padnakasht.nativeapp.data.local.ProductEntity
import com.padnakasht.nativeapp.data.local.PurchaseLineEntity
import com.padnakasht.nativeapp.security.DatabasePassphraseProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PurchaseManagementTest {
    private fun database(context: Context, name: String, withMigrations: Boolean = false): AppDatabase {
        System.loadLibrary("sqlcipher")
        context.getDatabasePath(name).delete()
        val builder = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(DatabasePassphraseProvider.getOrCreate(context), null, true))
        if (withMigrations) {
            builder.addMigrations(AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13)
        }
        return builder.build()
    }

    @Test
    fun purchaseReceiptUpdatesStockAndSupplierPayableSeparately() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = database(context, "purchase-receipt-test.db")
        try {
            val dao = db.businessDao()
            val productId = dao.insertProduct(
                ProductEntity(name = "کالای خرید", sku = "PURCHASE-RECEIPT-TEST", stockQuantity = 2, unitPriceMinor = 500)
            )
            val repository = BusinessRepository(dao)
            val purchaseId = repository.createPurchase(
                supplierName = "تأمین‌کننده آزمایشی",
                lines = listOf(
                    PurchaseLineEntity(
                        purchaseId = 0L,
                        productId = productId,
                        quantity = 5L,
                        unitCostMinor = 120L,
                        lineTotalMinor = 600L
                    )
                ),
                initialPaymentMinor = 200L
            )

            assertEquals(7L, dao.getProduct(productId)?.stockQuantity)
            assertEquals(600L, dao.getPurchase(purchaseId)?.totalMinor)
            assertEquals(400L, repository.purchaseOutstandingBalance(purchaseId))
            assertEquals(1, dao.getStockMovements().count { it.reason.contains("خرید #$purchaseId") })

            val key = "purchase-payment-idempotency"
            val paymentId = repository.recordPurchasePayment(purchaseId, 100L, "پرداخت تکمیلی", key)
            val repeatedId = repository.recordPurchasePayment(purchaseId, 100L, "پرداخت تکمیلی", key)
            assertEquals(paymentId, repeatedId)
            assertEquals(300L, repository.purchaseOutstandingBalance(purchaseId))

            repository.recordPurchasePayment(purchaseId, 300L, "تسویه")
            assertEquals(0L, repository.purchaseOutstandingBalance(purchaseId))
            assertEquals(600L, dao.getPurchasePaymentsTotal(purchaseId))
        } finally {
            db.close()
            context.getDatabasePath("purchase-receipt-test.db").delete()
        }
    }

    @Test
    fun failedPurchaseRollsBackEveryStockAndLedgerWrite() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = database(context, "purchase-rollback-test.db")
        try {
            val dao = db.businessDao()
            val productId = dao.insertProduct(
                ProductEntity(name = "کالای معتبر", sku = "PURCHASE-ROLLBACK-TEST", stockQuantity = 3, unitPriceMinor = 100)
            )
            val repository = BusinessRepository(dao)
            var rejected = false
            try {
                repository.createPurchase(
                    supplierName = "تأمین‌کننده",
                    lines = listOf(
                        PurchaseLineEntity(0L, productId, 2L, 50L, 100L),
                        PurchaseLineEntity(0L, 999_999L, 1L, 80L, 80L)
                    )
                )
            } catch (_: IllegalStateException) {
                rejected = true
            }
            assertTrue("Unknown product must reject the whole purchase", rejected)
            assertEquals(3L, dao.getProduct(productId)?.stockQuantity)
            assertEquals(0L, dao.observePurchases().first().size.toLong())
            assertTrue(dao.getStockMovements().isEmpty())
        } finally {
            db.close()
            context.getDatabasePath("purchase-rollback-test.db").delete()
        }
    }

    @Test
    fun migrationFromVersionElevenCreatesPurchaseTablesAndPreservesProducts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "purchase-migration-test.db"
        val original = database(context, name)
        val productId: Long
        try {
            productId = original.businessDao().insertProduct(
                ProductEntity(name = "کالای قدیمی", sku = "PURCHASE-MIGRATION-TEST", stockQuantity = 9, unitPriceMinor = 250)
            )
            val sql = original.openHelper.writableDatabase
            sql.execSQL("DROP TABLE purchase_payments")
            sql.execSQL("DROP TABLE purchase_lines")
            sql.execSQL("DROP TABLE purchases")
            sql.version = 11
        } finally {
            original.close()
        }

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13)
            .openHelperFactory(SupportOpenHelperFactory(DatabasePassphraseProvider.getOrCreate(context), null, true))
            .build()
        try {
            assertEquals(9L, migrated.businessDao().getProduct(productId)?.stockQuantity)
            assertTrue(migrated.businessDao().observePurchases().first().isEmpty())
        } finally {
            migrated.close()
            context.getDatabasePath(name).delete()
        }
    }
}
