package com.padnakasht.nativeapp.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A lightweight glass surface: translucent layered fills and a fine highlight border.
 * Intentionally avoids per-frame backdrop blur so it remains smooth on mid-range devices.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .shadow(8.dp, shape, ambientColor = Color(0x33116B55), spotColor = Color(0x22116B55))
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.84f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.93f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.95f), MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), Color.White.copy(alpha = 0.40f))
                ),
                shape = shape
            )
            .padding(16.dp),
        content = content
    )
}

/** Premium brand panel used by the live dashboard, not a static mockup. */
@Composable
fun PremiumBrandPanel(
    dateLabel: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, shape, ambientColor = Color(0x44116B55), spotColor = Color(0x33116B55))
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF0A4539), Color(0xFF176B55), Color(0xFF2B8A70), Color(0xFF124C43))
                )
            )
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = .62f), Color.White.copy(alpha = .10f), Color(0xFFC9F5E2).copy(alpha = .55f))), shape)
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("از دل طبیعت دنا", style = MaterialTheme.typography.labelMedium, color = Color(0xFFD6F4E6))
                    Text("پادناکِشت", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text("دفتر مالی کسب‌وکار شما", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .88f))
                }
                Surface(
                    color = Color.White.copy(alpha = .12f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.border(1.dp, Color.White.copy(alpha = .28f), RoundedCornerShape(20.dp))
                ) {
                    Text("PK", modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = Color.White.copy(alpha = .13f), contentColor = Color.White, shape = RoundedCornerShape(50)) {
                    Text("● آفلاین و محلی", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                }
                Text(dateLabel, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .94f))
            }
        }
    }
}

/** Brand-led section title keeps dense accounting screens consistent and scannable. */
@Composable
fun SectionHeading(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(
            Modifier.width(5.dp).height(if (subtitle == null) 30.dp else 44.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 8.dp, bottomEnd = 8.dp))
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Legacy API retained for existing call sites. */
@Composable
fun PadnaBrandHeader(
    eyebrow: String = "از دل طبیعت دنا",
    title: String = "پادناکِشت",
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(eyebrow, style = MaterialTheme.typography.labelMedium)
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
