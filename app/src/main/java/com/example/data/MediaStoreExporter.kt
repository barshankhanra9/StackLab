package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.domain.model.OutputFormat
import com.example.domain.model.OutputSettings
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExportOutcome(
    val uri: Uri,
    val displayPath: String
)

class MediaStoreExporter(private val context: Context) {

    suspend fun saveToGallery(
        bitmap: Bitmap,
        settings: OutputSettings
    ): ExportOutcome = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "StackLab_$timestamp.${settings.outputFormat.extension}"
        val compressFormat = when (settings.outputFormat) {
            OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
            OutputFormat.PNG -> Bitmap.CompressFormat.PNG
        }
        val quality = if (settings.outputFormat == OutputFormat.JPEG) settings.jpegQuality else 100

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, settings.outputFormat.mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/StackLab")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("Could not create MediaStore entry")

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    if (!bitmap.compress(compressFormat, quality, out)) {
                        throw IllegalStateException("Bitmap compression failed")
                    }
                } ?: throw IllegalStateException("Unable to open output stream")

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                ExportOutcome(uri = uri, displayPath = "Pictures/StackLab/$fileName")
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
        } else {
            val appPicturesDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "StackLab"
            ).apply { mkdirs() }
            val outFile = File(appPicturesDir, fileName)
            FileOutputStream(outFile).use { out ->
                if (!bitmap.compress(compressFormat, quality, out)) {
                    throw IllegalStateException("Bitmap compression failed")
                }
            }
            ExportOutcome(
                uri = Uri.fromFile(outFile),
                displayPath = "Pictures/StackLab/$fileName"
            )
        }
    }
}
