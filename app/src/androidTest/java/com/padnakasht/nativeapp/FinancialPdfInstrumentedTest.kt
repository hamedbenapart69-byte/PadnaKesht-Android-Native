package com.padnakasht.nativeapp

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.padnakasht.nativeapp.domain.FinancialTransaction
import com.padnakasht.nativeapp.domain.TransactionKind
import com.padnakasht.nativeapp.report.FinancialReportExporter
import com.padnakasht.nativeapp.report.BusinessReportData
import com.padnakasht.nativeapp.report.BusinessReportRow
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class FinancialPdfInstrumentedTest {

    @Test
    fun businessPdfExportProducesRenderablePageWithAccountingSummary() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "business-financial-pdf-test.pdf")
        file.delete()
        val day = LocalDate.of(2026, 10, 21).toEpochDay()
        val report = BusinessReportData(
            title = "گزارش فروش و دریافت‌ها",
            startEpochDay = day,
            endEpochDayInclusive = day,
            invoiceSalesMinor = 100_000,
            receiptsMinor = 60_000,
            returnCreditsMinor = 10_000,
            refundsMinor = 5_000,
            outstandingMinor = 30_000,
            rows = listOf(
                BusinessReportRow(day, "فاکتور قطعی", "فاکتور #1", "مشتری آزمایشی", 100_000, 30_000, "فاکتور"),
                BusinessReportRow(day, "دریافتی", "رسید #1", "مشتری آزمایشی", 60_000, 30_000, "پرداخت جزئی"),
                BusinessReportRow(day, "مرجوعی", "برگشت #1", "مشتری آزمایشی", 10_000, 30_000, "مرجوعی"),
                BusinessReportRow(day, "بازپرداخت", "بازپرداخت #1", "مشتری آزمایشی", 5_000, 30_000, "بازپرداخت")
            )
        )
        try {
            FinancialReportExporter.writeBusinessPdf(context.contentResolver, Uri.fromFile(file), report)
            assertTrue("Business PDF must exist and be non-empty", file.isFile && file.length() > 500)
            assertTrue("Business PDF header must be valid", file.inputStream().use {
                val header = ByteArray(5)
                it.read(header) == 5 && String(header, Charsets.US_ASCII) == "%PDF-"
            })
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertTrue("Business PDF must contain at least one page", renderer.pageCount >= 1)
                    renderer.openPage(0).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            var visible = 0
                            for (y in 0 until bitmap.height step 8) for (x in 0 until bitmap.width step 8) {
                                if (bitmap.getPixel(x, y) != Color.WHITE) visible++
                            }
                            assertTrue("Business PDF must render visible report content", visible > 20)
                        } finally { bitmap.recycle() }
                    }
                }
            }
        } finally { file.delete() }
    }

    @Test
    fun pdfExportProducesRenderablePageForPersianAndJalaliContent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "financial-pdf-rtl-test.pdf")
        file.delete()
        val transactions = listOf(
            FinancialTransaction(
                amountMinor = 123450,
                kind = TransactionKind.INCOME,
                category = "گردو و عسل",
                note = "تراکنش آزمایشی فارسی",
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
        try {
            FinancialReportExporter.writePdf(context.contentResolver, Uri.fromFile(file), transactions)
            assertTrue("PDF output must exist and be non-empty", file.isFile && file.length() > 500)
            assertTrue("PDF header must be valid", file.inputStream().use {
                val header = ByteArray(5)
                it.read(header) == 5 && String(header, Charsets.US_ASCII) == "%PDF-"
            })
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertTrue("PDF must contain at least one page", renderer.pageCount >= 1)
                    renderer.openPage(0).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            var nonWhitePixels = 0
                            for (y in 0 until bitmap.height step 8) {
                                for (x in 0 until bitmap.width step 8) {
                                    if (bitmap.getPixel(x, y) != Color.WHITE) nonWhitePixels++
                                }
                            }
                            assertTrue("Rendered PDF page must contain visible content", nonWhitePixels > 20)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        } finally {
            file.delete()
        }
    }
    @Test
    fun longBusinessPdfPaginatesWrappedRowsAcrossAllPages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "business-financial-pdf-pagination-test.pdf")
        file.delete()
        val day = LocalDate.of(2026, 10, 21).toEpochDay()
        val rows = (1..90).map { index ->
            BusinessReportRow(
                day,
                "اصلاح مانده مشتری",
                "مرجع #$index",
                "مشتری آزمایشی",
                1_000L,
                500L,
                "شرح طولانی رویداد حسابداری برای بررسی صفحه‌بندی و جلوگیری از حذف متن در خروجی PDF ".repeat(30)
            )
        }
        val report = BusinessReportData(
            title = "گزارش جامع آزمون صفحه‌بندی",
            startEpochDay = day,
            endEpochDayInclusive = day,
            invoiceSalesMinor = 90_000L,
            receiptsMinor = 45_000L,
            returnCreditsMinor = 0L,
            refundsMinor = 0L,
            outstandingMinor = 45_000L,
            rows = rows
        )
        try {
            FinancialReportExporter.writeBusinessPdf(context.contentResolver, Uri.fromFile(file), report)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertTrue("Long report must continue onto additional pages", renderer.pageCount > 4)
                    for (index in 0 until renderer.pageCount) {
                        renderer.openPage(index).use { page ->
                            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            try {
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                var visible = 0
                                for (y in 0 until bitmap.height step 12) for (x in 0 until bitmap.width step 12) {
                                    if (bitmap.getPixel(x, y) != Color.WHITE) visible++
                                }
                                assertTrue("PDF page $index must render visible content", visible > 5)
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
        } finally {
            file.delete()
        }
    }


}
