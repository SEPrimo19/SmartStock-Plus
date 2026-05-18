package com.example.smartstock.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.ActivityNotFoundException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import com.example.smartstock.core.export.ReportExporter
import com.example.smartstock.data.entity.ItemUsageRecord
import com.example.smartstock.ui.DashboardViewModel
import com.example.smartstock.ui.InventoryViewModel
import com.example.smartstock.ui.adaptive.AdaptiveInfo
import com.example.smartstock.ui.adaptive.AdaptiveScreenScaffold
import com.example.smartstock.ui.adaptive.DetailPlaceholder
import com.example.smartstock.ui.adaptive.MasterDetailPane
import com.example.smartstock.ui.adaptive.SmartStockDimens
import com.example.smartstock.ui.components.AppEmptyStateCard
import com.example.smartstock.ui.components.AppRefreshBox
import com.example.smartstock.ui.components.ConnectivityStatusBadge
import com.example.smartstock.ui.components.SkeletonList
import com.example.smartstock.ui.theme.Green
import com.example.smartstock.ui.theme.Orange
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class UsageTab(val label: String) {
    Active("Active"),
    Returned("Returned"),
    All("All")
}

private enum class DateFilter(val label: String) {
    ThisMonth("This Month"),
    ThisYear("This Year"),
    AllTime("All Time"),
    Custom("Custom")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun UsageReportScreen(
    viewModel: InventoryViewModel,
    dashboardViewModel: DashboardViewModel,
    adaptiveInfo: AdaptiveInfo
) {
    val allUsageRecords by viewModel.allUsageRecords.collectAsStateWithLifecycle()
    val isUsageLoading by viewModel.isUsageLoading.collectAsStateWithLifecycle()
    val allItems by dashboardViewModel.allItems.collectAsStateWithLifecycle()
    val isOnline by dashboardViewModel.isOnline.collectAsStateWithLifecycle()
    val syncState by dashboardViewModel.syncState.collectAsStateWithLifecycle()

    val itemNameById = remember(allItems) { allItems.associateBy({ it.id }, { it.name }) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var dateFilter by remember { mutableStateOf(DateFilter.AllTime) }
    var searchQuery by remember { mutableStateOf("") }
    var customStartDate by remember { mutableStateOf<Long?>(null) }
    var customEndDate by remember { mutableStateOf<Long?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var selectedRecordId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(adaptiveInfo.isTwoPane) {
        if (!adaptiveInfo.isTwoPane) {
            selectedRecordId = null
        }
    }

    val tabs = UsageTab.entries

    // Filter by tab
    val tabFiltered = when (tabs[selectedTab]) {
        UsageTab.Active -> allUsageRecords.filter { it.status == "Active" }
        UsageTab.Returned -> allUsageRecords.filter { it.status != "Active" }
        UsageTab.All -> allUsageRecords
    }

    // Filter by date range
    val dateFiltered = filterByDate(tabFiltered, dateFilter, customStartDate, customEndDate)

    // Filter by search
    val filteredRecords = if (searchQuery.isBlank()) {
        dateFiltered
    } else {
        val q = searchQuery.lowercase(Locale.ROOT)
        dateFiltered.filter { record ->
            val itemName = itemNameById[record.itemId]?.lowercase(Locale.ROOT).orEmpty()
            itemName.contains(q) ||
                    record.location.lowercase(Locale.ROOT).contains(q) ||
                    record.usedBy.lowercase(Locale.ROOT).contains(q)
        }
    }

    // Summary counts
    val activeCount = allUsageRecords.count { it.status == "Active" }
    val returnedCount = allUsageRecords.count { it.status != "Active" }

    // Date pickers
    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = customStartDate)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    customStartDate = state.selectedDateMillis
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = customEndDate)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    customEndDate = state.selectedDateMillis
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    val selectedRecord = remember(filteredRecords, selectedRecordId) {
        filteredRecords.find { it.id == selectedRecordId }
    }

    val context = LocalContext.current
    val exportSnackbarHostState = remember { SnackbarHostState() }
    var showExportMenu by remember { mutableStateOf(false) }

    val launchShare: (Intent, String) -> Unit = { intent, label ->
        try {
            context.startActivity(Intent.createChooser(intent, "Share $label"))
        } catch (e: ActivityNotFoundException) {
            // No share targets — user will see nothing happen; surface a snackbar.
        }
    }

    AdaptiveScreenScaffold(
        title = "Usage Reports",
        showTopBar = !adaptiveInfo.isTwoPane,
        snackbarHost = { SnackbarHost(exportSnackbarHostState) },
        actions = {
            Box {
                IconButton(
                    onClick = { showExportMenu = true },
                    enabled = filteredRecords.isNotEmpty()
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Export")
                }
                DropdownMenu(
                    expanded = showExportMenu,
                    onDismissRequest = { showExportMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Export as CSV") },
                        onClick = {
                            showExportMenu = false
                            val intent = ReportExporter.exportCsv(context, filteredRecords, itemNameById)
                            launchShare(intent, "CSV")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export as PDF") },
                        onClick = {
                            showExportMenu = false
                            val intent = ReportExporter.exportPdf(context, filteredRecords, itemNameById)
                            launchShare(intent, "PDF")
                        }
                    )
                }
            }
            ConnectivityStatusBadge(isOnline = isOnline, syncState = syncState)
        }
    ) { paddingValues ->
        MasterDetailPane(
            isTwoPane = adaptiveInfo.isTwoPane,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            primaryPane = { paneModifier ->
        AppRefreshBox(
            isSyncing = syncState == com.example.smartstock.core.sync.SyncState.SYNCING,
            onRefresh = { dashboardViewModel.requestSync() },
            modifier = paneModifier.fillMaxSize()
        ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Summary cards (hidden in two-pane — counts already shown in tab labels)
            if (!adaptiveInfo.isTwoPane) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    UsageSummaryCard(
                        title = "Active",
                        count = activeCount,
                        subtitle = "Checked out",
                        icon = Icons.Outlined.Schedule,
                        iconTint = Orange,
                        modifier = Modifier.weight(1f)
                    )
                    UsageSummaryCard(
                        title = "Returned",
                        count = returnedCount,
                        subtitle = "Completed",
                        icon = Icons.Outlined.CheckCircle,
                        iconTint = Green,
                        modifier = Modifier.weight(1f)
                    )
                    UsageSummaryCard(
                        title = "Total",
                        count = allUsageRecords.size,
                        subtitle = "All records",
                        icon = Icons.Outlined.Assessment,
                        iconTint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            val count = when (tab) {
                                UsageTab.Active -> activeCount
                                UsageTab.Returned -> returnedCount
                                UsageTab.All -> allUsageRecords.size
                            }
                            Text("${tab.label} ($count)")
                        }
                    )
                }
            }

            // Date filter chips + search
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(DateFilter.entries) { filter ->
                        FilterChip(
                            selected = dateFilter == filter,
                            onClick = { dateFilter = filter },
                            label = { Text(filter.label) },
                            leadingIcon = if (filter == DateFilter.Custom && dateFilter == DateFilter.Custom) {
                                { Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                // Custom date range selectors
                if (dateFilter == DateFilter.Custom) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customStartDate?.let { formatShortDate(it) } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("From") },
                            modifier = Modifier.weight(1f),
                            trailingIcon = {
                                IconButton(onClick = { showStartPicker = true }) {
                                    Icon(Icons.Default.CalendarMonth, "Pick start date")
                                }
                            },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = customEndDate?.let { formatShortDate(it) } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("To") },
                            modifier = Modifier.weight(1f),
                            trailingIcon = {
                                IconButton(onClick = { showEndPicker = true }) {
                                    Icon(Icons.Default.CalendarMonth, "Pick end date")
                                }
                            },
                            singleLine = true
                        )
                    }
                }

                // Search
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by item, location, or user...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Records list
            if (isUsageLoading && filteredRecords.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SkeletonList(itemCount = 5, modifier = Modifier.fillMaxWidth())
                }
            } else if (filteredRecords.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AppEmptyStateCard(
                        icon = Icons.Default.Assessment,
                        title = "No usage records",
                        subtitle = "Usage records will appear here when items are checked out or returned."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredRecords, key = { it.id }) { record ->
                        UsageRecordCard(
                            record = record,
                            itemName = itemNameById[record.itemId] ?: "Item #${record.itemId}",
                            isSelected = adaptiveInfo.isTwoPane && record.id == selectedRecordId,
                            onClick = if (adaptiveInfo.isTwoPane) {
                                { selectedRecordId = record.id }
                            } else null,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
        }
            },
            secondaryPane = { paneModifier ->
                val record = selectedRecord
                if (record != null) {
                    UsageRecordDetailPane(
                        record = record,
                        itemName = itemNameById[record.itemId] ?: "Item #${record.itemId}",
                        modifier = paneModifier
                    )
                } else {
                    DetailPlaceholder(
                        title = "Select a usage record",
                        subtitle = "Choose a record from the list to see its full details.",
                        modifier = paneModifier
                    )
                }
            }
        )
    }
}

