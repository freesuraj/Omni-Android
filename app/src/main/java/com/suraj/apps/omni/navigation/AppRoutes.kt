package com.suraj.apps.omni.navigation

object AppRoutes {
    const val LIBRARY = "library"
    const val AUDIO = "audio"
    const val SETTINGS = "settings"
    const val DASHBOARD = "dashboard/{documentId}"
    const val DASHBOARD_ARG_DOCUMENT_ID = "documentId"
    const val QUIZ = "quiz/{documentId}"
    const val QUIZ_ARG_DOCUMENT_ID = "documentId"
    const val NOTES = "notes/{documentId}"
    const val NOTES_ARG_DOCUMENT_ID = "documentId"
    const val SUMMARY = "summary/{documentId}"
    const val SUMMARY_ARG_DOCUMENT_ID = "documentId"
    const val QA = "qa/{documentId}"
    const val QA_ARG_DOCUMENT_ID = "documentId"
    const val ANALYSIS = "analysis/{documentId}"
    const val ANALYSIS_ARG_DOCUMENT_ID = "documentId"
    const val PAYWALL = "paywall"

    fun dashboard(documentId: String): String = "dashboard/$documentId"
    fun quiz(documentId: String): String = "quiz/$documentId"
    fun notes(documentId: String): String = "notes/$documentId"
    fun summary(documentId: String): String = "summary/$documentId"
    fun qa(documentId: String): String = "qa/$documentId"
    fun analysis(documentId: String): String = "analysis/$documentId"
}
