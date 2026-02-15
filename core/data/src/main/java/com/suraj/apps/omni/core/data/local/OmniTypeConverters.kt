package com.suraj.apps.omni.core.data.local

import androidx.room.TypeConverter
import com.suraj.apps.omni.core.model.DocumentFileType
import com.suraj.apps.omni.core.model.QuestionStatus
import com.suraj.apps.omni.core.model.QuizDifficulty
import com.suraj.apps.omni.core.model.QuizSettings
import org.json.JSONObject

class OmniTypeConverters {
    @TypeConverter
    fun toDocumentFileType(value: String): DocumentFileType = DocumentFileType.valueOf(value)

    @TypeConverter
    fun fromDocumentFileType(value: DocumentFileType): String = value.name

    @TypeConverter
    fun toQuestionStatus(value: String?): QuestionStatus? = value?.let(QuestionStatus::valueOf)

    @TypeConverter
    fun fromQuestionStatus(value: QuestionStatus?): String? = value?.name

    @TypeConverter
    fun toQuizSettings(value: String): QuizSettings {
        return runCatching {
            val json = JSONObject(value)
            QuizSettings(
                questionCount = json.optInt("questionCount", 10),
                difficulty = QuizDifficulty.valueOf(
                    json.optString("difficulty", QuizDifficulty.MEDIUM.name)
                ),
                showSourceSnippet = json.optBoolean("showSourceSnippet", true),
                soundsEnabled = json.optBoolean("soundsEnabled", true)
            )
        }.getOrDefault(QuizSettings())
    }

    @TypeConverter
    fun fromQuizSettings(value: QuizSettings): String {
        return JSONObject()
            .put("questionCount", value.questionCount)
            .put("difficulty", value.difficulty.name)
            .put("showSourceSnippet", value.showSourceSnippet)
            .put("soundsEnabled", value.soundsEnabled)
            .toString()
    }
}
