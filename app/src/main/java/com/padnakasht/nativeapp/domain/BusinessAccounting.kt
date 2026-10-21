package com.padnakasht.nativeapp.domain

/**
 * Invoice sales, cash receipts, return credits and refunds are distinct ledgers.
 * Dates are Gregorian epoch days in storage and are converted to Jalali only for display.
 */
data class InvoiceAccountingEvent(val invoiceId: Long, val epochDay: Long, val totalMinor: Long, val status: String)
data class PaymentAccountingEvent(val invoiceId: Long, val epochDay: Long, val amountMinor: Long)
data class ReturnAccountingEvent(val invoiceId: Long, val epochDay: Long, val creditMinor: Long)
data class RefundAccountingEvent(val invoiceId: Long, val epochDay: Long, val amountMinor: Long)

data class BusinessAccountingSummary(
    val postedInvoiceCount: Int,
    val invoicedSalesMinor: Long,
    val returnCreditsMinor: Long,
    val netSalesMinor: Long,
    val receiptsMinor: Long,
    val refundsMinor: Long,
    val netCashReceiptsMinor: Long,
    val outstandingBalanceMinor: Long
)

data class BusinessAccountingPeriod(
    val startEpochDay: Long,
    val endEpochDayInclusive: Long,
    val invoicedSalesMinor: Long,
    val receiptsMinor: Long,
    val returnCreditsMinor: Long,
    val refundsMinor: Long
)

object BusinessAccounting {
    fun summarize(
        invoices: List<InvoiceAccountingEvent>,
        payments: List<PaymentAccountingEvent>,
        returns: List<ReturnAccountingEvent>,
        refunds: List<RefundAccountingEvent>
    ): BusinessAccountingSummary {
        val posted = invoices.filter { it.status == "POSTED" }
        val postedIds = posted.mapTo(mutableSetOf()) { it.invoiceId }
        val sales = sumExact(posted.map { it.totalMinor })
        val credits = sumExact(returns.filter { it.invoiceId in postedIds }.map { it.creditMinor })
        val grossReceipts = sumExact(payments.map { it.amountMinor })
        val postedReceipts = sumExact(payments.filter { it.invoiceId in postedIds }.map { it.amountMinor })
        val refundTotal = sumExact(refunds.map { it.amountMinor })
        val netSales = Math.subtractExact(sales, credits)
        return BusinessAccountingSummary(
            postedInvoiceCount = posted.size,
            invoicedSalesMinor = sales,
            returnCreditsMinor = credits,
            netSalesMinor = netSales,
            receiptsMinor = grossReceipts,
            refundsMinor = refundTotal,
            netCashReceiptsMinor = Math.subtractExact(grossReceipts, refundTotal),
            outstandingBalanceMinor = maxOf(0L, Math.subtractExact(netSales, postedReceipts))
        )
    }

    fun period(
        startEpochDay: Long,
        endEpochDayInclusive: Long,
        invoices: List<InvoiceAccountingEvent>,
        payments: List<PaymentAccountingEvent>,
        returns: List<ReturnAccountingEvent>,
        refunds: List<RefundAccountingEvent>
    ): BusinessAccountingPeriod {
        require(startEpochDay <= endEpochDayInclusive) { "Invalid reporting period" }
        fun inPeriod(day: Long) = day in startEpochDay..endEpochDayInclusive
        return BusinessAccountingPeriod(
            startEpochDay, endEpochDayInclusive,
            sumExact(invoices.filter { it.status == "POSTED" && inPeriod(it.epochDay) }.map { it.totalMinor }),
            sumExact(payments.filter { inPeriod(it.epochDay) }.map { it.amountMinor }),
            sumExact(returns.filter { inPeriod(it.epochDay) }.map { it.creditMinor }),
            sumExact(refunds.filter { inPeriod(it.epochDay) }.map { it.amountMinor })
        )
    }

    private fun sumExact(values: Iterable<Long>): Long =
        values.fold(0L) { total, value ->
            require(value >= 0L) { "Accounting event amounts cannot be negative" }
            Math.addExact(total, value)
        }
}
