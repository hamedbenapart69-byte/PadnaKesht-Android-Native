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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.padnakasht.nativeapp.data.TransactionRepository
import com.padnakasht.nativeapp.data.local.AppDatabase
import com.padnakasht.nativeapp.security.DatabasePassphraseProvider
import com.padnakasht.nativeapp.ui.PinGate
import net.sqlcipher.database.SupportFactory
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
        System.loadLibrary("sqlcipher")
        val passphrase = DatabasePassphraseProvider.getOrCreate(applicationContext)
        val factory = SupportFactory(passphrase, null, true)
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "padnakasht.db")
            .openHelperFactory(factory)
            .build()
        val repository = TransactionRepository(db.transactionDao())
        setContent {
            MaterialTheme {
                var authenticated by remember { mutableStateOf(false) }
                if (!authenticated) {
                    PinGate(applicationContext) { authenticated = true }
                } else {
                val vm: LedgerViewModel = viewModel(factory = LedgerViewModel.factory(repository))
                val transactions by vm.transactions.collectAsState()
                val summary = remember(transactions) { LedgerRules.summarize(transactions) }
                var amount by remember { mutableStateOf("") }
                var category by remember { mutableStateOf("") }
                var note by remember { mutableStateOf("") }
                var kind by remember { mutableStateOf(TransactionKind.INCOME) }
                var error by remember { mutableStateOf<String?>(null) }

                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("پادناکِشت", style = MaterialTheme.typography.headlineMedium)
                        Text("دفتر مالی آفلاین", style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) {
                                Text("درآمد"); Text(summary.incomeMinor.toString(), style = MaterialTheme.typography.titleLarge)
                            } }
                            Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) {
                                Text("هزینه"); Text(summary.expenseMinor.toString(), style = MaterialTheme.typography.titleLarge)
                            } }
                        }
                        Text("خالص: ${summary.netMinor}", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { kind = TransactionKind.INCOME }) { Text(if (kind == TransactionKind.INCOME) "✓ درآمد" else "درآمد") }
                            Button(onClick = { kind = TransactionKind.EXPENSE }) { Text(if (kind == TransactionKind.EXPENSE) "✓ هزینه" else "هزینه") }
                        }
                        OutlinedTextField(
                            value = amount, onValueChange = { amount = it.filter(Char::isDigit); error = null },
                            modifier = Modifier.fillMaxWidth(), label = { Text("مبلغ به واحد خرد پول") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = category, onValueChange = { category = it; error = null },
                            modifier = Modifier.fillMaxWidth(), label = { Text("دسته‌بندی") }, singleLine = true
                        )
                        OutlinedTextField(
                            value = note, onValueChange = { note = it },
                            modifier = Modifier.fillMaxWidth(), label = { Text("توضیحات (اختیاری)") }, singleLine = true
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val parsed = amount.toLongOrNull()
                                if (parsed == null || parsed <= 0L) {
                                    error = "مبلغ باید عدد صحیح بزرگ‌تر از صفر باشد."
                                } else if (category.isBlank()) {
                                    error = "دسته‌بندی را وارد کنید."
                                } else {
                                    vm.addTransaction(parsed, kind, category.trim(), note.trim())
                                    amount = ""; category = ""; note = ""; error = null
                                }
                            }
                        ) { Text("ثبت تراکنش") }
                        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                        Text("تراکنش‌ها", style = MaterialTheme.typography.titleLarge)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(transactions, key = { it.id }) { item ->
                                Card(Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text("${item.category} — ${if (item.kind == TransactionKind.INCOME) "+" else "−"}${item.amountMinor}")
                                            if (item.note.isNotBlank()) Text(item.note)
                                        }
                                        TextButton(onClick = { vm.removeTransaction(item.id) }) { Text("حذف") }
                                    }
                                }
                            }
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

    fun addTransaction(amountMinor: Long, kind: TransactionKind, category: String, note: String) {
        viewModelScope.launch {
            repository.add(
                FinancialTransaction(
                    amountMinor = amountMinor,
                    kind = kind,
                    category = category,
                    note = note,
                    occurredAtEpochDay = LocalDate.now().toEpochDay()
                )
            )
        }
    }

    fun removeTransaction(id: Long) {
        viewModelScope.launch { repository.remove(id) }
    }

    companion object {
        fun factory(repository: TransactionRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LedgerViewModel(repository) as T
        }
    }
}
