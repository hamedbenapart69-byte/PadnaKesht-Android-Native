package com.padnakasht.nativeapp.report

import android.content.ContentResolver
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.padnakasht.nativeapp.domain.FinancialTransaction
import com.padnakasht.nativeapp.domain.LedgerRules
import com.padnakasht.nativeapp.domain.JalaliDate
import com.padnakasht.nativeapp.domain.TransactionKind
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class BusinessReportRow(val dateEpochDay: Long, val type: String, val reference: String, val customer: String, val amountMinor: Long, val balanceMinor: Long = 0L, val details: String = "")
data class BusinessReportData(val title: String, val startEpochDay: Long, val endEpochDayInclusive: Long, val invoiceSalesMinor: Long, val receiptsMinor: Long, val returnCreditsMinor: Long, val refundsMinor: Long, val outstandingMinor: Long, val rows: List<BusinessReportRow>, val generalIncomeMinor: Long = 0L, val generalExpenseMinor: Long = 0L)

object FinancialReportExporter {
    fun writePdf(resolver: ContentResolver, uri: Uri, transactions: List<FinancialTransaction>) {
        val output = resolver.openOutputStream(uri) ?: error("Cannot open destination for PDF")
        output.use { stream ->
            val document = PdfDocument()
            try {
                val summary = LedgerRules.summarize(transactions)
                val rowsPerPage = 28
                val pages = maxOf(1, (transactions.size + rowsPerPage - 1) / rowsPerPage)
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
                for (pageIndex in 0 until pages) {
                    val info = PdfDocument.PageInfo.Builder(595, 842, pageIndex + 1).create()
                    val page = document.startPage(info)
                    val canvas = page.canvas
                    var y = 36f
                    y += drawRtlText(canvas, "گزارش مالی پادناکِشت", y, paint, 515, 18f)
                    y += drawRtlText(
                        canvas,
                        "درآمد: ${persianDigits(summary.incomeMinor)} | هزینه: ${persianDigits(summary.expenseMinor)} | خالص: ${persianDigits(summary.netMinor)}",
                        y, paint, 515, 12f
                    )
                    y += drawRtlText(canvas, "تمام مبالغ به تومان هستند.", y, paint, 515, 10f)
                    y += 8f
                    y += drawRtlText(canvas, "تاریخ | نوع | دسته‌بندی | مبلغ | توضیحات", y, paint, 515, 12f)
                    y += 6f
                    val startIndex = pageIndex * rowsPerPage
                    val endIndex = minOf(transactions.size, startIndex + rowsPerPage)
                    for (i in startIndex until endIndex) {
                        val item = transactions[i]
                        val kind = if (item.kind == TransactionKind.INCOME) "درآمد" else "هزینه"
                        val date = JalaliDate.format(LocalDate.ofEpochDay(item.occurredAtEpochDay))
                        val line = "$date | $kind | ${item.category.take(16)} | ${persianDigits(item.amountMinor)} | ${item.note.take(16)}"
                        y += drawRtlText(canvas, line, y, paint, 515, 10f)
                    }
                    document.finishPage(page)
                }
                document.writeTo(stream)
            } finally {
                document.close()
            }
        }
    }

    fun writeBusinessXlsx(output: OutputStream, report: BusinessReportData) {
        ZipOutputStream(output).use { zip ->
            zip.putText("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            zip.putText("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            zip.putText("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="گزارش فروش" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            zip.putText("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            val rows = mutableListOf<List<String>>()
            rows += listOf(report.title, JalaliDate.format(LocalDate.ofEpochDay(report.startEpochDay)), JalaliDate.format(LocalDate.ofEpochDay(report.endEpochDayInclusive)), "", "", "", "")
            rows += listOf("خلاصه", "فروش فاکتوری", "", report.invoiceSalesMinor.toString(), "دریافتی", report.receiptsMinor.toString(), "")
            rows += listOf("خلاصه", "اعتبار مرجوعی", "", report.returnCreditsMinor.toString(), "بازپرداخت", report.refundsMinor.toString(), "")
            rows += listOf("خلاصه", "بدهی باقی‌مانده", "", report.outstandingMinor.toString(), "", "", "")
            rows += listOf("دفتر عمومی", "درآمد عمومی", "", report.generalIncomeMinor.toString(), "هزینه عمومی", report.generalExpenseMinor.toString(), "")
            rows += listOf("تاریخ شمسی", "نوع رویداد", "مرجع", "مشتری", "مبلغ (تومان)", "مانده بدهی", "شرح")
            report.rows.forEach { row -> rows += listOf(JalaliDate.format(LocalDate.ofEpochDay(row.dateEpochDay)), row.type, row.reference, row.customer, row.amountMinor.toString(), row.balanceMinor.toString(), row.details) }
            val sheet = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                rows.forEachIndexed { ri, cells ->
                    append("<row r=\"${ri + 1}\">")
                    cells.forEachIndexed { ci, value ->
                        val ref = "${columnName(ci + 1)}${ri + 1}"
                        if ((ri in 1..4 && (ci == 3 || ci == 5)) || (ri >= 6 && (ci == 4 || ci == 5))) append("<c r=\"$ref\"><v>${value.toLongOrNull() ?: 0L}</v></c>")
                        else append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xmlEscape(value)}</t></is></c>")
                    }
                    append("</row>")
                }
                append("</sheetData></worksheet>")
            }
            zip.putText("xl/worksheets/sheet1.xml", sheet)
        }
    }

