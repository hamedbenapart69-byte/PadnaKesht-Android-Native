package com.padnakasht.nativeapp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.padnakasht.nativeapp.data.BusinessRepository
import com.padnakasht.nativeapp.data.local.AppDatabase
import com.padnakasht.nativeapp.data.local.CustomerEntity
import com.padnakasht.nativeapp.data.local.ProductEntity
import com.padnakasht.nativeapp.security.DatabasePassphraseProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomerBalanceAdjustmentTest {
    private fun database(context: Context, name: String): AppDatabase {
        System.loadLibrary("sqlcipher")
        context.getDatabasePath(name).delete()
        return Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11)
            .openHelperFactory(SupportOpenHelperFactory(DatabasePassphraseProvider.getOrCreate(context), null, true))
            .build()
    }

    @Test
    fun manualOpeningBalanceAndAdjustmentsUpdateCustomerBalance() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = database(context, "customer-opening-balance-test.db")
        try {
            val dao = db.businessDao()
            val customerId = dao.insertCustomer(CustomerEntity(name = "مشتری آزمایشی"))
            val repository = BusinessRepository(dao)

            repository.adjustCustomerBalance(customerId, 125_000L, "مانده افتتاحیه")
            assertEquals(125_000L, dao.observeCustomerBalance(customerId).first())

            repository.adjustCustomerBalance(customerId, -25_000L, "اصلاح حساب")
            assertEquals(100_000L, dao.observeCustomerBalance(customerId).first())
        } finally {
            db.close()
            context.getDatabasePath("customer-opening-balance-test.db").delete()
        }
    }

    @Test
    fun customerCanBeEditedAndArchivedWithoutRemovingInvoiceHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = database(context, "customer-edit-archive-test.db")
        try {
            val dao = db.businessDao()
            val customerId = dao.insertCustomer(CustomerEntity(name = "نام اولیه"))
            val productId = dao.insertProduct(
                ProductEntity(name = "کالای آزمایشی", sku = "CUSTOMER-ARCHIVE-TEST", stockQuantity = 2, unitPriceMinor = 500)
            )
            val repository = BusinessRepository(dao)
            repository.createSingleProductInvoice(customerId, productId, 1)

            repository.updateCustomer(customerId, "نام اصلاح‌شده", "09120000000", "یادداشت")
            assertEquals("نام اصلاح‌شده", dao.getCustomer(customerId)?.name)
            assertEquals("09120000000", dao.getCustomer(customerId)?.phone)
            assertEquals(1, dao.invoiceCount())

            assertTrue(repository.setCustomerActive(customerId, false))
            assertFalse(dao.getCustomer(customerId)?.isActive ?: true)
            assertEquals(1, dao.invoiceCount())
            var archivedCustomerRejected = false
            try {
                repository.createSingleProductInvoice(customerId, productId, 1)
            } catch (_: IllegalArgumentException) {
                archivedCustomerRejected = true
            }
            assertTrue("Archived customers must not receive new invoices", archivedCustomerRejected)
            assertEquals(1, dao.invoiceCount())
            assertTrue(repository.setCustomerActive(customerId, true))
            assertTrue(dao.getCustomer(customerId)?.isActive ?: false)
        } finally {
            db.close()
            context.getDatabasePath("customer-edit-archive-test.db").delete()
        }
    }

    @Test
    fun migrationFromVersionNinePreservesExistingCustomerAndInvoiceData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "customer-ledger-migration-test.db"
        val original = database(context, name)
        val customerId: Long
        try {
            val dao = original.businessDao()
            customerId = dao.insertCustomer(CustomerEntity(name = "مشتری قدیمی"))
            val productId = dao.insertProduct(
                ProductEntity(name = "کالای قدیمی", sku = "CUSTOMER-MIGRATION-TEST", stockQuantity = 2, unitPriceMinor = 700)
            )
            BusinessRepository(dao).createSingleProductInvoice(customerId, productId, 1)

            val sql = original.openHelper.writableDatabase
            sql.execSQL("DROP TABLE customer_balance_adjustments")
            sql.execSQL("ALTER TABLE customers DROP COLUMN isActive")
            sql.execSQL("ALTER TABLE products DROP COLUMN isActive")
            sql.version = 9
        } finally {
            original.close()
        }

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11)
            .openHelperFactory(SupportOpenHelperFactory(DatabasePassphraseProvider.getOrCreate(context), null, true))
            .build()
        try {
            val dao = migrated.businessDao()
            assertEquals("مشتری قدیمی", dao.getCustomer(customerId)?.name)
            assertTrue(dao.getCustomer(customerId)?.isActive ?: false)
            assertEquals(1, dao.invoiceCount())
            assertEquals(700L, dao.observeCustomerBalance(customerId).first())
        } finally {
            migrated.close()
            context.getDatabasePath(name).delete()
        }
    }

    @Test
    fun zeroCustomerBalanceAdjustmentIsRejected() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = database(context, "customer-zero-adjustment-test.db")
        try {
            val dao = db.businessDao()
            val customerId = dao.insertCustomer(CustomerEntity(name = "مشتری صفر"))
            var rejected = false
            try {
                BusinessRepository(dao).adjustCustomerBalance(customerId, 0L, "مقدار نامعتبر")
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue("A zero balance adjustment must be rejected", rejected)
        } finally {
            db.close()
            context.getDatabasePath("customer-zero-adjustment-test.db").delete()
        }
    }
}
