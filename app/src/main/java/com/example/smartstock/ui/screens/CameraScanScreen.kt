package com.example.smartstock.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import androidx.compose.material.icons.Icons
import com.example.smartstock.ui.DashboardViewModel
import com.example.smartstock.ui.InventoryViewModel
import com.example.smartstock.data.entity.InventoryItem
import com.example.smartstock.data.entity.LinkedBarcode
import com.example.smartstock.ui.adaptive.AdaptiveInfo
import com.example.smartstock.ui.adaptive.AdaptiveScreenScaffold
import com.example.smartstock.ui.components.AppEmptyStateCard
import com.example.smartstock.ui.components.ConnectivityStatusBadge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun CameraScanScreen(
    inventoryViewModel: InventoryViewModel,
    dashboardViewModel: DashboardViewModel,
    adaptiveInfo: AdaptiveInfo
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val isOnline by dashboardViewModel.isOnline.collectAsStateWithLifecycle()
    val currentRole by inventoryViewModel.currentUserRole.collectAsStateWithLifecycle()
    val operationMessage by inventoryViewModel.operationMessage.collectAsStateWithLifecycle()
    val operationError by inventoryViewModel.operationError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(operationMessage) {
        operationMessage?.let {
            if (it.startsWith("Linked barcode")) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            snackbarHostState.showSnackbar(it)
            inventoryViewModel.clearOperationMessage()
        }
    }
    LaunchedEffect(operationError) {
        operationError?.let {
            snackbarHostState.showSnackbar(it)
            inventoryViewModel.clearOperationError()
        }
    }

    var lastScanned by remember { mutableStateOf<String?>(null) }
    var scanResult by remember { mutableStateOf<InventoryViewModel.ScanResult?>(null) }
    var showItemDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showScanCheckoutDialog by remember { mutableStateOf(false) }
    var showScanReturnDialog by remember { mutableStateOf(false) }

    val topSpacing = if (adaptiveInfo.isTwoPane) 8.dp else 24.dp

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
        scanResult = null
        showItemDialog = false
        showCreateDialog = false
        showEditDialog = false
        showLinkDialog = false
        showScanCheckoutDialog = false
        showScanReturnDialog = false
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val scannedValue = barcode.displayValue ?: barcode.rawValue.orEmpty()
                lastScanned = scannedValue
                scope.launch { snackbarHostState.showSnackbar("Scanned: $scannedValue") }

                scope.launch {
                    val result = inventoryViewModel.lookupScannedBarcode(scannedValue)
                    scanResult = result
                    when (result) {
                        is InventoryViewModel.ScanResult.MatchedByAssetCode -> {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showItemDialog = true
                        }
                        is InventoryViewModel.ScanResult.MatchedByLinkedBarcode -> {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        is InventoryViewModel.ScanResult.NoMatch -> {
                            if (!currentRole.canAddItem) {
                                snackbarHostState.showSnackbar(
                                    "No matching item found. Only Admin can register or link barcodes."
                                )
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                scope.launch { snackbarHostState.showSnackbar("Scan failed: ${e.message}") }
            }
            .addOnCanceledListener {
                scope.launch { snackbarHostState.showSnackbar("Scan cancelled") }
            }
    }

    AdaptiveScreenScaffold(
        title = "Camera Scan",
        showTopBar = !adaptiveInfo.isTwoPane,
        actions = { ConnectivityStatusBadge(isOnline = isOnline) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = if (adaptiveInfo.isTwoPane) 16.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                space = if (adaptiveInfo.isTwoPane) 12.dp else 16.dp,
                alignment = if (adaptiveInfo.isTwoPane) Alignment.Top else Alignment.CenterVertically
            )
        ) {
            Button(
                onClick = { doScan() },
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Start Barcode Scan")
            }

            Text(
                text = "Scan a barcode to identify an item, check out a unit, return it, or link a new barcode to an existing item.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(topSpacing))

            when (val result = scanResult) {
                null -> ScanEmptyStateCard()

                is InventoryViewModel.ScanResult.MatchedByAssetCode -> {
                    ScanResolutionCard(
                        scannedCode = normalizeAssetCode(lastScanned),
                        matchType = "Asset Code",
                        matchedItem = result.item,
                        linkedBarcode = null,
                        canCreateItem = currentRole.canAddItem,
                        canAdjustUsage = currentRole.canAdjustUsage,
                        onViewItem = { showItemDialog = true },
                        onCreateItem = { showCreateDialog = true },
                        onLinkToItem = { showLinkDialog = true },
                        onScanCheckout = { showScanCheckoutDialog = true },
                        onScanReturn = { showScanReturnDialog = true },
                        onScanAgain = { doScan() }
                    )
                }

                is InventoryViewModel.ScanResult.MatchedByLinkedBarcode -> {
                    ScanResolutionCard(
                        scannedCode = normalizeAssetCode(lastScanned),
                        matchType = "Linked Barcode",
                        matchedItem = result.item,
                        linkedBarcode = result.barcode,
                        canCreateItem = currentRole.canAddItem,
                        canAdjustUsage = currentRole.canAdjustUsage,
                        onViewItem = { showItemDialog = true },
                        onCreateItem = {},
                        onLinkToItem = {},
                        onScanCheckout = { showScanCheckoutDialog = true },
                        onScanReturn = { showScanReturnDialog = true },
                        onScanAgain = { doScan() }
                    )
                }

                is InventoryViewModel.ScanResult.NoMatch -> {
                    ScanResolutionCard(
                        scannedCode = result.scannedCode,
                        matchType = null,
                        matchedItem = null,
                        linkedBarcode = null,
                        canCreateItem = currentRole.canAddItem,
                        canAdjustUsage = currentRole.canAdjustUsage,
                        onViewItem = {},
                        onCreateItem = { showCreateDialog = true },
                        onLinkToItem = { showLinkDialog = true },
                        onScanCheckout = {},
                        onScanReturn = {},
                        onScanAgain = { doScan() }
                    )
                }
            }
        }
    }

    // --- Dialogs ---

    val matchedItem = when (val r = scanResult) {
        is InventoryViewModel.ScanResult.MatchedByAssetCode -> r.item
        is InventoryViewModel.ScanResult.MatchedByLinkedBarcode -> r.item
        else -> null
    }

    val matchedBarcode = (scanResult as? InventoryViewModel.ScanResult.MatchedByLinkedBarcode)?.barcode

    if (showItemDialog && matchedItem != null) {
        ItemDetailDialog(
            viewModel = inventoryViewModel,
            itemId = matchedItem.id,
            onDismiss = { showItemDialog = false },
            onEdit = {
                showItemDialog = false
                showEditDialog = true
            },
            onDeleted = {
                showItemDialog = false
                lastScanned = null
                scanResult = null
            }
        )
    }

    if (showEditDialog && matchedItem != null) {
        AddEditItemDialog(
            viewModel = inventoryViewModel,
            dashboardViewModel = dashboardViewModel,
            itemId = matchedItem.id,
            onDismissRequest = { showEditDialog = false },
            onSubmitSuccess = { showEditDialog = false }
        )
    }

    if (showCreateDialog && scanResult is InventoryViewModel.ScanResult.NoMatch) {
        val code = (scanResult as InventoryViewModel.ScanResult.NoMatch).scannedCode
        if (currentRole.canAddItem) {
            AddEditItemDialog(
                viewModel = inventoryViewModel,
                dashboardViewModel = dashboardViewModel,
                prefilledAssetCode = code,
                onDismissRequest = { showCreateDialog = false },
                onSubmitSuccess = { showCreateDialog = false }
            )
        } else {
            LaunchedEffect(code) {
                snackbarHostState.showSnackbar("Only Admin can create items from scan")
                showCreateDialog = false
            }
        }
    }

    if (showLinkDialog && scanResult is InventoryViewModel.ScanResult.NoMatch) {
        val code = (scanResult as InventoryViewModel.ScanResult.NoMatch).scannedCode
        LinkBarcodeDialog(
            scannedCode = code,
            inventoryViewModel = inventoryViewModel,
            onDismiss = { showLinkDialog = false },
            onLinked = {
                showLinkDialog = false
                // Success snackbar comes from operationMessage emitted by linkBarcode in the VM.
                scope.launch {
                    scanResult = inventoryViewModel.lookupScannedBarcode(code)
                }
            }
        )
    }

    if (showScanCheckoutDialog && matchedItem != null && matchedBarcode != null) {
        ScanCheckoutDialog(
            item = matchedItem,
            barcode = matchedBarcode,
            onDismiss = { showScanCheckoutDialog = false },
            onConfirm = { location ->
                inventoryViewModel.scanCheckout(matchedItem, matchedBarcode.id, location)
                showScanCheckoutDialog = false
            }
        )
    }

    if (showScanReturnDialog && matchedItem != null && matchedBarcode != null) {
        ScanReturnDialog(
            item = matchedItem,
            barcode = matchedBarcode,
            onDismiss = { showScanReturnDialog = false },
            onConfirm = { reason ->
                inventoryViewModel.scanReturn(matchedItem, matchedBarcode.id, reason)
                showScanReturnDialog = false
            }
        )
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
private fun ScanResolutionCard(
    scannedCode: String,
    matchType: String?,
    matchedItem: InventoryItem?,
    linkedBarcode: LinkedBarcode?,
    canCreateItem: Boolean,
    canAdjustUsage: Boolean,
    onViewItem: () -> Unit,
    onCreateItem: () -> Unit,
    onLinkToItem: () -> Unit,
    onScanCheckout: () -> Unit,
    onScanReturn: () -> Unit,
    onScanAgain: () -> Unit
) {
    val isMatch = matchedItem != null
    val isLinkedMatch = linkedBarcode != null
    val accentContainer = if (isMatch) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
    }
    val accentContent = if (isMatch) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.tertiary
    }

    OutlinedCard(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accentContainer
                ) {
                    Box(
                        modifier = Modifier.padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isMatch) Icons.Default.CheckCircle else Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = accentContent
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isLinkedMatch -> "Unit Identified"
                            isMatch -> "Item Found"
                            else -> "No Matching Item"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            isLinkedMatch -> "This barcode is linked to an inventory item as an individual unit."
                            isMatch -> "This scanned code matches an inventory item's asset code."
                            canCreateItem -> "This barcode is not linked to any item. Create a new item or link it to an existing one."
                            else -> "This barcode is not linked yet. Only Admin can register or link barcodes."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Scanned code display
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Scanned Code",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = scannedCode,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (matchType != null) {
                        Text(
                            text = "Matched via: $matchType",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Item info (when matched)
            if (isMatch && matchedItem != null) {
                Text(
                    text = "Matched item: ${matchedItem.name}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Category: ${matchedItem.category} | Available: ${matchedItem.availableQuantity} | In Use: ${matchedItem.inUseQuantity}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isLinkedMatch && linkedBarcode != null && linkedBarcode.label.isNotBlank()) {
                    Text(
                        text = "Unit label: ${linkedBarcode.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Action buttons
            if (isLinkedMatch && matchedItem != null) {
                // Linked barcode actions: checkout, return, view item
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onScanCheckout,
                        enabled = canAdjustUsage && matchedItem.isUsageTrackable && matchedItem.availableQuantity > 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Check Out")
                    }
                    OutlinedButton(
                        onClick = onScanReturn,
                        enabled = canAdjustUsage && matchedItem.inUseQuantity > 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Return")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onViewItem,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("View Item")
                    }
                    OutlinedButton(
                        onClick = onScanAgain,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Scan Again")
                    }
                }
            } else if (isMatch) {
                // Asset code match: view item, scan again
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onViewItem,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open Item")
                    }
                    OutlinedButton(
                        onClick = onScanAgain,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Scan Again")
                    }
                }
            } else {
                // No match: create item, link to existing, scan again
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCreateItem,
                        enabled = canCreateItem,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (canCreateItem) "Create Item" else "Admin Only")
                    }
                    OutlinedButton(
                        onClick = onLinkToItem,
                        enabled = canCreateItem,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Link")
                    }
                }
                OutlinedButton(
                    onClick = onScanAgain,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Scan Again")
                }
            }
        }
    }
}

