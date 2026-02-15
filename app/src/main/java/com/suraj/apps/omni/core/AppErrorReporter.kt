package com.suraj.apps.omni.core

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppErrorReporter @Inject constructor() {
    fun report(throwable: Throwable, context: String = "unknown") {
        Log.e("OmniError", "[$context] ${throwable.message}", throwable)
    }
}
