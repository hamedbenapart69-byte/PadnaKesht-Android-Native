package com.padnakasht.nativeapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT COALESCE(SUM(CASE WHEN invoices.status = 'POSTED' THEN invoices.totalMinor - (SELECT COALESCE(SUM(payments.amountMinor), 0) FROM payments WHERE payments.invoiceId = invoices.id) ELSE 0 END), 0) FROM invoices WHERE invoices.customerId = :customerId")
    abstract fun observeCustomerBalance(customerId: Long): Flow<Long>

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    abstract fun observeProducts(): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertProduct(product: ProductEntity): Long

    @Query("UPDATE products SET stockQuantity = stockQuantity - :quantity WHERE id = :productId AND stockQuantity >= :quantity")
    abstract suspend fun decreaseStockIfAvailable(productId: Long, quantity: Long): Int

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

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM payments WHERE invoiceId = :invoiceId")
    abstract suspend fun getPaymentsTotal(invoiceId: Long): Long

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM payments WHERE invoiceId = :invoiceId")
    abstract fun observePaymentsTotal(invoiceId: Long): Flow<Long>

    @Query("SELECT * FROM payments WHERE invoiceId = :invoiceId ORDER BY paidAtEpochDay DESC, id DESC")
    abstract fun observePayments(invoiceId: Long): Flow<List<PaymentEntity>>

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
        val invoice = getInvoice(payment.invoiceId) ?: error("Invoice not found")
        require(invoice.status == "POSTED") { "Cannot pay a cancelled invoice" }
        val paid = getPaymentsTotal(payment.invoiceId)
        require(Math.addExact(paid, payment.amountMinor) <= invoice.totalMinor) { "Payment exceeds invoice balance" }
        return insertPayment(payment)
    }

    @Transaction
    open suspend fun cancelInvoice(invoiceId: Long): Boolean {
        val invoice = getInvoice(invoiceId) ?: return false
        if (invoice.status != "POSTED") return false
        require(getPaymentsTotal(invoiceId) == 0L) { "Invoice with recorded payments cannot be cancelled until refunds are recorded" }
        val lines = getInvoiceLines(invoiceId)
        for (line in lines) {
            check(increaseStock(line.productId, line.quantity) == 1) {
                "Could not restore stock; cancellation rolled back"
            }
            insertStockMovement(StockMovementEntity(
                productId = line.productId,
                quantityDelta = line.quantity,
                reason = "برگشت موجودی بابت لغو فاکتور #$invoiceId",
                occurredAtEpochDay = invoice.createdAtEpochDay,
                invoiceId = invoiceId
            ))
        }
        check(markInvoiceCancelled(invoiceId) == 1) { "Invoice state changed; cancellation rolled back" }
        return true
    }
}
