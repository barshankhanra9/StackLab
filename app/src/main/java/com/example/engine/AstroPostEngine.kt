package com.example.engine

import android.graphics.Bitmap
import com.example.domain.model.AstroStretchAdjustments
import kotlin.math.asinh
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Post-stack Astrophotography processing engine:
 * 1. Light Pollution / Gradient Neutralizer via morphological opening skyglow modeling
 * 2. Asinh / Histogram Stretch preserving bright star cores and RGB color ratios
 * 3. Shadow-weighted CIE LAB Chroma Denoise keeping stars pinpoint sharp
 */
object AstroPostEngine {

    private const val PREVIEW_MAX_EDGE_PX = 1440

    fun createFastPreviewBase(source: Bitmap, maxEdgePx: Int = PREVIEW_MAX_EDGE_PX): Bitmap {
        return HdrToneEngine.createFastPreviewBase(source, maxEdgePx)
    }

    fun applyPostProcessing(
        source: Bitmap,
        adjustments: AstroStretchAdjustments
    ): Bitmap {
        if (adjustments.isIdentity()) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }

        OpenCvAlignmentPipeline.ensureOpenCvInitialized()

        val lpStrength = adjustments.lightPollutionRemoval.coerceIn(0f, 1f)
        val stretchAmount = adjustments.asinhStretch.coerceIn(0f, 1f)
        val chromaDenoise = adjustments.chromaDenoise.coerceIn(0f, 1f)

        val rgba = Mat()
        val bgr = Mat()
        var floatBgr = Mat()
        val outU8 = Mat()
        val outRgba = Mat()

        try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            rgba.release()

            bgr.convertTo(floatBgr, CvType.CV_32FC3, 1.0 / 255.0)
            bgr.release()

            if (lpStrength > 0.005f) {
                val neutralized = removeLightPollutionGradient(floatBgr, lpStrength)
                floatBgr.release()
                floatBgr = neutralized
            }

            if (stretchAmount > 0.005f) {
                val stretched = applyAsinhLuminanceStretch(floatBgr, stretchAmount)
                floatBgr.release()
                floatBgr = stretched
            }

            Core.min(floatBgr, Scalar(1.0, 1.0, 1.0), floatBgr)
            Core.max(floatBgr, Scalar(0.0, 0.0, 0.0), floatBgr)
            floatBgr.convertTo(outU8, CvType.CV_8UC3, 255.0)
            floatBgr.release()

            if (chromaDenoise > 0.005f) {
                applyShadowChromaDenoise(outU8, chromaDenoise)
            }

