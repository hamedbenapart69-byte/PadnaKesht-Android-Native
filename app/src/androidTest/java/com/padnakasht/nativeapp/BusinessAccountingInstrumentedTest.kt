package com.padnakasht.nativeapp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.padnakasht.nativeapp.data.local.AppDatabase
import com.padnakasht.nativeapp.data.local.CustomerEntity
import com.padnakasht.nativeapp.data.local.InvoiceEntity
import com.padnakasht.nativeapp.data.local.InvoiceLineEntity
import com.padnakasht.nativeapp.data.local.PaymentEntity
import com.padnakasht.nativeapp.data.local.ProductEntity
import com.padnakasht.nativeapp.security.DatabasePassphraseProvider
import kotlinx.coroutines.runBlocking
import net.sqlcipher.database.SupportFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BusinessAccountingInstrumentedTest {
    private fun database(context: Context, name: String): AppDatabase {
        System.loadLibrary("sqlcipher")
        context.getDatabasePath(name).delete()
        return Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(SupportFactory(DatabasePassphraseProvider.getOrCreate(context), null, true))
            .build()
    }

    @Test
    fun postingInvoiceChangesStockAndCancellationRestoresIt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = database(context, "business-atomic-test.db")
        try {
            val dao = db.businessDao()
            val customerId = dao.insertCustomer(CustomerEntity(name = "Customer test"))
            val productId = dao.insertProduct(ProductEntity(name = "Item test", sku = "SKU-ATOMIC", stockQuantity = 3, unitPriceMinor = 100))
            val invoiceId = dao.postInvoice(
                InvoiceEntity(customerId = customerId, createdAtEpochDay = 1),
                listOf(InvoiceLineEntity(invoiceId = 0, productId = productId, quantity = 2, unitPriceMinor = 100, lineTotalMinor = 200))
            )
            assertEquals(200L, dao.getInvoice(invoiceId)?.totalMinor)
            assertEquals(1L, dao.getProduct(productId)?.stockQuantity)
            dao.recordPayment(PaymentEntity(invoiceId = invoiceId, amountMinor = 100, paidAtEpochDay = 1))
            assertEquals(100L, dao.getPaymentsTotal(invoiceId))
            var rejected = false
            try {
                dao.recordPayment(PaymentEntity(invoiceId = invoiceId, amountMinor = 101, paidAtEpochDay = 1))
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertEquals(true, rejected)
            assertEquals(true, dao.cancelInvoice(invoiceId))
            assertEquals(3L, dao.getProduct(productId)?.stockQuantity)
            assertEquals("CANCELLED", dao.getInvoice(invoiceId)?.status)
        } finally {
            db.close()
            context.getDatabasePath("business-atomic-test.db").delete()
        }
    }

    @Test
    fun failedInvoicePostingRollsBackEarlierStockChanges() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = database(context, "business-rollback-test.db")
        try {
            val dao = db.businessDao()
            val customerId = dao.insertCustomer(CustomerEntity(name = "Rollback customer"))
            val stocked = dao.insertProduct(ProductEntity(name = "Available", sku = "SKU-ROLLBACK-A", stockQuantity = 2, unitPriceMinor = 10))
            val empty = dao.insertProduct(ProductEntity(name = "Unavailable", sku = "SKU-ROLLBACK-B", stockQuantity = 0, unitPriceMinor = 10))
            var rejected = false
            try {
                dao.postInvoice(
                    InvoiceEntity(customerId = customerId, createdAtEpochDay = 1),
                    listOf(
                        InvoiceLineEntity(invoiceId = 0, productId = stocked, quantity = 1, unitPriceMinor = 10, lineTotalMinor = 10),
                        InvoiceLineEntity(invoiceId = 0, productId = empty, quantity = 1, unitPriceMinor = 10, lineTotalMinor = 10)
                    )
                )
            } catch (_: IllegalStateException) {
                rejected = true
            }
            assertEquals(true, rejected)
            assertEquals(2L, dao.getProduct(stocked)?.stockQuantity)
            assertEquals(0, dao.invoiceCount())
        } finally {
            db.close()
            context.getDatabasePath("business-rollback-test.db").delete()
        }
    }
}
