package com.padnakasht.nativeapp.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val notes: String = ""
)

@Entity(tableName = "products", indices = [Index(value = ["sku"], unique = true)])
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sku: String,
    val stockQuantity: Long,
    val unitPriceMinor: Long
) {
    init {
        require(name.isNotBlank())
        require(stockQuantity >= 0)
        require(unitPriceMinor >= 0)
    }
}

@Entity(
    tableName = "invoices",
    foreignKeys = [ForeignKey(
        entity = CustomerEntity::class,
        parentColumns = ["id"],
        childColumns = ["customerId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index(value = ["customerId"])]
)
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val createdAtEpochDay: Long,
    val totalMinor: Long = 0,
    val status: String = "POSTED"
)

@Entity(
    tableName = "invoice_lines",
    foreignKeys = [
        ForeignKey(entity = InvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["invoiceId"]), Index(value = ["productId"])]
)
data class InvoiceLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val productId: Long,
    val quantity: Long,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long
) {
    init {
        require(quantity > 0)
        require(unitPriceMinor >= 0)
        require(lineTotalMinor >= 0)
    }
}

@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(
        entity = InvoiceEntity::class,
        parentColumns = ["id"],
        childColumns = ["invoiceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("invoiceId")]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val amountMinor: Long,
    val paidAtEpochDay: Long,
    val method: String = "CASH",
    val note: String = ""
) {
    init { require(amountMinor > 0) }
}
