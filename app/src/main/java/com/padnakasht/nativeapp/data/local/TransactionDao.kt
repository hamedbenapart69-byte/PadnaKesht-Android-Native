package com.padnakasht.nativeapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM financial_transactions WHERE status = 'POSTED' ORDER BY occurredAtEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM financial_transactions WHERE status = 'POSTED' ORDER BY occurredAtEpochDay DESC, id DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Insert
    suspend fun insert(entity: TransactionEntity): Long

    @Query("SELECT COUNT(*) FROM financial_transactions")
    suspend fun countAllRows(): Int

    @Query("UPDATE financial_transactions SET status = 'VOIDED' WHERE id = :id AND status = 'POSTED'")
    suspend fun voidById(id: Long): Int
}
