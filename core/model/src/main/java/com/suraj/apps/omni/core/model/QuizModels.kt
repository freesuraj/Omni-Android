package com.suraj.apps.omni.core.model

enum class QuizDifficulty {
    EASY,
    MEDIUM,
    HARD
}

enum class DocumentFileType {
    PDF,
    TXT,
    WEB,
    AUDIO
}

enum class QuestionStatus {
    NEW,
    CORRECT,
    INCORRECT
}

data class QuizSettings(
    val questionCount: Int = 10,
    val difficulty: QuizDifficulty = QuizDifficulty.MEDIUM,
    val showSourceSnippet: Boolean = true,
    val soundsEnabled: Boolean = true
)
