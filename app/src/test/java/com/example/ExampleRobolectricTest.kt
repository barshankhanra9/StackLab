package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.AlignmentMode
import com.example.domain.model.BlendAlgorithm
import com.example.domain.model.FocusRegion
import com.example.domain.model.RetouchPoint
import com.example.domain.model.RetouchStroke
import com.example.domain.model.StackFrame
import com.example.domain.model.StackMode
import com.example.engine.RetouchAndPeakingUtils
import com.example.ui.StackLabUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches StackLab`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("StackLab", appName)
    }

    @Test
    fun `stack modes include active focus stack and coming soon HDR and Astro modes`() {
        val state = StackLabUiState()
        assertEquals(3, state.availableModes.size)
        assertTrue(StackMode.FocusStack.isAvailable)
        assertFalse(StackMode.HdrExposureBlend.isAvailable)
        assertFalse(StackMode.AstroNoiseStack.isAvailable)
    }

    @Test
    fun `canStartStacking requires at least two enabled frames`() {
        val singleFrameState = StackLabUiState(
            frames = listOf(
                StackFrame("1", Uri.parse("content://media/1"), "f1.jpg", 1920, 1080)
            )
        )
        assertFalse(singleFrameState.canStartStacking)

        val twoFrameOneDisabled = singleFrameState.copy(
            frames = singleFrameState.frames + StackFrame(
                id = "2",
                uri = Uri.parse("content://media/2"),
                displayName = "f2.jpg",
                width = 1920,
                height = 1080,
                isEnabled = false,
                focusRegion = FocusRegion.FAR
            ),
            alignmentMode = AlignmentMode.AUTO_HOMOGRAPHY,
            blendAlgorithm = BlendAlgorithm.PYRAMID_PMAX
        )
        assertFalse(twoFrameOneDisabled.canStartStacking)

        val twoEnabledFrames = twoFrameOneDisabled.copy(
            frames = twoFrameOneDisabled.frames.map { it.copy(isEnabled = true) }
        )
        assertTrue(twoEnabledFrames.canStartStacking)
        assertTrue(twoEnabledFrames.autoCropBorders)
    }

    @Test
    fun `applyRetouchStroke blends source pixels onto canvas and renders mask`() {
        val canvasBmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK)
        }
        val sourceBmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }

        val stroke = RetouchStroke(
            sourceFrameIndex = 0,
            points = listOf(RetouchPoint(0.5f, 0.5f)),
            brushSizeFraction = 0.25f,
            feather = 0f,
            opacity = 1f
        )

        RetouchAndPeakingUtils.applyRetouchStroke(canvasBmp, sourceBmp, stroke)
        assertNotEquals(Color.BLACK, canvasBmp.getPixel(50, 50))
        assertEquals(Color.BLACK, canvasBmp.getPixel(5, 5))

        val maskBmp = RetouchAndPeakingUtils.renderRetouchMaskOverlay(100, 100, listOf(stroke))
        assertTrue(Color.alpha(maskBmp.getPixel(50, 50)) > 0)
        assertEquals(0, Color.alpha(maskBmp.getPixel(5, 5)))
    }
}
