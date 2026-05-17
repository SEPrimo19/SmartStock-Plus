package com.example.smartstock.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartstock.data.entity.ItemHistory
import com.example.smartstock.ui.DashboardViewModel
import com.example.smartstock.ui.InventoryViewModel
import com.example.smartstock.ui.adaptive.AdaptiveScreenScaffold
import com.example.smartstock.ui.components.AppEmptyStateCard
import com.example.smartstock.ui.components.ConnectivityStatusBadge
import com.example.smartstock.ui.components.SkeletonList
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: InventoryViewModel,
    dashboardViewModel: DashboardViewModel,
    adaptiveInfo: com.example.smartstock.ui.adaptive.AdaptiveInfo
) {
    val histories by viewModel.allHistory.collectAsStateWithLifecycle()
    val isHistoryLoading by viewModel.isHistoryLoading.collectAsStateWithLifecycle()
    val items by dashboardViewModel.allItems.collectAsStateWithLifecycle()
    val isOnline by dashboardViewModel.isOnline.collectAsStateWithLifecycle()
    val syncState by dashboardViewModel.syncState.collectAsStateWithLifecycle()
    val itemNameById = remember(items) { items.associateBy({ it.id }, { it.name }) }
    val groupedHistory = remember(histories) { histories.groupBy { formatDateHeader(it.timestamp) } }
    val mostRecentTimestamp = histories.firstOrNull()?.timestamp

    AdaptiveScreenScaffold(
        title = "History",
        showTopBar = !adaptiveInfo.isTwoPane,
        actions = { ConnectivityStatusBadge(isOnline = isOnline, syncState = syncState) }
    ) { paddingValues ->
        if (isHistoryLoading && histories.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkeletonList(itemCount = 6, modifier = Modifier.fillMaxWidth())
            }
            return@AdaptiveScreenScaffold
        }

        if (histories.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AppEmptyStateCard(
                    icon = Icons.Default.History,
                    title = "No history yet",
                    subtitle = "Item changes, check-outs, returns, and updates will appear here once activity starts."
                )
            }
            return@AdaptiveScreenScaffold
        }

        PullToRefreshBox(
            isRefreshing = syncState == com.example.smartstock.core.sync.SyncState.SYNCING,
            onRefresh = { dashboardViewModel.requestSync() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                state = rememberLazyListState()
            ) {
                item {
                    HistorySummaryCard(
                        totalEvents = histories.size,
                        lastUpdated = mostRecentTimestamp?.let(::formatDateTime).orEmpty()
                    )
                }

                groupedHistory.entries.forEach { entry ->
                    val dateHeader = entry.key
                    val dayEntries = entry.value

                    item {
                        HistoryDateHeader(
                            title = dateHeader,
                            count = dayEntries.size
                        )
                    }

                    items(dayEntries, key = { it.historyId }) { history ->
                        val itemName = itemNameById[history.itemId] ?: "Item #${history.itemId}"
                        HistoryEventCard(
                            itemName = itemName,
                            action = history.action,
                            details = history.details,
                            timestamp = formatTimeOnly(history.timestamp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySummaryCard(
    totalEvents: Int,
    lastUpdated: String
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Inventory Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Track every local item change and status movement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = totalEvents.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "events",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (lastUpdated.isNotBlank()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Text(
                text = "Last recorded: $lastUpdated",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun HistoryDateHeader(
    title: String,
    count: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$count event${if (count == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryEventCard(
    itemName: String,
    action: String,
    details: String,
    timestamp: String,
    modifier: Modifier = Modifier
) {
    val actionVisual = historyActionVisual(action)

    Card(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = actionVisual.containerColor,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = actionVisual.icon,
                        contentDescription = null,
                        tint = actionVisual.contentColor
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = itemName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = timestamp,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text(action) }
                    )
                }

                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class HistoryActionVisual(
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color
)

@Composable
private fun historyActionVisual(action: String): HistoryActionVisual {
    val lowerAction = action.lowercase(Locale.ROOT)
    return when {
        "delete" in lowerAction -> HistoryActionVisual(
            icon = Icons.Default.Delete,
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.error
        )

        "return" in lowerAction -> HistoryActionVisual(
            icon = Icons.AutoMirrored.Filled.KeyboardReturn,
            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
            contentColor = MaterialTheme.colorScheme.tertiary
        )

        "checked out" in lowerAction || "use" in lowerAction -> HistoryActionVisual(
            icon = Icons.AutoMirrored.Filled.Logout,
            containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
            contentColor = MaterialTheme.colorScheme.secondary
        )

        "update" in lowerAction -> HistoryActionVisual(
            icon = Icons.Default.BuildCircle,
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.primary
        )

        else -> HistoryActionVisual(
            icon = Icons.Default.AddCircle,
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

private fun formatDateHeader(timestamp: Long): String {
    return SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
}

private fun formatDateTime(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun formatTimeOnly(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}
