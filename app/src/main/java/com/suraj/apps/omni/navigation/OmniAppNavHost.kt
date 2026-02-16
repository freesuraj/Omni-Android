package com.suraj.apps.omni.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.suraj.apps.omni.feature.analysis.AnalysisRoute
import com.suraj.apps.omni.feature.audio.AudioRoute
import com.suraj.apps.omni.feature.dashboard.DashboardRoute
import com.suraj.apps.omni.feature.library.LibraryRoute
import com.suraj.apps.omni.feature.notes.NotesRoute
import com.suraj.apps.omni.feature.paywall.PaywallRoute
import com.suraj.apps.omni.feature.qa.QaRoute
import com.suraj.apps.omni.feature.quiz.QuizRoute
import com.suraj.apps.omni.feature.settings.SettingsRoute
import com.suraj.apps.omni.feature.summary.SummaryRoute

@Composable
fun OmniAppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoutes.LIBRARY
    ) {
        composable(AppRoutes.LIBRARY) {
            LibraryRoute(
                onOpenAudio = { navController.navigate(AppRoutes.AUDIO) },
                onOpenSettings = { navController.navigate(AppRoutes.SETTINGS) },
                onOpenDashboard = { documentId ->
                    navController.navigate(AppRoutes.dashboard(documentId))
                },
                onOpenPaywall = { navController.navigate(AppRoutes.PAYWALL) }
            )
        }
        composable(AppRoutes.AUDIO) {
            AudioRoute(
                onOpenDashboard = { documentId ->
                    navController.navigate(AppRoutes.dashboard(documentId))
                },
                onOpenPaywall = { navController.navigate(AppRoutes.PAYWALL) }
            )
        }
        composable(AppRoutes.SETTINGS) { SettingsRoute() }
        composable(
            route = AppRoutes.DASHBOARD,
            arguments = listOf(navArgument(AppRoutes.DASHBOARD_ARG_DOCUMENT_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments
                ?.getString(AppRoutes.DASHBOARD_ARG_DOCUMENT_ID)
                .orEmpty()
            DashboardRoute(
                documentId = documentId,
                onOpenQuiz = { quizDocumentId ->
                    navController.navigate(AppRoutes.quiz(quizDocumentId))
                },
                onOpenNotes = { notesDocumentId ->
                    navController.navigate(AppRoutes.notes(notesDocumentId))
                },
                onOpenSummary = { navController.navigate(AppRoutes.SUMMARY) },
                onOpenQa = { navController.navigate(AppRoutes.QA) },
                onOpenAnalysis = { navController.navigate(AppRoutes.ANALYSIS) },
                onOpenPaywall = { navController.navigate(AppRoutes.PAYWALL) }
            )
        }
        composable(
            route = AppRoutes.QUIZ,
            arguments = listOf(navArgument(AppRoutes.QUIZ_ARG_DOCUMENT_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val quizDocumentId = backStackEntry.arguments
                ?.getString(AppRoutes.QUIZ_ARG_DOCUMENT_ID)
                .orEmpty()
            QuizRoute(
                documentId = quizDocumentId,
                onBack = { navController.popBackStack() },
                onOpenPaywall = { navController.navigate(AppRoutes.PAYWALL) }
            )
        }
        composable(
            route = AppRoutes.NOTES,
            arguments = listOf(navArgument(AppRoutes.NOTES_ARG_DOCUMENT_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val notesDocumentId = backStackEntry.arguments
                ?.getString(AppRoutes.NOTES_ARG_DOCUMENT_ID)
                .orEmpty()
            NotesRoute(
                documentId = notesDocumentId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoutes.SUMMARY) { SummaryRoute() }
        composable(AppRoutes.QA) { QaRoute() }
        composable(AppRoutes.ANALYSIS) { AnalysisRoute() }
        composable(AppRoutes.PAYWALL) { PaywallRoute() }
    }
}
