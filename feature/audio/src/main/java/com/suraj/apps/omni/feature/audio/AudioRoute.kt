package com.suraj.apps.omni.feature.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.suraj.apps.omni.core.designsystem.component.OmniFeatureCard
import com.suraj.apps.omni.core.designsystem.component.OmniSectionHeader
import com.suraj.apps.omni.core.designsystem.component.OmniStatusPill
import com.suraj.apps.omni.core.designsystem.theme.OmniRadius
import com.suraj.apps.omni.core.designsystem.theme.OmniSpacing

@Composable
fun AudioRoute() {
    Scaffold { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(OmniSpacing.large),
            verticalArrangement = Arrangement.spacedBy(OmniSpacing.large)
        ) {
            OmniSectionHeader(
                title = "Live audio",
                subtitle = "Record, pause, continue, and transcribe in real time."
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
                    OmniStatusPill(text = "Idle", color = MaterialTheme.colorScheme.primary)
                    WavePlaceholder(modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { }) { Icon(Icons.Default.Mic, contentDescription = "Record") }
                        IconButton(onClick = { }) { Icon(Icons.Default.Pause, contentDescription = "Pause") }
                        IconButton(onClick = { }) { Icon(Icons.Default.Stop, contentDescription = "Stop") }
                    }
                }
            }

            OmniFeatureCard(
                title = "Live transcript",
                subtitle = "Your speech transcript appears here as you record."
            )
        }
    }
}

@Composable
private fun WavePlaceholder(modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val barCount = 24
        val spacing = size.width / barCount
        repeat(barCount) { index ->
            val x = index * spacing + spacing / 2
            val amplitudeFactor = when {
                index % 5 == 0 -> 0.9f
                index % 3 == 0 -> 0.6f
                else -> 0.4f
            }
            val barHeight = size.height * amplitudeFactor
            drawLine(
                color = barColor,
                start = androidx.compose.ui.geometry.Offset(x, (size.height - barHeight) / 2f),
                end = androidx.compose.ui.geometry.Offset(x, (size.height + barHeight) / 2f),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
        }
    }
}
