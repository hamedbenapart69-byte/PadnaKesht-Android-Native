package com.padnakasht.nativeapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.padnakasht.nativeapp.domain.FinancialTransaction
import com.padnakasht.nativeapp.domain.TransactionKind
import com.padnakasht.nativeapp.ui.FinancialCharts
import com.padnakasht.nativeapp.ui.transactionDetailText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real chart composable with real model objects; no PIN, database or MainActivity involved. */
@RunWith(AndroidJUnit4::class)
class FinancialChartsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val items = listOf(
        FinancialTransaction(id = 1, amountMinor = 500_000, kind = TransactionKind.INCOME, category = "فروش عسل", occurredAtEpochDay = 20_000),
        FinancialTransaction(id = 2, amountMinor = 120_000, kind = TransactionKind.EXPENSE, category = "بسته‌بندی", occurredAtEpochDay = 20_001),
        FinancialTransaction(id = 3, amountMinor = 300_000, kind = TransactionKind.INCOME, category = "فروش شیره", occurredAtEpochDay = 20_002)
    )
    private val hint = "برای دیدن جزئیات، روی یک ستون بزنید"

    private fun show(direction: LayoutDirection, data: List<FinancialTransaction> = items) {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Column(Modifier.verticalScroll(rememberScrollState())) { FinancialCharts(data) }
                }
            }
        }
    }

    /** Taps the bar chart at [fraction] of its width, vertically centered. */
    private fun tapBarChart(fraction: Float) {
        composeRule.onNodeWithTag("bar-chart").performScrollTo()
        composeRule.onNodeWithTag("bar-chart").performTouchInput { click(Offset(width * fraction, centerY)) }
    }

    @Test
    fun emptyDataShowsHonestEmptyStateInsteadOfFakeCharts() {
        show(LayoutDirection.Ltr, emptyList())
        composeRule.onNodeWithText("هنوز داده‌ای برای تحلیل وجود ندارد").assertIsDisplayed()
    }

    @Test
    fun chartsDescribeTheirRealValuesToScreenReaders() {
        show(LayoutDirection.Ltr)
        composeRule.onNodeWithTag("bar-chart").assertContentDescriptionContains(transactionDetailText(items[0]), substring = true)
        composeRule.onNodeWithTag("bar-chart").assertContentDescriptionContains(transactionDetailText(items[2]), substring = true)
        composeRule.onNodeWithTag("donut-chart").assertContentDescriptionContains("نمودار ترکیب درآمد و هزینه", substring = true)
        composeRule.onNodeWithTag("trend-chart").assertContentDescriptionContains("نمودار روند خالص تجمعی", substring = true)
    }

    @Test
    fun tappingABarShowsItsDateCategoryAndAmountInLtr() {
        show(LayoutDirection.Ltr)
        composeRule.onNodeWithTag("bar-detail").assertTextEquals(hint)
        tapBarChart(0.5f / 3f)
        composeRule.onNodeWithTag("bar-detail").assertTextEquals(transactionDetailText(items[0]))
        tapBarChart(2.5f / 3f)
        composeRule.onNodeWithTag("bar-detail").assertTextEquals(transactionDetailText(items[2]))
    }

    @Test
    fun tappingTheSameBarAgainClearsTheSelection() {
        show(LayoutDirection.Ltr)
        tapBarChart(1.5f / 3f)
        composeRule.onNodeWithTag("bar-detail").assertTextEquals(transactionDetailText(items[1]))
        tapBarChart(1.5f / 3f)
        composeRule.onNodeWithTag("bar-detail").assertTextEquals(hint)
    }

    @Test
    fun timeFlowsRightToLeftInRtl() {
        show(LayoutDirection.Rtl)
        tapBarChart(2.5f / 3f)
        composeRule.onNodeWithTag("bar-detail").assertTextEquals(transactionDetailText(items[0]))
        tapBarChart(0.5f / 3f)
        composeRule.onNodeWithTag("bar-detail").assertTextEquals(transactionDetailText(items[2]))
    }
}
