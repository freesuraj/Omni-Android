package com.suraj.apps.omni.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.suraj.apps.omni.core.data.local.dao.DocumentDao
import com.suraj.apps.omni.core.data.local.dao.DocumentSummaryDao
import com.suraj.apps.omni.core.data.local.dao.PageAnalysisDao
import com.suraj.apps.omni.core.data.local.dao.QaMessageDao
import com.suraj.apps.omni.core.data.local.dao.QuizDao
import com.suraj.apps.omni.core.data.local.dao.StudyNoteDao
import com.suraj.apps.omni.core.data.local.entity.DocumentEntity
import com.suraj.apps.omni.core.data.local.entity.DocumentSummaryEntity
import com.suraj.apps.omni.core.data.local.entity.PageAnalysisEntity
import com.suraj.apps.omni.core.data.local.entity.QaMessageEntity
import com.suraj.apps.omni.core.data.local.entity.QuestionEntity
import com.suraj.apps.omni.core.data.local.entity.QuizEntity
import com.suraj.apps.omni.core.data.local.entity.StudyNoteEntity

@Database(
    entities = [
        DocumentEntity::class,
        QuizEntity::class,
        QuestionEntity::class,
        StudyNoteEntity::class,
        DocumentSummaryEntity::class,
        QaMessageEntity::class,
        PageAnalysisEntity::class
    ],
    version = OmniMigrations.SCHEMA_VERSION,
    exportSchema = true
)
@TypeConverters(OmniTypeConverters::class)
abstract class OmniDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun quizDao(): QuizDao
    abstract fun studyNoteDao(): StudyNoteDao
    abstract fun documentSummaryDao(): DocumentSummaryDao
    abstract fun qaMessageDao(): QaMessageDao
    abstract fun pageAnalysisDao(): PageAnalysisDao
}
