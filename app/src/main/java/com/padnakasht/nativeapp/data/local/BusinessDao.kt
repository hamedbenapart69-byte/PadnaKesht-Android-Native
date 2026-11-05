package com.padnakasht.nativeapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode

@Dao
abstract class BusinessDao {
    @Query("SELECT * FROM invoices ORDER BY createdAtEpochDay DESC, id DESC")
    abstract fun observeInvoices(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE")
    abstract fun observeCustomers(): Flow<List<CustomerEntity>>

    @Insert
    abstract suspend fun insertCustomer(customer: CustomerEntity): Long

    @Query("SELECT * FROM customers WHERE id = :customerId LIMIT 1")
    abstract suspend fun getCustomer(customerId: Long): CustomerEntity?

    @Query("UPDATE customers SET name = :name, phone = :phone, notes = :notes WHERE id = :customerId")
    abstract suspend fun updateCustomerDetails(customerId: Long, name: String, phone: String, notes: String): Int

    @Query("UPDATE customers SET isActive = :isActive WHERE id = :customerId")
    abstract suspend fun setCustomerActive(customerId: Long, isActive: Boolean): Int

    @Insert
    abstract suspend fun insertCustomerBalanceAdjustment(adjustment: CustomerBalanceAdjustmentEntity): Long

    @Query("""
        SELECT
            COALESCE((
                SELECT SUM(
                    CASE WHEN invoices.status = 'POSTED' THEN
                        invoices.totalMinor
                        - (SELECT COALESCE(SUM(payments.amountMinor), 0) FROM payments WHERE payments.invoiceId = invoices.id)
                        - (SELECT COALESCE(SUM(sales_returns.creditAmountMinor), 0) FROM sales_returns WHERE sales_returns.invoiceId = invoices.id)
                        + (SELECT COALESCE(SUM(payment_refunds.amountMinor), 0) FROM payment_refunds WHERE payment_refunds.invoiceId = invoices.id)
                    ELSE 0 END
                )
                FROM invoices WHERE invoices.customerId = :customerId
            ), 0)
            + COALESCE((
                SELECT SUM(amountDeltaMinor)
                FROM customer_balance_adjustments
                WHERE customerId = :customerId
            ), 0)
    """)
    abstract fun observeCustomerBalance(customerId: Long): Flow<Long>

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    abstract fun observeProducts(): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertProduct(product: ProductEntity): Long

    @Query("UPDATE products SET stockQuantity = stockQuantity - :quantity WHERE id = :productId AND stockQuantity >= :quantity AND isActive = 1")
    abstract suspend fun decreaseStockIfAvailable(productId: Long, quantity: Long): Int

    @Query("UPDATE products SET name = :name, sku = :sku, unitPriceMinor = :unitPriceMinor, category = :category, lowStockThreshold = :lowStockThreshold WHERE id = :productId")
    abstract suspend fun updateProductDetails(productId: Long, name: String, sku: String, unitPriceMinor: Long, category: String, lowStockThreshold: Long): Int

    @Query("UPDATE products SET isActive = :isActive WHERE id = :productId")
    abstract suspend fun setProductActive(productId: Long, isActive: Boolean): Int

    @Query("UPDATE products SET stockQuantity = stockQuantity + :quantity WHERE id = :productId")
    abstract suspend fun increaseStock(productId: Long, quantity: Long): Int

    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    abstract suspend fun getProduct(productId: Long): ProductEntity?

    @Query("SELECT COUNT(*) FROM invoices")
    abstract suspend fun invoiceCount(): Int

    @Query("SELECT * FROM invoices WHERE id = :invoiceId LIMIT 1")
    abstract suspend fun getInvoice(invoiceId: Long): InvoiceEntity?

    @Query("SELECT * FROM invoice_lines WHERE invoiceId = :invoiceId ORDER BY id")
    abstract suspend fun getInvoiceLines(invoiceId: Long): List<InvoiceLineEntity>

    @Query("SELECT * FROM invoice_lines WHERE invoiceId = :invoiceId ORDER BY id")
    abstract fun observeInvoiceLines(invoiceId: Long): Flow<List<InvoiceLineEntity>>

    @Query("SELECT * FROM invoice_lines WHERE id = :lineId LIMIT 1")
    abstract suspend fun getInvoiceLine(lineId: Long): InvoiceLineEntity?

    @Query("SELECT COALESCE(SUM(lineTotalMinor), 0) FROM invoice_lines WHERE invoiceId = :invoiceId")
    abstract suspend fun getInvoiceSubtotal(invoiceId: Long): Long

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM sales_returns WHERE invoiceLineId = :lineId")
    abstract suspend fun getReturnedQuantity(lineId: Long): Long

    @Query("SELECT COALESCE(SUM(creditAmountMinor), 0) FROM sales_returns WHERE invoiceLineId = :lineId")
    abstract suspend fun getLineCreditTotal(lineId: Long): Long

    @Query("SELECT COALESCE(SUM(creditAmountMinor), 0) FROM sales_returns WHERE invoiceId = :invoiceId")
    abstract suspend fun getReturnsTotal(invoiceId: Long): Long

    @Query("SELECT * FROM sales_returns WHERE invoiceId = :invoiceId ORDER BY returnedAtEpochDay DESC, id DESC")
    abstract fun observeSalesReturns(invoiceId: Long): Flow<List<SalesReturnEntity>>

    @Insert
    abstract suspend fun insertSalesReturn(salesReturn: SalesReturnEntity): Long

    @Insert
    abstract suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Insert
    abstract suspend fun insertInvoiceLine(line: InvoiceLineEntity)

    @Insert
    abstract suspend fun insertPayment(payment: PaymentEntity): Long

    @Insert
    abstract suspend fun insertStockMovement(movement: StockMovementEntity): Long

    @Query("SELECT * FROM stock_movements ORDER BY occurredAtEpochDay DESC, id DESC LIMIT 20")
    abstract fun observeStockMovements(): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM purchases ORDER BY occurredAtEpochDay DESC, id DESC")
    abstract fun observePurchases(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchase_lines WHERE purchaseId = :purchaseId ORDER BY id")
    abstract fun observePurchaseLines(purchaseId: Long): Flow<List<PurchaseLineEntity>>

    @Query("SELECT * FROM purchase_payments WHERE purchaseId = :purchaseId ORDER BY paidAtEpochDay DESC, id DESC")
    abstract fun observePurchasePayments(purchaseId: Long): Flow<List<PurchasePaymentEntity>>

    @Query("SELECT * FROM purchase_payments ORDER BY paidAtEpochDay DESC, id DESC")
    abstract fun observeAllPurchasePayments(): Flow<List<PurchasePaymentEntity>>

    @Insert
    abstract suspend fun insertPurchase(purchase: PurchaseEntity): Long

    @Insert
    abstract suspend fun insertPurchaseLine(line: PurchaseLineEntity): Long

    @Insert
    abstract suspend fun insertPurchasePayment(payment: PurchasePaymentEntity): Long

    @Query("SELECT * FROM purchases WHERE id = :purchaseId LIMIT 1")
    abstract suspend fun getPurchase(purchaseId: Long): PurchaseEntity?

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM purchase_payments WHERE purchaseId = :purchaseId")
    abstract suspend fun getPurchasePaymentsTotal(purchaseId: Long): Long

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM purchase_payments WHERE purchaseId = :purchaseId")
    abstract fun observePurchasePaymentsTotal(purchaseId: Long): Flow<Long>

    @Query("SELECT * FROM purchase_payments WHERE idempotencyKey = :key LIMIT 1")
    abstract suspend fun getPurchasePaymentByIdempotencyKey(key: String): PurchasePaymentEntity?

    @Query("UPDATE products SET stockQuantity = stockQuantity + :quantity WHERE id = :productId AND isActive = 1")
    abstract suspend fun increaseStockForPurchase(productId: Long, quantity: Long): Int

    @Query("SELECT * FROM stock_movements ORDER BY occurredAtEpochDay DESC, id DESC")
    abstract suspend fun getStockMovements(): List<StockMovementEntity>

    @Transaction
    open suspend fun adjustStock(productId: Long, quantityDelta: Long, reason: String, occurredAtEpochDay: Long): Boolean {
        require(quantityDelta != 0L) { "Stock adjustment must not be zero" }
        require(reason.isNotBlank()) { "Stock adjustment reason is required" }
        if (getProduct(productId) == null) return false
        val updated = if (quantityDelta < 0L) {
            decreaseStockIfAvailable(productId, Math.negateExact(quantityDelta))
        } else {
            increaseStock(productId, quantityDelta)
        }
        check(updated == 1) { "Insufficient stock or product not found" }
        insertStockMovement(StockMovementEntity(
            productId = productId,
            quantityDelta = quantityDelta,
            reason = reason.trim(),
            occurredAtEpochDay = occurredAtEpochDay
        ))
        return true
    }


    @Transaction
    open suspend fun postPurchase(
        purchase: PurchaseEntity,
        lines: List<PurchaseLineEntity>,
        initialPaymentMinor: Long = 0L
    ): Long {
        require(purchase.supplierName.isNotBlank()) { "Supplier name is required" }
        require(lines.isNotEmpty()) { "A purchase must contain at least one line" }
        require(initialPaymentMinor >= 0L) { "Initial payment cannot be negative" }
        var total = 0L
        lines.forEach { line ->
            require(line.quantity > 0L) { "Purchase quantity must be positive" }
            require(line.unitCostMinor >= 0L) { "Unit cost cannot be negative" }
            val lineTotal = Math.multiplyExact(line.quantity, line.unitCostMinor)
            require(line.lineTotalMinor == lineTotal) { "Purchase line total does not match quantity × unit cost" }
            total = Math.addExact(total, lineTotal)
        }
        require(initialPaymentMinor <= total) { "Initial payment cannot exceed purchase total" }
        val purchaseId = insertPurchase(purchase.copy(totalMinor = total, supplierName = purchase.supplierName.trim()))
        lines.forEach { line ->
            val product = getProduct(line.productId) ?: error("Product not found")
            require(product.isActive) { "Cannot purchase into an archived product" }
            check(increaseStockForPurchase(line.productId, line.quantity) == 1) {
                "Could not increase stock for purchase; purchase posting rolled back"
            }
            insertPurchaseLine(line.copy(id = 0L, purchaseId = purchaseId))
            insertStockMovement(
                StockMovementEntity(
                    productId = line.productId,
                    quantityDelta = line.quantity,
                    reason = "خرید #${purchaseId} از ${purchase.supplierName.trim()}",
                    occurredAtEpochDay = purchase.occurredAtEpochDay
                )
            )
        }
        if (initialPaymentMinor > 0L) {
            insertPurchasePayment(
                PurchasePaymentEntity(
                    purchaseId = purchaseId,
                    amountMinor = initialPaymentMinor,
                    paidAtEpochDay = purchase.occurredAtEpochDay,
                    note = "پرداخت اولیه خرید",
                    idempotencyKey = "purchase-initial-${purchaseId}"
                )
            )
        }
        return purchaseId
    }

    @Transaction
    open suspend fun recordPurchasePayment(payment: PurchasePaymentEntity): Long {
        require(payment.amountMinor > 0L) { "Purchase payment must be positive" }
        val key = payment.idempotencyKey.trim()
        require(key.isNotEmpty()) { "Purchase payment idempotency key is required" }
        val previous = getPurchasePaymentByIdempotencyKey(key)
        if (previous != null) {
            require(
                previous.purchaseId == payment.purchaseId &&
                    previous.amountMinor == payment.amountMinor &&
                    previous.method == payment.method &&
                    previous.note == payment.note
            ) { "Idempotency key was already used for a different purchase payment" }
            return previous.id
        }
        val purchase = getPurchase(payment.purchaseId) ?: error("Purchase not found")
        require(purchase.status == "POSTED") { "Cannot pay a cancelled purchase" }
        val paid = getPurchasePaymentsTotal(payment.purchaseId)
        require(Math.addExact(paid, payment.amountMinor) <= purchase.totalMinor) {
            "Payment exceeds the outstanding supplier balance"
        }
        return insertPurchasePayment(payment.copy(idempotencyKey = key, note = payment.note.trim()))
    }

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM payments WHERE invoiceId = :invoiceId")
    abstract suspend fun getPaymentsTotal(invoiceId: Long): Long

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM payments WHERE invoiceId = :invoiceId")
    abstract fun observePaymentsTotal(invoiceId: Long): Flow<Long>

    @Query("SELECT * FROM payments ORDER BY paidAtEpochDay DESC, id DESC")
    abstract fun observeAllPayments(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM sales_returns ORDER BY returnedAtEpochDay DESC, id DESC")
    abstract fun observeAllSalesReturns(): Flow<List<SalesReturnEntity>>

    @Query("SELECT * FROM payment_refunds ORDER BY refundedAtEpochDay DESC, id DESC")
    abstract fun observeAllRefunds(): Flow<List<PaymentRefundEntity>>

    @Query("SELECT * FROM payments WHERE invoiceId = :invoiceId ORDER BY paidAtEpochDay DESC, id DESC")
    abstract fun observePayments(invoiceId: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE invoiceId = :invoiceId ORDER BY id")
    abstract suspend fun getPaymentsForInvoice(invoiceId: Long): List<PaymentEntity>

    @Insert
    abstract suspend fun insertPaymentRefund(refund: PaymentRefundEntity): Long

    @Query("SELECT * FROM payment_refunds WHERE invoiceId = :invoiceId ORDER BY refundedAtEpochDay DESC, id DESC")
    abstract fun observeRefunds(invoiceId: Long): Flow<List<PaymentRefundEntity>>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM payment_refunds WHERE invoiceId = :invoiceId")
    abstract suspend fun getRefundsTotal(invoiceId: Long): Long

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM payment_refunds WHERE paymentId = :paymentId")
    abstract suspend fun getRefundsTotalForPayment(paymentId: Long): Long

    @Query("SELECT * FROM payment_refunds WHERE idempotencyKey = :key LIMIT 1")
    abstract suspend fun getRefundByIdempotencyKey(key: String): PaymentRefundEntity?

    @Query("SELECT * FROM payments WHERE id = :paymentId LIMIT 1")
    abstract suspend fun getPayment(paymentId: Long): PaymentEntity?

    @Query("SELECT * FROM payments WHERE idempotencyKey = :key LIMIT 1")
    abstract suspend fun getPaymentByIdempotencyKey(key: String): PaymentEntity?

    @Query("UPDATE invoices SET status = 'CANCELLED' WHERE id = :invoiceId AND status = 'POSTED'")
    abstract suspend fun markInvoiceCancelled(invoiceId: Long): Int

    @Transaction
    open suspend fun postInvoice(invoice: InvoiceEntity, lines: List<InvoiceLineEntity>): Long {
        require(lines.isNotEmpty()) { "An invoice must contain at least one line" }
        var total = 0L
        lines.forEach { line ->
            require(line.quantity > 0) { "Quantity must be positive" }
            require(line.unitPriceMinor >= 0) { "Unit price must not be negative" }
            val lineTotal = Math.multiplyExact(line.quantity, line.unitPriceMinor)
            require(line.lineTotalMinor == lineTotal) { "Line total does not match quantity × unit price" }
            total = Math.addExact(total, lineTotal)
        }
        require(invoice.discountMinor >= 0L) { "Discount cannot be negative" }
        require(invoice.discountMinor <= total) { "Discount cannot exceed invoice subtotal" }
        val netTotal = Math.subtractExact(total, invoice.discountMinor)
        val invoiceId = insertInvoice(invoice.copy(totalMinor = netTotal))
        for (line in lines) {
            check(decreaseStockIfAvailable(line.productId, line.quantity) == 1) {
                "Insufficient stock or product not found; invoice posting rolled back"
            }
            insertInvoiceLine(line.copy(id = 0, invoiceId = invoiceId))
            insertStockMovement(StockMovementEntity(
                productId = line.productId,
                quantityDelta = Math.negateExact(line.quantity),
                reason = "فروش فاکتور #$invoiceId",
                occurredAtEpochDay = invoice.createdAtEpochDay,
                invoiceId = invoiceId
            ))
        }
        return invoiceId
    }

    @Transaction
    open suspend fun recordPayment(payment: PaymentEntity): Long {
        require(payment.amountMinor > 0)
        val key = payment.idempotencyKey?.trim()?.also { require(it.isNotEmpty()) { "Idempotency key cannot be blank" } }
        if (key != null) {
            val previous = getPaymentByIdempotencyKey(key)
            if (previous != null) {
                require(previous.invoiceId == payment.invoiceId && previous.amountMinor == payment.amountMinor && previous.method == payment.method && previous.note == payment.note) {
                    "Idempotency key was already used for a different payment"
                }
                return previous.id
            }
        }
        val invoice = getInvoice(payment.invoiceId) ?: error("Invoice not found")
        require(invoice.status == "POSTED") { "Cannot pay a cancelled invoice" }
        // Refunds reopen the receivable: only net cash retained counts as paid.
        val netPaid = Math.subtractExact(getPaymentsTotal(payment.invoiceId), getRefundsTotal(payment.invoiceId))
        val credits = getReturnsTotal(payment.invoiceId)
        require(Math.addExact(netPaid, payment.amountMinor) <= Math.subtractExact(invoice.totalMinor, credits)) {
            "Payment exceeds invoice balance after returns and refunds"
        }
        return insertPayment(payment)
    }

    @Transaction
    open suspend fun recordRefund(refund: PaymentRefundEntity): Long {
        require(refund.amountMinor > 0L) { "Refund amount must be positive" }
        val key = refund.idempotencyKey.trim()
        require(key.isNotEmpty()) { "Refund idempotency key is required" }
        val previous = getRefundByIdempotencyKey(key)
        if (previous != null) {
            require(
                previous.invoiceId == refund.invoiceId &&
                    previous.paymentId == refund.paymentId &&
                    previous.amountMinor == refund.amountMinor &&
                    previous.reason == refund.reason.trim()
            ) { "Idempotency key was already used for a different refund" }
            return previous.id
        }
        val invoice = getInvoice(refund.invoiceId) ?: error("Invoice not found")
        require(invoice.status == "POSTED") { "Cannot refund a cancelled invoice" }
        val payment = getPayment(refund.paymentId) ?: error("Payment not found")
        require(payment.invoiceId == refund.invoiceId) { "Payment does not belong to the selected invoice" }
        val refundedBefore = getRefundsTotalForPayment(payment.id)
        require(Math.addExact(refundedBefore, refund.amountMinor) <= payment.amountMinor) {
            "Refund exceeds the unrefunded part of this receipt"
        }
        return insertPaymentRefund(refund.copy(reason = refund.reason.trim(), idempotencyKey = key))
    }

    @Transaction
    open suspend fun cancelInvoice(
        invoiceId: Long,
        occurredAtEpochDay: Long = java.time.LocalDate.now().toEpochDay()
    ): Boolean {
        val invoice = getInvoice(invoiceId) ?: return false
        if (invoice.status != "POSTED") return false

        // Preserve each original receipt and record its refund in the same transaction.
        val payments = getPaymentsForInvoice(invoiceId)
        payments.forEach { payment ->
            val alreadyRefunded = getRefundsTotalForPayment(payment.id)
            val remainingRefund = Math.subtractExact(payment.amountMinor, alreadyRefunded)
            if (remainingRefund > 0L) {
                insertPaymentRefund(PaymentRefundEntity(
                    invoiceId = invoiceId,
                    paymentId = payment.id,
                    amountMinor = remainingRefund,
                    refundedAtEpochDay = occurredAtEpochDay,
                    reason = "بازپرداخت بابت لغو فاکتور #$invoiceId",
                    idempotencyKey = "cancel-invoice-" + invoiceId + "-payment-" + payment.id
                ))
            }
        }

        // Returned quantities are already back in stock; restore only the remainder.
        val lines = getInvoiceLines(invoiceId)
        for (line in lines) {
            val alreadyReturned = getReturnedQuantity(line.id)
            val quantityToRestore = Math.subtractExact(line.quantity, alreadyReturned)
            if (quantityToRestore > 0L) {
                check(increaseStock(line.productId, quantityToRestore) == 1) {
                    "Could not restore stock; cancellation rolled back"
                }
                insertStockMovement(StockMovementEntity(
                    productId = line.productId,
                    quantityDelta = quantityToRestore,
                    reason = "برگشت موجودی بابت لغو فاکتور #$invoiceId",
                    occurredAtEpochDay = occurredAtEpochDay,
                    invoiceId = invoiceId
                ))
            }
        }
        check(markInvoiceCancelled(invoiceId) == 1) { "Invoice state changed; cancellation rolled back" }
        return true
    }

    @Transaction
    open suspend fun recordReturn(
        invoiceId: Long,
        invoiceLineId: Long,
        quantity: Long,
        reason: String,
        returnedAtEpochDay: Long
    ): Long {
        require(quantity > 0L) { "Return quantity must be positive" }
        require(reason.isNotBlank()) { "Return reason is required" }
        val invoice = getInvoice(invoiceId) ?: error("Invoice not found")
        require(invoice.status == "POSTED") { "Only posted invoices can have returns" }
        val line = getInvoiceLine(invoiceLineId) ?: error("Invoice line not found")
        require(line.invoiceId == invoiceId) { "Invoice line does not belong to the selected invoice" }
        val previousQuantity = getReturnedQuantity(invoiceLineId)
        val newQuantity = Math.addExact(previousQuantity, quantity)
        require(newQuantity <= line.quantity) { "Return quantity exceeds the quantity sold" }
        val subtotal = getInvoiceSubtotal(invoiceId)
        val lineDiscount = if (subtotal == 0L) 0L else
            BigDecimal.valueOf(invoice.discountMinor)
                .multiply(BigDecimal.valueOf(line.lineTotalMinor))
                .divide(BigDecimal.valueOf(subtotal), 0, RoundingMode.HALF_UP)
                .longValueExact()
                .coerceAtMost(line.lineTotalMinor)
        val netLineAmount = Math.subtractExact(line.lineTotalMinor, lineDiscount)
        val targetCredit = BigDecimal.valueOf(netLineAmount)
            .multiply(BigDecimal.valueOf(newQuantity))
            .divide(BigDecimal.valueOf(line.quantity), 0, RoundingMode.HALF_UP)
            .longValueExact()
        val previousCredit = getLineCreditTotal(invoiceLineId)
        val credit = Math.subtractExact(targetCredit, previousCredit)
        check(credit >= 0L) { "Return credit calculation is inconsistent" }
        check(increaseStock(line.productId, quantity) == 1) { "Could not restore returned stock" }
        insertStockMovement(StockMovementEntity(
            productId = line.productId,
            quantityDelta = quantity,
            reason = "برگشت کالا: ${reason.trim()}",
            occurredAtEpochDay = returnedAtEpochDay,
            invoiceId = invoiceId
        ))
        return insertSalesReturn(SalesReturnEntity(
            invoiceId = invoiceId,
            invoiceLineId = invoiceLineId,
            productId = line.productId,
            quantity = quantity,
            creditAmountMinor = credit,
            reason = reason.trim(),
            returnedAtEpochDay = returnedAtEpochDay
        ))
    }
}
