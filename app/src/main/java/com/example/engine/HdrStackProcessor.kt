package com.example.engine

import android.graphics.Bitmap
import com.example.domain.model.AlignmentMode
import com.example.domain.model.HdrConfig
import com.example.domain.model.StackFrame
import com.example.domain.model.StackMode
import com.example.domain.model.StackProgress
import com.example.domain.model.StackResult
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.ensureActive
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

class HdrStackProcessor(
    private val bitmapLoader: FrameBitmapLoader
) : StackProcessor<HdrConfig> {

    override val mode: StackMode = StackMode.HdrExposureBlend

    override suspend fun process(
        frames: List<StackFrame>,
        config: HdrConfig,
        onProgress: (StackProgress) -> Unit
    ): StackResult {
        require(frames.size >= 2) { "Select at least 2 bracketed frames to perform HDR blending." }
        OpenCvAlignmentPipeline.ensureOpenCvInitialized()

        val startTimeMs = System.currentTimeMillis()
        val frameCount = frames.size
        val refIndex = selectMiddleExposureRefIndex(frames)
        val totalSteps = 4

        val budget = bitmapLoader.evaluateMemoryBudget(
            frameCount = frameCount,
            requestedPreset = config.outputSettings.maxResolution
        )

        val (targetWidth, targetHeight) = bitmapLoader.readOrientedDimensions(
            uri = frames[refIndex].uri,
            maxEdgePx = budget.effectiveMaxEdgePx
        )

        onProgress(
            StackProgress(
                currentStep = 1,
                totalSteps = totalSteps,
                fraction = 0.04f,
                message = "Analyzing middle-exposure reference #${refIndex + 1}..."
            )
        )

        // Pass 1: 1024px ECC / ORB alignment to middle-exposure reference
        val alignmentResult = OpenCvAlignmentPipeline.alignFramesOnProxy(
            bitmapLoader = bitmapLoader,
            frames = frames,
            refIndex = refIndex,
            alignmentMode = config.alignmentMode,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            totalSteps = totalSteps,
            progressStart = 0.06f,
            progressSpan = 0.26f,
            onProgress = onProgress
        )

        val cropRect = if (config.autoCropBorders && config.alignmentMode == AlignmentMode.AUTO_HOMOGRAPHY) {
            OpenCvAlignmentPipeline.computeValidOverlapRect(alignmentResult.transforms, targetWidth, targetHeight)
        } else {
            Rect(0, 0, targetWidth, targetHeight)
        }
        val wasAutoCropped = cropRect.width < targetWidth || cropRect.height < targetHeight
        val outWidth = cropRect.width
        val outHeight = cropRect.height

        onProgress(
            StackProgress(
                currentStep = 2,
                totalSteps = totalSteps,
                fraction = 0.34f,
                message = if (wasAutoCropped) {
                    "Auto-cropped to ${outWidth}×${outHeight}; computing exposure weights..."
                } else {
                    "Computing Mertens weights across $frameCount brackets..."
                }
            )
        )

        val runDir = bitmapLoader.getRetouchRunDir(UUID.randomUUID().toString())
        val preallocatedPreviews = arrayOfNulls<Bitmap>(frameCount)
        val preallocatedCachePaths = arrayOfNulls<String>(frameCount)
        val weightFiles = arrayOfNulls<File>(frameCount)

        val weightSum = Mat.zeros(outHeight, outWidth, CvType.CV_32FC1)
        var refGrayU8: Mat? = null
        var refGrayBlur: Mat? = null
        var refCdf: FloatArray? = null

        val pyramidLevels = OpenCvAlignmentPipeline.computePyramidLevels(outWidth, outHeight)
        val fusedPyramid = ArrayList<Mat>(pyramidLevels)
        val processingOrder = listOf(refIndex) + (0 until frameCount).filter { it != refIndex }

        try {
            // Pass 2: Stream each frame -> warp + crop -> save retouch cache -> Mertens weight + deghosting
            for ((stepIdx, frameIdx) in processingOrder.withIndex()) {
                coroutineContext.ensureActive()

                val frac = 0.35f + 0.30f * ((stepIdx + 1).toFloat() / frameCount)
                onProgress(
                    StackProgress(
                        currentStep = 2,
                        totalSteps = totalSteps,
                        fraction = frac,
                        message = "Aligning & analyzing exposure ${stepIdx + 1} of $frameCount..."
                    )
                )

                var rawBgr: Mat? = null
                var alignedBgr: Mat? = null
                var croppedBgr: Mat? = null
                var rawWeight: Mat? = null
                var deghostMask: Mat? = null

                try {
                    val frameBitmap = bitmapLoader.loadNormalizedBitmap(
                        uri = frames[frameIdx].uri,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight
                    )
                    rawBgr = OpenCvAlignmentPipeline.bitmapToBgrMat(frameBitmap)
                    frameBitmap.recycle()

                    alignedBgr = OpenCvAlignmentPipeline.warpFullFrame(
                        rawBgr,
                        alignmentResult.transforms[frameIdx],
                        targetWidth,
                        targetHeight
                    )
                    if (alignedBgr !== rawBgr) {
                        rawBgr.release()
                        rawBgr = null
                    }

                    croppedBgr = if (wasAutoCropped) {
                        val sub = Mat(alignedBgr, cropRect)
                        val cloned = sub.clone()
                        sub.release()
                        alignedBgr.release()
                        alignedBgr = null
                        cloned
                    } else {
                        val ref = alignedBgr
                        alignedBgr = null
                        ref
                    }

                    // Persist aligned+cropped frame for Pass 3 pyramid fusion & manual retouching
                    val cacheFile = File(runDir, "aligned_$frameIdx.jpg")
                    OpenCvAlignmentPipeline.saveMatAsJpeg(croppedBgr, cacheFile, quality = 97)
                    preallocatedCachePaths[frameIdx] = cacheFile.absolutePath
                    preallocatedPreviews[frameIdx] = OpenCvAlignmentPipeline.createPreviewFromBgrMat(croppedBgr)

                    rawWeight = computeMertensWeightMap(
                        bgrMat = croppedBgr,
                        contrastWeight = config.contrastWeight,
                        saturationWeight = config.saturationWeight,
                        exposureWeight = config.exposureWeight
                    )

                    if (config.deghostingEnabled) {
                        if (stepIdx == 0) {
                            val refG = Mat()
                            Imgproc.cvtColor(croppedBgr, refG, Imgproc.COLOR_BGR2GRAY)
                            val refB = Mat()
                            Imgproc.GaussianBlur(refG, refB, Size(7.0, 7.0), 0.0)
                            refCdf = computeGrayscaleCdf(refG)
                            refGrayU8 = refG
                            refGrayBlur = refB
                        } else {
                            val rGray = refGrayU8
                            val rBlur = refGrayBlur
                            val rCdf = refCdf
                            if (rGray != null && rBlur != null && rCdf != null) {
                                deghostMask = computeDeghostingMask(
                                    srcBgr = croppedBgr,
                                    refGrayU8 = rGray,
                                    refGrayBlur = rBlur,
                                    refCdf = rCdf
                                )
                                Core.multiply(rawWeight, deghostMask, rawWeight)
                            }
                        }
                    }

                    Core.add(weightSum, rawWeight, weightSum)
                    val wFile = File(runDir, "w_$frameIdx.bin")
                    writeFloatMatToFile(rawWeight, wFile)
                    weightFiles[frameIdx] = wFile
                } finally {
                    rawBgr?.release()
                    alignedBgr?.release()
                    croppedBgr?.release()
                    rawWeight?.release()
                    deghostMask?.release()
                }
            }

            refGrayU8?.release()
            refGrayU8 = null
            refGrayBlur?.release()
            refGrayBlur = null
            refCdf = null

            Core.max(weightSum, Scalar(1e-7), weightSum)

            // Pass 3: Stream aligned frames + normalized weights through multi-scale Laplacian pyramid
            for ((stepIdx, frameIdx) in processingOrder.withIndex()) {
                coroutineContext.ensureActive()

                val frac = 0.66f + 0.28f * ((stepIdx + 1).toFloat() / frameCount)
                onProgress(
                    StackProgress(
                        currentStep = 3,
                        totalSteps = totalSteps,
                        fraction = frac,
                        message = "Fusing exposure pyramid ${stepIdx + 1} of $frameCount..."
                    )
                )

                val cachePath = preallocatedCachePaths[frameIdx]
                    ?: throw IllegalStateException("Missing aligned frame cache for frame $frameIdx")
                val wFile = weightFiles[frameIdx]
                    ?: throw IllegalStateException("Missing weight map cache for frame $frameIdx")

                var alignedBgr: Mat? = null
                var rawWeight: Mat? = null
                var normWeight: Mat? = null
                var weightGauss: List<Mat>? = null
                var imgLap: List<Mat>? = null

                try {
                    alignedBgr = Imgcodecs.imread(cachePath)
                    if (alignedBgr.empty()) {
                        alignedBgr.release()
                        val bmp = bitmapLoader.loadCachedBitmap(cachePath)
                            ?: throw IllegalStateException("Unable to reload cached frame $frameIdx")
                        alignedBgr = OpenCvAlignmentPipeline.bitmapToBgrMat(bmp)
                        bmp.recycle()
                    }

                    rawWeight = readFloatMatFromFile(wFile, outWidth, outHeight)
                    wFile.delete()

                    normWeight = Mat()
                    Core.divide(rawWeight, weightSum, normWeight)
                    rawWeight.release()
                    rawWeight = null

                    weightGauss = OpenCvAlignmentPipeline.buildGaussianPyramid(normWeight, pyramidLevels)
                    normWeight.release()
                    normWeight = null

                    imgLap = OpenCvAlignmentPipeline.buildLaplacianPyramidFromBgr(alignedBgr, pyramidLevels)
                    alignedBgr.release()
                    alignedBgr = null

                    val init = fusedPyramid.isEmpty()
                    for (level in 0 until pyramidLevels) {
                        val w3ch = Mat()
                        Imgproc.cvtColor(weightGauss[level], w3ch, Imgproc.COLOR_GRAY2BGR)
                        val weighted = Mat()
                        Core.multiply(imgLap[level], w3ch, weighted)
                        w3ch.release()

                        if (init) {
                            fusedPyramid.add(weighted)
                        } else {
                            Core.add(fusedPyramid[level], weighted, fusedPyramid[level])
                            weighted.release()
                        }
                    }
                } finally {
                    alignedBgr?.release()
                    rawWeight?.release()
                    normWeight?.release()
                    weightGauss?.forEach { it.release() }
                    imgLap?.forEach { it.release() }
                }
            }

            weightSum.release()

            coroutineContext.ensureActive()
            onProgress(
                StackProgress(
                    currentStep = 4,
                    totalSteps = totalSteps,
                    fraction = 0.95f,
                    message = "Reconstructing HDR exposure blend..."
                )
            )

            val reconstructedFloat = OpenCvAlignmentPipeline.reconstructLaplacianPyramid(fusedPyramid)
            fusedPyramid.forEach { it.release() }
            fusedPyramid.clear()

            val outputBitmap = OpenCvAlignmentPipeline.floatBgrMatToBitmap(reconstructedFloat)
            reconstructedFloat.release()

            val elapsedMs = System.currentTimeMillis() - startTimeMs
            val deghostSuffix = if (config.deghostingEnabled) " • Deghost" else ""

            onProgress(
                StackProgress(
                    currentStep = 4,
                    totalSteps = totalSteps,
                    fraction = 1f,
                    message = "HDR blend complete"
                )
            )

            return StackResult(
                outputBitmap = outputBitmap,
                sourcePreviewBitmaps = preallocatedPreviews.map { it!! },
                alignedSourceCachePaths = preallocatedCachePaths.map { it!! },
                referenceFrameUri = frames[refIndex].uri,
                frameNames = frames.map { it.displayName },
                frameLabels = frames.map { it.exposureBracket.label },
                stackMode = StackMode.HdrExposureBlend,
                defaultComparisonIndex = refIndex,
                alignedFramesCount = alignmentResult.alignedCount,
                processingTimeMs = elapsedMs,
                usedProxyMode = budget.usedProxyMode,
                proxyReason = budget.reason,
                outputWidth = outputBitmap.width,
                outputHeight = outputBitmap.height,
                alignmentNote = "${alignmentResult.alignmentNote}$deghostSuffix",
                wasAutoCropped = wasAutoCropped,
                resolutionPreset = config.outputSettings.maxResolution
            )
        } finally {
            weightSum.release()
            refGrayU8?.release()
            refGrayBlur?.release()
            fusedPyramid.forEach { it.release() }
            weightFiles.forEach { it?.delete() }
        }
    }

    private fun selectMiddleExposureRefIndex(frames: List<StackFrame>): Int {
        if (frames.size <= 2) return frames.size / 2
        return if (frames.any { it.isAnalyzed }) {
            val sortedIndices = frames.indices.sortedBy { frames[it].meanLuminance }
            sortedIndices[sortedIndices.size / 2]
        } else {
            frames.size / 2
        }
    }

    private fun computeMertensWeightMap(
        bgrMat: Mat,
        contrastWeight: Float,
        saturationWeight: Float,
        exposureWeight: Float
    ): Mat {
        val rows = bgrMat.rows()
        val cols = bgrMat.cols()
        val floatBgr = Mat()
        val weight = Mat.ones(rows, cols, CvType.CV_32FC1)

        try {
            bgrMat.convertTo(floatBgr, CvType.CV_32FC3, 1.0 / 255.0)

            // 1. Contrast measure: |Laplacian(gray)|
            if (contrastWeight > 0.001f) {
                val gray = Mat()
                val lap = Mat()
                val contrast = Mat()
                try {
                    Imgproc.cvtColor(floatBgr, gray, Imgproc.COLOR_BGR2GRAY)
                    Imgproc.Laplacian(gray, lap, CvType.CV_32F, 3)
                    Core.absdiff(lap, Scalar(0.0), contrast)
                    applyWeightedFactor(weight, contrast, contrastWeight)
                } finally {
                    gray.release()
                    lap.release()
                    contrast.release()
                }
            }

            // Split float BGR channels once for Saturation and Well-Exposedness
            if (saturationWeight > 0.001f || exposureWeight > 0.001f) {
                val channels = ArrayList<Mat>(3)
                Core.split(floatBgr, channels)
                try {
                    // 2. Saturation measure: std-dev across R, G, B channels
                    if (saturationWeight > 0.001f) {
                        val sum = Mat()
                        val mean = Mat()
                        val varSum = Mat.zeros(rows, cols, CvType.CV_32FC1)
                        val diff = Mat()
                        val sq = Mat()
                        val sat = Mat()
                        try {
                            Core.add(channels[0], channels[1], sum)
                            Core.add(sum, channels[2], sum)
                            Core.multiply(sum, Scalar(1.0 / 3.0), mean)

                            for (ch in channels) {
                                Core.subtract(ch, mean, diff)
                                Core.multiply(diff, diff, sq)
                                Core.add(varSum, sq, varSum)
                            }
                            Core.multiply(varSum, Scalar(1.0 / 3.0), varSum)
                            Core.sqrt(varSum, sat)
                            applyWeightedFactor(weight, sat, saturationWeight)
                        } finally {
                            sum.release()
                            mean.release()
                            varSum.release()
                            diff.release()
                            sq.release()
                            sat.release()
                        }
                    }

                    // 3. Well-Exposedness measure: prod_c exp(-((c - 0.5)^2) / (2 * 0.2^2))
                    if (exposureWeight > 0.001f) {
                        val exposed = Mat.ones(rows, cols, CvType.CV_32FC1)
                        val diff = Mat()
                        val sq = Mat()
                        val expCh = Mat()
                        try {
                            for (ch in channels) {
                                Core.subtract(ch, Scalar(0.5), diff)
                                Core.multiply(diff, diff, sq, -12.5)
                                Core.exp(sq, expCh)
                                Core.multiply(exposed, expCh, exposed)
                            }
                            applyWeightedFactor(weight, exposed, exposureWeight)
                        } finally {
                            exposed.release()
                            diff.release()
                            sq.release()
                            expCh.release()
                        }
                    }
                } finally {
                    channels.forEach { it.release() }
                }
            }

            Core.add(weight, Scalar(1e-6), weight)
            return weight.clone()
        } finally {
            floatBgr.release()
            weight.release()
        }
    }

    private fun applyWeightedFactor(weightAccum: Mat, factor: Mat, exponent: Float) {
        Core.max(factor, Scalar(1e-5), factor)
        if (abs(exponent - 1f) > 0.01f) {
            Core.pow(factor, exponent.toDouble(), factor)
        }
        Core.multiply(weightAccum, factor, weightAccum)
    }

    private fun computeDeghostingMask(
        srcBgr: Mat,
        refGrayU8: Mat,
        refGrayBlur: Mat,
        refCdf: FloatArray
    ): Mat {
        val srcGray = Mat()
        val lutMat = Mat(1, 256, CvType.CV_8UC1)
        val mappedGray = Mat()
        val mappedBlur = Mat()
        val diffU8 = Mat()
        val validSrc = Mat()
        val validRef = Mat()
        val validBoth = Mat()
        val diffF = Mat()
        val mask = Mat()

        return try {
            Imgproc.cvtColor(srcBgr, srcGray, Imgproc.COLOR_BGR2GRAY)
            val srcCdf = computeGrayscaleCdf(srcGray)
            val lutBytes = buildImfLut(srcCdf, refCdf)
            lutMat.put(0, 0, lutBytes)

            Core.LUT(srcGray, lutMat, mappedGray)
            Imgproc.GaussianBlur(mappedGray, mappedBlur, Size(7.0, 7.0), 0.0)
            Core.absdiff(mappedBlur, refGrayBlur, diffU8)

            // Only evaluate motion where both frames have unclipped tonal data
            Core.inRange(srcGray, Scalar(8.0), Scalar(246.0), validSrc)
            Core.inRange(refGrayU8, Scalar(8.0), Scalar(246.0), validRef)
            Core.bitwise_and(validSrc, validRef, validBoth)
            Core.bitwise_and(diffU8, validBoth, diffU8)

            diffU8.convertTo(diffF, CvType.CV_32FC1, 1.0 / 255.0)
            Core.subtract(diffF, Scalar(0.07), diffF)
            Core.multiply(diffF, Scalar(-1.0 / 0.13), diffF)
            Core.add(diffF, Scalar(1.0), diffF)
            Core.min(diffF, Scalar(1.0), diffF)
            Core.max(diffF, Scalar(0.01), diffF)

            Imgproc.GaussianBlur(diffF, mask, Size(15.0, 15.0), 0.0)
            mask.clone()
        } finally {
            srcGray.release()
            lutMat.release()
            mappedGray.release()
            mappedBlur.release()
            diffU8.release()
            validSrc.release()
            validRef.release()
            validBoth.release()
            diffF.release()
            mask.release()
        }
    }

    private fun computeGrayscaleCdf(grayU8: Mat): FloatArray {
        val rows = grayU8.rows()
        val cols = grayU8.cols()
        val hist = LongArray(256)
        val rowChunk = 64
        val buf = ByteArray(cols * min(rowChunk, rows))

        var r = 0
        while (r < rows) {
            val countRows = min(rowChunk, rows - r)
            val sub = grayU8.rowRange(r, r + countRows)
            val needed = countRows * cols
            sub.get(0, 0, buf)
            sub.release()
            for (i in 0 until needed) {
                hist[buf[i].toInt() and 0xFF]++
            }
            r += countRows
        }

        val total = (rows.toDouble() * cols.toDouble()).coerceAtLeast(1.0)
        val cdf = FloatArray(256)
        var running = 0L
        for (v in 0 until 256) {
            running += hist[v]
            cdf[v] = (running / total).toFloat()
        }
        return cdf
    }

    private fun buildImfLut(srcCdf: FloatArray, refCdf: FloatArray): ByteArray {
        val lut = ByteArray(256)
        var refIdx = 0
        for (srcVal in 0 until 256) {
            val targetCdf = srcCdf[srcVal]
            while (refIdx < 255 && refCdf[refIdx] < targetCdf) {
                refIdx++
            }
            val best = if (refIdx > 0 && abs(refCdf[refIdx - 1] - targetCdf) < abs(refCdf[refIdx] - targetCdf)) {
                refIdx - 1
            } else {
                refIdx
            }
            lut[srcVal] = best.toByte()
        }
        return lut
    }

    private fun writeFloatMatToFile(mat: Mat, file: File) {
        val rows = mat.rows()
        val cols = mat.cols()
        val chunkRows = min(64, rows)
        val floatBuf = FloatArray(chunkRows * cols)
        val byteBuf = ByteBuffer.allocate(chunkRows * cols * 4).order(ByteOrder.nativeOrder())

        RandomAccessFile(file, "rw").use { raf ->
            val channel = raf.channel
            var r = 0
            while (r < rows) {
                val count = min(chunkRows, rows - r)
                val floatsToWrite = count * cols
                val sub = mat.rowRange(r, r + count)
                sub.get(0, 0, floatBuf)
                sub.release()

                byteBuf.clear()
                byteBuf.asFloatBuffer().put(floatBuf, 0, floatsToWrite)
                byteBuf.position(0)
                byteBuf.limit(floatsToWrite * 4)
                while (byteBuf.hasRemaining()) {
                    channel.write(byteBuf)
                }
                r += count
            }
        }
    }

    private fun readFloatMatFromFile(file: File, cols: Int, rows: Int): Mat {
        val mat = Mat(rows, cols, CvType.CV_32FC1)
        val chunkRows = min(64, rows)
        val floatBuf = FloatArray(chunkRows * cols)
        val byteBuf = ByteBuffer.allocate(chunkRows * cols * 4).order(ByteOrder.nativeOrder())

        RandomAccessFile(file, "r").use { raf ->
            val channel = raf.channel
            var r = 0
            while (r < rows) {
                val count = min(chunkRows, rows - r)
                val floatsToRead = count * cols
                byteBuf.clear()
                byteBuf.limit(floatsToRead * 4)
                while (byteBuf.hasRemaining()) {
                    if (channel.read(byteBuf) < 0) break
                }
                byteBuf.flip()
                byteBuf.asFloatBuffer().get(floatBuf, 0, floatsToRead)

                val sub = mat.rowRange(r, r + count)
                sub.put(0, 0, floatBuf)
                sub.release()
                r += count
            }
        }
        return mat
    }
}
