package com.suraj.apps.omni.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suraj.apps.omni.core.data.local.entity.DocumentEntity
import com.suraj.apps.omni.core.designsystem.component.OmniFeatureCard
import com.suraj.apps.omni.core.designsystem.component.OmniFeatureChip
import com.suraj.apps.omni.core.designsystem.component.OmniPrimaryButton
import com.suraj.apps.omni.core.designsystem.component.OmniProgressOverlay
import com.suraj.apps.omni.core.designsystem.component.OmniSectionHeader
import com.suraj.apps.omni.core.designsystem.component.OmniStatusPill
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
    var documentMenuDocumentId by remember { mutableStateOf<String?>(null) }

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
                    Text(stringResource(R.string.library_action_import))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissWebDialog) {
                    Text(stringResource(R.string.library_action_cancel))
                }
            },
            title = { Text(stringResource(R.string.library_import_web_article_title)) },
            text = {
                OutlinedTextField(
                    value = uiState.webUrlInput,
                    onValueChange = viewModel::updateWebUrlInput,
                    label = { Text(stringResource(R.string.library_article_url_label)) },
                    singleLine = true
                )
            }
        )
    }

    if (uiState.showRenameDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRenameDialog,
            confirmButton = {
                TextButton(onClick = viewModel::confirmRename) {
                    Text(stringResource(R.string.library_action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRenameDialog) {
                    Text(stringResource(R.string.library_action_cancel))
                }
            },
            title = { Text(stringResource(R.string.library_rename_document_title)) },
            text = {
                OutlinedTextField(
                    value = uiState.renameInput,
                    onValueChange = viewModel::updateRenameInput,
                    label = { Text(stringResource(R.string.library_document_title_label)) },
                    singleLine = true
                )
            }
        )
    }

    if (uiState.showDeleteDialog) {
        val target = uiState.documents.firstOrNull { it.id == uiState.deleteDocumentId }
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteDialog,
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(R.string.library_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteDialog) {
                    Text(stringResource(R.string.library_action_cancel))
                }
            },
            title = { Text(stringResource(R.string.library_delete_document_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.library_delete_document_message,
                        target?.title ?: stringResource(R.string.library_document_fallback_title)
                    )
                )
            }
        )
    }

    val featureChips = listOf(
        stringResource(R.string.library_chip_quiz),
        stringResource(R.string.library_chip_flashcards),
        stringResource(R.string.library_chip_summary),
        stringResource(R.string.library_chip_qa),
        stringResource(R.string.library_chip_analysis),
        stringResource(R.string.library_chip_audio)
    )
    val steps = listOf(
        StepContent(
            1,
            stringResource(R.string.library_step_import_title),
            stringResource(R.string.library_step_import_subtitle)
        ),
        StepContent(
            2,
            stringResource(R.string.library_step_generate_title),
            stringResource(R.string.library_step_generate_subtitle)
        ),
        StepContent(
            3,
            stringResource(R.string.library_step_study_title),
            stringResource(R.string.library_step_study_subtitle)
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                actions = {
                    IconButton(onClick = { importMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.library_content_desc_import)
                        )
                    }
                    DropdownMenu(
                        expanded = importMenuExpanded,
                        onDismissRequest = { importMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_import_document_option)) },
                            onClick = {
                                importMenuExpanded = false
                                documentPicker.launch(arrayOf("application/pdf", "text/plain", "text/*"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_import_web_article_option)) },
                            onClick = {
                                importMenuExpanded = false
                                viewModel.openWebDialog()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_import_audio_option)) },
                            onClick = {
                                importMenuExpanded = false
                                audioPicker.launch(arrayOf("audio/*"))
                            }
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.library_content_desc_settings)
                        )
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
                title = stringResource(R.string.library_header_title),
                subtitle = stringResource(R.string.library_header_subtitle)
            )

            if (uiState.documents.isEmpty()) {
                steps.forEach { step ->
                    OmniFeatureCard(
                        title = step.title,
                        subtitle = step.subtitle,
                        trailing = { OmniStepBadge(step = step.step) }
                    )
                }

                Text(text = stringResource(R.string.library_what_you_get), style = MaterialTheme.typography.titleMedium)
                featureChips.chunked(3).forEach { rowChips ->
                    Row(horizontalArrangement = Arrangement.spacedBy(OmniSpacing.small)) {
                        rowChips.forEach { chip ->
                            OmniFeatureChip(text = chip)
                        }
                    }
                }
            } else {
                OmniFeatureCard(
                    title = stringResource(R.string.library_plan_guardrails_title),
                    subtitle = stringResource(R.string.library_plan_guardrails_subtitle)
                )
                Text(text = stringResource(R.string.library_your_imports), style = MaterialTheme.typography.titleMedium)

                uiState.documents.forEach { document ->
                    DocumentItemCard(
                        document = document,
                        menuExpanded = documentMenuDocumentId == document.id,
                        onOpenDashboard = { onOpenDashboard(document.id) },
                        onOpenMenu = { documentMenuDocumentId = document.id },
                        onDismissMenu = { documentMenuDocumentId = null },
                        onRename = {
                            documentMenuDocumentId = null
                            viewModel.openRenameDialog(document)
                        },
                        onDelete = {
                            documentMenuDocumentId = null
                            viewModel.openDeleteDialog(document)
                        }
                    )
                }
            }

            OmniPrimaryButton(
                text = stringResource(R.string.library_record_live_audio),
                onClick = onOpenAudio
            )
        }
    }

    val overlayMessage = uiState.busyMessage
    if (!overlayMessage.isNullOrBlank()) {
        OmniProgressOverlay(message = overlayMessage)
    }
}

@Composable
private fun DocumentItemCard(
    document: DocumentEntity,
    menuExpanded: Boolean,
    onOpenDashboard: () -> Unit,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OmniSpacing.small)
    ) {
        OmniFeatureCard(
            title = document.title,
            subtitle = document.extractedTextPreview
                ?: stringResource(R.string.library_document_preview_fallback),
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OmniSpacing.small)
                ) {
                    OmniStatusPill(
                        text = if (document.isOnboarding) {
                            stringResource(R.string.library_status_onboarding)
                        } else {
                            stringResource(R.string.library_status_ready)
                        },
                        color = if (document.isOnboarding) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        }
                    )
                    Box {
                        IconButton(onClick = onOpenMenu) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.library_content_desc_document_actions)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = onDismissMenu
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_action_rename)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = null
                                    )
                                },
                                onClick = onRename
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_action_delete)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = null
                                    )
                                },
                                onClick = onDelete
                            )
                        }
                    }
                }
            }
        )
        OmniPrimaryButton(
            text = stringResource(R.string.library_open_dashboard),
            onClick = onOpenDashboard
        )
    }
}
