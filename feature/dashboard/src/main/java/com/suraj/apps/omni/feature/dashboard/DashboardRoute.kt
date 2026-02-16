package com.suraj.apps.omni.feature.dashboard

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suraj.apps.omni.core.designsystem.component.OmniFeatureCard
import com.suraj.apps.omni.core.designsystem.component.OmniPrimaryButton
import com.suraj.apps.omni.core.designsystem.component.OmniSectionHeader
import com.suraj.apps.omni.core.designsystem.component.OmniStatusPill
import com.suraj.apps.omni.core.designsystem.theme.OmniSpacing

@Composable
private fun featureAction(
    enabled: Boolean,
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled
    ) {
        Text(text)
    }
}

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
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: DashboardViewModel = viewModel(
        key = "dashboard-$documentId",
        factory = DashboardViewModel.factory(
            application = application,
            documentId = documentId
        )
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeError()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(OmniSpacing.large),
            verticalArrangement = Arrangement.spacedBy(OmniSpacing.medium)
        ) {
            OmniSectionHeader(
                title = uiState.document?.title ?: "Dashboard",
                subtitle = "Document ID: $documentId"
            )
            OmniStatusPill(
                text = uiState.onboardingLabel,
                color = if (uiState.isOnboarding) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                }
            )
            LinearProgressIndicator(
                progress = { uiState.onboardingProgress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )

            if (uiState.isOnboarding) {
                OmniFeatureCard(
                    title = "Onboarding in progress",
                    subtitle = "You can stay here or continue in background while processing completes.",
                    trailing = {
                        if (uiState.onboardingLabel.contains("failed", ignoreCase = true)) {
                            Button(onClick = viewModel::retryOnboarding) {
                                Text("Retry")
                            }
                        }
                    }
                )
            }

            OmniFeatureCard(
                title = "Quiz",
                subtitle = "Generate practice questions from this document.",
                trailing = {
                    featureAction(
                        enabled = !uiState.isOnboarding,
                        text = "Open",
                        onClick = onOpenQuiz
                    )
                }
            )
            OmniFeatureCard(
                title = "Study Notes",
                subtitle = "Create and review card-based study notes.",
                trailing = {
                    featureAction(
                        enabled = !uiState.isOnboarding,
                        text = "Open",
                        onClick = onOpenNotes
                    )
                }
            )
            OmniFeatureCard(
                title = "Summary",
                subtitle = "Create concise summaries with share and playback support.",
                trailing = {
                    featureAction(
                        enabled = !uiState.isOnboarding,
                        text = "Open",
                        onClick = onOpenSummary
                    )
                }
            )
            OmniFeatureCard(
                title = "Q&A",
                subtitle = "Ask questions against imported content.",
                trailing = {
                    featureAction(
                        enabled = !uiState.isOnboarding,
                        text = "Open",
                        onClick = onOpenQa
                    )
                }
            )
            OmniFeatureCard(
                title = "Detailed Analysis",
                subtitle = "Run deeper analysis for pages and transcripts.",
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(OmniSpacing.small)) {
                        featureAction(
                            enabled = !uiState.isOnboarding,
                            text = "Open",
                            onClick = onOpenAnalysis
                        )
                    }
                }
            )
            OmniPrimaryButton(
                text = "Upgrade for premium workflows",
                onClick = onOpenPaywall
            )
        }
    }
}
