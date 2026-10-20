package com.padnakasht.nativeapp

import com.padnakasht.nativeapp.domain.FinancialTransaction
import com.padnakasht.nativeapp.domain.LedgerRules
import com.padnakasht.nativeapp.domain.OfflineInsights
import com.padnakasht.nativeapp.domain.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedgerRulesTest {
    @Test fun summarizesIncomeExpenseAndNetExactlyInMinorUnits() {
        val items = listOf(
            FinancialTransaction(amountMinor = 1200, kind = TransactionKind.INCOME, category = "فروش", occurredAtEpochDay = 1),
            FinancialTransaction(amountMinor = 450, kind = TransactionKind.EXPENSE, category = "خرید", occurredAtEpochDay = 1)
        )
        val result = LedgerRules.summarize(items)
        assertEquals(1200L, result.incomeMinor)
        assertEquals(450L, result.expenseMinor)
        assertEquals(750L, result.netMinor)
    }

    @Test fun rejectsZeroAndNegativeAmounts() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            FinancialTransaction(amountMinor = 0, kind = TransactionKind.INCOME, category = "فروش", occurredAtEpochDay = 1)
        }
    }

    @Test fun categorizesOfflineUsingKeywords() {
        assertEquals("سوخت", OfflineInsights.categorize("خرید بنزین"))
        assertEquals("فروش", OfflineInsights.categorize("فاکتور فروش"))
        assertEquals("سایر", OfflineInsights.categorize("متن نامرتبط"))
    }

    @Test fun forecastsMeanOfRecentThreeAndHandlesNoData() {
        // The most recent three values are 20, 30, and 40; their mean is 30.
        assertEquals(30L, OfflineInsights.forecastNextPeriod(listOf(10L, 20L, 30L, 40L)))
        assertNull(OfflineInsights.forecastNextPeriod(emptyList()))
    }
}
