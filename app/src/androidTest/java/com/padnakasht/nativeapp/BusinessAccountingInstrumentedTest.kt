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
import com.padnakasht.nativeapp.data.local.TransactionEntity
import com.padnakasht.nativeapp.security.DatabasePassphraseProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BusinessAccountingInstrumentedTest {
    private fun database(context: Context, name: String): AppDatabase {
        System.loadLibrary("sqlcipher")
        context.getDatabasePath(name).delete()
        return Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(DatabasePassphraseProvider.getOrCreate(context), null, true))
            .build()
    }

    @Test
    fun voidingLedgerTransactionPreservesAuditRecord() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = database(context, "ledger-void-test.db")
        try {
            val dao = db.transactionDao()
            val id = dao.insert(
                TransactionEntity(
                    amountMinor = 1250L,
                    kind = "INCOME",
                    category = "Audit test",
                    note = "Retain original record",
                    occurredAtEpochDay = 10L
                )
            )
            assertEquals(1, dao.getAll().size)
            assertEquals(1, dao.voidById(id))
            assertTrue(dao.getAll().isEmpty())
            assertEquals(1, dao.countAllRows())
            assertEquals(0, dao.voidById(id))
        } finally {
            db.close()
            context.getDatabasePath("ledger-void-test.db").delete()
        }
    }

    @Test
    fun productCategoryAndLowStockThresholdPersistLocally() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = database(context, "product-threshold-test.db")
        try {
            val dao = db.businessDao()
            val id = dao.insertProduct(ProductEntity(
                name = "Walnut",
                sku = "SKU-LOW-STOCK",
                stockQuantity = 2,
                unitPriceMinor = 100,
                category = "خشکبار",
                lowStockThreshold = 3
            ))
            val saved = dao.getProduct(id)!!
            assertEquals("خشکبار", saved.category)
            assertEquals(3L, saved.lowStockThreshold)
            assertTrue(saved.stockQuantity <= saved.lowStockThreshold)
        } finally {
            db.close()
            context.getDatabasePath("product-threshold-test.db").delete()
        }
    }

    @Test
    fun partialAndFullReturnsRestoreStockAndApplyAuditedCustomerCredit() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = database(context, "business-return-test.db")
        try {
            val dao = db.businessDao()
            val customerId = dao.insertCustomer(CustomerEntity(name = "Return customer"))
            val productId = dao.insertProduct(ProductEntity(name = "Return item", sku = "SKU-RETURN", stockQuantity = 4, unitPriceMinor = 100))
            val invoiceId = dao.postInvoice(
                InvoiceEntity(customerId = customerId, createdAtEpochDay = 10),
                listOf(InvoiceLineEntity(invoiceId = 0, productId = productId, quantity = 3, unitPriceMinor = 100, lineTotalMinor = 300))
            )
            val lineId = dao.getInvoiceLines(invoiceId).single().id
            dao.recordReturn(invoiceId, lineId, 1, "کالای آسیب‌دیده", 11)
            assertEquals(2L, dao.getProduct(productId)?.stockQuantity)
            assertEquals(100L, dao.getReturnsTotal(invoiceId))
            assertEquals(200L, dao.observeCustomerBalance(customerId).first())
            dao.recordReturn(invoiceId, lineId, 2, "برگشت کامل باقی‌مانده", 12)
            assertEquals(4L, dao.getProduct(productId)?.stockQuantity)
            assertEquals(300L, dao.getReturnsTotal(invoiceId))
            var overReturnRejected = false
            try {
                dao.recordReturn(invoiceId, lineId, 1, "تعداد بیشتر از فروش", 13)
            } catch (_: IllegalArgumentException) {
                overReturnRejected = true
            }
            assertTrue(overReturnRejected)
            assertEquals(3, dao.getStockMovements().size)
        } finally {
            db.close()
            context.getDatabasePath("business-return-test.db").delete()
        }
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
            assertEquals(200L, dao.getInvoice(invoiceId)?.totalMinor ?: -1L)
            assertEquals(1L, dao.getProduct(productId)?.stockQuantity ?: -1L)
            assertTrue(dao.cancelInvoice(invoiceId))
            assertEquals(3L, dao.getProduct(productId)?.stockQuantity)
            assertEquals("CANCELLED", dao.getInvoice(invoiceId)?.status)

            val secondInvoiceId = dao.postInvoice(
                InvoiceEntity(customerId = customerId, createdAtEpochDay = 2),
                listOf(InvoiceLineEntity(invoiceId = 0, productId = productId, quantity = 1, unitPriceMinor = 100, lineTotalMinor = 100))
            )
            dao.recordPayment(PaymentEntity(invoiceId = secondInvoiceId, amountMinor = 50, paidAtEpochDay = 2))
            assertEquals(50L, dao.getPaymentsTotal(secondInvoiceId))
            var rejected = false
            try {
                dao.recordPayment(PaymentEntity(invoiceId = secondInvoiceId, amountMinor = 51, paidAtEpochDay = 2))
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(rejected)
            var cancellationRejected = false
            try {
                dao.cancelInvoice(secondInvoiceId)
            } catch (_: IllegalArgumentException) {
                cancellationRejected = true
            }
            assertTrue(cancellationRejected)
        } finally {
            db.close()
            context.getDatabasePath("business-atomic-test.db").delete()
        }
    }

    @Test
    fun stockMovementsTrackInvoiceCancellationAndManualAdjustment() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = database(context, "stock-movement-test.db")
        try {
            val dao = db.businessDao()
            val customerId = dao.insertCustomer(CustomerEntity(name = "Stock audit customer"))
            val productId = dao.insertProduct(ProductEntity(name = "Stock audit item", sku = "SKU-STOCK-AUDIT", stockQuantity = 5, unitPriceMinor = 100))
            val invoiceId = dao.postInvoice(
                InvoiceEntity(customerId = customerId, createdAtEpochDay = 100),
                listOf(InvoiceLineEntity(invoiceId = 0, productId = productId, quantity = 2, unitPriceMinor = 100, lineTotalMinor = 200))
            )
            assertEquals(-2L, dao.getStockMovements().first().quantityDelta)
            assertTrue(dao.cancelInvoice(invoiceId))
            assertEquals(2L, dao.getStockMovements().first().quantityDelta)
            assertTrue(dao.adjustStock(productId, 3, "Found during count", 101))
            val movements = dao.getStockMovements()
            assertEquals(3, movements.size)
            assertEquals(3L, movements.first().quantityDelta)
            assertEquals("Found during count", movements.first().reason)
            // 5 initial - 2 invoice + 2 cancellation restore + 3 manual adjustment = 8
            assertEquals(8L, dao.getProduct(productId)?.stockQuantity)
        } finally {
            db.close()
            context.getDatabasePath("stock-movement-test.db").delete()
        }
    }

    @Test
    fun discountAndDueDateArePersistedAndPaymentUsesDiscountedTotal() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = database(context, "business-discount-test.db")
        try {
            val dao = db.businessDao()
            val customerId = dao.insertCustomer(CustomerEntity(name = "Discount customer"))
            val productId = dao.insertProduct(ProductEntity(name = "Discount item", sku = "SKU-DISCOUNT", stockQuantity = 4, unitPriceMinor = 250))
            val dueDay = 20000L
            val invoiceId = dao.postInvoice(
                InvoiceEntity(customerId = customerId, createdAtEpochDay = 1, discountMinor = 100, dueAtEpochDay = dueDay),
                listOf(InvoiceLineEntity(invoiceId = 0, productId = productId, quantity = 2, unitPriceMinor = 250, lineTotalMinor = 500))
            )
            val invoice = dao.getInvoice(invoiceId)!!
            assertEquals(400L, invoice.totalMinor)
            assertEquals(100L, invoice.discountMinor)
            assertEquals(dueDay, invoice.dueAtEpochDay)
            dao.recordPayment(PaymentEntity(invoiceId = invoiceId, amountMinor = 400, paidAtEpochDay = 2))
            assertEquals(400L, dao.getPaymentsTotal(invoiceId))
        } finally {
            db.close()
            context.getDatabasePath("business-discount-test.db").delete()
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
            assertTrue(rejected)
            assertEquals(2L, dao.getProduct(stocked)?.stockQuantity ?: -1L)
            assertEquals(0, dao.invoiceCount())
        } finally {
            db.close()
            context.getDatabasePath("business-rollback-test.db").delete()
        }
    }

    @Test
    fun repeatedPaymentRequestWithSameIdempotencyKeyIsRecordedOnlyOnce() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = database(context, "payment-idempotency-test.db")
        try {
            val dao = db.businessDao()
            val customerId = dao.insertCustomer(CustomerEntity(name = "Idempotency customer"))
            val productId = dao.insertProduct(ProductEntity(name = "Idempotency item", sku = "SKU-IDEMPOTENCY", stockQuantity = 2, unitPriceMinor = 500))
            val invoiceId = dao.postInvoice(
                InvoiceEntity(customerId = customerId, createdAtEpochDay = 10),
                listOf(InvoiceLineEntity(invoiceId = 0, productId = productId, quantity = 2, unitPriceMinor = 500, lineTotalMinor = 1000))
            )
            val key = "ci-payment-operation-unique-key"
            val payment = PaymentEntity(invoiceId = invoiceId, amountMinor = 100, paidAtEpochDay = 10, note = "single receive", idempotencyKey = key)
            val firstId = dao.recordPayment(payment)
            val repeatedId = dao.recordPayment(payment.copy(paidAtEpochDay = 11))
            assertEquals(firstId, repeatedId)
            assertEquals(100L, dao.getPaymentsTotal(invoiceId))
            assertEquals(1, dao.observePayments(invoiceId).first().size)
            var mismatchRejected = false
            try {
                dao.recordPayment(payment.copy(amountMinor = 200))
            } catch (_: IllegalArgumentException) {
                mismatchRejected = true
            }
            assertTrue("Reusing an operation key for a different amount must be rejected", mismatchRejected)
            assertEquals(100L, dao.getPaymentsTotal(invoiceId))
        } finally {
            db.close()
            context.getDatabasePath("payment-idempotency-test.db").delete()
        }
    }

}
