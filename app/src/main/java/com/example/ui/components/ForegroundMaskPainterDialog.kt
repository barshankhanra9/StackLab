package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.domain.model.ForegroundMaskStroke
import com.example.domain.model.RetouchPoint
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.OnAmber
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ForegroundMaskPainterDialog(
    referenceFrameUri: Uri,
    frameWidth: Int,
    frameHeight: Int,
    initialStrokes: List<ForegroundMaskStroke>,
    onAddStroke: (ForegroundMaskStroke) -> Unit,
    onUndoStroke: () -> Unit,
    onClearStrokes: () -> Unit,
    onDismiss: () -> Unit
) {
    var brushSizeFraction by remember { mutableFloatStateOf(0.08f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val activePoints = remember { mutableStateListOf<RetouchPoint>() }
    var activeCursor by remember { mutableStateOf<RetouchPoint?>(null) }

    val latestScale by rememberUpdatedState(scale)
    val latestPan by rememberUpdatedState(panOffset)
    val latestBrush by rememberUpdatedState(brushSizeFraction)
    val latestOnAddStroke by rememberUpdatedState(onAddStroke)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0B0D))
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(CharcoalSurface.copy(alpha = 0.96f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("fg_mask_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close foreground mask editor",
                            tint = CrispWhite
                        )
                    }
                    Column {
                        Text(
                            text = "Freeze Ground Mask",
                            style = MaterialTheme.typography.titleMedium,
                            color = CrispWhite
                        )
                        Text(
                            text = "Brush over ground, trees & mountains",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldSuccess
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
                            shape = RoundedCornerShape(8.dp),
                            color = ObsidianBackground,
                            border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ZoomOutMap,
                                    contentDescription = "Reset zoom",
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

                    IconButton(
                        onClick = onUndoStroke,
                        enabled = initialStrokes.isNotEmpty(),
                        modifier = Modifier.testTag("fg_mask_undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = "Undo mask stroke",
                            tint = if (initialStrokes.isNotEmpty()) CrispWhite else MutedSlate.copy(alpha = 0.4f)
                        )
                    }

                    IconButton(
                        onClick = onClearStrokes,
                        enabled = initialStrokes.isNotEmpty(),
                        modifier = Modifier.testTag("fg_mask_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "Clear ground mask",
                            tint = if (initialStrokes.isNotEmpty()) AmberAccent else MutedSlate.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // Interactive Canvas Stage
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0A0B0D))
            ) {
                val density = LocalDensity.current
                val containerWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                val containerHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

                val imageW = frameWidth.toFloat().coerceAtLeast(1f)
                val imageH = frameHeight.toFloat().coerceAtLeast(1f)
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

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(containerWidthPx, containerHeightPx, imageW, imageH) {
                            awaitEachGesture {
                                val firstDown = awaitFirstDown(requireUnconsumed = false)
                                var isMultiTouch = false
                                activePoints.clear()
                                val startNorm = screenToNormalized(firstDown.position, latestScale, latestPan)
                                activePoints.add(startNorm)
                                activeCursor = startNorm

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressedCount = event.changes.count { it.pressed }
                                    if (pressedCount >= 2) {
                                        if (!isMultiTouch) {
                                            isMultiTouch = true
                                            activePoints.clear()
                                            activeCursor = null
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
                                            activePoints.add(norm)
                                            activeCursor = norm
                                        }
                                    } else if (pressedCount == 0) {
                                        if (!isMultiTouch && activePoints.isNotEmpty()) {
                                            latestOnAddStroke(
                                                ForegroundMaskStroke(
                                                    points = activePoints.toList(),
                                                    brushSizeFraction = latestBrush
                                                )
                                            )
                                        }
                                        activePoints.clear()
                                        activeCursor = null
                                        break
                                    }
                                }
                            }
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

                    AsyncImage(
                        model = referenceFrameUri,
                        contentDescription = "Reference frame for Freeze Ground mask",
                        contentScale = ContentScale.Fit,
                        modifier = transformModifier
                    )

                    Canvas(modifier = transformModifier) {
                        val boxFitScale = min(size.width / imageW, size.height / imageH)
                        val boxFittedW = imageW * boxFitScale
                        val boxFittedH = imageH * boxFitScale
                        val boxLeft = (size.width - boxFittedW) / 2f
                        val boxTop = (size.height - boxFittedH) / 2f
                        val minFitted = min(boxFittedW, boxFittedH)

                        for (stroke in initialStrokes) {
                            if (stroke.points.isEmpty()) continue
                            val radiusPx = (stroke.brushSizeFraction * minFitted * 0.5f).coerceAtLeast(3f)
                            if (stroke.points.size == 1) {
                                val p = stroke.points[0]
                                drawCircle(
                                    color = EmeraldSuccess.copy(alpha = 0.42f),
                                    radius = radiusPx,
                                    center = Offset(boxLeft + p.u * boxFittedW, boxTop + p.v * boxFittedH)
                                )
                            } else {
                                val path = Path()
                                stroke.points.forEachIndexed { idx, pt ->
                                    val x = boxLeft + pt.u * boxFittedW
                                    val y = boxTop + pt.v * boxFittedH
                                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(
                                    path = path,
                                    color = EmeraldSuccess.copy(alpha = 0.42f),
                                    style = Stroke(
                                        width = radiusPx * 2f,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        if (activePoints.isNotEmpty()) {
                            val activeRadius = (brushSizeFraction * minFitted * 0.5f).coerceAtLeast(3f)
                            if (activePoints.size >= 2) {
                                val path = Path()
                                activePoints.forEachIndexed { idx, pt ->
                                    val x = boxLeft + pt.u * boxFittedW
                                    val y = boxTop + pt.v * boxFittedH
                                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(
                                    path = path,
                                    color = CyanAccent.copy(alpha = 0.48f),
                                    style = Stroke(
                                        width = activeRadius * 2f,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                            activeCursor?.let { cur ->
                                drawCircle(
                                    color = AmberAccent,
                                    radius = activeRadius,
                                    center = Offset(boxLeft + cur.u * boxFittedW, boxTop + cur.v * boxFittedH),
                                    style = Stroke(width = 2.dp.toPx() / scale)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Brush Controls
            Surface(
                color = CharcoalSurface.copy(alpha = 0.97f),
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Brush,
                                contentDescription = null,
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Brush Size (${(brushSizeFraction * 100f).roundToInt()}%)",
                                style = MaterialTheme.typography.labelMedium,
                                color = CrispWhite
                            )
                        }
                        Text(
                            text = "1-FINGER PAINT • 2-FINGER ZOOM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedSlate
                        )
                    }

                    Slider(
                        value = brushSizeFraction,
                        onValueChange = { brushSizeFraction = it.coerceIn(0.02f, 0.20f) },
                        valueRange = 0.02f..0.20f,
                        colors = SliderDefaults.colors(
                            thumbColor = EmeraldSuccess,
                            activeTrackColor = EmeraldSuccess,
                            inactiveTrackColor = HairlineBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .testTag("fg_mask_brush_slider")
                    )

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberAccent,
                            contentColor = OnAmber
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("fg_mask_done_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Done (${initialStrokes.size} strokes)",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
