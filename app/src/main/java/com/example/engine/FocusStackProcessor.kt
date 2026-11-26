package com.example.engine

import android.graphics.Bitmap
import com.example.domain.model.AlignmentMode
import com.example.domain.model.BlendAlgorithm
import com.example.domain.model.FocusStackConfig
import com.example.domain.model.StackFrame
import com.example.domain.model.StackMode
import com.example.domain.model.StackProgress
import com.example.domain.model.StackResult
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class FocusStackProcessor(
    private val bitmapLoader: FrameBitmapLoader
) : StackProcessor<FocusStackConfig> {

    override val mode: StackMode = StackMode.FocusStack

    override suspend fun process(
        frames: List<StackFrame>,
        config: FocusStackConfig,
        onProgress: (StackProgress) -> Unit
    ): StackResult {
        require(frames.size >= 2) { "Select at least 2 frames to perform focus stacking." }
        ensureOpenCvInitialized()

        val startTimeMs = System.currentTimeMillis()
        val frameCount = frames.size
        val refIndex = frameCount / 2
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
                message = "Analyzing reference frame #${refIndex + 1}..."
            )
        )

        // Pass 1: 1024px Grayscale Proxy Alignment (Zero full-res Mat retention)
        val alignmentResult = OpenCvAlignmentPipeline.alignFramesOnProxy(
            bitmapLoader = bitmapLoader,
            frames = frames,
            refIndex = refIndex,
            alignmentMode = config.alignmentMode,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            totalSteps = totalSteps,
            progressStart = 0.06f,
            progressSpan = 0.30f,
            onProgress = onProgress
        )

        // Compute maximum valid overlapping bounding box across all warped frames
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
                fraction = 0.38f,
                message = if (wasAutoCropped) {
                    "Auto-cropped to ${outWidth}×${outHeight}; streaming frames..."
                } else {
                    "Streaming $frameCount frames through pyramid accumulator..."
                }
            )
        )

        // Pass 2: Full-Res Streaming Alignment + Laplacian Pyramid Accumulation
        val runDir = bitmapLoader.getRetouchRunDir(UUID.randomUUID().toString())
        val preallocatedPreviews = arrayOfNulls<Bitmap>(frameCount)
        val preallocatedCachePaths = arrayOfNulls<String>(frameCount)
        val previewCap = OpenCvAlignmentPipeline.previewMaxEdgeForFrameCount(frameCount)

        val pyramidLevels = OpenCvAlignmentPipeline.computePyramidLevels(outWidth, outHeight)
        val lapBlurKernel = (config.edgeSmoothingRadius.coerceIn(1, 15) * 2 + 1).toDouble()
        val energyWindowKernel = ((config.edgeSmoothingRadius * 2 + 3).coerceAtLeast(5) or 1).toDouble()
        val smoothDMapKernel = ((config.edgeSmoothingRadius * 4 + 3).coerceAtLeast(5) or 1).toDouble()
        val pmaxMaskBlurKernel = (config.edgeSmoothingRadius.coerceAtLeast(1) * 2 + 1).toDouble()

        val numeratorPyramid = ArrayList<Mat>(pyramidLevels)
        val denominatorPyramid = ArrayList<Mat>(pyramidLevels)
        var runningMaxSharpness: Mat? = null
        var completed = false

        val processingOrder = listOf(refIndex) + (0 until frameCount).filter { it != refIndex }

        try {
            for ((stepIdx, frameIdx) in processingOrder.withIndex()) {
                coroutineContext.ensureActive()

                val frac = 0.40f + 0.50f * ((stepIdx + 1).toFloat() / frameCount)
                onProgress(
                    StackProgress(
                        currentStep = if (stepIdx < frameCount / 2) 2 else 3,
                        totalSteps = totalSteps,
                        fraction = frac,
                        message = "Aligning & fusing frame ${stepIdx + 1} of $frameCount..."
                    )
                )

                var frameBitmap: Bitmap? = null
                var rawBgr: Mat? = null
                var alignedBgr: Mat? = null
                var croppedBgr: Mat? = null
                var sharpnessMap: Mat? = null
                var imgLap: List<Mat>? = null

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

                    val cacheFile = File(runDir, "aligned_$frameIdx.jpg")
                    OpenCvAlignmentPipeline.saveMatAsJpeg(croppedBgr, cacheFile, quality = 97)
                    preallocatedCachePaths[frameIdx] = cacheFile.absolutePath
                    preallocatedPreviews[frameIdx] = OpenCvAlignmentPipeline.createPreviewFromBgrMat(
                        croppedBgr,
                        maxEdgePx = previewCap
                    )

                    coroutineContext.ensureActive()
                    sharpnessMap = computeSharpnessEnergyMap(
                        bgrMat = croppedBgr,
                        preBlurKernel = lapBlurKernel,
                        energyWindowKernel = energyWindowKernel
                    )
                    imgLap = OpenCvAlignmentPipeline.buildLaplacianPyramidFromBgr(croppedBgr, pyramidLevels)

                    croppedBgr.release()
                    croppedBgr = null

                    when (config.blendAlgorithm) {
                        BlendAlgorithm.SMOOTH_DMAP -> {
                            accumulateSmoothDMapFrame(
                                imgLap = imgLap,
                                sharpnessMap = sharpnessMap,
                                pyramidLevels = pyramidLevels,
                                smoothKernel = smoothDMapKernel,
                                numeratorPyramid = numeratorPyramid,
                                denominatorPyramid = denominatorPyramid
                            )
                        }

                        BlendAlgorithm.PYRAMID_PMAX -> {
                            val nextMax = accumulatePMaxFrame(
                                isFirstFrame = (stepIdx == 0),
                                imgLap = imgLap,
                                sharpnessMap = sharpnessMap,
                                runningMaxSharpness = runningMaxSharpness,
                                pyramidLevels = pyramidLevels,
                                maskBlurKernel = pmaxMaskBlurKernel,
                                fusedPyramid = numeratorPyramid
                            )
                            if (nextMax === sharpnessMap) {
                                sharpnessMap = null // Ownership transferred to runningMaxSharpness
                            }
                            runningMaxSharpness = nextMax
                        }
                    }
                } finally {
                    if (frameBitmap != null && !frameBitmap.isRecycled) frameBitmap.recycle()
                    rawBgr?.release()
                    alignedBgr?.release()
                    croppedBgr?.release()
                    sharpnessMap?.release()
                    imgLap?.forEach { it.release() }
                }
            }

            coroutineContext.ensureActive()
            onProgress(
                StackProgress(
                    currentStep = 4,
                    totalSteps = totalSteps,
                    fraction = 0.94f,
                    message = "Reconstructing full-focus composite..."
                )
            )

            if (config.blendAlgorithm == BlendAlgorithm.SMOOTH_DMAP) {
                for (level in 0 until pyramidLevels) {
                    val denom3ch = Mat()
                    try {
                        Imgproc.cvtColor(denominatorPyramid[level], denom3ch, Imgproc.COLOR_GRAY2BGR)
                        Core.max(denom3ch, Scalar(1e-6, 1e-6, 1e-6), denom3ch)
                        Core.divide(numeratorPyramid[level], denom3ch, numeratorPyramid[level])
                    } finally {
                        denom3ch.release()
                    }
                }
            }

            denominatorPyramid.forEach { it.release() }
            denominatorPyramid.clear()
            runningMaxSharpness?.release()
            runningMaxSharpness = null

            val reconstructedFloat = OpenCvAlignmentPipeline.reconstructLaplacianPyramid(numeratorPyramid)
            numeratorPyramid.forEach { it.release() }
            numeratorPyramid.clear()

            val outputBitmap = try {
                OpenCvAlignmentPipeline.floatBgrMatToBitmap(reconstructedFloat)
            } finally {
                reconstructedFloat.release()
            }

            val elapsedMs = System.currentTimeMillis() - startTimeMs

            onProgress(
                StackProgress(
                    currentStep = 4,
                    totalSteps = totalSteps,
                    fraction = 1f,
                    message = "Stack complete"
                )
            )

            completed = true
            return StackResult(
                outputBitmap = outputBitmap,
                sourcePreviewBitmaps = preallocatedPreviews.map { it!! },
                alignedSourceCachePaths = preallocatedCachePaths.map { it!! },
                referenceFrameUri = frames[refIndex].uri,
                frameNames = frames.map { it.displayName },
                frameLabels = frames.map { it.focusRegion.label },
                stackMode = StackMode.FocusStack,
                defaultComparisonIndex = 0,
                alignedFramesCount = alignmentResult.alignedCount,
                processingTimeMs = elapsedMs,
                usedProxyMode = budget.usedProxyMode,
                proxyReason = budget.reason,
                outputWidth = outputBitmap.width,
                outputHeight = outputBitmap.height,
                alignmentNote = alignmentResult.alignmentNote,
                wasAutoCropped = wasAutoCropped,
                resolutionPreset = config.outputSettings.maxResolution
            )
        } finally {
            runningMaxSharpness?.release()
            numeratorPyramid.forEach { it.release() }
            denominatorPyramid.forEach { it.release() }
            if (!completed) {
                preallocatedPreviews.forEach { bmp ->
                    if (bmp != null && !bmp.isRecycled) bmp.recycle()
                }
                runDir.deleteRecursively()
            }
            System.gc()
        }
    }

    private fun computeSharpnessEnergyMap(
        bgrMat: Mat,
        preBlurKernel: Double,
        energyWindowKernel: Double
    ): Mat {
        val gray = Mat()
        val blurred = Mat()
        val lap = Mat()
        val energy = Mat()
        val windowedEnergy = Mat()
        return try {
            Imgproc.cvtColor(bgrMat, gray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(preBlurKernel, preBlurKernel), 0.0)
            Imgproc.Laplacian(blurred, lap, CvType.CV_32F, 3)
            Core.multiply(lap, lap, energy)
            Imgproc.GaussianBlur(energy, windowedEnergy, Size(energyWindowKernel, energyWindowKernel), 0.0)
            windowedEnergy
        } catch (t: Throwable) {
            windowedEnergy.release()
            throw t
        } finally {
            gray.release()
            blurred.release()
            lap.release()
            energy.release()
        }
    }

    private fun accumulateSmoothDMapFrame(
        imgLap: List<Mat>,
        sharpnessMap: Mat,
        pyramidLevels: Int,
        smoothKernel: Double,
        numeratorPyramid: MutableList<Mat>,
        denominatorPyramid: MutableList<Mat>
    ) {
        val smoothed = Mat()
        val weight = Mat()
        try {
            Imgproc.GaussianBlur(sharpnessMap, smoothed, Size(smoothKernel, smoothKernel), 0.0)
            Core.multiply(smoothed, smoothed, weight)
            Core.add(weight, Scalar(1e-6), weight)

            val weightGauss = OpenCvAlignmentPipeline.buildGaussianPyramid(weight, pyramidLevels)
            val init = numeratorPyramid.isEmpty()
            try {
                for (level in 0 until pyramidLevels) {
                    val weight3ch = Mat()
                    val weighted = Mat()
                    try {
                        Imgproc.cvtColor(weightGauss[level], weight3ch, Imgproc.COLOR_GRAY2BGR)
                        Core.multiply(imgLap[level], weight3ch, weighted)

                        if (init) {
                            numeratorPyramid.add(weighted)
                            denominatorPyramid.add(weightGauss[level].clone())
                        } else {
                            Core.add(numeratorPyramid[level], weighted, numeratorPyramid[level])
                            weighted.release()
                            Core.add(denominatorPyramid[level], weightGauss[level], denominatorPyramid[level])
                        }
                    } catch (t: Throwable) {
                        weighted.release()
                        throw t
                    } finally {
                        weight3ch.release()
                    }
                }
            } finally {
                weightGauss.forEach { it.release() }
            }
        } finally {
            smoothed.release()
            weight.release()
        }
    }

    private fun accumulatePMaxFrame(
        isFirstFrame: Boolean,
        imgLap: List<Mat>,
        sharpnessMap: Mat,
        runningMaxSharpness: Mat?,
        pyramidLevels: Int,
        maskBlurKernel: Double,
        fusedPyramid: MutableList<Mat>
    ): Mat {
        if (isFirstFrame || runningMaxSharpness == null) {
            for (level in 0 until pyramidLevels) {
                fusedPyramid.add(imgLap[level].clone())
            }
            return sharpnessMap
        }

        val cmpMask = Mat()
        val alphaFloat = Mat()
        try {
            Core.compare(sharpnessMap, runningMaxSharpness, cmpMask, Core.CMP_GT)
            cmpMask.convertTo(alphaFloat, CvType.CV_32F, 1.0 / 255.0)
            Imgproc.GaussianBlur(alphaFloat, alphaFloat, Size(maskBlurKernel, maskBlurKernel), 0.0)
            Core.max(runningMaxSharpness, sharpnessMap, runningMaxSharpness)

            val alphaGauss = OpenCvAlignmentPipeline.buildGaussianPyramid(alphaFloat, pyramidLevels)
            try {
                for (level in 0 until pyramidLevels) {
                    val alpha3ch = Mat()
                    val prevWeighted = Mat()
                    val currWeighted = Mat()
                    val invAlpha1ch = Mat()
                    val inv3ch = Mat()
                    try {
                        Imgproc.cvtColor(alphaGauss[level], alpha3ch, Imgproc.COLOR_GRAY2BGR)
                        Core.multiply(alphaGauss[level], Scalar(-1.0), invAlpha1ch)
                        Core.add(invAlpha1ch, Scalar(1.0), invAlpha1ch)
                        Imgproc.cvtColor(invAlpha1ch, inv3ch, Imgproc.COLOR_GRAY2BGR)

                        Core.multiply(fusedPyramid[level], inv3ch, prevWeighted)
                        Core.multiply(imgLap[level], alpha3ch, currWeighted)
                        Core.add(prevWeighted, currWeighted, fusedPyramid[level])
                    } finally {
                        invAlpha1ch.release()
                        inv3ch.release()
                        alpha3ch.release()
                        prevWeighted.release()
                        currWeighted.release()
                    }
                }
            } finally {
                alphaGauss.forEach { it.release() }
            }
        } finally {
            cmpMask.release()
            alphaFloat.release()
        }
        return runningMaxSharpness
    }

    companion object {
        fun ensureOpenCvInitialized() = OpenCvAlignmentPipeline.ensureOpenCvInitialized()
    }
}
