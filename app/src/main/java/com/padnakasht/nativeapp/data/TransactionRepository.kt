package com.padnakasht.nativeapp.data

import com.padnakasht.nativeapp.data.local.TransactionDao
import com.padnakasht.nativeapp.data.local.TransactionEntity
import com.padnakasht.nativeapp.domain.FinancialTransaction
import kotlinx.coroutines.flow.map

class TransactionRepository(private val dao: TransactionDao) {
    val transactions = dao.observeAll().map { rows -> rows.map { it.toDomain() } }
    suspend fun add(item: FinancialTransaction): Long = dao.insert(TransactionEntity.fromDomain(item))
    suspend fun remove(id: Long): Boolean = dao.voidById(id) == 1
    suspend fun all(): List<FinancialTransaction> = dao.getAll().map { it.toDomain() }
}
