package com.padnakasht.nativeapp.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.padnakasht.nativeapp.R
import com.padnakasht.nativeapp.data.local.AppDatabaseFactory
import com.padnakasht.nativeapp.domain.OverdueDebtReminderRules
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

internal object DebtReminderNotificationText {
    fun title(): String = "یادآوری مطالبات پادناکِشت"

    fun body(customerCount: Int, amountMinor: Long): String {
        require(customerCount >= 0)
        require(amountMinor >= 0L)
        val numbers = NumberFormat.getIntegerInstance(Locale("fa", "IR"))
        val customers = numbers.format(customerCount)
        val formatted = numbers.format(amountMinor)
        return "$customers مشتری • $formatted تومان"
    }
}

class OverdueDebtReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!DebtReminderScheduler.isEnabled(applicationContext)) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val database = runCatching { AppDatabaseFactory.open(applicationContext) }.getOrElse {
            return Result.retry()
        }
        return try {
            val today = LocalDate.now().toEpochDay()
            val cutoff = Math.subtractExact(today, 30L)
            val candidates = database.businessDao().getOverdueInvoiceCandidates(cutoff)
            val summary = OverdueDebtReminderRules.summarize(candidates, today)
            if (summary.invoiceCount > 0) {
                val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    manager.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID, "یادآوری بدهی‌ها", NotificationManager.IMPORTANCE_DEFAULT)
                    )
                }
                val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(DebtReminderNotificationText.title())
                    .setContentText(DebtReminderNotificationText.body(summary.customerCount, summary.totalOutstandingMinor))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(
                        DebtReminderNotificationText.body(summary.customerCount, summary.totalOutstandingMinor)
                    ))
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .build()
                NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
            } else {
                NotificationManagerCompat.from(applicationContext).cancel(NOTIFICATION_ID)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            database.close()
        }
    }

    companion object {
        const val CHANNEL_ID = "padnakasht_overdue_debt"
        const val NOTIFICATION_ID = 30131
    }
}
