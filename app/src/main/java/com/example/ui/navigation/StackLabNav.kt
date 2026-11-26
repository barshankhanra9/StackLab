package com.example.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.StackLabNavEvent
import com.example.ui.StackLabViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ResultScreen
import com.example.ui.screens.WorkspaceScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object WorkspaceRoute

@Serializable
data object ResultRoute

@Composable
fun StackLabNav(
    viewModel: StackLabViewModel = viewModel()
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event ->
            when (event) {
                StackLabNavEvent.OpenWorkspace -> {
                    navController.navigate(WorkspaceRoute) {
                        launchSingleTop = true
                    }
                }
                StackLabNavEvent.OpenResult -> {
                    navController.navigate(ResultRoute) {
                        launchSingleTop = true
                    }
                }
                StackLabNavEvent.ReturnHome -> {
                    navController.popBackStack(HomeRoute, inclusive = false)
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        composable<HomeRoute> {
            HomeScreen(
                uiState = uiState,
                onPhotosPicked = { uris ->
                    viewModel.onPhotosSelected(
                        uris = uris,
                        navigateToWorkspace = true,
                        mode = com.example.domain.model.StackMode.FocusStack
                    )
                },
                onHdrModeClicked = viewModel::openHdrWorkspace,
                onAstroModeClicked = viewModel::openAstroWorkspace,
                onComingSoonClicked = viewModel::onComingSoonModeClicked,
                onSelectResolution = viewModel::updateMaxResolution,
                onSelectFormat = viewModel::updateOutputFormat,
                onQualityChange = viewModel::updateJpegQuality,
                onSnackbarConsumed = viewModel::consumeSnackbar
            )
        }

        composable<WorkspaceRoute> {
            WorkspaceScreen(
                uiState = uiState,
                onNavigateBack = { navController.popBackStack() },
                onAddFrames = { uris ->
                    viewModel.onPhotosSelected(uris, navigateToWorkspace = false)
                },
                onSelectPreview = viewModel::selectPreviewFrame,
                onToggleFrameEnabled = viewModel::toggleFrameEnabled,
                onAutoSort = {
                    when (uiState.activeMode) {
                        com.example.domain.model.StackMode.HdrExposureBlend -> viewModel.autoSortFramesByEv()
                        com.example.domain.model.StackMode.AstroNoiseStack -> viewModel.sortAstroFramesLightsFirst()
                        com.example.domain.model.StackMode.FocusStack -> viewModel.autoSortFramesByFocusDistance()
                    }
                },
                onRemoveFrame = viewModel::removeFrame,
                onMoveFrame = viewModel::moveFrame,
                onAlignmentModeChange = viewModel::setAlignmentMode,
                onBlendAlgorithmChange = viewModel::setBlendAlgorithm,
                onEdgeSmoothingChange = viewModel::setEdgeSmoothingRadius,
                onAutoCropBordersChange = viewModel::setAutoCropBorders,
                onHdrContrastWeightChange = viewModel::setHdrContrastWeight,
                onHdrSaturationWeightChange = viewModel::setHdrSaturationWeight,
                onHdrExposureWeightChange = viewModel::setHdrExposureWeight,
                onHdrDeghostingChange = viewModel::setHdrDeghostingEnabled,
                onSelectHdrPreset = viewModel::selectHdrPreset,
                onSelectHdrAnchorFrame = viewModel::selectHdrAnchorFrame,
                onAstroFrameTypeChange = viewModel::setAstroFrameType,
                onAstroAlignmentModeChange = viewModel::setAstroAlignmentMode,
                onAstroStackAlgorithmChange = viewModel::setAstroStackAlgorithm,
                onAstroSigmaThresholdChange = viewModel::setAstroSigmaThreshold,
                onAstroFreezeGroundChange = viewModel::setAstroFreezeGroundEnabled,
                onAddAstroFreezeStroke = viewModel::addAstroFreezeGroundStroke,
                onUndoAstroFreezeStroke = viewModel::undoAstroFreezeGroundStroke,
                onClearAstroFreezeStrokes = viewModel::clearAstroFreezeGroundStrokes,
                onMaxResolutionChange = viewModel::updateMaxResolution,
                onStartStacking = viewModel::startStacking,
                onCancelStacking = viewModel::cancelStacking,
                onSnackbarConsumed = viewModel::consumeSnackbar
            )
        }

        composable<ResultRoute> {
            ResultScreen(
                uiState = uiState,
                onNavigateBack = { navController.popBackStack() },
                onSaveFullRes = viewModel::saveFullResolutionResult,
                onNewStack = viewModel::startNewStack,
                onToggleFocusPeaking = viewModel::toggleFocusPeaking,
                onCompareFrameChanged = viewModel::updateComparePeakingIfEnabled,
                onSetRetouchMode = viewModel::setRetouchMode,
                onSelectRetouchSource = viewModel::selectRetouchSourceFrame,
                onBrushSizeChange = viewModel::setRetouchBrushSize,
                onFeatherChange = viewModel::setRetouchFeather,
                onOpacityChange = viewModel::setRetouchOpacity,
                onCommitRetouchStroke = viewModel::commitRetouchStroke,
                onUndoRetouch = viewModel::undoRetouchStroke,
                onRedoRetouch = viewModel::redoRetouchStroke,
                onToneAdjustmentsChange = viewModel::updateHdrToneAdjustments,
                onResetToneAdjustments = viewModel::resetHdrToneAdjustments,
                onAstroStretchAdjustmentsChange = viewModel::updateAstroStretchAdjustments,
                onResetAstroStretchAdjustments = viewModel::resetAstroStretchAdjustments,
                onSnackbarConsumed = viewModel::consumeSnackbar
            )
        }
    }
}
