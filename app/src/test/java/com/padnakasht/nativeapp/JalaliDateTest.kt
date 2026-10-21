package com.padnakasht.nativeapp

import com.padnakasht.nativeapp.domain.JalaliDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JalaliDateTest {
    @Test
    fun convertsKnownNowruzDates() {
        assertEquals(LocalDate.of(2024, 3, 20), JalaliDate(1403, 1, 1).toGregorian())
        assertEquals(LocalDate.of(2025, 3, 21), JalaliDate(1404, 1, 1).toGregorian())
        assertEquals(LocalDate.of(2026, 3, 21), JalaliDate(1405, 1, 1).toGregorian())
    }

    @Test
    fun validatesLeapDayAtEndOfYear() {
        assertTrue(JalaliDate.isLeapYear(1403))
        assertEquals(LocalDate.of(2025, 3, 20), JalaliDate(1403, 12, 30).toGregorian())
        assertFalse(JalaliDate.isLeapYear(1404))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            JalaliDate(1404, 12, 30)
        }
    }

    @Test
    fun parsesPersianAndArabicDigitsAndFormatsBack() {
        val expected = LocalDate.of(2025, 3, 21)
        assertEquals(expected, JalaliDate.parse("۱۴۰۴/۰۱/۰۱"))
        assertEquals(expected, JalaliDate.parse("١٤٠٤-٠١-٠١"))
        assertEquals("۱۴۰۴/۰۱/۰۱", JalaliDate.format(expected))
        assertEquals("1404/01/01", JalaliDate.format(expected, persianDigits = false))
    }

    @Test
    fun roundTripsDatesAroundMonthAndYearBoundaries() {
        val dates = listOf(
            LocalDate.of(2024, 3, 19),
            LocalDate.of(2024, 3, 20),
            LocalDate.of(2024, 9, 21),
            LocalDate.of(2025, 3, 20),
            LocalDate.of(2025, 3, 21),
            LocalDate.of(2026, 3, 20),
            LocalDate.of(2026, 3, 21)
        )
        dates.forEach { date ->
            assertEquals(date, JalaliDate.fromGregorian(date).toGregorian())
        }
    }

    @Test
    fun rejectsImpossibleDatesAndWrongFormat() {
        listOf("1404/13/01", "1404/00/01", "1404/07/31", "1404/12/30", "1404-01", "not-a-date")
            .forEach { value ->
                org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                    JalaliDate.parse(value)
                }
            }
    }
}
