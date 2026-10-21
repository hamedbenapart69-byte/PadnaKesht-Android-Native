package com.padnakasht.nativeapp.report

import android.content.ContentResolver
import android.graphics.Paint
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

object FinancialReportExporter {
    fun writePdf(resolver: ContentResolver, uri: Uri, transactions: List<FinancialTransaction>) {
        val output = resolver.openOutputStream(uri) ?: error("Cannot open destination for PDF")
        output.use { stream ->
            val document = PdfDocument()
            try {
                val summary = LedgerRules.summarize(transactions)
                val rowsPerPage = 34
                val pages = maxOf(1, (transactions.size + rowsPerPage - 1) / rowsPerPage)
                for (pageIndex in 0 until pages) {
                    val info = PdfDocument.PageInfo.Builder(595, 842, pageIndex + 1).create()
                    val page = document.startPage(info)
                    val canvas = page.canvas
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 14f
                        textAlign = Paint.Align.RIGHT
                    }
                    var y = 42f
                    canvas.drawText("گزارش مالی پادناکِشت", 555f, y, paint)
                    y += 28f
                    canvas.drawText("درآمد: ${summary.incomeMinor} | هزینه: ${summary.expenseMinor} | خالص: ${summary.netMinor}", 555f, y, paint)
                    y += 30f
                    canvas.drawText("تاریخ | نوع | دسته‌بندی | مبلغ | توضیحات", 555f, y, paint)
                    y += 24f
                    val start = pageIndex * rowsPerPage
                    val end = minOf(transactions.size, start + rowsPerPage)
                    for (i in start until end) {
                        val item = transactions[i]
                        val kind = if (item.kind == TransactionKind.INCOME) "درآمد" else "هزینه"
                        val date = JalaliDate.format(LocalDate.ofEpochDay(item.occurredAtEpochDay))
                        val amount = item.amountMinor.toString().map { ch -> if (ch in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[ch - '0'] else ch }.joinToString("")
                        val line = "$date | $kind | ${item.category.take(18)} | $amount | ${item.note.take(18)}"
                        canvas.drawText(line.take(100), 555f, y, paint)
                        y += 20f
                    }
                    document.finishPage(page)
                }
                document.writeTo(stream)
            } finally {
                document.close()
            }
        }
    }

    fun writeXlsx(output: OutputStream, transactions: List<FinancialTransaction>) {
        val summary = LedgerRules.summarize(transactions)
        ZipOutputStream(output).use { zip ->
            zip.putText("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
            zip.putText("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            zip.putText("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="گزارش مالی" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            zip.putText("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            val rows = mutableListOf<List<String>>()
            rows += listOf("تاریخ", "نوع", "دسته‌بندی", "مبلغ (واحد خرد پول)", "توضیحات")
            rows += listOf("خلاصه", "درآمد", "", summary.incomeMinor.toString(), "")
            rows += listOf("خلاصه", "هزینه", "", summary.expenseMinor.toString(), "")
            rows += listOf("خلاصه", "خالص", "", summary.netMinor.toString(), "")
            transactions.forEach { item ->
                rows += listOf(
                    LocalDate.ofEpochDay(item.occurredAtEpochDay).toString(),
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
