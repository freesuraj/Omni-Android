package com.suraj.apps.omni.feature.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.suraj.apps.omni.core.designsystem.theme.OmniSpacing

@Composable
fun PaywallBenefitsRoute(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onRestorePurchases: () -> Unit
) {
    val benefits = listOf(
        ProBenefitItem(
            icon = Icons.Rounded.Psychology,
            title = stringResource(R.string.paywall_benefit_models_title),
            subtitle = stringResource(R.string.paywall_benefit_models_subtitle)
        ),
        ProBenefitItem(
            icon = Icons.Rounded.Description,
            title = stringResource(R.string.paywall_benefit_documents_title),
            subtitle = stringResource(R.string.paywall_benefit_documents_subtitle)
        ),
        ProBenefitItem(
            icon = Icons.Rounded.GraphicEq,
            title = stringResource(R.string.paywall_benefit_audio_title),
            subtitle = stringResource(R.string.paywall_benefit_audio_subtitle)
        ),
        ProBenefitItem(
            icon = Icons.Rounded.AllInclusive,
            title = stringResource(R.string.paywall_benefit_qa_title),
            subtitle = stringResource(R.string.paywall_benefit_qa_subtitle)
        ),
        ProBenefitItem(
            icon = Icons.Rounded.TextSnippet,
            title = stringResource(R.string.paywall_benefit_analysis_title),
            subtitle = stringResource(R.string.paywall_benefit_analysis_subtitle)
        ),
        ProBenefitItem(
            icon = Icons.Rounded.AutoAwesome,
            title = stringResource(R.string.paywall_benefit_cards_title),
            subtitle = stringResource(R.string.paywall_benefit_cards_subtitle)
        )
    )

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(OmniSpacing.large),
            verticalArrangement = Arrangement.spacedBy(OmniSpacing.large)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.paywall_back_content_description),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(OmniSpacing.medium)
            ) {
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(54.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.paywall_title),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.paywall_benefits_subheading),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(OmniSpacing.large)) {
                benefits.forEach { benefit ->
                    ProBenefitRow(item = benefit)
                }
            }

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.paywall_start_trial_button))
            }

            TextButton(
                onClick = onRestorePurchases,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.paywall_restore_button))
            }
        }
    }
}

private data class ProBenefitItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String
)

@Composable
private fun ProBenefitRow(item: ProBenefitItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OmniSpacing.medium),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 4.dp)
                .size(24.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
