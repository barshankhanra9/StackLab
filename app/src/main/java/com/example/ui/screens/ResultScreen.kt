package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.example.ui.StackLabUiState
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberDim
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanDim
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.OnAmber
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ResultScreen(
    uiState: StackLabUiState,
    onNavigateBack: () -> Unit,
    onSaveFullRes: () -> Unit,
    onNewStack: () -> Unit,
    onSnackbarConsumed: () -> Unit
) {
    val result = uiState.result
    val snackbarHostState = remember { SnackbarHostState() }

    var splitFraction by rememberSaveable { mutableFloatStateOf(0.5f) }
    var inspectSingleFrame by rememberSaveable { mutableStateOf(false) }
    var selectedCompareFrameIndex by rememberSaveable {
        mutableIntStateOf(result?.defaultComparisonIndex ?: 0)
    }
    var inspectingResultInSingleMode by rememberSaveable { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onSnackbarConsumed()
        }
    }

    Scaffold(
        containerColor = Color(0xFF0A0B0D),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (result != null) {
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
                                        text = "#${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isCurrent) CyanAccent else CrispWhite,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                                    )
                                }
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
        val afterImageBitmap = remember(result.outputBitmap) { result.outputBitmap.asImageBitmap() }
        val beforeImageBitmap = remember(result, safeCompareIdx) {
            result.sourcePreviewBitmaps[safeCompareIdx].asImageBitmap()
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val density = LocalDensity.current
            val containerWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val containerHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

            // Synced pinch-to-zoom and pan canvas
            Box(
                modifier = Modifier
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
            ) {
                val transformModifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = panOffset.x,
                        translationY = panOffset.y
                    )

                if (inspectSingleFrame) {
                    val singleBitmap = if (inspectingResultInSingleMode) {
                        afterImageBitmap
                    } else {
                        beforeImageBitmap
                    }
                    Image(
                        bitmap = singleBitmap,
                        contentDescription = if (inspectingResultInSingleMode) {
                            "Stacked composite result"
                        } else {
                            "Source frame #${safeCompareIdx + 1}"
                        },
                        contentScale = ContentScale.Fit,
                        modifier = transformModifier
                    )
                } else {
                    // Right side: Final Stacked Output
                    Image(
                        bitmap = afterImageBitmap,
                        contentDescription = "Stacked composite output",
                        contentScale = ContentScale.Fit,
                        modifier = transformModifier
                    )

                    // Left side: Before (Source Frame #N) clipped dynamically by splitFraction
                    Image(
                        bitmap = beforeImageBitmap,
                        contentDescription = "Before frame #${safeCompareIdx + 1}",
                        contentScale = ContentScale.Fit,
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
                    )
                }
            }

            // Interactive Before/After Split Divider & Handle
            if (!inspectSingleFrame) {
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (scale > 1.05f) {
                            Surface(
                                onClick = {
                                    scale = 1f
                                    panOffset = Offset.Zero
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = ObsidianBackground.copy(alpha = 0.82f),
                                border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ZoomOutMap,
                                        contentDescription = stringResource(R.string.cd_reset_zoom),
                                        tint = AmberAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.1fx", scale),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AmberAccent
                                    )
                                }
                            }
                        }

                        Surface(
                            onClick = { inspectSingleFrame = !inspectSingleFrame },
                            shape = RoundedCornerShape(10.dp),
                            color = ObsidianBackground.copy(alpha = 0.82f),
                            border = BorderStroke(
                                1.dp,
                                if (inspectSingleFrame) AmberAccent else CyanAccent
                            ),
                            modifier = Modifier.testTag("toggle_inspect_mode")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (inspectSingleFrame) Icons.Rounded.Compare else Icons.Rounded.Photo,
                                    contentDescription = null,
                                    tint = if (inspectSingleFrame) AmberAccent else CyanAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (inspectSingleFrame) "Split Compare" else "Inspect Frame",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (inspectSingleFrame) AmberAccent else CyanAccent
                                )
                            }
                        }
                    }
                }

                // Comparison & Telemetry Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!inspectSingleFrame) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ObsidianBackground.copy(alpha = 0.78f),
                            border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "BEFORE • #${safeCompareIdx + 1}",
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
                                text = if (inspectingResultInSingleMode) "VIEWING: STACKED OUTPUT"
                                else "VIEWING: SOURCE #${safeCompareIdx + 1}",
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
                        Text(
                            text = if (inspectSingleFrame) {
                                "${result.outputWidth}×${result.outputHeight} • $secs"
                            } else {
                                "AFTER • ${result.sourcePreviewBitmaps.size}F • $secs"
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
