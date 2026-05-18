package com.example.smartstock.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.smartstock.data.entity.InventoryItem
import com.example.smartstock.ui.DashboardViewModel
import com.example.smartstock.ui.InventoryViewModel
import com.example.smartstock.ui.adaptive.AdaptiveInfo
import com.example.smartstock.ui.adaptive.AdaptiveScreenScaffold
import com.example.smartstock.ui.adaptive.SmartStockDimens
import com.example.smartstock.ui.components.AppEmptyStateCard
import com.example.smartstock.ui.components.ConnectivityStatusBadge
import com.example.smartstock.ui.components.DashboardChartsCard
import com.example.smartstock.ui.components.StatusChip
import com.example.smartstock.ui.navigation.Screen
import com.example.smartstock.ui.theme.Elevation
import com.example.smartstock.ui.theme.Green
import com.example.smartstock.ui.theme.Orange
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.util.Locale

@Composable
fun DashboardScreen(
    navController: NavController,
    inventoryViewModel: InventoryViewModel,
    viewModel: DashboardViewModel,
    adaptiveInfo: AdaptiveInfo
) {
    val allItems by viewModel.allItems.collectAsStateWithLifecycle()
    val availableItems by viewModel.availableItems.collectAsStateWithLifecycle()
    val inUseItems by viewModel.inUseItems.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val currentRole by inventoryViewModel.currentUserRole.collectAsStateWithLifecycle()
    val loggedInUser by inventoryViewModel.loggedInUser.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Time-of-day greeting + first name + team for the header subtitle.
    val headerGreeting = remember(loggedInUser) {
        val hour = java.util.Calendar.getInstance()
            .get(java.util.Calendar.HOUR_OF_DAY)
        val partOfDay = when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
        val firstName = loggedInUser?.name?.trim()
            ?.substringBefore(' ')
            ?.takeIf { it.isNotBlank() }
        val team = loggedInUser?.teamName?.trim()?.takeIf { it.isNotBlank() }
        buildString {
            append(partOfDay)
            if (firstName != null) append(", ").append(firstName)
            if (team != null) append(" · ").append(team)
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var lastScanned by remember { mutableStateOf<String?>(null) }
    var showScanResolutionDialog by remember { mutableStateOf(false) }
    var showScannedItemDialog by remember { mutableStateOf(false) }
    var showScannedItemEditDialog by remember { mutableStateOf(false) }
    var showCreateFromScanDialog by remember { mutableStateOf(false) }

    val scannedCode = normalizeAssetCode(lastScanned)
    val matchedScannedItem = remember(scannedCode, allItems) {
        if (scannedCode.isBlank()) {
            null
        } else {
            allItems.firstOrNull { item ->
                normalizeAssetCode(item.assetCode) == scannedCode
            }
        }
    }

    val scanner = remember(context) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .allowManualInput()
            .build()
        GmsBarcodeScanning.getClient(context, options)
    }

    fun doScan() {
        lastScanned = null
        showScanResolutionDialog = false
        showScannedItemDialog = false
        showScannedItemEditDialog = false
        showCreateFromScanDialog = false
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val scannedValue = barcode.displayValue ?: barcode.rawValue.orEmpty()
                lastScanned = scannedValue
                showScanResolutionDialog = true
                Toast.makeText(context, "Scanned: $scannedValue", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { error ->
                Toast.makeText(context, "Scan failed: ${error.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnCanceledListener {
                Toast.makeText(context, "Scan cancelled", Toast.LENGTH_SHORT).show()
            }
    }

    AdaptiveScreenScaffold(
        title = "SmartStock+",
        subtitle = headerGreeting,
        showTopBar = !adaptiveInfo.isTwoPane,
        actions = { ConnectivityStatusBadge(isOnline = isOnline, syncState = syncState) }
    ) { paddingValues ->
        if (showAddDialog) {
            AddEditItemDialog(
                viewModel = inventoryViewModel,
                dashboardViewModel = viewModel,
                onDismissRequest = { showAddDialog = false },
                onSubmitSuccess = { showAddDialog = false }
            )
        }

        if (showScanResolutionDialog && scannedCode.isNotBlank()) {
            AlertDialog(
                onDismissRequest = { showScanResolutionDialog = false },
                title = { Text("Scanned Code") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(scannedCode)
                        if (matchedScannedItem != null) {
                            Text("Matched item: ${matchedScannedItem.name}")
                            Text(
                                text = "Category: ${matchedScannedItem.category} | Available: ${matchedScannedItem.availableQuantity}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text("No item is linked to this code yet.")
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showScanResolutionDialog = false
                            if (matchedScannedItem != null) {
                                showScannedItemDialog = true
                            } else {
                                if (currentRole.canAddItem) {
                                    showCreateFromScanDialog = true
                                } else {
                                    Toast.makeText(context, "Only Admin can create items from scan", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text(if (matchedScannedItem != null) "Open Item" else "Create Item")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showScanResolutionDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showScannedItemDialog && matchedScannedItem != null) {
            ItemDetailDialog(
                viewModel = inventoryViewModel,
                itemId = matchedScannedItem.id,
                onDismiss = { showScannedItemDialog = false },
                onEdit = {
                    showScannedItemDialog = false
                    showScannedItemEditDialog = true
                },
                onDeleted = {
                    showScannedItemDialog = false
                    lastScanned = null
                }
            )
        }

        if (showScannedItemEditDialog && matchedScannedItem != null) {
            AddEditItemDialog(
                viewModel = inventoryViewModel,
                dashboardViewModel = viewModel,
                itemId = matchedScannedItem.id,
                onDismissRequest = { showScannedItemEditDialog = false },
                onSubmitSuccess = { showScannedItemEditDialog = false }
            )
        }

        if (showCreateFromScanDialog && scannedCode.isNotBlank()) {
            AddEditItemDialog(
                viewModel = inventoryViewModel,
                dashboardViewModel = viewModel,
                prefilledAssetCode = scannedCode,
                onDismissRequest = { showCreateFromScanDialog = false },
                onSubmitSuccess = { showCreateFromScanDialog = false }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(SmartStockDimens.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(SmartStockDimens.paneSpacing)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SmartStockDimens.sectionSpacing)
            ) {
                DashboardSummaryRow(
                    allItemsCount = allItems.size,
                    availableCount = availableItems.size,
                    inUseCount = inUseItems.size
                )
                QuickActions(
                    navController = navController,
                    onScan = ::doScan,
                    onAddItem = { showAddDialog = true },
                    canAddItem = currentRole.canAddItem
                )

                if (!adaptiveInfo.isTwoPane) {
                    DashboardChartsCard(items = allItems)
                    RecentItemsSection(
                        items = allItems,
                        onViewAll = { navController.navigate(Screen.Inventory.route) },
                        bottomPadding = 0.dp
                    )
                }
            }

            if (adaptiveInfo.isTwoPane) {
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(SmartStockDimens.sectionSpacing)
                ) {
                    DashboardChartsCard(items = allItems)
                    RecentItemsSection(
                        items = allItems,
                        onViewAll = { navController.navigate(Screen.Inventory.route) },
                        bottomPadding = 0.dp
                    )
                }
            }
        }
    }
}

private fun normalizeAssetCode(value: String?): String {
    return value
        ?.trim()
        ?.replace("\\s+".toRegex(), "")
        ?.uppercase(Locale.ROOT)
        .orEmpty()
}

@Composable
private fun DashboardSummaryRow(
    allItemsCount: Int,
    availableCount: Int,
    inUseCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DashboardCard(
            title = "Total\nItems",
            count = allItemsCount.toString(),
            icon = Icons.Outlined.Inventory2,
            iconTint = MaterialTheme.colorScheme.primary,
            iconBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            supportingText = "Tracked assets",
            modifier = Modifier.weight(1f)
        )
        DashboardCard(
            title = "Available",
            count = availableCount.toString(),
            icon = Icons.Outlined.Check,
            iconTint = Green,
            iconBg = Green.copy(alpha = 0.1f),
            supportingText = "Ready to use",
            modifier = Modifier.weight(1f)
        )
        DashboardCard(
            title = "In-\nUse",
            count = inUseCount.toString(),
            icon = Icons.Outlined.Schedule,
            iconTint = Orange,
            iconBg = Orange.copy(alpha = 0.1f),
            supportingText = "Checked out",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickActions(
    navController: NavController,
    onScan: () -> Unit,
    onAddItem: () -> Unit,
    canAddItem: Boolean
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.level2),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Add items, scan codes, and review damaged stock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAddItem,
                    enabled = canAddItem,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Item")
                }

                OutlinedButton(
                    onClick = onScan,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Icon(
                        Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan")
                }
            }

            OutlinedButton(
                onClick = { navController.navigate(Screen.Inventory.route) },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Inventory and review damaged items")
            }

            OutlinedButton(
                onClick = { navController.navigate(Screen.StockTake.route) },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.FactCheck,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start a stock-take session")
            }
        }
    }
}

@Composable
private fun RecentItemsSection(
    modifier: Modifier = Modifier,
    items: List<InventoryItem>,
    onViewAll: () -> Unit,
    bottomPadding: Dp = 0.dp
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Items",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onViewAll) {
                Text("View All", color = MaterialTheme.colorScheme.primary)
            }
        }

        if (items.isEmpty()) {
            AppEmptyStateCard(
                icon = Icons.Outlined.Inventory2,
                title = "No recent items",
                subtitle = "Newly added or updated inventory records will appear here for quick review."
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items.take(5).forEach { item ->
                    RecentItemCard(item)
                }
            }
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    count: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    supportingText: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.level2),
        modifier = modifier.height(90.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = iconTint
                    )
                }
            }
            Text(
                text = count,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RecentItemCard(item: InventoryItem) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.level1),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    ) {
                        Text(
                            text = item.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                StatusChip(status = item.status)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardMetricChip(
                    label = "Total",
                    value = item.quantity.toString(),
                    modifier = Modifier.weight(1f)
                )
                DashboardMetricChip(
                    label = "Available",
                    value = item.availableQuantity.toString(),
                    modifier = Modifier.weight(1f)
                )
                DashboardMetricChip(
                    label = "In Use",
                    value = item.inUseQuantity.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DashboardMetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
