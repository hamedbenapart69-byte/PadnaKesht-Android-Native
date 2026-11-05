package com.padnakasht.nativeapp.domain

import com.padnakasht.nativeapp.ui.formatScaledQuantity
import com.padnakasht.nativeapp.ui.parseScaledQuantity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScaledQuantityTest {
    @Test fun parsesPersianAndAsciiFractionalQuantitiesExactly() {
        assertEquals(500L, parseScaledQuantity("۰٫۵", 1_000L))
        assertEquals(1_250L, parseScaledQuantity("1.25", 1_000L))
        assertEquals(-250L, parseScaledQuantity("-0.25", 1_000L))
    }

    @Test fun rejectsPrecisionThatCannotBeRepresentedAndInvalidValues() {
        assertNull(parseScaledQuantity("0.0001", 1_000L))
        assertNull(parseScaledQuantity("1.5", 1L))
        assertNull(parseScaledQuantity("", 1_000L))
        assertNull(parseScaledQuantity("abc", 1_000L))
    }

    @Test fun formatsScaledValuesAndCalculatesRoundedTomanLineTotals() {
        assertEquals("۰٫۵", formatScaledQuantity(500L, 1_000L))
        assertEquals("۲", formatScaledQuantity(2L, 1L))
        assertEquals(650_000L, calculateScaledAmount(500L, 1_300_000L, 1_000L))
        assertEquals(1L, calculateScaledAmount(1L, 1L, 2L))
    }
}
