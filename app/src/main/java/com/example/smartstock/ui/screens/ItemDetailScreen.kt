package com.example.smartstock.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.RadioButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.smartstock.core.labels.LabelFormat
import com.example.smartstock.core.labels.LabelGenerator
import com.example.smartstock.data.entity.InventoryItem
import com.example.smartstock.data.entity.ItemHistory
import com.example.smartstock.data.entity.LinkedBarcode
import com.example.smartstock.ui.components.LabelPreviewDialog
import com.example.smartstock.ui.DashboardViewModel
import com.example.smartstock.ui.InventoryViewModel
import com.example.smartstock.ui.adaptive.DetailPlaceholder
import com.example.smartstock.ui.components.ConnectivityStatusBadge
import com.example.smartstock.ui.components.ModalDialogHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    navController: NavController,
    viewModel: InventoryViewModel,
    dashboardViewModel: DashboardViewModel,
    itemId: Int
) {
    val item by viewModel.getItem(itemId).collectAsStateWithLifecycle(initialValue = null)
    val history by viewModel.getHistory(itemId).collectAsStateWithLifecycle(initialValue = emptyList())
    val linkedBarcodes by viewModel.getLinkedBarcodes(itemId).collectAsStateWithLifecycle(initialValue = emptyList())
    val operationError by viewModel.operationError.collectAsStateWithLifecycle()
    val operationMessage by viewModel.operationMessage.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val isOnline by dashboardViewModel.isOnline.collectAsStateWithLifecycle()
    val syncState by dashboardViewModel.syncState.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showUseDialog by remember { mutableStateOf(false) }
    var showReturnDialog by remember { mutableStateOf(false) }
    var showConditionDialog by remember { mutableStateOf(false) }
    var showAddBarcodeDialog by remember { mutableStateOf(false) }
    var showLabelMenu by remember { mutableStateOf(false) }
    var labelPreview by remember { mutableStateOf<LabelFormat?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(operationError) {
        operationError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearOperationError()
        }
    }
    LaunchedEffect(operationMessage) {
        operationMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearOperationMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(item?.name ?: "Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ConnectivityStatusBadge(isOnline = isOnline, syncState = syncState)
                    Box {
                        IconButton(
                            onClick = { if (item != null) showLabelMenu = true },
                            enabled = item != null
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = "Print label")
                        }
                        DropdownMenu(
                            expanded = showLabelMenu,
                            onDismissRequest = { showLabelMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("QR Code label") },
                                onClick = {
                                    showLabelMenu = false
                                    labelPreview = LabelFormat.QrCode
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Barcode label") },
                                onClick = {
                                    showLabelMenu = false
                                    labelPreview = LabelFormat.Barcode
                                }
                            )
                        }
                    }
                    if (currentRole.canDeleteItem) {
                        IconButton(onClick = {
                            if (item != null) {
                                showDeleteConfirm = true
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = currentRole.canEditItem,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = { showEditDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
            }
        }
    ) { paddingValues ->
        item?.let { currentItem ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                UsageActionButtons(
                    item = currentItem,
                    canUpdateCondition = currentRole.canUpdateCondition,
                    onUse = { showUseDialog = true },
                    onReturn = { showReturnDialog = true },
                    onUpdateCondition = { showConditionDialog = true }
                )
                Spacer(modifier = Modifier.height(12.dp))
                ItemDetailBody(
                    item = currentItem,
                    history = history,
                    modifier = Modifier.weight(1f),
                    linkedBarcodes = linkedBarcodes,
                    canEditBarcodes = currentRole.canEditItem,
                    onUnlinkBarcode = { viewModel.unlinkBarcode(it) },
                    onAddBarcode = { showAddBarcodeDialog = true }
                )
            }
        } ?: DetailPlaceholder(
            title = "Item not found",
            subtitle = "The selected inventory item is no longer available."
        )
    }

    val previewFormat = labelPreview
    val previewItem = item
    if (previewFormat != null && previewItem != null) {
        LabelPreviewDialog(
            item = previewItem,
            initialFormat = previewFormat,
            onDismiss = { labelPreview = null }
        )
    }

    if (showAddBarcodeDialog && item != null) {
        AddLinkedBarcodeDialog(
            onDismiss = { showAddBarcodeDialog = false },
            onConfirm = { barcodeValue, label ->
                viewModel.linkBarcode(itemId, barcodeValue, label)
                showAddBarcodeDialog = false
            }
        )
    }

    if (showConditionDialog && item != null) {
        ConditionUpdateDialog(
            currentCondition = item?.condition ?: "",
            onDismiss = { showConditionDialog = false },
            onConfirm = { newCondition ->
                item?.let { viewModel.updateItemCondition(it, newCondition) }
                showConditionDialog = false
            }
        )
    }

    if (showDeleteConfirm && item != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete item") },
            text = { Text("Remove \"${item?.name}\" from inventory? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        item?.let { viewModel.deleteItem(it) }
                        showDeleteConfirm = false
                        navController.popBackStack()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditDialog && item != null) {
        AddEditItemDialog(
            viewModel = viewModel,
            dashboardViewModel = dashboardViewModel,
            itemId = itemId,
            onDismissRequest = { showEditDialog = false },
            onSubmitSuccess = { showEditDialog = false }
        )
    }

    if (showUseDialog && item != null) {
        UseItemDialog(
            maxQuantity = item?.availableQuantity ?: 0,
            onDismiss = { showUseDialog = false },
            onConfirm = { amount, location ->
                item?.let { viewModel.useItem(it, amount, location) }
                showUseDialog = false
            }
        )
    }

    if (showReturnDialog && item != null) {
        ReturnItemDialog(
            maxQuantity = item?.inUseQuantity ?: 0,
            onDismiss = { showReturnDialog = false },
            onConfirm = { amount, reason ->
                item?.let { viewModel.returnItem(it, amount, reason) }
                showReturnDialog = false
            }
        )
    }
}

@Composable
fun ItemDetailDialog(
    viewModel: InventoryViewModel,
    itemId: Int,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit
) {
    val item by viewModel.getItem(itemId).collectAsStateWithLifecycle(initialValue = null)
    val history by viewModel.getHistory(itemId).collectAsStateWithLifecycle(initialValue = emptyList())
    val linkedBarcodes by viewModel.getLinkedBarcodes(itemId).collectAsStateWithLifecycle(initialValue = emptyList())
    val currentRole by viewModel.currentUserRole.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showUseDialog by remember { mutableStateOf(false) }
    var showReturnDialog by remember { mutableStateOf(false) }
    var showConditionDialog by remember { mutableStateOf(false) }
    var showAddBarcodeDialog by remember { mutableStateOf(false) }
    var showLabelMenu by remember { mutableStateOf(false) }
    var labelPreview by remember { mutableStateOf<LabelFormat?>(null) }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                ModalDialogHeader(
                    title = item?.name ?: "Item Details",
                    subtitle = item?.assetCode?.let { "Asset Code: $it" } ?: "View inventory details, usage, and item history.",
                    onClose = onDismiss
                )

                Spacer(modifier = Modifier.height(8.dp))

                item?.let { currentItem ->
                    UsageActionButtons(
                        item = currentItem,
                        canUpdateCondition = currentRole.canUpdateCondition,
                        onUse = { showUseDialog = true },
                        onReturn = { showReturnDialog = true },
                        onUpdateCondition = { showConditionDialog = true }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (currentRole.canEditItem) {
                        TextButton(onClick = onEdit) {
                            Text("Edit")
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { if (item != null) showLabelMenu = true },
                            enabled = item != null
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = "Print label")
                        }
                        DropdownMenu(
                            expanded = showLabelMenu,
                            onDismissRequest = { showLabelMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("QR Code label") },
                                onClick = {
                                    showLabelMenu = false
                                    labelPreview = LabelFormat.QrCode
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Barcode label") },
                                onClick = {
                                    showLabelMenu = false
                                    labelPreview = LabelFormat.Barcode
                                }
                            )
                        }
                    }
                    if (currentRole.canDeleteItem) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                item?.let { currentItem ->
                    ItemDetailBody(
                        item = currentItem,
                        history = history,
                        modifier = Modifier.weight(1f),
                        linkedBarcodes = linkedBarcodes,
                        canEditBarcodes = currentRole.canEditItem,
                        onUnlinkBarcode = { viewModel.unlinkBarcode(it) },
                        onAddBarcode = { showAddBarcodeDialog = true }
                    )
                } ?: DetailPlaceholder(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Item not found",
                    subtitle = "The selected inventory item is no longer available."
                )
            }
        }
    }

    if (showDeleteConfirm && item != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete item") },
            text = { Text("Remove \"${item?.name}\" from inventory? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        item?.let { viewModel.deleteItem(it) }
                        showDeleteConfirm = false
                        onDeleted()
                        onDismiss()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUseDialog && item != null) {
        UseItemDialog(
            maxQuantity = item?.availableQuantity ?: 0,
            onDismiss = { showUseDialog = false },
            onConfirm = { amount, location ->
                item?.let { viewModel.useItem(it, amount, location) }
                showUseDialog = false
            }
        )
    }

    if (showReturnDialog && item != null) {
        ReturnItemDialog(
            maxQuantity = item?.inUseQuantity ?: 0,
            onDismiss = { showReturnDialog = false },
            onConfirm = { amount, reason ->
                item?.let { viewModel.returnItem(it, amount, reason) }
                showReturnDialog = false
            }
        )
    }

    if (showConditionDialog && item != null) {
        ConditionUpdateDialog(
            currentCondition = item?.condition ?: "",
            onDismiss = { showConditionDialog = false },
            onConfirm = { newCondition ->
                item?.let { viewModel.updateItemCondition(it, newCondition) }
                showConditionDialog = false
            }
        )
    }

    val previewFormat = labelPreview
    val previewItem = item
    if (previewFormat != null && previewItem != null) {
        LabelPreviewDialog(
            item = previewItem,
            initialFormat = previewFormat,
            onDismiss = { labelPreview = null }
        )
    }

    if (showAddBarcodeDialog && item != null) {
        AddLinkedBarcodeDialog(
            onDismiss = { showAddBarcodeDialog = false },
            onConfirm = { barcodeValue, label ->
                viewModel.linkBarcode(itemId, barcodeValue, label)
                showAddBarcodeDialog = false
            }
        )
    }
}

@Composable
fun ItemDetailPaneContent(
    viewModel: InventoryViewModel,
    itemId: Int,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onDeleted: () -> Unit
) {
    val item by viewModel.getItem(itemId).collectAsStateWithLifecycle(initialValue = null)
    val history by viewModel.getHistory(itemId).collectAsStateWithLifecycle(initialValue = emptyList())
    val linkedBarcodes by viewModel.getLinkedBarcodes(itemId).collectAsStateWithLifecycle(initialValue = emptyList())
    val currentRole by viewModel.currentUserRole.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showUseDialog by remember { mutableStateOf(false) }
    var showReturnDialog by remember { mutableStateOf(false) }
    var showConditionDialog by remember { mutableStateOf(false) }
    var showAddBarcodeDialog by remember { mutableStateOf(false) }
    var showLabelMenu by remember { mutableStateOf(false) }
    var labelPreview by remember { mutableStateOf<LabelFormat?>(null) }
    val context = LocalContext.current

    item?.let { currentItem ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentItem.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { showLabelMenu = true }) {
                        Icon(Icons.Default.QrCode2, contentDescription = "Print label")
                    }
                    DropdownMenu(
                        expanded = showLabelMenu,
                        onDismissRequest = { showLabelMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("QR Code label") },
                            onClick = {
                                showLabelMenu = false
                                labelPreview = LabelFormat.QrCode
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Barcode label") },
                            onClick = {
                                showLabelMenu = false
                                labelPreview = LabelFormat.Barcode
                            }
                        )
                    }
                }
                if (currentRole.canEditItem) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
                if (currentRole.canDeleteItem) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
            UsageActionButtons(
                item = currentItem,
                canUpdateCondition = currentRole.canUpdateCondition,
                onUse = { showUseDialog = true },
                onReturn = { showReturnDialog = true },
                onUpdateCondition = { showConditionDialog = true }
            )
            Spacer(modifier = Modifier.height(12.dp))
            ItemDetailBody(
                item = currentItem,
                history = history,
                modifier = Modifier.weight(1f),
                linkedBarcodes = linkedBarcodes,
                canEditBarcodes = currentRole.canEditItem,
                onUnlinkBarcode = { viewModel.unlinkBarcode(it) },
                onAddBarcode = { showAddBarcodeDialog = true }
            )
        }
    } ?: DetailPlaceholder(
        modifier = modifier,
        title = "Select an item",
        subtitle = "Choose an item from the list to view details."
    )

    if (showDeleteConfirm && item != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete item") },
            text = { Text("Remove \"${item?.name}\" from inventory? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    item?.let { viewModel.deleteItem(it) }
                    showDeleteConfirm = false
                    onDeleted()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showUseDialog && item != null) {
        UseItemDialog(
            maxQuantity = item?.availableQuantity ?: 0,
            onDismiss = { showUseDialog = false },
            onConfirm = { amount, location ->
                item?.let { viewModel.useItem(it, amount, location) }
                showUseDialog = false
            }
        )
    }

    if (showReturnDialog && item != null) {
        ReturnItemDialog(
            maxQuantity = item?.inUseQuantity ?: 0,
            onDismiss = { showReturnDialog = false },
            onConfirm = { amount, reason ->
                item?.let { viewModel.returnItem(it, amount, reason) }
                showReturnDialog = false
            }
        )
    }

    if (showConditionDialog && item != null) {
        ConditionUpdateDialog(
            currentCondition = item?.condition ?: "",
            onDismiss = { showConditionDialog = false },
            onConfirm = { newCondition ->
                item?.let { viewModel.updateItemCondition(it, newCondition) }
                showConditionDialog = false
            }
        )
    }

    val previewFormat = labelPreview
    val previewItem = item
    if (previewFormat != null && previewItem != null) {
        LabelPreviewDialog(
            item = previewItem,
            initialFormat = previewFormat,
            onDismiss = { labelPreview = null }
        )
    }

    if (showAddBarcodeDialog && item != null) {
        AddLinkedBarcodeDialog(
            onDismiss = { showAddBarcodeDialog = false },
            onConfirm = { barcodeValue, label ->
                viewModel.linkBarcode(itemId, barcodeValue, label)
                showAddBarcodeDialog = false
            }
        )
    }
}

@Composable
private fun ItemDetailBody(
    item: InventoryItem,
    history: List<ItemHistory>,
    modifier: Modifier = Modifier,
    linkedBarcodes: List<LinkedBarcode> = emptyList(),
    canEditBarcodes: Boolean = false,
    onUnlinkBarcode: (LinkedBarcode) -> Unit = {},
    onAddBarcode: () -> Unit = {}
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        item { ItemHeroCard(item = item) }
        item { QuantityStatsCard(item = item) }
        item { DetailsCard(item = item) }
        item { DescriptionCard(item = item) }
        item {
            LinkedBarcodesCard(
                linkedBarcodes = linkedBarcodes,
                canEditBarcodes = canEditBarcodes,
                onUnlinkBarcode = onUnlinkBarcode,
                onAddBarcode = onAddBarcode
            )
        }
        item { ActivityHistoryCard(history = history) }
    }
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (trailing != null) {
                    trailing()
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.4f)
        )
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    container: Color,
    onContainer: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = onContainer
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = onContainer.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, container: Color, onContainer: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = container
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = onContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun statusChipColors(status: String): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (status.lowercase(Locale.getDefault())) {
        "available", "in stock" -> cs.tertiaryContainer to cs.onTertiaryContainer
        "in use" -> cs.secondaryContainer to cs.onSecondaryContainer
        "out of stock", "damaged", "missing" -> cs.errorContainer to cs.onErrorContainer
        else -> cs.surfaceVariant to cs.onSurfaceVariant
    }
}

@Composable
private fun conditionChipColors(condition: String): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (condition.lowercase(Locale.getDefault())) {
        "new", "good" -> cs.tertiaryContainer to cs.onTertiaryContainer
        "fair" -> cs.secondaryContainer to cs.onSecondaryContainer
        "poor", "damaged" -> cs.errorContainer to cs.onErrorContainer
        else -> cs.surfaceVariant to cs.onSurfaceVariant
    }
}

