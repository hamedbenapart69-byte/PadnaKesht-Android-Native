package com.padnakasht.nativeapp.data

import com.padnakasht.nativeapp.data.local.BusinessDao
import com.padnakasht.nativeapp.data.local.CustomerEntity
import com.padnakasht.nativeapp.data.local.InvoiceEntity
import com.padnakasht.nativeapp.data.local.InvoiceLineEntity
import com.padnakasht.nativeapp.data.local.PaymentEntity
import com.padnakasht.nativeapp.data.local.ProductEntity
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class BusinessRepository(private val dao: BusinessDao) {
    val customers = dao.observeCustomers()
    val products = dao.observeProducts()
    val invoices = dao.observeInvoices()

    suspend fun addCustomer(name: String, phone: String = "", notes: String = ""): Long {
        require(name.isNotBlank()) { "Customer name is required" }
        return dao.insertCustomer(CustomerEntity(name = name.trim(), phone = phone.trim(), notes = notes.trim()))
    }

    suspend fun addProduct(name: String, sku: String, stockQuantity: Long, unitPriceMinor: Long): Long {
        require(name.isNotBlank()) { "Product name is required" }
        require(sku.isNotBlank()) { "SKU is required" }
        require(stockQuantity >= 0) { "Stock cannot be negative" }
        require(unitPriceMinor >= 0) { "Price cannot be negative" }
        return dao.insertProduct(ProductEntity(name = name.trim(), sku = sku.trim(), stockQuantity = stockQuantity, unitPriceMinor = unitPriceMinor))
    }

    suspend fun createInvoice(customerId: Long, lines: List<InvoiceLineEntity>): Long {
        require(customerId > 0) { "Customer is required" }
        require(lines.isNotEmpty()) { "An invoice must contain at least one line" }
        require(dao.getCustomer(customerId) != null) { "Customer not found" }
        return dao.postInvoice(
            InvoiceEntity(customerId = customerId, createdAtEpochDay = LocalDate.now().toEpochDay()),
            lines
        )
    }

    suspend fun createSingleProductInvoice(customerId: Long, productId: Long, quantity: Long): Long {
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

    suspend fun outstandingBalance(invoiceId: Long): Long {
        val invoice = dao.getInvoice(invoiceId) ?: error("Invoice not found")
        return (invoice.totalMinor - dao.getPaymentsTotal(invoiceId)).coerceAtLeast(0L)
    }
}
