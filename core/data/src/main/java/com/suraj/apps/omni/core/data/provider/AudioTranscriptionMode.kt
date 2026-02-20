package com.suraj.apps.omni.core.data.provider

enum class AudioTranscriptionMode(
    val storageKey: String,
    val displayName: String
) {
    ON_DEVICE(
        storageKey = "on_device",
        displayName = "On-device (Vosk)"
    ),
    GEMINI(
        storageKey = "gemini",
        displayName = "Gemini"
    );

    companion object {
        fun fromStorageKey(rawValue: String?): AudioTranscriptionMode {
            if (rawValue.isNullOrBlank()) return ON_DEVICE
            return entries.firstOrNull { it.storageKey == rawValue } ?: ON_DEVICE
        }
    }
}
