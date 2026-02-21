package com.suraj.apps.omni.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suraj.apps.omni.core.designsystem.theme.OmniSpacing

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsRoute(
    onOpenPaywall: () -> Unit = {}
) {
    val viewModel: SettingsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val snackbars = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(uiState.infoMessage) {
        val message = uiState.infoMessage ?: return@LaunchedEffect
        snackbars.showSnackbar(message)
        viewModel.consumeInfoMessage()
    }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbars.showSnackbar(message)
        viewModel.consumeErrorMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
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
            if (!uiState.isPremiumUnlocked) {
                Button(
                    onClick = onOpenPaywall,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_upgrade_to_pro),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.settings_section_llm_provider),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.settings_choose_ai),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = OmniSpacing.large, vertical = OmniSpacing.medium)
                    )
                    uiState.providerOptions.forEachIndexed { index, option ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        ProviderOptionRow(
                            option = option,
                            onSelect = { viewModel.selectProvider(option.providerId) }
                        )
                    }
                }
            }

            if (uiState.selectedProvider.requiresApiKey) {
                Text(
                    text = stringResource(R.string.settings_provider_configuration, uiState.selectedProvider.displayName),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(OmniSpacing.large),
                        verticalArrangement = Arrangement.spacedBy(OmniSpacing.medium)
                    ) {
                        OutlinedTextField(
                            value = uiState.apiKeyInput,
                            onValueChange = viewModel::updateApiKeyInput,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.settings_api_key_label)) }
                        )

                        Button(
                            onClick = viewModel::saveApiKey,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_validate_save_key))
                        }

                        if (uiState.hasSavedApiKeyForSelectedProvider) {
                            Text(
                                text = stringResource(R.string.settings_saved_key_notice),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.hasSavedApiKeyForSelectedProvider) {
                                TextButton(onClick = viewModel::clearApiKey) {
                                    Text(stringResource(R.string.settings_clear_key))
                                }
                            }

                            val keyHelpUrl = uiState.selectedProvider.keyHelpUrl
                            if (!keyHelpUrl.isNullOrBlank()) {
                                TextButton(onClick = { uriHandler.openUri(keyHelpUrl) }) {
                                    Text(stringResource(R.string.settings_get_api_key))
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.settings_section_about),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = OmniSpacing.large, vertical = OmniSpacing.medium),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(R.string.settings_app_version_label))
                        Text(
                            text = uiState.appVersion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TextButton(
                        onClick = { uriHandler.openUri("https://omnistudy.app/terms") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = OmniSpacing.medium)
                    ) {
                        Text(stringResource(R.string.settings_terms_of_use))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderOptionRow(
    option: ProviderOptionUiState,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = OmniSpacing.large, vertical = OmniSpacing.medium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = option.title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (option.providerId.requiresApiKey && option.hasSavedApiKey) {
                Text(
                    text = stringResource(R.string.settings_api_key_configured),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (option.isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.settings_selected_content_description),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
