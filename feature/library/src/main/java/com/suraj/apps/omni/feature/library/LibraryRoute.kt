package com.suraj.apps.omni.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suraj.apps.omni.core.designsystem.component.OmniFeatureCard
import com.suraj.apps.omni.core.designsystem.component.OmniFeatureChip
import com.suraj.apps.omni.core.designsystem.component.OmniPrimaryButton
import com.suraj.apps.omni.core.designsystem.component.OmniProgressOverlay
import com.suraj.apps.omni.core.designsystem.component.OmniSectionHeader
import com.suraj.apps.omni.core.designsystem.component.OmniStepBadge
import com.suraj.apps.omni.core.designsystem.theme.OmniSpacing

private data class StepContent(val step: Int, val title: String, val subtitle: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryRoute(
    onOpenAudio: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDashboard: (String) -> Unit,
    onOpenPaywall: () -> Unit
) {
    val viewModel: LibraryViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val snackbars = remember { SnackbarHostState() }
    var importMenuExpanded by remember { mutableStateOf(false) }

    val documentPicker = rememberLauncherForActivityResult(
        contract = OpenDocument(),
        onResult = viewModel::onImportDocument
    )
    val audioPicker = rememberLauncherForActivityResult(
        contract = OpenDocument(),
        onResult = viewModel::onImportAudio
    )

    LaunchedEffect(uiState.pendingDashboardDocumentId) {
        val documentId = uiState.pendingDashboardDocumentId ?: return@LaunchedEffect
        viewModel.consumeDashboardNavigation()
        onOpenDashboard(documentId)
    }
    LaunchedEffect(uiState.shouldOpenPaywall) {
        if (!uiState.shouldOpenPaywall) return@LaunchedEffect
        viewModel.consumePaywallNavigation()
        onOpenPaywall()
    }
    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbars.showSnackbar(message)
        viewModel.consumeError()
    }

    if (uiState.showWebDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissWebDialog,
            confirmButton = {
                TextButton(onClick = viewModel::onImportWebArticle) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissWebDialog) {
                    Text("Cancel")
                }
            },
            title = { Text("Import web article") },
            text = {
                OutlinedTextField(
                    value = uiState.webUrlInput,
                    onValueChange = viewModel::updateWebUrlInput,
                    label = { Text("Article URL") },
                    singleLine = true
                )
            }
        )
    }

    val featureChips = listOf("Quiz", "Flashcards", "Summary", "Q&A", "Analysis", "Audio")
    val steps = listOf(
        StepContent(1, "Import source", "Use + to add document, audio note, or web article."),
        StepContent(2, "Generate study set", "Omni builds quiz, notes, summary, and Q&A context."),
        StepContent(3, "Study and review", "Use dashboard outputs and keep learning in one place.")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = { importMenuExpanded = true }) {
                        Icon(imageVector = Icons.Rounded.Add, contentDescription = "Import")
                    }
                    DropdownMenu(
                        expanded = importMenuExpanded,
                        onDismissRequest = { importMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Document") },
                            onClick = {
                                importMenuExpanded = false
                                documentPicker.launch(arrayOf("application/pdf", "text/plain", "text/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Web article") },
                            onClick = {
                                importMenuExpanded = false
                                viewModel.openWebDialog()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Audio file") },
                            onClick = {
                                importMenuExpanded = false
                                audioPicker.launch(arrayOf("audio/*"))
                            }
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(imageVector = Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbars) }
    ) { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(OmniSpacing.large),
            verticalArrangement = Arrangement.spacedBy(OmniSpacing.large)
        ) {
            OmniSectionHeader(
                title = "Start with document, audio note or article",
                subtitle = "Import with +, then open the dashboard to generate study outputs."
            )

            if (uiState.documents.isEmpty()) {
                steps.forEach { step ->
                    OmniFeatureCard(
                        title = step.title,
                        subtitle = step.subtitle,
                        trailing = { OmniStepBadge(step = step.step) }
                    )
                }

                Text(text = "What you get", style = MaterialTheme.typography.titleMedium)
                featureChips.chunked(3).forEach { rowChips ->
                    Row(horizontalArrangement = Arrangement.spacedBy(OmniSpacing.small)) {
                        rowChips.forEach { chip ->
                            OmniFeatureChip(text = chip)
                        }
                    }
                }
            } else {
                Text(text = "Your imports", style = MaterialTheme.typography.titleMedium)
                uiState.documents.forEach { document ->
                    OmniFeatureCard(
                        title = document.title,
                        subtitle = document.extractedTextPreview ?: "Open dashboard for onboarding outputs."
                    )
                    OmniPrimaryButton(
                        text = "Open dashboard",
                        onClick = { onOpenDashboard(document.id) }
                    )
                }
            }

            OmniPrimaryButton(text = "Record live audio", onClick = onOpenAudio)
        }
    }

    if (uiState.isImporting) {
        OmniProgressOverlay(message = "Importing source...")
    }
}
