package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MotionPhotosOff
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.AlignmentMode
import com.example.domain.model.BlendAlgorithm
import com.example.domain.model.ExposureBracket
import com.example.domain.model.FocusRegion
import com.example.domain.model.MaxResolutionPreset
import com.example.domain.model.StackFrame
import com.example.domain.model.StackMode
import com.example.ui.ProcessingUiState
import com.example.ui.StackLabUiState
import com.example.ui.components.ProcessingOverlay
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberDim
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanDim
import com.example.ui.theme.ElevatedCharcoal
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.OnAmber
import com.example.ui.theme.SubtleSurface
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    uiState: StackLabUiState,
    onNavigateBack: () -> Unit,
    onAddFrames: (List<Uri>) -> Unit,
    onSelectPreview: (Int) -> Unit,
    onToggleFrameEnabled: (Int) -> Unit,
    onAutoSort: () -> Unit,
    onRemoveFrame: (Int) -> Unit,
    onMoveFrame: (Int, Int) -> Unit,
    onAlignmentModeChange: (AlignmentMode) -> Unit,
    onBlendAlgorithmChange: (BlendAlgorithm) -> Unit,
    onEdgeSmoothingChange: (Int) -> Unit,
    onAutoCropBordersChange: (Boolean) -> Unit,
    onHdrContrastWeightChange: (Float) -> Unit,
    onHdrSaturationWeightChange: (Float) -> Unit,
    onHdrExposureWeightChange: (Float) -> Unit,
    onHdrDeghostingChange: (Boolean) -> Unit,
    onMaxResolutionChange: (MaxResolutionPreset) -> Unit,
    onStartStacking: () -> Unit,
    onCancelStacking: () -> Unit,
    onSnackbarConsumed: () -> Unit
) {
    val isHdrMode = uiState.activeMode == StackMode.HdrExposureBlend
    var advancedExpanded by rememberSaveable { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    val addPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onAddFrames(uris)
            }
        }
    )

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onSnackbarConsumed()
        }
    }

    Scaffold(
        containerColor = ObsidianBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianBackground,
                    titleContentColor = CrispWhite
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("workspace_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = CrispWhite
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isHdrMode) "HDR Prep" else "Stack Prep",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (isHdrMode) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CyanDim,
                                border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.55f))
                            ) {
                                Text(
                                    text = "HDR",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanAccent,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AmberDim,
                            border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.5f))
                        ) {
                            val enabledCount = uiState.enabledFramesCount
                            val totalCount = uiState.frames.size
                            val badgeText = if (enabledCount == totalCount) {
                                "$totalCount FRAMES"
                            } else {
                                "$enabledCount/$totalCount ACTIVE"
                            }
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            addPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("add_frames_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add",
                            style = MaterialTheme.typography.labelLarge,
                            color = CyanAccent
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onStartStacking,
                containerColor = if (uiState.canStartStacking) AmberAccent else ElevatedCharcoal,
                contentColor = if (uiState.canStartStacking) OnAmber else MutedSlate,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.AutoFixHigh,
                        contentDescription = null
                    )
                },
                text = {
                    Text(
                        text = if (isHdrMode) {
                            stringResource(R.string.blend_hdr)
                        } else {
                            stringResource(R.string.start_stacking)
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("start_stacking_button")
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Active Frame Inspection Stage
                ActiveFrameStage(
                    frames = uiState.frames,
                    selectedIndex = uiState.selectedPreviewIndex,
                    isHdrMode = isHdrMode,
                    onPickPhotos = {
                        addPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onToggleEnabled = onToggleFrameEnabled,
                    onMoveLeft = { idx -> onMoveFrame(idx, idx - 1) },
                    onMoveRight = { idx -> onMoveFrame(idx, idx + 1) }
                )

                // Horizontal Filmstrip + Analyzer Bar
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (isHdrMode) "EV BRACKET FILMSTRIP" else "FOCUS FILMSTRIP",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MutedSlate
                                )
                                if (uiState.isAnalyzingFocus) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = CyanAccent,
                                        strokeWidth = 1.5.dp
                                    )
                                }
                            }
                            Text(
                                text = if (isHdrMode) {
                                    "DARK → MID → BRIGHT • CHECKBOX TO INCLUDE"
                                } else {
                                    "NEAR → MID → FAR • CHECKBOX TO INCLUDE"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedSlate
                            )
                        }

                        if (uiState.frames.size >= 2) {
                            Surface(
                                onClick = onAutoSort,
                                enabled = !uiState.isAnalyzingFocus,
                                shape = RoundedCornerShape(10.dp),
                                color = CyanDim.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.55f)),
                                modifier = Modifier.testTag("auto_sort_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SwapVert,
                                        contentDescription = null,
                                        tint = CyanAccent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = if (isHdrMode) "Auto-Sort EV" else "Auto-Sort Focus",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyanAccent
                                    )
                                }
                            }
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(
                            items = uiState.frames,
                            key = { _, frame -> frame.id }
                        ) { index, frame ->
                            FilmstripFrameCard(
                                frame = frame,
                                index = index,
                                totalCount = uiState.frames.size,
                                isHdrMode = isHdrMode,
                                isSelected = index == uiState.selectedPreviewIndex,
                                onSelect = { onSelectPreview(index) },
                                onToggleEnabled = { onToggleFrameEnabled(index) },
                                onRemove = { onRemoveFrame(index) },
                                onMoveLeft = { onMoveFrame(index, index - 1) },
                                onMoveRight = { onMoveFrame(index, index + 1) }
                            )
                        }

                        item {
                            Surface(
                                onClick = {
                                    addPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = SubtleSurface,
                                border = BorderStroke(1.dp, HairlineBorder),
                                modifier = Modifier
                                    .width(86.dp)
                                    .height(126.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = stringResource(R.string.cd_add_frames),
                                        tint = CyanAccent
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Add",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CyanAccent
                                    )
                                }
                            }
                        }
                    }
                }

                // Collapsible Advanced Stack Settings Card
                AdvancedStackSettingsCard(
                    expanded = advancedExpanded,
                    isHdrMode = isHdrMode,
                    onToggleExpanded = { advancedExpanded = !advancedExpanded },
                    maxResolution = uiState.outputSettings.maxResolution,
                    alignmentMode = uiState.alignmentMode,
                    blendAlgorithm = uiState.blendAlgorithm,
                    edgeSmoothingRadius = uiState.edgeSmoothingRadius,
                    autoCropBorders = uiState.autoCropBorders,
                    hdrContrastWeight = uiState.hdrContrastWeight,
                    hdrSaturationWeight = uiState.hdrSaturationWeight,
                    hdrExposureWeight = uiState.hdrExposureWeight,
                    hdrDeghostingEnabled = uiState.hdrDeghostingEnabled,
                    onMaxResolutionChange = onMaxResolutionChange,
                    onAlignmentModeChange = onAlignmentModeChange,
                    onBlendAlgorithmChange = onBlendAlgorithmChange,
                    onEdgeSmoothingChange = onEdgeSmoothingChange,
                    onAutoCropBordersChange = onAutoCropBordersChange,
                    onHdrContrastWeightChange = onHdrContrastWeightChange,
                    onHdrSaturationWeightChange = onHdrSaturationWeightChange,
                    onHdrExposureWeightChange = onHdrExposureWeightChange,
                    onHdrDeghostingChange = onHdrDeghostingChange
                )
            }
        }
    }

    val processing = uiState.processingState
    if (processing is ProcessingUiState.Running) {
        ProcessingOverlay(
            progress = processing.progress,
            onCancel = onCancelStacking
        )
    }
}

