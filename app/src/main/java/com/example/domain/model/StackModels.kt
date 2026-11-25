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
        label = "Auto (Homography)",
        description = "Fixes focus breathing & hand shake via ORB + RANSAC"
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
    UHD_4K(3840, "4K UHD (3840px)", "4K UHD"),
    HD_2K(2048, "2K Studio (2048px)", "2K Studio"),
    PROXY_1600(1600, "Fast Proxy (1600px)", "1600px")
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
    val height: Int
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
    val frameNames: List<String>,
    val defaultComparisonIndex: Int,
    val alignedFramesCount: Int,
    val processingTimeMs: Long,
    val usedProxyMode: Boolean,
    val proxyReason: String?,
    val outputWidth: Int,
    val outputHeight: Int,
    val alignmentNote: String
)