@Composable
private fun UsageSummaryCard(
    title: String,
    count: Int,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            iconTint.copy(alpha = 0.1f),
                            RoundedCornerShape(12.dp)
                        ),
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
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UsageRecordCard(
    record: ItemUsageRecord,
    itemName: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val isActive = record.status == "Active"
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    else
        MaterialTheme.colorScheme.surface

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button) { onClick() } else Modifier)
            .semantics(mergeDescendants = true) {
                if (onClick != null) selected = isSelected
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isActive)
                            Orange.copy(alpha = 0.12f)
                        else
                            Green.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isActive)
                        Icons.AutoMirrored.Filled.Logout
                    else
                        Icons.AutoMirrored.Filled.KeyboardReturn,
                    contentDescription = null,
                    tint = if (isActive) Orange else Green
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = itemName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    UsageStatusChip(isActive = isActive)
                }

                // Qty and location
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DetailLabel("Qty", record.quantity.toString())
                    DetailLabel("Location", record.location)
                }

                // Used by
                DetailLabel("Used by", record.usedBy)

                // Dates
                Text(
                    text = "Checked out: ${formatDateTime(record.checkedOutAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (record.returnedAt != null) {
                    Text(
                        text = "Returned: ${formatDateTime(record.returnedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!record.returnReason.isNullOrBlank()) {
                    Text(
                        text = "Reason: ${record.returnReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailLabel(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun UsageStatusChip(isActive: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Orange.copy(alpha = 0.15f) else Green.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = if (isActive) "Active" else "Returned",
            color = if (isActive) Orange else Green,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun UsageRecordDetailPane(
    record: ItemUsageRecord,
    itemName: String,
    modifier: Modifier = Modifier
) {
    val isActive = record.status == "Active"
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SmartStockDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(SmartStockDimens.sectionSpacing)
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
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = if (isActive) Orange.copy(alpha = 0.12f) else Green.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isActive)
                            Icons.AutoMirrored.Filled.Logout
                        else
                            Icons.AutoMirrored.Filled.KeyboardReturn,
                        contentDescription = null,
                        tint = if (isActive) Orange else Green
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = itemName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    UsageStatusChip(isActive = isActive)
                }
            }
        }

        DetailSectionCard(title = "Details") {
            DetailRow(label = "Quantity", value = record.quantity.toString())
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DetailRow(label = "Location", value = record.location)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DetailRow(label = "Used by", value = record.usedBy)
        }

        DetailSectionCard(title = "Timeline") {
            TimelineEntry(
                icon = Icons.AutoMirrored.Filled.Logout,
                iconTint = Orange,
                label = "Checked out",
                timestamp = formatDateTime(record.checkedOutAt)
            )
            if (record.returnedAt != null) {
                TimelineEntry(
                    icon = Icons.AutoMirrored.Filled.KeyboardReturn,
                    iconTint = Green,
                    label = "Returned",
                    timestamp = formatDateTime(record.returnedAt)
                )
            } else {
                TimelineEntry(
                    icon = Icons.Outlined.Schedule,
                    iconTint = Orange,
                    label = "Status",
                    timestamp = "Currently checked out"
                )
            }
        }

        if (!record.returnReason.isNullOrBlank()) {
            DetailSectionCard(title = "Return reason") {
                Text(
                    text = record.returnReason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DetailSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() }
            )
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        )
    }
}

@Composable
private fun TimelineEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    label: String,
    timestamp: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconTint.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = timestamp,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun filterByDate(
    records: List<ItemUsageRecord>,
    filter: DateFilter,
    customStart: Long?,
    customEnd: Long?
): List<ItemUsageRecord> {
    val calendar = Calendar.getInstance()
    return when (filter) {
        DateFilter.ThisMonth -> {
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val start = calendar.timeInMillis
            records.filter { it.checkedOutAt >= start }
        }
        DateFilter.ThisYear -> {
            calendar.set(Calendar.MONTH, Calendar.JANUARY)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val start = calendar.timeInMillis
            records.filter { it.checkedOutAt >= start }
        }
        DateFilter.AllTime -> records
        DateFilter.Custom -> {
            records.filter { record ->
                val afterStart = customStart == null || record.checkedOutAt >= customStart
                val beforeEnd = customEnd == null || record.checkedOutAt <= customEnd + 86_400_000L
                afterStart && beforeEnd
            }
        }
    }
}

private fun formatDateTime(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun formatShortDate(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
}
