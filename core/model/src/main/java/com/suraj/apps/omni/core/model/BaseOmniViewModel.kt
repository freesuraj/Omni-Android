package com.suraj.apps.omni.core.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Shared ViewModel contract for feature modules.
 * Provides a common supervised scope + error event channel.
 */
open class BaseOmniViewModel : ViewModel() {
    private val _errors = MutableSharedFlow<Throwable>(extraBufferCapacity = 64)
    val errors: SharedFlow<Throwable> = _errors

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val errorHandler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        _errors.tryEmit(throwable)
    }

    protected fun publishError(throwable: Throwable) {
        _errors.tryEmit(throwable)
    }

    protected val uiScope: CoroutineScope
        get() = viewModelScope
}
