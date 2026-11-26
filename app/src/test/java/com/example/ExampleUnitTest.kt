package com.example

import com.example.domain.model.AlignmentMode
import com.example.domain.model.BlendAlgorithm
import com.example.domain.model.FocusStackConfig
import com.example.domain.model.HdrConfig
import com.example.domain.model.MaxResolutionPreset
import com.example.domain.model.OutputFormat
import com.example.domain.model.OutputSettings
import com.example.domain.model.StackMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `stack modes expose active focus and HDR modes and coming soon Astro mode`() {
        assertTrue(StackMode.FocusStack.isAvailable)
        assertTrue(StackMode.HdrExposureBlend.isAvailable)
        assertFalse(StackMode.AstroNoiseStack.isAvailable)
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
        assertTrue(config.autoCropBorders)
        assertEquals(MaxResolutionPreset.UHD_4K, config.outputSettings.maxResolution)
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
