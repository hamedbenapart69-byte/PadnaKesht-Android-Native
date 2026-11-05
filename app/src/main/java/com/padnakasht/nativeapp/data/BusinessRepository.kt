package com.padnakasht.nativeapp.data

import com.padnakasht.nativeapp.data.local.BusinessDao
import com.padnakasht.nativeapp.data.local.CustomerEntity
import com.padnakasht.nativeapp.data.local.CustomerBalanceAdjustmentEntity
import com.padnakasht.nativeapp.data.local.InvoiceEntity
import com.padnakasht.nativeapp.data.local.InvoiceLineEntity
import com.padnakasht.nativeapp.data.local.PaymentEntity
import com.padnakasht.nativeapp.data.local.ProductEntity
import com.padnakasht.nativeapp.data.local.StockMovementEntity
import com.padnakasht.nativeapp.data.local.SalesReturnEntity
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class BusinessRepository(private val dao: BusinessDao) {
    val customers = dao.observeCustomers()
    val products = dao.observeProducts()
    val invoices = dao.observeInvoices()
    val stockMovements = dao.observeStockMovements()
    val allPayments = dao.observeAllPayments()
    val allSalesReturns = dao.observeAllSalesReturns()
    val allRefunds = dao.observeAllRefunds()

    suspend fun addCustomer(name: String, phone: String = "", notes: String = ""): Long {
        require(name.isNotBlank()) { "Customer name is required" }
        return dao.insertCustomer(CustomerEntity(name = name.trim(), phone = phone.trim(), notes = notes.trim()))
    }

    suspend fun updateCustomer(customerId: Long, name: String, phone: String = "", notes: String = ""): Boolean {
        require(customerId > 0L) { "Customer ID is required" }
        require(name.isNotBlank()) { "Customer name is required" }
        require(dao.getCustomer(customerId) != null) { "Customer not found" }
        return dao.updateCustomerDetails(customerId, name.trim(), phone.trim(), notes.trim()) == 1
    }

    suspend fun setCustomerActive(customerId: Long, isActive: Boolean): Boolean {
        require(customerId > 0L) { "Customer ID is required" }
        require(dao.getCustomer(customerId) != null) { "Customer not found" }
        return dao.setCustomerActive(customerId, isActive) == 1
    }

    suspend fun adjustCustomerBalance(customerId: Long, amountDeltaMinor: Long, reason: String): Long {
        require(customerId > 0L) { "Customer ID is required" }
        require(amountDeltaMinor != 0L) { "Customer balance adjustment cannot be zero" }
        require(reason.isNotBlank()) { "Customer balance adjustment reason is required" }
        require(dao.getCustomer(customerId) != null) { "Customer not found" }
        return dao.insertCustomerBalanceAdjustment(
            CustomerBalanceAdjustmentEntity(
                customerId = customerId,
                amountDeltaMinor = amountDeltaMinor,
                occurredAtEpochDay = LocalDate.now().toEpochDay(),
                reason = reason.trim()
            )
        )
    }

    suspend fun addProduct(
        name: String,
        sku: String,
        stockQuantity: Long,
        unitPriceMinor: Long,
        category: String = "عمومی",
        lowStockThreshold: Long = 0L
    ): Long {
        require(name.isNotBlank()) { "Product name is required" }
        require(sku.isNotBlank()) { "SKU is required" }
        require(stockQuantity >= 0) { "Stock cannot be negative" }
        require(unitPriceMinor >= 0) { "Price cannot be negative" }
        require(lowStockThreshold >= 0) { "Low-stock threshold cannot be negative" }
        return dao.insertProduct(ProductEntity(
            name = name.trim(),
            sku = sku.trim(),
            stockQuantity = stockQuantity,
            unitPriceMinor = unitPriceMinor,
            category = category.trim().ifBlank { "عمومی" },
            lowStockThreshold = lowStockThreshold
        ))
    }

    suspend fun updateProduct(
        productId: Long,
        name: String,
        sku: String,
        unitPriceMinor: Long,
        category: String = "عمومی",
        lowStockThreshold: Long = 0L
    ): Boolean {
        require(productId > 0L) { "Product ID is required" }
        require(name.isNotBlank()) { "Product name is required" }
        require(sku.isNotBlank()) { "SKU is required" }
        require(unitPriceMinor >= 0L) { "Price cannot be negative" }
        require(lowStockThreshold >= 0L) { "Low-stock threshold cannot be negative" }
        require(dao.getProduct(productId) != null) { "Product not found" }
        return dao.updateProductDetails(
            productId,
            name.trim(),
            sku.trim(),
            unitPriceMinor,
            category.trim().ifBlank { "عمومی" },
            lowStockThreshold
        ) == 1
    }

    suspend fun setProductActive(productId: Long, isActive: Boolean): Boolean {
        require(productId > 0L) { "Product ID is required" }
        require(dao.getProduct(productId) != null) { "Product not found" }
        return dao.setProductActive(productId, isActive) == 1
    }

    suspend fun createInvoice(
        customerId: Long,
        lines: List<InvoiceLineEntity>,
        discountMinor: Long = 0L,
        dueAtEpochDay: Long? = null
    ): Long {
        require(customerId > 0) { "Customer is required" }
        require(lines.isNotEmpty()) { "An invoice must contain at least one line" }
        require(discountMinor >= 0L) { "Discount cannot be negative" }
        val customer = requireNotNull(dao.getCustomer(customerId)) { "Customer not found" }
        require(customer.isActive) { "Cannot invoice an archived customer" }
        lines.forEach { line ->
            val product = requireNotNull(dao.getProduct(line.productId)) { "Product not found" }
            require(product.isActive) { "Cannot sell an archived product" }
        }
        return dao.postInvoice(
            InvoiceEntity(
                customerId = customerId,
                createdAtEpochDay = LocalDate.now().toEpochDay(),
                discountMinor = discountMinor,
                dueAtEpochDay = dueAtEpochDay
            ),
            lines
        )
    }

    suspend fun createSingleProductInvoice(customerId: Long, productId: Long, quantity: Long): Long {
        require(customerId > 0) { "Customer is required" }
        val customer = requireNotNull(dao.getCustomer(customerId)) { "Customer not found" }
        require(customer.isActive) { "Cannot invoice an archived customer" }
        require(quantity > 0) { "Quantity must be positive" }
        val product = requireNotNull(dao.getProduct(productId)) { "Product not found" }
        require(product.isActive) { "Cannot sell an archived product" }
        val lineTotal = Math.multiplyExact(product.unitPriceMinor, quantity)
        return dao.postInvoice(
            InvoiceEntity(customerId = customerId, createdAtEpochDay = LocalDate.now().toEpochDay()),
            listOf(
                InvoiceLineEntity(
                    invoiceId = 0,
                    productId = productId,
                    quantity = quantity,
                    unitPriceMinor = product.unitPriceMinor,
                    lineTotalMinor = lineTotal
                )
            )
        )
    }

    suspend fun receivePayment(invoiceId: Long, amountMinor: Long, note: String = "", idempotencyKey: String? = null): Long =
        dao.recordPayment(
            PaymentEntity(
                invoiceId = invoiceId,
                amountMinor = amountMinor,
                paidAtEpochDay = LocalDate.now().toEpochDay(),
                note = note.trim(),
                idempotencyKey = idempotencyKey
            )
        )

    suspend fun refundPayment(
        invoiceId: Long,
        paymentId: Long,
        amountMinor: Long,
        reason: String,
        idempotencyKey: String = java.util.UUID.randomUUID().toString()
    ): Long {
        require(invoiceId > 0L && paymentId > 0L) { "Invoice and payment IDs are required" }
        require(amountMinor > 0L) { "Refund amount must be positive" }
        require(reason.isNotBlank()) { "Refund reason is required" }
        return dao.recordRefund(
            PaymentRefundEntity(
                invoiceId = invoiceId,
                paymentId = paymentId,
                amountMinor = amountMinor,
                refundedAtEpochDay = LocalDate.now().toEpochDay(),
                reason = reason.trim(),
                idempotencyKey = idempotencyKey
            )
        )
    }

    suspend fun cancelInvoice(invoiceId: Long): Boolean = dao.cancelInvoice(invoiceId, LocalDate.now().toEpochDay())

    fun observeInvoiceLines(invoiceId: Long) = dao.observeInvoiceLines(invoiceId)

    fun observeSalesReturns(invoiceId: Long) = dao.observeSalesReturns(invoiceId)

    suspend fun recordReturn(invoiceId: Long, invoiceLineId: Long, quantity: Long, reason: String): Long =
        dao.recordReturn(invoiceId, invoiceLineId, quantity, reason, LocalDate.now().toEpochDay())

    suspend fun adjustStock(productId: Long, quantityDelta: Long, reason: String): Boolean =
        dao.adjustStock(productId, quantityDelta, reason, LocalDate.now().toEpochDay())

    fun observePaymentsTotal(invoiceId: Long) = dao.observePaymentsTotal(invoiceId)

    fun observePayments(invoiceId: Long) = dao.observePayments(invoiceId)

    fun observeRefunds(invoiceId: Long) = dao.observeRefunds(invoiceId)

    fun observeCustomerBalance(customerId: Long) = dao.observeCustomerBalance(customerId)

    suspend fun outstandingBalance(invoiceId: Long): Long {
        val invoice = dao.getInvoice(invoiceId) ?: error("Invoice not found")
        if (invoice.status != "POSTED") return 0L
        return Math.subtractExact(
            Math.addExact(
                Math.subtractExact(invoice.totalMinor, dao.getPaymentsTotal(invoiceId)),
                dao.getRefundsTotal(invoiceId)
            ),
            dao.getReturnsTotal(invoiceId)
        )
    }
}
