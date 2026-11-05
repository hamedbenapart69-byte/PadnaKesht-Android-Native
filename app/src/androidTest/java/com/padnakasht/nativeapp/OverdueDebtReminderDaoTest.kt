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
import com.padnakasht.nativeapp.data.local.PaymentRefundEntity
import com.padnakasht.nativeapp.data.local.ProductEntity
import com.padnakasht.nativeapp.data.local.SalesReturnEntity
import com.padnakasht.nativeapp.domain.OverdueDebtReminderRules
import com.padnakasht.nativeapp.security.DatabasePassphraseProvider
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverdueDebtReminderDaoTest {
    @Test
    fun queryUsesDueDateFallbackAndAggregatesPostedInvoiceEvents() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "overdue-debt-reminder-test.db"
        context.getDatabasePath(name).delete()
        System.loadLibrary("sqlcipher")
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(
                AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13
            )
            .openHelperFactory(SupportOpenHelperFactory(DatabasePassphraseProvider.getOrCreate(context), null, true))
            .build()
        try {
            val dao = db.businessDao()
            val customerId = dao.insertCustomer(CustomerEntity(name = "عميل اختبار"))
            val productId = dao.insertProduct(
                ProductEntity(name = "کالای تست", sku = "OVERDUE-REMINDER-TEST", stockQuantity = 5, unitPriceMinor = 100_000)
            )

            val partialId = dao.insertInvoice(InvoiceEntity(customerId = customerId, createdAtEpochDay = 90, totalMinor = 100_000, dueAtEpochDay = 69))
            val partialLineId = dao.insertInvoiceLine(InvoiceLineEntity(invoiceId = partialId, productId = productId, quantity = 1, unitPriceMinor = 100_000, lineTotalMinor = 100_000)).let { 0L }
            val paymentId = dao.insertPayment(PaymentEntity(invoiceId = partialId, amountMinor = 20_000, paidAtEpochDay = 91))
            dao.insertPaymentRefund(PaymentRefundEntity(invoiceId = partialId, paymentId = paymentId, amountMinor = 5_000, refundedAtEpochDay = 92, reason = "آزمون", idempotencyKey = "overdue-refund-test"))
            dao.insertSalesReturn(SalesReturnEntity(invoiceId = partialId, invoiceLineId = partialLineId, productId = productId, quantity = 1, creditAmountMinor = 10_000, reason = "آزمون", returnedAtEpochDay = 93))

            dao.insertInvoice(InvoiceEntity(customerId = customerId, createdAtEpochDay = 68, totalMinor = 100_000, dueAtEpochDay = null))
            val settledId = dao.insertInvoice(InvoiceEntity(customerId = customerId, createdAtEpochDay = 60, totalMinor = 100_000, dueAtEpochDay = 69))
            dao.insertPayment(PaymentEntity(invoiceId = settledId, amountMinor = 100_000, paidAtEpochDay = 61))
            dao.insertInvoice(InvoiceEntity(customerId = customerId, createdAtEpochDay = 50, totalMinor = 100_000, status = "CANCELLED", dueAtEpochDay = 60))
            dao.insertInvoice(InvoiceEntity(customerId = customerId, createdAtEpochDay = 90, totalMinor = 100_000, dueAtEpochDay = 70))

            val candidates = dao.getOverdueInvoiceCandidates(cutoffEpochDay = 70)
            assertEquals(4, candidates.size)
            val summary = OverdueDebtReminderRules.summarize(candidates, todayEpochDay = 100)
            assertEquals(2, summary.invoiceCount)
            assertEquals(1, summary.customerCount)
            assertEquals(175_000L, summary.totalOutstandingMinor)
        } finally {
            db.close()
            context.getDatabasePath(name).delete()
        }
    }
}
