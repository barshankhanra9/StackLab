package com.example.engine

import android.graphics.Bitmap
import com.example.domain.model.AlignmentMode
import com.example.domain.model.StackFrame
import com.example.domain.model.StackProgress
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
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
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video

internal object OpenCvAlignmentPipeline {

    private const val ALIGN_PROXY_MAX_EDGE_PX = 1024
    const val PREVIEW_MAX_EDGE_PX = 1080

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

    sealed interface FrameTransform {
        data object Identity : FrameTransform
        data class Affine(val matrix2x3: DoubleArray) : FrameTransform
        data class Homography(val matrix3x3: DoubleArray) : FrameTransform
    }

    data class AlignmentResult(
        val transforms: Array<FrameTransform>,
        val alignedCount: Int,
        val eccSuccessCount: Int,
        val orbFallbackCount: Int,
        val alignmentNote: String
    )

    suspend fun alignFramesOnProxy(
        bitmapLoader: FrameBitmapLoader,
        frames: List<StackFrame>,
        refIndex: Int,
        alignmentMode: AlignmentMode,
        targetWidth: Int,
        targetHeight: Int,
        totalSteps: Int,
        progressStart: Float = 0.06f,
        progressSpan: Float = 0.30f,
        onProgress: (StackProgress) -> Unit
    ): AlignmentResult {
        val frameCount = frames.size
        val transforms = Array<FrameTransform>(frameCount) { FrameTransform.Identity }
        var alignedCount = 1
        var eccSuccessCount = 0
        var orbFallbackCount = 0

        if (alignmentMode == AlignmentMode.AUTO_HOMOGRAPHY) {
            var refGray1024: Mat? = null
            var refKeypoints1024: MatOfKeyPoint? = null
            var refDescriptors1024: Mat? = null

            try {
                val refProxyBmp = bitmapLoader.loadNormalizedBitmap(
                    uri = frames[refIndex].uri,
                    maxEdgePx = ALIGN_PROXY_MAX_EDGE_PX
                )
                val refProxyBgr = bitmapToBgrMat(refProxyBmp)
                refProxyBmp.recycle()

                refGray1024 = Mat()
                Imgproc.cvtColor(refProxyBgr, refGray1024, Imgproc.COLOR_BGR2GRAY)
                refProxyBgr.release()

                val proxyW = refGray1024.cols()
                val proxyH = refGray1024.rows()
                val scaleX = proxyW.toDouble() / targetWidth.toDouble()
                val scaleY = proxyH.toDouble() / targetHeight.toDouble()

                var alignedDone = 0
                for (i in 0 until frameCount) {
                    coroutineContext.ensureActive()
                    if (i == refIndex) continue

                    alignedDone++
                    val frac = progressStart + progressSpan * (alignedDone.toFloat() / (frameCount - 1).coerceAtLeast(1))
                    onProgress(
                        StackProgress(
                            currentStep = 1,
                            totalSteps = totalSteps,
                            fraction = frac,
                            message = "ECC 1024px aligning frame ${i + 1} of $frameCount..."
                        )
                    )

                    val srcProxyBmp = bitmapLoader.loadNormalizedBitmap(
                        uri = frames[i].uri,
                        targetWidth = proxyW,
                        targetHeight = proxyH
                    )
                    val srcProxyBgr = bitmapToBgrMat(srcProxyBmp)
                    srcProxyBmp.recycle()

                    val srcGray1024 = Mat()
                    Imgproc.cvtColor(srcProxyBgr, srcGray1024, Imgproc.COLOR_BGR2GRAY)
                    srcProxyBgr.release()

                    try {
                        val eccAffine = tryEccAffineOnProxy(
                            srcGray = srcGray1024,
                            refGray = refGray1024,
                            scaleX = scaleX,
                            scaleY = scaleY,
                            fullWidth = targetWidth,
                            fullHeight = targetHeight
                        )
                        if (eccAffine != null) {
                            transforms[i] = eccAffine
                            alignedCount++
                            eccSuccessCount++
                        } else {
                            if (refKeypoints1024 == null || refDescriptors1024 == null) {
                                val kp = MatOfKeyPoint()
                                val desc = Mat()
                                ORB.create(2000).detectAndCompute(refGray1024, Mat(), kp, desc)
                                refKeypoints1024 = kp
                                refDescriptors1024 = desc
                            }
                            val orbHomography = tryOrbHomographyOnProxy(
                                srcGray = srcGray1024,
                                refKeypoints = refKeypoints1024,
                                refDescriptors = refDescriptors1024,
                                scaleX = scaleX,
                                scaleY = scaleY,
                                fullWidth = targetWidth,
                                fullHeight = targetHeight
                            )
                            if (orbHomography != null) {
                                transforms[i] = orbHomography
                                alignedCount++
                                orbFallbackCount++
                            } else {
                                transforms[i] = FrameTransform.Identity
                            }
                        }
                    } finally {
                        srcGray1024.release()
                    }
                }
            } finally {
                refGray1024?.release()
                refKeypoints1024?.release()
                refDescriptors1024?.release()
            }
        }

        val alignmentNote = when {
            alignmentMode == AlignmentMode.NONE -> "Tripod mode (no warping)"
            orbFallbackCount > 0 -> "ECC affine ($eccSuccessCount) + ORB ($orbFallbackCount)"
            else -> "1024px ECC affine"
        }

        return AlignmentResult(
            transforms = transforms,
            alignedCount = alignedCount,
            eccSuccessCount = eccSuccessCount,
            orbFallbackCount = orbFallbackCount,
            alignmentNote = alignmentNote
        )
    }

