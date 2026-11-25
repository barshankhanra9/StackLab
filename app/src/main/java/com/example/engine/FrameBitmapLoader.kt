package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.example.domain.model.MaxResolutionPreset
import com.example.domain.model.StackFrame
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

data class MemoryBudgetDecision(
    val effectiveMaxEdgePx: Int?,
    val usedProxyMode: Boolean,
    val reason: String?
)

class FrameBitmapLoader(private val context: Context) {

    fun inspectFrames(uris: List<Uri>): List<StackFrame> {
        return uris.mapIndexed { index, uri ->
            val bounds = readImageBounds(uri)
            val orientation = readExifOrientation(uri)
            val rotated = isRotated90Or270(orientation)
            val w = if (rotated) bounds.second else bounds.first
            val h = if (rotated) bounds.first else bounds.second
            StackFrame(
                id = UUID.randomUUID().toString(),
                uri = uri,
                displayName = resolveDisplayName(uri, index),
                width = w.coerceAtLeast(1),
                height = h.coerceAtLeast(1)
            )
        }
    }

    fun evaluateMemoryBudget(
        frameCount: Int,
        requestedPreset: MaxResolutionPreset
    ): MemoryBudgetDecision {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)

        val isLowRamDevice = activityManager?.isLowRamDevice == true
        val availableMb = if (memInfo.availMem > 0L) memInfo.availMem / (1024L * 1024L) else 1024L
        val jvmMaxMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L)

        val autoProxyNeeded = frameCount > 15 || isLowRamDevice || availableMb < 450L || jvmMaxMb < 320L
        val presetCap = requestedPreset.maxEdgePx

        return when {
            autoProxyNeeded -> {
                val capped = if (presetCap != null) minOf(presetCap, PROXY_MAX_EDGE_PX) else PROXY_MAX_EDGE_PX
                val reason = when {
                    frameCount > 15 -> "Auto ${capped}px proxy enabled for ${frameCount}-frame stack"
                    else -> "Auto ${capped}px proxy enabled for device RAM headroom"
                }
                MemoryBudgetDecision(
                    effectiveMaxEdgePx = capped,
                    usedProxyMode = true,
                    reason = reason
                )
            }
            presetCap != null -> {
                MemoryBudgetDecision(
                    effectiveMaxEdgePx = presetCap,
                    usedProxyMode = presetCap <= PROXY_MAX_EDGE_PX,
                    reason = null
                )
            }
            else -> {
                MemoryBudgetDecision(
                    effectiveMaxEdgePx = null,
                    usedProxyMode = false,
                    reason = null
                )
            }
        }
    }

    fun loadNormalizedBitmap(
        uri: Uri,
        targetWidth: Int? = null,
        targetHeight: Int? = null,
        maxEdgePx: Int? = null
    ): Bitmap {
        val (rawW, rawH) = readImageBounds(uri)
        val orientation = readExifOrientation(uri)
        val rotated = isRotated90Or270(orientation)
        val orientedW = if (rotated) rawH else rawW
        val orientedH = if (rotated) rawW else rawH

        val desiredMaxEdge = when {
            targetWidth != null && targetHeight != null -> max(targetWidth, targetHeight)
            maxEdgePx != null -> maxEdgePx
            else -> max(orientedW, orientedH)
        }

        val sampleSize = calculateInSampleSize(orientedW, orientedH, desiredMaxEdge)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IllegalStateException("Unable to decode image frame")

        var orientedBitmap = applyExifTransform(decoded, orientation)
        if (orientedBitmap !== decoded) {
            decoded.recycle()
        }

        if (targetWidth != null && targetHeight != null &&
            (orientedBitmap.width != targetWidth || orientedBitmap.height != targetHeight)
        ) {
            val scaled = Bitmap.createScaledBitmap(orientedBitmap, targetWidth, targetHeight, true)
            if (scaled !== orientedBitmap) {
                orientedBitmap.recycle()
            }
            orientedBitmap = scaled
        } else if (maxEdgePx != null) {
            val currentMax = max(orientedBitmap.width, orientedBitmap.height)
            if (currentMax > maxEdgePx) {
                val scale = maxEdgePx.toFloat() / currentMax.toFloat()
                val newW = (orientedBitmap.width * scale).roundToInt().coerceAtLeast(1)
                val newH = (orientedBitmap.height * scale).roundToInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(orientedBitmap, newW, newH, true)
                if (scaled !== orientedBitmap) {
                    orientedBitmap.recycle()
                }
                orientedBitmap = scaled
            }
        }

        return orientedBitmap
    }

    fun createPreviewCopy(source: Bitmap, maxEdgePx: Int = 1080): Bitmap {
        val currentMax = max(source.width, source.height)
        if (currentMax <= maxEdgePx) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }
        val scale = maxEdgePx.toFloat() / currentMax.toFloat()
        val w = (source.width * scale).roundToInt().coerceAtLeast(1)
        val h = (source.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private fun readImageBounds(uri: Uri): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        return Pair(options.outWidth.coerceAtLeast(1), options.outHeight.coerceAtLeast(1))
    }

    private fun readExifOrientation(uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun isRotated90Or270(orientation: Int): Boolean {
        return orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE
    }

    private fun applyExifTransform(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxEdgePx: Int): Int {
        var inSampleSize = 1
        var curW = width
        var curH = height
        while (max(curW, curH) / 2 >= maxEdgePx) {
            curW /= 2
            curH /= 2
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun resolveDisplayName(uri: Uri, index: Int): String {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (col >= 0) cursor.getString(col) else null
                } else null
            } ?: "Frame_${index + 1}.jpg"
        } catch (_: Exception) {
            "Frame_${index + 1}.jpg"
        }
    }

    companion object {
        const val PROXY_MAX_EDGE_PX = 1600
    }
}
