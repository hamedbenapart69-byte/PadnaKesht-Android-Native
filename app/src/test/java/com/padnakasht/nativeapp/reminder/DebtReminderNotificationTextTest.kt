package com.padnakasht.nativeapp.reminder

import org.junit.Assert.assertTrue
import org.junit.Test

class DebtReminderNotificationTextTest {
    @Test fun notificationUsesPersianAggregateValuesOnly() {
        val body = DebtReminderNotificationText.body(12, 345_600)
        assertTrue(body.contains("مشتری"))
        assertTrue(body.contains("تومان"))
        assertTrue(body.contains("۳۴۵٬۶۰۰") || body.contains("۳۴۵,۶۰۰"))
        assertTrue(!body.contains("12"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeCustomerCountIsRejected() { DebtReminderNotificationText.body(-1, 100) }

    @Test(expected = IllegalArgumentException::class)
    fun negativeAmountIsRejected() { DebtReminderNotificationText.body(1, -100) }
}