@Composable
private fun ActiveFrameStage(
    frames: List<StackFrame>,
    selectedIndex: Int,
    isHdrMode: Boolean,
    onPickPhotos: () -> Unit,
    onToggleEnabled: (Int) -> Unit,
    onMoveLeft: (Int) -> Unit,
    onMoveRight: (Int) -> Unit
) {
    val activeFrame = frames.getOrNull(selectedIndex)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = SubtleSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (activeFrame == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(224.dp)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = null,
                        tint = if (isHdrMode) CyanAccent else MutedSlate,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = if (isHdrMode) {
                            "Select 2+ bracketed exposures (Dark, Mid, Bright) to blend HDR"
                        } else {
                            "Add at least 2 photos to begin focus stacking"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSlate
                    )
                    Button(
                        onClick = onPickPhotos,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberAccent,
                            contentColor = OnAmber
                        ),
                        modifier = Modifier.testTag("workspace_pick_photos_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isHdrMode) "Select Bracketed Photos" else "Select Photos",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        } else {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .background(ObsidianBackground)
                ) {
                    AsyncImage(
                        model = activeFrame.uri,
                        contentDescription = "Preview frame ${selectedIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (activeFrame.isEnabled) 1f else 0.45f)
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ObsidianBackground.copy(alpha = 0.82f),
                            border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = "#${selectedIndex + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = AmberAccent,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }

                        if (activeFrame.isAnalyzed) {
                            val badgeLabel = if (isHdrMode) {
                                activeFrame.exposureBracket.label.uppercase()
                            } else {
                                activeFrame.focusRegion.label.uppercase()
                            }
                            val badgeColor = if (isHdrMode) {
                                exposureBracketColor(activeFrame.exposureBracket)
                            } else {
                                focusRegionColor(activeFrame.focusRegion)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ObsidianBackground.copy(alpha = 0.82f),
                                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = badgeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianBackground.copy(alpha = 0.82f),
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "${activeFrame.width} × ${activeFrame.height}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CrispWhite,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }

                    if (!activeFrame.isEnabled) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ObsidianBackground.copy(alpha = 0.88f),
                            border = BorderStroke(1.dp, MutedSlate),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Text(
                                text = "EXCLUDED FROM STACK",
                                style = MaterialTheme.typography.labelMedium,
                                color = MutedSlate,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeFrame.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CrispWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Surface(
                            onClick = { onToggleEnabled(selectedIndex) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (activeFrame.isEnabled) CyanDim else ElevatedCharcoal,
                            border = BorderStroke(
                                1.dp,
                                if (activeFrame.isEnabled) CyanAccent.copy(alpha = 0.6f) else HairlineBorder
                            )
                        ) {
                            Text(
                                text = if (activeFrame.isEnabled) "Included" else "Excluded",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (activeFrame.isEnabled) CyanAccent else MutedSlate,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        IconButton(
                            onClick = { onMoveLeft(selectedIndex) },
                            enabled = selectedIndex > 0,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = stringResource(R.string.cd_move_frame_left),
                                tint = if (selectedIndex > 0) CyanAccent else MutedSlate.copy(alpha = 0.4f)
                            )
                        }
                        IconButton(
                            onClick = { onMoveRight(selectedIndex) },
                            enabled = selectedIndex < frames.lastIndex,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.cd_move_frame_right),
                                tint = if (selectedIndex < frames.lastIndex) CyanAccent else MutedSlate.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilmstripFrameCard(
    frame: StackFrame,
    index: Int,
    totalCount: Int,
    isHdrMode: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleEnabled: () -> Unit,
    onRemove: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit
) {
    val badgeLabel = if (isHdrMode) frame.exposureBracket.label else frame.focusRegion.label
    val badgeColor = if (isHdrMode) {
        exposureBracketColor(frame.exposureBracket)
    } else {
        focusRegionColor(frame.focusRegion)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CharcoalSurface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = when {
                isSelected -> AmberAccent
                !frame.isEnabled -> HairlineBorder.copy(alpha = 0.5f)
                else -> HairlineBorder
            }
        ),
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onSelect)
            .testTag("filmstrip_frame_$index")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .background(ObsidianBackground)
            ) {
                AsyncImage(
                    model = frame.uri,
                    contentDescription = "Frame #${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (frame.isEnabled) 1f else 0.38f)
                )

                // Top-left index badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ObsidianBackground.copy(alpha = 0.86f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                ) {
                    Text(
                        text = "#${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) AmberAccent else CrispWhite,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Bottom-left focus or brightness badge (Near/Mid/Far or Dark/Mid/Bright)
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = ObsidianBackground.copy(alpha = 0.86f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp)
                        .testTag("filmstrip_badge_$index")
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }

                // Top-right remove button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(ObsidianBackground.copy(alpha = 0.84f))
                        .border(1.dp, HairlineBorder, CircleShape)
                        .clickable(onClick = onRemove)
                        .testTag("remove_frame_$index"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_remove_frame),
                        tint = CrispWhite,
                        modifier = Modifier.size(13.dp)
                    )
                }

                if (!frame.isEnabled) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = ObsidianBackground.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text(
                            text = "OFF",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedSlate,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Bottom card control bar: Include Checkbox + Reorder Arrows
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(ElevatedCharcoal),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxSize()
                        .clickable(onClick = onToggleEnabled)
                        .testTag("toggle_frame_$index"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (frame.isEnabled) CyanAccent else Color.Transparent)
                            .border(
                                width = 1.5.dp,
                                color = if (frame.isEnabled) CyanAccent else MutedSlate,
                                shape = RoundedCornerShape(4.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (frame.isEnabled) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.cd_toggle_frame),
                                tint = ObsidianBackground,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(HairlineBorder)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(enabled = index > 0, onClick = onMoveLeft)
                        .testTag("move_left_$index"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.cd_move_frame_left),
                        tint = if (index > 0) CrispWhite else MutedSlate.copy(alpha = 0.35f),
                        modifier = Modifier.size(17.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(HairlineBorder)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(enabled = index < totalCount - 1, onClick = onMoveRight)
                        .testTag("move_right_$index"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.cd_move_frame_right),
                        tint = if (index < totalCount - 1) CrispWhite else MutedSlate.copy(alpha = 0.35f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

private fun focusRegionColor(region: FocusRegion): Color {
    return when (region) {
        FocusRegion.NEAR -> CyanAccent
        FocusRegion.MID -> AmberAccent
        FocusRegion.FAR -> EmeraldSuccess
        FocusRegion.ANALYZING -> MutedSlate
    }
}

private fun exposureBracketColor(bracket: ExposureBracket): Color {
    return when (bracket) {
        ExposureBracket.DARK -> CyanAccent
        ExposureBracket.MID -> EmeraldSuccess
        ExposureBracket.BRIGHT -> AmberAccent
        ExposureBracket.ANALYZING -> MutedSlate
    }
}

@Composable
private fun AdvancedStackSettingsCard(
    expanded: Boolean,
    isHdrMode: Boolean,
    onToggleExpanded: () -> Unit,
    maxResolution: MaxResolutionPreset,
    alignmentMode: AlignmentMode,
    blendAlgorithm: BlendAlgorithm,
    edgeSmoothingRadius: Int,
    autoCropBorders: Boolean,
    hdrContrastWeight: Float,
    hdrSaturationWeight: Float,
    hdrExposureWeight: Float,
    hdrDeghostingEnabled: Boolean,
    onMaxResolutionChange: (MaxResolutionPreset) -> Unit,
    onAlignmentModeChange: (AlignmentMode) -> Unit,
    onBlendAlgorithmChange: (BlendAlgorithm) -> Unit,
    onEdgeSmoothingChange: (Int) -> Unit,
    onAutoCropBordersChange: (Boolean) -> Unit,
    onHdrContrastWeightChange: (Float) -> Unit,
    onHdrSaturationWeightChange: (Float) -> Unit,
    onHdrExposureWeightChange: (Float) -> Unit,
    onHdrDeghostingChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CharcoalSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
                    .testTag("toggle_advanced_settings"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = AmberAccent
                    )
                    Column {
                        Text(
                            text = "Advanced Stack Settings",
                            style = MaterialTheme.typography.titleMedium,
                            color = CrispWhite
                        )
                        val alignShort = if (alignmentMode == AlignmentMode.AUTO_HOMOGRAPHY) "ECC + ORB" else "Tripod"
                        val summaryLine = if (isHdrMode) {
                            val deghostTag = if (hdrDeghostingEnabled) " • Deghost" else ""
                            "${maxResolution.shortLabel} • $alignShort • C:${formatWeight(hdrContrastWeight)} S:${formatWeight(hdrSaturationWeight)} E:${formatWeight(hdrExposureWeight)}$deghostTag"
                        } else {
                            val blendShort = if (blendAlgorithm == BlendAlgorithm.PYRAMID_PMAX) "PMax" else "DMap"
                            val cropShort = if (autoCropBorders) "Auto-Crop" else "Full Canvas"
                            "${maxResolution.shortLabel} • $alignShort • $blendShort • ${edgeSmoothingRadius * 2 + 1}px • $cropShort"
                        }
                        Text(
                            text = summaryLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanAccent
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse settings" else "Expand settings",
                    tint = CrispWhite
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HorizontalDivider(color = HairlineBorder)

                    // Output Resolution Mode
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "OUTPUT RESOLUTION",
                                style = MaterialTheme.typography.labelMedium,
                                color = MutedSlate
                            )
                            Text(
                                text = maxResolution.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberAccent
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                MaxResolutionPreset.PROXY_1600,
                                MaxResolutionPreset.UHD_4K,
                                MaxResolutionPreset.ORIGINAL
                            ).forEach { preset ->
                                val selected = preset == maxResolution
                                val chipLabel = when (preset) {
                                    MaxResolutionPreset.PROXY_1600 -> "1600px"
                                    MaxResolutionPreset.UHD_4K -> "4000px"
                                    MaxResolutionPreset.ORIGINAL -> "Full-Res"
                                    MaxResolutionPreset.HD_2K -> "2048px"
                                }
                                Surface(
                                    onClick = { onMaxResolutionChange(preset) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) AmberDim else ElevatedCharcoal,
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) AmberAccent else HairlineBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("workspace_resolution_${preset.name}")
                                ) {
                                    Text(
                                        text = chipLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) AmberAccent else CrispWhite,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Alignment Mode
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "ALIGNMENT MODE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedSlate
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AlignmentMode.entries.forEach { mode ->
                                val selected = mode == alignmentMode
                                Surface(
                                    onClick = { onAlignmentModeChange(mode) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) AmberDim else ElevatedCharcoal,
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) AmberAccent else HairlineBorder
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("alignment_${mode.name}")
                                ) {
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) AmberAccent else CrispWhite,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = alignmentMode.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isHdrMode) {
                        // Contrast Weight Slider
                        HdrWeightSlider(
                            title = "CONTRAST WEIGHT",
                            subtitle = "Controls local detail punch",
                            value = hdrContrastWeight,
                            accentColor = AmberAccent,
                            testTag = "hdr_contrast_slider",
                            onValueChange = onHdrContrastWeightChange
                        )

                        // Saturation Weight Slider
                        HdrWeightSlider(
                            title = "SATURATION WEIGHT",
                            subtitle = "Controls color richness in blended shadows & highlights",
                            value = hdrSaturationWeight,
                            accentColor = CyanAccent,
                            testTag = "hdr_saturation_slider",
                            onValueChange = onHdrSaturationWeightChange
                        )

                        // Exposure Balance Slider
                        HdrWeightSlider(
                            title = "EXPOSURE BALANCE",
                            subtitle = "Favors well-exposed midtones over clipped highlights/shadows",
                            value = hdrExposureWeight,
                            accentColor = AmberAccent,
                            testTag = "hdr_exposure_slider",
                            onValueChange = onHdrExposureWeightChange
                        )

                        HorizontalDivider(color = HairlineBorder)

                        // Deghosting Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onHdrDeghostingChange(!hdrDeghostingEnabled) }
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MotionPhotosOff,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Deghosting",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = CrispWhite
                                    )
                                    Text(
                                        text = "Suppress moving subjects (leaves, water, people) across brackets",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = hdrDeghostingEnabled,
                                onCheckedChange = onHdrDeghostingChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = OnAmber,
                                    checkedTrackColor = AmberAccent,
                                    uncheckedThumbColor = MutedSlate,
                                    uncheckedTrackColor = ElevatedCharcoal
                                ),
                                modifier = Modifier.testTag("hdr_deghosting_switch")
                            )
                        }
                    } else {
                        // Blend Algorithm
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "BLEND ALGORITHM",
                                style = MaterialTheme.typography.labelMedium,
                                color = MutedSlate
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                BlendAlgorithm.entries.forEach { algo ->
                                    val selected = algo == blendAlgorithm
                                    Surface(
                                        onClick = { onBlendAlgorithmChange(algo) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) CyanDim else ElevatedCharcoal,
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) CyanAccent else HairlineBorder
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("blend_${algo.name}")
                                    ) {
                                        Text(
                                            text = algo.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (selected) CyanAccent else CrispWhite,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = blendAlgorithm.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Edge Smoothing Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "EDGE SMOOTHING",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MutedSlate
                                )
                                val kernel = edgeSmoothingRadius * 2 + 1
                                Text(
                                    text = "${kernel}×${kernel} px Gaussian",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AmberAccent
                                )
                            }
                            Slider(
                                value = edgeSmoothingRadius.toFloat(),
                                onValueChange = { onEdgeSmoothingChange(it.roundToInt()) },
                                valueRange = 1f..10f,
                                steps = 8,
                                colors = SliderDefaults.colors(
                                    thumbColor = AmberAccent,
                                    activeTrackColor = AmberAccent,
                                    inactiveTrackColor = HairlineBorder
                                ),
                                modifier = Modifier.testTag("edge_smoothing_slider")
                            )
                        }
                    }

                    HorizontalDivider(color = HairlineBorder)

                    // Auto-Crop Borders Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAutoCropBordersChange(!autoCropBorders) }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Crop,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Auto-Crop Borders",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CrispWhite
                                )
                                Text(
                                    text = "Trim overlapping warp borders to remove black edges",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = autoCropBorders,
                            onCheckedChange = onAutoCropBordersChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnAmber,
                                checkedTrackColor = AmberAccent,
                                uncheckedThumbColor = MutedSlate,
                                uncheckedTrackColor = ElevatedCharcoal
                            ),
                            modifier = Modifier.testTag("auto_crop_switch")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HdrWeightSlider(
    title: String,
    subtitle: String,
    value: Float,
    accentColor: Color,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MutedSlate
            )
            Text(
                text = formatWeight(value),
                style = MaterialTheme.typography.labelMedium,
                color = accentColor
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = { onValueChange(((it * 20f).roundToInt() / 20f).coerceIn(0f, 2f)) },
            valueRange = 0f..2f,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = HairlineBorder
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

private fun formatWeight(value: Float): String {
    return String.format(Locale.US, "%.2f", value)
}