@Composable
private fun ScanEmptyStateCard() {
    AppEmptyStateCard(
        icon = Icons.Default.QrCodeScanner,
        title = "Ready to Scan",
        subtitle = "Scan a barcode to look up items, check out units, or link new barcodes to your inventory."
    )
}

@Composable
private fun LinkBarcodeDialog(
    scannedCode: String,
    inventoryViewModel: InventoryViewModel,
    onDismiss: () -> Unit,
    onLinked: () -> Unit
) {
    val allItems by inventoryViewModel.allItems.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<InventoryItem?>(null) }
    var label by remember { mutableStateOf("") }

    val filteredItems = remember(searchQuery, allItems) {
        if (searchQuery.isBlank()) allItems
        else allItems.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.assetCode.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Barcode to Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Link \"$scannedCode\" to an existing inventory item as an individual unit barcode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search items") },
                    placeholder = { Text("Name, asset code, or category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedItem != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Selected: ${selectedItem!!.name}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${selectedItem!!.category} | ${selectedItem!!.assetCode}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (selectedItem == null) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.height(200.dp)
                    ) {
                        LazyColumn {
                            items(filteredItems) { item ->
                                Surface(
                                    onClick = { selectedItem = item },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${item.category} | ${item.assetCode} | Qty: ${item.quantity}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Unit label (optional)") },
                    placeholder = { Text("e.g., Unit #3, SN: ABC123") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedItem?.let {
                        inventoryViewModel.linkBarcode(it.id, scannedCode, label)
                        onLinked()
                    }
                },
                enabled = selectedItem != null
            ) {
                Text("Link Barcode")
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
private fun ScanCheckoutDialog(
    item: InventoryItem,
    barcode: LinkedBarcode,
    onDismiss: () -> Unit,
    onConfirm: (location: String) -> Unit
) {
    var location by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check Out Unit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Check out 1 unit of \"${item.name}\" using scanned barcode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (barcode.label.isNotBlank()) {
                    Text(
                        text = "Unit: ${barcode.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "Barcode: ${barcode.barcodeValue}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                onClick = { onConfirm(location.trim()) },
                enabled = location.isNotBlank()
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
private fun ScanReturnDialog(
    item: InventoryItem,
    barcode: LinkedBarcode,
    onDismiss: () -> Unit,
    onConfirm: (reason: String) -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Return Unit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Return 1 unit of \"${item.name}\" using scanned barcode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (barcode.label.isNotBlank()) {
                    Text(
                        text = "Unit: ${barcode.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "Barcode: ${barcode.barcodeValue}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    placeholder = { Text("e.g., Task completed") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason.trim()) }
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
