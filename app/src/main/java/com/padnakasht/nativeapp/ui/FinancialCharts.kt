package com.padnakasht.nativeapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.padnakasht.nativeapp.domain.FinancialTransaction
import com.padnakasht.nativeapp.domain.LedgerRules
import com.padnakasht.nativeapp.domain.TransactionKind
import kotlin.math.abs
import kotlin.math.max

@Composable
fun FinancialCharts(transactions: List<FinancialTransaction>) {
    val summary = remember(transactions) { LedgerRules.summarize(transactions) }
    val recent = remember(transactions) { transactions.sortedBy { it.occurredAtEpochDay }.takeLast(7) }
    val netSeries = remember(recent) {
        var running = 0L
        recent.map { running = Math.addExact(running, it.signedAmountMinor()); running }
    }
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("ترکیب درآمد و هزینه", style = MaterialTheme.typography.titleMedium)
                Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                    val total = summary.incomeMinor.toDouble() + summary.expenseMinor.toDouble()
                    val diameter = minOf(size.height - 12f, size.width - 12f)
                    val topLeft = Offset((size.width - diameter) / 2f, 6f)
                    val chartSize = Size(diameter, diameter)
                    if (total <= 0.0) {
                        drawArc(scheme.outlineVariant, -90f, 360f, true, topLeft, chartSize)
                    } else {
                        val incomeSweep = (summary.incomeMinor.toDouble() / total * 360.0).toFloat()
                        drawArc(scheme.primary, -90f, incomeSweep, true, topLeft, chartSize)
                        drawArc(scheme.error, -90f + incomeSweep, 360f - incomeSweep, true, topLeft, chartSize)
                    }
                }
                Text("درآمد: ${summary.incomeMinor}  •  هزینه: ${summary.expenseMinor}")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("هفت تراکنش اخیر", style = MaterialTheme.typography.titleMedium)
                Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                    val baseline = size.height * 0.86f
                    drawLine(scheme.outline, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 2f)
                    if (recent.isNotEmpty()) {
                        val maxAbs = max(1.0, recent.maxOf { abs(it.amountMinor.toDouble()) }).toFloat()
                        val slot = size.width / recent.size
                        recent.forEachIndexed { index, item ->
                            val barHeight = abs(item.amountMinor.toFloat()) / maxAbs * size.height * 0.72f
                            val x = index * slot + slot * 0.18f
                            drawRect(
                                color = if (item.kind == TransactionKind.INCOME) scheme.primary else scheme.error,
                                topLeft = Offset(x, baseline - barHeight),
                                size = Size(slot * 0.64f, barHeight)
                            )
                        }
                    }
                }
                Text("سبز/اصلی: درآمد  •  رنگ هشدار: هزینه")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("روند خالص تجمعی", style = MaterialTheme.typography.titleMedium)
                Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                    if (netSeries.size >= 2) {
                        val low = netSeries.minOrNull()!!.toDouble()
                        val high = netSeries.maxOrNull()!!.toDouble()
                        val span = max(1.0, high - low)
                        val points = netSeries.mapIndexed { index, value ->
                            Offset(
                                x = index.toFloat() / (netSeries.size - 1).toFloat() * size.width,
                                y = size.height - ((value.toDouble() - low) / span * (size.height - 12f)).toFloat() - 6f
                            )
                        }
                        val path = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(path, scheme.primary, style = Stroke(width = 3f))
                        points.forEach { drawCircle(scheme.primary, radius = 4f, center = it) }
                    } else {
                        drawLine(scheme.outlineVariant, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 2f)
                    }
                }
                Text("مبنای نمودار: حداکثر ۷ تراکنش اخیر")
            }
        }
    }
}
