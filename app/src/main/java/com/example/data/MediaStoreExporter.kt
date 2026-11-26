package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.example.domain.model.OutputFormat
import com.example.domain.model.OutputSettings
import java.io.File
import java.io.FileInputStream
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
        settings: OutputSettings,
        referenceFrameUri: Uri? = null
    ): ExportOutcome = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "StackLab_$timestamp.${settings.outputFormat.extension}"
        val compressFormat = when (settings.outputFormat) {
            OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
            OutputFormat.PNG -> Bitmap.CompressFormat.PNG
        }
        val quality = if (settings.outputFormat == OutputFormat.JPEG) settings.jpegQuality else 100

        // Render to a temporary file first so ExifInterface can cleanly write full JPEG metadata
        // before streaming to MediaStore (reliable across all scoped-storage providers).
        val tempFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.${settings.outputFormat.extension}")
        try {
            FileOutputStream(tempFile).use { out ->
                if (!bitmap.compress(compressFormat, quality, out)) {
                    throw IllegalStateException("Bitmap compression failed")
                }
            }

            if (settings.outputFormat == OutputFormat.JPEG && referenceFrameUri != null) {
                copyExifMetadata(
                    sourceUri = referenceFrameUri,
                    targetFile = tempFile,
                    outputWidth = bitmap.width,
                    outputHeight = bitmap.height
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, settings.outputFormat.mimeType)
                    put(MediaStore.Images.Media.WIDTH, bitmap.width)
                    put(MediaStore.Images.Media.HEIGHT, bitmap.height)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/StackLab")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IllegalStateException("Could not create MediaStore entry")

                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(tempFile).use { input ->
                            input.copyTo(out)
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
                FileInputStream(tempFile).use { input ->
                    FileOutputStream(outFile).use { out ->
                        input.copyTo(out)
                    }
                }
                ExportOutcome(
                    uri = Uri.fromFile(outFile),
                    displayPath = "Pictures/StackLab/$fileName"
                )
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun copyExifMetadata(
        sourceUri: Uri,
        targetFile: File,
        outputWidth: Int,
        outputHeight: Int
    ) {
        try {
            val srcExif = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                ExifInterface(stream)
            } ?: return

            val dstExif = ExifInterface(targetFile.absolutePath)
            for (tag in PRESERVED_EXIF_TAGS) {
                val value = srcExif.getAttribute(tag)
                if (!value.isNullOrEmpty()) {
                    dstExif.setAttribute(tag, value)
                }
            }

            // Pixels are already rotated upright during FrameBitmapLoader normalization
            dstExif.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString()
            )
            dstExif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, outputWidth.toString())
            dstExif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, outputHeight.toString())
            dstExif.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, outputWidth.toString())
            dstExif.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, outputHeight.toString())

            dstExif.saveAttributes()
        } catch (_: Exception) {
            // Do not fail export if source image lacks EXIF or uses an unsupported container
        }
    }

    companion object {
        private val PRESERVED_EXIF_TAGS = listOf(
            // Camera & Lens
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_LENS_SPECIFICATION,
            ExifInterface.TAG_BODY_SERIAL_NUMBER,
            ExifInterface.TAG_CAMERA_OWNER_NAME,
            // Optics & Exposure
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_APERTURE_VALUE,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_SHUTTER_SPEED_VALUE,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_ISO_SPEED,
            ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
            ExifInterface.TAG_EXPOSURE_MODE,
            ExifInterface.TAG_EXPOSURE_PROGRAM,
            ExifInterface.TAG_METERING_MODE,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_LIGHT_SOURCE,
            ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
            ExifInterface.TAG_SUBJECT_DISTANCE,
            ExifInterface.TAG_SCENE_CAPTURE_TYPE,
            ExifInterface.TAG_GAIN_CONTROL,
            ExifInterface.TAG_CONTRAST,
            ExifInterface.TAG_SATURATION,
            ExifInterface.TAG_SHARPNESS,
            // Timestamps
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
            // GPS
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_IMG_DIRECTION,
            ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            // Color & Attribution
            ExifInterface.TAG_COLOR_SPACE,
            ExifInterface.TAG_GAMMA,
            ExifInterface.TAG_WHITE_POINT,
            ExifInterface.TAG_PRIMARY_CHROMATICITIES,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_USER_COMMENT
        )
    }
}
