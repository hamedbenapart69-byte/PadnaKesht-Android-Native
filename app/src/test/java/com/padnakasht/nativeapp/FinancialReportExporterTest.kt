package com.padnakasht.nativeapp

import com.padnakasht.nativeapp.domain.FinancialTransaction
import com.padnakasht.nativeapp.domain.JalaliDate
import com.padnakasht.nativeapp.domain.TransactionKind
import com.padnakasht.nativeapp.report.FinancialReportExporter
import com.padnakasht.nativeapp.report.BusinessReportData
import com.padnakasht.nativeapp.report.BusinessReportRow
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialReportExporterTest {
    @Test
    fun xlsxIsReadableZipAndContainsExpectedWorkbookParts() {
        val output = ByteArrayOutputStream()
        FinancialReportExporter.writeXlsx(output, sampleTransactions())

        val bytes = output.toByteArray()
        assertTrue("XLSX must not be empty", bytes.isNotEmpty())
        val entries = mutableMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }

        assertTrue("Workbook must contain content types", entries.containsKey("[Content_Types].xml"))
        assertTrue("Workbook must contain relationships", entries.containsKey("_rels/.rels"))
        assertTrue("Workbook must contain workbook metadata", entries.containsKey("xl/workbook.xml"))
        val sheet = entries["xl/worksheets/sheet1.xml"]
        assertNotNull("Worksheet must exist", sheet)
        assertTrue("Worksheet XML must close correctly", sheet!!.contains("</sheetData></worksheet>"))
    }

    @Test
    fun xlsxIncludesJalaliDateForEveryTransactionAndPreservesPersianText() {
        val transactions = sampleTransactions()
        val output = ByteArrayOutputStream()
        FinancialReportExporter.writeXlsx(output, transactions)

        val sheet = unzipWorksheet(output.toByteArray())
        transactions.forEachIndexed { index, transaction ->
            val expectedDate = JalaliDate.format(LocalDate.ofEpochDay(transaction.occurredAtEpochDay))
            assertTrue("Missing Jalali date for transaction row ${index + 5}: $expectedDate", sheet.contains(expectedDate))
        }
        assertTrue(sheet.contains("درآمد"))
        assertTrue(sheet.contains("هزینه"))
        assertTrue(sheet.contains("گردو &amp; عسل"))
        assertTrue(sheet.contains("توضیحات فارسی"))
        assertTrue(sheet.contains("123450"))
        assertTrue(sheet.contains("25000"))
        assertTrue(sheet.contains("خالص"))
    }

    @Test
    fun xlsxEscapesXmlSpecialCharactersAndRetainsRtlUnicode() {
        val transaction = FinancialTransaction(
            amountMinor = 321,
            kind = TransactionKind.INCOME,
            category = "محصول <ویژه>",
            note = "پرداخت & دریافت",
            occurredAtEpochDay = LocalDate.of(2026, 3, 21).toEpochDay()
        )
        val output = ByteArrayOutputStream()
        FinancialReportExporter.writeXlsx(output, listOf(transaction))
        val sheet = unzipWorksheet(output.toByteArray())

        assertTrue(sheet.contains("محصول &lt;ویژه&gt;"))
        assertTrue(sheet.contains("پرداخت &amp; دریافت"))
        val workbook = unzipEntry(output.toByteArray(), "xl/workbook.xml")
        assertTrue(workbook.contains("گزارش مالی"))
    }


    @Test
    fun businessXlsxIncludesSeparateAccountingEventsAndActualEventDates() {
        val invoiceDay = LocalDate.of(2026, 10, 1).toEpochDay()
        val paymentDay = LocalDate.of(2026, 10, 3).toEpochDay()
        val returnDay = LocalDate.of(2026, 10, 4).toEpochDay()
        val refundDay = LocalDate.of(2026, 10, 5).toEpochDay()
        val report = BusinessReportData(
            title = "گزارش ماهانه",
            startEpochDay = invoiceDay,
            endEpochDayInclusive = refundDay,
            invoiceSalesMinor = 100_000,
            receiptsMinor = 60_000,
            returnCreditsMinor = 10_000,
            refundsMinor = 20_000,
            outstandingMinor = 30_000,
            rows = listOf(
                BusinessReportRow(invoiceDay, "فاکتور قطعی", "فاکتور #1", "مشتری نمونه", 100_000, 30_000, "پرداخت‌نشده کامل نیست"),
                BusinessReportRow(paymentDay, "دریافتی", "رسید #1", "مشتری نمونه", 60_000, 30_000, "پرداخت جزئی"),
                BusinessReportRow(returnDay, "مرجوعی", "برگشت #1", "مشتری نمونه", 10_000, 30_000, "کالای برگشتی"),
                BusinessReportRow(refundDay, "بازپرداخت", "بازپرداخت #1", "مشتری نمونه", 20_000, 30_000, "لغو فاکتور")
            )
        )
        val output = ByteArrayOutputStream()
        FinancialReportExporter.writeBusinessXlsx(output, report)
        val sheet = unzipWorksheet(output.toByteArray())
        assertTrue(sheet.contains("فروش فاکتوری"))
        assertTrue(sheet.contains("دریافتی"))
        assertTrue(sheet.contains("مرجوعی"))
        assertTrue(sheet.contains("بازپرداخت"))
        assertTrue(sheet.contains("مشتری نمونه"))
        assertTrue(sheet.contains("100000"))
        assertTrue(sheet.contains("60000"))
        assertTrue(sheet.contains("10000"))
        assertTrue(sheet.contains("20000"))
        assertTrue(sheet.contains(JalaliDate.format(LocalDate.ofEpochDay(paymentDay))))
        assertTrue(sheet.contains(JalaliDate.format(LocalDate.ofEpochDay(refundDay))))
        assertTrue(sheet.contains("30000"))
    }

    private fun unzipWorksheet(bytes: ByteArray): String =
        unzipEntry(bytes, "xl/worksheets/sheet1.xml")

    private fun unzipEntry(bytes: ByteArray, expectedName: String): String {
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == expectedName) {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
            }
        }
        throw AssertionError("XLSX entry is missing: $expectedName")
    }

    private fun sampleTransactions() = listOf(
        FinancialTransaction(
            amountMinor = 123450,
            kind = TransactionKind.INCOME,
            category = "گردو & عسل",
            note = "توضیحات فارسی",
            occurredAtEpochDay = LocalDate.of(2026, 3, 21).toEpochDay()
        ),
        FinancialTransaction(
            amountMinor = 25000,
            kind = TransactionKind.EXPENSE,
            category = "بسته‌بندی",
            note = "هزینه ارسال",
            occurredAtEpochDay = LocalDate.of(2026, 10, 21).toEpochDay()
        )
    )
}
