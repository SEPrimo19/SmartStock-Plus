package com.example.smartstock.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smartstock.data.entity.InventoryItem
import com.example.smartstock.ui.InventoryViewModel
import com.example.smartstock.ui.adaptive.AdaptiveInfo
import com.example.smartstock.ui.adaptive.AdaptiveScreenScaffold
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class StockTakeStatus { Pending, Verified, Missing }

private val SESSION_LABEL_FMT = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

@Composable
fun StockTakeScreen(
    viewModel: InventoryViewModel,
    adaptiveInfo: AdaptiveInfo,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val items by viewModel.allItems.collectAsStateWithLifecycle()
    val operationMessage by viewModel.operationMessage.collectAsStateWithLifecycle()
    val operationError by viewModel.operationError.collectAsStateWithLifecycle()

    val verifiedIds = remember { mutableStateListOf<Int>() }
    val missingIds = remember { mutableStateListOf<Int>() }
    val unknownScans = remember { mutableStateListOf<String>() }
    var manualCode by remember { mutableStateOf("") }
    var showSubmitConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(operationMessage) {
        operationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearOperationMessage()
        }
    }
    LaunchedEffect(operationError) {
        operationError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearOperationError()
        }
    }

    val pendingItems by remember(items, verifiedIds, missingIds) {
        derivedStateOf {
            val excluded = (verifiedIds + missingIds).toSet()
            items.filter { it.id !in excluded }
        }
    }
    val verifiedItems by remember(items, verifiedIds) {
        derivedStateOf { items.filter { it.id in verifiedIds } }
    }
    val missingItems by remember(items, missingIds) {
        derivedStateOf { items.filter { it.id in missingIds } }
    }

    fun markVerified(item: InventoryItem) {
        missingIds.remove(item.id)
        if (item.id !in verifiedIds) verifiedIds.add(item.id)
    }

    fun markMissing(item: InventoryItem) {
        verifiedIds.remove(item.id)
        if (item.id !in missingIds) missingIds.add(item.id)
    }

    fun resetItem(item: InventoryItem) {
        verifiedIds.remove(item.id)
        missingIds.remove(item.id)
    }

    fun resolveAndVerify(rawValue: String) {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            when (val result = viewModel.lookupScannedBarcode(trimmed)) {
                is InventoryViewModel.ScanResult.MatchedByAssetCode -> {
                    markVerified(result.item)
                    snackbarHostState.showSnackbar("Verified: ${result.item.name}")
                }
                is InventoryViewModel.ScanResult.MatchedByLinkedBarcode -> {
                    markVerified(result.item)
                    snackbarHostState.showSnackbar("Verified: ${result.item.name}")
                }
                is InventoryViewModel.ScanResult.NoMatch -> {
                    if (trimmed !in unknownScans) unknownScans.add(trimmed)
                    snackbarHostState.showSnackbar("No match for \"$trimmed\"")
                }
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

    fun startScan() {
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.displayValue ?: barcode.rawValue.orEmpty()
                if (raw.isNotBlank()) resolveAndVerify(raw)
            }
            .addOnFailureListener { e ->
                scope.launch { snackbarHostState.showSnackbar("Scan failed: ${e.message}") }
            }
    }

    AdaptiveScreenScaffold(
        title = "Stock-Take",
        navigationIcon = {
            TextButton(onClick = onNavigateBack) {
                Text("Back", color = MaterialTheme.colorScheme.onPrimary)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProgressSummaryCard(
                verifiedCount = verifiedItems.size,
                missingCount = missingItems.size,
                pendingCount = pendingItems.size,
                totalCount = items.size,
                unknownCount = unknownScans.size
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Record items",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = manualCode,
                        onValueChange = { manualCode = it },
                        singleLine = true,
                        label = { Text("Asset code or barcode") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                resolveAndVerify(manualCode)
                                manualCode = ""
                            },
                            enabled = manualCode.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Verify")
                        }
                        Button(
                            onClick = { startScan() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Scan")
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                if (verifiedItems.isNotEmpty()) {
                    item {
                        SectionHeader(
                            label = "Verified",
                            count = verifiedItems.size,
                            icon = Icons.Outlined.CheckCircle,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    items(verifiedItems, key = { "v-${it.id}" }) { item ->
                        StockTakeRow(
                            item = item,
                            status = StockTakeStatus.Verified,
                            onPrimary = { resetItem(item) },
                            onSecondary = { markMissing(item) }
                        )
                    }
                }
                if (missingItems.isNotEmpty()) {
                    item {
                        SectionHeader(
                            label = "Missing",
                            count = missingItems.size,
                            icon = Icons.Outlined.Warning,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    items(missingItems, key = { "m-${it.id}" }) { item ->
                        StockTakeRow(
                            item = item,
                            status = StockTakeStatus.Missing,
                            onPrimary = { markVerified(item) },
                            onSecondary = { resetItem(item) }
                        )
                    }
                }
                if (pendingItems.isNotEmpty()) {
                    item {
                        SectionHeader(
                            label = "Pending",
                            count = pendingItems.size,
                            icon = Icons.Outlined.Inventory2,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(pendingItems, key = { "p-${it.id}" }) { item ->
                        StockTakeRow(
                            item = item,
                            status = StockTakeStatus.Pending,
                            onPrimary = { markVerified(item) },
                            onSecondary = { markMissing(item) }
                        )
                    }
                }
                if (unknownScans.isNotEmpty()) {
                    item {
                        SectionHeader(
                            label = "Unknown scans",
                            count = unknownScans.size,
                            icon = Icons.AutoMirrored.Outlined.HelpOutline,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    items(unknownScans, key = { "u-$it" }) { code ->
                        UnknownScanRow(code = code, onDismiss = { unknownScans.remove(code) })
                    }
                }
                if (items.isEmpty()) {
                    item {
                        Text(
                            "No inventory items to reconcile.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        pendingItems.forEach { markMissing(it) }
                    },
                    enabled = pendingItems.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Flag remaining missing")
                }
                Button(
                    onClick = { showSubmitConfirm = true },
                    enabled = verifiedIds.isNotEmpty() || missingIds.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Submit")
                }
            }
        }
    }

    if (showSubmitConfirm) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirm = false },
            title = { Text("Submit reconciliation") },
            text = {
                Text(
                    "${verifiedIds.size} verified, ${missingIds.size} missing. " +
                        "History entries will be written for each. Continue?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val sessionLabel = "stock-take ${SESSION_LABEL_FMT.format(Date())}"
                    viewModel.recordStockTakeReconciliation(
                        sessionLabel = sessionLabel,
                        verifiedItemIds = verifiedIds.toList(),
                        missingItemIds = missingIds.toList()
                    )
                    verifiedIds.clear()
                    missingIds.clear()
                    unknownScans.clear()
                    showSubmitConfirm = false
                }) { Text("Submit") }
            },
            dismissButton = {
                TextButton(onClick = { showSubmitConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProgressSummaryCard(
    verifiedCount: Int,
    missingCount: Int,
    pendingCount: Int,
    totalCount: Int,
    unknownCount: Int
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "$verifiedCount of $totalCount verified",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatPillSmall("Pending", pendingCount, MaterialTheme.colorScheme.surface, Modifier.weight(1f))
                StatPillSmall("Missing", missingCount, MaterialTheme.colorScheme.errorContainer, Modifier.weight(1f))
                StatPillSmall("Unknown", unknownCount, MaterialTheme.colorScheme.secondaryContainer, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatPillSmall(
    label: String,
    value: Int,
    container: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = container,
        modifier = modifier.height(56.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(
    label: String,
    count: Int,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "$label · $count",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun StockTakeRow(
    item: InventoryItem,
    status: StockTakeStatus,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit
) {
    val container = when (status) {
        StockTakeStatus.Verified -> MaterialTheme.colorScheme.tertiaryContainer
        StockTakeStatus.Missing -> MaterialTheme.colorScheme.errorContainer
        StockTakeStatus.Pending -> MaterialTheme.colorScheme.surface
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = container,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${item.assetCode} · qty ${item.quantity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when (status) {
                StockTakeStatus.Pending -> {
                    TextButton(onClick = onPrimary) { Text("Verified") }
                    TextButton(onClick = onSecondary) { Text("Missing") }
                }
                StockTakeStatus.Verified -> {
                    TextButton(onClick = onSecondary) { Text("Missing") }
                    TextButton(onClick = onPrimary) { Text("Undo") }
                }
                StockTakeStatus.Missing -> {
                    TextButton(onClick = onPrimary) { Text("Verified") }
                    TextButton(onClick = onSecondary) { Text("Undo") }
                }
            }
        }
    }
}

@Composable
private fun UnknownScanRow(code: String, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                code,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
