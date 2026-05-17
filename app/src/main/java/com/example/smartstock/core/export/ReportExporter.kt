package com.example.smartstock.core.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.smartstock.data.entity.InventoryItem
import com.example.smartstock.data.entity.ItemUsageRecord
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    private const val FILE_AUTHORITY_SUFFIX = ".fileprovider"
    private val DATE_FMT = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    private val FILE_TS_FMT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun exportCsv(
        context: Context,
        records: List<ItemUsageRecord>,
        itemNameById: Map<Int, String>
    ): Intent {
        val builder = StringBuilder()
        builder.append("ID,Item,Quantity,Location,Used By,Checked Out,Returned At,Return Reason,Status\n")
        for (r in records) {
            builder.append(csvCell(r.id.toString())).append(',')
            builder.append(csvCell(itemNameById[r.itemId] ?: "Item #${r.itemId}")).append(',')
            builder.append(csvCell(r.quantity.toString())).append(',')
            builder.append(csvCell(r.location)).append(',')
            builder.append(csvCell(r.usedBy)).append(',')
            builder.append(csvCell(DATE_FMT.format(Date(r.checkedOutAt)))).append(',')
            builder.append(csvCell(r.returnedAt?.let { DATE_FMT.format(Date(it)) } ?: "")).append(',')
            builder.append(csvCell(r.returnReason ?: "")).append(',')
            builder.append(csvCell(r.status)).append('\n')
        }
        val file = writeToCache(context, "usage_report_${FILE_TS_FMT.format(Date())}.csv", builder.toString().toByteArray())
        return shareIntent(context, file, "text/csv", "SmartStock+ Usage Report (CSV)")
    }

    /** Full inventory snapshot as CSV, returned as a share intent. */
    fun exportInventoryCsv(
        context: Context,
        items: List<InventoryItem>
    ): Intent {
        val builder = StringBuilder()
        builder.append(
            "Asset Code,Name,Category,Quantity,In Use,Available,Condition,Status,Location,Description\n"
        )
        for (i in items) {
            builder.append(csvCell(i.assetCode)).append(',')
            builder.append(csvCell(i.name)).append(',')
            builder.append(csvCell(i.category)).append(',')
            builder.append(csvCell(i.quantity.toString())).append(',')
            builder.append(csvCell(i.inUseQuantity.toString())).append(',')
            builder.append(csvCell(i.availableQuantity.toString())).append(',')
            builder.append(csvCell(i.condition)).append(',')
            builder.append(csvCell(i.status)).append(',')
            builder.append(csvCell(i.location ?: "")).append(',')
            builder.append(csvCell(i.description ?: "")).append('\n')
        }
        val file = writeToCache(
            context,
            "inventory_${FILE_TS_FMT.format(Date())}.csv",
            builder.toString().toByteArray()
        )
        return shareIntent(context, file, "text/csv", "SmartStock+ Inventory Export (CSV)")
    }

    fun exportPdf(
        context: Context,
        records: List<ItemUsageRecord>,
        itemNameById: Map<Int, String>
    ): Intent {
        val doc = PdfDocument()
        val pageWidth = 595  // A4 portrait at 72dpi
        val pageHeight = 842
        val margin = 32f
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f
            isFakeBoldText = true
        }
        val headerPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 11f
            isFakeBoldText = true
        }
        val cellPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
        }
        val mutedPaint = Paint().apply {
            color = Color.GRAY
            textSize = 10f
        }
        val rowHeight = 18f
        val columns = listOf(
            "Item" to 160f,
            "Qty" to 36f,
            "Location" to 110f,
            "Used By" to 90f,
            "Checked Out" to 100f,
            "Status" to 60f
        )

        var pageIndex = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas
        var y = margin + 18f

        canvas.drawText("SmartStock+ Usage Report", margin, y, titlePaint)
        y += 16f
        canvas.drawText("Generated: ${DATE_FMT.format(Date())}    Records: ${records.size}", margin, y, mutedPaint)
        y += 22f

        fun drawHeader(yPos: Float) {
            var x = margin
            for ((label, width) in columns) {
                canvas.drawText(label, x, yPos, headerPaint)
                x += width
            }
            canvas.drawLine(margin, yPos + 4f, pageWidth - margin, yPos + 4f, mutedPaint)
        }

        drawHeader(y)
        y += rowHeight

        for (r in records) {
            if (y > pageHeight - margin) {
                doc.finishPage(page)
                pageIndex += 1
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex).create()
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                y = margin + 18f
                drawHeader(y)
                y += rowHeight
            }
            var x = margin
            val cells = listOf(
                itemNameById[r.itemId] ?: "Item #${r.itemId}",
                r.quantity.toString(),
                r.location,
                r.usedBy,
                DATE_FMT.format(Date(r.checkedOutAt)),
                r.status
            )
            cells.forEachIndexed { idx, value ->
                val width = columns[idx].second
                canvas.drawText(truncate(value, width, cellPaint), x, y, cellPaint)
                x += width
            }
            y += rowHeight
        }

        doc.finishPage(page)
        val file = File(exportsDir(context), "usage_report_${FILE_TS_FMT.format(Date())}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()

        return shareIntent(context, file, "application/pdf", "SmartStock+ Usage Report (PDF)")
    }

    private fun csvCell(raw: String): String {
        val needsQuote = raw.contains(',') || raw.contains('"') || raw.contains('\n')
        val escaped = raw.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }

    private fun truncate(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth - 4f) return text
        var s = text
        while (s.length > 1 && paint.measureText("$s…") > maxWidth - 4f) {
            s = s.dropLast(1)
        }
        return "$s…"
    }

    private fun exportsDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun writeToCache(context: Context, name: String, bytes: ByteArray): File {
        val file = File(exportsDir(context), name)
        file.writeBytes(bytes)
        return file
    }

    private fun shareIntent(context: Context, file: File, mimeType: String, subject: String): Intent {
        val authority = context.packageName + FILE_AUTHORITY_SUFFIX
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
