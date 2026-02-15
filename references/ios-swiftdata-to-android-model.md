# Omni Data Model Mapping (iOS SwiftData -> Android)

This is the canonical reference for translating the current iOS SwiftData persistence model into Android persistence (Room + Kotlin).

Use this document when implementing Android entities, DAOs, relations, and migrations.

---

## 1) Recommended Android persistence stack

- **Room** for relational local persistence.
- **TypeConverters** for UUID, Date/Instant, and serialized settings.
- **Foreign keys with `CASCADE`** to match iOS delete behavior.
- Keep large extracted text on disk (`filesDir/<documentId>.txt`) as iOS does, not in DB.

---

## 2) Entity mapping

## `DocumentItem` -> `documents`

SwiftData fields:
- `id: UUID` (unique)
- `title: String`
- `createdAt: Date`
- `fileBookmarkData: Data?`
- `fileType: String`
- `sourceURL: String?`
- `extractedTextHash: String?`
- `extractedTextPreview: String?`
- `lastOpenedAt: Date?`
- `isOnboarding: Bool`
- `onboardingStatus: String?`
- `timeSpent: TimeInterval` (seconds)

Recommended Room entity:
- `id: String` (UUID string, PK)
- `title: String`
- `createdAtEpochMs: Long`
- `fileBookmarkData: ByteArray?` (or URI permission token equivalent)
- `fileType: String` (`pdf`, `txt`, `web`, `audio`)
- `sourceUrl: String?`
- `extractedTextHash: String?`
- `extractedTextPreview: String?`
- `lastOpenedAtEpochMs: Long?`
- `isOnboarding: Boolean`
- `onboardingStatus: String?`
- `timeSpentSeconds: Double`

Indexes:
- `createdAtEpochMs`
- `fileType`

---

## `Quiz` -> `quizzes`

SwiftData fields:
- `id: UUID` (unique)
- `createdAt: Date`
- `settings: QuizSettings` (Codable struct)
- `currentIndex: Int`
- `correctCount: Int`
- `completedAt: Date?`
- `isReview: Bool`
- `document: DocumentItem?`

Recommended Room entity:
- `id: String` (PK)
- `documentId: String?` (FK -> `documents.id`, `ON DELETE CASCADE`)
- `createdAtEpochMs: Long`
- `settingsJson: String` (serialized `QuizSettings`)
- `currentIndex: Int`
- `correctCount: Int`
- `completedAtEpochMs: Long?`
- `isReview: Boolean`

Indexes:
- `documentId`
- `createdAtEpochMs`

---

## `Question` -> `questions`

SwiftData fields:
- `id: UUID` (unique)
- `prompt: String`
- `optionA: String`
- `optionB: String`
- `correctAnswer: String` (`A`/`B`)
- `sourceSnippet: String?`
- `userAnswer: String?`
- `isCorrect: Bool?`
- `createdFromChunkIndex: Int?`
- `previousStatus: Status?` (`new`, `correct`, `incorrect`)

Relation:
- iOS stores `questions` inside `Quiz`.

Recommended Room entity:
- `id: String` (PK)
- `quizId: String` (FK -> `quizzes.id`, `ON DELETE CASCADE`)
- `prompt: String`
- `optionA: String`
- `optionB: String`
- `correctAnswer: String`
- `sourceSnippet: String?`
- `userAnswer: String?`
- `isCorrect: Boolean?`
- `createdFromChunkIndex: Int?`
- `previousStatus: String?` (`new`, `correct`, `incorrect`)

Indexes:
- `quizId`

---

## `StudyNote` -> `study_notes`

SwiftData fields:
- `id: UUID` (unique)
- `title: String` (legacy)
- `content: String` (legacy)
- `frontContent: String`
- `backContent: String`
- `isBookmarked: Bool`
- `colorHex: String`
- `createdAt: Date`
- `document: DocumentItem?`

Recommended Room entity:
- `id: String` (PK)
- `documentId: String?` (FK -> `documents.id`, `ON DELETE CASCADE`)
- `title: String` (keep for compatibility/migration)
- `content: String` (keep for compatibility/migration)
- `frontContent: String`
- `backContent: String`
- `isBookmarked: Boolean`
- `colorHex: String`
- `createdAtEpochMs: Long`

Indexes:
- `documentId`
- `createdAtEpochMs`
- `isBookmarked`

---

## `DocumentSummary` -> `document_summaries`

SwiftData fields:
- `id: UUID`
- `content: String`
- `wordCount: Int`
- `createdAt: Date`
- `document: DocumentItem?`

Recommended Room entity:
- `id: String` (PK)
- `documentId: String?` (FK -> `documents.id`, `ON DELETE CASCADE`)
- `content: String`
- `wordCount: Int`
- `createdAtEpochMs: Long`

Indexes:
- `documentId`
- `createdAtEpochMs`

---

## `QAMessage` -> `qa_messages`

SwiftData fields:
- `id: UUID` (unique)
- `content: String`
- `isUser: Bool`
- `isError: Bool`
- `createdAt: Date`
- `document: DocumentItem?`

Recommended Room entity:
- `id: String` (PK)
- `documentId: String?` (FK -> `documents.id`, `ON DELETE CASCADE`)
- `content: String`
- `isUser: Boolean`
- `isError: Boolean`
- `createdAtEpochMs: Long`

Indexes:
- `documentId`
- `createdAtEpochMs`

---

## `PageAnalysis` -> `page_analyses`

SwiftData fields:
- `id: UUID` (unique)
- `pageNumber: Int`
- `content: String`
- `thumbnailData: Data?` (external storage)
- `createdAt: Date`
- `document: DocumentItem?`

Recommended Room entity:
- `id: String` (PK)
- `documentId: String?` (FK -> `documents.id`, `ON DELETE CASCADE`)
- `pageNumber: Int`
- `content: String`
- `thumbnailData: ByteArray?`
- `createdAtEpochMs: Long`

Indexes:
- `documentId`
- `(documentId, pageNumber)` unique

---

## 3) Relationship summary (must preserve)

- `documents` 1:N `quizzes`
- `quizzes` 1:N `questions`
- `documents` 1:N `study_notes`
- `documents` 1:N `document_summaries`
- `documents` 1:N `qa_messages`
- `documents` 1:N `page_analyses`

Delete behavior:
- Deleting a document must cascade to all its child records.
- Deleting a quiz must cascade to its questions.

---

## 4) Non-DB persisted artifacts

To match iOS behavior, keep these on filesystem:
- Extracted full text/transcript: `<documentId>.txt`
- Imported audio files copied into app storage.

DB should store metadata/preview only; heavy text remains file-backed.

---

## 5) Suggested Room/Kotlin enums and value models

- `QuizDifficulty`: `EASY`, `MEDIUM`, `HARD`
- `QuestionStatus`: `NEW`, `CORRECT`, `INCORRECT`
- `QuizSettings` as a serializable data class stored via JSON converter:
  - `questionCount: Int`
  - `difficulty: QuizDifficulty`
  - `showSourceSnippet: Boolean`
  - `soundsEnabled: Boolean`

---

## 6) Implementation guardrail for agents

When Android data layer changes:
1. Check this reference first.
2. Keep field parity unless explicitly changing product behavior.
3. Document any intentional divergence in PR notes and migration notes.

