package com.padnakasht.nativeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.padnakasht.nativeapp.data.BusinessRepository
import com.padnakasht.nativeapp.data.local.PurchaseEntity
import com.padnakasht.nativeapp.data.local.PurchaseLineEntity
import com.padnakasht.nativeapp.domain.JalaliDate
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class PurchaseDraftLine(
    val productId: Long,
    val productName: String,
    val quantity: Long,
    val unitCostMinor: Long,
    val quantityScale: Long,
    val unitLabel: String
) {
    val totalMinor: Long get() = com.padnakasht.nativeapp.domain.calculateScaledAmount(quantity, unitCostMinor, quantityScale)
}

@Composable
fun PurchaseManagement(repository: BusinessRepository) {
    val products by repository.products.collectAsState(initial = emptyList())
    val purchases by repository.purchases.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var supplierName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedProductId by remember { mutableStateOf<Long?>(null) }
    var productMenuExpanded by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf("1") }
    var unitCost by remember { mutableStateOf("") }
    var initialPayment by remember { mutableStateOf("0") }
    var message by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val cart = remember { mutableStateListOf<PurchaseDraftLine>() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        SectionHeading("ثبت خرید کالا", "ثبت تأمین‌کننده، بهای خرید، ورود موجودی و بدهی تأمین‌کننده")
        OutlinedTextField(
            supplierName,
            { supplierName = it; message = null },
            Modifier.fillMaxWidth(),
            label = { Text("نام تأمین‌کننده") },
            singleLine = true
        )
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { productMenuExpanded = true },
                modifier = Modifier.fillMaxWidth().testTag("select-purchase-product")
            ) {
                Text(products.firstOrNull { it.id == selectedProductId }?.let { "${it.name} • ${it.unitLabel}" } ?: "انتخاب کالای خریداری‌شده")
            }
            DropdownMenu(
                expanded = productMenuExpanded,
                onDismissRequest = { productMenuExpanded = false }
            ) {
                products.filter { it.isActive }.forEach { product ->
                    DropdownMenuItem(
                        text = { Text("${product.name} • موجودی ${formatScaledQuantity(product.stockQuantity, product.quantityScale)} ${product.unitLabel}") },
                        onClick = { selectedProductId = product.id; unitCost = ""; productMenuExpanded = false }
                    )
                }
            }
        }
        val selectedProduct = products.firstOrNull { it.id == selectedProductId }
        OutlinedTextField(
            quantity,
            { quantity = it; message = null },
            Modifier.fillMaxWidth(),
            label = { Text("مقدار خرید (${selectedProduct?.unitLabel ?: "واحد کالا"})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        OutlinedTextField(
            unitCost,
            { unitCost = normalizePersianDigits(it).filter(Char::isDigit); message = null },
            Modifier.fillMaxWidth(),
            label = { Text("بهای خرید هر ${selectedProduct?.unitLabel ?: "واحد"} (تومان)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        Button(
            onClick = {
                val product = products.firstOrNull { it.id == selectedProductId }
                val count = product?.let { parseScaledQuantity(quantity, it.quantityScale) }
                val cost = unitCost.toLongOrNull()
                if (product == null || count == null || count <= 0L || cost == null || cost < 0L) {
                    message = "کالا، تعداد و بهای خرید معتبر را وارد کنید."
                } else {
                    val index = cart.indexOfFirst { it.productId == product.id && it.unitCostMinor == cost }
                    if (index >= 0) {
                        val previous = cart[index]
                        cart[index] = previous.copy(quantity = Math.addExact(previous.quantity, count))
                    } else {
                        cart.add(PurchaseDraftLine(product.id, product.name, count, cost, product.quantityScale, product.unitLabel))
                    }
                    message = "قلم خرید به فهرست اضافه شد."
                }
            },
            enabled = selectedProduct != null && parseScaledQuantity(quantity, selectedProduct.quantityScale)?.let { it > 0L } == true &&
                unitCost.toLongOrNull()?.let { it >= 0L } == true && !isSaving,
            modifier = Modifier.fillMaxWidth().testTag("add-purchase-line")
        ) { Text("افزودن قلم خرید") }

        if (cart.isNotEmpty()) {
            SectionHeading("اقلام خرید", "بهای هر قلم در زمان ثبت خرید نگهداری می‌شود")
            cart.forEachIndexed { index, line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(line.productName, style = MaterialTheme.typography.titleSmall)
                        Text("${formatScaledQuantity(line.quantity, line.quantityScale)} ${line.unitLabel} × ${formatToman(line.unitCostMinor)} = ${formatToman(line.totalMinor)}")
                    }
                    TextButton(onClick = { cart.removeAt(index) }, enabled = !isSaving) { Text("حذف") }
                }
            }
        }
        val purchaseTotal = cart.fold(0L) { total, line -> Math.addExact(total, line.totalMinor) }
        val parsedInitialPayment = initialPayment.toLongOrNull()
        Text("جمع خرید: ${formatToman(purchaseTotal)}")
        OutlinedTextField(
            initialPayment,
            { initialPayment = normalizePersianDigits(it).filter(Char::isDigit); message = null },
            Modifier.fillMaxWidth(),
            label = { Text("پرداخت اولیه به تأمین‌کننده (تومان)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        OutlinedTextField(
            notes,
            { notes = it },
            Modifier.fillMaxWidth(),
            label = { Text("شرح خرید (اختیاری)") }
        )
        Button(
            onClick = {
                if (isSaving) return@Button
                val paid = parsedInitialPayment
                if (supplierName.isBlank() || cart.isEmpty() || paid == null || paid < 0L || paid > purchaseTotal) {
                    message = "نام تأمین‌کننده، حداقل یک قلم و مبلغ پرداخت اولیه معتبر لازم است."
                } else {
                    isSaving = true
                    scope.launch {
                        runCatching {
                            repository.createPurchase(
                                supplierName = supplierName,
                                lines = cart.map { line ->
                                    PurchaseLineEntity(
                                        purchaseId = 0L,
                                        productId = line.productId,
                                        quantity = line.quantity,
                                        unitCostMinor = line.unitCostMinor,
                                        lineTotalMinor = line.totalMinor,
                                        quantityScale = line.quantityScale
                                    )
                                },
                                initialPaymentMinor = paid,
                                notes = notes
                            )
                        }.onSuccess {
                            cart.clear()
                            supplierName = ""
                            notes = ""
                            initialPayment = "0"
                            quantity = "1"
                            unitCost = ""
                            selectedProductId = null
                            message = "خرید ثبت شد؛ موجودی و حساب تأمین‌کننده به‌روزرسانی شدند."
                        }.onFailure { error ->
                            message = "ثبت خرید انجام نشد: ${error.message ?: "خطای نامشخص"}"
                        }.also { isSaving = false }
                    }
                }
            },
            enabled = supplierName.isNotBlank() && cart.isNotEmpty() && parsedInitialPayment != null &&
                parsedInitialPayment >= 0L && parsedInitialPayment <= purchaseTotal && !isSaving,
            modifier = Modifier.fillMaxWidth().testTag("post-purchase")
        ) { Text(if (isSaving) "در حال ثبت…" else "ثبت نهایی خرید") }

        message?.let {
            Text(it, color = if (it.contains("نشد") || it.contains("ناموفق")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }

        SectionHeading("سوابق خرید و بدهی تأمین‌کنندگان", "خرید، پرداخت‌ها و مانده هر تأمین‌کننده جداگانه ثبت می‌شوند")
        if (purchases.isEmpty()) {
            Text("هنوز خریدی ثبت نشده است.", style = MaterialTheme.typography.bodySmall)
        } else {
            purchases.forEach { purchase -> PurchaseBalanceRow(purchase, repository) }
        }
    }
}

@Composable
private fun PurchaseBalanceRow(purchase: PurchaseEntity, repository: BusinessRepository) {
    val paid by repository.observePurchasePaymentsTotal(purchase.id).collectAsState(initial = 0L)
    val payments by repository.observePurchasePayments(purchase.id).collectAsState(initial = emptyList())
    val lines by repository.observePurchaseLines(purchase.id).collectAsState(initial = emptyList())
    val products by repository.products.collectAsState(initial = emptyList())
    val outstanding = purchase.totalMinor - paid
    val scope = rememberCoroutineScope()
    var showPaymentDialog by remember { mutableStateOf(false) }
    var paymentAmount by remember { mutableStateOf("") }
    var paymentNote by remember { mutableStateOf("") }
    var operationKey by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("خرید #${formatFinancialAmount(purchase.id)} • ${purchase.supplierName}", style = MaterialTheme.typography.titleMedium)
            Text("تاریخ: ${JalaliDate.format(LocalDate.ofEpochDay(purchase.occurredAtEpochDay))}")
            Text("مبلغ خرید: ${formatToman(purchase.totalMinor)} • پرداخت‌شده: ${formatToman(paid)}")
            Text(
                "بدهی تأمین‌کننده: ${formatToman(outstanding)}",
                color = if (outstanding > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            lines.forEach { line ->
                val productName = products.firstOrNull { it.id == line.productId }?.name ?: "کالای #${line.productId}"
                val unit = products.firstOrNull { it.id == line.productId }
                Text("${productName} • ${formatScaledQuantity(line.quantity, line.quantityScale)} ${unit?.unitLabel ?: "واحد"} × ${formatToman(line.unitCostMinor)}")
            }
            payments.take(3).forEach { payment ->
                Text("پرداخت ${formatToman(payment.amountMinor)} • ${JalaliDate.format(LocalDate.ofEpochDay(payment.paidAtEpochDay))} • ${payment.note}")
            }
            if (outstanding > 0L) {
                TextButton(
                    onClick = {
                        paymentAmount = ""
                        paymentNote = ""
                        operationKey = null
                        message = null
                        showPaymentDialog = true
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ثبت پرداخت به تأمین‌کننده") }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showPaymentDialog) {
        AlertDialog(
            onDismissRequest = { if (!saving) showPaymentDialog = false },
            title = { Text("پرداخت بدهی تأمین‌کننده") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مانده قابل پرداخت: ${formatToman(outstanding)}")
                    OutlinedTextField(
                        paymentAmount,
                        { value -> paymentAmount = normalizePersianDigits(value).filter(Char::isDigit); operationKey = null; message = null },
                        label = { Text("مبلغ پرداخت (تومان)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        paymentNote,
                        { value -> paymentNote = value; operationKey = null; message = null },
                        label = { Text("شرح پرداخت (اختیاری)") }
                    )
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving && paymentAmount.toLongOrNull()?.let { it > 0L && it <= outstanding } == true,
                    onClick = {
                        val amount = paymentAmount.toLongOrNull() ?: return@TextButton
                        if (operationKey == null) operationKey = java.util.UUID.randomUUID().toString()
                        saving = true
                        scope.launch {
                            runCatching {
                                repository.recordPurchasePayment(purchase.id, amount, paymentNote, requireNotNull(operationKey))
                            }.onSuccess {
                                showPaymentDialog = false
                                message = null
                                operationKey = null
                            }.onFailure { error ->
                                message = error.message ?: "ثبت پرداخت ناموفق بود."
                            }.also { saving = false }
                        }
                    }
                ) { Text(if (saving) "در حال ثبت…" else "ثبت پرداخت") }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { showPaymentDialog = false }) { Text("انصراف") }
            }
        )
    }
}
