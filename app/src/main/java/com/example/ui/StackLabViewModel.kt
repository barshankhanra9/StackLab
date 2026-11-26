package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MediaStoreExporter
import com.example.data.SettingsRepository
import com.example.domain.model.AlignmentMode
import com.example.domain.model.AstroAlignmentMode
import com.example.domain.model.AstroConfig
import com.example.domain.model.AstroFrameType
import com.example.domain.model.AstroStackAlgorithm
import com.example.domain.model.AstroStretchAdjustments
import com.example.domain.model.BlendAlgorithm
import com.example.domain.model.FocusStackConfig
import com.example.domain.model.ForegroundMaskStroke
import com.example.domain.model.HdrConfig
import com.example.domain.model.HdrStylePreset
import com.example.domain.model.HdrToneAdjustments
import com.example.domain.model.MaxResolutionPreset
import com.example.domain.model.OutputFormat
import com.example.domain.model.OutputSettings
import com.example.domain.model.RetouchPoint
import com.example.domain.model.RetouchStroke
import com.example.domain.model.StackFrame
import com.example.domain.model.StackMode
import com.example.domain.model.StackProgress
import com.example.domain.model.StackResult
import com.example.engine.AstroPostEngine
import com.example.engine.AstroStackProcessor
import com.example.engine.EvFrameAnalyzer
import com.example.engine.FocusFrameAnalyzer
import com.example.engine.FocusStackProcessor
import com.example.engine.FrameBitmapLoader
import com.example.engine.HdrStackProcessor
import com.example.engine.HdrToneEngine
import com.example.engine.RetouchAndPeakingUtils
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ProcessingUiState {
    data object Idle : ProcessingUiState
    data class Running(val progress: StackProgress) : ProcessingUiState
}

sealed interface StackLabNavEvent {
    data object OpenWorkspace : StackLabNavEvent
    data object OpenResult : StackLabNavEvent
    data object ReturnHome : StackLabNavEvent
}

data class StackLabUiState(
    val activeMode: StackMode = StackMode.FocusStack,
    val availableModes: List<StackMode> = listOf(
        StackMode.FocusStack,
        StackMode.HdrExposureBlend,
        StackMode.AstroNoiseStack
    ),
    val outputSettings: OutputSettings = OutputSettings(),
    val frames: List<StackFrame> = emptyList(),
    val isAnalyzingFocus: Boolean = false,
    val selectedPreviewIndex: Int = 0,
    val alignmentMode: AlignmentMode = AlignmentMode.AUTO_HOMOGRAPHY,
    val blendAlgorithm: BlendAlgorithm = BlendAlgorithm.PYRAMID_PMAX,
    val edgeSmoothingRadius: Int = 3,
    val autoCropBorders: Boolean = true,
    val hdrContrastWeight: Float = 1.0f,
    val hdrSaturationWeight: Float = 1.0f,
    val hdrExposureWeight: Float = 1.0f,
    val hdrDeghostingEnabled: Boolean = false,
    val selectedHdrPreset: HdrStylePreset? = HdrStylePreset.NATURAL,
    val hdrAnchorFrameId: String? = null,
    val hdrToneAdjustments: HdrToneAdjustments = HdrToneAdjustments(),
    val astroAlignmentMode: AstroAlignmentMode = AstroAlignmentMode.STAR_FIELD,
    val astroStackAlgorithm: AstroStackAlgorithm = AstroStackAlgorithm.KAPPA_SIGMA,
    val astroSigmaThreshold: Float = 2.5f,
    val astroFreezeGroundEnabled: Boolean = false,
    val astroFreezeGroundStrokes: List<ForegroundMaskStroke> = emptyList(),
    val astroStretchAdjustments: AstroStretchAdjustments = AstroStretchAdjustments(),
    val isApplyingTone: Boolean = false,
    val processingState: ProcessingUiState = ProcessingUiState.Idle,
    val result: StackResult? = null,
    val retouchBitmapVersion: Int = 0,
    val isRetouchMode: Boolean = false,
    val retouchSourceIndex: Int = 0,
    val retouchBrushSizeFraction: Float = 0.06f,
    val retouchFeather: Float = 0.45f,
    val retouchOpacity: Float = 0.85f,
    val retouchUndoCount: Int = 0,
    val retouchRedoCount: Int = 0,
    val retouchMaskBitmap: Bitmap? = null,
    val isApplyingRetouch: Boolean = false,
    val isFocusPeakingEnabled: Boolean = false,
    val focusPeakingOverlayBitmap: Bitmap? = null,
    val comparePeakingOverlayBitmap: Bitmap? = null,
    val comparePeakingFrameIndex: Int = -1,
    val isSaving: Boolean = false,
    val lastSavedPath: String? = null,
    val snackbarMessage: String? = null
) {
    val enabledFramesCount: Int
        get() = frames.count { it.isEnabled }

    val enabledLightFramesCount: Int
        get() = frames.count { it.isEnabled && it.astroFrameType == AstroFrameType.LIGHT }

    val enabledDarkFramesCount: Int
        get() = frames.count { it.isEnabled && it.astroFrameType == AstroFrameType.DARK }

    val canStartStacking: Boolean
        get() = processingState is ProcessingUiState.Idle &&
            if (activeMode == StackMode.AstroNoiseStack) {
                enabledLightFramesCount >= 2
            } else {
                enabledFramesCount >= 2
            }
}

class StackLabViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val bitmapLoader = FrameBitmapLoader(application)
    private val focusAnalyzer = FocusFrameAnalyzer(bitmapLoader)
    private val evAnalyzer = EvFrameAnalyzer(bitmapLoader)
    private val focusStackProcessor = FocusStackProcessor(bitmapLoader)
    private val hdrStackProcessor = HdrStackProcessor(bitmapLoader)
    private val astroStackProcessor = AstroStackProcessor(bitmapLoader)
    private val mediaStoreExporter = MediaStoreExporter(application)

    private val _uiState = MutableStateFlow(StackLabUiState())
    val uiState: StateFlow<StackLabUiState> = _uiState.asStateFlow()

    private val _navEvents = MutableSharedFlow<StackLabNavEvent>(extraBufferCapacity = 4)
    val navEvents: SharedFlow<StackLabNavEvent> = _navEvents.asSharedFlow()

    private var stackingJob: Job? = null
    private var analysisJob: Job? = null
    private var peakingJob: Job? = null
    private var toneJob: Job? = null
    private var retouchSourceJob: Job? = null
    private val retouchSourceLock = Any()

    // Retouch & post-fusion tone session buffers (kept strictly O(1) — only 1 active source frame in RAM)
    private var baseOutputBitmap: Bitmap? = null
    private var workingOutputBitmap: Bitmap? = null
    private var tonePreviewBaseBitmap: Bitmap? = null
    private var activeRetouchSourceIndex: Int = -1
    private var activeRetouchSourceBitmap: Bitmap? = null
    private val undoStrokes = ArrayList<RetouchStroke>()
    private val redoStrokes = ArrayList<RetouchStroke>()

    init {
        viewModelScope.launch {
            settingsRepository.outputSettings.collect { settings ->
                _uiState.update { it.copy(outputSettings = settings) }
            }
        }
    }

    fun openHdrWorkspace() {
        analysisJob?.cancel()
        val switchingFromOtherMode = _uiState.value.activeMode != StackMode.HdrExposureBlend
        if (switchingFromOtherMode) {
            clearRetouchAndPeakingState()
            recycleResultBitmaps(_uiState.value.result)
            bitmapLoader.clearRetouchCache()
        }
        _uiState.update { state ->
            state.copy(
                activeMode = StackMode.HdrExposureBlend,
                frames = if (switchingFromOtherMode) emptyList() else state.frames,
                selectedPreviewIndex = if (switchingFromOtherMode) 0 else state.selectedPreviewIndex,
                astroFreezeGroundStrokes = if (switchingFromOtherMode) emptyList() else state.astroFreezeGroundStrokes,
                result = if (switchingFromOtherMode) null else state.result,
                lastSavedPath = null
            )
        }
        _navEvents.tryEmit(StackLabNavEvent.OpenWorkspace)
    }

    fun openAstroWorkspace() {
        analysisJob?.cancel()
        val switchingFromOtherMode = _uiState.value.activeMode != StackMode.AstroNoiseStack
        if (switchingFromOtherMode) {
            clearRetouchAndPeakingState()
            recycleResultBitmaps(_uiState.value.result)
            bitmapLoader.clearRetouchCache()
        }
        _uiState.update { state ->
            state.copy(
                activeMode = StackMode.AstroNoiseStack,
                frames = if (switchingFromOtherMode) emptyList() else state.frames,
                selectedPreviewIndex = if (switchingFromOtherMode) 0 else state.selectedPreviewIndex,
                hdrAnchorFrameId = if (switchingFromOtherMode) null else state.hdrAnchorFrameId,
                astroFreezeGroundStrokes = if (switchingFromOtherMode) emptyList() else state.astroFreezeGroundStrokes,
                result = if (switchingFromOtherMode) null else state.result,
                lastSavedPath = null
            )
        }
        _navEvents.tryEmit(StackLabNavEvent.OpenWorkspace)
    }

    fun onPhotosSelected(
        uris: List<Uri>,
        navigateToWorkspace: Boolean,
        mode: StackMode? = null
    ) {
        if (uris.isEmpty()) return
        val switchingMode = navigateToWorkspace && mode != null && mode != _uiState.value.activeMode
        if (switchingMode) {
            analysisJob?.cancel()
            clearRetouchAndPeakingState()
            recycleResultBitmaps(_uiState.value.result)
            bitmapLoader.clearRetouchCache()
        }
        viewModelScope.launch {
            val newFrames = withContext(Dispatchers.IO) {
                bitmapLoader.inspectFrames(uris)
            }
            var mergedFrames: List<StackFrame> = emptyList()
            var resolvedMode: StackMode = StackMode.FocusStack
            _uiState.update { state ->
                val targetMode = mode ?: if (navigateToWorkspace) StackMode.FocusStack else state.activeMode
                val merged = if (navigateToWorkspace) {
                    newFrames
                } else {
                    state.frames + newFrames
                }
                mergedFrames = merged
                resolvedMode = targetMode
                val nextAnchorId = if (targetMode == StackMode.HdrExposureBlend) {
                    if (state.hdrAnchorFrameId != null && merged.any { it.id == state.hdrAnchorFrameId && it.isEnabled }) {
                        state.hdrAnchorFrameId
                    } else {
                        resolveMiddleExposureFrameId(merged)
                    }
                } else {
                    null
                }
                state.copy(
                    activeMode = targetMode,
                    frames = merged,
                    hdrAnchorFrameId = nextAnchorId,
                    selectedPreviewIndex = state.selectedPreviewIndex.coerceIn(
                        0,
                        (merged.size - 1).coerceAtLeast(0)
                    ),
                    lastSavedPath = null
                )
            }
            if (navigateToWorkspace) {
                _navEvents.tryEmit(StackLabNavEvent.OpenWorkspace)
            }
            when (resolvedMode) {
                StackMode.HdrExposureBlend -> runEvAnalysis(mergedFrames, autoSort = true)
                StackMode.FocusStack -> runFocusAnalysis(mergedFrames)
                StackMode.AstroNoiseStack -> Unit // Light frames by default; no focus/EV pre-analysis needed
            }
        }
    }

    private fun runFocusAnalysis(framesToAnalyze: List<StackFrame>) {
        if (framesToAnalyze.isEmpty()) return
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzingFocus = true) }
            try {
                val analyzed = withContext(Dispatchers.Default) {
                    focusAnalyzer.analyzeFrames(framesToAnalyze)
                }
                val byId = analyzed.associateBy { it.id }
                _uiState.update { state ->
                    state.copy(
                        isAnalyzingFocus = false,
                        frames = state.frames.map { current ->
                            val updated = byId[current.id]
                            if (updated != null) {
                                current.copy(
                                    focusRegion = updated.focusRegion,
                                    focusDistanceScore = updated.focusDistanceScore,
                                    peakSharpnessScore = updated.peakSharpnessScore,
                                    isAnalyzed = true
                                )
                            } else {
                                current
                            }
                        }
                    )
                }
            } catch (_: CancellationException) {
                _uiState.update { it.copy(isAnalyzingFocus = false) }
            } catch (_: Throwable) {
                _uiState.update { it.copy(isAnalyzingFocus = false) }
            }
        }
    }

    private fun runEvAnalysis(framesToAnalyze: List<StackFrame>, autoSort: Boolean) {
        if (framesToAnalyze.isEmpty()) return
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzingFocus = true) }
            try {
                val analyzed = withContext(Dispatchers.Default) {
                    evAnalyzer.analyzeFrames(framesToAnalyze)
                }
                val byId = analyzed.associateBy { it.id }
                _uiState.update { state ->
                    val selectedId = state.frames.getOrNull(state.selectedPreviewIndex)?.id
                    val updatedList = state.frames.map { current ->
                        val updated = byId[current.id]
                        if (updated != null) {
                            current.copy(
                                exposureBracket = updated.exposureBracket,
                                meanLuminance = updated.meanLuminance,
                                isAnalyzed = true
                            )
                        } else {
                            current
                        }
                    }
                    val finalFrames = if (autoSort && updatedList.size >= 2) {
                        updatedList.sortedBy { it.meanLuminance }
                    } else {
                        updatedList
                    }
                    val nextSelected = selectedId?.let { id ->
                        finalFrames.indexOfFirst { it.id == id }.takeIf { it >= 0 }
                    } ?: state.selectedPreviewIndex.coerceIn(0, (finalFrames.size - 1).coerceAtLeast(0))

                    val anchorStillValid = state.hdrAnchorFrameId != null &&
                        finalFrames.any { it.id == state.hdrAnchorFrameId && it.isEnabled }
                    val nextAnchorId = if (anchorStillValid) {
                        state.hdrAnchorFrameId
                    } else {
                        resolveMiddleExposureFrameId(finalFrames)
                    }

                    state.copy(
                        isAnalyzingFocus = false,
                        frames = finalFrames,
                        hdrAnchorFrameId = nextAnchorId,
                        selectedPreviewIndex = nextSelected
                    )
                }
            } catch (_: CancellationException) {
                _uiState.update { it.copy(isAnalyzingFocus = false) }
            } catch (_: Throwable) {
                _uiState.update { it.copy(isAnalyzingFocus = false) }
            }
        }
    }

    fun toggleFrameEnabled(index: Int) {
        _uiState.update { state ->
            if (index !in state.frames.indices) return@update state
            val updated = state.frames.toMutableList()
            val target = updated[index]
            updated[index] = target.copy(isEnabled = !target.isEnabled)
            val nextAnchorId = if (state.activeMode == StackMode.HdrExposureBlend) {
                if (state.hdrAnchorFrameId != null && updated.any { it.id == state.hdrAnchorFrameId && it.isEnabled }) {
                    state.hdrAnchorFrameId
                } else {
                    resolveMiddleExposureFrameId(updated)
                }
            } else {
                state.hdrAnchorFrameId
            }
            state.copy(frames = updated, hdrAnchorFrameId = nextAnchorId)
        }
    }

    fun selectHdrAnchorFrame(index: Int) {
        _uiState.update { state ->
            val target = state.frames.getOrNull(index) ?: return@update state
            val updated = if (target.isEnabled) {
                state.frames
            } else {
                state.frames.toMutableList().apply {
                    set(index, target.copy(isEnabled = true))
                }
            }
            state.copy(
                frames = updated,
                hdrAnchorFrameId = target.id,
                snackbarMessage = "Anchor set to frame #${index + 1}"
            )
        }
    }

    private fun resolveMiddleExposureFrameId(frames: List<StackFrame>): String? {
        if (frames.isEmpty()) return null
        val pool = frames.filter { it.isEnabled }.ifEmpty { frames }
        return if (pool.any { it.isAnalyzed }) {
            val sorted = pool.sortedBy { it.meanLuminance }
            sorted[sorted.size / 2].id
        } else {
            pool[pool.size / 2].id
        }
    }

    fun autoSortFramesByFocusDistance() {
        val current = _uiState.value
        if (current.frames.size < 2) return

        val selectedId = current.frames.getOrNull(current.selectedPreviewIndex)?.id
        val sorted = current.frames.sortedWith(
            compareBy<StackFrame> { it.focusDistanceScore }.thenByDescending { it.peakSharpnessScore }
        )
        val newSelectedIdx = selectedId?.let { id ->
            sorted.indexOfFirst { it.id == id }.takeIf { it >= 0 }
        } ?: 0

        _uiState.update {
            it.copy(
                frames = sorted,
                selectedPreviewIndex = newSelectedIdx,
                snackbarMessage = "Frames sorted front-to-back (Near → Far)"
            )
        }
    }

    fun autoSortFramesByEv() {
        val current = _uiState.value
        if (current.frames.size < 2) return

        val selectedId = current.frames.getOrNull(current.selectedPreviewIndex)?.id
        val sorted = current.frames.sortedBy { it.meanLuminance }
        val newSelectedIdx = selectedId?.let { id ->
            sorted.indexOfFirst { it.id == id }.takeIf { it >= 0 }
        } ?: 0

        _uiState.update {
            it.copy(
                frames = sorted,
                selectedPreviewIndex = newSelectedIdx,
                snackbarMessage = "Brackets sorted Dark → Mid → Bright"
            )
        }
    }

    fun selectPreviewFrame(index: Int) {
        _uiState.update { state ->
            if (state.frames.isEmpty()) state
            else state.copy(selectedPreviewIndex = index.coerceIn(0, state.frames.lastIndex))
        }
    }

    fun removeFrame(index: Int) {
        _uiState.update { state ->
            if (index !in state.frames.indices) return@update state
            val updated = state.frames.toMutableList().apply { removeAt(index) }
            val nextPreview = if (updated.isEmpty()) 0 else state.selectedPreviewIndex.coerceAtMost(updated.lastIndex)
            val nextAnchorId = if (state.activeMode == StackMode.HdrExposureBlend) {
                if (state.hdrAnchorFrameId != null && updated.any { it.id == state.hdrAnchorFrameId && it.isEnabled }) {
                    state.hdrAnchorFrameId
                } else {
                    resolveMiddleExposureFrameId(updated)
                }
            } else {
                state.hdrAnchorFrameId
            }
            state.copy(frames = updated, selectedPreviewIndex = nextPreview, hdrAnchorFrameId = nextAnchorId)
        }
        val stateAfter = _uiState.value
        if (stateAfter.frames.isNotEmpty()) {
            when (stateAfter.activeMode) {
                StackMode.HdrExposureBlend -> runEvAnalysis(stateAfter.frames, autoSort = false)
                StackMode.FocusStack -> runFocusAnalysis(stateAfter.frames)
                StackMode.AstroNoiseStack -> Unit
            }
        }
    }

    fun moveFrame(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val list = state.frames
            if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
                return@update state
            }
            val mutable = list.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            val newPreview = when (state.selectedPreviewIndex) {
                fromIndex -> toIndex
                toIndex -> fromIndex
                else -> state.selectedPreviewIndex
            }
            state.copy(frames = mutable, selectedPreviewIndex = newPreview)
        }
    }

    fun setAlignmentMode(mode: AlignmentMode) {
        _uiState.update { it.copy(alignmentMode = mode) }
    }

    fun setBlendAlgorithm(algorithm: BlendAlgorithm) {
        _uiState.update { it.copy(blendAlgorithm = algorithm) }
    }

    fun setEdgeSmoothingRadius(radius: Int) {
        _uiState.update { it.copy(edgeSmoothingRadius = radius.coerceIn(1, 10)) }
    }

    fun setAutoCropBorders(enabled: Boolean) {
        _uiState.update { it.copy(autoCropBorders = enabled) }
    }

    fun selectHdrPreset(preset: HdrStylePreset) {
        _uiState.update {
            it.copy(
                selectedHdrPreset = preset,
                hdrContrastWeight = preset.contrastWeight,
                hdrSaturationWeight = preset.saturationWeight,
                hdrExposureWeight = preset.exposureWeight
            )
        }
    }

    fun setHdrContrastWeight(weight: Float) {
        _uiState.update { state ->
            val clamped = weight.coerceIn(0f, 2f)
            state.copy(
                hdrContrastWeight = clamped,
                selectedHdrPreset = findMatchingHdrPreset(
                    clamped,
                    state.hdrSaturationWeight,
                    state.hdrExposureWeight
                )
            )
        }
    }

    fun setHdrSaturationWeight(weight: Float) {
        _uiState.update { state ->
            val clamped = weight.coerceIn(0f, 2f)
            state.copy(
                hdrSaturationWeight = clamped,
                selectedHdrPreset = findMatchingHdrPreset(
                    state.hdrContrastWeight,
                    clamped,
                    state.hdrExposureWeight
                )
            )
        }
    }

    fun setHdrExposureWeight(weight: Float) {
        _uiState.update { state ->
            val clamped = weight.coerceIn(0f, 2f)
            state.copy(
                hdrExposureWeight = clamped,
                selectedHdrPreset = findMatchingHdrPreset(
                    state.hdrContrastWeight,
                    state.hdrSaturationWeight,
                    clamped
                )
            )
        }
    }

    private fun findMatchingHdrPreset(c: Float, s: Float, e: Float): HdrStylePreset? {
        return HdrStylePreset.entries.firstOrNull { preset ->
            abs(preset.contrastWeight - c) < 0.02f &&
                abs(preset.saturationWeight - s) < 0.02f &&
                abs(preset.exposureWeight - e) < 0.02f
        }
    }

    fun setHdrDeghostingEnabled(enabled: Boolean) {
        _uiState.update { it.copy(hdrDeghostingEnabled = enabled) }
    }

    // --- Astro & Low-Light Calibration & Settings ---

    fun setAstroFrameType(index: Int, frameType: AstroFrameType) {
        _uiState.update { state ->
            if (index !in state.frames.indices) return@update state
            val updated = state.frames.toMutableList()
            updated[index] = updated[index].copy(astroFrameType = frameType)
            state.copy(frames = updated)
        }
    }

    fun sortAstroFramesLightsFirst() {
        val current = _uiState.value
        if (current.frames.size < 2) return
        val selectedId = current.frames.getOrNull(current.selectedPreviewIndex)?.id
        val sorted = current.frames.sortedBy { it.astroFrameType.ordinal }
        val newSelectedIdx = selectedId?.let { id ->
            sorted.indexOfFirst { it.id == id }.takeIf { it >= 0 }
        } ?: 0
        _uiState.update {
            it.copy(
                frames = sorted,
                selectedPreviewIndex = newSelectedIdx,
                snackbarMessage = "Sorted Light frames first, Dark frames last"
            )
        }
    }

    fun setAstroAlignmentMode(mode: AstroAlignmentMode) {
        _uiState.update { it.copy(astroAlignmentMode = mode) }
    }

    fun setAstroStackAlgorithm(algorithm: AstroStackAlgorithm) {
        _uiState.update { it.copy(astroStackAlgorithm = algorithm) }
    }

    fun setAstroSigmaThreshold(sigma: Float) {
        val stepped = ((sigma * 10f).roundToInt() / 10f).coerceIn(1.2f, 4.5f)
        _uiState.update { it.copy(astroSigmaThreshold = stepped) }
    }

    fun setAstroFreezeGroundEnabled(enabled: Boolean) {
        _uiState.update { it.copy(astroFreezeGroundEnabled = enabled) }
    }

    fun addAstroFreezeGroundStroke(stroke: ForegroundMaskStroke) {
        if (stroke.points.isEmpty()) return
        _uiState.update { state ->
            state.copy(astroFreezeGroundStrokes = state.astroFreezeGroundStrokes + stroke)
        }
    }

    fun undoAstroFreezeGroundStroke() {
        _uiState.update { state ->
            if (state.astroFreezeGroundStrokes.isEmpty()) state
            else state.copy(astroFreezeGroundStrokes = state.astroFreezeGroundStrokes.dropLast(1))
        }
    }

    fun clearAstroFreezeGroundStrokes() {
        _uiState.update { it.copy(astroFreezeGroundStrokes = emptyList()) }
    }

    fun updateMaxResolution(preset: MaxResolutionPreset) {
        viewModelScope.launch { settingsRepository.updateMaxResolution(preset) }
    }

    fun updateOutputFormat(format: OutputFormat) {
        viewModelScope.launch { settingsRepository.updateOutputFormat(format) }
    }

    fun updateJpegQuality(quality: Int) {
        viewModelScope.launch { settingsRepository.updateJpegQuality(quality) }
    }

    fun startFocusStacking() {
        startStacking()
    }

    fun startStacking() {
        val current = _uiState.value
        val activeFrames = current.frames.filter { it.isEnabled }
        if (current.activeMode == StackMode.AstroNoiseStack) {
            val activeLights = activeFrames.count { it.astroFrameType == AstroFrameType.LIGHT }
            if (activeLights < 2) {
                postMessage("Enable at least 2 Light frames to stack (Dark frames are optional).")
                return
            }
        } else if (activeFrames.size < 2) {
            val noun = if (current.activeMode == StackMode.HdrExposureBlend) "bracketed frames" else "frames"
            postMessage("Enable at least 2 $noun to process.")
            return
        }
        if (current.processingState is ProcessingUiState.Running) return

        stackingJob?.cancel()
        stackingJob = viewModelScope.launch(Dispatchers.Default) {
            val initMsg = when (current.activeMode) {
                StackMode.HdrExposureBlend -> "Initializing Mertens HDR exposure fusion..."
                StackMode.AstroNoiseStack -> "Initializing Astro calibration & alignment engine..."
                StackMode.FocusStack -> "Initializing OpenCV darkroom engine..."
            }
            _uiState.update {
                it.copy(
                    processingState = ProcessingUiState.Running(
                        StackProgress(1, 4, 0.02f, initMsg)
                    )
                )
            }

            val powerManager = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = try {
                powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StackLab:StackingWakeLock")?.apply {
                    setReferenceCounted(false)
                    acquire(15 * 60 * 1000L)
                }
            } catch (_: Throwable) {
                null
            }

            try {
                withContext(Dispatchers.IO) {
                    bitmapLoader.clearRetouchCache()
                }

                val onProgress: (StackProgress) -> Unit = { progress ->
                    _uiState.update { state ->
                        if (state.processingState is ProcessingUiState.Running) {
                            state.copy(processingState = ProcessingUiState.Running(progress))
                        } else state
                    }
                }

                val result = when (current.activeMode) {
                    StackMode.HdrExposureBlend -> {
                        val resolvedAnchorIdx = current.hdrAnchorFrameId?.let { id ->
                            activeFrames.indexOfFirst { it.id == id }.takeIf { it >= 0 }
                        }
                        val hdrConfig = HdrConfig(
                            alignmentMode = current.alignmentMode,
                            contrastWeight = current.hdrContrastWeight,
                            saturationWeight = current.hdrSaturationWeight,
                            exposureWeight = current.hdrExposureWeight,
                            deghostingEnabled = current.hdrDeghostingEnabled,
                            anchorFrameIndex = resolvedAnchorIdx,
                            autoCropBorders = current.autoCropBorders,
                            outputSettings = current.outputSettings
                        )
                        hdrStackProcessor.process(
                            frames = activeFrames,
                            config = hdrConfig,
                            onProgress = onProgress
                        )
                    }
                    StackMode.AstroNoiseStack -> {
                        val astroConfig = AstroConfig(
                            alignmentMode = current.astroAlignmentMode,
                            stackAlgorithm = current.astroStackAlgorithm,
                            sigmaClippingThreshold = current.astroSigmaThreshold,
                            freezeGroundEnabled = current.astroFreezeGroundEnabled,
                            freezeGroundStrokes = current.astroFreezeGroundStrokes,
                            autoCropBorders = current.autoCropBorders,
                            outputSettings = current.outputSettings
                        )
                        astroStackProcessor.process(
                            frames = activeFrames,
                            config = astroConfig,
                            onProgress = onProgress
                        )
                    }
                    StackMode.FocusStack -> {
                        val focusConfig = FocusStackConfig(
                            alignmentMode = current.alignmentMode,
                            blendAlgorithm = current.blendAlgorithm,
                            edgeSmoothingRadius = current.edgeSmoothingRadius,
                            autoCropBorders = current.autoCropBorders,
                            outputSettings = current.outputSettings
                        )
                        focusStackProcessor.process(
                            frames = activeFrames,
                            config = focusConfig,
                            onProgress = onProgress
                        )
                    }
                }

                clearRetouchAndPeakingState()
                recycleResultBitmaps(_uiState.value.result)
                baseOutputBitmap = result.outputBitmap.copy(Bitmap.Config.ARGB_8888, false)
                workingOutputBitmap = result.outputBitmap

                val initialTone = if (result.stackMode == StackMode.HdrExposureBlend) {
                    current.selectedHdrPreset?.defaultTone ?: HdrToneAdjustments()
                } else {
                    HdrToneAdjustments()
                }
                val initialAstroStretch = AstroStretchAdjustments()
                val previewBase = when (result.stackMode) {
                    StackMode.HdrExposureBlend -> HdrToneEngine.createFastPreviewBase(result.outputBitmap)
                    StackMode.AstroNoiseStack -> AstroPostEngine.createFastPreviewBase(result.outputBitmap)
                    StackMode.FocusStack -> null
                }
                tonePreviewBaseBitmap = previewBase

                val finalDisplayResult = if (
                    result.stackMode == StackMode.HdrExposureBlend &&
                    previewBase != null &&
                    !initialTone.isIdentity()
                ) {
                    val tonedPreview = HdrToneEngine.applyToneAndClarity(previewBase, initialTone)
                    result.copy(outputBitmap = tonedPreview)
                } else {
                    result
                }

                _uiState.update {
                    it.copy(
                        processingState = ProcessingUiState.Idle,
                        result = finalDisplayResult,
                        hdrToneAdjustments = initialTone,
                        astroStretchAdjustments = initialAstroStretch,
                        isApplyingTone = false,
                        retouchBitmapVersion = 1,
                        retouchSourceIndex = finalDisplayResult.defaultComparisonIndex.coerceIn(
                            0,
                            (finalDisplayResult.sourcePreviewBitmaps.size - 1).coerceAtLeast(0)
                        ),
                        lastSavedPath = null
                    )
                }
                _navEvents.tryEmit(StackLabNavEvent.OpenResult)
            } catch (_: CancellationException) {
                bitmapLoader.clearRetouchCache()
                _uiState.update {
                    it.copy(
                        processingState = ProcessingUiState.Idle,
                        snackbarMessage = "Stacking cancelled"
                    )
                }
            } catch (t: Throwable) {
                bitmapLoader.clearRetouchCache()
                _uiState.update {
                    it.copy(
                        processingState = ProcessingUiState.Idle,
                        snackbarMessage = t.message ?: "Stacking failed. Try fewer frames or lower output resolution."
                    )
                }
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Throwable) {
                }
                System.gc()
            }
        }
    }

    fun cancelStacking() {
        stackingJob?.cancel()
        stackingJob = null
        _uiState.update { it.copy(processingState = ProcessingUiState.Idle) }
    }

    // --- Manual Retouch Mode ---

    fun setRetouchMode(enabled: Boolean) {
        if (!enabled) {
            retouchSourceJob?.cancel()
            releaseActiveRetouchSourceBitmap()
            _uiState.update { it.copy(isRetouchMode = false) }
            return
        }
        val result = _uiState.value.result
        val targetIndex = if (result != null) {
            _uiState.value.retouchSourceIndex.coerceIn(0, (result.sourcePreviewBitmaps.size - 1).coerceAtLeast(0))
        } else 0
        _uiState.update { it.copy(isRetouchMode = true, retouchSourceIndex = targetIndex) }
        if (result != null) {
            preloadSingleRetouchSourceFrame(result, targetIndex)
        }
    }

    fun selectRetouchSourceFrame(index: Int) {
        val state = _uiState.value
        val result = state.result ?: return
        val clamped = index.coerceIn(0, result.sourcePreviewBitmaps.lastIndex)
        _uiState.update { it.copy(retouchSourceIndex = clamped) }
        if (state.isRetouchMode) {
            preloadSingleRetouchSourceFrame(result, clamped)
        }
    }

    private fun preloadSingleRetouchSourceFrame(result: StackResult, index: Int) {
        retouchSourceJob?.cancel()
        retouchSourceJob = viewModelScope.launch(Dispatchers.IO) {
            loadOrGetActiveRetouchSource(result, index)
        }
    }

    fun setRetouchBrushSize(fraction: Float) {
        _uiState.update { it.copy(retouchBrushSizeFraction = fraction.coerceIn(0.015f, 0.18f)) }
    }

    fun setRetouchFeather(feather: Float) {
        _uiState.update { it.copy(retouchFeather = feather.coerceIn(0f, 1f)) }
    }

    fun setRetouchOpacity(opacity: Float) {
        _uiState.update { it.copy(retouchOpacity = opacity.coerceIn(0.1f, 1f)) }
    }

    fun commitRetouchStroke(points: List<RetouchPoint>) {
        val state = _uiState.value
        val result = state.result ?: return
        if (points.isEmpty() || state.isApplyingRetouch) return

        val stroke = RetouchStroke(
            sourceFrameIndex = state.retouchSourceIndex.coerceIn(0, result.sourcePreviewBitmaps.lastIndex),
            points = points,
            brushSizeFraction = state.retouchBrushSizeFraction,
            feather = state.retouchFeather,
            opacity = state.retouchOpacity
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingRetouch = true) }
            try {
                val (displayBitmap, newMask, newPeaking) = withContext(Dispatchers.Default) {
                    val working = ensureMutableWorkingBitmap(baseOutputBitmap ?: result.outputBitmap)
                    synchronized(retouchSourceLock) {
                        val sourceBmp = loadOrGetActiveRetouchSource(result, stroke.sourceFrameIndex)
                        RetouchAndPeakingUtils.applyRetouchStroke(working, sourceBmp, stroke)
                    }

                    undoStrokes.add(stroke)
                    redoStrokes.clear()

                    val display = refreshPreviewAndApplyTone(working, result.stackMode, _uiState.value.hdrToneAdjustments)

                    val mask = RetouchAndPeakingUtils.renderRetouchMaskOverlay(
                        width = working.width,
                        height = working.height,
                        strokes = undoStrokes
                    )
                    val peaking = if (_uiState.value.isFocusPeakingEnabled) {
                        RetouchAndPeakingUtils.generateFocusPeakingOverlay(display)
                    } else null

                    Triple(display, mask, peaking)
                }

                val oldMask = _uiState.value.retouchMaskBitmap
                val oldPeaking = _uiState.value.focusPeakingOverlayBitmap
                var oldDisplay: Bitmap? = null
                _uiState.update { current ->
                    oldDisplay = current.result?.outputBitmap
                    current.copy(
                        result = current.result?.copy(outputBitmap = displayBitmap),
                        retouchBitmapVersion = current.retouchBitmapVersion + 1,
                        retouchUndoCount = undoStrokes.size,
                        retouchRedoCount = 0,
                        retouchMaskBitmap = newMask,
                        focusPeakingOverlayBitmap = newPeaking ?: current.focusPeakingOverlayBitmap,
                        isApplyingRetouch = false,
                        lastSavedPath = null
                    )
                }
                safeRecycleBitmapIfUnused(oldDisplay, keep1 = displayBitmap)
                if (oldMask != null && oldMask !== newMask && !oldMask.isRecycled) oldMask.recycle()
                if (newPeaking != null && oldPeaking != null && oldPeaking !== newPeaking && !oldPeaking.isRecycled) {
                    oldPeaking.recycle()
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isApplyingRetouch = false,
                        snackbarMessage = "Retouch failed: ${t.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun undoRetouchStroke() {
        val state = _uiState.value
        val result = state.result ?: return
        if (undoStrokes.isEmpty() || state.isApplyingRetouch) return

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingRetouch = true) }
            val oldWorking = workingOutputBitmap
            val (rebuiltDisplay, newMask, newPeaking) = withContext(Dispatchers.Default) {
                val popped = undoStrokes.removeAt(undoStrokes.lastIndex)
                redoStrokes.add(popped)

                val base = baseOutputBitmap ?: result.outputBitmap
                val freshWorking = base.copy(Bitmap.Config.ARGB_8888, true)
                synchronized(retouchSourceLock) {
                    for (s in undoStrokes) {
                        val src = loadOrGetActiveRetouchSource(result, s.sourceFrameIndex)
                        RetouchAndPeakingUtils.applyRetouchStroke(freshWorking, src, s)
                    }
                }
                workingOutputBitmap = freshWorking

                val display = refreshPreviewAndApplyTone(freshWorking, result.stackMode, _uiState.value.hdrToneAdjustments)

                val mask = if (undoStrokes.isNotEmpty()) {
                    RetouchAndPeakingUtils.renderRetouchMaskOverlay(
                        width = freshWorking.width,
                        height = freshWorking.height,
                        strokes = undoStrokes
                    )
                } else null

                val peaking = if (_uiState.value.isFocusPeakingEnabled) {
                    RetouchAndPeakingUtils.generateFocusPeakingOverlay(display)
                } else null

                Triple(display, mask, peaking)
            }

            val oldMask = _uiState.value.retouchMaskBitmap
            val oldPeaking = _uiState.value.focusPeakingOverlayBitmap
            var oldDisplay: Bitmap? = null
            _uiState.update { current ->
                oldDisplay = current.result?.outputBitmap
                current.copy(
                    result = current.result?.copy(outputBitmap = rebuiltDisplay),
                    retouchBitmapVersion = current.retouchBitmapVersion + 1,
                    retouchUndoCount = undoStrokes.size,
                    retouchRedoCount = redoStrokes.size,
                    retouchMaskBitmap = newMask,
                    focusPeakingOverlayBitmap = newPeaking ?: current.focusPeakingOverlayBitmap,
                    isApplyingRetouch = false,
                    lastSavedPath = null
                )
            }
            safeRecycleBitmapIfUnused(oldDisplay, keep1 = rebuiltDisplay)
            safeRecycleBitmapIfUnused(oldWorking, keep1 = rebuiltDisplay)
            if (oldMask != null && oldMask !== newMask && !oldMask.isRecycled) oldMask.recycle()
            if (newPeaking != null && oldPeaking != null && oldPeaking !== newPeaking && !oldPeaking.isRecycled) {
                oldPeaking.recycle()
            }
        }
    }

    fun redoRetouchStroke() {
        val state = _uiState.value
        val result = state.result ?: return
        if (redoStrokes.isEmpty() || state.isApplyingRetouch) return

        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingRetouch = true) }
            val (displayBitmap, newMask, newPeaking) = withContext(Dispatchers.Default) {
                val stroke = redoStrokes.removeAt(redoStrokes.lastIndex)
                val working = ensureMutableWorkingBitmap(baseOutputBitmap ?: result.outputBitmap)
                synchronized(retouchSourceLock) {
                    val src = loadOrGetActiveRetouchSource(result, stroke.sourceFrameIndex)
                    RetouchAndPeakingUtils.applyRetouchStroke(working, src, stroke)
                }
                undoStrokes.add(stroke)

                val display = refreshPreviewAndApplyTone(working, result.stackMode, _uiState.value.hdrToneAdjustments)

                val mask = RetouchAndPeakingUtils.renderRetouchMaskOverlay(
                    width = working.width,
                    height = working.height,
                    strokes = undoStrokes
                )
                val peaking = if (_uiState.value.isFocusPeakingEnabled) {
                    RetouchAndPeakingUtils.generateFocusPeakingOverlay(display)
                } else null

                Triple(display, mask, peaking)
            }

            val oldMask = _uiState.value.retouchMaskBitmap
            val oldPeaking = _uiState.value.focusPeakingOverlayBitmap
            var oldDisplay: Bitmap? = null
            _uiState.update { current ->
                oldDisplay = current.result?.outputBitmap
                current.copy(
                    result = current.result?.copy(outputBitmap = displayBitmap),
                    retouchBitmapVersion = current.retouchBitmapVersion + 1,
                    retouchUndoCount = undoStrokes.size,
                    retouchRedoCount = redoStrokes.size,
                    retouchMaskBitmap = newMask,
                    focusPeakingOverlayBitmap = newPeaking ?: current.focusPeakingOverlayBitmap,
                    isApplyingRetouch = false,
                    lastSavedPath = null
                )
            }
            safeRecycleBitmapIfUnused(oldDisplay, keep1 = displayBitmap)
            if (oldMask != null && oldMask !== newMask && !oldMask.isRecycled) oldMask.recycle()
            if (newPeaking != null && oldPeaking != null && oldPeaking !== newPeaking && !oldPeaking.isRecycled) {
                oldPeaking.recycle()
            }
        }
    }

    private fun ensureMutableWorkingBitmap(currentBitmap: Bitmap): Bitmap {
        val existing = workingOutputBitmap
        if (existing != null && existing.isMutable && !existing.isRecycled) {
            return existing
        }
        val copy = currentBitmap.copy(Bitmap.Config.ARGB_8888, true)
        workingOutputBitmap = copy
        return copy
    }

    private fun loadOrGetActiveRetouchSource(result: StackResult, index: Int): Bitmap {
        synchronized(retouchSourceLock) {
            val cached = activeRetouchSourceBitmap
            if (activeRetouchSourceIndex == index && cached != null && !cached.isRecycled) {
                return cached
            }
            val cachePath = result.alignedSourceCachePaths.getOrNull(index)
            val loaded = if (cachePath != null) bitmapLoader.loadCachedBitmap(cachePath) else null
            val resolved = loaded ?: result.sourcePreviewBitmaps[index]

            if (cached != null && cached !== resolved && cached !in result.sourcePreviewBitmaps && !cached.isRecycled) {
                cached.recycle()
            }
            activeRetouchSourceIndex = index
            activeRetouchSourceBitmap = resolved
            return resolved
        }
    }

    private fun releaseActiveRetouchSourceBitmap() {
        synchronized(retouchSourceLock) {
            val result = _uiState.value.result
            val cached = activeRetouchSourceBitmap
            if (cached != null &&
                (result == null || cached !in result.sourcePreviewBitmaps) &&
                !cached.isRecycled
            ) {
                cached.recycle()
            }
            activeRetouchSourceBitmap = null
            activeRetouchSourceIndex = -1
        }
    }

    // --- Focus Peaking Overlay ---

    fun toggleFocusPeaking(compareIndex: Int) {
        val nextEnabled = !_uiState.value.isFocusPeakingEnabled
        if (!nextEnabled) {
            peakingJob?.cancel()
            val oldOut = _uiState.value.focusPeakingOverlayBitmap
            val oldCmp = _uiState.value.comparePeakingOverlayBitmap
            _uiState.update {
                it.copy(
                    isFocusPeakingEnabled = false,
                    focusPeakingOverlayBitmap = null,
                    comparePeakingOverlayBitmap = null,
                    comparePeakingFrameIndex = -1
                )
            }
            if (oldOut != null && !oldOut.isRecycled) oldOut.recycle()
            if (oldCmp != null && !oldCmp.isRecycled) oldCmp.recycle()
            return
        }

        _uiState.update { it.copy(isFocusPeakingEnabled = true) }
        refreshFocusPeakingOverlays(compareIndex)
    }

    fun updateComparePeakingIfEnabled(compareIndex: Int) {
        if (!_uiState.value.isFocusPeakingEnabled) return
        if (_uiState.value.comparePeakingFrameIndex == compareIndex &&
            _uiState.value.comparePeakingOverlayBitmap != null
        ) {
            return
        }
        refreshFocusPeakingOverlays(compareIndex)
    }

    private fun refreshFocusPeakingOverlays(compareIndex: Int) {
        val result = _uiState.value.result ?: return
        val safeCompareIdx = compareIndex.coerceIn(0, result.sourcePreviewBitmaps.lastIndex)
        peakingJob?.cancel()
        peakingJob = viewModelScope.launch {
            val existingOut = _uiState.value.focusPeakingOverlayBitmap
            val existingCmp = _uiState.value.comparePeakingOverlayBitmap
            val needOut = existingOut == null || existingOut.isRecycled
            val needCmp = existingCmp == null || existingCmp.isRecycled ||
                _uiState.value.comparePeakingFrameIndex != safeCompareIdx

            val (newOut, newCmp) = withContext(Dispatchers.Default) {
                val outBmp = if (needOut) {
                    RetouchAndPeakingUtils.generateFocusPeakingOverlay(result.outputBitmap)
                } else existingOut

                val cmpBmp = if (needCmp) {
                    RetouchAndPeakingUtils.generateFocusPeakingOverlay(result.sourcePreviewBitmaps[safeCompareIdx])
                } else existingCmp

                outBmp to cmpBmp
            }

            _uiState.update {
                if (!it.isFocusPeakingEnabled) it
                else it.copy(
                    focusPeakingOverlayBitmap = newOut,
                    comparePeakingOverlayBitmap = newCmp,
                    comparePeakingFrameIndex = safeCompareIdx
                )
            }
            if (needOut && existingOut != null && existingOut !== newOut && !existingOut.isRecycled) {
                existingOut.recycle()
            }
            if (needCmp && existingCmp != null && existingCmp !== newCmp && !existingCmp.isRecycled) {
                existingCmp.recycle()
            }
        }
    }

    private fun refreshPreviewAndApplyTone(
        fullResWorking: Bitmap,
        stackMode: StackMode,
        toneAdjustments: HdrToneAdjustments
    ): Bitmap {
        return when (stackMode) {
            StackMode.FocusStack -> fullResWorking
            StackMode.HdrExposureBlend -> {
                val oldPreviewBase = tonePreviewBaseBitmap
                val newPreviewBase = HdrToneEngine.createFastPreviewBase(fullResWorking)
                tonePreviewBaseBitmap = newPreviewBase
                if (oldPreviewBase != null &&
                    oldPreviewBase !== fullResWorking &&
                    oldPreviewBase !== newPreviewBase &&
                    !oldPreviewBase.isRecycled
                ) {
                    oldPreviewBase.recycle()
                }
                if (toneAdjustments.isIdentity()) {
                    newPreviewBase
                } else {
                    HdrToneEngine.applyToneAndClarity(newPreviewBase, toneAdjustments)
                }
            }
            StackMode.AstroNoiseStack -> {
                val oldPreviewBase = tonePreviewBaseBitmap
                val newPreviewBase = AstroPostEngine.createFastPreviewBase(fullResWorking)
                tonePreviewBaseBitmap = newPreviewBase
                if (oldPreviewBase != null &&
                    oldPreviewBase !== fullResWorking &&
                    oldPreviewBase !== newPreviewBase &&
                    !oldPreviewBase.isRecycled
                ) {
                    oldPreviewBase.recycle()
                }
                val astroStretch = _uiState.value.astroStretchAdjustments
                if (astroStretch.isIdentity()) {
                    newPreviewBase
                } else {
                    AstroPostEngine.applyPostProcessing(newPreviewBase, astroStretch)
                }
            }
        }
    }

    // --- Post-Fusion Tone & Clarity Engine ---

    fun updateHdrToneAdjustments(adjustments: HdrToneAdjustments) {
        val result = _uiState.value.result ?: return
        if (result.stackMode != StackMode.HdrExposureBlend) return

        val clamped = HdrToneAdjustments(
            highlightRecovery = adjustments.highlightRecovery.coerceIn(0f, 1f),
            shadowLift = adjustments.shadowLift.coerceIn(0f, 1f),
            clarity = adjustments.clarity.coerceIn(0f, 1f),
            vibrance = adjustments.vibrance.coerceIn(0f, 1f)
        )

        _uiState.update {
            it.copy(
                hdrToneAdjustments = clamped,
                isApplyingTone = true,
                lastSavedPath = null
            )
        }

        toneJob?.cancel()
        toneJob = viewModelScope.launch {
            try {
                val previewBase = tonePreviewBaseBitmap ?: run {
                    val src = workingOutputBitmap ?: baseOutputBitmap ?: result.outputBitmap
                    HdrToneEngine.createFastPreviewBase(src).also { tonePreviewBaseBitmap = it }
                }

                val (updatedDisplay, updatedPeaking) = withContext(Dispatchers.Default) {
                    val bmp = if (clamped.isIdentity()) {
                        previewBase
                    } else {
                        HdrToneEngine.applyToneAndClarity(previewBase, clamped)
                    }
                    val peaking = if (_uiState.value.isFocusPeakingEnabled) {
                        RetouchAndPeakingUtils.generateFocusPeakingOverlay(bmp)
                    } else null
                    bmp to peaking
                }

                val oldPeaking = _uiState.value.focusPeakingOverlayBitmap
                var oldDisplay: Bitmap? = null
                _uiState.update { current ->
                    oldDisplay = current.result?.outputBitmap
                    current.copy(
                        result = current.result?.copy(outputBitmap = updatedDisplay),
                        retouchBitmapVersion = current.retouchBitmapVersion + 1,
                        focusPeakingOverlayBitmap = updatedPeaking ?: current.focusPeakingOverlayBitmap,
                        isApplyingTone = false
                    )
                }
                safeRecycleBitmapIfUnused(oldDisplay, keep1 = updatedDisplay)
                if (updatedPeaking != null && oldPeaking != null && oldPeaking !== updatedPeaking && !oldPeaking.isRecycled) {
                    oldPeaking.recycle()
                }
            } catch (_: CancellationException) {
                // Superseded by newer slider tick
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isApplyingTone = false,
                        snackbarMessage = "Tone update failed: ${t.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun resetHdrToneAdjustments() {
        updateHdrToneAdjustments(HdrToneAdjustments())
    }

    // --- Post-Stack Astro Stretch & Sky Engine ---

    fun updateAstroStretchAdjustments(adjustments: AstroStretchAdjustments) {
        val result = _uiState.value.result ?: return
        if (result.stackMode != StackMode.AstroNoiseStack) return

        val clamped = AstroStretchAdjustments(
            asinhStretch = adjustments.asinhStretch.coerceIn(0f, 1f),
            lightPollutionRemoval = adjustments.lightPollutionRemoval.coerceIn(0f, 1f),
            chromaDenoise = adjustments.chromaDenoise.coerceIn(0f, 1f)
        )

        _uiState.update {
            it.copy(
                astroStretchAdjustments = clamped,
                isApplyingTone = true,
                lastSavedPath = null
            )
        }

        toneJob?.cancel()
        toneJob = viewModelScope.launch {
            try {
                val previewBase = tonePreviewBaseBitmap ?: run {
                    val src = workingOutputBitmap ?: baseOutputBitmap ?: result.outputBitmap
                    AstroPostEngine.createFastPreviewBase(src).also { tonePreviewBaseBitmap = it }
                }

                val (updatedDisplay, updatedPeaking) = withContext(Dispatchers.Default) {
                    val bmp = if (clamped.isIdentity()) {
                        previewBase
                    } else {
                        AstroPostEngine.applyPostProcessing(previewBase, clamped)
                    }
                    val peaking = if (_uiState.value.isFocusPeakingEnabled) {
                        RetouchAndPeakingUtils.generateFocusPeakingOverlay(bmp)
                    } else null
                    bmp to peaking
                }

                val oldPeaking = _uiState.value.focusPeakingOverlayBitmap
                var oldDisplay: Bitmap? = null
                _uiState.update { current ->
                    oldDisplay = current.result?.outputBitmap
                    current.copy(
                        result = current.result?.copy(outputBitmap = updatedDisplay),
                        retouchBitmapVersion = current.retouchBitmapVersion + 1,
                        focusPeakingOverlayBitmap = updatedPeaking ?: current.focusPeakingOverlayBitmap,
                        isApplyingTone = false
                    )
                }
                safeRecycleBitmapIfUnused(oldDisplay, keep1 = updatedDisplay)
                if (updatedPeaking != null && oldPeaking != null && oldPeaking !== updatedPeaking && !oldPeaking.isRecycled) {
                    oldPeaking.recycle()
                }
            } catch (_: CancellationException) {
                // Superseded by newer slider tick
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isApplyingTone = false,
                        snackbarMessage = "Astro stretch failed: ${t.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun resetAstroStretchAdjustments() {
        updateAstroStretchAdjustments(AstroStretchAdjustments())
    }

    fun saveFullResolutionResult() {
        val state = _uiState.value
        val result = state.result ?: return
        if (state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val outcome = withContext(Dispatchers.Default) {
                    val fullResBase = workingOutputBitmap?.takeIf { !it.isRecycled }
                        ?: baseOutputBitmap?.takeIf { !it.isRecycled }
                        ?: result.outputBitmap

                    val needsFullResHdrTone = result.stackMode == StackMode.HdrExposureBlend &&
                        !state.hdrToneAdjustments.isIdentity()
                    val needsFullResAstroPost = result.stackMode == StackMode.AstroNoiseStack &&
                        !state.astroStretchAdjustments.isIdentity()

                    val exportBitmap = when {
                        needsFullResHdrTone ->
                            HdrToneEngine.applyToneAndClarity(fullResBase, state.hdrToneAdjustments)
                        needsFullResAstroPost ->
                            AstroPostEngine.applyPostProcessing(fullResBase, state.astroStretchAdjustments)
                        else -> fullResBase
                    }

                    try {
                        mediaStoreExporter.saveToGallery(
                            bitmap = exportBitmap,
                            settings = state.outputSettings,
                            referenceFrameUri = result.referenceFrameUri
                        )
                    } finally {
                        if ((needsFullResHdrTone || needsFullResAstroPost) &&
                            exportBitmap !== fullResBase &&
                            !exportBitmap.isRecycled
                        ) {
                            exportBitmap.recycle()
                        }
                    }
                }
                val formatSummary = when (state.outputSettings.outputFormat) {
                    OutputFormat.JPEG -> "JPEG (${state.outputSettings.jpegQuality}%) with EXIF"
                    OutputFormat.PNG -> "lossless PNG"
                }
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        lastSavedPath = outcome.displayPath,
                        snackbarMessage = "Saved $formatSummary to ${outcome.displayPath}"
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        snackbarMessage = "Export failed: ${t.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun onExitResultScreen() {
        clearRetouchAndPeakingState()
        recycleResultBitmaps(_uiState.value.result)
        bitmapLoader.clearRetouchCache()
        _uiState.update {
            it.copy(
                hdrToneAdjustments = HdrToneAdjustments(),
                astroStretchAdjustments = AstroStretchAdjustments(),
                result = null,
                retouchBitmapVersion = 0,
                lastSavedPath = null
            )
        }
        System.gc()
    }

    fun startNewStack() {
        clearRetouchAndPeakingState()
        recycleResultBitmaps(_uiState.value.result)
        bitmapLoader.clearRetouchCache()
        _uiState.update {
            it.copy(
                frames = emptyList(),
                selectedPreviewIndex = 0,
                hdrAnchorFrameId = null,
                hdrToneAdjustments = HdrToneAdjustments(),
                astroFreezeGroundStrokes = emptyList(),
                astroStretchAdjustments = AstroStretchAdjustments(),
                result = null,
                retouchBitmapVersion = 0,
                lastSavedPath = null,
                processingState = ProcessingUiState.Idle
            )
        }
        System.gc()
        _navEvents.tryEmit(StackLabNavEvent.ReturnHome)
    }

    fun onComingSoonModeClicked(mode: StackMode) {
        postMessage("${mode.title} processor is coming in an upcoming StackLab release.")
    }

    fun postMessage(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun safeRecycleBitmapIfUnused(candidate: Bitmap?, keep1: Bitmap? = null) {
        if (candidate == null || candidate.isRecycled) return
        if (candidate === keep1 ||
            candidate === baseOutputBitmap ||
            candidate === workingOutputBitmap ||
            candidate === tonePreviewBaseBitmap
        ) {
            return
        }
        candidate.recycle()
    }

    private fun clearRetouchAndPeakingState() {
        peakingJob?.cancel()
        toneJob?.cancel()
        retouchSourceJob?.cancel()
        undoStrokes.clear()
        redoStrokes.clear()

        val state = _uiState.value
        val result = state.result

        releaseActiveRetouchSourceBitmap()

        if (tonePreviewBaseBitmap != null &&
            tonePreviewBaseBitmap !== result?.outputBitmap &&
            tonePreviewBaseBitmap !== workingOutputBitmap &&
            tonePreviewBaseBitmap !== baseOutputBitmap &&
            tonePreviewBaseBitmap?.isRecycled == false
        ) {
            tonePreviewBaseBitmap?.recycle()
        }
        tonePreviewBaseBitmap = null

        if (baseOutputBitmap != null && baseOutputBitmap !== result?.outputBitmap && baseOutputBitmap?.isRecycled == false) {
            baseOutputBitmap?.recycle()
        }
        baseOutputBitmap = null
        if (workingOutputBitmap != null &&
            workingOutputBitmap !== result?.outputBitmap &&
            workingOutputBitmap?.isRecycled == false
        ) {
            workingOutputBitmap?.recycle()
        }
        workingOutputBitmap = null

        if (state.retouchMaskBitmap?.isRecycled == false) state.retouchMaskBitmap.recycle()
        if (state.focusPeakingOverlayBitmap?.isRecycled == false) state.focusPeakingOverlayBitmap.recycle()
        if (state.comparePeakingOverlayBitmap?.isRecycled == false) state.comparePeakingOverlayBitmap.recycle()

        _uiState.update {
            it.copy(
                isRetouchMode = false,
                retouchSourceIndex = 0,
                retouchUndoCount = 0,
                retouchRedoCount = 0,
                retouchMaskBitmap = null,
                isApplyingRetouch = false,
                isApplyingTone = false,
                isFocusPeakingEnabled = false,
                focusPeakingOverlayBitmap = null,
                comparePeakingOverlayBitmap = null,
                comparePeakingFrameIndex = -1
            )
        }
    }

    private fun recycleResultBitmaps(result: StackResult?) {
        if (result == null) return
        if (!result.outputBitmap.isRecycled) result.outputBitmap.recycle()
        result.sourcePreviewBitmaps.forEach { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stackingJob?.cancel()
        analysisJob?.cancel()
        peakingJob?.cancel()
        toneJob?.cancel()
        retouchSourceJob?.cancel()
        clearRetouchAndPeakingState()
        recycleResultBitmaps(_uiState.value.result)
        bitmapLoader.clearRetouchCache()
    }
}
