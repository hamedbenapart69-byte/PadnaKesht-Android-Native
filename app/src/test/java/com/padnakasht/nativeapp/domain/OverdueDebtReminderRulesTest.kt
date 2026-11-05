package com.padnakasht.nativeapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class OverdueDebtReminderRulesTest {
    private fun invoice(
        customer: Long = 1,
        due: Long = 69,
        total: Long = 100_000,
        returns: Long = 0,
        receipts: Long = 0,
        refunds: Long = 0,
        status: String = "POSTED"
    ) = OverdueInvoiceCandidate(customer, due, total, returns, receipts, refunds, status)

    @Test fun exactThirtyDaysIsExcludedAndThirtyOneDaysIsIncluded() {
        val result = OverdueDebtReminderRules.summarize(
            listOf(invoice(due = 70), invoice(customer = 2, due = 69)),
            todayEpochDay = 100
        )
        assertEquals(1, result.invoiceCount)
        assertEquals(1, result.customerCount)
        assertEquals(100_000L, result.totalOutstandingMinor)
    }

    @Test fun futureDueDatesAndCancelledInvoicesAreExcluded() {
        val result = OverdueDebtReminderRules.summarize(
            listOf(invoice(due = 101), invoice(customer = 2, status = "CANCELLED")),
            todayEpochDay = 100
        )
        assertEquals(OverdueDebtSummary(0, 0, 0), result)
    }

    @Test fun settledAndNegativeBalancesAreExcluded() {
        val result = OverdueDebtReminderRules.summarize(
            listOf(invoice(receipts = 100_000), invoice(customer = 2, returns = 120_000)),
            todayEpochDay = 100
        )
        assertEquals(OverdueDebtSummary(0, 0, 0), result)
    }

    @Test fun returnsReduceOutstandingAndRefundsIncreaseIt() {
        val result = OverdueDebtReminderRules.summarize(
            listOf(invoice(total = 100_000, returns = 10_000, receipts = 20_000, refunds = 5_000)),
            todayEpochDay = 100
        )
        assertEquals(75_000L, result.totalOutstandingMinor)
    }

    @Test fun multipleInvoicesForOneCustomerCountAsOneCustomer() {
        val result = OverdueDebtReminderRules.summarize(
            listOf(invoice(due = 1), invoice(due = 2), invoice(customer = 2, due = 3)),
            todayEpochDay = 100
        )
        assertEquals(3, result.invoiceCount)
        assertEquals(2, result.customerCount)
        assertEquals(300_000L, result.totalOutstandingMinor)
    }

    @Test(expected = ArithmeticException::class)
    fun totalOverflowFailsClosed() {
        OverdueDebtReminderRules.summarize(
            listOf(invoice(total = Long.MAX_VALUE), invoice(customer = 2, total = 1)),
            todayEpochDay = 100
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeThresholdIsRejected() {
        OverdueDebtReminderRules.summarize(emptyList(), 100, -1)
    }
}
