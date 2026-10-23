package com.padnakasht.nativeapp.ui

import com.padnakasht.nativeapp.domain.FinancialTransaction
import com.padnakasht.nativeapp.domain.JalaliDate
import com.padnakasht.nativeapp.domain.TransactionKind
import java.time.LocalDate

/**
 * Pure, UI-framework-free chart rules. Nothing here changes accounting data: it only
 * decides which stored transactions are drawn, where a touch lands, and what text a
 * screen reader announces. All amounts come from the same stored values as the ledger.
 */
const val RECENT_BAR_COUNT = 7

/** The transactions drawn by the bar and trend charts, oldest first. Ties are ordered by id. */
fun recentChartTransactions(items: List<FinancialTransaction>): List<FinancialTransaction> =
    items.sortedWith(compareBy<FinancialTransaction>({ it.occurredAtEpochDay }, { it.id }))
        .takeLast(RECENT_BAR_COUNT)

/** Running net (income minus expense) after each transaction of [recent], oldest first. */
fun cumulativeNetSeries(recent: List<FinancialTransaction>): List<Long> {
    var running = 0L
    return recent.map {
        running = Math.addExact(running, it.signedAmountMinor())
        running
    }
}

/** Horizontal slot (0 = leftmost) of chronological [index]. Persian readers see time flow right to left. */
fun chartSlot(index: Int, count: Int, rtl: Boolean): Int {
    require(count > 0 && index in 0 until count) { "Index $index is outside 0 until $count" }
    return if (rtl) count - 1 - index else index
}

/** Chronological index at horizontal position [x], or null when the touch is outside the chart. */
fun chartIndexAt(x: Float, width: Float, count: Int, rtl: Boolean): Int? {
    if (count <= 0 || width <= 0f || x < 0f || x >= width) return null
    val slot = (x / (width / count)).toInt().coerceIn(0, count - 1)
    return if (rtl) count - 1 - slot else slot
}

/** One-line Persian description: Jalali date, category, kind and amount in toman. */
fun transactionDetailText(item: FinancialTransaction): String {
    val kind = if (item.kind == TransactionKind.INCOME) "درآمد" else "هزینه"
    val date = JalaliDate.format(LocalDate.ofEpochDay(item.occurredAtEpochDay))
    return "$date • ${item.category} • $kind ${formatToman(item.amountMinor)}"
}

fun barChartDescription(items: List<FinancialTransaction>): String {
    if (items.isEmpty()) return "نمودار ستونی: داده‌ای وجود ندارد"
    val rows = items.mapIndexed { index, item ->
        "${formatFinancialAmount((index + 1).toLong())}: ${transactionDetailText(item)}"
    }
    return "نمودار ستونی ${formatFinancialAmount(items.size.toLong())} تراکنش اخیر. " + rows.joinToString("؛ ")
}

fun donutChartDescription(incomeMinor: Long, expenseMinor: Long): String {
    val total = Math.addExact(incomeMinor, expenseMinor)
    if (total <= 0L) return "نمودار ترکیب درآمد و هزینه: داده‌ای وجود ندارد"
    val incomePercent = Math.round(incomeMinor.toDouble() * 100.0 / total.toDouble())
    val expensePercent = 100L - incomePercent
    return "نمودار ترکیب درآمد و هزینه: درآمد ${formatToman(incomeMinor)} " +
        "(${formatFinancialAmount(incomePercent)} درصد) و هزینه ${formatToman(expenseMinor)} " +
        "(${formatFinancialAmount(expensePercent)} درصد)"
}

fun trendChartDescription(netSeries: List<Long>): String {
    if (netSeries.size < 2) return "نمودار روند خالص تجمعی: برای نمایش روند حداقل دو تراکنش لازم است"
    return "نمودار روند خالص تجمعی: از ${formatToman(netSeries.first())} تا ${formatToman(netSeries.last())}؛ " +
        "کمترین ${formatToman(netSeries.min())} و بیشترین ${formatToman(netSeries.max())}"
}
