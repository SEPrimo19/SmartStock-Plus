package com.example.smartstock.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.smartstock.core.labels.LabelFormat
import com.example.smartstock.core.labels.LabelGenerator
import com.example.smartstock.data.entity.InventoryItem

/**
 * Shows the generated QR / Barcode label on screen with actions to
 * download it to the gallery, print it, or share it. The user can flip
 * between QR and barcode without leaving the dialog.
 */
@Composable
fun LabelPreviewDialog(
    item: InventoryItem,
    initialFormat: LabelFormat,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var format by remember { mutableStateOf(initialFormat) }
    // Re-render whenever the chosen format changes.
    val bitmap = remember(item.id, item.lastUpdated, format) {
        LabelGenerator.buildLabelBitmap(item, format)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (format == LabelFormat.QrCode) "QR Code" else "Barcode") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = androidx.compose.ui.graphics.Color.White,
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 2.dp
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "${item.name} label",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(8.dp)
                    )
                }
                Text(
                    text = item.assetCode,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
                TextButton(
                    onClick = {
                        format = if (format == LabelFormat.QrCode) {
                            LabelFormat.Barcode
                        } else {
                            LabelFormat.QrCode
                        }
                    },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        if (format == LabelFormat.QrCode) Icons.Default.ViewWeek
                        else Icons.Default.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        if (format == LabelFormat.QrCode) "Show barcode instead"
                        else "Show QR code instead"
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = {
                    val saved = LabelGenerator.saveToGallery(context, item, format)
                    if (saved) {
                        Toast.makeText(
                            context,
                            "Saved to Pictures/SmartStock",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        // Older devices: fall back to the share sheet so the
                        // user can still save the image somewhere.
                        launchShare(context, item, format)
                    }
                }) {
                    Icon(Icons.Default.Download, contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp))
                    Text("Download")
                }
                TextButton(onClick = {
                    runCatching { LabelGenerator.printLabel(context, item, format) }
                        .onFailure {
                            Toast.makeText(
                                context, "Printing not available.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }) {
                    Icon(Icons.Default.Print, contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp))
                    Text("Print")
                }
                TextButton(onClick = { launchShare(context, item, format) }) {
                    Icon(Icons.Default.Share, contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp))
                    Text("Share")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun launchShare(
    context: android.content.Context,
    item: InventoryItem,
    format: LabelFormat
) {
    runCatching {
        val intent = LabelGenerator.generateAndShare(context, item, format)
        context.startActivity(
            Intent.createChooser(intent, "Share label").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }.onFailure {
        Toast.makeText(context, "Unable to share the label.", Toast.LENGTH_SHORT).show()
    }
}
