package com.suraj.apps.omni.feature.dashboard

import android.app.Application
import android.media.MediaPlayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suraj.apps.omni.core.designsystem.theme.OmniSpacing
import kotlinx.coroutines.delay

@Composable
fun AudioTranscriptRoute(
    documentId: String,
    onBack: () -> Unit
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: DashboardViewModel = viewModel(
        key = "transcript-$documentId",
        factory = DashboardViewModel.factory(
            application = application,
            documentId = documentId
        )
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var mediaPlayer by remember(uiState.audioSourcePath) { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember(uiState.audioSourcePath) { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }

    val audioPreviewError = stringResource(R.string.dashboard_error_audio_preview_failed)
    val transcriptPreparingMessage = stringResource(R.string.dashboard_audio_transcript_preparing)

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeError()
    }

    DisposableEffect(uiState.audioSourcePath) {
        onDispose {
            runCatching { mediaPlayer?.release() }
            mediaPlayer = null
            isPlaying = false
            isSeeking = false
            playbackProgress = 0f
            elapsedMs = 0L
            durationMs = 0L
        }
    }

    LaunchedEffect(mediaPlayer, isPlaying, isSeeking) {
        while (mediaPlayer != null && isPlaying && !isSeeking) {
            val activePlayer = mediaPlayer ?: break
            val duration = activePlayer.duration.coerceAtLeast(0)
            val current = activePlayer.currentPosition.coerceAtLeast(0)
            durationMs = duration.toLong()
            elapsedMs = current.toLong()
            playbackProgress = if (duration > 0) {
                (current.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            delay(250L)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(OmniSpacing.large),
            verticalArrangement = Arrangement.spacedBy(OmniSpacing.medium)
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
                    text = uiState.document?.title ?: stringResource(R.string.dashboard_title_fallback),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Text(
                text = stringResource(R.string.dashboard_audio_preview_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isPlaying) {
                        stringResource(R.string.dashboard_audio_preview_playing)
                    } else {
                        stringResource(R.string.dashboard_audio_preview_idle)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (isPlaying) {
                        stringResource(R.string.dashboard_audio_action_pause)
                    } else {
                        stringResource(R.string.dashboard_audio_action_play)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        val audioPath = uiState.audioSourcePath
                        if (audioPath.isNullOrBlank()) {
                            viewModel.reportError(transcriptPreparingMessage)
                            return@clickable
                        }
                        val activePlayer = mediaPlayer ?: run {
                            val newPlayer = runCatching {
                                MediaPlayer().apply {
                                    setDataSource(audioPath)
                                    prepare()
                                    setOnCompletionListener {
                                        isPlaying = false
                                        elapsedMs = durationMs
                                        playbackProgress = 1f
                                    }
                                }
                            }.getOrElse {
                                viewModel.reportError(audioPreviewError)
                                return@clickable
                            }
                            durationMs = newPlayer.duration.coerceAtLeast(0).toLong()
                            elapsedMs = 0L
                            playbackProgress = 0f
                            mediaPlayer = newPlayer
                            newPlayer
                        }
                        if (isPlaying) {
                            activePlayer.pause()
                            isPlaying = false
                            elapsedMs = activePlayer.currentPosition.toLong()
                        } else {
                            activePlayer.start()
                            isPlaying = true
                        }
                    }
                )
            }

            Slider(
                value = playbackProgress.coerceIn(0f, 1f),
                onValueChange = { value ->
                    isSeeking = true
                    playbackProgress = value
                    elapsedMs = (durationMs * value).toLong().coerceIn(0L, durationMs)
                },
                onValueChangeFinished = {
                    val activePlayer = mediaPlayer
                    if (activePlayer != null && durationMs > 0L) {
                        val targetPosition = (durationMs * playbackProgress).toInt().coerceIn(0, durationMs.toInt())
                        runCatching {
                            activePlayer.seekTo(targetPosition)
                            elapsedMs = targetPosition.toLong()
                        }.onFailure {
                            viewModel.reportError(audioPreviewError)
                        }
                    }
                    isSeeking = false
                },
                enabled = durationMs > 0L
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatPlaybackTime(elapsedMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatPlaybackTime(durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = if (uiState.audioTranscript.isBlank()) {
                        transcriptPreparingMessage
                    } else {
                        uiState.audioTranscript
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(OmniSpacing.medium)
                )
            }
        }
    }
}

private fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
