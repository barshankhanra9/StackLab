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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.AlignmentMode
import com.example.domain.model.BlendAlgorithm
import com.example.domain.model.StackFrame
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
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.OnAmber
import com.example.ui.theme.SubtleSurface
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    uiState: StackLabUiState,
    onNavigateBack: () -> Unit,
    onAddFrames: (List<Uri>) -> Unit,
    onSelectPreview: (Int) -> Unit,
    onRemoveFrame: (Int) -> Unit,
    onMoveFrame: (Int, Int) -> Unit,
    onAlignmentModeChange: (AlignmentMode) -> Unit,
    onBlendAlgorithmChange: (BlendAlgorithm) -> Unit,
    onEdgeSmoothingChange: (Int) -> Unit,
    onStartStacking: () -> Unit,
    onCancelStacking: () -> Unit,
    onSnackbarConsumed: () -> Unit
) {
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Stack Prep",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AmberDim,
                            border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "${uiState.frames.size} FRAMES",
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
                        text = stringResource(R.string.start_stacking),
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
                    onMoveLeft = { idx -> onMoveFrame(idx, idx - 1) },
                    onMoveRight = { idx -> onMoveFrame(idx, idx + 1) }
                )

                // Horizontal Filmstrip
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FOCUS FILMSTRIP",
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedSlate
                        )
                        if (uiState.frames.size > 15) {
                            Text(
                                text = "AUTO 1600PX PROXY ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanAccent
                            )
                        } else {
                            Text(
                                text = "TAP TO PREVIEW • ARROWS TO REORDER",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedSlate
                            )
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
                                isSelected = index == uiState.selectedPreviewIndex,
                                onSelect = { onSelectPreview(index) },
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
                                    .height(114.dp)
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
                    onToggleExpanded = { advancedExpanded = !advancedExpanded },
                    alignmentMode = uiState.alignmentMode,
                    blendAlgorithm = uiState.blendAlgorithm,
                    edgeSmoothingRadius = uiState.edgeSmoothingRadius,
                    onAlignmentModeChange = onAlignmentModeChange,
                    onBlendAlgorithmChange = onBlendAlgorithmChange,
                    onEdgeSmoothingChange = onEdgeSmoothingChange
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
                    .height(210.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = null,
                        tint = MutedSlate,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add at least 2 photos to begin focus stacking",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSlate
                    )
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
                        modifier = Modifier.fillMaxSize()
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianBackground.copy(alpha = 0.82f),
                        border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "#${selectedIndex + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = AmberAccent,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
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
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CharcoalSurface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) AmberAccent else HairlineBorder
        ),
        modifier = Modifier
            .width(92.dp)
            .clickable(onClick = onSelect)
            .testTag("filmstrip_frame_$index")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp)
                    .background(ObsidianBackground)
            ) {
                AsyncImage(
                    model = frame.uri,
                    contentDescription = "Frame #${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ObsidianBackground.copy(alpha = 0.85f),
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

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(ObsidianBackground.copy(alpha = 0.82f))
                        .border(1.dp, HairlineBorder, CircleShape)
                        .clickable(onClick = onRemove)
                        .testTag("remove_frame_$index"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_remove_frame),
                        tint = CrispWhite,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(ElevatedCharcoal),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        modifier = Modifier.size(18.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
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
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedStackSettingsCard(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    alignmentMode: AlignmentMode,
    blendAlgorithm: BlendAlgorithm,
    edgeSmoothingRadius: Int,
    onAlignmentModeChange: (AlignmentMode) -> Unit,
    onBlendAlgorithmChange: (BlendAlgorithm) -> Unit,
    onEdgeSmoothingChange: (Int) -> Unit
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
                        val alignShort = if (alignmentMode == AlignmentMode.AUTO_HOMOGRAPHY) "Homography" else "Tripod"
                        val blendShort = if (blendAlgorithm == BlendAlgorithm.PYRAMID_PMAX) "PMax" else "DMap"
                        Text(
                            text = "$alignShort • $blendShort • ${edgeSmoothingRadius * 2 + 1}px",
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
            }
        }
    }
}