            Imgproc.cvtColor(outU8, outRgba, Imgproc.COLOR_BGR2RGBA)
            val result = Bitmap.createBitmap(
                outRgba.cols(),
                outRgba.rows(),
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(outRgba, result)
            return result
        } finally {
            rgba.release()
            bgr.release()
            floatBgr.release()
            outU8.release()
            outRgba.release()
        }
    }

    /**
     * Estimates low-frequency skyglow gradient using downsampled morphological opening
     * (suppressing stars and small structures) followed by broad Gaussian smoothing,
     * then subtracts excess gradient and neutralizes orange/green light pollution cast.
     */
    private fun removeLightPollutionGradient(floatBgr: Mat, strength: Float): Mat {
        val rows = floatBgr.rows()
        val cols = floatBgr.cols()
        val maxDim = max(rows, cols).coerceAtLeast(1)
        val proxyScale = (192.0 / maxDim.toDouble()).coerceAtMost(1.0)
        val pW = (cols * proxyScale).roundToInt().coerceAtLeast(16)
        val pH = (rows * proxyScale).roundToInt().coerceAtLeast(16)

        val small = Mat()
        val opened = Mat()
        val smoothSmall = Mat()
        val bgFull = Mat()
        val diff = Mat()
        val out = Mat()
        val kernelSize = (min(pW, pH) * 0.09).roundToInt().coerceIn(7, 23).let {
            if (it % 2 == 0) it + 1 else it
        }
        val morphKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE,
            Size(kernelSize.toDouble(), kernelSize.toDouble())
        )

        return try {
            Imgproc.resize(floatBgr, small, Size(pW.toDouble(), pH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
            Imgproc.morphologyEx(small, opened, Imgproc.MORPH_OPEN, morphKernel)
            val blurK = (kernelSize * 1.4).roundToInt().coerceAtLeast(9).let {
                if (it % 2 == 0) it + 1 else it
            }
            Imgproc.GaussianBlur(
                opened,
                smoothSmall,
                Size(blurK.toDouble(), blurK.toDouble()),
                0.0,
                0.0,
                Core.BORDER_REFLECT_101
            )

            // Find dark sky anchor across the smoothed background model (8th percentile)
            val bgPixels = FloatArray(pW * pH * 3)
            smoothSmall.get(0, 0, bgPixels)
            val ch0 = FloatArray(pW * pH)
            val ch1 = FloatArray(pW * pH)
            val ch2 = FloatArray(pW * pH)
            for (i in 0 until pW * pH) {
                ch0[i] = bgPixels[i * 3]
                ch1[i] = bgPixels[i * 3 + 1]
                ch2[i] = bgPixels[i * 3 + 2]
            }
            ch0.sort()
            ch1.sort()
            ch2.sort()
            val pIdx = ((pW * pH) * 0.08f).roundToInt().coerceIn(0, (pW * pH) - 1)
            val darkFloor = min(ch0[pIdx], min(ch1[pIdx], ch2[pIdx])).coerceIn(0.01f, 0.14f)

            Imgproc.resize(smoothSmall, bgFull, Size(cols.toDouble(), rows.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)

            // Excess gradient above dark neutral sky floor
            Core.subtract(bgFull, Scalar(darkFloor.toDouble(), darkFloor.toDouble(), darkFloor.toDouble()), diff)
            Core.max(diff, Scalar(0.0, 0.0, 0.0), diff)

            // Clamp subtraction so bright foreground structures aren't over-darkened
            Core.multiply(diff, Scalar(strength.toDouble(), strength.toDouble(), strength.toDouble()), diff)
            Core.subtract(floatBgr, diff, out)
            Core.max(out, Scalar(0.0, 0.0, 0.0), out)
            out.clone()
        } finally {
            small.release()
            opened.release()
            smoothSmall.release()
            bgFull.release()
            diff.release()
            out.release()
            morphKernel.release()
        }
    }

    /**
     * Hyperbolic arcsine stretch on pixel luminance:
     * L_out = asinh(beta * L) / asinh(beta)
     * Scales RGB channels by (L_out / L) to lift faint Milky Way dust & nebulae
     * while preserving star colors and preventing bright star core blowout.
     */
    private fun applyAsinhLuminanceStretch(floatBgr: Mat, stretch: Float): Mat {
        val rows = floatBgr.rows()
        val cols = floatBgr.cols()
        val beta = (exp(stretch.toDouble() * 4.5) - 1.0).coerceAtLeast(0.01)
        val normDenom = asinh(beta)

        val out = Mat(rows, cols, CvType.CV_32FC3)
        val chunkRows = min(64, rows)
        val buf = FloatArray(chunkRows * cols * 3)

        var r = 0
        while (r < rows) {
            val count = min(chunkRows, rows - r)
            val srcSub = floatBgr.rowRange(r, r + count)
            srcSub.get(0, 0, buf)
            srcSub.release()

            val total = count * cols
            var idx = 0
            for (i in 0 until total) {
                val b = buf[idx].coerceAtLeast(0f)
                val g = buf[idx + 1].coerceAtLeast(0f)
                val red = buf[idx + 2].coerceAtLeast(0f)

                val lum = (0.0722f * b + 0.7152f * g + 0.2126f * red)
                if (lum > 1e-5f) {
                    val stretchedLum = (asinh(beta * lum.toDouble()) / normDenom).toFloat()
                    val gain = stretchedLum / lum
                    val outB = b * gain
                    val outG = g * gain
                    val outR = red * gain
                    val maxCh = max(outB, max(outG, outR))
                    if (maxCh > 1f) {
                        // Soft chromatic highlight roll-off to preserve star core color
                        val inv = 1f / maxCh
                        val blend = (stretch * 0.25f).coerceIn(0f, 0.25f)
                        buf[idx] = (outB * inv * (1f - blend) + outB.coerceAtMost(1f) * blend)
                        buf[idx + 1] = (outG * inv * (1f - blend) + outG.coerceAtMost(1f) * blend)
                        buf[idx + 2] = (outR * inv * (1f - blend) + outR.coerceAtMost(1f) * blend)
                    } else {
                        buf[idx] = outB
                        buf[idx + 1] = outG
                        buf[idx + 2] = outR
                    }
                }
                idx += 3
            }

            val dstSub = out.rowRange(r, r + count)
            dstSub.put(0, 0, buf)
            dstSub.release()
            r += count
        }

        return out
    }

    /**
     * Smooths chroma blotches in CIE LAB (a, b) channels weighted toward dark shadows
     * while keeping Luminance (L) 100% untouched so stars remain pinpoint sharp.
     */
    private fun applyShadowChromaDenoise(bgrU8: Mat, strength: Float) {
        val lab = Mat()
        val channels = ArrayList<Mat>(3)
        val aSmooth = Mat()
        val bSmooth = Mat()
        val lFloat = Mat()
        val shadowWeight = Mat()
        val invWeight = Mat()
        val aOrigF = Mat()
        val bOrigF = Mat()
        val aSmoothF = Mat()
        val bSmoothF = Mat()
        val aOutF = Mat()
        val bOutF = Mat()

        try {
            Imgproc.cvtColor(bgrU8, lab, Imgproc.COLOR_BGR2Lab)
            Core.split(lab, channels)
            val lCh = channels[0]
            val aCh = channels[1]
            val bCh = channels[2]

            val blurRadius = (5 + (strength * 14f).roundToInt()).let { if (it % 2 == 0) it + 1 else it }
            val sigma = 1.5 + strength * 5.0
            Imgproc.GaussianBlur(aCh, aSmooth, Size(blurRadius.toDouble(), blurRadius.toDouble()), sigma)
            Imgproc.GaussianBlur(bCh, bSmooth, Size(blurRadius.toDouble(), blurRadius.toDouble()), sigma)

            // Shadow mask: stronger chroma denoise where L is dark (< ~110/255), gentler on bright stars/nebulae
            lCh.convertTo(lFloat, CvType.CV_32FC1, 1.0 / 255.0)
            Core.multiply(lFloat, Scalar(-1.35), shadowWeight)
            Core.add(shadowWeight, Scalar(1.0), shadowWeight)
            Core.max(shadowWeight, Scalar(0.28), shadowWeight)
            Core.min(shadowWeight, Scalar(1.0), shadowWeight)
            Core.multiply(shadowWeight, Scalar(strength.toDouble()), shadowWeight)

            Core.multiply(shadowWeight, Scalar(-1.0), invWeight)
            Core.add(invWeight, Scalar(1.0), invWeight)

            aCh.convertTo(aOrigF, CvType.CV_32FC1)
            bCh.convertTo(bOrigF, CvType.CV_32FC1)
            aSmooth.convertTo(aSmoothF, CvType.CV_32FC1)
            bSmooth.convertTo(bSmoothF, CvType.CV_32FC1)

            val term1 = Mat()
            val term2 = Mat()
            try {
                Core.multiply(aOrigF, invWeight, term1)
                Core.multiply(aSmoothF, shadowWeight, term2)
                Core.add(term1, term2, aOutF)
                aOutF.convertTo(aCh, CvType.CV_8UC1)

                Core.multiply(bOrigF, invWeight, term1)
                Core.multiply(bSmoothF, shadowWeight, term2)
                Core.add(term1, term2, bOutF)
                bOutF.convertTo(bCh, CvType.CV_8UC1)
            } finally {
                term1.release()
                term2.release()
            }

            Core.merge(channels, lab)
            Imgproc.cvtColor(lab, bgrU8, Imgproc.COLOR_Lab2BGR)
        } finally {
            lab.release()
            channels.forEach { it.release() }
            aSmooth.release()
            bSmooth.release()
            lFloat.release()
            shadowWeight.release()
            invWeight.release()
            aOrigF.release()
            bOrigF.release()
            aSmoothF.release()
            bSmoothF.release()
            aOutF.release()
            bOutF.release()
        }
    }
}
