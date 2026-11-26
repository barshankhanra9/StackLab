package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.example.domain.model.AstroAlignmentMode
import com.example.domain.model.AstroConfig
import com.example.domain.model.AstroFrameType
import com.example.domain.model.AstroStackAlgorithm
import com.example.domain.model.ForegroundMaskStroke
import com.example.domain.model.StackFrame
import com.example.domain.model.StackMode
import com.example.domain.model.StackProgress
import com.example.domain.model.StackResult
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.ensureActive
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.DMatch
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video

class AstroStackProcessor(
    private val bitmapLoader: FrameBitmapLoader
) : StackProcessor<AstroConfig> {

    override val mode: StackMode = StackMode.AstroNoiseStack

    override suspend fun process(
        frames: List<StackFrame>,
        config: AstroConfig,
        onProgress: (StackProgress) -> Unit
    ): StackResult {
        val lightFrames = frames.filter { it.astroFrameType == AstroFrameType.LIGHT }
        val darkFrames = frames.filter { it.astroFrameType == AstroFrameType.DARK }
        require(lightFrames.size >= 2) {
            "Enable at least 2 Light frames to perform Astro stacking."
        }

        OpenCvAlignmentPipeline.ensureOpenCvInitialized()
        val startTimeMs = System.currentTimeMillis()
        val lightCount = lightFrames.size
        val darkCount = darkFrames.size
        val refIndex = resolveReferenceLightIndex(lightFrames)
        val totalSteps = 4

        val budget = bitmapLoader.evaluateMemoryBudget(
            frameCount = lightCount,
            requestedPreset = config.outputSettings.maxResolution
        )
        val (targetWidth, targetHeight) = bitmapLoader.readOrientedDimensions(
            uri = lightFrames[refIndex].uri,
            maxEdgePx = budget.effectiveMaxEdgePx
        )

        val runDir = bitmapLoader.getRetouchRunDir(UUID.randomUUID().toString())
        var masterDarkU8: Mat? = null
        var masterDarkProxyGray: Mat? = null
        var fgMaskFloatFull: Mat? = null
        var fgMaskFloatCropped: Mat? = null
        var fgMaskProxyU8: Mat? = null

        val rawStripFiles = ArrayList<File>(lightCount)
        val preallocatedPreviews = arrayOfNulls<Bitmap>(lightCount)
        val preallocatedCachePaths = arrayOfNulls<String>(lightCount)

        try {
            // Step 1: Master Dark Calibration (if Dark frames are supplied)
            if (darkCount > 0) {
                onProgress(
                    StackProgress(
                        currentStep = 1,
                        totalSteps = totalSteps,
                        fraction = 0.04f,
                        message = "Integrating $darkCount dark frames into Master Dark..."
                    )
                )
                masterDarkU8 = buildMasterDarkFrame(
                    darkFrames = darkFrames,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight,
                    totalSteps = totalSteps,
                    onProgress = onProgress
                )
            } else {
                onProgress(
                    StackProgress(
                        currentStep = 1,
                        totalSteps = totalSteps,
                        fraction = 0.08f,
                        message = "Preparing $lightCount light frames..."
                    )
                )
            }

            // Build Freeze Ground foreground mask if enabled
            val hasFreezeMask = config.freezeGroundEnabled &&
                config.freezeGroundStrokes.any { it.points.isNotEmpty() }
            if (hasFreezeMask) {
                fgMaskFloatFull = renderForegroundMaskMat(
                    strokes = config.freezeGroundStrokes,
                    width = targetWidth,
                    height = targetHeight
                )
            }

            // Step 2: Align starfields or low-light handheld frames on 1024px proxy
            val proxyMaxEdge = ALIGN_PROXY_MAX_EDGE_PX
            val refProxyScale = min(1.0, proxyMaxEdge.toDouble() / max(targetWidth, targetHeight).toDouble())
            val proxyW = (targetWidth * refProxyScale).roundToInt().coerceAtLeast(32)
            val proxyH = (targetHeight * refProxyScale).roundToInt().coerceAtLeast(32)

            if (masterDarkU8 != null) {
                val darkSmall = Mat()
                val darkGray = Mat()
                try {
                    Imgproc.resize(masterDarkU8, darkSmall, Size(proxyW.toDouble(), proxyH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
                    Imgproc.cvtColor(darkSmall, darkGray, Imgproc.COLOR_BGR2GRAY)
                    masterDarkProxyGray = darkGray
                } finally {
                    darkSmall.release()
                }
            }

            if (fgMaskFloatFull != null) {
                val fgSmall = Mat()
                val skyMask = Mat()
                val skyMaskU8 = Mat()
                try {
                    Imgproc.resize(fgMaskFloatFull, fgSmall, Size(proxyW.toDouble(), proxyH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
                    Core.compare(fgSmall, Scalar(0.35), skyMask, Core.CMP_LT)
                    skyMask.convertTo(skyMaskU8, CvType.CV_8UC1)
                    fgMaskProxyU8 = skyMaskU8
                } finally {
                    fgSmall.release()
                    skyMask.release()
                }
            }

            val alignment = alignAstroFrames(
                lightFrames = lightFrames,
                refIndex = refIndex,
                alignmentMode = config.alignmentMode,
                masterDarkProxyGray = masterDarkProxyGray,
                skyOnlyMaskProxy = fgMaskProxyU8,
                proxyW = proxyW,
                proxyH = proxyH,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
                totalSteps = totalSteps,
                onProgress = onProgress
            )
            masterDarkProxyGray?.release()
            masterDarkProxyGray = null
            fgMaskProxyU8?.release()
            fgMaskProxyU8 = null

            val shouldCrop = config.autoCropBorders &&
                config.alignmentMode != AstroAlignmentMode.NONE_STAR_TRAILS &&
                !hasFreezeMask
            val cropRect = if (shouldCrop) {
                OpenCvAlignmentPipeline.computeValidOverlapRect(
                    transforms = alignment.transforms,
                    width = targetWidth,
                    height = targetHeight
                )
            } else {
                Rect(0, 0, targetWidth, targetHeight)
            }
            val wasAutoCropped = cropRect.width < targetWidth || cropRect.height < targetHeight
            val outWidth = cropRect.width
            val outHeight = cropRect.height

            if (fgMaskFloatFull != null) {
                fgMaskFloatCropped = if (wasAutoCropped) {
                    val sub = Mat(fgMaskFloatFull, cropRect)
                    val cloned = sub.clone()
                    sub.release()
                    cloned
                } else {
                    fgMaskFloatFull.clone()
                }
                fgMaskFloatFull.release()
                fgMaskFloatFull = null
            }

            // Step 3: Stream each Light frame -> Dark subtract -> Warp -> Freeze Ground blend -> Write tiled raw strip cache
            for (i in 0 until lightCount) {
                coroutineContext.ensureActive()
                val frac = 0.38f + 0.30f * ((i + 1).toFloat() / lightCount.toFloat())
                onProgress(
                    StackProgress(
                        currentStep = 3,
                        totalSteps = totalSteps,
                        fraction = frac,
                        message = "Calibrating & aligning light frame ${i + 1} of $lightCount..."
                    )
                )

                var rawBgr: Mat? = null
                var calibratedBgr: Mat? = null
                var alignedBgr: Mat? = null
                var blendedBgr: Mat? = null
                var finalBgr: Mat? = null

                try {
                    val frameBitmap = bitmapLoader.loadNormalizedBitmap(
                        uri = lightFrames[i].uri,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight
                    )
                    rawBgr = OpenCvAlignmentPipeline.bitmapToBgrMat(frameBitmap)
                    frameBitmap.recycle()

                    calibratedBgr = if (masterDarkU8 != null) {
                        val sub = Mat()
                        Core.subtract(rawBgr, masterDarkU8, sub)
                        rawBgr.release()
                        rawBgr = null
                        sub
                    } else {
                        val ref = rawBgr
                        rawBgr = null
                        ref
                    }

                    alignedBgr = if (i == refIndex || alignment.transforms[i] is OpenCvAlignmentPipeline.FrameTransform.Identity) {
                        calibratedBgr.clone()
                    } else {
                        OpenCvAlignmentPipeline.warpFullFrame(
                            srcBgr = calibratedBgr,
                            transform = alignment.transforms[i],
                            targetWidth = targetWidth,
                            targetHeight = targetHeight
                        )
                    }

                    blendedBgr = if (fgMaskFloatCropped != null && i != refIndex) {
                        blendForegroundAndSky(
                            foregroundBgr = calibratedBgr,
                            skyBgr = alignedBgr,
                            fgMaskFloat = fgMaskFloatCropped
                        )
                    } else {
                        val ref = alignedBgr
                        alignedBgr = null
                        ref
                    }

                    calibratedBgr.release()
                    calibratedBgr = null
                    alignedBgr?.release()
                    alignedBgr = null

                    finalBgr = if (wasAutoCropped) {
                        val sub = Mat(blendedBgr, cropRect)
                        val cloned = sub.clone()
                        sub.release()
                        blendedBgr.release()
                        blendedBgr = null
                        cloned
                    } else {
                        val ref = blendedBgr
                        blendedBgr = null
                        ref
                    }

                    val cacheFile = File(runDir, "aligned_$i.jpg")
                    OpenCvAlignmentPipeline.saveMatAsJpeg(finalBgr, cacheFile, quality = 96)
                    preallocatedCachePaths[i] = cacheFile.absolutePath
                    preallocatedPreviews[i] = OpenCvAlignmentPipeline.createPreviewFromBgrMat(finalBgr)

                    val rawBinFile = File(runDir, "astro_raw_$i.bin")
                    writeBgrMatBytes(finalBgr, rawBinFile)
                    rawStripFiles.add(rawBinFile)
                } finally {
                    rawBgr?.release()
                    calibratedBgr?.release()
                    alignedBgr?.release()
                    blendedBgr?.release()
                    finalBgr?.release()
                }
            }

            // Release calibration & mask buffers before tiled stacking
            masterDarkU8?.release()
            masterDarkU8 = null
            fgMaskFloatCropped?.release()
            fgMaskFloatCropped = null

            // Step 4: Memory-safe tiled/strip streaming accumulation
            val outputBgr = stackTiledRawFiles(
                rawFiles = rawStripFiles,
                width = outWidth,
                height = outHeight,
                algorithm = config.stackAlgorithm,
                kappa = config.sigmaClippingThreshold,
                totalSteps = totalSteps,
                onProgress = onProgress
            )

            val outputRgba = Mat()
            val outputBitmap: Bitmap
            try {
                Imgproc.cvtColor(outputBgr, outputRgba, Imgproc.COLOR_BGR2RGBA)
                outputBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(outputRgba, outputBitmap)
            } finally {
                outputRgba.release()
                outputBgr.release()
            }

            val elapsedMs = System.currentTimeMillis() - startTimeMs
            val algoLabel = when (config.stackAlgorithm) {
                AstroStackAlgorithm.KAPPA_SIGMA ->
                    "κ=${String.format(Locale.US, "%.1f", config.sigmaClippingThreshold)}σ Clip"
                AstroStackAlgorithm.MEDIAN -> "Median"
                AstroStackAlgorithm.MEAN -> "Mean"
                AstroStackAlgorithm.MAXIMUM_LIGHTEN -> "Max Lighten"
            }
            val darkSuffix = if (darkCount > 0) " • $darkCount Dark" else ""
            val fgSuffix = if (hasFreezeMask) " • Freeze Ground" else ""

            onProgress(
                StackProgress(
                    currentStep = totalSteps,
                    totalSteps = totalSteps,
                    fraction = 1f,
                    message = "Astro stack complete"
                )
            )

            val labels = lightFrames.mapIndexed { idx, _ ->
                if (idx == refIndex) "REF" else "LIGHT"
            }

            return StackResult(
                outputBitmap = outputBitmap,
                sourcePreviewBitmaps = preallocatedPreviews.map { it!! },
                alignedSourceCachePaths = preallocatedCachePaths.map { it!! },
                referenceFrameUri = lightFrames[refIndex].uri,
                frameNames = lightFrames.map { it.displayName },
                frameLabels = labels,
                stackMode = StackMode.AstroNoiseStack,
                defaultComparisonIndex = refIndex,
                alignedFramesCount = alignment.alignedCount,
                processingTimeMs = elapsedMs,
                usedProxyMode = budget.usedProxyMode,
                proxyReason = budget.reason,
                outputWidth = outputBitmap.width,
                outputHeight = outputBitmap.height,
                alignmentNote = "${alignment.note} • $algoLabel$darkSuffix$fgSuffix",
                wasAutoCropped = wasAutoCropped,
                resolutionPreset = config.outputSettings.maxResolution,
                lightFramesUsed = lightCount,
                darkFramesUsed = darkCount
            )
        } finally {
            masterDarkU8?.release()
            masterDarkProxyGray?.release()
            fgMaskFloatFull?.release()
            fgMaskFloatCropped?.release()
            fgMaskProxyU8?.release()
            rawStripFiles.forEach { it.delete() }
        }
    }

    private suspend fun buildMasterDarkFrame(
        darkFrames: List<StackFrame>,
        targetWidth: Int,
        targetHeight: Int,
        totalSteps: Int,
        onProgress: (StackProgress) -> Unit
    ): Mat {
        val count = darkFrames.size
        val sumMat = Mat.zeros(targetHeight, targetWidth, CvType.CV_32FC3)
        val masterU8 = Mat()
        try {
            for (i in 0 until count) {
                coroutineContext.ensureActive()
                val frac = 0.04f + 0.08f * ((i + 1).toFloat() / count.toFloat())
                onProgress(
                    StackProgress(
                        currentStep = 1,
                        totalSteps = totalSteps,
                        fraction = frac,
                        message = "Averaging Dark frame ${i + 1} of $count..."
                    )
                )
                val bmp = bitmapLoader.loadNormalizedBitmap(
                    uri = darkFrames[i].uri,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight
                )
                val bgr = OpenCvAlignmentPipeline.bitmapToBgrMat(bmp)
                bmp.recycle()
                val f32 = Mat()
                try {
                    bgr.convertTo(f32, CvType.CV_32FC3)
                    Core.add(sumMat, f32, sumMat)
                } finally {
                    bgr.release()
                    f32.release()
                }
            }
            Core.multiply(sumMat, Scalar(1.0 / count, 1.0 / count, 1.0 / count), sumMat)
            sumMat.convertTo(masterU8, CvType.CV_8UC3)
            return masterU8
        } finally {
            sumMat.release()
        }
    }

    private data class AstroAlignmentSummary(
        val transforms: Array<OpenCvAlignmentPipeline.FrameTransform>,
        val alignedCount: Int,
        val note: String
    )

    private suspend fun alignAstroFrames(
        lightFrames: List<StackFrame>,
        refIndex: Int,
        alignmentMode: AstroAlignmentMode,
        masterDarkProxyGray: Mat?,
        skyOnlyMaskProxy: Mat?,
        proxyW: Int,
        proxyH: Int,
        targetWidth: Int,
        targetHeight: Int,
        totalSteps: Int,
        onProgress: (StackProgress) -> Unit
    ): AstroAlignmentSummary {
        val count = lightFrames.size
        val transforms = Array<OpenCvAlignmentPipeline.FrameTransform>(count) {
            OpenCvAlignmentPipeline.FrameTransform.Identity
        }

        if (alignmentMode == AstroAlignmentMode.NONE_STAR_TRAILS) {
            return AstroAlignmentSummary(
                transforms = transforms,
                alignedCount = count,
                note = "Star Trails (Unaligned)"
            )
        }

        val scaleX = proxyW.toDouble() / targetWidth.toDouble()
        val scaleY = proxyH.toDouble() / targetHeight.toDouble()

        val refRawGray = loadProxyGray(lightFrames[refIndex], proxyW, proxyH, masterDarkProxyGray)
        val refBoostedGray = boostLowLightGray(refRawGray)
        var refStars: List<StarPeak>? = null
        var refTriangles: List<StarTriangle>? = null
        var refOrbKp: MatOfKeyPoint? = null
        var refOrbDesc: Mat? = null

        var alignedCount = 1
        var starMatchesCount = 0
        var lowLightFallbackCount = 0

        try {
            if (alignmentMode == AstroAlignmentMode.STAR_FIELD) {
                refStars = detectStarCentroids(refRawGray, skyOnlyMaskProxy, maxStars = 72)
                if (refStars.size >= 6) {
                    refTriangles = buildConstellationTriangles(refStars, topAnchorCount = 34, kNeighbors = 5)
                }
            }

            var done = 0
            for (i in 0 until count) {
                coroutineContext.ensureActive()
                if (i == refIndex) continue
                done++
                val frac = 0.14f + 0.24f * (done.toFloat() / (count - 1).coerceAtLeast(1).toFloat())
                val modeMsg = if (alignmentMode == AstroAlignmentMode.STAR_FIELD) {
                    "Matching star constellations ${done}/${count - 1}..."
                } else {
                    "Low-light aligning frame ${done}/${count - 1}..."
                }
                onProgress(
                    StackProgress(
                        currentStep = 2,
                        totalSteps = totalSteps,
                        fraction = frac,
                        message = modeMsg
                    )
                )

                val srcRawGray = loadProxyGray(lightFrames[i], proxyW, proxyH, masterDarkProxyGray)
                val srcBoostedGray = boostLowLightGray(srcRawGray)

                try {
                    var resolved: OpenCvAlignmentPipeline.FrameTransform? = null

                    if (alignmentMode == AstroAlignmentMode.STAR_FIELD && refStars != null && refTriangles != null) {
                        val srcStars = detectStarCentroids(srcRawGray, skyOnlyMaskProxy, maxStars = 72)
                        if (srcStars.size >= 6) {
                            resolved = tryStarConstellationAlignment(
                                srcStars = srcStars,
                                refStars = refStars,
                                refTriangles = refTriangles,
                                scaleX = scaleX,
                                scaleY = scaleY,
                                fullWidth = targetWidth,
                                fullHeight = targetHeight
                            )
                            if (resolved != null) {
                                starMatchesCount++
                            }
                        }
                    }

                    if (resolved == null) {
                        // Handheld Low-Light or Star-Field fallback: Multi-scale ECC + boosted ORB
                        resolved = tryMultiScaleEccAffine(
                            srcBoosted = srcBoostedGray,
                            refBoosted = refBoostedGray,
                            scaleX = scaleX,
                            scaleY = scaleY,
                            fullWidth = targetWidth,
                            fullHeight = targetHeight
                        )
                        if (resolved == null) {
                            if (refOrbKp == null || refOrbDesc == null) {
                                val kp = MatOfKeyPoint()
                                val desc = Mat()
                                ORB.create(3000).detectAndCompute(refBoostedGray, skyOnlyMaskProxy ?: Mat(), kp, desc)
                                refOrbKp = kp
                                refOrbDesc = desc
                            }
                            resolved = tryBoostedOrbHomography(
                                srcBoosted = srcBoostedGray,
                                skyMask = skyOnlyMaskProxy,
                                refKp = refOrbKp,
                                refDesc = refOrbDesc,
                                scaleX = scaleX,
                                scaleY = scaleY,
                                fullWidth = targetWidth,
                                fullHeight = targetHeight
                            )
                        }
                        if (resolved != null && alignmentMode == AstroAlignmentMode.STAR_FIELD) {
                            lowLightFallbackCount++
                        }
                    }

                    if (resolved != null) {
                        transforms[i] = resolved
                        alignedCount++
                    } else {
                        transforms[i] = OpenCvAlignmentPipeline.FrameTransform.Identity
                    }
                } finally {
                    srcRawGray.release()
                    srcBoostedGray.release()
                }
            }
        } finally {
            refRawGray.release()
            refBoostedGray.release()
            refOrbKp?.release()
            refOrbDesc?.release()
        }

        val note = when (alignmentMode) {
            AstroAlignmentMode.STAR_FIELD -> {
                if (starMatchesCount > 0 && lowLightFallbackCount == 0) {
                    "Star Field ($starMatchesCount aligned)"
                } else if (starMatchesCount > 0) {
                    "Star Field ($starMatchesCount) + Low-Light ($lowLightFallbackCount)"
                } else {
                    "Low-Light ECC/ORB"
                }
            }
            AstroAlignmentMode.HANDHELD_LOW_LIGHT -> "Handheld Low-Light"
            AstroAlignmentMode.NONE_STAR_TRAILS -> "Star Trails"
        }

        return AstroAlignmentSummary(
            transforms = transforms,
            alignedCount = alignedCount,
            note = note
        )
    }

    private fun loadProxyGray(
        frame: StackFrame,
        proxyW: Int,
        proxyH: Int,
        masterDarkProxyGray: Mat?
    ): Mat {
        val bmp = bitmapLoader.loadNormalizedBitmap(
            uri = frame.uri,
            targetWidth = proxyW,
            targetHeight = proxyH
        )
        val bgr = OpenCvAlignmentPipeline.bitmapToBgrMat(bmp)
        bmp.recycle()
        val gray = Mat()
        return try {
            Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
            if (masterDarkProxyGray != null) {
                val cal = Mat()
                Core.subtract(gray, masterDarkProxyGray, cal)
                cal
            } else {
                gray.clone()
            }
        } finally {
            bgr.release()
            gray.release()
        }
    }

    /**
     * High-gain luminance boost (gamma + CLAHE) on dark proxies for Handheld Low-Light ECC/ORB.
     */
    private fun boostLowLightGray(grayU8: Mat): Mat {
        val f32 = Mat()
        val gamma = Mat()
        val boostedU8 = Mat()
        val claheOut = Mat()
        return try {
            grayU8.convertTo(f32, CvType.CV_32FC1, 1.0 / 255.0)
            Core.pow(f32, 0.45, gamma)
            gamma.convertTo(boostedU8, CvType.CV_8UC1, 255.0)
            val clahe = Imgproc.createCLAHE(3.2, Size(8.0, 8.0))
            clahe.apply(boostedU8, claheOut)
            claheOut.clone()
        } finally {
            f32.release()
            gamma.release()
            boostedU8.release()
            claheOut.release()
        }
    }

    private data class StarPeak(val x: Double, val y: Double, val flux: Double)

    private data class StarTriangle(
        val iA: Int, // vertex opposite longest side a
        val iB: Int, // vertex opposite medium side b
        val iC: Int, // vertex opposite shortest side c
        val ratioB: Double, // b / a
        val ratioC: Double  // c / a
    )

    private fun detectStarCentroids(
        grayU8: Mat,
        skyMask: Mat?,
        maxStars: Int
    ): List<StarPeak> {
        val rows = grayU8.rows()
        val cols = grayU8.cols()
        val f32 = Mat()
        val bg = Mat()
        val starMap = Mat()
        val smoothed = Mat()
        val dilated = Mat()
        val peaksMask = Mat()
        val meanMat = MatOfDouble()
        val stdMat = MatOfDouble()

        return try {
            grayU8.convertTo(f32, CvType.CV_32FC1)
            Imgproc.GaussianBlur(f32, bg, Size(31.0, 31.0), 0.0)
            Core.subtract(f32, bg, starMap)
            Core.max(starMap, Scalar(0.0), starMap)

            if (skyMask != null) {
                val maskF = Mat()
                skyMask.convertTo(maskF, CvType.CV_32FC1, 1.0 / 255.0)
                Core.multiply(starMap, maskF, starMap)
                maskF.release()
            }

            Imgproc.GaussianBlur(starMap, smoothed, Size(3.0, 3.0), 0.8)
            val dilateK = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
            Imgproc.dilate(smoothed, dilated, dilateK)
            dilateK.release()

            Core.compare(smoothed, dilated, peaksMask, Core.CMP_EQ)
            Core.meanStdDev(smoothed, meanMat, stdMat)
            val meanVal = meanMat.toArray().firstOrNull() ?: 0.0
            val stdVal = stdMat.toArray().firstOrNull() ?: 2.0
            val minFlux = max(6.0, meanVal + 3.5 * stdVal)

            val mapData = FloatArray(rows * cols)
            smoothed.get(0, 0, mapData)
            val peakBytes = ByteArray(rows * cols)
            peaksMask.get(0, 0, peakBytes)

            val candidates = ArrayList<StarPeak>(256)
            val margin = 4
            for (y in margin until rows - margin) {
                val rowOff = y * cols
                for (x in margin until cols - margin) {
                    val idx = rowOff + x
                    if (peakBytes[idx] != 0.toByte()) {
                        val valCenter = mapData[idx].toDouble()
                        if (valCenter >= minFlux) {
                            // Sub-pixel 5x5 intensity-weighted centroid
                            var sumW = 0.0
                            var sumWx = 0.0
                            var sumWy = 0.0
                            for (dy in -2..2) {
                                val rOff = (y + dy) * cols
                                for (dx in -2..2) {
                                    val w = mapData[rOff + (x + dx)].toDouble().coerceAtLeast(0.0)
                                    sumW += w
                                    sumWx += w * (x + dx)
                                    sumWy += w * (y + dy)
                                }
                            }
                            if (sumW > 1e-6) {
                                candidates.add(StarPeak(sumWx / sumW, sumWy / sumW, valCenter))
                            }
                        }
                    }
                }
            }

            candidates.sortByDescending { it.flux }
            if (candidates.size > maxStars) candidates.subList(0, maxStars) else candidates
        } finally {
            f32.release()
            bg.release()
            starMap.release()
            smoothed.release()
            dilated.release()
            peaksMask.release()
            meanMat.release()
            stdMat.release()
        }
    }

    private fun buildConstellationTriangles(
        stars: List<StarPeak>,
        topAnchorCount: Int,
        kNeighbors: Int
    ): List<StarTriangle> {
        val anchorCount = min(stars.size, topAnchorCount)
        val searchPool = min(stars.size, 64)
        val triangles = ArrayList<StarTriangle>(anchorCount * 10)
        val seenKeys = HashSet<Long>(anchorCount * 12)

        for (i in 0 until anchorCount) {
            val s0 = stars[i]
            val neighbors = (0 until searchPool)
                .filter { it != i }
                .map { j -> j to hypot(stars[j].x - s0.x, stars[j].y - s0.y) }
                .filter { it.second > 10.0 }
                .sortedBy { it.second }
                .take(kNeighbors)

            for (aIdx in 0 until neighbors.size) {
                for (bIdx in aIdx + 1 until neighbors.size) {
                    val idx1 = i
                    val idx2 = neighbors[aIdx].first
                    val idx3 = neighbors[bIdx].first

                    val s1 = stars[idx1]
                    val s2 = stars[idx2]
                    val s3 = stars[idx3]

                    val d12 = hypot(s1.x - s2.x, s1.y - s2.y)
                    val d23 = hypot(s2.x - s3.x, s2.y - s3.y)
                    val d31 = hypot(s3.x - s1.x, s3.y - s1.y)

                    // Deduplicate index triplet
                    val sortedIds = intArrayOf(idx1, idx2, idx3).apply { sort() }
                    val key = (sortedIds[0].toLong() shl 20) or (sortedIds[1].toLong() shl 10) or sortedIds[2].toLong()
                    if (!seenKeys.add(key)) continue

                    // Opposite sides: side opposite idx1 is d23; opposite idx2 is d31; opposite idx3 is d12
                    val pairs = arrayOf(
                        idx1 to d23,
                        idx2 to d31,
                        idx3 to d12
                    ).sortedByDescending { it.second }

                    val maxSide = pairs[0].second
                    val medSide = pairs[1].second
                    val minSide = pairs[2].second
                    if (maxSide < 18.0 || minSide < 8.0) continue

                    val rB = medSide / maxSide
                    val rC = minSide / maxSide
                    // Reject near-degenerate or ambiguous equilateral triangles
                    if (rB < 0.96 && rC < rB * 0.96) {
                        triangles.add(
                            StarTriangle(
                                iA = pairs[0].first,
                                iB = pairs[1].first,
                                iC = pairs[2].first,
                                ratioB = rB,
                                ratioC = rC
                            )
                        )
                    }
                }
            }
        }
        return triangles
    }

    private fun tryStarConstellationAlignment(
        srcStars: List<StarPeak>,
        refStars: List<StarPeak>,
        refTriangles: List<StarTriangle>,
        scaleX: Double,
        scaleY: Double,
        fullWidth: Int,
        fullHeight: Int
    ): OpenCvAlignmentPipeline.FrameTransform? {
        val srcTriangles = buildConstellationTriangles(srcStars, topAnchorCount = 34, kNeighbors = 5)
        if (srcTriangles.isEmpty() || refTriangles.isEmpty()) return null

        val votes = HashMap<Long, Int>(256)
        val tol = 0.018

        for (st in srcTriangles) {
            for (rt in refTriangles) {
                if (abs(st.ratioB - rt.ratioB) <= tol && abs(st.ratioC - rt.ratioC) <= tol) {
                    val kA = (st.iA.toLong() shl 16) or rt.iA.toLong()
                    val kB = (st.iB.toLong() shl 16) or rt.iB.toLong()
                    val kC = (st.iC.toLong() shl 16) or rt.iC.toLong()
                    votes[kA] = (votes[kA] ?: 0) + 1
                    votes[kB] = (votes[kB] ?: 0) + 1
                    votes[kC] = (votes[kC] ?: 0) + 1
                }
            }
        }

        if (votes.isEmpty()) return null

        val topPairs = votes.entries
            .filter { it.value >= 2 }
            .sortedByDescending { it.value }
            .take(40)

        if (topPairs.size < 4) return null

        val srcPtsList = ArrayList<Point>(topPairs.size)
        val dstPtsList = ArrayList<Point>(topPairs.size)
        for (entry in topPairs) {
            val sIdx = (entry.key ushr 16).toInt()
            val rIdx = (entry.key and 0xFFFFL).toInt()
            val sp = srcStars[sIdx]
            val rp = refStars[rIdx]
            srcPtsList.add(Point(sp.x, sp.y))
            dstPtsList.add(Point(rp.x, rp.y))
        }

        val srcPts = MatOfPoint2f(*srcPtsList.toTypedArray())
        val dstPts = MatOfPoint2f(*dstPtsList.toTypedArray())
        val inliers = Mat()

        return try {
            val affineProxy = Calib3d.estimateAffine2D(
                srcPts,
                dstPts,
                inliers,
                Calib3d.RANSAC,
                3.5,
                2000,
                0.99,
                10
            )
            if (affineProxy.empty()) {
                affineProxy.release()
                return null
            }

            val inlierCount = Core.countNonZero(inliers)
            val mProxy = DoubleArray(6)
            affineProxy.get(0, 0, mProxy)
            affineProxy.release()

            if (inlierCount < 4) return null

            val mFull = doubleArrayOf(
                mProxy[0],
                mProxy[1] * (scaleY / scaleX),
                mProxy[2] / scaleX,
                mProxy[3] * (scaleX / scaleY),
                mProxy[4],
                mProxy[5] / scaleY
            )
            if (!isValidAstroAffine(mFull, fullWidth, fullHeight)) {
                null
            } else {
                OpenCvAlignmentPipeline.FrameTransform.Affine(mFull)
            }
        } catch (_: Exception) {
            null
        } finally {
            srcPts.release()
            dstPts.release()
            inliers.release()
        }
    }

    private fun tryMultiScaleEccAffine(
        srcBoosted: Mat,
        refBoosted: Mat,
        scaleX: Double,
        scaleY: Double,
        fullWidth: Int,
        fullHeight: Int
    ): OpenCvAlignmentPipeline.FrameTransform.Affine? {
        val refBlur = Mat()
        val srcBlur = Mat()
        val ref32 = Mat()
        val src32 = Mat()
        val refHalf = Mat()
        val srcHalf = Mat()
        val warpInvProxy = Mat.eye(2, 3, CvType.CV_32F)
        val warpInvFull = Mat(2, 3, CvType.CV_64F)
        val forwardFull = Mat()

        return try {
            Imgproc.GaussianBlur(refBoosted, refBlur, Size(5.0, 5.0), 0.0)
            Imgproc.GaussianBlur(srcBoosted, srcBlur, Size(5.0, 5.0), 0.0)
            refBlur.convertTo(ref32, CvType.CV_32F, 1.0 / 255.0)
            srcBlur.convertTo(src32, CvType.CV_32F, 1.0 / 255.0)

            // Coarse 512px pass for wider capture radius on handheld shots
            Imgproc.pyrDown(ref32, refHalf)
            Imgproc.pyrDown(src32, srcHalf)
            val criteriaCoarse = TermCriteria(TermCriteria.COUNT or TermCriteria.EPS, 50, 5e-4)
            try {
                Video.findTransformECC(refHalf, srcHalf, warpInvProxy, Video.MOTION_AFFINE, criteriaCoarse)
                // Scale translation up 2x for 1024px fine pass
                warpInvProxy.put(0, 2, warpInvProxy.get(0, 2)[0] * 2.0)
                warpInvProxy.put(1, 2, warpInvProxy.get(1, 2)[0] * 2.0)
            } catch (_: Exception) {
                warpInvProxy.setTo(Scalar(0.0))
                warpInvProxy.put(0, 0, 1.0)
                warpInvProxy.put(1, 1, 1.0)
            }

            // Fine 1024px ECC pass
            val criteriaFine = TermCriteria(TermCriteria.COUNT or TermCriteria.EPS, 70, 1e-4)
            Video.findTransformECC(ref32, src32, warpInvProxy, Video.MOTION_AFFINE, criteriaFine)

            val a = warpInvProxy.get(0, 0)[0]
            val b = warpInvProxy.get(0, 1)[0] * (scaleY / scaleX)
            val tx = warpInvProxy.get(0, 2)[0] / scaleX
            val c = warpInvProxy.get(1, 0)[0] * (scaleX / scaleY)
            val d = warpInvProxy.get(1, 1)[0]
            val ty = warpInvProxy.get(1, 2)[0] / scaleY

            warpInvFull.put(0, 0, a, b, tx, c, d, ty)
            Imgproc.invertAffineTransform(warpInvFull, forwardFull)

            val m = DoubleArray(6)
            forwardFull.get(0, 0, m)
            if (isValidAstroAffine(m, fullWidth, fullHeight)) {
                OpenCvAlignmentPipeline.FrameTransform.Affine(m)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            refBlur.release()
            srcBlur.release()
            ref32.release()
            src32.release()
            refHalf.release()
            srcHalf.release()
            warpInvProxy.release()
            warpInvFull.release()
            forwardFull.release()
        }
    }

    private fun tryBoostedOrbHomography(
        srcBoosted: Mat,
        skyMask: Mat?,
        refKp: MatOfKeyPoint?,
        refDesc: Mat?,
        scaleX: Double,
        scaleY: Double,
        fullWidth: Int,
        fullHeight: Int
    ): OpenCvAlignmentPipeline.FrameTransform.Homography? {
        if (refKp == null || refDesc == null || refDesc.empty()) return null
        val srcKp = MatOfKeyPoint()
        val srcDesc = Mat()
        return try {
            ORB.create(3000).detectAndCompute(srcBoosted, skyMask ?: Mat(), srcKp, srcDesc)
            if (srcDesc.empty() || srcDesc.rows() < 10) return null

            val matcher = BFMatcher.create(Core.NORM_HAMMING, false)
            val knnMatches = ArrayList<MatOfDMatch>()
            matcher.knnMatch(srcDesc, refDesc, knnMatches, 2)

            val good = ArrayList<DMatch>()
            for (m in knnMatches) {
                val arr = m.toArray()
                if (arr.size >= 2 && arr[0].distance < 0.78f * arr[1].distance) {
                    good.add(arr[0])
                }
                m.release()
            }
            if (good.size < 10) return null

            val sArr = srcKp.toArray()
            val rArr = refKp.toArray()
            val sPts = MatOfPoint2f(*good.map { sArr[it.queryIdx].pt }.toTypedArray())
            val rPts = MatOfPoint2f(*good.map { rArr[it.trainIdx].pt }.toTypedArray())
            val hMat = Calib3d.findHomography(sPts, rPts, Calib3d.RANSAC, 4.0)
            sPts.release()
            rPts.release()
            if (hMat.empty()) {
                hMat.release()
                return null
            }

            val hp = DoubleArray(9)
            hMat.get(0, 0, hp)
            hMat.release()

            val h22 = if (abs(hp[8]) > 1e-9) hp[8] else 1.0
            val hFull = doubleArrayOf(
                hp[0] / h22,
                (hp[1] * (scaleY / scaleX)) / h22,
                (hp[2] / scaleX) / h22,
                (hp[3] * (scaleX / scaleY)) / h22,
                hp[4] / h22,
                (hp[5] / scaleY) / h22,
                (hp[6] * scaleX) / h22,
                (hp[7] * scaleY) / h22,
                1.0
            )
            val det = hFull[0] * hFull[4] - hFull[1] * hFull[3]
            if (hFull.any { !it.isFinite() } || det < 0.45 || det > 2.2 ||
                abs(hFull[2]) > fullWidth * 0.35 || abs(hFull[5]) > fullHeight * 0.35
            ) {
                null
            } else {
                OpenCvAlignmentPipeline.FrameTransform.Homography(hFull)
            }
        } catch (_: Exception) {
            null
        } finally {
            srcKp.release()
            srcDesc.release()
        }
    }

    private fun isValidAstroAffine(m: DoubleArray, width: Int, height: Int): Boolean {
        if (m.size < 6 || m.any { !it.isFinite() }) return false
        val det = m[0] * m[4] - m[1] * m[3]
        if (det < 0.55 || det > 1.85) return false
        if (abs(m[2]) > width * 0.35 || abs(m[5]) > height * 0.35) return false
        return true
    }

    private fun renderForegroundMaskMat(
        strokes: List<ForegroundMaskStroke>,
        width: Int,
        height: Int
    ): Mat {
        // Render at capped proxy size (max 1024px) for fast blur, then resize to full target dimensions
        val maxDim = max(width, height).coerceAtLeast(1)
        val scale = min(1.0, 1024.0 / maxDim.toDouble())
        val pW = (width * scale).roundToInt().coerceAtLeast(16)
        val pH = (height * scale).roundToInt().coerceAtLeast(16)

        val bmp = Bitmap.createBitmap(pW, pH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.BLACK)
        val minDim = min(pW, pH).toFloat()

        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val diameter = (stroke.brushSizeFraction * minDim).coerceAtLeast(4f)
            if (stroke.points.size == 1) {
                paint.style = Paint.Style.FILL
                val p0 = stroke.points[0]
                canvas.drawCircle(p0.u * pW, p0.v * pH, diameter * 0.5f, paint)
            } else {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = diameter
                val path = Path()
                stroke.points.forEachIndexed { idx, pt ->
                    val x = pt.u.coerceIn(0f, 1f) * pW
                    val y = pt.v.coerceIn(0f, 1f) * pH
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                canvas.drawPath(path, paint)
            }
        }

        val rgba = Mat()
        val gray = Mat()
        val floatMask = Mat()
        val blurred = Mat()
        val fullMask = Mat()
        return try {
            Utils.bitmapToMat(bmp, rgba)
            bmp.recycle()
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            gray.convertTo(floatMask, CvType.CV_32FC1, 1.0 / 255.0)
            val blurSigma = (minDim * 0.014).coerceIn(3.0, 18.0)
            Imgproc.GaussianBlur(floatMask, blurred, Size(0.0, 0.0), blurSigma, blurSigma, Core.BORDER_REPLICATE)
            Imgproc.resize(blurred, fullMask, Size(width.toDouble(), height.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
            Core.min(fullMask, Scalar(1.0), fullMask)
            Core.max(fullMask, Scalar(0.0), fullMask)
            fullMask.clone()
        } finally {
            rgba.release()
            gray.release()
            floatMask.release()
            blurred.release()
            fullMask.release()
        }
    }

    private fun blendForegroundAndSky(
        foregroundBgr: Mat,
        skyBgr: Mat,
        fgMaskFloat: Mat
    ): Mat {
        val fgF = Mat()
        val skyF = Mat()
        val mask3 = Mat()
        val invMask3 = Mat()
        val termFg = Mat()
        val termSky = Mat()
        val blendedF = Mat()
        val outU8 = Mat()

        return try {
            foregroundBgr.convertTo(fgF, CvType.CV_32FC3)
            skyBgr.convertTo(skyF, CvType.CV_32FC3)
            Imgproc.cvtColor(fgMaskFloat, mask3, Imgproc.COLOR_GRAY2BGR)
            Core.multiply(mask3, Scalar(-1.0, -1.0, -1.0), invMask3)
            Core.add(invMask3, Scalar(1.0, 1.0, 1.0), invMask3)

            Core.multiply(fgF, mask3, termFg)
            Core.multiply(skyF, invMask3, termSky)
            Core.add(termFg, termSky, blendedF)
            blendedF.convertTo(outU8, CvType.CV_8UC3)
            outU8.clone()
        } finally {
            fgF.release()
            skyF.release()
            mask3.release()
            invMask3.release()
            termFg.release()
            termSky.release()
            blendedF.release()
            outU8.release()
        }
    }

    private fun writeBgrMatBytes(bgrMat: Mat, file: File) {
        val rows = bgrMat.rows()
        val cols = bgrMat.cols()
        val chunkRows = min(64, rows)
        val buf = ByteArray(chunkRows * cols * 3)
        BufferedOutputStream(FileOutputStream(file), 65536).use { out ->
            var r = 0
            while (r < rows) {
                val count = min(chunkRows, rows - r)
                val sub = bgrMat.rowRange(r, r + count)
                sub.get(0, 0, buf)
                sub.release()
                out.write(buf, 0, count * cols * 3)
                r += count
            }
        }
    }

    private suspend fun stackTiledRawFiles(
        rawFiles: List<File>,
        width: Int,
        height: Int,
        algorithm: AstroStackAlgorithm,
        kappa: Float,
        totalSteps: Int,
        onProgress: (StackProgress) -> Unit
    ): Mat {
        val frameCount = rawFiles.size
        val rowBytes = width * 3
        val maxTotalStripBytes = 24 * 1024 * 1024 // 24 MB memory cap across all frames per strip
        val stripRows = (maxTotalStripBytes / (frameCount * rowBytes).coerceAtLeast(1))
            .coerceIn(16, 256)

        val outputBgr = Mat(height, width, CvType.CV_8UC3)
        val rafs = rawFiles.map { RandomAccessFile(it, "r") }
        val maxStripBufLen = stripRows * rowBytes
        val stripBuffers = Array(frameCount) { ByteArray(maxStripBufLen) }
        val outStripBuf = ByteArray(maxStripBufLen)
        val sampleScratch = FloatArray(frameCount)

        try {
            var y = 0
            while (y < height) {
                coroutineContext.ensureActive()
                val countRows = min(stripRows, height - y)
                val bytesNeeded = countRows * rowBytes
                val offset = y.toLong() * rowBytes.toLong()

                val frac = 0.69f + 0.29f * ((y + countRows).toFloat() / height.toFloat())
                onProgress(
                    StackProgress(
                        currentStep = 4,
                        totalSteps = totalSteps,
                        fraction = frac,
                        message = "Stacking tile rows ${y + 1}–${y + countRows} of $height (${algorithm.label})..."
                    )
                )

                for (k in 0 until frameCount) {
                    val raf = rafs[k]
                    raf.seek(offset)
                    var readTotal = 0
                    while (readTotal < bytesNeeded) {
                        val n = raf.read(stripBuffers[k], readTotal, bytesNeeded - readTotal)
                        if (n <= 0) break
                        readTotal += n
                    }
                }

                stackByteStrip(
                    stripBuffers = stripBuffers,
                    outStrip = outStripBuf,
                    byteCount = bytesNeeded,
                    frameCount = frameCount,
                    algorithm = algorithm,
                    kappa = kappa,
                    scratch = sampleScratch
                )

                val sub = outputBgr.rowRange(y, y + countRows)
                sub.put(0, 0, outStripBuf.copyOfRange(0, bytesNeeded))
                sub.release()

                y += countRows
            }
            return outputBgr
        } finally {
            rafs.forEach {
                try {
                    it.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    companion object {
        private const val ALIGN_PROXY_MAX_EDGE_PX = 1024

        fun resolveReferenceLightIndex(lightFrames: List<StackFrame>): Int {
            if (lightFrames.isEmpty()) return 0
            return lightFrames.size / 2
        }

        fun resolveReferenceLightFrameId(frames: List<StackFrame>): String? {
            val lights = frames.filter { it.isEnabled && it.astroFrameType == AstroFrameType.LIGHT }
            if (lights.isEmpty()) return null
            return lights[resolveReferenceLightIndex(lights)].id
        }

        internal fun stackByteStrip(
            stripBuffers: Array<ByteArray>,
            outStrip: ByteArray,
            byteCount: Int,
            frameCount: Int,
            algorithm: AstroStackAlgorithm,
            kappa: Float,
            scratch: FloatArray
        ) {
            when (algorithm) {
                AstroStackAlgorithm.MEAN -> {
                    val invN = 1f / frameCount.toFloat()
                    for (p in 0 until byteCount) {
                        var sum = 0
                        for (k in 0 until frameCount) {
                            sum += stripBuffers[k][p].toInt() and 0xFF
                        }
                        outStrip[p] = (sum * invN).roundToInt().coerceIn(0, 255).toByte()
                    }
                }

                AstroStackAlgorithm.MAXIMUM_LIGHTEN -> {
                    for (p in 0 until byteCount) {
                        var maxVal = 0
                        for (k in 0 until frameCount) {
                            val v = stripBuffers[k][p].toInt() and 0xFF
                            if (v > maxVal) maxVal = v
                        }
                        outStrip[p] = maxVal.toByte()
                    }
                }

                AstroStackAlgorithm.MEDIAN -> {
                    for (p in 0 until byteCount) {
                        for (k in 0 until frameCount) {
                            scratch[k] = (stripBuffers[k][p].toInt() and 0xFF).toFloat()
                        }
                        outStrip[p] = computeMedian(scratch, frameCount).roundToInt().coerceIn(0, 255).toByte()
                    }
                }

                AstroStackAlgorithm.KAPPA_SIGMA -> {
                    for (p in 0 until byteCount) {
                        for (k in 0 until frameCount) {
                            scratch[k] = (stripBuffers[k][p].toInt() and 0xFF).toFloat()
                        }
                        outStrip[p] = computeKappaSigmaClippedMean(scratch, frameCount, kappa)
                            .roundToInt()
                            .coerceIn(0, 255)
                            .toByte()
                    }
                }
            }
        }

        internal fun computeMedian(values: FloatArray, count: Int): Float {
            if (count <= 0) return 0f
            if (count == 1) return values[0]
            // Insertion sort on small N (2..30 frames) is faster and allocation-free
            for (i in 1 until count) {
                val key = values[i]
                var j = i - 1
                while (j >= 0 && values[j] > key) {
                    values[j + 1] = values[j]
                    j--
                }
                values[j + 1] = key
            }
            val mid = count / 2
            return if (count % 2 == 1) values[mid] else (values[mid - 1] + values[mid]) * 0.5f
        }

        /**
         * Two-iteration Kappa-Sigma Clipping:
         * - Iteration 1: anchor around median (when count >= 3) so bright airplane/satellite trails
         *   cannot pull the center or inflate sigma; reject |x - center| > kappa * stdDev.
         * - Iteration 2: recompute mean & stdDev on remaining clean pixels and reject outliers again.
         * - Averages clean pixels (falling back to median if all samples are clipped).
         */
        internal fun computeKappaSigmaClippedMean(
            values: FloatArray,
            count: Int,
            kappa: Float
        ): Float {
            if (count <= 0) return 0f
            if (count == 1) return values[0]
            if (count == 2) return (values[0] + values[1]) * 0.5f

            val median = computeMedian(values, count)
            // `values[0 until count]` is now sorted ascending.
            // Merge absolute deviations |v - median| outward from median using two pointers (zero allocation).
            val mid = count / 2
            var left: Int
            var right: Int
            var devCount = 0
            var madPrev = 0f
            var madCurr = 0f
            var trimmedVarSum = 0.0
            val trimmedTarget = max(2, count - max(1, count / 4))
            val madLowIdx = (count - 1) / 2
            val madHighIdx = count / 2

            if (count % 2 == 1) {
                left = mid - 1
                right = mid + 1
                madCurr = 0f
                madPrev = 0f
                devCount = 1
            } else {
                left = mid - 1
                right = mid
            }

            while (devCount < count) {
                val dev = if (left >= 0 && right < count) {
                    val dl = median - values[left]
                    val dr = values[right] - median
                    if (dl <= dr) {
                        left--
                        dl
                    } else {
                        right++
                        dr
                    }
                } else if (left >= 0) {
                    left--
                    median - values[left + 1]
                } else {
                    right++
                    values[right - 1] - median
                }

                if (devCount == madLowIdx) madPrev = dev
                if (devCount == madHighIdx) madCurr = dev
                if (devCount < trimmedTarget) {
                    trimmedVarSum += dev.toDouble() * dev.toDouble()
                }
                devCount++
            }

            val mad = if (count % 2 == 1) madCurr else (madPrev + madCurr) * 0.5f
            val madSigma = 1.4826f * mad
            val trimmedSigma = sqrt(trimmedVarSum / trimmedTarget).toFloat()
            val robustSigma = max(madSigma, trimmedSigma).coerceAtLeast(1.0f)

            val thresh1 = kappa * robustSigma
            var keptCount = 0
            var keptSum = 0.0
            for (i in 0 until count) {
                if (abs(values[i] - median) <= thresh1) {
                    values[keptCount++] = values[i]
                    keptSum += values[i]
                }
            }
            if (keptCount == 0) return median
            if (keptCount == 1) return values[0]

            val mean2 = (keptSum / keptCount).toFloat()
            var varSum2 = 0.0
            for (i in 0 until keptCount) {
                val d = (values[i] - mean2).toDouble()
                varSum2 += d * d
            }
            val std2 = sqrt(varSum2 / keptCount).toFloat()
            if (std2 <= 1e-4f) return mean2

            val thresh2 = kappa * std2
            var finalCount = 0
            var finalSum = 0.0
            for (i in 0 until keptCount) {
                if (abs(values[i] - mean2) <= thresh2) {
                    finalSum += values[i]
                    finalCount++
                }
            }
            return if (finalCount > 0) (finalSum / finalCount).toFloat() else mean2
        }
    }
}
