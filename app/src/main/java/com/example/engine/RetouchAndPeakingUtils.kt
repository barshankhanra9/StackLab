package com.example.engine

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import com.example.domain.model.RetouchStroke
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object RetouchAndPeakingUtils {

    /**
     * Generates a transparent ARGB_8888 overlay highlighting sharp high-contrast edges
     * in Cyan (medium-high contrast) and Red (peak contrast), matching mirrorless focus peaking.
     */
    fun generateFocusPeakingOverlay(sourceBitmap: Bitmap, maxEdgePx: Int = 1920): Bitmap {
        FocusStackProcessor.ensureOpenCvInitialized()

        val srcW = sourceBitmap.width
        val srcH = sourceBitmap.height
        val maxDim = max(srcW, srcH)
        val scale = if (maxDim > maxEdgePx) maxEdgePx.toFloat() / maxDim.toFloat() else 1f
        val outW = (srcW * scale).roundToInt().coerceAtLeast(1)
        val outH = (srcH * scale).roundToInt().coerceAtLeast(1)

        val rgba = Mat()
        val resized = Mat()
        val gray = Mat()
        val gx = Mat()
        val gy = Mat()
        val mag = Mat()
        val meanMat = MatOfDouble()
        val stdMat = MatOfDouble()

        return try {
            Utils.bitmapToMat(sourceBitmap, rgba)
            if (outW != srcW || outH != srcH) {
                Imgproc.resize(rgba, resized, Size(outW.toDouble(), outH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
                Imgproc.cvtColor(resized, gray, Imgproc.COLOR_RGBA2GRAY)
            } else {
                Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            }

            Imgproc.Sobel(gray, gx, CvType.CV_32F, 1, 0, 3)
            Imgproc.Sobel(gray, gy, CvType.CV_32F, 0, 1, 3)
            Core.magnitude(gx, gy, mag)

            Core.meanStdDev(mag, meanMat, stdMat)
            val mean = meanMat.toArray().firstOrNull() ?: 20.0
            val std = stdMat.toArray().firstOrNull() ?: 25.0

            val cyanThreshold = max(45.0, mean + 1.65 * std).toFloat()
            val redThreshold = max(95.0, mean + 3.05 * std).toFloat()

            val pixelCount = outW * outH
            val magData = FloatArray(pixelCount)
            mag.get(0, 0, magData)

            val pixels = IntArray(pixelCount)
            val cyanColor = 0xD422D3EE.toInt()
            val redColor = 0xEBFF3B30.toInt()

            for (i in 0 until pixelCount) {
                val m = magData[i]
                pixels[i] = when {
                    m >= redThreshold -> redColor
                    m >= cyanThreshold -> cyanColor
                    else -> 0
                }
            }

            Bitmap.createBitmap(pixels, outW, outH, Bitmap.Config.ARGB_8888)
        } finally {
            rgba.release()
            resized.release()
            gray.release()
            gx.release()
            gy.release()
            mag.release()
            meanMat.release()
            stdMat.release()
        }
    }

    /**
     * Composites a single feathered retouch brush stroke from [sourceBitmap] onto mutable [canvasBitmap].
     * Uses saveLayerAlpha + BlurMaskFilter + SRC_IN inside the stroke's dirty bounding box so
     * single-stroke opacity never over-accumulates on self-overlapping paths.
     */
    fun applyRetouchStroke(
        canvasBitmap: Bitmap,
        sourceBitmap: Bitmap,
        stroke: RetouchStroke
    ) {
        if (stroke.points.isEmpty()) return
        val w = canvasBitmap.width.toFloat()
        val h = canvasBitmap.height.toFloat()
        val minDim = min(w, h)
        val radiusPx = (stroke.brushSizeFraction * minDim * 0.5f).coerceAtLeast(2f)
        val feather = stroke.feather.coerceIn(0f, 1f)
        val blurRadius = if (feather > 0.02f) (radiusPx * feather * 0.75f).coerceAtLeast(1f) else 0f
        val coreRadius = (radiusPx - blurRadius * 0.35f).coerceAtLeast(1.5f)

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        val path = Path()
        stroke.points.forEachIndexed { idx, pt ->
            val x = pt.u.coerceIn(0f, 1f) * w
            val y = pt.v.coerceIn(0f, 1f) * h
            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val pad = radiusPx + blurRadius * 2f + 4f
        val dirtyRectF = RectF(
            (minX - pad).coerceAtLeast(0f),
            (minY - pad).coerceAtLeast(0f),
            (maxX + pad).coerceAtMost(w),
            (maxY + pad).coerceAtMost(h)
        )
        if (dirtyRectF.width() <= 1f || dirtyRectF.height() <= 1f) return

        val canvas = Canvas(canvasBitmap)
        val layerAlpha = (stroke.opacity.coerceIn(0.05f, 1f) * 255f).roundToInt().coerceIn(1, 255)
        val saveCount = canvas.saveLayerAlpha(dirtyRectF, layerAlpha)

        try {
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                if (blurRadius > 0f) {
                    maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
                }
            }

            if (stroke.points.size == 1) {
                maskPaint.style = Paint.Style.FILL
                val p0 = stroke.points[0]
                canvas.drawCircle(p0.u.coerceIn(0f, 1f) * w, p0.v.coerceIn(0f, 1f) * h, coreRadius, maskPaint)
            } else {
                maskPaint.style = Paint.Style.STROKE
                maskPaint.strokeWidth = coreRadius * 2f
                maskPaint.strokeCap = Paint.Cap.ROUND
                maskPaint.strokeJoin = Paint.Join.ROUND
                canvas.drawPath(path, maskPaint)
            }

            val srcPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            }
            val srcScaleX = sourceBitmap.width.toFloat() / w
            val srcScaleY = sourceBitmap.height.toFloat() / h
            val srcRect = Rect(
                (dirtyRectF.left * srcScaleX).roundToInt().coerceIn(0, sourceBitmap.width - 1),
                (dirtyRectF.top * srcScaleY).roundToInt().coerceIn(0, sourceBitmap.height - 1),
                (dirtyRectF.right * srcScaleX).roundToInt().coerceIn(1, sourceBitmap.width),
                (dirtyRectF.bottom * srcScaleY).roundToInt().coerceIn(1, sourceBitmap.height)
            )
            canvas.drawBitmap(sourceBitmap, srcRect, dirtyRectF, srcPaint)
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    /**
     * Renders a translucent cyan/amber mask overlay showing all retouched brush regions
     * for the press-and-hold "Show Mask" inspection tool.
     */
    fun renderRetouchMaskOverlay(
        width: Int,
        height: Int,
        strokes: List<RetouchStroke>,
        maxEdgePx: Int = 1080
    ): Bitmap {
        val maxDim = max(width, height).coerceAtLeast(1)
        val scale = if (maxDim > maxEdgePx) maxEdgePx.toFloat() / maxDim.toFloat() else 1f
        val outW = (width * scale).roundToInt().coerceAtLeast(1)
        val outH = (height * scale).roundToInt().coerceAtLeast(1)

        val maskBmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        if (strokes.isEmpty()) return maskBmp

        val canvas = Canvas(maskBmp)
        val minDim = min(outW.toFloat(), outH.toFloat())

        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            val radiusPx = (stroke.brushSizeFraction * minDim * 0.5f).coerceAtLeast(2f)
            val feather = stroke.feather.coerceIn(0f, 1f)
            val blurRadius = if (feather > 0.02f) (radiusPx * feather * 0.75f).coerceAtLeast(1f) else 0f
            val coreRadius = (radiusPx - blurRadius * 0.35f).coerceAtLeast(1.5f)
            val alpha = (stroke.opacity.coerceIn(0.15f, 1f) * 185f).roundToInt().coerceIn(32, 210)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(alpha, 34, 211, 238) // CyanAccent mask
                if (blurRadius > 0f) {
                    maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
                }
            }

            if (stroke.points.size == 1) {
                paint.style = Paint.Style.FILL
                val p0 = stroke.points[0]
                canvas.drawCircle(
                    p0.u.coerceIn(0f, 1f) * outW,
                    p0.v.coerceIn(0f, 1f) * outH,
                    coreRadius,
                    paint
                )
            } else {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = coreRadius * 2f
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                val path = Path()
                stroke.points.forEachIndexed { idx, pt ->
                    val x = pt.u.coerceIn(0f, 1f) * outW
                    val y = pt.v.coerceIn(0f, 1f) * outH
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                canvas.drawPath(path, paint)
            }
        }
        return maskBmp
    }
}
