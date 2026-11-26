package com.example.engine

import com.example.domain.model.StackFrame
import com.example.domain.model.StackMode
import com.example.domain.model.StackProgress
import com.example.domain.model.StackResult

/**
 * Modular computational photography stack processor contract.
 * FocusStackProcessor, HdrStackProcessor, and AstroStackProcessor implement active modes.
 */
interface StackProcessor<C> {
    val mode: StackMode

    suspend fun process(
        frames: List<StackFrame>,
        config: C,
        onProgress: (StackProgress) -> Unit
    ): StackResult
}