    private fun tryEccAffineOnProxy(
        srcGray: Mat,
        refGray: Mat,
        scaleX: Double,
        scaleY: Double,
        fullWidth: Int,
        fullHeight: Int
    ): FrameTransform.Affine? {
        val refBlur = Mat()
        val srcBlur = Mat()
        val ref32 = Mat()
        val src32 = Mat()
        val warpInvProxy = Mat.eye(2, 3, CvType.CV_32F)
        val warpInvFull = Mat(2, 3, CvType.CV_64F)
        val forwardAffineFull = Mat()

        return try {
            Imgproc.GaussianBlur(refGray, refBlur, Size(3.0, 3.0), 0.0)
            Imgproc.GaussianBlur(srcGray, srcBlur, Size(3.0, 3.0), 0.0)
            refBlur.convertTo(ref32, CvType.CV_32F, 1.0 / 255.0)
            srcBlur.convertTo(src32, CvType.CV_32F, 1.0 / 255.0)

            val criteria = TermCriteria(TermCriteria.COUNT or TermCriteria.EPS, 80, 1e-4)
            Video.findTransformECC(ref32, src32, warpInvProxy, Video.MOTION_AFFINE, criteria)

            val a = warpInvProxy.get(0, 0)[0]
            val b = warpInvProxy.get(0, 1)[0] * (scaleY / scaleX)
            val tx = warpInvProxy.get(0, 2)[0] / scaleX
            val c = warpInvProxy.get(1, 0)[0] * (scaleX / scaleY)
            val d = warpInvProxy.get(1, 1)[0]
            val ty = warpInvProxy.get(1, 2)[0] / scaleY

            warpInvFull.put(0, 0, a, b, tx, c, d, ty)
            Imgproc.invertAffineTransform(warpInvFull, forwardAffineFull)

            val m = DoubleArray(6)
            forwardAffineFull.get(0, 0, m)
            if (!isValidAffine(m, fullWidth, fullHeight)) {
                null
            } else {
                FrameTransform.Affine(m)
            }
        } catch (_: Exception) {
            null
        } finally {
            refBlur.release()
            srcBlur.release()
            ref32.release()
            src32.release()
            warpInvProxy.release()
            warpInvFull.release()
            forwardAffineFull.release()
        }
    }

