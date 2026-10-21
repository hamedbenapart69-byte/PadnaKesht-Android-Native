package com.padnakasht.nativeapp.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Persistent primary navigation for the offline PadnaKesht app.
 * Keep section labels stable because they are also used by accessibility and UI tests.
 */
@Composable
fun PadnaBottomNavigation(
    selectedSection: String,
    onSectionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Triple("overview", "⌂", "نمای کلی"),
        Triple("business", "▦", "فروش و انبار"),
        Triple("reports", "▤", "گزارش‌ها")
    )
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        items.forEach { (section, symbol, label) ->
            NavigationBarItem(
                selected = selectedSection == section,
                onClick = { if (selectedSection != section) onSectionSelected(section) },
                icon = {
                    Text(
                        text = symbol,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (selectedSection == section) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = { Text(text = label, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
