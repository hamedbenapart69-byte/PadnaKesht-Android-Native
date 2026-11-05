package com.padnakasht.nativeapp.domain

/**
 * A posted invoice with its monetary events already aggregated by invoice.
 * All amounts are stored in the app's smallest currency unit (rial/minor unit).
 */
data class OverdueInvoiceCandidate(
    val customerId: Long,
    val dueEpochDay: Long,
    val invoiceTotalMinor: Long,
    val returnCreditsMinor: Long,
    val receiptsMinor: Long,
    val refundsMinor: Long,
    val status: String
) {
    init {
        require(customerId > 0L)
        require(invoiceTotalMinor >= 0L)
        require(returnCreditsMinor >= 0L)
        require(receiptsMinor >= 0L)
        require(refundsMinor >= 0L)
    }
}

data class OverdueDebtSummary(
    val invoiceCount: Int,
    val customerCount: Int,
    val totalOutstandingMinor: Long
)

/** Pure accounting rules; safe to test without Android, Room, or WorkManager. */
object OverdueDebtReminderRules {
    fun summarize(
        candidates: List<OverdueInvoiceCandidate>,
        todayEpochDay: Long,
        thresholdDays: Long = 30L
    ): OverdueDebtSummary {
        require(thresholdDays >= 0L) { "Threshold must not be negative" }
        val cutoffEpochDay = Math.subtractExact(todayEpochDay, thresholdDays)
        val qualifying = candidates.mapNotNull { invoice ->
            if (invoice.status != "POSTED" || invoice.dueEpochDay >= cutoffEpochDay) {
                return@mapNotNull null
            }
            val afterReturns = Math.subtractExact(invoice.invoiceTotalMinor, invoice.returnCreditsMinor)
            val afterReceipts = Math.subtractExact(afterReturns, invoice.receiptsMinor)
            val outstanding = Math.subtractExact(afterReceipts, -invoice.refundsMinor).coerceAtLeast(0L)
            outstanding.takeIf { it > 0L }?.let { invoice.customerId to it }
        }
        var total = 0L
        qualifying.forEach { (_, amount) -> total = Math.addExact(total, amount) }
        return OverdueDebtSummary(
            invoiceCount = qualifying.size,
            customerCount = qualifying.map { it.first }.distinct().size,
            totalOutstandingMinor = total
        )
    }
}
