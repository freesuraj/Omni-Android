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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suraj.apps.omni.core.designsystem.theme.OmniSpacing

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsRoute() {
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
            TopAppBar(title = { Text("Settings") })
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
            Text(
                text = "LLM Provider",
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
                        text = "Choose the AI to use",
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
                    text = "${uiState.selectedProvider.displayName} Configuration",
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
                            label = { Text("API Key") }
                        )

                        Button(
                            onClick = viewModel::saveApiKey,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Validate & Save Key")
                        }

                        if (uiState.hasSavedApiKeyForSelectedProvider) {
                            Text(
                                text = "A key is already saved on this device.",
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
                                    Text("Clear key")
                                }
                            }

                            val keyHelpUrl = uiState.selectedProvider.keyHelpUrl
                            if (!keyHelpUrl.isNullOrBlank()) {
                                TextButton(onClick = { uriHandler.openUri(keyHelpUrl) }) {
                                    Text("Get API Key")
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = "About",
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
                        Text(text = "Omni app version")
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
                        Text("Terms of Use")
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
                    text = "API key configured",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (option.isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
