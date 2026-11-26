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
        isAvailable = true
    )

    data object AstroNoiseStack : StackMode(
        id = "astro_stack",
        title = "Astro / Noise Stack",
        subtitle = "Align starfields, subtract darks & sigma-stack for clean night skies",
        isAvailable = true
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

enum class ExposureBracket(val label: String) {
    DARK("Dark"),
    MID("Mid"),
    BRIGHT("Bright"),
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

data class HdrToneAdjustments(
    val highlightRecovery: Float = 0f,
    val shadowLift: Float = 0f,
    val clarity: Float = 0f,
    val vibrance: Float = 0f
) {
    fun isIdentity(): Boolean =
        highlightRecovery <= 0.005f &&
            shadowLift <= 0.005f &&
            clarity <= 0.005f &&
            vibrance <= 0.005f

    fun activeCount(): Int = listOf(
        highlightRecovery,
        shadowLift,
        clarity,
        vibrance
    ).count { it > 0.005f }
}

enum class HdrStylePreset(
    val label: String,
    val description: String,
    val contrastWeight: Float,
    val saturationWeight: Float,
    val exposureWeight: Float,
    val defaultTone: HdrToneAdjustments
) {
    NATURAL(
        label = "Natural",
        description = "Balanced weights & subtle micro-contrast",
        contrastWeight = 1.00f,
        saturationWeight = 1.00f,
        exposureWeight = 1.00f,
        defaultTone = HdrToneAdjustments(
            highlightRecovery = 0.15f,
            shadowLift = 0.10f,
            clarity = 0.15f,
            vibrance = 0.08f
        )
    ),
    LANDSCAPE(
        label = "Landscape",
        description = "Boosted sky & foliage saturation with crisp detail",
        contrastWeight = 1.25f,
        saturationWeight = 1.55f,
        exposureWeight = 1.10f,
        defaultTone = HdrToneAdjustments(
            highlightRecovery = 0.30f,
            shadowLift = 0.20f,
            clarity = 0.35f,
            vibrance = 0.35f
        )
    ),
    ARCHITECTURE(
        label = "Architecture / Interior",
        description = "Strong window highlight recovery & high local clarity",
        contrastWeight = 1.45f,
        saturationWeight = 0.90f,
        exposureWeight = 1.35f,
        defaultTone = HdrToneAdjustments(
            highlightRecovery = 0.65f,
            shadowLift = 0.40f,
            clarity = 0.55f,
            vibrance = 0.10f
        )
    ),
    DRAMATIC(
        label = "Dramatic",
        description = "High contrast & micro-detail punch",
        contrastWeight = 1.80f,
        saturationWeight = 1.35f,
        exposureWeight = 0.85f,
        defaultTone = HdrToneAdjustments(
            highlightRecovery = 0.40f,
            shadowLift = 0.35f,
            clarity = 0.75f,
            vibrance = 0.25f
        )
    )
}

data class HdrConfig(
    val alignmentMode: AlignmentMode = AlignmentMode.AUTO_HOMOGRAPHY,
    val contrastWeight: Float = 1.0f,
    val saturationWeight: Float = 1.0f,
    val exposureWeight: Float = 1.0f,
    val deghostingEnabled: Boolean = false,
    val anchorFrameIndex: Int? = null,
    val autoCropBorders: Boolean = true,
    val outputSettings: OutputSettings = OutputSettings()
)

enum class AstroFrameType(val label: String, val shortLabel: String) {
    LIGHT("Light Frame", "LIGHT"),
    DARK("Dark Frame", "DARK")
}

enum class AstroAlignmentMode(val label: String, val description: String) {
    STAR_FIELD(
        label = "Star Field (Tripod)",
        description = "Detects bright star centroids & matches constellations via RANSAC to align stars across Earth's rotation"
    ),
    HANDHELD_LOW_LIGHT(
        label = "Handheld Low-Light",
        description = "Multi-scale ECC + ORB alignment with high-gain luminance boost for dark scenes"
    ),
    NONE_STAR_TRAILS(
        label = "None (Star Trails)",
        description = "Skips alignment so stars intentionally form smooth circular trails"
    )
}

enum class AstroStackAlgorithm(val label: String, val description: String) {
    KAPPA_SIGMA(
        label = "Kappa-Sigma Clip",
        description = "Best — rejects satellite/plane trails, meteors & hot pixels beyond ±κ·σ"
    ),
    MEDIAN(
        label = "Median Stack",
        description = "Great for removing moving crowds & cars in low-light city shots"
    ),
    MEAN(
        label = "Mean / Average",
        description = "Maximum signal-to-noise smoothness for static scenes"
    ),
    MAXIMUM_LIGHTEN(
        label = "Maximum / Lighten",
        description = "Keeps brightest pixel per frame for Star Trails & light painting"
    )
}

data class ForegroundMaskStroke(
    val points: List<RetouchPoint>,
    val brushSizeFraction: Float = 0.08f
)

data class AstroConfig(
    val alignmentMode: AstroAlignmentMode = AstroAlignmentMode.STAR_FIELD,
    val stackAlgorithm: AstroStackAlgorithm = AstroStackAlgorithm.KAPPA_SIGMA,
    val sigmaClippingThreshold: Float = 2.5f,
    val freezeGroundEnabled: Boolean = false,
    val freezeGroundStrokes: List<ForegroundMaskStroke> = emptyList(),
    val autoCropBorders: Boolean = true,
    val outputSettings: OutputSettings = OutputSettings()
)

data class AstroStretchAdjustments(
    val asinhStretch: Float = 0f,
    val lightPollutionRemoval: Float = 0f,
    val chromaDenoise: Float = 0f
) {
    fun isIdentity(): Boolean =
        asinhStretch <= 0.005f &&
            lightPollutionRemoval <= 0.005f &&
            chromaDenoise <= 0.005f

    fun activeCount(): Int = listOf(
        asinhStretch,
        lightPollutionRemoval,
        chromaDenoise
    ).count { it > 0.005f }
}

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
    val exposureBracket: ExposureBracket = ExposureBracket.ANALYZING,
    val meanLuminance: Float = 0.5f,
    val astroFrameType: AstroFrameType = AstroFrameType.LIGHT,
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
    val frameLabels: List<String> = emptyList(),
    val stackMode: StackMode = StackMode.FocusStack,
    val defaultComparisonIndex: Int,
    val alignedFramesCount: Int,
    val processingTimeMs: Long,
    val usedProxyMode: Boolean,
    val proxyReason: String?,
    val outputWidth: Int,
    val outputHeight: Int,
    val alignmentNote: String,
    val wasAutoCropped: Boolean = false,
    val resolutionPreset: MaxResolutionPreset = MaxResolutionPreset.UHD_4K,
    val lightFramesUsed: Int = 0,
    val darkFramesUsed: Int = 0
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
