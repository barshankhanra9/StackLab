package com.example.engine

import android.graphics.Bitmap
import com.example.domain.model.ExposureBracket
import com.example.domain.model.StackFrame
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

class EvFrameAnalyzer(
    private val bitmapLoader: FrameBitmapLoader
) {
    suspend fun analyzeFrames(frames: List<StackFrame>): List<StackFrame> {
        if (frames.isEmpty()) return frames

        val luminances = FloatArray(frames.size) { 0.5f }
        for (i in frames.indices) {
            coroutineContext.ensureActive()
            luminances[i] = computeMeanLuminance(frames[i])
        }

        val sortedIndices = frames.indices.sortedBy { luminances[it] }
        val rankOf = IntArray(frames.size)
        sortedIndices.forEachIndexed { rank, originalIdx ->
            rankOf[originalIdx] = rank
        }

        return frames.mapIndexed { i, frame ->
            val bracket = when {
                frames.size == 1 -> ExposureBracket.MID
                frames.size == 2 -> if (rankOf[i] == 0) ExposureBracket.DARK else ExposureBracket.BRIGHT
                else -> {
                    val relRank = rankOf[i].toFloat() / (frames.size - 1).coerceAtLeast(1)
                    when {
                        relRank < 0.34f -> ExposureBracket.DARK
                        relRank <= 0.67f -> ExposureBracket.MID
                        else -> ExposureBracket.BRIGHT
                    }
                }
            }
            frame.copy(
                exposureBracket = bracket,
                meanLuminance = luminances[i],
                isAnalyzed = true
            )
        }
    }

    private fun computeMeanLuminance(frame: StackFrame): Float {
        var bitmap: Bitmap? = null
        return try {
            bitmap = bitmapLoader.loadNormalizedBitmap(frame.uri, maxEdgePx = 256)
            val width = bitmap.width
            val height = bitmap.height
            val total = width * height
            if (total <= 0) return 0.5f

            val pixels = IntArray(total)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            var lumaSum = 0.0
            for (px in pixels) {
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                lumaSum += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            }
            (lumaSum / total).toFloat().coerceIn(0f, 1f)
        } catch (_: Exception) {
            0.5f
        } finally {
            bitmap?.recycle()
        }
    }
}
