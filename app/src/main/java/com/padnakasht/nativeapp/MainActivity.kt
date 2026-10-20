package com.padnakasht.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import androidx.lifecycle.viewModelScope
import com.padnakasht.nativeapp.data.TransactionRepository
import com.padnakasht.nativeapp.data.local.AppDatabase
import com.padnakasht.nativeapp.domain.FinancialTransaction
import com.padnakasht.nativeapp.domain.LedgerRules
import com.padnakasht.nativeapp.domain.TransactionKind
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "padnakasht.db").build()
        val repository = TransactionRepository(db.transactionDao())
        setContent {
            MaterialTheme {
                val vm: LedgerViewModel = viewModel(factory = LedgerViewModel.factory(repository))
                val transactions by vm.transactions.collectAsState()
                val summary = remember(transactions) { LedgerRules.summarize(transactions) }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("پادناکِشت", style = MaterialTheme.typography.headlineMedium)
                        Text("دفتر مالی آفلاین", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) {
                                Text("درآمد"); Text(summary.incomeMinor.toString(), style = MaterialTheme.typography.titleLarge)
                            } }
                            Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) {
                                Text("هزینه"); Text(summary.expenseMinor.toString(), style = MaterialTheme.typography.titleLarge)
                            } }
                        }
                        Text("خالص: ${summary.netMinor}", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.addSample(TransactionKind.INCOME) }) { Text("نمونه درآمد +") }
                            Button(onClick = { vm.addSample(TransactionKind.EXPENSE) }) { Text("نمونه هزینه +") }
                        }
                        Text("تراکنش‌ها", style = MaterialTheme.typography.titleLarge)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(transactions, key = { it.id }) { item ->
                                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                                    Text("${item.category} — ${if (item.kind == TransactionKind.INCOME) "+" else "−"}${item.amountMinor}")
                                    if (item.note.isNotBlank()) Text(item.note)
                                } }
                            }
                        }
                    }
                }
            }
        }
    }
}

class LedgerViewModel(private val repository: TransactionRepository) : ViewModel() {
    val transactions = repository.transactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun addSample(kind: TransactionKind) {
        viewModelScope.launch {
            repository.add(FinancialTransaction(
                amountMinor = if (kind == TransactionKind.INCOME) 100_000 else 25_000,
                kind = kind, category = if (kind == TransactionKind.INCOME) "فروش" else "هزینه آزمایشی",
                note = "ثبت نمونه — قابل حذف در نسخه‌های بعدی",
                occurredAtEpochDay = LocalDate.now().toEpochDay()
            ))
        }
    }
    companion object {
        fun factory(repository: TransactionRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LedgerViewModel(repository) as T
        }
    }
}
