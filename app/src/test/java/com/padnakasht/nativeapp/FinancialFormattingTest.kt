package com.padnakasht.nativeapp

import com.padnakasht.nativeapp.ui.formatFinancialAmount
import com.padnakasht.nativeapp.ui.formatToman
import com.padnakasht.nativeapp.ui.normalizePersianDigits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialFormattingTest {
    @Test
    fun normalizesPersianAndArabicIndicDigits() {
        assertEquals("0123456789", normalizePersianDigits("۰۱۲۳۴۵۶۷۸۹"))
        assertEquals("0123456789", normalizePersianDigits("٠١٢٣٤٥٦٧٨٩"))
    }

    @Test
    fun preservesNonNumericCharactersWhileNormalizingDigits() {
        assertEquals("1405/07/29", normalizePersianDigits("۱۴۰۵/۰۷/۲۹"))
        assertEquals("قسط 25", normalizePersianDigits("قسط ٢٥"))
    }

    @Test
    fun financialAmountUsesGroupedLocalizedDigits() {
        val formatted = formatFinancialAmount(1_234_567L)
        assertEquals(7, normalizePersianDigits(formatted).count(Char::isDigit))
        assertTrue(formatted.length >= 7)
    }

    @Test
    fun formatsNegativeAmountsWithoutLosingDigits() {
        val formatted = formatFinancialAmount(-42L)
        assertTrue(formatted.contains("-") || formatted.contains("−"))
        assertEquals(2, normalizePersianDigits(formatted).count(Char::isDigit))
    }

    @Test
    fun monetaryValuesAreExplicitlyLabeledInToman() {
        assertTrue(formatToman(1_234_567L).contains("تومان"))
        assertTrue(formatToman(1_234_567L).contains("۱٬۲۳۴٬۵۶۷"))
    }
}
