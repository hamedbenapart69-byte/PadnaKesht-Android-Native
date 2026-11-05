package com.padnakasht.nativeapp.reminder

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object DebtReminderScheduler {
    private const val PREFS = "local_debt_reminders"
    private const val ENABLED = "enabled"
    private const val PERIODIC_NAME = "padnakasht-overdue-debt-daily"
    private const val IMMEDIATE_NAME = "padnakasht-overdue-debt-immediate"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, false)

    fun enable(context: Context) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, true).apply()
        val periodic = PeriodicWorkRequestBuilder<OverdueDebtReminderWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.NONE)
            .build()
        val immediate = OneTimeWorkRequestBuilder<OverdueDebtReminderWorker>()
            .setConstraints(Constraints.NONE)
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, periodic)
        WorkManager.getInstance(app).enqueueUniqueWork(IMMEDIATE_NAME, ExistingWorkPolicy.REPLACE, immediate)
    }

    fun disable(context: Context) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, false).apply()
        WorkManager.getInstance(app).cancelUniqueWork(PERIODIC_NAME)
        WorkManager.getInstance(app).cancelUniqueWork(IMMEDIATE_NAME)
        androidx.core.app.NotificationManagerCompat.from(app).cancel(OverdueDebtReminderWorker.NOTIFICATION_ID)
    }
}
