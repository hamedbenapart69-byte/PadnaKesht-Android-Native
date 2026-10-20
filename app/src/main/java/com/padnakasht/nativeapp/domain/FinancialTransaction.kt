package com.padnakasht.nativeapp.domain

enum class TransactionKind { INCOME, EXPENSE }

data class FinancialTransaction(
    val id: Long = 0,
    val amountMinor: Long,
    val kind: TransactionKind,
    val category: String,
    val note: String = "",
    val occurredAtEpochDay: Long
) {
    init {
        require(amountMinor > 0) { "Amount must be positive and stored in minor currency units" }
        require(category.isNotBlank()) { "Category must not be blank" }
    }

    fun signedAmountMinor(): Long = if (kind == TransactionKind.INCOME) amountMinor else -amountMinor
}

data class FinancialSummary(val incomeMinor: Long, val expenseMinor: Long) {
    val netMinor: Long get() = incomeMinor - expenseMinor
}

object LedgerRules {
    fun summarize(items: Iterable<FinancialTransaction>): FinancialSummary {
        var income = 0L
        var expense = 0L
        for (item in items) {
            when (item.kind) {
                TransactionKind.INCOME -> income = Math.addExact(income, item.amountMinor)
                TransactionKind.EXPENSE -> expense = Math.addExact(expense, item.amountMinor)
            }
        }
        return FinancialSummary(income, expense)
    }
}
