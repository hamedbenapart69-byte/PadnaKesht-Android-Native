package com.padnakasht.nativeapp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.padnakasht.nativeapp.data.BusinessRepository
import com.padnakasht.nativeapp.data.local.AppDatabase
import com.padnakasht.nativeapp.data.local.CustomerEntity
import com.padnakasht.nativeapp.data.local.InvoiceLineEntity
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
class FractionalQuantityInstrumentedTest {
    private fun database(context: Context, name: String): AppDatabase {
        System.loadLibrary("sqlcipher")
        context.getDatabasePath(name).delete()
        return Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(DatabasePassphraseProvider.getOrCreate(context), null, true))
            .build()
    }

    @Test
    fun halfKilogramSaleQuarterKilogramReturnAndPurchaseKeepStockAndMoneyExact() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = database(context, "fractional-quantity-ledger-test.db")
        try {
            val dao = db.businessDao()
            val customerId = dao.insertCustomer(CustomerEntity(name = "مشتری وزنی"))
            val productId = dao.insertProduct(
                ProductEntity(
                    name = "گردوی وزنی",
                    sku = "FRACTIONAL-WEIGHT-TEST",
                    stockQuantity = 2_000L,
                    unitPriceMinor = 1_300_000L,
                    unitLabel = "کیلوگرم",
                    quantityScale = 1_000L
                )
            )
            val repository = BusinessRepository(dao)
            val invoiceId = repository.createInvoice(
                customerId,
                listOf(
                    InvoiceLineEntity(
                        invoiceId = 0L,
                        productId = productId,
                        quantity = 500L,
                        unitPriceMinor = 1_300_000L,
                        lineTotalMinor = 650_000L,
                        quantityScale = 1_000L
                    )
                )
            )
            assertEquals(650_000L, dao.getInvoice(invoiceId)?.totalMinor)
            assertEquals(1_500L, dao.getProduct(productId)?.stockQuantity)

            val invoiceLine = repository.observeInvoiceLines(invoiceId).first().single()
            repository.recordReturn(invoiceId, invoiceLine.id, 250L, "برگشت یک‌چهارم کیلو")
            val returned = repository.observeSalesReturns(invoiceId).first().single()
            assertEquals(325_000L, returned.creditAmountMinor)
            assertEquals(1_750L, dao.getProduct(productId)?.stockQuantity)

            val purchaseId = repository.createPurchase(
                supplierName = "تأمین‌کننده وزنی",
                lines = listOf(
                    PurchaseLineEntity(
                        purchaseId = 0L,
                        productId = productId,
                        quantity = 500L,
                        unitCostMinor = 900_000L,
                        lineTotalMinor = 450_000L,
                        quantityScale = 1_000L
                    )
                )
            )
            assertEquals(450_000L, dao.getPurchase(purchaseId)?.totalMinor)
            assertEquals(2_250L, dao.getProduct(productId)?.stockQuantity)
        } finally {
            db.close()
            context.getDatabasePath("fractional-quantity-ledger-test.db").delete()
        }
    }

    @Test
    fun mismatchedQuantityScaleCannotPostAnInvoiceOrChangeStock() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = database(context, "fractional-quantity-scale-rejection-test.db")
        try {
            val dao = db.businessDao()
            val customerId = dao.insertCustomer(CustomerEntity(name = "مشتری مقیاس"))
            val productId = dao.insertProduct(
                ProductEntity(
                    name = "کالای کیلوگرمی",
                    sku = "FRACTIONAL-SCALE-REJECT",
                    stockQuantity = 2_000L,
                    unitPriceMinor = 1_300_000L,
                    unitLabel = "کیلوگرم",
                    quantityScale = 1_000L
                )
            )
            val repository = BusinessRepository(dao)
            var rejected = false
            try {
                repository.createInvoice(
                    customerId,
                    listOf(
                        InvoiceLineEntity(
                            invoiceId = 0L,
                            productId = productId,
                            quantity = 500L,
                            unitPriceMinor = 1_300_000L,
                            lineTotalMinor = 650_000L,
                            quantityScale = 1L
                        )
                    )
                )
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue("Invoice quantities must use the product's stored scale", rejected)
            assertEquals(2_000L, dao.getProduct(productId)?.stockQuantity)
            assertEquals(0, dao.invoiceCount())
        } finally {
            db.close()
            context.getDatabasePath("fractional-quantity-scale-rejection-test.db").delete()
        }
    }
}
