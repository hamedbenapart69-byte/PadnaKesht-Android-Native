package com.padnakasht.nativeapp

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.padnakasht.nativeapp.data.TransactionRepository
import com.padnakasht.nativeapp.data.local.AppDatabase
import com.padnakasht.nativeapp.security.DatabasePassphraseProvider
import com.padnakasht.nativeapp.ui.PinGate
import com.padnakasht.nativeapp.ui.FinancialCharts
import com.padnakasht.nativeapp.report.FinancialReportExporter
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import com.padnakasht.nativeapp.domain.FinancialTransaction
import com.padnakasht.nativeapp.domain.LedgerRules
import com.padnakasht.nativeapp.domain.OfflineInsights
import com.padnakasht.nativeapp.domain.TransactionKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : FragmentActivity() {
    private fun isBiometricAvailable(): Boolean =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun authenticateBiometric(onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ورود امن به پادناکِشت")
            .setSubtitle("هویت خود را با اثر انگشت یا زیست‌سنجی دستگاه تأیید کنید")
            .setNegativeButtonText("استفاده از PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        System.loadLibrary("sqlcipher")
        val passphrase = DatabasePassphraseProvider.getOrCreate(applicationContext)
        val factory = SupportOpenHelperFactory(passphrase, null, true)
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "padnakasht.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .openHelperFactory(factory)
            .build()
        val repository = TransactionRepository(db.transactionDao())
        setContent {
            val darkTheme = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
                var authenticated by remember { mutableStateOf(false) }
                if (!authenticated) {
                    PinGate(
                        context = applicationContext,
                        biometricAvailable = isBiometricAvailable(),
                        onBiometricRequested = { authenticateBiometric { authenticated = true } },
                        onAuthenticated = { authenticated = true }
                    )
                } else {
                val vm: LedgerViewModel = viewModel(factory = LedgerViewModel.factory(repository))
                val transactions by vm.transactions.collectAsState()
                val summary = remember(transactions) { LedgerRules.summarize(transactions) }
                var reportMessage by remember { mutableStateOf<String?>(null) }
                val pdfLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/pdf")
                ) { uri ->
                    if (uri != null) lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { FinancialReportExporter.writePdf(contentResolver, uri, transactions) }
                        }
                        reportMessage = if (result.isSuccess) "گزارش PDF ذخیره شد." else "ساخت PDF ناموفق بود."
                    }
                }
                val xlsxLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                ) { uri ->
                    if (uri != null) lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val output = contentResolver.openOutputStream(uri) ?: error("Cannot open XLSX destination")
                                output.use { FinancialReportExporter.writeXlsx(it, transactions) }
                            }
                        }
                        reportMessage = if (result.isSuccess) "گزارش Excel ذخیره شد." else "ساخت Excel ناموفق بود."
                    }
                }
                var amount by remember { mutableStateOf("") }
                var category by remember { mutableStateOf("") }
                var note by remember { mutableStateOf("") }
                var kind by remember { mutableStateOf(TransactionKind.INCOME) }
                var error by remember { mutableStateOf<String?>(null) }
                var showCharts by remember { mutableStateOf(false) }

                Surface(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    Button(onClick = { kind = TransactionKind.INCOME }) { Text(if (kind == TransactionKind.INCOME) "✓ درآمد" else "درآمد") }
                                    Button(onClick = { kind = TransactionKind.EXPENSE }) { Text(if (kind == TransactionKind.EXPENSE) "✓ هزینه" else "هزینه") }
                                }
                                OutlinedTextField(
                                    value = amount, onValueChange = { amount = it.filter(Char::isDigit); error = null },
                                    modifier = Modifier.fillMaxWidth(), label = { Text("مبلغ به واحد خرد پول") }, singleLine = true
                                )
                                OutlinedTextField(
                                    value = category, onValueChange = { category = it; error = null },
                                    modifier = Modifier.fillMaxWidth(), label = { Text("دسته‌بندی (اختیاری)") }, singleLine = true
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
                                        } else {
                                            val finalCategory = category.trim().ifBlank { OfflineInsights.categorize(note) }
                                            vm.addTransaction(parsed, kind, finalCategory, note.trim())
                                            amount = ""; category = ""; note = ""; error = null
                                        }
                                    }
                                ) { Text("ثبت تراکنش") }
                                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { pdfLauncher.launch("padnakasht-report.pdf") }) { Text("خروجی PDF") }
                                    Button(onClick = { xlsxLauncher.launch("padnakasht-report.xlsx") }) { Text("خروجی Excel") }
                                }
                                Button(onClick = { showCharts = !showCharts }) {
                                    Text(if (showCharts) "بستن نمودارها" else "نمایش نمودارهای مالی")
                                }
                                if (reportMessage != null) Text(reportMessage!!)
                                Text("تراکنش‌ها", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                        if (showCharts) item { FinancialCharts(transactions) }
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
