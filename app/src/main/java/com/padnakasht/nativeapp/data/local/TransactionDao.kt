package com.padnakasht.nativeapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM financial_transactions ORDER BY occurredAtEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM financial_transactions ORDER BY occurredAtEpochDay DESC, id DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Insert
    suspend fun insert(entity: TransactionEntity): Long

    @Query("DELETE FROM financial_transactions WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
