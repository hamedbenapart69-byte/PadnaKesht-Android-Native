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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.padnakasht.nativeapp.ui.formatToman
import com.padnakasht.nativeapp.ui.normalizePersianDigits
import com.padnakasht.nativeapp.ui.SectionHeading
import com.padnakasht.nativeapp.ui.GlassPanel
import com.padnakasht.nativeapp.ui.PremiumBrandPanel
import com.padnakasht.nativeapp.ui.theme.PadnaKeshtTheme
import com.padnakasht.nativeapp.domain.JalaliDate
import com.padnakasht.nativeapp.report.FinancialReportExporter
import com.padnakasht.nativeapp.report.BusinessReportData
import com.padnakasht.nativeapp.report.BusinessReportRow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import com.padnakasht.nativeapp.domain.FinancialTransaction
import com.padnakasht.nativeapp.domain.LedgerRules
import com.padnakasht.nativeapp.domain.BusinessAccounting
import com.padnakasht.nativeapp.domain.InvoiceAccountingEvent
import com.padnakasht.nativeapp.domain.PaymentAccountingEvent
import com.padnakasht.nativeapp.domain.ReturnAccountingEvent
import com.padnakasht.nativeapp.domain.RefundAccountingEvent
import com.padnakasht.nativeapp.domain.OfflineInsights
import com.padnakasht.nativeapp.domain.TransactionKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import androidx.lifecycle.lifecycleScope

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
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9)
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
                val businessInvoices by businessRepository.invoices.collectAsState(initial = emptyList())
                val postedInvoices = remember(businessInvoices) { businessInvoices.filter { it.status == "POSTED" } }
                val postedInvoiceTotal = remember(postedInvoices) {
                    postedInvoices.fold(0L) { total, invoice -> Math.addExact(total, invoice.totalMinor) }
                }
                val businessCustomers by businessRepository.customers.collectAsState(initial = emptyList())
                val allPayments by businessRepository.allPayments.collectAsState(initial = emptyList())
                val allReturns by businessRepository.allSalesReturns.collectAsState(initial = emptyList())
                val allRefunds by businessRepository.allRefunds.collectAsState(initial = emptyList())
                val businessSummary = remember(businessInvoices, allPayments, allReturns, allRefunds) {
                    BusinessAccounting.summarize(
                        businessInvoices.map { InvoiceAccountingEvent(it.id, it.createdAtEpochDay, it.totalMinor, it.status) },
                        allPayments.map { PaymentAccountingEvent(it.invoiceId, it.paidAtEpochDay, it.amountMinor) },
                        allReturns.map { ReturnAccountingEvent(it.invoiceId, it.returnedAtEpochDay, it.creditAmountMinor) },
                        allRefunds.map { RefundAccountingEvent(it.invoiceId, it.refundedAtEpochDay, it.amountMinor) }
                    )
                }

                val summary = remember(transactions) { LedgerRules.summarize(transactions) }
                val businessReportRows = remember(businessInvoices, businessCustomers, allPayments, allReturns, allRefunds, transactions) {
                    val customerNames = businessCustomers.associate { it.id to it.name }
                    val rows = mutableListOf<com.padnakasht.nativeapp.report.BusinessReportRow>()
                    businessInvoices.forEach { invoice ->
                        val customer = customerNames[invoice.customerId] ?: "مشتری #${invoice.customerId}"
                        val invoicePayments = allPayments.filter { it.invoiceId == invoice.id }
                        val invoiceReturns = allReturns.filter { it.invoiceId == invoice.id }
                        val invoiceRefunds = allRefunds.filter { it.invoiceId == invoice.id }
                        val ledgerEvents = mutableListOf<Triple<com.padnakasht.nativeapp.report.BusinessReportRow, Int, Long>>()
                        val posted = invoice.status == "POSTED"
                        ledgerEvents += Triple(
                            com.padnakasht.nativeapp.report.BusinessReportRow(
                                invoice.createdAtEpochDay,
                                if (posted) "فاکتور قطعی" else "فاکتور لغوشده",
                                "فاکتور #${invoice.id}",
                                customer,
                                if (posted) invoice.totalMinor else 0L,
                                0L,
                                "تخفیف: ${invoice.discountMinor}" + if (posted) "" else "؛ مبلغ اصلی: ${invoice.totalMinor}"
                            ),
                            0,
                            if (posted) invoice.totalMinor else 0L
                        )
                        invoicePayments.forEach { payment ->
                            ledgerEvents += Triple(
                                com.padnakasht.nativeapp.report.BusinessReportRow(payment.paidAtEpochDay, "دریافتی", "رسید #${payment.id}", customer, payment.amountMinor, 0L, payment.note.ifBlank { payment.method }),
                                1,
                                if (posted) -payment.amountMinor else 0L
                            )
                        }
                        invoiceReturns.forEach { returned ->
                            ledgerEvents += Triple(
                                com.padnakasht.nativeapp.report.BusinessReportRow(returned.returnedAtEpochDay, "مرجوعی", "برگشت #${returned.id}", customer, returned.creditAmountMinor, 0L, returned.reason),
                                2,
                                if (posted) -returned.creditAmountMinor else 0L
                            )
                        }
                        invoiceRefunds.forEach { refund ->
                            ledgerEvents += Triple(
                                com.padnakasht.nativeapp.report.BusinessReportRow(refund.refundedAtEpochDay, "بازپرداخت", "بازپرداخت #${refund.id}", customer, refund.amountMinor, 0L, refund.reason),
                                3,
                                0L
                            )
                        }
                        var runningBalance = 0L
                        ledgerEvents.sortedWith(compareBy<Triple<com.padnakasht.nativeapp.report.BusinessReportRow, Int, Long>> { it.first.dateEpochDay }.thenBy { it.second }.thenBy { it.first.reference })
                            .forEach { (row, _, balanceDelta) ->
                                runningBalance = maxOf(0L, Math.addExact(runningBalance, balanceDelta))
                                rows += row.copy(balanceMinor = runningBalance)
                            }
                    }
                    transactions.forEach { transaction ->
                        rows += com.padnakasht.nativeapp.report.BusinessReportRow(
                            transaction.occurredAtEpochDay,
                            if (transaction.kind == TransactionKind.INCOME) "درآمد عمومی" else "هزینه عمومی",
                            "تراکنش #${transaction.id}",
                            transaction.category,
                            transaction.amountMinor,
                            0L,
                            transaction.note
                        )
                    }
                    rows.sortedWith(
                        compareBy<com.padnakasht.nativeapp.report.BusinessReportRow> { it.dateEpochDay }
                            .thenBy {
                                when (it.type) {
                                    "فاکتور قطعی", "فاکتور لغوشده" -> 0
                                    "دریافتی" -> 1
                                    "مرجوعی" -> 2
                                    "بازپرداخت" -> 3
                                    else -> 4
                                }
                            }
                            .thenBy { it.reference }
                    )
                }
                val businessReportData = remember(businessReportRows, businessSummary, summary, transactions) {
                    val today = LocalDate.now()
                    BusinessReportData(
                        title = "گزارش کامل فروش و دریافت‌ها",
                        startEpochDay = businessReportRows.minOfOrNull { it.dateEpochDay } ?: today.toEpochDay(),
                        endEpochDayInclusive = today.toEpochDay(),
                        invoiceSalesMinor = businessSummary.invoicedSalesMinor,
                        receiptsMinor = businessSummary.receiptsMinor,
                        returnCreditsMinor = businessSummary.returnCreditsMinor,
                        refundsMinor = businessSummary.refundsMinor,
                        outstandingMinor = businessSummary.outstandingBalanceMinor,
                        rows = businessReportRows,
                        generalIncomeMinor = summary.incomeMinor,
                        generalExpenseMinor = summary.expenseMinor
                    )
                }

                var reportMessage by remember { mutableStateOf<String?>(null) }
                var backupMessage by remember { mutableStateOf<String?>(null) }
                val backupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/octet-stream")
                ) { uri ->
                    if (uri != null) lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }
                                db.close()
                                val source = getDatabasePath("padnakasht.db")
                                val output = contentResolver.openOutputStream(uri) ?: error("Cannot open backup destination")
                                output.use { destination -> FileInputStream(source).use { it.copyTo(destination) } }
                            }
                        }
                        backupMessage = if (result.isSuccess) {
                            recreate()
                            "پشتیبان رمزگذاری‌شده ذخیره شد. این فایل فقط روی همین دستگاه و با همین نصب قابل بازیابی است."
                        } else {
                            recreate()
                            "ساخت پشتیبان ناموفق بود: ${result.exceptionOrNull()?.message ?: "خطای ناشناخته"}"
                        }
                    }
                }
                val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val tempName = "padnakasht-restore-validation.db"
                                val tempFile = getDatabasePath(tempName)
                                tempFile.parentFile?.mkdirs()
                                contentResolver.openInputStream(uri).use { input ->
                                    requireNotNull(input) { "Cannot open backup file" }
                                    FileOutputStream(tempFile).use { input.copyTo(it) }
                                }
                                val validation = Room.databaseBuilder(applicationContext, AppDatabase::class.java, tempName)
                                    .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9)
                                    .openHelperFactory(SupportOpenHelperFactory(passphrase))
                                    .build()
                                try {
                                    validation.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
                                        check(cursor.moveToFirst() && cursor.getString(0) == "ok") { "Backup database integrity check failed" }
                                    }
                                } finally {
                                    validation.close()
                                }
                                db.close()
                                val target = getDatabasePath("padnakasht.db")
                                val staged = getDatabasePath("padnakasht-restore-staged.db")
                                val previous = getDatabasePath("padnakasht-before-restore.db")
                                tempFile.copyTo(staged, overwrite = true)
                                listOf(File(target.path + "-wal"), File(target.path + "-shm")).forEach { check(!it.exists() || it.delete()) { "Could not clear database journal" } }
                                previous.delete()
                                if (target.exists()) check(target.renameTo(previous)) { "Could not preserve current database before restore" }
                                if (!staged.renameTo(target)) {
                                    if (previous.exists()) previous.renameTo(target)
                                    error("Could not atomically replace database")
                                }
                                previous.delete()
                                tempFile.delete()
                            }
                        }
                        backupMessage = if (result.isSuccess) {
                            recreate()
                            "بازیابی و بررسی سلامت پایگاه داده انجام شد."
                        } else {
                            recreate()
                            "بازیابی انجام نشد؛ پایگاه دادهٔ فعلی حفظ می‌شود. ${result.exceptionOrNull()?.message ?: "خطای ناشناخته"}"
                        }
                    }
                }
                val pdfLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/pdf")
                ) { uri ->
                    if (uri != null) lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { FinancialReportExporter.writeBusinessPdf(contentResolver, uri, businessReportData) }
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
                                output.use { FinancialReportExporter.writeBusinessXlsx(it, businessReportData) }
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

                val haptic = LocalHapticFeedback.current
                var activeSection by remember { mutableStateOf("overview") }
                var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LazyColumn(
                        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp).testTag("main-content-list"),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            com.padnakasht.nativeapp.ui.PremiumBrandPanel(
                                dateLabel = JalaliDate.format(LocalDate.now())
                            )
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("overview" to "نمای کلی", "business" to "فروش و انبار", "reports" to "گزارش‌ها").forEach { (section, label) ->
                                    androidx.compose.material3.FilterChip(
                                        selected = activeSection == section,
                                        onClick = { if (activeSection != section) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); activeSection = section } },
                                        label = { Text(label, maxLines = 1) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        if (activeSection == "overview") {
                            item {
                                SectionHeading("خلاصه مالی", "نمای کلی از درآمد، هزینه و جریان ثبت‌شده")
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFFE2F2EC))) {
                                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("درآمد ثبت‌شده", style = MaterialTheme.typography.labelLarge)
                                            Text(formatToman(summary.incomeMinor), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFFFBEAE7))) {
                                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("هزینه ثبت‌شده", style = MaterialTheme.typography.labelLarge)
                                            Text(formatToman(summary.expenseMinor), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                            item {
                                GlassPanel(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("خالص جریان ثبت‌شده", style = MaterialTheme.typography.titleMedium)
                                        Text(formatToman(summary.netMinor), style = MaterialTheme.typography.displaySmall, color = if (summary.netMinor >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                        Text("بر اساس تراکنش‌های عمومی ثبت‌شده؛ فروش فاکتوری جداگانه نگهداری می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            item {
                                GlassPanel(Modifier.fillMaxWidth()) {
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
                                            modifier = Modifier.fillMaxWidth(), label = { Text("مبلغ (تومان)") }, singleLine = true
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
                            item { SectionHeading("تراکنش‌های اخیر", "آخرین رویدادهای دفتر مالی") }
                            if (transactions.isEmpty()) {
                                item {
                                    GlassPanel(Modifier.fillMaxWidth()) {
                                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                            Text("هنوز تراکنشی ثبت نشده", style = MaterialTheme.typography.titleMedium)
                                            Text("با ثبت اولین درآمد یا هزینه، خلاصه مالی اینجا نمایش داده می‌شود.", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            } else {
                                items(transactions.take(5), key = { "recent-${it.id}" }) { item ->
                                    GlassPanel(Modifier.fillMaxWidth()) {
                                        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(item.category, style = MaterialTheme.typography.titleMedium)
                                                Text(JalaliDate.format(LocalDate.ofEpochDay(item.occurredAtEpochDay)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.bodyMedium)
                                            }
                                            Text("${if (item.kind == TransactionKind.INCOME) "+" else "−"}${formatToman(item.amountMinor)}", style = MaterialTheme.typography.titleMedium, color = if (item.kind == TransactionKind.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        } else if (activeSection == "business") {
                            item {
                                SectionHeading("فروش، مشتریان و کالاها", "مدیریت گردش روزانهٔ کسب‌وکار")
                                Text("ثبت‌های فاکتور و انبار در پایگاه داده محلی ذخیره می‌شوند.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            item { BusinessManagement(businessRepository) }
                        } else {
                            item { SectionHeading("گزارش و تحلیل", "نمایش جداگانهٔ دفتر مالی عمومی و فروش فاکتوری") }
                            item {
                                GlassPanel(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("خلاصه فروش فاکتوری", style = MaterialTheme.typography.titleMedium)
                                        if (postedInvoices.isEmpty()) {
                                            Text("هنوز فاکتور قطعی ثبت نشده است.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            Text("تعداد فاکتورهای قطعی: " + formatFinancialAmount(postedInvoices.size.toLong()))
                                            Text("جمع مبلغ فاکتورهای قطعی: " + formatToman(postedInvoiceTotal), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Text("جمع فاکتورها پس از تخفیف و پیش از اعتبار برگشت کالا است.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        androidx.compose.material3.HorizontalDivider()
                                        Text("دریافتی ثبت‌شده: " + formatToman(businessSummary.receiptsMinor))
                                        Text("اعتبار مرجوعی: " + formatToman(businessSummary.returnCreditsMinor))
                                        Text("بازپرداخت ثبت‌شده: " + formatToman(businessSummary.refundsMinor))
                                        Text("خالص دریافتی نقدی: " + formatToman(businessSummary.netCashReceiptsMinor), style = MaterialTheme.typography.titleMedium)
                                        Text("بدهی باقی‌مانده: " + formatToman(businessSummary.outstandingBalanceMinor), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                                        Text("فروش فاکتوری، دریافتی نقدی و بازپرداخت جدا محاسبه می‌شوند؛ ارقام از رویدادهای پایگاه داده می‌آیند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            item {
                                val today = LocalDate.now()
                                val monthStart = today.withDayOfMonth(1).toEpochDay()
                                val todayEpoch = today.toEpochDay()
                                fun totalFor(type: String, start: Long, end: Long): Long =
                                    businessReportRows.filter { it.type == type && it.dateEpochDay in start..end }
                                        .fold(0L) { total, row -> Math.addExact(total, row.amountMinor) }
                                val dailySales = totalFor("فاکتور قطعی", todayEpoch, todayEpoch)
                                val dailyReceipts = totalFor("دریافتی", todayEpoch, todayEpoch)
                                val dailyReturns = totalFor("مرجوعی", todayEpoch, todayEpoch)
                                val dailyRefunds = totalFor("بازپرداخت", todayEpoch, todayEpoch)
                                val monthlySales = totalFor("فاکتور قطعی", monthStart, todayEpoch)
                                val monthlyReceipts = totalFor("دریافتی", monthStart, todayEpoch)
                                val monthlyReturns = totalFor("مرجوعی", monthStart, todayEpoch)
                                val monthlyRefunds = totalFor("بازپرداخت", monthStart, todayEpoch)
                                GlassPanel(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("گزارش روزانه · ${JalaliDate.format(today)}", style = MaterialTheme.typography.titleMedium)
                                        Text("فاکتورها: ${formatToman(dailySales)}")
                                        Text("دریافتی: ${formatToman(dailyReceipts)}")
                                        Text("اعتبار مرجوعی: ${formatToman(dailyReturns)}")
                                        Text("بازپرداخت: ${formatToman(dailyRefunds)}")
                                        androidx.compose.material3.HorizontalDivider()
                                        Text("گزارش ماه جاری · از ${JalaliDate.format(today.withDayOfMonth(1))}", style = MaterialTheme.typography.titleMedium)
                                        Text("فاکتورها: ${formatToman(monthlySales)}")
                                        Text("دریافتی: ${formatToman(monthlyReceipts)}")
                                        Text("اعتبار مرجوعی: ${formatToman(monthlyReturns)}")
                                        Text("بازپرداخت: ${formatToman(monthlyRefunds)}")
                                    }
                                }
                            }

                            item { FinancialCharts(transactions) }
                            item {
                                GlassPanel(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("گزارش کامل فروش و جریان نقدی", style = MaterialTheme.typography.titleMedium)
                                        Button(onClick = { pdfLauncher.launch("padnakasht-report.pdf") }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره گزارش PDF") }
                                        Button(onClick = { xlsxLauncher.launch("padnakasht-report.xlsx") }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره گزارش Excel") }
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = { backupLauncher.launch("padnakasht-encrypted-backup.db") },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("ساخت پشتیبان رمزگذاری‌شده") }
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "application/vnd.sqlite3", "*/*")) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("بازیابی پشتیبان") }
                                        if (backupMessage != null) Text(backupMessage!!, color = MaterialTheme.colorScheme.primary)
                                        Text("خروجی شامل فاکتورها، پرداخت‌ها، مرجوعی‌ها، بازپرداخت‌ها، مانده‌ها و تاریخ شمسی است. دفتر درآمد/هزینهٔ عمومی همچنان در فهرست جداگانه نمایش داده می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (reportMessage != null) Text(reportMessage!!, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            item { SectionHeading("تمام تراکنش‌ها", "دفتر تراکنش‌های ثبت‌شده و قابل پیگیری") }
                            if (transactions.isEmpty()) item { Text("داده‌ای برای گزارش وجود ندارد.") }
                            items(transactions, key = { "ledger-${it.id}" }) { item ->
                                GlassPanel(Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(item.category, style = MaterialTheme.typography.titleMedium)
                                            Text(JalaliDate.format(LocalDate.ofEpochDay(item.occurredAtEpochDay)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (item.note.isNotBlank()) Text(item.note)
                                        }
                                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                            Text("${if (item.kind == TransactionKind.INCOME) "+" else "−"}${formatToman(item.amountMinor)}", color = if (item.kind == TransactionKind.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
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
