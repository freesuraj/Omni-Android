package com.suraj.apps.omni.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.suraj.apps.omni.R
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
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val destinations = listOf(
        TopLevelDestination(AppRoutes.LIBRARY, stringResource(R.string.tab_library), Icons.Rounded.LibraryBooks),
        TopLevelDestination(AppRoutes.AUDIO, stringResource(R.string.tab_audio), Icons.Rounded.GraphicEq),
        TopLevelDestination(AppRoutes.SETTINGS, stringResource(R.string.tab_settings), Icons.Rounded.Settings)
    )
    val showBottomTabs = currentRoute in setOf(AppRoutes.LIBRARY, AppRoutes.AUDIO, AppRoutes.SETTINGS)

    Scaffold(
        bottomBar = {
            if (showBottomTabs) {
                OmniBottomTabBar(
                    destinations = destinations,
                    currentRoute = currentRoute.orEmpty(),
                    onTabSelected = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoutes.LIBRARY,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppRoutes.LIBRARY) {
                LibraryRoute(
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
                    onBack = { navController.popBackStack() },
                    onOpenQuiz = { quizDocumentId ->
                        navController.navigate(AppRoutes.quiz(quizDocumentId))
                    },
                    onOpenNotes = { notesDocumentId ->
                        navController.navigate(AppRoutes.notes(notesDocumentId))
                    },
                    onOpenSummary = { summaryDocumentId ->
                        navController.navigate(AppRoutes.summary(summaryDocumentId))
                    },
                    onOpenQa = { qaDocumentId ->
                        navController.navigate(AppRoutes.qa(qaDocumentId))
                    },
                    onOpenAnalysis = { analysisDocumentId ->
                        navController.navigate(AppRoutes.analysis(analysisDocumentId))
                    },
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
            composable(
                route = AppRoutes.SUMMARY,
                arguments = listOf(navArgument(AppRoutes.SUMMARY_ARG_DOCUMENT_ID) { type = NavType.StringType })
            ) { backStackEntry ->
                val summaryDocumentId = backStackEntry.arguments
                    ?.getString(AppRoutes.SUMMARY_ARG_DOCUMENT_ID)
                    .orEmpty()
                SummaryRoute(
                    documentId = summaryDocumentId,
                    onBack = { navController.popBackStack() },
                    onOpenPaywall = { navController.navigate(AppRoutes.PAYWALL) }
                )
            }
            composable(
                route = AppRoutes.QA,
                arguments = listOf(navArgument(AppRoutes.QA_ARG_DOCUMENT_ID) { type = NavType.StringType })
            ) { backStackEntry ->
                val qaDocumentId = backStackEntry.arguments
                    ?.getString(AppRoutes.QA_ARG_DOCUMENT_ID)
                    .orEmpty()
                QaRoute(
                    documentId = qaDocumentId,
                    onBack = { navController.popBackStack() },
                    onOpenPaywall = { navController.navigate(AppRoutes.PAYWALL) }
                )
            }
            composable(
                route = AppRoutes.ANALYSIS,
                arguments = listOf(navArgument(AppRoutes.ANALYSIS_ARG_DOCUMENT_ID) { type = NavType.StringType })
            ) { backStackEntry ->
                val analysisDocumentId = backStackEntry.arguments
                    ?.getString(AppRoutes.ANALYSIS_ARG_DOCUMENT_ID)
                    .orEmpty()
                AnalysisRoute(
                    documentId = analysisDocumentId,
                    onBack = { navController.popBackStack() },
                    onOpenPaywall = { navController.navigate(AppRoutes.PAYWALL) }
                )
            }
            composable(AppRoutes.PAYWALL) {
                PaywallRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
private fun OmniBottomTabBar(
    destinations: List<TopLevelDestination>,
    currentRoute: String,
    onTabSelected: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .height(58.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(30.dp),
            tonalElevation = 2.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                destinations.forEach { destination ->
                    val selected = currentRoute == destination.route
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                RoundedCornerShape(22.dp)
                            )
                            .clickable { onTabSelected(destination.route) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
