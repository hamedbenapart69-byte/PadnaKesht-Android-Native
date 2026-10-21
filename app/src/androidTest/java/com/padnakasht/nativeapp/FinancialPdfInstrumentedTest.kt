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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class FinancialPdfInstrumentedTest {
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
}
