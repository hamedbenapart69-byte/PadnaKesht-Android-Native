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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.padnakasht.nativeapp.data.BusinessRepository
import com.padnakasht.nativeapp.data.local.InvoiceEntity
import com.padnakasht.nativeapp.data.local.InvoiceLineEntity
import com.padnakasht.nativeapp.data.local.CustomerEntity
import com.padnakasht.nativeapp.data.local.ProductEntity
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
    val scope = rememberCoroutineScope()

    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var customerSearch by remember { mutableStateOf("") }
    var productName by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("0") }
    var price by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf<Long?>(null) }
    var selectedProduct by remember { mutableStateOf<Long?>(null) }
    var quantity by remember { mutableStateOf("1") }
    var paymentInvoice by remember { mutableStateOf("") }
    var paymentAmount by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val cart = remember { mutableStateListOf<DraftLine>() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("مدیریت کسب‌وکار", style = MaterialTheme.typography.headlineSmall)
        Text("مشتریان", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(customerName, { customerName = it; message = null }, Modifier.fillMaxWidth(), label = { Text("نام مشتری") }, singleLine = true)
        OutlinedTextField(customerPhone, { customerPhone = it }, Modifier.fillMaxWidth(), label = { Text("تلفن (اختیاری)") }, singleLine = true)
        Button(
            onClick = {
                scope.launch {
                    runCatching { repository.addCustomer(customerName, customerPhone) }
                        .onSuccess { customerName = ""; customerPhone = ""; message = "مشتری ثبت شد." }
                        .onFailure { message = "ثبت مشتری ناموفق بود: ${it.message ?: "خطای نامشخص"}" }
                }
            },
            enabled = customerName.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("ثبت مشتری") }
        OutlinedTextField(customerSearch, { customerSearch = it }, Modifier.fillMaxWidth(), label = { Text("جست‌وجوی مشتری") }, singleLine = true)
        customers.filter { it.name.contains(customerSearch, ignoreCase = true) || it.phone.contains(customerSearch) }.forEach { customer ->
            TextButton(onClick = { selectedCustomer = customer.id }, modifier = Modifier.fillMaxWidth()) {
                Text("${if (selectedCustomer == customer.id) "✓ " else ""}${customer.name}  •  ${customer.phone}")
            }
        }

        Text("محصولات و موجودی", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(productName, { productName = it; message = null }, Modifier.fillMaxWidth(), label = { Text("نام محصول") }, singleLine = true)
        OutlinedTextField(sku, { sku = it; message = null }, Modifier.fillMaxWidth(), label = { Text("کد یکتای محصول") }, singleLine = true)
        OutlinedTextField(stock, { stock = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("موجودی اولیه") }, singleLine = true)
        OutlinedTextField(price, { price = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("قیمت واحد (واحد خرد پول)") }, singleLine = true)
        Button(
            onClick = {
                scope.launch {
                    runCatching { repository.addProduct(productName, sku, stock.toLong(), price.toLong()) }
                        .onSuccess { productName = ""; sku = ""; stock = "0"; price = ""; message = "محصول ثبت شد." }
                        .onFailure { message = "ثبت محصول ناموفق بود؛ نام، کد یکتا، موجودی و قیمت را بررسی کنید." }
                }
            },
            enabled = productName.isNotBlank() && sku.isNotBlank() && stock.toLongOrNull() != null && price.toLongOrNull() != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("ثبت محصول") }
        products.forEach { product ->
            TextButton(onClick = { selectedProduct = product.id }, modifier = Modifier.fillMaxWidth()) {
                Text("${if (selectedProduct == product.id) "✓ " else ""}${product.name} • کد ${product.sku} • موجودی ${product.stockQuantity} • قیمت ${product.unitPriceMinor}")
            }
        }

        Text("صدور فاکتور", style = MaterialTheme.typography.titleMedium)
        Text("مشتری و کالا را از فهرست بالا انتخاب کنید؛ سپس تعداد را به سبد اضافه کنید.")
        OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("تعداد") }, singleLine = true)
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
                Text("${line.productName} × ${line.quantity} = ${line.total}")
                TextButton(onClick = { cart.removeAt(index) }) { Text("حذف") }
            }
        }
        Text("جمع فاکتور: ${cart.fold(0L) { total, line -> Math.addExact(total, line.total) }}")
        Button(
            onClick = {
                val customerId = selectedCustomer
                if (customerId == null || cart.isEmpty()) message = "مشتری و حداقل یک قلم کالا لازم است."
                else scope.launch {
                    runCatching {
                        repository.createInvoice(
                            customerId,
                            cart.map { InvoiceLineEntity(invoiceId = 0, productId = it.productId, quantity = it.quantity, unitPriceMinor = it.unitPriceMinor, lineTotalMinor = it.total) }
                        )
                    }.onSuccess { cart.clear(); message = "فاکتور ثبت شد و موجودی به‌روزرسانی شد." }
                        .onFailure { message = "ثبت فاکتور انجام نشد؛ موجودی و اطلاعات را بررسی کنید." }
                }
            },
            enabled = selectedCustomer != null && cart.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("ثبت نهایی فاکتور") }

        Text("فاکتورها و دریافت‌ها", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(paymentInvoice, { paymentInvoice = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("شماره فاکتور (شناسه)") }, singleLine = true)
        OutlinedTextField(paymentAmount, { paymentAmount = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("مبلغ دریافت (واحد خرد پول)") }, singleLine = true)
        Button(
            onClick = {
                val invoiceId = paymentInvoice.toLongOrNull()
                val amount = paymentAmount.toLongOrNull()
                if (invoiceId == null || amount == null || amount <= 0) message = "شناسه فاکتور و مبلغ معتبر لازم است."
                else scope.launch {
                    runCatching { repository.receivePayment(invoiceId, amount) }
                        .onSuccess { paymentAmount = ""; message = "دریافت ثبت شد." }
                        .onFailure { message = "ثبت دریافت ناموفق بود؛ مانده فاکتور را بررسی کنید." }
                }
            },
            enabled = paymentInvoice.toLongOrNull()?.let { it > 0 } == true && paymentAmount.toLongOrNull()?.let { it > 0 } == true,
            modifier = Modifier.fillMaxWidth()
        ) { Text("ثبت دریافت") }
        invoices.forEach { invoice ->
            InvoiceBalanceRow(invoice, repository)
        }
        if (message != null) Text(message!!, color = if (message!!.contains("ناموفق") || message!!.contains("نشد")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun InvoiceBalanceRow(invoice: InvoiceEntity, repository: BusinessRepository) {
    var balance by remember(invoice.id) { mutableStateOf<Long?>(null) }
    LaunchedEffect(invoice.id, invoice.totalMinor, invoice.status) {
        balance = runCatching { repository.outstandingBalance(invoice.id) }.getOrNull()
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text("فاکتور #${invoice.id} • مشتری ${invoice.customerId} • جمع ${invoice.totalMinor}")
            Text("وضعیت: ${if (invoice.status == "CANCELLED") "لغو شده" else if (balance == 0L) "تسویه" else "بدهکار"} • مانده: ${balance?.toString() ?: "در حال محاسبه"}")
        }
    }
}
