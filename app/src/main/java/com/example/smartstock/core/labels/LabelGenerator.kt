package com.example.smartstock.core.labels

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.print.PrintHelper
import com.example.smartstock.data.entity.InventoryItem
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LabelGenerator {

    private const val FILE_AUTHORITY_SUFFIX = ".fileprovider"
    private val FILE_TS_FMT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /** The on-screen preview bitmap for [item] in the chosen [format]. */
    fun buildLabelBitmap(item: InventoryItem, format: LabelFormat): Bitmap =
        renderLabel(item, format)

    /**
     * Save the label PNG to the device gallery (Pictures/SmartStock) via
     * MediaStore — no storage permission needed on API 29+. Returns true
     * on success; the caller falls back to Share on false (e.g. API < 29
     * where a public-gallery insert needs WRITE_EXTERNAL_STORAGE).
     */
    fun saveToGallery(context: Context, item: InventoryItem, format: LabelFormat): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val bitmap = renderLabel(item, format)
        val name = "label_${item.assetCode}_${FILE_TS_FMT.format(Date())}.png"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/SmartStock"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection =
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)!!.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        }.getOrElse {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }

    /** Hand the label bitmap to the system print dialog (also offers Save-as-PDF). */
    fun printLabel(context: Context, item: InventoryItem, format: LabelFormat) {
        PrintHelper(context).apply {
            scaleMode = PrintHelper.SCALE_MODE_FIT
        }.printBitmap("Label ${item.assetCode}", renderLabel(item, format))
    }

    fun generateAndShare(context: Context, item: InventoryItem, format: LabelFormat): Intent {
        val bitmap = renderLabel(item, format)
        val file = File(labelsDir(context), "label_${item.assetCode}_${FILE_TS_FMT.format(Date())}.png")
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return shareIntent(context, file, "Label for ${item.name}")
    }

    private fun renderLabel(item: InventoryItem, format: LabelFormat): Bitmap {
        val width = 800
        val height = if (format == LabelFormat.QrCode) 480 else 360
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val borderPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        canvas.drawRect(Rect(8, 8, width - 8, height - 8), borderPaint)

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 36f
            isAntiAlias = true
            isFakeBoldText = true
        }
        val codePaint = Paint().apply {
            color = Color.BLACK
            textSize = 30f
            isAntiAlias = true
        }
        val mutedPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 24f
            isAntiAlias = true
        }

        val codeBitmap = when (format) {
            LabelFormat.QrCode -> encodeQr(item.assetCode, 360, 360)
            LabelFormat.Barcode -> encodeBarcode(item.assetCode, 480, 160)
        }

        if (format == LabelFormat.QrCode) {
            canvas.drawBitmap(codeBitmap, 40f, 60f, null)
            val textX = 440f
            canvas.drawText(item.name.take(28), textX, 100f, titlePaint)
            canvas.drawText(item.assetCode, textX, 150f, codePaint)
            canvas.drawText("Category: ${item.category.take(20)}", textX, 200f, mutedPaint)
            item.location?.takeIf { it.isNotBlank() }?.let {
                canvas.drawText("Location: ${it.take(20)}", textX, 240f, mutedPaint)
            }
            canvas.drawText("Qty: ${item.quantity}", textX, 280f, mutedPaint)
            canvas.drawText("SmartStock+", textX, 360f, mutedPaint)
        } else {
            val barcodeX = (width - codeBitmap.width) / 2f
            canvas.drawText(item.name.take(28), 32f, 60f, titlePaint)
            canvas.drawBitmap(codeBitmap, barcodeX, 90f, null)
            canvas.drawText(
                item.assetCode,
                width / 2f - codePaint.measureText(item.assetCode) / 2f,
                280f,
                codePaint
            )
            canvas.drawText(
                "${item.category.take(18)} · Qty ${item.quantity}",
                32f,
                330f,
                mutedPaint
            )
        }
        return bitmap
    }

    private fun encodeQr(value: String, width: Int, height: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, width, height, hints)
        return matrix.toBitmap()
    }

    private fun encodeBarcode(value: String, width: Int, height: Int): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 0)
        val matrix = MultiFormatWriter().encode(value, BarcodeFormat.CODE_128, width, height, hints)
        return matrix.toBitmap()
    }

    private fun BitMatrix.toBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    private fun labelsDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun shareIntent(context: Context, file: File, subject: String): Intent {
        val authority = context.packageName + FILE_AUTHORITY_SUFFIX
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

enum class LabelFormat { QrCode, Barcode }
