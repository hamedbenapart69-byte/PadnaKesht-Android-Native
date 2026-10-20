package com.padnakasht.nativeapp.domain

object OfflineInsights {
    fun categorize(note: String): String {
        val text = note.trim().lowercase()
        return when {
            listOf("سوخت", "بنزین", "گازوئیل", "fuel").any(text::contains) -> "سوخت"
            listOf("حقوق", "دستمزد", "salary", "payroll").any(text::contains) -> "حقوق"
            listOf("فروش", "فاکتور", "دریافت", "sale").any(text::contains) -> "فروش"
            listOf("خرید", "تأمین", "تامین", "purchase").any(text::contains) -> "خرید"
            listOf("اجاره", "rent").any(text::contains) -> "اجاره"
            else -> "سایر"
        }
    }

    /** Simple baseline, not a trained ML model: mean of the last up to 3 daily net totals. */
    fun forecastNextPeriod(dailyNetMinor: List<Long>): Long? {
        if (dailyNetMinor.isEmpty()) return null
        val window = dailyNetMinor.takeLast(3)
        return window.sumOf { it.toBigDecimal() }
            .divide(window.size.toBigDecimal(), 0, java.math.RoundingMode.HALF_UP).longValueExact()
    }
}
