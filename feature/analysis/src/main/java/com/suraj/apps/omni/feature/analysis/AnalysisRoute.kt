package com.suraj.apps.omni.feature.analysis

import android.app.Application
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.noties.markwon.Markwon

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AnalysisRoute(
    documentId: String,
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: AnalysisViewModel = viewModel(
        key = "analysis-$documentId",
        factory = AnalysisViewModel.factory(application = application, documentId = documentId)
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbars = remember { SnackbarHostState() }

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

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.analysis_cd_back))
                    }
                },
                title = {
                    Text(
                        text = when (uiState.mode) {
                            AnalysisScreenMode.CONFIG -> stringResource(R.string.analysis_title_configure)
                            AnalysisScreenMode.GENERATING -> stringResource(R.string.analysis_title_generating)
                            AnalysisScreenMode.VIEW -> uiState.documentTitle
                        }
                    )
                },
                actions = {
                    if (uiState.mode == AnalysisScreenMode.VIEW) {
                        TextButton(onClick = viewModel::retryAnalysisFromScratch) {
                            Text(stringResource(R.string.analysis_action_retry))
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbars) }
    ) { paddingValues: PaddingValues ->
        when (uiState.mode) {
            AnalysisScreenMode.CONFIG -> AnalysisConfigScreen(
                uiState = uiState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onGenerateOrResume = viewModel::generateOrResumeAnalysis,
                onRetry = viewModel::retryAnalysisFromScratch
            )

            AnalysisScreenMode.GENERATING -> AnalysisGeneratingScreen(
                uiState = uiState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )

            AnalysisScreenMode.VIEW -> AnalysisViewScreen(
                uiState = uiState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onGenerateOrResume = viewModel::generateOrResumeAnalysis,
                onRetry = viewModel::retryAnalysisFromScratch
            )
        }
    }
}

@Composable
private fun AnalysisConfigScreen(
    uiState: AnalysisUiState,
    modifier: Modifier = Modifier,
    onGenerateOrResume: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = stringResource(R.string.analysis_scope_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (uiState.expectedAnalysisCount == 0) {
                        stringResource(R.string.analysis_segments_not_ready)
                    } else {
                        stringResource(R.string.analysis_segments_progress, uiState.completedCount, uiState.expectedAnalysisCount)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (uiState.fileType.name == "PDF") {
                        stringResource(R.string.analysis_scope_pdf_description)
                    } else {
                        stringResource(R.string.analysis_scope_non_pdf_description)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!uiState.isPremiumUnlocked) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = stringResource(R.string.analysis_premium_notice),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Button(
            onClick = {
                if (uiState.hasPersistedAnalyses && !uiState.hasPartialAnalyses) {
                    onRetry()
                } else {
                    onGenerateOrResume()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.expectedAnalysisCount > 0
        ) {
            Text(
                text = when {
                    uiState.hasPartialAnalyses -> stringResource(R.string.analysis_action_resume)
                    uiState.hasPersistedAnalyses -> stringResource(R.string.analysis_action_regenerate)
                    else -> stringResource(R.string.analysis_action_generate)
                }
            )
        }

        if (uiState.hasPersistedAnalyses) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.analysis_action_retry_from_scratch))
            }
        }
    }
}

@Composable
private fun AnalysisGeneratingScreen(
    uiState: AnalysisUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            progress = {
                if (uiState.progress.totalCount <= 0) 0f
                else uiState.progress.completedCount.toFloat() / uiState.progress.totalCount.toFloat()
            }
        )
        Text(
            text = stringResource(R.string.analysis_generating_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = uiState.progress.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun AnalysisViewScreen(
    uiState: AnalysisUiState,
    modifier: Modifier = Modifier,
    onGenerateOrResume: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    if (uiState.hasPartialAnalyses) onGenerateOrResume() else onRetry()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (uiState.hasPartialAnalyses) stringResource(R.string.analysis_view_action_resume) else stringResource(R.string.analysis_view_action_regenerate))
            }
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.analysis_action_retry))
            }
        }

        Text(
            text = stringResource(R.string.analysis_saved_count, uiState.completedCount, uiState.expectedAnalysisCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        uiState.analyses.forEach { item ->
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    MarkdownAnalysisText(
                        markdown = item.content,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownAnalysisText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val markwon = remember(context) { Markwon.create(context) }
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext).apply {
                setTextColor(bodyColor)
            }
        },
        update = { textView ->
            textView.setTextColor(bodyColor)
            markwon.setMarkdown(textView, markdown)
        }
    )
}
