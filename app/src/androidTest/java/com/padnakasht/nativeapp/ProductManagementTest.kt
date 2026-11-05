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
class ProductManagementTest {
    private fun database(context: Context, name: String): AppDatabase {
        System.loadLibrary("sqlcipher")
        context.getDatabasePath(name).delete()
        return Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11)
            .openHelperFactory(SupportOpenHelperFactory(DatabasePassphraseProvider.getOrCreate(context), null, true))
            .build()
    }

    @Test
    fun editingProductDoesNotRewriteHistoricalInvoicePricesOrStock() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = database(context, "product-edit-history-test.db")
        try {
            val dao = db.businessDao()
            val customerId = dao.insertCustomer(CustomerEntity(name = "مشتری کالا"))
            val productId = dao.insertProduct(
                ProductEntity(name = "گردو", sku = "PRODUCT-EDIT-HISTORY", stockQuantity = 4, unitPriceMinor = 500)
            )
            val repository = BusinessRepository(dao)
            val invoiceId = repository.createSingleProductInvoice(customerId, productId, 1)

            repository.updateProduct(productId, "گردوی پادنا", "PRODUCT-EDIT-HISTORY", 900, "خشکبار", 2)

            val historicalLine = dao.getInvoiceLines(invoiceId).single()
            assertEquals(500L, historicalLine.unitPriceMinor)
            assertEquals(500L, historicalLine.lineTotalMinor)
            assertEquals("گردوی پادنا", dao.getProduct(productId)?.name)
            assertEquals(900L, dao.getProduct(productId)?.unitPriceMinor)
            assertEquals(3L, dao.getProduct(productId)?.stockQuantity)
        } finally {
            db.close()
            context.getDatabasePath("product-edit-history-test.db").delete()
        }
    }

    @Test
    fun archivedProductCannotBeSoldAndItsHistoricalInvoiceRemains() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = database(context, "product-archive-test.db")
        try {
            val dao = db.businessDao()
            val customerId = dao.insertCustomer(CustomerEntity(name = "مشتری بایگانی کالا"))
            val productId = dao.insertProduct(
                ProductEntity(name = "محصول بایگانی", sku = "PRODUCT-ARCHIVE-TEST", stockQuantity = 3, unitPriceMinor = 750)
            )
            val repository = BusinessRepository(dao)
            val invoiceId = repository.createSingleProductInvoice(customerId, productId, 1)

            assertTrue(repository.setProductActive(productId, false))
            assertFalse(dao.getProduct(productId)?.isActive ?: true)
            var rejected = false
            try {
                repository.createSingleProductInvoice(customerId, productId, 1)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue("Archived products must not be sold again", rejected)
            assertEquals(2L, dao.getProduct(productId)?.stockQuantity)
            assertEquals(1, dao.invoiceCount())
            assertEquals(invoiceId, dao.observeInvoices().first().single().id)
            assertTrue(repository.setProductActive(productId, true))
            assertTrue(dao.getProduct(productId)?.isActive ?: false)
        } finally {
            db.close()
            context.getDatabasePath("product-archive-test.db").delete()
        }
    }

    @Test
    fun productUpdateRejectsBlankNameAndSku() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = database(context, "product-validation-test.db")
        try {
            val dao = db.businessDao()
            val productId = dao.insertProduct(
                ProductEntity(name = "محصول معتبر", sku = "PRODUCT-VALIDATION", stockQuantity = 1, unitPriceMinor = 100)
            )
            val repository = BusinessRepository(dao)
            var nameRejected = false
            try {
                repository.updateProduct(productId, " ", "PRODUCT-VALIDATION", 100, "عمومی", 0)
            } catch (_: IllegalArgumentException) {
                nameRejected = true
            }
            var skuRejected = false
            try {
                repository.updateProduct(productId, "محصول معتبر", " ", 100, "عمومی", 0)
            } catch (_: IllegalArgumentException) {
                skuRejected = true
            }
            assertTrue(nameRejected)
            assertTrue(skuRejected)
            assertEquals("محصول معتبر", dao.getProduct(productId)?.name)
        } finally {
            db.close()
            context.getDatabasePath("product-validation-test.db").delete()
        }
    }
}
