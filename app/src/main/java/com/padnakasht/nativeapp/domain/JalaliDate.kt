package com.padnakasht.nativeapp.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Pure Kotlin Jalali/Gregorian conversion for the supported modern Jalali calendar.
 * Stored dates remain Gregorian epoch days; Jalali is an input/display format only.
 */
data class JalaliDate(val year: Int, val month: Int, val day: Int) {
    init {
        require(year in MIN_YEAR until MAX_YEAR) { "Jalali year is outside the supported range" }
        require(month in 1..12) { "Jalali month must be between 1 and 12" }
        require(day in 1..daysInMonth(year, month)) { "Invalid day for Jalali month" }
    }

    fun toGregorian(): LocalDate {
        val start = startOfYear(year)
        val offset = if (month <= 6) {
            (month - 1) * 31 + day - 1
        } else {
            186 + (month - 7) * 30 + day - 1
        }
        return start.plusDays(offset.toLong())
    }

    fun format(persianDigits: Boolean = true): String {
        val ascii = String.format(Locale.ROOT, "%04d/%02d/%02d", year, month, day)
        if (!persianDigits) return ascii
        return ascii.map { char ->
            if (char in '0'..'9') PERSIAN_DIGITS[char - '0'] else char
        }.joinToString("")
    }

    companion object {
        private const val MIN_YEAR = -61
        private const val MAX_YEAR = 3177
        private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"
        private val BREAKS = intArrayOf(
            -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
            1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
        )

        private data class CalendarCalculation(val leap: Int, val gregorianYear: Int, val marchDay: Int)

        fun parse(value: String): LocalDate {
            val normalized = normalizeDigits(value.trim())
            val match = Regex("""^(\d{1,4})[/-](\d{1,2})[/-](\d{1,2})$""").matchEntire(normalized)
                ?: throw IllegalArgumentException("Date must use Jalali YYYY/MM/DD format")
            val year = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val day = match.groupValues[3].toInt()
            return JalaliDate(year, month, day).toGregorian()
        }

        fun fromGregorian(date: LocalDate): JalaliDate {
            var jalaliYear = date.year - 621
            var start = startOfYear(jalaliYear)
            if (date.isBefore(start)) {
                jalaliYear -= 1
                start = startOfYear(jalaliYear)
            }
            val dayOfYear = ChronoUnit.DAYS.between(start, date).toInt()
            val month: Int
            val day: Int
            if (dayOfYear < 186) {
                month = dayOfYear / 31 + 1
                day = dayOfYear % 31 + 1
            } else {
                month = (dayOfYear - 186) / 30 + 7
                day = (dayOfYear - 186) % 30 + 1
            }
            return JalaliDate(jalaliYear, month, day)
        }

        fun format(date: LocalDate, persianDigits: Boolean = true): String =
            fromGregorian(date).format(persianDigits)

        fun isLeapYear(year: Int): Boolean =
            ChronoUnit.DAYS.between(startOfYear(year), startOfYear(year + 1)) == 366L

        private fun daysInMonth(year: Int, month: Int): Int = when (month) {
            in 1..6 -> 31
            in 7..11 -> 30
            12 -> if (isLeapYear(year)) 30 else 29
            else -> throw IllegalArgumentException("Jalali month must be between 1 and 12")
        }

        private fun startOfYear(year: Int): LocalDate {
            val calculation = jalaliCalendar(year)
            return LocalDate.of(calculation.gregorianYear, 3, calculation.marchDay)
        }

        private fun jalaliCalendar(year: Int): CalendarCalculation {
            require(year in MIN_YEAR until MAX_YEAR) { "Jalali year is outside the supported range" }
            val gregorianYear = year + 621
            var leapJ = -14
            var previousBreak = BREAKS[0]
            var jump = 0
            for (index in 1 until BREAKS.size) {
                val nextBreak = BREAKS[index]
                jump = nextBreak - previousBreak
                if (year < nextBreak) break
                leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
                previousBreak = nextBreak
            }
            var n = year - previousBreak
            leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
            if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1

            val leapG = div(gregorianYear, 4) -
                div((div(gregorianYear, 100) + 1) * 3, 4) - 150
            val march = 20 + leapJ - leapG
            if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
            var leap = mod(mod(n + 1, 33) - 1, 4)
            if (leap == -1) leap = 4
            return CalendarCalculation(leap, gregorianYear, march)
        }

        private fun normalizeDigits(value: String): String = buildString(value.length) {
            value.forEach { char ->
                when (char) {
                    in '۰'..'۹' -> append(('0'.code + (char.code - '۰'.code)).toChar())
                    in '٠'..'٩' -> append(('0'.code + (char.code - '٠'.code)).toChar())
                    else -> append(char)
                }
            }
        }

        private fun div(a: Int, b: Int): Int = Math.floorDiv(a, b)
        private fun mod(a: Int, b: Int): Int = a - Math.floorDiv(a, b) * b
    }
}
