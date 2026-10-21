package com.padnakasht.nativeapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BusinessAccountingTest {
    @Test fun invoiceWithoutPaymentIsReceivableNotCash() {
        val result = BusinessAccounting.summarize(listOf(InvoiceAccountingEvent(1, 10, 1_000, "POSTED")), emptyList(), emptyList(), emptyList())
        assertEquals(1_000L, result.invoicedSalesMinor)
        assertEquals(0L, result.receiptsMinor)
        assertEquals(1_000L, result.outstandingBalanceMinor)
    }

    @Test fun partialAndFullPaymentsAreNotAddedToSales() {
        val invoices = listOf(InvoiceAccountingEvent(1, 10, 1_000, "POSTED"))
        val partial = BusinessAccounting.summarize(invoices, listOf(PaymentAccountingEvent(1, 11, 400)), emptyList(), emptyList())
        assertEquals(1_000L, partial.invoicedSalesMinor)
        assertEquals(400L, partial.receiptsMinor)
        assertEquals(600L, partial.outstandingBalanceMinor)
        val full = BusinessAccounting.summarize(invoices, listOf(PaymentAccountingEvent(1, 11, 400), PaymentAccountingEvent(1, 12, 600)), emptyList(), emptyList())
        assertEquals(1_000L, full.invoicedSalesMinor)
        assertEquals(1_000L, full.receiptsMinor)
        assertEquals(0L, full.outstandingBalanceMinor)
    }

    @Test fun returnCreditReducesSalesAndReceivableButNotCash() {
        val result = BusinessAccounting.summarize(
            listOf(InvoiceAccountingEvent(1, 10, 1_000, "POSTED")),
            listOf(PaymentAccountingEvent(1, 11, 400)), listOf(ReturnAccountingEvent(1, 12, 200)), emptyList())
        assertEquals(800L, result.netSalesMinor)
        assertEquals(400L, result.receiptsMinor)
        assertEquals(400L, result.outstandingBalanceMinor)
    }

    @Test fun cancellationAndRefundRemainVisibleAsSeparateCashEvents() {
        val result = BusinessAccounting.summarize(
            listOf(InvoiceAccountingEvent(1, 10, 1_000, "CANCELLED")),
            listOf(PaymentAccountingEvent(1, 11, 500)), emptyList(),
            listOf(RefundAccountingEvent(1, 12, 500)))
        assertEquals(0L, result.invoicedSalesMinor)
        assertEquals(500L, result.receiptsMinor)
        assertEquals(500L, result.refundsMinor)
        assertEquals(0L, result.netCashReceiptsMinor)
        assertEquals(0L, result.outstandingBalanceMinor)
    }

    @Test fun periodUsesEachEventOwnDate() {
        val invoices = listOf(InvoiceAccountingEvent(1, 10, 1_000, "POSTED"), InvoiceAccountingEvent(2, 40, 2_000, "POSTED"))
        val payments = listOf(PaymentAccountingEvent(1, 20, 300), PaymentAccountingEvent(2, 41, 700))
        val returns = listOf(ReturnAccountingEvent(1, 21, 100))
        val refunds = listOf(RefundAccountingEvent(2, 42, 200))
        val first = BusinessAccounting.period(1, 30, invoices, payments, returns, refunds)
        assertEquals(1_000L, first.invoicedSalesMinor); assertEquals(300L, first.receiptsMinor)
        assertEquals(100L, first.returnCreditsMinor); assertEquals(0L, first.refundsMinor)
        val second = BusinessAccounting.period(31, 60, invoices, payments, returns, refunds)
        assertEquals(2_000L, second.invoicedSalesMinor); assertEquals(700L, second.receiptsMinor)
        assertEquals(0L, second.returnCreditsMinor); assertEquals(200L, second.refundsMinor)
    }

    @Test fun periodIncludesBothBoundaryDatesAndExcludesAdjacentEvents() {
        val invoices = listOf(
            InvoiceAccountingEvent(1, 9, 90, "POSTED"),
            InvoiceAccountingEvent(2, 10, 100, "POSTED"),
            InvoiceAccountingEvent(3, 20, 200, "POSTED"),
            InvoiceAccountingEvent(4, 21, 210, "POSTED")
        )
        val payments = listOf(
            PaymentAccountingEvent(1, 9, 9),
            PaymentAccountingEvent(2, 10, 10),
            PaymentAccountingEvent(3, 20, 20),
            PaymentAccountingEvent(4, 21, 21)
        )
        val result = BusinessAccounting.period(10, 20, invoices, payments, emptyList(), emptyList())
        assertEquals(300L, result.invoicedSalesMinor)
        assertEquals(30L, result.receiptsMinor)
    }

    @Test(expected = IllegalArgumentException::class)
    fun periodRejectsEndBeforeStart() {
        BusinessAccounting.period(20, 10, emptyList(), emptyList(), emptyList(), emptyList())
    }

    @Test(expected = ArithmeticException::class) fun totalsCannotSilentlyOverflow() {
        BusinessAccounting.summarize(
            listOf(InvoiceAccountingEvent(1, 1, Long.MAX_VALUE, "POSTED"), InvoiceAccountingEvent(2, 2, 1, "POSTED")),
            emptyList(), emptyList(), emptyList())
    }
}
