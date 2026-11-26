package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberDim
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanDim
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.OnAmber
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun RetouchBottomPanel(
    sourceBitmaps: List<Bitmap>,
    frameLabels: List<String> = emptyList(),
    selectedSourceIndex: Int,
    outputWidth: Int,
    outputHeight: Int,
    brushSizeFraction: Float,
    feather: Float,
    opacity: Float,
    isSaving: Boolean,
    onSelectSource: (Int) -> Unit,
    onBrushSizeChange: (Float) -> Unit,
    onFeatherChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onDoneRetouch: () -> Unit,
    onSaveFullRes: () -> Unit
) {
    Surface(
        color = CharcoalSurface.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Output Dimensions + Paint Source Header
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
                        tint = AmberAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "$outputWidth × $outputHeight px",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberAccent,
                        modifier = Modifier.testTag("retouch_dimensions_text")
                    )
                }
                Text(
                    text = "1-FINGER PAINT • 2-FINGER ZOOM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedSlate
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(sourceBitmaps) { index, bmp ->
                    val selected = index == selectedSourceIndex
                    val imageBmp = remember(bmp) { bmp.asImageBitmap() }
                    val labelTag = frameLabels.getOrNull(index)?.takeIf { it.isNotBlank() && it != "..." }
                    Surface(
                        onClick = { onSelectSource(index) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) CyanDim else ObsidianBackground,
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) CyanAccent else HairlineBorder
                        ),
                        modifier = Modifier
                            .size(width = if (labelTag != null) 74.dp else 62.dp, height = 48.dp)
                            .testTag("retouch_source_$index")
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                bitmap = imageBmp,
                                contentDescription = "Paint source frame #${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .background(ObsidianBackground.copy(alpha = 0.84f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = if (labelTag != null) "#${index + 1} $labelTag" else "#${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) CyanAccent else CrispWhite
                                )
                            }
                        }
                    }
                }
            }

            // Brush Size, Feather/Softness, and Opacity Sliders
            val approxPx = (brushSizeFraction * min(outputWidth, outputHeight)).roundToInt().coerceAtLeast(1)
            CompactBrushSlider(
                label = "Size",
                valueText = "${approxPx}px",
                value = brushSizeFraction,
                valueRange = 0.015f..0.18f,
                accentColor = AmberAccent,
                testTag = "retouch_size_slider",
                onValueChange = onBrushSizeChange
            )
            CompactBrushSlider(
                label = "Feather",
                valueText = "${(feather * 100f).roundToInt()}%",
                value = feather,
                valueRange = 0f..1f,
                accentColor = CyanAccent,
                testTag = "retouch_feather_slider",
                onValueChange = onFeatherChange
            )
            CompactBrushSlider(
                label = "Opacity",
                valueText = "${(opacity * 100f).roundToInt()}%",
                value = opacity,
                valueRange = 0.1f..1f,
                accentColor = AmberAccent,
                testTag = "retouch_opacity_slider",
                onValueChange = onOpacityChange
            )

            // Done / Save Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDoneRetouch,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("retouch_done_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.labelLarge,
                        color = CyanAccent
                    )
                }

                Button(
                    onClick = onSaveFullRes,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberAccent,
                        contentColor = OnAmber
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .weight(1.35f)
                        .height(48.dp)
                        .testTag("save_result_button")
                ) {
                    if (isSaving) {
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
                        text = if (isSaving) "Exporting..." else stringResource(R.string.save_full_res),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactBrushSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: androidx.compose.ui.graphics.Color,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MutedSlate,
            modifier = Modifier.width(52.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = HairlineBorder
            ),
            modifier = Modifier
                .weight(1f)
                .testTag(testTag)
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            modifier = Modifier.width(44.dp)
        )
    }
}
