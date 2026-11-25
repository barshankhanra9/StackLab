package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.MaxResolutionPreset
import com.example.domain.model.OutputFormat
import com.example.domain.model.OutputSettings
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberDim
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElevatedCharcoal
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MutedSlate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    settings: OutputSettings,
    onDismiss: () -> Unit,
    onSelectResolution: (MaxResolutionPreset) -> Unit,
    onSelectFormat: (OutputFormat) -> Unit,
    onQualityChange: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CharcoalSurface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Output & Export Settings",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Controls processing resolution cap and MediaStore export quality",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.PhotoSizeSelectLarge,
                    contentDescription = null,
                    tint = AmberAccent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MAX OUTPUT RESOLUTION",
                    style = MaterialTheme.typography.labelMedium,
                    color = AmberAccent
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MaxResolutionPreset.entries.forEach { preset ->
                    val selected = preset == settings.maxResolution
                    Surface(
                        onClick = { onSelectResolution(preset) },
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) AmberDim.copy(alpha = 0.7f) else ElevatedCharcoal,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected) AmberAccent else HairlineBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("resolution_option_${preset.name}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) AmberAccent else MaterialTheme.colorScheme.onSurface
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = AmberAccent
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = HairlineBorder)
            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.HighQuality,
                    contentDescription = null,
                    tint = CyanAccent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "EXPORT FORMAT & QUALITY",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanAccent
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutputFormat.entries.forEach { format ->
                    val selected = format == settings.outputFormat
                    Surface(
                        onClick = { onSelectFormat(format) },
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) CyanAccent.copy(alpha = 0.14f) else ElevatedCharcoal,
                        border = BorderStroke(
                            1.dp,
                            if (selected) CyanAccent else HairlineBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("format_option_${format.name}")
                    ) {
                        Text(
                            text = format.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) CyanAccent else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val isJpeg = settings.outputFormat == OutputFormat.JPEG
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isJpeg) "JPEG Compression Quality" else "Lossless PNG Compression",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isJpeg) MaterialTheme.colorScheme.onSurface else MutedSlate
                )
                Text(
                    text = if (isJpeg) "${settings.jpegQuality}%" else "100%",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isJpeg) AmberAccent else CyanAccent
                )
            }

            Slider(
                value = settings.jpegQuality.toFloat(),
                onValueChange = { onQualityChange(it.roundToInt()) },
                valueRange = 70f..100f,
                enabled = isJpeg,
                colors = SliderDefaults.colors(
                    thumbColor = AmberAccent,
                    activeTrackColor = AmberAccent,
                    inactiveTrackColor = HairlineBorder
                ),
                modifier = Modifier.testTag("jpeg_quality_slider")
            )
        }
    }
}
