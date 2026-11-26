package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.AlignmentMode
import com.example.domain.model.AstroAlignmentMode
import com.example.domain.model.AstroFrameType
import com.example.domain.model.AstroStackAlgorithm
import com.example.domain.model.BlendAlgorithm
import com.example.domain.model.FocusRegion
import com.example.domain.model.RetouchPoint
import com.example.domain.model.RetouchStroke
import com.example.domain.model.StackFrame
import com.example.domain.model.StackMode
import com.example.engine.AstroStackProcessor
import com.example.engine.FrameBitmapLoader
import com.example.engine.RetouchAndPeakingUtils
import com.example.ui.StackLabUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
    fun `stack modes include active focus HDR and Astro modes`() {
        val state = StackLabUiState()
        assertEquals(3, state.availableModes.size)
        assertTrue(StackMode.FocusStack.isAvailable)
        assertTrue(StackMode.HdrExposureBlend.isAvailable)
        assertTrue(StackMode.AstroNoiseStack.isAvailable)
        assertEquals(1.0f, state.hdrContrastWeight, 1e-4f)
        assertEquals(1.0f, state.hdrSaturationWeight, 1e-4f)
        assertEquals(1.0f, state.hdrExposureWeight, 1e-4f)
        assertFalse(state.hdrDeghostingEnabled)
        assertEquals(AstroAlignmentMode.STAR_FIELD, state.astroAlignmentMode)
        assertEquals(AstroStackAlgorithm.KAPPA_SIGMA, state.astroStackAlgorithm)
        assertEquals(2.5f, state.astroSigmaThreshold, 1e-4f)
    }

    @Test
    fun `astro mode requires at least two enabled light frames regardless of dark frames`() {
        val oneLightTwoDark = StackLabUiState(
            activeMode = StackMode.AstroNoiseStack,
            frames = listOf(
                StackFrame("l1", Uri.parse("content://media/1"), "light1.jpg", 1920, 1080, astroFrameType = AstroFrameType.LIGHT),
                StackFrame("d1", Uri.parse("content://media/2"), "dark1.jpg", 1920, 1080, astroFrameType = AstroFrameType.DARK),
                StackFrame("d2", Uri.parse("content://media/3"), "dark2.jpg", 1920, 1080, astroFrameType = AstroFrameType.DARK)
            )
        )
        assertEquals(1, oneLightTwoDark.enabledLightFramesCount)
        assertEquals(2, oneLightTwoDark.enabledDarkFramesCount)
        assertFalse(oneLightTwoDark.canStartStacking)

        val twoLightsOneDark = oneLightTwoDark.copy(
            frames = oneLightTwoDark.frames + StackFrame(
                id = "l2",
                uri = Uri.parse("content://media/4"),
                displayName = "light2.jpg",
                width = 1920,
                height = 1080,
                astroFrameType = AstroFrameType.LIGHT
            )
        )
        assertEquals(2, twoLightsOneDark.enabledLightFramesCount)
        assertTrue(twoLightsOneDark.canStartStacking)

        val twoLightsPlusThird = twoLightsOneDark.copy(
            frames = twoLightsOneDark.frames + StackFrame(
                id = "l3",
                uri = Uri.parse("content://media/5"),
                displayName = "light3.jpg",
                width = 1920,
                height = 1080,
                astroFrameType = AstroFrameType.LIGHT
            )
        )
        assertEquals("l2", AstroStackProcessor.resolveReferenceLightFrameId(twoLightsPlusThird.frames))
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
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
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

    @Test
    fun `retouch cache clears retouch_cache directory and wake lock permission is declared`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = FrameBitmapLoader(context)

        val runDir = loader.getRetouchRunDir("run_test")
        val dummyFrame = File(runDir, "aligned_0.jpg").apply { writeText("test") }
        val legacyDir = File(context.cacheDir, "retouch_frames").apply { mkdirs() }
        File(legacyDir, "old.jpg").writeText("old")

        assertTrue(dummyFrame.exists())
        loader.clearRetouchCache()
        assertFalse(File(context.cacheDir, FrameBitmapLoader.RETOUCH_CACHE_DIR).exists())
        assertFalse(legacyDir.exists())

        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        assertTrue(pkgInfo.requestedPermissions?.contains(Manifest.permission.WAKE_LOCK) == true)
    }
}
