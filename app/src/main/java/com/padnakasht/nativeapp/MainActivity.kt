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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.padnakasht.nativeapp.data.TransactionRepository
import com.padnakasht.nativeapp.data.BusinessRepository
import com.padnakasht.nativeapp.data.local.AppDatabase
import com.padnakasht.nativeapp.security.DatabasePassphraseProvider
import com.padnakasht.nativeapp.ui.PinGate
import com.padnakasht.nativeapp.ui.BusinessManagement
import com.padnakasht.nativeapp.ui.FinancialCharts
import com.padnakasht.nativeapp.ui.formatFinancialAmount
import com.padnakasht.nativeapp.ui.normalizePersianDigits
import com.padnakasht.nativeapp.ui.theme.PadnaKeshtTheme
import com.padnakasht.nativeapp.domain.JalaliDate
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
        val factory = SupportOpenHelperFactory(passphrase)
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "padnakasht.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .openHelperFactory(factory)
            .build()
        val repository = TransactionRepository(db.transactionDao())
        val businessRepository = BusinessRepository(db.businessDao())
        setContent {
            PadnaKeshtTheme {
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

                var activeSection by remember { mutableStateOf("overview") }
                var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Card(
                                Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 22.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text("پادناکِشت", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary)
                                            Text("دفتر مالی کسب‌وکار شما", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f))
                                        }
                                        Surface(
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.13f),
                                            shape = MaterialTheme.shapes.large
                                        ) {
                                            Text("PK", modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Surface(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f), shape = MaterialTheme.shapes.small) {
                                            Text("● آفلاین و محلی", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                                        }
                                        Text(JalaliDate.format(LocalDate.now()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f))
                                    }
                                }
                            }
                        }
                        item {
                            androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                listOf("overview" to "نمای کلی", "business" to "فروش و انبار", "reports" to "گزارش‌ها").forEachIndexed { index, (section, label) ->
                                    androidx.compose.material3.SegmentedButton(
                                        selected = activeSection == section,
                                        onClick = { activeSection = section },
                                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index = index, count = 3),
                                        label = { Text(label, maxLines = 1) }
                                    )
                                }
                            }
                        }
                        if (activeSection == "overview") {
                            item {
                                Text("خلاصه مالی", style = MaterialTheme.typography.titleLarge)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFFE2F2EC))) {
                                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("درآمد ثبت‌شده", style = MaterialTheme.typography.labelLarge)
                                            Text(formatFinancialAmount(summary.incomeMinor), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFFFBEAE7))) {
                                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("هزینه ثبت‌شده", style = MaterialTheme.typography.labelLarge)
                                            Text(formatFinancialAmount(summary.expenseMinor), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                            item {
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("خالص جریان ثبت‌شده", style = MaterialTheme.typography.titleMedium)
                                        Text(formatFinancialAmount(summary.netMinor), style = MaterialTheme.typography.headlineMedium, color = if (summary.netMinor >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                        Text("بر اساس تراکنش‌های عمومی ثبت‌شده؛ فروش فاکتوری جداگانه نگهداری می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            item {
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("ثبت سریع تراکنش", style = MaterialTheme.typography.titleLarge)
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (kind == TransactionKind.INCOME) Button(onClick = { kind = TransactionKind.INCOME }, modifier = Modifier.weight(1f)) { Text("＋ درآمد") }
                                            else TextButton(onClick = { kind = TransactionKind.INCOME }, modifier = Modifier.weight(1f)) { Text("＋ درآمد") }
                                            if (kind == TransactionKind.EXPENSE) Button(onClick = { kind = TransactionKind.EXPENSE }, modifier = Modifier.weight(1f)) { Text("− هزینه") }
                                            else TextButton(onClick = { kind = TransactionKind.EXPENSE }, modifier = Modifier.weight(1f)) { Text("− هزینه") }
                                        }
                                        OutlinedTextField(
                                            value = amount, onValueChange = { amount = normalizePersianDigits(it).filter(Char::isDigit); error = null },
                                            modifier = Modifier.fillMaxWidth(), label = { Text("مبلغ (واحد خرد پول)") }, singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = category, onValueChange = { category = it; error = null },
                                            modifier = Modifier.fillMaxWidth(), label = { Text("دسته‌بندی") }, singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = note, onValueChange = { note = it },
                                            modifier = Modifier.fillMaxWidth(), label = { Text("شرح (اختیاری)") }, singleLine = true
                                        )
                                        Button(
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = {
                                                val parsed = amount.toLongOrNull()
                                                if (parsed == null || parsed <= 0L) error = "مبلغ باید عدد صحیح بزرگ‌تر از صفر باشد."
                                                else {
                                                    vm.addTransaction(parsed, kind, category.trim().ifBlank { OfflineInsights.categorize(note) }, note.trim())
                                                    amount = ""; category = ""; note = ""; error = null
                                                }
                                            }
                                        ) { Text("ذخیره تراکنش") }
                                        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            item { Text("تراکنش‌های اخیر", style = MaterialTheme.typography.titleLarge) }
                            if (transactions.isEmpty()) {
                                item {
                                    Card(Modifier.fillMaxWidth()) {
                                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                            Text("هنوز تراکنشی ثبت نشده", style = MaterialTheme.typography.titleMedium)
                                            Text("با ثبت اولین درآمد یا هزینه، خلاصه مالی اینجا نمایش داده می‌شود.", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            } else {
                                items(transactions.take(5), key = { "recent-${it.id}" }) { item ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(item.category, style = MaterialTheme.typography.titleMedium)
                                                Text(JalaliDate.format(LocalDate.ofEpochDay(item.occurredAtEpochDay)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            Text("${if (item.kind == TransactionKind.INCOME) "+" else "−"}${formatFinancialAmount(item.amountMinor)}", style = MaterialTheme.typography.titleMedium, color = if (item.kind == TransactionKind.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        } else if (activeSection == "business") {
                            item {
                                Text("فروش، مشتریان و کالاها", style = MaterialTheme.typography.titleLarge)
                                Text("ثبت‌های فاکتور و انبار در پایگاه داده محلی ذخیره می‌شوند.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            item { BusinessManagement(businessRepository) }
                        } else {
                            item { Text("گزارش و تحلیل", style = MaterialTheme.typography.titleLarge) }
                            item { FinancialCharts(transactions) }
                            item {
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("خروجی گزارش دفتر مالی", style = MaterialTheme.typography.titleMedium)
                                        Button(onClick = { pdfLauncher.launch("padnakasht-report.pdf") }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره گزارش PDF") }
                                        Button(onClick = { xlsxLauncher.launch("padnakasht-report.xlsx") }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره گزارش Excel") }
                                        if (reportMessage != null) Text(reportMessage!!, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            item { Text("تمام تراکنش‌ها", style = MaterialTheme.typography.titleLarge) }
                            if (transactions.isEmpty()) item { Text("داده‌ای برای گزارش وجود ندارد.") }
                            items(transactions, key = { "ledger-${it.id}" }) { item ->
                                Card(Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(item.category, style = MaterialTheme.typography.titleMedium)
                                            Text(JalaliDate.format(LocalDate.ofEpochDay(item.occurredAtEpochDay)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (item.note.isNotBlank()) Text(item.note)
                                        }
                                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                            Text("${if (item.kind == TransactionKind.INCOME) "+" else "−"}${formatFinancialAmount(item.amountMinor)}", color = if (item.kind == TransactionKind.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                            TextButton(onClick = { pendingDeleteId = item.id }) { Text("حذف") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (pendingDeleteId != null) {
                    AlertDialog(
                        onDismissRequest = { pendingDeleteId = null },
                        title = { Text("ابطال تراکنش؟") },
                        text = { Text("تراکنش از گزارش‌های فعال خارج می‌شود، اما برای حفظ سابقه در پایگاه داده باقی می‌ماند.") },
                        confirmButton = { TextButton(onClick = { pendingDeleteId?.let(vm::removeTransaction); pendingDeleteId = null }) { Text("ابطال تراکنش") } },
                        dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("انصراف") } }
                    )
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
