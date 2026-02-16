package com.suraj.apps.omni.feature.summary

import android.app.Application
import android.content.Intent
import android.speech.tts.TextToSpeech
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suraj.apps.omni.core.data.summary.MIN_SUMMARY_WORDS
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SummaryRoute(
    documentId: String,
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: SummaryViewModel = viewModel(
        key = "summary-$documentId",
        factory = SummaryViewModel.factory(application = application, documentId = documentId)
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbars = remember { SnackbarHostState() }

    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                engine.language = Locale.getDefault()
            }
        }
        textToSpeech = engine

        onDispose {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            ttsReady = false
        }
    }

    fun speakSummary(summaryText: String) {
        if (summaryText.isBlank() || !ttsReady) return
        textToSpeech?.speak(
            summaryText,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "summary-${System.currentTimeMillis()}"
        )
    }

    fun shareSummary(summaryText: String) {
        if (summaryText.isBlank()) return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, summaryText)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share summary"))
    }

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = when (uiState.mode) {
                            SummaryScreenMode.CONFIG -> "Configure Summary"
                            SummaryScreenMode.GENERATING -> "Generating Summary"
                            SummaryScreenMode.VIEW -> uiState.documentTitle
                        }
                    )
                },
                actions = {
                    if (uiState.mode == SummaryScreenMode.VIEW) {
                        TextButton(onClick = viewModel::backToConfig) {
                            Text("New")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbars) }
    ) { paddingValues: PaddingValues ->
        when (uiState.mode) {
            SummaryScreenMode.CONFIG -> SummaryConfigScreen(
                uiState = uiState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onWordCountChanged = viewModel::onTargetWordCountChanged,
                onGenerate = viewModel::generateSummary
            )

            SummaryScreenMode.GENERATING -> SummaryGeneratingScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )

            SummaryScreenMode.VIEW -> SummaryViewScreen(
                uiState = uiState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onSelectSummary = viewModel::selectSummary,
                onGenerateAgain = viewModel::generateSummary,
                onSpeakSummary = ::speakSummary,
                onShareSummary = ::shareSummary
            )
        }
    }
}

@Composable
private fun SummaryConfigScreen(
    uiState: SummaryUiState,
    modifier: Modifier = Modifier,
    onWordCountChanged: (Int) -> Unit,
    onGenerate: () -> Unit
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Summary Length",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Target Word Count: ${uiState.targetWordCount}",
                    style = MaterialTheme.typography.titleMedium
                )
                Slider(
                    value = uiState.targetWordCount.toFloat(),
                    onValueChange = { onWordCountChanged(it.toInt()) },
                    valueRange = MIN_SUMMARY_WORDS.toFloat()..uiState.maxWordCount.toFloat(),
                    steps = (uiState.maxWordCount - MIN_SUMMARY_WORDS - 1).coerceAtLeast(0)
                )
            }
        }

        if (!uiState.isPremiumUnlocked) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "Free limit: up to ${uiState.maxWordCount} words per summary.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
            Text("Generate Summary")
        }
    }
}

@Composable
private fun SummaryGeneratingScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            text = "Generating summary...",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "Extracting key points from the document.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun SummaryViewScreen(
    uiState: SummaryUiState,
    modifier: Modifier = Modifier,
    onSelectSummary: (String) -> Unit,
    onGenerateAgain: () -> Unit,
    onSpeakSummary: (String) -> Unit,
    onShareSummary: (String) -> Unit
) {
    val selectedSummary = uiState.selectedSummary

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { selectedSummary?.let { onSpeakSummary(it.content) } },
                modifier = Modifier.weight(1f)
            ) {
                Text("Listen")
            }
            Button(
                onClick = { selectedSummary?.let { onShareSummary(it.content) } },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Text("Share", modifier = Modifier.padding(start = 6.dp))
            }
        }

        selectedSummary?.let { summary ->
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Summary • ${summary.wordCount} words",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = summary.content,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        Text(
            text = "Saved summaries",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        uiState.summaries.forEach { summary ->
            val label = remember(summary.createdAtEpochMs) {
                SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(summary.createdAtEpochMs))
            }
            FilterChip(
                selected = uiState.selectedSummaryId == summary.id,
                onClick = { onSelectSummary(summary.id) },
                label = {
                    Text("$label • ${summary.wordCount} words")
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        TextButton(onClick = onGenerateAgain, modifier = Modifier.fillMaxWidth()) {
            Text("Generate Another Summary")
        }
    }
}
