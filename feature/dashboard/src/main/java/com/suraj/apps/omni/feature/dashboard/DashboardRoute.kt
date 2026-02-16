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
import androidx.compose.ui.res.stringResource
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
    onOpenAnalysis: (String) -> Unit,
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
    val qaFeatureName = stringResource(R.string.dashboard_feature_qa_title)
    val analysisFeatureName = stringResource(R.string.dashboard_feature_analysis_title)
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
                title = uiState.document?.title ?: stringResource(R.string.dashboard_title_fallback),
                subtitle = stringResource(R.string.dashboard_document_id, documentId)
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
                    title = stringResource(R.string.dashboard_onboarding_in_progress_title),
                    subtitle = stringResource(R.string.dashboard_onboarding_in_progress_subtitle),
                    trailing = {
                        if (uiState.showRetryAction) {
                            Button(onClick = viewModel::retryOnboarding) {
                                Text(stringResource(R.string.dashboard_action_retry))
                            }
                        }
                    }
                )
            }

            OmniFeatureCard(
                title = stringResource(R.string.dashboard_source_details_title),
                subtitle = uiState.sourceStats.ifBlank { stringResource(R.string.dashboard_source_details_preparing) },
                trailing = {
                    featureAction(
                        enabled = uiState.document != null,
                        text = stringResource(R.string.dashboard_action_open_original),
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
                title = stringResource(R.string.dashboard_feature_quiz_title),
                subtitle = stringResource(R.string.dashboard_feature_quiz_subtitle),
                trailing = {
                    featureAction(
                        enabled = !uiState.isOnboarding,
                        text = stringResource(R.string.dashboard_action_open),
                        onClick = { onOpenQuiz(documentId) }
                    )
                }
            )
            OmniFeatureCard(
                title = stringResource(R.string.dashboard_feature_notes_title),
                subtitle = stringResource(R.string.dashboard_feature_notes_subtitle),
                trailing = {
                    featureAction(
                        enabled = !uiState.isOnboarding,
                        text = stringResource(R.string.dashboard_action_open),
                        onClick = { onOpenNotes(documentId) }
                    )
                }
            )
            OmniFeatureCard(
                title = stringResource(R.string.dashboard_feature_summary_title),
                subtitle = stringResource(R.string.dashboard_feature_summary_subtitle),
                trailing = {
                    featureAction(
                        enabled = !uiState.isOnboarding,
                        text = stringResource(R.string.dashboard_action_open),
                        onClick = { onOpenSummary(documentId) }
                    )
                }
            )
            OmniFeatureCard(
                title = stringResource(R.string.dashboard_feature_qa_title),
                subtitle = stringResource(R.string.dashboard_feature_qa_subtitle),
                trailing = {
                    featureAction(
                        enabled = !uiState.isOnboarding,
                        text = stringResource(R.string.dashboard_action_open),
                        onClick = {
                            if (viewModel.requestPremiumFeatureAccess(qaFeatureName)) {
                                onOpenQa(documentId)
                            } else {
                                onOpenPaywall()
                            }
                        }
                    )
                }
            )
            OmniFeatureCard(
                title = stringResource(R.string.dashboard_feature_analysis_title),
                subtitle = stringResource(R.string.dashboard_feature_analysis_subtitle),
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(OmniSpacing.small)) {
                        featureAction(
                            enabled = !uiState.isOnboarding,
                            text = stringResource(R.string.dashboard_action_open),
                            onClick = {
                                if (viewModel.requestPremiumFeatureAccess(analysisFeatureName)) {
                                    onOpenAnalysis(documentId)
                                } else {
                                    onOpenPaywall()
                                }
                            }
                        )
                    }
                }
            )
            OmniPrimaryButton(
                text = stringResource(R.string.dashboard_upgrade_button),
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
    val audioPreviewError = stringResource(R.string.dashboard_error_audio_preview_failed)

    DisposableEffect(audioPath) {
        onDispose {
            runCatching { mediaPlayer?.release() }
            mediaPlayer = null
        }
    }

    OmniFeatureCard(
        title = stringResource(R.string.dashboard_audio_preview_title),
        subtitle = if (isPlaying) {
            stringResource(R.string.dashboard_audio_preview_playing)
        } else {
            stringResource(R.string.dashboard_audio_preview_idle)
        },
        trailing = {
            featureAction(
                enabled = true,
                text = if (isPlaying) stringResource(R.string.dashboard_audio_action_pause) else stringResource(R.string.dashboard_audio_action_play),
                onClick = {
                    if (mediaPlayer == null) {
                        val newPlayer = runCatching {
                            MediaPlayer().apply {
                                setDataSource(audioPath)
                                prepare()
                                setOnCompletionListener { isPlaying = false }
                            }
                        }.getOrElse {
                            onError(audioPreviewError)
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
            stringResource(R.string.dashboard_audio_transcript_preparing)
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
        onError(context.getString(R.string.dashboard_error_document_unavailable))
        return
    }

    val uri = when (document.fileType) {
        DocumentFileType.WEB -> {
            val source = document.sourceUrl.orEmpty()
            if (source.isBlank()) {
                onError(context.getString(R.string.dashboard_error_web_source_missing))
                return
            }
            Uri.parse(source)
        }

        DocumentFileType.PDF,
        DocumentFileType.TXT,
        DocumentFileType.AUDIO -> {
            val path = document.fileBookmarkData?.toString(Charsets.UTF_8).orEmpty()
            if (path.isBlank()) {
                onError(context.getString(R.string.dashboard_error_file_reference_missing))
                return
            }
            val file = File(path)
            if (!file.exists()) {
                onError(context.getString(R.string.dashboard_error_file_missing))
                return
            }
            runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }.getOrElse {
                onError(context.getString(R.string.dashboard_error_open_source_file_failed))
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
            is ActivityNotFoundException -> onError(context.getString(R.string.dashboard_error_no_app_available))
            else -> onError(context.getString(R.string.dashboard_error_open_source_failed))
        }
    }
}
