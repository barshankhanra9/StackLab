package com.example.engine

import android.graphics.Bitmap
import com.example.domain.model.AlignmentMode
import com.example.domain.model.BlendAlgorithm
import com.example.domain.model.FocusStackConfig
import com.example.domain.model.StackFrame
import com.example.domain.model.StackMode
import com.example.domain.model.StackProgress
import com.example.domain.model.StackResult
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.ensureActive
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.DMatch
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video

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

        onProgress(
            StackProgress(
                currentStep = 1,
                totalSteps = totalSteps,
                fraction = 0.04f,
                message = "Loading reference frame #${refIndex + 1}..."
            )
        )

        val alignedColorMats = ArrayList<Mat>(frameCount)
        val previewBitmaps = ArrayList<Bitmap>(frameCount)
        val sharpnessMaps = ArrayList<Mat>(frameCount)

        var refGray: Mat? = null
        var refKeypoints: MatOfKeyPoint? = null
        var refDescriptors: Mat? = null
        var alignedCount = 1
        var eccFallbackCount = 0

        try {
            // 1. Load reference (middle) frame first to lock canonical target dimensions
            val refBitmap = bitmapLoader.loadNormalizedBitmap(
                uri = frames[refIndex].uri,
                maxEdgePx = budget.effectiveMaxEdgePx
            )
            val targetWidth = refBitmap.width
            val targetHeight = refBitmap.height

            val refBgr = bitmapToBgrMat(refBitmap)
            val refPreview = bitmapLoader.createPreviewCopy(refBitmap)
            refBitmap.recycle()

            val preallocatedMats = Array<Mat?>(frameCount) { null }
            val preallocatedPreviews = Array<Bitmap?>(frameCount) { null }
            preallocatedMats[refIndex] = refBgr
            preallocatedPreviews[refIndex] = refPreview

            if (config.alignmentMode == AlignmentMode.AUTO_HOMOGRAPHY) {
                refGray = Mat()
                Imgproc.cvtColor(refBgr, refGray, Imgproc.COLOR_BGR2GRAY)
                refKeypoints = MatOfKeyPoint()
                refDescriptors = Mat()
                val orb = ORB.create(2000)
                orb.detectAndCompute(refGray, Mat(), refKeypoints, refDescriptors)
            }

            // 2. Load & align each remaining frame sequentially
            var processedAlignment = 0
            for (i in 0 until frameCount) {
                coroutineContext.ensureActive()
                if (i == refIndex) continue

                processedAlignment++
                val stepFraction = 0.06f + 0.38f * (processedAlignment.toFloat() / (frameCount - 1).coerceAtLeast(1))
                val stepLabel = if (config.alignmentMode == AlignmentMode.AUTO_HOMOGRAPHY) {
                    "Aligning frame ${i + 1} of $frameCount..."
                } else {
                    "Decoding frame ${i + 1} of $frameCount..."
                }
                onProgress(
                    StackProgress(
                        currentStep = 1,
                        totalSteps = totalSteps,
                        fraction = stepFraction,
                        message = stepLabel
                    )
                )

                val frameBitmap = bitmapLoader.loadNormalizedBitmap(
                    uri = frames[i].uri,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight
                )
                val frameBgr = bitmapToBgrMat(frameBitmap)
                val framePreview = bitmapLoader.createPreviewCopy(frameBitmap)
                frameBitmap.recycle()

                if (config.alignmentMode == AlignmentMode.AUTO_HOMOGRAPHY && refGray != null) {
                    val aligned = alignFrameToReference(
                        srcBgr = frameBgr,
                        refGray = refGray,
                        refKeypoints = refKeypoints,
                        refDescriptors = refDescriptors
                    )
                    frameBgr.release()
                    preallocatedMats[i] = aligned.mat
                    if (aligned.usedAlignment) alignedCount++
                    if (aligned.usedEccFallback) eccFallbackCount++
                } else {
                    preallocatedMats[i] = frameBgr
                    alignedCount++
                }
                preallocatedPreviews[i] = framePreview
            }

            for (i in 0 until frameCount) {
                alignedColorMats.add(preallocatedMats[i]!!)
                previewBitmaps.add(preallocatedPreviews[i]!!)
            }
            refGray?.release()
            refGray = null
            refKeypoints?.release()
            refKeypoints = null
            refDescriptors?.release()
            refDescriptors = null

            // 3. Compute focus / sharpness weights via Gaussian + Laplacian + local energy windowing
            val lapBlurKernel = (config.edgeSmoothingRadius.coerceIn(1, 15) * 2 + 1).toDouble()
            val energyWindowKernel = ((config.edgeSmoothingRadius * 2 + 3).coerceAtLeast(5) or 1).toDouble()

            for (i in 0 until frameCount) {
                coroutineContext.ensureActive()
                val frac = 0.45f + 0.15f * ((i + 1).toFloat() / frameCount)
                onProgress(
                    StackProgress(
                        currentStep = 2,
                        totalSteps = totalSteps,
                        fraction = frac,
                        message = "Measuring focus sharpness (${i + 1}/$frameCount)..."
                    )
                )
                sharpnessMaps.add(
                    computeSharpnessEnergyMap(
                        bgrMat = alignedColorMats[i],
                        preBlurKernel = lapBlurKernel,
                        energyWindowKernel = energyWindowKernel
                    )
                )
            }

            // 4. Build multi-scale Laplacian pyramids & fuse sharpest regions
            onProgress(
                StackProgress(
                    currentStep = 3,
                    totalSteps = totalSteps,
                    fraction = 0.63f,
                    message = "Building Laplacian pyramids..."
                )
            )

            val pyramidLevels = computePyramidLevels(targetWidth, targetHeight)
            val fusedLaplacianPyramid = ArrayList<Mat>(pyramidLevels)

            when (config.blendAlgorithm) {
                BlendAlgorithm.PYRAMID_PMAX -> {
                    blendPyramidPMax(
                        colorMats = alignedColorMats,
                        sharpnessMaps = sharpnessMaps,
                        pyramidLevels = pyramidLevels,
                        smoothingRadius = config.edgeSmoothingRadius,
                        fusedPyramidOut = fusedLaplacianPyramid,
                        onFrameBlended = { idx ->
                            val frac = 0.65f + 0.25f * ((idx + 1).toFloat() / frameCount)
                            onProgress(
                                StackProgress(
                                    currentStep = 3,
                                    totalSteps = totalSteps,
                                    fraction = frac,
                                    message = "Fusing sharpest regions (${idx + 1}/$frameCount)..."
                                )
                            )
                        }
                    )
                }

                BlendAlgorithm.SMOOTH_DMAP -> {
                    blendSmoothDepthMap(
                        colorMats = alignedColorMats,
                        sharpnessMaps = sharpnessMaps,
                        pyramidLevels = pyramidLevels,
                        smoothingRadius = config.edgeSmoothingRadius,
                        fusedPyramidOut = fusedLaplacianPyramid,
                        onFrameBlended = { idx ->
                            val frac = 0.65f + 0.25f * ((idx + 1).toFloat() / frameCount)
                            onProgress(
                                StackProgress(
                                    currentStep = 3,
                                    totalSteps = totalSteps,
                                    fraction = frac,
                                    message = "Blending smooth depth map (${idx + 1}/$frameCount)..."
                                )
                            )
                        }
                    )
                }
            }

            // Release sharpness maps and aligned source Mats before reconstruction
            sharpnessMaps.forEach { it.release() }
            sharpnessMaps.clear()
            alignedColorMats.forEach { it.release() }
            alignedColorMats.clear()

            coroutineContext.ensureActive()
            onProgress(
                StackProgress(
                    currentStep = 4,
                    totalSteps = totalSteps,
                    fraction = 0.94f,
                    message = "Reconstructing full-focus composite..."
                )
            )

            val reconstructedFloat = reconstructLaplacianPyramid(fusedLaplacianPyramid)
            fusedLaplacianPyramid.forEach { it.release() }
            fusedLaplacianPyramid.clear()

            val outputBitmap = floatBgrMatToBitmap(reconstructedFloat)
            reconstructedFloat.release()

            val elapsedMs = System.currentTimeMillis() - startTimeMs
            val alignmentNote = when {
                config.alignmentMode == AlignmentMode.NONE -> "Tripod mode (no warping)"
                eccFallbackCount > 0 -> "ORB + RANSAC ($eccFallbackCount ECC affine)"
                else -> "ORB + RANSAC homography"
            }

            onProgress(
                StackProgress(
                    currentStep = 4,
                    totalSteps = totalSteps,
                    fraction = 1f,
                    message = "Stack complete"
                )
            )

            return StackResult(
                outputBitmap = outputBitmap,
                sourcePreviewBitmaps = previewBitmaps,
                frameNames = frames.map { it.displayName },
                defaultComparisonIndex = 0,
                alignedFramesCount = alignedCount,
                processingTimeMs = elapsedMs,
                usedProxyMode = budget.usedProxyMode,
                proxyReason = budget.reason,
                outputWidth = targetWidth,
                outputHeight = targetHeight,
                alignmentNote = alignmentNote
            )
        } finally {
            refGray?.release()
            refKeypoints?.release()
            refDescriptors?.release()
            sharpnessMaps.forEach { it.release() }
            alignedColorMats.forEach { it.release() }
        }
    }

    private data class AlignmentOutcome(
        val mat: Mat,
        val usedAlignment: Boolean,
        val usedEccFallback: Boolean
    )

    private fun alignFrameToReference(
        srcBgr: Mat,
        refGray: Mat,
        refKeypoints: MatOfKeyPoint?,
        refDescriptors: Mat?
    ): AlignmentOutcome {
        val srcGray = Mat()
        val srcKeypoints = MatOfKeyPoint()
        val srcDescriptors = Mat()
        try {
            Imgproc.cvtColor(srcBgr, srcGray, Imgproc.COLOR_BGR2GRAY)

            // Primary: ORB + BFMatcher (Lowe ratio test) + findHomography(RANSAC)
            if (refKeypoints != null && refDescriptors != null && !refDescriptors.empty()) {
                val orb = ORB.create(2000)
                orb.detectAndCompute(srcGray, Mat(), srcKeypoints, srcDescriptors)

                if (!srcDescriptors.empty() && srcDescriptors.rows() >= 12) {
                    val matcher = BFMatcher.create(Core.NORM_HAMMING, false)
                    val knnMatches = ArrayList<MatOfDMatch>()
                    matcher.knnMatch(srcDescriptors, refDescriptors, knnMatches, 2)

                    val goodMatches = ArrayList<DMatch>()
                    for (matOfDMatch in knnMatches) {
                        val arr = matOfDMatch.toArray()
                        if (arr.size >= 2 && arr[0].distance < 0.75f * arr[1].distance) {
                            goodMatches.add(arr[0])
                        }
                        matOfDMatch.release()
                    }

                    if (goodMatches.size >= 12) {
                        val srcPtsList = ArrayList<Point>(goodMatches.size)
                        val dstPtsList = ArrayList<Point>(goodMatches.size)
                        val srcKpArray = srcKeypoints.toArray()
                        val refKpArray = refKeypoints.toArray()
                        for (m in goodMatches) {
                            srcPtsList.add(srcKpArray[m.queryIdx].pt)
                            dstPtsList.add(refKpArray[m.trainIdx].pt)
                        }

                        val srcPts = MatOfPoint2f(*srcPtsList.toTypedArray())
                        val dstPts = MatOfPoint2f(*dstPtsList.toTypedArray())
                        val homography = Calib3d.findHomography(srcPts, dstPts, Calib3d.RANSAC, 4.0)
                        srcPts.release()
                        dstPts.release()

                        if (!homography.empty() && isValidHomography(homography, srcBgr.cols(), srcBgr.rows())) {
                            val warped = Mat()
                            Imgproc.warpPerspective(
                                srcBgr,
                                warped,
                                homography,
                                srcBgr.size(),
                                Imgproc.INTER_LINEAR,
                                Core.BORDER_REPLICATE
                            )
                            homography.release()
                            return AlignmentOutcome(warped, usedAlignment = true, usedEccFallback = false)
                        }
                        homography.release()
                    }
                }
            }

            // Fallback: ECC Affine transform on downscaled proxy for speed & robustness
            val eccWarped = tryEccAffineAlignment(srcBgr, srcGray, refGray)
            if (eccWarped != null) {
                return AlignmentOutcome(eccWarped, usedAlignment = true, usedEccFallback = true)
            }

            // Last resort: keep original unwarped frame rather than failing the stack
            return AlignmentOutcome(srcBgr.clone(), usedAlignment = false, usedEccFallback = false)
        } finally {
            srcGray.release()
            srcKeypoints.release()
            srcDescriptors.release()
        }
    }

    private fun tryEccAffineAlignment(srcBgr: Mat, srcGray: Mat, refGray: Mat): Mat? {
        val maxEccDim = 512.0
        val scale = min(1.0, maxEccDim / maxOf(refGray.cols(), refGray.rows()).toDouble())
        val smallRef = Mat()
        val smallSrc = Mat()
        val smallRef32 = Mat()
        val smallSrc32 = Mat()
        val warpMatrix = Mat.eye(2, 3, CvType.CV_32F)

        return try {
            val smallSize = Size(
                (refGray.cols() * scale).coerceAtLeast(32.0),
                (refGray.rows() * scale).coerceAtLeast(32.0)
            )
            Imgproc.resize(refGray, smallRef, smallSize, 0.0, 0.0, Imgproc.INTER_AREA)
            Imgproc.resize(srcGray, smallSrc, smallSize, 0.0, 0.0, Imgproc.INTER_AREA)
            smallRef.convertTo(smallRef32, CvType.CV_32F, 1.0 / 255.0)
            smallSrc.convertTo(smallSrc32, CvType.CV_32F, 1.0 / 255.0)

            val criteria = TermCriteria(TermCriteria.COUNT or TermCriteria.EPS, 50, 1e-4)
            Video.findTransformECC(smallRef32, smallSrc32, warpMatrix, Video.MOTION_AFFINE, criteria)

            // Scale translation components back to full resolution
            val tx = warpMatrix.get(0, 2)[0] / scale
            val ty = warpMatrix.get(1, 2)[0] / scale
            warpMatrix.put(0, 2, tx)
            warpMatrix.put(1, 2, ty)

            val warped = Mat()
            Imgproc.warpAffine(
                srcBgr,
                warped,
                warpMatrix,
                srcBgr.size(),
                Imgproc.INTER_LINEAR,
                Core.BORDER_REPLICATE
            )
            warped
        } catch (_: Exception) {
            null
        } finally {
            smallRef.release()
            smallSrc.release()
            smallRef32.release()
            smallSrc32.release()
            warpMatrix.release()
        }
    }

    private fun isValidHomography(h: Mat, width: Int, height: Int): Boolean {
        if (h.rows() != 3 || h.cols() != 3) return false
        val a = h.get(0, 0)[0]
        val b = h.get(0, 1)[0]
        val tx = h.get(0, 2)[0]
        val c = h.get(1, 0)[0]
        val d = h.get(1, 1)[0]
        val ty = h.get(1, 2)[0]
        val det = a * d - b * c
        if (det < 0.35 || det > 2.8) return false
        if (abs(tx) > width * 0.35 || abs(ty) > height * 0.35) return false
        return true
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
        try {
            Imgproc.cvtColor(bgrMat, gray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(preBlurKernel, preBlurKernel), 0.0)
            Imgproc.Laplacian(blurred, lap, CvType.CV_32F, 3)
            Core.multiply(lap, lap, energy)
            Imgproc.GaussianBlur(energy, windowedEnergy, Size(energyWindowKernel, energyWindowKernel), 0.0)
            return windowedEnergy.clone()
        } finally {
            gray.release()
            blurred.release()
            lap.release()
            energy.release()
            windowedEnergy.release()
        }
    }

    private suspend fun blendPyramidPMax(
        colorMats: List<Mat>,
        sharpnessMaps: List<Mat>,
        pyramidLevels: Int,
        smoothingRadius: Int,
        fusedPyramidOut: MutableList<Mat>,
        onFrameBlended: (Int) -> Unit
    ) {
        val rows = sharpnessMaps[0].rows()
        val cols = sharpnessMaps[0].cols()
        val frameCount = colorMats.size

        // Compute per-pixel maximum sharpness across all frames
        val maxSharpness = sharpnessMaps[0].clone()
        for (i in 1 until frameCount) {
            Core.max(maxSharpness, sharpnessMaps[i], maxSharpness)
        }

        // Count how many frames tie at each pixel's max so weights sum to 1.0 everywhere
        val tieCount = Mat.zeros(rows, cols, CvType.CV_32F)
        val cmpMask = Mat()
        val cmpFloat = Mat()
        val eps = Mat(rows, cols, CvType.CV_32F, Scalar(1e-6))
        val maxMinusEps = Mat()
        Core.subtract(maxSharpness, eps, maxMinusEps)
        eps.release()

        for (i in 0 until frameCount) {
            Core.compare(sharpnessMaps[i], maxMinusEps, cmpMask, Core.CMP_GE)
            cmpMask.convertTo(cmpFloat, CvType.CV_32F, 1.0 / 255.0)
            Core.add(tieCount, cmpFloat, tieCount)
        }
        Core.max(tieCount, Scalar(1.0), tieCount)

        val maskBlurSize = (smoothingRadius.coerceAtLeast(1) * 2 + 1).toDouble()

        try {
            for (i in 0 until frameCount) {
                coroutineContext.ensureActive()
                Core.compare(sharpnessMaps[i], maxMinusEps, cmpMask, Core.CMP_GE)
                cmpMask.convertTo(cmpFloat, CvType.CV_32F, 1.0 / 255.0)
                val normWeight = Mat()
                Core.divide(cmpFloat, tieCount, normWeight)
                if (smoothingRadius > 0) {
                    Imgproc.GaussianBlur(normWeight, normWeight, Size(maskBlurSize, maskBlurSize), 0.0)
                }

                accumulateFrameIntoPyramid(
                    bgrMat = colorMats[i],
                    weightMap = normWeight,
                    pyramidLevels = pyramidLevels,
                    fusedPyramid = fusedPyramidOut
                )
                normWeight.release()
                onFrameBlended(i)
            }
        } finally {
            maxSharpness.release()
            maxMinusEps.release()
            tieCount.release()
            cmpMask.release()
            cmpFloat.release()
        }
    }

    private suspend fun blendSmoothDepthMap(
        colorMats: List<Mat>,
        sharpnessMaps: List<Mat>,
        pyramidLevels: Int,
        smoothingRadius: Int,
        fusedPyramidOut: MutableList<Mat>,
        onFrameBlended: (Int) -> Unit
    ) {
        val rows = sharpnessMaps[0].rows()
        val cols = sharpnessMaps[0].cols()
        val frameCount = colorMats.size
        val smoothKernel = ((smoothingRadius * 4 + 3).coerceAtLeast(5) or 1).toDouble()

        val poweredWeights = ArrayList<Mat>(frameCount)
        val weightSum = Mat(rows, cols, CvType.CV_32F, Scalar(1e-6))

        try {
            for (i in 0 until frameCount) {
                coroutineContext.ensureActive()
                val smoothed = Mat()
                Imgproc.GaussianBlur(sharpnessMaps[i], smoothed, Size(smoothKernel, smoothKernel), 0.0)
                val squared = Mat()
                Core.multiply(smoothed, smoothed, squared)
                smoothed.release()
                Core.add(weightSum, squared, weightSum)
                poweredWeights.add(squared)
            }

            for (i in 0 until frameCount) {
                coroutineContext.ensureActive()
                val normWeight = Mat()
                Core.divide(poweredWeights[i], weightSum, normWeight)
                accumulateFrameIntoPyramid(
                    bgrMat = colorMats[i],
                    weightMap = normWeight,
                    pyramidLevels = pyramidLevels,
                    fusedPyramid = fusedPyramidOut
                )
                normWeight.release()
                onFrameBlended(i)
            }
        } finally {
            weightSum.release()
            poweredWeights.forEach { it.release() }
        }
    }

    private fun accumulateFrameIntoPyramid(
        bgrMat: Mat,
        weightMap: Mat,
        pyramidLevels: Int,
        fusedPyramid: MutableList<Mat>
    ) {
        val floatBgr = Mat()
        bgrMat.convertTo(floatBgr, CvType.CV_32FC3, 1.0 / 255.0)

        val imgGauss = buildGaussianPyramid(floatBgr, pyramidLevels)
        floatBgr.release()
        val imgLap = buildLaplacianPyramid(imgGauss)
        imgGauss.forEach { it.release() }

        val weightGauss = buildGaussianPyramid(weightMap, pyramidLevels)

        val initFused = fusedPyramid.isEmpty()
        for (level in 0 until pyramidLevels) {
            val weight3ch = Mat()
            Imgproc.cvtColor(weightGauss[level], weight3ch, Imgproc.COLOR_GRAY2BGR)
            val weighted = Mat()
            Core.multiply(imgLap[level], weight3ch, weighted)
            weight3ch.release()

            if (initFused) {
                fusedPyramid.add(weighted)
            } else {
                Core.add(fusedPyramid[level], weighted, fusedPyramid[level])
                weighted.release()
            }
        }

        imgLap.forEach { it.release() }
        weightGauss.forEach { it.release() }
    }

    private fun buildGaussianPyramid(base: Mat, levels: Int): List<Mat> {
        val pyramid = ArrayList<Mat>(levels)
        var current = base.clone()
        pyramid.add(current)
        for (i in 1 until levels) {
            val next = Mat()
            Imgproc.pyrDown(current, next)
            pyramid.add(next)
            current = next
        }
        return pyramid
    }

    private fun buildLaplacianPyramid(gaussianPyramid: List<Mat>): List<Mat> {
        val levels = gaussianPyramid.size
        val laplacian = ArrayList<Mat>(levels)
        for (i in 0 until levels - 1) {
            val current = gaussianPyramid[i]
            val next = gaussianPyramid[i + 1]
            val upsampled = Mat()
            Imgproc.pyrUp(next, upsampled, current.size())
            val diff = Mat()
            Core.subtract(current, upsampled, diff)
            upsampled.release()
            laplacian.add(diff)
        }
        laplacian.add(gaussianPyramid[levels - 1].clone())
        return laplacian
    }

    private fun reconstructLaplacianPyramid(laplacianPyramid: List<Mat>): Mat {
        var current = laplacianPyramid.last().clone()
        for (i in laplacianPyramid.size - 2 downTo 0) {
            val target = laplacianPyramid[i]
            val upsampled = Mat()
            Imgproc.pyrUp(current, upsampled, target.size())
            current.release()
            val merged = Mat()
            Core.add(upsampled, target, merged)
            upsampled.release()
            current = merged
        }
        return current
    }

    private fun computePyramidLevels(width: Int, height: Int): Int {
        var minDim = min(width, height)
        var levels = 1
        while (minDim >= 32 && levels < 6) {
            minDim /= 2
            levels++
        }
        return levels.coerceAtLeast(3)
    }

    private fun bitmapToBgrMat(bitmap: Bitmap): Mat {
        val rgba = Mat()
        val bgr = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
        rgba.release()
        return bgr
    }

    private fun floatBgrMatToBitmap(floatBgr: Mat): Bitmap {
        val clamped = Mat()
        val u8Bgr = Mat()
        val u8Rgba = Mat()
        try {
            Core.min(floatBgr, Scalar(1.0, 1.0, 1.0), clamped)
            Core.max(clamped, Scalar(0.0, 0.0, 0.0), clamped)
            clamped.convertTo(u8Bgr, CvType.CV_8UC3, 255.0)
            Imgproc.cvtColor(u8Bgr, u8Rgba, Imgproc.COLOR_BGR2RGBA)
            val bitmap = Bitmap.createBitmap(u8Rgba.cols(), u8Rgba.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(u8Rgba, bitmap)
            return bitmap
        } finally {
            clamped.release()
            u8Bgr.release()
            u8Rgba.release()
        }
    }

    companion object {
        @Volatile
        private var openCvLoaded = false

        fun ensureOpenCvInitialized() {
            if (!openCvLoaded) {
                synchronized(this) {
                    if (!openCvLoaded) {
                        val ok = OpenCVLoader.initLocal()
                        if (!ok) {
                            throw IllegalStateException("Failed to initialize OpenCV native library.")
                        }
                        openCvLoaded = true
                    }
                }
            }
        }
    }
}
