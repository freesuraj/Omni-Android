package com.suraj.apps.omni.feature.qa

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QaRoute(
    documentId: String,
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: QaViewModel = viewModel(
        key = "qa-$documentId",
        factory = QaViewModel.factory(application = application, documentId = documentId)
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbars = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbars.showSnackbar(message)
        viewModel.consumeError()
    }

    LaunchedEffect(uiState.shouldOpenPaywall) {
        if (!uiState.shouldOpenPaywall) return@LaunchedEffect
        viewModel.consumePaywallNavigation()
        onOpenPaywall()
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(uiState.documentTitle) }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbars) }
    ) { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.messages.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Ask anything about this document",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Questions and answers are saved per document.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.messages.size) { index ->
                    val message = uiState.messages[index]
                    val align = if (message.isUser) Alignment.End else Alignment.Start
                    val color = when {
                        message.isUser -> MaterialTheme.colorScheme.primaryContainer
                        message.isError -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val textColor = when {
                        message.isUser -> MaterialTheme.colorScheme.onPrimaryContainer
                        message.isError -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Column(horizontalAlignment = align, modifier = Modifier.fillMaxWidth()) {
                        Card(colors = CardDefaults.cardColors(containerColor = color)) {
                            Text(
                                text = message.content,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor
                            )
                        }
                    }
                }

                if (uiState.isSending) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = "Thinking...",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = uiState.draftQuestion,
                    onValueChange = viewModel::onDraftChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text("Ask a question") },
                    enabled = !uiState.isSending,
                    maxLines = 4
                )
                Button(
                    onClick = viewModel::sendQuestion,
                    enabled = !uiState.isSending
                ) {
                    Text("Send")
                }
            }
        }
    }
}
