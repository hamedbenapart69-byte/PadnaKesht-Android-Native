package com.padnakasht.nativeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.padnakasht.nativeapp.data.BusinessRepository
import com.padnakasht.nativeapp.data.local.InvoiceEntity
import com.padnakasht.nativeapp.data.local.InvoiceLineEntity
import com.padnakasht.nativeapp.domain.JalaliDate
import com.padnakasht.nativeapp.ui.normalizePersianDigits
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class DraftLine(val productId: Long, val productName: String, val quantity: Long, val unitPriceMinor: Long) {
    val total: Long get() = Math.multiplyExact(quantity, unitPriceMinor)
}

@Composable
fun BusinessManagement(repository: BusinessRepository) {
    val customers by repository.customers.collectAsState(initial = emptyList())
    val products by repository.products.collectAsState(initial = emptyList())
    val invoices by repository.invoices.collectAsState(initial = emptyList())
    val stockMovements by repository.stockMovements.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var customerSearch by remember { mutableStateOf("") }
    var activeBusinessTab by remember { mutableStateOf("customers") }
    var productName by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var productCategory by remember { mutableStateOf("عمومی") }
    var stock by remember { mutableStateOf("0") }
    var price by remember { mutableStateOf("") }
    var lowStockThreshold by remember { mutableStateOf("2") }
    var adjustmentDelta by remember { mutableStateOf("") }
    var adjustmentReason by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf<Long?>(null) }
    var customerMenuExpanded by remember { mutableStateOf(false) }
    var productMenuExpanded by remember { mutableStateOf(false) }
    var selectedProduct by remember { mutableStateOf<Long?>(null) }
    var quantity by remember { mutableStateOf("1") }
    var invoiceDiscount by remember { mutableStateOf("0") }
    var invoiceDueDate by remember { mutableStateOf("") }
    var paymentInvoice by remember { mutableStateOf("") }
    var paymentAmount by remember { mutableStateOf("") }
    var returnInvoiceId by remember { mutableStateOf("") }
    var returnLineId by remember { mutableStateOf<Long?>(null) }
    var returnQuantity by remember { mutableStateOf("1") }
    var returnReason by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val cart = remember { mutableStateListOf<DraftLine>() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        PadnaBrandHeader(subtitle = "مدیریت فروش، موجودی و حساب مشتریان")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("customers" to "مشتریان", "inventory" to "کالاها", "invoices" to "فاکتورها").forEach { (tab, label) ->
                androidx.compose.material3.FilterChip(
                    selected = activeBusinessTab == tab,
                    onClick = { activeBusinessTab = tab },
                    label = { Text(label, maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (activeBusinessTab == "customers") {
        SectionHeading("مشتریان", "ثبت و جست‌وجوی مشتریان و مانده حساب")
        OutlinedTextField(customerName, { customerName = it; message = null }, Modifier.fillMaxWidth(), label = { Text("نام مشتری") }, singleLine = true)
        OutlinedTextField(customerPhone, { customerPhone = it }, Modifier.fillMaxWidth(), label = { Text("تلفن (اختیاری)") }, singleLine = true)
        Button(
            onClick = {
                if (isSaving) return@Button
                isSaving = true
                scope.launch {
                    runCatching { repository.addCustomer(customerName, customerPhone) }
                        .onSuccess { customerName = ""; customerPhone = ""; message = "مشتری ثبت شد." }
                        .onFailure { message = "ثبت مشتری ناموفق بود: ${it.message ?: "خطای نامشخص"}" }
                        .also { isSaving = false }
                }
            },
            enabled = customerName.isNotBlank() && !isSaving,
            modifier = Modifier.fillMaxWidth().testTag("register-customer")
        ) { Text("ثبت مشتری") }
        OutlinedTextField(customerSearch, { customerSearch = it }, Modifier.fillMaxWidth(), label = { Text("جست‌وجوی مشتری") }, singleLine = true)
        customers.filter { it.name.contains(customerSearch, ignoreCase = true) || it.phone.contains(customerSearch) }.forEach { customer ->
            CustomerBalanceRow(
                customerId = customer.id,
                name = customer.name,
                phone = customer.phone,
                selected = selectedCustomer == customer.id,
                repository = repository,
                onSelect = { selectedCustomer = customer.id }
            )
        }

        }
        if (activeBusinessTab == "inventory") {
        SectionHeading("محصولات و موجودی", "قیمت‌گذاری، سطح هشدار و موجودی فعلی")
        OutlinedTextField(productName, { productName = it; message = null }, Modifier.fillMaxWidth(), label = { Text("نام محصول") }, singleLine = true)
        OutlinedTextField(sku, { sku = it; message = null }, Modifier.fillMaxWidth(), label = { Text("کد یکتای محصول") }, singleLine = true)
        OutlinedTextField(productCategory, { productCategory = it; message = null }, Modifier.fillMaxWidth(), label = { Text("دسته‌بندی کالا") }, singleLine = true)
        OutlinedTextField(stock, { stock = normalizePersianDigits(it).filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("موجودی اولیه") }, singleLine = true)
        OutlinedTextField(price, { price = normalizePersianDigits(it).filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("قیمت واحد (واحد خرد پول)") }, singleLine = true)
        OutlinedTextField(lowStockThreshold, { lowStockThreshold = normalizePersianDigits(it).filter(Char::isDigit); message = null }, Modifier.fillMaxWidth(), label = { Text("هشدار کمبود موجودی (صفر = غیرفعال)") }, singleLine = true)
        Button(
            onClick = {
                if (isSaving) return@Button
                isSaving = true
                scope.launch {
                    runCatching { repository.addProduct(productName, sku, stock.toLong(), price.toLong(), productCategory, lowStockThreshold.toLong()) }
                        .onSuccess { productName = ""; sku = ""; productCategory = "عمومی"; stock = "0"; price = ""; lowStockThreshold = "2"; message = "محصول ثبت شد." }
                        .onFailure { message = "ثبت محصول ناموفق بود؛ نام، کد یکتا، موجودی و قیمت را بررسی کنید." }
                        .also { isSaving = false }
                }
            },
            enabled = productName.isNotBlank() && sku.isNotBlank() && stock.toLongOrNull() != null && price.toLongOrNull() != null && lowStockThreshold.toLongOrNull()?.let { it >= 0 } == true && !isSaving,
            modifier = Modifier.fillMaxWidth().testTag("register-product")
        ) { Text("ثبت محصول") }
        products.forEach { product ->
            TextButton(onClick = { selectedProduct = product.id }, modifier = Modifier.fillMaxWidth()) {
                Text("${if (selectedProduct == product.id) "✓ " else ""}${product.name} • ${product.category} • کد ${product.sku} • موجودی ${formatFinancialAmount(product.stockQuantity)} • قیمت ${formatFinancialAmount(product.unitPriceMinor)}${if (product.lowStockThreshold > 0 && product.stockQuantity <= product.lowStockThreshold) " • ⚠ موجودی کم" else ""}")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("اصلاح موجودی با ثبت سابقه", style = MaterialTheme.typography.titleMedium)
                Text("کالا را از فهرست بالا انتخاب کنید. عدد مثبت ورود و عدد منفی خروج دستی است.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    adjustmentDelta,
                    {
                        val normalized = normalizePersianDigits(it)
                        adjustmentDelta = if (normalized == "-" || normalized.all(Char::isDigit)) normalized
                        else normalized.filterIndexed { index, ch -> ch.isDigit() || (index == 0 && ch == '-') }
                    },
                    Modifier.fillMaxWidth(),
                    label = { Text("تغییر تعداد (+/-)") },
                    singleLine = true
                )
                OutlinedTextField(adjustmentReason, { adjustmentReason = it }, Modifier.fillMaxWidth(), label = { Text("علت اصلاح موجودی") }, singleLine = true)
                Button(
                    onClick = {
                        val productId = selectedProduct
                        val delta = adjustmentDelta.toLongOrNull()
                        if (productId == null || delta == null || delta == 0L || adjustmentReason.isBlank()) {
                            message = "کالا، مقدار غیرصفر و علت اصلاح را وارد کنید."
                        } else if (isSaving) {
                            message = "عملیات قبلی هنوز در حال انجام است."
                        } else {
                            isSaving = true
                            scope.launch {
                                runCatching { repository.adjustStock(productId, delta, adjustmentReason) }
                                    .onSuccess { adjustmentDelta = ""; adjustmentReason = ""; message = "اصلاح موجودی و سابقه ثبت شد." }
                                    .onFailure { message = "اصلاح موجودی انجام نشد؛ موجودی و علت را بررسی کنید." }
                                    .also { isSaving = false }
                            }
                        }
                    },
                    enabled = selectedProduct != null && adjustmentDelta.toLongOrNull()?.let { it != 0L } == true && adjustmentReason.isNotBlank() && !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ثبت اصلاح موجودی") }
            }
        }
        SectionHeading("گردش اخیر انبار", "سوابق ثبت‌شدهٔ ورود، خروج و اصلاح")
        if (stockMovements.isEmpty()) {
            Text("هنوز گردش انباری ثبت نشده است.", style = MaterialTheme.typography.bodySmall)
        } else {
            stockMovements.take(8).forEach { movement ->
                val productLabel = products.firstOrNull { it.id == movement.productId }?.name ?: "کالای #${movement.productId}"
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(productLabel, style = MaterialTheme.typography.titleSmall)
                        Text("${if (movement.quantityDelta > 0) "+" else ""}${formatFinancialAmount(movement.quantityDelta)} • ${movement.reason}")
                        Text(JalaliDate.format(LocalDate.ofEpochDay(movement.occurredAtEpochDay)), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        }
        if (activeBusinessTab == "invoices") {
        SectionHeading("صدور فاکتور", "انتخاب مشتری، افزودن اقلام و ثبت سررسید")
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
            androidx.compose.material3.OutlinedButton(
                onClick = { customerMenuExpanded = true },
                modifier = Modifier.fillMaxWidth().testTag("select-invoice-customer")
            ) {
                Text("مشتری: " + (customers.firstOrNull { it.id == selectedCustomer }?.name ?: "انتخاب مشتری"))
            }
            androidx.compose.material3.DropdownMenu(
                expanded = customerMenuExpanded,
                onDismissRequest = { customerMenuExpanded = false }
            ) {
                customers.forEach { customer ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(customer.name) },
                        onClick = { selectedCustomer = customer.id; customerMenuExpanded = false }
                    )
                }
            }
        }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth()) {
            androidx.compose.material3.OutlinedButton(
                onClick = { productMenuExpanded = true },
                modifier = Modifier.fillMaxWidth().testTag("select-invoice-product")
            ) {
                val selected = products.firstOrNull { it.id == selectedProduct }
                Text("کالا: " + (selected?.let { it.name + " • موجودی " + formatFinancialAmount(it.stockQuantity) + " • " + formatFinancialAmount(it.unitPriceMinor) } ?: "انتخاب کالا"))
            }
            androidx.compose.material3.DropdownMenu(
                expanded = productMenuExpanded,
                onDismissRequest = { productMenuExpanded = false }
            ) {
                products.forEach { product ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(product.name + " • موجودی " + formatFinancialAmount(product.stockQuantity) + " • " + formatFinancialAmount(product.unitPriceMinor)) },
                        onClick = { selectedProduct = product.id; productMenuExpanded = false }
                    )
                }
            }
        }
        OutlinedTextField(quantity, { quantity = normalizePersianDigits(it).filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("تعداد") }, singleLine = true)
        Button(
            onClick = {
                val product = products.firstOrNull { it.id == selectedProduct }
                val qty = quantity.toLongOrNull()
                if (product == null || qty == null || qty <= 0) message = "مشتری، محصول و تعداد معتبر را انتخاب کنید."
                else {
                    val existingIndex = cart.indexOfFirst { it.productId == product.id }
                    if (existingIndex >= 0) {
                        val old = cart[existingIndex]
                        cart[existingIndex] = old.copy(quantity = Math.addExact(old.quantity, qty))
                    } else cart.add(DraftLine(product.id, product.name, qty, product.unitPriceMinor))
                    message = "قلم به سبد فاکتور اضافه شد."
                }
            },
            enabled = selectedProduct != null && quantity.toLongOrNull()?.let { it > 0 } == true,
            modifier = Modifier.fillMaxWidth()
        ) { Text("افزودن قلم به فاکتور") }
        cart.forEachIndexed { index, line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${line.productName} × ${formatFinancialAmount(line.quantity)} = ${formatFinancialAmount(line.total)}")
                TextButton(onClick = { cart.removeAt(index) }) { Text("حذف") }
            }
        }
        val invoiceSubtotal = cart.fold(0L) { total, line -> Math.addExact(total, line.total) }
        val parsedDiscount = invoiceDiscount.toLongOrNull()
        val parsedDueDate = invoiceDueDate.trim().takeIf { it.isNotEmpty() }?.let { runCatching { JalaliDate.parse(it) }.getOrNull() }
        OutlinedTextField(invoiceDiscount, { invoiceDiscount = normalizePersianDigits(it).filter(Char::isDigit); message = null }, Modifier.fillMaxWidth(), label = { Text("تخفیف کل (واحد خرد پول)") }, singleLine = true)
        OutlinedTextField(invoiceDueDate, { invoiceDueDate = it; message = null }, Modifier.fillMaxWidth(), label = { Text("سررسید اختیاری (شمسی ۱۴۰۵/۰۷/۲۹)") }, singleLine = true)
        Text("جمع قبل از تخفیف: ${formatFinancialAmount(invoiceSubtotal)} • تخفیف: ${formatFinancialAmount(parsedDiscount ?: 0L)} • مبلغ نهایی: ${if (parsedDiscount != null && parsedDiscount in 0..invoiceSubtotal) formatFinancialAmount(invoiceSubtotal - parsedDiscount) else "نامعتبر"}")
        Button(
            onClick = {
                val customerId = selectedCustomer
                if (customerId == null || cart.isEmpty()) message = "مشتری و حداقل یک قلم کالا لازم است."
                else if (isSaving) message = "عملیات قبلی هنوز در حال انجام است."
                else if (parsedDiscount == null || parsedDiscount < 0 || parsedDiscount > invoiceSubtotal) message = "تخفیف باید بین صفر و جمع فاکتور باشد."
                else if (invoiceDueDate.isNotBlank() && parsedDueDate == null) message = "سررسید را با قالب سال/ماه/روز شمسی وارد کنید؛ برای نمونه ۱۴۰۵/۰۷/۲۹."
                else {
                    isSaving = true
                    scope.launch {
                    runCatching {
                        repository.createInvoice(
                            customerId,
                            cart.map { InvoiceLineEntity(invoiceId = 0, productId = it.productId, quantity = it.quantity, unitPriceMinor = it.unitPriceMinor, lineTotalMinor = it.total) },
                            discountMinor = parsedDiscount,
                            dueAtEpochDay = parsedDueDate?.toEpochDay()
                        )
                    }.onSuccess { cart.clear(); invoiceDiscount = "0"; invoiceDueDate = ""; message = "فاکتور ثبت شد و موجودی به‌روزرسانی شد." }
                        .onFailure { message = "ثبت فاکتور انجام نشد؛ موجودی و اطلاعات را بررسی کنید." }
                        .also { isSaving = false }
                    }
                }
            },
            enabled = selectedCustomer != null && cart.isNotEmpty() && !isSaving &&
                parsedDiscount != null && parsedDiscount >= 0 && parsedDiscount <= invoiceSubtotal &&
                (invoiceDueDate.isBlank() || parsedDueDate != null),
            modifier = Modifier.fillMaxWidth()
        ) { Text("ثبت نهایی فاکتور") }

        SectionHeading("فاکتورها و دریافت‌ها", "مانده، پرداخت‌های ثبت‌شده و وضعیت سررسید")
        OutlinedTextField(paymentInvoice, { paymentInvoice = normalizePersianDigits(it).filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("شماره فاکتور (شناسه)") }, singleLine = true)
        OutlinedTextField(paymentAmount, { paymentAmount = normalizePersianDigits(it).filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("مبلغ دریافت (واحد خرد پول)") }, singleLine = true)
        Button(
            onClick = {
                val invoiceId = paymentInvoice.toLongOrNull()
                val amount = paymentAmount.toLongOrNull()
                if (invoiceId == null || amount == null || amount <= 0) message = "شناسه فاکتور و مبلغ معتبر لازم است."
                else if (isSaving) message = "عملیات قبلی هنوز در حال انجام است."
                else {
                    isSaving = true
                    scope.launch {
                        runCatching { repository.receivePayment(invoiceId, amount) }
                            .onSuccess { paymentAmount = ""; message = "دریافت ثبت شد." }
                            .onFailure { message = "ثبت دریافت ناموفق بود؛ مانده فاکتور را بررسی کنید." }
                            .also { isSaving = false }
                    }
                }
            },
            enabled = paymentInvoice.toLongOrNull()?.let { it > 0 } == true && paymentAmount.toLongOrNull()?.let { it > 0 } == true && !isSaving,
            modifier = Modifier.fillMaxWidth()
        ) { Text("ثبت دریافت") }
        invoices.forEach { invoice ->
            InvoiceBalanceRow(invoice, repository)
        }

        SectionHeading("ثبت برگشت کالا", "ثبت مستقل برگشت و محاسبهٔ اعتبار مشتری")
        Text("برگشت به‌صورت مستقل ثبت می‌شود؛ موجودی افزایش می‌یابد و اعتبار مشتری از مانده فاکتور کسر می‌شود.")
        OutlinedTextField(
            returnInvoiceId,
            { returnInvoiceId = normalizePersianDigits(it).filter(Char::isDigit); returnLineId = null },
            Modifier.fillMaxWidth(),
            label = { Text("شناسه فاکتور برگشت") },
            singleLine = true
        )
        val selectedReturnInvoice = invoices.firstOrNull { it.id == returnInvoiceId.toLongOrNull() }
        if (selectedReturnInvoice != null) {
            ReturnLinePicker(
                invoiceId = selectedReturnInvoice.id,
                repository = repository,
                products = products,
                selectedLineId = returnLineId,
                onSelect = { returnLineId = it }
            )
        }
        OutlinedTextField(returnQuantity, { returnQuantity = normalizePersianDigits(it).filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("تعداد برگشتی") }, singleLine = true)
        OutlinedTextField(returnReason, { returnReason = it; message = null }, Modifier.fillMaxWidth(), label = { Text("علت برگشت") }, singleLine = true)
        Button(
            onClick = {
                val invoiceId = returnInvoiceId.toLongOrNull()
                val lineId = returnLineId
                val qty = returnQuantity.toLongOrNull()
                if (invoiceId == null || lineId == null || qty == null || qty <= 0 || returnReason.isBlank()) {
                    message = "فاکتور، قلم، تعداد معتبر و علت برگشت را وارد کنید."
                } else if (isSaving) {
                    message = "عملیات قبلی هنوز در حال انجام است."
                } else {
                    isSaving = true
                    scope.launch {
                        runCatching { repository.recordReturn(invoiceId, lineId, qty, returnReason) }
                            .onSuccess {
                                returnQuantity = "1"
                                returnReason = ""
                                returnLineId = null
                                message = "برگشت ثبت شد؛ موجودی و اعتبار مشتری به‌روزرسانی شد."
                            }
                            .onFailure { message = "برگشت ثبت نشد؛ تعداد باقی‌مانده، فاکتور و علت را بررسی کنید." }
                            .also { isSaving = false }
                    }
                }
            },
            enabled = selectedReturnInvoice != null && returnLineId != null && returnQuantity.toLongOrNull()?.let { it > 0 } == true && returnReason.isNotBlank() && !isSaving,
            modifier = Modifier.fillMaxWidth()
        ) { Text("ثبت برگشت کالا") }
        }
        if (message != null) Text(message!!, color = if (message!!.contains("ناموفق") || message!!.contains("نشد")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun InvoiceBalanceRow(invoice: InvoiceEntity, repository: BusinessRepository) {
    val paid by repository.observePaymentsTotal(invoice.id).collectAsState(initial = 0L)
    val payments by repository.observePayments(invoice.id).collectAsState(initial = emptyList())
    val returns by repository.observeSalesReturns(invoice.id).collectAsState(initial = emptyList())
    val balance = invoice.totalMinor - paid - returns.sumOf { it.creditAmountMinor }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text("فاکتور #${formatFinancialAmount(invoice.id)} • مشتری ${formatFinancialAmount(invoice.customerId)} • مبلغ نهایی ${formatFinancialAmount(invoice.totalMinor)}")
            if (invoice.discountMinor > 0L) Text("تخفیف: ${formatFinancialAmount(invoice.discountMinor)}")
            invoice.dueAtEpochDay?.let { Text("سررسید: ${JalaliDate.format(LocalDate.ofEpochDay(it))}") }
            val overdue = invoice.status == "POSTED" && balance > 0L && invoice.dueAtEpochDay?.let { it < LocalDate.now().toEpochDay() } == true
            val statusLabel = when {
                invoice.status == "CANCELLED" -> "لغو شده"
                balance < 0L -> "بستانکار مشتری"
                overdue -> "سررسیدگذشته"
                balance == 0L -> "تسویه"
                else -> "بدهکار"
            }
            Text("وضعیت: $statusLabel • ${formatFinancialAmount(if (invoice.status == "CANCELLED") 0L else kotlin.math.abs(balance))}", color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            if (returns.isNotEmpty()) {
                Text("سوابق برگشت کالا", style = MaterialTheme.typography.labelLarge)
                returns.forEach { returned ->
                    Text("برگشت ${formatFinancialAmount(returned.quantity)} عدد • اعتبار ${formatFinancialAmount(returned.creditAmountMinor)} • ${returned.reason}")
                }
            }
            if (payments.isNotEmpty()) {
                Text("تاریخچه دریافت‌ها", style = MaterialTheme.typography.labelLarge)
                payments.forEach { payment ->
                    Text("دریافت ${formatFinancialAmount(payment.amountMinor)} • روز ثبت ${JalaliDate.format(LocalDate.ofEpochDay(payment.paidAtEpochDay))} • ${payment.method}")
                }
            }
        }
    }
}


@Composable
private fun ReturnLinePicker(
    invoiceId: Long,
    repository: BusinessRepository,
    products: List<com.padnakasht.nativeapp.data.local.ProductEntity>,
    selectedLineId: Long?,
    onSelect: (Long) -> Unit
) {
    val lines by repository.observeInvoiceLines(invoiceId).collectAsState(initial = emptyList())
    Text("قلم فاکتور را انتخاب کنید", style = MaterialTheme.typography.labelLarge)
    if (lines.isEmpty()) {
        Text("برای این فاکتور قلمی پیدا نشد.", style = MaterialTheme.typography.bodySmall)
    } else {
        lines.forEach { line ->
            val productName = products.firstOrNull { it.id == line.productId }?.name ?: "کالای #${line.productId}"
            TextButton(onClick = { onSelect(line.id) }, modifier = Modifier.fillMaxWidth()) {
                Text("${if (selectedLineId == line.id) "✓ " else ""}$productName • فروخته‌شده: ${formatFinancialAmount(line.quantity)} • شناسه قلم: ${formatFinancialAmount(line.id)}")
            }
        }
    }
}

@Composable
private fun CustomerBalanceRow(
    customerId: Long,
    name: String,
    phone: String,
    selected: Boolean,
    repository: BusinessRepository,
    onSelect: () -> Unit
) {
    val balance by repository.observeCustomerBalance(customerId).collectAsState(initial = 0L)
    TextButton(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
        Text("${if (selected) "✓ " else ""}${name} • ${phone} • مانده حساب: ${formatFinancialAmount(balance)}")
    }
}
