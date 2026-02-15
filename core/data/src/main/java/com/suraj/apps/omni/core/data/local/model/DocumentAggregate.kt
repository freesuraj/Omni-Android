package com.suraj.apps.omni.core.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.suraj.apps.omni.core.data.local.entity.DocumentEntity
import com.suraj.apps.omni.core.data.local.entity.DocumentSummaryEntity
import com.suraj.apps.omni.core.data.local.entity.PageAnalysisEntity
import com.suraj.apps.omni.core.data.local.entity.QaMessageEntity
import com.suraj.apps.omni.core.data.local.entity.QuizEntity
import com.suraj.apps.omni.core.data.local.entity.StudyNoteEntity

/** Full document aggregate mirror for SwiftData cascade model. */
data class DocumentAggregate(
    @Embedded val document: DocumentEntity,
    @Relation(
        entity = QuizEntity::class,
        parentColumn = "id",
        entityColumn = "documentId"
    )
    val quizzes: List<QuizWithQuestions>,
    @Relation(parentColumn = "id", entityColumn = "documentId")
    val studyNotes: List<StudyNoteEntity>,
    @Relation(parentColumn = "id", entityColumn = "documentId")
    val summaries: List<DocumentSummaryEntity>,
    @Relation(parentColumn = "id", entityColumn = "documentId")
    val qaMessages: List<QaMessageEntity>,
    @Relation(parentColumn = "id", entityColumn = "documentId")
    val pageAnalyses: List<PageAnalysisEntity>
)
