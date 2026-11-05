package com.padnakasht.nativeapp.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Product quantities are stored as integer sub-units (for example, 500 grams
 * for 0.5 kg). Prices remain per displayed unit (for example, per kilogram).
 * HALF_UP is the single, explicit toman rounding rule for fractional lines.
 */
fun calculateScaledAmount(quantity: Long, unitPriceMinor: Long, quantityScale: Long): Long {
    require(quantity >= 0L) { "Quantity cannot be negative" }
    require(unitPriceMinor >= 0L) { "Unit price cannot be negative" }
    require(quantityScale > 0L) { "Quantity scale must be positive" }
    return BigDecimal.valueOf(quantity)
        .multiply(BigDecimal.valueOf(unitPriceMinor))
        .divide(BigDecimal.valueOf(quantityScale), 0, RoundingMode.HALF_UP)
        .longValueExact()
}