    fun writeBusinessPdf(resolver: ContentResolver, uri: Uri, report: BusinessReportData) {
        val output = resolver.openOutputStream(uri) ?: error("Cannot open destination for PDF")
        output.use { stream ->
            val document = PdfDocument()
            try {
                val lines = listOf("گزارش مالی فروش پادناکِشت", "درآمد عمومی: ${persianDigits(report.generalIncomeMinor)} | هزینه عمومی: ${persianDigits(report.generalExpenseMinor)}", "بازه: ${JalaliDate.format(LocalDate.ofEpochDay(report.startEpochDay))} تا ${JalaliDate.format(LocalDate.ofEpochDay(report.endEpochDayInclusive))}", "فروش فاکتوری: ${persianDigits(report.invoiceSalesMinor)}", "دریافتی: ${persianDigits(report.receiptsMinor)} | اعتبار مرجوعی: ${persianDigits(report.returnCreditsMinor)}", "بازپرداخت: ${persianDigits(report.refundsMinor)} | بدهی باقی‌مانده: ${persianDigits(report.outstandingMinor)}", "تاریخ شمسی | رویداد | مرجع | مشتری | مبلغ | مانده | شرح") + report.rows.map { row -> "${JalaliDate.format(LocalDate.ofEpochDay(row.dateEpochDay))} | ${row.type} | ${row.reference} | ${row.customer} | ${persianDigits(row.amountMinor)} | ${persianDigits(row.balanceMinor)} | ${row.details}" }
                val perPage = 30
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
                for (pageIndex in 0 until maxOf(1, (lines.size + perPage - 1) / perPage)) {
                    val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageIndex + 1).create())
                    var y = 32f
                    lines.drop(pageIndex * perPage).take(perPage).forEach { line -> y += drawRtlText(page.canvas, line, y, paint, 515, 9f) }
                    document.finishPage(page)
                }
                document.writeTo(stream)
            } finally { document.close() }
        }
    }

    private fun drawRtlText(
        canvas: Canvas,
        text: String,
        top: Float,
        basePaint: TextPaint,
        width: Int,
        textSize: Float
    ): Float {
        val paint = TextPaint(basePaint).apply { this.textSize = textSize }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(40f, top)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat() + 4f
    }

    private fun persianDigits(value: Long): String = value.toString().map { ch ->
        if (ch in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[ch - '0'] else ch
    }.joinToString("")

    fun writeXlsx(output: OutputStream, transactions: List<FinancialTransaction>) {
        val summary = LedgerRules.summarize(transactions)
        ZipOutputStream(output).use { zip ->
            zip.putText("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            zip.putText("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            zip.putText("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="گزارش مالی" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            zip.putText("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            val rows = mutableListOf<List<String>>()
            rows += listOf("تاریخ", "نوع", "دسته‌بندی", "مبلغ (تومان)", "توضیحات")
            rows += listOf("خلاصه", "درآمد", "", summary.incomeMinor.toString(), "")
            rows += listOf("خلاصه", "هزینه", "", summary.expenseMinor.toString(), "")
            rows += listOf("خلاصه", "خالص", "", summary.netMinor.toString(), "")
            transactions.forEach { item ->
                rows += listOf(
                    JalaliDate.format(LocalDate.ofEpochDay(item.occurredAtEpochDay)),
                    if (item.kind == TransactionKind.INCOME) "درآمد" else "هزینه",
                    item.category,
                    item.amountMinor.toString(),
                    item.note
                )
            }
            val sheet = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                rows.forEachIndexed { rowIndex, cells ->
                    append("<row r=\"${rowIndex + 1}\">")
                    cells.forEachIndexed { columnIndex, value ->
                        val ref = "${columnName(columnIndex + 1)}${rowIndex + 1}"
                        if (columnIndex == 3 && rowIndex > 0) {
                            val numeric = value.toLongOrNull() ?: 0L
                            append("<c r=\"$ref\"><v>$numeric</v></c>")
                        } else {
                            append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xmlEscape(value)}</t></is></c>")
                        }
                    }
                    append("</row>")
                }
                append("</sheetData></worksheet>")
            }
            zip.putText("xl/worksheets/sheet1.xml", sheet)
        }
    }

    private fun ZipOutputStream.putText(path: String, value: String) {
        putNextEntry(ZipEntry(path))
        write(value.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun columnName(number: Int): String {
        var n = number
        var result = ""
        while (n > 0) {
            val rem = (n - 1) % 26
            result = ('A' + rem) + result
            n = (n - 1) / 26
        }
        return result
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
