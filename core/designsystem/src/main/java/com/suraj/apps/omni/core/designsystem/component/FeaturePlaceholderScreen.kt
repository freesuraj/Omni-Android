package com.suraj.apps.omni.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.suraj.apps.omni.core.designsystem.theme.OmniSpacing

@Composable
fun FeaturePlaceholderScreen(
    title: String,
    subtitle: String,
    action: (@Composable () -> Unit)? = null
) {
    Scaffold { paddingValues: PaddingValues ->
        Column(
            verticalArrangement = Arrangement.spacedBy(OmniSpacing.medium),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(OmniSpacing.xLarge)
        ) {
            Text(text = title)
            Text(text = subtitle)
            action?.invoke()
        }
    }
}
