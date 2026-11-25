package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.AlignmentMode
import com.example.domain.model.BlendAlgorithm
import com.example.domain.model.StackFrame
import com.example.domain.model.StackMode
import com.example.ui.StackLabUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `canStartStacking requires at least two frames`() {
        val singleFrameState = StackLabUiState(
            frames = listOf(
                StackFrame("1", Uri.parse("content://media/1"), "f1.jpg", 1920, 1080)
            )
        )
        assertFalse(singleFrameState.canStartStacking)

        val twoFrameState = singleFrameState.copy(
            frames = singleFrameState.frames + StackFrame(
                "2",
                Uri.parse("content://media/2"),
                "f2.jpg",
                1920,
                1080
            ),
            alignmentMode = AlignmentMode.AUTO_HOMOGRAPHY,
            blendAlgorithm = BlendAlgorithm.PYRAMID_PMAX
        )
        assertTrue(twoFrameState.canStartStacking)
    }
}
