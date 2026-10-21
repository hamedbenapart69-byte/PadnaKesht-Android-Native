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
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BusinessRepositoryInvariantTest {
    private fun database(context: Context, name: String): AppDatabase {
        System.loadLibrary("sqlcipher")
        context.getDatabasePath(name).delete()
        return Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(DatabasePassphraseProvider.getOrCreate(context), null, true))
            .build()
    }

    @Test
    fun singleProductInvoiceRejectsUnknownCustomerWithoutChangingStock() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = database(context, "repository-invalid-customer-test.db")
        try {
            val dao = db.businessDao()
            val productId = dao.insertProduct(
                ProductEntity(name = "Test product", sku = "INV-CUSTOMER-TEST", stockQuantity = 5, unitPriceMinor = 100)
            )
            val repository = BusinessRepository(dao)
            var rejected = false
            try {
                repository.createSingleProductInvoice(customerId = 999_999L, productId = productId, quantity = 1)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue("Unknown customer must be rejected before posting an invoice", rejected)
            assertEquals(5L, dao.getProduct(productId)?.stockQuantity)
            assertEquals(0, dao.invoiceCount())
        } finally {
            db.close()
            context.getDatabasePath("repository-invalid-customer-test.db").delete()
        }
    }

    @Test
    fun cancelledInvoiceHasZeroOutstandingBalance() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = database(context, "repository-cancelled-balance-test.db")
        try {
            val dao = db.businessDao()
            val customerId = dao.insertCustomer(CustomerEntity(name = "Balance test customer"))
            val productId = dao.insertProduct(
                ProductEntity(name = "Balance test product", sku = "INV-BALANCE-TEST", stockQuantity = 3, unitPriceMinor = 250)
            )
            val repository = BusinessRepository(dao)
            val invoiceId = repository.createSingleProductInvoice(customerId, productId, 1)
            assertEquals(250L, repository.outstandingBalance(invoiceId))
            assertTrue(repository.cancelInvoice(invoiceId))
            assertEquals(0L, repository.outstandingBalance(invoiceId))
            assertEquals(3L, dao.getProduct(productId)?.stockQuantity)
        } finally {
            db.close()
            context.getDatabasePath("repository-cancelled-balance-test.db").delete()
        }
    }
}
