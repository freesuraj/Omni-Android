package com.suraj.apps.omni.navigation

object AppRoutes {
    const val LIBRARY = "library"
    const val AUDIO = "audio"
    const val SETTINGS = "settings"
    const val DASHBOARD = "dashboard/{documentId}"
    const val DASHBOARD_ARG_DOCUMENT_ID = "documentId"
    const val QUIZ = "quiz"
    const val NOTES = "notes"
    const val SUMMARY = "summary"
    const val QA = "qa"
    const val ANALYSIS = "analysis"
    const val PAYWALL = "paywall"

    fun dashboard(documentId: String): String = "dashboard/$documentId"
}
