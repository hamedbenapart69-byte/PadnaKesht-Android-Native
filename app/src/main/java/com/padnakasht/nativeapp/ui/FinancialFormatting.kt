package com.padnakasht.nativeapp.ui

import java.text.NumberFormat
import java.util.Locale

/** Converts Persian and Arabic-Indic numerals to ASCII digits for safe numeric parsing. */
fun normalizePersianDigits(value: String): String = buildString(value.length) {
    value.forEach { char ->
        val digit = when (char) {
            in '\u06F0'..'\u06F9' -> char.code - 0x06F0
            in '\u0660'..'\u0669' -> char.code - 0x0660
            else -> -1
        }
        append(if (digit >= 0) ('0'.code + digit).toChar() else char)
    }
}

/** Localized integer grouping for quantities, IDs, and raw values. */
fun formatFinancialAmount(value: Long): String =
    NumberFormat.getIntegerInstance(Locale("fa", "IR")).format(value)

/** User-facing monetary display: business prices and ledger amounts are entered in toman. */
fun formatToman(value: Long): String = "${formatFinancialAmount(value)} تومان"
/** Parses a decimal quantity into exact integer sub-units without silently rounding input. */
fun parseScaledQuantity(value: String, scale: Long): Long? = runCatching {
    require(scale > 0L)
    val normalized = normalizePersianDigits(value)
        .replace('٫', '.')
        .replace(",", "")
        .replace("٬", "")
        .trim()
    require(normalized.matches(Regex("""-?(?:\d+(?:\.\d*)?|\.\d+)""")))
    java.math.BigDecimal(normalized)
        .multiply(java.math.BigDecimal.valueOf(scale))
        .setScale(0, java.math.RoundingMode.UNNECESSARY)
        .longValueExact()
}.getOrNull()

/** Formats stored sub-units as a concise Persian decimal quantity. */
fun formatScaledQuantity(value: Long, scale: Long): String {
    require(scale > 0L)
    val plain = java.math.BigDecimal.valueOf(value)
        .divide(java.math.BigDecimal.valueOf(scale))
        .stripTrailingZeros()
        .toPlainString()
    return buildString(plain.length) {
        plain.forEach { char ->
            append(
                when (char) {
                    in '0'..'9' -> ('\u06F0'.code + (char.code - '0'.code)).toChar()
                    '.' -> '٫'
                    '-' -> '−'
                    else -> char
                }
            )
        }
    }
}
