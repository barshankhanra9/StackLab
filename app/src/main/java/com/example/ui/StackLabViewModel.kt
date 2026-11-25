package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MediaStoreExporter
import com.example.data.SettingsRepository
import com.example.domain.model.AlignmentMode
import com.example.domain.model.BlendAlgorithm
import com.example.domain.model.FocusStackConfig
import com.example.domain.model.MaxResolutionPreset
import com.example.domain.model.OutputFormat
import com.example.domain.model.OutputSettings
import com.example.domain.model.StackFrame
import com.example.domain.model.StackMode
import com.example.domain.model.StackProgress
import com.example.domain.model.StackResult
import com.example.engine.FocusStackProcessor
import com.example.engine.FrameBitmapLoader
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
    val selectedPreviewIndex: Int = 0,
    val alignmentMode: AlignmentMode = AlignmentMode.AUTO_HOMOGRAPHY,
    val blendAlgorithm: BlendAlgorithm = BlendAlgorithm.PYRAMID_PMAX,
    val edgeSmoothingRadius: Int = 3,
    val processingState: ProcessingUiState = ProcessingUiState.Idle,
    val result: StackResult? = null,
    val isSaving: Boolean = false,
    val lastSavedPath: String? = null,
    val snackbarMessage: String? = null
) {
    val canStartStacking: Boolean
        get() = frames.size >= 2 && processingState is ProcessingUiState.Idle
}

class StackLabViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val bitmapLoader = FrameBitmapLoader(application)
    private val focusStackProcessor = FocusStackProcessor(bitmapLoader)
    private val mediaStoreExporter = MediaStoreExporter(application)

    private val _uiState = MutableStateFlow(StackLabUiState())
    val uiState: StateFlow<StackLabUiState> = _uiState.asStateFlow()

    private val _navEvents = MutableSharedFlow<StackLabNavEvent>(extraBufferCapacity = 4)
    val navEvents: SharedFlow<StackLabNavEvent> = _navEvents.asSharedFlow()

    private var stackingJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.outputSettings.collect { settings ->
                _uiState.update { it.copy(outputSettings = settings) }
            }
        }
    }

    fun onPhotosSelected(uris: List<Uri>, navigateToWorkspace: Boolean) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val newFrames = withContext(Dispatchers.IO) {
                bitmapLoader.inspectFrames(uris)
            }
            _uiState.update { state ->
                val merged = if (navigateToWorkspace) {
                    newFrames
                } else {
                    state.frames + newFrames
                }
                state.copy(
                    activeMode = StackMode.FocusStack,
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
        val current = _uiState.value
        if (current.frames.size < 2) {
            postMessage("Select at least 2 frames to build a focus stack.")
            return
        }
        if (current.processingState is ProcessingUiState.Running) return

        val config = FocusStackConfig(
            alignmentMode = current.alignmentMode,
            blendAlgorithm = current.blendAlgorithm,
            edgeSmoothingRadius = current.edgeSmoothingRadius,
            outputSettings = current.outputSettings
        )

        stackingJob?.cancel()
        stackingJob = viewModelScope.launch(Dispatchers.Default) {
            _uiState.update {
                it.copy(
                    processingState = ProcessingUiState.Running(
                        StackProgress(1, 4, 0.02f, "Initializing OpenCV darkroom engine...")
                    )
                )
            }
            try {
                val result = focusStackProcessor.process(
                    frames = current.frames,
                    config = config,
                    onProgress = { progress ->
                        _uiState.update { state ->
                            if (state.processingState is ProcessingUiState.Running) {
                                state.copy(processingState = ProcessingUiState.Running(progress))
                            } else state
                        }
                    }
                )
                recycleResultBitmaps(_uiState.value.result)
                _uiState.update {
                    it.copy(
                        processingState = ProcessingUiState.Idle,
                        result = result,
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

    fun saveFullResolutionResult() {
        val state = _uiState.value
        val result = state.result ?: return
        if (state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val outcome = mediaStoreExporter.saveToGallery(
                    bitmap = result.outputBitmap,
                    settings = state.outputSettings
                )
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        lastSavedPath = outcome.displayPath,
                        snackbarMessage = "Saved to ${outcome.displayPath}"
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
        recycleResultBitmaps(_uiState.value.result)
        _uiState.update {
            it.copy(
                frames = emptyList(),
                selectedPreviewIndex = 0,
                result = null,
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
        recycleResultBitmaps(_uiState.value.result)
    }
}
