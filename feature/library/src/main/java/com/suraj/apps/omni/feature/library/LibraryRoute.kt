package com.suraj.apps.omni.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suraj.apps.omni.core.data.local.entity.DocumentEntity
import com.suraj.apps.omni.core.designsystem.component.OmniFeatureChip
import com.suraj.apps.omni.core.designsystem.component.OmniProgressOverlay
import com.suraj.apps.omni.core.designsystem.theme.OmniSpacing
import com.suraj.apps.omni.core.model.DocumentFileType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryRoute(
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
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
                .padding(horizontal = OmniSpacing.large, vertical = OmniSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(OmniSpacing.large)
        ) {
            if (uiState.documents.isEmpty()) {
                EmptyLibraryContent(featureChips = featureChips)
            } else {
                LoadedLibraryContent(
                    documents = uiState.documents,
                    menuExpandedDocumentId = documentMenuDocumentId,
                    onOpenDashboard = onOpenDashboard,
                    onOpenDocumentMenu = { documentMenuDocumentId = it },
                    onDismissDocumentMenu = { documentMenuDocumentId = null },
                    onRename = { document ->
                        documentMenuDocumentId = null
                        viewModel.openRenameDialog(document)
                    },
                    onDelete = { document ->
                        documentMenuDocumentId = null
                        viewModel.openDeleteDialog(document)
                    }
                )
            }
            Spacer(modifier = Modifier.height(OmniSpacing.large))
        }
    }

    val overlayMessage = uiState.busyMessage
    if (!overlayMessage.isNullOrBlank()) {
        OmniProgressOverlay(message = overlayMessage)
    }
}

@Composable
private fun EmptyLibraryContent(featureChips: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OmniSpacing.large),
            verticalArrangement = Arrangement.spacedBy(OmniSpacing.small)
        ) {
            Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp)
            )
            Text(
                text = stringResource(R.string.library_header_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.library_header_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    StepItem(
        step = 1,
        title = stringResource(R.string.library_step_import_title),
        subtitle = stringResource(R.string.library_step_import_subtitle)
    )
    StepItem(
        step = 2,
        title = stringResource(R.string.library_step_generate_title),
        subtitle = stringResource(R.string.library_step_generate_subtitle)
    )
    StepItem(
        step = 3,
        title = stringResource(R.string.library_step_study_title),
        subtitle = stringResource(R.string.library_step_study_subtitle)
    )

    Text(text = stringResource(R.string.library_what_you_get), style = MaterialTheme.typography.titleLarge)

    featureChips.chunked(2).forEach { rowChips ->
        Row(horizontalArrangement = Arrangement.spacedBy(OmniSpacing.small), modifier = Modifier.fillMaxWidth()) {
            rowChips.forEach { chip ->
                OmniFeatureChip(text = chip, modifier = Modifier.weight(1f))
            }
            if (rowChips.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }

    Text(
        text = stringResource(R.string.library_empty_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun StepItem(step: Int, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(OmniSpacing.medium)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadedLibraryContent(
    documents: List<DocumentEntity>,
    menuExpandedDocumentId: String?,
    onOpenDashboard: (String) -> Unit,
    onOpenDocumentMenu: (String) -> Unit,
    onDismissDocumentMenu: () -> Unit,
    onRename: (DocumentEntity) -> Unit,
    onDelete: (DocumentEntity) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            documents.forEachIndexed { index, document ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 68.dp, end = OmniSpacing.large)
                    )
                }
                DocumentRow(
                    document = document,
                    menuExpanded = menuExpandedDocumentId == document.id,
                    onOpenDashboard = { onOpenDashboard(document.id) },
                    onOpenMenu = { onOpenDocumentMenu(document.id) },
                    onDismissMenu = onDismissDocumentMenu,
                    onRename = { onRename(document) },
                    onDelete = { onDelete(document) }
                )
            }
        }
    }
}

@Composable
private fun DocumentRow(
    document: DocumentEntity,
    menuExpanded: Boolean,
    onOpenDashboard: () -> Unit,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDashboard)
            .padding(horizontal = OmniSpacing.large, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OmniSpacing.medium)
    ) {
        Icon(
            imageVector = when (document.fileType) {
                DocumentFileType.AUDIO -> Icons.Rounded.GraphicEq
                else -> Icons.Rounded.Description
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = document.title,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = document.fileType.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            IconButton(onClick = onOpenMenu) {
                Icon(
                    imageVector = Icons.Rounded.StickyNote2,
                    contentDescription = stringResource(R.string.library_content_desc_document_actions),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = onDismissMenu
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_open_dashboard)) },
                    onClick = {
                        onDismissMenu()
                        onOpenDashboard()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_action_rename)) },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Edit, contentDescription = null) },
                    onClick = onRename
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_action_delete)) },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Delete, contentDescription = null) },
                    onClick = onDelete
                )
            }
        }
    }
}
