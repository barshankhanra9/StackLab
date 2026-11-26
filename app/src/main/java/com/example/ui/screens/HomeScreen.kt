package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Exposure
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.MaxResolutionPreset
import com.example.domain.model.OutputFormat
import com.example.domain.model.StackMode
import com.example.ui.StackLabUiState
import com.example.ui.components.SettingsBottomSheet
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: StackLabUiState,
    onPhotosPicked: (List<android.net.Uri>) -> Unit,
    onHdrModeClicked: () -> Unit,
    onAstroModeClicked: () -> Unit,
    onComingSoonClicked: (StackMode) -> Unit,
    onSelectResolution: (MaxResolutionPreset) -> Unit,
    onSelectFormat: (OutputFormat) -> Unit,
    onQualityChange: (Int) -> Unit,
    onSnackbarConsumed: () -> Unit
) {
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onPhotosPicked(uris)
            }
        }
    )

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onSnackbarConsumed()
        }
    }

    Scaffold(
        containerColor = ObsidianBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = ObsidianBackground,
                    titleContentColor = CrispWhite
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(AmberAccent, CyanAccent))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Camera,
                                contentDescription = null,
                                tint = ObsidianBackground,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "COMPUTATIONAL DARKROOM",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberAccent
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.cd_settings),
                            tint = CrispWhite
                        )
                    }
                }
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
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Active Output Summary Pill
                Surface(
                    onClick = { showSettingsSheet = true },
                    shape = RoundedCornerShape(12.dp),
                    color = CharcoalSurface,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EXPORT PIPELINE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedSlate
                        )
                        val qualitySuffix = if (uiState.outputSettings.outputFormat == OutputFormat.JPEG) {
                            " ${uiState.outputSettings.jpegQuality}%"
                        } else {
                            " LOSSLESS"
                        }
                        Text(
                            text = "${uiState.outputSettings.maxResolution.shortLabel} • ${uiState.outputSettings.outputFormat.name}$qualitySuffix",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanAccent
                        )
                    }
                }

                // Hero Card: Focus Stack
                FocusStackHeroCard(
                    onSelectPhotosClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )

                // Section Header for Secondary Modular Processors
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STACKING ENGINES",
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedSlate
                    )
                    Text(
                        text = "MODULAR SUITE",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent
                    )
                }

                // Secondary Cards wired to StackMode sealed hierarchy
                uiState.availableModes
                    .filter { it != StackMode.FocusStack }
                    .forEach { mode ->
                        val (icon, specsTag) = when (mode) {
                            StackMode.HdrExposureBlend -> Icons.Rounded.Exposure to "EV Bracketing • Mertens Pyramid Fusion"
                            StackMode.AstroNoiseStack -> Icons.Rounded.NightsStay to "Star Alignment • Sigma Clip • Dark Subtraction"
                            StackMode.FocusStack -> Icons.Rounded.CenterFocusStrong to "Laplacian Pyramid"
                        }
                        SecondaryStackModeCard(
                            mode = mode,
                            icon = icon,
                            specsTag = specsTag,
                            onClick = {
                                when {
                                    mode == StackMode.HdrExposureBlend && mode.isAvailable -> onHdrModeClicked()
                                    mode == StackMode.AstroNoiseStack && mode.isAvailable -> onAstroModeClicked()
                                    else -> onComingSoonClicked(mode)
                                }
                            }
                        )
                    }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showSettingsSheet) {
        SettingsBottomSheet(
            settings = uiState.outputSettings,
            onDismiss = { showSettingsSheet = false },
            onSelectResolution = onSelectResolution,
            onSelectFormat = onSelectFormat,
            onQualityChange = onQualityChange
        )
    }
}

@Composable
private fun FocusStackHeroCard(
    onSelectPhotosClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = CharcoalSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("focus_stack_hero_card")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_focus_stack_hero),
                    contentDescription = stringResource(R.string.cd_focus_stack_hero),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    ObsidianBackground.copy(alpha = 0.55f),
                                    CharcoalSurface
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianBackground.copy(alpha = 0.78f),
                        border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CenterFocusStrong,
                                contentDescription = null,
                                tint = AmberAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "MACRO & LANDSCAPE",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberAccent
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CyanDim.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "OPENCV PYRAMID",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    text = stringResource(R.string.focus_stack_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = CrispWhite
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.focus_stack_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onSelectPhotosClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberAccent,
                        contentColor = OnAmber
                    ),
                    contentPadding = PaddingValues(vertical = 15.dp, horizontal = 20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("select_photos_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.select_photos),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun SecondaryStackModeCard(
    mode: StackMode,
    icon: ImageVector,
    specsTag: String,
    onClick: () -> Unit
) {
    val isActiveMode = mode.isAvailable
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = CharcoalSurface,
        border = BorderStroke(
            1.dp,
            if (isActiveMode) CyanAccent.copy(alpha = 0.45f) else HairlineBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mode_card_${mode.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isActiveMode) CyanDim else ElevatedCharcoal),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mode.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = CrispWhite
                    )
                    if (isActiveMode) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CyanDim,
                            border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "OPEN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanAccent
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AmberDim,
                            border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = AmberAccent,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "COMING SOON",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberAccent
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = mode.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = specsTag,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActiveMode) CyanAccent else MutedSlate
                )
            }
        }
    }
}
