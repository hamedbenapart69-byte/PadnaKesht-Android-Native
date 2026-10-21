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
