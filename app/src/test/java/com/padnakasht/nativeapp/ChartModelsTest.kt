package com.padnakasht.nativeapp

import com.padnakasht.nativeapp.domain.FinancialTransaction
import com.padnakasht.nativeapp.domain.TransactionKind
import com.padnakasht.nativeapp.ui.RECENT_BAR_COUNT
import com.padnakasht.nativeapp.ui.barChartDescription
import com.padnakasht.nativeapp.ui.chartIndexAt
import com.padnakasht.nativeapp.ui.chartSlot
import com.padnakasht.nativeapp.ui.cumulativeNetSeries
import com.padnakasht.nativeapp.ui.donutChartDescription
import com.padnakasht.nativeapp.ui.formatFinancialAmount
import com.padnakasht.nativeapp.ui.formatToman
import com.padnakasht.nativeapp.ui.recentChartTransactions
import com.padnakasht.nativeapp.ui.transactionDetailText
import com.padnakasht.nativeapp.ui.trendChartDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartModelsTest {
    private fun tx(id: Long, amount: Long, kind: TransactionKind, day: Long, category: String = "دسته") =
        FinancialTransaction(id = id, amountMinor = amount, kind = kind, category = category, occurredAtEpochDay = day)

    @Test
    fun recentTransactionsAreOldestFirstWithIdAsTieBreakerAndCappedAtSeven() {
        val items = (1L..10L).map { tx(id = it, amount = it * 100, kind = TransactionKind.INCOME, day = 20_000L + it / 2) }
        val recent = recentChartTransactions(items.reversed())
        assertEquals(RECENT_BAR_COUNT, recent.size)
        assertEquals((4L..10L).toList(), recent.map { it.id })
    }

    @Test
    fun cumulativeNetUsesExactSignedAmountsInChronologicalOrder() {
        val recent = listOf(
            tx(1, 500, TransactionKind.INCOME, 1),
            tx(2, 200, TransactionKind.EXPENSE, 2),
            tx(3, 900, TransactionKind.EXPENSE, 3)
        )
        assertEquals(listOf(500L, 300L, -600L), cumulativeNetSeries(recent))
    }

    @Test
    fun tapMappingFollowsLayoutDirection() {
        assertEquals(0, chartIndexAt(10f, 700f, 7, rtl = false))
        assertEquals(6, chartIndexAt(690f, 700f, 7, rtl = false))
        assertEquals(6, chartIndexAt(10f, 700f, 7, rtl = true))
        assertEquals(0, chartIndexAt(690f, 700f, 7, rtl = true))
        assertEquals(3, chartIndexAt(350f, 700f, 7, rtl = false))
        assertEquals(3, chartIndexAt(350f, 700f, 7, rtl = true))
    }

    @Test
    fun tapsOutsideOrOnEmptyChartsAreIgnored() {
        assertNull(chartIndexAt(-1f, 700f, 7, rtl = false))
        assertNull(chartIndexAt(700f, 700f, 7, rtl = false))
        assertNull(chartIndexAt(10f, 0f, 7, rtl = false))
        assertNull(chartIndexAt(10f, 700f, 0, rtl = true))
    }

    @Test
    fun drawingSlotAndTapMappingAreInverseOfEachOther() {
        for (rtl in listOf(false, true)) {
            for (count in 1..7) {
                for (index in 0 until count) {
                    val slot = chartSlot(index, count, rtl)
                    val centerX = (slot + 0.5f) * (700f / count)
                    assertEquals("count=$count index=$index rtl=$rtl", index, chartIndexAt(centerX, 700f, count, rtl))
                }
            }
        }
    }

    @Test
    fun detailTextCarriesCategoryKindAndTomanAmount() {
        val text = transactionDetailText(tx(1, 1_500_000, TransactionKind.EXPENSE, 20_000, "بسته‌بندی"))
        assertTrue(text.contains("بسته‌بندی"))
        assertTrue(text.contains("هزینه"))
        assertTrue(text.contains(formatToman(1_500_000)))
    }

    @Test
    fun barDescriptionListsEveryDrawnBarWithItsRealAmount() {
        val items = listOf(tx(1, 500, TransactionKind.INCOME, 1, "الف"), tx(2, 200, TransactionKind.EXPENSE, 2, "ب"))
        val description = barChartDescription(items)
        assertTrue(description.contains(transactionDetailText(items[0])))
        assertTrue(description.contains(transactionDetailText(items[1])))
        assertTrue(description.contains(formatFinancialAmount(2)))
        assertEquals("نمودار ستونی: داده‌ای وجود ندارد", barChartDescription(emptyList()))
    }

    @Test
    fun donutDescriptionReportsShareOfIncomeAndExpense() {
        val description = donutChartDescription(750, 250)
        assertTrue(description.contains(formatToman(750)))
        assertTrue(description.contains(formatToman(250)))
        assertTrue(description.contains(formatFinancialAmount(75)))
        assertTrue(description.contains(formatFinancialAmount(25)))
        assertEquals("نمودار ترکیب درآمد و هزینه: داده‌ای وجود ندارد", donutChartDescription(0, 0))
    }

    @Test
    fun trendDescriptionNeedsTwoPointsAndReportsExtremes() {
        assertTrue(trendChartDescription(listOf(10L)).contains("حداقل دو تراکنش"))
        val description = trendChartDescription(listOf(100L, 400L, 250L))
        assertTrue(description.contains(formatToman(100)))
        assertTrue(description.contains(formatToman(250)))
        assertTrue(description.contains(formatToman(400)))
    }
}
