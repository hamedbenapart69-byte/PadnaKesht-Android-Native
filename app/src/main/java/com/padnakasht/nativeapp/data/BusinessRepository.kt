package com.padnakasht.nativeapp.data

import com.padnakasht.nativeapp.data.local.BusinessDao
import com.padnakasht.nativeapp.data.local.CustomerEntity
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

    suspend fun addCustomer(name: String, phone: String = "", notes: String = ""): Long {
        require(name.isNotBlank()) { "Customer name is required" }
        return dao.insertCustomer(CustomerEntity(name = name.trim(), phone = phone.trim(), notes = notes.trim()))
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

    suspend fun createInvoice(
        customerId: Long,
        lines: List<InvoiceLineEntity>,
        discountMinor: Long = 0L,
        dueAtEpochDay: Long? = null
    ): Long {
        require(customerId > 0) { "Customer is required" }
        require(lines.isNotEmpty()) { "An invoice must contain at least one line" }
        require(discountMinor >= 0L) { "Discount cannot be negative" }
        require(dao.getCustomer(customerId) != null) { "Customer not found" }
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
        require(dao.getCustomer(customerId) != null) { "Customer not found" }
        require(quantity > 0) { "Quantity must be positive" }
        val product = dao.getProduct(productId) ?: error("Product not found")
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

    suspend fun receivePayment(invoiceId: Long, amountMinor: Long, note: String = ""): Long =
        dao.recordPayment(
            PaymentEntity(
                invoiceId = invoiceId,
                amountMinor = amountMinor,
                paidAtEpochDay = LocalDate.now().toEpochDay(),
                note = note.trim()
            )
        )

    suspend fun cancelInvoice(invoiceId: Long): Boolean = dao.cancelInvoice(invoiceId)

    fun observeInvoiceLines(invoiceId: Long) = dao.observeInvoiceLines(invoiceId)

    fun observeSalesReturns(invoiceId: Long) = dao.observeSalesReturns(invoiceId)

    suspend fun recordReturn(invoiceId: Long, invoiceLineId: Long, quantity: Long, reason: String): Long =
        dao.recordReturn(invoiceId, invoiceLineId, quantity, reason, LocalDate.now().toEpochDay())

    suspend fun adjustStock(productId: Long, quantityDelta: Long, reason: String): Boolean =
        dao.adjustStock(productId, quantityDelta, reason, LocalDate.now().toEpochDay())

    fun observePaymentsTotal(invoiceId: Long) = dao.observePaymentsTotal(invoiceId)

    fun observePayments(invoiceId: Long) = dao.observePayments(invoiceId)

    fun observeCustomerBalance(customerId: Long) = dao.observeCustomerBalance(customerId)

    suspend fun outstandingBalance(invoiceId: Long): Long {
        val invoice = dao.getInvoice(invoiceId) ?: error("Invoice not found")
        if (invoice.status != "POSTED") return 0L
        return Math.subtractExact(
            Math.subtractExact(invoice.totalMinor, dao.getPaymentsTotal(invoiceId)),
            dao.getReturnsTotal(invoiceId)
        )
    }
}
