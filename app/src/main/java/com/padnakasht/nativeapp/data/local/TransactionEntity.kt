package com.padnakasht.nativeapp.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import com.padnakasht.nativeapp.domain.FinancialTransaction
import com.padnakasht.nativeapp.domain.TransactionKind

@Entity(tableName = "financial_transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val kind: String,
    val category: String,
    val note: String,
    val occurredAtEpochDay: Long,
    @ColumnInfo(defaultValue = "'POSTED'") val status: String = "POSTED"
) {
    fun toDomain() = FinancialTransaction(
        id = id,
        amountMinor = amountMinor,
        kind = TransactionKind.valueOf(kind),
        category = category,
        note = note,
        occurredAtEpochDay = occurredAtEpochDay
    )
    companion object {
        fun fromDomain(item: FinancialTransaction) = TransactionEntity(
            id = item.id, amountMinor = item.amountMinor, kind = item.kind.name,
            category = item.category, note = item.note, occurredAtEpochDay = item.occurredAtEpochDay
        )
    }
}
