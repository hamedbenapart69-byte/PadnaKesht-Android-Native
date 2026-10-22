package com.padnakasht.nativeapp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Persistent primary navigation for the offline PadnaKesht app.
 * Stable labels support accessibility and automated UI tests.
 */
@Composable
fun PadnaBottomNavigation(
    selectedSection: String,
    onSectionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    data class NavigationDestination(val route: String, val label: String, val icon: ImageVector)
    val items = listOf(
        NavigationDestination("overview", "نمای کلی", Icons.Filled.Home),
        NavigationDestination("business", "فروش و انبار", Icons.Filled.Inventory2),
        NavigationDestination("reports", "گزارش‌ها", Icons.Filled.Assessment)
    )
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        items.forEach { item ->
            val selected = selectedSection == item.route
            val iconScale by animateFloatAsState(
                targetValue = if (selected) 1.08f else 1f,
                animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
                label = "navigation-icon-scale"
            )
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSectionSelected(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                    )
                },
                label = { Text(text = item.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
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
