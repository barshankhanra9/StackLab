package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Exposure
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.MaxResolutionPreset
import com.example.domain.model.OutputFormat
import com.example.domain.model.StackMode
import com.example.ui.StackLabUiState
import com.example.ui.components.SettingsBottomSheet
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberDim
import com.example.ui.theme.AstroIndigo
import com.example.ui.theme.AstroIndigoDim
import com.example.ui.theme.AstroIndigoSoft
import com.example.ui.theme.AstroTeal
import com.example.ui.theme.AstroTealDim
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanDim
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.OnAmber
import com.example.ui.theme.OnAstro
import com.example.ui.theme.OnCyan
import com.example.ui.theme.SubtleSurface

@Composable
fun HomeScreen(
    uiState: StackLabUiState,
    onPhotosPicked: (List<android.net.Uri>, StackMode) -> Unit,
    onHdrModeClicked: () -> Unit,
    onAstroModeClicked: () -> Unit,
    onComingSoonClicked: (StackMode) -> Unit,
    onSelectResolution: (MaxResolutionPreset) -> Unit,
    onSelectFormat: (OutputFormat) -> Unit,
    onQualityChange: (Int) -> Unit,
    onSnackbarConsumed: () -> Unit
) {
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var pendingPickerModeId by rememberSaveable { mutableStateOf(StackMode.FocusStack.id) }
    val snackbarHostState = remember { SnackbarHostState() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                val targetMode = when (pendingPickerModeId) {
                    StackMode.HdrExposureBlend.id -> StackMode.HdrExposureBlend
                    StackMode.AstroNoiseStack.id -> StackMode.AstroNoiseStack
                    else -> StackMode.FocusStack
                }
                onPhotosPicked(uris, targetMode)
            }
        }
    )

    fun launchPickerFor(mode: StackMode) {
        pendingPickerModeId = mode.id
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

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
            HomeTopBar(
                onSettingsClick = { showSettingsSheet = true }
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Active Output Summary Pill
                Surface(
                    onClick = { showSettingsSheet = true },
                    shape = RoundedCornerShape(14.dp),
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
                            text = "${uiState.outputSettings.maxResolution.shortLabel.uppercase()} • ${uiState.outputSettings.outputFormat.name}$qualitySuffix",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanAccent
                        )
                    }
                }

                // Card 1 — Focus Stack (Amber Accent #F5A623)
                StackEngineHeroCard(
                    heroResId = R.drawable.img_focus_stack_hero,
                    heroContentDescription = stringResource(R.string.cd_focus_stack_hero),
                    categoryIcon = Icons.Rounded.CenterFocusStrong,
                    categoryLabel = "MACRO & LANDSCAPE",
                    categoryTint = AmberAccent,
                    categoryBg = ObsidianBackground.copy(alpha = 0.82f),
                    categoryBorder = AmberAccent.copy(alpha = 0.65f),
                    engineBadge = "OPENCV PYRAMID",
                    engineBadgeTint = CyanAccent,
                    engineBadgeBg = CyanDim.copy(alpha = 0.88f),
                    engineBadgeBorder = CyanAccent.copy(alpha = 0.55f),
                    title = stringResource(R.string.focus_stack_title),
                    description = stringResource(R.string.focus_stack_subtitle),
                    buttonColor = AmberAccent,
                    onButtonColor = OnAmber,
                    cardTestTag = "focus_stack_hero_card",
                    buttonTestTag = "select_photos_button",
                    onCardClick = { launchPickerFor(StackMode.FocusStack) },
                    onSelectPhotosClick = { launchPickerFor(StackMode.FocusStack) }
                )

                // Card 2 — HDR Exposure Blend (Cyan Accent #29B6F6)
                StackEngineHeroCard(
                    heroResId = R.drawable.img_hdr_blend_hero,
                    heroContentDescription = stringResource(R.string.cd_hdr_blend_hero),
                    categoryIcon = Icons.Rounded.Exposure,
                    categoryLabel = "EV BRACKETING",
                    categoryTint = CyanAccent,
                    categoryBg = ObsidianBackground.copy(alpha = 0.82f),
                    categoryBorder = CyanAccent.copy(alpha = 0.65f),
                    engineBadge = "MERTENS + CLAHE",
                    engineBadgeTint = AmberAccent,
                    engineBadgeBg = AmberDim.copy(alpha = 0.88f),
                    engineBadgeBorder = AmberAccent.copy(alpha = 0.55f),
                    title = stringResource(R.string.hdr_blend_title),
                    description = stringResource(R.string.hdr_blend_subtitle),
                    buttonColor = CyanAccent,
                    onButtonColor = OnCyan,
                    cardTestTag = "mode_card_${StackMode.HdrExposureBlend.id}",
                    buttonTestTag = "select_photos_hdr_button",
                    onCardClick = {
                        if (StackMode.HdrExposureBlend.isAvailable) onHdrModeClicked()
                        else onComingSoonClicked(StackMode.HdrExposureBlend)
                    },
                    onSelectPhotosClick = {
                        if (StackMode.HdrExposureBlend.isAvailable) launchPickerFor(StackMode.HdrExposureBlend)
                        else onComingSoonClicked(StackMode.HdrExposureBlend)
                    }
                )

                // Card 3 — Astro / Noise Stack (Indigo/Teal Accent #7E57C2 / #26C6DA)
                StackEngineHeroCard(
                    heroResId = R.drawable.img_astro_stack_hero,
                    heroContentDescription = stringResource(R.string.cd_astro_stack_hero),
                    categoryIcon = Icons.Rounded.NightsStay,
                    categoryLabel = "STARFIELD & NIGHT",
                    categoryTint = AstroIndigoSoft,
                    categoryBg = AstroIndigoDim.copy(alpha = 0.88f),
                    categoryBorder = AstroIndigo.copy(alpha = 0.75f),
                    engineBadge = "SIGMA CLIPPING",
                    engineBadgeTint = AstroTeal,
                    engineBadgeBg = AstroTealDim.copy(alpha = 0.88f),
                    engineBadgeBorder = AstroTeal.copy(alpha = 0.60f),
                    title = stringResource(R.string.astro_stack_title),
                    description = stringResource(R.string.astro_stack_subtitle),
                    buttonColor = AstroTeal,
                    onButtonColor = OnAstro,
                    cardTestTag = "mode_card_${StackMode.AstroNoiseStack.id}",
                    buttonTestTag = "select_photos_astro_button",
                    onCardClick = {
                        if (StackMode.AstroNoiseStack.isAvailable) onAstroModeClicked()
                        else onComingSoonClicked(StackMode.AstroNoiseStack)
                    },
                    onSelectPhotosClick = {
                        if (StackMode.AstroNoiseStack.isAvailable) launchPickerFor(StackMode.AstroNoiseStack)
                        else onComingSoonClicked(StackMode.AstroNoiseStack)
                    }
                )

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
private fun HomeTopBar(
    onSettingsClick: () -> Unit
) {
    Surface(
        color = ObsidianBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SubtleSurface,
                        tonalElevation = 2.dp,
                        shadowElevation = 4.dp,
                        border = BorderStroke(1.dp, HairlineBorder.copy(alpha = 0.8f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_stacklab_logo),
                                contentDescription = stringResource(R.string.cd_stacklab_logo),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = CrispWhite
                        )
                        Text(
                            text = "COMPUTATIONAL DARKROOM",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberAccent
                        )
                    }
                }

                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.cd_settings),
                        tint = CrispWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun StackEngineHeroCard(
    @DrawableRes heroResId: Int,
    heroContentDescription: String,
    categoryIcon: ImageVector,
    categoryLabel: String,
    categoryTint: Color,
    categoryBg: Color,
    categoryBorder: Color,
    engineBadge: String,
    engineBadgeTint: Color,
    engineBadgeBg: Color,
    engineBadgeBorder: Color,
    title: String,
    description: String,
    buttonColor: Color,
    onButtonColor: Color,
    cardTestTag: String,
    buttonTestTag: String,
    onCardClick: () -> Unit,
    onSelectPhotosClick: () -> Unit
) {
    Surface(
        onClick = onCardClick,
        shape = RoundedCornerShape(20.dp),
        color = CharcoalSurface,
        border = BorderStroke(1.dp, HairlineBorder),
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(cardTestTag)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp)
            ) {
                Image(
                    painter = painterResource(id = heroResId),
                    contentDescription = heroContentDescription,
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
                                    ObsidianBackground.copy(alpha = 0.45f),
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
                        shape = RoundedCornerShape(9.dp),
                        color = categoryBg,
                        border = BorderStroke(1.dp, categoryBorder),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = categoryIcon,
                                contentDescription = null,
                                tint = categoryTint,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = categoryLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = categoryTint,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = engineBadgeBg,
                        border = BorderStroke(1.dp, engineBadgeBorder),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = engineBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = engineBadgeTint,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = CrispWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onSelectPhotosClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = onButtonColor
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag(buttonTestTag)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
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
