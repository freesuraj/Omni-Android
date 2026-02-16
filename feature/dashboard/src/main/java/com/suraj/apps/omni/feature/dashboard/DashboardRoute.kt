package com.suraj.apps.omni.feature.dashboard

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suraj.apps.omni.core.data.local.entity.DocumentEntity
import com.suraj.apps.omni.core.designsystem.component.OmniFeatureCard
import com.suraj.apps.omni.core.designsystem.component.OmniPrimaryButton
import com.suraj.apps.omni.core.designsystem.component.OmniSectionHeader
import com.suraj.apps.omni.core.designsystem.component.OmniStatusPill
import com.suraj.apps.omni.core.designsystem.theme.OmniSpacing
import com.suraj.apps.omni.core.model.DocumentFileType
import java.io.File

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
    onOpenQuiz: (String) -> Unit,
    onOpenNotes: (String) -> Unit,
    onOpenSummary: (String) -> Unit,
    onOpenQa: (String) -> Unit,
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
                    subtitle = "You can continue in background while processing completes.",
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
                title = "Source details",
                subtitle = uiState.sourceStats.ifBlank { "Preparing source details..." },
                trailing = {
                    featureAction(
                        enabled = uiState.document != null,
                        text = "Open original",
                        onClick = {
                            openOriginalSource(
                                context = context,
                                document = uiState.document,
                                onError = viewModel::reportError
                            )
                        }
                    )
                }
            )

            if (!uiState.audioSourcePath.isNullOrBlank()) {
                AudioPreviewCard(
                    audioPath = uiState.audioSourcePath,
                    transcript = uiState.audioTranscript,
                    onError = viewModel::reportError
                )
            }

            OmniFeatureCard(
                title = "Quiz",
                subtitle = "Generate practice questions from this document.",
                trailing = {
                    featureAction(
                        enabled = !uiState.isOnboarding,
                        text = "Open",
                        onClick = { onOpenQuiz(documentId) }
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
                        onClick = { onOpenNotes(documentId) }
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
                        onClick = { onOpenSummary(documentId) }
                    )
                }
            )
            OmniFeatureCard(
                title = "Q&A",
                subtitle = "Ask questions against imported content (premium).",
                trailing = {
                    featureAction(
                        enabled = !uiState.isOnboarding,
                        text = "Open",
                        onClick = {
                            if (viewModel.requestPremiumFeatureAccess("Q&A")) {
                                onOpenQa(documentId)
                            } else {
                                onOpenPaywall()
                            }
                        }
                    )
                }
            )
            OmniFeatureCard(
                title = "Detailed Analysis",
                subtitle = "Run deeper analysis for pages and transcripts (premium).",
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(OmniSpacing.small)) {
                        featureAction(
                            enabled = !uiState.isOnboarding,
                            text = "Open",
                            onClick = {
                                if (viewModel.requestPremiumFeatureAccess("Detailed analysis")) {
                                    onOpenAnalysis()
                                } else {
                                    onOpenPaywall()
                                }
                            }
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

@Composable
private fun AudioPreviewCard(
    audioPath: String?,
    transcript: String,
    onError: (String) -> Unit
) {
    if (audioPath.isNullOrBlank()) return

    var mediaPlayer by remember(audioPath) { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember(audioPath) { mutableStateOf(false) }

    DisposableEffect(audioPath) {
        onDispose {
            runCatching { mediaPlayer?.release() }
            mediaPlayer = null
        }
    }

    OmniFeatureCard(
        title = "Audio player + transcript",
        subtitle = if (isPlaying) {
            "Playing imported audio."
        } else {
            "Preview imported audio and transcript."
        },
        trailing = {
            featureAction(
                enabled = true,
                text = if (isPlaying) "Pause" else "Play",
                onClick = {
                    if (mediaPlayer == null) {
                        val newPlayer = runCatching {
                            MediaPlayer().apply {
                                setDataSource(audioPath)
                                prepare()
                                setOnCompletionListener { isPlaying = false }
                            }
                        }.getOrElse {
                            onError("Unable to play audio preview.")
                            return@featureAction
                        }
                        mediaPlayer = newPlayer
                    }
                    val activePlayer = mediaPlayer ?: return@featureAction
                    if (isPlaying) {
                        activePlayer.pause()
                        isPlaying = false
                    } else {
                        activePlayer.start()
                        isPlaying = true
                    }
                }
            )
        }
    )
    Text(
        text = if (transcript.isBlank()) {
            "Transcript is being prepared."
        } else {
            transcript
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun openOriginalSource(
    context: Context,
    document: DocumentEntity?,
    onError: (String) -> Unit
) {
    if (document == null) {
        onError("Document is unavailable.")
        return
    }

    val uri = when (document.fileType) {
        DocumentFileType.WEB -> {
            val source = document.sourceUrl.orEmpty()
            if (source.isBlank()) {
                onError("Web source URL is missing.")
                return
            }
            Uri.parse(source)
        }

        DocumentFileType.PDF,
        DocumentFileType.TXT,
        DocumentFileType.AUDIO -> {
            val path = document.fileBookmarkData?.toString(Charsets.UTF_8).orEmpty()
            if (path.isBlank()) {
                onError("Original file reference is missing.")
                return
            }
            val file = File(path)
            if (!file.exists()) {
                onError("Original file no longer exists.")
                return
            }
            runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }.getOrElse {
                onError("Unable to open source file.")
                return
            }
        }
    }

    val mimeType = when (document.fileType) {
        DocumentFileType.PDF -> "application/pdf"
        DocumentFileType.TXT -> "text/plain"
        DocumentFileType.WEB -> null
        DocumentFileType.AUDIO -> "audio/*"
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = uri
        if (mimeType != null) setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    runCatching {
        context.startActivity(intent)
    }.onFailure { error ->
        when (error) {
            is ActivityNotFoundException -> onError("No app available to open this source.")
            else -> onError("Unable to open source.")
        }
    }
}
