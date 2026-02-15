package com.suraj.apps.omni.feature.dashboard

import androidx.compose.runtime.Composable
import com.suraj.apps.omni.core.designsystem.component.FeaturePlaceholderScreen
import com.suraj.apps.omni.core.designsystem.component.OmniPrimaryButton

@Composable
fun DashboardRoute(
    documentId: String,
    onOpenQuiz: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenSummary: () -> Unit,
    onOpenQa: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenPaywall: () -> Unit
) {
    FeaturePlaceholderScreen(
        title = "Dashboard",
        subtitle = "Onboarding started for document: $documentId",
        action = { OmniPrimaryButton(text = "Open Quiz", onClick = onOpenQuiz) }
    )
}
