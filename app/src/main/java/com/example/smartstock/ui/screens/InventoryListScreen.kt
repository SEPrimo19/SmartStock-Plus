package com.example.smartstock.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.rememberDismissState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartstock.data.entity.InventoryItem
import com.example.smartstock.ui.DashboardViewModel
import com.example.smartstock.ui.InventoryViewModel
import com.example.smartstock.ui.adaptive.AdaptiveInfo
import com.example.smartstock.ui.adaptive.AdaptiveScreenScaffold
import com.example.smartstock.ui.adaptive.DetailPlaceholder
import com.example.smartstock.ui.adaptive.MasterDetailPane
import com.example.smartstock.ui.adaptive.SmartStockDimens
import com.example.smartstock.ui.components.AppEmptyStateCard
import com.example.smartstock.ui.components.ConnectivityStatusBadge
import com.example.smartstock.ui.components.InventoryItemCard
import com.example.smartstock.ui.components.SkeletonList
import com.example.smartstock.ui.theme.Elevation
import androidx.compose.material3.Icon as Icon3

@OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun InventoryListScreen(
    viewModel: InventoryViewModel,
    dashboardViewModel: DashboardViewModel,
    adaptiveInfo: AdaptiveInfo
) {
    val searchQuery by viewModel.inventorySearchQuery.collectAsStateWithLifecycle()
    val items by viewModel.inventorySearchResults.collectAsStateWithLifecycle()
    val isInventoryLoading by viewModel.isInventoryLoading.collectAsStateWithLifecycle()
    val selectedItemId by viewModel.selectedInventoryItemId.collectAsStateWithLifecycle()
    val categoryOptions by viewModel.categoryNames.collectAsStateWithLifecycle()
    val statusOptions by viewModel.statusNames.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.inventoryCategoryFilter.collectAsStateWithLifecycle()
    val selectedStatus by viewModel.inventoryStatusFilter.collectAsStateWithLifecycle()
    val isOnline by dashboardViewModel.isOnline.collectAsStateWithLifecycle()
    val syncState by dashboardViewModel.syncState.collectAsStateWithLifecycle()
    val selectedSortField by viewModel.inventorySortField.collectAsStateWithLifecycle()
    val selectedSortDescending by viewModel.inventorySortDescending.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val operationMessage by viewModel.operationMessage.collectAsStateWithLifecycle()
    val operationError by viewModel.operationError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingUndoItem by remember { mutableStateOf<InventoryItem?>(null) }

    // Delete success paths surface as "Deleted X" via operationMessage. Match
    // on the prefix so we can attach an UNDO action to the same snackbar
    // (instead of stacking a second one underneath).
    LaunchedEffect(operationMessage) {
        operationMessage?.let { msg ->
            val undoTarget = pendingUndoItem.takeIf { msg.startsWith("Deleted ") }
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = if (undoTarget != null) "UNDO" else null,
                withDismissAction = undoTarget == null,
                duration = if (undoTarget != null) {
                    androidx.compose.material3.SnackbarDuration.Short
                } else {
                    androidx.compose.material3.SnackbarDuration.Short
                }
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed &&
                undoTarget != null
            ) {
                viewModel.restoreItem(undoTarget)
            }
            pendingUndoItem = null
            viewModel.clearOperationMessage()
        }
    }
    LaunchedEffect(operationError) {
        operationError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearOperationError()
        }
    }

    val inventoryCategoryOptions = listOf("All") + categoryOptions
    val inventoryStatusOptions = listOf("All") + statusOptions
    val sortOptions = listOf("Name", "Quantity", "Updated")

    var showAddDialog by remember { mutableStateOf(false) }
    var detailItemId by remember { mutableStateOf<Int?>(null) }
    var editItemId by remember { mutableStateOf<Int?>(null) }
    var pendingDeleteItem by remember { mutableStateOf<InventoryItem?>(null) }
    var swipeResetNonce by remember { mutableStateOf(0) }
    var isCategoryMenuExpanded by remember { mutableStateOf(false) }
    var isStatusMenuExpanded by remember { mutableStateOf(false) }
    var isSortFieldMenuExpanded by remember { mutableStateOf(false) }
    var areFiltersExpanded by remember(adaptiveInfo.isTwoPane) { mutableStateOf(!adaptiveInfo.isTwoPane) }
    val isCompactTwoPaneHeader = adaptiveInfo.isTwoPane && !areFiltersExpanded

    LaunchedEffect(adaptiveInfo.isTwoPane) {
        if (!adaptiveInfo.isTwoPane) {
            viewModel.selectInventoryItem(null)
        }
    }

    val visibleDetailItemId = detailItemId

    if (!adaptiveInfo.isTwoPane && visibleDetailItemId != null) {
        ItemDetailDialog(
            viewModel = viewModel,
            itemId = visibleDetailItemId,
            onEdit = {
                editItemId = visibleDetailItemId
                detailItemId = null
            },
            onDeleted = {
                val deletedId = detailItemId
                detailItemId = null
                if (selectedItemId == deletedId) {
                    viewModel.selectInventoryItem(null)
                }
            },
            onDismiss = { detailItemId = null }
        )
    }

    if (showAddDialog) {
        AddEditItemDialog(
            viewModel = viewModel,
            dashboardViewModel = dashboardViewModel,
            onDismissRequest = { showAddDialog = false },
            onSubmitSuccess = { showAddDialog = false }
        )
    }

    if (editItemId != null) {
        AddEditItemDialog(
            viewModel = viewModel,
            dashboardViewModel = dashboardViewModel,
            itemId = editItemId,
            onDismissRequest = { editItemId = null },
            onSubmitSuccess = { editItemId = null }
        )
    }

    pendingDeleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = {
                pendingDeleteItem = null
                swipeResetNonce++
            },
            title = { Text("Delete Item") },
            text = { Text("Are you sure you want to delete \"${item.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Snapshot before delete: the soft-delete mutates updatedAt
                        // and the operationMessage LaunchedEffect needs this to
                        // restore the row if the user taps UNDO.
                        pendingUndoItem = item
                        viewModel.deleteItem(item)
                        if (selectedItemId == item.id) {
                            viewModel.selectInventoryItem(null)
                        }
                        if (detailItemId == item.id) {
                            detailItemId = null
                        }
                        pendingDeleteItem = null
                        swipeResetNonce++
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingDeleteItem = null
                    swipeResetNonce++
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    AdaptiveScreenScaffold(
        title = "Inventory",
        showTopBar = !adaptiveInfo.isTwoPane,
        actions = { ConnectivityStatusBadge(isOnline = isOnline, syncState = syncState) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            AnimatedVisibility(
                visible = currentRole.canAddItem,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White
                ) {
                    Icon3(Icons.Default.Add, contentDescription = "Add Item")
                }
            }
        }
    ) { paddingValues ->
        MasterDetailPane(
            isTwoPane = adaptiveInfo.isTwoPane,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(SmartStockDimens.screenPadding),
            primaryPane = { paneModifier ->
                Column(
                    modifier = paneModifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                ) {
                    Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(if (isCompactTwoPaneHeader) 18.dp else 20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.level2),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (isCompactTwoPaneHeader) 10.dp else 16.dp, vertical = if (isCompactTwoPaneHeader) 8.dp else 14.dp),
                            verticalArrangement = Arrangement.spacedBy(if (isCompactTwoPaneHeader) 6.dp else 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Search & Filters",
                                        style = if (isCompactTwoPaneHeader) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!isCompactTwoPaneHeader) {
                                        Text(
                                            text = "Search inventory records and refine the visible list by category, status, and sort order.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (adaptiveInfo.isTwoPane) {
                                    TextButton(
                                        onClick = { areFiltersExpanded = !areFiltersExpanded },
                                        modifier = Modifier.height(if (isCompactTwoPaneHeader) 36.dp else 40.dp)
                                    ) {
                                        Text(if (areFiltersExpanded) "Hide Filters" else "Show Filters")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon3(
                                            imageVector = if (areFiltersExpanded) {
                                                Icons.Default.KeyboardArrowUp
                                            } else {
                                                Icons.Default.KeyboardArrowDown
                                            },
                                            contentDescription = null
                                        )
                                    }
                                }
                            }

                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.onInventoryQueryChanged(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (isCompactTwoPaneHeader) 52.dp else 56.dp),
                                placeholder = { Text("Search items or code") },
                                leadingIcon = { Icon3(Icons.Default.Search, contentDescription = null) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )

                            if (!adaptiveInfo.isTwoPane || areFiltersExpanded) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (adaptiveInfo.isTwoPane) "Filters" else "Filter Controls",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(onClick = { viewModel.clearInventoryFilters() }) {
                                        Text("Reset")
                                    }
                                }
                            }

                            AnimatedVisibility(visible = !adaptiveInfo.isTwoPane || areFiltersExpanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        InventoryFilterButton(
                                            label = "Category",
                                            selected = selectedCategory,
                                            options = inventoryCategoryOptions,
                                            isExpanded = isCategoryMenuExpanded,
                                            onExpandedChanged = { isCategoryMenuExpanded = it },
                                            onSelect = {
                                                viewModel.onInventoryCategoryFilterChanged(it)
                                                isCategoryMenuExpanded = false
                                            },
                                            modifier = Modifier.weight(1f)
                                        )

                                        InventoryFilterButton(
                                            label = "Status",
                                            selected = selectedStatus,
                                            options = inventoryStatusOptions,
                                            isExpanded = isStatusMenuExpanded,
                                            onExpandedChanged = { isStatusMenuExpanded = it },
                                            onSelect = {
                                                viewModel.onInventoryStatusFilterChanged(it)
                                                isStatusMenuExpanded = false
                                            },
                                            modifier = Modifier.weight(1f)
                                        )

                                        InventoryFilterButton(
                                            label = "Sort",
                                            selected = selectedSortField,
                                            options = sortOptions,
                                            isExpanded = isSortFieldMenuExpanded,
                                            onExpandedChanged = { isSortFieldMenuExpanded = it },
                                            onSelect = {
                                                viewModel.onInventorySortFieldChanged(it)
                                                isSortFieldMenuExpanded = false
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Surface(
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                                    ) {
                                        TextButton(
                                            onClick = { viewModel.onInventorySortDirectionChanged(!selectedSortDescending) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(if (selectedSortDescending) "Sort Direction: Descending" else "Sort Direction: Ascending")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isCompactTwoPaneHeader) 6.dp else SmartStockDimens.sectionSpacing))

                    PullToRefreshBox(
                        isRefreshing = syncState == com.example.smartstock.core.sync.SyncState.SYNCING,
                        onRefresh = { dashboardViewModel.requestSync() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (isInventoryLoading && items.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 8.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                SkeletonList(itemCount = 5, modifier = Modifier.fillMaxWidth())
                            }
                        } else if (items.isEmpty()) {
                            val hasActiveFilter = searchQuery.isNotBlank() ||
                                selectedCategory != "All" ||
                                selectedStatus != "All"
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 8.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                if (hasActiveFilter) {
                                    AppEmptyStateCard(
                                        icon = Icons.Default.Search,
                                        title = "No matching inventory",
                                        subtitle = "No items match your search and filters. Try clearing them to see your full inventory.",
                                        modifier = Modifier.fillMaxWidth(),
                                        actionLabel = "Clear filters",
                                        onAction = { viewModel.clearInventoryFilters() }
                                    )
                                } else {
                                    AppEmptyStateCard(
                                        icon = Icons.Default.Add,
                                        title = "No items yet",
                                        subtitle = "Your inventory is empty. Add an item or scan a barcode to get started.",
                                        modifier = Modifier.fillMaxWidth(),
                                        actionLabel = if (currentRole.canAddItem) "Add item" else null,
                                        onAction = if (currentRole.canAddItem) ({ showAddDialog = true }) else null
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
                            ) {
                                items(
                                    items = items,
                                    key = { item -> "${item.id}-$swipeResetNonce" }
                                ) { item ->
                                    val dismissState = rememberDismissState(confirmStateChange = { dismissValue: DismissValue ->
                                        if (!currentRole.canDeleteItem) {
                                            false
                                        } else if (dismissValue == DismissValue.DismissedToStart) {
                                            pendingDeleteItem = item
                                            false
                                        } else {
                                            false
                                        }
                                    })

                                    SwipeToDismiss(
                                        state = dismissState,
                                        modifier = Modifier.animateItem(),
                                        directions = if (currentRole.canDeleteItem) setOf(DismissDirection.EndToStart) else emptySet(),
                                        background = { SwipeToDeleteBackground(dismissState) },
                                        dismissContent = {
                                            InventoryItemCard(
                                                name = item.name,
                                                category = item.category,
                                                quantity = item.quantity,
                                                availableQuantity = item.availableQuantity,
                                                inUseQuantity = item.inUseQuantity,
                                                status = item.status,
                                                onClick = {
                                                    if (adaptiveInfo.isTwoPane) {
                                                        viewModel.selectInventoryItem(item.id)
                                                    } else {
                                                        detailItemId = item.id
                                                    }
                                                },
                                                isSelected = adaptiveInfo.isTwoPane && selectedItemId == item.id,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 4.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            secondaryPane = { paneModifier ->
                val detailId = selectedItemId
                if (detailId != null) {
                    ItemDetailPaneContent(
                        viewModel = viewModel,
                        itemId = detailId,
                        modifier = paneModifier,
                        onEdit = {
                            if (currentRole.canEditItem) {
                                editItemId = detailId
                            }
                        },
                        onDeleted = { viewModel.selectInventoryItem(null) }
                    )
                } else {
                    DetailPlaceholder(
                        modifier = paneModifier,
                        title = "Select an inventory item",
                        subtitle = "Details will appear here in landscape mode."
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SwipeToDeleteBackground(dismissState: androidx.compose.material.DismissState) {
    val showBackground = dismissState.currentValue != DismissValue.Default || dismissState.targetValue != DismissValue.Default
    if (!showBackground) {
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Delete",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun InventoryFilterButton(
    label: String,
    selected: String,
    options: List<String>,
    isExpanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { onExpandedChanged(true) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = selected,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon3(Icons.Default.ArrowDropDown, contentDescription = null)
        }

        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { onExpandedChanged(false) }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option) }
                )
            }
        }
    }
}
