package com.suraj.apps.omni.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.suraj.apps.omni.core.designsystem.component.OmniFeatureCard
import com.suraj.apps.omni.core.designsystem.component.OmniFeatureChip
import com.suraj.apps.omni.core.designsystem.component.OmniPrimaryButton
import com.suraj.apps.omni.core.designsystem.component.OmniSectionHeader
import com.suraj.apps.omni.core.designsystem.component.OmniStepBadge
import com.suraj.apps.omni.core.designsystem.theme.OmniSpacing

private data class StepContent(val step: Int, val title: String, val subtitle: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryRoute(
    onOpenAudio: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDashboard: (String) -> Unit,
    onOpenPaywall: () -> Unit
) {
    val featureChips = listOf("Quiz", "Flashcards", "Summary", "Q&A", "Analysis", "Audio")
    val steps = listOf(
        StepContent(1, "Import source", "Import sources from + action in upcoming ticket."),
        StepContent(2, "Generate study set", "Omni builds quiz, notes, summary, and Q&A context."),
        StepContent(3, "Study and review", "Use dashboard outputs and keep learning in one place.")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(imageVector = Icons.Rounded.Add, contentDescription = "Import")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(imageVector = Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(OmniSpacing.large),
            verticalArrangement = Arrangement.spacedBy(OmniSpacing.large)
        ) {
            OmniSectionHeader(
                title = "Start with document, audio note or article",
                subtitle = "Import and storage pipeline lands in ticket #4."
            )

            steps.forEach { step ->
                OmniFeatureCard(
                    title = step.title,
                    subtitle = step.subtitle,
                    trailing = { OmniStepBadge(step = step.step) }
                )
            }

            featureChips.forEach { chip ->
                OmniFeatureChip(text = chip)
            }

            OmniPrimaryButton(text = "Record live audio", onClick = onOpenAudio)
            OmniPrimaryButton(text = "Open dashboard placeholder", onClick = { onOpenDashboard("placeholder") })
            OmniPrimaryButton(text = "Open paywall", onClick = onOpenPaywall)
        }
    }
}
