package com.example.smartstock.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.smartstock.ui.DashboardViewModel
import com.example.smartstock.ui.InventoryViewModel
import com.example.smartstock.ui.SaveItemResult
import com.example.smartstock.ui.adaptive.AdaptiveScreenScaffold
import com.example.smartstock.ui.components.ConnectivityStatusBadge
import com.example.smartstock.ui.components.ModalDialogHeader
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import android.content.Context

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditItemScreen(
    navController: NavController,
    viewModel: InventoryViewModel,
    dashboardViewModel: DashboardViewModel,
    itemId: Int? = null,
    prefilledAssetCode: String? = null
) {
    val isOnline by dashboardViewModel.isOnline.collectAsStateWithLifecycle()

    AdaptiveScreenScaffold(
        title = if (itemId == null || itemId == 0) "Add Item" else "Edit Item",
        navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = { ConnectivityStatusBadge(isOnline = isOnline) }
    ) { paddingValues ->
        AddEditItemFormContent(
            viewModel = viewModel,
            dashboardViewModel = dashboardViewModel,
            itemId = itemId,
            prefilledAssetCode = prefilledAssetCode,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            onSubmitSuccess = { navController.popBackStack() }
        )
    }
}

@Composable
fun AddEditItemDialog(
    viewModel: InventoryViewModel,
    dashboardViewModel: DashboardViewModel,
    itemId: Int? = null,
    prefilledAssetCode: String? = null,
    onDismissRequest: () -> Unit,
    onSubmitSuccess: () -> Unit = {}
) {
    val title = if (itemId == null || itemId == 0) "Add Item" else "Edit Item"
    val configuration = LocalConfiguration.current
    val isCompactWidth = configuration.screenWidthDp < 600
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = if (isCompactWidth) RoundedCornerShape(0.dp) else RoundedCornerShape(24.dp),
            shadowElevation = if (isCompactWidth) 0.dp else 8.dp,
            modifier = if (isCompactWidth) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth(0.94f)
                    .widthIn(max = 640.dp)
                    .fillMaxHeight(0.92f)
            }
        ) {
            Column(modifier = Modifier.fillMaxHeight()) {
                ModalDialogHeader(
                    title = title,
                    subtitle = if (itemId == null || itemId == 0) {
                        "Create a new inventory record with quantity, status, and location."
                    } else {
                        "Update the selected inventory record and keep its usage data accurate."
                    },
                    onClose = onDismissRequest
                )
                AddEditItemFormContent(
                    viewModel = viewModel,
                    dashboardViewModel = dashboardViewModel,
                    itemId = itemId,
                    prefilledAssetCode = prefilledAssetCode,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                    onSubmitSuccess = {
                        onSubmitSuccess()
                        onDismissRequest()
                    },
                    showHeaderInForm = false
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddEditItemFormContent(
    viewModel: InventoryViewModel,
    dashboardViewModel: DashboardViewModel,
    itemId: Int?,
    prefilledAssetCode: String? = null,
    modifier: Modifier = Modifier,
    onSubmitSuccess: () -> Unit,
    showHeaderInForm: Boolean = false
) {
    val context = LocalContext.current
    val isAddMode = itemId == null || itemId == 0
    val isOnline by dashboardViewModel.isOnline.collectAsStateWithLifecycle()
    val existingItem by viewModel.getItem(itemId ?: 0).collectAsStateWithLifecycle(initialValue = null)
    val categories by viewModel.categoryNames.collectAsStateWithLifecycle()
    val statuses by viewModel.statusNames.collectAsStateWithLifecycle()
    val conditions = listOf("New", "Good", "Fair", "Poor")
    val editableStatuses = statuses.filter { it != "In-Use" }
    val resolvedPrefilledAssetCode = prefilledAssetCode?.trim()?.takeIf { it.isNotBlank() }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val nameFocusRequester = remember { FocusRequester() }
    val quantityFocusRequester = remember { FocusRequester() }
    val locationFocusRequester = remember { FocusRequester() }

    var name by rememberSaveable(itemId) { mutableStateOf("") }
    var category by rememberSaveable(itemId) { mutableStateOf("Equipment") }
    var quantity by rememberSaveable(itemId) { mutableStateOf("") }
    var status by rememberSaveable(itemId) { mutableStateOf("Available") }
    var location by rememberSaveable(itemId) { mutableStateOf("") }
    var condition by rememberSaveable(itemId) { mutableStateOf("New") }
    var description by rememberSaveable(itemId) { mutableStateOf("") }
    var assetCodeDisplay by rememberSaveable(itemId) { mutableStateOf("") }
    var imageUri by rememberSaveable(itemId) { mutableStateOf<String?>(null) }
    var initializedFromExisting by rememberSaveable(itemId) { mutableStateOf(false) }
    var attemptedSubmit by rememberSaveable(itemId) { mutableStateOf(false) }
    var nameTouched by rememberSaveable(itemId) { mutableStateOf(false) }
    var quantityTouched by rememberSaveable(itemId) { mutableStateOf(false) }

    // Camera capture support
    var tempCameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        // GetContent grants a one-shot read permission that's revoked the
        // moment our process dies. Copy the picked image into filesDir so
        // AsyncImage can keep loading it after a restart.
        uri?.let { src ->
            persistImageFromUri(context, src)?.let { imageUri = it }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            imageUri = tempCameraUri
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            val photoFile = createPersistentImageFile(context)
            val photoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            tempCameraUri = Uri.fromFile(photoFile).toString()
            cameraLauncher.launch(photoUri)
        } else {
            scope.launch { snackbarHostState.showSnackbar("Camera permission is required") }
        }
    }

    val parsedQuantity = quantity.toIntOrNull()
    val isFormValid = name.isNotBlank() && parsedQuantity != null && parsedQuantity > 0 && category.isNotBlank()

    LaunchedEffect(existingItem?.id, isAddMode) {
        if (!isAddMode && existingItem != null && !initializedFromExisting) {
            name = existingItem?.name.orEmpty()
            category = existingItem?.category ?: "Equipment"
            quantity = existingItem?.quantity?.toString().orEmpty()
            status = if (existingItem?.status == "In-Use") "Available" else (existingItem?.status ?: "Available")
            location = existingItem?.location.orEmpty()
            condition = existingItem?.condition ?: "New"
            description = existingItem?.description.orEmpty()
            assetCodeDisplay = existingItem?.assetCode.orEmpty()
            imageUri = existingItem?.imageUri
            initializedFromExisting = true
        }
    }

    LaunchedEffect(isAddMode, resolvedPrefilledAssetCode) {
        if (isAddMode && resolvedPrefilledAssetCode != null) {
            assetCodeDisplay = resolvedPrefilledAssetCode
        }
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        if (showHeaderInForm) {
            Text(
                text = if (isAddMode) "Add Item" else "Edit Item",
                style = MaterialTheme.typography.titleLarge
            )
        }

        OutlinedTextField(
            value = if (isAddMode) assetCodeDisplay.ifBlank { "Generated on save" } else assetCodeDisplay,
            onValueChange = {},
            label = { Text("Asset Code") },
            enabled = false,
            colors = TextFieldDefaults.colors(
                focusedContainerColor   = if (isOnline) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = if (isOnline) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                disabledContainerColor  = if (isOnline) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )

        val showNameError = (nameTouched || attemptedSubmit) && name.isBlank()
        OutlinedTextField(
            value = name,
            onValueChange = { value: String ->
                nameTouched = true
                name = value
            },
            label = { Text("Item Name") },
            isError = showNameError,
            singleLine = true,
            supportingText = {
                if (showNameError) {
                    Text("Item name is required")
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor   = if (isOnline) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = if (isOnline) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                disabledContainerColor  = if (isOnline) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { quantityFocusRequester.requestFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(nameFocusRequester)
        )

        Text("Category", style = MaterialTheme.typography.labelLarge)
        var showCustomCategoryField by rememberSaveable(itemId) { mutableStateOf(false) }
        // FlowRow wraps onto multiple lines once there are more categories
        // than fit in one row — without this, chips overflow horizontally
        // and the "Specify Category" + button gets pushed to its own line
        // with awkward empty space above it.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            categories.forEach { cat ->
                FilterChip(
                    selected = category == cat,
                    onClick = {
                        category = cat
                        showCustomCategoryField = false
                    },
                    label = { Text(cat) }
                )
            }
            FilterChip(
                selected = showCustomCategoryField,
                onClick = {
                    showCustomCategoryField = !showCustomCategoryField
                    if (showCustomCategoryField) category = ""
                },
                leadingIcon = {
                    Icon(
                        if (showCustomCategoryField) Icons.Default.Remove else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text(if (showCustomCategoryField) "Cancel" else "Specify") }
            )
        }
        if (showCustomCategoryField) {
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Specify Category") },
                placeholder = { Text("e.g., Furniture, Electronics") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor   = if (isOnline) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = if (isOnline) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor  = if (isOnline) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        val showQuantityGate = quantityTouched || attemptedSubmit
        val showQuantityError = showQuantityGate && (parsedQuantity == null || parsedQuantity <= 0)
        OutlinedTextField(
            value = quantity,
            onValueChange = { value: String ->
                if (value.all { char: Char -> char.isDigit() }) {
                    quantityTouched = true
                    quantity = value
                }
            },
            label = { Text("Quantity") },
            isError = showQuantityError,
            singleLine = true,
            supportingText = {
                if (showQuantityGate && parsedQuantity == null) {
                    Text("Quantity must be a number")
                } else if (showQuantityGate && parsedQuantity != null && parsedQuantity <= 0) {
                    Text("Quantity must be greater than 0")
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor   = if (isOnline) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = if (isOnline) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                disabledContainerColor  = if (isOnline) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { locationFocusRequester.requestFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(quantityFocusRequester)
        )

        if (!isAddMode && existingItem != null) {
            Text(
                text = "Available: ${existingItem?.availableQuantity ?: 0} | In Use: ${existingItem?.inUseQuantity ?: 0}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text("Status", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            editableStatuses.take(3).forEach { stat ->
                FilterChip(
                    selected = status == stat,
                    onClick = { status = stat },
                    label = { Text(stat) }
                )
            }
        }

        if (editableStatuses.size > 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                editableStatuses.drop(3).forEach { stat ->
                    FilterChip(
                        selected = status == stat,
                        onClick = { status = stat },
                        label = { Text(stat) }
                    )
                }
            }
        }

        Text("Condition", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            conditions.forEach { cond ->
                FilterChip(
                    selected = condition == cond,
                    onClick = { condition = cond },
                    label = { Text(cond) }
                )
            }
        }

        OutlinedTextField(
            value = location,
            onValueChange = { value: String ->
                location = value
            },
            label = { Text("Location") },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor   = if (isOnline) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = if (isOnline) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                disabledContainerColor  = if (isOnline) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(locationFocusRequester)
        )

        // ── Image Section ────────────────────────────────────────────────
        Text("Item Photo", style = MaterialTheme.typography.labelLarge)
        if (imageUri != null) {
            Box(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Item photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(12.dp)
                        )
                )
                IconButton(
                    onClick = { imageUri = null },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove photo",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    val photoFile = createPersistentImageFile(context)
                    val photoUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        photoFile
                    )
                    tempCameraUri = Uri.fromFile(photoFile).toString()
                    cameraLauncher.launch(photoUri)
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.padding(4.dp))
                Text("Camera")
            }
            OutlinedButton(onClick = { galleryLauncher.launch("image/*") }) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.padding(4.dp))
                Text("Gallery")
            }
        }

        OutlinedTextField(
            value = description,
            onValueChange = { value: String ->
                description = value
            },
            label = { Text("Description") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor   = if (isOnline) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = if (isOnline) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                disabledContainerColor  = if (isOnline) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            enabled = isFormValid,
            onClick = {
                attemptedSubmit = true
                when (
                    val result = viewModel.prepareItemForSave(
                        existingItem = existingItem,
                        itemId = itemId,
                        assetCodeOverride = resolvedPrefilledAssetCode,
                        name = name,
                        category = category,
                        quantityText = quantity,
                        status = status,
                        condition = condition,
                        location = location,
                        description = description,
                        imageUri = imageUri
                    )
                ) {
                    is SaveItemResult.ValidationError -> {
                        scope.launch { snackbarHostState.showSnackbar(result.message) }
                    }

                    is SaveItemResult.Success -> {
                        if (isAddMode) {
                            viewModel.insertItem(result.item)
                        } else {
                            viewModel.updateItem(result.item)
                        }
                        onSubmitSuccess()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.padding(4.dp))
            Text("Save Item")
        }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

private fun createPersistentImageFile(context: Context): File {
    val dir = File(context.filesDir, "images").apply { mkdirs() }
    return File(dir, "IMG_${UUID.randomUUID()}.jpg")
}

private fun persistImageFromUri(context: Context, sourceUri: Uri): String? {
    return try {
        val target = createPersistentImageFile(context)
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        // Return a file:// URI string. Coil's StringMapper parses model
        // strings as Uris, and Uris without a scheme fall through its
        // fetcher chain (the raw absolutePath silently fails to load).
        Uri.fromFile(target).toString()
    } catch (_: Exception) {
        null
    }
}
