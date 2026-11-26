package com.example

import com.example.domain.model.AlignmentMode
import com.example.domain.model.AstroAlignmentMode
import com.example.domain.model.AstroConfig
import com.example.domain.model.AstroFrameType
import com.example.domain.model.AstroStackAlgorithm
import com.example.domain.model.AstroStretchAdjustments
import com.example.domain.model.BlendAlgorithm
import com.example.domain.model.FocusStackConfig
import com.example.domain.model.HdrConfig
import com.example.domain.model.HdrStylePreset
import com.example.domain.model.HdrToneAdjustments
import com.example.domain.model.MaxResolutionPreset
import com.example.domain.model.OutputFormat
import com.example.domain.model.OutputSettings
import com.example.domain.model.StackMode
import com.example.engine.AstroStackProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `stack modes expose active focus HDR and Astro modes`() {
        assertTrue(StackMode.FocusStack.isAvailable)
        assertTrue(StackMode.HdrExposureBlend.isAvailable)
        assertTrue(StackMode.AstroNoiseStack.isAvailable)
    }

    @Test
    fun `focus stack config defaults match darkroom pipeline`() {
        val config = FocusStackConfig()
        assertEquals(AlignmentMode.AUTO_HOMOGRAPHY, config.alignmentMode)
        assertEquals(BlendAlgorithm.PYRAMID_PMAX, config.blendAlgorithm)
        assertEquals(3, config.edgeSmoothingRadius)
        assertTrue(config.autoCropBorders)
        assertEquals(MaxResolutionPreset.UHD_4K, config.outputSettings.maxResolution)
        assertEquals(OutputFormat.JPEG, config.outputSettings.outputFormat)
        assertEquals(95, config.outputSettings.jpegQuality)
    }

    @Test
    fun `hdr config defaults use balanced Mertens weights and auto alignment`() {
        val config = HdrConfig()
        assertEquals(AlignmentMode.AUTO_HOMOGRAPHY, config.alignmentMode)
        assertEquals(1.0f, config.contrastWeight, 1e-4f)
        assertEquals(1.0f, config.saturationWeight, 1e-4f)
        assertEquals(1.0f, config.exposureWeight, 1e-4f)
        assertFalse(config.deghostingEnabled)
        assertNull(config.anchorFrameIndex)
        assertTrue(config.autoCropBorders)
        assertEquals(MaxResolutionPreset.UHD_4K, config.outputSettings.maxResolution)
    }

    @Test
    fun `astro config defaults and stretch adjustments behave as expected`() {
        val config = AstroConfig()
        assertEquals(AstroAlignmentMode.STAR_FIELD, config.alignmentMode)
        assertEquals(AstroStackAlgorithm.KAPPA_SIGMA, config.stackAlgorithm)
        assertEquals(2.5f, config.sigmaClippingThreshold, 1e-4f)
        assertFalse(config.freezeGroundEnabled)
        assertTrue(config.freezeGroundStrokes.isEmpty())
        assertTrue(config.autoCropBorders)
        assertEquals(AstroFrameType.LIGHT, AstroFrameType.entries.first())

        val neutral = AstroStretchAdjustments()
        assertTrue(neutral.isIdentity())
        assertEquals(0, neutral.activeCount())

        val active = AstroStretchAdjustments(
            asinhStretch = 0.65f,
            lightPollutionRemoval = 0.50f,
            chromaDenoise = 0f
        )
        assertFalse(active.isIdentity())
        assertEquals(2, active.activeCount())
    }

    @Test
    fun `kappa sigma clipping rejects satellite and hot pixel outliers`() {
        val samples = floatArrayOf(20f, 21f, 19f, 20f, 250f)
        val clippedMean = AstroStackProcessor.computeKappaSigmaClippedMean(
            values = samples,
            count = samples.size,
            kappa = 2.5f
        )
        assertEquals(20f, clippedMean, 0.2f)
    }

    @Test
    fun `astro byte strip stacking computes kappa sigma median mean and max`() {
        val buffers = arrayOf(
            byteArrayOf(10, 100),
            byteArrayOf(12, 100),
            byteArrayOf(11, 102),
            byteArrayOf(9, 98),
            byteArrayOf(240.toByte(), 100)
        )
        val out = ByteArray(2)
        val scratch = FloatArray(buffers.size)

        AstroStackProcessor.stackByteStrip(
            stripBuffers = buffers,
            outStrip = out,
            byteCount = 2,
            frameCount = buffers.size,
            algorithm = AstroStackAlgorithm.KAPPA_SIGMA,
            kappa = 2.5f,
            scratch = scratch
        )
        assertTrue((out[0].toInt() and 0xFF) in 10..11)
        assertEquals(100, out[1].toInt() and 0xFF)

        AstroStackProcessor.stackByteStrip(
            stripBuffers = buffers,
            outStrip = out,
            byteCount = 2,
            frameCount = buffers.size,
            algorithm = AstroStackAlgorithm.MEDIAN,
            kappa = 2.5f,
            scratch = scratch
        )
        assertEquals(11, out[0].toInt() and 0xFF)
        assertEquals(100, out[1].toInt() and 0xFF)

        AstroStackProcessor.stackByteStrip(
            stripBuffers = buffers,
            outStrip = out,
            byteCount = 2,
            frameCount = buffers.size,
            algorithm = AstroStackAlgorithm.MAXIMUM_LIGHTEN,
            kappa = 2.5f,
            scratch = scratch
        )
        assertEquals(240, out[0].toInt() and 0xFF)
        assertEquals(102, out[1].toInt() and 0xFF)

        AstroStackProcessor.stackByteStrip(
            stripBuffers = buffers,
            outStrip = out,
            byteCount = 2,
            frameCount = buffers.size,
            algorithm = AstroStackAlgorithm.MEAN,
            kappa = 2.5f,
            scratch = scratch
        )
        assertEquals(56, out[0].toInt() and 0xFF)
        assertEquals(100, out[1].toInt() and 0xFF)
    }

    @Test
    fun `hdr style presets expose all four profiles with distinct weights and tone defaults`() {
        assertEquals(4, HdrStylePreset.entries.size)
        assertEquals("Natural", HdrStylePreset.NATURAL.label)
        assertEquals("Landscape", HdrStylePreset.LANDSCAPE.label)
        assertEquals("Architecture / Interior", HdrStylePreset.ARCHITECTURE.label)
        assertEquals("Dramatic", HdrStylePreset.DRAMATIC.label)

        assertTrue(HdrStylePreset.DRAMATIC.contrastWeight > HdrStylePreset.NATURAL.contrastWeight)
        assertTrue(HdrStylePreset.LANDSCAPE.saturationWeight > HdrStylePreset.NATURAL.saturationWeight)
        assertTrue(
            HdrStylePreset.ARCHITECTURE.defaultTone.highlightRecovery >
                HdrStylePreset.NATURAL.defaultTone.highlightRecovery
        )
    }

    @Test
    fun `hdr tone adjustments track identity and active slider count`() {
        val neutral = HdrToneAdjustments()
        assertTrue(neutral.isIdentity())
        assertEquals(0, neutral.activeCount())

        val tuned = HdrToneAdjustments(
            highlightRecovery = 0.45f,
            shadowLift = 0f,
            clarity = 0.60f,
            vibrance = 0.25f
        )
        assertFalse(tuned.isIdentity())
        assertEquals(3, tuned.activeCount())
    }

    @Test
    fun `resolution presets define expected edge caps`() {
        assertNull(MaxResolutionPreset.ORIGINAL.maxEdgePx)
        assertEquals(4000, MaxResolutionPreset.UHD_4K.maxEdgePx)
        assertEquals(2048, MaxResolutionPreset.HD_2K.maxEdgePx)
        assertEquals(1600, MaxResolutionPreset.PROXY_1600.maxEdgePx)
    }

    @Test
    fun `output settings preserve format mime types`() {
        val pngSettings = OutputSettings(outputFormat = OutputFormat.PNG)
        assertEquals("image/png", pngSettings.outputFormat.mimeType)
        assertEquals("png", pngSettings.outputFormat.extension)
    }
}
