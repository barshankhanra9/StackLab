package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AstroStretchAdjustments
import com.example.domain.model.HdrToneAdjustments
import com.example.domain.model.OutputFormat
import com.example.domain.model.RetouchPoint
import com.example.domain.model.StackMode
import com.example.ui.StackLabUiState
import com.example.ui.components.RetouchBottomPanel
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberDim
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanDim
import com.example.ui.theme.ElevatedCharcoal
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.OnAmber
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ResultScreen(
    uiState: StackLabUiState,
    onNavigateBack: () -> Unit,
    onSaveFullRes: () -> Unit,
    onSelectOutputFormat: (OutputFormat) -> Unit,
    onNewStack: () -> Unit,
    onToggleFocusPeaking: (Int) -> Unit,
    onCompareFrameChanged: (Int) -> Unit,
    onSetRetouchMode: (Boolean) -> Unit,
    onSelectRetouchSource: (Int) -> Unit,
    onBrushSizeChange: (Float) -> Unit,
    onFeatherChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onCommitRetouchStroke: (List<RetouchPoint>) -> Unit,
    onUndoRetouch: () -> Unit,
    onRedoRetouch: () -> Unit,
    onToneAdjustmentsChange: (HdrToneAdjustments) -> Unit,
    onResetToneAdjustments: () -> Unit,
    onAstroStretchAdjustmentsChange: (AstroStretchAdjustments) -> Unit,
    onResetAstroStretchAdjustments: () -> Unit,
    onSnackbarConsumed: () -> Unit
) {
    val result = uiState.result
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = true) {
        if (uiState.isRetouchMode) {
            onSetRetouchMode(false)
        } else {
            onNavigateBack()
        }
    }

    var splitFraction by rememberSaveable { mutableFloatStateOf(0.5f) }
    var inspectSingleFrame by rememberSaveable { mutableStateOf(false) }
    var selectedCompareFrameIndex by rememberSaveable(result) {
        mutableIntStateOf(result?.defaultComparisonIndex ?: 0)
    }
    var inspectingResultInSingleMode by rememberSaveable { mutableStateOf(false) }
    var toneBarExpanded by rememberSaveable { mutableStateOf(false) }
    var astroBarExpanded by rememberSaveable { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var showRetouchMaskOverlay by remember { mutableStateOf(false) }

    // Live stroke feedback state while finger is dragging in Retouch mode
    val activeStrokePoints = remember { mutableStateListOf<RetouchPoint>() }
    var activeCursorNorm by remember { mutableStateOf<RetouchPoint?>(null) }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onSnackbarConsumed()
        }
    }

    LaunchedEffect(selectedCompareFrameIndex, uiState.isFocusPeakingEnabled) {
        if (uiState.isFocusPeakingEnabled && result != null) {
            onCompareFrameChanged(selectedCompareFrameIndex)
        }
    }

    Scaffold(
        containerColor = Color(0xFF0A0B0D),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (result != null) {
                if (uiState.isRetouchMode) {
                    RetouchBottomPanel(
                        sourceBitmaps = result.sourcePreviewBitmaps,
                        frameLabels = result.frameLabels,
                        selectedSourceIndex = uiState.retouchSourceIndex,
                        outputWidth = result.outputWidth,
                        outputHeight = result.outputHeight,
                        brushSizeFraction = uiState.retouchBrushSizeFraction,
                        feather = uiState.retouchFeather,
                        opacity = uiState.retouchOpacity,
                        isSaving = uiState.isSaving,
                        onSelectSource = onSelectRetouchSource,
                        onBrushSizeChange = onBrushSizeChange,
                        onFeatherChange = onFeatherChange,
                        onOpacityChange = onOpacityChange,
                        onDoneRetouch = { onSetRetouchMode(false) },
                        onSaveFullRes = onSaveFullRes
                    )
                } else {
                    Surface(
                        color = CharcoalSurface.copy(alpha = 0.96f),
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (result.stackMode == StackMode.HdrExposureBlend) {
                                ToneAndDetailBar(
                                    expanded = toneBarExpanded,
                                    adjustments = uiState.hdrToneAdjustments,
                                    isApplying = uiState.isApplyingTone,
                                    onToggleExpanded = { toneBarExpanded = !toneBarExpanded },
                                    onAdjustmentsChange = onToneAdjustmentsChange,
                                    onReset = onResetToneAdjustments
                                )
                            } else if (result.stackMode == StackMode.AstroNoiseStack) {
                                AstroStretchAndSkyBar(
                                    expanded = astroBarExpanded,
                                    adjustments = uiState.astroStretchAdjustments,
                                    isApplying = uiState.isApplyingTone,
                                    onToggleExpanded = { astroBarExpanded = !astroBarExpanded },
                                    onAdjustmentsChange = onAstroStretchAdjustmentsChange,
                                    onReset = onResetAstroStretchAdjustments
                                )
                            }

                            // Actual Output Dimensions & Telemetry Strip directly below image
                            val actualW = result.outputWidth
                            val actualH = result.outputHeight
                            val megapixels = (actualW.toLong() * actualH.toLong()) / 1_000_000.0
                            val astroCalTag = if (result.stackMode == StackMode.AstroNoiseStack) {
                                if (result.darkFramesUsed > 0) {
                                    " • ${result.lightFramesUsed}L/${result.darkFramesUsed}D"
                                } else {
                                    " • ${result.lightFramesUsed}L"
                                }
                            } else ""
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$actualW × $actualH px (${String.format(Locale.US, "%.1f", megapixels)} MP)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AmberAccent,
                                    modifier = Modifier.testTag("result_dimensions_text")
                                )
                                Text(
                                    text = "${result.resolutionPreset.shortLabel} • ${result.alignmentNote}$astroCalTag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedSlate
                                )
                            }

                            // Source Frame Selector Strip
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (inspectSingleFrame) {
                                    item {
                                        val selected = inspectingResultInSingleMode
                                        Surface(
                                            onClick = { inspectingResultInSingleMode = true },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (selected) AmberDim else ObsidianBackground,
                                            border = BorderStroke(
                                                1.dp,
                                                if (selected) AmberAccent else HairlineBorder
                                            )
                                        ) {
                                            Text(
                                                text = "STACKED",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (selected) AmberAccent else CrispWhite,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                            )
                                        }
                                    }
                                } else {
                                    item {
                                        Text(
                                            text = "BEFORE:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CyanAccent,
                                            modifier = Modifier.padding(end = 2.dp)
                                        )
                                    }
                                }

                                itemsIndexed(result.sourcePreviewBitmaps) { index, _ ->
                                    val isCurrent = if (inspectSingleFrame) {
                                        !inspectingResultInSingleMode && selectedCompareFrameIndex == index
                                    } else {
                                        selectedCompareFrameIndex == index
                                    }
                                    val labelTag = result.frameLabels.getOrNull(index)
                                        ?.takeIf { it.isNotBlank() && it != "..." }
                                    Surface(
                                        onClick = {
                                            selectedCompareFrameIndex = index
                                            inspectingResultInSingleMode = false
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isCurrent) CyanDim else ObsidianBackground,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isCurrent) CyanAccent else HairlineBorder
                                        ),
                                        modifier = Modifier.testTag("inspect_frame_$index")
                                    ) {
                                        Text(
                                            text = if (labelTag != null) "#${index + 1} $labelTag" else "#${index + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isCurrent) CyanAccent else CrispWhite,
                                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }

                            // Unified Export Format Selector (JPEG 95% + EXIF vs Lossless PNG)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "EXPORT:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MutedSlate
                                )
                                val jpegSelected = uiState.outputSettings.outputFormat == OutputFormat.JPEG
                                Surface(
                                    onClick = { onSelectOutputFormat(OutputFormat.JPEG) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (jpegSelected) AmberDim else ObsidianBackground,
                                    border = BorderStroke(
                                        1.dp,
                                        if (jpegSelected) AmberAccent else HairlineBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("result_format_jpeg")
                                ) {
                                    Text(
                                        text = "JPEG ${uiState.outputSettings.jpegQuality}% • EXIF",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (jpegSelected) AmberAccent else CrispWhite,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                    )
                                }

                                val pngSelected = uiState.outputSettings.outputFormat == OutputFormat.PNG
                                Surface(
                                    onClick = { onSelectOutputFormat(OutputFormat.PNG) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (pngSelected) CyanDim else ObsidianBackground,
                                    border = BorderStroke(
                                        1.dp,
                                        if (pngSelected) CyanAccent else HairlineBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("result_format_png")
                                ) {
                                    Text(
                                        text = "PNG Lossless",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (pngSelected) CyanAccent else CrispWhite,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                    )
                                }
                            }

                            // Primary Action Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onNewStack,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, HairlineBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("new_stack_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = CrispWhite,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.new_stack),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = CrispWhite
                                    )
                                }

                                Button(
                                    onClick = onSaveFullRes,
                                    enabled = !uiState.isSaving,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AmberAccent,
                                        contentColor = OnAmber
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    modifier = Modifier
                                        .weight(1.35f)
                                        .height(50.dp)
                                        .testTag("save_result_button")
                                ) {
                                    if (uiState.isSaving) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = OnAmber,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (uiState.isSaving) "Exporting..." else stringResource(R.string.save_full_res),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (result == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No stacked composite available",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CrispWhite
                )
            }
            return@Scaffold
        }

        val safeCompareIdx = selectedCompareFrameIndex.coerceIn(0, result.sourcePreviewBitmaps.lastIndex)
        val afterImageBitmap = remember(result.outputBitmap, uiState.retouchBitmapVersion) {
            result.outputBitmap.asImageBitmap()
        }
        val beforeImageBitmap = remember(result, safeCompareIdx) {
            result.sourcePreviewBitmaps[safeCompareIdx].asImageBitmap()
        }
        val afterPeakingBitmap = remember(uiState.focusPeakingOverlayBitmap) {
            uiState.focusPeakingOverlayBitmap?.takeIf { !it.isRecycled }?.asImageBitmap()
        }
        val beforePeakingBitmap = remember(uiState.comparePeakingOverlayBitmap) {
            uiState.comparePeakingOverlayBitmap?.takeIf { !it.isRecycled }?.asImageBitmap()
        }
        val maskOverlayBitmap = remember(uiState.retouchMaskBitmap) {
            uiState.retouchMaskBitmap?.takeIf { !it.isRecycled }?.asImageBitmap()
        }

        val latestScale by rememberUpdatedState(scale)
        val latestPan by rememberUpdatedState(panOffset)
        val latestCommitStroke by rememberUpdatedState(onCommitRetouchStroke)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val density = LocalDensity.current
            val containerWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val containerHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

            val imageW = result.outputBitmap.width.toFloat().coerceAtLeast(1f)
            val imageH = result.outputBitmap.height.toFloat().coerceAtLeast(1f)
            val fitScale = min(containerWidthPx / imageW, containerHeightPx / imageH)
            val fittedW = imageW * fitScale
            val fittedH = imageH * fitScale
            val fitLeft = (containerWidthPx - fittedW) / 2f
            val fitTop = (containerHeightPx - fittedH) / 2f

            fun screenToNormalized(screenPt: Offset, curScale: Float, curPan: Offset): RetouchPoint {
                val cx = containerWidthPx / 2f
                val cy = containerHeightPx / 2f
                val boxX = cx + (screenPt.x - cx - curPan.x) / curScale
                val boxY = cy + (screenPt.y - cy - curPan.y) / curScale
                val u = ((boxX - fitLeft) / fittedW).coerceIn(0f, 1f)
                val v = ((boxY - fitTop) / fittedH).coerceIn(0f, 1f)
                return RetouchPoint(u, v)
            }

            // Gesture canvas: Retouch mode (1-finger paint + 2-finger zoom) vs Compare/Inspect (transform + double tap)
            val gestureModifier = if (uiState.isRetouchMode) {
                Modifier
                    .fillMaxSize()
                    .pointerInput(containerWidthPx, containerHeightPx, imageW, imageH) {
                        awaitEachGesture {
                            val firstDown = awaitFirstDown(requireUnconsumed = false)
                            var isMultiTouch = false
                            activeStrokePoints.clear()
                            val startNorm = screenToNormalized(firstDown.position, latestScale, latestPan)
                            activeStrokePoints.add(startNorm)
                            activeCursorNorm = startNorm

                            while (true) {
                                val event = awaitPointerEvent()
                                val pressedCount = event.changes.count { it.pressed }
                                if (pressedCount >= 2) {
                                    if (!isMultiTouch) {
                                        isMultiTouch = true
                                        activeStrokePoints.clear()
                                        activeCursorNorm = null
                                    }
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val newScale = (latestScale * zoomChange).coerceIn(1f, 8f)
                                    val maxPanX = ((newScale - 1f) * containerWidthPx) / 2f
                                    val maxPanY = ((newScale - 1f) * containerHeightPx) / 2f
                                    val nextOffset = if (newScale <= 1.01f) {
                                        Offset.Zero
                                    } else {
                                        Offset(
                                            x = (latestPan.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                            y = (latestPan.y + panChange.y).coerceIn(-maxPanY, maxPanY)
                                        )
                                    }
                                    scale = newScale
                                    panOffset = nextOffset
                                    event.changes.forEach { it.consume() }
                                } else if (pressedCount == 1 && !isMultiTouch) {
                                    val change = event.changes.firstOrNull { it.pressed }
                                    if (change != null) {
                                        change.consume()
                                        val norm = screenToNormalized(change.position, latestScale, latestPan)
                                        activeStrokePoints.add(norm)
                                        activeCursorNorm = norm
                                    }
                                } else if (pressedCount == 0) {
                                    if (!isMultiTouch && activeStrokePoints.isNotEmpty()) {
                                        latestCommitStroke(activeStrokePoints.toList())
                                    }
                                    activeStrokePoints.clear()
                                    activeCursorNorm = null
                                    break
                                }
                            }
                        }
                    }
            } else {
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 8f)
                            val maxPanX = ((newScale - 1f) * containerWidthPx) / 2f
                            val maxPanY = ((newScale - 1f) * containerHeightPx) / 2f
                            val nextOffset = if (newScale <= 1.01f) {
                                Offset.Zero
                            } else {
                                Offset(
                                    x = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                    y = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                )
                            }
                            scale = newScale
                            panOffset = nextOffset
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.2f) {
                                    scale = 1f
                                    panOffset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
            }

            Box(modifier = gestureModifier) {
                val transformModifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = panOffset.x,
                        translationY = panOffset.y
                    )

                if (uiState.isRetouchMode || inspectSingleFrame) {
                    val showOutput = uiState.isRetouchMode || inspectingResultInSingleMode
                    val singleBitmap = if (showOutput) afterImageBitmap else beforeImageBitmap
                    val singlePeaking = if (showOutput) afterPeakingBitmap else beforePeakingBitmap

                    Image(
                        bitmap = singleBitmap,
                        contentDescription = if (showOutput) {
                            "Stacked composite result"
                        } else {
                            "Source frame #${safeCompareIdx + 1}"
                        },
                        contentScale = ContentScale.Fit,
                        modifier = transformModifier
                    )

                    if (uiState.isFocusPeakingEnabled && singlePeaking != null) {
                        Image(
                            bitmap = singlePeaking,
                            contentDescription = "Focus peaking sharpness overlay",
                            contentScale = ContentScale.Fit,
                            modifier = transformModifier
                        )
                    }

                    if (uiState.isRetouchMode && showRetouchMaskOverlay && maskOverlayBitmap != null) {
                        Image(
                            bitmap = maskOverlayBitmap,
                            contentDescription = "Retouch painted mask overlay",
                            contentScale = ContentScale.Fit,
                            modifier = transformModifier
                        )
                    }

                    // Live brush stroke preview & cursor ring inside same zoomed graphicsLayer
                    if (uiState.isRetouchMode && (activeStrokePoints.isNotEmpty() || activeCursorNorm != null)) {
                        Canvas(modifier = transformModifier) {
                            val boxFitScale = min(size.width / imageW, size.height / imageH)
                            val boxFittedW = imageW * boxFitScale
                            val boxFittedH = imageH * boxFitScale
                            val boxLeft = (size.width - boxFittedW) / 2f
                            val boxTop = (size.height - boxFittedH) / 2f
                            val radiusPx = (uiState.retouchBrushSizeFraction * min(boxFittedW, boxFittedH) * 0.5f)
                                .coerceAtLeast(3f)

                            if (activeStrokePoints.size >= 2) {
                                val path = Path()
                                activeStrokePoints.forEachIndexed { idx, pt ->
                                    val x = boxLeft + pt.u * boxFittedW
                                    val y = boxTop + pt.v * boxFittedH
                                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(
                                    path = path,
                                    color = CyanAccent.copy(alpha = 0.45f),
                                    style = Stroke(
                                        width = radiusPx * 2f,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                            activeCursorNorm?.let { cur ->
                                val cx = boxLeft + cur.u * boxFittedW
                                val cy = boxTop + cur.v * boxFittedH
                                drawCircle(
                                    color = AmberAccent,
                                    radius = radiusPx,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 2.dp.toPx() / scale)
                                )
                            }
                        }
                    }
                } else {
                    // Right side: Final Stacked Output
                    Image(
                        bitmap = afterImageBitmap,
                        contentDescription = "Stacked composite output",
                        contentScale = ContentScale.Fit,
                        modifier = transformModifier
                    )
                    if (uiState.isFocusPeakingEnabled && afterPeakingBitmap != null) {
                        Image(
                            bitmap = afterPeakingBitmap,
                            contentDescription = "Focus peaking overlay on stacked output",
                            contentScale = ContentScale.Fit,
                            modifier = transformModifier
                        )
                    }

                    // Left side: Before (Source Frame #N) clipped dynamically by splitFraction
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                clipRect(right = size.width * splitFraction) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = panOffset.x,
                                translationY = panOffset.y
                            )
                    ) {
                        Image(
                            bitmap = beforeImageBitmap,
                            contentDescription = "Before frame #${safeCompareIdx + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (uiState.isFocusPeakingEnabled && beforePeakingBitmap != null) {
                            Image(
                                bitmap = beforePeakingBitmap,
                                contentDescription = "Focus peaking overlay on source frame",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // Interactive Before/After Split Divider & Handle
            if (!uiState.isRetouchMode && !inspectSingleFrame) {
                val dividerX = (containerWidthPx * splitFraction).roundToInt()
                val handleWidthPx = with(density) { 48.dp.toPx() }.roundToInt()

                Box(
                    modifier = Modifier
                        .offset { IntOffset(dividerX - handleWidthPx / 2, 0) }
                        .width(48.dp)
                        .fillMaxHeight()
                        .pointerInput(containerWidthPx) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                splitFraction = (splitFraction + dragAmount / containerWidthPx)
                                    .coerceIn(0.03f, 0.97f)
                            }
                        }
                        .testTag("before_after_split_handle"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(CyanAccent.copy(alpha = 0.85f))
                    )
                    Surface(
                        shape = CircleShape,
                        color = ObsidianBackground.copy(alpha = 0.92f),
                        border = BorderStroke(2.dp, CyanAccent),
                        shadowElevation = 6.dp,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = stringResource(R.string.cd_compare_slider),
                                tint = AmberAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // Top HUD Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ObsidianBackground.copy(alpha = 0.78f),
                        border = BorderStroke(1.dp, HairlineBorder)
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("result_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                tint = CrispWhite
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (scale > 1.05f) {
                            Surface(
                                onClick = {
                                    scale = 1f
                                    panOffset = Offset.Zero
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = ObsidianBackground.copy(alpha = 0.84f),
                                border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ZoomOutMap,
                                        contentDescription = stringResource(R.string.cd_reset_zoom),
                                        tint = AmberAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.1fx", scale),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AmberAccent
                                    )
                                }
                            }
                        }

                        // Focus Peaking Toggle Chip
                        Surface(
                            onClick = { onToggleFocusPeaking(safeCompareIdx) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (uiState.isFocusPeakingEnabled) CyanDim.copy(alpha = 0.9f) else ObsidianBackground.copy(alpha = 0.84f),
                            border = BorderStroke(
                                1.dp,
                                if (uiState.isFocusPeakingEnabled) CyanAccent else HairlineBorder
                            ),
                            modifier = Modifier.testTag("toggle_focus_peaking")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CenterFocusStrong,
                                    contentDescription = null,
                                    tint = if (uiState.isFocusPeakingEnabled) CyanAccent else CrispWhite,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Peaking",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.isFocusPeakingEnabled) CyanAccent else CrispWhite
                                )
                            }
                        }

                        // Retouch Mode Toggle Button
                        Surface(
                            onClick = { onSetRetouchMode(!uiState.isRetouchMode) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (uiState.isRetouchMode) AmberDim.copy(alpha = 0.92f) else ObsidianBackground.copy(alpha = 0.84f),
                            border = BorderStroke(
                                1.dp,
                                if (uiState.isRetouchMode) AmberAccent else HairlineBorder
                            ),
                            modifier = Modifier.testTag("toggle_retouch_mode")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Brush,
                                    contentDescription = null,
                                    tint = if (uiState.isRetouchMode) AmberAccent else CrispWhite,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Retouch",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.isRetouchMode) AmberAccent else CrispWhite
                                )
                            }
                        }

                        if (!uiState.isRetouchMode) {
                            Surface(
                                onClick = { inspectSingleFrame = !inspectSingleFrame },
                                shape = RoundedCornerShape(10.dp),
                                color = ObsidianBackground.copy(alpha = 0.84f),
                                border = BorderStroke(
                                    1.dp,
                                    if (inspectSingleFrame) AmberAccent else CyanAccent
                                ),
                                modifier = Modifier.testTag("toggle_inspect_mode")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = if (inspectSingleFrame) Icons.Rounded.Compare else Icons.Rounded.Photo,
                                        contentDescription = null,
                                        tint = if (inspectSingleFrame) AmberAccent else CyanAccent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = if (inspectSingleFrame) "Split" else "Single",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (inspectSingleFrame) AmberAccent else CyanAccent
                                    )
                                }
                            }
                        }
                    }
                }

                // Retouch Undo/Redo/Show-Mask Sub-Bar OR Comparison Telemetry Badges
                if (uiState.isRetouchMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val canUndo = uiState.retouchUndoCount > 0 && !uiState.isApplyingRetouch
                            val canRedo = uiState.retouchRedoCount > 0 && !uiState.isApplyingRetouch

                            Surface(
                                onClick = onUndoRetouch,
                                enabled = canUndo,
                                shape = RoundedCornerShape(8.dp),
                                color = ObsidianBackground.copy(alpha = 0.84f),
                                border = BorderStroke(1.dp, if (canUndo) CrispWhite.copy(alpha = 0.45f) else HairlineBorder),
                                modifier = Modifier.testTag("retouch_undo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                                    contentDescription = stringResource(R.string.cd_retouch_undo),
                                    tint = if (canUndo) CrispWhite else MutedSlate.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 7.dp)
                                        .size(18.dp)
                                )
                            }

                            Surface(
                                onClick = onRedoRetouch,
                                enabled = canRedo,
                                shape = RoundedCornerShape(8.dp),
                                color = ObsidianBackground.copy(alpha = 0.84f),
                                border = BorderStroke(1.dp, if (canRedo) CrispWhite.copy(alpha = 0.45f) else HairlineBorder),
                                modifier = Modifier.testTag("retouch_redo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Redo,
                                    contentDescription = stringResource(R.string.cd_retouch_redo),
                                    tint = if (canRedo) CrispWhite else MutedSlate.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 7.dp)
                                        .size(18.dp)
                                )
                            }

                            // Press-and-hold Show Mask button
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (showRetouchMaskOverlay) CyanDim else ObsidianBackground.copy(alpha = 0.84f),
                                border = BorderStroke(
                                    1.dp,
                                    if (showRetouchMaskOverlay) CyanAccent else HairlineBorder
                                ),
                                modifier = Modifier
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                showRetouchMaskOverlay = true
                                                tryAwaitRelease()
                                                showRetouchMaskOverlay = false
                                            }
                                        )
                                    }
                                    .testTag("retouch_show_mask_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Visibility,
                                        contentDescription = null,
                                        tint = if (showRetouchMaskOverlay) CyanAccent else CrispWhite,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = if (showRetouchMaskOverlay) "Mask Shown" else "Hold Mask",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (showRetouchMaskOverlay) CyanAccent else CrispWhite
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ObsidianBackground.copy(alpha = 0.82f),
                            border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.5f))
                        ) {
                            val srcLabel = result.frameLabels.getOrNull(uiState.retouchSourceIndex)
                                ?.takeIf { it.isNotBlank() && it != "..." }
                            Text(
                                text = if (srcLabel != null) {
                                    "SOURCE #${uiState.retouchSourceIndex + 1} • ${srcLabel.uppercase()}"
                                } else {
                                    "SOURCE #${uiState.retouchSourceIndex + 1}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val cmpLabel = result.frameLabels.getOrNull(safeCompareIdx)
                            ?.takeIf { it.isNotBlank() && it != "..." }
                        val cmpSuffix = if (cmpLabel != null) " (${cmpLabel.uppercase()})" else ""
                        if (!inspectSingleFrame) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ObsidianBackground.copy(alpha = 0.78f),
                                border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "BEFORE • #${safeCompareIdx + 1}$cmpSuffix",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanAccent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ObsidianBackground.copy(alpha = 0.78f),
                                border = BorderStroke(1.dp, HairlineBorder)
                            ) {
                                Text(
                                    text = if (inspectingResultInSingleMode) {
                                        when (result.stackMode) {
                                            StackMode.HdrExposureBlend -> "VIEWING: HDR COMPOSITE"
                                            StackMode.AstroNoiseStack -> "VIEWING: ASTRO STACK"
                                            StackMode.FocusStack -> "VIEWING: STACKED OUTPUT"
                                        }
                                    } else {
                                        "VIEWING: SOURCE #${safeCompareIdx + 1}$cmpSuffix"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CrispWhite,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ObsidianBackground.copy(alpha = 0.78f),
                            border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.4f))
                        ) {
                            val secs = String.format(Locale.US, "%.1fs", result.processingTimeMs / 1000f)
                            val cropTag = if (result.wasAutoCropped) " • CROPPED" else ""
                            Text(
                                text = if (inspectSingleFrame) {
                                    "${result.outputWidth}×${result.outputHeight} • $secs$cropTag"
                                } else {
                                    "AFTER • ${result.sourcePreviewBitmaps.size}F • $secs$cropTag"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToneAndDetailBar(
    expanded: Boolean,
    adjustments: HdrToneAdjustments,
    isApplying: Boolean,
    onToggleExpanded: () -> Unit,
    onAdjustmentsChange: (HdrToneAdjustments) -> Unit,
    onReset: () -> Unit
) {
    val activeCount = adjustments.activeCount()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ObsidianBackground.copy(alpha = 0.85f),
        border = BorderStroke(
            1.dp,
            if (expanded || activeCount > 0) AmberAccent.copy(alpha = 0.45f) else HairlineBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .testTag("toggle_tone_detail_bar"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = AmberAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.tone_and_detail),
                        style = MaterialTheme.typography.titleSmall,
                        color = CrispWhite
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (activeCount > 0) CyanDim else ElevatedCharcoal,
                        border = BorderStroke(
                            1.dp,
                            if (activeCount > 0) CyanAccent.copy(alpha = 0.55f) else HairlineBorder
                        )
                    ) {
                        Text(
                            text = if (activeCount > 0) "$activeCount Active" else "Neutral",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (activeCount > 0) CyanAccent else MutedSlate,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    if (isApplying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = AmberAccent,
                            strokeWidth = 1.5.dp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (activeCount > 0) {
                        Surface(
                            onClick = onReset,
                            shape = RoundedCornerShape(6.dp),
                            color = ElevatedCharcoal,
                            border = BorderStroke(1.dp, HairlineBorder),
                            modifier = Modifier.testTag("reset_tone_detail_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RestartAlt,
                                    contentDescription = null,
                                    tint = CrispWhite,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = stringResource(R.string.reset),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CrispWhite
                                )
                            }
                        }
                    }

                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                        contentDescription = if (expanded) "Collapse Tone & Detail" else "Expand Tone & Detail",
                        tint = CrispWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 228.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = HairlineBorder)
                    ToneSliderRow(
                        label = "Highlight Recovery",
                        hint = "Pulls back bright skies & windows",
                        value = adjustments.highlightRecovery,
                        accentColor = AmberAccent,
                        testTag = "tone_highlight_slider",
                        onValueChange = {
                            onAdjustmentsChange(adjustments.copy(highlightRecovery = it))
                        }
                    )
                    ToneSliderRow(
                        label = "Shadow Lift",
                        hint = "Brightens shadows without washing blacks",
                        value = adjustments.shadowLift,
                        accentColor = CyanAccent,
                        testTag = "tone_shadow_slider",
                        onValueChange = {
                            onAdjustmentsChange(adjustments.copy(shadowLift = it))
                        }
                    )
                    ToneSliderRow(
                        label = "Micro-Contrast / Clarity",
                        hint = "LAB Luminance CLAHE local texture punch",
                        value = adjustments.clarity,
                        accentColor = AmberAccent,
                        testTag = "tone_clarity_slider",
                        onValueChange = {
                            onAdjustmentsChange(adjustments.copy(clarity = it))
                        }
                    )
                    ToneSliderRow(
                        label = "Vibrance",
                        hint = "Boosts muted colors; protects skin & skies",
                        value = adjustments.vibrance,
                        accentColor = CyanAccent,
                        testTag = "tone_vibrance_slider",
                        onValueChange = {
                            onAdjustmentsChange(adjustments.copy(vibrance = it))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToneSliderRow(
    label: String,
    hint: String,
    value: Float,
    accentColor: Color,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = CrispWhite
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedSlate
                )
            }
            Text(
                text = "${(value * 100f).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = accentColor
            )
        }
        Slider(
            value = value,
            onValueChange = {
                val stepped = ((it * 100f).roundToInt() / 100f).coerceIn(0f, 1f)
                onValueChange(stepped)
            },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = HairlineBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .testTag(testTag)
        )
    }
}

@Composable
private fun AstroStretchAndSkyBar(
    expanded: Boolean,
    adjustments: AstroStretchAdjustments,
    isApplying: Boolean,
    onToggleExpanded: () -> Unit,
    onAdjustmentsChange: (AstroStretchAdjustments) -> Unit,
    onReset: () -> Unit
) {
    val activeCount = adjustments.activeCount()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ObsidianBackground.copy(alpha = 0.85f),
        border = BorderStroke(
            1.dp,
            if (expanded || activeCount > 0) CyanAccent.copy(alpha = 0.50f) else HairlineBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .testTag("toggle_astro_stretch_bar"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.astro_stretch_sky),
                        style = MaterialTheme.typography.titleSmall,
                        color = CrispWhite
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (activeCount > 0) CyanDim else ElevatedCharcoal,
                        border = BorderStroke(
                            1.dp,
                            if (activeCount > 0) CyanAccent.copy(alpha = 0.55f) else HairlineBorder
                        )
                    ) {
                        Text(
                            text = if (activeCount > 0) "$activeCount Active" else "Linear",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (activeCount > 0) CyanAccent else MutedSlate,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    if (isApplying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = CyanAccent,
                            strokeWidth = 1.5.dp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (activeCount > 0) {
                        Surface(
                            onClick = onReset,
                            shape = RoundedCornerShape(6.dp),
                            color = ElevatedCharcoal,
                            border = BorderStroke(1.dp, HairlineBorder),
                            modifier = Modifier.testTag("reset_astro_stretch_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RestartAlt,
                                    contentDescription = null,
                                    tint = CrispWhite,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = stringResource(R.string.reset),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CrispWhite
                                )
                            }
                        }
                    }

                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                        contentDescription = if (expanded) "Collapse Astro Stretch & Sky" else "Expand Astro Stretch & Sky",
                        tint = CrispWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 228.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = HairlineBorder)
                    ToneSliderRow(
                        label = "Asinh / Histogram Stretch",
                        hint = "Reveals faint Milky Way dust & nebulae without blowing star cores",
                        value = adjustments.asinhStretch,
                        accentColor = CyanAccent,
                        testTag = "astro_stretch_slider",
                        onValueChange = {
                            onAdjustmentsChange(adjustments.copy(asinhStretch = it))
                        }
                    )
                    ToneSliderRow(
                        label = "Light Pollution Neutralizer",
                        hint = "Models & subtracts orange/green skyglow gradients via sky background map",
                        value = adjustments.lightPollutionRemoval,
                        accentColor = AmberAccent,
                        testTag = "astro_light_pollution_slider",
                        onValueChange = {
                            onAdjustmentsChange(adjustments.copy(lightPollutionRemoval = it))
                        }
                    )
                    ToneSliderRow(
                        label = "Chroma Denoise",
                        hint = "Smooths color blotches in dark shadows while keeping stars pinpoint sharp",
                        value = adjustments.chromaDenoise,
                        accentColor = CyanAccent,
                        testTag = "astro_chroma_denoise_slider",
                        onValueChange = {
                            onAdjustmentsChange(adjustments.copy(chromaDenoise = it))
                        }
                    )
                }
            }
        }
    }
}

