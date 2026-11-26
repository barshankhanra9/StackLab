package com.example.engine

import android.graphics.Bitmap
import com.example.domain.model.HdrToneAdjustments
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Post-fusion Tone & Clarity Engine operating in CIE LAB color space on a merged HDR bitmap.
 * - Micro-Contrast / Clarity: OpenCV CLAHE on the Luminance (L) channel only.
 * - Highlight Recovery & Shadow Lift: Monotonic tonal curve on L preserving true black point.
 * - Vibrance: Chroma-adaptive boost on (a, b) channels with skin-tone and bright-sky protection.
 */
object HdrToneEngine {

    private const val PREVIEW_MAX_EDGE_PX = 1440

    fun createFastPreviewBase(source: Bitmap, maxEdgePx: Int = PREVIEW_MAX_EDGE_PX): Bitmap {
        val w = source.width
        val h = source.height
        val maxDim = max(w, h)
        if (maxDim <= maxEdgePx) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }
        val scale = maxEdgePx.toFloat() / maxDim.toFloat()
        val dstW = (w * scale).roundToInt().coerceAtLeast(1)
        val dstH = (h * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, dstW, dstH, true)
    }

    fun applyToneAndClarity(
        source: Bitmap,
        adjustments: HdrToneAdjustments
    ): Bitmap {
        if (adjustments.isIdentity()) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }

        OpenCvAlignmentPipeline.ensureOpenCvInitialized()

        val highlight = adjustments.highlightRecovery.coerceIn(0f, 1f)
        val shadow = adjustments.shadowLift.coerceIn(0f, 1f)
        val clarity = adjustments.clarity.coerceIn(0f, 1f)
        val vibrance = adjustments.vibrance.coerceIn(0f, 1f)

        val rgba = Mat()
        val bgr = Mat()
        val lab = Mat()
        val outBgr = Mat()
        val outRgba = Mat()
        val labChannels = ArrayList<Mat>(3)

        try {
            Utils.bitmapToMat(source, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            rgba.release()

            Imgproc.cvtColor(bgr, lab, Imgproc.COLOR_BGR2Lab)
            bgr.release()

            Core.split(lab, labChannels)
            val lChannel = labChannels[0]
            val aChannel = labChannels[1]
            val bChannel = labChannels[2]

            // 1. Micro-Contrast / Clarity via OpenCV CLAHE on Luminance channel only
            if (clarity > 0.005f) {
                val claheL = Mat()
                val blendedL = Mat()
                try {
                    val clipLimit = 1.2 + clarity * 2.8
                    val clahe = Imgproc.createCLAHE(clipLimit, Size(8.0, 8.0))
                    clahe.apply(lChannel, claheL)
                    val alpha = (0.85 * clarity).coerceIn(0.0, 0.90)
                    Core.addWeighted(lChannel, 1.0 - alpha, claheL, alpha, 0.0, blendedL)
                    blendedL.copyTo(lChannel)
                } finally {
                    claheL.release()
                    blendedL.release()
                }
            }

            // 2. Highlight Recovery & Shadow Lift via monotonic 256-entry LUT on Luminance (L)
            if (highlight > 0.005f || shadow > 0.005f) {
                val lutBytes = buildLuminanceToneLut(highlight, shadow)
                val lutMat = Mat(1, 256, CvType.CV_8UC1)
                val tonedL = Mat()
                try {
                    lutMat.put(0, 0, lutBytes)
                    Core.LUT(lChannel, lutMat, tonedL)
                    tonedL.copyTo(lChannel)
                } finally {
                    lutMat.release()
                    tonedL.release()
                }
            }

            // 3. Vibrance on (a, b) channels with muted-color preference + skin & sky protection
            if (vibrance > 0.005f) {
                applyLabVibrance(
                    aChannel = aChannel,
                    bChannel = bChannel,
                    vibrance = vibrance
                )
            }

            Core.merge(labChannels, lab)
            Imgproc.cvtColor(lab, outBgr, Imgproc.COLOR_Lab2BGR)
            Imgproc.cvtColor(outBgr, outRgba, Imgproc.COLOR_BGR2RGBA)

            val outBitmap = Bitmap.createBitmap(
                outRgba.cols(),
                outRgba.rows(),
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(outRgba, outBitmap)
            return outBitmap
        } finally {
            rgba.release()
            bgr.release()
            lab.release()
            outBgr.release()
            outRgba.release()
            labChannels.forEach { it.release() }
        }
    }

    private fun buildLuminanceToneLut(highlightRecovery: Float, shadowLift: Float): ByteArray {
        val lut = ByteArray(256)
        for (i in 0 until 256) {
            var l = i / 255.0

            // Shadow Lift: lifts dark areas while anchoring black point (l = 0 stays 0)
            if (shadowLift > 0.005f) {
                val ts = (l / 0.60).coerceIn(0.0, 1.0)
                val shadowCurve = l * (1.0 - ts).pow(2.0)
                l += shadowLift * 1.45 * shadowCurve
            }

            // Highlight Recovery: smooth monotonic shoulder compression above l = 0.48
            if (highlightRecovery > 0.005f) {
                val th = ((l - 0.48) / 0.52).coerceIn(0.0, 1.0)
                val wh = th * th * (3.0 - 2.0 * th)
                l -= highlightRecovery * 0.28 * wh
            }

            lut[i] = (l.coerceIn(0.0, 1.0) * 255.0).roundToInt().coerceIn(0, 255).toByte()
        }
        return lut
    }

    private fun applyLabVibrance(
        aChannel: Mat,
        bChannel: Mat,
        vibrance: Float
    ) {
        val aF = Mat()
        val bF = Mat()
        val chroma = Mat()
        val chromaNorm = Mat()
        val oneMinusChroma = Mat()
        val falloff = Mat()
        val skinDot = Mat()
        val safeChroma = Mat()
        val skinCos = Mat()
        val skinDampen = Mat()
        val skyBlue = Mat()
        val skyDampen = Mat()
        val extraGain = Mat()
        val totalGain = Mat()

        try {
            aChannel.convertTo(aF, CvType.CV_32FC1)
            bChannel.convertTo(bF, CvType.CV_32FC1)
            Core.subtract(aF, Scalar(128.0), aF)
            Core.subtract(bF, Scalar(128.0), bF)

            // Chroma magnitude and muted-color falloff (1 - chroma)^1.35
            Core.magnitude(aF, bF, chroma)
            Core.multiply(chroma, Scalar(1.0 / 95.0), chromaNorm)
            Core.min(chromaNorm, Scalar(1.0), chromaNorm)
            Core.max(chromaNorm, Scalar(0.0), chromaNorm)

            Core.multiply(chromaNorm, Scalar(-1.0), oneMinusChroma)
            Core.add(oneMinusChroma, Scalar(1.0), oneMinusChroma)
            Core.pow(oneMinusChroma, 1.35, falloff)

            // Skin-tone protection along warm red-yellow LAB vector (~0.55a + 0.83b)
            Core.addWeighted(aF, 0.55, bF, 0.83, 0.0, skinDot)
            Core.max(chroma, Scalar(1.0), safeChroma)
            Core.divide(skinDot, safeChroma, skinCos)
            // skinMask = clamp((skinCos - 0.55) / 0.45, 0, 1)
            Core.subtract(skinCos, Scalar(0.55), skinCos)
            Core.multiply(skinCos, Scalar(1.0 / 0.45), skinCos)
            Core.min(skinCos, Scalar(1.0), skinCos)
            Core.max(skinCos, Scalar(0.0), skinCos)
            // skinDampen = 1.0 - 0.65 * skinMask
            Core.multiply(skinCos, Scalar(-0.65), skinDampen)
            Core.add(skinDampen, Scalar(1.0), skinDampen)

            // Sky protection where b* is noticeably negative (blue)
            Core.multiply(bF, Scalar(-1.0), skyBlue)
            Core.subtract(skyBlue, Scalar(8.0), skyBlue)
            Core.multiply(skyBlue, Scalar(1.0 / 32.0), skyBlue)
            Core.min(skyBlue, Scalar(1.0), skyBlue)
            Core.max(skyBlue, Scalar(0.0), skyBlue)
            // skyDampen = 1.0 - 0.45 * skyBlue
            Core.multiply(skyBlue, Scalar(-0.45), skyDampen)
            Core.add(skyDampen, Scalar(1.0), skyDampen)

            // extraGain = vibrance * 0.90 * falloff * skinDampen * skyDampen
            Core.multiply(falloff, skinDampen, extraGain)
            Core.multiply(extraGain, skyDampen, extraGain)
            Core.multiply(extraGain, Scalar(vibrance * 0.90), extraGain)

            Core.add(extraGain, Scalar(1.0), totalGain)

            Core.multiply(aF, totalGain, aF)
            Core.multiply(bF, totalGain, bF)
            Core.add(aF, Scalar(128.0), aF)
            Core.add(bF, Scalar(128.0), bF)

            Core.min(aF, Scalar(255.0), aF)
            Core.max(aF, Scalar(0.0), aF)
            Core.min(bF, Scalar(255.0), bF)
            Core.max(bF, Scalar(0.0), bF)

            aF.convertTo(aChannel, CvType.CV_8UC1)
            bF.convertTo(bChannel, CvType.CV_8UC1)
        } finally {
            aF.release()
            bF.release()
            chroma.release()
            chromaNorm.release()
            oneMinusChroma.release()
            falloff.release()
            skinDot.release()
            safeChroma.release()
            skinCos.release()
            skinDampen.release()
            skyBlue.release()
            skyDampen.release()
            extraGain.release()
            totalGain.release()
        }
    }
}