    private fun tryOrbHomographyOnProxy(
        srcGray: Mat,
        refKeypoints: MatOfKeyPoint?,
        refDescriptors: Mat?,
        scaleX: Double,
        scaleY: Double,
        fullWidth: Int,
        fullHeight: Int
    ): FrameTransform.Homography? {
        if (refKeypoints == null || refDescriptors == null || refDescriptors.empty()) return null
        val srcKeypoints = MatOfKeyPoint()
        val srcDescriptors = Mat()

        return try {
            ORB.create(2000).detectAndCompute(srcGray, Mat(), srcKeypoints, srcDescriptors)
            if (srcDescriptors.empty() || srcDescriptors.rows() < 12) return null

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

            if (goodMatches.size < 12) return null

            val srcKpArray = srcKeypoints.toArray()
            val refKpArray = refKeypoints.toArray()
            val srcPtsList = ArrayList<Point>(goodMatches.size)
            val dstPtsList = ArrayList<Point>(goodMatches.size)
            for (m in goodMatches) {
                srcPtsList.add(srcKpArray[m.queryIdx].pt)
                dstPtsList.add(refKpArray[m.trainIdx].pt)
            }

            val srcPts = MatOfPoint2f(*srcPtsList.toTypedArray())
            val dstPts = MatOfPoint2f(*dstPtsList.toTypedArray())
            val homographyProxy = Calib3d.findHomography(srcPts, dstPts, Calib3d.RANSAC, 4.0)
            srcPts.release()
            dstPts.release()

            if (homographyProxy.empty()) {
                homographyProxy.release()
                return null
            }

            val hp = DoubleArray(9)
            homographyProxy.get(0, 0, hp)
            homographyProxy.release()

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

            if (!isValidHomography(hFull, fullWidth, fullHeight)) {
                null
            } else {
                FrameTransform.Homography(hFull)
            }
        } catch (_: Exception) {
            null
        } finally {
            srcKeypoints.release()
            srcDescriptors.release()
        }
    }

    private fun isValidAffine(m: DoubleArray, width: Int, height: Int): Boolean {
        if (m.size < 6 || m.any { !it.isFinite() }) return false
        val det = m[0] * m[4] - m[1] * m[3]
        if (det < 0.40 || det > 2.40) return false
        if (abs(m[2]) > width * 0.35 || abs(m[5]) > height * 0.35) return false
        return true
    }

    private fun isValidHomography(h: DoubleArray, width: Int, height: Int): Boolean {
        if (h.size < 9 || h.any { !it.isFinite() }) return false
        val det = h[0] * h[4] - h[1] * h[3]
        if (det < 0.35 || det > 2.8) return false
        if (abs(h[2]) > width * 0.35 || abs(h[5]) > height * 0.35) return false
        return true
    }

    fun computeValidOverlapRect(
        transforms: Array<FrameTransform>,
        width: Int,
        height: Int
    ): Rect {
        var minX = 0.0
        var minY = 0.0
        var maxX = width.toDouble()
        var maxY = height.toDouble()
        var hadWarp = false

        val w = width.toDouble()
        val h = height.toDouble()

        for (t in transforms) {
            if (t is FrameTransform.Identity) continue
            hadWarp = true
            val c00 = transformPoint(t, 0.0, 0.0)
            val c10 = transformPoint(t, w, 0.0)
            val c11 = transformPoint(t, w, h)
            val c01 = transformPoint(t, 0.0, h)

            val frameLeft = max(c00.first, c01.first)
            val frameRight = min(c10.first, c11.first)
            val frameTop = max(c00.second, c10.second)
            val frameBottom = min(c01.second, c11.second)

            minX = max(minX, frameLeft)
            maxX = min(maxX, frameRight)
            minY = max(minY, frameTop)
            maxY = min(maxY, frameBottom)
        }

        if (!hadWarp) return Rect(0, 0, width, height)

        val insetLeft = if (minX > 0.5) ceil(minX).toInt() + 2 else 0
        val insetTop = if (minY > 0.5) ceil(minY).toInt() + 2 else 0
        val insetRight = if (maxX < w - 0.5) floor(maxX).toInt() - 2 else width
        val insetBottom = if (maxY < h - 0.5) floor(maxY).toInt() - 2 else height

        val x = insetLeft.coerceIn(0, width - 1)
        val y = insetTop.coerceIn(0, height - 1)
        val cropW = (insetRight - x).coerceAtLeast(0)
        val cropH = (insetBottom - y).coerceAtLeast(0)

        if (cropW < (width * 0.45).toInt() || cropH < (height * 0.45).toInt() || cropW < 64 || cropH < 64) {
            return Rect(0, 0, width, height)
        }
        return Rect(x, y, cropW, cropH)
    }

    private fun transformPoint(t: FrameTransform, x: Double, y: Double): Pair<Double, Double> {
        return when (t) {
            FrameTransform.Identity -> x to y
            is FrameTransform.Affine -> {
                val m = t.matrix2x3
                (m[0] * x + m[1] * y + m[2]) to (m[3] * x + m[4] * y + m[5])
            }
            is FrameTransform.Homography -> {
                val h = t.matrix3x3
                val denom = h[6] * x + h[7] * y + h[8]
                if (abs(denom) < 1e-9) x to y
                else ((h[0] * x + h[1] * y + h[2]) / denom) to ((h[3] * x + h[4] * y + h[5]) / denom)
            }
        }
    }

