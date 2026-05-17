package com.example.smartstock.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.smartstock.data.entity.InventoryItem
import java.util.Locale

private val ChartPalette = listOf(
    Color(0xFF22C55E),
    Color(0xFF3B82F6),
    Color(0xFFF59E0B),
    Color(0xFFEF4444),
    Color(0xFF8B5CF6),
    Color(0xFF06B6D4),
    Color(0xFFEC4899)
)

private data class ChartSegment(
    val label: String,
    val count: Int,
    val color: Color
)

@Composable
fun DashboardChartsCard(
    items: List<InventoryItem>,
    modifier: Modifier = Modifier
) {
    val statusBreakdown = remember(items) {
        items.groupingBy { it.status.ifBlank { "Unspecified" } }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
    }
    val topCategories = remember(items) {
        items.groupingBy { it.category.ifBlank { "Uncategorized" } }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Inventory insights",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    "Distribution by status and top categories.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (items.isEmpty()) {
                Text(
                    "Add items to see charts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            val statusSegments = statusBreakdown.mapIndexed { index, (label, count) ->
                ChartSegment(label, count, statusColor(label, index))
            }
            StatusDonutSection(segments = statusSegments, total = items.size)

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 1.dp
            )

            CategoriesSection(categories = topCategories, total = items.size)
        }
    }
}

@Composable
private fun StatusDonutSection(segments: List<ChartSegment>, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("By status")
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                segments = segments,
                total = total,
                modifier = Modifier.size(140.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                segments.forEach { segment ->
                    LegendRow(segment = segment, total = total)
                }
            }
        }
    }
}

@Composable
private fun DonutChart(
    segments: List<ChartSegment>,
    total: Int,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 750),
        label = "donut-progress"
    )
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val strokeWidth = size.minDimension * 0.18f
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )

            if (total <= 0) return@Canvas
            var startAngle = -90f
            segments.forEach { segment ->
                val sweep = (segment.count.toFloat() / total) * 360f * animatedProgress
                if (sweep > 0f) {
                    drawArc(
                        color = segment.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke
                    )
                }
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = total.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = if (total == 1) "Item" else "Items",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegendRow(segment: ChartSegment, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(segment.color)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = segment.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${segment.count} · ${percent(segment.count, total)}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoriesSection(categories: List<Pair<String, Int>>, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("By category")
        if (categories.isEmpty()) {
            Text(
                "No category data yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }
        val maxCount = categories.maxOf { it.second }.coerceAtLeast(1)
        categories.forEachIndexed { index, (label, count) ->
            val color = ChartPalette[index % ChartPalette.size]
            CategoryRow(
                rank = index + 1,
                label = label,
                count = count,
                total = total,
                fraction = count.toFloat() / maxCount,
                color = color
            )
        }
    }
}

@Composable
private fun CategoryRow(
    rank: Int,
    label: String,
    count: Int,
    total: Int,
    fraction: Float,
    color: Color
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 700),
        label = "category-bar"
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = color
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${percent(count, total)}% of inventory",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = color.copy(alpha = 0.16f)
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = color,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(color.copy(alpha = 0.65f), color)
                        )
                    )
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface
    )
}

private fun statusColor(status: String, fallbackIndex: Int): Color {
    return when (status.lowercase(Locale.getDefault())) {
        "available", "in stock" -> Color(0xFF22C55E)
        "in use", "in-use", "checked out" -> Color(0xFF3B82F6)
        "damaged" -> Color(0xFFEF4444)
        "out of stock" -> Color(0xFFF59E0B)
        "missing" -> Color(0xFF94A3B8)
        "unspecified" -> Color(0xFF64748B)
        else -> ChartPalette[fallbackIndex % ChartPalette.size]
    }
}

private fun percent(part: Int, total: Int): Int {
    if (total == 0) return 0
    return ((part.toDouble() / total) * 100).toInt()
}
