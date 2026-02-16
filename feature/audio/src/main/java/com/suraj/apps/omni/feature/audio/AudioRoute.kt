package com.suraj.apps.omni.feature.audio

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suraj.apps.omni.core.designsystem.component.OmniSectionHeader
import com.suraj.apps.omni.core.designsystem.component.OmniStatusPill
import com.suraj.apps.omni.core.designsystem.theme.OmniRadius
import com.suraj.apps.omni.core.designsystem.theme.OmniSpacing

@Composable
fun AudioRoute(
    onOpenDashboard: (String) -> Unit,
    onOpenPaywall: () -> Unit
) {
    val viewModel: AudioViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbars = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = RequestPermission(),
        onResult = viewModel::onMicrophonePermissionResult
    )

    LaunchedEffect(uiState.shouldRequestMicrophonePermission) {
        if (!uiState.shouldRequestMicrophonePermission) return@LaunchedEffect
        viewModel.consumePermissionRequest()
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

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

    val hasMicrophonePermission = remember(context) {
        {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbars) }
    ) { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(OmniSpacing.large),
            verticalArrangement = Arrangement.spacedBy(OmniSpacing.large)
        ) {
            OmniSectionHeader(
                title = stringResource(R.string.audio_title),
                subtitle = stringResource(R.string.audio_subtitle)
            )

            Surface(
                shape = RoundedCornerShape(OmniRadius.large),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(OmniSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(OmniSpacing.medium)
                ) {
                    OmniStatusPill(
                        text = statusText(uiState.status),
                        color = when (uiState.status) {
                            RecordingStatus.RECORDING -> MaterialTheme.colorScheme.primary
                            RecordingStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
                            RecordingStatus.FINALIZING -> MaterialTheme.colorScheme.secondary
                            RecordingStatus.IDLE -> MaterialTheme.colorScheme.outline
                        }
                    )

                    Text(
                        text = stringResource(R.string.audio_elapsed_time, formatElapsed(uiState.elapsedMs)),
                        style = MaterialTheme.typography.titleMedium
                    )

                    LiveWaveform(
                        samples = uiState.waveform,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(128.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.onRecordTapped(hasMicrophonePermission()) },
                            enabled = uiState.status != RecordingStatus.FINALIZING
                        ) {
                            Icon(
                                imageVector = when (uiState.status) {
                                    RecordingStatus.PAUSED -> Icons.Default.PlayArrow
                                    else -> Icons.Default.Mic
                                },
                                contentDescription = stringResource(R.string.audio_cd_record_or_resume)
                            )
                        }
                        IconButton(
                            onClick = viewModel::onPauseTapped,
                            enabled = uiState.status == RecordingStatus.RECORDING
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = stringResource(R.string.audio_cd_pause)
                            )
                        }
                        IconButton(
                            onClick = viewModel::onFinishTapped,
                            enabled = uiState.status == RecordingStatus.RECORDING ||
                                uiState.status == RecordingStatus.PAUSED
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = stringResource(R.string.audio_cd_finish_recording)
                            )
                        }
                    }

                    when (val remaining = uiState.remainingFreeRecordings) {
                        null -> Text(
                            text = stringResource(R.string.audio_premium_unlocked_recordings),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        else -> Text(
                            text = stringResource(R.string.audio_free_recordings_remaining, remaining),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(OmniRadius.large),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(OmniSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(OmniSpacing.small)
                ) {
                    Text(
                        text = stringResource(R.string.audio_live_transcript_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(OmniRadius.medium)
                            )
                            .padding(OmniSpacing.medium),
                        contentAlignment = Alignment.TopStart
                    ) {
                        if (uiState.transcript.isBlank()) {
                            Text(
                                text = stringResource(R.string.audio_transcript_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = uiState.transcript,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveWaveform(
    samples: List<Float>,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        if (samples.isEmpty()) return@Canvas
        val spacing = size.width / samples.size
        samples.forEachIndexed { index, amplitude ->
            val x = index * spacing + spacing / 2f
            val normalized = amplitude.coerceIn(0f, 1f)
            val barHeight = size.height * normalized
            drawLine(
                color = barColor,
                start = Offset(x, (size.height - barHeight) / 2f),
                end = Offset(x, (size.height + barHeight) / 2f),
                strokeWidth = (spacing * 0.62f).coerceAtMost(10.dp.toPx()),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun statusText(status: RecordingStatus): String = when (status) {
    RecordingStatus.IDLE -> stringResource(R.string.audio_status_idle)
    RecordingStatus.RECORDING -> stringResource(R.string.audio_status_recording)
    RecordingStatus.PAUSED -> stringResource(R.string.audio_status_paused)
    RecordingStatus.FINALIZING -> stringResource(R.string.audio_status_saving)
}

private fun formatElapsed(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