    fun warpFullFrame(
        srcBgr: Mat,
        transform: FrameTransform,
        targetWidth: Int,
        targetHeight: Int
    ): Mat {
        return when (transform) {
            FrameTransform.Identity -> srcBgr
            is FrameTransform.Affine -> {
                val mat = Mat(2, 3, CvType.CV_64F)
                mat.put(0, 0, *transform.matrix2x3)
                val warped = Mat()
                Imgproc.warpAffine(
                    srcBgr,
                    warped,
                    mat,
                    Size(targetWidth.toDouble(), targetHeight.toDouble()),
                    Imgproc.INTER_LINEAR,
                    Core.BORDER_REPLICATE
                )
                mat.release()
                warped
            }
            is FrameTransform.Homography -> {
                val mat = Mat(3, 3, CvType.CV_64F)
                mat.put(0, 0, *transform.matrix3x3)
                val warped = Mat()
                Imgproc.warpPerspective(
                    srcBgr,
                    warped,
                    mat,
                    Size(targetWidth.toDouble(), targetHeight.toDouble()),
                    Imgproc.INTER_LINEAR,
                    Core.BORDER_REPLICATE
                )
                mat.release()
                warped
            }
        }
    }

    fun saveMatAsJpeg(bgrMat: Mat, file: File, quality: Int = 95) {
        val params = MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, quality)
        try {
            val ok = Imgcodecs.imwrite(file.absolutePath, bgrMat, params)
            if (!ok) {
                val rgba = Mat()
                Imgproc.cvtColor(bgrMat, rgba, Imgproc.COLOR_BGR2RGBA)
                val bmp = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(rgba, bmp)
                rgba.release()
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                bmp.recycle()
            }
        } finally {
            params.release()
        }
    }

    fun createPreviewFromBgrMat(bgrMat: Mat, maxEdgePx: Int = PREVIEW_MAX_EDGE_PX): Bitmap {
        val w = bgrMat.cols()
        val h = bgrMat.rows()
        val maxDim = max(w, h)
        val resizedBgr = if (maxDim > maxEdgePx) {
            val scale = maxEdgePx.toDouble() / maxDim.toDouble()
            val dst = Mat()
            Imgproc.resize(
                bgrMat,
                dst,
                Size((w * scale).coerceAtLeast(1.0), (h * scale).coerceAtLeast(1.0)),
                0.0,
                0.0,
                Imgproc.INTER_AREA
            )
            dst
        } else {
            bgrMat
        }

        val rgba = Mat()
        return try {
            Imgproc.cvtColor(resizedBgr, rgba, Imgproc.COLOR_BGR2RGBA)
            val bmp = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgba, bmp)
            bmp
        } finally {
            rgba.release()
            if (resizedBgr !== bgrMat) resizedBgr.release()
        }
    }

    fun buildLaplacianPyramidFromBgr(bgrMat: Mat, levels: Int): List<Mat> {
        val floatBgr = Mat()
        bgrMat.convertTo(floatBgr, CvType.CV_32FC3, 1.0 / 255.0)
        val imgGauss = buildGaussianPyramid(floatBgr, levels)
        floatBgr.release()
        val imgLap = buildLaplacianPyramid(imgGauss)
        imgGauss.forEach { it.release() }
        return imgLap
    }

    fun buildGaussianPyramid(base: Mat, levels: Int): List<Mat> {
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

    fun buildLaplacianPyramid(gaussianPyramid: List<Mat>): List<Mat> {
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

    fun reconstructLaplacianPyramid(laplacianPyramid: List<Mat>): Mat {
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

    fun computePyramidLevels(width: Int, height: Int): Int {
        var minDim = min(width, height)
        var levels = 1
        while (minDim >= 32 && levels < 6) {
            minDim /= 2
            levels++
        }
        return levels.coerceAtLeast(3)
    }

    fun bitmapToBgrMat(bitmap: Bitmap): Mat {
        val rgba = Mat()
        val bgr = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
        rgba.release()
        return bgr
    }

    fun floatBgrMatToBitmap(floatBgr: Mat): Bitmap {
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
}
