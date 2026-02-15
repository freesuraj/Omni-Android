package com.suraj.apps.omni.feature.paywall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.suraj.apps.omni.core.designsystem.component.OmniPrimaryButton
import com.suraj.apps.omni.core.designsystem.component.OmniSectionHeader
import com.suraj.apps.omni.core.designsystem.theme.OmniSpacing

@Composable
fun PaywallRoute() {
    val features = listOf(
        "Premium AI models" to "Access advanced providers for stronger outputs.",
        "Unlimited documents" to "Import and process without free-tier cap.",
        "Unlimited live audio recordings" to "Record as many live sessions as needed.",
        "Extended Q&A" to "Ask deeper questions about your sources.",
        "Deep analysis" to "Run page-level or transcript-level analysis."
    )

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
                title = "Unlock Omni Pro",
                subtitle = "Get full access to premium study and audio workflows."
            )

            features.forEach { (title, subtitle) ->
                OmniFeatureCard(title = title, subtitle = subtitle)
            }

            OmniPrimaryButton(text = "Start free trial", onClick = { })
            Text(
                text = "Subscriptions renew automatically unless cancelled 24 hours before renewal.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
