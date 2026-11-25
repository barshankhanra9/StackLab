package com.example.domain.model

import android.graphics.Bitmap
import android.net.Uri

sealed class StackMode(
    val id: String,
    val title: String,
    val subtitle: String,
    val isAvailable: Boolean
) {
    data object FocusStack : StackMode(
        id = "focus_stack",
        title = "Focus Stack",
        subtitle = "Merge macro & landscape shots for edge-to-edge sharpness",
        isAvailable = true
    )

    data object HdrExposureBlend : StackMode(
        id = "hdr_blend",
        title = "HDR Exposure Blend",
        subtitle = "Fuse bracketed exposures with natural highlight roll-off",
        isAvailable = false
    )

    data object AstroNoiseStack : StackMode(
        id = "astro_stack",
        title = "Astro / Noise Stack",
        subtitle = "Align starfields & median-stack to suppress sensor noise",
        isAvailable = false
    )
}

enum class AlignmentMode(val label: String, val description: String) {
    AUTO_HOMOGRAPHY(
        label = "Auto (ECC + ORB)",
        description = "1024px ECC affine alignment with ORB + RANSAC homography fallback"
    ),
    NONE(
        label = "None (Tripod)",
        description = "Locked camera — skips geometric warping"
    )
}

enum class BlendAlgorithm(val label: String, val description: String) {
    PYRAMID_PMAX(
        label = "Pyramid (PMax)",
        description = "Maximum local contrast — best for fine textures & hair"
    ),
    SMOOTH_DMAP(
        label = "Smooth (DMap)",
        description = "Continuous depth weights — minimizes edge halos"
    )
}

enum class OutputFormat(val label: String, val mimeType: String, val extension: String) {
    JPEG("JPEG", "image/jpeg", "jpg"),
    PNG("PNG (Lossless)", "image/png", "png")
}

enum class MaxResolutionPreset(val maxEdgePx: Int?, val label: String, val shortLabel: String) {
    ORIGINAL(null, "Original (Full Res)", "Full Res"),
    UHD_4K(4000, "High (4000px)", "4000px"),
    HD_2K(2048, "2K Studio (2048px)", "2K Studio"),
    PROXY_1600(1600, "Preview (1600px)", "1600px")
}

enum class FocusRegion(val label: String) {
    NEAR("Near"),
    MID("Mid"),
    FAR("Far"),
    ANALYZING("...")
}

data class OutputSettings(
    val maxResolution: MaxResolutionPreset = MaxResolutionPreset.UHD_4K,
    val outputFormat: OutputFormat = OutputFormat.JPEG,
    val jpegQuality: Int = 95
)

data class FocusStackConfig(
    val alignmentMode: AlignmentMode = AlignmentMode.AUTO_HOMOGRAPHY,
    val blendAlgorithm: BlendAlgorithm = BlendAlgorithm.PYRAMID_PMAX,
    val edgeSmoothingRadius: Int = 3,
    val autoCropBorders: Boolean = true,
    val outputSettings: OutputSettings = OutputSettings()
)

data class HdrConfig(
    val toneMappingStrength: Float = 0.6f
)

data class AstroConfig(
    val sigmaClippingThreshold: Float = 2.5f
)

data class StackFrame(
    val id: String,
    val uri: Uri,
    val displayName: String,
    val width: Int,
    val height: Int,
    val isEnabled: Boolean = true,
    val focusRegion: FocusRegion = FocusRegion.ANALYZING,
    val focusDistanceScore: Float = 0.5f,
    val peakSharpnessScore: Float = 0f,
    val isAnalyzed: Boolean = false
)

data class StackProgress(
    val currentStep: Int,
    val totalSteps: Int,
    val fraction: Float,
    val message: String
)

data class StackResult(
    val outputBitmap: Bitmap,
    val sourcePreviewBitmaps: List<Bitmap>,
    val alignedSourceCachePaths: List<String>,
    val referenceFrameUri: Uri?,
    val frameNames: List<String>,
    val defaultComparisonIndex: Int,
    val alignedFramesCount: Int,
    val processingTimeMs: Long,
    val usedProxyMode: Boolean,
    val proxyReason: String?,
    val outputWidth: Int,
    val outputHeight: Int,
    val alignmentNote: String,
    val wasAutoCropped: Boolean = false,
    val resolutionPreset: MaxResolutionPreset = MaxResolutionPreset.UHD_4K
)

data class RetouchPoint(
    val u: Float,
    val v: Float
)

data class RetouchStroke(
    val sourceFrameIndex: Int,
    val points: List<RetouchPoint>,
    val brushSizeFraction: Float,
    val feather: Float,
    val opacity: Float
)
