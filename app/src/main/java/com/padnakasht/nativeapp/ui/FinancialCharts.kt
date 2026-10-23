package com.padnakasht.nativeapp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.padnakasht.nativeapp.domain.FinancialTransaction
import com.padnakasht.nativeapp.domain.LedgerRules
import com.padnakasht.nativeapp.domain.TransactionKind
import kotlin.math.abs
import kotlin.math.max

private const val BAR_HINT = "برای دیدن جزئیات، روی یک ستون بزنید"

/**
 * Ledger charts drawn from stored transactions only. Compared with the first version they
 * (1) describe their real values to screen readers, (2) show date/category/amount when a bar is
 * tapped, (3) run time right-to-left in RTL, and (4) reveal with a short animation that is
 * skipped when the system animation scale is zero. No accounting values are computed here.
 */
@Composable
fun FinancialCharts(transactions: List<FinancialTransaction>) {
    if (transactions.isEmpty()) {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("هنوز داده‌ای برای تحلیل وجود ندارد", style = MaterialTheme.typography.titleMedium)
                Text("پس از ثبت چند درآمد یا هزینه، نمودارهای واقعی این بخش فعال می‌شوند.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    val summary = remember(transactions) { LedgerRules.summarize(transactions) }
    val recent = remember(transactions) { recentChartTransactions(transactions) }
    val netSeries = remember(recent) { cumulativeNetSeries(recent) }
    val maxAmount = remember(recent) { max(1.0, recent.maxOf { abs(it.amountMinor.toDouble()) }).toFloat() }
    val scheme = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val animate = remember(context) { animationsEnabled(context) }
    val reveal = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (animate) reveal.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = recent.firstOrNull { it.id == selectedId }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("ترکیب درآمد و هزینه", style = MaterialTheme.typography.titleMedium)
                Canvas(
                    Modifier.fillMaxWidth().height(150.dp)
                        .testTag("donut-chart")
                        .semantics {
                            role = Role.Image
                            contentDescription = donutChartDescription(summary.incomeMinor, summary.expenseMinor)
                        }
                ) {
                    val total = summary.incomeMinor.toDouble() + summary.expenseMinor.toDouble()
                    val diameter = minOf(size.height - 20f, size.width - 20f)
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                    val chartSize = Size(diameter, diameter)
                    val ring = Stroke(width = diameter * 0.19f, cap = StrokeCap.Butt)
                    drawArc(scheme.surfaceVariant, -90f, 360f, false, topLeft, chartSize, style = ring)
                    if (total > 0.0) {
                        val incomeSweep = (summary.incomeMinor.toDouble() / total * 360.0).toFloat()
                        drawArc(scheme.primary, -90f, incomeSweep * reveal.value, false, topLeft, chartSize, style = ring)
                        if (summary.expenseMinor > 0L) {
                            drawArc(scheme.error, -90f + incomeSweep, (360f - incomeSweep) * reveal.value, false, topLeft, chartSize, style = ring)
                        }
                    }
                }
                Text("درآمد: ${formatFinancialAmount(summary.incomeMinor)}  •  هزینه: ${formatFinancialAmount(summary.expenseMinor)}")
            }
        }
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("هفت تراکنش اخیر", style = MaterialTheme.typography.titleMedium)
                Canvas(
                    Modifier.fillMaxWidth().height(130.dp)
                        .testTag("bar-chart")
                        .semantics {
                            role = Role.Image
                            contentDescription = barChartDescription(recent)
                        }
                        .pointerInput(recent, rtl) {
                            detectTapGestures { offset ->
                                chartIndexAt(offset.x, size.width.toFloat(), recent.size, rtl)?.let { index ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val tappedId = recent[index].id
                                    selectedId = if (selectedId == tappedId) null else tappedId
                                }
                            }
                        }
                ) {
                    val baseline = size.height * 0.86f
                    drawLine(scheme.outline, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 2f)
                    val slotWidth = size.width / recent.size
                    recent.forEachIndexed { index, item ->
                        val slot = chartSlot(index, recent.size, rtl)
                        val barHeight = abs(item.amountMinor.toFloat()) / maxAmount * size.height * 0.72f * reveal.value
                        val topLeft = Offset(slot * slotWidth + slotWidth * 0.18f, baseline - barHeight)
                        val barSize = Size(slotWidth * 0.64f, barHeight)
                        val base = if (item.kind == TransactionKind.INCOME) scheme.primary else scheme.error
                        val isSelected = item.id == selectedId
                        drawRect(
                            color = if (selectedId != null && !isSelected) base.copy(alpha = 0.45f) else base,
                            topLeft = topLeft,
                            size = barSize
                        )
                        if (isSelected) drawRect(scheme.onSurface, topLeft, barSize, style = Stroke(width = 3f))
                    }
                }
                Text(
                    text = selected?.let(::transactionDetailText) ?: BAR_HINT,
                    modifier = Modifier.testTag("bar-detail"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected != null) scheme.onSurface else scheme.onSurfaceVariant
                )
                Text("درآمد با رنگ اصلی • هزینه با رنگ هشدار", color = scheme.onSurfaceVariant)
            }
        }
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("روند خالص تجمعی", style = MaterialTheme.typography.titleMedium)
                Canvas(
                    Modifier.fillMaxWidth().height(130.dp)
                        .testTag("trend-chart")
                        .semantics {
                            role = Role.Image
                            contentDescription = trendChartDescription(netSeries)
                        }
                ) {
                    if (netSeries.size >= 2) {
                        val low = netSeries.min().toDouble()
                        val high = netSeries.max().toDouble()
                        val span = max(1.0, high - low)
                        val points = netSeries.mapIndexed { index, value ->
                            val fraction = index.toFloat() / (netSeries.size - 1).toFloat()
                            Offset(
                                x = (if (rtl) 1f - fraction else fraction) * size.width,
                                y = size.height - ((value.toDouble() - low) / span * (size.height - 12f)).toFloat() - 6f
                            )
                        }
                        val path = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        val visible = size.width * reveal.value
                        clipRect(left = if (rtl) size.width - visible else 0f, right = if (rtl) size.width else visible) {
                            drawPath(path, scheme.primary, style = Stroke(width = 3f))
                            points.forEach { drawCircle(scheme.primary, radius = 4f, center = it) }
                        }
                    } else {
                        drawLine(scheme.outlineVariant, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 2f)
                    }
                }
                Text("مبنای نمودار: حداکثر ۷ تراکنش اخیر")
            }
        }
    }
}
