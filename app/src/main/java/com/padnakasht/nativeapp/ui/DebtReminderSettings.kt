package com.padnakasht.nativeapp.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.padnakasht.nativeapp.reminder.DebtReminderScheduler

@Composable
fun DebtReminderSettings() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(DebtReminderScheduler.isEnabled(context)) }
    var showPermissionHelp by remember { mutableStateOf(false) }

    fun activate() {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            showPermissionHelp = true
            return
        }
        DebtReminderScheduler.enable(context)
        enabled = true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) activate() else showPermissionHelp = true
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("یادآوری بدهی‌های بیش از ۳۰ روز", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (enabled) "یادآوری روزانه فعال است" else "به‌صورت پیش‌فرض خاموش است",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        if (!checked) {
                            DebtReminderScheduler.disable(context)
                            enabled = false
                        } else if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            activate()
                        }
                    }
                )
            }
            Text(
                "هر روز، بدون اینترنت، فقط تعداد مشتریان و جمع مطالبات سررسیدگذشته را یادآوری می‌کند؛ نام و شماره مشتری در اعلان نمایش داده نمی‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "اجرای اعلان به محدودیت‌های باتری و زمان‌بندی اندروید وابسته است و ممکن است با تأخیر انجام شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showPermissionHelp) {
        AlertDialog(
            onDismissRequest = { showPermissionHelp = false },
            title = { Text("اجازهٔ اعلان لازم است") },
            text = { Text("برای دریافت یادآوری، اعلان‌های پادناکِشت را در تنظیمات اندروید مجاز کنید؛ سپس این گزینه را دوباره روشن کنید.") },
            confirmButton = {
                TextButton(onClick = { showPermissionHelp = false }) { Text("متوجه شدم") }
            }
        )
    }
}
