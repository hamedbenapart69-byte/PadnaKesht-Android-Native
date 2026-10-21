package com.padnakasht.nativeapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        CustomerEntity::class,
        ProductEntity::class,
        InvoiceEntity::class,
        InvoiceLineEntity::class,
        PaymentEntity::class,
        PaymentRefundEntity::class,
        StockMovementEntity::class,
        SalesReturnEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun businessDao(): BusinessDao

    companion object {

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS payment_refunds (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, invoiceId INTEGER NOT NULL, paymentId INTEGER NOT NULL, amountMinor INTEGER NOT NULL, refundedAtEpochDay INTEGER NOT NULL, reason TEXT NOT NULL, idempotencyKey TEXT NOT NULL, FOREIGN KEY(invoiceId) REFERENCES invoices(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(paymentId) REFERENCES payments(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payment_refunds_invoiceId ON payment_refunds(invoiceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payment_refunds_paymentId ON payment_refunds(paymentId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payment_refunds_idempotencyKey ON payment_refunds(idempotencyKey)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE payments ADD COLUMN idempotencyKey TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payments_idempotencyKey ON payments(idempotencyKey)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS sales_returns (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, invoiceId INTEGER NOT NULL, invoiceLineId INTEGER NOT NULL, productId INTEGER NOT NULL, quantity INTEGER NOT NULL, creditAmountMinor INTEGER NOT NULL, reason TEXT NOT NULL, returnedAtEpochDay INTEGER NOT NULL, FOREIGN KEY(invoiceId) REFERENCES invoices(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(invoiceLineId) REFERENCES invoice_lines(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_returns_invoiceId ON sales_returns(invoiceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_returns_invoiceLineId ON sales_returns(invoiceLineId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_returns_productId ON sales_returns(productId)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN category TEXT NOT NULL DEFAULT 'عمومی'")
                db.execSQL("ALTER TABLE products ADD COLUMN lowStockThreshold INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS stock_movements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, productId INTEGER NOT NULL, quantityDelta INTEGER NOT NULL, reason TEXT NOT NULL, occurredAtEpochDay INTEGER NOT NULL, invoiceId INTEGER, FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(invoiceId) REFERENCES invoices(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_productId ON stock_movements(productId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_invoiceId ON stock_movements(invoiceId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE financial_transactions ADD COLUMN status TEXT NOT NULL DEFAULT 'POSTED'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN discountMinor INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE invoices ADD COLUMN dueAtEpochDay INTEGER")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS customers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, phone TEXT NOT NULL, notes TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS products (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, sku TEXT NOT NULL, stockQuantity INTEGER NOT NULL, unitPriceMinor INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_products_sku ON products(sku)")
                db.execSQL("CREATE TABLE IF NOT EXISTS invoices (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, customerId INTEGER NOT NULL, createdAtEpochDay INTEGER NOT NULL, totalMinor INTEGER NOT NULL, status TEXT NOT NULL, FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_customerId ON invoices(customerId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS invoice_lines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, invoiceId INTEGER NOT NULL, productId INTEGER NOT NULL, quantity INTEGER NOT NULL, unitPriceMinor INTEGER NOT NULL, lineTotalMinor INTEGER NOT NULL, FOREIGN KEY(invoiceId) REFERENCES invoices(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(productId) REFERENCES products(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_lines_invoiceId ON invoice_lines(invoiceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_lines_productId ON invoice_lines(productId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS payments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, invoiceId INTEGER NOT NULL, amountMinor INTEGER NOT NULL, paidAtEpochDay INTEGER NOT NULL, method TEXT NOT NULL, note TEXT NOT NULL, FOREIGN KEY(invoiceId) REFERENCES invoices(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payments_invoiceId ON payments(invoiceId)")
            }
        }
    }
}
