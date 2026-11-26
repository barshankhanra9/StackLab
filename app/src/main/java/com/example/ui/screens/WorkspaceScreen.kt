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
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.Anchor
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MotionPhotosOff
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.AlignmentMode
import com.example.domain.model.AstroAlignmentMode
import com.example.domain.model.AstroFrameType
import com.example.domain.model.AstroStackAlgorithm
import com.example.domain.model.BlendAlgorithm
import com.example.domain.model.ExposureBracket
import com.example.domain.model.FocusRegion
import com.example.domain.model.ForegroundMaskStroke
import com.example.domain.model.HdrStylePreset
import com.example.domain.model.MaxResolutionPreset
import com.example.domain.model.StackFrame
import com.example.domain.model.StackMode
import com.example.engine.AstroStackProcessor
import com.example.ui.ProcessingUiState
import com.example.ui.StackLabUiState
import com.example.ui.components.ForegroundMaskPainterDialog
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
import kotlin.math.min
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
    onSelectHdrPreset: (HdrStylePreset) -> Unit,
    onSelectHdrAnchorFrame: (Int) -> Unit,
    onAstroFrameTypeChange: (Int, AstroFrameType) -> Unit,
    onAstroAlignmentModeChange: (AstroAlignmentMode) -> Unit,
    onAstroStackAlgorithmChange: (AstroStackAlgorithm) -> Unit,
    onAstroSigmaThresholdChange: (Float) -> Unit,
    onAstroFreezeGroundChange: (Boolean) -> Unit,
    onAddAstroFreezeStroke: (ForegroundMaskStroke) -> Unit,
    onUndoAstroFreezeStroke: () -> Unit,
    onClearAstroFreezeStrokes: () -> Unit,
    onMaxResolutionChange: (MaxResolutionPreset) -> Unit,
    onStartStacking: () -> Unit,
    onCancelStacking: () -> Unit,
    onSnackbarConsumed: () -> Unit
) {
    val isHdrMode = uiState.activeMode == StackMode.HdrExposureBlend
    val isAstroMode = uiState.activeMode == StackMode.AstroNoiseStack
    var advancedExpanded by rememberSaveable { mutableStateOf(true) }
    var showFgMaskDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val astroRefFrameId = remember(uiState.frames, isAstroMode) {
        if (isAstroMode) AstroStackProcessor.resolveReferenceLightFrameId(uiState.frames) else null
    }
    val astroRefFrame = remember(uiState.frames, astroRefFrameId) {
        uiState.frames.firstOrNull { it.id == astroRefFrameId }
            ?: uiState.frames.firstOrNull { it.isEnabled && it.astroFrameType == AstroFrameType.LIGHT }
    }

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
                            text = when {
                                isHdrMode -> "HDR Prep"
                                isAstroMode -> "Astro Prep"
                                else -> "Stack Prep"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (isHdrMode || isAstroMode) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CyanDim,
                                border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.55f))
                            ) {
                                Text(
                                    text = if (isAstroMode) "ASTRO" else "HDR",
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
                            val badgeText = when {
                                isAstroMode -> {
                                    val lights = uiState.enabledLightFramesCount
                                    val darks = uiState.enabledDarkFramesCount
                                    if (darks > 0) "${lights}L • ${darks}D" else "${lights} LIGHT"
                                }
                                enabledCount == totalCount -> "$totalCount FRAMES"
                                else -> "$enabledCount/$totalCount ACTIVE"
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
                        imageVector = if (isAstroMode) Icons.Rounded.NightsStay else Icons.Rounded.AutoFixHigh,
                        contentDescription = null
                    )
                },
                text = {
                    Text(
                        text = when {
                            isHdrMode -> stringResource(R.string.blend_hdr)
                            isAstroMode -> stringResource(R.string.stack_astro)
                            else -> stringResource(R.string.start_stacking)
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
                    isAstroMode = isAstroMode,
                    hdrAnchorFrameId = uiState.hdrAnchorFrameId,
                    astroRefFrameId = astroRefFrameId,
                    astroFreezeGroundEnabled = uiState.astroFreezeGroundEnabled,
                    astroFreezeGroundStrokes = uiState.astroFreezeGroundStrokes,
                    onPickPhotos = {
                        addPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onToggleEnabled = onToggleFrameEnabled,
                    onSetAnchor = onSelectHdrAnchorFrame,
                    onAstroFrameTypeChange = onAstroFrameTypeChange,
                    onOpenFreezeGroundPainter = {
                        if (astroRefFrame != null) showFgMaskDialog = true
                    },
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
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = when {
                                        isHdrMode -> "EV BRACKET FILMSTRIP"
                                        isAstroMode -> "ASTRO CALIBRATION FILMSTRIP"
                                        else -> "FOCUS FILMSTRIP"
                                    },
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
                                text = when {
                                    isHdrMode -> "DARK → MID → BRIGHT • ANCHOR = DEGHOST REF"
                                    isAstroMode -> "TAP BADGE TO TAG LIGHT (SKY) OR DARK (LENS-CAP)"
                                    else -> "NEAR → MID → FAR • CHECKBOX TO INCLUDE"
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
                                        text = when {
                                            isHdrMode -> "Auto-Sort EV"
                                            isAstroMode -> "Lights First"
                                            else -> "Auto-Sort Focus"
                                        },
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
                                isAstroMode = isAstroMode,
                                isAnchor = isHdrMode && frame.id == uiState.hdrAnchorFrameId,
                                isAstroRef = isAstroMode && frame.id == astroRefFrameId,
                                isSelected = index == uiState.selectedPreviewIndex,
                                onSelect = { onSelectPreview(index) },
                                onToggleEnabled = { onToggleFrameEnabled(index) },
                                onSetAnchor = { onSelectHdrAnchorFrame(index) },
                                onAstroFrameTypeChange = { type -> onAstroFrameTypeChange(index, type) },
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
                    isAstroMode = isAstroMode,
                    onToggleExpanded = { advancedExpanded = !advancedExpanded },
                    maxResolution = uiState.outputSettings.maxResolution,
                    alignmentMode = uiState.alignmentMode,
                    blendAlgorithm = uiState.blendAlgorithm,
                    edgeSmoothingRadius = uiState.edgeSmoothingRadius,
                    autoCropBorders = uiState.autoCropBorders,
                    selectedHdrPreset = uiState.selectedHdrPreset,
                    hdrContrastWeight = uiState.hdrContrastWeight,
                    hdrSaturationWeight = uiState.hdrSaturationWeight,
                    hdrExposureWeight = uiState.hdrExposureWeight,
                    hdrDeghostingEnabled = uiState.hdrDeghostingEnabled,
                    astroAlignmentMode = uiState.astroAlignmentMode,
                    astroStackAlgorithm = uiState.astroStackAlgorithm,
                    astroSigmaThreshold = uiState.astroSigmaThreshold,
                    astroFreezeGroundEnabled = uiState.astroFreezeGroundEnabled,
                    astroFreezeGroundStrokeCount = uiState.astroFreezeGroundStrokes.size,
                    canPaintFreezeGround = astroRefFrame != null,
                    onMaxResolutionChange = onMaxResolutionChange,
                    onAlignmentModeChange = onAlignmentModeChange,
                    onBlendAlgorithmChange = onBlendAlgorithmChange,
                    onEdgeSmoothingChange = onEdgeSmoothingChange,
                    onAutoCropBordersChange = onAutoCropBordersChange,
                    onSelectHdrPreset = onSelectHdrPreset,
                    onHdrContrastWeightChange = onHdrContrastWeightChange,
                    onHdrSaturationWeightChange = onHdrSaturationWeightChange,
                    onHdrExposureWeightChange = onHdrExposureWeightChange,
                    onHdrDeghostingChange = onHdrDeghostingChange,
                    onAstroAlignmentModeChange = onAstroAlignmentModeChange,
                    onAstroStackAlgorithmChange = onAstroStackAlgorithmChange,
                    onAstroSigmaThresholdChange = onAstroSigmaThresholdChange,
                    onAstroFreezeGroundChange = { enabled ->
                        onAstroFreezeGroundChange(enabled)
                        if (enabled && uiState.astroFreezeGroundStrokes.isEmpty() && astroRefFrame != null) {
                            showFgMaskDialog = true
                        }
                    },
                    onOpenFreezeGroundPainter = {
                        if (astroRefFrame != null) showFgMaskDialog = true
                    },
                    onClearFreezeGroundStrokes = onClearAstroFreezeStrokes
                )
            }
        }
    }

    if (showFgMaskDialog && astroRefFrame != null) {
        ForegroundMaskPainterDialog(
            referenceFrameUri = astroRefFrame.uri,
            frameWidth = astroRefFrame.width,
            frameHeight = astroRefFrame.height,
            initialStrokes = uiState.astroFreezeGroundStrokes,
            onAddStroke = onAddAstroFreezeStroke,
            onUndoStroke = onUndoAstroFreezeStroke,
            onClearStrokes = onClearAstroFreezeStrokes,
            onDismiss = { showFgMaskDialog = false }
        )
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
    isAstroMode: Boolean,
    hdrAnchorFrameId: String?,
    astroRefFrameId: String?,
    astroFreezeGroundEnabled: Boolean,
    astroFreezeGroundStrokes: List<ForegroundMaskStroke>,
    onPickPhotos: () -> Unit,
    onToggleEnabled: (Int) -> Unit,
    onSetAnchor: (Int) -> Unit,
    onAstroFrameTypeChange: (Int, AstroFrameType) -> Unit,
    onOpenFreezeGroundPainter: () -> Unit,
    onMoveLeft: (Int) -> Unit,
    onMoveRight: (Int) -> Unit
) {
    val activeFrame = frames.getOrNull(selectedIndex)
    val isActiveAnchor = isHdrMode && activeFrame != null && activeFrame.id == hdrAnchorFrameId
    val isAstroRef = isAstroMode && activeFrame != null && activeFrame.id == astroRefFrameId
    var showTypeMenu by remember { mutableStateOf(false) }

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
                        imageVector = if (isAstroMode) Icons.Rounded.NightsStay else Icons.Rounded.Layers,
                        contentDescription = null,
                        tint = if (isHdrMode || isAstroMode) CyanAccent else MutedSlate,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = when {
                            isHdrMode -> "Select 2+ bracketed exposures (Dark, Mid, Bright) to blend HDR"
                            isAstroMode -> "Select 2+ night sky / low-light shots (plus optional Dark frames)"
                            else -> "Add at least 2 photos to begin focus stacking"
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
                            text = when {
                                isHdrMode -> "Select Bracketed Photos"
                                isAstroMode -> "Select Astro Frames"
                                else -> "Select Photos"
                            },
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

                    if (isAstroMode && astroFreezeGroundEnabled && isAstroRef && astroFreezeGroundStrokes.isNotEmpty()) {
                        val imgW = activeFrame.width.toFloat().coerceAtLeast(1f)
                        val imgH = activeFrame.height.toFloat().coerceAtLeast(1f)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val fitScale = min(size.width / imgW, size.height / imgH)
                            val fittedW = imgW * fitScale
                            val fittedH = imgH * fitScale
                            val fitLeft = (size.width - fittedW) / 2f
                            val fitTop = (size.height - fittedH) / 2f
                            val minFitted = min(fittedW, fittedH)
                            for (stroke in astroFreezeGroundStrokes) {
                                if (stroke.points.isEmpty()) continue
                                val r = (stroke.brushSizeFraction * minFitted * 0.5f).coerceAtLeast(2f)
                                if (stroke.points.size == 1) {
                                    val p = stroke.points[0]
                                    drawCircle(
                                        color = EmeraldSuccess.copy(alpha = 0.36f),
                                        radius = r,
                                        center = Offset(fitLeft + p.u * fittedW, fitTop + p.v * fittedH)
                                    )
                                } else {
                                    val path = Path()
                                    stroke.points.forEachIndexed { idx, pt ->
                                        val x = fitLeft + pt.u * fittedW
                                        val y = fitTop + pt.v * fittedH
                                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                    }
                                    drawPath(
                                        path = path,
                                        color = EmeraldSuccess.copy(alpha = 0.36f),
                                        style = Stroke(
                                            width = r * 2f,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }
                        }
                    }

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

                        if (isAstroMode) {
                            val typeColor = astroFrameTypeColor(activeFrame.astroFrameType)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ObsidianBackground.copy(alpha = 0.82f),
                                border = BorderStroke(1.dp, typeColor.copy(alpha = 0.65f))
                            ) {
                                Text(
                                    text = activeFrame.astroFrameType.shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = typeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (isAstroRef) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ObsidianBackground.copy(alpha = 0.86f),
                                    border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.7f))
                                ) {
                                    Text(
                                        text = "REF",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldSuccess,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        } else if (activeFrame.isAnalyzed) {
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

                        if (isActiveAnchor) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AmberDim.copy(alpha = 0.92f),
                                border = BorderStroke(1.dp, AmberAccent)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Anchor,
                                        contentDescription = null,
                                        tint = AmberAccent,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "ANCHOR",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AmberAccent
                                    )
                                }
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
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isHdrMode) {
                            Surface(
                                onClick = { onSetAnchor(selectedIndex) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isActiveAnchor) AmberDim else ElevatedCharcoal,
                                border = BorderStroke(
                                    1.dp,
                                    if (isActiveAnchor) AmberAccent else HairlineBorder
                                ),
                                modifier = Modifier.testTag("stage_set_anchor_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Anchor,
                                        contentDescription = stringResource(R.string.cd_set_anchor),
                                        tint = if (isActiveAnchor) AmberAccent else MutedSlate,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (isActiveAnchor) "Anchor" else "Set Anchor",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isActiveAnchor) AmberAccent else MutedSlate
                                    )
                                }
                            }
                        }

                        if (isAstroMode) {
                            Box {
                                val typeColor = astroFrameTypeColor(activeFrame.astroFrameType)
                                Surface(
                                    onClick = { showTypeMenu = true },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (activeFrame.astroFrameType == AstroFrameType.DARK) AmberDim else CyanDim,
                                    border = BorderStroke(1.dp, typeColor.copy(alpha = 0.65f)),
                                    modifier = Modifier.testTag("stage_astro_type_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (activeFrame.astroFrameType == AstroFrameType.DARK) {
                                                Icons.Rounded.DarkMode
                                            } else {
                                                Icons.Rounded.WbSunny
                                            },
                                            contentDescription = stringResource(R.string.cd_tag_astro_frame),
                                            tint = typeColor,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = activeFrame.astroFrameType.shortLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = typeColor
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showTypeMenu,
                                    onDismissRequest = { showTypeMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Light Frame (Main Sky / Night)") },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = CyanAccent)
                                        },
                                        onClick = {
                                            showTypeMenu = false
                                            onAstroFrameTypeChange(selectedIndex, AstroFrameType.LIGHT)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Dark Frame (Lens-Cap-On)") },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.DarkMode, contentDescription = null, tint = AmberAccent)
                                        },
                                        onClick = {
                                            showTypeMenu = false
                                            onAstroFrameTypeChange(selectedIndex, AstroFrameType.DARK)
                                        }
                                    )
                                }
                            }

                            if (astroFreezeGroundEnabled) {
                                Surface(
                                    onClick = onOpenFreezeGroundPainter,
                                    shape = RoundedCornerShape(8.dp),
                                    color = EmeraldSuccess.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.65f)),
                                    modifier = Modifier.testTag("stage_paint_ground_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Brush,
                                            contentDescription = "Paint Freeze Ground mask",
                                            tint = EmeraldSuccess,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Mask",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldSuccess
                                        )
                                    }
                                }
                            }
                        }

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
    isAstroMode: Boolean,
    isAnchor: Boolean,
    isAstroRef: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleEnabled: () -> Unit,
    onSetAnchor: () -> Unit,
    onAstroFrameTypeChange: (AstroFrameType) -> Unit,
    onRemove: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit
) {
    val badgeLabel = when {
        isHdrMode -> frame.exposureBracket.label
        isAstroMode -> frame.astroFrameType.shortLabel
        else -> frame.focusRegion.label
    }
    val badgeColor = when {
        isHdrMode -> exposureBracketColor(frame.exposureBracket)
        isAstroMode -> astroFrameTypeColor(frame.astroFrameType)
        else -> focusRegionColor(frame.focusRegion)
    }
    var showAstroTypeMenu by remember { mutableStateOf(false) }

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

                // Bottom-left focus, EV bracket, or Astro Light/Dark calibration tag (tappable in Astro mode)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = ObsidianBackground.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.65f)),
                        modifier = Modifier
                            .then(
                                if (isAstroMode) {
                                    Modifier.clickable { showAstroTypeMenu = true }
                                } else {
                                    Modifier
                                }
                            )
                            .testTag("filmstrip_badge_$index")
                    ) {
                        Text(
                            text = if (isAstroMode) "$badgeLabel ▾" else badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    if (isAstroMode) {
                        DropdownMenu(
                            expanded = showAstroTypeMenu,
                            onDismissRequest = { showAstroTypeMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Light Frame (Sky)") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = CyanAccent)
                                },
                                onClick = {
                                    showAstroTypeMenu = false
                                    onAstroFrameTypeChange(AstroFrameType.LIGHT)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Dark Frame (Lens-Cap)") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.DarkMode, contentDescription = null, tint = AmberAccent)
                                },
                                onClick = {
                                    showAstroTypeMenu = false
                                    onAstroFrameTypeChange(AstroFrameType.DARK)
                                }
                            )
                        }
                    }
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

                // Bottom-right Deghost Anchor button (HDR mode) or Astro REF badge
                if (isHdrMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(
                                if (isAnchor) AmberAccent else ObsidianBackground.copy(alpha = 0.84f)
                            )
                            .border(
                                1.dp,
                                if (isAnchor) AmberAccent else HairlineBorder,
                                CircleShape
                            )
                            .clickable(onClick = onSetAnchor)
                            .testTag("set_anchor_$index"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Anchor,
                            contentDescription = stringResource(R.string.cd_set_anchor),
                            tint = if (isAnchor) OnAmber else CrispWhite.copy(alpha = 0.78f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else if (isAstroRef) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = ObsidianBackground.copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                    ) {
                        Text(
                            text = "REF",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldSuccess,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
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

private fun astroFrameTypeColor(type: AstroFrameType): Color {
    return when (type) {
        AstroFrameType.LIGHT -> CyanAccent
        AstroFrameType.DARK -> AmberAccent
    }
}

@Composable
private fun AdvancedStackSettingsCard(
    expanded: Boolean,
    isHdrMode: Boolean,
    isAstroMode: Boolean,
    onToggleExpanded: () -> Unit,
    maxResolution: MaxResolutionPreset,
    alignmentMode: AlignmentMode,
    blendAlgorithm: BlendAlgorithm,
    edgeSmoothingRadius: Int,
    autoCropBorders: Boolean,
    selectedHdrPreset: HdrStylePreset?,
    hdrContrastWeight: Float,
    hdrSaturationWeight: Float,
    hdrExposureWeight: Float,
    hdrDeghostingEnabled: Boolean,
    astroAlignmentMode: AstroAlignmentMode,
    astroStackAlgorithm: AstroStackAlgorithm,
    astroSigmaThreshold: Float,
    astroFreezeGroundEnabled: Boolean,
    astroFreezeGroundStrokeCount: Int,
    canPaintFreezeGround: Boolean,
    onMaxResolutionChange: (MaxResolutionPreset) -> Unit,
    onAlignmentModeChange: (AlignmentMode) -> Unit,
    onBlendAlgorithmChange: (BlendAlgorithm) -> Unit,
    onEdgeSmoothingChange: (Int) -> Unit,
    onAutoCropBordersChange: (Boolean) -> Unit,
    onSelectHdrPreset: (HdrStylePreset) -> Unit,
    onHdrContrastWeightChange: (Float) -> Unit,
    onHdrSaturationWeightChange: (Float) -> Unit,
    onHdrExposureWeightChange: (Float) -> Unit,
    onHdrDeghostingChange: (Boolean) -> Unit,
    onAstroAlignmentModeChange: (AstroAlignmentMode) -> Unit,
    onAstroStackAlgorithmChange: (AstroStackAlgorithm) -> Unit,
    onAstroSigmaThresholdChange: (Float) -> Unit,
    onAstroFreezeGroundChange: (Boolean) -> Unit,
    onOpenFreezeGroundPainter: () -> Unit,
    onClearFreezeGroundStrokes: () -> Unit
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
                        val summaryLine = when {
                            isHdrMode -> {
                                val presetTag = selectedHdrPreset?.label ?: "Custom"
                                val deghostTag = if (hdrDeghostingEnabled) " • Deghost" else ""
                                "$presetTag • ${maxResolution.shortLabel} • $alignShort$deghostTag"
                            }
                            isAstroMode -> {
                                val fgTag = if (astroFreezeGroundEnabled) " • Freeze Ground" else ""
                                "${maxResolution.shortLabel} • ${astroAlignmentMode.label} • ${astroStackAlgorithm.label}$fgTag"
                            }
                            else -> {
                                val blendShort = if (blendAlgorithm == BlendAlgorithm.PYRAMID_PMAX) "PMax" else "DMap"
                                val cropShort = if (autoCropBorders) "Auto-Crop" else "Full Canvas"
                                "${maxResolution.shortLabel} • $alignShort • $blendShort • ${edgeSmoothingRadius * 2 + 1}px • $cropShort"
                            }
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

                    if (isAstroMode) {
                        // Astro Alignment Mode
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "ASTRO ALIGNMENT MODE",
                                style = MaterialTheme.typography.labelMedium,
                                color = MutedSlate
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AstroAlignmentMode.entries.forEach { mode ->
                                    val selected = mode == astroAlignmentMode
                                    Surface(
                                        onClick = { onAstroAlignmentModeChange(mode) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) AmberDim else ElevatedCharcoal,
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) AmberAccent else HairlineBorder
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("astro_alignment_${mode.name}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = mode.label,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (selected) AmberAccent else CrispWhite
                                            )
                                            Text(
                                                text = mode.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = HairlineBorder)

                        // Astro Stacking Algorithm
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "STACKING ALGORITHM",
                                style = MaterialTheme.typography.labelMedium,
                                color = MutedSlate
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AstroStackAlgorithm.entries.forEach { algo ->
                                    val selected = algo == astroStackAlgorithm
                                    Surface(
                                        onClick = { onAstroStackAlgorithmChange(algo) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) CyanDim else ElevatedCharcoal,
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) CyanAccent else HairlineBorder
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("astro_algorithm_${algo.name}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = algo.label,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = if (selected) CyanAccent else CrispWhite
                                                )
                                                if (algo == AstroStackAlgorithm.KAPPA_SIGMA) {
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = AmberDim,
                                                        border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.6f))
                                                    ) {
                                                        Text(
                                                            text = "BEST",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = AmberAccent,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = algo.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (astroStackAlgorithm == AstroStackAlgorithm.KAPPA_SIGMA) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "SIGMA THRESHOLD (κ)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MutedSlate
                                    )
                                    Text(
                                        text = "κ = ${String.format(Locale.US, "%.1f", astroSigmaThreshold)}σ",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AmberAccent
                                    )
                                }
                                Text(
                                    text = "Lower values reject trails & hot pixels more aggressively",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = astroSigmaThreshold,
                                    onValueChange = onAstroSigmaThresholdChange,
                                    valueRange = 1.2f..4.5f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AmberAccent,
                                        activeTrackColor = AmberAccent,
                                        inactiveTrackColor = HairlineBorder
                                    ),
                                    modifier = Modifier.testTag("astro_sigma_slider")
                                )
                            }
                        }

                        HorizontalDivider(color = HairlineBorder)

                        // Sky / Foreground Separation ("Freeze Ground")
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAstroFreezeGroundChange(!astroFreezeGroundEnabled) }
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
                                        imageVector = Icons.Rounded.Landscape,
                                        contentDescription = null,
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Freeze Ground (Sky / Foreground)",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = CrispWhite
                                        )
                                        Text(
                                            text = "Brush over ground, trees, or mountains so stars align while foreground stays razor sharp",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Switch(
                                    checked = astroFreezeGroundEnabled,
                                    onCheckedChange = onAstroFreezeGroundChange,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = ObsidianBackground,
                                        checkedTrackColor = EmeraldSuccess,
                                        uncheckedThumbColor = MutedSlate,
                                        uncheckedTrackColor = ElevatedCharcoal
                                    ),
                                    modifier = Modifier.testTag("astro_freeze_ground_switch")
                                )
                            }

                            if (astroFreezeGroundEnabled) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        onClick = onOpenFreezeGroundPainter,
                                        enabled = canPaintFreezeGround,
                                        shape = RoundedCornerShape(10.dp),
                                        color = EmeraldSuccess.copy(alpha = 0.16f),
                                        border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.65f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("paint_freeze_ground_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Brush,
                                                contentDescription = null,
                                                tint = EmeraldSuccess,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (astroFreezeGroundStrokeCount > 0) {
                                                    "Edit Ground Mask ($astroFreezeGroundStrokeCount)"
                                                } else {
                                                    "Brush Ground Mask"
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = EmeraldSuccess
                                            )
                                        }
                                    }

                                    if (astroFreezeGroundStrokeCount > 0) {
                                        Surface(
                                            onClick = onClearFreezeGroundStrokes,
                                            shape = RoundedCornerShape(10.dp),
                                            color = ElevatedCharcoal,
                                            border = BorderStroke(1.dp, HairlineBorder),
                                            modifier = Modifier.testTag("clear_freeze_ground_button")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteSweep,
                                                    contentDescription = "Clear ground mask",
                                                    tint = AmberAccent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Alignment Mode (Focus & HDR)
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
                            // HDR Style Presets Horizontal Selector
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "HDR STYLE PRESET",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MutedSlate
                                    )
                                    Text(
                                        text = selectedHdrPreset?.label ?: "Custom",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AmberAccent
                                    )
                                }

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 2.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    itemsIndexed(HdrStylePreset.entries) { _, preset ->
                                        val selected = preset == selectedHdrPreset
                                        Surface(
                                            onClick = { onSelectHdrPreset(preset) },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (selected) AmberDim else ElevatedCharcoal,
                                            border = BorderStroke(
                                                1.dp,
                                                if (selected) AmberAccent else HairlineBorder
                                            ),
                                            modifier = Modifier.testTag("hdr_preset_${preset.name}")
                                        ) {
                                            Text(
                                                text = preset.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (selected) AmberAccent else CrispWhite,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = selectedHdrPreset?.description
                                        ?: "Custom weights — manually adjusted fusion parameters",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            HorizontalDivider(color = HairlineBorder)

                            HdrWeightSlider(
                                title = "CONTRAST WEIGHT",
                                subtitle = "Controls local detail punch",
                                value = hdrContrastWeight,
                                accentColor = AmberAccent,
                                testTag = "hdr_contrast_slider",
                                onValueChange = onHdrContrastWeightChange
                            )

                            HdrWeightSlider(
                                title = "SATURATION WEIGHT",
                                subtitle = "Controls color richness in blended shadows & highlights",
                                value = hdrSaturationWeight,
                                accentColor = CyanAccent,
                                testTag = "hdr_saturation_slider",
                                onValueChange = onHdrSaturationWeightChange
                            )

                            HdrWeightSlider(
                                title = "EXPOSURE BALANCE",
                                subtitle = "Favors well-exposed midtones over clipped highlights/shadows",
                                value = hdrExposureWeight,
                                accentColor = AmberAccent,
                                testTag = "hdr_exposure_slider",
                                onValueChange = onHdrExposureWeightChange
                            )

                            HorizontalDivider(color = HairlineBorder)

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
                            // Focus Stack Blend Algorithm & Edge Smoothing
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
