package com.example.engine

import com.example.domain.model.AstroConfig
import com.example.domain.model.HdrConfig
import com.example.domain.model.StackFrame
import com.example.domain.model.StackMode
import com.example.domain.model.StackProgress
import com.example.domain.model.StackResult

/**
 * Modular computational photography stack processor contract.
 * FocusStackProcessor implements focus stacking; HDR and Astro processors
 * plug into the same contract for future modes.
 */
interface StackProcessor<C> {
    val mode: StackMode

    suspend fun process(
        frames: List<StackFrame>,
        config: C,
        onProgress: (StackProgress) -> Unit
    ): StackResult
}

class HdrBlendProcessor : StackProcessor<HdrConfig> {
    override val mode: StackMode = StackMode.HdrExposureBlend

    override suspend fun process(
        frames: List<StackFrame>,
        config: HdrConfig,
        onProgress: (StackProgress) -> Unit
    ): StackResult {
        throw UnsupportedOperationException("HDR Exposure Blend is coming in a future StackLab update.")
    }
}

class AstroStackProcessor : StackProcessor<AstroConfig> {
    override val mode: StackMode = StackMode.AstroNoiseStack

    override suspend fun process(
        frames: List<StackFrame>,
        config: AstroConfig,
        onProgress: (StackProgress) -> Unit
    ): StackResult {
        throw UnsupportedOperationException("Astro / Noise Stack is coming in a future StackLab update.")
    }
}