@Composable
private fun ItemHeroCard(item: InventoryItem) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (item.imageUri != null) {
                AsyncImage(
                    model = item.imageUri,
                    contentDescription = "Photo of ${item.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(14.dp)
                        )
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
            val (statusBg, statusFg) = statusChipColors(item.status)
            val (condBg, condFg) = conditionChipColors(item.condition)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(label = item.status, container = statusBg, onContainer = statusFg)
                StatusChip(label = item.condition, container = condBg, onContainer = condFg)
                if (item.assetCode.isNotBlank()) {
                    StatusChip(
                        label = "#${item.assetCode}",
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        onContainer = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun QuantityStatsCard(item: InventoryItem) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatPill(
            label = "Total",
            value = item.quantity.toString(),
            container = cs.primaryContainer,
            onContainer = cs.onPrimaryContainer,
            modifier = Modifier.weight(1f)
        )
        StatPill(
            label = "Available",
            value = item.availableQuantity.toString(),
            container = cs.tertiaryContainer,
            onContainer = cs.onTertiaryContainer,
            modifier = Modifier.weight(1f)
        )
        StatPill(
            label = "In Use",
            value = item.inUseQuantity.toString(),
            container = cs.secondaryContainer,
            onContainer = cs.onSecondaryContainer,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DetailsCard(item: InventoryItem) {
    SectionCard(title = "Details") {
        DetailRow(label = "Asset Code", value = item.assetCode.ifBlank { "—" })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        DetailRow(label = "Category", value = item.category.ifBlank { "—" })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        DetailRow(label = "Location", value = item.location?.takeIf { it.isNotBlank() } ?: "N/A")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        DetailRow(label = "Date Added", value = formatDate(item.createdAt))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        DetailRow(label = "Last Updated", value = formatDate(item.lastUpdated))
    }
}

@Composable
private fun DescriptionCard(item: InventoryItem) {
    SectionCard(title = "Description") {
        val desc = item.description?.takeIf { it.isNotBlank() }
        if (desc != null) {
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            Text(
                text = "No description available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
private fun LinkedBarcodesCard(
    linkedBarcodes: List<LinkedBarcode>,
    canEditBarcodes: Boolean,
    onUnlinkBarcode: (LinkedBarcode) -> Unit,
    onAddBarcode: () -> Unit
) {
    SectionCard(
        title = "Linked Barcodes",
        trailing = {
            if (canEditBarcodes) {
                TextButton(onClick = onAddBarcode) {
                    Text("+ Add")
                }
            }
        }
    ) {
        if (linkedBarcodes.isEmpty()) {
            Text(
                text = "No barcodes linked to this item yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                linkedBarcodes.forEach { barcode ->
                    LinkedBarcodeRow(
                        barcode = barcode,
                        canRemove = canEditBarcodes,
                        onRemove = { onUnlinkBarcode(barcode) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityHistoryCard(history: List<ItemHistory>) {
    SectionCard(title = "Activity History") {
        if (history.isEmpty()) {
            Text(
                text = "No history yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                history.forEach { entry ->
                    HistoryTimelineRow(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun HistoryTimelineRow(entry: ItemHistory) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.action,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
                    .format(Date(entry.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (entry.details.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun LinkedBarcodeRow(
    barcode: LinkedBarcode,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = barcode.barcodeValue,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (barcode.label.isNotBlank()) {
                    Text(
                        text = barcode.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Linked: ${
                        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                            .format(Date(barcode.linkedAt))
                    }",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove barcode",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageActionButtons(
    item: InventoryItem,
    canUpdateCondition: Boolean = false,
    onUse: () -> Unit,
    onReturn: () -> Unit,
    onUpdateCondition: () -> Unit = {}
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onUse,
            enabled = item.isUsageTrackable && item.availableQuantity > 0
        ) {
            Text("Use")
        }
        OutlinedButton(
            onClick = onReturn,
            enabled = item.inUseQuantity > 0
        ) {
            Text("Return")
        }
        if (canUpdateCondition) {
            OutlinedButton(onClick = onUpdateCondition) {
                Text("Condition")
            }
        }
    }
}

@Composable
private fun UseItemDialog(
    maxQuantity: Int,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Int, location: String) -> Unit
) {
    var quantityText by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    val parsedQuantity = quantityText.toIntOrNull()
    val isValid = parsedQuantity != null && parsedQuantity > 0 && parsedQuantity <= maxQuantity && location.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Check out items to a destination. Max available: $maxQuantity",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { value ->
                        if (value.all(Char::isDigit)) quantityText = value
                    },
                    label = { Text("Quantity") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Destination *") },
                    placeholder = { Text("e.g., Room 101, Lab B") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsedQuantity?.let { onConfirm(it, location.trim()) } },
                enabled = isValid
            ) {
                Text("Check Out")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ReturnItemDialog(
    maxQuantity: Int,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Int, reason: String) -> Unit
) {
    var quantityText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val parsedQuantity = quantityText.toIntOrNull()
    val isValid = parsedQuantity != null && parsedQuantity > 0 && parsedQuantity <= maxQuantity

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Return Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Return checked-out items. Max returnable: $maxQuantity",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { value ->
                        if (value.all(Char::isDigit)) quantityText = value
                    },
                    label = { Text("Quantity") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    placeholder = { Text("e.g., Task completed, No longer needed") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsedQuantity?.let { onConfirm(it, reason.trim()) } },
                enabled = isValid
            ) {
                Text("Return")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun QuantityAdjustmentDialog(
    title: String,
    maxQuantity: Int,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var quantityText by remember { mutableStateOf("") }
    val parsedQuantity = quantityText.toIntOrNull()
    val isValid = parsedQuantity != null && parsedQuantity > 0 && parsedQuantity <= maxQuantity

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = "Enter quantity for this action. Maximum allowed: $maxQuantity",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { value ->
                        if (value.all(Char::isDigit)) {
                            quantityText = value
                        }
                    },
                    label = { Text("Quantity") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsedQuantity?.let(onConfirm) },
                enabled = isValid
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ConditionUpdateDialog(
    currentCondition: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val conditions = listOf("New", "Good", "Fair", "Poor")
    var selectedCondition by remember { mutableStateOf(currentCondition) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Condition") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Select the current condition of this item.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                conditions.forEach { condition ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedCondition = condition }
                            .background(
                                if (selectedCondition == condition)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    Color.Transparent
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedCondition == condition,
                            onClick = { selectedCondition = condition }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = condition,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selectedCondition == condition) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                if (selectedCondition == "Damaged") {
                    Text(
                        text = "Setting condition to Damaged will also update the item status to Damaged.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedCondition) },
                enabled = selectedCondition != currentCondition
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AddLinkedBarcodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (barcodeValue: String, label: String) -> Unit
) {
    var barcodeValue by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Linked Barcode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter the barcode value to link to this item. You can also scan it using the Camera Scan screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = barcodeValue,
                    onValueChange = { barcodeValue = it },
                    label = { Text("Barcode Value *") },
                    placeholder = { Text("e.g., 1234567890128") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (optional)") },
                    placeholder = { Text("e.g., Unit #3, SN: ABC123") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(barcodeValue.trim(), label.trim()) },
                enabled = barcodeValue.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        .format(Date(timestamp))
}

private fun launchLabelShare(context: Context, item: InventoryItem, format: LabelFormat) {
    try {
        val intent = LabelGenerator.generateAndShare(context, item, format)
        val chooser = Intent.createChooser(intent, "Share Label").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app available to share the label.", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to generate label.", Toast.LENGTH_SHORT).show()
    }
}
