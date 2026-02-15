package com.suraj.apps.omni.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.suraj.apps.omni.core.designsystem.component.OmniFeatureCard
import com.suraj.apps.omni.core.designsystem.component.OmniFeatureChip
import com.suraj.apps.omni.core.designsystem.component.OmniPrimaryButton
import com.suraj.apps.omni.core.designsystem.component.OmniSectionHeader
import com.suraj.apps.omni.core.designsystem.component.OmniStatusPill
import com.suraj.apps.omni.core.designsystem.theme.OmniSpacing

@Composable
fun SettingsRoute() {
    Scaffold { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(OmniSpacing.large),
            verticalArrangement = Arrangement.spacedBy(OmniSpacing.large)
        ) {
            OmniSectionHeader(
                title = "Design system catalog",
                subtitle = "Reference components and token usage for feature teams."
            )

            OmniFeatureCard(
                title = "Feature card",
                subtitle = "Reusable card for dashboard/library/paywall rows."
            )

            Row(horizontalArrangement = Arrangement.spacedBy(OmniSpacing.small)) {
                OmniFeatureChip(text = "Quiz")
                OmniFeatureChip(text = "Notes")
                OmniFeatureChip(text = "Audio")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(OmniSpacing.small)) {
                OmniStatusPill(text = "In Progress", color = MaterialTheme.colorScheme.primary)
                OmniStatusPill(text = "Done", color = MaterialTheme.colorScheme.tertiary)
            }

            OmniPrimaryButton(text = "Primary action", onClick = { })

            Text(
                text = "Use design-system components for all new screens. Avoid inline styling duplication.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
