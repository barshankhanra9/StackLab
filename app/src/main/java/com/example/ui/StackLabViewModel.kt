package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MediaStoreExporter
import com.example.data.SettingsRepository
import com.example.domain.model.AlignmentMode
import com.example.domain.model.BlendAlgorithm
import com.example.domain.model.FocusStackConfig
import com.example.domain.model.HdrConfig
import com.example.domain.model.MaxResolutionPreset
import com.example.domain.model.OutputFormat
import com.example.domain.model.OutputSettings
import com.example.domain.model.RetouchPoint
import com.example.domain.model.RetouchStroke
import com.example.domain.model.StackFrame
import com.example.domain.model.StackMode
import com.example.domain.model.StackProgress
import com.example.domain.model.StackResult
import com.example.engine.EvFrameAnalyzer
import com.example.engine.FocusFrameAnalyzer
import com.example.engine.FocusStackProcessor
import com.example.engine.FrameBitmapLoader
import com.example.engine.HdrStackProcessor
import com.example.engine.RetouchAndPeakingUtils
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

    val canStartStacking: Boolean
        get() = enabledFramesCount >= 2 && processingState is ProcessingUiState.Idle
}

class StackLabViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val bitmapLoader = FrameBitmapLoader(application)
    private val focusAnalyzer = FocusFrameAnalyzer(bitmapLoader)
    private val evAnalyzer = EvFrameAnalyzer(bitmapLoader)
    private val focusStackProcessor = FocusStackProcessor(bitmapLoader)
    private val hdrStackProcessor = HdrStackProcessor(bitmapLoader)
    private val mediaStoreExporter = MediaStoreExporter(application)

    private val _uiState = MutableStateFlow(StackLabUiState())
    val uiState: StateFlow<StackLabUiState> = _uiState.asStateFlow()

    private val _navEvents = MutableSharedFlow<StackLabNavEvent>(extraBufferCapacity = 4)
    val navEvents: SharedFlow<StackLabNavEvent> = _navEvents.asSharedFlow()

    private var stackingJob: Job? = null
    private var analysisJob: Job? = null
    private var peakingJob: Job? = null

    // Retouch session buffers (kept strictly O(1) — only 1 active source frame in RAM)
    private var baseOutputBitmap: Bitmap? = null
    private var workingOutputBitmap: Bitmap? = null
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
        }
        _uiState.update { state ->
            state.copy(
                activeMode = StackMode.HdrExposureBlend,
                frames = if (switchingFromOtherMode) emptyList() else state.frames,
                selectedPreviewIndex = if (switchingFromOtherMode) 0 else state.selectedPreviewIndex,
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
                state.copy(
                    activeMode = targetMode,
                    frames = merged,
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
            if (resolvedMode == StackMode.HdrExposureBlend) {
                runEvAnalysis(mergedFrames, autoSort = true)
            } else {
                runFocusAnalysis(mergedFrames)
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

                    state.copy(
                        isAnalyzingFocus = false,
                        frames = finalFrames,
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
            state.copy(frames = updated)
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
            state.copy(frames = updated, selectedPreviewIndex = nextPreview)
        }
        val stateAfter = _uiState.value
        if (stateAfter.frames.isNotEmpty()) {
            if (stateAfter.activeMode == StackMode.HdrExposureBlend) {
                runEvAnalysis(stateAfter.frames, autoSort = false)
            } else {
                runFocusAnalysis(stateAfter.frames)
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

    fun setHdrContrastWeight(weight: Float) {
        _uiState.update { it.copy(hdrContrastWeight = weight.coerceIn(0f, 2f)) }
    }

    fun setHdrSaturationWeight(weight: Float) {
        _uiState.update { it.copy(hdrSaturationWeight = weight.coerceIn(0f, 2f)) }
    }

    fun setHdrExposureWeight(weight: Float) {
        _uiState.update { it.copy(hdrExposureWeight = weight.coerceIn(0f, 2f)) }
    }

    fun setHdrDeghostingEnabled(enabled: Boolean) {
        _uiState.update { it.copy(hdrDeghostingEnabled = enabled) }
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
        if (activeFrames.size < 2) {
            val noun = if (current.activeMode == StackMode.HdrExposureBlend) "bracketed frames" else "frames"
            postMessage("Enable at least 2 $noun to process.")
            return
        }
        if (current.processingState is ProcessingUiState.Running) return

        stackingJob?.cancel()
        stackingJob = viewModelScope.launch(Dispatchers.Default) {
            val initMsg = if (current.activeMode == StackMode.HdrExposureBlend) {
                "Initializing Mertens HDR exposure fusion..."
            } else {
                "Initializing OpenCV darkroom engine..."
            }
            _uiState.update {
                it.copy(
                    processingState = ProcessingUiState.Running(
                        StackProgress(1, 4, 0.02f, initMsg)
                    )
                )
            }
            try {
                val onProgress: (StackProgress) -> Unit = { progress ->
                    _uiState.update { state ->
                        if (state.processingState is ProcessingUiState.Running) {
                            state.copy(processingState = ProcessingUiState.Running(progress))
                        } else state
                    }
                }

                val result = if (current.activeMode == StackMode.HdrExposureBlend) {
                    val hdrConfig = HdrConfig(
                        alignmentMode = current.alignmentMode,
                        contrastWeight = current.hdrContrastWeight,
                        saturationWeight = current.hdrSaturationWeight,
                        exposureWeight = current.hdrExposureWeight,
                        deghostingEnabled = current.hdrDeghostingEnabled,
                        autoCropBorders = current.autoCropBorders,
                        outputSettings = current.outputSettings
                    )
                    hdrStackProcessor.process(
                        frames = activeFrames,
                        config = hdrConfig,
                        onProgress = onProgress
                    )
                } else {
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

                clearRetouchAndPeakingState()
                recycleResultBitmaps(_uiState.value.result)
                baseOutputBitmap = result.outputBitmap.copy(Bitmap.Config.ARGB_8888, false)
                workingOutputBitmap = result.outputBitmap

                _uiState.update {
                    it.copy(
                        processingState = ProcessingUiState.Idle,
                        result = result,
                        retouchBitmapVersion = 1,
                        retouchSourceIndex = result.defaultComparisonIndex.coerceIn(
                            0,
                            (result.sourcePreviewBitmaps.size - 1).coerceAtLeast(0)
                        ),
                        lastSavedPath = null
                    )
                }
                _navEvents.tryEmit(StackLabNavEvent.OpenResult)
            } catch (_: CancellationException) {
                _uiState.update {
                    it.copy(
                        processingState = ProcessingUiState.Idle,
                        snackbarMessage = "Stacking cancelled"
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        processingState = ProcessingUiState.Idle,
                        snackbarMessage = t.message ?: "Stacking failed. Try fewer frames or lower output resolution."
                    )
                }
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
        _uiState.update { it.copy(isRetouchMode = enabled) }
    }

    fun selectRetouchSourceFrame(index: Int) {
        val result = _uiState.value.result ?: return
        val clamped = index.coerceIn(0, result.sourcePreviewBitmaps.lastIndex)
        _uiState.update { it.copy(retouchSourceIndex = clamped) }
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
                val (updatedBitmap, newMask, newPeaking) = withContext(Dispatchers.Default) {
                    val working = ensureMutableWorkingBitmap(result.outputBitmap)
                    val sourceBmp = loadOrGetActiveRetouchSource(result, stroke.sourceFrameIndex)
                    RetouchAndPeakingUtils.applyRetouchStroke(working, sourceBmp, stroke)

                    undoStrokes.add(stroke)
                    redoStrokes.clear()

                    val mask = RetouchAndPeakingUtils.renderRetouchMaskOverlay(
                        width = working.width,
                        height = working.height,
                        strokes = undoStrokes
                    )
                    val peaking = if (_uiState.value.isFocusPeakingEnabled) {
                        RetouchAndPeakingUtils.generateFocusPeakingOverlay(working)
                    } else null

                    Triple(working, mask, peaking)
                }

                val oldMask = _uiState.value.retouchMaskBitmap
                val oldPeaking = _uiState.value.focusPeakingOverlayBitmap
                _uiState.update { current ->
                    current.copy(
                        result = current.result?.copy(outputBitmap = updatedBitmap),
                        retouchBitmapVersion = current.retouchBitmapVersion + 1,
                        retouchUndoCount = undoStrokes.size,
                        retouchRedoCount = 0,
                        retouchMaskBitmap = newMask,
                        focusPeakingOverlayBitmap = newPeaking ?: current.focusPeakingOverlayBitmap,
                        isApplyingRetouch = false,
                        lastSavedPath = null
                    )
                }
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
            val (rebuiltBitmap, newMask, newPeaking) = withContext(Dispatchers.Default) {
                val popped = undoStrokes.removeAt(undoStrokes.lastIndex)
                redoStrokes.add(popped)

                val base = baseOutputBitmap ?: result.outputBitmap
                val freshWorking = base.copy(Bitmap.Config.ARGB_8888, true)
                for (s in undoStrokes) {
                    val src = loadOrGetActiveRetouchSource(result, s.sourceFrameIndex)
                    RetouchAndPeakingUtils.applyRetouchStroke(freshWorking, src, s)
                }
                workingOutputBitmap = freshWorking

                val mask = if (undoStrokes.isNotEmpty()) {
                    RetouchAndPeakingUtils.renderRetouchMaskOverlay(
                        width = freshWorking.width,
                        height = freshWorking.height,
                        strokes = undoStrokes
                    )
                } else null

                val peaking = if (_uiState.value.isFocusPeakingEnabled) {
                    RetouchAndPeakingUtils.generateFocusPeakingOverlay(freshWorking)
                } else null

                Triple(freshWorking, mask, peaking)
            }

            val oldWorking = result.outputBitmap
            val oldMask = _uiState.value.retouchMaskBitmap
            val oldPeaking = _uiState.value.focusPeakingOverlayBitmap
            _uiState.update { current ->
                current.copy(
                    result = current.result?.copy(outputBitmap = rebuiltBitmap),
                    retouchBitmapVersion = current.retouchBitmapVersion + 1,
                    retouchUndoCount = undoStrokes.size,
                    retouchRedoCount = redoStrokes.size,
                    retouchMaskBitmap = newMask,
                    focusPeakingOverlayBitmap = newPeaking ?: current.focusPeakingOverlayBitmap,
                    isApplyingRetouch = false,
                    lastSavedPath = null
                )
            }
            if (oldWorking !== baseOutputBitmap && oldWorking !== rebuiltBitmap && !oldWorking.isRecycled) {
                oldWorking.recycle()
            }
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
            val (updatedBitmap, newMask, newPeaking) = withContext(Dispatchers.Default) {
                val stroke = redoStrokes.removeAt(redoStrokes.lastIndex)
                val working = ensureMutableWorkingBitmap(result.outputBitmap)
                val src = loadOrGetActiveRetouchSource(result, stroke.sourceFrameIndex)
                RetouchAndPeakingUtils.applyRetouchStroke(working, src, stroke)
                undoStrokes.add(stroke)

                val mask = RetouchAndPeakingUtils.renderRetouchMaskOverlay(
                    width = working.width,
                    height = working.height,
                    strokes = undoStrokes
                )
                val peaking = if (_uiState.value.isFocusPeakingEnabled) {
                    RetouchAndPeakingUtils.generateFocusPeakingOverlay(working)
                } else null

                Triple(working, mask, peaking)
            }

            val oldMask = _uiState.value.retouchMaskBitmap
            val oldPeaking = _uiState.value.focusPeakingOverlayBitmap
            _uiState.update { current ->
                current.copy(
                    result = current.result?.copy(outputBitmap = updatedBitmap),
                    retouchBitmapVersion = current.retouchBitmapVersion + 1,
                    retouchUndoCount = undoStrokes.size,
                    retouchRedoCount = redoStrokes.size,
                    retouchMaskBitmap = newMask,
                    focusPeakingOverlayBitmap = newPeaking ?: current.focusPeakingOverlayBitmap,
                    isApplyingRetouch = false,
                    lastSavedPath = null
                )
            }
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

    fun saveFullResolutionResult() {
        val state = _uiState.value
        val result = state.result ?: return
        if (state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val outcome = mediaStoreExporter.saveToGallery(
                    bitmap = result.outputBitmap,
                    settings = state.outputSettings,
                    referenceFrameUri = result.referenceFrameUri
                )
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        lastSavedPath = outcome.displayPath,
                        snackbarMessage = "Saved with EXIF to ${outcome.displayPath}"
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

    fun startNewStack() {
        clearRetouchAndPeakingState()
        recycleResultBitmaps(_uiState.value.result)
        _uiState.update {
            it.copy(
                frames = emptyList(),
                selectedPreviewIndex = 0,
                result = null,
                retouchBitmapVersion = 0,
                lastSavedPath = null,
                processingState = ProcessingUiState.Idle
            )
        }
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

    private fun clearRetouchAndPeakingState() {
        peakingJob?.cancel()
        undoStrokes.clear()
        redoStrokes.clear()

        val state = _uiState.value
        val result = state.result

        if (activeRetouchSourceBitmap != null &&
            (result == null || activeRetouchSourceBitmap !in result.sourcePreviewBitmaps) &&
            activeRetouchSourceBitmap?.isRecycled == false
        ) {
            activeRetouchSourceBitmap?.recycle()
        }
        activeRetouchSourceBitmap = null
        activeRetouchSourceIndex = -1

        if (baseOutputBitmap != null && baseOutputBitmap !== result?.outputBitmap && baseOutputBitmap?.isRecycled == false) {
            baseOutputBitmap?.recycle()
        }
        baseOutputBitmap = null
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
        clearRetouchAndPeakingState()
        recycleResultBitmaps(_uiState.value.result)
    }
}
