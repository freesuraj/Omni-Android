package com.suraj.apps.omni.core.data.transcription;

import org.vosk.Recognizer;

/**
 * Helper to bypass Kotlin interop issues with Vosk's native methods.
 * Kotlin sometimes fails to resolve native overloads like acceptWaveform.
 */
public class VoskHelper {
    public static boolean feedAudio(Recognizer recognizer, byte[] data, int len) {
        return recognizer.acceptWaveForm(data, len);
    }
}
