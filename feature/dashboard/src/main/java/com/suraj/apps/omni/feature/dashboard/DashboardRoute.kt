package com.suraj.apps.omni.feature.dashboard

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suraj.apps.omni.core.data.local.entity.DocumentEntity
import com.suraj.apps.omni.core.designsystem.component.OmniFeatureCard
import com.suraj.apps.omni.core.designsystem.theme.OmniSpacing
import com.suraj.apps.omni.core.model.DocumentFileType
import java.io.File

@Composable
fun DashboardRoute(
    documentId: String,
    onBack: () -> Unit,
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
        if (uiState.isOnboarding) {
            DashboardOnboardingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(OmniSpacing.large),
                title = uiState.document?.title ?: stringResource(R.string.dashboard_title_fallback),
                progress = uiState.onboardingProgress,
                label = uiState.onboardingLabel,
                onBack = onBack,
                onRetry = if (uiState.showRetryAction) {
                    viewModel::retryOnboarding
                } else {
                    null
                }
            )
            return@Scaffold
        }

        val featureCards = listOf(
            DashboardFeatureCard(
                title = stringResource(R.string.dashboard_feature_quiz_title),
                trailing = uiState.latestQuizQuestionCount.takeIf { it > 0 }?.let { count ->
                    pluralStringResource(R.plurals.dashboard_card_quiz_generated, count, count)
                },
                color = Color(0xFF1F9BFF),
                icon = Icons.Rounded.Help,
                onClick = { onOpenQuiz(documentId) }
            ),
            DashboardFeatureCard(
                title = stringResource(R.string.dashboard_feature_notes_title),
                trailing = uiState.studyNoteCount.takeIf { it > 0 }?.let { count ->
                    pluralStringResource(R.plurals.dashboard_card_notes_generated, count, count)
                },
                color = Color(0xFFC95DE8),
                icon = Icons.Rounded.AutoAwesome,
                onClick = { onOpenNotes(documentId) }
            ),
            DashboardFeatureCard(
                title = stringResource(R.string.dashboard_feature_summary_title),
                trailing = stringResource(R.string.dashboard_card_summary_trailing),
                color = Color(0xFFFF8F1F),
                icon = Icons.Rounded.Notes,
                onClick = { onOpenSummary(documentId) }
            ),
            DashboardFeatureCard(
                title = stringResource(R.string.dashboard_feature_analysis_title),
                trailing = stringResource(R.string.dashboard_card_analysis_trailing),
                color = Color(0xFF5660F7),
                icon = Icons.Rounded.Search,
                onClick = {
                    if (viewModel.requestPremiumFeatureAccess(analysisFeatureName)) {
                        onOpenAnalysis(documentId)
                    } else {
                        onOpenPaywall()
                    }
                }
            ),
            DashboardFeatureCard(
                title = stringResource(R.string.dashboard_feature_qa_title),
                trailing = stringResource(R.string.dashboard_card_qa_trailing),
                color = Color(0xFF34C759),
                icon = Icons.Rounded.Chat,
                onClick = {
                    if (viewModel.requestPremiumFeatureAccess(qaFeatureName)) {
                        onOpenQa(documentId)
                    } else {
                        onOpenPaywall()
                    }
                }
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(OmniSpacing.large),
            verticalArrangement = Arrangement.spacedBy(OmniSpacing.medium)
        ) {
            DashboardHeader(
                title = uiState.document?.title ?: stringResource(R.string.dashboard_title_fallback),
                sourceStats = uiState.sourceStats.ifBlank { stringResource(R.string.dashboard_source_details_preparing) },
                onBack = onBack,
                onOpenOriginal = {
                    openOriginalSource(
                        context = context,
                        document = uiState.document,
                        onError = viewModel::reportError
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

            featureCards.forEach { card ->
                DashboardActionCard(card = card)
            }
        }
    }
}

private data class DashboardFeatureCard(
    val title: String,
    val trailing: String?,
    val color: Color,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun DashboardHeader(
    title: String,
    sourceStats: String,
    onBack: () -> Unit,
    onOpenOriginal: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OmniSpacing.medium)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.dashboard_back_content_description)
                )
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f)
        )

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            IconButton(onClick = onOpenOriginal) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = stringResource(R.string.dashboard_action_open_original)
                )
            }
        }
    }

    Text(
        text = sourceStats,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DashboardOnboardingState(
    modifier: Modifier,
    title: String,
    progress: Float,
    label: String,
    onBack: () -> Unit,
    onRetry: (() -> Unit)?
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OmniSpacing.medium)
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.dashboard_back_content_description)
                    )
                }
            }
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OmniSpacing.medium)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(92.dp),
                    strokeWidth = 6.dp
                )
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onRetry != null) {
                Text(
                    text = stringResource(R.string.dashboard_action_retry),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onRetry)
                )
            }
        }

        Text(
            text = stringResource(R.string.dashboard_onboarding_footer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DashboardActionCard(card: DashboardFeatureCard) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = card.onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OmniSpacing.medium, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OmniSpacing.medium)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(card.color.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = card.icon,
                    contentDescription = null,
                    tint = card.color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = card.title,
                style = MaterialTheme.typography.titleLarge,
                color = card.color,
                modifier = Modifier.weight(1f)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                card.trailing?.let { trailing ->
                    Text(
                        text = trailing,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
            Text(
                text = if (isPlaying) stringResource(R.string.dashboard_audio_action_pause) else stringResource(R.string.dashboard_audio_action_play),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    if (mediaPlayer == null) {
                        val newPlayer = runCatching {
                            MediaPlayer().apply {
                                setDataSource(audioPath)
                                prepare()
                                setOnCompletionListener { isPlaying = false }
                            }
                        }.getOrElse {
                            onError(audioPreviewError)
                            return@clickable
                        }
                        mediaPlayer = newPlayer
                    }
                    val activePlayer = mediaPlayer ?: return@clickable
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = if (transcript.isBlank()) {
                stringResource(R.string.dashboard_audio_transcript_preparing)
            } else {
                transcript
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(OmniSpacing.medium)
        )
    }
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
        DocumentFileType.TXT -> {
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

        DocumentFileType.AUDIO -> {
            val transcriptFile = File(context.filesDir, "text/${document.id}.txt")
            if (!transcriptFile.exists()) {
                onError(context.getString(R.string.dashboard_audio_transcript_preparing))
                return
            }
            runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    transcriptFile
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
        DocumentFileType.AUDIO -> "text/plain"
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
