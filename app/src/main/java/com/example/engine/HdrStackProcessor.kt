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
        val refIndex = config.anchorFrameIndex?.takeIf { it in frames.indices }
            ?: selectMiddleExposureRefIndex(frames)
        val totalSteps = 4

        val budget = bitmapLoader.evaluateMemoryBudget(
            frameCount = frameCount,
            requestedPreset = config.outputSettings.maxResolution
        )

        val (targetWidth, targetHeight) = bitmapLoader.readOrientedDimensions(
            uri = frames[refIndex].uri,
            maxEdgePx = budget.effectiveMaxEdgePx
        )

        val refRoleLabel = if (config.deghostingEnabled) "deghost anchor" else "reference"
        onProgress(
            StackProgress(
                currentStep = 1,
                totalSteps = totalSteps,
                fraction = 0.04f,
                message = "Analyzing $refRoleLabel frame #${refIndex + 1}..."
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
        val previewCap = OpenCvAlignmentPipeline.previewMaxEdgeForFrameCount(frameCount)
        val weightFiles = arrayOfNulls<File>(frameCount)

        val weightSum = Mat.zeros(outHeight, outWidth, CvType.CV_32FC1)
        var refGrayU8: Mat? = null
        var refGrayBlur: Mat? = null
        var refCdf: FloatArray? = null
        var completed = false

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

                var frameBitmap: Bitmap? = null
                var rawBgr: Mat? = null
                var alignedBgr: Mat? = null
                var croppedBgr: Mat? = null
                var unsmoothedWeight: Mat? = null
                var rawWeight: Mat? = null
                var deghostMask: Mat? = null

                try {
                    frameBitmap = bitmapLoader.loadNormalizedBitmap(
                        uri = frames[frameIdx].uri,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight
                    )
                    rawBgr = OpenCvAlignmentPipeline.bitmapToBgrMat(frameBitmap)
                    frameBitmap.recycle()
                    frameBitmap = null

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
                        try {
                            sub.clone()
                        } finally {
                            sub.release()
                            alignedBgr.release()
                            alignedBgr = null
                        }
                    } else {
                        val ref = alignedBgr
                        alignedBgr = null
                        ref
                    }

                    // Persist aligned+cropped frame for Pass 3 pyramid fusion & manual retouching
                    val cacheFile = File(runDir, "aligned_$frameIdx.jpg")
                    OpenCvAlignmentPipeline.saveMatAsJpeg(croppedBgr, cacheFile, quality = 97)
                    preallocatedCachePaths[frameIdx] = cacheFile.absolutePath
                    preallocatedPreviews[frameIdx] = OpenCvAlignmentPipeline.createPreviewFromBgrMat(
                        croppedBgr,
                        maxEdgePx = previewCap
                    )

                    coroutineContext.ensureActive()
                    unsmoothedWeight = computeMertensWeightMap(
                        bgrMat = croppedBgr,
                        contrastWeight = config.contrastWeight,
                        saturationWeight = config.saturationWeight,
                        exposureWeight = config.exposureWeight
                    )
                    rawWeight = smoothWeightMapMultiScale(unsmoothedWeight)
                    unsmoothedWeight.release()
                    unsmoothedWeight = null

                    if (config.deghostingEnabled) {
                        if (stepIdx == 0) {
                            val refG = Mat()
                            val refB = Mat()
                            try {
                                Imgproc.cvtColor(croppedBgr, refG, Imgproc.COLOR_BGR2GRAY)
                                Imgproc.GaussianBlur(refG, refB, Size(7.0, 7.0), 0.0)
                                refCdf = computeGrayscaleCdf(refG)
                                refGrayU8 = refG
                                refGrayBlur = refB
                            } catch (t: Throwable) {
                                refG.release()
                                refB.release()
                                throw t
                            }
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
                    if (frameBitmap != null && !frameBitmap.isRecycled) frameBitmap.recycle()
                    rawBgr?.release()
                    alignedBgr?.release()
                    croppedBgr?.release()
                    unsmoothedWeight?.release()
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
                        alignedBgr = null
                        var bmp: Bitmap? = null
                        try {
                            bmp = bitmapLoader.loadCachedBitmap(cachePath)
                                ?: throw IllegalStateException("Unable to reload cached frame $frameIdx")
                            alignedBgr = OpenCvAlignmentPipeline.bitmapToBgrMat(bmp)
                        } finally {
                            if (bmp != null && !bmp.isRecycled) bmp.recycle()
                        }
                    }

                    coroutineContext.ensureActive()
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
                        val weighted = Mat()
                        try {
                            Imgproc.cvtColor(weightGauss[level], w3ch, Imgproc.COLOR_GRAY2BGR)
                            Core.multiply(imgLap[level], w3ch, weighted)

                            if (init) {
                                fusedPyramid.add(weighted)
                            } else {
                                Core.add(fusedPyramid[level], weighted, fusedPyramid[level])
                                weighted.release()
                            }
                        } catch (t: Throwable) {
                            weighted.release()
                            throw t
                        } finally {
                            w3ch.release()
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

            val outputBitmap = try {
                OpenCvAlignmentPipeline.floatBgrMatToBitmap(reconstructedFloat)
            } finally {
                reconstructedFloat.release()
            }

            val elapsedMs = System.currentTimeMillis() - startTimeMs
            val deghostSuffix = if (config.deghostingEnabled) " • Anchor #${refIndex + 1} Deghost" else ""

            onProgress(
                StackProgress(
                    currentStep = 4,
                    totalSteps = totalSteps,
                    fraction = 1f,
                    message = "HDR blend complete"
                )
            )

            completed = true
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
            if (!completed) {
                preallocatedPreviews.forEach { bmp ->
                    if (bmp != null && !bmp.isRecycled) bmp.recycle()
                }
                runDir.deleteRecursively()
            }
            System.gc()
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
            return weight
        } catch (t: Throwable) {
            weight.release()
            throw t
        } finally {
            floatBgr.release()
        }
    }

    private fun applyWeightedFactor(weightAccum: Mat, factor: Mat, exponent: Float) {
        Core.max(factor, Scalar(1e-5), factor)
        if (abs(exponent - 1f) > 0.01f) {
            Core.pow(factor, exponent.toDouble(), factor)
        }
        Core.multiply(weightAccum, factor, weightAccum)
    }

    /**
     * Multi-scale Gaussian smoothing of Mertens exposure weight maps before pyramid blending.
     * Blends fine, medium, and downsampled broad Gaussian scales so abrupt weight transitions
     * along high-contrast sky-to-mountain boundaries feather smoothly without dark halos.
     */
    private fun smoothWeightMapMultiScale(rawWeight: Mat): Mat {
        val rows = rawWeight.rows()
        val cols = rawWeight.cols()
        val minDim = min(rows, cols).toDouble()
        if (minDim < 32.0) return rawWeight.clone()

        val scaleFine = Mat()
        val scaleMed = Mat()
        val down4 = Mat()
        val down4Blur = Mat()
        val scaleBroad = Mat()
        val blended = Mat()

        return try {
            val sigmaFine = (minDim * 0.003).coerceIn(1.5, 8.0)
            val sigmaMed = (minDim * 0.010).coerceIn(4.0, 22.0)
            val sigmaBroadQuarter = (minDim * 0.008).coerceIn(3.5, 18.0) // ~0.032 * minDim at full res

            Imgproc.GaussianBlur(rawWeight, scaleFine, Size(0.0, 0.0), sigmaFine, sigmaFine, Core.BORDER_REFLECT_101)
            Imgproc.GaussianBlur(rawWeight, scaleMed, Size(0.0, 0.0), sigmaMed, sigmaMed, Core.BORDER_REFLECT_101)

            val qW = (cols / 4).coerceAtLeast(16)
            val qH = (rows / 4).coerceAtLeast(16)
            Imgproc.resize(rawWeight, down4, Size(qW.toDouble(), qH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
            Imgproc.GaussianBlur(down4, down4Blur, Size(0.0, 0.0), sigmaBroadQuarter, sigmaBroadQuarter, Core.BORDER_REFLECT_101)
            Imgproc.resize(down4Blur, scaleBroad, Size(cols.toDouble(), rows.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)

            Core.addWeighted(scaleFine, 0.25, scaleMed, 0.40, 0.0, blended)
            Core.addWeighted(blended, 1.0, scaleBroad, 0.35, 0.0, blended)
            Core.max(blended, Scalar(1e-6), blended)
            blended
        } catch (t: Throwable) {
            blended.release()
            throw t
        } finally {
            scaleFine.release()
            scaleMed.release()
            down4.release()
            down4Blur.release()
            scaleBroad.release()
        }
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
        val dilatedDiff = Mat()
        val dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0))
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

            // Expand motion perimeter slightly so moving subject edges don't leave ghost halos
            Imgproc.dilate(diffU8, dilatedDiff, dilateKernel)

            dilatedDiff.convertTo(diffF, CvType.CV_32FC1, 1.0 / 255.0)
            Core.subtract(diffF, Scalar(0.05), diffF)
            Core.multiply(diffF, Scalar(-1.0 / 0.10), diffF)
            Core.add(diffF, Scalar(1.0), diffF)
            Core.min(diffF, Scalar(1.0), diffF)
            Core.max(diffF, Scalar(1e-4), diffF)

            Imgproc.GaussianBlur(diffF, mask, Size(15.0, 15.0), 0.0)
            Core.max(mask, Scalar(1e-4), mask)
            mask
        } catch (t: Throwable) {
            mask.release()
            throw t
        } finally {
            srcGray.release()
            lutMat.release()
            mappedGray.release()
            mappedBlur.release()
            diffU8.release()
            validSrc.release()
            validRef.release()
            validBoth.release()
            dilatedDiff.release()
            dilateKernel.release()
            diffF.release()
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
            try {
                val needed = countRows * cols
                sub.get(0, 0, buf)
                for (i in 0 until needed) {
                    hist[buf[i].toInt() and 0xFF]++
                }
            } finally {
                sub.release()
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
                try {
                    sub.get(0, 0, floatBuf)
                } finally {
                    sub.release()
                }

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

        try {
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
                    try {
                        sub.put(0, 0, floatBuf)
                    } finally {
                        sub.release()
                    }
                    r += count
                }
            }
            return mat
        } catch (t: Throwable) {
            mat.release()
            throw t
        }
    }
}
