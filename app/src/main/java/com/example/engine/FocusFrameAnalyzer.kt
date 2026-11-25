package com.example.engine

import android.graphics.Bitmap
import com.example.domain.model.FocusRegion
import com.example.domain.model.StackFrame
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt
import kotlinx.coroutines.ensureActive
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video

class FocusFrameAnalyzer(
    private val bitmapLoader: FrameBitmapLoader
) {
    private data class RawAnalysis(
        val exifDistance: Float?,
        val verticalCentroid: Float,
        val peakSharpness: Float,
        val gray256: Mat?
    )

    suspend fun analyzeFrames(frames: List<StackFrame>): List<StackFrame> {
        if (frames.isEmpty()) return frames
        FocusStackProcessor.ensureOpenCvInitialized()

        val rawList = ArrayList<RawAnalysis>(frames.size)
        try {
            for (frame in frames) {
                coroutineContext.ensureActive()
                rawList.add(analyzeSingleFrame(frame))
            }

            // Determine relative magnification via fast 256px ECC against middle frame
            // to detect focus breathing when frames are on a tripod/macro rail.
            val refIdx = frames.size / 2
            val refGray = rawList[refIdx].gray256
            val breathingScales = FloatArray(frames.size) { 1f }
            if (frames.size >= 2 && refGray != null) {
                val ref32 = Mat()
                refGray.convertTo(ref32, CvType.CV_32F, 1.0 / 255.0)
                try {
                    for (i in frames.indices) {
                        coroutineContext.ensureActive()
                        if (i == refIdx) continue
                        val srcGray = rawList[i].gray256 ?: continue
                        if (srcGray.rows() != refGray.rows() || srcGray.cols() != refGray.cols()) continue
                        breathingScales[i] = estimateMagnificationScale(ref32, srcGray)
                    }
                } finally {
                    ref32.release()
                }
            }

            // Decide distance ordering metric:
            // 1. If all frames have EXIF SubjectDistance, use it directly (smaller = Near, larger = Far).
            // 2. Else if focus-breathing scale spread > 0.006, use scale oriented by vertical centroid.
            // 3. Else use vertical sharpness centroid (1 - yCentroid: bottom foreground = Near, top background = Far).
            val allHaveExifDist = rawList.all { it.exifDistance != null }
            val minScale = breathingScales.minOrNull() ?: 1f
            val maxScale = breathingScales.maxOrNull() ?: 1f
            val hasBreathing = (maxScale - minScale) > 0.006f

            // Orient scale polarity using correlation with (1 - verticalCentroid) so bottom-sharp stays Near
            var scalePolarity = 1f
            if (hasBreathing) {
                val meanScale = breathingScales.average().toFloat()
                val meanDistProxy = rawList.map { 1f - it.verticalCentroid }.average().toFloat()
                var cov = 0f
                for (i in rawList.indices) {
                    cov += (breathingScales[i] - meanScale) * ((1f - rawList[i].verticalCentroid) - meanDistProxy)
                }
                if (cov < 0f) scalePolarity = -1f
            }

            val rawScores = frames.indices.map { i ->
                when {
                    allHaveExifDist -> rawList[i].exifDistance ?: 0.5f
                    hasBreathing -> {
                        val normScale = if (scalePolarity > 0f) breathingScales[i] else (2f - breathingScales[i])
                        normScale * 0.7f + (1f - rawList[i].verticalCentroid) * 0.3f
                    }
                    else -> 1f - rawList[i].verticalCentroid
                }
            }

            val minScore = rawScores.minOrNull() ?: 0f
            val maxScore = rawScores.maxOrNull() ?: 1f
            val span = (maxScore - minScore).takeIf { it > 1e-5f } ?: 1f

            // Rank frames from Near (0f) to Far (1f)
            val sortedOrder = frames.indices.sortedBy { rawScores[it] }
            val rankOf = IntArray(frames.size)
            sortedOrder.forEachIndexed { rank, originalIdx ->
                rankOf[originalIdx] = rank
            }

            return frames.mapIndexed { i, frame ->
                val normalizedScore = ((rawScores[i] - minScore) / span).coerceIn(0f, 1f)
                val region = when {
                    frames.size == 1 -> FocusRegion.MID
                    frames.size == 2 -> if (rankOf[i] == 0) FocusRegion.NEAR else FocusRegion.FAR
                    else -> {
                        val relRank = rankOf[i].toFloat() / (frames.size - 1).coerceAtLeast(1)
                        when {
                            relRank < 0.34f -> FocusRegion.NEAR
                            relRank <= 0.67f -> FocusRegion.MID
                            else -> FocusRegion.FAR
                        }
                    }
                }
                frame.copy(
                    focusRegion = region,
                    focusDistanceScore = normalizedScore,
                    peakSharpnessScore = rawList[i].peakSharpness,
                    isAnalyzed = true
                )
            }
        } finally {
            rawList.forEach { it.gray256?.release() }
        }
    }

    private fun analyzeSingleFrame(frame: StackFrame): RawAnalysis {
        val exifDistance = bitmapLoader.readSubjectDistance(frame.uri)
        var bitmap: Bitmap? = null
        val rgba = Mat()
        val gray = Mat()
        val blurred = Mat()
        val lap = Mat()
        val energy = Mat()
        val smoothEnergy = Mat()

        return try {
            bitmap = bitmapLoader.loadNormalizedBitmap(frame.uri, maxEdgePx = 256)
            Utils.bitmapToMat(bitmap, rgba)
            bitmap.recycle()
            bitmap = null

            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(3.0, 3.0), 0.0)
            Imgproc.Laplacian(blurred, lap, CvType.CV_32F, 3)
            Core.multiply(lap, lap, energy)
            Imgproc.GaussianBlur(energy, smoothEnergy, Size(15.0, 15.0), 0.0)

            val rows = smoothEnergy.rows()
            val cols = smoothEnergy.cols()
            val data = FloatArray(rows * cols)
            smoothEnergy.get(0, 0, data)

            var maxVal = 0f
            for (v in data) {
                if (v > maxVal) maxVal = v
            }

            val threshold = maxVal * 0.35f
            var weightSum = 0.0
            var yWeightedSum = 0.0
            for (r in 0 until rows) {
                val yNorm = r.toDouble() / (rows - 1).coerceAtLeast(1)
                val rowOffset = r * cols
                for (c in 0 until cols) {
                    val v = data[rowOffset + c]
                    if (v >= threshold && v > 0f) {
                        val w = v.toDouble()
                        weightSum += w
                        yWeightedSum += w * yNorm
                    }
                }
            }

            val verticalCentroid = if (weightSum > 1e-6) {
                (yWeightedSum / weightSum).toFloat().coerceIn(0f, 1f)
            } else {
                0.5f
            }

            RawAnalysis(
                exifDistance = exifDistance,
                verticalCentroid = verticalCentroid,
                peakSharpness = maxVal,
                gray256 = gray.clone()
            )
        } catch (_: Exception) {
            RawAnalysis(
                exifDistance = exifDistance,
                verticalCentroid = 0.5f,
                peakSharpness = 0f,
                gray256 = null
            )
        } finally {
            bitmap?.recycle()
            rgba.release()
            gray.release()
            blurred.release()
            lap.release()
            energy.release()
            smoothEnergy.release()
        }
    }

    private fun estimateMagnificationScale(ref32: Mat, srcGray: Mat): Float {
        val src32 = Mat()
        val warp = Mat.eye(2, 3, CvType.CV_32F)
        return try {
            srcGray.convertTo(src32, CvType.CV_32F, 1.0 / 255.0)
            val criteria = TermCriteria(TermCriteria.COUNT or TermCriteria.EPS, 25, 1e-3)
            Video.findTransformECC(ref32, src32, warp, Video.MOTION_AFFINE, criteria)
            val a = warp.get(0, 0)[0]
            val b = warp.get(0, 1)[0]
            val c = warp.get(1, 0)[0]
            val d = warp.get(1, 1)[0]
            val det = a * d - b * c
            if (det in 0.6..1.6) sqrt(det).toFloat() else 1f
        } catch (_: Exception) {
            1f
        } finally {
            src32.release()
            warp.release()
        }
    }
}
